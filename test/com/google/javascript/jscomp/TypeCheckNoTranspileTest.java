/*
 * Copyright 2017 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * Tests {@link TypeCheck} on non-transpiled code.
 */
public final class TypeCheckNoTranspileTest extends TypeCheckTestCase {

  @Override
  protected CompilerOptions getDefaultOptions() {
    CompilerOptions options = super.getDefaultOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_NEXT);
    return options;
  }

    public void testArrowInferredReturn() {
    // TODO(johnlenz): infer simple functions return results.

    // Verify arrows have do not have an incorrect inferred return.
    testTypes(
        lines(
            "let fn = () => {",
            "  return 1;",
            "};",
            "var /** null */ x = fn();"));
  }

  public void testArrowBlocklessInferredReturn() {
    // TODO(johnlenz): infer simple functions return results.

    // Verify arrows have do not have an incorrect inferred return.
    testTypes(
        lines(
            "let fn = () => 1;",
            "var /** null */ x = fn();"));
  }

  public void testArrowRightScopeForBody() {
    testTypes(
        lines(
            "/** @type {string} */ let a = 's';",
            "/** ",
            "  @param {number} a",
            "  @return {null}",
            "*/",
            "let fn = (a) => {",
            "  return a;",
            "}"),
        lines(
            "inconsistent return type",
            "found   : number",
            "required: null"));
  }

  public void testArrowRightBodyScopeForBlocklessBody() {
    testTypes(
        lines(
            "/** @type {string} */ let a = 's';",
            "/** ",
            "  @param {number} a",
            "  @return {null}",
            "*/",
            "let fn = (a) => a",
            ""),
        lines(
            "inconsistent return type",
            "found   : number",
            "required: null"));
  }

  public void testArrowCorrectThis() {
    testTypes(
        lines(
            "/** @this {String} */ function fn() {",
            "  /** ",
            "    @return {null}",
            "  */",
            "  let fn = () => {",
            "    return this;",
            "  }",
            "}"),
        lines(
            "inconsistent return type",
            "found   : String",
            "required: null"));
  }

  public void testArrowBlocklessCorrectThis() {
    testTypes(
        lines(
            "/** @this {String} */ function fn() {",
            "  /** ",
            "    @return {null}",
            "  */",
            "  let fn = () => this;",
            "}"),
        lines(
            "inconsistent return type",
            "found   : String",
            "required: null"));
  }

  public void testArrowCorrectArguments() {
    testTypes(
        lines(
            "/** @this {String} */ function fn() {",
            "  {",
            "    /** @type {number} */ let arguments = 1;",
            "    /** @return {null} */",
            "    let fn = () => {",
            "      return arguments;",
            "    }",
            "  }",
            "}"),
        lines(
            "inconsistent return type",
            "found   : number",
            "required: null"));
  }

  public void testArrowBlocklessCorrectArguments() {
    testTypes(
        lines(
            "/** @this {String} */ function fn() {",
            "  {",
            "    /** @type {number} */ let arguments = 1;",
            "    /** @return {null} */",
            "    let fn = () => arguments;",
            "  }",
            "}"),
        lines(
            "inconsistent return type",
            "found   : number",
            "required: null"));
  }

  public void testArrowCorrectInheritsArguments() {
    testTypesWithExtraExterns(
        lines(
            "/** @type {!Arguments} */ var arguments;"),
        lines(
            "/** @this {String} */ function fn() {",
            "  {",
            "    /** @return {null} */",
            "    let fn = () => {",
            "      return arguments;",
            "    }",
            "  }",
            "}"),
        lines(
            "inconsistent return type",
            "found   : Arguments",
            "required: null"));
  }

  public void testArrowBlocklessCorrectInheritsArguments() {
    testTypesWithExtraExterns(
        lines(
            "/** @type {!Arguments} */ var arguments;"),
        lines(
            "/** @this {String} */ function fn() {",
            "  {",
            "    /** @return {null} */",
            "    let fn = () => arguments;",
            "  }",
            "}"),
        lines(
            "inconsistent return type",
            "found   : Arguments",
            "required: null"));
  }

  public void testArrayLitSpread() {
    // TODO(bradfordcsmith): check spread in array literal
    // Note that there's not much point in doing such a check until we check array literal
    // elements in general.
    // See https://github.com/google/closure-compiler/issues/312
    testTypesWithCommonExterns(
        lines(
            "/** @type {!Array<string>} */", // preserve newlines
            "const strings = [];",
            "/** @type {!Array<number>} */",
            "const numbers = [...strings];", // This should generate an error
            ""));
  }

  public void testArrayLitSpreadNonIterable() {
    testTypes(
        lines(
            "/** @type {!Array<number>} */", // preserve newlines
            "const numbers = [...1];",
            ""),
        lines(
            "Spread operator only applies to Iterable types",
            "found   : number",
            "required: Iterable"));
  }

  public void testTypecheckExpressionInArrayLitSpread() {
    testTypesWithCommonExterns(
        lines(
            "/** @type {!Array<string>} */", // preserve newlines
            "const strings = [];",
            "/** @type {!Array<number>} */",
            "let numbers = [];",
            "const a = [...(numbers = strings)];",
            ""),
        lines(
            "assignment", // preserve newlines
            "found   : Array<string>",
            "required: Array<number>"));
  }

  public void testInferTypesFromExpressionInArrayLitSpread() {
    testTypesWithCommonExterns(
        lines(
            "/** @type {!Array<string>} */", // preserve newlines
            "const strings = [];",
            "let inferred = 1;",
            "const a = [...(inferred = strings)];",
            "/** @type {null} */",
            "const n = inferred;",
            ""),
        lines(
            "initializing variable", // preserve newlines
            "found   : Array<string>",
            "required: null"));
  }

  public void testSpreadAndFollowingParametersNotTypeChecked() {
    testTypesWithExtraExterns(
        lines(
            "/**", // extra externs
            " * @param {number} num",
            " * @param {string} str",
            " * @param {boolean} bool",
            " */",
            "function use(num, str, bool) {}",
            ""),
        lines(
            "/** @type {!Array<null>} */ const nulls = [];", // input lines
            "use(1, ...nulls, null, null);"));
    // TODO(bradfordcsmith): Should get an error since there's no way for `str` and `bool` params
    // to get the right types here.
  }

  public void testSpreadArgumentTypeCheckedForVarArgs() {
    testTypesWithExtraExterns(
        lines(
            "/**", // extra externs
            " * @param {number} num",
            " * @param {...string} var_args",
            " */",
            "function use(num, var_args) {}",
            ""),
        lines(
            "/** @type {!Array<null>} */ const nulls = [];", // input lines
            "use(1, ...nulls);"));
    // TODO(bradfordcsmith): Should get an error since `nulls` doesn't contain strings.
  }

  public void testSpreadArgumentBackInference() {
    testTypesWithExtraExterns(
        lines(
            "/**", // extra externs
            " * @param {number} num",
            " * @param {...{prop: number}} var_args",
            " */",
            "function use(num, var_args) {}",
            ""),
        lines(
            "use(1, ...[{}]);"));
    // TODO(bradfordcsmith): Should generate error indicating inferred type of `[{}]`
    // as `{Iterable<{prop: (number|undefined)}>}
  }

  public void testTooManyNonSpreadParameters() {
    testTypesWithExtraExterns(
        lines(
            "/**", // extra externs
            " * @param {number} num",
            " * @param {string} str",
            " * @param {boolean} bool",
            " */",
            "function use(num, str, bool) {}",
            ""),
        lines(
            "/** @type {!Array<*>} */ const unusables = [];", // input lines
            "use(1, 'hi', ...unusables, null, null);"), // more than 3 non-spread parameters
        "Function use: called with at least 4 argument(s)."
            + " Function requires at least 3 argument(s) and no more than 3 argument(s).");
  }

  public void testArgumentSpreadDoesNotBlockTypeCheckOfInitialParameters() {
    testTypesWithExtraExterns(
        lines(
            "/**", // extra externs
            " * @param {number} num",
            " * @param {string} str",
            " * @param {boolean} bool",
            " */",
            "function use(num, str, bool) {}",
            ""),
        "use('should be number', ...[]);",
        lines(
            "actual parameter 1 of use does not match formal parameter",
            "found   : string",
            "required: number"));
  }

  public void testArgumentSpreadNonIterable() {
    testTypesWithExtraExterns(
        "function use(x) {}",
        "use(...1);",
        lines(
            "Spread operator only applies to Iterable types",
            "found   : number",
            "required: Iterable"));
  }

  public void testTypecheckExpressionInArgumentSpread() {
    testTypesWithExtraExterns(
        "function use(x) {}",
        lines(
            "/** @type {!Array<string>} */", // preserve newlines
            "const strings = [];",
            "/** @type {!Array<number>} */",
            "let numbers = [];",
            "use(...(numbers = strings));",
            ""),
        lines(
            "assignment", // preserve newlines
            "found   : Array<string>",
            "required: Array<number>"));
  }

  public void testInferTypesFromExpressionInArgumentSpread() {
    testTypesWithExtraExterns(
        "function use(x) {}",
        lines(
            "/** @type {!Array<string>} */", // preserve newlines
            "const strings = [];",
            "let inferred = 1;",
            "use(...(inferred = strings));",
            "/** @type {null} */",
            "const n = inferred;",
            ""),
        lines(
            "initializing variable", // preserve newlines
            "found   : Array<string>",
            "required: null"));
  }

  public void testOnlyRestParameterWithoutJSDocCalledWithNoArgs() {
    testTypes(
        lines(
            "", // input lines
            "function use(...numbers) {}",
            "use();", // no args provided in call - should be OK
            ""));
  }

  public void testOnlyRestParameterWithoutJSDocCalledWithArgs() {
    testTypes(
        lines(
            "", // input lines
            "function use(...numbers) {}",
            "use(1, 'hi', {});",
            ""));
  }

  public void testOnlyRestParameterWithJSDocCalledWithNoArgs() {
    testTypes(
        lines(
            "/**", // input lines
            " * @param {...number} numbers",
            " */",
            "function use(...numbers) {}",
            "use();", // no args provided in call - should be OK
            ""));
  }

  public void testOnlyRestParameterWithJSDocCalledWithGoodArgs() {
    testTypes(
        lines(
            "/**", // input lines
            " * @param {...number} numbers",
            " */",
            "function use(...numbers) {}",
            "use(1, 2, 3);",
            ""));
  }

  public void testOnlyRestParameterWithJSDocCalledWithBadArg() {
    testTypes(
        lines(
            "/**", // input lines
            " * @param {...number} numbers",
            " */",
            "function use(...numbers) {}",
            "use(1, 'hi', 3);",
            ""),
        lines(
            "actual parameter 2 of use does not match formal parameter",
            "found   : string",
            // TODO(bradfordcsmith): should not allow undefined
            // This is consistent with pre-ES6 var_args behavior.
            // See https://github.com/google/closure-compiler/issues/2561
            "required: (number|undefined)"
        ));
  }

  public void testNormalAndRestParameterWithJSDocCalledWithOneArg() {
    testTypes(
        lines(
            "/**", // input lines
            " * @param {string} str",
            " * @param {...number} numbers",
            " */",
            "function use(str, ...numbers) {}",
            "use('hi');", // no rest args provided in call - should be OK
            ""));
  }

  public void testNormalAndRestParameterWithJSDocCalledWithGoodArgs() {
    testTypes(
        lines(
            "/**", // input lines
            " * @param {string} str",
            " * @param {...number} numbers",
            " */",
            "function use(str, ...numbers) {}",
            "use('hi', 2, 3);",
            ""));
  }

  public void testOnlyRestParameterWithJSDocCalledWithBadNormalArg() {
    testTypes(
        lines(
            "/**", // input lines
            " * @param {string} str",
            " * @param {...number} numbers",
            " */",
            "function use(str, ...numbers) {}",
            "use(1, 2, 3);",
            ""),
        lines(
            "actual parameter 1 of use does not match formal parameter",
            "found   : number",
            "required: string"
        ));
  }

  public void testOnlyRestParameterWithJSDocCalledWithBadRestArg() {
    testTypes(
        lines(
            "/**", // input lines
            " * @param {string} str",
            " * @param {...number} numbers",
            " */",
            "function use(str, ...numbers) {}",
            "use('hi', 'there', 3);",
            ""),
        lines(
            "actual parameter 2 of use does not match formal parameter",
            "found   : string",
            // TODO(bradfordcsmith): should not allow undefined
            // This is consistent with pre-ES6 var_args behavior.
            // See https://github.com/google/closure-compiler/issues/2561
            "required: (number|undefined)"
        ));
  }

  public void testExponent1() {
    testTypes(
        lines(
            "function fn(someUnknown) {",
            "  var x = someUnknown ** 2;", // infer the result
            "  var /** null */ y = x;",
            "}"),
        lines(
            "initializing variable",
            "found   : number",
            "required: null"));
  }

  public void testExponent2() {
    testTypes(
        lines(
            "function fn(someUnknown) {",
            "  var x = someUnknown;",
            "  x **= 2;", // infer the result
            "  var /** null */ y = x;",
            "}"),
        lines(
            "initializing variable",
            "found   : number",
            "required: null"));
  }

  public void testExponent3() {
    testTypes(
        lines(
            "function fn(someUnknown) {",
            "  var y = true ** 3;",
            "}"),
        lines(
            "left operand",
            "found   : boolean",
            "required: number"));
  }

  public void testExponent4() {
    testTypes(
        lines(
            "function fn(someUnknown) {",
            "  var y = 1; y **= true;",
            "}"),
        lines(
            "right operand",
            "found   : boolean",
            "required: number"));
  }

  public void testDuplicateCatchVarName() {
    // Make sure that catch variables with the same name are considered to be distinct variables
    // rather than causing a redeclaration error.
    testTypes(
        lines(
            "try { throw 1; } catch (/** @type {number} */ err) {}",
            "try { throw 'error'; } catch (/** @type {string} */ err) {}",
            ""));
  }

  public void testTypedefFieldInLoopLocal() {
    testTypesWithCommonExterns(
        lines(
            "/** @typedef {{num: number, maybeNum: ?number}} */",
            "let XType;",
            "",
            "/** @param {!Array<!XType>} xlist */",
            "function f(xlist) {",
            "  for (let i = 0; i < xlist.length; i++) {",
            "    /** @type {!XType} */",
            "    const x = xlist[i];",
            "    if (x.maybeNum === null) {",
            "      continue;",
            "    }",
            "    x.num = x.maybeNum;",
            "  }",
            "}",
            ""));
  }

  public void testTypedefFieldInLoopGlobal() {
    testTypesWithCommonExterns(
        lines(
            "/** @typedef {{num: number, maybeNum: ?number}} */",
            "let XType;",
            "",
            "/** @type {!Array<!XType>} */",
            "const xlist = [{maybeNum: null, num: 0}, {maybeNum: 1, num: 1}];",
            "",
            "for (let i = 0; i < xlist.length; i++) {",
            "  /** @type {!XType} */",
            "  const x = xlist[i];",
            "  if (x.maybeNum === null) {",
            "    continue;",
            "  }",
            // TODO(b/78364240): Compiler should realize that x.maybeNum must be a number here
            "  x.num = x.maybeNum;",
            "}",
            ""),
        lines(
            "assignment to property num of x", // preserve newlines
            "found   : (null|number)",
            "required: number"));
  }

  public void testTypedefAlias() {
    // Ensure that the type of a variable representing a typedef is "undefined"
    testTypes(
        lines(
            "/** @typedef {number} */", // preserve newlines
            "var MyNumber;",
            "var ns = {};",
            "ns.MyNumber = MyNumber;",
            "/** @type {string} */ (ns.MyNumber);",
            ""),
        lines(
            "invalid cast - must be a subtype or supertype", // preserve newlines
            "from: undefined",
            "to  : string"));
  }

  public void testGetTypedPercent() {
    // Make sure names declared with `const` and `let` are counted correctly for typed percentage.
    // This was created my a modifying a copy of TypeCheckTest.testGetTypedPercent1()
    String js =
        lines(
            "const id = function(x) { return x; }",
            "let id2 = function(x) { return id(x); }");
    assertEquals(50.0, getTypedPercent(js), 0.1);
  }

  public void testBlockScopedVarInLoop1() {
    disableStrictMissingPropertyChecks();
    testTypesWithCommonExterns(
        lines(
            "/** @constructor */ function Node() {};",
            "function g(/** Node */ n){",
            "  n.foo = {bar: 3};",
            "}",
            "function f(/** !Array<!Node> */ arr){",
            "  for (var i = 0; i < arr.length; i++) {",
            "    const tile = arr[i];",
            "    const bar = tile.foo.bar;",
            // this assignment shouldn't cause 'tile.foo' to be inferred as undefined above.
            "    tile.foo = undefined",
            "  }",
            "}"));
  }

  public void testBlockScopedVarInLoop2() {
    testTypes(
        lines(
            "while (true) {",
            "  let num;",
            "  let /** undefined */ y = num;",
            // null assignment shouldn't make us think num could be null on the previous line.
            "  num = null;",
            "}"));
  }

  public void testBlockScopedVarInLoop3() {
     // Tests that the qualified name alias.num is reset between loop iterations
    testTypes(
        lines(
            "function takesNumber(/** number */ n) {}",
            "",
            "function f(/** {num: ?number} */ obj) {",
            "  for (const _ in {}) {",
            "    const alias = obj;",
            "    if (alias.num === null) {",
            "     continue;",
            "    }",
            "    takesNumber(alias.num);",
            "  }",
            "}"));
  }

  public void testGlobalEnumWithLet() {
    testTypes(
        lines(
            "/** @enum */", // type defaults to {number}
            "let E = {A: 1, B: 2};",
            "",
            "/**",
            " * @param {E} x",
            " * @return {number}",
            " */",
            "function f(x) {return x}"));
  }

  public void testGlobalEnumWithConst() {
    testTypes(
        lines(
            "/** @enum */", // type defaults to {number}
            "const E = {A: 1, B: 2};",
            "",
            "/**",
            " * @param {E} x",
            " * @return {number}",
            " */",
            "function f(x) {return x}"));
  }

  public void testLocalEnumWithLet() {
    // TODO(bradfordcsmith): Local enum types should be non-nullable just like the global ones.
    testTypes(
        lines(
            "{",
            "  /** @enum */", // type defaults to {number}
            "  let E = {A: 1, B: 2};",
            "",
            "  /**",
            "   * @param {E} x",
            "   * @return {number}",
            "   */",
            "  function f(x) {return x}",
            "}"),
        lines(
            "inconsistent return type",
            "found   : (E<number>|null)",
            "required: number"));
  }

  public void testLocalEnumWithConst() {
    // TODO(bradfordcsmith): Local enum types should be non-nullable just like the global ones.
    testTypes(
        lines(
            "{",
            "  /** @enum */", // type defaults to {number}
            "  const E = {A: 1, B: 2};",
            "",
            "  /**",
            "   * @param {E} x",
            "   * @return {number}",
            "   */",
            "  function f(x) {return x}",
            "}"),
        lines(
            "inconsistent return type",
            "found   : (E<number>|null)",
            "required: number"));
  }

  public void testGlobalTypedefWithLet() {
    testTypes(
        lines(
            "/** @typedef {number} */",
            "let Bar;",
            "/** @param {Bar} x */",
            "function f(x) {}",
            "f('3');",
            ""),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : string",
            "required: number"));
  }

  public void testLocalTypedefWithLet() {
    testTypes(
        lines(
            "{",
            "  /** @typedef {number} */",
            "  let Bar;",
            "  /** @param {Bar} x */",
            "  function f(x) {}",
            "  f('3');",
            "}",
            ""),
        lines(
            "actual parameter 1 of f does not match formal parameter",
            "found   : string",
            "required: number"));
  }

  public void testConstWrongType() {
    testTypes(
        "/** @type {number} */ const x = 'hi';",
        lines(
            "initializing variable", // preserve newlines
            "found   : string",
            "required: number"));
  }

  public void testLetWrongType() {
    testTypes(
        "/** @type {number} */ let x = 'hi';",
        lines(
            "initializing variable", // preserve newlines
            "found   : string",
            "required: number"));
  }

  public void testLetInitializedToUndefined1() {
    testTypes(
        "let foo; let /** number */ bar = foo;",
        lines(
            "initializing variable", // preserve newline
            "found   : undefined",
            "required: number"));
  }

  public void testLetInitializedToUndefined2() {
    // Use the declared type of foo instead of inferring it to be undefined.
    testTypes("let /** number */ foo; let /** number */ bar = foo;");
  }

  public void testLetInitializedToUndefined3() {
    // TODO(sdh): this should warn because foo is potentially undefined when getFoo() is called.
    // See comment in TypeInference#updateScopeForTypeChange
    testTypes(
        lines(
            "let foo;",
            "/** @return {number} */",
            "function getFoo() {",
            "  return foo;",
            "}",
            "function setFoo(/** number */ num) {",
            "  foo = num;",
            "}"));
  }

  public void testForOf1() {
    testTypes("/** @type {!Iterable} */ var it; for (var elem of it) {}");
  }

  public void testForOf2() {
    testTypes(
        lines(
            "function takesString(/** string */ s) {}",
            "/** @type {!Iterable<string>} */ var it;",
            "for (var elem of it) { takesString(elem); }"));
  }

  public void testForOf3() {
    testTypes(
        lines(
            "function takesString(/** string */ s) {}",
            "/** @type {!Iterable<number>} */ var it;",
            "for (var elem of it) { takesString(elem); }"),
        lines(
            "actual parameter 1 of takesString does not match formal parameter",
            "found   : number",
            "required: string"));
  }

  public void disabled_testForOf4() {
    // TODO(b/79532975): this currently crashes in TypeInference
    testTypes("/** @type {!Iterable} */ var it; var obj = {}; for (obj.elem of it) {}");
  }

  public void testForOf_wrongLoopVarType1() {
    testTypesWithCommonExterns(
        lines(
            "/** @type {!Array<number>} */",
            "var numArray = [1, 2];",
            "/** @type {string} */",
            "var elem = '';",
            "for (elem of numArray) {",
            "}"),
        lines(
            "declared type of for-of loop variable does not match inferred type",
            "found   : number",
            "required: string"));
  }

  public void testForOf_wrongLoopVarType2() {
    testTypesWithCommonExterns(
        lines(
            "/** @type {!Array<number>} */",
            "var numArray = [1, 2];",
            "for (let /** string */ elem of numArray) {",
            "}"),
        lines(
            "declared type of for-of loop variable does not match inferred type",
            "found   : number",
            "required: string"));
  }

  public void testForOf_wrongLoopVarType3() {
    // If the thing we're trying to iterate over is not actually an Iterable, we treat the inferred
    // type of the for-of loop variable as unknown and only warn for the non-Iterable item.
    testTypes(
        "for (var /** number */ x of 3) {}",
        lines(
            "Can only iterate over a (non-null) Iterable type",
            "found   : number",
            "required: Iterable"));
  }

  public void testForOf_wrongLoopVarType4a() {
    testTypesWithCommonExterns(
        lines(
            "/** @type {!Array<!Object>} */",
            "var arr = [1, 2];",
            "for (let /** ?Object */ elem of arr) {",
            "}"));
  }

  public void testForOf_wrongLoopVarType4b() {
    testTypesWithCommonExterns(
        lines(
            "/** @type {!Array<?Object>} */",
            "var arr = [1, 2];",
            "for (let /** !Object */ elem of arr) {",
            "}"),
        lines(
            "declared type of for-of loop variable does not match inferred type",
            "found   : (Object|null)",
            "required: Object"));
  }

  public void testForOf_wrongLoopVarType5() {
    // Test that we don't check the inferred type of n against the Iterable type
    testTypesWithCommonExterns(
        lines(
            "/** @type {!Array<number>} */",
            "var arr = [1, 2];",
            "let n = null;",
            "for (n of arr) {}"));
  }

  public void testForOf_wrongLoopVarType6a() {
    // Test that we typecheck the correct variable, given various shadowing variable declarations
    testTypesWithCommonExterns(
        lines(
            "/** @type {!Array<number>} */",
            "var arr = [1, 2, 3];",
            "let /** string */ n = 'foo';", // n in global scope
            "for (let /** number */ n of arr) {", // n in for of scope
            "  let /** null */ n = null;", // n in inner block scope
            "}"));
  }

  public void testForOf_wrongLoopVarType6b() {
    // Test that we typecheck the correct variable, given various shadowing variable declarations
    testTypesWithCommonExterns(
        lines(
            "/** @type {!Array<string>} */",
            "var arr = ['foo', 'bar'];",
            "let /** string */ n = 'foo';", // n in global scope
            "for (let /** number */ n of arr) {", // n in for of scope
            "  let /** null */ n = null;", // n in inner block scope
            "}"),
        lines(
            "declared type of for-of loop variable does not match inferred type",
            "found   : string",
            "required: number"));
  }

  public void testForOf_array1() {
    testTypesWithCommonExterns("for (var elem of [1, 2]) {}");
  }

  public void testForOf_array2() {
    testTypesWithCommonExterns(
        lines(
            "/** @type {!Array<number>} */ var arr = [1, 2];",
            "function takesString(/** string */ s) {}",
            "for (var elem of arr) { takesString(elem); }"),
        lines(
            "actual parameter 1 of takesString does not match formal parameter",
            "found   : number",
            "required: string"));
  }

  public void testForOf_array3() {
    testTypesWithCommonExterns(
        lines(
            "/** @type {!Array<number>} */ var arr = [1, 2];",
            "function takesNumber(/** number */ n) {}",
            "for (var elem of arr) { takesNumber(elem); }"));
  }

  public void testForOf_string1() {
    testTypesWithCommonExterns(
        lines(
            "function takesString(/** string */ s) {}",
            "for (var ch of 'a string') { takesString(ch); }"));
  }

  public void testForOf_string2() {
    testTypesWithCommonExterns(
        lines(
            "function takesNumber(/** number */ n) {}",
            "for (var ch of 'a string') { takesNumber(ch); }"),
        lines(
            "actual parameter 1 of takesNumber does not match formal parameter",
            "found   : string",
            "required: number"));
  }

  public void testForOf_StringObject1() {
    testTypesWithCommonExterns(
        lines(
            "function takesString(/** string */ s) {}",
            "for (var ch of new String('boxed')) { takesString(elem); }"));
  }

  public void testForOf_StringObject2() {
    testTypesWithCommonExterns(
        lines(
            "function takesNumber(/** number */ n) {}",
            "for (var ch of new String('boxed')) { takesNumber(elem); }"));
  }

  public void testForOf_iterableTypeIsNotFirstTemplateType() {
    testTypes(
        lines(
            "function takesNumber(/** number */ n) {}",
            "",
            "/**",
            " * @constructor",
            " * @implements {Iterable<T>}",
            " * @template S, T",
            " */",
            "function MyIterable() {}",
            "",
            "// Note that 'mi' is an Iterable<string>, not an Iterable<number>.",
            "/** @type {!MyIterable<number, string>} */",
            "var mi;",
            "",
            "for (var t of mi) { takesNumber(t); }", ""),
        lines(
            "actual parameter 1 of takesNumber does not match formal parameter",
            "found   : string",
            "required: number"));
  }

  public void testForOf_unionType1() {
    // TODO(b/77904110): Should be a type mismatch warning for passing a string to takesNumber
    testTypesWithCommonExterns(
        lines(
            "function takesNumber(/** number */ n) {}",
            "/** @param {(!Array<string>|undefined)} arr */",
            "function f(arr) {",
            "  for (let x of (arr || [])) {",
            "    takesNumber(x);",
            "  }",
            "}"));
  }

  public void testForOf_unionType2() {
    testTypes(
        lines(
            "/** @param {(number|undefined)} n */",
            "function f(n) {",
            "  for (let x of (n || [])) {}",
            "}"),
        lines(
            "Can only iterate over a (non-null) Iterable type",
            "found   : (Array|number)",
            "required: Iterable"));
  }

  public void testForOf_unionType3() {
    testTypesWithCommonExterns(
        lines(
            "function takesNull(/** null */ n) {}",
            "",
            "/** @param {string|!Array<number>} param */",
            "function f(param) {",
            "  for (let x of param) {",
            "    takesNull(x);", // TODO(lharker): this should cause a type error
            "  }",
            "}"));
  }

  public void testForOf_nullable() {
    testTypes(
        "/** @type {?Iterable} */ var it; for (var elem of it) {}",
        lines(
            "Can only iterate over a (non-null) Iterable type",
            "found   : (Iterable|null)",
            "required: Iterable"));
  }

  public void testForOf_null() {
    testTypes(
        "/** @type {null} */ var it = null; for (var elem of it) {}",
        lines(
            "Can only iterate over a (non-null) Iterable type",
            "found   : null",
            "required: Iterable"));
  }

  public void testForOf_maybeUndefined() {
    testTypes(
        "/** @type {!Iterable|undefined} */ var it; for (var elem of it) {}",
        lines(
            "Can only iterate over a (non-null) Iterable type",
            "found   : (Iterable|undefined)",
            "required: Iterable"));
  }

  public void testForOf_let() {
    // TypeCheck can now handle `let`
    testTypes("/** @type {!Iterable} */ let it; for (let elem of it) {}");
  }

  public void testForOf_const() {
    // TypeCheck can now handle const
    testTypesWithCommonExterns("/** @type {!Iterable} */ const it = []; for (const elem of it) {}");
  }

  public void testGenerator1() {
    testTypes("/** @return {!Generator<?>} */ function* gen() {}");
  }

  public void testGenerator2() {
    testTypes("/** @return {!Generator<number>} */ function* gen() { yield 1; }");
  }

  public void testGenerator3() {
    testTypesWithCommonExterns(
        "/** @return {!Generator<string>} */ function* gen() {  yield 1; }",
        lines(
            "Yielded type does not match declared return type.",
            "found   : number",
            "required: string"));
  }

  public void testGenerator4() {
    testTypes(
        lines(
            "/** @return {!Generator} */", // treat Generator as Generator<?>
            "function* gen() {",
            "  yield 1;",
            "}"));
  }

  public void testGenerator5() {
    // Test more complex type inference inside the yield expression
    testTypes(
        lines(
            "/** @return {!Generator<{a: number, b: string}>} */",
            "function *gen() {",
            "  yield {a: 3, b: '4'};",
            "}",
            "var g = gen();"));
  }

  public void testGenerator6() {
    testTypesWithCommonExterns(
        lines(
            "/** @return {!Generator<string>} */",
            "function* gen() {",
            "}",
            "var g = gen();",
            "var /** number */ n = g.next().value;"),
        lines(
            "initializing variable", // test that g.next().value typechecks properly
            "found   : string",
            "required: number"));
  }

  public void testGenerator_nextWithParameter() {
    // Note: we infer "var x = yield 1" to have a unknown type. Thus we don't warn "yield x + 2"
    // actually yielding a string, or "k" not being number type.
    testTypesWithCommonExterns(
        lines(
            "/** @return {!Generator<number>} */",
            "function* gen() {",
            "  var x = yield 1;",
            "  yield x + 2;",
            "}",
            "var g = gen();",
            "var /** number */ n = g.next().value;", // 1
            "var /** number */ k = g.next('').value;")); // '2'
  }

  public void testGenerator_yieldUndefined1() {
    testTypes(
        lines(
            "/** @return {!Generator<undefined>} */",
            "function* gen() {",
            "  yield undefined;",
            "  yield;", // yield undefined
            "}"));
  }

  public void testGenerator_yieldUndefined2() {
    testTypesWithCommonExterns(
        lines(
            "/** @return {!Generator<number>} */",
            "function* gen() {",
            "  yield;", // yield undefined
            "}"),
        lines(
            "Yielded type does not match declared return type.",
            "found   : undefined",
            "required: number"));
  }

  public void testGenerator_returnsIterable1() {
    testTypesWithCommonExterns("/** @return {!Iterable<?>} */ function *gen() {}");
  }

  public void testGenerator_returnsIterable2() {
    testTypesWithCommonExterns(
        "/** @return {!Iterable<string>} */ function* gen() {  yield 1; }",
        lines(
            "Yielded type does not match declared return type.",
            "found   : number",
            "required: string"));
  }

  public void testGenerator_returnsIterator1() {
    testTypesWithCommonExterns("/** @return {!Iterator<?>} */ function *gen() {}");
  }

  public void testGenerator_returnsIterator2() {
    testTypesWithCommonExterns(
        "/** @return {!Iterator<string>} */ function* gen() {  yield 1; }",
        lines(
            "Yielded type does not match declared return type.",
            "found   : number",
            "required: string"));
  }

  public void testGenerator_returnsIteratorIterable() {
    testTypesWithCommonExterns("/** @return {!IteratorIterable<?>} */ function *gen() {}");
  }

  public void testGenerator_cantReturnArray() {
    testTypes(
        "/** @return {!Array<?>} */ function *gen() {}",
        lines(
            "A generator function must return a (supertype of) Generator",
            "found   : Array<?>",
            "required: Generator"));
  }

  public void testGenerator_notAConstructor() {
    testTypes(
        lines(
            "/** @return {!Generator<number>} */",
            "function* gen() {",
            "  yield 1;",
            "}",
            "var g = new gen;"),
        "cannot instantiate non-constructor");
  }

  public void testGenerator_noDeclaredReturnType1() {
    testTypes("function *gen() {} var /** !Generator<?> */ g = gen();");
  }

  public void testGenerator_noDeclaredReturnType2() {
    testTypes("function *gen() {} var /** !Generator<number> */ g = gen();");
  }

  public void testGenerator_noDeclaredReturnType3() {
    // We infer gen() to return !Generator<?>, so don't warn for a type mismatch with string
    testTypesWithCommonExterns(
        lines(
            "function *gen() {",
            "  yield 1;",
            "  yield 2;",
            "}",
            "var /** string */ g = gen().next().value;"));
  }

  public void testGenerator_return1() {
    testTypes("/** @return {!Generator<number>} */ function *gen() { return 1; }");
  }

  public void testGenerator_return2() {
    testTypesWithCommonExterns("/** @return {!Generator<string>} */ function *gen() {  return 1; }",
        lines(
            "inconsistent return type",
            "found   : number",
            "required: string"));
  }

  public void testGenerator_return3() {
    // Allow this although returning "undefined" is inconsistent with !Generator<number>.
    // Probably the user is not intending to use the return value.
    testTypes("/** @return {!Generator<number>} */ function *gen() {  return; }");
  }

  // test yield*
  public void testGenerator_yieldAll1() {
    testTypesWithCommonExterns(
        lines(
            "/** @return {!Generator<number>} */",
            "function *gen() {",
            "  yield* [1, 2, 3];",
            "}"));
  }

  public void testGenerator_yieldAll2() {
    testTypes(
        "/** @return {!Generator<number>} */ function *gen() { yield* 1; }",
        lines(
            "Expression yield* expects an iterable",
            "found   : number",
            "required: Iterable"));
  }

  public void testGenerator_yieldAll3() {
    testTypesWithCommonExterns(
        lines(
            "/** @return {!Generator<number>} */",
            "function *gen1() {",
            "  yield 1;",
            "}",
            "",
            "/** @return {!Generator<number>} */",
            "function *gen2() {",
            "  yield* gen1();",
            "}"));
  }

  public void testGenerator_yieldAll4() {
    testTypesWithCommonExterns(
        lines(
            "/** @return {!Generator<string>} */",
            "function *gen1() {",
            "  yield 'a';",
            "}",
            "",
            "/** @return {!Generator<number>} */",
            "function *gen2() {",
            "  yield* gen1();",
            "}"),
        lines(
            "Yielded type does not match declared return type.",
            "found   : string",
            "required: number"));
  }

  public void testGenerator_yieldAll_string() {
    // Test that we autobox a string to a String
    testTypesWithCommonExterns(
        lines(
            "/** @return {!Generator<string>} */", // preserve newlines
            "function *gen() {",
            "  yield* 'some string';",
            "}"));
  }

  public void testGenerator_yieldAll_null() {
    testTypes(
        lines(
            "/** @return {!Generator<string>} */", // preserve newlines
            "function *gen() {",
            "  yield* null;",
            "}"),
        lines(
            "Expression yield* expects an iterable", // preserve newlines
            "found   : null",
            "required: Iterable"));
  }

  public void testMemberFunctionDef1() {
    testTypes(
        lines(
            "var obj = {", // line break
            "  method (/** number */ n) {}",
            "};",
            "obj.method(1);"));
  }

  public void testMemberFunctionDef2() {
    testTypes(
        lines(
            "var obj = {", // line break
            "  method (/** string */ n) {}",
            "};",
            "obj.method(1);"),
        lines(
            "actual parameter 1 of obj.method does not match formal parameter",
            "found   : number",
            "required: string"));
  }

  public void testMemberFunctionDef3() {
    testTypes("var obj = { method() {} }; new obj.method();", "cannot instantiate non-constructor");
  }

  public void testMemberFunctionDef_lends() {
    testTypesWithExterns(
        lines(
            "/** @constructor */",
            "function PolymerElement() {}",
            "/** @param {...*} var_args */",
            "PolymerElement.prototype.factoryImpl = function(var_args) {}",
            "var Polymer = function(a) {};"),
        lines(
            "/** @constructor @extends {PolymerElement} */",
            "var X = function() {};",
            "X = Polymer(/** @lends {X.prototype} */ {",
            "", // Test that we can override PolymerElement.prototype.factoryImpl with a one-arg fn
            "  factoryImpl(e) {",
            "    alert('Thank you for clicking');",
            "  },",
            "});"));
  }

  public void testMemberFunction_enum() {
    testTypes(
        "/** @enum */ var obj = {a() {}};",
        lines(
            "assignment to property a of enum{obj}",
            "found   : function(): undefined",
            "required: number"));
  }

  public void testComputedProp1() {
    testTypes("var i = 1; var obj = { ['var' + i]: i, };");
  }

  public void testComputedProp2a() {
    // Computed properties do type inference within
    testTypes(
        lines("var n; var obj = {[n = 'foo']: i}; var /** number */ m = n;"),
        lines(
            "initializing variable", // preserve new line
            "found   : string",
            "required: number"));
  }

  public void testComputedProp2b() {
    // Computed prop type checks within
    testTypes(
        lines(
            "var /** number */ n = 1;", // preserve new line
            "var obj = {",
            "  [n = 'foo']: i",
            "};"),
        lines(
            "assignment", // preserve new line
            "found   : string",
            "required: number"));
  }

  public void testComputedProp2c() {
    // Computed properties do type inference within
    testTypes(
        lines("var n; var obj = {[foo]: n = 'bar'}; var /** number */ m = n;"),
        lines(
            "initializing variable", // preserve new line
            "found   : string",
            "required: number"));
  }

  public void testComputedProp3() {
    // Computed prop does not exist as obj prop
    testTypes(
        lines("var i = 1; var obj = { ['var' + i]: i }; var x = obj.var1"),
        "Property var1 never defined on obj");
  }

  public void testComputedProp3b() {
    // Computed prop does not exist as obj prop even when a simple string literal
    testTypes(
        lines("var obj = { ['static']: 1 }; var /** number */ x = obj.static"),
        "Property static never defined on obj");
  }

  public void testComputedProp4() {
    testTypes(
        lines(
            "function takesString(/** string */ str) {}",
            "",
            "var obj = {",
            "  /** @param {number} x */",
            "  ['static']: (x) => {",
            "    takesString(x);",
            "  }",
            "};"),
        lines(
            "actual parameter 1 of takesString does not match formal parameter",
            "found   : number",
            "required: string"));
  }

  public void testComputedProp_symbol() {
    testTypes("var sym1 = Symbol('a'); var obj = {[sym1]: 1};");
  }

  public void testComputedProp_number() {
    testTypes("var obj = {[0]: 1};");
  }

  public void testComputedProp_badKeyType() {
    testTypes(
        "var foo = {}; var bar = {[foo]: 3};",
        lines(
            "property access", // preserve newline
            "found   : {}",
            "required: (string|symbol)"));
  }

  // TODO(b/78013196): Emit a warning for a restricted index type
  public void testComputedProp_restrictedIndexType() {
    testTypes("var /** !Object<string, *> */ obj = {[1]: 1};");
  }

  // TODO(b/78013196): Emit a warning here for a type mismatch
  // (Note - this also doesn't warn given non-computed properties.)
  public void testComputedProp_incorrectValueType1() {
    testTypes("var /** !Object<string, number> */ obj = {['x']: 'not numeric'};");
  }

  public void testComputedProp_incorrectValueType2() {
    // TODO(lharker): should we be emitting a type mismatch warning here?
    testTypes("var x = { /** @type {string} */ [1]: 12 };");
  }

  public void testComputedProp_struct1() {
    testTypes("/** @struct */ var obj = {[1 + 2]: 3};", "Cannot do '[]' access on a struct");
  }

  public void testComputedProp_struct2() {
    // Allow Symbol properties in a struct
    testTypesWithCommonExterns("/** @struct */ var obj = {[Symbol.iterator]: function() {}};");
  }

  public void testComputedProp_dict() {
    testTypes("/** @dict */ var obj = {[1]: 2};");
  }

  public void testComputedProp_enum() {
    testTypes("/** @enum */ var obj = {[1]: 2};", "enum key must be a string or numeric literal");
  }

  public void testTemplateLiteral1() {
    testTypes(
        lines(
            "", // preserve newline
            "var a, b",
            "var /** string */ s = `template ${a} string ${b}`;"));
  }

  public void testTemplateLiteral2() {
    // Check that type inference happens inside the TEMPLATE_SUB expression
    testTypes(
        "var n; var s = `${n = 'str'}`; var /** number */ m = n;",
        lines(
            "initializing variable", // preserve newline
            "found   : string",
            "required: number"));
  }

  public void testTemplateLiteral3() {
    // Check that we analyze types inside the TEMPLATE_SUB expression
    testTypes(
        "var /** number */ n = 1; var s = `template ${n = 'str'} string`;",
        lines(
            "assignment", // preserve newline
            "found   : string",
            "required: number"));
  }

  public void testTemplateLiteral_substitutionsHaveAnyType() {
    // Template strings can take any type.
    testTypes(
        lines(
            "function f(/** * */ anyTypeParam) {",
            "  var /** string */ s = `template ${anyTypeParam} string`;",
            "}"));
  }

  public void testTemplateLiteral_isStringType() {
    // Check template literal has type string
    testTypes(
        "var /** number */ n = `${1}`;",
        lines(
            "initializing variable", // preserve newline
            "found   : string",
            "required: number"));
  }

  // The first "argument" to a template literal tag function has type !ITemplateArray.
  public void testTaggedTemplateLiteral_tagParameters1() {
    // ITemplateArray works as the first parameter
    testTypesWithExtraExterns(
        lines(
            "/**",
            " * @param {!ITemplateArray} template",
            " * @param {...*} var_args Substitution values.",
            " * @return {string}",
            " */",
            "String.raw = function(template, var_args) {};"),
        "String.raw`one ${1} two`");
  }

  public void testTaggedTemplateLiteral_tagParameters2() {
    // !Array<string> works as the first parameter
    testTypesWithCommonExterns(
        lines(
            "function tag(/** !Array<string> */ strings){}", // preserve newline
            "tag`template string`;"));
  }

  public void testTaggedTemplateLiteral_tagParameters3() {
    // ?Array<string> works as the first parameter
    testTypesWithCommonExterns(
        lines(
            "function tag(/** ?Array<string> */ strings){}", // preserve newline
            "tag`template string`;"));
  }

  public void testTaggedTemplateLiteral_tagParameters4() {
    // Object works as the first parameter
    testTypes(
        lines(
            "function tag(/** Object */ strings){}", // preserve newline
            "tag `template string`;"));
  }

  public void testTaggedTemplateLiteral_tagParameters5() {
    // unknown type works as the first parameter.
    testTypes(
        lines(
            "function tag(/** ? */ strings){}", // preserve newline
            "tag `template string`;"));
  }

  public void testTaggedTemplateLiteral_invalidTagParameters1() {
    // Random object does not work as first parameter
    testTypes(
        lines(
            "function tag(/** {a: number} */ strings){}", // preserve newline
            "tag `template string`;"),
        lines(
            "Invalid type for the first parameter of tag function",
            "found   : {a: number}",
            "required: ITemplateArray"));
  }

  public void testTaggedTemplateLiteral_invalidTagParameters2() {
    // !Array<number> does not work as first parameter
    testTypes(
        lines(
            "function tag(/** !Array<number> */ strings) {}", // preserve newline
            "tag`template string`;"),
        lines(
            "Invalid type for the first parameter of tag function",
            "found   : Array<number>",
            "required: ITemplateArray"));
  }

  public void testTaggedTemplateLiteral_invalidTagParameters3() {
    // Tag function must have at least one parameter
    testTypes(
        "function tag(){} tag``;",
        "Function tag: called with 1 argument(s). "
            + "Function requires at least 0 argument(s) and no more than 0 argument(s).");
  }

  public void testTaggedTemplateLiteral_tagNotAFunction() {
    testTypes("const tag = 42; tag `template string`;", "number expressions are not callable");
  }

  public void testTaggedTemplateLiteral_nullableTagFunction() {
    testTypes(
        lines(
            "function f(/** ?function(!ITemplateArray) */ tag) {", // preserve newline
            "  tag `template string`;",
            "}"));
  }

  public void testTaggedTemplateLiteral_unknownTagFunction() {
    testTypes(
        lines(
            "function f(/** ? */ tag) {", // preserve newline
            "  tag `template string`;",
            "}"));
  }

  public void testTaggedTemplateLiteral_tooFewArguments() {
    testTypes(
        "function tag(strings, x, y) {} tag`${1}`;",
        "Function tag: called with 2 argument(s). "
            + "Function requires at least 3 argument(s) and no more than 3 argument(s).");
  }

  public void testTaggedTemplateLiteral_tooManyArguments() {
    testTypes(
        "function tag(strings, x) {} tag`${0} ${1}`;",
        "Function tag: called with 3 argument(s). "
            + "Function requires at least 2 argument(s) and no more than 2 argument(s).");
  }

  public void testTaggedTemplateLiteral_argumentTypeMismatch() {
    testTypes(
        lines(
            "function tag(strings, /** string */ s) {}", // preserve newline
            "tag`${123}`;"),
        lines(
            "actual parameter 2 of tag does not match formal parameter",
            "found   : number",
            "required: string"));
  }

  public void testTaggedTemplateLiteral_optionalArguments() {
    testTypes(
        lines(
            "/** @param {number=} y */ function tag(strings, y){}", // preserve newline
            "tag``;"));
  }

  public void testTaggedTemplateLiteral_varArgs() {
    testTypes(
        lines(
            "function tag(strings, /** ...number */ var_args){}", // preserve newline
            "tag`${1} ${'str'}`;"),
        lines(
            "actual parameter 3 of tag does not match formal parameter",
            "found   : string",
            "required: (number|undefined)"));
  }

  public void testTaggedTemplateLiteral_returnType1() {
    // Infer the TAGGED_TEMPLATELIT to have the return type of the tag function
    testTypes(
        lines(
            "function takesString(/** string */ s) {}",
            "",
            "/** @return {number} */",
            "function returnsNumber(strings){",
            "  return 1;",
            "}",
            "takesString(returnsNumber`str`);"),
        lines(
            "actual parameter 1 of takesString does not match formal parameter",
            "found   : number",
            "required: string"));
  }

  public void testTaggedTemplateLiteral_returnType2() {
    testTypes(
        lines(
            "function takesString(/** string */ s) {}",
            "/**",
            " * @param {!ITemplateArray} strings",
            " * @param {T} subExpr",
            " * @param {*} var_args",
            " * @return {T}",
            " * @template T",
            " */",
            "function getFirstTemplateLitSub(strings, subExpr, var_args) { return subExpr; }",
            "",
            "takesString(getFirstTemplateLitSub`${1}`);"),
        lines(
            "actual parameter 1 of takesString does not match formal parameter",
            "found   : number",
            "required: string"));
  }

  public void testTaggedTemplateLiteral_backInference() {
    // Test that we update the type of the template lit sub after back inference
    testTypes(
        lines(
            "/**",
            "* @param {T} x",
            "* @param {function(this:T, ...?)} z",
            "* @template T",
            "*/",
            "function f(x, z) {}",
            // infers that "this" is ITemplateArray inside the function literal
            "f`${ function() { /** @type {string} */ var x = this } }`;"),
        lines("initializing variable", "found   : ITemplateArray", "required: string"));
  }

  public void testITemplateArray1() {
    // Test that ITemplateArray is Iterable and iterating over it produces a string
    testTypesWithCommonExterns(
        lines(
            "function takesNumber(/** number */ n) {}",
            "function f(/** !ITemplateArray */ arr) {",
            "  for (let str of arr) {",
            "    takesNumber(str);",
            "  }",
            "}"),
        lines(
            "actual parameter 1 of takesNumber does not match formal parameter",
            "found   : string",
            "required: number"));
  }
}
