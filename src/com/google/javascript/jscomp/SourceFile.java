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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.io.CharStreams;
import com.google.javascript.rhino.StaticSourceFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * An abstract representation of a source file that provides access to language-neutral features.
 * The source file can be loaded from various locations, such as from disk or from a preloaded
 * string.
 */
public class SourceFile implements StaticSourceFile, Serializable {

  private static final long serialVersionUID = 1L;
  private static final String UTF8_BOM = "\uFEFF";

  /** A JavaScript source code provider.  The value should
   * be cached so that the source text stays consistent throughout a single
   * compile. */
  public interface Generator {
    String getCode();
  }

  /**
   * Number of lines in the region returned by {@link #getRegion(int)}.
   * This length must be odd.
   */
  private static final int SOURCE_EXCERPT_REGION_LENGTH = 5;

  private final String fileName;
  private SourceKind kind;

  // The fileName may not always identify the original file - for example,
  // supersourced Java inputs, or Java inputs that come from Jar files. This
  // is an optional field that the creator of an AST or SourceFile can set.
  // It could be a path to the original file, or in case this SourceFile came
  // from a Jar, it could be the path to the Jar.
  private String originalPath = null;

  // Source Line Information
  private transient int[] lineOffsets = null;

  private transient String code = null;

  /**
   * Construct a new abstract source file.
   *
   * @param fileName The file name of the source file. It does not necessarily need to correspond to
   *     a real path. But it should be unique. Will appear in warning messages emitted by the
   *     compiler.
   * @param kind The source kind.
   */
  public SourceFile(String fileName, SourceKind kind) {
    if (isNullOrEmpty(fileName)) {
      throw new IllegalArgumentException("a source must have a name");
    }

    if (!"/".equals(File.separator)) {
      this.fileName = fileName.replace(File.separator, "/");
    } else {
      this.fileName = fileName;
    }

    this.kind = kind;
  }

  @Override
  public int getLineOffset(int lineno) {
    findLineOffsets();
    if (lineno < 1 || lineno > lineOffsets.length) {
      throw new IllegalArgumentException(
          "Expected line number between 1 and " + lineOffsets.length +
          "\nActual: " + lineno);
    }
    return lineOffsets[lineno - 1];
  }

  /** @return The number of lines in this source file. */
  int getNumLines() {
    findLineOffsets();
    return lineOffsets.length;
  }

  private void findLineOffsets() {
    if (lineOffsets != null) {
      return;
    }
    try {
      String[] sourceLines = getCode().split("\n", -1);
      lineOffsets = new int[sourceLines.length];
      for (int ii = 1; ii < sourceLines.length; ++ii) {
        lineOffsets[ii] =
            lineOffsets[ii - 1] + sourceLines[ii - 1].length() + 1;
      }
    } catch (IOException e) {
      lineOffsets = new int[1];
      lineOffsets[0] = 0;
    }
  }

  private void resetLineOffsets() {
    lineOffsets = null;
  }

  /**
   * Gets all the code in this source file.
   * @throws IOException
   */
  public String getCode() throws IOException {
    return code;
  }

  /**
   * Gets a reader for the code in this source file.
   */
  @GwtIncompatible("java.io.Reader")
  public Reader getCodeReader() throws IOException {
    return new StringReader(getCode());
  }

  void setCode(String sourceCode) {
    code =
        sourceCode != null && sourceCode.startsWith(UTF8_BOM)
            ? sourceCode.substring(UTF8_BOM.length())
            : sourceCode;
    resetLineOffsets();
  }

  public String getOriginalPath() {
    return originalPath != null ? originalPath : fileName;
  }

  public void setOriginalPath(String originalPath) {
    this.originalPath = originalPath;
  }

  // For SourceFile types which cache source code that can be regenerated
  // easily, flush the cache.  We maintain the cache mostly to speed up
  // generating source when displaying error messages, so dumping the file
  // contents after the compile is a fine thing to do.
  public void clearCachedSource() {
    // By default, do nothing.  Not all kinds of SourceFiles can regenerate
    // code.
  }

  boolean hasSourceInMemory() {
    return code != null;
  }

  /** Returns a unique name for the source file. */
  @Override
  public String getName() {
    return fileName;
  }

