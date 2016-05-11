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

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.CharSource;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.javascript.rhino.StaticSourceFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * An abstract representation of a source file that provides access to
 * language-neutral features. The source file can be loaded from various
 * locations, such as from disk or from a preloaded string.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public class SourceFile implements StaticSourceFile, Serializable {
  private static final long serialVersionUID = 1L;
  private static final String UTF8_BOM = "\uFEFF";

  /** A JavaScript source code provider.  The value should
   * be cached so that the source text stays consistent throughout a single
   * compile. */
  public interface Generator {
    public String getCode();
  }

  /**
   * Number of lines in the region returned by {@link #getRegion(int)}.
   * This length must be odd.
   */
  private static final int SOURCE_EXCERPT_REGION_LENGTH = 5;

  private final String fileName;
  private boolean isExternFile = false;

  // The fileName may not always identify the original file - for example,
  // supersourced Java inputs, or Java inputs that come from Jar files. This
  // is an optional field that the creator of an AST or SourceFile can set.
  // It could be a path to the original file, or in case this SourceFile came
  // from a Jar, it could be the path to the Jar.
  private String originalPath = null;

  // Source Line Information
  private int[] lineOffsets = null;

  private String code = null;

  static final DiagnosticType DUPLICATE_ZIP_CONTENTS = DiagnosticType.warning(
      "JSC_DUPLICATE_ZIP_CONTENTS",
      "Two zip entries containing the same relative path.\n"
      + "Entry 1: {0}\n"
      + "Entry 2: {1}");

  /**
   * Construct a new abstract source file.
   *
   * @param fileName The file name of the source file. It does not necessarily
   *     need to correspond to a real path. But it should be unique. Will
   *     appear in warning messages emitted by the compiler.
   */
  public SourceFile(String fileName) {
    if (isNullOrEmpty(fileName)) {
      throw new IllegalArgumentException("a source must have a name");
    }

    if (!"/".equals(File.separator)) {
      this.fileName = fileName.replace(File.separator, "/");
    } else {
      this.fileName = fileName;
    }
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

  //////////////////////////////////////////////////////////////////////////////
  // Implementation

  /**
   * Gets all the code in this source file.
   * @throws IOException
   */
  public String getCode() throws IOException {
    return code;
  }

  /**
   * Gets a char source for the code in this source file.
   */
  @GwtIncompatible("com.google.common.io.CharSource")
  public CharSource getCodeCharSource() {
    return CharSource.wrap(code);
  }

  /**
   * Gets a reader for the code in this source file.
   */
  @GwtIncompatible("java.io.Reader")
  public Reader getCodeReader() throws IOException {
    return new StringReader(getCode());
  }

  @VisibleForTesting
  String getCodeNoCache() {
    return code;
  }

  void setCode(String sourceCode) {
    this.setCode(sourceCode, false);
  }

  void setCode(String sourceCode, boolean removeUtf8Bom) {
    if (removeUtf8Bom && sourceCode != null && sourceCode.startsWith(UTF8_BOM)) {
      code = sourceCode.substring(UTF8_BOM.length());
    } else {
      code = sourceCode;
    }
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

  /** Returns whether this is an extern. */
  @Override
  public boolean isExtern() {
    return isExternFile;
  }

  /** Sets that this is an extern. */
  void setIsExtern(boolean newVal) {
    isExternFile = newVal;
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
        return js.substring(pos, js.length());
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

  @GwtIncompatible("java.util.zip.ZipFile")
  public static List<SourceFile> fromZipFile(String zipName, Charset inputCharset)
      throws IOException {
    final String absoluteZipPath = new File(zipName).getAbsolutePath();
    List<SourceFile> sourceFiles = new ArrayList<>();

    try (ZipFile zipFile = new ZipFile(absoluteZipPath)) {
      Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();

      while (zipEntries.hasMoreElements()) {
        ZipEntry zipEntry = zipEntries.nextElement();
        String entryName = zipEntry.getName();
        if (!entryName.endsWith(".js")) { // Only accept js files
          continue;
        }
        URL zipEntryUrl = new URL("jar:file:" + absoluteZipPath + "!/" + entryName);
        sourceFiles.add(
            builder()
                .withCharset(inputCharset)
                .withOriginalPath(zipName + "!/" + zipEntry.getName())
                .buildFromUrl(zipEntryUrl));
      }
    }
    return sourceFiles;
  }

  @GwtIncompatible("java.io.File")
  public static SourceFile fromFile(String fileName, Charset c) {
    return builder().withCharset(c).buildFromFile(fileName);
  }

  public static SourceFile fromFile(String fileName) {
    return builder().buildFromFile(fileName);
  }

  @GwtIncompatible("java.io.File")
  public static SourceFile fromFile(File file, Charset c) {
    return builder().withCharset(c).buildFromFile(file);
  }

  @GwtIncompatible("java.io.File")
  public static SourceFile fromFile(File file) {
    return builder().buildFromFile(file);
  }

  public static SourceFile fromCode(String fileName, String code) {
    return builder().buildFromCode(fileName, code);
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
    private Charset charset = UTF_8;
    private String originalPath = null;

    public Builder() {}

    /** Set the charset to use when reading from an input stream or file. */
    public Builder withCharset(Charset charset) {
      this.charset = charset;
      return this;
    }

    public Builder withOriginalPath(String originalPath) {
      this.originalPath = originalPath;
      return this;
    }

    public SourceFile buildFromFile(String fileName) {
      return buildFromFile(new File(fileName));
    }

    public SourceFile buildFromFile(File file) {
      return new OnDisk(file, originalPath, charset);
    }

    @GwtIncompatible("java.net.URL")
    public SourceFile buildFromUrl(URL url) {
      return new AtUrl(url, originalPath, charset);
    }

    public SourceFile buildFromCode(String fileName, String code) {
      return new Preloaded(fileName, originalPath, code);
    }

    @GwtIncompatible("java.io.InputStream")
    public SourceFile buildFromInputStream(String fileName, InputStream s)
        throws IOException {
      return buildFromCode(fileName,
          CharStreams.toString(new InputStreamReader(s, charset)));
    }

    @GwtIncompatible("java.io.Reader")
    public SourceFile buildFromReader(String fileName, Reader r)
        throws IOException {
      return buildFromCode(fileName, CharStreams.toString(r));
    }

    public SourceFile buildFromGenerator(String fileName,
        Generator generator) {
      return new Generated(fileName, originalPath, generator);
    }
  }


  //////////////////////////////////////////////////////////////////////////////
  // Implementations

  /**
   * A source file where the code has been preloaded.
   */
  static class Preloaded extends SourceFile {
    private static final long serialVersionUID = 1L;

    Preloaded(String fileName, String originalPath, String code) {
      super(fileName);
      super.setOriginalPath(originalPath);
      super.setCode(code);
    }
  }

  /**
   * A source file where the code will be dynamically generated
   * from the injected interface.
   */
  static class Generated extends SourceFile {
    private static final long serialVersionUID = 1L;
    private final Generator generator;

    // Not private, so that LazyInput can extend it.
    Generated(String fileName, String originalPath, Generator generator) {
      super(fileName);
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
  }

  /**
   * A source file where the code is only read into memory if absolutely
   * necessary. We will try to delay loading the code into memory as long as
   * possible.
   */
  static class OnDisk extends SourceFile {
    private static final long serialVersionUID = 1L;
    private final File file;

    // This is stored as a String, but passed in and out as a Charset so that
    // we can serialize the class.
    // Default input file format for the compiler has always been UTF_8.
    private String inputCharset = UTF_8.name();

    OnDisk(File file, String originalPath, Charset c) {
      super(file.getPath());
      this.file = file;
      super.setOriginalPath(originalPath);
      if (c != null) {
        this.setCharset(c);
      }
    }

    @Override
    public synchronized String getCode() throws IOException {
      String cachedCode = super.getCode();

      if (cachedCode == null) {
        cachedCode = Files.toString(file, this.getCharset());
        super.setCode(cachedCode, this.getCharset() == StandardCharsets.UTF_8);
        // Byte Order Mark can be removed by setCode
        cachedCode = super.getCode();
      }
      return cachedCode;
    }

    /**
     * Gets a char source for the code in this source file.
     */
    @Override
    @GwtIncompatible("Files.asCharSource()")
    public CharSource getCodeCharSource() {
      if (hasSourceInMemory()) {
        return super.getCodeCharSource();
      } else {
        // If we haven't pulled the code into memory yet, don't.
        return Files.asCharSource(file, StandardCharsets.UTF_8);
      }
    }

    /**
     * Gets a reader for the code in this source file.
     */
    @Override
    @GwtIncompatible("java.io.Reader")
    public Reader getCodeReader() throws IOException {
      if (hasSourceInMemory()) {
        return super.getCodeReader();
      } else {
        // If we haven't pulled the code into memory yet, don't.
        return Files.newReader(file, StandardCharsets.UTF_8);
      }
    }

    // Flush the cached code after the compile; we can read it off disk
    // if we need it again.
    @Override
    public void clearCachedSource() {
      super.setCode(null);
    }

    /**
     * Store the Charset specification as the string version of the name,
     * rather than the Charset itself.  This allows us to serialize the
     * SourceFile class.
     * @param c charset to use when reading the input.
     */
    public void setCharset(Charset c) {
      inputCharset = c.name();
    }

    /**
     * Get the Charset specifying how we're supposed to read the file
     * in off disk and into UTF-16.  This is stored as a strong to allow
     * SourceFile to be serialized.
     * @return Charset object representing charset to use.
     */
    public Charset getCharset() {
      return Charset.forName(inputCharset);
    }
  }

  /**
   * A source file at a URL where the code is only read into memory if absolutely
   * necessary. We will try to delay loading the code into memory as long as
   * possible.
   * <p>
   * In practice this is used to load code in entries inside of zip files.
   */
  @GwtIncompatible("java.net.URL")
  static class AtUrl extends SourceFile {
    private static final long serialVersionUID = 1L;
    private final URL url;

    // This is stored as a String, but passed in and out as a Charset so that
    // we can serialize the class.
    // Default input file format for the compiler has always been UTF_8.
    private String inputCharset = UTF_8.name();

    AtUrl(URL url, String originalPath, Charset c) {
      super(originalPath);
      this.url = url;
      super.setOriginalPath(originalPath);
      if (c != null) {
        this.setCharset(c);
      }
    }

    @Override
    public synchronized String getCode() throws IOException {
      String cachedCode = super.getCode();

      if (cachedCode == null) {
        cachedCode = Resources.toString(url, this.getCharset());
        super.setCode(cachedCode, this.getCharset() == StandardCharsets.UTF_8);
        // Byte Order Mark can be removed by setCode
        cachedCode = super.getCode();
      }
      return cachedCode;
    }

    /**
     * Gets a char source for the code at this URL.
     */
    @Override
    public CharSource getCodeCharSource() {
      if (hasSourceInMemory()) {
        return super.getCodeCharSource();
      } else {
        // If we haven't pulled the code into memory yet, don't.
        return Resources.asCharSource(url, StandardCharsets.UTF_8);
      }
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
        return getCodeCharSource().openStream();
      }
    }

    // Flush the cached code after the compile; we can read it from the URL
    // if we need it again.
    @Override
    public void clearCachedSource() {
      super.setCode(null);
    }

    /**
     * Store the Charset specification as the string version of the name,
     * rather than the Charset itself.  This allows us to serialize the
     * SourceFile class.
     * @param c charset to use when reading the input.
     */
    public void setCharset(Charset c) {
      inputCharset = c.name();
    }

    /**
     * Get the Charset specifying how we're supposed to read the URL
     * into UTF-16.  This is stored as a string to allow SourceFile to be
     * serialized.
     * @return Charset object representing charset to use.
     */
    public Charset getCharset() {
      return Charset.forName(inputCharset);
    }
  }
}
