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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.Es6RewriteClass.DYNAMIC_EXTENDS_TYPE;
import static com.google.javascript.jscomp.Es6ToEs3Util.CANNOT_CONVERT_YET;
import static com.google.javascript.jscomp.parsing.parser.FeatureSet.ES2015_MODULES;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.testing.NoninjectingCompiler;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
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

@RunWith(JUnit4.class)
public final class Es6RewriteClassTest extends CompilerTestCase {

  // Stub out just enough of ES6 runtime libraries to satisfy the typechecker.
  // In a real compilation, the needed parts of the library are loaded automatically.
  static final String RUNTIME_LIBRARY_STUBS =
      lines(
          "const $jscomp = {};",
          "/**",
          " * @param {function(new: ?)} subclass",
          " * @param {function(new: ?)} superclass",
          " */",
          "$jscomp.inherits = function(subclass, superclass) {};",
          "");

  public Es6RewriteClassTest() {
    super(
        new TestExternsBuilder()
            .addArguments()
            .addArray()
            .addFunction()
            .addConsole()
            .addAlert()
            .addClosureExterns()
            .build());
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    enableTypeCheck();
    enableTypeInfoValidation();
    enableScriptFeatureValidation();
  }

  protected final PassFactory makePassFactory(String name, final CompilerPass pass) {
    return PassFactory.builder()
        .setName(name)
        .setInternalFactory((compiler) -> pass)
        .setFeatureSet(ES2015_MODULES)
        .build();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    PhaseOptimizer optimizer = new PhaseOptimizer(compiler, null);
    optimizer.addOneTimePass(makePassFactory("es6ConvertSuper", new Es6ConvertSuper(compiler)));
    optimizer.addOneTimePass(makePassFactory("es6ExtractClasses", new Es6ExtractClasses(compiler)));
    optimizer.addOneTimePass(makePassFactory("es6RewriteClass", new Es6RewriteClass(compiler)));
    return optimizer;
  }

  @Test
  public void testSimpleClassStatement() {
    test("class C { }", "/** @constructor */ let C = function() {};");

    // get types we need to check
    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    ObjectType classCInstanceType = registry.getGlobalType("C").toObjectType();
    FunctionType classCConstructorType = classCInstanceType.getConstructor();

    // `C` from `let C = function() {};`
    Node cName = getNodeWithName(getLastCompiler().getJsRoot(), "C");
    assertNode(cName).hasToken(Token.NAME);
    assertType(cName.getJSType()).isEqualTo(classCConstructorType);

    // function() {}
    Node function = cName.getOnlyChild();
    assertNode(function).hasToken(Token.FUNCTION);
    assertType(cName.getJSType()).isEqualTo(classCConstructorType);
  }

  @Test
  public void testClassStatementWithConstructor() {
    test("class C { constructor() {} }", "/** @constructor */ let C = function() {};");

    // get types we need to check
    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    ObjectType classCInstanceType = registry.getGlobalType("C").toObjectType();
    FunctionType classCConstructorType = classCInstanceType.getConstructor();

    // `C` from `let C = function() {};`
    Node cName = getNodeWithName(getLastCompiler().getJsRoot(), "C");
    assertNode(cName).hasToken(Token.NAME);
    assertType(cName.getJSType()).isEqualTo(classCConstructorType);

    // function() {}
    Node function = cName.getOnlyChild();
    assertNode(function).hasToken(Token.FUNCTION);
    assertType(cName.getJSType()).isEqualTo(classCConstructorType);
  }

  @Test
  public void testClassStatementWithMethod() {
    test(
        "class C { method() {}; }",
        lines(
            "/** @constructor */",
            "let C = function() {};",
            "C.prototype.method = function() {};"));

    // get types we need to check
    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    ObjectType classCInstanceType = registry.getGlobalType("C").toObjectType();
    FunctionType classCConstructorType = classCInstanceType.getConstructor();
    ObjectType classCPrototypeType = classCConstructorType.getPrototype();
    JSType methodType = classCPrototypeType.getPropertyType("method");

    // `C.prototype.method`
    Node cPrototypeMethod = getNodeWithName(getLastCompiler().getJsRoot(), "method");
    assertNode(cPrototypeMethod).matchesQualifiedName("C.prototype.method");
    assertType(cPrototypeMethod.getJSType()).isEqualTo(methodType);

    // `C.prototype`
    Node cPrototype = cPrototypeMethod.getFirstChild();
    assertNode(cPrototype).matchesQualifiedName("C.prototype");
    assertType(cPrototype.getJSType()).isEqualTo(classCPrototypeType);

    // `C`
    Node cName = cPrototype.getFirstChild();
    assertNode(cName).matchesQualifiedName("C");
    assertType(cName.getJSType()).isEqualTo(classCConstructorType);

    // `function() {}`
    Node function = cPrototypeMethod.getNext();
    assertNode(function).hasToken(Token.FUNCTION);
    assertType(function.getJSType()).isEqualTo(methodType);
  }

  @Test
  public void testClassStatementWithConstructorReferencingThis() {
    test(
        "class C { constructor(a) { this.a = a; } }",
        "/** @constructor */ let C = function(a) { this.a = a; };");
  }

  @Test
  public void testClassStatementWithConstructorAndMethod() {
    test(
        "class C { constructor() {} foo() {} }",
        lines("/** @constructor */", "let C = function() {};", "C.prototype.foo = function() {};"));
  }

  @Test
  public void testClassStatementWithConstructorAndMethods() {
    test(
        "class C { constructor() {}; foo() {}; bar() {} }",
        lines(
            "/** @constructor */",
            "let C = function() {};",
            "C.prototype.foo = function() {};",
            "C.prototype.bar = function() {};"));
  }

  @Test
  public void testClassStatementWithTwoMethods() {
    test(
        "class C { foo() {}; bar() {} }",
        lines(
            "/** @constructor */",
            "let C = function() {};",
            "C.prototype.foo = function() {};",
            "C.prototype.bar = function() {};"));
  }

  @Test
  public void testClassStatementWithNonEmptyConstructorAndMethods() {
    test(
        lines(
            "class C {",
            "  constructor(a) { this.a = a; }",
            "",
            "  foo() { console.log(this.a); }",
            "",
            "  bar() { alert(this.a); }",
            "}"),
        lines(
            "/** @constructor */",
            "let C = function(a) { this.a = a; };",
            "C.prototype.foo = function() { console.log(this.a); };",
            "C.prototype.bar = function() { alert(this.a); };"));
  }

  @Test
  public void testClassStatementsInDifferentBlockScopes() {
    test(
        lines(
            "if (true) {", //
            "  class Foo{}",
            "} else {",
            "  class Foo{}",
            "}"),
        lines(
            "if (true) {",
            "   /** @constructor */",
            "   let Foo = function() {};",
            "} else {",
            "   /** @constructor */",
            "   let Foo = function() {};",
            "}"));
  }

  @Test
  public void testAnonymousWithSuper() {
    enableClosurePass();
    ignoreWarnings(FunctionTypeBuilder.RESOLVED_TAG_EMPTY, TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);
    test(
        lines(
            "goog.forwardDeclare('D')", //
            "f(class extends D { f() { super.g() } })"),
        lines(
            "/** @constructor",
            " * @extends {D}",
            " */",
            "const testcode$classdecl$var0 = function() {",
            "  return D.apply(this, arguments) || this; ",
            "};",
            "$jscomp.inherits(testcode$classdecl$var0, D);",
            "testcode$classdecl$var0.prototype.f = function() { D.prototype.g.call(this); };",
            "f(testcode$classdecl$var0)"));
  }

  @Test
  public void testClassWithNoJsDoc() {
    test("class C { }", "/** @constructor */ let C = function() { };");
  }

  @Test
  public void testClassWithDeprecatedJsDoc() {
    test(
        "/** @deprecated */ class C { }", "/** @constructor @deprecated */ let C = function() {};");
  }

  @Test
  public void testClassWithDictJsDoc() {
    test("/** @dict */ class C { }", "/** @constructor @dict */ let C = function() {};");
  }

  @Test
  public void testClassWithTemplateJsDoc() {
    test(
        "/** @template T */ class C { }", "/** @constructor @template T */ let C = function() {};");
  }

  @Test
  public void testClassWithFinalJsDoc() {
    test("/** @final */ class C { }", "/** @constructor @final */ let C = function() {};");
  }

  @Test
  public void testClassWithPrivateJsDoc() {
    test("/** @private */ class C { }", "/** @constructor @private */ let C = function() {};");
  }

  @Test
  public void testInterfaceWithJsDoc() {
    enableClosurePass();
    test(
        lines(
            "goog.forwardDeclare('X');",
            "goog.forwardDeclare('Y');",
            "/**",
            " * Converts Xs to Ys.",
            " * @interface",
            " */",
            "class Converter {",
            "  /**",
            "   * @param {X} x",
            "   * @return {Y}",
            "   */",
            "  convert(x) {}",
            "}"),
        lines(
            "/**",
            " * Converts Xs to Ys.",
            " * @interface",
            " */",
            "let Converter = function() { };",
            "",
            "/**",
            " * @param {X} x",
            " * @return {Y}",
            " */",
            "Converter.prototype.convert = function(x) {};"));
  }

