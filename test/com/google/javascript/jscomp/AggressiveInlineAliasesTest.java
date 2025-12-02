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

package com.google.javascript.jscomp;

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.javascript.jscomp.CompilerOptions.ChunkOutputType;
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the aggressive inlining part of {@link InlineAndCollapseProperties}. */
@RunWith(JUnit4.class)
public class AggressiveInlineAliasesTest extends CompilerTestCase {

  private static final String EXTERNS =
      """
      var window
      function alert(s) {}
      function parseInt(s) {}
      function String() {};
      var arguments
      """;

  public AggressiveInlineAliasesTest() {
    super(EXTERNS);
  }

  private static PassFactory makePassFactory(
      String name, Function<AbstractCompiler, CompilerPass> pass) {
    return PassFactory.builder().setName(name).setInternalFactory(pass).build();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    PhaseOptimizer optimizer = new PhaseOptimizer(compiler, null);
    optimizer.addOneTimePass(makePassFactory("es6NormalizeClasses", Es6NormalizeClasses::new));
    optimizer.addOneTimePass(
        makePassFactory(
            "inlineAndCollapseProperties",
            (comp) ->
                InlineAndCollapseProperties.builder(compiler)
                    .setPropertyCollapseLevel(PropertyCollapseLevel.ALL)
                    .setChunkOutputType(ChunkOutputType.GLOBAL_NAMESPACE)
                    .setHaveModulesBeenRewritten(false)
                    .setModuleResolutionMode(ResolutionMode.BROWSER)
                    .testAggressiveInliningOnly(this::validateGlobalNamespace)
                    .build()));
    return optimizer;
  }

  @Before
  public void customSetUp() throws Exception {
    enableNormalize();
    setGenericNameReplacements(Es6NormalizeClasses.GENERIC_NAME_REPLACEMENTS);
  }

  @Test
  public void testHasOwnProperty() {
    test(
        "var a = {'b': 1, 'c': 1}; var alias = a;    alert(alias.hasOwnProperty('c'));", //
        "var a = {'b': 1, 'c': 1}; var alias = null; alert(a.hasOwnProperty('c'));");
  }

  @Test
  public void test_b19179602() {
    test(
        """
        var a = {};
        /** @constructor */ a.b = function() {};
        a.b.staticProp = 5;
        /** @constructor */
        function f() {
          while (true) {
            var b = a.b;
            alert(b.staticProp);
          }
        }
        """,
        """
        var a = {};
        /** @constructor */ a.b = function() {};
        a.b.staticProp = 5;
        /** @constructor */
        function f() {
          for(; true; ) {
            var b = null; // replaced with null
            alert(a.b.staticProp); // value inlined
          }
        }
        """);
    test(
        """
        var a = {};
        /** @constructor */ a.b = function() {};
        a.b.staticProp = 5;
        /** @constructor */
        function f() {
          while (true) {
            var b = a.b;
            alert(b?.staticProp);
          }
        }
        """,
        """
        var a = {};
        /** @constructor */ a.b = function() {};
        a.b.staticProp = 5;
        /** @constructor */
        function f() {
          for(; true; ) {
            var b = null; // replaced with null
            alert(a.b?.staticProp); // value inlined
          }
        }
        """);
    testSame(
        """
        var a = {};
        /** @constructor */ a.b = function() {};
        a.b.staticProp = 5;
        /** @constructor */
        function f() {
          for (; true;) {
            var b = a?.b; // not inlined when optional chain
            alert(b?.staticProp);
          }
        }
        """);
  }

  @Test
  public void test_b19179602_declareOutsideLoop() {
    test(
        """
        var a = {};
        a.b = function() {};
        a.b.staticProp = 5;
        function f() {
          var b = a.b;
          while (true) {
            alert(b.staticProp);
          }
        }
        """,
        """
        var a = {};
        a.b = function() {};
        a.b.staticProp = 5;
        function f() {
          var b = null; // replaced with null
          for (;true;) // converted to for-loop
            alert(a.b.staticProp); // inlined
        }
        """);
    test(
        """
        var a = {};
        a.b = function() {};
        a.b.staticProp = 5;
        function f() {
          var b = a.b;
          while (true) {
            alert(b?.staticProp); // reference with opt chain
          }
        }
        """,
        """
        var a = {};
        a.b = function() {};
        a.b.staticProp = 5;
        function f() {
          var b = null; // replaced with null
          for (;true;) // converted to for-loop
            alert(a.b?.staticProp); // inlined
        }
        """);

    testSame(
        """
        var a = {};
        a.b = function() {};
        a.b.staticProp = 5;
        function f() {
        // Not an alias for `a.b` when it's an optional chain.
          var b = a?.b;
          for (; true;) {
            alert(b?.staticProp);
          }
        }
        """);
  }

  @Test
  public void testCtorAliasedMultipleTimesNoWarning1() {
    // We can't inline the alias, but there are no unsafe property accesses.
    testSame(
        """
        /** @constructor */
        function a() {}
        function f() {
          var alias = a;
          use(alias.prototype); // The prototype is not collapsible.
          alias.apply(); // Can't collapse externs properties.
          alias = function() {}
        }
        """);
  }

  @Test
  public void testCtorAliasedMultipleTimesNoWarning2() {
    testSame(
        """
        /** @constructor */
        function a() {}
        /** @nocollapse */
        a.staticProp = 5; // Explicitly forbid collapsing a.staticProp.
        function f() {
          var alias = a;
          use(alias.staticProp); // Safe because we don't collapse a.staticProp
          alias = function() {}
        }
        """);
  }

  @Test
  public void testGlobalCtorAliasedMultipleTimes() {
    // TODO(lharker): Also warn for unsafe global ctor aliasing
    testSame(
        """
        /** @constructor */
        function a() {}
        a.staticProp = 5;
        var alias = a;
        use(alias.staticProp); // Unsafe because a.staticProp becomes a$staticProp.
        alias = function() {}
        """);
  }

  @Test
  public void testCtorAliasedMultipleTimesWarning1() {
    testSame(
        """
        /** @constructor */
        function a() {}
        a.staticProp = 5;
        function f() {
          var alias = a;
          use(alias.staticProp); // Unsafe because a.staticProp becomes a$staticProp.
          alias = function() {}
        }
        """,
        InlineAndCollapseProperties.UNSAFE_CTOR_ALIASING);
  }

  @Test
  public void testCtorAliasedMultipleTimesWarning2() {
    testSame(
        """
        /** @constructor */
        function a() {}
        a.b = {};
        a.b.staticProp = 5;
        function f() {
          var alias = a;
          use(alias.b.staticProp); // Unsafe because a.b.staticProp becomes a$b$staticProp.
          alias = function() {}
        }
        """,
        InlineAndCollapseProperties.UNSAFE_CTOR_ALIASING);
    testSame(
        """
        /** @constructor */
        function a() {}
        a.b = {};
        a.b.staticProp = 5;
        function f() {
          var alias = a;
        // Make sure a property reference via optional chain is still
        // recognized as unsafe.
          use(alias?.b.staticProp); // Unsafe because a.b.staticProp becomes a$b$staticProp.
          alias = function() {}
        }
        """,
        InlineAndCollapseProperties.UNSAFE_CTOR_ALIASING);
  }

  @Test
  public void testCtorAliasedMultipleTimesWarning3() {
    testSame(
        """
        /** @constructor */
        function a() {}
        a.staticProp = 5;
        function f(alias) {
          alias();
          alias = a;
          use(alias.staticProp); // Unsafe because a.staticProp becomes a$staticProp.
        }
        """,
        InlineAndCollapseProperties.UNSAFE_CTOR_ALIASING);
  }

  @Test
  public void testCtorAliasedMultipleTimesWarning4() {
    testWarning(
        """
        /** @constructor */
        function a() {}
        a.staticProp = 5;
        function f() {
          if (true) {
            var alias = a;
            use(alias.staticProp);
          } else {
            alias = {staticProp: 34};
            use(alias.staticProp);
          }
        }
        """,
        InlineAndCollapseProperties.UNSAFE_CTOR_ALIASING);
  }

  @Test
  public void testAliasingOfReassignedProperty1() {
    testSame("var obj = {foo: 3}; var foo = obj.foo; obj.foo = 42; alert(foo);");
    testSame("var obj = {foo: 3}; var foo = obj?.foo; obj.foo = 42; alert(foo);");
  }

  @Test
  public void testAliasingOfReassignedProperty2() {
    testSame("var obj = {foo: 3}; var foo = obj.foo; obj = {}; alert(foo);");
  }

  @Test
  public void testAliasingOfReassignedProperty3() {
    testSame("var obj = {foo: {bar: 3}}; var bar = obj.foo.bar; obj.foo = {}; alert(bar);");
    testSame("var obj = {foo: {bar: 3}}; var bar = obj.foo?.bar; obj.foo = {}; alert(bar);");
    testSame("var obj = {foo: {bar: 3}}; var bar = obj?.foo.bar; obj.foo = {}; alert(bar);");
  }

  @Test
  public void testAliasingOfReassignedProperty4() {
    // Note: it should be safe to inline aliases for properties of ns below, even though ns
    // has multiple definitions. Not inlining "foo" to "ns.ctor.foo" actually causes bad code later,
    // because CollapseProperties unsafely collapses aliased ctor properties.
    test(
        """
        var ns = {};
        /** @constructor */ ns.ctor = function() {};
        ns.ctor.foo = 3;
        var foo = ns.ctor.foo;
        ns = ns || {}; // safe reinitialization of ns.
        alert(foo);
        """,
        """
        var ns = {};
        /** @constructor */ ns.ctor = function() {};
        ns.ctor.foo = 3;
        var foo = null;
        ns = ns || {};
        alert(ns.ctor.foo);
        """);
    testSame(
        """
        var ns = {};
        /** @constructor */ ns.ctor = function() {};
        ns.ctor.foo = 3;
        // optional chain means this isn't a real alias
        var foo = ns.ctor?.foo;
        ns = ns || {}; // safe reinitialization of ns.
        alert(foo);
        """);
  }

  @Test
  public void testAliasingOfReassignedProperty5() {
    test(
        "var obj = {foo: {bar: 3}}; var bar = obj.foo.bar; var obj; alert(bar);",
        "var obj = {foo: {bar: 3}}; var bar =        null;          alert(obj.foo.bar);");
  }

