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

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CompilerOptions.BrowserFeaturesetYear;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CompilerOptions}. */
@RunWith(JUnit4.class)
public final class CompilerOptionsTest {

  @Test
  public void testBrowserFeaturesetYearOptionSetsLanguageOut() {
    CompilerOptions options = new CompilerOptions();
    options.setBrowserFeaturesetYear(2012);
    assertThat(options.getOutputFeatureSet())
        .isEqualTo(LanguageMode.ECMASCRIPT5_STRICT.toFeatureSet());

    options.setBrowserFeaturesetYear(2019);
    assertThat(options.getOutputFeatureSet())
        .isEqualTo(LanguageMode.ECMASCRIPT_2017.toFeatureSet());

    options.setBrowserFeaturesetYear(2020);
    assertThat(options.getOutputFeatureSet()).isEqualTo(FeatureSet.BROWSER_2020);

    options.setBrowserFeaturesetYear(2021);
    assertThat(options.getOutputFeatureSet()).isEqualTo(FeatureSet.BROWSER_2021);

    options.setBrowserFeaturesetYear(2022);
    assertThat(options.getOutputFeatureSet()).isEqualTo(FeatureSet.BROWSER_2022);

    options.setBrowserFeaturesetYear(2023);
    assertThat(options.getOutputFeatureSet()).isEqualTo(FeatureSet.BROWSER_2023);

    options.setBrowserFeaturesetYear(2024);
    assertThat(options.getOutputFeatureSet()).isEqualTo(FeatureSet.BROWSER_2024);

    options.setBrowserFeaturesetYear(2025);
    assertThat(options.getOutputFeatureSet()).isEqualTo(FeatureSet.BROWSER_2025);

    options.setBrowserFeaturesetYear(2026);
    assertThat(options.getOutputFeatureSet()).isEqualTo(FeatureSet.BROWSER_2026);
  }

  @Test
  public void testBrowserFeaturesetYearOptionSetsAssumeES5() {
    CompilerOptions options = new CompilerOptions();
    options.setBrowserFeaturesetYear(2012);
    assertThat(options.getDefineReplacements().get("$jscomp.ASSUME_ES5").getToken())
        .isEqualTo(Token.FALSE);
    options.setBrowserFeaturesetYear(2019);
    assertThat(options.getDefineReplacements().get("$jscomp.ASSUME_ES5").getToken())
        .isEqualTo(Token.TRUE);
  }

  @Test
  public void testBrowserFeaturesetYearOptionSetsAssumeES6() {
    CompilerOptions options = new CompilerOptions();
    options.setBrowserFeaturesetYear(2012);
    assertThat(options.getDefineReplacements().get("$jscomp.ASSUME_ES6").getToken())
        .isEqualTo(Token.FALSE);
    options.setBrowserFeaturesetYear(2018);
    assertThat(options.getDefineReplacements().get("$jscomp.ASSUME_ES6").getToken())
        .isEqualTo(Token.TRUE);
    options.setBrowserFeaturesetYear(2020);
    assertThat(options.getDefineReplacements().get("$jscomp.ASSUME_ES2020").getToken())
        .isEqualTo(Token.FALSE);
    options.setBrowserFeaturesetYear(2021);
    assertThat(options.getDefineReplacements().get("$jscomp.ASSUME_ES2020").getToken())
        .isEqualTo(Token.TRUE);
  }

  @Test
  public void testMinimumBrowserFeatureSetYearRequiredFor() {
    assertThat(BrowserFeaturesetYear.minimumRequiredFor(Feature.GETTER))
        .isEqualTo(BrowserFeaturesetYear.YEAR_2012);
    assertThat(BrowserFeaturesetYear.minimumRequiredFor(Feature.CLASSES))
        .isEqualTo(BrowserFeaturesetYear.YEAR_2018);
    assertThat(BrowserFeaturesetYear.minimumRequiredFor(Feature.REGEXP_UNICODE_PROPERTY_ESCAPE))
        .isEqualTo(BrowserFeaturesetYear.YEAR_2021);
  }

  @Test
  public void testMinimumBrowserFeatureSetYearRequiredFor_returnsUnspecifiedIfUnsupported() {
    // Newer features, in particular anything in ES_NEXT, may not be part of a browser featureset
    // year yet.
    assertThat(BrowserFeaturesetYear.minimumRequiredFor(Feature.ES_NEXT_RUNTIME)).isNull();
  }

