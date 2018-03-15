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
import static com.google.javascript.jscomp.CheckJSDoc.INVALID_DEFINE_ON_LET;
import static com.google.javascript.jscomp.CheckJSDoc.INVALID_MODIFIES_ANNOTATION;
import static com.google.javascript.jscomp.CheckJSDoc.INVALID_NO_SIDE_EFFECT_ANNOTATION;
import static com.google.javascript.jscomp.CheckJSDoc.MISPLACED_ANNOTATION;
import static com.google.javascript.jscomp.CheckJSDoc.MISPLACED_MSG_ANNOTATION;
import static com.google.javascript.jscomp.CheckJSDoc.MISPLACED_SUPPRESS;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * Tests for {@link CheckJSDoc}.
 *
 * @author chadkillingsworth@gmail.com (Chad Killingsworth)
 */

public final class CheckJsDocTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CheckJSDoc(compiler);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.MISPLACED_SUPPRESS, CheckLevel.WARNING);
    return options;
  }

  public void testInlineJsDoc_ES6() {
    testSame("function f(/** string */ x) {}");
    testSame("function f(/** number= */ x=3) {}");
    testSame("function f(/** !Object */ {x}) {}");
    testSame("function f(/** !Array */ [x]) {}");

    testWarning("function f([/** number */ x]) {}", MISPLACED_ANNOTATION);
  }

  public void testValidInlineJsDoc_ES6_withES6Modules() {
    testSame("export function f(/** string */ x) {};");
  }

  public void testInvalidInlineJsDoc_ES6_withES6Modules() {
    testWarning("export function f([/** number */ x]) {};", MISPLACED_ANNOTATION);
  }

  // TODO(tbreisacher): These should be a MISPLACED_ANNOTATION warning instead of silently failing.
  public void testInlineJsDocInsideObjectParams() {
    testSame("function f({ prop: {/** string */ x} }) {}");
    testSame("function f({ prop: {x: /** string */ y} }) {}");
    testSame("function f({ /** number */ x }) {}");
    testSame("function f({ prop: /** number */ x }) {}");
  }

  public void testInlineJsDocInsideObjectParams_withES6Modules() {
    testSame("export function f({ prop: {/** string */ x} }) {};");
  }

  public void testInvalidClassJsdoc() {
    testSame("class Foo { /** @param {number} x */ constructor(x) {}}");

    testWarning(
        "class Foo { /** @constructor */ constructor() {}}",
        DISALLOWED_MEMBER_JSDOC);

    testWarning(
        "class Foo { /** @interface */ constructor() {}}",
        DISALLOWED_MEMBER_JSDOC);

    testWarning(
        "class Foo { /** @extends {Foo} */ constructor() {}}",
        DISALLOWED_MEMBER_JSDOC);

    testWarning(
        "class Foo { /** @implements {Foo} */ constructor() {}}",
        DISALLOWED_MEMBER_JSDOC);
  }

  public void testValidClassJsdoc_withES6Modules() {
    testSame("export class Foo { /** @param {number} x */ constructor(x) {}; };");
  }

  public void testInvalidClassJsdoc_withES6Modules() {
    testWarning(
        "export class Foo { /** @constructor */ constructor() {}; };", DISALLOWED_MEMBER_JSDOC);
  }

  public void testMisplacedParamAnnotation() {
    testWarning(lines(
        "/** @param {string} x */ var Foo = goog.defineClass(null, {",
        "  constructor(x) {}",
        "});"), MISPLACED_ANNOTATION);

    testWarning(lines(
        "/** @param {string} x */ const Foo = class {",
        "  constructor(x) {}",
        "};"), MISPLACED_ANNOTATION);
  }

  public void testMisplacedParamAnnotation_withES6Modules() {
    testWarning(
        lines(
            "export /** @param {string} x */ var Foo = goog.defineClass(null, {",
            "  constructor(x) {}",
            "});"),
        MISPLACED_ANNOTATION);
  }

  public void testAbstract_method() {
    testSame("class Foo { /** @abstract */ doSomething() {}}");
    testSame(lines(
        "/** @constructor */",
        "var Foo = function() {};",
        "/** @abstract */",
        "Foo.prototype.something = function() {}"));
    testSame(lines(
        "/** @constructor */",
        "let Foo = function() {};",
        "/** @abstract */",
        "Foo.prototype.something = function() {}"));
    testSame(lines(
        "/** @constructor */",
        "const Foo = function() {};",
        "/** @abstract */",
        "Foo.prototype.something = function() {}"));
  }

  public void testAbstract_method_withES6Modules() {
    testSame("export class Foo { /** @abstract */ doSomething() {}; };");
  }

  public void testAbstract_getter_setter() {
    testSame("class Foo { /** @abstract */ get foo() {}}");
    testSame("class Foo { /** @abstract */ set foo(val) {}}");
    testWarning("class Foo { /** @abstract */ static get foo() {}}", MISPLACED_ANNOTATION);
    testWarning("class Foo { /** @abstract */ static set foo(val) {}}", MISPLACED_ANNOTATION);
  }

  public void testValidAbstract_getter_setter_withES6Modules() {
    testSame("export class Foo { /** @abstract */ get foo() {}; };");
  }

  public void testInvalidAbstract_getter_setter_withES6Modules() {
    testWarning(
        "export class Foo { /** @abstract */ static get foo() {}; };", MISPLACED_ANNOTATION);
  }

  public void testAbstract_nonEmptyMethod() {
    testWarning(
        "class Foo { /** @abstract */ doSomething() { return 0; }}",
        MISPLACED_ANNOTATION);
    testWarning(
        lines(
            "/** @constructor */",
            "var Foo = function() {};",
            "/** @abstract */",
            "Foo.prototype.something = function() { return 0; }"),
        MISPLACED_ANNOTATION);
  }

  public void testAbstract_nonEmptyMethod_withES6Modules() {
    testWarning(
        "export class Foo { /** @abstract */ doSomething() { return 0; }; };",
        MISPLACED_ANNOTATION);
  }

  public void testAbstract_staticMethod() {
    testWarning(
        "class Foo { /** @abstract */ static doSomething() {}}",
        MISPLACED_ANNOTATION);
    testWarning(
        lines(
            "/** @constructor */",
            "var Foo = function() {};",
            "/** @abstract */",
            "Foo.something = function() {}"),
        MISPLACED_ANNOTATION);
  }

  public void testAbstract_staticMethod_withES6Modules() {
    testWarning(
        "export class Foo { /** @abstract */ static doSomething() {}; };", MISPLACED_ANNOTATION);
  }

  public void testAbstract_class() {
    testSame("/** @abstract */ class Foo { constructor() {}}");
    testSame("/** @abstract */ exports.Foo = class {}");
    testSame("/** @abstract */ const Foo = class {}");
    testSame("/** @abstract @constructor */ exports.Foo = function() {}");
    testSame("/** @abstract @constructor */ var Foo = function() {};");
    testSame("/** @abstract @constructor */ var Foo = function() { var x = 1; };");
  }

  public void testAbstract_class_withES6Modules() {
    testSame("export /** @abstract */ class Foo { constructor() {}; };");
  }

  public void testAbstract_defineClass() {
    testSame("/** @abstract */ goog.defineClass(null, { constructor: function() {} });");
    testSame("/** @abstract */ var Foo = goog.defineClass(null, { constructor: function() {} });");
    testSame("/** @abstract */ ns.Foo = goog.defineClass(null, { constructor: function() {} });");
    testSame(lines(
        "/** @abstract */ ns.Foo = goog.defineClass(null, {",
        "  /** @abstract */ foo: function() {}",
        "});"));
    testSame(lines(
        "/** @abstract */ ns.Foo = goog.defineClass(null, {",
        "  /** @abstract */ foo() {}",
        "});"));
    testWarning("/** @abstract */ var Foo;", MISPLACED_ANNOTATION);
    testWarning(lines(
        "/** @abstract */ goog.defineClass(null, {",
        "  /** @abstract */ constructor: function() {}",
        "});"), MISPLACED_ANNOTATION);
    testWarning(lines(
        "/** @abstract */ goog.defineClass(null, {",
        "  /** @abstract */ constructor() {}",
        "});"), MISPLACED_ANNOTATION);
  }

  public void testValidAbstract_defineClass_withES6Modules() {
    testSame(
        lines(
            "export /** @abstract */ var Foo = goog.defineClass(null, {",
            "constructor: function() {} });"));
  }

  public void testInvalidAbstract_defineClass_withES6Modules() {
    testWarning("export /** @abstract */ var Foo;", MISPLACED_ANNOTATION);
  }

  public void testAbstract_constructor() {
    testWarning(
        "class Foo { /** @abstract */ constructor() {}}",
        MISPLACED_ANNOTATION);
    // ES5 constructors are treated as class definitions and tested above.

    // This is valid if foo() returns an abstract class constructor
    testSame(
        "/** @constructor */ var C = foo(); /** @abstract */ C.prototype.method = function() {};");
  }

  public void testInvalidAbstract_constructor_withES6Modules() {
    testWarning("export class Foo { /** @abstract */ constructor() {}; };", MISPLACED_ANNOTATION);
  }

  public void testValidAbstract_constructor_withES6Modules() {
    testSame(
        lines(
            "export /** @constructor */ var C = foo();",
            "/** @abstract */ C.prototype.method = function() {};"));
  }

  public void testAbstract_field() {
    testWarning(
        "class Foo { constructor() { /** @abstract */ this.x = 1;}}",
        MISPLACED_ANNOTATION);
    testWarning(
        lines(
            "/** @constructor */",
            "var Foo = function() {",
            "  /** @abstract */",
            "  this.x = 1;",
            "};"),
        MISPLACED_ANNOTATION);
  }

  public void testAbstract_field_withES6Modules() {
    testWarning(
        "export class Foo { constructor() { /** @abstract */ this.x = 1; }; };",
        MISPLACED_ANNOTATION);
  }

  public void testAbstract_var() {
    testWarning(
        "class Foo { constructor() {/** @abstract */ var x = 1;}}",
        MISPLACED_ANNOTATION);
    testWarning(
        lines(
            "/** @constructor */",
            "var Foo = function() {",
            "  /** @abstract */",
            "  var x = 1;",
            "};"),
        MISPLACED_ANNOTATION);
  }

  public void testAbstract_var_withES6Modules() {
    testWarning(
        "export class Foo { constructor() { /** @abstract */ var x = 1; }; };",
        MISPLACED_ANNOTATION);
  }

  public void testAbstract_function() {
    testWarning(
        "class Foo { constructor() {/** @abstract */ var x = function() {};}}",
        MISPLACED_ANNOTATION);
    testWarning(
        lines(
            "/** @constructor */",
            "var Foo = function() {",
            "  /** @abstract */",
            "  var x = function() {};",
            "};"),
        MISPLACED_ANNOTATION);
  }

  public void testAbstract_function_withES6Modules() {
    testWarning(
        "export class Foo { constructor() { /** @abstract */ var x = function() {}; }}",
        MISPLACED_ANNOTATION);
  }

  public void testInlineJSDoc() {
    testSame("function f(/** string */ x) {}");
    testSame("function f(/** @type {string} */ x) {}");

    testSame("var /** string */ x = 'x';");
    testSame("var /** @type {string} */ x = 'x';");
    testSame("var /** string */ x, /** number */ y;");
    testSame("var /** @type {string} */ x, /** @type {number} */ y;");
  }

  public void testInlineJSDoc_withES6Modules() {
    testSame("export function f(/** string */ x) {}");
  }

  public void testFunctionJSDocOnMethods() {
    testSame("class Foo { /** @return {?} */ bar() {} }");
    testSame("class Foo { /** @return {?} */ static bar() {} }");
    testSame("class Foo { /** @return {?} */ get bar() {} }");
    testSame("class Foo { /** @param {?} x */ set bar(x) {} }");

    testSame("class Foo { /** @return {?} */ [bar]() {} }");
    testSame("class Foo { /** @return {?} */ static [bar]() {} }");
    testSame("class Foo { /** @return {?} */ get [bar]() {} }");
    testSame("class Foo { /** @return {?} x */ set [bar](x) {} }");
  }

  public void testValidFunctionJSDocOnMethods_withES6Modules() {
    testSame("export class Foo { /** @return {?} */ bar() {} }");
  }

  public void testObjectLiterals() {
    testSame("var o = { /** @type {?} */ x: y };");
    testWarning("var o = { x: /** @type {?} */ y };", MISPLACED_ANNOTATION);
  }

  public void testValidObjectLiterals_withES6Modules() {
    testSame("export var o = { /** @type {?} */ x: y };");
  }

  public void testInvalidObjectLiterals_withES6Modules() {
    testWarning("export var o = { x: /** @type {?} */ y };", MISPLACED_ANNOTATION);
  }

  public void testMethodsOnObjectLiterals() {
    testSame("var x = { /** @return {?} */ foo() {} };");
    testSame("var x = { /** @return {?} */ [foo]() {} };");
    testSame("var x = { /** @return {?} */ foo: someFn };");
    testSame("var x = { /** @return {?} */ [foo]: someFn };");
  }

  public void testMethodsOnObjectLiterals_withES6Modules() {
    testSame("export var x = { /** @return {?} */ foo() {} };");
  }

  public void testExposeDeprecated() {
    testWarning("/** @expose */ var x = 0;", ANNOTATION_DEPRECATED);
  }

  public void testExposeDeprecated_withES6Modules() {
    testWarning("export /** @expose */ var x = 0;", ANNOTATION_DEPRECATED);
  }

  public void testJSDocFunctionNodeAttachment() {
    testWarning("var a = /** @param {number} index */5;"
        + "/** @return boolean */function f(index){}", MISPLACED_ANNOTATION);
  }

  public void testJSDocFunctionNodeAttachment_withES6Modules() {
    testWarning(
        lines(
            "export var a = /** @param {number} index */ 5;",
            "export /** @return boolean */ function f(index){}"),
        MISPLACED_ANNOTATION);
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

  public void testJSDocDescInExterns() {
    testWarning("/** @desc Foo. */ x.y.z.MSG_bar;", MISPLACED_MSG_ANNOTATION);
    testSame(externs("/** @desc Foo. */ x.y.z.MSG_bar;"), srcs(""));
  }

  public void testJSDocTypeAttachment() {
    testWarning(
        "function f() {  /** @type {string} */ if (true) return; };", MISPLACED_ANNOTATION);

    testWarning(
        "function f() {  /** @type {string} */  return; };", MISPLACED_ANNOTATION);
  }

  public void testJSDocTypeAttachment_withES6Modules() {
    testWarning(
        "export function f() { /** @type {string} */ if (true) return; };", MISPLACED_ANNOTATION);
  }

  public void testJSDocOnExports() {
    testSame(lines(
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

  public void testMisplacedTypeAnnotation_withES6Modules() {
    testWarning(
        "export var o = {}; /** @type {string} */ o.prop1 = 1, o.prop2 = 2;", MISPLACED_ANNOTATION);
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

  public void testAllowedNocollapseAnnotation_withES6Modules() {
    testSame("export var foo = {}; /** @nocollapse */ foo.bar = true;");
  }

  public void testMisplacedNocollapseAnnotation() {
    testWarning(
        "/** @constructor */ function foo() {};"
            + "/** @nocollapse */ foo.prototype.bar = function() {};",
        MISPLACED_ANNOTATION);
  }

  public void testMisplacedNocollapseAnnotation_withES6Modules() {
    testWarning(
        lines(
            "export /** @constructor */ function foo() {};",
            "/** @nocollapse */ foo.prototype.bar = function() {};"),
        MISPLACED_ANNOTATION);
  }

  public void testNocollapseInExterns() {
    test(
        externs("var foo = {}; /** @nocollapse */ foo.bar = true;"),
        srcs("foo.bar;"),
        warning(MISPLACED_ANNOTATION));
  }

  public void testNocollapseInExterns_withES6Modules() {
    test(
        externs("export var foo = {}; /** @nocollapse */ foo.bar = true;"),
        srcs("foo.bar;"),
        warning(MISPLACED_ANNOTATION));
  }

  public void testArrowFuncAsConstructor() {
    testError("/** @constructor */ var a = ()=>{}; var b = a();",
        ARROW_FUNCTION_AS_CONSTRUCTOR);
    testError("var a = /** @constructor */ ()=>{}; var b = a();",
        ARROW_FUNCTION_AS_CONSTRUCTOR);
    testError("/** @constructor */ let a = ()=>{}; var b = a();",
        ARROW_FUNCTION_AS_CONSTRUCTOR);
    testError("/** @constructor */ const a = ()=>{}; var b = a();",
        ARROW_FUNCTION_AS_CONSTRUCTOR);
    testError("var a; /** @constructor */ a = ()=>{}; var b = a();",
        ARROW_FUNCTION_AS_CONSTRUCTOR);
  }

  public void testArrowFuncAsConstructor_withES6Modules() {
    testError(
        "export /** @constructor */ var a = ()=>{}; var b = a();", ARROW_FUNCTION_AS_CONSTRUCTOR);
  }

  public void testDefaultParam() {
    testError("function f(/** number */ x=0) {}", DEFAULT_PARAM_MUST_BE_MARKED_OPTIONAL);
    testSame("function f(/** number= */ x=0) {}");
  }

  public void testInvalidDefaultParam_withES6Modules() {
    testError("export function f(/** number */ x=0) {}", DEFAULT_PARAM_MUST_BE_MARKED_OPTIONAL);
  }

  public void testValidDefaultParam_withES6Modules() {
    testSame("export function f(/** number= */ x=0) {}");
  }

  private void testBadTemplate(String code) {
    testWarning(code, MISPLACED_ANNOTATION);
  }

  public void testGoodTemplate1() {
    testSame("/** @template T */ class C {}");
    testSame("class C { /** @template T \n @param {T} a\n @param {T} b \n */ "
        + "constructor(a,b){} }");
    testSame("class C {/** @template T \n @param {T} a\n @param {T} b \n */ method(a,b){} }");
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

  public void testGoodTemplate_withES6Modules() {
    testSame(
        lines(
            "export class C { /** @template T \n @param {T} a\n @param {T} b \n */ ",
            "constructor(a,b){} }"));
  }

  public void testBadTemplate1() {
    testBadTemplate("/** @template T */ foo();");
  }

  public void testBadTemplate2() {
    testBadTemplate(lines(
        "x.y.z = goog.defineClass(null, {",
        "  /** @template T */ constructor: function() {}",
        "});"));
  }

  public void testBadTemplate3() {
    testBadTemplate("/** @template T */ function f() {}");
    testBadTemplate("/** @template T */ var f = function() {};");
    testBadTemplate("/** @template T */ Foo.prototype.f = function() {};");
  }

  public void testBadTemplate_withES6Modules() {
    testBadTemplate("export /** @template T */ function f() {}");
  }

  public void testBadTypedef() {
    testWarning(
        "/** @typedef {{foo: string}} */ class C { constructor() { this.foo = ''; }}",
        MISPLACED_ANNOTATION);

    testWarning(
        lines(
            "/** @typedef {{foo: string}} */",
            "var C = goog.defineClass(null, {",
            "  constructor: function() { this.foo = ''; }",
            "});"),
        MISPLACED_ANNOTATION);
  }

  public void testBadTypedef_withES6Modules() {
    testWarning(
        "export /** @typedef {{foo: string}} */ class C { constructor() { this.foo = ''; }}",
        MISPLACED_ANNOTATION);
  }

  public void testInvalidAnnotation1() throws Exception {
    testError("/** @nosideeffects */ function foo() {}", INVALID_NO_SIDE_EFFECT_ANNOTATION);
  }

  public void testInvalidAnnotation2() throws Exception {
    testError("var f = /** @nosideeffects */ function() {}", INVALID_NO_SIDE_EFFECT_ANNOTATION);
  }

  public void testInvalidAnnotation3() throws Exception {
    testError("/** @nosideeffects */ var f = function() {}", INVALID_NO_SIDE_EFFECT_ANNOTATION);
  }

  public void testInvalidAnnotation4() throws Exception {
    testError(
        "var f = function() {};" + "/** @nosideeffects */ f.x = function() {}",
        INVALID_NO_SIDE_EFFECT_ANNOTATION);
  }

  public void testInvalidAnnotation5() throws Exception {
    testError(
        "var f = function() {};" + "f.x = /** @nosideeffects */ function() {}",
        INVALID_NO_SIDE_EFFECT_ANNOTATION);
  }

  public void testInvalidAnnotation_withES6Modules() {
    testError(
        "export /** @nosideeffects */ function foo() {}", INVALID_NO_SIDE_EFFECT_ANNOTATION);
  }

  public void testInvalidModifiesAnnotation() throws Exception {
    testError("/** @modifies {this} */ var f = function() {};", INVALID_MODIFIES_ANNOTATION);
  }

  public void testInvalidModifiesAnnotation_withES6Modules() {
    testError(
        "export /** @modifies {this} */ var f = function() {};", INVALID_MODIFIES_ANNOTATION);
  }

  public void testInvalidDefinesVariableDeclaration() {
    testError("/** @define {boolean} */ let DEF = true;", INVALID_DEFINE_ON_LET);
    testError("/** @define {number} */ let DEF = 3;", INVALID_DEFINE_ON_LET);
    testSame("/** @define {boolean} */ var DEF = true;");
    testSame("/** @define {boolean} */ const DEF = true;");
    testError("/** @define {boolean} */ let DEF;", INVALID_DEFINE_ON_LET);
    testError("/** @define {boolean} */ let EXTERN_DEF;", INVALID_DEFINE_ON_LET);
    testSame("let a = {}; /** @define {boolean} */ a.B = false;");
  }

  public void testInvalidSuppress() {
    testSame("/** @suppress {missingRequire} */ var x = new y.Z();");
    testSame("/** @suppress {missingRequire} */ function f() { var x = new y.Z(); }");
    testSame("/** @suppress {missingRequire} */ var f = function() { var x = new y.Z(); }");
    testSame(
        lines(
            "var obj = {",
            "  /** @suppress {uselessCode} */",
            "  f: function() {},",
            "}"));
    testSame(
        lines(
            "var obj = {",
            "  /** @suppress {uselessCode} */",
            "  f() {},",
            "}"));
    testSame(
        lines(
            "class Example {",
            "  /** @suppress {uselessCode} */",
            "  f() {}",
            "}"));
    testSame(
        lines(
            "class Example {",
            "  /** @suppress {uselessCode} */",
            "  static f() {}",
            "}"));
    testSame(
        lines(
            "class Example {",
            "  /** @suppress {uselessCode} */",
            "  get f() {}",
            "}"));
    testSame(
        lines(
            "class Example {",
            "  /**",
            "   * @param {string} val",
            "   * @suppress {uselessCode}",
            "   */",
            "  set f(val) {}",
            "}"));

    testSame("/** @suppress {extraRequire} */ goog.require('unused.Class');");
    testSame("/** @const @suppress {duplicate} */ var google = {};");
    testSame("/** @suppress {const} */ var google = {};");
    testSame("/** @suppress {duplicate} @type {Foo} */ ns.something.foo;");
    testSame("/** @suppress {with} */ with (o) { x; }");

    testWarning("/** @suppress {uselessCode} */ goog.require('unused.Class');", MISPLACED_SUPPRESS);
    testWarning("const {/** @suppress {duplicate} */ Foo} = foo();", MISPLACED_SUPPRESS);
    testWarning("foo(/** @suppress {duplicate} */ ns.x = 7);", MISPLACED_SUPPRESS);
  }
}
