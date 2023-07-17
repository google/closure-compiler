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
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.colors.StandardColors;
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
  private static final ImmutableMap<String, String> SPECIAL_VARIABLE_MAP =
      ImmutableMap.of("UNUSED_CATCH", "$jscomp$unused$catch$");

  @Before
  public void customSetUp() {
    enableNormalize();
    enableTypeCheck();
    enableTypeInfoValidation();
    replaceTypesWithColors();
    enableMultistageCompilation();
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

  private void rewriteCatchTest(Sources srcs, Expected originalExpected) {
    Expected modifiedExpected =
        expected(
            UnitTestUtils.updateGenericVarNamesInExpectedFiles(
                (FlatSources) srcs, originalExpected, SPECIAL_VARIABLE_MAP));
    test(srcs, modifiedExpected);
  }

  @Test
  public void transpileCatchWithoutBinding() {
    Sources srcs =
        srcs(
            lines(
                "try {", //
                "  stuff();",
                "} catch {",
                "  onError();",
                "}"));
    Expected originalExpected =
        expected(
            lines(
                "try {", //
                "  stuff();",
                "} catch (UNUSED_CATCH$0) {",
                "  onError();",
                "}"));
    rewriteCatchTest(srcs, originalExpected);
    assertThat(getLastCompiler().getAllowableFeatures().contains(Feature.OPTIONAL_CATCH_BINDING))
        .isFalse();
  }

  @Test
  public void transpileCatchWithNoBindingNested() {
    Sources srcs =
        srcs(
            lines(
                "try {", //
                "  stuff();",
                "} catch {",
                "  try {",
                "    onError();",
                "  } catch {",
                "    shruggie();",
                "  }",
                "}"));
    Expected originalExpected =
        expected(
            lines(
                "try {", //
                "  stuff();",
                "} catch (UNUSED_CATCH$1) {",
                "  try {",
                "    onError();",
                "  } catch (UNUSED_CATCH$0) {",
                "    shruggie();",
                "  }",
                "}"));
    rewriteCatchTest(srcs, originalExpected);
  }

  @Test
  public void typeOfAddedBindingIsUnknown() {
    Sources srcs =
        srcs(
            lines(
                "try {", //
                "  stuff();",
                "} catch {",
                "  onError();",
                "}"));
    Expected originalExpected =
        expected(
            lines(
                "try {", //
                "  stuff();",
                "} catch (UNUSED_CATCH$0) {",
                "  onError();",
                "}"));
    rewriteCatchTest(srcs, originalExpected);

    Node binding =
        getLastCompiler()
            .getRoot()
            .getSecondChild() // ROOT
            .getFirstChild() // SCRIPT
            .getFirstChild() // TRY
            .getSecondChild() // BLOCK
            .getFirstChild() // CATCH
            .getFirstChild(); // NAME

    assertNode(binding).hasColorThat().isEqualTo(StandardColors.UNKNOWN);
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
