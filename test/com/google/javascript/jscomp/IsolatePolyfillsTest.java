/*
 * Copyright 2020 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.CompilerOptions.PropertyCollapseLevel;
import com.google.javascript.jscomp.PolyfillUsageFinder.Polyfills;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link com.google.javascript.jscomp.IsolatePolyfills} */
@RunWith(JUnit4.class)
public final class IsolatePolyfillsTest extends CompilerTestCase {

  private static final LanguageMode ES_2020 = LanguageMode.ECMASCRIPT_2020;
  private static final LanguageMode ES6 = LanguageMode.ECMASCRIPT_2015;
  private static final LanguageMode ES5 = LanguageMode.ECMASCRIPT5_STRICT;
  private static final LanguageMode ES3 = LanguageMode.ECMASCRIPT3;

  private final List<String> polyfillTable = new ArrayList<>();
  private final Set<String> polyfillsToInject = new LinkedHashSet<>();

  private boolean enablePropertyFlattening = false;

  private void addLibrary(String name, String from, String to, String library) {
    polyfillTable.add(String.format("%s %s %s %s", name, from, to, nullToEmpty(library)));
    polyfillsToInject.add(name);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    polyfillTable.clear();
    polyfillsToInject.clear();
    disableCompareSyntheticCode();
    allowExternsChanges();
    setLanguageOut(LanguageMode.ECMASCRIPT5);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return (externs, root) -> {
      // Synthetic definition of $jscomp$lookupPolyfilledValue
      compiler
          .getSynthesizedExternsInputAtEnd()
          .getAstRoot(compiler)
          .addChildToBack(IR.var(IR.name("$jscomp$lookupPolyfilledValue")));
      addPolyfillInjection(compiler.getNodeForCodeInsertion(/* module= */ null), compiler);
      new IsolatePolyfills(compiler, Polyfills.fromTable(Joiner.on("\n").join(polyfillTable)))
          .process(externs, root);
    };
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setCollapsePropertiesLevel(
        enablePropertyFlattening ? PropertyCollapseLevel.ALL : PropertyCollapseLevel.NONE);
    return options;
  }

  /** Adds synthetic defintitions of all the polyfills to the AST */
  private void addPolyfillInjection(Node parent, AbstractCompiler compiler) {
    if (this.polyfillsToInject.isEmpty()) {
      return;
    }
    String jscompPolyfillName =
        this.enablePropertyFlattening ? "$jscomp$polyfill" : "$jscomp.polyfill";

    StringBuilder syntheticCode = new StringBuilder().append("var $jscomp = {};\n");

    for (String polyfill : polyfillsToInject) {
      // $jscomp.polyfill('syntheticName');
      syntheticCode.append(jscompPolyfillName);
      syntheticCode.append("('");
      syntheticCode.append(polyfill);
      syntheticCode.append("');\n");
    }

    Node codeRoot = compiler.parseSyntheticCode(syntheticCode.toString());
    parent.addChildrenToFront(codeRoot.removeChildren());
    compiler.reportChangeToEnclosingScope(parent);
  }

  @Test
  public void testEmpty() {
    setLanguage(ES6, ES5);
    testSame("");
  }

  @Test
  public void testClassesAreIsolated() {
    addLibrary("Map", "es6", "es5", "es6/map");

    setLanguage(ES6, ES5);
    test("var m = new Map();", "var m = new $jscomp.polyfills['Map']();");
    test("var m = new window.Map();", "var m = new $jscomp.polyfills['Map']();");
    test("var m = new goog.global.Map();", "var m = new $jscomp.polyfills['Map']();");
  }

  @Test
  public void testClassesAreNotIsolatedUnlessPolyfillInjected() {
    // Model a case where there is no actual $jscomp.polyfill('Map' call.
    // (Possibly because RemoveUnusedCode deleted it.)
    // Replacing "new Map()" with "new $jscomp.polyfills['Map']()" would result in an undefined
    // reference at runtime, so just leave "new Map()" as is.

    addLibrary("Map", "es6", "es5", "es6/map");
    polyfillsToInject.remove("Map");

    setLanguage(ES6, ES5);
    testSame("var m = new Map();");
    testSame("var m = new window.Map();");
    testSame("var m = new goog.global.Map();");
  }

