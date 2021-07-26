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
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticScope;
import com.google.javascript.rhino.StaticSlot;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.BooleanLiteralSet;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.TemplateTypeMap;
import com.google.javascript.rhino.jstype.TemplateTypeReplacer;
import java.util.Arrays;
import java.util.List;
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
 *
 * <p>AstFactory supports both Closure types (see {@link JSType}) and optimization-only types (see
 * {@link Color})s. Colors contain less information than JSTypes which puts some restrictions on the
 * amount of inference this class can do. For example, there's no way to ask "What is the color of
 * property 'x' on receiver color 'obj'". This is why many methods accept a StaticScope instead of a
 * Scope: you may pass in a {@link GlobalNamespace} or similar object which contains fully qualified
 * names, to look up colors for an entire property chain.
 *
 * <p>TODO(b/193800507): delete the methods in this class that only work for JSTypes but not colors.
 */
final class AstFactory {

  private static final Splitter DOT_SPLITTER = Splitter.on(".");

  @Nullable private final JSTypeRegistry registry;
  // We need the unknown type so frequently, it's worth caching it.
  private final JSType unknownType;
  // We might not need Arguments type, but if we do, we should avoid redundant lookups
  private final Supplier<JSType> argumentsTypeSupplier;

  enum TypeMode {
    JSTYPE,
    COLOR,
    NONE
  }

