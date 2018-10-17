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
import static com.google.javascript.rhino.jstype.JSTypeNative.ARRAY_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.CHECKED_UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.ITERABLE_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.I_TEMPLATE_ARRAY_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_VALUE_OR_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.CodingConvention.AssertionFunctionSpec;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.type.FlowScope;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.BooleanLiteralSet;
import com.google.javascript.rhino.jstype.FunctionBuilder;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ModificationVisitor;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticTypedSlot;
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.jstype.TemplateTypeMap;
import com.google.javascript.rhino.jstype.TemplateTypeMapReplacer;
import com.google.javascript.rhino.jstype.TemplatizedType;
import com.google.javascript.rhino.jstype.UnionType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * Type inference within a script node or a function body, using the data-flow
 * analysis framework.
 *
 */
class TypeInference
    extends DataFlowAnalysis.BranchedForwardDataFlowAnalysis<Node, FlowScope> {

  // TODO(johnlenz): We no longer make this check, but we should.
  static final DiagnosticType FUNCTION_LITERAL_UNDEFINED_THIS =
    DiagnosticType.warning(
        "JSC_FUNCTION_LITERAL_UNDEFINED_THIS",
        "Function literal argument refers to undefined this argument");

  private final AbstractCompiler compiler;
  private final JSTypeRegistry registry;
  private final ReverseAbstractInterpreter reverseInterpreter;
  private final FlowScope functionScope;
  private final FlowScope bottomScope;
  private final TypedScope containerScope;
  private final TypedScopeCreator scopeCreator;
  private final Map<String, AssertionFunctionSpec> assertionFunctionsMap;

  // Scopes that have had their unbound untyped vars inferred as undefined.
  private final Set<TypedScope> inferredUnboundVars = new HashSet<>();

  // For convenience
  private final ObjectType unknownType;

  TypeInference(AbstractCompiler compiler, ControlFlowGraph<Node> cfg,
                ReverseAbstractInterpreter reverseInterpreter,
                TypedScope syntacticScope, TypedScopeCreator scopeCreator,
                Map<String, AssertionFunctionSpec> assertionFunctionsMap) {
    super(cfg, new LinkedFlowScope.FlowScopeJoinOp());
    this.compiler = compiler;
    this.registry = compiler.getTypeRegistry();
    this.reverseInterpreter = reverseInterpreter;
    this.unknownType = registry.getNativeObjectType(UNKNOWN_TYPE);

    this.containerScope = syntacticScope;

    this.scopeCreator = scopeCreator;
    this.assertionFunctionsMap = assertionFunctionsMap;

    FlowScope entryScope =
        inferDeclarativelyUnboundVarsWithoutTypes(
            LinkedFlowScope.createEntryLattice(syntacticScope));

    this.functionScope = inferParameters(entryScope);

    this.bottomScope =
        LinkedFlowScope.createEntryLattice(
            TypedScope.createLatticeBottom(syntacticScope.getRootNode()));
  }

  @CheckReturnValue
  private FlowScope inferDeclarativelyUnboundVarsWithoutTypes(FlowScope flow) {
    TypedScope scope = (TypedScope) flow.getDeclarationScope();
    if (!inferredUnboundVars.add(scope)) {
      return flow;
    }
    // For each local variable declared with the VAR keyword, the entry
    // type is VOID.
    for (TypedVar var : scope.getDeclarativelyUnboundVarsWithoutTypes()) {
      if (isUnflowable(var)) {
        continue;
      }

      flow = flow.inferSlotType(var.getName(), getNativeType(VOID_TYPE));
    }
    return flow;
  }

  /** Infers all of a function's parameters if their types aren't declared. */
  @SuppressWarnings("ReferenceEquality") // unknownType is a singleton
  private FlowScope inferParameters(FlowScope entryFlowScope) {
    Node functionNode = containerScope.getRootNode();
    Node astParameters = functionNode.getSecondChild();
    Node iifeArgumentNode = null;

    if (NodeUtil.isInvocationTarget(functionNode)) {
      iifeArgumentNode = functionNode.getNext();
    }

    FunctionType functionType = JSType.toMaybeFunctionType(functionNode.getJSType());
    if (functionType != null) {
      Node parameterTypes = functionType.getParametersNode();
      if (parameterTypes != null) {
        Node parameterTypeNode = parameterTypes.getFirstChild();
        for (Node astParameter : astParameters.children()) {
          boolean isRest = false;
          Node defaultValue = null;
          if (astParameter.isDefaultValue()) {
            defaultValue = astParameter.getSecondChild();
            astParameter = astParameter.getFirstChild();
          }
          if (astParameter.isRest()) {
            // e.g. `function f(p1, ...restParamName) {}`
            // set astParameter = restParamName
            astParameter = astParameter.getOnlyChild();
            isRest = true;
          }

          if (iifeArgumentNode != null && iifeArgumentNode.isSpread()) {
            // block inference on all parameters that might possibly be set by a spread, e.g. `z` in
            // (function f(x, y, z = 1))(...[1, 2], 'foo')
            iifeArgumentNode = null;
          }
          JSType inferredType = null;

          if (iifeArgumentNode != null) {
            inferredType = iifeArgumentNode.getJSType();
          } else if (parameterTypeNode != null) {
            inferredType = parameterTypeNode.getJSType();
          }

          if (inferredType != null && astParameter.isDestructuringPattern()) {
            entryFlowScope =
                traverseDestructuringPatternHelper(
                    astParameter,
                    entryFlowScope,
                    inferredType,
                    (FlowScope scope, Node lvalue, JSType type) -> {
                      TypedVar var = containerScope.getVar(lvalue.getString());
                      checkNotNull(var);
                      // This condition will trigger on cases like
                      //   (function f({x}) {})({x: 3})
                      // where `x` is of unknown type during the typed scope creation phase, but
                      // here we can infer that it is of type `number`
                      // Don't update the variable if it has a declared type or was already
                      // inferred to be something other than unknown.
                      if (var.isTypeInferred() && var.getType() == unknownType) {
                        var.setType(type);
                        lvalue.setJSType(type);
                      }
                      if (lvalue.getParent().isDefaultValue()) {
                        // e.g. given
                        //   /** @param {{age: (number|undefined)}} data */
                        //   function f({age = 99}) {}
                        // infer that `age` is now a `number` and not `number|undefined`
                        // but don't change the 'declared type' of `age`
                        // TODO(b/117162687): allow people to narrow the declared type to
                        // exclude 'undefined' inside the function body.
                        scope =
                            updateScopeForAssignment(scope, lvalue, type, AssignmentType.ASSIGN);
                      }
                      return scope;
                    });
          } else if (inferredType != null) {
            TypedVar var = containerScope.getVar(astParameter.getString());
            checkNotNull(var);
            if (var.isTypeInferred() && (var.getType() == unknownType || isRest)) {
              if (isRest) {
                // convert 'number' into 'Array<number>' for rest parameters
                inferredType =
                    registry.createTemplatizedType(
                        registry.getNativeObjectType(ARRAY_TYPE), inferredType);
              }
              var.setType(inferredType);
              astParameter.setJSType(inferredType);
            }
          }

          if (parameterTypeNode != null) {
            parameterTypeNode = parameterTypeNode.getNext();
          }
          if (iifeArgumentNode != null) {
            iifeArgumentNode = iifeArgumentNode.getNext();
          }

          // 1. do type inference within the default value expression
          // 2. add a flow scope slot for the assignment to the parameter. (which will not matter
          //     for declared parameters, just inferred parameters.
          if (defaultValue != null) {
            traverse(defaultValue, entryFlowScope);
            JSType newType =
                registry.createUnionType(
                    getJSType(astParameter).restrictByNotUndefined(), getJSType(defaultValue));
            entryFlowScope =
                updateScopeForAssignment(
                    entryFlowScope, astParameter, newType, AssignmentType.ASSIGN);
          }
        }
      }
    }
    return entryFlowScope;
  }

  /** Abstracts logic for declaring an lvalue in a particular scope */
  interface TypeDeclaringCallback {

    /**
     * Updates the given scope upon seeing an assignment or declaration
     *
     * @param scope the scope we are in
     * @param lvalue the value being updated, a NAME, GETPROP, GETELEM, or CAST
     * @param type the type we've inferred for the lvalue
     * @return the updated flow scope
     */
    FlowScope declareTypeInScope(FlowScope scope, Node lvalue, @Nullable JSType type);
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
  @CheckReturnValue
  FlowScope flowThrough(Node n, FlowScope input) {
    // If we have not walked a path from <entry> to <n>, then we don't
    // want to infer anything about this scope.
    if (input == bottomScope) {
      return input;
    }

    Node root = NodeUtil.getEnclosingScopeRoot(n);
    FlowScope output = input.withSyntacticScope(scopeCreator.createScope(root));
    output = inferDeclarativelyUnboundVarsWithoutTypes(output);
    output = traverse(n, output);
    return output;
  }

  @Override
  @SuppressWarnings({"fallthrough", "incomplete-switch"})
  List<FlowScope> branchedFlowThrough(Node source, FlowScope input) {
    // NOTE(nicksantos): Right now, we just treat ON_EX edges like UNCOND
    // edges. If we wanted to be perfect, we'd actually JOIN all the out
    // lattices of this flow with the in lattice, and then make that the out
    // lattice for the ON_EX edge. But it's probably too expensive to be
    // worthwhile.
    FlowScope output = flowThrough(source, input);
    Node condition = null;
    FlowScope conditionFlowScope = null;
    BooleanOutcomePair conditionOutcomes = null;

    List<DiGraphEdge<Node, Branch>> branchEdges = getCfg().getOutEdges(source);
    List<FlowScope> result = new ArrayList<>(branchEdges.size());
    for (DiGraphEdge<Node, Branch> branchEdge : branchEdges) {
      Branch branch = branchEdge.getValue();
      FlowScope newScope = output;

      switch (branch) {
        case ON_TRUE:
          if (source.isForIn() || source.isForOf()) {
            Node item = source.getFirstChild();
            Node obj = item.getNext();

            FlowScope informed = traverse(obj, output);

            final AssignmentType assignmentType;
            if (NodeUtil.isNameDeclaration(item)) {
              item = item.getFirstChild();
              assignmentType = AssignmentType.DECLARATION;
            } else {
              assignmentType = AssignmentType.ASSIGN;
            }
            if (item.isDestructuringLhs()) {
              item = item.getFirstChild();
            }
            if (source.isForIn()) {
              // item is assigned a property name, so its type should be string
              JSType iterKeyType = getNativeType(STRING_TYPE);
              JSType objType = getJSType(obj).autobox();
              JSType objIndexType =
                  objType
                      .getTemplateTypeMap()
                      .getResolvedTemplateType(registry.getObjectIndexKey());
              if (objIndexType != null && !objIndexType.isUnknownType()) {
                JSType narrowedKeyType = iterKeyType.getGreatestSubtype(objIndexType);
                if (!narrowedKeyType.isEmptyType()) {
                  iterKeyType = narrowedKeyType;
                }
              }
              if (item.isName()) {
                informed = redeclareSimpleVar(informed, item, iterKeyType);
              } else if (item.isDestructuringPattern()) {
                informed =
                    traverseDestructuringPattern(item, informed, iterKeyType, assignmentType);
              }
            } else {
              // for/of. The type of `item` is the type parameter of the Iterable type.
              JSType objType = getJSType(obj).autobox();
              // NOTE: this returns the UNKNOWN_TYPE if objType does not implement Iterable
              JSType newType = objType.getInstantiatedTypeArgument(getNativeType(ITERABLE_TYPE));

              // Note that `item` can be an arbitrary LHS expression we need to check.
              if (item.isDestructuringPattern()) {
                // for (const {x, y} of data) {
                informed = traverseDestructuringPattern(item, informed, newType, assignmentType);
              } else {
                informed = traverse(item, informed);
                informed = updateScopeForAssignment(informed, item, newType, assignmentType);
              }
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
                conditionFlowScope = traverse(condition.getFirstChild(), output);
              }
            }
          }

          if (condition != null) {
            if (condition.isAnd() || condition.isOr()) {
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
                conditionOutcomes =
                    condition.isAnd()
                        ? traverseAnd(condition, output)
                        : traverseOr(condition, output);
              }
              newScope =
                  reverseInterpreter.getPreciserScopeKnowingConditionOutcome(
                      condition,
                      conditionOutcomes.getOutcomeFlowScope(
                          condition.getToken(), branch == Branch.ON_TRUE),
                      branch == Branch.ON_TRUE);
            } else {
              // conditionFlowScope is cached from previous iterations
              // of the loop.
              if (conditionFlowScope == null) {
                conditionFlowScope = traverse(condition, output);
              }
              newScope =
                  reverseInterpreter.getPreciserScopeKnowingConditionOutcome(
                      condition, conditionFlowScope, branch == Branch.ON_TRUE);
            }
          }
          break;
        default:
          break;
      }

      result.add(newScope);
    }
    return result;
  }

  private FlowScope traverse(Node n, FlowScope scope) {
    switch (n.getToken()) {
      case ASSIGN:
        scope = traverseAssign(n, scope);
        break;

      case NAME:
        scope = traverseName(n, scope);
        break;

      case GETPROP:
        scope = traverseGetProp(n, scope);
        break;

      case CLASS:
        scope = traverseClass(n, scope);
        break;

      case AND:
        scope = traverseAnd(n, scope).getJoinedFlowScope();
        break;

      case OR:
        scope = traverseOr(n, scope).getJoinedFlowScope();
        break;

      case HOOK:
        scope = traverseHook(n, scope);
        break;

      case OBJECTLIT:
        scope = traverseObjectLiteral(n, scope);
        break;

      case CALL:
        scope = traverseFunctionInvocation(n, scope);
        scope = tightenTypesAfterAssertions(scope, n);
        break;

      case NEW:
        scope = traverseNew(n, scope);
        break;

      case NEW_TARGET:
        traverseNewTarget(n);
        break;

      case ASSIGN_ADD:
      case ADD:
        scope = traverseAdd(n, scope);
        break;

      case POS:
      case NEG:
        scope = traverse(n.getFirstChild(), scope);  // Find types.
        n.setJSType(getNativeType(NUMBER_TYPE));
        break;

      case ARRAYLIT:
        scope = traverseArrayLiteral(n, scope);
        break;

      case THIS:
        n.setJSType(scope.getTypeOfThis());
        break;

      case ASSIGN_LSH:
      case ASSIGN_RSH:
      case ASSIGN_URSH:
      case ASSIGN_DIV:
      case ASSIGN_MOD:
      case ASSIGN_BITAND:
      case ASSIGN_BITXOR:
      case ASSIGN_BITOR:
      case ASSIGN_MUL:
      case ASSIGN_SUB:
      case ASSIGN_EXPONENT:
        scope = traverseAssignOp(n, scope, getNativeType(NUMBER_TYPE));

        break;

      case LSH:
      case RSH:
      case URSH:
      case DIV:
      case MOD:
      case BITAND:
      case BITXOR:
      case BITOR:
      case MUL:
      case SUB:
      case DEC:
      case INC:
      case BITNOT:
      case EXPONENT:
        scope = traverseChildren(n, scope);
        n.setJSType(getNativeType(NUMBER_TYPE));
        break;

      case COMMA:
        scope = traverseChildren(n, scope);
        n.setJSType(getJSType(n.getLastChild()));
        break;

      case TEMPLATELIT:
      case TYPEOF:
        scope = traverseChildren(n, scope);
        n.setJSType(getNativeType(STRING_TYPE));
        break;

      case TEMPLATELIT_SUB:
        // TEMPLATELIT_SUBs are untyped but we do need to traverse their children.
        scope = traverseChildren(n, scope);
        break;

      case TAGGED_TEMPLATELIT:
        scope = traverseFunctionInvocation(n, scope);
        break;

      case DELPROP:
      case LT:
      case LE:
      case GT:
      case GE:
      case NOT:
      case EQ:
      case NE:
      case SHEQ:
      case SHNE:
      case INSTANCEOF:
      case IN:
        scope = traverseChildren(n, scope);
        n.setJSType(getNativeType(BOOLEAN_TYPE));
        break;

      case GETELEM:
        scope = traverseGetElem(n, scope);
        break;

      case EXPR_RESULT:
        scope = traverseChildren(n, scope);
        if (n.getFirstChild().isGetProp()) {
          Node getprop = n.getFirstChild();
          ObjectType ownerType = ObjectType.cast(
              getJSType(getprop.getFirstChild()).restrictByNotNullOrUndefined());
          if (ownerType != null) {
            ensurePropertyDeclaredHelper(getprop, ownerType, scope);
          }
        }
        break;

      case SWITCH:
        scope = traverse(n.getFirstChild(), scope);
        break;

      case RETURN:
        scope = traverseReturn(n, scope);
        break;

      case YIELD:
        scope = traverseChildren(n, scope);
        n.setJSType(getNativeType(UNKNOWN_TYPE));
        break;

      case VAR:
      case LET:
      case CONST:
        scope = traverseDeclaration(n, scope);
        break;

      case THROW:
        scope = traverseChildren(n, scope);
        break;

      case CATCH:
        scope = traverseCatch(n, scope);
        break;

      case CAST:
        scope = traverseChildren(n, scope);
        JSDocInfo info = n.getJSDocInfo();
        if (info != null && info.hasType()) {
          n.setJSType(info.getType().evaluate(scope.getDeclarationScope(), registry));
        }
        break;

      case SUPER:
        traverseSuper(n);
        break;

      case SPREAD:
        // The spread itself has no type, but the expression it contains does and may affect
        // type inference.
        scope = traverseChildren(n, scope);
        break;

      case AWAIT:
        scope = traverseAwait(n, scope);
        break;

      case VOID:
        n.setJSType(getNativeType(VOID_TYPE));
        scope = traverseChildren(n, scope);
        break;

      case ROOT:
      case SCRIPT:
      case FUNCTION:
      case PARAM_LIST:
      case BLOCK:
      case EMPTY:
      case IF:
      case WHILE:
      case DO:
      case FOR:
      case FOR_IN:
      case FOR_OF:
      case FOR_AWAIT_OF:
      case BREAK:
      case CONTINUE:
      case TRY:
      case CASE:
      case DEFAULT_CASE:
      case WITH:
      case DEBUGGER:
        // These don't need to be typed here, since they only affect control flow.
        break;

      case TRUE:
      case FALSE:
      case STRING:
      case NUMBER:
      case NULL:
      case REGEXP:
      case TEMPLATELIT_STRING:
        // Primitives are typed in TypedScopeCreator.AbstractScopeBuilder#attachLiteralTypes
        break;

      default:
        throw new IllegalStateException(
            "Type inference doesn't know to handle token " + n.getToken());
    }

    return scope;
  }

  private void traverseSuper(Node superNode) {
    // Find the closest non-arrow function (TODO(sdh): this could be an AbstractScope method).
    TypedScope scope = containerScope;
    while (scope != null && !NodeUtil.isVanillaFunction(scope.getRootNode())) {
      scope = scope.getParent();
    }
    if (scope == null) {
      superNode.setJSType(unknownType);
      return;
    }
    Node root = scope.getRootNode();
    JSType jsType = root.getJSType();
    FunctionType functionType = jsType != null ? jsType.toMaybeFunctionType() : null;
    ObjectType superNodeType = unknownType;
    Node context = superNode.getParent();
    // NOTE: we currently transpile subclass constructors to use "super.apply", which is not
    // actually valid ES6.  For now, provide a special case to support this, but it should be
    // removed once class transpilation is after type checking.
    if (context.isCall()) {
      // Call the superclass constructor.
      if (functionType != null && functionType.isConstructor()) {
        FunctionType superCtor = functionType.getSuperClassConstructor();
        if (superCtor != null) {
          superNodeType = superCtor;
        }
      }
    } else if (context.isGetProp() || context.isGetElem()) {
      // TODO(sdh): once getTypeOfThis supports statics, we can get rid of this branch, as well as
      // the vanilla function search at the top and just return functionScope.getVar("super").
      if (root.getParent().isStaticMember()) {
        // Since the root is a static member, we're guaranteed that the parent scope is a class.
        Node classNode = scope.getParent().getRootNode();
        checkState(classNode.isClass());
        FunctionType thisCtor = JSType.toMaybeFunctionType(classNode.getJSType());
        if (thisCtor != null) {
          FunctionType superCtor = thisCtor.getSuperClassConstructor();
          if (superCtor != null) {
            superNodeType = superCtor;
          }
        }
      } else if (functionType != null) {
        // Refer to a superclass instance property.
        ObjectType thisInstance = ObjectType.cast(functionType.getTypeOfThis());
        if (thisInstance != null) {
          FunctionType superCtor = thisInstance.getSuperClassConstructor();
          if (superCtor != null) {
            ObjectType superInstance = superCtor.getInstanceType();
            if (superInstance != null) {
              superNodeType = superInstance;
            }
          }
        }
      }
    }
    superNode.setJSType(superNodeType);
  }

  private void traverseNewTarget(Node newTargetNode) {
    // new.target is (undefined|!Function) within a vanilla function and !Function within an ES6
    // constructor.
    // Find the closest non-arrow function (TODO(sdh): this could be an AbstractScope method).
    TypedScope scope = containerScope;
    while (scope != null && !NodeUtil.isVanillaFunction(scope.getRootNode())) {
      scope = scope.getParent();
    }
    if (scope == null) {
      // NOTE: we already have a parse error for new.target outside a function.  The only other case
      // where this might happen is a top-level arrow function, which is a parse error in the VM,
      // but allowed by our parser.
      newTargetNode.setJSType(unknownType);
      return;
    }
    Node root = scope.getRootNode();
    Node parent = root.getParent();
    if (parent.isMemberFunctionDef() && parent.getGrandparent().isClass()) {
      // In an ES6 constuctor, new.target may not be undefined.  In any other method, it must be
      // undefined, since methods are not constructable.
      JSTypeNative type =
          "constructor".equals(parent.getString()) ? JSTypeNative.U2U_CONSTRUCTOR_TYPE : VOID_TYPE;
      newTargetNode.setJSType(registry.getNativeType(type));
    } else {
      // Other functions also include undefined, in case they are not called with 'new'.
      newTargetNode.setJSType(
          registry.createUnionType(
              registry.getNativeType(JSTypeNative.U2U_CONSTRUCTOR_TYPE),
              registry.getNativeType(VOID_TYPE)));
    }
  }

  /** Traverse a return value. */
  @CheckReturnValue
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
   * Any value can be thrown, so it's really impossible to determine the type of a CATCH param.
   * Treat it as the UNKNOWN type.
   */
  @CheckReturnValue
  private FlowScope traverseCatch(Node catchNode, FlowScope scope) {
    Node catchTarget = catchNode.getFirstChild();
    if (catchTarget.isName()) {
      // TODO(lharker): is this case even necessary? seems like TypedScopeCreator handles it
      Node name = catchNode.getFirstChild();
      JSType type;
      // If the catch expression name was declared in the catch use that type,
      // otherwise use "unknown".
      JSDocInfo info = name.getJSDocInfo();
      if (info != null && info.hasType()) {
        type = info.getType().evaluate(scope.getDeclarationScope(), registry);
      } else {
        type = getNativeType(JSTypeNative.UNKNOWN_TYPE);
      }
      name.setJSType(type);
      return redeclareSimpleVar(scope, name, type);
    } else if (catchTarget.isDestructuringPattern()) {
      Node pattern = catchNode.getFirstChild();
      return traverseDestructuringPattern(pattern, scope, unknownType, AssignmentType.DECLARATION);
    } else {
      checkState(catchTarget.isEmpty(), catchTarget);
      // ES2019 allows `try {} catch {}` with no catch expression
      return scope;
    }
  }

  @CheckReturnValue
  private FlowScope traverseAssign(Node n, FlowScope scope) {
    Node target = n.getFirstChild();
    Node value = n.getLastChild();
    if (target.isDestructuringPattern()) {
      scope = traverse(value, scope);
      JSType valueType = getJSType(value);
      n.setJSType(valueType);
      return traverseDestructuringPattern(target, scope, valueType, AssignmentType.ASSIGN);
    } else {
      scope = traverseChildren(n, scope);

      JSType valueType = getJSType(value);
      n.setJSType(valueType);

      return updateScopeForAssignment(scope, target, valueType, AssignmentType.ASSIGN);
    }
  }

  @CheckReturnValue
  private FlowScope traverseAssignOp(Node n, FlowScope scope, JSType resultType) {
    Node left = n.getFirstChild();
    scope = traverseChildren(n, scope);

    n.setJSType(resultType);

    // The lhs is both an input and an output, so don't update the input type here.
    return updateScopeForAssignment(
        scope, left, resultType, /* updateNode= */ null, AssignmentType.ASSIGN);
  }

  private static boolean isInExternFile(Node n) {
    return NodeUtil.getSourceFile(n).isExtern();
  }

  private static boolean isPossibleMixinApplication(Node lvalue, Node rvalue) {
    if (isInExternFile(lvalue)) {
      return true;
    }

    JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(lvalue);
    return jsdoc != null
        && jsdoc.isConstructor()
        && jsdoc.getImplementedInterfaceCount() > 0
        && lvalue.isQualifiedName()
        && rvalue.isCall();
  }

  /**
   * @param constructor A constructor function defined by a call, which may be a mixin application.
   *     The constructor implements at least one interface. If the constructor is missing some
   *     properties of the inherited interfaces, this method declares these properties.
   */
  private static void addMissingInterfaceProperties(JSType constructor) {
    if (constructor != null && constructor.isConstructor()) {
      FunctionType f = constructor.toMaybeFunctionType();
      ObjectType proto = f.getPrototype();
      for (ObjectType interf : f.getImplementedInterfaces()) {
        for (String pname : interf.getPropertyNames()) {
          if (!proto.hasProperty(pname)) {
            proto.defineDeclaredProperty(pname, interf.getPropertyType(pname), null);
          }
        }
      }
    }
  }

  // Either a combined declaration/initialization or a regular assignment
  private enum AssignmentType {
    DECLARATION, // var x = 3;
    ASSIGN // `a.b.c = d;` or `x = 4;`
  }

  @CheckReturnValue
  private FlowScope updateScopeForAssignment(
      FlowScope scope, Node target, JSType resultType, AssignmentType type) {
    return updateScopeForAssignment(scope, target, resultType, target, type);
  }

  /** Updates the scope according to the result of an assignment. */
  @CheckReturnValue
  private FlowScope updateScopeForAssignment(
      FlowScope scope, Node target, JSType resultType, Node updateNode, AssignmentType type) {
    checkNotNull(resultType);
    checkState(updateNode == null || updateNode == target);

    JSType targetType = target.getJSType(); // may be null

    Node right = NodeUtil.getRValueOfLValue(target);
    if (isPossibleMixinApplication(target, right)) {
      addMissingInterfaceProperties(targetType);
    }

    switch (target.getToken()) {
      case NAME:
        String varName = target.getString();
        TypedVar var = getDeclaredVar(scope, varName);
        JSType varType = var == null ? null : var.getType();
        boolean isVarDeclaration =
            type == AssignmentType.DECLARATION
                && varType != null
                && !var.isTypeInferred()
                && var.getNameNode() != null;

        boolean isTypelessConstDecl =
            isVarDeclaration
                && NodeUtil.isConstantDeclaration(
                    compiler.getCodingConvention(), var.getJSDocInfo(), var.getNameNode())
                && !(var.getJSDocInfo() != null && var.getJSDocInfo().hasType());

        // When looking at VAR initializers for declared VARs, we tend
        // to use the declared type over the type it's being
        // initialized to in the global scope.
        //
        // For example,
        // /** @param {number} */ var f = goog.abstractMethod;
        // it's obvious that the programmer wants you to use
        // the declared function signature, not the inferred signature.
        //
        // Or,
        // /** @type {Object.<string>} */ var x = {};
        // the one-time anonymous object on the right side
        // is as narrow as it can possibly be, but we need to make
        // sure we back-infer the <string> element constraint on
        // the left hand side, so we use the left hand side.

        boolean isVarTypeBetter = isVarDeclaration
            // Makes it easier to check for NPEs.
            && !resultType.isNullType() && !resultType.isVoidType()
            // Do not use the var type if the declaration looked like
            // /** @const */ var x = 3;
            // because this type was computed from the RHS
            && !isTypelessConstDecl;

        // TODO(nicksantos): This might be a better check once we have
        // back-inference of object/array constraints.  It will probably
        // introduce more type warnings.  It uses the result type iff it's
        // strictly narrower than the declared var type.
        //
        //boolean isVarTypeBetter = isVarDeclaration &&
        //    (varType.restrictByNotNullOrUndefined().isSubtype(resultType)
        //     || !resultType.isSubtype(varType));

        if (isVarTypeBetter) {
          scope = redeclareSimpleVar(scope, target, varType);
        } else {
          scope = redeclareSimpleVar(scope, target, resultType);
        }

        if (updateNode != null) {
          updateNode.setJSType(resultType);
        }

        if (var != null
            && var.isTypeInferred()
            // Don't change the typed scope to include "undefined" upon seeing "let foo;", because
            // this is incompatible with how we currently handle VARs and breaks existing code.
            // TODO(sdh): remove this condition after cleaning up code depending on it.
            && !(target.getParent().isLet() && !target.hasChildren())) {
          JSType oldType = var.getType();
          var.setType(oldType == null ? resultType : oldType.getLeastSupertype(resultType));
        } else if (isTypelessConstDecl) {
          // /** @const */ var x = y;
          // should be redeclared, so that the type of y
          // gets propagated to inner scopes.
          var.setType(resultType);
        }
        break;
      case GETPROP:
        if (target.isQualifiedName()) {
          String qualifiedName = target.getQualifiedName();
          boolean declaredSlotType = false;
          JSType rawObjType = target.getFirstChild().getJSType();
          if (rawObjType != null) {
            ObjectType objType = ObjectType.cast(
                rawObjType.restrictByNotNullOrUndefined());
            if (objType != null) {
              String propName = target.getLastChild().getString();
              declaredSlotType = objType.isPropertyTypeDeclared(propName);
            }
          }
          JSType safeLeftType = targetType == null ? unknownType : targetType;
          scope =
              scope.inferQualifiedSlot(
                  target, qualifiedName, safeLeftType, resultType, declaredSlotType);
        }

        if (updateNode != null) {
          updateNode.setJSType(resultType);
        }
        ensurePropertyDefined(target, resultType, scope);
        break;
      default:
        break;
    }
    return scope;
  }

  /** Defines a property if the property has not been defined yet. */
  private void ensurePropertyDefined(Node getprop, JSType rightType, FlowScope scope) {
    String propName = getprop.getLastChild().getString();
    Node obj = getprop.getFirstChild();
    JSType nodeType = getJSType(obj);
    ObjectType objectType = ObjectType.cast(
        nodeType.restrictByNotNullOrUndefined());
    boolean propCreationInConstructor =
        obj.isThis() && getJSType(containerScope.getRootNode()).isConstructor();

    if (objectType == null) {
      registry.registerPropertyOnType(propName, nodeType);
    } else {
      if (nodeType.isStruct() && !objectType.hasProperty(propName)) {
        // In general, we don't want to define a property on a struct object,
        // b/c TypeCheck will later check for improper property creation on
        // structs. There are two exceptions.
        // 1) If it's a property created inside the constructor, on the newly
        //    created instance, allow it.
        // 2) If it's a prototype property, allow it. For example:
        //    Foo.prototype.bar = baz;
        //    where Foo.prototype is a struct and the assignment happens at the
        //    top level and the constructor Foo is defined in the same file.
        boolean staticPropCreation = false;
        Node maybeAssignStm = getprop.getGrandparent();
        if (containerScope.isGlobal() && NodeUtil.isPrototypePropertyDeclaration(maybeAssignStm)) {
          String propCreationFilename = maybeAssignStm.getSourceFileName();
          Node ctor = objectType.getOwnerFunction().getSource();
          if (ctor != null && ctor.getSourceFileName().equals(propCreationFilename)) {
            staticPropCreation = true;
          }
        }
        if (!propCreationInConstructor && !staticPropCreation) {
          return; // Early return to avoid creating the property below.
        }
      }

      if (ensurePropertyDeclaredHelper(getprop, objectType, scope)) {
        return;
      }

      if (!objectType.isPropertyTypeDeclared(propName)) {
        // We do not want a "stray" assign to define an inferred property
        // for every object of this type in the program. So we use a heuristic
        // approach to determine whether to infer the property.
        //
        // 1) If the property is already defined, join it with the previously
        //    inferred type.
        // 2) If this isn't an instance object, define it.
        // 3) If the property of an object is being assigned in the constructor,
        //    define it.
        // 4) If this is a stub, define it.
        // 5) Otherwise, do not define the type, but declare it in the registry
        //    so that we can use it for missing property checks.
        if (objectType.hasProperty(propName) || !objectType.isInstanceType()) {
          if ("prototype".equals(propName)) {
            objectType.defineDeclaredProperty(propName, rightType, getprop);
          } else {
            objectType.defineInferredProperty(propName, rightType, getprop);
          }
        } else if (propCreationInConstructor) {
          objectType.defineInferredProperty(propName, rightType, getprop);
        } else {
          registry.registerPropertyOnType(propName, objectType);
        }
      }
    }
  }

  /**
   * Declares a property on its owner, if necessary.
   *
   * @return True if a property was declared.
   */
  private boolean ensurePropertyDeclaredHelper(
      Node getprop, ObjectType objectType, FlowScope scope) {
    if (getprop.isQualifiedName()) {
      String propName = getprop.getLastChild().getString();
      String qName = getprop.getQualifiedName();
      TypedVar var = getDeclaredVar(scope, qName);
      if (var != null && !var.isTypeInferred()) {
        // Handle normal declarations that could not be addressed earlier.
        if (propName.equals("prototype")
            ||
            // Handle prototype declarations that could not be addressed earlier.
            (!objectType.hasOwnProperty(propName)
                && (!objectType.isInstanceType()
                    || (var.isExtern() && !objectType.isNativeObjectType())))) {
          return objectType.defineDeclaredProperty(
              propName, var.getType(), getprop);
        }
      }
    }
    return false;
  }

  private FlowScope traverseDeclaration(Node n, FlowScope scope) {
    for (Node declarationChild : n.children()) {
      scope = traverseDeclarationChild(declarationChild, scope);
    }

    return scope;
  }

  private FlowScope traverseDeclarationChild(Node n, FlowScope scope) {
    if (n.isName()) {
      return traverseName(n, scope);
    }

    checkState(n.isDestructuringLhs(), n);
    scope = traverse(n.getSecondChild(), scope);
    return traverseDestructuringPattern(
        n.getFirstChild(), scope, getJSType(n.getSecondChild()), AssignmentType.DECLARATION);
  }

  /** Traverses a destructuring pattern in an assignment or declaration */
  private FlowScope traverseDestructuringPattern(
      Node pattern, FlowScope scope, JSType patternType, AssignmentType assignmentType) {
    return traverseDestructuringPatternHelper(
        pattern,
        scope,
        patternType,
        (FlowScope flowScope, Node targetNode, JSType targetType) -> {
          targetType = targetType != null ? targetType : getNativeType(UNKNOWN_TYPE);
          return updateScopeForAssignment(flowScope, targetNode, targetType, assignmentType);
        });
  }

  /**
   * Traverses a destructuring pattern, and calls {@code declarer.declareTypeInScope} on each lvalue
   *
   * <p>The purpose of the callback is to abstract different logic for declaring lvalues in function
   * parameters vs. in regular assignments/declarations.
   *
   * @param declarer contains a callback called on every lvalue.
   */
  private FlowScope traverseDestructuringPatternHelper(
      Node pattern, FlowScope scope, JSType patternType, TypeDeclaringCallback declarer) {
    checkArgument(pattern.isDestructuringPattern(), pattern);
    checkNotNull(patternType);
    for (DestructuredTarget target :
        DestructuredTarget.createAllNonEmptyTargetsInPattern(registry, patternType, pattern)) {

      // The computed property is always evaluated first.
      if (target.hasComputedProperty()) {
        scope = traverse(target.getComputedProperty().getFirstChild(), scope);
      }
      Node targetNode = target.getNode();

      if (targetNode.isDestructuringPattern()) {
        if (target.hasDefaultValue()) {
          traverse(target.getDefaultValue(), scope);
        }

        // traverse into nested patterns
        JSType targetType = target.inferType();
        targetType = targetType != null ? targetType : getNativeType(UNKNOWN_TYPE);
        scope = traverseDestructuringPatternHelper(targetNode, scope, targetType, declarer);
      } else {
        scope = traverse(targetNode, scope);

        if (target.hasDefaultValue()) {
          // TODO(lharker): what do we do with the inferred slots in the scope?
          // throw them away or join them with the previous scope?
          traverse(target.getDefaultValue(), scope);
        }

        // declare in the scope
        scope = declarer.declareTypeInScope(scope, targetNode, target.inferType());
      }
    }
    // put the `inferred type` of a pattern on it, to make it easier to do typechecking
    pattern.setJSType(patternType);
    return scope;
  }

  private FlowScope traverseName(Node n, FlowScope scope) {
    String varName = n.getString();
    Node value = n.getFirstChild();
    JSType type = n.getJSType();
    if (value != null) {
      // The only case where `value` isn't null is when we are in a name declaration/initialization
      //     var x = 3;
      scope = traverse(value, scope);
      return updateScopeForAssignment(scope, n, getJSType(value), AssignmentType.DECLARATION);
    } else if (n.getParent().isLet()) {
      // Whenever we see a LET, we're guaranteed it's not yet in the scope, and we don't need to
      // worry about it being from an outer scope.  In this case, it has no child, so the actual
      // type should be undefined, but we make a special allowance for type-annotated variables.
      // In that case, we use the annotated type instead.
      // TODO(sdh): I would have thought that #updateScopeForTypeChange would handle using the
      // declared type correctly, but for some reason it doesn't so we handle it here.
      JSType resultType = type != null ? type : getNativeType(VOID_TYPE);
      scope = updateScopeForAssignment(scope, n, resultType, AssignmentType.DECLARATION);
      type = resultType;
    } else {
      StaticTypedSlot var = scope.getSlot(varName);
      if (var != null) {
        // There are two situations where we don't want to use type information
        // from the scope, even if we have it.

        // 1) The var is escaped and assigned in an inner scope, e.g.,
        // function f() { var x = 3; function g() { x = null } (x); }
        boolean isInferred = var.isTypeInferred();
        boolean unflowable = isInferred && isUnflowable(getDeclaredVar(scope, varName));

        // 2) We're reading type information from another scope for an
        // inferred variable. That variable is assigned more than once,
        // and we can't know which type we're getting.
        //
        // var t = null; function f() { (t); } doStuff(); t = {};
        //
        // Notice that this heuristic isn't perfect. For example, you might
        // have:
        //
        // function f() { (t); } f(); var t = 3;
        //
        // In this case, we would infer the first reference to t as
        // type {number}, even though it's undefined.
        TypedVar maybeOuterVar =
            isInferred && containerScope.isLocal()
                ? containerScope.getParent().getVar(varName)
                : null;
        boolean nonLocalInferredSlot =
            var.equals(maybeOuterVar) && !maybeOuterVar.isMarkedAssignedExactlyOnce();

        if (!unflowable && !nonLocalInferredSlot) {
          type = var.getType();
          if (type == null) {
            type = unknownType;
          }
        }
      }
    }
    n.setJSType(type);
    return scope;
  }

  private FlowScope traverseClass(Node n, FlowScope scope) {
    // The name already has a type applied (from TypedScopeCreator) if it's non-empty, and the
    // members are traversed in the class scope (and in their own function scopes).  But the extends
    // clause and computed property keys are in the outer scope and must be traversed here.
    scope = traverse(n.getSecondChild(), scope);
    Node classMembers = NodeUtil.getClassMembers(n);
    for (Node member = classMembers.getFirstChild(); member != null; member = member.getNext()) {
      if (member.isComputedProp()) {
        scope = traverse(member.getFirstChild(), scope);
      }
    }
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
    checkNotNull(type);

    for (Node name = n.getFirstChild(); name != null; name = name.getNext()) {
      scope = traverseChildren(name, scope);
    }

    // Object literals can be reflected on other types.
    // See CodingConvention#getObjectLiteralCast and goog.reflect.object
    // Ignore these types of literals.
    ObjectType objectType = ObjectType.cast(type);
    if (objectType == null
        || n.getBooleanProp(Node.REFLECTED_OBJECT)
        || objectType.isEnumType()) {
      return scope;
    }

    String qObjName = NodeUtil.getBestLValueName(
        NodeUtil.getBestLValue(n));
    for (Node name = n.getFirstChild(); name != null;
         name = name.getNext()) {
      if (name.isComputedProp()) {
        // Don't define computed properties as inferred properties on the object
        continue;
      }
      String memberName = NodeUtil.getObjectLitKeyName(name);
      if (memberName != null) {
        JSType rawValueType =  name.getFirstChild().getJSType();
        JSType valueType =
            TypeCheck.getObjectLitKeyTypeFromValueType(name, rawValueType);
        if (valueType == null) {
          valueType = unknownType;
        }
        objectType.defineInferredProperty(memberName, valueType, name);

        // Do normal flow inference if this is a direct property assignment.
        if (qObjName != null && name.isStringKey()) {
          String qKeyName = qObjName + "." + memberName;
          TypedVar var = getDeclaredVar(scope, qKeyName);
          JSType oldType = var == null ? null : var.getType();
          if (var != null && var.isTypeInferred()) {
            var.setType(oldType == null ? valueType : oldType.getLeastSupertype(oldType));
          }

          scope =
              scope.inferQualifiedSlot(
                  name, qKeyName, oldType == null ? unknownType : oldType, valueType, false);
        }
      } else {
        n.setJSType(unknownType);
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

    JSType type = unknownType;
    if (leftType != null && rightType != null) {
      boolean leftIsUnknown = leftType.isUnknownType();
      boolean rightIsUnknown = rightType.isUnknownType();
      if (leftIsUnknown && rightIsUnknown) {
        type = unknownType;
      } else if ((!leftIsUnknown && leftType.isString())
          || (!rightIsUnknown && rightType.isString())) {
        type = getNativeType(STRING_TYPE);
      } else if (leftIsUnknown || rightIsUnknown) {
        type = unknownType;
      } else if (isAddedAsNumber(leftType) && isAddedAsNumber(rightType)) {
        type = getNativeType(NUMBER_TYPE);
      } else {
        type = registry.createUnionType(STRING_TYPE, NUMBER_TYPE);
      }
    }
    n.setJSType(type);

    if (n.isAssignAdd()) {
      // TODO(johnlenz): this should not update the type of the lhs as that is use as a
      // input and need to be preserved for type checking.
      // Instead call this overload `updateScopeForAssignment(scope, left, leftType, type, null);`
      scope = updateScopeForAssignment(scope, left, type, AssignmentType.ASSIGN);
    }

    return scope;
  }

  private boolean isAddedAsNumber(JSType type) {
    return type.isSubtypeOf(registry.createUnionType(VOID_TYPE, NULL_TYPE,
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
    traverse(trueNode, trueScope);

    // traverse the false node with the falseScope
    traverse(falseNode, falseScope);

    // meet true and false nodes' types and assign
    JSType trueType = trueNode.getJSType();
    JSType falseType = falseNode.getJSType();
    if (trueType != null && falseType != null) {
      n.setJSType(trueType.getLeastSupertype(falseType));
    } else {
      n.setJSType(null);
    }

    return scope;
  }

  /** @param n A non-constructor function invocation, i.e. CALL or TAGGED_TEMPLATELIT */
  private FlowScope traverseFunctionInvocation(Node n, FlowScope scope) {
    checkArgument(n.isCall() || n.isTaggedTemplateLit(), n);
    scope = traverseChildren(n, scope);

    Node left = n.getFirstChild();
    JSType functionType = getJSType(left).restrictByNotNullOrUndefined();
    if (left.isSuper()) {
      // TODO(sdh): This will probably return the super type; might want to return 'this' instead?
      return traverseInstantiation(n, functionType, scope);
    } else if (functionType.isFunctionType()) {
      FunctionType fnType = functionType.toMaybeFunctionType();
      n.setJSType(fnType.getReturnType());
      backwardsInferenceFromCallSite(n, fnType, scope);
    } else if (functionType.isEquivalentTo(getNativeType(CHECKED_UNKNOWN_TYPE))) {
      n.setJSType(getNativeType(CHECKED_UNKNOWN_TYPE));
    } else if (left.getJSType() != null && left.getJSType().isUnknownType()) {
      // TODO(lharker): do we also want to set this to unknown if the left's type is null? We would
      // lose some inference that TypeCheck does when given a null type.
      n.setJSType(getNativeType(UNKNOWN_TYPE));
    }
    return scope;
  }

  private FlowScope tightenTypesAfterAssertions(FlowScope scope, Node callNode) {
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
    JSType assertedType = assertionFunctionSpec.getAssertedOldType(
        callNode, registry);
    String assertedNodeName = assertedNode.getQualifiedName();

    JSType narrowed;
    // Handle assertions that enforce expressions evaluate to true.
    if (assertedType == null) {
      // Handle arbitrary expressions within the assert.
      scope = reverseInterpreter.getPreciserScopeKnowingConditionOutcome(
          assertedNode, scope, true);
      // Build the result of the assertExpression
      narrowed = getJSType(assertedNode).restrictByNotNullOrUndefined();
    } else {
      // Handle assertions that enforce expressions are of a certain type.
      JSType type = getJSType(assertedNode);
      if (assertedType.isUnknownType() || type.isUnknownType()) {
        narrowed = assertedType;
      } else {
        narrowed = type.getGreatestSubtype(assertedType);
      }
      if (assertedNodeName != null && type.differsFrom(narrowed)) {
        scope = narrowScope(scope, assertedNode, narrowed);
      }
    }

    callNode.setJSType(narrowed);
    return scope;
  }

  private FlowScope narrowScope(FlowScope scope, Node node, JSType narrowed) {
    if (node.isThis()) {
      // "this" references don't need to be modeled in the control flow graph.
      return scope;
    }

    if (node.isGetProp()) {
      return scope.inferQualifiedSlot(
          node, node.getQualifiedName(), getJSType(node), narrowed, false);
    }
    return redeclareSimpleVar(scope, node, narrowed);
  }

  /**
   * We only do forward type inference. We do not do full backwards type inference.
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
   * <p>However, there are a few special syntactic forms where we do some some half-assed backwards
   * type-inference, because programmers expect it in this day and age. To take an example from
   * Java,
   * <code>
   * List<String> x = Lists.newArrayList();
   * </code>
   * The Java compiler will be able to infer the generic type of the List returned by
   * newArrayList().
   *
   * <p>In much the same way, we do some special-case backwards inference for JS. Those cases are
   * enumerated here.
   */
  private void backwardsInferenceFromCallSite(Node n, FunctionType fnType, FlowScope scope) {
    boolean updatedFnType = inferTemplatedTypesForCall(n, fnType, scope);
    if (updatedFnType) {
      fnType = n.getFirstChild().getJSType().toMaybeFunctionType();
    }
    updateTypeOfArguments(n, fnType);
    updateBind(n);
  }

  /**
   * When "bind" is called on a function, we infer the type of the returned
   * "bound" function by looking at the number of parameters in the call site.
   * We also infer the "this" type of the target, if it's a function expression.
   */
  private void updateBind(Node n) {
    CodingConvention.Bind bind =
        compiler.getCodingConvention().describeFunctionBind(n, false, true);
    if (bind == null) {
      return;
    }

    Node target = bind.target;
    FunctionType callTargetFn = getJSType(target)
        .restrictByNotNullOrUndefined().toMaybeFunctionType();
    if (callTargetFn == null) {
      return;
    }

    if (bind.thisValue != null && target.isFunction()) {
      JSType thisType = getJSType(bind.thisValue);
      if (thisType.toObjectType() != null && !thisType.isUnknownType()
          && callTargetFn.getTypeOfThis().isUnknownType()) {
        callTargetFn = new FunctionBuilder(registry)
            .copyFromOtherFunction(callTargetFn)
            .withTypeOfThis(thisType.toObjectType())
            .build();
        target.setJSType(callTargetFn);
      }
    }

    n.setJSType(
        callTargetFn.getBindReturnType(
            // getBindReturnType expects the 'this' argument to be included.
            bind.getBoundParameterCount() + 1));
  }

  /**
   * For functions with function parameters, type inference will set the type of a function literal
   * argument from the function parameter type.
   */
  private void updateTypeOfArguments(Node n, FunctionType fnType) {
    checkState(NodeUtil.isInvocation(n), n);
    Iterator<Node> parameters = fnType.getParameters().iterator();
    if (n.isTaggedTemplateLit()) {
      // Skip the first parameter because it corresponds to a constructed array of the template lit
      // subs, not an actual AST node, so there's nothing to update.
      if (!parameters.hasNext()) {
        // TypeCheck will warn if there is no first parameter. Just bail out here.
        return;
      }
      parameters.next();
    }
    Iterator<Node> arguments = NodeUtil.getInvocationArgsAsIterable(n).iterator();

    Node iParameter;
    Node iArgument;

    // Note: if there are too many or too few arguments, TypeCheck will warn.
    while (parameters.hasNext() && arguments.hasNext()) {
      iArgument = arguments.next();
      JSType iArgumentType = getJSType(iArgument);

      iParameter = parameters.next();
      JSType iParameterType = getJSType(iParameter);

      inferPropertyTypesToMatchConstraint(iArgumentType, iParameterType);

      // If the parameter to the call is a function expression, propagate the
      // function signature from the call site to the function node.

      // Filter out non-function types (such as null and undefined) as
      // we only care about FUNCTION subtypes here.
      FunctionType restrictedParameter = null;
      if (iParameterType.isUnionType()) {
        UnionType union = iParameterType.toMaybeUnionType();
        for (JSType alternative : union.getAlternates()) {
          if (alternative.isFunctionType()) {
            // There is only one function type per union.
            restrictedParameter = alternative.toMaybeFunctionType();
            break;
          }
        }
      } else {
        restrictedParameter = iParameterType.toMaybeFunctionType();
      }

      if (restrictedParameter != null
          && iArgument.isFunction()
          && iArgumentType.isFunctionType()) {
        FunctionType argFnType = iArgumentType.toMaybeFunctionType();
        JSDocInfo argJsdoc = iArgument.getJSDocInfo();
        boolean declared = argJsdoc != null && argJsdoc.containsDeclaration();
        iArgument.setJSType(matchFunction(restrictedParameter, argFnType, declared));
      }
    }
  }

  /**
   * Take the current function type, and try to match the expected function
   * type. This is a form of backwards-inference, like record-type constraint
   * matching.
   */
  private FunctionType matchFunction(
      FunctionType expectedType, FunctionType currentType, boolean declared) {
    if (declared) {
      // If the function was declared but it doesn't have a known "this"
      // but the expected type does, back fill it.
      if (currentType.getTypeOfThis().isUnknownType()
          && !expectedType.getTypeOfThis().isUnknownType()) {
        FunctionType replacement = new FunctionBuilder(registry)
            .copyFromOtherFunction(currentType)
            .withTypeOfThis(expectedType.getTypeOfThis())
            .build();
         return replacement;
      }
    } else {
      // For now, we just make sure the current type has enough
      // arguments to match the expected type, and return the
      // expected type if it does.
      if (currentType.getMaxArity() <= expectedType.getMaxArity()) {
        return expectedType;
      }
    }
    return currentType;
  }

  /**
   * @param call A CALL, NEW, or TAGGED_TEMPLATELIT node
   * @param scope
   */
  private Map<TemplateType, JSType> inferTemplateTypesFromParameters(
      FunctionType fnType, Node call, FlowScope scope) {
    if (fnType.getTemplateTypeMap().getTemplateKeys().isEmpty()) {
      return Collections.emptyMap();
    }

    Map<TemplateType, JSType> resolvedTypes = Maps.newIdentityHashMap();
    Set<JSType> seenTypes = Sets.newIdentityHashSet();

    Node callTarget = call.getFirstChild();
    if (NodeUtil.isGet(callTarget)) {
      Node obj = callTarget.getFirstChild();
      JSType typeOfThisRequiredByTheFunction = fnType.getTypeOfThis();
      // The type placed on a SUPER node is the superclass type, which allows us to infer the right
      // property types for the GETPROP or GETELEM nodes built on it.
      // However, the type actually passed as `this` when making calls this way is the `this`
      // of the scope where the `super` appears.
      JSType typeOfThisProvidedByTheCall = obj.isSuper() ? scope.getTypeOfThis() : getJSType(obj);
      // We're looking at a call made as `obj['method']()` or `obj.method()` (see enclosing if),
      // so if the call is successfully made, then the object through which it is made isn't null
      // or undefined.
      typeOfThisProvidedByTheCall = typeOfThisProvidedByTheCall.restrictByNotNullOrUndefined();
      maybeResolveTemplatedType(
          typeOfThisRequiredByTheFunction, typeOfThisProvidedByTheCall, resolvedTypes, seenTypes);
    }

    if (call.isTaggedTemplateLit()) {
      Iterator<Node> fnParameters = fnType.getParameters().iterator();
      if (!fnParameters.hasNext()) {
        // TypeCheck will warn if there are too few function parameters
        return resolvedTypes;
      }
      // The first argument to the tag function is an array of strings (typed as ITemplateArray)
      // but not an actual AST node
      maybeResolveTemplatedType(
          fnParameters.next().getJSType(),
          getNativeType(I_TEMPLATE_ARRAY_TYPE),
          resolvedTypes,
          seenTypes);

      // Resolve the remaining template types from the template literal substitutions.
      maybeResolveTemplateTypeFromNodes(
          Iterables.skip(fnType.getParameters(), 1),
          NodeUtil.getInvocationArgsAsIterable(call),
          resolvedTypes,
          seenTypes);
    } else if (call.hasMoreThanOneChild()) {
      maybeResolveTemplateTypeFromNodes(
          fnType.getParameters(),
          NodeUtil.getInvocationArgsAsIterable(call),
          resolvedTypes,
          seenTypes);
    }
    return resolvedTypes;
  }

  private void maybeResolveTemplatedType(
      JSType paramType,
      JSType argType,
      Map<TemplateType, JSType> resolvedTypes, Set<JSType> seenTypes) {
    if (paramType.isTemplateType()) {
      // example: @param {T}
      resolvedTemplateType(
          resolvedTypes, paramType.toMaybeTemplateType(), argType);
    } else if (paramType.isUnionType()) {
      // example: @param {Array.<T>|NodeList|Arguments|{length:number}}
      UnionType unionType = paramType.toMaybeUnionType();
      for (JSType alernative : unionType.getAlternates()) {
        maybeResolveTemplatedType(alernative, argType, resolvedTypes, seenTypes);
      }
    } else if (paramType.isFunctionType()) {
      FunctionType paramFunctionType = paramType.toMaybeFunctionType();
      FunctionType argFunctionType = argType
          .restrictByNotNullOrUndefined()
          .collapseUnion()
          .toMaybeFunctionType();
      if (argFunctionType != null && argFunctionType.isSubtype(paramType)) {
        // infer from return type of the function type
        maybeResolveTemplatedType(
            paramFunctionType.getTypeOfThis(),
            argFunctionType.getTypeOfThis(), resolvedTypes, seenTypes);
        // infer from return type of the function type
        maybeResolveTemplatedType(
            paramFunctionType.getReturnType(),
            argFunctionType.getReturnType(), resolvedTypes, seenTypes);
        // infer from parameter types of the function type
        maybeResolveTemplateTypeFromNodes(
            paramFunctionType.getParameters(),
            argFunctionType.getParameters(), resolvedTypes, seenTypes);
      }
    } else if (paramType.isRecordType() && !paramType.isNominalType()) {
      // example: @param {{foo:T}}
      if (seenTypes.add(paramType)) {
        ObjectType paramRecordType = paramType.toObjectType();
        ObjectType argObjectType = argType.restrictByNotNullOrUndefined().toObjectType();
        if (argObjectType != null && !argObjectType.isUnknownType()
            && !argObjectType.isEmptyType()) {
          Set<String> names = paramRecordType.getPropertyNames();
          for (String name : names) {
            if (paramRecordType.hasOwnProperty(name) && argObjectType.hasProperty(name)) {
              maybeResolveTemplatedType(paramRecordType.getPropertyType(name),
                  argObjectType.getPropertyType(name), resolvedTypes, seenTypes);
            }
          }
        }
        seenTypes.remove(paramType);
      }
    } else if (paramType.isTemplatizedType()) {
      // example: @param {Array<T>}
      TemplatizedType templatizedParamType = paramType.toMaybeTemplatizedType();
      int keyCount = templatizedParamType.getTemplateTypes().size();
      // TODO(johnlenz): determine why we are creating TemplatizedTypes for
      // types with no type arguments.
      if (keyCount > 0) {
        ObjectType referencedParamType = templatizedParamType.getReferencedType();
        JSType argObjectType = argType
            .restrictByNotNullOrUndefined()
            .collapseUnion();

        if (argObjectType.isSubtypeOf(referencedParamType)) {
          // If the argument type is a subtype of the parameter type, resolve any
          // template types amongst their templatized types.
          TemplateTypeMap paramTypeMap = paramType.getTemplateTypeMap();

          ImmutableList<TemplateType> keys = paramTypeMap.getTemplateKeys();
          TemplateTypeMap argTypeMap = argObjectType.getTemplateTypeMap();
          for (int index = keys.size() - keyCount; index < keys.size(); index++) {
            TemplateType key = keys.get(index);
            maybeResolveTemplatedType(
                paramTypeMap.getResolvedTemplateType(key),
                argTypeMap.getResolvedTemplateType(key),
                resolvedTypes, seenTypes);
          }
        }
      }
    }
  }

  private void maybeResolveTemplateTypeFromNodes(
      Iterable<Node> declParams,
      Iterable<Node> callParams,
      Map<TemplateType, JSType> resolvedTypes, Set<JSType> seenTypes) {
    maybeResolveTemplateTypeFromNodes(
        declParams.iterator(), callParams.iterator(), resolvedTypes, seenTypes);
  }

  private void maybeResolveTemplateTypeFromNodes(
      Iterator<Node> declParams,
      Iterator<Node> callParams,
      Map<TemplateType, JSType> resolvedTypes,
      Set<JSType> seenTypes) {
    while (declParams.hasNext() && callParams.hasNext()) {
      Node declParam = declParams.next();
      maybeResolveTemplatedType(
          getJSType(declParam),
          getJSType(callParams.next()),
          resolvedTypes, seenTypes);
      if (declParam.isVarArgs()) {
        while (callParams.hasNext()) {
          maybeResolveTemplatedType(
              getJSType(declParam),
              getJSType(callParams.next()),
              resolvedTypes, seenTypes);
        }
      }
    }
  }

  private static void resolvedTemplateType(
      Map<TemplateType, JSType> map, TemplateType template, JSType resolved) {
    JSType previous = map.get(template);
    if (!resolved.isUnknownType()) {
      if (previous == null) {
        map.put(template, resolved);
      } else {
        JSType join = previous.getLeastSupertype(resolved);
        map.put(template, join);
      }
    }
  }

  private static class TemplateTypeReplacer extends ModificationVisitor {
    private final Map<TemplateType, JSType> replacements;
    private final JSTypeRegistry registry;
    boolean madeChanges = false;

    TemplateTypeReplacer(
        JSTypeRegistry registry, Map<TemplateType, JSType> replacements) {
      super(registry, true);
      this.registry = registry;
      this.replacements = replacements;
    }

    @Override
    public JSType caseTemplateType(TemplateType type) {
      madeChanges = true;
      JSType replacement = replacements.get(type);
      return replacement != null ? replacement : registry.getNativeType(UNKNOWN_TYPE);
    }
  }

  /**
   * Build the type environment where type transformations will be evaluated.
   * It only considers the template type variables that do not have a type
   * transformation.
   */
  private Map<String, JSType> buildTypeVariables(
      Map<TemplateType, JSType> inferredTypes) {
    Map<String, JSType> typeVars = new LinkedHashMap<>();
    for (Entry<TemplateType, JSType> e : inferredTypes.entrySet()) {
      // Only add the template type that do not have a type transformation
      if (!e.getKey().isTypeTransformation()) {
        typeVars.put(e.getKey().getReferenceName(), e.getValue());
      }
    }
    return typeVars;
  }

  /** This function will evaluate the type transformations associated to the template types */
  private Map<TemplateType, JSType> evaluateTypeTransformations(
      ImmutableList<TemplateType> templateTypes,
      Map<TemplateType, JSType> inferredTypes,
      FlowScope scope) {

    Map<String, JSType> typeVars = null;
    Map<TemplateType, JSType> result = null;
    TypeTransformation ttlObj = null;

    for (TemplateType type : templateTypes) {
      if (type.isTypeTransformation()) {
        // Lazy initialization when the first type transformation is found
        if (ttlObj == null) {
          ttlObj = new TypeTransformation(compiler, scope.getDeclarationScope());
          typeVars = buildTypeVariables(inferredTypes);
          result = new LinkedHashMap<>();
        }
        // Evaluate the type transformation expression using the current
        // known types for the template type variables
        JSType transformedType = ttlObj.eval(
            type.getTypeTransformation(),
            ImmutableMap.copyOf(typeVars));
        result.put(type, transformedType);
        // Add the transformed type to the type variables
        typeVars.put(type.getReferenceName(), transformedType);
      }
    }
    return result;
  }

  /**
   * For functions that use template types, specialize the function type for the call target based
   * on the call-site specific arguments. Specifically, this enables inference to set the type of
   * any function literal parameters based on these inferred types.
   */
  private boolean inferTemplatedTypesForCall(Node n, FunctionType fnType, FlowScope scope) {
    ImmutableList<TemplateType> keys = fnType.getTemplateTypeMap().getTemplateKeys();
    if (keys.isEmpty()) {
      return false;
    }

    // Try to infer the template types
    Map<TemplateType, JSType> rawInferrence = inferTemplateTypesFromParameters(fnType, n, scope);
    Map<TemplateType, JSType> inferred = Maps.newIdentityHashMap();
    for (TemplateType key : keys) {
      JSType type = rawInferrence.get(key);
      if (type == null) {
        type = unknownType;
      }
      inferred.put(key, type);
    }

    // Try to infer the template types using the type transformations
    Map<TemplateType, JSType> typeTransformations =
        evaluateTypeTransformations(keys, inferred, scope);
    if (typeTransformations != null) {
      inferred.putAll(typeTransformations);
    }

    // Replace all template types. If we couldn't find a replacement, we
    // replace it with UNKNOWN.
    TemplateTypeReplacer replacer = new TemplateTypeReplacer(registry, inferred);
    Node callTarget = n.getFirstChild();

    FunctionType replacementFnType = fnType.visit(replacer).toMaybeFunctionType();
    checkNotNull(replacementFnType);
    callTarget.setJSType(replacementFnType);
    n.setJSType(replacementFnType.getReturnType());

    return replacer.madeChanges;
  }

  private FlowScope traverseNew(Node n, FlowScope scope) {
    scope = traverseChildren(n, scope);
    Node constructor = n.getFirstChild();
    JSType constructorType = constructor.getJSType();
    return traverseInstantiation(n, constructorType, scope);
  }

  private FlowScope traverseInstantiation(Node n, JSType constructorType, FlowScope scope) {
    JSType type = null;
    if (constructorType != null) {
      constructorType = constructorType.restrictByNotNullOrUndefined();
      if (constructorType.isUnknownType()) {
        type = unknownType;
      } else {
        FunctionType ct = constructorType.toMaybeFunctionType();
        if (ct == null && constructorType instanceof FunctionType) {
          // If constructorType is a NoObjectType, then toMaybeFunctionType will
          // return null. But NoObjectType implements the FunctionType
          // interface, precisely because it can validly construct objects.
          ct = (FunctionType) constructorType;
        }
        if (ct != null && ct.isConstructor()) {
          backwardsInferenceFromCallSite(n, ct, scope);

          // If necessary, create a TemplatizedType wrapper around the instance
          // type, based on the types of the constructor parameters.
          ObjectType instanceType = ct.getInstanceType();
          Map<TemplateType, JSType> inferredTypes = inferTemplateTypesFromParameters(ct, n, scope);
          if (inferredTypes.isEmpty()) {
            type = instanceType;
          } else {
            type = registry.createTemplatizedType(instanceType, inferredTypes);
          }
        }
      }
    }
    n.setJSType(type);
    return scope;
  }

  private BooleanOutcomePair traverseAnd(Node n, FlowScope scope) {
    return traverseShortCircuitingBinOp(n, scope);
  }

  private FlowScope traverseChildren(Node n, FlowScope scope) {
    for (Node el = n.getFirstChild(); el != null; el = el.getNext()) {
      scope = traverse(el, scope);
    }
    return scope;
  }

  private FlowScope traverseGetElem(Node n, FlowScope scope) {
    scope = traverseChildren(n, scope);
    Node indexKey = n.getLastChild();
    JSType indexType = getJSType(indexKey);
    if (indexType.isSymbolValueType()) {
      // For now, allow symbols definitions/access on any type. In the future only allow them
      // on the subtypes for which they are defined.

      // TODO(b/77474174): Type well known symbol accesses.
      n.setJSType(unknownType);
    } else {
      JSType type = getJSType(n.getFirstChild()).restrictByNotNullOrUndefined();
      TemplateTypeMap typeMap = type.getTemplateTypeMap();
      if (typeMap.hasTemplateType(registry.getObjectElementKey())) {
        n.setJSType(typeMap.getResolvedTemplateType(registry.getObjectElementKey()));
      }
    }
    return tightenTypeAfterDereference(n.getFirstChild(), scope);
  }

  private FlowScope traverseGetProp(Node n, FlowScope scope) {
    Node objNode = n.getFirstChild();
    Node property = n.getLastChild();
    scope = traverseChildren(n, scope);

    n.setJSType(
        getPropertyType(
            objNode.getJSType(), property.getString(), n, scope));
    return tightenTypeAfterDereference(n.getFirstChild(), scope);
  }

  /**
   * Suppose X is an object with inferred properties.
   * Suppose also that X is used in a way where it would only type-check
   * correctly if some of those properties are widened.
   * Then we should be polite and automatically widen X's properties.
   *
   * For a concrete example, consider:
   * param x {{prop: (number|undefined)}}
   * function f(x) {}
   * f({});
   *
   * If we give the anonymous object an inferred property of (number|undefined),
   * then this code will type-check appropriately.
   */
  private static void inferPropertyTypesToMatchConstraint(
      JSType type, JSType constraint) {
    if (type == null || constraint == null) {
      return;
    }

    type.matchConstraint(constraint);
  }

  /**
   * If we access a property of a symbol, then that symbol is not
   * null or undefined.
   */
  private FlowScope tightenTypeAfterDereference(Node n, FlowScope scope) {
    if (n.isQualifiedName()) {
      JSType type = getJSType(n);
      JSType narrowed = type.restrictByNotNullOrUndefined();
      if (!type.equals(narrowed)) {
        scope = narrowScope(scope, n, narrowed);
      }
    }
    return scope;
  }

  private JSType getPropertyType(JSType objType, String propName,
      Node n, FlowScope scope) {
    // We often have a couple of different types to choose from for the
    // property. Ordered by accuracy, we have
    // 1) A locally inferred qualified name (which is in the FlowScope)
    // 2) A globally declared qualified name (which is in the FlowScope)
    // 3) A property on the owner type (which is on objType)
    // 4) A name in the type registry (as a last resort)
    JSType propertyType = null;
    boolean isLocallyInferred = false;

    // Scopes sometimes contain inferred type info about qualified names.
    String qualifiedName = n.getQualifiedName();
    StaticTypedSlot var = qualifiedName != null ? scope.getSlot(qualifiedName) : null;
    if (var != null) {
      JSType varType = var.getType();
      if (varType != null) {
        boolean isDeclared = !var.isTypeInferred();
        isLocallyInferred = (var != getDeclaredVar(scope, qualifiedName));
        if (isDeclared || isLocallyInferred) {
          propertyType = varType;
        }
      }
    }

    if (propertyType == null && objType != null) {
      JSType foundType = objType.findPropertyType(propName);
      if (foundType != null) {
        propertyType = foundType;
      }
    }

    if (propertyType != null && objType != null) {
      JSType restrictedObjType = objType.restrictByNotNullOrUndefined();
      if (!restrictedObjType.getTemplateTypeMap().isEmpty()
          && propertyType.hasAnyTemplateTypes()) {
        TemplateTypeMap typeMap = restrictedObjType.getTemplateTypeMap();
        TemplateTypeMapReplacer replacer = new TemplateTypeMapReplacer(
            registry, typeMap);
        propertyType = propertyType.visit(replacer);
      }
    }

    if ((propertyType == null || propertyType.isUnknownType())
        && qualifiedName != null) {
      // If we find this node in the registry, then we can infer its type.
      ObjectType regType = ObjectType.cast(
          registry.getType(scope.getDeclarationScope(), qualifiedName));
      if (regType != null) {
        propertyType = regType.getConstructor();
      }
    }

    if (propertyType == null) {
      return unknownType;
    } else if (propertyType.isEquivalentTo(unknownType) && isLocallyInferred) {
      // If the type has been checked in this scope,
      // then use CHECKED_UNKNOWN_TYPE instead to indicate that.
      return getNativeType(CHECKED_UNKNOWN_TYPE);
    } else {
      return propertyType;
    }
  }

  private BooleanOutcomePair traverseOr(Node n, FlowScope scope) {
    return traverseShortCircuitingBinOp(n, scope);
  }

  private BooleanOutcomePair traverseShortCircuitingBinOp(
      Node n, FlowScope scope) {
    checkArgument(n.isAnd() || n.isOr());
    boolean nIsAnd = n.isAnd();
    Node left = n.getFirstChild();
    Node right = n.getLastChild();

    // type the left node
    BooleanOutcomePair leftOutcome = traverseWithinShortCircuitingBinOp(left, scope);
    JSType leftType = left.getJSType();

    // reverse abstract interpret the left node to produce the correct
    // scope in which to verify the right node
    FlowScope rightScope =
        reverseInterpreter.getPreciserScopeKnowingConditionOutcome(
            left, leftOutcome.getOutcomeFlowScope(left.getToken(), nIsAnd), nIsAnd);

    // type the right node
    BooleanOutcomePair rightOutcome = traverseWithinShortCircuitingBinOp(right, rightScope);
    JSType rightType = right.getJSType();

    JSType type;
    BooleanOutcomePair outcome;
    if (leftType != null && rightType != null) {
      leftType = leftType.getRestrictedTypeGivenToBooleanOutcome(!nIsAnd);
      if (leftOutcome.toBooleanOutcomes == BooleanLiteralSet.get(!nIsAnd)) {
        // Either n is && and lhs is false, or n is || and lhs is true.
        // Use the restricted left type; the right side never gets evaluated.
        type = leftType;
        outcome = leftOutcome;
      } else {
        // Use the join of the restricted left type knowing the outcome of the
        // ToBoolean predicate and of the right type.
        type = leftType.getLeastSupertype(rightType);
        outcome = new BooleanOutcomePair(
            joinBooleanOutcomes(nIsAnd,
                leftOutcome.toBooleanOutcomes, rightOutcome.toBooleanOutcomes),
            joinBooleanOutcomes(nIsAnd,
                leftOutcome.booleanValues, rightOutcome.booleanValues),
            leftOutcome.getJoinedFlowScope(),
            rightOutcome.getJoinedFlowScope());
      }
      // Exclude the boolean type if the literal set is empty because a boolean
      // can never actually be returned.
      if (outcome.booleanValues == BooleanLiteralSet.EMPTY
          && getNativeType(BOOLEAN_TYPE).isSubtypeOf(type)) {
        // Exclusion only makes sense for a union type.
        if (type.isUnionType()) {
          type = type.toMaybeUnionType().getRestrictedUnion(
              getNativeType(BOOLEAN_TYPE));
        }
      }
    } else {
      type = null;
      outcome = new BooleanOutcomePair(
          BooleanLiteralSet.BOTH, BooleanLiteralSet.BOTH,
          leftOutcome.getJoinedFlowScope(),
          rightOutcome.getJoinedFlowScope());
    }
    n.setJSType(type);
    return outcome;
  }

  private BooleanOutcomePair traverseWithinShortCircuitingBinOp(
      Node n, FlowScope scope) {
    switch (n.getToken()) {
      case AND:
        return traverseAnd(n, scope);

      case OR:
        return traverseOr(n, scope);

      default:
        scope = traverse(n, scope);
        return newBooleanOutcomePair(n.getJSType(), scope);
    }
  }

  private FlowScope traverseAwait(Node await, FlowScope scope) {
    scope = traverseChildren(await, scope);

    Node expr = await.getFirstChild();
    JSType exprType = getJSType(expr);
    await.setJSType(Promises.getResolvedType(registry, exprType));

    return scope;
  }

  private static BooleanLiteralSet joinBooleanOutcomes(
      boolean isAnd, BooleanLiteralSet left, BooleanLiteralSet right) {
    // A truthy value on the lhs of an {@code &&} can never make it to the
    // result. Same for a falsy value on the lhs of an {@code ||}.
    // Hence the intersection.
    return right.union(left.intersection(BooleanLiteralSet.get(!isAnd)));
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
    FlowScope getOutcomeFlowScope(Token nodeType, boolean outcome) {
      if ((nodeType == Token.AND && outcome) || (nodeType == Token.OR && !outcome)) {
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
    return new BooleanOutcomePair(
        jsType.getPossibleToBooleanOutcomes(),
        registry.getNativeType(BOOLEAN_TYPE).isSubtypeOf(jsType)
            ? BooleanLiteralSet.BOTH
            : BooleanLiteralSet.EMPTY,
        flowScope,
        flowScope);
  }

  @CheckReturnValue
  private FlowScope redeclareSimpleVar(FlowScope scope, Node nameNode, JSType varType) {
    checkState(nameNode.isName(), nameNode);
    String varName = nameNode.getString();
    if (varType == null) {
      varType = getNativeType(JSTypeNative.UNKNOWN_TYPE);
    }
    if (isUnflowable(getDeclaredVar(scope, varName))) {
      return scope;
    }
    return scope.inferSlotType(varName, varType);
  }

  private boolean isUnflowable(TypedVar v) {
    return v != null
        && v.isLocal()
        && v.isMarkedEscaped()
        // It's OK to flow a variable in the scope where it's escaped.
        && v.getScope().getClosestContainerScope() == containerScope;
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
      return unknownType;
    } else {
      return jsType;
    }
  }

  private JSType getNativeType(JSTypeNative typeId) {
    return registry.getNativeType(typeId);
  }

  private static TypedVar getDeclaredVar(FlowScope scope, String name) {
    return ((TypedScope) scope.getDeclarationScope()).getVar(name);
  }
}
