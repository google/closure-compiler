/*
 * Copyright 2017 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.base.JSCompObjects.identical;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.ReferenceCollector.Behavior;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Collects global variable references for use by {@link CrossChunkCodeMotion}. */
public final class CrossChunkReferenceCollector implements CompilerPass {

  /** List of all top-level statements in the order they appear in the AST. */
  private final List<TopLevelStatement> topLevelStatements = new ArrayList<>();

  /** JavaScript compiler to use in traversing. */
  private final AbstractCompiler compiler;

  private final ReferenceCollector referenceCollector;

  private int statementCounter = 0;
  private @Nullable TopLevelStatementDraft topLevelStatementDraft = null;

  /** Constructor initializes reference collector. */
  CrossChunkReferenceCollector(AbstractCompiler compiler, ScopeCreator creator) {
    this.compiler = compiler;
    this.referenceCollector =
        new ReferenceCollector(
            compiler,
            new TopLevelStatementCollectingBehavior(),
            creator,
            this::shouldCollectVar,
            /* collectHoistedFunctionsInReferenceOrder= */ false);
  }

  private boolean shouldCollectVar(Var v) {
    // Only global, non-exported names can be moved
    return v.isGlobal()
        && !compiler.getCodingConvention().isExported(v.getName(), /* local= */ false);
  }

  /** Collects references and top-level statements from the given externs and root. */
  @Override
  public void process(Node externs, Node root) {
    checkState(topLevelStatements.isEmpty(), "process() called more than once");
    referenceCollector.process(externs, root);
  }

  /** Collects references and top-level statements from the given root. */
  public void process(Node root) {
    checkState(topLevelStatements.isEmpty(), "process() called more than once");
    referenceCollector.process(root);
  }

  /** Gets the reference collection for the given variable. */
  ReferenceCollection getReferences(Var v) {
    return referenceCollector.getReferences(v);
  }

  @VisibleForTesting
  ImmutableMap<String, Var> getGlobalVariableNamesMap() {
    ImmutableMap.Builder<String, Var> builder = ImmutableMap.builder();
    for (Var v : referenceCollector.getAllSymbols()) {
      builder.put(v.getName(), v);
    }
    return builder.buildOrThrow();
  }

  private final class TopLevelStatementCollectingBehavior implements Behavior {
    @Override
    public void beforeTraverseNode(NodeTraversal t, Node n, @Nullable Node parent) {
      if (parent != null && NodeUtil.isTopLevel(parent)) {
        checkState(topLevelStatementDraft == null, n);
        topLevelStatementDraft = initializeDraftStatement(t.getChunk(), n);
      }
    }

    @Override
    public void afterVisitNode(
        NodeTraversal t, Node n, @Nullable Node parent, @Nullable Reference addedReference) {
      if (topLevelStatementDraft == null) {
        return;
      }
      if (n.equals(topLevelStatementDraft.statementNode)) {
        topLevelStatements.add(new TopLevelStatement(topLevelStatementDraft));
        topLevelStatementDraft = null;
      } else if (addedReference != null && n.isName()) {
        if (identical(addedReference.getNode(), topLevelStatementDraft.declaredNameNode)) {
          topLevelStatementDraft.declaredNameReference = addedReference;
        } else {
          topLevelStatementDraft.nonDeclarationReferences.add(addedReference);
        }
      }
    }

    @Override
    public void afterExitScope(NodeTraversal t, ReferenceMap referenceMap) {}
  }

