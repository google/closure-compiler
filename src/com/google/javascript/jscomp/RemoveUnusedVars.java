/*
 * Copyright 2008 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Garbage collection for variable and function definitions. Basically performs
 * a mark-and-sweep type algorithm over the JavaScript parse tree.
 *
 * For each scope:
 * (1) Scan the variable/function declarations at that scope.
 * (2) Traverse the scope for references, marking all referenced variables.
 *     Unlike other compiler passes, this is a pre-order traversal, not a
 *     post-order traversal.
 * (3) If the traversal encounters an assign without other side-effects,
 *     create a continuation. Continue the continuation iff the assigned
 *     variable is referenced.
 * (4) When the traversal completes, remove all unreferenced variables.
 *
 * If it makes it easier, you can think of the continuations of the traversal
 * as a reference graph. Each continuation represents a set of edges, where the
 * source node is a known variable, and the destination nodes are lazily
 * evaluated when the continuation is executed.
 *
 * This algorithm is similar to the algorithm used by {@code SmartNameRemoval}.
 * {@code SmartNameRemoval} maintains an explicit graph of dependencies
 * between global symbols. However, {@code SmartNameRemoval} cannot handle
 * non-trivial edges in the reference graph ("A is referenced iff both B and C
 * are referenced"), or local variables. {@code SmartNameRemoval} is also
 * substantially more complicated because it tries to handle namespaces
 * (which is largely unnecessary in the presence of {@code CollapseProperties}.
 *
 * This pass also uses a more complex analysis of assignments, where
 * an assignment to a variable or a property of that variable does not
 * necessarily count as a reference to that variable, unless we can prove
 * that it modifies external state. This is similar to
 * {@code FlowSensitiveInlineVariables}, except that it works for variables
 * used across scopes.
 *
 * Multiple datastructures are used to accumulate nodes, some of which are
 * later removed. Since some nodes encompass a subtree of nodes, the removal
 * can sometimes pre-remove other nodes which are also referenced in these
 * datastructures for later removal. Attempting double-removal violates scope
 * change notification constraints so there is a desire to excise
 * already-removed subtree nodes from these datastructures. But not all of the
 * datastructures are conducive to flexible removal and the ones that are
 * conducive don't necessarily track all flavors of nodes. So instead of
 * updating datastructures on the fly a pre-check is performed to skip
 * already-removed nodes right before the moment an attempt to remove them
 * would otherwise be made.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class RemoveUnusedVars implements CompilerPass {

  private final AbstractCompiler compiler;

  private final CodingConvention codingConvention;

  private final boolean removeGlobals;

  private final boolean preserveFunctionExpressionNames;

  /**
   * Used to hold continuations that need to be invoked.
   *
   * When we find a subtree of the AST that may not need to be traversed, we create a Continuation
   * for it. If we later discover that we do need to traverse it, we add it to this worklist
   * rather than traversing it immediately. If we invoked the traversal immediately, we could
   * end up modifying a data structure in the traversal as we're iterating over it.
   */
  private final Deque<Continuation> worklist = new ArrayDeque<>();

  private final Map<Var, VarInfo> varInfoMap = new HashMap<>();

  private final Set<String> referencedPropertyNames = new HashSet<>();

  /**
   * Map from property name to variables on which the property is defined.
   *
   * TODO(bradfordcsmith): Rework to avoid the need for this map.
   */
  private final Multimap<String, VarInfo> varInfoForPropertyNameMap = HashMultimap.create();

  /** Single value to use for all vars for which we cannot remove anything at all. */
  private final VarInfo canonicalTotallyUnremovableVarInfo;

  /**
   * Keep track of scopes that we've traversed.
   */
  private final List<Scope> allFunctionParamScopes = new ArrayList<>();

  private final ScopeCreator scopeCreator;

  // TODO(bradfordcsmith): Make this a constructor option that can be enabled
  private final boolean removeUnusedProperties = false;

  RemoveUnusedVars(
      AbstractCompiler compiler,
      boolean removeGlobals,
      boolean preserveFunctionExpressionNames) {
    this.compiler = compiler;
    this.codingConvention = compiler.getCodingConvention();
    this.removeGlobals = removeGlobals;
    this.preserveFunctionExpressionNames = preserveFunctionExpressionNames;
    this.scopeCreator = new Es6SyntacticScopeCreator(compiler);

    // All Vars that are completely unremovable will share this VarInfo instance.
    canonicalTotallyUnremovableVarInfo = new VarInfo();
    canonicalTotallyUnremovableVarInfo.setCannotRemoveAnything();
  }

  /**
   * Traverses the root, removing all unused variables. Multiple traversals
   * may occur to ensure all unused variables are removed.
   */
  @Override
  public void process(Node externs, Node root) {
    checkState(compiler.getLifeCycleStage().isNormalized());
    traverseAndRemoveUnusedReferences(root);
  }

  /**
   * Traverses a node recursively. Call this once per pass.
   */
  private void traverseAndRemoveUnusedReferences(Node root) {
    // TODO(bradfordcsmith): Include externs in the scope.
    // Since we don't do this now, scope.getVar(someExtern) returns null.
    Scope scope = scopeCreator.createScope(root, null);
    worklist.add(new Continuation(root, scope));
    while (!worklist.isEmpty()) {
      Continuation continuation = worklist.remove();
      continuation.apply();
    }

    removeUnreferencedVars();
    if (removeUnusedProperties) {
      removeUnreferencedProperties();
    }
    for (Scope fparamScope : allFunctionParamScopes) {
      removeUnreferencedFunctionArgs(fparamScope);
    }
  }

  private void removeUnreferencedProperties() {
    for (VarInfo varInfo : varInfoForPropertyNameMap.values()) {
      varInfo.removeUnreferencedProperties();
    }
  }

  /**
   * Traverses everything in the current scope and marks variables that
   * are referenced.
   *
   * During traversal, we identify subtrees that will only be
   * referenced if their enclosing variables are referenced. Instead of
   * traversing those subtrees, we create a continuation for them,
   * and traverse them lazily.
   */
  private void traverseNode(Node n, Scope scope) {
    Node parent = n.getParent();
    Token type = n.getToken();
    Var var = null;
    switch (type) {
      case CATCH:
        traverseCatch(n, scope);
        break;

      case FUNCTION:
        {
          VarInfo varInfo = null;
          // If this function is a removable var, then create a continuation
          // for it instead of traversing immediately.
          if (NodeUtil.isFunctionDeclaration(n)) {
            varInfo = getVarInfo(scope.getVar(n.getFirstChild().getString()));
            FunctionDeclaration functionDeclaration =
                new RemovableBuilder()
                    .addContinuation(new Continuation(n, scope))
                    .buildFunctionDeclaration(n);
            varInfo.addRemovable(functionDeclaration);
            if (parent.isExport()) {
              varInfo.markAsReferenced();
            }
          } else {
            traverseFunction(n, scope);
          }
        }
        break;

      case ASSIGN:
        traverseAssign(n, scope);
        break;

      case CALL:
        traverseCall(n, scope);
        break;

      case BLOCK:
        // This case if for if there are let and const variables in block scopes.
        // Otherwise other variables will be hoisted up into the global scope and already be
        // handled.
        traverseChildren(
            n, NodeUtil.createsBlockScope(n) ? scopeCreator.createScope(n, scope) : scope);
        break;

      case MODULE_BODY:
        traverseChildren(n, scopeCreator.createScope(n, scope));
        break;

      case CLASS:
        traverseClass(n, scope);
        break;

      case DEFAULT_VALUE:
        traverseDefaultValue(n, scope);
        break;

      case REST:
        traverseRest(n, scope);
        break;

      case ARRAY_PATTERN:
        traverseArrayPattern(n, scope);
        break;

      case OBJECT_PATTERN:
        traverseObjectPattern(n, scope);
        break;

      case OBJECTLIT:
        traverseObjectLiteral(n, scope);
        break;

      case FOR:
        traverseVanillaFor(n, scope);
        break;

      case FOR_IN:
      case FOR_OF:
        traverseEnhancedFor(n, scope);
        break;

      case LET:
      case CONST:
      case VAR:
        // for-loop cases are handled by custom traversal methods.
        checkState(NodeUtil.isStatement(n));
        traverseDeclarationStatement(n, scope);
        break;

      case NAME:
        // The only cases that should reach this point are parameter declarations and references
        // to names. The name node does not have children in these cases.
        checkState(!n.hasChildren());
        // the parameter declaration is not a read of the name
        if (!parent.isParamList()) {
          // var|let|const name;
          // are handled at a higher level.
          checkState(!NodeUtil.isNameDeclaration(parent));
          // function name() {}
          // class name() {}
          // handled at a higher level
          checkState(!((parent.isFunction() || parent.isClass()) && parent.getFirstChild() == n));
          var = scope.getVar(n.getString());
          if (var != null) {
            // All name references that aren't handled elsewhere are references to vars.
            getVarInfo(var).markAsReferenced();
          }
        }
        break;

      case GETPROP:
        Node objectNode = n.getFirstChild();
        Node propertyNameNode = objectNode.getNext();
        String propertyName = propertyNameNode.getString();
        markPropertyNameReferenced(propertyName);
        traverseNode(objectNode, scope);
        break;

      default:
        traverseChildren(n, scope);
        break;
    }
  }

  private void traverseCall(Node callNode, Scope scope) {
    Node parent = callNode.getParent();
    String classVarName = null;

    // A call that is a statement unto itself or the left side of a comma expression might be
    // a call to a known method for doing class setup
    // e.g. $jscomp.inherits(Class, BaseClass) or goog.addSingletonGetter(Class)
    // Such methods never have meaningful return values, so we won't look for them in other
    // contexts
    if (parent.isExprResult() || (parent.isComma() && parent.getFirstChild() == callNode)) {
      SubclassRelationship subclassRelationship =
          codingConvention.getClassesDefinedByCall(callNode);
      if (subclassRelationship != null) {
        // e.g. goog.inherits(DerivedClass, BaseClass);
        // NOTE: DerivedClass and BaseClass must be QNames. Otherwise getClassesDefinedByCall() will
        // return null.
        classVarName = subclassRelationship.subclassName;
      } else {
        // Look for calls to addSingletonGetter calls.
        classVarName = codingConvention.getSingletonGetterClassName(callNode);
      }
    }

    Var classVar = (classVarName == null) ? null : scope.getVar(classVarName);

    if (classVar == null || !classVar.isGlobal()) {
      // This isn't one of the special call types, or it isn't acting on a global class name.
      // It would be more correct to only not track when the class name does not
      // reference a constructor, but checking that it is a global is easier and mostly the same.
      traverseChildren(callNode, scope);
    } else {
      VarInfo classVarInfo = getVarInfo(classVar);
      RemovableBuilder builder = new RemovableBuilder();
      for (Node child = callNode.getFirstChild(); child != null; child = child.getNext()) {
        builder.addContinuation(new Continuation(child, scope));
      }
      classVarInfo.addRemovable(builder.buildClassSetupCall(callNode));
    }
  }

  private void traverseRest(Node restNode, Scope scope) {
    Node target = restNode.getOnlyChild();
    if (!target.isName()) {
      traverseNode(target, scope);
    } else {
      Var var = scope.getVar(target.getString());
      if (var != null) {
        VarInfo varInfo = getVarInfo(var);
        // NOTE: DestructuringAssign is currently used for both actual destructuring and
        // default or rest parameters.
        // TODO(bradfordcsmith): Maybe distinguish between these 2 cases.
        varInfo.addRemovable(new RemovableBuilder().buildDestructuringAssign(restNode, target));
      }
    }
  }

  private void traverseObjectLiteral(Node objectLiteral, Scope scope) {
    for (Node propertyNode = objectLiteral.getFirstChild();
        propertyNode != null;
        propertyNode = propertyNode.getNext()) {
      if (propertyNode.isStringKey() && !propertyNode.isQuotedString()) {
        // An unquoted property name in an object literal counts as a reference to that property
        // name, because of some reflection patterns.
        // TODO(bradfordcsmith): Handle this better for `Foo.prototype = {a: 1, b: 2}`
        markPropertyNameReferenced(propertyNode.getString());
        traverseNode(propertyNode.getFirstChild(), scope);
      } else {
        traverseNode(propertyNode, scope);
      }
    }
  }

  private void traverseCatch(Node catchNode, Scope scope) {
    Node exceptionNameNode = catchNode.getFirstChild();
    Node block = exceptionNameNode.getNext();
    VarInfo exceptionVarInfo = getVarInfo(scope.getVar(exceptionNameNode.getString()));
    exceptionVarInfo.setCannotRemoveAnything();
    traverseNode(block, scope);
  }

  private void traverseEnhancedFor(Node enhancedFor, Scope scope) {
    Scope forScope = scopeCreator.createScope(enhancedFor, scope);
    // for (iterationTarget in|of collection) body;
    Node iterationTarget = enhancedFor.getFirstChild();
    Node collection = iterationTarget.getNext();
    Node body = collection.getNext();
    if (iterationTarget.isName()) {
      // using previously-declared loop variable. e.g.
      // `for (varName of collection) {}`
      Var var = forScope.getVar(iterationTarget.getString());
      // NOTE: var will be null if it was declared in externs
      if (var != null) {
        VarInfo varInfo = getVarInfo(var);
        varInfo.setCannotRemoveAnything();
      }
    } else if (NodeUtil.isNameDeclaration(iterationTarget)) {
      // loop has const/var/let declaration
      Node declNode = iterationTarget.getOnlyChild();
      if (declNode.isDestructuringLhs()) {
        // e.g.
        // `for (const [a, b] of pairList) {}`
        // destructuring is handled at a lower level
        // Note that destructuring assignments are always considered to set an unknown value
        // equivalent to what we set for the var name case above and below.
        // It isn't necessary to set the variable names as not removable, though, because the
        // thing that isn't removable is the destructuring pattern itself, which we never remove.
        // TODO(bradfordcsmith): The need to explain all the above shows this should be reworked.
        traverseNode(declNode, forScope);
      } else {
        // e.g.
        // `for (const varName of collection) {}`
        checkState(declNode.isName());
        checkState(!declNode.hasChildren());
        // We can never remove the loop variable of a for-in or for-of loop, because it's
        // essential to loop syntax.
        VarInfo varInfo = getVarInfo(forScope.getVar(declNode.getString()));
        varInfo.setCannotRemoveAnything();
      }
    } else {
      // using some general LHS value e.g.
      // `for ([a, b] of collection) {}` destructuring with existing vars
      // `for (a.x of collection) {}` using a property as the loop var
      // TODO(bradfordcsmith): This should be considered a write if it's a property reference.
      traverseNode(iterationTarget, forScope);
    }
    traverseNode(collection, forScope);
    traverseNode(body, forScope);
  }

  private void traverseVanillaFor(Node forNode, Scope scope) {
    Scope forScope = scopeCreator.createScope(forNode, scope);
    Node initialization = forNode.getFirstChild();
    Node condition = initialization.getNext();
    Node update = condition.getNext();
    Node block = update.getNext();
    if (NodeUtil.isNameDeclaration(initialization)) {
      traverseVanillaForNameDeclarations(initialization, forScope);
    } else {
      traverseNode(initialization, forScope);
    }
    traverseNode(condition, forScope);
    traverseNode(update, forScope);
    traverseNode(block, forScope);
  }

  private void traverseVanillaForNameDeclarations(Node nameDeclaration, Scope scope) {
    for (Node child = nameDeclaration.getFirstChild(); child != null; child = child.getNext()) {
      if (!child.isName()) {
        // TODO(bradfordcsmith): Customize handling of destructuring
        traverseNode(child, scope);
      } else {
        Node nameNode = child;
        @Nullable Node valueNode = child.getFirstChild();
        VarInfo varInfo = getVarInfo(scope.getVar(nameNode.getString()));
        if (valueNode == null) {
          varInfo.addRemovable(new RemovableBuilder().buildVanillaForNameDeclaration(nameNode));
        } else if (NodeUtil.mayHaveSideEffects(valueNode)) {
          // TODO(bradfordcsmith): Actually allow for removing the variable while keeping the
          // valueNode for its side-effects.
          varInfo.setIsExplicitlyNotRemovable();
          traverseNode(valueNode, scope);
        } else {
          VanillaForNameDeclaration vanillaForNameDeclaration =
              new RemovableBuilder()
                  .setAssignedValue(valueNode)
                  .addContinuation(new Continuation(valueNode, scope))
                  .buildVanillaForNameDeclaration(nameNode);
          varInfo.addRemovable(vanillaForNameDeclaration);
        }
      }
    }
  }

  private void traverseDeclarationStatement(Node declarationStatement, Scope scope) {
    // Normalization should ensure that declaration statements always have just one child.
    Node nameNode = declarationStatement.getOnlyChild();
    if (!nameNode.isName()) {
      // TODO(bradfordcsmith): Customize handling of destructuring
      traverseNode(nameNode, scope);
    } else {
      Node valueNode = nameNode.getFirstChild();
      VarInfo varInfo = getVarInfo(checkNotNull(scope.getVar(nameNode.getString())));
      RemovableBuilder builder = new RemovableBuilder();
      if (valueNode == null) {
        varInfo.addRemovable(builder.buildNameDeclarationStatement(declarationStatement));
      } else {
        if (NodeUtil.mayHaveSideEffects(valueNode)) {
          traverseNode(valueNode, scope);
        } else {
          builder.addContinuation(new Continuation(valueNode, scope));
        }
        NameDeclarationStatement removable =
            builder.setAssignedValue(valueNode).buildNameDeclarationStatement(declarationStatement);
        varInfo.addRemovable(removable);
      }
    }
  }

  private void traverseAssign(Node assignNode, Scope scope) {
    checkState(NodeUtil.isAssignmentOp(assignNode));

    Node lhs = assignNode.getFirstChild();
    Node nameNode = null;
    Node propertyNode = null;
    boolean isVariableAssign = false;
    boolean isComputedPropertyAssign = false;
    boolean isNamedPropertyAssign = false;

    if (lhs.isName()) {
      isVariableAssign = true;
      nameNode = lhs;
    } else if (NodeUtil.isGet(lhs)) {
      propertyNode = lhs.getLastChild();
      Node possibleNameNode = lhs.getFirstChild();
      // Handle assignments to properties of a variable or its prototype property.
      // However, don't handle any longer qualified names, because it gets hard to track
      // properties of properties.
      if (possibleNameNode.isGetProp()
          && possibleNameNode.getSecondChild().getString().equals("prototype")) {
        possibleNameNode = possibleNameNode.getFirstChild();
      }
      if (possibleNameNode.isName()) {
        nameNode = possibleNameNode;
        if (lhs.isGetProp()) {
          isNamedPropertyAssign = true;
        } else {
          checkState(lhs.isGetElem());
          isComputedPropertyAssign = true;
        }
      }
    }
    // else LHS is something else, like a destructuring pattern, which will be handled by
    // traverseChildren() below
    // TODO(bradfordcsmith): Handle destructuring at this level for better clarity and so we can
    // do a better job with removal.

    // If we successfully identified a name node & there is a corresponding Var,
    // then we have a removable assignment.
    Var var = (nameNode == null) ? null : scope.getVar(nameNode.getString());
    if (var == null) {
      traverseChildren(assignNode, scope);
    } else {
      Node valueNode = assignNode.getLastChild();
      RemovableBuilder builder = new RemovableBuilder().setAssignedValue(valueNode);
      if (NodeUtil.isExpressionResultUsed(assignNode) || NodeUtil.mayHaveSideEffects(valueNode)) {
        traverseNode(valueNode, scope);
      } else {
        builder.addContinuation(new Continuation(valueNode, scope));
      }

      VarInfo varInfo = getVarInfo(var);
      if (isNamedPropertyAssign) {
        varInfo.addRemovable(builder.buildNamedPropertyAssign(assignNode, nameNode, propertyNode));
      } else if (isVariableAssign) {
        varInfo.addRemovable(builder.buildVariableAssign(assignNode, nameNode));
      } else {
        checkState(isComputedPropertyAssign);
        if (NodeUtil.mayHaveSideEffects(propertyNode)) {
          traverseNode(propertyNode, scope);
        } else {
          builder.addContinuation(new Continuation(propertyNode, scope));
        }
        varInfo.addRemovable(
            builder.buildComputedPropertyAssign(assignNode, nameNode, propertyNode));
      }
    }
  }

  private void traverseDefaultValue(Node defaultValueNode, Scope scope) {
    Var var;
    Node target = defaultValueNode.getFirstChild();
    Node value = target.getNext();
    if (!target.isName()) {
      traverseNode(target, scope);
      traverseNode(value, scope);
    } else {
      var = scope.getVar(target.getString());
      if (var == null) {
        traverseNode(value, scope);
      } else {
        VarInfo varInfo = getVarInfo(var);
        if (NodeUtil.mayHaveSideEffects(value)) {
          // TODO(johnlenz): we don't really need to retain all uses of the variable, just
          //     enough to host the default value assignment.
          varInfo.markAsReferenced();
          traverseNode(value, scope);
        } else {
          DestructuringAssign assign =
              new RemovableBuilder()
                  .addContinuation(new Continuation(value, scope))
                  .buildDestructuringAssign(defaultValueNode, target);
          varInfo.addRemovable(assign);
        }
      }
    }
  }

  private void traverseArrayPattern(Node arrayPattern, Scope scope) {
    for (Node c = arrayPattern.getFirstChild(); c != null; c = c.getNext()) {
      if (!c.isName()) {
        // TODO(bradfordcsmith): Treat destructuring assignments to properties as removable writes.
        traverseNode(c, scope);
      } else {
        Var var = scope.getVar(c.getString());
        if (var != null) {
          VarInfo varInfo = getVarInfo(var);
          varInfo.addRemovable(new RemovableBuilder().buildDestructuringAssign(c, c));
        }
      }
    }
  }

  private void traverseObjectPattern(Node objectPattern, Scope scope) {
    for (Node propertyNode = objectPattern.getFirstChild();
        propertyNode != null;
        propertyNode = propertyNode.getNext()) {
      traverseObjectPatternElement(propertyNode, scope);
    }
  }

  private void traverseObjectPatternElement(Node elm, Scope scope) {
    // non-null for computed properties
    // `{[propertyExpression]: target} = ...`
    Node propertyExpression = null;
    // non-null for named properties
    // `{propertyName: target} = ...`
    String propertyName = null;
    Node target = null;
    Node defaultValue = null;

    // Get correct values for all the variables above.
    if (elm.isComputedProp()) {
      propertyExpression = elm.getFirstChild();
      target = elm.getLastChild();
    } else {
      checkState(elm.isStringKey());
      target = elm.getOnlyChild();
      // Treat `{'a': x} = ...` like `{['a']: x} = ...`, but it never has side-effects and we
      // have no propertyExpression to traverse.
      // NOTE: The parser will convert `{1: x} = ...` to `{'1': x} = ...`
      if (!elm.isQuotedString()) {
        propertyName = elm.getString();
      }
    }

    if (target.isDefaultValue()) {
      target = target.getFirstChild();
      defaultValue = checkNotNull(target.getNext());
    }

    // TODO(bradfordcsmith): Handle property assignments also
    Var var = target.isName() ? scope.getVar(target.getString()) : null;

    // TODO(bradfordcsmith): Arrange to safely remove side-effect cases.
    boolean cannotRemove =
        var == null
            || (propertyExpression != null && NodeUtil.mayHaveSideEffects(propertyExpression))
            || (defaultValue != null && NodeUtil.mayHaveSideEffects(defaultValue));

    if (cannotRemove) {
      if (propertyExpression != null) {
        traverseNode(propertyExpression, scope);
      }
      if (propertyName != null) {
        markPropertyNameReferenced(propertyName);
      }
      traverseNode(target, scope);
      if (defaultValue != null) {
        traverseNode(defaultValue, scope);
      }
      if (var != null) {
        // Since we cannot remove it, we must now treat this usage as a reference.
        getVarInfo(var).markAsReferenced();
      }
    } else {
      RemovableBuilder builder = new RemovableBuilder();
      if (propertyName != null) {
        // TODO(bradfordcsmith): Use a continuation here.
        markPropertyNameReferenced(propertyName);
      }
      if (propertyExpression != null) {
        builder.addContinuation(new Continuation(propertyExpression, scope));
      }
      if (defaultValue != null) {
        builder.addContinuation(new Continuation(defaultValue, scope));
      }
      getVarInfo(var).addRemovable(builder.buildDestructuringAssign(elm, target));
    }
  }

  private void traverseChildren(Node n, Scope scope) {
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      traverseNode(c, scope);
    }
  }

  private void traverseClass(Node classNode, Scope scope) {
    checkArgument(classNode.isClass());
    Node classNameNode = classNode.getFirstChild();
    Node baseClassExpression = classNameNode.getNext();
    Node classBodyNode = baseClassExpression.getNext();
    Scope classScope = scopeCreator.createScope(classNode, scope);
    if (!NodeUtil.isNamedClass(classNode) || classNode.getParent().isExport()) {
      // If this isn't a named class, there's no var to consider here.
      // If it is exported, it definitely cannot be removed.
      traverseNode(baseClassExpression, classScope);
      traverseNode(classBodyNode, classScope);
    } else if (NodeUtil.mayHaveSideEffects(baseClassExpression)) {
      // TODO(bradfordcsmith): implement removal without losing side-effects for this case
      traverseNode(baseClassExpression, classScope);
      traverseNode(classBodyNode, classScope);
    } else {
      RemovableBuilder builder =
          new RemovableBuilder()
              .addContinuation(new Continuation(baseClassExpression, classScope))
              .addContinuation(new Continuation(classBodyNode, classScope));
      VarInfo varInfo = getVarInfo(classScope.getVar(classNameNode.getString()));
      if (NodeUtil.isClassDeclaration(classNode)) {
        varInfo.addRemovable(builder.buildClassDeclaration(classNode));
      } else {
        varInfo.addRemovable(builder.buildNamedClassExpression(classNode));
      }
    }
  }

  /**
   * Traverses a function
   *
   * ES6 scopes of a function include the parameter scope and the body scope
   * of the function.
   *
   * Note that CATCH blocks also create a new scope, but only for the
   * catch variable. Declarations within the block actually belong to the
   * enclosing scope. Because we don't remove catch variables, there's
   * no need to treat CATCH blocks differently like we do functions.
   */
  private void traverseFunction(Node function, Scope parentScope) {
    checkState(function.getChildCount() == 3, function);
    checkState(function.isFunction(), function);

    final Node paramlist = NodeUtil.getFunctionParameters(function);
    final Node body = function.getLastChild();
    checkState(body.getNext() == null && body.isNormalBlock(), body);

    // Checking the parameters
    Scope fparamScope = scopeCreator.createScope(function, parentScope);

    // Checking the function body
    Scope fbodyScope = scopeCreator.createScope(body, fparamScope);

    // for cases like
    // var x = function funcName() {};
    // make sure funcName gets into the varInfoMap so it will be considered for removal.
    getFunctionNameVarInfo(function, fparamScope);

    traverseChildren(paramlist, fparamScope);
    if (NodeUtil.isVarArgsFunction(function)) {
      // if arguments is referenced anywhere, we'll assume we cannot remove any parameters
      for (Node p : NodeUtil.findLhsNodesInNode(paramlist)) {
        Var paramVar = checkNotNull(fparamScope.getOwnSlot(p.getString()));
        getVarInfo(paramVar).markAsReferenced();
      }
    }
    traverseChildren(body, fbodyScope);

    allFunctionParamScopes.add(fparamScope);
  }

  @Nullable
  private VarInfo getFunctionNameVarInfo(Node function, Scope scope) {
    Node nameNode = checkNotNull(function.getFirstChild());
    checkState(nameNode.isName());
    String name = nameNode.getString();
    if (name.isEmpty()) {
      // function() {}
      return null;
    } else {
      // function name() {}
      Var var = checkNotNull(scope.getVar(name));
      return getVarInfo(var);
    }
  }

  private boolean canRemoveParameters(Node parameterList) {
    checkState(parameterList.isParamList());
    Node function = parameterList.getParent();
    return removeGlobals && !NodeUtil.isGetOrSetKey(function.getParent());
  }

  /**
   * Removes unreferenced arguments from a function declaration and when
   * possible the function's callSites.
   *
   * @param fparamScope The function parameter
   */
  private void removeUnreferencedFunctionArgs(Scope fparamScope) {
    // Notice that removing unreferenced function args breaks
    // Function.prototype.length. In advanced mode, we don't really care
    // about this: we consider "length" the equivalent of reflecting on
    // the function's lexical source.
    //
    // Rather than create a new option for this, we assume that if the user
    // is removing globals, then it's OK to remove unused function args.
    //
    // See http://blickly.github.io/closure-compiler-issues/#253
    if (!removeGlobals) {
      return;
    }

    Node function = fparamScope.getRootNode();
    checkState(function.isFunction());
    if (NodeUtil.isGetOrSetKey(function.getParent())) {
      // The parameters object literal setters can not be removed.
      return;
    }

    Node argList = NodeUtil.getFunctionParameters(function);
    // Strip as many unreferenced args off the end of the function declaration as possible.
    maybeRemoveUnusedTrailingParameters(argList, fparamScope);

    // Mark any remaining unused parameters are unused to OptimizeParameters can try to remove
    // them.
    markUnusedParameters(argList, fparamScope);
  }

  private void markPropertyNameReferenced(String propertyName) {
    if (referencedPropertyNames.add(propertyName)) {
      // on first reference, find all vars having that property and tell them it is now referenced.
      for (VarInfo varInfo : varInfoForPropertyNameMap.get(propertyName)) {
        varInfo.markPropertyNameReferenced(propertyName);
      }
    }
  }

  /**
   * Mark any remaining unused parameters as being unused so it can be used elsewhere.
   *
   * @param paramList list of function's parameters
   * @param fparamScope
   */
  private void markUnusedParameters(Node paramList, Scope fparamScope) {
    for (Node param = paramList.getFirstChild(); param != null; param = param.getNext()) {
      if (!param.isUnusedParameter()) {
        Node lValue = param;
        if (lValue.isDefaultValue()) {
          lValue = lValue.getFirstChild();
        }
        if (lValue.isRest()) {
          lValue = lValue.getFirstChild();
        }
        if (lValue.isDestructuringPattern()) {
          continue;
        }
        Var var = fparamScope.getVar(lValue.getString());
        VarInfo varInfo = getVarInfo(var);
        if (varInfo.isRemovable()) {
          param.setUnusedParameter(true);
          compiler.reportChangeToEnclosingScope(paramList);
        }
      }
    }
  }

  /**
   * Strip as many unreferenced args off the end of the function declaration as possible. We start
   * from the end of the function declaration because removing parameters from the middle of the
   * param list could mess up the interpretation of parameters being sent over by any function
   * calls.
   *
   * @param argList list of function's arguments
   * @param fparamScope
   */
  private void maybeRemoveUnusedTrailingParameters(Node argList, Scope fparamScope) {
    Node lastArg;
    while ((lastArg = argList.getLastChild()) != null) {
      Node lValue = lastArg;
      if (lastArg.isDefaultValue()) {
        lValue = lastArg.getFirstChild();
        if (NodeUtil.mayHaveSideEffects(lastArg.getLastChild())) {
          break;
        }
      }

      if (lValue.isRest()) {
        lValue = lValue.getFirstChild();
      }

      if (lValue.isDestructuringPattern()) {
        if (lValue.hasChildren()) {
          // TODO(johnlenz): handle the case where there are no assignments.
          break;
        } else {
          // Remove empty destructuring patterns and their associated object literal assignment
          // if it exists and if the right hand side does not have side effects. Note, a
          // destructuring pattern with a "leftover" property key as in {a:{}} is not considered
          // empty in this case!
          NodeUtil.deleteNode(lastArg, compiler);
          continue;
        }
      }

      Var var = fparamScope.getVar(lValue.getString());
      VarInfo varInfo = getVarInfo(var);
      if (varInfo.isRemovable()) {
        NodeUtil.deleteNode(lastArg, compiler);
      } else {
        break;
      }
    }
  }

  /**
   * Get the right {@link VarInfo} object to use for the given {@link Var}.
   *
   * <p>This method is responsible for managing the entries in {@link #varInfoMap}.
   * <p>Note: Several {@link Var}s may share the same {@link VarInfo} when they should be treated
   * the same way.
   */
  private VarInfo getVarInfo(Var var) {
    checkNotNull(var);
    VarInfo varInfo = varInfoMap.get(var);
    if (varInfo == null) {
      boolean isGlobal = var.isGlobal();
      if (isGlobal && !removeGlobals) {
        // TODO(bradfordcsmith): Should we allow removal of properties here?
        varInfo = canonicalTotallyUnremovableVarInfo;
      } else if (codingConvention.isExported(var.getName(), !isGlobal)) {
        varInfo = canonicalTotallyUnremovableVarInfo;
      } else if (var.isArguments()) {
        // TODO(bradfordcsmith): mark all function parameters unremovable at this point.
        varInfo = canonicalTotallyUnremovableVarInfo;
      } else {
        varInfo = new VarInfo();
        if (var.getParentNode().isParamList()) {
          varInfo.propertyAssignmentsWillPreventRemoval = true;
          varInfo.unreferencedPropertiesMayBeRemoved = false;
        }
        varInfoMap.put(var, varInfo);
      }
    }
    return varInfo;
  }

  /**
   * Removes any vars in the scope that were not referenced. Removes any assignments to those
   * variables as well.
   */
  private void removeUnreferencedVars() {
    for (Entry<Var, VarInfo>entry : varInfoMap.entrySet()) {
      Var var = entry.getKey();
      VarInfo varInfo = entry.getValue();

      if (!varInfo.isRemovable()) {
        continue;
      }

      // Regardless of what happens to the original declaration,
      // we need to remove all assigns, because they may contain references
      // to other unreferenced variables.
      varInfo.removeAllRemovables();

      compiler.addToDebugLog("Unreferenced var: ", var.name);
      Node nameNode = var.nameNode;
      Node toRemove = nameNode.getParent();
      if (toRemove == null || alreadyRemoved(toRemove)) {
        // varInfo.removeAllRemovables () already removed it
      } else if (NodeUtil.isFunctionExpression(toRemove)) {
        // TODO(bradfordcsmith): Add a Removable for this case.
        if (!preserveFunctionExpressionNames) {
          Node fnNameNode = toRemove.getFirstChild();
          compiler.reportChangeToEnclosingScope(fnNameNode);
          fnNameNode.setString("");
        }
      } else if (toRemove.isParamList()) {
        // TODO(bradfordcsmith): handle parameter declarations with removables
        // Don't remove function arguments here. That's a special case
        // that's taken care of in removeUnreferencedFunctionArgs.
      } else {
        throw new IllegalStateException("unremoved code");
      }
    }
  }

  /**
   * Our progress in a traversal can be expressed completely as the
   * current node and scope. The continuation lets us save that
   * information so that we can continue the traversal later.
   */
  private class Continuation {
    private final Node node;
    private final Scope scope;

    Continuation(Node node, Scope scope) {
      this.node = node;
      this.scope = scope;
    }

    void apply() {
      if (node.isFunction()) {
        // Calling traverseNode here would create infinite recursion for a function declaration
        traverseFunction(node, scope);
      } else {
        traverseNode(node, scope);
      }
    }
  }

  /** Represents a portion of the AST that can be removed. */
  private abstract class Removable {

    private final List<Continuation> continuations;
    @Nullable private final String propertyName;
    @Nullable private final Node assignedValue;

    private boolean continuationsAreApplied = false;
    private boolean isRemoved = false;

    Removable(RemovableBuilder builder) {
      continuations = builder.continuations;
      propertyName = builder.propertyName;
      assignedValue = builder.assignedValue;
    }

    String getPropertyName() {
      checkState(isNamedPropertyAssignment());
      return checkNotNull(propertyName);
    }

    /** Remove the associated nodes from the AST. */
    abstract void removeInternal(AbstractCompiler compiler);

    /** Remove the associated nodes from the AST, unless they've already been removed. */
    void remove(AbstractCompiler compiler) {
      if (!isRemoved) {
        isRemoved = true;
        removeInternal(compiler);
      }
    }

    public void applyContinuations() {
      if (!continuationsAreApplied) {
        continuationsAreApplied = true;
        for (Continuation c : continuations) {
          // Enqueue the continuation for processing.
          // Don't invoke the continuation immediately, because that can lead to concurrent
          // modification of data structures.
          worklist.add(c);
        }
        continuations.clear();
      }
    }

    boolean isLiteralValueAssignment() {
      // An assigned value of null occurs for a name declaration when no initializer is given.
      // It is the same as assigning `undefined`, so it is a literal value.
      return assignedValue == null
          || NodeUtil.isLiteralValue(assignedValue, /* includeFunctions */ true);
    }

    /** True if this object represents assignment to a variable. */
    boolean isVariableAssignment() {
      return false;
    }

    /** True if this object represents assignment of a value to a property. */
    boolean isPropertyAssignment() {
      return false;
    }

    /** True if this object represents assignment to a named property. */
    boolean isNamedPropertyAssignment() {
      return propertyName != null;
    }

    /**
     * True if this object has an assigned value that may escape to another context through aliasing
     * or some other means.
     */
    boolean assignedValueMayEscape() {
      return false;
    }

    boolean isPrototypeAssignment() {
      return isNamedPropertyAssignment() && propertyName.equals("prototype");
    }
  }

  private class RemovableBuilder {
    final List<Continuation> continuations = new ArrayList<>();

    @Nullable String propertyName = null;
    @Nullable public Node assignedValue = null;

    RemovableBuilder addContinuation(Continuation continuation) {
      continuations.add(continuation);
      return this;
    }

    RemovableBuilder setAssignedValue(@Nullable Node assignedValue) {
      this.assignedValue = assignedValue;
      return this;
    }

    DestructuringAssign buildDestructuringAssign(Node removableNode, Node nameNode) {
      return new DestructuringAssign(this, removableNode, nameNode);
    }

    ClassDeclaration buildClassDeclaration(Node classNode) {
      return new ClassDeclaration(this, classNode);
    }

    NamedClassExpression buildNamedClassExpression(Node classNode) {
      return new NamedClassExpression(this, classNode);
    }

    FunctionDeclaration buildFunctionDeclaration(Node functionNode) {
      return new FunctionDeclaration(this, functionNode);
    }

    NameDeclarationStatement buildNameDeclarationStatement(Node declarationStatement) {
      return new NameDeclarationStatement(this, declarationStatement);
    }

    Assign buildNamedPropertyAssign(Node assignNode, Node nameNode, Node propertyNode) {
      this.propertyName = propertyNode.getString();
      checkNotNull(assignedValue);
      return new Assign(this, assignNode, nameNode, Kind.NAMED_PROPERTY, propertyNode);
    }

    Assign buildComputedPropertyAssign(Node assignNode, Node nameNode, Node propertyNode) {
      checkNotNull(assignedValue);
      return new Assign(this, assignNode, nameNode, Kind.COMPUTED_PROPERTY, propertyNode);
    }

    Assign buildVariableAssign(Node assignNode, Node nameNode) {
      return new Assign(this, assignNode, nameNode, Kind.VARIABLE, /* propertyNode */ null);
    }

    ClassSetupCall buildClassSetupCall(Node callNode) {
      return new ClassSetupCall(this, callNode);
    }

    VanillaForNameDeclaration buildVanillaForNameDeclaration(Node nameNode) {
      return new VanillaForNameDeclaration(this, nameNode);
    }
  }

  private class DestructuringAssign extends Removable {
    final Node removableNode;
    final Node nameNode;

    DestructuringAssign(RemovableBuilder builder, Node removableNode, Node nameNode) {
      super(builder);
      checkState(nameNode.isName());
      this.removableNode = removableNode;
      this.nameNode = nameNode;

      Node parent = nameNode.getParent();
      if (parent.isDefaultValue()) {
        checkState(!NodeUtil.mayHaveSideEffects(parent.getLastChild()));
      }
    }

    @Override
    boolean isVariableAssignment() {
      // TODO(bradfordcsmith): Handle destructuring assignments to properties.
      return true;
    }

    @Override
    boolean isLiteralValueAssignment() {
      // TODO(bradfordcsmith): Determine assigned value when possible.
      // We don't look at the rhs of destructuring assignments at all right now,
      // so assume they always assign some non-literal value.
      return false;
    }

    @Override
    public void removeInternal(AbstractCompiler compiler) {
      if (alreadyRemoved(removableNode)) {
        return;
      }
      Node removableParent = removableNode.getParent();
      if (removableParent.isArrayPattern()) {
        // [a, removableName, b] = something;
        // [a, ...removableName] = something;
        // [a, removableName = removableValue, b] = something;
        // [a, ...removableName = removableValue] = something;
        compiler.reportChangeToEnclosingScope(removableParent);
        if (removableNode == removableParent.getLastChild()) {
          removableNode.detach();
        } else {
          removableNode.replaceWith(IR.empty().srcref(removableNode));
        }
        // We prefer `[a, b]` to `[a, b, , , , ]`
        // So remove any trailing empty nodes.
        for (Node maybeEmpty = removableParent.getLastChild();
            maybeEmpty != null && maybeEmpty.isEmpty();
            maybeEmpty = removableParent.getLastChild()) {
          maybeEmpty.detach();
        }
        NodeUtil.markFunctionsDeleted(removableNode, compiler);
      } else if (removableParent.isParamList() && removableNode.isDefaultValue()) {
        // function(removableName = removableValue)
        compiler.reportChangeToEnclosingScope(removableNode);
        // preserve the slot in the parameter list
        Node name = removableNode.getFirstChild();
        checkState(name.isName());
        if (removableNode == removableParent.getLastChild()
            && removeGlobals
            && canRemoveParameters(removableParent)) {
          // function(p1, removableName = removableDefault)
          // and we're allowed to remove the parameter entirely
          removableNode.detach();
        } else {
          // function(removableName = removableDefault, otherParam)
          // or removableName is at the end, but cannot be completely removed.
          removableNode.replaceWith(name.detach());
        }
        NodeUtil.markFunctionsDeleted(removableNode, compiler);
      } else if (removableNode.isDefaultValue()) {
        // { a: removableName = removableValue }
        // { [removableExpression]: removableName = removableValue }
        checkState(
            removableParent.isStringKey()
                || (removableParent.isComputedProp()
                    && !NodeUtil.mayHaveSideEffects(removableParent.getFirstChild())));
        // Remove the whole property, not just its default value part.
        NodeUtil.deleteNode(removableParent, compiler);
      } else {
        // { removableStringKey: removableName }
        // function(...removableName) {}
        // function(...removableName = default)
        checkState(
            removableParent.isObjectPattern()
                || (removableParent.isParamList() && removableNode.isRest()));
        NodeUtil.deleteNode(removableNode, compiler);
      }
    }
  }

  private class ClassDeclaration extends Removable {
    final Node classDeclarationNode;

    ClassDeclaration(RemovableBuilder builder, Node classDeclarationNode) {
      super(builder);
      this.classDeclarationNode = classDeclarationNode;
    }

    @Override
    public void removeInternal(AbstractCompiler compiler) {
      NodeUtil.deleteNode(classDeclarationNode, compiler);
    }
  }

  private class NamedClassExpression extends Removable {
    final Node classNode;

    NamedClassExpression(RemovableBuilder builder, Node classNode) {
      super(builder);
      this.classNode = classNode;
    }

    @Override
    public void removeInternal(AbstractCompiler compiler) {
      if (!alreadyRemoved(classNode)) {
        Node nameNode = classNode.getFirstChild();
        if (!nameNode.isEmpty()) {
          // Just empty the class's name. If the expression is assigned to an unused variable,
          // then the whole class might still be removed as part of that assignment.
          classNode.replaceChild(nameNode, IR.empty().useSourceInfoFrom(nameNode));
          compiler.reportChangeToEnclosingScope(classNode);
        }
      }
    }
  }

  private class FunctionDeclaration extends Removable {
    final Node functionDeclarationNode;

    FunctionDeclaration(RemovableBuilder builder, Node functionDeclarationNode) {
      super(builder);
      this.functionDeclarationNode = functionDeclarationNode;
    }

    @Override
    public void removeInternal(AbstractCompiler compiler) {
      NodeUtil.deleteNode(functionDeclarationNode, compiler);
    }
  }

  private class NameDeclarationStatement extends Removable {
    private final Node declarationStatement;

    public NameDeclarationStatement(RemovableBuilder builder, Node declarationStatement) {
      super(builder);
      this.declarationStatement = declarationStatement;
    }

    @Override
    void removeInternal(AbstractCompiler compiler) {
      Node nameNode = declarationStatement.getOnlyChild();
      Node valueNode = nameNode.getFirstChild();
      if (valueNode != null && NodeUtil.mayHaveSideEffects(valueNode)) {
        compiler.reportChangeToEnclosingScope(declarationStatement);
        valueNode.detach();
        declarationStatement.replaceWith(IR.exprResult(valueNode).useSourceInfoFrom(valueNode));
      } else {
        NodeUtil.deleteNode(declarationStatement, compiler);
      }
    }

    @Override
    boolean isVariableAssignment() {
      return true;
    }

  }

  enum Kind {
    // X = something;
    VARIABLE,
    // X.propertyName = something;
    // X.prototype.propertyName = something;
    NAMED_PROPERTY,
    // X[expression] = something;
    // X.prototype[expression] = something;
    COMPUTED_PROPERTY;
  }

  private class Assign extends Removable {

    final Node assignNode;
    final Node nameNode;
    final Kind kind;

    @Nullable final Node propertyNode;

    // If true, the value may have escaped and any modification is a use.
    final boolean maybeAliased;

    Assign(
        RemovableBuilder builder,
        Node assignNode,
        Node nameNode,
        Kind kind,
        @Nullable Node propertyNode) {
      super(builder);
      checkState(NodeUtil.isAssignmentOp(assignNode));
      if (kind == Kind.VARIABLE) {
        checkArgument(
            propertyNode == null,
            "got property node for simple variable assignment: %s",
            propertyNode);
      } else {
        checkArgument(propertyNode != null, "missing property node");
        if (kind == Kind.NAMED_PROPERTY) {
          checkArgument(propertyNode.isString(), "property name is not a string: %s", propertyNode);
        }
      }
      this.assignNode = assignNode;
      this.nameNode = nameNode;
      this.kind = kind;
      this.propertyNode = propertyNode;

      this.maybeAliased = NodeUtil.isExpressionResultUsed(assignNode);
    }

    @Override
    boolean assignedValueMayEscape() {
      return maybeAliased;
    }

    /** True for `varName = value` assignments. */
    @Override
    boolean isVariableAssignment() {
      return kind == Kind.VARIABLE;
    }

    @Override
    boolean isPropertyAssignment() {
      return isNamedPropertyAssignment() || isComputedPropertyAssignment();
    }

    /** True for `varName.propName = value` and `varName.prototype.propName = value` assignments. */
    @Override
    boolean isNamedPropertyAssignment() {
      return kind == Kind.NAMED_PROPERTY;
    }

    /** True for `varName[expr] = value` and `varName.prototype[expr] = value` assignments. */
    boolean isComputedPropertyAssignment() {
      return kind == Kind.COMPUTED_PROPERTY;
    }

    /** Replace the current assign with its right hand side. */
    @Override
    public void removeInternal(AbstractCompiler compiler) {
      if (alreadyRemoved(assignNode)) {
        return;
      }
      Node parent = assignNode.getParent();
      compiler.reportChangeToEnclosingScope(parent);
      Node lhs = assignNode.getFirstChild();
      Node rhs = assignNode.getSecondChild();
      boolean mustPreserveRhs =
          NodeUtil.mayHaveSideEffects(rhs) || NodeUtil.isExpressionResultUsed(assignNode);
      boolean mustPreserveGetElmExpr =
          lhs.isGetElem() && NodeUtil.mayHaveSideEffects(lhs.getLastChild());

      if (mustPreserveRhs && mustPreserveGetElmExpr) {
        Node replacement =
            IR.comma(lhs.getLastChild().detach(), rhs.detach()).useSourceInfoFrom(assignNode);
        assignNode.replaceWith(replacement);
      } else if (mustPreserveGetElmExpr) {
        assignNode.replaceWith(lhs.getLastChild().detach());
        NodeUtil.markFunctionsDeleted(rhs, compiler);
      } else if (mustPreserveRhs) {
        assignNode.replaceWith(rhs.detach());
        NodeUtil.markFunctionsDeleted(lhs, compiler);
      } else if (parent.isExprResult()) {
        parent.detach();
        NodeUtil.markFunctionsDeleted(parent, compiler);
      } else {
        // value isn't needed, but we need to keep the AST valid.
        assignNode.replaceWith(IR.number(0).useSourceInfoFrom(assignNode));
        NodeUtil.markFunctionsDeleted(assignNode, compiler);
      }
    }
  }

  /**
   * Represents a call to a class setup method such as `goog.inherits()` or
   * `goog.addSingletonGetter()`.
   */
  private class ClassSetupCall extends Removable {

    final Node callNode;

    ClassSetupCall(RemovableBuilder builder, Node callNode) {
      super(builder);
      this.callNode = callNode;
    }

    @Override
    public void removeInternal(AbstractCompiler compiler) {
      Node parent = callNode.getParent();
      // NOTE: The call must either be its own statement or the LHS of a comma expression,
      // because it doesn't have a meaningful return value.
      if (parent.isExprResult()) {
        NodeUtil.deleteNode(parent, compiler);
      } else {
        // `(goog.inherits(A, B), something)` -> `something`
        checkState(parent.isComma());
        Node rhs = checkNotNull(callNode.getNext());
        compiler.reportChangeToEnclosingScope(parent);
        parent.replaceWith(rhs.detach());
      }
    }
  }

  private static boolean alreadyRemoved(Node n) {
    Node parent = n.getParent();
    if (parent == null) {
      return true;
    }
    if (parent.isRoot()) {
      return false;
    }
    return alreadyRemoved(parent);
  }

  private class VarInfo {
    /**
     * Objects that represent variable declarations, assignments, or class setup calls that can
     * be removed.
     *
     * NOTE: Once we realize that we cannot remove the variable, this list will be cleared and
     * no more will be added.
     */
    final List<Removable> removables = new ArrayList<>();

    /**
     * Objects that represent assignments to named properties on the variable or on
     * `varName.prototype`. These can be considered for removal even if the variable itself
     * cannot.
     *
     * NOTE: Once we realize that we cannot remove a property, the removables for that property
     * will be dropped and no more will be added.
     */
    Multimap<String, Removable> namedPropertyRemovables = null;

    boolean isEntirelyRemovable = true;

    /**
     * Is it OK to remove properties that appear unused when we are leaving the variable itself in
     * place?
     *
     * <p>Defaults to true if we allow this behavior at all.
     * Once set to false, it will not be changed back to true.
     */
    boolean unreferencedPropertiesMayBeRemoved = removeUnusedProperties;

    /**
     * Used along with hasPropertyAssignments to handle cases where property assignment may have
     * an unknown side-effect, and so make it unsafe to remove the variable.
     *
     * If we're unsure where the variable's value comes from, then setting a property on it may
     * have a side-effect we cannot easily detect.
     */
    boolean propertyAssignmentsWillPreventRemoval = false;

    /**
     * Records whether any properties are set on the variable.
     *
     * This includes both named properties (`x.propName =`) and computed ones (`x[expr] = `).
     * It is used in combination with propertyAssignmentsWillPreventRemoval.
     */
    boolean hasPropertyAssignments = false;

    void addRemovable(Removable removable) {
      // determine how this removable affects removability
      if (removable.isPropertyAssignment()) {
        hasPropertyAssignments = true;
        if (removable.isPrototypeAssignment() && removable.assignedValueMayEscape()) {
          // Assignment to properties could have unexpected side-effects.
          // x = varName.prototype = {};
          // foo(varName.prototype = {});
          // NOTE: Arguably we should also check for literal value assignment, but that would
          // prevent us from removing cases like this one.
          // Foo.prototype = {
          //     constructor: Foo, // not considered a literal value.
          //     ...
          // };
          propertyAssignmentsWillPreventRemoval = true;
        }
        if (propertyAssignmentsWillPreventRemoval) {
          setIsExplicitlyNotRemovable();
        }
      } else if (removable.isVariableAssignment()
          && (removable.assignedValueMayEscape() || !removable.isLiteralValueAssignment())) {
        // Assignment to properties could have unexpected side-effects.
        // x = varName = {};
        // foo(varName = {});
        // varName = foo();
        propertyAssignmentsWillPreventRemoval = true;
        if (hasPropertyAssignments) {
          setIsExplicitlyNotRemovable();
        }
      }

      // immediately apply continuations, or save the removable for possible removal
      if (removable.isNamedPropertyAssignment()) {
        String propertyName = removable.getPropertyName();

        if (isPropertyRemovable(propertyName)) {
          if (namedPropertyRemovables == null) {
            namedPropertyRemovables = HashMultimap.create();
          }
          namedPropertyRemovables.put(propertyName, removable);
          varInfoForPropertyNameMap.put(propertyName, this);
        } else {
          removable.applyContinuations();
        }
      } else if (isEntirelyRemovable) {
        removables.add(removable);
      } else {
        removable.applyContinuations();
      }
    }

    /**
     * Marks this variable as referenced and evaluates any continuations if not previously marked as
     * referenced.
     *
     * @return true if the variable was not already marked as referenced
     */
    boolean markAsReferenced() {
      return setIsExplicitlyNotRemovable();
    }

    void markPropertyNameReferenced(String propertyName) {
      // Only apply continuations and drop the removals for the name if we've decided we cannot
      // remove this variable entirely.
      if (!isEntirelyRemovable && namedPropertyRemovables != null) {
        for (Removable r : namedPropertyRemovables.removeAll(propertyName)) {
          r.applyContinuations();
        }
      }
    }

    boolean isRemovable() {
      return isEntirelyRemovable;
    }

    /**
     * Do not remove this variable or any of its property assignments.
     */
    void setCannotRemoveAnything() {
      unreferencedPropertiesMayBeRemoved = false;
      setIsExplicitlyNotRemovable();
    }

    boolean isPropertyRemovable(String propertyName) {
      return isEntirelyRemovable
          || (unreferencedPropertiesMayBeRemoved
              && !referencedPropertyNames.contains(propertyName));
    }

    boolean setIsExplicitlyNotRemovable() {
      if (isEntirelyRemovable) {
        isEntirelyRemovable = false;
        for (Removable r : removables) {
          r.applyContinuations();
        }
        removables.clear();
        if (namedPropertyRemovables != null) {
          // iterate over a copy to avoid ConcurrentModificationException when we remove keys
          // within the loop
          for (String propertyName : ImmutableList.copyOf(namedPropertyRemovables.keySet())) {
            if (!isPropertyRemovable(propertyName)) {
              for (Removable r : namedPropertyRemovables.removeAll(propertyName)) {
                r.applyContinuations();
              }
            }
          }
        }
        return true;
      } else {
        return false;
      }
    }

    void removeAllRemovables() {
      checkState(isEntirelyRemovable);
      for (Removable removable : removables) {
        removable.remove(compiler);
      }
      removables.clear();
      if (namedPropertyRemovables != null) {
        for (Removable removable : namedPropertyRemovables.values()) {
          removable.remove(compiler);
        }
        namedPropertyRemovables.clear();
        namedPropertyRemovables = null;
      }
    }

    void removeUnreferencedProperties() {
      checkState(!isEntirelyRemovable && unreferencedPropertiesMayBeRemoved);
      if (namedPropertyRemovables != null) {
        // iterate over a copy to avoid ConcurrentModificationException when we remove keys
        // within the loop
        for (String propertyName : ImmutableList.copyOf(namedPropertyRemovables.keySet())) {
          // There shouldn't be any entries in namedPropertyRemovables for properties we know are
          // referenced.
          checkState(!referencedPropertyNames.contains(propertyName));
          for (Removable r : namedPropertyRemovables.removeAll(propertyName)) {
            r.remove(compiler);
          }
        }
      }
    }
  }

  /**
   * Represents declarations in the standard for-loop initialization.
   *
   * e.g. the `let i = 0` part of `for (let i = 0; i < 10; ++i) {...}`.
   * These must be handled differently from declaration statements because:
   *
   * <ol>
   *   <li>
   *     For-loop declarations may declare more than one variable.
   *     The normalization doesn't break them up as it does for declaration statements.
   *   </li>
   *   <li>
   *     Removal must be handled differently.
   *   </li>
   *   <li>
   *     We don't currently preserve initializers with side effects here.
   *     Instead, we just consider such cases non-removable.
   *   </li>
   * </ol>
   */
  private class VanillaForNameDeclaration extends Removable {

    private final Node nameNode;

    private VanillaForNameDeclaration(RemovableBuilder builder, Node nameNode) {
      super(builder);
      this.nameNode = nameNode;
    }

    @Override
    void removeInternal(AbstractCompiler compiler) {
      Node declaration = checkNotNull(nameNode.getParent());
      compiler.reportChangeToEnclosingScope(declaration);
      // NOTE: We don't need to preserve the initializer value, because we currently do not remove
      //     for-loop vars whose initializing values have side effects.
      if (nameNode.getPrevious() == null && nameNode.getNext() == null) {
        // only child, so we can remove the whole declaration
        declaration.replaceWith(IR.empty().useSourceInfoFrom(declaration));
      } else {
        declaration.removeChild(nameNode);
      }
      NodeUtil.markFunctionsDeleted(nameNode, compiler);
    }

  }
}
