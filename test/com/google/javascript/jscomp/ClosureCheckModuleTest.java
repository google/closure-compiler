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
import static com.google.javascript.jscomp.ClosureCheckModule.GOOG_MODULE_IN_NON_MODULE;
import static com.google.javascript.jscomp.ClosureCheckModule.GOOG_MODULE_MISPLACED;
import static com.google.javascript.jscomp.ClosureCheckModule.GOOG_MODULE_REFERENCES_THIS;
import static com.google.javascript.jscomp.ClosureCheckModule.GOOG_MODULE_USES_THROW;
import static com.google.javascript.jscomp.ClosureCheckModule.INCORRECT_SHORTNAME_CAPITALIZATION;
import static com.google.javascript.jscomp.ClosureCheckModule.INVALID_DESTRUCTURING_REQUIRE;
import static com.google.javascript.jscomp.ClosureCheckModule.LEGACY_NAMESPACE_ARGUMENT;
import static com.google.javascript.jscomp.ClosureCheckModule.LEGACY_NAMESPACE_NOT_AFTER_GOOG_MODULE;
import static com.google.javascript.jscomp.ClosureCheckModule.LEGACY_NAMESPACE_NOT_AT_TOP_LEVEL;
import static com.google.javascript.jscomp.ClosureCheckModule.LET_GOOG_REQUIRE;
import static com.google.javascript.jscomp.ClosureCheckModule.MULTIPLE_MODULES_IN_FILE;
import static com.google.javascript.jscomp.ClosureCheckModule.ONE_REQUIRE_PER_DECLARATION;
import static com.google.javascript.jscomp.ClosureCheckModule.REFERENCE_TO_FULLY_QUALIFIED_IMPORT_NAME;
import static com.google.javascript.jscomp.ClosureCheckModule.REFERENCE_TO_MODULE_GLOBAL_NAME;
import static com.google.javascript.jscomp.ClosureCheckModule.REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME;
import static com.google.javascript.jscomp.ClosureCheckModule.REQUIRE_NOT_AT_TOP_LEVEL;
import static com.google.javascript.jscomp.ClosureCheckModule.USE_OF_GOOG_PROVIDE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.INVALID_DESTRUCTURING_FORWARD_DECLARE;
import static com.google.javascript.jscomp.ClosurePrimitiveErrors.MODULE_USES_GOOG_MODULE_GET;

