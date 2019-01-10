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
import static com.google.javascript.jscomp.CheckJSDoc.JSDOC_IN_BLOCK_COMMENT;
import static com.google.javascript.jscomp.CheckJSDoc.MISPLACED_ANNOTATION;
import static com.google.javascript.jscomp.CheckJSDoc.MISPLACED_MSG_ANNOTATION;
import static com.google.javascript.jscomp.CheckJSDoc.MISPLACED_SUPPRESS;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.Config.JsDocParsing;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link CheckJSDoc}.
 *
 * @author chadkillingsworth@gmail.com (Chad Killingsworth)
 */
@RunWith(JUnit4.class)
public final class CheckJsDocTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CheckJSDoc(compiler);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.MISPLACED_SUPPRESS, CheckLevel.WARNING);
    options.setParseJsDocDocumentation(JsDocParsing.INCLUDE_DESCRIPTIONS_WITH_WHITESPACE);
    options.setPreserveDetailedSourceInfo(true);
    return options;
  }

  @Test
  public void testInlineJsDocOnObjectPatternTargetNames() {
    testSame("let {/** string */ prop} = {prop: 'hi'};");
    testSame("let {/** string */ prop = 'default'} = {prop: 'hi'};");
    testSame("let {prop: /** string */ x} = {prop: 'hi'};");
    testSame("let {prop: /** string */ x = 'default'} = {prop: 'hi'};");
    // TODO(bradfordcsmith): These two cases should be misplaced annotations.
    testSame("let {/** string */ prop: x} = {prop: 'hi'};");
    testSame("let {/** string */ prop: x = 'default'} = {prop: 'hi'};");
  }

  @Test
  public void testInlineJsDocOnObjectPatternTargetNamesInAssign() {
    // We don't allow any inline type annotations in object pattern assigns.
    // Simple variable names must have their type declared upon the variable declaration, and
    // qualified names must be declared outside of a destructuring pattern.
    testWarning("({/** string */ prop} = {prop: 'hi'});", CheckJSDoc.MISPLACED_ANNOTATION);
    testWarning(
        "({['prop']: /** string */ prop} = {prop: 'hi'});", CheckJSDoc.MISPLACED_ANNOTATION);
    testWarning("({prop: /** string */ ns.prop} = {prop: 'hi'});", CheckJSDoc.MISPLACED_ANNOTATION);
    testWarning(
        "({prop: /** string */ ns.prop = 'default'} = {prop: 'hi'});",
        CheckJSDoc.MISPLACED_ANNOTATION);
    testWarning(
        "({prop: /** string */ f().y['z']} = {prop: 'hi'});", CheckJSDoc.MISPLACED_ANNOTATION);
  }

  @Test
  public void testInlineJsDocOnArrayPatternTargetNames() {
    testSame("let [/** string */ x] = ['hi'];");
    testSame("let [/** string */ x = 'lo'] = ['hi'];");
    testSame("try {} catch ([/** string */ x]) {}");
  }

  @Test
  public void testInlineJsDocOnArrayPatternTargetInAssign() {
    // We don't allow any inline type annotations in array pattern assigns.
    // Simple variable names must have their type declared upon the variable declaration, and
    // qualified names must be declared outside of a destructuring pattern.
    testWarning("[/** string */ x] = ['hi'];", CheckJSDoc.MISPLACED_ANNOTATION);
    testWarning("[/** string */ x = 'foo'] = ['hi'];", CheckJSDoc.MISPLACED_ANNOTATION);
    testWarning("[/** string */ x.y] = ['hi'];", CheckJSDoc.MISPLACED_ANNOTATION);
    testWarning("[/** string */ x.y = 'lo'] = ['hi'];", CheckJSDoc.MISPLACED_ANNOTATION);
    testWarning("[/** string */ f().y['z']] = [];", CheckJSDoc.MISPLACED_ANNOTATION);
  }

  @Test
  public void testInlineJsDocOnDeclaration() {
    testSame("var /** number */ x;");
    testSame("var /** number */ x = 3;");
    // See testInlineJsDocOnArrayPatternTargetNames() for cases where the inline JSDoc is attached
    // to the names within the pattern.
    testWarning("var /** !Array<number> */ [x, y] = someArr;", CheckJSDoc.MISPLACED_ANNOTATION);
    testWarning("var /** {x: number} */ {x} = someObj;", CheckJSDoc.MISPLACED_ANNOTATION);
  }

  @Test
  public void testInvalidJsDocOnDestructuringDeclaration() {
    // Type annotations are not allowed on any declaration containing >=1 destructuring pattern.
    testWarning("/** @type {number} */ const [a] = arr;", CheckJSDoc.MISPLACED_ANNOTATION);
    testWarning("/** @type {number} */ const {a} = obj;", CheckJSDoc.MISPLACED_ANNOTATION);
    testWarning("/** @type {number} */ const a = 1, [b] = arr;", CheckJSDoc.MISPLACED_ANNOTATION);
    testWarning("/** @type {number} */ const a = 1, {b} = obj;", CheckJSDoc.MISPLACED_ANNOTATION);
  }

  @Test
  public void testInlineJsDoc_ES6() {
    testSame("function f(/** string */ x) {}");
    testSame("function f(/** number= */ x=3) {}");
    testSame("function f(/** !Object */ {x}) {}");
    testSame("function f(/** !Array */ [x]) {}");
    testSame("function f([/** number */ x]) {}");
  }

  @Test
  public void testValidInlineJsDoc_ES6_withES6Modules() {
    testSame("export function f(/** string */ x) {};");
  }

  @Test
  public void testInlineJsDoc_ES6_withES6Modules() {
    testSame("export function f([/** number */ x]) {};");
  }

  // TODO(tbreisacher): These should be a MISPLACED_ANNOTATION warning instead of silently failing.
  @Test
  public void testInlineJsDocInsideObjectParams() {
    testSame("function f({ prop: {/** string */ x} }) {}");
    testSame("function f({ prop: {x: /** string */ y} }) {}");
    testSame("function f({ /** number */ x }) {}");
    testSame("function f({ prop: /** number */ x }) {}");
  }

  @Test
  public void testInlineJsDocInsideObjectParams_withES6Modules() {
    testSame("export function f({ prop: {/** string */ x} }) {};");
  }

  @Test
  public void testJsDocOnCatchVariables() {
    testSame("try {} catch (/** @type {number} */ n) {}");
    // See testInlineJsDocOnArrayPatternTargetNames for cases where the inline JsDoc is attached to
    // the names within the pattern.
    testWarning(
        "try {} catch (/** @type {!Array<number>} */ [x, y]) {}", CheckJSDoc.MISPLACED_ANNOTATION);
  }

  @Test
  public void testInvalidJSDocOnName() {
    testWarning("var f = () => /** @type {number} */ n;", CheckJSDoc.MISPLACED_ANNOTATION);
    testWarning("var f = /** @type {number} */ n;", CheckJSDoc.MISPLACED_ANNOTATION);
  }

  @Test
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

  @Test
  public void testValidClassJsdoc_withES6Modules() {
    testSame("export class Foo { /** @param {number} x */ constructor(x) {}; };");
  }

  @Test
  public void testInvalidClassJsdoc_withES6Modules() {
    testWarning(
        "export class Foo { /** @constructor */ constructor() {}; };", DISALLOWED_MEMBER_JSDOC);
  }

  @Test
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

  @Test
  public void testMisplacedParamAnnotation_withES6Modules() {
    testWarning(
        lines(
            "export /** @param {string} x */ var Foo = goog.defineClass(null, {",
            "  constructor(x) {}",
            "});"),
        MISPLACED_ANNOTATION);
  }

  @Test
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

  @Test
  public void testAbstract_method_withES6Modules() {
    testSame("export class Foo { /** @abstract */ doSomething() {}; };");
  }

  @Test
  public void testAbstract_getter_setter() {
    testSame("class Foo { /** @abstract */ get foo() {}}");
    testSame("class Foo { /** @abstract */ set foo(val) {}}");
    testWarning("class Foo { /** @abstract */ static get foo() {}}", MISPLACED_ANNOTATION);
    testWarning("class Foo { /** @abstract */ static set foo(val) {}}", MISPLACED_ANNOTATION);
  }

  @Test
  public void testValidAbstract_getter_setter_withES6Modules() {
    testSame("export class Foo { /** @abstract */ get foo() {}; };");
  }

  @Test
  public void testInvalidAbstract_getter_setter_withES6Modules() {
    testWarning(
        "export class Foo { /** @abstract */ static get foo() {}; };", MISPLACED_ANNOTATION);
  }

  @Test
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

  @Test
  public void testAbstract_nonEmptyMethod_withES6Modules() {
    testWarning(
        "export class Foo { /** @abstract */ doSomething() { return 0; }; };",
        MISPLACED_ANNOTATION);
  }

  @Test
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

  @Test
  public void testAbstract_staticMethod_withES6Modules() {
    testWarning(
        "export class Foo { /** @abstract */ static doSomething() {}; };", MISPLACED_ANNOTATION);
  }

  @Test
  public void testAbstract_class() {
    testSame("/** @abstract */ class Foo { constructor() {}}");
    testSame("/** @abstract */ exports.Foo = class {}");
    testSame("/** @abstract */ const Foo = class {}");
    testSame("/** @abstract @constructor */ exports.Foo = function() {}");
    testSame("/** @abstract @constructor */ var Foo = function() {};");
    testSame("/** @abstract @constructor */ var Foo = function() { var x = 1; };");
  }

  @Test
  public void testAbstract_class_withES6Modules() {
    testSame("export /** @abstract */ class Foo { constructor() {}; };");
  }

  @Test
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

  @Test
  public void testValidAbstract_defineClass_withES6Modules() {
    testSame(
        lines(
            "export /** @abstract */ var Foo = goog.defineClass(null, {",
            "constructor: function() {} });"));
  }

  @Test
  public void testInvalidAbstract_defineClass_withES6Modules() {
    testWarning("export /** @abstract */ var Foo;", MISPLACED_ANNOTATION);
  }

  @Test
  public void testAbstract_constructor() {
    testWarning(
        "class Foo { /** @abstract */ constructor() {}}",
        MISPLACED_ANNOTATION);
    // ES5 constructors are treated as class definitions and tested above.

    // This is valid if foo() returns an abstract class constructor
    testSame(
        "/** @constructor */ var C = foo(); /** @abstract */ C.prototype.method = function() {};");
  }

  @Test
  public void testInvalidAbstract_constructor_withES6Modules() {
    testWarning("export class Foo { /** @abstract */ constructor() {}; };", MISPLACED_ANNOTATION);
  }

  @Test
  public void testValidAbstract_constructor_withES6Modules() {
    testSame(
        lines(
            "export /** @constructor */ var C = foo();",
            "/** @abstract */ C.prototype.method = function() {};"));
  }

  @Test
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

  @Test
  public void testAbstract_field_withES6Modules() {
    testWarning(
        "export class Foo { constructor() { /** @abstract */ this.x = 1; }; };",
        MISPLACED_ANNOTATION);
  }

  @Test
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

  @Test
  public void testAbstract_var_withES6Modules() {
    testWarning(
        "export class Foo { constructor() { /** @abstract */ var x = 1; }; };",
        MISPLACED_ANNOTATION);
  }

  @Test
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

  @Test
  public void testAbstract_function_withES6Modules() {
    testWarning(
        "export class Foo { constructor() { /** @abstract */ var x = function() {}; }}",
        MISPLACED_ANNOTATION);
  }

  @Test
  public void testInlineJSDoc() {
    testSame("function f(/** string */ x) {}");
    testSame("function f(/** @type {string} */ x) {}");

    testSame("var /** string */ x = 'x';");
    testSame("var /** @type {string} */ x = 'x';");
    testSame("var /** string */ x, /** number */ y;");
    testSame("var /** @type {string} */ x, /** @type {number} */ y;");
  }

  @Test
  public void testInlineJSDoc_withES6Modules() {
    testSame("export function f(/** string */ x) {}");
  }

  @Test
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

  @Test
  public void testValidFunctionJSDocOnMethods_withES6Modules() {
    testSame("export class Foo { /** @return {?} */ bar() {} }");
  }

  @Test
  public void testObjectLiterals() {
    testSame("var o = { /** @type {?} */ x: y };");
    testWarning("var o = { x: /** @type {?} */ y };", MISPLACED_ANNOTATION);
  }

  @Test
  public void testValidObjectLiterals_withES6Modules() {
    testSame("export var o = { /** @type {?} */ x: y };");
  }

  @Test
  public void testInvalidObjectLiterals_withES6Modules() {
    testWarning("export var o = { x: /** @type {?} */ y };", MISPLACED_ANNOTATION);
  }

  @Test
  public void testMethodsOnObjectLiterals() {
    testSame("var x = { /** @return {?} */ foo() {} };");
    testSame("var x = { /** @return {?} */ [foo]() {} };");
    testSame("var x = { /** @return {?} */ foo: someFn };");
    testSame("var x = { /** @return {?} */ [foo]: someFn };");
  }

  @Test
  public void testMethodsOnObjectLiterals_withES6Modules() {
    testSame("export var x = { /** @return {?} */ foo() {} };");
  }

  @Test
  public void testExposeDeprecated() {
    testWarning("/** @expose */ var x = 0;", ANNOTATION_DEPRECATED);
  }

  @Test
  public void testExposeDeprecated_withES6Modules() {
    testWarning("export /** @expose */ var x = 0;", ANNOTATION_DEPRECATED);
  }

  @Test
  public void testJSDocFunctionNodeAttachment() {
    testWarning("var a = /** @param {number} index */5;"
        + "/** @return boolean */function f(index){}", MISPLACED_ANNOTATION);
  }

  @Test
  public void testJSDocFunctionNodeAttachment_withES6Modules() {
    testWarning(
        lines(
            "export var a = /** @param {number} index */ 5;",
            "export /** @return boolean */ function f(index){}"),
        MISPLACED_ANNOTATION);
  }

  @Test
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

    testWarning("/** @desc Foo. */ var {bar} = goog.getMsg('x');", MISPLACED_MSG_ANNOTATION);
    testWarning("/** @desc Foo. */ let {bar} = goog.getMsg('x');", MISPLACED_MSG_ANNOTATION);
    testWarning("/** @desc Foo. */ const {bar} = goog.getMsg('x');", MISPLACED_MSG_ANNOTATION);
    testWarning(
        "var bar;\n/** @desc Foo. */ ({bar} = goog.getMsg('x'));",
        MISPLACED_MSG_ANNOTATION);
  }

  @Test
  public void testJSDocDescInExterns() {
    testWarning("/** @desc Foo. */ x.y.z.MSG_bar;", MISPLACED_MSG_ANNOTATION);
    testSame(externs("/** @desc Foo. */ x.y.z.MSG_bar;"), srcs(""));
  }

  @Test
  public void testJSDocTypeAttachment() {
    testWarning(
        "function f() {  /** @type {string} */ if (true) return; };", MISPLACED_ANNOTATION);

    testWarning(
        "function f() {  /** @type {string} */  return; };", MISPLACED_ANNOTATION);
  }

  @Test
  public void testJSDocTypeAttachment_withES6Modules() {
    testWarning(
        "export function f() { /** @type {string} */ if (true) return; };", MISPLACED_ANNOTATION);
  }

  @Test
  public void testJSDocOnExports() {
    testSame(lines(
        "goog.module('foo');",
        "/** @const {!Array<number>} */",
        "exports = [];"));
  }

  @Test
  public void testMisplacedTypeAnnotation1() {
    // misuse with COMMA
    testWarning(
        "var o = {}; /** @type {string} */ o.prop1 = 1, o.prop2 = 2;", MISPLACED_ANNOTATION);
  }

  @Test
  public void testMisplacedTypeAnnotation2() {
    // missing parentheses for the cast.
    testWarning(
        "var o = /** @type {string} */ getValue();",
        MISPLACED_ANNOTATION);
  }

  @Test
  public void testMisplacedTypeAnnotation3() {
    // missing parentheses for the cast.
    testWarning(
        "var o = 1 + /** @type {string} */ value;",
        MISPLACED_ANNOTATION);
  }

  @Test
  public void testMisplacedTypeAnnotation4() {
    // missing parentheses for the cast.
    testWarning(
        "var o = /** @type {!Array.<string>} */ ['hello', 'you'];",
        MISPLACED_ANNOTATION);
  }

  @Test
  public void testMisplacedTypeAnnotation5() {
    // missing parentheses for the cast.
    testWarning(
        "var o = (/** @type {!Foo} */ {});",
        MISPLACED_ANNOTATION);
  }

  @Test
  public void testMisplacedTypeAnnotation6() {
    testWarning(
        "var o = /** @type {function():string} */ function() {return 'str';}",
        MISPLACED_ANNOTATION);
  }

  @Test
  public void testMisplacedTypeAnnotation7() {
    testWarning(
        "var x = /** @type {string} */ y;",
        MISPLACED_ANNOTATION);
  }

  @Test
  public void testMisplacedTypeAnnotation_withES6Modules() {
    testWarning(
        "export var o = {}; /** @type {string} */ o.prop1 = 1, o.prop2 = 2;", MISPLACED_ANNOTATION);
  }

  @Test
  public void testAllowedNocollapseAnnotation1() {
    testSame("var foo = {}; /** @nocollapse */ foo.bar = true;");
  }

  @Test
  public void testAllowedNocollapseAnnotation2() {
    testSame(
        "/** @constructor */ function Foo() {};\n"
        + "var ns = {};\n"
        + "/** @nocollapse */ ns.bar = Foo.prototype.blah;");
  }

  @Test
  public void testAllowedNocollapseAnnotation_withES6Modules() {
    testSame("export var foo = {}; /** @nocollapse */ foo.bar = true;");
  }

  @Test
  public void testMisplacedNocollapseAnnotation() {
    testWarning(
        "/** @constructor */ function foo() {};"
            + "/** @nocollapse */ foo.prototype.bar = function() {};",
        MISPLACED_ANNOTATION);
  }

  @Test
  public void testMisplacedNocollapseAnnotation_withES6Modules() {
    testWarning(
        lines(
            "export /** @constructor */ function foo() {};",
            "/** @nocollapse */ foo.prototype.bar = function() {};"),
        MISPLACED_ANNOTATION);
  }

  @Test
  public void testNocollapseInExterns() {
    test(
        externs("var foo = {}; /** @nocollapse */ foo.bar = true;"),
        srcs("foo.bar;"),
        warning(MISPLACED_ANNOTATION));
  }

  @Test
  public void testNocollapseInExterns_withES6Modules() {
    test(
        externs("export var foo = {}; /** @nocollapse */ foo.bar = true;"),
        srcs("foo.bar;"),
        warning(MISPLACED_ANNOTATION));
  }

  @Test
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

  @Test
  public void testArrowFuncAsConstructor_withES6Modules() {
    testError(
        "export /** @constructor */ var a = ()=>{}; var b = a();", ARROW_FUNCTION_AS_CONSTRUCTOR);
  }

  @Test
  public void testDefaultParam() {
    testError("function f(/** number */ x=0) {}", DEFAULT_PARAM_MUST_BE_MARKED_OPTIONAL);
    testSame("function f(/** number= */ x=0) {}");
  }

  @Test
  public void testInvalidDefaultParam_withES6Modules() {
    testError("export function f(/** number */ x=0) {}", DEFAULT_PARAM_MUST_BE_MARKED_OPTIONAL);
  }

  @Test
  public void testValidDefaultParam_withES6Modules() {
    testSame("export function f(/** number= */ x=0) {}");
  }

  private void testBadTemplate(String code) {
    testWarning(code, MISPLACED_ANNOTATION);
  }

  @Test
  public void testGoodTemplate1() {
    testSame("/** @template T */ class C {}");
    testSame("class C { /** @template T \n @param {T} a\n @param {T} b \n */ "
        + "constructor(a,b){} }");
    testSame("class C {/** @template T \n @param {T} a\n @param {T} b \n */ method(a,b){} }");
    testSame("/** @template T \n @param {T} a\n @param {T} b\n */ var x = function(a, b){};");
    testSame("/** @constructor @template T */ var x = function(){};");
    testSame("/** @interface @template T */ var x = function(){};");
  }

  @Test
  public void testGoodTemplate2() {
    testSame("/** @template T */ x.y.z = goog.defineClass(null, {constructor: function() {}});");
  }

  @Test
  public void testGoodTemplate3() {
    testSame("var /** @template T */ x = goog.defineClass(null, {constructor: function() {}});");
  }

  @Test
  public void testGoodTemplate4() {
    testSame("x.y.z = goog.defineClass(null, {/** @return T @template T */ m: function() {}});");
  }

  @Test
  public void testGoodTemplate_withES6Modules() {
    testSame(
        lines(
            "export class C { /** @template T \n @param {T} a\n @param {T} b \n */ ",
            "constructor(a,b){} }"));
  }

  @Test
  public void testBadTemplate1() {
    testBadTemplate("/** @template T */ foo();");
  }

  @Test
  public void testBadTemplate2() {
    testBadTemplate(lines(
        "x.y.z = goog.defineClass(null, {",
        "  /** @template T */ constructor: function() {}",
        "});"));
  }

  @Test
  public void testBadTemplate3() {
    testBadTemplate("/** @template T */ function f() {}");
    testBadTemplate("/** @template T */ var f = function() {};");
    testBadTemplate("/** @template T */ Foo.prototype.f = function() {};");
  }

  @Test
  public void testBadTemplate_withES6Modules() {
    testBadTemplate("export /** @template T */ function f() {}");
  }

  @Test
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

  @Test
  public void testBadTypedef_withES6Modules() {
    testWarning(
        "export /** @typedef {{foo: string}} */ class C { constructor() { this.foo = ''; }}",
        MISPLACED_ANNOTATION);
  }

  @Test
  public void testInvalidAnnotation1() {
    testError("/** @nosideeffects */ function foo() {}", INVALID_NO_SIDE_EFFECT_ANNOTATION);
  }

  @Test
  public void testInvalidAnnotation2() {
    testError("var f = /** @nosideeffects */ function() {}", INVALID_NO_SIDE_EFFECT_ANNOTATION);
  }

  @Test
  public void testInvalidAnnotation3() {
    testError("/** @nosideeffects */ var f = function() {}", INVALID_NO_SIDE_EFFECT_ANNOTATION);
  }

  @Test
  public void testInvalidAnnotation4() {
    testError(
        "var f = function() {};" + "/** @nosideeffects */ f.x = function() {}",
        INVALID_NO_SIDE_EFFECT_ANNOTATION);
  }

  @Test
  public void testInvalidAnnotation5() {
    testError(
        "var f = function() {};" + "f.x = /** @nosideeffects */ function() {}",
        INVALID_NO_SIDE_EFFECT_ANNOTATION);
  }

  @Test
  public void testInvalidAnnotation_withES6Modules() {
    testError(
        "export /** @nosideeffects */ function foo() {}", INVALID_NO_SIDE_EFFECT_ANNOTATION);
  }

  @Test
  public void testInvalidModifiesAnnotation() {
    testError("/** @modifies {this} */ var f = function() {};", INVALID_MODIFIES_ANNOTATION);
  }

  @Test
  public void testInvalidModifiesAnnotation_withES6Modules() {
    testError(
        "export /** @modifies {this} */ var f = function() {};", INVALID_MODIFIES_ANNOTATION);
  }

  @Test
  public void testInvalidDefinesVariableDeclaration() {
    testError("/** @define {boolean} */ let DEF = true;", INVALID_DEFINE_ON_LET);
    testError("/** @define {number} */ let DEF = 3;", INVALID_DEFINE_ON_LET);
    testSame("/** @define {boolean} */ var DEF = true;");
    testSame("/** @define {boolean} */ const DEF = true;");
    testError("/** @define {boolean} */ let DEF;", INVALID_DEFINE_ON_LET);
    testError("/** @define {boolean} */ let EXTERN_DEF;", INVALID_DEFINE_ON_LET);
    testSame("let a = {}; /** @define {boolean} */ a.B = false;");
  }

  @Test
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
            "var obj = {", //
            "  /** @suppress {uselessCode} */",
            "  ['h' + 6]() {},",
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
    testSame(
        lines(
            "class Example {", //
            "  /** @suppress {uselessCode} */",
            "  ['f' + 7]() {}",
            "}"));

    testWarning(
        lines(
            "var obj = {", //
            "  /** @suppress {uselessCode} */",
            "  ['h' + 6]: 'hello',",
            "}"),
        MISPLACED_SUPPRESS);

    testSame("/** @suppress {extraRequire} */ goog.require('unused.Class');");
    testSame("/** @const @suppress {duplicate} */ var google = {};");
    testSame("/** @suppress {const} */ var google = {};");
    testSame("/** @suppress {duplicate} @type {Foo} */ ns.something.foo;");
    testSame("/** @suppress {with} */ with (o) { x; }");

    testWarning("/** @suppress {uselessCode} */ goog.require('unused.Class');", MISPLACED_SUPPRESS);
    testWarning("const {/** @suppress {duplicate} */ Foo} = foo();", MISPLACED_SUPPRESS);
    testWarning("foo(/** @suppress {duplicate} */ ns.x = 7);", MISPLACED_SUPPRESS);
  }

  @Test
  public void testImplicitCastOnlyAllowedInExterns() {
    testSame(
        externs(
            lines(
                "/** @constructor */ function Element() {};",
                "/**",
                " * @type {string}",
                " * @implicitCast ",
                " */",
                "Element.prototype.innerHTML;")),
        srcs(""));

    testWarning(
        lines(
            "/** @constructor */ function Element() {};",
            "/**",
            " * @type {string}",
            " * @implicitCast ",
            " */",
            "Element.prototype.innerHTML;"),
        TypeCheck.ILLEGAL_IMPLICIT_CAST);
  }

  @Test
  public void testJsDocInBlockComments() {
    testSame("/** @type {X} */ let x;");
    testSame("// @type {X}\nlet x;");

    testWarning("/* @type {X} */ let x;", JSDOC_IN_BLOCK_COMMENT);

    // jsdoc tags contain letters only, no underscores etc.
    testSame("/* @cc_on */ var x = 3;");
    // a jsdoc tag can't be immediately followed by a paren
    testSame("/* @TODO(username) */ var x = 3;");
  }
}
