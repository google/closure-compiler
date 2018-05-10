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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.type.ClosureReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests {@link TypeCheck} on non-transpiled code.
 */
public final class TypeCheckNoTranspileTest extends CompilerTypeTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // Enable missing override checks that are disabled by default.
    compiler.getOptions().setWarningLevel(DiagnosticGroups.MISSING_OVERRIDE, CheckLevel.WARNING);
    compiler.getOptions().setWarningLevel(DiagnosticGroups.STRICT_CHECK_TYPES, CheckLevel.WARNING);
  }

  @Override
  protected CompilerOptions getDefaultOptions() {
    CompilerOptions options = super.getDefaultOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_NEXT);
    return options;
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
    // TODO(b/79437694): this should produce a type error but assign ops are not properly
    // tightened.
    testTypes(
        lines(
            "function fn(someUnknown) {",
            "  var x = someUnknown;",
            "  x **= 2;", // infer the result
            "  var /** null */ y = x;",
            "}"));
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
    testTypes(
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
    testTypes(
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

  private double getTypedPercent(String js) {
    Node n = compiler.parseTestCode(js);

    Node externs = IR.root();
    IR.root(externs, n);

    TypeCheck t = makeTypeCheck();
    t.processForTesting(null, n);
    return t.getTypedPercent();
  }

  public void testBlockScopedVarInLoop1() {
    disableStrictMissingPropertyChecks();
    testTypes(
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
    testTypes(
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
    testTypes(
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
    testTypes(
        lines(
            "/** @type {!Array<!Object>} */",
            "var arr = [1, 2];",
            "for (let /** ?Object */ elem of arr) {",
            "}"));
  }

  public void testForOf_wrongLoopVarType4b() {
    testTypes(
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
    testTypes(
        lines(
            "/** @type {!Array<number>} */",
            "var arr = [1, 2];",
            "let n = null;",
            "for (n of arr) {}"));
  }

  public void testForOf_wrongLoopVarType6a() {
    // Test that we typecheck the correct variable, given various shadowing variable declarations
    testTypes(
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
    testTypes(
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
    testTypes("for (var elem of [1, 2]) {}");
  }

  public void testForOf_array2() {
    testTypes(
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
    testTypes(
        lines(
            "/** @type {!Array<number>} */ var arr = [1, 2];",
            "function takesNumber(/** number */ n) {}",
            "for (var elem of arr) { takesNumber(elem); }"));
  }

  public void testForOf_string1() {
    testTypes(
        lines(
            "function takesString(/** string */ s) {}",
            "for (var ch of 'a string') { takesString(ch); }"));
  }

  public void testForOf_string2() {
    testTypes(
        lines(
            "function takesNumber(/** number */ n) {}",
            "for (var ch of 'a string') { takesNumber(ch); }"),
        lines(
            "actual parameter 1 of takesNumber does not match formal parameter",
            "found   : string",
            "required: number"));
  }

  public void testForOf_StringObject1() {
    testTypes(
        lines(
            "function takesString(/** string */ s) {}",
            "for (var ch of new String('boxed')) { takesString(elem); }"));
  }

  public void testForOf_StringObject2() {
    testTypes(
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
    testTypes(
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
    testTypes(
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
    testTypes("/** @type {!Iterable} */ const it = []; for (const elem of it) {}");
  }

  public void testGenerator1() {
    testTypes("/** @return {!Generator<?>} */ function* gen() {}");
  }

  public void testGenerator2() {
    testTypes("/** @return {!Generator<number>} */ function* gen() { yield 1; }");
  }

  public void testGenerator3() {
    testTypes(
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
    testTypes(
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
    testTypes(
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
    testTypes(
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
    testTypes("/** @return {!Iterable<?>} */ function *gen() {}");
  }

  public void testGenerator_returnsIterable2() {
    testTypes(
        "/** @return {!Iterable<string>} */ function* gen() {  yield 1; }",
        lines(
            "Yielded type does not match declared return type.",
            "found   : number",
            "required: string"));
  }

  public void testGenerator_returnsIterator1() {
    testTypes("/** @return {!Iterator<?>} */ function *gen() {}");
  }

  public void testGenerator_returnsIterator2() {
    testTypes(
        "/** @return {!Iterator<string>} */ function* gen() {  yield 1; }",
        lines(
            "Yielded type does not match declared return type.",
            "found   : number",
            "required: string"));
  }

  public void testGenerator_returnsIteratorIterable() {
    testTypes("/** @return {!IteratorIterable<?>} */ function *gen() {}");
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
    testTypes(
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
    testTypes("/** @return {!Generator<string>} */ function *gen() {  return 1; }",
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
    testTypes(
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
    testTypes(
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
    testTypes(
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
    testTypes(
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

  public void testMemberFunction_enum() {
    testTypes(
        "/** @enum */ var obj = {a() {}};",
        lines(
            "assignment to property a of enum{obj}",
            "found   : function(): undefined",
            "required: number"));
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

  public void disabled_testTaggedTemplateLiteral1() {
    // TODO(b/78891530): Make the typechecker handle tagged template lits. Currently this crashes.
    testTypes(
        lines(
            "function tag(/** !Array<string> */ strings, /** number */ num) {",
            "  return num;",
            "}",
            "tag`foo ${3} bar`"));
  }

  private void testTypes(String js) {
    testTypes(js, (String) null);
  }

  private void testTypes(String js, String description) {
    testTypes(js, description, false);
  }

  private void testTypes(String js, DiagnosticType type) {
    testTypes(js, type, false);
  }

  void testTypes(String js, String description, boolean isError) {
    testTypes(DEFAULT_EXTERNS, js, description, isError);
  }

  void testTypes(
      String externs, String js, String description, boolean isError) {
    parseAndTypeCheck(externs, js);

    JSError[] errors = compiler.getErrors();
    if (description != null && isError) {
      assertTrue("expected an error", errors.length > 0);
      assertEquals(description, errors[0].description);
      errors = Arrays.asList(errors).subList(1, errors.length).toArray(
          new JSError[errors.length - 1]);
    }
    if (errors.length > 0) {
      fail("unexpected error(s):\n" + LINE_JOINER.join(errors));
    }

    JSError[] warnings = compiler.getWarnings();
    if (description != null && !isError) {
      assertTrue("expected a warning", warnings.length > 0);
      assertEquals(description, warnings[0].description);
      warnings = Arrays.asList(warnings).subList(1, warnings.length).toArray(
          new JSError[warnings.length - 1]);
    }
    if (warnings.length > 0) {
      fail("unexpected warnings(s):\n" + LINE_JOINER.join(warnings));
    }
  }

  void testTypes(String js, DiagnosticType diagnosticType, boolean isError) {
    testTypes(DEFAULT_EXTERNS, js, diagnosticType, isError);
  }

  void testTypes(String externs, String js, DiagnosticType diagnosticType,
      boolean isError) {
    parseAndTypeCheck(externs, js);

    JSError[] errors = compiler.getErrors();
    if (diagnosticType != null && isError) {
      assertTrue("expected an error", errors.length > 0);
      assertEquals(diagnosticType, errors[0].getType());
      errors = Arrays.asList(errors).subList(1, errors.length).toArray(
          new JSError[errors.length - 1]);
    }
    if (errors.length > 0) {
      fail("unexpected error(s):\n" + LINE_JOINER.join(errors));
    }

    JSError[] warnings = compiler.getWarnings();
    if (diagnosticType != null && !isError) {
      assertTrue("expected a warning", warnings.length > 0);
      assertEquals(diagnosticType, warnings[0].getType());
      warnings = Arrays.asList(warnings).subList(1, warnings.length).toArray(
          new JSError[warnings.length - 1]);
    }
    if (warnings.length > 0) {
      fail("unexpected warnings(s):\n" + LINE_JOINER.join(warnings));
    }
  }

  void testTypes(String js, String[] warnings) {
    Node n = compiler.parseTestCode(js);
    assertEquals(0, compiler.getErrorCount());
    Node externsNode = IR.root();
    // create a parent node for the extern and source blocks
    IR.root(externsNode, n);

    makeTypeCheck().processForTesting(null, n);
    assertEquals(0, compiler.getErrorCount());
    if (warnings != null) {
      assertEquals(warnings.length, compiler.getWarningCount());
      JSError[] messages = compiler.getWarnings();
      for (int i = 0; i < warnings.length && i < compiler.getWarningCount();
           i++) {
        assertEquals(warnings[i], messages[i].description);
      }
    } else {
      assertEquals(0, compiler.getWarningCount());
    }
  }

  private void testClosureTypes(String js, String description) {
    testClosureTypesMultipleWarnings(js,
        description == null ? null : ImmutableList.of(description));
  }

  private void testClosureTypesMultipleWarnings(
      String js, List<String> descriptions) {
    compiler.initOptions(compiler.getOptions());
    Node n = compiler.parseTestCode(js);
    Node externs = IR.root();
    IR.root(externs, n);

    assertEquals("parsing error: " +
        Joiner.on(", ").join(compiler.getErrors()),
        0, compiler.getErrorCount());

    // For processing goog.addDependency for forward typedefs.
    new ProcessClosurePrimitives(compiler, null, CheckLevel.ERROR, false).process(externs, n);

    new TypeCheck(compiler,
        new ClosureReverseAbstractInterpreter(registry).append(
                new SemanticReverseAbstractInterpreter(registry))
            .getFirst(),
        registry)
        .processForTesting(null, n);

    assertEquals(
        "unexpected error(s) : " +
        Joiner.on(", ").join(compiler.getErrors()),
        0, compiler.getErrorCount());

    if (descriptions == null) {
      assertEquals(
          "unexpected warning(s) : " +
          Joiner.on(", ").join(compiler.getWarnings()),
          0, compiler.getWarningCount());
    } else {
      assertEquals(
          "unexpected warning(s) : " +
          Joiner.on(", ").join(compiler.getWarnings()),
          descriptions.size(), compiler.getWarningCount());
      Set<String> actualWarningDescriptions = new HashSet<>();
      for (int i = 0; i < descriptions.size(); i++) {
        actualWarningDescriptions.add(compiler.getWarnings()[i].description);
      }
      assertEquals(
          new HashSet<>(descriptions), actualWarningDescriptions);
    }
  }

  void testTypesWithExterns(String externs, String js) {
    testTypes(externs, js, (String) null, false);
  }

  void testTypesWithExtraExterns(String externs, String js) {
    testTypes(DEFAULT_EXTERNS + "\n" + externs, js, (String) null, false);
  }

  void testTypesWithExtraExterns(
      String externs, String js, String description) {
    testTypes(DEFAULT_EXTERNS + "\n" + externs, js, description, false);
  }

  void testTypesWithExtraExterns(String externs, String js, DiagnosticType diag) {
    testTypes(DEFAULT_EXTERNS + "\n" + externs, js, diag, false);
  }

  /**
   * Parses and type checks the JavaScript code.
   */
  private Node parseAndTypeCheck(String js) {
    return parseAndTypeCheck(DEFAULT_EXTERNS, js);
  }

  private Node parseAndTypeCheck(String externs, String js) {
    return parseAndTypeCheckWithScope(externs, js).root;
  }

  /**
   * Parses and type checks the JavaScript code and returns the TypedScope used
   * whilst type checking.
   */
  private TypeCheckResult parseAndTypeCheckWithScope(String js) {
    return parseAndTypeCheckWithScope(DEFAULT_EXTERNS, js);
  }

  private TypeCheckResult parseAndTypeCheckWithScope(String externs, String js) {
    registry.clearNamedTypes();
    registry.clearTemplateTypeNames();
    compiler.init(
        ImmutableList.of(SourceFile.fromCode("[externs]", externs)),
        ImmutableList.of(SourceFile.fromCode("[testcode]", js)),
        compiler.getOptions());

    Node n = IR.root(compiler.getInput(new InputId("[testcode]")).getAstRoot(compiler));
    Node externsNode = IR.root(compiler.getInput(new InputId("[externs]")).getAstRoot(compiler));
    Node externAndJsRoot = IR.root(externsNode, n);
    compiler.jsRoot = n;
    compiler.externsRoot = externsNode;
    compiler.externAndJsRoot = externAndJsRoot;

    assertEquals("parsing error: " +
        Joiner.on(", ").join(compiler.getErrors()),
        0, compiler.getErrorCount());

    TypedScope s = makeTypeCheck().processForTesting(externsNode, n);
    return new TypeCheckResult(n, s);
  }

  private Node typeCheck(Node n) {
    Node externsNode = IR.root();
    Node externAndJsRoot = IR.root(externsNode);
    externAndJsRoot.addChildToBack(n);

    makeTypeCheck().processForTesting(null, n);
    return n;
  }

  private TypeCheck makeTypeCheck() {
    return new TypeCheck(compiler, new SemanticReverseAbstractInterpreter(registry), registry);
  }

  String suppressMissingProperty(String ... props) {
    String result = "function dummy(x) { ";
    for (String prop : props) {
      result += "x." + prop + " = 3;";
    }
    return result + "}";
  }

  String suppressMissingPropertyFor(String type, String ... props) {
    String result = "function dummy(x) { ";
    for (String prop : props) {
      result += type + ".prototype." + prop + " = 3;";
    }
    return result + "}";
  }

  private void disableStrictMissingPropertyChecks() {
    compiler
        .getOptions()
        .setWarningLevel(DiagnosticGroups.STRICT_MISSING_PROPERTIES, CheckLevel.OFF);
  }

  private static class TypeCheckResult {
    private final Node root;
    private final TypedScope scope;

    private TypeCheckResult(Node root, TypedScope scope) {
      this.root = root;
      this.scope = scope;
    }
  }
}
