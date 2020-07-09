/*
 * Copyright 2011 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.ClosureOptimizePrimitives.DUPLICATE_SET_MEMBER;
import static com.google.javascript.jscomp.parsing.parser.testing.FeatureSetSubject.assertFS;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link ClosureOptimizePrimitives}.
 *
 * @author agrieve@google.com (Andrew Grieve)
 */
@RunWith(JUnit4.class)
public final class ClosureOptimizePrimitivesTest extends CompilerTestCase {

  private boolean propertyRenamingEnabled = true;
  private boolean canUseEs6Syntax = true;

  @Override protected CompilerPass getProcessor(final Compiler compiler) {
    return new ClosureOptimizePrimitives(compiler, propertyRenamingEnabled, canUseEs6Syntax);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
    disableScriptFeatureValidation();
  }

  @Test
  public void testObjectCreateOddParams() {
    testSame("goog.object.create('a',1,2);");
  }

  @Test
  public void testObjectCreate1() {
    test("var a = goog.object.create()", "var a = {}");
  }

  @Test
  public void testObjectCreate2() {
    test("var a = goog$object$create('b',goog$object$create('c','d'))",
         "var a = {'b':{'c':'d'}};");
  }

  @Test
  public void testObjectCreate3() {
    test("var a = goog.object.create(1,2)", "var a = {1:2}");
  }

  @Test
  public void testObjectCreate4() {
    test("alert(goog.object.create(1,2).toString())",
         "alert({1:2}.toString())");
  }

  @Test
  public void testObjectCreate5() {
    test("goog.object.create('a',2).toString()", "({'a':2}).toString()");
  }

  @Test
  public void testObjectCreateNonConstKey1() {
    test("var a = goog.object.create('a', 1, 2, 3, foo, bar);",
         "var a = {'a': 1, 2: 3, [foo]: bar};");

    assertFS(getLastCompiler().getFeatureSet()).has(Feature.COMPUTED_PROPERTIES);
  }

  @Test
  public void testObjectCreateNonConstKey2() {
    test("var a = goog.object.create('a' + 'b', 0);", "var a = {['a' + 'b']: 0};");
  }

  @Test
  public void testObjectCreateNonConstKey3() {
    test("var a = goog$object$create(i++,goog$object$create(foo(), 'd'))",
         "var a = {[i++]: {[foo()]: 'd'}};");
  }

  @Test
  public void testObjectCreateNonConstKey4() {
    test("alert(goog.object.create(a = 1, 2).toString())",
        "alert({[a = 1]: 2}.toString())");
  }

  @Test
  public void testObjectCreateNonConstKey5() {
    test("goog.object.create(function foo() {}, 2).toString()",
        "({[function foo() {}]: 2}).toString()");
  }

  @Test
  public void testObjectCreateNonConstKeyNotEs6() {
    canUseEs6Syntax = false;
    testSame("var a = goog.object.create(foo, bar)");
  }

  @Test
  public void testObjectCreateSetZeroArg() {
    test("var a = goog.object.createSet()", "var a = {}");
  }

  @Test
  public void testObjectCreateSetTwoNumericalArgs() {
    test("var a = goog.object.createSet(1,2)", "var a = {1:true,2:true}");
  }

  @Test
  public void testObjectCreateSetOneNumericalArg() {
    test("alert(goog.object.createSet(1).toString())",
         "alert({1:true}.toString())");
  }

  @Test
  public void testObjectCreateSetOneStringArg() {
    test("goog.object.createSet('a').toString()", "({'a':true}).toString()");
  }

  @Test
  public void testObjectCreateSet_duplicate() {
    testWarning("goog.object.createSet('a', 'a')", DUPLICATE_SET_MEMBER);
    testWarning("goog.object.createSet(4, 4)", DUPLICATE_SET_MEMBER);
    testWarning("goog.object.createSet(4, '4')", DUPLICATE_SET_MEMBER);
  }

  @Test
  public void testObjectCreateSetNonConstKey1() {
    test("var a = goog.object.createSet(foo, bar);",
         "var a = {[foo]: true, [bar]: true};");
  }

  @Test
  public void testObjectCreateSetNonConstKey2() {
    test("alert(goog$object$createSet(a = 1, 2).toString())",
        "alert({[a = 1]: true, 2: true}.toString())");
  }

  @Test
  public void testObjectCreateSetNonConstSingleKey() {
    // this triggers the 'first argument may be an array' case and we back off
    testSame("goog.object.createSet(() => {}).toString()");
  }

  @Test
  public void testObjectCreateSetNonConstKeyNotEs6() {
    canUseEs6Syntax = false;
    testSame("var a = goog.object.createSet(foo, bar);");
  }

  @Test
  public void testObjectCreateSetSingleNonLiteralArg() {
    testSame("const arr = [1, 2, 3]; const s = goog.object.createSet(arr);");
    testSame("const num = 1; const s = goog.object.createSet(num);");
    testSame("const s = goog.object.createSet(undefined || [1, 2, 3]);");
  }

  @Test
  public void testObjectCreateSetSingleLiteralArg() {
    test("const s = goog.object.createSet(3);", "const s = {3: true};");
    test("const s = goog.object.createSet('a');", "const s = {'a': true};");
  }

  @Test
  public void testPropertyReflectionSimple() {
    propertyRenamingEnabled = false;
    test("goog.reflect.objectProperty('push', [])", "'push'");
    test("JSCompiler_renameProperty('push', [])", "'push'");
  }

  @Test
  public void testPropertyReflectionAdvanced() {
    test("goog.reflect.objectProperty('push', [])", "JSCompiler_renameProperty('push', [])");
    testSame("JSCompiler_renameProperty('push', [])");
  }

  @Test
  public void testEs6ArrowCompatibility() {
    // Arrow
    test("var f = () => goog.object.create(1, 2);", "var f = () => ({1: 2});");
  }

  @Test
  public void testEs6ClassCompatibility() {
    test(
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = goog.object.create(1, 2);",
            "  }",
            "}"),
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = {1: 2};",
            "  }",
            "}"));
  }

  @Test
  public void testEs6MemberFunctionDefCompatibility() {
    test(
        lines(
            "var obj = {",
            "  method() {",
            "    return goog.object.create('a', 2);",
            "  }",
            "}"),
        lines(
            "var obj = {",
            "  method() {",
            "    return {'a': 2};",
            "  }",
            "}"));
  }

  @Test
  public void testEs6ComputedPropCompatibility() {
    test(
        lines(
            "var obj = {",
            "  [goog.object.create(1, 2)]: 42",
            "}"),
        lines(
            "var obj = {",
            "  [{1: 2}]: 42",
            "}"));
  }

  @Test
  public void testEs6TemplateLitCompatibility() {
    test(
        lines(
            "function tag(strings) {",
            "  return goog.object.create('a', 2);",
            "}",
            "tag`template`"),
        lines(
            "function tag(strings) {",
            "  return {'a': 2};",
            "}",
            "tag`template`"));
  }

  @Test
  public void testEs6DestructuringCompatibility() {
    test("var {a: x} = goog.object.create('a', 2);", "var {a: x} = {'a': 2};");
  }

  @Test
  public void testEs6AsyncCompatibility() {
    test(
        lines(
            "async function foo() {",
            "   return await goog.object.create('a', 2);",
            "}"),
        lines(
            "async function foo() {",
            "   return await {'a': 2};",
            "}"));
  }
}
