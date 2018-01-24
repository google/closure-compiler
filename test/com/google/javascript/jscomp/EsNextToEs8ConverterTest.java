/*
 * Copyright 2018 The Closure Compiler Authors.
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

/** Test cases for ESNext transpilation. */
public final class EsNextToEs8ConverterTest extends TypeICompilerTestCase {

  public EsNextToEs8ConverterTest() {
    super(MINIMAL_EXTERNS);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);
    setLanguageOut(LanguageMode.ECMASCRIPT_2017);
    enableRunTypeCheckAfterProcessing();
    this.mode = TypeInferenceMode.NEITHER;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new EsNextToEs8Converter(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testObjectLiteralWithSpread() {
    test("({first, ...spread});", "Object.assign({}, {first}, spread)");
    test("({first, second, ...spread});", "Object.assign({}, {first, second}, spread)");
    test("({...spread, last});", "Object.assign({}, spread, {last})");
    test("({...spread, penultimate, last});", "Object.assign({}, spread, {penultimate, last})");
    test(
        "({before, ...spread1, mid1, mid2, ...spread2, after});",
        "Object.assign({}, {before}, spread1, {mid1, mid2}, spread2, {after})");
    test("({first, ...{...nested}});", "Object.assign({}, {first}, Object.assign({}, nested))");
    test(
        "({first, [foo()]: baz(), ...spread});",
        "Object.assign({}, {first, [foo()]: baz()}, spread)");
  }

  public void testObjectPatternWithRestDecl() {
    test(
        "var {a: b, c: d, ...rest} = foo();",
        lines(
            "var $jscomp$objpattern$var0 = foo(),",
            "    {a: b, c: d} = $jscomp$objpattern$var0,",
            "    rest = (delete $jscomp$objpattern$var0.a,",
            "            delete $jscomp$objpattern$var0.c,",
            "            $jscomp$objpattern$var0);"));

    test(
        "const {a: b, c: d, ...rest} = foo();",
        lines(
            "const $jscomp$objpattern$var0 = foo(),",
            "      {a: b, c: d} = $jscomp$objpattern$var0,",
            "      rest = (delete $jscomp$objpattern$var0.a,",
            "              delete $jscomp$objpattern$var0.c,",
            "              $jscomp$objpattern$var0);"));

    test(
        "let {a: b, c: d, ...rest} = foo();",
        lines(
            "let $jscomp$objpattern$var0 = foo(),",
            "    {a: b, c: d} = $jscomp$objpattern$var0,",
            "    rest = (delete $jscomp$objpattern$var0.a,",
            "            delete $jscomp$objpattern$var0.c,",
            "            $jscomp$objpattern$var0);"));

    test(
        "var pre = foo(), {a: b, c: d, ...rest} = foo();",
        lines(
            "var pre = foo(),",
            "    $jscomp$objpattern$var0 = foo(),",
            "    {a: b, c: d} = $jscomp$objpattern$var0,",
            "    rest = (delete $jscomp$objpattern$var0.a,",
            "            delete $jscomp$objpattern$var0.c,",
            "            $jscomp$objpattern$var0);"));

    test(
        "var {a: b, c: d, ...rest} = foo(), post = foo();",
        lines(
            "var $jscomp$objpattern$var0 = foo(),",
            "    {a: b, c: d} = $jscomp$objpattern$var0,",
            "    rest = (delete $jscomp$objpattern$var0.a,",
            "            delete $jscomp$objpattern$var0.c,",
            "            $jscomp$objpattern$var0),",
            "    post = foo();"));

    test(
        "var pre = foo(), {a: b, c: d, ...rest} = foo(), post = foo();",
        lines(
            "var pre = foo(),",
            "    $jscomp$objpattern$var0 = foo(),",
            "    {a: b, c: d} = $jscomp$objpattern$var0,",
            "    rest = (delete $jscomp$objpattern$var0.a,",
            "            delete $jscomp$objpattern$var0.c,",
            "            $jscomp$objpattern$var0),",
            "    post = foo();"));

    test(
        "var {a: b1, c: d1, ...rest1} = foo(), {a: b2, c: d2, ...rest2} = foo();",
        lines(
            "var $jscomp$objpattern$var0 = foo(),",
            "    {a: b1, c: d1} = $jscomp$objpattern$var0,",
            "    rest1 = (delete $jscomp$objpattern$var0.a,",
            "             delete $jscomp$objpattern$var0.c,",
            "             $jscomp$objpattern$var0),",
            "    $jscomp$objpattern$var1 = foo(),",
            "    {a: b2, c: d2} = $jscomp$objpattern$var1,",
            "    rest2 = (delete $jscomp$objpattern$var1.a,",
            "             delete $jscomp$objpattern$var1.c,",
            "             $jscomp$objpattern$var1);"));
  }

  public void testObjectPatternWithRestAssignStatement() {
    test(
        "var b,d,rest; ({a: b, c: d, ...rest} = foo());",
        lines(
            "var b,d,rest;",
            "(()=>{",
            "      let $jscomp$objpattern$var0 = foo();",
            "      ({a: b, c: d} = $jscomp$objpattern$var0),",
            "      rest = (delete $jscomp$objpattern$var0.a,",
            "              delete $jscomp$objpattern$var0.c,",
            "              $jscomp$objpattern$var0);",
            "})();"));

    test(
        "var b,d,rest,pre; pre = foo(), {a: b, c: d, ...rest} = foo();",
        lines(
            "var b,d,rest,pre;",
            "pre = foo(),",
            "(()=>{",
            "      let $jscomp$objpattern$var0 = foo();",
            "      ({a: b, c: d} = $jscomp$objpattern$var0),",
            "      rest = (delete $jscomp$objpattern$var0.a,",
            "              delete $jscomp$objpattern$var0.c,",
            "              $jscomp$objpattern$var0);",
            "})();"));

    test(
        "var b,d,rest,post; ({a: b, c: d, ...rest} = foo()), post = foo();",
        lines(
            "var b,d,rest,post;",
            "(()=>{",
            "      let $jscomp$objpattern$var0 = foo();",
            "      ({a: b, c: d} = $jscomp$objpattern$var0),",
            "      rest = (delete $jscomp$objpattern$var0.a,",
            "              delete $jscomp$objpattern$var0.c,",
            "              $jscomp$objpattern$var0);",
            "})(),",
            "post = foo();"));

    test(
        "var b,d,rest,pre,post; pre = foo(), {a: b, c: d, ...rest} = foo(), post = foo();",
        lines(
            "var b,d,rest,pre,post;",
            "pre = foo(),",
            "(()=>{",
            "      let $jscomp$objpattern$var0 = foo();",
            "      ({a: b, c: d} = $jscomp$objpattern$var0),",
            "      rest = (delete $jscomp$objpattern$var0.a,",
            "              delete $jscomp$objpattern$var0.c,",
            "              $jscomp$objpattern$var0);",
            "})(),",
            "post = foo();"));

    test(
        lines(
            "var b1,d1,rest1,b2,d2,rest2;",
            "({a: b1, c: d1, ...rest1} = foo(),",
            " {a: b2, c: d2, ...rest2} = foo());"),
        lines(
            "var b1,d1,rest1,b2,d2,rest2;",
            "(()=>{",
            "      let $jscomp$objpattern$var0 = foo();",
            "      ({a: b1, c: d1} = $jscomp$objpattern$var0),",
            "      rest1 = (delete $jscomp$objpattern$var0.a,",
            "               delete $jscomp$objpattern$var0.c,",
            "               $jscomp$objpattern$var0);",
            "})(),",
            "(()=>{",
            "      let $jscomp$objpattern$var1 = foo();",
            "      ({a: b2, c: d2} = $jscomp$objpattern$var1),",
            "      rest2 = (delete $jscomp$objpattern$var1.a,",
            "               delete $jscomp$objpattern$var1.c,",
            "               $jscomp$objpattern$var1);",
            "})();"));
  }

  public void testObjectPatternWithRestAssignExpr() {
    test(
        "var x,b,d,rest; x = ({a: b, c: d, ...rest} = foo());",
        lines(
            "var x,b,d,rest;",
            "x = (()=>{",
            "    let $jscomp$objpattern$var0 = foo();",
            "    let $jscomp$objpattern$var1 = $jscomp$objpattern$var0;",
            "    ({a: b, c: d} = $jscomp$objpattern$var0),",
            "    rest = (delete $jscomp$objpattern$var0.a,",
            "            delete $jscomp$objpattern$var0.c,",
            "            $jscomp$objpattern$var0);",
            "    return $jscomp$objpattern$var1",
            "})();"));

    test(
        "var x,b,d,rest; baz({a: b, c: d, ...rest} = foo());",
        lines(
            "var x,b,d,rest;",
            "baz((()=>{",
            "    let $jscomp$objpattern$var0 = foo();",
            "    let $jscomp$objpattern$var1 = $jscomp$objpattern$var0;",
            "    ({a: b, c: d} = $jscomp$objpattern$var0),",
            "    rest = (delete $jscomp$objpattern$var0.a,",
            "            delete $jscomp$objpattern$var0.c,",
            "            $jscomp$objpattern$var0);",
            "    return $jscomp$objpattern$var1",
            "})());"));
  }

  public void testObjectPatternWithRestForOf() {
    test(
        "for ({a: b, c: d, ...rest} of foo()) { console.log(rest.z); }",
        lines(
            "for (let $jscomp$objpattern$var0 of foo()) {",
            "    ({a: b, c: d} = $jscomp$objpattern$var0,",
            "     rest = (delete $jscomp$objpattern$var0.a,",
            "            delete $jscomp$objpattern$var0.c,",
            "            $jscomp$objpattern$var0));",
            "    console.log(rest.z);",
            "}"));

    test(
        "for (var {a: b, c: d, ...rest} of foo()) { console.log(rest.z); }",
        lines(
            "for (let $jscomp$objpattern$var0 of foo()) {",
            "    var {a: b, c: d} = $jscomp$objpattern$var0,",
            "        rest = (delete $jscomp$objpattern$var0.a,",
            "                delete $jscomp$objpattern$var0.c,",
            "                $jscomp$objpattern$var0);",
            "    console.log(rest.z);",
            "}"));

    test(
        "for (let {a: b, c: d, ...rest} of foo()) { console.log(rest.z); }",
        lines(
            "for (let $jscomp$objpattern$var0 of foo()) {",
            "    let {a: b, c: d} = $jscomp$objpattern$var0,",
            "        rest = (delete $jscomp$objpattern$var0.a,",
            "                delete $jscomp$objpattern$var0.c,",
            "                $jscomp$objpattern$var0);",
            "    console.log(rest.z);",
            "}"));

    test(
        "for (const {a: b, c: d, ...rest} of foo()) { console.log(rest.z); }",
        lines(
            "for (let $jscomp$objpattern$var0 of foo()) {",
            "    const {a: b, c: d} = $jscomp$objpattern$var0,",
            "          rest = (delete $jscomp$objpattern$var0.a,",
            "                  delete $jscomp$objpattern$var0.c,",
            "                  $jscomp$objpattern$var0);",
            "    console.log(rest.z);",
            "}"));

    test(
        "for (var {a: b, [baz()]: d, ...rest} of foo()) { console.log(rest.z); }",
        lines(
            "for (let $jscomp$objpattern$var0 of foo()) {",
            "    let $jscomp$objpattern$var1 = baz();",
            "    var {a: b, [$jscomp$objpattern$var1]: d} = $jscomp$objpattern$var0,",
            "        rest = (delete $jscomp$objpattern$var0.a,",
            "                delete $jscomp$objpattern$var0[$jscomp$objpattern$var1],",
            "                $jscomp$objpattern$var0);",
            "    console.log(rest.z);",
            "}"));
  }

  public void testObjectPatternWithRestAndComputedPropertyName() {
    test(
        "var {a: b = 3, [bar()]: d, [baz()]: e, ...rest} = foo();",
        lines(
            "var $jscomp$objpattern$var0 = foo(),",
            "    $jscomp$objpattern$var1 = bar(),",
            "    $jscomp$objpattern$var2 = baz(),",
            "    {a: b = 3, ",
            "     [$jscomp$objpattern$var1]: d,",
            "     [$jscomp$objpattern$var2]: e} = $jscomp$objpattern$var0,",
            "    rest = (delete $jscomp$objpattern$var0.a,",
            "            delete $jscomp$objpattern$var0[$jscomp$objpattern$var1],",
            "            delete $jscomp$objpattern$var0[$jscomp$objpattern$var2],",
            "            $jscomp$objpattern$var0);"));
  }

  public void testObjectPatternWithRestAndDefaults() {
    test(
        "var {a = 3, ...rest} = foo();",
        lines(
            "var $jscomp$objpattern$var0 = foo(),",
            "    {a = 3} = $jscomp$objpattern$var0,",
            "    rest = (delete $jscomp$objpattern$var0.a,",
            "            $jscomp$objpattern$var0);"));

    test(
        "var {[bar()]:a = 3, 'b c':b = 12, ...rest} = foo();",
        lines(
            "var $jscomp$objpattern$var0 = foo(),",
            "    $jscomp$objpattern$var1 = bar(),",
            "    {[$jscomp$objpattern$var1]:a = 3, 'b c':b = 12} = $jscomp$objpattern$var0,",
            "    rest = (delete $jscomp$objpattern$var0[$jscomp$objpattern$var1],",
            "            delete $jscomp$objpattern$var0['b c'],",
            "            $jscomp$objpattern$var0);"));
  }

  public void testObjectPatternWithRestInCatch() {
    test(
        "try {} catch ({first, second, ...rest}) { console.log(rest.z); }",
        lines(
            "try {}",
            "catch ($jscomp$objpattern$var0) {",
            "  let {first, second} = $jscomp$objpattern$var0,",
            "      rest = (delete $jscomp$objpattern$var0.first, ",
            "              delete $jscomp$objpattern$var0.second, ",
            "              $jscomp$objpattern$var0);",
            "  console.log(rest.z);",
            "}"));
  }

  public void testObjectPatternWithRestAssignReturn() {
    test(
        "function f() { return {x:a, ...rest} = foo(); }",
        lines(
            "function f() {",
            "  return (()=>{",
            "    let $jscomp$objpattern$var0=foo();",
            "    let $jscomp$objpattern$var1=$jscomp$objpattern$var0;",
            "    ({x:a}=$jscomp$objpattern$var0),",
            "    rest=(delete $jscomp$objpattern$var0.x,",
            "          $jscomp$objpattern$var0);",
            "    return $jscomp$objpattern$var1",
            "  })();",
            "}"));
  }

  public void testObjectPatternWithRestParamList() {
    test(
        "function f({x = a(), ...rest}, y=b()) { console.log(y); }",
        lines(
            "function f($jscomp$objpattern$var0, y=b()) {",
            "  let {x=a()} = $jscomp$objpattern$var0,",
            "      rest = (delete $jscomp$objpattern$var0.x, $jscomp$objpattern$var0);",
            "  console.log(y);",
            "}"));

    test(
        "function f({x = a(), ...rest}={}, y=b()) { console.log(y); }",
        lines(
            "function f($jscomp$objpattern$var0={}, y=b()) {",
            "  let {x=a()} = $jscomp$objpattern$var0,",
            "      rest = (delete $jscomp$objpattern$var0.x, $jscomp$objpattern$var0);",
            "  console.log(y);",
            "}"));
  }

  public void testObjectPatternWithRestArrowParamList() {
    test(
        "var f = ({x = a(), ...rest}, y=b()) => { console.log(y); };",
        lines(
            "var f = ($jscomp$objpattern$var0, y=b()) => {",
            "  let {x=a()} = $jscomp$objpattern$var0,",
            "      rest = (delete $jscomp$objpattern$var0.x, $jscomp$objpattern$var0);",
            "  console.log(y);",
            "};"));

    test(
        "var f = ({x = a(), ...rest}={}, y=b()) => { console.log(y); };",
        lines(
            "var f = ($jscomp$objpattern$var0={}, y=b()) => {",
            "  let {x=a()} = $jscomp$objpattern$var0,",
            "      rest = (delete $jscomp$objpattern$var0.x, $jscomp$objpattern$var0);",
            "  console.log(y);",
            "};"));
  }
}
