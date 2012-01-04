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

import static com.google.javascript.rhino.jstype.JSTypeNative.ARRAY_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.CHECKED_UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_VALUE_OR_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.CodingConvention.AssertionFunctionSpec;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.BooleanLiteralSet;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticSlot;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Type inference within a script node or a function body, using the data-flow
 * analysis framework.
 *
 */
class TypeInference
    extends DataFlowAnalysis.BranchedForwardDataFlowAnalysis<Node, FlowScope> {
  static final DiagnosticType TEMPLATE_TYPE_NOT_OBJECT_TYPE =
      DiagnosticType.warning(
      "JSC_TEMPLATE_TYPE_NOT_OBJECT_TYPE",
      "The template type must be an object type.\nActual: {0}");

  static final DiagnosticType TEMPLATE_TYPE_OF_THIS_EXPECTED =
      DiagnosticType.warning(
      "JSC_TEMPLATE_TYPE_OF_THIS_EXPECTED",
      "A function type with the template type as the type of this must be a " +
      "parameter type");

  static final DiagnosticType FUNCTION_LITERAL_UNDEFINED_THIS =
    DiagnosticType.warning(
        "JSC_FUNCTION_LITERAL_UNDEFINED_THIS",
        "Function literal argument refers to undefined this argument");

  private final AbstractCompiler compiler;
  private final JSTypeRegistry registry;
  private final ReverseAbstractInterpreter reverseInterpreter;
  private final Scope syntacticScope;
  private final FlowScope functionScope;
  private final FlowScope bottomScope;
  private final Map<String, AssertionFunctionSpec> assertionFunctionsMap;

  TypeInference(AbstractCompiler compiler, ControlFlowGraph<Node> cfg,
                ReverseAbstractInterpreter reverseInterpreter,
                Scope functionScope,
                Map<String, AssertionFunctionSpec> assertionFunctionsMap) {
    super(cfg, new LinkedFlowScope.FlowScopeJoinOp());
    this.compiler = compiler;
    this.registry = compiler.getTypeRegistry();
    this.reverseInterpreter = reverseInterpreter;
    this.syntacticScope = functionScope;
    this.functionScope = LinkedFlowScope.createEntryLattice(functionScope);
    this.assertionFunctionsMap = assertionFunctionsMap;

    // For each local variable declared with the VAR keyword, the entry
    // type is VOID.
    Iterator<Var> varIt =
        functionScope.getDeclarativelyUnboundVarsWithoutTypes();
    while (varIt.hasNext()) {
      Var var = varIt.next();
      if (isUnflowable(var)) {
        continue;
      }

      this.functionScope.inferSlotType(
          var.getName(), getNativeType(VOID_TYPE));
    }

    this.bottomScope = LinkedFlowScope.createEntryLattice(
        new Scope(functionScope.getRootNode(), functionScope.getTypeOfThis()));
  }

  @Override
  FlowScope createInitialEstimateLattice() {
    return bottomScope;
  }

  @Override
  FlowScope createEntryLattice() {
    return functionScope;
  }

  @Override
  FlowScope flowThrough(Node n, FlowScope input) {
    // If we have not walked a path from <entry> to <n>, then we don't
    // want to infer anything about this scope.
    if (input == bottomScope) {
      return input;
    }

    FlowScope output = input.createChildFlowScope();
    output = traverse(n, output);
    return output;
  }

  @Override
  @SuppressWarnings("fallthrough")
  List<FlowScope> branchedFlowThrough(Node source, FlowScope input) {
    // NOTE(nicksantos): Right now, we just treat ON_EX edges like UNCOND
    // edges. If we wanted to be perfect, we'd actually JOIN all the out
    // lattices of this flow with the in lattice, and then make that the out
    // lattice for the ON_EX edge. But it's probably to expensive to be
    // worthwhile.
    FlowScope output = flowThrough(source, input);
    Node condition = null;
    FlowScope conditionFlowScope = null;
    BooleanOutcomePair conditionOutcomes = null;

    List<DiGraphEdge<Node, Branch>> branchEdges = getCfg().getOutEdges(source);
    List<FlowScope> result = Lists.newArrayListWithCapacity(branchEdges.size());
    for (DiGraphEdge<Node, Branch> branchEdge : branchEdges) {
      Branch branch = branchEdge.getValue();
      FlowScope newScope = output;

      switch (branch) {
        case ON_TRUE:
          if (NodeUtil.isForIn(source)) {
            // item is assigned a property name, so its type should be string.
            Node item = source.getFirstChild();
            Node obj = item.getNext();

            FlowScope informed = traverse(obj, output.createChildFlowScope());

            if (item.isVar()) {
              item = item.getFirstChild();
            }
            if (item.isName()) {
              JSType iterKeyType = getNativeType(STRING_TYPE);
              ObjectType objType = getJSType(obj).dereference();
              JSType objIndexType = objType == null ?
                  null : objType.getIndexType();
              if (objIndexType != null && !objIndexType.isUnknownType()) {
                JSType narrowedKeyType =
                    iterKeyType.getGreatestSubtype(objIndexType);
                if (!narrowedKeyType.isEmptyType()) {
                  iterKeyType = narrowedKeyType;
                }
              }
              redeclareSimpleVar(informed, item, iterKeyType);
            }
            newScope = informed;
            break;
          }

          // FALL THROUGH

        case ON_FALSE:
          if (condition == null) {
            condition = NodeUtil.getConditionExpression(source);
            if (condition == null && source.isCase()) {
              condition = source;

              // conditionFlowScope is cached from previous iterations
              // of the loop.
              if (conditionFlowScope == null) {
                conditionFlowScope = traverse(
                    condition.getFirstChild(), output.createChildFlowScope());
              }
            }
          }

          if (condition != null) {
            if (condition.isAnd() ||
                condition.isOr()) {
              // When handling the short-circuiting binary operators,
              // the outcome scope on true can be different than the outcome
              // scope on false.
              //
              // TODO(nicksantos): The "right" way to do this is to
              // carry the known outcome all the way through the
              // recursive traversal, so that we can construct a
              // different flow scope based on the outcome. However,
              // this would require a bunch of code and a bunch of
              // extra computation for an edge case. This seems to be
              // a "good enough" approximation.

              // conditionOutcomes is cached from previous iterations
              // of the loop.
              if (conditionOutcomes == null) {
                conditionOutcomes = condition.isAnd() ?
                    traverseAnd(condition, output.createChildFlowScope()) :
                    traverseOr(condition, output.createChildFlowScope());
              }
              newScope =
                  reverseInterpreter.getPreciserScopeKnowingConditionOutcome(
                      condition,
                      conditionOutcomes.getOutcomeFlowScope(
                          condition.getType(), branch == Branch.ON_TRUE),
                      branch == Branch.ON_TRUE);
            } else {
              // conditionFlowScope is cached from previous iterations
              // of the loop.
              if (conditionFlowScope == null) {
                conditionFlowScope =
                    traverse(condition, output.createChildFlowScope());
              }
              newScope =
                  reverseInterpreter.getPreciserScopeKnowingConditionOutcome(
                      condition, conditionFlowScope, branch == Branch.ON_TRUE);
            }
          }
          break;
      }

      result.add(newScope.optimize());
    }
    return result;
  }

  private FlowScope traverse(Node n, FlowScope scope) {
    switch (n.getType()) {
      case Token.ASSIGN:
        scope = traverseAssign(n, scope);
        break;

      case Token.NAME:
        scope = traverseName(n, scope);
        break;

      case Token.GETPROP:
        scope = traverseGetProp(n, scope);
        break;

      case Token.AND:
        scope = traverseAnd(n, scope).getJoinedFlowScope()
            .createChildFlowScope();
        break;

      case Token.OR:
        scope = traverseOr(n, scope).getJoinedFlowScope()
            .createChildFlowScope();
        break;

      case Token.HOOK:
        scope = traverseHook(n, scope);
        break;

      case Token.OBJECTLIT:
        scope = traverseObjectLiteral(n, scope);
        break;

      case Token.CALL:
        scope = traverseCall(n, scope);
        break;

      case Token.NEW:
        scope = traverseNew(n, scope);
        break;

      case Token.ASSIGN_ADD:
      case Token.ADD:
        scope = traverseAdd(n, scope);
        break;

      case Token.POS:
      case Token.NEG:
        scope = traverse(n.getFirstChild(), scope);  // Find types.
        n.setJSType(getNativeType(NUMBER_TYPE));
        break;

      case Token.ARRAYLIT:
        scope = traverseArrayLiteral(n, scope);
        break;

      case Token.THIS:
        n.setJSType(scope.getTypeOfThis());
        break;

      case Token.ASSIGN_LSH:
      case Token.ASSIGN_RSH:
      case Token.LSH:
      case Token.RSH:
      case Token.ASSIGN_URSH:
      case Token.URSH:
      case Token.ASSIGN_DIV:
      case Token.ASSIGN_MOD:
      case Token.ASSIGN_BITAND:
      case Token.ASSIGN_BITXOR:
      case Token.ASSIGN_BITOR:
      case Token.ASSIGN_MUL:
      case Token.ASSIGN_SUB:
      case Token.DIV:
      case Token.MOD:
      case Token.BITAND:
      case Token.BITXOR:
      case Token.BITOR:
      case Token.MUL:
      case Token.SUB:
      case Token.DEC:
      case Token.INC:
      case Token.BITNOT:
        scope = traverseChildren(n, scope);
        n.setJSType(getNativeType(NUMBER_TYPE));
        break;

      case Token.PARAM_LIST:
        scope = traverse(n.getFirstChild(), scope);
        n.setJSType(getJSType(n.getFirstChild()));
        break;

      case Token.COMMA:
        scope = traverseChildren(n, scope);
        n.setJSType(getJSType(n.getLastChild()));
        break;

      case Token.TYPEOF:
        scope = traverseChildren(n, scope);
        n.setJSType(getNativeType(STRING_TYPE));
        break;

      case Token.DELPROP:
      case Token.LT:
      case Token.LE:
      case Token.GT:
      case Token.GE:
      case Token.NOT:
      case Token.EQ:
      case Token.NE:
      case Token.SHEQ:
      case Token.SHNE:
      case Token.INSTANCEOF:
      case Token.IN:
        scope = traverseChildren(n, scope);
        n.setJSType(getNativeType(BOOLEAN_TYPE));
        break;

      case Token.GETELEM:
        scope = traverseGetElem(n, scope);
        break;

      case Token.EXPR_RESULT:
        scope = traverseChildren(n, scope);
        if (n.getFirstChild().isGetProp()) {
          ensurePropertyDeclared(n.getFirstChild());
        }
        break;

      case Token.SWITCH:
        scope = traverse(n.getFirstChild(), scope);
        break;

      case Token.RETURN:
        scope = traverseReturn(n, scope);
        break;

      case Token.VAR:
      case Token.THROW:
        scope = traverseChildren(n, scope);
        break;

      case Token.CATCH:
        scope = traverseCatch(n, scope);
        break;
    }
    if (!n.isFunction()) {
      JSDocInfo info = n.getJSDocInfo();
      if (info != null && info.hasType()) {
        JSType castType = info.getType().evaluate(syntacticScope, registry);

        // A stubbed type cast on a qualified name should take
        // effect for all subsequent accesses of that name,
        // so treat it the same as an assign to that name.
        if (n.isQualifiedName() &&
            n.getParent().isExprResult()) {
          updateScopeForTypeChange(scope, n, n.getJSType(), castType);
        }

        n.setJSType(castType);
      }
    }

    return scope;
  }

  /**
   * Traverse a return value.
   */
  private FlowScope traverseReturn(Node n, FlowScope scope) {
    scope = traverseChildren(n, scope);

    Node retValue = n.getFirstChild();
    if (retValue != null) {
      JSType type = functionScope.getRootNode().getJSType();
      if (type != null) {
        FunctionType fnType = type.toMaybeFunctionType();
        if (fnType != null) {
          inferPropertyTypesToMatchConstraint(
              retValue.getJSType(), fnType.getReturnType());
        }
      }
    }
    return scope;
  }

  /**
   * Any value can be thrown, so it's really impossible to determine the type
   * of a CATCH param. Treat it as the UNKNOWN type.
   */
  private FlowScope traverseCatch(Node n, FlowScope scope) {
    Node name = n.getFirstChild();
    JSType type = getNativeType(JSTypeNative.UNKNOWN_TYPE);
    name.setJSType(type);
    redeclareSimpleVar(scope, name, type);
    return scope;
  }

  private FlowScope traverseAssign(Node n, FlowScope scope) {
    Node left = n.getFirstChild();
    Node right = n.getLastChild();
    scope = traverseChildren(n, scope);

    JSType leftType = left.getJSType();
    JSType rightType = getJSType(right);
    n.setJSType(rightType);

    updateScopeForTypeChange(scope, left, leftType, rightType);
    return scope;
  }

  /**
   * Updates the scope according to the result of a type change, like
   * an assignment or a type cast.
   */
  private void updateScopeForTypeChange(
      FlowScope scope, Node left, JSType leftType, JSType resultType) {
    Preconditions.checkNotNull(resultType);
    switch (left.getType()) {
      case Token.NAME:
        String varName = left.getString();
        Var var = syntacticScope.getVar(varName);

        // When looking at VAR initializers for declared VARs, we trust
        // the declared type over the type it's being initialized to.
        // This has two purposes:
        // 1) We avoid re-declaring declared variables so that built-in
        //    types defined in externs are not redeclared.
        // 2) When there's a lexical closure like
        //    /** @type {?string} */ var x = null;
        //    function f() { x = 'xyz'; }
        //    the inference will ignore the lexical closure,
        //    which is just wrong. This bug needs to be fixed eventually.
        boolean isVarDeclaration = left.hasChildren();
        if (!isVarDeclaration || var == null || var.isTypeInferred()) {
          redeclareSimpleVar(scope, left, resultType);
        }
        left.setJSType(isVarDeclaration || leftType == null ?
            resultType : null);

        if (var != null && var.isTypeInferred()) {
          JSType oldType = var.getType();
          var.setType(oldType == null ?
              resultType : oldType.getLeastSupertype(resultType));
        }
        break;
      case Token.GETPROP:
        String qualifiedName = left.getQualifiedName();
        if (qualifiedName != null) {
          scope.inferQualifiedSlot(left, qualifiedName,
              leftType == null ? getNativeType(UNKNOWN_TYPE) : leftType,
              resultType);
        }

        left.setJSType(resultType);
        ensurePropertyDefined(left, resultType);
        break;
    }
  }

  /**
   * Defines a property if the property has not been defined yet.
   */
  private void ensurePropertyDefined(Node getprop, JSType rightType) {
    String propName = getprop.getLastChild().getString();
    JSType nodeType = getJSType(getprop.getFirstChild());
    ObjectType objectType = ObjectType.cast(
        nodeType.restrictByNotNullOrUndefined());
    if (objectType == null) {
      registry.registerPropertyOnType(propName, nodeType);
    } else {
      if (ensurePropertyDeclaredHelper(getprop, objectType)) {
        return;
      }

      if (!objectType.isPropertyTypeDeclared(propName)) {
        // We do not want a "stray" assign to define an inferred property
        // for every object of this type in the program. So we use a heuristic
        // approach to determine whether to infer the propery.
        //
        // 1) If the property is already defined, join it with the previously
        //    inferred type.
        // 2) If this isn't an instance object, define it.
        // 3) If the property of an object is being assigned in the constructor,
        //    define it.
        // 4) If this is a stub, define it.
        // 5) Otherwise, do not define the type, but declare it in the registry
        //    so that we can use it for missing property checks.
        if (objectType.hasProperty(propName) ||
            !objectType.isInstanceType()) {
          if ("prototype".equals(propName)) {
            objectType.defineDeclaredProperty(
                propName, rightType, getprop);
          } else {
            objectType.defineInferredProperty(
                propName, rightType, getprop);
          }
        } else {
          if (getprop.getFirstChild().isThis() &&
              getJSType(syntacticScope.getRootNode()).isConstructor()) {
            objectType.defineInferredProperty(
                propName, rightType, getprop);
          } else {
            registry.registerPropertyOnType(propName, objectType);
          }
        }
      }
    }
  }

  /**
   * Defines a declared property if it has not been defined yet.
   *
   * This handles the case where a property is declared on an object where
   * the object type is inferred, and so the object type will not
   * be known in {@code TypedScopeCreator}.
   */
  private void ensurePropertyDeclared(Node getprop) {
    ObjectType ownerType = ObjectType.cast(
        getJSType(getprop.getFirstChild()).restrictByNotNullOrUndefined());
    if (ownerType != null) {
      ensurePropertyDeclaredHelper(getprop, ownerType);
    }
  }

  /**
   * Declares a property on its owner, if necessary.
   * @return True if a property was declared.
   */
  private boolean ensurePropertyDeclaredHelper(
      Node getprop, ObjectType objectType) {
    String propName = getprop.getLastChild().getString();
    String qName = getprop.getQualifiedName();
    if (qName != null) {
      Var var = syntacticScope.getVar(qName);
      if (var != null && !var.isTypeInferred()) {
        // Handle normal declarations that could not be addressed earlier.
        if (propName.equals("prototype") ||
        // Handle prototype declarations that could not be addressed earlier.
            (!objectType.hasOwnProperty(propName) &&
             (!objectType.isInstanceType() ||
                 (var.isExtern() && !objectType.isNativeObjectType())))) {
          return objectType.defineDeclaredProperty(
              propName, var.getType(), getprop);
        }
      }
    }
    return false;
  }

  private FlowScope traverseName(Node n, FlowScope scope) {
    String varName = n.getString();
    Node value = n.getFirstChild();
    JSType type = n.getJSType();
    if (value != null) {
      scope = traverse(value, scope);
      updateScopeForTypeChange(scope, n, n.getJSType() /* could be null */,
          getJSType(value));
      return scope;
    } else {
      StaticSlot<JSType> var = scope.getSlot(varName);
      if (var != null) {
        // There are two situations where we don't want to use type information
        // from the scope, even if we have it.

        // 1) The var is escaped in a weird way, e.g.,
        // function f() { var x = 3; function g() { x = null } (x); }
        boolean isInferred = var.isTypeInferred();
        boolean unflowable = isInferred &&
            isUnflowable(syntacticScope.getVar(varName));

        // 2) We're reading type information from another scope for an
        // inferred variable.
        // var t = null; function f() { (t); }
        boolean nonLocalInferredSlot =
            isInferred &&
            syntacticScope.getParent() != null &&
            var == syntacticScope.getParent().getSlot(varName);

        if (!unflowable && !nonLocalInferredSlot) {
          type = var.getType();
          if (type == null) {
            type = getNativeType(UNKNOWN_TYPE);
          }
        }
      }
    }
    n.setJSType(type);
    return scope;
  }

  /** Traverse each element of the array. */
  private FlowScope traverseArrayLiteral(Node n, FlowScope scope) {
    scope = traverseChildren(n, scope);
    n.setJSType(getNativeType(ARRAY_TYPE));
    return scope;
  }

  private FlowScope traverseObjectLiteral(Node n, FlowScope scope) {
    JSType type = n.getJSType();
    Preconditions.checkNotNull(type);

    for (Node name = n.getFirstChild(); name != null; name = name.getNext()) {
      scope = traverse(name.getFirstChild(), scope);
    }

    // Object literals can be reflected on other types, or changed with
    // type casts.
    // See CodingConvention#getObjectLiteralCase and goog.object.reflect.
    // Ignore these types of literals.
    // TODO(nicksantos): There should be an "anonymous object" type that
    // we can check for here.
    ObjectType objectType = ObjectType.cast(type);
    if (objectType == null) {
      return scope;
    }

    boolean hasLendsName = n.getJSDocInfo() != null &&
        n.getJSDocInfo().getLendsName() != null;
    if (objectType.hasReferenceName() && !hasLendsName) {
      return scope;
    }

    String qObjName = NodeUtil.getBestLValueName(
        NodeUtil.getBestLValue(n));
    for (Node name = n.getFirstChild(); name != null;
         name = name.getNext()) {
      Node value = name.getFirstChild();
      String memberName = NodeUtil.getObjectLitKeyName(name);
      if (memberName != null) {
        JSType rawValueType =  name.getFirstChild().getJSType();
        JSType valueType = NodeUtil.getObjectLitKeyTypeFromValueType(
            name, rawValueType);
        if (valueType == null) {
          valueType = getNativeType(UNKNOWN_TYPE);
        }
        objectType.defineInferredProperty(memberName, valueType, name);

        // Do normal flow inference if this is a direct property assignment.
        if (qObjName != null && name.isString()) {
          String qKeyName = qObjName + "." + memberName;
          Var var = syntacticScope.getVar(qKeyName);
          JSType oldType = var == null ? null : var.getType();
          if (var != null && var.isTypeInferred()) {
            var.setType(oldType == null ?
                valueType : oldType.getLeastSupertype(oldType));
          }

          scope.inferQualifiedSlot(name, qKeyName,
              oldType == null ? getNativeType(UNKNOWN_TYPE) : oldType,
              valueType);
        }
      } else {
        n.setJSType(getNativeType(UNKNOWN_TYPE));
      }
    }
    return scope;
  }

  private FlowScope traverseAdd(Node n, FlowScope scope) {
    Node left = n.getFirstChild();
    Node right = left.getNext();
    scope = traverseChildren(n, scope);

    JSType leftType = left.getJSType();
    JSType rightType = right.getJSType();

    JSType type = getNativeType(UNKNOWN_TYPE);
    if (leftType != null && rightType != null) {
      boolean leftIsUnknown = leftType.isUnknownType();
      boolean rightIsUnknown = rightType.isUnknownType();
      if (leftIsUnknown && rightIsUnknown) {
        type = getNativeType(UNKNOWN_TYPE);
      } else if ((!leftIsUnknown && leftType.isString()) ||
                 (!rightIsUnknown && rightType.isString())) {
        type = getNativeType(STRING_TYPE);
      } else if (leftIsUnknown || rightIsUnknown) {
        type = getNativeType(UNKNOWN_TYPE);
      } else if (isAddedAsNumber(leftType) && isAddedAsNumber(rightType)) {
        type = getNativeType(NUMBER_TYPE);
      } else {
        type = registry.createUnionType(STRING_TYPE, NUMBER_TYPE);
      }
    }
    n.setJSType(type);

    if (n.isAssignAdd()) {
      updateScopeForTypeChange(scope, left, leftType, type);
    }

    return scope;
  }

  private boolean isAddedAsNumber(JSType type) {
    return type.isSubtype(registry.createUnionType(VOID_TYPE, NULL_TYPE,
        NUMBER_VALUE_OR_OBJECT_TYPE, BOOLEAN_TYPE, BOOLEAN_OBJECT_TYPE));
  }

  private FlowScope traverseHook(Node n, FlowScope scope) {
    Node condition = n.getFirstChild();
    Node trueNode = condition.getNext();
    Node falseNode = n.getLastChild();

    // verify the condition
    scope = traverse(condition, scope);

    // reverse abstract interpret the condition to produce two new scopes
    FlowScope trueScope = reverseInterpreter.
        getPreciserScopeKnowingConditionOutcome(
            condition, scope, true);
    FlowScope falseScope = reverseInterpreter.
        getPreciserScopeKnowingConditionOutcome(
            condition, scope, false);

    // traverse the true node with the trueScope
    traverse(trueNode, trueScope.createChildFlowScope());

    // traverse the false node with the falseScope
    traverse(falseNode, falseScope.createChildFlowScope());

    // meet true and false nodes' types and assign
    JSType trueType = trueNode.getJSType();
    JSType falseType = falseNode.getJSType();
    if (trueType != null && falseType != null) {
      n.setJSType(trueType.getLeastSupertype(falseType));
    } else {
      n.setJSType(null);
    }

    return scope.createChildFlowScope();
  }

  private FlowScope traverseCall(Node n, FlowScope scope) {
    scope = traverseChildren(n, scope);

    Node left = n.getFirstChild();
    JSType functionType = getJSType(left).restrictByNotNullOrUndefined();
    if (functionType != null) {
      if (functionType.isFunctionType()) {
        FunctionType fnType = functionType.toMaybeFunctionType();
        n.setJSType(fnType.getReturnType());
        backwardsInferenceFromCallSite(n, fnType);
      } else if (functionType.equals(getNativeType(CHECKED_UNKNOWN_TYPE))) {
        n.setJSType(getNativeType(CHECKED_UNKNOWN_TYPE));
      }
    }

    scope = tightenTypesAfterAssertions(scope, n);
    return scope;
  }

  private FlowScope tightenTypesAfterAssertions(FlowScope scope,
      Node callNode) {
    Node left = callNode.getFirstChild();
    Node firstParam = left.getNext();
    AssertionFunctionSpec assertionFunctionSpec =
        assertionFunctionsMap.get(left.getQualifiedName());
    if (assertionFunctionSpec == null || firstParam == null) {
      return scope;
    }
    Node assertedNode = assertionFunctionSpec.getAssertedParam(firstParam);
    if (assertedNode == null) {
      return scope;
    }
    JSTypeNative assertedType = assertionFunctionSpec.getAssertedType();
    String assertedNodeName = assertedNode.getQualifiedName();
    // Handle assertions that enforce expressions evaluate to true.
    if (assertedType == null) {
      if (assertedNodeName != null) {
        JSType type = getJSType(assertedNode);
        JSType narrowed = type.restrictByNotNullOrUndefined();
        if (type != narrowed) {
          scope = narrowScope(scope, assertedNode, narrowed);
          callNode.setJSType(narrowed);
        }
      } else if (assertedNode.isAnd() ||
                 assertedNode.isOr()) {
        BooleanOutcomePair conditionOutcomes =
            traverseWithinShortCircuitingBinOp(assertedNode, scope);
        scope = reverseInterpreter.getPreciserScopeKnowingConditionOutcome(
            assertedNode, conditionOutcomes.getOutcomeFlowScope(
                assertedNode.getType(), true), true);
      }
    } else if (assertedNodeName != null) {
      // Handle assertions that enforce expressions are of a certain type.
      JSType type = getJSType(assertedNode);
      JSType narrowed = type.getGreatestSubtype(getNativeType(assertedType));
      if (type != narrowed) {
        scope = narrowScope(scope, assertedNode, narrowed);
        callNode.setJSType(narrowed);
      }
    }
    return scope;
  }

  private FlowScope narrowScope(FlowScope scope, Node node, JSType narrowed) {
    scope = scope.createChildFlowScope();
    if (node.isGetProp()) {
      scope.inferQualifiedSlot(
          node, node.getQualifiedName(), getJSType(node), narrowed);
    } else {
      redeclareSimpleVar(scope, node, narrowed);
    }
    return scope;
  }

  /**
   * We only do forward type inference. We do not do full backwards
   * type inference.
   *
   * In other words, if we have,
   * <code>
   * var x = f();
   * g(x);
   * </code>
   * a forward type-inference engine would try to figure out the type
   * of "x" from the return type of "f". A backwards type-inference engine
   * would try to figure out the type of "x" from the parameter type of "g".
   *
   * However, there are a few special syntactic forms where we do some
   * some half-assed backwards type-inference, because programmers
   * expect it in this day and age. To take an example from java,
   * <code>
   * List<String> x = Lists.newArrayList();
   * </code>
   * The Java compiler will be able to infer the generic type of the List
   * returned by newArrayList().
   *
   * In much the same way, we do some special-case backwards inference for
   * JS. Those cases are enumerated here.
   */
  private void backwardsInferenceFromCallSite(Node n, FunctionType fnType) {
    updateTypeOfParameters(n, fnType);
    updateTypeOfThisOnClosure(n, fnType);
    updateBind(n, fnType);
  }

  /**
   * When "bind" is called on a function, we infer the type of the returned
   * "bound" function by looking at the number of parameters in the call site.
   */
  private void updateBind(Node n, FunctionType fnType) {
    // TODO(nicksantos): Use the coding convention, so that we get goog.bind
    // for free.
    Node calledFn = n.getFirstChild();
    boolean looksLikeBind = calledFn.isGetProp()
        && calledFn.getLastChild().getString().equals("bind");
    if (!looksLikeBind) {
      return;
    }

    Node callTarget = calledFn.getFirstChild();
    FunctionType callTargetFn = getJSType(callTarget)
        .restrictByNotNullOrUndefined().toMaybeFunctionType();
    if (callTargetFn == null) {
      return;
    }

    n.setJSType(callTargetFn.getBindReturnType(n.getChildCount() - 1));
  }

  /**
   * For functions with function parameters, type inference will set the type of
   * a function literal argument from the function parameter type.
   */
  private void updateTypeOfParameters(Node n, FunctionType fnType) {
    int i = 0;
    int childCount = n.getChildCount();
    for (Node iParameter : fnType.getParameters()) {
      if (i + 1 >= childCount) {
        // TypeCheck#visitParametersList will warn so we bail.
        return;
      }

      JSType iParameterType = getJSType(iParameter);
      Node iArgument = n.getChildAtIndex(i + 1);
      JSType iArgumentType = getJSType(iArgument);
      inferPropertyTypesToMatchConstraint(iArgumentType, iParameterType);

      if (iParameterType.isFunctionType()) {
        FunctionType iParameterFnType = iParameterType.toMaybeFunctionType();

        if (iArgument.isFunction() &&
            iArgumentType.isFunctionType() &&
            iArgument.getJSDocInfo() == null) {
          iArgument.setJSType(iParameterFnType);
        }
      }
      i++;
    }
  }

  /**
   * For functions with function(this: T, ...) and T as parameters, type
   * inference will set the type of this on a function literal argument to the
   * the actual type of T.
   */
  private void updateTypeOfThisOnClosure(Node n, FunctionType fnType) {
    // TODO(user): Make the template logic more general.

    if (fnType.getTemplateTypeName() == null) {
      return;
    }

    int i = 0;
    int childCount = n.getChildCount();
    // Find the parameter whose type is the template type.
    for (Node iParameter : fnType.getParameters()) {
      JSType iParameterType =
          getJSType(iParameter).restrictByNotNullOrUndefined();
      if (iParameterType.isTemplateType()) {
        // Find the actual type of this argument.
        ObjectType iArgumentType = null;
        if (i + 1 < childCount) {
          Node iArgument = n.getChildAtIndex(i + 1);
          iArgumentType = getJSType(iArgument)
              .restrictByNotNullOrUndefined()
              .collapseUnion()
              .toObjectType();
          if (iArgumentType == null) {
            compiler.report(
                JSError.make(NodeUtil.getSourceName(iArgument), iArgument,
                    TEMPLATE_TYPE_NOT_OBJECT_TYPE,
                    getJSType(iArgument).toString()));
            return;
          }
        }

        // Find the parameter whose type is function(this: T, ...)
        boolean foundTemplateTypeOfThisParameter = false;
        int j = 0;
        for (Node jParameter : fnType.getParameters()) {
          JSType jParameterType =
              getJSType(jParameter).restrictByNotNullOrUndefined();
          if (jParameterType.isFunctionType()) {
            FunctionType jParameterFnType = jParameterType.toMaybeFunctionType();
            if (jParameterFnType.getTypeOfThis().equals(iParameterType)) {
              foundTemplateTypeOfThisParameter = true;
              // Find the actual type of the this argument.
              if (j + 1 >= childCount) {
                // TypeCheck#visitParameterList will warn so we bail.
                return;
              }
              Node jArgument = n.getChildAtIndex(j + 1);
              JSType jArgumentType = getJSType(jArgument);
              if (jArgument.isFunction() &&
                  jArgumentType.isFunctionType()) {
                if (iArgumentType != null &&
                    // null and undefined get filtered out above.
                    !iArgumentType.isNoType()) {
                  // If it's an function expression, update the type of this
                  // using the actual type of T.
                  FunctionType jArgumentFnType = jArgumentType.toMaybeFunctionType();
                  if (jArgumentFnType.getTypeOfThis().isUnknownType()) {
                    // The new type will be picked up when we traverse the inner
                    // function.
                    jArgument.setJSType(
                        registry.createFunctionTypeWithNewThisType(
                            jArgumentFnType, iArgumentType));
                  }
                } else {
                  // Warn if the anonymous function literal references this.
                  if (NodeUtil.referencesThis(
                          NodeUtil.getFunctionBody(jArgument))) {
                    compiler.report(JSError.make(NodeUtil.getSourceName(n), n,
                        FUNCTION_LITERAL_UNDEFINED_THIS));
                  }
                }
              }
              // TODO(user): Add code to TypeCheck to check that the
              // types of the arguments match.
            }
          }
          j++;
        }

        if (!foundTemplateTypeOfThisParameter) {
          compiler.report(JSError.make(NodeUtil.getSourceName(n), n,
              TEMPLATE_TYPE_OF_THIS_EXPECTED));
          return;
        }
      }
      i++;
    }
  }

  private FlowScope traverseNew(Node n, FlowScope scope) {
    Node constructor = n.getFirstChild();
    scope = traverse(constructor, scope);

    JSType constructorType = constructor.getJSType();
    JSType type = null;
    if (constructorType != null) {
      constructorType = constructorType.restrictByNotNullOrUndefined();
      if (constructorType.isUnknownType()) {
        type = getNativeType(UNKNOWN_TYPE);
      } else {
        FunctionType ct = constructorType.toMaybeFunctionType();
        if (ct == null && constructorType instanceof FunctionType) {
          // If constructorType is a NoObjectType, then toMaybeFunctionType will
          // return null. But NoObjectType implements the FunctionType
          // interface, precisely because it can validly construct objects.
          ct = (FunctionType) constructorType;
        }
        if (ct != null && ct.isConstructor()) {
          type = ct.getInstanceType();
        }
      }
    }
    n.setJSType(type);

    for (Node arg = constructor.getNext(); arg != null; arg = arg.getNext()) {
      scope = traverse(arg, scope);
    }
    return scope;
  }

  private BooleanOutcomePair traverseAnd(Node n, FlowScope scope) {
    return traverseShortCircuitingBinOp(n, scope, true);
  }

  private FlowScope traverseChildren(Node n, FlowScope scope) {
    for (Node el = n.getFirstChild(); el != null; el = el.getNext()) {
      scope = traverse(el, scope);
    }
    return scope;
  }

  private FlowScope traverseGetElem(Node n, FlowScope scope) {
    scope = traverseChildren(n, scope);
    ObjectType objType = ObjectType.cast(
        getJSType(n.getFirstChild()).restrictByNotNullOrUndefined());
    if (objType != null) {
      JSType type = objType.getParameterType();
      if (type != null) {
        n.setJSType(type);
      }
    }
    return dereferencePointer(n.getFirstChild(), scope);
  }

  private FlowScope traverseGetProp(Node n, FlowScope scope) {
    Node objNode = n.getFirstChild();
    Node property = n.getLastChild();
    scope = traverseChildren(n, scope);
    n.setJSType(
        getPropertyType(
            objNode.getJSType(), property.getString(), n, scope));
    return dereferencePointer(n.getFirstChild(), scope);
  }

  /**
   * Suppose X is an object with inferred properties.
   * Suppose also that X is used in a way where it would only type-check
   * correctly if some of those properties are widened.
   * Then we should be polite and automatically widen X's properties for him.
   *
   * For a concrete example, consider:
   * param x {{prop: (number|undefined)}}
   * function f(x) {}
   * f({});
   *
   * If we give the anonymous object an inferred property of (number|undefined),
   * then this code will type-check appropriately.
   */
  private void inferPropertyTypesToMatchConstraint(
      JSType type, JSType constraint) {
    if (type == null || constraint == null) {
      return;
    }

    ObjectType constraintObj =
        ObjectType.cast(constraint.restrictByNotNullOrUndefined());
    if (constraintObj != null && constraintObj.isRecordType()) {
      ObjectType objType = ObjectType.cast(type.restrictByNotNullOrUndefined());
      if (objType != null) {
        for (String prop : constraintObj.getOwnPropertyNames()) {
          JSType propType = constraintObj.getPropertyType(prop);
          if (!objType.isPropertyTypeDeclared(prop)) {
            JSType typeToInfer = propType;
            if (!objType.hasProperty(prop)) {
              typeToInfer =
                  getNativeType(VOID_TYPE).getLeastSupertype(propType);
            }
            objType.defineInferredProperty(prop, typeToInfer, null);
          }
        }
      }
    }
  }

  /**
   * If we access a property of a symbol, then that symbol is not
   * null or undefined.
   */
  private FlowScope dereferencePointer(Node n, FlowScope scope) {
    if (n.isQualifiedName()) {
      JSType type = getJSType(n);
      JSType narrowed = type.restrictByNotNullOrUndefined();
      if (type != narrowed) {
        scope = narrowScope(scope, n, narrowed);
      }
    }
    return scope;
  }

  private JSType getPropertyType(JSType objType, String propName,
      Node n, FlowScope scope) {
    // Scopes sometimes contain inferred type info about qualified names.
    String qualifiedName = n.getQualifiedName();
    StaticSlot<JSType> var = scope.getSlot(qualifiedName);
    if (var != null) {
      JSType varType = var.getType();
      if (varType != null) {
        if (varType.equals(getNativeType(UNKNOWN_TYPE)) &&
            var != syntacticScope.getSlot(qualifiedName)) {
          // If the type of this qualified name has been checked in this scope,
          // then use CHECKED_UNKNOWN_TYPE instead to indicate that.
          return getNativeType(CHECKED_UNKNOWN_TYPE);
        } else {
          return varType;
        }
      }
    }

    JSType propertyType = null;
    if (objType != null) {
      propertyType = objType.findPropertyType(propName);
    }

    if ((propertyType == null || propertyType.isUnknownType()) &&
        qualifiedName != null) {
      // If we find this node in the registry, then we can infer its type.
      ObjectType regType = ObjectType.cast(registry.getType(qualifiedName));
      if (regType != null) {
        propertyType = regType.getConstructor();
      }
    }

    return propertyType;
  }

  private BooleanOutcomePair traverseOr(Node n, FlowScope scope) {
    return traverseShortCircuitingBinOp(n, scope, false);
  }

  private BooleanOutcomePair traverseShortCircuitingBinOp(
      Node n, FlowScope scope, boolean condition) {
    Node left = n.getFirstChild();
    Node right = n.getLastChild();

    // type the left node
    BooleanOutcomePair leftLiterals =
        traverseWithinShortCircuitingBinOp(left,
            scope.createChildFlowScope());
    JSType leftType = left.getJSType();

    // reverse abstract interpret the left node to produce the correct
    // scope in which to verify the right node
    FlowScope rightScope = reverseInterpreter.
        getPreciserScopeKnowingConditionOutcome(
            left, leftLiterals.getOutcomeFlowScope(left.getType(), condition),
            condition);

    // type the right node
    BooleanOutcomePair rightLiterals =
        traverseWithinShortCircuitingBinOp(
            right, rightScope.createChildFlowScope());
    JSType rightType = right.getJSType();

    JSType type;
    BooleanOutcomePair literals;
    if (leftType != null && rightType != null) {
      leftType = leftType.getRestrictedTypeGivenToBooleanOutcome(!condition);
      if (leftLiterals.toBooleanOutcomes ==
          BooleanLiteralSet.get(!condition)) {
        // Use the restricted left type, since the right side never gets
        // evaluated.
        type = leftType;
        literals = leftLiterals;
      } else {
        // Use the join of the restricted left type knowing the outcome of the
        // ToBoolean predicate and of the right type.
        type = leftType.getLeastSupertype(rightType);
        literals =
            getBooleanOutcomePair(leftLiterals, rightLiterals, condition);
      }

      // Exclude the boolean type if the literal set is empty because a boolean
      // can never actually be returned.
      if (literals.booleanValues == BooleanLiteralSet.EMPTY &&
          getNativeType(BOOLEAN_TYPE).isSubtype(type)) {
        // Exclusion only make sense for a union type.
        if (type.isUnionType()) {
          type = type.toMaybeUnionType().getRestrictedUnion(
              getNativeType(BOOLEAN_TYPE));
        }
      }
    } else {
      type = null;
      literals = new BooleanOutcomePair(
          BooleanLiteralSet.BOTH, BooleanLiteralSet.BOTH,
          leftLiterals.getJoinedFlowScope(),
          rightLiterals.getJoinedFlowScope());
    }
    n.setJSType(type);

    return literals;
  }

  private BooleanOutcomePair traverseWithinShortCircuitingBinOp(Node n,
      FlowScope scope) {
    switch (n.getType()) {
      case Token.AND:
        return traverseAnd(n, scope);

      case Token.OR:
        return traverseOr(n, scope);

      default:
        scope = traverse(n, scope);
        return newBooleanOutcomePair(n.getJSType(), scope);
    }
  }

  /**
   * Infers the boolean outcome pair that can be taken by a
   * short-circuiting binary operation ({@code &&} or {@code ||}).
   * @see #getBooleanOutcomes(BooleanLiteralSet, BooleanLiteralSet, boolean)
   */
  BooleanOutcomePair getBooleanOutcomePair(BooleanOutcomePair left,
      BooleanOutcomePair right, boolean condition) {
    return new BooleanOutcomePair(
        getBooleanOutcomes(left.toBooleanOutcomes, right.toBooleanOutcomes,
                           condition),
        getBooleanOutcomes(left.booleanValues, right.booleanValues, condition),
        left.getJoinedFlowScope(), right.getJoinedFlowScope());
  }

  /**
   * Infers the boolean literal set that can be taken by a
   * short-circuiting binary operation ({@code &&} or {@code ||}).
   * @param left the set of possible {@code ToBoolean} predicate results for
   *    the expression on the left side of the operator
   * @param right the set of possible {@code ToBoolean} predicate results for
   *    the expression on the right side of the operator
   * @param condition the left side {@code ToBoolean} predicate result that
   *    causes the right side to get evaluated (i.e. not short-circuited)
   * @return a set of possible {@code ToBoolean} predicate results for the
   *    entire expression
   */
  static BooleanLiteralSet getBooleanOutcomes(BooleanLiteralSet left,
      BooleanLiteralSet right, boolean condition) {
    return right.union(left.intersection(BooleanLiteralSet.get(!condition)));
  }

  /**
   * When traversing short-circuiting binary operations, we need to keep track
   * of two sets of boolean literals:
   * 1. {@code toBooleanOutcomes}: boolean literals as converted from any types,
   * 2. {@code booleanValues}: boolean literals from just boolean types.
   */
  private final class BooleanOutcomePair {
    final BooleanLiteralSet toBooleanOutcomes;
    final BooleanLiteralSet booleanValues;

    // The scope if only half of the expression executed, when applicable.
    final FlowScope leftScope;

    // The scope when the whole expression executed.
    final FlowScope rightScope;

    // The scope when we don't know how much of the expression is executed.
    FlowScope joinedScope = null;

    BooleanOutcomePair(
        BooleanLiteralSet toBooleanOutcomes, BooleanLiteralSet booleanValues,
        FlowScope leftScope, FlowScope rightScope) {
      this.toBooleanOutcomes = toBooleanOutcomes;
      this.booleanValues = booleanValues;
      this.leftScope = leftScope;
      this.rightScope = rightScope;
    }

    /**
     * Gets the safe estimated scope without knowing if all of the
     * subexpressions will be evaluated.
     */
    FlowScope getJoinedFlowScope() {
      if (joinedScope == null) {
        if (leftScope == rightScope) {
          joinedScope = rightScope;
        } else {
          joinedScope = join(leftScope, rightScope);
        }
      }
      return joinedScope;
    }

    /**
     * Gets the outcome scope if we do know the outcome of the entire
     * expression.
     */
    FlowScope getOutcomeFlowScope(int nodeType, boolean outcome) {
      if (nodeType == Token.AND && outcome ||
          nodeType == Token.OR && !outcome) {
        // We know that the whole expression must have executed.
        return rightScope;
      } else {
        return getJoinedFlowScope();
      }
    }
  }

  private BooleanOutcomePair newBooleanOutcomePair(
      JSType jsType, FlowScope flowScope) {
    if (jsType == null) {
      return new BooleanOutcomePair(
          BooleanLiteralSet.BOTH, BooleanLiteralSet.BOTH, flowScope, flowScope);
    }
    return new BooleanOutcomePair(jsType.getPossibleToBooleanOutcomes(),
        registry.getNativeType(BOOLEAN_TYPE).isSubtype(jsType) ?
            BooleanLiteralSet.BOTH : BooleanLiteralSet.EMPTY,
        flowScope, flowScope);
  }

  private void redeclareSimpleVar(
      FlowScope scope, Node nameNode, JSType varType) {
    Preconditions.checkState(nameNode.isName());
    String varName = nameNode.getString();
    if (varType == null) {
      varType = getNativeType(JSTypeNative.UNKNOWN_TYPE);
    }
    if (isUnflowable(syntacticScope.getVar(varName))) {
      return;
    }
    scope.inferSlotType(varName, varType);
  }

  private boolean isUnflowable(Var v) {
    return v != null && v.isLocal() && v.isMarkedEscaped() &&
        // It's OK to flow a variable in the scope where it's escaped.
        v.getScope() == syntacticScope;
  }

  /**
   * This method gets the JSType from the Node argument and verifies that it is
   * present.
   */
  private JSType getJSType(Node n) {
    JSType jsType = n.getJSType();
    if (jsType == null) {
      // TODO(nicksantos): This branch indicates a compiler bug, not worthy of
      // halting the compilation but we should log this and analyze to track
      // down why it happens. This is not critical and will be resolved over
      // time as the type checker is extended.
      return getNativeType(UNKNOWN_TYPE);
    } else {
      return jsType;
    }
  }

  private JSType getNativeType(JSTypeNative typeId) {
    return registry.getNativeType(typeId);
  }
}
