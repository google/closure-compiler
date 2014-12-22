/*
 * Copyright 2013 The Closure Compiler Authors.
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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.newtypes.JSType;
import com.google.javascript.jscomp.newtypes.JSTypeCreatorFromJSDoc;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for {@link NewTypeInference}.
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */

public class NewTypeInferenceTest extends CompilerTypeTestCase {
  private static final String CLOSURE_BASE = "var goog;";
  static final String DEFAULT_EXTERNS =
      CompilerTypeTestCase.DEFAULT_EXTERNS + "/** @return {string} */\n"
      + "String.prototype.toString = function() { return '' };\n"
      + "/**\n"
      + " * @constructor\n"
      + " * @param {*=} arg\n"
      + " * @return {number}\n"
      + " */\n"
      + "function Number(arg) {}\n"
      + "/** @return {string} */\n"
      + "Number.prototype.toString = function() { return '' };\n"
      + "/**\n"
      + " * @constructor\n"
      + " * @param {*=} arg\n"
      + " * @return {boolean}\n"
      + " */\n"
      + "function Boolean(arg) {}\n"
      + "/** @return {string} */\n"
      + "Boolean.prototype.toString = function() { return '' };";

  private NewTypeInference parseAndTypeCheck(String externs, String js) {
    setUp();
    CompilerOptions options = compiler.getOptions();
    options.setClosurePass(true);
    options.setWarningLevel(
        DiagnosticGroups.CHECK_VARIABLES, CheckLevel.WARNING);
    compiler.init(Lists.newArrayList(SourceFile.fromCode("[externs]", externs)),
        Lists.newArrayList(SourceFile.fromCode("[testcode]", js)), options);

    Node externsRoot =
        compiler.getInput(new InputId("[externs]")).getAstRoot(compiler);
    Node astRoot =
        compiler.getInput(new InputId("[testcode]")).getAstRoot(compiler);

    assertEquals("parsing error: " + Joiner.on(", ").join(compiler.getErrors()),
        0, compiler.getErrorCount());
    assertEquals(
        "parsing warning: " + Joiner.on(", ").join(compiler.getWarnings()), 0,
        compiler.getWarningCount());

    GlobalTypeInfo symbolTable = new GlobalTypeInfo(compiler);
    symbolTable.process(externsRoot, astRoot);
    compiler.setSymbolTable(symbolTable);
    NewTypeInference typeInf = new NewTypeInference(compiler, true);
    typeInf.process(externsRoot, astRoot);
    return typeInf;
  }

  private void checkNoWarnings(String js) {
    checkNoWarnings(DEFAULT_EXTERNS, js);
  }

  private void checkNoWarnings(String externs, String js) {
    parseAndTypeCheck(externs, js);
    JSError[] warnings = compiler.getWarnings();
    assertEquals(
        "Expected no warning, but found: " + Arrays.toString(warnings) + "\n",
        0, warnings.length);
  }

  private NewTypeInference typeCheck(String js, DiagnosticType warningKind) {
    return typeCheck(DEFAULT_EXTERNS, js, warningKind);
  }

  private NewTypeInference typeCheck(
      String externs, String js, DiagnosticType warningKind) {
    Preconditions.checkNotNull(warningKind);
    return typeCheck(externs, js, ImmutableList.of(warningKind));
  }

  private NewTypeInference typeCheck(
      String js, List<DiagnosticType> warningKinds) {
    return typeCheck(DEFAULT_EXTERNS, js, warningKinds);
  }

  private NewTypeInference typeCheck(
      String externs, String js, List<DiagnosticType> warningKinds) {
    Preconditions.checkNotNull(warningKinds);
    NewTypeInference typeInf = parseAndTypeCheck(externs, js);
    if (compiler.getErrors().length > 0) {
      fail("Expected no errors, but found: "
          + Arrays.toString(compiler.getErrors()));
    }
    JSError[] warnings = compiler.getWarnings();
    String errorMessage =
        "Expected warning of type:\n"
        + "================================================================\n"
        + warningKinds
        + "================================================================\n"
        + "but found:\n"
        + "----------------------------------------------------------------\n"
        + Arrays.toString(warnings) + "\n"
        + "----------------------------------------------------------------\n";
    assertEquals(
        errorMessage + "Warning count", warningKinds.size(), warnings.length);
    for (JSError warning : warnings) {
      assertTrue(
          "Wrong warning type\n" + errorMessage,
          warningKinds.contains(warning.getType()));
    }
    return typeInf;
  }

  // Only for tests where there is a single top-level function in the program
  private void inferFirstFormalType(String js, JSType expected) {
    NewTypeInference typeInf = parseAndTypeCheck(DEFAULT_EXTERNS, js);
    JSError[] warnings = compiler.getWarnings();
    if (warnings.length > 0) {
      fail("Expected no warnings, but found: " + Arrays.toString(warnings));
    }
    assertEquals(expected, typeInf.getFormalType(0));
  }

  // Only for tests where there is a single top-level function in the program
  private void inferReturnType(String js, JSType expected) {
    NewTypeInference typeInf = parseAndTypeCheck(DEFAULT_EXTERNS, js);
    JSError[] warnings = compiler.getWarnings();
    if (warnings.length > 0) {
      fail("Expected no warnings, but found: " + Arrays.toString(warnings));
    }
    assertEquals(expected, typeInf.getReturnType());
  }

  private void checkDeclaredType(String js, String varName, JSType expected) {
    NewTypeInference typeInf = parseAndTypeCheck(DEFAULT_EXTERNS, js);
    assertEquals(expected, typeInf.getDeclaredType(varName));
  }

