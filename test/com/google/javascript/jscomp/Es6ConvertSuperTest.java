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

import static com.google.javascript.jscomp.TranspilationUtil.CANNOT_CONVERT_YET;
import static com.google.javascript.jscomp.testing.CodeSubTree.findClassDefinition;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.testing.NoninjectingCompiler;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
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
@SuppressWarnings("RhinoNodeGetFirstFirstChild")
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
    replaceTypesWithColors();
    enableMultistageCompilation();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new Es6ConvertSuper(compiler);
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
                "  /** @param {number} x @return {string} */",
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
    Color classAPrototypeType =
        Color.createUnion(
            findClassDefinition(getLastCompiler(), "A").getRootNode().getColor().getPrototypes());

    Color aDotGMethodType =
        findClassDefinition(getLastCompiler(), "A")
            .findMethodDefinition("g")
            .getRootNode()
            .getFirstChild()
            .getColor();

    Color classBInstanceType =
        Color.createUnion(
            findClassDefinition(getLastCompiler(), "B")
                .getRootNode()
                .getColor()
                .getInstanceColors());

    // A.prototype.g.call(this, 3)
    Node callNode =
        findClassDefinition(getLastCompiler(), "B")
            .findMethodDefinition("f")
            .findMatchingQNameReferences("A.prototype.g.call")
            .get(0) // GETPROP node for A.prototype.g.call
            .getParent(); // CALL node within the statement
    assertNode(callNode)
        .hasToken(Token.CALL)
        .hasLineno(4) // position and length of `super.g(3)`
        .hasCharno(8);
    assertNode(callNode).hasColorThat().isEqualTo(StandardColors.STRING);

    // A.prototype.g.call
    Node callee = callNode.getFirstChild();
    assertNode(callee)
        .matchesQualifiedName("A.prototype.g.call")
        .hasLineno(4) // position and length of `super.g`
        .hasCharno(14);
    assertNode(callee).hasColorThat().isEqualTo(StandardColors.UNKNOWN);

    // A.prototype.g
    Node superDotGReplacement = callee.getFirstChild();
    assertNode(superDotGReplacement)
        .matchesQualifiedName("A.prototype.g")
        .hasLineno(4) // position and length of `super.g`
        .hasCharno(14);
    assertNode(superDotGReplacement).hasColorThat().isEqualTo(aDotGMethodType);

    // A.prototype
    Node superReplacement = superDotGReplacement.getFirstChild();
    assertNode(superReplacement)
        .matchesQualifiedName("A.prototype")
        .hasLineno(4) // position and length of `super`
        .hasCharno(8)
        .hasOriginalName("super");
    assertNode(superReplacement).hasColorThat().isEqualTo(classAPrototypeType);

    // `this` node from `A.prototype.g.call(this, 3)`
    Node thisNode = callee.getNext();
    assertNode(thisNode)
        .hasToken(Token.THIS)
        .hasLineno(4) // position and length of `super.g`
        .hasCharno(14)
        .isIndexable(false); // there's no direct correlation with text in the original source
    assertNode(thisNode).hasColorThat().isEqualTo(classBInstanceType);
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
                "class B extends A {",
                "  constructor() {",
                "    super();",
                "  }",
                "",
                "  ['f']() { A.prototype['g'].call(this, 4); }",
                "}")));

    // get types we need to check
    Color classAPrototypeType =
        Color.createUnion(
            findClassDefinition(getLastCompiler(), "A").getRootNode().getColor().getPrototypes());

    Color classBInstanceType =
        Color.createUnion(
            findClassDefinition(getLastCompiler(), "B")
                .getRootNode()
                .getColor()
                .getInstanceColors());

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
        .hasCharno(12);
    // computed property prevents doing any better than StandardColors.UNKNOWN
    assertNode(callNode).hasColorThat().isEqualTo(StandardColors.UNKNOWN);

    // A.prototype['g'].call
    Node callee = callNode.getFirstChild();
    assertNode(callee)
        .hasToken(Token.GETPROP)
        .hasLineno(5) // position and length of `super['g']`
        .hasCharno(12);
    assertNode(callee).hasColorThat().isEqualTo(StandardColors.UNKNOWN);

    // A.prototype['g']
    Node superGetelemReplacement = callee.getFirstChild();
    assertNode(superGetelemReplacement)
        .hasToken(Token.GETELEM)
        .hasLineno(5) // position and length of `super['g']`
        .hasCharno(12);
    // computed property prevents doing any better than StandardColors.UNKNOWN
    assertNode(superGetelemReplacement).hasColorThat().isEqualTo(StandardColors.UNKNOWN);

    // A.prototype
    Node superReplacement = superGetelemReplacement.getFirstChild();
    assertNode(superReplacement)
        .matchesQualifiedName("A.prototype")
        .hasLineno(5) // position and length of `super`
        .hasCharno(12)
        .hasOriginalName("super");
    assertNode(superReplacement).hasColorThat().isEqualTo(classAPrototypeType);

    // `this` node from `A.prototype['g'].call(this, 3)`
    Node thisNode = callee.getNext();
    assertNode(thisNode)
        .hasToken(Token.THIS)
        .hasLineno(5) // position and length of `super['g']`
        .hasCharno(12)
        .isIndexable(false); // there's no direct correlation with text in the original source
    assertNode(thisNode).hasColorThat().isEqualTo(classBInstanceType);
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
    Color classAPrototypeType =
        Color.createUnion(
            findClassDefinition(getLastCompiler(), "A").getRootNode().getColor().getPrototypes());
    Color aDotGMethodType =
        findClassDefinition(getLastCompiler(), "A")
            .findMethodDefinition("g")
            .getRootNode()
            .getFirstChild()
            .getColor();

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
        .hasCharno(22);
    assertNode(superDotGReplacement).hasColorThat().isEqualTo(aDotGMethodType);

    // A.prototype
    Node superReplacement = superDotGReplacement.getFirstChild();
    assertNode(superReplacement)
        .matchesQualifiedName("A.prototype")
        .hasLineno(4) // position and length of `super`
        .hasCharno(16)
        .hasOriginalName("super");
    assertNode(superReplacement).hasColorThat().isEqualTo(classAPrototypeType);
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
                "class B extends A {",
                "  constructor() {",
                "    super();",
                "  }",
                "",
                "  ['f']() { var t = A.prototype['g']; }",
                "}")));

    // get types we need to check
    Color classAPrototypeType =
        Color.createUnion(
            findClassDefinition(getLastCompiler(), "A").getRootNode().getColor().getPrototypes());

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
        .hasCharno(20);
    // getelem prevents us doing any better than unknown type
    assertNode(superGetelemReplacement).hasColorThat().isEqualTo(StandardColors.UNKNOWN);

    // A.prototype
    Node superReplacement = superGetelemReplacement.getFirstChild();
    assertNode(superReplacement)
        .matchesQualifiedName("A.prototype")
        .hasLineno(5) // position and length of `super`
        .hasCharno(20)
        .hasOriginalName("super");
    assertNode(superReplacement).hasColorThat().isEqualTo(classAPrototypeType);
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
                "  /** @param {number} x @return {string} */",
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
    Color classAType = findClassDefinition(getLastCompiler(), "A").getRootNode().getColor();
    Color aDotGMethodType =
        findClassDefinition(getLastCompiler(), "A")
            .findMethodDefinition("g")
            .getRootNode()
            .getFirstChild()
            .getColor();
    Color aDotGDotCallType = StandardColors.UNKNOWN; // colors do not track ".call" type

    Color classBType = findClassDefinition(getLastCompiler(), "B").getRootNode().getColor();

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
        .hasCharno(15);
    assertNode(callNode).hasColorThat().isEqualTo(StandardColors.STRING);

    // A.g.call
    Node callee = callNode.getFirstChild();
    assertNode(callee)
        .matchesQualifiedName("A.g.call")
        .hasLineno(4) // position and length of `super.g`
        .hasCharno(21);
    assertNode(callee).hasColorThat().isEqualTo(aDotGDotCallType);

    // A.g
    Node superDotGReplacement = callee.getFirstChild();
    assertNode(superDotGReplacement)
        .matchesQualifiedName("A.g")
        .hasLineno(4) // position and length of `super.g`
        .hasCharno(21);
    assertNode(superDotGReplacement).hasColorThat().isEqualTo(aDotGMethodType);

    // A
    Node superReplacement = superDotGReplacement.getFirstChild();
    assertNode(superReplacement)
        .matchesQualifiedName("A")
        .hasLineno(4) // position and length of `super`
        .hasCharno(15)
        .hasOriginalName("super");
    assertNode(superReplacement).hasColorThat().isEqualTo(classAType);

    Node thisNode = callee.getNext();
    assertNode(thisNode).hasType(Token.THIS);
    assertNode(thisNode).hasColorThat().isEqualTo(classBType);
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
                "class B extends A {",
                "  constructor() {",
                "    super();",
                "  }",
                "",
                "  static ['f']() { A['g'].call(this, 4); }",
                "}")));

    // get types we need to check
    Color classAType = findClassDefinition(getLastCompiler(), "A").getRootNode().getColor();
    Color classBType = findClassDefinition(getLastCompiler(), "B").getRootNode().getColor();

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
        .hasCharno(19);
    // computed property prevents doing any better than StandardColors.UNKNOWN
    assertNode(callNode).hasColorThat().isEqualTo(StandardColors.UNKNOWN);

    // A['g'].call
    Node callee = callNode.getFirstChild();
    assertNode(callee)
        .hasToken(Token.GETPROP)
        .hasLineno(5) // position and length of `super['g']`
        .hasCharno(19);
    assertNode(callee).hasColorThat().isEqualTo(StandardColors.UNKNOWN);

    // A['g']
    Node superGetelemReplacement = callee.getFirstChild();
    assertNode(superGetelemReplacement)
        .hasToken(Token.GETELEM)
        .hasLineno(5) // position and length of `super['g']`
        .hasCharno(19);
    // computed property prevents doing any better than StandardColors.UNKNOWN
    assertNode(superGetelemReplacement).hasColorThat().isEqualTo(StandardColors.UNKNOWN);

    // A
    Node superReplacement = superGetelemReplacement.getFirstChild();
    assertNode(superReplacement)
        .matchesName("A")
        .hasLineno(5) // position and length of `super`
        .hasCharno(19)
        .hasOriginalName("super");
    assertNode(superReplacement).hasColorThat().isEqualTo(classAType);

    // `this` node from `A['g'].call(this, 3)`
    Node thisNode = callee.getNext();
    assertNode(thisNode)
        .hasToken(Token.THIS)
        .hasLineno(5) // position and length of `super['g']`
        .hasCharno(19)
        .isIndexable(false); // there's no direct correlation with text in the original source
    assertNode(thisNode).hasColorThat().isEqualTo(classBType);
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
    Color classAType = findClassDefinition(getLastCompiler(), "A").getRootNode().getColor();
    Color aDotGMethodType =
        findClassDefinition(getLastCompiler(), "A")
            .findMethodDefinition("g")
            .getRootNode()
            .getFirstChild()
            .getColor();

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
        .hasCharno(29);
    assertNode(superDotGReplacement).hasColorThat().isEqualTo(aDotGMethodType);

    // A.prototype
    Node superReplacement = superDotGReplacement.getFirstChild();
    assertNode(superReplacement)
        .matchesQualifiedName("A")
        .hasLineno(4) // position and length of `super`
        .hasCharno(23)
        .hasOriginalName("super");
    assertNode(superReplacement).hasColorThat().isEqualTo(classAType);
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
                "class B extends A {",
                "  constructor() {",
                "    super();",
                "  }",
                "",
                "  static ['f']() { var t = A['g']; }",
                "}")));

    // get types we need to check
    Color classAType = findClassDefinition(getLastCompiler(), "A").getRootNode().getColor();

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
        .hasCharno(27);
    // computed property prevents doing any better than StandardColors.UNKNOWN
    assertNode(superGetElemReplacement).hasColorThat().isEqualTo(StandardColors.UNKNOWN);

    // A.prototype
    Node superReplacement = superGetElemReplacement.getFirstChild();
    assertNode(superReplacement)
        .matchesQualifiedName("A")
        .hasLineno(5) // position and length of `super`
        .hasCharno(27)
        .hasOriginalName("super");
    assertNode(superReplacement).hasColorThat().isEqualTo(classAType);
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
                "  /** @param {number} x @return {number} */",
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
    Color classAPrototypeType =
        Color.createUnion(
            findClassDefinition(getLastCompiler(), "A").getRootNode().getColor().getPrototypes());
    Color aDotGMethodType =
        findClassDefinition(getLastCompiler(), "A")
            .findMethodDefinition("g")
            .getRootNode()
            .getFirstChild()
            .getColor();
    Color aDotGDotCallType = StandardColors.UNKNOWN; // colors do not track ".call" type

    Color classBInstanceType =
        Color.createUnion(
            findClassDefinition(getLastCompiler(), "B")
                .getRootNode()
                .getColor()
                .getInstanceColors());

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
        .hasCharno(12);
    assertNode(callNode).hasColorThat().isEqualTo(StandardColors.NUMBER);

    // A.prototype.g.call
    Node callee = callNode.getFirstChild();
    assertNode(callee)
        .matchesQualifiedName("A.prototype.g.call")
        .hasLineno(4) // position and length of `super.g`
        .hasCharno(18);
    assertNode(callee).hasColorThat().isEqualTo(aDotGDotCallType);

    // A.prototype.g
    Node superDotGReplacement = callee.getFirstChild();
    assertNode(superDotGReplacement)
        .matchesQualifiedName("A.prototype.g")
        .hasLineno(4) // position and length of `super.g`
        .hasCharno(18);
    assertNode(superDotGReplacement).hasColorThat().isEqualTo(aDotGMethodType);

    // A.prototype
    Node superReplacement = superDotGReplacement.getFirstChild();
    assertNode(superReplacement)
        .matchesQualifiedName("A.prototype")
        .hasLineno(4) // position and length of `super`
        .hasCharno(12)
        .hasOriginalName("super");
    assertNode(superReplacement).hasColorThat().isEqualTo(classAPrototypeType);

    // `this` node from `A.prototype.g.call(this, 3)`
    Node thisNode = callee.getNext();
    assertNode(thisNode)
        .hasToken(Token.THIS)
        .hasLineno(4) // position and length of `super.g`
        .hasCharno(18)
        .isIndexable(false); // there's no direct correlation with text in the original source
    assertNode(thisNode).hasColorThat().isEqualTo(classBInstanceType);
  }

  @Test
  public void testResolvingSuperInSetter() {
    test(
        externs(
            lines(
                "class A {",
                "  constructor() { }",
                "",
                "  /** @param {number} x @return {string} */",
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
                "  set f(x) { A.prototype.g.call(this, x); }",
                "}")));

    // get types we need to check
    Color classAPrototypeType =
        Color.createUnion(
            findClassDefinition(getLastCompiler(), "A").getRootNode().getColor().getPrototypes());
    Color aDotGMethodType =
        findClassDefinition(getLastCompiler(), "A")
            .findMethodDefinition("g")
            .getRootNode()
            .getFirstChild()
            .getColor();
    Color aDotGDotCallType = StandardColors.UNKNOWN; // colors do not track ".call" type

    Color classBInstanceType =
        Color.createUnion(
            findClassDefinition(getLastCompiler(), "B")
                .getRootNode()
                .getColor()
                .getInstanceColors());

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
        .hasCharno(13);
    assertNode(callNode).hasColorThat().isEqualTo(StandardColors.STRING);

    // A.prototype.g.call
    Node callee = callNode.getFirstChild();
    assertNode(callee)
        .matchesQualifiedName("A.prototype.g.call")
        .hasLineno(5) // position and length of `super.g`
        .hasCharno(19);
    assertNode(callee).hasColorThat().isEqualTo(aDotGDotCallType);

    // A.prototype.g
    Node superDotGReplacement = callee.getFirstChild();
    assertNode(superDotGReplacement)
        .matchesQualifiedName("A.prototype.g")
        .hasLineno(5) // position and length of `super.g`
        .hasCharno(19);
    assertNode(superDotGReplacement).hasColorThat().isEqualTo(aDotGMethodType);

    // A.prototype
    Node superReplacement = superDotGReplacement.getFirstChild();
    assertNode(superReplacement)
        .matchesQualifiedName("A.prototype")
        .hasLineno(5) // position and length of `super`
        .hasCharno(13)
        .hasOriginalName("super");
    assertNode(superReplacement).hasColorThat().isEqualTo(classAPrototypeType);

    // `this` node from `A.prototype.g.call(this, 3)`
    Node thisNode = callee.getNext();
    assertNode(thisNode)
        .hasToken(Token.THIS)
        .hasLineno(5) // position and length of `super.g`
        .hasCharno(19)
        .isIndexable(false); // there's no direct correlation with text in the original source
    assertNode(thisNode).hasColorThat().isEqualTo(classBInstanceType);
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

    // class A { ... }
    Node classANode = findClassDefinition(getLastCompiler(), "A").getRootNode();
    Color classAConstructorType = classANode.getColor();

    // constructor() { }
    Node constructorMemberFunctionDefForA =
        classANode
            .getLastChild() // { ... }
            .getFirstChild();
    assertNode(constructorMemberFunctionDefForA)
        .isMemberFunctionDef("constructor")
        .hasLineno(1) // synthetic constructor gets position and length of original class definition
        .hasCharno(0)
        .isIndexable(false);
    assertNode(constructorMemberFunctionDefForA).hasColorThat().isEqualTo(classAConstructorType);

    Node constructorFunctionForA = constructorMemberFunctionDefForA.getOnlyChild();
    assertNode(constructorFunctionForA)
        .hasToken(Token.FUNCTION)
        .hasLineno(1) // synthetic constructor gets position and length of original class definition
        .hasCharno(0)
        .isIndexable(false);
    assertNode(constructorFunctionForA).hasColorThat().isEqualTo(classAConstructorType);
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
                "  constructor() { super(...arguments); }",
                "}")));

    // class A { ... }
    Node classANode = findClassDefinition(getLastCompiler(), "A").getRootNode();
    Color classAConstructorType = classANode.getColor();

    // class B extends A { ... }
    Node classBNode = findClassDefinition(getLastCompiler(), "B").getRootNode();
    Color classBConstructorType = classBNode.getColor();
    Color classBInstanceType = Color.createUnion(classBConstructorType.getInstanceColors());

    // constructor() { }
    Node constructorMemberFunctionDefForB =
        classBNode
            .getLastChild() // { ... }
            .getFirstChild();
    assertNode(constructorMemberFunctionDefForB)
        .isMemberFunctionDef("constructor")
        .hasLineno(5) // synthetic constructor gets position and length of original class definition
        .hasCharno(0)
        .isIndexable(false);
    assertNode(constructorMemberFunctionDefForB).hasColorThat().isEqualTo(classBConstructorType);

    Node constructorFunctionForB = constructorMemberFunctionDefForB.getOnlyChild();
    assertNode(constructorFunctionForB)
        .hasToken(Token.FUNCTION)
        .hasLineno(5) // synthetic constructor gets position and length of original class definition
        .hasCharno(0)
        .isIndexable(false);
    assertNode(constructorFunctionForB).hasColorThat().isEqualTo(classBConstructorType);

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
        .isIndexable(false);
    assertNode(superConstructorCall).hasColorThat().isEqualTo(classBInstanceType);

    Node superNode = superConstructorCall.getFirstChild();
    assertNode(superNode)
        .hasToken(Token.SUPER)
        .hasLineno(5) // synthetic constructor gets position and length of original class definition
        .hasCharno(0)
        .isIndexable(false);
    assertNode(superNode).hasColorThat().isEqualTo(classAConstructorType);

    Node argumentsNode =
        superNode
            .getNext() // ...arguments
            .getOnlyChild();
    assertNode(argumentsNode)
        .isName("arguments")
        .hasLineno(5) // synthetic constructor gets position and length of original class definition
        .hasCharno(0)
        .isIndexable(false);
  }

  @Test
  public void testSynthesizingConstructorOfBaseInterface() {
    test(
        externs(""),
        srcs("/** @interface */ class A { }"),
        expected("/** @interface */ class A { constructor() { } }"));

    // class A { ... }
    Node classANode = findClassDefinition(getLastCompiler(), "A").getRootNode();
    Color classAConstructorType = classANode.getColor();

    // constructor() { }
    Node constructorMemberFunctionDefForA =
        classANode
            .getLastChild() // { ... }
            .getFirstChild();
    assertNode(constructorMemberFunctionDefForA)
        .isMemberFunctionDef("constructor")
        .hasLineno(1) // synthetic constructor gets position and length of original class definition
        .hasCharno(18)
        .isIndexable(false);
    assertNode(constructorMemberFunctionDefForA).hasColorThat().isEqualTo(classAConstructorType);

    Node constructorFunctionForA = constructorMemberFunctionDefForA.getOnlyChild();
    assertNode(constructorFunctionForA)
        .hasToken(Token.FUNCTION)
        .hasLineno(1) // synthetic constructor gets position and length of original class definition
        .hasCharno(18)
        .isIndexable(false);
    assertNode(constructorFunctionForA).hasColorThat().isEqualTo(classAConstructorType);
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
                "/** @interface */", //
                "class B extends A {",
                "  constructor() { }",
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
