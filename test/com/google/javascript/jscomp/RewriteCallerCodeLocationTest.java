/*
 * Copyright 2007 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.RewriteCallerCodeLocation.JSC_ANONYMOUS_FUNCTION_CODE_LOCATION_ERROR;
import static com.google.javascript.jscomp.RewriteCallerCodeLocation.JSC_CALLER_LOCATION_MISUSE_ERROR;
import static com.google.javascript.jscomp.RewriteCallerCodeLocation.JSC_CALLER_LOCATION_POSITION_ERROR;
import static com.google.javascript.jscomp.RewriteCallerCodeLocation.JSC_UNDEFINED_CODE_LOCATION_ERROR;

import com.google.javascript.rhino.Node;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link RewriteCallerCodeLocation}. */
@RunWith(JUnit4.class)
public final class RewriteCallerCodeLocationTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return (Node externs, Node root) ->
        new RewriteCallerCodeLocation(compiler).process(externs, root);
  }

  @Test
  public void testSimpleCallee() {
    test(
        """
        function signal(here = goog.callerLocation()) {}
        signal();
        """,
        """
        function signal(here = goog.callerLocation()) {}
        signal(goog.callerLocationIdInternalDoNotCallOrElse('testcode:2:0'))
        """);
  }

  @Test
  public void testSimpleCallee2() {
    test(
        """
        function signal(val, here = goog.callerLocation()) {}
        const mySignal = (0, signal)(0);
        """,
        """
        function signal(val, here = goog.callerLocation()) {}
        const mySignal = signal(0, goog.callerLocationIdInternalDoNotCallOrElse('testcode:2:17'));
        """);
  }

  @Test
  public void testAlisedCalleeDoesNotRewrite() {
    test(
        """
        function signal(here = goog.callerLocation()) {}
        const mySignal = signal;
        mySignal();
        """,
        """
        function signal(here = goog.callerLocation()) {}
        const mySignal = signal;
        mySignal();
        """);
  }

  @Test
  public void testRenamedExportDoesNotRewrite() {
    testSame(
        """
        function localSignal(val, here = goog.callerLocation()) {
          return val;
        }
        exports.signal = localSignal;
        signal(0);
        """);
  }

  @Test
  public void testUndefined() {
    enableTypeCheck();
    enableParseTypeInfo();
    testError(
        """
        function signal(val, here = goog.callerLocation()) {}
        const mySignal = (0, signal)(0, undefined);
        """,
        JSC_UNDEFINED_CODE_LOCATION_ERROR);
  }

  @Test
  public void testAssigned() {
    test(
        """
        function signal(val, here = goog.callerLocation()) {}
        const mySignal = signal(0);
        """,
        """
        function signal(val, here = goog.callerLocation()) {}
        const mySignal = signal(0, goog.callerLocationIdInternalDoNotCallOrElse('testcode:2:17'));
        """);
  }

  @Test
  public void testAliasedFunctionDoesNotRewrite() {
    // aliased functions are not rewritten
    test(
        """
        function signal(val, here = goog.callerLocation()) {}
        const mySignal = signal(0); // we will rewrite this `signal(0)` call
        // mySignal will not be rewritten because we will not rewrite aliased functions
        const myComputed = computed(() => mySignal[0]() % 2 === 0);
        """,
        """
        function signal(val, here = goog.callerLocation()) {}
        const mySignal = signal(0, goog.callerLocationIdInternalDoNotCallOrElse('testcode:2:17'));
        const myComputed = computed(() => mySignal[0]() % 2 === 0);
        """);
  }

  @Test
  public void testArrayDestructuring() {
    test(
        """
        function signal(val, here = goog.callerLocation()) {}
        const [foo, setFoo] = signal(0);
        """,
        """
function signal(val, here = goog.callerLocation()) {}
const [foo, setFoo] = signal(0, goog.callerLocationIdInternalDoNotCallOrElse('testcode:2:22'));
""");
  }

  @Test
  public void testObjectDestructuring() {
    test(
        """
        function signal(val, here = goog.callerLocation()) {}
        const {foo, setFoo} = signal(0);
        """,
        """
function signal(val, here = goog.callerLocation()) {}
const {foo, setFoo} = signal(0, goog.callerLocationIdInternalDoNotCallOrElse('testcode:2:22'));
""");
  }

  @Test
  public void testPropertyAssignment() {
    test(
        """
        function signal(val, here = goog.callerLocation()) {}
        const obj = {prop: signal(0)};
        """,
        """
function signal(val, here = goog.callerLocation()) {}
const obj = {prop: signal(0, goog.callerLocationIdInternalDoNotCallOrElse('testcode:2:19'))};
""");
  }

  @Test
  public void testTernaryAssignment() {
    test(
        """
        function signal(val, here = goog.callerLocation()) {}
        const maybeSignal = Math.random() ? signal(0) : null;
        """,
        """
function signal(val, here = goog.callerLocation()) {}
const maybeSignal = Math.random() ? signal(0, goog.callerLocationIdInternalDoNotCallOrElse('testcode:2:36')) : null;
""");
  }

  @Test
  public void testIntermediateFunction() {
    test(
        """
        function signal(val, here = goog.callerLocation()) {}
        const intermediateFunction = (() => signal(0))();
        """,
        """
function signal(val, here = goog.callerLocation()) {}
const intermediateFunction = (() => signal(0, goog.callerLocationIdInternalDoNotCallOrElse('testcode:2:36')))();
""");
  }

  @Test
  public void testArray() {
    test(
        """
        function signal(val, here = goog.callerLocation()) {}
        const signalArray = [signal(0), signal(1)];
        """,
        """
function signal(val, here = goog.callerLocation()) {}
const signalArray = [signal(0, goog.callerLocationIdInternalDoNotCallOrElse('testcode:2:21')), signal(1, goog.callerLocationIdInternalDoNotCallOrElse('testcode:2:32'))];
""");
  }

  @Test
  public void testNoCallerLocationDefaultParameter() {
    // no rewriting if `= goog.callerLocation()` is not set as default parameter
    testSame(
        """
        function signal() {}
        signal();
        """);
  }

  @Test
  public void testGoogCallerLocationDefinitionInBaseJsDoesNotError() {
    String baseJsSource =
        """
        var goog = goog || {};
        goog.callerLocation = function() {};
        """;
    SourceFile baseJs = SourceFile.fromCode("javascript/closure/base.js", baseJsSource);
    // this code is only allowed in base.js
    test(srcs(baseJs), expected(baseJs));
  }

  @Test
  public void testNoRewritingIfCallerLocationIsSpecified() {
    enableTypeCheck();
    enableParseTypeInfo();

    String baseJsSource =
        """
        var goog = goog || {};
        goog.callerLocation = function() {};
        """;
    SourceFile baseJs = SourceFile.fromCode("javascript/closure/base.js", baseJsSource);

    // no rewriting if CodeLocation is provided as an argument
    String codeLocationProvided =
        """
        function signal(here = goog.callerLocation()) {}
        const mySignal = signal('path/to/file.ts:25');
        """;
    SourceFile testJs = SourceFile.fromCode("codeLocationProvided.js", codeLocationProvided);
    testSame(srcs(baseJs, testJs));

    String codeLocationProvided2 =
        """
        function signal(val, here = goog.callerLocation()) {}
        const mySignal = signal(0, xid('path/to/file.ts:25'));
        """;
    SourceFile testJs2 = SourceFile.fromCode("codeLocationProvided2.js", codeLocationProvided2);
    testSame(srcs(baseJs, testJs2));

    String codeLocationProvided3 =
        """
        function signal(val, here = goog.callerLocation()) {}
        const mySignal = signal(
          () => foo() % 2 === 0,
          xid('path/to/file.ts:25'),
        );
        """;
    SourceFile testJs3 = SourceFile.fromCode("codeLocationProvided3.js", codeLocationProvided3);
    testSame(srcs(baseJs, testJs3));

    // no rewriting if CodeLocation is provided as an argument, regardless of the type of the arg
    String codeLocationProvided4 =
        """
        function signal(here = goog.callerLocation()) {}
        const mySignal = signal(foo());
        """;
    SourceFile testJs4 = SourceFile.fromCode("codeLocationProvided4.js", codeLocationProvided4);
    testSame(srcs(baseJs, testJs4));
  }

  @Test
  public void testNoRewriteOnClassMethods() {
    testError(
        """
        class Foo {
          bar(here = goog.callerLocation()) {}
        }
        const baz = new Foo();
        baz.bar()
        """,
        JSC_ANONYMOUS_FUNCTION_CODE_LOCATION_ERROR);
  }

  @Test
  public void testTransitiveUsage() {
    // `foo` has a goog.callerLocation() default parameter.
    // When `bar` calls `foo` internally, we will not rewrite the call sites of `bar` unless
    // `bar` itself has a goog.callerLocation() default.
    test(
        """
        function foo(val, here = goog.callerLocation()) {}
        function bar(val) {
          foo(val);
        }
        // `bar(0)` will not get rewritten because `bar` does not have a goog.callerLocation()
        // default parameter
        bar(0)
        """,
        """
        function foo(val, here = goog.callerLocation()) {}
        function bar(val) {
          foo(val, goog.callerLocationIdInternalDoNotCallOrElse('testcode:3:2'));
        }
        bar(0)
        """);

    test(
        """
        function foo(val, here = goog.callerLocation()) {}
        function bar(val, here = goog.callerLocation()) {
          foo(val);
        }
        // `bar(0)` will get rewritten because `bar` has a goog.callerLocation() default
        // parameter
        bar(0)
        """,
        """
        function foo(val, here = goog.callerLocation()) {}
        function bar(val, here = goog.callerLocation()) {
          foo(val, goog.callerLocationIdInternalDoNotCallOrElse('testcode:3:2'));
        }
        bar(0, goog.callerLocationIdInternalDoNotCallOrElse('testcode:7:0'));
        """);
  }

  @Test
  public void testErrorOnCallerLocationNotUsedAsDefaultParam() {
    // no rewriting if `goog.callerLocation()` is not used in a default parameter
    testError(
        """
        function foo() {
          const x = goog.callerLocation();
        }
        """,
        JSC_CALLER_LOCATION_MISUSE_ERROR);
  }

  @Test
  public void testDestructuringOptionsBag() {
    // Error if `goog.callerLocation()` is used in an object literal
    testError(
        """
        function foo({val1, val2, here = goog.callerLocation()}) {}
        foo({val1:0, val2:0})
        """,
        JSC_CALLER_LOCATION_MISUSE_ERROR);
  }

  @Test
  public void testMultipleDefaultParameters() {
    testError(
        """
        function foo(val1 = 0, val2 = 5, here = goog.callerLocation()) {}
        foo()
        foo(1)
        foo(1, 2)
        """,
        JSC_CALLER_LOCATION_POSITION_ERROR);

    testError(
        """
        function foo(val1 = 0, val2 = 5, here = goog.callerLocation(), val3 = 10, val4 = 15) {}
        foo()
        foo(1)
        foo(1, 2)
        foo(1, 2, goog.callerLocationIdInternalDoNotCallOrElse('customString'))
        foo(1, 2, goog.callerLocationIdInternalDoNotCallOrElse('customString'), 3)
        foo(1, 2, goog.callerLocationIdInternalDoNotCallOrElse('customString'), 3, 4)
        """,
        JSC_CALLER_LOCATION_POSITION_ERROR);
  }

  @Test
  public void testTaggedTemplateLiteralsDoesNotRewrite() {
    testSame(
        """
        function signal(strings, here = goog.callerLocation()) {}
        const tpl = signal`not passing a here argument explicitly`;
        const tpl = signal`passing a here argument ${'here!'}`;
        """);
  }

  @Test
  public void testAnonymousFunctionCallErrors() {
    testError(
        """
        const add = function(a, b, here = goog.callerLocation()) {
          return a + b;
        }
        add(2, 3);
        """,
        JSC_ANONYMOUS_FUNCTION_CODE_LOCATION_ERROR);
  }

  @Test
  public void testModuleRewriting() {
    test(
        """
        function module$contents$main_signal(val, here = goog.callerLocation()) {
          return val;
        }
        module$exports$main.signal = module$contents$main_signal;
        // `module$exports$fileName.signal` is the call-site function name after
        // ClosureRewriteModule runs
        (0,module$exports$main.signal)(0);
        """,
        """
function module$contents$main_signal(val, here = goog.callerLocation()) {
  return val;
}
module$exports$main.signal = module$contents$main_signal;
(0,module$exports$main.signal)(0, goog.callerLocationIdInternalDoNotCallOrElse('testcode:7:0'));
""");
  }

  @Test
  public void testScopeRewriting() {
    test(
        """
        function signal(here = goog.callerLocation()) {}
        class Button {
          method(signal) {
            signal(); // this signal() call shouldn't be rewritten
          }
        }
        signal();
        """,
        """
        function signal(here = goog.callerLocation()) {}
        class Button {
          method(signal) {
            signal(); // not rewritten
          }
        }
        signal(goog.callerLocationIdInternalDoNotCallOrElse('testcode:7:0'));
        """);

    test(
        """
        function signal() {}
        function outter() {
          function signal(here = goog.callerLocation()) {}
          signal(); // this signal() call should be rewritten
        }
        signal();
        """,
        """
        function signal() {}
        function outter() {
          function signal(here = goog.callerLocation()) {}
          signal(goog.callerLocationIdInternalDoNotCallOrElse('testcode:4:2')); // rewritten
        }
        signal();
        """);
  }
}
