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

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link RemoveTrailingCommaFromParamList()}. */
@RunWith(JUnit4.class)
@SuppressWarnings("RhinoNodeGetFirstFirstChild")
public class RemoveTrailingCommaFromParamListTest extends CompilerTestCase {

  @Before
  public void enableTypeCheckBeforePass() {
    enableTypeCheck();
    enableTypeInfoValidation();
    // Trailing commas are not considered when comparing Nodes for equality, so we must do a text
    // comparison.
    disableCompareAsTree();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RemoveTrailingCommaFromParamList(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    // ES 2017 started allowing trailing commas in parameter lists and argument lists
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2016);
    // Trailing commas are only printed in pretty-print mode
    options.setPrettyPrint(true);
    return options;
  }

  @Test
  public void transpileTrailingCommaInFunctionDeclaration() {
    test("function f(a, b,) {}", "function f(a, b) {\n}\n");
    assertThat(getLastCompiler().getFeatureSet().contains(Feature.TRAILING_COMMA_IN_PARAM_LIST))
        .isFalse();
  }

  @Test
  public void transpileTrailingCommaInFunctionCall() {
    test("f(a, b,);", "f(a, b);\n");
    assertThat(getLastCompiler().getFeatureSet().contains(Feature.TRAILING_COMMA_IN_PARAM_LIST))
        .isFalse();
  }

  @Test
  public void transpileTrailingCommaInNewObject() {
    test("let x = new Number(1,);", "let x = new Number(1);\n");
    assertThat(getLastCompiler().getFeatureSet().contains(Feature.TRAILING_COMMA_IN_PARAM_LIST))
        .isFalse();
  }
}