  @Test
  public void testNoTypeForParam() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        lines(
            "class MdMenu {", //
            "  /**",
            "   * @param c",
            "   */",
            "  set classList(c) {}",
            "}"),
        lines(
            "/** @constructor */",
            "let MdMenu=function(){};",
            "$jscomp.global.Object.defineProperties(",
            "    MdMenu.prototype,",
            "    {",
            "      classList: {",
            "        configurable:true,",
            "        enumerable:true,",
            "        /**",
            "         * @param c",
            "         */",
            "        set:function(c){}",
            "      }",
            "    })"));
  }

  @Test
  public void testParamNameMismatch() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    ignoreWarnings(FunctionTypeBuilder.INEXISTENT_PARAM);

    test(
        lines(
            "class MdMenu {",
            "/**",
            "* @param {boolean} classes",
            "*/",
            "set classList(c) {}",
            "}"),
        lines(
            "/** @constructor */",
            "let MdMenu=function(){};",
            "$jscomp.global.Object.defineProperties(",
            "    MdMenu.prototype,",
            "    {",
            "      classList: {",
            "        configurable:true,",
            "        enumerable:true,",
            "       /**",
            "        * @param {boolean} classes",
            "        */",
            "       set:function(c){}",
            "     }",
            "   })"));
  }

  @Test
  public void testParamNameMismatchAndNoParamType() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    ignoreWarnings(FunctionTypeBuilder.INEXISTENT_PARAM);

    test(
        lines("class MdMenu {", "/**", "* @param classes", "*/", "set classList(c) {}", "}"),
        lines(
            "/** @constructor */",
            "let MdMenu=function(){};",
            "$jscomp.global.Object.defineProperties(",
            "    MdMenu.prototype,",
            "    {",
            "      classList: {",
            "        configurable:true,",
            "        enumerable:true,",
            "        /**",
            "         * @param  classes",
            "         */",
            "        set:function(c){}",
            "      }",
            "   })"));
  }

  @Test
  public void testRecordWithJsDoc() {
    enableClosurePass();
    test(
        lines(
            "goog.forwardDeclare('X');",
            "goog.forwardDeclare('Y');",
            "/**",
            " * @record",
            " */",
            "class Converter {",
            "  /**",
            "   * @param {X} x",
            "   * @return {Y}",
            "   */",
            "  convert(x) {}",
            "}"),
        lines(
            "/**",
            " * @record",
            " */",
            "let Converter = function() { };",
            "",
            "/**",
            " * @param {X} x",
            " * @return {Y}",
            " */",
            "Converter.prototype.convert = function(x) {};"));
  }

  @Test
  public void testMemberWithJsDoc() {
    test(
        "class C { /** @param {boolean} b */ foo(b) {} }",
        lines(
            "/**",
            " * @constructor",
            " */",
            "let C = function() {};",
            "",
            "/** @param {boolean} b */",
            "C.prototype.foo = function(b) {};"));
  }

  @Test
  public void testClassStatementInsideIf() {
    test("if (foo) { class C { } }", "if (foo) { /** @constructor */ let C = function() {}; }");
  }

  @Test
  public void testClassStatementInsideBlocklessIf() {
    test("if (foo) class C {}", "if (foo) { /** @constructor */ let C = function() {}; }");
  }

  /** Class expressions that are the RHS of a 'var' statement. */
  @Test
  public void testClassExpressionInVar() {
    test("var C = class { }", "/** @constructor */ var C = function() {}");
  }

  @Test
  public void testClassExpressionInVarWithMethod() {
    test(
        "var C = class { foo() {} }",
        lines("/** @constructor */ var C = function() {}", "", "C.prototype.foo = function() {}"));
  }

  @Test
  public void testNamedClassExpressionInVar() {
    test(
        "var C = class C { }",
        lines(
            "/** @constructor */",
            "const testcode$classdecl$var0 = function() {};",
            "/** @constructor */",
            "var C = testcode$classdecl$var0;"));
  }

  @Test
  public void testNamedClassExpressionInVarWithMethod() {
    test(
        "var C = class C { foo() {} }",
        lines(
            "/** @constructor */",
            "const testcode$classdecl$var0 = function() {}",
            "testcode$classdecl$var0.prototype.foo = function() {};",
            "",
            "/** @constructor */",
            "var C = testcode$classdecl$var0;"));
  }

  /** Class expressions that are the RHS of an assignment. */
  @Test
  public void testClassExpressionInAssignment() {
    test(
        lines(
            "/** @const */ goog.example = {};", //
            "goog.example.C = class { }"),
        lines(
            "/** @const */ goog.example = {};", //
            "/** @constructor */ goog.example.C = function() {}"));
  }

  @Test
  public void testClassExpressionInAssignmentWithMethod() {
    test(
        lines(
            "/** @const */ goog.example = {};", //
            "goog.example.C = class { foo() {} }"),
        lines(
            "/** @const */ goog.example = {};", //
            "/** @constructor */ goog.example.C = function() {}",
            "goog.example.C.prototype.foo = function() {};"));
  }

  @Test
  public void testClassExpressionInAssignment_getElem() {
    test(
        "window['MediaSource'] = class {};",
        lines(
            "/** @constructor */",
            "const testcode$classdecl$var0 = function() {};",
            "window['MediaSource'] = testcode$classdecl$var0;"));
  }

  @Test
  public void testClassExpressionImmediatelyInstantiated() {
    test(
        "var C = new (class {})();",
        lines(
            "/** @constructor */",
            "const testcode$classdecl$var0=function(){};",
            "var C=new testcode$classdecl$var0"));
  }

  @Test
  public void testClassExpressionAssignedToComplexLhs() {
    test(
        "(condition ? obj1 : obj2).prop = class C { };",
        lines(
            "/** @constructor */",
            "const testcode$classdecl$var0 = function(){};",
            "(condition ? obj1 : obj2).prop = testcode$classdecl$var0;"));
  }

  @Test
  public void testClassExpression_cannotConvert() {
    test(
        "var C = new (foo || (foo = class { }))();",
        lines(
            "var JSCompiler_temp$jscomp$0;",
            "if (JSCompiler_temp$jscomp$0 = foo) {",
            "} else {",
            "  /** @constructor */ const testcode$classdecl$var0 = function(){};",
            "  JSCompiler_temp$jscomp$0 = foo = testcode$classdecl$var0;",
            "}",
            "var C = new JSCompiler_temp$jscomp$0;"));
  }

  @Test
  public void testExtendsWithImplicitConstructor() {
    test(
        "class D {} class C extends D {}",
        lines(
            "/** @constructor */",
            "let D = function() {};",
            "/** @constructor",
            " * @extends {D}",
            " */",
            "let C = function() { D.apply(this, arguments); };",
            "$jscomp.inherits(C, D);"));
  }

  @Test
  public void testExtendsWithImplicitConstructorNoTypeInfo() {
    disableTypeCheck();
    disableTypeInfoValidation();
    test(
        srcs("class D {} class C extends D {}"),
        expected(
            lines(
                "/** @constructor */",
                "let D = function() {};",
                "/** @constructor",
                " * @extends {D}",
                " */",
                "let C = function() {",
                // even without type information we recognize `arguments` and avoid generating
                // `[...arguments]`
                "  D.apply(this, arguments);",
                "};",
                "$jscomp.inherits(C, D);")));
  }

  @Test
  public void testExtendsWithSpreadsPassedToSuper() {
    test(
        lines(
            "class D {", //
            "  /** @param {...string|number} args*/",
            "  constructor(...args) {",
            "    /** @type {!Arguments} */",
            "    this.args = arguments;",
            "  }",
            "}",
            "class C extends D {",
            "  /**",
            "   * @param {!Array<number>} numbers",
            "   * @param {!Array<string>} strings",
            "   */",
            "  constructor(numbers, strings) {",
            "    super(...numbers, ...strings);",
            "  }",
            "}",
            ""),
        lines(
            "/**",
            " * @constructor",
            " * @param {!Array<string|number>}",
            " */",
            "let D = function(...args) {",
            "  /** @type {!Arguments} */",
            "  this.args = arguments;",
            "};",
            "/**",
            " * @constructor",
            " * @extends {D}",
            " */",
            "let C = function(numbers, strings) {",
            "  D.apply(this, [...numbers, ...strings]);",
            "};",
            "$jscomp.inherits(C, D);"));
  }

  @Test
  public void testExtendsWithExplicitSuperCall() {
    test(
        "class D {} class C extends D { constructor() { super(); } }",
        lines(
            "/** @constructor */",
            "let D = function() {};",
            "/** @constructor @extends {D} */",
            "let C = function() {",
            "  D.call(this);",
            "}",
            "$jscomp.inherits(C, D);"));
  }

  @Test
  public void testExtendsWithExplicitSuperCallWithArguments() {
    test(
        lines(
            "class D { constructor(str) {} }",
            "class C extends D { constructor(str) { super(str); } }"),
        lines(
            "/** @constructor */",
            "let D = function(str) {};",
            "/** @constructor @extends {D} */",
            "let C = function(str) { ",
            "  D.call(this, str);",
            "}",
            "$jscomp.inherits(C, D);"));
  }

  @Test
  public void testExtendsExternsClass() {
    test(
        externs("const ns = {}; /** @constructor */ ns.D = function() {};"),
        srcs(linesWithStubs("class C extends ns.D { }")),
        expected(
            linesWithStubs(
                "/** @constructor",
                " * @extends {ns.D}",
                " */",
                "let C = function() {",
                " return ns.D.apply(this, arguments) || this;",
                "};",
                "$jscomp.inherits(C, ns.D);")));
  }

  @Test
  public void testExtendForwardDeclaredClass() {
    enableClosurePass();
    testWarning(
        lines(
            "goog.forwardDeclare('ns.D');", //
            "class C extends ns.D { }"),
        TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);
  }

  @Test
  public void testImplementForwardDeclaredInterface() {
    ignoreWarnings(TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);
    enableClosurePass();
    testWarning(
        lines(
            "goog.forwardDeclare('ns.D');", //
            "/** @implements {ns.D} */",
            "class C { }"),
        FunctionTypeBuilder.RESOLVED_TAG_EMPTY);
  }

  @Test
  public void testExtendForwardDeclaredInterface() {
    ignoreWarnings(TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);
    enableClosurePass();
    testWarning(
        lines(
            "goog.forwardDeclare('ns.D');", //
            "/** @interface @extends {ns.D} */ class C { }"),
        FunctionTypeBuilder.RESOLVED_TAG_EMPTY);
  }

  @Test
  public void testExtendNonNativeErrorWithAutogeneratedConstructor() {
    test(
        lines(
            "class Error {",
            "  /** @param {string} msg */",
            "  constructor(msg) {",
            "    /** @const */ this.message = msg;",
            "  }",
            "}",
            "class C extends Error {}"), // autogenerated constructor
        lines(
            "/** @constructor */",
            "let Error = function(msg) {",
            "  /** @const */ this.message = msg;",
            "};",
            "/** @constructor",
            " * @extends {Error}",
            " */",
            "let C = function() { Error.apply(this, arguments); };",
            "$jscomp.inherits(C, Error);"));
  }

  @Test
  public void testExtendNonNativeErrorWithExplicitSuperCall() {
    test(
        lines(
            "",
            "class Error {",
            "  /** @param {string} msg */",
            "  constructor(msg) {",
            "    /** @const */ this.message = msg;",
            "  }",
            "}",
            "class C extends Error {",
            "  constructor() {",
            "    super('C error');", // explicit super() call
            "  }",
            "}"),
        lines(
            "/** @constructor */",
            "let Error = function(msg) {",
            "  /** @const */ this.message = msg;",
            "};",
            "/** @constructor",
            " * @extends {Error}",
            " */",
            "let C = function() { Error.call(this, 'C error'); };",
            "$jscomp.inherits(C, Error);"));
  }

  @Test
  public void testExtendNativeErrorWithAutogeneratedConstructor() {
    ignoreWarnings(RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR);
    test(
        "class C extends Error {}", // autogenerated constructor
        lines(
            "/** @constructor",
            " * @extends {Error}",
            " */",
            "let C = function() {",
            "  var $jscomp$tmp$error;",
            "  $jscomp$tmp$error = Error.apply(this, arguments),",
            "      this.message = $jscomp$tmp$error.message,",
            "      ('stack' in $jscomp$tmp$error) && (this.stack = $jscomp$tmp$error.stack),",
            "      this;",
            "};",
            "$jscomp.inherits(C, Error);"));
  }

  @Test
  public void testExtendNativeErrorExplicitSuperCall() {
    ignoreWarnings(RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR);
    test(
        lines(
            "",
            "class C extends Error {",
            "  constructor() {",
            "    var self = super('C error') || this;", // explicit super() call in an expression
            "  }",
            "}"),
        lines(
            "/** @constructor",
            " * @extends {Error}",
            " */",
            "let C = function() {",
            "  var $jscomp$tmp$error;",
            "  var self =",
            "      ($jscomp$tmp$error = Error.call(this, 'C error'),",
            "          this.message = $jscomp$tmp$error.message,",
            "          ('stack' in $jscomp$tmp$error) && (this.stack = $jscomp$tmp$error.stack),",
            "          this)",
            "      || this;",
            "};",
            "$jscomp.inherits(C, Error);"));
  }

  @Test
  public void testInvalidExtends_call() {
    testError("class C extends foo() {}", DYNAMIC_EXTENDS_TYPE);
  }

  @Test
  public void testInvalidExtends_functionExpression() {
    testError("class C extends function(){} {}", DYNAMIC_EXTENDS_TYPE);
  }

  @Test
  public void testInvalidExtends_logicalExpression() {
    testError("class A {}; class B {}; class C extends (foo ? A : B) {}", DYNAMIC_EXTENDS_TYPE);
  }

  @Test
  public void testExtendsInterface() {
    test(
        lines(
            "/** @interface */",
            "class D {",
            "  f() {}",
            "}",
            "/** @interface */",
            "class C extends D {",
            "  g() {}",
            "}"),
        lines(
            "/** @interface */",
            "let D = function() {};",
            "D.prototype.f = function() {};",
            "/**",
            " * @interface",
            " * @extends{D} */",
            "let C = function() {};",
            "$jscomp.inherits(C,D);",
            "C.prototype.g = function() {};"));
  }

  @Test
  public void testExtendsRecord() {
    test(
        lines(
            "/** @record */",
            "class D {",
            "  f() {}",
            "}",
            "/** @record */",
            "class C extends D {",
            "  g() {}",
            "}"),
        lines(
            "/** @record */",
            "let D = function() {};",
            "D.prototype.f = function() {};",
            "/**",
            " * @record",
            " * @extends{D} */",
            "let C = function() {};",
            "$jscomp.inherits(C,D);",
            "C.prototype.g = function() {};"));
  }

  @Test
  public void testImplementsInterface() {
    test(
        lines(
            "/** @interface */",
            "class D {",
            "  f() {}",
            "}",
            "/** @implements {D} */",
            "class C {",
            "  f() {console.log('hi');}",
            "}"),
        lines(
            "/** @interface */",
            "let D = function() {};",
            "D.prototype.f = function() {};",
            "/** @constructor @implements {D} */",
            "let C = function() {};",
            "C.prototype.f = function() {console.log('hi');};"));
  }

  @Test
  public void testSuperCall() {
    test(
        lines(
            "class D {}", //
            "class C extends D {",
            "  constructor() { SUPER: super(); } ",
            "}"),
        lines(
            "/** @constructor */",
            "let D = function() {};",
            "/** @constructor @extends {D} */",
            "let C = function() {",
            "  SUPER: D.call(this);",
            "}",
            "$jscomp.inherits(C, D);"));

    // get types we need to check
    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    ObjectType classDInstanceType = registry.getGlobalType("D").toObjectType();
    FunctionType classDConstructorType = classDInstanceType.getConstructor();
    JSType dDotCallType = classDConstructorType.getPropertyType("call");
    //
    JSType classCInstanceType = registry.getGlobalType("C");

    // D.call(this);
    Node callNode = getNodeMatchingLabel(getLastCompiler().getJsRoot(), "SUPER").getOnlyChild();
    assertNode(callNode).hasToken(Token.CALL);
    assertType(callNode.getJSType()).isEqualTo(registry.getNativeType(JSTypeNative.VOID_TYPE));

    // D.call
    Node callee = callNode.getFirstChild();
    assertNode(callee).matchesQualifiedName("D.call");
    assertType(callee.getJSType()).isEqualTo(dDotCallType);

    // D
    Node superDotGReplacement = callee.getFirstChild();
    assertNode(superDotGReplacement).matchesQualifiedName("D");
    assertType(superDotGReplacement.getJSType()).isEqualTo(classDConstructorType);

    // `this` node from `D.call(this)`
    Node thisNode = callee.getNext();
    assertNode(thisNode).hasToken(Token.THIS);
    assertType(thisNode.getJSType()).isEqualTo(classCInstanceType);
  }

  @Test
  public void testSuperCall_withArgument() {
    ignoreWarnings(TypeCheck.WRONG_ARGUMENT_COUNT);
    test(
        "class D {} class C extends D { constructor(str) { super(str); } }",
        lines(
            "/** @constructor */",
            "let D = function() {}",
            "/** @constructor @extends {D} */",
            "let C = function(str) {",
            "  D.call(this,str);",
            "}",
            "$jscomp.inherits(C, D);"));
  }

  @Test
  public void testSuperCall_withArgumentAndSubsequentCode() {
    ignoreWarnings(TypeCheck.WRONG_ARGUMENT_COUNT);
    test(
        "class D {} class C extends D { constructor(str, n) { super(str); this.n = n; } }",
        lines(
            "/** @constructor */",
            "let D = function() {}",
            "/** @constructor @extends {D} */",
            "let C = function(str, n) {",
            "  D.call(this,str);",
            "  this.n = n;",
            "}",
            "$jscomp.inherits(C, D);"));
  }

  @Test
  public void testSuperMethodCall() {
    ignoreWarnings(TypeCheck.INEXISTENT_PROPERTY);
    test(
        lines(
            "class D {}",
            "class C extends D {",
            "  constructor() { }",
            "  foo() { return super.foo(); }",
            "}"),
        lines(
            "/** @constructor */",
            "let D = function() {}",
            "/** @constructor @extends {D} */",
            "let C = function() { }",
            "$jscomp.inherits(C, D);",
            "C.prototype.foo = function() {",
            "  return D.prototype.foo.call(this);",
            "}"));
  }

  @Test
  public void testSuperMethodCall_withArgument() {
    ignoreWarnings(TypeCheck.INEXISTENT_PROPERTY);
    test(
        lines(
            "class D {}",
            "class C extends D {",
            "  constructor() {}",
            "  foo(bar) { return super.foo(bar); }",
            "}"),
        lines(
            "/** @constructor */",
            "let D = function() {}",
            "/** @constructor @extends {D} */",
            "let C = function() {};",
            "$jscomp.inherits(C, D);",
            "C.prototype.foo = function(bar) {",
            "  return D.prototype.foo.call(this, bar);",
            "}"));
  }

  @Test
  public void testSuperCall_inSubclassInsideMethod() {
    test(
        "class C { method() { class D extends C { constructor() { super(); }}}}",
        lines(
            "/** @constructor */",
            "let C = function() {}",
            "C.prototype.method = function() {",
            "  /** @constructor @extends {C} */",
            "  let D = function() {",
            "    C.call(this);",
            "  }",
            "  $jscomp.inherits(D, C);",
            "};"));
  }

  @Test
  public void testSuperKnownNotToChangeThis() {
    test(
        lines(
            "class D {",
            "  /** @param {string} str */",
            "  constructor(str) {",
            "    this.str = str;",
            "    /** @type {?} */ this.n;",
            "    return;", // Empty return should not trigger this-changing behavior.
            "  }",
            "}",
            "class C extends D {",
            "  /**",
            "   * @param {string} str",
            "   * @param {number} n",
            "   */",
            "  constructor(str, n) {",
            // This is nuts, but confirms that super() used in an expression works.
            "    super(str).n = n;",
            // Also confirm that an existing empty return is handled correctly.
            "    return;",
            "  }",
            "}"),
        lines(
            "/**",
            " * @constructor",
            " */",
            "let D = function(str) {",
            "  this.str = str;",
            "  /** @type {?} */ this.n;",
            "  return;",
            "}",
            "/**",
            " * @constructor @extends {D}",
            " */",
            "let C = function(str, n) {",
            "  (D.call(this,str), this).n = n;", // super() returns `this`.
            "  return;",
            "}",
            "$jscomp.inherits(C, D);"));
  }

  @Test
  public void testSuperMightChangeThis() {
    // Class D is unknown, so we must assume its constructor could change `this`.
    enableClosurePass();
    ignoreWarnings(FunctionTypeBuilder.RESOLVED_TAG_EMPTY);
    test(
        lines(
            "goog.forwardDeclare('D');",
            "class C extends D {",
            "  constructor(str, n) {",
            // This is nuts, but confirms that super() used in an expression works.
            "    super(str).n = n;",
            // Also confirm that an existing empty return is handled correctly.
            "    return;",
            "  }",
            "}"),
        lines(
            "/** @constructor @extends {D} */",
            "let C = function(str, n) {",
            "  var $jscomp$super$this;",
            "  ($jscomp$super$this = D.call(this,str) || this).n = n;",
            "  return $jscomp$super$this;", // Duplicate because of existing return statement.
            "  return $jscomp$super$this;",
            "}",
            "$jscomp.inherits(C, D);"));
  }

  @Test
  public void testAlternativeSuperCalls_withExplicitSuperclassCtor() {
    test(
        lines(
            "class D {",
            "  /** @param {string} name */",
            "  constructor(name) {",
            "    this.name = name;",
            "  }",
            "}",
            "class C extends D {",
            "  /** @param {string} str",
            "   * @param {number} n */",
            "  constructor(str, n) {",
            "    if (n >= 0) {",
            "      super('positive: ' + str);",
            "    } else {",
            "      super('negative: ' + str);",
            "    }",
            "    this.n = n;",
            "  }",
            "}"),
        lines(
            "/** @constructor */",
            "let D = function(name) {",
            "  this.name = name;",
            "}",
            "/** @constructor @extends {D} */",
            "let C = function(str, n) {",
            "  if (n >= 0) {",
            "    D.call(this, 'positive: ' + str);",
            "  } else {",
            "    D.call(this, 'negative: ' + str);",
            "  }",
            "  this.n = n;",
            "}",
            "$jscomp.inherits(C, D);"));
  }

  @Test
  public void testAlternativeSuperCalls_withUnknkownSuperclass() {
    // Class being extended is unknown, so we must assume super() could change the value of `this`.
    enableClosurePass();
    ignoreWarnings(FunctionTypeBuilder.RESOLVED_TAG_EMPTY);
    test(
        lines(
            "goog.forwardDeclare('D');",
            "class C extends D {",
            "  /** @param {string} str",
            "   * @param {number} n */",
            "  constructor(str, n) {",
            "    if (n >= 0) {",
            "      super('positive: ' + str);",
            "    } else {",
            "      super('negative: ' + str);",
            "    }",
            "    this.n = n;",
            "  }",
            "}"),
        lines(
            "/** @constructor @extends {D} */",
            "let C = function(str, n) {",
            "  var $jscomp$super$this;",
            "  if (n >= 0) {",
            "    $jscomp$super$this = D.call(this, 'positive: ' + str) || this;",
            "  } else {",
            "    $jscomp$super$this = D.call(this, 'negative: ' + str) || this;",
            "  }",
            "  $jscomp$super$this.n = n;",
            "  return $jscomp$super$this;",
            "}",
            "$jscomp.inherits(C, D);"));
  }

  @Test
  public void testComputedSuper() {
    test(
        lines(
            "/** @unrestricted */",
            "class Foo {",
            "  ['m']() { return 1; }",
            "}",
            "",
            "/** @unrestricted */",
            "class Bar extends Foo {",
            "  ['m']() {",
            "    RETURN: return super['m']() + 1;",
            "  }",
            "}"),
        lines(
            "/** @constructor @unrestricted */",
            "let Foo = function() {};",
            "Foo.prototype['m'] = function() { return 1; };",
            "/** @constructor @unrestricted @extends {Foo} */",
            "let Bar = function() { Foo.apply(this, arguments); };",
            "$jscomp.inherits(Bar, Foo);",
            "Bar.prototype['m'] = function () {",
            "  RETURN: return Foo.prototype['m'].call(this) + 1;",
            "};"));

    // get types we need to check
    JSTypeRegistry registry = getLastCompiler().getTypeRegistry();
    ObjectType classFooInstanceType = registry.getGlobalType("Foo").toObjectType();
    FunctionType classFooConstructorType = classFooInstanceType.getConstructor();
    ObjectType classFooPrototypeType = classFooConstructorType.getPrototype();
    JSType classBarInstanceType = registry.getGlobalType("Bar");

    // Foo.prototype['m'].call(this)
    Node callNode =
        getNodeMatchingLabel(getLastCompiler().getJsRoot(), "RETURN").getFirstFirstChild();
    assertNode(callNode).hasToken(Token.CALL);
    assertType(callNode.getJSType()).isEqualTo(registry.getNativeType(JSTypeNative.UNKNOWN_TYPE));

    // Foo.prototype['m'].call
    Node callee = callNode.getFirstChild();
    assertNode(callee).hasToken(Token.GETPROP);
    assertType(callee.getJSType()).isEqualTo(registry.getNativeType(JSTypeNative.UNKNOWN_TYPE));

    // Foo.prototype['m']
    Node property = callee.getFirstChild();
    assertNode(property).hasToken(Token.GETELEM);
    assertType(callee.getJSType()).isEqualTo(registry.getNativeType(JSTypeNative.UNKNOWN_TYPE));

    // Foo.prototype
    Node prototype = property.getFirstChild();
    assertNode(prototype).matchesQualifiedName("Foo.prototype").hasOriginalName("super");
    assertType(prototype.getJSType()).isEqualTo(classFooPrototypeType);

    // Foo
    Node superDotGReplacement = prototype.getFirstChild();
    assertNode(superDotGReplacement).matchesQualifiedName("Foo");
    assertType(superDotGReplacement.getJSType()).isEqualTo(classFooConstructorType);

    // `this` node from `Foo.prototype['m'].call(this)`
    Node thisNode = callee.getNext();
    assertNode(thisNode).hasToken(Token.THIS);
    assertType(thisNode.getJSType()).isEqualTo(classBarInstanceType);
  }

  @Test
  public void testSuperMethodInGetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        lines(
            "class Base {",
            "  method() {",
            "    return 5;",
            "  }",
            "}",
            "",
            "class Subclass extends Base {",
            "  constructor() {",
            "    super();",
            "  }",
            "",
            "  get x() {",
            "    return super.method();",
            "  }",
            "}"),
        lines(
            "/** @constructor */",
            "let Base = function() {};",
            "Base.prototype.method = function() { return 5; };",
            "",
            "/** @constructor @extends {Base} */",
            "let Subclass = function() { Base.call(this); };",
            "",
            "$jscomp.inherits(Subclass, Base);",
            "$jscomp.global.Object.defineProperties(Subclass.prototype, {",
            "  x: {",
            "    configurable:true,",
            "    enumerable:true,",
            "    get: function() { return Base.prototype.method.call(this); },",
            "  }",
            "});"));
  }

  @Test
  public void testOverrideOfGetterFromInterface() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        lines(
            "/** @interface */",
            "class Int {",
            "  /** @return {number} */",
            "  get x() {}",
            "}",
            "",
            "/** @implements {Int} */",
            "class C {",
            "  /** @override @return {number} */",
            "  get x() {}",
            "}"),
        lines(
            "/** @interface */",
            "let Int = function() {};",
            "$jscomp.global.Object.defineProperties(Int.prototype, {",
            "  x: {",
            "    configurable:true,",
            "    enumerable:true,",
            "    /** @return {number} */",
            "    get: function() {},",
            "  }",
            "});",
            "",
            "/** @constructor @implements {Int} */",
            "let C = function() {};",
            "",
            "$jscomp.global.Object.defineProperties(C.prototype, {",
            "  x: {",
            "    configurable:true,",
            "    enumerable:true,",
            "    /** @override @return {number}  */",
            "    get: function() {},",
            "  }",
            "});"));
  }

  @Test
  public void testSuperMethodInSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        lines(
            "class Base {",
            "  constructor() {}",
            "  method() {",
            "    var x = 5;",
            "  }",
            "}",
            "",
            "class Subclass extends Base {",
            "  constructor() {",
            "    super();",
            "  }",
            "",
            "  set x(value) {",
            "    super.method();",
            "  }",
            "}"),
        lines(
            "/** @constructor */",
            "let Base = function() {};",
            "Base.prototype.method = function() { var x = 5; };",
            "",
            "/** @constructor @extends {Base} */",
            "let Subclass = function() { Base.call(this); };",
            "",
            "$jscomp.inherits(Subclass, Base);",
            "$jscomp.global.Object.defineProperties(Subclass.prototype, {",
            "  x: {",
            "    configurable:true,",
            "    enumerable:true,",
            "    set: function(value) { Base.prototype.method.call(this); },",
            "  }",
            "});"));
  }

  @Test
  public void testExtendNativeClass_withExplicitConstructor() {
    // Function and other native classes cannot be correctly extended in transpiled form.
    // Test both explicit and automatically generated constructors.
    test(
        lines(
            "class FooPromise extends Promise {",
            "  /** @param {string} msg */",
            "  constructor(callback, msg) {",
            "    super(callback);",
            "    this.msg = msg;",
            "  }",
            "}"),
        lines(
            "/**",
            " * @constructor",
            " * @extends {Promise}",
            " */",
            "let FooPromise = function(callback, msg) {",
            "  var $jscomp$super$this;",
            "  $jscomp$super$this = $jscomp.construct(Promise, [callback], this.constructor)",
            "  $jscomp$super$this.msg = msg;",
            "  return $jscomp$super$this;",
            "}",
            "$jscomp.inherits(FooPromise, Promise);",
            ""));
  }

  @Test
  public void testExtendNativeClass_withImplicitConstructor() {
    test(
        "class FooPromise extends Promise {}",
        lines(
            "/**",
            " * @constructor",
            " * @extends {Promise}",
            " */",
            "let FooPromise = function() {",
            "  return $jscomp.construct(Promise, arguments, this.constructor)",
            "}",
            "$jscomp.inherits(FooPromise, Promise);",
            ""));
  }

  @Test
  public void testExtendObject_replaceSuperCallWithThis() {
    // Object can be correctly extended in transpiled form, but we don't want or need to call
    // the `Object()` constructor in place of `super()`. Just replace `super()` with `this` instead.
    // Test both explicit and automatically generated constructors.
    test(
        lines(
            "class Foo extends Object {",
            "  /** @param {string} msg */",
            "  constructor(msg) {",
            "    super();",
            "    this.msg = msg;",
            "  }",
            "}"),
        lines(
            "/**",
            " * @constructor @extends {Object}",
            " */",
            "let Foo = function(msg) {",
            "  this;", // super() replaced with its return value
            "  this.msg = msg;",
            "};",
            "$jscomp.inherits(Foo, Object);"));
  }

  @Test
  public void testExtendObject_withImplicitConstructor() {
    test(
        "class Foo extends Object {}",
        lines(
            "/**",
            " * @constructor @extends {Object}",
            " */",
            "let Foo = function() {",
            "  this;", // super.apply(this, arguments) replaced with its return value
            "};",
            "$jscomp.inherits(Foo, Object);"));
  }

  @Test
  public void testExtendNonNativeObject_withSuperCall() {
    // No special handling when Object is redefined.
    ignoreWarnings(TypeCheck.ES5_CLASS_EXTENDING_ES6_CLASS, FunctionTypeBuilder.TYPE_REDEFINITION);
    test(
        lines(
            "class Object {}",
            "class Foo extends Object {",
            "  /** @param {string} msg */",
            "  constructor(msg) {",
            "    super();",
            "    this.msg = msg;",
            "  }",
            "}"),
        lines(
            "/**",
            " * @constructor",
            " */",
            "let Object = function() {",
            "};",
            "/**",
            " * @constructor @extends {Object}",
            " */",
            "let Foo = function(msg) {",
            "  Object.call(this);",
            "  this.msg = msg;",
            "};",
            "$jscomp.inherits(Foo, Object);"));
  }

  @Test
  public void testExtendNonNativeObject_withImplicitConstructor() {
    ignoreWarnings(TypeCheck.ES5_CLASS_EXTENDING_ES6_CLASS, FunctionTypeBuilder.TYPE_REDEFINITION);
    test(
        lines(
            "class Object {}", //
            "class Foo extends Object {}"), // autogenerated constructor
        lines(
            "/**",
            " * @constructor",
            " */",
            "let Object = function() {",
            "};",
            "/**",
            " * @constructor @extends {Object}",
            " */",
            "let Foo = function() {",
            "  Object.apply(this, arguments);", // all arguments passed on to super()
            "};",
            "$jscomp.inherits(Foo, Object);"));
  }

  @Test
  public void testMultiNameClass_inVarDeclaration() {
    test(
        "var F = class G {}",
        lines(
            "/** @constructor */",
            "const testcode$classdecl$var0 = function(){};",
            "/** @constructor */",
            "var F = testcode$classdecl$var0;"));
  }

  @Test
  public void testMultiNameClass_inAssignment() {
    test(
        "F = class G {}",
        lines(
            "/** @constructor */",
            "const testcode$classdecl$var0 = function(){};",
            "/** @constructor */",
            "F = testcode$classdecl$var0;"));
  }

  @Test
  public void testClassNested() {
    test(
        "class C { f() { class D {} } }",
        lines(
            "/** @constructor */",
            "let C = function() {};",
            "C.prototype.f = function() {",
            "  /** @constructor */",
            "  let D = function() {}",
            "};"));
  }

  @Test
  public void testClassNestedWithSuperclass() {
    test(
        "class C { f() { class D extends C {} } }",
        lines(
            "/** @constructor */",
            "let C = function() {};",
            "C.prototype.f = function() {",
            "  /**",
            " * @constructor",
            " * @extends{C} */",
            "  let D = function() {",
            "    C.apply(this, arguments); ",
            "  };",
            "  $jscomp.inherits(D, C);",
            "};"));
  }

  @Test
  public void testSuperGetProp() {
    test(
        "class D { d() {} } class C extends D { f() {var i = super.d;} }",
        lines(
            "/** @constructor */",
            "let D = function() {};",
            "D.prototype.d = function() {};",
            "/**",
            " * @constructor",
            " * @extends{D} */",
            "let C = function() {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.prototype.f = function() {",
            "  var i = D.prototype.d;",
            "};"));
  }

  @Test
  public void testSuperGetElem() {
    test(
        lines(
            "/** @unrestricted */",
            "class D { ['d']() {} }",
            "/** @unrestricted */",
            "class C extends D { f() {var i = super['d'];} }"),
        lines(
            "/** @constructor @unrestricted */",
            "let D = function() {};",
            "D.prototype['d'] = function() {};",
            "/**",
            " * @constructor @unrestricted",
            " * @extends{D} */",
            "let C = function() {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.prototype.f = function() {",
            "  var i = D.prototype['d'];",
            "};"));
  }

  @Test
  public void testSuperGetPropInStaticMethod() {
    // NOTE: super.d refers to a *static* property D.d (which is not defined), rather than the
    // instance propery D.prototype.d, which is defined.
    ignoreWarnings(TypeCheck.INEXISTENT_PROPERTY);
    test(
        lines(
            "class D { d() {} }", //
            "class C extends D { static f() {var i = super.d;} }"),
        lines(
            "/** @constructor */",
            "let D = function() {};",
            "D.prototype.d = function() {};",
            "/**",
            " * @constructor",
            " * @extends{D} */",
            "let C = function() {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.f = function() {",
            "  var i = D.d;",
            "};"));
  }

  @Test
  public void testSuperGetElemInStaticMethod() {
    test(
        lines(
            "/** @unrestricted */",
            "class D { ['d']() {}}",
            "/** @unrestricted */",
            "class C extends D { static f() {var i = super['d'];} }"),
        lines(
            "/** @constructor @unrestricted */",
            "let D = function() {};",
            "D.prototype['d'] = function() {};",
            "/**",
            " * @constructor @unrestricted",
            " * @extends{D} */",
            "let C = function() {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.f = function() {",
            "  var i = D['d'];",
            "};"));
  }

  @Test
  public void testSuperGetProp_returnUndefinedProperty() {
    ignoreWarnings(TypeCheck.INEXISTENT_PROPERTY);
    test(
        lines(
            "class D {}", //
            "class C extends D { f() {return super.s;} }"),
        lines(
            "/** @constructor */",
            "let D = function() {};",
            "/**",
            " * @constructor",
            " * @extends{D} */",
            "let C = function() {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.prototype.f = function() {",
            "  return D.prototype.s;",
            "};"));
  }

  @Test
  public void testSuperGetProp_useUndefinedPropInCall() {
    ignoreWarnings(TypeCheck.INEXISTENT_PROPERTY);
    test(
        lines(
            "class D {}", //
            "class C extends D { f() { m(super.s);} }"),
        lines(
            "/** @constructor */",
            "let D = function() {};",
            "/**",
            " * @constructor",
            " * @extends{D} */",
            "let C = function() {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.prototype.f = function() {",
            "  m(D.prototype.s);",
            "};"));
  }

  @Test
  public void testSuperGetProp_dereferenceUndefinedProperty() {
    ignoreWarnings(TypeCheck.INEXISTENT_PROPERTY);
    test(
        lines(
            "class D {}", //
            "class C extends D { foo() { return super.m.foo();} }"),
        lines(
            "/** @constructor */",
            "let D = function() {};",
            "/**",
            " * @constructor",
            " * @extends{D} */",
            "let C = function() {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.prototype.foo = function() {",
            "  return D.prototype.m.foo();",
            "};"));
  }

  @Test
  public void testSuperGet_dereferenceUndefinedPropertyInStaticMethod() {
    ignoreWarnings(TypeCheck.INEXISTENT_PROPERTY);
    test(
        lines(
            "class D {}", //
            "class C extends D { static foo() { return super.m.foo();} }"),
        lines(
            "/** @constructor */",
            "let D = function() {};",
            "/**",
            " * @constructor",
            " * @extends{D} */",
            "let C = function() {",
            "  D.apply(this, arguments); ",
            "};",
            "$jscomp.inherits(C, D);",
            "C.foo = function() {",
            "  return D.m.foo();",
            "};"));
  }

  @Test
  public void testStaticThis() {
    test(
        "class F { static f() { return this; } }",
        lines(
            "/** @constructor */ let F = function() {}",
            "/** @this {?} */ F.f = function() { return this; };"));
  }

  @Test
  public void testStaticMethods() {
    test(
        "class C { static foo() {} }",
        "/** @constructor */ let C = function() {}; C.foo = function() {};");
  }

  @Test
  public void testStaticMethods_withSameNamedInstanceMethod() {
    test(
        "class C { static foo() {}; foo() {} }",
        lines(
            "/** @constructor */",
            "let C = function() {};",
            "",
            "C.foo = function() {};",
            "",
            "C.prototype.foo = function() {};"));
  }

  @Test
  public void testStaticMethods_withCallInsideInstanceMethod() {
    test(
        "class C { static foo() {}; bar() { C.foo(); } }",
        lines(
            "/** @constructor */",
            "let C = function() {};",
            "",
            "C.foo = function() {};",
            "",
            "C.prototype.bar = function() { C.foo(); };"));
  }

  @Test
  public void testStaticInheritance() {
    test(
        lines(
            "class D {",
            "  static f() {}",
            "}",
            "class C extends D { constructor() {} }",
            "C.f();"),
        lines(
            "/** @constructor */",
            "let D = function() {};",
            "D.f = function () {};",
            "/** @constructor @extends{D} */",
            "let C = function() {};",
            "$jscomp.inherits(C, D);",
            "C.f();"));
  }

  @Test
  public void testStaticInheritance_withSameNamedInstanceMethod() {
    test(
        lines(
            "class D {",
            "  static f() {}",
            "}",
            "class C extends D {",
            "  constructor() {}",
            "  f() {}",
            "}",
            "C.f();"),
        lines(
            "/** @constructor */",
            "let D = function() {};",
            "D.f = function() {};",
            "/** @constructor @extends{D} */",
            "let C = function() { };",
            "$jscomp.inherits(C, D);",
            "C.prototype.f = function() {};",
            "C.f();"));
  }

  @Test
  public void testStaticInheritance_overrideStaticMethod() {
    test(
        lines(
            "class D {",
            "  static f() {}",
            "}",
            "class C extends D {",
            "  constructor() {}",
            "  static f() {}",
            "  g() {}",
            "}"),
        lines(
            "/** @constructor */",
            "let D = function() {};",
            "D.f = function() {};",
            "/** @constructor @extends{D} */",
            "let C = function() { };",
            "$jscomp.inherits(C, D);",
            "C.f = function() {};",
            "C.prototype.g = function() {};"));
  }

  @Test
  public void testInheritFromExterns() {
    test(
        externs(
            lines(
                "/** @constructor */ function ExternsClass() {}",
                "ExternsClass.m = function() {};")),
        srcs(linesWithStubs("class CodeClass extends ExternsClass {}")),
        expected(
            linesWithStubs(
                "/** @constructor",
                " * @extends {ExternsClass}",
                " */",
                "let CodeClass = function() {",
                "  return ExternsClass.apply(this, arguments) || this;",
                "};",
                "$jscomp.inherits(CodeClass,ExternsClass)")));
  }

  // Make sure we don't crash on this code.
  // https://github.com/google/closure-compiler/issues/752
  @Test
  public void testGithub752() {
    test(
        "function f() { var a = b = class {};}",
        lines(
            "function f() {",
            "  /** @constructor */",
            "  const testcode$classdecl$var0 = function() {};",
            "  var a = b = testcode$classdecl$var0;",
            "}"));
  }

  @Test
  public void testGithub752b() {
    test(
        "var ns = {}; function f() { var self = ns.Child = class {};}",
        lines(
            "var ns = {};",
            "function f() {",
            "  /** @constructor */",
            "  const testcode$classdecl$var0 = function() {};",
            "  var self = ns.Child = testcode$classdecl$var0",
            "}"));
  }

  /**
   * Getters and setters are supported, both in object literals and in classes, but only if the
   * output language is ES5.
   */
  @Test
  public void testEs5GettersAndSettersClasses_withSingleGetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { get value() { return 0; } }",
        lines(
            "/** @constructor */",
            "let C = function() {};",
            "$jscomp.global.Object.defineProperties(C.prototype, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    get: function() {",
            "      return 0;",
            "    }",
            "  }",
            "});"));
  }

  @Test
  public void testEs5GettersAndSettersClasses_withSingleSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { set value(val) { var internalVal = val; } }",
        lines(
            "/** @constructor */",
            "let C = function() {};",
            "$jscomp.global.Object.defineProperties(C.prototype, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    set: function(val) {",
            "      var internalVal = val;",
            "    }",
            "  }",
            "});"));
  }

  @Test
  public void testEs5GettersAndSettersClasses_withGetterSetterPair() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        lines(
            "/** @unrestricted */",
            "class C {",
            "  set value(val) {",
            "    this.internalVal = val;",
            "  }",
            "  get value() {",
            "    return this.internalVal;",
            "  }",
            "}"),
        lines(
            "/** @constructor @unrestricted */",
            "let C = function() {};",
            "$jscomp.global.Object.defineProperties(C.prototype, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    set: function(val) {",
            "      this.internalVal = val;",
            "    },",
            "    get: function() {",
            "      return this.internalVal;",
            "    }",
            "  }",
            "});"));
  }

  @Test
  public void testEs5GettersAndSettersClasses_withTwoGetters() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        lines(
            "class C {",
            "  get alwaysTwo() {",
            "    return 2;",
            "  }",
            "",
            "  get alwaysThree() {",
            "    return 3;",
            "  }",
            "}"),
        lines(
            "/** @constructor */",
            "let C = function() {};",
            "$jscomp.global.Object.defineProperties(C.prototype, {",
            "  alwaysTwo: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    get: function() {",
            "      return 2;",
            "    }",
            "  },",
            "  alwaysThree: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    get: function() {",
            "      return 3;",
            "    }",
            "  },",
            "});"));
  }

  @Test
  public void testEs5GettersAndSettersOnClassesWithClassSideInheritance() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { static get value() {} }  class D extends C { static get value() {} }",
        lines(
            "/** @constructor */",
            "let C = function() {};",
            "/** @nocollapse */",
            "C.value;",
            "$jscomp.global.Object.defineProperties(C, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    get: function() {}",
            "  }",
            "});",
            "/** @constructor",
            " * @extends {C}",
            " */",
            "let D = function() {",
            "  C.apply(this, arguments); ",
            "};",
            "/** @nocollapse */",
            "D.value;",
            "$jscomp.inherits(D, C);",
            "$jscomp.global.Object.defineProperties(D, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    get: function() {}",
            "  }",
            "});"));
  }

  /** Check that the types from the getter/setter are copied to the declaration on the prototype. */
  @Test
  public void testEs5GettersAndSettersClassesWithTypes_withSingleGetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { /** @return {number} */ get value() { return 0; } }",
        lines(
            "/** @constructor */",
            "let C = function() {};",
            "$jscomp.global.Object.defineProperties(C.prototype, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /**",
            "     * @return {number}",
            "     */",
            "    get: function() {",
            "      return 0;",
            "    }",
            "  }",
            "});"));
  }

  @Test
  public void testEs5GettersAndSettersClassesWithTypes_withSingleSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { /** @param {string} v */ set value(v) { } }",
        lines(
            "/** @constructor */",
            "let C = function() {};",
            "$jscomp.global.Object.defineProperties(C.prototype, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /**",
            "     * @param {string} v",
            "     */",
            "    set: function(v) {}",
            "  }",
            "});"));
  }

  @Test
  public void testEs5GettersAndSettersClassesWithTypes_withConflictingGetterSetterType() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    testWarning(
        lines(
            "class C {",
            "  /** @return {string} */",
            "  get value() { }",
            "",
            "  /** @param {number} v */",
            "  set value(v) { }",
            "}"),
        TypeCheck.CONFLICTING_GETTER_SETTER_TYPE);

    // Also verify what the actual output is
    disableTypeCheck();
    disableTypeInfoValidation();

    test(
        lines(
            "class C {",
            "  /** @return {string} */",
            "  get value() { }",
            "",
            "  /** @param {number} v */",
            "  set value(v) { }",
            "}"),
        lines(
            "/** @constructor */",
            "let C = function() {};",
            "$jscomp.global.Object.defineProperties(C.prototype, {",
            "  value: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    /**",
            "     * @return {string}",
            "     */",
            "    get: function() {},",
            "   /**",
            "    * @param {number} v",
            "    */",
            "    set:function(v){}",
            "  }",
            "});"));
  }

  @Test
  public void testEs5GetterWithExport() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { /** @export @return {string} */ get foo() {} }",
        lines(
            "/**",
            " * @constructor",
            " */",
            "let C = function() {}",
            "",
            "$jscomp.global.Object.defineProperties(C.prototype, {",
            "  foo: {",
            "    configurable: true,",
            "    enumerable: true,",
            "",
            "    /**",
            "     * @return {string}",
            "     * @export",
            "     */",
            "    get: function() {},",
            "  }",
            "});"));
  }

  @Test
  public void testEs5SetterWithExport() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { /** @export @param {string} x */ set foo(x) {} }",
        lines(
            "/**",
            " * @constructor",
            " */",
            "let C = function() {}",
            "",
            "$jscomp.global.Object.defineProperties(C.prototype, {",
            "  foo: {",
            "    configurable: true,",
            "    enumerable: true,",
            "",
            "    /**",
            "     * @param {string} x",
            "     * @export",
            "     */",
            "    set: function(x) {},",
            "  }",
            "});"));
  }

  /** @bug 20536614 */
  @Test
  public void testStaticGetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { static get foo() {} }",
        lines(
            "/** @constructor */",
            "let C = function() {};",
            "/** @nocollapse */",
            "C.foo;",
            "$jscomp.global.Object.defineProperties(C, {",
            "  foo: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    get: function() {}",
            "  }",
            "})"));
  }

  @Test
  public void testStaticGetter_withClassSideInheritance() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        lines(
            "class C { static get foo() {} }", //
            "class Sub extends C {}"),
        lines(
            "/** @constructor */",
            "let C = function() {};",
            "/** @nocollapse */",
            "C.foo;",
            "$jscomp.global.Object.defineProperties(C, {",
            "  foo: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    get: function() {}",
            "  }",
            "})",
            "",
            "/** @constructor",
            " * @extends {C}",
            " */",
            "let Sub = function() {",
            "  C.apply(this, arguments);",
            "};",
            "$jscomp.inherits(Sub, C)"));
  }

  @Test
  public void testStaticSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { static set foo(x) {} }",
        lines(
            "/** @constructor */",
            "let C = function() {};",
            "/** @nocollapse */",
            "C.foo;",
            "$jscomp.global.Object.defineProperties(C, {",
            "  foo: {",
            "    configurable: true,",
            "    enumerable: true,",
            "    set: function(x) {}",
            "  }",
            "});"));
  }

  @Test
  public void testClassStaticComputedProps_withSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "/** @unrestricted */ class C { static set [se()](val) {}}",
        lines(
            "/** @constructor @unrestricted */",
            "let C = function() {};",
            "$jscomp.global.Object.defineProperty(C, se(), {",
            "  configurable: true,",
            "  enumerable: true,",
            "  set: function(val) {},",
            "});"));
  }

  @Test
  public void testClassStaticComputedProps_withGetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "/** @unrestricted */ class C { static get [se()]() {}}",
        lines(
            "/** @constructor @unrestricted */",
            "let C = function() {};",
            "$jscomp.global.Object.defineProperty(C, se(), {",
            "  configurable: true,",
            "  enumerable: true,",
            "  get: function() {},",
            "});"));
  }

  @Test
  public void testClassStaticComputedProps_withGetterSetterPair() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "/** @unrestricted */ class C { static get [se()]() {} static set [se()](val) {}}",
        lines(
            "/** @constructor @unrestricted */",
            "let C = function() {};",
            "",
            "$jscomp.global.Object.defineProperty(",
            "  C,",
            "  se(), {",
            "    configurable: true,",
            "    enumerable: true,",
            "    get: function() {},",
            "  }",
            ");",
            "",
            "$jscomp.global.Object.defineProperty(C, se(), {",
            "  configurable: true,",
            "  enumerable: true,",
            "  set: function(val) {},",
            "});"));
  }

  @Test
  public void testSuperDotPropWithoutExtends() {
    testError(
        lines(
            "class C {", //
            "  foo() { return super.x; }",
            "}",
            ""),
        CANNOT_CONVERT_YET);
  }

  @Test
  public void testClassComputedPropGetterAndSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        lines(
            "/** @unrestricted */",
            "class C {",
            "  /** @return {boolean} */",
            "  get [foo]() {}",
            "  /** @param {boolean} val */",
            "  set [foo](val) {}",
            "}"),
        lines(
            "/** @constructor @unrestricted */",
            "let C = function() {};",
            "",
            "$jscomp.global.Object.defineProperty(C.prototype, foo, {",
            "  configurable:true,",
            "  enumerable:true,",
            "  get: function() {},",
            "});",
            "",
            "$jscomp.global.Object.defineProperty(C.prototype, foo, {",
            "  configurable:true,",
            "  enumerable:true,",
            "  set: function(val) {},",
            "});"));
  }

  @Test
  public void testClassComputedPropGetterAndSetter_withConflictingGetterSetterType() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    testNoWarning(
        lines(
            "/** @unrestricted */",
            "class C {",
            "  /** @return {boolean} */",
            "  get [foo]() {}",
            "  /** @param {string} val */",
            "  set [foo](val) {}",
            "}"));
  }

  @Test
  public void testClassComputedPropGetterAndSetter_mixedWithOtherMembers() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        externs(
            lines(
                "function one() {}", //
                "function two() {}",
                "function three() {}")),
        srcs(
            linesWithStubs(
                "/** @unrestricted */",
                "class C {",
                "  get [one()]() { return true; }",
                "  get a() {}",
                "  [two()]() {}",
                "  get b() {}",
                "  c() {}",
                "  set [three()](val) { return true; }",
                "}")),
        expected(
            linesWithStubs(
                "/** @unrestricted  @constructor */",
                "let C = function() {};",
                "$jscomp.global.Object.defineProperty(C.prototype, one(), {",
                "  configurable: true,",
                "  enumerable: true,",
                "  get: function() {",
                "    return true",
                "  },",
                "});",
                "C.prototype[two()] = function() {};",
                "C.prototype.c = function() {};",
                "$jscomp.global.Object.defineProperty(C.prototype, three(), {",
                "  configurable: true,",
                "  enumerable: true,",
                "  set: function(val) {",
                "    return true",
                "  },",
                "});",
                "$jscomp.global.Object.defineProperties(C.prototype, {",
                "  a: {",
                "    configurable: true,",
                "    enumerable: true,",
                "    get: function() {},",
                "  },",
                "  b: {",
                "    configurable: true,",
                "    enumerable: true,",
                "    get: function() {},",
                "  }",
                "});",
                "")));
  }

  @Test
  public void testComputedPropClass() {
    test(
        "/** @unrestricted */ class C { [foo]() { alert(1); } }",
        lines(
            "/** @constructor @unrestricted */",
            "let C = function() {};",
            "C.prototype[foo] = function() { alert(1); };"));
  }

  @Test
  public void testStaticComputedPropClass() {
    test(
        "/** @unrestricted */ class C { static [foo]() { alert(2); } }",
        lines(
            "/** @constructor @unrestricted */",
            "let C = function() {};",
            "C[foo] = function() { alert(2); };"));
  }

  @Test
  public void testComputedPropGeneratorMethods() {
    test(
        "/** @unrestricted */ class C { *[foo]() { yield 1; } }",
        lines(
            "/** @constructor @unrestricted */",
            "let C = function() {};",
            "C.prototype[foo] = function*() { yield 1; };"));
  }

  @Test
  public void testStaticComputedPropGeneratorMethods() {
    test(
        "/** @unrestricted */ class C { static *[foo]() { yield 2; } }",
        lines(
            "/** @constructor @unrestricted */",
            "let C = function() {};",
            "C[foo] = function*() { yield 2; };"));
  }

  @Test
  public void testClassGenerator() {
    test(
        "class C { *foo() { yield 1; } }",
        lines(
            "/** @constructor */",
            "let C = function() {};",
            "C.prototype.foo = function*() { yield 1;};"));
    assertThat(getLastCompiler().getInjected()).isEmpty();
  }

  @Test
  public void testClassInsideCast() {
    test(
        "const Foo = /** @type {?} */ (class {});",
        lines(
            "/** @constructor */ const testcode$classdecl$var0=function(){};",
            "const Foo= /** @type {?} */ (testcode$classdecl$var0)"));
  }

  @Test
  public void testClassWithSuperclassInsideCast() {
    test(
        lines(
            "function mixin(baseClass) {",
            "  return /** @type {?} */ (class extends baseClass {",
            "    constructor() {",
            "      super();",
            "    }",
            "  });",
            "}"),
        lines(
            "function mixin(baseClass){",
            "  /**",
            "   * @constructor",
            "   * @extends {baseClass}",
            "   */",
            "  const testcode$classdecl$var0 = function(){",
            "    return baseClass.call(this) || this",
            "  };",
            "  $jscomp.inherits(testcode$classdecl$var0, baseClass);",
            "  return /** @type {?} */ (testcode$classdecl$var0);",
            "}"));
  }

  /**
   * Tests that we have reasonable source information on the transpiled nodes.
   *
   * <p>We do this by comparing the source information of nodes on the transpiled AST with nodes on
   * a different parsed-but-not-transpiled AST.
   */
  @Test
  public void testSimpleClassStatement_hasCorrectSourceInfo() {
    String source = "class C { }";
    String expected = "/** @constructor */ let C = function() {};";

    AstPair asts = testAndReturnAsts(source, expected);
    Node sourceRoot = asts.sourceRoot;
    Node expectedRoot = asts.expectedRoot;

    // Get nodes from the original, pre-transpiled AST
    // `C` from `class C { }`
    Node sourceCName = getNodeWithName(sourceRoot, "C");
    Node sourceClass = sourceCName.getParent();

    // `C` from `let C = function() {};` matches `C` from `class C { }`
    Node expectedCName = getNodeWithName(expectedRoot, "C");
    assertNode(expectedCName).matchesQualifiedName("C").hasEqualSourceInfoTo(sourceCName);

    // function() {}
    Node expectedFunction = expectedCName.getOnlyChild();
    assertNode(expectedFunction).hasToken(Token.FUNCTION).hasEqualSourceInfoTo(sourceClass);
  }

  @Test
  public void testClassStatementWithConstructor_hasCorrectSourceInfo() {
    String source = "class C { constructor() {} }";
    String expected = "/** @constructor */ let C = function() {};";

    AstPair asts = testAndReturnAsts(source, expected);
    Node sourceRoot = asts.sourceRoot;
    Node expectedRoot = asts.expectedRoot;

    // Get nodes from the original, pre-transpiled AST
    // `C` from `class C {`
    Node sourceCName = getNodeWithName(sourceRoot, "C");
    // `constructor() {}`
    Node sourceConstructorFunction = getNodeWithName(sourceRoot, "constructor").getOnlyChild();
    assertNode(sourceConstructorFunction).hasToken(Token.FUNCTION);

    // `C` from `let C = function() {};` matches `C` from `class C {`
    Node expectedCName = getNodeWithName(expectedRoot, "C");
    assertNode(expectedCName).matchesQualifiedName("C").hasEqualSourceInfoTo(sourceCName);

    // `function() {}` matches `constructor() {}`
    Node expectedFunction = expectedCName.getOnlyChild();
    assertNode(expectedFunction)
        .hasToken(Token.FUNCTION)
        .hasEqualSourceInfoTo(sourceConstructorFunction);
  }

  @Test
  public void testClassStatementWithMethod_hasCorrectSourceInfo() {
    String source = "class C { method() {}; }";
    String expected =
        lines(
            "/** @constructor */", "let C = function() {};", "C.prototype.method = function() {};");

    AstPair asts = testAndReturnAsts(source, expected);
    Node sourceRoot = asts.sourceRoot;
    Node expectedRoot = asts.expectedRoot;

    // Get nodes from the original, pre-transpiled AST
    // The MEMBER_FUNCTION_DEF for `method`
    Node sourceMethodMemberDef = getNodeWithName(sourceRoot, "method");
    // The FUNCTION node for `method() {}`
    Node sourceMethodFunction = sourceMethodMemberDef.getOnlyChild();

    // `C.prototype.method` has source info matching `method`
    Node cPrototypeMethod = getNodeWithName(expectedRoot, "method");
    assertNode(cPrototypeMethod).hasEqualSourceInfoTo(sourceMethodMemberDef);

    // `C.prototype` has source info matching `method`
    Node cPrototype = cPrototypeMethod.getFirstChild();
    assertNode(cPrototype)
        .matchesQualifiedName("C.prototype")
        .hasEqualSourceInfoTo(sourceMethodMemberDef);

    // `C` has source info matching `method`
    Node cName = cPrototype.getFirstChild();
    assertNode(cName).matchesQualifiedName("C").hasEqualSourceInfoTo(sourceMethodMemberDef);

    // `function() {}` has source info matching `method() {}`
    assertNode(cPrototypeMethod.getNext())
        .hasToken(Token.FUNCTION)
        .hasEqualSourceInfoTo(sourceMethodFunction);
  }

  @Test
  public void testSuperCall_hasCorrectSourceInfo() {
    String source =
        lines(
            "class D {}", //
            "class C extends D {",
            "  constructor() {",
            "    SUPER: super();",
            "  } ",
            "}");
    String expected =
        lines(
            "/** @constructor */",
            "let D = function() {};",
            "/** @constructor @extends {D} */",
            "let C = function() {",
            "  SUPER: D.call(this);",
            "}",
            "$jscomp.inherits(C, D);");

    AstPair asts = testAndReturnAsts(source, expected);
    Node sourceRoot = asts.sourceRoot;
    Node expectedRoot = asts.expectedRoot;

    // Get nodes from the original, pre-transpiled AST
    Node sourceSuperCall = getNodeMatchingLabel(sourceRoot, "SUPER").getOnlyChild();
    assertNode(sourceSuperCall).hasToken(Token.CALL);
    Node sourceSuper = sourceSuperCall.getFirstChild();
    assertNode(sourceSuper).hasToken(Token.SUPER);

    // D.call(this); has the position and length of `super()`
    Node callNode = getNodeMatchingLabel(expectedRoot, "SUPER").getOnlyChild();
    assertNode(callNode).hasToken(Token.CALL).hasEqualSourceInfoTo(sourceSuperCall);

    // D.call has the position and length of `super`
    Node callee = callNode.getFirstChild();
    assertNode(callee).matchesQualifiedName("D.call").hasEqualSourceInfoTo(sourceSuper);

    // D has the position and length of `super`
    Node superDotGReplacement = callee.getFirstChild();
    assertNode(superDotGReplacement).matchesQualifiedName("D").hasEqualSourceInfoTo(sourceSuper);

    // `this` node from `D.call(this)` has the position and length of `super`
    Node thisNode = callee.getNext();
    assertNode(thisNode).hasToken(Token.THIS).hasEqualSourceInfoTo(sourceSuper);
  }

  @Test
  public void testComputedSuper_hasCorrectSourceInfo() {
    String source =
        lines(
            "/** @unrestricted */",
            "class Foo {",
            "  ['m']() { return 1; }",
            "}",
            "",
            "/** @unrestricted */",
            "class Bar extends Foo {",
            "  ['m']() {",
            "    RETURN: return super['m']();",
            "  }",
            "}");
    String expected =
        lines(
            "/** @constructor @unrestricted */",
            "let Foo = function() {};",
            "Foo.prototype['m'] = function() { return 1; };",
            "/** @constructor @unrestricted @extends {Foo} */",
            "let Bar = function() { Foo.apply(this, arguments); };",
            "$jscomp.inherits(Bar, Foo);",
            "Bar.prototype['m'] = function () {",
            "  RETURN: return Foo.prototype['m'].call(this);",
            "};");

    AstPair asts = testAndReturnAsts(source, expected);
    Node sourceRoot = asts.sourceRoot;
    Node expectedRoot = asts.expectedRoot;

    // Get nodes from the original, pre-transpiled AST
    Node sourceSuperCall = getNodeMatchingLabel(sourceRoot, "RETURN").getOnlyChild();
    assertNode(sourceSuperCall).hasToken(Token.CALL);
    Node sourceSuperGet = sourceSuperCall.getFirstChild();
    assertNode(sourceSuperGet).hasToken(Token.GETELEM);
    Node sourceSuper = sourceSuperGet.getFirstChild();
    assertNode(sourceSuper).hasToken(Token.SUPER);

    // Foo.prototype['m'].call(this) matches source info of `super['m']()`
    Node callNode = getNodeMatchingLabel(expectedRoot, "RETURN").getOnlyChild();
    assertNode(callNode).hasToken(Token.CALL).hasEqualSourceInfoTo(sourceSuperCall);

    // Foo.prototype['m'].call has the position and length of `super['m']'`
    Node callee = callNode.getFirstChild();
    assertNode(callee).hasToken(Token.GETPROP).hasEqualSourceInfoTo(sourceSuperGet);

    // Foo.prototype['m'] has the position and length of `super['m']'`
    Node property = callee.getFirstChild();
    assertNode(property).hasToken(Token.GETELEM).hasEqualSourceInfoTo(sourceSuperGet);

    // Foo.prototype has the position and length of `super`
    Node prototype = property.getFirstChild();
    assertNode(prototype).matchesQualifiedName("Foo.prototype").hasEqualSourceInfoTo(sourceSuper);

    // Foo has the position and length of `super`
    Node superDotGReplacement = prototype.getFirstChild();
    assertNode(superDotGReplacement).matchesQualifiedName("Foo").hasEqualSourceInfoTo(sourceSuper);

    // `this` node from `Foo.prototype['m'].call(this)` has position and length of `super['m']`
    Node thisNode = callee.getNext();
    assertNode(thisNode).hasToken(Token.THIS).hasEqualSourceInfoTo(sourceSuperGet);
  }

  @Test
  public void testStringKeyGetterAndSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    // Can't add a property to a struct
    disableTypeCheck();
    disableTypeInfoValidation();

    test(
        lines(
            "class MyClass {",
            "  /** @param {number} v */",
            "  set 'setter'(v) {}",
            "  /** @return {number} */",
            "  get 'getter'() { return 1; }",
            "}"),
        lines(
            "/** @constructor */",
            "let MyClass=function(){};",
            "",
            "$jscomp.global.Object.defineProperties(",
            "    MyClass.prototype,",
            "    {",
            "      'setter': {",
            "        configurable:true,",
            "        enumerable:true,",
            "        /** @param {number} v */",
            "        set:function(v){ }",
            "      },",
            "      'getter': {",
            "        configurable:true,",
            "        enumerable:true,",
            "        /** @return {number} */",
            "        get:function(){ return 1; }",
            "      },",
            "    })"));
  }

  @Test
  public void testNumericGetterAndSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    // Can't add a property to a struct
    disableTypeCheck();
    disableTypeInfoValidation();

    test(
        lines(
            "class MyClass {",
            "  /** @param {number} v */",
            "  set 1(v) {}",
            "  /** @return {number} */",
            "  get 1.5() { return 1; }",
            "}"),
        lines(
            "/** @constructor */",
            "let MyClass=function(){};",
            "$jscomp.global.Object.defineProperties(",
            "    MyClass.prototype,",
            "    {",
            "      1: {",
            "        configurable:true,",
            "        enumerable:true,",
            "        /** @param {number} v */",
            "        set:function(v){ }",
            "      },",
            "      1.5: {",
            "        configurable:true,",
            "        enumerable:true,",
            "        /** @return {number} */",
            "        get:function(){ return 1; }",
            "      },",
            "    })"));
  }

  /** Returns the first node (preorder) in the given AST labeled as {@code label} */
  private Node getNodeMatchingLabel(Node root, String label) {
    if (root.isLabel() && root.getFirstChild().getString().equals(label)) {
      // "FOO: return foo;"
      //   becomes
      // LABEL
      //   LABEL_NAME FOO
      //   RETURN
      //   NAME foo
      return root.getSecondChild();
    }
    for (Node child = root.getFirstChild(); child != null; child = child.getNext()) {
      Node result = getNodeMatchingLabel(child, label);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  /** Returns the first node (preorder) in the given AST that matches the given qualified name */
  private Node getNodeWithName(Node root, String name) {
    switch (root.getToken()) {
      case GETPROP:
      case OPTCHAIN_GETPROP:
        if (root.getString().equals(name)) {
          return root;
        }
        break;

      case NAME:
      case STRINGLIT:
      case MEMBER_FUNCTION_DEF:
      case STRING_KEY:
        if (root.getString().equals(name)) {
          return root;
        }
        break;

      default:
        break;
    }

    for (Node child = root.getFirstChild(); child != null; child = child.getNext()) {
      Node result = getNodeWithName(child, name);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Override
  protected void test(String before, String after) {
    super.test(RUNTIME_LIBRARY_STUBS + before, RUNTIME_LIBRARY_STUBS + after);
  }

  @Override
  protected void testError(String before, DiagnosticType error) {
    super.testError(RUNTIME_LIBRARY_STUBS + before, error);
  }

  @Override
  protected void testWarning(String before, DiagnosticType error) {
    super.testWarning(RUNTIME_LIBRARY_STUBS + before, error);
  }

  @Override
  protected void testNoWarning(String before) {
    super.testNoWarning(RUNTIME_LIBRARY_STUBS + before);
  }

  private static String linesWithStubs(String... lines) {
    return RUNTIME_LIBRARY_STUBS + lines(lines);
  }

  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler();
  }

  @Override
  protected NoninjectingCompiler getLastCompiler() {
    return (NoninjectingCompiler) super.getLastCompiler();
  }

  /**
   * Tests that the transpiled source matches the expected code and then returns the AST roots for
   * the source and the transpiled source respectively.
   */
  private AstPair testAndReturnAsts(String source, String expected) {
    Node originalRoot = createCompiler().parseTestCode(RUNTIME_LIBRARY_STUBS + source);

    test(source, expected);

    return new AstPair(originalRoot, getLastCompiler().getJsRoot());
  }

  private static class AstPair {
    final Node sourceRoot;

    final Node expectedRoot;

    AstPair(Node sourceRoot, Node expectedRoot) {
      this.sourceRoot = sourceRoot;
      this.expectedRoot = expectedRoot;
    }
  }
}
