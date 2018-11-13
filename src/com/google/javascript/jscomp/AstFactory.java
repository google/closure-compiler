/*
 * Copyright 2018 The Closure Compiler Authors.
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

import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.BooleanLiteralSet;
import com.google.javascript.rhino.jstype.FunctionBuilder;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.TemplateTypeMap;
import com.google.javascript.rhino.jstype.TemplateTypeMapReplacer;
import javax.annotation.Nullable;

/**
 * Creates AST nodes and subtrees.
 *
 * <p>This class supports creating nodes either with or without type information.
 *
 * <p>The idea is that client code can create the trees of nodes it needs without having to contain
 * logic for deciding whether type information should be added or not, and only minimal logic for
 * determining which types to add when they are necessary. Most methods in this class are able to
 * determine the correct type information from already existing AST nodes and the current scope.
 */
final class AstFactory {

  @Nullable private final JSTypeRegistry registry;
  // We need the unknown type so frequently, it's worth caching it.
  private final JSType unknownType;
  // We might not need Arguments type, but if we do, we should avoid redundant lookups
  private final Supplier<JSType> argumentsTypeSupplier;

  private AstFactory() {
    this.registry = null;
    unknownType = null;
    argumentsTypeSupplier = () -> null;
  }

  private AstFactory(JSTypeRegistry registry) {
    this.registry = registry;
    this.unknownType = getNativeType(JSTypeNative.UNKNOWN_TYPE);
    this.argumentsTypeSupplier =
        Suppliers.memoize(
            () -> {
              JSType globalType = registry.getGlobalType("Arguments");
              if (globalType != null) {
                return globalType;
              } else {
                return unknownType;
              }
            });
    ;
  }

  static AstFactory createFactoryWithoutTypes() {
    return new AstFactory();
  }

  static AstFactory createFactoryWithTypes(JSTypeRegistry registry) {
    return new AstFactory(registry);
  }

  /** Does this class instance add types to the nodes it creates? */
  boolean isAddingTypes() {
    return registry != null;
  }

  /**
   * Returns a new EXPR_RESULT node.
   *
   * <p>Blocks have no type information, so this is functionally the same as calling {@code
   * IR.exprResult(expr)}. It exists so that a pass can be consistent about always using {@code
   * AstFactory} to create new nodes.
   */
  Node exprResult(Node expr) {
    return IR.exprResult(expr);
  }

  /**
   * Returns a new BLOCK node.
   *
   * <p>Blocks have no type information, so this is functionally the same as calling {@code
   * IR.block()}. It exists so that a pass can be consistent about always using {@code AstFactory}
   * to create new nodes.
   */
  Node createBlock() {
    return IR.block();
  }

  /**
   * Returns a new BLOCK node.
   *
   * <p>Blocks have no type information, so this is functionally the same as calling {@code
   * IR.block(statement)}. It exists so that a pass can be consistent about always using {@code
   * AstFactory} to create new nodes.
   */
  Node createBlock(Node statement) {
    return IR.block(statement);
  }

  /**
   * Returns a new {@code return} statement.
   *
   * <p>Return statements have no type information, so this is functionally the same as calling
   * {@code IR.return(value)}. It exists so that a pass can be consistent about always using {@code
   * AstFactory} to create new nodes.
   */
  Node createReturn(Node value) {
    return IR.returnNode(value);
  }

  /**
   * Returns a new {@code yield} expression.
   *
   * @param jsType Type we expect to get back after the yield
   * @param value value to yield
   */
  Node createYield(JSType jsType, Node value) {
    Node result = IR.yield(value);
    if (isAddingTypes()) {
      result.setJSType(checkNotNull(jsType));
    }
    return result;
  }

  Node createString(String value) {
    Node result = IR.string(value);
    if (isAddingTypes()) {
      result.setJSType(getNativeType(JSTypeNative.STRING_TYPE));
    }
    return result;
  }

  Node createNumber(double value) {
    Node result = IR.number(value);
    if (isAddingTypes()) {
      result.setJSType(getNativeType(JSTypeNative.NUMBER_TYPE));
    }
    return result;
  }

