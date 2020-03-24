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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.gson.Gson;
import com.google.javascript.jscomp.DiagnosticToSuppressionMapper.OutputFormat;
import com.google.javascript.jscomp.parsing.ParserRunner;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link com.google.javascript.jscomp.DiagnosticToSuppressionMapper} */
@RunWith(JUnit4.class)
public final class DiagnosticToSuppressionMapperTest {

  @Test
  public void createSuppressionMap_asJson_skipsNonParseableSuppressions() {
    final DiagnosticType testError1 = DiagnosticType.error("JSC_TEST_ERROR_1", "testing");
    final DiagnosticType testError2 = DiagnosticType.error("JSC_TEST_ERROR_2", "testing");
    ImmutableMap<String, DiagnosticGroup> diagnosticGroups =
        ImmutableMap.of(
            "validSuppress", DiagnosticGroup.forType(testError1),
            "invalidSuppress", DiagnosticGroup.forType(testError2));

    ImmutableSortedMap<String, String> suppressions =
        new DiagnosticToSuppressionMapper(ImmutableSet.of("validSuppress"), diagnosticGroups)
            .createSuppressionMap();

    assertThat(suppressions).isEqualTo(ImmutableMap.of(testError1.key, "validSuppress"));
  }

  @Test
  public void createSuppressionMap_choosesArbitrarySuppressionGivenMultiple() {
    final DiagnosticType testError = DiagnosticType.error("JSC_TEST_ERROR", "testing");
    ImmutableMap<String, DiagnosticGroup> diagnosticGroups =
        ImmutableMap.of(
            "suppress0", DiagnosticGroup.forType(testError),
            "suppress1", DiagnosticGroup.forType(testError));

    ImmutableSortedMap<String, String> suppressions =
        new DiagnosticToSuppressionMapper(
                ImmutableSet.of("suppress0", "suppress1"), diagnosticGroups)
            .createSuppressionMap();

    assertThat(suppressions).isEqualTo(ImmutableMap.of(testError.key, "suppress1"));
  }

  @Test
  @SuppressWarnings("unchecked") // for deserialization of json
  public void printSuppressionMap_asJson_printsValidJson() {
    final ByteArrayOutputStream myOut = new ByteArrayOutputStream();
    System.setOut(new PrintStream(myOut));

    DiagnosticToSuppressionMapper mapper =
        new DiagnosticToSuppressionMapper(
            ParserRunner.getSuppressionNames(), DiagnosticGroups.getRegisteredGroups());
    mapper.printSuppressions(OutputFormat.JSON);

    LinkedHashMap<String, String> suppressions =
        new Gson().fromJson(myOut.toString(), LinkedHashMap.class);

    assertThat(suppressions).isEqualTo(mapper.createSuppressionMap());
  }

  @Test
  public void printSuppressionMap_asMarkdown_printsSortedTable() {
    final DiagnosticType testError1 = DiagnosticType.error("JSC_TEST_ERROR_1", "testing");
    final DiagnosticType testError2 = DiagnosticType.error("JSC_TEST_ERROR_2", "testing");
    final DiagnosticType testError3 = DiagnosticType.error("JSC_OTHER_ERROR", "testing");
    ImmutableMap<String, DiagnosticGroup> diagnosticGroups =
        ImmutableMap.of(
            "suppressionA",
            new DiagnosticGroup("suppressionA", testError2, testError3),
            "suppressionB",
            DiagnosticGroup.forType(testError1));

    final ByteArrayOutputStream myOut = new ByteArrayOutputStream();
    System.setOut(new PrintStream(myOut));

    DiagnosticToSuppressionMapper mapper =
        new DiagnosticToSuppressionMapper(
            ImmutableSet.of("suppressionA", "suppressionB"), diagnosticGroups);
    mapper.printSuppressions(OutputFormat.MD);

    assertThat(myOut.toString())
        .isEqualTo(
            "| Error | Suppression tag |\n"
                + "|---|---|\n"
                + "|JSC_OTHER_ERROR|suppressionA|\n"
                + "|JSC_TEST_ERROR_1|suppressionB|\n"
                + "|JSC_TEST_ERROR_2|suppressionA|\n");
  }
}
