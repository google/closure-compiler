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

import com.google.javascript.jscomp.testing.TestExternsBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link TypeCheck} on non-transpiled code. */
@RunWith(JUnit4.class)
public final class TypeCheckNoTranspileTest extends TypeCheckTestCase {

  @Override
  protected CompilerOptions getDefaultOptions() {
    CompilerOptions options = super.getDefaultOptions();
    options.setWarningLevel(DiagnosticGroups.TOO_MANY_TYPE_PARAMS, CheckLevel.WARNING);
    return options;
  }

  @Test
  public void testCorrectSubtyping_ofRecursiveTemplateType() {
    newTest()
        .addSource(
            "/** @template T */",
            "class Base { }",
            "",
            "/** @extends {Base<!Child>} */",
            "class Child extends Base { }",
            "",
            // Confirm that `Child` is seen as a subtype of `Base<Child>`.
            "const /** !Base<!Child> */ x = new Child();")
        .run();
  }

  @Test
  public void testArrowInferredReturn() {
    // TODO(johnlenz): infer simple functions return results.

    // Verify arrows have do not have an incorrect inferred return.
    newTest()
        .addSource(
            "let fn = () => {", //
            "  return 1;",
            "};",
            "var /** null */ x = fn();")
        .run();
  }

  @Test
  public void testArrowBlocklessInferredReturn() {
    // TODO(johnlenz): infer simple functions return results.

    // Verify arrows have do not have an incorrect inferred return.
    newTest()
        .addSource(
            "let fn = () => 1;", //
            "var /** null */ x = fn();")
        .run();
  }

