/*
 * Copyright 2026 The Closure Compiler Authors.
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

import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PeepholeTypePredicatesIntegrationTest extends CompilerTestCase {
  private static final String MATH =
      """
      /** @const */
      const Math = {};
      /** @nosideeffects */
      Math.random = function(){};
      /**
       * @param {number} radians
       * @return {number}
       * @nosideeffects
       */
      Math.sin = function(radians) {};
      """;

  public PeepholeTypePredicatesIntegrationTest() {
    super(CompilerTypeTestCase.DEFAULT_EXTERNS + MATH);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    enableNormalize();
    disableCompareJsDoc();
    enableGatherExternProperties();
    enableTypeCheck();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        compiler.getOptions().setUseTypesForLocalOptimization(true);
        new PureFunctionIdentifier.Driver(compiler).process(externs, root);
        new PeepholeOptimizationsPass(
                compiler,
                getName(),
                new PeepholeFoldConstants(false, true),
                new PeepholeReplaceKnownMethods(false, true))
            .process(externs, root);
      }
    };
  }

  @Test
  public void testFoldArrayIsArray() {
    replaceTypesWithColors();

    foldArrayIsArrayTyped("!Array<symbol>", "true");
    foldArrayIsArrayTyped("!ReadonlyArray<!Array<undefined>>", "true");
    foldArrayIsArrayTyped("!ReadonlyArray<string>|!Array<string|bigint>", "true");

    foldArrayIsArrayTyped("string", "false");
    foldArrayIsArrayTyped("?bigint", "false");
    foldArrayIsArrayTyped("symbol", "false");
    foldArrayIsArrayTyped("?number|string|(bigint|number)", "false");

    foldSameArrayIsArrayTyped("?Array<number>");
    foldSameArrayIsArrayTyped("Array<string>");
  }

  @Test
  public void testFoldArrayIsArrayPreservesObservableArguments() {
    ignoreWarnings(TypeCheck.WRONG_ARGUMENT_COUNT);

    fold(
        """
        function /** boolean */ f(/** number */ x) {
          return Array.isArray(x++);
        }
        """,
        """
        function f(x) {
          return x++, false;
        }
        """);
    fold(
        """
        function f(/** !Array<number> */ a) {
          return Array.isArray(a = []);
        }
        """,
        """
        function f(a) {
          return a = [], true;
        }
        """);
    fold(
        """
        function f() {
          return Array.isArray([Math.random(), Math.sin(2)]);
        }
        """,
        """
        function f() {
          return true;
        }
        """);
    fold(
        """
        function f(/** !Array<number> */ a, /** !Array<number> */ b) {
          return Array.isArray(a, ...b);
        }
        """,
        """
        function f(a, b) {
          return [...b], true;
        }
        """);
    fold(
        """
        function f(/** !Array<number> */ a) {
          return Array.isArray(a, foo() + bar());
        }
        """,
        """
        function f(a) {
          return foo(), bar(), true;
        }
        """);
    fold(
        """
        /** @param {!Array<number>=} a */
        function f(a = [1, 2]) {
          return Array.isArray(a.concat(3, 4));
        }
        """,
        """
        function f(a = [1, 2]) {
          return true;
        }
        """);
    fold(
        """
        /**
         * @param {!Array<bigint>} a
         * @return {boolean}
         */
        function f(a) {
          return Array.isArray((a.shift(), a));
        }
        """,
        """
        function f(a) {
          return a.shift(), true;
        }
        """);
  }

  @Test
  public void testFoldTypeofPreservesObservableEffects() {
    fold(
        """
        function f() {
          return typeof (Math.random(), Math.sin(2), null);
        }
        """,
        """
        function f() {
          return 'object';
        }
        """);
    fold(
        """
        function f() {
          return typeof (foo(), Math.sin(2), null);
        }
        """,
        """
        function f() {
          return foo(), 'object';
        }
        """);

    replaceTypesWithColors();

    fold(
        """
        /** @constructor */ function Foo() {}
        function f() {
          return typeof (Math.random(), Math.sin(2), Foo);
        }
        """,
        """
        /** @constructor */ function Foo() {}
        function f() {
          return 'function';
        }
        """);
    fold(
        """
        /** @constructor */ function Foo() {}
        function f() {
          return typeof (foo(), Math.sin(2), Foo);
        }
        """,
        """
        /** @constructor */ function Foo() {}
        function f() {
          return foo(), 'function';
        }
        """);
    fold(
        """
        /** @constructor */ function Foo() {}
        function f(/** !Foo */ x) {
          return typeof (Math.random(), Math.sin(2), x);
        }
        """,
        """
        /** @constructor */ function Foo() {}
        function f(x) {
          return 'object';
        }
        """);
    fold(
        """
        /** @constructor */ function Foo() {}
        function f(/** !Foo */ x) {
          return typeof (foo(), Math.sin(2), x);
        }
        """,
        """
        /** @constructor */ function Foo() {}
        function f(x) {
          return foo(), 'object';
        }
        """);
  }

  @Test
  public void testArrayIsArrayPreservesNarrowing() {
    foldSame(
        """
        /**
         * @param {!Array<number>|string} s
         * @return {number}
         */
        function f(s) {
          if (Array.isArray(s)) {
            return 0;
          } else {
            return s.charCodeAt(0);
          }
        };
        """);
    foldSame(
        """
        /**
         * @param {string|!Array<number>|number} s
         * @return {number}
         */
        function f(s) {
          if (typeof s == 'string') {
            return s.toLowerCase().indexOf('a');
          } else if (Array.isArray(s)) {
            return s.length;
          } else {
            return s.toFixed(0).length;
          }
        };
        """);
  }

  protected void foldSame(String js) {
    testSame(js);
  }

  protected void fold(String js, String expected) {
    test(js, expected);
  }

  private void foldSameArrayIsArrayTyped(String type) {
    foldArrayIsArrayTyped(type, "Array.isArray(a)");
  }

  private void foldArrayIsArrayTyped(String type, String expected) {
    test(
        "function f(/** " + type + " */ a) { return Array.isArray(a); }",
        "function f(/** " + type + " */ a) { return " + expected + "; }");
  }
}