  @Test
  public void testAddPropertyToChildFuncOfUncollapsibleObjectInLocalScope() {
    test(
        """
        var a = {};
        a.b = function () {};
        a.b.x = 0;
        var c = a;
        (function() { a.b.y = 1; })();
        a.b.x;
        a.b.y;
        """,
        """
        var a = {};
        a.b = function() {};
        a.b.x = 0;
        var c = null;
        (function() { a.b.y = 1; })();
        a.b.x;
        a.b.y;
        """);
  }

  @Test
  public void testAddPropertyToChildOfUncollapsibleCtorInLocalScope() {
    test(
        """
        var a = function() {};
        a.b = { x: 0 };
        var c = a;
        (function() { a.b.y = 0; })();
        a.b.y;
        """,
        """
        var a = function() {};
        a.b = { x: 0 };
        var c = null;
        (function() { a.b.y = 0; })();
        a.b.y;
        """);
  }

  @Test
  public void testAddPropertyToChildOfUncollapsibleFunctionInLocalScope() {
    test(
        """
        function a() {} a.b = { x: 0 };
        var c = a;
        (function() { a.b.y = 0; })();
        a.b.y;
        """,
        """
        function a() {} a.b = { x: 0 };
        var c = null;
        (function() { a.b.y = 0; })();
        a.b.y;
        """);
  }

  @Test
  public void testAddPropertyToChildTypeOfUncollapsibleObjectInLocalScope() {
    test(
        """
        var a = {};
        a.b = function () {};
        a.b.x = 0;
        var c = a;
        (function() { a.b.y = 1; })();
        a.b.x;
        a.b.y;
        """,
        """
        var a = {};
        a.b = function() {};
        a.b.x = 0;
        var c = null;
        (function() { a.b.y = 1; })();
        a.b.x;
        a.b.y;
        """);
  }

  @Test
  public void testAddPropertyToUncollapsibleCtorInLocalScopeDepth1() {
    test(
        """
        var a = function() {};
        var c = a;
        (function() { a.b = 0;
        })();
        a.b;
        """,
        """
        var a = function() {};
        var c = null;
        (function() { a.b = 0 })();
        a.b;
        """);
  }

  @Test
  public void testAddPropertyToUncollapsibleCtorInLocalScopeDepth2() {
    test(
        """
        var a = {};
        a.b = function () {};
        var d = a.b;
        (function() { a.b.c = 0; })();
        a.b.c;
        """,
        """
        var a = {};
        a.b = function() {};
        var d = null;
        (function() { a.b.c = 0; })();
        a.b.c;
        """);
  }

  @Test
  public void testAddPropertyToUncollapsibleNamedCtorInLocalScopeDepth1() {
    test(
        """
        function a() {} var a$b;
        var c = a;
        (function() { a$b = 0;
        })();
        a$b;
        """,
        """
        function a() {} var a$b;
        var c = null;
        (function() { a$b = 0;
        })();
        a$b;
        """);
  }

  @Test
  public void testAddPropertyToUncollapsibleObjectInLocalScopeDepth1() {
    test(
        """
        var a = {};
        var c = a;
        use(c);
        (function() { a.b = 0;
        })();
        a.b;
        """,
        """
        var a = {};
        var c = null;
        use(a);
        (function() { a.b = 0;
        })();
        a.b;
        """);
  }

  @Test
  public void testAddPropertyToUncollapsibleObjectInLocalScopeDepth2() {
    test(
        """
        var a = {};
        a.b = {};
        var d = a.b;
        use(d);
        (function() { a.b.c = 0; })();
        a.b.c;
        """,
        """
        var a = {};
        a.b = {};
        var d = null;
        use(a.b);
        (function() { a.b.c = 0; })();
        a.b.c;
        """);
    testSame(
        """
        var a = {};
        a.b = {};
        var d = a?.b; // optional chain makes this no longer an alias
        use(d);
        (function() { a.b.c = 0; })();
        a.b.c;
        """);
  }

  @Test
  public void testAliasCreatedForClassDepth1_1() {
    test(
        """
        var a = {};
        a.b = function() {};
        var c = a;
        c.b = 0;
        a.b != c.b;
        """,
        """
        var a = {};
        a.b = function() {};
        var c = null;
        a.b = 0;
        a.b != a.b;
        """);

    test(
        """
        var a = {};
        a.b = function() {};
        var c = a;
        c.b = 0;
        a.b == c.b;
        """,
        """
        var a = {};
        a.b = function() {};
        var c = null;
        a.b = 0;
        a.b == a.b;
        """);
  }

  @Test
  public void testAliasCreatedForCtorDepth1_1() {
    test(
        """
        var a = function() {};
        a.b = 1;
        var c = a;
        c.b = 2;
        a.b == c.b;
        """,
        """
        var a = function() {};
        a.b = 1;
        var c = null;
        a.b = 2;
        a.b == a.b;
        """);

    test(
        """
        var a = function() {};
        a.b = 1;
        var c = a;
        c.b = 2;
        a?.b == c?.b;
        """,
        """
        var a = function() {};
        a.b = 1;
        var c = null;
        a.b = 2;
        a?.b == a?.b;
        """);
  }

  @Test
  public void testAliasCreatedForCtorDepth2() {
    test(
        """
        var a = {};
        a.b = function() {};
        a.b.c = 1;
        var d = a.b;
        a.b.c == d.c;
        """,
        """
        var a = {};
        a.b = function() {};
        a.b.c = 1;
        var d = null;
        a.b.c == a.b.c
        """);
    testSame(
        """
        var a = {};
        a.b = function() {};
        a.b.c = 1;
        var d = a?.b; // optional chain prevents this being a true alias
        a.b.c == d?.c;
        """);
  }

  @Test
  public void testAliasCreatedForFunctionDepth2() {
    test(
        """
        var a = {};
        a.b = function() {};
        a.b.c = 1;
        var d = a.b;
        a.b.c != d.c;
        """,
        """
        var a = {};
        a.b = function() {};
        a.b.c = 1;
        var d = null;
        a.b.c != a.b.c
        """);

    test(
        """
        var a = {};
        a.b = function() {};
        a.b.c = 1;
        var d = a.b;
        a.b.c == d.c;
        """,
        """
        var a = {};
        a.b = function() {};
        a.b.c = 1;
        var d = null;
        a.b.c == a.b.c
        """);
  }

  @Test
  public void testAliasCreatedForObjectDepth1_1() {
    test(
        """
        var a = { b: 0 }
        var c = a;
        c.b = 1;
        a.b == c.b;
        """,
        """
        var a = { b: 0 }
        var c = null;
        a.b = 1;
        a.b == a.b;
        """);

    test(
        """
        var a = { b: 0 }
        var c = a;
        c.b = 1;
        a.b == c.b;
        use(c);
        """,
        """
        var a = { b: 0 }
        var c = null;
        a.b = 1;
        a.b == a.b;
        use(a);
        """);
  }

  @Test
  public void testLocalAliasCreatedAfterVarDeclaration1() {
    test(
        """
        var a = { b : 3 };
        function f() {
          var tmp;
          if (true) {
            tmp = a;
            use(tmp);
          }
        }
        """,
        """
        var a = { b : 3 };
        function f() {
          var tmp;
          if (true) {
            tmp = null;
            use(a);
          }
        }
        """);
  }

  @Test
  public void testLocalAliasCreatedAfterVarDeclaration2() {
    test(
        """
        var a = { b : 3 };
        function f() {
          var tmp;
          if (true) {
            tmp = a;
            use(tmp);
          }
        }
        """,
        """
        var a = { b : 3 };
        function f() {
          var tmp;
          if (true) {
            tmp = null;
            use(a);
          }
        }
        """);
  }

  @Test
  public void testLocalAliasCreatedAfterVarDeclaration3() {
    testSame(
        """
        var a = { b : 3 };
        function f() {
          var tmp;
          if (true) {
            tmp = a;
          }
          use(tmp);
        }
        """);
  }

  @Test
  public void testLocalCtorAliasCreatedAfterVarDeclaration1() {
    test(
        """
        /** @constructor @struct */ var Main = function() {};
        Main.doSomething = function(i) {}
        function f() {
          var tmp;
          tmp = Main;
          tmp.doSomething(5);
        }
        """,
        """
        /** @constructor */ var Main = function() {};
        Main.doSomething = function(i) {}
        function f() {
          var tmp;
          tmp = null;
          Main.doSomething(5);
        }
        """);
    test(
        """
        /** @constructor @struct */ var Main = function() {};
        Main.doSomething = function(i) {}
        function f() {
          var tmp;
          tmp = Main;
          tmp?.doSomething(5);
        }
        """,
        """
        /** @constructor */ var Main = function() {};
        Main.doSomething = function(i) {}
        function f() {
          var tmp;
          tmp = null;
          Main?.doSomething(5);
        }
        """);
  }

  @Test
  public void testLocalCtorAliasCreatedAfterVarDeclaration2() {
    test(
        """
        /** @constructor @struct */ var Main = function() {};
        Main.doSomething = function(i) {}
        function f() {
          tmp = Main;
          tmp.doSomething(5);
          if (true) {
            tmp.doSomething(6);
          }
          var tmp;
        }
        """,
        """
        /** @constructor */ var Main = function() {};
        Main.doSomething = function(i) {}
        function f() {
          tmp = null;
          Main.doSomething(5);
          if (true) {
            Main.doSomething(6);
          }
          var tmp;
        }
        """);
  }

  @Test
  public void testLocalCtorAliasCreatedAfterVarDeclaration3() {
    test(
        """
        /** @constructor @struct */ var Main = function() {};
        Main.doSomething = function(i) {}
        function f() {
          if (tmp = Main) {
            tmp.doSomething(6);
          }
          var tmp;
        }
        """,
        """
        /** @constructor */ var Main = function() {};
        Main.doSomething = function(i) {}
        function f() {
          if (tmp = Main) { // Don't set "tmp = null" because that would change control flow.
            Main.doSomething(6);
          }
          var tmp;
        }
        """);
  }

