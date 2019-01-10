/*
 * Copyright 2012 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.ClosureRewriteClass.GOOG_CLASS_CONSTRUCTOR_MISSING;
import static com.google.javascript.jscomp.ClosureRewriteClass.GOOG_CLASS_CONSTRUCTOR_NOT_VALID;
import static com.google.javascript.jscomp.ClosureRewriteClass.GOOG_CLASS_CONSTRUCTOR_ON_INTERFACE;
import static com.google.javascript.jscomp.ClosureRewriteClass.GOOG_CLASS_DESCRIPTOR_NOT_VALID;
import static com.google.javascript.jscomp.ClosureRewriteClass.GOOG_CLASS_ES6_ARROW_FUNCTION_NOT_SUPPORTED;
import static com.google.javascript.jscomp.ClosureRewriteClass.GOOG_CLASS_ES6_COMPUTED_PROP_NAMES_NOT_SUPPORTED;
import static com.google.javascript.jscomp.ClosureRewriteClass.GOOG_CLASS_NG_INJECT_ON_CLASS;
import static com.google.javascript.jscomp.ClosureRewriteClass.GOOG_CLASS_STATICS_NOT_VALID;
import static com.google.javascript.jscomp.ClosureRewriteClass.GOOG_CLASS_SUPER_CLASS_NOT_VALID;
import static com.google.javascript.jscomp.ClosureRewriteClass.GOOG_CLASS_TARGET_INVALID;
import static com.google.javascript.jscomp.ClosureRewriteClass.GOOG_CLASS_UNEXPECTED_PARAMS;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for ClosureRewriteGoogClass
 *
 * @author johnlenz@google.com (John Lenz)
 */
@RunWith(JUnit4.class)
public final class ClosureRewriteClassTest extends CompilerTestCase {
  private static final String EXTERNS = lines(
      MINIMAL_EXTERNS,
      "/** @const */ var goog = {};",
      "goog.inherits = function(a,b) {};",
      "goog.defineClass = function(a,b) {};",
      "var use;");

  private static final Diagnostic INSTANTIATE_ABSTRACT_CLASS =
      warning(TypeCheck.INSTANTIATE_ABSTRACT_CLASS);

  private static final Diagnostic NOT_A_CONSTRUCTOR = warning(TypeCheck.NOT_A_CONSTRUCTOR);

  private static final Diagnostic INEXISTENT_PROPERTY = warning(TypeCheck.INEXISTENT_PROPERTY);

  public ClosureRewriteClassTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ClosureRewriteClass(compiler);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    disableTypeCheck();
    enableRunTypeCheckAfterProcessing();
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  private void testRewrite(String code, String expected, LanguageMode lang) {
    setAcceptedLanguage(lang);
    test(code, expected);
  }

  private void testRewrite(String code, String expected){
    testRewrite(code, expected, LanguageMode.ECMASCRIPT3);
    testRewrite(code, expected, LanguageMode.ECMASCRIPT_2015);
  }

  private void testRewriteError(String js, DiagnosticType error, LanguageMode lang) {
    setAcceptedLanguage(lang);
    testError(js, error);
  }

  private void testRewriteError(String js, DiagnosticType error){
    testRewriteError(js, error, LanguageMode.ECMASCRIPT3);
    testRewriteError(js, error, LanguageMode.ECMASCRIPT_2015);
  }

  private void testRewriteWarning(String code, String expected,
                                  Diagnostic warning, LanguageMode lang) {
    setAcceptedLanguage(lang);
    test(code, expected, warning);
  }

  private void testRewriteWarning(String code, String expected, Diagnostic warning) {
    testRewriteWarning(code, expected, warning, LanguageMode.ECMASCRIPT3);
    testRewriteWarning(code, expected, warning, LanguageMode.ECMASCRIPT_2015);
  }

  @Test
  public void testBasic1() {
    testRewrite(
        "var x = goog.defineClass(null, {\n"
        + "  constructor: function(){}\n"
        + "});",

        "/** @constructor @struct */"
        + "var x = function() {};");
  }

  @Test
  public void testBasic2() {
    testRewrite(
        "var x = {};\n"
        + "x.y = goog.defineClass(null, {\n"
        + "  constructor: function(){}\n"
        + "});",

        "var x = {};"
        + "/** @constructor @struct */"
        + "x.y = function() {};");
  }

