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
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class J2clPropertyInlinerPassTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableNormalize(); // Inlining will fail if normalization hasn't happened yet.
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new J2clPropertyInlinerPass(compiler);
  }

  @Override
  protected Compiler createCompiler() {
    Compiler compiler = super.createCompiler();
    J2clSourceFileChecker.markToRunJ2clPasses(compiler);
    return compiler;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  private void testDoesntChange(List<SourceFile> js) {
    test(js, js);
  }

  @Test
  public void testNoInlineNonJ2clProps() {
    testDoesntChange(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                lines(
                    "var A = function() {};",
                    "var A$$0clinit = function() {",
                    "  A$$0x = 2;",
                    "};",
                    "Object.defineProperties(A, {x :{",
                    "  configurable:true,",
                    "  enumerable:true,",
                    "  get:function() {",
                    "    return A$$0clinit(), A$$0x;",
                    "  }",
                    "}});",
                    "var A$$0x = null;",
                    "var x = A.x;"))));
  }

  @Test
  public void testNoInlineNonJ2clPropsValue() {
    testDoesntChange(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                lines(
                    "var A = function() {};",
                    "var A$$0clinit = function() {",
                    "  A$$0x = 2;",
                    "};",
                    "Object.defineProperties(A, {x :{",
                    "  configurable:true,",
                    "  enumerable:true,",
                    "  value: 2",
                    "}});",
                    "var A$$0x = null;",
                    "var x = A.x;"))));
  }

  // In this test we want to remove the J2CL property but not the entire Object.defineProperties
  // since it also defines another non J2CL property.
  @Test
  public void testNoStripDefineProperties() {
    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                lines(
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
                lines(
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

  @Test
  public void testInlineDefinePropertiesGetter() {
    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                lines(
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
                lines(
                    "var A = function() {};",
                    "A.$clinit = function() {",
                    "  A.$x = 2;",
                    "};",
                    "A.$x = 3;",
                    "var xx = (A.$clinit(), A.$x);"))));
  }

  @Test
  public void testInlineDefinePropertiesSetter() {
    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                lines(
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
                lines(
                    "var A = function() {};",
                    "A.$clinit = function() {",
                    "  A.$x = 2;",
                    "};",
                    "A.$x = 3;",
                    "{(A.$clinit(), A.$x = 10);}"))));
  }

  @Test
  public void testInlineGettersInQualifier() {
    test(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                lines(
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
                lines(
                    "var A = function() {};",
                    "A.$clinit = function() {",
                    "  A.$x = {y: 2};",
                    "};",
                    "A.$x = null;",
                    "var xy = (A.$clinit(), A.$x).y;"))));
  }

  @Test
  public void testNoInlineCompoundAssignment() {
    testDoesntChange(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                lines(
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

  @Test
  public void testNoInlineIncrementGetter() {
    // Test ++
    testDoesntChange(
        Lists.newArrayList(
            SourceFile.fromCode(
                "someFile.js",
                lines(
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
                lines(
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

  @Test
  public void testInlineEs6Getter() {
    test(
        lines(
            "class A {",
            "  static $clinit() {",
            "    A.$clinit = function() {};",
            "    A.$x = 2;",
            "  }",
            "  static get x() {",
            "    return A.$clinit(), A.$x",
            "  }",
            "  static set x(value) {",
            "    A.$clinit(), A.$x = value;",
            "  }",
            "}",
            "A.$x = 3;",
            "var xx = A.x"),
        lines(
            "class A {",
            "  static $clinit() {",
            "    A.$clinit = function() {};",
            "    A.$x = 2;",
            "  }",
            "}",
            "A.$x = 3;",
            "var xx = (A.$clinit(), A.$x);"));
  }

  @Test
  public void testInlineEs6Setter() {
    test(
        lines(
            "class A {",
            "  static $clinit() {",
            "    A.$clinit = function() {};",
            "    A.$x = 2;",
            "  }",
            "  static get x() {",
            "    return A.$clinit(), A.$x",
            "  }",
            "  static set x(value) {",
            "    A.$clinit(), A.$x = value;",
            "  }",
            "}",
            "A.$x = 3;",
            "A.x = 5;"),
        lines(
            "class A {",
            "  static $clinit() {",
            "    A.$clinit = function() {};",
            "    A.$x = 2;",
            "  }",
            "}",
            "A.$x = 3;",
            "{(A.$clinit(), A.$x = 5)}"));
  }

  @Test
  public void testInlineEs6GetterSetter_multiple() {
    test(
        lines(
            "class A {",
            "  static $clinit() {",
            "    A.$clinit = function() {};",
            "    A.$x = 2;",
            "    A.$y = 3;",
            "  }",
            "  static get x() {",
            "    return A.$clinit(), A.$x",
            "  }",
            "  static set y(value) {",
            "    A.$clinit(), A.$y = value;",
            "  }",
            "  static get y() {",
            "    return A.$clinit(), A.$y",
            "  }",
            "  static set x(value) {",
            "    A.$clinit(), A.$x = value;",
            "  }",
            "}",
            "var xx = A.x;",
            "var yy = A.y",
            "A.x = 5;",
            "A.y = 5;"),
        lines(
            "class A {",
            "  static $clinit() {",
            "    A.$clinit = function() {};",
            "    A.$x = 2;",
            "    A.$y = 3;",
            "  }",
            "}",
            "var xx = (A.$clinit(), A.$x);",
            "var yy = (A.$clinit(), A.$y)",
            "{(A.$clinit(), A.$x = 5)}",
            "{(A.$clinit(), A.$y = 5)}"));
  }
}