  private TopLevelStatementDraft initializeDraftStatement(JSChunk chunk, Node statementNode) {
    TopLevelStatementDraft draft =
        new TopLevelStatementDraft(statementCounter++, chunk, statementNode);
    // Determine whether this statement declares a name or not.
    // If so, save its name node and value node, if any.
    if (NodeUtil.isNameDeclaration(statementNode)) {
      // variable declaration
      draft.declaredNameNode = statementNode.getFirstChild();
      draft.declaredValueNode = statementNode.getFirstFirstChild();
    } else if (statementNode.isClass()) {
      draft.declaredNameNode = statementNode.getFirstChild();
      draft.declaredValueNode = statementNode;
    } else if (statementNode.isFunction()) {
      // function declaration
      draft.declaredNameNode = statementNode.getFirstChild();
      draft.declaredValueNode = statementNode;
    } else if (statementNode.isExprResult()) {
      Node expr = checkNotNull(statementNode.getFirstChild());
      if (expr.isAssign()) {
        Node lhs = checkNotNull(expr.getFirstChild());
        Node rhs = checkNotNull(expr.getSecondChild());
        if (lhs.isName()) {
          // `varName = value;`
          draft.declaredNameNode = lhs;
          draft.declaredValueNode = rhs;
        } else if (lhs.isGetProp() || isPotentiallyMoveableGetElem(lhs)) {
          Node nameNode = checkNotNull(lhs.getFirstChild());
          while (nameNode.isGetProp()) {
            nameNode = checkNotNull(nameNode.getFirstChild());
          }
          if (nameNode.isName()) {
            // `varName.some.property = value;`
            // OR
            // `varName[someProperty] = value;`
            draft.declaredNameNode = nameNode;
            draft.declaredValueNode = rhs;
          }
        }
      } else if (expr.isCall()) {
        Node nameNode = null;
        Node valueNode = null;
        CodingConvention.SubclassRelationship relationship =
            compiler.getCodingConvention().getClassesDefinedByCall(expr);
        if (relationship != null) {
          // Check for $jscomp.inherits(SubC, SuperC), goog.inherits(Sub, SuperC), etc.
          String declaredName = checkNotNull(relationship.subclassName);
          for (Node callArg = expr.getSecondChild(); callArg != null; callArg = callArg.getNext()) {
            // We're assuming that the child class must be an argument to the function that
            // establishes its inheritance, which is true for `goog.inherits()` and
            // `$jscomp.inherits()`
            // TODO(bradfordcsmith): handle cases like `goog.inherits(x.ChildClass, SuperClass)`
            if (callArg.isName() && declaredName.equals(callArg.getString())) {
              nameNode = callArg;
              break;
            }
          }
        } else if (NodeUtil.isObjectDefinePropertiesDefinition(expr)) {
          // Check for $jscomp$global.Object.defineProperties.
          Node targetObject = expr.getSecondChild();

          // Get the global var being referenced in the first parameter of Object.defineProperties.
          // Can be 'Foo' or 'Foo.prototype'.
          nameNode = targetObject.isName() ? targetObject : targetObject.getFirstChild();

          // Second parameter of defineProperties is the value node.
          valueNode = targetObject.getNext();
        }

        if (nameNode != null) {
          draft.declaredNameNode = nameNode;
          draft.declaredValueNode = valueNode;
        }
      }
    }
    return draft;
  }

  private static boolean isPotentiallyMoveableGetElem(Node maybeGetElem) {
    if (!maybeGetElem.isGetElem()) {
      return false;
    }

    // We'll only consider a GETELEM as potentially moveable if the property being indexed is on
    // `Symbol` (e.g. `Symbol.iterator`). We don't generally want to consider GETELEM nodes as
    // moveable as registries (ex. `GlobalRegistry['Foo'] = Foo`) will create a reference from `Foo`
    // to `GlobalRegistry`. As other pieces of code reference `GlobalRegistry` this will ultimately
    // cause a large cluster of symbols that would have to be considered as moving together, but
    // likely will no to be able to.
    // Therefore, we we'll only consider a subset of cases, like `Symbol.iterator`, as moveable as
    // these large reference clusters are unlikely in this case. This should allow classes that
    // define these properties to potentialy be moved to later chunks.
    Node property = maybeGetElem.getLastChild();
    if (!property.isGetProp()) {
      return false;
    }
    Node name = property.getFirstChild();
    return name.isName() && name.getString().equals("Symbol");
  }

  List<TopLevelStatement> getTopLevelStatements() {
    return Collections.unmodifiableList(topLevelStatements);
  }

