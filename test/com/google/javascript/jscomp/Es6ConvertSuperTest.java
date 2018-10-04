/*
 * Copyright 2014 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.Es6ToEs3Util.CANNOT_CONVERT_YET;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test cases for a transpilation pass that rewrites most usages of `super` syntax.
 *
 * <p>The covered rewrites are:
 *
 * <ul>
 *   <li>`super.method` accesses and calls
 *   <li>`super['prop']` accesses and calls
 *   <li>adding constructor definitions (with `super()` calls if needed) to classes that omit them
 *   <li>stripping `super()` calls from constructors of externs classes and interfaces (i.e stubs)
 * </ul>
 */
@RunWith(JUnit4.class)
public final class Es6ConvertSuperTest extends CompilerTestCase {

  public Es6ConvertSuperTest() {
    super(MINIMAL_EXTERNS);
  }

  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler();
  }

  @Override
  protected NoninjectingCompiler getLastCompiler() {
    return (NoninjectingCompiler) super.getLastCompiler();
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2016);
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    enableTypeCheck();
    enableTypeInfoValidation();
    enableScriptFeatureValidation();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new Es6ConvertSuper(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  // Instance `super` resolution

  @Test
  public void testCallingSuperInstanceProperty() {
    test(
        externs(
            lines(
                "class A {",
                "  constructor() { }",
                "",
                "  /** @param {number} x */",
                "  g(x) { }",
                "}")),
        srcs(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  f() { super.g(3); }",
                "}")),
        expected(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  f() { A.prototype.g.call(this, 3); }",
                "}")));

    // get types we need to check
    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    ObjectType classAInstanceType = registry.getGlobalType("A").toObjectType();
    ObjectType classAPrototypeType = classAInstanceType.getImplicitPrototype();
    FunctionType aDotGMethodType = classAPrototypeType.getPropertyType("g").toMaybeFunctionType();
    JSType aDotGDotCallType =
        aDotGMethodType // A.g property type
            .getPropertyType("call");

    JSType classBInstanceType = registry.getGlobalType("B");

    // A.prototype.g.call(this, 3)
    Node callNode =
        getLastCompiler()
            .getJsRoot() // root
            .getFirstChild() // script
            .getFirstChild() // class B
            .getLastChild() // class B's body
            .getLastChild() // f
            .getOnlyChild() // function that implements f() {}
            .getLastChild() // method body
            .getFirstChild() // statement `A.prototype.g.call(this, 3);`
            .getOnlyChild(); // CALL node within the statement
    assertNode(callNode)
        .hasToken(Token.CALL)
        .hasLineno(4) // position and length of `super.g(3)`
        .hasCharno(8)
        .hasLength(10);
    assertType(callNode.getJSType()).isEqualTo(aDotGMethodType.getReturnType());

    // A.prototype.g.call
    Node callee = callNode.getFirstChild();
    assertNode(callee)
        .matchesQualifiedName("A.prototype.g.call")
        .hasLineno(4) // position and length of `super.g`
        .hasCharno(8)
        .hasLength(7);
    assertType(callee.getJSType()).isEqualTo(aDotGDotCallType);

    // A.prototype.g
    Node superDotGReplacement = callee.getFirstChild();
    assertNode(superDotGReplacement)
        .matchesQualifiedName("A.prototype.g")
        .hasLineno(4) // position and length of `super.g`
        .hasCharno(8)
        .hasLength(7);
    assertType(superDotGReplacement.getJSType()).isEqualTo(aDotGMethodType);

    // A.prototype
    Node superReplacement = superDotGReplacement.getFirstChild();
    assertNode(superReplacement)
        .matchesQualifiedName("A.prototype")
        .hasLineno(4) // position and length of `super`
        .hasCharno(8)
        .hasLength(5)
        .hasOriginalName("super");
    assertType(superReplacement.getJSType()).isEqualTo(classAPrototypeType);

    // `this` node from `A.prototype.g.call(this, 3)`
    Node thisNode = callee.getNext();
    assertNode(thisNode)
        .hasToken(Token.THIS)
        .hasLineno(4) // position and length of `super.g`
        .hasCharno(8)
        .hasLength(7)
        .isIndexable(false); // there's no direct correlation with text in the original source
    assertType(thisNode.getJSType()).isEqualTo(classBInstanceType);
  }

  @Test
  public void testCallingSuperInstanceElement() {
    test(
        externs(
            lines(
                "/** @dict */",
                "class A {",
                "  constructor() { }",
                "",
                "  /** @param {number} x */",
                "  ['g'](x) { };",
                "}")),
        srcs(
            lines(
                "/** @dict */",
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  ['f']() { super['g'](4); }",
                "}")),
        expected(
            lines(
                "/** @dict */",
                "class B extends A {",
                "  constructor() {",
                "    super();",
                "  }",
                "",
                "  ['f']() { A.prototype['g'].call(this, 4); }",
                "}")));

    // get types we need to check
    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    ObjectType classAInstanceType = registry.getGlobalType("A").toObjectType();
    ObjectType classAPrototypeType = classAInstanceType.getImplicitPrototype();
    JSType unknownType = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);

    JSType classBInstanceType = registry.getGlobalType("B");

    // A.prototype['g'].call(this, 4)
    Node callNode =
        getLastCompiler()
            .getJsRoot() // root
            .getFirstChild() // script
            .getFirstChild() // class B
            .getLastChild() // class B's body
            .getLastChild() // ['f']() { ... }
            .getSecondChild() // () { ... }
            .getLastChild() // { ... }
            .getFirstChild() // statement `A.prototype['g'].call(this, 4);`
            .getOnlyChild(); // CALL node within the statement
    assertNode(callNode)
        .hasToken(Token.CALL)
        .hasLineno(5) // position and length of `super['g'](4)`
        .hasCharno(12)
        .hasLength(13);
    // computed property prevents doing any better than unknownType
    assertType(callNode.getJSType()).isEqualTo(unknownType);

    // A.prototype['g'].call
    Node callee = callNode.getFirstChild();
    assertNode(callee)
        .hasToken(Token.GETPROP)
        .hasLineno(5) // position and length of `super['g']`
        .hasCharno(12)
        .hasLength(10);
    assertType(callee.getJSType()).isEqualTo(unknownType);

    // A.prototype['g']
    Node superGetelemReplacement = callee.getFirstChild();
    assertNode(superGetelemReplacement)
        .hasToken(Token.GETELEM)
        .hasLineno(5) // position and length of `super['g']`
        .hasCharno(12)
        .hasLength(10);
    // computed property prevents doing any better than unknownType
    assertType(superGetelemReplacement.getJSType()).isEqualTo(unknownType);

    // A.prototype
    Node superReplacement = superGetelemReplacement.getFirstChild();
    assertNode(superReplacement)
        .matchesQualifiedName("A.prototype")
        .hasLineno(5) // position and length of `super`
        .hasCharno(12)
        .hasLength(5)
        .hasOriginalName("super");
    assertType(superReplacement.getJSType()).isEqualTo(classAPrototypeType);

    // `this` node from `A.prototype['g'].call(this, 3)`
    Node thisNode = callee.getNext();
    assertNode(thisNode)
        .hasToken(Token.THIS)
        .hasLineno(5) // position and length of `super['g']`
        .hasCharno(12)
        .hasLength(10)
        .isIndexable(false); // there's no direct correlation with text in the original source
    assertType(thisNode.getJSType()).isEqualTo(classBInstanceType);
  }

  @Test
  public void testAccessingSuperInstanceProperty() {
    test(
        externs(
            lines(
                "class A {",
                "  constructor() { }",
                "",
                "  /** @param {number} x */",
                "  g(x) { }",
                "}")),
        srcs(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  f() { var t = super.g; }",
                "}")),
        expected(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  f() { var t = A.prototype.g; }",
                "}")));

    // get types we need to check
    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    ObjectType classAInstanceType = registry.getGlobalType("A").toObjectType();
    ObjectType classAPrototypeType = classAInstanceType.getImplicitPrototype();
    FunctionType aDotGMethodType = classAPrototypeType.getPropertyType("g").toMaybeFunctionType();

    // A.prototype.g
    Node superDotGReplacement =
        getLastCompiler()
            .getJsRoot() // root
            .getFirstChild() // script
            .getFirstChild() // class B
            .getLastChild() // class B's body
            .getLastChild() // f
            .getOnlyChild() // function that implements f() {}
            .getLastChild() // method body
            .getFirstChild() // `var t = A.prototype.g;`
            .getFirstChild() // `t = A.prototype.g`
            .getOnlyChild(); // A.prototype.g
    assertNode(superDotGReplacement)
        .matchesQualifiedName("A.prototype.g")
        .hasLineno(4) // position and length of `super.g`
        .hasCharno(16)
        .hasLength(7);
    assertType(superDotGReplacement.getJSType()).isEqualTo(aDotGMethodType);

    // A.prototype
    Node superReplacement = superDotGReplacement.getFirstChild();
    assertNode(superReplacement)
        .matchesQualifiedName("A.prototype")
        .hasLineno(4) // position and length of `super`
        .hasCharno(16)
        .hasLength(5)
        .hasOriginalName("super");
    assertType(superReplacement.getJSType()).isEqualTo(classAPrototypeType);
  }

  @Test
  public void testAccessingSuperInstanceElement() {
    test(
        externs(
            lines(
                "/** @dict */",
                "class A {",
                "  constructor() { }",
                "",
                "  /** @param {number} x */",
                "  ['g'](x) { };",
                "}")),
        srcs(
            lines(
                "/** @dict */",
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  ['f']() { var t = super['g']; }",
                "}")),
        expected(
            lines(
                "/** @dict */",
                "class B extends A {",
                "  constructor() {",
                "    super();",
                "  }",
                "",
                "  ['f']() { var t = A.prototype['g']; }",
                "}")));

    // get types we need to check
    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    ObjectType classAInstanceType = registry.getGlobalType("A").toObjectType();
    ObjectType classAPrototypeType = classAInstanceType.getImplicitPrototype();
    JSType unknownType = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);

    // A.prototype['g']
    Node superGetelemReplacement =
        getLastCompiler()
            .getJsRoot() // root
            .getFirstChild() // script
            .getFirstChild() // class B
            .getLastChild() // class B's body
            .getLastChild() // ['f']
            .getSecondChild() // () { ... }
            .getLastChild() // { ... }
            .getFirstChild() // `var t = A.prototype['g'];`
            .getFirstChild() // `t = A.prototype['g']`
            .getOnlyChild(); // A.prototype['g']
    assertNode(superGetelemReplacement)
        .hasToken(Token.GETELEM)
        .hasLineno(5) // position and length of `super['g']`
        .hasCharno(20)
        .hasLength(10);
    // getelem prevents us doing any better than unknown type
    assertType(superGetelemReplacement.getJSType()).isEqualTo(unknownType);

    // A.prototype
    Node superReplacement = superGetelemReplacement.getFirstChild();
    assertNode(superReplacement)
        .matchesQualifiedName("A.prototype")
        .hasLineno(5) // position and length of `super`
        .hasCharno(20)
        .hasLength(5)
        .hasOriginalName("super");
    assertType(superReplacement.getJSType()).isEqualTo(classAPrototypeType);
  }

  @Test
  public void testCannotAssignToSuperInstanceProperty() {
    testError(
        lines(
            "class A {",
            "  constructor() { }",
            "",
            "  /** @param {number} x */",
            "  g(x) { }",
            "}",
            "",
            "class B extends A {",
            "  constructor() { super(); }",
            "",
            "  f() { super.g = 5; }",
            "}"),
        CANNOT_CONVERT_YET);
  }

  @Test
  public void testCannotAssignToSuperInstanceElement() {
    testError(
        lines(
            "/** @dict */",
            "class A {",
            "  constructor() { }",
            "",
            "  /** @param {number} x */",
            "  ['g'](x) { }",
            "}",
            "",
            "class B extends A {",
            "  constructor() { super(); }",
            "",
            "  ['f']() { super['g'] = 5; }",
            "}"),
        CANNOT_CONVERT_YET);
  }

  // Static `super` resolution

  @Test
  public void testCallingSuperStaticProperty() {
    test(
        externs(
            lines(
                "class A {",
                "  constructor() { }",
                "",
                "  /** @param {number} x */",
                "  static g(x) { }",
                "}")),
        srcs(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  static f() { super.g(3); }",
                "}")),
        expected(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  static f() { A.g.call(this, 3); }",
                "}")));

    // get types we need to check
    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    FunctionType classAType =
        registry
            .getGlobalType("A")
            .toObjectType() // instance type for A
            .getConstructor();
    FunctionType aDotGMethodType = classAType.getPropertyType("g").toMaybeFunctionType();
    JSType aDotGDotCallType = aDotGMethodType.getPropertyType("call");

    FunctionType classBType =
        registry
            .getGlobalType("B")
            .toObjectType() // instance type for B
            .getConstructor();

    // A.g.call(this, 3)
    Node callNode =
        getLastCompiler()
            .getJsRoot() // root
            .getFirstChild() // script
            .getFirstChild() // class B
            .getLastChild() // class B's body
            .getLastChild() // static f
            .getOnlyChild() // function that implements static f() {}
            .getLastChild() // method body
            .getFirstChild() // `A.g.call(this, 3);`
            .getOnlyChild(); // CALL node within the statement
    assertNode(callNode)
        .hasToken(Token.CALL)
        .hasLineno(4) // position and length of `super.g(3)`
        .hasCharno(15)
        .hasLength(10);
    assertType(callNode.getJSType()).isEqualTo(aDotGMethodType.getReturnType());

    // A.g.call
    Node callee = callNode.getFirstChild();
    assertNode(callee)
        .matchesQualifiedName("A.g.call")
        .hasLineno(4) // position and length of `super.g`
        .hasCharno(15)
        .hasLength(7);
    assertType(callee.getJSType()).isEqualTo(aDotGDotCallType);

    // A.g
    Node superDotGReplacement = callee.getFirstChild();
    assertNode(superDotGReplacement)
        .matchesQualifiedName("A.g")
        .hasLineno(4) // position and length of `super.g`
        .hasCharno(15)
        .hasLength(7);
    assertType(superDotGReplacement.getJSType()).isEqualTo(aDotGMethodType);

    // A
    Node superReplacement = superDotGReplacement.getFirstChild();
    assertNode(superReplacement)
        .matchesQualifiedName("A")
        .hasLineno(4) // position and length of `super`
        .hasCharno(15)
        .hasLength(5)
        .hasOriginalName("super");
    assertType(superReplacement.getJSType()).isEqualTo(classAType);

    Node thisNode = callee.getNext();
    assertNode(thisNode).hasType(Token.THIS);
    assertType(thisNode.getJSType()).isEqualTo(classBType);
  }

  @Test
  public void testCallingSuperStaticElement() {
    test(
        externs(
            lines(
                "/** @dict */",
                "class A {",
                "  constructor() { }",
                "",
                "  /** @param {number} x */",
                "  static ['g'](x) { };",
                "}")),
        srcs(
            lines(
                "/** @dict */",
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  static ['f']() { super['g'](4); }",
                "}")),
        expected(
            lines(
                "/** @dict */",
                "class B extends A {",
                "  constructor() {",
                "    super();",
                "  }",
                "",
                "  static ['f']() { A['g'].call(this, 4); }",
                "}")));

    // get types we need to check
    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    ObjectType classAInstanceType = registry.getGlobalType("A").toObjectType();
    FunctionType classAType = classAInstanceType.getConstructor();
    JSType unknownType = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);

    JSType classBType = registry.getGlobalType("B").toObjectType().getConstructor();

    // A['g'].call(this, 4)
    Node callNode =
        getLastCompiler()
            .getJsRoot() // root
            .getFirstChild() // script
            .getFirstChild() // class B
            .getLastChild() // class B's body
            .getLastChild() // static ['f']() { ... }
            .getSecondChild() // () { ... }
            .getLastChild() // { ... }
            .getFirstChild() // statement `A['g'].call(this, 4);`
            .getOnlyChild(); // CALL node within the statement
    assertNode(callNode)
        .hasToken(Token.CALL)
        .hasLineno(5) // position and length of `super['g'](4)`
        .hasCharno(19)
        .hasLength(13);
    // computed property prevents doing any better than unknownType
    assertType(callNode.getJSType()).isEqualTo(unknownType);

    // A['g'].call
    Node callee = callNode.getFirstChild();
    assertNode(callee)
        .hasToken(Token.GETPROP)
        .hasLineno(5) // position and length of `super['g']`
        .hasCharno(19)
        .hasLength(10);
    assertType(callee.getJSType()).isEqualTo(unknownType);

    // A['g']
    Node superGetelemReplacement = callee.getFirstChild();
    assertNode(superGetelemReplacement)
        .hasToken(Token.GETELEM)
        .hasLineno(5) // position and length of `super['g']`
        .hasCharno(19)
        .hasLength(10);
    // computed property prevents doing any better than unknownType
    assertType(superGetelemReplacement.getJSType()).isEqualTo(unknownType);

    // A
    Node superReplacement = superGetelemReplacement.getFirstChild();
    assertNode(superReplacement)
        .matchesQualifiedName("A")
        .hasLineno(5) // position and length of `super`
        .hasCharno(19)
        .hasLength(5)
        .hasOriginalName("super");
    assertType(superReplacement.getJSType()).isEqualTo(classAType);

    // `this` node from `A['g'].call(this, 3)`
    Node thisNode = callee.getNext();
    assertNode(thisNode)
        .hasToken(Token.THIS)
        .hasLineno(5) // position and length of `super['g']`
        .hasCharno(19)
        .hasLength(10)
        .isIndexable(false); // there's no direct correlation with text in the original source
    assertType(thisNode.getJSType()).isEqualTo(classBType);
  }

  @Test
  public void testAccessingSuperStaticProperty() {
    test(
        externs(
            lines(
                "class A {",
                "  constructor() { }",
                "",
                "  /** @param {number} x */",
                "  static g(x) { }",
                "}")),
        srcs(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  static f() { var t = super.g; }",
                "}")),
        expected(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  static f() { var t = A.g; }",
                "}")));

    // get types we need to check
    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    FunctionType classAType = registry.getGlobalType("A").toObjectType().getConstructor();
    JSType aDotGMethodType = classAType.getPropertyType("g");

    // A.prototype.g
    Node superDotGReplacement =
        getLastCompiler()
            .getJsRoot() // root
            .getFirstChild() // script
            .getFirstChild() // class B
            .getLastChild() // class B's body
            .getLastChild() // f
            .getOnlyChild() // function that implements f() {}
            .getLastChild() // method body
            .getFirstChild() // `var t = A.g;`
            .getFirstChild() // `t = A.g`
            .getOnlyChild(); // A.g
    assertNode(superDotGReplacement)
        .matchesQualifiedName("A.g")
        .hasLineno(4) // position and length of `super.g`
        .hasCharno(23)
        .hasLength(7);
    assertType(superDotGReplacement.getJSType()).isEqualTo(aDotGMethodType);

    // A.prototype
    Node superReplacement = superDotGReplacement.getFirstChild();
    assertNode(superReplacement)
        .matchesQualifiedName("A")
        .hasLineno(4) // position and length of `super`
        .hasCharno(23)
        .hasLength(5)
        .hasOriginalName("super");
    assertType(superReplacement.getJSType()).isEqualTo(classAType);
  }

  @Test
  public void testAccessingSuperStaticElement() {
    test(
        externs(
            lines(
                "/** @dict */",
                "class A {",
                "  constructor() { }",
                "",
                "  /** @param {number} x */",
                "  static ['g'](x) { };",
                "}")),
        srcs(
            lines(
                "/** @dict */",
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  static ['f']() { var t = super['g']; }",
                "}")),
        expected(
            lines(
                "/** @dict */",
                "class B extends A {",
                "  constructor() {",
                "    super();",
                "  }",
                "",
                "  static ['f']() { var t = A['g']; }",
                "}")));

    // get types we need to check
    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    FunctionType classAType = registry.getGlobalType("A").toObjectType().getConstructor();
    JSType unknownType = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);

    // A.prototype.g
    Node superGetElemReplacement =
        getLastCompiler()
            .getJsRoot() // root
            .getFirstChild() // script
            .getFirstChild() // class B
            .getLastChild() // class B's body
            .getLastChild() // ['f']() { ... }
            .getSecondChild() // () { ... }
            .getLastChild() // { ... }
            .getFirstChild() // `var t = A['g'];`
            .getFirstChild() // `t = A['g']`
            .getOnlyChild(); // `A['g']`
    assertNode(superGetElemReplacement)
        .hasToken(Token.GETELEM)
        .hasLineno(5) // position and length of `super['g']`
        .hasCharno(27)
        .hasLength(10);
    // computed property prevents doing any better than unknownType
    assertType(superGetElemReplacement.getJSType()).isEqualTo(unknownType);

    // A.prototype
    Node superReplacement = superGetElemReplacement.getFirstChild();
    assertNode(superReplacement)
        .matchesQualifiedName("A")
        .hasLineno(5) // position and length of `super`
        .hasCharno(27)
        .hasLength(5)
        .hasOriginalName("super");
    assertType(superReplacement.getJSType()).isEqualTo(classAType);
  }

  // Getters and setters

  @Test
  public void testResolvingSuperInGetter() {
    test(
        externs(
            lines(
                "class A {",
                "  constructor() { }",
                "",
                "  /** @param {number} x */",
                "  g(x) { }",
                "}")),
        srcs(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  get f() { super.g(3); }",
                "}")),
        expected(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  get f() { A.prototype.g.call(this, 3); }",
                "}")));

    // get types we need to check
    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    ObjectType classAInstanceType = registry.getGlobalType("A").toObjectType();
    ObjectType classAPrototypeType = classAInstanceType.getImplicitPrototype();
    FunctionType aDotGMethodType = classAPrototypeType.getPropertyType("g").toMaybeFunctionType();
    JSType aDotGDotCallType =
        aDotGMethodType // A.g property type
            .getPropertyType("call");

    JSType classBInstanceType = registry.getGlobalType("B");

    // A.prototype.g.call(this, 3)
    Node callNode =
        getLastCompiler()
            .getJsRoot() // root
            .getFirstChild() // script
            .getFirstChild() // class B
            .getLastChild() // class B's body
            .getLastChild() // get f() { ... }
            .getOnlyChild() // () { ... }
            .getLastChild() // { ... }
            .getFirstChild() // statement `A.prototype.g.call(this, 3);`
            .getOnlyChild(); // CALL node within the statement
    assertNode(callNode)
        .hasToken(Token.CALL)
        .hasLineno(4) // position and length of `super.g(3)`
        .hasCharno(12)
        .hasLength(10);
    assertType(callNode.getJSType()).isEqualTo(aDotGMethodType.getReturnType());

    // A.prototype.g.call
    Node callee = callNode.getFirstChild();
    assertNode(callee)
        .matchesQualifiedName("A.prototype.g.call")
        .hasLineno(4) // position and length of `super.g`
        .hasCharno(12)
        .hasLength(7);
    assertType(callee.getJSType()).isEqualTo(aDotGDotCallType);

    // A.prototype.g
    Node superDotGReplacement = callee.getFirstChild();
    assertNode(superDotGReplacement)
        .matchesQualifiedName("A.prototype.g")
        .hasLineno(4) // position and length of `super.g`
        .hasCharno(12)
        .hasLength(7);
    assertType(superDotGReplacement.getJSType()).isEqualTo(aDotGMethodType);

    // A.prototype
    Node superReplacement = superDotGReplacement.getFirstChild();
    assertNode(superReplacement)
        .matchesQualifiedName("A.prototype")
        .hasLineno(4) // position and length of `super`
        .hasCharno(12)
        .hasLength(5)
        .hasOriginalName("super");
    assertType(superReplacement.getJSType()).isEqualTo(classAPrototypeType);

    // `this` node from `A.prototype.g.call(this, 3)`
    Node thisNode = callee.getNext();
    assertNode(thisNode)
        .hasToken(Token.THIS)
        .hasLineno(4) // position and length of `super.g`
        .hasCharno(12)
        .hasLength(7)
        .isIndexable(false); // there's no direct correlation with text in the original source
    assertType(thisNode.getJSType()).isEqualTo(classBInstanceType);
  }

  @Test
  public void testResolvingSuperInSetter() {
    test(
        externs(
            lines(
                "class A {",
                "  constructor() { }",
                "",
                "  /** @param {number} x */",
                "  g(x) { }",
                "}")),
        srcs(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  /** @param {number} x */",
                "  set f(x) { super.g(x); }",
                "}")),
        expected(
            lines(
                "class B extends A {",
                "  constructor() { super(); }",
                "",
                "  /** @param {number} x */",
                "  set f(x) { A.prototype.g.call(this, x); }",
                "}")));

    // get types we need to check
    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    ObjectType classAInstanceType = registry.getGlobalType("A").toObjectType();
    ObjectType classAPrototypeType = classAInstanceType.getImplicitPrototype();
    FunctionType aDotGMethodType = classAPrototypeType.getPropertyType("g").toMaybeFunctionType();
    JSType aDotGDotCallType =
        aDotGMethodType // A.g property type
            .getPropertyType("call");

    JSType classBInstanceType = registry.getGlobalType("B");

    // A.prototype.g.call(this, x)
    Node callNode =
        getLastCompiler()
            .getJsRoot() // root
            .getFirstChild() // script
            .getFirstChild() // class B
            .getLastChild() // class B's body
            .getLastChild() // set f(x) { ... }
            .getOnlyChild() // (x) { ... }
            .getLastChild() // { ... }
            .getFirstChild() // statement `A.prototype.g.call(this, x);`
            .getOnlyChild(); // CALL node within the statement
    assertNode(callNode)
        .hasToken(Token.CALL)
        .hasLineno(5) // position and length of `super.g(3)`
        .hasCharno(13)
        .hasLength(10);
    assertType(callNode.getJSType()).isEqualTo(aDotGMethodType.getReturnType());

    // A.prototype.g.call
    Node callee = callNode.getFirstChild();
    assertNode(callee)
        .matchesQualifiedName("A.prototype.g.call")
        .hasLineno(5) // position and length of `super.g`
        .hasCharno(13)
        .hasLength(7);
    assertType(callee.getJSType()).isEqualTo(aDotGDotCallType);

    // A.prototype.g
    Node superDotGReplacement = callee.getFirstChild();
    assertNode(superDotGReplacement)
        .matchesQualifiedName("A.prototype.g")
        .hasLineno(5) // position and length of `super.g`
        .hasCharno(13)
        .hasLength(7);
    assertType(superDotGReplacement.getJSType()).isEqualTo(aDotGMethodType);

    // A.prototype
    Node superReplacement = superDotGReplacement.getFirstChild();
    assertNode(superReplacement)
        .matchesQualifiedName("A.prototype")
        .hasLineno(5) // position and length of `super`
        .hasCharno(13)
        .hasLength(5)
        .hasOriginalName("super");
    assertType(superReplacement.getJSType()).isEqualTo(classAPrototypeType);

    // `this` node from `A.prototype.g.call(this, 3)`
    Node thisNode = callee.getNext();
    assertNode(thisNode)
        .hasToken(Token.THIS)
        .hasLineno(5) // position and length of `super.g`
        .hasCharno(13)
        .hasLength(7)
        .isIndexable(false); // there's no direct correlation with text in the original source
    assertType(thisNode.getJSType()).isEqualTo(classBInstanceType);
  }

  // Constructor synthesis

  @Test
  public void testSynthesizingConstructorOfBaseClassInSource() {
    test(
        externs(""),
        srcs(
            lines(
                "class A { }", // Force wrapping.
                "",
                "class B extends A {",
                "  constructor() { super(); }",
                "}")),
        expected(
            lines(
                "class A {",
                "  constructor() { }",
                "}",
                "",
                "class B extends A {",
                "  constructor() { super(); }",
                "}")));

    // get the types we'll need to check
    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    FunctionType classAConstructorType =
        registry.getGlobalType("A").toObjectType().getConstructor();

    // class A { ... }
    Node classANode =
        getLastCompiler()
            .getJsRoot() // root
            .getFirstChild() // script
            .getFirstChild();
    assertType(classANode.getJSType()).isEqualTo(classAConstructorType);

    // constructor() { }
    Node constructorMemberFunctionDefForA =
        classANode
            .getLastChild() // { ... }
            .getFirstChild();
    assertNode(constructorMemberFunctionDefForA)
        .isMemberFunctionDef("constructor")
        .hasLineno(1) // synthetic constructor gets position and length of original class definition
        .hasCharno(0)
        .hasLength(11)
        .isIndexable(false);
    assertType(constructorMemberFunctionDefForA.getJSType()).isEqualTo(classAConstructorType);

    Node constructorFunctionForA = constructorMemberFunctionDefForA.getOnlyChild();
    assertNode(constructorFunctionForA)
        .hasToken(Token.FUNCTION)
        .hasLineno(1) // synthetic constructor gets position and length of original class definition
        .hasCharno(0)
        .hasLength(11)
        .isIndexable(false);
    assertType(constructorFunctionForA.getJSType()).isEqualTo(classAConstructorType);
  }

  @Test
  public void testSynthesizingConstructorOfDerivedClassInSource() {
    test(
        externs(new TestExternsBuilder().addArguments().build()),
        srcs(
            lines(
                "class A {", // Force wrapping.
                "  constructor() { }",
                "}",
                "",
                "class B extends A { }")),
        expected(
            lines(
                "class A {",
                "  constructor() { }",
                "}",
                "",
                "class B extends A {",
                "  /** @param {...?} var_args */",
                "  constructor(var_args) { super(...arguments); }",
                "}")));

    // get the types we'll need to check
    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    FunctionType classAConstructorType =
        registry.getGlobalType("A").toObjectType().getConstructor();
    ObjectType classBInstanceType = registry.getGlobalType("B").toObjectType();
    FunctionType classBConstructorType = classBInstanceType.getConstructor();
    JSType argumentsType = registry.getGlobalType("Arguments");

    // class B extends A { ... }
    Node classBNode =
        getLastCompiler()
            .getJsRoot() // root
            .getFirstChild() // script
            .getSecondChild(); // class A is first, then B
    assertType(classBNode.getJSType()).isEqualTo(classBConstructorType);

    // constructor() { }
    Node constructorMemberFunctionDefForB =
        classBNode
            .getLastChild() // { ... }
            .getFirstChild();
    assertNode(constructorMemberFunctionDefForB)
        .isMemberFunctionDef("constructor")
        .hasLineno(5) // synthetic constructor gets position and length of original class definition
        .hasCharno(0)
        .hasLength(21)
        .isIndexable(false);
    assertType(constructorMemberFunctionDefForB.getJSType()).isEqualTo(classBConstructorType);

    Node constructorFunctionForB = constructorMemberFunctionDefForB.getOnlyChild();
    assertNode(constructorFunctionForB)
        .hasToken(Token.FUNCTION)
        .hasLineno(5) // synthetic constructor gets position and length of original class definition
        .hasCharno(0)
        .hasLength(21)
        .isIndexable(false);
    assertType(constructorFunctionForB.getJSType()).isEqualTo(classBConstructorType);

    // super(...arguments)
    Node superConstructorCall =
        constructorFunctionForB
            .getLastChild() // constructor body
            .getFirstChild() // expr_result statement
            .getOnlyChild();
    assertNode(superConstructorCall)
        .hasToken(Token.CALL)
        .hasLineno(5) // synthetic constructor gets position and length of original class definition
        .hasCharno(0)
        .hasLength(21)
        .isIndexable(false);
    assertType(superConstructorCall.getJSType()).isEqualTo(classBInstanceType);

    Node superNode = superConstructorCall.getFirstChild();
    assertNode(superNode)
        .hasToken(Token.SUPER)
        .hasLineno(5) // synthetic constructor gets position and length of original class definition
        .hasCharno(0)
        .hasLength(21)
        .isIndexable(false);
    assertType(superNode.getJSType()).isEqualTo(classAConstructorType);

    Node argumentsNode =
        superNode
            .getNext() // ...arguments
            .getOnlyChild();
    assertNode(argumentsNode)
        .isName("arguments")
        .hasLineno(5) // synthetic constructor gets position and length of original class definition
        .hasCharno(0)
        .hasLength(21)
        .isIndexable(false);
    assertType(argumentsNode.getJSType()).isEqualTo(argumentsType);
  }

  @Test
  public void testSynthesizingConstructorOfBaseClassInExtern() {
    testExternChanges(
        "class A { }",
        "new A();", // Source to pin externs.
        "class A { constructor() { } }");
    // TODO(bradfordcsmith): Test addition of types in externs.
    // Currently testExternChanges() doesn't set things up correctly for getting the last compiler
    // and looking up type information in the registry to work.
  }

  @Test
  public void testSynthesizingConstructorOfDerivedClassInExtern() {
    testExternChanges(
        lines(
            "class A {", // Force wrapping.
            "  constructor() { }",
            "}",
            "",
            "class B extends A { }"),
        "new B();", // Source to pin externs.
        lines(
            "class A {",
            "  constructor() { }",
            "}",
            "",
            "class B extends A {",
            "  /** @param {...?} var_args */",
            "  constructor(var_args) { }",
            "}"));
    // TODO(bradfordcsmith): Test addition of types in externs.
    // Currently testExternChanges() doesn't set things up correctly for getting the last compiler
    // and looking up type information in the registry to work.
  }

  @Test
  public void testStrippingSuperCallFromConstructorOfDerivedClassInExtern() {
    testExternChanges(
        lines(
            "const namespace = {};",
            "",
            "namespace.A = class {",
            "  constructor() { }",
            "}",
            "",
            "class B extends namespace.A {",
            "  constructor() { super(); }",
            "}"),
        "new B();", // Source to pin externs.
        lines(
            "const namespace = {};",
            "",
            "namespace.A = class {",
            "  constructor() { }",
            "}",
            "",
            "class B extends namespace.A {",
            "  constructor() { }",
            "}"));
    // TODO(bradfordcsmith): Test addition of types in externs.
    // Currently testExternChanges() doesn't set things up correctly for getting the last compiler
    // and looking up type information in the registry to work.
  }

  @Test
  public void testSynthesizingConstructorOfBaseInterface() {
    test(
        externs(""),
        srcs("/** @interface */ class A { }"),
        expected("/** @interface */ class A { constructor() { } }"));

    // get the types we'll need to check
    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    FunctionType classAConstructorType =
        registry.getGlobalType("A").toObjectType().getConstructor();

    // class A { ... }
    Node classANode =
        getLastCompiler()
            .getJsRoot() // root
            .getFirstChild() // script
            .getFirstChild();
    assertType(classANode.getJSType()).isEqualTo(classAConstructorType);

    // constructor() { }
    Node constructorMemberFunctionDefForA =
        classANode
            .getLastChild() // { ... }
            .getFirstChild();
    assertNode(constructorMemberFunctionDefForA)
        .isMemberFunctionDef("constructor")
        .hasLineno(1) // synthetic constructor gets position and length of original class definition
        .hasCharno(18)
        .hasLength(11)
        .isIndexable(false);
    assertType(constructorMemberFunctionDefForA.getJSType()).isEqualTo(classAConstructorType);

    Node constructorFunctionForA = constructorMemberFunctionDefForA.getOnlyChild();
    assertNode(constructorFunctionForA)
        .hasToken(Token.FUNCTION)
        .hasLineno(1) // synthetic constructor gets position and length of original class definition
        .hasCharno(18)
        .hasLength(11)
        .isIndexable(false);
    assertType(constructorFunctionForA.getJSType()).isEqualTo(classAConstructorType);
  }

  @Test
  public void testSynthesizingConstructorOfDerivedInterface() {
    test(
        externs(
            lines(
                "/** @interface */", // Force wrapping.
                "class A {",
                "  constructor() { }",
                "}")),
        srcs("/** @interface */ class B extends A { }"),
        expected(
            lines(
                "/** @interface */",
                "class B extends A {",
                "  /** @param {...?} var_args */",
                "  constructor(var_args) { }",
                "}")));
  }

  @Test
  public void testStrippingSuperCallFromConstructorOfDerivedInterface() {
    test(
        externs(
            lines(
                "const namespace = {};",
                "",
                "/** @interface */",
                "namespace.A = class {",
                "  constructor() { }",
                "}")),
        srcs(
            lines(
                "/** @interface */",
                "class B extends namespace.A {",
                "  constructor() { super(); }",
                "}")),
        expected(
            lines(
                "/** @interface */", //
                "class B extends namespace.A {",
                "  constructor() { }",
                "}")));
  }
}