  @Test
  public void testBasic3() {
    // verify we don't add a goog.inherits for Object
    testRewrite(
        "var x = goog.defineClass(Object, {\n"
        + "  constructor: function(){}\n"
        + "});",

        "/** @constructor @struct */"
        + "var x = function() {};");
  }

  @Test
  public void testLet() {
    testRewrite(
        "let x = goog.defineClass(null, {\n" + "  constructor: function(){}\n" + "});",
        "/** @constructor @struct */" + "let x = function() {};",
        LanguageMode.ECMASCRIPT_2015);
  }

  @Test
  public void testConst() {
    testRewrite(
        "const x = goog.defineClass(null, {\n" + "  constructor: function(){}\n" + "});",
        "/** @constructor @struct */" + "const x = function() {};",
        LanguageMode.ECMASCRIPT_2015);
  }

  @Test
  public void testAnnotations1() {
    // verify goog.defineClass values are constructible, by default
    enableTypeCheck();
    testRewrite(
        "var x = goog.defineClass(Object, {\n"
        + "  constructor: function(){}\n"
        + "});"
        + "new x();",

        "/** @constructor @struct */"
        + "var x = function() {};"
        + "new x();");
  }

  @Test
  public void testAnnotations2a() {
    // @interface is preserved
    enableTypeCheck();
    testRewriteWarning(
        lines(
            "var x = goog.defineClass(null, {",
            "  /** @interface */",
            "  constructor: function(){}",
            "});",
            "new x();"),
        lines(
            "/** @struct @interface */",
            "var x = function() {};",
            "new x();"),
        NOT_A_CONSTRUCTOR);
  }

  @Test
  public void testAnnotations2b() {
    // @interface is preserved, at the class level too
    enableTypeCheck();
    testRewriteWarning(
        lines(
            "/** @interface */",
            "var x = goog.defineClass(null, {});",
            "new x();"),
        lines(
            "/** @struct @interface */",
            "var x = function() {};",
            "new x();"),
        NOT_A_CONSTRUCTOR);
  }

  @Test
  public void testAnnotations3a() {
    // verify goog.defineClass is a @struct by default
    enableTypeCheck();
    testRewriteWarning(
        lines(
            "var y = goog.defineClass(null, {",
            "  constructor: function(){}",
            "});",
            "var x = goog.defineClass(y, {",
            "  constructor: function(){this.a = 1}",
            "});",
            "use(new y().a);"),
        lines(
            "/** @constructor @struct */",
            "var y = function () {};",
            "/** @constructor @struct @extends {y} */",
            "var x = function() {this.a = 1};",
            "goog.inherits(x,y);",
            "use(new y().a);"),
        INEXISTENT_PROPERTY);
  }

  @Test
  public void testAnnotations3b() {
    // verify goog.defineClass is a @struct by default, but can be overridden
    enableTypeCheck();
    testRewrite(
        lines(
            "/** @unrestricted */",
            "var y = goog.defineClass(null, {",
            "  constructor: function(){}",
            "});",
            "var x = goog.defineClass(y, {",
            "  constructor: function(){this.a = 1}",
            "});",
            "use(new y().a);"),
        lines(
            "/** @constructor @unrestricted */",
            "var y = function () {};",
            "/** @constructor @struct @extends {y} */",
            "var x = function() {this.a = 1};",
            "goog.inherits(x,y);",
            "use(new y().a);"));
  }

  @Test
  public void testRecordAnnotations() {
    // @record is preserved
    testRewrite(
        "/** @record */\n"
        + "var Rec = goog.defineClass(null, {f : function() {}});",

        "/** @struct @record */\n"
        + "var Rec = function() {};\n"
        + "Rec.prototype.f = function() {};");
  }

  @Test
  public void testRecordAnnotations2() {
    enableTypeCheck();
    testRewrite(
        "/** @record */\n"
        + "var Rec = goog.defineClass(null, {f : function() {}});\n"
        + "var /** !Rec */ r = { f : function() {} };",

        "/** @struct @record */\n"
        + "var Rec = function() {};\n"
        + "Rec.prototype.f = function() {};\n"
        + "var /** !Rec */ r = { f : function() {} };");
  }

  @Test
  public void testAbstract1() {
    // @abstract is preserved
    enableTypeCheck();
    testRewriteWarning(
        lines(
            "var x = goog.defineClass(null, {",
            "  /** @abstract */",
            "  constructor: function() {}",
            "});",
            "new x();"),
        lines(
            "/** @abstract @struct @constructor */",
            "var x = function() {};",
            "new x();"),
        INSTANTIATE_ABSTRACT_CLASS);
  }

