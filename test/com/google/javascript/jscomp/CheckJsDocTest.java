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

import static com.google.javascript.jscomp.CheckJSDoc.ARROW_FUNCTION_AS_CONSTRUCTOR;
import static com.google.javascript.jscomp.CheckJSDoc.DEFAULT_PARAM_MUST_BE_MARKED_OPTIONAL;
import static com.google.javascript.jscomp.CheckJSDoc.DISALLOWED_MEMBER_JSDOC;
import static com.google.javascript.jscomp.CheckJSDoc.INVALID_DEFINE_ON_LET;
import static com.google.javascript.jscomp.CheckJSDoc.INVALID_MODIFIES_ANNOTATION;
import static com.google.javascript.jscomp.CheckJSDoc.JSDOC_IN_BLOCK_COMMENT;
import static com.google.javascript.jscomp.CheckJSDoc.JSDOC_ON_RETURN;
import static com.google.javascript.jscomp.CheckJSDoc.MISPLACED_ANNOTATION;
import static com.google.javascript.jscomp.CheckJSDoc.MISPLACED_MSG_ANNOTATION;
import static com.google.javascript.jscomp.CheckJSDoc.MISPLACED_SUPPRESS;

import com.google.javascript.jscomp.parsing.Config.JsDocParsing;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CheckJSDoc}. */
@RunWith(JUnit4.class)
public final class CheckJsDocTest extends CompilerTestCase {

  private JsDocParsing jsdocParsingMode;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    this.jsdocParsingMode = JsDocParsing.INCLUDE_DESCRIPTIONS_WITH_WHITESPACE;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CheckJSDoc(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.MISPLACED_SUPPRESS, CheckLevel.WARNING);
    options.setParseJsDocDocumentation(jsdocParsingMode);
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

