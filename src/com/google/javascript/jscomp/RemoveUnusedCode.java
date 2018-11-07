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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
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
class RemoveUnusedCode implements CompilerPass {

  // Properties that are implicitly used as part of the JS language.
  private static final ImmutableSet<String> IMPLICITLY_USED_PROPERTIES =
      ImmutableSet.of("length", "toString", "valueOf", "constructor");

  private final AbstractCompiler compiler;

  private final CodingConvention codingConvention;

  private final boolean removeLocalVars;
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

  private final Set<String> referencedPropertyNames = new HashSet<>(IMPLICITLY_USED_PROPERTIES);

  /** Stores Removable objects for each property name that is currently considered removable. */
  private final Multimap<String, Removable> removablesForPropertyNames = HashMultimap.create();

  /** Single value to use for all vars for which we cannot remove anything at all. */
  private final VarInfo canonicalUnremovableVarInfo;

  /**
   * Keep track of scopes that we've traversed.
   */
  private final List<Scope> allFunctionParamScopes = new ArrayList<>();

  private final Es6SyntacticScopeCreator scopeCreator;

  private final boolean removeUnusedPrototypeProperties;
  private final boolean allowRemovalOfExternProperties;
  private final boolean removeUnusedThisProperties;
  private final boolean removeUnusedStaticProperties;
  private final boolean removeUnusedObjectDefinePropertiesDefinitions;

  RemoveUnusedCode(Builder builder) {
    this.compiler = builder.compiler;
    this.codingConvention = builder.compiler.getCodingConvention();
    this.removeLocalVars = builder.removeLocalVars;
    this.removeGlobals = builder.removeGlobals;
    this.preserveFunctionExpressionNames = builder.preserveFunctionExpressionNames;
    this.removeUnusedPrototypeProperties = builder.removeUnusedPrototypeProperties;
    this.allowRemovalOfExternProperties = builder.allowRemovalOfExternProperties;
    this.removeUnusedThisProperties = builder.removeUnusedThisProperties;
    this.removeUnusedStaticProperties = builder.removeUnusedStaticProperties;
    this.removeUnusedObjectDefinePropertiesDefinitions =
        builder.removeUnusedObjectDefinePropertiesDefinitions;
    this.scopeCreator = new Es6SyntacticScopeCreator(builder.compiler);

    // All Vars that are completely unremovable will share this VarInfo instance.
    canonicalUnremovableVarInfo = new VarInfo();
    canonicalUnremovableVarInfo.setIsExplicitlyNotRemovable();
  }

  public static class Builder {
    private final AbstractCompiler compiler;

    private boolean removeLocalVars = false;
    private boolean removeGlobals = false;
    private boolean preserveFunctionExpressionNames = false;
    private boolean removeUnusedPrototypeProperties = false;
    private boolean allowRemovalOfExternProperties = false;
    private boolean removeUnusedThisProperties = false;
    private boolean removeUnusedStaticProperties = false;
    private boolean removeUnusedObjectDefinePropertiesDefinitions = false;

    Builder(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    Builder removeLocalVars(boolean value) {
      this.removeLocalVars = value;
      return this;
    }

    Builder removeGlobals(boolean value) {
      this.removeGlobals = value;
      return this;
    }

    Builder preserveFunctionExpressionNames(boolean value) {
      this.preserveFunctionExpressionNames = value;
      return this;
    }

    Builder removeUnusedPrototypeProperties(boolean value) {
      this.removeUnusedPrototypeProperties = value;
      return this;
    }

    Builder allowRemovalOfExternProperties(boolean value) {
      this.allowRemovalOfExternProperties = value;
      return this;
    }

    Builder removeUnusedThisProperties(boolean value) {
      this.removeUnusedThisProperties = value;
      return this;
    }

    Builder removeUnusedConstructorProperties(boolean value) {
      this.removeUnusedStaticProperties = value;
      return this;
    }

    Builder removeUnusedObjectDefinePropertiesDefinitions(boolean value) {
      this.removeUnusedObjectDefinePropertiesDefinitions = value;
      return this;
    }

    RemoveUnusedCode build() {
      return new RemoveUnusedCode(this);
    }
  }

  /**
   * Traverses the root, removing all unused variables. Multiple traversals
   * may occur to ensure all unused variables are removed.
   */
  @Override
  public void process(Node externs, Node root) {
    checkState(compiler.getLifeCycleStage().isNormalized());
    if (!allowRemovalOfExternProperties) {
      referencedPropertyNames.addAll(compiler.getExternProperties());
    }
    traverseAndRemoveUnusedReferences(root);
    // This pass may remove definitions of getter or setter properties.
    GatherGettersAndSetterProperties.update(compiler, externs, root);
  }

  /**
   * Traverses a node recursively. Call this once per pass.
   */
  private void traverseAndRemoveUnusedReferences(Node root) {
    // Create scope from parent of root node, which also has externs as a child, so we'll
    // have extern definitions in scope.
    Scope scope = scopeCreator.createScope(root.getParent(), null);
    if (!scope.hasSlot(NodeUtil.JSC_PROPERTY_NAME_FN)) {
      // TODO(b/70730762): Passes that add references to this should ensure it is declared.
      // NOTE: null input makes this an extern var.
      scope.declare(
          NodeUtil.JSC_PROPERTY_NAME_FN, /* no declaration node */ null, /* no input */ null);
    }
    worklist.add(new Continuation(root, scope));
    while (!worklist.isEmpty()) {
      Continuation continuation = worklist.remove();
      continuation.apply();
    }

    removeUnreferencedVars();
    removeIndependentlyRemovableProperties();
    for (Scope fparamScope : allFunctionParamScopes) {
      removeUnreferencedFunctionArgs(fparamScope);
    }
  }

  private void removeIndependentlyRemovableProperties() {
    for (Removable removable : removablesForPropertyNames.values()) {
      removable.remove(compiler);
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
            varInfo = traverseNameNode(n.getFirstChild(), scope);
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

      case ASSIGN_BITOR:
      case ASSIGN_BITXOR:
      case ASSIGN_BITAND:
      case ASSIGN_LSH:
      case ASSIGN_RSH:
      case ASSIGN_URSH:
      case ASSIGN_ADD:
      case ASSIGN_SUB:
      case ASSIGN_MUL:
      case ASSIGN_EXPONENT:
      case ASSIGN_DIV:
      case ASSIGN_MOD:
        traverseCompoundAssign(n, scope);
        break;

      case INC:
      case DEC:
        traverseIncrementOrDecrementOp(n, scope);
        break;

      case CALL:
        traverseCall(n, scope);
        break;

      case SWITCH:
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

      case CLASS_MEMBERS:
        traverseClassMembers(n, scope);
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

      case INSTANCEOF:
        traverseInstanceof(n, scope);
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
          traverseNameNode(n, scope).markAsReferenced();
        }
        break;

      case GETPROP:
        traverseGetProp(n, scope);
        break;

      default:
        traverseChildren(n, scope);
        break;
    }
  }

  private void traverseInstanceof(Node instanceofNode, Scope scope) {
    checkArgument(instanceofNode.isInstanceOf(), instanceofNode);
    Node lhs = instanceofNode.getFirstChild();
    Node rhs = lhs.getNext();
    traverseNode(lhs, scope);
    if (rhs.isName()) {
      VarInfo varInfo = traverseNameNode(rhs, scope);
      RemovableBuilder builder = new RemovableBuilder();
      varInfo.addRemovable(builder.buildInstanceofName(instanceofNode));
    } else {
      traverseNode(rhs, scope);
    }
  }

