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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.nullness.Nullable;

/** Collects global variable references for use by {@link CrossChunkCodeMotion}. */
public final class CrossChunkReferenceCollector implements ScopedCallback, CompilerPass {

  /** Maps global variable name to the corresponding {@link Var} object. */
  private final Map<String, Var> varsByName = new HashMap<>();

  /**
   * Maps a given variable to a collection of references to that name. Note that Var objects are not
   * stable across multiple traversals (unlike scope root or name).
   */
  private final Map<Var, ReferenceCollection> referenceMap = new LinkedHashMap<>();

  /** The stack of basic blocks and scopes the current traversal is in. */
  private final List<BasicBlock> blockStack = new ArrayList<>();

  /** List of all top-level statements in the order they appear in the AST. */
  private final List<TopLevelStatement> topLevelStatements = new ArrayList<>();

  private final ScopeCreator scopeCreator;

  /** JavaScript compiler to use in traversing. */
  private final AbstractCompiler compiler;

  private int statementCounter = 0;
  private @Nullable TopLevelStatementDraft topLevelStatementDraft = null;

  /** Constructor initializes block stack. */
  CrossChunkReferenceCollector(AbstractCompiler compiler, ScopeCreator creator) {
    this.compiler = compiler;
    this.scopeCreator = creator;
  }

  /** Convenience method for running this pass over a tree with this class as a callback. */
  @Override
  public void process(Node externs, Node root) {
    checkState(topLevelStatements.isEmpty(), "process() called more than once");
    NodeTraversal.builder()
        .setCompiler(compiler)
        .setCallback(this)
        .setScopeCreator(scopeCreator)
        .traverseRoots(externs, root);
  }

  public void process(Node root) {
    checkState(topLevelStatements.isEmpty(), "process() called more than once");
    NodeTraversal.builder()
        .setCompiler(compiler)
        .setCallback(this)
        .setScopeCreator(scopeCreator)
        .traverse(root);
  }

  /** Gets the variables that were referenced in this callback. */
  Iterable<Var> getAllSymbols() {
    return referenceMap.keySet();
  }

  /** Gets the reference collection for the given variable. */
  ReferenceCollection getReferences(Var v) {
    return referenceMap.get(v);
  }

  ImmutableMap<String, Var> getGlobalVariableNamesMap() {
    return ImmutableMap.copyOf(varsByName);
  }

