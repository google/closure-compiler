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

import com.google.common.collect.Lists;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

import java.util.List;

/**
 * Tests for {@link J2clPass}.
 */
public class J2clPassTest extends CompilerTestCase {

  public J2clPassTest() {
    this.enableNormalize();
  }

  private void testDoesntChange(List<SourceFile> js) {
    test(js, js);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new J2clPass(compiler);
  }

  public void testUtilGetDefine() {
    String defineAbc = "var a={}; a.b={}; /** @define {boolean} */ a.b.c = true;\n";
    test(
        defineAbc + "nativebootstrap.Util.$getDefine('a.b.c', 'def');",
        defineAbc + "('def', String(a.b.c));");
    test(
        defineAbc + "nativebootstrap.Util.$getDefine('a.b.c');",
        defineAbc + "(null, String(a.b.c));");
  }

  public void testUtilGetDefine_notDefined() {
    test("nativebootstrap.Util.$getDefine('not.defined');", "null;");
    test("nativebootstrap.Util.$getDefine('not.defined', 'def');", "'def';");
  }

  public void testQualifiedInlines() {
    // Arrays functions.
    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Arrays.impl.js",
                LINE_JOINER.join(
                    // Function definitions and calls are qualified globals.
                    "var Arrays = function() {};",
                    "Arrays.$create = function() { return 1; }",
                    "Arrays.$init = function() { return 2; }",
                    "Arrays.$instanceIsOfType = function() { return 3; }",
                    "Arrays.$castTo = function() { return 4; }",
                    "",
                    "alert(Arrays.$create());",
                    "alert(Arrays.$init());",
                    "alert(Arrays.$instanceIsOfType());",
                    "alert(Arrays.$castTo());"))),
        Lists.newArrayList(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Arrays.impl.js",
                LINE_JOINER.join(
                    // Function definitions and calls are qualified globals.
                    "var Arrays = function() {};",
                    "Arrays.$create = function() { return 1; }",
                    "Arrays.$init = function() { return 2; }",
                    "Arrays.$instanceIsOfType = function() { return 3; }",
                    "Arrays.$castTo = function() { return 4; }",
                    "",
                    "alert(1);",
                    "alert(2);",
                    "alert(3);",
                    "alert(4);"))));

    // Casts functions.
    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Casts.impl.js",
                LINE_JOINER.join(
                    // Function definitions and calls are qualified globals.
                    "var Casts = function() {};",
                    "Casts.to = function() { return 1; }",
                    "",
                    "alert(Casts.to());"))),
        Lists.newArrayList(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Casts.impl.js",
                LINE_JOINER.join(
                    // Function definitions and calls are qualified globals.
                    "var Casts = function() {};",
                    "Casts.to = function() { return 1; }",
                    "",
                    "alert(1);"))));

    // Interface $markImplementor() functions.
    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "name/doesnt/matter/Foo.impl.js",
                LINE_JOINER.join(
                    // Function definitions and calls are qualified globals.
                    "var FooInterface = function() {};",
                    "FooInterface.$markImplementor = function(classDef) {",
                    "  classDef.$implements__FooInterface = true;",
                    "}",
                    "",
                    "var Foo = function() {};",
                    "FooInterface.$markImplementor(Foo);"))),
        Lists.newArrayList(
            SourceFile.fromCode(
                "name/doesnt/matter/Foo.impl.js",
                LINE_JOINER.join(
                    // Function definitions and calls are qualified globals.
                    "var FooInterface = function() {};",
                    "FooInterface.$markImplementor = function(classDef) {",
                    "  classDef.$implements__FooInterface = true;",
                    "}",
                    "",
                    "var Foo = function() {};",
                    "{Foo.$implements__FooInterface = true;}"))));
  }

  public void testRenamedQualifierStillInlines() {
    // Arrays functions.
    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Arrays.impl.js",
                LINE_JOINER.join(
                    // Function definitions and calls are qualified globals.
                    "var $jscomp = {};",
                    "$jscomp.scope = {};",
                    "$jscomp.scope.Arrays = {};",
                    "$jscomp.scope.Arrays.$create = function() { return 1; }",
                    "$jscomp.scope.Arrays.$init = function() { return 2; }",
                    "$jscomp.scope.Arrays.$instanceIsOfType = function() { return 3; }",
                    "$jscomp.scope.Arrays.$castTo = function() { return 4; }",
                    "",
                    "alert($jscomp.scope.Arrays.$create());",
                    "alert($jscomp.scope.Arrays.$init());",
                    "alert($jscomp.scope.Arrays.$instanceIsOfType());",
                    "alert($jscomp.scope.Arrays.$castTo());"))),
        Lists.newArrayList(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Arrays.impl.js",
                LINE_JOINER.join(
                    // Function definitions and calls are qualified globals.
                    "var $jscomp = {};",
                    "$jscomp.scope = {};",
                    "$jscomp.scope.Arrays = {};",
                    "$jscomp.scope.Arrays.$create = function() { return 1; }",
                    "$jscomp.scope.Arrays.$init = function() { return 2; }",
                    "$jscomp.scope.Arrays.$instanceIsOfType = function() { return 3; }",
                    "$jscomp.scope.Arrays.$castTo = function() { return 4; }",
                    "",
                    "alert(1);",
                    "alert(2);",
                    "alert(3);",
                    "alert(4);"))));

    // Casts functions.
    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Casts.impl.js",
                LINE_JOINER.join(
                    // Function definitions and calls are qualified globals.
                    "var Casts = function() {};",
                    "Casts.to = function() { return 1; }",
                    "",
                    "alert(Casts.to());"))),
        Lists.newArrayList(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Casts.impl.js",
                LINE_JOINER.join(
                    // Function definitions and calls are qualified globals.
                    "var Casts = function() {};",
                    "Casts.to = function() { return 1; }",
                    "",
                    "alert(1);"))));

    // Interface $markImplementor() functions.
    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "name/doesnt/matter/Foo.impl.js",
                LINE_JOINER.join(
                    // Function definitions and calls are qualified globals.
                    "var $jscomp = {};",
                    "$jscomp.scope = {};",
                    "$jscomp.scope.FooInterface = function() {};",
                    "$jscomp.scope.FooInterface.$markImplementor = function(classDef) {",
                    "  classDef.$implements__FooInterface = true;",
                    "}",
                    "",
                    "$jscomp.scope.Foo = function() {};",
                    "$jscomp.scope.FooInterface.$markImplementor($jscomp.scope.Foo);"))),
        Lists.newArrayList(
            SourceFile.fromCode(
                "name/doesnt/matter/Foo.impl.js",
                LINE_JOINER.join(
                    // Function definitions and calls are qualified globals.
                    "var $jscomp = {};",
                    "$jscomp.scope = {};",
                    "$jscomp.scope.FooInterface = function() {};",
                    "$jscomp.scope.FooInterface.$markImplementor = function(classDef) {",
                    "  classDef.$implements__FooInterface = true;",
                    "}",
                    "",
                    "$jscomp.scope.Foo = function() {};",
                    "{$jscomp.scope.Foo.$implements__FooInterface = true;}"))));
  }

  public void testUnexpectedFunctionDoesntInline() {
    // Arrays functions.
    testDoesntChange(
        Lists.newArrayList(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Arrays.impl.js",
                LINE_JOINER.join(
                    // Function definitions and calls are qualified globals.
                    "var Arrays = function() {};",
                    "Arrays.fooBar = function() { return 4; }",
                    "",
                    "alert(Arrays.fooBar());"))));

    // Casts functions.
    testDoesntChange(
        Lists.newArrayList(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Casts.impl.js",
                LINE_JOINER.join(
                    // Function definitions and calls are qualified globals.
                    "var Casts = function() {};",
                    "Casts.fooBar = function() { return 4; }",
                    "",
                    "alert(Casts.fooBar());"))));

    // No applicable for $markImplementor() inlining since it is not limited to just certain class
    // files and so there are no specific files in which "other" functions should be ignored.
  }

  public void testUnqualifiedDoesntInline() {
    // Arrays functions.
    testDoesntChange(
        Lists.newArrayList(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Arrays.impl.js",
                LINE_JOINER.join(
                    // Function definitions and calls are qualified globals.
                    "var $create = function() { return 1; }",
                    "var $init = function() { return 2; }",
                    "var $instanceIsOfType = function() { return 3; }",
                    "var $castTo = function() { return 4; }",
                    "",
                    "alert($create());",
                    "alert($init());",
                    "alert($instanceIsOfType());",
                    "alert($castTo());"))));

    // Casts functions.
    testDoesntChange(
        Lists.newArrayList(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Casts.impl.js",
                LINE_JOINER.join(
                    // Function definitions and calls are qualified globals.
                    "var to = function() { return 1; }", "", "alert(to());"))));

    // Interface $markImplementor() functions.
    testDoesntChange(
        Lists.newArrayList(
            SourceFile.fromCode(
                "name/doesnt/matter/Foo.impl.js",
                LINE_JOINER.join(
                    // Function definitions and calls are qualified globals.
                    "var $markImplementor = function(classDef) {",
                    "  classDef.$implements__FooInterface = true;",
                    "}",
                    "",
                    "var Foo = function() {};",
                    "$markImplementor(Foo);"))));
  }

  public void testWrongFileNameDoesntInline() {
    // Arrays functions.
    testDoesntChange(
        Lists.newArrayList(
            SourceFile.fromCode(
                "Arrays.impl.js",
                LINE_JOINER.join(
                    // Function definitions and calls are qualified globals.
                    "var Arrays = function() {};",
                    "Arrays.$create = function() { return 1; }",
                    "Arrays.$init = function() { return 2; }",
                    "Arrays.$instanceIsOfType = function() { return 3; }",
                    "Arrays.$castTo = function() { return 4; }",
                    "",
                    "alert(Arrays.$create());",
                    "alert(Arrays.$init());",
                    "alert(Arrays.$instanceIsOfType());",
                    "alert(Arrays.$castTo());"))));

    // Casts functions.
    testDoesntChange(
        Lists.newArrayList(
            SourceFile.fromCode(
                "Casts.impl.js",
                LINE_JOINER.join(
                    // Function definitions and calls are qualified globals.
                    "var Casts = function() {};",
                    "Casts.to = function() { return 1; }",
                    "",
                    "alert(Casts.to());"))));

    // No applicable for $markImplementor() inlining since it is not limited to just certain class
    // files.
  }

  public void testInlineNativeAlias() {
    test(
        LINE_JOINER.join(
            "/** @constructor */ var $RegExp = window.RegExp;",
            "var foo = new $RegExp('', '');"),
        LINE_JOINER.join(
            "/** @constructor */ var $RegExp = window.RegExp;",
            "var foo = new window.RegExp('', '');"));
  }

  public void testInlineNativeAlias_const() {
    setLanguage(LanguageMode.ECMASCRIPT6, LanguageMode.ECMASCRIPT5);
    test(
        LINE_JOINER.join(
            "/** @constructor */ const $RegExp = window.RegExp;",
            "const foo = new $RegExp('', '');"),
        LINE_JOINER.join(
            "/** @constructor */ const $RegExp = window.RegExp;",
            "const foo = new window.RegExp('', '');"));
  }

  public void testInlineNativeAlias_let() {
    setLanguage(LanguageMode.ECMASCRIPT6, LanguageMode.ECMASCRIPT5);
    test(
        LINE_JOINER.join(
            "/** @constructor */ let $RegExp = window.RegExp;",
            "let foo = new $RegExp('', '');"),
        LINE_JOINER.join(
            "/** @constructor */ let $RegExp = window.RegExp;",
            "let foo = new window.RegExp('', '');"));
  }

  public void testInlineNativeAlias_notTopLevel() {
    testSame(
        LINE_JOINER.join(
            "function x() {",
            "  /** @constructor */ var $RegExp = window.RegExp;",
            "  var foo = new $RegExp('', '');",
            "}"));
  }

  public void testInlineNativeAlias_notConstructor() {
    testSame(
        LINE_JOINER.join(
            "var $RegExp = window.RegExp;",
            "var foo = new $RegExp('', '');"));
  }

  public void testInlineNativeAlias_redeclared() {
    testSame(
        LINE_JOINER.join(
            "/** @constructor */ var $RegExp = window.RegExp;",
            "function x() {",
            "  var $RegExp = function() {};",
            "  var foo = new $RegExp('', '');",
            "}"));
  }
}
