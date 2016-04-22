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

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.RewritePolyfills.Polyfills;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;

import java.util.Set;

/** Unit tests for the RewritePolyfills compiler pass. */
public final class RewritePolyfillsTest extends CompilerTestCase {

  private static final LanguageMode ES6 = LanguageMode.ECMASCRIPT6_STRICT;
  private static final LanguageMode ES5 = LanguageMode.ECMASCRIPT5_STRICT;
  private static final LanguageMode ES3 = LanguageMode.ECMASCRIPT3;

  private static final Polyfills POLYFILLS = new Polyfills.Builder()
      // Classes
      .addClasses(FeatureSet.ES6, FeatureSet.ES6, "", "Proxy")
      .addClasses(FeatureSet.ES6, FeatureSet.ES5, "$jscomp", "Map")
      .addClasses(FeatureSet.ES6, FeatureSet.ES3, "$jscomp", "Set")
      // Statics
      .addStatics(FeatureSet.ES6, FeatureSet.ES6, "", "Array", "from")
      .addStatics(FeatureSet.ES6, FeatureSet.ES5, "$jscomp.math", "Math", "clz32")
      .addStatics(FeatureSet.ES6, FeatureSet.ES3, "$jscomp.array", "Array", "of")
      .addStatics(FeatureSet.ES5, FeatureSet.ES3, "$jscomp.object", "Object", "keys")
      // Methods
      .addMethods(FeatureSet.ES6, FeatureSet.ES6, "", "normalize")
      .addMethods(FeatureSet.ES6, FeatureSet.ES5, "$jscomp.string", "endsWith")
      .addMethods(FeatureSet.ES6, FeatureSet.ES3, "$jscomp.array", "fill")
      .addMethods(FeatureSet.ES5, FeatureSet.ES3, "$jscomp.array", "forEach")
      .build();


  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RewritePolyfills(compiler, POLYFILLS);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.rewritePolyfills = true;
    return options;
  }

  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler();
  }

  protected Set<String> getInjectedLibraries() {
    return ((NoninjectingCompiler) getLastCompiler()).injected;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testEmpty() {
    setLanguage(ES6, ES5);
    testSame("");
  }

  public void testClassesRewritten() {
    setLanguage(ES6, ES5);
    test(
        "var m = new Map();",
        "$jscomp.Map$install(); var m = new $jscomp.Map();");
    assertThat(getInjectedLibraries()).containsExactly("es6_runtime");

    setLanguage(ES6, ES3);
    test(
        "var s = new Set();",
        "$jscomp.Set$install(); var s = new $jscomp.Set();");
    assertThat(getInjectedLibraries()).containsExactly("es6_runtime");
  }

  public void testClassesRewrittenInstallerNotDuplicated() {
    setLanguage(ES6, ES5);
    test(
        "var m = new Map(); var n = new Map();",
        "$jscomp.Map$install(); var m = new $jscomp.Map(); var n = new $jscomp.Map()");
  }

  public void testClassesNotRewrittenIfSufficientLanguageOut() {
    setLanguage(ES6, ES6);
    testSame("new Proxy();");
    assertThat(getInjectedLibraries()).isEmpty();

    testSame("var m = new Map();");
    assertThat(getInjectedLibraries()).isEmpty();

    testSame("new Set();");
    assertThat(getInjectedLibraries()).isEmpty();
  }

  public void testClassesNotRewrittenIfDeclaredInScope() {
    setLanguage(ES6, ES5);
    testSame("/** @constructor */ var Map = function() {}; new Map();");
    assertThat(getInjectedLibraries()).isEmpty();
  }

  public void testClassesWarnIfInsufficientLanguageOut() {
    setLanguage(ES6, ES5);
    testSame("new Proxy();", RewritePolyfills.INSUFFICIENT_OUTPUT_VERSION_ERROR);
    assertThat(getInjectedLibraries()).isEmpty();

    setLanguage(ES6, ES3);
    test(
        "new Map();",
        "$jscomp.Map$install(); new $jscomp.Map();",
        null,
        RewritePolyfills.INSUFFICIENT_OUTPUT_VERSION_ERROR);
    assertThat(getInjectedLibraries()).containsExactly("es6_runtime");
  }

  public void testJsdocTypesRewritten() {
    setLanguage(ES6, ES5);
    test(
        "/** @type {!Map<string, {x: !Set<string>, y: number}>} */ var x;",
        "/** @type {!$jscomp.Map<string, {x: !$jscomp.Set<string>, y: number}>} */ var x;");

    setLanguage(ES6, ES3);
    test(
        "/** @param {function(!Set<string>)} s */ function f(s) {}",
        "/** @param {function(!$jscomp.Set<string>)} s */ function f(s) {}");
  }

  public void testJsdocTypesNotRewrittenIfSufficientLanguageOut() {
    setLanguage(ES6, ES6);
    testSame("/** @type {!Map<string, {x: !Set<string>, y: number}>} */ var x;");
  }

  public void testStaticMethodsRewritten() {
    setLanguage(ES6, ES5);
    test(
        "Math.clz32(x);",
        "$jscomp.math.clz32(x);");
    assertThat(getInjectedLibraries()).containsExactly("es6_runtime");

    setLanguage(ES6, ES3);
    test(
        "Array.of(x);",
        "$jscomp.array.of(x);");
    assertThat(getInjectedLibraries()).containsExactly("es6_runtime");

    setLanguage(ES6, ES3);
    test(
        "Object.keys(x);",
        "$jscomp.object.keys(x);");
    assertThat(getInjectedLibraries()).containsExactly("es6_runtime");
  }

  public void testStaticMethodsNotRewrittenIfSufficientLanguageOut() {
    setLanguage(ES6, ES6);
    testSame("Array.from(x);");
    assertThat(getInjectedLibraries()).isEmpty();

    testSame("Math.clz32(x);");
    assertThat(getInjectedLibraries()).isEmpty();

    testSame("Array.of(x);");
    assertThat(getInjectedLibraries()).isEmpty();

    setLanguage(ES5, ES5);
    testSame("Object.keys(x);");
    assertThat(getInjectedLibraries()).isEmpty();
  }

  public void testStaticMethodsNotRewrittenIfDeclaredInScope() {
    setLanguage(ES6, ES5);
    testSame("var Math = {clz32: function() {}}; Math.clz32(x);");
    assertThat(getInjectedLibraries()).isEmpty();
  }

  public void testStaticMethodsWarnIfInsufficientLanguageOut() {
    setLanguage(ES6, ES5);
    testSame("Array.from(x);", RewritePolyfills.INSUFFICIENT_OUTPUT_VERSION_ERROR);
    assertThat(getInjectedLibraries()).isEmpty();

    setLanguage(ES6, ES3);
    test(
        "Math.clz32(x);",
        "$jscomp.math.clz32(x);",
        null,
        RewritePolyfills.INSUFFICIENT_OUTPUT_VERSION_ERROR);
    assertThat(getInjectedLibraries()).containsExactly("es6_runtime");
  }

  public void testPrototypeMethodsInstalled() {
    setLanguage(ES6, ES5);
    test(
        "x.endsWith(y);",
        "$jscomp.string.endsWith$install(); x.endsWith(y);");
    assertThat(getInjectedLibraries()).containsExactly("es6_runtime");

    test(
        "x.fill();",
        "$jscomp.array.fill$install(); x.fill();");
    assertThat(getInjectedLibraries()).containsExactly("es6_runtime");

    setLanguage(ES5, ES3);
    test(
        "x.forEach(y);",
        "$jscomp.array.forEach$install(); x.forEach(y);");
    assertThat(getInjectedLibraries()).containsExactly("es6_runtime");
  }

  public void testPrototypeMethodsInstalled_JSModule() {
    setLanguage(ES6, ES5);

    JSModule[] jsModules = createModules("x.endsWith(y);");

    test(
        jsModules,
        new String[] {
          "$jscomp.string.endsWith$install(); x.endsWith(y);"
        });
  }

  /** Install methods are only added to the base module. */
  public void testPrototypeMethodsInstalled_JSModules() {
    setLanguage(ES6, ES5);

    JSModule[] jsModules = createModules(
        "z();",
        "x.endsWith(y);",
        "w.endsWith(z);");

    test(
        jsModules,
        new String[] {
          "$jscomp.string.endsWith$install(); z();",
          "x.endsWith(y);",
          "w.endsWith(z);",
        });
  }

  public void testPrototypeMethodsNotInstalledIfSufficientLanguageOut() {
    setLanguage(ES6, ES6);
    testSame("x.normalize();");
    assertThat(getInjectedLibraries()).isEmpty();

    testSame("x.endsWith();");
    assertThat(getInjectedLibraries()).isEmpty();

    testSame("x.fill(y);");
    assertThat(getInjectedLibraries()).isEmpty();

    setLanguage(ES5, ES5);
    testSame("x.forEach();");
    assertThat(getInjectedLibraries()).isEmpty();
  }

  public void testPrototypeMethodsInstalledIfStaticMethodShadowed() {
    setLanguage(ES6, ES5);
    test(
        "var string = {}; string.endsWith = function() {}; "
        + "string.foo = function(string) { return string.endsWith('x'); };",

        "$jscomp.string.endsWith$install(); "
        + "var string = {}; string.endsWith = function() {}; "
        + "string.foo = function(string) { return string.endsWith('x'); };");
    assertThat(getInjectedLibraries()).containsExactly("es6_runtime");
  }

  public void testPrototypeMethodsNotInstalledIfActuallyStatic() {
    setLanguage(ES6, ES5);
    testSame("var string = {}; string.endsWith = function() {}; string.endsWith('x');");
    assertThat(getInjectedLibraries()).isEmpty();

    testSame("var string = {endsWith: function() {}}; string.endsWith('x');");
    assertThat(getInjectedLibraries()).isEmpty();

    testSame(
        "var string = {}; string.endsWith = function() {}; "
        + "string.foo = function() { return string.endsWith('x'); };");
    assertThat(getInjectedLibraries()).isEmpty();
  }
}
