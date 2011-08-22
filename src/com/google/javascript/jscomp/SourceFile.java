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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.javascript.rhino.jstype.StaticSourceFile;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.nio.charset.Charset;

/**
 * An abstract representation of a source file that provides access to
 * language-neutral features. The source file can be loaded from various
 * locations, such as from disk or from a preloaded string.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public class SourceFile implements StaticSourceFile, Serializable {
  private static final long serialVersionUID = 1L;

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

  // Remember the offset for the previous line query.  If the next line
  // is after this point, we can start scanning at the previous offset rather
  // than starting at the beginning of the file.
  private int lastOffset;
  private int lastLine;

  private String code = null;

  /**
   * Construct a new abstract source file.
   *
   * @param fileName The file name of the source file. It does not necessarily
   *     need to correspond to a real path. But it should be unique. Will
   *     appear in warning messages emitted by the compiler.
   */
  public SourceFile(String fileName) {
    if (fileName == null || fileName.isEmpty()) {
      throw new IllegalArgumentException("a source must have a name");
    }
    this.fileName = fileName;
    // Starting point: offset 0 is at line 1.
    this.lastOffset = 0;
    this.lastLine = 1;
  }

  @Override
  public int getLineOffset(int lineno) {
    if (lineOffsets == null) {
      findLineOffsets();
    }
    if (lineno < 1 || lineno > lineOffsets.length) {
      throw new IllegalArgumentException(
          "Expected line number between 1 and " + lineOffsets.length +
          "\nActual: " + lineno);
    }
    return lineOffsets[lineno - 1];
  }

  /** @return The number of lines in this source file. */
  int getNumLines() {
    if (lineOffsets == null) {
      findLineOffsets();
    }
    return lineOffsets.length;
  }

  private void findLineOffsets() {
    try {
      String[] sourceLines = getCode().split("\n");
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
   * Gets a reader for the code in this source file.
   */
  public Reader getCodeReader() throws IOException {
    return new StringReader(getCode());
  }

  @VisibleForTesting
  String getCodeNoCache() {
    return code;
  }

  private void setCode(String sourceCode) {
    code = sourceCode;
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

  /**
   * Gets the source line for the indicated line number.
   *
   * @param lineNumber the line number, 1 being the first line of the file.
   * @return The line indicated. Does not include the newline at the end
   *     of the file. Returns {@code null} if it does not exist,
   *     or if there was an IO exception.
   */
  public String getLine(int lineNumber) {
    String js = "";
    try {
      // NOTE(nicksantos): Right now, this is optimized for few warnings.
      // This is probably the right trade-off, but will be slow if there
      // are lots of warnings in one file.
      js = getCode();
    } catch (IOException e) {
      return null;
    }

    int pos = 0;
    int startLine = 1;

    // If we've saved a previous offset and it's for a line less than the
    // one we're searching for, then start at that point.
    if (lineNumber >= lastLine) {
      pos = lastOffset;
      startLine = lastLine;
    }

    for (int n = startLine; n < lineNumber; n++) {
      int nextpos = js.indexOf('\n', pos);
      if (nextpos == -1) {
        return null;
      }
      pos = nextpos + 1;
    }

    // Remember this offset for the next search we do.
    lastOffset = pos;
    lastLine = lineNumber;

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

  public static SourceFile fromFile(String fileName, Charset c) {
    return fromFile(new File(fileName), c);
  }

  public static SourceFile fromFile(String fileName) {
    return fromFile(new File(fileName));
  }

  public static SourceFile fromFile(File file, Charset c) {
    return new OnDisk(file, c);
  }

  public static SourceFile fromFile(File file) {
    return new OnDisk(file);
  }

  public static SourceFile fromCode(String fileName, String code) {
    return new Preloaded(fileName, code);
  }

  public static SourceFile fromCode(String fileName,
      String originalPath, String code) {
    return new Preloaded(fileName, originalPath, code);
  }

  public static SourceFile fromInputStream(String fileName, InputStream s)
      throws IOException {
    return fromCode(fileName,
        CharStreams.toString(new InputStreamReader(s, Charsets.UTF_8)));
  }

  public static SourceFile fromInputStream(String fileName,
      String originalPath, InputStream s) throws IOException {
    return fromCode(fileName, originalPath,
        CharStreams.toString(new InputStreamReader(s, Charsets.UTF_8)));
  }

  public static SourceFile fromReader(String fileName, Reader r)
      throws IOException {
    return fromCode(fileName, CharStreams.toString(r));
  }

  public static SourceFile fromGenerator(String fileName,
      Generator generator) {
    return new Generated(fileName, generator);
  }


  //////////////////////////////////////////////////////////////////////////////
  // Implementations

  /**
   * A source file where the code has been preloaded.
   */
  static class Preloaded extends SourceFile {
    private static final long serialVersionUID = 1L;

    Preloaded(String fileName, String code) {
      this(fileName, fileName, code);
    }

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
    Generated(String fileName, Generator generator) {
      super(fileName);
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
    // Default input file format for JSCompiler has always been UTF_8.
    protected String inputCharset = Charsets.UTF_8.name();

    OnDisk(File file, Charset c) {
      this(file);
      if (c != null) {
        this.setCharset(c);
      }
    }

    // No Charset provided?
    OnDisk(File file) {
      super(file.getPath());
      this.file = file;
    }

    @Override
    public synchronized String getCode() throws IOException {
      String cachedCode = super.getCode();

      if (cachedCode == null) {
        cachedCode = Files.toString(file, this.getCharset());
        super.setCode(cachedCode);
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
        return new FileReader(file);
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
}