  Node createBoolean(boolean value) {
    Node result = value ? IR.trueNode() : IR.falseNode();
    if (isAddingTypes()) {
      result.setJSType(getNativeType(JSTypeNative.BOOLEAN_TYPE));
    }
    return result;
  }

  Node createNull() {
    Node result = IR.nullNode();
    if (isAddingTypes()) {
      result.setJSType(getNativeType(JSTypeNative.NULL_TYPE));
    }
    return result;
  }

  Node createThis(JSType thisType) {
    Node result = IR.thisNode();
    if (isAddingTypes()) {
      result.setJSType(checkNotNull(thisType));
    }
    return result;
  }

  /** Creates a THIS node with the correct type for the given function node. */
  Node createThisForFunction(Node functionNode) {
    final Node result = IR.thisNode();
    if (isAddingTypes()) {
      result.setJSType(getTypeOfThisForFunctionNode(functionNode));
    }
    return result;
  }

  @Nullable
  private JSType getTypeOfThisForFunctionNode(Node functionNode) {
    if (isAddingTypes()) {
      FunctionType functionType = getFunctionType(functionNode);
      return checkNotNull(functionType.getTypeOfThis(), functionType);
    } else {
      return null; // not adding type information
    }
  }

  private FunctionType getFunctionType(Node functionNode) {
    checkState(functionNode.isFunction(), "not a function: %s", functionNode);
    // If the function declaration was cast to a different type, we want the original type
    // from before the cast.
    final JSType typeBeforeCast = functionNode.getJSTypeBeforeCast();
    final FunctionType functionType;
    if (typeBeforeCast != null) {
      functionType = typeBeforeCast.assertFunctionType();
    } else {
      functionType = functionNode.getJSTypeRequired().assertFunctionType();
    }
    return functionType;
  }

  /** Creates a NAME node having the type of "this" appropriate for the given function node. */
  Node createThisAliasReferenceForFunction(String aliasName, Node functionNode) {
    final Node result = IR.name(aliasName);
    if (isAddingTypes()) {
      result.setJSType(getTypeOfThisForFunctionNode(functionNode));
    }
    return result;
  }

  /**
   * Creates a statement declaring a const alias for "this" to be used in the given function node.
   *
   * <p>e.g. `const aliasName = this;`
   */
  Node createThisAliasDeclarationForFunction(String aliasName, Node functionNode) {
    return createSingleConstNameDeclaration(
        aliasName, createThis(getTypeOfThisForFunctionNode(functionNode)));
  }

  /**
   * Creates a new `const` declaration statement for a single variable name.
   *
   * <p>Takes the type for the variable name from the value node.
   *
   * <p>e.g. `const variableName = value;`
   */
  Node createSingleConstNameDeclaration(String variableName, Node value) {
    return IR.constNode(createName(variableName, value.getJSType()), value);
  }

  /**
   * Creates a reference to "arguments" with the type specified in externs, or unknown if the
   * externs for it weren't included.
   */
  Node createArgumentsReference() {
    Node result = IR.name("arguments");
    if (isAddingTypes()) {
      result.setJSType(argumentsTypeSupplier.get());
    }
    return result;
  }

  /**
   * Creates a statement declaring a const alias for "arguments".
   *
   * <p>e.g. `const argsAlias = arguments;`
   */
  Node createArgumentsAliasDeclaration(String aliasName) {
    return createSingleConstNameDeclaration(aliasName, createArgumentsReference());
  }

  Node createName(String name, JSType type) {
    Node result = IR.name(name);
    if (isAddingTypes()) {
      result.setJSType(checkNotNull(type));
    }
    return result;
  }

  Node createName(String name, JSTypeNative nativeType) {
    Node result = IR.name(name);
    if (isAddingTypes()) {
      result.setJSType(getNativeType(nativeType));
    }
    return result;
  }

  Node createName(Scope scope, String name) {
    Node result = IR.name(name);
    if (isAddingTypes()) {
      result.setJSType(getVarNameType(scope, name));
    }
    return result;
  }