  @Test
  public void testClassesGuardedByIfAreIsolated() {
    addLibrary("Map", "es6", "es5", "es6/map");

    setLanguage(ES6, ES5);
    test(
        "if (Map) { var m = new Map(); }",
        "if ($jscomp.polyfills['Map']) { var m = new $jscomp.polyfills['Map'](); }");
  }

  @Test
  public void testClassesAccessedViaBrackets_notIsolated() {
    addLibrary("Map", "es6", "es5", "es6/map");

    // We could support literal accesses like these but are currently choosing not to.
    setLanguage(ES6, ES5);
    testSame("var m = new window['Map']();");
    testSame("var m = new goog.global['Map']();");
  }

  @Test
  public void testGlobalThisIsolated() {
    addLibrary("globalThis", "es_2020", "es3", "es6/globalthis");
    addLibrary("Map", "es6", "es5", "es6/map");

    setLanguage(ES_2020, ES5);
    test("alert(globalThis);", "alert($jscomp.polyfills['globalThis']);");

    setLanguage(ES_2020, ES_2020);
    testSame("var m = new globalThis.Map();");
  }

  @Test
  public void testClassOnGlobalThisIsolated() {
    addLibrary("globalThis", "es_2020", "es3", "es6/globalthis");
    addLibrary("Map", "es6", "es5", "es6/map");

    setLanguage(ES_2020, ES5);
    test("var m = new globalThis.Map();", "var m = new $jscomp.polyfills['Map']();");

    setLanguage(ES_2020, ES6);
    test("var m = new globalThis.Map();", "var m = new $jscomp.polyfills['globalThis'].Map();");
  }

  @Test
  public void testEnablingPropertyFlatteningUsesFlattenedJscompPolyfills() {
    addLibrary("Map", "es6", "es5", "es6/map");

    enablePropertyFlattening = true;
    setLanguage(ES6, ES5);
    test("var m = new Map();", "var m = new $jscomp$polyfills['Map']();");
    test("var m = new window.Map();", "var m = new $jscomp$polyfills['Map']();");
    test("var m = new goog.global.Map();", "var m = new $jscomp$polyfills['Map']();");
  }

  @Test
  public void testMultipleUsagesOfClassAllIsolated() {
    addLibrary("Map", "es6", "es5", "es6/map");

    setLanguage(ES6, ES5);
    test(
        "var m = new Map(); m = new Map();",
        "var m = new $jscomp.polyfills['Map'](); m = new $jscomp.polyfills['Map']();");
  }

  @Test
  public void testClassesGivenSufficientLanguageOutNotIsolated() {
    addLibrary("Proxy", "es6", "es6", null);
    addLibrary("Map", "es6", "es5", "es6/map");
    addLibrary("Set", "es6", "es3", "es6/set");

    setLanguage(ES6, ES6);
    testSame("new Proxy();");
    testSame("var m = new Map();");
    testSame("new Set();");
  }

  @Test
  public void testClassesDeclaredInScopeNotIsolated() {
    addLibrary("Map", "es6", "es5", "es6/map");

    setLanguage(ES6, ES5);
    testSame("/** @constructor */ var Map = function() {}; new Map();");
  }

  @Test
  public void testStaticMethodsIsolated() {
    addLibrary("Array.of", "es6", "es3", "es6/array/of");
    addLibrary("Object.keys", "es5", "es3", "es5/object/keys");
    addLibrary("Math.clz32", "es6", "es5", "es6/math/clz32");

    // NOTE: Storing an entry for `Array.of` in the $jscomp.polyfills dictionary instead of using
    // `$jscomp$lookupPolyfilledValue` is a potential optimization, but not necessary for
    // correctness.
    setLanguage(ES6, ES5);
    test("Array.of(x);", "$jscomp$lookupPolyfilledValue(Array, 'of').call(Array, x);");
    test("Math.clz32(x);", "$jscomp$lookupPolyfilledValue(Math, 'clz32').call(Math, x);");

    setLanguage(ES6, ES3);
    test("Object.keys(x);", "$jscomp$lookupPolyfilledValue(Object, 'keys').call(Object, x);");
  }

