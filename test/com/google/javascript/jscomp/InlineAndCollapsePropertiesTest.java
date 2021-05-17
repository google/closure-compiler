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

import com.google.javascript.jscomp.CompilerOptions.ChunkOutputType;
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link InlineAndCollapseProperties}. */
@RunWith(JUnit4.class)
public final class InlineAndCollapsePropertiesTest extends CompilerTestCase {

  private static final String EXTERNS =
      lines(
          "var window;", //
          "function alert(s) {}",
          "function parseInt(s) {}",
          "/** @constructor */ function String() {};",
          "var arguments");

  public InlineAndCollapsePropertiesTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return InlineAndCollapseProperties.builder(compiler)
        .setPropertyCollapseLevel(PropertyCollapseLevel.ALL)
        .setChunkOutputType(ChunkOutputType.GLOBAL_NAMESPACE)
        .setHaveModulesBeenRewritten(false)
        .setModuleResolutionMode(ResolutionMode.BROWSER)
        .build();
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableNormalize();
    disableCompareJsDoc();
  }

  @Test
  public void testConstObjRefInTemplateLiteralComputedPropKey() {
    test(
        srcs(
            lines(
                "var module$name = {}", //
                "module$name.cssClasses = {",
                "  CLASS_A: 'class-a',",
                "};",
                "",
                "module$name.oldCssClassesMap = {",
                "  [`${module$name.cssClasses.CLASS_A}`]: 'old-class-a',",
                "};",
                "")),
        // TODO(bradfordcsmith): Shouldn't we be leaving the computed property in place?
        expected("var module$name$cssClasses$CLASS_A = 'class-a';"));
  }

  @Test
  public void testObjLitSpread() {
    test(
        lines(
            "const other = {bar: 'some' };",
            "let ns = {}",
            "ns.foo = { ...other };",
            "use(ns.foo.bar);"),
        lines("const other = {bar: 'some' };", "var ns$foo = { ...other };", "use(ns$foo.bar);"));
  }

  // same behavior with transpiled version of above test
  @Test
  public void testObjAssign() {
    test(
        lines(
            "const other = {bar: 'some' };",
            "let ns = {}",
            "ns.foo = Object.assign({}, other);",
            "use(ns.foo.bar);"),
        lines(
            "const other = {bar: 'some' };",
            "var ns$foo = Object.assign({}, other);",
            "use(ns$foo.bar);"));
  }

  @Test
  public void testObjLitSpread_twoSpreads() {
    test(
        lines(
            "const other = {bar: 'bar' };",
            "const another = {baz: 'baz' };",
            "let ns = {};",
            "ns.foo = { ...other, ...another };",
            "use(ns.foo.bar, ns.foo.baz);"),
        lines(
            "const other = {bar: 'bar' };",
            "const another = {baz : 'baz'};",
            "var ns$foo = { ...other, ...another };",
            "use(ns$foo.bar, ns$foo.baz);"));
  }

  // same behavior with transpiled version of above test
  @Test
  public void testObjAssign_twoSpreads() {
    test(
        lines(
            "const other = {bar: 'bar'};",
            "const another = {baz: 'baz'};",
            "let ns = {};",
            "ns.foo = Object.assign({}, other, another);",
            "use(ns.foo.bar, ns.foo.baz);"),
        lines(
            "const other = {bar: 'bar'};",
            "const another = {baz : 'baz'};",
            "var ns$foo = Object.assign({}, other, another);",
            "use(ns$foo.bar, ns$foo.baz);"));
  }

  @Test
  public void testObjLitSpread_withNormalPropAfter() {
    test(
        lines(
            "const other = {bar: 'some' };",
            "let ns = {}",
            "ns.foo = { ...other, prop : 0};",
            "use(ns.foo.bar, ns.foo.prop);"),
        lines(
            "const other = {bar: 'some' };", //
            "var ns$foo$prop = 0;",
            "var ns$foo = { ...other};",
            "use(ns$foo.bar, ns$foo$prop);"));
  }

  @Test
  public void testObjAssign_withNormalPropAfter() {
    test(
        lines(
            "const other = {bar: 'some' };",
            "let ns = {}",
            "ns.foo = Object.assign({}, other, {prop : 0});",
            "use(ns.foo.bar, ns.foo.prop);"),
        lines(
            "const other = {bar: 'some' };", //
            "var ns$foo = Object.assign({}, other, {prop:0});",
            "use(ns$foo.bar, ns$foo.prop);")); // both properties not collapsed.
  }

  @Test
  public void testObjLitSpread_chained() {
    test(
        lines(
            "const other = {bar: 'some' };",
            "let ns = {...other}",
            "let ns2 = { ...ns };",
            "use(ns2.bar);"),
        lines(
            "const other = {bar: 'some' };",
            "let ns = { ...other };",
            "let ns2 = { ...ns };",
            "use(ns2.bar);"));
  }

  // same behavior with transpiled version of above test
  @Test
  public void testObjAssign_chained() {
    test(
        lines(
            "const other = {bar: 'some' };",
            "let ns = Object.assign({}, other);",
            "let ns2 = Object.assign({}, ns);",
            "use(ns2.bar);"),
        lines(
            "const other = {bar: 'some' };",
            "let ns = Object.assign({}, other);",
            "let ns2 = Object.assign({}, ns);",
            "use(ns2.bar);"));
  }

  @Test
  public void testObjLitSpread_chainedWithGetProp() {
    test(
        lines(
            "const other = {bar: 'some' };",
            "let ns = {...other};",
            "let ns2 = {};",
            "ns2.foo = { ...ns };",
            "use(ns2.foo.bar);"),
        lines(
            "const other = {bar: 'some' };",
            "let ns = { ...other };",
            "var ns2$foo = { ...ns };",
            "use(ns2$foo.bar);"));
  }

  // same behavior with transpiled version of above test
  @Test
  public void testObjAssign_chainedWithGetProp() {
    test(
        lines(
            "const other = {bar: 'some' };",
            "let ns = Object.assign({}, other);",
            "let ns2 = {};",
            "ns2.foo = Object.assign({}, ns);",
            "use(ns2.foo.bar);"),
        lines(
            "const other = {bar: 'some' };",
            "let ns = Object.assign({}, other);",
            "var ns2$foo = Object.assign({}, ns);",
            "use(ns2$foo.bar);"));
  }

  @Test
  public void testGitHubIssue3733() {
    testSame(
        srcs(
            lines(
                "const X = {Y: 1};",
                "",
                "function fn(a) {",
                "  if (a) {",
                // Before issue #3733 was fixed GlobalNamespace failed to see this reference
                // as creating an alias for X due to a switch statement that failed to check
                // for the RETURN node type, so X.Y was incorrectly collapsed.
                "    return a ? X : {};",
                "  }",
                "}",
                "",
                "console.log(fn(true).Y);")));
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
  public void testOptChainPreventsInlineAndCollapse() {
    testSame(
        lines(
            "var a = {};",
            "a.b = {};",
            // c is not really an alias due to optional chain,
            // and optional chain prevents collapse of a.b.
            "var c = a?.b;",
            "use(c);"));
    test(
        lines(
            "var a = {};", //
            "a.b = {};",
            "var b = a.b;", // can be inlined and collapsed
            "b.c = {};",
            "var c = b?.c;", // opt chain prevents both
            "use(c);"),
        lines(
            "var a$b = {};", //
            "var b = null;",
            "a$b.c = {};",
            "var c = a$b?.c;",
            "use(c);"));
    test(
        lines(
            "const a = {};", //
            "a.b = {};",
            "a.b.c = {};",
            "const {b} = a;", // can be inlined and collapsed
            "const c = b?.c;", // opt chain prevents both
            "use(c);"),
        lines(
            "var a$b = {};", //
            "a$b.c = {};",
            "const b = null;",
            "const c = a$b?.c;",
            "use(c);"));
    test(
        lines(
            "const ns = {};", //
            "ns.Y = {};",
            "ns.Y.prop = 3;",
            "const {Y} = ns;",
            "use(Y);"),
        lines(
            "var ns$Y = {};", //
            "ns$Y.prop = 3;",
            "const Y = null;",
            "use(ns$Y);"));
    test(
        lines(
            "var a = {};", //
            "a.b = {};",
            "a.b.c = {};",
            "var c = a.b?.c;",
            "use(c);"),
        lines(
            "var a$b = {};", //
            "a$b.c = {};",
            "var c = a$b?.c;",
            "use(c);"));
    test(
        lines(
            "const ns = {};", //
            "/** @constructor */",
            "ns.Y = function() {};",
            "ns.Y.prop = 3;",
            "const {Y} = ns;",
            "use(Y);"),
        lines(
            "/** @constructor */", //
            "var ns$Y = function() {};",
            "var ns$Y$prop = 3;",
            "const Y = null;",
            "use(ns$Y);"));
    test(
        lines(
            "const ns = {};", //
            "/** @constructor */",
            "ns.Y = function() {};",
            "ns.Y.prop = 3;",
            "const {Y} = ns;",
            "use(Y?.prop);"),
        lines(
            "/** @constructor */", //
            "var ns$Y = function() {};",
            "var ns$Y$prop = 3;",
            "const Y = null;",
            "use(ns$Y?.prop);"));
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
        lines(
            "var a = {}; a.b = {}; a.b.c = {d: 1, /** @nocollapse */ e: 2}; ", //
            "var f = a.b.c.d; var g = a.b.c.e"),
        "var a$b$c$d = 1; var a$b$c = {/** @nocollapse */ e: 2}; var f = null; var g = null;");

    test(
        lines(
            "var a = {}; a.b = {}; a.b.c = {d: 1, /** @nocollapse */ e: 2}; ", //
            "var f = a.b.c.d; var g = a.b.c.e; use(f, g);"),
        lines(
            "var a$b$c$d = 1; var a$b$c = { /** @nocollapse */ e: 2};", //
            "var f = null; var g = null; use(a$b$c$d, a$b$c.e);"));

    testSame(
        lines(
            "var a = {}; /** @nocollapse*/ a.b = {}; ", //
            "a.b.c = {d: 1, e: 2}; ",
            "var f = null; var g = null;"));
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
        lines(
            "var a = {}; var d = a; a.b = function() {};", //
            "/** @constructor */ a.b.c = 0; a.b.c;"),
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
        lines(
            "/** @constructor */ var a = function(){}; a.b = 1; ", //
            "var c = a; c.b = 2; a.b == c.b;"),
        lines(
            "/** @constructor */ var a = function(){}; var a$b = 1;", //
            "var c = null; a$b = 2; a$b == a$b;"));
    test(
        lines(
            "/** @constructor */ var a = function(){}; a.b = 1; ", //
            "var c = a; c.b = 2; a?.b == c?.b;"),
        lines(
            "/** @constructor */ var a = function(){}; var a$b = 1;", //
            "var c = null; a$b = 2; a?.b == a?.b;"));

    // Sometimes we want to prevent static members of a constructor from
    // being collapsed.
    test(
        lines(
            "/** @constructor */ var a = function(){};", //
            "/** @nocollapse */ a.b = 1; var c = a; c.b = 2; a.b == c.b;"),
        lines(
            "/** @constructor */ var a = function(){};", //
            "/** @nocollapse */ a.b = 1; var c = null; a.b = 2; a.b == a.b;"));
    test(
        lines(
            "/** @constructor */ var a = function(){};", //
            "/** @nocollapse */ a.b = 1; var c = a; c.b = 2; a?.b == c?.b;"),
        lines(
            "/** @constructor */ var a = function(){};", //
            "/** @nocollapse */ a.b = 1; var c = null; a.b = 2; a?.b == a?.b;"));
  }

  @Test
  public void testAliasCreatedForFunctionDepth2() {
    test(
        "var a = {}; a.b = function() {}; a.b.c = 1; var d = a.b; a.b.c != d.c;", //
        "var a$b = function() {}; var a$b$c = 1; var d = null; a$b$c != a$b$c;");
    testSame("var a = {}; a.b = function() {}; a.b.c = 1; var d = a?.b; a.b?.c != d?.c;");

    test(
        lines(
            "var a = {}; a.b = function() {}; /** @nocollapse */ a.b.c = 1;", //
            "var d = a.b; a.b.c == d.c;"),
        "var a$b = function() {}; /** @nocollapse */ a$b.c = 1; var d = null; a$b.c == a$b.c;");
  }

  @Test
  public void testAliasCreatedForCtorDepth2() {
    test(
        lines(
            "var a = {}; /** @constructor */ a.b = function() {}; a.b.c = 1; var d = a.b;", //
            "a.b.c == d.c;"),
        lines(
            "/** @constructor */ var a$b = function() {}; var a$b$c = 1; var d = null;", //
            "a$b$c == a$b$c;"));
    testSame(
        srcs(
            lines(
                "var a = {}; /** @constructor */ a.b = function() {}; a.b.c = 1; var d = a?.b;", //
                "a.b?.c == d?.c;")),
        warning(PARTIAL_NAMESPACE_WARNING));
    test(
        lines(
            "var a = {}; /** @constructor */ a.b = function() {}; ", //
            "/** @nocollapse */ a.b.c = 1; var d = a.b;",
            "a.b.c == d.c;"),
        lines(
            "/** @constructor */ var a$b = function() {};",
            "/** @nocollapse */ a$b.c = 1; var d = null;",
            "a$b.c == a$b.c;"));
  }

  @Test
  public void testAliasCreatedForClassDepth1_1() {
    test(
        lines(
            "var a = {}; /** @constructor */ a.b = function(){};", //
            "var c = a; c.b = 0; a.b == c.b;"),
        lines(
            "/** @constructor */ var a$b = function(){};", //
            "var c = null; a$b = 0; a$b == a$b;"));

    test(
        srcs(
            lines(
                "var a = {}; /** @constructor */ a.b = function(){};", //
                // `a?.b` and `c?.b` are aliasing gets
                "var c = a; c.b = 0; a?.b == c?.b;")),
        expected(
            lines(
                "var a = {}; /** @constructor */ a.b = function(){};", //
                // `a?.b` and `c?.b` are aliasing gets
                "var c = null; a.b = 0; a?.b == a?.b;")),
        warning(PARTIAL_NAMESPACE_WARNING));

    testSame(
        lines(
            "var a = {}; /** @constructor @nocollapse */ a.b = function(){};", //
            "var c = 1; c = a; c.b = 0; a.b == c.b;"),
        PARTIAL_NAMESPACE_WARNING);

    testSame(
        lines(
            "var a = {}; /** @constructor @nocollapse */ a.b = function(){};", //
            "var c = 1; c = a; c.b = 0; a?.b == c?.b;"),
        PARTIAL_NAMESPACE_WARNING);

    test(
        lines(
            "var a = {}; /** @constructor @nocollapse */ a.b = function(){};", //
            "var c = a; c.b = 0; a.b == c.b;"),
        lines(
            "var a = {}; /** @constructor @nocollapse */ a.b = function(){};", //
            "var c = null; a.b = 0; a.b == a.b;"));

    test(
        srcs(
            lines(
                "var a = {}; /** @constructor @nocollapse */ a.b = function(){};", //
                "var c = a; c.b = 0; a?.b == c?.b;")),
        expected(
            lines(
                "var a = {}; /** @constructor @nocollapse */ a.b = function(){};", //
                "var c = null; a.b = 0; a?.b == a?.b;")),
        warning(PARTIAL_NAMESPACE_WARNING));

    test(
        lines(
            "var a = {}; /** @constructor @nocollapse */ a.b = function(){};", //
            "var c = a; c.b = 0; a.b == c.b; use(c);"),
        lines(
            "var a = {}; /** @constructor @nocollapse */ a.b = function(){};", //
            "var c = null; a.b = 0; a.b == a.b; use(a);"),
        warning(PARTIAL_NAMESPACE_WARNING));

    test(
        lines(
            "var a = {}; /** @constructor @nocollapse */ a.b = function(){};", //
            "var c = a; c.b = 0; a?.b == c?.b; use(c);"),
        lines(
            "var a = {}; /** @constructor @nocollapse */ a.b = function(){};", //
            "var c = null; a.b = 0; a?.b == a?.b; use(a);"),
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
        lines(
            "/** @constructor */ function a() {} var a$b; var c = a; ", //
            "(function() {a$b = 0;})(); a$b;"),
        lines(
            "/** @constructor */ function a() {} var a$b; var c = null; ", //
            "(function() {a$b = 0;})(); a$b;"));
  }

  @Test
  public void testAddPropertyToUncollapsibleCtorInLocalScopeDepth1() {
    test(
        lines(
            "/** @constructor */ var a = function() {}; var c = a; ", //
            "(function() {a.b = 0;})(); a.b;"),
        lines(
            "/** @constructor */ var a = function() {}; var a$b; ", //
            "var c = null; (function() {a$b = 0;})(); a$b;"));
  }

  @Test
  public void testAddPropertyToUncollapsibleObjectInLocalScopeDepth2() {
    test(
        lines(
            "var a = {}; a.b = {}; var d = a.b; use(d);", //
            "(function() {a.b.c = 0;})(); a.b.c;"),
        lines(
            "var a$b = {}; var d = null; use(a$b);", //
            "(function() {a$b.c = 0;})(); a$b.c;"));
  }

  @Test
  public void testAddPropertyToUncollapsibleCtorInLocalScopeDepth2() {
    test(
        lines(
            "var a = {}; /** @constructor */ a.b = function (){}; var d = a.b;", //
            "(function() {a.b.c = 0;})(); a.b.c;"),
        lines(
            "/** @constructor */ var a$b = function (){}; var a$b$c; var d = null;", //
            "(function() {a$b$c = 0;})(); a$b$c;"));
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
        "var a = {}; a.b = {}; var c = a.b; a.b.c = function (){}; a.b.c.x = 0; a.b.c.x;"
            + " use(c);", //
        "var a$b = {}; var c=null; a$b.c = function(){}; a$b.c.x=0; a$b.c.x; use(a$b);");
  }

  @Test
  public void testAddPropertyToChildFuncOfUncollapsibleObjectInLocalScope() {
    test(
        lines(
            "var a = {}; a.b = function (){}; a.b.x = 0;", //
            "var c = a; (function() {a.b.y = 1;})(); a.b.x; a.b.y;"),
        lines(
            "var a$b=function() {}; var a$b$y; var a$b$x = 0; var c=null;", //
            "(function() { a$b$y=1; })(); a$b$x; a$b$y"));
  }

  @Test
  public void testAddPropertyToChildTypeOfUncollapsibleObjectInLocalScope() {
    test(
        lines(
            "var a = {};", //
            "/** @constructor */",
            "a.b = function (){};",
            "a.b.x = 0;",
            "var c = a;",
            "(function() {a.b.y = 1;})();",
            "a.b.x;",
            "a.b.y;"),
        lines(
            "/** @constructor */", //
            "var a$b = function (){};",
            "var a$b$y;",
            "var a$b$x = 0;",
            "var c = null;",
            "(function() {a$b$y = 1;})();",
            "a$b$x;",
            "a$b$y;"));

    test(
        lines(
            "var a = {};", //
            "/** @constructor */",
            "a.b = function (){};",
            "a.b.x = 0;",
            "var c = a;",
            "(function() {a.b.y = 1;})();",
            "a.b.x;",
            "a.b.y;",
            "use(c);"),
        lines(
            "var a = {};", //
            "/** @constructor */",
            "a.b = function (){};",
            "a.b.x = 0;",
            "var c = null;",
            "(function() {a.b.y = 1;})();",
            "a.b.x;",
            "a.b.y;",
            "use(a);"),
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
        lines(
            "/** @constructor */ var a = function() {}; a.b = {x: 0}; var c = a;", //
            "(function() {a.b.y = 0;})(); a.b.y;"),
        lines(
            "/** @constructor */ var a = function() {}; var a$b$x = 0; var a$b$y; var c = null;", //
            "(function() {a$b$y = 0;})(); a$b$y;"));
  }

  @Test
  public void testLocalAliasCreatedAfterVarDeclaration1() {
    test(
        lines(
            "var a = {b: 3};", //
            "function f() {",
            "  var tmp;",
            "  tmp = a;",
            "  use(tmp.b);",
            "}"),
        lines(
            "var a$b = 3", //
            "function f() {",
            "  var tmp;",
            "  tmp = null;",
            "  use(a$b);",
            "}"));
    test(
        lines(
            "var a = {b: 3};", //
            "function f() {",
            "  var tmp;",
            "  tmp = a;",
            "  use?.(tmp?.b);",
            "}"),
        lines(
            "var a = {b: 3};", //
            "function f() {",
            "  var tmp;",
            "  tmp = null;",
            "  use?.(a?.b);",
            "}"));
  }

  @Test
  public void testLocalAliasCreatedAfterVarDeclaration2() {
    test(
        lines(
            "var a = {b: 3}", //
            "function f() {",
            "  var tmp;",
            "  if (true) {",
            "    tmp = a;",
            "    use(tmp.b);",
            "  }",
            "}"),
        lines(
            "var a$b = 3;", //
            "function f() {",
            "  var tmp;",
            "  if (true) {",
            "    tmp = null;",
            "    use(a$b);",
            "  }",
            "}"));
    test(
        lines(
            "var a = {b: 3}", //
            "function f() {",
            "  var tmp;",
            "  if (true) {",
            "    tmp = a;",
            "    use?.(tmp?.b);",
            "  }",
            "}"),
        lines(
            "var a = {b: 3}", //
            "function f() {",
            "  var tmp;",
            "  if (true) {",
            "    tmp = null;",
            "    use?.(a?.b);",
            "  }",
            "}"));
  }

  @Test
  public void testLocalAliasCreatedAfterVarDeclaration3() {
    testSame(
        lines(
            "var a = { b : 3 };", //
            "function f() {",
            "  var tmp;",
            "  if (true) {",
            "    tmp = a;",
            "  }",
            "  use(tmp);",
            "}"));
  }

  @Test
  public void testPartialLocalCtorAlias() {
    testWarning(
        lines(
            "/** @constructor */ var Main = function() {};", //
            "Main.doSomething = function(i) {}",
            "function f() {",
            "  var tmp;",
            "  if (g()) {",
            "    use(tmp.doSomething);",
            "    tmp = Main;",
            "    tmp.doSomething(5);",
            "  }",
            "  use(tmp.doSomething);", // can't inline this use of tmp.
            "}"),
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
        lines(
            "var a = {b: 3, c: {d: 5}}; ", //
            "function f() { var x = a; f(x.b); f(x.c); f(x.c.d); }"),
        lines(
            "var a$b = 3; var a$c = {d: 5}; ", //
            "function f() { var x = null; f(a$b); f(a$c); f(a$c.d);}"));
    test(
        lines(
            "var a = {b: 3, c: {d: 5}};", //
            "function f() { var x =    a; f?.(x?.b); f?.(x?.c); f?.(x.c?.d);}"),
        lines(
            "var a = {b: 3, c: {d:5}};", //
            "function f() { var x = null; f?.(a?.b); f?.(a?.c); f?.(a.c?.d);}"));
  }

  @Test
  public void testLocalAlias4() {
    test(
        lines(
            "var a = {b: 3}; var c = {d: 5}; ", //
            "function f() { var x = a; var y = c; f(x.b); f(y.d); }"),
        lines(
            "var a$b = 3; var c$d = 5; ", //
            "function f() { var x = null; var y = null; f(a$b); f(c$d);}"));
    test(
        lines(
            "var a = {b: 3}; var c = {d: 5};", //
            "function f() { var x =    a; var y =    c; f?.(x?.b); f?.(y?.d); }"),
        lines(
            "var a = {b: 3}; var c = {d: 5};", //
            "function f() { var x = null; var y = null; f?.(a?.b); f?.(c?.d); }"));
  }

  @Test
  public void testLocalAlias5() {
    test(
        lines(
            "var a = {b: {c: 5}}; ", //
            "function f() { var x = a; var y = x.b; f(a.b.c); f(y.c); }"),
        lines(
            "var a$b$c = 5; ", //
            "function f() { var x = null; var y = null; f(a$b$c); f(a$b$c);}"));
    test(
        lines(
            "var a = {b: {c: 5}};", //
            "function f() { var x =    a; var y = x?.b; f?.(a.b?.c); f?.(y?.c); }"),
        lines(
            "var a = {b: {c: 5}};", //
            "function f() { var x = null; var y = a?.b; f?.(a.b?.c); f?.(y?.c); }"));
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
        lines(
            "var a = {b: 3}; ", //
            "function f() { a.b = 5; var x = a; f(a.b); }"),
        "var a$b = 3; function f() { a$b = 5; var x = null; f(a$b); } ");
    test(
        lines(
            "var a = {b: 3}; ", //
            "function f() { a.b = 5; var x =    a; f?.(a?.b); }"),
        lines(
            "var a = {b: 3}; ", //
            "function f() { a.b = 5; var x = null; f?.(a?.b); }"));
  }

  @Test
  public void testLocalAliasOfAncestor() {
    testSame(
        lines(
            "var a = {b: {c: 5}}; function g() { f(a); } ", //
            "function f() { var x = a.b; f(x.c); }"));
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
        lines(
            "var foo = function() { return {b: 3}; };", //
            "var a = foo(); a.b = 5; ",
            "function f() { var x = a.b; f(x); }"));
  }

  @Test
  public void testLocalAliasOfFunction() {
    test(
        lines(
            "var a = function() {}; a.b = 5; ", //
            "function f() { var x = a.b; f(x); }"),
        lines(
            "var a = function() {}; var a$b = 5; ", //
            "function f() { var x = null; f(a$b); }"));
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
        lines(
            "var ns = {};", //
            "ns.Foo = {};",
            "var Baz = {};",
            "Baz.Foo = ns.Foo;",
            "(Baz.Foo.bar = 10, 123);"),
        lines(
            "var Baz$Foo=null;", //
            "var ns$Foo$bar;",
            "(ns$Foo$bar = 10, 123);"));

    test(
        lines(
            "var ns = {};", //
            "ns.Foo = {};",
            "var Baz = {};",
            "Baz.Foo = ns?.Foo;",
            "(Baz.Foo.bar = 10, 123);"),
        lines(
            "var ns = {};", //
            "ns.Foo = {};",
            "var Baz$Foo = ns?.Foo;",
            "(Baz$Foo.bar = 10, 123);"));

    test(
        lines(
            "var ns = {};", //
            "ns.Foo = {};",
            "var Baz = {};",
            "Baz.Foo = ns.Foo;",
            "function f() { (Baz.Foo.bar = 10, 123); }"),
        lines(
            "var ns$Foo$bar;", //
            "var Baz$Foo=null;",
            "function f() { (ns$Foo$bar = 10, 123); }"));
    test(
        lines(
            "var ns = {};", //
            "ns.Foo = {};",
            "var Baz = {};",
            "Baz.Foo = ns?.Foo;",
            "function f() { (Baz.Foo.bar = 10, 123); }"),
        lines(
            "var ns = {};", //
            "ns.Foo = {};",
            "var Baz$Foo = ns?.Foo;",
            "function f() { (Baz$Foo.bar = 10, 123); }"));
  }

  @Test
  public void testTypeDefAlias1() {
    test(
        lines(
            "/** @constructor */ var D = function() {};", //
            "/** @constructor */ D.L = function() {};",
            "/** @type {D.L} */ D.L.A = new D.L();",
            "",
            "/** @const */ var M = {};",
            "/** @typedef {D.L} */ M.L = D.L;",
            "",
            "use(M.L.A);"),
        lines(
            "/** @constructor */ var D = function() {};", //
            "/** @constructor */ var D$L = function() {};",
            "/** @type {D.L} */ var D$L$A = new D$L();",
            "/** @typedef {D.L} */ var M$L = null",
            "use(D$L$A);"));
    test(
        lines(
            "/** @constructor */ var D = function() {};", //
            "/** @constructor */ D.L = function() {};",
            "/** @type {D.L} */ D.L.A = new D.L();",
            "",
            "/** @const */ var M = {};",
            "/** @typedef {D.L} */ M.L = D.L;",
            "",
            "use?.(M.L?.A);"),
        lines(
            "/** @constructor */ var D = function() {};", //
            "/** @constructor */ var D$L = function() {};",
            "/** @type {D.L} */ var D$L$A = new D$L();",
            "/** @typedef {D.L} */ var M$L = null",
            // TODO(b/148237949): collapse above breaks this reference
            "use?.(D$L?.A);"));
  }

  @Test
  public void testGlobalAliasWithProperties1() {
    test(
        lines(
            "var ns = {}; ", //
            "/** @constructor */ ns.Foo = function() {};",
            "/** @enum {number} */ ns.Foo.EventType = {A:1, B:2};",
            "/** @constructor */ ns.Bar = ns.Foo;",
            "var x = function() {use(ns.Bar.EventType.A)};",
            "use(x);"),
        lines(
            "/** @constructor */ var ns$Foo = function(){};", //
            "var ns$Foo$EventType$A = 1;",
            "var ns$Foo$EventType$B = 2;",
            "/** @constructor */ var ns$Bar = null;",
            "var x = function(){use(ns$Foo$EventType$A)};",
            "use(x);"));
    testSame(
        srcs(
            lines(
                "var ns = {}; ", //
                "/** @constructor */ ns.Foo = function() {};",
                "/** @enum {number} */ ns.Foo.EventType = {A:1, B:2};",
                "/** @constructor */ ns.Bar = ns?.Foo;",
                "var x = function() {use?.(ns.Bar.EventType?.A)};",
                "use?.(x);")),
        warning(PARTIAL_NAMESPACE_WARNING));
  }

  @Test
  public void testGlobalAliasWithProperties2() {
    // Reassignment of properties was necessary to prevent invalid code in
    // previous iterations of this optimization.  Verify we don't break
    // code like this.  Now it causes a back-off of the collapsing because
    // the value is assigned more than once.
    test(
        lines(
            "var ns = {}; ", //
            "/** @constructor */ ns.Foo = function() {};",
            "/** @enum {number} */ ns.Foo.EventType = {A:1, B:2};",
            "/** @constructor */ ns.Bar = ns.Foo;",
            "/** @enum {number} */ ns.Bar.EventType = ns.Foo.EventType;",
            "var x = function() {use(ns.Bar.EventType.A)};",
            "use(x)"),
        lines(
            "/** @constructor */ var ns$Foo = function(){};", //
            "/** @enum {number} */ var ns$Foo$EventType = {A:1, B:2};",
            "/** @constructor */ var ns$Bar = null;",
            "/** @enum {number} */ ns$Foo$EventType = ns$Foo$EventType;",
            "var x = function(){use(ns$Foo$EventType.A)};",
            "use(x);"));
    testSame(
        srcs(
            lines(
                "var ns = {}; ", //
                "/** @constructor */ ns.Foo = function() {};",
                "/** @enum {number} */ ns.Foo.EventType = {A:1, B:2};",
                "/** @constructor */ ns.Bar = ns?.Foo;",
                "/** @enum {number} */ ns.Bar.EventType = ns.Foo?.EventType;",
                "var x = function() {use?.(ns.Bar.EventType?.A)};",
                "use?.(x)")),
        warning(PARTIAL_NAMESPACE_WARNING));
  }

  @Test
  public void testGlobalAliasWithProperties3() {
    test(
        lines(
            "var ns = {}; ", //
            "/** @constructor */ ns.Foo = function() {};",
            "/** @enum {number} */ ns.Foo.EventType = {A:1, B:2};",
            "/** @constructor */ ns.Bar = ns.Foo;",
            "/** @enum {number} */ ns.Bar.Other = {X:1, Y:2};",
            "var x = function() {use(ns.Bar.Other.X)};",
            "use(x)"),
        lines(
            "/** @constructor */ var ns$Foo=function(){};", //
            "var ns$Foo$EventType$A=1;",
            "var ns$Foo$EventType$B=2;",
            "/** @constructor */ var ns$Bar=null;",
            "var ns$Foo$Other$X=1;",
            "var ns$Foo$Other$Y=2;",
            "var x=function(){use(ns$Foo$Other$X)};",
            "use(x)\n"));
    testSame(
        srcs(
            lines(
                "var ns = {}; ", //
                "/** @constructor */ ns.Foo = function() {};",
                "/** @enum {number} */ ns.Foo.EventType = {A:1, B:2};",
                "/** @constructor */ ns.Bar = ns?.Foo;",
                "/** @enum {number} */ ns.Bar.Other = {X:1, Y:2};",
                "var x = function() {use?.(ns.Bar.Other?.X)};",
                "use?.(x)")),
        warning(PARTIAL_NAMESPACE_WARNING));
  }

  @Test
  public void testGlobalAliasWithProperties4() {
    testSame(
        lines(
            "", //
            "var nullFunction = function(){};",
            "var blob = {};",
            "blob.init = nullFunction;",
            "use(blob)"));
  }

  @Test
  public void testGlobalAlias_propertyOnExternedConstructor_isNotChanged() {
    testSame(
        externs("/** @constructor */ var blob = function() {}"),
        srcs(
            lines(
                "var nullFunction = function(){};", //
                "blob.init = nullFunction;",
                "use(blob.init)")));
  }

  @Test
  public void testLocalAliasOfEnumWithInstanceofCheck() {
    test(
        lines(
            "/** @constructor */", //
            "var Enums = function() {",
            "};",
            "",
            "/** @enum {number} */",
            "Enums.Fruit = {",
            " APPLE: 1,",
            " BANANA: 2,",
            "};",
            "",
            "function foo(f) {",
            " if (f instanceof Enums) { alert('what?'); return; }",
            "",
            " var Fruit = Enums.Fruit;",
            " if (f == Fruit.APPLE) alert('apple');",
            " if (f == Fruit.BANANA) alert('banana');",
            "}"),
        lines(
            "/** @constructor */", //
            "var Enums = function() {};",
            "var Enums$Fruit$APPLE = 1;",
            "var Enums$Fruit$BANANA = 2;",
            "function foo(f) {",
            " if (f instanceof Enums) { alert('what?'); return; }",
            " var Fruit = null;",
            " if (f == Enums$Fruit$APPLE) alert('apple');",
            " if (f == Enums$Fruit$BANANA) alert('banana');",
            "}"));
  }

  @Test
  public void testCollapsePropertiesOfClass1() {
    test(
        lines(
            "/** @constructor */", //
            "var namespace = function() {};",
            "goog.inherits(namespace, Object);",
            "",
            "namespace.includeExtraParam = true;",
            "",
            "/** @enum {number} */",
            "namespace.Param = {",
            "  param1: 1,",
            "  param2: 2",
            "};",
            "",
            "if (namespace.includeExtraParam) {",
            "  namespace.Param.optParam = 3;",
            "}",
            "",
            "function f() {",
            "  var Param = namespace.Param;",
            "  log(namespace.Param.optParam);",
            "  log(Param.optParam);",
            "}"),
        lines(
            "/** @constructor */", //
            "var namespace = function() {};",
            "goog.inherits(namespace, Object);",
            "var namespace$includeExtraParam = true;",
            "var namespace$Param$param1 = 1;",
            "var namespace$Param$param2 = 2;",
            "if (namespace$includeExtraParam) {",
            "  var namespace$Param$optParam = 3;",
            "}",
            "function f() {",
            "  var Param = null;",
            "  log(namespace$Param$optParam);",
            "  log(namespace$Param$optParam);",
            "}"));
    // TODO(b/148237949): CollapseProperties breaks several optional chain references here
    test(
        lines(
            "/** @constructor */", //
            "var namespace = function() {};",
            "goog.inherits(namespace, Object);",
            "",
            "namespace.includeExtraParam = true;",
            "",
            "/** @enum {number} */",
            "namespace.Param = {",
            "  param1: 1,",
            "  param2: 2",
            "};",
            "",
            "if (namespace?.includeExtraParam) {",
            "  namespace.Param.optParam = 3;",
            "}",
            "",
            "function f() {",
            "  var Param = namespace?.Param;",
            "  log(namespace.Param?.optParam);",
            "  log(Param?.optParam);",
            "}"),
        lines(
            "/** @constructor */", //
            "var namespace = function() {};",
            "goog.inherits(namespace, Object);",
            "var namespace$includeExtraParam = true;",
            "var namespace$Param$param1 = 1;",
            "var namespace$Param$param2 = 2;",
            "/** @enum {number} */",
            "var namespace$Param = {",
            "  param1: namespace$Param$param1,",
            "  param2: namespace$Param$param2",
            "};",
            "if (namespace?.includeExtraParam) {", // broken
            "  var namespace$Param$optParam = 3;",
            "}",
            "function f() {",
            "  var Param = namespace?.Param;", // broken
            "  log(namespace$Param?.optParam);", // broken
            "  log(Param?.optParam);", // broken
            "}"));
  }

  @Test
  public void testCollapsePropertiesOfClass2() {
    test(
        lines(
            "var goog = goog || {};", //
            "goog.addSingletonGetter = function(cls) {};",
            "",
            "var a = {};",
            "",
            "/** @constructor */",
            "a.b = function() {};",
            "goog.addSingletonGetter(a.b);",
            "a.b.prototype.get = function(key) {};",
            "",
            "/** @constructor */",
            "a.b.c = function() {};",
            "a.b.c.XXX = new a.b.c();",
            "",
            "function f() {",
            "  var x = a.b.getInstance();",
            "  var Key = a.b.c;",
            "  x.get(Key.XXX);",
            "}"),
        lines(
            "var goog = goog || {};", //
            "var goog$addSingletonGetter = function(cls) {};",
            "/** @constructor */",
            "var a$b = function() {};",
            "goog$addSingletonGetter(a$b);",
            "a$b.prototype.get = function(key) {};",
            "/** @constructor */",
            "var a$b$c = function() {};",
            "var a$b$c$XXX = new a$b$c();",
            "",
            "function f() {",
            "  var x = a$b.getInstance();",
            "  var Key = null;",
            "  x.get(a$b$c$XXX);",
            "}"));
  }

  @Test
  public void test_b19179602() {
    // Note that this only collapses a.b.staticProp because a.b is a constructor.
    // Normally AggressiveInlineAliases would not inline "b" inside the loop.
    test(
        lines(
            "var a = {};", //
            "/** @constructor */ a.b = function() {};",
            "a.b.staticProp = 5;",
            "function f() {",
            "  while (true) {",
            // b is declared inside a loop, so it is reassigned multiple times
            "    var b = a.b;",
            "    alert(b.staticProp);",
            "  }",
            "}"),
        lines(
            "/** @constructor */ var a$b = function() {};", //
            "var a$b$staticProp = 5;",
            "function f() {",
            "  while (true) {",
            "    var b = null;",
            "    alert(a$b$staticProp);",
            "  }",
            "}"));
    test(
        lines(
            "var a = {};", //
            "/** @constructor */ a.b = function() {};",
            "a.b.staticProp = 5;",
            "function f() {",
            "  while (true) {",
            // b is declared inside a loop, so it is reassigned multiple times
            "    var b = a.b;",
            "    alert(b?.staticProp);",
            "  }",
            "}"),
        lines(
            "/** @constructor */ var a$b = function() {};", //
            "var a$b$staticProp = 5;",
            "function f() {",
            "  while (true) {",
            "    var b = null;",
            // TODO(bradfordcsmith): This reference is broken by collapsing above.
            "    alert(a$b?.staticProp);",
            "  }",
            "}"));
  }

  @Test
  public void test_b19179602_declareOutsideLoop() {
    test(
        lines(
            "var a = {};", //
            "/** @constructor */ a.b = function() {};",
            "a.b.staticProp = 5;",
            "function f() {",
            // b is declared outside the loop
            "  var b = a.b;",
            "  while (true) {",
            "    alert(b.staticProp);",
            "  }",
            "}"),
        lines(
            "/** @constructor */", //
            "var a$b = function() {};",
            "var a$b$staticProp = 5;",
            "",
            "function f() {",
            "  var b = null;",
            "  while (true) {",
            "    alert(a$b$staticProp);",
            "  }",
            "}"));
    test(
        lines(
            "var a = {};", //
            "/** @constructor */ a.b = function() {};",
            "a.b.staticProp = 5;",
            "function f() {",
            // b is declared outside the loop
            "  var b = a.b;",
            "  while (true) {",
            "    alert(b?.staticProp);",
            "  }",
            "}"),
        lines(
            "/** @constructor */", //
            "var a$b = function() {};",
            // TODO(b/148237949): Collapsing this breaks the optional chain reference below.
            "var a$b$staticProp = 5;",
            "",
            "function f() {",
            "  var b = null;",
            "  while (true) {",
            "    alert(a$b?.staticProp);",
            "  }",
            "}"));
  }

  @Test
  public void testCtorManyAssignmentsDontInlineWarn() {
    test(
        lines(
            "var a = {};", //
            "/** @constructor */ a.b = function() {};",
            "a.b.staticProp = 5;",
            "function f(y, z) {",
            "  var x = a.b;",
            "  if (y) {",
            "    x = z;",
            "  }",
            "  return x.staticProp;",
            "}"),
        lines(
            "/** @constructor */ var a$b = function() {};", //
            "var a$b$staticProp = 5;",
            "function f(y, z) {",
            "  var x = a$b;",
            "  if (y) {",
            "    x = z;",
            "  }",
            "  return x.staticProp;",
            "}"),
        warning(InlineAndCollapseProperties.UNSAFE_CTOR_ALIASING));
  }

  @Test
  public void testCodeGeneratedByGoogModule() {
    // The static property is added to the exports object
    test(
        lines(
            "var $jscomp = {};", //
            "$jscomp.scope = {};",
            "/** @constructor */",
            "$jscomp.scope.Foo = function() {};",
            "var exports = $jscomp.scope.Foo;",
            "exports.staticprop = {A:1};",
            "var y = exports.staticprop.A;"),
        lines(
            "/** @constructor */", //
            "var $jscomp$scope$Foo = function() {}",
            "var exports = null;",
            "var $jscomp$scope$Foo$staticprop$A = 1;",
            "var y = null;"));

    // The static property is added to the constructor
    test(
        lines(
            "var $jscomp = {};", //
            "$jscomp.scope = {};",
            "/** @constructor */",
            "$jscomp.scope.Foo = function() {};",
            "$jscomp.scope.Foo.staticprop = {A:1};",
            "var exports = $jscomp.scope.Foo;",
            "var y = exports.staticprop.A;"),
        lines(
            "/** @constructor */", //
            "var $jscomp$scope$Foo = function() {}",
            "var $jscomp$scope$Foo$staticprop$A = 1;",
            "var exports = null;",
            "var y = null;"));
  }

  @Test
  public void testInlineCtorInObjLit() {
    test(
        lines(
            "/** @constructor */", //
            "function Foo() {}",
            "",
            "/** @constructor */",
            "var Bar = Foo;",
            "",
            "var objlit = {",
            "  'prop' : Bar",
            "};"),
        lines(
            "/** @constructor */", //
            "function Foo() {}",
            "/** @constructor */",
            "var Bar = null;",
            "var objlit$prop = Foo;"));
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
        lines(
            "var ns = {};", //
            "/** @constructor */",
            "ns.Foo = function () {};",
            "var Bar = ns.Foo;",
            "/** @const @enum */",
            "Bar.prop = { A: 1 };"),
        lines(
            "/** @constructor */", //
            "var ns$Foo = function(){};",
            "var Bar = null;",
            "var ns$Foo$prop$A = 1"));
  }

  @Test
  public void testDontCrashNamespaceAliasAcrossScopes() {
    test(
        lines(
            "var ns = {};", //
            "ns.VALUE = 0.01;",
            "function f() {",
            "    var constants = ns;",
            "    (function() {",
            "       var x = constants.VALUE;",
            "    })();",
            "}"),
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
        lines(
            "let ns = {};", //
            "ns.foo = 'bar';",
            "let ns2;",
            "if (true) {",
            "  ns2 = ns;",
            "}",
            "use(ns2.foo);"));
  }

  @Test
  public void testGlobalAliasWithLet3() {
    // Back off since in general we don't check that ns2 is only ever accessed within the if block.
    testSame(
        lines(
            "let ns = {};", //
            "ns.foo = 'bar';",
            "let ns2;",
            "if (true) {",
            "  ns2 = ns;",
            "  use(ns2.foo);",
            "}"));
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
        lines(
            "let ns = {};", //
            "ns.foo = 'bar';",
            "function f() {",
            "  if (true) {",
            "    let ns2 = ns;",
            "    use(ns2.foo);",
            "  }",
            "}"),
        lines(
            "var ns$foo = 'bar';", //
            "function f() {",
            "  if (true) {",
            "    let ns2 = null;",
            "    use(ns$foo);",
            "  }",
            "}"));
    test(
        lines(
            "let ns = {};", //
            "ns.foo = 'bar';",
            "function f() {",
            "  if (true) {",
            "    let ns2 = ns;",
            "    use?.(ns2?.foo);",
            "  }",
            "}"),
        lines(
            "let ns = {};", //
            "ns.foo = 'bar';",
            "function f() {",
            "  if (true) {",
            "    let ns2 = null;",
            "    use?.(ns?.foo);",
            "  }",
            "}"));
  }

  @Test
  public void testLocalAliasWithLet3() {
    test(
        lines(
            "let ns = {};", //
            "ns.foo = 'bar';",
            "if (true) {",
            "  let ns2 = ns;",
            "  use(ns2.foo);",
            "}"),
        lines(
            "var ns$foo = 'bar';", //
            "if (true) {",
            "  let ns2 = null;",
            "  use(ns$foo);",
            "}"));
    test(
        lines(
            "let ns = {};", //
            "ns.foo = 'bar';",
            "if (true) {",
            "  let ns2 = ns;",
            "  use?.(ns2?.foo);",
            "}"),
        lines(
            "let ns = {};", //
            "ns.foo = 'bar';",
            "if (true) {",
            "  let ns2 = null;",
            "  use?.(ns?.foo);",
            "}"));
  }

  @Test
  public void testLocalAliasWithLet4() {
    test(
        lines(
            "let ns = {};", //
            "ns.foo = 'bar';",
            "if (true) {",
            "  let baz = ns.foo;",
            "  use(baz);",
            "}"),
        lines(
            "var ns$foo = 'bar';", //
            "if (true) {",
            "  let baz = null;",
            "  use(ns$foo);",
            "}"));
  }

  @Test
  public void testLocalAliasWithLet5() {
    // For local variables (VAR, CONST, or LET) we only handle cases where the alias is a variable,
    // not a property.
    testSame(
        lines(
            "let ns = {};", //
            "ns.foo = 'bar';",
            "if (true) {",
            "  let ns2 = {};",
            "  ns2.baz = ns;",
            "  use(ns2.baz.foo);",
            "}"));
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
        lines(
            "const ns = {};", //
            "ns.foo = 'bar';",
            "function f() {",
            "  const ns2 = ns;",
            "  use(ns2.foo);",
            "}"),
        lines(
            "var ns$foo = 'bar';", //
            "function f() {",
            "  const ns2 = null;",
            "  use(ns$foo);",
            "}"));
    test(
        lines(
            "const ns = {};", //
            "ns.foo = 'bar';",
            "function f() {",
            "  const ns2 = ns;",
            "  use?.(ns2?.foo);",
            "}"),
        lines(
            "const ns = {};", //
            "ns.foo = 'bar';",
            "function f() {",
            "  const ns2 = null;",
            "  use?.(ns?.foo);",
            "}"));
  }

  @Test
  public void testLocalAliasWithConst2() {
    test(
        lines(
            "const ns = {};", //
            "ns.foo = 'bar';",
            "function f() {",
            "  if (true) {",
            "    const ns2 = ns;",
            "    use(ns2.foo);",
            "  }",
            "}"),
        lines(
            "var ns$foo = 'bar';", //
            "function f() {",
            "  if (true) {",
            "    const ns2 = null;",
            "    use(ns$foo);",
            "  }",
            "}"));
    test(
        lines(
            "const ns = {};", //
            "ns.foo = 'bar';",
            "function f() {",
            "  if (true) {",
            "    const ns2 = ns;",
            "    use?.(ns2?.foo);",
            "  }",
            "}"),
        lines(
            "const ns = {};", //
            "ns.foo = 'bar';",
            "function f() {",
            "  if (true) {",
            "    const ns2 = null;",
            "    use?.(ns?.foo);",
            "  }",
            "}"));
  }

  @Test
  public void testLocalAliasWithConst3() {
    test(
        lines(
            "const ns = {};", //
            "ns.foo = 'bar';",
            "if (true) {",
            "  const ns2 = ns;",
            "  use(ns2.foo);",
            "}"),
        lines(
            "var ns$foo = 'bar';", //
            "if (true) {",
            "  const ns2 = null;",
            "  use(ns$foo);",
            "}"));
    test(
        lines(
            "const ns = {};", //
            "ns.foo = 'bar';",
            "if (true) {",
            "  const ns2 = ns;",
            "  use?.(ns2?.foo);",
            "}"),
        lines(
            "const ns = {};", //
            "ns.foo = 'bar';",
            "if (true) {",
            "  const ns2 = null;",
            "  use?.(ns?.foo);",
            "}"));
  }

  @Test
  public void testLocalAliasWithConst4() {
    test(
        lines(
            "const ns = {};", //
            "ns.foo = 'bar';",
            "if (true) {",
            "  const baz = ns.foo;",
            "  use(baz);",
            "}"),
        lines(
            "var ns$foo = 'bar';", //
            "if (true) {",
            "  const baz = null;",
            "  use(ns$foo);",
            "}"));
  }

  @Test
  public void testLocalAliasWithConst5() {
    // For local variables (VAR, CONST, or LET) we only handle cases where the alias is a variable,
    // not a property.
    testSame(
        lines(
            "const ns = {};", //
            "ns.foo = 'bar';",
            "if (true) {",
            "  const ns2 = {};",
            "  ns2.baz = ns;",
            "  use(ns2.baz.foo);",
            "}"));
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
            lines(
                "class A {", //
                "  static useFoo() {",
                "    alert(this.foo);",
                "  }",
                "}",
                "A.foo = 'bar';",
                "const B = A;",
                "B.foo = 'baz';",
                "B.useFoo();")),
        expected(
            lines(
                "var A$useFoo = function() { alert(this.foo); };", //
                "class A {}",
                "var A$foo = 'bar';",
                "const B = null;",
                "A$foo = 'baz';",
                "A$useFoo();")),
        warning(RECEIVER_AFFECTED_BY_COLLAPSE));

    // Adding @nocollapse makes this safe.
    test(
        lines(
            "class A {", //
            "  /** @nocollapse */",
            "  static useFoo() {",
            "    alert(this.foo);",
            "  }",
            "}",
            "/** @nocollapse */",
            "A.foo = 'bar';",
            "const B = A;",
            "B.foo = 'baz';",
            "B.useFoo();"),
        lines(
            "class A {", //
            "  /** @nocollapse */",
            "  static useFoo() {",
            "    alert(this.foo);",
            "  }",
            "}",
            "/** @nocollapse */",
            "A.foo = 'bar';",
            "const B = null;",
            "A.foo = 'baz';",
            "A.useFoo();"));
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
        lines(
            "class A {}", //
            "A.foo = 5;",
            "class B {}",
            "B.foo = 6;",
            "function getSuperclass() { return 1 < 2 ? A : B; }",
            "class C extends getSuperclass() {}",
            "use(C.foo);"),
        lines(
            "class A {}", //
            "var A$foo = 5;",
            "class B {}",
            "var B$foo = 6;",
            "function getSuperclass() { return 1 < 2 ? A : B; }",
            "class C extends getSuperclass() {}",
            "use(C.foo);"));
  }

  @Test
  public void testAliasForSuperclassNamespace() {
    test(
        lines(
            "var ns = {};", //
            "class Foo {}",
            "ns.clazz = Foo;",
            "var Bar = class extends ns.clazz.Baz {}"),
        lines(
            "class Foo {}", //
            "var ns$clazz = null;",
            "var Bar = class extends Foo.Baz {}"));

    test(
        lines(
            "var ns = {};", //
            "class Foo {}",
            "Foo.Builder = class {}",
            "ns.clazz = Foo;",
            "var Bar = class extends ns.clazz.Builder {}"),
        lines(
            "class Foo {}", //
            "var Foo$Builder = class {}",
            "var ns$clazz = null;",
            "var Bar = class extends Foo$Builder {}"));
  }

  @Test
  public void testAliasForSuperclassNamespace_withStaticInheritance() {
    test(
        lines(
            "var ns = {};", //
            "class Foo {}",
            "Foo.Builder = class {}",
            "Foo.Builder.baz = 3;",
            "ns.clazz = Foo;",
            "var Bar = class extends ns.clazz.Builder {}",
            "use(Bar.baz);"),
        lines(
            "class Foo {}", //
            "var Foo$Builder = class {}",
            "var Foo$Builder$baz = 3;",
            "var ns$clazz = null;",
            "var Bar = class extends Foo$Builder {}",
            "use(Foo$Builder$baz);"));
    test(
        lines(
            "var ns = {};", //
            "class Foo {}",
            "Foo.Builder = class {}",
            "Foo.Builder.baz = 3;",
            "ns.clazz = Foo;",
            "var Bar = class extends ns.clazz?.Builder {}",
            "use(Bar?.baz);"),
        lines(
            "class Foo {}", //
            "var Foo$Builder = class {}",
            "var Foo$Builder$baz = 3;",
            "var ns$clazz = null;",
            // TODO(b/148237979): Collapse above breaks this reference.
            "var Bar = class extends Foo?.Builder {}",
            // TODO(b/148237979): Collapse above breaks this reference.
            "use(Bar?.baz);"));
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
        lines(
            "var ns = {};", //
            "/** @constructor */",
            "ns.ctor = function() {}",
            "const {ctor} = ns;",
            "let c = new ctor;"),
        lines(
            "/** @constructor */", //
            "var ns$ctor = function() {}",
            "const ctor = null;",
            "let c = new ns$ctor;"));
  }

  @Test
  public void namespaceInDestructuringPattern() {
    testSame(
        lines(
            "const ns = {};", //
            "ns.x = 1;",
            "ns.y = 2;",
            "let {x, y} = ns;",
            "x = 4;", // enforce that we can't inline x -> ns.x because it's set multiple times
            "use(x + y);"));
  }

  @Test
  public void inlineDestructuringPatternConstructorWithProperty() {
    test(
        lines(
            "const ns = {};", //
            "/** @constructor */",
            "ns.Y = function() {};",
            "ns.Y.prop = 3;",
            "const {Y} = ns;",
            "use(Y.prop);"),
        lines(
            "/** @constructor */", //
            "var ns$Y = function() {};",
            "var ns$Y$prop = 3;",
            "const Y = null;",
            "use(ns$Y$prop);"));

    test(
        lines(
            "const ns = {};", //
            "/** @constructor */",
            "ns.Y = function() {};",
            "ns.Y.prop = 3;",
            "const {Y} = ns;",
            "use(Y?.prop);"),
        lines(
            "/** @constructor */", //
            "var ns$Y = function() {};",
            // TODO(bradfordcsmith): This collapse breaks the optional chain
            //     reference below.
            "var ns$Y$prop = 3;",
            "const Y = null;",
            "use(ns$Y?.prop);"));

    // Only `const` destructuring aliases have special handling by AggressiveInlineAliases
    testSame(
        lines(
            "const ns = {};", //
            "/** @constructor */",
            "ns.Y = function() {};",
            "ns.Y.prop = 3;",
            "let {Y} = ns;",
            "use(Y.prop);"),
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
        lines(
            "var ns = {};", //
            "/** @constructor */ ns.ctor = function() {};",
            "ns.ctor.foo = 3;",
            "var foo = ns.ctor.foo;",
            "ns = ns || {};",
            "alert(foo);"),
        lines(
            "var ns = {};", //
            "/** @constructor */ var ns$ctor = function() {};",
            "var ns$ctor$foo = 3;",
            "var foo = null;",
            "ns = ns || {};",
            "alert(ns$ctor$foo);"));

    // NOTE(lharker): this mirrors current code in Closure library
    test(
        lines(
            "var goog = {};", //
            "goog.module = function() {};",
            "/** @constructor */ goog.module.ModuleManager = function() {};",
            "goog.module.ModuleManager.getInstance = function() {};",
            "goog.module = goog.module || {};",
            "var ModuleManager = goog.module.ModuleManager;",
            "alert(ModuleManager.getInstance());"),
        lines(
            "var goog$module = function() {};", //
            "/** @constructor */ var goog$module$ModuleManager = function() {};",
            "var goog$module$ModuleManager$getInstance = function() {};",
            "goog$module = goog$module || {};",
            "var ModuleManager = null;",
            "alert(goog$module$ModuleManager$getInstance());"));
  }

  @Test
  public void testClassInObjectLiteral() {
    test(
        "var obj = {foo: class {}};", //
        "var obj$foo = class {};");

    // TODO(lharker): this is unsafe, obj$foo.foo is undefined now that A$foo is collapsed
    test(
        "class A {}     A.foo = 3; var obj = {foo: class extends A {}}; use(obj.foo.foo);", //
        "class A {} var A$foo = 3; var obj$foo =   class extends A{};   use(obj$foo.foo)");
  }

  @Test
  public void testLoopInAliasChainOfTypedefConstructorProperty() {
    test(
        lines(
            "/** @constructor */ var Item = function() {};", //
            "/** @typedef {number} */ Item.Models;",
            "Item.Models = Item.Models;"),
        lines(
            "/** @constructor */ var Item = function() {};", //
            "Item$Models;",
            "var Item$Models = Item$Models;"));
    test(
        lines(
            "/** @constructor */ var Item = function() {};", //
            "/** @typedef {number} */ Item.Models;",
            "Item.Models = Item?.Models;"),
        lines(
            "/** @constructor */ var Item = function() {};", //
            "Item$Models;",
            // TODO(b/148237949): Collapsing breaks this reference.
            "var Item$Models = Item?.Models;"));
  }

  @Test
  public void testDontInlinePropertiesOnEscapedNamespace() {
    testSame(
        externs("function use(obj) {}"),
        srcs(
            lines(
                "function Foo() {}", //
                "Foo.Bar = {};",
                "Foo.Bar.baz = {A: 1, B: 2};",
                "",
                "var $jscomp$destructuring$var1 = Foo.Bar;",
                // This call could potentially have changed the value of Foo.Bar, so don't
                // collapse Foo.Bar.baz.A/Foo.Bar.baz.B
                "use(Foo);",
                "var baz = $jscomp$destructuring$var1.baz;",
                "use(baz.A);")));
  }

  @Test
  public void testInliningPropertiesOnEscapedNamespace_withDeclaredType() {
    test(
        externs("function use(obj) {}"),
        srcs(
            lines(
                "/** @constructor */", //
                "function Foo() {}",
                "/** @constructor */",
                "Foo.Bar = function() {};",
                "/** @enum {number} */",
                "Foo.Bar.baz = {A: 1, B: 2};",
                "",
                "var $jscomp$destructuring$var1 = Foo.Bar;",
                "use(Foo);",
                "var baz = $jscomp$destructuring$var1.baz;",
                "use(baz.A);")),
        expected(
            lines(
                "/** @constructor */", //
                "function Foo() {}",
                "/** @constructor */",
                "var Foo$Bar = function() {};",
                "var Foo$Bar$baz$A = 1;",
                "var Foo$Bar$baz$B = 2;",
                "",
                "var $jscomp$destructuring$var1 = null;",
                // This call could potentially read Foo.Bar, which will now be broken.
                // AggressiveInlineAliases/CollapseProperties intentionally generate unsafe code
                // in this case. The main motivation is to collapse long namespaces generated by
                // goog.provide or goog.module.declareLegacyNamespace()
                // https://developers.google.com/closure/compiler/docs/limitations#implications-of-object-property-flattening
                "use(Foo);",
                "var baz = null;",
                "use(Foo$Bar$baz$A);")));
  }

  @Test
  public void testDontInlinePropertiesOnNamespace_withNoCollapse() {
    testSame(
        externs("function use(obj) {}"),
        srcs(
            lines(
                "/** @constructor */", //
                "function Foo() {}",
                "/** @constructor @nocollapse */",
                "Foo.Bar = function() {};",
                "/** @enum {number} @nocollapse */",
                "Foo.Bar.baz = {A: 1, B: 2};",
                "",
                "var $jscomp$destructuring$var1 = Foo.Bar;",
                // This call could potentially have changed the value of Foo.Bar, and without
                // @nocollapse on Foo.Bar and Foo.Bar.baz the compiler would generate unsafe code.
                "use(Foo);",
                "var baz = $jscomp$destructuring$var1.baz;",
                "use(baz.A);")));
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
            lines(
                "/** @constructor */", //
                "function Foo() {}",
                "Foo.prop = 2;",
                "var ns = {};",
                "ns.alias = Foo;",
                "use(ns);",
                "use(ns.alias.prop);")),
        expected(
            lines(
                "/** @constructor */", //
                "function Foo() {}",
                "var Foo$prop = 2;",
                "var ns = {};",
                "ns.alias = Foo;",
                "use(ns);",
                "use(ns.alias.prop);")));
  }

  @Test
  public void testClassStaticMemberAccessedWithSuper() {
    test(
        lines(
            "class Bar {", //
            "  static double(n) {",
            "    return n*2",
            "  }",
            "}",
            "class Baz extends Bar {",
            "  static quadruple(n) {",
            "    return 2 * super.double(n);",
            " }",
            "}"),
        lines(
            "var Bar$double = function(n) { return n * 2; }", //
            "class Bar {}",
            "var Baz$quadruple = function(n) { return 2 * Bar$double(n); }",
            "class Baz extends Bar {}"));
  }
}
