/*
 * Copyright 2014 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerTestCase.NoninjectingCompiler;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test cases for the transpilation pass that eliminates shorthand property declarations.
 *
 * <p>A shorthand property is where another identifier (variable) is used to define both the name
 * <em>and</em> value of the property.
 */
@RunWith(JUnit4.class)
public final class Es6NormalizeShorthandPropertiesTest extends CompilerTestCase {

  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler();
  }

  @Override
  protected NoninjectingCompiler getLastCompiler() {
    return (NoninjectingCompiler) super.getLastCompiler();
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2016);
    setLanguageOut(LanguageMode.ECMASCRIPT3);

    enableTypeInfoValidation();
    enableTypeCheck();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new Es6NormalizeShorthandProperties(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Test
  public void testNormalizationInSource() {
    test(
        externs(""),
        // Note that there's no *structural* AST difference here for the validator to notice.
        srcs("const x = 5; const y = {x};"),
        expected("const x = 5; const y = {x: x};"),
        postcondition(
            (compiler) -> {
              NodeUtil.visitPreOrder(
                  compiler.getRoot(),
                  Es6NormalizeShorthandPropertiesTest::assertNotShorthandProperty);
            }));
  }

  @Test
  public void testNormalizationInExterns() {
    String externWithShorthandProperty = "const x = 5; const y = {x};";
    String pinningSource = "const t = y.x;";

    // Verify the extern is preserved and looks reasonable. Note that there's no *structural* AST
    // difference here for the validator to notice.
    testExternChanges(externWithShorthandProperty, pinningSource, "const x = 5; const y = {x: x};");

    // Verify the shorthand property flag is removed.
    test(
        externs(externWithShorthandProperty),
        srcs(pinningSource),
        postcondition(
            (compiler) -> {
              NodeUtil.visitPreOrder(
                  compiler.getExternsRoot(),
                  Es6NormalizeShorthandPropertiesTest::assertNotShorthandProperty);
            }));
  }

  private static void assertNotShorthandProperty(Node node) {
    assertWithMessage("Detected shorthand property node <%s>.", node)
        .that(node.isShorthandProperty())
        .isFalse();
  }
}
