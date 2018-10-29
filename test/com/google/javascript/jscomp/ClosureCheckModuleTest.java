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

import static com.google.javascript.jscomp.ClosureCheckModule.DECLARE_LEGACY_NAMESPACE_IN_NON_MODULE;
import static com.google.javascript.jscomp.ClosureCheckModule.DUPLICATE_NAME_SHORT_REQUIRE;
import static com.google.javascript.jscomp.ClosureCheckModule.EXPORT_NOT_AT_MODULE_SCOPE;
import static com.google.javascript.jscomp.ClosureCheckModule.EXPORT_NOT_A_STATEMENT;
import static com.google.javascript.jscomp.ClosureCheckModule.EXPORT_REPEATED_ERROR;
import static com.google.javascript.jscomp.ClosureCheckModule.GOOG_MODULE_REFERENCES_THIS;
import static com.google.javascript.jscomp.ClosureCheckModule.GOOG_MODULE_USES_THROW;
import static com.google.javascript.jscomp.ClosureCheckModule.INCORRECT_SHORTNAME_CAPITALIZATION;
import static com.google.javascript.jscomp.ClosureCheckModule.INVALID_DESTRUCTURING_REQUIRE;
import static com.google.javascript.jscomp.ClosureCheckModule.JSDOC_REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME;
import static com.google.javascript.jscomp.ClosureCheckModule.LET_GOOG_REQUIRE;
import static com.google.javascript.jscomp.ClosureCheckModule.MODULE_AND_PROVIDES;
import static com.google.javascript.jscomp.ClosureCheckModule.MULTIPLE_MODULES_IN_FILE;
import static com.google.javascript.jscomp.ClosureCheckModule.ONE_REQUIRE_PER_DECLARATION;
import static com.google.javascript.jscomp.ClosureCheckModule.REFERENCE_TO_FULLY_QUALIFIED_IMPORT_NAME;
import static com.google.javascript.jscomp.ClosureCheckModule.REFERENCE_TO_MODULE_GLOBAL_NAME;
import static com.google.javascript.jscomp.ClosureCheckModule.REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME;
import static com.google.javascript.jscomp.ClosureCheckModule.REQUIRE_NOT_AT_TOP_LEVEL;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_DESTRUCTURING_FORWARD_DECLARE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.MODULE_USES_GOOG_MODULE_GET;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ClosureCheckModuleTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ClosureCheckModule(compiler);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setLanguage(LanguageMode.ECMASCRIPT_NEXT, LanguageMode.ECMASCRIPT_NEXT);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.ERROR);
    return options;
  }

  @Test
  public void testGoogModuleReferencesThis() {
    testError("goog.module('xyz');\nfoo.call(this, 1, 2, 3);", GOOG_MODULE_REFERENCES_THIS);

    testError(
        lines(
            "goog.module('xyz');",
            "",
            "var x = goog.require('other.x');",
            "",
            "if (x) {",
            "  alert(this);",
            "}"),
        GOOG_MODULE_REFERENCES_THIS);

    testSame(
        lines(
            "goog.module('xyz');",
            "",
            "class Foo {",
            "  constructor() {",
            "    this.x = 5;",
            "  }",
            "}",
            "",
            "exports = Foo;"));
  }

  @Test
  public void testGoogModuleUsesThrow() {
    testError("goog.module('xyz');\nthrow 4;", GOOG_MODULE_USES_THROW);

    testError(
        lines(
            "goog.module('xyz');",
            "",
            "var x = goog.require('other.x');",
            "",
            "if (x) {",
            "  throw 5;",
            "}"),
        GOOG_MODULE_USES_THROW);
  }

  @Test
  public void testGoogModuleGetAtTopLevel() {
    testError("goog.module('xyz');\ngoog.module.get('abc');", MODULE_USES_GOOG_MODULE_GET);

    testError(
        lines(
            "goog.module('xyz');",
            "",
            "var x = goog.require('other.x');",
            "",
            "if (x) {",
            "  var y = goog.module.get('abc');",
            "}"),
        MODULE_USES_GOOG_MODULE_GET);

    testSame(
        lines(
            "goog.module('xyz');",
            "",
            "var x = goog.require('other.x');",
            "",
            "function f() {",
            "  var y = goog.module.get('abc');",
            "}"));
  }

  @Test
  public void testGoogModuleAndProvide() {
    testError("goog.module('xyz');\ngoog.provide('abc');", MODULE_AND_PROVIDES);
  }

  @Test
  public void testMultipleGoogModules() {
    testError(
        lines(
            "goog.module('xyz');",
            "goog.module('abc');",
            "",
            "var x = goog.require('other.x');"),
        MULTIPLE_MODULES_IN_FILE);
  }

  @Test
  public void testBundledGoogModules() {
    testError(
        lines(
            "goog.loadModule(function(exports){",
            "  'use strict';",
            "  goog.module('xyz');",
            "  foo.call(this, 1, 2, 3);",
            "  return exports;",
            "});"),
        GOOG_MODULE_REFERENCES_THIS);

    testError(
        lines(
            "goog.loadModule(function(exports){",
            "  'use strict';",
            "  goog.module('foo.example.ClassName');",
            "  /** @constructor @export */ function ClassName() {}",
            "  exports = ClassName;",
            "  return exports;",
            "});"),
        ClosureCheckModule.AT_EXPORT_IN_GOOG_MODULE);

    testSame(
        lines(
            "goog.loadModule(function(exports){",
            "  'use strict';",
            "  goog.module('Xyz');",
            "  exports = class {}",
            "  return exports;",
            "});",
            "goog.loadModule(function(exports){",
            "  goog.module('abc');",
            "  var Foo = goog.require('Xyz');",
            "  var x = new Foo;",
            "  return exports;",
            "});"));

    testError(
        lines(
            "goog.loadModule(function(exports){",
            "  'use strict';",
            "  goog.module('xyz');",
            "  goog.module('abc');",
            "  var x = goog.require('other.x');",
            "  return exports;",
            "});"),
        MULTIPLE_MODULES_IN_FILE);
  }

  @Test
  public void testGoogModuleReferencesGlobalName() {
    testError("goog.module('x.y.z');\nx.y.z = function() {};", REFERENCE_TO_MODULE_GLOBAL_NAME);
  }

  @Test
  public void testIllegalAtExport() {
    testError(
        lines(
            "goog.module('foo.example.ClassName');",
            "",
            "/** @constructor @export */ function ClassName() {}",
            "",
            "exports = ClassName;"),
        ClosureCheckModule.AT_EXPORT_IN_GOOG_MODULE);

    testError(
        lines(
            "goog.module('foo.example.ClassName');",
            "",
            "/** @export */ class ClassName {}",
            "",
            "exports = ClassName;"),
        ClosureCheckModule.AT_EXPORT_IN_GOOG_MODULE);

    testError(
        lines(
            "goog.module('foo.example.ClassName');",
            "",
            "/** @constructor */ function ClassName() {}",
            "",
            "/** @export */",
            "exports = ClassName;"),
        ClosureCheckModule.AT_EXPORT_IN_NON_LEGACY_GOOG_MODULE);

    testError(
        lines(
            "goog.module('foo.example.ns');",
            "",
            "/** @constructor */ function ClassName() {}",
            "",
            "/** @export */",
            "exports.ClassName = ClassName;"),
        ClosureCheckModule.AT_EXPORT_IN_NON_LEGACY_GOOG_MODULE);

    testError(
        lines(
            "goog.module('foo.example.ClassName');",
            "goog.module.declareLegacyNamespace();",
            "",
            "/** @constructor @export */ function ClassName() {}",
            "",
            "exports = ClassName;"),
        ClosureCheckModule.AT_EXPORT_IN_GOOG_MODULE);
  }

  @Test
  public void testLegalAtExport() {
    testSame(
        lines(
            "goog.module('foo.example.ClassName');",
            "",
            "class ClassName {",
            "  constructor() {",
            "    /** @export */",
            "    this.prop;",
            "    /** @export */",
            "    this.anotherProp = false;",
            "  }",
            "}",
            "",
            "exports = ClassName;"));

    testSame(
        lines(
            "goog.module('foo.example.ClassName');",
            "",
            "var ClassName = class {",
            "  constructor() {",
            "    /** @export */",
            "    this.prop;",
            "    /** @export */",
            "    this.anotherProp = false;",
            "  }",
            "}",
            "",
            "exports = ClassName;"));

    testSame(
        lines(
            "goog.module('foo.example.ClassName');",
            "",
            "/** @constructor */",
            "function ClassName() {",
            "  /** @export */",
            "  this.prop;",
            "  /** @export */",
            "  this.anotherProp = false;",
            "}",
            "",
            "exports = ClassName;"));

    testSame(
        lines(
            "goog.module('foo.example.ClassName');",
            "",
            "/** @constructor */",
            "var ClassName = function() {",
            "  /** @export */",
            "  this.prop;",
            "  /** @export */",
            "  this.anotherProp = false;",
            "};",
            "",
            "exports = ClassName;"));

    testSame(
        lines(
            "goog.module('foo.example.ClassName');",
            "goog.module.declareLegacyNamespace();",
            "",
            "/** @constructor */ function ClassName() {}",
            "",
            "/** @export */",
            "exports = ClassName;"));

    testSame(
        lines(
            "goog.module('foo.example.ns');",
            "goog.module.declareLegacyNamespace();",
            "",
            "/** @constructor */ function ClassName() {}",
            "",
            "/** @export */",
            "exports.ClassName = ClassName;"));

    testSame(
        lines(
            "goog.module('foo.example.ClassName');",
            "",
            "/** @constructor */ var exports = function() {}",
            "",
            "/** @export */",
            "exports.prototype.fly = function() {};"));
  }

  @Test
  public void testIllegalDeclareLegacyNamespace() {
    testError(
        lines(
            "goog.provide('a.provided.namespace');",
            "goog.module.declareLegacyNamespace();"),
        DECLARE_LEGACY_NAMESPACE_IN_NON_MODULE);
  }

  @Test
  public void testIllegalGoogRequires() {
    testError(
        lines(
            "goog.module('xyz');",
            "",
            "var foo = goog.require('other.x').foo;"),
        REQUIRE_NOT_AT_TOP_LEVEL);

    testError(
        lines(
            "goog.module('xyz');",
            "",
            "var x = goog.require('other.x').foo.toString();"),
        REQUIRE_NOT_AT_TOP_LEVEL);

    testError(
        lines(
            "goog.module('xyz');",
            "",
            "var moduleNames = [goog.require('other.x').name];"),
        REQUIRE_NOT_AT_TOP_LEVEL);

    testError(
        lines(
            "goog.module('xyz');",
            "",
            "exports = [goog.require('other.x').name];"),
        REQUIRE_NOT_AT_TOP_LEVEL);

    testError(
        lines(
            "goog.module('xyz');",
            "",
            "var a = goog.require('foo.a'), b = goog.require('foo.b');"),
        ONE_REQUIRE_PER_DECLARATION);

    testError(
        lines(
            "goog.module('xyz');",
            "",
            "var [foo, bar] = goog.require('other.x');"),
        INVALID_DESTRUCTURING_REQUIRE);

    testError(
        lines(
            "goog.module('xyz');",
            "",
            "var {foo, bar = 'str'} = goog.require('other.x');"),
        INVALID_DESTRUCTURING_REQUIRE);

    testError(
        lines(
            "goog.module('xyz');",
            "",
            "var {foo, bar: {name}} = goog.require('other.x');"),
        INVALID_DESTRUCTURING_REQUIRE);

    testError(
        lines(
            "goog.module('xyz');",
            "",
            "var foo = goog.require('abc.foo');",
            "var foo = goog.require('def.foo');"),
        DUPLICATE_NAME_SHORT_REQUIRE);

    testError(
        lines(
            "goog.module('xyz');",
            "",
            "var {foo, bar} = goog.require('abc');",
            "var foo = goog.require('def.foo');"),
        DUPLICATE_NAME_SHORT_REQUIRE);

    testError(
        lines(
            "goog.module('xyz');",
            "",
            "const localName = goog.require(namespace.without.the.quotes);"),
        ProcessClosurePrimitives.INVALID_ARGUMENT_ERROR);

    testError(
        lines(
            "goog.module('xyz');",
            "",
            "goog.require(namespace.without.the.quotes);"),
        ProcessClosurePrimitives.INVALID_ARGUMENT_ERROR);
  }

  @Test
  public void testIllegalShortImportReferencedByLongName() {
    testError(
        lines(
            "goog.module('x.y.z');",
            "",
            "var A = goog.require('foo.A');",
            "",
            "exports = function() { return new foo.A; };"),
        REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME);
  }

  @Test
  public void testIllegalShortImportReferencedByLongName_extends() {
    testError(
        lines(
            "goog.module('x.y.z');",
            "",
            "var A = goog.require('foo.A');",
            "",
            "/** @constructor @implements {foo.A} */ function B() {}"),
        JSDOC_REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME);

    testError(
        lines(
            "goog.module('x.y.z');",
            "",
            "var A = goog.require('foo.A');",
            "",
            "/** @type {foo.A} */ var a;"),
        JSDOC_REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME);

    testSame(
        lines(
            "goog.module('x.y.z');",
            "",
            "var A = goog.require('foo.A');",
            "",
            "/** @type {A} */ var a;"));

    testSame(
        lines(
            "goog.module('x.y.z');",
            "",
            "var Foo = goog.require('Foo');",
            "",
            "/** @type {Foo} */ var a;"));

    testError(
        lines(
            "goog.module('x.y.z');",
            "",
            "var ns = goog.require('some.namespace');",
            "",
            "/** @type {some.namespace.Foo} */ var foo;"),
        JSDOC_REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME);

    testError(
        lines(
            "goog.module('x.y.z');",
            "",
            "var ns = goog.require('some.namespace');",
            "",
            "/** @type {Array<some.namespace.Foo>} */ var foos;"),
        JSDOC_REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME);
  }

  @Test
  public void testIllegalShortImportDestructuring() {
    testError(
        lines(
            "goog.module('x.y.z');",
            "",
            "var {doThing} = goog.require('foo.utils');",
            "",
            "exports = function() { return foo.utils.doThing(''); };"),
        REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME);

    testError(
        lines(
            "goog.module('x.y.z');",
            "",
            "var {doThing: fooDoThing} = goog.require('foo.utils');",
            "",
            "exports = function() { return foo.utils.doThing(''); };"),
        REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME);
  }

  @Test
  public void testIllegalImportNoAlias() {
    testError(
        lines(
            "goog.module('x.y.z');",
            "",
            "goog.require('foo.utils');",
            "",
            "exports = function() { return foo.utils.doThing(''); };"),
        REFERENCE_TO_FULLY_QUALIFIED_IMPORT_NAME);
  }

  // TODO(johnlenz): Re-enable these tests (they are a bit tricky).
  public void disable_testSingleNameImportNoAlias1() {
    testError(
        lines(
            "goog.module('x.y.z');",
            "",
            "goog.require('foo');",
            "",
            "exports = function() { return foo.doThing(''); };"),
        REFERENCE_TO_FULLY_QUALIFIED_IMPORT_NAME);
  }

  public void disable_testSingleNameImportWithAlias() {
    testError(
        lines(
            "goog.module('x.y.z');",
            "",
            "var bar = goog.require('foo');",
            "",
            "exports = function() { return foo.doThing(''); };"),
        REFERENCE_TO_FULLY_QUALIFIED_IMPORT_NAME);
  }

  @Test
  public void testSingleNameImportCrossAlias() {
    testSame(
        lines(
            "goog.module('x.y.z');",
            "",
            "var bar = goog.require('foo');",
            "var foo = goog.require('bar');",
            "",
            "exports = function() { return foo.doThing(''); };"));
  }

  @Test
  public void testLegalSingleNameImport() {
    testSame(
        lines(
            "goog.module('x.y.z');",
            "",
            "var foo = goog.require('foo');",
            "",
            "exports = function() { return foo.doThing(''); };"));
  }

  @Test
  public void testIllegalLetShortRequire() {
    testSame(
        lines(
            "goog.module('xyz');",
            "",
            "let a = goog.forwardDeclare('foo.a');"));

    testError(
        lines(
            "goog.module('xyz');",
            "",
            "let a = goog.require('foo.a');"),
        LET_GOOG_REQUIRE);
  }

  @Test
  public void testIllegalDestructuringForwardDeclare() {
    testError(
        "goog.module('m'); var {x} = goog.forwardDeclare('a.b.c');",
        INVALID_DESTRUCTURING_FORWARD_DECLARE);
  }

  @Test
  public void testLegalGoogRequires() {
    testSame(
        lines(
            "goog.module('xyz');",
            "",
            "var {assert} = goog.require('goog.asserts');"));

    testSame(
        lines(
            "goog.module('xyz');",
            "",
            "const {assert} = goog.require('goog.asserts');"));

    testSame(
        lines(
            "goog.module('xyz');",
            "",
            "const {assert, fail} = goog.require('goog.asserts');"));
  }

  @Test
  public void testShorthandNameConvention() {
    testSame(
        lines(
            "goog.module('xyz');",
            "",
            "const googAsserts = goog.require('goog.asserts');"));

    testError(
        lines(
            "goog.module('xyz');",
            "",
            "const GoogAsserts = goog.require('goog.asserts');"),
        INCORRECT_SHORTNAME_CAPITALIZATION,
        "The capitalization of short name GoogAsserts is incorrect; it should be googAsserts.");

    testSame(
        lines(
            "goog.module('xyz');",
            "",
            "const Event = goog.require('goog.events.Event');"));

    testError(
        lines(
            "goog.module('xyz');",
            "",
            "const event = goog.require('goog.events.Event');"),
        INCORRECT_SHORTNAME_CAPITALIZATION,
        "The capitalization of short name event is incorrect; it should be Event.");
  }

  @Test
  public void testNonModuleLevelExports() {
    testError(
        lines("goog.module('xyz');", "", "if (window.exportMe) { exports = 5; }"),
        EXPORT_NOT_AT_MODULE_SCOPE);

    testError(
        lines("goog.module('xyz');", "", "window.exportMe && (exports = 5);"),
        EXPORT_NOT_A_STATEMENT);

    testError(
        lines("goog.module('xyz');", "", "exports.f = () => { exports.me = 5; }"),
        EXPORT_NOT_AT_MODULE_SCOPE);

    testSame(
        lines(
            "goog.module('xyz');",
            "",
            "exports = class {};",
            "exports.staticMethod = () => { exports.me = 5; }"));
  }

  @Test
  public void testRepeatedExports() {
    testError(
        lines("goog.module('xyz');", "", "exports = 5;", "exports = 'str';"),
        EXPORT_REPEATED_ERROR);

    testError(
        lines("goog.module('xyz');", "", "exports.y = 5;", "exports.y = 'str';"),
        EXPORT_REPEATED_ERROR);

    testSame(
        lines(
            "goog.module('xyz');",
            "",
            "exports = class {};",
            "exports.y = 'str';",
            "exports.y = decorate(exports.y);"));

    // This pattern is used by typescript in a way that won't violate our goog.module assumptions.
    testSame(
        lines(
            "/** @fileoverview @suppress {googModuleExportNotAStatement} */",
            "goog.module('xyz');",
            "",
            "((x) => {})(exports.y || (exports.y = {}));",
            "",
            "((x) => {})(exports.y || (exports.y = {}));",
            ""));
  }

  @Test
  public void testDontCrashOnTrailingDot() {
    testSame(
        lines(
            "goog.module('foo');",
            "",
            "var a = goog.require('abc.');"));
  }
}