  /** Determines whether the given value is eligible to be moved across chunks. */
  private boolean canMoveValue(Scope scope, Node valueNode) {
    // the value is only movable if it's
    // - nothing,
    // - a constant literal,
    // - a function
    // - class without computed properties, or whose computed properties expressions are moveable
    // - an array/object literal of movable values
    // - a template literal with movable substitutions
    // - a function stub generated by CrossChunkMethodMotion.
    if (valueNode == null || NodeUtil.isLiteralValue(valueNode, true) || valueNode.isFunction()) {
      return true;
    }

    // Consider the node eligible for move if the current statement or expression is annotated
    // with @pureOrBreakMyCode.
    JSDocInfo jsDocInfo = NodeUtil.getBestJSDocInfo(valueNode);
    if (jsDocInfo != null && jsDocInfo.isPureOrBreakMyCode()) {
      return true;
    }

    switch (valueNode.getToken()) {
      case CLASS -> {
        // NOTE: Why aren't class extends expressions checked here?
        Node classMembers = valueNode.getLastChild();
        for (Node member = classMembers.getFirstChild();
            member != null;
            member = member.getNext()) {
          if (member.isComputedProp()) {
            Node keyExpr = member.getFirstChild();
            Node method = member.getLastChild();
            checkState(method.isFunction(), method);
            if (!canMoveValue(scope, keyExpr)) {
              return false;
            }
          } else if (member.isComputedFieldDef()) {
            Node keyExpr = member.getFirstChild();
            if (!canMoveValue(scope, keyExpr)) {
              return false;
            }
          } else if (member.isMemberFieldDef()) {
            if (member.isStaticMember() && !canMoveValue(scope, member.getFirstChild())) {
              // Unlike non-static fields,the RHS of a static field is executed at
              // class definition time. So, we must check canMoveValue for static fields.
              return false;
            }
          } else if (member.isBlock()) {
            // TODO(bradfordcsmith): Ideally could move these in some cases, fix later
            return false;
          } else {
            checkState(member.isMemberFunctionDef() || NodeUtil.isGetOrSetKey(member), member);
          }
        }
        return true;
      }
      case CALL -> {
        // In general it is not safe to move function calls, but we carve out an exception
        // for the special stub method calls used for CrossChunkMethodMotion.
        // Case: `JSCompiler_stubMethod(x)`
        Node functionName = checkNotNull(valueNode.getFirstChild());
        return functionName.isName()
            && functionName.getString().equals(CrossChunkMethodMotion.STUB_METHOD_NAME);
      }
      case ARRAYLIT -> {
        // Movable if all of the array values are movable.
        for (Node child = valueNode.getFirstChild(); child != null; child = child.getNext()) {
          if (!canMoveValue(scope, child)) {
            return false;
          }
        }

        return true;
      }
      case OBJECTLIT -> {
        // Movable if all of the keys and values are movable.
        for (Node child = valueNode.getFirstChild(); child != null; child = child.getNext()) {
          switch (child.getToken()) {
            case MEMBER_FUNCTION_DEF:
            case GETTER_DEF:
            case SETTER_DEF:
              break;

            case COMPUTED_PROP:
              if (!canMoveValue(scope, child.getFirstChild())
                  || !canMoveValue(scope, child.getLastChild())) {
                return false;
              }
              break;

            case STRING_KEY:
            case OBJECT_SPREAD:
              if (!canMoveValue(scope, child.getOnlyChild())) {
                return false;
              }
              break;

            default:
              throw new IllegalStateException(
                  "Unexpected child of OBJECTLIT: " + child.toStringTree());
          }
        }

        return true;
      }
      case NAME -> {
        // If the value is guaranteed to never be changed after
        // this reference, then we can move it.
        Var v = scope.getVar(valueNode.getString());
        if (v != null && v.isGlobal()) {
          ReferenceCollection refCollection = getReferences(v);
          if (refCollection != null
              && refCollection.isWellDefined()
              && refCollection.isAssignedOnceInLifetime()) {
            return true;
          }
        }
      }
      case TEMPLATELIT -> {
        // A template literal is movable if all of the substitutions it contains are movable.
        for (Node child = valueNode.getFirstChild(); child != null; child = child.getNext()) {
          if (child.isTemplateLitSub()) {
            if (!canMoveValue(scope, child.getFirstChild())) {
              return false;
            }
          } else {
            checkState(child.isTemplateLitString(), child);
          }
        }
        return true;
      }
      case GETPROP -> {
        // `Symbol` is a built-in JavaScript object that is guaranteed to be unique. Therefore it
        // is movable. Any properties on `Symbol` are also movable (e.g `Symbol.iterator`).
        Node child = valueNode.getFirstChild();
        if (child != null && child.isName()) {
          String symbol = child.getString();
          if (symbol != null && symbol.equals("Symbol")) {
            return true;
          }
        }
        return false;
      }
      default -> {}
    }

    return false;
  }

  /** Represents a top-level statement and the references to global names it contains. */
  final class TopLevelStatement {

    /** 0-based index indicating original order of this statement in the source. */
    private final int originalOrder;

    private final JSChunk chunk;
    private final Node statementNode;
    private final List<Reference> nonDeclarationReferences;
    private final Reference declaredNameReference;
    private final Node declaredValueNode;

    TopLevelStatement(TopLevelStatementDraft draft) {
      this.originalOrder = draft.originalOrder;
      this.chunk = draft.chunk;
      this.statementNode = draft.statementNode;
      this.nonDeclarationReferences = Collections.unmodifiableList(draft.nonDeclarationReferences);
      this.declaredNameReference = draft.declaredNameReference;
      this.declaredValueNode = draft.declaredValueNode;
    }

    int getOriginalOrder() {
      return originalOrder;
    }

    JSChunk getChunk() {
      return chunk;
    }

    Node getStatementNode() {
      return statementNode;
    }

    List<Reference> getNonDeclarationReferences() {
      return Collections.unmodifiableList(nonDeclarationReferences);
    }

    boolean isDeclarationStatement() {
      return declaredNameReference != null;
    }

    Reference getDeclaredNameReference() {
      return checkNotNull(declaredNameReference);
    }

    @Nullable
    Node getDeclaredValueNode() {
      return declaredValueNode;
    }

    boolean isMovableDeclaration() {
      return isDeclarationStatement()
          && canMoveValue(declaredNameReference.getScope(), declaredValueNode);
    }
  }

  /** Holds statement info temporarily while the statement is being traversed. */
  private static final class TopLevelStatementDraft {

    /** 0-based index indicating original order of this statement in the source. */
    final int originalOrder;

    final JSChunk chunk;
    final Node statementNode;
    final List<Reference> nonDeclarationReferences = new ArrayList<>();
    @Nullable Node declaredValueNode = null;
    @Nullable Node declaredNameNode = null;
    @Nullable Reference declaredNameReference = null;

    TopLevelStatementDraft(int originalOrder, JSChunk chunk, Node statementNode) {
      this.originalOrder = originalOrder;
      this.chunk = chunk;
      this.statementNode = statementNode;
    }
  }
}
