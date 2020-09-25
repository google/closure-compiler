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

import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Splitter;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProductionInstrumentationReporterTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private static String readFile(String filePath) throws IOException {
    return CharStreams.toString(Files.newBufferedReader(Paths.get(filePath), UTF_8));
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

    List<String> actualFileContents =
        Splitter.on('\n').omitEmptyStrings().splitToList(readFile(tempFilePath.toString()));

    List<String> expectedFileContents =
        Splitter.on('\n')
            .omitEmptyStrings()
            .splitToList(
                readFile(
                    "test/"
                        + "com/google/javascript/jscomp/instrumentation/reporter/testdata/expectedFinalResult.json"));

    assertWithMessage("Number of lines between expected and actual do not match")
        .that(actualFileContents.size())
        .isEqualTo(expectedFileContents.size());

    for (int i = 0; i < actualFileContents.size(); i++) {
      assertWithMessage(
              "Expected final JSON does not match expected at line no: "
                  + (i + 1)
                  + "\nExpected:\n"
                  + expectedFileContents.get(i)
                  + "\nFound:\n"
                  + actualFileContents.get(i)
                  + "\n")
          .that(actualFileContents.get(i))
          .isEqualTo(expectedFileContents.get(i));
    }
  }
}