  @Test
  public void testLocalCtorAliasCreatedAfterVarDeclaration4() {
    test(
        """
        /** @constructor @struct */ var Main = function() {};
        Main.doSomething = function(i) {}
        function f() {
          var a = tmp = Main;
          tmp.doSomething(6);
          a.doSomething(7);
          var tmp;
        }
        """,
        """
        /** @constructor */ var Main = function() {};
        Main.doSomething = function(i) {}
        function f() {
          var a = tmp = Main; // Don't set "tmp = null" because that would mess up a's value.
          Main.doSomething(6);
          a.doSomething(7); // Main doesn't get inlined here, which makes collapsing unsafe.
          var tmp;
        }
        """);
  }

  @Test
  public void testLocalCtorAliasAssignedInLoop1() {
    testWarning(
        """
        /** @constructor @struct */ var Main = function() {};
        Main.doSomething = function(i) {}
        function f() {
          var tmp;
          for (let i = 0; i < n(); i++) {
            tmp = Main;
            tmp.doSomething(5);
            use(tmp);
          }
          use(tmp);
          use(tmp.doSomething);
        }
        """,
        InlineAndCollapseProperties.UNSAFE_CTOR_ALIASING);
  }

  @Test
  public void testLocalCtorAliasAssignedInLoop2() {
    // Test when the alias is assigned in a loop after being used.
    testWarning(
        """
        /** @constructor @struct */ var Main = function() {};
        Main.doSomething = function(i) {}
        function f() {
          var tmp;
          for (let i = 0; i < n(); i++) {
            use(tmp); // Don't inline tmp here since it changes between loop iterations.
            tmp = Main;
          }
          use(tmp);
          use(tmp.doSomething);
        }
        """,
        InlineAndCollapseProperties.UNSAFE_CTOR_ALIASING);
  }

  @Test
  public void testLocalCtorAliasAssignedInSwitchCase() {
    // This mimics how the async generator polyfill behaves.
    test(
        """
        /** @constructor @struct */ var Main = function() {};
        Main.doSomething = function(i) {}
        function f() {
          function g() {
            for (;;) {
              switch(state) {
                case 0:
                  tmp1 = Main;
                  tmp2 = tmp1.doSomething;
                  state = 1;
                  return;
                 case 1:
                   return tmp2.call(tmp1, 3);
              }
            }
          }
          var tmp1;
          var tmp2;
          var state = 0;
          g();
          return g();
        }
        """,
        """
        /** @constructor */ var Main = function() {};
        Main.doSomething = function(i) {}
        function f() {
          function g() {
            for (;;) {
              switch(state) {
                case 0:
                  tmp1 = Main;
                  tmp2 = Main.doSomething;
                  state = 1;
                  return;
                 case 1:
                   return tmp2.call(tmp1, 3);
              }
            }
          }
          var tmp1;
          var tmp2;
          var state = 0;
          g();
          return g();
        }
        """);
  }

  @Test
  public void testCodeGeneratedByGoogModule() {
    test(
        """
        var $jscomp = {}
        $jscomp.scope = {};
        $jscomp.scope.Foo = function() {};
        var exports = $jscomp.scope.Foo;
        exports.staticprop = { A: 1 };
        var y = exports.staticprop.A;
        """,
        """
        var $jscomp = {}
        $jscomp.scope = {};
        $jscomp.scope.Foo = function() {};
        var exports = null;
        $jscomp.scope.Foo.staticprop = { A: 1 };
        var y = null;
        """);

    test(
        """
        var $jscomp = {}
        $jscomp.scope = {};
        $jscomp.scope.Foo = function() {};
        $jscomp.scope.Foo.staticprop = { A: 1 };
        var exports = $jscomp.scope.Foo;
        var y = exports.staticprop.A;
        """,
        """
        var $jscomp = {}
        $jscomp.scope = {};
        $jscomp.scope.Foo = function() {};
        $jscomp.scope.Foo.staticprop = { A: 1 };
        var exports = null;
        var y = null;
        """);
  }

  @Test
  public void testCollapse() {
    test(
        """
        var a = {}
        a.b = {};
        var c = a.b;
        """,
        """
        var a = {}
        a.b = {};
        var c = null
        """);

    test(
        """
        var a = {}
        a.b = {};
        var c = a.b;
        use(c);
        """,
        """
        var a = {}
        a.b = {};
        var c = null;
        use(a.b)
        """);

    testSame(
        """
        var a = {};
        a.b;
        """);
  }

  @Test
  public void testCollapsePropertiesOfClass1() {
    test(
        """
        /** @constructor */ var namespace = function() {};
        goog.inherits(namespace, Object);
        namespace.includeExtraParam = true;
        /** @enum { number } */
        namespace.Param = { param1: 1, param2: 2 };
        if (namespace.includeExtraParam) namespace.Param.optParam = 3;
        /** @constructor */
        function f() {
          var Param = namespace.Param;
          log(namespace.Param.optParam);
          log(Param.optParam);
        }
        """,
        """
        /** @constructor */ var namespace = function() {};
        goog.inherits(namespace,Object);
        namespace.includeExtraParam = true;
        /** @enum {JSDocSerializer_placeholder_type} */
        namespace.Param = { param1: 1,param2: 2 };
        if(namespace.includeExtraParam) namespace.Param.optParam = 3;
        /** @constructor */
        function f() {
          var Param = null;
          log(namespace.Param.optParam);
          log(namespace.Param.optParam);
        }
        """);
  }

  @Test
  public void testCollapsePropertiesOfClass2() {
    test(
        """
        var goog = goog || {}
        goog.addSingletonGetter = function(cls) {};
        var a = {};
        a.b = function() {};
        goog.addSingletonGetter(a.b);
        a.b.prototype.get = function(key) {};
        a.b.c = function() {};
        a.b.c.XXX = new a.b.c;
        function f() {
          var x = a.b.getInstance();
          var Key = a.b.c;
          x.get(Key.XXX);
        }
        """,
        """
        var goog = goog || {}
        goog.addSingletonGetter = function(cls) {};
        var a = {};
        a.b = function() {};
        goog.addSingletonGetter(a.b);
        a.b.prototype.get = function(key) {};
        a.b.c = function() {};
        a.b.c.XXX = new a.b.c;
        function f() {
          var x = a.b.getInstance();
          var Key = null;
          x.get(a.b.c.XXX);
        }
        """);
  }

  @Test
  public void testCommaOperator() {
    test(
        """
        var ns = {}
        ns.Foo = {};
        var Baz = {};
        Baz.Foo = ns.Foo;
        Baz.Foo.bar = 10, 123;
        """,
        """
        var ns = {}
        ns.Foo = {};
        var Baz = {};
        Baz.Foo = null;
        ns.Foo.bar = 10, 123;
        """);
  }

  @Test
  public void testDontCrashCtorAliasWithEnum() {
    test(
        """
        var ns = {};
        /** @constructor */ ns.Foo = function () {};
        var Bar = ns.Foo;
        /** @const @enum */
        Bar.prop = { A: 1 };
        """,
        """
        var ns = {};
        /** @constructor */ ns.Foo = function() {};
        var Bar = null;
        /** @const @enum {JSDocSerializer_placeholder_type} */
        ns.Foo.prop = { A: 1 }
        """);
  }

  @Test
  public void testFunctionAlias2() {
    test(
        """
        var a = {}
        a.b = {};
        a.b.c = function() {};
        a.b.d = a.b.c;
        use(a.b.d);
        """,
        """
        var a = {}
        a.b = {};
        a.b.c = function() {};
        a.b.d = null;
        use(a.b.c);
        """);
    testSame(
        """
        var a = {}
        a.b = {};
        a.b.c = function() {};
        a.b.d = a.b.c;
        // reference to alias via optional chain stops inlining
        use(a.b?.d);
        """);
  }

  @Test
  public void testGlobalAliasOfAncestor() {
    test(
        """
        var a = { b: { c: 5 } }
        function f() {
          var x = a.b;
          f(x.c);
        }
        """,
        """
        var a = { b: { c: 5 } }
        function f() {
          var x = null;
          f(a.b.c);
        }
        """);
    test(
        """
        var a = { b: { c: 5 } }
        function f() {
          var x = a.b;
          f(x?.c);
        }
        """,
        """
        var a = { b: { c: 5 } }
        function f() {
          var x = null;
          f(a.b?.c);
        }
        """);
  }

  @Test
  public void testGlobalES5ClassVarWithInnerNameDotPropReference() {
    test(
        """
        /** @constructor */
        var GlobalName = function InnerName() {
            InnerName.staticMethod(); // replace InnerName with GlobalName
        };
        GlobalName.staticMethod = function() {
          console.log('staticMethod');
        };
        """,
        """
        /** @constructor */
        var GlobalName = function InnerName() {
            GlobalName.staticMethod();
        };
        GlobalName.staticMethod = function() {
          console.log('staticMethod');
        };
        """);
  }

  @Test
  public void testGlobalClassVarWithInnerNameDotPropReference() {
    test(
        """
        const GlobalName = class InnerName {
          method() {
            return InnerName.staticMethod(); // replace InnerName with GlobalName
          }
          static staticMethod() {
            console.log('staticMethod');
          }
        };
        """,
        """
        const GlobalName = class {
          method() {
            return GlobalName.staticMethod();
          }
          static staticMethod() {
            console.log("staticMethod");
          }
        };
        """);
  }

  @Test
  public void testGlobalClassPropWithInnerNameDotPropReference() {
    test(
        """
        const GlobalName = {};
        GlobalName.prop = class InnerName {
          method() {
            return InnerName.staticMethod(); // replace InnerName with GlobalName.prop
          }
          static staticMethod() {
            console.log('staticMethod');
          }
        };
        """,
        """
        const GlobalName = {};
        GlobalName.prop = class {
          method() {
            return GlobalName.prop.staticMethod();
          }
          static staticMethod() {
            console.log("staticMethod");
          }
        };
        """);
  }

  @Test
  public void testGlobalClassVarSetTwiceWithInnerNameDotPropReference() {
    test(
        """
        let GlobalName = class InnerName {
          method() {
            return InnerName.staticMethod(); // replace InnerName with GlobalName
          }
          static staticMethod() {
            console.log('staticMethod');
          }
        };
        // second assignment prevents inlining
        GlobalName = function SecondValue() {}
        """,
        """
        let GlobalName = class {
          method() {
            return GlobalName.staticMethod();
          }
          static staticMethod() {
            console.log("staticMethod");
          }
        };
        GlobalName = function SecondValue() {};
        """);
  }

