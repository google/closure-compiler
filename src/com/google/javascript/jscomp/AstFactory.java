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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.BooleanLiteralSet;
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

  private AstFactory() {
    this.registry = null;
  }

  private AstFactory(JSTypeRegistry registry) {
    this.registry = registry;
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

  /**
   * Creates a reference to "arguments" with the type specified in externs, or unknown if the
   * externs for it weren't included.
   */
  Node createArgumentsReference() {
    Node result = IR.name("arguments");
    if (isAddingTypes()) {
      JSType argumentsType = registry.getGlobalType("Arguments");
      if (argumentsType == null) {
        argumentsType = getNativeType(JSTypeNative.UNKNOWN_TYPE);
      }
      result.setJSType(argumentsType);
    }
    return result;
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
      result.setJSType(getNativeType(JSTypeNative.UNKNOWN_TYPE));
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
      JSType returnType =
          calleeType != null
              ? calleeType.getReturnType()
              : getNativeType(JSTypeNative.UNKNOWN_TYPE);
      result.setJSType(returnType);
    }
    return result;
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

  Node createZeroArgFunction(String name, Node body, JSType returnType) {
    if (isAddingTypes()) {
      FunctionType type = registry.createFunctionType(returnType);
      return createFunction(name, IR.paramList(), body, type);
    } else {
      return createFunction(name, IR.paramList(), body, null);
    }
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
      type = getNativeType(JSTypeNative.UNKNOWN_TYPE);
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
                ? getNativeType(JSTypeNative.UNKNOWN_TYPE)
                : receiverObjectType.getPropertyType(propertyName);
      } else {
        // handle issue where findPropertyType does not correctly replace template types with their
        // values. (although getPropertyType does).
        // TODO(b/116830836): remove this code path once TemplatizedType overrides findPropertyType
        JSType restrictedObjType = receiverJSType.restrictByNotNullOrUndefined();
        if (!restrictedObjType.getTemplateTypeMap().isEmpty()
            && getpropType.hasAnyTemplateTypes()) {
          TemplateTypeMap typeMap = restrictedObjType.getTemplateTypeMap();
          TemplateTypeMapReplacer replacer = new TemplateTypeMapReplacer(registry, typeMap);
          getpropType = getpropType.visit(replacer);
        }
      }
    }
    if (getpropType == null) {
      getpropType = getNativeType(JSTypeNative.UNKNOWN_TYPE);
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