  @Test
  public void testAbstract2() {
    // @abstract is preserved, at the class level too
    enableTypeCheck();
    testRewriteWarning(
        lines(
            "/** @abstract */",
            "var x = goog.defineClass(null, {",
            "  constructor: function() {}",
            "});",
            "new x();"),
        lines(
            "/** @abstract @struct @constructor */",
            "var x = function() {};",
            "new x();"),
        INSTANTIATE_ABSTRACT_CLASS);
  }

  @Test
  public void testInnerClass1() {
    testRewrite(
        "var x = goog.defineClass(some.Super, {\n"
        + "  constructor: function(){\n"
        + "    this.foo = 1;\n"
        + "  },\n"
        + "  statics: {\n"
        + "    inner: goog.defineClass(x,{\n"
        + "      constructor: function(){\n"
        + "        this.bar = 1;\n"
        + "      }\n"
        + "    })\n"
        + "  }\n"
        + "});",

        "/** @constructor @struct @extends {some.Super} */\n"
        + "var x = function() { this.foo = 1; };\n"
        + "goog.inherits(x, some.Super);\n"
        + "/** @constructor @struct @extends {x} */\n"
        + "x.inner = function() { this.bar = 1; };\n"
        + "goog.inherits(x.inner, x);");
  }

  @Test
  public void testComplete1() {
    testRewrite(
        "var x = goog.defineClass(some.Super, {\n"
        + "  constructor: function(){\n"
        + "    this.foo = 1;\n"
        + "  },\n"
        + "  statics: {\n"
        + "    prop1: 1,\n"
        + "    /** @const */\n"
        + "    PROP2: 2\n"
        + "  },\n"
        + "  anotherProp: 1,\n"
        + "  aMethod: function() {}\n"
        + "});",

        "/** @constructor @struct @extends {some.Super} */\n"
        + "var x=function(){this.foo=1};\n"
        + "goog.inherits(x, some.Super);\n"
        + "x.prop1=1;\n"
        + "/** @const */\n"
        + "x.PROP2=2;\n"
        + "x.prototype.anotherProp = 1;\n"
        + "x.prototype.aMethod = function(){};");
  }

  @Test
  public void testComplete2() {
    testRewrite(
        "x.y = goog.defineClass(some.Super, {\n"
        + "  constructor: function(){\n"
        + "    this.foo = 1;\n"
        + "  },\n"
        + "  statics: {\n"
        + "    prop1: 1,\n"
        + "    /** @const */\n"
        + "    PROP2: 2\n"
        + "  },\n"
        + "  anotherProp: 1,\n"
        + "  aMethod: function() {}\n"
        + "});",

        "/** @constructor @struct @extends {some.Super} */\n"
        + "x.y=function(){this.foo=1};\n"
        + "goog.inherits(x.y,some.Super);\n"
        + "x.y.prop1 = 1;\n"
        + "/** @const */\n"
        + "x.y.PROP2 = 2;\n"
        + "x.y.prototype.anotherProp = 1;\n"
        + "x.y.prototype.aMethod=function(){};");
  }

  @Test
  public void testClassWithStaticInitFn() {
    testRewrite(
        "x.y = goog.defineClass(some.Super, {\n"
            + "  constructor: function(){\n"
            + "    this.foo = 1;\n"
            + "  },\n"
            + "  statics: function(cls) {\n"
            + "    cls.prop1 = 1;\n"
            + "    /** @const */\n"
            + "    cls.PROP2 = 2;\n"
            + "  },\n"
            + "  anotherProp: 1,\n"
            + "  aMethod: function() {}\n"
            + "});",

        lines(
            "/** @constructor @struct @extends {some.Super} */",
            "x.y = function() { this.foo = 1; };",
            "goog.inherits(x.y, some.Super);",
            "x.y.prototype.anotherProp = 1;",
            "x.y.prototype.aMethod = function() {};",
            "(function(cls) {",
            "  x.y.prop1 = 1;",
            "  /** @const */",
            "  x.y.PROP2 = 2;",
            "})(x.y);"));
  }

  @Test
  public void testPrivate1() {
    testRewrite(
        lines(
            "/** @private */",
            "x.y_ = goog.defineClass(null, {",
            "  constructor: function() {}",
            "});"),
        "/** @private @constructor @struct */ x.y_ = function() {};");
  }

