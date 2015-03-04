/*
 * Copyright 2009 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.TypeValidator.TYPE_MISMATCH_WARNING;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;

import com.google.common.collect.Lists;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.TypeValidator.TypeMismatch;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;

import java.util.Collections;
import java.util.List;

/**
 * Tests for TypeValidator.
 * @author nicksantos@google.com (Nick Santos)
 */
public class TypeValidatorTest extends CompilerTestCase {

  private Compiler compiler = null;

  public TypeValidatorTest() {
    enableTypeCheck(CheckLevel.ERROR);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    this.compiler = compiler;
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node n) {
        // Do nothing: we're in it for the type-checking.
      }
    };
  }

  @Override public int getNumRepetitions() { return 1; }

  public void testBasicMismatch() throws Exception {
    testSame("/** @param {number} x */ function f(x) {} f('a');",
        TYPE_MISMATCH_WARNING);
    assertMismatches(Lists.newArrayList(fromNatives(STRING_TYPE, NUMBER_TYPE)));
  }

  public void testFunctionMismatch() throws Exception {
    testSame(
        "/** \n" +
        " * @param {function(string): number} x \n" +
        " * @return {function(boolean): string} \n" +
        " */ function f(x) { return x; }",
        TYPE_MISMATCH_WARNING);

    JSTypeRegistry registry = compiler.getTypeRegistry();
    JSType string = registry.getNativeType(STRING_TYPE);
    JSType bool = registry.getNativeType(BOOLEAN_TYPE);
    JSType number = registry.getNativeType(NUMBER_TYPE);
    JSType firstFunction = registry.createFunctionType(number, string);
    JSType secondFunction = registry.createFunctionType(string, bool);

    assertMismatches(
        Lists.newArrayList(
            new TypeMismatch(firstFunction, secondFunction, null),
            fromNatives(STRING_TYPE, BOOLEAN_TYPE),
            fromNatives(NUMBER_TYPE, STRING_TYPE)));
  }

  public void testFunctionMismatch2() throws Exception {
    testSame(
        "/** \n" +
        " * @param {function(string): number} x \n" +
        " * @return {function(boolean): number} \n" +
        " */ function f(x) { return x; }",
        TYPE_MISMATCH_WARNING);

    JSTypeRegistry registry = compiler.getTypeRegistry();
    JSType string = registry.getNativeType(STRING_TYPE);
    JSType bool = registry.getNativeType(BOOLEAN_TYPE);
    JSType number = registry.getNativeType(NUMBER_TYPE);
    JSType firstFunction = registry.createFunctionType(number, string);
    JSType secondFunction = registry.createFunctionType(number, bool);

    assertMismatches(
        Lists.newArrayList(
            new TypeMismatch(firstFunction, secondFunction, null),
            fromNatives(STRING_TYPE, BOOLEAN_TYPE)));
  }

  /**
   * Make sure the 'found' and 'required' strings are not identical when there is a mismatch.
   * See https://code.google.com/p/closure-compiler/issues/detail?id=719.
   */
  public void testFunctionMismatchLongTypes() throws Exception {
    testSame("",
        "/**\n" +
        " * @param {{a: string, b: string, c: string, d: string, e: string}} x\n" +
        " */\n" +
        "function f(x) {}\n" +
        "var y = {a:'',b:'',c:'',d:'',e:0};" +
        "f(y);",
        TYPE_MISMATCH_WARNING,
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : {a: string, b: string, c: string, d: string, e: (number|string)}\n" +
        "required: {a: string, b: string, c: string, d: string, e: string}");
  }

  /**
   * Same as testFunctionMismatchLongTypes, but with one of the types being a typedef.
   */
  public void testFunctionMismatchTypedef() throws Exception {
    testSame("",
        "/**\n" +
        " * @typedef {{a: string, b: string, c: string, d: string, e: string}}\n" +
        " */\n" +
        "var t;\n" +
        "/**\n" +
        " * @param {t} x\n" +
        " */\n" +
        "function f(x) {}\n" +
        "var y = {a:'',b:'',c:'',d:'',e:0};" +
        "f(y);",
        TYPE_MISMATCH_WARNING,
        "actual parameter 1 of f does not match formal parameter\n" +
        "found   : {a: string, b: string, c: string, d: string, e: (number|string)}\n" +
        "required: {a: string, b: string, c: string, d: string, e: string}");
  }

  public void testNullUndefined() {
    testSame("/** @param {string} x */ function f(x) {}\n" +
             "f(/** @type {string|null|undefined} */ ('a'));",
             TYPE_MISMATCH_WARNING);
    assertMismatches(Collections.<TypeMismatch>emptyList());
  }

  public void testSubclass() {
    testSame("/** @constructor */\n"  +
             "function Super() {}\n" +
             "/**\n" +
             " * @constructor\n" +
             " * @extends {Super}\n" +
             " */\n" +
             "function Sub() {}\n" +
             "/** @param {Sub} x */ function f(x) {}\n" +
             "f(/** @type {Super} */ (new Sub));",
             TYPE_MISMATCH_WARNING);
    assertMismatches(Collections.<TypeMismatch>emptyList());
  }

  private TypeMismatch fromNatives(JSTypeNative a, JSTypeNative b) {
    JSTypeRegistry registry = compiler.getTypeRegistry();
    return new TypeMismatch(
        registry.getNativeType(a), registry.getNativeType(b), null);
  }

  private void assertMismatches(List<TypeMismatch> expected) {
    List<TypeMismatch> actual = Lists.newArrayList(compiler.getTypeMismatches());
    assertEquals(expected, actual);
  }
}