  Node createQName(Scope scope, String qname) {
    return createQName(scope, Splitter.on(".").split(qname));
  }

  private Node createQName(Scope scope, Iterable<String> names) {
    String baseName = checkNotNull(Iterables.getFirst(names, null));
    Iterable<String> propertyNames = Iterables.skip(names, 1);
    Node baseNameNode = createName(scope, baseName);
    return createGetProps(baseNameNode, propertyNames);
  }

  Node createGetProp(Node receiver, String propertyName) {
    Node result = IR.getprop(receiver, IR.string(propertyName));
    if (isAddingTypes()) {
      result.setJSType(getJsTypeForProperty(receiver, propertyName));
    }
    return result;
  }

  /** Creates a tree of nodes representing `receiver.name1.name2.etc`. */
  private Node createGetProps(Node receiver, Iterable<String> propertyNames) {
    Node result = receiver;
    for (String propertyName : propertyNames) {
      result = createGetProp(result, propertyName);
    }
    return result;
  }

  Node createGetElem(Node receiver, Node key) {
    Node result = IR.getelem(receiver, key);
    if (isAddingTypes()) {
      // In general we cannot assume we know the type we get from a GETELEM.
      // TODO(bradfordcsmith): When receiver is an Array<T> or an Object<K, V>, use the template
      // type here.
      result.setJSType(unknownType);
    }
    return result;
  }

  Node createDelProp(Node target) {
    Node result = IR.delprop(target);
    if (isAddingTypes()) {
      result.setJSType(getNativeType(JSTypeNative.BOOLEAN_TYPE));
    }
    return result;
  }

  Node createStringKey(String key, Node value) {
    Node result = IR.stringKey(key, value);
    if (isAddingTypes()) {
      result.setJSType(value.getJSType());
    }
    return result;
  }

  Node createComputedProperty(Node key, Node value) {
    Node result = IR.computedProp(key, value);
    if (isAddingTypes()) {
      result.setJSType(value.getJSType());
    }
    return result;
  }

  Node createIn(Node left, Node right) {
    Node result = IR.in(left, right);
    if (isAddingTypes()) {
      result.setJSType(getNativeType(JSTypeNative.BOOLEAN_TYPE));
    }
    return result;
  }

  Node createComma(Node left, Node right) {
    Node result = IR.comma(left, right);
    if (isAddingTypes()) {
      result.setJSType(right.getJSType());
    }
    return result;
  }

  Node createCommas(Node first, Node second, Node... rest) {
    Node result = createComma(first, second);
    for (Node next : rest) {
      result = createComma(result, next);
    }
    return result;
  }

  Node createAnd(Node left, Node right) {
    Node result = IR.and(left, right);
    if (isAddingTypes()) {
      JSType leftType = checkNotNull(left.getJSType(), left);
      JSType rightType = checkNotNull(right.getJSType(), right);

      BooleanLiteralSet possibleLhsBooleanValues = leftType.getPossibleToBooleanOutcomes();
      switch (possibleLhsBooleanValues) {
        case TRUE:
          // left cannot be false, so rhs will always be evaluated
          result.setJSType(rightType);
          break;
        case FALSE:
          // left cannot be true, so rhs will never be evaluated
          result.setJSType(leftType);
          break;
        case BOTH:
          // result could be the type of either the lhs or the rhs
          result.setJSType(leftType.getLeastSupertype(rightType));
          break;
        default:
          checkState(
              possibleLhsBooleanValues == BooleanLiteralSet.EMPTY,
              "unexpected enum value: %s",
              possibleLhsBooleanValues);
          // TODO(bradfordcsmith): Should we be trying to determine whether we actually need
          // NO_OBJECT_TYPE or similar here? It probably doesn't matter since this code is
          // expected to execute only after all static type analysis has been done.
          result.setJSType(getNativeType(JSTypeNative.NO_TYPE));
          break;
      }
    }
    return result;
  }

