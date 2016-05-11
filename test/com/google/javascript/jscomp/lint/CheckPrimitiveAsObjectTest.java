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
package com.google.javascript.jscomp.lint;

import static com.google.javascript.jscomp.lint.CheckPrimitiveAsObject.NEW_PRIMITIVE_OBJECT;
import static com.google.javascript.jscomp.lint.CheckPrimitiveAsObject.PRIMITIVE_OBJECT_DECLARATION;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.Es6CompilerTestCase;

public final class CheckPrimitiveAsObjectTest extends Es6CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckPrimitiveAsObject(compiler);
  }

  public void testWarningForBooleanObjectCreation() {
    testWarning("new Boolean(false)", NEW_PRIMITIVE_OBJECT);
  }

  public void testWarningForNumberObjectCreation() {
    testWarning("new Number(5)", NEW_PRIMITIVE_OBJECT);
  }

  public void testWarningForStringObjectCreation() {
    testWarning("new String(\"hello\")", NEW_PRIMITIVE_OBJECT);
  }

  public void testNoWarningForObjectCreation() {
    testSame("new Object()");
  }

  public void testNoWarningForQualifiedClassCreation() {
    testSame("new my.qualified.ClassName()");
  }

  public void testWarningForBooleanTypeDeclaration() {
    testWarning("/** @type {Boolean} */ var x;", PRIMITIVE_OBJECT_DECLARATION);
  }

  public void testWarningForNumberTypeDeclaration() {
    testWarning("/** @type {Number} */ var x;", PRIMITIVE_OBJECT_DECLARATION);
  }

  public void testWarningForStringTypeDeclaration() {
    testWarning("/** @type {String} */ var x;", PRIMITIVE_OBJECT_DECLARATION);
  }

  public void testWarningForBooleanInsideTypeDeclaration() {
    testWarning("/** @type {function(): Boolean} */ var x;", PRIMITIVE_OBJECT_DECLARATION);
    testWarning("/** @type {function(Boolean)} */ var x;", PRIMITIVE_OBJECT_DECLARATION);
    testWarning("/** @type {{b: Boolean}} */ var x;", PRIMITIVE_OBJECT_DECLARATION);
  }

  public void testWarningForNumberParameterDeclaration() {
    testWarning(
        LINE_JOINER.join(
            "/**",
            " * @param {Number=} x",
            " * @return {number}",
            " */",
            "function f(x) {",
            "  return x + 1;",
            "}"),
        PRIMITIVE_OBJECT_DECLARATION);
  }

  public void testWarningForBooleanParameterDeclarationInTypedef() {
    testWarning(
        LINE_JOINER.join(
            "/**",
            " * @typedef {function(Boolean=)}",
            " */",
            "var takesOptionalBoolean;"),
        PRIMITIVE_OBJECT_DECLARATION);
  }

  public void testWarningForNumberReturnDeclaration() {
    testWarning(
        LINE_JOINER.join(
            "/**",
            " * @return {Number}",
            " */",
            "function f() {",
            "  return 5;",
            "}"),
        PRIMITIVE_OBJECT_DECLARATION);
  }
}
