/*
 * Copyright 2007 The Closure Compiler Authors.
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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.Iterables;
import com.google.common.io.MoreFiles;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@RunWith(JUnit4.class)
public final class SourceFileTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void testSourceKind() {
    SourceFile sf1 = SourceFile.fromCode("test1.js", "1");
    assertThat(sf1.isStrong()).isTrue();
    assertThat(sf1.isWeak()).isFalse();
    assertThat(sf1.isExtern()).isFalse();

    sf1.setKind(SourceKind.WEAK);
    assertThat(sf1.isStrong()).isFalse();
    assertThat(sf1.isWeak()).isTrue();
    assertThat(sf1.isExtern()).isFalse();

    SourceFile sf2 = SourceFile.fromCode("test2.js", "2", SourceKind.WEAK);
    assertThat(sf2.isStrong()).isFalse();
    assertThat(sf2.isWeak()).isTrue();
    assertThat(sf2.isExtern()).isFalse();

    sf2.setKind(SourceKind.EXTERN);
    assertThat(sf2.isStrong()).isFalse();
    assertThat(sf2.isWeak()).isFalse();
    assertThat(sf2.isExtern()).isTrue();
  }

  @Test
  public void testLineOffset() {
    SourceFile sf = SourceFile.fromCode("test.js", "");
    assertThat(sf.getLineOfOffset(0)).isEqualTo(1);
    assertThat(sf.getColumnOfOffset(0)).isEqualTo(0);
    assertThat(sf.getLineOfOffset(10)).isEqualTo(1);
    assertThat(sf.getColumnOfOffset(10)).isEqualTo(10);

    sf.setCode("'1';\n'2';\n'3'\n");
    assertThat(sf.getLineOffset(1)).isEqualTo(0);
    assertThat(sf.getLineOffset(2)).isEqualTo(5);
    assertThat(sf.getLineOffset(3)).isEqualTo(10);

    sf.setCode("'100';\n'200;'\n'300'\n");
    assertThat(sf.getLineOffset(1)).isEqualTo(0);
    assertThat(sf.getLineOffset(2)).isEqualTo(7);
    assertThat(sf.getLineOffset(3)).isEqualTo(14);
  }

  @Test
  public void testCachingFile() throws IOException {
    // Setup environment.
    String expectedContent = "// content content content";
    String newExpectedContent = "// new content new content new content";

    Path jsPath = folder.newFile("test.js").toPath();
    MoreFiles.asCharSink(jsPath, StandardCharsets.UTF_8).write(expectedContent);
    SourceFile sourceFile = SourceFile.fromPath(jsPath, StandardCharsets.UTF_8);

    // Verify initial state.
    assertThat(sourceFile.getCode()).isEqualTo(expectedContent);

    // Perform a change.
    MoreFiles.asCharSink(jsPath, StandardCharsets.UTF_8).write(newExpectedContent);
    sourceFile.clearCachedSource();

    // Verify final state.
    assertThat(sourceFile.getCode()).isEqualTo(newExpectedContent);
  }

  @Test
  public void testCachingZipFile() throws IOException {
    // Setup environment.
    String expectedContent = "// content content content";
    String newExpectedContent = "// new content new content new content";
    Path jsZipFile = folder.newFile("test.js.zip").toPath();
    createZipWithContent(jsZipFile, expectedContent);
    SourceFile zipSourceFile =
        SourceFile.fromZipEntry(
            jsZipFile.toString(),
            jsZipFile.toAbsolutePath().toString(),
            "foo.js",
            StandardCharsets.UTF_8);

    // Verify initial state.
    assertThat(zipSourceFile.getCode()).isEqualTo(expectedContent);

    // Perform a change.
    createZipWithContent(jsZipFile, newExpectedContent);
    // Verify cache is consistent unless cleared.
    assertThat(zipSourceFile.getCode()).isEqualTo(expectedContent);
    zipSourceFile.clearCachedSource();

    // Verify final state.
    assertThat(zipSourceFile.getCode()).isEqualTo(newExpectedContent);
  }

  @Test
  public void testSourceFileResolvesZipEntries() throws IOException {
    // Setup environment.
    String expectedContent = "// <program goes here>";
    Path jsZipPath = folder.newFile("test.js.zip").toPath();
    createZipWithContent(jsZipPath, expectedContent);

    // Test SourceFile#fromZipEntry(String, String, String, Charset, SourceKind)
    SourceFile sourceFileFromZipEntry =
        SourceFile.fromZipEntry(
            jsZipPath.toString(),
            jsZipPath.toAbsolutePath().toString(),
            "foo.js",
            StandardCharsets.UTF_8,
            SourceKind.WEAK);
    assertThat(sourceFileFromZipEntry.getCode()).isEqualTo(expectedContent);
    assertThat(sourceFileFromZipEntry.getKind()).isEqualTo(SourceKind.WEAK);

    // Test SourceFile#fromZipEntry(String, String, String, Charset)
    SourceFile sourceFileFromZipEntryDefaultKind =
        SourceFile.fromZipEntry(
            jsZipPath.toString(),
            jsZipPath.toAbsolutePath().toString(),
            "foo.js",
            StandardCharsets.UTF_8);
    assertThat(sourceFileFromZipEntryDefaultKind.getCode()).isEqualTo(expectedContent);
    assertThat(sourceFileFromZipEntryDefaultKind.getKind()).isEqualTo(SourceKind.STRONG);

    // Test SourceFile#fromFile(String)
    SourceFile sourceFileFromFileString =
        SourceFile.fromFile(jsZipPath + "!/foo.js", StandardCharsets.UTF_8);
    assertThat(sourceFileFromFileString.getCode()).isEqualTo(expectedContent);

    // Test SourceFile#fromFile(String, Charset)
    SourceFile sourceFileFromFileStringCharset =
        SourceFile.fromFile(jsZipPath + "!/foo.js", StandardCharsets.UTF_8);
    assertThat(sourceFileFromFileStringCharset.getCode()).isEqualTo(expectedContent);

    // Test SourceFile#fromPath(Path, Charset)
    Path zipEntryPath = Paths.get(jsZipPath + "!/foo.js");
    SourceFile sourceFileFromPathCharset =
        SourceFile.fromPath(zipEntryPath, StandardCharsets.UTF_8);
    assertThat(sourceFileFromPathCharset.getCode()).isEqualTo(expectedContent);
  }

  @Test
  public void testSourceFileFromZipFile() throws IOException {
    // Setup environment.
    String expectedContent = "// <program goes here>";
    Path jsZipPath = folder.newFile("test.js.zip").toPath();
    createZipWithContent(jsZipPath, "// <program goes here>");

    List<SourceFile> sourceFiles =
        SourceFile.fromZipFile(jsZipPath.toString(), StandardCharsets.UTF_8);
    assertThat(sourceFiles).hasSize(1);

    SourceFile sourceFile = Iterables.getOnlyElement(sourceFiles);
    assertThat(sourceFile.getName()).isEqualTo(jsZipPath + "!/foo.js");
    assertThat(sourceFile.getCode()).isEqualTo(expectedContent);
  }

  @Test
  public void testSourceFileFromZipInput() throws IOException {
    // Setup environment.
    String expectedContent = "// <program goes here>";
    Path jsZipPath = folder.newFile("test.js.zip").toPath();
    createZipWithContent(jsZipPath, "// <program goes here>");

    List<SourceFile> sourceFiles =
        SourceFile.fromZipInput(
            jsZipPath.toString(), new FileInputStream(jsZipPath.toFile()), StandardCharsets.UTF_8);
    assertThat(sourceFiles).hasSize(1);

    SourceFile sourceFile = Iterables.getOnlyElement(sourceFiles);
    assertThat(sourceFile.getName()).isEqualTo(jsZipPath + "!/foo.js");
    assertThat(sourceFile.getCode()).isEqualTo(expectedContent);
  }

  private static void createZipWithContent(Path zipFile, String content) throws IOException {
    Instant lastModified = Instant.now();
    if (zipFile.toFile().exists()) {
      // Ensure that file modified date is updated, otherwise could cause flakiness (b/123962282).
      lastModified = Files.getLastModifiedTime(zipFile).toInstant().plusSeconds(1);
      zipFile.toFile().delete();
    }

    zipFile.toFile().createNewFile();
    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
      zos.putNextEntry(new ZipEntry("foo.js"));
      zos.write(content.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();
    }
    Files.setLastModifiedTime(zipFile, FileTime.from(lastModified));
  }

  @Test
  public void testDiskFile() throws IOException {
    String expectedContent = "var c;";

    Path tempFile = folder.newFile("test.js").toPath();
    MoreFiles.asCharSink(tempFile, UTF_8).write(expectedContent);

    SourceFile newFile = SourceFile.fromFile(tempFile.toString());
    String actualContent;

    actualContent = newFile.getLine(1);
    assertThat(actualContent).isEqualTo(expectedContent);

    newFile.clearCachedSource();

    assertThat(newFile.getCodeNoCache()).isNull();
    actualContent = newFile.getLine(1);
    assertThat(actualContent).isEqualTo(expectedContent);
  }

  private static class CodeGeneratorHelper implements SourceFile.Generator {
    int reads = 0;

    @Override
    public String getCode() {
      reads++;
      return "var a;\n";
    }

    public int numberOfReads() {
      return reads;
    }
  }

  @Test
  public void testGeneratedFile() {
    String expectedContent = "var a;";
    CodeGeneratorHelper myGenerator = new CodeGeneratorHelper();
    SourceFile newFile = SourceFile.fromGenerator("file.js", myGenerator);
    String actualContent;

    actualContent = newFile.getLine(1);
    assertThat(actualContent).isEqualTo(expectedContent);
    assertThat(myGenerator.numberOfReads()).isEqualTo(1);

    newFile.clearCachedSource();
    assertThat(newFile.getCodeNoCache()).isNull();

    actualContent = newFile.getLine(1);
    assertThat(actualContent).isEqualTo(expectedContent);
    assertThat(myGenerator.numberOfReads()).isEqualTo(2);
  }
}
