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

import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.javascript.jscomp.testing.ColorSubject;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.testing.NodeSubject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link J2clPass}. */
@RunWith(JUnit4.class)
public class J2clPassTest extends CompilerTestCase {

  @Before
  public void customSetUp() throws Exception {
    enableTypeCheck();
    replaceTypesWithColors();
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
  public void testQualifiedInlines_arrays() {
    // Function definitions and calls are qualified globals.
    String declarations =
        lines(
            "class Arrays {",
            "  static $create() { return 1; }",
            "  static $init() { return 2; }",
            "  static $instanceIsOfType() { return 3; }",
            "  static $castTo() { return 4; }",
            "  static $stampType() { return 5; }",
            "}");

    test(
        srcs(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Arrays.impl.java.js",
                lines(
                    declarations,
                    "",
                    "alert(Arrays.$create());",
                    "alert(Arrays.$init());",
                    "alert(Arrays.$instanceIsOfType());",
                    "alert(Arrays.$castTo());",
                    "alert(Arrays.$stampType());"))),
        expected(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Arrays.impl.java.js",
                lines(
                    declarations,
                    "",
                    "alert(1);",
                    "alert(2);",
                    "alert(3);",
                    "alert(4);",
                    "alert(5);"))));
  }

  @Test
  public void testQualifiedInlines_casts() {
    // Function definitions and calls are qualified globals.
    String declarations =
        lines(
            "class Casts {", //
            "  static $to() { return 1; }",
            "}");

    test(
        srcs(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Casts.impl.java.js",
                lines(
                    declarations, //
                    "",
                    "alert(Casts.$to());"))),
        expected(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Casts.impl.java.js",
                lines(
                    declarations, //
                    "",
                    "alert(1);"))));
  }

  @Test
  public void testQualifiedInlines_markImplementor() {
    // Function definitions and calls are qualified globals.
    String declarations =
        lines(
            "class FooInterface {",
            "  static $markImplementor(classDef) {",
            "    classDef.$implements__FooInterface = true;",
            "  }",
            "}");

    test(
        srcs(
            SourceFile.fromCode(
                "name/doesnt/matter/Foo.impl.java.js",
                lines(
                    declarations,
                    "/** @constructor */",
                    "var Foo = function() {};",
                    "FooInterface.$markImplementor(Foo);"))),
        expected(
            SourceFile.fromCode(
                "name/doesnt/matter/Foo.impl.java.js",
                lines(
                    declarations,
                    "/** @constructor */",
                    "var Foo = function() {};",
                    "{Foo.$implements__FooInterface = true;}"))));
    final Node scriptNode = getLastCompiler().getJsRoot().getLastChild();
    assertNode(scriptNode).isScript();
    final Node inlinedBlock = scriptNode.getLastChild();
    final Node varStatement = inlinedBlock.getPrevious();

    final NodeSubject declaredFooNameNodeSubject =
        assertNode(varStatement).isVar().hasOneChildThat().isName("Foo");
    final ColorSubject declaredColorSubject = declaredFooNameNodeSubject.hasColorThat();

    final NodeSubject inlinedFooNameNodeSubject =
        assertNode(inlinedBlock)
            .isBlock()
            .hasOneChildThat()
            .isExprResult()
            .hasOneChildThat()
            .isAssign()
            .hasFirstChildThat()
            .isGetProp()
            .hasOneChildThat()
            .isName("Foo");
    final ColorSubject inlinedColorSubject = inlinedFooNameNodeSubject.hasColorThat();

    inlinedColorSubject.isEqualTo(declaredColorSubject);
  }

  @Test
  public void testRenamedQualifierStillInlines_arrays() {
    // Function definitions and calls are qualified globals.
    String declarations =
        lines(
            "var $jscomp = {};",
            "$jscomp.scope = {};",
            "$jscomp.scope.Arrays = class {;",
            "  static $create() { return 1; }",
            "  static $init() { return 2; }",
            "  static $instanceIsOfType() { return 3; }",
            "  static $castTo() { return 4; }",
            "}");

    test(
        srcs(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Arrays.impl.java.js",
                lines(
                    declarations,
                    "",
                    "alert($jscomp.scope.Arrays.$create());",
                    "alert($jscomp.scope.Arrays.$init());",
                    "alert($jscomp.scope.Arrays.$instanceIsOfType());",
                    "alert($jscomp.scope.Arrays.$castTo());"))),
        expected(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Arrays.impl.java.js",
                lines(
                    declarations, //
                    "",
                    "alert(1);",
                    "alert(2);",
                    "alert(3);",
                    "alert(4);"))));
  }

  @Test
  public void testRenamedQualifierStillInlines_casts() {
    // Function definitions and calls are qualified globals.
    String declarations =
        lines(
            "var $jscomp = {};",
            "$jscomp.scope = {};",
            "$jscomp.scope.Casts = class {",
            "  static $to() { return 1; }",
            "}");

    test(
        srcs(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Casts.impl.java.js",
                lines(
                    declarations, //
                    "",
                    "alert($jscomp.scope.Casts.$to());"))),
        expected(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Casts.impl.java.js",
                lines(
                    declarations, //
                    "",
                    "alert(1);"))));
  }

  @Test
  public void testRenamedQualifierStillInlines_markImplementor() {
    // Function definitions and calls are qualified globals.
    String declarations =
        lines(
            "var $jscomp = {};",
            "$jscomp.scope = {};",
            "$jscomp.scope.FooInterface = class {",
            "  static $markImplementor(classDef) {",
            "    classDef.$implements__FooInterface = true;",
            "  }",
            "}");

    test(
        srcs(
            SourceFile.fromCode(
                "name/doesnt/matter/Foo.impl.java.js",
                lines(
                    declarations,
                    "",
                    "$jscomp.scope.Foo = function() {};",
                    "$jscomp.scope.FooInterface.$markImplementor($jscomp.scope.Foo);"))),
        expected(
            SourceFile.fromCode(
                "name/doesnt/matter/Foo.impl.java.js",
                lines(
                    declarations,
                    "",
                    "$jscomp.scope.Foo = function() {};",
                    "{$jscomp.scope.Foo.$implements__FooInterface = true;}"))));
  }

  @Test
  public void testUnexpectedFunctionDoesntInline() {
    // Arrays functions.
    testSame(
        srcs(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Arrays.impl.java.js",
                lines(
                    // Function definitions and calls are qualified globals.
                    "var Arrays = class {",
                    "  static fooBar() { return 4; }",
                    "}",
                    "",
                    "alert(Arrays.fooBar());"))));

    // Casts functions.
    testSame(
        srcs(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Casts.impl.java.js",
                lines(
                    // Function definitions and calls are qualified globals.
                    "var Casts = class {",
                    "  static fooBar() { return 4; }",
                    "}",
                    "",
                    "alert(Casts.fooBar());"))));

    // No applicable for $markImplementor() inlining since it is not limited to just certain class
    // files and so there are no specific files in which "other" functions should be ignored.
  }

  @Test
  public void testUnqualifiedDoesntInline() {
    // Arrays functions.
    testSame(
        srcs(
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
        srcs(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Casts.impl.java.js",
                lines(
                    // Function definitions and calls are qualified globals.
                    "var to = function() { return 1; }", "", "alert(to());"))));

    // Interface $markImplementor() functions.
    testSame(
        srcs(
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
        srcs(
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
        srcs(
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
        srcs(
            SourceFile.fromCode(
                "j2cl/transpiler/vmbootstrap/Casts.impl.java.js",
                lines(
                    // Function definitions and calls are qualified globals.
                    "var Casts = function() {};",
                    "Casts.$to = function(instance) { return instance; }",
                    "",
                    "alert(Casts.$to(function(a) { return a; }));"))),
        expected(
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
