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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.Es6RewriteDestructuring.ObjectDestructuringRewriteMode;
import com.google.javascript.jscomp.colors.Color;
import com.google.javascript.jscomp.colors.StandardColors;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class Es6RewriteDestructuringTest extends CompilerTestCase {

  private ObjectDestructuringRewriteMode destructuringRewriteMode =
      ObjectDestructuringRewriteMode.REWRITE_ALL_OBJECT_PATTERNS;

  private static final String EXTERNS_BASE =
      new TestExternsBuilder().addObject().addArguments().addString().addJSCompLibraries().build();

  public Es6RewriteDestructuringTest() {
    super(EXTERNS_BASE);
  }

  @Before
  public void customSetUp() throws Exception {
    enableNormalize();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2018);
    enableTypeCheck();
    enableTypeInfoValidation();
    replaceTypesWithColors();
    enableMultistageCompilation();

    // there are a lot of 'property x never defined on ?' warnings caused by object destructuring
    ignoreWarnings(TypeCheck.POSSIBLE_INEXISTENT_PROPERTY);
  }

  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();
    this.destructuringRewriteMode = ObjectDestructuringRewriteMode.REWRITE_ALL_OBJECT_PATTERNS;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new Es6RewriteDestructuring.Builder(compiler)
        .setDestructuringRewriteMode(destructuringRewriteMode)
        .build();
  }

  @Test
  public void testForLoopInitializer_let_accessedInClosure() {
    test(
        """
        const aClosures = [];
        const bClosures = [];
        for (let a = 0, [b, c] = [a, 3]; b < c; a--, b++) {
          aClosures.push(() => a);
          bClosures.push(() => b);
        }
        """,
        """
        const aClosures = [];
        const bClosures = [];
        for (let a = 0, b, c,
                 $jscomp$destructuring$var0$unused = ($jscomp$destructuring$var1 => {
                   var $jscomp$destructuring$var2 =
                       (0, $jscomp.makeIterator)($jscomp$destructuring$var1);
                   b = $jscomp$destructuring$var2.next().value;
                   c = $jscomp$destructuring$var2.next().value;
                   return $jscomp$destructuring$var1;
                 })([a, 3]);
             b < c; a--, b++) {
          aClosures.push(() => {
            return a;
          });
          bClosures.push(() => {
            return b;
          });
        }
        """);
  }

  @Test
  public void testObjectDestructuring_forLoopInitializer_doesNotCrash() {
    test(
        """
        function isDeclaredInLoop(path) {
          for (let {
              parentPath,
              key
            } = path;
            parentPath; ({
              parentPath,
              key
            } = parentPath)) {
            return isDeclaredInLoop(parentPath);
          }
          return false;
        }
        """,
        """
        function isDeclaredInLoop(path) {
          for (let parentPath, key,
               $jscomp$destructuring$var0$unused = ($jscomp$destructuring$var1 => {
                 var $jscomp$destructuring$var2 = $jscomp$destructuring$var1;
                 parentPath = $jscomp$destructuring$var2.parentPath;
                 key = $jscomp$destructuring$var2.key;
                 return $jscomp$destructuring$var1;
               })(path);
               parentPath; ($jscomp$destructuring$var3 => {
                 var $jscomp$destructuring$var4 = $jscomp$destructuring$var3;
                 parentPath = $jscomp$destructuring$var4.parentPath;
                 key = $jscomp$destructuring$var4.key;
                 return $jscomp$destructuring$var3;
               })(parentPath)) {
            return isDeclaredInLoop(parentPath);
          }
          return false;
        }
        """);
  }

  @Test
  public void testObjectDestructuring() {
    test(
        "var {a: b, c: d} = foo();",
        """
        var b; var d;
        var $jscomp$destructuring$var0 = foo();
        b = $jscomp$destructuring$var0.a;
        d = $jscomp$destructuring$var0.c;
        """);

    test(
        "var {a,b} = foo();",
        """
        var a; var b;
        var $jscomp$destructuring$var0 = foo();
        a = $jscomp$destructuring$var0.a;
        b = $jscomp$destructuring$var0.b;
        """);

    test(
        "let {a,b} = foo();",
        """
        var $jscomp$destructuring$var0 = foo();
        let a = $jscomp$destructuring$var0.a;
        let b = $jscomp$destructuring$var0.b;
        """);

    test(
        "const {a,b} = foo();",
        """
        /** @const */ var $jscomp$destructuring$var0 = foo();
        const a = $jscomp$destructuring$var0.a;
        const b = $jscomp$destructuring$var0.b;
        """);

    test(
        "var x; ({a: x} = foo());",
        """
        var x;
        var $jscomp$destructuring$var0 = foo();
        x = $jscomp$destructuring$var0.a;
        """);
  }

  @Test
  public void testObjectDestructuringWithInitializer() {
    test(
        "var {a : b = 'default'} = foo();",
        """
        var b;
        var $jscomp$destructuring$var0 = foo();
        b = ($jscomp$destructuring$var0.a === void 0) ?
            'default' :
            $jscomp$destructuring$var0.a
        """);

    test(
        "var {a = 'default'} = foo();",
        """
        var a;
        var $jscomp$destructuring$var0 = foo();
        a = ($jscomp$destructuring$var0.a === void 0) ?
            'default' :
            $jscomp$destructuring$var0.a
        """);
  }

  @Test
  public void testObjectDestructuring_inVanillaForInitialize_var() {
    test(
        """
        A: B: for (
            var z = 1, {x: {a: b = 'default'}} = foo(), x = 0, {c} = bar();
            true;
            c++) {
        }
        """,
        """
        var z = 1;
        var b;
        var $jscomp$destructuring$var0 = foo();
        var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.x;
        b = $jscomp$destructuring$var1.a === void 0 ?
            'default' : $jscomp$destructuring$var1.a;

        var x = 0;
        var c;
        var $jscomp$destructuring$var2 = bar();
        c = $jscomp$destructuring$var2.c;
        A: B: for (; true; c++) {}
        """);
  }

  @Test
  public void testObjectDestructuring_inVanillaForInitialize_const() {
    test(
        """
        A: B: for (
            const z = 1, {x: {a: b = 'default'}} = foo(), x = 0, {c} = bar();
            true;
            c++) {
        }
        """,
        """
        {
          const z = 1;
          /** @const */ var $jscomp$destructuring$var0 = foo();
          /** @const */ var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.x;
          const b = $jscomp$destructuring$var1.a === void 0 ?
              'default' : $jscomp$destructuring$var1.a;
          {
            const x = 0;
            /** @const */ var $jscomp$destructuring$var2 = bar();
            const c = $jscomp$destructuring$var2.c;
            A: B: for (; true; c++) {}
          }
        }
        """);
  }

  @Test
  public void testObjectDestructuring_inVanillaForInitialize_let() {
    test(
        """
        A: B: for (
            let z = 1, {x: {a: b = 'default'}} = foo(), x = 0, {c} = bar();
            true;
            c++) {
        }
        """,
        """
        A: B: for (let z = 1, b,
                   $jscomp$destructuring$var0$unused = ($jscomp$destructuring$var1 => {
                     var $jscomp$destructuring$var2 = $jscomp$destructuring$var1;
                     var $jscomp$destructuring$var3 = $jscomp$destructuring$var2.x;
                     b = $jscomp$destructuring$var3.a === void 0 ?
                         'default' :
                         $jscomp$destructuring$var3.a;
                     return $jscomp$destructuring$var1;
                   })(foo()),
                   x = 0, c,
                   $jscomp$destructuring$var4$unused = ($jscomp$destructuring$var5 => {
                     var $jscomp$destructuring$var6 = $jscomp$destructuring$var5;
                     c = $jscomp$destructuring$var6.c;
                     return $jscomp$destructuring$var5;
                   })(bar());
                   true; c++) {}
        """);
  }

  @Test
  public void testObjectDestructuringNested() {
    test(
        "var {a: {b}} = foo();",
        """
        var b;
        var $jscomp$destructuring$var0 = foo();
        var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.a;
        b = $jscomp$destructuring$var1.b
        """);
  }

  @Test
  public void testObjectDestructuringComputedProps() {
    test(
        "var {[a]: b} = foo();",
        "var b; var $jscomp$destructuring$var0 = foo(); b = $jscomp$destructuring$var0[a];");

    test(
        "({[a]: b} = foo());",
        "var $jscomp$destructuring$var0 = foo(); b = $jscomp$destructuring$var0[a];");

    test(
        "var {[foo()]: x = 5} = {};",
        """
        var x;
        var $jscomp$destructuring$var0 = {};
        var $jscomp$destructuring$var1 = $jscomp$destructuring$var0[foo()];
        x = $jscomp$destructuring$var1 === void 0 ?
            5 : $jscomp$destructuring$var1
        """);

    test(
        "function f({['KEY']: x}) {}",
        """
        function f($jscomp$destructuring$var0) {
          var x;
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0
          x = $jscomp$destructuring$var1['KEY']
        }
        """);
  }

  // https://github.com/google/closure-compiler/issues/2189
  @Test
  public void testGithubIssue2189() {
    setExpectParseWarningsInThisTest();
    test(
        """
        /**
         * @param {string} a
         * @param {{b: ?<?>}} __1
         */
        function x(a, { b }) {}
        """,
        """
        function x(a, $jscomp$destructuring$var0) {
          var b;
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          b=$jscomp$destructuring$var1.b;
        }
        """);
  }

  @Test
  public void testObjectDestructuringStrangeProperties() {
    test(
        "var {5: b} = foo();",
        "var b; var $jscomp$destructuring$var0 = foo(); b = $jscomp$destructuring$var0['5']");

    test(
        "var {0.1: b} = foo();",
        """
        var b;
        var $jscomp$destructuring$var0 = foo();
        b = $jscomp$destructuring$var0['0.1']
        """);

    test(
        "var {'str': b} = foo();",
        """
        var b;
        var $jscomp$destructuring$var0 = foo();
        b = $jscomp$destructuring$var0['str']
        """);
  }

  @Test
  public void testObjectDestructuringFunction() {
    test(
        "function f({a: b}) {}",
        """
        function f($jscomp$destructuring$var0) {
          var b;
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0
          b = $jscomp$destructuring$var1.a
        }
        """);

    test(
        "function f({a}) {}",
        """
        function f($jscomp$destructuring$var0) {
          var a;
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0
          a = $jscomp$destructuring$var1.a
        }
        """);

    test(
        "function f({k: {subkey : a}}) {}",
        """
        function f($jscomp$destructuring$var0) {
          var a;
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0
          var $jscomp$destructuring$var2 = $jscomp$destructuring$var1.k;
          a = $jscomp$destructuring$var2.subkey;
        }
        """);

    test(
        "function f({k: [x, y, z]}) {}",
        """
        function f($jscomp$destructuring$var0) {
          var x; var y; var z;
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0
          var $jscomp$destructuring$var2 =
              (0, $jscomp.makeIterator)($jscomp$destructuring$var1.k);
          x = $jscomp$destructuring$var2.next().value;
          y = $jscomp$destructuring$var2.next().value;
          z = $jscomp$destructuring$var2.next().value;
        }
        """);

    test(
        "function f({key: x = 5}) {}",
        """
        function f($jscomp$destructuring$var0) {
          var x;
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0
          x = $jscomp$destructuring$var1.key === void 0 ?
              5 : $jscomp$destructuring$var1.key
        }
        """);

    test(
        "function f({[key]: x = 5}) {}",
        """
        function f($jscomp$destructuring$var0) {
          var x;
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0
          var $jscomp$destructuring$var2 = $jscomp$destructuring$var1[key]
          x = $jscomp$destructuring$var2 === void 0 ?
              5 : $jscomp$destructuring$var2
        }
        """);

    test(
        "function f({x = 5}) {}",
        """
        function f($jscomp$destructuring$var0) {
          var x;
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0
          x = $jscomp$destructuring$var1.x === void 0 ?
              5 : $jscomp$destructuring$var1.x
        }
        """);
  }

  @Test
  public void testObjectDestructuringFunctionBadJsdoc() {
    // see https://github.com/google/closure-compiler/issues/3175
    setExpectParseWarningsInThisTest(); // intentionally pass bad JSDoc
    ignoreWarnings(TypeCheck.INEXISTENT_PROPERTY);
    test(
        """
        /**
         * @param {{foo: string[]}} obj
         * @param {string} id
         */
        function f({foo}, id) {}
        """,
        """
        function f($jscomp$destructuring$var0, id) {
          var foo;
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          foo = $jscomp$destructuring$var1.foo;
        }
        """);
  }

  @Test
  public void testObjectDestructuringFunctionJsDoc() {
    test(
        "function f(/** {x: number, y: number} */ {x, y}) {}",
        """
        function f($jscomp$destructuring$var0) {
          var x; var y;
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          x = $jscomp$destructuring$var1.x;
          y = $jscomp$destructuring$var1.y;
        }
        """);
  }

  @Test
  public void testDefaultParametersDestructuring() {
    test(
        "function f({a,b} = foo()) {}",
        """
        function f($jscomp$destructuring$var0){
          var a; var b;
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0=== void 0 ?
            foo() : $jscomp$destructuring$var0;
          a = $jscomp$destructuring$var1.a;
          b = $jscomp$destructuring$var1.b;
        }
        """);
  }

  @Test
  public void testArrayDestructuring() {
    test(
        "var [x,y] = z();",
        """
        var x;
        var y;
        var $jscomp$destructuring$var0 = (0, $jscomp.makeIterator)(z());
        x = $jscomp$destructuring$var0.next().value;
        y = $jscomp$destructuring$var0.next().value;
        """);

    test(
        """
        var x,y;
        [x,y] = z();
        """,
        """
        var x;
        var y;
        var $jscomp$destructuring$var0 = (0, $jscomp.makeIterator)(z());
        x = $jscomp$destructuring$var0.next().value;
        y = $jscomp$destructuring$var0.next().value;
        """);

    test(
        """
        var [a,b] = c();
        var [x,y] = z();
        """,
        """
        var a;
        var b;
        var $jscomp$destructuring$var0 = (0, $jscomp.makeIterator)(c());
        a = $jscomp$destructuring$var0.next().value;
        b = $jscomp$destructuring$var0.next().value;
        var x;
        var y;
        var $jscomp$destructuring$var1 = (0, $jscomp.makeIterator)(z());
        x = $jscomp$destructuring$var1.next().value;
        y = $jscomp$destructuring$var1.next().value;
        """);
  }

  @Test
  public void testArrayDestructuringDefaultValues() {
    test(
        "var a; [a=1] = b();",
        """
        var a;
        var $jscomp$destructuring$var0 = (0, $jscomp.makeIterator)(b())
        var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.next().value
        a = ($jscomp$destructuring$var1 === void 0) ?
            1 :
            $jscomp$destructuring$var1;
        """);

    test(
        "var [a=1] = b();",
        """
        var a;
        var $jscomp$destructuring$var0 = (0, $jscomp.makeIterator)(b())
        var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.next().value
        a = ($jscomp$destructuring$var1 === void 0) ?
            1 :
            $jscomp$destructuring$var1;
        """);

    test(
        "var [a, b=1, c] = d();",
        """
        var a;
        var b;
        var c;
        var $jscomp$destructuring$var0=(0, $jscomp.makeIterator)(d());
        a = $jscomp$destructuring$var0.next().value;
        var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.next().value;
        b = ($jscomp$destructuring$var1 === void 0) ?
            1 :
            $jscomp$destructuring$var1;
        c=$jscomp$destructuring$var0.next().value
        """);

    test(
        srcs("var a; [[a] = ['b']] = [];"),
        expected(
            """
            var a;
            var $jscomp$destructuring$var0 = (0, $jscomp.makeIterator)([]);
            var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.next().value;
            var $jscomp$destructuring$var2 = (0, $jscomp.makeIterator)(
                $jscomp$destructuring$var1 === void 0
                    ? ['b']
                    : $jscomp$destructuring$var1);
            a = $jscomp$destructuring$var2.next().value
            """));
  }

  @Test
  public void testArrayDestructuringParam() {
    test(
        "function f([x,y]) { use(x); use(y); }",
        """
        function f($jscomp$destructuring$var0) {
          var x; var y;
          var $jscomp$destructuring$var1 = (0, $jscomp.makeIterator)($jscomp$destructuring$var0);
          x = $jscomp$destructuring$var1.next().value;
          y = $jscomp$destructuring$var1.next().value;
          use(x);
          use(y);
        }
        """);

    test(
        "function f([x, , y]) { use(x); use(y); }",
        """
        function f($jscomp$destructuring$var0) {
          var x; var y;
          var $jscomp$destructuring$var1 = (0, $jscomp.makeIterator)($jscomp$destructuring$var0);
          x = $jscomp$destructuring$var1.next().value;
          $jscomp$destructuring$var1.next();
          y = $jscomp$destructuring$var1.next().value;
          use(x);
          use(y);
        }
        """);
  }

  @Test
  public void testArrayDestructuring_inVanillaForInitializer_var() {
    test(
        "A: B: for (var z = 1, [a = 'default'] = foo(), x = 0, [c] = bar(); true; c++) { }",
        """
        var z = 1;
        var a;
        var $jscomp$destructuring$var0 = (0, $jscomp.makeIterator)(foo());
        var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.next().value;
        a = $jscomp$destructuring$var1 === void 0 ?
            'default' : $jscomp$destructuring$var1;

        var x = 0;
        var c;
        var $jscomp$destructuring$var2 = (0, $jscomp.makeIterator)(bar());
        c = $jscomp$destructuring$var2.next().value;

        A: B: for (; true; c++) {}
        """);
  }

  @Test
  public void testArrayDestructuring_inVanillaForInitializer_const() {
    test(
        "A: B: for (const z = 1, [a = 'default'] = foo(), x = 0, [c] = bar(); true; c++) { }",
        """
        {
          const z = 1;
          var $jscomp$destructuring$var0 = (0, $jscomp.makeIterator)(foo());
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.next().value;
          const a = $jscomp$destructuring$var1 === void 0 ?
              'default' : $jscomp$destructuring$var1;
          {
            const x = 0;
            var $jscomp$destructuring$var2 = (0, $jscomp.makeIterator)(bar());
            const c = $jscomp$destructuring$var2.next().value;
            A: B: for (; true; c++) {}
          }
        }
        """);
  }

  @Test
  public void testArrayDestructuring_inVanillaForInitializer_let() {

    test(
        "A: B: for (let z = 1, [a = 'default'] = foo(), x = 0, [c] = bar(); true; c++) { }",
        """
        A: B: for (let z = 1, a,
                   $jscomp$destructuring$var0$unused = ($jscomp$destructuring$var1 => {
                     var $jscomp$destructuring$var2 =
                         (0, $jscomp.makeIterator)($jscomp$destructuring$var1);
                     var $jscomp$destructuring$var3 =
                         $jscomp$destructuring$var2.next().value;
                     a = $jscomp$destructuring$var3 === void 0 ?
                         'default' :
                         $jscomp$destructuring$var3;
                     return $jscomp$destructuring$var1;
                   })(foo()),
                   x = 0, c,
                   $jscomp$destructuring$var4$unused = ($jscomp$destructuring$var5 => {
                     var $jscomp$destructuring$var6 =
                         (0, $jscomp.makeIterator)($jscomp$destructuring$var5);
                     c = $jscomp$destructuring$var6.next().value;
                     return $jscomp$destructuring$var5;
                   })(bar());
                   true; c++) {}
        """);
  }

  @Test
  public void testSimpleDestructuring_constAnnotationAdded() {

    test(
        "function foo(...{length}) {}",
        // pull destructuring out of params first.
        """
        function foo(...$jscomp$destructuring$var0) {
          var length;
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          length = $jscomp$destructuring$var1.length;
        }
        """);
    Node script = getLastCompiler().getJsRoot().getFirstChild();
    checkState(script.isScript());
    Node func = script.getFirstChild();
    checkState(func.isFunction());
    Node block = func.getLastChild();
    checkState(block.isBlock());
    // `var $jscomp$destructuring$var1 =  ...`
    Node var1 = block.getSecondChild().getFirstChild();
    checkState(var1.isName());
    assertThat(var1.getString()).isEqualTo("$jscomp$destructuring$var1");
    assertThat(var1.getBooleanProp(Node.IS_CONSTANT_NAME)).isTrue();
  }

  @Test
  public void testArrayDestructuringRest() {
    test(
        "let [one, ...others] = f();",
        """
        var $jscomp$destructuring$var0 = (0, $jscomp.makeIterator)(f());
        let one = $jscomp$destructuring$var0.next().value;
        let others = (0, $jscomp.arrayFromIterator)($jscomp$destructuring$var0);
        """);

    test(
        "function f([first, ...rest]) {}",
        """
        function f($jscomp$destructuring$var0) {
          var first; var rest;
          var $jscomp$destructuring$var1 = (0, $jscomp.makeIterator)($jscomp$destructuring$var0);
          first = $jscomp$destructuring$var1.next().value;
          rest = (0, $jscomp.arrayFromIterator)($jscomp$destructuring$var1);
        }
        """);
  }

  @Test
  public void testRestParamDestructuring() {
    test(
        "function f(first, ...[re, st, ...{length: num_left}]) {}",
"""
function f(first, ...$jscomp$destructuring$var0) {
  var re; var st; var num_left;
  var $jscomp$destructuring$var1 = (0, $jscomp.makeIterator)($jscomp$destructuring$var0);
  re = $jscomp$destructuring$var1.next().value;
  st = $jscomp$destructuring$var1.next().value;
  var $jscomp$destructuring$var2 = (0, $jscomp.arrayFromIterator)($jscomp$destructuring$var1);
  num_left = $jscomp$destructuring$var2.length;
}
""");
  }

  @Test
  public void testArrayDestructuringMixedRest() {
    test(
        srcs("let [first, ...[re, st, ...{length: num_left}]] = f();"),
        expected(
            """
            var $jscomp$destructuring$var0 = (0, $jscomp.makeIterator)(f());
            let first = $jscomp$destructuring$var0.next().value;
            var $jscomp$destructuring$var1 =
                (0, $jscomp.makeIterator)(
                    (0, $jscomp.arrayFromIterator)($jscomp$destructuring$var0));
            let re = $jscomp$destructuring$var1.next().value;
            let st = $jscomp$destructuring$var1.next().value;
            var $jscomp$destructuring$var2 =
                (0, $jscomp.arrayFromIterator)($jscomp$destructuring$var1);
            let num_left = $jscomp$destructuring$var2.length;
            """));
  }

  @Test
  public void testArrayDestructuringArguments() {
    test(
        "function f() { var [x, y] = arguments; }",
        """
        function f() {
          var x;
          var y;
          var $jscomp$destructuring$var0 = (0, $jscomp.makeIterator)(arguments);
          x = $jscomp$destructuring$var0.next().value;
          y = $jscomp$destructuring$var0.next().value;
        }
        """);
  }

  @Test
  public void testMixedDestructuring() {
    test(
        "var [a,{b,c}] = foo();",
        """
        var a;
        var b;
        var c;
        var $jscomp$destructuring$var0 = (0, $jscomp.makeIterator)(foo());
        a = $jscomp$destructuring$var0.next().value;
        var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.next().value;
        b = $jscomp$destructuring$var1.b;
        c = $jscomp$destructuring$var1.c
        """);

    test(
        "var {a,b:[c,d]} = foo();",
        """
        var a;
        var c;
        var d;
        var $jscomp$destructuring$var0 = foo();
        a = $jscomp$destructuring$var0.a;
        var $jscomp$destructuring$var1 = (0, $jscomp.makeIterator)($jscomp$destructuring$var0.b);
        c = $jscomp$destructuring$var1.next().value;
        d = $jscomp$destructuring$var1.next().value
        """);
  }

  @Test
  public void testDestructuringForOf() {
    test(
        "for ({x} of y) { console.log(x); }",
        """
        var $jscomp$destructuring$var0;
        for ($jscomp$destructuring$var0 of y) {
           var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
           x = $jscomp$destructuring$var1.x;
           console.log(x);
        }
        """);
  }

  @Test
  public void testDestructuringForOfWithShadowing() {
    test(
        srcs("for (const [x] of []) { const y = 0; }"),
        expected(
            """
            for (const $jscomp$destructuring$var0 of []) {
              var $jscomp$destructuring$var1 =
                  (0, $jscomp.makeIterator)($jscomp$destructuring$var0);
              const x = $jscomp$destructuring$var1.next().value;
              {
                const y = 0;
              }
            }
            """));
  }

  @Test
  public void testDestructuringForInWithShadowing() {
    test(
        srcs("for (const [x] in {}) { const y = 0; }"),
        expected(
            """
            for (const $jscomp$destructuring$var0 in {}) {
              var $jscomp$destructuring$var1 =
                  (0, $jscomp.makeIterator)($jscomp$destructuring$var0);
              const x = $jscomp$destructuring$var1.next().value;
              {
                const y = 0;
              }
            }
            """));
  }

  @Test
  public void testDefaultValueInObjectPattern() {
    test(
        "function f({x = a()}, y = b()) {}",
        """
        function f($jscomp$destructuring$var0, y) {
          var x;
        var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
        x = $jscomp$destructuring$var1.x === void 0
               ? a() : $jscomp$destructuring$var1.x;
        y = y === void 0 ? b() : y
        }
        """);
  }

  @Test
  public void testDefaultParameters() {
    enableTypeCheck();

    test(
        "function f(/** ? */ zero, /** ?= */ one = 1, /** ?= */ two = 2) {}; f(1); f(1,2,3);",
        """
        function f(zero, one, two) {
          one = (one === void 0) ? 1 : one;
          two = (two === void 0) ? 2 : two;
        };
        f(1); f(1,2,3);
        """);

    test(
        srcs("function f(/** ? */ zero, /** ?= */ one = 1, /** ?= */ two = 2) {}; f();"),
        expected(
            """
            function f(zero, one, two) {
              one = (one === void 0) ? 1 : one;
              two = (two === void 0) ? 2 : two;
            }; f();
            """),
        warning(TypeCheck.WRONG_ARGUMENT_COUNT));
  }

  @Test
  public void testDefaultAndRestParameters() {
    test(
        "function f(zero, one = 1, ...two) {}",
        """
        function f(zero, one, ...two) {
          one = (one === void 0) ? 1 : one;
        }
        """);

    test(
        "function f(/** number= */ x = 5) {}",
        """
        function f(x) {
          x = (x === void 0) ? 5 : x;
        }
        """);
  }

  @Test
  public void testDefaultUndefinedParameters() {
    enableTypeCheck();

    test("function f(zero, one=undefined) {}", "function f(zero, one) {}");

    test("function f(zero, one=void 42) {}", "function f(zero, one) {}");

    test("function f(zero, one=void(42)) {}", "function f(zero, one) {}");

    test("function f(zero, one=void '\\x42') {}", "function f(zero, one) {}");

    test(
        "function f(zero, one='undefined') {}",
        "function f(zero, one) {   one = (one === void 0) ? 'undefined' : one; }");

    test(
        "function f(zero, one=void g()) {}",
        "function f(zero, one) {   one = (one === void 0) ? void g() : one; }");
  }

  @Test
  public void testCatch() {
    test(
        "try {} catch ({message}) {}",
        """
        try {} catch($jscomp$destructuring$var0) {
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          let message = $jscomp$destructuring$var1.message
        }
        """);
  }

  @Test
  public void testTypeCheck() {
    enableTypeCheck();

    test(
        "/** @param {{x: number}} obj */ function f({x}) {}",
        """
        function f($jscomp$destructuring$var0) {
          var x;
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          x = $jscomp$destructuring$var1.x;
        }
        """);

    test(
        srcs(
            """
            /** @param {{x: number}} obj */
            function f({x}) {}
            f({ x: 'str'});
            """),
        warning(TypeValidator.TYPE_MISMATCH_WARNING));

    test(
        """
        /** @param {{x: number}} obj */
        var f = function({x}) {}
        """,
        """
        var f = function($jscomp$destructuring$var0) {
          var x;
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          x = $jscomp$destructuring$var1.x;
        }
        """);

    test(
        """
        /** @param {{x: number}} obj */
        f = function({x}) {}
        """,
        """
        f = function($jscomp$destructuring$var0) {
          var x;
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          x = $jscomp$destructuring$var1.x;
        }
        """);

    test(
        """
        /** @param {{x: number}} obj */
        ns.f = function({x}) {}
        """,
        """
        ns.f = function($jscomp$destructuring$var0) {
          var x;
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          x = $jscomp$destructuring$var1.x;
        }
        """);

    test(
        "ns.f = function({x} = {x: 0}) {};",
        """
        ns.f = function($jscomp$destructuring$var0) {
          var x;
          var $jscomp$destructuring$var1 =
              $jscomp$destructuring$var0 === void 0 ? {x:0} : $jscomp$destructuring$var0;
          x = $jscomp$destructuring$var1.x
        };
        """);

    test(
        """
        /** @param {{x: number}=} obj */
        ns.f = function({x} = {x: 0}) {};
        """,
        """
        ns.f = function($jscomp$destructuring$var0) {
          var x;
          var $jscomp$destructuring$var1 =
              $jscomp$destructuring$var0=== void 0 ? {x:0} : $jscomp$destructuring$var0;
          x = $jscomp$destructuring$var1.x
        };
        """);
  }

  @Test
  public void testDestructuringPatternInExterns() {
    enableTypeCheck();
    allowExternsChanges();

    testSame(
        externs(
            EXTERNS_BASE
                + """
                /** @constructor */
                function Foo() {}

                Foo.prototype.bar = function({a}) {};
                """),
        srcs("(new Foo).bar({b: 0});"));
    // TODO(sdh): figure out what's going on here
  }

  @Test
  public void testTypeCheck_inlineAnnotations() {
    enableTypeCheck();

    test(
        "function f(/** {x: number} */ {x}) {}",
        """
        function f($jscomp$destructuring$var0) {
          var x;
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          x = $jscomp$destructuring$var1.x;
        }
        """);

    test(
        srcs(
            """
            function f(/** {x: number} */ {x}) {}
            f({ x: 'str'});
            """),
        warning(TypeValidator.TYPE_MISMATCH_WARNING));
  }

  @Test
  public void testDestructuringArrayNotInExprResult() {
    test(
        srcs(
            """
            var x, a, b;
            x = ([a,b] = [1,2])
            """),
        expected(
            """
            var x;
            var a;
            var b;
            x = ($jscomp$destructuring$var0 => {
              var $jscomp$destructuring$var1 =
                  (0, $jscomp.makeIterator)($jscomp$destructuring$var0);
              a = $jscomp$destructuring$var1.next().value;
              b = $jscomp$destructuring$var1.next().value;
              return $jscomp$destructuring$var0;
            })([1, 2]);
            """));

    test(
        srcs(
            """
            var foo = function () {
            var x, a, b;
            x = ([a,b] = [1,2]);
            }
            foo();
            """),
        expected(
            """
            var foo = function() {
              var x;
              var a;
              var b;
              x = ($jscomp$destructuring$var0 => {
                var $jscomp$destructuring$var1 =
                    (0, $jscomp.makeIterator)($jscomp$destructuring$var0);
                a = $jscomp$destructuring$var1.next().value;
                b = $jscomp$destructuring$var1.next().value;
                return $jscomp$destructuring$var0;
              })([1, 2]);
            };
            foo();
            """));

    test(
        srcs(
            """
            var prefix;
            for (;;[, prefix] = /** @type {!Array<string>} */ (/\\.?([^.]+)$/.exec(prefix))){
            }
            """),
        expected(
            """
            var prefix;
            for (;; ($jscomp$destructuring$var0 => {
                  var $jscomp$destructuring$var1 =
                      (0, $jscomp.makeIterator)($jscomp$destructuring$var0);
                  $jscomp$destructuring$var1.next();
                  prefix = $jscomp$destructuring$var1.next().value;
                  return $jscomp$destructuring$var0;
                })(/\\.?([^.]+)$/.exec(prefix))) {
            }
            """));

    test(
        srcs(
            """
            var prefix;
            for (;;[, prefix] = /** @type {!Array<string>} */ (/\\.?([^.]+)$/.exec(prefix))){
               console.log(prefix);
            }
            """),
        expected(
            """
            var prefix;
            for (;; ($jscomp$destructuring$var0 => {
                  var $jscomp$destructuring$var1 =
                      (0, $jscomp.makeIterator)($jscomp$destructuring$var0);
                  $jscomp$destructuring$var1.next();
                  prefix = $jscomp$destructuring$var1.next().value;
                  return $jscomp$destructuring$var0;
                })(/\\.?([^.]+)$/.exec(prefix))) {
              console.log(prefix);
            }
            """));

    test(
        srcs(
            """
            var x = 1;
            for (; x < 3; [x,] = [3,4]){
               console.log(x);
            }
            """),
        expected(
            """
            var x = 1;
            for (; x < 3; (($jscomp$destructuring$var0) => {
               var $jscomp$destructuring$var1 =
                   (0, $jscomp.makeIterator)($jscomp$destructuring$var0);
               x = $jscomp$destructuring$var1.next().value;
               return $jscomp$destructuring$var0;
             })([3, 4])){
            console.log(x);
            }
            """));
  }

  @Test
  public void testDestructuringObjectNotInExprResult() {
    test(
        "var x = ({a: b, c: d} = foo());",
        """
        var x = ($jscomp$destructuring$var0 => {
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          b = $jscomp$destructuring$var1.a;
          d = $jscomp$destructuring$var1.c;
          return $jscomp$destructuring$var0;
        })(foo());
        """);

    test(
        "var x = ({a: b, c: d} = foo());",
        """
        var x = ($jscomp$destructuring$var0 => {
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          b = $jscomp$destructuring$var1.a;
          d = $jscomp$destructuring$var1.c;
          return $jscomp$destructuring$var0;
        })(foo());
        """);

    test(
        "var x; var y = ({a: x} = foo());",
        """
        var x;
        var y = ($jscomp$destructuring$var0 => {
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          x = $jscomp$destructuring$var1.a;
          return $jscomp$destructuring$var0;
        })(foo());
        """);

    test(
        "var x; var y = (() => {return {a,b} = foo();})();",
        """
        var x;
        var y = (() => {
          return ($jscomp$destructuring$var0 => {
            var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
            a = $jscomp$destructuring$var1.a;
            b = $jscomp$destructuring$var1.b;
            return $jscomp$destructuring$var0;
          })(foo());
        })();
        """);
  }

  @Test
  public void testNestedDestructuring() {
    test(
        srcs("var [[x]] = [[1]];"),
        expected(
            """
            var x;
            var $jscomp$destructuring$var0 = (0, $jscomp.makeIterator)([[1]]);
            var $jscomp$destructuring$var1 =
            (0, $jscomp.makeIterator)($jscomp$destructuring$var0.next().value);
            x = $jscomp$destructuring$var1.next().value;
            """));

    test(
        srcs("var [[x,y],[z]] = [[1,2],[3]];"),
        expected(
            """
            var x;
            var y;
            var z;
            var $jscomp$destructuring$var0 = (0, $jscomp.makeIterator)([[1,2],[3]]);
            var $jscomp$destructuring$var1 =
            (0, $jscomp.makeIterator)($jscomp$destructuring$var0.next().value);
            x = $jscomp$destructuring$var1.next().value;
            y = $jscomp$destructuring$var1.next().value;
            var $jscomp$destructuring$var2 =
            (0, $jscomp.makeIterator)($jscomp$destructuring$var0.next().value);
            z = $jscomp$destructuring$var2.next().value;
            """));

    test(
        srcs("var [[x,y],z] = [[1,2],3];"),
        expected(
            """
            var x;
            var y;
            var z;
            var $jscomp$destructuring$var0 = (0, $jscomp.makeIterator)([[1,2],3]);
            var $jscomp$destructuring$var1 =
            (0, $jscomp.makeIterator)($jscomp$destructuring$var0.next().value);
            x = $jscomp$destructuring$var1.next().value;
            y = $jscomp$destructuring$var1.next().value;
            z = $jscomp$destructuring$var0.next().value;
            """));
  }

  @Test
  public void testTryCatch() {
    test(
        """
        var a = 1;
        try {
          throw [];
        } catch ([x]) {}
        """,
        """
        var a = 1;
        try {
          throw [];
        } catch ($jscomp$destructuring$var0) {
           var $jscomp$destructuring$var1 = (0, $jscomp.makeIterator)($jscomp$destructuring$var0);
           let x = $jscomp$destructuring$var1.next().value;
        }
        """);

    test(
        """
        var a = 1;
        try {
          throw [[]];
        } catch ([[x]]) {}
        """,
        """
        var a = 1;
        try {
          throw [[]];
        } catch ($jscomp$destructuring$var0) {
           var $jscomp$destructuring$var1 = (0, $jscomp.makeIterator)($jscomp$destructuring$var0);
           var $jscomp$destructuring$var2 =
        (0, $jscomp.makeIterator)($jscomp$destructuring$var1.next().value);
           let x = $jscomp$destructuring$var2.next().value;
        }
        """);
  }

  @Test
  public void testObjectPatternWithRestDecl() {
    test(
        "var {a: b, c: d, ...rest} = foo();",
        """
        var b; var d; var rest
        var $jscomp$destructuring$var0 = foo();
        var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);
        b = $jscomp$destructuring$var0.a;
        d = $jscomp$destructuring$var0.c;
        rest = (delete $jscomp$destructuring$var1.a,
                    delete $jscomp$destructuring$var1.c,
                    $jscomp$destructuring$var1);
        """);

    test(
        "const {a: b, c: d, ...rest} = foo();",
        """
        /** @const */ var $jscomp$destructuring$var0 = foo();
        var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);
        const b = $jscomp$destructuring$var0.a;
        const d = $jscomp$destructuring$var0.c;
        const rest = (delete $jscomp$destructuring$var1.a,
                      delete $jscomp$destructuring$var1.c,
                      $jscomp$destructuring$var1);
        """);

    test(
        "let {a: b, c: d, ...rest} = foo();",
        """
        var $jscomp$destructuring$var0 = foo();
        var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);
        let b = $jscomp$destructuring$var0.a;
        let d = $jscomp$destructuring$var0.c;
        let rest = (delete $jscomp$destructuring$var1.a,
                    delete $jscomp$destructuring$var1.c,
                    $jscomp$destructuring$var1);
        """);

    test(
        "var pre = foo(); var {a: b, c: d, ...rest} = foo();",
        """
        var pre = foo();
        var b; var d; var rest;
        var $jscomp$destructuring$var0 = foo();
        var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);
        b = $jscomp$destructuring$var0.a;
        d = $jscomp$destructuring$var0.c;
        rest = (delete $jscomp$destructuring$var1.a,
                    delete $jscomp$destructuring$var1.c,
                    $jscomp$destructuring$var1);
        """);

    test(
        "var {a: b, c: d, ...rest} = foo(); var post = foo();",
        """
        var b; var d; var rest;
        var $jscomp$destructuring$var0 = foo();
        var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);
        b = $jscomp$destructuring$var0.a;
        d = $jscomp$destructuring$var0.c;
        rest = (delete $jscomp$destructuring$var1.a,
                    delete $jscomp$destructuring$var1.c,
                    $jscomp$destructuring$var1);
        var post = foo();
        """);

    test(
        "var pre = foo(); var {a: b, c: d, ...rest} = foo(); var post = foo();",
        """
        var pre = foo();
        var b; var d; var rest;
        var $jscomp$destructuring$var0 = foo();
        var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);
        b = $jscomp$destructuring$var0.a;
        d = $jscomp$destructuring$var0.c;
        rest = (delete $jscomp$destructuring$var1.a,
                    delete $jscomp$destructuring$var1.c,
                    $jscomp$destructuring$var1);
        var post = foo();
        """);

    test(
        "var {a: b1, c: d1, ...rest1} = foo(); var {a: b2, c: d2, ...rest2} = foo();",
        """
        var b1; var d1; var rest1;
        var $jscomp$destructuring$var0 = foo();
        var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);
        b1 = $jscomp$destructuring$var0.a;
        d1 = $jscomp$destructuring$var0.c;
        rest1 = (delete $jscomp$destructuring$var1.a,
                     delete $jscomp$destructuring$var1.c,
                     $jscomp$destructuring$var1);
        var b2; var d2; var rest2;
        var $jscomp$destructuring$var2 = foo();
        var $jscomp$destructuring$var3 = Object.assign({}, $jscomp$destructuring$var2);
        b2 = $jscomp$destructuring$var2.a;
        d2 = $jscomp$destructuring$var2.c;
        rest2 = (delete $jscomp$destructuring$var3.a,
                     delete $jscomp$destructuring$var3.c,
                     $jscomp$destructuring$var3);
        """);

    test(
        "var {...rest} = foo();",
        """
        var rest;
        var $jscomp$destructuring$var0 = foo();
        var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);
        rest = ($jscomp$destructuring$var1);
        """);

    test(
        "const {...rest} = foo();",
        """
        /** @const */ var $jscomp$destructuring$var0 = foo();
        var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);
        const rest = ($jscomp$destructuring$var1);
        """);
  }

  @Test
  public void testObjectPatternWithRestAssignStatement() {
    test(
        "var b,d,rest; ({a: b, c: d, ...rest} = foo());",
        """
        var b;
        var d;
        var rest;
        var $jscomp$destructuring$var0 = foo();
        var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);
        b = $jscomp$destructuring$var0.a;
        d = $jscomp$destructuring$var0.c;
        rest = (delete $jscomp$destructuring$var1.a,
                    delete $jscomp$destructuring$var1.c,
                    $jscomp$destructuring$var1);
        """);

    test(
        "var b,d,rest,pre; pre = foo(), {a: b, c: d, ...rest} = foo();",
        """
        var b;
        var d;
        var rest;
        var pre;
        pre = foo(), ($jscomp$destructuring$var0 => {
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          var $jscomp$destructuring$var2 =
              Object.assign({}, $jscomp$destructuring$var1);
          b = $jscomp$destructuring$var1.a;
          d = $jscomp$destructuring$var1.c;
          rest =
              (delete $jscomp$destructuring$var2.a, delete $jscomp$destructuring$var2.c,
               $jscomp$destructuring$var2);
          return $jscomp$destructuring$var0;
        })(foo());
        """);

    test(
        "var b,d,rest,post; ({a: b, c: d, ...rest} = foo()), post = foo();",
        """
        var b;
        var d;
        var rest;
        var post;
        ($jscomp$destructuring$var0 => {
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          var $jscomp$destructuring$var2 =
              Object.assign({}, $jscomp$destructuring$var1);
          b = $jscomp$destructuring$var1.a;
          d = $jscomp$destructuring$var1.c;
          rest =
              (delete $jscomp$destructuring$var2.a, delete $jscomp$destructuring$var2.c,
               $jscomp$destructuring$var2);
          return $jscomp$destructuring$var0;
        })(foo()),
            post = foo();
        """);

    test(
        "var b,d,rest,pre,post; pre = foo(), {a: b, c: d, ...rest} = foo(), post = foo();",
        """
        var b;
        var d;
        var rest;
        var pre;
        var post;
        pre = foo(), ($jscomp$destructuring$var0 => {
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          var $jscomp$destructuring$var2 =
              Object.assign({}, $jscomp$destructuring$var1);
          b = $jscomp$destructuring$var1.a;
          d = $jscomp$destructuring$var1.c;
          rest =
              (delete $jscomp$destructuring$var2.a, delete $jscomp$destructuring$var2.c,
               $jscomp$destructuring$var2);
          return $jscomp$destructuring$var0;
        })(foo()),
            post = foo();
        """);

    test(
        """
        var b1,d1,rest1,b2,d2,rest2;
        ({a: b1, c: d1, ...rest1} = foo(),
         {a: b2, c: d2, ...rest2} = foo());
        """,
        """
        var b1;
        var d1;
        var rest1;
        var b2;
        var d2;
        var rest2;
        ($jscomp$destructuring$var0 => {
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          var $jscomp$destructuring$var2 =
              Object.assign({}, $jscomp$destructuring$var1);
          b1 = $jscomp$destructuring$var1.a;
          d1 = $jscomp$destructuring$var1.c;
          rest1 =
              (delete $jscomp$destructuring$var2.a, delete $jscomp$destructuring$var2.c,
               $jscomp$destructuring$var2);
          return $jscomp$destructuring$var0;
        })(foo()),
            ($jscomp$destructuring$var3 => {
              var $jscomp$destructuring$var4 = $jscomp$destructuring$var3;
              var $jscomp$destructuring$var5 =
                  Object.assign({}, $jscomp$destructuring$var4);
              b2 = $jscomp$destructuring$var4.a;
              d2 = $jscomp$destructuring$var4.c;
              rest2 =
                  (delete $jscomp$destructuring$var5.a,
                   delete $jscomp$destructuring$var5.c, $jscomp$destructuring$var5);
              return $jscomp$destructuring$var3;
            })(foo());
        """);
  }

  @Test
  public void testObjectPatternWithRestAssignStatement_assignedToProperty() {
    test(
        "var a, obj = {}; ({a, ...obj.rest} = foo());",
        """
        var a;
        var obj = {};
        var $jscomp$destructuring$var0 = foo();
        var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);
        a = $jscomp$destructuring$var0.a;
        obj.rest = (delete $jscomp$destructuring$var1.a, $jscomp$destructuring$var1);
        """);
  }

  @Test
  public void testObjectPatternInAssignExpression_inGeneratorUsingYield() {
    // Test usage of yield in the RHS of the assign. We need to make it an argument to the arrow
    // function, as we cannot reference 'yield' within the arrow function body itself.
    test(
        """
        function *g(o) {
          return yield zzz(), ({[a]: o.a} = foo(yield b(), yield c())), yield zzz();
        }
        """,
        """
        function* g(o) {
          return yield zzz(), ($jscomp$destructuring$var0 => {
            var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
            o.a = $jscomp$destructuring$var1[a];
            return $jscomp$destructuring$var0;
          })(foo(yield b(), yield c())), yield zzz();
        }
        """);
    // A yield in the LHS is explicitly unsupported, for now. We could also provide it as an
    // argument, but it's more complicated than just providing the entire RHS, and we haven't seen
    // any actual instances of someone trying to put yield here.
    // Additionally, the compiler actually refuses to parse yield in a destructuring pattern default
    // value, e.g. `function *g() { const {x = yield 0} = {}; }`. The edge case of someone
    // using yield in either a computed key, or as the assignment target, seems very very unlikely.
    var ex =
        assertThrows(
            RuntimeException.class,
            () ->
                test(
                    """
                    function *g(o) {
                      return ({[yield 0]: o[yield a]} = foo());
                    }
                    """,
                    """
                    """));
    assertThat(ex)
        .hasMessageThat()
        .contains(
            "Cannot transpile yet: destructuring assignment referencing await or yield in lhs");
  }

  @Test
  public void testDestructuringAssign_notInExprResult_cannotDecomposeToStatement() {
    // Test a case where we cannot trivially extract the destructuring expression to a statement.
    // Instead we wrap it in an arrow function.
    test(
        """
        for (let
          a = 0,
          b = ([z] = y);;
        ) {}
        """,
        """
        for (let a = 0, b = ($jscomp$destructuring$var0 => {
                          var $jscomp$destructuring$var1 =
                              (0, $jscomp.makeIterator)($jscomp$destructuring$var0);
                          z = $jscomp$destructuring$var1.next().value;
                          return $jscomp$destructuring$var0;
                        })(y);
             ;) {
        }
        """);

    test(
        """
        function *gen() {
          for (let
            a = 0,
            b = ([z] = yield y);;
          ) {}
        }
        """,
        """
        function* gen() {
          for (let a = 0, b = ($jscomp$destructuring$var0 => {
                            var $jscomp$destructuring$var1 =
                                (0, $jscomp.makeIterator)($jscomp$destructuring$var0);
                            z = $jscomp$destructuring$var1.next().value;
                            return $jscomp$destructuring$var0;
                          })(yield y);
               ;) {
          }
        }
        """);

    test(
        """
        async function f() {
          for (let a = 0, b = ([z] = await y());;) {}
        }
        """,
        """
        async function f() {
          for (let a = 0, b = ($jscomp$destructuring$var0 => {
                            var $jscomp$destructuring$var1 =
                                (0, $jscomp.makeIterator)($jscomp$destructuring$var0);
                            z = $jscomp$destructuring$var1.next().value;
                            return $jscomp$destructuring$var0;
                          })(await y());
               ;) {
          }
        }
        """);
  }

  @Test
  public void testObjectPatternWithRestAssignExpr() {
    test(
        "var x,b,d,rest; x = ({a: b, c: d, ...rest} = foo());",
        """
        var x;
        var b;
        var d;
        var rest;
        x = ($jscomp$destructuring$var0 => {
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          var $jscomp$destructuring$var2 =
              Object.assign({}, $jscomp$destructuring$var1);
          b = $jscomp$destructuring$var1.a;
          d = $jscomp$destructuring$var1.c;
          rest =
              (delete $jscomp$destructuring$var2.a, delete $jscomp$destructuring$var2.c,
               $jscomp$destructuring$var2);
          return $jscomp$destructuring$var0;
        })(foo());
        """);

    test(
        "var x,b,d,rest; baz({a: b, c: d, ...rest} = foo());",
        """
        var x;
        var b;
        var d;
        var rest;
        baz(($jscomp$destructuring$var0 => {
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          var $jscomp$destructuring$var2 =
              Object.assign({}, $jscomp$destructuring$var1);
          b = $jscomp$destructuring$var1.a;
          d = $jscomp$destructuring$var1.c;
          rest =
              (delete $jscomp$destructuring$var2.a, delete $jscomp$destructuring$var2.c,
               $jscomp$destructuring$var2);
          return $jscomp$destructuring$var0;
        })(foo()));
        """);
  }

  @Test
  public void testObjectPatternWithRestForOf() {
    test(
        "for ({a: b, c: d, ...rest} of foo()) { console.log(rest.z); }",
        """
        var $jscomp$destructuring$var0;
        for ($jscomp$destructuring$var0 of foo()) {
            var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
            var $jscomp$destructuring$var2 = Object.assign({}, $jscomp$destructuring$var1);
            b = $jscomp$destructuring$var1.a;
            d = $jscomp$destructuring$var1.c;
            rest = (delete $jscomp$destructuring$var2.a,
                    delete $jscomp$destructuring$var2.c,
                    $jscomp$destructuring$var2);
            console.log(rest.z);
        }
        """);

    test(
        "for (var {a: b, c: d, ...rest} of foo()) { console.log(rest.z); }",
        """
        var b;
        var d;
        var rest;
        var $jscomp$destructuring$var0;
        for ($jscomp$destructuring$var0 of foo()) {
            var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
            var $jscomp$destructuring$var2 = Object.assign({}, $jscomp$destructuring$var1);
            b = $jscomp$destructuring$var1.a;
            d = $jscomp$destructuring$var1.c;
            rest = (delete $jscomp$destructuring$var2.a,
                    delete $jscomp$destructuring$var2.c,
                    $jscomp$destructuring$var2);
            console.log(rest.z);
        }
        """);

    test(
        "for (let {a: b, c: d, ...rest} of foo()) { console.log(rest.z); }",
        """
        for (let $jscomp$destructuring$var0 of foo()) {
            var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
            var $jscomp$destructuring$var2 = Object.assign({}, $jscomp$destructuring$var1);
            let b = $jscomp$destructuring$var1.a;
            let d = $jscomp$destructuring$var1.c;
            let rest = (delete $jscomp$destructuring$var2.a,
                    delete $jscomp$destructuring$var2.c,
                    $jscomp$destructuring$var2);
            {
              console.log(rest.z);
            }
        }
        """);

    test(
        "for (const {a: b, c: d, ...rest} of foo()) { console.log(rest.z); }",
        """
        for (const $jscomp$destructuring$var0 of foo()) {
            /** @const */ var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
            var $jscomp$destructuring$var2 = Object.assign({}, $jscomp$destructuring$var1);
            const b = $jscomp$destructuring$var1.a;
            const d = $jscomp$destructuring$var1.c;
            const rest = (delete $jscomp$destructuring$var2.a,
                    delete $jscomp$destructuring$var2.c,
                    $jscomp$destructuring$var2);
            {
              console.log(rest.z);
            }
        }
        """);

    test(
        "for (var {a: b, [baz()]: d, ...rest} of foo()) { console.log(rest.z); }",
        """
        var b;
        var d;
        var rest;
        var $jscomp$destructuring$var0;
        for ($jscomp$destructuring$var0 of foo()) {
            var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
            var $jscomp$destructuring$var2 = Object.assign({}, $jscomp$destructuring$var1);
            b = $jscomp$destructuring$var1.a;
            var $jscomp$destructuring$var3 = baz();
            d = $jscomp$destructuring$var1[$jscomp$destructuring$var3];
            rest = (delete $jscomp$destructuring$var2.a,
                    delete $jscomp$destructuring$var2[$jscomp$destructuring$var3],
                    $jscomp$destructuring$var2);
            console.log(rest.z);
        }
        """);

    test(
        "for (var {a: b, [baz()]: d = 1, ...rest} of foo()) { console.log(rest.z); }",
        """
        var b;
        var d;
        var rest;
        var $jscomp$destructuring$var0;
        for ($jscomp$destructuring$var0 of foo()) {
            var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
            var $jscomp$destructuring$var2 = Object.assign({}, $jscomp$destructuring$var1);
            b = $jscomp$destructuring$var1.a;
            var $jscomp$destructuring$var3 = baz();
            var $jscomp$destructuring$var4 =
                $jscomp$destructuring$var1[$jscomp$destructuring$var3];
            d = $jscomp$destructuring$var4=== void 0 ? 1 : $jscomp$destructuring$var4;
            rest = (delete $jscomp$destructuring$var2.a,
                    delete $jscomp$destructuring$var2[$jscomp$destructuring$var3],
                    $jscomp$destructuring$var2);
            console.log(rest.z);
        }
        """);
  }

  @Test
  public void testObjectPatternWithRestAndComputedPropertyName() {
    test(
        "var {a: b = 3, [bar()]: d, [baz()]: e, ...rest} = foo();",
        """
        var b; var d; var e; var rest;
        var $jscomp$destructuring$var0 = foo();
        var $jscomp$destructuring$var1 = Object.assign({},$jscomp$destructuring$var0);
        b = $jscomp$destructuring$var0.a=== void 0 ? 3 : $jscomp$destructuring$var0.a;
        var $jscomp$destructuring$var2 = bar();
        d = $jscomp$destructuring$var0[$jscomp$destructuring$var2];
        var $jscomp$destructuring$var3 = baz();
        e = $jscomp$destructuring$var0[$jscomp$destructuring$var3];
        rest = (delete $jscomp$destructuring$var1.a,
                    delete $jscomp$destructuring$var1[$jscomp$destructuring$var2],
                    delete $jscomp$destructuring$var1[$jscomp$destructuring$var3],
                    $jscomp$destructuring$var1);
        """);
  }

  @Test
  public void testObjectPatternWithRestAndDefaults() {
    test(
        "var {a = 3, ...rest} = foo();",
        """
        var a; var rest;
        var $jscomp$destructuring$var0 = foo();
        var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);
        a = $jscomp$destructuring$var0.a=== void 0 ? 3 : $jscomp$destructuring$var0.a;
        rest = (delete $jscomp$destructuring$var1.a,
                    $jscomp$destructuring$var1);
        """);

    test(
        "var {[bar()]:a = 3, 'b c':b = 12, ...rest} = foo();",
        """
        var a; var b; var rest;
        var $jscomp$destructuring$var0=foo();
        var $jscomp$destructuring$var1 = Object.assign({},$jscomp$destructuring$var0);
        var $jscomp$destructuring$var2 = bar();
        var $jscomp$destructuring$var3 =
            $jscomp$destructuring$var0[$jscomp$destructuring$var2];
        a = $jscomp$destructuring$var3=== void 0 ? 3 : $jscomp$destructuring$var3;
        b = $jscomp$destructuring$var0["b c"]=== void 0
            ? 12 : $jscomp$destructuring$var0["b c"];
        rest=(delete $jscomp$destructuring$var1[$jscomp$destructuring$var2],
                  delete $jscomp$destructuring$var1["b c"],
                  $jscomp$destructuring$var1);
        """);
  }

  @Test
  public void testObjectPatternWithRestInCatch() {
    test(
        "try {} catch ({first, second, ...rest}) { console.log(rest.z); }",
        """
        try {}
        catch ($jscomp$destructuring$var0) {
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          var $jscomp$destructuring$var2 = Object.assign({}, $jscomp$destructuring$var1);
          let first = $jscomp$destructuring$var1.first;
          let second = $jscomp$destructuring$var1.second;
          let rest = (delete $jscomp$destructuring$var2.first,
                      delete $jscomp$destructuring$var2.second,
                      $jscomp$destructuring$var2);
          console.log(rest.z);
        }
        """);
  }

  @Test
  public void testObjectPatternWithRestAssignReturn() {
    test(
        "function f() { return {x:a, ...rest} = foo(); }",
        """
        function f() {
          return ($jscomp$destructuring$var0 => {
            var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
            var $jscomp$destructuring$var2 =
                Object.assign({}, $jscomp$destructuring$var1);
            a = $jscomp$destructuring$var1.x;
            rest = (delete $jscomp$destructuring$var2.x, $jscomp$destructuring$var2);
            return $jscomp$destructuring$var0;
          })(foo());
        }
        """);
  }

  @Test
  public void testObjectPatternWithRestParamList() {
    test(
        "function f({x = a(), ...rest}, y=b()) { console.log(y); }",
        """
        function f($jscomp$destructuring$var0,y) {
          var x; var rest;
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          var $jscomp$destructuring$var2 = Object.assign({},$jscomp$destructuring$var1);
          x = $jscomp$destructuring$var1.x === void 0
              ? a() : $jscomp$destructuring$var1.x;
          rest= (delete $jscomp$destructuring$var2.x,
                     $jscomp$destructuring$var2);
          y = y=== void 0 ? b() : y;
          console.log(y)
        }
        """);

    test(
        "function f({x = a(), ...rest}={}, y=b()) { console.log(y); }",
        """
        function f($jscomp$destructuring$var0,y) {
          var x; var rest;
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0=== void 0
              ? {} : $jscomp$destructuring$var0;
          var $jscomp$destructuring$var2 = Object.assign({},$jscomp$destructuring$var1);
          x = $jscomp$destructuring$var1.x=== void 0
              ? a() : $jscomp$destructuring$var1.x;
          rest= (delete $jscomp$destructuring$var2.x,
                     $jscomp$destructuring$var2);
          y = y=== void 0 ? b() : y;
          console.log(y)
        }
        """);
  }

  @Test
  public void testObjectPatternWithRestArrowParamList() {
    test(
        "var f = ({x = a(), ...rest}, y=b()) => { console.log(y); };",
        """
        var f = ($jscomp$destructuring$var0,y) => {
          var x; var rest;
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0;
          var $jscomp$destructuring$var2 = Object.assign({},$jscomp$destructuring$var1);
          x = $jscomp$destructuring$var1.x=== void 0
              ? a() : $jscomp$destructuring$var1.x;
          rest = (delete $jscomp$destructuring$var2.x,
                      $jscomp$destructuring$var2);
          y = y=== void 0 ? b() : y;
          console.log(y)
        }
        """);

    test(
        "var f = ({x = a(), ...rest}={}, y=b()) => { console.log(y); };",
        """
        var f = ($jscomp$destructuring$var0,y) => {
          var x; var rest;
          var $jscomp$destructuring$var1 = $jscomp$destructuring$var0=== void 0
              ? {} : $jscomp$destructuring$var0;
          var $jscomp$destructuring$var2 = Object.assign({},$jscomp$destructuring$var1);
          x = $jscomp$destructuring$var1.x=== void 0
              ? a() : $jscomp$destructuring$var1.x;
          rest= (delete $jscomp$destructuring$var2.x,
                     $jscomp$destructuring$var2);
          y = y=== void 0 ? b() : y;
          console.log(y)
        }
        """);
  }

  @Test
  public void testAllRewriteMode() {
    this.destructuringRewriteMode = ObjectDestructuringRewriteMode.REWRITE_ALL_OBJECT_PATTERNS;

    test(
        "var {a} = foo();",
        """
        var a; var $jscomp$destructuring$var0 = foo();
        a = $jscomp$destructuring$var0.a;
        """);

    test(
        "var {a} = foo(); var {...b} = bar();",
        """
        var a;
        var $jscomp$destructuring$var0 = foo();
        a = $jscomp$destructuring$var0.a;
        var b;
        var $jscomp$destructuring$var1 = bar();
        var $jscomp$destructuring$var2 = Object.assign({}, $jscomp$destructuring$var1);
        b = $jscomp$destructuring$var2;
        """);

    test(
        "var {[foo0()]: {[foo1()]: a, ...r}, [foo2()]: { [foo3()]: b}, [foo4()]: c } = bar();",
        """
        var a; var r; var b; var c;
        var $jscomp$destructuring$var0 = bar();
        var $jscomp$destructuring$var1 = $jscomp$destructuring$var0[foo0()];
        var $jscomp$destructuring$var2 = Object.assign({},$jscomp$destructuring$var1);
        var $jscomp$destructuring$var3 = foo1();
        a = $jscomp$destructuring$var1[$jscomp$destructuring$var3];
        r = (delete $jscomp$destructuring$var2[$jscomp$destructuring$var3],
                 $jscomp$destructuring$var2);
        var $jscomp$destructuring$var4 = $jscomp$destructuring$var0[foo2()];
        b = $jscomp$destructuring$var4[foo3()];
        c = $jscomp$destructuring$var0[foo4()]
        """);

    test(
        "var [a] = foo(); var [{b}] = foo(); var [{...c}] = foo();",
        """
        var a;
        var $jscomp$destructuring$var0 = (0, $jscomp.makeIterator)(foo());
        a = $jscomp$destructuring$var0.next().value;
        // var [{b}] = foo();
        var b;
        var $jscomp$destructuring$var1 = (0, $jscomp.makeIterator)(foo());
        var $jscomp$destructuring$var2 = $jscomp$destructuring$var1.next().value;
        b = $jscomp$destructuring$var2.b;
        // var [{...c}] = foo();
        var c;
        var $jscomp$destructuring$var3 = (0, $jscomp.makeIterator)(foo());
        var $jscomp$destructuring$var4 = $jscomp$destructuring$var3.next().value;
        var $jscomp$destructuring$var5 = Object.assign({},$jscomp$destructuring$var4);
        c = $jscomp$destructuring$var5
        """);
  }

  @Test
  public void testOnlyRestRewriteMode() {
    this.destructuringRewriteMode = ObjectDestructuringRewriteMode.REWRITE_OBJECT_REST;

    test("var {a} = foo();", "var a; ({a} = foo());");

    test(
        "var {a} = foo(); var {...b} = bar();",
        """
        var a; ({a} = foo());
        var b;
        var $jscomp$destructuring$var0 = bar();
        var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);
        b = $jscomp$destructuring$var1;
        """);

    // test that object patterns are rewritten if they have a rest property nested within
    test(
        "var {[foo0()]: {[foo1()]: a, ...r}, [foo2()]: { [foo3()]: b}, [foo4()]: c } = bar();",
        """
        var a; var r; var b; var c;
        var $jscomp$destructuring$var0 = bar();
        var $jscomp$destructuring$var1 = $jscomp$destructuring$var0[foo0()];
        var $jscomp$destructuring$var2 = Object.assign({},$jscomp$destructuring$var1);
        var $jscomp$destructuring$var3 = foo1();
        a = $jscomp$destructuring$var1[$jscomp$destructuring$var3];
        r = (delete $jscomp$destructuring$var2[$jscomp$destructuring$var3],
                 $jscomp$destructuring$var2);
        var $jscomp$destructuring$var4 = $jscomp$destructuring$var0[foo2()];
        b = $jscomp$destructuring$var4[foo3()];
        c = $jscomp$destructuring$var0[foo4()]
        """);

    test(
        "var [a] = foo(); var [{b}] = foo(); var [{...c}] = foo();",
        """
        var a;
        [a] = foo();
        // var [{b}] = foo();
        var b;
        [{b}] = foo();
        // var [{...c}] = foo();
        var c;
        var $jscomp$destructuring$var0 = (0, $jscomp.makeIterator)(foo());
        var $jscomp$destructuring$var1 = $jscomp$destructuring$var0.next().value;
        var $jscomp$destructuring$var2 = Object.assign({},$jscomp$destructuring$var1);
        c = $jscomp$destructuring$var2
        """);
  }

  @Test
  public void testArrayDestructuring_getsCorrectTypes() {

    test(
        srcs(
            """
            function takesIterable(/** !Iterable<number> */ iterableVar) {
              const [a] = iterableVar;
            }
            """),
        expected(
            """
            function takesIterable(iterableVar) {
              var $jscomp$destructuring$var0 = (0, $jscomp.makeIterator)(iterableVar);
              const a = $jscomp$destructuring$var0.next().value;
            }
            """));

    Compiler lastCompiler = getLastCompiler();

    // `$jscomp$destructuring$var0` is an Iterator<number>
    Node jscompDestructuringVar0 =
        getNodeMatchingQName(lastCompiler.getJsRoot(), "$jscomp$destructuring$var0");
    assertNode(jscompDestructuringVar0)
        .hasColorThat()
        .isEqualTo(lastCompiler.getColorRegistry().get(StandardColors.ITERATOR_ID));

    // `a` is a number
    Node aName = getNodeMatchingQName(lastCompiler.getJsRoot(), "a");
    assertNode(aName).hasColorThat().isEqualTo(StandardColors.NUMBER);

    // `$jscomp$destructuring$var0.next().value` is a number
    Node destructuringVarNextDotValue = aName.getOnlyChild();
    assertNode(destructuringVarNextDotValue).hasColorThat().isEqualTo(StandardColors.NUMBER);

    // `$jscomp$destructuring$var0.next()` is an IIterableResult<number>
    Node destructuringVarNextCall = destructuringVarNextDotValue.getFirstChild();
    assertNode(destructuringVarNextCall).hasColorThat().isEqualTo(StandardColors.UNKNOWN);
  }

  @Test
  public void testArrayDestructuringRest_getsCorrectTypes() {
    test(
        srcs(
            """
            function takesIterable(/** !Iterable<number> */ iterableVar) {
              const [a, ...rest] = iterableVar;
            }
            """),
        expected(
            """
            function takesIterable(iterableVar) {
              var $jscomp$destructuring$var0 = (0, $jscomp.makeIterator)(iterableVar);
              const a = $jscomp$destructuring$var0.next().value;
              const rest = (0, $jscomp.arrayFromIterator)($jscomp$destructuring$var0);
            }
            """));

    Compiler lastCompiler = getLastCompiler();

    Node jscompArrayFromIterator =
        getNodeMatchingQName(lastCompiler.getJsRoot(), "$jscomp.arrayFromIterator");
    assertNode(jscompArrayFromIterator).hasColorThat().isEqualTo(StandardColors.TOP_OBJECT);

    Node jscompArrayFromIteratorCall = jscompArrayFromIterator.getParent();
    assertNode(jscompArrayFromIteratorCall)
        .hasColorThat()
        .isEqualTo(lastCompiler.getColorRegistry().get(StandardColors.ARRAY_ID));

    // `rest` is Array<number>
    Node restName = getNodeMatchingQName(lastCompiler.getJsRoot(), "rest");
    assertNode(restName)
        .hasColorThat()
        .isEqualTo(lastCompiler.getColorRegistry().get(StandardColors.ARRAY_ID));
  }

  @Test
  public void testObjectDestructuring_getsCorrectTypes() {
    test(
        """
        const obj = {a: 3, b: 'string', c: null};
        const {a} = obj;
        """,
        """
        const obj = {a: 3, b: 'string', c: null};
        /** @const */ var $jscomp$destructuring$var0=obj;
        const a = $jscomp$destructuring$var0.a;
        """);

    Node jsRoot = getLastCompiler().getJsRoot();

    Node aName = getNodeMatchingQName(jsRoot, "a");
    assertNode(aName).hasColorThat().isEqualTo(StandardColors.NUMBER);

    Color objType = getNodeMatchingQName(jsRoot, "obj").getColor();
    // `$jscomp$destructuring$var0` has the same type as `obj`
    Node jscompDestructuringVar0Name = getNodeMatchingQName(jsRoot, "$jscomp$destructuring$var0");
    assertNode(jscompDestructuringVar0Name).hasColorThat().isEqualTo(objType);
  }

  @Test
  public void testObjectDestructuringDefaultValue_getsCorrectTypes() {
    test(
        """
        const obj = {a: 3, b: 'string', c: null};
        const {a = 4} = obj;
        """,
        """
        const obj = {a: 3, b: 'string', c: null};
        /** @const */ var $jscomp$destructuring$var0=obj;
        const a = $jscomp$destructuring$var0.a === void 0
            ? 4: $jscomp$destructuring$var0.a;
        """);

    Node jsRoot = getLastCompiler().getJsRoot();

    Node aName = getNodeMatchingQName(jsRoot, "a");
    assertNode(aName).hasColorThat().isEqualTo(StandardColors.NUMBER);

    Color objType = getNodeMatchingQName(jsRoot, "obj").getColor();

    // `$jscomp$destructuring$var0` has the same type as `obj`
    assertThat(
            getAllNodesMatchingQName(jsRoot, "$jscomp$destructuring$var0").stream()
                .map(Node::getColor)
                .collect(Collectors.toSet()))
        .containsExactly(objType);
  }

  @Test
  public void testObjectDestructuringComputedPropWithDefault_getsCorrectTypes() {
    test(
        """
        const /** !Object<string, number> */ obj = {['a']: 3};
        const {['a']: a = 4} = obj;
        """,
        """
        const obj = {['a']: 3};
        /** @const */ var $jscomp$destructuring$var0=obj;
        var $jscomp$destructuring$var1 = $jscomp$destructuring$var0['a'];
        const a = $jscomp$destructuring$var1 === void 0
            ? 4: $jscomp$destructuring$var1;
        """);

    Node jsRoot = getLastCompiler().getJsRoot();

    Node aName = getNodeMatchingQName(jsRoot, "a");
    assertNode(aName).hasColorThat().isEqualTo(StandardColors.NUMBER);

    // `$jscomp$destructuring$var0` has the same type as `obj`
    Node jscompDestructuringVar0Name = getNodeMatchingQName(jsRoot, "$jscomp$destructuring$var0");

    Color objType = jscompDestructuringVar0Name.getOnlyChild().getColor();
    assertThat(objType).isEqualTo(StandardColors.TOP_OBJECT);
    assertNode(jscompDestructuringVar0Name).hasColorThat().isEqualTo(objType);

    // `$jscomp$destructuring$var1` is typed as `number` (this is probably less important!)
    Node jscompDestructuringVar1Name = getNodeMatchingQName(jsRoot, "$jscomp$destructuring$var0");
    assertNode(jscompDestructuringVar1Name).hasColorThat().isEqualTo(objType);
  }

  @Test
  public void testObjectDestructuringRest_typesRestAsObject() {
    test(
        """
        const obj = {a: 3, b: 'string', c: null};
        const {...rest} = obj;
        """,
        """
        const obj = {a: 3, b: 'string', c: null};
        /** @const */ var $jscomp$destructuring$var0=obj;
        var $jscomp$destructuring$var1 = Object.assign({}, $jscomp$destructuring$var0);
        const rest = $jscomp$destructuring$var1;
        """);

    Node jsRoot = getLastCompiler().getJsRoot();

    Color objType = getNodeMatchingQName(jsRoot, "obj").getColor();

    // `$jscomp$destructuring$var0` has the same type as `obj`
    Node jscompDestructuringVar0Name = getNodeMatchingQName(jsRoot, "$jscomp$destructuring$var0");
    assertNode(jscompDestructuringVar0Name).hasColorThat().isEqualTo(objType);

    // `$jscomp$destructuring$var1` has the same type as `obj`
    Node jscompDestructuringVar1Name = getNodeMatchingQName(jsRoot, "$jscomp$destructuring$var1");
    assertNode(jscompDestructuringVar1Name).hasColorThat().isEqualTo(objType);

    // TODO(b/128355893) Do better inferrence. For now we just consider `rest` an `Object` rather
    // than trying to figure out what properties it gets.
    Node restName = getNodeMatchingQName(jsRoot, "rest");
    assertNode(restName).hasColorThat().isEqualTo(StandardColors.TOP_OBJECT);
  }

  /** Returns a list of all nodes in the given AST that matches the given qualified name */
  private ImmutableList<Node> getAllNodesMatchingQName(Node root, String qname) {
    ImmutableList.Builder<Node> builder = ImmutableList.builder();
    addAllNodesMatchingQNameHelper(root, qname, builder);
    return builder.build();
  }

  private void addAllNodesMatchingQNameHelper(
      Node root, String qname, ImmutableList.Builder<Node> nodesSoFar) {
    if (root.matchesQualifiedName(qname)) {
      nodesSoFar.add(root);
    }
    for (Node child = root.getFirstChild(); child != null; child = child.getNext()) {
      addAllNodesMatchingQNameHelper(child, qname, nodesSoFar);
    }
  }

  /** Returns the first node (preorder) in the given AST that matches the given qualified name */
  private Node getNodeMatchingQName(Node root, String qname) {
    if (root.matchesQualifiedName(qname)) {
      return root;
    }
    for (Node child = root.getFirstChild(); child != null; child = child.getNext()) {
      Node result = getNodeMatchingQName(child, qname);
      if (result != null) {
        return result;
      }
    }
    return null;
  }
}
