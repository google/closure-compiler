/*
 * Copyright 2006 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.InlineAndCollapseProperties.PARTIAL_NAMESPACE_WARNING;
import static com.google.javascript.jscomp.InlineAndCollapseProperties.RECEIVER_AFFECTED_BY_COLLAPSE;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.ChunkOutputType;
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.testing.JSChunkGraphBuilder;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.testing.NodeSubject;
import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link InlineAndCollapseProperties}. */
@RunWith(JUnit4.class)
public final class InlineAndCollapsePropertiesTest extends CompilerTestCase {

  private static final String EXTERNS =
      """
      var window;
      function alert(s) {}
      function parseInt(s) {}
      /** @constructor */ function String() {};
      var arguments
      """;

  public InlineAndCollapsePropertiesTest() {
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
                InlineAndCollapseProperties.builder(comp)
                    .setPropertyCollapseLevel(PropertyCollapseLevel.ALL)
                    .setChunkOutputType(ChunkOutputType.GLOBAL_NAMESPACE)
                    .setHaveModulesBeenRewritten(false)
                    .setModuleResolutionMode(ResolutionMode.BROWSER)
                    .build()));
    return optimizer;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableNormalize();
    // TODO(bradfordcsmith): Stop normalizing the expected output or document why it is necessary.
    enableNormalizeExpectedOutput();
    disableCompareJsDoc();
    setGenericNameReplacements(Es6NormalizeClasses.GENERIC_NAME_REPLACEMENTS);
  }

  @Test
  public void testTs52OutputChange() {
    test(
        srcs(
            """
            var alias;
            var module$exports$C = class {
              method1() {
                return alias.staticPropOnC;
              }
              method2() {
                return alias.staticPropOnAlias;
              }
            };
            alias = module$exports$C;
            (() => {
              alias.staticPropOnAlias = 1;
            })();
            module$exports$C.staticPropOnC = 2;
            """),
        expected(
            """
            var alias;
            var module$exports$C = class {
              method1() {
                return module$exports$C$staticPropOnC;
              }
              method2() {
                return module$exports$C$staticPropOnAlias;
              }
            };
            var module$exports$C$staticPropOnAlias;
            alias = null;
            (() => {
              module$exports$C$staticPropOnAlias = 1;
            })();
            var module$exports$C$staticPropOnC = 2;
            """));
  }

  @Test
  public void testTs52OutputChangeVariableReassignment() {
    // This test case was created by executing
    // AdvancedOptimizationsIntegrationTest#testTSVariableReassignmentAndAliasingDueToDecoration
    // with `options.setPrintSourceAfterEachPass(true)`.
    // The input source here is how the source code looks at the point just before
    // InlineAndCollapseProperties runs in that test case except for the addition of one line of
    // JSDoc to make the unit test recognize a function declaration as a class declaration.
    test(
        externs(
            ImmutableList.of(
                new TestExternsBuilder()
                    .addObject()
                    .addConsole()
                    .addClosureExterns()
                    .addExtra(
                        // simulate "const tslib_1 = goog.require('tslib');",
                        """
                        var tslib_1 = {
                          __decorate: function(decorators, clazz) {}
                        };
                        """)
                    .buildExternsFile("externs.js"))),
        srcs(
            """
            var module$exports$main = {};
            var module$contents$main_module =
                module$contents$main_module || {id: 'main.ts'};
            var module$contents$main_Foo_1;
            function module$contents$main_noopDecorator(arg) {
              return arg;
            }
            // This JSDoc annotation makes this unit test recognize the declaration as a
            // class definition.
            /** @constructor */
            var i0$classdecl$var0 = function() {};
            i0$classdecl$var0.foo = function() {
              console.log('Hello');
            };
            i0$classdecl$var0.prototype.bar = function() {
              module$contents$main_Foo_1.foo();
              console.log('ID: ' + module$contents$main_Foo_1.ID + '');
            };
            var module$contents$main_Foo = module$contents$main_Foo_1 = i0$classdecl$var0;
            module$contents$main_Foo.ID = 'original';
            module$contents$main_Foo.ID2 = module$contents$main_Foo_1.ID;
            (function() {
            module$contents$main_Foo_1.foo();
            console.log('ID: ' + module$contents$main_Foo_1.ID + '');
            })();
            module$contents$main_Foo = module$contents$main_Foo_1 = tslib_1.__decorate(
                [module$contents$main_noopDecorator], module$contents$main_Foo);
            if (false) {
              module$contents$main_Foo.ID;
              module$contents$main_Foo.ID2;
            }
            (new module$contents$main_Foo()).bar();
            """),
        expected(
            """
            var module$contents$main_module =
                module$contents$main_module || {id: 'main.ts'};
            var module$contents$main_Foo_1;
            function module$contents$main_noopDecorator(arg) {
              return arg;
            }
            /** @constructor */
            var i0$classdecl$var0 = function() {};
            // TODO : b/299055739 - This property collapse breaks the output code.
            var i0$classdecl$var0$foo = function() {
            // "i0$classdecl$var0.foo = function() {", // this is the correct line
              console.log('Hello');
            };
            i0$classdecl$var0.prototype.bar = function() {
            // This reference to foo() is broken.
              module$contents$main_Foo_1.foo();
              console.log('ID: ' + module$contents$main_Foo_1.ID + '');
            };
            var module$contents$main_Foo = module$contents$main_Foo_1 = i0$classdecl$var0;
            module$contents$main_Foo.ID = 'original';
            module$contents$main_Foo.ID2 = module$contents$main_Foo_1.ID;
            (function() {
            module$contents$main_Foo_1.foo();
            console.log('ID: ' + module$contents$main_Foo_1.ID + '');
            })();
            module$contents$main_Foo = module$contents$main_Foo_1 = tslib_1.__decorate(
                [module$contents$main_noopDecorator], module$contents$main_Foo);
            if (false) {
              module$contents$main_Foo.ID;
              module$contents$main_Foo.ID2;
            }
            (new module$contents$main_Foo()).bar();
            """));
  }

  @Test
  public void testDoNotCollapseDeletedProperty() {
    testSame(
        srcs(
            """
            const global = window;
            delete global.HTMLElement;
            global.HTMLElement = (class {});
            """));
  }

  @Test
  public void testConstObjRefInTemplateLiteralComputedPropKey() {
    test(
        srcs(
            """
            var module$name = {}
            module$name.cssClasses = {
              CLASS_A: 'class-a',
            };

            module$name.oldCssClassesMap = {
              [`${module$name.cssClasses.CLASS_A}`]: 'old-class-a',
            };
            """),
        expected(
            """
            var module$name$cssClasses$CLASS_A = 'class-a';
            var module$name$oldCssClassesMap = {
              [`${module$name$cssClasses$CLASS_A}`]:'old-class-a'
            };
            """));
  }

  @Test
  public void testObjLitSpread() {
    test(
        """
        const other = {bar: 'some' };
        let ns = {}
        ns.foo = { ...other };
        use(ns.foo.bar);
        """,
        """
        const other = {bar: 'some' };
        var ns$foo = { ...other };
        use(ns$foo.bar);
        """);
  }

  // same behavior with transpiled version of above test
  @Test
  public void testObjAssign() {
    test(
        """
        const other = {bar: 'some' };
        let ns = {}
        ns.foo = Object.assign({}, other);
        use(ns.foo.bar);
        """,
        """
        const other = {bar: 'some' };
        var ns$foo = Object.assign({}, other);
        use(ns$foo.bar);
        """);
  }

  @Test
  public void testObjLitSpread_twoSpreads() {
    test(
        """
        const other = {bar: 'bar' };
        const another = {baz: 'baz' };
        let ns = {};
        ns.foo = { ...other, ...another };
        use(ns.foo.bar, ns.foo.baz);
        """,
        """
        const other = {bar: 'bar' };
        const another = {baz : 'baz'};
        var ns$foo = { ...other, ...another };
        use(ns$foo.bar, ns$foo.baz);
        """);
  }

  // same behavior with transpiled version of above test
  @Test
  public void testObjAssign_twoSpreads() {
    test(
        """
        const other = {bar: 'bar'};
        const another = {baz: 'baz'};
        let ns = {};
        ns.foo = Object.assign({}, other, another);
        use(ns.foo.bar, ns.foo.baz);
        """,
        """
        const other = {bar: 'bar'};
        const another = {baz : 'baz'};
        var ns$foo = Object.assign({}, other, another);
        use(ns$foo.bar, ns$foo.baz);
        """);
  }

  @Test
  public void testObjLitSpread_withNormalPropAfter() {
    test(
        """
        const other = {bar: 'some' };
        let ns = {}
        ns.foo = { ...other, prop : 0};
        use(ns.foo.bar, ns.foo.prop);
        """,
        """
        const other = {bar: 'some' };
        var ns$foo$prop = 0;
        var ns$foo = { ...other};
        use(ns$foo.bar, ns$foo$prop);
        """);
  }

  @Test
  public void testObjAssign_withNormalPropAfter() {
    test(
        """
        const other = {bar: 'some' };
        let ns = {}
        ns.foo = Object.assign({}, other, {prop : 0});
        use(ns.foo.bar, ns.foo.prop);
        """,
        """
        const other = {bar: 'some' };
        var ns$foo = Object.assign({}, other, {prop:0});
        use(ns$foo.bar, ns$foo.prop);
        """); // both properties not collapsed.
  }

  @Test
  public void testObjLitSpread_chained() {
    test(
        """
        const other = {bar: 'some' };
        let ns = {...other}
        let ns2 = { ...ns };
        use(ns2.bar);
        """,
        """
        const other = {bar: 'some' };
        let ns = { ...other };
        let ns2 = { ...ns };
        use(ns2.bar);
        """);
  }

  // same behavior with transpiled version of above test
  @Test
  public void testObjAssign_chained() {
    test(
        """
        const other = {bar: 'some' };
        let ns = Object.assign({}, other);
        let ns2 = Object.assign({}, ns);
        use(ns2.bar);
        """,
        """
        const other = {bar: 'some' };
        let ns = Object.assign({}, other);
        let ns2 = Object.assign({}, ns);
        use(ns2.bar);
        """);
  }

  @Test
  public void testObjLitSpread_chainedWithGetProp() {
    test(
        """
        const other = {bar: 'some' };
        let ns = {...other};
        let ns2 = {};
        ns2.foo = { ...ns };
        use(ns2.foo.bar);
        """,
        """
        const other = {bar: 'some' };
        let ns = { ...other };
        var ns2$foo = { ...ns };
        use(ns2$foo.bar);
        """);
  }

  // same behavior with transpiled version of above test
  @Test
  public void testObjAssign_chainedWithGetProp() {
    test(
        """
        const other = {bar: 'some' };
        let ns = Object.assign({}, other);
        let ns2 = {};
        ns2.foo = Object.assign({}, ns);
        use(ns2.foo.bar);
        """,
        """
        const other = {bar: 'some' };
        let ns = Object.assign({}, other);
        var ns2$foo = Object.assign({}, ns);
        use(ns2$foo.bar);
        """);
  }

  @Test
  public void testGitHubIssue3733() {
    testSame(
        srcs(
            """
            const X = {Y: 1};

            function fn(a) {
              if (a) {
            // Before issue #3733 was fixed GlobalNamespace failed to see this reference
            // as creating an alias for X due to a switch statement that failed to check
            // for the RETURN node type, so X.Y was incorrectly collapsed.
                return a ? X : {};
              }
            }

            console.log(fn(true).Y);
            """));
  }

  @Test
  public void testCollapse() {
    test(
        "var a = {}; a.b = {}; var c = a.b;", //
        "var c = null");

    test(
        "var a = {}; a.b = {}; var c = a.b; use(c);", //
        "var a$b = {}; var c = null; use(a$b);");

    testSame("var a = {}; /** @nocollapse */ a.b;");
  }

  @Test
  public void testCollapseKeepsSourceInfoForAliases() {
    test(
        """
        var a = {};
        a.use = function(arg) {};
        a.b = {};
        var c = a.b;
        a.use(c);
        """,
        """
        var a$use = function (arg) { };
        var a$b = {};
        var c = null;
        a$use(a$b);
        """);

    final Node scriptNode =
        getLastCompiler()
            .getRoot()
            .getLastChild() // sources root
            .getOnlyChild(); // only one source file
    assertNode(scriptNode).isScript().hasXChildren(4);
    final Node statement1 = scriptNode.getFirstChild();
    final Node statement2 = statement1.getNext();
    final Node statement3 = statement2.getNext();
    final Node statement4 = statement3.getNext();
    final String scriptName = scriptNode.getSourceFileName();

    // Original line 2: `a.use = function(arg) {};`
    // Compiled line 1: `var a$use = function(arg) {};`
    assertNode(statement1)
        .isVar()
        .hasSourceFileName(scriptName)
        .hasLineno(2)
        .hasCharno(0)
        .hasOneChildThat() // `a$use = function(arg) {};
        .isName("a$use")
        .hasSourceFileName(scriptName)
        .hasLineno(2)
        .hasCharno(0) // `a.use = ` was the beginning of the line
        .hasOneChildThat() // `function(arg) {}`
        .isFunction()
        .hasSourceFileName(scriptName)
        .hasLineno(2)
        .hasCharno(8); // `a.use = ` 8 chars

    // Original line 3: `a.b = {};`
    // Compiled line 2: `var a$b = {};`
    assertNode(statement2)
        .isVar()
        .hasSourceFileName(scriptName)
        .hasLineno(3)
        .hasCharno(0)
        .hasOneChildThat() // a$b = {}
        .isName("a$b")
        .hasSourceFileName(scriptName)
        .hasLineno(3)
        .hasCharno(0)
        .hasOneChildThat() // {}
        .isObjectLit()
        .hasSourceFileName(scriptName)
        .hasLineno(3)
        .hasCharno(6);

    // Original line 4: `var c = a.b;`
    // Compiled line 3: `var c = null;`
    assertNode(statement3)
        .isVar()
        .hasSourceFileName(scriptName)
        .hasLineno(4)
        .hasCharno(0)
        .hasOneChildThat() // c = a.b
        .isName("c")
        .hasSourceFileName(scriptName)
        .hasLineno(4)
        .hasCharno(4)
        .hasOneChildThat() // null
        .isNullNode()
        .hasSourceFileName(scriptName)
        .hasLineno(4)
        .hasCharno(10);

    // Original line 5: `a.use(c);`
    // Compiled line 4: `a$use(a$b);`
    final NodeSubject callNodeSubject =
        assertNode(statement4)
            .isExprResult()
            .hasSourceFileName(scriptName)
            .hasLineno(5)
            .hasCharno(0)
            .hasOneChildThat() // a.use(c)
            .isCall()
            .hasSourceFileName(scriptName)
            .hasLineno(5)
            .hasCharno(0);

    // `a$use` from `a.use(c);`
    callNodeSubject
        .hasFirstChildThat()
        .isName("a$use")
        .hasSourceFileName(scriptName)
        .hasLineno(5)
        .hasCharno(2); // source info points to the `use` part of `a.use`

    // Original line 5: `c` in `a.use(c)`
    // Compiled line 4: `a$b` in `a$use(a$b)`
    callNodeSubject
        .hasSecondChildThat()
        .isName("a$b")
        .hasSourceFileName(scriptName)
        .hasLineno(5)
        .hasCharno(6); // `a.use(` is 6 chars
  }

  @Test
  public void testOptChainPreventsInlineAndCollapse() {
    testSame(
        """
        var a = {};
        a.b = {};
        // c is not really an alias due to optional chain,
        // and optional chain prevents collapse of a.b.
        var c = a?.b;
        use(c);
        """);
    test(
        """
        var a = {};
        a.b = {};
        var b = a.b; // can be inlined and collapsed
        b.c = {};
        var c = b?.c; // opt chain prevents both
        use(c);
        """,
        """
        var a$b = {};
        var b = null;
        a$b.c = {};
        var c = a$b?.c;
        use(c);
        """);
    test(
        """
        const a = {};
        a.b = {};
        a.b.c = {};
        const {b} = a; // can be inlined and collapsed
        const c = b?.c; // opt chain prevents both
        use(c);
        """,
        """
        var a$b = {};
        a$b.c = {};
        const b = null;
        const c = a$b?.c;
        use(c);
        """);
    test(
        """
        const ns = {};
        ns.Y = {};
        ns.Y.prop = 3;
        const {Y} = ns;
        use(Y);
        """,
        """
        var ns$Y = {};
        ns$Y.prop = 3;
        const Y = null;
        use(ns$Y);
        """);
    test(
        """
        var a = {};
        a.b = {};
        a.b.c = {};
        var c = a.b?.c;
        use(c);
        """,
        """
        var a$b = {};
        a$b.c = {};
        var c = a$b?.c;
        use(c);
        """);
    test(
        """
        const ns = {};
        /** @constructor */
        ns.Y = function() {};
        ns.Y.prop = 3;
        const {Y} = ns;
        use(Y);
        """,
        """
        /** @constructor */
        var ns$Y = function() {};
        var ns$Y$prop = 3;
        const Y = null;
        use(ns$Y);
        """);
    test(
        """
        const ns = {};
        /** @constructor */
        ns.Y = function() {};
        ns.Y.prop = 3;
        const {Y} = ns;
        use(Y?.prop);
        """,
        """
        /** @constructor */
        var ns$Y = function() {};
        var ns$Y$prop = 3;
        const Y = null;
        use(ns$Y?.prop);
        """);
  }

  @Test
  public void testObjLitDeclaration() {
    test(
        "var a = {b: {}, c: {}}; var d = a.b; var e = a.c", //
        "var d = null; var e = null;");

    test(
        "var a = {b: {}, c: {}}; var d = a.b; var e = a.c; use(d, e);", //
        "var a$b = {}; var a$c = {}; var d = null; var e = null; use(a$b, a$c);");

    test(
        "var a = {b: {}, /** @nocollapse */ c: {}}; var d = a.b; var e = a.c", //
        "var a = {/** @nocollapse */ c: {}}; var d = null; var e = null;");

    test(
        "var a = {b: {}, /** @nocollapse */ c: {}}; var d = a.b; var e = a.c; use(d, e);", //
        "var a$b = {};var a = {/** @nocollapse */ c: {}};var d = null;var e = null;use(a$b, a.c);");
  }

  @Test
  public void testObjLitDeclarationWithGet1() {
    testSame("var a = {get b(){}};");
  }

  @Test
  public void testObjLitDeclarationWithGet2() {
    test(
        "var a = {b: {}, get c(){}}; var d = a.b; var e = a.c;", //
        "var a = {get c() {}}; var d=null; var e = a.c");

    test(
        "var a = {b: {}, get c(){}}; var d = a.b; var e = a.c; use(d);", //
        "var a$b = {};var a = {get c(){}};var d = null; var e = a.c; use(a$b)");
  }

  @Test
  public void testObjLitDeclarationWithGet3() {
    test(
        "var a = {b: {get c() { return 3; }}};", //
        "var a$b = {get c() { return 3; }};");
  }

  @Test
  public void testObjLitDeclarationWithSet1() {
    testSame("var a = {set b(a){}};");
  }

  @Test
  public void testObjLitDeclarationWithSet2() {
    test(
        "var a = {b: {}, set c(a){}}; var d = a.b; var e = a.c", //
        "var a = {set c(a$jscomp$1){}}; var d=null; var e = a.c");

    test(
        "var a = {b: {}, set c(a){}}; var d = a.b; var e = a.c; use(d);", //
        "var a$b = {}; var a = {set c(a$jscomp$1){}}; var d=null; var e = a.c; use(a$b);");
  }

  @Test
  public void testObjLitDeclarationWithSet3() {
    test(
        "var a = {b: {set c(d) {}}};", //
        "var a$b = {set c(d) {}};");
  }

  @Test
  public void testObjLitDeclarationWithGetAndSet1() {
    test(
        "var a = {b: {get c() { return 3; },set c(d) {}}};", //
        "var a$b = {get c() { return 3; },set c(d) {}};");
  }

  @Test
  public void testObjLitAssignmentDepth3() {
    test(
        "var a = {}; a.b = {}; a.b.c = {d: 1, e: 2}; var f = a.b.c.d", //
        "var a$b$c$d = 1; var a$b$c$e = 2; var f = null");

    test(
        "var a = {}; a.b = {}; a.b.c = {d: 1, e: 2}; var f = a.b.c.d; use(f);", //
        "var a$b$c$d = 1; var a$b$c$e = 2; var f = null; use(a$b$c$d);");

    test(
        """
        var a = {}; a.b = {}; a.b.c = {d: 1, /** @nocollapse */ e: 2};
        var f = a.b.c.d; var g = a.b.c.e
        """,
        "var a$b$c$d = 1; var a$b$c = {/** @nocollapse */ e: 2}; var f = null; var g = null;");

    test(
        """
        var a = {}; a.b = {}; a.b.c = {d: 1, /** @nocollapse */ e: 2};
        var f = a.b.c.d; var g = a.b.c.e; use(f, g);
        """,
        """
        var a$b$c$d = 1; var a$b$c = { /** @nocollapse */ e: 2};
        var f = null; var g = null; use(a$b$c$d, a$b$c.e);
        """);

    testSame(
        """
        var a = {}; /** @nocollapse*/ a.b = {};
        a.b.c = {d: 1, e: 2};
        var f = null; var g = null;
        """);
  }

  @Test
  public void testObjLitAssignmentDepth4() {
    test(
        "var a = {}; a.b = {}; a.b.c = {}; a.b.c.d = {e: 1, f: 2}; var g = a.b.c.d.e;", //
        "var a$b$c$d$e = 1; var a$b$c$d$f = 2; var g = null;");

    test(
        "var a = {}; a.b = {}; a.b.c = {}; a.b.c.d = {e: 1, f: 2}; var g = a.b.c.d.e; use(g);", //
        "var a$b$c$d$e = 1; var a$b$c$d$f = 2; var g = null; use(a$b$c$d$e);");
  }

  @Test
  public void testHasOwnProperty() {
    test(
        "var a = {'b': 1, 'c': 1}; var alias = a;    alert(alias.hasOwnProperty('c'));", //
        "var a = {'b': 1, 'c': 1}; var alias = null; alert(a.hasOwnProperty('c'));");
  }

  @Test
  public void testAliasCreatedForObjectDepth1_1() {
    // An object's properties are not collapsed if the object is referenced
    // in a such a way that an alias is created for it, if that alias is used.
    test(
        "var a = {b: 0}; var c = a; c.b = 1; a.b == c.b;", //
        "var a$b = 0; var c = null; a$b = 1; a$b == a$b;");
    test(
        "var a = {b: 0}; var c =    a; c.b = 1; a?.b == c?.b;",
        "var a = {b: 0}; var c = null; a.b = 1; a?.b == a?.b;");

    test(
        "var a = {b: 0}; var c = a; c.b = 1; a.b == c.b; use(c);", //
        "var a={b:0}; var c=null; a.b=1; a.b == a.b; use(a);");
  }

  @Test
  public void testAliasCreatedForObjectDepth1_2() {
    testSame("var a = {b: 0}; f(a); a.b;");
  }

  @Test
  public void testAliasCreatedForObjectDepth1_3() {
    testSame("var a = {b: 0}; new f(a); a.b;");
  }

  @Test
  public void testMisusedConstructorTag() {
    test(
        """
        var a = {}; var d = a; a.b = function() {};
        /** @constructor */ a.b.c = 0; a.b.c;
        """,
        "var d=null; var a$b=function(){}; /** @constructor */ var a$b$c=0; a$b$c;");
  }

  @Test
  public void testAliasCreatedForCtorDepth1_1() {
    // A constructor's properties *are* collapsed even if the function is
    // referenced in a such a way that an alias is created for it,
    // since a function with custom properties is considered a class and its
    // non-prototype properties are considered static methods and variables.
    // People don't typically iterate through static members of a class or
    // refer to them using an alias for the class name.
    test(
        """
        /** @constructor */ var a = function(){}; a.b = 1;
        var c = a; c.b = 2; a.b == c.b;
        """,
        """
        /** @constructor */ var a = function(){}; var a$b = 1;
        var c = null; a$b = 2; a$b == a$b;
        """);
    test(
        """
        /** @constructor */ var a = function(){}; a.b = 1;
        var c = a; c.b = 2; a?.b == c?.b;
        """,
        """
        /** @constructor */ var a = function(){}; var a$b = 1;
        var c = null; a$b = 2; a?.b == a?.b;
        """);

    // Sometimes we want to prevent static members of a constructor from
    // being collapsed.
    test(
        """
        /** @constructor */ var a = function(){};
        /** @nocollapse */ a.b = 1; var c = a; c.b = 2; a.b == c.b;
        """,
        """
        /** @constructor */ var a = function(){};
        /** @nocollapse */ a.b = 1; var c = null; a.b = 2; a.b == a.b;
        """);
    test(
        """
        /** @constructor */ var a = function(){};
        /** @nocollapse */ a.b = 1; var c = a; c.b = 2; a?.b == c?.b;
        """,
        """
        /** @constructor */ var a = function(){};
        /** @nocollapse */ a.b = 1; var c = null; a.b = 2; a?.b == a?.b;
        """);
  }

  @Test
  public void testAliasCreatedForFunctionDepth2() {
    test(
        "var a = {}; a.b = function() {}; a.b.c = 1; var d = a.b; a.b.c != d.c;", //
        "var a$b = function() {}; var a$b$c = 1; var d = null; a$b$c != a$b$c;");
    testSame("var a = {}; a.b = function() {}; a.b.c = 1; var d = a?.b; a.b?.c != d?.c;");

    test(
        """
        var a = {}; a.b = function() {}; /** @nocollapse */ a.b.c = 1;
        var d = a.b; a.b.c == d.c;
        """,
        "var a$b = function() {}; /** @nocollapse */ a$b.c = 1; var d = null; a$b.c == a$b.c;");
  }

  @Test
  public void testAliasCreatedForCtorDepth2() {
    test(
        """
        var a = {}; /** @constructor */ a.b = function() {}; a.b.c = 1; var d = a.b;
        a.b.c == d.c;
        """,
        """
        /** @constructor */ var a$b = function() {}; var a$b$c = 1; var d = null;
        a$b$c == a$b$c;
        """);
    testSame(
        srcs(
            """
            var a = {}; /** @constructor */ a.b = function() {}; a.b.c = 1; var d = a?.b;
            a.b?.c == d?.c;
            """),
        warning(PARTIAL_NAMESPACE_WARNING));
    test(
        """
        var a = {}; /** @constructor */ a.b = function() {};
        /** @nocollapse */ a.b.c = 1; var d = a.b;
        a.b.c == d.c;
        """,
        """
        /** @constructor */ var a$b = function() {};
        /** @nocollapse */ a$b.c = 1; var d = null;
        a$b.c == a$b.c;
        """);
  }

  @Test
  public void testAliasCreatedForClassDepth1_1() {
    test(
        """
        var a = {}; /** @constructor */ a.b = function(){};
        var c = a; c.b = 0; a.b == c.b;
        """,
        """
        /** @constructor */ var a$b = function(){};
        var c = null; a$b = 0; a$b == a$b;
        """);

    test(
        srcs(
            """
            var a = {}; /** @constructor */ a.b = function(){};
            // `a?.b` and `c?.b` are aliasing gets
            var c = a; c.b = 0; a?.b == c?.b;
            """),
        expected(
            """
            var a = {}; /** @constructor */ a.b = function(){};
            // `a?.b` and `c?.b` are aliasing gets
            var c = null; a.b = 0; a?.b == a?.b;
            """),
        warning(PARTIAL_NAMESPACE_WARNING));

    testSame(
        """
        var a = {}; /** @constructor @nocollapse */ a.b = function(){};
        var c = 1; c = a; c.b = 0; a.b == c.b;
        """,
        PARTIAL_NAMESPACE_WARNING);

    testSame(
        """
        var a = {}; /** @constructor @nocollapse */ a.b = function(){};
        var c = 1; c = a; c.b = 0; a?.b == c?.b;
        """,
        PARTIAL_NAMESPACE_WARNING);

    test(
        """
        var a = {}; /** @constructor @nocollapse */ a.b = function(){};
        var c = a; c.b = 0; a.b == c.b;
        """,
        """
        var a = {}; /** @constructor @nocollapse */ a.b = function(){};
        var c = null; a.b = 0; a.b == a.b;
        """);

    test(
        srcs(
            """
            var a = {}; /** @constructor @nocollapse */ a.b = function(){};
            var c = a; c.b = 0; a?.b == c?.b;
            """),
        expected(
            """
            var a = {}; /** @constructor @nocollapse */ a.b = function(){};
            var c = null; a.b = 0; a?.b == a?.b;
            """),
        warning(PARTIAL_NAMESPACE_WARNING));

    test(
        """
        var a = {}; /** @constructor @nocollapse */ a.b = function(){};
        var c = a; c.b = 0; a.b == c.b; use(c);
        """,
        """
        var a = {}; /** @constructor @nocollapse */ a.b = function(){};
        var c = null; a.b = 0; a.b == a.b; use(a);
        """,
        warning(PARTIAL_NAMESPACE_WARNING));

    test(
        """
        var a = {}; /** @constructor @nocollapse */ a.b = function(){};
        var c = a; c.b = 0; a?.b == c?.b; use(c);
        """,
        """
        var a = {}; /** @constructor @nocollapse */ a.b = function(){};
        var c = null; a.b = 0; a?.b == a?.b; use(a);
        """,
        warning(PARTIAL_NAMESPACE_WARNING));
  }

  @Test
  public void testObjLitDeclarationUsedInSameVarList() {
    // The collapsed properties must be defined in the same place in the var list
    // where they were originally defined (and not, for example, at the end).
    test(
        "var a = {b: {}, c: {}}; var d = a.b; var e = a.c; use(d, e);", //
        "var a$b = {}; var a$c = {}; var d = null; var e = null; use(a$b, a$c);");

    test(
        "var a = {b: {}, /** @nocollapse */ c: {}}; var d = a.b; var e = a.c; use(d, e);", //
        "var a$b = {};var a = {/** @nocollapse */ c: {}};var d = null;var e = null;use(a$b, a.c);");
  }

  @Test
  public void testAddPropertyToUncollapsibleObjectInLocalScopeDepth1() {
    test(
        "var a = {}; var c = a; use(c); (function() {a.b = 0;})(); a.b;", //
        "var a={}; var c=null; use(a); (function(){ a.b = 0; })(); a.b;");

    test(
        "var a = {}; var c = 1; c = a; (function() {a.b = 0;})(); a.b;", //
        "var a = {}; var c=1; c = a; (function(){a.b = 0;})(); a.b;");
  }

  @Test
  public void testAddPropertyToUncollapsibleNamedCtorInLocalScopeDepth1() {
    test(
        """
        /** @constructor */ function a() {} var a$b; var c = a;
        (function() {a$b = 0;})(); a$b;
        """,
        """
        /** @constructor */ function a() {} var a$b; var c = null;
        (function() {a$b = 0;})(); a$b;
        """);
  }

  @Test
  public void testAddPropertyToUncollapsibleCtorInLocalScopeDepth1() {
    test(
        """
        /** @constructor */ var a = function() {}; var c = a;
        (function() {a.b = 0;})(); a.b;
        """,
        """
        /** @constructor */ var a = function() {}; var a$b;
        var c = null; (function() {a$b = 0;})(); a$b;
        """);
  }

  @Test
  public void testAddPropertyToUncollapsibleObjectInLocalScopeDepth2() {
    test(
        """
        var a = {}; a.b = {}; var d = a.b; use(d);
        (function() {a.b.c = 0;})(); a.b.c;
        """,
        """
        var a$b = {}; var d = null; use(a$b);
        (function() {a$b.c = 0;})(); a$b.c;
        """);
  }

  @Test
  public void testAddPropertyToUncollapsibleCtorInLocalScopeDepth2() {
    test(
        """
        var a = {}; /** @constructor */ a.b = function (){}; var d = a.b;
        (function() {a.b.c = 0;})(); a.b.c;
        """,
        """
        /** @constructor */ var a$b = function (){}; var a$b$c; var d = null;
        (function() {a$b$c = 0;})(); a$b$c;
        """);
  }

  @Test
  public void testPropertyOfChildFuncOfUncollapsibleObjectDepth1() {
    test(
        "var a = {}; var c = a; a.b = function (){}; a.b.x = 0; a.b.x;", //
        "var c = null; var a$b=function() {}; var a$b$x = 0; a$b$x;");

    test(
        "var a = {}; var c = a; a.b = function (){}; a.b.x = 0; a.b.x; use(c);", //
        "var a = {}; var c = null; a.b=function() {}; a.b.x = 0; a.b.x; use(a);");
  }

  @Test
  public void testPropertyOfChildFuncOfUncollapsibleObjectDepth2() {
    test(
        "var a = {}; a.b = {}; var c = a.b; a.b.c = function (){}; a.b.c.x = 0; a.b.c.x;", //
        "var c=null; var a$b$c = function(){}; var a$b$c$x = 0; a$b$c$x");

    test(
        """
        var a = {}; a.b = {}; var c = a.b; a.b.c = function (){}; a.b.c.x = 0; a.b.c.x;
         use(c);
        """, //
        "var a$b = {}; var c=null; a$b.c = function(){}; a$b.c.x=0; a$b.c.x; use(a$b);");
  }

  @Test
  public void testAddPropertyToChildFuncOfUncollapsibleObjectInLocalScope() {
    test(
        """
        var a = {}; a.b = function (){}; a.b.x = 0;
        var c = a; (function() {a.b.y = 1;})(); a.b.x; a.b.y;
        """,
        """
        var a$b=function() {}; var a$b$y; var a$b$x = 0; var c=null;
        (function() { a$b$y=1; })(); a$b$x; a$b$y
        """);
  }

  @Test
  public void testAddPropertyToChildTypeOfUncollapsibleObjectInLocalScope() {
    test(
        """
        var a = {};
        /** @constructor */
        a.b = function (){};
        a.b.x = 0;
        var c = a;
        (function() {a.b.y = 1;})();
        a.b.x;
        a.b.y;
        """,
        """
        /** @constructor */
        var a$b = function (){};
        var a$b$y;
        var a$b$x = 0;
        var c = null;
        (function() {a$b$y = 1;})();
        a$b$x;
        a$b$y;
        """);

    test(
        """
        var a = {};
        /** @constructor */
        a.b = function (){};
        a.b.x = 0;
        var c = a;
        (function() {a.b.y = 1;})();
        a.b.x;
        a.b.y;
        use(c);
        """,
        """
        var a = {};
        /** @constructor */
        a.b = function (){};
        a.b.x = 0;
        var c = null;
        (function() {a.b.y = 1;})();
        a.b.x;
        a.b.y;
        use(a);
        """,
        warning(PARTIAL_NAMESPACE_WARNING));
  }

  @Test
  public void testAddPropertyToChildOfUncollapsibleFunctionInLocalScope() {
    test(
        "function a() {} a.b = {x: 0}; var c = a; (function() {a.b.y = 0;})(); a.b.y;", //
        "function a() {} var a$b$x=0; var a$b$y; var c=null; (function(){a$b$y=0})(); a$b$y");
  }

  @Test
  public void testAddPropertyToChildOfUncollapsibleCtorInLocalScope() {
    test(
        """
        /** @constructor */ var a = function() {}; a.b = {x: 0}; var c = a;
        (function() {a.b.y = 0;})(); a.b.y;
        """,
        """
        /** @constructor */ var a = function() {}; var a$b$x = 0; var a$b$y; var c = null;
        (function() {a$b$y = 0;})(); a$b$y;
        """);
  }

  @Test
  public void testLocalAliasCreatedAfterVarDeclaration1() {
    test(
        """
        var a = {b: 3};
        function f() {
          var tmp;
          tmp = a;
          use(tmp.b);
        }
        """,
        """
        var a$b = 3
        function f() {
          var tmp;
          tmp = null;
          use(a$b);
        }
        """);
    test(
        """
        var a = {b: 3};
        function f() {
          var tmp;
          tmp = a;
          use?.(tmp?.b);
        }
        """,
        """
        var a = {b: 3};
        function f() {
          var tmp;
          tmp = null;
          use?.(a?.b);
        }
        """);
  }

  @Test
  public void testLocalAliasCreatedAfterVarDeclaration2() {
    test(
        """
        var a = {b: 3}
        function f() {
          var tmp;
          if (true) {
            tmp = a;
            use(tmp.b);
          }
        }
        """,
        """
        var a$b = 3;
        function f() {
          var tmp;
          if (true) {
            tmp = null;
            use(a$b);
          }
        }
        """);
    test(
        """
        var a = {b: 3}
        function f() {
          var tmp;
          if (true) {
            tmp = a;
            use?.(tmp?.b);
          }
        }
        """,
        """
        var a = {b: 3}
        function f() {
          var tmp;
          if (true) {
            tmp = null;
            use?.(a?.b);
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
  public void testPartialLocalCtorAlias() {
    testWarning(
        """
        /** @constructor */ var Main = function() {};
        Main.doSomething = function(i) {}
        function f() {
          var tmp;
          if (g()) {
            use(tmp.doSomething);
            tmp = Main;
            tmp.doSomething(5);
          }
          use(tmp.doSomething); // can't inline this use of tmp.
        }
        """,
        InlineAndCollapseProperties.UNSAFE_CTOR_ALIASING);
  }

  @Test
  public void testFunctionAlias2() {
    test(
        "var a = {}; a.b = {}; a.b.c = function(){}; a.b.d = a.b.c;use(a.b.d)", //
        "var a$b$c = function(){}; var a$b$d = null;use(a$b$c);");
    test(
        "var a = {}; a.b = {}; a.b.c = function(){}; a.b.d = a.b?.c; use?.(a.b?.d);", //
        "var         a$b = {}; a$b.c = function(){}; a$b.d = a$b?.c; use?.(a$b?.d);");
  }

  @Test
  public void testLocalAlias1() {
    test(
        "var   a = {b: 3}; function f() { var x =    a; f(x.b); }", //
        "var a$b =     3 ; function f() { var x = null; f(a$b); }");
    test(
        "var   a = {b: 3}; function f() { var x =    a; f(x?.b); }",
        "var   a = {b: 3}; function f() { var x = null; f(a?.b); }");
  }

  @Test
  public void testLocalAlias2() {
    test(
        "var a   = {b: 3,       c : 4}; function f() { var x =    a; f(x.b); f(x.c);}",
        "var a$b =     3; var a$c = 4 ; function f() { var x = null; f(a$b); f(a$c);}");
    test(
        "var a = {b: 3, c: 4}; function f() { var x =    a; f?.(x?.b); f?.(x?.c);}",
        "var a = {b: 3, c: 4}; function f() { var x = null; f?.(a?.b); f?.(a?.c);}");
  }

  @Test
  public void testLocalAlias3() {
    test(
        """
        var a = {b: 3, c: {d: 5}};
        function f() { var x = a; f(x.b); f(x.c); f(x.c.d); }
        """,
        """
        var a$b = 3; var a$c = {d: 5};
        function f() { var x = null; f(a$b); f(a$c); f(a$c.d);}
        """);
    test(
        """
        var a = {b: 3, c: {d: 5}};
        function f() { var x =    a; f?.(x?.b); f?.(x?.c); f?.(x.c?.d);}
        """,
        """
        var a = {b: 3, c: {d:5}};
        function f() { var x = null; f?.(a?.b); f?.(a?.c); f?.(a.c?.d);}
        """);
  }

  @Test
  public void testLocalAlias4() {
    test(
        """
        var a = {b: 3}; var c = {d: 5};
        function f() { var x = a; var y = c; f(x.b); f(y.d); }
        """,
        """
        var a$b = 3; var c$d = 5;
        function f() { var x = null; var y = null; f(a$b); f(c$d);}
        """);
    test(
        """
        var a = {b: 3}; var c = {d: 5};
        function f() { var x =    a; var y =    c; f?.(x?.b); f?.(y?.d); }
        """,
        """
        var a = {b: 3}; var c = {d: 5};
        function f() { var x = null; var y = null; f?.(a?.b); f?.(c?.d); }
        """);
  }

  @Test
  public void testLocalAlias5() {
    test(
        """
        var a = {b: {c: 5}};
        function f() { var x = a; var y = x.b; f(a.b.c); f(y.c); }
        """,
        """
        var a$b$c = 5;
        function f() { var x = null; var y = null; f(a$b$c); f(a$b$c);}
        """);
    test(
        """
        var a = {b: {c: 5}};
        function f() { var x =    a; var y = x?.b; f?.(a.b?.c); f?.(y?.c); }
        """,
        """
        var a = {b: {c: 5}};
        function f() { var x = null; var y = a?.b; f?.(a.b?.c); f?.(y?.c); }
        """);
  }

  @Test
  public void testLocalAlias6() {
    test(
        "var a = {b: 3}; function f() { var x = a; if (x.b) { f(x.b); } }", //
        "var a$b = 3; function f() { var x = null; if (a$b) { f(a$b); } }");
    test(
        "var a = {b: 3}; function f() { var x =    a; if (x?.b) { f?.(x?.b); } }", //
        "var a = {b: 3}; function f() { var x = null; if (a?.b) { f?.(a?.b); } }");
  }

  @Test
  public void testLocalAlias7() {
    test(
        "var a = {b: {c: 5}}; function f() { var x = a.b; f(x.c); }", //
        "var a$b$c = 5; function f() { var x = null; f(a$b$c); }");
    testSame("var a = {b: {c: 5}}; function f() { var x = a?.b; f?.(x?.c); }");
  }

  @Test
  public void testLocalAlias8() {
    testSame("var a = { b: 3 }; function f() { if (true) { var x = a; f(x.b); } x = { b : 4}; }");
  }

  @Test
  public void testGlobalWriteToAncestor() {
    testSame("var a = {b: 3}; function f() { var x = a; f(a.b); } a = 5;");
  }

  @Test
  public void testGlobalWriteToNonAncestor() {
    test(
        "var a = {b: 3}; function f() { var x =    a; f(a.b); } a.b = 5;", //
        "var    a$b = 3; function f() { var x = null; f(a$b); } a$b = 5;");
    test(
        "var a = {b: 3}; function f() { var x =    a; f(a?.b); } a.b = 5;",
        "var a = {b: 3}; function f() { var x = null; f(a?.b); } a.b = 5;");
  }

  @Test
  public void testLocalWriteToAncestor() {
    testSame("var a = {b: 3}; function f() { a = 5; var x = a; f(a.b); } ");
  }

  @Test
  public void testLocalWriteToNonAncestor() {
    test(
        """
        var a = {b: 3};
        function f() { a.b = 5; var x = a; f(a.b); }
        """,
        "var a$b = 3; function f() { a$b = 5; var x = null; f(a$b); } ");
    test(
        """
        var a = {b: 3};
        function f() { a.b = 5; var x =    a; f?.(a?.b); }
        """,
        """
        var a = {b: 3};
        function f() { a.b = 5; var x = null; f?.(a?.b); }
        """);
  }

  @Test
  public void testLocalAliasOfAncestor() {
    testSame(
        """
        var a = {b: {c: 5}}; function g() { f(a); }
        function f() { var x = a.b; f(x.c); }
        """);
  }

  @Test
  public void testGlobalAliasOfAncestor() {
    test(
        "var a = {b: {c: 5}}; function f() { var x = a.b; f(x.c); }", //
        "var a$b$c=5; function f() {var x=null; f(a$b$c); }");
    testSame("var a = {b: {c: 5}}; function f() { var x = a?.b; f?.(x?.c); }");
  }

  @Test
  public void testLocalAliasOfOtherName() {
    testSame(
        """
        var foo = function() { return {b: 3}; };
        var a = foo(); a.b = 5;
        function f() { var x = a.b; f(x); }
        """);
  }

  @Test
  public void testLocalAliasOfFunction() {
    test(
        """
        var a = function() {}; a.b = 5;
        function f() { var x = a.b; f(x); }
        """,
        """
        var a = function() {}; var a$b = 5;
        function f() { var x = null; f(a$b); }
        """);
  }

  @Test
  public void testNoInlineGetpropIntoCall() {
    test(
        "var b = x; function f() { var a = b; a(); }", //
        "var b = x; function f() { var a = null; b(); }");
    test(
        "var b = {}; b.c = x; function f() { var a = b.c; a(); }", //
        "var b$c = x; function f() { var a = null; b$c(); }");
  }

  @Test
  public void testCommaOperator() {
    test(
        "var a = {}; a.b = function() {}, a.b();", //
        "var a$b; a$b=function() {}, a$b();");

    test(
        """
        var ns = {};
        ns.Foo = {};
        var Baz = {};
        Baz.Foo = ns.Foo;
        (Baz.Foo.bar = 10, 123);
        """,
        """
        var Baz$Foo=null;
        var ns$Foo$bar;
        (ns$Foo$bar = 10, 123);
        """);

    test(
        """
        var ns = {};
        ns.Foo = {};
        var Baz = {};
        Baz.Foo = ns?.Foo;
        (Baz.Foo.bar = 10, 123);
        """,
        """
        var ns = {};
        ns.Foo = {};
        var Baz$Foo = ns?.Foo;
        (Baz$Foo.bar = 10, 123);
        """);

    test(
        """
        var ns = {};
        ns.Foo = {};
        var Baz = {};
        Baz.Foo = ns.Foo;
        function f() { (Baz.Foo.bar = 10, 123); }
        """,
        """
        var ns$Foo$bar;
        var Baz$Foo=null;
        function f() { (ns$Foo$bar = 10, 123); }
        """);
    test(
        """
        var ns = {};
        ns.Foo = {};
        var Baz = {};
        Baz.Foo = ns?.Foo;
        function f() { (Baz.Foo.bar = 10, 123); }
        """,
        """
        var ns = {};
        ns.Foo = {};
        var Baz$Foo = ns?.Foo;
        function f() { (Baz$Foo.bar = 10, 123); }
        """);
  }

  @Test
  public void testTypeDefAlias1() {
    test(
        """
        /** @constructor */ var D = function() {};
        /** @constructor */ D.L = function() {};
        /** @type {D.L} */ D.L.A = new D.L();

        /** @const */ var M = {};
        /** @typedef {D.L} */ M.L = D.L;

        use(M.L.A);
        """,
        """
        /** @constructor */ var D = function() {};
        /** @constructor */ var D$L = function() {};
        /** @type {D.L} */ var D$L$A = new D$L();
        /** @typedef {D.L} */ var M$L = null
        use(D$L$A);
        """);
    test(
        """
        /** @constructor */ var D = function() {};
        /** @constructor */ D.L = function() {};
        /** @type {D.L} */ D.L.A = new D.L();

        /** @const */ var M = {};
        /** @typedef {D.L} */ M.L = D.L;

        use?.(M.L?.A);
        """,
        """
        /** @constructor */ var D = function() {};
        /** @constructor */ var D$L = function() {};
        /** @type {D.L} */ var D$L$A = new D$L();
        /** @typedef {D.L} */ var M$L = null
        // TODO(b/148237949): collapse above breaks this reference
        use?.(D$L?.A);
        """);
  }

  @Test
  public void testGlobalAliasWithProperties1() {
    test(
        """
        var ns = {};
        /** @constructor */ ns.Foo = function() {};
        /** @enum {number} */ ns.Foo.EventType = {A:1, B:2};
        /** @constructor */ ns.Bar = ns.Foo;
        var x = function() {use(ns.Bar.EventType.A)};
        use(x);
        """,
        """
        /** @constructor */ var ns$Foo = function(){};
        var ns$Foo$EventType$A = 1;
        var ns$Foo$EventType$B = 2;
        /** @constructor */ var ns$Bar = null;
        var x = function(){use(ns$Foo$EventType$A)};
        use(x);
        """);
    testSame(
        srcs(
            """
            var ns = {};
            /** @constructor */ ns.Foo = function() {};
            /** @enum {number} */ ns.Foo.EventType = {A:1, B:2};
            /** @constructor */ ns.Bar = ns?.Foo;
            var x = function() {use?.(ns.Bar.EventType?.A)};
            use?.(x);
            """),
        warning(PARTIAL_NAMESPACE_WARNING));
  }

  @Test
  public void testGlobalAliasWithProperties2() {
    // Reassignment of properties was necessary to prevent invalid code in
    // previous iterations of this optimization.  Verify we don't break
    // code like this.  Now it causes a back-off of the collapsing because
    // the value is assigned more than once.
    test(
        """
        var ns = {};
        /** @constructor */ ns.Foo = function() {};
        /** @enum {number} */ ns.Foo.EventType = {A:1, B:2};
        /** @constructor */ ns.Bar = ns.Foo;
        /** @enum {number} */ ns.Bar.EventType = ns.Foo.EventType;
        var x = function() {use(ns.Bar.EventType.A)};
        use(x)
        """,
        """
        /** @constructor */ var ns$Foo = function(){};
        /** @enum {number} */ var ns$Foo$EventType = {A:1, B:2};
        /** @constructor */ var ns$Bar = null;
        /** @enum {number} */ ns$Foo$EventType = ns$Foo$EventType;
        var x = function(){use(ns$Foo$EventType.A)};
        use(x);
        """);
    testSame(
        srcs(
            """
            var ns = {};
            /** @constructor */ ns.Foo = function() {};
            /** @enum {number} */ ns.Foo.EventType = {A:1, B:2};
            /** @constructor */ ns.Bar = ns?.Foo;
            /** @enum {number} */ ns.Bar.EventType = ns.Foo?.EventType;
            var x = function() {use?.(ns.Bar.EventType?.A)};
            use?.(x)
            """),
        warning(PARTIAL_NAMESPACE_WARNING));
  }

  @Test
  public void testGlobalAliasWithProperties3() {
    test(
        """
        var ns = {};
        /** @constructor */ ns.Foo = function() {};
        /** @enum {number} */ ns.Foo.EventType = {A:1, B:2};
        /** @constructor */ ns.Bar = ns.Foo;
        /** @enum {number} */ ns.Bar.Other = {X:1, Y:2};
        var x = function() {use(ns.Bar.Other.X)};
        use(x)
        """,
        """
        /** @constructor */ var ns$Foo=function(){};
        var ns$Foo$EventType$A=1;
        var ns$Foo$EventType$B=2;
        /** @constructor */ var ns$Bar=null;
        var ns$Foo$Other$X=1;
        var ns$Foo$Other$Y=2;
        var x=function(){use(ns$Foo$Other$X)};
        use(x)
        """);
    testSame(
        srcs(
            """
            var ns = {};
            /** @constructor */ ns.Foo = function() {};
            /** @enum {number} */ ns.Foo.EventType = {A:1, B:2};
            /** @constructor */ ns.Bar = ns?.Foo;
            /** @enum {number} */ ns.Bar.Other = {X:1, Y:2};
            var x = function() {use?.(ns.Bar.Other?.X)};
            use?.(x)
            """),
        warning(PARTIAL_NAMESPACE_WARNING));
  }

  @Test
  public void testGlobalAliasWithProperties4() {
    testSame(
        """
        var nullFunction = function(){};
        var blob = {};
        blob.init = nullFunction;
        use(blob)
        """);
  }

  @Test
  public void testGlobalAlias_propertyOnExternedConstructor_isNotChanged() {
    testSame(
        externs("/** @constructor */ var blob = function() {}"),
        srcs(
            """
            var nullFunction = function(){};
            blob.init = nullFunction;
            use(blob.init)
            """));
  }

  @Test
  public void testLocalAliasOfEnumWithInstanceofCheck() {
    test(
        """
        /** @constructor */
        var Enums = function() {
        };

        /** @enum {number} */
        Enums.Fruit = {
         APPLE: 1,
         BANANA: 2,
        };

        function foo(f) {
         if (f instanceof Enums) { alert('what?'); return; }

         var Fruit = Enums.Fruit;
         if (f == Fruit.APPLE) alert('apple');
         if (f == Fruit.BANANA) alert('banana');
        }
        """,
        """
        /** @constructor */
        var Enums = function() {};
        var Enums$Fruit$APPLE = 1;
        var Enums$Fruit$BANANA = 2;
        function foo(f) {
         if (f instanceof Enums) { alert('what?'); return; }
         var Fruit = null;
         if (f == Enums$Fruit$APPLE) alert('apple');
         if (f == Enums$Fruit$BANANA) alert('banana');
        }
        """);
  }

  @Test
  public void testCollapsePropertiesOfClass1() {
    test(
        """
        /** @constructor */
        var namespace = function() {};
        goog.inherits(namespace, Object);

        namespace.includeExtraParam = true;

        /** @enum {number} */
        namespace.Param = {
          param1: 1,
          param2: 2
        };

        if (namespace.includeExtraParam) {
          namespace.Param.optParam = 3;
        }

        function f() {
          var Param = namespace.Param;
          log(namespace.Param.optParam);
          log(Param.optParam);
        }
        """,
        """
        /** @constructor */
        var namespace = function() {};
        goog.inherits(namespace, Object);
        var namespace$includeExtraParam = true;
        var namespace$Param$param1 = 1;
        var namespace$Param$param2 = 2;
        if (namespace$includeExtraParam) {
          var namespace$Param$optParam = 3;
        }
        function f() {
          var Param = null;
          log(namespace$Param$optParam);
          log(namespace$Param$optParam);
        }
        """);
    // TODO(b/148237949): CollapseProperties breaks several optional chain references here
    test(
        """
        /** @constructor */
        var namespace = function() {};
        goog.inherits(namespace, Object);

        namespace.includeExtraParam = true;

        /** @enum {number} */
        namespace.Param = {
          param1: 1,
          param2: 2
        };

        if (namespace?.includeExtraParam) {
          namespace.Param.optParam = 3;
        }

        function f() {
          var Param = namespace?.Param;
          log(namespace.Param?.optParam);
          log(Param?.optParam);
        }
        """,
        """
        /** @constructor */
        var namespace = function() {};
        goog.inherits(namespace, Object);
        var namespace$includeExtraParam = true;
        var namespace$Param$param1 = 1;
        var namespace$Param$param2 = 2;
        /** @enum {number} */
        var namespace$Param = {
          param1: namespace$Param$param1,
          param2: namespace$Param$param2
        };
        if (namespace?.includeExtraParam) { // broken
          var namespace$Param$optParam = 3;
        }
        function f() {
          var Param = namespace?.Param; // broken
          log(namespace$Param?.optParam); // broken
          log(Param?.optParam); // broken
        }
        """);
  }

  @Test
  public void testCollapsePropertiesOfClass2() {
    test(
        """
        var goog = goog || {};
        goog.addSingletonGetter = function(cls) {};

        var a = {};

        /** @constructor */
        a.b = function() {};
        goog.addSingletonGetter(a.b);
        a.b.prototype.get = function(key) {};

        /** @constructor */
        a.b.c = function() {};
        a.b.c.XXX = new a.b.c();

        function f() {
          var x = a.b.getInstance();
          var Key = a.b.c;
          x.get(Key.XXX);
        }
        """,
        """
        var goog = goog || {};
        var goog$addSingletonGetter = function(cls) {};
        /** @constructor */
        var a$b = function() {};
        goog$addSingletonGetter(a$b);
        a$b.prototype.get = function(key) {};
        /** @constructor */
        var a$b$c = function() {};
        var a$b$c$XXX = new a$b$c();

        function f() {
          var x = a$b.getInstance();
          var Key = null;
          x.get(a$b$c$XXX);
        }
        """);
  }

  @Test
  public void test_b19179602() {
    // Note that this only collapses a.b.staticProp because a.b is a constructor.
    // Normally AggressiveInlineAliases would not inline "b" inside the loop.
    test(
        """
        var a = {};
        /** @constructor */ a.b = function() {};
        a.b.staticProp = 5;
        function f() {
          while (true) {
        // b is declared inside a loop, so it is reassigned multiple times
            var b = a.b;
            alert(b.staticProp);
          }
        }
        """,
        """
        /** @constructor */ var a$b = function() {};
        var a$b$staticProp = 5;
        function f() {
          while (true) {
            var b = null;
            alert(a$b$staticProp);
          }
        }
        """);
    test(
        """
        var a = {};
        /** @constructor */ a.b = function() {};
        a.b.staticProp = 5;
        function f() {
          while (true) {
        // b is declared inside a loop, so it is reassigned multiple times
            var b = a.b;
            alert(b?.staticProp);
          }
        }
        """,
        """
        /** @constructor */ var a$b = function() {};
        var a$b$staticProp = 5;
        function f() {
          while (true) {
            var b = null;
        // TODO(bradfordcsmith): This reference is broken by collapsing above.
            alert(a$b?.staticProp);
          }
        }
        """);
  }

  @Test
  public void test_b19179602_declareOutsideLoop() {
    test(
        """
        var a = {};
        /** @constructor */ a.b = function() {};
        a.b.staticProp = 5;
        function f() {
        // b is declared outside the loop
          var b = a.b;
          while (true) {
            alert(b.staticProp);
          }
        }
        """,
        """
        /** @constructor */
        var a$b = function() {};
        var a$b$staticProp = 5;

        function f() {
          var b = null;
          while (true) {
            alert(a$b$staticProp);
          }
        }
        """);
    test(
        """
        var a = {};
        /** @constructor */ a.b = function() {};
        a.b.staticProp = 5;
        function f() {
        // b is declared outside the loop
          var b = a.b;
          while (true) {
            alert(b?.staticProp);
          }
        }
        """,
        """
        /** @constructor */
        var a$b = function() {};
        // TODO(b/148237949): Collapsing this breaks the optional chain reference below.
        var a$b$staticProp = 5;

        function f() {
          var b = null;
          while (true) {
            alert(a$b?.staticProp);
          }
        }
        """);
  }

  @Test
  public void testCtorManyAssignmentsDontInlineWarn() {
    test(
        """
        var a = {};
        /** @constructor */ a.b = function() {};
        a.b.staticProp = 5;
        function f(y, z) {
          var x = a.b;
          if (y) {
            x = z;
          }
          return x.staticProp;
        }
        """,
        """
        /** @constructor */ var a$b = function() {};
        var a$b$staticProp = 5;
        function f(y, z) {
          var x = a$b;
          if (y) {
            x = z;
          }
          return x.staticProp;
        }
        """,
        warning(InlineAndCollapseProperties.UNSAFE_CTOR_ALIASING));
  }

  @Test
  public void testCollapseOfGoogModule() {
    test(
        """
        var goog = goog || {};
        /** @constructor */
        goog.module = function(id) {};
        goog.module.get = function(id) {};
        goog.module.getInternal_ = function(id) {};
        goog.module = goog.module || {};
        """,
        """
        var goog = goog || {};
        var goog$module = function(id) {};
        // TODO(b/389129315): This should be goog$module$get.
        goog$module.get = function(id) {};
        goog$module.getInternal_ = function(id) {};
        goog$module = goog$module || {};
        """);
  }

  @Test
  public void testCodeGeneratedByGoogModule() {
    // The static property is added to the exports object
    test(
        """
        var $jscomp = {};
        $jscomp.scope = {};
        /** @constructor */
        $jscomp.scope.Foo = function() {};
        var exports = $jscomp.scope.Foo;
        exports.staticprop = {A:1};
        var y = exports.staticprop.A;
        """,
        """
        /** @constructor */
        var $jscomp$scope$Foo = function() {}
        var exports = null;
        var $jscomp$scope$Foo$staticprop$A = 1;
        var y = null;
        """);

    // The static property is added to the constructor
    test(
        """
        var $jscomp = {};
        $jscomp.scope = {};
        /** @constructor */
        $jscomp.scope.Foo = function() {};
        $jscomp.scope.Foo.staticprop = {A:1};
        var exports = $jscomp.scope.Foo;
        var y = exports.staticprop.A;
        """,
        """
        /** @constructor */
        var $jscomp$scope$Foo = function() {}
        var $jscomp$scope$Foo$staticprop$A = 1;
        var exports = null;
        var y = null;
        """);
  }

  @Test
  public void test_b269515361_codeGeneratedByGoogRequireDynamicForEs5() {
    // Case 1
    // The original code of this test case is:
    // ```
    // const {LateLoadTs} = await goog.requireDynamic('a.b.c');
    // new LateLoadTs().render();
    // ```
    // This can be reproduced by actual compilation to es5.
    //
    // This case is for testing the following line, in which the alias is used in dot get property.
    // LateLoadTs$jscomp$1 = $jscomp$destructuring$var22.LateLoadTs
    test(
        """
        var module$exports$path$lateload_ts =
          {
            LateLoadTs:class LateLoadTs {
              render() {}
          }
        };

        var func = function() {
          var $jscomp$destructuring$var22;
          var LateLoadTs$jscomp$1;
          return $jscomp$asyncExecutePromiseGeneratorProgram(
            function($jscomp$generator$context){
              if ($jscomp$generator$context.nextAddress == 1) {
                return $jscomp$generator$context.yield(goog.importHandler_('WS8L6d'), 2);
              }
              $jscomp$destructuring$var22 = module$exports$path$lateload_ts;
              LateLoadTs$jscomp$1 = $jscomp$destructuring$var22.LateLoadTs;
              (new LateLoadTs$jscomp$1()).render();
              $jscomp$generator$context.jumpToEnd();
            }
          );
        };
        """,
        """
        const CLASS_DECL$0 = class {
          render() {
          }
        };
        var module$exports$path$lateload_ts$LateLoadTs = CLASS_DECL$0;
        var func = function() {
          var $jscomp$destructuring$var22;
          var LateLoadTs$jscomp$1;
          return $jscomp$asyncExecutePromiseGeneratorProgram(function($jscomp$generator$context) {
            if ($jscomp$generator$context.nextAddress == 1) {
              return $jscomp$generator$context.yield(goog.importHandler_("WS8L6d"), 2);
            }
            $jscomp$destructuring$var22 = null;
            LateLoadTs$jscomp$1 = module$exports$path$lateload_ts$LateLoadTs;
            (new LateLoadTs$jscomp$1()).render();
            $jscomp$generator$context.jumpToEnd();
          });
        };
        """);

    // Case 2
    // This case is for testing assigning value to the exported object.
    //
    // The original code of this test case is:
    // ```
    // const x = await goog.requireDynamic('a.b.c');
    // x.LateLoadTs.num = 0;
    // console.log(x.LateLoadTs);
    // ```
    // This can be reproduced by actual compilation to es5.
    test(
        """
        var module$exports$path$lateload_ts =
          {
            LateLoadTs:class LateLoadTs { }
        };

        var func = function() {
          var x;
          return $jscomp$asyncExecutePromiseGeneratorProgram(
            function($jscomp$generator$context){
              if ($jscomp$generator$context.nextAddress == 1) {
                return $jscomp$generator$context.yield(goog.importHandler_('WS8L6d'), 2);
              }
              x = module$exports$path$lateload_ts;
              x.LateLoadTs.num = 0;
              console.log(x.LateLoadTs);
              $jscomp$generator$context.jumpToEnd();
            }
          );
        };
        """,
        """
        const CLASS_DECL$0 = class {
        };
        var module$exports$path$lateload_ts$LateLoadTs = CLASS_DECL$0;
        var func = function() {
          var x;
          return $jscomp$asyncExecutePromiseGeneratorProgram(function($jscomp$generator$context) {
            if ($jscomp$generator$context.nextAddress == 1) {
              return $jscomp$generator$context.yield(goog.importHandler_("WS8L6d"), 2);
            }
            x = null;
            module$exports$path$lateload_ts$LateLoadTs.num = 0;
            console.log(module$exports$path$lateload_ts$LateLoadTs);
            $jscomp$generator$context.jumpToEnd();
          });
        };
        """);
    // Case 3
    // The original code of this test case is:
    // ```
    // const {LateLoadTs} = await goog.requireDynamic('a.b.c');
    // new LateLoadTs().render();
    // ```
    // This can be reproduced by actual compilation to es5.
    //
    // This case is for testing non-exported module, i.e., the global name is not prefixed with
    // 'module$exports$'. Test should fail.
    test(
        """
        var not_module$exports$path$lateload_ts =
          {
            LateLoadTs:class LateLoadTs {
              render() {}
          }
        };

        var func = function() {
          var $jscomp$destructuring$var22;
          var LateLoadTs$jscomp$1;
          return $jscomp$asyncExecutePromiseGeneratorProgram(
            function($jscomp$generator$context){
              if ($jscomp$generator$context.nextAddress == 1) {
                return $jscomp$generator$context.yield(goog.importHandler_('WS8L6d'), 2);
              }
              $jscomp$destructuring$var22 = not_module$exports$path$lateload_ts;
              LateLoadTs$jscomp$1 = $jscomp$destructuring$var22.LateLoadTs;
              (new LateLoadTs$jscomp$1()).render();
              $jscomp$generator$context.jumpToEnd();
            }
          );
        };
        """,
        """
        const CLASS_DECL$0 = class {
          render() {
          }
        };
        var not_module$exports$path$lateload_ts = {LateLoadTs:CLASS_DECL$0};
        var func = function() {
          var $jscomp$destructuring$var22;
          var LateLoadTs$jscomp$1;
          return $jscomp$asyncExecutePromiseGeneratorProgram(function($jscomp$generator$context) {
            if ($jscomp$generator$context.nextAddress == 1) {
              return $jscomp$generator$context.yield(goog.importHandler_("WS8L6d"), 2);
            }
            $jscomp$destructuring$var22 = not_module$exports$path$lateload_ts;
            LateLoadTs$jscomp$1 = $jscomp$destructuring$var22.LateLoadTs;
            (new LateLoadTs$jscomp$1()).render();
            $jscomp$generator$context.jumpToEnd();
          });
        };
        """);

    // Case 4
    // This is a manually configured example for this test.
    //
    // This case is for testing the case where the alias is not only used in property access/
    // destructuring.
    test(
        """
        var _module$exports$path$lateload_ts =
          {
            LateLoadTs:class LateLoadTs {
              render() {}
          }
        };

        var func = function() {
          var $jscomp$destructuring$var22;
          var LateLoadTs$jscomp$1;
          return $jscomp$asyncExecutePromiseGeneratorProgram(
            function($jscomp$generator$context){
              if ($jscomp$generator$context.nextAddress == 1) {
                return $jscomp$generator$context.yield(goog.importHandler_('WS8L6d'), 2);
              }
              $jscomp$destructuring$var22 = _module$exports$path$lateload_ts;
              $jscomp$destructuring$var22 = 'foo';
              LateLoadTs$jscomp$1 = $jscomp$destructuring$var22.LateLoadTs;
              (new LateLoadTs$jscomp$1()).render();
              $jscomp$generator$context.jumpToEnd();
            }
          );
        };
        """,
        """
        const CLASS_DECL$0 = class {
          render() {
          }
        };
        var _module$exports$path$lateload_ts = {LateLoadTs:CLASS_DECL$0};
        var func = function() {
          var $jscomp$destructuring$var22;
          var LateLoadTs$jscomp$1;
          return $jscomp$asyncExecutePromiseGeneratorProgram(function($jscomp$generator$context) {
            if ($jscomp$generator$context.nextAddress == 1) {
              return $jscomp$generator$context.yield(goog.importHandler_("WS8L6d"), 2);
            }
            $jscomp$destructuring$var22 = _module$exports$path$lateload_ts;
            $jscomp$destructuring$var22 = "foo";
            LateLoadTs$jscomp$1 = $jscomp$destructuring$var22.LateLoadTs;
            (new LateLoadTs$jscomp$1()).render();
            $jscomp$generator$context.jumpToEnd();
          });
        };
        """);

    // Case 5
    // This is a manually configured example for this test.
    //
    // This case is for testing the case where the alias is initialized in declaration and then
    // reassigned a value.
    test(
        """
        var module$exports$path$lateload_ts =
          {
            LateLoadTs:class LateLoadTs {
              render() {}
          }
        };

        var func = function() {
          var $jscomp$destructuring$var22 = 'foo';
          var LateLoadTs$jscomp$1;
          return $jscomp$asyncExecutePromiseGeneratorProgram(
            function($jscomp$generator$context){
              if ($jscomp$generator$context.nextAddress == 1) {
                return $jscomp$generator$context.yield(goog.importHandler_('WS8L6d'), 2);
              }
              $jscomp$destructuring$var22 = module$exports$path$lateload_ts;
              LateLoadTs$jscomp$1 = $jscomp$destructuring$var22.LateLoadTs;
              (new LateLoadTs$jscomp$1()).render();
              $jscomp$generator$context.jumpToEnd();
            }
          );
        };
        """,
        """
        const CLASS_DECL$0 = class {
          render() {
          }
        };
        var module$exports$path$lateload_ts = {LateLoadTs:CLASS_DECL$0};
        var func = function() {
          var $jscomp$destructuring$var22 = "foo";
          var LateLoadTs$jscomp$1;
          return $jscomp$asyncExecutePromiseGeneratorProgram(function($jscomp$generator$context) {
            if ($jscomp$generator$context.nextAddress == 1) {
              return $jscomp$generator$context.yield(goog.importHandler_("WS8L6d"), 2);
            }
            $jscomp$destructuring$var22 = module$exports$path$lateload_ts;
            LateLoadTs$jscomp$1 = $jscomp$destructuring$var22.LateLoadTs;
            (new LateLoadTs$jscomp$1()).render();
            $jscomp$generator$context.jumpToEnd();
          });
        };
        """);
  }

  @Test
  public void testInlineCtorInObjLit() {
    test(
        """
        /** @constructor */
        function Foo() {}

        /** @constructor */
        var Bar = Foo;

        var objlit = {
          'prop' : Bar
        };
        """,
        """
        /** @constructor */
        function Foo() {}
        /** @constructor */
        var Bar = null;
        var objlit$prop = Foo;
        """);
  }

  @Test
  public void testNoCollapseExportedNode() {
    test(
        "var x = {}; x.y = {}; var dontExportMe = x.y; use(dontExportMe);", //
        "var x$y = {}; var dontExportMe = null; use(x$y);");

    test(
        "var x = {}; x.y = {}; var _exportMe = x.y;", //
        "var x$y = {}; var _exportMe = x$y;");
  }

  @Test
  public void testDontCrashCtorAliasWithEnum() {
    test(
        """
        var ns = {};
        /** @constructor */
        ns.Foo = function () {};
        var Bar = ns.Foo;
        /** @const @enum */
        Bar.prop = { A: 1 };
        """,
        """
        /** @constructor */
        var ns$Foo = function(){};
        var Bar = null;
        var ns$Foo$prop$A = 1
        """);
  }

  @Test
  public void testDontCrashNamespaceAliasAcrossScopes() {
    test(
        """
        var ns = {};
        ns.VALUE = 0.01;
        function f() {
            var constants = ns;
            (function() {
               var x = constants.VALUE;
            })();
        }
        """,
        null);
  }

  @Test
  public void testGlobalAliasWithLet() {
    test(
        "let ns = {}; ns.foo = 'bar'; let ns2 = ns; use(ns2.foo);", //
        "var ns$foo = 'bar'; let ns2 = null; use(ns$foo);");
    test(
        "let ns = {}; ns.foo = 'bar'; let ns2 =   ns; use?.(ns2?.foo);", //
        "let ns = {}; ns.foo = 'bar'; let ns2 = null; use?.( ns?.foo);");
  }

  @Test
  public void testGlobalAliasForLet2() {
    // We don't do any sort of branch prediction, so can't collapse here.
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
  }

  @Test
  public void testGlobalAliasWithLet3() {
    // Back off since in general we don't check that ns2 is only ever accessed within the if block.
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
        "let ns = {}; ns.foo = 'bar'; function f() { let ns2 =   ns; use(ns2.foo); }",
        "var          ns$foo = 'bar'; function f() { let ns2 = null; use( ns$foo); }");
    test(
        "let ns = {}; ns.foo = 'bar'; function f() { let ns2 =   ns; use?.(ns2?.foo); }",
        "let ns = {}; ns.foo = 'bar'; function f() { let ns2 = null; use?.( ns?.foo); }");
  }

  @Test
  public void testLocalAliasWithLet2() {
    test(
        """
        let ns = {};
        ns.foo = 'bar';
        function f() {
          if (true) {
            let ns2 = ns;
            use(ns2.foo);
          }
        }
        """,
        """
        var ns$foo = 'bar';
        function f() {
          if (true) {
            let ns2 = null;
            use(ns$foo);
          }
        }
        """);
    test(
        """
        let ns = {};
        ns.foo = 'bar';
        function f() {
          if (true) {
            let ns2 = ns;
            use?.(ns2?.foo);
          }
        }
        """,
        """
        let ns = {};
        ns.foo = 'bar';
        function f() {
          if (true) {
            let ns2 = null;
            use?.(ns?.foo);
          }
        }
        """);
  }

  @Test
  public void testLocalAliasWithLet3() {
    test(
        """
        let ns = {};
        ns.foo = 'bar';
        if (true) {
          let ns2 = ns;
          use(ns2.foo);
        }
        """,
        """
        var ns$foo = 'bar';
        if (true) {
          let ns2 = null;
          use(ns$foo);
        }
        """);
    test(
        """
        let ns = {};
        ns.foo = 'bar';
        if (true) {
          let ns2 = ns;
          use?.(ns2?.foo);
        }
        """,
        """
        let ns = {};
        ns.foo = 'bar';
        if (true) {
          let ns2 = null;
          use?.(ns?.foo);
        }
        """);
  }

  @Test
  public void testLocalAliasWithLet4() {
    test(
        """
        let ns = {};
        ns.foo = 'bar';
        if (true) {
          let baz = ns.foo;
          use(baz);
        }
        """,
        """
        var ns$foo = 'bar';
        if (true) {
          let baz = null;
          use(ns$foo);
        }
        """);
  }

  @Test
  public void testLocalAliasWithLet5() {
    // For local variables (VAR, CONST, or LET) we only handle cases where the alias is a variable,
    // not a property.
    testSame(
        """
        let ns = {};
        ns.foo = 'bar';
        if (true) {
          let ns2 = {};
          ns2.baz = ns;
          use(ns2.baz.foo);
        }
        """);
  }

  @Test
  public void testGlobalAliasWithConst() {
    test(
        "const ns = {}; ns.foo = 'bar'; const ns2 = ns; use(ns2.foo);", //
        "var ns$foo = 'bar'; const ns2 = null; use(ns$foo);");
    test(
        "const ns = {}; ns.foo = 'bar'; const ns2 =   ns; use?.(ns2?.foo);", //
        "const ns = {}; ns.foo = 'bar'; const ns2 = null; use?.( ns?.foo);");
  }

  @Test
  public void testLocalAliasWithConst1() {
    test(
        """
        const ns = {};
        ns.foo = 'bar';
        function f() {
          const ns2 = ns;
          use(ns2.foo);
        }
        """,
        """
        var ns$foo = 'bar';
        function f() {
          const ns2 = null;
          use(ns$foo);
        }
        """);
    test(
        """
        const ns = {};
        ns.foo = 'bar';
        function f() {
          const ns2 = ns;
          use?.(ns2?.foo);
        }
        """,
        """
        const ns = {};
        ns.foo = 'bar';
        function f() {
          const ns2 = null;
          use?.(ns?.foo);
        }
        """);
  }

  @Test
  public void testLocalAliasWithConst2() {
    test(
        """
        const ns = {};
        ns.foo = 'bar';
        function f() {
          if (true) {
            const ns2 = ns;
            use(ns2.foo);
          }
        }
        """,
        """
        var ns$foo = 'bar';
        function f() {
          if (true) {
            const ns2 = null;
            use(ns$foo);
          }
        }
        """);
    test(
        """
        const ns = {};
        ns.foo = 'bar';
        function f() {
          if (true) {
            const ns2 = ns;
            use?.(ns2?.foo);
          }
        }
        """,
        """
        const ns = {};
        ns.foo = 'bar';
        function f() {
          if (true) {
            const ns2 = null;
            use?.(ns?.foo);
          }
        }
        """);
  }

  @Test
  public void testLocalAliasWithConst3() {
    test(
        """
        const ns = {};
        ns.foo = 'bar';
        if (true) {
          const ns2 = ns;
          use(ns2.foo);
        }
        """,
        """
        var ns$foo = 'bar';
        if (true) {
          const ns2 = null;
          use(ns$foo);
        }
        """);
    test(
        """
        const ns = {};
        ns.foo = 'bar';
        if (true) {
          const ns2 = ns;
          use?.(ns2?.foo);
        }
        """,
        """
        const ns = {};
        ns.foo = 'bar';
        if (true) {
          const ns2 = null;
          use?.(ns?.foo);
        }
        """);
  }

  @Test
  public void testLocalAliasWithConst4() {
    test(
        """
        const ns = {};
        ns.foo = 'bar';
        if (true) {
          const baz = ns.foo;
          use(baz);
        }
        """,
        """
        var ns$foo = 'bar';
        if (true) {
          const baz = null;
          use(ns$foo);
        }
        """);
  }

  @Test
  public void testLocalAliasWithConst5() {
    // For local variables (VAR, CONST, or LET) we only handle cases where the alias is a variable,
    // not a property.
    testSame(
        """
        const ns = {};
        ns.foo = 'bar';
        if (true) {
          const ns2 = {};
          ns2.baz = ns;
          use(ns2.baz.foo);
        }
        """);
  }

  @Test
  public void testLocalAliasInsideClass() {
    test(
        "var a = {b: 5}; class A { fn() { var c = a; use(a.b); } }", //
        "var a$b = 5; class A { fn() { var c = null; use(a$b); } }");
    test(
        "var a = {b: 5}; class A { fn() { var c =    a; use?.(a?.b); } }", //
        "var a = {b: 5}; class A { fn() { var c = null; use?.(a?.b); } }");
  }

  @Test
  public void testEs6ClassStaticProperties() {
    // Collapsing static properties (A.foo and A.useFoo in this case) is known to be unsafe.
    test(
        srcs(
            """
            class A {
              static useFoo() {
                alert(this.foo);
              }
            }
            A.foo = 'bar';
            const B = A;
            B.foo = 'baz';
            B.useFoo();
            """),
        expected(
            """
            var A$useFoo = function() { alert(this.foo); };
            class A {}
            var A$foo = 'bar';
            const B = null;
            A$foo = 'baz';
            A$useFoo();
            """),
        warning(RECEIVER_AFFECTED_BY_COLLAPSE));

    // Adding @nocollapse makes this safe.
    test(
        """
        class A {
          /** @nocollapse */
          static useFoo() {
            alert(this.foo);
          }
        }
        /** @nocollapse */
        A.foo = 'bar';
        const B = A;
        B.foo = 'baz';
        B.useFoo();
        """,
        """
        class A {
          /** @nocollapse */
          static useFoo() {
            alert(this.foo);
          }
        }
        /** @nocollapse */
        A.foo = 'bar';
        const B = null;
        A.foo = 'baz';
        A.useFoo();
        """);
  }

  @Test
  public void testEs6ClassStaticProperties_asStaticField() {
    // Collapsing static properties (A.foo and A.useFoo in this case) is known to be unsafe.
    test(
        srcs(
            """
            class A {
              static useFoo() {
                alert(this.foo);
              }
              static foo = 'bar';
            }
            const B = A;
            B.foo = 'baz';
            B.useFoo();
            """),
        expected(
            """
            var A$useFoo = function() {
              alert(this.foo);
            };
            var A$foo;
            var A$$0jscomp$0staticInit$0m1146332801$00 = function() {
              A$foo = "bar";
            };
            class A {}
            A$$0jscomp$0staticInit$0m1146332801$00();
            const B = null;
            A$foo = "baz";
            A$useFoo();
            """),
        warning(RECEIVER_AFFECTED_BY_COLLAPSE));

    // Adding @nocollapse makes this safe.
    test(
        """
        class A {
          /** @nocollapse */
          static useFoo() {
            alert(this.foo);
          }
          /** @nocollapse */
          static foo = 'bar';
        }
        const B = A;
        B.foo = 'baz';
        B.useFoo();
        """,
        """
        var A$$0jscomp$0staticInit$0m1146332801$00 = function() {
          A.foo = "bar";
        };
        class A {
          /** @nocollapse */
          static useFoo() {
            alert(this.foo);
          }
          /** @nocollapse */
          static foo;
        }
        A$$0jscomp$0staticInit$0m1146332801$00();
        const B = null;
        A.foo = "baz";
        A.useFoo();
        """);
  }

  @Test
  public void testClassStaticInheritance_method() {
    test(
        "class A { static s() {} } class B extends A {} const C = B;    C.s();", //
        "var A$s = function() {}; class A {} class B extends A {} const C = null; A$s();");

    test(
        "class A { static s() {} } class B extends A {} B.s();", //
        "var A$s = function() {}; class A {} class B extends A {} A$s();");

    test(
        "class A {}     A.s = function() {}; class B extends A {} B.s();", //
        "class A {} var A$s = function() {}; class B extends A {} A$s();");
  }

  @Test
  public void testClassStaticInheritance_propertyAlias() {
    test(
        "class A {}     A.staticProp = 6; class B extends A {} let b = new B;", //
        "class A {} var A$staticProp = 6; class B extends A {} let b = new B;");

    test(
        "class A {}     A.staticProp = 6; class B extends A {} use(B.staticProp);", //
        "class A {} var A$staticProp = 6; class B extends A {} use(A$staticProp);");
    test(
        "class A {}     A.staticProp = 6; class B extends A {} use(B?.staticProp);",
        // TODO(b/148237949): collapse breaks `B?.staticProp` reference
        "class A {} var A$staticProp = 6; class B extends A {} use(B?.staticProp);");
  }

  @Test
  public void testClassStaticInheritance_propertyWithSubproperty() {
    test(
        "class A {}     A.ns = {foo: 'bar'}; class B extends A {} use(B.ns.foo);", //
        "class A {} var A$ns$foo = 'bar'; class B extends A {} use(A$ns$foo);");
    test(
        "class A {}     A.ns = {foo: 'bar'}; class B extends A {} use(B.ns?.foo);", //
        "class A {} var A$ns = {foo: 'bar'}; class B extends A {} use(A$ns?.foo);");

    test(
        "class A {} A.ns = {}; class B extends A {}     B.ns.foo = 'baz'; use(B.ns.foo);",
        "class A {}            class B extends A {} var A$ns$foo = 'baz'; use(A$ns$foo);");
    test(
        "class A {}     A.ns = {}; class B extends A {} B.ns.foo = 'baz'; use(B.ns?.foo);",
        "class A {} var A$ns = {}; class B extends A {} A$ns.foo = 'baz'; use(A$ns?.foo);");
  }

  @Test
  public void testClassStaticInheritance_propertyWithShadowing() {
    test(
        "class A {}     A.staticProp = 6; class B extends A {}     B.staticProp = 7;", //
        "class A {} var A$staticProp = 6; class B extends A {} var B$staticProp = 7;");

    // At the time use() is called, B.staticProp is still the same as A.staticProp, but we back off
    // rewriting it because of the shadowing afterwards. This makes CollapseProperties unsafely
    // collapse in this case - the same issue occurs when transpiling down to ES5.
    test(
        "class A {}     A.foo = 6; class B extends A {} use(B.foo); B.foo = 7;", //
        "class A {} var A$foo = 6; class B extends A {} use(B$foo); var B$foo = 7;");
    test(
        "class A {}     A.foo = 6; class B extends A {} use(B?.foo); B.foo = 7;",
        // TODO(b/148237949): collapse properties breaks `B?.foo` reference
        "class A {} var A$foo = 6; class B extends A {} use(B?.foo); var B$foo = 7;");
  }

  @Test
  public void testClassStaticInheritance_cantDetermineSuperclass() {
    // Here A.foo and B.foo are unsafely collapsed because getSuperclass() creates an alias for them
    test(
        """
        class A {}
        A.foo = 5;
        class B {}
        B.foo = 6;
        function getSuperclass() { return 1 < 2 ? A : B; }
        class C extends getSuperclass() {}
        use(C.foo);
        """,
        """
        class A {
        }
        var A$foo = 5;
        class B {
        }
        var B$foo = 6;
        function getSuperclass() {
          return 1 < 2 ? A : B;
        }
        const CLASS_EXTENDS$0 = getSuperclass();
        class C extends CLASS_EXTENDS$0 {
        }
        use(C.foo);
        """);
  }

  @Test
  public void testTypeScriptDecoratedClass() {
    // TypeScript 5.8 emits this for decorated classes.
    test(
        """
        class A {
          static getId() {
            return A.ID;
          }
        }
        A.ID = 'a';
        tslib.__decorate([], A);
        if (false) {
          /** @const {string} */ A.ID;
        }
        console.log(A.getId());
        """,
        """
        var A$getId = function() {
          return A$ID;
        };
        class A {
        }
        var A$ID = "a";
        tslib.__decorate([], A);
        if (false) {
          A$ID;
        }
        console.log(A$getId());
        """);
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
        class Foo {}
        var ns$clazz = null;
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
        class Foo {}
        var Foo$Builder = class {}
        var ns$clazz = null;
        var Bar = class extends Foo$Builder {}
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
        class Foo {}
        var Foo$Builder = class {}
        var Foo$Builder$baz = 3;
        var ns$clazz = null;
        var Bar = class extends Foo$Builder {}
        use(Foo$Builder$baz);
        """);
    test(
        """
        var ns = {};
        class Foo {}
        Foo.Builder = class {}
        Foo.Builder.baz = 3;
        ns.clazz = Foo;
        var Bar = class extends ns.clazz?.Builder {}
        use(Bar?.baz);
        """,
        """
        class Foo {
        }
        var Foo$Builder = class {
        };
        var Foo$Builder$baz = 3;
        var ns$clazz = null;
        // TODO(b/148237979): Collapse above breaks this reference.
        const CLASS_EXTENDS$0 = Foo?.Builder;
        var Bar = class extends CLASS_EXTENDS$0 {
        };
        // TODO(b/148237979): Collapse above breaks this reference.
        use(Bar?.baz);
        """);
  }

  @Test
  public void testDestructuringAlias1() {
    testSame("var a = { x: 5 }; var [b] = [a]; use(b.x);");
  }

  @Test
  public void testDestructuringAlias2() {
    testSame("var a = { x: 5 }; var {b} = {b: a}; use(b.x);");
  }

  @Test
  public void testDestructingAliasWithConstructor() {
    test(
        """
        var ns = {};
        /** @constructor */
        ns.ctor = function() {}
        const {ctor} = ns;
        let c = new ctor;
        """,
        """
        /** @constructor */
        var ns$ctor = function() {}
        const ctor = null;
        let c = new ns$ctor;
        """);
  }

  @Test
  public void testDestructuringThatUsedToCrash() {
    test(
        """
        var CakeFlavors = {
          CARROT: 1,
          TIRAMISU: 2,
        };
        class UglyCake {}
        const Cake = UglyCake;
        /** @const */
        UglyCake.Flavors = CakeFlavors;
        const {Flavors: {CARROT, TIRAMISU}} = Cake;
        alert(CARROT, TIRAMISU);
        """,
        """
        var CakeFlavors$CARROT = 1;
        var CakeFlavors$TIRAMISU = 2;
        class UglyCake {}
        const Cake = null;
        /** @const */
        var UglyCake$Flavors = null;
        const destructuring$m1146332801$0 = null;
        const CARROT = null;
        const TIRAMISU = null;
        alert(CakeFlavors$CARROT, CakeFlavors$TIRAMISU);
        """);
  }

  @Test
  public void namespaceInDestructuringPattern() {
    testSame(
        """
        const ns = {};
        ns.x = 1;
        ns.y = 2;
        let {x, y} = ns;
        x = 4; // enforce that we can't inline x -> ns.x because it's set multiple times
        use(x + y);
        """);
  }

  @Test
  public void inlineDestructuringPatternConstructorWithProperty() {
    test(
        """
        const ns = {};
        /** @constructor */
        ns.Y = function() {};
        ns.Y.prop = 3;
        const {Y} = ns;
        use(Y.prop);
        """,
        """
        /** @constructor */
        var ns$Y = function() {};
        var ns$Y$prop = 3;
        const Y = null;
        use(ns$Y$prop);
        """);

    test(
        """
        const ns = {};
        /** @constructor */
        ns.Y = function() {};
        ns.Y.prop = 3;
        const {Y} = ns;
        use(Y?.prop);
        """,
        """
        /** @constructor */
        var ns$Y = function() {};
        // TODO(bradfordcsmith): This collapse breaks the optional chain
        //     reference below.
        var ns$Y$prop = 3;
        const Y = null;
        use(ns$Y?.prop);
        """);

    // Only `const` destructuring aliases have special handling by AggressiveInlineAliases
    testSame(
        """
        const ns = {};
        /** @constructor */
        ns.Y = function() {};
        ns.Y.prop = 3;
        let {Y} = ns;
        use(Y.prop);
        """,
        PARTIAL_NAMESPACE_WARNING);
  }

  @Test
  public void testDefaultParamAlias1() {
    test(
        "var a = {b: 5}; var b = a; function f(x=b) { alert(x.b); }", //
        "var a = {b: 5}; var b = null; function f(x=a) { alert(x.b); }");
  }

  @Test
  public void testDefaultParamAlias2() {
    test(
        "var a = {b: {c: 5}}; var b = a; function f(x=b.b) { alert(x.c); }", //
        "var a$b = {c: 5}; var b = null; function f(x=a$b) { alert(x.c); }");
    test(
        "var a = {b: {c: 5}}; var b =    a; function f(x=b?.b) { alert(x?.c); }", //
        "var a = {b: {c: 5}}; var b = null; function f(x=a?.b) { alert(x?.c); }");
  }

  @Test
  public void testAliasPropertyOnUnsafelyRedefinedNamespace() {
    testSame("var obj = {foo: 3}; var foo = obj.foo; obj = {}; alert(foo);");
  }

  @Test
  public void testAliasPropertyOnSafelyRedefinedNamespace() {
    // non-constructor property doesn't get collapsed
    test(
        "var obj = {foo: 3}; var foo = obj.foo; obj = obj || {}; alert(foo);", //
        "var obj = {foo: 3}; var foo = null   ; obj = obj || {}; alert(obj.foo);");

    // constructor property does get collapsed
    test(
        """
        var ns = {};
        /** @constructor */ ns.ctor = function() {};
        ns.ctor.foo = 3;
        var foo = ns.ctor.foo;
        ns = ns || {};
        alert(foo);
        """,
        """
        var ns = {};
        /** @constructor */ var ns$ctor = function() {};
        var ns$ctor$foo = 3;
        var foo = null;
        ns = ns || {};
        alert(ns$ctor$foo);
        """);

    // NOTE(lharker): this mirrors current code in Closure library
    test(
        """
        var goog = {};
        goog.module = function() {};
        /** @constructor */ goog.module.ModuleManager = function() {};
        goog.module.ModuleManager.getInstance = function() {};
        goog.module = goog.module || {};
        var ModuleManager = goog.module.ModuleManager;
        alert(ModuleManager.getInstance());
        """,
        """
        var goog$module = function() {};
        /** @constructor */ var goog$module$ModuleManager = function() {};
        var goog$module$ModuleManager$getInstance = function() {};
        goog$module = goog$module || {};
        var ModuleManager = null;
        alert(goog$module$ModuleManager$getInstance());
        """);
  }

  @Test
  public void testClassInObjectLiteral() {
    test(
        """
        var obj = {
          foo: class {}
        };
        """,
        """
        const CLASS_DECL$0 = class {
        };
        var obj$foo = CLASS_DECL$0;
        """);

    // TODO(lharker): this is unsafe, obj$foo.foo is undefined now that A$foo is collapsed
    test(
        """
        class A {}
        A.foo = 3;
        var obj = {foo: class extends A {}};
        use(obj.foo.foo);
        """,
        """
        class A {
        }
        var A$foo = 3;
        const CLASS_DECL$0 = class extends A {
        };
        var obj$foo = CLASS_DECL$0;
        use(obj$foo.foo);
        """);
  }

  @Test
  public void testToStringValueOfPropertiesInObjectLiteral() {
    // toString/valueOf are not collapsed because they are properties implicitly used as part of the
    // JS language
    testSame(
        """
        let z = {
          toString() { return 'toString'; },
          valueOf() { return 'valueOf'; }
        };
        var zAsString = z + "";
        """);
  }

  @Test
  public void testToStringValueOfNonMethodPropertiesInObjectLiteral() {
    testSame(
        """
        let z = {
          toString: function() { return 'a'; },
          valueOf: function() { return 3; },
        };
        """);
  }

  @Test
  public void testToStringValueOfPropertiesInObjectLiteralAssignmentDepth() {
    test(
        """
        var a = {}; a.b = {}; a.b.c = {};
        a.b.c.d = {
          toString() { return 'a'; },
          valueOf: function() { return 3; },
        };
        """,
        """
        var a$b$c$d = {
          toString() { return 'a'; },
          valueOf: function() { return 3; },
        };
        """);
  }

  @Test
  public void testLoopInAliasChainOfTypedefConstructorProperty() {
    test(
        """
        /** @constructor */ var Item = function() {};
        /** @typedef {number} */ Item.Models;
        Item.Models = Item.Models;
        """,
        """
        /** @constructor */ var Item = function() {};
        Item$Models;
        var Item$Models = Item$Models;
        """);
    test(
        """
        /** @constructor */ var Item = function() {};
        /** @typedef {number} */ Item.Models;
        Item.Models = Item?.Models;
        """,
        """
        /** @constructor */ var Item = function() {};
        Item$Models;
        // TODO(b/148237949): Collapsing breaks this reference.
        var Item$Models = Item?.Models;
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
            // This call could potentially have changed the value of Foo.Bar, so don't
            // collapse Foo.Bar.baz.A/Foo.Bar.baz.B
            use(Foo);
            var baz = $jscomp$destructuring$var1.baz;
            use(baz.A);
            """));
  }

  @Test
  public void testInliningPropertiesOnEscapedNamespace_withDeclaredType() {
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
            use(Foo);
            var baz = $jscomp$destructuring$var1.baz;
            use(baz.A);
            """),
        expected(
"""
/** @constructor */
function Foo() {}
/** @constructor */
var Foo$Bar = function() {};
var Foo$Bar$baz$A = 1;
var Foo$Bar$baz$B = 2;

var $jscomp$destructuring$var1 = null;
// This call could potentially read Foo.Bar, which will now be broken.
// AggressiveInlineAliases/CollapseProperties intentionally generate unsafe code
// in this case. The main motivation is to collapse long namespaces generated by
// goog.provide or goog.module.declareLegacyNamespace()
// https://developers.google.com/closure/compiler/docs/limitations#implications-of-object-property-flattening
use(Foo);
var baz = null;
use(Foo$Bar$baz$A);
"""));
  }

  @Test
  public void testDontInlinePropertiesOnNamespace_withNoCollapse() {
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
            // This call could potentially have changed the value of Foo.Bar, and without
            // @nocollapse on Foo.Bar and Foo.Bar.baz the compiler would generate unsafe code.
            use(Foo);
            var baz = $jscomp$destructuring$var1.baz;
            use(baz.A);
            """));
  }

  @Test
  public void testInlinePropertyOnAliasedConstructor() {
    // TODO(b/117905881): as long as we unsafely collapse Foo.prop -> Foo$prop, there's no way to
    // safely rewrite the "use(ns.alias.prop)" call in the compiler
    // Either
    // a) we replace "ns.alias.prop" with "Foo$prop" or
    // b) we leave "ns.alias.prop" as is.

    // Option a) can break code if the "use(ns)" call actually reassigns ns.alias (which is not
    // declared const).
    // Option b) breaks code if "use(ns)" does NOT reassign ns.alias, since now
    // ns.alias.prop is Foo.prop, which is undefined.
    // but we might want to consider movign to (b) instead of (a), since CollapseProperties will
    // also break case (a)
    test(
        externs("function use(ctor) {}"),
        srcs(
            """
            /** @constructor */
            function Foo() {}
            Foo.prop = 2;
            var ns = {};
            ns.alias = Foo;
            use(ns);
            use(ns.alias.prop);
            """),
        expected(
            """
            /** @constructor */
            function Foo() {}
            var Foo$prop = 2;
            var ns = {};
            ns.alias = Foo;
            use(ns);
            use(ns.alias.prop);
            """));
  }

  @Test
  public void testClassStaticMemberAccessedWithSuper() {
    test(
        """
        class Bar {
          static double(n) {
            return n*2
          }
        }
        class Baz extends Bar {
          static quadruple(n) {
            return 2 * super.double(n);
          }

          static val1;
          static {
            Baz.val1 = super.double(5);
          }

          static val2 = super.double(6);
        }
        """,
        """
        var Bar$double = function(n) {
          return n * 2;
        };
        class Bar {}
        var Baz$quadruple = function(n$jscomp$1) {
          return 2 * Bar$double(n$jscomp$1);
        };
        var Baz$val1;
        var Baz$val2;
        var Baz$$0jscomp$0staticInit$0m1146332801$00 = function() {
          {
            Baz$val1 = Bar$double(5);
          }
          Baz$val2 = Bar$double(6);
        };
        class Baz extends Bar {}
        Baz$$0jscomp$0staticInit$0m1146332801$00();
        """);
  }

  @Test
  public void testClassStaticMemberAccessedWithSuperAndThis() {
    var src =
        """
        class Bar {
          static get name() {
            return 'Bar';
          }
          /** @nocollapse */
          static getClassname() {
            return `${this.name} class`;
          }
        }
        class Baz extends Bar {
          static get name() {
            return 'Baz';
          }
          static get classname() {
            return `${super.getClassname()} - is a subclass`;
          }
        }
        """;

    setAssumeStaticInheritanceIsNotUsed(false);
    testSame(src);

    setAssumeStaticInheritanceIsNotUsed(true);
    test(
        src,
        """
        class Bar {
          static get name() {
            return "Bar";
          }
          /** @nocollapse */
          static getClassname() {
            return `${this.name} class`;
          }
        }
        class Baz extends Bar {
          static get name() {
            return "Baz";
          }
          static get classname() {
            return `${Bar.getClassname()} - is a subclass`;
          }
        }
        """);
  }

  @Test
  public void testClassStaticMemberAccessedWithThis() {
    test(
        """
        class Baz extends Bar {
          static double(n) {
            return 2 * n;
          }

          static val1;
          static {
            this.val1 = this.double(1);
          }

          static val2;
          static {
            Baz.val2 = Baz.double(2);
          }

          static val3 = this.double(3);

          static val4 = Baz.double(4);
        }
        """,
        """
        var Baz$double = function(n) {
          return 2 * n;
        };
        var Baz$val1;
        var Baz$val2;
        var Baz$val3;
        var Baz$val4;
        var Baz$$0jscomp$0staticInit$0m1146332801$00 = function() {
          {
            Baz$val1 = Baz$double(1);
          }
          {
            Baz$val2 = Baz$double(2);
          }
          Baz$val3 = Baz$double(3);
          Baz$val4 = Baz$double(4);
        };
        class Baz extends Bar {}
        Baz$$0jscomp$0staticInit$0m1146332801$00();
        """);
  }

  @Test
  public void testClassStaticGetterAccessedWithSuper() {
    var src =
        """
        function JSCompiler_renameProperty(str, strContainerType) {
          return a;
        }

        class Parent {
          static getName() {
            return 'Parent';
          }
          static get greeting() {
            return 'Hello ' + this.getName();
          }
        }
        class Child extends Parent {
          static getName() {
            return 'Child';
          }
          static msg = super.greeting;  // 'Hello Child'
        }
        """;

    // non-strict
    test(
        src,
        """
        function JSCompiler_renameProperty(str, strContainerType) {
          return a;
        }
        var Parent$getName = function() {
          return "Parent";
        };
        class Parent {
          static get greeting() {
            return "Hello " + this.getName();
          }
        }
        var Child$getName = function() {
          return "Child";
        };
        var Child$msg;
        var Child$$0jscomp$0staticInit$0m1146332801$00 = function() {
          Child$msg = Parent.greeting;
        };
        class Child extends Parent {}
        Child$$0jscomp$0staticInit$0m1146332801$00();
        """);

    // strict
    setAssumeStaticInheritanceIsNotUsed(false);
    test(
        src,
        """
        function JSCompiler_renameProperty(str, strContainerType) {
          return a;
        }
        class Parent {
          static getName() {
            return "Parent";
          }
          static get greeting() {
            return "Hello " + this.getName();
          }
        }
        class Child extends Parent {
          static getName() {
            return "Child";
          }
          static msg;
          static STATIC_INIT$0() {
            Child.msg = super.greeting;
          }
        }
        Child.STATIC_INIT$0();
        """);
  }

  @Test
  public void testClassExpressionWithInnerClassName() {
    test(
        """
        const OuterName = class InnerName {
          static sf1 = 1;
          static sf2 = InnerName.sf1;
          static sf3 = this.sf2;

          static {
            Object.defineProperties(InnerName.prototype, {bar: {value: 1}});
            InnerName.sf2++;
            this.sf3++;
          }
        };
        alert(OuterName.sf3);
        """,
        """
        var OuterName$sf1;
        var OuterName$sf2;
        var OuterName$sf3;
        var OuterName$$0jscomp$0staticInit$0m1146332801$00 = function() {
          OuterName$sf1 = 1;
          OuterName$sf2 = OuterName$sf1;
          OuterName$sf3 = OuterName$sf2;
          {
            Object.defineProperties(OuterName.prototype, {bar:{value:1}});
            OuterName$sf2++;
            OuterName$sf3++;
          }
        };
        const OuterName = class {};
        OuterName$$0jscomp$0staticInit$0m1146332801$00();
        alert(OuterName$sf3);
        """);
  }

  @Test
  public void testCommaCallAliasing1() {
    test(
        """
        var xid = function(a) {};
        xid.internal_ = function() {};
        (0,xid)('abc');
        """,
        """
        var xid = function(a) {};
        // The indirect call below does not prevent this property collapse
        var xid$internal_ = function() {};
        (0,xid)('abc');
        """);
  }

  @Test
  public void testCommaCallAliasing2() {
    test(
        """
        var xid = function(a) {};
        xid.internal_ = function() {};
        fn((0,xid)('abc'));
        """,
        """
        var xid = function(a) {};
        // The indirect call below does not prevent this property collapse
        var xid$internal_ = function() {};
        fn((0,xid)('abc'));
        """);
  }

  @Test
  public void testCommaCallAliasing3() {
    test(
        """
        var xid = function(a) {};
        xid.internal_ = function() {};
        throw ((0, xid), 0);
        """,
        """
        var xid = function(a) {};
        // The indirect call below does not prevent this property collapse
        var xid$internal_ = function() {};
        throw ((0, xid), 0);
        """);
  }

  @Test
  public void testHookNewAliasing1() {
    testSame(
        """
        var xid = function(a) {};
        xid.internal_ = function() {};
        // The indirect call prevents this property collapse
        var xx = new Thing(x ? xid : abc).method();
        """);
  }

  @Test
  public void testHookGetProp1() {
    testSame(
        """
        var xid = function(a) {};
        xid.internal_ = function() {};
        // The indirect call prevents this property collapse
        var xx = (x ? xid : abc).method();
        """);
  }

  @Test
  public void testHookGetProp2() {
    test(
        """
        var xid = function(a) {};
        xid.internal_ = function() {};
        var xx = ((x ? xid : abc) - def).toExponential(5);
        """,
        """
        var xid = function(a) {};
        // The indirect call below does not prevent this property collapse
        var xid$internal_ = function() {};
        var xx = ((x ? xid : abc) - def).toExponential(5);
        """);
  }

  @Test
  public void conditionallyLoadedChunk_unsafeToInlineVar() {
    JSChunk[] chunks =
        JSChunkGraphBuilder.forChain()
            // m1, always loaded
            .addChunk(
                """
                var modFactory;
                const registry = {};
                const factories = {};
                factories.one = function() {};

                function build() {
                  const mod = modFactory;
                  if (mod) mod();
                }
                """)
            // m2, conditionally loaded after m1
            .addChunk("modFactory = factories.one; modFactory();")
            .build();

    test(
        srcs(chunks[0], chunks[1]),
        expected(
            """
            var modFactory;
            var factories$one = function() {};

            function build() {
              const mod = modFactory;
              if (mod) mod();
            }
            """,
            "modFactory = factories$one; factories$one();"));
  }

  @Test
  public void conditionallyLoadedChunk_unsafeToInlineQname() {
    JSChunk[] chunks =
        JSChunkGraphBuilder.forChain()
            // m1, always loaded
            .addChunk(
                """
                const registry = {};
                const factories = {};
                factories.one = function() {};

                function build() {
                  const mod = registry.mod;
                  if (mod) mod();
                }
                """)
            // m2, conditionally loaded after m1
            .addChunk("registry.mod = factories.one; registry.mod();")
            .build();

    test(
        srcs(chunks[0], chunks[1]),
        expected(
            """
            var factories$one = function() {};

            function build() {
              const mod = null;
              if (registry$mod) registry$mod();
            }
            """,
            "var registry$mod = factories$one; factories$one();"));
  }

  @Test
  public void conditionallyLoadedChunk_safeToInline() {
    JSChunk[] chunks =
        JSChunkGraphBuilder.forChain()
            // m1, always loaded
            .addChunk(
                """
                const registry = {};
                const factories = {};
                factories.one = function() {};
                registry.mod = factories.one;
                """)
            // m2, conditionally loaded after m1
            // Even though this chunk is conditionally loaded, it depends on m1, so it's safe to
            // inline the value of registry.mod because if this chunk executes, m1 must have
            // executed first.
            .addChunk(
                """
                function build() {
                  const mod = registry.mod;
                  if (mod) mod();
                }
                """)
            .build();

    test(
        srcs(chunks[0], chunks[1]),
        expected(
            // m1
            """
            var factories$one = function() {};
            var registry$mod = null;
            """,
            // m2
            """
            function build() {
              const mod = null;
              if (factories$one) factories$one();
            }
            """));
  }
}