  Node createOr(Node left, Node right) {
    Node result = IR.or(left, right);
    if (isAddingTypes()) {
      JSType leftType = checkNotNull(left.getJSType(), left);
      JSType rightType = checkNotNull(right.getJSType(), right);

      BooleanLiteralSet possibleLhsBooleanValues = leftType.getPossibleToBooleanOutcomes();
      switch (possibleLhsBooleanValues) {
        case TRUE:
          // left cannot be false, so rhs will never be evaluated
          result.setJSType(leftType);
          break;
        case FALSE:
          // left cannot be true, so rhs will always be evaluated
          result.setJSType(rightType);
          break;
        case BOTH:
          // result could be the type of either the lhs or the rhs
          result.setJSType(leftType.getLeastSupertype(rightType));
          break;
        default:
          checkState(
              possibleLhsBooleanValues == BooleanLiteralSet.EMPTY,
              "unexpected enum value: %s",
              possibleLhsBooleanValues);
          // TODO(bradfordcsmith): Should we be trying to determine whether we actually need
          // NO_OBJECT_TYPE or similar here? It probably doesn't matter since this code is
          // expected to execute only after all static type analysis has been done.
          result.setJSType(getNativeType(JSTypeNative.NO_TYPE));
          break;
      }
    }
    return result;
  }

  Node createCall(Node callee, Node... args) {
    Node result = NodeUtil.newCallNode(callee, args);
    if (isAddingTypes()) {
      FunctionType calleeType = JSType.toMaybeFunctionType(callee.getJSType());
      // TODO(sdh): this does not handle generic functions - we'd need to unify the argument types.
      // checkState(calleeType == null || !calleeType.hasAnyTemplateTypes(), calleeType);
      // TODO(bradfordcsmith): Consider throwing an exception if calleeType is null.
      JSType returnType = calleeType != null ? calleeType.getReturnType() : unknownType;
      result.setJSType(returnType);
    }
    return result;
  }

  Node createObjectGetPrototypeOfCall(Node argObjectNode) {
    Node objectName = createName("Object", JSTypeNative.OBJECT_FUNCTION_TYPE);
    Node objectGetPrototypeOf = createGetProp(objectName, "getPrototypeOf");
    Node result = NodeUtil.newCallNode(objectGetPrototypeOf, argObjectNode);
    if (isAddingTypes()) {
      ObjectType typeOfArgObject = argObjectNode.getJSTypeRequired().assertObjectType();
      result.setJSType(getPrototypeObjectType(typeOfArgObject));
    }
    return result;
  }

  ObjectType getPrototypeObjectType(ObjectType objectType) {
    checkNotNull(objectType);
    if (objectType.isUnknownType()) {
      // Calling getImplicitPrototype() on the unknown type returns `null`, but we want
      // the prototype of an unknown type to also be unknown.
      // TODO(bradfordcsmith): Can we fix this behavior of the unknown type?
      return objectType;
    } else {
      return checkNotNull(objectType.getImplicitPrototype(), "null prototype: %s", objectType);
    }
  }

  /**
   * Create a call that returns an instance of the given class type.
   *
   * <p>This method is intended for use in special cases, such as calling `super()` in a
   * constructor.
   */
  Node createConstructorCall(@Nullable JSType classType, Node callee, Node... args) {
    Node result = NodeUtil.newCallNode(callee, args);
    if (isAddingTypes()) {
      checkNotNull(classType);
      FunctionType constructorType = checkNotNull(classType.toMaybeFunctionType());
      ObjectType instanceType = checkNotNull(constructorType.getInstanceType());
      result.setJSType(instanceType);
    }
    return result;
  }

  Node createAssign(Node lhs, Node rhs) {
    Node result = IR.assign(lhs, rhs);
    if (isAddingTypes()) {
      result.setJSType(rhs.getJSType());
    }
    return result;
  }

  Node createEmptyObjectLit() {
    Node result = IR.objectlit();
    if (isAddingTypes()) {
      result.setJSType(registry.createAnonymousObjectType(null));
    }
    return result;
  }

  Node createEmptyFunction(JSType type) {
    Node result = NodeUtil.emptyFunction();
    if (isAddingTypes()) {
      checkNotNull(type);
      checkArgument(type.isFunctionType(), type);
      result.setJSType(checkNotNull(type));
    }
    return result;
  }

