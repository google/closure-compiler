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
import static com.google.javascript.rhino.jstype.JSTypeNative.BIGINT_NUMBER;
import static com.google.javascript.rhino.jstype.JSTypeNative.BIGINT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.CHECKED_UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.CodingConvention.AssertionFunctionLookup;
import com.google.javascript.jscomp.CodingConvention.AssertionFunctionSpec;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.modules.Export;
import com.google.javascript.jscomp.modules.Module;
import com.google.javascript.jscomp.type.FlowScope;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Outcome;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.BooleanLiteralSet;
import com.google.javascript.rhino.jstype.EnumElementType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.FunctionType.Parameter;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticTypedSlot;
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.jstype.TemplateTypeMap;
import com.google.javascript.rhino.jstype.TemplateTypeReplacer;
import com.google.javascript.rhino.jstype.UnionType;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
 * Type inference within a script node or a function body, using the data-flow analysis framework.
 */
class TypeInference extends DataFlowAnalysis.BranchedForwardDataFlowAnalysis<Node, FlowScope> {

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
  private final AssertionFunctionLookup assertionFunctionLookup;
  private final ModuleImportResolver moduleImportResolver;
  // A record is pushed onto this stack during the traversal of each optional chain.
  // Inference of the type at the end of the optional chain requires scope information
  // from traversing the start of the chain. This stack serves as a communication channel
  // for that information.
  private final ArrayDeque<OptChainInfo> optChainArrayDeque = new ArrayDeque<>();

  // Scopes that have had their unbound untyped vars inferred as undefined.
  private final Set<TypedScope> inferredUnboundVars = new HashSet<>();

  // For convenience
  private final ObjectType unknownType;

  TypeInference(
      AbstractCompiler compiler,
      ControlFlowGraph<Node> cfg,
      ReverseAbstractInterpreter reverseInterpreter,
      TypedScope syntacticScope,
      TypedScopeCreator scopeCreator,
      AssertionFunctionLookup assertionFunctionLookup) {
    super(cfg, new LinkedFlowScope.FlowScopeJoinOp(compiler));
    this.compiler = compiler;
    this.registry = compiler.getTypeRegistry();
    this.reverseInterpreter = reverseInterpreter;
    this.unknownType = registry.getNativeObjectType(UNKNOWN_TYPE);
    this.moduleImportResolver =
        new ModuleImportResolver(
            compiler.getModuleMap(), scopeCreator.getNodeToScopeMapper(), this.registry);

    this.containerScope = syntacticScope;

    this.scopeCreator = scopeCreator;
    this.assertionFunctionLookup = assertionFunctionLookup;

    FlowScope entryScope =
        inferDeclarativelyUnboundVarsWithoutTypes(
            LinkedFlowScope.createEntryLattice(compiler, syntacticScope));

    this.functionScope = inferParameters(entryScope);

    this.bottomScope =
        LinkedFlowScope.createEntryLattice(
            compiler, TypedScope.createLatticeBottom(syntacticScope.getRootNode()));
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
    if (!functionNode.isFunction()) {
      return entryFlowScope; // we're in the global scope
    } else if (NodeUtil.isBundledGoogModuleCall(functionNode.getParent())) {
      // Pretend the function literal in `goog.loadModule(function(exports) {` does not exist.
      return entryFlowScope;
    }
    Node astParameters = functionNode.getSecondChild();
    Node iifeArgumentNode = null;

    if (NodeUtil.isInvocationTarget(functionNode)) {
      iifeArgumentNode = functionNode.getNext();
    }

    FunctionType functionType = JSType.toMaybeFunctionType(functionNode.getJSType());
    Iterator<Parameter> parameterTypes = functionType.getParameters().iterator();
    Parameter parameter = parameterTypes.hasNext() ? parameterTypes.next() : null;

    // This really iterates over three different things at once:
    //   - the actual AST parameter nodes (which may be REST, DEFAULT_VALUE, etc.)
    //   - the argument nodes in an IIFE
    //   - the parameter type nodes from the FunctionType on the FUNCTION node
    // Always visit every AST parameter once, regardless of how many IIFE arguments or
    // FunctionType param nodes there are.
    for (Node astParameter : astParameters.children()) {
      if (iifeArgumentNode != null && iifeArgumentNode.isSpread()) {
        // block inference on all parameters that might possibly be set by a spread, e.g. `z` in
        // (function f(x, y, z = 1))(...[1, 2], 'foo')
        iifeArgumentNode = null;
      }

      // Running variable for the type of the param within the body of the function. We use the
      // existing type on the param node as the default, and then transform it according to the
      // declaration syntax.
      JSType inferredType = getJSType(astParameter);

      if (iifeArgumentNode != null) {
        if (iifeArgumentNode.getJSType() != null) {
          inferredType = iifeArgumentNode.getJSType();
        }
      } else if (parameter != null) {
        if (parameter.getJSType() != null) {
          inferredType = parameter.getJSType();
        }
      }

      Node defaultValue = null;
      if (astParameter.isDefaultValue()) {
        defaultValue = astParameter.getSecondChild();
        // must call `traverse` to correctly type the default value
        entryFlowScope = traverse(defaultValue, entryFlowScope);
        astParameter = astParameter.getFirstChild();
      } else if (astParameter.isRest()) {
        // e.g. `function f(p1, ...restParamName) {}`
        // set astParameter = restParamName
        astParameter = astParameter.getOnlyChild();
        // convert 'number' into 'Array<number>' for rest parameters
        inferredType =
            registry.createTemplatizedType(registry.getNativeObjectType(ARRAY_TYPE), inferredType);
      }

      if (defaultValue != null) {
        // The param could possibly be the default type, and `undefined` args won't propagate in.
        inferredType =
            registry.createUnionType(
                inferredType.restrictByNotUndefined(), getJSType(defaultValue));
      }

      if (astParameter.isDestructuringPattern()) {
        // even if the inferredType is null, we still need to type all the nodes inside the
        // destructuring pattern. (e.g. in computed properties or default value expressions)
        entryFlowScope = updateDestructuringParameter(astParameter, inferredType, entryFlowScope);
      } else {
        // for simple named parameters, we only need to update the scope/AST if we have a new
        // inferred type.
        entryFlowScope =
            updateNamedParameter(astParameter, defaultValue != null, inferredType, entryFlowScope);
      }

      parameter = parameterTypes.hasNext() ? parameterTypes.next() : null;
      iifeArgumentNode = iifeArgumentNode != null ? iifeArgumentNode.getNext() : null;
    }

    return entryFlowScope;
  }