  @Test
  public void testArrowRightScopeForBody() {
    newTest()
        .addSource(
            "/** @type {string} */ let a = 's';",
            "/** ",
            "  @param {number} a",
            "  @return {null}",
            "*/",
            "let fn = (a) => {",
            "  return a;",
            "}")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : number",
                "required: null"))
        .run();
  }

  @Test
  public void testArrowRightBodyScopeForBlocklessBody() {
    newTest()
        .addSource(
            "/** @type {string} */ let a = 's';",
            "/** ",
            "  @param {number} a",
            "  @return {null}",
            "*/",
            "let fn = (a) => a",
            "")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : number",
                "required: null"))
        .run();
  }

  @Test
  public void testArrowCorrectThis() {
    newTest()
        .addSource(
            "/** @this {String} */ function fn() {",
            "  /** ",
            "    @return {null}",
            "  */",
            "  let fn = () => {",
            "    return this;",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : String",
                "required: null"))
        .run();
  }

  @Test
  public void testArrowBlocklessCorrectThis() {
    newTest()
        .addSource(
            "/** @this {String} */ function fn() {",
            "  /** ",
            "    @return {null}",
            "  */",
            "  let fn = () => this;",
            "}")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : String",
                "required: null"))
        .run();
  }

  @Test
  public void testArrowCorrectArguments() {
    newTest()
        .addSource(
            "/** @this {String} */ function fn() {",
            "  {",
            "    /** @type {number} */ let arguments = 1;",
            "    /** @return {null} */",
            "    let fn = () => {",
            "      return arguments;",
            "    }",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : number",
                "required: null"))
        .run();
  }

  @Test
  public void testArrowBlocklessCorrectArguments() {
    newTest()
        .addSource(
            "/** @this {String} */ function fn() {",
            "  {",
            "    /** @type {number} */ let arguments = 1;",
            "    /** @return {null} */",
            "    let fn = () => arguments;",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : number",
                "required: null"))
        .run();
  }

  @Test
  public void testArrowCorrectInheritsArguments() {
    newTest()
        .addExterns("/** @type {!Arguments} */ var arguments;")
        .addSource(
            "/** @this {String} */ function fn() {",
            "  {",
            "    /** @return {null} */",
            "    let fn = () => {",
            "      return arguments;",
            "    }",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : Arguments",
                "required: null"))
        .run();
  }

  @Test
  public void testArrowBlocklessCorrectInheritsArguments() {
    newTest()
        .addExterns("/** @type {!Arguments} */ var arguments;")
        .addSource(
            "/** @this {String} */ function fn() {",
            "  {",
            "    /** @return {null} */",
            "    let fn = () => arguments;",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : Arguments",
                "required: null"))
        .run();
  }

  @Test
  public void testAsyncArrow_withValidBlocklessReturn_isAllowed() {
    newTest()
        .addSource(
            "function takesPromiseProvider(/** function():!Promise<number> */ getPromise) {}",
            "takesPromiseProvider(async () => 1);")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAsyncArrow_withInvalidBlocklessReturn_isError() {
    newTest()
        .addSource(
            "function takesPromiseProvider(/** function():!Promise<string> */ getPromise) {}",
            "takesPromiseProvider(async () => 1);")
        .addDiagnostic(
            lines(
                "inconsistent return type",
                "found   : number",
                "required: (IThenable<string>|string)"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAsyncArrow_withInferredReturnType_ofValidUnionType_isAllowed() {
    newTest()
        .addSource(
            "/** @param {function():(number|!Promise<string>)} getPromise */",
            "function takesPromiseProvider(getPromise) {}",
            "",
            "takesPromiseProvider(async () => '');")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAsyncArrow_withInferredReturnType_ofInvalidUnionType_isError() {
    newTest()
        .addSource(
            "/** @param {function():(number|!Promise<string>)} getPromise */",
            "function takesPromiseProvider(getPromise) {}",
            "",
            "takesPromiseProvider(async () => true);")
        .addDiagnostic(
            lines(
                "inconsistent return type",
                "found   : boolean",
                "required: (IThenable<string>|string)"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testTypedefOfPropertyInBlock() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addExterns("/** @interface */ function Foo() {}")
        .addSource(
            "/** @constructor */",
            "function Bar(/** !Foo */ foo) {",
            "  /** @type {!Foo} */",
            "  this.foo = foo;",
            "  {",
            "    /** @typedef {boolean} */",
            "    this.foo.bar;",
            "    (() => this.foo.bar)();",
            "  }",
            "}")
        .run();
  }

  @Test
  public void testTypedefOfPropertyInFunctionScope() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addExterns("/** @interface */ function Foo() {}")
        .addSource(
            "/** @constructor */",
            "function Bar(/** !Foo */ foo) {",
            "  /** @type {!Foo} */",
            "  this.foo = foo;",
            "  /** @typedef {boolean} */",
            "  this.foo.bar;",
            "  {",
            "    (() => this.foo.bar)();",
            "  }",
            "}")
        .run();
  }

  @Test
  public void testArrayLitSpread() {
    // TODO(bradfordcsmith): check spread in array literal
    // Note that there's not much point in doing such a check until we check array literal
    // elements in general.
    // See https://github.com/google/closure-compiler/issues/312
    newTest()
        .addSource(
            "/** @type {!Array<string>} */",
            "const strings = [];",
            "/** @type {!Array<number>} */",
            "const numbers = [...strings];", // This should generate an error
            "")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testArrayLitSpreadNonIterable() {
    newTest()
        .addSource(
            "/** @type {!Array<number>} */", //
            "const numbers = [...1];",
            "")
        .addDiagnostic(
            lines(
                "Spread operator only applies to Iterable types",
                "found   : number",
                "required: Iterable"))
        .run();
  }

  @Test
  public void testTypecheckExpressionInArrayLitSpread() {
    newTest()
        .addSource(
            "/** @type {!Array<string>} */",
            "const strings = [];",
            "/** @type {!Array<number>} */",
            "let numbers = [];",
            "const a = [...(numbers = strings)];",
            "")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : Array<string>",
                "required: Array<number>"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testInferTypesFromExpressionInArrayLitSpread() {
    newTest()
        .addSource(
            "/** @type {!Array<string>} */",
            "const strings = [];",
            "let inferred = 1;",
            "const a = [...(inferred = strings)];",
            "/** @type {null} */",
            "const n = inferred;",
            "")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : Array<string>",
                "required: null"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testSpreadAndFollowingParametersNotTypeChecked() {
    newTest()
        .addExterns(
            "/**",
            " * @param {number} num",
            " * @param {string} str",
            " * @param {boolean} bool",
            " */",
            "function use(num, str, bool) {}",
            "")
        .addSource(
            "/** @type {!Array<null>} */ const nulls = [];", //
            "use(1, ...nulls, null, null);")
        .includeDefaultExterns()
        .run();
    // TODO(bradfordcsmith): Should get an error since there's no way for `str` and `bool` params
    // to get the right types here.
  }

  @Test
  public void testSpreadArgumentTypeCheckedForVarArgs() {
    newTest()
        .addExterns(
            "/**",
            " * @param {number} num",
            " * @param {...string} var_args",
            " */",
            "function use(num, var_args) {}",
            "")
        .addSource(
            "/** @type {!Array<null>} */ const nulls = [];", //
            "use(1, ...nulls);")
        .includeDefaultExterns()
        .run();
    // TODO(bradfordcsmith): Should get an error since `nulls` doesn't contain strings.
  }

  @Test
  public void testSpreadArgumentBackInference() {
    newTest()
        .addExterns(
            "/**",
            " * @param {number} num",
            " * @param {...{prop: number}} var_args",
            " */",
            "function use(num, var_args) {}",
            "")
        .addSource("use(1, ...[{}]);")
        .includeDefaultExterns()
        .run();
    // TODO(bradfordcsmith): Should generate error indicating inferred type of `[{}]`
    // as `{Iterable<{prop: (number|undefined)}>}
  }

  @Test
  public void testTooManyNonSpreadParameters() {
    newTest()
        .addExterns(
            "/**",
            " * @param {number} num",
            " * @param {string} str",
            " * @param {boolean} bool",
            " */",
            "function use(num, str, bool) {}",
            "")
        .addSource(
            "/** @type {!Array<*>} */ const unusables = [];",
            "use(1, 'hi', ...unusables, null, null);" // more than 3 non-spread parameters
            )
        .addDiagnostic(
            "Function use: called with at least 4 argument(s)."
                + " Function requires at least 3 argument(s) and no more than 3 argument(s).")
        .run();
  }

  @Test
  public void testArgumentSpreadDoesNotBlockTypeCheckOfInitialParameters() {
    newTest()
        .addExterns(
            "/**",
            " * @param {number} num",
            " * @param {string} str",
            " * @param {boolean} bool",
            " */",
            "function use(num, str, bool) {}",
            "")
        .addSource("use('should be number', ...[]);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of use does not match formal parameter",
                "found   : string",
                "required: number"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testArgumentSpreadNonIterable() {
    newTest()
        .addExterns("function use(x) {}")
        .addSource("use(...1);")
        .addDiagnostic(
            lines(
                "Spread operator only applies to Iterable types",
                "found   : number",
                "required: Iterable"))
        .run();
  }

  @Test
  public void testArgumentSpreadNonIterable_optChainCall() {
    newTest()
        .addExterns("function use(x) {}")
        .addSource("use?.(...1);")
        .addDiagnostic(
            lines(
                "Spread operator only applies to Iterable types",
                "found   : number",
                "required: Iterable"))
        .run();
  }

  @Test
  public void testTypecheckExpressionInArgumentSpread() {
    newTest()
        .addExterns("function use(x) {}")
        .addSource(
            "/** @type {!Array<string>} */",
            "const strings = [];",
            "/** @type {!Array<number>} */",
            "let numbers = [];",
            "use(...(numbers = strings));",
            "")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : Array<string>",
                "required: Array<number>"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testInferTypesFromExpressionInArgumentSpread() {
    newTest()
        .addExterns("function use(x) {}")
        .addSource(
            "/** @type {!Array<string>} */",
            "const strings = [];",
            "let inferred = 1;",
            "use(...(inferred = strings));",
            "/** @type {null} */",
            "const n = inferred;",
            "")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : Array<string>",
                "required: null"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testOnlyRestParameterWithoutJSDocCalledWithNoArgs() {
    newTest()
        .addSource(
            "function use(...numbers) {}",
            "use();", // no args provided in call - should be OK
            "")
        .run();
  }

  @Test
  public void testBadRestJSDoc() {
    newTest()
        .addSource(
            "/** @param {number} numbers */ function f(...numbers) { var /** null */ n = numbers;"
                + " }")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : Array<number>",
                "required: null"))
        .run();
  }

  @Test
  public void testOnlyRestParameterWithoutJSDocCalledWithArgs() {
    newTest()
        .addSource(
            "function use(...numbers) {}", //
            "use(1, 'hi', {});",
            "")
        .run();
  }

  @Test
  public void testOnlyRestParameterWithJSDocCalledWithNoArgs() {
    newTest()
        .addSource(
            "/**",
            " * @param {...number} numbers",
            " */",
            "function use(...numbers) {}",
            "use();", // no args provided in call - should be OK
            "")
        .run();
  }

  @Test
  public void testOnlyRestParameterWithJSDocCalledWithGoodArgs() {
    newTest()
        .addSource(
            "/**",
            " * @param {...number} numbers",
            " */",
            "function use(...numbers) {}",
            "use(1, 2, 3);",
            "")
        .run();
  }

  @Test
  public void testOnlyRestParameterWithJSDocCalledWithBadArg() {
    newTest()
        .addSource(
            "/**",
            " * @param {...number} numbers",
            " */",
            "function use(...numbers) {}",
            "use(1, 'hi', 3);",
            "")
        .addDiagnostic(
            lines(
                "actual parameter 2 of use does not match formal parameter",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testNormalAndRestParameterWithJSDocCalledWithOneArg() {
    newTest()
        .addSource(
            "/**",
            " * @param {string} str",
            " * @param {...number} numbers",
            " */",
            "function use(str, ...numbers) {}",
            "use('hi');", // no rest args provided in call - should be OK
            "")
        .run();
  }

  @Test
  public void testNormalAndRestParameterWithJSDocCalledWithGoodArgs() {
    newTest()
        .addSource(
            "/**",
            " * @param {string} str",
            " * @param {...number} numbers",
            " */",
            "function use(str, ...numbers) {}",
            "use('hi', 2, 3);",
            "")
        .run();
  }

  @Test
  public void testOnlyRestParameterWithJSDocCalledWithBadNormalArg() {
    newTest()
        .addSource(
            "/**",
            " * @param {string} str",
            " * @param {...number} numbers",
            " */",
            "function use(str, ...numbers) {}",
            "use(1, 2, 3);",
            "")
        .addDiagnostic(
            lines(
                "actual parameter 1 of use does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testOnlyRestParameterWithJSDocCalledWithBadRestArg() {
    newTest()
        .addSource(
            "/**",
            " * @param {string} str",
            " * @param {...number} numbers",
            " */",
            "function use(str, ...numbers) {}",
            "use('hi', 'there', 3);",
            "")
        .addDiagnostic(
            lines(
                "actual parameter 2 of use does not match formal parameter",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testRestParameterInCallbackIsInferred() {
    newTest()
        .addSource(
            "/** @param {function(...number)} callback */",
            "function f(callback) {}",
            "",
            "f((...strings) => {",
            "  const /** null */ n = strings;", // verify that this causes a type mismatch
            "});")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : Array<number>",
                "required: null"))
        .run();
  }

  @Test
  public void testExponent1() {
    newTest()
        .addSource(
            "function fn(someUnknown) {",
            "  var x = someUnknown ** 2;", // infer the result
            "  var /** null */ y = x;",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: null"))
        .run();
  }

  @Test
  public void testExponent2() {
    newTest()
        .addSource(
            "function fn(someUnknown) {",
            "  var x = someUnknown;",
            "  x **= 2;", // infer the result
            "  var /** null */ y = x;",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: null"))
        .run();
  }

  @Test
  public void testExponent3() {
    newTest()
        .addSource(
            "function fn(someUnknown) {", //
            "  var y = true ** 3;",
            "}")
        .addDiagnostic(
            lines(
                "left operand", //
                "found   : boolean",
                "required: number"))
        .run();
  }

  @Test
  public void testExponent4() {
    newTest()
        .addSource(
            "function fn(someUnknown) {", //
            "  var y = 1; y **= true;",
            "}")
        .addDiagnostic(
            lines(
                "right operand", //
                "found   : boolean",
                "required: number"))
        .run();
  }

  @Test
  public void testDuplicateCatchVarName() {
    // Make sure that catch variables with the same name are considered to be distinct variables
    // rather than causing a redeclaration error.
    newTest()
        .addSource(
            "try { throw 1; } catch (/** @type {number} */ err) {}",
            "try { throw 'error'; } catch (/** @type {string} */ err) {}",
            "")
        .run();
  }

  @Test
  public void testTypedefFieldInLoopLocal() {
    newTest()
        .addSource(
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
            "")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testTypedefFieldInLoopGlobal() {
    newTest()
        .includeDefaultExterns()
        .addSource(
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
            "")
        .addDiagnostic(
            lines(
                "assignment to property num of x", //
                "found   : (null|number)",
                "required: number"))
        .run();
  }

  @Test
  public void testTypedefAliasValueTypeIsUndefined() {
    // Aliasing a typedef (const Alias = SomeTypedef) should be interchangeable with the original.
    newTest()
        .addSource(
            "/** @typedef {number} */",
            "var MyNumber;",
            "var ns = {};",
            "ns.MyNumber = MyNumber;",
            "/** @type {string} */ (ns.MyNumber);",
            "")
        .run();
  }

  @Test
  public void testTypedefAliasOfLocalTypedef() {
    // Aliasing should work on local typedefs as well as global.
    newTest()
        .addSource(
            "function f() {",
            "  /** @typedef {number} */",
            "  var MyNumber;",
            "  /** @const */",
            "  var Alias = MyNumber;",
            "  /** @type {Alias} */",
            "  var x = 'x';",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testTypedefDestructuredAlias() {
    // Aliasing should work on local typedefs as well as global.
    newTest()
        .addSource(
            "function f() {",
            "  const ns = {};",
            "  /** @typedef {number} */",
            "  ns.MyNumber;",
            "  const {MyNumber: Alias} = ns;",
            "  /** @type {Alias} */",
            "  var x = 'x';",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testTypedefDestructuredAlias_deeplyNested() {
    // Aliasing should work on local typedefs as well as global.
    newTest()
        .addSource(
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
            "}")
        .addDiagnostic( // TODO(sdh): Should parse correctly and give an initializing variable
            // error.
            // It looks like this is a result of the `const` being ignored.
            "Bad type annotation. Unknown type alias.ns.MyNumber")
        .run();
  }

  @Test
  public void testTypedefLocalQualifiedName() {
    // Aliasing should work on local typedefs as well as global.
    newTest()
        .addSource(
            "function f() {",
            "  /** @const */",
            "  var ns = {};",
            "  /** @typedef {number} */",
            "  ns.MyNumber;",
            "  /** @type {ns.MyNumber} */",
            "  var x = 'x';",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testTypedefLocalQualifiedNameAlias() {
    // Aliasing should work on local typedefs as well as global.
    newTest()
        .addSource(
            "function f() {",
            "  /** @typedef {number} */",
            "  var MyNumber;",
            "  /** @const */",
            "  var ns = {};",
            "  /** @const */",
            "  ns.MyNumber = MyNumber;",
            "  /** @type {ns.MyNumber} */",
            "  var x = 'x';",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testTypedefLocalAliasOfGlobalTypedef() {
    // Should also work if the alias is local but the typedef is global.
    newTest()
        .addSource(
            "/** @typedef {number} */",
            "var MyNumber;",
            "function f() {",
            "  /** @const */ var Alias = MyNumber;",
            "  var /** Alias */ x = 'x';",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testTypedefOnAliasedNamespace() {
    // Aliasing a namespace (const alias = ns) should carry over any typedefs on the namespace.
    newTest()
        .addSource(
            "const ns = {};",
            "/** @const */ ns.bar = 'x';",
            "/** @typedef {number} */",
            "ns.MyNumber;",
            "const alias = ns;",
            "/** @const */ alias.foo = 42",
            "/** @type {alias.MyNumber} */ const x = 'str';",
            "")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testTypedefOnLocalAliasedNamespace() {
    // Aliasing a namespace (const alias = ns) should carry over any typedefs on the namespace.
    newTest()
        .addSource(
            "function f() {",
            "  const ns = {};",
            "  /** @typedef {number} */",
            "  ns.MyNumber;",
            "  const alias = ns;",
            "  /** @const */ alias.foo = 42",
            "  /** @type {alias.MyNumber} */ const x = 'str';",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testTypedefOnClassSideInheritedSubtypeInaccessible() {
    // Class-side inheritance should not carry over any types nested on the class.
    newTest()
        .addSource(
            "class Base {}",
            "/** @typedef {number} */",
            "Base.MyNumber;",
            "class Sub extends Base {}",
            "/** @type {Sub.MyNumber} */ let x;",
            "")
        .addDiagnostic("Bad type annotation. Unknown type Sub.MyNumber")
        .run();
  }

  @Test
  public void testGetTypedPercent() {
    // Make sure names declared with `const` and `let` are counted correctly for typed percentage.
    // This was created my a modifying a copy of TypeCheckTest.testGetTypedPercent1()
    String js =
        lines(
            "const id = function(x) { return x; }", //
            "let id2 = function(x) { return id(x); }");
    assertThat(getTypedPercent(js)).isWithin(0.1).of(50.0);
  }

  @Test
  public void testBlockScopedVarInLoop1() {
    disableStrictMissingPropertyChecks();
    newTest()
        .includeDefaultExterns()
        .addSource(
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
            "}")
        .run();
  }

  @Test
  public void testBlockScopedVarInLoop2() {
    newTest()
        .addSource(
            "while (true) {",
            "  let num;",
            "  let /** undefined */ y = num;",
            // null assignment shouldn't make us think num could be null on the previous line.
            "  num = null;",
            "}")
        .run();
  }

  @Test
  public void testBlockScopedVarInLoop3() {
    // Tests that the qualified name alias.num is reset between loop iterations
    newTest()
        .addSource(
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
            "}")
        .run();
  }

  @Test
  public void testGlobalEnumWithLet() {
    newTest()
        .addSource(
            "/** @enum */", // type defaults to {number}
            "let E = {A: 1, B: 2};",
            "",
            "/**",
            " * @param {E} x",
            " * @return {number}",
            " */",
            "function f(x) {return x}")
        .run();
  }

  @Test
  public void testGlobalEnumWithConst() {
    newTest()
        .addSource(
            "/** @enum */", // type defaults to {number}
            "const E = {A: 1, B: 2};",
            "",
            "/**",
            " * @param {E} x",
            " * @return {number}",
            " */",
            "function f(x) {return x}")
        .run();
  }

  @Test
  public void testLocalEnumWithLet() {
    newTest()
        .addSource(
            "{",
            "  /** @enum */", // type defaults to {number}
            "  let E = {A: 1, B: 2};",
            "",
            "  /**",
            "   * @param {E} x",
            "   * @return {number}",
            "   */",
            "  function f(x) {return x}",
            "}")
        .run();
  }

  @Test
  public void testLocalEnumWithConst() {
    newTest()
        .addSource(
            "{",
            "  /** @enum */", // type defaults to {number}
            "  const E = {A: 1, B: 2};",
            "",
            "  /**",
            "   * @param {E} x",
            "   * @return {number}",
            "   */",
            "  function f(x) {return x}",
            "}")
        .run();
  }

  @Test
  public void testGlobalTypedefWithLet() {
    newTest()
        .addSource(
            "/** @typedef {number} */",
            "let Bar;",
            "/** @param {Bar} x */",
            "function f(x) {}",
            "f('3');",
            "")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testLocalTypedefWithLet() {
    newTest()
        .addSource(
            "{",
            "  /** @typedef {number} */",
            "  let Bar;",
            "  /** @param {Bar} x */",
            "  function f(x) {}",
            "  f('3');",
            "}",
            "")
        .addDiagnostic(
            lines(
                "actual parameter 1 of f does not match formal parameter",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testConstWrongType() {
    newTest()
        .addSource("/** @type {number} */ const x = 'hi';")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testLetWrongType() {
    newTest()
        .addSource("/** @type {number} */ let x = 'hi';")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testLetInitializedToUndefined1() {
    newTest()
        .addSource("let foo; let /** number */ bar = foo;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : undefined",
                "required: number"))
        .run();
  }

  @Test
  public void testLetInitializedToUndefined2() {
    // Use the declared type of foo instead of inferring it to be undefined.
    newTest().addSource("let /** number */ foo; let /** number */ bar = foo;").run();
  }

  @Test
  public void testLetInitializedToUndefined3() {
    // TODO(sdh): this should warn because foo is potentially undefined when getFoo() is called.
    // See comment in TypeInference#updateScopeForTypeChange
    newTest()
        .addSource(
            "let foo;",
            "/** @return {number} */",
            "function getFoo() {",
            "  return foo;",
            "}",
            "function setFoo(/** number */ num) {",
            "  foo = num;",
            "}")
        .run();
  }

  @Test
  public void testForOf1() {
    newTest().addSource("/** @type {!Iterable} */ var it; for (var elem of it) {}").run();
  }

  @Test
  public void testForOf2() {
    newTest()
        .addSource(
            "function takesString(/** string */ s) {}",
            "/** @type {!Iterable<string>} */ var it;",
            "for (var elem of it) { takesString(elem); }")
        .run();
  }

  @Test
  public void testForOf3() {
    newTest()
        .addSource(
            "function takesString(/** string */ s) {}",
            "/** @type {!Iterable<number>} */ var it;",
            "for (var elem of it) { takesString(elem); }")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesString does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testForOf4() {
    newTest()
        .addSource("/** @type {!Iterable} */ var it; var obj = {}; for (obj.elem of it) {}")
        .run();
  }

  @Test
  public void testForOf5() {
    // We infer the type of a qualified name in a for-of loop initializer
    newTest()
        .addSource(
            "function takesString(/** string */ s) {}",
            "",
            "function f(/** !Iterable<number> */ it) {",
            "  var obj = {};",
            "  for (obj.elem of it) {",
            "    takesString(obj.elem);",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesString does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testForOf_wrongLoopVarType1() {
    newTest()
        .addSource(
            "/** @type {!Array<number>} */",
            "var numArray = [1, 2];",
            "/** @type {string} */",
            "var elem = '';",
            "for (elem of numArray) {",
            "}")
        .addDiagnostic(
            lines(
                "declared type of for-of loop variable does not match inferred type",
                "found   : number",
                "required: string"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf_wrongLoopVarType2() {
    newTest()
        .addSource(
            "/** @type {!Array<number>} */",
            "var numArray = [1, 2];",
            "for (let /** string */ elem of numArray) {",
            "}")
        .addDiagnostic(
            lines(
                "declared type of for-of loop variable does not match inferred type",
                "found   : number",
                "required: string"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf_wrongLoopVarType3() {
    // If the thing we're trying to iterate over is not actually an Iterable, we treat the inferred
    // type of the for-of loop variable as unknown and only warn for the non-Iterable item.
    newTest()
        .addSource("for (var /** number */ x of 3) {}")
        .addDiagnostic(
            lines(
                "Can only iterate over a (non-null) Iterable type",
                "found   : number",
                "required: Iterable"))
        .run();
  }

  @Test
  public void testForOf_wrongLoopVarType4a() {
    newTest()
        .addSource(
            "/** @type {!Array<!Object>} */",
            "var arr = [1, 2];",
            "for (let /** ?Object */ elem of arr) {",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf_wrongLoopVarType4b() {
    newTest()
        .addSource(
            "/** @type {!Array<?Object>} */",
            "var arr = [1, 2];",
            "for (let /** !Object */ elem of arr) {",
            "}")
        .addDiagnostic(
            lines(
                "declared type of for-of loop variable does not match inferred type",
                "found   : (Object|null)",
                "required: Object"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf_wrongLoopVarType5() {
    // Test that we don't check the inferred type of n against the Iterable type
    newTest()
        .addSource(
            "/** @type {!Array<number>} */",
            "var arr = [1, 2];",
            "let n = null;",
            "for (n of arr) {}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf_wrongLoopVarType6a() {
    // Test that we typecheck the correct variable, given various shadowing variable declarations
    newTest()
        .includeDefaultExterns()
        .addSource(
            "/** @type {!Array<number>} */",
            "var arr = [1, 2, 3];",
            "let /** string */ n = 'foo';", // n in global scope
            "for (let /** number */ n of arr) {", // n in for of scope
            "  let /** null */ n = null;", // n in inner block scope
            "}")
        .run();
  }

  @Test
  public void testForOf_wrongLoopVarType6b() {
    // Test that we typecheck the correct variable, given various shadowing variable declarations
    newTest()
        .includeDefaultExterns()
        .addSource(
            "/** @type {!Array<string>} */",
            "var arr = ['foo', 'bar'];",
            "let /** string */ n = 'foo';", // n in global scope
            "for (let /** number */ n of arr) {", // n in for of scope
            "  let /** null */ n = null;", // n in inner block scope
            "}")
        .addDiagnostic(
            lines(
                "declared type of for-of loop variable does not match inferred type",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testForOf_wrongLoopVarType7() {
    newTest()
        .addSource(
            "/** @type {!Iterable<string>} */ var it;",
            "var /** !Object<string, number> */ obj = {};",
            "for (obj['x'] of it) {}")
        .addDiagnostic(
            lines(
                "declared type of for-of loop variable does not match inferred type",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testForOf_wrongLoopVarType8() {
    newTest()
        .addSource(
            "/** @type {!Iterable<string>} */ var it;",
            "const /** @type {{x: number}} */ obj = {x: 5};",
            "for (obj.x of it) {}")
        .addDiagnostic(
            lines(
                "assignment to property x of obj", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testForOf_illegalPropertyCreation() {
    newTest()
        .addSource(
            "/** @type {!Iterable<string>} */ var it;",
            "const /** @struct */ obj = {};",
            "for (obj.x of it) {}")
        .addDiagnostic(
            "Cannot add a property to a struct instance after it is constructed. "
                + "(If you already declared the property, make sure to give it a type.)")
        .run();
  }

  @Test
  public void testForOf_badInterfaceMemberCreation() {
    newTest()
        .addSource(
            "/** @interface */", //
            "function Foo() {}",
            "for (Foo.prototype.bar of []) {}")
        .addDiagnostic(
            "interface members can only be empty property declarations, "
                + "empty functions, or goog.abstractMethod")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf_badEnumCreation() {
    newTest()
        .addSource("for (var /** @enum */ myEnum of []) {}")
        .addDiagnostic("enum initializer must be an object literal or an enum")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf_array1() {
    newTest().addSource("for (var elem of [1, 2]) {}").includeDefaultExterns().run();
  }

  @Test
  public void testForOf_array2() {
    newTest()
        .addSource(
            "/** @type {!Array<number>} */ var arr = [1, 2];",
            "function takesString(/** string */ s) {}",
            "for (var elem of arr) { takesString(elem); }")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesString does not match formal parameter",
                "found   : number",
                "required: string"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf_array3() {
    newTest()
        .addSource(
            "/** @type {!Array<number>} */ var arr = [1, 2];",
            "function takesNumber(/** number */ n) {}",
            "for (var elem of arr) { takesNumber(elem); }")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf_string1() {
    newTest()
        .addSource(
            "function takesString(/** string */ s) {}",
            "for (var ch of 'a string') { takesString(ch); }")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf_string2() {
    newTest()
        .addSource(
            "function takesNumber(/** number */ n) {}",
            "for (var ch of 'a string') { takesNumber(ch); }")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesNumber does not match formal parameter",
                "found   : string",
                "required: number"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf_StringObject1() {
    newTest()
        .addSource(
            "function takesString(/** string */ s) {}",
            "for (var ch of new String('boxed')) { takesString(elem); }")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf_StringObject2() {
    newTest()
        .addSource(
            "function takesNumber(/** number */ n) {}",
            "for (var ch of new String('boxed')) { takesNumber(elem); }")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf_forbidsAsyncIterable() {
    newTest()
        .addSource(
            "/** @param {!AsyncIterable<string>} asyncIterable */",
            "function f(asyncIterable) {",
            "  for (var elem of asyncIterable) {}",
            "}")
        .includeDefaultExterns()
        .addDiagnostic(
            lines(
                "Can only iterate over a (non-null) Iterable type",
                "found   : AsyncIterable<string>",
                "required: Iterable"))
        .run();
  }

  @Test
  public void testForOf_iterableTypeIsNotFirstTemplateType() {
    newTest()
        .addSource(
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
            "for (var t of mi) { takesNumber(t); }",
            "")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesNumber does not match formal parameter",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testForOf_unionType1() {
    // TODO(b/77904110): Should be a type mismatch warning for passing a string to takesNumber
    newTest()
        .addSource(
            "function takesNumber(/** number */ n) {}",
            "/** @param {(!Array<string>|undefined)} arr */",
            "function f(arr) {",
            "  for (let x of (arr || [])) {",
            "    takesNumber(x);",
            "  }",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testForOf_unionType2() {
    newTest()
        .addSource(
            "/** @param {(number|undefined)} n */",
            "function f(n) {",
            "  for (let x of (n || [])) {}",
            "}")
        .addDiagnostic(
            lines(
                "Can only iterate over a (non-null) Iterable type",
                "found   : (Array<?>|number)",
                "required: Iterable"))
        .run();
  }

  @Test
  public void testForOf_unionType_stringAndArray() {
    newTest()
        .includeDefaultExterns()
        .addSource(
            "function takesNull(/** null */ n) {}",
            "",
            "/** @param {string|!Array<number>} param */",
            "function f(param) {",
            "  for (let x of param) {",
            "    takesNull(x);",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesNull does not match formal parameter",
                "found   : (number|string)",
                "required: null"))
        .run();
  }

  @Test
  public void testForOf_unionType_readonlyArrayAndArray() {
    newTest()
        .includeDefaultExterns()
        .addSource(
            "function takesNull(/** null */ n) {}",
            "",
            "/** @param {!ReadonlyArray<number>|!Array<string>} param */",
            "function f(param) {",
            "  for (let x of param) {",
            "    takesNull(x);",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesNull does not match formal parameter",
                "found   : (number|string)",
                "required: null"))
        .run();
  }

  @Test
  public void testForOf_nullable() {
    newTest()
        .addSource("/** @type {?Iterable} */ var it; for (var elem of it) {}")
        .addDiagnostic(
            lines(
                "Can only iterate over a (non-null) Iterable type",
                "found   : (Iterable|null)",
                "required: Iterable"))
        .run();
  }

  @Test
  public void testForOf_null() {
    newTest()
        .addSource("/** @type {null} */ var it = null; for (var elem of it) {}")
        .addDiagnostic(
            lines(
                "Can only iterate over a (non-null) Iterable type",
                "found   : null",
                "required: Iterable"))
        .run();
  }

  @Test
  public void testForOf_maybeUndefined() {
    newTest()
        .addSource("/** @type {!Iterable|undefined} */ var it; for (var elem of it) {}")
        .addDiagnostic(
            lines(
                "Can only iterate over a (non-null) Iterable type",
                "found   : (Iterable|undefined)",
                "required: Iterable"))
        .run();
  }

  @Test
  public void testForOf_let() {
    // TypeCheck can now handle `let`
    newTest().addSource("/** @type {!Iterable} */ let it; for (let elem of it) {}").run();
  }

  @Test
  public void testForOf_const() {
    // TypeCheck can now handle const
    newTest()
        .addSource("/** @type {!Iterable} */ const it = []; for (const elem of it) {}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testImplicitCastInForOf() {
    newTest()
        .addExterns(
            "/** @constructor */ function Element() {};",
            "/**",
            " * @type {string}",
            " * @implicitCast",
            " */",
            "Element.prototype.innerHTML;")
        .addSource(
            "/** @param {?Element} element",
            " * @param {!Array<string|number>} texts",
            " */",
            "function f(element, texts) {",
            "  for (element.innerHTML of texts) {};",
            "}",
            "")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testGenerator1() {
    newTest().addSource("/** @return {!Generator<?>} */ function* gen() {}").run();
  }

  @Test
  public void testGenerator2() {
    newTest().addSource("/** @return {!Generator<number>} */ function* gen() { yield 1; }").run();
  }

  @Test
  public void testGenerator3() {
    newTest()
        .addSource("/** @return {!Generator<string>} */ function* gen() {  yield 1; }")
        .addDiagnostic(
            lines(
                "Yielded type does not match declared return type.",
                "found   : number",
                "required: string"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testGenerator4() {
    newTest()
        .addSource(
            "/** @return {!Generator} */", // treat Generator as Generator<?>
            "function* gen() {",
            "  yield 1;",
            "}")
        .run();
  }

  @Test
  public void testGenerator5() {
    // Test more complex type inference inside the yield expression
    newTest()
        .addSource(
            "/** @return {!Generator<{a: number, b: string}>} */",
            "function *gen() {",
            "  yield {a: 3, b: '4'};",
            "}",
            "var g = gen();")
        .run();
  }

  @Test
  public void testGenerator6() {
    newTest()
        .includeDefaultExterns()
        .addSource(
            "/** @return {!Generator<string>} */",
            "function* gen() {",
            "}",
            "var g = gen();",
            "var /** number */ n = g.next().value;")
        .addDiagnostic(
            lines(
                "initializing variable", // test that g.next().value typechecks properly
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testGenerator_nextWithParameter() {
    // Note: we infer "var x = yield 1" to have a unknown type. Thus we don't warn "yield x + 2"
    // actually yielding a string, or "k" not being number type.
    newTest()
        .includeDefaultExterns()
        .addSource(
            "/** @return {!Generator<number>} */",
            "function* gen() {",
            "  var x = yield 1;",
            "  yield x + 2;",
            "}",
            "var g = gen();",
            "var /** number */ n = g.next().value;", // 1
            "var /** number */ k = g.next('').value;")
        .run(); // '2'
  }

  @Test
  public void testGenerator_yieldUndefined1() {
    newTest()
        .addSource(
            "/** @return {!Generator<undefined>} */",
            "function* gen() {",
            "  yield undefined;",
            "  yield;", // yield undefined
            "}")
        .run();
  }

  @Test
  public void testGenerator_yieldUndefined2() {
    newTest()
        .includeDefaultExterns()
        .addSource(
            "/** @return {!Generator<number>} */",
            "function* gen() {",
            "  yield;", // yield undefined
            "}")
        .addDiagnostic(
            lines(
                "Yielded type does not match declared return type.",
                "found   : undefined",
                "required: number"))
        .run();
  }

  @Test
  public void testGenerator_returnsIterable1() {
    newTest()
        .addSource("/** @return {!Iterable<?>} */ function *gen() {}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testGenerator_returnsIterable2() {
    newTest()
        .addSource("/** @return {!Iterable<string>} */ function* gen() {  yield 1; }")
        .addDiagnostic(
            lines(
                "Yielded type does not match declared return type.",
                "found   : number",
                "required: string"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testGenerator_returnsIterator1() {
    newTest()
        .addSource("/** @return {!Iterator<?>} */ function *gen() {}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testGenerator_returnsIterator2() {
    newTest()
        .addSource("/** @return {!Iterator<string>} */ function* gen() {  yield 1; }")
        .addDiagnostic(
            lines(
                "Yielded type does not match declared return type.",
                "found   : number",
                "required: string"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testGenerator_returnsIteratorIterable() {
    newTest()
        .addSource("/** @return {!IteratorIterable<?>} */ function *gen() {}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testGenerator_cantReturnArray() {
    newTest()
        .addSource("/** @return {!Array<?>} */ function *gen() {}")
        .addDiagnostic(
            lines(
                "A generator function must return a (supertype of) Generator",
                "found   : Array<?>",
                "required: Generator"))
        .run();
  }

  @Test
  public void testGenerator_notAConstructor() {
    newTest()
        .addSource(
            "/** @return {!Generator<number>} */",
            "function* gen() {",
            "  yield 1;",
            "}",
            "var g = new gen;")
        .addDiagnostic("cannot instantiate non-constructor")
        .run();
  }

  @Test
  public void testGenerator_noDeclaredReturnType1() {
    newTest().addSource("function *gen() {} var /** !Generator<?> */ g = gen();").run();
  }

  @Test
  public void testGenerator_noDeclaredReturnType2() {
    newTest().addSource("function *gen() {} var /** !Generator<number> */ g = gen();").run();
  }

  @Test
  public void testGenerator_noDeclaredReturnType3() {
    // We infer gen() to return !Generator<?>, so don't warn for a type mismatch with string
    newTest()
        .addSource(
            "function *gen() {",
            "  yield 1;",
            "  yield 2;",
            "}",
            "var /** string */ g = gen().next().value;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testGenerator_return1() {
    newTest().addSource("/** @return {!Generator<number>} */ function *gen() { return 1; }").run();
  }

  @Test
  public void testGenerator_return2() {
    newTest()
        .addSource("/** @return {!Generator<string>} */ function *gen() {  return 1; }")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : number",
                "required: string"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testGenerator_return3() {
    // Allow this although returning "undefined" is inconsistent with !Generator<number>.
    // Probably the user is not intending to use the return value.
    newTest().addSource("/** @return {!Generator<number>} */ function *gen() {  return; }").run();
  }

  // test yield*
  @Test
  public void testGenerator_yieldAll1() {
    newTest()
        .addSource(
            "/** @return {!Generator<number>} */", "function *gen() {", "  yield* [1, 2, 3];", "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testGenerator_yieldAll2() {
    newTest()
        .addSource("/** @return {!Generator<number>} */ function *gen() { yield* 1; }")
        .addDiagnostic(
            lines(
                "Expression yield* expects an iterable", "found   : number", "required: Iterable"))
        .run();
  }

  @Test
  public void testGenerator_yieldAll3() {
    newTest()
        .addSource(
            "/** @return {!Generator<number>} */",
            "function *gen1() {",
            "  yield 1;",
            "}",
            "",
            "/** @return {!Generator<number>} */",
            "function *gen2() {",
            "  yield* gen1();",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testGenerator_yieldAll4() {
    newTest()
        .addSource(
            "/** @return {!Generator<string>} */",
            "function *gen1() {",
            "  yield 'a';",
            "}",
            "",
            "/** @return {!Generator<number>} */",
            "function *gen2() {",
            "  yield* gen1();",
            "}")
        .addDiagnostic(
            lines(
                "Yielded type does not match declared return type.",
                "found   : string",
                "required: number"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testGenerator_yieldAll_string() {
    // Test that we autobox a string to a String
    newTest()
        .addSource(
            "/** @return {!Generator<string>} */",
            "function *gen() {",
            "  yield* 'some string';",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testGenerator_yieldAll_null() {
    newTest()
        .addSource(
            "/** @return {!Generator<string>} */", "function *gen() {", "  yield* null;", "}")
        .addDiagnostic(
            lines(
                "Expression yield* expects an iterable", //
                "found   : null",
                "required: Iterable"))
        .run();
  }

  @Test
  public void testMemberFunctionDef1() {
    newTest()
        .addSource(
            "var obj = {", // line break
            "  method (/** number */ n) {}",
            "};",
            "obj.method(1);")
        .run();
  }

  @Test
  public void testMemberFunctionDef2() {
    newTest()
        .addSource(
            "var obj = {", // line break
            "  method (/** string */ n) {}",
            "};",
            "obj.method(1);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of obj.method does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testMemberFunctionDef3() {
    newTest()
        .addSource("var obj = { method() {} }; new obj.method();")
        .addDiagnostic("cannot instantiate non-constructor")
        .run();
  }

  @Test
  public void testMemberFunctionDef_lends() {
    newTest()
        .addExterns(
            "/** @constructor */",
            "function PolymerElement() {}",
            "/** @param {...*} var_args */",
            "PolymerElement.prototype.factoryImpl = function(var_args) {}",
            "var Polymer = function(a) {};")
        .addSource(
            "/** @constructor @extends {PolymerElement} */",
            "var X = function() {};",
            "X = Polymer(/** @lends {X.prototype} */ {",
            "", // Test that we can override PolymerElement.prototype.factoryImpl with a one-arg fn
            "  factoryImpl(e) {",
            "    alert('Thank you for clicking');",
            "  },",
            "});")
        .run();
  }

  @Test
  public void testMemberFunction_enum() {
    newTest()
        .addSource("/** @enum */ var obj = {a() {}};")
        .addDiagnostic(
            lines(
                "assignment to property a of enum{obj}",
                "found   : function(): undefined",
                "required: number"))
        .run();
  }

  @Test
  public void testComputedProp1() {
    newTest().addSource("var i = 1; var obj = { ['var' + i]: i, };").run();
  }

  @Test
  public void testComputedProp2a() {
    // Computed properties do type inference within
    newTest()
        .addSource(
            "var n;", //
            "var obj = {[n = 'foo']: i};",
            "var /** number */ m = n;")
        .addDiagnostic(
            lines(
                "initializing variable", // preserve new line
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testComputedProp2b() {
    // Computed prop type checks within
    newTest()
        .addSource(
            "var /** number */ n = 1;", // preserve new line
            "var obj = {",
            "  [n = 'foo']: i",
            "};")
        .addDiagnostic(
            lines(
                "assignment", // preserve new line
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testComputedProp2c() {
    // Computed properties do type inference within
    newTest()
        .addSource(
            "var n;", //
            "var obj = {[foo]: n = 'bar'};",
            "var /** number */ m = n;")
        .addDiagnostic(
            lines(
                "initializing variable", // preserve new line
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testComputedProp3() {
    // Computed prop does not exist as obj prop
    newTest()
        .addSource(
            "var i = 1;", //
            "var obj = { ['var' + i]: i };",
            "var x = obj.var1")
        .addDiagnostic("Property var1 never defined on obj")
        .run();
  }

  @Test
  public void testComputedProp3b() {
    // Computed prop does not exist as obj prop even when a simple string literal
    newTest()
        .addSource(
            "var obj = { ['static']: 1 };", //
            "var /** number */ x = obj.static")
        .addDiagnostic("Property static never defined on obj")
        .run();
  }

  @Test
  public void testComputedProp4() {
    newTest()
        .addSource(
            "function takesString(/** string */ str) {}",
            "",
            "var obj = {",
            "  /** @param {number} x */",
            "  ['static']: (x) => {",
            "    takesString(x);",
            "  }",
            "};")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesString does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testComputedProp_symbol() {
    newTest().addSource("var sym1 = Symbol('a'); var obj = {[sym1]: 1};").run();
  }

  @Test
  public void testComputedProp_number() {
    newTest().addSource("var obj = {[0]: 1};").run();
  }

  @Test
  public void testComputedProp_badKeyType() {
    newTest()
        .addSource("var foo = {}; var bar = {[foo]: 3};")
        .addDiagnostic(
            lines(
                "property access", //
                "found   : {}",
                "required: (string|symbol)"))
        .run();
  }

  // TODO(b/78013196): Emit a warning for a restricted index type
  @Test
  public void testComputedProp_restrictedIndexType() {
    newTest().addSource("var /** !Object<string, *> */ obj = {[1]: 1};").run();
  }

  // TODO(b/78013196): Emit a warning here for a type mismatch
  // (Note - this also doesn't warn given non-computed properties.)
  @Test
  public void testComputedProp_incorrectValueType1() {
    newTest().addSource("var /** !Object<string, number> */ obj = {['x']: 'not numeric'};").run();
  }

  @Test
  public void testComputedProp_incorrectValueType2() {
    // TODO(lharker): should we be emitting a type mismatch warning here?
    newTest().addSource("var x = { /** @type {string} */ [1]: 12 };").run();
  }

  @Test
  public void testComputedProp_struct1() {
    newTest()
        .addSource("/** @struct */ var obj = {[1 + 2]: 3};")
        .addDiagnostic("Cannot do '[]' access on a struct")
        .run();
  }

  @Test
  public void testComputedProp_struct2() {
    // Allow Symbol properties in a struct
    newTest()
        .addSource("/** @struct */ var obj = {[Symbol.iterator]: function() {}};")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testComputedProp_dict() {
    newTest().addSource("/** @dict */ var obj = {[1]: 2};").run();
  }

  @Test
  public void testComputedProp_enum() {
    newTest()
        .addSource("/** @enum */ var obj = {[1]: 2};")
        .addDiagnostic("enum key must be a string or numeric literal")
        .run();
  }

  @Test
  public void testComputedPropAllowedOnDictClass() {
    newTest()
        .addSource(
            "/** @dict */", "class C {", "  ['f']() {}", "  [123]() {}", "  [123n]() {}", "}")
        .run();
  }

  @Test
  public void testNormalPropNotAllowedOnDictClass() {
    newTest()
        .addSource(
            "/** @dict */", //
            "class C {",
            "  foo() {}",
            "}")
        .addDiagnostic("Illegal key, the class is a dict")
        .run();
  }

  @Test
  public void testComputedPropNotAllowedOnStructClass() {
    newTest()
        .addSource(
            "class C {", // @struct is the default
            "  foo() {}",
            "  ['f']() {}",
            "}")
        .addDiagnostic("Cannot do '[]' access on a struct")
        .run();
  }

  @Test
  public void testQuotedGetterPropNotAllowedOnStructClass() {
    newTest()
        .addSource(
            "class C {", // @struct is the default
            "  foo() {}",
            "  get 'f'() {}",
            "}")
        .addDiagnostic("Illegal key, the class is a struct")
        .run();
  }

  @Test
  public void testTemplateLiteral1() {
    newTest()
        .addSource(
            "", //
            "var a, b",
            "var /** string */ s = `template ${a} string ${b}`;")
        .run();
  }

  @Test
  public void testTemplateLiteral2() {
    // Check that type inference happens inside the TEMPLATE_SUB expression
    newTest()
        .addSource("var n; var s = `${n = 'str'}`; var /** number */ m = n;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testTemplateLiteral3() {
    // Check that we analyze types inside the TEMPLATE_SUB expression
    newTest()
        .addSource("var /** number */ n = 1; var s = `template ${n = 'str'} string`;")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testTemplateLiteral_substitutionsHaveAnyType() {
    // Template strings can take any type.
    newTest()
        .addSource(
            "function f(/** * */ anyTypeParam) {",
            "  var /** string */ s = `template ${anyTypeParam} string`;",
            "}")
        .run();
  }

  @Test
  public void testTemplateLiteral_isStringType() {
    // Check template literal has type string
    newTest()
        .addSource("var /** number */ n = `${1}`;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: number"))
        .run();
  }

  // The first "argument" to a template literal tag function has type !ITemplateArray.
  @Test
  public void testTaggedTemplateLiteral_tagParameters1() {
    // ITemplateArray works as the first parameter
    newTest()
        .addExterns(
            "/**",
            " * @param {!ITemplateArray} template",
            " * @param {...*} var_args Substitution values.",
            " * @return {string}",
            " */",
            "String.raw = function(template, var_args) {};")
        .addSource("String.raw`one ${1} two`")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testTaggedTemplateLiteral_tagParameters2() {
    // !Array<string> works as the first parameter
    newTest()
        .addSource(
            "function tag(/** !Array<string> */ strings){}", //
            "tag`template string`;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testTaggedTemplateLiteral_tagParameters3() {
    // ?Array<string> works as the first parameter
    newTest()
        .addSource(
            "function tag(/** ?Array<string> */ strings){}", //
            "tag`template string`;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testTaggedTemplateLiteral_tagParameters4() {
    // Object works as the first parameter
    newTest()
        .addSource(
            "function tag(/** Object */ strings){}", //
            "tag `template string`;")
        .run();
  }

  @Test
  public void testTaggedTemplateLiteral_tagParameters5() {
    // unknown type works as the first parameter.
    newTest()
        .addSource(
            "function tag(/** ? */ strings){}", //
            "tag `template string`;")
        .run();
  }

  @Test
  public void testTaggedTemplateLiteral_invalidTagParameters1() {
    // Random object does not work as first parameter
    newTest()
        .addSource(
            "function tag(/** {a: number} */ strings){}", //
            "tag `template string`;")
        .addDiagnostic(
            lines(
                "Invalid type for the first parameter of tag function",
                "found   : {a: number}",
                "required: ITemplateArray"))
        .run();
  }

  @Test
  public void testTaggedTemplateLiteral_invalidTagParameters2() {
    // !Array<number> does not work as first parameter
    newTest()
        .addSource(
            "function tag(/** !Array<number> */ strings) {}", //
            "tag`template string`;")
        .addDiagnostic(
            lines(
                "Invalid type for the first parameter of tag function",
                "found   : Array<number>",
                "required: ITemplateArray"))
        .run();
  }

  @Test
  public void testTaggedTemplateLiteral_invalidTagParameters3() {
    // Tag function must have at least one parameter
    newTest()
        .addSource("function tag(){} tag``;")
        .addDiagnostic(
            "Function tag: called with 1 argument(s). "
                + "Function requires at least 0 argument(s) and no more than 0 argument(s).")
        .run();
  }

  @Test
  public void testTaggedTemplateLiteral_tagNotAFunction() {
    newTest()
        .addSource("const tag = 42; tag `template string`;")
        .addDiagnostic("number expressions are not callable")
        .run();
  }

  @Test
  public void testTaggedTemplateLiteral_nullableTagFunction() {
    newTest()
        .addSource(
            "function f(/** ?function(!ITemplateArray) */ tag) {", "  tag `template string`;", "}")
        .run();
  }

  @Test
  public void testTaggedTemplateLiteral_unknownTagFunction() {
    newTest()
        .addSource(
            "function f(/** ? */ tag) {", //
            "  tag `template string`;",
            "}")
        .run();
  }

  @Test
  public void testTaggedTemplateLiteral_tooFewArguments() {
    newTest()
        .addSource("function tag(strings, x, y) {} tag`${1}`;")
        .addDiagnostic(
            "Function tag: called with 2 argument(s). "
                + "Function requires at least 3 argument(s) and no more than 3 argument(s).")
        .run();
  }

  @Test
  public void testTaggedTemplateLiteral_tooManyArguments() {
    newTest()
        .addSource("function tag(strings, x) {} tag`${0} ${1}`;")
        .addDiagnostic(
            "Function tag: called with 3 argument(s). "
                + "Function requires at least 2 argument(s) and no more than 2 argument(s).")
        .run();
  }

  @Test
  public void testTaggedTemplateLiteral_argumentTypeMismatch() {
    newTest()
        .addSource(
            "function tag(strings, /** string */ s) {}", //
            "tag`${123}`;")
        .addDiagnostic(
            lines(
                "actual parameter 2 of tag does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testTaggedTemplateLiteral_argumentWithCast() {
    newTest()
        .addSource(
            "function tag(strings, /** string */ s) {}", //
            "tag`${ /** @type {?} */ (123) }`;")
        .run();
  }

  @Test
  public void testTaggedTemplateLiteral_optionalArguments() {
    newTest()
        .addSource(
            "/** @param {number=} y */ function tag(strings, y){}", //
            "tag``;")
        .run();
  }

  @Test
  public void testTaggedTemplateLiteral_varArgs() {
    newTest()
        .addSource(
            "function tag(strings, /** ...number */ var_args){}", //
            "tag`${1} ${'str'}`;")
        .addDiagnostic(
            lines(
                "actual parameter 3 of tag does not match formal parameter",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testTaggedTemplateLiteral_returnType1() {
    // Infer the TAGGED_TEMPLATELIT to have the return type of the tag function
    newTest()
        .addSource(
            "function takesString(/** string */ s) {}",
            "",
            "/** @return {number} */",
            "function returnsNumber(strings){",
            "  return 1;",
            "}",
            "takesString(returnsNumber`str`);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesString does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testTaggedTemplateLiteral_returnType2() {
    newTest()
        .addSource(
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
            "takesString(getFirstTemplateLitSub`${1}`);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesString does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testTaggedTemplateLiteral_backInference() {
    // Test that we update the type of the template lit sub after back inference
    newTest()
        .addSource(
            "/**",
            "* @param {T} x",
            "* @param {function(this:T, ...?)} z",
            "* @template T",
            "*/",
            "function f(x, z) {}",
            // infers that "this" is ITemplateArray inside the function literal
            "f`${ function() { /** @type {string} */ var x = this } }`;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : ITemplateArray",
                "required: string"))
        .run();
  }

  @Test
  public void testITemplateArray1() {
    // Test that ITemplateArray is Iterable and iterating over it produces a string
    newTest()
        .addSource(
            "function takesNumber(/** number */ n) {}",
            "function f(/** !ITemplateArray */ arr) {",
            "  for (let str of arr) {",
            "    takesNumber(str);",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesNumber does not match formal parameter",
                "found   : string",
                "required: number"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testClassDeclarationWithReturn() {
    newTest()
        .addSource(
            "var /** ?Foo */ cached = null;",
            "class Foo {",
            "  constructor() {",
            "    if (cached) return cached; ",
            "  }",
            "}",
            "")
        .run();
  }

  @Test
  public void testClassErrorsReportedOnClassAndNotConstructor() {
    newTest()
        .addSource(
            "/** @implements {number} */",
            "class Foo {",
            "  constructor() {",
            // Make sure there's an explicit constructor.
            "  }",
            "}")
        // The actual error isn't important. What matters is that it's a class-level error and it's
        // only reported once.
        .addDiagnostic("can only implement interfaces")
        .run();
  }

  @Test
  public void testInvalidInvocationOfClassConstructor() {
    newTest()
        .addSource(
            "class Foo {", //
            "  constructor() {",
            "  }",
            "}",
            "let /** ? */ x = Foo()")
        .addDiagnostic(lines("Constructor (typeof Foo) should be called with the \"new\" keyword"))
        .run();
  }

  @Test
  public void testInvalidInvocationOfClassConstructorWithReturnDeclaration() {
    newTest()
        .addSource(
            "class Foo {",
            "  /** @return {!Array} */",
            "  constructor() {",
            "  }",
            "}",
            "let /** ? */ x = Foo()")
        .addDiagnostic(lines("Constructor (typeof Foo) should be called with the \"new\" keyword"))
        .run();
  }

  @Test
  public void testClassDeclarationWithTemplate() {
    newTest()
        .addSource(
            "/** @template T */",
            "class C {",
            "  /** @param {T} a */",
            "  constructor(a) {",
            "  }",
            "}",
            "/** @type {null} */",
            "const x = new C(0);")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : C<number>",
                "required: null"))
        .run();
  }

  @Test
  public void testClassDeclaration() {
    newTest()
        .addSource(
            "class Foo {}", //
            "var /** !Foo */ foo = new Foo();")
        .run();
  }

  @Test
  public void testClassDeclarationMismatch() {
    newTest()
        .addSource(
            "class Foo {}", //
            "class Bar {}",
            "var /** !Foo */ foo = new Bar();")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : Bar",
                "required: Foo"))
        .run();
  }

  @Test
  public void testClassGenerics() {
    newTest()
        .addSource(
            "/** @template T */",
            "class Foo {}",
            "var /** !Foo<number> */ x = new Foo();",
            "var /** !Foo<string> */ y = x;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : Foo<number>",
                "required: Foo<string>"))
        .run();
  }

  @Test
  public void testClassTooManyTypeParameters() {
    newTest()
        .addSource(
            "class Foo {}", //
            "var /** !Foo<number> */ x = new Foo();",
            "")
        .addDiagnostic(RhinoErrorReporter.TOO_MANY_TEMPLATE_PARAMS)
        .run();
  }

  @Test
  public void testClassWithTemplatizedConstructorTooManyTypeParameters() {
    newTest()
        .addSource(
            "class Foo {",
            "  /** @template T */ constructor() {}",
            "}",
            "var /** !Foo<number> */ x = new Foo();",
            "")
        .addDiagnostic(RhinoErrorReporter.TOO_MANY_TEMPLATE_PARAMS)
        .run();
  }

  @Test
  public void testClassWithTemplatizedClassAndConstructorTooManyTypeParameters() {
    newTest()
        .addSource(
            "/** @template T */",
            "class Foo {",
            "  /** @template U */ constructor() {}",
            "}",
            "var /** !Foo<number, number> */ x = new Foo();",
            "")
        .addDiagnostic(RhinoErrorReporter.TOO_MANY_TEMPLATE_PARAMS)
        .run();
  }

  @Test
  public void testClassDeclarationWithExtends() {
    newTest()
        .addSource(
            "class Foo {}", //
            "class Bar extends Foo {}",
            "var /** !Foo */ foo = new Bar();")
        .run();
  }

  @Test
  public void testClassDeclarationWithExtendsMismatch() {
    newTest()
        .addSource(
            "class Foo {}", //
            "class Bar extends Foo {}",
            "var /** !Bar */ foo = new Foo();")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : Foo",
                "required: Bar"))
        .run();
  }

  @Test
  public void testClassDeclarationWithTransitiveExtends() {
    newTest()
        .addSource(
            "class Foo {}",
            "class Bar extends Foo {}",
            "class Baz extends Bar {}",
            "var /** !Foo */ foo = new Baz();")
        .run();
  }

  @Test
  public void testClassDeclarationWithAnonymousExtends() {
    newTest()
        .addSource(
            "class Foo {}",
            "class Bar extends class extends Foo {} {}",
            "var /** !Foo */ foo = new Bar();")
        .run();
  }

  @Test
  public void testClassDeclarationInlineConstructorParameters() {
    newTest()
        .addSource(
            "class Foo {", //
            "  constructor(/** number */ arg) {}",
            "}",
            "new Foo(42);")
        .run();
  }

  @Test
  public void testClassDeclarationConstructorParametersMismatch() {
    newTest()
        .addSource(
            "class Foo {", //
            "  constructor(/** number */ arg) {}",
            "}",
            "new Foo('xyz');")
        .addDiagnostic(
            lines(
                "actual parameter 1 of Foo does not match formal parameter",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testClassDeclarationTraditionalConstructorParameters() {
    newTest()
        .addSource(
            "class Foo {",
            "  /** @param {number} arg */",
            "  constructor(arg) {}",
            "}",
            "new Foo(42);")
        .run();
  }

  @Test
  public void testClassDeclarationTraditionalConstructorParametersMismatch() {
    newTest()
        .addSource(
            "class Foo {",
            "  /** @param {number} arg */",
            "  constructor(arg) {}",
            "}",
            "new Foo('xyz');")
        .addDiagnostic(
            lines(
                "actual parameter 1 of Foo does not match formal parameter",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testClassDeclarationInheritedConstructorParameters() {
    newTest()
        .addSource(
            "class Foo {",
            "  constructor(/** number */ arg) {}",
            "}",
            "class Bar extends Foo {}",
            "new Bar(42);")
        .run();
  }

  @Test
  public void testClassDeclarationInheritedConstructorParametersMismatch() {
    newTest()
        .addSource(
            "class Foo {",
            "  constructor(/** number */ arg) {}",
            "}",
            "class Bar extends Foo {}",
            "new Bar('xyz');")
        .addDiagnostic(
            lines(
                "actual parameter 1 of Bar does not match formal parameter",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testClassDeclarationWithSemicolonsBetweenMembers() {
    newTest()
        .addSource(
            "class Foo {", //
            "  constructor() {};",
            "  foo() {};",
            "  bar() {};",
            "}")
        .run();
  }

  @Test
  public void testClassPassedAsParameter() {
    newTest()
        .addSource(
            "class Foo {}",
            "function foo(/** function(new: Foo) */ arg) {}",
            "foo(class extends Foo {});")
        .run();
  }

  @Test
  public void testClassPassedAsParameterClassMismatch() {
    newTest()
        .addSource(
            "class Foo {}", "function foo(/** function(new: Foo) */ arg) {}", "foo(class {});")
        .addDiagnostic(
            lines(
                "actual parameter 1 of foo does not match formal parameter",
                "found   : (typeof <anonymous@[testcode]:3>)",
                "required: function(new:Foo): ?"))
        .run();
  }

  @Test
  public void testClassPassedAsParameterConstructorParamsMismatch() {
    newTest()
        .addSource(
            "class Foo {",
            "  constructor(/** string */ arg) {}",
            "}",
            "function foo(/** function(new: Foo, number) */ arg) {}",
            "foo(Foo);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of foo does not match formal parameter",
                "found   : (typeof Foo)",
                "required: function(new:Foo, number): ?"))
        .run();
  }

  @Test
  public void testClassExpression() {
    newTest()
        .addSource(
            "var Foo = class Bar {}", //
            "var /** !Foo */ foo = new Foo();")
        .run();
  }

  @Test
  public void testClassExpressionDoesNotDefineTypeNameInOuterScope() {
    newTest()
        .addSource(
            "var Foo = class Bar {}", //
            "var /** !Bar */ foo = new Foo();")
        .addDiagnostic("Bad type annotation. Unknown type Bar")
        .run();
  }

  @Test
  public void testClassExpressionDoesNotDefineConstructorReferenceInOuterScope() {
    // Test that Bar is not defined in the outer scope, which makes it unknown (the error is
    // generated by VarCheck, which is not run here).  If it were defined in the outer scope then
    // we'd get a type error assigning it to null.
    newTest()
        .addSource(
            "var Foo = class Bar {}", //
            "var /** null */ foo = new Bar();")
        .run();
  }

  @Test
  public void testClassExpressionAsStaticClassProeprty() {
    newTest()
        .addSource(
            "class Foo {}",
            "Foo.Bar = class extends Foo {}",
            "var /** !Foo */ foo = new Foo.Bar();")
        .run();
  }

  @Test
  public void testClassSyntaxClassExtendsInterface() {
    newTest()
        .addSource(
            "/** @interface */", //
            "class Bar {}",
            "class Foo extends Bar {}")
        .addDiagnostic("Foo cannot extend this type; constructors can only extend constructors")
        .run();
  }

  @Test
  public void testClassSyntaxClassExtendsNonClass() {
    newTest()
        .addSource("class Foo extends 42 {}")
        .addDiagnostic("Foo cannot extend this type; constructors can only extend constructors")
        .run();
  }

  @Test
  public void testClassSyntaxInterfaceExtendsClass() {
    newTest()
        .addSource(
            "class Bar {}", //
            "/** @interface */",
            "class Foo extends Bar {}")
        .addDiagnostic("Foo cannot extend this type; interfaces can only extend interfaces")
        .run();
  }

  @Test
  public void testClassSyntaxInterfaceExtendsInterface() {
    newTest()
        .addSource(
            "/** @interface */",
            "class Bar {}",
            "/** @interface */",
            "class Foo extends Bar {}",
            "var /** !Foo */ foo;",
            "var /** !Bar */ bar = foo;")
        .run();
  }

  @Test
  public void testClassSyntaxInterfaceExtendsInterfaceMismatch() {
    newTest()
        .addSource(
            "/** @interface */",
            "class Bar {}",
            "/** @interface */",
            "class Foo extends Bar {}",
            "var /** !Bar */ bar;",
            "var /** !Foo */ foo = bar;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : Bar",
                "required: Foo"))
        .run();
  }

  @Test
  public void testClassSyntaxRecord() {
    newTest()
        .addSource(
            "/** @record */",
            "class Rec {",
            "  constructor() { /** @type {string} */ this.bar; }",
            "  foo(/** number */ arg) {}",
            "}",
            "var /** !Rec */ rec = {bar: 'x', foo() {}};")
        .run();
  }

  @Test
  public void testClassSyntaxRecordWithMethodMismatch() {
    newTest()
        .addSource(
            "/** @record */",
            "class Rec {",
            "  foo(/** number */ arg) {}",
            "}",
            "var /** !Rec */ rec = {foo(/** string */ arg) {}};")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : {foo: function(string): undefined}",
                "required: Rec",
                "missing : []",
                "mismatch: [foo]"))
        .run();
  }

  @Test
  public void testClassSyntaxRecordWithPropertyMismatch() {
    newTest()
        .addSource(
            "/** @record */",
            "class Rec {",
            "  constructor() {",
            "    /** @type {number} */",
            "    this.foo;",
            "  }",
            "}",
            "var /** !Rec */ rec = {foo: 'string'};")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : {foo: string}",
                "required: Rec",
                "missing : []",
                "mismatch: [foo]"))
        .run();
  }

  @Test
  public void testClassJSDocExtendsInconsistentWithExtendsClause() {
    newTest()
        .addSource(
            "class Bar {}", "class Baz {}", "/** @extends {Bar} */", "class Foo extends Baz {}")
        .addDiagnostic(
            lines(
                "mismatch in declaration of superclass type", //
                "found   : Baz",
                "required: Bar"))
        .run();
  }

  @Test
  public void testClassJSDocExtendsWithMissingExtendsClause() {
    // TODO(sdh): Should be an error, but we may need to clean up the codebase first.
    newTest()
        .addSource(
            "class Bar {}", //
            "/** @extends {Bar} */",
            "class Foo {}")
        .run();
  }

  @Test
  public void testInterfaceHasBothExtendsClauseAndExtendsJSDoc() {
    // TODO(b/114472257): ban this syntax because it results in strange behavior in class-side
    // inheritance - the inferface only inherits properties from one of the extended interfaces.
    // We may also ban using the extends keyword at all for extending interfaces, since extending
    // an interface should not result in actually sharing code.
    newTest()
        .addSource(
            "/** @interface */",
            "class Bar {}",
            "/** @interface */",
            "class Baz {}",
            "/**",
            " * @interface",
            " * @extends {Bar}",
            " */",
            "class Foo extends Baz {}")
        .run();
  }

  @Test
  public void testClassExtendsGetElem() {
    newTest()
        .addSource(
            "class Foo {}",
            "/** @const {!Object<string, function(new:Foo)>} */",
            "var obj = {};",
            "class Bar extends obj['abc'] {}",
            "var /** !Foo */ foo = new Bar();")
        .addDiagnostic(
            "The right-hand side of an extends clause must be a qualified name, or else @extends"
                + " must be specified in JSDoc")
        .addDiagnostic(
            // TODO(sdh): This is a little confusing, but there doesn't seem to be a way to suppress
            // this additional error.
            lines(
                "initializing variable", //
                "found   : Bar",
                "required: Foo"))
        .run();
  }

  @Test
  public void testClassExtendsFunctionCall() {
    newTest()
        .addSource(
            "class Foo {}",
            "/** @return {function(new:Foo)} */",
            "function mixin() {}",
            "class Bar extends mixin() {}",
            "var /** !Foo */ foo = new Bar();")
        .addDiagnostic(
            "The right-hand side of an extends clause must be a qualified name, or else @extends"
                + " must be specified in JSDoc")
        .addDiagnostic(
            // TODO(sdh): This is a little confusing, but there doesn't seem to be a way to suppress
            // this additional error.
            lines(
                "initializing variable", //
                "found   : Bar",
                "required: Foo"))
        .run();
  }

  @Test
  public void testClassInterfaceExtendsFunctionCall() {
    newTest()
        .addSource(
            "/** @interface */",
            "class Foo {}",
            "/** @return {function(new:Foo)} */",
            "function mixin() {}",
            "/** @interface */",
            "class Bar extends mixin() {}")
        .addDiagnostic(
            "The right-hand side of an extends clause must be a qualified name, or else @extends"
                + " must be specified in JSDoc")
        .run();
  }

  @Test
  public void testClassExtendsFunctionCallWithJSDoc() {
    newTest()
        .addSource(
            "class Foo {",
            "  constructor() { /** @type {number} */ this.foo; }",
            "}",
            "/** @return {function(new:Foo)} */",
            "function mixin() {}",
            "/** @extends {Foo} */",
            "class Bar extends mixin() {}",
            "var /** null */ x = new Bar().foo;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: null"))
        .run();
  }

  @Test
  public void testClassExtendsFunctionCallWithIncompatibleJSDoc() {
    newTest()
        .addSource(
            "class Foo {}",
            "class Baz {}",
            "/** @return {function(new:Foo)} */",
            "function mixin() {}",
            "/** @extends {Baz} */",
            "class Bar extends mixin() {}")
        .addDiagnostic(
            lines(
                "mismatch in declaration of superclass type", //
                "found   : Foo",
                "required: Baz"))
        .run();
  }

  @Test
  public void testClassImplementsInterface() {
    newTest()
        .addSource(
            "/** @interface */",
            "class Foo { foo() {} }",
            "/** @implements {Foo} */",
            "class Bar {",
            "  /** @override */",
            "  foo() {}",
            "}")
        .run();
  }

  @Test
  public void testClassImplementsInterfaceViaParent() {
    newTest()
        .addSource(
            "/** @interface */",
            "class IFoo { /** @return {*} */ foo() {} }",
            "class Foo { /** @return {number} */ foo() {} }",
            "/** @implements {IFoo} */",
            "class Zoo extends Foo {}")
        .run();
  }

  @Test
  public void testClassExtendsAbstractClassesThatImplementsInterface() {
    newTest()
        .addSource(
            "/** @interface */",
            "class IFoo { foo() {} }",
            "/** @abstract @implements {IFoo} */",
            "class Foo { /** @override */ foo() {} }",
            "/** @abstract @implements {IFoo} */",
            "class Bar extends Foo {}",
            "class Zoo extends Bar {}")
        .run();
  }

  @Test
  public void testClassMissingInterfaceMethod() {
    newTest()
        .addSource(
            "/** @interface */",
            "class Foo { foo() {} }",
            "/** @implements {Foo} */",
            "class Bar {}")
        .addDiagnostic("property foo on interface Foo is not implemented by type Bar")
        .run();
  }

  @Test
  public void testClassCannotImplementInterfaceWithAPrototypeAssignment() {
    newTest()
        .addSource(
            "/** @interface */",
            "function MyInterface() {}",
            "/** @type {string} */",
            "MyInterface.prototype.foo;",
            "/** @constructor @implements {MyInterface} */",
            "function MyClass() {}",
            "MyClass.prototype = MyInterface.prototype;")
        .addDiagnostic("property foo on interface MyInterface is not implemented by type MyClass")
        .run();
  }

  @Test
  public void testClassAbstractClassNeedNotExplicitlyOverrideUnimplementedInterfaceMethods() {
    newTest()
        .addSource(
            "/** @interface */",
            "class Foo { foo() {} }",
            "/** @abstract @implements {Foo} */",
            "class Bar {",
            // Also make sure that we can call the interface method that is not re-declared within
            // the abstract class itself.
            "    bar() { this.foo(); }",
            "}")
        .run();
  }

  @Test
  public void testClassMissingOverrideAnnotationForInterfaceMethod() {
    newTest()
        .addSource(
            "/** @interface */",
            "class Foo { foo() {} }",
            "/** @implements {Foo} */",
            "class Bar {",
            "  foo() {}",
            "}")
        .addDiagnostic(
            "property foo already defined on interface Foo; use @override to override it")
        .run();
  }

  @Test
  public void testAbstractClassMissingOverrideAnnotationForInterfaceMethod() {
    newTest()
        .addSource(
            "/** @interface */",
            "class Foo { foo() {} }",
            "/** @abstract @implements {Foo} */",
            "class Bar {",
            "  foo() {}",
            "}")
        .addDiagnostic(
            "property foo already defined on interface Foo; use @override to override it")
        .run();
  }

  @Test
  public void testClassMissingOverrideAnnotationForInterfaceInstanceProperty() {
    newTest()
        .addSource(
            "/** @record */", // `@interface` would also trigger this.
            "class Foo {",
            "  constructor() {",
            "    /** @type {number} */",
            "    this.bar;",
            "  }",
            "}",
            "",
            "/** @implements {Foo} */",
            "class MyFoo { }",
            // No `@override`.
            // For some reason we only check this when assigning to prototype properties, not to
            // instance properties.
            "/** @type {number} */",
            "MyFoo.prototype.bar = 0;")
        .addDiagnostic(
            "property bar already defined on interface Foo; use @override to override it")
        .run();
  }

  @Test
  public void testClassIncompatibleInterfaceMethodImplementation() {
    newTest()
        .addSource(
            "/** @interface */",
            "class Foo {",
            "  /** @return {number} */ foo() {}",
            "}",
            "/** @implements {Foo} */",
            "class Bar {",
            "  /** @override @return {number|string} */",
            "  foo() {}",
            "}")
        .addDiagnostic(
            lines(
                "mismatch of the foo property on type Bar and the type of the property it overrides"
                    + " from interface Foo",
                "original: function(this:Foo): number",
                "override: function(this:Bar): (number|string)"))
        .run();
  }

  @Test
  public void testClassIncompatibleInterfaceMethodImplementationInheritedOverAbstractClass() {
    newTest()
        .addSource(
            "/** @interface */",
            "class Foo {",
            "  /** @return {number} */ foo() {}",
            "}",
            "/** @abstract @implements {Foo} */",
            "class Bar {}",
            "class Zoo extends Bar {",
            "  /** @override @return {number|string} */",
            "  foo() {}",
            "}")
        .addDiagnostic(
            lines(
                "mismatch of the foo property on type Zoo and the type of the property it overrides"
                    + " from interface Foo",
                "original: function(this:Foo): number",
                "override: function(this:Zoo): (number|string)"))
        .run();
  }

  @Test
  public void testAbstractClassIncompatibleInterfaceMethodImplementation() {
    newTest()
        .addSource(
            "/** @interface */",
            "class Foo {",
            "  /** @return {number} */ foo() {}",
            "}",
            "/** @abstract @implements {Foo} */",
            "class Bar {",
            "  /** @override @return {number|string} */",
            "  foo() {}",
            "}")
        .addDiagnostic(
            lines(
                "mismatch of the foo property on type Bar and the type of the property it overrides"
                    + " from interface Foo",
                "original: function(this:Foo): number",
                "override: function(this:Bar): (number|string)"))
        .run();
  }

  @Test
  public void testClassMissingTransitiveInterfaceMethod() {
    newTest()
        .addSource(
            "/** @interface */",
            "class Foo { foo() {} }",
            "/** @interface @extends {Foo} */",
            "class Bar {}",
            "/** @implements {Bar} */",
            "class Baz {}")
        .addDiagnostic("property foo on interface Foo is not implemented by type Baz")
        .run();
  }

  @Test
  public void testClassMissingInterfaceInstanceProperty() {
    newTest()
        .addSource(
            "/** @record */", // `@interface` would also trigger this.
            "class Foo {",
            "  constructor() {",
            "    /** @type {number} */",
            "    this.bar;",
            "  }",
            "}",
            "",
            "/** @implements {Foo} */",
            "class MyFoo { }")
        .addDiagnostic("property bar on interface Foo is not implemented by type MyFoo")
        .run();
  }

  @Test
  public void testClassMissingTransitiveInterfaceInstanceProperty() {
    newTest()
        .addSource(
            "/** @record */", // `@interface` would also trigger this.
            "class Foo {",
            "  constructor() {",
            "    /** @type {number} */",
            "    this.bar;",
            "  }",
            "}",
            "",
            "/** @record */",
            "class SubFoo extends Foo { }",
            "",
            "/** @implements {SubFoo} */",
            "class MyFoo { }")
        .addDiagnostic("property bar on interface Foo is not implemented by type MyFoo")
        .run();
  }

  @Test
  public void testClassInvalidOverrideOfInterfaceInstanceProperty() {
    newTest()
        .addSource(
            "/** @record */", // `@interface` would also trigger this.
            "class Foo {",
            "  constructor() {",
            "    /** @type {number} */",
            "    this.bar;",
            "  }",
            "}",
            "",
            "/** @implements {Foo} */",
            "class MyFoo {",
            "  constructor() {",
            "    /** @type {string} */",
            "    this.bar;",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "mismatch of the bar property on type MyFoo and the type "
                    + "of the property it overrides from interface Foo",
                "original: number",
                "override: string"))
        .run();
  }

  @Test
  public void testClassPrototypeOverrideOfInterfaceInstanceProperty() {
    newTest()
        .addSource(
            "/** @record */", // `@interface` would also trigger this.
            "class Foo {",
            "  constructor() {",
            "    /** @type {number} */",
            "    this.bar;",
            "  }",
            "}",
            "",
            "/** @implements {Foo} */",
            "class MyFoo { }",
            // It's legal to fulfill the interface using either instance or prototype properties.
            "/** @override */",
            "MyFoo.prototype.bar;")
        .run();
  }

  @Test
  public void testClassInheritedInterfaceMethod() {
    newTest()
        .addSource(
            "/** @interface */",
            "class Foo { foo() {} bar() {} }",
            "/** @abstract */",
            "class Bar { foo() {} }",
            "/** @implements {Foo} */",
            "class Baz extends Bar { /** @override */ bar() {} }")
        .run();
  }

  @Test
  public void testClassMixinAllowsNonOverriddenInterfaceMethods() {
    // See cl/188076790 and b/74120976
    newTest()
        .addSource(
            "/** @interface */",
            "class Foo {",
            "  /** @return {number} */ foo() {}",
            "}",
            "class Bar {}",
            // TODO(sdh): Intersection types would allow annotating this correctly.
            "/** @return {function(new:Bar)} */",
            "function mixin() {}",
            "/** @extends {Bar} @implements {Foo} */",
            "class Baz extends mixin() {}")
        .addDiagnostic( // TODO(sdh): This is supposed to be allowed.
            "property foo on interface Foo is not implemented by type Baz")
        .run();
  }

  @Test
  public void testClassDeclarationWithExtendsOnlyInJSDoc() {
    // TODO(sdh): Should be an error, but we may need to clean up the codebase first.
    newTest()
        .addSource(
            "class Foo {}",
            "/** @extends {Foo} */",
            "class Bar {}",
            "var /** !Foo */ foo = new Bar();")
        .run();
  }

  @Test
  public void testClassConstructorTypeParametersNotIncludedOnClass() {
    newTest()
        .addSource(
            "/** @template T */",
            "class Foo {",
            "  /** @template U */",
            "  constructor() {}",
            "}",
            "var /** !Foo<string, string> */ x = new Foo();",
            "")
        .addDiagnostic(RhinoErrorReporter.TOO_MANY_TEMPLATE_PARAMS)
        .run();
  }

  @Test
  public void testClassConstructorTypeParametersNotVisibleFromOtherMethods() {
    newTest()
        .addSource(
            "class Foo {",
            "  /** @template T */",
            "  constructor() {}",
            "  foo() {",
            "    var /** T */ x;",
            "  }",
            "}")
        .addDiagnostic("Bad type annotation. Unknown type T")
        .run();
  }

  @Test
  public void testClassTtlNotAllowedOnClass() {
    newTest()
        .addSource("/** @template T := 'number' =: */ class Foo {}")
        .addDiagnostic("Template type transformation T not allowed on classes or interfaces")
        .run();
  }

  @Test
  public void testClassTtlAllowedOnConstructor() {
    // TODO(sdh): Induce a mismatch by assigning T to null, once typevars aren't treated as unknown
    newTest()
        .addSource(
            "class Foo {",
            "  /**",
            "   * @param {T} arg",
            "   * @template T := 'number' =:",
            "   */",
            "  constructor(arg) {",
            "    var /** T */ x = arg;",
            "  }",
            "}")
        .run();
  }

  @Test
  public void testClassTtlAllowedOnMethod() {
    newTest()
        .addSource(
            "class Foo {",
            "  /** @template T := 'number' =: */",
            "  foo(/** T */ arg) {",
            "    var /** T */ x = arg;",
            "  }",
            "}",
            "new Foo().foo('x')")
        .addDiagnostic(
            lines(
                "actual parameter 1 of Foo.prototype.foo does not match formal parameter",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testClassConstructorTypeParametersChecked() {
    newTest()
        .addSource(
            "/** @template T */",
            "class Foo {",
            "  /** @template U */",
            "  constructor(/** U */ arg1, /** function(U): T */ arg2) {}",
            "}",
            "/** @param {string} arg",
            "    @return {number} */",
            "function f(arg) {}",
            "var /** !Foo<number> */ foo = new Foo('x', f);")
        .run();
  }

  @Test
  public void testClassConstructorTypeParametersWithClassTypeMismatch() {
    newTest()
        .addSource(
            "/** @template T */",
            "class Foo {",
            "  /** @template U */",
            "  constructor(/** U */ arg1, /** function(U): T */ arg2) {}",
            "}",
            "/** @param {string} arg",
            "    @return {number} */",
            "function f(arg) {}",
            "var /** !Foo<string> */ foo = new Foo('x', f);")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : Foo<number>",
                "required: Foo<string>"))
        .run();
  }

  @Test
  public void testClassConstructorTypeParametersWithParameterTypeMismatch() {
    newTest()
        .addSource(
            "/** @template T */",
            "class Foo {",
            "  /** @template U */",
            "  constructor(/** U */ arg1, /** function(U): T */ arg2) {}",
            "}",
            "/** @param {string} arg",
            "    @return {number} */",
            "function f(arg) {}",
            "var foo = new Foo(42, f);")
        .addDiagnostic(
            lines(
                "actual parameter 2 of Foo does not match formal parameter",
                "found   : function(string): number",
                "required: function((number|string)): number"))
        .run();
  }

  @Test
  public void testClassSideInheritanceFillsInParameterTypesWhenCheckingBody() {
    newTest()
        .addSource(
            "class Foo {",
            "  static foo(/** string */ arg) {}",
            "}",
            "class Bar extends Foo {",
            "  /** @override */",
            "  static foo(arg) {",
            "    var /** null */ x = arg;",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: null"))
        .run();
  }

  @Test
  public void testClassMethodParameters() {
    newTest()
        .addSource(
            "class C {", "  /** @param {number} arg */", "  m(arg) {}", "}", "new C().m('x');")
        .addDiagnostic(
            lines(
                "actual parameter 1 of C.prototype.m does not match formal parameter",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testClassInheritedMethodParameters() {
    newTest()
        .addSource(
            "var B = class {",
            "  /** @param {boolean} arg */",
            "  method(arg) {}",
            "};",
            "var C = class extends B {};",
            "new C().method(1);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of B.prototype.method does not match formal parameter",
                "found   : number",
                "required: boolean"))
        .run();
  }

  @Test
  public void testClassMethodReturns() {
    newTest()
        .addSource(
            "var D = class {",
            "  /** @return {number} */",
            "  m() {}",
            "}",
            "var /** null */ x = new D().m();")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: null"))
        .run();
  }

  @Test
  public void testClassInheritedMethodReturns() {
    newTest()
        .addSource(
            "class Q {",
            "  /** @return {string} */",
            "  method() {}",
            "};",
            "var P = class extends Q {};",
            "var /** null */ x = new P().method();")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: null"))
        .run();
  }

  @Test
  public void testStaticMethodParameters() {
    newTest()
        .addSource(
            "class C {", "  /** @param {number} arg */", "  static m(arg) {}", "}", "C.m('x');")
        .addDiagnostic(
            lines(
                "actual parameter 1 of C.m does not match formal parameter",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testClassInheritedStaticMethodParameters() {
    newTest()
        .addSource(
            "var B = class {",
            "  /** @param {boolean} arg */",
            "  static method(arg) {}",
            "};",
            "var C = class extends B {};",
            "C.method(1);")
        .addDiagnostic(
            lines(
                "actual parameter 1 of C.method does not match formal parameter",
                "found   : number",
                "required: boolean"))
        .run();
  }

  @Test
  public void testStaticMethodReturns() {
    newTest()
        .addSource(
            "var D = class {",
            "  /** @return {number} */",
            "  static m() {}",
            "};",
            "var /** null */ x = D.m();")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: null"))
        .run();
  }

  @Test
  public void testClassInheritedStaticMethodReturns() {
    newTest()
        .addSource(
            "class Q {",
            "  /** @return {string} */",
            "  static method() {}",
            "}",
            "class P extends Q {}",
            "var /** null */ x = P.method();")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: null"))
        .run();
  }

  @Test
  public void testStaticMethodCalledOnInstance() {
    newTest()
        .addSource(
            "class C {", //
            "  static m() {}",
            "}",
            "new C().m();")
        .addDiagnostic( // TODO(b/111229815): Fix to "Property m never defined on instances of C".
            "Property m never defined on C")
        .run();
  }

  @Test
  public void testClassInstanceMethodCalledOnClass() {
    newTest()
        .addSource(
            "class C {", //
            "  m() {}",
            "}",
            "C.m();")
        .addDiagnostic( // TODO(b/111229815): Fix to "Property m never defined on namespace C".
            "Property m never defined on C")
        .run();
  }

  @Test
  public void testClassInstanceMethodOverriddenWithMissingOverrideAnnotation() {
    newTest()
        .addSource(
            "class Base {",
            "  /** @param {string|number} arg */",
            "  method(arg) {}",
            "}",
            "class Sub extends Base {",
            "  method(arg) {}",
            "}")
        .addDiagnostic(
            "property method already defined on superclass Base; use @override to override it")
        .run();
  }

  @Test
  public void testClassInstanceMethodOverriddenWithWidenedType() {
    newTest()
        .addSource(
            "class Base {",
            "  /** @param {string} arg */",
            "  method(arg) {}",
            "}",
            "class Sub extends Base {",
            "  /** @override @param {string|number} arg */",
            "  method(arg) {}",
            "}")
        .run();
  }

  @Test
  public void testClassInstanceMethodOverriddenWithIncompatibleType() {
    newTest()
        .addSource(
            "class Base {",
            "  /** @param {string|number} arg */",
            "  method(arg) {}",
            "}",
            "class Sub extends Base {",
            "  /** @override @param {string} arg */",
            "  method(arg) {}",
            "}")
        .addDiagnostic(
            lines(
                "mismatch of the method property type and the type of the property it overrides "
                    + "from superclass Base",
                "original: function(this:Base, (number|string)): undefined",
                "override: function(this:Sub, string): undefined"))
        .run();
  }

  @Test
  public void testClassInstanceMethodOverriddenWithIncompatibleType2() {
    newTest()
        .addSource(
            "class Base {",
            "  /** @return {string} */",
            "  method() {}",
            "}",
            "class Sub extends Base {",
            "  /** @override @return {string|number} */",
            "  method() {}",
            "}")
        .addDiagnostic(
            lines(
                "mismatch of the method property type and the type of the property it overrides "
                    + "from superclass Base",
                "original: function(this:Base): string",
                "override: function(this:Sub): (number|string)"))
        .run();
  }

  @Test
  public void testStaticMethod_overriddenInBody_withSubtype_atOverride_isOk() {
    newTest()
        .addSource(
            "class Base {",
            "  /** @param {string} arg */",
            "  static method(arg) {}",
            "}",
            "",
            "class Sub extends Base {",
            "  /**",
            "   * @override",
            // Method is a subtype due to parameter contravariance.
            "   * @param {string|number} arg",
            "   */",
            "  static method(arg) {}",
            "}")
        .run();
  }

  @Test
  public void testStaticMethod_overriddenInBody_notAtOverride_isBad() {
    newTest()
        .addSource(
            "class Base {",
            "  /** @param {string} arg */",
            "  static method(arg) {}",
            "}",
            "",
            "class Sub extends Base {",
            // Method is a subtype due to parameter contravariance.
            "  /** @param {string|number} arg */",
            "  static method(arg) {}",
            "}")
        .addDiagnostic(
            lines(
                "property method already defined on supertype (typeof Base); "
                    + "use @override to override it"))
        .run();
  }

  @Test
  public void testStaticMethod_thatIsNotAnOverride_atOverride_isBad() {
    newTest()
        .addSource(
            "class Base {",
            "  /**",
            "   * @override",
            "   * @param {string} arg",
            "   */",
            "  static method(arg) {}",
            "}")
        .addDiagnostic(lines("property method not defined on any supertype of (typeof Base)"))
        .run();
  }

  @Test
  public void testStaticMethod_overriddenInBody_withSupertype_isBad() {
    newTest()
        .addSource(
            "class Base {",
            "  /** @param {string|number} arg */",
            "  static method(arg) {}",
            "}",
            "class Sub extends Base {",
            "  /**",
            "   * @override",
            // Method is a supertype due to parameter contravariance.
            "   * @param {string} arg",
            "   */",
            "  static method(arg) {}",
            "}")
        .addDiagnostic(
            lines(
                "mismatch of the method property type and the type of the property it overrides "
                    + "from supertype (typeof Base)",
                "original: function(this:(typeof Base), (number|string)): undefined",
                "override: function(this:(typeof Sub), string): undefined"))
        .run();
  }

  @Test
  public void testStaticMethod_overriddenInBody_withSupertype_fromInline_isBad() {
    newTest()
        .addSource(
            "class Base {",
            "  static method(/** string|number */ arg) {}",
            "}",
            "class Sub extends Base {",
            "  /** @override */",
            "  static method(/** string */ arg) {}",
            "}")
        .addDiagnostic(
            lines(
                "mismatch of the method property type and the type of the property it overrides "
                    + "from supertype (typeof Base)",
                "original: function(this:(typeof Base), (number|string)): undefined",
                "override: function(this:(typeof Sub), string): undefined"))
        .run();
  }

  @Test
  public void testStaticMethod_overriddenOutsideBody_withSubtype_atOverride_isOk() {
    newTest()
        .addSource(
            "class Base {",
            "  static method(/** string */ arg) {}",
            "}",
            "",
            "class Sub extends Base { }",
            "",
            "/**",
            " * @override",
            // Method is a subtype due to parameter contravariance.
            " * @param {string|number} arg",
            " */",
            "Sub.method = function(arg) {};")
        .run();
  }

  @Test
  public void testStaticMethod_overriddenOutsideBody_withSupertype_isBad() {
    newTest()
        .addSource(
            "class Base {",
            "  static method(/** string|number */ arg) {}",
            "}",
            "",
            "class Sub extends Base { }",
            "",
            "/**",
            " * @override",
            " * @param {string} arg",
            " */",
            "Sub.method = function(arg) {};")
        .addDiagnostic(
            lines(
                "mismatch of the method property type and the type of the property it overrides "
                    + "from supertype (typeof Base)",
                "original: function(this:(typeof Base), (number|string)): undefined",
                "override: function(string): undefined"))
        .run();
  }

  @Test
  public void testStaticMethod_onInterface_overriddenInBody_withSubtype_atOverride_isOk() {
    newTest()
        .addSource(
            "/** @interface */",
            "class Base {",
            "  /** @param {string} arg */",
            "  static method(arg) {}",
            "}",
            "",
            "/** @interface */",
            "class Sub extends Base {",
            "  /**",
            "   * @override",
            // Method is a subtype due to parameter contravariance.
            "   * @param {string|number} arg",
            "   */",
            "  static method(arg) {}",
            "}")
        .run();
  }

  @Test
  public void
      testStaticMethod_onNamespacedType_overriddenOutsideBody_withSubtype_atOverride_isOk() {
    newTest()
        .addSource(
            "const ns = {};",
            "",
            "ns.Base = class {",
            "  /** @param {string} arg */",
            "  static method(arg) {}",
            "};",
            "",
            "ns.Sub = class extends ns.Base {};",
            "",
            "/**",
            " * @override",
            // Method is a subtype due to parameter contravariance.
            " * @param {string|number} arg",
            " */",
            // We specifically want to check that q-name lookups are checked.
            "ns.Sub.method = function(arg) {};")
        .run();
  }

  @Test
  public void testStaticMethod_onNamespacedType_overriddenOutsideBody_notAtOverride_isBad() {
    newTest()
        .addSource(
            "const ns = {};",
            "",
            "ns.Base = class {",
            "  /** @param {string} arg */",
            "  static method(arg) {}",
            "};",
            "",
            "ns.Sub = class extends ns.Base {};",
            "",
            // Method is a subtype due to parameter contravariance.
            "/** @param {string|number} arg */",
            // We specifically want to check that q-name lookups are checked.
            "ns.Sub.method = function(arg) {};")
        .addDiagnostic(
            lines(
                "property method already defined on supertype (typeof ns.Base); "
                    + "use @override to override it"))
        .run();
  }

  @Test
  public void testStaticMethod_onNamespacedType_overridden_withNonSubtype_isBad() {
    newTest()
        .addSource(
            "const ns = {};",
            "",
            "ns.Base = class {",
            "  /** @param {string} arg */",
            "  static method(arg) {}",
            "};",
            "",
            "ns.Sub = class extends ns.Base {};",
            "",
            "/**",
            " * @override",
            // Method is a subtype due to parameter contravariance.
            " * @param {number} arg",
            " */",
            // We specifically want to check that q-name lookups are checked.
            "ns.Sub.method = function(arg) {};")
        .addDiagnostic(
            lines(
                "mismatch of the method property type and the type of the property it overrides "
                    + "from supertype (typeof ns.Base)",
                "original: function(this:(typeof ns.Base), string): undefined",
                "override: function(number): undefined"))
        .run();
  }

  @Test
  public void testClassExtendsForwardReference_staticMethodThatIsAnOverride_atOverride_isOk() {
    newTest()
        .addSource(
            "/** @return {function(new: Parent): ?} */",
            "function mixin() {}",
            "/** @extends {Parent} */",
            "class Middle extends mixin() {",
            "  /** @override */",
            "  static method() {}",
            "}",
            "",
            "class Child extends Middle {",
            "  /** @override */",
            "  static method() {}",
            "}",
            "",
            "class Parent {",
            "  method() {}",
            "}")
        .run();
  }

  @Test
  public void testClassTreatedAsStruct() {
    newTest()
        .addSource(
            "class Foo {}", //
            "var foo = new Foo();",
            "foo.x = 42;")
        .addDiagnostic(
            "Cannot add a property to a struct instance after it is constructed."
                + " (If you already declared the property, make sure to give it a type.)")
        .run();
  }

  @Test
  public void testClassTreatedAsStructSymbolAccess() {
    newTest()
        .addSource(
            "class Foo {}", //
            "var foo = new Foo();",
            "foo[Symbol.iterator] = 42;")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testClassAnnotatedWithUnrestricted() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @unrestricted */ class Foo {}", //
            "var foo = new Foo();",
            "foo.x = 42;")
        .run();
  }

  @Test
  public void testClassAnnotatedWithDictDotAccess() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @dict */ class Foo {}", //
            "var foo = new Foo();",
            "foo.x = 42;")
        .addDiagnostic("Cannot do '.' access on a dict")
        .run();
  }

  @Test
  public void testClassAnnotatedWithDictComputedAccess() {
    newTest()
        .addSource(
            "/** @dict */ class Foo {}", //
            "var foo = new Foo();",
            "foo['x'] = 42;")
        .run();
  }

  @Test
  public void testClassSuperInConstructor() {
    newTest()
        .addSource(
            "class Foo {",
            "  constructor(/** number */ arg) {}",
            "}",
            "class Bar extends Foo {",
            "  constructor(/** string */ arg) { super(1); }",
            "}",
            "var /** !Foo */ foo = new Bar('x');")
        .run();
  }

  @Test
  public void testClassSuperConstructorParameterMismatch() {
    newTest()
        .addSource(
            "class Foo {",
            "  constructor(/** number */ arg) {}",
            "}",
            "class Bar extends Foo {",
            "  constructor() {",
            "    super('x');",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of super does not match formal parameter",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testClassSuperConstructorParameterCountMismatch() {
    newTest()
        .addSource(
            "class Foo {}",
            "class Bar extends Foo {",
            "  constructor() {",
            "    super(1);",
            "  }",
            "}")
        .addDiagnostic(
            "Function super: called with 1 argument(s). Function requires at least 0 argument(s) "
                + "and no more than 0 argument(s).")
        .run();
  }

  @Test
  public void testClassSuperMethodNotPresent() {
    newTest()
        .addSource(
            "class Foo {}", "class Bar extends Foo {", "  foo() {", "    super.foo();", "  }", "}")
        .addDiagnostic("Property foo never defined on Foo")
        .run();
  }

  @Test
  public void testClassSuperMethodParameterMismatch() {
    newTest()
        .addSource(
            "class Foo {",
            "  /** @param {string} arg */",
            "  foo(arg) {}",
            "}",
            "class Bar extends Foo {",
            "  /** @override */",
            "  foo() {",
            "    super.foo(42);",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of Foo.prototype.foo does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testClassSuperMethodCalledFromArrow() {
    newTest()
        .addSource(
            "class Foo {",
            "  /** @param {string} arg */",
            "  foo(arg) {}",
            "}",
            "class Bar extends Foo {",
            "  /** @override */",
            "  foo() {",
            "    () => super.foo(42);",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of Foo.prototype.foo does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testClassSuperMethodReturnType() {
    newTest()
        .addSource(
            "class Foo {",
            "  /** @return {string} */",
            "  foo() {}",
            "}",
            "class Bar extends Foo {",
            "  /** @override */",
            "  foo() {",
            "    var /** null */ x = super.foo();",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: null"))
        .run();
  }

  @Test
  public void testClassSuperMethodFromDifferentMethod() {
    newTest()
        .addSource(
            "class Foo {",
            "  /** @return {string} */",
            "  foo() {}",
            "}",
            "class Bar extends Foo {",
            "  bar() {",
            "    var /** null */ x = super.foo();",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: null"))
        .run();
  }

  @Test
  public void testClassSuperMethodNotWidenedWhenOverrideWidens() {
    newTest()
        .addSource(
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
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of Foo.prototype.foo does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testClassSuperMethodCallableInParameters() {
    newTest()
        .addSource(
            "class Foo {",
            "  /** @return {string|number} */",
            "  foo() {",
            "    return 0;",
            "  }",
            "}",
            "class Bar extends Foo {",
            "  /**",
            "   * @param {number=} param",
            "   * @return {string}",
            "   * @override",
            "   */",
            //  super.foo() returns string|number, so `param` is typed as `string|number`
            "  foo(param = super.foo()) {",
            "    return 'param: ' + param;",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "default value has wrong type",
                "found   : (number|string)",
                "required: (number|undefined)"))
        .run();
  }

  @Test
  public void testAbstractSuperMethodCall_warning() {
    newTest()
        .addSource(
            "/** @abstract */",
            "class Foo {",
            "  /** @abstract */",
            "  foo() {}",
            "}",
            "class Bar extends Foo {",
            "  /** @override */",
            "  foo() {",
            "    super.foo();",
            "  }",
            "}")
        .addDiagnostic("Abstract super method Foo.prototype.foo cannot be dereferenced")
        .run();
  }

  @Test
  public void testAbstractInheritedSuperMethodCall_warning() {
    newTest()
        .addSource(
            "/** @abstract */",
            "class Foo {",
            "  /** @abstract */",
            "  foo() {}",
            "}",
            "/** @abstract */",
            "class Bar extends Foo {}",
            "class Baz extends Bar {",
            "  /** @override */",
            "  foo() {",
            "    super.foo();",
            "  }",
            "}")
        .addDiagnostic("Abstract super method Foo.prototype.foo cannot be dereferenced")
        .run();
  }

  @Test
  public void testAbstractInheritedSuperMethodCallInAbstractClass_warning() {
    newTest()
        .addSource(
            "/** @abstract */",
            "class Base {",
            "  /** @abstract */",
            "  foo() {}",
            "}",
            "/** @abstract */",
            "class Sub extends Base {",
            "  bar() {",
            "    super.foo();",
            "  }",
            "}")
        .addDiagnostic("Abstract super method Base.prototype.foo cannot be dereferenced")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testConcreteSuperMethodCall_noWarning() {
    newTest()
        .addSource(
            "/** @abstract */",
            "class Foo {",
            "  foo() {}",
            "}",
            "class Bar extends Foo {",
            "  /** @override */",
            "  foo() {",
            "    super.foo();",
            "  }",
            "}")
        .run();
  }

  @Test
  public void testClassStaticSuperParameterMismatch() {
    newTest()
        .addSource(
            "class Foo {",
            "  static foo(/** number */ arg) {}",
            "}",
            "class Bar extends Foo {",
            "  /** @override */",
            "  static foo() {",
            "    super.foo('x');",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                // TODO(b/111229815): "Foo.foo" instead of "super.foo"
                "actual parameter 1 of super.foo does not match formal parameter",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testClassStaticSuperCalledFromArrow() {
    newTest()
        .addSource(
            "class Foo {",
            "  static foo(/** number */ arg) {}",
            "}",
            "class Bar extends Foo {",
            "  /** @override */",
            "  static foo() {",
            "    () => super.foo('x');",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                // TODO(b/111229815): "Foo.foo" instead of "super.foo"
                "actual parameter 1 of super.foo does not match formal parameter",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testClassStaticSuperParameterCountMismatch() {
    newTest()
        .addSource(
            "class Foo {",
            "  static foo() {}",
            "}",
            "class Bar extends Foo {",
            "  /** @override */",
            "  static foo() {",
            "    super.foo(1);",
            "  }",
            "}")
        .addDiagnostic( // TODO(b/111229815): "Foo.foo" instead of "super.foo"
            "Function super.foo: called with 1 argument(s). "
                + "Function requires at least 0 argument(s) and no more than 0 argument(s).")
        .run();
  }

  @Test
  public void testClassStaticSuperNotPresent() {
    newTest()
        .addSource(
            "class Foo {}",
            "class Bar extends Foo {",
            "  static foo() {",
            "    super.foo;",
            "  }",
            "}")
        .addDiagnostic( // TODO(b/111229815): "Property foo never defined on namespace Foo"
            "Property foo never defined on super")
        .run();
  }

  @Test
  public void testClassStaticSuperCallsDifferentMethod() {
    newTest()
        .addSource(
            "class Foo {",
            "  /** @param {string} arg */",
            "  static foo(arg) {}",
            "}",
            "class Bar extends Foo {",
            "  /** @override */",
            "  static foo(/** string|number */ arg) {}",
            "  static bar() { super.foo(42); }",
            "}")
        .addDiagnostic(
            lines(
                // TODO(b/111229815): "Foo.foo" instead of "super.foo"
                "actual parameter 1 of super.foo does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testClassFields() {
    newTest()
        .addSource(
            "class A {", //
            "  x = 2;",
            "}")
        .run();
    newTest()
        .addSource(
            "class B {", //
            "  x;",
            "}")
        .run();
    newTest()
        .addSource(
            "class C {", //
            "  x",
            "}")
        .run();
    newTest()
        .addSource(
            "class D {", //
            "  /** @type {string|undefined} */",
            "  x;",
            "}")
        .run();
    newTest()
        .addSource(
            "class E {", //
            "  /** @type {string} @suppress {checkTypes} */",
            "  x = 2;",
            "}")
        .run();
  }

  @Test
  public void testClassFieldsThis() {
    newTest()
        .addSource(
            "class F {", //
            "  /** @type {number} */",
            "  x = 2;",
            "  /** @type {boolean} */",
            "  y = this.x",
            "}")
        .addDiagnostic(
            lines(
                "assignment to property y of F", //
                "found   : number",
                "required: boolean"))
        .run();
  }

  @Test
  public void testClassFieldsSuper() {
    newTest()
        .addSource(
            "class G {", //
            "  /** @return {number} */",
            "  getX() { return 2; }",
            "}",
            "class H extends G {",
            "  /** @return {?} */",
            "  /** @override*/ getX() {}",
            "  /** @type {string} */",
            "  y = super.getX();",
            "}")
        .addDiagnostic(
            lines(
                "assignment to property y of H", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testComputedFields() {
    newTest()
        .addSource(
            "var /** number */ x = 1;",
            "function takesNumber(/** number */ x) {}",
            "/** @unrestricted */",
            "class Foo {",
            "  /** @type {boolean} */",
            "  x = true;",
            "  [this.x] = takesNumber(this.x);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesNumber does not match formal parameter", //
                "found   : boolean",
                "required: number"))
        .run();
  }

  @Test
  public void testStaticClassFields() {
    newTest()
        .addSource(
            "class A {", //
            "  static x = 2;",
            "}")
        .run();
    newTest()
        .addSource(
            "class B {", //
            "  static x;",
            "}")
        .run();
    newTest()
        .addSource(
            "class C {", //
            "  static x",
            "}")
        .run();
    newTest()
        .addSource(
            "class D {", //
            "  /** @type {string|undefined} */",
            "  static x;",
            "}")
        .run();
    newTest()
        .addSource(
            "class E {", //
            "  /** @type {string} @suppress {checkTypes} */",
            "  static x = 2;",
            "}")
        .run();
  }

  @Test
  public void testStaticClassFieldsThis() {
    newTest()
        .addSource(
            "class F {", //
            "  /** @type {number} */",
            "  static x = 2;",
            "  /** @type {boolean} */",
            "  static y = this.x",
            "}")
        .addDiagnostic(
            lines(
                "assignment to property y of F", //
                "found   : number",
                "required: boolean"))
        .run();
  }

  @Test
  public void testStaticComputedFields() {
    newTest()
        .addSource(
            "var /** number */ x = 1;",
            "function takesNumber(/** number */ x) {}",
            "/** @unrestricted */",
            "class Foo {",
            "  /** @type {boolean} */",
            "  static x = true;",
            "  static [this.x] = takesNumber(this.x);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesNumber does not match formal parameter", //
                "found   : boolean",
                "required: number"))
        .run();
  }

  @Test
  public void testStaticClassFieldsSuper() {
    newTest()
        .addSource(
            "class G {", //
            "  /** @type {number} */",
            "  static x = 2;",
            "}",
            "class H extends G {",
            "  /** @type {string} */",
            "  static y = super.x;",
            "}")
        .addDiagnostic(
            lines(
                "assignment to property y of H", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testClassStaticBlockVariablesWrongTypes() {
    newTest()
        .addSource(
            "class Foo {", //
            "  static {",
            "    /** @type {number} */",
            "    let str = 'str';",
            "    /** @type {boolean|string} */",
            "    const num = 5;",
            "    /** @type {?string} */",
            "    var bool = true;",
            "  }",
            "};")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: number"))
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: (boolean|string)"))
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : boolean",
                "required: (null|string)"))
        .run();
  }

  @Test
  public void testClassStaticBlockPropertyWithThis() {
    newTest()
        .addSource(
            "/** @type {number} */",
            "var x = 4;",
            "class Foo {", //
            "  static {",
            "    this.x;",
            "  }",
            "};")
        .addDiagnostic("Property x never defined on this")
        .run();
  }

  @Test
  public void testClassStaticBlockWithWrongTypeThisRHS() {
    newTest()
        .addSource(
            "class Foo {",
            "  static {",
            "    /** @type {number} */",
            "    this.num = 1;",
            "    /** @type {string} */",
            "    var str = this.num;",
            "  }",
            "};")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testClassStaticBlockWithWrongTypeThisLHS() {
    newTest()
        .addSource(
            "class Foo { ",
            "  static {",
            "    /** @type {string} */",
            "    this.str = 2;",
            "  }",
            "};")
        .addDiagnostic(
            lines(
                "assignment to property str of this", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testClassStaticBlockWithSuper() {
    newTest()
        .addSource(
            "class Foo {",
            "  /** @type {string} */",
            "  static str;",
            "}",
            "class Bar extends Foo {",
            "  static {",
            "    super.str = 'str';",
            "  }",
            "}")
        .run();
  }

  @Test
  public void testClassStaticBlockWithWrongTypeSuper() {
    newTest()
        .addSource(
            "class Foo {",
            "  /** @type {string} */",
            "  static str;",
            "}",
            "class Bar extends Foo {",
            "  static {",
            "    super.str = 5;",
            "  }",
            "};")
        .addDiagnostic(
            lines(
                "assignment to property str of super", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testClassStaticBlockInheritanceWithClassName() {
    newTest()
        .addSource(
            "class Foo {",
            "  static foo(/** number */ arg) {}",
            "}",
            "class Bar extends Foo {",
            "  static {",
            "    Foo.foo('str');",
            "  }",
            "};")
        .addDiagnostic(
            lines(
                "actual parameter 1 of Foo.foo does not match formal parameter", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testClassStaticBlockWithWrongTypeSuperParameter() {
    newTest()
        .addSource(
            "class Foo {",
            "  static foo(/** number */ arg) {}",
            "}",
            "class Bar extends Foo {",
            "  static {",
            "    super.foo('str');",
            "  }",
            "};")
        .addDiagnostic(
            lines(
                "actual parameter 1 of super.foo does not match formal parameter",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testClassStaticBlockTypeNarrowing() {
    newTest()
        .addSource(
            "class C {",
            "  static {",
            "    /** @param {?string} x */",
            "    function foo(x) {",
            "      if (x != null) {",
            "        /** @type {string} */",
            "        const noNull = x;",
            "      }",
            "    }",
            "  }",
            "}")
        .run();
  }

  @Test
  public void testClassStaticBlockTypeNarrowing2() {
    newTest()
        .addExterns("/** @type {?string} */ var strOrNull;")
        .addSource(
            "class C {",
            "  static {",
            "    if (strOrNull != null) {",
            "      /** @type {string} */",
            "      const noNull = strOrNull;",
            "    }",
            "  }",
            "}")
        .run();
  }

  @Test
  public void testClassStaticBlockOverrideSupertypeOnAnonymousClass() {
    newTest()
        .addSource(
            "function use(ctor) {}",
            "",
            "class Foo { ",
            "  static {",
            "    /** @type {string} */",
            "    this.str;",
            "  }",
            "}",
            "use(class extends Foo {",
            "  static { this.str = 3; }",
            "});")
        .addDiagnostic(
            lines(
                "assignment to property str of this", //
                "found   : number",
                "required: string"))
        .addDiagnostic(
            "property str already defined on supertype (typeof Foo); use @override to override it")
        .run();
  }

  @Test
  public void testClassTypeOfThisInConstructor() {
    newTest()
        .addSource(
            "class Foo {", "  constructor() {", "    var /** null */ foo = this;", "  }", "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : Foo",
                "required: null"))
        .run();
  }

  @Test
  public void testClassTypeOfThisInMethod() {
    newTest()
        .addSource(
            "class Foo {", //
            "  foo() {",
            "    var /** null */ foo = this;",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : Foo",
                "required: null"))
        .run();
  }

  @Test
  public void testClassTypeOfThisInStaticMethod() {
    newTest()
        .addSource(
            "class Foo {", //
            "  static foo() {",
            "    var /** null */ foo = this;",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : (typeof Foo)",
                "required: null"))
        .run();
  }

  @Test
  public void testClassGetter() {
    newTest()
        .addSource(
            "class C {", //
            "  get x() {}",
            "}",
            "var /** null */ y = new C().x;")
        .run();
  }

  @Test
  public void testClassGetterMismatch() {
    newTest()
        .addSource(
            "class C {",
            "  /** @return {number} */",
            "  get x() {}",
            "}",
            "var /** null */ y = new C().x;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: null"))
        .run();
  }

  @Test
  public void testClassStaticGetter() {
    newTest()
        .addSource(
            "class C {", //
            "  static get x() {}",
            "}",
            "var /** null */ y = C.x;")
        .run();
  }

  @Test
  public void testClassStaticGetterMismatch() {
    newTest()
        .addSource(
            "class C {",
            "  /** @return {number} */",
            "  static get x() {}",
            "}",
            "var /** null */ y = C.x;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: null"))
        .run();
  }

  @Test
  public void testClassSetter() {
    newTest()
        .addSource(
            "class C {", //
            "  set x(arg) {}",
            "}",
            "new C().x = null;")
        .run();
  }

  @Test
  public void testClassSetterMismatch() {
    newTest()
        .addSource(
            "class C {",
            "  /** @param {number} arg */",
            "  set x(arg) {}",
            "}",
            "new C().x = null;")
        .addDiagnostic(
            lines(
                "assignment to property x of C", //
                "found   : null",
                "required: number"))
        .run();
  }

  @Test
  public void testClassSetterWithMissingParameter() {
    newTest().addSource("class C { set a(b) {} }").run();
  }

  @Test
  public void testClassStaticSetter() {
    newTest()
        .addSource(
            "class C {", //
            "  static set x(arg) {}",
            "}",
            "C.x = null;")
        .run();
  }

  @Test
  public void testClassStaticSetterMismatch() {
    newTest()
        .addSource(
            "class C {",
            "  /** @param {number} arg */",
            "  static set x(arg) {}",
            "}",
            "C.x = null;")
        .addDiagnostic(
            lines(
                "assignment to property x of C", //
                "found   : null",
                "required: number"))
        .run();
  }

  @Test
  public void testClassGetterAndSetter() {
    newTest()
        .addSource(
            "class C {",
            "  /** @return {number} */",
            "  get x() {}",
            "  /** @param {number} arg */",
            "  set x(arg) {}",
            "}",
            "var /** number */ y = new C().x;",
            "new C().x = 42;")
        .run();
  }

  @Test
  public void testClassGetterAndSetterNoJsDoc() {
    newTest()
        .addSource(
            "class C {",
            "  get x() {}",
            "  set x(arg) {}",
            "}",
            "var /** number */ y = new C().x;",
            "new C().x = 42;")
        .run();
  }

  @Test
  public void testClassGetterAndSetterDifferentTypes() {
    newTest()
        .addSource(
            "class C {",
            "  /** @return {number} */",
            "  get x() {}",
            "  /** @param {string} arg */",
            "  set x(arg) {}",
            "}",
            "var /** null */ y = new C().x;",
            "new C().x = null;")
        .addDiagnostic(
            lines(
                // TODO(b/116797078): Having different getter and setter types should be allowed and
                // not produce the following error.
                "The types of the getter and setter for property 'x' do not match.",
                "getter type is: number",
                "setter type is: string"))
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: null"))
        .addDiagnostic(
            lines(
                "assignment to property x of C",
                "found   : null",
                // TODO(b/116797078): This should report that it requires a string.
                "required: number"))
        .run();
  }

  @Test
  public void testClassGetterAndSetterWithSameStructuralTypeIsAllowed() {
    // Regression test for a case where we were warning for CONFLICTING_GETTER_SETTER_TYPE when the
    // getter and setter used record types.
    // This was fixed by always using structural equality when checking equality for RecordTypes
    newTest()
        .addSource(
            "class C {",
            "  /** @return {{x: number}} */",
            "  get x() { return {x: 0}; }",
            "  /** @param {{x: number}} arg */",
            "  set x(arg) {}",
            "}",
            "const c = new C();",
            "c.x = {x: 3};",
            "const /** {x: number} */ something = c.x;")
        .run();
  }

  @Test
  public void testClassGetterAndSetterWithSameStructuralRecordAndNominalTypeIsAllowed() {
    // NOTE: we would actually `like` to allow this, but right now only actual RecordTypes are
    // compared structurally when checking equality.
    newTest()
        .addSource(
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
            "const /** {x: number} */ something = c.x;")
        .addDiagnostic(
            lines(
                "The types of the getter and setter for property 'x' do not match.",
                "getter type is: xRecord",
                "setter type is: {x: number}"))
        .run();
  }

  @Test
  public void testClassGetterWithShadowingDeclarationOnInstanceType() {
    newTest()
        .addSource(
            "class C {",
            "  constructor() {",
            "    /** @type {string|undefined} */",
            "    this.x;",
            "  }",
            "  /** @return {number} */",
            "  get x() { return 0; }",
            "}",
            "new C().x = null;")
        .addDiagnostic( // TODO(b/144954613): we could really throw a clearer error here at the
            // point where we
            // redeclare 'this.x', and also should forbid writing to 'new C().x'.
            lines(
                "assignment to property x of C", //
                "found   : null",
                "required: number"))
        .run();
  }

  @Test
  public void testClassGetterWithDuplicateDeclarationLater() {
    newTest()
        .addSource(
            "class C {",
            "  /** @return {number} */",
            "  get x() {}",
            "}",
            "/** @type {string} */",
            "C.prototype.x;",
            "new C().x = null;")
        .addDiagnostic(
            // TODO(b/144954613): this should report an error related to the redeclaration of 'x'
            lines(
                "assignment to property x of C", //
                "found   : null",
                "required: number"))
        .run();
  }

  @Test
  public void testClassNewTargetInArrowFunction() {
    // TODO(sdh): This should be an error.
    newTest().addSource("const f = () => { const /** null */ x = new.target; };").run();
  }

  @Test
  public void testClassNewTargetInMethod() {
    newTest()
        .addSource("class Foo { foo() { const /** null */ x = new.target; } }")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : undefined",
                "required: null"))
        .run();
  }

  @Test
  public void testClassNewTargetInVanillaFunction() {
    newTest()
        .addSource("function f() { const /** null */ x = new.target; }")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : (Function|undefined)",
                "required: null"))
        .run();
  }

  @Test
  public void testClassNewTargetInVanillaFunctionNestedArrow() {
    newTest()
        .addSource("function f() { const f = () => { const /** null */ x = new.target; }; }")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : (Function|undefined)",
                "required: null"))
        .run();
  }

  @Test
  public void testClassNewTargetInConstructor() {
    newTest()
        .addSource("class Foo { constructor() { const /** null */ x = new.target; } };")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : Function",
                "required: null"))
        .run();
  }

  @Test
  public void testClassNewTargetInConstructorNestedArrow() {
    newTest()
        .addSource(
            "class Foo { constructor() { const f = () => { const /** null */ x = new.target; }; }"
                + " };")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : Function",
                "required: null"))
        .run();
  }

  @Test
  public void testClassEs5ClassCannotExtendEs6Class() {
    newTest()
        .addSource(
            "class Base {}", //
            "/** @constructor @extends {Base} */",
            "function Sub() {}")
        .addDiagnostic("ES5 class Sub cannot extend ES6 class Base")
        .run();
  }

  @Test
  public void testClassEs5ClassCanImplementEs6Interface() {
    newTest()
        .addSource(
            "/** @interface */",
            "class Inter {}",
            "/** @constructor @implements {Inter} */",
            "function Sub() {}")
        .run();
  }

  @Test
  public void testClassExtendsForwardReferencedClass() {
    newTest()
        .addSource(
            "/** @const */ var ns = {};",
            "(function() {",
            "  ns.Base = class {};",
            "})();",
            "class Sub extends ns.Base {}",
            "var /** !ns.Base */ x = new Sub();")
        .run();
  }

  @Test
  public void testClassExtendsItself() {
    newTest()
        .addSource("class Foo extends Foo {}")
        .addDiagnostic("Cycle detected in inheritance chain of type Foo")
        .run();
  }

  @Test
  public void testClassExtendsCycle() {
    newTest()
        .addSource(
            "class Foo extends Bar {}", //
            "class Bar extends Foo {}")
        .addDiagnostic("Cycle detected in inheritance chain of type Bar")
        .run();
  }

  @Test
  public void testClassExtendsCycleOnlyInJsdoc() {
    newTest()
        .addSource(
            "class Bar {}", //
            "/** @extends {Foo} */",
            "class Foo extends Bar {}")
        .addDiagnostic("Cycle detected in inheritance chain of type Foo")
        .addDiagnostic("Could not resolve type in @extends tag of Foo")
        .run();
  }

  @Test
  public void testClassExtendsCycleOnlyInAst() {
    // TODO(sdh): This should give an error.
    newTest()
        .addSource(
            "class Bar {}", //
            "/** @extends {Bar} */",
            "class Foo extends Foo {}")
        .run();
  }

  @Test
  public void testMixinFunction() {
    newTest()
        .addSource(
            "/** @param {function(new: ?, ...?)} ctor */",
            "function mixin(ctor) {",
            // ctor isn't properly declared as a type,
            // but we shouldn't generate an error,
            // because it is a real value, not an annotation,
            // and we need this coding pattern to work.
            "  class Foo extends ctor {}",
            "}")
        .run();
  }

  @Test
  public void testClassImplementsForwardReferencedInterface() {
    newTest()
        .addSource(
            "/** @const */ var ns = {};",
            "(function() {",
            "  /** @interface */",
            "  ns.Base = class {};",
            "})();",
            "/** @implements {ns.Base} */",
            "class Sub {}",
            "var /** !ns.Base */ x = new Sub();")
        .run();
  }

  @Test
  public void testClassSuperCallResult() {
    newTest()
        .addSource(
            "class Bar {}",
            "class Foo extends Bar {",
            "  constructor() {",
            "    var /** null */ x = super();",
            "  }",
            "}")
        // TODO(sdh): This should probably infer Foo, rather than Bar?
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : Bar",
                "required: null"))
        .run();
  }

  @Test
  public void testClassComputedSymbolPropAllowed() {
    newTest()
        .addExterns(new TestExternsBuilder().addIterable().build())
        .addSource("class Foo { [Symbol.iterator]() {} }")
        .run();
  }

  @Test
  public void testClassExtendsNonNativeObject() {
    // This is a weird thing to do but should not crash the compiler.
    newTest()
        .addSource(
            "class Object {}",
            "class Foo extends Object {",
            "  /** @param {string} msg */",
            "  constructor(msg) {",
            "    super();",
            "    this.msg = msg;",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "attempted re-definition of type Object",
                "found   : (typeof Object)",
                "expected: (typeof Object)"))
        .run();
  }

  @Test
  public void testAsyncFunctionWithoutJSDoc() {
    newTest().addSource("async function f() { return 3; }").run();
  }

  @Test
  public void testAsyncFunctionInferredToReturnPromise_noExplicitReturns() {
    newTest()
        .addSource("async function f() {} var /** null */ n = f();")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : Promise<undefined>",
                "required: null"))
        .run();
  }

  @Test
  public void testAsyncFunctionInferredToReturnPromise_withExplicitReturns() {
    newTest()
        .addSource("async function f() { return 3; } var /** null */ n = f();")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : Promise<?>",
                "required: null"))
        .run();
  }

  @Test
  public void testAsyncFunction_cannotDeclareReturnToBe_Number() {
    newTest()
        .addSource("/** @return {number} */ async function f() {}")
        .addDiagnostic(
            lines(
                "The return type of an async function must be a supertype of Promise",
                "found: number"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAsyncFunction_cannotDeclareReturnToBe_Array() {
    newTest()
        .addSource("/** @return {!Array} */ async function f() {}")
        .addDiagnostic(
            lines(
                "The return type of an async function must be a supertype of Promise",
                "found: Array"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAsyncFunction_canDeclareReturnToBe_Object_andAccepts_undefined() {
    newTest()
        .addSource("/** @return {!Object} */ async function f() { return undefined; }")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAsyncFunction_canDeclareReturnToBe_allType_andAccepts_undefined() {
    newTest()
        .addSource("/** @return {*} */ async function f() { return undefined; }")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAsyncReturnsPromise1() {
    newTest()
        .addSource(
            "/** @return {!Promise<number>} */",
            "async function getANumber() {",
            "  return 1;",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAsyncReturnsPromise2() {
    newTest()
        .addSource(
            "/** @return {!Promise<string>} */",
            "async function getAString() {",
            "  return 1;",
            "}")
        .addDiagnostic(
            lines(
                "inconsistent return type",
                "found   : number",
                "required: (IThenable<string>|string)"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAsyncFunction_canDeclareReturnToBe_nullablePromise() {
    newTest()
        .addSource(
            "/** @return {?Promise<string>} */",
            "async function getAString() {",
            "  return '';",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAsyncFunction_canDeclareReturnToBe_unionOfPromiseAndNumber() {
    newTest()
        .addSource(
            "/** @return {(number|!Promise<number>)} */",
            "async function getAString() {",
            "  return 1;",
            "}")
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testSuperclassMixinDoesntCollideWithAnotherScope() {
    newTest()
        .addSource(
            "class ParentOne {}",
            "class ParentTwo {}",
            "function fn(x) {",
            "  let klass;",
            "  if (x) {",
            "    /** @constructor @extends {ParentOne} */",
            "    let templatizedBase = SomeVar;",
            "    klass = class TE extends templatizedBase {};",
            "  } else {",
            "    /** @constructor @extends {ParentTwo} */",
            "    let templatizedBase = OtherVar;",
            "    klass = class TY extends templatizedBase {};",
            "  }",
            "}")
        // TODO(b/140735194): stop reporting this error, and either ban this pattern of reassigning
        // klass outright or make it work as expected.
        .addDiagnostic(
            lines(
                "mismatch in declaration of superclass type",
                "found   : templatizedBase",
                "required: templatizedBase"))
        .run();
  }

  @Test
  public void testAsyncFunction_cannotDeclareReturnToBe_aSubtypeOfPromise() {
    newTest()
        .addSource(
            "/** @extends {Promise<string>} */",
            "class MyPromise extends Promise { }",
            "",
            "/** @return {!MyPromise} */",
            "async function getAString() {",
            "  return '';",
            "}")
        .addDiagnostic(
            lines(
                "The return type of an async function must be a supertype of Promise",
                "found: MyPromise"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAsyncFunction_cannotDeclareReturnToBe_aSiblingOfPromise() {
    newTest()
        .addSource(
            "/**",
            " * @interface",
            " * @extends {IThenable<string>}",
            " */",
            "class MyThenable { }",
            "",
            "/** @return {!MyThenable} */",
            "async function getAString() {",
            "  return '';",
            "}")
        .addDiagnostic(
            lines(
                "The return type of an async function must be a supertype of Promise",
                "found: MyThenable"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAsyncFunction_canDeclareReturnToBe_IThenable1() {
    newTest()
        .addSource(
            "/** @return {!IThenable<string>} */",
            "async function getAString() {",
            "  return 1;",
            "}")
        .addDiagnostic(
            lines(
                "inconsistent return type",
                "found   : number",
                "required: (IThenable<string>|string)"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAsyncFunction_checksReturnExpressionType_againstCorrectUpperBound() {
    newTest()
        .includeDefaultExterns()
        .addSource(
            "/** @return {string|!IThenable<boolean|undefined>|!Promise<null>} */",
            "async function getAString() {",
            "  return {};",
            "}")
        .addDiagnostic(
            lines(
                "inconsistent return type",
                "found   : {}",
                // We're specifically checking this type.
                "required: (IThenable<(boolean|null|undefined)>|boolean|null|undefined)"))
        .run();
  }

  @Test
  public void testAsyncReturnStatementIsResolved() {
    // Test that we correctly handle resolving an "IThenable" return statement inside an async
    // function.
    newTest()
        .addSource(
            "/** @return {!IThenable<string>} */",
            "async function getAString(/** !IThenable<number> */ iThenable) {",
            "  return iThenable;",
            "}")
        .addDiagnostic(
            lines(
                "inconsistent return type",
                "found   : IThenable<number>",
                "required: (IThenable<string>|string)"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testAwaitPromiseOfNumber1() {
    newTest()
        .addSource(
            "function takesNumber(/** number*/ num) {}",
            "",
            "async function f(/** !Promise<number> */ p) {",
            "  takesNumber(await p);",
            "}")
        .run();
  }

  @Test
  public void testAwaitPromiseOfNumber2() {
    newTest()
        .addSource(
            "function takesNumber(/** number*/ num) {}",
            "",
            "async function f(/** !Promise<string> */ p) {",
            "  takesNumber(await p);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesNumber does not match formal parameter",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testAwaitPromiseOfPromise() {
    // TODO(lharker): forbid this annotation, since it is impossible for a Promise to resolve to a
    // Promise.
    newTest()
        .addSource(
            "function takesNumber(/** number*/ num) {}",
            "",
            "async function f(/** !Promise<!Promise<number>> */ p) {",
            "  takesNumber(await p);",
            "}")
        .run();
  }

  @Test
  public void testAwaitPromiseOfUnknown() {
    newTest()
        .addSource(
            "function takesNumber(/** number*/ num) {}",
            "",
            "async function f(/** !Promise<?> */ p) {",
            "  takesNumber(await p);",
            "}")
        .run();
  }

  @Test
  public void testAwaitIThenable() {
    newTest()
        .addSource(
            "function takesNumber(/** number*/ num) {}",
            "",
            "async function f(/** !IThenable<string> */ p) {",
            "  takesNumber(await p);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesNumber does not match formal parameter",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testAwaitNumber() {
    newTest()
        .addSource(
            "function takesNumber(/** number*/ num) {}",
            "",
            "async function f(/** string */ str) {",
            "  takesNumber(await str);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesNumber does not match formal parameter",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testAwaitDoesTypeInferenceWithin() {
    newTest()
        .addSource(
            "function takesNumber(/** number*/ num) {}",
            "",
            "async function f() {",
            "  var x = 1;",
            "  await (x = 'some string');", // test we recognize that "x" is now a string.
            "  takesNumber(x);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesNumber does not match formal parameter",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testAwaitUnionType1() {
    newTest()
        .addSource(
            "function takesNumber(/** number*/ num) {}",
            "",
            "async function f(/** (number|!Promise<number>) */ param) {",
            "  takesNumber(await param);",
            "}")
        .run();
  }

  @Test
  public void testAwaitUnionType2() {
    newTest()
        .addSource(
            "function takesNumber(/** number*/ num) {}",
            "",
            "async function f(/** (string|!Promise<number>) */ param) {",
            "  takesNumber(await param);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesNumber does not match formal parameter",
                "found   : (number|string)",
                "required: number"))
        .run();
  }

  @Test
  public void testAwaitUnionType3() {
    newTest()
        .addSource(
            "function takesNumber(/** number*/ num) {}",
            "",
            "async function f(/** (number|!Promise<string>) */ param) {",
            "  takesNumber(await param);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesNumber does not match formal parameter",
                "found   : (number|string)",
                "required: number"))
        .run();
  }

  @Test
  public void testAwaitUnionOfPromiseAndIThenable() {
    newTest()
        .addSource(
            "function takesNumber(/** number*/ num) {}",
            "",
            "async function f(/** (!IThenable<number>|!Promise<string>) */ param) {",
            "  takesNumber(await param);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesNumber does not match formal parameter",
                "found   : (number|string)",
                "required: number"))
        .run();
  }

  @Test
  public void testAwaitNullableIThenable() {
    // We treat "?IThenable" the same as any other union type
    newTest()
        .addSource(
            "function takesNumber(/** number */ n) {}",
            "",
            "async function main(/** ?IThenable<number> */ iThenable) {",
            "  takesNumber(await iThenable);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesNumber does not match formal parameter",
                "found   : (null|number)",
                "required: number"))
        .run();
  }

  @Test
  public void testAwaitThenable() {
    // awaiting something with a .then property that does not implement IThenable results in the
    // unknown type. This matches the behavior of IThenable.then(...)
    // Thus the call to takesNumber below doesn't cause a type error, although at runtime
    // `await thenable` evaluates to `thenable`, since `thenable.then` is not a function.
    newTest()
        .addSource(
            "function takesNumber(/** number */ n) {}",
            "",
            "async function f(/** {then: string} */ thenable) {",
            "  takesNumber(await thenable);",
            "}")
        .run();
  }

  @Test
  public void testDefaultParameterWithCorrectType() {
    newTest().addSource("function f(/** number= */ n = 3) {}").run();
  }

  @Test
  public void testDefaultParameterWithWrongType() {
    newTest()
        .addSource("function f(/** number= */ n = 'foo') {}")
        .addDiagnostic(
            lines(
                "default value has wrong type", "found   : string", "required: (number|undefined)"))
        .run();
  }

  @Test
  public void testDefaultParameterIsUndefined() {
    newTest().addSource("function f(/** number= */ n = undefined) {}").run();
  }

  @Test
  public void testDefaultParameter_IsVariableTypedAsUndefined() {
    newTest()
        .addSource(
            "const alsoUndefined = undefined;",
            "",
            "/** @param {string=} str */",
            "function f(str = alsoUndefined) {}")
        .run();
  }

  @Test
  public void testDefaultParameter_IsKnownNotUndefinedInClosure() {
    // TODO(b/117162687): treat `str` as having declared type `string`, which will remove the
    // spurious warning here.
    newTest()
        .addSource(
            "function takesString(/** string */ str) {}",
            "",
            "/** @param {string=} str */",
            "function f(str = '') {",
            "  return () => takesString(str);",
            "}")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesString does not match formal parameter",
                "found   : (string|undefined)",
                "required: string"))
        .run();
  }

  @Test
  public void testDefaultParameterInDestructuringIsUndefined() {
    newTest()
        .addSource(
            "/** @param {{prop: (string|undefined)}} obj */", "function f({prop = undefined}) {}")
        .run();
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
    newTest()
        .addSource(
            "const alsoUndefined = undefined;",
            "",
            "/** @param {{prop: (string|undefined)}} obj */",
            "function f({prop = alsoUndefined}) {}")
        .run();
  }

  @Test
  public void testTypeCheckingOccursWithinDefaultParameter() {
    newTest()
        .addSource("let /** number */ age = 0; function f(x = age = 'foo') {}")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testDefaultParameterWithTypeInferredFromCallback() {
    newTest()
        .addSource(
            "function f(/** function(number=) */ callback) {}",
            "",
            "f((x = 3) => {",
            "  var /** number */ y = x;",
            "})")
        .run();
  }

  @Test
  public void testDefaultParameterInIifeWithInferredType() {
    newTest()
        .addSource(
            "var /** string|undefined */ stringOrUndefined;",
            "(function f(x = 3) {",
            "  var /** string */ str = x;",
            "})(stringOrUndefined);")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : (number|string)",
                "required: string"))
        .run();
  }

  @Test
  public void testDefaultParameterWithNoJSDocTreatedAsOptional() {
    newTest().addSource("function f(a = 3) {} f();").run();
  }

  @Test
  public void testOverridingMethodHasDefaultParameterInPlaceOfRequiredParam() {
    newTest()
        .addSource(
            "class Parent {",
            "  /** @param {number} num */",
            "  f(num) {}",
            "}",
            "class Child extends Parent {",
            "  /** @override */",
            "  f(num = undefined) {}",
            "}",
            "(new Child()).f();",
            "(new Child()).f(undefined);")
        .run();
  }

  @Test
  public void testOverridingMethodAddsOptionalParameterWithDefaultValue() {
    newTest()
        .addSource(
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
            "(new Child()).f(3, undefined);")
        .run();
  }

  @Test
  public void testOptionalDestructuringParameterWithoutDefaultValue() {
    newTest().addSource("/** @param {{x: number}=} opts */ function f({x}) {} f();").run();
  }

  @Test
  public void testBasicArrayPatternDeclaration() {
    newTest()
        .addSource(
            "function f(/** !Iterable<number> */ numbers) {",
            "  const [/** string */ str] = numbers;",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testNestedDestructuringPatternDeclaration() {
    newTest()
        .addSource(
            "function f(/** !Iterable<{x: number}> */ xNumberObjs) {",
            "  const [{/** string */ x}] = xNumberObjs;",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testArrayPatternDeclarationWithElision() {
    newTest()
        .addSource(
            "function f(/** !Iterable<number> */ numbers) {",
            "  const [, /** number */ x, , /** string */ y] = numbers;",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testBasicArrayPatternAssign() {
    newTest()
        .addSource(
            "function f(/** !Iterable<number> */ numbers) {",
            "  var /** string */ str;",
            "  [str] = numbers;",
            "}")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testNestedDestructuringPatternAssign() {
    newTest()
        .addSource(
            "function f(/** !Iterable<{x: number}> */ xNumberObjs) {",
            "  var /** string */ x;",
            "  [{x}] = xNumberObjs;",
            "}")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testValidArrayPatternInForOfInitializer() {
    newTest()
        .addSource(
            "function f(/** !Iterable<!Iterable<number>> */ numberLists) {",
            "  for (const [/** number */ x, /** number */ y] of numberLists) {}",
            "}")
        .run();
  }

  @Test
  public void testArrayPatternInForOfInitializerWithTypeMismatch() {
    newTest()
        .addSource(
            "function f(/** !Iterable<!Iterable<number>> */ numberLists) {",
            "  for (const [/** number */ x, /** string */ y] of numberLists) {}",
            "}")
        .addDiagnostic(
            lines(
                "declared type of for-of loop variable does not match inferred type",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testValidArrayPatternInForInInitializer() {
    newTest()
        .addExterns(new TestExternsBuilder().addString().build())
        .addSource(
            "function f(/** !Object<string, number> */ obj) {",
            "  for (const [/** string */ a, /** string */ b] in obj) {}",
            "}")
        .run();
  }

  @Test
  public void testArrayPatternInForInInitializerWithTypeMismatch() {
    // TODO(b/77903996): this should cause a type mismatch warning
    newTest()
        .addExterns(new TestExternsBuilder().addString().build())
        .addSource(
            "function f(/** !Object<string, number> */ obj) {",
            "  for (const [/** number */ a, /** number */ b] in obj) {}",
            "}")
        .run();
  }

  @Test
  public void testBadDefaultValueInCatchCausesWarning() {
    newTest()
        .addSource("try { throw {x: undefined}; } catch ({/** string */ x = 3 + 4}) {}")
        .addDiagnostic(
            lines(
                "default value has wrong type", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testCannotAliasEnumThroughDestructuring() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource(
            "/** @enum {number} */ const THINGS = {THING1: 1, THING2: 2};",
            // TODO(lharker): warn for putting @enum here
            "/** @enum */ const [OTHERTHINGS] = [THINGS];")
        .run();
  }

  @Test
  public void testArrayPatternAssign_badInterfacePropertyCreation() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource("/** @interface */ function Foo() {}; [Foo.prototype.bar] = [];")
        .addDiagnostic(
            "interface members can only be "
                + "empty property declarations, empty functions, or goog.abstractMethod")
        .run();
  }

  @Test
  public void testArrayPatternAssign_badPropertyAssignment() {
    newTest()
        .addSource(
            "/** @param {!Iterable<number>} numbers */",
            "function f(numbers) {",
            "  const /** {a: string} */ obj = {a: 'foo'};",
            "  [obj.a] = numbers;",
            "}")
        .addDiagnostic(
            lines(
                "assignment to property a of obj", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testBadComputedPropertyKeyTypeInObjectPattern() {
    newTest()
        .addSource("const {[{}]: x} = {};")
        .addDiagnostic(
            lines(
                "property access", //
                "found   : {}",
                "required: (string|symbol)"))
        .run();
  }

  @Test
  public void testComputedPropertyAccessOnStructInObjectPattern() {
    newTest()
        .addSource("/** @struct */ const myStruct = {a: 1}; const {['a']: a} = myStruct;")
        .addDiagnostic("Cannot do '[]' access on a struct")
        .run();
  }

  @Test
  public void testQuotedPropertyAccessOnStructInObjectPattern() {
    newTest()
        .addSource("/** @struct */ const myStruct = {a: 1}; const {'a': a} = myStruct;")
        .addDiagnostic("Cannot do quoted access on a struct")
        .run();
  }

  @Test
  public void testNonQuotedPropertyAccessOnDictInObjectPattern() {
    newTest()
        .addSource("/** @dict*/ const myDict = {'a': 1}; const {a} = myDict;")
        .addDiagnostic("Cannot do unquoted access on a dict")
        .run();
  }

  @Test
  public void testRestrictedIndexTypeInComputedPropertyKeyInObjectPattern() {
    newTest()
        .addSource(
            "const /** !Object<number, number> */ obj = {3: 3, 4: 4};",
            "const {['string']: x} = obj;")
        .addDiagnostic(
            lines(
                "restricted index type", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testObjectDestructuringNullInDeclarationCausesWarning() {
    newTest()
        .addSource("const {} = null;")
        .addDiagnostic(
            lines(
                "cannot destructure 'null' or 'undefined'", //
                "found   : null",
                "required: Object"))
        .run();
  }

  @Test
  public void testObjectDestructuringUndefinedInDeclarationCausesWarning() {
    newTest()
        .addSource("const {} = undefined;")
        .addDiagnostic(
            lines(
                "cannot destructure 'null' or 'undefined'",
                "found   : undefined",
                "required: Object"))
        .run();
  }

  @Test
  public void testObjectDestructuringBoxablePrimitiveInDeclarationIsAllowed() {
    newTest().addSource("const {} = 0;").run();
  }

  @Test
  public void testObjectDestructuringNullInParametersCausesWarning() {
    newTest()
        .addSource("/** @param {null} obj */ function f({}) {}")
        .addDiagnostic(
            lines(
                "cannot destructure 'null' or 'undefined'", //
                "found   : null",
                "required: Object"))
        .run();
  }

  @Test
  public void testObjectDestructuringNullInNestedPatternCausesWarning() {
    newTest()
        .addSource("const {a: {}} = {a: null};")
        .addDiagnostic(
            lines(
                "cannot destructure 'null' or 'undefined'", //
                "found   : null",
                "required: Object"))
        .run();
  }

  @Test
  public void testObjectDestructuringNullableDoesntCauseWarning() {
    // Test that we don't get a "cannot destructure 'null' or 'undefined'" warning, which matches
    // the legacy behavior when typechecking transpiled destructuring patterns.
    newTest()
        .addSource(
            "function f(/** ?{x: number} */ nullableObj) {", //
            "const {x} = nullableObj;",
            "}")
        .run();
  }

  @Test
  public void testArrayDestructuringNonIterableCausesWarning() {
    newTest()
        .addSource("const [] = 3;")
        .addDiagnostic(
            lines(
                "array pattern destructuring requires an Iterable",
                "found   : number",
                "required: Iterable"))
        .run();
  }

  @Test
  public void testArrayDestructuringStringIsAllowed() {
    newTest()
        .addExterns(new TestExternsBuilder().addString().build())
        .addSource("const [] = 'foobar';")
        .run(); // strings autobox to Strings, which implement Iterable
  }

  @Test
  public void testArrayDestructuringNonIterableInParametersCausesWarning() {
    newTest()
        .addSource("/** @param {number} arr */ function f([]) {}")
        .addDiagnostic(
            lines(
                "array pattern destructuring requires an Iterable",
                "found   : number",
                "required: Iterable"))
        .run();
  }

  @Test
  public void testArrayDestructuringNonIterableInForOfLoopCausesWarning() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource("const /** !Iterable<number> */ iter = [0]; for (const [] of iter) {}")
        .addDiagnostic(
            lines(
                "array pattern destructuring requires an Iterable",
                "found   : number",
                "required: Iterable"))
        .run();
  }

  @Test
  public void testBadDefaultValueInArrayPatternCausesWarning() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource("const [/** string */ foo = 0] = [];")
        .addDiagnostic(
            lines(
                "default value has wrong type", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testDefaultValueForNestedArrayPatternMustBeIterable() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource("const [[] = 0] = [];")
        .addDiagnostic(
            lines(
                "array pattern destructuring requires an Iterable",
                "found   : number",
                "required: Iterable"))
        .run();
  }

  @Test
  public void testArrayPatternParameterCanBeOptional() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource("/** @param {!Array<string>=} arr */ function f([x, y] = []) {}")
        .run();
  }

  @Test
  public void testDefaultValueForArrayPatternParameterMustBeIterable() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource("function f([] = 0) {}")
        .addDiagnostic(
            lines(
                "array pattern destructuring requires an Iterable",
                "found   : number",
                "required: Iterable"))
        .run();
  }

  @Test
  public void testDefaultValueForNestedObjectPatternMustNotBeNull() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource("const [{} = null] = [];")
        .addDiagnostic(
            lines(
                "cannot destructure a 'null' or 'undefined' default value",
                "found   : null",
                "required: Object"))
        .run();
  }

  @Test
  public void testDefaultValueForObjectPatternParameterMustNotBeNull() {
    newTest()
        .addSource("function f({} = null) {}")
        .addDiagnostic(
            lines(
                "cannot destructure a 'null' or 'undefined' default value",
                "found   : null",
                "required: Object"))
        .run();
  }

  @Test
  public void testArrayDestructuringParameterWithElision() {
    newTest()
        .addExterns(new TestExternsBuilder().addArray().build())
        .addSource("/** @param {!Array<number>} numbers */ function f([, x, , y]) {}")
        .run();
  }

  @Test
  public void testObjectPatternDeclarationWithMissingPropertyWarning() {
    newTest()
        .addSource(
            "function f(/** {a: number} */ obj) {", //
            "  const {a, b} = obj;",
            "}")
        .addDiagnostic("Property b never defined on obj")
        .run();
  }

  @Test
  public void testObjectPatternDoesntCheckMissingPropertyForQuotedStringKey() {
    newTest()
        .addSource(
            "function f(/** {a: number} */ obj) {", //
            "  const {a, 'b': b} = obj;",
            "}")
        .run();
  }

  @Test
  public void testObjectPatternAssignWithMissingPropertyWarning() {
    newTest()
        .addSource(
            "function f(/** {a: number} */ obj) {", //
            "  let a, b;",
            "  ({a, b} = obj);",
            "}")
        .addDiagnostic("Property b never defined on obj")
        .run();
  }

  @Test
  public void testObjectPatternDeclarationWithMissingPropertyWarningInForOf() {
    newTest()
        .addSource(
            "function f(/** !Iterable<{a: number}> */ aNumberObj) {",
            "  for (const {a, b} of aNumberObj) {}",
            "}")
        .addDiagnostic("Property b never defined on {a: number}")
        .run();
  }

  @Test
  public void testForAwaitOfWithDestructuring() {
    newTest()
        .addSource(
            "async function f(/** !Iterable<Promise<{a: number}>> */ o) {",
            "  for await (const {a, b} of o) {}",
            "}")
        .addDiagnostic("Property b never defined on {a: number}")
        .run();
  }

  @Test
  public void testObjectPatternWithMissingPropertyWarningInParameters() {
    newTest()
        .addSource(
            "/** @param {{a: number}} obj */", //
            "function f(/** {a: number} */ {b}) {}")
        .addDiagnostic("Property b never defined on {a: number}")
        .run();
  }

  @Test
  public void testArrayPatternAssignWithIllegalPropCreationInStruct() {
    newTest()
        .addSource(
            "class C {", "  f(/** !Iterable<number> */ ) {", "    [this.x] = arr;", "  }", "}")
        .addDiagnostic(
            "Cannot add a property to a struct instance after it is constructed. "
                + "(If you already declared the property, make sure to give it a type.)")
        .addDiagnostic("Property x never defined on C")
        .run();
  }

  @Test
  public void testTypedefAliasedThroughDestructuringFromLegacyNamespacePassesTypechecking() {
    newTest()
        .addSource(
            "/** @typedef {number} */",
            "let TypeOriginal;",
            "class clientOpClass {}",
            "",
            "const clientOp = clientOpClass;",
            "/** @const */",
            "clientOp.Type = TypeOriginal;",
            // The above pattern mimics some goog.module.declareLegacyNamespace() code
            "",
            "const {Type} = clientOp;",
            "class C {",
            "  /** @param {!Type} type */",
            "  m(type) {",
            "    type = 'cause a type error';",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testEnumAliasedThroughDestructuringPassesTypechecking() {
    newTest()
        .addSource(
            "const ns = {};",
            "/** @enum {number} */",
            "ns.myEnum = {FOO: 1, BAR: 2};",
            "",
            "const {myEnum} = ns;",
            "const /** myEnum */ n = myEnum.FOO;")
        .run();
  }

  @Test
  public void testEnumAliasedThroughDestructuringReportsCorrectMissingPropWarning() {
    newTest()
        .addSource(
            "const ns = {};",
            "/** @enum {number} */",
            "ns.myEnum = {FOO: 1, BAR: 2};",
            "",
            "const {myEnum} = ns;",
            "const missing = myEnum.MISSING;")
        .addDiagnostic("element MISSING does not exist on this enum")
        .run();
  }

  @Test
  public void testAnnotatedObjectLiteralInDefaultParameterInitializer() {
    // Default parameter initializers need to handle defining object literals with annotated
    // function members.
    newTest()
        .addSource(
            "/** @param {{g: function(number): undefined}=} x */",
            "function f(x = {/** @param {string} x */ g(x) {}}) {}")
        .addDiagnostic(
            lines(
                "default value has wrong type",
                "found   : {g: function(string): undefined}",
                "required: (undefined|{g: function(number): undefined})",
                "missing : []",
                "mismatch: [g]"))
        .run();
  }

  @Test
  public void testDictClass1() {
    newTest().addSource("/** @dict */ var C = class { constructor() {} 'x'(){} };").run();
  }

  @Test
  public void testTypeCheckingOverriddenGetterFromSuperclass() {
    newTest()
        .addSource(
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
            "var /** string */ x = (new Baz).num;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testTypeCheckingOverriddenGetterFromSuperclassWithBadReturnType() {
    newTest()
        .addSource(
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
            "}")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testGetterOverridesPrototypePropertyFromInterface() {
    newTest()
        .addSource(
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
            "var /** string */ x = (new Baz).num;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testGetterOverridesInstancePropertyFromInterface() {
    newTest()
        .addSource(
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
            "var /** string */ x = (new Baz).num;")
        .run();
  }

  @Test
  public void testOverriddenSetterFromSuperclass() {
    newTest()
        .addSource(
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
            "(new Baz).num = 'foo';")
        .addDiagnostic(
            lines(
                "assignment to property num of Baz", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testGetterOverridesMethod() {
    // If a getter overrides a method, we infer the getter to be for a function type
    newTest()
        .addSource(
            "class Bar {",
            "  /** @return {number} */",
            "  num() { return 1; }",
            "}",
            "/** @extends {Bar} */",
            "class Baz extends Bar {",
            "  /** @override */",
            "  get num() { return 1; }",
            "}")
        .addDiagnostic(
            lines(
                "inconsistent return type",
                "found   : number",
                "required: function(this:Bar): number"))
        .run();
  }

  @Test
  public void testMisplacedOverrideOnGetter() {
    newTest()
        .addSource(
            "/** @abstract */",
            "class Bar {}",
            "/** @extends {Bar} */",
            "class Baz extends Bar {",
            "  /** @override */",
            "  get num() { return 3; }",
            "}",
            "var /** string */ x = (new Baz).num;")
        .addDiagnostic("property num not defined on any superclass of Baz")
        .run();
  }

  @Test
  public void testOverridingNonMethodWithMethodDoesntBlockTypeCheckingInsideMethod() {
    // verify that we still type Bar.prototype.bar with function(this:Bar, number) even though it
    // overrides a property from Foo
    // thus we get both a "mismatch of ... and the property it overrides" warning
    // and a warning for "initializing variable ..." inside bar()
    newTest()
        .addSource(
            "class Foo {}",
            "/** @type {number} */",
            "Foo.prototype.bar = 3;",
            "",
            "class Bar extends Foo {",
            "  /** @override */",
            "  bar(/** number */ n) {",
            "    var /** string */ str = n;",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "mismatch of the bar property type "
                    + "and the type of the property it overrides from superclass Foo",
                "original: number",
                "override: function(this:Bar, number): undefined"))
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testGetterWithTemplateTypeReturnIsTypeChecked() {
    newTest()
        .addSource(
            "/** @interface @template T */",
            "class C {",
            "  /** @return {T} */",
            "  get t() {}",
            "}",
            "/** @implements {C<string>} */",
            "class CString {",
            "  /** @override */",
            "  get t() { return 3; }", // inconsistent return type
            "}")
        .addDiagnostic(
            lines(
                "inconsistent return type", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testStubMethodDeclarationDoesntBlockTypecheckingOfGetter() {
    newTest()
        .addSource(
            "/** @interface */",
            "class Foo {}",
            "/** @return {number} */",
            "Foo.prototype.num;",
            "/** @implements {Foo} */",
            "class Bar {",
            "  /** @override */",
            "  get num() { return 1; }",
            "}",
            "var /** string */ x = (new Bar).num;")
        .addDiagnostic(
            lines(
                "inconsistent return type",
                "found   : number",
                "required: function(this:Foo): number"))
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : function(this:Foo): number",
                "required: string"))
        .run();
  }

  @Test
  public void testOverrideSupertypeOnAnonymousClass() {
    // Test that we infer the supertype of a class not assigned to an lvalue
    newTest()
        .addSource(
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
            "});")
        .addDiagnostic(
            lines(
                "assignment to property str of <anonymous@[testcode]:9>",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testOverrideSupertypeOnClassExpression() {
    // Test that we infer the supertype of a class not assigned to an lvalue
    newTest()
        .addSource(
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
            "});")
        .addDiagnostic(
            lines(
                "assignment to property str of <anonymous@[testcode]:9>",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testOverrideInferredOnClassExpression() {
    // Test that we infer the type of overridden methods even on classes not assigned to an lvalue
    newTest()
        .addSource(
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
            "});")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testMixinWithUnknownTemplatedSupertypeDoesntCauseWarning() {
    // Although in general we warn when we can't resolve the superclass type in an extends clause,
    // we allow this when the superclass type is a template type in order to support mixins.
    newTest()
        .addSource(
            "/**",
            " * @template T",
            " * @param {function(new:T)} superClass",
            " */",
            "function mixin(superClass) {",
            "  class Changed extends superClass {}",
            "}")
        .run();
  }

  @Test
  public void testMixinImplementingInterfaceAndUnknownTemplatedSuperclass() {
    newTest()
        .addSource(
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
            "}")
        .run();
  }

  @Test
  public void testGlobalAliasOfEnumIsNonNullable() {
    newTest()
        .addSource(
            "class Foo {}",
            "/** @enum {number} */",
            "Foo.E = {A: 1};",
            "const E = Foo.E;",
            "/** @type {E} */ let e = undefined;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : undefined",
                "required: Foo.E<number>"))
        .run();
  }

  @Test
  public void testTypeNameAliasOnAliasedNamespace() {
    newTest()
        .addSource(
            "class Foo {}",
            "/** @enum {number} */",
            "Foo.E = {A: 1};",
            "const F = Foo;",
            "const E = F.E;",
            "/** @type {E} */ let e = undefined;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : undefined",
                "required: Foo.E<number>"))
        .run();
  }

  @Test
  public void testTypeNamePropertyOnAliasedNamespace() {
    newTest()
        .addSource(
            "class Foo {}",
            "/** @enum {number} */",
            "Foo.E = {A: 1};",
            "const F = Foo;",
            "/** @type {F.E} */ let e = undefined;")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : undefined",
                // TODO(b/116853368): this should be non-null
                "required: (Foo.E<number>|null)"))
        .run();
  }

  @Test
  public void testTypedefNameAliasOnAliasedNamespace() {
    newTest()
        .addSource(
            "class Foo {}",
            "/** @typedef {number|string} */",
            "Foo.E;",
            "const F = Foo;",
            "const E = F.E;",
            "/** @type {E} */ let e = undefined;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : undefined",
                "required: (number|string)"))
        .run();
  }

  @Test
  public void testTypeNameAliasOnAliasedClassSideNamespace() {
    newTest()
        .addSource(
            "class Foo {}",
            "/** @enum {number} */ Foo.E = {A: 1};",
            "class Bar extends Foo {};",
            "const B = Bar;",
            "const E = B.E;",
            "/** @type {E} */ let e = undefined;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : undefined",
                "required: Foo.E<number>"))
        .run();
  }

  @Test
  public void testForwardDeclaredGlobalAliasOfEnumIsNonNullable_constDeclaration() {
    newTest()
        .addSource(
            "/** @enum {string} */",
            "const Colors = {RED: 'red', YELLOW: 'yellow'};",
            "const /** ColorsAlias */ c = null",
            "const ColorsAlias = Colors;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : null",
                "required: Colors<string>"))
        .run();
  }

  @Test
  public void testForwardDeclaredGlobalAliasOfEnumIsNonNullable_constJSDoc() {
    newTest()
        .addSource(
            "/** @enum {string} */",
            "const Colors = {RED: 'red', YELLOW: 'yellow'};",
            "const /** ns.ColorsAlias */ c = null",
            "const ns = {};",
            "/** @const */ ns.ColorsAlias = Colors;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : null",
                "required: Colors<string>"))
        .run();
  }

  @Test
  public void testLocalEnumDoesNotInfluenceGlobalDefaultNullablity() {
    newTest()
        .addSource(
            "class Foo {};",
            "function f() {",
            "  /** @enum {number} */ const Foo = {A: 1};",
            "}",
            "/** @type {Foo} */ let x = null;")
        .run();
  }

  @Test
  public void testGlobalEnumDoesNotInfluenceLocalDefaultNullablity() {
    newTest()
        .addSource(
            "/** @enum {number} */ const Foo = {A: 1};",
            "function f() {",
            "  class Foo {};",
            "  /** @type {Foo} */ let x = null;",
            "}")
        .run();
  }

  @Test
  public void testLocalEnumAliasDoesNotInfluenceGlobalDefaultNullablity() {
    newTest()
        .addSource(
            "class Foo {};",
            "/** @enum {number} */ const Bar = {A: 1};",
            "function f() {",
            "  const Foo = Bar;",
            "}",
            "/** @type {Foo} */ let x = null;")
        .run();
  }

  @Test
  public void testTypedefInExtern() {
    newTest()
        .addExterns("/** @typedef {boolean} */ var ConstrainBoolean;")
        .addSource("var /** ConstrainBoolean */ x = 42;")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : number",
                "required: boolean"))
        .includeDefaultExterns()
        .run();
  }

  @Test
  public void testDeeplyNestedAliases() {
    newTest()
        .addSource(
            "const ns = {};",
            "/** @typedef {number} */",
            "ns.MyNumber;",
            "const alias = {};",
            "/** @const */",
            "alias.child = ns;",
            "const outer = {};",
            "/** @const */",
            "outer.inner = alias;",
            "const /** outer.inner.child.MyNumber */ x = '';")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testAsyncGeneratorNoReturnOrYield() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "/** @return {!AsyncGenerator<?>} */", //
            "async function* asyncGen() {}")
        .run();
  }

  @Test
  public void testAsyncGeneratorDeclaredReturnMustBeSupertypeOfAsyncGenerator() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "/** @return {!AsyncIterator<?>} */", //
            "async function* asyncGen() {}")
        .run();

    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "/** @return {!AsyncIterable<?>} */", //
            "async function* asyncGen() {}")
        .run();

    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "/** @return {!AsyncIteratorIterable<?>} */", //
            "async function* asyncGen() {}")
        .run();

    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "/** @return {!Object} */", //
            "async function* asyncGen() {}")
        .run();

    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "/** @return {*} */", //
            "async function* asyncGen() {}")
        .run();

    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "/** @return {?} */", //
            "async function* asyncGen() {}")
        .run();

    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "/** @return {number} */", //
            "async function* asyncGen() {}")
        .addDiagnostic(
            lines(
                "An async generator function must return a (supertype of) AsyncGenerator",
                "found   : number",
                "required: AsyncGenerator"))
        .run();
  }

  @Test
  public void testAsyncGeneratorWithYield() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "/** @return {!AsyncGenerator<number>} */", "async function* asyncGen() { yield 0; }")
        .run();
  }

  @Test
  public void testAsyncGeneratorWithYieldStarOtherAsyncGenerator() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen0() { yield 0; }",
            "",
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen1() { yield* asyncGen0(); }")
        .run();
  }

  @Test
  public void testAsyncGeneratorYieldStarNonIterable() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            lines(
                "/** @return {!AsyncGenerator<number>} */",
                "async function* asyncGen() { yield* 0; }"))
        .addDiagnostic(
            lines(
                "Expression yield* expects an iterable or async iterable",
                "found   : number",
                "required: (AsyncIterator|Iterator)"))
        .run();
  }

  @Test
  public void testAsyncGeneratorYieldStarBoxableIterable() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().addString().build())
        .addSource(
            "let /** string */ boxable;",
            "/** @return {!AsyncGenerator<string>} */",
            "async function* asyncGen() { yield* 'boxable'; }")
        .run();

    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().addArray().build())
        .addSource(
            "let /** !Array<number> */ boxable;",
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen() { yield* boxable; }")
        .run();
  }

  @Test
  public void testAsyncGeneratorWithYieldStarSyncGenerator() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "/** @return {!Generator<number>} */",
            "function* gen() { yield 0; }",
            "",
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen() { yield* gen(); }")
        .run();
  }

  @Test
  public void testAsyncGeneratorWithYieldStarSyncAndAsyncGeneratorUnion() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "let /** !Generator<string>|!AsyncGenerator<number> */ gen;",
            "",
            "/** @return {!AsyncGenerator<string|number>} */",
            "async function* asyncGen() { yield* gen; }")
        .run();
  }

  @Test
  public void testAsyncGeneratorWithYieldStarSyncAndAsyncGeneratorAndNonGeneratorUnion() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "let /** !Generator<string>|!AsyncGenerator<number>|number */ gen;",
            "",
            "/** @return {!AsyncGenerator<string|number>} */",
            "async function* asyncGen() { yield* gen; }")
        .addDiagnostic(
            lines(
                "Expression yield* expects an iterable or async iterable",
                "found   : (AsyncGenerator<number,?,?>|Generator<string,?,?>|number)",
                "required: (AsyncIterator|Iterator)"))
        .run();
  }

  @Test
  public void testAsyncGeneratorWithYieldStarSyncAndAsyncGeneratorUnionMismatch() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "let /** !Generator<string>|!AsyncGenerator<number> */ gen;",
            "",
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen() { yield* gen; }")
        .addDiagnostic(
            lines(
                "Yielded type does not match declared return type.",
                "found   : (number|string)",
                "required: (IThenable<number>|number)"))
        .run();
  }

  @Test
  public void testAsyncGeneratorWithMismatchReturn() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen() { return 'str'; }")
        .addDiagnostic(
            lines(
                "inconsistent return type",
                "found   : string",
                "required: (IThenable<number>|number)"))
        .run();
  }

  @Test
  public void testAsyncGeneratorWithMismatchYield() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen() { yield 'str'; }")
        .addDiagnostic(
            lines(
                "Yielded type does not match declared return type.",
                "found   : string",
                "required: (IThenable<number>|number)"))
        .run();
  }

  @Test
  public void testAsyncGeneratorYieldAwaitNonThenable() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen() { yield await 0; }")
        .run();
  }

  @Test
  public void testAsyncGeneratorYieldAwaitNonThenableMismatch() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen() { yield await 'str'; }")
        .addDiagnostic(
            lines(
                "Yielded type does not match declared return type.",
                "found   : string",
                "required: (IThenable<number>|number)"))
        .run();
  }

  @Test
  public void testAsyncGeneratorYieldAwaitThenable() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "let /** !IThenable<number> */ thenable;",
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen() { yield await thenable; }")
        .run();
  }

  @Test
  public void testAsyncGeneratorYieldAwaitThenableMismatch() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "let /** !IThenable<string> */ thenable;",
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen() { yield await thenable; }")
        .addDiagnostic(
            lines(
                "Yielded type does not match declared return type.",
                "found   : string",
                "required: (IThenable<number>|number)"))
        .run();
  }

  @Test
  public void testAsyncGeneratorYieldAwaitPromise() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen() { yield await Promise.resolve(0); }")
        .run();
  }

  @Test
  public void testAsyncGeneratorYieldAwaitPromiseMismatch() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen() { yield await Promise.resolve('str'); }")
        .addDiagnostic(
            lines(
                "Yielded type does not match declared return type.",
                "found   : string",
                "required: (IThenable<number>|number)"))
        .run();
  }

  @Test
  public void testAsyncGeneratorYieldPromise() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen() { yield Promise.resolve(0); }")
        .run();
  }

  @Test
  public void testAsyncGeneratorYieldPromiseMismatch() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen() { yield Promise.resolve('str'); }")
        .addDiagnostic(
            lines(
                "Yielded type does not match declared return type.",
                "found   : Promise<string>",
                "required: (IThenable<number>|number)"))
        .run();
  }

  @Test
  public void testAsyncGeneratorYieldIThenable() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "let /** !IThenable<number> */ thenable;",
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen() { yield thenable; }")
        .run();
  }

  @Test
  public void testAsyncGeneratorYieldThenableMismatch() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "let /** !IThenable<string> */ thenable;",
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen() { yield thenable; }")
        .addDiagnostic(
            lines(
                "Yielded type does not match declared return type.",
                "found   : IThenable<string>",
                "required: (IThenable<number>|number)"))
        .run();
  }

  @Test
  public void testAsyncGeneratorYieldIThenableUnionNonIThenable() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "let /** (!IThenable<number>|string) */ thenableOrString;",
            "/** @return {!AsyncGenerator<number|string>} */",
            "async function* asyncGen() { yield thenable; }")
        .run();
  }

  @Test
  public void testAsyncGeneratorYieldIThenableUnionNonIThenableMismatch() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "let /** (!IThenable<number>|string) */ thenableOrString;",
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen() { yield thenableOrString; }")
        .addDiagnostic(
            lines(
                "Yielded type does not match declared return type.",
                "found   : (IThenable<number>|string)",
                "required: (IThenable<number>|number)"))
        .run();
  }

  @Test
  public void testAsyncGeneratorReturnNothing() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "/** @return {!AsyncGenerator<number>} */", "async function* asyncGen() { return; }")
        .run();
  }

  @Test
  public void testAsyncGeneratorReturnSameType() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "/** @return {!AsyncGenerator<number>} */", "async function* asyncGen() { return 0; }")
        .run();
  }

  @Test
  public void testAsyncGeneratorReturnMismatchType() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen() { return 'str'; }")
        .addDiagnostic(
            lines(
                "inconsistent return type",
                "found   : string",
                "required: (IThenable<number>|number)"))
        .run();
  }

  @Test
  public void testAsyncGeneratorReturnVoidPromise() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "let /** !Promise<void> */ voidPromise;",
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen() { return voidPromise; }")
        .addDiagnostic(
            lines(
                "inconsistent return type",
                "found   : Promise<undefined>",
                "required: (IThenable<number>|number)"))
        .run();
  }

  @Test
  public void testAsyncGeneratorReturnUndefinedPromise() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "let /** !Promise<undefined> */ undefPromise;",
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen() { return undefPromise; }")
        .addDiagnostic(
            lines(
                "inconsistent return type",
                "found   : Promise<undefined>",
                "required: (IThenable<number>|number)"))
        .run();
  }

  @Test
  public void testAsyncGeneratorReturnPromise() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "let /** !Promise<number> */ promise;",
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen() { return promise; }")
        .run();
  }

  @Test
  public void testAsyncGeneratorReturnPromiseMismatch() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "let /** !Promise<string> */ promise;",
            "/** @return {!AsyncGenerator<number>} */",
            "async function* asyncGen() { return promise; }")
        .addDiagnostic(
            lines(
                "inconsistent return type",
                "found   : Promise<string>",
                "required: (IThenable<number>|number)"))
        .run();
  }

  @Test
  public void testAsyncGeneratorFunctionInferredToBeAsyncGenerator() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "async function* asyncGen() { return 0; }",
            "let /** !AsyncGenerator<number> */ g = asyncGen();")
        .run();
  }

  @Test
  public void testAsyncGeneratorFunctionInferredToBeAsyncGeneratorTemplateMismatch() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            // TODO - there is no inference on return type for any functions, so should be
            // AsyncGenerator<?>
            "async function* asyncGen() { return 0; }",
            "let /** !AsyncGenerator<string> */ g = asyncGen();")
        .run();
  }

  @Test
  public void testAsyncGeneratorFunctionInferredToBeAsyncGeneratorMismatch() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "async function* asyncGen() { return 0; }", //
            "let /** null */ g = asyncGen();")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : AsyncGenerator<?,?,?>",
                "required: null"))
        .run();
  }

  @Test
  public void testObjectSpread_typedAsObject() {
    // TODO(b/128355893): Do smarter inferrence. There are a lot of potential issues with
    // inference on object-rest, so for now we just give up and say `Object`. In theory the LHS type
    // is correct.
    newTest()
        .addSource(
            "let obj = {a: 1, b: 'str'};",
            "let /** !{a: string, b: string, c: boolean} */ copy = {c: true, ...obj, a:"
                + " 'hello'};")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : Object",
                "required: {",
                "  a: string,",
                "  b: string,",
                "  c: boolean",
                "}",
                "missing : [a,b,c]",
                "mismatch: []"))
        .run();
  }

  @Test
  public void testForAwaitOfNonIterable() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "async function foo() {", //
            "  for await (const n of 0) {",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "Can only async iterate over a (non-null) Iterable or AsyncIterable type",
                "found   : number",
                "required: (AsyncIterator|Iterator)"))
        .run();
  }

  @Test
  public void testForAwaitOfAsyncIterator() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "let /** !AsyncIterable<number> */ gen;",
            "async function foo() {",
            "  for await (const /** number */ n of gen) {",
            "  }",
            "}")
        .run();
  }

  @Test
  public void testForAwaitOfNullableAsyncIterator() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "let /** ?AsyncIterable<number> */ gen;",
            "async function foo() {",
            "  for await (const /** number */ n of gen) {",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "Can only async iterate over a (non-null) Iterable or AsyncIterable type",
                "found   : (AsyncIterable<number>|null)",
                "required: (AsyncIterator|Iterator)"))
        .run();
  }

  @Test
  public void testForAwaitOfAsyncIteratorMismatch() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().addString().build())
        .addSource(
            "let /** !AsyncIterable<string> */ gen;",
            "async function foo() {",
            "  for await (const /** number */ n of gen) {",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "declared type of for-of loop variable does not match inferred type",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testForAwaitOfSynchronousIterable() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "let /** !Iterable<number> */ gen;",
            "async function foo() {",
            "  for await (const /** number */ n of gen) {",
            "  }",
            "}")
        .run();
  }

  @Test
  public void testForAwaitOfSynchronousIterableMismatch() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().addString().build())
        .addSource(
            "let /** !Iterable<string> */ gen;",
            "async function foo() {",
            "  for await (const /** number */ n of gen) {",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "declared type of for-of loop variable does not match inferred type",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testForAwaitOfBoxedSynchronousIterable() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().addString().build())
        .addSource(
            "let /** string */ gen;",
            "async function foo() {",
            "  for await (const /** string */ n of gen) {",
            "  }",
            "}")
        .run();
  }

  @Test
  public void testForAwaitOfBoxedSynchronousIterableMismatch() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().addString().build())
        .addSource(
            "let /** string */ gen;",
            "async function foo() {",
            "  for await (const /** number */ n of gen) {",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "declared type of for-of loop variable does not match inferred type",
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testForAwaitOfAsyncAndSynchronousIterableUnion() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "let /** !Iterable<number>|!AsyncIterable<string> */ gen;",
            "async function foo() {",
            "  for await (const /** number|string */ n of gen) {",
            "  }",
            "}")
        .run();
  }

  @Test
  public void testForAwaitOfAsyncAndSynchronousIterableUnionMismatch() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().addString().build())
        .addSource(
            "let /** !Iterable<number>|!AsyncIterable<string> */ gen;",
            "async function foo() {",
            "  for await (const /** boolean */ n of gen) {",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "declared type of for-of loop variable does not match inferred type",
                "found   : (number|string)",
                "required: boolean"))
        .run();
  }

  @Test
  public void testForAwaitOfAsyncAndBoxIterableUnion() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().addString().build())
        .addSource(
            "let /** !Iterable<number>|string */ gen;",
            "async function foo() {",
            "  for await (const /** number|string */ n of gen) {",
            "  }",
            "}")
        .run();
  }

  @Test
  public void testForAwaitOfAsyncAndBoxIterableUnionMismatch() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().addString().build())
        .addSource(
            "let /** !Iterable<number>|string */ gen;",
            "async function foo() {",
            "  for await (const /** boolean */ n of gen) {",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "declared type of for-of loop variable does not match inferred type",
                "found   : (number|string)",
                "required: boolean"))
        .run();
  }

  @Test
  public void testForAwaitOfUnknown() {
    newTest()
        .addExterns(new TestExternsBuilder().addAsyncIterable().build())
        .addSource(
            "let /** ? */ gen;",
            "async function foo() {",
            "  for await (const /** null */ n of gen) {",
            "  }",
            "}")
        .run();
  }

  @Test
  public void testForAwaitOf_nonAsyncIterable_loopVarInferred() {
    newTest()
        .addSource(
            "async function f(/** !Iterable<!Promise<string>> */ o) {",
            "  for await (const s of o) {",
            "    const /** number */ n = s;",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testForAwaitOf_asyncIterable_loopVarInferred() {
    newTest()
        .addSource(
            "async function f(/** !AsyncIterable<string> */ o) {",
            "  for await (const s of o) {",
            "    const /** number */ n = s;",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testForAwaitOf_unionOfIterableAndAsyncIterable_loopVarInferred() {
    newTest()
        .addSource(
            "async function f(/** !AsyncIterable<string>|!Iterable<number> */ o) {",
            "  for await (const s of o) {",
            "    const /** null */ n = s;",
            "  }",
            "}")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : (number|string)",
                "required: null"))
        .run();
  }

  @Test
  public void testNoCatchBinding() {
    newTest().addSource("try {} catch {}").run();
    newTest().addSource("try {} catch {} finally {}").run();
  }

  @Test
  public void testMethodWithAtConstructorDoesNotDeclareType_staticClassMethod() {
    newTest()
        .addSource(
            "class Foo {",
            "  /** @constructor */",
            "  static Bar() { }",
            "}",
            "",
            "var /** !Foo.Bar */ x;",
            "")
        .addDiagnostic("Bad type annotation. Unknown type Foo.Bar")
        .run();
  }

  @Test
  public void testMethodWithAtConstructorDoesNotDeclareType_namespaceMemberMethod() {
    newTest()
        .addSource(
            "const ns = {",
            "  /** @constructor */",
            "  Bar() { }",
            "};",
            "",
            "var /** !ns.Bar */ x;",
            "")
        .addDiagnostic("Bad type annotation. Unknown type ns.Bar")
        .run();
  }

  @Test
  public void testMethodWithAtInterfaceDoesNotDeclareType() {
    newTest()
        .addSource(
            "class Foo {",
            "  /** @interface */",
            "  static Bar() { }",
            "}",
            "",
            "var /** !Foo.Bar */ x;",
            "")
        .addDiagnostic("Bad type annotation. Unknown type Foo.Bar")
        .run();
  }

  @Test
  public void testMethodWithAtRecordDoesNotDeclareType() {
    newTest()
        .addSource(
            "class Foo {",
            "  /** @record */",
            "  static Bar() { }",
            "}",
            "",
            "var /** !Foo.Bar */ x;",
            "")
        .addDiagnostic("Bad type annotation. Unknown type Foo.Bar")
        .run();
  }

  @Test
  public void testTypeCheckingInsideGoogModule() {
    newTest()
        .addExterns(DEFAULT_EXTERNS + CLOSURE_DEFS)
        .addSource(
            "goog.module('mod.A');", //
            "const /** number */ n = 'a string';")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testGoogModuleGet_hasTypeInferredInNestedExpression() {
    newTest()
        .addExterns(DEFAULT_EXTERNS + CLOSURE_DEFS)
        .addSource(
            "function takesString(/** string */ s) {}",
            "goog.loadModule(function(exports) {",
            "  goog.module('a');",
            "  exports.NUM = 0;",
            "  return exports;",
            "});",
            "",
            "(function() {",
            "  takesString(goog.module.get('a').NUM);",
            "})();")
        .addDiagnostic(
            lines(
                "actual parameter 1 of takesString does not match formal parameter",
                "found   : number",
                "required: string"))
        .run();
  }

  @Test
  public void testJsdocCanReferToGoogModuleType_withoutNamedType() {
    newTest()
        .addExterns(DEFAULT_EXTERNS)
        .addSource(
            "goog.loadModule(function(exports) {",
            "  goog.module('a');",
            "  exports.Foo = class {};",
            "  return exports;",
            "});",
            "/** @type {!a.Foo<number>} */",
            "let x;",
            "")
        .addDiagnostic(RhinoErrorReporter.TOO_MANY_TEMPLATE_PARAMS)
        .run();
  }

  @Test
  public void testJsdocCanReferToFunctionDeclarationType_withoutNamedType() {
    newTest()
        .addExterns(DEFAULT_EXTERNS + CLOSURE_DEFS)
        .addSource(
            // file1
            "goog.provide('a.Foo');",
            "/** @constructor */",
            "a.Foo = function() {};",
            "",
            // file2
            "goog.loadModule(function(exports) {",
            "  goog.module('b.Bar');",
            "",
            "  const Foo = goog.require('a.Foo');",
            "  /** @constructor @extends {Foo} */",
            "  function Bar() {}",
            "  exports = Bar;",
            "  return exports;",
            "});",
            "",
            // file3
            "/** @type {!b.Bar<number>} */",
            "let x;",
            "")
        .addDiagnostic(RhinoErrorReporter.TOO_MANY_TEMPLATE_PARAMS)
        .run();
  }

  @Test
  public void testTypeCheckingEsModule_exportSpecs() {
    newTest().addSource("const x = 0; export {x};").run();
  }

  @Test
  public void testTypeCheckingEsExportedNameDecl() {
    newTest()
        .addSource("export const /** number */ x = 'not a number';")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testTypeCheckingInsideEsExportDefault() {
    newTest()
        .addSource("let /** number */ x; export default (x = 'not a number');")
        .addDiagnostic(
            lines(
                "assignment", //
                "found   : string",
                "required: number"))
        .run();
  }

  @Test
  public void testTypeCheckingEsModule_importSpecs() {
    compiler.getOptions().setWarningLevel(DiagnosticGroups.MODULE_LOAD, CheckLevel.OFF);
    newTest().addSource("import {x} from './input0';").run();
  }

  @Test
  public void testTypeCheckingEsModule_importStar() {
    compiler.getOptions().setWarningLevel(DiagnosticGroups.MODULE_LOAD, CheckLevel.OFF);
    newTest().addSource("import * as mod from './input0';").run();
  }

  @Test
  public void testExplicitUnrestrictedOverridesSuperImplicitStruct() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "class A {}",
            "/** @unrestricted */",
            "class B extends A {",
            "  foo() { this.x; this.x = 0; this[0]; this[0] = 0; }",
            "}")
        .run();
  }

  @Test
  public void testImplicitStructOverridesSuperExplicitUnrestricted() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @unrestricted */",
            "class A {}",
            "class B extends A {",
            "  foo() { this.x; this.x = 0; this[0]; this[0] = 0;}",
            "}")
        .addDiagnostic("Property x never defined on B")
        .addDiagnostic(
            "Cannot add a property to a struct instance after it is constructed. (If you already"
                + " declared the property, make sure to give it a type.)")
        .addDiagnostic("Cannot do '[]' access on a struct")
        .addDiagnostic("Cannot do '[]' access on a struct")
        .run();
  }

  @Test
  public void testExplicitUnrestrictedOverridesSuperExplicitStruct() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @constructor @struct */",
            "function A() {}",
            "/** @unrestricted */",
            "class B extends A {",
            "  foo() { this.x; this.x = 0; this[0]; this[0] = 0;}",
            "}")
        .run();
  }

  @Test
  public void testImplicitUnrestrictedDoesNotOverridesSuperExplicitStruct() {
    disableStrictMissingPropertyChecks();
    newTest()
        .addSource(
            "/** @constructor @struct */",
            "function A() {}",
            "/** @constructor @extends {A} */",
            "function B() {}",
            "B.prototype.foo = function() { this.x; this.x = 0; this[0]; this[0] = 0;};")
        .addDiagnostic("Property x never defined on B")
        .addDiagnostic(
            "Cannot add a property to a struct instance after it is constructed. (If you already"
                + " declared the property, make sure to give it a type.)")
        .addDiagnostic("Cannot do '[]' access on a struct")
        .addDiagnostic("Cannot do '[]' access on a struct")
        .run();
  }

  @Test
  public void testUnion_forwardEnumRefAndNumber() {
    newTest()
        .addSource(
            "/** @enum {Type} */",
            "const Enum = {A: 'a'};",
            "/** @typedef {string} */ let Type;",
            "const /** !Enum|number */ n = null;")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : null",
                "required: (Enum<string>|number)" // Verify this doesn't drop Enum<string>
                ))
        .run();
  }

  @Test
  public void testUnion_numberAndForwardEnumRef() {
    newTest()
        .addSource(
            "/** @enum {Type} */",
            "const Enum = {A: 'a'};",
            "/** @typedef {string} */ let Type;",
            "const /** number|!Enum */ n = null;")
        .addDiagnostic(
            lines(
                "initializing variable",
                "found   : null",
                "required: (Enum<string>|number)" // Verify this doesn't drop Enum<string>
                ))
        .run();
  }

  @Test
  public void testDynamicImport() {
    compiler.getOptions().setWarningLevel(DiagnosticGroups.MODULE_LOAD, CheckLevel.OFF);
    newTest()
        .addSource(
            "/** @type {string} */", //
            "let foo = import('./foo.js');")
        .addDiagnostic(
            lines(
                "initializing variable", //
                "found   : Promise<?>",
                "required: string"))
        .run();
  }

  @Test
  public void testDynamicImportSpecifier() {
    newTest()
        .addSource(
            "const bar = null;", //
            "import(bar);")
        .addDiagnostic(
            lines(
                "dynamic import specifier", //
                "found   : null",
                "required: string"))
        .run();
  }
}
