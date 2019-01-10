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
import com.google.javascript.jscomp.CompilerTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CheckPrimitiveAsObjectTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckPrimitiveAsObject(compiler);
  }

  @Test
  public void testWarningForBooleanObjectCreation() {
    testWarning("new Boolean(false)", NEW_PRIMITIVE_OBJECT);
  }

  @Test
  public void testWarningForNumberObjectCreation() {
    testWarning("new Number(5)", NEW_PRIMITIVE_OBJECT);
  }

  @Test
  public void testWarningForStringObjectCreation() {
    testWarning("new String(\"hello\")", NEW_PRIMITIVE_OBJECT);
  }

  @Test
  public void testNoWarningForObjectCreation() {
    testSame("new Object()");
  }

  @Test
  public void testNoWarningForQualifiedClassCreation() {
    testSame("new my.qualified.ClassName()");
  }

  @Test
  public void testWarningForBooleanTypeDeclaration() {
    testWarning("/** @type {Boolean} */ var x;", PRIMITIVE_OBJECT_DECLARATION);
  }

  @Test
  public void testWarningForBooleanTypeDeclaration_withES6Modules() {
    testWarning("export /** @type {Boolean} */ var x;", PRIMITIVE_OBJECT_DECLARATION);
  }

  @Test
  public void testWarningForNumberTypeDeclaration() {
    testWarning("/** @type {Number} */ var x;", PRIMITIVE_OBJECT_DECLARATION);
  }

  @Test
  public void testWarningForNumberTypeDeclaration_withES6Modules() {
    testWarning("export /** @type {Number} */ var x;", PRIMITIVE_OBJECT_DECLARATION);
  }

  @Test
  public void testWarningForStringTypeDeclaration() {
    testWarning("/** @type {String} */ var x;", PRIMITIVE_OBJECT_DECLARATION);
  }

  @Test
  public void testWarningForStringTypeDeclaration_withES6Modules() {
    testWarning("export /** @type {String} */ var x;", PRIMITIVE_OBJECT_DECLARATION);
  }

  @Test
  public void testWarningForBooleanInsideTypeDeclaration() {
    testWarning("/** @type {function(): Boolean} */ var x;", PRIMITIVE_OBJECT_DECLARATION);
    testWarning("/** @type {function(Boolean)} */ var x;", PRIMITIVE_OBJECT_DECLARATION);
    testWarning("/** @type {{b: Boolean}} */ var x;", PRIMITIVE_OBJECT_DECLARATION);
  }

  @Test
  public void testWarningForBooleanInsideTypeDeclaration_withES6Modules() {
    testWarning("export /** @type {function(): Boolean} */ var x;", PRIMITIVE_OBJECT_DECLARATION);
  }

  @Test
  public void testWarningForNumberParameterDeclaration() {
    testWarning(
        lines(
            "/**",
            " * @param {Number=} x",
            " * @return {number}",
            " */",
            "function f(x) {",
            "  return x + 1;",
            "}"),
        PRIMITIVE_OBJECT_DECLARATION);
  }

  @Test
  public void testWarningForNumberParameterDeclaration_withES6Modules() {
    testWarning(
        lines(
            "export",
            "/**",
            " * @param {Number=} x",
            " * @return {number}",
            " */",
            "function f(x) {",
            "  return x + 1;",
            "}"),
        PRIMITIVE_OBJECT_DECLARATION);
  }

  @Test
  public void testWarningForBooleanParameterDeclarationInTypedef() {
    testWarning(
        lines(
            "/**", " * @typedef {function(Boolean=)}", " */", "var takesOptionalBoolean;"),
        PRIMITIVE_OBJECT_DECLARATION);
  }

  @Test
  public void testWarningForBooleanParameterDeclarationInTypedef_withES6Modules() {
    testWarning(
        lines(
            "export",
            "/**",
            " * @typedef {function(Boolean=)}",
            " */",
            "var takesOptionalBoolean;"),
        PRIMITIVE_OBJECT_DECLARATION);
  }

  @Test
  public void testWarningForNumberReturnDeclaration() {
    testWarning(
        lines("/**", " * @return {Number}", " */", "function f() {", "  return 5;", "}"),
        PRIMITIVE_OBJECT_DECLARATION);
  }

  @Test
  public void testWarningForNumberReturnDeclaration_withES6Modules() {
    testWarning(
        lines(
            "export", "/**", " * @return {Number}", " */", "function f() {", "  return 5;", "}"),
        PRIMITIVE_OBJECT_DECLARATION);
  }
}