  /** For each node, update the block stack and reference collection as appropriate. */
  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (topLevelStatementDraft != null) {
      if (n.equals(topLevelStatementDraft.statementNode)) {
        topLevelStatements.add(new TopLevelStatement(topLevelStatementDraft));
        topLevelStatementDraft = null;
      } else if (n.isName()) {
        String varName = n.getString();
        Var v = t.getScope().getVar(varName);

        if (v != null) {
          // Only global, non-exported names can be moved
          if (v.isGlobal()
              && !compiler.getCodingConvention().isExported(v.getName(), /* local= */ false)) {
            if (varsByName.containsKey(varName)) {
              checkState(Objects.equals(varsByName.get(varName), v));
            } else {
              varsByName.put(varName, v);
            }
            Reference reference = new Reference(n, t, peek(blockStack));
            if (reference.getNode() == topLevelStatementDraft.declaredNameNode) {
              topLevelStatementDraft.declaredNameReference = reference;
            } else {
              topLevelStatementDraft.nonDeclarationReferences.add(reference);
            }
            addReferenceToCollection(v, reference);
          }
        }
      }
    }
    if (isBlockBoundary(n, parent)) {
      pop(blockStack);
    }
  }

  /** Updates block stack and invokes any additional behavior. */
  @Override
  public void enterScope(NodeTraversal t) {
    Node n = t.getScopeRoot();
    BasicBlock parent = blockStack.isEmpty() ? null : peek(blockStack);
    // Don't add all ES6 scope roots to blockStack, only those that are also scopes according to
    // the ES5 scoping rules. Other nodes that ought to be considered the root of a BasicBlock
    // are added in shouldTraverse() and removed in visit().
    if (t.isHoistScope()) {
      blockStack.add(new BasicBlock(parent, n));
    }
  }

  /** Updates block stack and invokes any additional behavior. */
  @Override
  public void exitScope(NodeTraversal t) {
    if (t.isHoistScope()) {
      pop(blockStack);
    }
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (parent != null && NodeUtil.isTopLevel(parent)) {
      checkState(topLevelStatementDraft == null, n);
      topLevelStatementDraft = initializeDraftStatement(t.getChunk(), n);
    }
    // If node is a new basic block, put on basic block stack
    if (isBlockBoundary(n, parent)) {
      blockStack.add(new BasicBlock(peek(blockStack), n));
    }
    return true;
  }

  private TopLevelStatementDraft initializeDraftStatement(JSChunk module, Node statementNode) {
    TopLevelStatementDraft draft =
        new TopLevelStatementDraft(statementCounter++, module, statementNode);
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
        } else if (lhs.isGetProp()) {
          Node nameNode = checkNotNull(lhs.getFirstChild());
          while (nameNode.isGetProp()) {
            nameNode = checkNotNull(nameNode.getFirstChild());
          }
          if (nameNode.isName()) {
            // `varName.some.property = value;`
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

  private static <T> T pop(List<T> list) {
    return list.remove(list.size() - 1);
  }

  private static <T> T peek(List<T> list) {
    return Iterables.getLast(list);
  }

  /**
   * @return true if this node marks the start of a new basic block
   */
  private static boolean isBlockBoundary(Node n, Node parent) {
    if (parent != null) {
      switch (parent.getToken()) {
        case DO:
        case FOR:
        case FOR_IN:
        case FOR_OF:
        case FOR_AWAIT_OF:
        case TRY:
        case WHILE:
        case WITH:
        case CLASS:
          // NOTE: TRY has up to 3 child blocks:
          // TRY
          //   BLOCK
          //   BLOCK
          //     CATCH
          //   BLOCK
          // Note that there is an explicit CATCH token but no explicit
          // FINALLY token. For simplicity, we consider each BLOCK
          // a separate basic BLOCK.
          return true;
        case AND:
        case HOOK:
        case IF:
        case OR:
        case SWITCH:
        case COALESCE:
          // The first child of a conditional is not a boundary,
          // but all the rest of the children are.
          return n != parent.getFirstChild();

        default:
          break;
      }
    }

    return n.isCase();
  }

  private void addReferenceToCollection(Var v, Reference reference) {
    // Create collection if none already
    ReferenceCollection referenceInfo =
        referenceMap.computeIfAbsent(v, (Var k) -> new ReferenceCollection());

    // Add this particular reference
    referenceInfo.add(reference);
  }

  List<TopLevelStatement> getTopLevelStatements() {
    return Collections.unmodifiableList(topLevelStatements);
  }

  /** Determines whether the given value is eligible to be moved across modules. */
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
      case CLASS:
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

      case CALL:
        // In general it is not safe to move function calls, but we carve out an exception
        // for the special stub method calls used for CrossChunkMethodMotion.
        // Case: `JSCompiler_stubMethod(x)`
        Node functionName = checkNotNull(valueNode.getFirstChild());
        return functionName.isName()
            && functionName.getString().equals(CrossChunkMethodMotion.STUB_METHOD_NAME);

      case ARRAYLIT:
        // Movable if all of the array values are movable.
        for (Node child = valueNode.getFirstChild(); child != null; child = child.getNext()) {
          if (!canMoveValue(scope, child)) {
            return false;
          }
        }

        return true;

      case OBJECTLIT:
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

      case NAME:
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
        break;

      case TEMPLATELIT:
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

      default:
        break;
    }

    return false;
  }

  /** Represents a top-level statement and the references to global names it contains. */
  final class TopLevelStatement {

    /** 0-based index indicating original order of this statement in the source. */
    private final int originalOrder;

    private final JSChunk module;
    private final Node statementNode;
    private final List<Reference> nonDeclarationReferences;
    private final Reference declaredNameReference;
    private final Node declaredValueNode;

    TopLevelStatement(TopLevelStatementDraft draft) {
      this.originalOrder = draft.originalOrder;
      this.module = draft.module;
      this.statementNode = draft.statementNode;
      this.nonDeclarationReferences = Collections.unmodifiableList(draft.nonDeclarationReferences);
      this.declaredNameReference = draft.declaredNameReference;
      this.declaredValueNode = draft.declaredValueNode;
    }

    int getOriginalOrder() {
      return originalOrder;
    }

    JSChunk getModule() {
      return module;
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

    final JSChunk module;
    final Node statementNode;
    final List<Reference> nonDeclarationReferences = new ArrayList<>();
    @Nullable Node declaredValueNode = null;
    @Nullable Node declaredNameNode = null;
    @Nullable Reference declaredNameReference = null;

    TopLevelStatementDraft(int originalOrder, JSChunk module, Node statementNode) {
      this.originalOrder = originalOrder;
      this.module = module;
      this.statementNode = statementNode;
    }
  }
}
