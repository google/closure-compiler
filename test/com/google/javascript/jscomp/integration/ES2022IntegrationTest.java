/*
 * Copyright 2020 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.integration;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.DevMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.DiagnosticGroup;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.GoogleCodingConvention;
import com.google.javascript.jscomp.PropertyRenamingPolicy;
import com.google.javascript.jscomp.VariableRenamingPolicy;
import com.google.javascript.jscomp.WarningLevel;
import com.google.javascript.jscomp.js.RuntimeJsLibManager.RuntimeLibraryMode;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests related to ES2022 features like class fields */
@RunWith(JUnit4.class)
public final class ES2022IntegrationTest extends IntegrationTestCase {

  /** Creates a CompilerOptions object with google coding conventions. */
  CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguage(LanguageMode.UNSUPPORTED);
    options.setDevMode(DevMode.EVERY_PASS);
    options.setCodingConvention(new GoogleCodingConvention());
    return options;
  }

  private CompilerOptions checksOnlyCompilerOptions() {
    CompilerOptions options = createCompilerOptions();
    options.setChecksOnly(true);
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);

    return options;
  }

  private CompilerOptions fullyOptimizedCompilerOptions() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setTypeBasedOptimizationOptions(options);
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);

    return options;
  }

  @Test
  public void publicClassFields_supportedInChecksOnlyMode() {
    CompilerOptions options = checksOnlyCompilerOptions();
    externs = ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs"));

    testNoWarnings(
        options,
        """
        class MyClass {
          /** @type {number} */
          x = 2;
          y;
        }
        console.log(new MyClass().x);
        """);
  }

  @Test
  public void publicClassFields_supportedInChecksOnlyMode2() {
    CompilerOptions options = checksOnlyCompilerOptions();
    externs = ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs"));

    testNoWarnings(
        options,
        """
        class MyClass {
          x = '';
          y;
        }
        console.log(new MyClass().x);
        """);
  }

  @Test
  public void publicClassFields_supportedInChecksOnlyMode3() {
    CompilerOptions options = checksOnlyCompilerOptions();

    test(
        options,
        new String[] {
          """
          class MyClass {
            /** @type {string} */
            x = 2;
          }
          """
        },
        /* compiled= */ null,
        new DiagnosticGroup[] {DiagnosticGroups.CHECK_TYPES});
  }

  @Test
  public void computedPublicClassFields_supportedInChecksOnlyMode() {
    CompilerOptions options = checksOnlyCompilerOptions();
    externs = ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs"));

    testNoWarnings(
        options,
        """
        /** @dict */
        class MyClass {
          [3 + 4] = 5;
          [6];
          'x' = 2;
        }
        console.log(new MyClass()[6]);
        """);
  }

  @Test
  public void computedPublicClassFields_supportedInChecksOnlyMode2() {
    CompilerOptions options = checksOnlyCompilerOptions();
    externs = ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs"));

    testNoWarnings(
        options,
        """
        /** @dict */
        class MyClass {
          ['x'] = 5;
        }
        console.log(new MyClass()['x']);
        """);
  }

  // deprecated warnings aren't given on computed fields:
  // users should be careful about tags on computed fields
  @Test
  public void computedPublicClassFields_supportedInChecksOnlyMode3() {
    CompilerOptions options = checksOnlyCompilerOptions();

    testNoWarnings(
        options,
        """
        /** @unrestricted */
        class MyClass {
          /** @deprecated */
          ['x'] = 5;
          baz() { return this['x']; }
        }
        """);
  }

  @Test
  public void computedPublicClassFields_supportedInChecksOnlyMode4() {
    CompilerOptions options = checksOnlyCompilerOptions();

    // test will give JSC_ILLEGAL_PROPERTY_ACCESS error because @dict or @restricted is missing
    test(
        options,
        new String[] {
          """
          class MyClass {
            [3 + 4] = 5;
          }
          """,
        },
        /* compiled= */ null,
        new DiagnosticGroup[] {DiagnosticGroups.CHECK_TYPES});
  }

  @Test
  public void publicMixedClassFields_supportedInChecksOnlyMode() {
    CompilerOptions options = checksOnlyCompilerOptions();

    testNoWarnings(
        options,
        """
        /** @unrestricted */
        class MyClass {
          a = 2;
          ['b'] = 'hi';
          'c' = 5;
          2 = 4;
          d;
          ['e'];
        }
        """);
  }

  @Test
  public void publicClassFields_supportedInOptimizationsMode() {
    CompilerOptions options = fullyOptimizedCompilerOptions();
    options.setPrintSourceAfterEachPass(true);

    externs = ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs"));

    test(
        options,
        """
        class C {
          f1 = 1;
        static f2 = 3;
          m1() {return this.f1}
        }
        console.log(new C().f1);
        console.log(C.f2);
        console.log(new C().m1());
        """,
        """
        console.log(1);
        console.log(3);
        console.log(1);
        """);
  }

  @Test
  public void publicClassFields_supportedInOptimizationsMode1() {
    CompilerOptions options = fullyOptimizedCompilerOptions();
    options.setPrintSourceAfterEachPass(true);

    externs = ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs"));

    test(
        options,
        """
        class MyClass {
          /** @type {number} */
          f1 = 2;
          /** @type {string} */
          f2 = 'hi';
          f3 = function() { return this.f1 };
          m1() { return this.f2; }
        }
        console.log(new MyClass().f1);
        console.log(new MyClass().f2);
        console.log(new MyClass().f3());
        console.log(new MyClass().m1());
        """,
        """
        console.log(2);
        console.log('hi');
        console.log(2);
        console.log('hi');
        """);
  }

  @Test
  public void publicClassFields_supportedInOptimizationsMode2() {
    CompilerOptions options = fullyOptimizedCompilerOptions();
    externs = ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs"));

    test(
        options,
        """
        /** @dict */
        class MyClass {
          ['f1'] = 2;
          'f2' = 'hi';
          2 = 4;
          ['m1']() { return this['f1']; }
        }
        console.log(new MyClass()['f1']);
        console.log(new MyClass()[2]);
        console.log(new MyClass()['m1']());
        """,
        """
        class a {
          f1 = 2;
          f2= 'hi';
          2 = 4;
          m1() { return this.f1; }
        }
        console.log((new a).f1);
        console.log((new a)[2]);
        console.log((new a).m1());
        """);
  }

  @Test
  public void publicClassFields_supportedInOptimizationsMode3() {
    CompilerOptions options = fullyOptimizedCompilerOptions();
    externs = ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs"));

    test(
        options,
        """
        /** @unrestricted */
        class MyClass {
          f1 = 1;
          ['a'] = 'hi';
          ['f3'] = function() { return this.f1; };
        }
        console.log(new MyClass().f1);
        console.log(new MyClass()['a']);
        console.log(new MyClass()['f3']());
        """,
        """
        class a {
          b = 1;
          a = 'hi';
          f3 = function() { return this.b; };
        }
        console.log(1);
        console.log(new a().a);
        console.log(new a().f3());
        """);
  }

  @Test
  public void publicClassFields_supportedInOptimizationsMode4() {
    CompilerOptions options = fullyOptimizedCompilerOptions();

    test(
        options,
        """
        /** @unrestricted */
        class MyClass {
          static f1 = alert(2);
          ['f2'] = 'hi';
          'f3' = 5;
          4 = 4;
          f5;
          ['f6'] = alert(1);
        }
        """,
        """
        alert(2);
        """);
  }

  @Test
  public void publicClassFields_newTargetInFieldInitializers() {
    // `new.target` is not supported in field initializers, where it is always `undefined`.
    CompilerOptions options = createCompilerOptions();
    externs = ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs"));
    String src =
        """
        class C {
          inst = new.target;
          static stat = new.target;
        }
        """;

    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        options,
        new String[] {src},
        (String[]) null,
        new DiagnosticGroup[] {DiagnosticGroups.PARSING, DiagnosticGroups.PARSING});

    options.setLanguageOut(LanguageMode.ECMASCRIPT_2022);
    test(
        options,
        new String[] {src},
        (String[]) null,
        new DiagnosticGroup[] {DiagnosticGroups.PARSING, DiagnosticGroups.PARSING});

    // Also when the class is inside a function.
    String srcInFunction =
        """
        function f() {
          class C {
            inst = new.target;
            static stat = new.target;
          }
          return C;
        }
        """;

    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    test(
        options,
        new String[] {srcInFunction},
        (String[]) null,
        new DiagnosticGroup[] {DiagnosticGroups.PARSING, DiagnosticGroups.PARSING});

    options.setLanguageOut(LanguageMode.ECMASCRIPT_2022);
    test(
        options,
        new String[] {srcInFunction},
        (String[]) null,
        new DiagnosticGroup[] {DiagnosticGroups.PARSING, DiagnosticGroups.PARSING});
  }

  @Test
  public void publicClassFields_derivedConstructorReturnsObjectWithoutSuperCall() {
    // The fields of `Sub` are never initialized, since its constructor never calls `super()`.
    // `CheckSuper` reports it, although the constructor returns a value, since the transpiled
    // fields need a `super()` call to be installed after.
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2021);
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_ONLY);
    externs = ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs"));

    String src =
        """
        class Base {}
        class Sub extends Base {
          y = 2;
          constructor() {
            return {tag: 'ret'};
          }
        }
        console.log(new Sub().tag);
        """;
    Compiler compiler = compile(options, src);
    checkUnexpectedErrorsOrWarnings(compiler, 1);
    assertThat(compiler.getErrors().get(0).type().key)
        .isEqualTo("JSC_MISSING_ROOT_LEVEL_CALL_TO_SUPER");
  }

  @Test
  public void computedFieldExecutionOrderAndDeadAssignmentElimination() {
    CompilerOptions options = fullyOptimizedCompilerOptions();

    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addConsole()
                .addExtra("var window;")
                .buildExternsFile("externs"));

    test(
        options,
        """
        window.test = function() {
          var x = 0;
          /** @unrestricted */
          class MyClass {
            static f1 = x;
            static[(x = 1)] = 1;  // (x = 1) executes before assigning 'static f1 = x'
          }
          console.log(MyClass.f1);  // prints 1
        };
        """,
        """
        window.b = function() {
          class a{
            static a;
            static [1];
          }
          a.a = 1;
          a[1] = 1;
          console.log(a.a);  // prints 1
        }
        """);
  }

  @Test
  public void computedMethodExecutionOrderAndDeadAssignmentElimination() {
    CompilerOptions options = fullyOptimizedCompilerOptions();

    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addConsole()
                .addExtra("var window;")
                .buildExternsFile("externs"));

    test(
        options,
        """
        window.test = function() {
          var x = 0;
          /** @unrestricted */
          class MyClass {
            static f1 = x;
            static[(x = 1)]() {};  // (x = 1) executes before assigning 'static f1 = x'
          }
          console.log(MyClass.f1);  // prints 1
        };
        """,
        """
        window.b = function() {
          var a = 0, d = a = 1;
          class c {
            static a;
            static[d]() {}
          }
          c.a = a;
          console.log(c.a)  // prints 1
        }
        """);
  }

  @Test
  public void publicClassFields_derivedConstructorParamDefaultReadsThis() {
    // In a derived class `this` is unbound until super() returns, and parameter defaults run before
    // the body, so `z = this.b` throws a ReferenceError natively instead of reading the field.
    // `CheckSuper` reports it.
    CompilerOptions options = createCompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2021);

    String src =
        """
        class Parent {}
        class Child extends Parent {
          b = 2;
          constructor(z = this.b) {
            super();
            this.z = z;
          }
        }
        """;
    Compiler compiler = compile(options, src);
    checkUnexpectedErrorsOrWarnings(compiler, 1);
    assertThat(compiler.getErrors().get(0).type().key).isEqualTo("JSC_THIS_BEFORE_SUPER");
  }

  @Test
  public void testEs6RewriteClassExtendsExpression() {
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setWarningLevel(DiagnosticGroups.CHECK_VARIABLES, CheckLevel.OFF);
    options.setPropertyRenaming(PropertyRenamingPolicy.OFF);
    options.setGeneratePseudoNames(true);

    String src =
        """
        /** @unrestricted */
        class __PRIVATE_WebChannelConnection extends class __PRIVATE_RestConnection {
          constructor(e) {
            this.databaseInfo = e, this.databaseId = e.databaseId;
          }
        }
        {
          constructor(e) {
            super(e), this.forceLongPolling = e.forceLongPolling,
                      this.autoDetectLongPolling = e.autoDetectLongPolling,
                      this.useFetchStreams = e.useFetchStreams,
                      this.longPollingOptions = e.longPollingOptions;
            console.log('test');
          }
        }
        """;
    String expected =
        """
        const $$jscomp$classExtends$98447280$0$$ = class {};
        class __PRIVATE_WebChannelConnection extends $$jscomp$classExtends$98447280$0$$ {
          constructor() {
            super();
            console.log("test");
          }
        }
        """;

    options.setLanguageIn(LanguageMode.UNSTABLE);
    options.setLanguageOut(LanguageMode.UNSTABLE);
    test(options, src, expected);

    options.setLanguageIn(LanguageMode.UNSTABLE);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2019);
    test(options, src, expected);
  }

  @Test
  public void privateClassFields_supportedInChecksOnlyMode() {
    CompilerOptions options = checksOnlyCompilerOptions();
    externs = ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs"));

    testNoWarnings(
        options,
        """
        class MyClass {
          #x = 2;
          #y;
          getX() {
            return this.#x;
          }
        }
        console.log(new MyClass().getX());
        """);
  }

  @Test
  public void privateClassFields_supportedInOptimizationsMode() {
    CompilerOptions options = createCompilerOptions();
    // Avoid prepending polyfill implementation code (e.g. WeakMap) into the expected test output.
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_ONLY);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);
    // Declare $jscomp in externs to satisfy VarCheck when runtime libraries are not injected.
    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addConsole()
                .addExtra("/** @const */ var $jscomp = {};")
                .buildExternsFile("externs"));

    String src =
        """
        class MyClass {
          #x = 1;
          inc() {
            ++this.#x;
          }
          getX() {
            return this.#x;
          }
        }

        const o1 = new MyClass();
        console.log(o1.getX());
        o1.inc();
        console.log(o1.getX());

        const o2 = new MyClass();
        o1.inc();
        o1.inc();
        console.log(o2.getX());
        """;

    // TODO(b/236744850): When languageOut is ECMASCRIPT_2022, private fields should be emitted
    // natively instead of being transpiled to $jscomp.PrivateMap (or modified to public fields).
    String expected =
        """
        const $jscomp$privateMap$98447280$0 = new $jscomp.PrivateMap();
        class MyClass {
          constructor() {
            var $jscomp$priv$98447280$1 = Object.create(null);
            $jscomp$privateMap$98447280$0.set(this, $jscomp$priv$98447280$1);
            $jscomp$priv$98447280$1.x = 1;
          }
          inc() {
            ++$jscomp$privateMap$98447280$0.get(this).x;
          }
          getX() {
            return $jscomp$privateMap$98447280$0.get(this).x;
          }
        }
        const o1 = new MyClass();
        console.log(o1.getX());
        o1.inc();
        console.log(o1.getX());
        const o2 = new MyClass();
        o1.inc();
        o1.inc();
        console.log(o2.getX());
        """;

    // TODO(b/236744850): Update expected output for ES2022 once private fields are emitted
    // natively.
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2022);
    test(options, src, expected);

    // Transpile ES2022 private class fields to ES2021-compatible JavaScript ($jscomp.PrivateMap).
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2021);
    test(options, src, expected);
  }

  @Test
  public void privateClassFields_nativeWithTranspiledPublicFields_knownBug() {
    // Expected behavior: with public fields lowered and private fields kept native, field
    // initializers still run in declaration order. So `#b` reads `a` after `a = 1` has run, and
    // `new C().getB()` returns 1, as in the input.
    CompilerOptions options = createCompilerOptions();
    options.setTranspilePublicClassFields(true);
    externs = ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs"));

    // TODO(b/236744850): Lowering only the public fields changes the initializer order.
    // Current behavior: `a = 1` becomes `this.a = 1` in the constructor, but `#b = this.a` stays a
    //   field, and fields are initialized before the constructor body runs.
    // Why it's a bug: `#b` reads `this.a` before `this.a = 1` runs, so `new C().getB()` returns
    //   undefined instead of 1.
    // In a full build: only when a tool calls `setTranspilePublicClassFields(true)`. No flag sets
    //   it, and no output language lowers public fields but keeps private ones.
    test(
        options,
        """
        class C {
          a = 1;
          #b = this.a;
          getB() { return this.#b; }
        }
        console.log(new C().getB());
        """,
        """
        class C {
          constructor() {
            this.a = 1;
          }
          #b = this.a;
          getB() { return this.#b; }
        }
        console.log(new C().getB());
        """);
  }

  @Test
  public void privateClassMembers_fullIntegrationTest() {
    CompilerOptions options = createCompilerOptions();
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_ONLY);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);
    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addConsole()
                .addExtra("/** @const */ var $jscomp = {};")
                .buildExternsFile("externs"));

    String src =
        """
        class C {
          x = 1;
          get #foo() {
            return this.x;
          }
          set #foo(x) {
            this.x = x;
          }
          #y = this.x + this.#foo;
          #z;
          #method(p) {
            return this.#foo + this.#y + p;
          }
          callAll() {
            return (
                this.#method(1) +
                ++this.#foo +
                C.#sMethod()
            );
          }
          static brandChecks(x) {
            if (#y in x) return 'C instance';
            if (#s2 in x) return 'C';
            return false;
          }
          canRewrite() {
            try {
              this.#foo = 1;
              return true;
            } catch (e) {
              return false;
            }
          }
          static s1 = 1;
          static #s2 = this.s1;
          static #sMethod() {
            return this.s1 + this.#s2;
          }
        }
        """;

    String expected =
        """
        const $jscomp$privateMap$98447280$0 = new $jscomp.PrivateMap();
        const $jscomp$priv$proto$98447280$2 = Object.create(null, {
          foo: {
            get: function() {
              return this.$self.x;
            },
            set: function(x) {
              this.$self.x = x;
            }
          },
          method: {
            value: function(p) {
              return $jscomp$privateMap$98447280$0.get(this).foo + $jscomp$privateMap$98447280$0.get(this).y + p;
            }
          }
        });
        const $jscomp$staticPrivateMap$98447280$1 = new $jscomp.PrivateMap();
        class C {
          constructor() {
            var $jscomp$priv$98447280$3 = Object.create($jscomp$priv$proto$98447280$2);
            $jscomp$priv$98447280$3.$self = this;
            $jscomp$privateMap$98447280$0.set(this, $jscomp$priv$98447280$3);
            this.x = 1;
            $jscomp$priv$98447280$3.y = this.x + $jscomp$privateMap$98447280$0.get(this).foo;
            $jscomp$priv$98447280$3.z = void 0;
          }
          callAll() {
            return $jscomp$privateMap$98447280$0.get(this).method.call(this, 1)
                + ++$jscomp$privateMap$98447280$0.get(this).foo
                + $jscomp$staticPrivateMap$98447280$1.get(C).sMethod.call(C);
          }
          static brandChecks(x) {
            if ($jscomp$privateMap$98447280$0.has(x)) return 'C instance';
            if ($jscomp$staticPrivateMap$98447280$1.has(x)) return 'C';
            return false;
          }
          canRewrite() {
            try {
              $jscomp$privateMap$98447280$0.get(this).foo = 1;
              return true;
            } catch (e) {
              return false;
            }
          }
          static $jscomp$staticInit$98447280$5() {
            var $jscomp$priv$98447280$4 = Object.create(null);
            $jscomp$staticPrivateMap$98447280$1.set(C, $jscomp$priv$98447280$4);
            $jscomp$priv$98447280$4.sMethod = function() {
              return this.s1 + $jscomp$staticPrivateMap$98447280$1.get(this).s2;
            };
            C.s1 = 1;
            $jscomp$priv$98447280$4.s2 = C.s1;
          }
        }
        C.$jscomp$staticInit$98447280$5();
        """;

    options.setLanguageOut(LanguageMode.ECMASCRIPT_2021);
    test(options, src, expected);
  }

  @Test
  public void privateClassMembers_subclassAndCrossInstanceAccess() {
    CompilerOptions options = createCompilerOptions();
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_ONLY);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);
    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addConsole()
                .addExtra("/** @const */ var $jscomp = {};")
                .buildExternsFile("externs"));

    String src =
        """
        class Base {
          #secret = 42;
          #getSecret() { return this.#secret; }
          compareSecret(other) {
            return this.#getSecret() === other.#getSecret();
          }
        }
        class Derived extends Base {
          #childSecret = 100;
          getChildSecret() {
            return this.#childSecret;
          }
        }
        """;

    String expected =
        """
        const $jscomp$privateMap$98447280$0 = new $jscomp.PrivateMap();
        const $jscomp$priv$proto$98447280$1 = Object.create(null, {
          getSecret: {
            value: function() {
              return $jscomp$privateMap$98447280$0.get(this).secret;
            }
          }
        });
        class Base {
          constructor() {
            var $jscomp$priv$98447280$2 = Object.create($jscomp$priv$proto$98447280$1);
            $jscomp$privateMap$98447280$0.set(this, $jscomp$priv$98447280$2);
            $jscomp$priv$98447280$2.secret = 42;
          }
          compareSecret(other) {
            return $jscomp$privateMap$98447280$0.get(this).getSecret.call(this)
                === $jscomp$privateMap$98447280$0.get(other).getSecret.call(other);
          }
        }
        const $jscomp$privateMap$98447280$3 = new $jscomp.PrivateMap();
        class Derived extends Base {
          constructor() {
            super(...arguments);
            var $jscomp$priv$98447280$4 = Object.create(null);
            $jscomp$privateMap$98447280$3.set(this, $jscomp$priv$98447280$4);
            $jscomp$priv$98447280$4.childSecret = 100;
          }
          getChildSecret() {
            return $jscomp$privateMap$98447280$3.get(this).childSecret;
          }
        }
        """;

    options.setLanguageOut(LanguageMode.ECMASCRIPT_2021);
    test(options, src, expected);
  }

  @Test
  public void privateClassMembers_sideEffectingReceivers() {
    CompilerOptions options = createCompilerOptions();
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_ONLY);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);
    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addConsole()
                .addExtra("/** @const */ var $jscomp = {};")
                .buildExternsFile("externs"));

    String src =
        """
        class Target {
          #data = 0;
          #update(val) {
            this.#data = val;
            return this.#data;
          }
          run(factory) {
            return factory().#update(10);
          }
        }
        """;

    String expected =
        """
        const $jscomp$privateMap$98447280$0 = new $jscomp.PrivateMap();
        const $jscomp$priv$proto$98447280$2 = Object.create(null, {
          update: {
            value: function(val) {
              $jscomp$privateMap$98447280$0.get(this).data = val;
              return $jscomp$privateMap$98447280$0.get(this).data;
            }
          }
        });
        class Target {
          constructor() {
            var $jscomp$priv$98447280$3 = Object.create($jscomp$priv$proto$98447280$2);
            $jscomp$privateMap$98447280$0.set(this, $jscomp$priv$98447280$3);
            $jscomp$priv$98447280$3.data = 0;
          }
          run(factory) {
            var $jscomp$tmp$98447280$1;
            return ($jscomp$tmp$98447280$1 = factory(),
                    $jscomp$privateMap$98447280$0.get($jscomp$tmp$98447280$1).update.call($jscomp$tmp$98447280$1, 10));
          }
        }
        """;

    options.setLanguageOut(LanguageMode.ECMASCRIPT_2021);
    test(options, src, expected);
  }

  @Test
  public void privateClassMembers_accessorCompoundAssignAndIncDec() {
    CompilerOptions options = createCompilerOptions();
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_ONLY);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);
    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addConsole()
                .addExtra("/** @const */ var $jscomp = {};")
                .buildExternsFile("externs"));

    String src =
        """
        class Counter {
          #count = 0;
          get #val() { return this.#count; }
          set #val(v) { this.#count = v; }
          step() {
            this.#val += 5;
            ++this.#val;
            return this.#val++;
          }
        }
        """;

    String expected =
        """
        const $jscomp$privateMap$98447280$0 = new $jscomp.PrivateMap();
        const $jscomp$priv$proto$98447280$1 = Object.create(null, {
          val: {
            get: function() {
              return $jscomp$privateMap$98447280$0.get(this.$self).count;
            },
            set: function(v) {
              $jscomp$privateMap$98447280$0.get(this.$self).count = v;
            }
          }
        });
        class Counter {
          constructor() {
            var $jscomp$priv$98447280$2 = Object.create($jscomp$priv$proto$98447280$1);
            $jscomp$priv$98447280$2.$self = this;
            $jscomp$privateMap$98447280$0.set(this, $jscomp$priv$98447280$2);
            $jscomp$priv$98447280$2.count = 0;
          }
          step() {
            $jscomp$privateMap$98447280$0.get(this).val += 5;
            ++$jscomp$privateMap$98447280$0.get(this).val;
            return $jscomp$privateMap$98447280$0.get(this).val++;
          }
        }
        """;

    options.setLanguageOut(LanguageMode.ECMASCRIPT_2021);
    test(options, src, expected);
  }

  @Test
  public void privateClassMembers_staticMethodAndFieldOrder() {
    CompilerOptions options = createCompilerOptions();
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_ONLY);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);
    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addConsole()
                .addExtra("/** @const */ var $jscomp = {};")
                .buildExternsFile("externs"));

    String src =
        """
        class C {
          static #a = 1;
          static #b() { return this.#a + 1; }
          static #c = this.#b() + 1;
          static getC() { return this.#c; }
        }
        """;

    String expected =
        """
        const $jscomp$staticPrivateMap$98447280$0 = new $jscomp.PrivateMap();
        class C {
          static getC() {
            return $jscomp$staticPrivateMap$98447280$0.get(this).c;
          }
          static $jscomp$staticInit$98447280$2() {
            var $jscomp$priv$98447280$1 = Object.create(null);
            $jscomp$staticPrivateMap$98447280$0.set(C, $jscomp$priv$98447280$1);
            $jscomp$priv$98447280$1.b = function() {
              return $jscomp$staticPrivateMap$98447280$0.get(this).a + 1;
            };
            $jscomp$priv$98447280$1.a = 1;
            $jscomp$priv$98447280$1.c = $jscomp$staticPrivateMap$98447280$0.get(C).b.call(C) + 1;
          }
        }
        C.$jscomp$staticInit$98447280$2();
        """;

    options.setLanguageOut(LanguageMode.ECMASCRIPT_2021);
    test(options, src, expected);
  }

  @Test
  public void privateClassMembers_superReadWithConstReflectExterns_knownBug() {
    // Expected behavior: `super.p` in a private method is lowered to
    // `Reflect.get(Base.prototype, "p", this)`, with the `Reflect` name marked constant. The
    // externs declare `@const Reflect`, and the compiler requires every reference to a constant
    // name to be marked constant.
    // In a full build: only `DevMode.EVERY_PASS` checks this; the output is correct either way.
    //
    // TODO(b/236744850): `super.p` in a private method crashes the compiler with `@const Reflect`.
    // Current behavior: the `Reflect` name in the lowered `Reflect.get(...)` isn't marked constant,
    //   so the validity check after `es6NormalizeClasses` fails.
    // Why it's a bug: the compiler crashes on valid code when its validity checks are on, as in
    //   this test (`DevMode.EVERY_PASS`).
    CompilerOptions options = createCompilerOptions();
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_ONLY);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2021);
    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addReflect()
                .addExtra("/** @const */ var $jscomp = {};")
                .buildExternsFile("externs"));
    String src =
        """
        class Base {}
        class Sub extends Base {
          #m() {
            return super.p;
          }
        }
        """;

    var ex = assertThrows(RuntimeException.class, () -> compile(options, src));
    assertThat(ex).hasMessageThat().contains("Validity check failed for es6NormalizeClasses");
    assertThat(ex)
        .hasCauseThat()
        .hasCauseThat()
        .hasMessageThat()
        .contains("Should be const but not marked as const");
  }

  @Test
  public void privateClassMembers_staticPrivateAccessorWithPropertyRenaming_knownBug() {
    // Expected behavior: property renaming renames the key of a static private accessor and its
    // reads the same way, so `C.read()` still returns "sa". Renaming must not change what a
    // property read returns.
    //
    // TODO(b/236744850): Property renaming breaks reads of a static private accessor.
    // Current behavior: the getter is defined under the quoted key "sa", which property renaming
    //   doesn't rename, but the inlined read of `C.#sa` is renamed.
    // Why it's a bug: `console.log(C.read())` logs `undefined` instead of "sa".
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_ONLY);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2021);
    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addConsole()
                .addObject()
                .addExtra(
                    "/** @const */ var $jscomp = {};",
                    "/** @constructor */ $jscomp.PrivateMap = function() {};",
                    "$jscomp.PrivateMap.prototype.get = function(o) {};",
                    "$jscomp.PrivateMap.prototype.set = function(o, v) {};")
                .buildExternsFile("externs"));

    test(
        options,
        """
        class C {
          static get #sa() { return 'sa'; }
          static read() { return C.#sa; }
        }
        console.log(C.read());
        """,
        """
        const $jscomp$staticPrivateMap$98447280$0 = new $jscomp.PrivateMap();
        class C {}
        (function() {
          var $jscomp$priv$98447280$1 = Object.create(null);
          $jscomp$staticPrivateMap$98447280$0.set(C, $jscomp$priv$98447280$1);
          Object.defineProperty($jscomp$priv$98447280$1, "sa", {
            get: function() {
              return "sa";
            }
          });
        })();
        console.log($jscomp$staticPrivateMap$98447280$0.get(C).a);
        """);
  }

  @Test
  public void privateClassMembers_optionalChainingIntegrationTest() {
    CompilerOptions options = createCompilerOptions();
    options.setRuntimeLibraryMode(RuntimeLibraryMode.RECORD_ONLY);
    options.setVariableRenaming(VariableRenamingPolicy.OFF);
    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addConsole()
                .addExtra("/** @const */ var $jscomp = {};")
                .buildExternsFile("externs"));

    String src =
        """
        class Foo {
          #field = 100;
          #fnField = (x) => x * 2;
          #nullFn = null;
          #method(val) {
            return this.#field + val;
          }
          get #getter() {
            return this.#field * 2;
          }

          getField(target) {
            return target?.#field;
          }
          callMethod(target, val) {
            return target?.#method(val);
          }
          callFnField(target, val) {
            return target?.#fnField?.(val);
          }
          callNullFn(target, val) {
            return target?.#nullFn?.(val);
          }
          getGetter(target) {
            return target?.#getter;
          }

          static #staticField = 500;
          static #staticMethod(val) {
            return this.#staticField + val;
          }
          static get #staticGetter() {
            return this.#staticField * 3;
          }

          static getStaticField(target) {
            return target?.#staticField;
          }
          static callStaticMethod(target, val) {
            return target?.#staticMethod(val);
          }
          static getStaticGetter(target) {
            return target?.#staticGetter;
          }
        }

        const f = new Foo();
        console.log(f.getField(f));
        console.log(f.getField(null));
        try {
          f.getField({});
        } catch (e) {
          console.log(e instanceof TypeError);
        }
        console.log(f.callMethod(f, 50));
        console.log(f.callMethod(null, 50));
        try {
          f.callMethod({}, 50);
        } catch (e) {
          console.log(e instanceof TypeError);
        }
        console.log(f.callFnField(f, 10));
        console.log(f.callFnField(null, 10));
        console.log(f.callNullFn(f, 10));
        console.log(f.getGetter(f));
        console.log(f.getGetter(null));
        console.log(Foo.getStaticField(Foo));
        console.log(Foo.getStaticField(null));
        console.log(Foo.callStaticMethod(Foo, 100));
        console.log(Foo.callStaticMethod(null, 100));
        console.log(Foo.getStaticGetter(Foo));
        console.log(Foo.getStaticGetter(null));
        """;

    String expected =
        """
        const $jscomp$privateMap$98447280$0 = new $jscomp.PrivateMap();
        const $jscomp$priv$proto$98447280$2 = Object.create(null, {
          method: {
            value: function(val) {
              return $jscomp$privateMap$98447280$0.get(this).field + val;
            }
          },
          getter: {
            get: function() {
              return $jscomp$privateMap$98447280$0.get(this.$self).field * 2;
            }
          }
        });
        const $jscomp$staticPrivateMap$98447280$1 = new $jscomp.PrivateMap();
        class Foo {
          constructor() {
            var $jscomp$priv$98447280$3 = Object.create($jscomp$priv$proto$98447280$2);
            $jscomp$priv$98447280$3.$self = this;
            $jscomp$privateMap$98447280$0.set(this, $jscomp$priv$98447280$3);
            $jscomp$priv$98447280$3.field = 100;
            $jscomp$priv$98447280$3.fnField = x => x * 2;
            $jscomp$priv$98447280$3.nullFn = null;
          }
          getField(target) {
            return target == null ? void 0 : $jscomp$privateMap$98447280$0.get(target).field;
          }
          callMethod(target, val) {
            return target == null ? void 0 : $jscomp$privateMap$98447280$0.get(target).method.call(target, val);
          }
          callFnField(target, val) {
            return target == null ? void 0 : $jscomp$privateMap$98447280$0.get(target).fnField?.call(target, val);
          }
          callNullFn(target, val) {
            return target == null ? void 0 : $jscomp$privateMap$98447280$0.get(target).nullFn?.call(target, val);
          }
          getGetter(target) {
            return target == null ? void 0 : $jscomp$privateMap$98447280$0.get(target).getter;
          }
          static getStaticField(target) {
            return target == null ? void 0 : $jscomp$staticPrivateMap$98447280$1.get(target).staticField;
          }
          static callStaticMethod(target, val) {
            return target == null ? void 0 : $jscomp$staticPrivateMap$98447280$1.get(target).staticMethod.call(target, val);
          }
          static getStaticGetter(target) {
            return target == null ? void 0 : $jscomp$staticPrivateMap$98447280$1.get(target).staticGetter;
          }
          static $jscomp$staticInit$98447280$5() {
            var $jscomp$priv$98447280$4 = Object.create(null);
            $jscomp$priv$98447280$4.$self = Foo;
            $jscomp$staticPrivateMap$98447280$1.set(Foo, $jscomp$priv$98447280$4);
            $jscomp$priv$98447280$4.staticMethod = function(val) {
              return $jscomp$staticPrivateMap$98447280$1.get(this).staticField + val;
            };
            Object.defineProperty($jscomp$priv$98447280$4, "staticGetter", {
              get: function() {
                return $jscomp$staticPrivateMap$98447280$1.get(this.$self).staticField * 3;
              }
            });
            $jscomp$priv$98447280$4.staticField = 500;
          }
        }
        Foo.$jscomp$staticInit$98447280$5();
        const f = new Foo();
        console.log(f.getField(f));
        console.log(f.getField(null));
        try {
          f.getField({});
        } catch (e) {
          console.log(e instanceof TypeError);
        }
        console.log(f.callMethod(f, 50));
        console.log(f.callMethod(null, 50));
        try {
          f.callMethod({}, 50);
        } catch (e) {
          console.log(e instanceof TypeError);
        }
        console.log(f.callFnField(f, 10));
        console.log(f.callFnField(null, 10));
        console.log(f.callNullFn(f, 10));
        console.log(f.getGetter(f));
        console.log(f.getGetter(null));
        console.log(Foo.getStaticField(Foo));
        console.log(Foo.getStaticField(null));
        console.log(Foo.callStaticMethod(Foo, 100));
        console.log(Foo.callStaticMethod(null, 100));
        console.log(Foo.getStaticGetter(Foo));
        console.log(Foo.getStaticGetter(null));
        """;

    options.setLanguageOut(LanguageMode.ECMASCRIPT_2021);
    test(options, src, expected);
  }

  @Test
  public void privateClassMembers_brandCheckWithTypeAndSymbolChecks_knownBug() {
    // Expected behavior: no diagnostics with `setCheckSymbols(true)` or `setCheckTypes(true)`. In
    // `#x in this`, `#x` is a private name of `C`, not a variable, and a brand check is valid on
    // any object, including a class instance, which the type checker treats as a struct.
    String js = "class C { #x; has() { return #x in this; } }";
    CompilerOptions checkSymbols = createCompilerOptions();
    checkSymbols.setCheckSymbols(true);
    CompilerOptions checkTypes = createCompilerOptions();
    checkTypes.setCheckTypes(true);
    // TODO(b/236744850): `VarCheck` reports the private name `#x` as an undeclared variable.
    // Current behavior: `VarCheck` reports the error `JSC_UNDEFINED_VARIABLE` ("variable #x is
    //   undeclared").
    // Why it's a bug: the error is false, because `#x` is a private name declared in `C`, not a
    //   variable. Valid code fails to compile.
    test(checkSymbols, js, DiagnosticGroups.UNDEFINED_VARIABLES);
    // TODO(b/236744850): `TypeCheck` reports a brand check on a struct.
    // Current behavior: `TypeCheck` reports the warning `JSC_IN_USED_WITH_STRUCT` ("Cannot use
    //   the IN operator with structs"), because class instances are structs.
    // Why it's a bug: the warning is false, because `#x in this` only checks whether `this` has
    //   the private field `#x`, which is fine on a struct. In strict builds, the warning is an
    //   error, so valid code fails to compile.
    test(checkTypes, js, DiagnosticGroups.CHECK_TYPES);
  }

  @Test
  public void privateClassMembers_weakMapPolyfillInjection_wontFix() {
    // Won't fix (b/236744850): The `WeakMap` polyfill is injected for output with native classes.
    // Native behavior: every engine with native classes also has a native `WeakMap`, which backs
    // `$jscomp.PrivateMap`, so only output without native classes needs the polyfill.
    CompilerOptions options = createCompilerOptions();
    options.setRewritePolyfills(true);
    String src = "class C { #x = 1; }";

    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    assertThat(compile(options, src).getRuntimeJsLibManager().getInjectedLibraries())
        .containsAtLeast("es6/privatemap", "es6/weakmap");

    options.setLanguageOut(LanguageMode.ECMASCRIPT_2021);
    ImmutableList<String> injected =
        compile(options, src).getRuntimeJsLibManager().getInjectedLibraries();
    assertThat(injected).contains("es6/privatemap");
    // Current behavior: `privatemap.js` requires `es6/weakmap`, so the polyfill is injected
    //   whenever `es6/privatemap` is, including for this ES2021 output.
    // Why it's a bug: ES2021 output only runs on engines with a native `WeakMap`, so the polyfill
    //   just makes the output bigger.
    // Why we won't fix it: it only costs size (about 2 KB minified), not behavior. Without the
    //   `require`, the compiler would need its own code to inject the polyfill for ES5 output;
    //   if it does so only when `rewritePolyfills` is on, ES5 builds with it off would have to
    //   provide `WeakMap` themselves.
    assertThat(injected).contains("es6/weakmap");
  }

  @Test
  public void privateClassMembers_nativeOutputWithAdvancedOptimizations_knownBug() {
    // Expected behavior: when private elements are not lowered, ADVANCED optimizations keep the
    // private members in `C` and rename them to other private names. Private names are valid only
    // inside the body of their class.
    CompilerOptions options = createCompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    externs = ImmutableList.of(new TestExternsBuilder().addConsole().buildExternsFile("externs"));
    String js =
        """
        class C {
          static #s = 1;
          #x = 2;
          #m() { return C.#s; }
          run() { return this.#x + this.#m(); }
        }
        console.log(new C().run());
        """;
    String expected =
        """
        class a {
          static #b = 1;
          #c = 2;
          #a() { return a.#b; }
          d() { return this.#c + this.#a(); }
        }
        console.log(new a().d());
        """;
    // TODO(b/236744850): ADVANCED optimizations crash on a static private field.
    // Current behavior: `CollapseProperties` tries to move `static #s = 1` out of `C` into a
    //   global variable, and fails a `checkState` because `1` is not a function.
    //   `test(options, js, expected)` throws the `IllegalStateException`.
    // Why it's a bug: the compiler crashes on valid code.
    var ex = assertThrows(IllegalStateException.class, () -> test(options, js, expected));
    // The failed `checkState` has no message, so check that it is the one in
    // `checkForReceiverAffectedByCollapse`.
    assertThat(ex.getStackTrace()[1].getMethodName())
        .isEqualTo("checkForReceiverAffectedByCollapse");
  }
}
