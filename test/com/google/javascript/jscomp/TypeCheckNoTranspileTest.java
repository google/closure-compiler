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

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link TypeCheck} on non-transpiled code. */
@RunWith(JUnit4.class)
public final class TypeCheckNoTranspileTest extends TypeCheckTestCase {

  @Override
  protected CompilerOptions getDefaultOptions() {
    CompilerOptions options = super.getDefaultOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_NEXT);
    return options;
  }

  @Test
  public void testCorrectSubtyping_ofRecursiveTemplateType() {
    testTypes(
        lines(
            "/** @template T */", //
            "class Base { }",
            "",
            "/** @extends {Base<!Child>} */",
            "class Child extends Base { }",
            "",
            // Confirm that `Child` is seen as a subtype of `Base<Child>`.
            "const /** !Base<!Child> */ x = new Child();"));
  }

  @Test
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

  @Test
  public void testArrowBlocklessInferredReturn() {
    // TODO(johnlenz): infer simple functions return results.

    // Verify arrows have do not have an incorrect inferred return.
    testTypes(
        lines(
            "let fn = () => 1;",
            "var /** null */ x = fn();"));
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testAsyncArrow_withValidBlocklessReturn_isAllowed() {
    testTypesWithCommonExterns(
        lines(
            "function takesPromiseProvider(/** function():!Promise<number> */ getPromise) {}",
            "takesPromiseProvider(async () => 1);"));
  }

  @Test
  public void testAsyncArrow_withInvalidBlocklessReturn_isError() {
    testTypesWithCommonExterns(
        lines(
            "function takesPromiseProvider(/** function():!Promise<string> */ getPromise) {}",
            "takesPromiseProvider(async () => 1);"),
        lines(
            "inconsistent return type", // preserve newline
            "found   : number",
            "required: (IThenable<string>|string)"));
  }

  @Test
  public void testAsyncArrow_withInferredReturnType_ofValidUnionType_isAllowed() {
    testTypesWithCommonExterns(
        lines(
            "/** @param {function():(number|!Promise<string>)} getPromise */",
            "function takesPromiseProvider(getPromise) {}",
            "",
            "takesPromiseProvider(async () => '');"));
  }

  @Test
  public void testAsyncArrow_withInferredReturnType_ofInvalidUnionType_isError() {
    testTypesWithCommonExterns(
        lines(
            "/** @param {function():(number|!Promise<string>)} getPromise */",
            "function takesPromiseProvider(getPromise) {}",
            "",
            "takesPromiseProvider(async () => true);"),
        lines(
            "inconsistent return type", // preserve newline
            "found   : boolean",
            "required: (IThenable<string>|string)"));
  }

  @Test
  public void testTypedefOfPropertyInBlock() {
    disableStrictMissingPropertyChecks();
    testTypesWithExterns(
        "/** @interface */ function Foo() {}",
        lines(
            "/** @constructor */",
            "function Bar(/** !Foo */ foo) {",
            "  /** @type {!Foo} */",
            "  this.foo = foo;",
            "  {",
            "    /** @typedef {boolean} */",
            "    this.foo.bar;",
            "    (() => this.foo.bar)();",
            "  }",
            "}"));
  }

  @Test
  public void testTypedefOfPropertyInFunctionScope() {
    disableStrictMissingPropertyChecks();
    testTypesWithExterns(
        "/** @interface */ function Foo() {}",
        lines(
            "/** @constructor */",
            "function Bar(/** !Foo */ foo) {",
            "  /** @type {!Foo} */",
            "  this.foo = foo;",
            "  /** @typedef {boolean} */",
            "  this.foo.bar;",
            "  {",
            "    (() => this.foo.bar)();",
            "  }",
            "}"));
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testArgumentSpreadNonIterable() {
    testTypesWithExtraExterns(
        "function use(x) {}",
        "use(...1);",
        lines(
            "Spread operator only applies to Iterable types",
            "found   : number",
            "required: Iterable"));
  }

  @Test
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

  @Test
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

  @Test
  public void testOnlyRestParameterWithoutJSDocCalledWithNoArgs() {
    testTypes(
        lines(
            "", // input lines
            "function use(...numbers) {}",
            "use();", // no args provided in call - should be OK
            ""));
  }

  @Test
  public void testBadRestJSDoc() {
    // TODO(lharker): this should warn that the "number" should be "...number".
    // Currently we issue this warning in Es6RewriteRestAndSpread.
    testTypes(
        "/** @param {number} numbers */ function f(...numbers) { var /** null */ n = numbers; }",
        lines(
            "initializing variable", //
            "found   : Array<number>",
            "required: null"));
  }

  @Test
  public void testOnlyRestParameterWithoutJSDocCalledWithArgs() {
    testTypes(
        lines(
            "", // input lines
            "function use(...numbers) {}",
            "use(1, 'hi', {});",
            ""));
  }

  @Test
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

  @Test
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

  @Test
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
            "required: number"));
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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
            "required: number"));
  }

  @Test
  public void testRestParameterInCallbackIsInferred() {
    testTypes(
        lines(
            "/** @param {function(...number)} callback */",
            "function f(callback) {}",
            "",
            "f((...strings) => {",
            "  const /** null */ n = strings;", // verify that this causes a type mismatch
            "});"),
        lines(
            "initializing variable", //
            "found   : Array<number>",
            "required: null"));
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testDuplicateCatchVarName() {
    // Make sure that catch variables with the same name are considered to be distinct variables
    // rather than causing a redeclaration error.
    testTypes(
        lines(
            "try { throw 1; } catch (/** @type {number} */ err) {}",
            "try { throw 'error'; } catch (/** @type {string} */ err) {}",
            ""));
  }

  @Test
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

  @Test
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

  @Test
  public void testTypedefAliasValueTypeIsUndefined() {
    // Aliasing a typedef (const Alias = SomeTypedef) should be interchangeable with the original.
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

  @Test
  public void testTypedefAliasOfLocalTypedef() {
    // Aliasing should work on local typedefs as well as global.
    testTypes(
        lines(
            "function f() {",
            "  /** @typedef {number} */",
            "  var MyNumber;",
            "  /** @const */",
            "  var Alias = MyNumber;",
            "  /** @type {Alias} */",
            "  var x = 'x';",
            "}"),
        lines(
            "initializing variable", // preserve newlines
            "found   : string",
            "required: number"));
  }

  @Test
  public void testTypedefDestructuredAlias() {
    // Aliasing should work on local typedefs as well as global.
    testTypes(
        lines(
            "function f() {",
            "  const ns = {};",
            "  /** @typedef {number} */",
            "  ns.MyNumber;",
            "  const {MyNumber: Alias} = ns;",
            "  /** @type {Alias} */",
            "  var x = 'x';",
            "}"),
        lines(
            "initializing variable", // preserve newlines
            "found   : string",
            "required: number"));
  }

  @Test
  public void testTypedefDestructuredAlias_deeplyNested() {
    // Aliasing should work on local typedefs as well as global.
    testTypes(
        lines(
            "function f() {",
            "  const outer = {};",
            "  /** @const */",
            "  outer.inner = {};",
            "  /** @typedef {number} */",
            "  outer.inner.MyNumber;",
            "  const alias = {};",
            "  ({inner: /** @const */ alias.ns} = outer);",
            "  /** @type {alias.ns.MyNumber} */",
            "  var x = 'x';",
            "}"),
        // TODO(sdh): Should parse correctly and give an initializing variable error.
        // It looks like this is a result of the `const` being ignored.
        "Bad type annotation. Unknown type alias.ns.MyNumber");
  }

  @Test
  public void testTypedefLocalQualifiedName() {
    // Aliasing should work on local typedefs as well as global.
    testTypes(
        lines(
            "function f() {",
            "  /** @const */",
            "  var ns = {};",
            "  /** @typedef {number} */",
            "  ns.MyNumber;",
            "  /** @type {ns.MyNumber} */",
            "  var x = 'x';",
            "}"),
        lines(
            "initializing variable", // preserve newlines
            "found   : string",
            "required: number"));
  }

  @Test
  public void testTypedefLocalQualifiedNameAlias() {
    // Aliasing should work on local typedefs as well as global.
    testTypes(
        lines(
            "function f() {",
            "  /** @typedef {number} */",
            "  var MyNumber;",
            "  /** @const */",
            "  var ns = {};",
            "  /** @const */",
            "  ns.MyNumber = MyNumber;",
            "  /** @type {ns.MyNumber} */",
            "  var x = 'x';",
            "}"),
        lines(
            "initializing variable", // preserve newlines
            "found   : string",
            "required: number"));
  }

  @Test
  public void testTypedefLocalAliasOfGlobalTypedef() {
    // Should also work if the alias is local but the typedef is global.
    testTypes(
        lines(
            "/** @typedef {number} */",
            "var MyNumber;",
            "function f() {",
            "  /** @const */ var Alias = MyNumber;",
            "  var /** Alias */ x = 'x';",
            "}"),
        lines(
            "initializing variable", // preserve newlines
            "found   : string",
            "required: number"));
  }

  @Test
  public void testTypedefOnAliasedNamespace() {
    // Aliasing a namespace (const alias = ns) should carry over any typedefs on the namespace.
    testTypes(
        lines(
            "const ns = {};",
            "/** @const */ ns.bar = 'x';",
            "/** @typedef {number} */", // preserve newlines
            "ns.MyNumber;",
            "const alias = ns;",
            "/** @const */ alias.foo = 42",
            "/** @type {alias.MyNumber} */ const x = 'str';",
            ""),
        // TODO(sdh): should be non-nullable number, but we get nullability wrong.
        lines(
            "initializing variable", // preserve newlines
            "found   : string",
            "required: (null|number)"));
  }

  @Test
  public void testTypedefOnLocalAliasedNamespace() {
    // Aliasing a namespace (const alias = ns) should carry over any typedefs on the namespace.
    testTypes(
        lines(
            "function f() {",
            "  const ns = {};",
            "  /** @typedef {number} */", // preserve newlines
            "  ns.MyNumber;",
            "  const alias = ns;",
            "  /** @const */ alias.foo = 42",
            "  /** @type {alias.MyNumber} */ const x = 'str';",
            "}"),
        // TODO(sdh): should be non-nullable number, but we get nullability wrong.
        lines(
            "initializing variable", // preserve newlines
            "found   : string",
            "required: (null|number)"));
  }

  @Test
  public void testTypedefOnClassSideInheritedSubtype() {
    // Class-side inheritance should carry over any typedefs nested on the class.
    testTypes(
        lines(
            "class Base {}",
            "/** @typedef {number} */", // preserve newlines
            "Base.MyNumber;",
            "class Sub extends Base {}",
            "/** @type {Sub.MyNumber} */ const x = 'str';",
            ""),
        lines(
            "initializing variable", // preserve newlines
            "found   : string",
            "required: (null|number)"));
  }

  @Test
  public void testGetTypedPercent() {
    // Make sure names declared with `const` and `let` are counted correctly for typed percentage.
    // This was created my a modifying a copy of TypeCheckTest.testGetTypedPercent1()
    String js =
        lines(
            "const id = function(x) { return x; }",
            "let id2 = function(x) { return id(x); }");
    assertThat(getTypedPercent(js)).isWithin(0.1).of(50.0);
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testConstWrongType() {
    testTypes(
        "/** @type {number} */ const x = 'hi';",
        lines(
            "initializing variable", // preserve newlines
            "found   : string",
            "required: number"));
  }

  @Test
  public void testLetWrongType() {
    testTypes(
        "/** @type {number} */ let x = 'hi';",
        lines(
            "initializing variable", // preserve newlines
            "found   : string",
            "required: number"));
  }

  @Test
  public void testLetInitializedToUndefined1() {
    testTypes(
        "let foo; let /** number */ bar = foo;",
        lines(
            "initializing variable", // preserve newline
            "found   : undefined",
            "required: number"));
  }

  @Test
  public void testLetInitializedToUndefined2() {
    // Use the declared type of foo instead of inferring it to be undefined.
    testTypes("let /** number */ foo; let /** number */ bar = foo;");
  }

  @Test
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

  @Test
  public void testForOf1() {
    testTypes("/** @type {!Iterable} */ var it; for (var elem of it) {}");
  }

  @Test
  public void testForOf2() {
    testTypes(
        lines(
            "function takesString(/** string */ s) {}",
            "/** @type {!Iterable<string>} */ var it;",
            "for (var elem of it) { takesString(elem); }"));
  }

  @Test
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

  @Test
  public void testForOf4() {
    testTypes("/** @type {!Iterable} */ var it; var obj = {}; for (obj.elem of it) {}");
  }

  @Test
  public void testForOf5() {
    // We infer the type of a qualified name in a for-of loop initializer
    testTypes(
        lines(
            "function takesString(/** string */ s) {}",
            "",
            "function f(/** !Iterable<number> */ it) {",
            "  var obj = {};",
            "  for (obj.elem of it) {",
            "    takesString(obj.elem);",
            "  }",
            "}"),
        lines(
            "actual parameter 1 of takesString does not match formal parameter",
            "found   : number",
            "required: string"));
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testForOf_wrongLoopVarType4a() {
    testTypesWithCommonExterns(
        lines(
            "/** @type {!Array<!Object>} */",
            "var arr = [1, 2];",
            "for (let /** ?Object */ elem of arr) {",
            "}"));
  }

  @Test
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

  @Test
  public void testForOf_wrongLoopVarType5() {
    // Test that we don't check the inferred type of n against the Iterable type
    testTypesWithCommonExterns(
        lines(
            "/** @type {!Array<number>} */",
            "var arr = [1, 2];",
            "let n = null;",
            "for (n of arr) {}"));
  }

  @Test
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

  @Test
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

  @Test
  public void testForOf_wrongLoopVarType7() {
    testTypes(
        lines(
            "/** @type {!Iterable<string>} */ var it;",
            "var /** !Object<string, number> */ obj = {};",
            "for (obj['x'] of it) {}"),
        lines(
            "declared type of for-of loop variable does not match inferred type",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testForOf_wrongLoopVarType8() {
    testTypes(
        lines(
            "/** @type {!Iterable<string>} */ var it;",
            "const /** @type {{x: number}} */ obj = {x: 5};",
            "for (obj.x of it) {}"),
        lines(
            "assignment to property x of obj", // preserve newline
            "found   : string",
            "required: number"));
  }

  @Test
  public void testForOf_illegalPropertyCreation() {
    testTypes(
        lines(
            "/** @type {!Iterable<string>} */ var it;",
            "const /** @struct */ obj = {};",
            "for (obj.x of it) {}"),
        "Cannot add a property to a struct instance after it is constructed. "
            + "(If you already declared the property, make sure to give it a type.)");
  }

  @Test
  public void testForOf_badInterfaceMemberCreation() {
    testTypesWithCommonExterns(
        lines(
            "/** @interface */", // preserve newline
            "function Foo() {}",
            "for (Foo.prototype.bar of []) {}"),
        "interface members can only be empty property declarations, "
            + "empty functions, or goog.abstractMethod");
  }

  @Test
  public void testForOf_badEnumCreation() {
    testTypesWithCommonExterns(
        "for (var /** @enum */ myEnum of []) {}",
        "enum initializer must be an object literal or an enum");
  }

  @Test
  public void testForOf_array1() {
    testTypesWithCommonExterns("for (var elem of [1, 2]) {}");
  }

  @Test
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

  @Test
  public void testForOf_array3() {
    testTypesWithCommonExterns(
        lines(
            "/** @type {!Array<number>} */ var arr = [1, 2];",
            "function takesNumber(/** number */ n) {}",
            "for (var elem of arr) { takesNumber(elem); }"));
  }

  @Test
  public void testForOf_string1() {
    testTypesWithCommonExterns(
        lines(
            "function takesString(/** string */ s) {}",
            "for (var ch of 'a string') { takesString(ch); }"));
  }

  @Test
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

  @Test
  public void testForOf_StringObject1() {
    testTypesWithCommonExterns(
        lines(
            "function takesString(/** string */ s) {}",
            "for (var ch of new String('boxed')) { takesString(elem); }"));
  }

  @Test
  public void testForOf_StringObject2() {
    testTypesWithCommonExterns(
        lines(
            "function takesNumber(/** number */ n) {}",
            "for (var ch of new String('boxed')) { takesNumber(elem); }"));
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testForOf_nullable() {
    testTypes(
        "/** @type {?Iterable} */ var it; for (var elem of it) {}",
        lines(
            "Can only iterate over a (non-null) Iterable type",
            "found   : (Iterable|null)",
            "required: Iterable"));
  }

  @Test
  public void testForOf_null() {
    testTypes(
        "/** @type {null} */ var it = null; for (var elem of it) {}",
        lines(
            "Can only iterate over a (non-null) Iterable type",
            "found   : null",
            "required: Iterable"));
  }

  @Test
  public void testForOf_maybeUndefined() {
    testTypes(
        "/** @type {!Iterable|undefined} */ var it; for (var elem of it) {}",
        lines(
            "Can only iterate over a (non-null) Iterable type",
            "found   : (Iterable|undefined)",
            "required: Iterable"));
  }

  @Test
  public void testForOf_let() {
    // TypeCheck can now handle `let`
    testTypes("/** @type {!Iterable} */ let it; for (let elem of it) {}");
  }

  @Test
  public void testForOf_const() {
    // TypeCheck can now handle const
    testTypesWithCommonExterns("/** @type {!Iterable} */ const it = []; for (const elem of it) {}");
  }

  @Test
  public void testImplicitCastInForOf() {
    testTypesWithExtraExterns(
        lines(
            "/** @constructor */ function Element() {};",
            "/**",
            " * @type {string}",
            " * @implicitCast",
            " */",
            "Element.prototype.innerHTML;"),
        lines(
            "/** @param {?Element} element",
            " * @param {!Array<string|number>} texts",
            " */",
            "function f(element, texts) {",
            "  for (element.innerHTML of texts) {};",
            "}",
            ""));
  }

  @Test
  public void testGenerator1() {
    testTypes("/** @return {!Generator<?>} */ function* gen() {}");
  }

  @Test
  public void testGenerator2() {
    testTypes("/** @return {!Generator<number>} */ function* gen() { yield 1; }");
  }

  @Test
  public void testGenerator3() {
    testTypesWithCommonExterns(
        "/** @return {!Generator<string>} */ function* gen() {  yield 1; }",
        lines(
            "Yielded type does not match declared return type.",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testGenerator4() {
    testTypes(
        lines(
            "/** @return {!Generator} */", // treat Generator as Generator<?>
            "function* gen() {",
            "  yield 1;",
            "}"));
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testGenerator_yieldUndefined1() {
    testTypes(
        lines(
            "/** @return {!Generator<undefined>} */",
            "function* gen() {",
            "  yield undefined;",
            "  yield;", // yield undefined
            "}"));
  }

  @Test
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

  @Test
  public void testGenerator_returnsIterable1() {
    testTypesWithCommonExterns("/** @return {!Iterable<?>} */ function *gen() {}");
  }

  @Test
  public void testGenerator_returnsIterable2() {
    testTypesWithCommonExterns(
        "/** @return {!Iterable<string>} */ function* gen() {  yield 1; }",
        lines(
            "Yielded type does not match declared return type.",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testGenerator_returnsIterator1() {
    testTypesWithCommonExterns("/** @return {!Iterator<?>} */ function *gen() {}");
  }

  @Test
  public void testGenerator_returnsIterator2() {
    testTypesWithCommonExterns(
        "/** @return {!Iterator<string>} */ function* gen() {  yield 1; }",
        lines(
            "Yielded type does not match declared return type.",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testGenerator_returnsIteratorIterable() {
    testTypesWithCommonExterns("/** @return {!IteratorIterable<?>} */ function *gen() {}");
  }

  @Test
  public void testGenerator_cantReturnArray() {
    testTypes(
        "/** @return {!Array<?>} */ function *gen() {}",
        lines(
            "A generator function must return a (supertype of) Generator",
            "found   : Array<?>",
            "required: Generator"));
  }

  @Test
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

  @Test
  public void testGenerator_noDeclaredReturnType1() {
    testTypes("function *gen() {} var /** !Generator<?> */ g = gen();");
  }

  @Test
  public void testGenerator_noDeclaredReturnType2() {
    testTypes("function *gen() {} var /** !Generator<number> */ g = gen();");
  }

  @Test
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

  @Test
  public void testGenerator_return1() {
    testTypes("/** @return {!Generator<number>} */ function *gen() { return 1; }");
  }

  @Test
  public void testGenerator_return2() {
    testTypesWithCommonExterns("/** @return {!Generator<string>} */ function *gen() {  return 1; }",
        lines(
            "inconsistent return type",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testGenerator_return3() {
    // Allow this although returning "undefined" is inconsistent with !Generator<number>.
    // Probably the user is not intending to use the return value.
    testTypes("/** @return {!Generator<number>} */ function *gen() {  return; }");
  }

  // test yield*
  @Test
  public void testGenerator_yieldAll1() {
    testTypesWithCommonExterns(
        lines(
            "/** @return {!Generator<number>} */",
            "function *gen() {",
            "  yield* [1, 2, 3];",
            "}"));
  }

  @Test
  public void testGenerator_yieldAll2() {
    testTypes(
        "/** @return {!Generator<number>} */ function *gen() { yield* 1; }",
        lines(
            "Expression yield* expects an iterable",
            "found   : number",
            "required: Iterable"));
  }

  @Test
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

  @Test
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

  @Test
  public void testGenerator_yieldAll_string() {
    // Test that we autobox a string to a String
    testTypesWithCommonExterns(
        lines(
            "/** @return {!Generator<string>} */", // preserve newlines
            "function *gen() {",
            "  yield* 'some string';",
            "}"));
  }

  @Test
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

  @Test
  public void testMemberFunctionDef1() {
    testTypes(
        lines(
            "var obj = {", // line break
            "  method (/** number */ n) {}",
            "};",
            "obj.method(1);"));
  }

  @Test
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

  @Test
  public void testMemberFunctionDef3() {
    testTypes("var obj = { method() {} }; new obj.method();", "cannot instantiate non-constructor");
  }

  @Test
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

  @Test
  public void testMemberFunction_enum() {
    testTypes(
        "/** @enum */ var obj = {a() {}};",
        lines(
            "assignment to property a of enum{obj}",
            "found   : function(): undefined",
            "required: number"));
  }

  @Test
  public void testComputedProp1() {
    testTypes("var i = 1; var obj = { ['var' + i]: i, };");
  }

  @Test
  public void testComputedProp2a() {
    // Computed properties do type inference within
    testTypes(
        lines(
            "var n;", //
            "var obj = {[n = 'foo']: i};",
            "var /** number */ m = n;"),
        lines(
            "initializing variable", // preserve new line
            "found   : string",
            "required: number"));
  }

  @Test
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

  @Test
  public void testComputedProp2c() {
    // Computed properties do type inference within
    testTypes(
        lines(
            "var n;", //
            "var obj = {[foo]: n = 'bar'};",
            "var /** number */ m = n;"),
        lines(
            "initializing variable", // preserve new line
            "found   : string",
            "required: number"));
  }

  @Test
  public void testComputedProp3() {
    // Computed prop does not exist as obj prop
    testTypes(
        lines(
            "var i = 1;", //
            "var obj = { ['var' + i]: i };",
            "var x = obj.var1"),
        "Property var1 never defined on obj");
  }

  @Test
  public void testComputedProp3b() {
    // Computed prop does not exist as obj prop even when a simple string literal
    testTypes(
        lines(
            "var obj = { ['static']: 1 };", //
            "var /** number */ x = obj.static"),
        "Property static never defined on obj");
  }

  @Test
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

  @Test
  public void testComputedProp_symbol() {
    testTypes("var sym1 = Symbol('a'); var obj = {[sym1]: 1};");
  }

  @Test
  public void testComputedProp_number() {
    testTypes("var obj = {[0]: 1};");
  }

  @Test
  public void testComputedProp_badKeyType() {
    testTypes(
        "var foo = {}; var bar = {[foo]: 3};",
        lines(
            "property access", // preserve newline
            "found   : {}",
            "required: (string|symbol)"));
  }

  // TODO(b/78013196): Emit a warning for a restricted index type
  @Test
  public void testComputedProp_restrictedIndexType() {
    testTypes("var /** !Object<string, *> */ obj = {[1]: 1};");
  }

  // TODO(b/78013196): Emit a warning here for a type mismatch
  // (Note - this also doesn't warn given non-computed properties.)
  @Test
  public void testComputedProp_incorrectValueType1() {
    testTypes("var /** !Object<string, number> */ obj = {['x']: 'not numeric'};");
  }

  @Test
  public void testComputedProp_incorrectValueType2() {
    // TODO(lharker): should we be emitting a type mismatch warning here?
    testTypes("var x = { /** @type {string} */ [1]: 12 };");
  }

  @Test
  public void testComputedProp_struct1() {
    testTypes("/** @struct */ var obj = {[1 + 2]: 3};", "Cannot do '[]' access on a struct");
  }

  @Test
  public void testComputedProp_struct2() {
    // Allow Symbol properties in a struct
    testTypesWithCommonExterns("/** @struct */ var obj = {[Symbol.iterator]: function() {}};");
  }

  @Test
  public void testComputedProp_dict() {
    testTypes("/** @dict */ var obj = {[1]: 2};");
  }

  @Test
  public void testComputedProp_enum() {
    testTypes("/** @enum */ var obj = {[1]: 2};", "enum key must be a string or numeric literal");
  }

  @Test
  public void testComputedPropAllowedOnDictClass() {
    testTypes(
        lines(
            "/** @dict */", //
            "class C {",
            "  ['f']() {}",
            "}"));
  }

  @Test
  public void testNormalPropNotAllowedOnDictClass() {
    testTypes(
        lines(
            "/** @dict */", //
            "class C {",
            "  foo() {}",
            "}"),
        "Illegal key, the class is a dict");
  }

  @Test
  public void testComputedPropNotAllowedOnStructClass() {
    testTypes(
        lines(
            "class C {", // @struct is the default
            "  foo() {}",
            "  ['f']() {}",
            "}"),
        "Cannot do '[]' access on a struct");
  }

  @Test
  public void testQuotedGetterPropNotAllowedOnStructClass() {
    testTypes(
        lines(
            "class C {", // @struct is the default
            "  foo() {}",
            "  get 'f'() {}",
            "}"),
        "Illegal key, the class is a struct");
  }

  @Test
  public void testTemplateLiteral1() {
    testTypes(
        lines(
            "", // preserve newline
            "var a, b",
            "var /** string */ s = `template ${a} string ${b}`;"));
  }

  @Test
  public void testTemplateLiteral2() {
    // Check that type inference happens inside the TEMPLATE_SUB expression
    testTypes(
        "var n; var s = `${n = 'str'}`; var /** number */ m = n;",
        lines(
            "initializing variable", // preserve newline
            "found   : string",
            "required: number"));
  }

  @Test
  public void testTemplateLiteral3() {
    // Check that we analyze types inside the TEMPLATE_SUB expression
    testTypes(
        "var /** number */ n = 1; var s = `template ${n = 'str'} string`;",
        lines(
            "assignment", // preserve newline
            "found   : string",
            "required: number"));
  }

  @Test
  public void testTemplateLiteral_substitutionsHaveAnyType() {
    // Template strings can take any type.
    testTypes(
        lines(
            "function f(/** * */ anyTypeParam) {",
            "  var /** string */ s = `template ${anyTypeParam} string`;",
            "}"));
  }

  @Test
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
  @Test
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

  @Test
  public void testTaggedTemplateLiteral_tagParameters2() {
    // !Array<string> works as the first parameter
    testTypesWithCommonExterns(
        lines(
            "function tag(/** !Array<string> */ strings){}", // preserve newline
            "tag`template string`;"));
  }

  @Test
  public void testTaggedTemplateLiteral_tagParameters3() {
    // ?Array<string> works as the first parameter
    testTypesWithCommonExterns(
        lines(
            "function tag(/** ?Array<string> */ strings){}", // preserve newline
            "tag`template string`;"));
  }

  @Test
  public void testTaggedTemplateLiteral_tagParameters4() {
    // Object works as the first parameter
    testTypes(
        lines(
            "function tag(/** Object */ strings){}", // preserve newline
            "tag `template string`;"));
  }

  @Test
  public void testTaggedTemplateLiteral_tagParameters5() {
    // unknown type works as the first parameter.
    testTypes(
        lines(
            "function tag(/** ? */ strings){}", // preserve newline
            "tag `template string`;"));
  }

  @Test
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

  @Test
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

  @Test
  public void testTaggedTemplateLiteral_invalidTagParameters3() {
    // Tag function must have at least one parameter
    testTypes(
        "function tag(){} tag``;",
        "Function tag: called with 1 argument(s). "
            + "Function requires at least 0 argument(s) and no more than 0 argument(s).");
  }

  @Test
  public void testTaggedTemplateLiteral_tagNotAFunction() {
    testTypes("const tag = 42; tag `template string`;", "number expressions are not callable");
  }

  @Test
  public void testTaggedTemplateLiteral_nullableTagFunction() {
    testTypes(
        lines(
            "function f(/** ?function(!ITemplateArray) */ tag) {", // preserve newline
            "  tag `template string`;",
            "}"));
  }

  @Test
  public void testTaggedTemplateLiteral_unknownTagFunction() {
    testTypes(
        lines(
            "function f(/** ? */ tag) {", // preserve newline
            "  tag `template string`;",
            "}"));
  }

  @Test
  public void testTaggedTemplateLiteral_tooFewArguments() {
    testTypes(
        "function tag(strings, x, y) {} tag`${1}`;",
        "Function tag: called with 2 argument(s). "
            + "Function requires at least 3 argument(s) and no more than 3 argument(s).");
  }

  @Test
  public void testTaggedTemplateLiteral_tooManyArguments() {
    testTypes(
        "function tag(strings, x) {} tag`${0} ${1}`;",
        "Function tag: called with 3 argument(s). "
            + "Function requires at least 2 argument(s) and no more than 2 argument(s).");
  }

  @Test
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

  @Test
  public void testTaggedTemplateLiteral_argumentWithCast() {
    testTypes(
        lines(
            "function tag(strings, /** string */ s) {}", // preserve newline
            "tag`${ /** @type {?} */ (123) }`;"));
  }

  @Test
  public void testTaggedTemplateLiteral_optionalArguments() {
    testTypes(
        lines(
            "/** @param {number=} y */ function tag(strings, y){}", // preserve newline
            "tag``;"));
  }

  @Test
  public void testTaggedTemplateLiteral_varArgs() {
    testTypes(
        lines(
            "function tag(strings, /** ...number */ var_args){}", // preserve newline
            "tag`${1} ${'str'}`;"),
        lines(
            "actual parameter 3 of tag does not match formal parameter",
            "found   : string",
            "required: number"));
  }

  @Test
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

  @Test
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

  @Test
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
        lines(
            "initializing variable", //
            "found   : ITemplateArray",
            "required: string"));
  }

  @Test
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

  @Test
  public void testClassDeclarationWithReturn() {
    testTypes(
        lines(
            "var /** ?Foo */ cached = null;", //
            "class Foo {",
            "  constructor() {",
            "    if (cached) return cached; ",
            "  }",
            "}",
            ""));
  }

  @Test
  public void testInvalidInvocationOfClassConstructor() {
    testTypes(
        lines(
            "class Foo {", //
            "  constructor() {",
            "  }",
            "}",
            "let /** ? */ x = Foo()"),
        lines(
            "Constructor function(new:Foo): undefined should be called with the \"new\" keyword"));
  }

  @Test
  public void testInvalidInvocationOfClassConstructorWithReturnDeclaration() {
    testTypes(
        lines(
            "class Foo {", //
            "  /** @return {!Array} */",
            "  constructor() {",
            "  }",
            "}",
            "let /** ? */ x = Foo()"),
        lines(
            "Constructor function(new:Foo): undefined should be called with the \"new\" keyword"));
  }

  @Test
  public void testClassDeclarationWithTemplate() {
    testTypes(
        lines(
            "/** @template T */", //
            "class C {",
            "  /** @param {T} a */",
            "  constructor(a) {",
            "  }",
            "}",
            "/** @type {null} */",
            "const x = new C(0);"),
        lines(
            "initializing variable", //
            "found   : C<number>",
            "required: null"));
  }

  @Test
  public void testClassDeclaration() {
    testTypes(
        lines(
            "class Foo {}", //
            "var /** !Foo */ foo = new Foo();"));
  }

  @Test
  public void testClassDeclarationMismatch() {
    testTypes(
        lines(
            "class Foo {}", //
            "class Bar {}",
            "var /** !Foo */ foo = new Bar();"),
        lines(
            "initializing variable", //
            "found   : Bar",
            "required: Foo"));
  }

  @Test
  public void testClassGenerics() {
    testTypes(
        lines(
            "/** @template T */", //
            "class Foo {}",
            "var /** !Foo<number> */ x = new Foo();",
            "var /** !Foo<string> */ y = x;"),
        lines(
            "initializing variable", //
            "found   : Foo<number>",
            "required: Foo<string>"));
  }

  @Test
  public void testClassTooManyTypeParameters() {
    // TODO(sdh): This should give a warning about too many type parameters.
    testTypes(
        lines(
            "class Foo {}", //
            "var /** !Foo<number> */ x = new Foo();",
            "var /** !Foo<string> */ y = x;"));
  }

  @Test
  public void testClassWithTemplatizedConstructorTooManyTypeParameters() {
    // TODO(sdh): This should give a warning about too many type parameters.
    testTypes(
        lines(
            "class Foo {",
            "  /** @template T */ constructor() {}",
            "}", //
            "var /** !Foo<number> */ x = new Foo();",
            "var /** !Foo<string> */ y = x;"));
  }

  @Test
  public void testClassWithTemplatizedClassAndConstructorTooManyTypeParameters() {
    // TODO(sdh): This should give a warning about too many type parameters.
    testTypes(
        lines(
            "/** @template T */",
            "class Foo {",
            "  /** @template U */ constructor() {}",
            "}", //
            "var /** !Foo<number, number> */ x = new Foo();",
            "var /** !Foo<number, string> */ y = x;"));
  }

  @Test
  public void testClassDeclarationWithExtends() {
    testTypes(
        lines(
            "class Foo {}", //
            "class Bar extends Foo {}",
            "var /** !Foo */ foo = new Bar();"));
  }

  @Test
  public void testClassDeclarationWithExtendsMismatch() {
    testTypes(
        lines(
            "class Foo {}", //
            "class Bar extends Foo {}",
            "var /** !Bar */ foo = new Foo();"),
        lines(
            "initializing variable", //
            "found   : Foo",
            "required: Bar"));
  }

  @Test
  public void testClassDeclarationWithTransitiveExtends() {
    testTypes(
        lines(
            "class Foo {}", //
            "class Bar extends Foo {}",
            "class Baz extends Bar {}",
            "var /** !Foo */ foo = new Baz();"));
  }

  @Test
  public void testClassDeclarationWithAnonymousExtends() {
    testTypes(
        lines(
            "class Foo {}", //
            "class Bar extends class extends Foo {} {}",
            "var /** !Foo */ foo = new Bar();"));
  }

  @Test
  public void testClassDeclarationInlineConstructorParameters() {
    testTypes(
        lines(
            "class Foo {", //
            "  constructor(/** number */ arg) {}",
            "}",
            "new Foo(42);"));
  }

  @Test
  public void testClassDeclarationConstructorParametersMismatch() {
    testTypes(
        lines(
            "class Foo {", //
            "  constructor(/** number */ arg) {}",
            "}",
            "new Foo('xyz');"),
        lines(
            "actual parameter 1 of Foo does not match formal parameter",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testClassDeclarationTraditionalConstructorParameters() {
    testTypes(
        lines(
            "class Foo {", //
            "  /** @param {number} arg */",
            "  constructor(arg) {}",
            "}",
            "new Foo(42);"));
  }

  @Test
  public void testClassDeclarationTraditionalConstructorParametersMismatch() {
    testTypes(
        lines(
            "class Foo {",
            "  /** @param {number} arg */",
            "  constructor(arg) {}",
            "}",
            "new Foo('xyz');"),
        lines(
            "actual parameter 1 of Foo does not match formal parameter",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testClassDeclarationInheritedConstructorParameters() {
    testTypes(
        lines(
            "class Foo {",
            "  constructor(/** number */ arg) {}",
            "}",
            "class Bar extends Foo {}",
            "new Bar(42);"));
  }

  @Test
  public void testClassDeclarationInheritedConstructorParametersMismatch() {
    testTypes(
        lines(
            "class Foo {",
            "  constructor(/** number */ arg) {}",
            "}",
            "class Bar extends Foo {}",
            "new Bar('xyz');"),
        lines(
            "actual parameter 1 of Bar does not match formal parameter",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testClassDeclarationWithSemicolonsBetweenMembers() {
    testTypes(
        lines(
            "class Foo {",
            "  constructor() {};",
            "  foo() {};",
            "  bar() {};",
            "}"));
  }

  @Test
  public void testClassPassedAsParameter() {
    testTypes(
        lines(
            "class Foo {}",
            "function foo(/** function(new: Foo) */ arg) {}",
            "foo(class extends Foo {});"));
  }

  @Test
  public void testClassPassedAsParameterClassMismatch() {
    testTypes(
        lines(
            "class Foo {}",
            "function foo(/** function(new: Foo) */ arg) {}",
            "foo(class {});"),
        lines(
            "actual parameter 1 of foo does not match formal parameter",
            "found   : function(new:<anonymous@[testcode]:3>): undefined",
            "required: function(new:Foo): ?"));
  }

  @Test
  public void testClassPassedAsParameterConstructorParamsMismatch() {
    testTypes(
        lines(
            "class Foo {",
            "  constructor(/** string */ arg) {}",
            "}",
            "function foo(/** function(new: Foo, number) */ arg) {}",
            "foo(Foo);"),
        lines(
            "actual parameter 1 of foo does not match formal parameter",
            "found   : function(new:Foo, string): undefined",
            "required: function(new:Foo, number): ?"));
  }

  @Test
  public void testClassExpression() {
    testTypes(
        lines(
            "var Foo = class Bar {}", //
            "var /** !Foo */ foo = new Foo();"));
  }

  @Test
  public void testClassExpressionDoesNotDefineTypeNameInOuterScope() {
    testTypes(
        lines(
            "var Foo = class Bar {}", //
            "var /** !Bar */ foo = new Foo();"),
        "Bad type annotation. Unknown type Bar");
  }

  @Test
  public void testClassExpressionDoesNotDefineConstructorReferenceInOuterScope() {
    // Test that Bar is not defined in the outer scope, which makes it unknown (the error is
    // generated by VarCheck, which is not run here).  If it were defined in the outer scope then
    // we'd get a type error assigning it to null.
    testTypes(
        lines(
            "var Foo = class Bar {}", //
            "var /** null */ foo = new Bar();"));
  }

  @Test
  public void testClassExpressionAsStaticClassProeprty() {
    testTypes(
        lines(
            "class Foo {}", //
            "Foo.Bar = class extends Foo {}",
            "var /** !Foo */ foo = new Foo.Bar();"));
  }

  @Test
  public void testClassSyntaxClassExtendsInterface() {
    testTypes(
        lines(
            "/** @interface */", //
            "class Bar {}",
            "class Foo extends Bar {}"),
        "Foo cannot extend this type; constructors can only extend constructors");
  }

  @Test
  public void testClassSyntaxClassExtendsNonClass() {
    testTypes(
        "class Foo extends 42 {}",
        "Foo cannot extend this type; constructors can only extend constructors");
  }

  @Test
  public void testClassSyntaxInterfaceExtendsClass() {
    testTypes(
        lines(
            "class Bar {}", //
            "/** @interface */",
            "class Foo extends Bar {}"),
        "Foo cannot extend this type; interfaces can only extend interfaces");
  }

  @Test
  public void testClassSyntaxInterfaceExtendsInterface() {
    testTypes(
        lines(
            "/** @interface */", //
            "class Bar {}",
            "/** @interface */",
            "class Foo extends Bar {}",
            "var /** !Foo */ foo;",
            "var /** !Bar */ bar = foo;"));
  }

  @Test
  public void testClassSyntaxInterfaceExtendsInterfaceMismatch() {
    testTypes(
        lines(
            "/** @interface */", //
            "class Bar {}",
            "/** @interface */",
            "class Foo extends Bar {}",
            "var /** !Bar */ bar;",
            "var /** !Foo */ foo = bar;"),
        lines(
            "initializing variable", //
            "found   : Bar",
            "required: Foo"));
  }

  @Test
  public void testClassSyntaxRecord() {
    testTypes(
        lines(
            "/** @record */", //
            "class Rec {",
            "  constructor() { /** @type {string} */ this.bar; }",
            "  foo(/** number */ arg) {}",
            "}",
            "var /** !Rec */ rec = {bar: 'x', foo() {}};"));
  }

  @Test
  public void testClassSyntaxRecordWithMethodMismatch() {
    testTypes(
        lines(
            "/** @record */", //
            "class Rec {",
            "  foo(/** number */ arg) {}",
            "}",
            "var /** !Rec */ rec = {foo(/** string */ arg) {}};"),
        lines(
            "initializing variable",
            "found   : {foo: function(string): undefined}",
            "required: Rec",
            "missing : []",
            "mismatch: [foo]"));
  }

  @Test
  public void testClassSyntaxRecordWithPropertyMismatch() {
    testTypes(
        lines(
            "/** @record */", //
            "class Rec {",
            "  constructor() {",
            "    /** @type {number} */",
            "    this.foo;",
            "  }",
            "}",
            "var /** !Rec */ rec = {foo: 'string'};"),
        lines(
            "initializing variable",
            "found   : {foo: string}",
            "required: Rec",
            "missing : []",
            "mismatch: [foo]"));
  }

  @Test
  public void testClassJSDocExtendsInconsistentWithExtendsClause() {
    testTypes(
        lines(
            "class Bar {}", //
            "class Baz {}",
            "/** @extends {Bar} */",
            "class Foo extends Baz {}"),
        lines(
            "mismatch in declaration of superclass type",
            "found   : Baz",
            "required: Bar"));
  }

  @Test
  public void testClassJSDocExtendsWithMissingExtendsClause() {
    // TODO(sdh): Should be an error, but we may need to clean up the codebase first.
    testTypes(
        lines(
            "class Bar {}", //
            "/** @extends {Bar} */",
            "class Foo {}"));
  }

  @Test
  public void testInterfaceHasBothExtendsClauseAndExtendsJSDoc() {
    // TODO(b/114472257): ban this syntax because it results in strange behavior in class-side
    // inheritance - the inferface only inherits properties from one of the extended interfaces.
    // We may also ban using the extends keyword at all for extending interfaces, since extending
    // an interface should not result in actually sharing code.
    testTypes(
        lines(
            "/** @interface */",
            "class Bar {}",
            "/** @interface */",
            "class Baz {}",
            "/**",
            " * @interface",
            " * @extends {Bar}",
            " */",
            "class Foo extends Baz {}"));
  }

  @Test
  public void testClassExtendsGetElem() {
    testTypes(
        lines(
            "class Foo {}",
            "/** @const {!Object<string, function(new:Foo)>} */",
            "var obj = {};",
            "class Bar extends obj['abc'] {}",
            "var /** !Foo */ foo = new Bar();"),
        new String[] {
          "The right-hand side of an extends clause must be a qualified name, or else @extends must"
              + " be specified in JSDoc",
          // TODO(sdh): This is a little confusing, but there doesn't seem to be a way to suppress
          // this additional error.
          lines(
              "initializing variable",
              "found   : Bar",
              "required: Foo"),
        });
  }

  @Test
  public void testClassExtendsFunctionCall() {
    testTypes(
        lines(
            "class Foo {}",
            "/** @return {function(new:Foo)} */",
            "function mixin() {}",
            "class Bar extends mixin() {}",
            "var /** !Foo */ foo = new Bar();"),
        new String[] {
          "The right-hand side of an extends clause must be a qualified name, or else @extends must"
              + " be specified in JSDoc",
          // TODO(sdh): This is a little confusing, but there doesn't seem to be a way to suppress
          // this additional error.
          lines(
              "initializing variable",
              "found   : Bar",
              "required: Foo"),
        });
  }

  @Test
  public void testClassInterfaceExtendsFunctionCall() {
    testTypes(
        lines(
            "/** @interface */",
            "class Foo {}",
            "/** @return {function(new:Foo)} */",
            "function mixin() {}",
            "/** @interface */",
            "class Bar extends mixin() {}"),
        "The right-hand side of an extends clause must be a qualified name, or else @extends must"
            + " be specified in JSDoc");
  }

  @Test
  public void testClassExtendsFunctionCallWithJSDoc() {
    testTypes(
        lines(
            "class Foo {",
            "  constructor() { /** @type {number} */ this.foo; }",
            "}",
            "/** @return {function(new:Foo)} */",
            "function mixin() {}",
            "/** @extends {Foo} */",
            "class Bar extends mixin() {}",
            "var /** null */ x = new Bar().foo;"),
        lines(
            "initializing variable", //
            "found   : number",
            "required: null"));
  }

  @Test
  public void testClassExtendsFunctionCallWithIncompatibleJSDoc() {
    testTypes(
        lines(
            "class Foo {}",
            "class Baz {}",
            "/** @return {function(new:Foo)} */",
            "function mixin() {}",
            "/** @extends {Baz} */",
            "class Bar extends mixin() {}"),
        lines(
            "mismatch in declaration of superclass type", //
            "found   : Foo",
            "required: Baz"));
  }

  @Test
  public void testClassImplementsInterface() {
    testTypes(
        lines(
            "/** @interface */",
            "class Foo { foo() {} }",
            "/** @implements {Foo} */",
            "class Bar {",
            "  /** @override */",
            "  foo() {}",
            "}"));
  }

  @Test
  public void testClassMissingInterfaceMethod() {
    testTypes(
        lines(
            "/** @interface */",
            "class Foo { foo() {} }",
            "/** @implements {Foo} */",
            "class Bar {}"),
        "property foo on interface Foo is not implemented by type Bar");
  }

  @Test
  public void testClassAbstractClassNeedNotExplicitlyOverrideUnimplementedInterfaceMethods() {
    testTypes(
        lines(
            "/** @interface */",
            "class Foo { foo() {} }",
            "/** @abstract @implements {Foo} */",
            "class Bar {}"),
        // TODO(sdh): allow this without error, provided we can get the error on the concrete class
        "property foo on interface Foo is not implemented by type Bar");
  }

  @Test
  public void testClassMissingOverrideAnnotationForInterfaceMethod() {
    testTypes(
        lines(
            "/** @interface */",
            "class Foo { foo() {} }",
            "/** @implements {Foo} */",
            "class Bar {",
            "  foo() {}",
            "}"),
        "property foo already defined on interface Foo; use @override to override it");
  }

  @Test
  public void testClassIncompatibleInterfaceMethodImplementation() {
    testTypes(
        lines(
            "/** @interface */",
            "class Foo {",
            "  /** @return {number} */ foo() {}",
            "}",
            "/** @implements {Foo} */",
            "class Bar {",
            "  /** @override @return {number|string} */",
            "  foo() {}",
            "}"),
        lines(
            "mismatch of the foo property on type Bar and the type of the property it overrides "
                + "from interface Foo",
            "original: function(this:Foo): number",
            "override: function(this:Bar): (number|string)"));
  }

  @Test
  public void testClassMissingTransitiveInterfaceMethod() {
    testTypes(
        lines(
            "/** @interface */",
            "class Foo { foo() {} }",
            "/** @interface @extends {Foo} */",
            "class Bar {}",
            "/** @implements {Bar} */",
            "class Baz {}"),
        "property foo on interface Foo is not implemented by type Baz");
  }

  @Test
  public void testClassInheritedInterfaceMethod() {
    testTypes(
        lines(
            "/** @interface */",
            "class Foo { foo() {} bar() {} }",
            "/** @abstract */",
            "class Bar { foo() {} }",
            "/** @implements {Foo} */",
            "class Baz extends Bar { /** @override */ bar() {} }"));
  }

  @Test
  public void testClassMixinAllowsNonOverriddenInterfaceMethods() {
    // See cl/188076790 and b/74120976
    testTypes(
        lines(
            "/** @interface */",
            "class Foo {",
            "  /** @return {number} */ foo() {}",
            "}",
            "class Bar {}",
            // TODO(sdh): Intersection types would allow annotating this correctly.
            "/** @return {function(new:Bar)} */",
            "function mixin() {}",
            "/** @extends {Bar} @implements {Foo} */",
            "class Baz extends mixin() {}"),
        // TODO(sdh): This is supposed to be allowed.
        "property foo on interface Foo is not implemented by type Baz");
  }

  @Test
  public void testClassDeclarationWithExtendsOnlyInJSDoc() {
    // TODO(sdh): Should be an error, but we may need to clean up the codebase first.
    testTypes(
        lines(
            "class Foo {}", //
            "/** @extends {Foo} */",
            "class Bar {}",
            "var /** !Foo */ foo = new Bar();"));
  }

  @Test
  public void testClassConstructorTypeParametersNotIncludedOnClass() {
    // TODO(sdh): This should give a warning about too many type parameters.
    testTypes(
        lines(
            "/** @template T */",
            "class Foo {",
            "  /** @template U */",
            "  constructor() {}",
            "}",
            "var /** !Foo<string, string> */ x = new Foo();",
            "var /** !Foo<string, number> */ y = x;"));
  }

  @Test
  public void testClassConstructorTypeParametersNotVisibleFromOtherMethods() {
    testTypes(
        lines(
            "class Foo {",
            "  /** @template T */",
            "  constructor() {}",
            "  foo() {",
            "    var /** T */ x;",
            "  }",
            "}"),
        "Bad type annotation. Unknown type T");
  }

  @Test
  public void testClassTtlNotAllowedOnClass() {
    testTypes(
        "/** @template T := 'number' =: */ class Foo {}",
        "Template type transformation T not allowed on classes or interfaces");
  }

  @Test
  public void testClassTtlAllowedOnConstructor() {
    // TODO(sdh): Induce a mismatch by assigning T to null, once typevars aren't treated as unknown
    testTypes(
        lines(
            "class Foo {",
            "  /**",
            "   * @param {T} arg",
            "   * @template T := 'number' =:",
            "   */",
            "  constructor(arg) {",
            "    var /** T */ x = arg;",
            "  }",
            "}"));
  }

  @Test
  public void testClassTtlAllowedOnMethod() {
    testTypes(
        lines(
            "class Foo {",
            "  /** @template T := 'number' =: */",
            "  foo(/** T */ arg) {",
            "    var /** T */ x = arg;",
            "  }",
            "}",
            "new Foo().foo('x')"),
        lines(
            "actual parameter 1 of Foo.prototype.foo does not match formal parameter",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testClassConstructorTypeParametersChecked() {
    testTypes(
        lines(
            "/** @template T */",
            "class Foo {",
            "  /** @template U */",
            "  constructor(/** U */ arg1, /** function(U): T */ arg2) {}",
            "}",
            "/** @param {string} arg",
            "    @return {number} */",
            "function f(arg) {}",
            "var /** !Foo<number> */ foo = new Foo('x', f);"));
  }

  @Test
  public void testClassConstructorTypeParametersWithClassTypeMismatch() {
    testTypes(
        lines(
            "/** @template T */",
            "class Foo {",
            "  /** @template U */",
            "  constructor(/** U */ arg1, /** function(U): T */ arg2) {}",
            "}",
            "/** @param {string} arg",
            "    @return {number} */",
            "function f(arg) {}",
            "var /** !Foo<string> */ foo = new Foo('x', f);"),
        lines(
            "initializing variable", //
            "found   : Foo<number>",
            "required: Foo<string>"));
  }

  @Test
  public void testClassConstructorTypeParametersWithParameterTypeMismatch() {
    testTypes(
        lines(
            "/** @template T */",
            "class Foo {",
            "  /** @template U */",
            "  constructor(/** U */ arg1, /** function(U): T */ arg2) {}",
            "}",
            "/** @param {string} arg",
            "    @return {number} */",
            "function f(arg) {}",
            "var foo = new Foo(42, f);"),
        lines(
            "actual parameter 2 of Foo does not match formal parameter",
            "found   : function(string): number",
            "required: function((number|string)): number"));
  }

  @Test
  public void testClassSideInheritanceFillsInParameterTypesWhenCheckingBody() {
    testTypes(
        lines(
            "class Foo {",
            "  static foo(/** string */ arg) {}",
            "}",
            "class Bar extends Foo {",
            // TODO(sdh): Should need @override here.
            "  static foo(arg) {",
            "    var /** null */ x = arg;",
            "  }",
            "}"),
        lines(
            "initializing variable", //
            "found   : string",
            "required: null"));
  }

  @Test
  public void testClassMethodParameters() {
    testTypes(
        lines(
            "class C {",
            "  /** @param {number} arg */",
            "  m(arg) {}",
            "}",
            "new C().m('x');"),
        lines(
            "actual parameter 1 of C.prototype.m does not match formal parameter",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testClassInheritedMethodParameters() {
    testTypes(
        lines(
            "var B = class {",
            "  /** @param {boolean} arg */",
            "  method(arg) {}",
            "};",
            "var C = class extends B {};",
            "new C().method(1);"),
        lines(
            "actual parameter 1 of B.prototype.method does not match formal parameter",
            "found   : number",
            "required: boolean"));
  }

  @Test
  public void testClassMethodReturns() {
    testTypes(
        lines(
            "var D = class {",
            "  /** @return {number} */",
            "  m() {}",
            "}",
            "var /** null */ x = new D().m();"),
        lines(
            "initializing variable", //
            "found   : number",
            "required: null"));
  }

  @Test
  public void testClassInheritedMethodReturns() {
    testTypes(
        lines(
            "class Q {",
            "  /** @return {string} */",
            "  method() {}",
            "};",
            "var P = class extends Q {};",
            "var /** null */ x = new P().method();"),
        lines(
            "initializing variable", //
            "found   : string",
            "required: null"));
  }

  @Test
  public void testClassStaticMethodParameters() {
    testTypes(
        lines(
            "class C {",
            "  /** @param {number} arg */",
            "  static m(arg) {}",
            "}",
            "C.m('x');"),
        lines(
            "actual parameter 1 of C.m does not match formal parameter",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testClassInheritedStaticMethodParameters() {
    testTypes(
        lines(
            "var B = class {",
            "  /** @param {boolean} arg */",
            "  static method(arg) {}",
            "};",
            "var C = class extends B {};",
            "C.method(1);"),
        lines(
            "actual parameter 1 of C.method does not match formal parameter",
            "found   : number",
            "required: boolean"));
  }

  @Test
  public void testClassStaticMethodReturns() {
    testTypes(
        lines(
            "var D = class {",
            "  /** @return {number} */",
            "  static m() {}",
            "};",
            "var /** null */ x = D.m();"),
        lines(
            "initializing variable", //
            "found   : number",
            "required: null"));
  }

  @Test
  public void testClassInheritedStaticMethodReturns() {
    testTypes(
        lines(
            "class Q {",
            "  /** @return {string} */",
            "  static method() {}",
            "}",
            "class P extends Q {}",
            "var /** null */ x = P.method();"),
        lines(
            "initializing variable", //
            "found   : string",
            "required: null"));
  }

  @Test
  public void testClassStaticMethodCalledOnInstance() {
    testTypes(
        lines(
            "class C {",
            "  static m() {}",
            "}",
            "new C().m();"),
        // TODO(b/111229815): Fix to "Property m never defined on instances of C".
        "Property m never defined on C");
  }

  @Test
  public void testClassInstanceMethodCalledOnClass() {
    testTypes(
        lines(
            "class C {",
            "  m() {}",
            "}",
            "C.m();"),
        // TODO(b/111229815): Fix to "Property m never defined on namespace C".
        "Property m never defined on C");
  }

  @Test
  public void testClassInstanceMethodOverriddenWithMissingOverrideAnnotation() {
    testTypes(
        lines(
            "class Base {",
            "  /** @param {string|number} arg */",
            "  method(arg) {}",
            "}",
            "class Sub extends Base {",
            "  method(arg) {}",
            "}"),
        "property method already defined on superclass Base; use @override to override it");
  }

  @Test
  public void testClassInstanceMethodOverriddenWithWidenedType() {
    testTypes(
        lines(
            "class Base {",
            "  /** @param {string} arg */",
            "  method(arg) {}",
            "}",
            "class Sub extends Base {",
            "  /** @override @param {string|number} arg */",
            "  method(arg) {}",
            "}"));
  }

  @Test
  public void testClassInstanceMethodOverriddenWithIncompatibleType() {
    testTypes(
        lines(
            "class Base {",
            "  /** @param {string|number} arg */",
            "  method(arg) {}",
            "}",
            "class Sub extends Base {",
            "  /** @override @param {string} arg */",
            "  method(arg) {}",
            "}"),
        lines(
            "mismatch of the method property type and the type of the property it overrides "
                + "from superclass Base",
            "original: function(this:Base, (number|string)): undefined",
            "override: function(this:Sub, string): undefined"));
  }

  @Test
  public void testClassInstanceMethodOverriddenWithIncompatibleType2() {
    testTypes(
        lines(
            "class Base {",
            "  /** @return {string} */",
            "  method() {}",
            "}",
            "class Sub extends Base {",
            "  /** @override @return {string|number} */",
            "  method() {}",
            "}"),
        lines(
            "mismatch of the method property type and the type of the property it overrides "
                + "from superclass Base",
            "original: function(this:Base): string",
            "override: function(this:Sub): (number|string)"));
  }

  @Test
  public void testClassStaticMethodOverriddenWithWidenedType() {
    testTypes(
        lines(
            "class Base {",
            "  /** @param {string} arg */",
            "  static method(arg) {}",
            "}",
            "class Sub extends Base {",
            // TODO(sdh): should need @override (new warning: wait until later)
            "  /** @param {string|number} arg */",
            "  static method(arg) {}",
            "}"));
  }

  @Test
  public void testClassStaticMethodOverriddenWithIncompatibleType() {
    testTypes(
        lines(
            "class Base {",
            "  /** @param {string|number} arg */",
            "  static method(arg) {}",
            "}",
            "class Sub extends Base {",
            "  /** @override @param {string} arg */",
            "  static method(arg) {}",
            "}"));
        // TODO(sdh): This should actually check the override (new warning: wait until later).
        // lines(
        //     "mismatch of the method property type and the type of the property it overrides "
        //         + "from superclass Base",
        //     "original: function((number|string)): undefined",
        //     "override: function(string): undefined"));
  }

  @Test
  public void testClassStaticMethodOverriddenWithIncompatibleInlineType() {
    testTypes(
        lines(
            "class Base {",
            "  static method(/** string|number */ arg) {}",
            "}",
            "class Sub extends Base {",
            "  /** @override */",
            "  static method(/** string */ arg) {}",
            "}"));
        // TODO(sdh): This should actually check the override.
        // lines(
        //     "mismatch of the method property type and the type of the property it overrides "
        //         + "from superclass Base",
        //     "original: function((number|string)): undefined",
        //     "override: function(string): undefined"));
  }

  @Test
  public void testClassTreatedAsStruct() {
    testTypes(
        lines(
            "class Foo {}", //
            "var foo = new Foo();",
            "foo.x = 42;"),
        "Cannot add a property to a struct instance after it is constructed."
              + " (If you already declared the property, make sure to give it a type.)");
  }

  @Test
  public void testClassTreatedAsStructSymbolAccess() {
    testTypesWithCommonExterns(
        lines(
            "class Foo {}", //
            "var foo = new Foo();",
            "foo[Symbol.iterator] = 42;"));
  }

  @Test
  public void testClassAnnotatedWithUnrestricted() {
    disableStrictMissingPropertyChecks();
    testTypes(
        lines(
            "/** @unrestricted */ class Foo {}", //
            "var foo = new Foo();",
            "foo.x = 42;"));
  }

  @Test
  public void testClassAnnotatedWithDictDotAccess() {
    disableStrictMissingPropertyChecks();
    testTypes(
        lines(
            "/** @dict */ class Foo {}",
            "var foo = new Foo();",
            "foo.x = 42;"),
        "Cannot do '.' access on a dict");
  }

  @Test
  public void testClassAnnotatedWithDictComputedAccess() {
    testTypes(
        lines(
            "/** @dict */ class Foo {}",
            "var foo = new Foo();",
            "foo['x'] = 42;"));
  }

  @Test
  public void testClassSuperInConstructor() {
    testTypes(
        lines(
            "class Foo {",
            "  constructor(/** number */ arg) {}",
            "}",
            "class Bar extends Foo {",
            "  constructor(/** string */ arg) { super(1); }",
            "}",
            "var /** !Foo */ foo = new Bar('x');"));
  }

  @Test
  public void testClassSuperConstructorParameterMismatch() {
    testTypes(
        lines(
            "class Foo {",
            "  constructor(/** number */ arg) {}",
            "}",
            "class Bar extends Foo {",
            "  constructor() {",
            "    super('x');",
            "  }",
            "}"),
        lines(
            "actual parameter 1 of super does not match formal parameter",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testClassSuperConstructorParameterCountMismatch() {
    testTypes(
        lines(
            "class Foo {}",
            "class Bar extends Foo {",
            "  constructor() {",
            "    super(1);",
            "  }",
            "}"),
        "Function super: called with 1 argument(s). Function requires at least 0 argument(s) "
            + "and no more than 0 argument(s).");
  }

  @Test
  public void testClassSuperMethodNotPresent() {
    testTypes(
        lines(
            "class Foo {}",
            "class Bar extends Foo {",
            "  foo() {",
            "    super.foo();",
            "  }",
            "}"),
        "Property foo never defined on Foo");
  }

  @Test
  public void testClassSuperMethodParameterMismatch() {
    testTypes(
        lines(
            "class Foo {",
            "  /** @param {string} arg */",
            "  foo(arg) {}",
            "}",
            "class Bar extends Foo {",
            "  /** @override */",
            "  foo() {",
            "    super.foo(42);",
            "  }",
            "}"),
        lines(
            "actual parameter 1 of Foo.prototype.foo does not match formal parameter",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testClassSuperMethodCalledFromArrow() {
    testTypes(
        lines(
            "class Foo {",
            "  /** @param {string} arg */",
            "  foo(arg) {}",
            "}",
            "class Bar extends Foo {",
            "  /** @override */",
            "  foo() {",
            "    () => super.foo(42);",
            "  }",
            "}"),
        lines(
            "actual parameter 1 of Foo.prototype.foo does not match formal parameter",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testClassSuperMethodReturnType() {
    testTypes(
        lines(
            "class Foo {",
            "  /** @return {string} */",
            "  foo() {}",
            "}",
            "class Bar extends Foo {",
            "  /** @override */",
            "  foo() {",
            "    var /** null */ x = super.foo();",
            "  }",
            "}"),
        lines(
            "initializing variable",
            "found   : string",
            "required: null"));
  }

  @Test
  public void testClassSuperMethodFromDifferentMethod() {
    testTypes(
        lines(
            "class Foo {",
            "  /** @return {string} */",
            "  foo() {}",
            "}",
            "class Bar extends Foo {",
            "  bar() {",
            "    var /** null */ x = super.foo();",
            "  }",
            "}"),
        lines(
            "initializing variable",
            "found   : string",
            "required: null"));
  }

  @Test
  public void testClassSuperMethodNotWidenedWhenOverrideWidens() {
    testTypes(
        lines(
            "class Foo {",
            "  /** @param {string} arg */",
            "  foo(arg) {}",
            "}",
            "class Bar extends Foo {",
            "  /** @override @param {string|number} arg */",
            "  foo(arg) {}",
            "  bar() {",
            "    super.foo(42);",
            "  }",
            "}"),
        lines(
            "actual parameter 1 of Foo.prototype.foo does not match formal parameter",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testClassStaticSuperParameterMismatch() {
    testTypes(
        lines(
            "class Foo {",
            "  static foo(/** number */ arg) {}",
            "}",
            "class Bar extends Foo {",
            "  /** @override */",
            "  static foo() {",
            "    super.foo('x');",
            "  }",
            "}"),
        lines(
            // TODO(b/111229815): "Foo.foo" instead of "super.foo"
            "actual parameter 1 of super.foo does not match formal parameter",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testClassStaticSuperCalledFromArrow() {
    testTypes(
        lines(
            "class Foo {",
            "  static foo(/** number */ arg) {}",
            "}",
            "class Bar extends Foo {",
            "  /** @override */",
            "  static foo() {",
            "    () => super.foo('x');",
            "  }",
            "}"),
        lines(
            // TODO(b/111229815): "Foo.foo" instead of "super.foo"
            "actual parameter 1 of super.foo does not match formal parameter",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testClassStaticSuperParameterCountMismatch() {
    testTypes(
        lines(
            "class Foo {",
            "  static foo() {}",
            "}",
            "class Bar extends Foo {",
            "  /** @override */",
            "  static foo() {",
            "    super.foo(1);",
            "  }",
            "}"),
        // TODO(b/111229815): "Foo.foo" instead of "super.foo"
        "Function super.foo: called with 1 argument(s). "
            + "Function requires at least 0 argument(s) and no more than 0 argument(s).");
  }

  @Test
  public void testClassStaticSuperNotPresent() {
    testTypes(
        lines(
            "class Foo {}",
            "class Bar extends Foo {",
            "  static foo() {",
            "    super.foo;",
            "  }",
            "}"),
        // TODO(b/111229815): "Property foo never defined on namespace Foo"
        "Property foo never defined on super");
  }

  @Test
  public void testClassStaticSuperCallsDifferentMethod() {
    testTypes(
        lines(
            "class Foo {",
            "  /** @param {string} arg */",
            "  static foo(arg) {}",
            "}",
            "class Bar extends Foo {",
            "  /** @override */",
            "  static foo(/** string|number */ arg) {}",
            "  static bar() { super.foo(42); }",
            "}"),
        lines(
            // TODO(b/111229815): "Foo.foo" instead of "super.foo"
            "actual parameter 1 of super.foo does not match formal parameter",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testClassTypeOfThisInConstructor() {
    testTypes(
        lines(
            "class Foo {",
            "  constructor() {",
            "    var /** null */ foo = this;",
            "  }",
            "}"),
        lines(
            "initializing variable",
            "found   : Foo",
            "required: null"));
  }

  @Test
  public void testClassTypeOfThisInMethod() {
    testTypes(
        lines(
            "class Foo {",
            "  foo() {",
            "    var /** null */ foo = this;",
            "  }",
            "}"),
        lines(
            "initializing variable",
            "found   : Foo",
            "required: null"));
  }

  @Test
  public void testClassTypeOfThisInStaticMethod() {
    testTypes(
        lines(
            "class Foo {",
            "  static foo() {",
            "    var /** null */ foo = this;",
            "  }",
            "}"));
        // TODO(sdh): Should be an error, but wait on it since it's a new warning.
        // lines(
        //     "initializing variable",
        //     "found   : function(new:Foo): undefined",
        //     "required: null"));
  }

  @Test
  public void testClassGetter() {
    testTypes(
        lines(
            "class C {",
            "  get x() {}",
            "}",
            "var /** null */ y = new C().x;"));
  }

  @Test
  public void testClassGetterMismatch() {
    testTypes(
        lines(
            "class C {",
            "  /** @return {number} */",
            "  get x() {}",
            "}",
            "var /** null */ y = new C().x;"),
        lines(
            "initializing variable",
            "found   : number",
            "required: null"));
  }

  @Test
  public void testClassStaticGetter() {
    testTypes(
        lines(
            "class C {",
            "  static get x() {}",
            "}",
            "var /** null */ y = C.x;"));
  }

  @Test
  public void testClassStaticGetterMismatch() {
    testTypes(
        lines(
            "class C {",
            "  /** @return {number} */",
            "  static get x() {}",
            "}",
            "var /** null */ y = C.x;"),
        lines(
            "initializing variable",
            "found   : number",
            "required: null"));
  }

  @Test
  public void testClassSetter() {
    testTypes(
        lines(
            "class C {",
            "  set x(arg) {}",
            "}",
            "new C().x = null;"));
  }

  @Test
  public void testClassSetterMismatch() {
    testTypes(
        lines(
            "class C {",
            "  /** @param {number} arg */",
            "  set x(arg) {}",
            "}",
            "new C().x = null;"),
        lines(
            "assignment to property x of C",
            "found   : null",
            "required: number"));
  }

  @Test
  public void testClassSetterWithMissingParameter() {
    testTypes("class C { set a(b) {} }");
  }

  @Test
  public void testClassStaticSetter() {
    testTypes(
        lines(
            "class C {",
            "  static set x(arg) {}",
            "}",
            "C.x = null;"));
  }

  @Test
  public void testClassStaticSetterMismatch() {
    testTypes(
        lines(
            "class C {",
            "  /** @param {number} arg */",
            "  static set x(arg) {}",
            "}",
            "C.x = null;"),
        lines(
            "assignment to property x of C",
            "found   : null",
            "required: number"));
  }

  @Test
  public void testClassGetterAndSetter() {
    testTypes(
        lines(
            "class C {",
            "  /** @return {number} */",
            "  get x() {}",
            "  /** @param {number} arg */",
            "  set x(arg) {}",
            "}",
            "var /** number */ y = new C().x;",
            "new C().x = 42;"));
  }

  @Test
  public void testClassGetterAndSetterNoJsDoc() {
    testTypes(
        lines(
            "class C {",
            "  get x() {}",
            "  set x(arg) {}",
            "}",
            "var /** number */ y = new C().x;",
            "new C().x = 42;"));
  }

  @Test
  public void testClassGetterAndSetterDifferentTypes() {
    testTypes(
        lines(
            "class C {",
            "  /** @return {number} */",
            "  get x() {}",
            "  /** @param {string} arg */",
            "  set x(arg) {}",
            "}",
            "var /** null */ y = new C().x;",
            "new C().x = null;"),
        new String[] {
          lines(
              // TODO(sdh): Having different getter and setter types should be allowed and not
              // produce the following error.
              "The types of the getter and setter for property 'x' do not match.",
              "getter type is: number",
              "setter type is: string"),
          lines(
              "initializing variable", //
              "found   : number",
              "required: null"),
          lines(
              "assignment to property x of C",
              "found   : null",
              // TODO(sdh): This should report that it requires a string.
              "required: number")
        });
  }

  @Test
  public void testClassGetterAndSetterWithSameStructuralTypeIsAllowed() {
    // Regression test for a case where we were warning for CONFLICTING_GETTER_SETTER_TYPE when the
    // getter and setter used record types.
    // This was fixed by always using structural equality when checking equality for RecordTypes
    testTypes(
        lines(
            "class C {",
            "  /** @return {{x: number}} */",
            "  get x() { return {x: 0}; }",
            "  /** @param {{x: number}} arg */",
            "  set x(arg) {}",
            "}",
            "const c = new C();",
            "c.x = {x: 3};",
            "const /** {x: number} */ something = c.x;"));
  }

  @Test
  public void testClassGetterAndSetterWithSameStructuralRecordAndNominalTypeIsAllowed() {
    // NOTE: we would actually `like` to allow this, but right now only actual RecordTypes are
    // compared structurally when checking equality.
    testTypes(
        lines(
            "/** @record */",
            "function xRecord() {}",
            "/** @type {number} */",
            "xRecord.prototype.x;",
            "",
            "class C {",
            "  /** @return {!xRecord} */",
            "  get x() { return {x: 0}; }",
            "  /** @param {{x: number}} arg */",
            "  set x(arg) {}",
            "}",
            "const c = new C();",
            "c.x = {x: 3};",
            "const /** {x: number} */ something = c.x;"),
        lines(
            "The types of the getter and setter for property 'x' do not match.",
            "getter type is: xRecord",
            "setter type is: {x: number}"));
  }

  @Test
  public void testClassNewTargetInArrowFunction() {
    // TODO(sdh): This should be an error.
    testTypes("const f = () => { const /** null */ x = new.target; };");
  }

  @Test
  public void testClassNewTargetInMethod() {
    testTypes(
        "class Foo { foo() { const /** null */ x = new.target; } }",
        lines(
            "initializing variable",
            "found   : undefined",
            "required: null"));
  }

  @Test
  public void testClassNewTargetInVanillaFunction() {
    testTypes(
        "function f() { const /** null */ x = new.target; }",
        lines(
            "initializing variable",
            "found   : (Function|undefined)",
            "required: null"));
  }

  @Test
  public void testClassNewTargetInVanillaFunctionNestedArrow() {
    testTypes(
        "function f() { const f = () => { const /** null */ x = new.target; }; }",
        lines(
            "initializing variable",
            "found   : (Function|undefined)",
            "required: null"));
  }

  @Test
  public void testClassNewTargetInConstructor() {
    testTypes(
        "class Foo { constructor() { const /** null */ x = new.target; } };",
        lines(
            "initializing variable",
            "found   : Function",
            "required: null"));
  }

  @Test
  public void testClassNewTargetInConstructorNestedArrow() {
    testTypes(
        "class Foo { constructor() { const f = () => { const /** null */ x = new.target; }; } };",
        lines(
            "initializing variable",
            "found   : Function",
            "required: null"));
  }

  @Test
  public void testClassEs5ClassCannotExtendEs6Class() {
    testTypes(
        lines(
            "class Base {}",
            "/** @constructor @extends {Base} */",
            "function Sub() {}"),
        "ES5 class Sub cannot extend ES6 class Base");
  }

  @Test
  public void testClassEs5ClassCanImplementEs6Interface() {
    testTypes(
        lines(
            "/** @interface */",
            "class Inter {}",
            "/** @constructor @implements {Inter} */",
            "function Sub() {}"));
  }

  @Test
  public void testClassExtendsForwardReferencedClass() {
    testTypes(
        lines(
            "/** @const */ var ns = {};",
            "(function() {",
            "  ns.Base = class {};",
            "})();",
            "class Sub extends ns.Base {}",
            "var /** !ns.Base */ x = new Sub();"));
  }

  @Test
  public void testClassExtendsItself() {
    testTypes(
        "class Foo extends Foo {}",
        new String[] {
          "Parse error. Cycle detected in inheritance chain of type Foo",
        });
  }

  @Test
  public void testClassExtendsCycle() {
    testTypes(
        lines(
            "class Foo extends Bar {}",
            "class Bar extends Foo {}"),
        "Parse error. Cycle detected in inheritance chain of type Bar");
  }

  @Test
  public void testClassExtendsCycleOnlyInJsdoc() {
    testTypes(
        lines(
            "class Bar {}",
            "/** @extends {Foo} */",
            "class Foo extends Bar {}"),
        new String[] {
          "Parse error. Cycle detected in inheritance chain of type Foo",
          "Could not resolve type in @extends tag of Foo",
        });
  }

  @Test
  public void testClassExtendsCycleOnlyInAst() {
    // TODO(sdh): This should give an error.
    testTypes(
        lines(
            "class Bar {}",
            "/** @extends {Bar} */",
            "class Foo extends Foo {}"));
  }

  @Test
  public void testMixinFunction() {
    testTypes(
        lines(
            "/** @param {function(new: ?, ...?)} ctor */",
            "function mixin(ctor) {",
            // ctor isn't properly declared as a type,
            // but we shouldn't generate an error,
            // because it is a real value, not an annotation,
            // and we need this coding pattern to work.
            "  class Foo extends ctor {}",
            "}"));
  }

  @Test
  public void testClassImplementsForwardReferencedInterface() {
    testTypes(
        lines(
            "/** @const */ var ns = {};",
            "(function() {",
            "  /** @interface */",
            "  ns.Base = class {};",
            "})();",
            "/** @implements {ns.Base} */",
            "class Sub {}",
            "var /** !ns.Base */ x = new Sub();"));
  }

  @Test
  public void testClassSuperCallResult() {
    testTypes(
        lines(
            "class Bar {}",
            "class Foo extends Bar {",
            "  constructor() {",
            "    var /** null */ x = super();",
            "  }",
            "}"),
        // TODO(sdh): This should probably infer Foo, rather than Bar?
        lines(
            "initializing variable", //
            "found   : Bar",
            "required: null"));
  }

  @Test
  public void testClassComputedSymbolPropAllowed() {
    testTypesWithExterns(
        new TestExternsBuilder().addIterable().build(), //
        "class Foo { [Symbol.iterator]() {} }");
  }

  @Test
  public void testClassExtendsNonNativeObject() {
    // This is a weird thing to do but should not crash the compiler.
    testTypes(
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
            "attempted re-definition of type Object",
            "found   : function(new:Object): undefined",
            "expected: function(new:Object, *=): Object"));
  }

  @Test
  public void testAsyncFunctionWithoutJSDoc() {
    testTypes("async function f() { return 3; }");
  }

  @Test
  public void testAsyncFunctionInferredToReturnPromise_noExplicitReturns() {
    testTypes(
        "async function f() {} var /** null */ n = f();",
        lines(
            "initializing variable", // preserve newline
            "found   : Promise<undefined>",
            "required: null"));
  }

  @Test
  public void testAsyncFunctionInferredToReturnPromise_withExplicitReturns() {
    testTypes(
        "async function f() { return 3; } var /** null */ n = f();",
        lines(
            "initializing variable", // preserve newline
            "found   : Promise<?>",
            "required: null"));
  }

  @Test
  public void testAsyncFunction_cannotDeclareReturnToBe_Number() {
    testTypesWithCommonExterns(
        "/** @return {number} */ async function f() {}",
        lines(
            "The return type of an async function must be a supertype of Promise",
            "found: number"));
  }

  @Test
  public void testAsyncFunction_cannotDeclareReturnToBe_Array() {
    testTypesWithCommonExterns(
        "/** @return {!Array} */ async function f() {}",
        lines(
            "The return type of an async function must be a supertype of Promise", "found: Array"));
  }

  @Test
  public void testAsyncFunction_canDeclareReturnToBe_Object_andAccepts_undefined() {
    testTypesWithCommonExterns("/** @return {!Object} */ async function f() { return undefined; }");
  }

  @Test
  public void testAsyncFunction_canDeclareReturnToBe_allType_andAccepts_undefined() {
    testTypesWithCommonExterns("/** @return {*} */ async function f() { return undefined; }");
  }

  @Test
  public void testAsyncReturnsPromise1() {
    testTypesWithCommonExterns(
        lines(
            "/** @return {!Promise<number>} */",
            "async function getANumber() {",
            "  return 1;",
            "}"));
  }

  @Test
  public void testAsyncReturnsPromise2() {
    testTypesWithCommonExterns(
        lines(
            "/** @return {!Promise<string>} */",
            "async function getAString() {",
            "  return 1;",
            "}"),
        lines(
            "inconsistent return type", // preserve newline
            "found   : number",
            "required: (IThenable<string>|string)"));
  }

  @Test
  public void testAsyncFunction_canDeclareReturnToBe_nullablePromise() {
    testTypesWithCommonExterns(
        lines(
            "/** @return {?Promise<string>} */",
            "async function getAString() {",
            "  return '';",
            "}"));
  }

  @Test
  public void testAsyncFunction_canDeclareReturnToBe_unionOfPromiseAndNumber() {
    testTypesWithCommonExterns(
        lines(
            "/** @return {(number|!Promise<number>)} */",
            "async function getAString() {",
            "  return 1;",
            "}"));
  }

  @Test
  public void testAsyncFunction_cannotDeclareReturnToBe_aSubtypeOfPromise() {
    testTypesWithCommonExterns(
        lines(
            "/** @extends {Promise<string>} */",
            "class MyPromise extends Promise { }",
            "",
            "/** @return {!MyPromise} */",
            "async function getAString() {",
            "  return '';",
            "}"),
        lines(
            "The return type of an async function must be a supertype of Promise",
            "found: MyPromise"));
  }

  @Test
  public void testAsyncFunction_cannotDeclareReturnToBe_aSiblingOfPromise() {
    testTypesWithCommonExterns(
        lines(
            "/**",
            " * @interface",
            " * @extends {IThenable<string>}",
            " */",
            "class MyThenable { }",
            "",
            "/** @return {!MyThenable} */",
            "async function getAString() {",
            "  return '';",
            "}"),
        lines(
            "The return type of an async function must be a supertype of Promise",
            "found: MyThenable"));
  }

  @Test
  public void testAsyncFunction_canDeclareReturnToBe_IThenable1() {
    testTypesWithCommonExterns(
        lines(
            "/** @return {!IThenable<string>} */",
            "async function getAString() {",
            "  return 1;",
            "}"),
        lines(
            "inconsistent return type", //
            "found   : number",
            "required: (IThenable<string>|string)"));
  }

  @Test
  public void testAsyncFunction_checksReturnExpressionType_againstCorrectUpperBound() {
    testTypesWithCommonExterns(
        lines(
            "/** @return {string|!IThenable<boolean|undefined>|!Promise<null>} */",
            "async function getAString() {",
            "  return {};",
            "}"),
        lines(
            "inconsistent return type", //
            "found   : {}",
            // We're specifically checking this type.
            "required: (IThenable<(boolean|null|undefined)>|boolean|null|undefined)"));
  }

  @Test
  public void testAsyncReturnStatementIsResolved() {
    // Test that we correctly handle resolving an "IThenable" return statement inside an async
    // function.
    testTypesWithCommonExterns(
        lines(
            "/** @return {!IThenable<string>} */",
            "async function getAString(/** !IThenable<number> */ iThenable) {",
            "  return iThenable;",
            "}"),
        lines(
            "inconsistent return type", // preserve newline
            "found   : IThenable<number>",
            "required: (IThenable<string>|string)"));
  }

  @Test
  public void testAwaitPromiseOfNumber1() {
    testTypes(
        lines(
            "function takesNumber(/** number*/ num) {}",
            "",
            "async function f(/** !Promise<number> */ p) {",
            "  takesNumber(await p);",
            "}"));
  }

  @Test
  public void testAwaitPromiseOfNumber2() {
    testTypes(
        lines(
            "function takesNumber(/** number*/ num) {}",
            "",
            "async function f(/** !Promise<string> */ p) {",
            "  takesNumber(await p);",
            "}"),
        lines(
            "actual parameter 1 of takesNumber does not match formal parameter",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testAwaitPromiseOfPromise() {
    // TODO(lharker): forbid this annotation, since it is impossible for a Promise to resolve to a
    // Promise.
    testTypes(
        lines(
            "function takesNumber(/** number*/ num) {}",
            "",
            "async function f(/** !Promise<!Promise<number>> */ p) {",
            "  takesNumber(await p);",
            "}"));
  }

  @Test
  public void testAwaitPromiseOfUnknown() {
    testTypes(
        lines(
            "function takesNumber(/** number*/ num) {}",
            "",
            "async function f(/** !Promise<?> */ p) {",
            "  takesNumber(await p);",
            "}"));
  }

  @Test
  public void testAwaitIThenable() {
    testTypes(
        lines(
            "function takesNumber(/** number*/ num) {}",
            "",
            "async function f(/** !IThenable<string> */ p) {",
            "  takesNumber(await p);",
            "}"),
        lines(
            "actual parameter 1 of takesNumber does not match formal parameter",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testAwaitNumber() {
    testTypes(
        lines(
            "function takesNumber(/** number*/ num) {}",
            "",
            "async function f(/** string */ str) {",
            "  takesNumber(await str);",
            "}"),
        lines(
            "actual parameter 1 of takesNumber does not match formal parameter",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testAwaitDoesTypeInferenceWithin() {
    testTypes(
        lines(
            "function takesNumber(/** number*/ num) {}",
            "",
            "async function f() {",
            "  var x = 1;",
            "  await (x = 'some string');", // test we recognize that "x" is now a string.
            "  takesNumber(x);",
            "}"),
        lines(
            "actual parameter 1 of takesNumber does not match formal parameter",
            "found   : string",
            "required: number"));
  }

  @Test
  public void testAwaitUnionType1() {
    testTypes(
        lines(
            "function takesNumber(/** number*/ num) {}",
            "",
            "async function f(/** (number|!Promise<number>) */ param) {",
            "  takesNumber(await param);",
            "}"));
  }

  @Test
  public void testAwaitUnionType2() {
    testTypes(
        lines(
            "function takesNumber(/** number*/ num) {}",
            "",
            "async function f(/** (string|!Promise<number>) */ param) {",
            "  takesNumber(await param);",
            "}"),
        lines(
            "actual parameter 1 of takesNumber does not match formal parameter",
            "found   : (number|string)",
            "required: number"));
  }

  @Test
  public void testAwaitUnionType3() {
    testTypes(
        lines(
            "function takesNumber(/** number*/ num) {}",
            "",
            "async function f(/** (number|!Promise<string>) */ param) {",
            "  takesNumber(await param);",
            "}"),
        lines(
            "actual parameter 1 of takesNumber does not match formal parameter",
            "found   : (number|string)",
            "required: number"));
  }

  @Test
  public void testAwaitUnionOfPromiseAndIThenable() {
    testTypes(
        lines(
            "function takesNumber(/** number*/ num) {}",
            "",
            "async function f(/** (!IThenable<number>|!Promise<string>) */ param) {",
            "  takesNumber(await param);",
            "}"),
        lines(
            "actual parameter 1 of takesNumber does not match formal parameter",
            "found   : (number|string)",
            "required: number"));
  }

  @Test
  public void testAwaitNullableIThenable() {
    // We treat "?IThenable" the same as any other union type
    testTypes(
        lines(
            "function takesNumber(/** number */ n) {}",
            "",
            "async function main(/** ?IThenable<number> */ iThenable) {",
            "  takesNumber(await iThenable);",
            "}"),
        lines(
            "actual parameter 1 of takesNumber does not match formal parameter",
            "found   : (null|number)",
            "required: number"));
  }

  @Test
  public void testAwaitThenable() {
    // awaiting something with a .then property that does not implement IThenable results in the
    // unknown type. This matches the behavior of IThenable.then(...)
    // Thus the call to takesNumber below doesn't cause a type error, although at runtime
    // `await thenable` evaluates to `thenable`, since `thenable.then` is not a function.
    testTypes(
        lines(
            "function takesNumber(/** number */ n) {}",
            "",
            "async function f(/** {then: string} */ thenable) {",
            "  takesNumber(await thenable);",
            "}"));
  }

  @Test
  public void testDefaultParameterWithCorrectType() {
    testTypes("function f(/** number= */ n = 3) {}");
  }

  @Test
  public void testDefaultParameterWithWrongType() {
    testTypes(
        "function f(/** number= */ n = 'foo') {}",
        lines(
            "default value has wrong type", //
            "found   : string",
            "required: (number|undefined)"));
  }

  @Test
  public void testDefaultParameterIsUndefined() {
    testTypes("function f(/** number= */ n = undefined) {}");
  }

  @Test
  public void testDefaultParameter_IsVariableTypedAsUndefined() {
    testTypes(
        lines(
            "const alsoUndefined = undefined;",
            "",
            "/** @param {string=} str */", //
            "function f(str = alsoUndefined) {}"));
  }

  @Test
  public void testDefaultParameter_IsKnownNotUndefinedInClosure() {
    // TODO(b/117162687): treat `str` as having declared type `string`, which will remove the
    // spurious warning here.
    testTypes(
        lines(
            "function takesString(/** string */ str) {}",
            "",
            "/** @param {string=} str */",
            "function f(str = '') {",
            "  return () => takesString(str);",
            "}"),
        lines(
            "actual parameter 1 of takesString does not match formal parameter",
            "found   : (string|undefined)",
            "required: string"));
  }

  @Test
  public void testDefaultParameterInDestructuringIsUndefined() {
    testTypes(
        lines(
            "/** @param {{prop: (string|undefined)}} obj */", //
            "function f({prop = undefined}) {}"));
  }

  @Test
  public void testDefaultParameterInDestructuringIsVariableTypedUndefined() {
    // In TypedScopeCreator, when declaring parameters in a scope, we declare any parameter with a
    // default value as not undefined UNLESS it has the literal 'undefined' as a default value.
    // This is because for arbitrary cases in TypedScopeCreator, we do not know what the actual
    // type of the default value is because TypeInference hasn't yet run.
    // Consequently, if the default value is possibly undefined but is not the literal undefined,
    // we will emit a warning saying the default value has the wrong type. See the below test.

    // We are assuming this use case is rare, and that special casing the literal undefined is
    // more readable than special casing anything that may be undefined (especially unknown values).
    // If there turns out to be large demand for this use case we can revisit this decision.
    // See also b/112651122
    testTypes(
        lines(
            "const alsoUndefined = undefined;",
            "",
            "/** @param {{prop: (string|undefined)}} obj */", //
            "function f({prop = alsoUndefined}) {}"));
  }

  @Test
  public void testTypeCheckingOccursWithinDefaultParameter() {
    testTypes(
        "let /** number */ age = 0; function f(x = age = 'foo') {}",
        lines(
            "assignment", //
            "found   : string",
            "required: number"));
  }

  @Test
  public void testDefaultParameterWithTypeInferredFromCallback() {
    testTypes(
        lines(
            "function f(/** function(number=) */ callback) {}",
            "",
            "f((x = 3) => {",
            "  var /** number */ y = x;",
            "})"));
  }

  @Test
  public void testDefaultParameterInIifeWithInferredType() {
    testTypes(
        lines(
            "var /** string|undefined */ stringOrUndefined;",
            "(function f(x = 3) {",
            "  var /** string */ str = x;",
            "})(stringOrUndefined);"),
        lines(
            "initializing variable", //
            "found   : (number|string)",
            "required: string"));
  }

  @Test
  public void testDefaultParameterWithNoJSDocTreatedAsOptional() {
    testTypes("function f(a = 3) {} f();");
  }

  @Test
  public void testOverridingMethodHasDefaultParameterInPlaceOfRequiredParam() {
    testTypes(
        lines(
            "class Parent {",
            "  /** @param {number} num */",
            "  f(num) {}",
            "}",
            "class Child extends Parent {",
            "  /** @override */",
            "  f(num = undefined) {}",
            "}",
            "(new Child()).f();",
            "(new Child()).f(undefined);"));
  }

  @Test
  public void testOverridingMethodAddsOptionalParameterWithDefaultValue() {
    testTypes(
        lines(
            "class Parent {",
            "  /** @param {number} num */",
            "  f(num) {}",
            "}",
            "class Child extends Parent {",
            "  /** @override */",
            "  f(num, otherParam = undefined) {}",
            "}",
            "(new Child()).f(3);",
            "(new Child()).f(3, 'str');",
            "(new Child()).f(3, undefined);"));
  }

  @Test
  public void testOptionalDestructuringParameterWithoutDefaultValue() {
    testTypes("/** @param {{x: number}=} opts */ function f({x}) {} f();");
  }

  @Test
  public void testBasicArrayPatternDeclaration() {
    testTypes(
        lines(
            "function f(/** !Iterable<number> */ numbers) {",
            "  const [/** string */ str] = numbers;",
            "}"),
        lines(
            "initializing variable", //
            "found   : number",
            "required: string"));
  }

  @Test
  public void testNestedDestructuringPatternDeclaration() {
    testTypes(
        lines(
            "function f(/** !Iterable<{x: number}> */ xNumberObjs) {",
            "  const [{/** string */ x}] = xNumberObjs;",
            "}"),
        lines(
            "initializing variable", //
            "found   : number",
            "required: string"));
  }

  @Test
  public void testArrayPatternDeclarationWithElision() {
    testTypes(
        lines(
            "function f(/** !Iterable<number> */ numbers) {",
            "  const [, /** number */ x, , /** string */ y] = numbers;",
            "}"),
        lines(
            "initializing variable", //
            "found   : number",
            "required: string"));
  }

  @Test
  public void testBasicArrayPatternAssign() {
    testTypes(
        lines(
            "function f(/** !Iterable<number> */ numbers) {",
            "  var /** string */ str;",
            "  [str] = numbers;",
            "}"),
        lines(
            "assignment", //
            "found   : number",
            "required: string"));
  }

  @Test
  public void testNestedDestructuringPatternAssign() {
    testTypes(
        lines(
            "function f(/** !Iterable<{x: number}> */ xNumberObjs) {",
            "  var /** string */ x;",
            "  [{x}] = xNumberObjs;",
            "}"),
        lines(
            "assignment", //
            "found   : number",
            "required: string"));
  }

  @Test
  public void testValidArrayPatternInForOfInitializer() {
    testTypes(
        lines(
            "function f(/** !Iterable<!Iterable<number>> */ numberLists) {",
            "  for (const [/** number */ x, /** number */ y] of numberLists) {}",
            "}"));
  }

  @Test
  public void testArrayPatternInForOfInitializerWithTypeMismatch() {
    testTypes(
        lines(
            "function f(/** !Iterable<!Iterable<number>> */ numberLists) {",
            "  for (const [/** number */ x, /** string */ y] of numberLists) {}",
            "}"),
        lines(
            "declared type of for-of loop variable does not match inferred type",
            "found   : number",
            "required: string"));
  }

  @Test
  public void testValidArrayPatternInForInInitializer() {
    testTypes(
        lines(
            "function f(/** !Object<string, number> */ obj) {",
            "  for (const [/** string */ a, /** string */ b] in obj) {}",
            "}"));
  }

  @Test
  public void testArrayPatternInForInInitializerWithTypeMismatch() {
    // TODO(b/77903996): this should cause a type mismatch warning
    testTypes(
        lines(
            "function f(/** !Object<string, number> */ obj) {",
            "  for (const [/** number */ a, /** number */ b] in obj) {}",
            "}"));
  }

  @Test
  public void testBadDefaultValueInCatchCausesWarning() {
    testTypes(
        "try { throw {x: undefined}; } catch ({/** string */ x = 3 + 4}) {}",
        lines(
            "default value has wrong type", //
            "found   : number",
            "required: string"));
  }

  @Test
  public void testCannotAliasEnumThroughDestructuring() {
    testTypesWithExterns(
        new TestExternsBuilder().addArray().build(),
        lines(
            "/** @enum {number} */ const THINGS = {THING1: 1, THING2: 2};",
            // TODO(lharker): warn for putting @enum here
            "/** @enum */ const [OTHERTHINGS] = [THINGS];"));
  }

  @Test
  public void testArrayPatternAssign_badInterfacePropertyCreation() {
    testTypesWithExterns(
        new TestExternsBuilder().addArray().build(),
        "/** @interface */ function Foo() {}; [Foo.prototype.bar] = [];",
        "interface members can only be "
            + "empty property declarations, empty functions, or goog.abstractMethod");
  }

  @Test
  public void testArrayPatternAssign_badPropertyAssignment() {
    testTypes(
        lines(
            "/** @param {!Iterable<number>} numbers */",
            "function f(numbers) {",
            "  const /** {a: string} */ obj = {a: 'foo'};",
            "  [obj.a] = numbers;",
            "}"),
        lines(
            "assignment to property a of obj", //
            "found   : number",
            "required: string"));
  }

  @Test
  public void testBadComputedPropertyKeyInObjectPattern() {
    testTypes(
        "const {[{}]: x} = {};",
        lines(
            "property access", //
            "found   : {}",
            "required: (string|symbol)"));
  }

  @Test
  public void testRestrictedIndexTypeInComputedPropertyKeyInObjectPattern() {
    testTypes(
        lines(
            "const /** !Object<number, number> */ obj = {3: 3, 4: 4};",
            "const {['string']: x} = obj;"),
        lines(
            "restricted index type", //
            "found   : string",
            "required: number"));
  }

  @Test
  public void testObjectDestructuringNullCausesWarning() {
    testTypes(
        "const {} = null;",
        lines(
            "cannot destructure 'null' or 'undefined'", //
            "found   : null",
            "required: Object"));
  }

  @Test
  public void testObjectDestructuringNullableDoesntCauseWarning() {
    // Test that we don't get a "cannot destructure 'null' or 'undefined'" warning, which matches
    // the legacy behavior when typechecking transpiled destructuring patterns.
    testTypes(
        lines(
            "function f(/** ?{x: number} */ nullableObj) {", //
            "const {x} = nullableObj;",
            "}"));
  }

  @Test
  public void testArrayDestructuringNonIterableCausesWarning() {
    testTypes(
        "const [] = 3;",
        lines(
            "array destructuring rhs must be Iterable", //
            "found   : number",
            "required: Iterable"));
  }

  @Test
  public void testBadDefaultValueInArrayPatternCausesWarning() {
    testTypesWithExterns(
        new TestExternsBuilder().addArray().build(),
        "const [/** string */ foo = 0] = [];",
        lines(
            "default value has wrong type", //
            "found   : number",
            "required: string"));
  }

  @Test
  public void testDefaultValueForNestedArrayPatternMustBeIterable() {
    testTypesWithExterns(
        new TestExternsBuilder().addArray().build(),
        "const [[/** string */ x] = 0] = [];",
        lines(
            "array destructuring rhs must be Iterable", //
            "found   : number",
            "required: Iterable"));
  }

  @Test
  public void testArrayDestructuringParameterWithElision() {
    testTypes("/** @param {!Array<number>} numbers */ function f([, x, , y]) {}");
  }

  @Test
  public void testObjectPatternDeclarationWithMissingPropertyWarning() {
    testTypes(
        lines(
            "function f(/** {a: number} */ obj) {", //
            "  const {a, b} = obj;",
            "}"),
        "Property b never defined on obj");
  }

  @Test
  public void testObjectPatternDoesntCheckMissingPropertyForQuotedStringKey() {
    testTypes(
        lines(
            "function f(/** {a: number} */ obj) {", //
            "  const {a, 'b': b} = obj;",
            "}"));
  }

  @Test
  public void testObjectPatternAssignWithMissingPropertyWarning() {
    testTypes(
        lines(
            "function f(/** {a: number} */ obj) {", //
            "  let a, b;",
            "  ({a, b} = obj);",
            "}"),
        "Property b never defined on obj");
  }

  @Test
  public void testObjectPatternDeclarationWithMissingPropertyWarningInForOf() {
    testTypes(
        lines(
            "function f(/** !Iterable<{a: number}> */ aNumberObj) {",
            "  for (const {a, b} of aNumberObj) {}",
            "}"),
        "Property b never defined on {a: number}");
  }

  @Test
  public void testObjectPatternWithMissingPropertyWarningInParameters() {
    testTypes(
        lines(
            "/** @param {{a: number}} obj */", //
            "function f(/** {a: number} */ {b}) {}"),
        "Property b never defined on {a: number}");
  }

  @Test
  public void testArrayPatternAssignWithIllegalPropCreationInStruct() {
    testTypes(
        lines(
            "class C {", //
            "  f(/** !Iterable<number> */ ) {",
            "    [this.x] = arr;",
            "  }",
            "}"),
        new String[] {
          "Cannot add a property to a struct instance after it is constructed. "
              + "(If you already declared the property, make sure to give it a type.)",
          "Property x never defined on C"
        });
  }

  @Test
  public void testEnumAliasedThroughDestructuringPassesTypechecking() {
    testTypes(
        lines(
            "const ns = {};",
            "/** @enum {number} */",
            "ns.myEnum = {FOO: 1, BAR: 2};",
            "",
            "const {myEnum} = ns;",
            "const /** myEnum */ n = myEnum.FOO;"));
  }

  @Test
  public void testEnumAliasedThroughDestructuringReportsCorrectMissingPropWarning() {
    testTypes(
        lines(
            "const ns = {};",
            "/** @enum {number} */",
            "ns.myEnum = {FOO: 1, BAR: 2};",
            "",
            "const {myEnum} = ns;",
            "const missing = myEnum.MISSING;"),
        "element MISSING does not exist on this enum");
  }

  @Test
  public void testAnnotatedObjectLiteralInDefaultParameterInitializer() {
    // Default parameter initializers need to handle defining object literals with annotated
    // function members.
    testTypes(
        lines(
            "/** @param {{g: function(number): undefined}=} x */",
            "function f(x = {/** @param {string} x */ g(x) {}}) {}"),
        lines(
            "default value has wrong type",
            "found   : {g: function(string): undefined}",
            "required: (undefined|{g: function(number): undefined})"));
  }

  @Test
  public void testDictClass1() {
    testTypes("/** @dict */ var C = class { constructor() {} 'x'(){} };");
  }

  @Test
  public void testTypeCheckingOverriddenGetterFromSuperclass() {
    testTypes(
        lines(
            "/** @abstract */",
            "class Bar {",
            "  /**",
            "   * @abstract",
            "   * @return {number} ",
            "   */",
            "  get num() { return 1; }",
            "}",
            "/** @extends {Bar} */",
            "class Baz extends Bar {",
            "  /** @override */",
            "  get num() { return 3; }",
            "}",
            "var /** string */ x = (new Baz).num;"),
        lines(
            "initializing variable", //
            "found   : number",
            "required: string"));
  }

  @Test
  public void testTypeCheckingOverriddenGetterFromSuperclassWithBadReturnType() {
    testTypes(
        lines(
            "/** @abstract */",
            "class Bar {",
            "  /**",
            "   * @abstract",
            "   * @return {number} ",
            "   */",
            "  get num() { return 1; }",
            "}",
            "/** @extends {Bar} */",
            "class Baz extends Bar {",
            "  /** @override */",
            "  get num() { return 'foo'; }",
            "}"),
        lines(
            "inconsistent return type", //
            "found   : string",
            "required: number"));
  }

  @Test
  public void testGetterOverridesPrototypePropertyFromInterface() {
    testTypes(
        lines(
            "/** @interface */",
            "class Bar {}",
            "/** @type {number} */",
            "Bar.prototype.num;",
            "",
            "/** @implements {Bar} */",
            "class Baz {",
            "  /** @override */",
            "  get num() { return 3; }",
            "}",
            "var /** string */ x = (new Baz).num;"),
        lines(
            "initializing variable", //
            "found   : number",
            "required: string"));
  }

  @Test
  public void testGetterOverridesInstancePropertyFromInterface() {
    // We treat the interface fields in the constructor as different from prototype properties,
    // so trying to override the `num` field with a getter doesn't work.
    testTypes(
        lines(
            "/** @interface */",
            "class Bar {",
            "  constructor() {",
            "    /** @type {number} */",
            "    this.num;",
            "  }",
            "}",
            "/** @implements {Bar} */",
            "class Baz {",
            "  /** @override */",
            "  get num() { return 3; }",
            "}",
            "var /** string */ x = (new Baz).num;"),
        "property num not defined on any superclass of Baz");
  }

  @Test
  public void testOverriddenSetterFromSuperclass() {
    testTypes(
        lines(
            "/** @abstract */",
            "class Bar {",
            "  /**",
            "   * @abstract",
            "   * @param {number} x",
            "   */",
            "  set num(x) {}",
            "}",
            "/** @extends {Bar} */",
            "class Baz extends Bar {",
            "  /** @override */",
            "  set num(x) {}",
            "}",
            "(new Baz).num = 'foo';"),
        lines(
            "assignment to property num of Baz", //
            "found   : string",
            "required: number"));
  }

  @Test
  public void testGetterOverridesMethod() {
    // If a getter overrides a method, we infer the getter to be for a function type
    testTypes(
        lines(
            "class Bar {",
            "  /** @return {number} */",
            "  num() { return 1; }",
            "}",
            "/** @extends {Bar} */",
            "class Baz extends Bar {",
            "  /** @override */",
            "  get num() { return 1; }",
            "}"),
        lines(
            "inconsistent return type", //
            "found   : number",
            "required: function(this:Bar): number"));
  }

  @Test
  public void testMisplacedOverrideOnGetter() {
    testTypes(
        lines(
            "/** @abstract */",
            "class Bar {}",
            "/** @extends {Bar} */",
            "class Baz extends Bar {",
            "  /** @override */",
            "  get num() { return 3; }",
            "}",
            "var /** string */ x = (new Baz).num;"),
        "property num not defined on any superclass of Baz");
  }

  @Test
  public void testOverridingNonMethodWithMethodDoesntBlockTypeCheckingInsideMethod() {
    // verify that we still type Bar.prototype.bar with function(this:Bar, number) even though it
    // overrides a property from Foo
    // thus we get both a "mismatch of ... and the property it overrides" warning
    // and a warning for "initializing variable ..." inside bar()
    testTypes(
        lines(
            "class Foo {}",
            "/** @type {number} */",
            "Foo.prototype.bar = 3;",
            "",
            "class Bar extends Foo {",
            "  /** @override */",
            "  bar(/** number */ n) {",
            "    var /** string */ str = n;",
            "  }",
            "}"),
        new String[] {
          lines(
              "mismatch of the bar property type "
                  + "and the type of the property it overrides from superclass Foo",
              "original: number",
              "override: function(this:Bar, number): undefined"),
          lines(
              "initializing variable", //
              "found   : number",
              "required: string")
        });
  }

  @Test
  public void testGetterWithTemplateTypeReturnIsTypeChecked() {
    testTypes(
        lines(
            "/** @interface @template T */",
            "class C {",
            "  /** @return {T} */",
            "  get t() {}",
            "}",
            "/** @implements {C<string>} */",
            "class CString {",
            "  /** @override */",
            "  get t() { return 3; }", // inconsistent return type
            "}"),
        lines(
            "inconsistent return type", //
            "found   : number",
            "required: string"));
  }

  @Test
  public void testStubMethodDeclarationDoesntBlockTypecheckingOfGetter() {
    testTypes(
        lines(
            "/** @interface */",
            "class Foo {}",
            "/** @return {number} */",
            "Foo.prototype.num;",
            "/** @implements {Foo} */",
            "class Bar {",
            "  /** @override */",
            "  get num() { return 1; }",
            "}",
            "var /** string */ x = (new Bar).num;"),
        new String[] {
          lines(
              "inconsistent return type",
              "found   : number",
              "required: function(this:Foo): number"),
          lines(
              "initializing variable", //
              "found   : function(this:Foo): number",
              "required: string")
        });
  }

  @Test
  public void testOverrideSupertypeOnAnonymousClass() {
    // Test that we infer the supertype of a class not assigned to an lvalue
    testTypes(
        lines(
            "function use(ctor) {}",
            "",
            "class Foo { ",
            "  constructor() {",
            "    /** @type {string} */",
            "    this.str;",
            "  }",
            "}",
            "use(class extends Foo {",
            "  f() { this.str = 3; }",
            "});"),
        lines(
            "assignment to property str of <anonymous@[testcode]:9>", //
            "found   : number",
            "required: string"));
  }

  @Test
  public void testOverrideSupertypeOnClassExpression() {
    // Test that we infer the supertype of a class not assigned to an lvalue
    testTypes(
        lines(
            "function use(ctor) {}",
            "",
            "class Foo { ",
            "  constructor() {",
            "    /** @type {string} */",
            "    this.str;",
            "  }",
            "}",
            "use(class Bar extends Foo {",
            "  f() { this.str = 3; }",
            "});"),
        lines(
            "assignment to property str of <anonymous@[testcode]:9>", //
            "found   : number",
            "required: string"));
  }

  @Test
  public void testOverrideInferredOnClassExpression() {
    // Test that we infer the type of overridden methods even on classes not assigned to an lvalue
    testTypes(
        lines(
            "function use(ctor) {}",
            "",
            "class Foo { ",
            "  f(/** number */ num) {}",
            "}",
            "use(class Bar extends Foo {",
            "  /** @override */",
            "  f(num) {",
            "    var /** string */ str = num;",
            "  }",
            "});"),
        lines(
            "initializing variable", //
            "found   : number",
            "required: string"));
  }

  @Test
  public void testMixinWithUnknownTemplatedSupertypeDoesntCauseWarning() {
    // Although in general we warn when we can't resolve the superclass type in an extends clause,
    // we allow this when the superclass type is a template type in order to support mixins.
    testTypes(
        lines(
            "/**",
            " * @template T",
            " * @param {function(new:T)} superClass",
            " */",
            "function mixin(superClass) {",
            "  class Changed extends superClass {}",
            "}"));
  }

  @Test
  public void testMixinImplementingInterfaceAndUnknownTemplatedSuperclass() {
    testTypes(
        lines(
            "/**",
            " * @template T",
            " * @param {function(new:T)} superClass",
            " */",
            "function mixin(superClass) {",
            "  /** @implements {ChangedInterface} */",
            "  class Changed extends superClass {",
            "    /**",
            "     * @override",
            "     * @return {number} ",
            "     */",
            "    method() {",
            "      return 3;",
            "    }",
            "  }",
            "}",
            "",
            "/** @interface */",
            "class ChangedInterface {",
            "  /** @return {number} */",
            "  method() {}",
            "}"));
  }

  @Test
  public void testTypeNameAliasOnAliasedNamespace() {
    testTypes(
        lines(
            "class Foo {}",
            "/** @enum {number} */",
            "Foo.E = {A: 1};",
            "const F = Foo;",
            "const E = F.E;",
            "/** @type {E} */ let e = undefined;"),
        lines(
            "initializing variable", //
            "found   : undefined",
            // TODO(johnlenz): this should not be nullable
            "required: (Foo.E<number>|null)"));
  }

  @Test
  public void testTypedefNameAliasOnAliasedNamespace() {
    testTypes(
        lines(
            "class Foo {}",
            "/** @typedef {number|string} */",
            "Foo.E;",
            "const F = Foo;",
            "const E = F.E;",
            "/** @type {E} */ let e = undefined;"),
        lines(
            "initializing variable", //
            "found   : undefined",
            "required: (number|string)"));
  }

  @Test
  public void testTypeNameAliasOnAliasedClassSideNamespace() {
    testTypes(
        lines(
            "class Foo {}",
            "/** @enum {number} */ Foo.E = {A: 1};",
            "class Bar extends Foo {};",
            "const B = Bar;",
            "const E = B.E;",
            "/** @type {E} */ let e = undefined;"),
        lines(
            "initializing variable", //
            "found   : undefined",
            // TODO(johnlenz): this should not be nullable
            "required: (Foo.E<number>|null)"));
  }

  @Test
  public void testTypedefInExtern() {
    testTypesWithExtraExterns(
        "/** @typedef {boolean} */ var ConstrainBoolean;",
        "var /** ConstrainBoolean */ x = 42;",
        lines(
            "initializing variable", //
            "found   : number",
            "required: boolean"));
  }

  @Test
  public void testDeeplyNestedAliases() {
    testTypes(
        lines(
            "const ns = {};",
            "/** @typedef {number} */",
            "ns.MyNumber;",
            "const alias = {};",
            "/** @const */",
            "alias.child = ns;",
            "const outer = {};",
            "/** @const */",
            "outer.inner = alias;",
            "const /** outer.inner.child.MyNumber */ x = '';"),
        lines(
            "initializing variable",
            "found   : string",
            // TODO(sdh): this should not be nullable
            "required: (null|number)"));
  }
}
