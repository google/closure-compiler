/*
 * Copyright 2016 The Closure Compiler Authors.
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

/** Tests for {@link ConvertIIFEArgsToVars}. */
public final class ConvertIIFEArgsToVarsTest extends CompilerTestCase {
  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new ConvertIIFEArgsToVars(compiler);
  }

  @Override
  public void setUp() {
    enableNormalize();
  }

  public void testBasic() {
    test(
        "var a = 1; var b = 2; (function(c, d) { console.log(c, d); })(a, b);",
        LINE_JOINER.join(
            "var a = 1;",
            "var b = 2;",
            "(function() {",
            "  var c = a;",
            "  var d = b;",
            "  console.log(c, d);",
            "})()"));
  }

  public void testExplicitThis() {
    test(
        LINE_JOINER.join(
            "var a = 1;",
            "var b = 2;",
            "(function(c, d) {",
            "  console.log(c, d);",
            "}).call(null, a, b)"),
        LINE_JOINER.join(
            "var a = 1;",
            "var b = 2;",
            "(function() {",
            "  var c = a;",
            "  var d = b;",
            "  console.log(c, d);",
            "}).call(null)"));
  }

  public void testNested() {
    test(
        LINE_JOINER.join(
            "var a = 1;",
            "var b = 2;",
            "(function(c, d) {",
            "  (function(e, f) {",
            "    console.log(e, f);",
            "  })(c, d);",
            "}).call(null, a, b);"),
        LINE_JOINER.join(
            "var a = 1;",
            "var b = 2;",
            "(function() {",
            "  var c = a;",
            "  var d = b;",
            "  (function() {",
            "    var e = c;",
            "    var f = d;",
            "    console.log(e, f);",
            "  })();",
            "}).call(null)"));
  }

  public void testArgumentsReference() {
    testSame(
        LINE_JOINER.join(
            "var a = 1;",
            "var b = 2;",
            "(function(c, d) {",
            "  var len = arguments.length;",
            "  console.log(c, d, len);",
            "}).call(null, a, b);"));
  }

  public void testUndefinedAlias() {
    test(
        "(function(c, d) { console.log(c, d); })();",
        LINE_JOINER.join(
            "(function() {",
            "  var c = undefined;",
            "  var d = undefined;",
            "  console.log(c, d);",
            "})();"));
  }

  public void testMoreArgsThanParams() {
    test(
        "(function(c, d) { console.log(c, d); })(1, 2, 3);",
        "(function() { var c = 1; var d = 2; console.log(c, d); })();");
  }

  public void testParamsSameNameAsArgs() {
    test(
        "var c = 1; var d = 2; (function(c, d) { console.log(c, d); })(c, d);",
        LINE_JOINER.join(
            "var c = 1;",
            "var d = 2;",
            "(function() {",
            "  var c$jscomp$1 = c;",
            "  var d$jscomp$1 = d;",
            "  console.log(c$jscomp$1, d$jscomp$1);",
            "})();"));
  }

  public void testExprResult() {
    test(
        LINE_JOINER.join(
            "var foo = 4;",
            "var bar = {};",
            "bar.baz = (function(bar) {",
            "  return bar + 2;",
            "})(foo);"),
        LINE_JOINER.join(
            "var foo = 4;",
            "var bar = {};",
            "bar.baz = (function() {",
            "  var bar = foo;",
            "  return bar + 2;",
            "})();"));
  }

  public void testPreventAccidentalVariableCapture() {
    test(
        "var a = 1; (function(c) { var a = 2; console.log(c); })(a)",
        LINE_JOINER.join(
            "var a = 1;",
            "(function() {",
            " var c = a;",
            " var a$jscomp$1 = 2;",
            " console.log(c);",
            "})()"));
  }
}