  /**
   * Sets the types of a un-named/destructuring function parameter to an inferred type.
   *
   * <p>This method is responsible for typing:
   *
   * <ul>
   *   <li>The scope slot
   *   <li>The pattern nodes
   * </ul>
   */
  @CheckReturnValue
  @SuppressWarnings("ReferenceEquality") // unknownType is a singleton
  private FlowScope updateDestructuringParameter(
      Node pattern, JSType inferredType, FlowScope entryFlowScope) {
    // look through all expressions and lvalues in the pattern.
    // given an lvalue, change its type if either a) it's inferred (not declared in
    // TypedScopeCreator) or b) it has a default value
    entryFlowScope =
        traverseDestructuringPatternHelper(
            pattern,
            entryFlowScope,
            inferredType,
            (FlowScope scope, Node lvalue, JSType type) -> {
              TypedVar var = containerScope.getVar(lvalue.getString());
              checkNotNull(var);
              // This condition will trigger on cases like
              //   (function f({x}) {})({x: 3})
              // where `x` is of unknown type during the typed scope creation phase, but
              // here we can infer that it is of type `number`
              if (var.isTypeInferred()) {
                var.setType(type);
                lvalue.setJSType(type);
              }
              if (lvalue.getParent().isDefaultValue()) {
                // Given
                //   /** @param {{age: (number|undefined)}} data */
                //   function f({age = 99}) {}
                // infer that `age` is now a `number` and not `number|undefined`
                // treat this similarly to if there was an assignment inside the function body
                // TODO(b/117162687): allow people to narrow the declared type to
                // exclude 'undefined' inside the function body.
                scope = updateScopeForAssignment(scope, lvalue, type, AssignmentType.ASSIGN);
              }
              return scope;
            });

    return entryFlowScope;
  }

  /**
   * Sets the types of a named/non-destructuring function parameter to an inferred type.
   *
   * <p>This method is responsible for typing:
   *
   * <ul>
   *   <li>The scope slot
   *   <li>The param node
   * </ul>
   */
  @CheckReturnValue
  @SuppressWarnings("ReferenceEquality") // unknownType is a singleton
  private FlowScope updateNamedParameter(
      Node paramName, boolean hasDefaultValue, JSType inferredType, FlowScope entryFlowScope) {
    TypedVar var = containerScope.getVar(paramName.getString());
    checkNotNull(var, "Missing var for parameter %s", paramName);

    paramName.setJSType(inferredType);

    if (var.isTypeInferred()) {
      var.setType(inferredType);
    } else if (hasDefaultValue) {
      // If this is a declared type with a default value, update the LinkedFlowScope slots but not
      // the actual TypedVar. This is similar to what would happen if the default value was moved
      // into an assignment in the fn body
      entryFlowScope = redeclareSimpleVar(entryFlowScope, paramName, inferredType);
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

    // This method also does some logic for ES modules right before and after entering/exiting the
    // scope rooted at the module. The reasoning for separating out this logic is that we can just
    // ignore the actual AST nodes for IMPORT/EXPORT, in most cases, because we have already
    // created an abstraction of imports and exports.
    Node root = NodeUtil.getEnclosingScopeRoot(n);
    // Inferred types of ES module imports/exports aren't knowable until after TypeInference runs.
    // First update the type of all imports in the scope, then do flow-sensitive inference, then
    // update the implicit '*exports*' object.
    Module module =
        ModuleImportResolver.getModuleFromScopeRoot(compiler.getModuleMap(), compiler, root);
    TypedScope syntacticBlockScope = scopeCreator.createScope(root);
    if (module != null && module.metadata().isEs6Module()) {
      moduleImportResolver.declareEsModuleImports(
          module, syntacticBlockScope, compiler.getInput(NodeUtil.getInputId(n)));
    }

    // This logic is not specific to ES modules.
    FlowScope output = input.withSyntacticScope(syntacticBlockScope);
    output = inferDeclarativelyUnboundVarsWithoutTypes(output);
    output = traverse(n, output);

    updateModuleScope(module, syntacticBlockScope);
    return output;
  }

  /** Updates the given scope after running inference over a goog.module or ES module */
  private void updateModuleScope(Module module, TypedScope syntacticBlockScope) {
    if (module == null) {
      return;
    }
    switch (module.metadata().moduleType()) {
      case ES6_MODULE:
        // This call only affects exports with an inferred, not declared, type. Declared exports
        // were already added to the namespace object type in TypedScopeCreator.
        moduleImportResolver.updateEsModuleNamespaceType(
            syntacticBlockScope.getVar(Export.NAMESPACE).getType().toObjectType(),
            module,
            syntacticBlockScope);
        return;
      case LEGACY_GOOG_MODULE:
        // Update the global scope for the implicit assignment "legacy.module.id = exports;" created
        // by `goog.module.declareLegacyNamespace();`
        TypedVar exportsVar =
            checkNotNull(
                syntacticBlockScope.getVar("exports"),
                "Missing exports var for %s",
                module.metadata());
        if (exportsVar.getType() == null) {
          return;
        }
        JSType exportsType = exportsVar.getType();
        // Store the type of the namespace on the AST for the convenience of later passes that want
        // to access it.
        Node rootNode = syntacticBlockScope.getRootNode();
        if (rootNode.isModuleBody()) {
          rootNode.setJSType(exportsType);
        } else {
          // For goog.loadModule, give the `exports` parameter the correct type.
          checkState(rootNode.isBlock(), rootNode);
          Node paramList = NodeUtil.getFunctionParameters(rootNode.getParent());
          paramList.getOnlyChild().setJSType(exportsType);
        }

        String moduleId = module.closureNamespace();
        TypedScope globalScope = syntacticBlockScope.getGlobalScope();
        TypedVar globalVar = globalScope.getVar(moduleId);
        if (globalVar.isTypeInferred()) {
          globalVar.setType(exportsType);
        }
        // Update the property slot on the parent namespace.
        QualifiedName moduleQname = QualifiedName.of(moduleId);
        if (!moduleQname.isSimple()) {
          JSType parentType = globalScope.lookupQualifiedName(moduleQname.getOwner());
          ObjectType parentObjectType = parentType != null ? parentType.toMaybeObjectType() : null;
          if (parentObjectType != null
              && !parentObjectType.isPropertyTypeDeclared(moduleQname.getComponent())) {
            parentObjectType.defineInferredProperty(
                moduleQname.getComponent(), exportsType, exportsVar.getNode());
          }
        }
        return;
      default:
        break;
    }
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

    List<? extends DiGraphEdge<Node, Branch>> branchEdges = getCfg().getOutEdges(source);
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
              TemplateType templateType = registry.getIterableTemplate();
              JSType newType = objType.getTemplateTypeMap().getResolvedTemplateType(templateType);

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
                      Outcome.forBoolean(branch.equals(Branch.ON_TRUE)));
            } else {
              // conditionFlowScope is cached from previous iterations
              // of the loop.
              if (conditionFlowScope == null) {
                conditionFlowScope = traverse(condition, output);
              }
              newScope =
                  reverseInterpreter.getPreciserScopeKnowingConditionOutcome(
                      condition,
                      conditionFlowScope,
                      Outcome.forBoolean(branch.equals(Branch.ON_TRUE)));
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
    boolean isTypeable = true;
    switch (n.getToken()) {
      case ASSIGN:
        scope = traverseAssign(n, scope);
        break;

      case NAME:
        scope = traverseName(n, scope);
        break;

      case OPTCHAIN_GETPROP:
      case OPTCHAIN_CALL:
      case OPTCHAIN_GETELEM:
        scope = traverseOptChain(n, scope);
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

      case COALESCE:
        scope = traverseNullishCoalesce(n, scope);
        break;

      case HOOK:
        scope = traverseHook(n, scope);
        break;

      case OBJECTLIT:
        scope = traverseObjectLiteral(n, scope);
        break;

      case CALL:
        scope = traverseCall(n, scope);
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
        scope = traverseUnaryPlus(n, scope);
        break;

      case NEG:
      case BITNOT:
      case DEC:
      case INC:
        scope = traverseUnaryNumericOperation(n, scope);
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
      case THROW:
      case ITER_SPREAD:
      case OBJECT_SPREAD:
        // these nodes are untyped but have children that may affect the flow scope and need to
        // be typed.
        scope = traverseChildren(n, scope);
        isTypeable = false;
        break;

      case TAGGED_TEMPLATELIT:
        scope = traverseTaggedTemplateLit(n, scope);
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
          ObjectType ownerType =
              ObjectType.cast(getJSType(getprop.getFirstChild()).restrictByNotNullOrUndefined());
          if (ownerType != null) {
            ensurePropertyDeclaredHelper(getprop, ownerType, scope);
          }
        }
        isTypeable = false;
        break;

      case SWITCH:
        scope = traverse(n.getFirstChild(), scope);
        isTypeable = false;
        break;

      case RETURN:
        scope = traverseReturn(n, scope);
        isTypeable = false;
        break;

      case YIELD:
        scope = traverseChildren(n, scope);
        n.setJSType(getNativeType(UNKNOWN_TYPE));
        break;

      case VAR:
      case LET:
      case CONST:
        scope = traverseDeclaration(n, scope);
        isTypeable = false;
        break;

      case CATCH:
        scope = traverseCatch(n, scope);
        isTypeable = false;
        break;

      case CAST:
        scope = traverseChildren(n, scope);
        JSDocInfo info = n.getJSDocInfo();
        // TODO(b/123955687): also check that info.hasType() is true
        checkNotNull(info, "CAST node should always have JSDocInfo");
        if (info.hasType()) {
          // NOTE(lharker) - I tried moving CAST type evaluation into the typed scope creation
          // phase.
          // Since it caused a few new, seemingly spurious, 'Bad type annotation' and
          // 'unknown property type' warnings, and having it in TypeInference seems to work, we just
          // do the lookup + resolution here.
          n.setJSType(
              info.getType()
                  .evaluate(scope.getDeclarationScope(), registry)
                  .resolve(registry.getErrorReporter()));
        } else {
          n.setJSType(unknownType);
        }
        break;

      case SUPER:
        traverseSuper(n);
        break;

      case AWAIT:
        scope = traverseAwait(n, scope);
        break;

      case VOID:
        n.setJSType(getNativeType(VOID_TYPE));
        scope = traverseChildren(n, scope);
        break;

      case EXPORT:
        scope = traverseChildren(n, scope);
        if (n.getBooleanProp(Node.EXPORT_DEFAULT)) {
          // TypedScopeCreator declared a dummy variable *default* to store this type. Update the
          // variable with the inferred type.
          TypedVar defaultExport = getDeclaredVar(scope, Export.DEFAULT_EXPORT_NAME);
          if (defaultExport.isTypeInferred()) {
            defaultExport.setType(getJSType(n.getOnlyChild()));
          }
        }
        isTypeable = false;
        break;

      case IMPORT_META:
        // TODO(b/137797083): Set an appropriate type.
        n.setJSType(unknownType);
        break;

      case ROOT:
      case SCRIPT:
      case MODULE_BODY:
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
      case IMPORT:
      case IMPORT_SPEC:
      case IMPORT_SPECS:
      case EXPORT_SPECS:
        // These don't need to be typed here, since they only affect control flow.
        isTypeable = false;
        break;

      case TRUE:
      case FALSE:
      case STRING:
      case NUMBER:
      case BIGINT:
      case NULL:
      case REGEXP:
      case TEMPLATELIT_STRING:
        // Primitives are typed in TypedScopeCreator.AbstractScopeBuilder#attachLiteralTypes
        break;

      default:
        throw new IllegalStateException(
            "Type inference doesn't know to handle token " + n.getToken());
    }