  /** Returns the source kind. */
  @Override
  public SourceKind getKind() {
    return kind;
  }

  /** Sets the source kind. */
  public void setKind(SourceKind kind) {
    this.kind = kind;
  }

  @Override
  public int getLineOfOffset(int offset) {
    findLineOffsets();
    int search = Arrays.binarySearch(lineOffsets, offset);
    if (search >= 0) {
      return search + 1; // lines are 1-based.
    } else {
      int insertionPoint = -1 * (search + 1);
      return Math.min(insertionPoint - 1, lineOffsets.length - 1) + 1;
    }
  }

  @Override
  public int getColumnOfOffset(int offset) {
    int line = getLineOfOffset(offset);
    return offset - lineOffsets[line - 1];
  }

  /**
   * Gets the source line for the indicated line number.
   *
   * @param lineNumber the line number, 1 being the first line of the file.
   * @return The line indicated. Does not include the newline at the end
   *     of the file. Returns {@code null} if it does not exist,
   *     or if there was an IO exception.
   */
  public String getLine(int lineNumber) {
    findLineOffsets();
    if (lineNumber > lineOffsets.length) {
      return null;
    }

    if (lineNumber < 1) {
      lineNumber = 1;
    }

    int pos = lineOffsets[lineNumber - 1];
    String js = "";
    try {
      // NOTE(nicksantos): Right now, this is optimized for few warnings.
      // This is probably the right trade-off, but will be slow if there
      // are lots of warnings in one file.
      js = getCode();
    } catch (IOException e) {
      return null;
    }

    if (js.indexOf('\n', pos) == -1) {
      // If next new line cannot be found, there are two cases
      // 1. pos already reaches the end of file, then null should be returned
      // 2. otherwise, return the contents between pos and the end of file.
      if (pos >= js.length()) {
        return null;
      } else {
        return js.substring(pos);
      }
    } else {
      return js.substring(pos, js.indexOf('\n', pos));
    }
  }

  /**
   * Get a region around the indicated line number. The exact definition of a
   * region is implementation specific, but it must contain the line indicated
   * by the line number. A region must not start or end by a carriage return.
   *
   * @param lineNumber the line number, 1 being the first line of the file.
   * @return The line indicated. Returns {@code null} if it does not exist,
   *     or if there was an IO exception.
   */
  public Region getRegion(int lineNumber) {
    String js = "";
    try {
      js = getCode();
    } catch (IOException e) {
      return null;
    }
    int pos = 0;
    int startLine = Math.max(1,
        lineNumber - (SOURCE_EXCERPT_REGION_LENGTH + 1) / 2 + 1);
    for (int n = 1; n < startLine; n++) {
      int nextpos = js.indexOf('\n', pos);
      if (nextpos == -1) {
        break;
      }
      pos = nextpos + 1;
    }
    int end = pos;
    int endLine = startLine;
    for (int n = 0; n < SOURCE_EXCERPT_REGION_LENGTH; n++, endLine++) {
      end = js.indexOf('\n', end);
      if (end == -1) {
        break;
      }
      end++;
    }
    if (lineNumber >= endLine) {
      return null;
    }
    if (end == -1) {
      int last = js.length() - 1;
      if (js.charAt(last) == '\n') {
        return
            new SimpleRegion(startLine, endLine, js.substring(pos, last));
      } else {
        return new SimpleRegion(startLine, endLine, js.substring(pos));
      }
    } else {
      return new SimpleRegion(startLine, endLine, js.substring(pos, end));
    }
  }

  @Override
  public String toString() {
    return fileName;
  }

  @GwtIncompatible("fromZipInput")
  public static List<SourceFile> fromZipFile(String zipName, Charset inputCharset)
      throws IOException {
    try (InputStream input = new FileInputStream(zipName)) {
      return fromZipInput(zipName, input, inputCharset);
    }
  }

  @GwtIncompatible("java.util.zip.ZipInputStream")
  public static List<SourceFile> fromZipInput(
      String zipName, InputStream input, Charset inputCharset) throws IOException {
    final String absoluteZipPath = new File(zipName).getAbsolutePath();
    List<SourceFile> sourceFiles = new ArrayList<>();

    try (ZipInputStream in = new ZipInputStream(input, inputCharset)) {
      ZipEntry zipEntry;
      while ((zipEntry = in.getNextEntry()) != null) {
        String entryName = zipEntry.getName();
        if (!entryName.endsWith(".js")) { // Only accept js files
          continue;
        }
        sourceFiles.add(fromZipEntry(zipName, absoluteZipPath, entryName, inputCharset));
      }
    }
    return sourceFiles;
  }

