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

import com.google.common.collect.Lists;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.J2clPropertyInlinerPass.StaticFieldGetterSetterInliner;

import java.util.List;

public class J2clPropertyInlinerPassTest extends CompilerTestCase {

  public J2clPropertyInlinerPassTest() {
    this.enableNormalize(); // Inlining will fail if normailization hasnt happened yet.
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6_TYPED);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new J2clPropertyInlinerPass(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  private void testDoesntChange(List<SourceFile> js) {
    test(js, js);
  }

  public void testMatchesJ2clPropertyNameMatcher() {
    StaticFieldGetterSetterInliner matcher =
        new J2clPropertyInlinerPass(null).new StaticFieldGetterSetterInliner(null);

    // J2cl Properties
    assertTrue(matcher.matchesJ2clStaticFieldName("f_fieldName__some_java_package"));
    assertTrue(matcher.matchesJ2clStaticFieldName("f_name$asdf__some_java_package"));
    assertTrue(matcher.matchesJ2clStaticFieldName("f_name$asdf__package"));
    assertTrue(matcher.matchesJ2clStaticFieldName("f_name$asdf__package_outer_outer$inner"));
    assertTrue(matcher.matchesJ2clStaticFieldName("f_x__com_google_j2cl_transpiler"));

    // Non J2cl Properties
    assertFalse(matcher.matchesJ2clStaticFieldName("name"));
    assertFalse(matcher.matchesJ2clStaticFieldName("_name__"));
    assertFalse(matcher.matchesJ2clStaticFieldName("f_fieldName_some_java_package"));
    assertFalse(matcher.matchesJ2clStaticFieldName("f_name__"));
    assertFalse(matcher.matchesJ2clStaticFieldName("name__some_java_package"));
    assertFalse(matcher.matchesJ2clStaticFieldName("m_name__some_java_package"));
    assertFalse(matcher.matchesJ2clStaticFieldName("f___com_google_j2cl_transpiler"));
    assertFalse(matcher.matchesJ2clStaticFieldName("f_x__com_google_j2cl_transpiler__"));
  }

  public void testNoInlineNonJ2clNamedProps() {
    testDoesntChange(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                "var A = function() {};"
                    + "var A$$0clinit = function() {"
                    + "  A$$0x = 2;"
                    + "};"
                    + "Object.defineProperties(A, {x :{"
                    + "  configurable:true,"
                    + "  enumerable:true,"
                    + "  get:function() {"
                    + "    return A$$0clinit(), A$$0x;"
                    + "  }"
                    + "}});"
                    + "var A$$0x = null;"
                    + "var x = A.x;")));
  }

  // In this test we want to remove the j2cl property but not the entire Object.defineProperties
  // since it also defines another non j2cl property.
  public void testNoStripDefineProperties() {
    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                "var A = function() {};"
                    + "Object.defineProperties(A, {"
                    + "  f_j2clProp__com_google_j2cl_transpiler: {"
                    + "    get:function() {"
                    + "      return 2;"
                    + "    },"
                    + "    set: function(a) {}"
                    + "  }, nonj2clprop: {"
                    + "    get:function() {"
                    + "      return 2;"
                    + "    }"
                    + "  }"
                    + "});")),
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                "var A = function() {};"
                    + "Object.defineProperties(A, {"
                    + "  nonj2clprop: {"
                    + "    get:function() {"
                    + "      return 2;"
                    + "    }"
                    + "  }"
                    + "});")));

    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                "var A = function() {};"
                    + "Object.defineProperties(A, {"
                    + "  f_j2clProp__com_google_j2cl_transpiler: {"
                    + "    get:function() {"
                    + "      return 2;"
                    + "    },"
                    + "    set: function(a) {}"
                    + "  }, nonj2clprop: {"
                    + "    value:function() {"
                    + "      return 2;"
                    + "    }"
                    + "  }"
                    + "});")),
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                "var A = function() {};"
                    + "Object.defineProperties(A, {"
                    + "  nonj2clprop: {"
                    + "    value:function() {"
                    + "      return 2;"
                    + "    }"
                    + "  }"
                    + "});")));
  }

  public void testInlineDefinePropertiesGetter() {
    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                "var A = function() {};"
                    + "var A$$0clinit = function() {"
                    + "  A$$0x = 2;"
                    + "};"
                    + "Object.defineProperties(A, {f_x__com_google_j2cl_transpiler:{"
                    + "  configurable:true,"
                    + "  enumerable:true,"
                    + "  get:function() {"
                    + "    return A$$0clinit(), A$$0x;"
                    + "  },"
                    + "  set:function(a) {"
                    + "    A$$0clinit(), A$$0x = a;"
                    + "  }"
                    + "}});"
                    + "var A$$0x = null;"
                    + "var x = A.f_x__com_google_j2cl_transpiler;")),
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                "var A = function() {};"
                    + "var A$$0clinit = function() {"
                    + "  A$$0x = 2;"
                    + "};"
                    + "var A$$0x = null;"
                    + "var x = (A$$0clinit(), A$$0x);")));
  }

  public void testInlineDefinePropertiesSetter() {
    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                "var A = function() {};"
                    + "var A$$0clinit = function() {"
                    + "  A$$0x = 2;"
                    + "};"
                    + "Object.defineProperties(A, {f_x__com_google_j2cl_transpiler:{"
                    + "  configurable:true,"
                    + "  enumerable:true,"
                    + "  get:function() {"
                    + "    return A$$0clinit(), A$$0x;"
                    + "  },"
                    + "  set:function(a) {"
                    + "    A$$0clinit(), A$$0x = a;"
                    + "  }"
                    + "}});"
                    + "var A$$0x = null;"
                    + "A.f_x__com_google_j2cl_transpiler = 10;")),
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                "var A = function() {};"
                    + "var A$$0clinit = function() {"
                    + "  A$$0x = 2;"
                    + "};"
                    + "var A$$0x = null;"
                    + "{(A$$0clinit(), A$$0x = 10);}")));
  }

  public void testInlineGettersInQualifier() {
    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                "var A = function() {};"
                    + "var A$$0clinit = function() {"
                    + "  A$$0x = {y: 2};"
                    + "};"
                    + "Object.defineProperties(A, {f_x__com_google_j2cl_transpiler:{"
                    + "  configurable:true,"
                    + "  enumerable:true,"
                    + "  get:function() {"
                    + "    return A$$0clinit(), A$$0x;"
                    + "  },"
                    + "  set:function(a) {"
                    + "    A$$0clinit(), A$$0x = a;"
                    + "  }"
                    + "}});"
                    + "var A$$0x = null;"
                    + "var y = A.f_x__com_google_j2cl_transpiler.y;"
                    + "var x = A.f_x__com_google_j2cl_transpiler;")),
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                "var A = function() {};"
                    + "var A$$0clinit = function() {"
                    + "  A$$0x = {y: 2};"
                    + "};"
                    + "var A$$0x = null;"
                    + "var y = (A$$0clinit(), A$$0x).y;"
                    + "var x = (A$$0clinit(), A$$0x);")));
  }

  public void testNoInlineCompoundAssignment() {
    testDoesntChange(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                "var A = function() {};"
                    + "var A$$0clinit = function() {"
                    + "  A$$0x = 'hello';"
                    + "};"
                    + "Object.defineProperties(A, {f_x__com_google_j2cl_transpiler:{"
                    + "  configurable:true,"
                    + "  enumerable:true,"
                    + "  get:function() {"
                    + "    return A$$0clinit(), A$$0x;"
                    + "  },"
                    + "  set:function(a) {"
                    + "    A$$0clinit(), A$$0x = a;"
                    + "  }"
                    + "}});"
                    + "var A$$0x = null;"
                    + "A.f_x__com_google_j2cl_transpiler += ' j2cl';")));
  }

  public void testNoInlineIncrementGetter() {
    // Test ++
    testDoesntChange(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                "var A = function() {};"
                    + "var A$$0clinit = function() {"
                    + "  A$$0x = 'hello';"
                    + "};"
                    + "Object.defineProperties(A, {f_x__com_google_j2cl_transpiler:{"
                    + "  configurable:true,"
                    + "  enumerable:true,"
                    + "  get:function() {"
                    + "    return A$$0clinit(), A$$0x;"
                    + "  },"
                    + "  set:function(a) {"
                    + "    A$$0clinit(), A$$0x = a;"
                    + "  }"
                    + "}});"
                    + "var A$$0x = null;"
                    + "A.f_x__com_google_j2cl_transpiler++;")));

    // Test --
    testDoesntChange(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                "var A = function() {};"
                    + "var A$$0clinit = function() {"
                    + "  A$$0x = 'hello';"
                    + "};"
                    + "Object.defineProperties(A, {f_x__com_google_j2cl_transpiler:{"
                    + "  configurable:true,"
                    + "  enumerable:true,"
                    + "  get:function() {"
                    + "    return A$$0clinit(), A$$0x;"
                    + "  },"
                    + "  set:function(a) {"
                    + "    A$$0clinit(), A$$0x = a;"
                    + "  }"
                    + "}});"
                    + "var A$$0x = null;"
                    + "A.f_x__com_google_j2cl_transpiler--;")));
  }
}