  @Test
  public void testDefines() {
    CompilerOptions options = new CompilerOptions();
    options.setDefineToBooleanLiteral("trueVar", true);
    options.setDefineToBooleanLiteral("falseVar", false);
    options.setDefineToNumberLiteral("threeVar", 3);
    options.setDefineToStringLiteral("strVar", "str");

    ImmutableMap<String, Node> actual = options.getDefineReplacements();
    assertEquivalent(new Node(Token.TRUE), actual.get("trueVar"));
    assertEquivalent(new Node(Token.FALSE), actual.get("falseVar"));
    assertEquivalent(Node.newNumber(3), actual.get("threeVar"));
    assertEquivalent(Node.newString("str"), actual.get("strVar"));
  }

  public void assertEquivalent(Node a, Node b) {
    assertThat(a.isEquivalentTo(b)).isTrue();
  }

  @Test
  public void testLanguageModeFromString() {
    assertThat(LanguageMode.fromString("ECMASCRIPT3")).isEqualTo(LanguageMode.ECMASCRIPT3);
    // Whitespace should be trimmed, characters converted to uppercase and leading 'ES' replaced
    // with 'ECMASCRIPT'.
    assertThat(LanguageMode.fromString("  es3  ")).isEqualTo(LanguageMode.ECMASCRIPT3);
    assertThat(LanguageMode.fromString("junk")).isNull();

    assertThat(LanguageMode.fromString("ECMASCRIPT_2020")).isEqualTo(LanguageMode.ECMASCRIPT_2020);
    // Whitespace should be trimmed, characters converted to uppercase and leading 'ES' replaced
    // with 'ECMASCRIPT'.
    assertThat(LanguageMode.fromString("  es_2020  ")).isEqualTo(LanguageMode.ECMASCRIPT_2020);
    assertThat(LanguageMode.fromString("  es2020  "))
        .isNull(); // generates invalid "ECMASCRIPT2020"
    assertThat(LanguageMode.fromString("junk")).isNull();
  }

  @Test
  public void testEmitUseStrictWorksInEs3() {
    CompilerOptions options = new CompilerOptions();
    options.setEmitUseStrict(true);

    assertThat(options.shouldEmitUseStrict()).isTrue();
  }

  @Test
  public void testRemoveRegexFromPath() {
    CompilerOptions options = new CompilerOptions();
    Pattern pattern = options.getConformanceRemoveRegexFromPath().get();
    assertThat(pattern.matcher("/blaze-out/k8-fastbin/bin/some/path").replaceAll(""))
        .isEqualTo("some/path");
    assertThat(pattern.matcher("blaze-out/k8-fastbin/bin/some/path").replaceAll(""))
        .isEqualTo("some/path");
    assertThat(pattern.matcher("google3/blaze-out/k8-fastbin/bin/some/path").replaceAll(""))
        .isEqualTo("some/path");
    assertThat(pattern.matcher("google3/blaze-out/k8-fastbin/genfiles/some/path").replaceAll(""))
        .isEqualTo("some/path");
    assertThat(pattern.matcher("something/google3/blaze-out/k8-fastbin/some/path").replaceAll(""))
        .isEqualTo("blaze-out/k8-fastbin/some/path");
    assertThat(
            pattern.matcher("/something/google3/blaze-out/k8-fastbin/bin/some/path").replaceAll(""))
        .isEqualTo("some/path");
    assertThat(pattern.matcher("google3/some/path").replaceAll("")).isEqualTo("some/path");

    assertThat(pattern.matcher("google3/foo/blaze-out/some/path").replaceAll(""))
        .isEqualTo("foo/blaze-out/some/path");
    assertThat(pattern.matcher("google3/blaze-out/foo/blaze-out/some/path").replaceAll(""))
        .isEqualTo("blaze-out/foo/blaze-out/some/path");

    assertThat(pattern.matcher("bazel-out/k8-fastbin/bin/some/path").replaceAll(""))
        .isEqualTo("some/path");
    assertThat(pattern.matcher("bazel-out/k8-fastbin/genfiles/some/path").replaceAll(""))
        .isEqualTo("some/path");
  }
}
