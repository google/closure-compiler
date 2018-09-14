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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link J2clPass}. */
@RunWith(JUnit4.class)
public class J2clPassTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    this.enableNormalize();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new J2clPass(compiler);
  }

  @Override
  protected Compiler createCompiler() {
    Compiler compiler = super.createCompiler();
    J2clSourceFileChecker.markToRunJ2clPasses(compiler);
    return compiler;
  }

  @Test
  public void testQualifiedInlines() {
    // Arrays functions.
    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Arrays.impl.java.js",
                lines(
                    // Function definitions and calls are qualified globals.
                    "var Arrays = function() {};",
                    "Arrays.$create = function() { return 1; }",
                    "Arrays.$init = function() { return 2; }",
                    "Arrays.$instanceIsOfType = function() { return 3; }",
                    "Arrays.$castTo = function() { return 4; }",
                    "Arrays.$stampType = function() { return 5; }",
                    "",
                    "alert(Arrays.$create());",
                    "alert(Arrays.$init());",
                    "alert(Arrays.$instanceIsOfType());",
                    "alert(Arrays.$castTo());",
                    "alert(Arrays.$stampType());"))),
        Lists.newArrayList(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Arrays.impl.java.js",
                lines(
                    // Function definitions and calls are qualified globals.
                    "var Arrays = function() {};",
                    "Arrays.$create = function() { return 1; }",
                    "Arrays.$init = function() { return 2; }",
                    "Arrays.$instanceIsOfType = function() { return 3; }",
                    "Arrays.$castTo = function() { return 4; }",
                    "Arrays.$stampType = function() { return 5; }",
                    "",
                    "alert(1);",
                    "alert(2);",
                    "alert(3);",
                    "alert(4);",
                    "alert(5);"))));

    // Casts functions.
    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Casts.impl.java.js",
                lines(
                    // Function definitions and calls are qualified globals.
                    "var Casts = function() {};",
                    "Casts.$to = function() { return 1; }",
                    "",
                    "alert(Casts.$to());"))),
        Lists.newArrayList(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Casts.impl.java.js",
                lines(
                    // Function definitions and calls are qualified globals.
                    "var Casts = function() {};",
                    "Casts.$to = function() { return 1; }",
                    "",
                    "alert(1);"))));

    // Interface $markImplementor() functions.
    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "name/doesnt/matter/Foo.impl.java.js",
                lines(
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
                "name/doesnt/matter/Foo.impl.java.js",
                lines(
                    // Function definitions and calls are qualified globals.
                    "var FooInterface = function() {};",
                    "FooInterface.$markImplementor = function(classDef) {",
                    "  classDef.$implements__FooInterface = true;",
                    "}",
                    "",
                    "var Foo = function() {};",
                    "{Foo.$implements__FooInterface = true;}"))));
  }

  @Test
  public void testRenamedQualifierStillInlines() {
    // Arrays functions.
    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Arrays.impl.java.js",
                lines(
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
                "j2cl/transpiler/vmbootstrap/Arrays.impl.java.js",
                lines(
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
                "j2cl/transpiler/vmbootstrap/Casts.impl.java.js",
                lines(
                    // Function definitions and calls are qualified globals.
                    "var $jscomp = {};",
                    "$jscomp.scope = {};",
                    "$jscomp.scope.Casts = function() {};",
                    "$jscomp.scope.Casts.$to = function() { return 1; }",
                    "",
                    "alert($jscomp.scope.Casts.$to());"))),
        Lists.newArrayList(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Casts.impl.java.js",
                lines(
                    // Function definitions and calls are qualified globals.
                    "var $jscomp = {};",
                    "$jscomp.scope = {};",
                    "$jscomp.scope.Casts = function() {};",
                    "$jscomp.scope.Casts.$to = function() { return 1; }",
                    "",
                    "alert(1);"))));

    // Interface $markImplementor() functions.
    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "name/doesnt/matter/Foo.impl.java.js",
                lines(
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
                "name/doesnt/matter/Foo.impl.java.js",
                lines(
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

  @Test
  public void testUnexpectedFunctionDoesntInline() {
    // Arrays functions.
    testSame(
        Lists.newArrayList(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Arrays.impl.java.js",
                lines(
                    // Function definitions and calls are qualified globals.
                    "var Arrays = function() {};",
                    "Arrays.fooBar = function() { return 4; }",
                    "",
                    "alert(Arrays.fooBar());"))));

    // Casts functions.
    testSame(
        Lists.newArrayList(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Casts.impl.java.js",
                lines(
                    // Function definitions and calls are qualified globals.
                    "var Casts = function() {};",
                    "Casts.fooBar = function() { return 4; }",
                    "",
                    "alert(Casts.fooBar());"))));

    // No applicable for $markImplementor() inlining since it is not limited to just certain class
    // files and so there are no specific files in which "other" functions should be ignored.
  }

  @Test
  public void testUnqualifiedDoesntInline() {
    // Arrays functions.
    testSame(
        Lists.newArrayList(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Arrays.impl.java.js",
                lines(
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
    testSame(
        Lists.newArrayList(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Casts.impl.java.js",
                lines(
                    // Function definitions and calls are qualified globals.
                    "var to = function() { return 1; }", "", "alert(to());"))));

    // Interface $markImplementor() functions.
    testSame(
        Lists.newArrayList(
            SourceFile.fromCode(
                "name/doesnt/matter/Foo.impl.java.js",
                lines(
                    // Function definitions and calls are qualified globals.
                    "var $markImplementor = function(classDef) {",
                    "  classDef.$implements__FooInterface = true;",
                    "}",
                    "",
                    "var Foo = function() {};",
                    "$markImplementor(Foo);"))));
  }

  @Test
  public void testWrongFileNameDoesntInline() {
    // Arrays functions.
    testSame(
        Lists.newArrayList(
            SourceFile.fromCode(
                "Arrays2.impl.java.js",
                lines(
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
    testSame(
        Lists.newArrayList(
            SourceFile.fromCode(
                "Casts2.impl.java.js",
                lines(
                    // Function definitions and calls are qualified globals.
                    "var Casts = function() {};",
                    "Casts.to = function() { return 1; }",
                    "",
                    "alert(Casts.to());"))));

    // No applicable for $markImplementor() inlining since it is not limited to just certain class
    // files.
  }

  @Test
  public void testMarksChanges() {
    test(
        ImmutableList.of(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Casts.impl.java.js",
                lines(
                    // Function definitions and calls are qualified globals.
                    "var Casts = function() {};",
                    "Casts.$to = function(instance) { return instance; }",
                    "",
                    "alert(Casts.$to(function(a) { return a; }));"))),
        ImmutableList.of(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Casts.impl.java.js",
                lines(
                    // Function definitions and calls are qualified globals.
                    "var Casts = function() {};",
                    "Casts.$to = function(instance) { return instance; }",
                    "",
                    "alert(function(a) { return a; });"))));

  }
}