  private final TypeMode typeMode;

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
    this.typeMode = TypeMode.JSTYPE;
  }

  private AstFactory(TypeMode typeMode) {
    checkArgument(
        !TypeMode.JSTYPE.equals(typeMode), "Must pass JSTypeRegistry for mode %s", typeMode);
    this.registry = null;
    this.unknownType = null;
    this.argumentsTypeSupplier =
        () -> {
          throw new AssertionError();
        };
    this.typeMode = typeMode;
  }

  static AstFactory createFactoryWithoutTypes() {
    return new AstFactory(TypeMode.NONE);
  }

  static AstFactory createFactoryWithTypes(JSTypeRegistry registry) {
    return new AstFactory(registry);
  }

  static AstFactory createFactoryWithColors() {
    return new AstFactory(TypeMode.COLOR);
  }

  /** Does this class instance add types to the nodes it creates? */
  boolean isAddingTypes() {
    return TypeMode.JSTYPE.equals(this.typeMode);
  }

  /** Does this class instance add optimization colors to the nodes it creates? */
  boolean isAddingColors() {
    return TypeMode.COLOR.equals(this.typeMode);
  }

  // TODO(b/193800507): delete all calls to this method
  private void assertNotAddingColors() {
    checkState(!this.isAddingColors(), "method not supported for colors");
  }

  private void assertNotAddingJSTypes() {
    checkState(!this.isAddingTypes(), "method not supported for JSTypes");
  }

  /**
   * Returns a new EXPR_RESULT node.
   *
   * <p>Statements have no type information, so this is functionally the same as calling {@code
   * IR.exprResult(expr)}. It exists so that a pass can be consistent about always using {@code
   * AstFactory} to create new nodes.
   */
  Node exprResult(Node expr) {
    return IR.exprResult(expr).srcref(expr);
  }

  /**
   * Returns a new EMPTY node.
   *
   * <p>EMPTY Nodes have no type information, so this is functionally the same as calling {@code
   * IR.empty()}. It exists so that a pass can be consistent about always using {@code AstFactory}
   * to create new nodes.
   */
  Node createEmpty() {
    return IR.empty();
  }

  /**
   * Returns a new BLOCK node.
   *
   * <p>Blocks have no type information, so this is functionally the same as calling {@code
   * IR.block(statements)}. It exists so that a pass can be consistent about always using {@code
   * AstFactory} to create new nodes.
   */
  Node createBlock(Node... statements) {
    return IR.block(statements);
  }

  /**
   * Returns a new IF node.
   *
   * <p>Blocks have no type information, so this is functionally the same as calling {@code
   * IR.ifNode(cond, then)}. It exists so that a pass can be consistent about always using {@code
   * AstFactory} to create new nodes.
   */
  Node createIf(Node cond, Node then) {
    return IR.ifNode(cond, then);
  }

  /**
   * Returns a new IF node.
   *
   * <p>Blocks have no type information, so this is functionally the same as calling {@code
   * IR.ifNode(cond, then, elseNode)}. It exists so that a pass can be consistent about always using
   * {@code AstFactory} to create new nodes.
   */
  Node createIf(Node cond, Node then, Node elseNode) {
    return IR.ifNode(cond, then, elseNode);
  }

  /**
   * Returns a new FOR node.
   *
   * <p>Blocks have no type information, so this is functionally the same as calling {@code
   * IR.forNode(init, cond, incr, body)}. It exists so that a pass can be consistent about always
   * using {@code AstFactory} to create new nodes.
   */
  Node createFor(Node init, Node cond, Node incr, Node body) {
    return IR.forNode(init, cond, incr, body);
  }

  /**
   * Returns a new BREAK node.
   *
   * <p>Breaks have no type information, so this is functionally the same as calling {@code
   * IR.breakNode()}. It exists so that a pass can be consistent about always using {@code
   * AstFactory} to create new nodes.
   */
  Node createBreak() {
    return IR.breakNode();
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
   * @param type Type we expect to get back after the yield
   * @param value value to yield
   */
  Node createYield(Type type, Node value) {
    Node result = IR.yield(value);
    this.setJSTypeOrColor(type, result);
    return result;
  }

  /**
   * Returns a new {@code await} expression.
   *
   * @param type Type we expect to get back after the await
   * @param value value to await
   */
  Node createAwait(Type type, Node value) {
    Node result = IR.await(value);
    setJSTypeOrColor(type, result);
    return result;
  }

  Node createString(String value) {
    Node result = IR.string(value);
    setJSTypeOrColor(type(JSTypeNative.STRING_TYPE, StandardColors.STRING), result);
    return result;
  }

  Node createNumber(double value) {
    Node result = IR.number(value);
    setJSTypeOrColor(type(JSTypeNative.NUMBER_TYPE, StandardColors.NUMBER), result);
    return result;
  }

  Node createBoolean(boolean value) {
    Node result = value ? IR.trueNode() : IR.falseNode();
    setJSTypeOrColor(type(JSTypeNative.BOOLEAN_TYPE, StandardColors.BOOLEAN), result);
    return result;
  }

  Node createNull() {
    Node result = IR.nullNode();
    setJSTypeOrColor(type(JSTypeNative.NULL_TYPE, StandardColors.NULL_OR_VOID), result);
    return result;
  }

  Node createVoid(Node child) {
    Node result = IR.voidNode(child);
    setJSTypeOrColor(type(JSTypeNative.VOID_TYPE, StandardColors.NULL_OR_VOID), result);
    return result;
  }

  /** Returns a new Node representing the undefined value. */
  public Node createUndefinedValue() {
    // We prefer `void 0` as being shorter than `undefined`.
    // Also, it's technically possible for malicious code to assign a value to `undefined`.
    return createVoid(createNumber(0));
  }

  Node createCastToUnknown(Node child, JSDocInfo jsdoc) {
    Node result = IR.cast(child, jsdoc);
    setJSTypeOrColor(type(unknownType, StandardColors.UNKNOWN), result);
    return result;
  }

  Node createNot(Node child) {
    Node result = IR.not(child);
    setJSTypeOrColor(type(JSTypeNative.BOOLEAN_TYPE, StandardColors.BOOLEAN), result);
    return result;
  }

  Node createThis(Type thisType) {
    Node result = IR.thisNode();
    setJSTypeOrColor(thisType, result);
    return result;
  }

  Node createSuper(Type superType) {
    Node result = IR.superNode();
    setJSTypeOrColor(superType, result);
    return result;
  }

  /** Creates a THIS node with the correct type for the given function node. */
  Node createThisForFunction(Node functionNode) {
    assertNotAddingColors();
    final Node result = IR.thisNode();
    if (isAddingTypes()) {
      result.setJSType(getTypeOfThisForFunctionNode(functionNode));
    }
    return result;
  }

  /** Creates a SUPER node with the correct type for the given function node. */
  Node createSuperForFunction(Node functionNode) {
    assertNotAddingColors();
    final Node result = IR.superNode();
    if (isAddingTypes()) {
      result.setJSType(getTypeOfSuperForFunctionNode(functionNode));
    }
    return result;
  }

  @Nullable
  private JSType getTypeOfThisForFunctionNode(Node functionNode) {
    assertNotAddingColors();
    if (isAddingTypes()) {
      FunctionType functionType = getFunctionType(functionNode);
      return checkNotNull(functionType.getTypeOfThis(), functionType);
    } else {
      return null; // not adding type information
    }
  }

  @Nullable
  private JSType getTypeOfSuperForFunctionNode(Node functionNode) {
    assertNotAddingColors();
    if (isAddingTypes()) {
      ObjectType thisType = getTypeOfThisForFunctionNode(functionNode).assertObjectType();
      return checkNotNull(thisType.getSuperClassConstructor().getInstanceType(), thisType);
    } else {
      return null; // not adding type information
    }
  }

  private FunctionType getFunctionType(Node functionNode) {
    checkState(functionNode.isFunction(), "not a function: %s", functionNode);
    assertNotAddingColors();
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
    assertNotAddingColors();
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
    assertNotAddingColors();
    return createSingleConstNameDeclaration(
        aliasName, createThis(type(getTypeOfThisForFunctionNode(functionNode))));
  }

  /**
   * Creates a new `let` declaration for a single variable name with a void type and no JSDoc.
   *
   * <p>e.g. `let variableName`
   */
  Node createSingleLetNameDeclaration(String variableName) {
    return IR.let(
        createName(variableName, type(JSTypeNative.VOID_TYPE, StandardColors.NULL_OR_VOID)));
  }

  /**
   * Creates a new `var` declaration statement for a single variable name with void type and no
   * JSDoc.
   *
   * <p>e.g. `var variableName`
   */
  Node createSingleVarNameDeclaration(String variableName) {
    return IR.var(
        createName(variableName, type(JSTypeNative.VOID_TYPE, StandardColors.NULL_OR_VOID)));
  }

  /**
   * Creates a new `var` declaration statement for a single variable name.
   *
   * <p>Takes the type for the variable name from the value node.
   *
   * <p>e.g. `var variableName = value;`
   */
  Node createSingleVarNameDeclaration(String variableName, Node value) {
    return IR.var(createName(variableName, type(value)), value);
  }

  /**
   * Creates a new `const` declaration statement for a single variable name.
   *
   * <p>Takes the type for the variable name from the value node.
   *
   * <p>e.g. `const variableName = value;`
   */
  Node createSingleConstNameDeclaration(String variableName, Node value) {
    return IR.constNode(createName(variableName, type(value.getJSType(), value.getColor())), value);
  }

  /**
   * Creates a reference to "arguments" with the type specified in externs, or unknown if the
   * externs for it weren't included.
   */
  Node createArgumentsReference() {
    assertNotAddingColors();
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
    assertNotAddingColors();
    return createSingleConstNameDeclaration(aliasName, createArgumentsReference());
  }

  Node createName(String name, JSType type) {
    return createName(name, type(type));
  }

  Node createName(String name, JSTypeNative nativeType) {
    Node result = IR.name(name);
    setJSTypeOrColor(type(nativeType, StandardColors.UNKNOWN), result);
    return result;
  }

  Node createName(String name, Color color) {
    assertNotAddingJSTypes();
    return createName(name, type(unknownType, color));
  }

  Node createName(String name, Type type) {
    Node result = IR.name(name);
    setJSTypeOrColor(type, result);
    return result;
  }

  Node createName(StaticScope scope, String name) {
    Node result = IR.name(name);
    switch (this.typeMode) {
      case JSTYPE:
        result.setJSType(getVarNameType(scope, name));
        break;
      case COLOR:
        result.setColor(getVarNameColor(scope, name));
        break;
      case NONE:
        break;
    }
    return result;
  }

  Node createNameWithUnknownType(String name) {
    return createName(name, type(unknownType, StandardColors.UNKNOWN));
  }

  /**
   * Creates a qualfied name in the given scope.
   *
   * <p>Only works if {@link StaticScope#getSlot(String)} returns a name. In practice, that means
   * this throws an exception for instance methods and properties, for example.
   */
  Node createQName(StaticScope scope, String qname) {
    return createQName(scope, DOT_SPLITTER.split(qname));
  }

  /**
   * Looks up the type of a name from a {@link TypedScope} created from typechecking
   *
   * @param globalTypedScope Must be the top, global scope.
   */
  Node createQName(TypedScope globalTypedScope, String qname) {
    checkArgument(globalTypedScope == null || globalTypedScope.isGlobal(), globalTypedScope);
    assertNotAddingColors();
    List<String> nameParts = DOT_SPLITTER.splitToList(qname);
    checkState(!nameParts.isEmpty());

    String receiverPart = nameParts.get(0);
    Node receiver = IR.name(receiverPart);
    if (this.isAddingTypes()) {
      TypedVar var = checkNotNull(globalTypedScope.getVar(receiverPart), receiverPart);
      receiver.setJSType(checkNotNull(var.getType(), var));
    }

    List<String> otherParts = nameParts.subList(1, nameParts.size());
    return this.createGetProps(receiver, otherParts);
  }

  /**
   * Creates a qualfied name in the given scope.
   *
   * <p>Only works if {@link StaticScope#getSlot(String)} returns a name. In practice, that means
   * this does not work for instance methods or properties, for example.
   */
  Node createQName(StaticScope scope, Iterable<String> names) {
    String baseName = checkNotNull(Iterables.getFirst(names, null));
    Iterable<String> propertyNames = Iterables.skip(names, 1);
    return createQName(scope, baseName, propertyNames);
  }

  /**
   * Creates a qualfied name in the given scope.
   *
   * <p>Only works if {@link StaticScope#getSlot(String)} returns a name. In practice, that means
   * this does not work for instance methods or properties, for example.
   */
  Node createQName(StaticScope scope, String baseName, String... propertyNames) {
    checkNotNull(baseName);
    return createQName(scope, baseName, Arrays.asList(propertyNames));
  }

  /**
   * Creates a qualfied name in the given scope.
   *
   * <p>Only works if {@link StaticScope#getSlot(String)} returns a name. In practice, that means
   * this does not work for instance methods or properties, for example.
   */
  Node createQName(StaticScope scope, String baseName, Iterable<String> propertyNames) {
    Node baseNameNode = createName(scope, baseName);
    Node qname = baseNameNode;
    String name = baseName;
    for (String propertyName : propertyNames) {
      name += "." + propertyName;
      Type type = null;
      if (isAddingTypes()) {
        Node def =
            checkNotNull(scope.getSlot(name), "Cannot find name %s in StaticScope.", name)
                .getDeclaration()
                .getNode();
        type = type(def);
      }
      qname = createGetProp(qname, propertyName, type);
    }
    return qname;
  }

  Node createQNameWithUnknownType(String qname) {
    assertNotAddingColors();
    return createQNameWithUnknownType(DOT_SPLITTER.split(qname));
  }

  private Node createQNameWithUnknownType(Iterable<String> names) {

    String baseName = checkNotNull(Iterables.getFirst(names, null));
    Iterable<String> propertyNames = Iterables.skip(names, 1);
    return createQNameWithUnknownType(baseName, propertyNames);
  }

  Node createQNameWithUnknownType(String baseName, Iterable<String> propertyNames) {
    assertNotAddingColors();
    Node baseNameNode = createNameWithUnknownType(baseName);
    return createGetPropsWithUnknownType(baseNameNode, propertyNames);
  }

  /**
   * Creates an access of the given {@code qname} on {@code $jscomp.global}
   *
   * <p>For example, given "Object.defineProperties", returns an AST representation of
   * "$jscomp.global.Object.defineProperties".
   *
   * <p>This may be useful if adding synthetic code to a local scope, which can shadow the global
   * like Object you're trying to access. The $jscomp global should not be shadowed.
   */
  Node createJSCompDotGlobalAccess(StaticScope scope, String qname) {
    Node jscompDotGlobal = createQName(scope, "$jscomp.global");
    Node result = createQName(scope, qname);
    // Move the fully qualified qname onto the $jscomp.global getprop
    Node qnameRoot = NodeUtil.getRootOfQualifiedName(result);
    qnameRoot.replaceWith(createGetProp(jscompDotGlobal, qnameRoot.getString(), type(qnameRoot)));
    return result;
  }

  Node createGetProp(Node receiver, String propertyName) {
    assertNotAddingColors();
    Node result = IR.getprop(receiver, propertyName);
    if (isAddingTypes()) {
      result.setJSType(getJsTypeForProperty(receiver, propertyName));
    }
    return result;
  }

  Node createGetProp(Node receiver, String propertyName, Type type) {
    Node result = IR.getprop(receiver, propertyName);
    setJSTypeOrColor(type, result);
    return result;
  }

  /** Creates a tree of nodes representing `receiver.name1.name2.etc`. */
  Node createGetProps(Node receiver, Iterable<String> propertyNames) {
    assertNotAddingColors();
    Node result = receiver;
    for (String propertyName : propertyNames) {
      result = createGetProp(result, propertyName);
    }
    return result;
  }

  /** Creates a tree of nodes representing `receiver.name1.name2.etc`. */
  Node createGetProps(Node receiver, String firstPropName, String... otherPropNames) {
    assertNotAddingColors();
    Node result = createGetProp(receiver, firstPropName);
    for (String propertyName : otherPropNames) {
      result = createGetProp(result, propertyName);
    }
    return result;
  }

  /** Creates a tree of nodes representing `receiver.name1.name2.etc`. */
  Node createGetPropsWithUnknownType(Node receiver, Iterable<String> propertyNames) {
    Node result = receiver;
    for (String propertyName : propertyNames) {
      result = createGetPropWithUnknownType(result, propertyName);
    }
    return result;
  }

  Node createGetPropWithUnknownType(Node receiver, String propertyName) {
    Node result = IR.getprop(receiver, propertyName);
    setJSTypeOrColor(type(unknownType, StandardColors.UNKNOWN), result);
    return result;
  }

  Node createGetElem(Node receiver, Node key) {
    Node result = IR.getelem(receiver, key);
    // TODO(bradfordcsmith): When receiver is an Array<T> or an Object<K, V>, use the template
    // type here.
    setJSTypeOrColor(type(unknownType, StandardColors.UNKNOWN), result);
    return result;
  }

  Node createDelProp(Node target) {
    Node result = IR.delprop(target);
    setJSTypeOrColor(type(JSTypeNative.BOOLEAN_TYPE, StandardColors.BOOLEAN), result);
    return result;
  }

  Node createStringKey(String key, Node value) {
    Node result = IR.stringKey(key, value);
    setJSTypeOrColor(type(value.getJSType(), value.getColor()), result);
    return result;
  }

  Node createComputedProperty(Node key, Node value) {
    Node result = IR.computedProp(key, value);
    setJSTypeOrColor(type(value.getJSType(), value.getColor()), result);
    return result;
  }

  /**
   * Create a getter definition to be inserted into either a class body or object literal.
   *
   * <p>{@code get name() { return value; }}
   */
  Node createGetterDef(String name, Node value) {
    assertNotAddingColors();
    JSType returnType = value.getJSType();
    // Name is stored on the GETTER_DEF node. The function has no name.
    Node functionNode =
        createZeroArgFunction(/* name= */ "", IR.block(createReturn(value)), returnType);
    Node getterNode = Node.newString(Token.GETTER_DEF, name);
    getterNode.addChildToFront(functionNode);
    return getterNode;
  }

  Node createIn(Node left, Node right) {
    Node result = IR.in(left, right);
    setJSTypeOrColor(type(JSTypeNative.BOOLEAN_TYPE, StandardColors.BOOLEAN), result);
    return result;
  }

  Node createComma(Node left, Node right) {
    Node result = IR.comma(left, right);
    setJSTypeOrColor(type(right.getJSType(), right.getColor()), result);
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
    assertNotAddingColors();
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
    assertNotAddingColors();
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
    assertNotAddingColors();
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

  Node createCall(Node callee, Type resultType, Node... args) {
    Node result = NodeUtil.newCallNode(callee, args);
    setJSTypeOrColor(resultType, result);
    return result;
  }

  Node createCallWithUnknownType(Node callee, Node... args) {
    return createCall(callee, type(unknownType, StandardColors.UNKNOWN), args);
  }

  /**
   * Creates a call to Object.assign that returns the specified type.
   *
   * <p>Object.assign returns !Object in the externs, which can lose type information if the actual
   * type is known.
   */
  Node createObjectDotAssignCall(StaticScope scope, JSType returnType, Node... args) {
    assertNotAddingColors();
    Node objAssign = createQName(scope, "Object", "assign");
    Node result = createCall(objAssign, args);

    if (isAddingTypes()) {
      // Make a unique function type that returns the exact type we've inferred it to be.
      // Object.assign in the externs just returns !Object, which loses type information.
      JSType objAssignType =
          registry.createFunctionTypeWithVarArgs(
              returnType,
              registry.getNativeType(JSTypeNative.OBJECT_TYPE),
              registry.createUnionType(JSTypeNative.OBJECT_TYPE, JSTypeNative.NULL_TYPE));
      objAssign.setJSType(objAssignType);
      result.setJSType(returnType);
    }

    return result;
  }

  Node createNewNode(Node target, Node... args) {
    assertNotAddingColors();
    Node result = IR.newNode(target, args);
    if (isAddingTypes()) {
      JSType instanceType = target.getJSType();
      if (instanceType.isFunctionType()) {
        instanceType = instanceType.toMaybeFunctionType().getInstanceType();
      } else {
        instanceType = getNativeType(JSTypeNative.UNKNOWN_TYPE);
      }
      result.setJSType(instanceType);
    }
    return result;
  }

  Node createObjectGetPrototypeOfCall(StaticScope scope, Node argObjectNode) {
    assertNotAddingColors();
    Node objectGetPrototypeOf = createQName(scope, "Object.getPrototypeOf");
    Node result = createCall(objectGetPrototypeOf, argObjectNode);
    if (isAddingTypes()) {
      ObjectType typeOfArgObject = argObjectNode.getJSTypeRequired().assertObjectType();
      JSType returnedType = getPrototypeObjectType(typeOfArgObject);
      result.setJSType(returnedType);

      // Return type of the function needs to match that of the entire expression. getPrototypeOf
      // normally returns !Object.
      objectGetPrototypeOf.setJSType(
          registry.createFunctionType(returnedType, getNativeType(JSTypeNative.OBJECT_TYPE)));
    }
    return result;
  }

  ObjectType getPrototypeObjectType(ObjectType objectType) {
    assertNotAddingColors();
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
    assertNotAddingColors();
    Node result = NodeUtil.newCallNode(callee, args);
    if (isAddingTypes()) {
      checkNotNull(classType);
      FunctionType constructorType = checkNotNull(classType.toMaybeFunctionType());
      ObjectType instanceType = checkNotNull(constructorType.getInstanceType());
      result.setJSType(instanceType);
    }
    return result;
  }

  /** Creates a statement `lhs = rhs;`. */
  Node createAssignStatement(Node lhs, Node rhs) {
    return exprResult(createAssign(lhs, rhs));
  }

  /** Creates an assignment expression `lhs = rhs` */
  Node createAssign(Node lhs, Node rhs) {
    Node result = IR.assign(lhs, rhs);
    setJSTypeOrColor(type(rhs.getJSType(), rhs.getColor()), result);
    return result;
  }

  /** Creates an assignment expression `lhs = rhs` */
  Node createAssign(String lhsName, Node rhs) {
    Node name = createName(lhsName, type(rhs.getJSType(), rhs.getColor()));
    return createAssign(name, rhs);
  }

  /**
   * Creates an object-literal with zero or more elements, `{}`.
   *
   * <p>The type of the literal, if assigned, may be a supertype of the known properties.
   */
  Node createObjectLit(Node... elements) {
    Node result = IR.objectlit(elements);
    switch (this.typeMode) {
      case JSTYPE:
        result.setJSType(registry.createAnonymousObjectType(null));
        break;
      case COLOR:
        result.setColor(StandardColors.TOP_OBJECT);
        break;
      case NONE:
        break;
    }
    return result;
  }

  /** Creates an object-literal with zero or more elements and a specific type. */
  Node createObjectLit(Type type, Node... elements) {
    Node result = IR.objectlit(elements);
    setJSTypeOrColor(type, result);
    return result;
  }

  public Node createQuotedStringKey(String key, Node value) {
    Node result = IR.stringKey(key, value);
    result.setQuotedString();
    return result;
  }

  /** Creates an empty function `function() {}` */
  Node createEmptyFunction(Type type) {
    Node result = NodeUtil.emptyFunction();
    if (isAddingTypes()) {
      checkArgument(type.getJSType(registry).isFunctionType(), type);
    }
    setJSTypeOrColor(type, result);
    return result;
  }

  /** Creates an empty function `function*() {}` */
  Node createEmptyGeneratorFunction(Type type) {
    Node result = createEmptyFunction(type);
    result.setIsGeneratorFunction(true);
    return result;
  }

  /**
   * Creates a function `function name(paramList) { body }`
   *
   * @param name STRING node - empty string if no name
   * @param paramList PARAM_LIST node
   * @param body BLOCK node
   * @param type type to apply to the function itself
   */
  Node createFunction(String name, Node paramList, Node body, JSType type) {
    assertNotAddingColors();
    return createFunction(name, paramList, body, type(type));
  }

  /**
   * Creates a function `function name(paramList) { body }`
   *
   * @param name STRING node - empty string if no name
   * @param paramList PARAM_LIST node
   * @param body BLOCK node
   * @param type type to apply to the function itself
   */
  Node createFunction(String name, Node paramList, Node body, Type type) {
    Node nameNode = createName(name, type);
    Node result = IR.function(nameNode, paramList, body);
    if (isAddingTypes()) {
      checkArgument(type.getJSType(registry).isFunctionType(), type);
    }
    setJSTypeOrColor(type, result);
    return result;
  }

  Node createParamList(String... parameterNames) {
    final Node paramList = IR.paramList();
    for (String parameterName : parameterNames) {
      paramList.addChildToBack(createNameWithUnknownType(parameterName));
    }
    return paramList;
  }

  Node createZeroArgFunction(String name, Node body, @Nullable JSType returnType) {
    assertNotAddingColors();
    FunctionType functionType =
        isAddingTypes() ? registry.createFunctionType(returnType).toMaybeFunctionType() : null;
    return createFunction(name, IR.paramList(), body, type(functionType));
  }

  Node createZeroArgGeneratorFunction(String name, Node body, @Nullable JSType returnType) {
    assertNotAddingColors();
    Node result = createZeroArgFunction(name, body, returnType);
    result.setIsGeneratorFunction(true);
    return result;
  }

  Node createZeroArgArrowFunctionForExpression(Node expression) {
    assertNotAddingColors();
    Node result = IR.arrowFunction(IR.name(""), IR.paramList(), expression);
    if (isAddingTypes()) {
      // It feels like we should be adding type-of-this here, but it should remain unknown,
      // because you're allowed to supply any kind of value of `this` when calling an arrow
      // function. It will just be ignored in favor of the `this` in the scope where the
      // arrow was defined.
      FunctionType functionType =
          FunctionType.builder(registry)
              .withReturnType(expression.getJSTypeRequired())
              .withParameters()
              .buildAndResolve();
      result.setJSType(functionType);
    }
    return result;
  }

  Node createMemberFunctionDef(String name, Node function) {
    assertNotAddingColors();
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
    setJSTypeOrColor(type(JSTypeNative.BOOLEAN_TYPE, StandardColors.BOOLEAN), result);
    return result;
  }

  Node createEq(Node expr1, Node expr2) {
    Node result = IR.eq(expr1, expr2);
    setJSTypeOrColor(type(JSTypeNative.BOOLEAN_TYPE, StandardColors.BOOLEAN), result);
    return result;
  }

  Node createNe(Node expr1, Node expr2) {
    Node result = IR.ne(expr1, expr2);
    setJSTypeOrColor(type(JSTypeNative.BOOLEAN_TYPE, StandardColors.BOOLEAN), result);
    return result;
  }

  Node createHook(Node condition, Node expr1, Node expr2) {
    assertNotAddingColors();
    Node result = IR.hook(condition, expr1, expr2);
    if (isAddingTypes()) {
      result.setJSType(registry.createUnionType(expr1.getJSType(), expr2.getJSType()));
    }
    return result;
  }

  Node createArraylit(Node... elements) {
    assertNotAddingColors();
    return createArraylit(Arrays.asList(elements));
  }

  Node createArraylit(Iterable<Node> elements) {
    assertNotAddingColors();
    Node result = IR.arraylit(elements);
    if (isAddingTypes()) {
      result.setJSType(
          registry.createTemplatizedType(
              registry.getNativeObjectType(JSTypeNative.ARRAY_TYPE),
              // TODO(nickreid): Use a reasonable template type. Remeber to consider SPREAD.
              getNativeType(JSTypeNative.UNKNOWN_TYPE)));
    }
    return result;
  }

  Node createJSCompMakeIteratorCall(Node iterable, StaticScope scope) {
    assertNotAddingColors();
    String function = "makeIterator";
    Node makeIteratorName = createQName(scope, "$jscomp", function);
    // Since createCall (currently) doesn't handle templated functions, fill in the template types
    // of makeIteratorName manually.
    if (isAddingTypes() && !makeIteratorName.getJSType().isUnknownType()) {
      // if makeIteratorName has the unknown type, we must have not injected the required runtime
      // libraries - hopefully because this is in a test using NonInjectingCompiler.

      // e.g get `number` from `Iterable<number>`
      JSType iterableType =
          iterable
              .getJSType()
              .getTemplateTypeMap()
              .getResolvedTemplateType(registry.getIterableTemplate());
      JSType makeIteratorType = makeIteratorName.getJSType();
      // e.g. replace
      //   function(Iterable<T>): Iterator<T>
      // with
      //   function(Iterable<number>): Iterator<number>
      makeIteratorName.setJSType(replaceTemplate(makeIteratorType, ImmutableList.of(iterableType)));
    }
    return createCall(makeIteratorName, iterable);
  }

  Node createJscompArrayFromIteratorCall(Node iterator, StaticScope scope) {
    assertNotAddingColors();
    Node makeIteratorName = createQName(scope, "$jscomp", "arrayFromIterator");
    // Since createCall (currently) doesn't handle templated functions, fill in the template types
    // of makeIteratorName manually.
    if (isAddingTypes() && !makeIteratorName.getJSType().isUnknownType()) {
      // if makeIteratorName has the unknown type, we must have not injected the required runtime
      // libraries - hopefully because this is in a test using NonInjectingCompiler.

      JSType iterableType =
          iterator
              .getJSType()
              .getTemplateTypeMap()
              .getResolvedTemplateType(registry.getIteratorValueTemplate());
      JSType makeIteratorType = makeIteratorName.getJSType();
      // e.g. replace
      //   function(Iterator<T>): Array<T>
      // with
      //   function(Iterator<number>): Array<number>
      makeIteratorName.setJSType(replaceTemplate(makeIteratorType, ImmutableList.of(iterableType)));
    }
    return createCall(makeIteratorName, iterator);
  }

  Node createJscompArrayFromIterableCall(Node iterable, StaticScope scope) {
    // TODO(b/193800507): consider making this verify the arrayFromIterable $jscomp runtime library
    // is injected.
    assertNotAddingColors();
    Node makeIterableName = createQName(scope, "$jscomp", "arrayFromIterable");
    // Since createCall (currently) doesn't handle templated functions, fill in the template types
    // of makeIteratorName manually.
    if (isAddingTypes() && !makeIterableName.getJSType().isUnknownType()) {
      // if makeIteratorName has the unknown type, we must have not injected the required runtime
      // libraries - hopefully because this is in a test using NonInjectingCompiler.

      JSType iterableType =
          iterable
              .getJSType()
              .getTemplateTypeMap()
              .getResolvedTemplateType(registry.getIterableTemplate());
      JSType makeIterableType = makeIterableName.getJSType();
      // e.g. replace
      //   function(Iterable<T>): Array<T>
      // with
      //   function(Iterable<number>): Array<number>
      makeIterableName.setJSType(replaceTemplate(makeIterableType, ImmutableList.of(iterableType)));
    }
    return createCall(makeIterableName, iterable);
  }

  /**
   * Given an iterable like {@code rhs} in
   *
   * <pre>{@code
   * for await (lhs of rhs) { block(); }
   * }</pre>
   *
   * <p>returns a call node for the {@code rhs} wrapped in a {@code $jscomp.makeAsyncIterator} call.
   *
   * <pre>{@code
   * $jscomp.makeAsyncIterator(rhs)
   * }</pre>
   */
  Node createJSCompMakeAsyncIteratorCall(Node iterable, StaticScope scope) {
    assertNotAddingColors();
    Node makeIteratorAsyncName = createQName(scope, "$jscomp", "makeAsyncIterator");
    // Since createCall (currently) doesn't handle templated functions, fill in the template types
    // of makeIteratorName manually.
    if (isAddingTypes() && !makeIteratorAsyncName.getJSType().isUnknownType()) {
      // if makeIteratorName has the unknown type, we must have not injected the required runtime
      // libraries - hopefully because this is in a test using NonInjectingCompiler.

      // e.g get `number` from `AsyncIterable<number>`
      JSType asyncIterableType =
          JsIterables.maybeBoxIterableOrAsyncIterable(iterable.getJSType(), registry)
              .orElse(unknownType);
      JSType makeAsyncIteratorType = makeIteratorAsyncName.getJSType();
      // e.g. replace
      //   function(AsyncIterable<T>): AsyncIterator<T>
      // with
      //   function(AsyncIterable<number>): AsyncIterator<number>
      makeIteratorAsyncName.setJSType(
          replaceTemplate(makeAsyncIteratorType, ImmutableList.of(asyncIterableType)));
    }
    return createCall(makeIteratorAsyncName, iterable);
  }

  private JSType replaceTemplate(JSType templatedType, ImmutableList<JSType> templateTypes) {
    TemplateTypeMap typeMap =
        registry
            .getEmptyTemplateTypeMap()
            .copyWithExtension(templatedType.getTemplateTypeMap().getTemplateKeys(), templateTypes);
    TemplateTypeReplacer replacer = TemplateTypeReplacer.forPartialReplacement(registry, typeMap);
    return templatedType.visit(replacer);
  }

  /**
   * Creates a reference to $jscomp.AsyncGeneratorWrapper with the template filled in to match the
   * original function.
   *
   * @param originalFunctionType the type of the async generator function that needs transpilation
   */
  Node createAsyncGeneratorWrapperReference(JSType originalFunctionType, StaticScope scope) {
    assertNotAddingColors();
    Node ctor = createQName(scope, "$jscomp", "AsyncGeneratorWrapper");

    if (isAddingTypes() && !ctor.getJSType().isUnknownType()) {
      // if ctor has the unknown type, we must have not injected the required runtime
      // libraries - hopefully because this is in a test using NonInjectingCompiler.

      // e.g get `number` from `AsyncIterable<number>`
      JSType yieldedType =
          originalFunctionType
              .toMaybeFunctionType()
              .getReturnType()
              .getTemplateTypeMap()
              .getResolvedTemplateType(registry.getAsyncIterableTemplate());

      // e.g. replace
      //  AsyncGeneratorWrapper<T>
      // with
      //  AsyncGeneratorWrapper<number>
      ctor.setJSType(replaceTemplate(ctor.getJSType(), ImmutableList.of(yieldedType)));
    }

    return ctor;
  }

  /**
   * Creates an empty generator function with the correct return type to be an argument to
   * $jscomp.AsyncGeneratorWrapper.
   *
   * @param asyncGeneratorWrapperType the specific type of the $jscomp.AsyncGeneratorWrapper with
   *     its template filled in. Should be the type on the node returned from
   *     createAsyncGeneratorWrapperReference.
   */
  Node createEmptyAsyncGeneratorWrapperArgument(JSType asyncGeneratorWrapperType) {
    assertNotAddingColors();
    JSType generatorType = null;

    if (isAddingTypes()) {
      if (asyncGeneratorWrapperType.isUnknownType()) {
        // Not injecting libraries?
        generatorType =
            registry.createFunctionType(
                replaceTemplate(
                    getNativeType(JSTypeNative.GENERATOR_TYPE), ImmutableList.of(unknownType)));
      } else {
        // Generator<$jscomp.AsyncGeneratorWrapper$ActionRecord<number>>
        JSType innerFunctionReturnType =
            Iterables.getOnlyElement(
                    asyncGeneratorWrapperType.toMaybeFunctionType().getParameters())
                .getJSType();
        generatorType = registry.createFunctionType(innerFunctionReturnType);
      }
    }

    return createEmptyGeneratorFunction(type(generatorType));
  }

  Node createJscompAsyncExecutePromiseGeneratorFunctionCall(
      StaticScope scope, Node generatorFunction) {
    assertNotAddingColors();
    Node jscompDotAsyncExecutePromiseGeneratorFunction =
        createQName(scope, "$jscomp.asyncExecutePromiseGeneratorFunction");
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
  private JSType getVarNameType(StaticScope scope, String name) {
    StaticSlot var = scope.getSlot(name);
    JSType type = null;
    if (var != null) {
      Node nameDefinitionNode =
          checkNotNull(
                  var.getDeclaration(), "Cannot find type for var with missing declaration %s", var)
              .getNode();
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

  /**
   * Look up the correct type for the given name in the given scope.
   *
   * <p>Returns the unknown type if no type can be found
   */
  private Color getVarNameColor(StaticScope scope, String name) {
    StaticSlot var = scope.getSlot(name);
    Color color = null;
    if (var != null) {
      Node nameDefinitionNode = var.getDeclaration().getNode();
      if (nameDefinitionNode != null) {
        color = nameDefinitionNode.getColor();
      }
    }
    if (color == null) {
      // TODO(bradfordcsmith): Consider throwing an error if the type cannot be found.
      color = StandardColors.UNKNOWN;
    }
    return color;
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
        && receiver.matchesName("$jscomp")) {
      getpropType = getNativeType(JSTypeNative.GLOBAL_THIS);
    }
    return getpropType;
  }

  private void setJSTypeOrColor(Type type, Node result) {
    switch (this.typeMode) {
      case JSTYPE:
        result.setJSType(type.getJSType(registry));
        break;
      case COLOR:
        result.setColor(type.getColor());
        break;
      case NONE:
        break;
    }
  }

  interface Type {
    JSType getJSType(JSTypeRegistry registry);

    Color getColor();
  }

  private static final class TypeOnNode implements Type {
    private final Node n;

    TypeOnNode(Node n) {
      this.n = n;
    }

    @Override
    public JSType getJSType(JSTypeRegistry registry) {
      return checkNotNull(this.n.getJSType(), n);
    }

    @Override
    public Color getColor() {
      return checkNotNull(this.n.getColor(), n);
    }
  }

  private static final class JSTypeOrColor implements Type {
    private final JSType jstype;
    private final JSTypeNative jstypeNative;
    private final Color color;

    JSTypeOrColor(JSTypeNative jstypeNative, Color color) {
      this.jstypeNative = jstypeNative;
      this.jstype = null;
      this.color = color;
    }

    JSTypeOrColor(JSType jstype, Color color) {
      this.jstype = jstype;
      this.jstypeNative = null;
      this.color = color;
    }

    @Override
    public JSType getJSType(JSTypeRegistry registry) {
      return this.jstype != null ? checkNotNull(this.jstype) : registry.getNativeType(jstypeNative);
    }

    @Override
    public Color getColor() {
      return checkNotNull(this.color);
    }
  }

  /** Uses the JSType or Color of the given node as a template for adding type information */
  static Type type(Node node) {
    return new TypeOnNode(node);
  }

  static Type type(JSType type) {
    return new JSTypeOrColor(type, null);
  }

  static Type type(Color type) {
    return new JSTypeOrColor((JSType) null, type);
  }

  static Type type(JSType type, Color color) {
    return new JSTypeOrColor(type, color);
  }

  static Type type(JSTypeNative type, Color color) {
    return new JSTypeOrColor(type, color);
  }
}