  @Test
  public void testGlobalClassPropWithInnerNameInstanceOfReference() {
    test(
        """
        const GlobalName = {};
        GlobalName.prop = class InnerName {
          method() {
            return this instanceof InnerName;
          }
        };
        """,
        """
        const GlobalName = {};
        GlobalName.prop = class {
          method() {
            return this instanceof GlobalName.prop;
          }
        };
        """);
  }

  @Test
  public void testGlobalAliasWithProperties1() {
    test(
        """
        var ns = {}
        ns.Foo = function() {};
        /** @enum { number } */ ns.Foo.EventType = { A: 1, B: 2 };
        ns.Bar = ns.Foo;
        var x = function() { use(ns.Bar.EventType.A) };
        use(x);
        """,
        """
        var ns = {}
        ns.Foo = function() {};
        /** @enum {JSDocSerializer_placeholder_type} */ ns.Foo.EventType = { A: 1, B: 2 };
        ns.Bar = null;
        var x = function() { use(ns.Foo.EventType.A) };
        use(x);
        """);
  }

  @Test
  public void testGlobalAliasWithProperties2() {
    test(
        """
        var ns = {}
        ns.Foo = function() {};
        /** @enum { number } */ ns.Foo.EventType = { A: 1, B: 2 };
        ns.Bar = ns.Foo;
        /** @const */ ns.Bar.EventType = ns.Foo.EventType;
        var x = function() { use(ns.Bar.EventType.A) };
        use(x)
        """,
        """
        var ns = {}
        ns.Foo = function() {};
        /** @enum {JSDocSerializer_placeholder_type} */ ns.Foo.EventType = { A: 1, B: 2 };
        ns.Bar = null;
        /** @const */ ns.Foo.EventType = ns.Foo.EventType;
        var x = function() { use(ns.Foo.EventType.A) };
        use(x)
        """);
  }

  @Test
  public void testGlobalAliasWithProperties3() {
    test(
        """
        var ns = {}
        ns.Foo = function() {};
        /** @enum { number } */ ns.Foo.EventType = { A: 1, B: 2 };
        ns.Bar = ns.Foo;
        /** @const */ ns.Bar.Other = { X: 1, Y: 2 };
        var x = function() { use(ns.Bar.Other.X) };
        use(x)
        """,
        """
        var ns = {}
        ns.Foo = function() {};
        /** @enum {JSDocSerializer_placeholder_type} */ ns.Foo.EventType = { A: 1, B: 2 };
        ns.Bar = null;
        /** @const */ ns.Foo.Other = { X: 1, Y: 2 };
        var x = function() { use(ns.Foo.Other.X) };
        use(x)
        """);
  }

  @Test
  public void testGlobalAliasWithPropertiesAndOptChainReference() {
    test(
        """
        var ns = {}
        ns.Foo = function() {};
        /** @enum { number } */ ns.Foo.EventType = { A: 1, B: 2 };
        ns.Bar = ns.Foo;
        /** @const */ ns.Bar.Other = { X: 1, Y: 2 };
        var x = function() { use(ns.Bar?.Other.X) };
        use(x)
        """,
        """
        var ns = {}
        ns.Foo = function() {};
        /** @enum {JSDocSerializer_placeholder_type} */ ns.Foo.EventType = { A: 1, B: 2 };
        ns.Bar = null;
        /** @const */ ns.Foo.Other = { X: 1, Y: 2 };
        var x = function() { use(ns.Foo?.Other.X) };
        use(x)
        """);
  }

  @Test
  public void testGlobalAliasWithPropertiesAsNestedObjectLits() {
    test(
        """
        var ns = {}
        ns.Foo = function() {};
        ns.Bar = ns.Foo;
        /** @enum { number } */ ns.Bar.Other = { X: {Y: 1}};
        var x = function() { use(ns.Bar.Other.X.Y) };
        use(x)
        """,
        """
        var ns = {}
        ns.Foo = function() {};
        ns.Bar = null;
        /** @enum {JSDocSerializer_placeholder_type} */ ns.Foo.Other = { X: {Y: 1}};
        var x = function() { use(ns.Foo.Other.X.Y) };
        use(x)
        """);
  }

  @Test
  public void testGlobalWriteToNonAncestor() {
    test(
        """
        var a = { b: 3 }
        function f() {
          var x = a;
          f(a.b);
        }
        a.b = 5;
        """,
        """
        var a = { b: 3 }
        function f() {
          var x = null;
          f(a.b);
        }
        a.b = 5;
        """);
  }

  @Test
  public void testInlineCtorInObjLit() {
    test(
        """
        function Foo() {}
        var Bar = Foo;
        var objlit = { 'prop' : Bar };
        """,
        """
        function Foo() {}
        var Bar = null;
        var objlit = { 'prop': Foo };
        """);
  }

  @Test
  public void testLocalAlias1() {
    test(
        """
        var a = { b: 3 }
        function f() {
          var x = a;
          f(x.b);
        }
        """,
        """
        var a = { b: 3 }
        function f() {
          var x = null;
          f(a.b);
        }
        """);
  }

  @Test
  public void testLocalAlias2() {
    test(
        """
        var a = { b: 3, c: 4 }
        function f() { var x = a;
        f(x.b);
        f(x.c);
        }
        """,
        """
        var a = { b: 3, c: 4 }
        function f() { var x = null;
        f(a.b);
        f(a.c);
        }
        """);
  }

  @Test
  public void testLocalAlias3() {
    test(
        """
        var a = { b: 3, c: { d: 5 } }
        function f() {
          var x = a;
          f(x.b);
          f(x.c);
          f(x.c?.d);
        }
        """,
        """
        var a = { b: 3, c: { d: 5 } }
        function f() {
          var x = null;
          f(a.b);
          f(a.c);
          f(a.c?.d)
        }
        """);
  }

  @Test
  public void testLocalAlias4() {
    test(
        """
        var a = { b: 3 }
        var c = { d: 5 };
        function f() {
          var x = a;
          var y = c;
          f(x.b);
          f(y.d);
        }
        """,
        """
        var a = { b: 3 }
        var c = { d: 5 };
        function f() {
          var x = null;
          var y = null;
          f(a.b);
          f(c.d)
        }
        """);
  }

  @Test
  public void testLocalAlias5() {
    test(
        """
        var a = { b: { c: 5 } }
        function f() {
          var x = a;
          var y = x.b;
          f(a.b.c);
          f(y.c);
        }
        """,
        """
        var a = { b: { c: 5 } }
        function f() {
          var x = null;
          var y = null;
          f(a.b.c);
          f(a.b.c);
        }
        """);
  }

  @Test
  public void testLocalAlias6() {
    test(
        """
        var a = { b: 3 }
        function f() {
          var x = a;
          if (x.b) f(x.b);
        }
        """,
        """
        var a = { b: 3 }
        function f() {
          var x = null;
          if (a.b) f(a.b);
        }
        """);
  }

  @Test
  public void testLocalAlias7() {
    test(
        """
        var a = { b: { c: 5 } }
        function f() {
          var x = a.b;
          f(x.c);
        }
        """,
        """
        var a = { b: { c: 5 } }
        function f() {
          var x = null;
          f(a.b.c);
        }
        """);
  }

  @Test
  public void testLocalAlias8() {
    testSame(
        """
        var a = { b: 3 };
        function f() { if (true) { var x = a; f(x.b); } x = { b : 4}; }
        """);
  }

  @Test
  public void testLocalAliasOfEnumWithInstanceofCheck() {
    test(
        """
        /** @constructor */ var Enums = function() {};
        /** @enum { number } */
        Enums.Fruit = { APPLE: 1, BANANA: 2 };
        /** @constructor */ function foo(f) {
        if (f instanceof Enums) { alert('what?'); return; }
        var Fruit = Enums.Fruit;
        if (f == Fruit.APPLE) alert('apple');
        if (f == Fruit.BANANA) alert('banana'); }
        """,
        """
        /** @constructor */ var Enums = function() {};
        /** @enum {JSDocSerializer_placeholder_type} */
        Enums.Fruit = { APPLE: 1,BANANA: 2 };
        /** @constructor */ function foo(f) {
        if (f instanceof Enums) { alert('what?'); return; }
        var Fruit = null;
        if(f == Enums.Fruit.APPLE) alert('apple');
        if(f == Enums.Fruit.BANANA) alert('banana'); }
        """);
  }

  @Test
  public void testLocalAliasOfFunction() {
    test(
        """
        var a = function() {}
        a.b = 5;
        function f() { var x = a.b;
        f(x);
        }
        """,
        """
        var a = function() {}
        a.b = 5;
        function f() { var x = null;
        f(a.b);
        }
        """);
  }

  @Test
  public void testLocalWriteToNonAncestor() {
    test(
        """
        var a = { b: 3 }
        function f() {
          a.b = 5;
          var x = a;
          f(a.b);
        }
        """,
        """
        var a = { b: 3 }
        function f() {
          a.b = 5;
          var x = null;
          f(a.b);
        }
        """);
  }

  @Test
  public void testLocalAliasInChainedAssignment() {
    testSame("var a = { b: 3 }; function f() { var c; var d = c = a; a.b; d.b; }");
  }

  @Test
  public void testMisusedConstructorTag() {
    test(
        """
        var a = {}
        var d = a;
        a.b = function() {};
        a.b.c = 0;
        a.b.c;
        """,
        """
        var a = {}
        var d = null;
        a.b = function() {};
        a.b.c = 0;
        a.b.c;
        """);
  }

  @Test
  public void testNoInlineGetpropIntoCall() {
    test(
        """
        var b = x
        function f() {
          var a = b;
          a();
        }
        """,
        """
        var b = x
        function f() {
          var a = null;
          b()
        }
        """);
  }

