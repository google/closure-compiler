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

import static com.google.common.base.Strings.nullToEmpty;

import com.google.common.base.Joiner;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.PolyfillUsageFinder.Polyfills;
import com.google.javascript.jscomp.testing.NoninjectingCompiler;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for the RewritePolyfills compiler pass. */
@RunWith(JUnit4.class)
public final class RewritePolyfillsTest extends CompilerTestCase {

  private static final LanguageMode ES_2020 = LanguageMode.ECMASCRIPT_2020;
  private static final LanguageMode ES6 = LanguageMode.ECMASCRIPT_2015;
  private static final LanguageMode ES5 = LanguageMode.ECMASCRIPT5_STRICT;
  private static final LanguageMode ES3 = LanguageMode.ECMASCRIPT3;

  private final Map<String, String> injectableLibraries = new HashMap<>();
  private final List<String> polyfillTable = new ArrayList<>();
  private boolean isolatePolyfills = false;
  private boolean injectPolyfills = true;
  private LanguageMode injectPolyfillsNewerThan = null;

  private void addLibrary(String name, String from, String to, @Nullable String library) {
    if (library != null) {
      injectableLibraries.put(
          library,
          String.format("$jscomp.polyfill('%s', function() {}, '%s', '%s');\n", name, from, to));
    }
    polyfillTable.add(String.format("%s %s %s %s", name, from, to, nullToEmpty(library)));
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    injectableLibraries.clear();
    polyfillTable.clear();
    injectPolyfillsNewerThan = null;
    setLanguageOut(LanguageMode.ECMASCRIPT5);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RewritePolyfills(
        compiler,
        Polyfills.fromTable(Joiner.on("\n").join(polyfillTable)),
        injectPolyfills,
        isolatePolyfills,
        injectPolyfillsNewerThan);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.MISSING_POLYFILL, CheckLevel.WARNING);
    return options;
  }

  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler() {
      @Nullable Node lastInjected = null;

      @Override
      public Node ensureLibraryInjected(String library, boolean force) {
        if (getInjected().contains(library)) {
          // already injected
          return lastInjected;
        } else {
          // super method just records library in `injected`
          super.ensureLibraryInjected(library, force);
          Node parent = getNodeForCodeInsertion(null);
          Node ast = parseSyntheticCode(library, injectableLibraries.get(library));
          Node lastChild = ast.getLastChild();
          Node firstChild = ast.removeChildren();
          // Any newly added functions must be marked as changed.
          for (Node child = firstChild; child != null; child = child.getNext()) {
            NodeUtil.markNewScopesChanged(child, this);
          }
          if (lastInjected == null) {
            parent.addChildrenToFront(firstChild);
          } else {
            parent.addChildrenAfter(firstChild, lastInjected);
          }
          return lastInjected = lastChild;
        }
      }
    };
  }

  private String addLibraries(String code, String[] libraries) {
    StringBuilder expected = new StringBuilder();
    for (String library : libraries) {
      expected.append(injectableLibraries.get(library));
    }
    expected.append(code);
    return expected.toString();
  }

  private void testDoesNotInject(String code) {
    testInjects(code); // empty list of injections
  }

  private void testInjects(String code, String... libraries) {
    test(code, addLibraries(code, libraries));
  }

  private void testInjects(String code, DiagnosticType warning, String... libraries) {
    test(code, addLibraries(code, libraries), warning(warning));
  }

  @Test
  public void testEmpty() {
    setLanguage(ES6, ES5);
    testInjects("");
  }

  @Test
  public void testClassesInjected() {
    addLibrary("Map", "es6", "es5", "es6/map");
    addLibrary("Set", "es6", "es3", "es6/set");

    setLanguage(ES6, ES5);
    testInjects("var m = new Map();", "es6/map");
    testInjects("var m = new goog.global.Map();", "es6/map");
    testInjects("var Map = goog.global.Map; new Map();", "es6/map");
    testInjects("var m = new window.Map();", "es6/map");

    setLanguage(ES6, ES3);
    testInjects("var s = new Set();", "es6/set");
  }

  @Test
  public void testLibrariesOnlyInjectedOnce() {
    addLibrary("Map", "es6", "es5", "es6/map");

    setLanguage(ES6, ES5);
    testInjects("var m = new Map(); m = new Map();", "es6/map");
  }

  @Test
  public void testClassesNotInjectedIfSufficientLanguageOut() {
    addLibrary("Proxy", "es6", "es6", null);
    addLibrary("Map", "es6", "es5", "es6/map");
    addLibrary("Set", "es6", "es3", "es6/set");

    setLanguage(ES6, ES6);
    testDoesNotInject("new Proxy();");
    testDoesNotInject("var m = new Map();");
    testDoesNotInject("new Set();");
  }

  @Test
  public void testClassesNotInjectedIfDeclaredInScope() {
    addLibrary("Map", "es6", "es5", "es6/map");

    setLanguage(ES6, ES5);
    testDoesNotInject("/** @constructor */ var Map = function() {}; new Map();");
  }

  @Test
  public void testClassesWarnIfInsufficientLanguageOut() {
    addLibrary("Proxy", "es6", "es6", null);
    addLibrary("Map", "es6", "es5", "es6/map");

    setLanguage(ES6, ES5);
    testInjects("new Proxy();", RewritePolyfills.INSUFFICIENT_OUTPUT_VERSION_ERROR);

    setLanguage(ES6, ES3);
    testInjects("new Map();", RewritePolyfills.INSUFFICIENT_OUTPUT_VERSION_ERROR, "es6/map");
  }

  @Test
  public void testGlobalThisInjected() {
    addLibrary("globalThis", "es_2020", "es3", "es6/globalThis");
    addLibrary("Map", "es6", "es5", "es6/map");

    testInjects("globalThis.String", "es6/globalThis");
    testInjects("var m = new globalThis.Map();", "es6/globalThis", "es6/map");
  }

  @Test
  public void testGlobalThisInjectedNotInjectedGivenSufficientLanguageOut() {
    addLibrary("globalThis", "es_2020", "es3", "es6/globalThis");
    addLibrary("Map", "es6", "es5", "es6/map");

    setLanguage(ES_2020, ES_2020);
    testDoesNotInject("globalThis.String");
    testDoesNotInject("var m = new globalThis.Map();");
  }

  @Test
  public void testStaticMethodsInjected() {
    addLibrary("Math.clz32", "es6", "es5", "es6/math/clz32");
    addLibrary("Array.of", "es6", "es3", "es6/array/of");
    addLibrary("Object.keys", "es5", "es3", "es5/object/keys");

    setLanguage(ES6, ES5);
    testInjects("Math.clz32(x);", "es6/math/clz32");

    setLanguage(ES6, ES3);
    testInjects("Array.of(x);", "es6/array/of");

    setLanguage(ES6, ES3);
    testInjects("Object.keys(x);", "es5/object/keys");
  }

  @Test
  public void testStaticMethodsNotInjectedIfSufficientLanguageOut() {
    addLibrary("Array.from", "es6", "es6", null);
    addLibrary("Math.clz32", "es6", "es5", "es6/math/clz32");
    addLibrary("Array.of", "es6", "es3", "es6/array/of");
    addLibrary("Object.keys", "es5", "es3", "es5/object/keys");

    setLanguage(ES6, ES6);
    testDoesNotInject("Array.from(x);");
    testDoesNotInject("Math.clz32(x);");
    testDoesNotInject("Array.of(x);");

    setLanguage(ES5, ES5);
    testDoesNotInject("Object.keys(x);");
  }

  @Test
  public void testStaticMethodsNotInjectedIfDeclaredInScope() {
    addLibrary("Math.clz32", "es6", "es5", "es6/math/clz32");

    setLanguage(ES6, ES5);
    testDoesNotInject("var Math = {clz32: function() {}}; Math.clz32(x);");
  }

  @Test
  public void testStaticMethodsWarnIfInsufficientLanguageOut() {
    addLibrary("Array.from", "es6", "es6", null);
    addLibrary("Math.clz32", "es6", "es5", "es6/math/clz32");

    setLanguage(ES6, ES5);
    testInjects("Array.from(x);", RewritePolyfills.INSUFFICIENT_OUTPUT_VERSION_ERROR);

    setLanguage(ES6, ES3);
    testInjects(
        "Math.clz32(x);", RewritePolyfills.INSUFFICIENT_OUTPUT_VERSION_ERROR, "es6/math/clz32");
  }

  @Test
  public void testStaticMethodsNotInstalledIfGuardedByIf() {
    addLibrary("Array.of", "es6", "es5", "es6/array/of");

    testDoesNotInject("if (Array.of) { Array.of(); } else { Array.of(); }");
    testDoesNotInject("if (x || Array.of) { Array.of(x); }");
    testDoesNotInject("if (x && Array.of) { Array.of(x); }");
    testDoesNotInject("if (!Array.of) { Array.of(x); }");
    testDoesNotInject("if (Array.of != 'x') { Array.of(x); }");
    testDoesNotInject("if (Array.of !== 'x') { Array.of(x); }");
    testDoesNotInject("if (Array.of == 'x') { Array.of(x); }");
    testDoesNotInject("if (Array.of === 'x') { Array.of(x); }");
    testDoesNotInject("if (typeof Array.of == 'function') { Array.of(); }");
  }

  @Test
  public void testStaticMethodsNotInstalledIfGuardedByLogicalOperator() {
    addLibrary("Array.of", "es6", "es5", "es6/array/of");

    testDoesNotInject("Array.of && Array.of();");
    testDoesNotInject("!Array.of || Array.of();");
    // NOTE: needs to be first argument to actually guard.
    testInjects("x && Array.of;", "es6/array/of");
    // NOTE: || is not safe by itself.
    testInjects("Array.of || Array.of();", "es6/array/of");
  }

  @Test
  public void testStaticMethodsNotInstalledIfGuardedByNullishCoalesce() {
    setAcceptedLanguage(LanguageMode.UNSTABLE);
    addLibrary("Array.of", "es_unstable", "es5", "es6/array/of");

    testDoesNotInject("!Array.of ?? Array.of();");
    // NOTE: ?? is not safe by itself.
    setLanguage(ES_2020, ES5);
    testInjects("Array.of ?? Array.of();", "es6/array/of");
  }

  @Test
  public void testStaticMethodsNotInstalledIfGuardedByHook() {
    addLibrary("Array.of", "es6", "es5", "es6/array/of");

    testDoesNotInject("var x = Array.of ? y : function(z) { Array.of(z); };");
    testDoesNotInject("var x = Array.of ? function(y) { Array.of(y); } : z;");
    testDoesNotInject("typeof Array.of ? Array.of(x) : Array.of(y);");
    testDoesNotInject("String(Array.of) == 'foo' ? Array.of(x) : Array.of(y);");
    testDoesNotInject("Array.of instanceof Function ? Array.of(x) : Array.of(y);");
    // NOTE: needs to be first argument to actually guard.
    testInjects("x ? (Array.of ? y : z) : Array.of(x)", "es6/array/of");
  }

  @Test
  public void testStaticMethodsNotInstalledIfGuardedByAbruptReturn() {
    addLibrary("Array.of", "es6", "es5", "es6/array/of");

    testDoesNotInject("if (!Array.of) throw 'x'; Array.of();");
    testDoesNotInject("!function() { if (!Array.of) return; Array.of(); }");
    testDoesNotInject("if (!Array.of) { throw 'x'; } Array.of();");
    // NOTE: abrupt return must be a sibling conditional's direct child
    testInjects("{ if (!Array.of) throw 'x'; } Array.of();", "es6/array/of");
    testInjects("!function() { { if (!Array.of) return; } Array.of(); }()", "es6/array/of");
    testInjects("if (!Array.of) { { throw 'x'; } } Array.of();", "es6/array/of");
    testInjects("{ if (!Array.of) throw 'x'; throw 'x'; } Array.of();", "es6/array/of");
    testInjects("{ if (Array.of) throw 'x'; { throw 'x'; } } Array.of();", "es6/array/of");
    testInjects(
        "if (unrelated) { if (Array.of) throw 'x'; { throw 'x'; } } else Array.of();",
        "es6/array/of");
  }

  @Test
  public void testPrototypeMethodsInjected() {
    addLibrary("String.prototype.endsWith", "es6", "es5", "es6/string/endswith");
    addLibrary("Array.prototype.fill", "es6", "es3", "es6/array/fill");
    addLibrary("Array.prototype.forEach", "es5", "es3", "es5/array/foreach");

    setLanguage(ES6, ES5);
    testInjects("x.endsWith(y);", "es6/string/endswith");
    testInjects("x.fill();", "es6/array/fill");

    setLanguage(ES5, ES3);
    testInjects("x.forEach(y);", "es5/array/foreach");
  }

  @Test
  public void testPrototypeMethodsNotInjectedIfSufficientLanguageOut() {
    addLibrary("String.prototype.normalize", "es6", "es6", null);
    addLibrary("String.prototype.endsWith", "es6", "es5", "es6/string/endswith");
    addLibrary("Array.prototype.fill", "es6", "es3", "es6/array/fill");
    addLibrary("Array.prototype.forEach", "es5", "es3", "es5/array/foreach");

    setLanguage(ES6, ES6);
    testDoesNotInject("x.normalize();");
    testDoesNotInject("x.endsWith();");
    testDoesNotInject("x.fill(y);");

    setLanguage(ES5, ES5);
    testDoesNotInject("x.forEach();");
  }

  @Test
  public void testPrototypeMethodsDontWarnIfInsufficientVersionNumber() {
    addLibrary("String.prototype.normalize", "es6", "es6", null);
    addLibrary("String.prototype.endsWith", "es6", "es5", "es6/string/endswith");
    addLibrary("Array.prototype.fill", "es6", "es3", "es6/array/fill");

    setLanguage(ES6, ES3);
    testDoesNotInject("x.normalize();");
    testInjects("x.endsWith();", "es6/string/endswith");
    testInjects("x.fill(y);", "es6/array/fill");
  }

  @Test
  public void testMultiplePrototypeMethodsWithSameName() {
    addLibrary("Array.prototype.includes", "es6", "es3", "es6/array/includes");
    addLibrary("String.prototype.includes", "es5", "es3", "es5/string/includes");

    setLanguage(ES6, ES5);
    testInjects("x.includes();", "es6/array/includes");

    setLanguage(ES5, ES3);
    testInjects("x.includes();", "es6/array/includes", "es5/string/includes");
  }

  @Test
  public void testPrototypeMethodsInstalledIfStaticMethodShadowed() {
    addLibrary("String.prototype.endsWith", "es6", "es5", "es6/string/endswith");

    setLanguage(ES6, ES5);
    testInjects(
        """
        var string = {}; string.endsWith = function() {};
        string.foo = function(string) { return string.endsWith('x'); };
        """,
        "es6/string/endswith");
  }

  // NOTE(sdh): it's not clear what makes the most sense here.  At one point we
  // took care to avoid installing these, but it may make sense to instead leave
  // this distinction to a type-based optimization.  As such, I've simplified the
  // logic to no longer look at variables' scope and instead just skiplist known
  // symbols like goog.string and goog.array.
  @Test
  public void testPrototypeMethodsInstalledIfActuallyStatic() {
    addLibrary("String.prototype.endsWith", "es6", "es5", "es6/string/endswith");

    setLanguage(ES6, ES5);
    testInjects(
        "var string = {}; string.endsWith = function() {}; string.endsWith('x');",
        "es6/string/endswith");
    testInjects(
        "var string = {endsWith: function() {}}; string.endsWith('x');", "es6/string/endswith");
    testInjects(
        """
        var string = {}; string.endsWith = function() {};
        string.foo = function() { return string.endsWith('x'); };
        """,
        "es6/string/endswith");
  }

  @Test
  public void testPrototypeMethodsNotInstalledIfGuardedByIf() {
    addLibrary("String.prototype.endsWith", "es6", "es5", "es6/string/endswith");

    testDoesNotInject("if (String.prototype.endsWith) { x.endsWith(); } else { y.endsWith(); }");
    testDoesNotInject("if (x || String.prototype.endsWith) { x.endsWith(); }");
    testDoesNotInject("if (x && String.prototype.endsWith) { x.endsWith(); }");
    testDoesNotInject("if (!String.prototype.endsWith) { x.endsWith(); }");
    testDoesNotInject("if (String.prototype.endsWith != 'x') { x.endsWith(); }");
    testDoesNotInject("if (String.prototype.endsWith !== 'x') { x.endsWith(); }");
    testDoesNotInject("if (String.prototype.endsWith == 'x') { x.endsWith(); }");
    testDoesNotInject("if (String.prototype.endsWith === 'x') { x.endsWith(); }");
    testDoesNotInject("if (typeof String.prototype.endsWith == 'function') { x.endsWith(); }");
  }

  @Test
  public void testPrototypeMethodsNotInstalledIfGuardedByLogicalOperator() {
    addLibrary("String.prototype.endsWith", "es6", "es5", "es6/string/endswith");

    testDoesNotInject("String.prototype.endsWith && x.endsWith();");
    testDoesNotInject("!String.prototype.endsWith || x.endsWith();");
    testDoesNotInject("x.endsWith && x.endsWith();");
    // NOTE: needs to be first argument to actually guard.
    testInjects("x && x.endsWith;", "es6/string/endswith");
    // NOTE: || is not safe by itself.
    testInjects("String.prototype.endsWith || x.endsWith();", "es6/string/endswith");
  }

  @Test
  public void testPrototypeMethodsNotInstalledIfGuardedByNullishCoalesce() {
    setAcceptedLanguage(LanguageMode.UNSTABLE);
    addLibrary("String.prototype.endsWith", "es_unstable", "es5", "es6/string/endswith");

    testDoesNotInject("!String.prototype.endsWith ?? x.endsWith();");
    // NOTE: ?? is not safe by itself.
    setLanguage(ES_2020, ES5);
    testInjects("String.prototype.endsWith ?? x.endsWith();", "es6/string/endswith");
  }

  @Test
  public void testPrototypeMethodsNotInstalledIfGuardedByHook() {
    addLibrary("String.prototype.endsWith", "es6", "es5", "es6/string/endswith");

    testDoesNotInject("var x = String.prototype.endsWith ? y : function(z) { z.endsWith(); };");
    testDoesNotInject("var x = String.prototype.endsWith ? function(y) { y.endsWith(); } : z;");
    testDoesNotInject("typeof String.prototype.endsWith ? x.endsWith() : y.endsWith();");
    testDoesNotInject("String(x.endsWith) == 'foo' ? x.endsWith() : y.endsWith();");
    testDoesNotInject("Boolean(x.endsWith) ? x.endsWith() : y.endsWith();");
    // NOTE: needs to be first argument to actually guard.
    testInjects("x ? (String.prototype.endsWith ? y : z) : x.endsWith()", "es6/string/endswith");
  }

  @Test
  public void testPrototypeMethodsNotInstalledIfGuardedByAbruptReturn() {
    addLibrary("String.prototype.endsWith", "es6", "es5", "es6/string/endswith");

    testDoesNotInject("if (!x.endsWith) throw 'x'; y.endsWith();");
    testDoesNotInject("if (!x.endsWith) { throw 'x'; } y.endsWith();");
    testDoesNotInject("!function() { if (!x.endsWith) return; y.endsWith(); }()");
    // NOTE: abrupt return must be a sibling conditional's direct child
    testInjects("{ if (!x.endsWith) throw 'x'; } y.endsWith();", "es6/string/endswith");
    testInjects("if (!x.endsWith) { { throw 'x'; } } y.endsWith();", "es6/string/endswith");
    testInjects(
        "!function() { { if (!x.endsWith) return; } y.endsWith(); }()", "es6/string/endswith");
    testInjects("{ if (!x.endsWith) throw 'x'; throw 'x'; } y.endsWith();", "es6/string/endswith");
    testInjects(
        "{ if (x.endsWith) throw 'x'; { throw 'x'; } } x.endsWith();", "es6/string/endswith");
    testInjects(
        "if (unrelated) { if (x.endsWith) throw 'x'; { throw 'x'; } } else x.endsWith();",
        "es6/string/endswith");
  }

  @Test
  public void testCleansUpUnnecessaryPolyfills() {
    // Put two polyfill statements in the same library.
    injectableLibraries.put(
        "es6/set",
        """
        $jscomp.polyfill('Set', function() {}, 'es6', 'es3');
        $jscomp.polyfill('Map', function() {}, 'es5', 'es3');
        """);
    polyfillTable.add("Set es6 es3 es6/set");

    setLanguage(ES6, ES5);
    test(
        "var set = new Set();",
        """
        $jscomp.polyfill('Set', function() {}, 'es6', 'es3');
        var set = new Set();
        """);

    setLanguage(ES6, ES3);
    test(
        "var set = new Set();",
        """
        $jscomp.polyfill('Set', function() {}, 'es6', 'es3');
        $jscomp.polyfill('Map', function() {}, 'es5', 'es3');
        var set = new Set();
        """);
  }

  @Test
  public void testRegexFeatureSetException() {
    // Tests the special casing done to handle the fact that Safari has lagged behind on
    // implementation of some regex features, which messes with the way polyfills are linked
    // with the feature set of the specification that added them.

    // Promise.prototype.finally was added in ES_2018
    addLibrary("Promise.prototype.finally", "es_2018", "es3", "es6/promise/finally");

    // input is assumed to contain features from the ES_2020 spec
    setAcceptedLanguage(ES_2020);

    // The output is required to work for the latest browsers as of early 2020
    // This will set an output `FeatureSet` that does not include some regex features that are
    // part of ES_2018, so ES_2018 will not be "supported", and could cause a polyfill
    // that is part of ES_2018 to be included. We have special casing to avoid this.
    setBrowserFeaturesetYear(2020);

    // the change marking check will report an error because:
    // 1. We add the polyfill code to the SCRIPT node
    // 2. We then strip out the polyfill
    // 3. We mark the SCRIPT node as changed, but it ended up the same as it started.
    disableValidateAstChangeMarking();

    // The polyfill is not included even though the output feature set is not a super set of ES_2018
    testSame("Promise.resolve(1).finally(console.log('done'));");
  }

  @Test
  public void testCleansUpUnnecessaryPreviouslyInjectedPolyfills() {
    // Put two polyfill statements in the same library.
    injectableLibraries.put(
        "es6/set",
        """
        $jscomp.polyfill('Set', function() {}, 'es6', 'es3');
        // pretend Map isn't needed for ES5
        $jscomp.polyfill('Map', function() {}, 'es5', 'es3');
        """);
    polyfillTable.add("Set es6 es3 es6/set");

    // simulate injection of Map by a prior-run pass
    ensureLibraryInjected("es6/set");
    setLanguage(ES6, ES5);
    test(
        "var set = new Set();",
        """
         // Map gets removed even though not added by RewritePolyfills
        $jscomp.polyfill('Set', function() {}, 'es6', 'es3');
        var set = new Set();
        """);
  }

  @Test
  public void testAddsPolyfillMethodToExterns() {
    isolatePolyfills = true;
    addLibrary("String.prototype.endsWith", "es6", "es5", "es6/string/endswith");

    testExternChanges(srcs(""), expected("var $jscomp$lookupPolyfilledValue"));
  }

  @Test
  public void testNoCodeChangesIfInjectionDisabled() {
    isolatePolyfills = true;
    injectPolyfills = false;
    addLibrary("String.prototype.endsWith", "es6", "es5", "es6/string/endswith");

    testExternChanges(srcs(""), expected("var $jscomp$lookupPolyfilledValue"));

    allowExternsChanges();
    testSame("'x'.endsWith('y');");
  }

  @Test
  public void testForceInject_es5_addsES6AndES8() {
    injectPolyfillsNewerThan = LanguageMode.ECMASCRIPT5;
    addLibrary("String.prototype.endsWith", "es6", "es5", "es6/string/endswith");
    addLibrary("Object.values", "es8", "es3", "es6/object/values");

    testInjects("", "es6/string/endswith", "es6/object/values");
  }

  @Test
  public void testForceInject_es2015_addsES8Polyfill() {
    injectPolyfillsNewerThan = LanguageMode.ECMASCRIPT5;
    addLibrary("Object.values", "es8", "es3", "es6/object/values");

    testInjects("", "es6/object/values");
  }

  @Test
  public void testForceInject_es2015_skipsEs2015Polyfills() {
    injectPolyfillsNewerThan = LanguageMode.ECMASCRIPT_2015;
    addLibrary("String.prototype.endsWith", "es6", "es5", "es6/string/endswith");

    testDoesNotInject("");
  }
}
