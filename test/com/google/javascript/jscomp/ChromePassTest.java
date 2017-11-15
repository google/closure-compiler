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
package com.google.javascript.jscomp;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/** Tests {@link ChromePass}. */
public class ChromePassTest extends CompilerTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    setLanguage(LanguageMode.ECMASCRIPT_2017, LanguageMode.ECMASCRIPT_2017);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ChromePass(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    // This pass isn't idempotent and only runs once.
    return 1;
  }

  public void testCrDefineCreatesObjectsForQualifiedName() throws Exception {
    test(
        "cr.define('my.namespace.name', function() {\n" + "  return {};\n" + "});",
        "var my = my || {};\n"
            + "my.namespace = my.namespace || {};\n"
            + "my.namespace.name = my.namespace.name || {};\n"
            + "cr.define('my.namespace.name', function() {\n"
            + "  return {};\n"
            + "});");
  }

  public void testChromePassIgnoresModules() throws Exception {
    testSame("export var x;");
  }

  public void testCrDefineAssignsExportedFunctionByQualifiedName() throws Exception {
    test(
        "cr.define('namespace', function() {\n"
            + "  function internalStaticMethod() {\n"
            + "    alert(42);\n"
            + "  }\n"
            + "  return {\n"
            + "    externalStaticMethod: internalStaticMethod\n"
            + "  };\n"
            + "});",
        "var namespace = namespace || {};\n"
            + "cr.define('namespace', function() {\n"
            + "  namespace.externalStaticMethod = function internalStaticMethod() {\n"
            + "    alert(42);\n"
            + "  }\n"
            + "  return {\n"
            + "    externalStaticMethod: namespace.externalStaticMethod\n"
            + "  };\n"
            + "});");
  }

  public void testCrDefineCopiesJSDocForExportedFunction() throws Exception {
    test(
        "cr.define('namespace', function() {\n"
            + "  /** I'm function's JSDoc */\n"
            + "  function internalStaticMethod() {\n"
            + "    alert(42);\n"
            + "  }\n"
            + "  return {\n"
            + "    externalStaticMethod: internalStaticMethod\n"
            + "  };\n"
            + "});",
        "var namespace = namespace || {};\n"
            + "cr.define('namespace', function() {\n"
            + "  /** I'm function's JSDoc */\n"
            + "  namespace.externalStaticMethod = function internalStaticMethod() {\n"
            + "    alert(42);\n"
            + "  }\n"
            + "  return {\n"
            + "    externalStaticMethod: namespace.externalStaticMethod\n"
            + "  };\n"
            + "});");
  }

  public void testCrDefineReassignsExportedVarByQualifiedName() throws Exception {
    test(
        "cr.define('namespace', function() {\n"
            + "  var internalStaticMethod = function() {\n"
            + "    alert(42);\n"
            + "  }\n"
            + "  return {\n"
            + "    externalStaticMethod: internalStaticMethod\n"
            + "  };\n"
            + "});",
        "var namespace = namespace || {};\n"
            + "cr.define('namespace', function() {\n"
            + "  namespace.externalStaticMethod = function() {\n"
            + "    alert(42);\n"
            + "  }\n"
            + "  return {\n"
            + "    externalStaticMethod: namespace.externalStaticMethod\n"
            + "  };\n"
            + "});");
  }

  public void testCrDefineExportsVarsWithoutAssignment() throws Exception {
    test(
        "cr.define('namespace', function() {\n"
            + "  var a;\n"
            + "  return {\n"
            + "    a: a\n"
            + "  };\n"
            + "});\n",
        "var namespace = namespace || {};\n"
            + "cr.define('namespace', function() {\n"
            + "  namespace.a;\n"
            + "  return {\n"
            + "    a: namespace.a\n"
            + "  };\n"
            + "});\n");
  }

  public void testCrDefineExportsVarsWithoutAssignmentWithJSDoc() throws Exception {
    test(
        "cr.define('namespace', function() {\n"
            + "  /** @type {number} */\n"
            + "  var a;\n"
            + "  return {\n"
            + "    a: a\n"
            + "  };\n"
            + "});\n",
        "var namespace = namespace || {};\n"
            + "cr.define('namespace', function() {\n"
            + "  /** @type {number} */\n"
            + "  namespace.a;\n"
            + "  return {\n"
            + "    a: namespace.a\n"
            + "  };\n"
            + "});\n");
  }

  public void testCrDefineCopiesJSDocForExportedVariable() throws Exception {
    test(
        "cr.define('namespace', function() {\n"
            + "  /** I'm function's JSDoc */\n"
            + "  var internalStaticMethod = function() {\n"
            + "    alert(42);\n"
            + "  }\n"
            + "  return {\n"
            + "    externalStaticMethod: internalStaticMethod\n"
            + "  };\n"
            + "});",
        "var namespace = namespace || {};\n"
            + "cr.define('namespace', function() {\n"
            + "  /** I'm function's JSDoc */\n"
            + "  namespace.externalStaticMethod = function() {\n"
            + "    alert(42);\n"
            + "  }\n"
            + "  return {\n"
            + "    externalStaticMethod: namespace.externalStaticMethod\n"
            + "  };\n"
            + "});");
  }

  public void testCrDefineDoesNothingWithNonExportedFunction() throws Exception {
    test(
        "cr.define('namespace', function() {\n"
            + "  function internalStaticMethod() {\n"
            + "    alert(42);\n"
            + "  }\n"
            + "  return {};\n"
            + "});",
        "var namespace = namespace || {};\n"
            + "cr.define('namespace', function() {\n"
            + "  function internalStaticMethod() {\n"
            + "    alert(42);\n"
            + "  }\n"
            + "  return {};\n"
            + "});");
  }

  public void testCrDefineDoesNothingWithNonExportedVar() throws Exception {
    test(
        "cr.define('namespace', function() {\n"
            + "  var a;\n"
            + "  var b;\n"
            + "  return {\n"
            + "    a: a\n"
            + "  };\n"
            + "});\n",
        "var namespace = namespace || {};\n"
            + "cr.define('namespace', function() {\n"
            + "  namespace.a;\n"
            + "  var b;\n"
            + "  return {\n"
            + "    a: namespace.a\n"
            + "  };\n"
            + "});\n");
  }

  public void testCrDefineDoesNothingWithExportedNotAName() throws Exception {
    test(
        "cr.define('namespace', function() {\n"
            + "  return {\n"
            + "    a: 42\n"
            + "  };\n"
            + "});\n",
        "var namespace = namespace || {};\n"
            + "cr.define('namespace', function() {\n"
            + "  return {\n"
            + "    a: 42\n"
            + "  };\n"
            + "});\n");
  }

  public void testCrDefineChangesReferenceToExportedFunction() throws Exception {
    test(
        "cr.define('namespace', function() {\n"
            + "  function internalStaticMethod() {\n"
            + "    alert(42);\n"
            + "  }\n"
            + "  function letsUseIt() {\n"
            + "    internalStaticMethod();\n"
            + "  }\n"
            + "  return {\n"
            + "    externalStaticMethod: internalStaticMethod\n"
            + "  };\n"
            + "});",
        "var namespace = namespace || {};\n"
            + "cr.define('namespace', function() {\n"
            + "  namespace.externalStaticMethod = function internalStaticMethod() {\n"
            + "    alert(42);\n"
            + "  }\n"
            + "  function letsUseIt() {\n"
            + "    namespace.externalStaticMethod();\n"
            + "  }\n"
            + "  return {\n"
            + "    externalStaticMethod: namespace.externalStaticMethod\n"
            + "  };\n"
            + "});");
  }

  public void testCrDefineConstEnum() {
    test(
        lines(
            "cr.define('foo', function() {",
            "  /** ",
            "   * @enum {string}",
            "   */",
            "  const DangerType = {",
            "    NOT_DANGEROUS: 'NOT_DANGEROUS',",
            "    DANGEROUS: 'DANGEROUS',",
            "  };",
            "",
            "  return {",
            "    DangerType: DangerType,",
            "  };",
            "});"),
        lines(
            "var foo = foo || {};",
            "cr.define('foo', function() {",
            "  /** @enum {string} */",
            "  foo.DangerType = {",
            "    NOT_DANGEROUS:'NOT_DANGEROUS',",
            "    DANGEROUS:'DANGEROUS',",
            "  };",
            "",
            "  return {",
            "    DangerType: foo.DangerType",
            "  }",
            "})",
            ""));
  }

  public void testCrDefineLetEnum() {
    test(
        lines(
            "cr.define('foo', function() {",
            "  /** ",
            "   * @enum {string}",
            "   */",
            "  let DangerType = {",
            "    NOT_DANGEROUS: 'NOT_DANGEROUS',",
            "    DANGEROUS: 'DANGEROUS',",
            "  };",
            "",
            "  return {",
            "    DangerType: DangerType,",
            "  };",
            "});"),
        lines(
            "var foo = foo || {};",
            "cr.define('foo', function() {",
            "  /** @enum {string} */",
            "  foo.DangerType = {",
            "    NOT_DANGEROUS:'NOT_DANGEROUS',",
            "    DANGEROUS:'DANGEROUS',",
            "  };",
            "",
            "  return {",
            "    DangerType: foo.DangerType",
            "  }",
            "})",
            ""));
  }

  public void testCrDefineWrongNumberOfArguments() throws Exception {
    testError(
        "cr.define('namespace', function() { return {}; }, 'invalid argument')\n",
        ChromePass.CR_DEFINE_WRONG_NUMBER_OF_ARGUMENTS);
  }

  public void testCrDefineInvalidFirstArgument() throws Exception {
    testError(
        "cr.define(42, function() { return {}; })\n", ChromePass.CR_DEFINE_INVALID_FIRST_ARGUMENT);
  }

  public void testCrDefineInvalidSecondArgument() throws Exception {
    testError("cr.define('namespace', 42)\n", ChromePass.CR_DEFINE_INVALID_SECOND_ARGUMENT);
  }

  public void testCrDefineInvalidReturnInFunction() throws Exception {
    testError(
        "cr.define('namespace', function() {})\n", ChromePass.CR_DEFINE_INVALID_RETURN_IN_FUNCTION);
  }

  public void testObjectDefinePropertyDefinesUnquotedProperty() throws Exception {
    test(
        "Object.defineProperty(a.b, 'c', {});",
        "Object.defineProperty(a.b, 'c', {});\n" + "/** @type {?} */\n" + "a.b.c;");
  }

  public void testCrDefinePropertyDefinesUnquotedPropertyWithStringTypeForPropertyKindAttr()
      throws Exception {
    test(
        "cr.defineProperty(a.prototype, 'c', cr.PropertyKind.ATTR);",
        "cr.defineProperty(a.prototype, 'c', cr.PropertyKind.ATTR);\n"
            + "/** @type {string} */\n"
            + "a.prototype.c;");
  }

  public void testCrDefinePropertyDefinesUnquotedPropertyWithBooleanTypeForPropertyKindBoolAttr()
      throws Exception {
    test(
        "cr.defineProperty(a.prototype, 'c', cr.PropertyKind.BOOL_ATTR);",
        "cr.defineProperty(a.prototype, 'c', cr.PropertyKind.BOOL_ATTR);\n"
            + "/** @type {boolean} */\n"
            + "a.prototype.c;");
  }

  public void testCrDefinePropertyDefinesUnquotedPropertyWithAnyTypeForPropertyKindJs()
      throws Exception {
    test(
        "cr.defineProperty(a.prototype, 'c', cr.PropertyKind.JS);",
        "cr.defineProperty(a.prototype, 'c', cr.PropertyKind.JS);\n"
            + "/** @type {?} */\n"
            + "a.prototype.c;");
  }

  public void testCrDefinePropertyDefinesUnquotedPropertyWithTypeInfoForPropertyKindJs()
      throws Exception {
    test(
        lines(
            // @type starts here.
            "/** @type {!Object} */",
            "cr.defineProperty(a.prototype, 'c', cr.PropertyKind.JS);"),
        lines(
            "cr.defineProperty(a.prototype, 'c', cr.PropertyKind.JS);",
            // But gets moved here.
            "/** @type {!Object} */",
            "a.prototype.c;"));
  }

  public void testCrDefinePropertyDefinesUnquotedPropertyIgnoringJsDocWhenBoolAttrIsPresent()
      throws Exception {
    test(
        lines(
            // PropertyKind is used at runtime and is canonical. When it's specified, ignore @type.
            "/** @type {!Object} */",
            "cr.defineProperty(a.prototype, 'c', cr.PropertyKind.BOOL_ATTR);"),
        lines(
            "cr.defineProperty(a.prototype, 'c', cr.PropertyKind.BOOL_ATTR);",
            // @type is now {boolean}. Earlier, manually-specified @type is ignored.
            "/** @type {boolean} */",
            "a.prototype.c;"));
  }

  public void testCrDefinePropertyDefinesUnquotedPropertyIgnoringJsDocWhenAttrIsPresent()
      throws Exception {
    test(
        lines(
            // PropertyKind is used at runtime and is canonical. When it's specified, ignore @type.
            "/** @type {!Array} */",
            "cr.defineProperty(a.prototype, 'c', cr.PropertyKind.ATTR);"),
        lines(
            "cr.defineProperty(a.prototype, 'c', cr.PropertyKind.ATTR);",
            // @type is now {string}. Earlier, manually-specified @type is ignored.
            "/** @type {string} */",
            "a.prototype.c;"));
  }

  public void testCrDefinePropertyCalledWithouthThirdArgumentMeansCrPropertyKindJs()
      throws Exception {
    test(
        "cr.defineProperty(a.prototype, 'c');",
        "cr.defineProperty(a.prototype, 'c');\n" + "/** @type {?} */\n" + "a.prototype.c;");
  }

  public void testCrDefinePropertyDefinesUnquotedPropertyOnPrototypeWhenFunctionIsPassed()
      throws Exception {
    test(
        "cr.defineProperty(a, 'c', cr.PropertyKind.JS);",
        "cr.defineProperty(a, 'c', cr.PropertyKind.JS);\n"
            + "/** @type {?} */\n"
            + "a.prototype.c;");
  }

  public void testCrDefinePropertyInvalidPropertyKind() throws Exception {
    testError(
        "cr.defineProperty(a.b, 'c', cr.PropertyKind.INEXISTENT_KIND);",
        ChromePass.CR_DEFINE_PROPERTY_INVALID_PROPERTY_KIND);
  }

  public void testCrExportPath() throws Exception {
    test(
        "cr.exportPath('a.b.c');",
        "var a = a || {};\n"
            + "a.b = a.b || {};\n"
            + "a.b.c = a.b.c || {};\n"
            + "cr.exportPath('a.b.c');");
  }

  public void testCrDefineCreatesEveryObjectOnlyOnce() throws Exception {
    test(
        "cr.define('a.b.c.d', function() {\n"
            + "  return {};\n"
            + "});\n"
            + "cr.define('a.b.e.f', function() {\n"
            + "  return {};\n"
            + "});",
        "var a = a || {};\n"
            + "a.b = a.b || {};\n"
            + "a.b.c = a.b.c || {};\n"
            + "a.b.c.d = a.b.c.d || {};\n"
            + "cr.define('a.b.c.d', function() {\n"
            + "  return {};\n"
            + "});\n"
            + "a.b.e = a.b.e || {};\n"
            + "a.b.e.f = a.b.e.f || {};\n"
            + "cr.define('a.b.e.f', function() {\n"
            + "  return {};\n"
            + "});");
  }

  public void testCrDefineAndCrExportPathCreateEveryObjectOnlyOnce() throws Exception {
    test(
        "cr.exportPath('a.b.c.d');\n"
            + "cr.define('a.b.e.f', function() {\n"
            + "  return {};\n"
            + "});",
        "var a = a || {};\n"
            + "a.b = a.b || {};\n"
            + "a.b.c = a.b.c || {};\n"
            + "a.b.c.d = a.b.c.d || {};\n"
            + "cr.exportPath('a.b.c.d');\n"
            + "a.b.e = a.b.e || {};\n"
            + "a.b.e.f = a.b.e.f || {};\n"
            + "cr.define('a.b.e.f', function() {\n"
            + "  return {};\n"
            + "});");
  }

  public void testCrDefineDoesntRedefineCrVar() throws Exception {
    test(
        "cr.define('cr.ui', function() {\n" + "  return {};\n" + "});",
        "cr.ui = cr.ui || {};\n" + "cr.define('cr.ui', function() {\n" + "  return {};\n" + "});");
  }

  public void testCrDefineFunction() throws Exception {
    test(
        lines(
            "cr.define('settings', function() {",
            "  var x = 0;",
            "  function C() {}",
            "  return { C: C };",
            "});"),
        lines(
            "var settings = settings || {};",
            "cr.define('settings', function() {",
            "  var x = 0;",
            "  settings.C = function C() {};",
            "  return { C: settings.C };",
            "});"));
  }

  public void testCrDefineClassStatement() throws Exception {
    test(
        lines(
            "cr.define('settings', function() {",
            "  var x = 0;",
            "  class C {}",
            "  return { C: C };",
            "});"),
        lines(
            "var settings = settings || {};",
            "cr.define('settings', function() {",
            "  var x = 0;",
            "  settings.C = class {}",
            "  return { C: settings.C };",
            "});"));
  }

  public void testCrDefineClassExpression() throws Exception {
    test(
        lines(
            "cr.define('settings', function() {",
            "  var x = 0;",
            "  var C = class {}",
            "  return { C: C };",
            "});"),
        lines(
            "var settings = settings || {};",
            "cr.define('settings', function() {",
            "  var x = 0;",
            "  settings.C = class {}",
            "  return { C: settings.C };",
            "});"));
  }

  public void testCrDefineClassWithInternalSelfReference() throws Exception {
    test(
        lines(
            "cr.define('settings', function() {",
            "  var x = 0;",
            "  class C {",
            "    static create() { return new C; }",
            "  }",
            "  return { C: C };",
            "});"),
        lines(
            "var settings = settings || {};",
            "cr.define('settings', function() {",
            "  var x = 0;",
            "  settings.C = class {",
            "    static create() { return new settings.C; }",
            "  }",
            "  return { C: settings.C };",
            "});"));
  }

  public void testCrExportPathInvalidNumberOfArguments() throws Exception {
    testError("cr.exportPath();", ChromePass.CR_EXPORT_PATH_TOO_FEW_ARGUMENTS);
  }

  public void testCrMakePublicWorksOnOneMethodDefinedInPrototypeObject() throws Exception {
    test(
        "/** @constructor */\n"
            + "function Class() {};\n"
            + "\n"
            + "Class.prototype = {\n"
            + "  /** @return {number} */\n"
            + "  method_: function() { return 42; }\n"
            + "};\n"
            + "\n"
            + "cr.makePublic(Class, ['method']);",
        "/** @constructor */\n"
            + "function Class() {};\n"
            + "\n"
            + "Class.prototype = {\n"
            + "  /** @return {number} */\n"
            + "  method_: function() { return 42; }\n"
            + "};\n"
            + "\n"
            + "/** @return {number} */\n"
            + "Class.method;\n"
            + "\n"
            + "cr.makePublic(Class, ['method']);");
  }

  public void testCrMakePublicWorksOnTwoMethods() throws Exception {
    test(
        "/** @constructor */\n"
            + "function Class() {}\n"
            + "\n"
            + "Class.prototype = {\n"
            + "  /** @return {number} */\n"
            + "  m1_: function() { return 42; },\n"
            + "\n"
            + "  /** @return {string} */\n"
            + "  m2_: function() { return ''; }\n"
            + "};\n"
            + "\n"
            + "cr.makePublic(Class, ['m1', 'm2']);",
        "/** @constructor */\n"
            + "function Class() {}\n"
            + "\n"
            + "Class.prototype = {\n"
            + "  /** @return {number} */\n"
            + "  m1_: function() { return 42; },\n"
            + "\n"
            + "  /** @return {string} */\n"
            + "  m2_: function() { return ''; }\n"
            + "}\n"
            + "\n"
            + "/** @return {number} */\n"
            + "Class.m1;\n"
            + "\n"
            + "/** @return {string} */\n"
            + "Class.m2;\n"
            + "\n"
            + "cr.makePublic(Class, ['m1', 'm2']);");
  }

  public void testCrMakePublicRequiresMethodsToHaveJSDoc() throws Exception {
    testError(
        "/** @constructor */\n"
            + "function Class() {}\n"
            + "\n"
            + "Class.prototype = {\n"
            + "  method_: function() {}\n"
            + "}\n"
            + "\n"
            + "cr.makePublic(Class, ['method']);",
        ChromePass.CR_MAKE_PUBLIC_HAS_NO_JSDOC);
  }

  public void testCrMakePublicDoesNothingWithMethodsNotInAPI() throws Exception {
    test(
        "/** @constructor */\n"
            + "function Class() {}\n"
            + "\n"
            + "Class.prototype = {\n"
            + "  method_: function() {}\n"
            + "}\n"
            + "\n"
            + "cr.makePublic(Class, []);",
        "/** @constructor */\n"
            + "function Class() {}\n"
            + "\n"
            + "Class.prototype = {\n"
            + "  method_: function() {}\n"
            + "}\n"
            + "\n"
            + "cr.makePublic(Class, []);");
  }

  public void testCrMakePublicRequiresExportedMethodToBeDeclared() throws Exception {
    testError(
        "/** @constructor */\n"
            + "function Class() {}\n"
            + "\n"
            + "Class.prototype = {\n"
            + "}\n"
            + "\n"
            + "cr.makePublic(Class, ['method']);",
        ChromePass.CR_MAKE_PUBLIC_MISSED_DECLARATION);
  }

  public void testCrMakePublicWorksOnOneMethodDefinedDirectlyOnPrototype() throws Exception {
    test(
        "/** @constructor */\n"
            + "function Class() {}\n"
            + "\n"
            + "/** @return {number} */\n"
            + "Class.prototype.method_ = function() {};\n"
            + "\n"
            + "cr.makePublic(Class, ['method']);",
        "/** @constructor */\n"
            + "function Class() {}\n"
            + "\n"
            + "/** @return {number} */\n"
            + "Class.prototype.method_ = function() {};\n"
            + "\n"
            + "/** @return {number} */\n"
            + "Class.method;\n"
            + "\n"
            + "cr.makePublic(Class, ['method']);");
  }

  public void testCrMakePublicWorksOnDummyDeclaration() throws Exception {
    test(
        "/** @constructor */\n"
            + "function Class() {}\n"
            + "\n"
            + "/** @return {number} */\n"
            + "Class.prototype.method_;\n"
            + "\n"
            + "cr.makePublic(Class, ['method']);",
        "/** @constructor */\n"
            + "function Class() {}\n"
            + "\n"
            + "/** @return {number} */\n"
            + "Class.prototype.method_;\n"
            + "\n"
            + "/** @return {number} */\n"
            + "Class.method;\n"
            + "\n"
            + "cr.makePublic(Class, ['method']);");
  }

  public void testCrMakePublicReportsInvalidSecondArgumentMissing() throws Exception {
    testError("cr.makePublic(Class);", ChromePass.CR_MAKE_PUBLIC_INVALID_SECOND_ARGUMENT);
  }

  public void testCrMakePublicReportsInvalidSecondArgumentNotAnArray() throws Exception {
    testError("cr.makePublic(Class, 42);", ChromePass.CR_MAKE_PUBLIC_INVALID_SECOND_ARGUMENT);
  }

  public void testCrMakePublicReportsInvalidSecondArgumentArrayWithNotAString() throws Exception {
    testError("cr.makePublic(Class, [42]);", ChromePass.CR_MAKE_PUBLIC_INVALID_SECOND_ARGUMENT);
  }
}