  @Test
  public void testStaticMethodsWithSufficientLanguageOutNotIsolated() {
    addLibrary("Array.from", "es6", "es6", null);
    addLibrary("Math.clz32", "es6", "es5", "es6/math/clz32");
    addLibrary("Array.of", "es6", "es3", "es6/array/of");
    addLibrary("Object.keys", "es5", "es3", "es5/object/keys");

    setLanguage(ES6, ES6);
    testSame("Array.from(x);");
    testSame("Math.clz32(x);");
    testSame("Array.of(x);");

    setLanguage(ES5, ES5);
    testSame("Object.keys(x);");
  }

  @Test
  public void testStaticMethodsDeclaredInScopeNotIsolated() {
    addLibrary("Math.clz32", "es6", "es5", "es6/math/clz32");

    setLanguage(ES6, ES5);
    testSame("var Math = {clz32: function() {}}; Math.clz32(x);");
  }

  @Test
  public void testStaticMethodsGuardedByIfStillIsolated() {
    addLibrary("Array.of", "es6", "es5", "es6/array/of");

    test(
        "if (Array.of) { Array.of(); } else { Array.of(); }",
        lines(
            "if ($jscomp$lookupPolyfilledValue(Array, 'of')) {",
            "$jscomp$lookupPolyfilledValue(Array, 'of').call(Array);",
            "} else {",
            "  $jscomp$lookupPolyfilledValue(Array, 'of').call(Array);",
            "}"));
  }

  @Test
  public void testStaticMethodIsolatedOnPolyfilledNameIsolated() {
    addLibrary("Promise", "es6", "es3", "es6/promise/promise");
    addLibrary("Promise.allSettled", "es_2020", "es3", "es6/promise/allSettled");

    setLanguage(ES_2020, ES3);
    test(
        "Promise.allSettled([p1, p2]);",
        lines(
            "$jscomp$lookupPolyfilledValue($jscomp.polyfills['Promise'], 'allSettled')",
            "   .call($jscomp.polyfills['Promise'], [p1, p2]);"));

    setLanguage(ES_2020, ES6);
    test(
        "Promise.allSettled([p1, p2]);",
        "$jscomp$lookupPolyfilledValue(Promise, 'allSettled').call(Promise, [p1, p2]);");
  }

  @Test
  public void testSinglePrototypeMethodIsIsolated() {
    addLibrary("String.prototype.endsWith", "es6", "es5", "es6/string/endswith");

    setLanguage(ES6, ES5);
    test("x.endsWith(y);", "$jscomp$lookupPolyfilledValue(x, 'endsWith').call(x, y);");

    test(
        "x().endsWith(y);",
        lines(
            "var $jscomp$polyfillTmp;",
            "($jscomp$polyfillTmp = x(),",
            "  $jscomp$lookupPolyfilledValue($jscomp$polyfillTmp, 'endsWith'))",
            "      .call($jscomp$polyfillTmp, y);"));
  }

  @Test
  public void testMultiplePrototypeMethodsAllIsolated() {
    addLibrary("String.prototype.startsWith", "es6", "es5", "es6/string/startswith");
    addLibrary("String.prototype.endsWith", "es6", "es5", "es6/string/endswith");

    setLanguage(ES6, ES5);
    test(
        "x.endsWith(y) || x.startsWith(y);",
        lines(
            "$jscomp$lookupPolyfilledValue(x, 'endsWith').call(x, y)",
            "  || $jscomp$lookupPolyfilledValue(x, 'startsWith').call(x, y);"));

    test(
        "x().endsWith(y) || x().startsWith(y);",
        lines(
            "var $jscomp$polyfillTmp;",
            "($jscomp$polyfillTmp = x(), ",
            "    $jscomp$lookupPolyfilledValue($jscomp$polyfillTmp, 'endsWith'))",
            "  .call($jscomp$polyfillTmp, y)",
            " || ($jscomp$polyfillTmp = x(),",
            "      $jscomp$lookupPolyfilledValue($jscomp$polyfillTmp, 'startsWith'))",
            "    .call($jscomp$polyfillTmp, y);"));
  }

  @Test
  public void testMethodMatchingMultiplePolyfillsIsolatedOnlyOnce() {
    addLibrary("String.prototype.includes", "es6", "es5", "es6/string/includes");
    addLibrary("Array.prototype.includes", "es6", "es5", "es6/array/includes");

    setLanguage(ES6, ES5);
    test("x.includes(y);", "$jscomp$lookupPolyfilledValue(x, 'includes').call(x, y);");
  }