  private void traverseGetProp(Node getProp, Scope scope) {
    Node objectNode = getProp.getFirstChild();
    Node propertyNameNode = objectNode.getNext();
    String propertyName = propertyNameNode.getString();

    if (NodeUtil.isExpressionResultUsed(getProp)) {
      // must record as reference to the property and continue traversal.
      markPropertyNameReferenced(propertyName);
      traverseNode(objectNode, scope);
    } else if (objectNode.isThis()) {
      // this.propName;
      RemovableBuilder builder = new RemovableBuilder().setIsThisDotPropertyReference(true);
      considerForIndependentRemoval(builder.buildUnusedReadReference(getProp, propertyNameNode));
    } else if (isDotPrototype(objectNode)) {
      // (objExpression).prototype.propName;
      RemovableBuilder builder = new RemovableBuilder().setIsPrototypeDotPropertyReference(true);
      Node objExpression = objectNode.getFirstChild();
      if (objExpression.isName()) {
        // name.prototype.propName;
        VarInfo varInfo = traverseNameNode(objExpression, scope);
        varInfo.addRemovable(builder.buildUnusedReadReference(getProp, propertyNameNode));
      } else {
        // (objExpression).prototype.propName;
        if (NodeUtil.mayHaveSideEffects(objExpression)) {
          traverseNode(objExpression, scope);
        } else {
          builder.addContinuation(new Continuation(objExpression, scope));
        }
        considerForIndependentRemoval(builder.buildUnusedReadReference(getProp, propertyNameNode));
      }
    } else {
      // TODO(bradfordcsmith): add removal of `varName.propName;`
      markPropertyNameReferenced(propertyName);
      traverseNode(objectNode, scope);
    }
  }

  private void traverseIncrementOrDecrementOp(Node incOrDecOp, Scope scope) {
    checkArgument(incOrDecOp.isInc() || incOrDecOp.isDec(), incOrDecOp);
    Node arg = incOrDecOp.getOnlyChild();
    if (NodeUtil.isExpressionResultUsed(incOrDecOp)) {
      // If expression result is used, then this expression is definitely not removable.
      traverseNode(arg, scope);
    } else if (arg.isGetProp()) {
      Node getPropObj = arg.getFirstChild();
      Node propertyNameNode = arg.getLastChild();
      if (getPropObj.isThis()) {
        // this.propName++
        RemovableBuilder builder = new RemovableBuilder().setIsThisDotPropertyReference(true);
        considerForIndependentRemoval(builder.buildIncOrDepOp(incOrDecOp, propertyNameNode));
      } else if (isDotPrototype(getPropObj)) {
        // someExpression.prototype.propName++
        Node exprObj = getPropObj.getFirstChild();
        RemovableBuilder builder = new RemovableBuilder().setIsPrototypeDotPropertyReference(true);
        if (exprObj.isName()) {
          // varName.prototype.propName++
          VarInfo varInfo = traverseNameNode(exprObj, scope);
          varInfo.addRemovable(builder.buildIncOrDepOp(incOrDecOp, propertyNameNode));
        } else {
          // (someExpression).prototype.propName++
          if (NodeUtil.mayHaveSideEffects(exprObj)) {
            traverseNode(exprObj, scope);
          } else {
            builder.addContinuation(new Continuation(exprObj, scope));
          }
          considerForIndependentRemoval(builder.buildIncOrDepOp(incOrDecOp, propertyNameNode));
        }
      } else {
        // someExpression.propName++ is not removable except in the cases covered above
        traverseNode(arg, scope);
      }
    } else {
      // TODO(bradfordcsmith): varName++ should be removable if varName is otherwise unused
      traverseNode(arg, scope);
    }
  }

  private void traverseCompoundAssign(Node compoundAssignNode, Scope scope) {
    // We'll allow removal of compound assignment to a this property as long as the result of the
    // assignment is unused.
    // e.g. `this.x += 3;`
    Node targetNode = compoundAssignNode.getFirstChild();
    Node valueNode = compoundAssignNode.getLastChild();
    if (targetNode.isGetProp()
        && targetNode.getFirstChild().isThis()
        && !NodeUtil.isExpressionResultUsed(compoundAssignNode)) {
      RemovableBuilder builder = new RemovableBuilder().setIsThisDotPropertyReference(true);
      traverseRemovableAssignValue(valueNode, builder, scope);
      considerForIndependentRemoval(
          builder.buildNamedPropertyAssign(compoundAssignNode, targetNode.getLastChild()));
    } else {
      traverseNode(targetNode, scope);
      traverseNode(valueNode, scope);
    }
  }

  private VarInfo traverseNameNode(Node n, Scope scope) {
    return traverseVar(getVarForNameNode(n, scope));
  }

  private void traverseCall(Node callNode, Scope scope) {
    Node callee = callNode.getFirstChild();

    if (callee.isQualifiedName()
        && codingConvention.isPropertyRenameFunction(callee.getOriginalQualifiedName())) {
      Node propertyNameNode = checkNotNull(callee.getNext());
      if (propertyNameNode.isString()) {
        markPropertyNameReferenced(propertyNameNode.getString());
      }
      traverseChildren(callNode, scope);
    } else if (NodeUtil.isObjectDefinePropertiesDefinition(callNode)) {
      // TODO(bradfordcsmith): Should also handle Object.create() and Object.defineProperty().
      traverseObjectDefinePropertiesCall(callNode, scope);
    } else {
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
          // NOTE: DerivedClass and BaseClass must be QNames. Otherwise getClassesDefinedByCall()
          // will return null.
          classVarName = subclassRelationship.subclassName;
        } else {
          // Look for calls to addSingletonGetter calls.
          classVarName = codingConvention.getSingletonGetterClassName(callNode);
        }
      }

      Var classVar = null;
      if (classVarName != null && NodeUtil.isValidSimpleName(classVarName)) {
        classVar = checkNotNull(scope.getVar(classVarName), classVarName);
      }

