/*
 * Copyright 2014 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.lint;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test case for {@link CheckInterfaces}. */
@RunWith(JUnit4.class)
public final class CheckInterfacesTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckInterfaces(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  @Test
  public void testInterfaceArgs() {
    testSame(
        "/** @interface */ function A(x) {}",
        CheckInterfaces.INTERFACE_CONSTRUCTOR_SHOULD_NOT_TAKE_ARGS);

    testSame(
        "/** @interface */ class C { constructor(x) {} }",
        CheckInterfaces.INTERFACE_CONSTRUCTOR_SHOULD_NOT_TAKE_ARGS);

    testSame(
        lines("var ns = {};\n", "/** @interface */\n", "ns.SomeInterface = function(x) {};"),
        CheckInterfaces.INTERFACE_CONSTRUCTOR_SHOULD_NOT_TAKE_ARGS);

    testSame(
        lines(
            "var ns = {};\n",
            "/** @interface */\n",
            "ns.SomeInterface = class { constructor(x) {}};"),
        CheckInterfaces.INTERFACE_CONSTRUCTOR_SHOULD_NOT_TAKE_ARGS);
  }

  @Test
  public void testInterfaceArgs_withES6Modules() {
    testSame(
        "export /** @interface */ function A(x) {}",
        CheckInterfaces.INTERFACE_CONSTRUCTOR_SHOULD_NOT_TAKE_ARGS);

    testSame(
        "export /** @interface */ class C { constructor(x) {} }",
        CheckInterfaces.INTERFACE_CONSTRUCTOR_SHOULD_NOT_TAKE_ARGS);
  }

  @Test
  public void testInterfaceConstructorWithFieldDeclarations_noWarning() {
    testSame("/** @interface */ function A() { /** @type {string} */ this.foo; }");

    testSame("/** @interface */ class C { constructor() { /** @type {string} */ this.foo; }}");

    testSame(
        lines(
            "var ns = {};",
            "/** @interface */",
            "ns.SomeInterface = function() { /** @type {string} */ this.foo; };"));

    testSame(
        lines(
            "var ns = {};",
            "/** @interface */",
            "ns.SomeInterface = class { constructor() { /** @type {string} */ this.foo; }; }"));
  }

  @Test
  public void testInterfaceMethodNotEmpty() {
    testSame(
        lines(
            "/** @interface */ ", //
            "class C { ",
            "  constructor() {} ",
            "  A() { this.foo; }",
            "}"),
        CheckInterfaces.INTERFACE_CLASS_NONSTATIC_METHOD_NOT_EMPTY);
  }

  @Test
  public void testInterfaceWithFieldDeclarations_withES6Modules() {
    testSame("export /** @interface */ function A() { /** @type {string} */ this.foo; }");
    testSame(
        "export /** @interface */ class C { constructor() { /** @type {string} */ this.foo; } }");
  }

  @Test
  public void testInterfaceClass_callToSuperInConstructorNotAllowed() {
    disableAstValidation(); // silences ASTValidator for `super()` used without `extends`
    testWarning(
        "class D {} /** @interface \n @extends {D} */ class C { constructor() { super(); } }",
        CheckInterfaces.NON_DECLARATION_STATEMENT_IN_INTERFACE);
    testWarning(
        "class D {} /** @interface \n @extends {D} */ class C { constructor() { super(x); } }",
        CheckInterfaces.NON_DECLARATION_STATEMENT_IN_INTERFACE);
    testWarning(
        "class D {} /** @record \n @extends {D} */ class C { constructor() { super(); } }",
        CheckInterfaces.NON_DECLARATION_STATEMENT_IN_INTERFACE);
    testWarning(
        "class D {} /** @record \n @extends {D} */ class C { constructor() { super(x); } }",
        CheckInterfaces.NON_DECLARATION_STATEMENT_IN_INTERFACE);
  }

  @Test
  public void testInterfaceClass_extendsKeywordNotAllowed() {
    test(
        srcs("class D {} /** @interface */ class C extends D { constructor() { super(); } }"),
        warning(CheckInterfaces.INTERFACE_DEFINED_WITH_EXTENDS),
        warning(CheckInterfaces.NON_DECLARATION_STATEMENT_IN_INTERFACE));
    test(
        srcs("class D {} /** @interface */ class C extends D { constructor() { super(x); } }"),
        warning(CheckInterfaces.INTERFACE_DEFINED_WITH_EXTENDS),
        warning(CheckInterfaces.NON_DECLARATION_STATEMENT_IN_INTERFACE));

    test(
        srcs("class D {} /** @record */ class C extends D { constructor() { super(); } }"),
        warning(CheckInterfaces.INTERFACE_DEFINED_WITH_EXTENDS),
        warning(CheckInterfaces.NON_DECLARATION_STATEMENT_IN_INTERFACE));
    test(
        srcs("class D {} /** @record */ class C extends D { constructor() { super(x); } }"),
        warning(CheckInterfaces.INTERFACE_DEFINED_WITH_EXTENDS),
        warning(CheckInterfaces.NON_DECLARATION_STATEMENT_IN_INTERFACE));
  }

  @Test
  public void testInterfaceComputedProperties() {
    testSame(
        "/** @interface */  class C { ['f']() { return 1; }}",
        CheckInterfaces.INTERFACE_CLASS_NONSTATIC_METHOD_NOT_EMPTY);
  }

  @Test
  public void testInterfaceGetters() {
    testSame(
        "/** @interface */  class C { get One() { return 1; }}",
        CheckInterfaces.INTERFACE_CLASS_NONSTATIC_METHOD_NOT_EMPTY);
  }

  @Test
  public void testInterfaceSetters() {
    testSame(
        lines(
            "/** @interface */", //
            "class C {",
            "  set One(x) {",
            "   this.one = x;",
            "  }",
            "}"),
        CheckInterfaces.INTERFACE_CLASS_NONSTATIC_METHOD_NOT_EMPTY);
  }

  @Test
  public void testInterfaceEs6ClassDeclaration_havingStaticMethod() {
    testSame(
        srcs(
            lines(
                "/** @interface */",
                "class I {",
                "  constructor() {}",
                "  static foo() {}",
                "}",
                "I.foo();")),
        warning(CheckInterfaces.STATIC_MEMBER_FUNCTION_IN_INTERFACE_CLASS)
            .withMessageContaining(
                "Consider pulling out the static method into a flat name as I_foo"));
  }

  @Test
  public void testInterfaceEs6ClassAssignment_havingStaticMethod() {
    testSame(
        srcs(
            lines(
                "/** @interface */",
                "let I = class {",
                "  constructor() {}",
                "  static foo() {}",
                "}",
                "I.foo();")),
        warning(CheckInterfaces.STATIC_MEMBER_FUNCTION_IN_INTERFACE_CLASS)
            .withMessageContaining(
                "Consider pulling out the static method into a flat name as I_foo"));
  }

  @Test
  public void testInterfaceEs6ClassAssignment_havingStaticMethod2() {
    testSame(
        srcs(
            lines(
                "let C;",
                "/** @interface */",
                "C.I = class {",
                "  constructor() {}",
                "  static foo() {}",
                "}",
                "C.I.foo();")),
        warning(CheckInterfaces.STATIC_MEMBER_FUNCTION_IN_INTERFACE_CLASS)
            .withMessageContaining(
                "Consider pulling out the static method into a flat name as C.I_foo"));
  }

  @Test
  public void testInterfaceWithFieldDeclarations_missingJDoc() {
    testSame(
        "/** @interface */ function A() { this.foo; }",
        CheckInterfaces.MISSING_JSDOC_IN_DECLARATION_STATEMENT);

    testSame(
        "/** @interface */ class C { constructor() { this.foo; }}",
        CheckInterfaces.MISSING_JSDOC_IN_DECLARATION_STATEMENT);

    // in ES6 modules
    testSame(
        "export /** @interface */ function A() {this.foo; }",
        CheckInterfaces.MISSING_JSDOC_IN_DECLARATION_STATEMENT);
  }

  @Test
  public void testRecordWithFieldDeclarations() {
    testSame(
        lines(
            "/** @record */",
            "function R() {",
            "  /** @type {string} */",
            "  this.foo;",
            "",
            "  /** @type {number} */",
            "  this.bar;",
            "}"));
  }

  @Test
  public void testRecordWithFieldDeclarationsMissingJSDoc() {
    testSame(
        lines(
            "/** @record */",
            "function R() {",
            "  // This should have a JSDoc.",
            "  this.noJSDoc;",
            "",
            "}"),
        CheckInterfaces.MISSING_JSDOC_IN_DECLARATION_STATEMENT);
  }

  @Test
  public void testRecordWithOtherContents() {
    testSame(
        lines(
            "/** @record */", //
            "function R() {",
            "  /** @type {string} */",
            "  let foo = '';",
            "}"),
        CheckInterfaces.NON_DECLARATION_STATEMENT_IN_INTERFACE);
  }

  @Test
  public void testInterfaceWithOtherContents() {
    testSame(
        lines(
            "/** @interface */", //
            "function R() {",
            "  /** @type {string} */",
            "  let foo = '';",
            "}"),
        CheckInterfaces.NON_DECLARATION_STATEMENT_IN_INTERFACE);
  }
}
