/*
 * Copyright 2015 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.CheckJSDoc.ANNOTATION_DEPRECATED;
import static com.google.javascript.jscomp.CheckJSDoc.ARROW_FUNCTION_AS_CONSTRUCTOR;
import static com.google.javascript.jscomp.CheckJSDoc.DEFAULT_PARAM_MUST_BE_MARKED_OPTIONAL;
import static com.google.javascript.jscomp.CheckJSDoc.DISALLOWED_MEMBER_JSDOC;
import static com.google.javascript.jscomp.CheckJSDoc.MISPLACED_ANNOTATION;
import static com.google.javascript.jscomp.CheckJSDoc.MISPLACED_MSG_ANNOTATION;

/**
 * Tests for {@link CheckJSDoc}.
 *
 * @author chadkillingsworth@gmail.com (Chad Killingsworth)
 */

public final class CheckJsDocTest extends Es6CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CheckJSDoc(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(
        DiagnosticGroups.MISPLACED_TYPE_ANNOTATION, CheckLevel.WARNING);
    return options;
  }

  public void testInlineJsDoc_ES6() {
    testSame("function f(/** string */ x) {}");
    testSameEs6("function f(/** number= */ x=3) {}");
    testSameEs6("function f(/** !Object */ {x}) {}");
    testSameEs6("function f(/** !Array */ [x]) {}");

    testWarningEs6("function f([/** number */ x]) {}", MISPLACED_ANNOTATION);
  }

  // TODO(tbreisacher): These should be a MISPLACED_ANNOTATION warning instead of silently failing.
  public void testInlineJsDocInsideObjectParams() {
    testSameEs6("function f({ prop: {/** string */ x} }) {}");
    testSameEs6("function f({ prop: {x: /** string */ y} }) {}");
    testSameEs6("function f({ /** number */ x }) {}");
    testSameEs6("function f({ prop: /** number */ x }) {}");
  }

  public void testInvalidClassJsdoc() {
    testSameEs6("class Foo { /** @param {number} x */ constructor(x) {}}");

    testWarningEs6(
        "class Foo { /** @constructor */ constructor() {}}",
        DISALLOWED_MEMBER_JSDOC);

    testWarningEs6(
        "class Foo { /** @interface */ constructor() {}}",
        DISALLOWED_MEMBER_JSDOC);

    testWarningEs6(
        "class Foo { /** @extends {Foo} */ constructor() {}}",
        DISALLOWED_MEMBER_JSDOC);

    testWarningEs6(
        "class Foo { /** @implements {Foo} */ constructor() {}}",
        DISALLOWED_MEMBER_JSDOC);
  }

  public void testInlineJSDoc() {
    testSame("function f(/** string */ x) {}");
    testSame("function f(/** @type {string} */ x) {}");

    testSame("var /** string */ x = 'x';");
    testSame("var /** @type {string} */ x = 'x';");
    testSame("var /** string */ x, /** number */ y;");
    testSame("var /** @type {string} */ x, /** @type {number} */ y;");
  }

  public void testFunctionJSDocOnMethods() {
    testSameEs6("class Foo { /** @return {?} */ bar() {} }");
    testSameEs6("class Foo { /** @return {?} */ static bar() {} }");
    testSameEs6("class Foo { /** @return {?} */ get bar() {} }");
    testSameEs6("class Foo { /** @param {?} x */ set bar(x) {} }");

    testSameEs6("class Foo { /** @return {?} */ [bar]() {} }");
    testSameEs6("class Foo { /** @return {?} */ static [bar]() {} }");
    testSameEs6("class Foo { /** @return {?} */ get [bar]() {} }");
    testSameEs6("class Foo { /** @return {?} x */ set [bar](x) {} }");
  }

  public void testObjectLiterals() {
    testSame("var o = { /** @type {?} */ x: y };");
    testWarning("var o = { x: /** @type {?} */ y };", MISPLACED_ANNOTATION);
  }

  public void testMethodsOnObjectLiterals() {
    testSameEs6("var x = { /** @return {?} */ foo() {} };");
    testSameEs6("var x = { /** @return {?} */ [foo]() {} };");
    testSameEs6("var x = { /** @return {?} */ foo: someFn };");
    testSameEs6("var x = { /** @return {?} */ [foo]: someFn };");
  }

  public void testExposeDeprecated() {
    testWarning("/** @expose */ var x = 0;", ANNOTATION_DEPRECATED);
  }

  public void testJSDocFunctionNodeAttachment() {
    testWarning("var a = /** @param {number} index */5;"
        + "/** @return boolean */function f(index){}", MISPLACED_ANNOTATION);
  }

  public void testJSDocDescAttachment() {
    testWarning(
        "function f() { return /** @type {string} */ (g(1 /** @desc x */)); };",
        MISPLACED_MSG_ANNOTATION);

    testWarning("/** @desc Foo. */ var bar = goog.getMsg('hello');",
        MISPLACED_MSG_ANNOTATION);
    testWarning("/** @desc Foo. */ x.y.z.bar = goog.getMsg('hello');",
        MISPLACED_MSG_ANNOTATION);
    testWarning("var msgs = {/** @desc x */ x: goog.getMsg('x')}",
        MISPLACED_MSG_ANNOTATION);
    testWarning("/** @desc Foo. */ bar = goog.getMsg('x');",
        MISPLACED_MSG_ANNOTATION);
  }

  public void testJSDocTypeAttachment() {
    testWarning(
        "function f() {  /** @type {string} */ if (true) return; };", MISPLACED_ANNOTATION);

    testWarning(
        "function f() {  /** @type {string} */  return; };", MISPLACED_ANNOTATION);
  }

  public void testJSDocOnExports() {
    testSame(LINE_JOINER.join(
        "goog.module('foo');",
        "/** @const {!Array<number>} */",
        "exports = [];"));
  }

  public void testMisplacedTypeAnnotation1() {
    // misuse with COMMA
    testWarning(
        "var o = {}; /** @type {string} */ o.prop1 = 1, o.prop2 = 2;", MISPLACED_ANNOTATION);
  }

  public void testMisplacedTypeAnnotation2() {
    // missing parentheses for the cast.
    testWarning(
        "var o = /** @type {string} */ getValue();",
        MISPLACED_ANNOTATION);
  }

  public void testMisplacedTypeAnnotation3() {
    // missing parentheses for the cast.
    testWarning(
        "var o = 1 + /** @type {string} */ value;",
        MISPLACED_ANNOTATION);
  }

  public void testMisplacedTypeAnnotation4() {
    // missing parentheses for the cast.
    testWarning(
        "var o = /** @type {!Array.<string>} */ ['hello', 'you'];",
        MISPLACED_ANNOTATION);
  }

  public void testMisplacedTypeAnnotation5() {
    // missing parentheses for the cast.
    testWarning(
        "var o = (/** @type {!Foo} */ {});",
        MISPLACED_ANNOTATION);
  }

  public void testMisplacedTypeAnnotation6() {
    testWarning(
        "var o = /** @type {function():string} */ function() {return 'str';}",
        MISPLACED_ANNOTATION);
  }

  public void testMisplacedTypeAnnotation7() {
    testWarning(
        "var x = /** @type {string} */ y;",
        MISPLACED_ANNOTATION);
  }

  public void testAllowedNocollapseAnnotation1() {
    testSame("var foo = {}; /** @nocollapse */ foo.bar = true;");
  }

  public void testAllowedNocollapseAnnotation2() {
    testSame(
        "/** @constructor */ function Foo() {};\n"
        + "var ns = {};\n"
        + "/** @nocollapse */ ns.bar = Foo.prototype.blah;");
  }

  public void testMisplacedNocollapseAnnotation1() {
    testWarning(
        "/** @constructor */ function foo() {};"
            + "/** @nocollapse */ foo.prototype.bar = function() {};",
        MISPLACED_ANNOTATION);
  }

  public void testNocollapseInExterns() {
    testSame("var foo = {}; /** @nocollapse */ foo.bar = true;",
        "foo.bar;", MISPLACED_ANNOTATION);
  }

  public void testArrowFuncAsConstructor() {
    testErrorEs6("/** @constructor */ var a = ()=>{}; var b = a();",
        ARROW_FUNCTION_AS_CONSTRUCTOR);
    testErrorEs6("var a = /** @constructor */ ()=>{}; var b = a();",
        ARROW_FUNCTION_AS_CONSTRUCTOR);
    testErrorEs6("/** @constructor */ let a = ()=>{}; var b = a();",
        ARROW_FUNCTION_AS_CONSTRUCTOR);
    testErrorEs6("/** @constructor */ const a = ()=>{}; var b = a();",
        ARROW_FUNCTION_AS_CONSTRUCTOR);
    testErrorEs6("var a; /** @constructor */ a = ()=>{}; var b = a();",
        ARROW_FUNCTION_AS_CONSTRUCTOR);
  }

  public void testDefaultParam() {
    testErrorEs6("function f(/** number */ x=0) {}", DEFAULT_PARAM_MUST_BE_MARKED_OPTIONAL);
    testSameEs6("function f(/** number= */ x=0) {}");
  }

  private void testBadTemplate(String code) {
    testWarning(code, MISPLACED_ANNOTATION);
  }

  public void testGoodTemplate1() {
    testSameEs6("/** @template T */ class C {}");
    testSameEs6("class C { /** @template T \n @param {T} a\n @param {T} b \n */ "
        + "constructor(a,b){} }");
    testSameEs6("class C {/** @template T \n @param {T} a\n @param {T} b \n */ method(a,b){} }");
    testSame("/** @template T \n @param {T} a\n @param {T} b\n */ var x = function(a, b){};");
    testSame("/** @constructor @template T */ var x = function(){};");
    testSame("/** @interface @template T */ var x = function(){};");
  }

  public void testGoodTemplate2() {
    testSame("/** @template T */ x.y.z = goog.defineClass(null, {constructor: function() {}});");
  }

  public void testGoodTemplate3() {
    testSame("var /** @template T */ x = goog.defineClass(null, {constructor: function() {}});");
  }

  public void testGoodTemplate4() {
    testSame("x.y.z = goog.defineClass(null, {/** @return T @template T */ m: function() {}});");
  }

  public void testBadTemplate1() {
    testBadTemplate("/** @template T */ foo();");
  }

  public void testBadTemplate2() {
    testBadTemplate(LINE_JOINER.join(
        "x.y.z = goog.defineClass(null, {",
        "  /** @template T */ constructor: function() {}",
        "});"));
  }

  public void testBadTemplate3() {
    testBadTemplate("/** @template T */ function f() {}");
    testBadTemplate("/** @template T */ var f = function() {};");
    testBadTemplate("/** @template T */ Foo.prototype.f = function() {};");
  }

  public void testBadTypedef() {
    testWarningEs6(
        "/** @typedef {{foo: string}} */ class C { constructor() { this.foo = ''; }}",
        MISPLACED_ANNOTATION);

    testWarning(
        LINE_JOINER.join(
            "/** @typedef {{foo: string}} */",
            "var C = goog.defineClass(null, {",
            "  constructor: function() { this.foo = ''; }",
            "});"),
        MISPLACED_ANNOTATION);
  }

  public void testNoSideEffectsInSrc() {
    testSame("/** @nosideeffects */ function foo() {}; foo();", MISPLACED_ANNOTATION);

    testSame("/** @nosideeffects */ function foo() {};", "foo();", null);
  }
}
