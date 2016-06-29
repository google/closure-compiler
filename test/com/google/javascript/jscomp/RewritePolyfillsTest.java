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
import com.google.javascript.jscomp.RewritePolyfills.Polyfills;
import com.google.javascript.rhino.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unit tests for the RewritePolyfills compiler pass. */
public final class RewritePolyfillsTest extends CompilerTestCase {

  private static final LanguageMode ES6 = LanguageMode.ECMASCRIPT6_STRICT;
  private static final LanguageMode ES5 = LanguageMode.ECMASCRIPT5_STRICT;
  private static final LanguageMode ES3 = LanguageMode.ECMASCRIPT3;

  private final Map<String, String> injectableLibraries = new HashMap<>();
  private final List<String> polyfillTable = new ArrayList<>();

  private void addLibrary(String name, String from, String to, String library) {
    if (library != null) {
      injectableLibraries.put(
          library,
          String.format("$jscomp.polyfill('%s', function() {}, '%s', '%s');\n", name, from, to));
    }
    polyfillTable.add(String.format("%s %s %s %s", name, from, to, nullToEmpty(library)));
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    injectableLibraries.clear();
    polyfillTable.clear();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RewritePolyfills(compiler, Polyfills.fromTable(Joiner.on("\n").join(polyfillTable)));
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.rewritePolyfills = true;
    return options;
  }

  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler() {
      Node lastInjected = null;
      @Override Node ensureLibraryInjected(String library, boolean force) {
        Node parent = getNodeForCodeInsertion(null);
        Node ast = parseSyntheticCode(injectableLibraries.get(library));
        Node firstChild = ast.removeChildren();
        Node lastChild = firstChild.getLastSibling();
        if (lastInjected == null) {
          parent.addChildrenToFront(firstChild);
        } else {
          parent.addChildrenAfter(firstChild, lastInjected);
        }
        return lastInjected = lastChild;
      }
    };
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  private String addLibraries(String code, String[] libraries) {
    StringBuilder expected = new StringBuilder();
    for (String library : libraries) {
      expected.append(injectableLibraries.get(library));
    }
    expected.append(code);
    return expected.toString();
  }

  private void testInjects(String code, String... libraries) {
    test(code, addLibraries(code, libraries));
  }

  private void testInjects(String code, DiagnosticType warning, String... libraries) {
    test(code, addLibraries(code, libraries), null, warning);
  }

  public void testEmpty() {
    setLanguage(ES6, ES5);
    testInjects("");
  }

  public void testClassesInjected() {
    addLibrary("Map", "es6", "es5", "es6/map");
    addLibrary("Set", "es6", "es3", "es6/set");

    setLanguage(ES6, ES5);
    testInjects("var m = new Map();", "es6/map");

    setLanguage(ES6, ES3);
    testInjects("var s = new Set();", "es6/set");
  }

  public void testLibrariesOnlyInjectedOnce() {
    addLibrary("Map", "es6", "es5", "es6/map");

    setLanguage(ES6, ES5);
    testInjects("var m = new Map(); m = new Map();", "es6/map");
  }

  public void testClassesNotInjectedIfSufficientLanguageOut() {
    addLibrary("Proxy", "es6", "es6", null);
    addLibrary("Map", "es6", "es5", "es6/map");
    addLibrary("Set", "es6", "es3", "es6/set");

    setLanguage(ES6, ES6);
    testInjects("new Proxy();");
    testInjects("var m = new Map();");
    testInjects("new Set();");
  }

  public void testClassesNotInjectedIfDeclaredInScope() {
    addLibrary("Map", "es6", "es5", "es6/map");

    setLanguage(ES6, ES5);
    testInjects("/** @constructor */ var Map = function() {}; new Map();");
  }

  public void testClassesWarnIfInsufficientLanguageOut() {
    addLibrary("Proxy", "es6", "es6", null);
    addLibrary("Map", "es6", "es5", "es6/map");

    setLanguage(ES6, ES5);
    testInjects("new Proxy();", RewritePolyfills.INSUFFICIENT_OUTPUT_VERSION_ERROR);

    setLanguage(ES6, ES3);
    testInjects("new Map();", RewritePolyfills.INSUFFICIENT_OUTPUT_VERSION_ERROR, "es6/map");
  }

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

