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

import com.google.common.io.MoreFiles;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import junit.framework.TestCase;


public final class SourceFileTest extends TestCase {

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

  public void testLineOffset() throws Exception {
    SourceFile sf = SourceFile.fromCode("test.js", "'1';\n'2';\n'3'\n");
    assertThat(sf.getLineOffset(1)).isEqualTo(0);
    assertThat(sf.getLineOffset(2)).isEqualTo(5);
    assertThat(sf.getLineOffset(3)).isEqualTo(10);

    sf.setCode("'100';\n'200;'\n'300'\n");
    assertThat(sf.getLineOffset(1)).isEqualTo(0);
    assertThat(sf.getLineOffset(2)).isEqualTo(7);
    assertThat(sf.getLineOffset(3)).isEqualTo(14);
  }

  public void testCachingFile() throws IOException {
    // Setup environment.
    String expectedContent = "// content content content";
    String newExpectedContent = "// new content new content new content";
    Path jsPath = Files.createTempFile("test", ".js");
    MoreFiles.asCharSink(jsPath, StandardCharsets.UTF_8).write(expectedContent);
    SourceFile sourceFile = SourceFile.fromPath(jsPath, StandardCharsets.UTF_8);

    // Verify initial state.
    assertEquals(expectedContent, sourceFile.getCode());

    // Perform a change.
    MoreFiles.asCharSink(jsPath, StandardCharsets.UTF_8).write(newExpectedContent);
    sourceFile.clearCachedSource();

    // Verify final state.
    assertEquals(newExpectedContent, sourceFile.getCode());
  }

  public void testCachingZipFile() throws IOException {
    // Setup environment.
    String expectedContent = "// content content content";
    String newExpectedContent = "// new content new content new content";
    Path jsZipFile = Files.createTempFile("test", ".js.zip");
    createZipWithContent(jsZipFile, expectedContent);
    SourceFile zipSourceFile =
        SourceFile.fromZipEntry(
            jsZipFile.toString(),
            jsZipFile.toAbsolutePath().toString(),
            "foo.js",
            StandardCharsets.UTF_8);

    // Verify initial state.
    assertEquals(expectedContent, zipSourceFile.getCode());

    // Perform a change.
    createZipWithContent(jsZipFile, newExpectedContent);
    // Verify cache is consistent unless cleared.
    assertEquals(expectedContent, zipSourceFile.getCode());
    zipSourceFile.clearCachedSource();

    // Verify final state.
    assertEquals(newExpectedContent, zipSourceFile.getCode());
  }

  public void testSourceFileResolvesZipEntries() throws IOException {
    // Setup environment.
    String expectedContent = "// <program goes here>";
    Path jsZipPath = Files.createTempFile("test", ".js.zip");
    createZipWithContent(jsZipPath, expectedContent);

    // Test SourceFile#fromZipEntry(String, String, String, Charset)
    SourceFile sourceFileFromZipEntry =
        SourceFile.fromZipEntry(
            jsZipPath.toString(),
            jsZipPath.toAbsolutePath().toString(),
            "foo.js",
            StandardCharsets.UTF_8);
    assertEquals(expectedContent, sourceFileFromZipEntry.getCode());

    // Test SourceFile#fromFile(String)
    SourceFile sourceFileFromFileString =
        SourceFile.fromFile(jsZipPath + "!/foo.js", StandardCharsets.UTF_8);
    assertEquals(expectedContent, sourceFileFromFileString.getCode());

    // Test SourceFile#fromFile(String, Charset)
    SourceFile sourceFileFromFileStringCharset =
        SourceFile.fromFile(jsZipPath + "!/foo.js", StandardCharsets.UTF_8);
    assertEquals(expectedContent, sourceFileFromFileStringCharset.getCode());

    // Test SourceFile#fromPath(Path, Charset)
    Path zipEntryPath = Paths.get(jsZipPath + "!/foo.js");
    SourceFile sourceFileFromPathCharset =
        SourceFile.fromPath(zipEntryPath, StandardCharsets.UTF_8);
    assertEquals(expectedContent, sourceFileFromPathCharset.getCode());
  }

  private static void createZipWithContent(Path zipFile, String content) throws IOException {
    ZipOutputStream zos;
    if (zipFile.toFile().exists()) {
      zipFile.toFile().delete();
    }
    zipFile.toFile().createNewFile();
    zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()));
    zos.putNextEntry(new ZipEntry("foo.js"));
    zos.write(content.getBytes(StandardCharsets.UTF_8));
    zos.closeEntry();
    zos.close();
  }
}