import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ClosureCheckModuleTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return (externs, root) -> {
      GatherModuleMetadata gatherModuleMetadata =
          new GatherModuleMetadata(
              compiler, /* processCommonJsModules= */ false, ResolutionMode.BROWSER);
      gatherModuleMetadata.process(externs, root);
      new ClosureCheckModule(compiler, compiler.getModuleMetadataMap()).process(externs, root);
    };
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.ERROR);
    options.setWarningLevel(DiagnosticGroups.STRICT_MODULE_CHECKS, CheckLevel.ERROR);
    return options;
  }

  @Test
  public void testGoogModuleThisInSubclass() {
    testSame(
        """
        goog.module('xyz');
        class Foo {
          x = 5;
        }
        class Bar extends Foo {
           z = this.x;
        }
        """);

    testSame(
        """
        goog.module('xyz');
        class Foo {
          [x] = 5;
        }
        class Bar extends Foo {
           z = this[x];
        }
        """);

    testSame(
        """
        goog.module('xyz');
        class Foo {
          [x] = 5;
        }
        class Bar extends Foo {
           [z] = this[x];
        }
        """);
  }

  @Test
  public void testGoogModuleThisOnFields() {
    // Ensure `this.property` is allowed as an assignment on public fields.
    testSame(
        """
        goog.module('xyz');
        class Foo {
          x = 5;
          y = this.x
        }
        """);

    testSame(
        """
        goog.module('xyz');
        class Foo {
          [x] = 5;
          [y] = this[x]
        }
        """);

    testError(
        """
        goog.module('xyz');
        class Foo {
          [x] = 5;
          [this.x] = 6
        }
        """,
        GOOG_MODULE_REFERENCES_THIS);
  }

  @Test
  public void testStaticGoogModuleThisOnStaticFields() {
    // Allow this to be allowed when a field is already declared
    testSame(
        """
        goog.module('math')
        class Foo {
          static x = 5;
          static y = this.x
        }
        """);

    testSame(
        """
        goog.module('math')
        class Foo {
          static [x] = 5;
          static [y] = this[x]
        }
        """);
  }

  @Test
  public void testGoogModuleReferencesThis() {
    testError("goog.module('xyz');\nfoo.call(this, 1, 2, 3);", GOOG_MODULE_REFERENCES_THIS);

    testError(
        """
        goog.module('xyz');

        var x = goog.require('other.x');

        if (x) {
          alert(this);
        }
        """,
        GOOG_MODULE_REFERENCES_THIS);

    testSame(
        """
        goog.module('xyz');

        class Foo {
          constructor() {
            this.x = 5;
          }
        }

        exports = Foo;
        """);
  }

  @Test
  public void testGoogModuleUsesThrow() {
    testError("goog.module('xyz');\nthrow 4;", GOOG_MODULE_USES_THROW);

    testError(
        """
        goog.module('xyz');

        var x = goog.require('other.x');

        if (x) {
          throw 5;
        }
        """,
        GOOG_MODULE_USES_THROW);
  }

  @Test
  public void testGoogModuleGetAtTopLevel() {
    testError("goog.module('xyz');\ngoog.module.get('abc');", MODULE_USES_GOOG_MODULE_GET);

    testError(
        """
        goog.module('xyz');

        var x = goog.require('other.x');

        if (x) {
          var y = goog.module.get('abc');
        }
        """,
        MODULE_USES_GOOG_MODULE_GET);

    testSame(
        """
        goog.module('xyz');

        var x = goog.require('other.x');

        function f() {
          var y = goog.module.get('abc');
        }
        """);
  }

  @Test
  public void testMultipleGoogModules() {
    testError(
        """
        goog.module('xyz');
        goog.module('abc');

        var x = goog.require('other.x');
        """,
        MULTIPLE_MODULES_IN_FILE);
  }

  @Test
  public void testMisplacedGoogModuleCall() {
    testError(
        """
        var x;
        goog.module('xyz');
        """,
        GOOG_MODULE_MISPLACED);

    testError(
        """
        ;
        goog.module('xyz');
        """,
        GOOG_MODULE_MISPLACED);

    testError(
        """
        function fn() {
          goog.module('xyz');
         }
        """,
        GOOG_MODULE_MISPLACED);

    testError("const mod =  goog.module('xyz');", GOOG_MODULE_MISPLACED);

    testError(
        """
        goog.loadModule(function(exports) {
          var x;
          goog.module('xyz');
          return exports;
        })
        """,
        GOOG_MODULE_IN_NON_MODULE);
  }

  @Test
  public void testBundledGoogModules() {
    testError(
        """
        goog.loadModule(function(exports){
          'use strict';
          goog.module('xyz');
          foo.call(this, 1, 2, 3);
          return exports;
        });
        """,
        GOOG_MODULE_REFERENCES_THIS);

    testError(
        """
        goog.loadModule(function(exports){
          'use strict';
          goog.module('foo.example.ClassName');
          /** @constructor @export */ function ClassName() {}
          exports = ClassName;
          return exports;
        });
        """,
        ClosureCheckModule.AT_EXPORT_IN_GOOG_MODULE);

    testSame(
        """
        goog.loadModule(function(exports){
          'use strict';
          goog.module('Xyz');
          exports = class {}
          return exports;
        });
        goog.loadModule(function(exports){
          goog.module('abc');
          var Foo = goog.require('Xyz');
          var x = new Foo;
          return exports;
        });
        """);

    testError(
        """
        goog.loadModule(function(exports){
          'use strict';
          goog.module('xyz');
          goog.module('abc');
          var x = goog.require('other.x');
          return exports;
        });
        """,
        MULTIPLE_MODULES_IN_FILE);
  }

  @Test
  public void testBundledGoogModulesAndProvides() {
    test(
        srcs(
            """
            goog.provide('first.provide');
            first.provide = 0;

            goog.provide('second.provide');
            second.provide = 0;

            goog.loadModule(function(exports){
              'use strict';
              goog.module('Xyz');
              const first = goog.require('first.provide');
              exports = class {}
              return exports;
            });

            goog.loadModule(function(exports){
              goog.module('abc');
              const Foo = goog.require('Xyz');
              const second = goog.require('second.provide');
              var x = new Foo;
              return exports;
            });
            """),
        error(USE_OF_GOOG_PROVIDE),
        error(USE_OF_GOOG_PROVIDE));
  }

  @Test
  public void testBundledGoogModulesAndProvidesWithGoog() {
    test(
        srcs(
            """
            /** @provideGoog */
            var goog = {};

            goog.provide('first.provide');
            first.provide = 0;

            goog.provide('second.provide');
            second.provide = 0;

            goog.loadModule(function(exports){
              'use strict';
              goog.module('Xyz');
              const first = goog.require('first.provide');
              exports = class {}
              return exports;
            });

            goog.loadModule(function(exports){
              goog.module('abc');
              const Foo = goog.require('Xyz');
              const second = goog.require('second.provide');
              var x = new Foo;
              return exports;
            });
            """),
        error(USE_OF_GOOG_PROVIDE),
        error(USE_OF_GOOG_PROVIDE));
  }

  @Test
  public void testGoogModuleReferencesGlobalName() {
    testError("goog.module('x.y.z');\nx.y.z = function() {};", REFERENCE_TO_MODULE_GLOBAL_NAME);
  }

  @Test
  public void testIllegalAtExport() {
    testError(
        """
        goog.module('foo.example.ClassName');

        /** @constructor @export */ function ClassName() {}

        exports = ClassName;
        """,
        ClosureCheckModule.AT_EXPORT_IN_GOOG_MODULE);

    testError(
        """
        goog.module('foo.example.ClassName');

        /** @export */ class ClassName {}

        exports = ClassName;
        """,
        ClosureCheckModule.AT_EXPORT_IN_GOOG_MODULE);

    testError(
        """
        goog.module('foo.example.ClassName');

        /** @constructor */ function ClassName() {}

        /** @export */
        exports = ClassName;
        """,
        ClosureCheckModule.AT_EXPORT_IN_NON_LEGACY_GOOG_MODULE);

    testError(
        """
        goog.module('foo.example.ns');

        /** @constructor */ function ClassName() {}

        /** @export */
        exports.ClassName = ClassName;
        """,
        ClosureCheckModule.AT_EXPORT_IN_NON_LEGACY_GOOG_MODULE);

    testError(
        """
        goog.module('foo.example.ClassName');
        goog.module.declareLegacyNamespace();

        /** @constructor @export */ function ClassName() {}

        exports = ClassName;
        """,
        ClosureCheckModule.AT_EXPORT_IN_GOOG_MODULE);

    // TODO(b/123020550): warn for this, as the actual name exported will be
    //   module$contents$foo_example_ClassName.Builder
    testSame(
        """
        goog.module('foo.example.ClassName');

        class ClassName {}

        /** @export */
        ClassName.Builder = function() {};

        exports = ClassName;
        """);
  }

  @Test
  public void testLegalAtExport() {
    testSame(
        """
        goog.module('foo.example.ClassName');

        class ClassName {
          constructor() {
            /** @export */
            this.prop;
            /** @export */
            this.anotherProp = false;
          }
        }

        exports = ClassName;
        """);

    testSame(
        """
        goog.module('foo.example.ClassName');

        var ClassName = class {
          constructor() {
            /** @export */
            this.prop;
            /** @export */
            this.anotherProp = false;
          }
        }

        exports = ClassName;
        """);

    testSame(
        """
        goog.module('foo.example.ClassName');

        /** @constructor */
        function ClassName() {
          /** @export */
          this.prop;
          /** @export */
          this.anotherProp = false;
        }

        exports = ClassName;
        """);

    testSame(
        """
        goog.module('foo.example.ClassName');

        /** @constructor */
        var ClassName = function() {
          /** @export */
          this.prop;
          /** @export */
          this.anotherProp = false;
        };

        exports = ClassName;
        """);

    testSame(
        """
        goog.module('foo.example.ClassName');
        goog.module.declareLegacyNamespace();

        /** @constructor */ function ClassName() {}

        /** @export */
        exports = ClassName;
        """);

    testSame(
        """
        goog.module('foo.example.ns');
        goog.module.declareLegacyNamespace();

        /** @constructor */ function ClassName() {}

        /** @export */
        exports.ClassName = ClassName;
        """);

    testSame(
        """
        goog.module('foo.example.ClassName');

        /** @constructor */ var exports = function() {}

        /** @export */
        exports.prototype.fly = function() {};
        """);
  }

  @Test
  public void testIllegalDeclareLegacyNamespace() {
    test(
        srcs(
            """
            goog.provide('a.provided.namespace');
            goog.module.declareLegacyNamespace();
            """),
        error(DECLARE_LEGACY_NAMESPACE_IN_NON_MODULE),
        error(USE_OF_GOOG_PROVIDE));

    testError(
        """
        goog.module('xyz');
        const ns = goog.module.declareLegacyNamespace();
        """,
        LEGACY_NAMESPACE_NOT_AT_TOP_LEVEL);

    testError(
        """
        goog.module('xyz');
        if (cond) {
          goog.module.declareLegacyNamespace();
        }
        """,
        LEGACY_NAMESPACE_NOT_AFTER_GOOG_MODULE);

    testError(
        """
        goog.module('xyz');
        var foo = 0;
        goog.module.declareLegacyNamespace();
        """,
        LEGACY_NAMESPACE_NOT_AFTER_GOOG_MODULE);

    testError(
        """
        goog.loadModule(function(exports) {
          goog.module('xyz');
          var x;
          goog.module.declareLegacyNamespace();
          return exports
        });
        """,
        LEGACY_NAMESPACE_NOT_AFTER_GOOG_MODULE);

    testError(
        """
        goog.module('my.mod');
        goog.module.declareLegacyNamespace('some comment');
        """,
        LEGACY_NAMESPACE_ARGUMENT);
  }

  @Test
  public void testIllegalGoogRequires() {
    testError(
        """
        goog.module('xyz');

        var foo = goog.require('other.x').foo;
        """,
        REQUIRE_NOT_AT_TOP_LEVEL);

    testError(
        """
        goog.module('xyz');

        var x = goog.require('other.x').foo.toString();
        """,
        REQUIRE_NOT_AT_TOP_LEVEL);

    testError(
        """
        goog.module('xyz');

        var moduleNames = [goog.require('other.x').name];
        """,
        REQUIRE_NOT_AT_TOP_LEVEL);

    testError(
        """
        goog.module('xyz');

        exports = [goog.require('other.x').name];
        """,
        REQUIRE_NOT_AT_TOP_LEVEL);

    testError(
        """
        goog.module('xyz');

        var a = goog.require('foo.a'), b = goog.require('foo.b');
        """,
        ONE_REQUIRE_PER_DECLARATION);

    testError(
        """
        goog.module('xyz');

        var [foo, bar] = goog.require('other.x');
        """,
        INVALID_DESTRUCTURING_REQUIRE);

    testError(
        """
        goog.module('xyz');

        var {foo, bar = 'str'} = goog.require('other.x');
        """,
        INVALID_DESTRUCTURING_REQUIRE);

    testError(
        """
        goog.module('xyz');

        var {foo, bar: {name}} = goog.require('other.x');
        """,
        INVALID_DESTRUCTURING_REQUIRE);

    testError(
        """
        goog.module('xyz');

        var foo = goog.require('abc.foo');
        var foo = goog.require('def.foo');
        """,
        DUPLICATE_NAME_SHORT_REQUIRE);

    testError(
        """
        goog.module('xyz');

        var {foo, bar} = goog.require('abc');
        var foo = goog.require('def.foo');
        """,
        DUPLICATE_NAME_SHORT_REQUIRE);
  }

  @Test
  public void testIllegalShortImportReferencedByLongName() {
    testError(
        """
        goog.module('x.y.z');

        var A = goog.require('foo.A');

        exports = function() { return new foo.A; };
        """,
        REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME);
  }

  @Test
  public void testCorrectShortImportSuggestedInJsDoc() {
    testError(
        srcs(
            """
            goog.module('x.y.z');

            var A = goog.require('foo.A');
            var B = goog.require('foo.A.B');

            /** @type {foo.A.B} */ var b;
            """),
        error(REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME)
            .withMessageContaining("Please use the short name 'B' instead."));
  }

  @Test
  public void testCorrectShortImportSuggestedInJsCode() {
    testError(
        srcs(
            """
            goog.module('x.y.z');

            var A = goog.require('foo.A');
            var B = goog.require('foo.A.B');

            ref(foo.A.B);
            """),
        error(REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME)
            .withMessageContaining("Please use the short name 'B' instead."));
  }

  @Test
  public void testIllegalShortImportReferencedByLongNameInJsDoc() {
    testError(
        """
        goog.module('x.y.z');

        var A = goog.require('foo.A');

        /** @constructor @implements {foo.A} */ function B() {}
        """,
        REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME);

    testError(
        """
        goog.module('x.y.z');

        var A = goog.requireType('foo.A');

        /** @constructor @implements {foo.A} */ function B() {}
        """,
        REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME);

    testError(
        """
        goog.module('x.y.z');

        var A = goog.require('foo.A');

        /** @type {foo.A} */ var a;
        """,
        REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME);

    testError(
        """
        goog.module('x.y.z');

        var A = goog.requireType('foo.A');

        /** @type {foo.A} */ var a;
        """,
        REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME);

    testSame(
        """
        goog.module('x.y.z');

        var A = goog.require('foo.A');

        /** @type {A} */ var a;
        """);

    testSame(
        """
        goog.module('x.y.z');

        var A = goog.requireType('foo.A');

        /** @type {A} */ var a;
        """);

    testSame(
        """
        goog.module('x.y.z');

        var Foo = goog.require('Foo');

        /** @type {Foo} */ var a;
        """);

    testSame(
        """
        goog.module('x.y.z');

        var Foo = goog.requireType('Foo');

        /** @type {Foo} */ var a;
        """);

    testError(
        """
        goog.module('x.y.z');

        var ns = goog.require('some.namespace');

        /** @type {some.namespace.Foo} */ var foo;
        """,
        REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME);

    testError(
        """
        goog.module('x.y.z');

        var ns = goog.requireType('some.namespace');

        /** @type {some.namespace.Foo} */ var foo;
        """,
        REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME);

    testError(
        """
        goog.module('x.y.z');

        var ns = goog.require('some.namespace');

        /** @type {Array<some.namespace.Foo>} */ var foos;
        """,
        REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME);

    testError(
        """
        goog.module('x.y.z');

        var ns = goog.requireType('some.namespace');

        /** @type {Array<some.namespace.Foo>} */ var foos;
        """,
        REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME);
  }

  @Test
  public void testIllegalShortImportDestructuring() {
    testError(
        """
        goog.module('x.y.z');

        var {doThing} = goog.require('foo.utils');

        exports = function() { return foo.utils.doThing(''); };
        """,
        REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME);

    testError(
        """
        goog.module('x.y.z');

        var {doThing: fooDoThing} = goog.require('foo.utils');

        exports = function() { return foo.utils.doThing(''); };
        """,
        REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME);
  }

  @Test
  public void testIllegalRequireNoAlias() {
    testError(
        """
        goog.module('x.y.z');

        goog.require('foo.utils');

        exports = function() { return foo.utils.doThing(''); };
        """,
        REFERENCE_TO_FULLY_QUALIFIED_IMPORT_NAME);
  }

  @Test
  public void testIllegalRequireTypeNoAlias() {
    testError(
        """
        goog.module('x.y.z');

        goog.requireType('foo.Bar');

        /** @type {foo.Bar} */ var foo;
        """,
        REFERENCE_TO_FULLY_QUALIFIED_IMPORT_NAME);
  }

  @Test
  public void testLegalForwardDeclareNoAlias() {
    testNoWarning(
        """
        goog.module('x.y.z');

        goog.forwardDeclare('foo.utils');

        exports = function() { return foo.utils.doThing(''); };
        """);
  }

  @Test
  public void testSingleNameImportNoAlias1() {
    testError(
        """
        goog.module('x.y.z');

        goog.require('foo');

        exports = function() { return foo.doThing(''); };
        """,
        REFERENCE_TO_FULLY_QUALIFIED_IMPORT_NAME);
  }

  @Test
  public void testSingleNameImportWithRenamingAlias() {
    testError(
        """
        goog.module('x.y.z');

        var bar = goog.require('foo');

        exports = function() { return foo.doThing(''); };
        """,
        REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME);
  }

  @Test
  public void testSingleNameImportWithRenamingAlias_forwardDeclare() {
    testError(
        """
        goog.module('x.y.z');

        var bar = goog.forwardDeclare('foo');

        exports = function() { return foo.doThing(''); };
        """,
        REFERENCE_TO_SHORT_IMPORT_BY_LONG_NAME_INCLUDING_SHORT_NAME);
  }

  @Test
  public void testSingleNameImportCrossAlias() {
    testSame(
        """
        goog.module('x.y.z');

        var bar = goog.require('foo');
        var foo = goog.require('bar');

        exports = function() { return foo.doThing(''); };
        """);
  }

  @Test
  public void testLegalSingleNameImport() {
    testSame(
        """
        goog.module('x.y.z');

        var foo = goog.require('foo');

        exports = function() { return foo.doThing(''); };
        """);
  }

  @Test
  public void testSingleNameImportShadowed() {
    testSame(
        """
        goog.module('x.y.z');

        // for side-effects only
        goog.require('foo');

        exports = function(foo) { return foo.doThing(''); };
        """);
  }

  @Test
  public void testIllegalLetShortRequire() {
    testSame(
        """
        goog.module('xyz');

        let a = goog.forwardDeclare('foo.a');
        """);

    testError(
        """
        goog.module('xyz');

        let a = goog.require('foo.a');
        """,
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
        """
        goog.module('xyz');

        var {assert} = goog.require('goog.asserts');
        """);

    testSame(
        """
        goog.module('xyz');

        const {assert} = goog.require('goog.asserts');
        """);

    testSame(
        """
        goog.module('xyz');

        const {assert, fail} = goog.require('goog.asserts');
        """);
  }

  @Test
  public void testShorthandNameConvention() {
    testSame(
        """
        goog.module('xyz');

        const googAsserts = goog.require('goog.asserts');
        """);

    testError(
        """
        goog.module('xyz');

        const GoogAsserts = goog.require('goog.asserts');
        """,
        INCORRECT_SHORTNAME_CAPITALIZATION,
        "The capitalization of short name GoogAsserts is incorrect; it should be googAsserts.");

    testSame(
        """
        goog.module('xyz');

        const Event = goog.require('goog.events.Event');
        """);

    testError(
        """
        goog.module('xyz');

        const event = goog.require('goog.events.Event');
        """,
        INCORRECT_SHORTNAME_CAPITALIZATION,
        "The capitalization of short name event is incorrect; it should be Event.");

    testSame(
        """
        goog.module('xyz');

        const Event = goog.require('google3.closure.events.event');
        """);
  }

  @Test
  public void testNonModuleLevelExports() {
    testError(
        """
        goog.module('xyz');

        if (window.exportMe) { exports = 5; }
        """,
        EXPORT_NOT_AT_MODULE_SCOPE);

    testError(
        """
        goog.module('xyz');

        window.exportMe && (exports = 5);
        """,
        EXPORT_NOT_A_STATEMENT);

    testError(
        """
        goog.module('xyz');

        exports.f = () => { exports.me = 5; }
        """,
        EXPORT_NOT_AT_MODULE_SCOPE);

    testSame(
        """
        goog.module('xyz');

        exports = class {};
        exports.staticMethod = () => { exports.me = 5; }
        """);
  }

  @Test
  public void testRepeatedExports() {
    testError(
        """
        goog.module('xyz');

        exports = 5;
        exports = 'str';
        """,
        EXPORT_REPEATED_ERROR);

    testError(
        """
        goog.module('xyz');

        exports.y = 5;
        exports.y = 'str';
        """,
        EXPORT_REPEATED_ERROR);

    testSame(
        """
        goog.module('xyz');

        exports = class {};
        exports.y = 'str';
        exports.y = decorate(exports.y);
        """);
  }

  @Test
  public void testDontCrashOnTrailingDot() {
    testSame(
        """
        goog.module('foo');

        var a = goog.require('abc.');
        """);
  }

  @Test
  public void testAllowGoogProvideDeclaration() {
    testSame("const goog = {}; goog.provide = function(ns) {};");
  }

  @Test
  public void testWarnOnGoogProvideCall() {
    testError("goog.provide('foo.bar');", ClosureCheckModule.USE_OF_GOOG_PROVIDE);
  }
}