      if (classVar == null || !classVar.isGlobal()) {
        // The call we are traversing does not modify a class definition,
        // or the class is not specified with a simple variable name,
        // or the variable name is not global.
        // TODO(bradfordcsmith): It would be more correct to check whether the class name
        // references a known constructor and expand to allow QNames.
        traverseChildren(callNode, scope);
      } else {
        RemovableBuilder builder = new RemovableBuilder();
        for (Node child = callNode.getFirstChild(); child != null; child = child.getNext()) {
          builder.addContinuation(new Continuation(child, scope));
        }
        traverseVar(classVar).addRemovable(builder.buildClassSetupCall(callNode));
      }
    }
  }

  /** Traverse `Object.defineProperties(someObject, propertyDefinitions);`. */
  private void traverseObjectDefinePropertiesCall(Node callNode, Scope scope) {
    // First child is Object.defineProperties or some equivalent of it.
    Node callee = callNode.getFirstChild();
    Node targetObject = callNode.getSecondChild();
    Node propertyDefinitions = targetObject.getNext();

    if ((targetObject.isName() || isNameDotPrototype(targetObject))
        && !NodeUtil.isExpressionResultUsed(callNode)) {
      // NOTE: Object.defineProperties() returns its first argument, so if its return value is used
      // that counts as a use of the targetObject.
      Node nameNode = targetObject.isName() ? targetObject : targetObject.getFirstChild();
      VarInfo varInfo = traverseNameNode(nameNode, scope);
      RemovableBuilder builder = new RemovableBuilder();
      // TODO(bradfordcsmith): Is it really necessary to traverse the callee
      // (aka. Object.defineProperties)?
      builder.addContinuation(new Continuation(callee, scope));
      if (NodeUtil.mayHaveSideEffects(propertyDefinitions)) {
        traverseNode(propertyDefinitions, scope);
      } else {
        builder.addContinuation(new Continuation(propertyDefinitions, scope));
      }
      varInfo.addRemovable(builder.buildClassSetupCall(callNode));
    } else {
      // TODO(bradfordcsmith): Is it really necessary to traverse the callee
      // (aka. Object.defineProperties)?
      traverseNode(callee, scope);
      traverseNode(targetObject, scope);
      traverseNode(propertyDefinitions, scope);
    }
  }

  /** Traverse the object literal passed as the second argument to `Object.defineProperties()`. */
  private void traverseObjectDefinePropertiesLiteral(Node propertyDefinitions, Scope scope) {
    for (Node property = propertyDefinitions.getFirstChild();
        property != null;
        property = property.getNext()) {
      if (property.isQuotedString()) {
        // Quoted property name counts as a reference to the property and protects it from removal.
        markPropertyNameReferenced(property.getString());
        traverseNode(property.getOnlyChild(), scope);
      } else if (property.isStringKey()) {
        Node definition = property.getOnlyChild();
        if (NodeUtil.mayHaveSideEffects(definition)) {
          traverseNode(definition, scope);
        } else {
          considerForIndependentRemoval(
              new RemovableBuilder()
                  .addContinuation(new Continuation(definition, scope))
                  .buildObjectDefinePropertiesDefinition(property));
        }
      } else {
        // TODO(bradfordcsmith): Maybe report error for anything other than a computed property,
        // since getters, setters, and methods don't make much sense in this context.
        traverseNode(property, scope);
      }
    }
  }

  private void traverseRest(Node restNode, Scope scope) {
    Node target = restNode.getOnlyChild();
    if (target.isName()) {
      VarInfo varInfo = traverseNameNode(target, scope);
      if (!restNode.getParent().isParamList()) {
        // Parameter removal is done in removeUnreferencedFunctionArgs().
        // TODO(bradfordcsmith): Handle parameter placeholders with removables for better code
        // consistency.
        varInfo.addRemovable(new RemovableBuilder().buildDestructuringAssign(target));
      }
    } else if (isThisDotProperty(target)) {
      considerForIndependentRemoval(new RemovableBuilder().buildDestructuringAssign(target));
    } else {
      traverseNode(target, scope);
    }
  }

  private Var getVarForNameNode(Node nameNode, Scope scope) {
    return checkNotNull(scope.getVar(nameNode.getString()), nameNode);
  }

  private void traverseObjectLiteral(Node objectLiteral, Scope scope) {
    checkArgument(objectLiteral.isObjectLit(), objectLiteral);
    // Is this an object literal that is assigned directly to a 'prototype' property?
    if (isAssignmentToPrototype(objectLiteral.getParent())) {
      traversePrototypeLiteral(objectLiteral, scope);
    } else if (isObjectDefinePropertiesSecondArgument(objectLiteral)) {
      // TODO(bradfordcsmith): Consider restricting special handling of the properties literal to
      // cases where the target object is a known class, prototype, or this.
      traverseObjectDefinePropertiesLiteral(objectLiteral, scope);
    } else {
      traverseNonPrototypeObjectLiteral(objectLiteral, scope);
    }
  }

  private boolean isObjectDefinePropertiesSecondArgument(Node n) {
    Node parent = n.getParent();
    return NodeUtil.isObjectDefinePropertiesDefinition(parent) && parent.getLastChild() == n;
  }

  private void traverseNonPrototypeObjectLiteral(Node objectLiteral, Scope scope) {
    for (Node propertyNode = objectLiteral.getFirstChild();
        propertyNode != null;
        propertyNode = propertyNode.getNext()) {
      if (propertyNode.isStringKey()) {
        // A property name in an object literal counts as a reference,
        // because of some reflection patterns.
        // Note that we are intentionally treating both quoted and unquoted keys as
        // references.
        markPropertyNameReferenced(propertyNode.getString());
        traverseNode(propertyNode.getFirstChild(), scope);
      } else {
        traverseNode(propertyNode, scope);
      }
    }
  }

  private void traversePrototypeLiteral(Node objectLiteral, Scope scope) {
    for (Node propertyNode = objectLiteral.getFirstChild();
        propertyNode != null;
        propertyNode = propertyNode.getNext()) {
      if (propertyNode.isComputedProp() || propertyNode.isQuotedString()) {
        traverseChildren(propertyNode, scope);
      } else {
        Node valueNode = propertyNode.getOnlyChild();
        if (NodeUtil.mayHaveSideEffects(valueNode)) {
          // TODO(bradfordcsmith): Ideally we should preserve the side-effect without keeping the
          // property itself alive.
          traverseNode(valueNode, scope);
        } else {
          // If we've come this far, we already know we're keeping the prototype literal itself,
          // but we may be able to remove unreferenced properties in it.
          considerForIndependentRemoval(
              new RemovableBuilder()
                  .addContinuation(new Continuation(valueNode, scope))
                  .buildClassOrPrototypeNamedProperty(propertyNode));
        }
      }
    }
  }

  private boolean isAssignmentToPrototype(Node n) {
    return n.isAssign() && isDotPrototype(n.getFirstChild());
  }

  /** True for `someExpression.prototype`. */
  private static boolean isDotPrototype(Node n) {
    return n.isGetProp() && n.getLastChild().getString().equals("prototype");
  }

  private void traverseCatch(Node catchNode, Scope scope) {
    Node exceptionNameNode = catchNode.getFirstChild();
    Node block = exceptionNameNode.getNext();
    VarInfo exceptionVarInfo = traverseNameNode(exceptionNameNode, scope);
    exceptionVarInfo.setIsExplicitlyNotRemovable();
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
      VarInfo varInfo = traverseNameNode(iterationTarget, forScope);
      varInfo.setIsExplicitlyNotRemovable();
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
        VarInfo varInfo = traverseNameNode(declNode, forScope);
        varInfo.setIsExplicitlyNotRemovable();
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
        VarInfo varInfo = traverseNameNode(nameNode, scope);
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
      // Destructuring declarations are handled elsewhere.
      traverseNode(nameNode, scope);
    } else {
      Node valueNode = nameNode.getFirstChild();
      VarInfo varInfo = traverseNameNode(nameNode, scope);
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
            builder.buildNameDeclarationStatement(declarationStatement);
        varInfo.addRemovable(removable);
      }
    }
  }

  private void traverseAssign(Node assignNode, Scope scope) {
    checkState(NodeUtil.isAssignmentOp(assignNode));

    Node lhs = assignNode.getFirstChild();
    Node valueNode = assignNode.getLastChild();
    if (lhs.isName()) {
      // varName = something
      VarInfo varInfo = traverseNameNode(lhs, scope);
      RemovableBuilder builder = new RemovableBuilder();
      traverseRemovableAssignValue(valueNode, builder, scope);
      varInfo.addRemovable(builder.buildVariableAssign(assignNode));
    } else if (lhs.isGetElem()) {
      Node getElemObj = lhs.getFirstChild();
      Node getElemKey = lhs.getLastChild();
      Node varNameNode =
          getElemObj.isName()
              ? getElemObj
              : isNameDotPrototype(getElemObj) ? getElemObj.getFirstChild() : null;

      if (varNameNode != null) {
        // varName[someExpression] = someValue
        // OR
        // varName.prototype[someExpression] = someValue
        VarInfo varInfo = traverseNameNode(varNameNode, scope);
        RemovableBuilder builder = new RemovableBuilder();
        if (NodeUtil.mayHaveSideEffects(getElemKey)) {
          traverseNode(getElemKey, scope);
        } else {
          builder.addContinuation(new Continuation(getElemKey, scope));
        }
        traverseRemovableAssignValue(valueNode, builder, scope);
        varInfo.addRemovable(builder.buildComputedPropertyAssign(assignNode, getElemKey));
      } else {
        traverseNode(getElemObj, scope);
        traverseNode(getElemKey, scope);
        traverseNode(valueNode, scope);
      }
    } else if (lhs.isGetProp()) {
      Node getPropLhs = lhs.getFirstChild();
      Node propNameNode = lhs.getLastChild();

      if (getPropLhs.isName()) {
        // varName.propertyName = someValue
        VarInfo varInfo = traverseNameNode(getPropLhs, scope);
        RemovableBuilder builder = new RemovableBuilder();
        traverseRemovableAssignValue(valueNode, builder, scope);
        varInfo.addRemovable(builder.buildNamedPropertyAssign(assignNode, propNameNode));
      } else if (isDotPrototype(getPropLhs)) {
        // objExpression.prototype.propertyName = someValue
        Node objExpression = getPropLhs.getFirstChild();
        RemovableBuilder builder = new RemovableBuilder().setIsPrototypeDotPropertyReference(true);
        traverseRemovableAssignValue(valueNode, builder, scope);
        if (objExpression.isName()) {
          // varName.prototype.propertyName = someValue
          VarInfo varInfo = traverseNameNode(getPropLhs.getFirstChild(), scope);
          varInfo.addRemovable(builder.buildNamedPropertyAssign(assignNode, propNameNode));
        } else {
          // (someExpression).prototype.propertyName = someValue
          if (NodeUtil.mayHaveSideEffects(objExpression)) {
            traverseNode(objExpression, scope);
          } else {
            builder.addContinuation(new Continuation(objExpression, scope));
          }
          considerForIndependentRemoval(
              builder.buildAnonymousPrototypeNamedPropertyAssign(
                  assignNode, propNameNode.getString()));
        }
      } else if (getPropLhs.isThis()) {
        // this.propertyName = someValue
        RemovableBuilder builder = new RemovableBuilder().setIsThisDotPropertyReference(true);
        traverseRemovableAssignValue(valueNode, builder, scope);
        considerForIndependentRemoval(builder.buildNamedPropertyAssign(assignNode, propNameNode));
      } else {
        traverseNode(lhs, scope);
        traverseNode(valueNode, scope);
      }
    } else {
      // no other cases are removable
      traverseNode(lhs, scope);
      traverseNode(valueNode, scope);
    }
  }

  private void traverseRemovableAssignValue(Node valueNode, RemovableBuilder builder, Scope scope) {
    if (NodeUtil.mayHaveSideEffects(valueNode)
        || NodeUtil.isExpressionResultUsed(valueNode.getParent())) {
      traverseNode(valueNode, scope);
    } else {
      builder.addContinuation(new Continuation(valueNode, scope));
    }
  }

  private boolean isNameDotPrototype(Node n) {
    return n.isGetProp()
        && n.getFirstChild().isName()
        && n.getLastChild().getString().equals("prototype");
  }

  private void traverseDefaultValue(Node defaultValueNode, Scope scope) {
    Node target = defaultValueNode.getFirstChild();
    Node value = target.getNext();
    if (NodeUtil.mayHaveSideEffects(value)) {
      // TODO(bradfordcsmith): Preserve side effects without keeping unreferenced variables and
      // properties alive.
      traverseNode(target, scope);
      traverseNode(value, scope);
    } else if (target.isName()) {
      VarInfo varInfo = traverseNameNode(target, scope);
      DestructuringAssign assign =
          new RemovableBuilder()
              .addContinuation(new Continuation(value, scope))
              .buildDestructuringAssign(target);
      varInfo.addRemovable(assign);
    } else if (isThisDotProperty(target)) {
      DestructuringAssign assign =
          new RemovableBuilder()
              .addContinuation(new Continuation(value, scope))
              .buildDestructuringAssign(target);
      considerForIndependentRemoval(assign);
    } else {
      traverseNode(target, scope);
      traverseNode(value, scope);
    }
  }

  private void traverseArrayPattern(Node arrayPattern, Scope scope) {
    for (Node c = arrayPattern.getFirstChild(); c != null; c = c.getNext()) {
      if (c.isName()) {
        VarInfo varInfo = traverseNameNode(c, scope);
        varInfo.addRemovable(new RemovableBuilder().buildDestructuringAssign(c));
      } else if (isThisDotProperty(c)) {
        considerForIndependentRemoval(new RemovableBuilder().buildDestructuringAssign(c));
      } else if (c.isDefaultValue()) {
        // traverseDefaultValue() will create a removable if necessary
        traverseDefaultValue(c, scope);
      } else {
        // TODO(bradfordcsmith): Treat destructuring assignments to properties as removable writes.
        traverseNode(c, scope);
      }
    }
  }

  private void traverseObjectPattern(Node objectPattern, Scope scope) {
    for (Node propertyNode = objectPattern.getFirstChild();
        propertyNode != null;
        propertyNode = propertyNode.getNext()) {
      if (propertyNode.isComputedProp()) {
        traverseObjectPatternComputedProperty(propertyNode, scope);
      } else {
        traverseObjectPatternStringKey(propertyNode, scope);
      }
    }
  }

  private void traverseObjectPatternStringKey(Node elm, Scope scope) {
    checkArgument(elm.isStringKey(), elm);
    // `{propertyName: target} = ...`
    // or
    // `{'propertyName': target} = ...`
    // NOTE: The parser will convert `{1: x} = ...` to `{'1': x} = ...`

    // TODO(bradfordcsmith): Avoid marking the property name as referenced until we know we won't
    // remove the assignment.
    if (!elm.isQuotedString()) {
      markPropertyNameReferenced(elm.getString());
    }

    Node target = elm.getOnlyChild();
    Node defaultValue = null;

    if (target.isDefaultValue()) {
      target = target.getFirstChild();
      defaultValue = checkNotNull(target.getNext());
    }

    if (defaultValue != null && NodeUtil.mayHaveSideEffects(defaultValue)) {
      // TODO(bradfordcsmith): Preserve side effects without preventing removal of variables and
      // properties.
      if (defaultValue != null) {
        traverseNode(defaultValue, scope);
      }
      traverseNode(target, scope);
    } else if (target.isName()) {
      VarInfo varInfo = traverseNameNode(target, scope);
      RemovableBuilder builder = new RemovableBuilder();
      if (defaultValue != null) {
        builder.addContinuation(new Continuation(defaultValue, scope));
      }
      varInfo.addRemovable(builder.buildDestructuringAssign(target));
    } else if (isThisDotProperty(target)) {
      RemovableBuilder builder = new RemovableBuilder();
      if (defaultValue != null) {
        builder.addContinuation(new Continuation(defaultValue, scope));
      }
      considerForIndependentRemoval(builder.buildDestructuringAssign(target));
    } else {
      // TODO(bradfordcsmith): Handle property assignments also
      // e.g. `({a: foo.bar, b: foo.baz}) = {a: 1, b: 2}`
      if (defaultValue != null) {
        traverseNode(defaultValue, scope);
      }
      traverseNode(target, scope);
    }
  }

  private void traverseObjectPatternComputedProperty(Node elm, Scope scope) {
    // `{[propertyExpression]: target} = ...`
    checkArgument(elm.isComputedProp(), elm);
    Node propertyExpression = elm.getFirstChild();
    Node target = propertyExpression.getNext();

    Node defaultValue = null;

    if (target.isDefaultValue()) {
      target = target.getFirstChild();
      defaultValue = checkNotNull(target.getNext());
    }

    if (NodeUtil.mayHaveSideEffects(propertyExpression)
        || (defaultValue != null && NodeUtil.mayHaveSideEffects(defaultValue))) {
      // TODO(bradfordcsmith): Preserve side effects without preventing removal of variables and
      // properties.
      traverseNode(propertyExpression, scope);
      if (defaultValue != null) {
        traverseNode(defaultValue, scope);
      }
      traverseNode(target, scope);
    } else if (target.isName()) {
      VarInfo varInfo = traverseNameNode(target, scope);

      RemovableBuilder builder = new RemovableBuilder();
      builder.addContinuation(new Continuation(propertyExpression, scope));
      if (defaultValue != null) {
        builder.addContinuation(new Continuation(defaultValue, scope));
      }
      varInfo.addRemovable(builder.buildDestructuringAssign(target));
    } else if (isNameDotPrototype(target)) {
      RemovableBuilder builder = new RemovableBuilder();
      builder.addContinuation(new Continuation(propertyExpression, scope));
      if (defaultValue != null) {
        builder.addContinuation(new Continuation(defaultValue, scope));
      }
      considerForIndependentRemoval(builder.buildDestructuringAssign(target));
    } else {
      // TODO(bradfordcsmith): Handle property assignments also
      // e.g. `({a: foo.bar, b: foo.baz}) = {a: 1, b: 2}`
      traverseNode(propertyExpression, scope);
      if (defaultValue != null) {
        traverseNode(defaultValue, scope);
      }
      traverseNode(target, scope);
    }
  }

  private void traverseChildren(Node n, Scope scope) {
    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      traverseNode(c, scope);
    }
  }

  /**
   * Handle a class that is not the RHS child of an assignment or a variable declaration
   * initializer.
   *
   * <p>For
   * @param classNode
   * @param scope
   */
  private void traverseClass(Node classNode, Scope scope) {
    checkArgument(classNode.isClass());
    if (NodeUtil.isClassDeclaration(classNode)) {
      traverseClassDeclaration(classNode, scope);
    } else {
      traverseClassExpression(classNode, scope);
    }
  }

  private void traverseClassDeclaration(Node classNode, Scope scope) {
    checkArgument(classNode.isClass());
    Node classNameNode = classNode.getFirstChild();
    Node baseClassExpression = classNameNode.getNext();
    Node classBodyNode = baseClassExpression.getNext();
    Scope classScope = scopeCreator.createScope(classNode, scope);

    VarInfo varInfo = traverseNameNode(classNameNode, scope);
    if (classNode.getParent().isExport()) {
      // Cannot remove an exported class.
      varInfo.setIsExplicitlyNotRemovable();
      traverseNode(baseClassExpression, scope);
      // Use traverseChildren() here, because we should not consider any properties on the exported
      // class to be removable.
      traverseChildren(classBodyNode, classScope);
    } else if (NodeUtil.mayHaveSideEffects(baseClassExpression)) {
      // TODO(bradfordcsmith): implement removal without losing side-effects for this case
      varInfo.setIsExplicitlyNotRemovable();
      traverseNode(baseClassExpression, scope);
      traverseClassMembers(classBodyNode, classScope);
    } else {
      RemovableBuilder builder =
          new RemovableBuilder()
              .addContinuation(new Continuation(baseClassExpression, classScope))
              .addContinuation(new Continuation(classBodyNode, classScope));
      varInfo.addRemovable(builder.buildClassDeclaration(classNode));
    }
  }

  private void traverseClassExpression(Node classNode, Scope scope) {
    checkArgument(classNode.isClass());
    Node classNameNode = classNode.getFirstChild();
    Node baseClassExpression = classNameNode.getNext();
    Node classBodyNode = baseClassExpression.getNext();
    Scope classScope = scopeCreator.createScope(classNode, scope);

    if (classNameNode.isName()) {
      // We may be able to remove the name node if nothing ends up referring to it.
      VarInfo varInfo = traverseNameNode(classNameNode, classScope);
      varInfo.addRemovable(new RemovableBuilder().buildNamedClassExpression(classNode));
    }
    // If we're traversing the class expression, we've already decided we cannot remove it.
    traverseNode(baseClassExpression, scope);
    traverseClassMembers(classBodyNode, classScope);
  }

  private void traverseClassMembers(Node node, Scope scope) {
    checkArgument(node.isClassMembers(), node);
    if (removeUnusedPrototypeProperties) {
      for (Node member = node.getFirstChild(); member != null; member = member.getNext()) {
        if (member.isMemberFunctionDef() || NodeUtil.isGetOrSetKey(member)) {
          // If we get as far as traversing the members of a class, we've already decided that
          // we cannot remove the class itself, so just consider individual members for removal.
          considerForIndependentRemoval(
              new RemovableBuilder()
                  .addContinuation(new Continuation(member, scope))
                  .buildClassOrPrototypeNamedProperty(member));
        } else {
          checkState(member.isComputedProp());
          traverseChildren(member, scope);
        }
      }
    } else {
      traverseChildren(node, scope);
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
    checkState(body.getNext() == null && body.isBlock(), body);

    // Checking the parameters
    Scope fparamScope = scopeCreator.createScope(function, parentScope);

    // Checking the function body
    Scope fbodyScope = scopeCreator.createScope(body, fparamScope);

    Node nameNode = function.getFirstChild();
    if (!nameNode.getString().isEmpty()) {
      // var x = function funcName() {};
      // make sure funcName gets into the varInfoMap so it will be considered for removal.
      VarInfo varInfo = traverseNameNode(nameNode, fparamScope);
      if (NodeUtil.isExpressionResultUsed(function)) {
        // var f = function g() {};
        // The f is an alias for g, so g escapes from the scope where it is defined.
        varInfo.hasNonLocalOrNonLiteralValue = true;
      }
    }

    traverseChildren(paramlist, fparamScope);
    traverseChildren(body, fbodyScope);

    allFunctionParamScopes.add(fparamScope);
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
      // Continue traversal of all of the property name's values and no longer consider them for
      // removal.
      for (Removable removable : removablesForPropertyNames.removeAll(propertyName)) {
        removable.applyContinuations();
      }
    }
  }

  private void considerForIndependentRemoval(Removable removable) {
    if (removable.isNamedProperty()) {
      String propertyName = removable.getPropertyName();

      if (referencedPropertyNames.contains(propertyName)
          || codingConvention.isExported(propertyName)) {
        // Referenced or exported, so not removable.
        removable.applyContinuations();
      } else if (isIndependentlyRemovable(removable)) {
        // Store for possible removal later.
        removablesForPropertyNames.put(propertyName, removable);
      } else {
        removable.applyContinuations();
        // This assignment counts as a reference, since we won't be removing it.
        // This is necessary in order to preserve getters and setters for the property.
        markPropertyNameReferenced(propertyName);
      }
    } else {
      removable.applyContinuations();
    }
  }

  private boolean isIndependentlyRemovable(Removable removable) {
    return (removeUnusedPrototypeProperties && removable.isPrototypeProperty())
        || (removeUnusedThisProperties && removable.isThisDotPropertyReference())
        || (removeUnusedObjectDefinePropertiesDefinitions
            && removable.isObjectDefinePropertiesDefinition())
        || (removeUnusedStaticProperties && removable.isStaticProperty());
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
          lValue = lValue.getOnlyChild();
        }
        if (lValue.isDestructuringPattern()) {
          continue;
        }
        VarInfo varInfo = traverseNameNode(lValue, fparamScope);
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

      VarInfo varInfo = getVarInfo(getVarForNameNode(lValue, fparamScope));
      if (varInfo.isRemovable()) {
        NodeUtil.deleteNode(lastArg, compiler);
      } else {
        break;
      }
    }
  }

  /**
   * Handles a variable reference seen during traversal and returns a {@link VarInfo} object
   * appropriate for the given {@link Var}.
   *
   * <p>This is a wrapper for {@link #getVarInfo} that handles additional logic needed when we're
   * getting the {@link VarInfo} during traversal.
   */
  private VarInfo traverseVar(Var var) {
    checkNotNull(var);
    if (removeLocalVars && var.isArguments()) {
      // If we are considering removing local variables, that includes parameters.
      // If `arguments` is used in a function we must consider all parameters to be referenced.
      Scope functionScope = var.getScope().getClosestHoistScope();
      Node paramList = NodeUtil.getFunctionParameters(functionScope.getRootNode());
      for (Node param = paramList.getFirstChild(); param != null; param = param.getNext()) {
        Node lValue = param;
        if (lValue.isDefaultValue()) {
          lValue = lValue.getFirstChild();
        }
        if (lValue.isRest()) {
          lValue = lValue.getOnlyChild();
        }
        if (lValue.isDestructuringPattern()) {
          continue;
        }
        getVarInfo(getVarForNameNode(lValue, functionScope)).markAsReferenced();
      }
      // `arguments` is never removable.
      return canonicalUnremovableVarInfo;
    } else {
      return getVarInfo(var);
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
    boolean isGlobal = var.isGlobal();
    if (var.isExtern()) {
      return canonicalUnremovableVarInfo;
    } else if (isGlobal && !removeGlobals) {
      return canonicalUnremovableVarInfo;
    } else if (!isGlobal && !removeLocalVars) {
      return canonicalUnremovableVarInfo;
    } else if (codingConvention.isExported(var.getName(), !isGlobal)) {
      return canonicalUnremovableVarInfo;
    } else if (var.isArguments()) {
      return canonicalUnremovableVarInfo;
    } else {
      VarInfo varInfo = varInfoMap.get(var);
      if (varInfo == null) {
        varInfo = new VarInfo();
        if (var.getParentNode().isParamList()) {
          varInfo.hasNonLocalOrNonLiteralValue = true;
        }
        varInfoMap.put(var, varInfo);
      }
      return varInfo;
    }
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

      Node nameNode = var.nameNode;
      Node toRemove = nameNode.getParent();
      if (toRemove == null || alreadyRemoved(toRemove)) {
        // assignedVarInfo.removeAllRemovables () already removed it
      } else if (NodeUtil.isFunctionExpression(toRemove)) {
        // TODO(bradfordcsmith): Add a Removable for this case.
        if (!preserveFunctionExpressionNames) {
          Node fnNameNode = toRemove.getFirstChild();
          compiler.reportChangeToEnclosingScope(fnNameNode);
          fnNameNode.setString("");
        }
      } else {
        // Removables are not created for theses cases.
        // function foo(unused1 = someSideEffectingValue, ...unused2) {}
        // removeUnreferencedFunctionArgs() is responsible for removing these.
        // TODO(bradfordcsmith): handle parameter declarations with removables
        checkState(
            toRemove.isParamList()
                || (toRemove.getParent().isParamList()
                    && (toRemove.isDefaultValue() || toRemove.isRest())),
            "unremoved code: %s",
            toRemove);
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
    private final boolean isPrototypeDotPropertyReference;
    private final boolean isThisDotPropertyReference;

    private boolean continuationsAreApplied = false;
    private boolean isRemoved = false;

    Removable(RemovableBuilder builder) {
      continuations = builder.continuations;
      propertyName = builder.propertyName;
      isPrototypeDotPropertyReference = builder.isPrototypeDotPropertyReference;
      isThisDotPropertyReference = builder.isThisDotPropertyReference;
    }

    String getPropertyName() {
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

    /** True if this object represents assignment to a variable. */
    boolean isVariableAssignment() {
      return false;
    }

    /** True if this object represents a named property, either assignment or declaration. */
    boolean isNamedProperty() {
      return propertyName != null;
    }

    /**
     * True if this object represents assignment to a named property.
     *
     * <p>This does not include class or object literal member declarations.
     */
    boolean isNamedPropertyAssignment() {
      return false;
    }

    boolean isAssignedValueLocal() {
      return false; // assume non-local by default
    }

    /** Is this a direct assignment to `varName.prototype`? */
    boolean isPrototypeAssignment() {
      return isNamedPropertyAssignment() && propertyName.equals("prototype");
    }

    /** Is this an assignment to a property on a prototype object? */
    boolean isPrototypeDotPropertyReference() {
      return isPrototypeDotPropertyReference;
    }

    boolean isClassOrPrototypeNamedProperty() {
      return false;
    }

    boolean isPrototypeProperty() {
      return isPrototypeDotPropertyReference() || isClassOrPrototypeNamedProperty();
    }

    boolean isThisDotPropertyReference() {
      return isThisDotPropertyReference;
    }

    public boolean isObjectDefinePropertiesDefinition() {
      return false;
    }

    public boolean isStaticProperty() {
      return false;
    }

    /**
     * Would a nonlocal or nonliteral value prevent removal of a variable associated with this
     * {@link Removable}?
     *
     * <p>True if the nature of this removable is such that a variable associated with it must not
     * be removed if its value or its prototype is not a local, literal value.
     *
     * <p>e.g. When X or X.prototype is nonlocal and / or nonliteral we don't know whether it is
     * safe to remove code like this.
     *
     * <pre><code>
     *   X.propName = something; // Don't know the effect of setting X.propName
     *   use(something instanceof X); // can't be certain there are no instances of X
     * </code></pre>
     */
    public boolean preventsRemovalOfVariableWithNonLocalValueOrPrototype() {
      return false;
    }
  }

  private class RemovableBuilder {
    final List<Continuation> continuations = new ArrayList<>();

    @Nullable String propertyName = null;
    boolean isPrototypeDotPropertyReference = false;
    boolean isThisDotPropertyReference = false;

    RemovableBuilder addContinuation(Continuation continuation) {
      continuations.add(continuation);
      return this;
    }

    RemovableBuilder setIsPrototypeDotPropertyReference(boolean value) {
      this.isPrototypeDotPropertyReference = value;
      return this;
    }

    RemovableBuilder setIsThisDotPropertyReference(boolean value) {
      this.isThisDotPropertyReference = value;
      return this;
    }

    DestructuringAssign buildDestructuringAssign(Node targetNode) {
      return new DestructuringAssign(this, targetNode);
    }

    ClassDeclaration buildClassDeclaration(Node classNode) {
      return new ClassDeclaration(this, classNode);
    }

    NamedClassExpression buildNamedClassExpression(Node classNode) {
      return new NamedClassExpression(this, classNode);
    }

    ClassOrPrototypeNamedProperty buildClassOrPrototypeNamedProperty(Node propertyNode) {
      checkArgument(
          propertyNode.isMemberFunctionDef()
              || NodeUtil.isGetOrSetKey(propertyNode)
              || (propertyNode.isStringKey() && !propertyNode.isQuotedString()),
          propertyNode);
      this.propertyName = propertyNode.getString();
      return new ClassOrPrototypeNamedProperty(this, propertyNode);
    }

    ObjectDefinePropertiesDefinition buildObjectDefinePropertiesDefinition(Node propertyNode) {
      this.propertyName = propertyNode.getString();
      return new ObjectDefinePropertiesDefinition(this, propertyNode);
    }

    FunctionDeclaration buildFunctionDeclaration(Node functionNode) {
      return new FunctionDeclaration(this, functionNode);
    }

    NameDeclarationStatement buildNameDeclarationStatement(Node declarationStatement) {
      return new NameDeclarationStatement(this, declarationStatement);
    }

    Assign buildNamedPropertyAssign(Node assignNode, Node propertyNode) {
      this.propertyName = propertyNode.getString();
      return new Assign(this, assignNode, Kind.NAMED_PROPERTY, propertyNode);
    }

    Assign buildComputedPropertyAssign(Node assignNode, Node propertyNode) {
      return new Assign(this, assignNode, Kind.COMPUTED_PROPERTY, propertyNode);
    }

    Assign buildVariableAssign(Node assignNode) {
      return new Assign(this, assignNode, Kind.VARIABLE, /* propertyNode */ null);
    }

    ClassSetupCall buildClassSetupCall(Node callNode) {
      return new ClassSetupCall(this, callNode);
    }

    VanillaForNameDeclaration buildVanillaForNameDeclaration(Node nameNode) {
      return new VanillaForNameDeclaration(this, nameNode);
    }

    AnonymousPrototypeNamedPropertyAssign buildAnonymousPrototypeNamedPropertyAssign(
        Node assignNode, String propertyName) {
      this.propertyName = propertyName;
      return new AnonymousPrototypeNamedPropertyAssign(this, assignNode);
    }

    IncOrDecOp buildIncOrDepOp(Node incOrDecOp, Node propertyNode) {
      this.propertyName = propertyNode.getString();
      return new IncOrDecOp(this, incOrDecOp);
    }

    UnusedReadReference buildUnusedReadReference(Node referenceNode, Node propertyNode) {
      this.propertyName = propertyNode.getString();
      return new UnusedReadReference(this, referenceNode);
    }

    public Removable buildInstanceofName(Node instanceofNode) {
      return new InstanceofName(this, instanceofNode);
    }
  }

  /** Represents a read reference whose value is not used. */
  private class UnusedReadReference extends Removable {
    final Node referenceNode;

    UnusedReadReference(RemovableBuilder builder, Node referenceNode) {
      super(builder);
      // TODO(bradfordcsmith): handle `name;` and `name.property;` references
      checkState(
          isThisDotProperty(referenceNode) || isDotPrototypeDotProperty(referenceNode),
          referenceNode);
      this.referenceNode = referenceNode;
    }

    @Override
    void removeInternal(AbstractCompiler compiler) {
      if (!alreadyRemoved(referenceNode)) {
        if (isThisDotProperty(referenceNode)) {
          removeExpressionCompletely(referenceNode);
        } else {
          checkState(isDotPrototypeDotProperty(referenceNode), referenceNode);
          // objExpression.prototype.propertyName
          Node objExpression = referenceNode.getFirstFirstChild();
          if (NodeUtil.mayHaveSideEffects(objExpression)) {
            replaceNodeWith(referenceNode, objExpression.detach());
          } else {
            removeExpressionCompletely(referenceNode);
          }
        }
      }
    }

    @Override
    public String toString() {
      return "UnusedReadReference:" + referenceNode;
    }
  }

  /**
   * Represents `something instanceof varName`.
   *
   * <p>If `varName` is removed, this expression can be replaced with `false` or
   * `(something, false)` to preserve side effects.
   */
  private class InstanceofName extends Removable {
    final Node instanceofNode;

    InstanceofName(RemovableBuilder builder, Node instanceofNode) {
      super(builder);
      checkArgument(instanceofNode.isInstanceOf(), instanceofNode);
      this.instanceofNode = instanceofNode;
    }

    @Override
    void removeInternal(AbstractCompiler compiler) {
      if (!alreadyRemoved(instanceofNode)) {
        Node lhs = instanceofNode.getFirstChild();
        Node falseNode = IR.falseNode().srcref(instanceofNode);
        if (NodeUtil.mayHaveSideEffects(lhs)) {
          replaceNodeWith(instanceofNode, IR.comma(lhs.detach(), falseNode).srcref(instanceofNode));
        } else {
          replaceNodeWith(instanceofNode, falseNode);
        }
      }
    }

    @Override
    public boolean preventsRemovalOfVariableWithNonLocalValueOrPrototype() {
      // If we aren't sure where X comes from and what aliases it might have, we cannot be sure
      // there are no instances of it.
      return true;
    }

    @Override
    public String toString() {
      return "InstanceofName:" + instanceofNode;
    }
  }

  /** Represents an increment or decrement operation that could be removed. */
  private class IncOrDecOp extends Removable {
    final Node incOrDecNode;

    IncOrDecOp(RemovableBuilder builder, Node incOrDecNode) {
      super(builder);
      checkArgument(incOrDecNode.isInc() || incOrDecNode.isDec(), incOrDecNode);
      Node arg = incOrDecNode.getOnlyChild();
      // TODO(bradfordcsmith): handle `name;` and `name.property;` references
      checkState(isThisDotProperty(arg) || isDotPrototypeDotProperty(arg), arg);
      this.incOrDecNode = incOrDecNode;
    }

    @Override
    void removeInternal(AbstractCompiler compiler) {
      if (!alreadyRemoved(incOrDecNode)) {
        Node arg = incOrDecNode.getOnlyChild();
        checkState(arg.isGetProp(), arg);

        if (isThisDotProperty(arg)) {
          removeExpressionCompletely(incOrDecNode);
        } else {
          checkState(isDotPrototypeDotProperty(arg), arg);
          // objExpression.prototype.propertyName
          Node objExpression = arg.getFirstFirstChild();
          if (NodeUtil.mayHaveSideEffects(objExpression)) {
            replaceNodeWith(incOrDecNode, objExpression.detach());
          } else {
            removeExpressionCompletely(incOrDecNode);
          }
        }
      }
    }

    @Override
    public String toString() {
      return "IncOrDecOp:" + incOrDecNode;
    }
  }

  /** True for `this.propertyName` */
  private static boolean isThisDotProperty(Node n) {
    return n.isGetProp() && n.getFirstChild().isThis();
  }

  /** True for `(something).prototype.propertyName` */
  private static boolean isDotPrototypeDotProperty(Node n) {
    return n.isGetProp() && isDotPrototype(n.getFirstChild());
  }

  private class DestructuringAssign extends Removable {
    final Node targetNode;

    DestructuringAssign(RemovableBuilder builder, Node targetNode) {
      super(builder);
      checkState(targetNode.isName() || isThisDotProperty(targetNode), targetNode);
      this.targetNode = targetNode;
    }

    @Override
    boolean isVariableAssignment() {
      return targetNode.isName();
    }

    @Override
    boolean isThisDotPropertyReference() {
      return isThisDotProperty(targetNode);
    }

    @Override
    boolean isNamedProperty() {
      return targetNode.isGetProp();
    }

    @Override
    public boolean preventsRemovalOfVariableWithNonLocalValueOrPrototype() {
      if (targetNode.isGetProp()) {
        Node getPropLhs = targetNode.getFirstChild();
        // assignment to varName.property or varName.prototype.property
        // cannot be removed unless varName and varName.prototype have literal, local values.
        return getPropLhs.isName() || isNameDotPrototype(getPropLhs);
      } else {
        return false;
      }
    }

    @Override
    boolean isNamedPropertyAssignment() {
      return targetNode.isGetProp();
    }

    @Override
    String getPropertyName() {
      checkState(targetNode.isGetProp(), targetNode);
      return targetNode.getLastChild().getString();
    }

    @Override
    public void removeInternal(AbstractCompiler compiler) {
      if (!alreadyRemoved(targetNode)) {
        removeDestructuringTarget(targetNode);
      }
    }

    private void removeDestructuringTarget(Node target) {
      Node targetParent = target.getParent();
      if (targetParent.isArrayPattern()) {
        // [a, target, b] = something;
        // [a, target] = something;
        // Replace target with an empty node to avoid messing up the order of patterns,
        // then clean up trailing empties.
        replaceNodeWith(target, IR.empty().srcref(target));
        // We prefer `[a, b]` to `[a, b, , , , ]`
        // So remove any trailing empty nodes.
        for (Node maybeEmpty = targetParent.getLastChild();
            maybeEmpty != null && maybeEmpty.isEmpty();
            maybeEmpty = targetParent.getLastChild()) {
          maybeEmpty.detach();
        }
        compiler.reportChangeToEnclosingScope(targetParent);
        // TODO(bradfordcsmith): If the array pattern is now empty, try to remove it entirely.
      } else if (targetParent.isParamList()) {
        // removeUnreferencedFunctionArgs() is responsible for removal of function parameter
        // positions, so all we can do here is remove the default value.
        // NOTE: traverseRest() avoids creating a removable for a rest parameter.
        // TODO(bradfordcsmith): Handle parameter removal consistently with other removals.
        checkState(target.isDefaultValue(), target);
        // function(removableName = removableValue)
        compiler.reportChangeToEnclosingScope(targetParent);
        // preserve the slot in the parameter list
        Node name = target.getFirstChild();
        checkState(name.isName());
        if (target == targetParent.getLastChild()
            && removeGlobals
            && canRemoveParameters(targetParent)) {
          // function(p1, removableName = removableDefault)
          // and we're allowed to remove the parameter entirely
          target.detach();
        } else {
          // function(removableName = removableDefault, otherParam)
          // or removableName is at the end, but cannot be completely removed.
          target.replaceWith(name.detach());
        }
        NodeUtil.markFunctionsDeleted(target, compiler);
      } else if (targetParent.isRest()) {
        // remove all of `...target`
        removeDestructuringTarget(targetParent);
      } else if (targetParent.isDefaultValue()) {
        // remove value along with target
        removeDestructuringTarget(targetParent);
      } else {
        // ({ [propExpression]: target } = something)
        // becomes
        // ({} = something)
        checkState(targetParent.isStringKey() || targetParent.isComputedProp(), targetParent);
        NodeUtil.deleteNode(targetParent, compiler);
        // TODO(bradfordcsmith): If the pattern is now empty, see if it can be removed.
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

    @Override
    public String toString() {
      return "ClassDeclaration:" + classDeclarationNode;
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

    @Override
    public String toString() {
      return "NamedClassExpression:" + classNode;
    }
  }

  private class ClassOrPrototypeNamedProperty extends Removable {
    final Node propertyNode;

    ClassOrPrototypeNamedProperty(RemovableBuilder builder, Node propertyNode) {
      super(builder);
      this.propertyNode = propertyNode;
    }

    @Override
    public boolean isStaticProperty() {
      return propertyNode.isStaticMember();
    }

    @Override
    boolean isClassOrPrototypeNamedProperty() {
      return !isStaticProperty();
    }

    @Override
    void removeInternal(AbstractCompiler compiler) {
      NodeUtil.deleteNode(propertyNode, compiler);
    }

    @Override
    public String toString() {
      return "ClassOrPrototypeNamedProperty:" + propertyNode;
    }
  }

  /**
   * Represents a single property definition in the object literal passed as the second argument to
   * e.g. `Object.defineProperties(obj, {p1: {value: 1}, p2: {value: 3}});`.
   */
  private class ObjectDefinePropertiesDefinition extends Removable {
    final Node propertyNode;

    ObjectDefinePropertiesDefinition(RemovableBuilder builder, Node propertyNode) {
      super(builder);
      this.propertyNode = propertyNode;
    }

    @Override
    public boolean isObjectDefinePropertiesDefinition() {
      return true;
    }

    @Override
    void removeInternal(AbstractCompiler compiler) {
      NodeUtil.deleteNode(propertyNode, compiler);
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

    @Override
    boolean isAssignedValueLocal() {
      // The declared function is always created locally.
      return true;
    }

    @Override
    public String toString() {
      return "FunctionDeclaration:" + functionDeclarationNode;
    }
  }

  private class NameDeclarationStatement extends Removable {
    private final Node declarationStatement;

    public NameDeclarationStatement(RemovableBuilder builder, Node declarationStatement) {
      super(builder);
      checkArgument(NodeUtil.isNameDeclaration(declarationStatement), declarationStatement);
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

    @Override
    boolean isAssignedValueLocal() {
      Node nameNode = declarationStatement.getOnlyChild();
      Node valueNode = nameNode.getFirstChild();
      return valueNode == null
          || isLocalDefaultValueAssignment(nameNode, valueNode)
          || NodeUtil.evaluatesToLocalValue(valueNode);
    }

    @Override
    public String toString() {
      return "NameDeclStmt:" + declarationStatement;
    }
  }

  /**
   * True if targetNode is a qualified name and the valueNode is of the form `targetQualifiedName ||
   * localValue`.
   */
  private static boolean isLocalDefaultValueAssignment(Node targetNode, Node valueNode) {
    return valueNode.isOr()
        && targetNode.isQualifiedName()
        && valueNode.getFirstChild().isEquivalentTo(targetNode)
        && NodeUtil.evaluatesToLocalValue(valueNode.getLastChild());
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
    final Kind kind;

    Assign(
        RemovableBuilder builder,
        Node assignNode,
        Kind kind,
        @Nullable Node propertyNode) {
      super(builder);
      checkArgument(NodeUtil.isAssignmentOp(assignNode), assignNode);
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
      this.kind = kind;
    }

    /** True for `varName = value` assignments. */
    @Override
    boolean isVariableAssignment() {
      return kind == Kind.VARIABLE;
    }

    @Override
    boolean isAssignedValueLocal() {
      if (NodeUtil.isExpressionResultUsed(assignNode)) {
        // assigned value may escape or be aliased
        return false;
      } else {
        Node targetNode = assignNode.getFirstChild();
        Node valueNode = targetNode.getNext();
        return NodeUtil.evaluatesToLocalValue(valueNode)
            || isLocalDefaultValueAssignment(targetNode, valueNode);
      }
    }

    @Override
    public boolean preventsRemovalOfVariableWithNonLocalValueOrPrototype() {
      // If we don't know where the variable comes from or where it may go, then we don't know
      // whether it is safe to remove assignments to properties on it.
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

    @Override
    public boolean isStaticProperty() {
      Node lhs = assignNode.getFirstChild();
      if (lhs.isGetProp()) {
        // something.propName = someValue
        Node getPropLhs = lhs.getFirstChild();
        JSType typeI = getPropLhs.getJSType();
        return typeI != null && (typeI.isConstructor() || typeI.isInterface());
      } else {
        return false;
      }
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
        replaceNodeWith(assignNode, replacement);
      } else if (mustPreserveGetElmExpr) {
        replaceNodeWith(assignNode, lhs.getLastChild().detach());
      } else if (mustPreserveRhs) {
        replaceNodeWith(assignNode, rhs.detach());
      } else {
        removeExpressionCompletely(assignNode);
      }
    }

    @Override
    public String toString() {
      return "Assign:" + assignNode;
    }
  }

  /** Represents `(someObjectExpression).prototype.propertyName = someValue`. */
  private class AnonymousPrototypeNamedPropertyAssign extends Removable {
    final Node assignNode;

    AnonymousPrototypeNamedPropertyAssign(RemovableBuilder builder, Node assignNode) {
      super(builder);
      checkNotNull(builder.propertyName);
      checkArgument(assignNode.isAssign(), assignNode);
      this.assignNode = assignNode;
    }

    @Override
    void removeInternal(AbstractCompiler compiler) {
      if (alreadyRemoved(assignNode)) {
        return;
      }
      Node parent = assignNode.getParent();
      compiler.reportChangeToEnclosingScope(parent);
      Node lhs = assignNode.getFirstChild();
      Node rhs = assignNode.getLastChild();

      checkState(lhs.isGetProp(), lhs);
      Node objDotPrototype = lhs.getFirstChild();
      checkState(objDotPrototype.isGetProp(), objDotPrototype);
      Node objExpression = objDotPrototype.getFirstChild();
      Node prototype = objDotPrototype.getLastChild();
      checkState(prototype.getString().equals("prototype"), prototype);

      boolean mustPreserveRhs =
          NodeUtil.mayHaveSideEffects(rhs) || NodeUtil.isExpressionResultUsed(assignNode);
      boolean mustPreserveObjExpression = NodeUtil.mayHaveSideEffects(objExpression);

      if (mustPreserveRhs && mustPreserveObjExpression) {
        Node replacement =
            IR.comma(objExpression.detach(), rhs.detach()).useSourceInfoFrom(assignNode);
        replaceNodeWith(assignNode, replacement);
      } else if (mustPreserveObjExpression) {
        replaceNodeWith(assignNode, objExpression.detach());
      } else if (mustPreserveRhs) {
        replaceNodeWith(assignNode, rhs.detach());
      } else {
        removeExpressionCompletely(assignNode);
      }
    }

    @Override
    boolean isPrototypeProperty() {
      return true;
    }

    @Override
    public String toString() {
      return "AnonymousPrototypeNamedPropertyAssign:" + assignNode;
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

      Node replacement = null;
      // Need to keep call args that have side effects.
      // Easiest thing to do is break apart the call node as we go.
      // First child is the callee (aka. Object.defineProperties or equivalent)
      callNode.removeFirstChild();
      for (Node arg = callNode.getLastChild(); arg != null; arg = callNode.getLastChild()) {
        arg.detach();
        if (NodeUtil.mayHaveSideEffects(arg)) {
          if (replacement == null) {
            replacement = arg;
          } else {
            replacement = IR.comma(arg, replacement).srcref(callNode);
          }
        } else {
          NodeUtil.markFunctionsDeleted(arg, compiler);
        }
      }
      // NOTE: The call must either be its own statement or the LHS of a comma expression,
      // because it doesn't have a meaningful return value.
      if (replacement != null) {
        replaceNodeWith(callNode, replacement);
      } else if (parent.isExprResult()) {
        NodeUtil.deleteNode(parent, compiler);
      } else {
        // `(goog.inherits(A, B), something)` -> `something`
        checkState(parent.isComma());
        Node rhs = checkNotNull(callNode.getNext());
        compiler.reportChangeToEnclosingScope(parent);
        parent.replaceWith(rhs.detach());
      }
    }

    @Override
    public boolean preventsRemovalOfVariableWithNonLocalValueOrPrototype() {
      // If we aren't sure where X comes from and what aliases it might have, we cannot be sure
      // it's safe to remove the class setup for it.
      return true;
    }

    @Override
    public String toString() {
      return "ClassSetupCall:" + callNode;
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

    boolean isEntirelyRemovable = true;

    boolean hasNonLocalOrNonLiteralValue = false;
    boolean requiresLocalLiteralValueForRemoval = false;

    void addRemovable(Removable removable) {
      if (!removable.isAssignedValueLocal()
          && (removable.isVariableAssignment() || removable.isPrototypeAssignment())) {
        hasNonLocalOrNonLiteralValue = true;
      }
      if (removable.preventsRemovalOfVariableWithNonLocalValueOrPrototype()) {
        requiresLocalLiteralValueForRemoval = true;
      }
      if (hasNonLocalOrNonLiteralValue && requiresLocalLiteralValueForRemoval) {
        setIsExplicitlyNotRemovable();
      }

      if (isEntirelyRemovable) {
        // Store for possible removal later.
        removables.add(removable);
      } else {
        considerForIndependentRemoval(removable);
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

    boolean isRemovable() {
      return isEntirelyRemovable;
    }

    boolean setIsExplicitlyNotRemovable() {
      if (isEntirelyRemovable) {
        isEntirelyRemovable = false;
        for (Removable r : removables) {
          considerForIndependentRemoval(r);
        }
        removables.clear();
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

  void removeExpressionCompletely(Node expression) {
    checkState(!NodeUtil.isExpressionResultUsed(expression), expression);
    Node parent = expression.getParent();
    if (parent.isExprResult()) {
      NodeUtil.deleteNode(parent, compiler);
    } else if (parent.isComma()) {
      // Expression is probably the first child of the comma,
      // but it could be the second if the entire comma expression value is unused.
      Node otherChild = expression.getNext();
      if (otherChild == null) {
        otherChild = expression.getPrevious();
      }
      replaceNodeWith(parent, otherChild.detach());
    } else {
      // value isn't needed, but we need to keep the AST valid.
      replaceNodeWith(expression, IR.number(0).useSourceInfoFrom(expression));
    }
  }

  void replaceNodeWith(Node n, Node replacement) {
    compiler.reportChangeToEnclosingScope(n);
    n.replaceWith(replacement);
    NodeUtil.markFunctionsDeleted(n, compiler);
  }
}
