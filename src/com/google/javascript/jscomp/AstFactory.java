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
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.ColorId;
import com.google.javascript.jscomp.colors.ColorRegistry;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticRef;
import com.google.javascript.rhino.StaticScope;
import com.google.javascript.rhino.StaticSlot;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.TemplateTypeMap;
import com.google.javascript.rhino.jstype.TemplateTypeReplacer;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.jspecify.nullness.Nullable;

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

  private final @Nullable ColorRegistry colorRegistry;
  private final @Nullable JSTypeRegistry registry;
  // We need the unknown type so frequently, it's worth caching it.
  private final @Nullable JSType unknownType;
  private static final Supplier<Color> bigintNumberStringColor =
      Suppliers.memoize(
          () ->
              Color.createUnion(
                  ImmutableSet.of(
                      StandardColors.BIGINT, StandardColors.STRING, StandardColors.NUMBER)));

  enum TypeMode {
    JSTYPE,
    COLOR,
    NONE
  }

  private final TypeMode typeMode;
  private final LifeCycleStage lifeCycleStage;

  private AstFactory(LifeCycleStage lifeCycleStage, JSTypeRegistry registry) {
    this.lifeCycleStage = lifeCycleStage;
    this.registry = registry;
    this.colorRegistry = null;
    this.unknownType = getNativeType(JSTypeNative.UNKNOWN_TYPE);
    this.typeMode = TypeMode.JSTYPE;
  }

  private AstFactory(LifeCycleStage lifeCycleStage) {
    this.lifeCycleStage = lifeCycleStage;
    this.registry = null;
    this.colorRegistry = null;
    this.unknownType = null;
    this.typeMode = TypeMode.NONE;
  }

  private AstFactory(LifeCycleStage lifeCycleStage, ColorRegistry colorRegistry) {
    this.lifeCycleStage = lifeCycleStage;
    this.registry = null;
    this.colorRegistry = colorRegistry;
    this.unknownType = null;
    this.typeMode = TypeMode.COLOR;
  }

  static AstFactory createFactoryWithoutTypes(LifeCycleStage lifeCycleStage) {
    return new AstFactory(lifeCycleStage);
  }

  static AstFactory createFactoryWithTypes(LifeCycleStage lifeCycleStage, JSTypeRegistry registry) {
    return new AstFactory(lifeCycleStage, registry);
  }

  static AstFactory createFactoryWithColors(
      LifeCycleStage lifeCycleStage, ColorRegistry colorRegistry) {
    return new AstFactory(lifeCycleStage, colorRegistry);
  }

  /** Does this class instance add types to the nodes it creates? */
  boolean isAddingTypes() {
    return TypeMode.JSTYPE.equals(this.typeMode);
  }

  /** Does this class instance add optimization colors to the nodes it creates? */
  boolean isAddingColors() {
    return TypeMode.COLOR.equals(this.typeMode);
  }

  private void assertNotAddingColors() {
    checkState(!this.isAddingColors(), "method not supported for colors");
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
   * Returns a new LABEL node.
   *
   * <p>Breaks have no type information, so this is functionally the same as calling {@code
   * IR.label()}. It exists so that a pass can be consistent about always using {@code AstFactory}
   * to create new nodes.
   */
  Node createLabel(Node label, Node stmt) {
    return IR.label(label, stmt);
  }

  /**
   * Returns a new CATCH node.
   *
   * <p>CATCH have no type information, so this is functionally the same as calling {@code
   * IR.catchNode()}. It exists so that a pass can be consistent about always using {@code
   * AstFactory} to create new nodes.
   */
  Node createCatch(Node error, Node block) {
    return IR.catchNode(error, block);
  }

  /**
   * Returns a new TRY-FINALLY node.
   *
   * <p>TRY-FINALLY have no type information, so this is functionally the same as calling {@code
   * IR.tryFinally()}. It exists so that a pass can be consistent about always using {@code
   * AstFactory} to create new nodes.
   */
  Node createTryFinally(Node tryBlock, Node finallyBlock) {
    return IR.tryFinally(tryBlock, finallyBlock);
  }

  /**
   * Returns a new TRY-CATCH-FINALLY node.
   *
   * <p>TRY-CATCH-FINALLY have no type information, so this is functionally the same as calling
   * {@code IR.tryCatchFinally()}. It exists so that a pass can be consistent about always using
   * {@code AstFactory} to create new nodes.
   */
  Node createTryCatchFinally(Node tryBlock, Node catchNode, Node finallyBlock) {
    checkState(tryBlock.isBlock());
    checkState(catchNode.isCatch());
    checkState(finallyBlock.isBlock());
    return IR.tryCatchFinally(tryBlock, catchNode, finallyBlock);
  }

  /**
   * Returns a new THROW node.
   *
   * <p>THROW have no type information, so this is functionally the same as calling {@code
   * IR.throwNode()}. It exists so that a pass can be consistent about always using {@code
   * AstFactory} to create new nodes.
   */
  Node createThrow(Node expr) {
    return IR.throwNode(expr);
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
    Node result = IR.yieldNode(value);
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

  /**
   * Creates a THIS node with the correct type for the given ES6 class node.
   *
   * <p>With the optimization colors type system, we can support inferring the type of this for
   * constructors but not generic functions annotated @this
   */
  Node createThisForEs6Class(Node functionNode) {
    checkState(functionNode.isClass(), functionNode);
    final Node result = IR.thisNode();
    setJSTypeOrColor(getTypeOfThisForEs6Class(functionNode), result);
    return result;
  }

  /**
   * Creates a THIS node with the correct type for the given ES6 class node.
   *
   * <p>With the optimization colors type system, we can support inferring the type of this for
   * constructors but not generic functions annotated @this
   */
  Node createThisForEs6ClassMember(Node memberNode) {
    checkArgument(memberNode.getParent().isClassMembers());
    checkArgument(
        memberNode.isMemberFunctionDef()
            || memberNode.isMemberFieldDef()
            || memberNode.isComputedFieldDef());
    Node classNode = memberNode.getGrandparent();
    if (memberNode.isStaticMember()) {
      final Node result = IR.thisNode();
      setJSTypeOrColor(type(classNode), result);
      return result;
    } else {
      return createThisForEs6Class(classNode);
    }
  }

  private @Nullable JSType getTypeOfThisForFunctionNode(Node functionNode) {
    assertNotAddingColors();
    if (isAddingTypes()) {
      FunctionType functionType = getFunctionType(functionNode);
      return checkNotNull(functionType.getTypeOfThis(), functionType);
    } else {
      return null; // not adding type information
    }
  }

  private @Nullable Type getTypeOfThisForEs6Class(Node functionNode) {
    checkArgument(functionNode.isClass(), functionNode);
    switch (this.typeMode) {
      case JSTYPE:
        return type(getTypeOfThisForFunctionNode(functionNode));
      case COLOR:
        return type(getInstanceOfColor(functionNode.getColor()));
      case NONE:
        return noTypeInformation();
    }
    throw new AssertionError();
  }

  private FunctionType getFunctionType(Node functionNode) {
    checkState(
        functionNode.isFunction() || functionNode.isClass(),
        "not a function or class: %s",
        functionNode);
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

  /**
   * Creates a NAME node having the type of "this" appropriate for the given ES6 class node
   *
   * <p>With the optimization colors type system, we can support inferring the type of this for
   * classes but not generic functions annotated @this.
   */
  Node createThisAliasReferenceForEs6Class(String aliasName, Node functionNode) {
    return createName(aliasName, getTypeOfThisForEs6Class(functionNode));
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
   * Creates a new `let` declaration statement for a single variable name.
   *
   * <p>Takes the type for the variable name from the value node.
   *
   * <p>e.g. `let variableName = value;`
   */
  Node createSingleLetNameDeclaration(String variableName, Node value) {
    return IR.let(createName(variableName, type(value)), value);
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
    Node nameNode = createConstantName(variableName, type(value.getJSType(), value.getColor()));
    return IR.constNode(nameNode, value);
  }

  /**
   * Creates a new `const` declaration statement for an object pattern.
   *
   * <p>e.g. `const {Foo} = value;`
   */
  Node createSingleConstObjectPatternDeclaration(Node objectPattern, Node value) {
    checkState(objectPattern.isObjectPattern(), "not an object pattern: %s", objectPattern);
    return IR.constNode(objectPattern, value);
  }

  /**
   * Creates a reference to "arguments" with the type specified in externs, or unknown if the
   * externs for it weren't included.
   */
  Node createArgumentsReference() {
    Node result = IR.name("arguments");
    switch (this.typeMode) {
      case JSTYPE:
        result.setJSType(registry.getNativeType(JSTypeNative.ARGUMENTS_TYPE));
        break;
      case COLOR:
        result.setColor(colorRegistry.get(StandardColors.ARGUMENTS_ID));
        break;
      case NONE:
        break;
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

  /**
   * Creates a name node with the provided type information.
   *
   * <p>NOTE: You should use {@link #createName(StaticScope, String)} when creating a new name node
   * that references an existing variable. That version will look up the declaration of the variable
   * and ensure this reference to it is correct by adding type information and / or any other
   * necessary data.
   *
   * <p>If you are creating a reference to a variable that won't be visible in the current scope
   * (e.g. because you are creating a new variable), you can use this method. However, if you've
   * just created the variable declaration, you could also just clone the {@code NAME} node from it
   * to create the new reference.
   */
  Node createName(String name, Type type) {
    Node result = IR.name(name);
    setJSTypeOrColor(type, result);
    if (lifeCycleStage.isNormalized() && name.startsWith("$jscomp")) {
      // $jscomp will always be a constant and needs to be marked that way to satisfy
      // the normalization invariants.
      result.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    }
    return result;
  }

  /** Use this when you know you need to create a constant name for const declarations */
  Node createConstantName(String name, Type type) {
    Node result = IR.name(name);
    setJSTypeOrColor(type, result);
    if (lifeCycleStage.isNormalized()) {
      result.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    }
    return result;
  }

  Node createName(@Nullable StaticScope scope, String name) {
    final Node result = IR.name(name);

    if (lifeCycleStage.isNormalized() || !typeMode.equals(TypeMode.NONE)) {
      // We need a scope to maintain normalization and / or propagate type information
      checkNotNull(
          scope,
          "A scope is required [lifeCycleStage: %s, typeMode: %s]",
          lifeCycleStage,
          typeMode);
      final StaticSlot var = scope.getSlot(name);
      if (var == null) {
        // TODO(bradfordcsmith): Why is this special exception needed?
        // There are a few cases where `$jscomp` isn't found in the code (implying that runtime
        // library injection somehow didn't happen), but we do perform transpilations which require
        // it to exist. Can we fix that?
        // This only happens when type checking is not being done.
        checkState(
            name.equals("$jscomp") && typeMode.equals(TypeMode.NONE),
            "Missing var %s in scope %s",
            name,
            scope);
      } else {
        final StaticRef declaration =
            checkNotNull(
                var.getDeclaration(), "Cannot find type for var with missing declaration %s", var);
        final Node varDefinitionNode =
            checkNotNull(declaration.getNode(), "Missing node for declaration %s", declaration);

        // Normalization requires that all references to a constant variable have this property.
        if (varDefinitionNode.getBooleanProp(Node.IS_CONSTANT_NAME)) {
          result.putBooleanProp(Node.IS_CONSTANT_NAME, true);
        }
        switch (typeMode) {
          case JSTYPE:
            JSType definitionType = varDefinitionNode.getJSType();
            // TODO(b/149843534): crash instead of defaulting to unknown
            result.setJSType(definitionType != null ? definitionType : unknownType);
            break;
          case COLOR:
            Color definitionColor = varDefinitionNode.getColor();
            // TODO(b/149843534): crash instead of defaulting to unknown
            result.setColor(definitionColor != null ? definitionColor : StandardColors.UNKNOWN);
            break;
          case NONE:
            break;
        }
      }
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
   * @deprecated Prefer {@link #createQName(StaticScope, String)}
   */
  @Deprecated
  Node createQNameFromTypedScope(TypedScope globalTypedScope, String qname) {
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
    return this.createGetPropsWithoutColors(receiver, otherParts);
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
      if (isAddingTypes() || isAddingColors()) {
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
    return createQNameWithUnknownType(DOT_SPLITTER.split(qname));
  }

  private Node createQNameWithUnknownType(Iterable<String> names) {
    String baseName = checkNotNull(Iterables.getFirst(names, null));
    Iterable<String> propertyNames = Iterables.skip(names, 1);
    return createQNameWithUnknownType(baseName, propertyNames);
  }

  Node createQNameWithUnknownType(String baseName, Iterable<String> propertyNames) {
    Node baseNameNode = createNameWithUnknownType(baseName);
    return createGetPropsWithUnknownType(baseNameNode, propertyNames);
  }

  /**
   * Creates a Node representing <receiver>.prototype
   *
   * <p>For example, given the AST for `Foo`, returns `Foo.prototype`
   */
  Node createPrototypeAccess(Node receiver) {
    Node result = IR.getprop(receiver, "prototype");
    switch (this.typeMode) {
      case JSTYPE:
        result.setJSType(getJsTypeForProperty(receiver, "prototype"));
        break;
      case COLOR:
        checkNotNull(receiver.getColor(), "Missing color on %s", receiver);
        ImmutableSet<Color> possiblePrototypes = receiver.getColor().getPrototypes();
        result.setColor(
            possiblePrototypes.isEmpty()
                ? StandardColors.UNKNOWN
                : Color.createUnion(possiblePrototypes));
        break;
      case NONE:
        break;
    }
    return result;
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

  /**
   * @deprecated Use {@link #createGetProp(Node, String, Type)} instead.
   */
  @Deprecated
  Node createGetPropWithoutColor(Node receiver, String propertyName) {
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

  /**
   * Creates a tree of nodes representing `receiver.name1.name2.etc`.
   *
   * @deprecated use individual {@link #createGetProp(Node, String, Type)} calls or {@link
   *     #createQName(StaticScope, String)} instead.
   */
  @Deprecated
  Node createGetPropsWithoutColors(Node receiver, Iterable<String> propertyNames) {
    assertNotAddingColors();
    Node result = receiver;
    for (String propertyName : propertyNames) {
      result = createGetPropWithoutColor(result, propertyName);
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
    Node result = IR.and(left, right);
    switch (this.typeMode) {
      case JSTYPE:
        JSType leftType = checkNotNull(left.getJSType(), left);
        JSType rightType = checkNotNull(right.getJSType(), right);
        result.setJSType(this.registry.createUnionType(leftType, rightType));
        break;
      case COLOR:
        Color leftColor = checkNotNull(left.getColor(), left);
        Color rightColor = checkNotNull(right.getColor(), right);
        result.setColor(Color.createUnion(ImmutableSet.of(leftColor, rightColor)));
        break;
      case NONE:
        break;
    }
    return result;
  }

  Node createOr(Node left, Node right) {
    Node result = IR.or(left, right);
    switch (this.typeMode) {
      case JSTYPE:
        JSType leftType = checkNotNull(left.getJSType(), left);
        JSType rightType = checkNotNull(right.getJSType(), right);
        result.setJSType(this.registry.createUnionType(leftType, rightType));
        break;
      case COLOR:
        Color leftColor = checkNotNull(left.getColor(), left);
        Color rightColor = checkNotNull(right.getColor(), right);
        result.setColor(Color.createUnion(ImmutableSet.of(leftColor, rightColor)));
        break;
      case NONE:
        break;
    }
    return result;
  }

  Node createAdd(Node left, Node right) {
    Node result = IR.add(left, right);
    // Note: this result type could be made tighter if it proves useful for optimizations later on
    // like setting the string type if both operands are strings.
    switch (this.typeMode) {
      case JSTYPE:
        result.setJSType(getNativeType(JSTypeNative.BIGINT_NUMBER_STRING));
        break;
      case COLOR:
        result.setColor(bigintNumberStringColor.get());
        break;
      case NONE:
        break;
    }
    return result;
  }

  Node createSub(Node left, Node right) {
    Node result = IR.sub(left, right);
    setJSTypeOrColor(type(JSTypeNative.NUMBER_TYPE, StandardColors.NUMBER), result);
    return result;
  }

  Node createInc(Node operand, boolean isPost) {
    Node result = IR.inc(operand, isPost);
    setJSTypeOrColor(type(JSTypeNative.NUMBER_TYPE, StandardColors.NUMBER), result);
    return result;
  }

  Node createLessThan(Node left, Node right) {
    Node result = IR.lt(left, right);
    setJSTypeOrColor(type(JSTypeNative.BOOLEAN_TYPE, StandardColors.BOOLEAN), result);
    return result;
  }

  Node createBitwiseAnd(Node left, Node right) {
    Node result = IR.bitwiseAnd(left, right);
    setJSTypeOrColor(type(JSTypeNative.NUMBER_TYPE, StandardColors.NUMBER), result);
    return result;
  }

  Node createRightShift(Node left, Node right) {
    Node result = IR.rightShift(left, right);
    setJSTypeOrColor(type(JSTypeNative.NUMBER_TYPE, StandardColors.NUMBER), result);
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
  Node createObjectDotAssignCall(StaticScope scope, Type returnType, Node... args) {
    Node objAssign = createQName(scope, "Object", "assign");
    Node result = createCall(objAssign, returnType, args);

    switch (this.typeMode) {
      case JSTYPE:
        // Make a unique function type that returns the exact type we've inferred it to be.
        // Object.assign in the externs just returns !Object, which loses type information.
        JSType returnJSType = returnType.getJSType(registry);
        JSType objAssignType =
            registry.createFunctionTypeWithVarArgs(
                returnJSType,
                registry.getNativeType(JSTypeNative.OBJECT_TYPE),
                registry.createUnionType(JSTypeNative.OBJECT_TYPE, JSTypeNative.NULL_TYPE));
        objAssign.setJSType(objAssignType);
        break;
      case COLOR:
      case NONE:
        break;
    }

    return result;
  }

  Node createNewNode(Node target, Node... args) {
    Node result = IR.newNode(target, args);
    switch (this.typeMode) {
      case JSTYPE:
        JSType instanceType = target.getJSType();
        if (instanceType.isFunctionType()) {
          instanceType = instanceType.toMaybeFunctionType().getInstanceType();
        } else {
          instanceType = getNativeType(JSTypeNative.UNKNOWN_TYPE);
        }
        result.setJSType(instanceType);
        break;
      case COLOR:
        result.setColor(getInstanceOfColor(target.getColor()));
        break;
      case NONE:
        break;
    }
    return result;
  }

  private Color getInstanceOfColor(Color color) {
    ImmutableSet<Color> possibleInstanceColors = color.getInstanceColors();
    return possibleInstanceColors.isEmpty()
        ? StandardColors.UNKNOWN
        : Color.createUnion(possibleInstanceColors);
  }

  /**
   * Create a call that returns an instance of the given class type.
   *
   * <p>This method is intended for use in special cases, such as calling `super()` in a
   * constructor.
   */
  Node createConstructorCall(Type classType, Node callee, Node... args) {
    Node result = NodeUtil.newCallNode(callee, args);
    switch (this.typeMode) {
      case JSTYPE:
        JSType classJSType = classType.getJSType(registry);
        FunctionType constructorType = checkNotNull(classJSType.toMaybeFunctionType());
        ObjectType instanceType = checkNotNull(constructorType.getInstanceType());
        result.setJSType(instanceType);
        break;
      case COLOR:
        result.setColor(getInstanceOfColor(classType.getColor(colorRegistry)));
        break;
      case NONE:
        break;
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
    setJSTypeOrColor(type(rhs), result);
    return result;
  }

  /** Creates an assignment expression `lhs = rhs` */
  Node createAssign(String lhsName, Node rhs) {
    Node name = createName(lhsName, type(rhs));
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
    result.setQuotedStringKey();
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
    FunctionType functionType =
        isAddingTypes() ? registry.createFunctionType(returnType).toMaybeFunctionType() : null;
    return createFunction(
        name, IR.paramList(), body, type(functionType, StandardColors.TOP_OBJECT));
  }

  Node createZeroArgGeneratorFunction(String name, Node body, @Nullable JSType returnType) {
    Node result = createZeroArgFunction(name, body, returnType);
    result.setIsGeneratorFunction(true);
    return result;
  }

  Node createZeroArgArrowFunctionForExpression(Node expression) {
    Node result = IR.arrowFunction(IR.name(""), IR.paramList(), expression);
    switch (this.typeMode) {
      case JSTYPE:
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
        break;
      case COLOR:
        result.setColor(StandardColors.TOP_OBJECT);
        break;
      case NONE:
        break;
    }
    return result;
  }

  Node createMemberFunctionDef(String name, Node function) {
    // A function used for a member function definition must have an empty name,
    // because the name string goes on the MEMBER_FUNCTION_DEF node.
    checkArgument(function.getFirstChild().getString().isEmpty(), function);
    Node result = IR.memberFunctionDef(name, function);
    setJSTypeOrColor(type(function), result);
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
    Node result = IR.hook(condition, expr1, expr2);
    switch (this.typeMode) {
      case JSTYPE:
        result.setJSType(registry.createUnionType(expr1.getJSType(), expr2.getJSType()));
        break;
      case COLOR:
        result.setColor(Color.createUnion(ImmutableSet.of(expr1.getColor(), expr2.getColor())));
        break;
      case NONE:
        break;
    }

    return result;
  }

  Node createArraylit(Node... elements) {
    return createArraylit(Arrays.asList(elements));
  }

  Node createArraylit(Iterable<Node> elements) {
    Node result = IR.arraylit(elements);
    switch (this.typeMode) {
      case JSTYPE:
        result.setJSType(
            registry.createTemplatizedType(
                registry.getNativeObjectType(JSTypeNative.ARRAY_TYPE),
                // TODO(nickreid): Use a reasonable template type. Remeber to consider SPREAD.
                getNativeType(JSTypeNative.UNKNOWN_TYPE)));
        break;
      case COLOR:
        result.setColor(colorRegistry.get(StandardColors.ARRAY_ID));
        break;
      case NONE:
        break;
    }
    return result;
  }

  Node createJSCompMakeIteratorCall(Node iterable, StaticScope scope) {
    Node makeIteratorName = createQName(scope, "$jscomp.makeIterator");
    final Type type;
    switch (this.typeMode) {
      case JSTYPE:
        // Since createCall (currently) doesn't handle templated functions, fill in the template
        // types
        // of makeIteratorName manually.
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
        makeIteratorName.setJSType(
            replaceTemplate(makeIteratorType, ImmutableList.of(iterableType)));
        type = type(makeIteratorName.getJSType().assertFunctionType().getReturnType());
        break;
      case COLOR:
        type = type(colorRegistry.get(StandardColors.ITERATOR_ID));
        break;
      case NONE:
        type = noTypeInformation();
        break;
      default:
        throw new AssertionError();
    }
    return createCall(makeIteratorName, type, iterable);
  }

  Node createJscompArrayFromIteratorCall(Node iterator, StaticScope scope) {
    Node makeIteratorName = createQName(scope, "$jscomp.arrayFromIterator");

    final Type resultType;
    switch (this.typeMode) {
      case JSTYPE:
        // Since createCall (currently) doesn't handle templated functions, fill in the template
        // types of makeIteratorName manually.
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
        makeIteratorName.setJSType(
            replaceTemplate(makeIteratorType, ImmutableList.of(iterableType)));
        resultType = type(makeIteratorName.getJSType().assertFunctionType().getReturnType());
        break;
      case COLOR:
        // colors don't include generics, so just set the return type to Array.
        resultType = type(colorRegistry.get(StandardColors.ARRAY_ID));
        break;
      case NONE:
        resultType = noTypeInformation();
        break;
      default:
        throw new AssertionError();
    }
    return createCall(makeIteratorName, resultType, iterator);
  }

  Node createJscompArrayFromIterableCall(Node iterable, StaticScope scope) {
    // TODO(b/193800507): consider making this verify the arrayFromIterable $jscomp runtime library
    // is injected.
    Node makeIterableName = createQName(scope, "$jscomp.arrayFromIterable");

    final Type resultType;
    switch (this.typeMode) {
      case JSTYPE:
        // Since createCall (currently) doesn't handle templated functions, fill in the template
        // types of makeIteratorName manually.
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
        makeIterableName.setJSType(
            replaceTemplate(makeIterableType, ImmutableList.of(iterableType)));
        resultType = type(makeIterableName.getJSType().assertFunctionType().getReturnType());
        break;
      case COLOR:
        // colors don't include generics, so just set the return type to Array.
        resultType = type(colorRegistry.get(StandardColors.ARRAY_ID));
        break;
      case NONE:
        resultType = noTypeInformation();
        break;
      default:
        throw new AssertionError();
    }
    return createCall(makeIterableName, resultType, iterable);
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
    Node makeIteratorAsyncName = createQName(scope, "$jscomp.makeAsyncIterator");
    final Type resultType;
    switch (this.typeMode) {
      case JSTYPE:
        // Since createCall (currently) doesn't handle templated functions, fill in the template
        // types of makeIteratorName manually.
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
        resultType = type(makeIteratorAsyncName.getJSType().assertFunctionType().getReturnType());
        break;
      case COLOR:
        resultType = type(colorRegistry.get(StandardColors.ASYNC_ITERATOR_ITERABLE_ID));
        break;
      case NONE:
        resultType = noTypeInformation();
        break;
      default:
        throw new AssertionError();
    }
    return createCall(makeIteratorAsyncName, resultType, iterable);
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
   * Creates an empty generator function with the correct return type to be an argument to
   * $jscomp.AsyncGeneratorWrapper.
   *
   * @param asyncGeneratorWrapperType the specific type of the $jscomp.AsyncGeneratorWrapper with
   *     its template filled in. Should be the type on the node returned from
   *     createAsyncGeneratorWrapperReference.
   */
  Node createEmptyAsyncGeneratorWrapperArgument(JSType asyncGeneratorWrapperType) {
    Type generatorType = noTypeInformation();

    if (isAddingTypes()) {
      if (asyncGeneratorWrapperType.isUnknownType()) {
        // Not injecting libraries?
        generatorType =
            type(
                registry.createFunctionType(
                    replaceTemplate(
                        getNativeType(JSTypeNative.GENERATOR_TYPE),
                        ImmutableList.of(unknownType))));
      } else {
        // Generator<$jscomp.AsyncGeneratorWrapper$ActionRecord<number>>
        JSType innerFunctionReturnType =
            Iterables.getOnlyElement(
                    asyncGeneratorWrapperType.toMaybeFunctionType().getParameters())
                .getJSType();
        generatorType = type(registry.createFunctionType(innerFunctionReturnType));
      }
    } else if (isAddingColors()) {
      // colors don't model function types, so it's fine to fallback to the top object.
      generatorType = type(StandardColors.TOP_OBJECT);
    }

    return createEmptyGeneratorFunction(generatorType);
  }

  Node createJscompAsyncExecutePromiseGeneratorFunctionCall(
      StaticScope scope, Node generatorFunction) {
    Node jscompDotAsyncExecutePromiseGeneratorFunction =
        createQName(scope, "$jscomp.asyncExecutePromiseGeneratorFunction");
    Type resultType = type(unknownType);
    if (isAddingTypes()) {
      // TODO(bradfordcsmith): Maybe update the type to be more specific
      // Currently this method expects `function(): !Generator<?>` and returns `Promise<?>`.
      // Since we propagate type information only if type checking has already run,
      // these unknowns probably don't matter, but we should be able to be more specific with the
      // return type at least.
      resultType =
          type(
              jscompDotAsyncExecutePromiseGeneratorFunction
                  .getJSType()
                  .assertFunctionType()
                  .getReturnType());
    } else if (isAddingColors()) {
      resultType = type(colorRegistry.get(StandardColors.PROMISE_ID));
    }
    return createCall(jscompDotAsyncExecutePromiseGeneratorFunction, resultType, generatorFunction);
  }

  private JSType getNativeType(JSTypeNative nativeType) {
    checkNotNull(registry, "registry is null");
    return checkNotNull(
        registry.getNativeType(nativeType), "native type not found: %s", nativeType);
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
        result.setColor(type.getColor(colorRegistry));
        break;
      case NONE:
        break;
    }
  }

  interface Type {
    JSType getJSType(JSTypeRegistry registry);

    Color getColor(ColorRegistry registry);
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
    public Color getColor(ColorRegistry registry) {
      return checkNotNull(this.n.getColor(), n);
    }
  }

  private static final class JSTypeOrColor implements Type {
    private final @Nullable JSType jstype;
    private final @Nullable JSTypeNative jstypeNative;
    private final @Nullable Color color;
    private final @Nullable ColorId colorId;

    JSTypeOrColor(@Nullable JSTypeNative jstypeNative, ColorId colorId) {
      this.jstypeNative = jstypeNative;
      this.jstype = null;
      this.color = null;
      this.colorId = colorId;
    }

    JSTypeOrColor(JSTypeNative jstypeNative, @Nullable Color color) {
      this.jstypeNative = jstypeNative;
      this.jstype = null;
      this.color = color;
      this.colorId = null;
    }

    JSTypeOrColor(@Nullable JSType jstype, @Nullable Color color) {
      this.jstype = jstype;
      this.jstypeNative = null;
      this.color = color;
      this.colorId = null;
    }

    @Override
    public JSType getJSType(JSTypeRegistry registry) {
      return this.jstype != null ? this.jstype : registry.getNativeType(jstypeNative);
    }

    @Override
    public Color getColor(ColorRegistry registry) {
      return this.color != null ? this.color : registry.get(checkNotNull(this.colorId));
    }
  }

  /** Uses the JSType or Color of the given node as a template for adding type information */
  static Type type(Node node) {
    return new TypeOnNode(node);
  }

  static Type type(JSType type) {
    return new JSTypeOrColor(type, null);
  }

  static Type type(JSTypeNative type) {
    return new JSTypeOrColor(type, (Color) null);
  }

  static Type type(Color type) {
    return new JSTypeOrColor((JSType) null, type);
  }

  static Type type(ColorId type) {
    return new JSTypeOrColor(null, type);
  }

  static Type type(JSType type, Color color) {
    return new JSTypeOrColor(type, color);
  }

  static Type type(JSTypeNative type, Color color) {
    return new JSTypeOrColor(type, color);
  }

  private static Type noTypeInformation() {
    return new JSTypeOrColor((JSType) null, null);
  }
}