  @Test
  public void testObjLitAssignmentDepth3() {
    test(
        """
        var a = {}
        a.b = {};
        a.b.c = { d: 1, e: 2 };
        var f = a.b.c.d;
        """,
        """
        var a = {}
        a.b = {};
        a.b.c = { d: 1,e: 2 };
        var f = null;
        """);

    test(
        """
        var a = {}
        a.b = {};
        a.b.c = { d: 1, e: 2 };
        var f = a.b.c.d;
        use(f);
        """,
        """
        var a = {}
        a.b = {};
        a.b.c = { d: 1,e: 2 };
        var f = null;
        use(a.b.c.d);
        """);

    test(
        """
        var a = {}
        a.b = {};
        a.b.c = { d: 1, e: 2 };
        var f = a.b.c.d;
        var g = a.b.c.e;
        """,
        """
        var a = {}
        a.b = {};
        a.b.c = { d: 1, e: 2 };
        var f = null;
        var g = null;
        """);

    test(
        """
        var a = {}
        a.b = {};
        a.b.c = { d: 1, e: 2 };
        var f = a.b.c.d;
        var g = a.b.c.e;
        use(f, g);
        """,
        """
        var a = {}
        a.b = {};
        a.b.c = { d: 1, e: 2 };
        var f = null;
        var g = null;
        use(a.b.c.d, a.b.c.e);
        """);

    testSame(
        """
        var a = {}
        a.b = {};
        a.b.c = { d: 1, e: 2 };
        var f = null;
        var g = null;
        """);
  }

  @Test
  public void testObjLitAssignmentDepth4() {
    test(
        """
        var a = {}
        a.b = {};
        a.b.c = {};
        a.b.c.d = { e: 1, f: 2 };
        var g = a.b.c.d.e;
        """,
        """
        var a = {}
        a.b = {};
        a.b.c = {};
        a.b.c.d = { e: 1, f: 2 };
        var g = null;
        """);

    test(
        """
        var a = {}
        a.b = {};
        a.b.c = {};
        a.b.c.d = { e: 1, f: 2 };
        var g = a.b.c.d.e;
        use(g);
        """,
        """
        var a = {}
        a.b = {};
        a.b.c = {};
        a.b.c.d = { e: 1, f: 2 };
        var g = null;
        use(a.b.c.d.e);
        """);
  }

  @Test
  public void testObjLitDeclaration() {
    test(
        """
        var a = { b: {}, c: {} }
        var d = a.b;
        var e = a.c;
        """,
        """
        var a = { b: {}, c: {} }
        var d = null;
        var e = null;
        """);

    test(
        """
        var a = { b: {}, c: {} }
        var d = a.b;
        var e = a.c;
        use(d, e);
        """,
        """
        var a = { b: {}, c: {} }
        var d = null;
        var e = null;
        use(a.b,a.c);
        """);

    test(
        """
        var a = { b: {}, c: {} }
        var d = a.b;
        var e = a.c;
        """,
        """
        var a = { b: {}, c: {} }
        var d = null;
        var e = null;
        """);

    test(
        """
        var a = { b: {}, c: {} }
        var d = a.b;
        var e = a.c;
        use(d, e);
        """,
        """
        var a = { b: {}, c: {} }
        var d = null;
        var e = null;
        use(a.b, a.c);
        """);
  }

  @Test
  public void testObjLitDeclarationUsedInSameVarList() {
    test(
        """
        var a = { b: {}, c: {} }
        var d = a.b;
        var e = a.c;
        use(d, e);
        """,
        """
        var a = { b: {}, c: {} }
        var d = null;
        var e = null;
        use(a.b, a.c)
        """);
  }

  @Test
  public void testObjLitDeclarationWithGet2() {
    test(
        """
        var a = { b: {}, get c() {} }
        var d = a.b;
        var e = a.c;
        """,
        """
        var a = { b: {}, get c() {} }
        var d = null;
        var e = a.c;
        """);

    test(
        """
        var a = { b: {}, get c() {} }
        var d = a.b;
        var e = a.c;
        use(d);
        """,
        """
        var a = { b: {}, get c() {} }
        var d = null;
        var e = a.c;
        use(a.b);
        """);
  }

  @Test
  public void testObjLitDeclarationWithSet2() {
    test(
        """
        var a = { b: {}, set c(a) {} }
        var d = a.b;
        var e = a.c
        """,
        """
        var a = { b: {}, set c(a$jscomp$1) {} }
        var d = null;
        var e = a.c;
        """);

    test(
        """
        var a = { b: {}, set c(a) {} }
        var d = a.b;
        var e = a.c;
        use(d);
        """,
        """
        var a = { b: {}, set c(a$jscomp$1) {} }
        var d = null;
        var e = a.c;
        use(a.b);
        """);
  }

  @Test
  public void testPropertyOfChildFuncOfUncollapsibleObjectDepth1() {
    test(
        """
        var a = {}
        var c = a;
        a.b = function () {};
        a.b.x = 0;
        a.b.x;
        """,
        """
        var a = {}
        var c = null;
        a.b = function() {};
        a.b.x = 0;
        a.b.x;
        """);

    test(
        """
        var a = {}
        var c = a;
        a.b = function () {};
        a.b.x = 0;
        a.b.x;
        use(c);
        """,
        """
        var a = {}
        var c = null;
        a.b = function() {};
        a.b.x = 0;
        a.b.x;
        use(a);
        """);
  }

  @Test
  public void testPropertyOfChildFuncOfUncollapsibleObjectDepth2() {
    test(
        """
        var a = {}
        a.b = {};
        var c = a.b;
        a.b.c = function () {};
        a.b.c.x = 0;
        a.b.c.x;
        """,
        """
        var a = {}
        a.b = {};
        var c = null;
        a.b.c = function() {};
        a.b.c.x = 0;
        a.b.c.x
        """);

    test(
        """
        var a = {}
        a.b = {};
        var c = a.b;
        a.b.c = function () {};
        a.b.c.x = 0;
        a.b.c.x;
        use(c);
        """,
        """
        var a = {}
        a.b = {};
        var c = null;
        a.b.c = function() {};
        a.b.c.x = 0;
        a.b.c.x;
        use(a.b)
        """);
  }

  @Test
  public void testTypeDefAlias1() {
    test(
        """
        var D = function() {}
        D.L = function() {};
        /** @type { D.L } */ D.L.A = new D.L();
        /** @const */ var M = {};
        /** @typedef { D.L } */ M.L = D.L;
        use(M.L.A);
        """,
        """
        var D = function() {}
        D.L = function() {};
         D.L.A = new D.L;
        /** @const */ var M = {};
        M.L = null;
        use(D.L.A);
        """);
  }

  @Test
  public void testGlobalAliasWithConst() {
    test("const a = {}; a.b = {}; const c = a.b;", "const a = {}; a.b = {}; const c = null");

    test(
        "const a = {}; a.b = {}; const c = a.b; use(c);",
        "const a = {}; a.b = {}; const c = null; use(a.b)");

    testSame("const a = {}; a.b;");
  }

  @Test
  public void testGlobalAliasWithLet1() {
    test("let a = {}; a.b = {}; let c = a.b;", "let a = {}; a.b = {}; let c = null");

    test(
        "let a = {}; a.b = {}; let c = a.b; use(c);",
        "let a = {}; a.b = {}; let c = null; use(a.b)");

    testSame("let a = {}; a.b;");

    test(
        "let a = {}; if (true) { a.b = 5; } let c = a.b; use(c);",
        "let a = {}; if (true) { a.b = 5; } let c = null; use(a.b);");
  }

  @Test
  public void testGlobalAliasWithLet2() {
    // Inlining ns is unsafe because ns2 may or may not be equal to ns.
    testSame(
        """
        let ns = {};
        ns.foo = 'bar';
        let ns2;
        if (true) {
          ns2 = ns;
        }
        use(ns2.foo);
        """);

    // In this case, it would be safe to inline ns, as long as all subsequent references
    // to ns2 are inside the if block, but the algorithm is not complex enough to know that.
    testSame(
        """
        let ns = {};
        ns.foo = 'bar';
        let ns2;
        if (true) {
          ns2 = ns;
          use(ns2.foo);
        }
        """);
  }

  @Test
  public void testLocalAliasWithLet1() {
    test(
        """
        var a = {};
        a.b = {};
        function f() {
          if (true) {
            let c = a.b;
            alert(c);
          }
        }
        """,
        """
        var a = {};
        a.b = {};
        function f() {
          if (true) {
            let c = null;
            alert(a.b);
          }
        }
        """);
  }

  @Test
  public void testLocalAliasWithLet2() {
    test(
        "var a = {}; a.b = {}; if (true) { let c = a.b;  alert(c); }",
        "var a = {}; a.b = {}; if (true) { let c = null;  alert(a.b); }");
  }

  @Test
  public void testLocalAliasWithLet3() {
    test(
        "let ns = {a: 1}; { let y = ns; use(y.a); }",
        "let ns = {a: 1}; { let y = null; use(ns.a); }");
  }

  @Test
  public void testLocalAliasInsideClass() {
    test(
        "var a = {x: 5}; class A { fn() { var b = a; use(b.x); } }",
        "var a = {x: 5}; class A { fn() { var b = null; use(a.x); } }");
  }

  @Test
  public void testGlobalClassAlias1() {
    test(
        "class A {} A.foo = 5; const B = A; use(B.foo);",
        "class A {} A.foo = 5; const B = null; use(A.foo)");
  }

  @Test
  public void testGlobalClassAlias2() {
    test(
        "class A { static fn() {} } const B = A; B.fn();",
        "class A { static fn() {} } const B = null; A.fn();");
  }

  @Test
  public void testGlobalClassAlias3() {
    test(
        "class A {} const B =    A; B.prototype.fn = () =>          5   ;",
        "class A {} const B = null; A.prototype.fn = () => { return 5; };");
  }

  @Test
  public void testDestructuringAlias1() {
    // CollapseProperties backs off on destructuring, so it's okay not to inline here.
    test(
        "var a = {x: 5}; var [b] = [a]; use(b.x);", //
        "var a = {x: 5}; var b; [b] = [a]; use(b.x);");
  }

  @Test
  public void testDestructuringAlias2() {
    testSame("var a = {x: 5}; const {b} = {b: a}; use(b.x);");
  }

  @Test
  public void testDestructuringArrayAlias() {
    testSame("var a = [5, 6]; const [b, c] = a; use(b);");
  }

