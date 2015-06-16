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
import static com.google.javascript.jscomp.ClosureRewriteClass.GOOG_CLASS_CONSTRUCTOR_ON_INTERFACE;
import static com.google.javascript.jscomp.ClosureRewriteClass.GOOG_CLASS_DESCRIPTOR_NOT_VALID;
import static com.google.javascript.jscomp.ClosureRewriteClass.GOOG_CLASS_NG_INJECT_ON_CLASS;
import static com.google.javascript.jscomp.ClosureRewriteClass.GOOG_CLASS_STATICS_NOT_VALID;
import static com.google.javascript.jscomp.ClosureRewriteClass.GOOG_CLASS_SUPER_CLASS_NOT_VALID;
import static com.google.javascript.jscomp.ClosureRewriteClass.GOOG_CLASS_TARGET_INVALID;
import static com.google.javascript.jscomp.ClosureRewriteClass.GOOG_CLASS_UNEXPECTED_PARAMS;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * Unit tests for ClosureRewriteGoogClass
 * @author johnlenz@google.com (John Lenz)
 */
public final class ClosureRewriteClassTest extends CompilerTestCase {
  private static final String EXTERNS =
      "var goog = {};\n"
      + "goog.inherits = function(a,b) {};\n"
      + "goog.defineClass = function(a,b) {};\n"
      + "var use\n";