  Node createFunction(String name, Node paramList, Node body, JSType type) {
    Node nameNode = createName(name, type);
    Node result = IR.function(nameNode, paramList, body);
    if (isAddingTypes()) {
      checkArgument(type.isFunctionType(), type);
      result.setJSType(type);
    }
    return result;
  }

  Node createZeroArgFunction(String name, Node body, @Nullable JSType returnType) {
    FunctionType functionType = isAddingTypes() ? registry.createFunctionType(returnType) : null;
    return createFunction(name, IR.paramList(), body, functionType);
  }

  Node createZeroArgGeneratorFunction(String name, Node body, @Nullable JSType returnType) {
    Node result = createZeroArgFunction(name, body, returnType);
    result.setIsGeneratorFunction(true);
    return result;
  }

  Node createZeroArgArrowFunctionForExpression(Node expression) {
    Node result = IR.arrowFunction(IR.name(""), IR.paramList(), expression);
    if (isAddingTypes()) {
      // It feels like we should be adding type-of-this here, but it should remain unknown,
      // because you're allowed to supply any kind of value of `this` when calling an arrow
      // function. It will just be ignored in favor of the `this` in the scope where the
      // arrow was defined.
      FunctionType functionType =
          new FunctionBuilder(registry)
              .withReturnType(expression.getJSTypeRequired())
              .withParamsNode(IR.paramList())
              .build();
      result.setJSType(functionType);
    }
    return result;
  }

  Node createMemberFunctionDef(String name, Node function) {
    // A function used for a member function definition must have an empty name,
    // because the name string goes on the MEMBER_FUNCTION_DEF node.
    checkArgument(function.getFirstChild().getString().isEmpty(), function);
    Node result = IR.memberFunctionDef(name, function);
    if (isAddingTypes()) {
      // member function definition must share the type of the function that implements it
      result.setJSType(function.getJSType());
    }
    return result;
  }

  Node createSheq(Node expr1, Node expr2) {
    Node result = IR.sheq(expr1, expr2);
    if (isAddingTypes()) {
      result.setJSType(getNativeType(JSTypeNative.BOOLEAN_TYPE));
    }
    return result;
  }

  Node createHook(Node condition, Node expr1, Node expr2) {
    Node result = IR.hook(condition, expr1, expr2);
    if (isAddingTypes()) {
      result.setJSType(registry.createUnionType(expr1.getJSType(), expr2.getJSType()));
    }
    return result;
  }

  Node createJSCompMakeIteratorCall(Node iterable, Scope scope) {
    String function = "makeIterator";
    Node makeIteratorName = createQName(scope, "$jscomp." + function);
    // Since createCall (currently) doesn't handle templated functions, fill in the template types
    // of makeIteratorName manually.
    if (isAddingTypes() && !makeIteratorName.getJSType().isUnknownType()) {
      // if makeIteratorName has the unknown type, we must have not injected the required runtime
      // libraries - hopefully because this is in a test using NonInjectingCompiler.

      // e.g get `number` from `Iterable<number>`
      JSType iterableType =
          iterable
              .getJSType()
              .getInstantiatedTypeArgument(getNativeType(JSTypeNative.ITERABLE_TYPE));
      JSType makeIteratorType = makeIteratorName.getJSType();
      // e.g. replace
      //   function(Iterable<T>): Iterator<T>
      // with
      //   function(Iterable<number>): Iterator<number>
      TemplateTypeMap typeMap =
          registry.createTemplateTypeMap(
              makeIteratorType.getTemplateTypeMap().getTemplateKeys(),
              ImmutableList.of(iterableType));
      TemplateTypeMapReplacer replacer = new TemplateTypeMapReplacer(registry, typeMap);
      makeIteratorName.setJSType(makeIteratorType.visit(replacer));
    }
    return createCall(makeIteratorName, iterable);
  }