  @Test
  public void testPrivate2() {
    testRewrite(
        lines(
            "/** @private */",
            "x.y_ = goog.defineClass(null, {",
            "  /** @param {string} s */",
            "  constructor: function(s) {}",
            "});"),
        lines(
            "/**",
            " * @private",
            " * @constructor",
            " * @struct",
            " * @param {string} s",
            " */",
            "x.y_ = function(s) {};"));
  }

  @Test
  public void testInvalid1() {
    testRewriteError("var x = goog.defineClass();", GOOG_CLASS_SUPER_CLASS_NOT_VALID);
    testRewriteError("var x = goog.defineClass('foo');", GOOG_CLASS_SUPER_CLASS_NOT_VALID);
    testRewriteError("var x = goog.defineClass(foo());", GOOG_CLASS_SUPER_CLASS_NOT_VALID);
    testRewriteError("var x = goog.defineClass({'foo':1});", GOOG_CLASS_SUPER_CLASS_NOT_VALID);
    testRewriteError("var x = goog.defineClass({1:1});", GOOG_CLASS_SUPER_CLASS_NOT_VALID);

    testRewriteError(
        "var x = goog.defineClass({get foo() {return 1}});",
        GOOG_CLASS_SUPER_CLASS_NOT_VALID, LanguageMode.ECMASCRIPT5);
    testRewriteError(
        "var x = goog.defineClass({set foo(a) {}});",
        GOOG_CLASS_SUPER_CLASS_NOT_VALID, LanguageMode.ECMASCRIPT5);
  }

  @Test
  public void testInvalid2() {
    testRewriteError("var x = goog.defineClass(null);", GOOG_CLASS_DESCRIPTOR_NOT_VALID);
    testRewriteError("var x = goog.defineClass(null, null);", GOOG_CLASS_DESCRIPTOR_NOT_VALID);
    testRewriteError("var x = goog.defineClass(null, foo());", GOOG_CLASS_DESCRIPTOR_NOT_VALID);
  }

  @Test
  public void testInvalid3() {
    testRewriteError("var x = goog.defineClass(null, {});", GOOG_CLASS_CONSTRUCTOR_MISSING);

    testRewriteError(
        "/** @interface */\n" + "var x = goog.defineClass(null, { constructor: function() {} });",
        GOOG_CLASS_CONSTRUCTOR_ON_INTERFACE);
  }

  @Test
  public void testInvalid4() {
    testRewriteError(
        "var x = goog.defineClass(null, {"
            + "  constructor: function(){},"
            + "  statics: null"
            + "});",
        GOOG_CLASS_STATICS_NOT_VALID);

    testRewriteError(
        "var x = goog.defineClass(null, {"
            + "  constructor: function(){},"
            + "  statics: foo"
            + "});",
        GOOG_CLASS_STATICS_NOT_VALID);

    testRewriteError(
        "var x = goog.defineClass(null, {"
            + "  constructor: function(){},"
            + "  statics: {'foo': 1}"
            + "});",
        GOOG_CLASS_STATICS_NOT_VALID);

    testRewriteError(
        "var x = goog.defineClass(null, {"
            + "  constructor: function(){},"
            + "  statics: {1: 1}"
            + "});",
        GOOG_CLASS_STATICS_NOT_VALID);
  }

  @Test
  public void testInvalid5() {
    testRewriteError(
        "var x = goog.defineClass(null, {" + "  constructor: function(){}" + "}, null);",
        GOOG_CLASS_UNEXPECTED_PARAMS);
  }

  @Test
  public void testInvalid6() {
    testRewriteError("goog.defineClass();", GOOG_CLASS_TARGET_INVALID);

    testRewriteError("var x = goog.defineClass() || null;", GOOG_CLASS_TARGET_INVALID);

    testRewriteError("({foo: goog.defineClass()});", GOOG_CLASS_TARGET_INVALID);
  }

  @Test
  public void testInvalid7() {
    testRewriteError(lines(
        "var x = goog.defineClass(null, {",
        "  constructor: foo",
        "});"),
        GOOG_CLASS_CONSTRUCTOR_NOT_VALID);
  }

  @Test
  public void testGoogModuleGet() {
    // This pattern can be produced by goog.scope processing from code that originally looks like:
    // goog.scope(function() {
    //   var super = goog.module.get('ns.Foo');
    //   var y = goog.defineClass(super, {
    //     // ...
    //   });
    // };
    testRewrite(
        lines(
            "var y = goog.defineClass(goog.module.get('ns.Foo'), {",
            "  constructor: function(){}",
            "});"),
        lines(
            "/** @struct @constructor @extends {ns.Foo} */",
            "var y = function(){};",
            "goog.inherits(y, goog.module.get('ns.Foo'));"));
  }

