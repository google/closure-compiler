/*
 * Copyright 2016 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class J2clIntegrationTest extends IntegrationTestCase {
  @Test
  public void testInlineClassStaticGetterSetter() {
    externs =
        ImmutableList.of(
            new TestExternsBuilder().addFunction().addConsole().buildExternsFile("externs.js"));
    test(
        createCompilerOptions(),
        """
        var A = class {
          static $clinit() {
            A.$x = 2;
          }
          static get x() {
            return A.$clinit(), A.$x;
          }
          static set x(value) {
            A.$clinit(), A.$x = value;
          }
        };
        A.x = 3;
        console.log(A.x);
        """,
        """
        var a;
        a = 2;
        a = 3;
        console.log(a);
        """);
  }

  @Test
  public void testInlineDefinePropertiesGetterSetter() {
    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addObject()
                .addFunction()
                .addConsole()
                .buildExternsFile("externs.js"));
    test(
        createCompilerOptions(),
        """
        /** @constructor */
        var A = function() {};
        A.$clinit = function() {
          A.$x = 2;
        };
        Object.defineProperties(
            A,
            {
              x: {
                configurable:true,
                enumerable:true,
                get: function() {
                  return A.$clinit(), A.$x;
                },
                set: function(value) {
                  A.$clinit(), A.$x = value;
                }
              }
            });
        A.x = 3;
        console.log(A.x);
        """,
        """
        var a;
        a = 2;
        a = 3;
        console.log(a);
        """);
  }

  @Test
  public void testStripNoSideEffectsClinit() {
    String source =
        """
        class Preconditions {
          static $clinit() {
            Preconditions.$clinit = function() {};
          }
          static check(str) {
            Preconditions.$clinit();
            if (str[0] > 'a') {
              return Preconditions.check(str + str);
            }
            return str;
          }
        }
        class Main {
          static main() {
            var a = Preconditions.check('a');
            alert('hello');
          }
        }
        Main.main();
        """;
    test(createCompilerOptions(), source, "alert('hello')");
  }

  @Test
  public void testFoldJ2clClinits() {
    String code =
        """
        function InternalWidget(){}
        InternalWidget.$clinit = function () {
          InternalWidget.$clinit = function() {};
          InternalWidget.$clinit();
        };
        InternalWidget.$clinit();
        """;

    test(createCompilerOptions(), code, "");
  }

  /**
   * Demonstrates the interface marker renaming bug caused by goog.inherits clobbering
   * Function.prototype (b/253690550).
   *
   * <p>When {@code inherits(childCtor, parentCtor)} with {@code @param {!Function}} is present,
   * type inference clobbers {@code Function.prototype}, giving it a non-axiomatic, non-invalidating
   * color (0x51e30d0a5d476004L). As a result, AmbiguateProperties ambiguates {@code
   * $implements__FooInterface} with {@code AnotherClass.prototype.anotherMethod} to {@code $a$}.
   * Without {@code inherits}, the marker is preserved as {@code $$implements__FooInterface$}.
   */
  @Test
  public void testInterfaceMarkerRenamingBug_b253690550() {
    externs =
        ImmutableList.of(
            new TestExternsBuilder()
                .addObject()
                .addFunction()
                .addAlert()
                .buildExternsFile("externs.js"));

    CompilerOptions options = createCompilerOptions();
    options.setAmbiguateProperties(true);
    options.setDevirtualizeMethods(false);
    options.setGeneratePseudoNames(true);
    options.setPrettyPrint(true);

    String baselineSource =
        """
        /** @interface */
        class FooInterface {}
        /** @type {boolean} */
        FooInterface.prototype.$implements__FooInterface;

        /**
         * @noinline
         * @param {!Function} ctor
         */
        function markImplementor(ctor) {
          ctor.prototype.$implements__FooInterface = true;
        }

        /**
         * @suppress {checkTypes}
         * @implements {FooInterface}
         */
        class FooImpl {
          /** @noinline */
          realMethod(x) {
            alert('real:' + x);
            return x;
          }
        }
        class AnotherClass {
          /** @noinline */
          anotherMethod(x) {
            alert('another:' + x);
            return x;
          }
        }
        markImplementor(FooImpl);

        /**
         * @noinline
         * @param {!FooInterface} instance
         */
        function isInstance(instance) {
          return !!instance.$implements__FooInterface;
        }

        function run(/** !FooImpl */ f, /** !AnotherClass */ a, x) {
          f.realMethod(x);
          a.anotherMethod(x);
          alert(isInstance(f));
        }
        alert(run);
        run(new FooImpl(), new AnotherClass(), 1);
        """;

    String bugSource =
        """
        /**
         * @param {!Function} childCtor
         * @param {!Function} parentCtor
         */
        function inherits(childCtor, parentCtor) {
          childCtor.prototype = Object.create(parentCtor.prototype);
        }
        """
            + baselineSource;

    // 1. Baseline: without inherits(), Function.prototype is axiomatic TOP_OBJECT (invalidating).
    // AmbiguateProperties skips $implements__FooInterface, so RenameProperties preserves it
    // as $$implements__FooInterface$, while realMethod and anotherMethod are ambiguated to $a$.
    test(
        options,
        baselineSource,
        """
        function $markImplementor$$($ctor$$){
          $ctor$$.prototype.$$implements__FooInterface$=!0
        }
        class $FooImpl$${
          $a$($x$$){alert("real:"+$x$$)}
        }
        class $AnotherClass$${
          $a$($x$jscomp$1$$){alert("another:"+$x$jscomp$1$$)}
        }
        $markImplementor$$($FooImpl$$);
        function $isInstance$$($instance$$){
          return!!$instance$$.$$implements__FooInterface$
        }
        function $run$$($f$$,$a$$,$x$jscomp$2$$){
          $f$$.$a$($x$jscomp$2$$);
          $a$$.$a$($x$jscomp$2$$);
          alert($isInstance$$($f$$))
        }
        alert($run$$);
        $run$$(new $FooImpl$$,new $AnotherClass$$,1)
        """);

    // 2. Bug: with inherits(), Function.prototype is clobbered by type inference,
    // receiving a non-invalidating color. AmbiguateProperties no longer skips
    // $implements__FooInterface and ambiguates it to $a$ (sharing the property name
    // with AnotherClass.prototype.anotherMethod), instead of keeping it unrenamed.
    test(
        options,
        bugSource,
        """
        function $markImplementor$$($ctor$$){
          $ctor$$.prototype.$a$=!0
        }
        class $FooImpl$${
          $b$($x$$){alert("real:"+$x$$)}
        }
        class $AnotherClass$${
          $a$($x$jscomp$1$$){alert("another:"+$x$jscomp$1$$)}
        }
        $markImplementor$$($FooImpl$$);
        function $isInstance$$($instance$$){
          return!!$instance$$.$a$
        }
        function $run$$($f$$,$a$$,$x$jscomp$2$$){
          $f$$.$b$($x$jscomp$2$$);
          $a$$.$a$($x$jscomp$2$$);
          alert($isInstance$$($f$$))
        }
        alert($run$$);
        $run$$(new $FooImpl$$,new $AnotherClass$$,1)
        """);
  }

  @Override
  @Before
  public void setUp() {
    super.setUp();
    inputFileNameSuffix = ".java.js";
  }

  public CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    return options;
  }
}