  @Test
  public void testObjectRest_restingFromANamespace_isNotInlinable() {
    // TODO(nickreid): These might actually be inlinable.
    testSame("var a = {x: 5, y: 6}; const {...b} = a; use(b.y);");
    testSame("var a = {x: 5, y: 6}; const {x, ...b} = a; use(b.y);");
  }

  @Test
  public void testObjectSpread_spreadingInNamespaceDef_preventsInliningItsProps() {
    testSame("var a = {x: 5, y: 6}; var b = {...a}; use(b.z);");
    testSame("var a = {x: 5, y: 6, z: 7}; var b = {z: -7, ...a}; use(b.z);");
    testSame("var a = {x: 5, y: 6, z: 7}; var b = Object.assign({}, {z: -7}, a); use(b.z);");

    testSame("var a = {x: 5, y: 6, z: 7}; var b = {...a, z: -7}; use(b.z);");
    testSame("var a = {x: 5, y: 6, z: 7}; var b = Object.assign({}, a, {z: -7}); use(b.z);");
  }

  @Test
  public void testDefaultParamAlias() {
    test(
        "var a = {b: 5}; var b = a; function f(x=b) { alert(x.b); }",
        "var a = {b: 5}; var b = null; function f(x=a) { alert(x.b); }");
  }

  @Test
  public void testComputedPropertyNames() {
    // We don't support computed properties.
    testSame(
        """
        var foo = {['ba' + 'r']: {}};
        var foobar = foo.bar;
        foobar.baz = 5;
        use(foo.bar.baz);
        """);
  }

  @Test
  public void testAliasInTemplateString() {
    test(
        "const a = {b: 5}; const c = a; alert(`${c.b}`);",
        "const a = {b: 5}; const c = null; alert(`${a.b}`);");
  }

  @Test
  public void testClassStaticInheritance_method() {
    test(
        "class A { static s() {} } class B extends A {} const C = B; C.s();",
        "class A { static s() {} } class B extends A {} const C = null; A.s();");

    test(
        "class A { static s() {} } class B extends A {} B.s();",
        "class A { static s() {} } class B extends A {} A.s();");
    test(
        "class A {} A.s = function() {}; class B extends A {} B.s();",
        "class A {} A.s = function() {}; class B extends A {} A.s();");
  }

  @Test
  public void testClassStaticInheritance_methodsFromMultipleClasses() {
    // C is an alias of B
    test(
        """
        class A { static a() {} }
        class B extends A { static b() {} }
        const C = B;
        C.a();
        C.b()
        """,
        """
        class A { static a() {} }
        class B extends A { static b() {} }
        const C = null;
        A.a();
        B.b()
        """);

    // C is a subclass of A and B
    test(
        """
        class A { static a() {} }
        class B extends A { static b() {} }
        class C extends B {}
        C.a();
        C.b()
        """,
        """
        class A { static a() {} }
        class B extends A { static b() {} }
        class C extends B {}
        A.a();
        B.b()
        """);
  }

  @Test
  public void testClassStaticInheritance_methodWithNoCollapse() {
    // back off on replacing `B.s` -> `A.s` if A.s is not collapsible for two reasons:
    //  1. the main reason we do this replacing is to make collapsing safer
    //  2. people may use @nocollapse to avoid breaking static `this` refs, and if that were the
    //     case then inlining would also break those refs.
    testSame("class A { /** @nocollapse */ static s() {} } class B extends A {} B.s();");
  }

  @Test
  public void testChainedClassStaticInheritance_methodWithNoCollapse() {
    // verify we also don't replace C.s with B.s, and inherit the 'non-collapsibility' from 'A.s'
    testSame(
        """
        class A {/** @nocollapse */ static s() {}}
        class B extends A {}
        class C extends B {}
        C.s();
        """);
  }

  @Test
  public void testClassStaticInheritance_propertyAlias() {
    testSame("class A {} A.staticProp = 6; class B extends A {} let b = new B;");

    test(
        "class A {} A.staticProp = 6; class B extends A {} use(B.staticProp);",
        "class A {} A.staticProp = 6; class B extends A {} use(A.staticProp);");
  }

  @Test
  public void testClassStaticInheritance_classExpression() {
    test(
        "var A = class {}; A.staticProp = 6; var B = class extends A {}; use(B.staticProp);",
        "var A = class {}; A.staticProp = 6; var B = class extends A {}; use(A.staticProp);");

    test(
        "let A = class {}; A.staticProp = 6; let B = class extends A {}; use(B.staticProp);",
        "let A = class {}; A.staticProp = 6; let B = class extends A {}; use(A.staticProp);");

    test(
        "const A = class {}; A.staticProp = 6; const B = class extends A {}; use(B.staticProp);",
        "const A = class {}; A.staticProp = 6; const B = class extends A {}; use(A.staticProp);");
  }

  @Test
  public void testClassStaticInheritence_lateInitializedExpression() {
    testSame(
        "var A; A = class {}; A.staticProp = 6; var B = class extends A {}; use(B.staticProp);");
  }

  @Test
  public void testClassStaticInheritance_propertyWithSubproperty() {
    test(
        "class A {} A.ns = {foo: 'bar'}; class B extends A {} use(B.ns.foo);",
        "class A {} A.ns = {foo: 'bar'}; class B extends A {} use(A.ns.foo);");

    test(
        "class A {} A.ns = {foo: 'bar'}; class B extends A {} B.ns.foo = 'baz'; use(B.ns.foo);",
        "class A {} A.ns = {foo: 'bar'}; class B extends A {} A.ns.foo = 'baz'; use(A.ns.foo);");
  }

  @Test
  public void testClassStaticInheritance_propertyWithShadowing() {
    testSame("class A {} A.staticProp = 6; class B extends A {} B.staticProp = 7;");

    // Here, B.staticProp is a different property from A.staticProp, so don't rewrite.
    testSame(
        "class A {} A.staticProp = 6; class B extends A {} B.staticProp = 7; use(B.staticProp);");

    // At the time use() is called, B.staticProp is still the same as A.staticProp, so we
    // *could* rewrite it. But instead we back off because of the shadowing afterwards.
    testSame(
        "class A {} A.staticProp = 6; class B extends A {} use(B.staticProp); B.staticProp = 7;");
  }

  @Test
  public void testClassStaticInheritance_propertyMultiple() {
    test(
        "class A {} A.foo = 5; A.bar = 6; class B extends A {} use(B.foo); use(B.bar);",
        "class A {} A.foo = 5; A.bar = 6; class B extends A {} use(A.foo); use(A.bar);");

    testSame("class A {} A.foo = {bar: 5}; A.baz = 6; class B extends A {} B.baz = 7;");

    test(
        "class A {} A.foo = {}; A.baz = 6; class B extends A {}  B.foo.bar = 5; B.baz = 7;",
        "class A {} A.foo = {}; A.baz = 6; class B extends A {} A.foo.bar = 5; B.baz = 7;");
  }

  @Test
  public void testClassStaticInheritance_property_chainedSubclasses() {
    test(
        "class A {} A.foo = 5; class B extends A {} class C extends B {} use(C.foo);",
        "class A {} A.foo = 5; class B extends A {} class C extends B {} use(A.foo);");
  }

  @Test
  public void testClassStaticInheritance_namespacedClass() {
    test(
        """
        var ns1 = {}, ns2 = {};
        ns1.A = class {};
        ns1.A.staticProp = {foo: 'bar'};
        ns2.B = class extends ns1.A {}
        use(ns2.B.staticProp.bar);
        """,
        """
        var ns1 = {};
        var ns2 = {};
        ns1.A = class {};
        ns1.A.staticProp = {foo: 'bar'};
        ns2.B = class extends ns1.A {}
        use(ns1.A.staticProp.bar);
        """);
    test(
        """
        var ns1 = {}, ns2 = {};
        ns1.A = class {};
        ns1.A.staticProp = {foo: 'bar'};
        ns2.B = class extends ns1.A {}
        use(ns2.B.staticProp?.bar);
        """,
        """
        var ns1 = {};
        var ns2 = {};
        ns1.A = class {};
        ns1.A.staticProp = {foo: 'bar'};
        ns2.B = class extends ns1.A {}
        use(ns1.A.staticProp?.bar);
        """);
    testSame(
        """
        var ns1 = {};
        var ns2 = {};
        ns1.A = class {};
        ns1.A.staticProp = {foo: 'bar'};
        ns2.B = class extends ns1.A {}
        // ns2.B isn't an alias for ns1.A, so it isn't inlined.
        // ns2.B?.staticProp isn't an alias, because it's an optional chain.
        use(ns2.B?.staticProp.bar);
        """);
  }

  @Test
  public void testClassStaticInheritance_es5Class() {
    // ES6 classes do not inherit static properties of ES5 class constructors.
    testSame(
        """
        /** @constructor */
        function A() {}
        A.staticProp = 5;
        class B extends A {}
        use(B.staticProp);
        """); // undefined
  }

  @Test
  public void testClassStaticInheritance_cantDetermineSuperclass() {
    // Currently we only inline inherited properties when the extends clause contains a simple or
    // qualified name.
    test(
        """
        class A {}
        A.foo = 5;
        class B {}
        B.foo = 6;
        function getSuperclass() { return A; }
        class C extends getSuperclass() {}
        use(C.foo);
        """,
        """
        class A {}
        A.foo = 5;
        class B {}
        B.foo = 6;
        function getSuperclass() {
          return A;
        }
        const CLASS_EXTENDS$0 = getSuperclass();
        class C extends CLASS_EXTENDS$0 {}
        use(C.foo);
        """);
  }

  @Test
  public void testAliasInsideGenerator() {
    test(
        "const a = {b: 5}; const c = a;    function *f() { yield c.b; }",
        "const a = {b: 5}; const c = null; function *f() { yield a.b; }");
  }

  @Test
  public void testAliasInsideModuleScope() {
    // CollapseProperties currently only handles global variables, so we don't handle aliasing in
    // module bodies here.
    testSame("const a = {b: 5}; const c = a; export default function() {};");
  }

  @Test
  public void testAliasForSuperclassNamespace() {
    test(
        """
        var ns = {};
        class Foo {}
        ns.clazz = Foo;
        var Bar = class extends ns.clazz.Baz {}
        """,
        """
        var ns = {};
        class Foo {}
        ns.clazz = null;
        var Bar = class extends Foo.Baz {}
        """);

    test(
        """
        var ns = {};
        class Foo {}
        Foo.Builder = class {}
        ns.clazz = Foo;
        var Bar = class extends ns.clazz.Builder {}
        """,
        """
        var ns = {};
        class Foo {}
        Foo.Builder = class {}
        ns.clazz = null;
        var Bar = class extends Foo.Builder {}
        """);
  }

