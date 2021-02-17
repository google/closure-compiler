/*
 * Copyright 2019 The Closure Compiler Authors.
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
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link RewriteCatchWithNoBinding}. */
@RunWith(JUnit4.class)
@SuppressWarnings("RhinoNodeGetFirstFirstChild")
public class RewriteCatchWithNoBindingTest extends CompilerTestCase {

  @Before
  public void enableTypeCheckBeforePass() {
    enableTypeCheck();
    enableTypeInfoValidation();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RewriteCatchWithNoBinding(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2018);
    return options;
  }

  @Test
  public void transpileCatchWithoutBinding() {
    test(
        lines(
            "try {", //
            "  stuff();",
            "} catch {",
            "  onError();",
            "}"),
        lines(
            "try {", //
            "  stuff();",
            "} catch ($jscomp$unused$catch) {",
            "  onError();",
            "}"));
    assertThat(getLastCompiler().getFeatureSet().contains(Feature.OPTIONAL_CATCH_BINDING))
        .isFalse();
  }

  @Test
  public void transpileCatchWithNoBindingNested() {
    test(
        lines(
            "try {", //
            "  stuff();",
            "} catch {",
            "  try {",
            "    onError();",
            "  } catch {",
            "    shruggie();",
            "  }",
            "}"),
        lines(
            "try {", //
            "  stuff();",
            "} catch ($jscomp$unused$catch) {",
            "  try {",
            "    onError();",
            "  } catch ($jscomp$unused$catch) {",
            "    shruggie();",
            "  }",
            "}"));
  }

  @Test
  public void typeOfAddedBindingIsUnknown() {
    test(
        lines(
            "try {", //
            "  stuff();",
            "} catch {",
            "  onError();",
            "}"),
        lines(
            "try {", //
            "  stuff();",
            "} catch ($jscomp$unused$catch) {",
            "  onError();",
            "}"));

    Node binding =
        getLastCompiler()
            .getRoot()
            .getSecondChild() // ROOT
            .getFirstChild() // SCRIPT
            .getFirstChild() // TRY
            .getSecondChild() // BLOCK
            .getFirstChild() // CATCH
            .getFirstChild(); // NAME

    assertType(binding.getJSType()).isUnknown();
  }

  @Test
  public void noTranspileCatchWithBinding() {
    testSame(
        lines(
            "try {", //
            "  stuff();",
            "} catch (err) {",
            "  onError(err);",
            "}"));
  }
}