  public void testStaticMethodsNotInjectedIfSufficientLanguageOut() {
    addLibrary("Array.from", "es6", "es6", null);
    addLibrary("Math.clz32", "es6", "es5", "es6/math/clz32");
    addLibrary("Array.of", "es6", "es3", "es6/array/of");
    addLibrary("Object.keys", "es5", "es3", "es5/object/keys");

    setLanguage(ES6, ES6);
    testInjects("Array.from(x);");
    testInjects("Math.clz32(x);");
    testInjects("Array.of(x);");

    setLanguage(ES5, ES5);
    testInjects("Object.keys(x);");
  }

  public void testStaticMethodsNotInjectedIfDeclaredInScope() {
    addLibrary("Math.clz32", "es6", "es5", "es6/math/clz32");

    setLanguage(ES6, ES5);
    testInjects("var Math = {clz32: function() {}}; Math.clz32(x);");
  }

  public void testStaticMethodsWarnIfInsufficientLanguageOut() {
    addLibrary("Array.from", "es6", "es6", null);
    addLibrary("Math.clz32", "es6", "es5", "es6/math/clz32");

    setLanguage(ES6, ES5);
    testInjects("Array.from(x);", RewritePolyfills.INSUFFICIENT_OUTPUT_VERSION_ERROR);

    setLanguage(ES6, ES3);
    testInjects(
        "Math.clz32(x);", RewritePolyfills.INSUFFICIENT_OUTPUT_VERSION_ERROR, "es6/math/clz32");
  }

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

  public void testPrototypeMethodsNotInjectedIfSufficientLanguageOut() {
    addLibrary("String.prototype.normalize", "es6", "es6", null);
    addLibrary("String.prototype.endsWith", "es6", "es5", "es6/string/endswith");
    addLibrary("Array.prototype.fill", "es6", "es3", "es6/array/fill");
    addLibrary("Array.prototype.forEach", "es5", "es3", "es5/array/foreach");

    setLanguage(ES6, ES6);
    testInjects("x.normalize();");
    testInjects("x.endsWith();");
    testInjects("x.fill(y);");

    setLanguage(ES5, ES5);
    testInjects("x.forEach();");
  }

  public void testMultiplePrototypeMethodsWithSameName() {
    addLibrary("Array.prototype.includes", "es6", "es3", "es6/array/includes");
    addLibrary("String.prototype.includes", "es5", "es3", "es5/string/includes");

    setLanguage(ES6, ES5);
    testInjects("x.includes();", "es6/array/includes");

    setLanguage(ES5, ES3);
    testInjects("x.includes();", "es6/array/includes", "es5/string/includes");
  }

  public void testPrototypeMethodsInstalledIfStaticMethodShadowed() {
    addLibrary("String.prototype.endsWith", "es6", "es5", "es6/string/endswith");

    setLanguage(ES6, ES5);
    testInjects(
        "var string = {}; string.endsWith = function() {}; "
            + "string.foo = function(string) { return string.endsWith('x'); };",
        "es6/string/endswith");
  }

  // NOTE(sdh): it's not clear what makes the most sense here.  At one point we
  // took care to avoid installing these, but it may make sense to instead leave
  // this distinction to a type-based optimization.  As such, I've simplified the
  // logic to no longer look at variables' scope and instead just blacklist known
  // symbols like goog.string and goog.array.
  public void testPrototypeMethodsInstalledIfActuallyStatic() {
    addLibrary("String.prototype.endsWith", "es6", "es5", "es6/string/endswith");

    setLanguage(ES6, ES5);
    testInjects(
        "var string = {}; string.endsWith = function() {}; string.endsWith('x');",
        "es6/string/endswith");
    testInjects(
        "var string = {endsWith: function() {}}; string.endsWith('x');",
        "es6/string/endswith");
    testInjects(
        "var string = {}; string.endsWith = function() {}; "
            + "string.foo = function() { return string.endsWith('x'); };",
        "es6/string/endswith");
  }

  public void testPrototypeMethodsNotInstalledIfBlacklisted() {
    addLibrary("String.prototype.endsWith", "es6", "es5", "es6/string/endswith");

    setLanguage(ES6, ES5);
    // NOTE: By the time this pass runs, goog.module aliases have already been fully expanded.
    testInjects("goog.string.endsWith('x');");
  }
}