  @Test
  public void testNgInject() {
    testRewrite(
        "var x = goog.defineClass(Object, {\n"
        + "  /** @ngInject */ constructor: function(x, y) {}\n"
        + "});",
        "/** @ngInject @constructor @struct */\n"
        + "var x = function(x, y) {};");
  }

  @Test
  public void testNgInject_onClass() {
    testRewriteWarning(
        "/** @ngInject */\n"
        + "var x = goog.defineClass(Object, {\n"
        + "  constructor: function(x, y) {}\n"
        + "});",
        "/** @ngInject @constructor @struct */\n"
        + "var x = function(x, y) {};",
        warning(GOOG_CLASS_NG_INJECT_ON_CLASS));
  }

  // The two following tests are just to make sure that these functionalities in
  // Es6 does not break the compiler during this pass

  @Test
  public void testDestructParamOnFunction() {
    testRewrite(
        lines(
            "var FancyClass = goog.defineClass(null, {",
            "  constructor: function({a, b, c}) {}",
            "});"),
        lines(
            "/** @constructor @struct */",
            "var FancyClass = function({a, b, c}) {};"),
        LanguageMode.ECMASCRIPT_2015);
  }

  @Test
  public void testDefaultParamOnFunction() {
    testRewrite(
        lines(
            "var FancyClass = goog.defineClass(null, {",
            "  constructor: function(a = 1) {}",
            "});"),
        lines("/** @constructor @struct */", "var FancyClass = function(a = 1) {};"),
        LanguageMode.ECMASCRIPT_2015);
  }

  @Test
  public void testExtendedObjLitMethodDefinition1() {
    testRewrite(
        lines("var FancyClass = goog.defineClass(null, {", "  constructor() {}", "});"),
        lines("/** @constructor @struct */", "var FancyClass = function() {};"),
        LanguageMode.ECMASCRIPT_2015);
  }

  @Test
  public void testExtendedObjLitMethodDefinition2() {
    testRewrite(
        lines(
            "var FancyClass = goog.defineClass(null, {",
            "  constructor: function() {},",
            "  someMethod1() {},",
            "  someMethod2() {}",
            "});"),
        lines(
            "/** @constructor @struct */",
            "var FancyClass = function() {};",
            "FancyClass.prototype.someMethod1 = function() {};",
            "FancyClass.prototype.someMethod2 = function() {};"),
        LanguageMode.ECMASCRIPT_2015);
  }

  @Test
  public void testExtendedObjLitMethodDefinition3() {
    testRewrite(
        lines(
            "var FancyClass = goog.defineClass(null, {",
            "  constructor() {},",
            "  someMethod1() {},",
            "  someMethod2() {}",
            "});"),
        lines(
            "/** @constructor @struct */",
            "var FancyClass = function() {};",
            "FancyClass.prototype.someMethod1 = function() {};",
            "FancyClass.prototype.someMethod2 = function() {};"),
        LanguageMode.ECMASCRIPT_2015);
  }

  @Test
  public void testExtendedObjLitMethodDefinition4() {
    testRewrite(
        lines(
            "var FancyClass = goog.defineClass(null, {",
            "  constructor() {},",
            "  statics:{",
            "    someMethod1() {}",
            "  },",
            "  someMethod2() {}",
            "});"),
        lines(
            "/** @constructor @struct */",
            "var FancyClass = function() {};",
            "FancyClass.someMethod1 = function() {};",
            "FancyClass.prototype.someMethod2 = function() {};"),
        LanguageMode.ECMASCRIPT_2015);
  }

  @Test
  public void testExtendedObjLitArrowFunction1() {
    testRewriteError(
        lines(
            "var FancyClass = goog.defineClass(null, {",
            "  constructor: function() {},",
            "  someArrowFunc: value => value",
            "});"),
        GOOG_CLASS_ES6_ARROW_FUNCTION_NOT_SUPPORTED,
        LanguageMode.ECMASCRIPT_2015);
  }

  @Test
  public void testExtendedObjLitArrowFunction2() {
    testRewriteError(
        lines(
            "var FancyClass = goog.defineClass(null, {",
            "  constructor: function() {},",
            "  statics:{",
            "    someArrowFunc: value => value",
            "  }",
            "});"),
        GOOG_CLASS_ES6_ARROW_FUNCTION_NOT_SUPPORTED,
        LanguageMode.ECMASCRIPT_2015);
  }

