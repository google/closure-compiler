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

import static com.google.javascript.jscomp.ClosureRewriteModule.DUPLICATE_MODULE;
import static com.google.javascript.jscomp.ClosureRewriteModule.DUPLICATE_NAMESPACE;
import static com.google.javascript.jscomp.ClosureRewriteModule.ILLEGAL_DESTRUCTURING_DEFAULT_EXPORT;
import static com.google.javascript.jscomp.ClosureRewriteModule.ILLEGAL_DESTRUCTURING_NOT_EXPORTED;
import static com.google.javascript.jscomp.ClosureRewriteModule.IMPORT_INLINING_SHADOWS_VAR;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_EXPORT_COMPUTED_PROPERTY;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_FORWARD_DECLARE_NAMESPACE;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_GET_ALIAS;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_GET_CALL_SCOPE;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_GET_NAMESPACE;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_MODULE_NAMESPACE;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_PROVIDE_CALL;
import static com.google.javascript.jscomp.ClosureRewriteModule.INVALID_REQUIRE_NAMESPACE;
import static com.google.javascript.jscomp.ClosureRewriteModule.LATE_PROVIDE_ERROR;
import static com.google.javascript.jscomp.ClosureRewriteModule.MISSING_MODULE_OR_PROVIDE;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * Unit tests for ClosureRewriteModule
 * @author johnlenz@google.com (John Lenz)
 * @author stalcup@google.com (John Stalcup)
 */
public final class ClosureRewriteModuleTest extends CompilerTestCase {