  public void testExterns() {
    typeCheck(
        "/** @param {Array<string>} x */ function f(x) {}; f([5]);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testVarDefinitionsInExterns() {
    checkNoWarnings(
        DEFAULT_EXTERNS + "var undecl = {};", "if (undecl) { undecl.x = 7 };");

    checkNoWarnings(
        DEFAULT_EXTERNS + "var undecl = {};",
        "function f() { if (undecl) { undecl.x = 7 }; }");

    checkNoWarnings(DEFAULT_EXTERNS + "var undecl;", "undecl(5);");

    checkNoWarnings(
        DEFAULT_EXTERNS + "/** @type {number} */ var num;", "num - 5;");

    checkNoWarnings(
        DEFAULT_EXTERNS + "var maybeStr; /** @type {string} */ var maybeStr;",
        "maybeStr - 5;");

    typeCheck(DEFAULT_EXTERNS + "/** @type {string} */ var str;", "str - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    // TODO(blickly): Warn if function in externs has body
    checkNoWarnings(
        DEFAULT_EXTERNS + "function f() {/** @type {string} */ var invisible;}",
        "invisible - 5;");
    //         VarCheck.UNDEFINED_VAR_ERROR);

    typeCheck(
        DEFAULT_EXTERNS + "/** @type {number} */ var num;",
        "/** @type {undefined} */ var x = num;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        DEFAULT_EXTERNS + "var untypedNum;",
        "function f(x) {\n"
        + " x < untypedNum;\n"
        + " untypedNum - 5;\n"
        + "}\n"
        + "f('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testThisInAtTypeFunction() {
    typeCheck(
        "/** @constructor */ function Foo(){};\n"
        + "/** @type {number} */ Foo.prototype.n;\n"
        + "/** @type {function(this:Foo)} */ function f() { this.n = 'str' };",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @type {function(this:gibberish)} */ function foo() {}",
        GlobalTypeInfo.UNRECOGNIZED_TYPE_NAME);

    checkNoWarnings("/** @constructor */\n"
        + "function Foo() {}\n"
        + "var /** function(this:Foo) */ x = function() {};");

    checkNoWarnings("/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @param {function(this:Foo)} x */\n"
        + "function f(x) {}\n"
        + "f(/** @type {function(this:Foo)} */ (function() {}));");

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() { /** @type {number} */ this.prop = 1; }\n"
        + "/** @type {function(this:Foo)} */\n"
        + "function f() { this.prop = 'asdf'; }",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @constructor */\n"
        + "function Bar() {}\n"
        + "/** @param {function(this:Foo)} x */\n"
        + "function f(x) {}\n"
        + "f(/** @type {function(this:Bar)} */ (function() {}));",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @constructor */\n"
        + "function High() {}\n"
        + "/** @constructor @extends {High} */\n"
        + "function Low() {}\n"
        + "function f(/** function(this:Low) */ low,\n"
        + "           /** function(this:High) */ high) {\n"
        + "  var fun = (1 < 2) ? low : high;\n"
        + "  var /** function(this:High) */ f2 = fun;\n"
        + "  var /** function(this:Low) */ f3 = fun;\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @constructor */\n"
        + "function High() {}\n"
        + "/** @constructor @extends {High} */\n"
        + "function Low() {}\n"
        + "function f(/** function(function(this:Low)) */ low,\n"
        + "           /** function(function(this:High)) */ high) {\n"
        + "  var fun = (1 < 2) ? low : high;\n"
        + "  var /** function(function(this:High)) */ f2 = fun;\n"
        + "  var /** function(function(this:Low)) */ f3 = fun;\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/**\n"
        + " * @constructor\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " */\n"
        + "function Foo(x) {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {function(this:Foo<T>)} fun\n"
        + " */\n"
        + "function f(fun) { return fun; }\n"
        + "var /** function(this:Foo<string>) */ x =\n"
        + "    f(/** @type {function(this:Foo<number>)} */ (function() {}));",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testThisInFunctionJsdoc() {
    typeCheck(
        "/** @constructor */ function Foo() {};\n"
        + "/** @type {number} */ Foo.prototype.n;\n"
        + "/** @this {Foo} */\n"
        + "function f() { this.n = 'str'; }",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @this {gibberish} */ function foo() {}",
        GlobalTypeInfo.UNRECOGNIZED_TYPE_NAME);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() { /** @type {number} */ this.prop = 1; }\n"
        + "/** @this {Foo} */\n"
        + "function f() { this.prop = 'asdf'; }",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  // TODO(dimvar): we must warn when a THIS fun isn't called as a method
  public void testDontCallMethodAsFunction() {
    checkNoWarnings("/** @type{function(this: Object)} */\n"
        + "function f() {}\n"
        + "f();");

    checkNoWarnings("/** @constructor */\n"
        + "function Foo() {}\n"
        + "Foo.prototype.method = function() {};\n"
        + "var f = (new Foo).method;\n"
        + "f();");
  }

  public void testNewInFunctionJsdoc() {
    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "function h(/** function(new:Foo,...[number]):number */ f) {\n"
        + "  (new f()) - 5;\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/**\n"
        + " * @constructor\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " */\n"
        + "function Foo(x) {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {function(new:Foo<T>)} fun\n"
        + " */\n"
        + "function f(fun) { return fun; }\n"
        + "/** @type {function(new:Foo<number>)} */\n"
        + "function f2() {}\n"
        + "var /** function(new:Foo<string>) */ x = f(f2);",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings("/** @constructor */\n"
        + "function Foo() {}\n"
        + "function f(x) {\n"
        + "  x();\n"
        + "  var /** !Foo */ y = new x();\n"
        + "  var /** function(new:Foo, number) */ z = x;\n"
        + "}");
  }

  public void testInvalidThisReference() {
    typeCheck("this.x = 5;", CheckGlobalThis.GLOBAL_THIS);
    typeCheck("function f(x){}; f(this);", CheckGlobalThis.GLOBAL_THIS);
  }

  public void testSuperClassWithUndeclaredProps() {
    checkNoWarnings("/** @constructor */ function Error() {};\n"
        + "Error.prototype.sourceURL;\n"
        + "/** @constructor @extends {Error} */ function SyntaxError() {}");
  }

  public void testInheritMethodFromParent() {
    typeCheck(
        "/** @constructor */ function Foo() {};\n"
        + "/** @param {string} x */ Foo.prototype.method = function(x) {};\n"
        + "/** @constructor @extends {Foo} */ function Bar() {};\n"
        + "(new Bar).method(4)",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testSubClassWithUndeclaredProps() {
    checkNoWarnings("/** @constructor */ function Super() {};\n"
        + "/** @type {string} */ Super.prototype.str;\n"
        + "/** @constructor @extends {Super} */ function Sub() {};\n"
        + "Sub.prototype.str;");
  }

  public void testUseBeforeDeclaration() {
    // typeCheck("x; var x;", VariableReferenceCheck.EARLY_REFERENCE);

    // typeCheck("x = 7; var x;", VariableReferenceCheck.EARLY_REFERENCE);

    checkNoWarnings("function f() { return 9; }\n"
        + "var x = f();\n"
        + "x - 7;");
  }

  // public void testUseWithoutDeclaration() {
  //   typeCheck("x;", VarCheck.UNDEFINED_VAR_ERROR);
  //   typeCheck("x = 7;", VarCheck.UNDEFINED_VAR_ERROR);
  //   typeCheck("var y = x;", VarCheck.UNDEFINED_VAR_ERROR);
  // }

  // public void testVarRedeclaration() {
  //   typeCheck(
  //       "function f(x) { var x; }",
  //       VariableReferenceCheck.REDECLARED_VARIABLE);

  //   typeCheck(
  //       "function f(x) { function x() {} }",
  //       VariableReferenceCheck.REDECLARED_VARIABLE);

  //   typeCheck(
  //       "function f(x) { /** @typedef {number} */ var x; }",
  //       VariableReferenceCheck.REDECLARED_VARIABLE);

  //   typeCheck(
  //       "var x; var x;",
  //       VariableReferenceCheck.REDECLARED_VARIABLE);

  //   typeCheck(
  //       "var x; function x() {}",
  //       VariableReferenceCheck.REDECLARED_VARIABLE);

  //   typeCheck(
  //       "var x; /** @typedef {number} */ var x;",
  //       VariableReferenceCheck.REDECLARED_VARIABLE);

  //   typeCheck(
  //       "function x() {} function x() {}",
  //       VariableReferenceCheck.REDECLARED_VARIABLE);

  //   typeCheck(
  //       "function x() {} var x;",
  //       VariableReferenceCheck.REDECLARED_VARIABLE);

  //   typeCheck(
  //       "function x() {} /** @typedef {number} */ var x;",
  //       VariableReferenceCheck.REDECLARED_VARIABLE);

  //   typeCheck(
  //       "/** @typedef {number} */ var x; /** @typedef {number} */ var x;",
  //       VariableReferenceCheck.REDECLARED_VARIABLE);

  //   typeCheck(
  //       "/** @typedef {number} */ var x; var x;",
  //       VariableReferenceCheck.REDECLARED_VARIABLE);

  //   typeCheck(
  //       "/** @typedef {number} */ var x; function x() {}",
  //       VariableReferenceCheck.REDECLARED_VARIABLE);

  //   typeCheck("var f = function g() {}; function f() {};",
  //       VariableReferenceCheck.REDECLARED_VARIABLE);

  //   typeCheck("var f = function g() {}; var f = function h() {};",
  //       VariableReferenceCheck.REDECLARED_VARIABLE);

  //   checkNoWarnings("var g = function f() {}; var h = function f() {};");

  //   typeCheck(
  //       "var x; /** @enum */ var x = { ONE: 1 };",
  //       VariableReferenceCheck.REDECLARED_VARIABLE);

  //   typeCheck(
  //       "/** @enum */ var x = { ONE: 1 }; var x;",
  //       VariableReferenceCheck.REDECLARED_VARIABLE);
  // }

  public void testDeclaredVariables() {
    typeCheck("var /** null */ obj = 5;", NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "var /** ?number */ n = true;", NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testEmptyBlockPropagation() {
    typeCheck(
        "var x = 5; { }; var /** string */ s = x",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testForLoopInference() {
    typeCheck(
        "var x = 5;\n"
        + "for (;true;) {\n"
        + "  x = 'str';\n"
        + "}\n"
        + "var /** (string|number) */ y = x;\n"
        + "(function(/** string */ s){})(x);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "var x = 5;"
        + "while (true) {"
        + "  x = 'str';"
        + "}\n"
        + "(function(/** string */ s){})(x);\n"
        + "var /** (string|number) */ y = x;",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "while (true) {"
        + "  var x = 'str';"
        + "}\n"
        + "var /** (string|undefined) */ y = x;\n"
        + "(function(/** string */ s){})(x);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "for (var x = 5; x < 10; x++) {}\n"
        + "(function(/** string */ s){})(x);\n"
        + "var /** number */ y = x;",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testConditionalSpecialization() {
    checkNoWarnings("var x, y = 5;\n"
        + "if (true) {\n"
        + "  x = 5;\n"
        + "} else {\n"
        + "  x = 'str';\n"
        + "}\n"
        + "if (x === 5) {\n"
        + "  y = x;\n"
        + "}\n"
        + "y - 5");

    checkNoWarnings("var x, y = 5;\n"
        + "if (true) {\n"
        + "  x = 5;\n"
        + "} else {\n"
        + "  x = null;\n"
        + "}\n"
        + "if (x !== null) {\n"
        + "  y = x;\n"
        + "}\n"
        + "y - 5");

    checkNoWarnings("var x, y;\n"
        + "if (true) {\n"
        + "  x = 5;\n"
        + "} else {\n"
        + "  x = null;\n"
        + "}\n"
        + "if (x === null) {\n"
        + "  y = 5;"
        + "} else {\n"
        + "  y = x;\n"
        + "}\n"
        + "y - 5");

    checkNoWarnings("var numOrNull = true ? null : 1\n"
        + "if (null === numOrNull) { var /** null */ n = numOrNull; }");
  }

  public void testUnspecializedStrictComparisons() {
    typeCheck(
        "var /** number */ n = (1 === 2);",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testAndOrConditionalSpecialization() {
    checkNoWarnings("var x, y = 5;\n"
        + "if (true) {\n"
        + "  x = 5;\n"
        + "} else if (true) {\n"
        + "  x = null;\n"
        + "}\n"
        + "if (x !== null && x !== undefined) {\n"
        + "  y = x;\n"
        + "}\n"
        + "y - 5");

    checkNoWarnings("var x, y;\n"
        + "if (true) {\n"
        + "  x = 5;\n"
        + "} else if (true) {\n"
        + "  x = null;\n"
        + "}\n"
        + "if (x === null || x === void 0) {\n"
        + "  y = 5;\n"
        + "} else {\n"
        + "  y = x;\n"
        + "}\n"
        + "y - 5");

    typeCheck(
        "var x, y = 5;\n"
        + "if (true) {\n"
        + "  x = 5;\n"
        + "} else if (true) {\n"
        + "  x = null;\n"
        + "}\n"
        + "if (x === null || x === undefined) {\n"
        + "  y = x;\n"
        + "}\n"
        + "var /** (number|null|undefined) **/ z = y;\n"
        + "(function(/** (number|null) */ x){})(y);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "var x, y;\n"
        + "if (true) {\n"
        + "  x = 5;\n"
        + "} else if (true) {\n"
        + "  x = null;\n"
        + "}\n"
        + "if (x !== null && x !== undefined) {\n"
        + "  y = 5;\n"
        + "} else {\n"
        + "  y = x;\n"
        + "}\n"
        + "var /** (number|null|undefined) **/ z = y;\n"
        + "(function(/** (number|null) */ x){})(y);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("var x, y = 5;\n"
        + "if (true) {\n"
        + "  x = 5;\n"
        + "} else {\n"
        + "  x = 'str';\n"
        + "}\n"
        + "if (x === 7 || x === 8) {\n"
        + "  y = x;\n"
        + "}\n"
        + "y - 5");

    typeCheck(
        "/** @constructor */ function C(){}\n"
        + "var obj = new C;\n"
        + "if (obj || false) { 123, obj.asdf; }",
        TypeCheck.INEXISTENT_PROPERTY);

    checkNoWarnings("function f(/** (number|string) */ x) {\n"
        + "  (typeof x === 'number') && (x - 5);\n"
        + "}");

    checkNoWarnings("function f(/** (number|string|null) */ x) {\n"
        + "  (x && (typeof x === 'number')) && (x - 5);\n"
        + "}");

    typeCheck(
        "function f(/** (number|string|null) */ x) {\n"
        + "  (x && (typeof x === 'string')) && (x - 5);\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(/** (number|string|null) */ x) {\n"
        + "  typeof x === 'string' && x;\n"
        + "  x < 'asdf';\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testLoopConditionSpecialization() {
    checkNoWarnings("var x = true ? null : 'str';\n"
        + "while (x !== null) {}\n"
        + "var /** null */ y = x;");

    checkNoWarnings("var x = true ? null : 'str';\n"
        + "for (;x !== null;) {}\n"
        + "var /** null */ y = x;");

    checkNoWarnings("for (var x = true ? null : 'str'; x === null;) {}\n"
        + "var /** string */ y = x;");

    checkNoWarnings("var x;\n"
        + "for (x = true ? null : 'str'; x === null;) {}\n"
        + "var /** string */ y = x;");

    checkNoWarnings("var x = true ? null : 'str';\n"
        + "do {} while (x === null);\n"
        + "var /** string */ y = x;");
  }

  public void testVarDecls() {
    checkDeclaredType("/** @type {number} */ var x;", "x", JSType.NUMBER);

    checkDeclaredType(
        "var /** number */ x, /** string */ y;", "x", JSType.NUMBER);

    checkDeclaredType(
        "var /** number */ x, /** string */ y;", "y", JSType.STRING);

    typeCheck("/** @type {number} */ var x, y;", TypeCheck.MULTIPLE_VAR_DEF);

    typeCheck(
        "/** @type {number} */ var /** number */ x;",
        GlobalTypeInfo.DUPLICATE_JSDOC);

    typeCheck(
        "var /** number */ x = 5, /** string */ y = 6;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "var /** number */ x = 'str', /** string */ y = 'str2';",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testBadInitialization() {
    typeCheck(
        "/** @type {string} */ var s = 123;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testBadAssignment() {
    typeCheck(
        "/** @type {string} */ var s; s = 123;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testBadArithmetic() {
    typeCheck("123 - 'str';", NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck("123 * 'str';", NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck("123 / 'str';", NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck("123 % 'str';", NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "var y = 123; var x = 'str'; var z = x - y;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "var y = 123; var x; var z = x - y;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings("+true;"); // This is considered an explicit coercion

    typeCheck("true + 5;", NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck("5 + true;", NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testTypeAfterIF() {
    typeCheck(
        "var x = true ? 1 : 'str'; x - 1;",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testSimpleBwdPropagation() {
    inferFirstFormalType("function f(x) { x - 5; }", JSType.NUMBER);

    inferFirstFormalType("function f(x) { x++; }", JSType.NUMBER);

    inferFirstFormalType("function f(y) { var x = y; x - 5; }", JSType.NUMBER);

    inferFirstFormalType(
        "function f(y) { var x; x = y; x - 5; }", JSType.NUMBER);

    inferFirstFormalType(
        "function f(x) { x + 5; }", JSType.join(JSType.NUMBER, JSType.STRING));
  }

  public void testSimpleReturn() {
    inferReturnType("function f(x) {}", JSType.UNDEFINED);

    inferReturnType("function f(x) { return; }", JSType.UNDEFINED);

    inferReturnType("function f(x) { return 123; }", JSType.NUMBER);

    inferReturnType(
        "function f(x) { if (x) {return 123;} else {return 'asdf';} }",
        JSType.join(JSType.NUMBER, JSType.STRING));

    inferReturnType(
        "function f(x) { if (x) {return 123;} }",
        JSType.join(JSType.NUMBER, JSType.UNDEFINED));

    inferReturnType(
        "function f(x) { var y = x; y - 5; return x; }", JSType.NUMBER);
  }

  public void testComparisons() {
    checkNoWarnings(
        "1 < 0; 'a' < 'b'; true < false; null < null; undefined < undefined;");

    checkNoWarnings(
        "/** @param {{ p1: ?, p2: ? }} x */ function f(x) { x.p1 < x.p2; }");

    checkNoWarnings("function f(x, y) { x < y; }");

    typeCheck(
        "var x = 1; var y = true ? 1 : 'str'; x < y;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "var x = 'str'; var y = 1; x < y;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    inferReturnType(
        "function f(x) {\n"
        + "  var y = 1;\n"
        + "  x < y;\n"
        + "  return x;\n"
        + "}",
        JSType.NUMBER);

    checkNoWarnings("function f(x) {\n"
        + "  var y = x, z = 7;\n"
        + "  y < z;\n"
        + "}");
  }

  public void testFunctionJsdoc() {
    inferReturnType(
        "/** @return {number} */\n"
        + "function f() { return 1; }",
        JSType.NUMBER);

    inferReturnType(
        "/** @param {number} n */\n"
        + "function f(n) { return n; }",
        JSType.NUMBER);

    checkNoWarnings("/** @param {number} n */\n"
        + "function f(n) { n < 5; }");

    typeCheck(
        "/** @param {string} n */\n"
        + "function f(n) { n < 5; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @return {string} */\n"
        + "function f() { return 1; }",
        NewTypeInference.RETURN_NONDECLARED_TYPE);

    typeCheck(
        "/** @return {string} */\n"
        + "function f() { return; }",
        NewTypeInference.RETURN_NONDECLARED_TYPE);

    inferFirstFormalType(
        "/** @return {string} */\n"
        + "function f(s) { return s; }",
        JSType.STRING);

    typeCheck(
        "/** @return {number} */\n"
        + "function f() {}",
        CheckMissingReturn.MISSING_RETURN_STATEMENT);

    typeCheck(
        "/** @return {(undefined|number)} */\n"
        + "function f() { if (true) { return 'str'; } }",
        NewTypeInference.RETURN_NONDECLARED_TYPE);

    typeCheck(
        "/** @param {function(number)} fun */\n"
        + "function f(fun) {}\n"
        + "f(function (/** string */ s) {});",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @param {number} n */ function f(/** number */ n) {}",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    checkNoWarnings("/** @constructor */ var Foo = function() {}; new Foo;");

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @param {number} x */ Foo.prototype.method = function(x) {};\n"
        + "(new Foo).method('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.method = /** @param {number} x */ function(x) {};\n"
        + "(new Foo).method('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.method = function(/** number */ x) {};\n"
        + "(new Foo).method('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @type {function(number)} */ function f(x) {}; f('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @type {number} */ function f() {}",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "/** @type {function():number} */\n"
        + "function /** number */ f() { return 1; }",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    checkNoWarnings("function f(/** function(number) */ fnum, floose, cond) {\n"
        + "  var y;\n"
        + "  if (cond) {\n"
        + "    y = fnum;\n"
        + "  } else {\n"
        + "    floose();\n"
        + "    y = floose;\n"
        + "  }\n"
        + "  return y;\n"
        + "}");

    typeCheck(
        "/** @param {function(): *} x */ function g(x) {}\n"
        + "/** @param {function(number): string} x */ function f(x) {\n"
        + "  g(x);\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "var x = {}; x.a = function(/** string */ x) {}; x.a(123);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("/** @param {function(...)} x */ function f(x) {}");

    checkNoWarnings("/**\n"
        + " * @interface\n"
        + " */\n"
        + "function A() {};\n"
        + "/** @return {number} */\n"
        + "A.prototype.foo = function() {};");

    typeCheck(
        "/** @param {number} x */ function f(y) {}",
        GlobalTypeInfo.INEXISTENT_PARAM);
  }

  public void testFunctionSubtyping() {
    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @constructor */\n"
        + "function Bar() {}\n"
        + "function f(/** function(new:Foo) */ x) {}\n"
        + "f(Bar);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "function f(/** function(new:Foo) */ x) {}\n"
        + "f(function() {});",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @constructor @extends {Foo} */\n"
        + "function Bar() {}\n"
        + "function f(/** function(new:Foo) */ x) {}\n"
        + "f(Bar);");
  }

  public void testFunctionJoin() {
    checkNoWarnings("/** @constructor */\n"
        + "function Foo() {}\n"
        + "/**\n"
        + " * @param {function(new:Foo, (number|string))} x \n"
        + " * @param {function(new:Foo, number)} y \n"
        + " */\n"
        + "function f(x, y) {\n"
        + "  var z = 1 < 2 ? x : y;\n"
        + "  return new z(123);\n"
        + "}");

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @constructor */\n"
        + "function Bar() {}\n"
        + "/**\n"
        + " * @param {function(new:Foo)} x \n"
        + " * @param {function(new:Bar)} y \n"
        + " */\n"
        + "function f(x, y) {\n"
        + "  var z = 1 < 2 ? x : y;\n"
        + "  return new z();\n"
        + "}",
        NewTypeInference.NOT_A_CONSTRUCTOR);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "function f(/** function(new:Foo) */ x, /** function() */ y) {\n"
        + "  var z = 1 < 2 ? x : y;\n"
        + "  return new z();\n"
        + "}",
        NewTypeInference.NOT_A_CONSTRUCTOR);

    checkNoWarnings("/** @constructor */\n"
        + "function Foo() {}\n"
        + "function f(/** function(new:Foo) */ x, /** function() */ y) {\n"
        + "  var z = 1 < 2 ? x : y;\n"
        + "  return z();\n"
        + "}");
  }

  public void testFunctionMeet() {
    checkNoWarnings("/** @constructor */\n"
        + "function Foo() {}\n"
        + "/**\n"
        + " * @param {function(new:Foo, (number|string))} x \n"
        + " * @param {function(new:Foo, number)} y \n"
        + " */\n"
        + "function f(x, y) { if (x === y) { return x; } }");
  }

  public void testRecordWithoutTypesJsdoc() {
    typeCheck(
        "function f(/** {a, b} */ x) {}\n"
        + "f({c: 123});",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testBackwardForwardPathologicalCase() {
    inferFirstFormalType("function f(x) { var y = 5; y < x; }", JSType.NUMBER);
  }

  public void testTopInitialization() {
    checkNoWarnings("function f(x) { var y = x; y < 5; }");

    checkNoWarnings("function f(x) { x < 5; }");

    typeCheck(
        "function f(x) { x - 5; x < 'str'; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(x) {\n"
        + "  var y = x; y - 5; y < 'str';\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  // public void testMultipleFunctions() {
  //   typeCheck("function g() {};\n function f(x) { var x; };",
  //       VariableReferenceCheck.REDECLARED_VARIABLE);

  //   typeCheck("function f(x) { var x; };\n function g() {};",
  //       VariableReferenceCheck.REDECLARED_VARIABLE);
  // }

  public void testSimpleCalls() {
    typeCheck("function f() {}; f(5);", TypeCheck.WRONG_ARGUMENT_COUNT);

    typeCheck("function f(x) { x-5; }; f();", TypeCheck.WRONG_ARGUMENT_COUNT);

    typeCheck(
        "/** @return {number} */ function f() { return 1; }\n"
        + "var /** string */ s = f();",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "function f(/** number */ x) {}; f(true);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(/** boolean */ x) {}\n"
        + "function g() { f(123); }",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(/** void */ x) {}\n"
        + "function g() { f(123); }",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(/** boolean */ x) {}\n"
        + "function g(x) {\n"
        + "  var /** string */ s = x;\n"
        + "  f(x < 7);\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(/** number */ x) {}\n"
        + "function g(x, y) {\n"
        + "  y < x;\n"
        + "  f(x);\n"
        + "  var /** string */ s = y;\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testObjectType() {
    checkNoWarnings("/** @constructor */ function Foo() {}\n"
        + "function takesObj(/** Object */ x) {}\n"
        + "takesObj(new Foo);");

    checkNoWarnings("function takesObj(/** Object */ x) {}\n"
        + "takesObj(null);");

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "function /** Object */ returnsObj() { return {}; }\n"
        + "function takesFoo(/** Foo */ x) {}\n"
        + "takesFoo(returnsObj());",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testCallsWithComplexOperator() {
    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @constructor */ function Bar() {}\n"
        + "function fun(cond, /** !Foo */ f, /** !Bar */ g) {\n"
        + "  (cond ? f : g)();\n"
        + "}",
        TypeCheck.NOT_CALLABLE);
  }

  public void testDeferredChecks() {
    typeCheck(
        "function f() { return 'str'; }\n"
        + "function g() { f() - 5; }",
        NewTypeInference.INVALID_INFERRED_RETURN_TYPE);

    typeCheck(
        "function f(x) { x - 5; }\n"
        + "f(5 < 6);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(x, y) { x - y; }\n"
        + "f(5);",
        TypeCheck.WRONG_ARGUMENT_COUNT);

    typeCheck(
        "function f() { return 'str'; }\n"
        + "function g() { var x = f(); x - 7; }",
        NewTypeInference.INVALID_INFERRED_RETURN_TYPE);

    typeCheck(
        "function f(/** number */ x, y) { return x-y; }\n"
        + "f(5, 'str');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @return {number} */ function f(x) { return x; }\n"
        + "f('str');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(/** number */ x) { return x; }\n"
        + "function g(x) {\n"
        + "  var /** string */ s = f(x);\n"
        + "};",
        NewTypeInference.INVALID_INFERRED_RETURN_TYPE);

    typeCheck(
        "function f() { new Foo('asdf'); }\n"
        + "/** @constructor */ function Foo(x) { x - 5; }",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("/** @constructor */\n"
        + "function Arr() {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {...T} var_args\n"
        + " */\n"
        + "Arr.prototype.push = function(var_args) {};\n"
        + "function f(x) {}\n"
        + "var renameByParts = function(parts) {\n"
        + "  var mapped = new Arr();\n"
        + "  mapped.push(f(parts));\n"
        + "};");

    // Here we don't want a deferred check and an INVALID_INFERRED_RETURN_TYPE
    // warning b/c the return type is declared.
    typeCheck(
        "/** @return {string} */ function foo(){ return 'str'; }\n"
        + "function g() { foo() - 123; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f() {"
        + " function x() {};\n"
        + " function g() { x(1); }"
        + " g();"
        + "}",
        TypeCheck.WRONG_ARGUMENT_COUNT);

    // We used to erroneously create a deferred check for the call to f
    // (and crash as a result), because we had a bug where the top-level
    // function was not being shadowed by the formal parameter.
    checkNoWarnings("function f() { return 123; }\n"
        + "var outer = 123;\n"
        + "function g(/** function(number) */ f) {\n"
        + "  f(123) < 'str';\n"
        + "  return outer;\n"
        + "}");
  }

  public void testFunctionsInsideFunctions() {
    typeCheck(
        "(function() {\n"
        + "  function f() {}; f(5);\n"
        + "})();",
        TypeCheck.WRONG_ARGUMENT_COUNT);

    typeCheck(
        "(function() {\n"
        + "  function f() { return 'str'; }\n"
        + "  function g() { f() - 5; }\n"
        + "})();",
        NewTypeInference.INVALID_INFERRED_RETURN_TYPE);

    typeCheck(
        "var /** number */ x;\n"
        + "function f() { x = 'str'; }",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "var x;\n"
        + "function f() { x - 5; x < 'str'; }",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testCrossScopeWarnings() {
    typeCheck(
        "function f() {\n"
        + "  x < 'str';\n"
        + "}"
        + "var x = 5;\n"
        + "f()",
        NewTypeInference.CROSS_SCOPE_GOTCHA);

    typeCheck( // CROSS_SCOPE_GOTCHA is only for undeclared variables
        "/** @type {string} */ var s;\n"
        + "function f() {\n"
        + "  s = 123;\n"
        + "}\n"
        + "f();",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings("function g(x) {\n"
        + "  function f() { x < 'str'; z < 'str'; x = 5; }\n"
        + "  var z = x;\n"
        + "  f();\n"
        + "  x - 5;\n"
        + "  z < 'str';\n"
        + "}");

    // TODO(dimvar): we can't do this yet; requires more info in the summary
    // checkNoWarnings(
    //     "/** @constructor */\n" +
    //     "function Foo() {\n" +
    //     "  /** @type{?Object} */ this.prop = null;\n" +
    //     "}\n" +
    //     "Foo.prototype.initProp = function() { this.prop = {}; };\n" +
    //     "var obj = new Foo();\n" +
    //     "if (obj.prop == null) {\n" +
    //     "  obj.initProp();\n" +
    //     "  obj.prop.a = 123;\n" +
    //     "}");
  }

  public void testTrickyUnknownBehavior() {
    checkNoWarnings("function f(/** function() */ x, cond) {\n"
        + "  var y = cond ? x() : 5;\n"
        + "  y < 'str';\n"
        + "}");

    checkNoWarnings("/** @param {function() : ?} x */ function f(x, cond) {\n"
        + "  var y = cond ? x() : 5;\n"
        + "  y < 'str';\n"
        + "}");

    checkNoWarnings("function f(/** function() */ x) {\n"
        + "  x() < 'str';\n"
        + "}");

    typeCheck(
        "function g() { return {}; }\n"
        + "function f() {\n"
        + "  var /** ? */ x = g();\n"
        + "  return x.y;\n"
        + "}",
        NewTypeInference.INVALID_INFERRED_RETURN_TYPE);

    checkNoWarnings("function g() { return {}; }\n"
        + "function f() {\n"
        + "  var /** ? */ x = g()\n"
        + "  x.y = 5;\n"
        + "}");

    checkNoWarnings("function g(x) { return x; }\n"
        + "function f(z) {\n"
        + "  var /** ? */ x = g(z);\n"
        + "  x.y2 = 123;\n"
        + // specializing to a loose object here
        "  return x.y1 - 5;\n"
        + "}");
  }

  public void testDeclaredFunctionTypesInFormals() {
    typeCheck(
        "function f(/** function():number */ x) {\n"
        + "  var /** string */ s = x();\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "function f(/** function(number) */ x) {\n"
        + "  x(true);\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function g(x, y, /** function(number) */ f) {\n"
        + "  y < x;\n"
        + "  f(x);\n"
        + "  var /** string */ s = y;\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(x) {\n"
        + "  var y = x(); y - 5; y < 'str';\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @param {function():?} x */ function f(x) {\n"
        + "  var y = x(); y - 5; y < 'str';\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(/** ? */ x) { x < 'asdf'; x - 5; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings(
        "/** @param {function(number): string} x */ function g(x) {}\n"
        + "/** @param {function(number): string} x */ function f(x) {\n"
        + "  g(x);\n"
        + "}");

    checkNoWarnings("/** @param {function(number): *} x */ function g(x) {}\n"
        + "/** @param {function(*): string} x */ function f(x) {\n"
        + "  g(x);\n"
        + "}");

    typeCheck(
        "/** @param {function(*): string} x */ function g(x) {}\n"
        + "/** @param {function(number): string} x */ function f(x) {\n"
        + "  g(x);\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @param {function(number): string} x */ function g(x) {}\n"
        + "/** @param {function(number): *} x */ function f(x) {\n"
        + "  g(x);\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testSpecializedFunctions() {
    typeCheck(
        "function f(/** function(string) : number */ x) {\n"
        + "  if (x('str') === 5) {\n"
        + "    x(5);\n"
        + "  }\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(/** function(string) : string */ x) {\n"
        + "  if (x('str') === 5) {\n"
        + "    x(5);\n"
        + "  }\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(/** function(string) */ x, y) {\n"
        + "  y(1);\n"
        + "  if (x === y) {\n"
        + "    x(5);\n"
        + "  }\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(x) {\n"
        + "  if (x === null) {\n"
        + "    return 5;\n"
        + "  } else {\n"
        + "    return x - 43;\n"
        + "  }\n"
        + "}\n"
        + "f('str');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("/** @const */ var goog = {};\n"
        + "/** @type {!Function} */ goog.abstractMethod = function(){};\n"
        + "/** @constructor */ function Foo(){};\n"
        + "/** @return {!Foo} */ Foo.prototype.clone = goog.abstractMethod;\n"
        + "/** @constructor @extends {Foo} */\n"
        + "function Bar() {}\n"
        + "/** @return {!Bar} */ Bar.prototype.clone = goog.abstractMethod;");

    typeCheck(
        "/** @const */ var goog = {};\n"
        + "/** @type {!Function} */ goog.abstractMethod = function(){};\n"
        + "/** @constructor */ function Foo(){};\n"
        + "/** @return {!Foo} */ Foo.prototype.clone = goog.abstractMethod;\n"
        + "/** @constructor @extends {Foo} */\n"
        + "function Bar() {}\n"
        + "/** @return {!Bar} */ Bar.prototype.clone = goog.abstractMethod;\n"
        + "var /** null */ n = (new Bar).clone();",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testDifficultObjectSpecialization() {
    checkNoWarnings("/** @constructor */\n"
        + "function X() { this.p = 1; }\n"
        + "/** @constructor */\n"
        + "function Y() { this.p = 2; }\n"
        + "/** @param {(!X|!Y)} a */\n"
        + "function fn(a) {\n"
        + "  a.p;\n"
        + "  /** @type {!X} */ (a);\n"
        + "}");

    // Currently, two types that have a common subtype specialize to bottom
    // instead of to the common subtype. If we change that, then this test will
    // have no warnings.
    typeCheck(
        "/** @interface */\n"
        + "function High1() {}\n"
        + "/** @interface */\n"
        + "function High2() {}\n"
        + "/**\n"
        + " * @constructor\n"
        + " * @implements {High1}\n"
        + " * @implements {High2}\n"
        + " */\n"
        + "function Low() {}\n"
        + "function f(x) {\n"
        + "  var /** !High1 */ v1 = x;\n"
        + "  var /** !High2 */ v2 = x;\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    // Currently, two types that have a common subtype specialize to bottom
    // instead of to the common subtype. If we change that, then this test will
    // have no warnings, and the type of x will be !Low.
    // (We must normalize the output of specialize to avoid getting (!Med|!Low))
    typeCheck(
        "/** @interface */\n"
        + "function High1() {}\n"
        + "/** @interface */\n"
        + "function High2() {}\n"
        + "/** @interface */\n"
        + "function High3() {}\n"
        + "/**\n"
        + " * @interface\n"
        + " * @extends {High1}\n"
        + " * @extends {High2}\n"
        + " */\n"
        + "function Mid() {}\n"
        + "/**\n"
        + " * @interface\n"
        + " * @extends {Mid}\n"
        + " * @extends {High3}\n"
        + " */\n"
        + "function Low() {}\n"
        + "function f(x) {\n"
        + "  var /** !High1 */ v1 = x;\n"
        + "  var /** (!High2|!High3) */ v2 = x;\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testLooseConstructors() {
    checkNoWarnings("function f(ctor) {\n"
        + "  new ctor(1);\n"
        + "}");

    typeCheck(
        "function f(ctor) {\n"
        + "  new ctor(1);\n"
        + "}\n"
        + "/** @constructor */ function Foo(/** string */ y) {}\n"
        + "f(Foo);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testLooseFunctions() {
    checkNoWarnings("function f(x) {\n"
        + "  x(1);\n"
        + "}");

    typeCheck(
        "function f(x) {\n"
        + "  x(1);\n"
        + "}\n"
        + "function g(/** string */ y) {}\n"
        + "f(g);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("function f(x) {\n"
        + "  x(1);\n"
        + "}\n"
        + "function g(/** number */ y) {}\n"
        + "f(g);");

    checkNoWarnings("function f(x) {\n"
        + "  x(1);\n"
        + "}\n"
        + "function g(/** (number|string) */ y) {}\n"
        + "f(g);");

    typeCheck(
        "function f(x) {\n"
        + "  5 - x(1);\n"
        + "}\n"
        + "/** @return {string} */\n"
        + "function g(/** number */ y) { return ''; }\n"
        + "f(g);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("function f(x) {\n"
        + "  5 - x(1);\n"
        + "}\n"
        + "/** @return {(number|string)} */\n"
        + "function g(/** number */ y) { return 5; }\n"
        + "f(g);");

    checkNoWarnings("function f(x, y) {\n"
        + "  x(5);\n"
        + "  y(5);\n"
        + "  return x(y);\n"
        + "}");

    typeCheck(
        "function f(x) {\n"
        + "  x();\n"
        + "  return x;\n"
        + "}\n"
        + "function g() {}\n"
        + "function h() { f(g) - 5; }",
        NewTypeInference.INVALID_INFERRED_RETURN_TYPE);

    typeCheck(
        "function f(x, cond) {\n"
        + "  x();\n"
        + "  return cond ? 5 : x;\n"
        + "}\n"
        + "function g() {}\n"
        + "function h() { f(g, true) - 5; }",
        NewTypeInference.INVALID_INFERRED_RETURN_TYPE);
    // A loose function is a loose subtype of a non-loose function.
    // Traditional function subtyping would warn here.
    checkNoWarnings("function f(x) {\n"
        + "  x(5);\n"
        + "  return x;\n"
        + "}\n"
        + "function g(x) {}\n"
        + "function h() {\n"
        + "  var /** function((number|string)) */ fun = f(g);\n"
        + "}");

    typeCheck(
        "function g(/** string */ x) {}\n"
        + "function f(x, y) {\n"
        + "  y - 5;\n"
        + "  x(y);\n"
        + "  y + y;\n"
        + "}"
        + "f(g, 5)",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @return {string} */\n"
        + "function g(/** number */ x) { return 'str'; }\n"
        + "/** @return {number} */\n"
        + "function f(x) {\n"
        + "  var y = 5;\n"
        + "  var z = x(y);\n"
        + "  return z;\n"
        + "}"
        + "f(g)",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("/** @return {number} */\n"
        + "function g(/** number */ y) { return 6; }\n"
        + "function f(x, cond) {\n"
        + "  if (cond) {\n"
        + "    5 - x(1);\n"
        + "  } else {\n"
        + "    x('str') < 'str';\n"
        + "  }\n"
        + "}\n"
        + "f(g, true)\n");

    checkNoWarnings("function f(g, cond) {\n"
        + "  if (cond) {\n"
        + "    g(5, cond);\n"
        + "  }\n"
        + "}");
  }

  public void testBackwardForwardPathologicalCase2() {
    typeCheck(
        "function f(/** number */ x, /** string */ y, z) {\n"
        + "  var w = z;\n"
        + "  x < z;\n"
        + "  w < y;\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testNotCallable() {
    typeCheck(
        "/** @param {number} x */ function f(x) {\n"
        + "  x(7);\n"
        + "}",
        TypeCheck.NOT_CALLABLE);
  }

  public void testSimpleLocallyDefinedFunction() {
    typeCheck(
        "function f() { return 'str'; }\n"
        + "var x = f();\n"
        + "x - 7;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f() { return 'str'; }\n"
        + "f() - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "(function() {\n"
        + "  function f() { return 'str'; }\n"
        + "  f() - 5;\n"
        + "})();",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "(function() {\n"
        + "  function f() { return 'str'; }\n"
        + "  f() - 5;\n"
        + "})();",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testIdentityFunction() {
    checkNoWarnings("function f(x) { return x; }\n"
        + "5 - f(1);");
  }

  public void testReturnTypeInferred() {
    typeCheck(
        "function f() {\n"
        + "  var x = g();\n"
        + "  var /** string */ s = x;\n"
        + "  x - 5;\n"
        + "};\n"
        + "function g() { return 'str'};",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testGetpropOnNonObjects() {
    typeCheck("(null).foo;", NewTypeInference.PROPERTY_ACCESS_ON_NONOBJECT);

    typeCheck(
        "var /** undefined */ n;\n"
        + "n.foo;",
        NewTypeInference.PROPERTY_ACCESS_ON_NONOBJECT);

    typeCheck("var x = {}; x.foo.bar = 1;", TypeCheck.INEXISTENT_PROPERTY);

    typeCheck(
        "var /** undefined */ n;\n"
        + "n.foo = 5;",
        NewTypeInference.PROPERTY_ACCESS_ON_NONOBJECT);

    checkNoWarnings("/** @param {*} x */\n"
        + "function f(x) {\n"
        + "  if (x.prop) {\n"
        + "    var /** { prop: ? } */ y = x;\n"
        + "  }\n"
        + "}");

    // TODO(blickly): Currently, this warning is not good, referring to props of
    // BOTTOM. Ideally, we could warn about accessing a prop on undefined.
    typeCheck(
        "/** @param {undefined} x */\n"
        + "function f(x) {\n"
        + "  if (x.prop) {}\n"
        + "}",
        NewTypeInference.PROPERTY_ACCESS_ON_NONOBJECT);

    typeCheck("null[123];", NewTypeInference.PROPERTY_ACCESS_ON_NONOBJECT);

    checkNoWarnings(
        "function f(/** !Object */ x) { if (x[123]) { return 1; } }");

    typeCheck(
        "function f(/** undefined */ x) { if (x[123]) { return 1; } }",
        NewTypeInference.PROPERTY_ACCESS_ON_NONOBJECT);

    typeCheck(
        "function f(/** (number|null) */ n) {\n"
        + "  n.foo;\n"
        + "}",
        NewTypeInference.PROPERTY_ACCESS_ON_NONOBJECT);

    typeCheck(
        "function f(/** (number|null|undefined) */ n) {\n"
        + "  n.foo;\n"
        + "}",
        NewTypeInference.PROPERTY_ACCESS_ON_NONOBJECT);

    typeCheck(
        "function f(/** (!Object|number|null|undefined) */ n) {\n"
        + "  n.foo;\n"
        + "}",
        NewTypeInference.PROPERTY_ACCESS_ON_NONOBJECT);

    typeCheck(
        "/** @constructor */ function Foo(){}\n"
        + "Foo.prototype.prop;\n"
        + "function f(/** (!Foo|undefined) */ n) {\n"
        + "  n.prop;\n"
        + "}",
        NewTypeInference.NULLABLE_DEREFERENCE);

    typeCheck(
        "/** @constructor */ function Foo(){}\n"
        + "/** @type {string} */ Foo.prototype.prop1;\n"
        + "function g(/** Foo */ f) {\n"
        + "  f.prop1.prop2 = 'str';\n"
        + "};",
        NewTypeInference.NULLABLE_DEREFERENCE);
  }

  public void testNonexistentProperty() {
    typeCheck(
        "/** @param {{ a: number }} obj */\n"
        + "function f(obj) {\n"
        + "  123, obj.b;\n"
        + "  obj.b = 'str';\n"
        + "}",
        TypeCheck.INEXISTENT_PROPERTY);

    typeCheck("({}).p < 'asdf';", TypeCheck.INEXISTENT_PROPERTY);

    checkNoWarnings("(/** @type {?} */ (null)).prop - 123;");

    checkNoWarnings("(/** @type {?} */ (null)).prop += 123;");

    typeCheck("var x = {}; var y = x.a;", TypeCheck.INEXISTENT_PROPERTY);

    typeCheck("var x = {}; x.y - 3; x.y = 5;", TypeCheck.INEXISTENT_PROPERTY);

    // TODO(dimvar): fix
    // typeCheck(
    //     "function f(/** !Object */ x) {\n" +
    //     "  if (x.foo) {} else { x.foo(); }\n" +
    //     "}",
    //     TypeCheck.INEXISTENT_PROPERTY);
  }

  public void testNullableDereference() {
    typeCheck(
        "function f(/** ?{ p : number } */ o) { return o.p; }",
        NewTypeInference.NULLABLE_DEREFERENCE);

    typeCheck(
        "/** @constructor */ function Foo() { /** @const */ this.p = 5; }\n"
        + "function g(/** ?Foo */ f) { return f.p; }",
        NewTypeInference.NULLABLE_DEREFERENCE);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.p = function(){};\n"
        + "function g(/** ?Foo */ f) { f.p(); }",
        NewTypeInference.NULLABLE_DEREFERENCE);

    typeCheck(
        "var f = 5 ? function() {} : null; f();",
        NewTypeInference.NULLABLE_DEREFERENCE);

    typeCheck(
        "var f = 5 ? function(/** number */ n) {} : null; f('str');",
        ImmutableList.of(
            NewTypeInference.NULLABLE_DEREFERENCE,
            NewTypeInference.INVALID_ARGUMENT_TYPE));

    checkNoWarnings(CLOSURE_BASE + "function f(/** ?{ p : number } */ o) {\n"
        + "  goog.asserts.assert(o);\n"
        + "  return o.p;\n"
        + "}");

    checkNoWarnings(CLOSURE_BASE + "function f(/** ?{ p : number } */ o) {\n"
        + "  goog.asserts.assertObject(o);\n"
        + "  return o.p;\n"
        + "}");

    checkNoWarnings(CLOSURE_BASE + "function f(/** ?Array<string> */ a) {\n"
        + "  goog.asserts.assertArray(a);\n"
        + "  return a.length;\n"
        + "}");

    checkNoWarnings(CLOSURE_BASE + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.p = function(){};\n"
        + "function g(/** ?Foo */ f) {\n"
        + "  goog.asserts.assertInstanceof(f, Foo);\n"
        + "  f.p();\n"
        + "}");

    typeCheck(
        CLOSURE_BASE + "/** @constructor */ function Foo() {}\n"
        + "/** @constructor */ function Bar() {}\n"
        + "function g(/** !Bar */ o) {\n"
        + "  goog.asserts.assertInstanceof(o, Foo);\n"
        + "}",
        NewTypeInference.ASSERT_FALSE);

    typeCheck(
        CLOSURE_BASE + "/** @constructor */ function Foo() {}\n"
        + "function g(/** !Foo */ o) {\n"
        + "  goog.asserts.assertInstanceof(o, 42);\n"
        + "}",
        NewTypeInference.UNKNOWN_ASSERTION_TYPE);

    typeCheck(
        CLOSURE_BASE + "/** @constructor */ function Foo() {}\n"
        + "function Bar() {}\n"
        + "function g(/** !Foo */ o) {\n"
        + "  goog.asserts.assertInstanceof(o, Bar);\n"
        + "}",
        NewTypeInference.UNKNOWN_ASSERTION_TYPE);

    typeCheck(
        CLOSURE_BASE + "/** @constructor */ function Foo() {}\n"
        + "/** @interface */ function Bar() {}\n"
        + "function g(/** !Foo */ o) {\n"
        + "  goog.asserts.assertInstanceof(o, Bar);\n"
        + "}",
        NewTypeInference.UNKNOWN_ASSERTION_TYPE);
  }

  public void testAsserts() {
    typeCheck(
        CLOSURE_BASE
        + "function f(/** ({ p : string }|null|undefined) */ o) {\n"
        + "  goog.asserts.assert(o);\n"
        + "  o.p - 5;\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        CLOSURE_BASE + "/** @constructor */ function Foo() {}\n"
        + "function f(/** (Array.<string>|Foo) */ o) {\n"
        + "  goog.asserts.assert(o instanceof Array);\n"
        + "  var /** string */ s = o.length;\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        CLOSURE_BASE + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.p = function(/** number */ x){};\n"
        + "function f(/** (function(new:Foo)) */ ctor,\n"
        + "           /** ?Foo */ o) {\n"
        + "  goog.asserts.assertInstanceof(o, ctor);\n"
        + "  o.p('str');\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testDontInferBottom() {
    typeCheck(
        // Ensure we don't infer bottom for x here
        "function f(x) { var /** string */ s; (s = x) - 5; } f(9);",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testAssignToInvalidObject() {
    typeCheck(
        "n.foo = 5; var n;",
        ImmutableList.of(
            // VariableReferenceCheck.EARLY_REFERENCE,
            NewTypeInference.PROPERTY_ACCESS_ON_NONOBJECT));
  }

  public void testAssignmentDoesntFlowWrongInit() {
    typeCheck(
        "function f(/** number */ n) {\n"
        + "  n = 'typo';\n"
        + "  n - 5;\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @param {{ n: number }} x */ function f(x) {\n"
        + "  x.n = 'typo';\n"
        + "  x.n - 5;\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testPossiblyNonexistentProperties() {
    checkNoWarnings("/** @param {{ n: number }} x */ function f(x) {\n"
        + "  if (x.p) {\n"
        + "    return x.p;\n"
        + "  }\n"
        + "}");

    typeCheck(
        "/** @param {{ p : string }} x */ function reqHasPropP(x){}\n"
        + "/** @param {{ n: number }} x */ function f(x, cond) {\n"
        + "  if (cond) {\n"
        + "    x.p = 'str';\n"
        + "  }\n"
        + "  reqHasPropP(x);\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @param {{ n: number }} x */ function f(x, cond) {\n"
        + "  if (cond) { x.p = 'str'; }\n"
        + "  if (x.p) {\n"
        + "    x.p - 5;\n"
        + "  }\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(/** { n : number } */ x) {\n"
        + "  x.s = 'str';\n"
        + "  return x.inexistentProp;\n"
        + "}",
        TypeCheck.INEXISTENT_PROPERTY);
  }

  public void testDeclaredRecordTypes() {
    checkNoWarnings("/** @param {{ p: number }} x */ function f(x) {\n"
        + "  return x.p - 3;\n"
        + "}");

    typeCheck(
        "/** @param {{ p: string }} x */ function f(x) {\n"
        + "  return x.p - 3;\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @param {{ 'p': string }} x */ function f(x) {\n"
        + "  return x.p - 3;\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @param {{ p: number }} x */ function f(x) {\n"
        + "  return x.q;\n"
        + "}",
        TypeCheck.INEXISTENT_PROPERTY);

    typeCheck(
        "/** @param {{ p: string }} obj */ function f(obj, x, y) {\n"
        + "  x < y;\n"
        + "  x - 5;\n"
        + "  obj.p < y;\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings("/** @param {{ p: number }} x */ function f(x) {\n"
        + "  x.p = 3;\n"
        + "}");

    typeCheck(
        "/** @param {{ p: number }} x */ function f(x) {\n"
        + "  x.p = 'str';\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings("/** @param {{ p: number }} x */ function f(x) {\n"
        + "  x.q = 'str';\n"
        + "}");

    checkNoWarnings("/** @param {{ p: number }} x */ function f(x) {\n"
        + "  x.q = 'str';\n"
        + "}\n"
        + "/** @param {{ p: number }} x */ function g(x) {\n"
        + "  f(x);\n"
        + "}");

    typeCheck(
        "/** @param {{ p: number }} x */ function f(x) {\n"
        + "  x.q = 'str';\n"
        + "  return x.q;\n"
        + "}\n"
        + "/** @param {{ p: number }} x */ function g(x) {\n"
        + "  f(x) - 5;\n"
        + "}",
        NewTypeInference.INVALID_INFERRED_RETURN_TYPE);

    checkNoWarnings("/** @param {{ p: number }} x */ function f(x) {\n"
        + "  x.q = 'str';\n"
        + "  x.q = 7;\n"
        + "}");

    typeCheck(
        "function f(/** { prop: number} */ obj) {\n"
        + "  obj.prop = 'asdf';\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "function f(/** { prop: number} */ obj, cond) {\n"
        + "  if (cond) { obj.prop = 123; } else { obj.prop = 234; }\n"
        + "  obj.prop = 'asdf';\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings(
        "function f(/** {p: number} */ x, /** {p: (number|null)} */ y) {\n"
        + "  var z;\n"
        + "  if (true) { z = x; } else { z = y; }\n"
        + "}");

    typeCheck(
        "var /** { a: number } */ obj1 = { a: 321};\n"
        + "var /** { a: number, b: number } */ obj2 = obj1;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testSimpleObjectLiterals() {
    checkNoWarnings("/** @param {{ p: number }} obj */\n"
        + "function f(obj) {\n"
        + "  obj = { p: 123 };\n"
        + "}");

    typeCheck(
        "/** @param {{ p: number, p2: string }} obj */\n"
        + "function f(obj) {\n"
        + "  obj = { p: 123 };\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @param {{ p: number }} obj */\n"
        + "function f(obj) {\n"
        + "  obj = { p: 'str' };\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "var obj;\n"
        + "obj = { p: 123 };\n"
        + "obj.p < 'str';",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @param {{ p: number }} obj */\n"
        + "function f(obj, x) {\n"
        + "  obj = { p: x };\n"
        + "  x < 'str';\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @param {{ p: number }} obj */\n"
        + "function f(obj, x) {\n"
        + "  obj = { p: 123, q: x };\n"
        + "  obj.q - 5;\n"
        + "  x < 'str';\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);
    // An example of how record types can hide away the extra properties and
    // allow type misuse.
    checkNoWarnings("/** @param {{ p: number }} obj */\n"
        + "function f(obj) {\n"
        + "  obj.q = 123;\n"
        + "}\n"
        + "/** @param {{ p: number, q: string }} obj */\n"
        + "function g(obj) { f(obj); }");

    checkNoWarnings("/** @param {{ p: number }} obj */\n"
        + "function f(obj) {}\n"
        + "var obj = {p: 5};\n"
        + "if (true) {\n"
        + "  obj.q = 123;\n"
        + "}\n"
        + "f(obj);");

    typeCheck(
        "function f(/** number */ n) {}; f({});",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testInferPreciseTypeWithDeclaredUnknown() {
    typeCheck(
        "var /** ? */ x = 'str'; x - 123;",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testSimpleLooseObjects() {
    checkNoWarnings("function f(obj) { obj.x = 1; obj.x - 5; }");

    typeCheck(
        "function f(obj) { obj.x = 'str'; obj.x - 5; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(obj) {\n"
        + "  var /** number */ x = obj.p;\n"
        + "  obj.p < 'str';\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(obj) {\n"
        + "  var /** @type {{ p: number }} */ x = obj;\n"
        + "  obj.p < 'str';\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings("function f(obj) {\n"
        + "  obj.x = 1;\n"
        + "  return obj.x;\n"
        + "}\n"
        + "f({x: 'str'});");

    typeCheck(
        "function f(obj) {\n"
        + "  obj.x - 1;\n"
        + "}\n"
        + "f({x: 'str'});",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("function f(obj, cond) {\n"
        + "  if (cond) {\n"
        + "    obj.x = 'str';\n"
        + "  }\n"
        + "  obj.x - 5;\n"
        + "}");

    typeCheck(
        "function f(obj) {\n"
        + "  obj.x - 1;\n"
        + "  return obj;\n"
        + "}\n"
        + "var /** string */ s = (f({x: 5})).x;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }


  public void testNestedLooseObjects() {
    checkNoWarnings("function f(obj) {\n"
        + "  obj.a.b = 123;\n"
        + "}");

    typeCheck(
        "function f(obj) {\n"
        + "  obj.a.b = 123;\n"
        + "  obj.a.b < 'str';\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(obj, cond) {\n"
        + "  (cond ? obj : obj).x - 1;\n"
        + "  return obj.x;\n"
        + "}\n"
        + "f({x: 'str'}, true);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(obj) {\n"
        + "  obj.a.b - 123;\n"
        + "}\n"
        + "f({a: {b: 'str'}})",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("function f(obj) {\n"
        + "  obj.a.b = 123;\n"
        + "}\n"
        + "f({a: {b: 'str'}})");

    typeCheck(
        "function f(obj) {\n"
        + "  var o;\n"
        + "  (o = obj).x - 1;\n"
        + "  return o.x;\n"
        + "}\n"
        + "f({x: 'str'});",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(obj) {\n"
        + "  ({x: obj.foo}).x - 1;\n"
        + "}\n"
        + "f({foo: 'str'});",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(x) {\n"
        + "  ({p: x++}).p = 'str';\n"
        + "}\n"
        + "f('str');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(x) {\n"
        + "  ({p: 'str'}).p = x++;\n"
        + "}\n"
        + "f('str');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(x, y, z) {\n"
        + "  ({p: (y = x++), q: 'str'}).p = z = y;\n"
        + "  z < 'str';\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testLooseObjectSubtyping() {
    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @constructor */\n"
        + "function Bar() {}\n"
        + "function f(obj) { obj.prop - 5; }\n"
        + "var /** !Foo */ x = new Foo;\n"
        + "f(x);\n"
        + "var /** !Bar */ y = x;",
        ImmutableList.of(
            NewTypeInference.INVALID_ARGUMENT_TYPE,
            NewTypeInference.MISTYPED_ASSIGN_RHS));

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "function f(obj) { obj.prop - 5; }\n"
        + "f(new Foo);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() { /** @type {string} */ this.prop = 'str'; }\n"
        + "function f(obj) { obj.prop - 5; }\n"
        + "f(new Foo);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @constructor */ function Foo() { /** @type {number} */ this.prop = 1; }\n"
        + "function g(obj) { var /** string */ s = obj.prop; return obj; }\n"
        + "var /** !Foo */ x = g({ prop: '' });",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    // Infer obj.a as loose, don't warn at the call to f.
    checkNoWarnings("function f(obj) { obj.a.num - 5; }\n"
        + "function g(obj) {\n"
        + "  obj.a.str < 'str';\n"
        + "  f(obj);\n"
        + "}");

    // A loose object is a subtype of Array even if it has a dotted property
    checkNoWarnings("function f(/** Array<?> */ x) {}\n"
        + "function g(obj) {\n"
        + "  obj.x = 123;\n"
        + "  f(obj);\n"
        + "}");
  }

  public void testUnionOfRecords() {
    // The previous type inference doesn't warn because it keeps records
    // separate in unions.
    // We treat {x:number}|{y:number} as {x:number=, y:number=}
    typeCheck(
        "/** @param {({x:number}|{y:number})} obj */\n"
        + "function f(obj) {}\n"
        + "f({x: 5, y: 'asdf'});",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testUnionOfFunctionAndNumber() {
    checkNoWarnings("var x = function(/** number */ y){};");

    // typeCheck("var x = function(/** number */ y){}; var x = 5",
    //     VariableReferenceCheck.REDECLARED_VARIABLE);

    typeCheck(
        "var x = function(/** number */ y){}; x('str');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "var x = true ? function(/** number */ y){} : 5; x('str');",
        ImmutableList.of(
            TypeCheck.NOT_CALLABLE, NewTypeInference.INVALID_ARGUMENT_TYPE));
  }

  public void testAnonymousNominalType() {
    typeCheck(
        "function f() { return {}; }\n"
        + "/** @constructor */\n"
        + "f().Foo = function() {};",
        GlobalTypeInfo.ANONYMOUS_NOMINAL_TYPE);

    typeCheck(
        "var x = {};\n"
        + "function f() { return x; }\n"
        + "/** @constructor */\n"
        + "f().Foo = function() {};\n"
        + "new (f().Foo)();",
        GlobalTypeInfo.ANONYMOUS_NOMINAL_TYPE);
  }

  public void testFoo() {
    typeCheck(
        "/** @constructor */ function Foo() {}; Foo();",
        TypeCheck.CONSTRUCTOR_NOT_CALLABLE);

    typeCheck(
        "function Foo() {}; new Foo();", NewTypeInference.NOT_A_CONSTRUCTOR);

    checkNoWarnings("/** @constructor */ function Foo() {};\n"
        + "function reqFoo(/** Foo */ f) {};\n"
        + "reqFoo(new Foo());");

    typeCheck(
        "/** @constructor */ function Foo() {};\n"
        + "/** @constructor */ function Bar() {};\n"
        + "function reqFoo(/** Foo */ f) {};\n"
        + "reqFoo(new Bar());",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @constructor */ function Foo() {};\n"
        + "function reqFoo(/** Foo */ f) {};\n"
        + "function g() {\n"
        + "  /** @constructor */ function Foo() {};\n"
        + "  reqFoo(new Foo());\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @param {number} x */\n"
        + "Foo.prototype.method = function(x) {};\n"
        + "/** @param {!Foo} x */\n"
        + "function f(x) { x.method('asdf'); }",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testComma() {
    typeCheck(
        "var x; var /** string */ s = (x = 1, x);",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "function f(x) {\n"
        + "  var y = x;\n"
        + "  y < (123, 'asdf');\n"
        + "}\n"
        + "f(123);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testTypeof() {
    typeCheck("(typeof 'asdf') < 123;", NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(x) {\n"
        + "  var y = x;\n"
        + "  y < (typeof 123);\n"
        + "}\n"
        + "f(123);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(x) {\n"
        + "  if (typeof x === 'string') {\n"
        + "    x - 5;\n"
        + "  }\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings("function f(x) {\n"
        + "  if (typeof x != 'function') {\n"
        + "    x - 5;\n"
        + "  }\n"
        + "}");

    typeCheck(
        "function f(x) {\n"
        + "  if (typeof x == 'string') {\n"
        + "    x - 5;\n"
        + "  }\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(x) {\n"
        + "  if ('string' === typeof x) {\n"
        + "    x - 5;\n"
        + "  }\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(x) {\n"
        + "  if (typeof x === 'number') {\n"
        + "    x < 'asdf';\n"
        + "  }\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(x) {\n"
        + "  if (typeof x === 'boolean') {\n"
        + "    x - 5;\n"
        + "  }\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(x) {\n"
        + "  if (typeof x === 'undefined') {\n"
        + "    x - 5;\n"
        + "  }\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    // TODO(dimvar): if you look at how ObjectType#isLooseSubtypeOf works,
    // the loose top function happens to be a subtype of Number, so we miss the
    // warning here.
    // But we can get it back once we make function objects inherit from
    // Function; then we can see that the nominal types don't match.

    // typeCheck(
    //     "function f(x) {\n" +
    //     "  if (typeof x === 'function') {\n" +
    //     "    x - 5;\n" +
    //     "  }\n" +
    //     "}",
    //     NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings("/** @param {*} x */\n"
        + "function f(x) {\n"
        + "  if (typeof x === 'function') {\n"
        + "    x();\n"
        + "  }\n"
        + "}");

    typeCheck(
        "function f(x) {\n"
        + "  if (typeof x === 'object') {\n"
        + "    x - 5;\n"
        + "  }\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings("function f(x) {\n"
        + "  if (!(typeof x == 'number')) {\n"
        + "    x.prop;\n"
        + "  }\n"
        + "}");

    checkNoWarnings("function f(x) {\n"
        + "  if (!(typeof x == 'undefined')) {\n"
        + "    x - 5;\n"
        + "  }\n"
        + "}");

    typeCheck(
        "/** @param {*} x */\n"
        + "function f(x) {\n"
        + "  if (!(typeof x == 'undefined')) {\n"
        + "    var /** undefined */ y = x;\n"
        + "  }\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @param {*} x */\n"
        + "function f(x) {\n"
        + "  if (typeof x !== 'undefined') {\n"
        + "    var /** undefined */ y = x;\n"
        + "  }\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @param {*} x */\n"
        + "function f(x) {\n"
        + "  if (typeof x == 'undefined') {} else {\n"
        + "    var /** undefined */ y = x;\n"
        + "  }\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings("function f(/** (number|undefined) */ x) {\n"
        + "  if (typeof x !== 'undefined') {\n"
        + "    x - 5;\n"
        + "  }\n"
        + "}");

    checkNoWarnings("function f() {"
        + "  return (typeof 123 == 'number' ||"
        + "    typeof 123 == 'string' ||"
        + "    typeof 123 == 'boolean' ||"
        + "    typeof 123 == 'undefined' ||"
        + "    typeof 123 == 'function' ||"
        + "    typeof 123 == 'object' ||"
        + "    typeof 123 == 'unknown');"
        + "}");

    typeCheck(
        "function f(){ if (typeof 123 == 'numbr') return 321; }",
        TypeValidator.UNKNOWN_TYPEOF_VALUE);

    typeCheck(
        "switch (typeof 123) { case 'foo': }",
        TypeValidator.UNKNOWN_TYPEOF_VALUE);

    checkNoWarnings("/** @constructor */ function Foo() {}\n"
        + "/** @param {(number|null|Foo)} x */\n"
        + "function f(x) {\n"
        + "  if (!(typeof x === 'object')) {\n"
        + "    var /** number */ n = x;\n"
        + "  }\n"
        + "}");

    checkNoWarnings("/** @param {(number|function(number):number)} x */\n"
        + "function f(x) {\n"
        + "  if (!(typeof x === 'function')) {\n"
        + "    var /** number */ n = x;\n"
        + "  }\n"
        + "}");
  }

  public void testAssignWithOp() {
    typeCheck(
        "function f(x) {\n"
        + "  var y = x, z = 0;\n"
        + "  y < (z -= 123);\n"
        + "}\n"
        + "f('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(x) {\n"
        + "  var y = x, z = { prop: 0 };\n"
        + "  y < (z.prop -= 123);\n"
        + "}\n"
        + "f('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(x) {\n"
        + "  var z = { prop: 0 };\n"
        + "  x < z.prop;\n"
        + "  z.prop -= 123;\n"
        + "}\n"
        + "f('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck("var x = 0; x *= 'asdf';", NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "var /** string */ x = 'asdf'; x *= 123;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck("var x; x *= 123;", NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testClassConstructor() {
    checkNoWarnings("/** @constructor */ function Foo() {\n"
        + "  /** @type {number} */ this.n = 5;\n"
        + "};\n"
        + "(new Foo()).n - 5;");

    typeCheck(
        "/** @constructor */ function Foo() {\n"
        + "  /** @type {number} */ this.n = 5;\n"
        + "};\n"
        + "(new Foo()).n = 'str';",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @constructor */ function Foo() {\n"
        + "  /** @type {number} */ this.n;\n"
        + "};\n"
        + "(new Foo()).n = 'str';",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "function f() { (new Foo()).n = 'str'; }\n"
        + "/** @constructor */ function Foo() {\n"
        + "  /** @type {number} */ this.n = 5;\n"
        + "};",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "function f() { var x = new Foo(); x.n = 'str'; }\n"
        + "/** @constructor */ function Foo() {\n"
        + "  /** @type {number} */ this.n = 5;\n"
        + "};",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings("function f() { var x = new Foo(); return x.n - 5; }\n"
        + "/** @constructor */ function Foo() {\n"
        + "  this.n = 5;\n"
        + "};");

    typeCheck(
        "function f() { var x = new Foo(); x.s = 'str'; x.s < x.n; }\n"
        + "/** @constructor */ function Foo() {\n"
        + "  /** @type {number} */ this.n = 5;\n"
        + "};",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @constructor */ function Foo() {\n"
        + "  /** @type {number} */ this.n = 5;\n"
        + "};\n"
        + "function reqFoo(/** Foo */ x) {};\n"
        + "reqFoo({ n : 20 });",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f() { var x = new Foo(); x.n - 5; x.n < 'str'; }\n"
        + "/** @constructor */ function Foo() {\n"
        + "  this.n = 5;\n"
        + "};",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testPropertyDeclarations() {
    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {\n"
        + "  /** @type {number} */ this.x = 'abc';\n"
        + "  /** @type {string} */ this.x = 'def';\n"
        + "}",
        GlobalTypeInfo.REDECLARED_PROPERTY);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {\n"
        + "  /** @type {number} */ this.x = 5;\n"
        + "  /** @type {number} */ this.x = 7;\n"
        + "}",
        GlobalTypeInfo.REDECLARED_PROPERTY);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {\n"
        + "  this.x = 5;\n"
        + "  /** @type {number} */ this.x = 7;\n"
        + "}\n"
        + "function g() { (new Foo()).x < 'str'; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {\n"
        + "  /** @type {number} */ this.x = 7;\n"
        + "  this.x = 5;\n"
        + "}\n"
        + "function g() { (new Foo()).x < 'str'; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {\n"
        + "  /** @type {number} */ this.x = 7;\n"
        + "  this.x < 'str';\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {\n"
        + "  /** @type {?} */ this.x = 1;\n"
        + "  /** @type {?} */ this.x = 1;\n"
        + "}",
        GlobalTypeInfo.REDECLARED_PROPERTY);
  }

  public void testPrototypePropertyAssignments() {
    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @type {string} */ Foo.prototype.x = 'str';\n"
        + "function g() { (new Foo()).x - 5; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.x = 'str';\n"
        + "function g() { var f = new Foo(); f.x - 5; f.x < 'str'; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @type {function(string)} s */\n"
        + "Foo.prototype.bar = function(s) {};\n"
        + "function g() { (new Foo()).bar(5); }",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @constructor */ function Foo() {};\n"
        + "Foo.prototype.bar = function(s) {\n"
        + "  /** @type {string} */ this.x = 'str';\n"
        + "};\n"
        + "(new Foo()).x - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "(function() { Foo.prototype.prop = 123; })();",
        GlobalTypeInfo.CTOR_IN_DIFFERENT_SCOPE);

    typeCheck(
        "/** @constructor */ function F() {}"
        + "F.prototype.bar = function() {};"
        + "F.prototype.bar = function() {};",
        GlobalTypeInfo.REDECLARED_PROPERTY);

    typeCheck(
        "/** @constructor */ function F() {}"
        + "/** @return {void} */ F.prototype.bar = function() {};"
        + "F.prototype.bar = function() {};",
        GlobalTypeInfo.REDECLARED_PROPERTY);

    checkNoWarnings("/** @constructor */ function C(){}\n"
        + "C.prototype.foo = {};\n"
        + "C.prototype.method = function() { this.foo.bar = 123; }");
    // TODO(dimvar): I think we can fix the next one with better deferred checks
    // for prototype methods. Look into it.
    // typeCheck(
    //     "/** @constructor */ function Foo() {};\n" +
    //     "Foo.prototype.bar = function(s) { s < 'asdf'; };\n" +
    //     "function g() { (new Foo()).bar(5); }",
    //     NewTypeInference.INVALID_ARGUMENT_TYPE);
    // TODO(blickly): Add fancier JSDoc annotation finding to jstypecreator
    // typeCheck(
    //     "/** @constructor */ function Foo() {};\n" +
    //     "/** @param {string} s */ Foo.prototype.bar = function(s) {};\n" +
    //     "function g() { (new Foo()).bar(5); }",
    //     NewTypeInference.INVALID_ARGUMENT_TYPE);
    // typeCheck(
    //     "/** @constructor */ function Foo() {};\n" +
    //     "Foo.prototype.bar = function(/** string */ s) {};\n" +
    //     "function g() { (new Foo()).bar(5); }",
    //     NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("function f() {}\n"
        + "function g() { f.prototype.prop = 123; }");

    checkNoWarnings("/** @param {!Function} f */"
        + "function foo(f) { f.prototype.bar = function(x) {}; }");

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "Foo.prototype.method = function() {};\n"
        + "/** @type {number} */\n"
        + "Foo.prototype.method.pnum = 123;\n"
        + "var /** number */ n = Foo.prototype['method.pnum'];",
        TypeCheck.INEXISTENT_PROPERTY);
  }

  public void testAssignmentsToPrototype() {
    // TODO(dimvar): the 1st should pass, the 2nd we may stop catching
    // if we decide to not check these assignments at all.

    // checkNoWarnings(
    //     "/** @constructor */\n" +
    //     "function Foo() {}\n" +
    //     "/** @constructor @extends {Foo} */\n" +
    //     "function Bar() {}\n" +
    //     "Bar.prototype = new Foo;\n" +
    //     "Bar.prototype.method1 = function() {};");

    // typeCheck(
    //     "/**\n" +
    //     " * @constructor\n" +
    //     " * @struct\n" +
    //     " */\n" +
    //     "function Bar() {}\n" +
    //     "Bar.prototype = {};\n",
    //     TypeCheck.CONFLICTING_SHAPE_TYPE);
  }

  public void testConflictingPropertyDefinitions() {
    typeCheck(
        "/** @constructor */ function Foo() { this.x = 'str1'; };\n"
        + "/** @type {string} */ Foo.prototype.x = 'str2';\n"
        + "(new Foo).x - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @type {string} */ Foo.prototype.x = 'str1';\n"
        + "Foo.prototype.x = 'str2';\n"
        + "(new Foo).x - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.x = 'str2';\n"
        + "/** @type {string} */ Foo.prototype.x = 'str1';\n"
        + "(new Foo).x - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() { /** @type {string} */ this.x = 'str1'; };\n"
        + "Foo.prototype.x = 'str2';\n"
        + "(new Foo).x - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @constructor */ function Foo() { this.x = 5; };\n"
        + "/** @type {string} */ Foo.prototype.x = 'str';",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() { /** @type {string} */ this.x = 'str1'; };\n"
        + "Foo.prototype.x = 5;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() { /** @type {string} */ this.x = 'str'; };\n"
        + "/** @type {number} */ Foo.prototype.x = 'str';",
        GlobalTypeInfo.REDECLARED_PROPERTY);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @type {number} */ Foo.prototype.x = 1;\n"
        + "/** @type {number} */ Foo.prototype.x = 2;",
        GlobalTypeInfo.REDECLARED_PROPERTY);
  }

  public void testPrototypeAliasing() {
    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.x = 'str';\n"
        + "var fp = Foo.prototype;\n"
        + "fp.x - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testInstanceof() {
    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "function takesFoos(/** Foo */ afoo) {}\n"
        + "function f(/** (number|Foo) */ x) {\n"
        + "  takesFoos(x);\n"
        + "  if (x instanceof Foo) { takesFoos(x); }\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "({} instanceof function(){});", NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "(123 instanceof Foo);",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "function takesFoos(/** Foo */ afoo) {}\n"
        + "function f(/** boolean */ cond, /** (number|Foo) */ x) {\n"
        + "  if (x instanceof (cond || Foo)) { takesFoos(x); }\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings("/** @constructor */ function Foo() {}\n"
        + "function f(/** (number|!Foo) */ x) {\n"
        + "  if (x instanceof Foo) {} else { x - 5; }\n"
        + "}");

    checkNoWarnings("/** @constructor */ function Foo() {}\n"
        + "function f(/** (number|!Foo) */ x) {\n"
        + "  if (!(x instanceof Foo)) { x - 5; }\n"
        + "}");

    checkNoWarnings("/** @constructor */ function Foo() {}\n"
        + "/** @constructor */ function Bar() {}\n"
        + "function takesFoos(/** Foo */ afoo) {}\n"
        + "function f(/** Foo */ x) {\n"
        + "  if (x instanceof Bar) {} else { takesFoos(x); }\n"
        + "}");

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "function takesFoos(/** Foo */ afoo) {}\n"
        + "/** @param {*} x */ function f(x) {\n"
        + "  takesFoos(x);\n"
        + "  if (x instanceof Foo) { takesFoos(x); }\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "var x = new Foo();\n"
        + "x.bar = 'asdf';\n"
        + "if (x instanceof Foo) { x.bar - 5; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    // typeCheck(
    //     "function f(x) { if (x instanceof UndefinedClass) {} }",
    //     VarCheck.UNDEFINED_VAR_ERROR);

    typeCheck(
        "/** @constructor */ function Foo() { this.prop = 123; }\n"
        + "function f(x) { x = 123; if (x instanceof Foo) { x.prop; } }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings("/** @constructor */ function Foo() {}\n"
        + "/** @constructor @extends {Foo} */ function Bar() {}\n"
        + "/** @param {(number|!Bar)} x */\n"
        + "function f(x) {\n"
        + "  if (!(x instanceof Foo)) {\n"
        + "    var /** number */ n = x;\n"
        + "  }\n"
        + "}");

    checkNoWarnings("/** @constructor */ function Foo() {}\n"
        + "/** @enum {!Foo} */\n"
        + "var E = { ONE: new Foo };\n"
        + "/** @param {(number|E)} x */\n"
        + "function f(x) {\n"
        + "  if (!(x instanceof Foo)) {\n"
        + "    var /** number */ n = x;\n"
        + "  }\n"
        + "}");
  }

  public void testFunctionsExtendFunction() {
    checkNoWarnings("function f(x) {\n"
        + "  if (x instanceof Function) { x(); }\n"
        + "}");

    checkNoWarnings("function f(x) {\n"
        + "  if (x instanceof Function) { x(1); x('str') }\n"
        + "}");

    checkNoWarnings("function f(/** (null|function()) */ x) {\n"
        + "  if (x instanceof Function) { x(); }\n"
        + "}");

    typeCheck(
        "function f(/** (null|function()) */ x) {\n"
        + "  if (x instanceof Function) {} else { x(); }\n"
        + "}",
        TypeCheck.NOT_CALLABLE);

    checkNoWarnings("(function(){}).call(null);");

    checkNoWarnings("function greet(name) {}\n"
        + "greet.call(null, 'bob');\n"
        + "greet.apply(null, ['bob']);");

    checkNoWarnings("/** @constructor */ function Foo(){}\n"
        + "Foo.prototype.greet = function(name){};\n"
        + "Foo.prototype.greet.call(new Foo, 'bob');");

    typeCheck(
        "Function.prototype.method = function(/** string */ x){};\n"
        + "(function(){}).method(5);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("function f(value) {\n"
        + "  if (value instanceof Function) {} else if (value instanceof Object) {\n"
        + "    return value.displayName || value.name || '';\n"
        + "  }\n"
        + "};");
  }

  public void testObjectsAreNotClassy() {
    typeCheck(
        "function g(obj) {\n"
        + "  if (!(obj instanceof Object)) { throw -1; }\n"
        + "  return obj.x - 5;\n"
        + "}\n"
        + "g(new Object);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testFunctionWithProps() {
    typeCheck(
        "function f() {}\n"
        + "f.x = 'asdf';\n"
        + "f.x - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testConstructorProperties() {
    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @type {number} */ Foo.n = 1\n"
        + "/** @type {number} */ Foo.n = 1",
        GlobalTypeInfo.REDECLARED_PROPERTY);

    checkNoWarnings("function g() { Foo.bar - 5; }\n"
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.bar = 42;");

    typeCheck(
        "function g() { Foo.bar - 5; }\n"
        + "/** @constructor */ function Foo() {}\n"
        + "/** @type {string} */ Foo.bar = 'str';",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function g() { return (new Foo).bar; }\n"
        + "/** @constructor */ function Foo() {}\n"
        + "/** @type {string} */ Foo.bar = 'str';",
        TypeCheck.INEXISTENT_PROPERTY);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @type {string} */ Foo.prop = 'asdf';\n"
        + "var x = Foo;\n"
        + "x.prop - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function g() { Foo.prototype.baz = (new Foo).bar + Foo.bar; }\n"
        + "/** @constructor */ function Foo() {}\n"
        + "/** @type {number} */ Foo.prototype.bar = 5\n"
        + "/** @type {string} */ Foo.bar = 'str';",
        GlobalTypeInfo.CTOR_IN_DIFFERENT_SCOPE);

    // TODO(dimvar): warn about redeclared property
    checkNoWarnings("/** @constructor */ function Foo() {}\n"
        + "/** @type {number} */ Foo.n = 1;\n"
        + "Foo.n = 1;");

    // TODO(dimvar): warn about redeclared property
    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @type {number} */ Foo.n;\n"
        + "Foo.n = '';",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testTypeTighteningHeuristic() {
    typeCheck(
        "/** @param {*} x */ function f(x) { var /** ? */ y = x; x - 5; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings("function f(/** ? */ x) {\n"
        + "  if (!(typeof x == 'number')) {\n"
        + "    x < 'asdf';\n"
        + "  }\n"
        + "}");

    checkNoWarnings("function f(/** { prop: ? } */ x) {\n"
        + "  var /** (number|string) */ y = x.prop;\n"
        + "  x.prop < 5;\n"
        + "}");

    typeCheck(
        "function f(/** (number|string) */ x, /** (number|string) */ y) {\n"
        + "  var z;\n"
        + "  if (1 < 2) {\n"
        + "    z = x;\n"
        + "  } else {\n"
        + "    z = y;\n"
        + "  }\n"
        + "  z - 5;\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testDeclaredPropertyIndirectly() {
    typeCheck(
        "function f(/** { n: number } */ obj) {\n"
        + "  var o2 = obj;\n"
        + "  o2.n = 'asdf';\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testNonRequiredArguments() {
    checkNoWarnings("function f(f1, /** function(string=) */ f2, cond) {\n"
        + "  var y;\n"
        + "  if (cond) {\n"
        + "    f1();"
        + "    y = f1;\n"
        + "  } else {\n"
        + "    y = f2;\n"
        + "  }\n"
        + "  return y;\n"
        + "}");

    typeCheck(
        "/** @param {function(number=)} fnum */\n"
        + "function f(fnum) {\n"
        + "  fnum(); fnum('asdf');\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @param {function(... [number])} fnum */\n"
        + "function f(fnum) {\n"
        + "  fnum(); fnum(1, 2, 3, 'asdf');\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @param {function(number=, number)} g */\n"
        + "function f(g) {}",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "/** @param {number=} x */\n"
        + "function f(x) {}\n"
        + "f(); f('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @param {number=} x */\n"
        + "function f(x) {}\n"
        + "f(1, 2);",
        TypeCheck.WRONG_ARGUMENT_COUNT);

    typeCheck(
        "/** @type {function()} */ function f(x) {}",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "/** @type {function(number)} */ function f() {}",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "/** @type {function(number)} */ function f(/** number */ x) {}",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "/**\n"
        + " * @param {number=} x\n"
        + " * @param {number} y\n"
        + " */\n"
        + "function f(x, y) {}",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "/** @type {function(number=)} */ function f(x) {}\n"
        + "f(); f('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @type {function(number=, number)} */ function f(x, y) {}",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "function /** number */ f() { return 'asdf'; }",
        NewTypeInference.RETURN_NONDECLARED_TYPE);

    typeCheck(
        "/** @return {number} */ function /** number */ f() { return 1; }",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "/** @type {function(): number} */\n"
        + "function /** number */ f() { return 1; }",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "/** @type {function(... [number])} */ function f() {}"
        + "f(); f(1, 2, 3); f(1, 2, 'asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @param {...number} var_args */ function f(var_args) {}\n"
        + "f(); f(1, 2, 3); f(1, 2, 'asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @type {function(... [number])} */ function f(x) {}",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "/**\n"
        + " * @param {...number} var_args\n"
        + " * @param {number=} x\n"
        + " */\n"
        + "function f(var_args, x) {}",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "/** @type {function(number=, ...[number])} */\n"
        + "function f(x) {}\n"
        + "f('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(/** function(number=) */ fnum,"
        + "  /** function(string=) */ fstr, cond) {\n"
        + "  var y;\n"
        + "  if (cond) {\n"
        + "    y = fnum;\n"
        + "  } else {\n"
        + "    y = fstr;\n"
        + "  }\n"
        + "  y();\n"
        + "  y(123);\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(/** function(... [number]) */ fnum,"
        + "  /** function(... [string]) */ fstr, cond) {\n"
        + "  var y;\n"
        + "  if (cond) {\n"
        + "    y = fnum;\n"
        + "  } else {\n"
        + "    y = fstr;\n"
        + "  }\n"
        + "  y();\n"
        + "  y(123);\n"
        + "}",
        NewTypeInference.CALL_FUNCTION_WITH_BOTTOM_FORMAL);

    typeCheck(
        "function f(\n"
        + "  /** function() */ f1, /** function(string=) */ f2, cond) {\n"
        + "  var y;\n"
        + "  if (cond) {\n"
        + "    y = f1;\n"
        + "  } else {\n"
        + "    y = f2;\n"
        + "  }\n"
        + "  y(123);\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @param {function(string): *} x */ function g(x) {}\n"
        + "/** @param {function(... [number]): string} x */ function f(x) {\n"
        + "  g(x);\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/**\n"
        + " * @param {number=} x\n"
        + " * @param {number=} y\n"
        + " */\n"
        + "function f(x, y) {}\n"
        + "f(undefined, 123);\n"
        + "f('str')",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("function f(/** function(...) */ fun) {}\n"
        + "f(function() {});");

    // The restarg formal doesn't have to be called var_args.
    // It shouldn't be used in the body of the function.
    // typeCheck(
    //     "/** @param {...number} x */ function f(x) { x - 5; }",
    //     VarCheck.UNDEFINED_VAR_ERROR);

    typeCheck(
        "/** @param {number=} x */ function f(x) { x - 5; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings(
        "/** @param {number=} x */ function f(x) { if (x) { x - 5; } }");

    checkNoWarnings("function f(/** function(... [number]) */ x) {}\n"
        + "f(function() {});");

    checkNoWarnings("function f(/** function() */ x) {}\n"
        + "f(/** @type {function(... [number])} */ (function(nums) {}));");

    typeCheck(
        "function f(/** function(string=) */ x) {}\n"
        + "f(/** @type {function(... [number])} */ (function(nums) {}));",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(/** function(... [number]) */ x) {}\n"
        + "f(/** @type {function(string=)} */ (function(x) {}));",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("/** @param {number} opt_num */ function f(opt_num) {}\n"
        + "f();");

    typeCheck(
        "function f(opt_num, x) {}", RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    checkNoWarnings("function f(var_args) {} f(1, 2, 3);");

    typeCheck(
        "function f(var_args, x) {}", RhinoErrorReporter.BAD_JSDOC_ANNOTATION);
  }

  public void testInferredOptionalFormals() {
    checkNoWarnings("function f(x) {} f();");

    checkNoWarnings("function f(/** number */ x, y) { x-5; } f(123);");

    typeCheck(
        "function f(x) {\n"
        + "  if (x !== undefined) {\n"
        + "    return x-5;\n"
        + "  } else {\n"
        + "    return 0;\n"
        + "  }\n"
        + "}\n"
        + "f() - 1;\n"
        + "f('str');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @return {function(number=)} */\n"
        + "function f() {\n"
        + "  return function(x) {};\n"
        + "}\n"
        + "f()();\n"
        + "f()('str');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testSimpleClassInheritance() {
    checkNoWarnings("/** @constructor */\n"
        + "function Parent() {}\n"
        + "/** @constructor @extends{Parent} */\n"
        + "function Child() {}\n"
        + "Child.prototype = new Parent();");

    typeCheck(
        "/** @constructor */\n"
        + "function Parent() {\n"
        + "  /** @type {string} */ this.prop = 'asdf';\n"
        + "}\n"
        + "/** @constructor @extends{Parent} */\n"
        + "function Child() {}\n"
        + "Child.prototype = new Parent();\n"
        + "(new Child()).prop - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @constructor */\n"
        + "function Parent() {\n"
        + "  /** @type {string} */ this.prop = 'asdf';\n"
        + "}\n"
        + "/** @constructor @extends{Parent} */\n"
        + "function Child() {}\n"
        + "Child.prototype = new Parent();\n"
        + "(new Child()).prop = 5;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @constructor */\n"
        + "function Parent() {}\n"
        + "/** @type {string} */ Parent.prototype.prop = 'asdf';\n"
        + "/** @constructor @extends{Parent} */\n"
        + "function Child() {}\n"
        + "Child.prototype = new Parent();\n"
        + "(new Child()).prop - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @constructor */\n"
        + "function Parent() {}\n"
        + "/** @type {string} */ Parent.prototype.prop = 'asdf';\n"
        + "/** @constructor @extends{Parent} */\n"
        + "function Child() {\n"
        + "  /** @type {number} */ this.prop = 5;\n"
        + "}\n"
        + "Child.prototype = new Parent();",
        GlobalTypeInfo.INVALID_PROP_OVERRIDE);

    typeCheck(
        "/** @constructor */\n"
        + "function Parent() {}\n"
        + "/** @type {string} */ Parent.prototype.prop = 'asdf';\n"
        + "/** @constructor @extends{Parent} */\n"
        + "function Child() {}\n"
        + "Child.prototype = new Parent();\n"
        + "/** @type {number} */ Child.prototype.prop = 5;",
        ImmutableList.of(
            GlobalTypeInfo.INVALID_PROP_OVERRIDE,
            NewTypeInference.MISTYPED_ASSIGN_RHS));

    typeCheck(
        "/** @constructor */\n"
        + "function Parent() {}\n"
        + "/** @extends {Parent} */ function Child() {}",
        JSTypeCreatorFromJSDoc.EXTENDS_NOT_ON_CTOR_OR_INTERF);

    typeCheck(
        "/** @constructor @extends{number} */ function Foo() {}",
        JSTypeCreatorFromJSDoc.EXTENDS_NON_OBJECT);

    typeCheck(
        "/**\n"
        + " * @constructor\n"
        + " * @implements {string}\n"
        + " */\n"
        + "function Foo() {}",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "/**\n"
        + " * @interface\n"
        + " * @extends {number}\n"
        + " */\n"
        + "function Foo() {}",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "/** @interface */ function Foo() {}\n"
        + "/** @implements {Foo} */ function bar() {}",
        JSTypeCreatorFromJSDoc.IMPLEMENTS_WITHOUT_CONSTRUCTOR);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "Foo.prototype.method = function(x) { x - 1; };\n"
        + "/** @constructor @extends {Foo} */\n"
        + "function Bar() {}\n"
        + "Bar.prototype.method = function(x, y) { x - y; };\n"
        + "Bar.prototype.method2 = function(x, y) {};\n"
        + "Bar.prototype.method = Bar.prototype.method2;",
        GlobalTypeInfo.INVALID_PROP_OVERRIDE);
  }

  public void testInheritanceSubtyping() {
    checkNoWarnings("/** @constructor */ function Parent() {}\n"
        + "/** @constructor @extends{Parent} */ function Child() {}\n"
        + "(function(/** Parent */ x) {})(new Child);");

    typeCheck(
        "/** @constructor */ function Parent() {}\n"
        + "/** @constructor @extends{Parent} */ function Child() {}\n"
        + "(function(/** Child */ x) {})(new Parent);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @constructor */ function Parent() {}\n"
        + "/** @constructor @extends{Parent} */ function Child() {}\n"
        + "/** @constructor */\n"
        + "function Foo() { /** @type {Parent} */ this.x = new Child(); }\n"
        + "/** @type {Child} */ Foo.prototype.y = new Parent();",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings("/** @interface */\n"
        + "function High() {}\n"
        + "/** @constructor @implements {High} */\n"
        + "function Low() {}\n"
        + "var /** !High */ x = new Low");

    checkNoWarnings("/** @interface */\n"
        + "function High() {}\n"
        + "/** @interface @extends {High}*/\n"
        + "function Low() {}\n"
        + "function f(/** !High */ h, /** !Low */ l) { h = l; }");

    checkNoWarnings("/** @interface */\n"
        + "function High() {}\n"
        + "/** @interface @extends {High}*/\n"
        + "function Low() {}\n"
        + "/** @constructor @implements {Low} */\n"
        + "function Foo() {}\n"
        + "var /** !High */ x = new Foo;");

    checkNoWarnings("/** @interface */\n"
        + "function Foo() {}\n"
        + "/** @interface */\n"
        + "function High() {}\n"
        + "/** @interface @extends {High} */\n"
        + "function Med() {}\n"
        + "/**\n"
        + " * @interface\n"
        + " * @extends {Med}\n"
        + " * @extends {Foo}\n"
        + " */\n"
        + "function Low() {}\n"
        + "function f(/** !High */ x, /** !Low */ y) { x = y }");

    typeCheck(
        "/**\n"
        + " * @interface\n"
        + " * @template T\n"
        + " */\n"
        + "function Foo() {}\n"
        + "function f(/** !Foo<number> */ x, /** !Foo<string> */ y) { x = y; }",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/**\n"
        + " * @interface\n"
        + " * @template T\n"
        + " */\n"
        + "function Foo() {}\n"
        + "/**\n"
        + " * @constructor\n"
        + " * @implements {Foo<number>}\n"
        + " */\n"
        + "function Bar() {}\n"
        + "function f(/** !Foo<string> */ x, /** Bar */ y) { x = y; }",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/**\n"
        + " * @interface\n"
        + " * @template T\n"
        + " */\n"
        + "function Foo() {}\n"
        + "/**\n"
        + " * @constructor\n"
        + " * @template T\n"
        + " * @implements {Foo<T>}\n"
        + " */\n"
        + "function Bar() {}\n"
        + "function f(/** !Foo<string> */ x, /** !Bar<number> */ y) { x = y; }",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings("/**\n"
        + " * @interface\n"
        + " * @template T\n"
        + " */\n"
        + "function Foo() {}\n"
        + "/**\n"
        + " * @constructor\n"
        + " * @template T\n"
        + " * @implements {Foo<T>}\n"
        + " */\n"
        + "function Bar() {}\n"
        + "function f(/** !Foo<string> */ x, /** !Bar<string> */ y) {\n"
        + "  x = y;\n"
        + "}");

    typeCheck(
        "/**\n"
        + " * @interface\n"
        + " * @template T\n"
        + " */\n"
        + "function Foo() {}\n"
        + "/**\n"
        + " * @constructor\n"
        + " * @template T\n"
        + " * @implements {Foo<T>}\n"
        + " */\n"
        + "function Bar() {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {!Foo<T>} x\n"
        + " * @param {!Bar<number>} y\n"
        + " */\n"
        + "function f(x, y) { x = y; }",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testInheritanceImplicitObjectSubtyping() {
    String objectExterns =
        DEFAULT_EXTERNS
        + "/** @return {string} */ Object.prototype.toString = function() {};";

    checkNoWarnings(
        objectExterns,
        "/** @constructor */ function Foo() {}\n"
        + "/** @override */ Foo.prototype.toString = function(){ return ''; };");

    typeCheck(
        objectExterns,
        "/** @constructor */ function Foo() {}\n"
        + "/** @override */ Foo.prototype.toString = function(){ return 5; };",
        NewTypeInference.RETURN_NONDECLARED_TYPE);
  }

  public void testRecordtypeSubtyping() {
    // TODO(dimvar): fix
    // checkNoWarnings(
    //     "/** @interface */ function I() {}\n" +
    //     "/** @type {number} */ I.prototype.prop;\n" +
    //     "function f(/** !I */ x) {" +
    //     "  var /** { prop: number} */ y = x;" +
    //     "}");
  }

  public void testWarnAboutOverridesNotVisibleDuringGlobalTypeInfo() {
    typeCheck(
        "/** @constructor @extends {Parent} */ function Child() {}\n"
        + "/** @type {string} */ Child.prototype.y = 'str';\n"
        + "/** @constructor */ function Grandparent() {}\n"
        + "/** @type {number} */ Grandparent.prototype.y = 9;\n"
        + "/** @constructor @extends {Grandparent} */ function Parent() {}",
        GlobalTypeInfo.INVALID_PROP_OVERRIDE);
  }

  public void testInvalidMethodPropertyOverride() {
    typeCheck(
        "/** @interface */ function Parent() {}\n"
        + "/** @type {number} */ Parent.prototype.y = 9;\n"
        + "/** @constructor @implements {Parent} */ function Child() {}\n"
        + "/** @param {string} x */ Child.prototype.y = function(x) {};",
        GlobalTypeInfo.INVALID_PROP_OVERRIDE);

    typeCheck(
        "/** @interface */ function Parent() {}\n"
        + "/** @param {string} x */ Parent.prototype.y = function(x) {};\n"
        + "/** @constructor @implements {Parent} */ function Child() {}\n"
        + "/** @type {number} */ Child.prototype.y = 9;",
        GlobalTypeInfo.INVALID_PROP_OVERRIDE);

    typeCheck(
        "/** @constructor */ function Parent() {}\n"
        + "/** @type {number} */ Parent.prototype.y = 9;\n"
        + "/** @constructor @extends {Parent} */ function Child() {}\n"
        + "/** @param {string} x */ Child.prototype.y = function(x) {};",
        GlobalTypeInfo.INVALID_PROP_OVERRIDE);

    typeCheck(
        "/** @constructor */ function Parent() {}\n"
        + "/** @param {string} x */ Parent.prototype.y = function(x) {};\n"
        + "/** @constructor @extends {Parent} */ function Child() {}\n"
        + "/** @type {number} */ Child.prototype.y = 9;",
        GlobalTypeInfo.INVALID_PROP_OVERRIDE);

    // TODO(dimvar): fix
    // typeCheck(
    //     "/** @constructor */\n" +
    //     "function Foo() {}\n" +
    //     "Foo.prototype.f = function(/** number */ x, /** number */ y) {};\n" +
    //     "/** @constructor @extends {Foo} */\n" +
    //     "function Bar() {}\n" +
    //     "/** @override */\n" +
    //     "Bar.prototype.f = function(x) {};",
    //     GlobalTypeInfo.INVALID_PROP_OVERRIDE);
  }

  public void testMultipleObjects() {
    checkNoWarnings("/** @constructor */ function Foo() {}\n"
        + "/** @constructor */ function Bar() {}\n"
        + "/** @param {(Foo|Bar)} x */ function reqFooBar(x) {}\n"
        + "function f(cond) {\n"
        + "  reqFooBar(cond ? new Foo : new Bar);\n"
        + "}");

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @constructor */ function Bar() {}\n"
        + "/** @param {Foo} x */ function reqFoo(x) {}\n"
        + "function f(cond) {\n"
        + "  reqFoo(cond ? new Foo : new Bar);\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @constructor */ function Bar() {}\n"
        + "/** @param {(Foo|Bar)} x */ function g(x) {\n"
        + "  if (x instanceof Foo) {\n"
        + "    var /** Foo */ y = x;\n"
        + "  } else {\n"
        + "    var /** Bar */ z = x;\n"
        + "  }\n"
        + "  var /** Foo */ w = x;\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() { /** @type {string} */ this.s = 'str'; }\n"
        + "/** @param {(!Foo|{n:number, s:string})} x */ function g(x) {\n"
        + "  if (x instanceof Foo) {\n"
        + "  } else {\n"
        + "    x.s - 5;\n"
        + "  }\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings("/** @constructor */ function Foo() {}\n"
        + "/** @type {number} */ Foo.prototype.n = 5;\n"
        + "/** @param {{n : number}} x */ function reqRecord(x) {}\n"
        + "function f() {\n"
        + "  reqRecord(new Foo);\n"
        + "}");

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @type {number} */ Foo.prototype.n = 5;\n"
        + "/** @param {{n : string}} x */ function reqRecord(x) {}\n"
        + "function f() {\n"
        + "  reqRecord(new Foo);\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @param {{n : number}|!Foo} x */\n"
        + "function f(x) {\n"
        + "  x.n - 5;\n"
        + "}",
        NewTypeInference.POSSIBLY_INEXISTENT_PROPERTY);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @param {{n : number}|!Foo} x */\n"
        + "function f(x) {\n"
        + "  x.abc - 5;\n"
        + "}",
        TypeCheck.INEXISTENT_PROPERTY);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @constructor */ function Bar() {}\n"
        + "/** @param {!Bar|!Foo} x */\n"
        + "function f(x) {\n"
        + "  x.abc = 'str';\n"
        + "  if (x instanceof Foo) {\n"
        + "    x.abc - 5;\n"
        + "  }\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testMultipleFunctionsInUnion() {
    checkNoWarnings("/** @param {function():string | function():number} x\n"
        + "  * @return {string|number} */\n"
        + "function f(x) {\n"
        + "  return x();\n"
        + "}");

    typeCheck(
        "/** @param {function(string)|function(number)} x\n"
        + "  * @param {string|number} y */\n"
        + "function f(x, y) {\n"
        + "  x(y);\n"
        + "}",
        NewTypeInference.CALL_FUNCTION_WITH_BOTTOM_FORMAL);
    // typeCheck(
    //     // Right now we treat the parameter as undeclared. This could change.
    //     "/** @type {(function(string)|function(number))} */\n" +
    //     "function f(x) {\n" +
    //     "  x = 'str'; x = 7; x = null; x = true;\n" +
    //     "  x - 5;\n" +
    //     "}",
    //     NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testPrototypeOnNonCtorFunction() {
    checkNoWarnings("function Foo() {}; Foo.prototype.y = 5;");
  }

  public void testInvalidTypeReference() {
    typeCheck(
        "/** @type {gibberish} */ var x;",
        GlobalTypeInfo.UNRECOGNIZED_TYPE_NAME);

    typeCheck(
        "/** @param {gibberish} x */ function f(x){};",
        GlobalTypeInfo.UNRECOGNIZED_TYPE_NAME);

    typeCheck(
        "function f(/** gibberish */ x){};",
        GlobalTypeInfo.UNRECOGNIZED_TYPE_NAME);

    typeCheck(
        "/** @returns {gibberish} */\n"
        + "function f(x) { return x; };",
        GlobalTypeInfo.UNRECOGNIZED_TYPE_NAME);

    typeCheck(
        "/** @interface @extends {gibberish} */ function Foo(){};",
        GlobalTypeInfo.UNRECOGNIZED_TYPE_NAME);

    typeCheck(
        "/** @constructor @implements {gibberish} */ function Foo(){};",
        GlobalTypeInfo.UNRECOGNIZED_TYPE_NAME);

    typeCheck(
        "/** @constructor @extends {gibberish} */ function Foo() {};",
        GlobalTypeInfo.UNRECOGNIZED_TYPE_NAME);
  }

  public void testCircularDependencies() {
    checkNoWarnings("/** @constructor @extends {Bar}*/ function Foo() {}\n"
        + "/** @constructor */ function Bar() {}");

    checkNoWarnings("/** @param {Foo} x */ function f(x) {}\n"
        + "/** @constructor */ function Foo() {}");

    typeCheck(
        "f(new Bar)\n"
        + "/** @param {Foo} x */ function f(x) {}\n"
        + "/** @constructor */ function Foo() {}\n"
        + "/** @constructor */ function Bar() {}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("/** @constructor @param {Foo} x */ function Bar(x) {}\n"
        + "/** @constructor @param {Bar} x */ function Foo(x) {}\n"
        + "new Bar(new Foo(null));");

    typeCheck(
        "/** @constructor @param {Foo} x */ function Bar(x) {}\n"
        + "/** @constructor @param {Bar} x */ function Foo(x) {}\n"
        + "new Bar(new Foo(undefined));",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @constructor @extends {Bar} */ function Foo() {}\n"
        + "/** @constructor @extends {Foo} */ function Bar() {}",
        JSTypeCreatorFromJSDoc.INHERITANCE_CYCLE);

    typeCheck(
        "/** @interface @extends {Bar} */ function Foo() {}\n"
        + "/** @interface @extends {Foo} */ function Bar() {}",
        JSTypeCreatorFromJSDoc.INHERITANCE_CYCLE);

    typeCheck(
        "/** @constructor @extends {Foo} */ function Foo() {}",
        JSTypeCreatorFromJSDoc.INHERITANCE_CYCLE);
  }

  public void testInterfaceNonEmptyFunction() throws Exception {
    typeCheck(
        "/** @interface */ function T() {};\n"
        + "T.prototype.x = function() { return 'foo'; }",
        TypeCheck.INTERFACE_METHOD_NOT_EMPTY);
  }

  public void testInterfaceMistypedProp() {
    typeCheck(
        "/** @interface */ function I() {}\n"
        + "/** @type {number} */ I.prototype.y = 'asdf';",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testInterfaceSingleInheritance() {
    typeCheck(
        "/** @interface */ function I() {}\n"
        + "/** @type {string} */ I.prototype.prop;\n"
        + "/** @constructor @implements{I} */ function C() {}",
        TypeValidator.INTERFACE_METHOD_NOT_IMPLEMENTED);

    typeCheck(
        "/** @interface */ function I() {}\n"
        + "/** @param {number} x */\n"
        + "I.prototype.method = function(x) {};\n"
        + "/** @constructor @implements{I} */ function C() {}",
        TypeValidator.INTERFACE_METHOD_NOT_IMPLEMENTED);

    typeCheck(
        "/** @interface */ function IParent() {}\n"
        + "/** @type {number} */ IParent.prototype.prop;\n"
        + "/** @interface @extends{IParent} */ function IChild() {}\n"
        + "/** @constructor @implements{IChild} */\n"
        + "function C() { this.prop = 5; }\n"
        + "(new C).prop < 'adsf';",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @interface */ function IParent() {}\n"
        + "/** @type {number} */ IParent.prototype.prop;\n"
        + "/** @interface @extends{IParent} */ function IChild() {}\n"
        + "/** @constructor @implements{IChild} */\n"
        + "function C() { this.prop = 'str'; }",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings("/** @constructor */\n"
        + "function Parent() { /** @type {number} */ this.prop = 123; }\n"
        + "/** @constructor @extends {Parent} */ function Child() {}\n"
        + "(new Child).prop = 321;");

    typeCheck(
        "/** @constructor */\n"
        + "function Parent() { /** @type {number} */ this.prop = 123; }\n"
        + "/** @constructor @extends {Parent} */ function Child() {}\n"
        + "(new Child).prop = 'str';",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @interface */ function I() {}\n"
        + "/** @param {number} x */\n"
        + "I.prototype.method = function(x, y) {};\n"
        + "/** @constructor @implements{I} */ function C() {}\n"
        + "/** @param {string} y */\n"
        + "C.prototype.method = function(x, y) {};\n"
        + "(new C).method(5, 6);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @interface */ function I() {}\n"
        + "/** @param {number} x */\n"
        + "I.prototype.method = function(x, y) {};\n"
        + "/** @constructor @implements{I} */ function C() {}\n"
        + "/** @param {string} y */\n"
        + "C.prototype.method = function(x, y) {};\n"
        + "(new C).method('asdf', 'fgr');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @interface */ function I() {}\n"
        + "/** @param {number} x */\n"
        + "I.prototype.method = function(x) {};\n"
        + "/** @constructor @implements{I} */ function C() {}\n"
        + "C.prototype.method = function(x) {};\n"
        + "(new C).method('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @interface */ function I1() {}\n"
        + "/** @param {number} x */ I1.prototype.method = function(x, y) {};\n"
        + "/** @interface */ function I2() {}\n"
        + "/** @param {string} y */ I2.prototype.method = function(x, y) {};\n"
        + "/** @constructor @implements{I1} @implements{I2} */ function C(){}\n"
        + "C.prototype.method = function(x, y) {};\n"
        + "(new C).method('asdf', 'fgr');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @interface */ function I1() {}\n"
        + "/** @param {number} x */ I1.prototype.method = function(x, y) {};\n"
        + "/** @interface */ function I2() {}\n"
        + "/** @param {string} y */ I2.prototype.method = function(x, y) {};\n"
        + "/** @constructor @implements{I1} @implements{I2} */ function C(){}\n"
        + "C.prototype.method = function(x, y) {};\n"
        + "(new C).method(1, 2);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("/** @interface */ function I1() {}\n"
        + "/** @param {number} x */ I1.prototype.method = function(x) {};\n"
        + "/** @interface */ function I2() {}\n"
        + "/** @param {string} x */ I2.prototype.method = function(x) {};\n"
        + "/** @constructor @implements{I1} @implements{I2} */ function C(){}\n"
        +
        // Type of C.method is @param {(string|number)}
        "C.prototype.method = function(x) {};");

    typeCheck(
        "/** @interface */ function I1() {}\n"
        + "/** @param {number} x */ I1.prototype.method = function(x) {};\n"
        + "/** @interface */ function I2() {}\n"
        + "/** @param {string} x */ I2.prototype.method = function(x) {};\n"
        + "/** @constructor @implements{I1} @implements{I2} */ function C(){}\n"
        +
        // Type of C.method is @param {(string|number)}
        "C.prototype.method = function(x) {};\n"
        + "(new C).method(true);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @interface */ function I() {}\n"
        + "/** @param {number} x */ I.prototype.method = function(x) {};\n"
        + "/** @constructor */ function S() {}\n"
        + "/** @param {string} x */ S.prototype.method = function(x) {};\n"
        + "/** @constructor @implements{I} @extends{S} */ function C(){}\n"
        +
        // Type of C.method is @param {(string|number)}
        "C.prototype.method = function(x) {};\n"
        + "(new C).method(true);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testInterfaceMultipleInheritanceNoCrash() {
    checkNoWarnings("/** @interface */\n"
        + "function I1() {}\n"
        + "I1.prototype.method = function(x) {};\n"
        + "/** @interface */\n"
        + "function I2() {}\n"
        + "I2.prototype.method = function(x) {};\n"
        + "/**\n"
        + " * @interface\n"
        + " * @extends {I1}\n"
        + " * @extends {I2}\n"
        + " */\n"
        + "function I3() {}\n"
        + "/** @constructor @implements {I3} */\n"
        + "function Foo() {}\n"
        + "Foo.prototype.method = function(x) {};");
  }

  public void testInterfaceArgument() {
    typeCheck(
        "/** @interface */\n"
        + "function I() {}\n"
        + "/** @param {number} x */\n"
        + "I.prototype.method = function(x) {};\n"
        + "/** @param {!I} x */\n"
        + "function foo(x) { x.method('asdf'); }",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @interface */\n"
        + "function IParent() {}\n"
        + "/** @param {number} x */\n"
        + "IParent.prototype.method = function(x) {};\n"
        + "/** @interface @extends {IParent} */\n"
        + "function IChild() {}\n"
        + "/** @param {!IChild} x */\n"
        + "function foo(x) { x.method('asdf'); }",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testExtendedInterfacePropertiesCompatibility() {
    typeCheck(
        "/** @interface */function Int0() {};"
        + "/** @interface */function Int1() {};"
        + "/** @type {number} */"
        + "Int0.prototype.foo;"
        + "/** @type {string} */"
        + "Int1.prototype.foo;"
        + "/** @interface \n @extends {Int0} \n @extends {Int1} */"
        + "function Int2() {};",
        TypeCheck.INCOMPATIBLE_EXTENDED_PROPERTY_TYPE);

    typeCheck(
        "/** @interface */\n"
        + "function Parent1() {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @return {number}\n"
        + " */\n"
        + "Parent1.prototype.method = function(x) {};\n"
        + "/** @interface */\n"
        + "function Parent2() {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @return {string}\n"
        + " */\n"
        + "Parent2.prototype.method = function(x) {};\n"
        + "/** @interface @extends {Parent1} @extends {Parent2} */\n"
        + "function Child() {}",
        TypeCheck.INCOMPATIBLE_EXTENDED_PROPERTY_TYPE);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @constructor */ function Bar() {}\n"
        + "/** @interface */\n"
        + "function Parent1() {}\n"
        + "/** @type {!Foo} */\n"
        + "Parent1.prototype.obj;\n"
        + "/** @interface */\n"
        + "function Parent2() {}\n"
        + "/** @type {!Bar} */\n"
        + "Parent2.prototype.obj;\n"
        + "/** @interface @extends {Parent1} @extends {Parent2} */\n"
        + "function Child() {}",
        TypeCheck.INCOMPATIBLE_EXTENDED_PROPERTY_TYPE);
  }

  public void testTwoLevelExtendedInterface() {
    typeCheck(
        "/** @interface */function Int0() {};"
        + "/** @type {function()} */"
        + "Int0.prototype.foo;"
        + "/** @interface @extends {Int0} */function Int1() {};"
        + "/** @constructor \n @implements {Int1} */"
        + "function Ctor() {};",
        TypeValidator.INTERFACE_METHOD_NOT_IMPLEMENTED);
  }

  public void testConstructorExtensions() {
    typeCheck(
        "/** @constructor */ function I() {}\n"
        + "/** @param {number} x */\n"
        + "I.prototype.method = function(x) {};\n"
        + "/** @constructor @extends{I} */ function C() {}\n"
        + "C.prototype.method = function(x) {};\n"
        + "(new C).method('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @constructor */ function I() {}\n"
        + "/** @param {number} x */\n"
        + "I.prototype.method = function(x, y) {};\n"
        + "/** @constructor @extends{I} */ function C() {}\n"
        + "/** @param {string} y */\n"
        + "C.prototype.method = function(x, y) {};\n"
        + "(new C).method('asdf', 'fgr');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testInterfaceAndConstructorInvalidConstructions() {
    typeCheck(
        "/** @constructor @extends {Bar} */\n"
        + "function Foo() {}\n"
        + "/** @interface */\n"
        + "function Bar() {}",
        TypeCheck.CONFLICTING_EXTENDED_TYPE);

    typeCheck(
        "/** @constructor @implements {Bar} */\n"
        + "function Foo() {}\n"
        + "/** @constructor */\n"
        + "function Bar() {}",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @interface @implements {Foo} */\n"
        + "function Bar() {}",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @interface @extends {Foo} */\n"
        + "function Bar() {}",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);
  }

  public void testNot() {
    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @constructor */\n"
        + "function Bar() { /** @type {string} */ this.prop = 'asdf'; }\n"
        + "function f(/** (!Foo|!Bar) */ obj) {\n"
        + "  if (!(obj instanceof Foo)) {\n"
        + "    obj.prop - 5;\n"
        + "  }\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings("function f(cond) {\n"
        + "  var x = cond ? null : 123;\n"
        + "  if (!(x === null)) { x - 5; }\n"
        + "}");

    typeCheck(
        "/** @constructor */ function Foo(){ this.prop = 123; }\n"
        + "function f(/** Foo */ obj) {\n"
        + "  if (!obj) { obj.prop; }\n"
        + "}",
        NewTypeInference.PROPERTY_ACCESS_ON_NONOBJECT);
  }

  public void testNullability() {
    checkNoWarnings("/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @param {Foo} x */\n"
        + "function f(x) {}\n"
        + "f(new Foo);");

    typeCheck(
        "/** @constructor */ function Foo(){ this.prop = 123; }\n"
        + "function f(/** Foo */ obj) { obj.prop; }",
        NewTypeInference.NULLABLE_DEREFERENCE);

    typeCheck(
        "/** @interface */\n"
        + "function I() {}\n"
        + "I.prototype.method = function() {};\n"
        + "/** @param {I} x */\n"
        + "function foo(x) { x.method(); }",
        NewTypeInference.NULLABLE_DEREFERENCE);
  }

  public void testGetElem() {
    typeCheck(
        "/** @constructor */\n"
        + "function C(){ /** @type {number} */ this.prop = 1; }\n"
        + "(new C)['prop'] < 'asdf';",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(x, y) {\n"
        + "  x < y;\n"
        + "  ({})[y - 5];\n"
        + "}\n"
        + "f('asdf', 123);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    // We don't see the warning here b/c the formal param x is assigned to a
    // string, and we use x's type at the end of the function to create the
    // summary.
    checkNoWarnings("function f(x, y) {\n"
        + "  x < y;\n"
        + "  ({})[y - 5];\n"
        + "  x = 'asdf';\n"
        + "}\n"
        + "f('asdf', 123);");

    typeCheck(
        "function f(x, y) {\n"
        + "  ({})[y - 5];\n"
        + "  x < y;\n"
        + "}\n"
        + "f('asdf', 123);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(x) {\n"
        + "  x['prop'] = 'str';\n"
        + "  return x['prop'] - 5;\n"
        + "}\n"
        + "f({});",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings("function f(/** ? */ o) { return o[0].prop; }");

    // TODO(blickly): The fact that this has no warnings is somewhat unpleasant.
    checkNoWarnings("function f(x) {\n"
        + "  x['prop'] = 7;\n"
        + "  var p = 'prop';\n"
        + "  x[p] = 'str';\n"
        + "  return x['prop'] - 5;\n"
        + "}\n"
        + "f({});");
  }

  public void testNamespaces() {
    typeCheck(
        "/** @const */ var ns = {};\n"
        + "/** @constructor */ ns.C = function() {};\n"
        + "ns.C();",
        TypeCheck.CONSTRUCTOR_NOT_CALLABLE);

    typeCheck(
        "/** @const */ var ns = {};\n"
        + "/** @param {number} x */ ns.f = function(x) {};\n"
        + "ns.f('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @const */ var ns = {};\n"
        + "/** @constructor */ ns.C = function(){}\n"
        + "ns.C.prototype.method = function(/** string */ x){};\n"
        + "(new ns.C).method(5);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @const */ var ns = {};\n"
        + "/** @const */ ns.ns2 = {};\n"
        + "/** @constructor */ ns.ns2.C = function() {};\n"
        + "ns.ns2.C();",
        TypeCheck.CONSTRUCTOR_NOT_CALLABLE);

    typeCheck(
        "/** @const */ var ns = {};\n"
        + "/** @const */ ns.ns2 = {};\n"
        + "/** @constructor */ ns.ns2.C = function() {};\n"
        + "ns.ns2.C.prototype.method = function(/** string */ x){};\n"
        + "(new ns.ns2.C).method(11);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @constructor */ function C1(){}\n"
        + "/** @constructor */ C1.C2 = function(){}\n"
        + "C1.C2.prototype.method = function(/** string */ x){};\n"
        + "(new C1.C2).method(1);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @constructor */ function C1(){};\n"
        + "/** @constructor */ C1.prototype.C2 = function(){};\n"
        + "(new C1).C2();",
        TypeCheck.CONSTRUCTOR_NOT_CALLABLE);

    typeCheck(
        "/** @const */ var ns = {};\n"
        + "/** @type {number} */ ns.N = 5;\n"
        + "ns.N();",
        TypeCheck.NOT_CALLABLE);

    typeCheck(
        "/** @const */ var ns = {};\n"
        + "/** @type {number} */ ns.foo = 123;\n"
        + "/** @type {string} */ ns.foo = '';",
        ImmutableList.of(
            GlobalTypeInfo.REDECLARED_PROPERTY,
            NewTypeInference.MISTYPED_ASSIGN_RHS));

    typeCheck(
        "/** @const */ var ns = {};\n"
        + "/** @type {number} */ ns.foo;\n"
        + "/** @type {string} */ ns.foo;",
        GlobalTypeInfo.REDECLARED_PROPERTY);

    // We warn for duplicate declarations even if they are the same type.
    typeCheck(
        "/** @const */ var ns = {};\n"
        + "/** @type {number} */ ns.foo;\n"
        + "/** @type {number} */ ns.foo;",
        GlobalTypeInfo.REDECLARED_PROPERTY);

    // Without the @const, we don't consider it a namespace and don't warn.
    checkNoWarnings("var ns = {};\n"
        + "/** @type {number} */ ns.foo = 123;\n"
        + "/** @type {string} */ ns.foo = '';");

    // TODO(dimvar): warn about redeclared property
    typeCheck(
        "/** @const */ var ns = {};\n"
        + "ns.x = 5;\n"
        + "/** @type {string} */\n"
        + "ns.x = 'str';",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @const */ var ns = {};\n"
        + "ns.prop = 1;\n"
        + "function f() { var /** string */ s = ns.prop; }",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  // public void testUndeclaredNamespaces() {
  //   typeCheck(
  //         "/** @constructor */ ns.Foo = function(){};\n"
  //       + "ns.Foo.prototype.method = function(){};",
  //       ImmutableList.of(
  //           VarCheck.UNDEFINED_VAR_ERROR,
  //           VarCheck.UNDEFINED_VAR_ERROR));
  // }

  public void testNestedNamespaces() {
    // In the previous type inference, ns.subns did not need a
    // @const annotation, but we require it.
    typeCheck(
        "/** @const */\n"
        + "var ns = {};\n"
        + "/** @const */\n"
        + "ns.subns = {};\n"
        + "/** @type {string} */\n"
        + "ns.subns.n = 'str';\n"
        + "function f() { ns.subns.n - 5; }",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testNonnamespaceLooksLikeANamespace() {
    checkNoWarnings("/** @const */\n"
        + "var ns = {};\n"
        + "/** @type {Object} */\n"
        + "ns.obj = null;\n"
        + "function setObj() {\n"
        + "  ns.obj = {};\n"
        + "}");

    checkNoWarnings("/** @const */\n"
        + "var ns = {};\n"
        + "/** @type {Object} */\n"
        + "ns.obj = null;\n"
        + "function setObj() {\n"
        + "  ns.obj = {};\n"
        + "  ns.obj.str = 'str';\n"
        + "}");

    typeCheck(
        "/** @const */\n"
        + "var ns = {};\n"
        + "/** @type {Object} */\n"
        + "ns.obj = null;\n"
        + "ns.obj = {};\n"
        + "ns.obj.x = 'str';\n"
        + "ns.obj.x - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings("/** @const */\n"
        + "var ns = {};\n"
        + "/** @type {Object} */\n"
        + "ns.obj = null;\n"
        + "ns.obj = { x : 1, y : 5};\n"
        + "ns.obj.x = 'str';");

    typeCheck(
        "/** @const */\n"
        + "var ns = {};\n"
        + "/** @type {Object} */\n"
        + "ns.obj = null;\n"
        + "ns.obj = { x : 1, y : 5};\n"
        + "ns.obj.x = 'str';\n"
        + "ns.obj.x - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testNamespacedObjectsDontCrash() {
    checkNoWarnings("/** @const */\n"
        + "var ns = {};\n"
        + "/** @constructor */\n"
        + "ns.Foo = function() {\n"
        + "  ns.Foo.obj.value = ns.Foo.VALUE;\n"
        + "};\n"
        + "ns.Foo.obj = {};\n"
        + "ns.Foo.VALUE = 128;");
  }

  public void testRedeclaredNamespaces() {
    // TODO(blickly): Consider a warning if RHS doesn't contain ||
    checkNoWarnings("/** @const */ var ns = ns || {}\n"
        + "/** @const */ var ns = ns || {}");

    checkNoWarnings("/** @const */ var ns = ns || {}\n"
        + "ns.subns = ns.subns || {}\n"
        + "ns.subns = ns.subns || {}");
  }

  public void testReferenceToNonexistentNamespace() {
    // typeCheck(
    //     "/** @constructor */ ns.Foo = function(){};",
    //     VarCheck.UNDEFINED_VAR_ERROR);

    // typeCheck(
    //     "ns.subns = {};",
    //     VarCheck.UNDEFINED_VAR_ERROR);

    // typeCheck(
    //     "/** @enum {number} */ ns.NUM = { N : 1 };",
    //     VarCheck.UNDEFINED_VAR_ERROR);

    // typeCheck(
    //     "/** @typedef {number} */ ns.NUM;",
    //     VarCheck.UNDEFINED_VAR_ERROR);

    typeCheck(
        "/** @const */ var ns = {};\n"
        + "/** @constructor */ ns.subns.Foo = function(){};",
        TypeCheck.INEXISTENT_PROPERTY);

    typeCheck(
        "/** @const */ var ns = {};\n"
        + "ns.subns.subsubns = {};",
        TypeCheck.INEXISTENT_PROPERTY);

    typeCheck(
        "/** @const */ var ns = {};\n"
        + "/** @enum {number} */ ns.subns.NUM = { N : 1 };",
        TypeCheck.INEXISTENT_PROPERTY);

    typeCheck(
        "/** @const */ var ns = {};\n"
        + "/** @typedef {number} */ ns.subns.NUM;",
        TypeCheck.INEXISTENT_PROPERTY);

    typeCheck(
        "/** @constructor */ function Foo(){}\n"
        + "Foo.subns.subsubns = {};",
        TypeCheck.INEXISTENT_PROPERTY);

    typeCheck(
        "/** @constructor */ function Foo(){}\n"
        + "/** @constructor */ Foo.subns.Bar = function(){};",
        TypeCheck.INEXISTENT_PROPERTY);
  }

  public void testThrow() {
    checkNoWarnings("throw 123;");

    checkNoWarnings("var msg = 'hello'; throw msg;");

    checkNoWarnings("function f(cond, x, y) {\n"
        + "  if (cond) {\n"
        + "    x < y;\n"
        + "    throw 123;\n"
        + "  } else {\n"
        + "    x < 2;\n"
        + "  }\n"
        + "}");

    typeCheck("throw (1 - 'asdf');", NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testQnameInJsdoc() {
    typeCheck(
        "/** @const */ var ns = {};\n"
        + "/** @constructor */ ns.C = function() {};\n"
        + "/** @param {!ns.C} x */ function f(x) {\n"
        + "  123, x.prop;\n"
        + "}",
        TypeCheck.INEXISTENT_PROPERTY);
  }

  public void testIncrementDecrements() {
    checkNoWarnings("/** @const */ var ns = { x : 5 };\n"
        + "ns.x++; ++ns.x; ns.x--; --ns.x;");

    typeCheck(
        "function f(ns) { --ns.x; }; f({x : 'str'})",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testAndOr() {
    checkNoWarnings("function f(x, y, z) { return x || y && z;}");

    typeCheck(
        "function f(/** number */ x, /** string */ y) {\n"
        + "  var /** number */ n = x || y;\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "function f(/** number */ x, /** string */ y) {\n"
        + "  var /** number */ n = y || x;\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testNonStringComparisons() {
    checkNoWarnings("function f(x) {\n"
        + "  if (null == x) {\n"
        + "    var /** (null|undefined) */ y = x;\n"
        + "  }\n"
        + "}");

    checkNoWarnings("function f(x) {\n"
        + "  if (x == null) {\n"
        + "    var /** (null|undefined) */ y = x;\n"
        + "  }\n"
        + "}");

    typeCheck(
        "function f(x) {\n"
        + "  if (null == x) {\n"
        + "    var /** null */ y = x;\n"
        + "    var /** undefined */ z = x;\n"
        + "  }\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @param {*} x */\n"
        + "function f(x) {\n"
        + "  if (5 == x) {\n"
        + "    var /** (null|undefined) */ y = x;\n"
        + "  }\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @param {*} x */\n"
        + "function f(x) {\n"
        + "  if (x == 5) {\n"
        + "    var /** (null|undefined) */ y = x;\n"
        + "  }\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @param {*} x */\n"
        + "function f(x) {\n"
        + "  if (null == x) {\n"
        + "  } else {\n"
        + "    var /** (null|undefined) */ y = x;\n"
        + "  }\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @param {*} x */\n"
        + "function f(x) {\n"
        + "  if (x == null) {\n"
        + "  } else {\n"
        + "    var /** (null|undefined) */ y = x;\n"
        + "  }\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings("function f(x) {\n"
        + "  if (null != x) {\n"
        + "  } else {\n"
        + "    var /** (null|undefined) */ y = x;\n"
        + "  }\n"
        + "}");

    checkNoWarnings("function f(x) {\n"
        + "  if (x != null) {\n"
        + "  } else {\n"
        + "    var /** (null|undefined) */ y = x;\n"
        + "  }\n"
        + "}");

    typeCheck(
        "/** @param {*} x */\n"
        + "function f(x) {\n"
        + "  if (5 != x) {\n"
        + "  } else {\n"
        + "    var /** (null|undefined) */ y = x;\n"
        + "  }\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @param {*} x */\n"
        + "function f(x) {\n"
        + "  if (x != 5) {\n"
        + "  } else {\n"
        + "    var /** (null|undefined) */ y = x;\n"
        + "  }\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @param {*} x */\n"
        + "function f(x) {\n"
        + "  if (null != x) {\n"
        + "    var /** (null|undefined) */ y = x;\n"
        + "  }\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @param {*} x */\n"
        + "function f(x) {\n"
        + "  if (x != null) {\n"
        + "    var /** (null|undefined) */ y = x;\n"
        + "  }\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testAnalyzeLoopsBwd() {
    checkNoWarnings("for(;;);");

    typeCheck(
        "function f(x) {\n"
        + "  for (; x - 5 > 0; ) {}\n"
        + "  x = undefined;\n"
        + "}\n"
        + "f(true);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(x) {\n"
        + "  while (x - 5 > 0) {}\n"
        + "  x = undefined;\n"
        + "}\n"
        + "f(true);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(x) {\n"
        + "  if (x - 5 > 0) {}\n"
        + "  x = undefined;\n"
        + "}\n"
        + "f(true);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(x) {\n"
        + "  do {} while (x - 5 > 0);\n"
        + "  x = undefined;\n"
        + "}\n"
        + "f(true);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testDontLoosenNominalTypes() {
    checkNoWarnings("/** @constructor */ function Foo() { this.prop = 123; }\n"
        + "function f(x) { if (x instanceof Foo) { var y = x.prop; } }");

    checkNoWarnings("/** @constructor */ function Foo() { this.prop = 123; }\n"
        + "/** @constructor */ function Bar() { this.prop = 123; }\n"
        + "function f(cond, x) {\n"
        + "  x = cond ? new Foo : new Bar;\n"
        + "  var y = x.prop;\n"
        + "}");
  }

  public void testFunctionsWithAbnormalExit() {
    checkNoWarnings("function f(x) { x = 1; throw x; }");

    // TODO(dimvar): to fix these, we must collect all THROWs w/out an out-edge
    // and use the envs from them in the summary calculation. (Rare case.)

    // typeCheck(
    //     "function f(x) {\n" +
    //     "  var y = 1;\n" +
    //     "  x < y;\n" +
    //     "  throw 123;\n" +
    //     "}\n" +
    //     "f('asdf');",
    //     NewTypeInference.INVALID_ARGUMENT_TYPE);
    // typeCheck(
    //     "function f(x, cond) {\n" +
    //     "  if (cond) {\n" +
    //     "    var y = 1;\n" +
    //     "    x < y;\n" +
    //     "    throw 123;\n" +
    //     "  }\n" +
    //     "}\n" +
    //     "f('asdf', 'whatever');",
    //     NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testAssignAdd() {
    // Without a type annotation, we can't find the type error here.
    typeCheck(
        "function f(x, y) {\n"
        + "  x < y;\n"
        + "  var /** number */ z = 5;\n"
        + "  z += y;\n"
        + "}\n"
        + "f('asdf', 5);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("function f(x, y) {\n"
        + "  x < y;\n"
        + "  var z = 5;\n"
        + "  z += y;\n"
        + "}\n"
        + "f('asdf', 5);");

    typeCheck(
        "var s = 'asdf'; (s += 'asdf') - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings("var s = 'asdf'; s += 5;");

    typeCheck("var b = true; b += 5;", NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "var n = 123; n += 'asdf';", NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "var s = 'asdf'; s += true;", NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testTypeCoercions() {
    typeCheck(
        "function f(/** * */ x) {\n"
        + "  var /** string */ s = !x;\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "function f(/** * */ x) {\n"
        + "  var /** string */ s = +x;\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings(
        "function f(/** * */ x) {\n"
        + "  var /** string */ s = '' + x;\n"
        + "}");

    typeCheck(
        "function f(/** * */ x) {\n"
        + "  var /** number */ s = '' + x;\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testSwitch() {
    checkNoWarnings(
        "switch (1) { case 1: break; case 2: break; default: break; }");

    typeCheck(
        "switch (1) {\n"
        + "  case 1:\n"
        + "    1 - 'asdf';\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "switch (1) {\n"
        + "  default:\n"
        + "    1 - 'asdf';\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "switch (1 - 'asdf') {\n"
        + "  case 1:\n"
        + "    break;\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "switch (1) {\n"
        + "  case (1 - 'asdf'):\n"
        + "    break;\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings("/** @constructor */\n"
        + "function Foo() {}\n"
        + "function f(/** Foo */ x) {\n"
        + "  switch (x) {\n"
        + "    case null:\n"
        + "      break;\n"
        + "    default:\n"
        + "      var /** !Foo */ y = x;\n"
        + "  }\n"
        + "}");

    checkNoWarnings("function f(x) {\n"
        + "  switch (x) {\n"
        + "    case 123:\n"
        + "      x - 5;\n"
        + "  }\n"
        + "}");

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "function f(/** Foo */ x) {\n"
        + "  switch (x) {\n"
        + "    case null:\n"
        + "    default:\n"
        + "      var /** !Foo */ y = x;\n"
        + "  }\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "function f(x) {\n"
        + "  switch (x) {\n"
        + "    case null:\n"
        + "      x - 5;\n"
        + "  }\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(x) {\n"
        + "  switch (x) {\n"
        + "    case null:\n"
        + "      var /** undefined */ y = x;\n"
        + "  }\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    // Tests for fall-through
    typeCheck(
        "function f(x) {\n"
        + "  switch (x) {\n"
        + "    case 1: x - 5;\n"
        + "    case 'asdf': x < 123; x < 'asdf'; break;\n"
        + "  }\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings("function f(x) {\n"
        + "  switch (x) {\n"
        + "    case 1: x - 5;\n"
        + "    case 'asdf': break;\n"
        + "  }\n"
        + "}");

    typeCheck(
        "function g(/** number */ x) { return 5; }\n"
        + "function f() {\n"
        + "  switch (3) { case g('asdf'): return 123; }\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    // TODO(dimvar): warn for type mismatch between label and condition
    checkNoWarnings("function f(/** number */ x, /** string */ y) {\n"
        + "  switch (y) { case x: ; }\n"
        + "}");
  }

  public void testForIn() {
    checkNoWarnings("function f(/** string */ y) {\n"
        + "  for (var x in { a: 1, b: 2 }) { y = x; }\n"
        + "  x = 234;\n"
        + "  return 123;\n"
        + "}");

    typeCheck(
        "function f(y) {\n"
        + "  var z = x + 234;\n"
        + "  for (var x in { a: 1, b: 2 }) {}\n"
        + "  return 123;\n"
        + "}",
        ImmutableList.of(
            // VariableReferenceCheck.EARLY_REFERENCE,
            NewTypeInference.INVALID_OPERAND_TYPE));

    typeCheck(
        "function f(/** number */ y) {\n"
        + "  for (var x in { a: 1, b: 2 }) { y = x; }\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck("for (var x in 123) ;", NewTypeInference.FORIN_EXPECTS_OBJECT);

    typeCheck(
        "var /** number */ x = 5; for (x in {a : 1});",
        NewTypeInference.FORIN_EXPECTS_STRING_KEY);

    typeCheck(
        "function f(/** undefined */ y) {\n"
        + "  var x;\n"
        + "  for (x in { a: 1, b: 2 }) { y = x; }\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testTryCatch() {
    // typeCheck(
    //     "try { e; } catch (e) {}",
    //     VariableReferenceCheck.EARLY_REFERENCE);

    // typeCheck(
    //     "e; try {} catch (e) {}",
    //     VariableReferenceCheck.EARLY_REFERENCE);

    checkNoWarnings("try {} catch (e) { e; }");
    // If the CFG can see that the TRY won't throw, it doesn't go to the catch.
    checkNoWarnings("try {} catch (e) { 1 - 'asdf'; }");

    typeCheck(
        "try { throw 123; } catch (e) { 1 - 'asdf'; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "try { throw 123; } catch (e) {} finally { 1 - 'asdf'; }",
        NewTypeInference.INVALID_OPERAND_TYPE);
    // The next tests should fail when we model local scopes properly.
    checkNoWarnings("try {} catch (e) {} e;");

    // typeCheck(
    //     "var /** string */ e = 'asdf'; try {} catch (e) {} e - 5;",
    //     VariableReferenceCheck.REDECLARED_VARIABLE);
  }

  public void testIn() {
    typeCheck("(true in {});", NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck("('asdf' in 123);", NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "var /** number */ n = ('asdf' in {});",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings("function f(/** { a: number } */ obj) {\n"
        + "  if ('p' in obj) {\n"
        + "    return obj.p;\n"
        + "  }\n"
        + "}\n"
        + "f({ a: 123 });");

    typeCheck(
        "function f(/** { a: number } */ obj) {\n"
        + "  if (!('p' in obj)) {\n"
        + "    return obj.p;\n"
        + "  }\n"
        + "}",
        TypeCheck.INEXISTENT_PROPERTY);
  }

  public void testDelprop() {
    checkNoWarnings("delete ({ prop: 123 }).prop;");

    typeCheck(
        "var /** number */ x = delete ({ prop: 123 }).prop;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
    // We don't detect the missing property
    checkNoWarnings("var obj = { a: 1, b: 2 }; delete obj.a; obj.a;");
  }

  public void testArrayLit() {
    typeCheck("[1, 2, 3 - 'asdf']", NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(x, y) {\n"
        + "  x < y;\n"
        + "  [y - 5];\n"
        + "}\n"
        + "f('asdf', 123);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testArrayAccesses() {
    typeCheck(
        "var a = [1,2,3]; a['str'];", NewTypeInference.NON_NUMERIC_ARRAY_INDEX);

    typeCheck(
        "function f(/** !Array<number> */ arr, i) {\n"
        + "  arr[i];\n"
        + "}\n"
        + "f([1, 2, 3], 'str');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testRegExpLit() {
    checkNoWarnings("/abc/");
  }

  public void testDifficultLvalues() {
    checkNoWarnings("function f() { return {}; }\n"
        + "f().x = 123;");

    checkNoWarnings("function f() { return {}; }\n"
        + "f().ns = {};");

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() { /** @type {number} */ this.a = 123; }\n"
        + "/** @return {!Foo} */\n"
        + "function retFoo() { return new Foo(); }\n"
        + "function f(cond) {\n"
        + "  (retFoo()).a = 'asdf';\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "(new Foo).x += 123;",
        TypeCheck.INEXISTENT_PROPERTY);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() { /** @type {number} */ this.a = 123; }\n"
        + "function f(cond, /** !Foo */ foo1) {\n"
        + "  var /** { a: number } */ x = { a: 321 };\n"
        + "  (cond ? foo1 : x).a = 'asdf';\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "function f(obj) { obj[1 - 'str'] = 3; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(/** undefined */ n, pname) { n[pname] = 3; }",
        NewTypeInference.PROPERTY_ACCESS_ON_NONOBJECT);
  }

  public void testQuestionableUnionJsDoc() {
    // 'string|?' is the same as '?'
    typeCheck(
        "/** @type {string|?} */ var x;",
        JSTypeCreatorFromJSDoc.BAD_JSDOC_ANNOTATION);

    checkNoWarnings(""
        + "/**\n"
        + " * @return {T|S}\n"
        + " * @template T, S\n"
        + " */\n"
        + "function f(){};");
  }

  public void testGenericsJsdocParsing() {
    checkNoWarnings("/** @template T\n@param {T} x */ function f(x) {}");

    checkNoWarnings("/** @template T\n @param {T} x\n @return {T} */\n"
        + "function f(x) { return x; };");

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @constructor\n"
        + " * @param {T} x\n"
        + " * @extends {Bar<T>} // error, Bar is not templatized \n"
        + " */\n"
        + "function Foo(x) {}\n"
        + "/** @constructor */\n"
        + "function Bar() {}",
        JSTypeCreatorFromJSDoc.INVALID_GENERICS_INSTANTIATION);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @constructor\n"
        + " * @param {T} x\n"
        + " */\n"
        + "function Foo(x) {}\n"
        + "/** @param {Foo<number, string>} x */\n"
        + "function f(x) {}",
        JSTypeCreatorFromJSDoc.INVALID_GENERICS_INSTANTIATION);

    checkNoWarnings("/** @type {Array<number>} */ var x;");

    checkNoWarnings("/** @type {Object<number>} */ var x;");

    typeCheck(
        "/** @template T\n@param {!T} x */ function f(x) {}",
        JSTypeCreatorFromJSDoc.BAD_JSDOC_ANNOTATION);
  }

  public void testPolymorphicFunctionInstantiation() {
    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @return {T}\n"
        + " */\n"
        + "function id(x) { return x; }\n"
        + "id('str') - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {T} y\n"
        + " */\n"
        + "function f(x, y) {}\n"
        + "f(123, 'asdf');",
        NewTypeInference.NOT_UNIQUE_INSTANTIATION);

    checkNoWarnings("/**\n"
        + " * @template T\n"
        + " * @param {(T|null)} x\n"
        + " * @return {(T|number)}\n"
        + " */\n"
        + "function f(x) { return x === null ? 123 : x; }\n"
        + "/** @return {(null|undefined)} */ function g() { return null; }\n"
        + "var /** (number|undefined) */ y = f(g());");

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {(T|number)} x\n"
        + " */\n"
        + "function f(x) {}\n"
        + "/** @return {*} */ function g() { return 1; }\n"
        + "f(g());",
        NewTypeInference.FAILED_TO_UNIFY);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @return {T}\n"
        + " */\n"
        + "function id(x) { return x; }\n"
        + "/** @return {*} */ function g() { return 1; }\n"
        + "id(g()) - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T, U\n"
        + " * @param {T} x\n"
        + " * @param {U} y\n"
        + " * @return {U}\n"
        + " */\n"
        + "function f(x, y) { return y; }\n"
        + "f(10, 'asdf') - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function g(x) {\n"
        + "  /**\n"
        + "   * @template T\n"
        + "   * @param {T} x\n"
        + "   * @param {T} y\n"
        + "   */\n"
        + "  function f(x, y) {}\n"
        + "  f(x, 5);\n"
        + "}\n"
        + "g('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("function g(/** ? */ x) {\n"
        + "  /**\n"
        + "   * @template T\n"
        + "   * @param {(T|number)} x\n"
        + "   */\n"
        + "  function f(x) {}\n"
        + "  f(x)\n"
        + "}");
    // TODO(blickly): Catching the INVALID_ARGUMENT_TYPE here requires
    // return-type unification.
    checkNoWarnings("function g(x) {\n"
        + "  /**\n"
        + "   * @template T\n"
        + "   * @param {T} x\n"
        + "   * @return {T}\n"
        + "   */\n"
        + "  function f(x) { return x; }\n"
        + "  f(x) - 5;\n"
        + "  x = 'asdf';\n"
        + "}\n"
        + "g('asdf');");
    // Empty instantiations
    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {(T|number)} x\n"
        + " */\n"
        + "function f(x) {}\n"
        + "f(123);",
        NewTypeInference.FAILED_TO_UNIFY);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {(T|null)} x\n"
        + " * @param {(T|number)} y\n"
        + " */\n"
        + "function f(x, y) {}\n"
        + "f(null, 'str');",
        NewTypeInference.FAILED_TO_UNIFY);

    typeCheck(
        "/** @constructor */ function Foo(){};\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {(T|Foo)} x\n"
        + " * @param {(T|number)} y\n"
        + " */\n"
        + "function f(x, y) {}\n"
        + "f(new Foo(), 'str');",
        NewTypeInference.FAILED_TO_UNIFY);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {function(T):T} f\n"
        + " * @param {T} x\n"
        + " */\n"
        + "function apply(f, x) { return f(x); }\n"
        + "/** @type {string} */"
        + "var out;"
        + "var result = apply(function(x){ out = x; return x; }, 0);",
        ImmutableList.of(
            NewTypeInference.NOT_UNIQUE_INSTANTIATION,
            NewTypeInference.MISTYPED_ASSIGN_RHS));

    typeCheck(
        "/** @template T */\n"
        + "function f(/** T */ x, /** T */ y) {}\n"
        + "f(1, 'str');",
        NewTypeInference.NOT_UNIQUE_INSTANTIATION);

    typeCheck(
        "/** @template T */\n"
        + "function /** T */ f(/** T */ x) { return x; }\n"
        + "f('str') - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " */\n"
        + "function f(x) {\n"
        + "  /** @constructor */\n"
        + "  function Foo() {\n"
        + "    /** @type {T} */\n"
        + "    this.prop = x;\n"
        + "  }\n"
        + "  return (new Foo()).prop;\n"
        + "}\n"
        + "f('asdf') - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings("/**\n"
        + " * @template T\n"
        + " * @param {*} x\n"
        + " */\n"
        + "function f(x) {}\n"
        + "f(123);");

    checkNoWarnings("/**\n"
        + " * @constructor\n"
        + " * @template T\n"
        + " */\n"
        + "function Foo() {}\n"
        + "/**\n"
        + " * @template U\n"
        + " * @param {function(U)} x\n"
        + " */\n"
        + "Foo.prototype.f = function(x) { this.f(x); };");
  }

  public void testUnification() {
    typeCheck(
        "/** @constructor */ function Foo(){};\n"
        + "/** @constructor */ function Bar(){};\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @return {T}\n"
        + " */\n"
        + "function id(x) { return x; }\n"
        + "var /** Bar */ x = id(new Foo);",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @return {T}\n"
        + " */\n"
        + "function id(x) { return x; }\n"
        + "id({}) - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings("/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @return {T}\n"
        + " */\n"
        + "function id(x) { return x; }\n"
        + "var /** (number|string) */ x = id('str');");

    typeCheck(
        "function f(/** * */ a, /** string */ b) {\n"
        + "  /**\n"
        + "   * @template T\n"
        + "   * @param {T} x\n"
        + "   * @param {T} y\n"
        + "   */\n"
        + "  function f(x, y) {}\n"
        + "  f(a, b);\n"
        + "}",
        NewTypeInference.NOT_UNIQUE_INSTANTIATION);

    checkNoWarnings("function f(/** string */ b) {\n"
        + "  /**\n"
        + "   * @template T\n"
        + "   * @param {T} x\n"
        + "   * @param {T} y\n"
        + "   */\n"
        + "  function f(x, y) {}\n"
        + "  f({p:5, r:'str'}, {p:20, r:b});\n"
        + "}");

    typeCheck(
        "function f(/** string */ b) {\n"
        + "  /**\n"
        + "   * @template T\n"
        + "   * @param {T} x\n"
        + "   * @param {T} y\n"
        + "   */\n"
        + "  function f(x, y) {}\n"
        + "  f({r:'str'}, {p:20, r:b});\n"
        + "}",
        NewTypeInference.NOT_UNIQUE_INSTANTIATION);

    typeCheck(
        "function g(x) {\n"
        + "  /**\n"
        + "   * @template T\n"
        + "   * @param {T} x\n"
        + "   * @param {T} y\n"
        + "   */\n"
        + "  function f(x, y) {}\n"
        + "  var /** boolean */ y = true;\n"
        + "  f(x, y);\n"
        + "}\n"
        + "g('str');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {number} y\n"
        + " */\n"
        + "function f(x, y) {}\n"
        + "f(123, 'asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/**\n"
        + " * @constructor\n"
        + " * @template T\n"
        + " */\n"
        + "function Foo() {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {Foo<T>} x\n"
        + " */\n"
        + "function takesFoo(x) {}\n"
        + "takesFoo(undefined);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("/**\n"
        + " * @template T\n"
        + " * @param {T|undefined} x\n"
        + " */\n"
        + "function f(x) {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {T|undefined} x\n"
        + " */\n"
        + "function g(x) { f(x); }");

    checkNoWarnings("/**\n"
        + " * @template T\n"
        + " * @param {T|undefined} x\n"
        + " * @return {T}\n"
        + " */\n"
        + "function f(x) {\n"
        + "  if (x === undefined) {\n"
        + "    throw new Error('');\n"
        + "  }\n"
        + "  return x;\n"
        + "}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {T|undefined} x\n"
        + " * @return {T}\n"
        + " */\n"
        + "function g(x) { return f(x); }\n"
        + "g(123) - 5;");

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T|undefined} x\n"
        + " * @return {T}\n"
        + " */\n"
        + "function f(x) {\n"
        + "  if (x === undefined) {\n"
        + "    throw new Error('');\n"
        + "  }\n"
        + "  return x;\n"
        + "}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {T|undefined} x\n"
        + " * @return {T}\n"
        + " */\n"
        + "function g(x) { return f(x); }\n"
        + "g(123) < 'asdf';",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testUnifyObjects() {
    checkNoWarnings("function f(b) {\n"
        + "  /**\n"
        + "   * @template T\n"
        + "   * @param {T} x\n"
        + "   * @param {T} y\n"
        + "   */\n"
        + "  function f(x, y) {}\n"
        + "  f({p:5, r:'str'}, {p:20, r:b});\n"
        + "}");

    checkNoWarnings("function f(b) {\n"
        + "  /**\n"
        + "   * @template T\n"
        + "   * @param {T} x\n"
        + "   * @param {T} y\n"
        + "   */\n"
        + "  function f(x, y) {}\n"
        + "  f({p:20, r:b}, {p:5, r:'str'});\n"
        + "}");

    typeCheck(
        "function g(x) {\n"
        + "  /**\n"
        + "   * @template T\n"
        + "   * @param {T} x\n"
        + "   * @param {T} y\n"
        + "   */\n"
        + "  function f(x, y) {}\n"
        + "  f({prop: x}, {prop: 5});\n"
        + "}\n"
        + "g('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function g(x, cond) {\n"
        + "  /**\n"
        + "   * @template T\n"
        + "   * @param {T} x\n"
        + "   * @param {T} y\n"
        + "   */\n"
        + "  function f(x, y) {}\n"
        + "  var y = cond ? {prop: 'str'} : {prop: 5};\n"
        + "  f({prop: x}, y);\n"
        + "}\n"
        + "g({}, true);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function g(x, cond) {\n"
        + "  /**\n"
        + "   * @template T\n"
        + "   * @param {T} x\n"
        + "   * @param {T} y\n"
        + "   */\n"
        + "  function f(x, y) {}\n"
        + "  /** @type {{prop : (string | number)}} */\n"
        + "  var y = cond ? {prop: 'str'} : {prop: 5};\n"
        + "  f({prop: x}, y);\n"
        + "}\n"
        + "g({}, true);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {{a: number, b: T}} x\n"
        + " * @return {T}\n"
        + " */\n"
        + "function f(x) { return x.b; }\n"
        + "f({a: 1, b: 'asdf'}) - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @return {T}\n"
        + " */\n"
        + "function f(x) { return x.b; }\n"
        + "f({b: 'asdf'}) - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testFunctionTypeUnifyUnknowns() {
    checkNoWarnings("/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {T} y\n"
        + " */\n"
        + "function f(x, y) {}\n"
        + "/** @type {function(number)} */\n"
        + "function g(x) {}\n"
        + "/** @type {function(?)} */\n"
        + "function h(x) {}\n"
        + "f(g, h);");

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {T} y\n"
        + " */\n"
        + "function f(x, y) {}\n"
        + "/** @type {function(number)} */\n"
        + "function g(x) {}\n"
        + "/** @type {function(string)} */\n"
        + "function h(x) {}\n"
        + "f(g, h);",
        NewTypeInference.NOT_UNIQUE_INSTANTIATION);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {T} y\n"
        + " */\n"
        + "function f(x, y) {}\n"
        + "/** @type {function(number)} */\n"
        + "function g(x) {}\n"
        + "/** @type {function(?, string)} */\n"
        + "function h(x, y) {}\n"
        + "f(g, h);",
        NewTypeInference.NOT_UNIQUE_INSTANTIATION);

    checkNoWarnings("/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {T} y\n"
        + " */\n"
        + "function f(x, y) {}\n"
        + "/** @type {function(number=, ...[string])} */\n"
        + "function g(x) {}\n"
        + "/** @type {function(number=, ...[?])} */\n"
        + "function h(x) {}\n"
        + "f(g, h);");

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {T} y\n"
        + " */\n"
        + "function f(x, y) {}\n"
        + "/** @type {function(number):number} */\n"
        + "function g(x) { return 1; }\n"
        + "/** @type {function(?):string} */\n"
        + "function h(x) { return ''; }\n"
        + "f(g, h);",
        NewTypeInference.NOT_UNIQUE_INSTANTIATION);

    checkNoWarnings("/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {T} y\n"
        + " */\n"
        + "function f(x, y) {}\n"
        + "/** @constructor */ function Foo() {}\n"
        + "/** @type {function(new:Foo)} */\n"
        + "function g() {}\n"
        + "/** @type {function(new:Foo)} */\n"
        + "function h() {}\n"
        + "f(g, h);");

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {T} y\n"
        + " */\n"
        + "function f(x, y) {}\n"
        + "/** @constructor */ function Foo() {}\n"
        + "/** @constructor */ function Bar() {}\n"
        + "/** @type {function(this:Foo)} */\n"
        + "function g() {}\n"
        + "/** @type {function(this:Bar)} */\n"
        + "function h() {}\n"
        + "f(g, h);",
        NewTypeInference.NOT_UNIQUE_INSTANTIATION);
  }

  public void testInstantiationInsideObjectTypes() {
    typeCheck(
        "/**\n"
        + " * @template U\n"
        + " * @param {U} y\n"
        + " */\n"
        + "function g(y) {\n"
        + "  /**\n"
        + "   * @template T\n"
        + "   * @param {{a: U, b: T}} x\n"
        + "   * @return {T}\n"
        + "   */\n"
        + "  function f(x) { return x.b; }\n"
        + "  f({a: y, b: 'asdf'}) - 5;\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/**\n"
        + " * @template U\n"
        + " * @param {U} y\n"
        + " */\n"
        + "function g(y) {\n"
        + "  /**\n"
        + "   * @template T\n"
        + "   * @param {{b: T}} x\n"
        + "   * @return {T}\n"
        + "   */\n"
        + "  function f(x) { return x.b; }\n"
        + "  f({b: y}) - 5;\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testInstantiateInsideFunctionTypes() {
    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {function(T):T} fun\n"
        + " */\n"
        + "function f(x, fun) {}\n"
        + "function g(x) { return x - 5; }\n"
        + "f('asdf', g);",
        NewTypeInference.NOT_UNIQUE_INSTANTIATION);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {function(T):number} fun\n"
        + " */\n"
        + "function f(fun) {}\n"
        + "function g(x) { return 'asdf'; }\n"
        + "f(g);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("/**\n"
        + " * @template T\n"
        + " * @param {function(T=)} fun\n"
        + " */\n"
        + "function f(fun) {}\n"
        + "/** @param{string=} x */ function g(x) {}\n"
        + "f(g);");

    checkNoWarnings("/**\n"
        + " * @template T\n"
        + " * @param {function(... [T])} fun\n"
        + " */\n"
        + "function f(fun) {}\n"
        + "/** @param {...number} var_args */ function g(var_args) {}\n"
        + "f(g);");
  }

  public void testPolymorphicFuncallsFromDifferentScope() {
    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @return {T}\n"
        + " */\n"
        + "function id(x) { return x; }\n"
        + "function g() {\n"
        + "  id('asdf') - 5;\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {number} y\n"
        + " */\n"
        + "function f(x, y) {}\n"
        + "function g() {\n"
        + "  f('asdf', 'asdf');\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {T} y\n"
        + " */\n"
        + "function f(x, y) {}\n"
        + "function g() {\n"
        + "  f(123, 'asdf');\n"
        + "}",
        NewTypeInference.NOT_UNIQUE_INSTANTIATION);
  }

  public void testOpacityOfTypeParameters() {
    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " */\n"
        + "function f(x) {\n"
        + "  x - 5;\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {{ a: T }} x\n"
        + " */\n"
        + "function f(x) {\n"
        + "  x.a - 5;\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {function(T):T} fun\n"
        + " */\n"
        + "function f(x, fun) {\n"
        + "  fun(x) - 5;\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @return {T}\n"
        + " */\n"
        + "function f(x) {\n"
        + "  return 5;\n"
        + "}",
        NewTypeInference.RETURN_NONDECLARED_TYPE);

    checkNoWarnings("/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " */\n"
        + "function f(x) {\n"
        + "  var /** ? */ y = x;\n"
        + "}");

    checkNoWarnings("/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @return {(T|number)}\n"
        + " */\n"
        + "function f(x) {\n"
        + "  var y;\n"
        + "  if (1 < 2) {\n"
        + "    y = x;\n"
        + "  } else {\n"
        + "    y = 123;\n"
        + "  }\n"
        + "  return y;\n"
        + "}");

    checkNoWarnings("/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @return {(T|number)}\n"
        + " */\n"
        + "function f(x) {\n"
        + "  var y;\n"
        + "  if (1 < 2) {\n"
        + "    y = x;\n"
        + "  } else {\n"
        + "    y = 123;\n"
        + "  }\n"
        + "  return y;\n"
        + "}\n"
        + "f(123) - 5;");

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @return {(T|number)}\n"
        + " */\n"
        + "function f(x) {\n"
        + "  var y;\n"
        + "  if (1 < 2) {\n"
        + "    y = x;\n"
        + "  } else {\n"
        + "    y = 123;\n"
        + "  }\n"
        + "  return y;\n"
        + "}\n"
        + "var /** (number|boolean) */ z = f('asdf');",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " */\n"
        + "function f(x) {\n"
        + "  var /** T */ y = x;\n"
        + "  y - 5;\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T, U\n"
        + " * @param {T} x\n"
        + " * @param {U} y\n"
        + " */\n"
        + "function f(x, y) {\n"
        + "  x = y;\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testGenericClassInstantiation() {
    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @constructor\n"
        + " */\n"
        + "function Foo(x) {}\n"
        + "/** @param {T} y */\n"
        + "Foo.prototype.bar = function(y) {}\n"
        + "new Foo('str').bar(5)",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @constructor\n"
        + " */\n"
        + "function Foo(x) {}\n"
        + "/** @type {function(T)} y */\n"
        + "Foo.prototype.bar = function(y) {};\n"
        + "new Foo('str').bar(5)",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @constructor\n"
        + " */\n"
        + "function Foo(x) { /** @type {T} */ this.x = x; }\n"
        + "/** @return {T} */\n"
        + "Foo.prototype.bar = function() { return this.x; };\n"
        + "new Foo('str').bar() - 5",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @constructor\n"
        + " */\n"
        + "function Foo(x) { /** @type {T} */ this.x = x; }\n"
        + "/** @type {function() : T} */\n"
        + "Foo.prototype.bar = function() { return this.x; };\n"
        + "new Foo('str').bar() - 5",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @constructor\n"
        + " */\n"
        + "function Foo(x) {}\n"
        + "/** @type {function(this:Foo<T>, T)} */\n"
        + "Foo.prototype.bar = function(x) { this.x = x; };\n"
        + "new Foo('str').bar(5)",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @constructor\n"
        + " */\n"
        + "function Foo(x) {}\n"
        + "/** @param {!Foo<number>} x */\n"
        + "function f(x) {}\n"
        + "f(new Foo(7));");

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @constructor\n"
        + " */\n"
        + "function Foo(x) {}\n"
        + "/** @param {Foo<number>} x */\n"
        + "function f(x) {}\n"
        + "f(new Foo('str'));",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @constructor\n"
        + " */\n"
        + "function Foo(x) {}\n"
        + "/** @param {T} x */\n"
        + "Foo.prototype.method = function(x) {};\n"
        + "/** @param {!Foo<number>} x */\n"
        + "function f(x) { x.method('asdf'); }",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/**\n"
        + " * @constructor\n"
        + " * @template T\n"
        + " */\n"
        + "function Foo() {}\n"
        + "/** @param {T} x */\n"
        + "Foo.prototype.method = function(x) {};\n"
        + "var /** @type {Foo<string>} */ foo = null;\n"
        + "foo.method('asdf');",
        NewTypeInference.PROPERTY_ACCESS_ON_NONOBJECT);
  }

  public void testLooserCheckingForInferredProperties() {
    checkNoWarnings("/** @constructor */\n"
        + "function Foo(x) { this.prop = x; }\n"
        + "function f(/** !Foo */ obj) {\n"
        + "  obj.prop = true ? 1 : 'asdf';\n"
        + "  obj.prop - 5;\n"
        + "}");

    checkNoWarnings("/** @constructor */\n"
        + "function Foo(x) { this.prop = x; }\n"
        + "function f(/** !Foo */ obj) {\n"
        + "  if (!(typeof obj.prop == 'number')) {\n"
        + "    obj.prop < 'asdf';\n"
        + "  }\n"
        + "}");

    typeCheck(
        "/** @constructor */\n"
        + "function Foo(x) { this.prop = x; }\n"
        + "function f(/** !Foo */ obj) {\n"
        + "  obj.prop = true ? 1 : 'asdf';\n"
        + "  obj.prop - 5;\n"
        + "  obj.prop < 'asdf';\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function /** string */ f(/** ?number */ x) {\n"
        + "  var o = { prop: 'str' };\n"
        + "  if (x) {\n"
        + "    o.prop = x;\n"
        + "  }\n"
        + "  return o.prop;\n"
        + "}",
        NewTypeInference.RETURN_NONDECLARED_TYPE);
  }

  public void testInheritanceWithGenerics() {
    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @interface\n"
        + " */\n"
        + "function I() {}\n"
        + "/** @param {T} x */\n"
        + "I.prototype.bar = function(x) {};\n"
        + "/** @constructor @implements {I<number>} */\n"
        + "function Foo() {}\n"
        + "Foo.prototype.bar = function(x) {};\n"
        + "(new Foo).bar('str');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @interface\n"
        + " */\n"
        + "function I() {}\n"
        + "/** @param {T} x */\n"
        + "I.prototype.bar = function(x) {};\n"
        + "/** @constructor @implements {I<number>} */\n"
        + "function Foo() {}\n"
        + "/** @override */\n"
        + "Foo.prototype.bar = function(x) {};\n"
        + "new Foo().bar('str');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @interface\n"
        + " */\n"
        + "function I() {}\n"
        + "/** @param {T} x */\n"
        + "I.prototype.bar = function(x) {};\n"
        + "/**\n"
        + " * @template U\n"
        + " * @constructor\n"
        + " * @implements {I<U>}\n"
        + " * @param {U} x\n"
        + " */\n"
        + "function Foo(x) {}\n"
        + "Foo.prototype.bar = function(x) {};{}\n"
        + "new Foo(5).bar('str');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @interface\n"
        + " */\n"
        + "function I() {}\n"
        + "/** @param {T} x */\n"
        + "I.prototype.bar = function(x) {};\n"
        + "/** @constructor @implements {I<number>} */\n"
        + "function Foo() {}\n"
        + "Foo.prototype.bar = function(x) {};\n"
        + "/** @param {I<string>} x */ function f(x) {};\n"
        + "f(new Foo());",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @interface\n"
        + " */\n"
        + "function I() {}\n"
        + "/** @param {T} x */\n"
        + "I.prototype.bar = function(x) {};\n"
        + "/** @constructor @implements {I<number>} */\n"
        + "function Foo() {}\n"
        + "/** @param {string} x */\n"
        + "Foo.prototype.bar = function(x) {};",
        GlobalTypeInfo.INVALID_PROP_OVERRIDE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @interface\n"
        + " */\n"
        + "function I() {}\n"
        + "/** @param {T} x */\n"
        + "I.prototype.bar = function(x) {};\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @constructor @implements {I<number>}\n"
        + " */\n"
        + "function Foo(x) {}\n"
        + "/** @param {T} x */\n"
        + "Foo.prototype.bar = function(x) {};",
        GlobalTypeInfo.INVALID_PROP_OVERRIDE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @constructor\n"
        + " */\n"
        + "function Foo() {}\n"
        + "/** @param {T} x */\n"
        + "Foo.prototype.method = function(x) {};\n"
        + "/**\n"
        + " * @template T\n"
        + " * @constructor\n"
        + " * @extends {Foo<T>}\n"
        + " * @param {T} x\n"
        + " */\n"
        + "function Bar(x) {}\n"
        + "/** @param {number} x */\n"
        + "Bar.prototype.method = function(x) {};",
        GlobalTypeInfo.INVALID_PROP_OVERRIDE);

    checkNoWarnings("/**\n"
        + " * @template T\n"
        + " * @constructor\n"
        + " */\n"
        + "function High() {}\n"
        + "/** @param {Low<T>} x */\n"
        + "High.prototype.method = function(x) {};\n"
        + "/**\n"
        + " * @template T\n"
        + " * @constructor\n"
        + " * @extends {High<T>}\n"
        + " */\n"
        + "function Low() {}");

    checkNoWarnings("/**\n"
        + " * @template T\n"
        + " * @constructor\n"
        + " */\n"
        + "function High() {}\n"
        + "/** @param {Low<number>} x */\n"
        + "High.prototype.method = function(x) {};\n"
        + "/**\n"
        + " * @template T\n"
        + " * @constructor\n"
        + " * @extends {High<T>}\n"
        + " */\n"
        + "function Low() {}");

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @constructor\n"
        + " */\n"
        + "function High() {}\n"
        + "/** @param {Low<T>} x */ // error, low is not templatized\n"
        + "High.prototype.method = function(x) {};\n"
        + "/**\n"
        + " * @constructor\n"
        + " * @extends {High<number>}\n"
        + " */\n"
        + "function Low() {}",
        JSTypeCreatorFromJSDoc.INVALID_GENERICS_INSTANTIATION);

    // BAD INHERITANCE, WE DON'T HAVE A WARNING TYPE FOR THIS
    // TODO(dimvar): fix
    checkNoWarnings("/**\n"
        + " * @template T\n"
        + " * @interface\n"
        + " */\n"
        + "function I() {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @constructor\n"
        + " * @implements {I<T>}\n"
        + " * @extends {Bar}\n"
        + " */\n"
        + "function Foo(x) {}\n"
        + "/**\n"
        + " * @constructor\n"
        + " * @implements {I<number>}\n"
        + " */\n"
        + "function Bar(x) {}");

    checkNoWarnings("/**\n"
        + " * @interface\n"
        + " * @template T\n"
        + " */\n"
        + "function Foo() {}\n"
        + "/** @constructor @implements {Foo<number>} */\n"
        + "function A() {}\n"
        + "var /** Foo<number> */ x = new A();");
  }

  public void testGenericsSubtyping() {
    typeCheck(
        "/** @interface */ function Parent() {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {T} y\n"
        + " */\n"
        + "Parent.prototype.method = function(x, y){};\n"
        + "/** @constructor @implements {Parent} */\n"
        + "function Child() {}\n"
        + "/**\n"
        + " * @param {number} x\n"
        + " * @param {number} y\n"
        + " */\n"
        + "Child.prototype.method = function(x, y){};",
        GlobalTypeInfo.INVALID_PROP_OVERRIDE);

    typeCheck(
        "/** @interface */ function Parent() {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {T} y\n"
        + " */\n"
        + "Parent.prototype.method = function(x, y){};\n"
        + "/** @constructor @implements {Parent} */\n"
        + "function Child() {}\n"
        + "/**\n"
        + " * @param {?} x\n"
        + " * @param {number} y\n"
        + " */\n"
        + "Child.prototype.method = function(x, y){};",
        GlobalTypeInfo.INVALID_PROP_OVERRIDE);

    checkNoWarnings("/** @interface */ function Parent() {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {T} y\n"
        + " */\n"
        + "Parent.prototype.method = function(x, y){};\n"
        + "/** @constructor @implements {Parent} */\n"
        + "function Child() {}\n"
        + "/**\n"
        + " * @param {*} x\n"
        + " * @param {*} y\n"
        + " */\n"
        + "Child.prototype.method = function(x, y){};");

    checkNoWarnings("/** @interface */ function Parent() {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {T} y\n"
        + " */\n"
        + "Parent.prototype.method = function(x, y){};\n"
        + "/** @constructor @implements {Parent} */\n"
        + "function Child() {}\n"
        + "/**\n"
        + " * @param {?} x\n"
        + " * @param {?} y\n"
        + " */\n"
        + "Child.prototype.method = function(x, y){};");

    checkNoWarnings("/** @interface */ function Parent() {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @return {T}\n"
        + " */\n"
        + "Parent.prototype.method = function(x){};\n"
        + "/** @constructor @implements {Parent} */\n"
        + "function Child() {}\n"
        + "/**\n"
        + " * @param {?} x\n"
        + " * @return {?}\n"
        + " */\n"
        + "Child.prototype.method = function(x){ return x; };");

    checkNoWarnings("/** @interface */ function Parent() {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @return {T}\n"
        + " */\n"
        + "Parent.prototype.method = function(x){};\n"
        + "/** @constructor @implements {Parent} */\n"
        + "function Child() {}\n"
        + "/**\n"
        + " * @param {*} x\n"
        + " * @return {?}\n"
        + " */\n"
        + "Child.prototype.method = function(x){ return x; };");

    typeCheck(
        "/** @interface */ function Parent() {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @return {T}\n"
        + " */\n"
        + "Parent.prototype.method = function(x){};\n"
        + "/** @constructor @implements {Parent} */\n"
        + "function Child() {}\n"
        + "/**\n"
        + " * @param {*} x\n"
        + " * @return {*}\n"
        + " */\n"
        + "Child.prototype.method = function(x){ return x; };",
        GlobalTypeInfo.INVALID_PROP_OVERRIDE);

    typeCheck(
        "/** @interface */ function Parent() {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @return {T}\n"
        + " */\n"
        + "Parent.prototype.method = function(x){};\n"
        + "/** @constructor @implements {Parent} */\n"
        + "function Child() {}\n"
        + "/**\n"
        + " * @param {number} x\n"
        + " * @return {number}\n"
        + " */\n"
        + "Child.prototype.method = function(x){ return x; };",
        GlobalTypeInfo.INVALID_PROP_OVERRIDE);

    typeCheck(
        "/** @interface */ function Parent() {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @return {T}\n"
        + " */\n"
        + "Parent.prototype.method = function(x){};\n"
        + "/** @constructor @implements {Parent} */\n"
        + "function Child() {}\n"
        + "/**\n"
        + " * @param {?} x\n"
        + " * @return {*}\n"
        + " */\n"
        + "Child.prototype.method = function(x){ return x; };",
        GlobalTypeInfo.INVALID_PROP_OVERRIDE);

    typeCheck(
        "/** @interface */ function Parent() {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {function(T, T) : boolean} x\n"
        + " */\n"
        + "Parent.prototype.method = function(x){};\n"
        + "/** @constructor @implements {Parent} */\n"
        + "function Child() {}\n"
        + "/**\n"
        + " * @param {function(number, number) : boolean} x\n"
        + " */\n"
        + "Child.prototype.method = function(x){ return x; };",
        GlobalTypeInfo.INVALID_PROP_OVERRIDE);

    checkNoWarnings("/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " */\n"
        + "function f(x) {}\n"
        + "/** @param {function(number, number)} x */\n"
        + "function g(x) {}\n"
        + "g(f);");

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " */\n"
        + "function f(x) {}\n"
        + "/** @param {function()} x */\n"
        + "function g(x) {}\n"
        + "g(f);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("/** @interface */\n"
        + "function Parent() {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " */\n"
        + "Parent.prototype.method = function(x) {};\n"
        + "/**\n"
        + " * @constructor\n"
        + " * @implements {Parent}\n"
        + " */\n"
        + "function Child() {}\n"
        + "/**\n"
        + " * @template U\n"
        + " * @param {U} x\n"
        + " */\n"
        + "Child.prototype.method = function(x) {};");

    checkNoWarnings("/** @interface */ function Parent() {}\n"
        + "/** @param {string} x */\n"
        + "Parent.prototype.method = function(x){};\n"
        + "/** @constructor @implements {Parent} */\n"
        + "function Child() {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " */\n"
        + "Child.prototype.method = function(x){};");

    checkNoWarnings("/** @interface */ function Parent() {}\n"
        + "/** @param {*} x */\n"
        + "Parent.prototype.method = function(x){};\n"
        + "/** @constructor @implements {Parent} */\n"
        + "function Child() {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " */\n"
        + "Child.prototype.method = function(x){};");

    checkNoWarnings("/** @interface */ function Parent() {}\n"
        + "/** @param {?} x */\n"
        + "Parent.prototype.method = function(x){};\n"
        + "/** @constructor @implements {Parent} */\n"
        + "function Child() {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " */\n"
        + "Child.prototype.method = function(x){};");

    // This shows a bug in subtyping of generic functions.
    // We don't catch the invalid prop override.
    checkNoWarnings("/** @interface */ function Parent() {}\n"
        + "/**\n"
        + " * @param {string} x\n"
        + " * @param {number} y\n"
        + " */\n"
        + "Parent.prototype.method = function(x, y){};\n"
        + "/** @constructor @implements {Parent} */\n"
        + "function Child() {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {T} y\n"
        + " */\n"
        + "Child.prototype.method = function(x, y){};");

    // This shows a bug in subtyping of generic functions.
    // We don't catch the invalid prop override.
    checkNoWarnings("/** @interface */ function Parent() {}\n"
        + "/**\n"
        + " * @template A, B\n"
        + " * @param {A} x\n"
        + " * @param {B} y\n"
        + " * @return {A}\n"
        + " */\n"
        + "Parent.prototype.method = function(x, y){};\n"
        + "/** @constructor @implements {Parent} */\n"
        + "function Child() {}\n"
        + "/**\n"
        + " * @template A, B\n"
        + " * @param {A} x\n"
        + " * @param {B} y\n"
        + " * @return {B}\n"
        + " */\n"
        + "Child.prototype.method = function(x, y){ return y; };");
  }

  public void testGenericsVariance() {
    // Array generic parameter is co-variant
    checkNoWarnings("/** @constructor */ function Foo() {}\n"
        + "/** @constructor @extends {Foo} */ function Bar() {}\n"
        + "var /** Array<Foo> */ a = [new Bar];");

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @constructor @extends {Foo} */ function Bar() {}\n"
        + "var /** Array<Bar> */ a = [new Foo];",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    // TODO(blickly): Make other generics invariant to match old type inference
    checkNoWarnings(
        "/** @constructor @param {T} x @template T */ function Gen(x){}\n"
        + "/** @constructor */ function Foo() {}\n"
        + "/** @constructor @extends {Foo} */ function Bar() {}\n"
        + "var /** Gen<Foo> */ a = new Gen(new Bar);");

    typeCheck(
        "/** @constructor @param {T} x @template T */ function Gen(x){}\n"
        + "/** @constructor */ function Foo() {}\n"
        + "/** @constructor @extends {Foo} */ function Bar() {}\n"
        + "var /** Gen<Bar> */ a = new Gen(new Foo);",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testInferredArrayGenerics() {
    typeCheck(
        "/** @const */ var x = [];", GlobalTypeInfo.COULD_NOT_INFER_CONST_TYPE);

    typeCheck(
        "/** @const */ var x = [1, 'str'];",
        GlobalTypeInfo.COULD_NOT_INFER_CONST_TYPE);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @constructor @extends {Foo} */ function Bar() {}\n"
        + "/** @const */ var x = [new Foo, new Bar];",
        GlobalTypeInfo.COULD_NOT_INFER_CONST_TYPE);

    typeCheck(
        "var /** Array<string> */ a = [1, 2];",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings("var arr = [];\n"
        + "var /** Array<string> */ as = arr;");

    typeCheck(
        "var arr = [1, 2, 3];\n"
        + "var /** Array<string> */ as = arr;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "var /** Array<string> */ a = [new Foo, new Foo];",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings("/** @constructor */ function Foo() {}\n"
        + "/** @constructor @extends {Foo} */ function Bar() {}\n"
        + "var /** Array<Foo> */ a = [new Foo, new Bar];");

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @constructor @extends {Foo} */ function Bar() {}\n"
        + "var /** Array<Bar> */ a = [new Foo, new Bar];",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @const */ var x = [1, 2, 3];\n"
        + "function g() { var /** Array<string> */ a = x; }",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @constructor @extends {Foo} */ function Bar() {}\n"
        + "/** @const */ var x = [new Foo, new Foo];\n"
        + "function g() { var /** Array<Bar> */ a = x; }",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testSpecializedInstanceofCantGoToBottom() {
    checkNoWarnings("/** @const */ var ns = {};\n"
        + "ns.f = function() {};\n"
        + "if (ns.f instanceof Function) {}");

    checkNoWarnings("/** @constructor */ function Foo(){}\n"
        + "/** @const */ var ns = {};\n"
        + "ns.f = new Foo;\n"
        + "if (ns.f instanceof Foo) {}");

    checkNoWarnings("/** @constructor */ function Foo(){}\n"
        + "/** @constructor */ function Bar(){}\n"
        + "/** @const */ var ns = {};\n"
        + "ns.f = new Foo;\n"
        + "if (ns.f instanceof Bar) {}");
  }

  public void testDeclaredGenericArrayTypes() {
    typeCheck(
        "/** @type {Array<string>} */\n"
        + "var arr = ['str'];\n"
        + "arr[0]++;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "var arr = ['str'];\n"
        + "arr[0]++;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function foo (/** Array<string> */ a) {}\n"
        + "/** @type {Array<number>} */\n"
        + "var b = [1];\n"
        + "foo(b);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function foo (/** Array<string> */ a) {}\n"
        + "foo([1]);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @type {!Array<number>} */\n"
        + "var arr = [1, 2, 3];\n"
        + "arr[0] = 'str';",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings("/** @type {!Array<number>} */\n"
        + "var arr = [1, 2, 3];\n"
        + "arr['0'] = 'str';");

    // We warn here even though the declared type of the lvalue includes null.
    typeCheck(
        "/** @type {Array<number>} */\n"
        + "var arr = [1, 2, 3];\n"
        + "arr[0] = 'str';",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "function f(/** Array<number> */ arr) {\n"
        + "  arr[0] = 'str';\n"
        + "}",
        NewTypeInference.NULLABLE_DEREFERENCE);

    typeCheck(
        "/** @const */\n"
        + "var arr = [1, 2, 3];\n"
        + "arr[0] = 'str';",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings("var arr = [1, 2, 3];\n"
        + "arr[0] = 'str';");

    checkNoWarnings("/** @constructor */ function Super(){}\n"
        + "/** @constructor @extends {Super} */ function Sub(){}\n"
        + "/** @type {!Array<Super>} */ var arr = [new Sub];\n"
        + "arr[0] = new Super;");

    typeCheck(
        "/** @type {Array<number>} */ var arr = [];\n"
        + "arr[0] = 'str';",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @type {Array<number>} */ var arr = [];\n"
        + "(function (/** Array<string> */ x){})(arr);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function /** string */ f(/** !Array<number> */ arr) {\n"
        + "  return arr[0];\n"
        + "}",
        NewTypeInference.RETURN_NONDECLARED_TYPE);

    // TODO(blickly): Would be nice if we caught the MISTYPED_ASSIGN_RHS here
    checkNoWarnings("var arr = [];\n"
        + "arr[0] = 5;\n"
        + "var /** Array<string> */ as = arr;");
  }

  public void testInferConstTypeFromGenerics() {
    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @return {T}\n"
        + " */\n"
        + "function f(x) { return x; }\n"
        + "/** @const */ var x = f(5);\n"
        + "function g() { var /** null */ n = x; }",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @constructor\n"
        + " */\n"
        + "function Foo(x) {}\n"
        + "/** @const */ var foo_str = new Foo('str');\n"
        + "function g() { var /** !Foo<number> */ foo_num = foo_str; }",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @return {T}\n"
        + " */\n"
        + "function f(x) { return x; }\n"
        + "/** @const */ var x = f(f ? 'str' : 5);",
        GlobalTypeInfo.COULD_NOT_INFER_CONST_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {T} y\n"
        + " * @return {T}\n"
        + " */\n"
        + "function f(x, y) { return true ? y : x; }\n"
        + "/** @const */ var x = f(5, 'str');"
        + "function g() { var /** null */ n = x; }",
        ImmutableList.of(
            GlobalTypeInfo.COULD_NOT_INFER_CONST_TYPE,
            NewTypeInference.NOT_UNIQUE_INSTANTIATION));

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @return {T}\n"
        + " */\n"
        + "function f(x) { return x; }\n"
        + "/** @const */\n"
        + "var y = f(1, 2);",
        ImmutableList.of(
            GlobalTypeInfo.COULD_NOT_INFER_CONST_TYPE,
            TypeCheck.WRONG_ARGUMENT_COUNT));

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @return {T}\n"
        + " */\n"
        + "function f(x) { return x; }\n"
        + "/** @const */\n"
        + "var y = f();",
        ImmutableList.of(
            GlobalTypeInfo.COULD_NOT_INFER_CONST_TYPE,
            TypeCheck.WRONG_ARGUMENT_COUNT));
  }

  public void testDifficultClassGenericsInstantiation() {
    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @constructor\n"
        + " * @param {T} x\n"
        + " */\n"
        + "function Foo(x) {}\n"
        + "/** @param {Bar<T>} x */\n"
        + "Foo.prototype.method = function(x) {};\n"
        + "/**\n"
        + " * @template T\n"
        + " * @constructor\n"
        + " * @param {T} x\n"
        + " */\n"
        + "function Bar(x) {}\n"
        + "/** @param {Foo<T>} x */\n"
        + "Bar.prototype.method = function(x) {};\n"
        + "(new Foo(123)).method(new Bar('asdf'));",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @constructor\n"
        + " * @param {T} x\n"
        + " */\n"
        + "function Foo(x) {}\n"
        + "/** @param {Foo<Foo<T>>} x */\n"
        + "Foo.prototype.method = function(x) {};\n"
        + "(new Foo(123)).method(new Foo(new Foo('asdf')));",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @interface\n @template T */function A() {};"
        + "/** @return {T} */A.prototype.foo = function() {};"
        + "/** @interface\n @template U\n @extends {A<U>} */function B() {};"
        + "/** @constructor\n @implements {B<string>} */function C() {};"
        + "/** @return {string}\n @override */\n"
        + "C.prototype.foo = function() { return 123; };",
        NewTypeInference.RETURN_NONDECLARED_TYPE);

    // Polymorphic method on a generic class.
    typeCheck(
        "/**\n"
        + " * @constructor\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " */\n"
        + "function Foo(x) {}\n"
        + "/**\n"
        + " * @template U\n"
        + " * @param {U} x\n"
        + " * @return {U}\n"
        + " */\n"
        + "Foo.prototype.method = function(x) { return x; };\n"
        + "(new Foo(123)).method('asdf') - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    // typeCheck(
    //     "/**\n" +
    //     " * @template T\n" +
    //     " * @constructor\n" +
    //     " */\n" +
    //     "function Foo() {}\n" +
    //     "/** @param {T} x */\n" +
    //     "Foo.prototype.method = function(x) {};\n" +
    //     "\n" +
    //     "/**\n" +
    //     " * @template T\n" +
    //     " * @constructor\n" +
    //     " * @extends {Foo<T>}\n" +
    //     " * @param {T} x\n" +
    //     " */\n" +
    //     "function Bar(x) {}\n" +
    //     // Invalid instantiation here, must be T, o/w bugs like the call to f
    //     "/** @param {number} x */\n" +
    //     "Bar.prototype.method = function(x) {};\n" +
    //     "\n" +
    //     "/** @param {!Foo<string>} x */\n" +
    //     "function f(x) { x.method('sadf'); };\n" +
    //     "f(new Bar('asdf'));",
    //     NewTypeInference.FAILED_TO_UNIFY);
  }

  public void testNominalTypeUnification() {
    typeCheck(
        "/**\n"
        + " * @constructor\n"
        + " * @template T, U\n"
        + " * @param {T} x\n"
        + " */\n"
        + "function Foo(x) {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {!Foo<T>} x\n"
        + " */\n"
        + "function fn(x) {}\n"
        + "fn(new Foo('asdf'));",
        // {!Foo<T>} is instantiating only the 1st template var of Foo
        JSTypeCreatorFromJSDoc.INVALID_GENERICS_INSTANTIATION);

    typeCheck(
        "/**\n"
        + " * @constructor\n"
        + " * @template S, T\n"
        + " * @param {S} x\n"
        + " */\n"
        + "function Foo(x) {\n"
        + "  /** @type {S} */ this.prop = x;\n"
        + "}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {!Foo<T>} x\n"
        + " * @return {T}\n"
        + " */\n"
        + "function fn(x) { return x.prop; }\n"
        + "fn(new Foo('asdf')) - 5;",
        ImmutableList.of(
            // {!Foo<T>} is instantiating only the 1st template var of Foo
            JSTypeCreatorFromJSDoc.INVALID_GENERICS_INSTANTIATION,
            NewTypeInference.INVALID_OPERAND_TYPE));
  }

  public void testCasts() {
    typeCheck("(/** @type {number} */ ('asdf'));", TypeValidator.INVALID_CAST);

    checkNoWarnings("function f(/** (number|string) */ x) {\n"
        + "  var y = /** @type {number} */ (x);\n"
        + "}");

    checkNoWarnings("(/** @type {(number|string)} */ (1));");

    checkNoWarnings("(/** @type {number} */ (/** @type {?} */ ('asdf')))");
  }

  public void testOverride() {
    typeCheck(
        "/** @interface */\n"
        + "function Intf() {}\n"
        + "/** @param {(number|string)} x */\n"
        + "Intf.prototype.method = function(x) {};\n"
        + "/**\n"
        + " * @constructor\n"
        + " * @implements {Intf}\n"
        + " */\n"
        + "function C() {}\n"
        + "/** @override */\n"
        + "C.prototype.method = function (x) { x - 1; };\n"
        + "(new C).method('asdf');",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @interface */\n"
        + "function Intf() {}\n"
        + "/** @param {(number|string)} x */\n"
        + "Intf.prototype.method = function(x) {};\n"
        + "/**\n"
        + " * @constructor\n"
        + " * @implements {Intf}\n"
        + " */\n"
        + "function C() {}\n"
        + "/** @inheritDoc */\n"
        + "C.prototype.method = function (x) { x - 1; };\n"
        + "(new C).method('asdf');",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @override */\n"
        + "Foo.prototype.method = function() {};",
        TypeCheck.UNKNOWN_OVERRIDE);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @inheritDoc */\n"
        + "Foo.prototype.method = function() {};",
        TypeCheck.UNKNOWN_OVERRIDE);
  }

  public void testOverrideNoInitializer() {
    typeCheck(
        "/** @interface */ function Intf() {}\n"
        + "/** @param {number} x */\n"
        + "Intf.prototype.method = function(x) {};\n"
        + "/** @interface @extends {Intf} */\n"
        + "function Subintf() {}\n"
        + "/** @override */\n"
        + "Subintf.prototype.method;\n"
        + "function f(/** !Subintf */ x) { x.method('asdf'); }",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @interface */ function Intf() {}\n"
        + "/** @param {number} x */\n"
        + "Intf.prototype.method = function(x) {};\n"
        + "/** @interface @extends {Intf} */\n"
        + "function Subintf() {}\n"
        + "Subintf.prototype.method;\n"
        + "function f(/** !Subintf */ x) { x.method('asdf'); }",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @interface */ function Intf() {}\n"
        + "/** @param {number} x */\n"
        + "Intf.prototype.method = function(x) {};\n"
        + "/** @constructor  @implements {Intf} */\n"
        + "function C() {}\n"
        + "/** @override */\n"
        + "C.prototype.method = (function(){ return function(x){}; })();\n"
        + "(new C).method('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @interface */ function Intf() {}\n"
        + "/** @param {number} x */\n"
        + "Intf.prototype.method = function(x) {};\n"
        + "/** @constructor  @implements {Intf} */\n"
        + "function C() {}\n"
        + "C.prototype.method = (function(){ return function(x){}; })();\n"
        + "(new C).method('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @interface */ function Intf() {}\n"
        + "/** @type {string} */\n"
        + "Intf.prototype.s = 'str';\n"
        + "/** @constructor @implements {Intf} */\n"
        + "function C() {}\n"
        + "/** @override */\n"
        + "C.prototype.s = 'str2';\n"
        + "(new C).s - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @interface */ function Intf() {}\n"
        + "/** @type {string} */\n"
        + "Intf.prototype.s = 'str';\n"
        + "/** @constructor @implements {Intf} */\n"
        + "function C() {}\n"
        + "/** @type {number} @override */\n"
        + "C.prototype.s = 72;",
        GlobalTypeInfo.INVALID_PROP_OVERRIDE);

    typeCheck(
        "/** @interface */ function Intf() {}\n"
        + "/** @type {string} */\n"
        + "Intf.prototype.s = 'str';\n"
        + "/** @constructor @implements {Intf} */\n"
        + "function C() {}\n"
        + "/** @override */\n"
        + "C.prototype.s = 72;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testFunctionConstructor() {
    checkNoWarnings("/** @type {Function} */ function topFun() {}\n"
        + "topFun(1);");

    checkNoWarnings(
        "/** @type {Function} */ function topFun(x) { return x - 5; }");

    checkNoWarnings("function f(/** Function */ fun) {}\n"
        + "f(function g(x) { return x - 5; });");

    checkNoWarnings(
        "function f(/** !Function */ fun) { return new fun(1, 2); }");

    checkNoWarnings("function f(/** !Function */ fun) { [] instanceof fun; }");
  }

  public void testConditionalExBranch() {
    checkNoWarnings("function g() { throw 1; }\n"
        + "function f() {\n"
        + "  try {\n"
        + "    if (g()) {}\n"
        + "  } catch (e) {}\n"
        + "};");
  }

  public void testGenericInterfaceDoesntCrash() {
    checkNoWarnings("/** @const */ var ns = {};\n"
        + "/** @interface @template T */\n"
        + "ns.Interface = function(){}");
  }

  public void testGetpropOnTopDoesntCrash() {
    typeCheck(
        "/** @constructor */ function Foo() {};\n"
        + "/** @type {*} */ Foo.prototype.stuff;\n"
        + "function f(/** !Foo */ foo, x) {\n"
        + "  (foo.stuff.prop = x) || false;\n"
        + "};",
        NewTypeInference.PROPERTY_ACCESS_ON_NONOBJECT);

    typeCheck(
        "/** @constructor */ function Foo() {};\n"
        + "/** @type {*} */ Foo.prototype.stuff;\n"
        + "function f(/** Foo */ foo) {\n"
        + "  foo.stuff.prop || false;\n"
        + "};",
        NewTypeInference.NULLABLE_DEREFERENCE);
  }

  public void testImplementsGenericInterfaceDoesntCrash() {
    checkNoWarnings("/** @interface @template Z */\n"
        + "function Foo(){}\n"
        + "Foo.prototype.getCount = function /** number */ (){};\n"
        + "/**\n"
        + " * @constructor @implements {Foo<T>}\n"
        + " * @template T\n"
        + " */\n"
        + "function Bar(){}\n"
        + "Bar.prototype.getCount = function /** number */ (){};");
  }

  public void testDeadCodeDoesntCrash() {
    checkNoWarnings("function f() {\n"
        + "   throw 'Error';\n"
        + "   return 5;\n"
        + "}");
  }

  public void testSpecializeFunctionToNominalDoesntCrash() {
    typeCheck(
        "/** @interface */ function Foo() {}\n"
        + "function reqFoo(/** Foo */ foo) {};\n"
        + "/** @param {Function} fun */\n"
        + "function f(fun) {\n"
        + "    reqFoo(fun);\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @constructor */ function Foo(){}\n"
        + "function f(x) {\n"
        + "  if (typeof x == 'function') {\n"
        + "    var /** !Foo */ y = x;\n"
        + "  }\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testPrototypeMethodOnUndeclaredDoesntCrash() {
    typeCheck(
        "Foo.prototype.method = function(){ this.x = 5; };",
        ImmutableList.of(
            // VarCheck.UNDEFINED_VAR_ERROR,
            CheckGlobalThis.GLOBAL_THIS));
  }

  public void testFunctionGetpropDoesntCrash() {
    typeCheck(
        "function g() {}\n"
        + "function f() {\n"
        + "  g();\n"
        + "  return g.prop;\n"
        + "}",
        TypeCheck.INEXISTENT_PROPERTY);
  }

  public void testUnannotatedBracketAccessDoesntCrash() {
    checkNoWarnings("function f(foo, i, j) {\n"
        + "  foo.array[i][j] = 5;\n"
        + "}");
  }

  public void testUnknownTypeReferenceDoesntCrash() {
    typeCheck(
        "/** @interface */ function I(){}\n"
        + "/** @type {function(NonExistentClass)} */\n"
        + "I.prototype.method;",
        GlobalTypeInfo.UNRECOGNIZED_TYPE_NAME);
  }

  // TODO(blickly): This warning is not very good.
  public void testBottomPropAccessDoesntCrash() {
    typeCheck(
        "var obj = null;\n"
        + "if (obj) obj.prop += 7;",
        TypeCheck.INEXISTENT_PROPERTY);
  }

  public void testUnannotatedFunctionSummaryDoesntCrash() {
    checkNoWarnings(
        DEFAULT_EXTERNS + "/** @interface */\n"
        + "var IThenable = function() {};\n"
        + "IThenable.prototype.then = function(onFulfilled) {};\n"
        + "/** @constructor @implements {IThenable} */\n"
        + "var Promise = function() {};\n"
        + "/**\n"
        + " * @param {function():RESULT} onFulfilled\n"
        + " * @template RESULT\n"
        + " */\n"
        + "Promise.prototype.then = function(onFulfilled) {};",
        "var /** !Promise */ p;\n"
        + "function f(unused) {\n"
        + "  function g(){ return 5; }\n"
        + "  p.then(g);\n"
        + "}");

    typeCheck(
        DEFAULT_EXTERNS + "/** @interface */\n"
        + "var IThenable = function() {};\n"
        + "IThenable.prototype.then = function(onFulfilled) {};\n"
        + "/** @constructor @implements {IThenable} */\n"
        + "var Promise = function() {};\n"
        + "/**\n"
        + " * @param {function():RESULT} onFulfilled\n"
        + " * @return {RESULT}\n"
        + " * @template RESULT\n"
        + " */\n"
        + "Promise.prototype.then = function(onFulfilled) {};",
        "var /** !Promise */ p;\n"
        + "function f(unused) {\n"
        + "  function g(){ return 5; }\n"
        + "  var /** null */ n = p.then(g);\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testSpecializeLooseNullDoesntCrash() {
    checkNoWarnings("/** @constructor */ function Foo(){}\n"
        + "function reqFoo(/** Foo */ x) {}\n"
        + "function f(x) {\n"
        + "   x = null;\n"
        + "   reqFoo(x);\n"
        + "}");
  }

  public void testOuterVarDefinitionJoinDoesntCrash() {
    checkNoWarnings("/** @constructor */ function Foo(){}\n"
        + "function f() {\n"
        + "  if (true) {\n"
        + "    function g() { new Foo; }\n"
        + "    g();\n"
        + "  }\n"
        + "}");

    // typeCheck(
    //     "function f() {\n" +
    //     "  if (true) {\n" +
    //     "    function g() { new Foo; }\n" +
    //     "    g();\n" +
    //     "  }\n" +
    //     "}",
    //     VarCheck.UNDEFINED_VAR_ERROR);
  }

  public void testUnparameterizedArrayDefinitionDoesntCrash() {
    checkNoWarnings(
        "/** @constructor */ function Function(){}\n"
        + "/** @constructor */ function Array(){}",
        "function f(/** !Array */ arr) {\n"
        + "  var newarr = [];\n"
        + "  newarr[0] = arr[0];\n"
        + "  newarr[0].prop1 = newarr[0].prop2;\n"
        + "};");
  }

  public void testInstanceofGenericTypeDoesntCrash() {
    checkNoWarnings("/** @constructor @template T */ function Foo(){}\n"
        + "function f(/** !Foo<?> */ f) {\n"
        + "  if (f instanceof Foo) return true;\n"
        + "};");
  }

  public void testRedeclarationOfFunctionAsNamespaceDoesntCrash() {
    typeCheck(
        "/** @const */ var ns = ns || {};\n"
        + "ns.fun = function(name) {};\n"
        + "ns.fun = ns.fun || {};\n"
        + "ns.fun.get = function(/** string */ name) {};",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @const */ var ns = ns || {};\n"
        + "ns.fun = function(name) {};\n"
        + "ns.fun.get = function(/** string */ name) {};\n"
        + "ns.fun = ns.fun || {};",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testInvalidEnumDoesntCrash() {
    typeCheck(
        "/** @enum {Array<number>} */\n"
        + "var FooEnum = {\n"
        + "  BAR: [5]\n"
        + "};\n"
        + "/** @param {FooEnum} x */\n"
        + "function f(x) {\n"
        + "    var y = x[0];\n"
        + "};",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "var ns = {};\n"
        + "function f() {\n"
        + "  /** @enum {number} */ var EnumType = ns;\n"
        + "}",
        GlobalTypeInfo.MALFORMED_ENUM);
  }

  public void testRemoveNonexistentPropDoesntCrash() {
    // TODO(blickly): Would be nice not to warn here,
    // even if it means missing the warning below
    typeCheck(
        "/** @constructor */ function Foo() {\n"
        + " /** @type {!Object} */ this.obj = {arr : []}\n"
        + "}\n"
        + "Foo.prototype.bar = function() {\n"
        + " this.obj.arr.length = 0;\n"
        + "}",
        TypeCheck.INEXISTENT_PROPERTY);

    typeCheck(
        "/** @constructor */ function Foo() {\n"
        + " /** @type {!Object} */ this.obj = {}\n"
        + "}\n"
        + "Foo.prototype.bar = function() {\n"
        + " this.obj.prop1.prop2 = 0;\n"
        + "}",
        TypeCheck.INEXISTENT_PROPERTY);
  }

  public void testDoublyAssignedPrototypeMethodDoesntCrash() {
    typeCheck(
        "/** @constructor */ function Foo(){}\n"
        + "Foo.prototype.method = function(){};\n"
        + "var f = function() {\n"
        + "   Foo.prototype.method = function(){};\n"
        + "}",
        GlobalTypeInfo.CTOR_IN_DIFFERENT_SCOPE);
  }

  public void testTopFunctionAsArgumentDoesntCrash() {
    checkNoWarnings("function f(x) {}\n"
        + "function g(value) {\n"
        + "  if (typeof value == 'function') {\n"
        + "    f(value);\n"
        + "  }\n"
        + "}");
  }

  public void testGetpropDoesntCrash() {
    checkNoWarnings("/** @constructor */ function Obj(){}\n"
        + "/** @constructor */ var Foo = function() {\n"
        + "    /** @private {Obj} */ this.obj;\n"
        + "};\n"
        + "Foo.prototype.update = function() {\n"
        + "    if (!this.obj) {}\n"
        + "};");

    typeCheck(
        "/** @constructor */ function Obj(){}\n"
        + "/** @constructor */ var Foo = function() {\n"
        + "    /** @private {Obj} */ this.obj;\n"
        + "};\n"
        + "Foo.prototype.update = function() {\n"
        + "    if (!this.obj.size) {}\n"
        + "};",
        NewTypeInference.NULLABLE_DEREFERENCE);
  }

  public void testLooseFunctionSubtypeDoesntCrash() {
    checkNoWarnings("/** @constructor */\n"
        + "var Foo = function() {};\n"
        + "/** @param {function(!Foo)} fooFun */\n"
        + "var reqFooFun = function(fooFun) {};\n"
        + "/** @type {function(!Foo)} */\n"
        + "var declaredFooFun;\n"
        + "function f(opt_fooFun) {\n"
        + "  reqFooFun(opt_fooFun);\n"
        + "  var fooFun = opt_fooFun || declaredFooFun;\n"
        + "  reqFooFun(fooFun);\n"
        + "};");

    checkNoWarnings("var /** @type {function(number)} */ f;\n"
        + "f = (function(x) {\n"
        + "  x(1, 2);\n"
        + "  return x;\n"
        + "})(function(x, y) {});");
  }

  public void testMeetOfLooseObjAndNamedDoesntCrash() {
    checkNoWarnings("/** @constructor */ function Foo(){ this.prop = 5; }\n"
        + "/** @constructor */ function Bar(){}\n"
        + "/** @param {function(!Foo)} func */\n"
        + "Bar.prototype.forEach = function(func) {\n"
        + "  this.forEach(function(looseObj) { looseObj.prop; });\n"
        + "};");
  }

  // public void testAccessVarargsDoesntCrash() {
  //   // TODO(blickly): Support arguments so we only get one warning
  //   typeCheck(
  //       "/** @param {...} var_args */\n" +
  //       "function f(var_args) { return true ? var_args : arguments; }",
  //       ImmutableList.of(
  //           VarCheck.UNDEFINED_VAR_ERROR,
  //           VarCheck.UNDEFINED_VAR_ERROR));
  // }

  public void testUninhabitableObjectTypeDoesntCrash() {
    checkNoWarnings("function f(/** number */ n) {\n"
        + "  if (typeof n == 'string') {\n"
        + "    return { 'First': n, 'Second': 5 };\n"
        + "  }\n"
        + "};");
  }

  public void testMockedOutConstructorDoesntCrash() {
    typeCheck(
        "/** @constructor */ function Foo(){}\n"
        + "/** @constructor */ Foo = function(){};",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testNamespacePropWithNoTypeDoesntCrash() {
    checkNoWarnings("/** @const */ var ns = {};\n"
        + "/** @public */ ns.prop;");
  }

  public void testArrayLiteralUsedGenericallyDoesntCrash() {
    checkNoWarnings("/**\n"
        + " * @template T\n"
        + " * @param {!Array<T>} arr\n"
        + " * @return {T}\n"
        + " */\n"
        + "function f(arr) { return arr[0]; }\n"
        + "f([1,2,3]);");
  }

  public void testSpecializeLooseFunctionDoesntCrash() {
    checkNoWarnings(CLOSURE_BASE + "function f(/** !Function */ func) {};\n"
        + "function g(obj) {\n"
        + "    if (goog.isFunction(obj)) {\n"
        + "      f(obj);\n"
        + "    }\n"
        + "};");

    checkNoWarnings("function f(/** !Function */ func) {};\n"
        + "function g(obj) {\n"
        + "    if (typeof obj === 'function') {\n"
        + "      f(obj);\n"
        + "    }\n"
        + "};");

    checkNoWarnings("/** @param {function(T)} fn @param {T} x @template T */\n"
        + "function reqGenFun(fn, x) {};\n"
        + "function g(obj, str) {\n"
        + "  var member = obj[str];\n"
        + "  if (typeof member === 'function') {\n"
        + "    reqGenFun(member, str);\n"
        + "  }\n"
        + "};");
  }

  public void testGetpropOnPossiblyInexistentPropertyDoesntCrash() {
    typeCheck(
        "/** @constructor */ function Foo(){};\n"
        + "function f() {\n"
        + "  var obj = 3 ? new Foo : { prop : { subprop : 'str'}};\n"
        + "  obj.prop.subprop = 'str';\n"
        + "};",
        NewTypeInference.POSSIBLY_INEXISTENT_PROPERTY);
  }

  public void testCtorManipulationDoesntCrash() {
    checkNoWarnings("/** @constructor */ var X = function() {};\n"
        + "var f = function(ctor) {\n"
        + "  /** @type {function(new: X)} */\n"
        + "  function InstantiableCtor() {};\n"
        + "  InstantiableCtor.prototype = ctor.prototype;\n"
        + "}");
  }

  public void testAbstractMethodOverrides() {
    checkNoWarnings("/** @type {!Function} */ function abstractMethod(){}\n"
        + "/** @interface */ function I() {}\n"
        + "/** @param {string} opt_str */\n"
        + "I.prototype.done = abstractMethod;\n"
        + "/** @implements {I} @constructor */ function Foo() {}\n"
        + "/** @override */ Foo.prototype.done = function(opt_str) {}\n"
        + "/** @param {I} stats */ function f(stats) {}\n"
        + "function g() {\n"
        + "  var x = new Foo();\n"
        + "  f(x);\n"
        + "  x.done();\n"
        + "}");
  }

  public void testThisReferenceUsedGenerically() {
    checkNoWarnings("/** @constructor @template T */\n"
        + "var Foo = function(t) {\n"
        + "  /** @type {Foo<T>} */\n"
        + "  this.parent_ = null;\n"
        + "}\n"
        + "Foo.prototype.method = function() {\n"
        + "  var p = this;\n"
        + "  while (p != null) p = p.parent_;\n"
        + "}");

    checkNoWarnings("/** @constructor @template T */\n"
        + "var Foo = function(t) {\n"
        + "  /** @type {Foo<T>} */\n"
        + "  var p = this;\n"
        + "}");
  }

  public void testGrandparentTemplatizedDoesntCrash() {
    checkNoWarnings("/** @constructor @template VALUE */\n"
        + "var Grandparent = function() {};\n"
        + "/** @constructor @extends {Grandparent<number>} */\n"
        + "var Parent = function(){};\n"
        + "/** @constructor @extends {Parent} */ function Child(){}");

    typeCheck(
        "/** @constructor @template VALUE */\n"
        + "var Grandparent = function() {};\n"
        + "/** @constructor @extends {Grandparent} */\n"
        + "var Parent = function(){};\n"
        + "/** @constructor @extends {Parent} */ function Child(){}",
        JSTypeCreatorFromJSDoc.INVALID_GENERICS_INSTANTIATION);
  }

  public void testDebuggerStatementDoesntCrash() {
    checkNoWarnings("debugger;");
  }

  public void testDeclaredMethodWithoutScope() {
    checkNoWarnings("/** @interface */ function Foo(){}\n"
        + "/** @type {function(number)} */ Foo.prototype.bar;\n"
        + "/** @constructor @implements {Foo} */ function Bar(){}\n"
        + "Bar.prototype.bar = function(x){}");

    checkNoWarnings("/** @type {!Function} */\n"
        + "var g = function() { throw 0; };\n"
        + "/** @constructor */ function Foo(){}\n"
        + "/** @type {function(number)} */ Foo.prototype.bar = g;\n"
        + "/** @constructor @extends {Foo} */ function Bar(){}\n"
        + "Bar.prototype.bar = function(x){}");

    checkNoWarnings("/** @param {string} s */\n"
        + "var reqString = function(s) {};\n"
        + "/** @constructor */ function Foo(){}\n"
        + "/** @type {function(string)} */ Foo.prototype.bar = reqString;\n"
        + "/** @constructor @extends {Foo} */ function Bar(){}\n"
        + "Bar.prototype.bar = function(x){}");

    typeCheck(
        "/** @param {string} s */\n"
        + "var reqString = function(s) {};\n"
        + "/** @constructor */ function Foo(){}\n"
        + "/** @type {function(number)} */ Foo.prototype.bar = reqString;\n"
        + "/** @constructor @extends {Foo} */ function Bar(){}\n"
        + "Bar.prototype.bar = function(x){}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings("/** @constructor */ function Foo(){}\n"
        + "/** @type {Function} */ Foo.prototype.bar = null;\n"
        + "/** @constructor @extends {Foo} */ function Bar(){}\n"
        + "Bar.prototype.bar = function(){}");

    typeCheck(
        "/** @constructor */ function Foo(){}\n"
        + "/** @type {!Function} */ Foo.prototype.bar = null;\n"
        + "/** @constructor @extends {Foo} */ function Bar(){}\n"
        + "Bar.prototype.bar = function(){}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings("/** @interface */ function I(){}\n"
        + "/** @return {void} */\n"
        + "I.prototype.method;");
  }

  public void testDontOverrideNestedPropWithWorseType() {
    typeCheck(
        "/** @interface */\n"
        + "var Bar = function() {};\n"
        + "/** @type {Function} */\n"
        + "Bar.prototype.method;\n"
        + "/** @interface */\n"
        + "var Baz = function() {};\n"
        + "Baz.prototype.method = function() {};\n"
        + "/** @constructor */\n"
        + "var Foo = function() {};\n"
        + "/** @type {!Bar|!Baz} */\n"
        + "Foo.prototype.obj;\n"
        + "Foo.prototype.set = function() {\n"
        + "    this.obj.method = 5;\n"
        + "};",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "function f(/** { prop: number } */ obj, x) {\n"
        + " x < obj.prop;\n"
        + " obj.prop < 'str';\n"
        + " obj.prop = 123;\n"
        + " x = 123;\n"
        + "}\n"
        + "f({ prop: 123}, 123)",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testPropNamesWithDot() {
    checkNoWarnings("var x = { '.': 1, ';': 2, '/': 3, '{': 4, '}': 5 }");

    checkNoWarnings("function f(/** { foo : { bar : string } } */ x) {\n"
        + "  x['foo.bar'] = 5;\n"
        + "}");

    typeCheck(
        "var x = { '.' : 'str' };\n"
        + "x['.'] - 5",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testObjLitDeclaredProps() {
    typeCheck(
        "({ /** @type {string} */ prop: 123 });",
        NewTypeInference.INVALID_OBJLIT_PROPERTY_TYPE);

    typeCheck(
        "var lit = { /** @type {string} */ prop: 'str' };\n"
        + "lit.prop = 123;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings(
        "var lit = { /** @type {(number|string)} */ prop: 'str' };\n"
        + "var /** string */ s = lit.prop;");
  }

  public void testCallArgumentsChecked() {
    typeCheck(
        "3(1 - 'str');",
        ImmutableList.of(
            TypeCheck.NOT_CALLABLE, NewTypeInference.INVALID_OPERAND_TYPE));
  }

  public void testRecursiveFunctions() {
    typeCheck(
        "function foo(){ foo() - 123; return 'str'; }",
        NewTypeInference.INVALID_INFERRED_RETURN_TYPE);

    typeCheck(
        "/** @return {string} */ function foo(){ foo() - 123; return 'str'; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @return {number} */\n"
        + "var f = function rec() { return rec; };",
        NewTypeInference.RETURN_NONDECLARED_TYPE);
  }

  public void testStructPropAccess() {
    checkNoWarnings(
        "/** @constructor @struct */ function Foo() { this.prop = 123; }\n"
        + "(new Foo).prop;");

    typeCheck(
        "/** @constructor @struct */ function Foo() { this.prop = 123; }\n"
        + "(new Foo)['prop'];",
        TypeValidator.ILLEGAL_PROPERTY_ACCESS);

    typeCheck(
        "/** @interface */ function Foo() {}\n"
        + "/** @type {number} */ Foo.prototype.prop;\n"
        + "function f(/** !Foo */ x) { x['prop']; }",
        TypeValidator.ILLEGAL_PROPERTY_ACCESS);

    typeCheck(
        "/** @constructor @struct */ function Foo() {\n"
        + "  this.prop = 123;\n"
        + "  this['prop'] - 123;\n"
        + "}",
        TypeValidator.ILLEGAL_PROPERTY_ACCESS);

    typeCheck(
        "/** @constructor @struct */ function Foo() { this.prop = 123; }\n"
        + "(new Foo)['prop'] = 123;",
        TypeValidator.ILLEGAL_PROPERTY_ACCESS);

    typeCheck(
        "/** @constructor @struct */ function Foo() { this.prop = 123; }\n"
        + "function f(pname) { (new Foo)[pname] = 123; }",
        TypeValidator.ILLEGAL_PROPERTY_ACCESS);

    typeCheck(
        "/** @constructor @struct */ function Foo() { this.prop = {}; }\n"
        + "(new Foo)['prop'].newprop = 123;",
        TypeValidator.ILLEGAL_PROPERTY_ACCESS);

    typeCheck(
        "/** @constructor @struct */ function Foo() {}\n"
        + "/** @constructor */ function Bar() {}\n"
        + "function f(cond) {\n"
        + "  var x;\n"
        + "  if (cond) {\n"
        + "    x = new Foo;\n"
        + "  }\n"
        + "  else {\n"
        + "    x = new Bar;\n"
        + "  }\n"
        + "  x['prop'] = 123;\n"
        + "}",
        TypeValidator.ILLEGAL_PROPERTY_ACCESS);

    typeCheck("(/** @struct */ { 'prop' : 1 });", TypeCheck.ILLEGAL_OBJLIT_KEY);

    typeCheck(
        "var lit = /** @struct */ { prop : 1 }; lit['prop'];",
        TypeValidator.ILLEGAL_PROPERTY_ACCESS);

    typeCheck(
        "function f(cond) {\n"
        + "  var x;\n"
        + "  if (cond) {\n"
        + "    x = /** @struct */ { a: 1 };\n"
        + "  }\n"
        + "  else {\n"
        + "    x = /** @struct */ { a: 2 };\n"
        + "  }\n"
        + "  x['a'] = 123;\n"
        + "}",
        TypeValidator.ILLEGAL_PROPERTY_ACCESS);

    typeCheck(
        "function f(cond) {\n"
        + "  var x;\n"
        + "  if (cond) {\n"
        + "    x = /** @struct */ { a: 1 };\n"
        + "  }\n"
        + "  else {\n"
        + "    x = {};\n"
        + "  }\n"
        + "  x['random' + 'propname'] = 123;\n"
        + "}",
        TypeValidator.ILLEGAL_PROPERTY_ACCESS);
  }

  public void testDictPropAccess() {
    checkNoWarnings(
        "/** @constructor @dict */ function Foo() { this['prop'] = 123; }\n"
        + "(new Foo)['prop'];");

    typeCheck(
        "/** @constructor @dict */ function Foo() { this['prop'] = 123; }\n"
        + "(new Foo).prop;",
        TypeValidator.ILLEGAL_PROPERTY_ACCESS);

    typeCheck(
        "/** @constructor @dict */ function Foo() {\n"
        + "  this['prop'] = 123;\n"
        + "  this.prop - 123;\n"
        + "}",
        TypeValidator.ILLEGAL_PROPERTY_ACCESS);

    typeCheck(
        "/** @constructor @dict */ function Foo() { this['prop'] = 123; }\n"
        + "(new Foo).prop = 123;",
        TypeValidator.ILLEGAL_PROPERTY_ACCESS);

    typeCheck(
        "/** @constructor @dict */ function Foo() { this['prop'] = {}; }\n"
        + "(new Foo).prop.newprop = 123;",
        TypeValidator.ILLEGAL_PROPERTY_ACCESS);

    typeCheck(
        "/** @constructor @dict */ function Foo() {}\n"
        + "/** @constructor */ function Bar() {}\n"
        + "function f(cond) {\n"
        + "  var x;\n"
        + "  if (cond) {\n"
        + "    x = new Foo;\n"
        + "  }\n"
        + "  else {\n"
        + "    x = new Bar;\n"
        + "  }\n"
        + "  x.prop = 123;\n"
        + "}",
        TypeValidator.ILLEGAL_PROPERTY_ACCESS);

    typeCheck("(/** @dict */ { prop : 1 });", TypeCheck.ILLEGAL_OBJLIT_KEY);

    typeCheck(
        "var lit = /** @dict */ { 'prop' : 1 }; lit.prop;",
        TypeValidator.ILLEGAL_PROPERTY_ACCESS);

    typeCheck(
        "(/** @dict */ {}).toString();", TypeValidator.ILLEGAL_PROPERTY_ACCESS);
  }

  public void testStructWithIn() {
    typeCheck("('prop' in /** @struct */ {});", TypeCheck.IN_USED_WITH_STRUCT);

    typeCheck(
        "for (var x in /** @struct */ {});", TypeCheck.IN_USED_WITH_STRUCT);
  }

  public void testStructDictSubtyping() {
    checkNoWarnings("var lit = { a: 1 }; lit.a - 2; lit['a'] + 5;");

    typeCheck(
        "/** @constructor @struct */ function Foo() {}\n"
        + "/** @constructor @dict */ function Bar() {}\n"
        + "function f(/** Foo */ x) {}\n"
        + "f(/** @dict */ {});",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(/** { a : number } */ x) {}\n"
        + "f(/** @dict */ { 'a' : 5 });",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testInferStructDictFormal() {
    typeCheck(
        "function f(obj) {\n"
        + "  return obj.prop;\n"
        + "}\n"
        + "f(/** @dict */ { 'prop': 123 });",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(obj) {\n"
        + "  return obj['prop'];\n"
        + "}\n"
        + "f(/** @struct */ { prop: 123 });",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("/** @constructor @struct */\n"
        + "function Foo() {}\n"
        + "function f(obj) { obj['prop']; return obj; }\n"
        + "var /** !Foo */ x = f({ prop: 123 });");

    // We infer unrestricted loose obj for mixed . and [] access
    checkNoWarnings("function f(obj) {\n"
        + "  if (obj['p1']) {\n"
        + "    obj.p2.p3 = 123;\n"
        + "  }\n"
        + "}");

    checkNoWarnings("function f(obj) {\n"
        + "  return obj['a'] + obj.b;\n"
        + "}\n"
        + "f({ a: 123, 'b': 234 });");
  }

  public void testStructDictInheritance() {
    checkNoWarnings("/** @constructor @struct */\n"
        + "function Foo() {}\n"
        + "/** @constructor @struct @extends {Foo} */\n"
        + "function Bar() {}");

    checkNoWarnings("/** @constructor @struct */\n"
        + "function Foo() {}\n"
        + "/** @constructor @unrestricted @extends {Foo} */\n"
        + "function Bar() {}");

    checkNoWarnings("/** @constructor @dict */\n"
        + "function Foo() {}\n"
        + "/** @constructor @dict @extends {Foo} */\n"
        + "function Bar() {}");

    typeCheck(
        "/** @constructor @unrestricted */\n"
        + "function Foo() {}\n"
        + "/** @constructor @struct @extends {Foo} */\n"
        + "function Bar() {}",
        TypeCheck.CONFLICTING_SHAPE_TYPE);

    typeCheck(
        "/** @constructor @unrestricted */\n"
        + "function Foo() {}\n"
        + "/** @constructor @dict @extends {Foo} */\n"
        + "function Bar() {}",
        TypeCheck.CONFLICTING_SHAPE_TYPE);

    typeCheck(
        "/** @interface */\n"
        + "function Foo() {}\n"
        + "/** @constructor @dict @implements {Foo} */\n"
        + "function Bar() {}",
        JSTypeCreatorFromJSDoc.DICT_IMPLEMENTS_INTERF);
  }

  public void testStructPropCreation() {
    checkNoWarnings("/** @constructor @struct */\n"
        + "function Foo() { this.prop = 1; }\n"
        + "(new Foo).prop = 2;");

    typeCheck(
        "/** @constructor @struct */\n"
        + "function Foo() {}\n"
        + "Foo.prototype.method = function() { this.prop = 1; };\n"
        + "(new Foo).prop = 2;",
        ImmutableList.of(
            TypeCheck.ILLEGAL_PROPERTY_CREATION,
            TypeCheck.ILLEGAL_PROPERTY_CREATION));

    typeCheck(
        "/** @constructor @struct */\n"
        + "function Foo() {}\n"
        + "(new Foo).prop += 2;",
        TypeCheck.INEXISTENT_PROPERTY);

    typeCheck(
        "/** @constructor @struct */\n"
        + "function Foo() {}\n"
        + "Foo.prototype.method = function() { this.prop = 1; };\n"
        + "(new Foo).prop++;",
        ImmutableList.of(
            TypeCheck.ILLEGAL_PROPERTY_CREATION,
            TypeCheck.INEXISTENT_PROPERTY));

    typeCheck(
        "(/** @struct */ { prop: 1 }).prop2 = 123;",
        TypeCheck.ILLEGAL_PROPERTY_CREATION);

    checkNoWarnings("/** @constructor @struct */\n"
        + "function Foo() {}\n"
        + "/** @constructor @struct @extends {Foo} */\n"
        + "function Bar() {}\n"
        + "Bar.prototype.prop = 123;");

    typeCheck(
        "/** @constructor @struct */\n"
        + "function Foo() {}\n"
        + "/** @constructor @struct @extends {Foo} */\n"
        + "function Bar() {}\n"
        + "Bar.prototype.prop = 123;\n"
        + "(new Foo).prop = 234;",
        TypeCheck.ILLEGAL_PROPERTY_CREATION);

    typeCheck(
        "/**\n"
        + " * @constructor\n"
        + " * @struct\n"
        + " */\n"
        + "function Foo() {\n"
        + "  var t = this;\n"
        + "  t.x = 123;\n"
        + "}",
        TypeCheck.ILLEGAL_PROPERTY_CREATION);

    checkNoWarnings("/**\n"
        + " * @constructor\n"
        + " * @struct\n"
        + " */\n"
        + "function Foo() {}\n"
        + "Foo.someprop = 123;");

    // TODO(dimvar): the current type inf also doesn't catch this.
    // Consider warning when the prop is not an "own" prop.
    checkNoWarnings("/** @constructor @struct */\n"
        + "function Foo() {}\n"
        + "Foo.prototype.bar = 123;\n"
        + "(new Foo).bar = 123;");

    checkNoWarnings("function f(obj) { obj.prop = 123; }\n"
        + "f(/** @struct */ {});");

    typeCheck(
        "/** @constructor @struct */\n"
        + "function Foo() {}\n"
        + "function f(obj) { obj.prop - 5; return obj; }\n"
        + "var s = (1 < 2) ? new Foo : f({ prop: 123 });\n"
        + "s.newprop = 123;",
        TypeCheck.ILLEGAL_PROPERTY_CREATION);
  }

  public void testMisplacedStructDictAnnotation() {
    typeCheck(
        "/** @struct */ function Struct1() {}",
        GlobalTypeInfo.STRUCTDICT_WITHOUT_CTOR);
    typeCheck(
        "/** @dict */ function Dict() {}",
        GlobalTypeInfo.STRUCTDICT_WITHOUT_CTOR);
  }

  // public void testGlobalVariableInJoin() {
  //   typeCheck(
  //       "function f() { true ? globalVariable : 123; }",
  //       VarCheck.UNDEFINED_VAR_ERROR);
  // }

  // public void testGlobalVariableInAssign() {
  //   typeCheck(
  //       "u.prop = 123;",
  //       VarCheck.UNDEFINED_VAR_ERROR);
  // }

  public void testGetters() {
    typeCheck(
        "var x = { /** @return {string} */ get a() { return 1; } };",
        NewTypeInference.RETURN_NONDECLARED_TYPE);

    typeCheck(
        "var x = { /** @param {number} n */ get a() {} };",
        GlobalTypeInfo.INEXISTENT_PARAM);

    typeCheck(
        "var x = { /** @type {string} */ get a() {} };",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "var x = {\n"
        + "  /**\n"
        + "   * @return {T|number} b\n"
        + "   * @template T\n"
        + "   */\n"
        + "  get a() {}\n"
        + "};",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "var x = /** @dict */ { get a() {} };", TypeCheck.ILLEGAL_OBJLIT_KEY);

    typeCheck(
        "var x = /** @struct */ { get 'a'() {} };",
        TypeCheck.ILLEGAL_OBJLIT_KEY);

    typeCheck(
        "var x = { get a() { 1 - 'asdf'; } };",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "var x = { get a() { return 1; } };\n"
        + "x.a < 'str';",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "var x = { get a() { return 1; } };\n"
        + "x.a();",
        TypeCheck.NOT_CALLABLE);

    typeCheck(
        "var x = { get 'a'() { return 1; } };\n"
        + "x['a']();",
        TypeCheck.NOT_CALLABLE);

    checkNoWarnings( // assigning to a getter doesn't remove it
        "var x = { get a() { return 1; } };\n"
        + "x.a = 'str';\n"
        + "x.a - 1;");

    typeCheck(
        "var x = /** @struct */ { get a() {} }; x.a = 123;",
        TypeCheck.ILLEGAL_PROPERTY_CREATION);
  }

  public void testSetters() {
    typeCheck(
        "var x = { /** @return {string} */ set a(b) {} };",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "var x = { /** @type{function(number):number} */ set a(b) {} };",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "var x = { set /** string */ a(b) {} };",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "var x = {\n"
        + "  /**\n"
        + "   * @param {T|number} b\n"
        + "   * @template T\n"
        + "   */\n"
        + "  set a(b) {}\n"
        + "};",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "var x = { set a(b) { return 1; } };",
        NewTypeInference.RETURN_NONDECLARED_TYPE);

    typeCheck(
        "var x = { /** @type {string} */ set a(b) {} };",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "var x = /** @dict */ { set a(b) {} };", TypeCheck.ILLEGAL_OBJLIT_KEY);

    typeCheck(
        "var x = /** @struct */ { set 'a'(b) {} };",
        TypeCheck.ILLEGAL_OBJLIT_KEY);

    typeCheck(
        "var x = { set a(b) { 1 - 'asdf'; } };",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "var x = { set a(b) {}, prop: 123 }; var y = x.a;",
        TypeCheck.INEXISTENT_PROPERTY);

    typeCheck(
        "var x = { /** @param {string} b */ set a(b) {} };\n"
        + "x.a = 123;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "var x = { set a(b) { b - 5; } };\n"
        + "x.a = 'str';",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "var x = { set 'a'(b) { b - 5; } };\n"
        + "x['a'] = 'str';",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testConstMissingInitializer() {
    typeCheck("/** @const */ var x;", GlobalTypeInfo.CONST_WITHOUT_INITIALIZER);

    typeCheck("/** @final */ var x;", GlobalTypeInfo.CONST_WITHOUT_INITIALIZER);

    checkNoWarnings("/** @const {number} */ var x;", "");

    // TODO(dimvar): must fix externs initialization
    // checkNoWarnings("/** @const {number} */ var x;", "x - 5;");

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @const */\n"
        + "Foo.prop;",
        GlobalTypeInfo.CONST_WITHOUT_INITIALIZER);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {\n"
        + "  /** @const */ this.prop;\n"
        + "}",
        GlobalTypeInfo.CONST_WITHOUT_INITIALIZER);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @const */\n"
        + "Foo.prototype.prop;",
        GlobalTypeInfo.CONST_WITHOUT_INITIALIZER);

    typeCheck(
        "/** @const */\n"
        + "var ns = {};\n"
        + "/** @const */\n"
        + "ns.prop;",
        GlobalTypeInfo.CONST_WITHOUT_INITIALIZER);
  }

  public void testMisplacedConstPropertyAnnotation() {
    typeCheck(
        "function f(obj) { /** @const */ obj.prop = 123; }",
        GlobalTypeInfo.MISPLACED_CONST_ANNOTATION);

    typeCheck(
        "function f(obj) { /** @const */ obj.prop; }",
        GlobalTypeInfo.MISPLACED_CONST_ANNOTATION);

    typeCheck(
        "var obj = { /** @const */ prop: 1 };",
        GlobalTypeInfo.MISPLACED_CONST_ANNOTATION);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.method = function() {\n"
        + "  /** @const */ this.prop = 1;\n"
        + "}",
        GlobalTypeInfo.MISPLACED_CONST_ANNOTATION);

    // A final constructor isn't the same as a @const property
    checkNoWarnings("/** @constructor */\n"
        + "function Foo() {\n"
        + "  /**\n"
        + "   * @constructor\n"
        + "   * @final\n"
        + "   */\n"
        + "  this.Bar = function() {};\n"
        + "}");
  }

  public void testConstVarsDontReassign() {
    typeCheck(
        "/** @const */ var x = 1; x = 2;", NewTypeInference.CONST_REASSIGNED);

    typeCheck(
        "/** @const */ var x = 1; x += 2;", NewTypeInference.CONST_REASSIGNED);

    typeCheck(
        "/** @const */ var x = 1; x -= 2;", NewTypeInference.CONST_REASSIGNED);

    typeCheck(
        "/** @const */ var x = 1; x++;", NewTypeInference.CONST_REASSIGNED);

    typeCheck(
        "/** @const */ var x = 1;\n"
        + "function f() { x = 2; }",
        NewTypeInference.CONST_REASSIGNED);

    typeCheck("/** @const {number} */ var x;", "x = 2;",
        NewTypeInference.CONST_REASSIGNED);
  }

  public void testConstPropertiesDontReassign() {
    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {\n"
        + "  /** @const */ this.prop = 1;\n"
        + "}\n"
        + "var obj = new Foo;\n"
        + "obj.prop = 2;",
        NewTypeInference.CONST_REASSIGNED);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {\n"
        + "  /** @const {number} */\n"
        + "  this.prop = 1;\n"
        + "}\n"
        + "var obj = new Foo;\n"
        + "obj.prop = 2;",
        NewTypeInference.CONST_REASSIGNED);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {\n"
        + "  /** @const */ this.prop = 1;\n"
        + "}\n"
        + "var obj = new Foo;\n"
        + "obj.prop += 2;",
        NewTypeInference.CONST_REASSIGNED);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {\n"
        + "  /** @const */ this.prop = 1;\n"
        + "}\n"
        + "var obj = new Foo;\n"
        + "obj.prop++;",
        NewTypeInference.CONST_REASSIGNED);

    typeCheck(
        "/** @const */\n"
        + "var ns = {};\n"
        + "/** @const */\n"
        + "ns.prop = 1;\n"
        + "ns.prop = 2;",
        NewTypeInference.CONST_REASSIGNED);

    typeCheck(
        "/** @const */\n"
        + "var ns = {};\n"
        + "/** @const */\n"
        + "ns.prop = 1;\n"
        + "function f() { ns.prop = 2; }",
        NewTypeInference.CONST_REASSIGNED);

    typeCheck(
        "/** @const */\n"
        + "var ns = {};\n"
        + "/** @const {number} */\n"
        + "ns.prop = 1;\n"
        + "ns.prop = 2;",
        NewTypeInference.CONST_REASSIGNED);

    typeCheck(
        "/** @const */\n"
        + "var ns = {};\n"
        + "/** @const */\n"
        + "ns.prop = 1;\n"
        + "ns.prop++;",
        NewTypeInference.CONST_REASSIGNED);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @const */ Foo.prop = 1;\n"
        + "Foo.prop = 2;",
        NewTypeInference.CONST_REASSIGNED);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @const {number} */ Foo.prop = 1;\n"
        + "Foo.prop++;",
        NewTypeInference.CONST_REASSIGNED);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @const */ Foo.prototype.prop = 1;\n"
        + "Foo.prototype.prop = 2;",
        NewTypeInference.CONST_REASSIGNED);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @const */ Foo.prototype.prop = 1;\n"
        + "var protoAlias = Foo.prototype;\n"
        + "protoAlias.prop = 2;",
        NewTypeInference.CONST_REASSIGNED);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() { /** @const */ this.X = 4; }\n"
        + "/** @constructor */\n"
        + "function Bar() { /** @const */ this.X = 5; }\n"
        + "var fb = true ? new Foo : new Bar;\n"
        + "fb.X++;",
        NewTypeInference.CONST_REASSIGNED);
  }

  public void testConstantByConvention() {
    typeCheck(
        "var ABC = 123;\n"
        + "ABC = 321;",
        NewTypeInference.CONST_REASSIGNED);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {\n"
        + "  this.ABC = 123;\n"
        + "}\n"
        + "(new Foo).ABC = 321;",
        NewTypeInference.CONST_REASSIGNED);
  }

  public void testDontOverrideFinalMethods() {
    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @final */\n"
        + "Foo.prototype.method = function(x) {};\n"
        + "/** @constructor @extends {Foo} */\n"
        + "function Bar() {}\n"
        + "Bar.prototype.method = function(x) {};",
        GlobalTypeInfo.CANNOT_OVERRIDE_FINAL_METHOD);

    checkNoWarnings("/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @final */\n"
        + "Foo.prototype.num = 123;\n"
        + "/** @constructor @extends {Foo} */\n"
        + "function Bar() {}\n"
        + "Bar.prototype.num = 2;");

    // // TODO(dimvar): fix
    // typeCheck(
    //     "/** @constructor */\n" +
    //     "function High() {}\n" +
    //     "/**\n" +
    //     " * @param {number} x\n" +
    //     " * @final\n" +
    //     " */\n" +
    //     "High.prototype.method = function(x) {};\n" +
    //     "/** @constructor @extends {High} */\n" +
    //     "function Mid() {}\n" +
    //     "/** @constructor @extends {Mid} */\n" +
    //     "function Low() {}\n" +
    //     "Low.prototype.method = function(x) {};",
    //     GlobalTypeInfo.CANNOT_OVERRIDE_FINAL_METHOD);
  }

  public void testInferenceOfConstType() {
    typeCheck(
        "/** @const */\n"
        + "var s = 'str';\n"
        + "function f() { s - 5; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(/** string */ x) {\n"
        + "  /** @const */\n"
        + "  var s = x;\n"
        + "  function g() { s - 5; }\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings("/** @const */\n"
        + "var r = /find/;");

    typeCheck(
        "/** @constructor */ function RegExp(){}\n"
        + "/** @const */\n"
        + "var r = /find/;\n"
        + "function g() { r - 5; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings("/** @const */\n"
        + "var a = [5];");

    typeCheck(
        "/** @const */\n"
        + "var a = [5];\n"
        + "function g() { a - 5; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "var x;\n"
        + "/** @const */ var o = x = {};\n"
        + "function g() { return o.prop; }",
        TypeCheck.INEXISTENT_PROPERTY);

    typeCheck(
        "/** @const */ var o = (0,{});\n"
        + "function g() { return o.prop; }",
        TypeCheck.INEXISTENT_PROPERTY);

    typeCheck(
        "/** @const */ var s = true ? null : null;\n"
        + "function g() { s - 5; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @const */ var s = true ? void 0 : undefined;\n"
        + "function g() { s - 5; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @const */ var b = true ? (1<2) : ('' in {});\n"
        + "function g() { b - 5; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @const */ var n = 0 || 6;\n"
        + "function g() { n < 'str'; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @const */ var s = 'str' + 5;\n"
        + "function g() { s - 5; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    // TODO(dimvar): must fix externs initialization
    // typeCheck(
    //     "var /** string */ x;",
    //     "/** @const */\n" +
    //     "var s = x;\n" +
    //     "function g() { s - 5; }",
    //     NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {\n"
        + "  /** @const */\n"
        + "  this.prop = 'str';\n"
        + "}\n"
        + "(new Foo).prop - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @const */\n"
        + "Foo.prop = 'str';\n"
        + "function g() { Foo.prop - 5; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(/** string */ s) {\n"
        + "  /** @constructor */\n"
        + "  function Foo() {}\n"
        + "  /** @const */\n"
        + "  Foo.prototype.prop = s;\n"
        + "  function g() {\n"
        + "    (new Foo).prop - 5;\n"
        + "  }\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @const */\n"
        + "var ns = {};\n"
        + "/** @const */\n"
        + "ns.prop = 'str';\n"
        + "function f() {\n"
        + "  ns.prop - 5;\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(x, y) {\n"
        + "  /** @const */\n"
        + "  var n = x - y;\n"
        + "  function g() { n < 'str'; }\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(x) {\n"
        + "  /** @const */\n"
        + "  var notx = !x;\n"
        + "  function g() { notx - 5; }\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @const */\n"
        + "var lit = { a: 'a', b: 'b' };\n"
        + "function g() { lit.a - 5; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @const */\n"
        + "var n = ('str', 123);\n"
        + "function f() { n < 'str'; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @const */\n"
        + "var s = x;\n"
        + "var /** string */ x;\n",
        ImmutableList.of(
            // VariableReferenceCheck.EARLY_REFERENCE,
            GlobalTypeInfo.COULD_NOT_INFER_CONST_TYPE));

    typeCheck(
        "function f(x) {\n"
        + "  /** @const */\n"
        + "  var c = x;\n"
        + "}",
        GlobalTypeInfo.COULD_NOT_INFER_CONST_TYPE);

    checkNoWarnings("function f(x) {\n"
        + "  /** @const */\n"
        + "  var c = { a: 1, b: x };\n"
        + "}");

    typeCheck(
        "/**\n"
        + " * @constructor\n"
        + " * @param {{ a: string }} x\n"
        + " */\n"
        + "function Foo(x) {\n"
        + "  /** @const */\n"
        + "  this.prop = x.a;\n"
        + "}\n"
        + "(new Foo({ a: ''})).prop - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @return {string} */\n"
        + "function f() { return ''; }\n"
        + "/** @const */\n"
        + "var s = f();\n"
        + "function g() { s - 5; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @const */\n"
        + "var s = f();\n"
        + "/** @return {string} */\n"
        + "function f() { return ''; }",
        GlobalTypeInfo.COULD_NOT_INFER_CONST_TYPE);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @constructor */\n"
        + "function Bar() {}\n"
        + "/** @const */\n"
        + "var foo = new Foo;\n"
        + "function g() {\n"
        + "  var /** Bar */ bar = foo;\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @const */\n"
        + "var n1 = 1;\n"
        + "/** @const */\n"
        + "var n2 = n1;\n"
        + "function g() { n2 < 'str'; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    // Don't treat aliased constructors as if they were const variables.
    checkNoWarnings("/** @constructor */ function Bar() {}\n"
        + "/**\n"
        + " * @constructor\n"
        + " * @final\n"
        + " */\n"
        + "var Foo = Bar;");

    checkNoWarnings("/** @constructor */ function Bar() {}\n"
        + "/** @const */\n"
        + "var ns = {};\n"
        + "/**\n"
        + " * @constructor\n"
        + " * @final\n"
        + " */\n"
        + "ns.Foo = Bar;");

    // (Counterintuitive) On a constructor, @final means don't subclass, not
    // that it's a const. We don't warn about reassignment.
    checkNoWarnings("/**\n"
        + " * @constructor\n"
        + " * @final\n"
        + " */\n"
        + "var Foo = function() {};\n"
        + "Foo = /** @type {?} */ (function() {});");
  }

  public void testSuppressions() {
    checkNoWarnings("/**\n"
        + " * @fileoverview\n"
        + " * @suppress {newCheckTypes}\n"
        + " */\n"
        + "123();");

    typeCheck(
        "123();\n"
        + "/** @suppress {newCheckTypes} */\n"
        + "function f() { 123(); }",
        TypeCheck.NOT_CALLABLE);

    typeCheck(
        "123();\n"
        + "/** @suppress {newCheckTypes} */\n"
        + "function f() { 1 - 'str'; }",
        TypeCheck.NOT_CALLABLE);

    checkNoWarnings("/** @const */ var ns = {};\n"
        + "/** @type {Object} */\n"
        + "ns.obj = { prop: 123 };\n"
        + "/**\n"
        + " * @suppress {duplicate}\n"
        + " * @type {Object}\n"
        + " */\n"
        + "ns.obj = null;");

    checkNoWarnings("function f() {\n"
        + "  /** @const */\n"
        + "  var ns = {};\n"
        + "  /** @type {number} */\n"
        + "  ns.prop = 1;\n"
        + "  /**\n"
        + "   * @type {number}\n"
        + "   * @suppress {duplicate}\n"
        + "   */\n"
        + "  ns.prop = 2;\n"
        + "}");
  }

  public void testTypedefs() {
    typeCheck(
        "/** @typedef {number} */\n"
        + "var num = 1;",
        GlobalTypeInfo.CANNOT_INIT_TYPEDEF);

    // typeCheck(
    //     "/** @typedef {number} */\n" +
    //     "var num;\n" +
    //     "num - 5;",
    //     VarCheck.UNDEFINED_VAR_ERROR);

    typeCheck(
        "/** @typedef {NonExistentType} */\n"
        + "var t;\n"
        + "function f(/** t */ x) { x - 1; }",
        GlobalTypeInfo.UNRECOGNIZED_TYPE_NAME);

    // typeCheck(
    //     "/** @typedef {number} */\n" +
    //     "var dup;\n" +
    //     "/** @typedef {number} */\n" +
    //     "var dup;",
    //     VariableReferenceCheck.REDECLARED_VARIABLE);

    typeCheck(
        "/** @typedef {number} */\n"
        + "var dup;\n"
        + "/** @typedef {string} */\n"
        + "var dup;\n"
        + "var /** dup */ n = 'str';",
        ImmutableList.of(
            // VariableReferenceCheck.REDECLARED_VARIABLE,
            NewTypeInference.MISTYPED_ASSIGN_RHS));

    checkNoWarnings("/** @typedef {number} */\n"
        + "var num;\n"
        + "/** @type {num} */\n"
        + "var n = 1;");

    typeCheck(
        "/** @typedef {number} */\n"
        + "var num;\n"
        + "/** @type {num} */\n"
        + "var n = 'str';",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @type {num} */\n"
        + "var n = 'str';\n"
        + "/** @typedef {number} */\n"
        + "var num;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @typedef {number} */\n"
        + "var num;\n"
        + "function f() {\n"
        + "  /** @type {num} */\n"
        + "  var n = 'str';\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @type {num2} */\n"
        + "var n = 'str';\n"
        + "/** @typedef {num} */\n"
        + "var num2;\n"
        + "/** @typedef {number} */\n"
        + "var num;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @typedef {rec2} */\n"
        + "var rec1;\n"
        + "/** @typedef {rec1} */\n"
        + "var rec2;",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "/** @typedef {{ prop: rec2 }} */\n"
        + "var rec1;\n"
        + "/** @typedef {{ prop: rec1 }} */\n"
        + "var rec2;",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    checkNoWarnings("/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @typedef {Foo} */\n"
        + "var Bar;\n"
        + "var /** Bar */ x = null;");

    // NOTE(dimvar): I don't know if long term we want to support ! on anything
    // other than a nominal-type name, but for now it's good to have this test.
    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @typedef {Foo} */\n"
        + "var Bar;\n"
        + "var /** !Bar */ x = null;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @typedef {number} */\n"
        + "var N;\n"
        + "function f() {\n"
        + "  /** @constructor */\n"
        + "  function N() {}\n"
        + "  function g(/** N */ obj) { obj - 5; }\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testLends() {
    typeCheck(
        "(/** @lends {InexistentType} */ { a: 1 });",
        GlobalTypeInfo.LENDS_ON_BAD_TYPE);

    typeCheck(
        "(/** @lends {number} */ { a: 1 });", GlobalTypeInfo.LENDS_ON_BAD_TYPE);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "(/** @lends {Foo.badname} */ { a: 1 });",
        GlobalTypeInfo.LENDS_ON_BAD_TYPE);

    typeCheck(
        "/** @interface */\n"
        + "function Foo() {}\n"
        + "(/** @lends {Foo} */ { a: 1 });",
        GlobalTypeInfo.LENDS_ON_BAD_TYPE);

    typeCheck(
        "/** @interface */\n"
        + "function Foo() {}\n"
        + "(/** @lends {Foo.prototype} */ { a: 1 });",
        GlobalTypeInfo.LENDS_ON_BAD_TYPE);

    typeCheck(
        "(/** @lends {Inexistent.Type} */ { a: 1 });",
        GlobalTypeInfo.LENDS_ON_BAD_TYPE);

    typeCheck(
        "/** @const */ var ns = {};\n"
        + "(/** @lends {ns} */ { /** @type {number} */ prop : 1 });\n"
        + "function f() { ns.prop = 'str'; }",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "(/** @lends {ns} */ { /** @type {number} */ prop : 1 });\n"
        + "/** @const */ var ns = {};\n"
        + "function f() { ns.prop = 'str'; }",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @const */ var ns = {};\n"
        + "(/** @lends {ns} */ { prop : 1 });\n"
        + "function f() { var /** string */ s = ns.prop; }",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @const */\n"
        + "var ns = {};\n"
        + "/** @const */\n"
        + "ns.subns = {};\n"
        + "(/** @lends {ns.subns} */ { prop: 1 });\n"
        + "var /** string */ s = ns.subns.prop;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "(/** @lends {Foo} */ { prop: 1 });\n"
        + "var /** string */ s = Foo.prop;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "(/** @lends {Foo} */ { prop: 1 });\n"
        + "function f() { var /** string */ s = Foo.prop; }",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @const */\n"
        + "var ns = {};\n"
        + "/** @constructor */\n"
        + "ns.Foo = function() {};\n"
        + "(/** @lends {ns.Foo} */ { prop: 1 });\n"
        + "function f() { var /** string */ s = ns.Foo.prop; }",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "(/** @lends {Foo.prototype} */ { /** @type {number} */ a: 1 });\n"
        + "var /** string */ s = Foo.prototype.a;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "(/** @lends {Foo.prototype} */ { /** @type {number} */ a: 1 });\n"
        + "var /** string */ s = (new Foo).a;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() { /** @type {number} */ this.prop = 1; }\n"
        + "(/** @lends {Foo.prototype} */\n"
        + " { /** @return {number} */ m: function() { return this.prop; } });\n"
        + "var /** string */ s = (new Foo).m();",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testEnumBasicTyping() {
    typeCheck(
        "/** @enum {number} */\n"
        + "var E = {\n"
        + "  ONE: 1,\n"
        + "  TWO: 2\n"
        + "};\n"
        + "function f(/** E */ x) { x < 'str'; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @enum */\n"
        + // No type annotation defaults to number
        "var E = {\n"
        + "  ONE: 1,\n"
        + "  TWO: 2\n"
        + "};\n"
        + "function f(/** E */ x) { x < 'str'; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings("/** @enum {number} */\n"
        + "var E = {\n"
        + "  ONE: 1,\n"
        + "  TWO: 2\n"
        + "};\n"
        + "function f(/** E */ x) {}\n"
        + "function g(/** number */ x) {}\n"
        + "f(E.TWO);\n"
        + "g(E.TWO);");

    typeCheck(
        "/** @enum {number} */\n"
        + "var E = {\n"
        + "  ONE: 1,\n"
        + "  TWO: 2\n"
        + "};\n"
        + "function f(/** E */ x) {}\n"
        + "f(1);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @enum {number} */\n"
        + "var E = {\n"
        + "  ONE: 1,\n"
        + "  TWO: 2\n"
        + "};\n"
        + "function f() { E.THREE - 5; }",
        TypeCheck.INEXISTENT_PROPERTY);

    checkNoWarnings("/** @enum {!Foo} */\n"
        + "var E = { ONE: new Foo };\n"
        + "/** @constructor */\n"
        + "function Foo() {}");

    typeCheck(
        "/** @typedef {number} */\n"
        + "var num;\n"
        + "/** @enum {num} */\n"
        + "var E = { ONE: 1 };\n"
        + "function f(/** E */ x) { x < 'str'; }",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testEnumsWithNonScalarDeclaredType() {
    typeCheck(
        "/** @enum {!Object} */ var E = {FOO: { prop: 1 }};\n"
        + "E.FOO.prop - 5;",
        TypeCheck.INEXISTENT_PROPERTY);

    checkNoWarnings(
        "/** @enum {{prop: number}} */ var E = {FOO: { prop: 1 }};\n"
        + "E.FOO.prop - 5;");

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {\n"
        + "  /** @const */ this.prop = 1;\n"
        + "}\n"
        + "/** @enum {!Foo} */\n"
        + "var E = { ONE: new Foo() };\n"
        + "function f(/** E */ x) { x.prop < 'str'; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {\n"
        + "  /** @const */ this.prop = 1;\n"
        + "}\n"
        + "/** @enum {!Foo} */\n"
        + "var E = { ONE: new Foo() };\n"
        + "function f(/** E */ x) { x.prop = 2; }",
        NewTypeInference.CONST_REASSIGNED);

    checkNoWarnings("/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @enum {!Foo} */\n"
        + "var E = { A: new Foo };\n"
        + "function f(/** E */ x) { x instanceof Foo; }");
  }

  public void testEnumBadInitializer() {
    typeCheck(
        "/** @enum {number} */\n"
        + "var E;",
        GlobalTypeInfo.MALFORMED_ENUM);

    typeCheck(
        "/** @enum {number} */\n"
        + "var E = {};",
        GlobalTypeInfo.MALFORMED_ENUM);

    typeCheck(
        "/** @enum {number} */\n"
        + "var E = 1;",
        GlobalTypeInfo.MALFORMED_ENUM);

    typeCheck(
        "/** @enum {number} */\n"
        + "var E = {\n"
        + "  ONE: 1,\n"
        + "  TWO: true\n"
        + "};",
        NewTypeInference.INVALID_OBJLIT_PROPERTY_TYPE);

    typeCheck(
        "/** @enum {number} */\n"
        + "var E = { a: 1 };",
        TypeCheck.ENUM_NOT_CONSTANT);

    typeCheck(
        "/** @enum {number} */\n"
        + "var E = { A: 1, A: 2 };",
        GlobalTypeInfo.DUPLICATE_PROP_IN_ENUM);
  }

  public void testEnumPropertiesConstant() {
    checkNoWarnings("/** @enum {number} */\n"
        + "var E = {\n"
        + "  ONE: 1,\n"
        + "  TWO: 2\n"
        + "};\n"
        + "E.THREE = 3;");

    typeCheck(
        "/** @enum {number} */\n"
        + "var E = {\n"
        + "  ONE: 1,\n"
        + "  TWO: 2\n"
        + "};\n"
        + "E.ONE = E.TWO;",
        NewTypeInference.CONST_REASSIGNED);

    typeCheck(
        "/** @enum {number} */\n"
        + "var E = {\n"
        + "  ONE: 1,\n"
        + "  TWO: 2\n"
        + "};\n"
        + "function f(/** E */) { E.ONE = E.TWO; }",
        NewTypeInference.CONST_REASSIGNED);
  }

  public void testEnumIllegalRecursion() {
    typeCheck(
        "/** @enum {Type2} */\n"
        + "var Type1 = {\n"
        + "  ONE: null\n"
        + "};\n"
        + "/** @enum {Type1} */\n"
        + "var Type2 = {\n"
        + "  ONE: null\n"
        + "};",
        ImmutableList.of(
            RhinoErrorReporter.BAD_JSDOC_ANNOTATION,
            // This warning is a side-effect of the fact that, when there is a
            // cycle, the resolution of one enum will fail but the others will
            // complete successfully.
            NewTypeInference.INVALID_OBJLIT_PROPERTY_TYPE));

    typeCheck(
        "/** @enum {Type2} */\n"
        + "var Type1 = {\n"
        + "  ONE: null\n"
        + "};\n"
        + "/** @typedef {Type1} */\n"
        + "var Type2;",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);
  }

  public void testEnumBadDeclaredType() {
    typeCheck(
        "/** @enum {InexistentType} */\n"
        + "var E = { ONE : null };",
        GlobalTypeInfo.UNRECOGNIZED_TYPE_NAME);

    typeCheck(
        "/** @enum {*} */\n"
        + "var E = { ONE: 1, STR: '' };",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    // No free type variables in enums
    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " */\n"
        + "function f(x) {\n"
        + "  /** @enum {function(T):number} */\n"
        + "  var E = { ONE: x };\n"
        + "}",
        GlobalTypeInfo.UNRECOGNIZED_TYPE_NAME);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " */\n"
        + "function f(x) {\n"
        + "  /** @enum {T} */\n"
        + "  var E1 = { ONE: 1 };\n"
        + "  /** @enum {function(E1):E1} */\n"
        + "  var E2 = { ONE: function(x) { return x; } };\n"
        + "}",
        GlobalTypeInfo.UNRECOGNIZED_TYPE_NAME);

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " */\n"
        + "function f(x) {\n"
        + "  /** @typedef {T} */ var AliasT;\n"
        + "  /** @enum {T} */\n"
        + "  var E1 = { ONE: 1 };\n"
        + "  /** @enum {function(E1):T} */\n"
        + "  var E2 = { ONE: function(x) { return x; } };\n"
        + "}",
        ImmutableList.of(
            GlobalTypeInfo.UNRECOGNIZED_TYPE_NAME,
            GlobalTypeInfo.UNRECOGNIZED_TYPE_NAME,
            GlobalTypeInfo.UNRECOGNIZED_TYPE_NAME));

    // No unions in enums
    typeCheck(
        "/** @enum {number|string} */\n"
        + "var E = { ONE: 1, STR: '' };",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @enum {Foo} */\n"
        + "var E = { ONE: new Foo, TWO: null };",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);

    typeCheck(
        "/** @typedef {number|string} */\n"
        + "var NOS;\n"
        + "/** @enum {NOS} */\n"
        + "var E = { ONE: 1, STR: '' };",
        RhinoErrorReporter.BAD_JSDOC_ANNOTATION);
  }

  public void testEnumsWithGenerics() {
    checkNoWarnings("/** @enum */ var E1 = { A: 1};\n"
        + "/** @enum */ var E2 = { A: 2};\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {(T|E1)} x\n"
        + " */\n"
        + "function f(x) {}\n"
        + "f(/** @type {(string|E1)} */ ('str'));");

    typeCheck(
        "/** @enum */ var E1 = { A: 1};\n"
        + "/** @enum */ var E2 = { A: 2};\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {(T|E1)} x\n"
        + " */\n"
        + "function f(x) {}\n"
        + "f(/** @type {(string|E2)} */ ('str'));",
        NewTypeInference.FAILED_TO_UNIFY);
  }

  public void testEnumJoinSpecializeMeet() {
    // join: enum {number} with number
    typeCheck(
        "/** @enum {number} */\n"
        + "var E = { ONE: 1 };\n"
        + "function f(cond) {\n"
        + "  var x = cond ? E.ONE : 5;\n"
        + "  x - 2;\n"
        + "  var /** E */ y = x;\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    // join: enum {Low} with High, to High
    typeCheck(
        "/** @constructor */\n"
        + "function High() {}\n"
        + "/** @constructor @extends {High} */\n"
        + "function Low() {}\n"
        + "/** @enum {!Low} */\n"
        + "var E = { A: new Low };\n"
        + "function f(cond) {\n"
        + "  var x = cond ? E.A : new High;\n"
        + "  var /** High */ y = x;\n"
        + "  var /** E */ z = x;\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    // join: enum {High} with Low, to (enum{High}|Low)
    checkNoWarnings("/** @constructor */\n"
        + "function High() {}\n"
        + "/** @constructor @extends {High} */\n"
        + "function Low() {}\n"
        + "/** @enum {!High} */\n"
        + "var E = { A: new High };\n"
        + "function f(cond) {\n"
        + "  var x = cond ? E.A : new Low;\n"
        + "  if (!(x instanceof Low)) { var /** E */ y = x; }\n"
        + "}");

    // meet: enum {?} with string, to enum {?}
    typeCheck(
        "/** @enum {?} */\n"
        + "var E = { A: 123 };\n"
        + "function f(x) {\n"
        + "  var /** string */ s = x;\n"
        + "  var /** E */ y = x;\n"
        + "  s = x;\n"
        + "}\n"
        + "f('str');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    // meet: E1|E2 with E1|E3, to E1
    typeCheck(
        "/** @enum {number} */\n"
        + "var E1 = { ONE: 1 };\n"
        + "/** @enum {number} */\n"
        + "var E2 = { TWO: 1 };\n"
        + "/** @enum {number} */\n"
        + "var E3 = { THREE: 1 };\n"
        + "function f(x) {\n"
        + "  var /** (E1|E2) */ y = x;\n"
        + "  var /** (E1|E3) */ z = x;\n"
        + "  var /** E1 */ w = x;\n"
        + "}\n"
        + "f(E2.TWO);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    // meet: enum {number} with number, to enum {number}
    typeCheck(
        "/** @enum {number} */\n"
        + "var E = { ONE: 1 };\n"
        + "function f(x) {\n"
        + "  var /** E */ y = x;\n"
        + "  var /** number */ z = x;\n"
        + "}\n"
        + "f(123);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    // meet: enum {Low} with High, to enum {Low}
    typeCheck(
        "/** @constructor */\n"
        + "function High() {}\n"
        + "/** @constructor @extends {High} */\n"
        + "function Low() {}\n"
        + "/** @enum {!Low} */\n"
        + "var E = { A: new Low };\n"
        + "function f(x) {\n"
        + "  var /** !High */ y = x;\n"
        + "  var /** E */ z = x;\n"
        + "}\n"
        + "f(new High);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    // meet: enum {Low} with (High1|High2), to enum {Low}
    typeCheck(
        "/** @interface */\n"
        + "function High1() {}\n"
        + "/** @interface */\n"
        + "function High2() {}\n"
        + "/** @constructor @implements {High1} @implements {High2} */\n"
        + "function Low() {}\n"
        + "/** @enum {!Low} */\n"
        + "var E = { A: new Low };\n"
        + "function f(x) {\n"
        + "  var /** (!High1 | !High2) */ y = x;\n"
        + "  var /** E */ z = x;\n"
        + "}\n"
        + "f(/** @type {!High1} */ (new Low));",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    // meet: enum {High} with Low
    typeCheck(
        "/** @constructor */\n"
        + "function High() {}\n"
        + "/** @constructor @extends {High} */\n"
        + "function Low() {}\n"
        + "/** @enum {!High} */\n"
        + "var E = { A: new High };\n"
        + "/** @param {function(E)|function(!Low)} x */\n"
        + "function f(x) { x(123); }",
        NewTypeInference.CALL_FUNCTION_WITH_BOTTOM_FORMAL);
  }

  public void testEnumAliasing() {
    checkNoWarnings("/** @enum {number} */\n"
        + "var e1 = { A: 1 };\n"
        + "/** @enum {number} */\n"
        + "var e2 = e1;");

    typeCheck(
        "var x;\n"
        + "/** @enum {number} */\n"
        + "var e1 = { A: 1 };\n"
        + "/** @enum {number} */\n"
        + "var e2 = x;",
        GlobalTypeInfo.MALFORMED_ENUM);

    checkNoWarnings("/** @const */\n"
        + "var ns1 = {};\n"
        + "/** @enum {number} */\n"
        + "ns1.e1 = { A: 1 };\n"
        + "/** @const */\n"
        + "var ns2 = {};\n"
        + "/** @enum {number} */\n"
        + "ns2.e2 = ns1.e1;");

    checkNoWarnings("/** @enum {number} */\n"
        + "var e1 = { A: 1 };\n"
        + "/** @enum {number} */\n"
        + "var e2 = e1;\n"
        + "function f(/** e2 */ x) {}\n"
        + "f(e1.A);");

    checkNoWarnings("/** @enum {number} */\n"
        + "var e1 = { A: 1 };\n"
        + "/** @enum {number} */\n"
        + "var e2 = e1;\n"
        + "function f(/** e2 */ x) {}\n"
        + "f(e2.A);");

    typeCheck(
        "/** @enum {number} */\n"
        + "var e1 = { A: 1 };\n"
        + "/** @enum {number} */\n"
        + "var e2 = e1;\n"
        + "function f(/** e2 */ x) {}\n"
        + "f('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("/** @const */\n"
        + "var ns1 = {};\n"
        + "/** @enum {number} */\n"
        + "ns1.e1 = { A: 1 };\n"
        + "/** @const */\n"
        + "var ns2 = {};\n"
        + "/** @enum {number} */\n"
        + "ns2.e2 = ns1.e1;\n"
        + "function f(/** ns2.e2 */ x) {}\n"
        + "f(ns1.e1.A);");

    checkNoWarnings("/** @const */\n"
        + "var ns1 = {};\n"
        + "/** @enum {number} */\n"
        + "ns1.e1 = { A: 1 };\n"
        + "/** @const */\n"
        + "var ns2 = {};\n"
        + "/** @enum {number} */\n"
        + "ns2.e2 = ns1.e1;\n"
        + "function f(/** ns1.e1 */ x) {}\n"
        + "f(ns2.e2.A);");

    checkNoWarnings("/** @const */\n"
        + "var ns1 = {};\n"
        + "/** @enum {number} */\n"
        + "ns1.e1 = { A: 1 };\n"
        + "function g() {\n"
        + "  /** @const */\n"
        + "  var ns2 = {};\n"
        + "  /** @enum {number} */\n"
        + "  ns2.e2 = ns1.e1;\n"
        + "  function f(/** ns1.e1 */ x) {}\n"
        + "  f(ns2.e2.A);\n"
        + "}");
  }

  public void testNoDoubleWarnings() {
    typeCheck(
        "if ((4 - 'str') && true) { 4 + 5; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck("(4 - 'str') ? 5 : 6;", NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testRecordSpecializeNominalPreservesRequired() {
    typeCheck(
        "/** @constructor */\n"
        + "function Foo() { /** @type {number|string} */ this.x = 5 };\n"
        + "var o = true ? {x:5} : {y:'str'};\n"
        + "if (o instanceof Foo) {\n"
        + "  var /** {x:number} */ o2 = o;\n"
        + "}\n"
        + "(function(/** {x:number} */ o3){})(o);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);
  }

  public void testGoogIsPredicatesNoSpecializedContext() {
    typeCheck(CLOSURE_BASE + "goog.isNull();", TypeCheck.WRONG_ARGUMENT_COUNT);

    typeCheck(
        CLOSURE_BASE + "goog.isNull(1, 2, 5 - 'str');",
        ImmutableList.of(
            TypeCheck.WRONG_ARGUMENT_COUNT,
            NewTypeInference.INVALID_OPERAND_TYPE));

    checkNoWarnings(CLOSURE_BASE
        + "function f(x) { var /** boolean */ b = goog.isNull(x); }");
  }

  public void testGoogIsPredicatesTrue() {
    typeCheck(
        CLOSURE_BASE
        + "function f(x) { if (goog.isNull(x)) { var /** undefined */ y = x; } }",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        CLOSURE_BASE + "/** @param {number=} x */\n"
        + "function f(x) {\n"
        + "  if (goog.isDef(x)) {\n"
        + "    x - 5;\n"
        + "  }\n"
        + "  x - 5;\n"
        + "}",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        CLOSURE_BASE + "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @param {Foo=} x */\n"
        + "function f(x) {\n"
        + "  var /** !Foo */ y;\n"
        + "  if (goog.isDefAndNotNull(x)) {\n"
        + "    y = x;\n"
        + "  }\n"
        + "  y = x;\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        CLOSURE_BASE + "function f(/** (Array<number>|number) */ x) {\n"
        + "  var /** Array<number> */ a;\n"
        + "  if (goog.isArray(x)) {\n"
        + "    a = x;\n"
        + "  }\n"
        + "  a = x;\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        CLOSURE_BASE + "/** @param {null|function(number)} x */ \n"
        + "function f(x) {\n"
        + "  if (goog.isFunction(x)) {\n"
        + "    x('str');\n"
        + "  }\n"
        + "}",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        CLOSURE_BASE + "function f(x) {\n"
        + "  if (goog.isObject(x)) {\n"
        + "    var /** null */ y = x;\n"
        + "  }\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings(CLOSURE_BASE + "function f(/** (number|string) */ x) {\n"
        + "  if (goog.isString(x)) {\n"
        + "    x < 'str';\n"
        + "  }\n"
        + "}");

    checkNoWarnings(CLOSURE_BASE + "function f(/** (number|string) */ x) {\n"
        + "  if (goog.isNumber(x)) {\n"
        + "    x - 5;\n"
        + "  }\n"
        + "}");

    checkNoWarnings(CLOSURE_BASE + "function f(/** (number|boolean) */ x) {\n"
        + "  if (goog.isBoolean(x)) {\n"
        + "    var /** boolean */ b = x;\n"
        + "  }\n"
        + "}");

    checkNoWarnings(CLOSURE_BASE + "/**\n"
        + " * @param {number|string} x\n"
        + " * @return {string}\n"
        + " */\n"
        + "function f(x) {\n"
        + "  return goog.isString(x) && (1 < 2) ? x : 'a';\n"
        + "}");
  }

  public void testGoogIsPredicatesFalse() {
    typeCheck(
        CLOSURE_BASE + "/** @constructor */\n"
        + "function Foo() {}\n"
        + "function f(/** Foo */ x) {\n"
        + "  var /** !Foo */ y;\n"
        + "  if (!goog.isNull(x)) {\n"
        + "    y = x;\n"
        + "  }\n"
        + "  y = x;\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings(CLOSURE_BASE + "/** @param {number=} x */\n"
        + "function f(x) {\n"
        + "  if (!goog.isDef(x)) {\n"
        + "    var /** undefined */ u = x;\n"
        + "  }\n"
        + "}");

    checkNoWarnings(CLOSURE_BASE + "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @param {Foo=} x */\n"
        + "function f(x) {\n"
        + "  if (!goog.isDefAndNotNull(x)) {\n"
        + "    var /** (null|undefined) */ y = x;\n"
        + "  }\n"
        + "}");

    checkNoWarnings(CLOSURE_BASE + "function f(/** (number|string) */ x) {\n"
        + "  if (!goog.isString(x)) {\n"
        + "    x - 5;\n"
        + "  }\n"
        + "}");

    checkNoWarnings(CLOSURE_BASE + "function f(/** (number|string) */ x) {\n"
        + "  if (!goog.isNumber(x)) {\n"
        + "    x < 'str';\n"
        + "  }\n"
        + "}");

    checkNoWarnings(CLOSURE_BASE + "function f(/** (number|boolean) */ x) {\n"
        + "  if (!goog.isBoolean(x)) {\n"
        + "    x - 5;\n"
        + "  }\n"
        + "}");

    checkNoWarnings(CLOSURE_BASE
        + "function f(/** (number|!Array<number>) */ x) {\n"
        + "  if (!goog.isArray(x)) {\n"
        + "    x - 5;\n"
        + "  }\n"
        + "}");

    checkNoWarnings(CLOSURE_BASE + "function f(x) {\n"
        + "  if (goog.isArray(x)) {\n"
        + "    return x[0] - 5;\n"
        + "  }\n"
        + "}");

    checkNoWarnings(CLOSURE_BASE
        + "function f(/** (number|function(number)) */ x) {\n"
        + "  if (!goog.isFunction(x)) {\n"
        + "    x - 5;\n"
        + "  }\n"
        + "}");

    checkNoWarnings(CLOSURE_BASE + "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @param {?Foo} x */\n"
        + "function f(x) {\n"
        + "  if (!goog.isObject(x)) {\n"
        + "    var /** null */ y = x;\n"
        + "  }\n"
        + "}");
  }

  public void testGoogTypeof() {
    checkNoWarnings(CLOSURE_BASE + "function f(/** (number|string) */ x) {\n"
        + "  if (goog.typeOf(x) === 'number') {\n"
        + "    var /** number */ n = x;\n"
        + "  } else {\n"
        + "    var /** string */ s = x;\n"
        + "  }\n"
        + "}");

    checkNoWarnings(CLOSURE_BASE + "function f(/** (number|string) */ x) {\n"
        + "  if ('number' === goog.typeOf(x)) {\n"
        + "    var /** number */ n = x;\n"
        + "  }\n"
        + "}");

    checkNoWarnings(CLOSURE_BASE + "function f(/** (number|string) */ x) {\n"
        + "  if (goog.typeOf(x) == 'number') {\n"
        + "    var /** number */ n = x;\n"
        + "  }\n"
        + "}");

    checkNoWarnings(CLOSURE_BASE + "/** @param {number=} x */\n"
        + "function f(x) {\n"
        + "  if (goog.typeOf(x) === 'undefined') {\n"
        + "    var /** undefined */ u = x;\n"
        + "  } else {\n"
        + "    var /** number */ n = x;\n"
        + "  }\n"
        + "}");

    typeCheck(
        CLOSURE_BASE + "/** @param {string} x */\n"
        + "function f(x, other) {\n"
        + "  if (goog.typeOf(x) === other) {\n"
        + "    var /** null */ n = x;\n"
        + "  } else {\n"
        + "    x - 5;\n"
        + "  }\n"
        + "}",
        ImmutableList.of(
            NewTypeInference.MISTYPED_ASSIGN_RHS,
            NewTypeInference.INVALID_OPERAND_TYPE));
  }

  public void testSuperClassCtorProperty() throws Exception {
    String CLOSURE_DEFS =
        "/** @const */ var goog = {};\n"
        + "goog.inherits = function(child, parent){};\n";

    checkNoWarnings(CLOSURE_DEFS + "/** @constructor */function base() {}\n"
        + "/** @return {number} */ "
        + "  base.prototype.foo = function() { return 1; };\n"
        + "/** @extends {base}\n * @constructor */function derived() {}\n"
        + "goog.inherits(derived, base);\n"
        + "var /** number */ n = derived.superClass_.foo()");

    typeCheck(
        CLOSURE_DEFS + "/** @constructor */ function OldType() {}"
        + "/** @param {?function(new:OldType)} f */ function g(f) {"
        + "  /**\n"
        + "    * @constructor\n"
        + "    * @extends {OldType}\n"
        + "    */\n"
        + "  function NewType() {};"
        + "  goog.inherits(NewType, f);"
        + "  NewType.prototype.method = function() {"
        + "    NewType.superClass_.foo.call(this);"
        + "  };"
        + "}",
        TypeCheck.INEXISTENT_PROPERTY);

    checkNoWarnings(CLOSURE_DEFS + "/** @constructor */ function Foo() {}\n"
        + "goog.inherits(Foo, Object);\n"
        + "var /** !Object */ b = Foo.superClass_;");

    typeCheck(
        CLOSURE_DEFS + "/** @constructor */ function base() {}\n"
        + "/** @constructor @extends {base} */ function derived() {}\n"
        + "goog.inherits(derived, base);\n"
        + "var /** null */ b = derived.superClass_",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        CLOSURE_DEFS + "var /** !Object */ b = Object.superClass_",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        CLOSURE_DEFS + "var o = {x: 'str'}; var q = o.superClass_;",
        TypeCheck.INEXISTENT_PROPERTY);

    checkNoWarnings(CLOSURE_DEFS
        + "var o = {superClass_: 'str'}; var /** string */ s = o.superClass_;");
  }

  public void testAcrossScopeNamespaces() {
    typeCheck(
        "/** @const */\n"
        + "var ns = {};\n"
        + "(function() {\n"
        + "  /** @constructor */\n"
        + "  ns.Foo = function() {};\n"
        + "})();\n"
        + "ns.Foo();",
        TypeCheck.CONSTRUCTOR_NOT_CALLABLE);

    typeCheck(
        "/** @const */\n"
        + "var ns = {};\n"
        + "(function() {\n"
        + "  /** @type {string} */\n"
        + "  ns.str = 'str';\n"
        + "})();\n"
        + "ns.str - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @const */\n"
        + "var ns = {};\n"
        + "(function() {\n"
        + "  /** @constructor */\n"
        + "  ns.Foo = function() {};\n"
        + "})();\n"
        + "function f(/** ns.Foo */ x) {}\n"
        + "f(1);",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @const */\n"
        + "var ns = {};\n"
        + "(function() {\n"
        + "  /** @constructor */\n"
        + "  ns.Foo = function() {};\n"
        + "})();\n"
        + "var /** ns.Foo */ x = 123;",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testQualifiedNamedTypes() {
    typeCheck(
        "/** @const */\n"
        + "var ns = {};\n"
        + "/** @constructor */\n"
        + "ns.Foo = function() {};\n"
        + "ns.Foo();",
        TypeCheck.CONSTRUCTOR_NOT_CALLABLE);

    typeCheck(
        "/** @const */\n"
        + "var ns = {};\n"
        + "/** @typedef {number} */\n"
        + "ns.num;\n"
        + "var /** ns.num */ y = 'str';",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @const */\n"
        + "var ns = {};\n"
        + "/** @enum {number} */\n"
        + "ns.Foo = { A: 1 };\n"
        + "var /** ns.Foo */ y = 'str';",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @enum {number} */\n"
        + "var E = { A: 1 };\n"
        + "/** @typedef {number} */\n"
        + "E.num;\n"
        + "var /** E.num */ x = 'str';",
        NewTypeInference.MISTYPED_ASSIGN_RHS);
  }

  public void testEnumsAsNamespaces() {
    typeCheck(
        "/** @const */ var ns = {};\n"
        + "/** @enum {number} */\n"
        + "ns.E = {\n"
        + "  ONE: 1,\n"
        + "  TWO: true\n"
        + "};",
        NewTypeInference.INVALID_OBJLIT_PROPERTY_TYPE);

    typeCheck(
        "/** @enum */\n"
        + "var E = { A: 1 };\n"
        + "/** @enum */\n"
        + "E.E2 = { B: true };\n"
        + "var /** E */ x = E.A;",
        NewTypeInference.INVALID_OBJLIT_PROPERTY_TYPE);

    checkNoWarnings("/** @enum */\n"
        + "var E = { A: 1 };\n"
        + "/** @constructor */\n"
        + "E.Foo = function(x) {};\n"
        + "var /** E */ x = E.A;");

    checkNoWarnings("/** @const */\n"
        + "var ns = {};\n"
        + "/** @enum {number} */\n"
        + "ns.E = { A: 1 };\n"
        + "/** @constructor */\n"
        + "ns.E.Foo = function(x) {};");

    typeCheck(
        "/** @const */\n"
        + "var ns = {};\n"
        + "/** @enum {number} */\n"
        + "ns.E = { A: 1 };\n"
        + "/** @constructor */\n"
        + "ns.E.Foo = function(x) {};\n"
        + "function f() { ns.E.Foo(); }",
        TypeCheck.CONSTRUCTOR_NOT_CALLABLE);
  }

  public void testStringMethods() {
    checkNoWarnings("/** @constructor */ function String(){}\n"
        + "/** @this {String|string} */\n"
        + "String.prototype.substr = function(start) {};\n"
        + "/** @const */ var ns = {};\n"
        + "/** @const */ var s = 'str';\n"
        + "ns.r = s.substr(2);");
  }

  public void testOutOfOrderDeclarations() {
    // This is technically valid JS, but we don't support it
    checkNoWarnings("Foo.STATIC = 5; /** @constructor */ function Foo(){}");
  }

  public void testAbstractMethodsAreTypedCorrectly() {
    checkNoWarnings("/** @type {!Function} */\n"
        + "var abstractMethod = function(){};\n"
        + "/** @constructor */ function Foo(){};\n"
        + "/** @constructor @extends {Foo} */ function Bar(){};\n"
        + "/** @param {number} index */\n"
        + "Foo.prototype.m = abstractMethod;\n"
        + "/** @override */\n"
        + "Bar.prototype.m = function(index) {};");

    typeCheck(
        "/** @type {!Function} */\n"
        + "var abstractMethod = function(){};\n"
        + "/** @constructor */ function Foo(){};\n"
        + "/** @constructor @extends {Foo} */ function Bar(){};\n"
        + "/** @param {number} index */\n"
        + "Foo.prototype.m = abstractMethod;\n"
        + "/** @override */\n"
        + "Bar.prototype.m = function(index) {};\n"
        + "(new Bar).m('str');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @type {!Function} */\n"
        + "var abstractMethod = function(){};\n"
        + "/** @constructor */ function Foo(){};\n"
        + "/** @constructor @extends {Foo} */ function Bar(){};\n"
        + "/**\n"
        + " * @param {number} b\n"
        + " * @param {string} a\n"
        + " */\n"
        + "Foo.prototype.m = abstractMethod;\n"
        + "/** @override */\n"
        + "Bar.prototype.m = function(a, b) {};\n"
        + "(new Bar).m('str', 5);",
        ImmutableList.of(
            NewTypeInference.INVALID_ARGUMENT_TYPE,
            NewTypeInference.INVALID_ARGUMENT_TYPE));

    typeCheck(
        "/** @type {!Function} */\n"
        + "var abstractMethod = function(){};\n"
        + "/** @constructor */ function Foo(){};\n"
        + "/** @constructor @extends {Foo} */ function Bar(){};\n"
        + "/** @type {function(number, string)} */\n"
        + "Foo.prototype.m = abstractMethod;\n"
        + "/** @override */\n"
        + "Bar.prototype.m = function(a, b) {};\n"
        + "(new Bar).m('str', 5);",
        ImmutableList.of(
            NewTypeInference.INVALID_ARGUMENT_TYPE,
            NewTypeInference.INVALID_ARGUMENT_TYPE));
  }

  public void testUseJsdocOfCalleeForUnannotatedFunctionsInArgumentPosition() {
    typeCheck(
        "/** @constructor */\n"
        + "function Foo() { /** @type {string} */ this.prop = 'asdf'; }\n"
        + "/** @param {function(!Foo)} fun */\n"
        + "function f(fun) {}\n"
        + "f(function(x) { x.prop = 123; });",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() { /** @type {string} */ this.prop = 'asdf'; }\n"
        + "function f(/** function(this:Foo) */ x) {}\n"
        + "f(function() { this.prop = 123; });",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/** @param {function(string)} fun */\n"
        + "function f(fun) {}\n"
        + "f(function(str) { str - 5; });",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @param {function(number, number=)} fun */\n"
        + "function f(fun) {}\n"
        + "f(function(num, maybeNum) { num - maybeNum; });",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @param {function(string, ... [string])} fun */\n"
        + "function f(fun) {}\n"
        + "f(function(str, maybeStrs) { str - 5; });",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @const */ var ns = {};\n"
        + "/** @param {function(string)} fun */\n"
        + "ns.f = function(fun) {}\n"
        + "ns.f(function(str) { str - 5; });",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @const */ var ns = {};\n"
        + "/** @type {function(function(string))} */\n"
        + "ns.f = function(fun) {}\n"
        + "ns.f(function(str) { str - 5; });",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @constructor */ function Foo() {}\n"
        + "/** @param {function(string)} fun */\n"
        + "Foo.f = function(fun) {}\n"
        + "Foo.f(function(str) { str - 5; });",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "(/** @param {function(string)} fun */ function(fun) {})(\n"
        + "  function(str) { str - 5; });",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "/** @const */ var ns = {};\n"
        + "/** @param {function(function(function(string)))} outerFun */\n"
        + "ns.f = function(outerFun) {};\n"
        + "ns.f(function(innerFun) {\n"
        + "  innerFun(function(str) { str - 5; });\n"
        + "});",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testNamespacesWithNonEmptyObjectLiteral() {
    checkNoWarnings("/** @const */ var o = { /** @const */ PROP: 5 };");

    checkNoWarnings(
        "var x = 5; /** @const */ var o = { /** @const {number} */ PROP: x };");

    typeCheck(
        "var x = 'str';\n"
        + "/** @const */ var o = { /** @const {string} */ PROP: x };\n"
        + "function g() { o.PROP - 5; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    checkNoWarnings("/** @const */ var ns = {};\n"
        + "/** @const */ ns.o = { /** @const */ PROP: 5 };");

    checkNoWarnings("/** @const */ var ns = {};\n"
        + "var x = 5;\n"
        + "/** @const */ ns.o = { /** @const {number} */ PROP: x };");

    typeCheck(
        "/** @const */ var ns = {};\n"
        + "var x = 'str';\n"
        + "/** @const */ ns.o = { /** @const {string} */ PROP: x };\n"
        + "function g() { ns.o.PROP - 5; }",
        NewTypeInference.INVALID_OPERAND_TYPE);

    // These declarations are not considered namespaces
    typeCheck(
        "(function(){ return {}; })().ns = { /** @const */ PROP: 5 };",
        GlobalTypeInfo.MISPLACED_CONST_ANNOTATION);

    typeCheck(
        "function f(/** { x : string } */ obj) {\n"
        + "  obj.ns = { /** @const */ PROP: 5 };\n"
        + "}",
        GlobalTypeInfo.MISPLACED_CONST_ANNOTATION);
  }

  // TODO(dimvar): fix
  public void testAllTestsShouldHaveDupPropWarnings() {
    checkNoWarnings("/** @const */\n"
        + "var ns = {};\n"
        + "/** @const */\n"
        + "ns.Foo = {};\n"
        + "ns.Foo = { a: 123 };");

    checkNoWarnings("/** @const */\n"
        + "var ns = {};\n"
        + "/** @const */\n"
        + "ns.Foo = {};\n"
        + "/** @const */\n"
        + "ns.Foo = { a: 123 };");

    checkNoWarnings("/** @const */\n"
        + "var ns = {};\n"
        + "/** @const */\n"
        + "ns.Foo = {};\n"
        + "/**\n"
        + " * @const\n"
        +
        // @suppress is ignored here b/c there is no @type in the jsdoc.
        " * @suppress {duplicate}\n"
        + " */\n"
        + "ns.Foo = { a: 123 };");

    checkNoWarnings("/** @const */\n"
        + "var ns = {};\n"
        + "/** @const */\n"
        + "ns.Foo = {};\n"
        + "/** @type {number} */\n"
        + "ns.Foo = 123;");

    checkNoWarnings("/** @enum {number} */\n"
        + "var en = { A: 5 };\n"
        + "/** @const */\n"
        + "en.Foo = {};\n"
        + "/** @type {number} */\n"
        + "en.Foo = 123;");

    checkNoWarnings("/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @const */\n"
        + "Foo.ns = {};\n"
        + "/** @const */\n"
        + "Foo.ns = {};");
  }

  public void testNominalTypeAliasing() {
    checkNoWarnings("/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @constructor */\n"
        + "var Bar = Foo;\n"
        + "var /** !Bar */ x = new Foo();");

    checkNoWarnings("/** @const */\n"
        + "var ns = {};\n"
        + "/** @constructor */\n"
        + "ns.Foo = function() {};\n"
        + "/** @constructor */\n"
        + "ns.Bar = ns.Foo;\n"
        + "function g() {\n"
        + "  var /** !ns.Bar */ x = new ns.Foo();\n"
        + "  var /** !ns.Bar */ y = new ns.Bar();\n"
        + "}");

    typeCheck(
        "/** @type {number} */\n"
        + "var n = 123;\n"
        + "/** @constructor */\n"
        + "var Foo = n;",
        GlobalTypeInfo.EXPECTED_CONSTRUCTOR);

    typeCheck(
        "/** @type {number} */\n"
        + "var n = 123;\n"
        + "/** @interface */\n"
        + "var Foo = n;",
        GlobalTypeInfo.EXPECTED_INTERFACE);

    typeCheck(
        "/** @interface */\n"
        + "function Foo() {}\n"
        + "/** @constructor */\n"
        + "var Bar = Foo;",
        GlobalTypeInfo.EXPECTED_CONSTRUCTOR);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @interface */\n"
        + "var Bar = Foo;",
        GlobalTypeInfo.EXPECTED_INTERFACE);

    // TODO(dimvar): When we allow unknown type names, eg, for fwd-declared
    // types, then we can also fix this.
    // Currently, the type checker doesn't know what !Foo is.
    typeCheck(
        "var Bar;",
        "/**\n"
        + " * @constructor\n"
        + " * @final\n"
        + " */\n"
        + "var Foo = Bar;\n"
        + "var /** !Foo */ x;",
        ImmutableList.of(
            GlobalTypeInfo.EXPECTED_CONSTRUCTOR,
            GlobalTypeInfo.UNRECOGNIZED_TYPE_NAME));
  }

  public void testTypeVariablesVisibleInPrototypeMethods() {
    typeCheck(
        "/**\n"
        + " * @constructor\n"
        + " * @template T\n"
        + " */\n"
        + "function Foo() {}\n"
        + "Foo.prototype.method = function() {\n"
        + "  /** @type {T} */\n"
        + "  this.prop = 123;\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "/**\n"
        + " * @constructor\n"
        + " * @template T\n"
        + " */\n"
        + "function Foo() {}\n"
        + "/** @param {T} x */"
        + "Foo.prototype.method = function(x) {\n"
        + "  x = 123;\n"
        + "}",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings("/**\n"
        + " * @constructor\n"
        + " * @template T\n"
        + " */\n"
        + "function Foo() {}\n"
        + "/** @param {T} x */"
        + "Foo.prototype.method = function(x) {\n"
        + "  this.prop = x;\n"
        + "}");

    typeCheck(
        "/**\n"
        + " * @constructor\n"
        + " * @template T\n"
        + " */\n"
        + "function Foo() {}\n"
        + "/** @param {T} x */"
        + "Foo.prototype.method = function(x) {\n"
        + "  /** @const */\n"
        + "  this.prop = x;\n"
        + "}",
        GlobalTypeInfo.MISPLACED_CONST_ANNOTATION);

    typeCheck(
        "/**\n"
        + " * @constructor\n"
        + " * @template T\n"
        + " */\n"
        + "function Parent() {}\n"
        + "/**\n"
        + " * @constructor\n"
        + " * @extends {Parent<string>}\n"
        + " */\n"
        + "function Child() {}\n"
        + "Child.prototype.method = function() {\n"
        + "  /** @type {T} */\n"
        + "  this.prop = 123;\n"
        + "}",
        GlobalTypeInfo.UNRECOGNIZED_TYPE_NAME);
  }

  public void testInferConstTypeFromEnumProps() {
    checkNoWarnings("/** @enum */\n"
        + "var e = { A: 1 };\n"
        + "/** @const */\n"
        + "var numarr = [ e.A ];");

    typeCheck(
        "/** @enum */\n"
        + "var e = { A: 1 };\n"
        + "/** @type {number} */\n"
        + "e.prop = 123;\n"
        + "/** @const */\n"
        + "var x = e.prop;",
        GlobalTypeInfo.COULD_NOT_INFER_CONST_TYPE);
  }

  public void testForwardDeclarations() {
    final String DEFINITIONS =
        "/** @const */ var goog = {};\n"
        + "goog.addDependency = function(file, provides, requires){};\n"
        + "goog.forwardDeclare = function(name){};";

    checkNoWarnings(DEFINITIONS + "goog.addDependency('', ['Foo'], []);\n"
        + "goog.forwardDeclare('Bar');\n"
        + "function f(/** !Foo */ x) {}\n"
        + "function g(/** !Bar */ y) {}");

    checkNoWarnings(DEFINITIONS + "/** @const */ var ns = {};\n"
        + "goog.addDependency('', ['ns.Foo'], []);\n"
        + "goog.forwardDeclare('ns.Bar');\n"
        + "function f(/** !ns.Foo */ x) {}\n"
        + "function g(/** !ns.Bar */ y) {}");

    // TODO(blickly): Allow forward declared names that are used in code.
    //     checkNoWarnings(DEFINITIONS +
    //         "goog.addDependency('', ['Foo'], []);\n" +
    //         "goog.forwardDeclare('Bar');\n" +
    //         "var f = new Foo;\n" +
    //         "var b = new Bar;");
    //
    //     checkNoWarnings(DEFINITIONS +
    //         "/** @const */ var ns = {};\n" +
    //         "goog.addDependency('', ['ns.Foo'], []);\n" +
    //         "goog.forwardDeclare('ns.Bar');\n" +
    //         "var f = new ns.Foo;\n" +
    //         "var b = new ns.Bar;");
    //
    //     checkNoWarnings(DEFINITIONS +
    //         "/** @const */ var ns = {};\n" +
    //         "goog.addDependency('', ['ns.subns.Foo'], []);\n" +
    //         "goog.forwardDeclare('ns.subns.Bar');\n" +
    //         "var f = new ns.subns.Foo;\n" +
    //         "var b = new ns.subns.Bar;");
    //
    //     checkNoWarnings(DEFINITIONS +
    //         "goog.addDependency('', ['ns.subns.Foo'], []);\n" +
    //         "goog.forwardDeclare('ns.subns.Bar');\n" +
    //         "var f = new ns.subns.Foo;\n" +
    //         "var b = new ns.subns.Bar;");
    //
    //     checkNoWarnings(DEFINITIONS +
    //         "goog.forwardDeclare('ns.subns');\n" +
    //         "goog.forwardDeclare('ns.subns.Bar');\n" +
    //         "var b = new ns.subns.Bar;");

    // In the following cases the old type inference warned about arg type,
    // but we allow rather than create synthetic named type
    checkNoWarnings(DEFINITIONS + "goog.forwardDeclare('Foo');\n"
        + "function f(/** !Foo */ x) {}\n"
        + "/** @constructor */ function Bar(){}\n"
        + "f(new Bar);");

    checkNoWarnings(DEFINITIONS + "/** @const */ var ns = {};\n"
        + "goog.forwardDeclare('ns.Foo');\n"
        + "function f(/** !ns.Foo */ x) {}\n"
        + "/** @constructor */ function Bar(){}\n"
        + "f(new Bar);");
  }

  public void testDontLookupInParentScopeForNamesWithoutDeclaredType() {
    checkNoWarnings("/** @type {number} */\n"
        + "var x;\n"
        + "function f() {\n"
        + "  var x = true;\n"
        + "}");
  }

  public void testSpecializationInPropertyAccesses() {
    checkNoWarnings("/** @const */\n"
        + "var ns = {};\n"
        + "/** @type {?number} */ ns.n = 123;\n"
        + "if (ns.n === null) {\n"
        + "} else {\n"
        + "  ns.n - 5;\n"
        + "}");

    checkNoWarnings("/** @const */\n"
        + "var ns = {};\n"
        + "/** @type {?number} */ ns.n = 123;\n"
        + "if (ns.n !== null) {\n"
        + "  ns.n - 5;\n"
        + "}");

    checkNoWarnings("var obj = { n: (1 < 2 ? null : 123) };\n"
        + "if (obj.n !== null) {\n"
        + "  obj.n - 123;\n"
        + "}");

    typeCheck(
        "var obj = { n: (1 < 2 ? null : 123) };\n"
        + "if (obj.n !== null) {\n"
        + "  obj.n - 123;\n"
        + "}\n"
        + "obj.n - 123;",
        NewTypeInference.INVALID_OPERAND_TYPE);
  }

  public void testAutoconvertBoxedNumberToNumber() {
    typeCheck(
        "var /** !Number */ n = 123;", NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "var /** number */ n = new Number(123);",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings("function f(/** !Number */ x, y) {\n"
        + "  return x - y;\n"
        + "}");

    checkNoWarnings("function f(x, /** !Number */ y) {\n"
        + "  return x - y;\n"
        + "}");

    checkNoWarnings("function f(/** !Number */ x) {\n"
        + "  return x + 'asdf';\n"
        + "}");

    checkNoWarnings("function f(/** !Number */ x) {\n"
        + "  return -x;\n"
        + "}");

    checkNoWarnings("function f(/** !Number */ x) {\n"
        + "  x -= 123;\n"
        + "}");

    checkNoWarnings("function f(/** !Number */ x, y) {\n"
        + "  y -= x;\n"
        + "}");

    checkNoWarnings("function f(/** !Number */ x, /** !Array<string>*/ arr) {\n"
        + "  return arr[x];\n"
        + "}");
  }

  public void testAutoconvertBoxedStringToString() {
    typeCheck(
        "var /** !String */ s = '';", NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "var /** string */ s = new String('');",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings("function f() {\n"
        + "  var /** !String */ x;\n"
        + "  for (x in { p1: 123, p2: 234 }) ;\n"
        + "}");

    checkNoWarnings("function f(/** !String */ x) {\n"
        + "  return x + 1;\n"
        + "}");
  }

  public void testAutoconvertBoxedBooleanToBoolean() {
    typeCheck(
        "var /** !Boolean */ b = true;", NewTypeInference.MISTYPED_ASSIGN_RHS);

    typeCheck(
        "var /** boolean */ b = new Boolean(true);",
        NewTypeInference.MISTYPED_ASSIGN_RHS);

    checkNoWarnings("function f(/** !Boolean */ x) {\n"
        + "  if (x) { return 123; };\n"
        + "}");
  }

  public void testAutoconvertScalarsToBoxedScalars() {
    checkNoWarnings("var /** number */ n = 123;\n"
        + "n.toString();");

    checkNoWarnings("var /** boolean */ b = true;\n"
        + "b.toString();");

    checkNoWarnings("var /** string */ s = '';\n"
        + "s.toString();");

    typeCheck(
        "var /** number */ n = 123;\n"
        + "n.prop = 0;\n"
        + "n.prop - 5;",
        TypeCheck.INEXISTENT_PROPERTY);

    checkNoWarnings("var /** number */ n = 123;\n"
        + "n['to' + 'String'];");

    checkNoWarnings("/** @constructor */\n"
        + "function Foo() {\n"
        + "  /** @type {number} */\n"
        + "  this.prop = 123;\n"
        + "}\n"
        + "(new Foo).prop.newprop = 5;");

    typeCheck(
        "/** @enum */\n"
        + "var E = { A: 1 };\n"
        + "function f(/** E */ x) {\n"
        + "  return x.toString();\n"
        + "}",
        NewTypeInference.PROPERTY_ACCESS_ON_NONOBJECT);

    checkNoWarnings("/** @constructor */\n"
        + "function Foo() {}\n"
        + "Foo.prototype.toString = function() {};\n"
        + "function f(/** (number|!Foo) */ x) {\n"
        + "  return x.toString();\n"
        + "}");
  }

  public void testConstructorsCalledWithoutNew() {
    checkNoWarnings("var n = new Number();\n"
        + "n.prop = 0;\n"
        + "n.prop - 5;");

    typeCheck(
        "var n = Number();\n"
        + "n.prop = 0;\n"
        + "n.prop - 5;",
        TypeCheck.INEXISTENT_PROPERTY);

    checkNoWarnings(
        "/** @constructor @return {number} */ function Foo(){ return 5; }\n"
        + "var /** !Foo */ f = new Foo;\n"
        + "var /** number */ n = Foo();");

    typeCheck(
        "/** @constructor */ function Foo(){ return 5; }\n"
        + "var /** !Foo */ f = new Foo;\n"
        + "var n = Foo();",
        TypeCheck.CONSTRUCTOR_NOT_CALLABLE);

    // For constructors, return of ? is interpreted the same as undeclared
    typeCheck(
        "/** @constructor @return {?} */ function Foo(){}\n"
        + "var /** !Foo */ f = new Foo;\n"
        + "var n = Foo();",
        TypeCheck.CONSTRUCTOR_NOT_CALLABLE);
  }

  public void testFunctionBind() {
    typeCheck( // Don't handle specially
        "var obj = { bind: function() { return 'asdf'; } };\n"
        + "obj.bind() - 5;",
        NewTypeInference.INVALID_OPERAND_TYPE);

    typeCheck(
        "function f(x) { return x; }\n"
        + "f.bind(null, 1, 2);",
        TypeCheck.WRONG_ARGUMENT_COUNT);

    typeCheck(
        "function f(x) { return x; }\n"
        + "f.bind();",
        TypeCheck.WRONG_ARGUMENT_COUNT);

    typeCheck(
        "function f(x) { return x - 1; }\n"
        + "var g = f.bind(null);\n"
        + "g();",
        TypeCheck.WRONG_ARGUMENT_COUNT);

    typeCheck(
        "function f() {}\n"
        + "f.bind(1);",
        NewTypeInference.INVALID_THIS_TYPE_IN_BIND);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "/** @constructor */\n"
        + "function Bar() {}\n"
        + "/** @this {!Foo} */\n"
        + "function f() {}\n"
        + "f.bind(new Bar);",
        NewTypeInference.INVALID_THIS_TYPE_IN_BIND);

    typeCheck(
        "function f(/** number */ x, /** number */ y) { return x - y; }\n"
        + "var g = f.bind(null, 123);\n"
        + "g('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(x, y) { return x - y; }\n"
        + "var g = f.bind(null, 123);\n"
        + "g('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(/** number */ x) { return x - 1; }\n"
        + "var g = f.bind(null, 'asdf');\n"
        + "g() - 3;",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @param {number=} x */\n"
        + "function f(x) {}\n"
        + "var g = f.bind(null);\n"
        + "g();\n"
        + "g('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "/** @param {...number} var_args */\n"
        + "function f(var_args) {}\n"
        + "var g = f.bind(null);\n"
        + "g();\n"
        + "g(123, 'asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("/** @param {number=} x */\n"
        + "function f(x) {}\n"
        + "f.bind(null, undefined);");

    checkNoWarnings("/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {T} y\n"
        + " */\n"
        + "function f(x, y) {}\n"
        + "f.bind(null, 1, 2);");

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {T} y\n"
        + " */\n"
        + "function f(x, y) {}\n"
        + "var g = f.bind(null, 1);\n"
        + "g(2);\n"
        + "g('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings( // T was instantiated to ? in the f.bind call.
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {T} y\n"
        + " */\n"
        + "function f(x, y) {}\n"
        + "var g = f.bind(null);\n"
        + "g(2, 'asdf');");

    typeCheck(
        "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {T} y\n"
        + " */\n"
        + "function f(x, y) {}\n"
        + "f.bind(null, 1, 'asdf');",
        NewTypeInference.NOT_UNIQUE_INSTANTIATION);

    typeCheck(
        "/**\n"
        + " * @constructor\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " */\n"
        + " function Foo(x) {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @this {Foo<T>}\n"
        + " * @param {T} x\n"
        + " * @param {T} y\n"
        + " */\n"
        + "function f(x, y) {}\n"
        + "f.bind(new Foo('asdf'), 1, 2);",
        NewTypeInference.INVALID_THIS_TYPE_IN_BIND);

    typeCheck(
        "/**\n"
        + " * @constructor\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " */\n"
        + " function Foo(x) {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " * @param {T} y\n"
        + " */\n"
        + "Foo.prototype.f = function(x, y) {};\n"
        + "Foo.prototype.f.bind(new Foo('asdf'), 1, 2);",
        NewTypeInference.INVALID_THIS_TYPE_IN_BIND);

    typeCheck(
        "/** @constructor */\n"
        + "function Foo() {}\n"
        + "Foo.bind(new Foo);",
        NewTypeInference.CANNOT_BIND_CTOR);

    checkNoWarnings( // We can't detect that f takes a string
        "/**\n"
        + " * @constructor\n"
        + " * @template T\n"
        + " * @param {T} x\n"
        + " */\n"
        + "function Foo(x) {}\n"
        + "/**\n"
        + " * @template T\n"
        + " * @this {Foo<T>}\n"
        + " * @param {T} x\n"
        + " */\n"
        + "function poly(x) {}\n"
        + "function f(x) {\n"
        + "  poly.bind(new Foo('asdf'), x);\n"
        + "}\n"
        + "f(123);");
  }

  public void testClosureStyleFunctionBind() {
    typeCheck(
        "goog.bind(123, null);", NewTypeInference.GOOG_BIND_EXPECTS_FUNCTION);

    typeCheck(
        "function f(x) { return x; }\n"
        + "goog.bind(f, null, 1, 2);",
        TypeCheck.WRONG_ARGUMENT_COUNT);

    typeCheck(
        "function f(x) { return x; }\n"
        + "goog.bind(f);",
        TypeCheck.WRONG_ARGUMENT_COUNT);

    typeCheck(
        "function f() {}\n"
        + "goog.bind(f, 1);",
        NewTypeInference.INVALID_THIS_TYPE_IN_BIND);

    typeCheck(
        "function f(/** number */ x, /** number */ y) { return x - y; }\n"
        + "var g = goog.bind(f, null, 123);\n"
        + "g('asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(/** number */ x) { return x - 1; }\n"
        + "var g = goog.partial(f, 'asdf');",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    typeCheck(
        "function f(/** number */ x) { return x - 1; }\n"
        + "var g = goog.partial(f, 'asdf');\n"
        + "g() - 3;",
        NewTypeInference.INVALID_ARGUMENT_TYPE);

    checkNoWarnings("function f(x) {\n"
        + "  if (typeof x == 'function') {\n"
        + "    goog.bind(x, {}, 1, 2);\n"
        + "  }\n"
        + "}");
  }
}