  @Test
  public void testMethodsNotIsolatedUnlessPolyfillInjected() {
    addLibrary("String.prototype.includes", "es6", "es5", "es6/string/includes");
    addLibrary("String.prototype.endsWith", "es6", "es5", "es6/string/endswith");
    polyfillsToInject.remove("String.prototype.endsWith");

    setLanguage(ES6, ES5);
    testSame("x.endsWith(y);");
    test(
        "x.includes(y) && x.endsWith(z);",
        "$jscomp$lookupPolyfilledValue(x, 'includes').call(x, y) && x.endsWith(z);");
  }

  @Test
  public void testLvaluePolyfillUsageNotIsolated() {
    addLibrary("String.prototype.includes", "es6", "es5", "es6/string/includes");

    setLanguage(ES6, ES5);

    testSame("x.includes = true;");
  }

  @Test
  public void testLvaluePolyfillUsage_doesntCauseBackoffForOtherUsages() {
    addLibrary("String.prototype.includes", "es6", "es5", "es6/string/includes");

    setLanguage(ES6, ES5);

    test(
        "x.includes = true; y.includes(z)",
        "x.includes = true; $jscomp$lookupPolyfilledValue(y, 'includes').call(y, z)");
  }

  @Test
  public void testLvaluePolyfillUsageInNestedAssignNotIsolated() {
    addLibrary("String.prototype.includes", "es6", "es5", "es6/string/includes");

    setLanguage(ES6, ES5);

    testSame("y = x.includes = true");
    testSame("y = x.includes = z.includes = true");
  }

  @Test
  public void testPolyfilledMethodOnPolyfilledClassAreBothIsolated() {
    addLibrary("Promise", "es6", "es3", "es6/promise/promise");
    addLibrary("Promise.prototype.finally", "es9", "es3", "es6/promise/finally");

    setLanguage(ES_2020, ES3);

    test("p.finally(cb);", "$jscomp$lookupPolyfilledValue(p, 'finally').call(p, cb);");

    setLanguage(ES_2020, ES6);
    test("p.finally(cb);", "$jscomp$lookupPolyfilledValue(p, 'finally').call(p, cb);");
  }

  @Test
  public void testNonPolyfilledMethodOnPolyfilledClassNotIsolated() {
    addLibrary("Promise", "es6", "es3", "es6/promise/promise");

    setLanguage(ES_2020, ES3);
    test("Promise.all(p1, p2);", "$jscomp.polyfills['Promise'].all(p1, p2);");
  }

  @Test
  public void testExternForJSCompLookupPolyfillDeleted() {
    disableCompareAsTree();
    testExternChanges(
        // The getProcessor() method injects $jscomp$lookupPolyfilledValue so just add an empty
        // extern here.
        /* extern= */ "", /* input= */ "x.includes(y);", /* expectedExtern= */ "");
  }

  @Test
  public void testJscompLookupPolyfillDeletedIfNotUsed() {
    addLibrary("Promise", "es6", "es3", "es6/promise/promise");
    addLibrary("Promise.prototype.finally", "es9", "es3", "es6/promise/promise");

    setLanguage(ES_2020, ES3);
    test("var $jscomp$lookupPolyfilledValue = function() {};", "");
    test(
        lines(
            "var $jscomp$lookupPolyfilledValue = function() {};", //
            "new Promise();"),
        "new $jscomp.polyfills['Promise']();");
  }

  @Test
  public void testJscompLookupPolyfillKeptIfUsed() {
    addLibrary("Promise", "es6", "es3", "es6/promise/promise");
    addLibrary("Promise.prototype.finally", "es9", "es3", "es6/promise/promise");

    setLanguage(ES_2020, ES3);
    test(
        lines(
            "var $jscomp$lookupPolyfilledValue = function() {};", //
            "new Promise().finally(cb);"),
        lines(
            "var $jscomp$polyfillTmp;",
            "var $jscomp$lookupPolyfilledValue = function() {};",
            "($jscomp$polyfillTmp = new $jscomp.polyfills['Promise'],",
            "    $jscomp$lookupPolyfilledValue($jscomp$polyfillTmp, 'finally'))",
            "  .call($jscomp$polyfillTmp, cb);"));
  }
}