  @Test
  public void testExtendedObjLitArrowFunction3() {
    testRewrite(
        lines(
            "var FancyClass = goog.defineClass(null, {",
            "  constructor: function() {},",
            "  statics:{",
            "    someFunction() {",
            "      return () => 42",
            "    }",
            "  }",
            "});"),
        lines(
            "/** @constructor @struct */",
            "  var FancyClass = function() {};",
            "  FancyClass.someFunction = function() {",
            "    return () => 42",
            "  };"),
        LanguageMode.ECMASCRIPT_2015);
  }

  @Test
  public void testExtendedObjLitArrowFunction4() {
    testRewrite(
        lines(
            "var FancyClass = goog.defineClass(null, {",
            "  constructor: function() {},",
            "  someFunction: function() {",
            "      return () => 42",
            "  }",
            "});"),
        lines(
            "/** @constructor @struct */",
            "  var FancyClass = function() {};",
            "  FancyClass.prototype.someFunction = function(){",
            "    return () => 42",
            "  };"),
        LanguageMode.ECMASCRIPT_2015);
  }

  @Test
  public void testExtendedObjLitComputedPropName1() {
    testRewriteError(
        lines(
            "var FancyClass = goog.defineClass(null, {",
            "  ['someCompProp_' + 42]: 47,",
            "  someMember: 49,",
            "  constructor: function() {},",
            "});"),
        GOOG_CLASS_ES6_COMPUTED_PROP_NAMES_NOT_SUPPORTED,
        LanguageMode.ECMASCRIPT_2015);
  }

  @Test
  public void testExtendedObjLitComputedPropName2() {
    testRewriteError(
        lines(
            "var FancyClass = goog.defineClass(null, {",
            "  constructor: function() {},",
            "  statics:{",
            "    ['someCompProp_' + 1999]: 47",
            "  }",
            "});"),
        GOOG_CLASS_ES6_COMPUTED_PROP_NAMES_NOT_SUPPORTED,
        LanguageMode.ECMASCRIPT_2015);
  }

  @Test
  public void testExtendedObjLitSuperCall1() {
    testRewrite(
        lines(
            "var FancyClass = goog.defineClass(null, {",
            "  constructor: function() {},",
            "  someMethod: function() {",
            "    super.someMethod();",
            "  }",
            "});"),
        lines(
            "/** @constructor @struct */",
            "  var FancyClass = function() {};",
            "  FancyClass.prototype.someMethod = function() {",
            "    super.someMethod();",
            "  };"),
        LanguageMode.ECMASCRIPT_2015);
  }

  @Test
  public void testExtendedObjLitSuperCall2() {
    testRewrite(
        lines(
            "var FancyClass = goog.defineClass(null, {",
            "  constructor: function() {super();},",
            "  someMethod: function() {}",
            "});"),
        lines(
            "/** @constructor @struct */",
            "  var FancyClass = function() {super();};",
            "  FancyClass.prototype.someMethod = function() {};"),
        LanguageMode.ECMASCRIPT_2015);
  }

  @Test
  public void testExtendedObjLitSuperCall3() {
    testRewrite(
        lines(
            "var FancyClass = goog.defineClass(null, {",
            "  constructor: function() {},",
            "  someMethod: function() {super();}",
            "});"),
        lines(
            "/** @constructor @struct */",
            "var FancyClass = function() {};",
            "FancyClass.prototype.someMethod = function() {super();};"),
        LanguageMode.ECMASCRIPT_2015);
  }

  //public void testNestedObjectLiteral(){
  //testRewriteError(
  //    lines(
  //        "var FancyClass = goog.defineClass(null, {",
  //        "  constructor: function() {},",
  //        "  someNestedObjLit:{}",
  //        "});"),
  //    GOOG_CLASS_NESTED_OBJECT_LITERAL_FOUND, LanguageMode.ECMASCRIPT_2015);
  //testRewriteError(
  //    lines(
  //        "var FancyClass = goog.defineClass(null, {",
  //        "  constructor() {},",
  //        "  statics:{",
  //        "    someNestedObjLit:{}",
  //        "  }",
  //        "});"),
  //    GOOG_CLASS_NESTED_OBJECT_LITERAL_FOUND, LanguageMode.ECMASCRIPT_2015);
  //}
}
