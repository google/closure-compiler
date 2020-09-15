package com.google.javascript.jscomp.instrumentation.reporter;

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Splitter;
import com.google.common.io.CharStreams;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProductionInstrumentationReporterTest {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  private static String readFile(String filePath) throws IOException {
    return CharStreams.toString(new FileReader(filePath));
  }

  @Test
  public void testExpectedReportingResults() throws IOException {

    Path tempFolderPath = folder.getRoot().toPath();
    String filePath = tempFolderPath + "\\someFile.json";

    String[] args = new String[6];
    args[0] = "--mapping_file";
    args[1] = "test/com/google/javascript/jscomp/testdata/ProductionInstrumentationReporterTest/instrumentationMapping.txt";
    args[2] = "--reports_directory";
    args[3] = "test/com/google/javascript/jscomp/testdata/ProductionInstrumentationReporterTest/reports";
    args[4] = "--result_output";
    args[5] = filePath;

    ProductionInstrumentationReporter.main(args);

    List<String> actualFileContents = Splitter.on('\n').omitEmptyStrings()
        .splitToList(readFile(filePath));

    List<String> expectedFileContents = Splitter.on('\n').omitEmptyStrings()
        .splitToList(readFile("test/com/google/javascript/jscomp/testdata/ProductionInstrumentationReporterTest/expectedFinalResult.json"));

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