  private boolean preserveClosurePrimitives = false;

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ClosureRewriteModule(compiler, null, null);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    preserveClosurePrimitives = false;
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    options.setPreserveClosurePrimitives(this.preserveClosurePrimitives);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROVIDE, CheckLevel.WARNING);
    return options;
  }

  public void testBasic0() {
    testSame("");
    testSame("goog.provide('a');");
  }

  public void testBasic1() {
    test(
        "goog.module('a');",

        "/** @const */ var module$exports$a = {};");
  }

  public void testBasic2() {
    test(
        new String[] {
            "goog.module('ns.b');",
            lines(
                "goog.module('ns.a');",
                "var b = goog.require('ns.b');")},

        new String[] {
            "/** @const */ var module$exports$ns$b = {};",
            "/** @const */ var module$exports$ns$a = {};"});
  }

  public void testBasic3() {
    // Multivar.
    test(
        new String[] {
            "goog.module('ns.b');",
            "goog.module('ns.c');",
            lines(
                "goog.module('ns.a');",
                "var b = goog.require('ns.b'), c = goog.require('ns.c');")},

        new String[] {
            "/** @const */ var module$exports$ns$b = {};",
            "/** @const */ var module$exports$ns$c = {};",
            "/** @const */ var module$exports$ns$a = {};"});
  }

  public void testIjsModule() {
    allowExternsChanges();
    test(
        // .i.js file
        externs("goog.module('external'); /** @constructor */ exports = function() {};"),
        // source file
        srcs("goog.module('ns.a'); var b = goog.require('external'); /** @type {b} */ new b;"),
        expected(
            lines(
                "/** @const */ var module$exports$ns$a = {};",
                "/** @type {module$exports$external} */ new module$exports$external")));
  }

  public void testDestructuringInsideModule() {
    // Array destrucuturing
    test(
        lines(
          "goog.module('a');",
          "var [x, y, z] = foo();"),
        lines(
          "/** @const */ var module$exports$a = {};",
          "var [module$contents$a_x, module$contents$a_y, module$contents$a_z] = foo();"));

    // Object destructuring with explicit names
    test(
        lines(
          "goog.module('a');",
          "var {p1: x, p2: y} = foo();"),
        lines(
          "/** @const */ var module$exports$a = {};",
          "var {p1: module$contents$a_x, p2: module$contents$a_y} = foo();"));

    // Object destructuring with short names
    test(
        lines(
          "goog.module('a');",
          "var {x, y} = foo();"),
        lines(
          "/** @const */ var module$exports$a = {};",
          "var {x: module$contents$a_x, y: module$contents$a_y} = foo();"));
  }

  public void testShortObjectLiteralsInsideModule() {
    test(
        lines(
          "goog.module('a');",
          "var x = foo();",
          "var o = {x};"),
        lines(
          "/** @const */ var module$exports$a = {};",
          "var module$contents$a_x = foo();",
          "var module$contents$a_o = {x: module$contents$a_x};"));

    test(
        lines(
          "goog.module('a');",
          "var x = foo();",
          "exports = {x};"),
        lines(
          "/** @const */ var module$exports$a = {};",
          "module$exports$a.x = foo();"));
  }

  public void testDestructuringImports() {
    test(
        new String[] {
          "goog.module('ns.b'); /** @constructor */ exports.Foo = function() {};",
          lines(
              "goog.module('ns.a');",
              "",
              "var {Foo} = goog.require('ns.b');",
              "",
              "/** @type {Foo} */",
              "var f = new Foo;")
        },
        new String[] {
          lines(
              "/** @const */ var module$exports$ns$b = {};",
              "/** @constructor @const */ module$exports$ns$b.Foo = function() {};"),
          lines(
              "/** @const */ var module$exports$ns$a = {}",
              "/** @type {module$exports$ns$b.Foo} */",
              "var module$contents$ns$a_f = new module$exports$ns$b.Foo;")});

    test(
        new String[] {
          "goog.module('ns.b'); /** @typedef {number} */ exports.Foo;",
          lines(
              "goog.module('ns.a');",
              "",
              "var {Foo} = goog.require('ns.b');",
              "",
              "/** @type {Foo} */",
              "var f = 4;")
        },
        new String[] {
          lines(
              "/** @const */ var module$exports$ns$b = {};",
              "/** @const @typedef {number} */ module$exports$ns$b.Foo;"),
          lines(
              "/** @const */ var module$exports$ns$a = {}",
              "/** @type {module$exports$ns$b.Foo} */",
              "var module$contents$ns$a_f = 4;")
        });

    test(
        new String[] {
          "goog.provide('ns.b'); /** @constructor */ ns.b.Foo = function() {};",
          lines(
              "goog.module('ns.a');",
              "",
              "var {Foo} = goog.require('ns.b');",
              "",
              "/** @type {Foo} */",
              "var f = new Foo;")
        },
        new String[] {
          "goog.provide('ns.b'); /** @constructor */ ns.b.Foo = function() {};",
          lines(
              "/** @const */ var module$exports$ns$a = {}",
              "/** @type {ns.b.Foo} */",
              "var module$contents$ns$a_f = new ns.b.Foo;")});

    test(
        new String[] {
          lines(
              "goog.module('ns.b');",
              "goog.module.declareLegacyNamespace();",
              "",
              "/** @constructor */ exports.Foo = function() {};"),
          lines(
              "goog.module('ns.a');",
              "",
              "var {Foo} = goog.require('ns.b');",
              "",
              "/** @type {Foo} */",
              "var f = new Foo;")
        },
        new String[] {
          lines(
              "goog.provide('ns.b');",
              "/** @constructor @const */ ns.b.Foo = function() {};"),
          lines(
              "/** @const */ var module$exports$ns$a = {}",
              "/** @type {ns.b.Foo} */",
              "var module$contents$ns$a_f = new ns.b.Foo;")});

    test(
        new String[] {
          "goog.module('ns.b'); /** @constructor */ exports.Foo = function() {};",
          lines(
              "goog.module('ns.a');",
              "",
              "var {Foo: Bar} = goog.require('ns.b');",
              "",
              "/** @type {Bar} */",
              "var f = new Bar;")
        },
        new String[] {
          lines(
              "/** @const */ var module$exports$ns$b = {};",
              "/** @constructor @const */ module$exports$ns$b.Foo = function() {};"),
          lines(
              "/** @const */ var module$exports$ns$a = {}",
              "/** @type {module$exports$ns$b.Foo} */",
              "var module$contents$ns$a_f = new module$exports$ns$b.Foo;")});

    test(
        new String[] {
          "goog.module('modA'); class Foo {} exports.Foo = Foo;",
          lines(
              "goog.module('modB');",
              "",
              "var {Foo:importedFoo} = goog.require('modA');",
              "",
              "/** @type {importedFoo} */",
              "var f = new importedFoo;")
        },
        new String[] {
          lines(
              "/** @const */ var module$exports$modA = {};",
              "module$exports$modA.Foo = class {}"),
          lines(
              "/** @const */ var module$exports$modB = {}",
              "/** @type {module$exports$modA.Foo} */",
              "var module$contents$modB_f = new module$exports$modA.Foo;")});

    test(
        new String[] {
          "goog.module('modA'); class Foo {} exports.Foo = Foo;",
          lines(
              "goog.module('modB');",
              "",
              "var {Foo} = goog.require('modA');",
              "",
              "/** @type {Foo} */",
              "var f = new Foo;")
        },
        new String[] {
          lines(
              "/** @const */ var module$exports$modA = {};",
              "module$exports$modA.Foo = class {}"),
          lines(
              "/** @const */ var module$exports$modB = {}",
              "/** @type {module$exports$modA.Foo} */",
              "var module$contents$modB_f = new module$exports$modA.Foo;")});

    test(
        new String[] {
          "goog.module('modA'); class Foo {} exports = {Foo};",
          lines(
              "goog.module('modB');",
              "",
              "var {Foo} = goog.require('modA');",
              "",
              "/** @type {Foo} */",
              "var f = new Foo;")
        },
        new String[] {
          lines(
              "/** @const */ var module$exports$modA = {};",
              "module$exports$modA.Foo = class {};"),
          lines(
              "/** @const */ var module$exports$modB = {}",
              "/** @type {module$exports$modA.Foo} */",
              "var module$contents$modB_f = new module$exports$modA.Foo;")});

    test(
        new String[] {
          "goog.module('modA'); class Bar {} exports = class Foo {}; exports.Bar = Bar;",
          lines(
              "goog.module('modB');",
              "",
              "var Foo = goog.require('modA');",
              "",
              "/** @type {Foo} */",
              "var f = new Foo;")
        },
        new String[] {
          lines(
              "class module$contents$modA_Bar {}",
              "/** @const */ var module$exports$modA = class Foo {};",
              "/** @const */ module$exports$modA.Bar = module$contents$modA_Bar;"),
          lines(
              "/** @const */ var module$exports$modB = {}",
              "/** @type {module$exports$modA} */",
              "var module$contents$modB_f = new module$exports$modA;")});

    test(
        new String[] {
          lines(
              "goog.module('modA');",
              "goog.module.declareLegacyNamespace();",
              "",
              "class Foo {}",
              "exports = {Foo};"),
          lines(
              "goog.module('modB');",
              "",
              "var {Foo} = goog.require('modA');",
              "",
              "/** @type {Foo} */",
              "var f = new Foo;")
        },
        new String[] {
          lines(
              "goog.provide('modA');",
              "class module$contents$modA_Foo {}",
              "/** @const */ modA.Foo = module$contents$modA_Foo;"),
          lines(
              "/** @const */ var module$exports$modB = {}",
              "/** @type {modA.Foo} */",
              "var module$contents$modB_f = new modA.Foo;")});
  }

  public void testUninlinableExports() {
    test(
        new String[] {
          "goog.module('ns.b'); /** @constructor */ exports.Foo = function() {};",
          lines(
              "goog.module('ns.a');",
              "",
              "var {Foo} = goog.require('ns.b');",
              "",
              "/** @type {Foo} */",
              "var f = new Foo;")
        },
        new String[] {
          lines(
              "/** @const */ var module$exports$ns$b = {};",
              "/** @const @constructor */ module$exports$ns$b.Foo = function() {};"),
          lines(
              "/** @const */ var module$exports$ns$a = {}",
              "/** @type {module$exports$ns$b.Foo} */",
              "var module$contents$ns$a_f = new module$exports$ns$b.Foo;")});
  }

  public void testObjectLiteralDefaultExport() {
    testError(
        new String[] {
          lines(
              "goog.module('modA');",
              "",
              "class Foo {}",
              "// This is not a named exports object because of the value literal",
              "exports = {Foo, Bar: [1,2,3]};"),
          lines("goog.module('modB');", "", "var {Foo} = goog.require('modA');")
        },
        ILLEGAL_DESTRUCTURING_DEFAULT_EXPORT);
  }

  public void testUninlinableNamedExports() {
    test(
        new String[] {
          "goog.module('modA'); \n exports = class {};",
          lines(
              "goog.module('modB');",
              "",
              "var Foo = goog.require('modA');",
              "",
              "exports.Foo = Foo;"),
        },
        new String[] {
          "/** @const */ var module$exports$modA = class {};",
          lines(
              "/** @const */ var module$exports$modB = {};",
              "/** @const */ module$exports$modB.Foo = module$exports$modA;"),
        });

    test(
        new String[] {
          "goog.module('modA'); \n exports = class {};",
          lines(
              "goog.module('modB');",
              "",
              "var Foo = goog.require('modA');",
              "",
              "exports = {Foo};"),
        },
        new String[] {
          "/** @const */ var module$exports$modA = class {};",
          lines(
              "/** @const */ var module$exports$modB = {};",
              "/** @const */ module$exports$modB.Foo = module$exports$modA;"),
        });

    test(
        new String[] {
          "goog.module('modA'); \n exports = class {};",
          lines(
              "goog.module('modB');",
              "",
              "var Foo = goog.require('modA');",
              "",
              "exports = {ExportedFoo: Foo};"),
        },
        new String[] {
          "/** @const */ var module$exports$modA = class {};",
          lines(
              "/** @const */ var module$exports$modB = {};",
              "/** @const */ module$exports$modB.ExportedFoo = module$exports$modA;"),
        });

    test(
        new String[] {
          "goog.module('modA'); \n exports = class {};",
          lines(
              "goog.module('modB');",
              "",
              "var Foo = goog.require('modA');",
              "class Bar {}",
              "",
              "exports = {Foo, Bar};"),
        },
        new String[] {
          "/** @const */ var module$exports$modA = class {};",
          lines(
              "/** @const */ var module$exports$modB = {};",
              "module$exports$modB.Bar = class {};",
              "/** @const */ module$exports$modB.Foo = module$exports$modA;"),
        });
  }

  public void testIllegalDestructuringImports() {
    testError(
        new String[] {
          lines(
              "goog.module('p.A');",
              "/** @constructor */ var A = function() {}",
              "A.method = function() {}",
              "exports = A"),
          lines(
              "goog.module('p.C');",
              "var {method} = goog.require('p.A');",
              "function main() {",
              "  method();",
              "}")
        },
        ILLEGAL_DESTRUCTURING_DEFAULT_EXPORT);

    testError(
        new String[] {
          lines(
              "goog.module('p.A');",
              "/** @constructor */ exports = class { static method() {} }"),
          lines(
              "goog.module('p.C');",
              "var {method} = goog.require('p.A');",
              "function main() {",
              "  method();",
              "}")
        },
        ILLEGAL_DESTRUCTURING_DEFAULT_EXPORT);

    testError(
        new String[] {
          lines(
              "goog.module('p.A');",
              "",
              "/** @constructor */ exports.Foo = class {};",
              "/** @constructor */ exports.Bar = class {};"),
          lines(
              "goog.module('p.C');",
              "",
              "var {Baz} = goog.require('p.A');")
        },
        ILLEGAL_DESTRUCTURING_NOT_EXPORTED);

    // TODO(blickly): We should warn for this as well, but it's harder to detect.
    test(
        new String[] {
          lines(
              "goog.provide('p.A');",
              "/** @constructor */ p.A = function() {}",
              "p.A.method = function() {}"),
          lines(
              "goog.module('p.C');",
              "var {method} = goog.require('p.A');",
              "function main() {",
              "  method();",
              "}")
        },
        null);
  }


  public void testDeclareLegacyNamespace() {
    test("goog.module('ns.a'); goog.module.declareLegacyNamespace();", "goog.provide('ns.a');");
  }

  public void testSideEffectOnlyModuleImport() {
    test(
        new String[] {
            lines(
                "goog.module('ns.b');",
                "alert('hello world');"),
            lines(
                "goog.module('ns.a');",
                "goog.require('ns.b');")},

        new String[] {
            "/** @const */ var module$exports$ns$b = {}; alert('hello world');",
            "/** @const */ var module$exports$ns$a = {};"});
  }

  public void testTypeOnlyModuleImport() {
    test(
        new String[] {
            lines(
                "goog.module('ns.B');",
                "/** @constructor */ exports = function() {};"),
            lines(
                "goog.module('ns.a');",
                "",
                "goog.require('ns.B');",
                "",
                "/** @type {ns.B} */ var c;")},

        new String[] {
            "/** @constructor @const */ var module$exports$ns$B = function() {};",
            lines(
                "/** @const */ var module$exports$ns$a = {};",
                "/** @type {module$exports$ns$B} */ var module$contents$ns$a_c;")});
  }

  public void testSideEffectOnlyImportOfGoogProvide() {
    test(
        new String[] {
            lines(
                "goog.provide('ns.b');",
                "",
                "alert('hello world');"),
            lines(
                "goog.module('ns.a');",
                "",
                "goog.require('ns.b');")},

        new String[] {
            "goog.provide('ns.b'); alert('hello world');",
            "/** @const */ var module$exports$ns$a = {}; goog.require('ns.b');"});
  }

  public void testSideEffectOnlyImportOfLegacyGoogModule() {
    test(
        new String[] {
          lines(
              "goog.module('ns.b');",
              "goog.module.declareLegacyNamespace();",
              "",
              "alert('hello world');"),
          lines("goog.module('ns.a');", "", "goog.require('ns.b');")
        },
        new String[] {
          "goog.provide('ns.b'); alert('hello world');",
          "/** @const */ var module$exports$ns$a = {}; goog.require('ns.b');"
        });
  }

  public void testTypeOnlyModuleImportFromLegacyFile() {
    test(
        new String[] {
            lines(
                "goog.module('ns.B');",
                "/** @constructor */ exports = function() {};"),
            lines(
                "goog.provide('ns.a');",
                "",
                "goog.require('ns.B');",
                "",
                "/** @type {ns.B} */ var c;")},

        new String[] {
            "/** @constructor @const */ var module$exports$ns$B = function() {};",
            lines(
                "goog.provide('ns.a');",
                "",
                "/** @type {module$exports$ns$B} */ var c;")});
  }

  public void testBundle1() {
    test(
        new String[] {
            "goog.module('ns.b');",
            lines(
                "goog.loadModule(function(exports) {",
                "  goog.module('ns.a');",
                "  var b = goog.require('ns.b');",
                "  exports.b = b;",
                "  return exports;",
                "});")},

        new String[] {
            "/** @const */ var module$exports$ns$b = {};",
            lines(
                "/** @const */ var module$exports$ns$a = {};",
                "/** @const */ module$exports$ns$a.b = module$exports$ns$b;")});
  }

  public void testBundle2() {
    test(
        lines(
            "goog.loadModule(function(exports) {",
            "  goog.module('ns.b');",
            "  return exports;",
            "});",
            "goog.loadModule(function(exports) {",
            "  goog.module('ns.a');",
            "  var b = goog.require('ns.b');",
            "  exports.b = b;",
            "  return exports;",
            "});",
            "goog.loadModule(function(exports) {",
            "  goog.module('ns.c');",
            "  var b = goog.require('ns.b');",
            "  exports.b = b;",
            "  return exports;",
            "});"),

        lines(
            "/** @const */ var module$exports$ns$b = {};",
            "/** @const */ var module$exports$ns$a = {};",
            "/** @const */ module$exports$ns$a.b = module$exports$ns$b;",
            "/** @const */ var module$exports$ns$c = {};",
            "/** @const */ module$exports$ns$c.b = module$exports$ns$b;"));
  }

  public void testBundle3() {
    test(
        lines(
            "goog.loadModule(function(exports) {",
            "  goog.module('ns.b');",
            "  return exports;",
            "});",
            "goog.loadModule(function(exports) {",
            "  'use strict';",
            "  goog.module('ns.a');",
            "  goog.module.declareLegacyNamespace();",
            "  var b = goog.require('ns.b');",
            "  return exports;",
            "});"),
        lines("/** @const */ var module$exports$ns$b = {};", "goog.provide('ns.a');"));
  }

  public void testBundle4() {
    test(
        lines(
            "goog.loadModule(function(exports) {",
            "  goog.module('goog.asserts');",
            "  return exports;",
            "});",
            "goog.loadModule(function(exports) {",
            "  'use strict';",
            "  goog.module('ns.a');",
            "  var b = goog.require('goog.asserts');",
            "  return exports;",
            "});"),

        lines(
            "/** @const */ var module$exports$goog$asserts = {};",
            "/** @const */ var module$exports$ns$a = {};"));
  }

  public void testBundle5() {
    test(
        lines(
            "goog.loadModule(function(exports) {",
            "  goog.module('goog.asserts');",
            "  return exports;",
            "});",
            "goog.loadModule(function(exports) {",
            "  'use strict';",
            "  goog.module('xid');",
            "  goog.module.declareLegacyNamespace();",
            "  var asserts = goog.require('goog.asserts');",
            "  exports = function(id) {",
            "    return xid.internal_(id);",
            "  };",
            "  var xid = exports;",
            "  return exports;",
            "});"),
        lines(
            "/** @const */ var module$exports$goog$asserts = {};",
            "goog.provide('xid');",
            "/** @const */ xid = function(id) {",
            "  return module$contents$xid_xid.internal_(id);",
            "};",
            "var module$contents$xid_xid = xid"));
  }

  public void testBundle6() {
    test(
        lines(
            "goog.loadModule(function(exports) {",
            "  goog.module('goog.asserts');",
            "  return exports;",
            "});",
            "goog.loadModule(function(exports) {",
            "  'use strict';",
            "  goog.module('xid');",
            "  goog.module.declareLegacyNamespace();",
            "  var asserts = goog.require('goog.asserts');",
            "  var xid = function(id) {",
            "    return xid.internal_(id);",
            "  };",
            "  xid.internal_ = function(id) {};",
            "  exports = xid;",
            "  return exports;",
            "});"),
        lines(
            "/** @const */ var module$exports$goog$asserts = {};",
            "goog.provide('xid');",
            "var module$contents$xid_xid = function(id) {",
            "  return module$contents$xid_xid.internal_(id);",
            "};",
            "module$contents$xid_xid.internal_ = function(id) {};",
            "/** @const */ xid = module$contents$xid_xid "));
  }

  public void testBundleWithDestructuringImport() {
    test(
        lines(
            "goog.loadModule(function(exports) { 'use strict';",
            "  goog.module('mod_B');",
            "",
            "  /** @interface */ function B(){}",
            "",
            "  exports.B = B;",
            "  return exports;",
            "});",
            "goog.loadModule(function(exports) { 'use strict';",
            "  goog.module('mod_A');",
            "",
            "  var {B} = goog.require('mod_B');",
            "",
            "  /** @constructor @implements {B} */",
            "  function A() {}",
            "  return exports;",
            "});"),
        lines(
            "/** @const */ var module$exports$mod_B = {};",
            "/** @interface */ module$exports$mod_B.B = function(){};",
            "",
            "/** @const */ var module$exports$mod_A = {};",
            "/** @constructor @implements {module$exports$mod_B.B} */",
            "function module$contents$mod_A_A(){}"));
  }

  public void testGoogLoadModuleString() {
    testSame("goog.loadModule(\"goog.module('a.b.c'); exports = class {};\");");
  }

  public void testGoogScope1() {
    // Typedef defined inside a goog.scope(). The typedef is seen and is *not* legacy-to-binary
    // bridge exported.
    testSame(
        lines(
            "goog.provide('a.c.B');",
            "goog.provide('a.u.M');",
            "goog.scope(function() {",
            "  /** @constructor */",
            "  a.c.B = function() {}",
            "  /** @typedef {function(!Array<a.u.E>)} */",
            "  a.u.M;",
            "});"));
  }

  public void testTopLevelNames1() {
    // Vars defined inside functions are not module top level.
    test(
        lines(
            "goog.module('a');",
            "var a, b, c;",
            "function Outer() {",
            "  var a, b, c;",
            "  function Inner() {",
            "    var a, b, c;",
            "  }",
            "}"),

        lines(
            "/** @const */ var module$exports$a = {};",
            "var module$contents$a_a, module$contents$a_b, module$contents$a_c;",
            "function module$contents$a_Outer() {",
            "  var a, b, c;",
            "  function Inner() {",
            "    var a, b, c",
            "  }",
            "}"));
  }

  public void testTopLevelNames2() {
    // Vars in blocks are module top level because they are hoisted to the first execution context.
    test(
        lines(
            "goog.module('a.c');",
            "if (true) {",
            "  var a, b, c;",
            "}"),

        lines(
            "/** @const */ var module$exports$a$c = {};",
            "if (true) {",
            "  var module$contents$a$c_a, module$contents$a$c_b, module$contents$a$c_c;",
            "}"));
  }

  public void testTopLevelNames3() {
    // Functions in blocks are not module top level because they are block scoped.
    test(
        lines(
            "goog.module('a.c');",
            "if (true) {",
            "  function a() {}",
            "  function b() {}",
            "  function c() {}",
            "}"),

        lines(
            "/** @const */ var module$exports$a$c = {};",
            "if (true) {",
            "  function a() {}",
            "  function b() {}",
            "  function c() {}",
            "}"));
  }

  public void testThis() {
    // global "this" is retained.
    test(
        lines(
            "goog.module('a');",
            "this;"),

        lines(
            "/** @const */ var module$exports$a = {};",
            "this;"));
  }

  public void testInvalidModule() {
    testError("goog.module(a);", INVALID_MODULE_NAMESPACE);
  }

  public void testInvalidRequire() {
    testError("goog.module('ns.a');" + "goog.require(a);", INVALID_REQUIRE_NAMESPACE);
  }

  public void testInvalidProvide() {
    // The ES6 path turns on DependencyOptions.needsManagement() which leads to JsFileLineParser
    // execution that throws a different exception on some invalid goog.provide()s.
    testError("goog.module('a'); goog.provide('b');", INVALID_PROVIDE_CALL);
  }

  public void testGoogModuleGet1() {
    test(
        new String[] {
            "goog.module('a');",
            lines(
                "function f() {",
                "  var x = goog.module.get('a');",
                "}")},

        new String[] {
            "/** @const */ var module$exports$a = {};",
            lines(
                "function f() {",
                "  var x = module$exports$a;",
                "}")});
  }

  public void testGoogModuleGet2() {
    test(
        new String[] {
            "goog.module('a.b.c');",
            lines(
                "function f() {",
                "  var x = goog.module.get('a.b.c');",
                "}")},

        new String[] {
            "/** @const */ var module$exports$a$b$c = {};",
            lines(
                "function f() {",
                "  var x = module$exports$a$b$c;",
                "}")});
  }

  public void testGoogModuleGet3() {
    test(
        new String[] {
            lines(
                "goog.module('a.b.c');",
                "exports = class {};"),
            lines(
                "goog.module('x.y.z');",
                "",
                "function f() {",
                "  return new (goog.module.get('a.b.c'))();",
                "}")},

        new String[] {
            "/** @const */ var module$exports$a$b$c = class {};",
            lines(
                "/** @const */ var module$exports$x$y$z = {};",
                "function module$contents$x$y$z_f() {",
                "  return new module$exports$a$b$c();",
                "}")});
  }

  public void testGoogModuleGet_missing() {
    test(
        srcs(lines(
            "goog.module('x.y.z');",
            "",
            "function f() {",
            "  return goog.module.get('a.b.c');",
            "}",
            "f();")),
        expected(lines(
            "/** @const */",
            "var module$exports$x$y$z={};",
            "",
            "function module$contents$x$y$z_f() {}",
            "",
            "module$contents$x$y$z_f();")),
        warning(MISSING_MODULE_OR_PROVIDE));
  }

  public void testGoogModuleGet_missing_preserve() {
    preserveClosurePrimitives = true;
    // Need to disable tree comparison because compiler adds MODULE_BODY token when parsing
    // expected output but it is not present in actual tree.
    disableCompareAsTree();

    test(
        srcs(lines(
            "goog.module('x.y.z');",
            "",
            "function f() {",
            "  return goog.module.get('a.b.c');",
            "}",
            "f();")),
        expected(""
            + "goog.module(\"x.y.z\");"
            + "/** @const */ var module$exports$x$y$z={};"
            + "function module$contents$x$y$z_f(){return goog.module.get(\"a.b.c\")}"
            + "module$contents$x$y$z_f()"),
        warning(MISSING_MODULE_OR_PROVIDE));
  }

  public void testAliasedGoogModuleGet1() {
    test(
        new String[] {
            "goog.module('b');",
            lines(
                "goog.module('a');",
                "var x = goog.forwardDeclare('b');",
                "function f() {",
                "  x = goog.module.get('b');",
                "  new x;",
                "}")},

        new String[] {
            "/** @const */ var module$exports$b = {};",
            lines(
                "/** @const */ var module$exports$a = {};",
                "function module$contents$a_f() {",
                "  new module$exports$b;",
                "}")});
  }

  public void testAliasedGoogModuleGet2() {
    test(
        new String[] {
            "goog.module('x.y.z');",
            lines(
                "goog.module('a');",
                "var x = goog.forwardDeclare('x.y.z');",
                "function f() {",
                "  x = goog.module.get('x.y.z');",
                "  new x;",
                "}")},

        new String[] {
            "/** @const */ var module$exports$x$y$z = {};",
            lines(
                "/** @const */ var module$exports$a = {};",
                "function module$contents$a_f() {",
                "  new module$exports$x$y$z;",
                "}")});
  }

  public void testAliasedGoogModuleGet3() {
    test(
        new String[] {
            lines(
                "goog.module('a.b.c');",
                "/** @constructor */ function C() {}",
                "exports = C"),
            lines(
                "/** @type {a.b.c} */ var c;",
                "function f() {",
                "  var C = goog.module.get('a.b.c');",
                "  c = new C;",
                "}"),
        },

        new String[] {
            "/** @constructor */ function module$exports$a$b$c() {}",
            lines(
                "/** @type {module$exports$a$b$c} */ var c;",
                "function f() {",
                "  var C = module$exports$a$b$c;",
                "  c = new C;",
                "}")});
  }

  public void testAliasedGoogModuleGet4() {
    test(
        new String[] {
            lines(
                "goog.module('x.y.z');",
                "/** @constructor */ function Z() {}",
                "exports = Z"),
            lines(
              "goog.module('a');",
              "/** @type {x.y.z} */ var c;",
              "var x = goog.forwardDeclare('x.y.z');",
              "function f() {",
              "  x = goog.module.get('x.y.z');",
              "  c = new x;",
              "}")},

        new String[] {
            "/** @constructor */ function module$exports$x$y$z() {}",
            lines(
                "/** @const */ var module$exports$a = {};",
                "/** @type {module$exports$x$y$z} */ var module$contents$a_c;",
                "function module$contents$a_f() {",
                "  module$contents$a_c = new module$exports$x$y$z;",
                "}")});
  }

  public void testAliasedGoogModuleGet5() {
    test(
        new String[] {
            "goog.provide('b');",
            lines(
                "goog.module('a');",
                "var x = goog.forwardDeclare('b');",
                "function f() {",
                "  x = goog.module.get('b');",
                "  new x;",
                "}")},

        new String[] {
            "goog.provide('b');",
            lines(
                "/** @const */ var module$exports$a = {};",
                "goog.forwardDeclare('b');",
                "function module$contents$a_f() {",
                "  new b;",
                "}")});
  }

  public void testAliasedGoogModuleGet6() {
    test(
        new String[] {
            "goog.provide('x.y.z');",
            lines(
                "goog.module('a');",
                "var z = goog.forwardDeclare('x.y.z');",
                "function f() {",
                "  z = goog.module.get('x.y.z');",
                "  new z;",
                "}")},

        new String[] {
            "goog.provide('x.y.z');",
            lines(
                "/** @const */ var module$exports$a = {};",
                "goog.forwardDeclare('x.y.z');",
                "function module$contents$a_f() {",
                "  new x.y.z;",
                "}")});
  }

  public void testAliasedGoogModuleGet7() {
    test(
        new String[] {
            "goog.module('a.b.c.D');",
            lines(
                "goog.require('a.b.c.D');",
                "goog.scope(function() {",
                "var D = goog.module.get('a.b.c.D');",
                "});")},

        new String[] {
            "/** @const */ var module$exports$a$b$c$D = {};",
            lines(
                "goog.scope(function() {",
                "var D = module$exports$a$b$c$D;",
                "});")});
  }

  public void testAliasedGoogModuleGet8() {
    test(
        new String[] {
            "goog.module('a.b.c.D');",
            lines(
                "goog.require('a.b.c.D');",
                "goog.scope(function() {",
                "var D = goog.module.get('a.b.c.D');",
                "var d = new D;",
                "});")},

        new String[] {
            "/** @const */ var module$exports$a$b$c$D = {};",
            lines(
                "goog.scope(function() {",
                "var D = module$exports$a$b$c$D;",
                "var d = new D;",
                "});")});
  }

  public void testInvalidGoogForwardDeclareParameter() {
    // Wrong parameter count.
    testError(
        lines(
            "goog.module('a');",
            "var x = goog.forwardDeclare();"),

        INVALID_FORWARD_DECLARE_NAMESPACE);

    // Wrong parameter count.
    testError(
        lines(
            "goog.module('a');",
            "var x = goog.forwardDeclare('a', 'b');"),

        INVALID_FORWARD_DECLARE_NAMESPACE);

    // Wrong parameter type.
    testError(
        lines(
            "goog.module('a');",
            "var x = goog.forwardDeclare({});"),

        INVALID_FORWARD_DECLARE_NAMESPACE);
  }

  public void testInvalidGoogModuleGetAlias() {
    testError(
        new String[] {
            "goog.provide('g');",
            lines(
                "goog.module('a');",
                "x = goog.module.get('g');"),
        },
        INVALID_GET_ALIAS);

    testError(
        new String[] {
            "goog.provide('g');",
            lines(
                "goog.module('a');",
                "var x;",
                "x = goog.module.get('g');"),
        },
        INVALID_GET_ALIAS);

    testError(
        new String[] {
            "goog.provide('g'); goog.provide('z');",
            lines(
                "goog.module('a');",
                "var x = goog.forwardDeclare('z');",
                "x = goog.module.get('g');"),
        },
        INVALID_GET_ALIAS);
  }


  public void testInvalidGoogModuleGet1() {
    testError(
        lines(
            "function f() {",
            "  goog.module.get(a);",
            "}"),

        INVALID_GET_NAMESPACE);
  }

  public void testInvalidGoogModuleGet2() {
    testError("goog.module.get('a');", INVALID_GET_CALL_SCOPE);
  }

  public void testExtractableExport1() {
    test(
        lines(
            "goog.module('xid');",
            "var xid = function() {};",
            "exports = xid;"),

        "var module$exports$xid = function() {};");
  }

  public void testExtractableExport2() {
    test(
        lines(
            "goog.module('xid');",
            "function xid() {}",
            "exports = xid;"),

        "function module$exports$xid() {}");
  }

  public void testExtractableExport3() {
    test(
        lines(
            "goog.module('Foo');",
            "class Foo {}",
            "exports = Foo;"),

        "class module$exports$Foo {}");
  }

  public void testExtractableExport4() {
    test(
        lines(
            "goog.module('Foo');",
            "const Foo = class {}",
            "exports = Foo;"),

        "const module$exports$Foo = class {};");
  }

  public void testExport0() {
    test(
        "goog.module('ns.a');",

        "/** @const */ var module$exports$ns$a = {};");
  }

  public void testExport1() {
    test(
        lines(
            "goog.module('ns.a');",
            "exports = {};"),

        "/** @const */ var module$exports$ns$a = {};");
  }

  public void testExport2() {
    test(
        lines(
            "goog.module('ns.a');",
            "exports.x = 1;"),

        lines(
            "/** @const */ var module$exports$ns$a = {};",
            "/** @const */ module$exports$ns$a.x = 1"));
  }

  public void testExport4() {
    test(
        lines(
            "goog.module('ns.a');",
            "exports = { something: 1 };"),

        "/** @const */ var module$exports$ns$a = { /** @const */ something : 1 };");
  }

  public void testExport5() {
    test(
        lines(
            "goog.module('ns.a');",
            "/** @typedef {string} */ var x;",
            "exports.x = x;"),

        lines(
            "/** @const */ var module$exports$ns$a = {};",
            "/** @typedef {string} */ module$exports$ns$a.x;"));
  }

  public void testExport6() {
    test(
        lines(
            "goog.module('ns.a');",
            "/** @typedef {string} */ var x;",
            "exports = { something: x };"),

        lines(
          "/** @const */ var module$exports$ns$a = {};",
          "/** @typedef {string} */ module$exports$ns$a.something;"));
  }

  public void testExport6_1() {
    test(
        lines(
            "goog.module('ns.a');",
            "/** @typedef {string} */ var x;",
            "exports.something = x;"),

        lines(
          "/** @const */ var module$exports$ns$a = {};",
          "/** @typedef {string} */ module$exports$ns$a.something;"));
  }

  public void testExport7() {
    test(
        lines(
            "goog.module('ns.a');",
            "/** @constructor */",
            "exports = function() {};"),

        "/** @constructor @const */ var module$exports$ns$a = function() {};");
  }

  public void testExport8() {
    test(
        lines(
            "goog.module('ns.a');",
            "exports = goog.defineClass({});"),

        "/** @const */ var module$exports$ns$a = goog.defineClass({});");
  }

  public void testExport9() {
    // Doesn't legacy-to-binary bridge export a typedef.
    testSame(
        lines(
            "goog.provide('goog.ui.ControlContent');",
            "/** @typedef {string} */ goog.ui.ControlContent;"));
  }

  public void testExport10() {
    // Doesn't rewrite exports in legacy scripts.
    testSame(
        lines(
            "(function() {",
            "  /** @constructor */ function S(string) {}",
            "  exports.S = S;",
            "})();"));
  }

  public void testExport11() {
    // Does rewrite export typedefs and defensively creates the exports root object first.
    test(
        lines(
            "goog.module('a.B');",
            "/** @typedef {string} */ exports.C;"),

        lines(
            "/** @const */ var module$exports$a$B = {};",
            "/** @const @typedef {string} */ module$exports$a$B.C;"));
  }

  public void testExport12() {
    test(
        lines(
            "goog.module('ns.a');",
            "exports.foo = goog.defineClass({});"),

        lines(
            "/** @const */ var module$exports$ns$a = {};",
            "/** @const */ module$exports$ns$a.foo = goog.defineClass({});"));
  }

  public void testExport13() {
    // Creates the exports root object before export object reads.
    test(
        lines(
            "goog.module('a.B');",
            "var field = exports;"),

        lines(
            "/** @const */ var module$exports$a$B = {};",
            "var module$contents$a$B_field = module$exports$a$B;"));
  }

  public void testExportEnhancedObjectLiteral() {
    test(
        lines(
            "goog.module('ns.a');",
            "class Something {}",
            "exports = { Something };"),

        lines(
            "/** @const */ var module$exports$ns$a = {};",
            "module$exports$ns$a.Something = class {};"));

    testError(
        lines(
            "goog.module('ns.a');",
            "exports = { [something]: 3 };"),

        INVALID_EXPORT_COMPUTED_PROPERTY);
  }

  public void testImport() {
    // A goog.module() that imports, jsdocs, and uses both another goog.module() and a legacy
    // script.
    test(
        new String[] {
            lines(
                "goog.module('p.A');",
                "/** @constructor */ function A() {}",
                "exports = A;"),
            lines(
                "goog.provide('p.B');",
                "/** @constructor */ p.B = function() {}"),
            lines(
                "goog.module('p.C');",
                "var A = goog.require('p.A');",
                "var B = goog.require('p.B');",
                "function main() {",
                "  /** @type {A} */ var a = new A;",
                "  /** @type {B} */ var b = new B;",
                "}")},

        new String[] {
            "/** @constructor */ function module$exports$p$A() {}",
            lines(
                "goog.provide('p.B');",
                "/** @constructor */ p.B = function() {}"),
            lines(
                "/** @const */ var module$exports$p$C = {};",
                "goog.require('p.B');",
                "function module$contents$p$C_main() {",
                "  /** @type {module$exports$p$A} */ var a = new module$exports$p$A;",
                "  /** @type {p.B} */ var b = new p.B;",
                "}")});
  }

  public void testSetTestOnly() {
    test(
        lines(
            "goog.module('ns.a');",
            "goog.setTestOnly();"),

        lines(
            "/** @const */ var module$exports$ns$a = {};",
            "goog.setTestOnly();"));
  }

  public void testRewriteJsDoc1() {
    // Inlines JsDoc references to aliases of imported types.
    test(
        new String[] {
            lines(
                "goog.module('p.A');",
                "/** @constructor */",
                "function A() {}",
                "exports = A;"),
            lines(
                "goog.module('p.B');",
                "var A = goog.require('p.A');",
                "function main() {",
                "  /** @type {A} */",
                "  var a = new A;",
                "}")},

        new String[] {
            "/** @constructor */ function module$exports$p$A() {}",
            lines(
                "/** @const */ var module$exports$p$B = {};",
                "function module$contents$p$B_main() {",
                "  /** @type {module$exports$p$A} */",
                "  var a = new module$exports$p$A;",
                "}")});
  }

  public void testRewriteJsDoc2() {
    // Inlines JsDoc references to own declared types.
    test(
        new String[] {
            lines(
                "goog.module('p.b');",
                "/** @constructor */",
                "function B() {}",
                "function main() {",
                "  /** @type {B} */",
                "  var b = new B;",
                "}")},

        new String[] {
            lines(
                "/** @const */ var module$exports$p$b = {};",
                "/** @constructor */",
                "function module$contents$p$b_B() {}",
                "function module$contents$p$b_main() {",
                "  /** @type {module$contents$p$b_B} */",
                "  var b = new module$contents$p$b_B;",
                "}")});
  }

  public void testRewriteJsDoc3() {
    // Rewrites fully qualified JsDoc references to types.
    test(
        new String[] {
            lines(
                "goog.module('p.A');",
                "/** @constructor */",
                "function A() {}",
                "exports = A;"),
            lines(
                "goog.module('p.B');",
                "var A = goog.require('p.A');",
                "function main() {",
                "  /** @type {p.A} */",
                "  var a = new A;",
                "}")},

        new String[] {
            lines(
                "/** @constructor */",
                "function module$exports$p$A() {}"),
            lines(
                "/** @const */ var module$exports$p$B = {};",
                "function module$contents$p$B_main() {",
                "  /** @type {module$exports$p$A} */",
                "  var a = new module$exports$p$A;",
                "}")});
  }

  public void testRewriteJsDoc4() {
    // Rewrites fully qualified JsDoc references to types in goog.module() files even if they come
    // after the reference.
    test(
        new String[] {
            lines(
                "goog.module('p.A');",
                "/** @constructor */",
                "function A() {}",
                "A.prototype.setB = function(/** p.B */ x) {}",
                "exports = A;"),
            lines(
                "goog.module('p.B');",
                "var A = goog.require('p.A');",
                "/** @constructor @extends {A} */",
                "function B() {}",
                "B.prototype = new A;",
                "exports = B;")},

        new String[] {
            lines(
                "/** @constructor */",
                "function module$exports$p$A() {}",
                "module$exports$p$A.prototype.setB = function(/** module$exports$p$B */ x) {}"),
            lines(
                "/** @constructor @extends {module$exports$p$A} */",
                "function module$exports$p$B() {}",
                "module$exports$p$B.prototype = new module$exports$p$A;")});
  }

  public void testRewriteJsDoc5() {
    test(
          lines(
              "goog.module('p.A');",
              "",
              "/** @constructor */",
              "function A() {}",
              "",
              "/** @type {!A} */",
              "var x = new A;",
              "",
              "exports = A;"),
          lines(
              "/** @constructor */",
              "function module$exports$p$A() {}",
              "/** @type {!module$exports$p$A} */",
              "var module$contents$p$A_x = new module$exports$p$A;"));
  }

  public void testDuplicateModule() {
    testError(
        new String[] {
            "goog.module('ns.a');",
            "goog.module('ns.a');"},

        DUPLICATE_MODULE);
  }

  public void testDuplicateNamespace() {
    testError(
        new String[] {
            "goog.module('ns.a');",
            "goog.provide('ns.a');"},

        DUPLICATE_NAMESPACE);
  }

  public void testImportInliningDoesntShadow() {
    testNoWarning(
        lines(
            "/** @const */ var a = a || {};",
            "goog.provide('a.b.c');",
            "a.b.c = class {};",
            "goog.loadModule(function(exports) { 'use strict';",
            "  goog.module('a.b.d');",
            "  goog.module.declareLegacyNamespace();",
            "  var c = goog.require('a.b.c');",
            "  exports.c = new c;",
            "  return exports;",
            "});"));
  }

  public void testImportInliningShadowsVar() {
    testError(
        new String[] {
            lines(
                "goog.provide('a.b.c');",
                "a.b.c = 5;"),
            lines(
                "goog.module('a.b.d');",
                "var c = goog.require('a.b.c');",
                "function foo() {",
                "  var a = 10;",
                "  var b = c;",
                "}")},
        IMPORT_INLINING_SHADOWS_VAR);
  }

  public void testImportInliningShadowsDestructuredImport() {
    testError(
        new String[] {
            lines(
                "goog.provide('a.b.c');",
                "a.b.c.d = 5;"),
            lines(
                "goog.module('a.b.d');",
                "const {d} = goog.require('a.b.c');",
                "function foo() {",
                "  var a = 10;",
                "  var b = d;",
                "}")},
        IMPORT_INLINING_SHADOWS_VAR);
  }

  public void testExportsShadowingAllowed() {
    testNoWarning(
        lines(
            "goog.loadModule(function(exports) {",
            "   goog.module('a.b.c');",
            "",
            "   class Foo {}",
            "   /** @const {*} */ Foo.prototype.x;",
            "   exports.Foo = Foo;",
            "",
            "   return exports;",
            "});"));
  }

  public void testExportRewritingShadows() {
    test(
        lines(
            "goog.module('a.b.c');",
            "function test() {}",
            "function f(test) { return test; }",
            "exports = test;"),
        lines(
            "function module$exports$a$b$c() {}",
            "function module$contents$a$b$c_f(test) { return test; }"));

    test(
        lines(
            "goog.module('a.b.c');",
            "function test() {}",
            "function f(test) { return test; }",
            "exports.test = test;"),
        lines(
            "/** @const */ var module$exports$a$b$c = {};",
            "module$exports$a$b$c.test = function() {};",
            "function module$contents$a$b$c_f(test) { return test; }"));
  }

  public void testRequireTooEarly1() {
    // Module to Module require.
    testError(
        new String[] {
            lines(
                "goog.module('ns.a');",
                "goog.require('ns.b')"),
            "goog.module('ns.b');"},

        LATE_PROVIDE_ERROR);
  }

  public void testValidEarlyGoogModuleGet() {
    // Legacy Script to Module goog.module.get.
    test(
        new String[] {
          lines(
              "goog.provide('ns.a');",
              "function foo() {",
              "  var b = goog.module.get('ns.b');",
              "}"),
          "goog.module('ns.b');"
        },
        new String[] {
          "goog.provide('ns.a'); function foo() { var b = module$exports$ns$b; }",
          "/** @const */ var module$exports$ns$b = {};"
        });
  }

  public void testRequireTooEarly3() {
    // Module to Legacy Script require.
    testError(
        new String[] {
            lines(
                "goog.module('ns.a');",
                "goog.require('ns.b')"),
            "goog.provide('ns.b');"},

        LATE_PROVIDE_ERROR);
  }

  public void testInnerScriptOuterModule() {
    // Rewrites fully qualified JsDoc references to types but without writing a prefix as
    // module$exports when there's a longer prefix that references a script.
    test(
        new String[] {
            lines(
                "goog.module('A');",
                "/** @constructor */",
                "function A() {}",
                "exports = A;"),
            lines(
                "goog.provide('A.b.c.D');",
                "/** @constructor */",
                "A.b.c.L = function () {}",
                "function main() {",
                "  /** @type {A.b.c.L} */",
                "  var l = new A.b.c.L();",
                "}")},

        new String[] {
            "/** @constructor */ function module$exports$A() {}",
            lines(
                "goog.provide('A.b.c.D');",
                "/** @constructor */",
                "A.b.c.L = function() {};", // Note L not D
                "function main() {",
                // Note A.b.c.L was NOT written to module$exports$A.b.c.L.
                "  /** @type {A.b.c.L} */",
                "  var l = new A.b.c.L();",
                "}")});
  }

  public void testModuleLevelVars() {
    test(
        lines(
            "goog.module('b.c.c');",
            "/** @const */",
            "var F = 0;"),

        lines(
            "/** @const */ var module$exports$b$c$c = {};",
            "/** @const */ var module$contents$b$c$c_F = 0;"));
  }

  public void testPublicExport() {
    test(
        lines(
            "goog.module('a.b.c');",
            "goog.module.declareLegacyNamespace();",
            "/** @public */ exports = 5;"),
        lines(
            "goog.provide('a.b.c');",
            "/** @const @public */ a.b.c = 5;"));
  }

  public void testReferenceToNonLegacyGoogModuleName() {
    test(
        new String[] {"goog.module('a.b.c');", "use(a.b.c);"},
        new String[] {"/** @const */ var module$exports$a$b$c={}", "use(a.b.c);"});
  }

  public void testGoogModuleValidReferences() {
    test(
        new String[] {
          "goog.module('a.b.c');",
          "goog.module('x.y.z'); var c = goog.require('a.b.c'); use(c);"
        },
        new String[] {
          "/** @const */ var module$exports$a$b$c={};",
          "/** @const */ var module$exports$x$y$z={}; use(module$exports$a$b$c);"
        });

    test(
        new String[] {
          "goog.module('a.b.c');",
          lines(
              "goog.require('a.b.c');",
              "goog.scope(function() {",
              "  var c = goog.module.get('a.b.c');",
              "  use(c);",
              "});")
        },
        new String[] {
          "/** @const */ var module$exports$a$b$c={};",
          "goog.scope(function() { var c = module$exports$a$b$c; use(c); });"
        });
  }

  public void testLegacyGoogModuleValidReferences() {
    test(
        new String[] {
          "goog.module('a.b.c'); goog.module.declareLegacyNamespace();",
          "goog.require('a.b.c'); use(a.b.c);"
        },
        new String[] {
            "goog.provide('a.b.c');",
            "goog.require('a.b.c'); use(a.b.c);"
        });

    test(
        new String[] {
          "goog.module('a.b.c'); goog.module.declareLegacyNamespace();",
          "goog.module('x.y.z'); var c = goog.require('a.b.c'); use(c);"
        },
        new String[] {
          "goog.provide('a.b.c');",
          "/** @const */ var module$exports$x$y$z={}; goog.require('a.b.c'); use(a.b.c);"
        });

    test(
        new String[] {
          lines(
              "goog.module('a.b.Foo');",
              "goog.module.declareLegacyNamespace();",
              "",
              "/** @constructor */ exports = function() {};"),
          "/** @param {a.b.Foo} x */ function f(x) {}"
        },
        new String[] {
          "goog.provide('a.b.Foo'); /** @constructor @const */ a.b.Foo = function() {};",
          "/** @param {a.b.Foo} x */ function f(x) {}"
        });

    test(
        new String[] {
          lines(
              "goog.module('a.b.c');",
              "goog.module.declareLegacyNamespace();",
              "",
              "exports = function() {};"),
          "function f() { return goog.module.get('a.b.c'); }"
        },
        new String[] {
          "goog.provide('a.b.c'); /** @const */ a.b.c = function() {};",
          "function f() { return a.b.c; }"
        });

    test(
        new String[] {
          lines(
              "goog.module('a.b.Foo');",
              "goog.module.declareLegacyNamespace();",
              "",
              "/** @constructor */ function Foo() {}",
              "",
              "exports = Foo;"),
          "/** @param {a.b.Foo} x */ function f(x) {}"
        },
        new String[] {
            lines(
                "goog.provide('a.b.Foo');",
                "/** @constructor */ function module$contents$a$b$Foo_Foo() {}",
                "/** @const */ a.b.Foo = module$contents$a$b$Foo_Foo;"),
          "/** @param {a.b.Foo} x */ function f(x) {}"
        });

    test(
        new String[] {
          lines(
              "goog.module('a.b');",
              "goog.module.declareLegacyNamespace();",
              "",
              "/** @constructor */ function Foo() {};",
              "",
              "exports.Foo = Foo;"),
          "/** @param {a.b.Foo} x */ function f(x) {}"
        },
        new String[] {
            lines(
                "goog.provide('a.b');",
                "/** @constructor */ function module$contents$a$b_Foo() {};",
                "/** @const */ a.b.Foo = module$contents$a$b_Foo;"),
          "/** @param {a.b.Foo} x */ function f(x) {}"
        });
  }

  public void testUselessUseStrict() {
    testWarning(
        "'use strict'; goog.module('b.c.c');", ClosureRewriteModule.USELESS_USE_STRICT_DIRECTIVE);
  }

  public void testRewriteGoogModuleAliases1() {
    test(
        new String[] {
          lines(
              "goog.module('base');",
              "",
              "/** @constructor */ var Base = function() {}",
              "exports = Base;"),
          lines(
              "goog.module('leaf');",
              "",
              "var Base = goog.require('base');",
              "exports = /** @constructor @extends {Base} */ function Foo() {}")
        },
        new String[] {
          "/** @constructor */ var module$exports$base = function() {};",
            lines(
                "/** @const */ var module$exports$leaf = ",
                "/** @constructor @extends {module$exports$base} */ function Foo() {}")
        });
  }

  public void testRewriteGoogModuleAliases2() {
    test(
        new String[] {
            lines(
                "goog.module('ns.base');",
                "",
                "/** @constructor */ var Base = function() {}",
                "exports = Base;"),
            lines(
                "goog.module('leaf');",
                "",
                "var Base = goog.require('ns.base');",
                "exports = /** @constructor @extends {Base} */ function Foo() {}")
        },
        new String[] {
            "/** @constructor */ var module$exports$ns$base = function() {};",
            lines(
                "/** @const */ var module$exports$leaf = ",
                "/** @constructor @extends {module$exports$ns$base} */ function Foo() {}")
        });
  }

  public void testRewriteGoogModuleAliases3() {
    test(
        new String[] {
            lines(
                "goog.module('ns.base');",
                "",
                "/** @constructor */ var Base = function() {};",
                "/** @constructor */ Base.Foo = function() {};",
                "exports = Base;"),
            lines(
                "goog.module('leaf');",
                "",
                "var Base = goog.require('ns.base');",
                "exports = /** @constructor @extends {Base.Foo} */ function Foo() {}")
        },
        new String[] {
            lines(
                "/** @constructor */ var module$exports$ns$base = function() {};",
                "/** @constructor */ module$exports$ns$base.Foo = function() {};"),
            lines(
                "/** @const */ var module$exports$leaf = ",
                "/** @constructor @extends {module$exports$ns$base.Foo} */ function Foo() {}")
        });
  }

  public void testRewriteGoogModuleAliases4() {
    test(
        new String[] {
            lines(
                "goog.module('ns.base');",
                "",
                "/** @constructor */ var Base = function() {}",
                "exports = Base;"),
            lines(
                "goog.module('leaf');",
                "",
                "var Base = goog.require('ns.base');",
                "exports = new Base;")
        },
        new String[] {
            "/** @constructor */ var module$exports$ns$base = function() {};",
            "/** @const */ var module$exports$leaf = new module$exports$ns$base;"
        });
  }

  public void testRewriteGoogModuleAliases5() {
    test(
        new String[] {
            lines(
                "goog.module('ns.base');",
                "",
                "/** @constructor */ var Base = function() {}",
                "exports = Base;"),
            lines(
                "goog.module('mid');",
                "",
                "var Base = goog.require('ns.base');",
                "exports = Base;"),
            lines(
                "goog.module('leaf')",
                "var Base = goog.require('mid');",
                "new Base;")
        },
        new String[] {
            "/** @constructor */ var module$exports$ns$base = function() {};",
            "/** @const */ var module$exports$mid = module$exports$ns$base;",
            "/** @const */ var module$exports$leaf = {}; new module$exports$mid;",
        });
  }

  public void testRewriteGoogModuleAliases6() {
    test(
        new String[] {
            lines(
                "goog.module('base');",
                "",
                "/** @constructor */ exports.Foo = function() {};"),
            lines(
                "goog.module('FooWrapper');",
                "",
                "const {Foo} = goog.require('base');",
                "exports = Foo;"),
        },
        new String[] {
            lines(
                "/** @const */ var module$exports$base = {};",
                "/** @constructor @const */ module$exports$base.Foo = function() {};"),
            "/** @const */ var module$exports$FooWrapper = module$exports$base.Foo;",
        });
  }

  public void testRewriteGoogModuleAliases7() {
    test(
        new String[] {
            lines(
                "goog.module('base');",
                "",
                "/** @constructor */ exports.Foo = function() {};"),
            lines(
                "goog.module('FooWrapper');",
                "",
                "const {Foo: FooFromBaseModule} = goog.require('base');",
                "exports = FooFromBaseModule;"),
        },
        new String[] {
            lines(
              "/** @const */ var module$exports$base = {};",
              "/** @constructor @const */ module$exports$base.Foo = function() {};"),
            "/** @const */ var module$exports$FooWrapper = module$exports$base.Foo;",
        });
  }

  public void testRewriteGoogModuleAliasesWithPreservedPrimitives() {
    preserveClosurePrimitives = true;
    // Need to disable tree comparison because compiler adds MODULE_BODY token when parsing
    // expected output but it is not present in actual tree.
    disableCompareAsTree();
    test(
        new String[] {
            lines(
                "goog.module('Foo');",
                "",
                "/** @constructor */ exports = function() {};"),
            lines(
                "goog.module('bar');",
                "",
                "exports.doBar = function() {};"),
            lines(
                "goog.module('baz');",
                "",
                "exports.doBaz = function() {};"),
            lines(
                "goog.module('leaf');",
                "",
                "var Foo = goog.require('Foo');",
                "var {doBar} = goog.require('bar');",
                "var {doBaz: doooBaz} = goog.require('baz')"),
        },
        new String[] {
            "goog.module(\"Foo\");",
            "/** @const @constructor */ var module$exports$Foo=function(){};",

            "goog.module(\"bar\");",
            "/** @const */ var module$exports$bar={};",
            "/** @const */ module$exports$bar.doBar=function(){};",

            "goog.module(\"baz\");",
            "/** @const */ var module$exports$baz={};",
            "/** @const */ module$exports$baz.doBaz=function(){};",

            "goog.module(\"leaf\");",
            "/** @const */ var module$exports$leaf={};",
            "var module$contents$leaf_Foo=goog.require(\"Foo\");",
            "var {doBar:module$contents$leaf_doBar}=goog.require(\"bar\");\n",
            "var {doBaz:module$contents$leaf_doooBaz}=goog.require(\"baz\")"
        });
  }

  public void testGoogModuleExportsProvidedName() {
    test(
        new String[] {
            lines(
                "goog.provide('Foo');",
                "",
                "/** @constructor */ var Foo = function() {};"),
            lines(
                "goog.module('FooWrapper');",
                "",
                "goog.require('Foo');",
                "",
                "exports = Foo;"),
        },
        new String[] {
            lines(
                "goog.provide('Foo');",
                "",
                "/** @constructor */ var Foo = function() {};"),
            "goog.require('Foo'); /** @const */ var module$exports$FooWrapper = Foo;",
        });
  }

  public void testRewriteGoogModuleAliasesWithPrototypeGets1() {
    test(
        new String[] {
            lines(
                "goog.module('mod_B');",
                "",
                "/** @interface */ function B(){}",
                "B.prototype.f = function(){};",
                "",
                "exports = B;"),
            lines(
                "goog.module('mod_A');",
                "",
                "var B = goog.require('mod_B');",
                "",
                "/** @type {B} */",
                "var b;")
        },
        new String[] {
            lines(
                "/**@interface */ function module$exports$mod_B() {}",
                "module$exports$mod_B.prototype.f = function() {};"),
            lines(
                "/** @const */ var module$exports$mod_A = {};",
                "/**@type {module$exports$mod_B} */ var module$contents$mod_A_b;")
        });
  }

  public void testRewriteGoogModuleAliasesWithPrototypeGets2() {
    test(
        new String[] {
            lines(
                "goog.module('mod_B');",
                "",
                "/** @interface */ function B(){}",
                "",
                "exports = B;"),
            lines(
                "goog.module('mod_A');",
                "",
                "var B = goog.require('mod_B');",
                "B.prototype;",
                "",
                "/** @type {B} */",
                "var b;")
        },
        new String[] {
            "/**@interface */ function module$exports$mod_B() {}",
            lines(
                "/** @const */ var module$exports$mod_A = {}",
                "module$exports$mod_B.prototype;",
                "/**@type {module$exports$mod_B} */ var module$contents$mod_A_b;")
        });
  }

  public void testLegacyModuleIsUninlined() {
    test(
        lines(
            "goog.module('mod.ns');",
            "goog.module.declareLegacyNamespace();",
            "",
            "class Foo {}",
            "",
            "exports.Foo = Foo;"),
        lines(
            "goog.provide('mod.ns');",
            "class module$contents$mod$ns_Foo {}",
            "/** @const */ mod.ns.Foo = module$contents$mod$ns_Foo;"));
  }

  public void testLegacyModuleExportStillExported() {
    test(
          lines(
              "goog.module('modA');",
              "goog.module.declareLegacyNamespace();",
              "",
              "class Foo {}",
              "exports = { /** @export */ Foo};"),
          lines(
              "goog.provide('modA');",
              "class module$contents$modA_Foo {}",
              "/** @const @export */ modA.Foo = module$contents$modA_Foo;"));
  }

  public void testMultiplyExportedSymbolDoesntCrash() {
    test(
        lines(
            "goog.module('mod');",
            "",
            "class Foo {}",
            "",
            "exports.name1 = Foo;",
            "exports.name2 = Foo;"),
        lines(
            "/** @const */ var module$exports$mod = {};",
            "module$exports$mod.name1 = class {};",
            "/** @const */ module$exports$mod.name2 = module$exports$mod.name1;"));
  }

  public void testIjsFileInExterns() {
    allowExternsChanges();
    testNoWarning(
        lines(
            "/** @externs */",
            "goog.module('mod_B');",
            "",
            "/** @interface */ function B(){}",
            "",
            "exports = B;"),
        lines(
            "goog.module('mod_A');",
            "",
            "var B = goog.require('mod_B');",
            "",
            "/** @constructor @implements {B} */",
            "function A() {}"));

    testNoWarning(
        lines(
            "/** @externs */",
            "goog.loadModule(function(exports) { 'use strict';",
            "  goog.module('mod_B');",
            "",
            "  /** @interface */ function B(){}",
            "",
            "  exports = B;",
            "  return exports;",
            "});"),
        lines(
            "goog.module('mod_A');",
            "",
            "var B = goog.require('mod_B');",
            "",
            "/** @constructor @implements {B} */",
            "function A() {}"));

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_NEXT);
    testNoWarning(
        lines(
            "/** @externs */",
            "goog.loadModule(function(exports) { 'use strict';",
            "  goog.module('mod_B');",
            "",
            "  /** @interface */ function B(){}",
            "",
            "  exports.B = B;",
            "  return exports;",
            "});"),
        lines(
            "goog.module('mod_A');",
            "",
            "var {B} = goog.require('mod_B');",
            "",
            "/** @constructor @implements {B} */",
            "function A() {}"));
  }

  // This pass only handles goog.modules. ES6 modules are left alone.
  public void testEs6Module() {
    testSame("export var x;");
    testSame("import {x} from 'y';");
  }
}
