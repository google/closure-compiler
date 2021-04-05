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
import static java.nio.charset.StandardCharsets.US_ASCII;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
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
  }

  @Test
  public void testDefines() {
    CompilerOptions options = new CompilerOptions();
    options.setDefineToBooleanLiteral("trueVar", true);
    options.setDefineToBooleanLiteral("falseVar", false);
    options.setDefineToNumberLiteral("threeVar", 3);
    options.setDefineToStringLiteral("strVar", "str");

    Map<String, Node> actual = options.getDefineReplacements();
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
  public void testSerialization() throws Exception {
    CompilerOptions options = new CompilerOptions();
    options.setDefineToBooleanLiteral("trueVar", true);
    options.setDefineToBooleanLiteral("falseVar", false);
    options.setDefineToNumberLiteral("threeVar", 3);
    options.setDefineToStringLiteral("strVar", "str");
    options.setOptimizeArgumentsArray(true);
    options.setAmbiguateProperties(false);
    options.setOutputCharset(US_ASCII);

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    options.serialize(byteArrayOutputStream);

    options =
        CompilerOptions.deserialize(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));

    Map<String, Node> actual = options.getDefineReplacements();
    assertEquivalent(new Node(Token.TRUE), actual.get("trueVar"));
    assertEquivalent(new Node(Token.FALSE), actual.get("falseVar"));
    assertEquivalent(Node.newNumber(3), actual.get("threeVar"));
    assertEquivalent(Node.newString("str"), actual.get("strVar"));
    assertThat(options.shouldAmbiguateProperties()).isFalse();
    assertThat(options.optimizeArgumentsArray).isTrue();
    assertThat(options.getOutputCharset()).isEqualTo(US_ASCII);
  }

  @Test
  public void testRemoveRegexFromPath() {
    CompilerOptions options = new CompilerOptions();
    Pattern pattern = options.getConformanceRemoveRegexFromPath().get();
    assertThat(pattern.matcher("/blaze-out/k8-fastbin/bin/some/path").replaceAll(""))
        .isEqualTo("some/path");
    assertThat(pattern.matcher("blaze-out/k8-fastbin/bin/some/path").replaceAll(""))
        .isEqualTo("some/path");
    assertThat(pattern.matcher("google3/bazel-out/k8-fastbin/bin/some/path").replaceAll(""))
        .isEqualTo("some/path");
    assertThat(pattern.matcher("google3/blaze-out/k8-fastbin/genfiles/some/path").replaceAll(""))
        .isEqualTo("genfiles/some/path");
    assertThat(pattern.matcher("somethin/google3/blaze-out/k8-fastbin/some/path").replaceAll(""))
        .isEqualTo("blaze-out/k8-fastbin/some/path");

    assertThat(pattern.matcher("google3/foo/blaze-out/some/path").replaceAll(""))
        .isEqualTo("foo/blaze-out/some/path");
    assertThat(pattern.matcher("google3/blaze-out/foo/blaze-out/some/path").replaceAll(""))
        .isEqualTo("blaze-out/foo/blaze-out/some/path");
  }
}