  @Test
  public void testAliasForSuperclass_withStaticInheritance() {
    test(
        """
        var ns = {};
        class Foo {}
        Foo.baz = 3;
        ns.clazz = Foo;
        var Bar = class extends ns.clazz {}
        use(Bar.baz);
        """,
        """
        var ns = {};
        class Foo {}
        Foo.baz = 3;
        ns.clazz = null;
        var Bar = class extends Foo {}
        use(Foo.baz);
        """);
  }

  @Test
  public void testStaticInheritance_superclassIsStaticProperty() {
    test(
        """
        class Foo {}
        Foo.Builder = class {}
        Foo.Builder.baz = 3;
        var Bar = class extends Foo.Builder {}
        use(Bar.baz);
        """,
        """
        class Foo {}
        Foo.Builder = class {}
        Foo.Builder.baz = 3;
        var Bar = class extends Foo.Builder {}
        use(Foo.Builder.baz);
        """);
  }

  @Test
  public void testAliasForSuperclassNamespace_withStaticInheritance() {
    test(
        """
        var ns = {};
        class Foo {}
        Foo.Builder = class {}
        Foo.Builder.baz = 3;
        ns.clazz = Foo;
        var Bar = class extends ns.clazz.Builder {}
        use(Bar.baz);
        """,
        """
        var ns = {};
        class Foo {}
        Foo.Builder = class {}
        Foo.Builder.baz = 3;
        ns.clazz = null;
        var Bar = class extends Foo.Builder {}
        use(Foo.Builder.baz);
        """);
  }

  @Test
  public void testGithubIssue2754() {
    testSame(
        """
        var ns = {};
        /** @constructor */
        ns.Bean = function() {}

        ns.Bean.x = function(a=null) {
          if (a == null || a === undefined) {
            a = ns.Bean;
          }
          return new a();
        }
        """);
  }

  @Test
  public void testParameterDefaultIsAlias() {
    testSame(
        """
        /** @constructor */
        function a() {}
        a.staticProp = 5;

        function f(param = a) {
          return new param();
        }
        """);
  }

  @Test
  public void testInliningPropertyAliasBeforeNamespace_withConstructorProperty() {
    test(
        """
        var prop = 1;
        /** @constructor */
        var Foo = function() {}

        Foo.prop = prop;

        var aliasOfFoo = Foo;
        alert(aliasOfFoo.prop);
        """,
        """
        var prop = 1;
        /** @constructor */
        var Foo = function() {}

        Foo.prop = null;

        var aliasOfFoo = null;
        alert(prop);
        """);
  }

  @Test
  public void testInliningPropertyAliasBeforeNamespace_withNonConstructorProperty() {
    // NOTE: the only difference between this input and the above input is that the
    // above has annotated Foo with @constructor. The @constructor case used to generate bad output
    test(
        """
        var prop = 1;
        /** @const */
        var Foo = {}

        Foo.prop = prop;

        var aliasOfFoo = Foo;
        alert(aliasOfFoo.prop);
        """,
        """
        var prop = 1;
        /** @const */
        var Foo = {};

        Foo.prop = null;

        var aliasOfFoo = null;
        alert(prop);
        """);
  }

  @Test
  public void testInliningPropertyAlias_onEscapedConstructor() {
    // This test demonstrates somewhat unsafe behavior in this pass that ends up working around
    // CollapseProperties behavior.
    //
    test(
        """
        var prop = 1;
        /** @constructor */
        var Foo = function() {}

        Foo.prop = prop;

        /** @constructor */
        function Bar() {}
        Bar.aliasOfFoo = Foo;
        use(Bar);
        alert(Bar.aliasOfFoo.prop);
        """,
        """
        var prop = 1;
        /** @constructor */
        var Foo = function() {}

        Foo.prop = prop;

        /** @constructor */
        function Bar() {}
        Bar.aliasOfFoo = Foo; // don't remove this initialization
        use(Bar); // this might write to Bar.aliasOfFoo.prop
        alert(prop);
        """); // but set this to prop anyway to fix CollapseProperties
  }

  @Test
  public void testAliasChain() {
    test(
        """
        var goog = {};
        goog.DEBUG = false;
        var foo = {};
        var global_DEBUG = goog.DEBUG;
        foo.DEBUG = global_DEBUG;
        alert(foo.DEBUG);
        """,
        """
        var goog = {};
        goog.DEBUG = false;
        var foo = {};
        var global_DEBUG = null;
        foo.DEBUG = null;
        alert(goog.DEBUG);
        """);
    test(
        """
        var goog = {};
        goog.DEBUG = false;
        var foo = {};
        var global_DEBUG = goog.DEBUG;
        foo.DEBUG = global_DEBUG;
        alert(foo?.DEBUG);
        """,
        """
        var goog = {};
        goog.DEBUG = false;
        var foo = {};
        var global_DEBUG = null;
        // Does not get inlined because of the optional chain reference
        foo.DEBUG = goog.DEBUG;
        alert(foo?.DEBUG);
        """);
  }

  @Test
  public void testTranspiledEs6StaticMethods_withNoCollapse() {
    // This is what transpiled ES6 class statics look like.
    // We don't replace "Child.f = Parent.f" with "Child.f = null" because of the @nocollapse
    disableCompareJsDoc(); // multistage compilation erases the @extends
    testSame(
        """
        /** @constructor */ var Parent = function() {};
        /** @nocollapse */ Parent.f = function() {};
        /** @constructor @extends {Parent} @param {...?} var_args  */
        var Child = function(var_args) {
          Parent.apply(this, arguments);
        }
        $jscomp.inherits(Child, Parent);
        /** @nocollapse */ Child.f = Parent.f;
        Child.prototype.g = function() { return this.f(); }
        """);
  }

  @Test
  public void testLoopInAliasChainWithTypedefConstructorProperty() {
    // This kind of code can get produced by module exports rewriting and was causing a crash in
    // AggressiveInlineAliases.
    disableCompareJsDoc(); // multistage compilation erases the @typedef
    testSame(
        """
        /** @constructor */ var Item = function() {};
        /** @typedef {number} */ Item.Models;
        Item.Models = Item.Models;
        """);
  }

  @Test
  public void testDontInlinePropertiesOnEscapedNamespace() {
    testSame(
        externs("function use(obj) {}"),
        srcs(
            """
            function Foo() {}
            Foo.Bar = {};
            Foo.Bar.baz = {A: 1, B: 2};

            var $jscomp$destructuring$var1 = Foo.Bar;
            // This call could potentially have changed the value of Foo.Bar, so don't replace
            // $jscomp$destructuring$var1.baz with Foo.Bar.baz
            use(Foo);
            var baz = $jscomp$destructuring$var1.baz;
            use(baz.A);
            """));
  }

  @Test
  public void testInlinePropertiesOnEscapedNamespace_withDeclaredType() {
    test(
        externs("function use(obj) {}"),
        srcs(
            """
            /** @constructor */
            function Foo() {}
            /** @constructor */
            Foo.Bar = function() {};
            /** @enum {number} */
            Foo.Bar.baz = {A: 1, B: 2};

            var $jscomp$destructuring$var1 = Foo.Bar;
            // This call could potentially have changed the value of Foo.Bar.
            use(Foo);
            var baz = $jscomp$destructuring$var1.baz;
            use(baz.A);
            """),
        expected(
            """
            /** @constructor */
            function Foo() {}
            /** @constructor */
            Foo.Bar = function() {};
            /** @enum {JSDocSerializer_placeholder_type} */
            Foo.Bar.baz = {A: 1, B: 2};

            var $jscomp$destructuring$var1 = null;
            use(Foo);
            var baz = null;
            // If we didn't unsafely replace baz.A with Foo.Bar.baz.A, this reference would
            // break after CollapseProperties runs because Foo.Bar.baz -> Foo$Bar$baz
            // So although inlining this is technically unsafe, because use(Foo) could have
            // changed the value of Foo.Bar, it actually fixes a breakage caused by
            // CollapseProperties.
            use(Foo.Bar.baz.A);
            """));
  }

  @Test
  public void testDontInlinePropertiesOnNamespace_withNoCollapse() {
    disableCompareJsDoc(); // multistage compilation removes the @enum type
    testSame(
        externs("function use(obj) {}"),
        srcs(
            """
            /** @constructor */
            function Foo() {}
            /** @constructor @nocollapse */
            Foo.Bar = function() {};
            /** @enum {number} @nocollapse */
            Foo.Bar.baz = {A: 1, B: 2};

            var $jscomp$destructuring$var1 = Foo.Bar;
            use(Foo);
            var baz = $jscomp$destructuring$var1.baz;
            use(baz.A);
            """));
  }

  @Test
  public void testNestedAssignWithAlias() {
    test(
        """
        var ns = {a: {}};
        var Letters = { B: 'b'};
        ns.c = ns.a.b = Letters.B;
        use(ns.c);
        use(ns.a.b);
        """,
        """
        var ns = {a: {}};
        var Letters = {B: 'b'};
        // test that we handle nested assigns correctly
        ns.c = null;
        use(Letters.B);
        use(Letters.B);
        """);
  }

  @Test
  public void testCommaExpression() {
    test(
        """
        /** @const */ var exports = {};
        /** @const @enum {string} */ var Letters = {
          A: 'a',
          B: 'b'};
        exports.A = Letters.A, exports.B = Letters.B;
        use(exports.B);
        """,
        """
        /** @const */ var exports = {};
        /** @const @enum {JSDocSerializer_placeholder_type} */ var Letters = {
          A: 'a',
          B: 'b'};
        // this used to become
        //   exports.A = null, Letters.B = null;
        // breaking all references to Letters.B
        // GlobalNamespace treats 'exports.B' as both a read and write of
        // 'exports.B', and AggressiveInlineAliases used to replace all reads of exports.B with
        // `Letters.B` without verifying that the read was not also a write.
        Letters.A, Letters.B;
        use(Letters.B);
        """);
  }

