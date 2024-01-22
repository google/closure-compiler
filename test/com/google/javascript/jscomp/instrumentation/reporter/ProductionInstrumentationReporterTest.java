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

package com.google.javascript.jscomp.instrumentation.reporter;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.CharStreams;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProductionInstrumentationReporterTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private static String readFile(String filePath) throws IOException {
    return CharStreams.toString(Files.newBufferedReader(Path.of(filePath), UTF_8)).trim();
  }

  @Test
  public void testExpectedReportingResults() throws IOException {
    Path tempFilePath = Files.createTempFile("somefile", "json");

    String[] args = new String[6];
    args[0] = "--mapping_file";
    args[1] =
        "test/"
            + "com/google/javascript/jscomp/instrumentation/reporter/testdata/instrumentationMapping.txt";
    args[2] = "--reports_directory";
    args[3] =
        "test/"
            + "com/google/javascript/jscomp/instrumentation/reporter/testdata/reports";
    args[4] = "--result_output";
    args[5] = tempFilePath.toString();

    ProductionInstrumentationReporter.main(args);

    assertThat(readFile(tempFilePath.toString()))
        .isEqualTo(
            readFile(
                "test/"
                    + "com/google/javascript/jscomp/instrumentation/reporter/testdata/expectedFinalResult.txt"));
  }
}
