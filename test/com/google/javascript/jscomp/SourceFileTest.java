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
import static com.google.javascript.rhino.testing.Asserts.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.Iterables;
import com.google.common.io.MoreFiles;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
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
    testLineOffsetHelper((code) -> SourceFile.fromCode("test.js", code));
  }

  @Test
  public void testLineOffset_serialization() {
    testLineOffsetHelper(
        (code) -> serializeRoundTrip(SourceFile.fromCode("test.js", code, SourceKind.NON_CODE)));
  }

  @Test
  public void testLineOffset_serialization_noReloadingSource() {
    testLineOffsetHelper(
        (code) -> {
          SourceFile inMemory = SourceFile.fromCode("test.js", code, SourceKind.NON_CODE);
          assertThat(inMemory.getLineOfOffset(0)).isEqualTo(1); // Trigger line offset generation
          SourceFile fakeOnDisk =
              SourceFile.fromFile("fake_on_disk.js", UTF_8, SourceKind.NON_CODE);

          fakeOnDisk.restoreFrom(inMemory);
          assertThat(fakeOnDisk.getLineOfOffset(0)).isEqualTo(1);

          return serializeRoundTrip(fakeOnDisk);
        });
  }

  private void testLineOffsetHelper(Function<String, SourceFile> factory) {
    SourceFile f0 = factory.apply("");
    assertThat(f0.getLineOfOffset(0)).isEqualTo(1);
    assertThat(f0.getColumnOfOffset(0)).isEqualTo(0);
    assertThat(f0.getLineOfOffset(10)).isEqualTo(1);
    assertThat(f0.getColumnOfOffset(10)).isEqualTo(10);

    SourceFile f1 = factory.apply("'1';\n'2';\n'3'\n");
    assertThat(f1.getLineOffset(1)).isEqualTo(0);
    assertThat(f1.getLineOffset(2)).isEqualTo(5);
    assertThat(f1.getLineOffset(3)).isEqualTo(10);

    SourceFile f2 = factory.apply("'100';\n'200;'\n'300'\n");
    assertThat(f2.getLineOffset(1)).isEqualTo(0);
    assertThat(f2.getLineOffset(2)).isEqualTo(7);
    assertThat(f2.getLineOffset(3)).isEqualTo(14);

    String longLine = stringOfLength(300);
    SourceFile f3 = factory.apply(longLine + "\n" + longLine + "\n" + longLine + "\n");
    assertThat(f3.getLineOffset(1)).isEqualTo(0);
    assertThat(f3.getLineOffset(2)).isEqualTo(301);
    assertThat(f3.getLineOffset(3)).isEqualTo(602);

    assertThat(f3.getLineOfOffset(0)).isEqualTo(1);
    assertThat(f3.getLineOfOffset(300)).isEqualTo(1);
    assertThat(f3.getLineOfOffset(301)).isEqualTo(2);
    assertThat(f3.getLineOfOffset(601)).isEqualTo(2);
    assertThat(f3.getLineOfOffset(602)).isEqualTo(3);
    assertThat(f3.getLineOfOffset(902)).isEqualTo(3);
    assertThat(f3.getLineOfOffset(903)).isEqualTo(4);

    // TODO(nickreid): This seems like a bug.
    assertThat(f3.getLineOfOffset(-1)).isEqualTo(0);
    assertThrows(Exception.class, () -> f3.getColumnOfOffset(-1));
  }

  @Test
  public void testCachingFile() throws IOException {
    // Setup environment.
    String expectedContent = "// content content content";
    String newExpectedContent = "// new content new content new content";

    Path jsPath = folder.newFile("test.js").toPath();
    MoreFiles.asCharSink(jsPath, UTF_8).write(expectedContent);
    SourceFile sourceFile = SourceFile.fromPath(jsPath, UTF_8);

    // Verify initial state.
    assertThat(sourceFile.getCode()).isEqualTo(expectedContent);

    // Perform a change.
    MoreFiles.asCharSink(jsPath, UTF_8).write(newExpectedContent);
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
            jsZipFile.toString(), jsZipFile.toAbsolutePath().toString(), "foo.js", UTF_8);

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
            UTF_8,
            SourceKind.WEAK);
    assertThat(sourceFileFromZipEntry.getCode()).isEqualTo(expectedContent);
    assertThat(sourceFileFromZipEntry.getKind()).isEqualTo(SourceKind.WEAK);

    // Test SourceFile#fromZipEntry(String, String, String, Charset)
    SourceFile sourceFileFromZipEntryDefaultKind =
        SourceFile.fromZipEntry(
            jsZipPath.toString(), jsZipPath.toAbsolutePath().toString(), "foo.js", UTF_8);
    assertThat(sourceFileFromZipEntryDefaultKind.getCode()).isEqualTo(expectedContent);
    assertThat(sourceFileFromZipEntryDefaultKind.getKind()).isEqualTo(SourceKind.STRONG);

    // Test SourceFile#fromFile(String)
    SourceFile sourceFileFromFileString = SourceFile.fromFile(jsZipPath + "!/foo.js", UTF_8);
    assertThat(sourceFileFromFileString.getCode()).isEqualTo(expectedContent);

    // Test SourceFile#fromFile(String, Charset)
    SourceFile sourceFileFromFileStringCharset = SourceFile.fromFile(jsZipPath + "!/foo.js", UTF_8);
    assertThat(sourceFileFromFileStringCharset.getCode()).isEqualTo(expectedContent);

    // Test SourceFile#fromPath(Path, Charset)
    Path zipEntryPath = Paths.get(jsZipPath + "!/foo.js");
    SourceFile sourceFileFromPathCharset = SourceFile.fromPath(zipEntryPath, UTF_8);
    assertThat(sourceFileFromPathCharset.getCode()).isEqualTo(expectedContent);
  }

  @Test
  public void testSourceFileFromZipFile() throws IOException {
    // Setup environment.
    String expectedContent = "// <program goes here>";
    Path jsZipPath = folder.newFile("test.js.zip").toPath();
    createZipWithContent(jsZipPath, "// <program goes here>");

    List<SourceFile> sourceFiles = SourceFile.fromZipFile(jsZipPath.toString(), UTF_8);
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
            jsZipPath.toString(), new FileInputStream(jsZipPath.toFile()), UTF_8);
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
      zos.write(content.getBytes(UTF_8));
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

    assertThat(newFile.hasSourceInMemory()).isFalse();
    actualContent = newFile.getLine(1);
    assertThat(actualContent).isEqualTo(expectedContent);
  }

  @Test
  public void testDiskFileWithOriginalPath() throws IOException {
    String expectedContent = "var c;";

    Path tempFile = folder.newFile("test.js").toPath();
    MoreFiles.asCharSink(tempFile, UTF_8).write(expectedContent);

    SourceFile newFile =
        SourceFile.builder()
            .withOriginalPath("original_test.js")
            .buildFromFile(tempFile.toString());
    String actualContent;

    actualContent = newFile.getLine(1);
    assertThat(actualContent).isEqualTo(expectedContent);

    newFile.clearCachedSource();

    assertThat(newFile.hasSourceInMemory()).isFalse();
    actualContent = newFile.getLine(1);
    assertThat(actualContent).isEqualTo(expectedContent);

    assertThat(newFile.getName()).isEqualTo("original_test.js");
  }

  @Test
  public void testCanOnlySerializeNonCode() throws IOException, ClassNotFoundException {
    SourceFile strong = SourceFile.fromCode("file.js", "var i = 0;", SourceKind.STRONG);
    SourceFile weak = SourceFile.fromCode("file.js", "var i = 0;", SourceKind.WEAK);
    SourceFile extern = SourceFile.fromCode("file.js", "var i;", SourceKind.EXTERN);
    SourceFile nonCode = SourceFile.fromCode("file.js", "var i = 0;", SourceKind.NON_CODE);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);

    // Files representing JS sources must be serialized via TypedAstSerializer.
    assertThrows(Exception.class, () -> oos.writeObject(strong));
    assertThrows(Exception.class, () -> oos.writeObject(weak));
    assertThrows(Exception.class, () -> oos.writeObject(extern));

    oos.writeObject(nonCode);
    oos.close();
  }

  @Test
  public void testFromCodeSerialization() throws IOException, ClassNotFoundException {
    SourceFile sourceFile = SourceFile.fromCode("file.js", "var i = 0;", SourceKind.NON_CODE);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(sourceFile);
    oos.close();

    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
    SourceFile afterSerialization = (SourceFile) ois.readObject();
    ois.close();
    assertThat(afterSerialization.getCode()).isEqualTo(sourceFile.getCode());
  }

  @Test
  public void testGetLines() {
    SourceFile sourceFile =
        SourceFile.fromCode("file.js", "const a = 0;\nconst b = 1;\nconst c = 2;");

    assertThat(sourceFile.getLines(1, 1).getSourceExcerpt()).isEqualTo("const a = 0;");
    assertThat(sourceFile.getLines(2, 1).getSourceExcerpt()).isEqualTo("const b = 1;");
    assertThat(sourceFile.getLines(3, 1).getSourceExcerpt()).isEqualTo("const c = 2;");

    assertThat(sourceFile.getLines(1, "const a = 0;\n".length()).getSourceExcerpt())
        .isEqualTo("const a = 0;");
    assertThat(sourceFile.getLines(1, "const a = 0;\nconst b".length()).getSourceExcerpt())
        .isEqualTo("const a = 0;\nconst b = 1;");
    assertThat(sourceFile.getLines(2, "const b = 1;\nconst c".length()).getSourceExcerpt())
        .isEqualTo("const b = 1;\nconst c = 2;");

    assertThat(sourceFile.getLines(3, "const c = 2;".length()).getSourceExcerpt())
        .isEqualTo("const c = 2;");
  }

  @Test
  public void testGetLines_invalidLengths() {
    SourceFile sourceFile =
        SourceFile.fromCode("file.js", "const a = 0;\nconst b = 1;\nconst c = 2;");

    assertThat(sourceFile.getLines(0, -3).getSourceExcerpt()).isEqualTo("const a = 0;");
    assertThat(sourceFile.getLines(0, 0).getSourceExcerpt()).isEqualTo("const a = 0;");
    assertThat(sourceFile.getLines(1, 100000).getSourceExcerpt())
        .isEqualTo("const a = 0;\nconst b = 1;\nconst c = 2;");
    assertThat(sourceFile.getLines(2, 10000).getSourceExcerpt())
        .isEqualTo("const b = 1;\nconst c = 2;");
    assertThat(sourceFile.getLines(3, 10000).getSourceExcerpt()).isEqualTo("const c = 2;");
  }

  @Test
  public void testGetLines_whenFileEndsWithNewline() {
    SourceFile sourceFile =
        SourceFile.fromCode("file.js", "const a = 0;\nconst b = 1;\nconst c = 2;\n");

    assertThat(sourceFile.getLines(1, 100000).getSourceExcerpt())
        .isEqualTo("const a = 0;\nconst b = 1;\nconst c = 2;");
    assertThat(sourceFile.getLines(2, 10000).getSourceExcerpt())
        .isEqualTo("const b = 1;\nconst c = 2;");
    assertThat(sourceFile.getLines(3, 1).getSourceExcerpt()).isEqualTo("const c = 2;");
    assertThat(sourceFile.getLines(4, 1).getSourceExcerpt()).isEmpty();
  }

  @Test
  public void testGetLines_invalidLineNumbers() {
    SourceFile sourceFile =
        SourceFile.fromCode("file.js", "const a = 0;\nconst b = 1;\nconst c = 2;");

    assertThat(sourceFile.getLines(-20, 1).getSourceExcerpt()).isEqualTo("const a = 0;");
    assertThat(sourceFile.getLines(0, 1).getSourceExcerpt()).isEqualTo("const a = 0;");
    assertThat(sourceFile.getLines(4, 1)).isNull();
  }

  @SuppressWarnings("unchecked")
  private static <T extends Serializable> T serializeRoundTrip(T t) {
    try {
      ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
      ObjectOutputStream outObjects = new ObjectOutputStream(outBytes);
      outObjects.writeObject(t);
      outObjects.close();

      ByteArrayInputStream inBytes = new ByteArrayInputStream(outBytes.toByteArray());
      ObjectInputStream inObjects = new ObjectInputStream(inBytes);
      return (T) inObjects.readObject();
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private static String stringOfLength(int length) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < length; i++) {
      builder.append('a');
    }
    return builder.toString();
  }
}