  // just here to make sure import.meta doesn't break anything
  @Test
  public void testImportMeta() {
    testSame("var /** number */ x = foo; import.meta.foo = x");
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
  public void testFieldMisplacedAnnotation() {
    testWarning(
        lines(
            "class Foo {", //
            "  /** @nocollapse */",
            "  x = 5;",
            "}"),
        CheckJSDoc.MISPLACED_ANNOTATION);
  }

  @Test
  public void testStaticFieldNoCollapse() {
    testSame(
        lines(
            "class Bar {", //
            "  /** @nocollapse */",
            "  static y = 1",
            "}"));
  }

  @Test
  public void testThisPropertyMisplacedAnnotation() {
    testWarning(
        lines(
            "class Foo {", //
            "  constructor() {",
            "    /** @nocollapse */",
            "    this.x = 4;",
            "  }",
            "}"),
        CheckJSDoc.MISPLACED_ANNOTATION);
  }

  @Test
  public void testThisInFunctionMisplacedAnnotation() {
    testWarning(
        lines(
            "/** @constructor */", //
            "function Bar() {",
            " /** @nocollapse */",
            " this.x = 4;",
            "}"),
        CheckJSDoc.MISPLACED_ANNOTATION);
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

    testWarning("class Foo { /** @constructor */ constructor() {}}", DISALLOWED_MEMBER_JSDOC);

    testWarning("class Foo { /** @interface */ constructor() {}}", DISALLOWED_MEMBER_JSDOC);

    testWarning("class Foo { /** @extends {Foo} */ constructor() {}}", DISALLOWED_MEMBER_JSDOC);

    testWarning("class Foo { /** @implements {Foo} */ constructor() {}}", DISALLOWED_MEMBER_JSDOC);
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
    testWarning(
        lines(
            "/** @param {string} x */ var Foo = goog.defineClass(null, {",
            "  constructor(x) {}",
            "});"),
        MISPLACED_ANNOTATION);

    testWarning(
        lines("/** @param {string} x */ const Foo = class {", "  constructor(x) {}", "};"),
        MISPLACED_ANNOTATION);
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
    testSame(
        lines(
            "/** @constructor */",
            "var Foo = function() {};",
            "/** @abstract */",
            "Foo.prototype.something = function() {}"));
    testSame(
        lines(
            "/** @constructor */",
            "let Foo = function() {};",
            "/** @abstract */",
            "Foo.prototype.something = function() {}"));
    testSame(
        lines(
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
    testWarning("class Foo { /** @abstract */ doSomething() { return 0; }}", MISPLACED_ANNOTATION);
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
    testWarning("class Foo { /** @abstract */ static doSomething() {}}", MISPLACED_ANNOTATION);
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
  public void testAbstract_class_withSymbolNamedMethod() {
    testSame("/** @abstract */ class Foo { /** @abstract */ [Symbol.iterator]() {} }");
  }

  @Test
  public void testAbstract_defineClass() {
    testSame("/** @abstract */ goog.defineClass(null, { constructor: function() {} });");
    testSame("/** @abstract */ var Foo = goog.defineClass(null, { constructor: function() {} });");
    testSame("/** @abstract */ ns.Foo = goog.defineClass(null, { constructor: function() {} });");
    testSame(
        lines(
            "/** @abstract */ ns.Foo = goog.defineClass(null, {",
            "  /** @abstract */ foo: function() {}",
            "});"));
    testSame(
        lines(
            "/** @abstract */ ns.Foo = goog.defineClass(null, {",
            "  /** @abstract */ foo() {}",
            "});"));
    testWarning("/** @abstract */ var Foo;", MISPLACED_ANNOTATION);
    testWarning(
        lines(
            "/** @abstract */ goog.defineClass(null, {",
            "  /** @abstract */ constructor: function() {}",
            "});"),
        MISPLACED_ANNOTATION);
    testWarning(
        lines(
            "/** @abstract */ goog.defineClass(null, {",
            "  /** @abstract */ constructor() {}",
            "});"),
        MISPLACED_ANNOTATION);
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
    testWarning("class Foo { /** @abstract */ constructor() {}}", MISPLACED_ANNOTATION);
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
    testWarning("class Foo { constructor() { /** @abstract */ this.x = 1;}}", MISPLACED_ANNOTATION);
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
    testWarning("class Foo { constructor() {/** @abstract */ var x = 1;}}", MISPLACED_ANNOTATION);
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
  public void testFunctionJSDocOnStubDeclarations() {
    testSame("/** @return {string} */ ns.my.abstract.ctor.method;");
    // should this only be allowed on well-known Symbols ?
    testSame("/** @return {string} */ ns.my.abstract.ctor['method'];");
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
  public void testJSDocFunctionNodeAttachment() {
    testWarning(
        "var a = /** @param {number} index */5;" + "/** @return boolean */function f(index){}",
        MISPLACED_ANNOTATION);
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

    testWarning("/** @desc Foo. */ var bar = goog.getMsg('hello');", MISPLACED_MSG_ANNOTATION);
    testWarning("/** @desc Foo. */ x.y.z.bar = goog.getMsg('hello');", MISPLACED_MSG_ANNOTATION);
    testWarning("var msgs = {/** @desc x */ x: goog.getMsg('x')}", MISPLACED_MSG_ANNOTATION);
    testWarning("/** @desc Foo. */ bar = goog.getMsg('x');", MISPLACED_MSG_ANNOTATION);

    testWarning("/** @desc Foo. */ var {bar} = goog.getMsg('x');", MISPLACED_MSG_ANNOTATION);
    testWarning("/** @desc Foo. */ let {bar} = goog.getMsg('x');", MISPLACED_MSG_ANNOTATION);
    testWarning("/** @desc Foo. */ const {bar} = goog.getMsg('x');", MISPLACED_MSG_ANNOTATION);
    testWarning(
        "var bar;\n/** @desc Foo. */ ({bar} = goog.getMsg('x'));", MISPLACED_MSG_ANNOTATION);

    // allow in TS code
    testNoWarning(
        srcs(
            SourceFile.fromCode(
                "foo.closure.js", "/** @desc Foo. */ var bar = goog.getMsg('hello');")));
  }

  @Test
  public void testJSDocDescInExterns() {
    testWarning("/** @desc Foo. */ x.y.z.MSG_bar;", MISPLACED_MSG_ANNOTATION);
    testSame(externs("/** @desc Foo. */ x.y.z.MSG_bar;"), srcs(""));
  }

  @Test
  public void testJSDocTypeAttachment() {
    testWarning("function f() {  /** @type {string} */ if (true) return; };", MISPLACED_ANNOTATION);

    testWarning("function f() {  /** @type {string} */  return; };", MISPLACED_ANNOTATION);
  }

  @Test
  public void testJSDocTypeAttachment_withES6Modules() {
    testWarning(
        "export function f() { /** @type {string} */ if (true) return; };", MISPLACED_ANNOTATION);
  }

  @Test
  public void testJSDocOnExports() {
    testSame(lines("goog.module('foo');", "/** @const {!Array<number>} */", "exports = [];"));
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
    testWarning("var o = /** @type {string} */ getValue();", MISPLACED_ANNOTATION);
  }

  @Test
  public void testMisplacedTypeAnnotation3() {
    // missing parentheses for the cast.
    testWarning("var o = 1 + /** @type {string} */ value;", MISPLACED_ANNOTATION);
  }

  @Test
  public void testMisplacedTypeAnnotation4() {
    // missing parentheses for the cast.
    testWarning("var o = /** @type {!Array.<string>} */ ['hello', 'you'];", MISPLACED_ANNOTATION);
  }

  @Test
  public void testMisplacedTypeAnnotation5() {
    // missing parentheses for the cast.
    testWarning("var o = (/** @type {!Foo} */ {});", MISPLACED_ANNOTATION);
  }

  @Test
  public void testMisplacedTypeAnnotation6() {
    testWarning(
        "var o = /** @type {function():string} */ function() {return 'str';}",
        MISPLACED_ANNOTATION);
  }

  @Test
  public void testMisplacedTypeAnnotation7() {
    testWarning("var x = /** @type {string} */ y;", MISPLACED_ANNOTATION);
  }

  @Test
  public void testMisplacedTypeAnnotationOnStubPropertyOnCall() {
    testWarning("/** @type {string} */ a.b.c().d;", MISPLACED_ANNOTATION);
    testWarning("/** @typedef {string} */ a.b.c().d;", MISPLACED_ANNOTATION);
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
  public void testAllowedNoCollapseAnnotationOnEsClassMember() {
    testSame(
        lines(
            "class Foo {", //
            "  /** @nocollapse */ static bar() {}",
            "  /** @nocollapse */ static get bar() {}",
            "}"));
  }

  @Test
  public void testAllowedNoCollapseAnnotation_onEsClassStaticPropAssignment() {
    testSame(
        lines(
            "class Foo {}", //
            "/** @nocollapse */ Foo.foo = true"));
  }

  @Test
  public void testAllowedNocollapseAnnotation_withES6Modules() {
    testSame("export var foo = {}; /** @nocollapse */ foo.bar = true;");
  }

  @Test
  public void testMisplacedNocollapseAnnotationOnPrototypeMethod() {
    testWarning(
        lines(
            "/** @constructor */",
            "function Foo() {};",
            "/** @nocollapse */",
            "Foo.prototype.bar = function() {};"),
        MISPLACED_ANNOTATION);

    testWarning(
        lines(
            "class Foo {}", //
            "/** @nocollapse */",
            "Foo.prototype.bar = function() {};"),
        MISPLACED_ANNOTATION);

    testWarning(
        lines(
            "class Foo {", //
            "  /** @nocollapse */ bar() {}",
            "}"),
        MISPLACED_ANNOTATION);

    testWarning(
        lines(
            "class Foo {", //
            "  /** @nocollapse */ get bar() {}",
            "}"),
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
    testError("/** @constructor */ var a = ()=>{}; var b = a();", ARROW_FUNCTION_AS_CONSTRUCTOR);
    testError("var a = /** @constructor */ ()=>{}; var b = a();", ARROW_FUNCTION_AS_CONSTRUCTOR);
    testError("/** @constructor */ let a = ()=>{}; var b = a();", ARROW_FUNCTION_AS_CONSTRUCTOR);
    testError("/** @constructor */ const a = ()=>{}; var b = a();", ARROW_FUNCTION_AS_CONSTRUCTOR);
    testError("var a; /** @constructor */ a = ()=>{}; var b = a();", ARROW_FUNCTION_AS_CONSTRUCTOR);
  }

  @Test
  public void testArrowFuncAsConstructor_withES6Modules() {
    testError(
        "export /** @constructor */ var a = ()=>{}; var b = a();", ARROW_FUNCTION_AS_CONSTRUCTOR);
  }

  @Test
  public void testValidRestParameter() {
    testNoWarning(srcs("function f(...args) {}"));
    testNoWarning(srcs("/** @param {...string} args */ function f(...args) {}"));
    testNoWarning(srcs("function f(...[x, y, ...args]) {}"));
    testNoWarning(srcs("/** @param {...number} args */ function f(...[x, y, ...args]) {}"));
    testNoWarning(srcs("function f(.../** ...string */ args) {}"));
  }

  @Test
  public void testRestParameter_missingVarArgsInParamJSDoc() {
    test(
        srcs("/** @param {string} args */ function f(...args) {}"),
        warning(CheckJSDoc.BAD_REST_PARAMETER_ANNOTATION));
  }

  @Test
  public void testRestParameter_missingVarArgsInDestructuringParamJSDoc() {
    test(
        srcs("/** @param {number} rest */ function f(...[x, y, ...args]) {}"),
        warning(CheckJSDoc.BAD_REST_PARAMETER_ANNOTATION));
    test(
        srcs(
            lines(
                "/**",
                " * @param {number} p",
                " * @param {string} rest",
                " */",
                "function f(p, ...[x, y, ...args]) {}")),
        warning(CheckJSDoc.BAD_REST_PARAMETER_ANNOTATION));
  }

  @Test
  public void testRestParameter_missingVarArgsInInlineJSDoc() {
    test(
        srcs("function f(.../** string */ args) {}"),
        warning(CheckJSDoc.BAD_REST_PARAMETER_ANNOTATION));
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
    testSame(
        "class C { /** @template T \n @param {T} a\n @param {T} b \n */ " + "constructor(a,b){} }");
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
    testSame("export /** @template T */ function f(/** T */ a) {}");
  }

  @Test
  public void testGoodTemplate_constructorDefinition() {
    testSame(
        lines(
            "x.y.z = goog.defineClass(null, {",
            "  /** @template T */ constructor: function() {}",
            "});"));
  }

  @Test
  public void testGoodTemplate_FunctionDefinition() {
    testSame("/** @template T */ function f() {}");
    testSame("/** @template T */ var f = function() {};");
    testSame("/** @template T */ Foo.prototype.f = function() {};");
    testSame("/** @template T */ function f(/** T */ a) {}");
  }

  @Test
  public void testBadTemplate_functionInvocation() {
    testBadTemplate("/** @template T */ foo();");
  }

  @Test
  public void testGoodTypedef() {
    testSame("/** @typedef {string} */ var x;");
    testSame("/** @typedef {string} */ let x;");
    testSame("/** @typedef {string} */ const x = {};");
    testSame("/** @typedef {string} */ a.b.c;");
    testSame("/** @typedef {string} */ a.b.c = {};");
    testSame(
        lines(
            "const C = goog.defineClass(",
            "   null, {",
            "     constructor() {},",
            "     statics: { /** @typedef {string} */ StringType: null},",
            "});"));
  }

  @Test
  public void testBadTypedef_usedAsCast() {
    testWarning("const s = /** @typedef {?} */ (0);", MISPLACED_ANNOTATION);
  }

  @Test
  public void testBadTypedef_onFunction() {
    testWarning("/** @typedef {?} */ function foo() {}", MISPLACED_ANNOTATION);
  }

  @Test
  public void testBadTypedef_onClass() {
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
  public void testBadTypedef_onInstanceProp() {
    testWarning(
        "class C { constructor() { /** @typedef {string} */ this.foo = ''; }}",
        MISPLACED_ANNOTATION);

    testWarning(
        "class C { constructor() { this.foo = {}; /** @typedef {string} */ this.foo.bar = ''; }}",
        MISPLACED_ANNOTATION);

    testWarning(
        lines(
            "class D {}",
            "class C extends D {",
            "  constructor() {",
            "    super();",
            "    /** @typedef {string} */",
            "    super.foo = ''; }",
            "}"),
        MISPLACED_ANNOTATION);

    testWarning(
        "class C { constructor() { /** @typedef {string} */ this.foo; }}", MISPLACED_ANNOTATION);

    testWarning(
        "/** @constructor */ function C() { /** @typedef {string} */ this.foo; }",
        MISPLACED_ANNOTATION);
  }

  @Test
  public void testBadTypedef_onPrototypeProp() {
    testWarning(
        "/** @constructor */ function C() {} /** @typedef {string} */ C.prototype.foo;",
        MISPLACED_ANNOTATION);

    testWarning(
        externs("/** @constructor */ function C() {} /** @typedef {string} */ C.prototype.foo;"),
        srcs(""),
        warning(MISPLACED_ANNOTATION));

    testWarning(
        "/** @constructor */ function C() {} /** @typedef {string} */ C.prototype.foo = 'foobar';",
        MISPLACED_ANNOTATION);
  }

  @Test
  public void testBadTypedef_withES6Modules() {
    testWarning(
        "export /** @typedef {{foo: string}} */ class C { constructor() { this.foo = ''; }}",
        MISPLACED_ANNOTATION);
  }

  @Test
  public void testNoSideEffectsAnnotation1() {
    testSame("/** @nosideeffects */ function foo() {}");
  }

  @Test
  public void testNoSideEffectsAnnotation2() {
    testSame("var f = /** @nosideeffects */ function() {}");
  }

  @Test
  public void testNoSideEffectsAnnotation3() {
    testSame("/** @nosideeffects */ var f = function() {}");
  }

  @Test
  public void testNoSideEffectsAnnotation4() {
    testSame("var f = function() {};" + "/** @nosideeffects */ f.x = function() {}");
  }

  @Test
  public void testNoSideEffectsAnnotation5() {
    testSame("var f = function() {};" + "f.x = /** @nosideeffects */ function() {}");
  }

  @Test
  public void testNoSideEffectsAnnotations() {
    testSame("export /** @nosideeffects */ function foo() {}");
  }

  @Test
  public void testInvalidModifiesAnnotation() {
    testError("/** @modifies {this} */ var f = function() {};", INVALID_MODIFIES_ANNOTATION);
  }

  @Test
  public void testInvalidModifiesAnnotation_withES6Modules() {
    testError("export /** @modifies {this} */ var f = function() {};", INVALID_MODIFIES_ANNOTATION);
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
  public void testMisplacedSuppress() {
    testSame("/** @suppress {missingRequire} */ var x = new y.Z();");
    testSame("/** @suppress {missingRequire} */ function f() { var x = new y.Z(); }");
    testSame("/** @suppress {missingRequire} */ var f = function() { var x = new y.Z(); }");
    testSame(lines("var obj = {", "  /** @suppress {uselessCode} */", "  f: function() {},", "}"));
    testSame(lines("var obj = {", "  /** @suppress {uselessCode} */", "  f() {},", "}"));
    testSame(
        lines(
            "var obj = {", //
            "  /** @suppress {uselessCode} */",
            "  ['h' + 6]() {},",
            "}"));
    testSame(lines("class Example {", "  /** @suppress {uselessCode} */", "  f() {}", "}"));
    testSame(lines("class Example {", "  /** @suppress {uselessCode} */", "  static f() {}", "}"));
    testSame(lines("class Example {", "  /** @suppress {uselessCode} */", "  get f() {}", "}"));
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

    // Suppressions are not allowed:
    //  * on arbitrary sub-expressions within a statement
    //  * on control-flow structures like loops, if, switch, or on blocks
    testWarning("const {/** @suppress {duplicate} */ Foo} = foo();", MISPLACED_SUPPRESS);
    testWarning("foo(/** @suppress {duplicate} */ ns.x = 7);", MISPLACED_SUPPRESS);
    testWarning("/** @suppress {duplicate} */ switch (0) { default: ; }", MISPLACED_SUPPRESS);
    testWarning("/** @suppress {duplicate} */ do {} while (true);", MISPLACED_SUPPRESS);
    testWarning("/** @suppress {duplicate} */ while (true) {}", MISPLACED_SUPPRESS);
    testWarning("/** @suppress {duplicate} */ for (; true; ) {}", MISPLACED_SUPPRESS);
    testWarning("/** @suppress {duplicate} */ { (0); }", MISPLACED_SUPPRESS);
    testWarning("/** @suppress {duplicate} */ if (true) {}", MISPLACED_SUPPRESS);

    testSame("/** @suppress {uselessCode} */ goog.require('unused.Class');");
    testSame("/** @suppress {checkTypes} */ foo(0);");

    testSame("/** @suppress {visibility} */ a.x_ = 0;");
    testSame("/** @suppress {visibility} */ a.x_ += 0;");
    testSame("/** @suppress {visibility} */ a.x_ *= 0;");
    testSame("/** @suppress {visibility} */ a.x_ /= 0;");
  }

  @Test
  public void testClassFieldSuppressed() {
    testSame(
        lines(
            "class Example {", //
            "  /** @suppress {uselessCode} */",
            "  x = 2;",
            "}"));
    testSame(
        lines(
            "class Example {", //
            "  /** @suppress {uselessCode} */",
            "  static x = 2",
            "}"));
    testSame(
        lines(
            "class Example {", //
            "  /** @suppress {uselessCode} */",
            "  ['x'] = 2",
            "}"));
    testSame(
        lines(
            "class Example {", //
            "  /** @suppress {uselessCode} */",
            "  static ['x'] = 2",
            "}"));
    testSame(
        lines(
            "class Example {", //
            "  /** @type {string}  */",
            "   x = 2",
            "}"));
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

  @Test
  public void testClosurePrimitive() {
    testSame("/** @closurePrimitive {asserts.fail} */ function fail() {}");
    testSame("/** @closurePrimitive {asserts.fail} */ goog.asserts.fail = function() {};");
    testSame("/** @closurePrimitive {asserts.fail} */ let fail = function() {}");
    testWarning("/** @fileoverview @closurePrimitive {asserts.fail} */", MISPLACED_ANNOTATION);
  }

  @Test
  public void testJsDocOnReturn() {
    // JSDoc on a class defined in return statement is a warning.
    testWarning(
        lines(
            "/** @return {function(new:EventTarget)} */",
            "function get() {",
            "/** @implements {EventTarget} */",
            "return class {};",
            "}"),
        JSDOC_ON_RETURN);
    testWarning("function get() {\n/** @enum {string} */\nreturn {A: 'a'};\n}", JSDOC_ON_RETURN);
    testWarning("function get() {\n/** @typedef {string} */\nreturn 'a';\n}", MISPLACED_ANNOTATION);

    // No warning when returning a class annotated with JSDoc.
    testSame(
        lines(
            "/** @return {function(new:EventTarget)} */",
            "function mixin() {",
            "/** @implements {EventTarget} */",
            "class MyEventTarget {}",
            "return MyEventTarget;",
            "}"));

    // No warning for regular JSDoc.
    testSame(lines("function f() {", "/** Some value. */", "return 5;", "}"));
  }

  @Test
  public void testConstructorFieldError_withTypeAnnotation() {
    testSame("class C { /** @constructor */ x = function() {}; }");
  }

  @Test
  public void testPublicClassField_allowsTypeAnnotation() {
    testSame("class C { /** @type {number} */ x = 2; }");
  }

  @Test
  public void testPublicClassComputedField_allowsTypeAnnotation() {
    testSame("class C { /** @type {number} */ [x] = 2; }");
  }

  @Test
  public void testPublicClassField_typeDefError() {
    testWarning("class C { /** @typedef {number} */ x = 2;}", MISPLACED_ANNOTATION);
  }

  @Test
  public void testPublicClassComputedField_typeDefError() {
    testWarning("class C { /** @typedef {number} */ [x] = 2;}", MISPLACED_ANNOTATION);
  }

  @Test
  public void testMangleClosureModuleExportsContentsTypes() {
    // disable parsing anything besides types; otherwise this test case fails because the
    // "sourceComment"s from the actual/expected JSDocInfo do not match
    jsdocParsingMode = JsDocParsing.TYPES_ONLY;

    test(
        "/** @type {!module$exports$foo$bar} */ let x;",
        "/** @type {!UnrecognizedType_module$exports$foo$bar} */ let x;");
    test(
        srcs(
            "goog.module('foo.bar'); exports = class {};",
            "/** @type {!module$exports$foo$bar} */ let x;"),
        expected(
            "goog.module('foo.bar'); exports = class {};",
            "/** @type {!UnrecognizedType_module$exports$foo$bar} */ let x;"));
    test(
        "/** @type {!module$exports$foo$bar.A.B} */ let x;",
        "/** @type {!UnrecognizedType_module$exports$foo$bar.A.B} */ let x;");
    test(
        "/** @type {!module$contents$foo$bar_local} */ let x;",
        "/** @type {!UnrecognizedType_module$contents$foo$bar_local} */ let x;");
    test(
        "/** @type {!Array<module$exports$foo$bar>} */ let x;",
        "/** @type {!Array<UnrecognizedType_module$exports$foo$bar>} */ let x;");
  }

  @Test
  public void testNameDeclarationAndAssignForAbstractClass() {
    testSame(lines("/** @abstract */", "let A = A_1 = class A {}"));
  }

  @Test
  public void testNameDeclarationAndAssignForTemplatedClass() {
    testSame(lines("/** @template T */", "let A = A_1 = class A {}"));
  }
}
