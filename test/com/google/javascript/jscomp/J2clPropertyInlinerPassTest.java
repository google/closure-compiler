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

import java.util.List;

public class J2clPropertyInlinerPassTest extends CompilerTestCase {

  public J2clPropertyInlinerPassTest() {
    this.enableNormalize(); // Inlining will fail if normailization hasn't happened yet.
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

  public void testNoInlineNonJ2clProps() {
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
  
  public void testNoInlineNonJ2clPropsValue() {
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
                    + "  value: 2"
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
                LINE_JOINER.join(
                    "var A = function() {};",
                    "A.$clinit = function() {",
                    "  A.$j2cl_prop = 2;",
                    "};",
                    "Object.defineProperties(A, {",
                    "  j2cl_prop: {",
                    "    configurable: true,",
                    "    enumerable: true,",
                    "    get: function() {",
                    "      return A.$clinit(), A.$j2cl_prop;",
                    "    },",
                    "    set: function(value) {",
                    "      A.$clinit(), A.$j2cl_prop = value;",
                    "    }",
                    "  },",
                    "  non_j2cl_prop: {",
                    "    configurable: true,",
                    "    enumerable: true,",
                    "    get: function() {",
                    "      return 55;",
                    "    },",
                    "    set: function(v) {",
                    "      console.log(v);",
                    "    }",
                    "  },",
                    "});"))),
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                LINE_JOINER.join(
                    "var A = function() {};",
                    "A.$clinit = function() {",
                    "  A.$j2cl_prop = 2;",
                    "};",
                    "Object.defineProperties(A, {",
                    "  non_j2cl_prop: {",
                    "    configurable: true,",
                    "    enumerable: true,",
                    "    get: function() {",
                    "      return 55;",
                    "    },",
                    "    set: function(v) {",
                    "      console.log(v);",
                    "    }",
                    "  },",
                    "});"))));
  }

  public void testInlineDefinePropertiesGetter() {
    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                LINE_JOINER.join(
                    "var A = function() {};",
                    "A.$clinit = function() {",
                    "  A.$x = 2;",
                    "};",
                    "Object.defineProperties(A, {x: {",
                    "  configurable:true,",
                    "  enumerable:true,",
                    "  get:function() {",
                    "    return A.$clinit(), A.$x;",
                    "  },",
                    "  set: function(value) {",
                    "    A.$clinit(), A.$x = value;",
                    "  }",
                    "}});",
                    "A.$x = 3;",
                    "var xx = A.x;"))),
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                LINE_JOINER.join(
                    "var A = function() {};",
                    "A.$clinit = function() {",
                    "  A.$x = 2;",
                    "};",
                    "A.$x = 3;",
                    "var xx = (A.$clinit(), A.$x);"))));
  }

  public void testInlineDefinePropertiesSetter() {
    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                LINE_JOINER.join(
                    "var A = function() {};",
                    "A.$clinit = function() {",
                    "  A.$x = 2;",
                    "};",
                    "Object.defineProperties(A, {x: {",
                    "  configurable:true,",
                    "  enumerable:true,",
                    "  get:function() {",
                    "    return A.$clinit(), A.$x;",
                    "  },",
                    "  set: function(value) {",
                    "    A.$clinit(), A.$x = value;",
                    "  }",
                    "}});",
                    "A.$x = 3;",
                    "A.x = 10;"))),
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                LINE_JOINER.join(
                    "var A = function() {};",
                    "A.$clinit = function() {",
                    "  A.$x = 2;",
                    "};",
                    "A.$x = 3;",
                    "{(A.$clinit(), A.$x = 10);}"))));
  }

  public void testInlineGettersInQualifier() {
    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                LINE_JOINER.join(
                    "var A = function() {};",
                    "A.$clinit = function() {",
                    "  A.$x = {y: 2};",
                    "};",
                    "Object.defineProperties(A, {x: {",
                    "  configurable:true,",
                    "  enumerable:true,",
                    "  get:function() {",
                    "    return A.$clinit(), A.$x;",
                    "  },",
                    "  set: function(value) {",
                    "    A.$clinit(), A.$x = value;",
                    "  }",
                    "}});",
                    "A.$x = null;",
                    "var xy = A.x.y;"))),
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                LINE_JOINER.join(
                    "var A = function() {};",
                    "A.$clinit = function() {",
                    "  A.$x = {y: 2};",
                    "};",
                    "A.$x = null;",
                    "var xy = (A.$clinit(), A.$x).y;"))));
  }


  public void testNoInlineCompoundAssignment() {
    testDoesntChange(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                LINE_JOINER.join(
                    "var A = function() {};",
                    "A.$clinit = function() {",
                    "  A.$x = 2;",
                    "};",
                    "Object.defineProperties(A, {x: {",
                    "  configurable:true,",
                    "  enumerable:true,",
                    "  get:function() {",
                    "    return A.$clinit(), A.$x;",
                    "  },",
                    "  set: function(value) {",
                    "    A.$clinit(), A.$x = value;",
                    "  }",
                    "}});",
                    "A.$x = 3;",
                    "A.x += 5;"))));
  }

  public void testNoInlineIncrementGetter() {
    // Test ++
    testDoesntChange(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                LINE_JOINER.join(
                    "var A = function() {};",
                    "A.$clinit = function() {",
                    "  A.$x = 2;",
                    "};",
                    "Object.defineProperties(A, {x: {",
                    "  configurable:true,",
                    "  enumerable:true,",
                    "  get:function() {",
                    "    return A.$clinit(), A.$x;",
                    "  },",
                    "  set: function(value) {",
                    "    A.$clinit(), A.$x = value;",
                    "  }",
                    "}});",
                    "A.$x = 3;",
                    "A.x--;"))));

    // Test --
    testDoesntChange(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                LINE_JOINER.join(
                    "var A = function() {};",
                    "A.$clinit = function() {",
                    "  A.$x = 2;",
                    "};",
                    "Object.defineProperties(A, {x: {",
                    "  configurable:true,",
                    "  enumerable:true,",
                    "  get:function() {",
                    "    return A.$clinit(), A.$x;",
                    "  },",
                    "  set: function(value) {",
                    "    A.$clinit(), A.$x = value;",
                    "  }",
                    "}});",
                    "A.$x = 3;",
                    "A.x++;"))));
  }
}