    if (isTypeable && n.getJSType() == null && !TOKENS_ALLOWING_NULL_TYPES.contains(n.getToken())) {
      throw new IllegalStateException("Failed to infer JSType for " + n);
    }
    return scope;
  }

  // TODO(b/154044898): delete these exceptions. Names are given null types to enable inference of
  // undeclared names assigned in multiple local scopes. The compiler also infers call and new
  // types when the invocation target is such a name.
  private static final ImmutableSet<Token> TOKENS_ALLOWING_NULL_TYPES =
      ImmutableSet.of(Token.NAME, Token.CALL, Token.NEW);

  private FlowScope traverseCall(Node callNode, FlowScope originalScope) {
    checkArgument(callNode.isCall() || callNode.isOptChainCall(), callNode);
    FlowScope scopeAfterChildren = traverseChildren(callNode, originalScope);
    FlowScope scopeAfterExecution =
        setCallNodeTypeAfterChildrenTraversed(callNode, scopeAfterChildren);
    // e.g. after `goog.assertString(x);` we can infer `x` is a string.
    return tightenTypesAfterAssertions(scopeAfterExecution, callNode);
  }

  private FlowScope traverseTaggedTemplateLit(Node tagTempLitNode, FlowScope originalScope) {
    checkArgument(tagTempLitNode.isTaggedTemplateLit(), tagTempLitNode);
    FlowScope scopeAfterChildren = traverseChildren(tagTempLitNode, originalScope);
    // A tagged template literal is really a special kind of function call.
    FlowScope scopeAfterExecution =
        setCallNodeTypeAfterChildrenTraversed(tagTempLitNode, scopeAfterChildren);
    if (tagTempLitNode.getJSType() == null) {
      tagTempLitNode.setJSType(unknownType);
    }
    return scopeAfterExecution;
  }

  private void traverseSuper(Node superNode) {
    // Find the closest non-arrow function (TODO(sdh): this could be an AbstractScope method).
    TypedScope scope = containerScope;
    while (scope != null && !NodeUtil.isNonArrowFunction(scope.getRootNode())) {
      scope = scope.getParent();
    }
    if (scope == null) {
      superNode.setJSType(unknownType);
      return;
    }

    FunctionType enclosingFunctionType =
        JSType.toMaybeFunctionType(scope.getRootNode().getJSType());
    ObjectType superNodeType = null;

    switch (superNode.getParent().getToken()) {
      case CALL:
        // Inside a constructor, `super` may have two different types. Calls to `super()` use the
        // super-ctor type, while property accesses use the super-instance type. `Scopes` are only
        // aware of the latter case.
        if (enclosingFunctionType != null && enclosingFunctionType.isConstructor()) {
          superNodeType = enclosingFunctionType.getSuperClassConstructor();
        }
        break;

      case GETELEM:
      case GETPROP:
        superNodeType = ObjectType.cast(functionScope.getSlot("super").getType());
        break;

      default:
        throw new IllegalStateException(
            "Unexpected parent of SUPER: " + superNode.getParent().toStringTree());
    }

    superNode.setJSType(superNodeType != null ? superNodeType : unknownType);
  }

  private void traverseNewTarget(Node newTargetNode) {
    // new.target is (undefined|!Function) within a vanilla function and !Function within an ES6
    // constructor.
    // Find the closest non-arrow function (TODO(sdh): this could be an AbstractScope method).
    TypedScope scope = containerScope;
    while (scope != null && !NodeUtil.isNonArrowFunction(scope.getRootNode())) {
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
    if (parent.getGrandparent().isClass()) {
      // In an ES6 constuctor, new.target may not be undefined.  In any other method, it must be
      // undefined, since methods are not constructable.
      JSTypeNative type =
          NodeUtil.isEs6ConstructorMemberFunctionDef(parent)
              ? JSTypeNative.FUNCTION_TYPE
              : VOID_TYPE;
      newTargetNode.setJSType(registry.getNativeType(type));
    } else {
      // Other functions also include undefined, in case they are not called with 'new'.
      newTargetNode.setJSType(
          registry.createUnionType(
              registry.getNativeType(JSTypeNative.FUNCTION_TYPE),
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
          inferPropertyTypesToMatchConstraint(retValue.getJSType(), fnType.getReturnType());
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
      Node name = catchNode.getFirstChild();
      JSType type = name.getJSType();
      // If the catch expression name was declared in the catch in TypedScopeCreator use that type.
      // Otherwise use "unknown".
      if (type == null) {
        type = unknownType;
        name.setJSType(unknownType);
      }
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
                && !var.isTypeInferred()
                && var.getNameNode() != null; // implicit vars (like arguments) have no nameNode

        // Whether this variable is declared not because it has JSDoc with a declaration, but
        // because it is const and the right-hand-side is easily inferrable.
        // e.g. these are 'typeless const declarations':
        //   const x = 0;
        //   /** @const */
        //   a.b.c = SomeOtherConstructor;
        // but these are not:
        //    let x = 0;
        //    /** @const @constructor */
        //    a.b.c = someMixin();
        // This is messy, since the definition of 'typeless const' is duplicated in
        // TypedScopeCreator and this code.
        boolean isTypelessConstDecl =
            isVarDeclaration
                && var.getNameNode().isName() // ignore redeclarations of implicit globals
                && NodeUtil.isConstantDeclaration(var.getJSDocInfo(), var.getNameNode())
                && !(var.getJSDocInfo() != null
                    && var.getJSDocInfo().containsDeclarationExcludingTypelessConst());

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

        boolean isVarTypeBetter =
            isVarDeclaration
                // Makes it easier to check for NPEs.
                && !resultType.isNullType()
                && !resultType.isVoidType()
                // Do not use the var type if the declaration looked like
                // /** @const */ var x = 3;
                // because this type was computed from the RHS
                && !isTypelessConstDecl;

        // TODO(nicksantos): This might be a better check once we have
        // back-inference of object/array constraints.  It will probably
        // introduce more type warnings.  It uses the result type iff it's
        // strictly narrower than the declared var type.
        //
        // boolean isVarTypeBetter = isVarDeclaration &&
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
            ObjectType objType = ObjectType.cast(rawObjType.restrictByNotNullOrUndefined());
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
    ObjectType objectType = ObjectType.cast(nodeType.restrictByNotNullOrUndefined());
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
          return objectType.defineDeclaredProperty(propName, var.getType(), getprop);
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

  /**
   * Now that BigInt is a factor, we need a better understanding of which types are involved in any
   * given operation. In most cases, simply knowing that BigInt is involved is not enough
   * information to determine that we should report an error, for example. So, we have designed this
   * enumeration (and a respective utility function below) to aid in providing more context for
   * cases that are more complex. Currently, there are four possibilities that concern us. This is
   * subject to change as we add more support for type inference with BigInts.
   */
  public enum BigIntPresence {
    NO_BIGINT,
    ALL_BIGINT,
    BIGINT_OR_NUMBER,
    BIGINT_OR_OTHER
  }

  /** Utility function for determining the BigIntPresence for any type. */
  static BigIntPresence getBigIntPresence(JSType type) {
    // Base case
    if (type.isOnlyBigInt()) {
      return BigIntPresence.ALL_BIGINT;
    }

    // Checking enum case
    EnumElementType typeAsEnumElement = type.toMaybeEnumElementType();
    if (typeAsEnumElement != null) {
      // No matter what type the enum element is, this function can resolve it to a BigIntPresence
      return getBigIntPresence(typeAsEnumElement.getPrimitiveType());
    }

    // Union case
    UnionType typeAsUnion = type.toMaybeUnionType();
    if (typeAsUnion != null) {
      boolean containsBigInt = false;
      boolean containsNumber = false;
      boolean containsOther = false;
      for (JSType alternate : typeAsUnion.getAlternates()) {
        if (getBigIntPresence(alternate) != BigIntPresence.NO_BIGINT) {
          containsBigInt = true;
        } else if (alternate.isNumber() && !alternate.isUnknownType()) {
          containsNumber = true;
        } else {
          containsOther = true;
        }
      }
      if (containsBigInt) {
        if (containsOther) {
          return BigIntPresence.BIGINT_OR_OTHER;
        } else if (containsNumber) {
          return BigIntPresence.BIGINT_OR_NUMBER;
        } else {
          return BigIntPresence.ALL_BIGINT;
        }
      }
    }

    // If it’s not a bigint object, bigint value, enum containing either, or a union, then we can
    // safely assume that it’s not a bigint in anyway
    return BigIntPresence.NO_BIGINT;
  }

  /** Check for BigInt with a unary plus. */
  private FlowScope traverseUnaryPlus(Node n, FlowScope scope) {
    scope = traverseChildren(n, scope); // Find types.
    BigIntPresence bigintPresenceInOperand = getBigIntPresence(getJSType(n.getFirstChild()));
    if (bigintPresenceInOperand != BigIntPresence.NO_BIGINT) {
      // Unary plus throws an exception when applied to a bigint
      n.setJSType(getNativeType(NO_TYPE));
    } else {
      n.setJSType(getNativeType(NUMBER_TYPE));
    }
    return scope;
  }

  /** Traverse unary minus and bitwise NOT */
  private FlowScope traverseUnaryNumericOperation(Node n, FlowScope scope) {
    scope = traverseChildren(n, scope); // Find types.
    switch (getBigIntPresence(getJSType(n.getFirstChild()))) { // BigIntPresence in operand
      case ALL_BIGINT:
        n.setJSType(getNativeType(BIGINT_TYPE));
        break;
      case NO_BIGINT:
        n.setJSType(getNativeType(NUMBER_TYPE));
        break;
      case BIGINT_OR_NUMBER:
      case BIGINT_OR_OTHER:
        n.setJSType(getNativeType(BIGINT_NUMBER));
        break;
    }
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
    n.setJSType(
        registry.createTemplatizedType(
            registry.getNativeObjectType(ARRAY_TYPE), getNativeType(UNKNOWN_TYPE)));
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
    if (objectType == null || n.getBooleanProp(Node.REFLECTED_OBJECT) || objectType.isEnumType()) {
      return scope;
    }

    String qObjName = NodeUtil.getBestLValueName(NodeUtil.getBestLValue(n));
    for (Node key = n.getFirstChild(); key != null; key = key.getNext()) {
      if (key.isComputedProp()) {
        // Don't define computed properties as inferred properties on the object
        continue;
      }

      if (key.isSpread()) {
        // TODO(b/128355893): Do smarter inferrence. There are a lot of potential issues with
        // inference on object-spread, so for now we just give up and say `Object`.
        n.setJSType(registry.getNativeType(JSTypeNative.OBJECT_TYPE));
        break;
      }

      String memberName = NodeUtil.getObjectLitKeyName(key);
      if (memberName != null) {
        JSType rawValueType = key.getFirstChild().getJSType();
        JSType valueType = TypeCheck.getObjectLitKeyTypeFromValueType(key, rawValueType);
        if (valueType == null) {
          valueType = unknownType;
        }
        objectType.defineInferredProperty(memberName, valueType, key);

        // Do normal flow inference if this is a direct property assignment.
        if (qObjName != null && key.isStringKey()) {
          String qKeyName = qObjName + "." + memberName;
          TypedVar var = getDeclaredVar(scope, qKeyName);
          JSType oldType = var == null ? null : var.getType();
          if (var != null && var.isTypeInferred()) {
            var.setType(oldType == null ? valueType : oldType.getLeastSupertype(oldType));
          }

          scope =
              scope.inferQualifiedSlot(
                  key, qKeyName, oldType == null ? unknownType : oldType, valueType, false);
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
    return type.isSubtypeOf(
        registry.createUnionType(
            VOID_TYPE,
            NULL_TYPE,
            NUMBER_TYPE,
            NUMBER_OBJECT_TYPE,
            BOOLEAN_TYPE,
            BOOLEAN_OBJECT_TYPE));
  }

  private FlowScope traverseHook(Node n, FlowScope scope) {
    Node condition = n.getFirstChild();
    Node trueNode = condition.getNext();
    Node falseNode = n.getLastChild();

    // verify the condition
    scope = traverse(condition, scope);

    // reverse abstract interpret the condition to produce two new scopes
    FlowScope trueScope =
        reverseInterpreter.getPreciserScopeKnowingConditionOutcome(condition, scope, Outcome.TRUE);
    FlowScope falseScope =
        reverseInterpreter.getPreciserScopeKnowingConditionOutcome(condition, scope, Outcome.FALSE);

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
      n.setJSType(unknownType);
    }

    return scope;
  }

  private FlowScope traverseNullishCoalesce(Node n, FlowScope scope) {
    checkArgument(n.isNullishCoalesce());
    Node left = n.getFirstChild();
    Node right = n.getLastChild();

    scope = traverse(left, scope);

    FlowScope rightScope =
        reverseInterpreter.getPreciserScopeKnowingConditionOutcome(left, scope, Outcome.NULLISH);

    FlowScope scopeAfterTraverseRight = traverse(right, rightScope);

    JSType leftType = left.getJSType();
    JSType rightType = right.getJSType();

    if (leftType != null) {
      if (!leftType.isNullable() && !leftType.isVoidable()) {
        n.setJSType(leftType);
      } else if (rightType != null) {
        n.setJSType(registry.createUnionType(leftType.restrictByNotNullOrUndefined(), rightType));
        return join(scope, scopeAfterTraverseRight);
      } else {
        n.setJSType(unknownType);
      }
    } else {
      n.setJSType(unknownType);
    }
    return scope;
  }

  /** @param n A non-constructor function invocation, i.e. CALL or TAGGED_TEMPLATELIT */
  private FlowScope setCallNodeTypeAfterChildrenTraversed(Node n, FlowScope scopeAfterChildren) {
    // Resolve goog.{require,requireType,forwardDeclare,module.get} calls separately, as they are
    // not normal functions.
    if (n.isCall()
        && !n.getParent().isExprResult() // Don't bother typing calls if the result is unused.
        && ModuleImportResolver.isGoogModuleDependencyCall(n)) {
      ScopedName name = moduleImportResolver.getClosureNamespaceTypeFromCall(n);
      if (name != null) {
        TypedScope otherModuleScope =
            scopeCreator.getNodeToScopeMapper().apply(name.getScopeRoot());
        TypedVar otherVar =
            otherModuleScope != null ? otherModuleScope.getSlot(name.getName()) : null;
        n.setJSType(otherVar != null ? otherVar.getType() : unknownType);
      } else {
        n.setJSType(unknownType);
      }
      return scopeAfterChildren;
    }

    Node left = n.getFirstChild();
    JSType functionType = getJSType(left).restrictByNotNullOrUndefined();
    if (left.isSuper()) {
      // TODO(sdh): This will probably return the super type; might want to return 'this' instead?
      return traverseInstantiation(n, functionType, scopeAfterChildren);
    } else if (functionType.isFunctionType()) {
      FunctionType fnType = functionType.toMaybeFunctionType();
      n.setJSType(fnType.getReturnType());
      backwardsInferenceFromCallSite(n, fnType, scopeAfterChildren);
    } else if (functionType.equals(getNativeType(CHECKED_UNKNOWN_TYPE))) {
      n.setJSType(getNativeType(CHECKED_UNKNOWN_TYPE));
    } else if (left.getJSType() != null && left.getJSType().isUnknownType()) {
      // TODO(lharker): do we also want to set this to unknown if the left's type is null? We would
      // lose some inference that TypeCheck does when given a null type.
      n.setJSType(unknownType);
    }
    return scopeAfterChildren;
  }

  private FlowScope tightenTypesAfterAssertions(FlowScope scope, Node callNode) {
    Node left = callNode.getFirstChild();
    Node firstParam = left.getNext();
    if (firstParam == null) {
      // this may be an assertion call but there are no arguments to assert
      return scope;
    }
    AssertionFunctionSpec assertionFunctionSpec = assertionFunctionLookup.lookupByCallee(left);
    if (assertionFunctionSpec == null) {
      // this is not a recognized assertion function
      return scope;
    }
    Node assertedNode = assertionFunctionSpec.getAssertedArg(firstParam);
    if (assertedNode == null) {
      return scope;
    }
    String assertedNodeName = assertedNode.getQualifiedName();

    // Handle assertions that enforce expressions evaluate to true.
    switch (assertionFunctionSpec.getAssertionKind()) {
      case TRUTHY:
        // Handle arbitrary expressions within the assert.
        // e.g. given `assert(typeof x === 'string')`, the resulting scope will infer x to be a
        // string.
        scope =
            reverseInterpreter.getPreciserScopeKnowingConditionOutcome(
                assertedNode, scope, Outcome.TRUE);
        // Build the result of the assertExpression
        JSType truthyType = getJSType(assertedNode).restrictByNotNullOrUndefined();
        callNode.setJSType(truthyType);
        break;

      case MATCHES_RETURN_TYPE:
        // Handle assertions that enforce expressions match the return type of the function
        FunctionType callType = JSType.toMaybeFunctionType(left.getJSType());
        JSType assertedType = callType != null ? callType.getReturnType() : unknownType;
        JSType type = getJSType(assertedNode);
        JSType narrowed;
        if (assertedType.isUnknownType() || type.isUnknownType()) {
          narrowed = assertedType;
        } else {
          narrowed = type.getGreatestSubtype(assertedType);
        }
        callNode.setJSType(narrowed);
        if (assertedNodeName != null && type.differsFrom(narrowed)) {
          scope = narrowScope(scope, assertedNode, narrowed);
        }
        break;
    }

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
   * <p>In other words, if we have, <code>
   * var x = f();
   * g(x);
   * </code> a forward type-inference engine would try to figure out the type of "x" from the return
   * type of "f". A backwards type-inference engine would try to figure out the type of "x" from the
   * parameter type of "g".
   *
   * <p>However, there are a few special syntactic forms where we do some some half-assed backwards
   * type-inference, because programmers expect it in this day and age. To take an example from
   * Java, <code>
   * List<String> x = Lists.newArrayList();
   * </code> The Java compiler will be able to infer the generic type of the List returned by
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
   * When "bind" is called on a function, we infer the type of the returned "bound" function by
   * looking at the number of parameters in the call site. We also infer the "this" type of the
   * target, if it's a function expression.
   */
  private void updateBind(Node n) {
    CodingConvention.Bind bind =
        compiler.getCodingConvention().describeFunctionBind(n, false, true);
    if (bind == null) {
      return;
    }

    Node target = bind.target;
    FunctionType callTargetFn =
        getJSType(target).restrictByNotNullOrUndefined().toMaybeFunctionType();
    if (callTargetFn == null) {
      return;
    }

    if (bind.thisValue != null && target.isFunction()) {
      JSType thisType = getJSType(bind.thisValue);
      if (thisType.toObjectType() != null
          && !thisType.isUnknownType()
          && callTargetFn.getTypeOfThis().isUnknownType()) {
        callTargetFn =
            FunctionType.builder(registry)
                .copyFromOtherFunction(callTargetFn)
                .withTypeOfThis(thisType.toObjectType())
                .buildAndResolve();
        target.setJSType(callTargetFn);
      }
    }

    JSType returnType =
        callTargetFn.getBindReturnType(
            // getBindReturnType expects the 'this' argument to be included.
            bind.getBoundParameterCount() + 1);
    FunctionType bindType = getJSType(n.getFirstChild()).toMaybeFunctionType();
    if (bindType != null && n.getFirstChild() != target) {
      // Update the type of the
      bindType =
          FunctionType.builder(registry)
              .copyFromOtherFunction(bindType)
              .withReturnType(returnType)
              .buildAndResolve();
      n.getFirstChild().setJSType(bindType);
    }

    n.setJSType(returnType);
  }

  /**
   * Performs a limited back-inference on function arguments based on the expected parameter types.
   *
   * <p>Currently this only does back-inference in two cases: it infers the type of function literal
   * arguments and adds inferred properties to inferred object-typed arguments.
   *
   * <p>For example: if someone calls `Promise<string>.prototype.then` with `(result) => ...` then
   * we infer that the type of the arrow function is `function(string): ?`, and inside the arrow
   * function body we know that `result` is a string.
   */
  private void updateTypeOfArguments(Node n, FunctionType fnType) {
    checkState(NodeUtil.isInvocation(n), n);
    Iterator<Parameter> parameters = fnType.getParameters().iterator();
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

    Parameter iParameter;
    Node iArgument;

    // Note: if there are too many or too few arguments, TypeCheck will warn.
    while (parameters.hasNext() && arguments.hasNext()) {
      iArgument = arguments.next();
      JSType iArgumentType = getJSType(iArgument);

      iParameter = parameters.next();
      JSType iParameterType = iParameter.getJSType() != null ? iParameter.getJSType() : unknownType;

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

      if (restrictedParameter != null && iArgument.isFunction() && iArgumentType.isFunctionType()) {
        FunctionType argFnType = iArgumentType.toMaybeFunctionType();
        JSDocInfo argJsdoc = iArgument.getJSDocInfo();
        // Treat the parameter & return types of the function as 'declared' if the function has
        // JSDoc with type annotations, or a parameter has inline JSDoc.
        // Note that this does not distinguish between cases where all parameters have JSDoc vs
        // only one parameter has JSDoc.
        boolean declared =
            (argJsdoc != null && argJsdoc.containsDeclaration())
                || NodeUtil.functionHasInlineJsdocs(iArgument);
        iArgument.setJSType(matchFunction(restrictedParameter, argFnType, declared));
      }
    }
  }

  /**
   * Take the current function type, and try to match the expected function type. This is a form of
   * backwards-inference, like record-type constraint matching.
   *
   * @param declared Whether the given function type is user-provided as opposed to inferred
   */
  private FunctionType matchFunction(
      FunctionType expectedType, FunctionType currentType, boolean declared) {
    if (declared) {
      // If the function was declared but it doesn't have a known "this"
      // but the expected type does, back fill it.
      if (currentType.getTypeOfThis().isUnknownType()
          && !expectedType.getTypeOfThis().isUnknownType()) {
        FunctionType replacement =
            FunctionType.builder(registry)
                .copyFromOtherFunction(currentType)
                .withTypeOfThis(expectedType.getTypeOfThis())
                .buildAndResolve();
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
   * Build the type environment where type transformations will be evaluated. It only considers the
   * template type variables that do not have a type transformation.
   */
  private Map<String, JSType> buildTypeVariables(Map<TemplateType, JSType> inferredTypes) {
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
        JSType transformedType =
            ttlObj.eval(type.getTypeTransformation(), ImmutableMap.copyOf(typeVars));
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
    Map<TemplateType, JSType> bindings =
        new InvocationTemplateTypeMatcher(this.registry, fnType, scope.getTypeOfThis(), n).match();
    Map<TemplateType, JSType> inferred = Maps.newIdentityHashMap();
    for (TemplateType key : keys) {
      inferred.put(key, bindings.getOrDefault(key, unknownType));
    }

    // If the inferred type doesn't satisfy the template bound, swap to using the bound. This
    // ensures errors will be reported in type-checking.
    inferred.replaceAll((k, v) -> v.isSubtypeOf(k.getBound()) ? v : k.getBound());

    // Try to infer the template types using the type transformations
    Map<TemplateType, JSType> typeTransformations =
        evaluateTypeTransformations(keys, inferred, scope);
    if (typeTransformations != null) {
      inferred.putAll(typeTransformations);
    }

    // Replace all template types. If we couldn't find a replacement, we
    // replace it with UNKNOWN.
    TemplateTypeReplacer replacer = TemplateTypeReplacer.forInference(registry, inferred);
    Node callTarget = n.getFirstChild();

    FunctionType replacementFnType = fnType.visit(replacer).toMaybeFunctionType();
    checkNotNull(replacementFnType);
    callTarget.setJSType(replacementFnType);
    n.setJSType(replacementFnType.getReturnType());

    return replacer.hasMadeReplacement();
  }

  private FlowScope traverseNew(Node n, FlowScope scope) {
    scope = traverseChildren(n, scope);
    Node constructor = n.getFirstChild();
    JSType constructorType = constructor.getJSType();
    return traverseInstantiation(n, constructorType, scope);
  }

  private FlowScope traverseInstantiation(Node n, JSType ctorType, FlowScope scope) {
    if (ctorType == null || ctorType.isUnknownType()) {
      n.setJSType(unknownType);
      return scope;
    }

    ctorType = ctorType.restrictByNotNullOrUndefined();

    FunctionType ctorFnType = ctorType.toMaybeFunctionType();
    if (ctorFnType == null && ctorType instanceof FunctionType) {
      // If ctorType is a NoObjectType, then toMaybeFunctionType will
      // return null. But NoObjectType implements the FunctionType
      // interface, precisely because it can validly construct objects.
      ctorFnType = (FunctionType) ctorType;
    }

    if (ctorFnType == null || !ctorFnType.isConstructor()) {
      n.setJSType(unknownType);
      return scope;
    }

    // TODO(nickreid): This probably isn't the right thing to do based on the differences between
    // the `this` parameter between CALL and NEW.
    backwardsInferenceFromCallSite(n, ctorFnType, scope);

    ObjectType instantiatedType = ctorFnType.getInstanceType();
    if (ctorFnType == registry.getNativeType(JSTypeNative.OBJECT_FUNCTION_TYPE)) {
      // TODO(b/138617950): Delete this case when `Object` and `Object<?, ?> are sparate.
    } else if (ctorFnType.hasAnyTemplateTypes()) {
      if (instantiatedType.isTemplatizedType()) {
        instantiatedType = instantiatedType.toMaybeTemplatizedType().getRawType();
      }
      // If necessary, templatized the instance type based on the the constructor parameters.
      Map<TemplateType, JSType> inferredTypes =
          new InvocationTemplateTypeMatcher(this.registry, ctorFnType, scope.getTypeOfThis(), n)
              .match();
      instantiatedType =
          registry.createTemplatizedType(instantiatedType, inferredTypes).toMaybeObjectType();
    }

    n.setJSType(instantiatedType != null ? instantiatedType : unknownType);
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

  private FlowScope traverseGetElem(Node n, FlowScope originalScope) {
    FlowScope executedScope = traverseChildren(n, originalScope);
    return setGetElemNodeTypeAfterChildrenTraversed(n, executedScope);
  }

  /**
   * Sets the appropriate type on the GETELEM node `n` after its children have been traversed.
   *
   * @param n the node that we want to finishTraversing
   * @param scopeAfterChildren scope after children are traversed
   */
  private FlowScope setGetElemNodeTypeAfterChildrenTraversed(Node n, FlowScope scopeAfterChildren) {
    checkArgument(n.isGetElem() || n.isOptChainGetElem());
    inferGetElemType(n);
    scopeAfterChildren = tightenTypeAfterDereference(n.getFirstChild(), scopeAfterChildren);
    return scopeAfterChildren;
  }

  private void inferGetElemType(Node n) {
    Node indexKey = n.getLastChild();
    JSType indexType = getJSType(indexKey);
    JSType inferredType = unknownType;
    if (indexType.isSymbolValueType()) {
      // For now, allow symbols definitions/access on any type. In the future only allow them
      // on the subtypes for which they are defined.
      // TODO(b/77474174): Type well known symbol accesses.
    } else {
      JSType type = getJSType(n.getFirstChild()).restrictByNotNullOrUndefined();
      TemplateTypeMap typeMap = type.getTemplateTypeMap();
      if (typeMap.hasTemplateType(registry.getObjectElementKey())) {
        inferredType = typeMap.getResolvedTemplateType(registry.getObjectElementKey());
      }
    }
    n.setJSType(inferredType != null ? inferredType : unknownType);
  }

  private FlowScope traverseGetProp(Node n, FlowScope scope) {
    scope = traverseChildren(n, scope);
    return setGetPropNodeTypeAfterChildrenTraversed(n, scope);
  }

  // Sets the appropriate type on the GETPROP node `n` after its children have been traversed.
  private FlowScope setGetPropNodeTypeAfterChildrenTraversed(Node n, FlowScope scopeAfterChildren) {
    checkArgument(n.isGetProp() || n.isOptChainGetProp());
    Node objNode = n.getFirstChild();
    Node property = n.getLastChild();
    n.setJSType(getPropertyType(objNode.getJSType(), property.getString(), n, scopeAfterChildren));
    return tightenTypeAfterDereference(n.getFirstChild(), scopeAfterChildren);
  }

  // Sets the appropriate type on the OptChain node `n` after its children have been traversed.
  private FlowScope setOptChainNodeTypeAfterChildrenTraversed(
      Node n, FlowScope scopeAfterChildren) {
    switch (n.getToken()) {
      case OPTCHAIN_GETPROP:
        return setGetPropNodeTypeAfterChildrenTraversed(n, scopeAfterChildren);
      case OPTCHAIN_GETELEM:
        return setGetElemNodeTypeAfterChildrenTraversed(n, scopeAfterChildren);
      case OPTCHAIN_CALL:
        return setCallNodeTypeAfterChildrenTraversed(n, scopeAfterChildren);
      default:
        throw new IllegalStateException("Illegal token inside finishTraversingOptChain");
    }
  }

  /**
   * Holds flow scopes for conditional and unconditional parts during traversal of an optional chain
   */
  private static class OptChainInfo {
    private final Node endOfChain;
    private final Node startOfChain;
    private FlowScope unconditionalScope;

    OptChainInfo(Node endOfChain, Node startOfChain) {
      this.endOfChain = endOfChain;
      this.startOfChain = startOfChain;
      this.unconditionalScope = null;
    }
  }

  /**
   * Traversal requires holding scopes from the unconditional start (lhs of the `?.`) and the
   * conditional part (rhs of the `?.`), and using the startType to determine the resulting scope.
   */
  private FlowScope traverseOptChain(Node n, FlowScope scope) {
    checkArgument(NodeUtil.isOptChainNode(n));

    if (NodeUtil.isEndOfOptChain(n)) {
      // Create new optional chain tracking object and push it onto the stack.
      final Node startOfChain = NodeUtil.getStartOfOptChain(n);
      OptChainInfo optChainInfo = new OptChainInfo(n, startOfChain);
      optChainArrayDeque.addFirst(optChainInfo);
    }

    FlowScope lhsScope = traverse(n.getFirstChild(), scope);

    if (n.isOptionalChainStart()) {
      // Store lhsScope into top-of-stack as unexecuted (unconditional) scope.
      optChainArrayDeque.peekFirst().unconditionalScope = lhsScope;
    }

    // Traverse the remaining children and capture their changes into a new FlowScope var
    // `aboutToExecuteScope`. This FlowScope must be constructed on top of the `lhsScope` and not
    // the original scope `scope`, otherwise changes to outer variable that are preserved in the
    // lhsScope would not be captured in the `aboutToExecuteScope`.
    FlowScope aboutToExecuteScope = lhsScope;
    Node nextChild = n.getSecondChild();
    while (nextChild != null) {
      aboutToExecuteScope = traverse(nextChild, aboutToExecuteScope);
      nextChild = nextChild.getNext();
    }

    // Assigns the type to `n` assuming the entire chain executes and returns the the executed
    // scope.
    FlowScope executedScope = setOptChainNodeTypeAfterChildrenTraversed(n, aboutToExecuteScope);

    // Unlike CALL, the OPTCHAIN_CALL nodes must not remain untyped when left child is untyped.
    if (n.getJSType() == null) {
      n.setJSType(unknownType);
    }

    if (NodeUtil.isEndOfOptChain(n)) {
      // Use the startNode's type to selectively join the executed scope with the unexecuted scope,
      // and update the type assigned to `n` in `setXAfterChildrenTraversed()`
      final Node startOfChain = NodeUtil.getStartOfOptChain(n);

      // Pop the stack to obtain the current chain.
      OptChainInfo currentChain = optChainArrayDeque.removeFirst();

      // Sanity check that the popped chain correctly corresponds to the current optional chain
      checkState(currentChain.endOfChain == n);
      checkState(currentChain.startOfChain == startOfChain);

      final Node startNode = checkNotNull(startOfChain.getFirstChild());
      return updateTypeWhenEndOfOptChain(n, startNode, currentChain, executedScope);
    } else {
      // `n` is just an inner node (i.e. not the end of the current chain). Simply return the
      // executedScope.
      return executedScope;
    }
  }

  // Uses the startNode's type to selectively join the executed scope with the unexecuted scope, and
  // updates the type assigned to `optChain` in `setXAfterChildrenTraversed()`
  private FlowScope updateTypeWhenEndOfOptChain(
      Node optChain, Node startNode, OptChainInfo currentChain, FlowScope executedScope) {

    JSType startType = getJSType(startNode);
    if (startType.isUnknownType()) {
      // Unknown startType: Conditional part may execute. Result type must be unknown.
      optChain.setJSType(unknownType);
      return join(executedScope, currentChain.unconditionalScope);
    } else if (startType.isNullType() || startType.isVoidType()) {
      // Conditional part will not execute.
      optChain.setJSType(registry.getNativeType(VOID_TYPE));
      return currentChain.unconditionalScope;
    } else if (!startType.isNullable()) {
      // Conditional part will execute; `optChain` was assigned the right type in
      // `setXAfterChildrenTraversed()`.
      return executedScope;
    } else {
      // Nullable start: Conditional part may execute. Need to add a VOID_TYPE to the type
      // assigned to `optChain` within `setXAfterChildrenTraversed()`.
      optChain.setJSType(
          registry.createUnionType(registry.getNativeType(VOID_TYPE), optChain.getJSType()));
      final FlowScope unexecutedScope =
          reverseInterpreter.getPreciserScopeKnowingConditionOutcome(
              startNode, currentChain.unconditionalScope, Outcome.NULLISH);
      return join(executedScope, unexecutedScope);
    }
  }

  /**
   * Suppose X is an object with inferred properties. Suppose also that X is used in a way where it
   * would only type-check correctly if some of those properties are widened. Then we should be
   * polite and automatically widen X's properties.
   *
   * <p>For a concrete example, consider: param x {{prop: (number|undefined)}} function f(x) {}
   * f({});
   *
   * <p>If we give the anonymous object an inferred property of (number|undefined), then this code
   * will type-check appropriately.
   */
  private static void inferPropertyTypesToMatchConstraint(JSType type, JSType constraint) {
    if (type == null || constraint == null) {
      return;
    }

    type.matchConstraint(constraint);
  }

  /** If we access a property of a symbol, then that symbol is not null or undefined. */
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

  private JSType getPropertyType(JSType objType, String propName, Node n, FlowScope scope) {
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

    if ((propertyType == null || propertyType.isUnknownType()) && qualifiedName != null) {
      // If we find this node in the registry, then we can infer its type.
      ObjectType regType =
          ObjectType.cast(registry.getType(scope.getDeclarationScope(), qualifiedName));
      if (regType != null) {
        propertyType = regType.getConstructor();
      }
    }

    if (propertyType == null) {
      return unknownType;
    } else if (propertyType.equals(unknownType) && isLocallyInferred) {
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

  private BooleanOutcomePair traverseShortCircuitingBinOp(Node n, FlowScope scope) {
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
            left,
            leftOutcome.getOutcomeFlowScope(left.getToken(), nIsAnd),
            Outcome.forBoolean(nIsAnd));

    // type the right node
    BooleanOutcomePair rightOutcome = traverseWithinShortCircuitingBinOp(right, rightScope);
    JSType rightType = right.getJSType();

    JSType type;
    BooleanOutcomePair outcome;
    if (leftType != null && rightType != null) {
      leftType = leftType.getRestrictedTypeGivenOutcome(Outcome.forBoolean(!nIsAnd));
      if (leftOutcome.toBooleanOutcomes == BooleanLiteralSet.get(!nIsAnd)) {
        // Either n is && and lhs is false, or n is || and lhs is true.
        // Use the restricted left type; the right side never gets evaluated.
        type = leftType;
        outcome = leftOutcome;
      } else {
        // Use the join of the restricted left type knowing the outcome of the
        // ToBoolean predicate and of the right type.
        type = leftType.getLeastSupertype(rightType);
        outcome =
            new BooleanOutcomePair(
                joinBooleanOutcomes(
                    nIsAnd, leftOutcome.toBooleanOutcomes, rightOutcome.toBooleanOutcomes),
                joinBooleanOutcomes(nIsAnd, leftOutcome.booleanValues, rightOutcome.booleanValues),
                leftOutcome.getJoinedFlowScope(),
                rightOutcome.getJoinedFlowScope());
      }
      // Exclude the boolean type if the literal set is empty because a boolean
      // can never actually be returned.
      if (outcome.booleanValues == BooleanLiteralSet.EMPTY
          && getNativeType(BOOLEAN_TYPE).isSubtypeOf(type)) {
        // Exclusion only makes sense for a union type.
        if (type.isUnionType()) {
          type = type.toMaybeUnionType().getRestrictedUnion(getNativeType(BOOLEAN_TYPE));
        }
      }
    } else {
      type = unknownType;
      outcome =
          new BooleanOutcomePair(
              BooleanLiteralSet.BOTH,
              BooleanLiteralSet.BOTH,
              leftOutcome.getJoinedFlowScope(),
              rightOutcome.getJoinedFlowScope());
    }
    n.setJSType(type);
    return outcome;
  }

  private BooleanOutcomePair traverseWithinShortCircuitingBinOp(Node n, FlowScope scope) {
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
   * When traversing short-circuiting binary operations, we need to keep track of two sets of
   * boolean literals: 1. {@code toBooleanOutcomes}: boolean literals as converted from any types,
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
        BooleanLiteralSet toBooleanOutcomes,
        BooleanLiteralSet booleanValues,
        FlowScope leftScope,
        FlowScope rightScope) {
      this.toBooleanOutcomes = toBooleanOutcomes;
      this.booleanValues = booleanValues;
      this.leftScope = leftScope;
      this.rightScope = rightScope;
    }

    /**
     * Gets the safe estimated scope without knowing if all of the subexpressions will be evaluated.
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

    /** Gets the outcome scope if we do know the outcome of the entire expression. */
    FlowScope getOutcomeFlowScope(Token nodeType, boolean outcome) {
      if ((nodeType == Token.AND && outcome) || (nodeType == Token.OR && !outcome)) {
        // We know that the whole expression must have executed.
        return rightScope;
      } else {
        return getJoinedFlowScope();
      }
    }
  }

  private BooleanOutcomePair newBooleanOutcomePair(JSType jsType, FlowScope flowScope) {
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

  /** This method gets the JSType from the Node argument and verifies that it is present. */
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