  public ClosureRewriteClassTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ClosureRewriteClass(compiler);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT3);
    disableTypeCheck();
    runTypeCheckAfterProcessing = true;
    compareJsDoc = true;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testBasic1() {
    test(
        "var x = goog.defineClass(null, {\n"
        + "  constructor: function(){}\n"
        + "});",

        "/** @constructor @struct */"
        + "var x = function() {};");
  }

  public void testBasic2() {
    test(
        "var x = {};\n"
        + "x.y = goog.defineClass(null, {\n"
        + "  constructor: function(){}\n"
        + "});",

        "var x = {};"
        + "/** @constructor @struct */"
        + "x.y = function() {};");
  }

  public void testBasic3() {
    // verify we don't add a goog.inherits for Object
    test(
        "var x = goog.defineClass(Object, {\n"
        + "  constructor: function(){}\n"
        + "});",

        "/** @constructor @struct */"
        + "var x = function() {};");
  }

  public void testAnnotations1() {
    // verify goog.defineClass values are constructible, by default
    enableTypeCheck(CheckLevel.WARNING);
    test(
        "var x = goog.defineClass(Object, {\n"
        + "  constructor: function(){}\n"
        + "});"
        + "new x();",

        "/** @constructor @struct */"
        + "var x = function() {};"
        + "new x();");
  }

  public void testAnnotations2a() {
    // @interface is preserved
    enableTypeCheck(CheckLevel.WARNING);
    test(
        "var x = goog.defineClass(null, {\n"
        + "  /** @interface */\n"
        + "  constructor: function(){}\n"
        + "});"
        + "new x();",

        "/** @interface */\n"
        + "var x = function() {};"
        + "new x();",
        null,
        TypeCheck.NOT_A_CONSTRUCTOR);
  }

  public void testAnnotations2b() {
    // @interface is preserved, at the class level too
    enableTypeCheck(CheckLevel.WARNING);
    test(
        "/** @interface */\n"
        + "var x = goog.defineClass(null, {});"
        + "new x();",

        "/** @interface */\n"
        + "var x = function() {};"
        + "new x();",
        null,
        TypeCheck.NOT_A_CONSTRUCTOR);
  }

  public void testAnnotations3a() {
    // verify goog.defineClass is a @struct by default
    enableTypeCheck(CheckLevel.WARNING);
    test(
        "var y = goog.defineClass(null, {\n"
        + "  constructor: function(){}\n"
        + "});\n"
        + "var x = goog.defineClass(y, {\n"
        + "  constructor: function(){this.a = 1}\n"
        + "});\n"
        + "use(new y().a);\n",

        "/** @constructor @struct */"
        + "var y = function () {};\n"
        + "/** @constructor @struct @extends {y} */"
        + "var x = function() {this.a = 1};\n"
        + "goog.inherits(x,y);\n"
        + "use(new y().a);\n",
        null,
        TypeCheck.INEXISTENT_PROPERTY);
  }

  public void testAnnotations3b() {
    // verify goog.defineClass is a @struct by default, but can be overridden
    enableTypeCheck(CheckLevel.WARNING);
    test(
        "/** @unrestricted */"
        + "var y = goog.defineClass(null, {\n"
        + "  constructor: function(){}\n"
        + "});\n"
        + "var x = goog.defineClass(y, {\n"
        + "  constructor: function(){this.a = 1}\n"
        + "});\n"
        + "use(new y().a);\n",

        "/** @constructor @unrestricted */"
        + "var y = function () {};\n"
        + "/** @constructor @struct @extends {y} */"
        + "var x = function() {this.a = 1};\n"
        + "goog.inherits(x,y);\n"
        + "use(new y().a);\n");
  }

  public void testInnerClass1() {
    test(
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

  public void testComplete1() {
    test(
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

  public void testComplete2() {
    test(
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

  public void testClassWithStaticInitFn() {
    test(
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

        LINE_JOINER.join(
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

  public void testPrivate1() {
    test(
        LINE_JOINER.join(
            "/** @private */",
            "x.y_ = goog.defineClass(null, {",
            "  constructor: function() {}",
            "});"),
        "/** @private @constructor @struct */ x.y_ = function() {};");
  }

  public void testPrivate2() {
    test(
        LINE_JOINER.join(
            "/** @private */",
            "x.y_ = goog.defineClass(null, {",
            "  /** @param {string} s */",
            "  constructor: function(s) {}",
            "});"),
        LINE_JOINER.join(
            "/**",
            " * @private",
            " * @constructor",
            " * @struct",
            " * @param {string} s",
            " */",
            "x.y_ = function(s) {};"));
  }

  public void testInvalid1() {
    testSame("var x = goog.defineClass();", GOOG_CLASS_SUPER_CLASS_NOT_VALID, true);
    testSame("var x = goog.defineClass('foo');", GOOG_CLASS_SUPER_CLASS_NOT_VALID, true);
    testSame("var x = goog.defineClass(foo());", GOOG_CLASS_SUPER_CLASS_NOT_VALID, true);
    testSame("var x = goog.defineClass({'foo':1});", GOOG_CLASS_SUPER_CLASS_NOT_VALID, true);
    testSame("var x = goog.defineClass({1:1});", GOOG_CLASS_SUPER_CLASS_NOT_VALID, true);

    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);

    testSame("var x = goog.defineClass({get foo() {return 1}});", GOOG_CLASS_SUPER_CLASS_NOT_VALID,
        true);
    testSame("var x = goog.defineClass({set foo(a) {}});", GOOG_CLASS_SUPER_CLASS_NOT_VALID, true);
  }

  public void testInvalid2() {
    testSame("var x = goog.defineClass(null);", GOOG_CLASS_DESCRIPTOR_NOT_VALID, true);
    testSame("var x = goog.defineClass(null, null);", GOOG_CLASS_DESCRIPTOR_NOT_VALID, true);
    testSame("var x = goog.defineClass(null, foo());", GOOG_CLASS_DESCRIPTOR_NOT_VALID, true);
  }

  public void testInvalid3() {
    testSame("var x = goog.defineClass(null, {});", GOOG_CLASS_CONSTRUCTOR_MISSING, true);

    testSame(
        "/** @interface */\n"
        + "var x = goog.defineClass(null, { constructor: function() {} });",
        GOOG_CLASS_CONSTRUCTOR_ON_INTERFACE, true);
  }

  public void testInvalid4() {
    testSame(
        "var x = goog.defineClass(null, {"
        + "  constructor: function(){},"
        + "  statics: null"
        + "});",
        GOOG_CLASS_STATICS_NOT_VALID, true);
    testSame(
        "var x = goog.defineClass(null, {"
        + "  constructor: function(){},"
        + "  statics: foo"
        + "});",
        GOOG_CLASS_STATICS_NOT_VALID, true);
    testSame(
        "var x = goog.defineClass(null, {"
        + "  constructor: function(){},"
        + "  statics: {'foo': 1}"
        + "});",
        GOOG_CLASS_STATICS_NOT_VALID, true);
    testSame(
        "var x = goog.defineClass(null, {"
        + "  constructor: function(){},"
        + "  statics: {1: 1}"
        + "});",
        GOOG_CLASS_STATICS_NOT_VALID, true);
  }

  public void testInvalid5() {
    testSame(
        "var x = goog.defineClass(null, {"
        + "  constructor: function(){}"
        + "}, null);",
        GOOG_CLASS_UNEXPECTED_PARAMS, true);
  }

  public void testInvalid6() {
    testSame("goog.defineClass();", GOOG_CLASS_TARGET_INVALID, true);

    testSame("var x = goog.defineClass() || null;", GOOG_CLASS_TARGET_INVALID, true);

    testSame("({foo: goog.defineClass()});", GOOG_CLASS_TARGET_INVALID, true);
  }

  public void testNgInject() {
    test(
        "var x = goog.defineClass(Object, {\n"
        + "  /** @ngInject */ constructor: function(x, y) {}\n"
        + "});",
        "/** @ngInject @constructor @struct */\n"
        + "var x = function(x, y) {};");
  }

  public void testNgInject_onClass() {
    test(
        "/** @ngInject */\n"
        + "var x = goog.defineClass(Object, {\n"
        + "  constructor: function(x, y) {}\n"
        + "});",
        "/** @ngInject @constructor @struct */\n"
        + "var x = function(x, y) {};",
        null, GOOG_CLASS_NG_INJECT_ON_CLASS);
  }
}