  private static final String BANG_SLASH = "!/";

  private static boolean isZipEntry(String path) {
    return path.contains(".zip!" + File.separator)
        && (path.endsWith(".js") || path.endsWith(".js.map"));
  }

  @GwtIncompatible("java.io.File")
  private static SourceFile fromZipEntry(String zipURL, Charset inputCharset, SourceKind kind) {
    checkArgument(isZipEntry(zipURL));
    String[] components = zipURL.split(Pattern.quote(BANG_SLASH.replace("/", File.separator)));
    try {
      String zipPath = components[0];
      String relativePath = components[1];
      return fromZipEntry(zipPath, zipPath, relativePath, inputCharset, kind);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  @GwtIncompatible("java.io.File")
  public static SourceFile fromZipEntry(
      String originalZipPath, String absoluteZipPath, String entryPath, Charset inputCharset)
      throws MalformedURLException {
    return fromZipEntry(
        originalZipPath, absoluteZipPath, entryPath, inputCharset, SourceKind.STRONG);
  }

  @GwtIncompatible("java.io.File")
  public static SourceFile fromZipEntry(
      String originalZipPath,
      String absoluteZipPath,
      String entryPath,
      Charset inputCharset,
      SourceKind kind)
      throws MalformedURLException {
    // No longer throws MalformedURLException but we are keeping it for backward compatibility.
    return builder()
        .withKind(kind)
        .withCharset(inputCharset)
        .withOriginalPath(originalZipPath + BANG_SLASH + entryPath)
        .buildFromZipEntry(
            new ZipEntryReader(absoluteZipPath, entryPath.replace(File.separator, "/")));
  }

  @GwtIncompatible("java.io.File")
  public static SourceFile fromFile(String fileName, Charset charset, SourceKind kind) {
    return builder().withKind(kind).withCharset(charset).buildFromFile(fileName);
  }

  @GwtIncompatible("java.io.File")
  public static SourceFile fromFile(String fileName, Charset charset) {
    return fromFile(fileName, charset, SourceKind.STRONG);
  }

  @GwtIncompatible("java.io.File")
  public static SourceFile fromFile(String fileName) {
    return fromFile(fileName, UTF_8);
  }

  @GwtIncompatible("java.io.File")
  public static SourceFile fromPath(Path path, Charset charset, SourceKind kind) {
    return builder().withKind(kind).withCharset(charset).buildFromPath(path);
  }

  @GwtIncompatible("java.io.File")
  public static SourceFile fromPath(Path path, Charset charset) {
    return fromPath(path, charset, SourceKind.STRONG);
  }

  public static SourceFile fromCode(String fileName, String code, SourceKind kind) {
    return builder().withKind(kind).buildFromCode(fileName, code);
  }

  public static SourceFile fromCode(String fileName, String code) {
    return fromCode(fileName, code, SourceKind.STRONG);
  }

  /**
   * @deprecated Use {@link #fromInputStream(String, InputStream, Charset)}
   */
  @Deprecated
  @GwtIncompatible("java.io.InputStream")
  public static SourceFile fromInputStream(String fileName, InputStream s)
      throws IOException {
    return builder().buildFromInputStream(fileName, s);
  }

  @GwtIncompatible("java.io.InputStream")
  public static SourceFile fromInputStream(String fileName, InputStream s,
      Charset charset) throws IOException {
    return builder().withCharset(charset).buildFromInputStream(fileName, s);
  }

  @GwtIncompatible("java.io.Reader")
  public static SourceFile fromReader(String fileName, Reader r)
      throws IOException {
    return builder().buildFromReader(fileName, r);
  }

  public static SourceFile fromGenerator(String fileName,
      Generator generator) {
    return builder().buildFromGenerator(fileName, generator);
  }

  /** Create a new builder for source files. */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * A builder interface for source files.
   *
   * Allows users to customize the Charset, and the original path of
   * the source file (if it differs from the path on disk).
   */
  public static class Builder {
    private SourceKind kind = SourceKind.STRONG;
    private Charset charset = UTF_8;
    private String originalPath = null;

    public Builder() {}

    /** Set the source kind. */
    public Builder withKind(SourceKind kind) {
      this.kind = kind;
      return this;
    }

    /** Set the charset to use when reading from an input stream or file. */
    public Builder withCharset(Charset charset) {
      this.charset = charset;
      return this;
    }

    public Builder withOriginalPath(String originalPath) {
      this.originalPath = originalPath;
      return this;
    }

    @GwtIncompatible("java.io.File")
    public SourceFile buildFromFile(String fileName) {
      return buildFromPath(Paths.get(fileName));
    }

    @GwtIncompatible("java.io.File")
    public SourceFile buildFromPath(Path path) {
      checkNotNull(path);
      checkNotNull(charset);
      if (isZipEntry(path.toString())) {
        return fromZipEntry(path.toString(), charset, kind);
      }
      return new OnDisk(path, originalPath, charset, kind);
    }

    @GwtIncompatible("java.io.File")
    public SourceFile buildFromZipEntry(ZipEntryReader zipEntryReader) {
      checkNotNull(zipEntryReader);
      checkNotNull(charset);
      return new SourceFile.AtZip(zipEntryReader, originalPath, charset, kind);
    }

    public SourceFile buildFromCode(String fileName, String code) {
      return new Preloaded(fileName, originalPath, code, kind);
    }

    @GwtIncompatible("java.io.InputStream")
    public SourceFile buildFromInputStream(String fileName, InputStream s) throws IOException {
      return buildFromCode(fileName, CharStreams.toString(new InputStreamReader(s, charset)));
    }

    @GwtIncompatible("java.io.Reader")
    public SourceFile buildFromReader(String fileName, Reader r) throws IOException {
      return buildFromCode(fileName, CharStreams.toString(r));
    }

    public SourceFile buildFromGenerator(String fileName, Generator generator) {
      return new Generated(fileName, originalPath, generator, kind);
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  // Implementations

  /** A source file where the code has been preloaded. */
  private static class Preloaded extends SourceFile {
    private static final long serialVersionUID = 2L;

    Preloaded(String fileName, String originalPath, String code, SourceKind kind) {
      super(fileName, kind);
      super.setOriginalPath(originalPath);
      super.setCode(code);
    }

    @GwtIncompatible("ObjectOutputStream")
    private void writeObject(java.io.ObjectOutputStream os) throws Exception {
      os.defaultWriteObject();
      os.writeObject(getCode());
    }

    @GwtIncompatible("ObjectInputStream")
    private void readObject(java.io.ObjectInputStream in) throws Exception {
      in.defaultReadObject();
      super.setCode((String) in.readObject());
    }
  }

  /** A source file where the code will be dynamically generated from the injected interface. */
  private static class Generated extends SourceFile {
    // Avoid serializing generator and remove the burden to make classes that implement
    // Generator serializable. There should be no need to obtain generated source in the
    // second stage of compilation. Making the generator transient relies on not clearing the
    // code cache for these classes up serialization which might be quite wasteful.
    private transient Generator generator;

    // Not private, so that LazyInput can extend it.
    Generated(String fileName, String originalPath, Generator generator, SourceKind kind) {
      super(fileName, kind);
      super.setOriginalPath(originalPath);
      this.generator = generator;
    }

    @Override
    public synchronized String getCode() throws IOException {
      String cachedCode = super.getCode();

      if (cachedCode == null) {
        cachedCode = generator.getCode();
        super.setCode(cachedCode);
      }
      return cachedCode;
    }

    // Clear out the generated code when finished with a compile; we can
    // regenerate it if we ever need it again.
    @Override
    public void clearCachedSource() {
      super.setCode(null);
    }

    @Override
    public void restoreFrom(SourceFile sourceFile) {
      super.restoreFrom(sourceFile);
      this.generator = ((Generated) sourceFile).generator;
    }
  }

  /**
   * A source file where the code is only read into memory if absolutely necessary. We will try to
   * delay loading the code into memory as long as possible.
   */
  @GwtIncompatible("com.google.common.io.CharStreams")
  private static class OnDisk extends SourceFile {
    private static final long serialVersionUID = 1L;
    private transient Path path;
    private transient Charset inputCharset;

    OnDisk(Path path, String originalPath, Charset c, SourceKind kind) {
      super(path.toString(), kind);
      this.path = path;
      this.inputCharset = c;
      setOriginalPath(originalPath);
    }

    @Override
    public synchronized String getCode() throws IOException {
      String cachedCode = super.getCode();

      if (cachedCode == null) {
        try (Reader r = getCodeReader()) {
          cachedCode = CharStreams.toString(r);
        } catch (java.nio.charset.MalformedInputException e) {
          throw new IOException("Failed to read: " + path + ", is this input UTF-8 encoded?", e);
        }

        super.setCode(cachedCode);
        // Byte Order Mark can be removed by setCode
        cachedCode = super.getCode();
      }
      return cachedCode;
    }

    /**
     * Gets a reader for the code in this source file.
     */
    @Override
    public Reader getCodeReader() throws IOException {
      if (hasSourceInMemory()) {
        return super.getCodeReader();
      } else {
        // If we haven't pulled the code into memory yet, don't.
        return Files.newBufferedReader(path, inputCharset);
      }
    }

    // Flush the cached code after the compile; we can read it off disk
    // if we need it again.
    @Override
    public void clearCachedSource() {
      super.setCode(null);
    }

    @GwtIncompatible("ObjectOutputStream")
    private void writeObject(java.io.ObjectOutputStream out) throws Exception {
      out.defaultWriteObject();
      out.writeObject(inputCharset.name());
      out.writeObject(path.toUri());
    }

    @GwtIncompatible("ObjectInputStream")
    private void readObject(java.io.ObjectInputStream in) throws Exception {
      in.defaultReadObject();
      inputCharset = Charset.forName((String) in.readObject());
      path = Paths.get((URI) in.readObject());

      // Code will be reread or restored.
      super.setCode(null);
    }
  }

  /**
   * A source file at a zip where the code is only read into memory if absolutely necessary. We will
   * try to delay loading the code into memory as long as possible.
   */
  @GwtIncompatible("java.io.File")
  private static class AtZip extends SourceFile {
    private static final long serialVersionUID = 1L;
    private final ZipEntryReader zipEntryReader;
    private transient Charset inputCharset;

    AtZip(ZipEntryReader zipEntryReader, String originalPath, Charset c, SourceKind kind) {
      super(originalPath, kind);
      this.inputCharset = c;
      this.zipEntryReader = zipEntryReader;
      setOriginalPath(originalPath);
    }

    @Override
    public synchronized String getCode() throws IOException {
      String cachedCode = super.getCode();

      if (cachedCode == null) {
        cachedCode = zipEntryReader.read(inputCharset);
        super.setCode(cachedCode);
        // Byte Order Mark can be removed by setCode
        cachedCode = super.getCode();
      }
      return cachedCode;
    }

    /**
     * Gets a reader for the code at this URL.
     */
    @Override
    public Reader getCodeReader() throws IOException {
      if (hasSourceInMemory()) {
        return super.getCodeReader();
      } else {
        // If we haven't pulled the code into memory yet, don't.
        return zipEntryReader.getReader(inputCharset);
      }
    }

    // Flush the cached code after the compile; we can read it from the URL
    // if we need it again.
    @Override
    public void clearCachedSource() {
      super.setCode(null);
    }

    @GwtIncompatible("ObjectOutputStream")
    private void writeObject(java.io.ObjectOutputStream os) throws Exception {
      os.defaultWriteObject();
      os.writeObject(inputCharset.name());
    }

    @GwtIncompatible("ObjectInputStream")
    private void readObject(java.io.ObjectInputStream in) throws Exception {
      in.defaultReadObject();
      inputCharset = Charset.forName((String) in.readObject());

      // Code will be reread or restored.
      super.setCode(null);
    }
  }

  public void restoreFrom(SourceFile sourceFile) {
    this.code = sourceFile.code;
  }

  @GwtIncompatible("ObjectInputStream")
  private void readObject(java.io.ObjectInputStream in) throws Exception {
    in.defaultReadObject();
    code = "<UNAVAILABLE>";
  }
}