  Node createJscompArrayFromIteratorCall(Node iterator, Scope scope) {
    String function = "arrayFromIterator";
    Node makeIteratorName = createQName(scope, "$jscomp." + function);
    // Since createCall (currently) doesn't handle templated functions, fill in the template types
    // of makeIteratorName manually.
    if (isAddingTypes() && !makeIteratorName.getJSType().isUnknownType()) {
      // if makeIteratorName has the unknown type, we must have not injected the required runtime
      // libraries - hopefully because this is in a test using NonInjectingCompiler.

      // e.g get `number` from `Iterator<number>`
      JSType iterableType =
          iterator
              .getJSType()
              .getInstantiatedTypeArgument(getNativeType(JSTypeNative.ITERATOR_TYPE));
      JSType makeIteratorType = makeIteratorName.getJSType();
      // e.g. replace
      //   function(Iterator<T>): Array<T>
      // with
      //   function(Iterator<number>): Array<number>
      TemplateTypeMap typeMap =
          registry.createTemplateTypeMap(
              makeIteratorType.getTemplateTypeMap().getTemplateKeys(),
              ImmutableList.of(iterableType));
      TemplateTypeMapReplacer replacer = new TemplateTypeMapReplacer(registry, typeMap);
      makeIteratorName.setJSType(makeIteratorType.visit(replacer));
    }
    return createCall(makeIteratorName, iterator);
  }

  Node createJscompAsyncExecutePromiseGeneratorFunctionCall(Scope scope, Node generatorFunction) {
    String function = "asyncExecutePromiseGeneratorFunction";
    Node jscompDotAsyncExecutePromiseGeneratorFunction = createQName(scope, "$jscomp." + function);
    // TODO(bradfordcsmith): Maybe update the type to be more specific
    // Currently this method expects `function(): !Generator<?>` and returns `Promise<?>`.
    // Since we propagate type information only if type checking has already run,
    // these unknowns probably don't matter, but we should be able to be more specific with the
    // return type at least.
    return createCall(jscompDotAsyncExecutePromiseGeneratorFunction, generatorFunction);
  }

  private JSType getNativeType(JSTypeNative nativeType) {
    checkNotNull(registry, "registry is null");
    return checkNotNull(
        registry.getNativeType(nativeType), "native type not found: %s", nativeType);
  }

  /**
   * Look up the correct type for the given name in the given scope.
   *
   * <p>Returns the unknown type if no type can be found
   */
  private JSType getVarNameType(Scope scope, String name) {
    Var var = scope.getVar(name);
    JSType type = null;
    if (var != null) {
      Node nameDefinitionNode = var.getNode();
      if (nameDefinitionNode != null) {
        type = nameDefinitionNode.getJSType();
      }
    }
    if (type == null) {
      // TODO(bradfordcsmith): Consider throwing an error if the type cannot be found.
      type = unknownType;
    }
    return type;
  }

  private JSType getJsTypeForProperty(Node receiver, String propertyName) {
    // NOTE: we use both findPropertyType and getPropertyType because they are subtly
    // different: findPropertyType works on JSType, autoboxing scalars and joining unions,
    // but it returns null if the type is not found and does not handle dynamic types of
    // Function.prototype.call and .apply; whereas getPropertyType does not autobox nor
    // iterate over unions, but it does synthesize the function properties correctly, and
    // it returns unknown instead of null if the property is missing.
    JSType getpropType = null;
    JSType receiverJSType = receiver.getJSType();
    if (receiverJSType != null) {
      getpropType = receiverJSType.findPropertyType(propertyName);
      if (getpropType == null) {
        ObjectType receiverObjectType = ObjectType.cast(receiverJSType.autobox());
        getpropType =
            receiverObjectType == null
                ? unknownType
                : receiverObjectType.getPropertyType(propertyName);
      }
    }
    if (getpropType == null) {
      getpropType = unknownType;
    }
    // TODO(bradfordcsmith): Special case $jscomp.global until we annotate its type correctly.
    if (getpropType.isUnknownType()
        && propertyName.equals("global")
        && receiver.matchesQualifiedName("$jscomp")) {
      getpropType = getNativeType(JSTypeNative.GLOBAL_THIS);
    }
    return getpropType;
  }
}
