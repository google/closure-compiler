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
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class Es6RewriteRestParametersTest extends CompilerTestCase {
  private static final String EXTERNS_BASE =
      new TestExternsBuilder()
          .addFunction()
          .addJSCompLibraries()
          .addExtra("$jscomp.getRestArguments = function(argument) {};")
          .build();

  public Es6RewriteRestParametersTest() {
    super(EXTERNS_BASE);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return (externs, root) -> {
      new InjectTranspilationRuntimeLibraries(compiler).process(externs, root);
      new Es6RewriteRestParameters(compiler).process(externs, root);
    };
  }

  @Before
  public void customSetUp() {
    enableNormalize();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2016);
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    enableTypeInfoValidation();
    enableTypeCheck();
    replaceTypesWithColors();
    enableMultistageCompilation();
  }

  @Test
  public void testUnusedRestParameterAtPositionZero() {
    test("function f(...zero) {}", "function f() {}");
  }

  @Test
  public void testUnusedRestParameterAtPositionOne() {
    test("function f(zero, ...one) {}", "function f(zero) {}");
  }

  @Test
  public void testUnusedRestParameterAtPositionTwo() {
    test("function f(zero, one, ...two) {}", "function f(zero, one) {}");
  }

  @Test
  public void testUsedRestParameterAtPositionZero() {
    test(
        "function f(...zero) { return zero; }",
        """
        function f() {
          let zero = $jscomp.getRestArguments.apply(0, arguments)
          return zero;
        }
        """);
  }

  @Test
  public void testUsedRestParameterAtPositionTwo() {
    test(
        "function f(zero, one, ...two) { return two; }",
        """
        function f(zero, one) {
          let two = $jscomp.getRestArguments.apply(2, arguments);
          return two;
        }
        """);
  }

  @Test
  public void testUsedRestParameterAtPositionTwo_maintainsNormalization() {
    test(
        "function f(zero, one, ...two) { function inner() {} return two; }",
        """
        function f(zero, one) {
          function inner() {} // stays hoisted
          let two = $jscomp.getRestArguments.apply(2, arguments);
          return two;
        }
        """);
  }

  @Test
  public void testUsedRestParameterAtPositionTwo_maintainsNormalization_withoutReturn() {
    test(
        "function f(zero, one, ...two) { function inner() {} two; }",
        """
        function f(zero, one) {
          function inner() {} // stays hoisted
          let two = $jscomp.getRestArguments.apply(2, arguments);
          two;
        }
        """);
  }

  @Test
  public void testUnusedRestParameterAtPositionTwo_noGoodInsertionPoint() {
    test(
        "function f(zero, one, ...two) { function inner() {} }",
        """
        function f(zero, one) {
          function inner() {} // stays hoisted
          let two = $jscomp.getRestArguments.apply(2, arguments); // declaration inserted
        }
        """);
  }

  @Test
  public void testUnusedRestParameterAtPositionZeroWithTypingOnFunction() {
    test("/** @param {...number} zero */ function f(...zero) {}", "function f() {}");
  }

  @Test
  public void testUnusedRestParameterAtPositionZeroWithInlineTyping() {
    test("function f(/** ...number */ ...zero) {}", "function f() {}");
  }

  @Test
  public void testUsedRestParameterAtPositionTwoWithTypingOnFunction() {
    test(
        "/** @param {...number} two */ function f(zero, one, ...two) { return two; }",
        """
        function f(zero, one) {
         let two = $jscomp.getRestArguments.apply(2, arguments);
         return two;
        }
        """);
  }

  @Test
  public void testUsedRestParameterAtPositionTwoWithTypingOnFunctionVariable() {
    test(
        "/** @param {...number} two */ var f = function(zero, one, ...two) { return two; }",
        """
        var f = function(zero, one) {
          let two = $jscomp.getRestArguments.apply(2, arguments);
          return two;
        }
        """);
  }

  @Test
  public void testUsedRestParameterAtPositionTwoWithTypingOnFunctionProperty() {
    test(
        "/** @param {...number} two */ ns.f = function(zero, one, ...two) { return two; }",
        """
        ns.f = function(zero, one) {
          let two = $jscomp.getRestArguments.apply(2, arguments);
          return two;
        }
        """);
  }

  @Test
  public void testUnusedRestParameterAtPositionTwoWithUsedParameterAtPositionOne() {
    test(
        "function f(zero, one, ...two) {one = (one === undefined) ? 1 : one;}",
        """
        function f(zero, one) {
          let two = $jscomp.getRestArguments.apply(2, arguments);
          one = (one === undefined) ? 1 : one;
        }
        """);
  }

  @Test
  public void testRestTranspiled_spreadExpressionsPreserved() {
    test(
        "function f(a, ...rest) { return f(...rest); }",
        """
        function f(a) {
          let rest = $jscomp.getRestArguments.apply(1, arguments);
          return f(...rest);
        }
        """);
  }

  @Test
  public void testArrayDestructuringRestDoesNotCrash() {
    test(
        "const keyFn = ([that, ...args]) => serializer(uidF, args);",
        """
        const keyFn = ([that, ...args]) => {
          return serializer(uidF, args);
        };
        """);
  }
}
