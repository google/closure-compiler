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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.testing.BaseJSTypeTestCase.ALL_NATIVE_EXTERN_TYPES;

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link FunctionTypeBuilder}.
 *
 */
@RunWith(JUnit4.class)
public final class FunctionTypeBuilderTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableParseTypeInfo();
    enableTypeCheck();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    // By turning on type checking, the FunctionTypeBuilder will be invoked.
    return new CompilerPass() {
          @Override
          public void process(Node externs, Node js) {}
        };
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Test
  public void testValidBuiltInTypeRedefinition() {
    testSame(externs(ALL_NATIVE_EXTERN_TYPES), srcs(""));
  }

  @Test
  public void testBuiltInTypeDifferentReturnType() {
    test(
        externs(
            "/**\n"
                + " * @constructor\n"
                + " * @param {*} opt_str\n"
                + " * @return {number}\n"
                + " */\n"
                + "function String(opt_str) {}\n"),
        srcs(""),
        warning(FunctionTypeBuilder.TYPE_REDEFINITION)
            .withMessage(
                "attempted re-definition of type String\n"
                    + "found   : function(new:String, *=): number\n"
                    + "expected: function(new:String, *=): string"));
  }

  @Test
  public void testBuiltInTypeDifferentNumParams() {
    test(
        externs(
            "/**\n"
                + " * @constructor\n"
                + " * @return {string}\n"
                + " */\n"
                + "function String() {}\n"),
        srcs(""),
        warning(FunctionTypeBuilder.TYPE_REDEFINITION)
            .withMessage(
                "attempted re-definition of type String\n"
                    + "found   : function(new:String): string\n"
                    + "expected: function(new:String, *=): string"));
  }

  @Test
  public void testBuiltInTypeDifferentNumParams2() {
    test(
        externs(
            "/**\n"
                + " * @constructor\n"
                + " * @return {string}\n"
                + " */\n"
                + "function String(opt_str, opt_nothing) {}\n"),
        srcs(""),
        warning(FunctionTypeBuilder.TYPE_REDEFINITION)
            .withMessage(
                "attempted re-definition of type String\n"
                    + "found   : function(new:String, ?=, ?=): string\n"
                    + "expected: function(new:String, *=): string"));
  }

  @Test
  public void testBuiltInTypeDifferentParamType() {
    test(
        externs(
            "/**\n"
                + " * @constructor\n"
                + " * @return {string}\n"
                + " */\n"
                + "function String(opt_str) {}\n"),
        srcs(""),
        warning(FunctionTypeBuilder.TYPE_REDEFINITION)
            .withMessage(
                "attempted re-definition of type String\n"
                    + "found   : function(new:String, ?=): string\n"
                    + "expected: function(new:String, *=): string"));
  }

  @Test
  public void testBadFunctionTypeDefinition() {
    test(
        externs("/** @constructor */function Function(opt_str) {}\n"),
        srcs(""),
        warning(FunctionTypeBuilder.TYPE_REDEFINITION)
            .withMessage(
                "attempted re-definition of type Function\n"
                    + "found   : function(new:Function, ?=): ?\n"
                    + "expected: function(new:Function, ...*): ?"));
  }

  @Test
  public void testInlineJsDoc() {
    test(
        externs("/** @return {number} */ function f(/** string */ x) { return x; }"),
        srcs(""),
        warning(TypeValidator.TYPE_MISMATCH_WARNING)
            .withMessage("inconsistent return type\n" + "found   : string\n" + "required: number"));
  }

  @Test
  public void testInlineJsDoc2() {
    test(
        externs(
            "/** @return {T} \n @template T */ "
                + "function f(/** T */ x) { return x; }"
                + "/** @type {string} */ var x = f(1);"),
        srcs(""),
        warning(TypeValidator.TYPE_MISMATCH_WARNING)
            .withMessage("initializing variable\n" + "found   : number\n" + "required: string"));
  }

  @Test
  public void testExternSubTypes() {
    testSame(externs(ALL_NATIVE_EXTERN_TYPES), srcs(""));

    List<FunctionType> subtypes =
        ImmutableList.copyOf(
            ((ObjectType) getLastCompiler().getTypeRegistry().getGlobalType("Error"))
                .getConstructor().getDirectSubTypes());
    for (FunctionType type : subtypes) {
      String typeName = type.getInstanceType().toString();
      FunctionType typeInRegistry = ((ObjectType) getLastCompiler()
          .getTypeRegistry().getGlobalType(typeName)).getConstructor();
      assertThat(typeInRegistry).isSameAs(type);
    }
  }
}