  @Test
  public void testDontInlineDestructuredAliasProp_quoted() {
    testSame("var a = {x: 2}; var b = {}; b['y'] = a.x; const {'y': y} = b; use(y);");
  }

  @Test
  public void testInlineDestructuredAliasProp() {
    test(
        "var a = {x: 2}; var b = {}; b.y =  a.x; const {y} =    b; use( y );",
        "var a = {x: 2}; var b = {}; b.y = null; const  y  = null; use(a.x);");
  }

  @Test
  public void testInlineDestructuredAliasPropWithKeyBefore() {
    test(
        "var a = {x: 2}; var b = {z: 3}; b.y = a.x; b.z = 4; const {z, y} = b; use(y + z);",
        """
        var a = {x: 2};
        var b = {z: 3};
        b.y = null;
        b.z = 4;
        const z = b.z;
        const y = null;
        use(a.x + z);
        """);
  }

  @Test
  public void testInlineDestructuredAliasPropWithKeyAfter() {
    test(
        "var a = {x: 2}; var b = {z: 3}; b.y = a.x; b.z = 4; const {y, z} = b; use(y + z);",
        """
        var a = {x: 2};
        var b = {z: 3};
        b.y = null;
        b.z = 4;
        const y = null;
        const z = b.z;
        use(a.x + z);
        """);
  }

  @Test
  public void testInlineDestructuredAliasPropWithKeyBeforeAndAfter() {
    test(
        """
        var a = {x: 2};
        var b = {z: 3};
        b.y = a.x;
        b.z = 4; // add second assign so that this won't get inlined
        const {x, y, z} = b;
        use(y + z);
        """,
        """
        var a = {x: 2};
        var b = {z: 3};
        b.y = null;
        b.z = 4;
        const x = b.x;
        const y = null;
        const z = b.z;
        use(a.x + z);
        """);
  }

  @Test
  public void testDestructuredPropAccessInAssignWithKeyBefore() {
    testSame(
        """
        var a = {x: 2};
        var b = {};
        b.y = a.x;
        var obj = {};
        ({missing: obj.foo, y: obj.foo} = b);
        use(obj.foo);
        """);
  }

  @Test
  public void testDestructuredPropAccessInAssignWithKeyAfter() {
    testSame(
        """
        var a = {x: 2};
        var b = {};
        b.y = a.x;
        var obj = {};
        ({y: obj.foo, missing: obj.foo} = b);
        use(obj.foo);
        """);
  }

  @Test
  public void testDestructuredPropAccessInDeclarationWithDefault() {
    testSame(
        """
        var a = {x: {}};
        var b = {};
        b.y = a.x;
        const {y = 0} = b;
        use(y);
        """);
  }

  @Test
  public void testDestructuringPropertyOnAliasedNamespace() {
    // We can inline a part of a getprop chain on the rhs of a destructuring pattern:
    //   replace 'alias -> a.b' in 'const {c} = alias.Enum;'
    test(
        """
        const a = {};
        /** @const */ a.b = {};
        /** @enum {string} */ a.b.Enum = {c: 'c'};

        const alias = a.b;
        function f() { const {c} = alias.Enum; use(c); }
        """,
        """
        const a = {};
        /** @const */ a.b = {};
        /** @enum {JSDocSerializer_placeholder_type} */ a.b.Enum = {c: 'c'};

        const alias = null;
        function f() { const c = null; use(a.b.Enum.c); }
        """);
  }

  @Test
  public void testReplaceSuperGetPropInStaticMethod() {
    test(
        """
        class Foo {
          static m() {}
        }
        class Bar extends Foo {
          static m() {
            super.m();
          }
        }
        """,
        """
        class Foo {
          static m() {}
        }
        class Bar extends Foo {
          static m() {
            Foo.m();
          }
        }
        """);
  }

  @Test
  public void testReplaceSuperInArrowInStaticMethod() {
    test(
        """
        class Foo {
          static m() {}
        }
        class Bar extends Foo {
          static m() {
            return () => super.m();
          }
        }
        """,
        """
        class Foo {
          static m() {}
        }
        class Bar extends Foo {
          static m() {
            return () => {
              return Foo.m();
            };
          }
        }
        """);
  }

  @Test
  public void testReplaceSuperInStaticMethodWithQualifiedNameSuperclass() {
    test(
        """
        const a = {b:{}};
        /** @const */
        a.b.Foo = class {
          static m() {}
        };
        class Bar extends a.b.Foo {
          static m() {
            super.m();
          }
        }
        """,
        """
        const a = {b:{}};
        /** @const */
        a.b.Foo = class {
          static m() {}
        };
        class Bar extends a.b.Foo {
          static m() {
            a.b.Foo.m();
          }
        }
        """);
  }

  @Test
  public void testDontReplaceSuperInObjectLiteralMethod() {
    testSame("var obj = {m() { super.n(); } };");
  }

  @Test
  public void testDontReplaceSuperInObjectLitFnInStaticClassMethod() {
    testSame(
        """
        class Foo { static m() {} }
        // `super.n` refers to a different object than `Foo.n`
        class Bar extends Foo { static m() { return {m() { super.n(); }}; } }
        """);
  }

  @Test
  public void testDestructuredClassAlias() {
    test(
        """
        const ns = {};
        ns.Foo = class {};
        ns.Foo.STR = '';
        const {Foo} = ns;
        foo(Foo.STR);
        """,
        """
        const ns = {};
        ns.Foo = class {};
        ns.Foo.STR = '';
        const Foo = null;
        foo(ns.Foo.STR);
        """);
  }

  @Test
  public void testReplaceChainedSuperRefInStaticMethod() {
    test(
        """
        class Foo {
          static m() {}
        }
        class Bar extends Foo {}
        class Baz extends Bar {
          static m() {
            super.m();
          }
        }
        """,
        """
        class Foo {
          static m() {}
        }
        class Bar extends Foo {}
        class Baz extends Bar {
          static m() {
            Foo.m();
          }
        }
        """);
  }

  @Test
  public void testReplaceSuperInStaticMethodWithNonQnameSuperclass() {
    test(
        """
        class Bar extends getClass() {
          static m() {
            super.m();
          }
        }
        """,
        """
        const CLASS_EXTENDS$0 = getClass();
        class Bar extends CLASS_EXTENDS$0 {
          static m() {
            CLASS_EXTENDS$0.m();
          }
        }
        """);
  }

  @Test
  public void testReplaceSuperInStaticMethodWithNonQnameGetPropSuperclass() {
    test(
        """
        class Bar extends getClasses().Foo {
          static m() {
            super.m();
          }
        }
        """,
        """
        const CLASS_EXTENDS$0 = getClasses().Foo;
        class Bar extends CLASS_EXTENDS$0 {
          static m() {
            CLASS_EXTENDS$0.m();
          }
        }
        """);
  }

  @Test
  public void testReplaceSuperGetElemInStaticMethod() {
    // while CollapseProperties won't collapse Foo['m'], replacing `super` enables collapsing Bar.m
    // (since CollapseProperties cannot collapse methods using super) and helps code size.
    test(
        """
        class Foo {
          static 'm'() {}
        }
        class Bar extends Foo {
          static m() {
            super['m']();
          }
        }
        """,
        """
        class Foo {
          static ["m"]() {}
        }
        class Bar extends Foo {
          static m() {
            Foo["m"]();
          }
        }
        """);
  }

  @Test
  public void testDontReplaceSuperInClassPrototypeMethod() {
    testSame(
        """
        class Foo {
          m() {}
        }
        class Bar extends Foo {
          m() {
            super.m();
          }
        }
        """);
  }

  /**
   * To ensure that as we modify the AST, the GlobalNamespace stays up-to-date, we do a consistency
   * check after every unit test.
   *
   * <p>This check compares the names in the global namespace in the pass with a freshly-created
   * global namespace.
   */
  public void validateGlobalNamespace(GlobalNamespace passGlobalNamespace) {
    GlobalNamespace expectedGlobalNamespace =
        new GlobalNamespace(getLastCompiler(), getLastCompiler().getJsRoot());

    // GlobalNamespace (understandably) does not override equals. It would be silly to put it in
    // a datastructure. Neither does GlobalNamespace.Name (which probably could?)
    // So to compare equality: we verify that
    //  1. the two namespaces have the same qualified names, bar extern names
    //  2. each name has the same number of references in both namespaces
    //  3. each name has the same computed `Inlinability`
    for (Name expectedName : expectedGlobalNamespace.getNameForest()) {
      if (expectedName.inExterns()) {
        continue;
      }
      String fullName = expectedName.getFullName();
      Name actualName = passGlobalNamespace.getSlot(expectedName.getFullName());
      assertWithMessage(fullName).that(actualName).isNotNull();

      assertWithMessage(fullName)
          .that(actualName.getAliasingGets())
          .isEqualTo(expectedName.getAliasingGets());
      assertWithMessage(fullName)
          .that(actualName.getSubclassingGets())
          .isEqualTo(expectedName.getSubclassingGets());
      assertWithMessage(fullName)
          .that(actualName.getLocalSets())
          .isEqualTo(expectedName.getLocalSets());
      assertWithMessage(fullName)
          .that(actualName.getGlobalSets())
          .isEqualTo(expectedName.getGlobalSets());
      assertWithMessage(fullName)
          .that(actualName.getDeleteProps())
          .isEqualTo(expectedName.getDeleteProps());
      assertWithMessage(fullName)
          .that(actualName.getCallGets())
          .isEqualTo(expectedName.getCallGets());
      assertWithMessage("%s: canCollapseOrInline()", fullName)
          .that(actualName.canCollapseOrInline())
          .isEqualTo(expectedName.canCollapseOrInline());
      assertWithMessage("%s: canCollapseOrInlineChildNames()", fullName)
          .that(actualName.canCollapseOrInlineChildNames())
          .isEqualTo(expectedName.canCollapseOrInlineChildNames());
    }
    // Verify that no names in the actual name forest are not present in the expected name forest
    for (Name actualName : passGlobalNamespace.getNameForest()) {
      String actualFullName = actualName.getFullName();
      assertWithMessage(actualFullName)
          .that(expectedGlobalNamespace.getSlot(actualFullName))
          .isNotNull();
    }
  }
}
