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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.TranspilationUtil.CANNOT_CONVERT_YET;
import static com.google.javascript.jscomp.TypeCheck.INSTANTIATE_ABSTRACT_CLASS;

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.jscomp.serialization.ConvertTypesToColors;
import com.google.javascript.jscomp.serialization.SerializationOptions;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test cases for ES6 transpilation.
 *
 * <p>This class actually tests several transpilation passes together. See {@link #getProcessor}.
 */
@RunWith(JUnit4.class)
public final class Es6TranspilationIntegrationTest extends CompilerTestCase {

  public Es6TranspilationIntegrationTest() {
    super(getDefaultExterns());
  }

  private static String getDefaultExterns() {
    return getDefaultExternsBuilder().build();
  }

  private static TestExternsBuilder getDefaultExternsBuilder() {
    return new TestExternsBuilder()
        .addAsyncIterable()
        .addArray()
        .addArguments()
        .addObject()
        .addMath()
        .addExtra(
            // stubs of runtime libraries
            """
            /** @const */
            var $jscomp = {};
            $jscomp.generator = {};
            $jscomp.generator.createGenerator = function() {};
            /** @constructor */
            $jscomp.generator.Context = function() {};
            /** @constructor */
            $jscomp.generator.Context.PropertyIterator = function() {};
            $jscomp.asyncExecutePromiseGeneratorFunction = function(program) {};
            """);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2016);
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    // We will do normalization as part of transpilation in getProcessor()
    disableNormalize();
    disableTypeCheck();
    disableCompareJsDoc(); // optimization passes see simplified JSDoc.
    // Normalization does renaming on the externs (parameter names and maybe other things) and the
    // pass that inverts renaming ignores the externs, leaving them changed.
    allowExternsChanges();
    // Our getProcessor() returns a fake "pass" that actually runs several passes,
    // including one that undoes some of the changes that were made.
    // this confuses the logic for validating AST change marking.
    // That logic is really only valid when testing a single, real pass.
    disableValidateAstChangeMarking();
    setGenericNameReplacements(
        ImmutableMap.<String, String>builder()
            .putAll(Es6NormalizeClasses.GENERIC_NAME_REPLACEMENTS)
            .put("KEY", "$jscomp$key$")
            .buildOrThrow());
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    PhaseOptimizer optimizer = new PhaseOptimizer(compiler, null);

    CompilerOptions compilerOptions = compiler.getOptions();
    PassListBuilder passes = new PassListBuilder(compilerOptions);

    passes.maybeAdd(
        PassFactory.builder()
            .setName("es6InjectRuntimeLibraries")
            .setInternalFactory(InjectTranspilationRuntimeLibraries::new)
            .build());

    passes.maybeAdd(
        PassFactory.builder()
            .setName("rewritePolyfills")
            .setInternalFactory(
                c ->
                    new RewritePolyfills(
                        c, /* injectPolyfills= */ true, /* isolatePolyfills= */ false, null))
            .build());

    passes.maybeAdd(
        PassFactory.builder()
            .setName("convertTypesToColors")
            .setInternalFactory(
                (c) ->
                    new ConvertTypesToColors(
                        c, SerializationOptions.builder().setIncludeDebugInfo(true).build()))
            .build());

    passes.maybeAdd(
        PassFactory.builder()
            .setName(PassNames.NORMALIZE)
            .setInternalFactory((abstractCompiler) -> Normalize.builder(abstractCompiler).build())
            .build());
    TranspilationPasses.addTranspilationPasses(passes, compilerOptions);
    // Since we're testing the transpile-only case, we need to put back the original variable names
    // where possible once transpilation is complete. This matches the behavior in
    // DefaultPassConfig. See comments there for further explanation.
    passes.maybeAdd(
        PassFactory.builder()
            .setName("invertContextualRenaming")
            .setInternalFactory(MakeDeclaredNamesUnique::getContextualRenameInverter)
            .build());
    optimizer.consume(passes.build());

    return optimizer;
  }

  private void rewriteUniqueIdAndTest(Sources srcs, Expected originalExpected) {
    Externs externs = externs("");
    test(externs, srcs, originalExpected);
  }

  @Test
  public void testObjectLiteralStringKeysWithNoValue() {
    test("var x = {a, b};", "var x = {a: a, b: b};");
    assertThat(getLastCompiler().getInjectedLibraries()).isEmpty();
  }

  @Test
  public void testSpreadLibInjection() {
    test("var x = [...a];", "var x=[].concat((0, $jscomp.arrayFromIterable)(a))");
    assertThat(getLastCompiler().getInjectedLibraries())
        .containsExactly("es6/util/arrayfromiterable");
  }

  @Test
  public void testObjectLiteralMemberFunctionDef() {
    test(
        "var x = {/** @return {number} */ a() { return 0; } };",
        "var x = {/** @return {number} */ a: function() { return 0; } };");
    assertThat(getLastCompiler().getInjectedLibraries()).isEmpty();
  }

  @Test
  public void testClassStatement() {
    test("class C { }", "/** @constructor */ var C = function() {};");
    test("class C { constructor() {} }", "/** @constructor */ var C = function() {};");
    test(
        "class C { method() {}; }",
        """
        /** @constructor */
        var C = function() {};
        C.prototype.method = function() {};
        """);
    test(
        "class C { constructor(a) { this.a = a; } }",
        "/** @constructor */ var C = function(a) { this.a = a; };");

    test(
        "class C { constructor() {} foo() {} }",
        """
        /** @constructor */
        var C = function() {};
        C.prototype.foo = function() {};
        """);

    test(
        "class C { constructor() {}; foo() {}; bar() {} }",
        """
        /** @constructor */
        var C = function() {};
        C.prototype.foo = function() {};
        C.prototype.bar = function() {};
        """);

    test(
        "class C { foo() {}; bar() {} }",
        """
        /** @constructor */
        var C = function() {};
        C.prototype.foo = function() {};
        C.prototype.bar = function() {};
        """);

    test(
        """
        class C {
          constructor(a) { this.a = a; }

          foo() { console.log(this.a); }

          bar() { alert(this.a); }
        }
        """,
        """
        /** @constructor */
        var C = function(a) { this.a = a; };
        C.prototype.foo = function() { console.log(this.a); };
        C.prototype.bar = function() { alert(this.a); };
        """);

    rewriteUniqueIdAndTest(
        srcs(
            """
            if (true) {
               class Foo{}
            } else {
               class Foo{}
            }
            """),
        expected(
            """
            if (true) {
                /** @constructor */
                var Foo = function() {};
            } else {
                /** @constructor */
                var Foo$jscomp$1 = function() {};
            }
            """));
  }

  @Test
  public void testAnonymousSuper() {
    test(
        "f(class extends D { f() { super.g() } })",
        """
        /** @const @constructor */
        var CLASS_DECL$0 = function() {
          return D.apply(this, arguments) || this;
        };
        $jscomp.inherits(CLASS_DECL$0, D);
        CLASS_DECL$0.prototype.f = function() {
          D.prototype.g.call(this);
        };
        f(CLASS_DECL$0);
        """);
  }

  @Test
  public void testNewTarget() {
    testError("function Foo() { new.target; }", CANNOT_CONVERT_YET);
    testError("class Example { foo() { new.target; } }", CANNOT_CONVERT_YET);
  }

  @Test
  public void testClassWithJsDoc() {
    test("class C { }", "/** @constructor */ var C = function() { };");

    test(
        "/** @deprecated */ class C { }", "/** @constructor @deprecated */ var C = function() {};");

    test(
        "/**              @dict */ class C              { }", //
        "/** @constructor @dict */ var   C = function() { };");

    test(
        "/** @template T */ class C { }", "/** @constructor @template T */ var C = function() {};");

    test("/** @final */ class C { }", "/** @constructor @final */ var C = function() {};");

    test("/** @private */ class C { }", "/** @constructor @private */ var C = function() {};");
  }

  @Test
  public void testInterfaceWithJsDoc() {
    test(
        """
        /**
         * Converts Xs to Ys.
         * @interface
         */
        class Converter {
          /**
           * @param {X} x
           * @return {Y}
           */
          convert(x) {}
        }
        """,
        """
        /**
         * Converts Xs to Ys.
         * @interface
         */
        var Converter = function() { };

        /**
         * @param {X} x
         * @return {Y}
         */
        Converter.prototype.convert = function(x) {};
        """);
  }

  @Test
  public void testRecordWithJsDoc() {
    test(
        """
        /**
         * @record
         */
        class Converter {
          /**
           * @param {X} x
           * @return {Y}
           */
          convert(x) {}
        }
        """,
        """
        /**
         * @record
         */
        var Converter = function() { };

        /**
         * @param {X} x
         * @return {Y}
         */
        Converter.prototype.convert = function(x) {};
        """);
  }

  @Test
  public void testMemberWithJsDoc() {
    test(
        "class C { /** @param {boolean} b */ foo(b) {} }",
        """
        /**
         * @constructor
         */
        var C = function() {};

        /** @param {boolean} b */
        C.prototype.foo = function(b) {};
        """);
  }

  @Test
  public void testClassStatementInsideIf() {
    test("if (foo) { class C { } }", "if (foo) { /** @constructor */ var C = function() {}; }");

    test("if (foo) class C {}", "if (foo) { /** @constructor */ var C = function() {}; }");
  }

  /** Class expressions that are the RHS of a 'var' statement. */
  @Test
  public void testClassExpressionInVar() {
    test("var C = class { }", "/** @constructor */ var C = function() {}");

    test(
        "var C = class { foo() {} }",
        """
        /** @constructor */ var C = function() {}

        C.prototype.foo = function() {}
        """);

    test(
        "var C = class C { }",
        """
        /** @const @constructor */
        var CLASS_DECL$0 = function() {
        };
        /** @constructor */
        var C = CLASS_DECL$0;
        """);

    test(
        "var C = class C { foo() {} }",
        """
        /** @const @constructor */
        var CLASS_DECL$0 = function() {
        };
        CLASS_DECL$0.prototype.foo = function() {
        };
        /** @constructor */
        var C = CLASS_DECL$0;
        """);
  }

  /** Class expressions that are the RHS of an assignment. */
  @Test
  public void testClassExpressionInAssignment() {
    test("goog.example.C = class { }", "/** @constructor */ goog.example.C = function() {}");

    test(
        "goog.example.C = class { foo() {} }",
        """
        /** @constructor */ goog.example.C = function() {}
        goog.example.C.prototype.foo = function() {};
        """);
  }

  @Test
  public void testClassExpressionInAssignment_getElem() {
    test(
        "window['MediaSource'] = class {};",
        """
        /** @const @constructor */
        var CLASS_DECL$0 = function() {
        };
        window["MediaSource"] = CLASS_DECL$0;
        """);
  }

  @Test
  public void testClassExpression() {
    test(
        "var C = new (class {})();",
        """
        /** @const @constructor */
        var CLASS_DECL$0 = function() {
        };
        var C = new CLASS_DECL$0();
        """);
    test(
        "(condition ? obj1 : obj2).prop = class C { };",
        """
        /** @const @constructor */
        var CLASS_DECL$0 = function() {
        };
        (condition ? obj1 : obj2).prop = CLASS_DECL$0;
        """);
  }

  @Test
  public void testAbstractClass() {
    enableTypeCheck();

    test(
        "/** @abstract */ class Foo {} var x = new Foo();",
        "/** @abstract @constructor */ var Foo = function() {}; var x = new Foo();",
        warning(INSTANTIATE_ABSTRACT_CLASS));
  }

  @Test
  public void testClassExpression_cannotConvert() {
    test(
        "var C = new (foo || (foo = class { }))();",
        """
        var C = new (foo || (foo = function() {
          /** @const @constructor */
          var CLASS_DECL$0 = function() {
          };
          return CLASS_DECL$0;
        }()))();
        """);
  }

  @Test
  public void testExtends() {
    test(
        "class D {} class C extends D {}",
        """
        /** @constructor */
        var D = function() {};
        /** @constructor
         * @extends {D}
         */
        var C = function() { D.apply(this, arguments); };
        $jscomp.inherits(C, D);
        """);
    assertThat(getLastCompiler().getInjectedLibraries())
        .containsExactly("es6/util/inherits", "es6/util/construct", "es6/util/arrayfromiterable");

    test(
        "class D {} class C extends D { constructor() { super(); } }",
        """
        /** @constructor */
        var D = function() {};
        /** @constructor @extends {D} */
        var C = function() {
          D.call(this);
        }
        $jscomp.inherits(C, D);
        """);

    test(
        "class D {} class C extends D { constructor(str) { super(str); } }",
        """
        /** @constructor */
        var D = function() {};
        /** @constructor @extends {D} */
        var C = function(str) {
          D.call(this, str);
        }
        $jscomp.inherits(C, D);
        """);

    test(
        "class C extends ns.D { }",
        """
        /** @constructor
         * @extends {ns.D}
         */
        var C = function() {
         return ns.D.apply(this, arguments) || this;
        };
        $jscomp.inherits(C, ns.D);
        """);
  }

  @Test
  public void testExtendNonNativeError() {
    test(
        """
        class Error {
          /** @param {string} msg */
          constructor(msg) {
            /** @const */ this.message = msg;
          }
        }
        class C extends Error {}
        """, // autogenerated constructor
        """
        /** @constructor
         */
        var Error = function(msg) {
          /** @const */ this.message = msg;
        };
        /** @constructor
         * @extends {Error}
         */
        var C = function() { Error.apply(this, arguments); };
        $jscomp.inherits(C, Error);
        """);
    test(
        """
        class Error {
          /** @param {string} msg */
          constructor(msg) {
            /** @const */ this.message = msg;
          }
        }
        class C extends Error {
          constructor() {
            super('C error'); // explicit super() call
          }
        }
        """,
        """
        /** @constructor
         */
        var Error = function(msg) {
          /** @const */ this.message = msg;
        };
        /** @constructor
         * @extends {Error}
         */
        var C = function() { Error.call(this, 'C error'); };
        $jscomp.inherits(C, Error);
        """);
  }

  @Test
  public void testExtendNativeError() {
    test(
        "class C extends Error {}", // autogenerated constructor
"""
/** @constructor
 * @extends {Error}
 */
var C = function() {
  var $jscomp$tmp$error$m1146332801$1;
  $jscomp$tmp$error$m1146332801$1 = Error.apply(this, arguments),
      this.message = $jscomp$tmp$error$m1146332801$1.message,
      ('stack' in $jscomp$tmp$error$m1146332801$1) && (this.stack = $jscomp$tmp$error$m1146332801$1.stack),
      this;
};
$jscomp.inherits(C, Error);
""");
    test(
        """
        class C extends Error {
          constructor() {
            var self = super('C error') || this; // explicit super() call in an expression
          }
        }
        """,
"""
/** @constructor
 * @extends {Error}
 */
var C = function() {
  var $jscomp$tmp$error$m1146332801$1;
  var self =
      ($jscomp$tmp$error$m1146332801$1 = Error.call(this, 'C error'),
          this.message = $jscomp$tmp$error$m1146332801$1.message,
          ('stack' in $jscomp$tmp$error$m1146332801$1) && (this.stack = $jscomp$tmp$error$m1146332801$1.stack),
          this)
      || this;
};
$jscomp.inherits(C, Error);
""");
  }

  @Test
  public void testDynamicExtends() {
    test(
        "class C extends foo() {}",
        """
        /** @const */
        var CLASS_EXTENDS$0 = foo();
        /** @constructor */
        var C = function() {
          return CLASS_EXTENDS$0.apply(this, arguments) || this;
        };
        $jscomp.inherits(C, CLASS_EXTENDS$0);
        """);

    test(
        "class C extends function(){} {}",
        """
        /** @const */
        var CLASS_EXTENDS$0 = function() {
        };
        /** @constructor */
        var C = function() {
          CLASS_EXTENDS$0.apply(this, arguments);
        };
        $jscomp.inherits(C, CLASS_EXTENDS$0);
        """);
  }

  @Test
  public void testExtendsInterface() {
    test(
        """
        /** @interface */
        class D {
          f() {}
        }
        /** @interface */
        class C extends D {
          g() {}
        }
        """,
        """
        /** @interface */
        var D = function() {};
        D.prototype.f = function() {};
        /**
         * @interface
         * @extends{D} */
        var C = function() {};
        $jscomp.inherits(C, D);
        C.prototype.g = function() {};
        """);
  }

  @Test
  public void testExtendsRecord() {
    test(
        """
        /** @record */
        class D {
          f() {}
        }
        /** @record */
        class C extends D {
          g() {}
        }
        """,
        """
        /** @record */
        var D = function() {};
        D.prototype.f = function() {};
        /**
         * @record
         * @extends{D} */
        var C = function() {};
        $jscomp.inherits(C, D);
        C.prototype.g = function() {};
        """);
  }

  @Test
  public void testImplementsInterface() {
    test(
        """
        /** @interface */
        class D {
          f() {}
        }
        /** @implements {D} */
        class C {
          f() {console.log('hi');}
        }
        """,
        """
        /** @interface */
        var D = function() {};
        D.prototype.f = function() {};
        /** @constructor @implements{D} */
        var C = function() {};
        C.prototype.f = function() {console.log('hi');};
        """);
  }

  @Test
  public void testSuperCall() {
    test(
        "class D {} class C extends D { constructor() { super(); } }",
        """
        /** @constructor */
        var D = function() {};
        /** @constructor @extends {D} */
        var C = function() {
          D.call(this);
        }
        $jscomp.inherits(C, D);
        """);

    test(
        "class D {} class C extends D { constructor(str) { super(str); } }",
        """
        /** @constructor */
        var D = function() {}
        /** @constructor @extends {D} */
        var C = function(str) {
          D.call(this,str);
        }
        $jscomp.inherits(C, D);
        """);

    test(
        "class D {} class C extends D { constructor(str, n) { super(str); this.n = n; } }",
        """
        /** @constructor */
        var D = function() {}
        /** @constructor @extends {D} */
        var C = function(str, n) {
          D.call(this,str);
          this.n = n;
        }
        $jscomp.inherits(C, D);
        """);

    test(
        """
        class D {}
        class C extends D {
          constructor() { }
          foo() { return super.foo(); }
        }
        """,
        """
        /** @constructor */
        var D = function() {}
        /** @constructor @extends {D} */
        var C = function() { }
        $jscomp.inherits(C, D);
        C.prototype.foo = function() {
          return D.prototype.foo.call(this);
        }
        """);

    test(
        """
        class D {}
        class C extends D {
          constructor() {}
          foo(bar) { return super.foo(bar); }
        }
        """,
        """
        /** @constructor */
        var D = function() {}
        /** @constructor @extends {D} */
        var C = function() {};
        $jscomp.inherits(C, D);
        C.prototype.foo = function(bar) {
          return D.prototype.foo.call(this, bar);
        }
        """);

    test(
        "class C { method() { class D extends C { constructor() { super(); }}}}",
        """
        /** @constructor */
        var C = function() {}
        C.prototype.method = function() {
          /** @constructor @extends{C} */
          var D = function() {
            C.call(this);
          }
          $jscomp.inherits(D, C);
        };
        """);
  }

  @Test
  public void testSuperKnownNotToChangeThis() {
    test(
        """
        class D {
          /** @param {string} str */
          constructor(str) {
            this.str = str;
            return; // Empty return should not trigger this-changing behavior.
          }
        }
        class C extends D {
          /**
           * @param {string} str
           * @param {number} n
           */
          constructor(str, n) {
        // This is nuts, but confirms that super() used in an expression works.
            super(str).n = n;
        // Also confirm that an existing empty return is handled correctly.
            return;
          }
        }
        """,
        """
        /**
         * @constructor
         */
        var D = function(str) {
          this.str = str;
          return;
        }
        /**
         * @constructor @extends {D}
         */
        var C = function(str, n) {
          (D.call(this,str), this).n = n; // super() returns `this`.
          return;
        }
        $jscomp.inherits(C, D);
        """);
  }

  @Test
  public void testSuperMightChangeThis() {
    // Class D is unknown, so we must assume its constructor could change `this`.
    test(
        """
        class C extends D {
          constructor(str, n) {
        // This is nuts, but confirms that super() used in an expression works.
            super(str).n = n;
        // Also confirm that an existing empty return is handled correctly.
            return;
          }
        }
        """,
        """
        /** @constructor @extends {D} */
        var C = function(str, n) {
          var $jscomp$super$this$m1146332801$0;
          ($jscomp$super$this$m1146332801$0 = D.call(this,str) || this).n = n;
          return $jscomp$super$this$m1146332801$0; // Duplicate because of existing return
        // statement.
          return $jscomp$super$this$m1146332801$0;
        }
        $jscomp.inherits(C, D);
        """);
  }

  @Test
  public void testAlternativeSuperCalls() {
    test(
        """
        class D {
          /** @param {string} name */
          constructor(name) {
            this.name = name;
          }
        }
        class C extends D {
          /** @param {string} str
           * @param {number} n */
          constructor(str, n) {
            if (n >= 0) {
              super('positive: ' + str);
            } else {
              super('negative: ' + str);
            }
            this.n = n;
          }
        }
        """,
        """
        /** @constructor */
        var D = function(name) {
          this.name = name;
        }
        /** @constructor @extends {D} */
        var C = function(str, n) {
          if (n >= 0) {
            D.call(this, 'positive: ' + str);
          } else {
            D.call(this, 'negative: ' + str);
          }
          this.n = n;
        }
        $jscomp.inherits(C, D);
        """);

    // Class being extended is unknown, so we must assume super() could change the value of `this`.
    test(
        """
        class C extends D {
          /** @param {string} str
           * @param {number} n */
          constructor(str, n) {
            if (n >= 0) {
              super('positive: ' + str);
            } else {
              super('negative: ' + str);
            }
            this.n = n;
          }
        }
        """,
        """
        /** @constructor @extends {D} */
        var C = function(str, n) {
          var $jscomp$super$this$m1146332801$0;
          if (n >= 0) {
            $jscomp$super$this$m1146332801$0 = D.call(this, 'positive: ' + str) || this;
          } else {
            $jscomp$super$this$m1146332801$0 = D.call(this, 'negative: ' + str) || this;
          }
          $jscomp$super$this$m1146332801$0.n = n;
          return $jscomp$super$this$m1146332801$0;
        }
        $jscomp.inherits(C, D);
        """);
  }

  @Test
  public void testComputedSuper() {
    test(
        """
        class Foo {
          ['m']() { return 1; }
        }

        class Bar extends Foo {
          ['m']() {
            return super['m']() + 1;
          }
        }
        """,
        """
        /** @constructor */
        var Foo = function() {};
        Foo.prototype['m'] = function() { return 1; };
        /** @constructor @extends {Foo} */
        var Bar = function() { Foo.apply(this, arguments); };
        $jscomp.inherits(Bar, Foo);
        Bar.prototype['m'] = function () { return Foo.prototype['m'].call(this) + 1; };
        """);
  }

  @Test
  public void testSuperMethodInGetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        """
        class Base {
          method() {
            return 5;
          }
        }

        class Subclass extends Base {
          constructor() {
            super();
          }

          get x() {
            return super.method();
          }
        }
        """,
        """
        /** @constructor */
        var Base = function() {};
        Base.prototype.method = function() { return 5; };

        /** @constructor @extends {Base} */
        var Subclass = function() { Base.call(this); };

        $jscomp.inherits(Subclass, Base);
        $jscomp.global.Object.defineProperties(Subclass.prototype, {
          x: {
            configurable:true,
            enumerable:true,
            get: function() { return Base.prototype.method.call(this); },
          }
        });
        """);
  }

  @Test
  public void testSuperMethodInSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        """
        class Base {
          method() {
            this._x = 5;
          }
        }

        class Subclass extends Base {
          constructor() {
            super();
          }

          set x(value) {
            super.method();
          }
        }
        """,
        """
        /** @constructor */
        var Base = function() {};
        Base.prototype.method = function() { this._x = 5; };

        /** @constructor @extends {Base} */
        var Subclass = function() { Base.call(this); };

        $jscomp.inherits(Subclass, Base);
        $jscomp.global.Object.defineProperties(Subclass.prototype, {
          x: {
            configurable:true,
            enumerable:true,
            set: function(value) { Base.prototype.method.call(this); },
          }
        });
        """);
  }

  @Test
  public void testExtendNativeClass() {
    test(
        """
        class FooPromise extends Promise {
          /** @param {string} msg */
        // explicit constructor
          constructor(callback, msg) {
            super(callback);
            this.msg = msg;
          }
        }
        """,
"""
/**
 * @constructor
 * @extends {Promise}
 */
var FooPromise = function(callback, msg) {
  var $jscomp$super$this$m1146332801$0;
  $jscomp$super$this$m1146332801$0 = $jscomp.construct(Promise, [callback], this.constructor)
  $jscomp$super$this$m1146332801$0.msg = msg;
  return $jscomp$super$this$m1146332801$0;
}
$jscomp.inherits(FooPromise, Promise);

""");

    test(
        // automatically generated constructor
        "class FooPromise extends Promise {}",
        """
        /**
         * @constructor
         * @extends {Promise}
         */
        var FooPromise = function() {
          return $jscomp.construct(Promise, arguments, this.constructor)
        }
        $jscomp.inherits(FooPromise, Promise);
        """);
  }

  @Test
  public void testExtendObject() {
    // Object can be correctly extended in transpiled form, but we don't want or need to call
    // the `Object()` constructor in place of `super()`. Just replace `super()` with `this` instead.
    // Test both explicit and automatically generated constructors.
    test(
        """
        class Foo extends Object {
          /** @param {string} msg */
          constructor(msg) {
            super();
            this.msg = msg;
          }
        }
        """,
        """
        /**
         * @constructor @extends {Object}
         */
        var Foo = function(msg) {
          this; // super() replaced with its return value
          this.msg = msg;
        };
        $jscomp.inherits(Foo, Object);
        """);
    test(
        "class Foo extends Object {}",
        """
        /**
         * @constructor @extends {Object}
         */
        var Foo = function() {
          this; // super.apply(this, arguments) replaced with its return value
        };
        $jscomp.inherits(Foo, Object);
        """);
  }

  @Test
  public void testExtendNonNativeObject() {
    // No special handling when Object is redefined.
    Externs customExterns =
        externs(
            new TestExternsBuilder()
                .addAsyncIterable()
                .addArray()
                .addArguments()
                // TODO(sdh): If a normal Object extern is found, then this test fails because
                // the pass adds extra handling for super() possibly changing 'this'.  Does setting
                // the externs to "" to prevent this defeat the purpose of this test?  It became
                // necessary as a result of adding "/** @constructor */ function Object() {}" to
                // the externs used by this test.
                // .addObject()
                .addMath()
                .addExtra(
                    // stubs of runtime libraries
                    """
                    /** @const */
                    var $jscomp = {};
                    $jscomp.generator = {};
                    $jscomp.generator.createGenerator = function() {};
                    /** @constructor */
                    $jscomp.generator.Context = function() {};
                    /** @constructor */
                    $jscomp.generator.Context.PropertyIterator = function() {};
                    $jscomp.asyncExecutePromiseGeneratorFunction = function(program) {};
                    """)
                .build());
    test(
        customExterns,
        srcs(
            """
            class Object {}
            class Foo extends Object {
              /** @param {string} msg */
              constructor(msg) {
                super();
                this.msg = msg;
              }
            }
            """),
        expected(
            """
            /**
             * @constructor
             */
            var Object = function() {
            };
            /**
             * @constructor @extends {Object}
             */
            var Foo = function(msg) {
              Object.call(this);
              this.msg = msg;
            };
            $jscomp.inherits(Foo, Object);
            """));
    test(
        customExterns,
        srcs(
            """
            class Object {}
            class Foo extends Object {}
            """), // autogenerated constructor
        expected(
            """
            /**
             * @constructor
             */
            var Object = function() {
            };
            /**
             * @constructor @extends {Object}
             */
            var Foo = function() {
              Object.apply(this, arguments); // all arguments passed on to super()
            };
            $jscomp.inherits(Foo, Object);
            """));
  }

  @Test
  public void testMultiNameClass() {
    test(
        "var F = class G {}",
        """
        /** @const @constructor */
        var CLASS_DECL$0 = function() {
        };
        /** @constructor */
        var F = CLASS_DECL$0;
        """);

    test(
        "F = class G {}",
        """
        /** @const @constructor */
        var CLASS_DECL$0 = function() {
        };
        /** @constructor */
        F = CLASS_DECL$0;
        """);
  }

  @Test
  public void testOutputLevelES3_compilerFeatureSetIsUpdated() {
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    test(
        "class C { f() { class D {} } }",
        """
        /** @constructor */
        var C = function() {};
        C.prototype.f = function() {
          /** @constructor */
          var D = function() {}
        };
        """);
    // The compiler feature set gets updated to ES3.
    assertThat(getLastCompiler().getAllowableFeatures()).isEqualTo(FeatureSet.ES3);
  }

  @Test
  public void testOutputLevelES3_classGettersSettersAreReported() {
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    testError(
        "class C { get x() { return 1; }}",
        ReportUntranspilableFeatures.UNTRANSPILABLE_FEATURE_PRESENT);
    testError(
        "class C { set x(value) {}}", ReportUntranspilableFeatures.UNTRANSPILABLE_FEATURE_PRESENT);
  }

  @Test
  public void testES5FeatureTrailingCommaIsRemovedUnconditionally() {
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    // trailing comma is removed
    test("let obj = {a: 1, b: 2,};", "var obj = {a: 1, b: 2};");
    // also removed from the featureset
    assertThat(getLastCompiler().getAllowableFeatures()).isEqualTo(FeatureSet.ES3);

    // also removed for ES5 output
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    // trailing comma is removed unconditionally regardless of output level
    test("let obj = {a: 1, b: 2,};", "var obj = {a: 1, b: 2};");
    // also removed from the featureset
    assertThat(getLastCompiler().getAllowableFeatures().contains(Feature.TRAILING_COMMA)).isFalse();
  }

  @Test
  public void testES5FeatureMultiLineStringContinuationIsRemovedUnconditionally() {
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    // string continuation is removed
    test("let obj = 'a\\\nb';", "var obj = 'ab';");
    // also removed from the featureset
    assertThat(getLastCompiler().getAllowableFeatures().contains(Feature.STRING_CONTINUATION))
        .isFalse();

    // also removed for ES5 output
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    // string continuation is removed
    test("let obj = 'a\\\nb';", "var obj = 'ab';");
    // also removed from the featureset
    assertThat(getLastCompiler().getAllowableFeatures().contains(Feature.STRING_CONTINUATION))
        .isFalse();
  }

  @Test
  public void testClassNested() {
    test(
        "class C { f() { class D {} } }",
        """
        /** @constructor */
        var C = function() {};
        C.prototype.f = function() {
          /** @constructor */
          var D = function() {}
        };
        """);

    test(
        "class C { f() { class D extends C {} } }",
        """
        /** @constructor */
        var C = function() {};
        C.prototype.f = function() {
          /**
         * @constructor
         * @extends{C} */
          var D = function() {
            C.apply(this, arguments);
          };
          $jscomp.inherits(D, C);
        };
        """);
  }

  @Test
  public void testSuperGet() {
    test(
        "class D { d() {} } class C extends D { f() {var i = super.d;} }",
        """
        /** @constructor */
        var D = function() {};
        D.prototype.d = function() {};
        /**
         * @constructor
         * @extends{D} */
        var C = function() {
          D.apply(this, arguments);
        };
        $jscomp.inherits(C, D);
        C.prototype.f = function() {
          var i = D.prototype.d;
        };
        """);

    test(
        "class D { ['d']() {} } class C extends D { f() {var i = super['d'];} }",
        """
        /** @constructor */
        var D = function() {};
        D.prototype['d'] = function() {};
        /**
         * @constructor
         * @extends{D} */
        var C = function() {
          D.apply(this, arguments);
        };
        $jscomp.inherits(C, D);
        C.prototype.f = function() {
          var i = D.prototype['d'];
        };
        """);

    test(
        "class D { d() {}} class C extends D { static f() {var i = super.d;} }",
        """
        /** @constructor */
        var D = function() {};
        D.prototype.d = function() {};
        /**
         * @constructor
         * @extends{D} */
        var C = function() {
          D.apply(this, arguments);
        };
        $jscomp.inherits(C, D);
        C.f = function() {
          var i = D.d;
        };
        """);

    test(
        "class D { ['d']() {}} class C extends D { static f() {var i = super['d'];} }",
        """
        /** @constructor */
        var D = function() {};
        D.prototype['d'] = function() {};
        /**
         * @constructor
         * @extends{D} */
        var C = function() {
          D.apply(this, arguments);
        };
        $jscomp.inherits(C, D);
        C.f = function() {
          var i = D['d'];
        };
        """);

    test(
        "class D {} class C extends D { f() {return super.s;} }",
        """
        /** @constructor */
        var D = function() {};
        /**
         * @constructor
         * @extends{D} */
        var C = function() {
          D.apply(this, arguments);
        };
        $jscomp.inherits(C, D);
        C.prototype.f = function() {
          return D.prototype.s;
        };
        """);

    test(
        "class D {} class C extends D { f() { m(super.s);} }",
        """
        /** @constructor */
        var D = function() {};
        /**
         * @constructor
         * @extends{D} */
        var C = function() {
          D.apply(this, arguments);
        };
        $jscomp.inherits(C, D);
        C.prototype.f = function() {
          m(D.prototype.s);
        };
        """);

    test(
        "class D {} class C extends D { foo() { return super.m.foo();} }",
        """
        /** @constructor */
        var D = function() {};
        /**
         * @constructor
         * @extends{D} */
        var C = function() {
          D.apply(this, arguments);
        };
        $jscomp.inherits(C, D);
        C.prototype.foo = function() {
          return D.prototype.m.foo();
        };
        """);

    test(
        "class D {} class C extends D { static foo() { return super.m.foo();} }",
        """
        /** @constructor */
        var D = function() {};
        /**
         * @constructor
         * @extends{D} */
        var C = function() {
          D.apply(this, arguments);
        };
        $jscomp.inherits(C, D);
        C.foo = function() {
          return D.m.foo();
        };
        """);
  }

  @Test
  public void testSuperAccessToGettersAndSetters() {
    // Getters cannot be transpiled to ES3
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        """
        class Base {
          get g() { return 'base'; }
          set g(v) { alert('base.prototype.g = ' + v); }
        }
        class Sub extends Base {
          get g() { return super.g + '-sub'; }
        }
        """,
        """
        /** @constructor */
        var Base = function() {};
        $jscomp.global.Object.defineProperties(
            Base.prototype,
            {
                g:{
                    configurable:true,
                    enumerable:true,
                    get:function(){return"base"},
                    set:function(v){alert("base.prototype.g = " + v);}
                }
            });
        /**
         * @constructor
         * @extends {Base}
         */
        var Sub = function() {
          Base.apply(this, arguments);
        };
        $jscomp.inherits(Sub, Base);
        $jscomp.global.Object.defineProperties(
            Sub.prototype,
            {
                g:{
                    configurable:true,
                    enumerable:true,
                    get:function(){return Base.prototype.g + "-sub";},
                }
            });
        """);

    testError(
        """
        class Base {
          get g() { return 'base'; }
          set g(v) { alert('base.prototype.g = ' + v); }
        }
        class Sub extends Base {
          get g() { return super.g + '-sub'; }
          set g(v) { super.g = v + '-sub'; }
        }
        """,
        CANNOT_CONVERT_YET);
  }

  @Test
  public void testStaticThis() {
    test(
        "class F { static f() { return this; } }",
        """
        /** @constructor */ var F = function() {}
        /** @this {?} */ F.f = function() { return this; };
        """);
  }

  @Test
  public void testStaticMethods() {
    test(
        "class C { static foo() {} }",
        "/** @constructor */ var C = function() {}; C.foo = function() {};");

    test(
        "class C { static foo() {}; foo() {} }",
        """
        /** @constructor */
        var C = function() {};

        C.foo = function() {};

        C.prototype.foo = function() {};
        """);

    test(
        "class C { static foo() {}; bar() { C.foo(); } }",
        """
        /** @constructor */
        var C = function() {};

        C.foo = function() {};

        C.prototype.bar = function() { C.foo(); };
        """);
  }

  @Test
  public void testStaticInheritance() {

    test(
        """
        class D {
          static f() {}
        }
        class C extends D { constructor() {} }
        C.f();
        """,
        """
        /** @constructor */
        var D = function() {};
        D.f = function () {};
        /** @constructor @extends{D} */
        var C = function() {};
        $jscomp.inherits(C, D);
        C.f();
        """);

    test(
        """
        class D {
          static f() {}
        }
        class C extends D {
          constructor() {}
          f() {}
        }
        C.f();
        """,
        """
        /** @constructor */
        var D = function() {};
        D.f = function() {};
        /** @constructor @extends{D} */
        var C = function() { };
        $jscomp.inherits(C, D);
        C.prototype.f = function() {};
        C.f();
        """);

    test(
        """
        class D {
          static f() {}
        }
        class C extends D {
          constructor() {}
          static f() {}
          g() {}
        }
        """,
        """
        /** @constructor */
        var D = function() {};
        D.f = function() {};
        /** @constructor @extends{D} */
        var C = function() { };
        $jscomp.inherits(C, D);
        C.f = function() {};
        C.prototype.g = function() {};
        """);
  }

  @Test
  public void testInheritFromExterns() {
    test(
        externs(
            getDefaultExternsBuilder()
                .addExtra(
                    """
                    /** @constructor */ function ExternsClass() {}
                    ExternsClass.m = function() {};
                    """)
                .build()),
        srcs("class CodeClass extends ExternsClass {}"),
        expected(
            """
            /** @constructor
             * @extends {ExternsClass}
             */
            var CodeClass = function() {
              return ExternsClass.apply(this,arguments) || this;
            };
            $jscomp.inherits(CodeClass,ExternsClass)
            """));
  }

  // Make sure we don't crash on this code.
  // https://github.com/google/closure-compiler/issues/752
  @Test
  public void testGithub752() {
    test(
        "function f() { var a = b = class {};}",
        """
        function f() {
          /** @const @constructor */
          var CLASS_DECL$0 = function() {
          };
          var a = b = CLASS_DECL$0;
        }
        """);

    test(
        "var ns = {}; function f() { var self = ns.Child = class {};}",
        """
        var ns = {};
        function f() {
          /** @const @constructor */
          var CLASS_DECL$0 = function() {
          };
          var self = ns.Child = CLASS_DECL$0;
        }
        """);
  }

  @Test
  public void testInvalidClassUse() {
    SourceFile externsFile =
        new TestExternsBuilder()
            .addArguments()
            .addFunction()
            .addJSCompLibraries()
            .buildExternsFile("externs.js");
    // Normalization changes some externs declarations,
    allowExternsChanges();
    enableTypeCheck();

    test(
        externs(externsFile),
        srcs(
            """
            /** @constructor */
            function Foo() {}
            Foo.prototype.f = function() {};
            class Sub extends Foo {}
            (new Sub).f();
            """),
        expected(
            """
            /** @constructor */
            function Foo() {}
            Foo.prototype.f = function() {};
            /**
             * @constructor
             * @extends {Foo}
             */
            var Sub=function() { Foo.apply(this, arguments); }
            $jscomp.inherits(Sub, Foo);
            (new Sub).f();
            """));

    test(
        externs(externsFile),
        srcs(
            """
            /** @constructor @struct */
            function Foo() {}
            Foo.f = function() {};
            class Sub extends Foo {}
            Sub.f();
            """),
        expected(
            """
            /** @constructor @struct */
            function Foo() {}
            Foo.f = function() {};
            /** @constructor
             * @extends {Foo}
             */
            var Sub = function() { Foo.apply(this, arguments); };
            $jscomp.inherits(Sub, Foo);
            Sub.f();
            """));

    test(
        externs(externsFile),
        srcs(
            """
            /** @constructor */
            function Foo() {}
            Foo.f = function() {};
            class Sub extends Foo {}
            """),
        expected(
            """
            /** @constructor */
            function Foo() {}
            Foo.f = function() {};
            /** @constructor
             * @extends {Foo}
             */
            var Sub = function() { Foo.apply(this, arguments); };
            $jscomp.inherits(Sub, Foo);
            """));
  }

  /**
   * Getters and setters are supported, both in object literals and in classes, but only if the
   * output language is ES5.
   */
  @Test
  public void testEs5GettersAndSettersClasses() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        "class C { get value() { return 0; } }",
        """
        /** @constructor */
        var C = function() {};
        $jscomp.global.Object.defineProperties(C.prototype, {
          value: {
            configurable: true,
            enumerable: true,
            get: function() {
              return 0;
            }
          }
        });
        """);

    test(
        "class C { set value(val) { this.internalVal = val; } }",
        """
        /** @constructor */
        var C = function() {};
        $jscomp.global.Object.defineProperties(C.prototype, {
          value: {
            configurable: true,
            enumerable: true,
            set: function(val) {
              this.internalVal = val;
            }
          }
        });
        """);

    test(
        """
        class C {
          set value(val) {
            this.internalVal = val;
          }
          get value() {
            return this.internalVal;
          }
        }
        """,
        """
        /** @constructor */
        var C = function() {};
        $jscomp.global.Object.defineProperties(C.prototype, {
          value: {
            configurable: true,
            enumerable: true,
            set: function(val) {
              this.internalVal = val;
            },
            get: function() {
              return this.internalVal;
            }
          }
        });
        """);

    test(
        """
        class C {
          get alwaysTwo() {
            return 2;
          }

          get alwaysThree() {
            return 3;
          }
        }
        """,
        """
        /** @constructor */
        var C = function() {};
        $jscomp.global.Object.defineProperties(C.prototype, {
          alwaysTwo: {
            configurable: true,
            enumerable: true,
            get: function() {
              return 2;
            }
          },
          alwaysThree: {
            configurable: true,
            enumerable: true,
            get: function() {
              return 3;
            }
          },
        });
        """);
  }

  @Test
  public void testEs5GettersAndSettersOnClassesWithClassSideInheritance() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { static get value() {} }  class D extends C { static get value() {} }",
        """
        /** @constructor */
        var C = function() {};
        /** @nocollapse */
        C.value;
        $jscomp.global.Object.defineProperties(C, {
          value: {
            configurable: true,
            enumerable: true,
            get: function() {}
          }
        });
        /** @constructor
         * @extends {C}
         */
        var D = function() {
          C.apply(this,arguments);
        };
        /** @nocollapse */
        D.value;
        $jscomp.inherits(D, C);
        $jscomp.global.Object.defineProperties(D, {
          value: {
            configurable: true,
            enumerable: true,
            get: function() {}
          }
        });
        """);
  }

  /** Check that the types from the getter/setter are copied to the declaration on the prototype. */
  @Test
  public void testEs5GettersAndSettersClassesWithTypes() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        "class C { /** @return {number} */ get value() { return 0; } }",
        """
        /** @constructor */
        var C = function() {};
        $jscomp.global.Object.defineProperties(C.prototype, {
          value: {
            configurable: true,
            enumerable: true,
            /**
             * @return {number}
             */
            get: function() {
              return 0;
            }
          }
        });
        """);

    test(
        "class C { /** @param {string} v */ set value(v) { } }",
        """
        /** @constructor */
        var C = function() {};
        $jscomp.global.Object.defineProperties(C.prototype, {
          value: {
            configurable: true,
            enumerable: true,
            /**
             * @param {string} v
             */
            set: function(v) {}
          }
        });
        """);
  }

  @Test
  public void testClassEs5GetterSetterIncorrectTypes() {
    // TODO(b/144721663): these should cause some sort of type warning
    enableTypeCheck();
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    // Using @type instead of @return on a getter.
    SourceFile externsFile =
        new TestExternsBuilder()
            .addArguments()
            .addFunction()
            .addJSCompLibraries()
            .addObject()
            .buildExternsFile("externs.js");
    // Normalization changes some externs declarations,
    allowExternsChanges();

    test(
        externs(externsFile),
        srcs("class C { /** @type {string} */ get value() { } }"),
        expected(
            """
            /** @constructor */
            var C = function() {};
            $jscomp.global.Object.defineProperties(C.prototype, {
              value: {
                configurable: true,
                enumerable: true,
                /** @type {string} */
                get: function() {}
              }
            });
            """));

    // Using @type instead of @param on a setter.
    test(
        externs(externsFile),
        srcs("class C { /** @type {string} */ set value(v) { } }"),
        expected(
            """
            /** @constructor */
            var C = function() {};
            $jscomp.global.Object.defineProperties(C.prototype, {
              value: {
                configurable: true,
                enumerable: true,
                /** @type {string} */
                set: function(v) {}
              }
            });
            """));
  }

  /**
   * @bug 20536614
   */
  @Test
  public void testStaticGetterSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    test(
        "class C { static get foo() {} }",
        """
        /** @constructor */
        var C = function() {};
        /** @nocollapse */
        C.foo;
        $jscomp.global.Object.defineProperties(C, {
          foo: {
            configurable: true,
            enumerable: true,
            get: function() {}
          }
        })
        """);

    test(
        """
        class C { static get foo() {} }
        class Sub extends C {}
        """,
        """
        /** @constructor */
        var C = function() {};
        /** @nocollapse */
        C.foo;
        $jscomp.global.Object.defineProperties(C, {
          foo: {
            configurable: true,
            enumerable: true,
            get: function() {}
          }
        })

        /** @constructor
         * @extends {C}
         */
        var Sub = function() {
          C.apply(this, arguments);
        };
        $jscomp.inherits(Sub, C)
        """);
  }

  @Test
  public void testStaticSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        "class C { static set foo(x) {} }",
        """
        /** @constructor */
        var C = function() {};
        /** @nocollapse */
        C.foo;
        $jscomp.global.Object.defineProperties(C, {
          foo: {
            configurable: true,
            enumerable: true,
            set: function(x) {}
          }
        });
        """);
  }

  @Test
  public void testInitSymbol() {
    // Include the extern for Symbol, because we're testing renaming of local variables with the
    // same name.
    SourceFile externsFileWithSymbol =
        new TestExternsBuilder()
            .addIterable() // there's no method for just Symbol, but this will get it
            .buildExternsFile("externs.js");

    test(
        externs(externsFileWithSymbol), //
        srcs("let a = alert(Symbol.thimble);"),
        expected("var a = alert(Symbol.thimble)"));
    assertThat(getLastCompiler().getInjectedLibraries()).containsExactly("es6/symbol");

    test(
        externs(externsFileWithSymbol), //
        srcs("let a = alert(Symbol.iterator);"),
        expected("var a = alert(Symbol.iterator)"));
    assertThat(getLastCompiler().getInjectedLibraries()).containsExactly("es6/symbol");

    test(
        externs(externsFileWithSymbol),
        srcs(
            """
            function f() {
              let x = 1;
              let y = Symbol('nimble');
            }
            """),
        expected(
            """
            function f() {
              var x = 1;
              var y = Symbol('nimble');
            }
            """));
    Externs externs = externs(externsFileWithSymbol);
    Sources srcs =
        srcs(
            """
            function f() {
              if (true) {
                 let Symbol = function() {};
              }
            // This Symbol is the global one
              alert(Symbol.ism)
            }
            """);
    Expected expected =
        expected(
            """
            function f() {
              if (true) {
            // normalization renames the local Symbol to be different from the global Symbol
                 var Symbol$jscomp$0 = function() {};
              }
              alert(Symbol.ism)
            }
            """);
    test(externs, srcs, expected);

    externs = externs(externsFileWithSymbol);
    srcs =
        srcs(
            """
            function f() {
              if (true) {
                let Symbol = function() {};
            // This Symbol is the local definition. There's no use of the global Symbol in
            // this function.
                alert(Symbol.ism)
              }
            }
            """);
    expected =
        expected(
            """
            function f() {
              if (true) {
            // The local definition of Symbol doesn't have to be renamed, because there's
            // no usage of the global Symbol to conflict with it.
                var Symbol = function() {};
                alert(Symbol.ism)
              }
            }
            """);
    test(externs, srcs, expected);
    // No $jscomp.initSymbol in externs
    testSame(externs("alert(Symbol.thimble);"), srcs(""));
  }

  @Test
  public void testInitSymbolIterator() {
    test(
        "var x = {[Symbol.iterator]: function() { return this; }};",
        """
        var $jscomp$compprop0 = {};
        var x = ($jscomp$compprop0[Symbol.iterator] = function() {return this;},
                 $jscomp$compprop0)
        """);
  }

  /** ES5 getters and setters should report an error if the languageOut is ES3. */
  @Test
  public void testEs5GettersAndSetters_es3() {
    testError(
        "let x = { get y() {} };", ReportUntranspilableFeatures.UNTRANSPILABLE_FEATURE_PRESENT);
    testError(
        "let x = { set y(value) {} };",
        ReportUntranspilableFeatures.UNTRANSPILABLE_FEATURE_PRESENT);
  }

  /** ES5 getters and setters on object literals should be left alone if the languageOut is ES5. */
  @Test
  public void testEs5GettersAndSettersObjLit_es5() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);
    testSame("var x = { get y() {} };");
    testSame("var x = { set y(value) {} };");
  }

  @Test
  public void testForOfLoop() {
    // Iteration var shadows an outer var ()
    test(
        "var i = 'outer'; for (let i of [1, 2, 3]) { alert(i); } alert(i);",
        """
        var i = 'outer';
        var $jscomp$iter$0 = (0, $jscomp.makeIterator)([1, 2, 3]);
        // Normalize runs before for-of rewriting. Therefore, first Normalize renames the
        // `let i` to `let i$jscomp$1` to avoid conficting it with outer `i`. Then, the
        // for-of rewriting prepends the unique ID `$jscomp$key$m123..456$0` to its declared
        // name as it does to all for-of loop keys.
        var KEY$0$i$jscomp$1 = $jscomp$iter$0.next();
        for (; !KEY$0$i$jscomp$1.done; KEY$0$i$jscomp$1 = $jscomp$iter$0.next()) {
          var i$jscomp$1 = KEY$0$i$jscomp$1.value;
          {
            alert(i$jscomp$1);
          }
        }
        alert(i);
        """);
  }

  @Test
  public void testForOfWithConstInitiliazer() {
    enableNormalize();

    test(
        "for(const i of [1,2]) {i;}",
        """
        var $jscomp$iter$0 = (0, $jscomp.makeIterator)([1, 2]);
        // Normalize runs before for-of rewriting. Normalize does not rename the `const i`
        // if there is no other conflicting `i` declaration. Then, the  for-of rewriting
        // prepends `$jscomp$key$m123..456$0` to its declared name as it does to all for-of
        // loop keys.
        var KEY$0$i = $jscomp$iter$0.next();
        for (; !KEY$0$i.done; KEY$0$i = $jscomp$iter$0.next()) {
          /** @const */
          var i = KEY$0$i.value; // marked as const name
          {
            i; // marked as const name
          }
        }
        """);
  }

  @Test
  public void testMultipleForOfWithSameInitializerName() {
    enableNormalize();
    test(
        """
        function* inorder1(t) {
            for (var x of []) {
              yield x;
            }
            for (var x of []) {
              yield x;
            }
        }
        """,
"""
function inorder1(t) {
  var x;
  var $jscomp$iter$0;
  var KEY$0$x; // key for first for-of loop
  var $jscomp$iter$1;
  var KEY$1$x;
  return $jscomp.generator.createGenerator(inorder1, function($jscomp$generator$context$m1146332801$2) {
    switch($jscomp$generator$context$m1146332801$2.nextAddress) {
      case 1:
        $jscomp$iter$0 = (0, $jscomp.makeIterator)([]);
        KEY$0$x = $jscomp$iter$0.next();
      case 2:
        if (!!KEY$0$x.done) {
          $jscomp$generator$context$m1146332801$2.jumpTo(4);
          break;
        }
        x = KEY$0$x.value;
        return $jscomp$generator$context$m1146332801$2.yield(x, 3);
      case 3:
        KEY$0$x = $jscomp$iter$0.next();
        $jscomp$generator$context$m1146332801$2.jumpTo(2);
        break;
      case 4:
        $jscomp$iter$1 = (0, $jscomp.makeIterator)([]);
        KEY$1$x = $jscomp$iter$1.next();
      case 6:
        if (!!KEY$1$x.done) {
          $jscomp$generator$context$m1146332801$2.jumpTo(0);
          break;
        }
        x = KEY$1$x.value;
        return $jscomp$generator$context$m1146332801$2.yield(x, 7);
      case 7:
        KEY$1$x = $jscomp$iter$1.next();
        $jscomp$generator$context$m1146332801$2.jumpTo(6);
        break;
    }
  });}
""");
  }

  @Test
  public void testForOfRedeclaredVar() {
    test(
        """
        for (let x of []) {
          let x = 0;
        }
        """,
        """
        var $jscomp$iter$0 = (0, $jscomp.makeIterator)([]);
        var KEY$0$x = $jscomp$iter$0.next();
        for (; !KEY$0$x.done; KEY$0$x = $jscomp$iter$0.next()) {
          var x = KEY$0$x.value;
          {
            var x$jscomp$1 = 0;
          }
        }
        """);
  }

  @Test
  public void testArgumentsEscaped() {
    testSame(
        """
        function f() {
          return g(arguments);
        }
        """);
  }

  @Test
  public void testMethodInObject() {
    test(
        "var obj = {           f() {alert(1); } };", //
        "var obj = { f: function() {alert(1); } };");

    test(
        "var obj = {           f() { alert(1); },    x };", //
        "var obj = { f: function() { alert(1); }, x: x };");
  }

  @Test
  public void testComputedPropertiesWithMethod() {
    test(
        "var obj = { ['f' + 1]: 1, m() {}, ['g' + 1]: 1, };",
        """
        var $jscomp$compprop0 = {};
        var obj = ($jscomp$compprop0['f' + 1] = 1,
          ($jscomp$compprop0.m = function() {},
             ($jscomp$compprop0['g' + 1] = 1, $jscomp$compprop0)));
        """);
  }

  @Test
  public void testComputedProperties() {
    test(
        "var obj = { ['f' + 1] : 1, ['g' + 1] : 1 };",
        """
        var $jscomp$compprop0 = {};
        var obj = ($jscomp$compprop0['f' + 1] = 1,
          ($jscomp$compprop0['g' + 1] = 1, $jscomp$compprop0));
        """);

    test(
        "var obj = { ['f'] : 1};",
        """
        var $jscomp$compprop0 = {};
        var obj = ($jscomp$compprop0['f'] = 1,
          $jscomp$compprop0);
        """);

    test(
        "var o = { ['f'] : 1}; var p = { ['g'] : 1};",
        """
        var $jscomp$compprop0 = {};
        var o = ($jscomp$compprop0['f'] = 1,
          $jscomp$compprop0);
        var $jscomp$compprop1 = {};
        var p = ($jscomp$compprop1['g'] = 1,
          $jscomp$compprop1);
        """);

    test(
        "({['f' + 1] : 1})",
        """
        var $jscomp$compprop0 = {};
        ($jscomp$compprop0['f' + 1] = 1,
          $jscomp$compprop0)
        """);

    test(
        "({'a' : 2, ['f' + 1] : 1})",
        """
        var $jscomp$compprop0 = {};
        ($jscomp$compprop0['a'] = 2,
          ($jscomp$compprop0['f' + 1] = 1, $jscomp$compprop0));
        """);

    test(
        "({['f' + 1] : 1, 'a' : 2})",
        """
        var $jscomp$compprop0 = {};
        ($jscomp$compprop0['f' + 1] = 1,
          ($jscomp$compprop0['a'] = 2, $jscomp$compprop0));
        """);

    test(
        "({'a' : 1, ['f' + 1] : 1, 'b' : 1})", //
        """
        var $jscomp$compprop0 = {};
        ($jscomp$compprop0['a'] = 1,
          ($jscomp$compprop0['f' + 1] = 1,
            ($jscomp$compprop0['b'] = 1,
              $jscomp$compprop0)));
        """);

    test(
        "({'a' : x++, ['f' + x++] : 1, 'b' : x++})",
        """
        var $jscomp$compprop0 = {};
        ($jscomp$compprop0['a'] = x++, ($jscomp$compprop0['f' + x++] = 1,
          ($jscomp$compprop0['b'] = x++, $jscomp$compprop0)))
        """);

    test(
        "({a : x++, ['f' + x++] : 1, b : x++})",
        """
        var $jscomp$compprop0 = {};
        ($jscomp$compprop0.a = x++, ($jscomp$compprop0['f' + x++] = 1,
          ($jscomp$compprop0.b = x++, $jscomp$compprop0)))
        """);

    test(
        "({a, ['f' + 1] : 1})",
        """
        var $jscomp$compprop0 = {};
          ($jscomp$compprop0.a = a, ($jscomp$compprop0['f' + 1] = 1, $jscomp$compprop0))
        """);

    test(
        "({['f' + 1] : 1, a})",
        """
        var $jscomp$compprop0 = {};
          ($jscomp$compprop0['f' + 1] = 1, ($jscomp$compprop0.a = a, $jscomp$compprop0))
        """);

    test(
        "var obj = { [foo]() {}}",
        """
        var $jscomp$compprop0 = {};
        var obj = ($jscomp$compprop0[foo] = function(){}, $jscomp$compprop0)
        """);
  }

  @Test
  public void testComputedPropGetterSetter() {
    setLanguageOut(LanguageMode.ECMASCRIPT5);

    testSame("var obj = {get latest () {return undefined;}}");
    testSame("var obj = {set latest (str) {}}");
    test(
        "var obj = {'a' : 2, get l () {return null;}, ['f' + 1] : 1}",
        """
        var $jscomp$compprop0 = {get l () {return null;}};
        var obj = ($jscomp$compprop0['a'] = 2,
          ($jscomp$compprop0['f' + 1] = 1, $jscomp$compprop0));
        """);
    test(
        "var obj = {['a' + 'b'] : 2, set l (str) {}}",
        """
        var $jscomp$compprop0 = {set l (str) {}};
        var obj = ($jscomp$compprop0['a' + 'b'] = 2, $jscomp$compprop0);
        """);
  }

  @Test
  public void testComputedPropClass() {
    test(
        "class C { [foo]() { alert(1); } }",
        """
        var COMP_FIELD$0 = foo;
        /** @constructor */
        var C = function() {
        };
        C.prototype[COMP_FIELD$0] = function() {
          alert(1);
        };
        """);

    test(
        "class C { static [foo]() { alert(2); } }",
        """
        var COMP_FIELD$0 = foo;
        /** @constructor */
        var C = function() {
        };
        C[COMP_FIELD$0] = function() {
          alert(2);
        };
        """);
  }

  @Test
  public void testComputedPropCannotConvert() {
    testError("var o = { get [foo]() {}}", CANNOT_CONVERT_YET);
    testError("var o = { set [foo](val) {}}", CANNOT_CONVERT_YET);
  }

  @Test
  public void testNoComputedProperties() {
    testSame("({'a' : 1})");
    testSame("({'a' : 1, f : 1, b : 1})");
  }

  @Test
  public void testUntaggedTemplateLiteral() {
    test("``", "''");
    test("`\"`", "'\\\"'");
    test("`'`", "\"'\"");
    test("`\\``", "'`'");
    test("`\\\"`", "'\\\"'");
    test("`\\\\\"`", "'\\\\\\\"'");
    test("`\"\\\\`", "'\"\\\\'");
    test("`$$`", "'$$'");
    test("`$$$`", "'$$$'");
    test("`\\$$$`", "'$$$'");
    test("`hello`", "'hello'");
    test("`hello\nworld`", "'hello\\nworld'");
    test("`hello\rworld`", "'hello\\nworld'");
    test("`hello\r\nworld`", "'hello\\nworld'");
    test("`hello\n\nworld`", "'hello\\n\\nworld'");
    test("`hello\\r\\nworld`", "'hello\\r\\nworld'");
    test("`${world}`", "'' + world");
    test("`hello ${world}`", "'hello ' + world");
    test("`${hello} world`", "hello + ' world'");
    test("`${hello}${world}`", "'' + hello + world");
    test("`${a} b ${c} d ${e}`", "a + ' b ' + c + ' d ' + e");
    test("`hello ${a + b}`", "'hello ' + (a + b)");
    test("`hello ${a, b, c}`", "'hello ' + (a, b, c)");
    test("`hello ${a ? b : c}${a * b}`", "'hello ' + (a ? b : c) + (a * b)");
  }

  @Test
  public void testUnicodeEscapes() {
    test(
        "var \\u{73} = \'\\u{2603}\'", // ☃
        "var       s = \'\u2603\'");
    test(
        "var \\u{63} = \'\\u{1f42a}\'", // 🐪
        "var       c = \'\uD83D\uDC2A\'");
    test(
        "var str = `begin\\u{2026}end`", //
        "var str = 'begin\\u2026end'");
  }

  @Test
  public void testObjectLiteralShorthand() {
    rewriteUniqueIdAndTest(
        srcs(
            """
            function f() {
              var x = 1;
              if (a) {
                let x = 2;
                return {x};
              }
              return x;
            }
            """),
        expected(
            """
            function f() {
              var x = 1;
              if (a) {
                var x$jscomp$0 = 2;
                return {x: x$jscomp$0};
              }
              return x;
            }
            """));

    rewriteUniqueIdAndTest(
        srcs(
            """
            function f(a) {
              var {x} = a;
              if (a) {
                let x = 2;
                return x;
              }
              return x;
            }
            """),
        expected(
            """
            function f(a) {
              var x;
              var $jscomp$destructuring$var0 = a;
              x = $jscomp$destructuring$var0.x;
              if (a) {
                var x$jscomp$0 = 2;
                return x$jscomp$0;
              }
              return x;
            }
            """));

    // Note: if the inner `let` declaration is defined as a destructuring assignment
    // then the test would fail because Es6RewriteBlockScopeDeclaration does not even
    // look at destructuring declarations, expecting them to already have been
    // rewritten, and this test does not include that pass.
  }
}
