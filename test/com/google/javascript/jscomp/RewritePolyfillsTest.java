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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.RewritePolyfills.Polyfills;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;

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
    assertTrue(getLastCompiler().needsEs6Runtime);

    setLanguage(ES6, ES3);
    test(
        "var s = new Set();",
        "$jscomp.Set$install(); var s = new $jscomp.Set();");
    assertTrue(getLastCompiler().needsEs6Runtime);
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
    assertFalse(getLastCompiler().needsEs6Runtime);

    testSame("var m = new Map();");
    assertFalse(getLastCompiler().needsEs6Runtime);

    testSame("new Set();");
    assertFalse(getLastCompiler().needsEs6Runtime);
  }

  public void testClassesNotRewrittenIfDeclaredInScope() {
    setLanguage(ES6, ES5);
    testSame("/** @constructor */ var Map = function() {}; new Map();");
    assertFalse(getLastCompiler().needsEs6Runtime);
  }

  public void testClassesWarnIfInsufficientLanguageOut() {
    setLanguage(ES6, ES5);
    testSame("new Proxy();", RewritePolyfills.INSUFFICIENT_OUTPUT_VERSION_ERROR);
    assertFalse(getLastCompiler().needsEs6Runtime);

    setLanguage(ES6, ES3);
    test(
        "new Map();",
        "$jscomp.Map$install(); new $jscomp.Map();",
        null,
        RewritePolyfills.INSUFFICIENT_OUTPUT_VERSION_ERROR);
    assertTrue(getLastCompiler().needsEs6Runtime);
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
    assertTrue(getLastCompiler().needsEs6Runtime);

    setLanguage(ES6, ES3);
    test(
        "Array.of(x);",
        "$jscomp.array.of(x);");
    assertTrue(getLastCompiler().needsEs6Runtime);

    setLanguage(ES6, ES3);
    test(
        "Object.keys(x);",
        "$jscomp.object.keys(x);");
    assertTrue(getLastCompiler().needsEs6Runtime); // No point separating out separate ES5 runtime
  }

  public void testStaticMethodsNotRewrittenIfSufficientLanguageOut() {
    setLanguage(ES6, ES6);
    testSame("Array.from(x);");
    assertFalse(getLastCompiler().needsEs6Runtime);

    testSame("Math.clz32(x);");
    assertFalse(getLastCompiler().needsEs6Runtime);

    testSame("Array.of(x);");
    assertFalse(getLastCompiler().needsEs6Runtime);

    setLanguage(ES5, ES5);
    testSame("Object.keys(x);");
    assertFalse(getLastCompiler().needsEs6Runtime);
  }

  public void testStaticMethodsNotRewrittenIfDeclaredInScope() {
    setLanguage(ES6, ES5);
    testSame("var Math = {clz32: function() {}}; Math.clz32(x);");
    assertFalse(getLastCompiler().needsEs6Runtime);
  }

  public void testStaticMethodsWarnIfInsufficientLanguageOut() {
    setLanguage(ES6, ES5);
    testSame("Array.from(x);", RewritePolyfills.INSUFFICIENT_OUTPUT_VERSION_ERROR);
    assertFalse(getLastCompiler().needsEs6Runtime);

    setLanguage(ES6, ES3);
    test(
        "Math.clz32(x);",
        "$jscomp.math.clz32(x);",
        null,
        RewritePolyfills.INSUFFICIENT_OUTPUT_VERSION_ERROR);
    assertTrue(getLastCompiler().needsEs6Runtime);
  }

  public void testPrototypeMethodsInstalled() {
    setLanguage(ES6, ES5);
    test(
        "x.endsWith(y);",
        "$jscomp.string.endsWith$install(); x.endsWith(y);");
    assertTrue(getLastCompiler().needsEs6Runtime);

    test(
        "x.fill();",
        "$jscomp.array.fill$install(); x.fill();");
    assertTrue(getLastCompiler().needsEs6Runtime);

    setLanguage(ES5, ES3);
    test(
        "x.forEach(y);",
        "$jscomp.array.forEach$install(); x.forEach(y);");
    assertTrue(getLastCompiler().needsEs6Runtime);
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

  /**
   * If multiple modules use the same method, the install() method is
   * called in both, so that code works regardless of which module is
   * loaded first.
   */
  public void testPrototypeMethodsInstalled_JSModules() {
    setLanguage(ES6, ES5);

    JSModule[] jsModules = createModules(
        "x.endsWith(y);",
        "w.endsWith(z);");

    test(
        jsModules,
        new String[] {
          "$jscomp.string.endsWith$install(); x.endsWith(y);",
          "$jscomp.string.endsWith$install(); w.endsWith(z);",
        });
  }
  
  public void testPrototypeMethodsNotInstalledIfSufficientLanguageOut() {
    setLanguage(ES6, ES6);
    testSame("x.normalize();");
    assertFalse(getLastCompiler().needsEs6Runtime);

    testSame("x.endsWith();");
    assertFalse(getLastCompiler().needsEs6Runtime);

    testSame("x.fill(y);");
    assertFalse(getLastCompiler().needsEs6Runtime);

    setLanguage(ES5, ES5);
    testSame("x.forEach();");
    assertFalse(getLastCompiler().needsEs6Runtime);
  }

  public void testPrototypeMethodsInstalledIfStaticMethodShadowed() {
    setLanguage(ES6, ES5);
    test(
        "var string = {}; string.endsWith = function() {}; "
        + "string.foo = function(string) { return string.endsWith('x'); };",

        "$jscomp.string.endsWith$install(); "
        + "var string = {}; string.endsWith = function() {}; "
        + "string.foo = function(string) { return string.endsWith('x'); };");
    assertTrue(getLastCompiler().needsEs6Runtime);
  }

  public void testPrototypeMethodsNotInstalledIfActuallyStatic() {
    setLanguage(ES6, ES5);
    testSame("var string = {}; string.endsWith = function() {}; string.endsWith('x');");
    assertFalse(getLastCompiler().needsEs6Runtime);

    testSame("var string = {endsWith: function() {}}; string.endsWith('x');");
    assertFalse(getLastCompiler().needsEs6Runtime);

    testSame(
        "var string = {}; string.endsWith = function() {}; "
        + "string.foo = function() { return string.endsWith('x'); };");
    assertFalse(getLastCompiler().needsEs6Runtime);
  }
}
