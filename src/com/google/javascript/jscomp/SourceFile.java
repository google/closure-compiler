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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.io.CharStreams;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.InlineMe;
import com.google.javascript.jscomp.serialization.SourceFileProto;
import com.google.javascript.jscomp.serialization.SourceFileProto.FileOnDisk;
import com.google.javascript.jscomp.serialization.SourceFileProto.ZipEntryOnDisk;
import com.google.javascript.rhino.StaticSourceFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jspecify.nullness.Nullable;

/**
 * An abstract representation of a source file that provides access to language-neutral features.
 *
 * <p>The source file can be loaded from various locations, such as from disk or from a preloaded
 * string. Loading is done as lazily as possible to minimize IO delays and memory cost of source
 * text.
 */
public final class SourceFile implements StaticSourceFile {
  private static final String UTF8_BOM = "\uFEFF";

  private static final String BANG_SLASH = "!" + Platform.getFileSeperator();

  /** Number of lines in the region returned by {@link #getRegion(int)}. This length must be odd. */
  private static final int SOURCE_EXCERPT_REGION_LENGTH = 5;

  /**
   * The file name of the source file.
   *
   * <p>It does not necessarily need to correspond to a real path. But it should be unique. Will
   * appear in warning messages emitted by the compiler.
   */
  private final String fileName;

  private SourceKind kind;

  private final CodeLoader loader;

  // Source Line Information
  private int @Nullable [] lineOffsets = null;

  private volatile @Nullable String code = null;

  // Code statistics used for metrics.
  // For both of these, a negative value means we haven't counted yet.
  private int numLines = -1;
  private int numBytes = -1;

  private SourceFile(CodeLoader loader, String fileName, SourceKind kind) {
    if (isNullOrEmpty(fileName)) {
      throw new IllegalArgumentException("a source must have a name");
    }

    if (!"/".equals(Platform.getFileSeperator())) {
      fileName = fileName.replace(Platform.getFileSeperator(), "/");
    }

    this.loader = loader;
    this.fileName = fileName;
    this.kind = kind;
  }

  @Override
  public int getLineOffset(int lineno) {
    findLineOffsets();
    if (lineno < 1 || lineno > lineOffsets.length) {
      throw new IllegalArgumentException(
          "Expected line number between 1 and " + lineOffsets.length + "\nActual: " + lineno);
    }
    return lineOffsets[lineno - 1];
  }

  /**
   * Returns the number of lines in the source file.
   *
   * <p>NOTE: this may be stale if the SourceFile has been {@link #clearCachedSource cleared} and
   * the file has changed on disk. If being fully accurate is required call {@link #getCode} and
   * then call this method.
   */
  int getNumLines() {
    if (numLines < 0) {
      // A negative value means we need to read in the code and calculate this information.
      // Otherwise, assume the file hasn't changed since we read it.
      try {
        var unused = getCode();
      } catch (IOException e) {
        // this is consistent with old behavior of this method.
        return 0;
      }
    }
    return numLines;
  }

  /**
   * Returns the number of bytes in the source file
   *
   * <p>Oddly, bytes is defined as 'length of code as a Java String' this is accurate assuming files
   * only use Latin1 characters but may be inaccurate outside of that.
   *
   * <p>NOTE: this may be stale if the SourceFile has been {@link #clearCachedSource cleared} and
   * the file has changed on disk. If being fully accurate is required call {@link #getCode} and
   * then call this method.
   */
  int getNumBytes() {
    if (numBytes < 0) {
      // A negative value means we need to read in the code and calculate this information.
      // Otherwise, assume the file hasn't changed since we read it.
      try {
        var unused = getCode();
      } catch (IOException e) {
        // this is consistent with old behavior of this method.
        return 0;
      }
    }
    return numBytes;
  }

  private void findLineOffsets() {
    if (this.lineOffsets != null) {
      return;
    }

    String localCode = this.code;
    if (localCode == null) {
      try {
        localCode = this.getCode();
      } catch (IOException e) {
        localCode = "";
        this.lineOffsets = new int[1];
        return;
      }
    }

    // getCode() updates numLines, so this is always in sync with the code.
    int[] offsets = new int[this.numLines];
    int index = 1; // start at 1 since the offset for line 0 is always at byte 0
    int offset = 0;
    while ((offset = localCode.indexOf('\n', offset)) != -1) {
      // +1 because this is the offset of the next line which is one past the newline
      offset++;
      offsets[index++] = offset;
    }
    checkState(index == offsets.length);
    this.lineOffsets = offsets;
  }

  /** Gets all the code in this source file. */
  public final String getCode() throws IOException {
    if (this.code == null) {
      // Only synchronize if we need to
      synchronized (this) {
        // Make sure another thread hasn't loaded the code while we waited.
        if (this.code == null) {
          this.setCodeAndDoBookkeeping(this.loader.loadUncachedCode());
        }
      }
    }

    return this.code;
  }

  @Deprecated
  final void setCodeDeprecated(String code) {
    this.setCodeAndDoBookkeeping(code);
  }

  /** Gets a reader for the code in this source file. */
  @GwtIncompatible("java.io.Reader")
  public Reader getCodeReader() throws IOException {
    // Only synchronize if we need to
    if (this.code == null) {
      synchronized (this) {
        // Make sure another thread hasn't loaded the code while we waited.
        if (this.code == null) {
          Reader uncachedReader = this.loader.openUncachedReader();
          if (uncachedReader != null) {
            return uncachedReader;
          }
        }
      }
    }

    return new StringReader(this.getCode());
  }

  private void setCodeAndDoBookkeeping(@Nullable String sourceCode) {
    this.code = null;
    // Force recalculation of all of these values when they are requested.
    this.lineOffsets = null;

    if (sourceCode != null) {
      if (sourceCode.startsWith(UTF8_BOM)) {
        sourceCode = sourceCode.substring(UTF8_BOM.length());
      }

      this.code = sourceCode;
      // Update numLines and numBytes
      // NOTE: In some edit/refresh development workflows, we may end up re-reading the file
      //       and getting different numbers than we got last time we read it, so we should
      //       not have checkState() calls here to assert they have not changed.
      // Misleading variable name.  This really stores the 'number of utf16' code points which is
      // not the same as number of bytes.
      this.numBytes = sourceCode.length();
      int numLines = 1; // there is always at least one line
      int index = 0;
      while ((index = sourceCode.indexOf('\n', index)) != -1) {
        index++;
        numLines++;
      }
      this.numLines = numLines;
    }
  }

  /**
   * @deprecated alias of {@link #getName()}. Use that instead
   */
  @InlineMe(replacement = "this.getName()")
  @Deprecated
  public String getOriginalPath() {
    return this.getName();
  }

  /**
   * For SourceFile types which cache source code that can be regenerated easily, flush the cache.
   *
   * <p>We maintain the cache mostly to speed up generating source when displaying error messages,
   * so dumping the file contents after the compile is a fine thing to do.
   */
  public void clearCachedSource() {
    this.setCodeAndDoBookkeeping(null);
  }

  boolean hasSourceInMemory() {
    return code != null;
  }

  /**
   * Returns a unique name for the source file.
   *
   * <p>This name is not required to be an actual file path on disk.
   */
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
      return min(insertionPoint - 1, lineOffsets.length - 1) + 1;
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
   * @return The line indicated. Does not include the newline at the end of the file. Returns {@code
   *     null} if it does not exist, or if there was an IO exception.
   */
  public @Nullable String getLine(int lineNumber) {
    String js;
    try {
      js = getCode();
    } catch (IOException e) {
      return null;
    }
    findLineOffsets();
    if (lineNumber > lineOffsets.length) {
      return null;
    }

    if (lineNumber < 1) {
      lineNumber = 1;
    }

    int pos = lineOffsets[lineNumber - 1];

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
   * Gets the source lines starting at `lineNumber` and continuing until `length`. Omits any
   * trailing newlines.
   *
   * @param lineNumber the line number, 1 being the first line of the file.
   * @param length the number of characters desired, starting at the 0th character of the specified
   *     line. If negative or 0, returns a single line.
   * @return The line(s) indicated. Returns {@code null} if it does not exist or if there was an IO
   *     exception.
   */
  public @Nullable Region getLines(int lineNumber, int length) {
    String js;
    try {
      js = getCode();
    } catch (IOException e) {
      return null;
    }
    findLineOffsets();
    if (lineNumber > lineOffsets.length) {
      return null;
    }

    if (lineNumber < 1) {
      lineNumber = 1;
    }
    if (length <= 0) {
      length = 1;
    }

    int pos = lineOffsets[lineNumber - 1];
    if (pos == js.length()) {
      return new SimpleRegion(
          lineNumber, lineNumber, ""); // Happens when asking for the last empty line in a file.
    }
    int endChar = pos;
    int endLine = lineNumber;
    // go through lines until we've reached the end of the file or met the specified length.
    for (; endChar < pos + length && endLine <= lineOffsets.length; endLine++) {
      endChar = (endLine < lineOffsets.length) ? lineOffsets[endLine] : js.length();
    }

    if (js.charAt(endChar - 1) == '\n') {
      return new SimpleRegion(lineNumber, endLine, js.substring(pos, endChar - 1));
    }
    return new SimpleRegion(lineNumber, endLine, js.substring(pos, endChar));
  }

  /**
   * Get a region around the indicated line number. The exact definition of a region is
   * implementation specific, but it must contain the line indicated by the line number. A region
   * must not start or end by a carriage return.
   *
   * @param lineNumber the line number, 1 being the first line of the file.
   * @return The line indicated. Returns {@code null} if it does not exist, or if there was an IO
   *     exception.
   */
  public @Nullable Region getRegion(int lineNumber) {
    String js = "";
    try {
      js = getCode();
    } catch (IOException e) {
      return null;
    }
    int pos = 0;
    int startLine = max(1, lineNumber - (SOURCE_EXCERPT_REGION_LENGTH + 1) / 2 + 1);
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
        return new SimpleRegion(startLine, endLine, js.substring(pos, last));
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
        sourceFiles.add(
            builder()
                .withCharset(inputCharset)
                .withOriginalPath(zipName + BANG_SLASH + entryName)
                .withZipEntryPath(absoluteZipPath, entryName)
                .build());
      }
    }
    return sourceFiles;
  }

  @GwtIncompatible("java.io.File")
  public static SourceFile fromFile(String fileName, Charset charset) {
    return builder().withPath(fileName).withCharset(charset).build();
  }

  @GwtIncompatible("java.io.File")
  public static SourceFile fromFile(String fileName) {
    return builder().withPath(fileName).build();
  }

  @GwtIncompatible("java.io.File")
  public static SourceFile fromPath(Path path, Charset charset) {
    return builder().withPath(path).withCharset(charset).build();
  }

  public static SourceFile fromCode(String fileName, String code, SourceKind kind) {
    return builder().withPath(fileName).withKind(kind).withContent(code).build();
  }

  public static SourceFile fromCode(String fileName, String code) {
    return builder().withPath(fileName).withContent(code).build();
  }

  /**
   * Reconciles serialized state in a {@link SourceFileProto} with the existing state in this file.
   *
   * <p>This should be called whenever initializing a compilation based on TypedAST protos. For
   * these compilations, the compiler initalization methods require creating SourceFiles before
   * deserializing TypedAST protos, so we sometimes get two copies of the same SourceFile.)
   */
  public void restoreCachedStateFrom(SourceFileProto protoSourceFile) {
    checkState(
        protoSourceFile.getFilename().equals(this.getName()),
        "Cannot restore state for %s from %s",
        this.getName(),
        protoSourceFile.getFilename());
    // TypedAST proto information is expected to be more accurate for:
    //  1) whether a SourceFile contains an @extern annotation or not. In non-TypedAST
    //    builds, we allow passing @extern files under the --js flag.  For TypedAST builds, we
    //    could support this, but it's an uncommon pattern and trickier to support than ban.
    //  2) tracking some extra state that is lazily computed in a SourceFile, like the number of
    //     lines and bytes in a file. SourceFile::restoreCachedStateFrom handles this case.
    // Note: the state in the proto might be incorrect in other cases, since some state cannot be
    // computed during library-level typechecking (e.g. what files are in the weak chunk)
    if (protoSourceFile.getSourceKind() == SourceFileProto.SourceKind.EXTERN) {
      checkState(
          this.getKind() == SourceKind.EXTERN,
          "TypedAST compilations must pass all extern files as externs, not js, but found %s",
          this.getName());
    }

    // Restore the number of lines/bytes from the proto, unless we already have cached
    // numLines and numBytes. Offset by 1. the proto "unset" value is 0, where as in SourceFile we
    // use "-1"
    int protoNumLines = protoSourceFile.getNumLinesPlusOne() - 1;
    int protoNumBytes = protoSourceFile.getNumBytesPlusOne() - 1;
    // It's possible that this.numLines and protoNumLines are both not "-1" but contain
    // conflicting values. This would happen if a file changes on disk after TypedAST
    // proto serialization. We choose the proto value over this.numLines because this
    // data is intended for metrics recording, which should care most about the size of a
    // file when compilation began and parsing ran.
    this.numLines = protoNumLines != -1 ? protoNumLines : this.numLines;
    this.numBytes = protoNumBytes != -1 ? protoNumBytes : this.numBytes;
  }

  @GwtIncompatible("java.io.Reader")
  public static SourceFile fromProto(SourceFileProto protoSourceFile) {
    SourceKind sourceKind = getSourceKindFromProto(protoSourceFile);
    SourceFile sourceFile = fromProto(protoSourceFile, sourceKind);
    // Restore the number of lines/bytes, which are offset by 1 in the proto.
    sourceFile.numLines = protoSourceFile.getNumLinesPlusOne() - 1;
    sourceFile.numBytes = protoSourceFile.getNumBytesPlusOne() - 1;
    return sourceFile;
  }

  private static SourceFile fromProto(SourceFileProto protoSourceFile, SourceKind sourceKind) {
    switch (protoSourceFile.getLoaderCase()) {
      case PRELOADED_CONTENTS:
        return SourceFile.fromCode(
            protoSourceFile.getFilename(), protoSourceFile.getPreloadedContents(), sourceKind);
      case FILE_ON_DISK:
        String pathOnDisk =
            protoSourceFile.getFileOnDisk().getActualPath().isEmpty()
                ? protoSourceFile.getFilename()
                : protoSourceFile.getFileOnDisk().getActualPath();
        return SourceFile.builder()
            .withCharset(toCharset(protoSourceFile.getFileOnDisk().getCharset()))
            .withOriginalPath(protoSourceFile.getFilename())
            .withKind(sourceKind)
            .withPath(pathOnDisk)
            .build();
      case ZIP_ENTRY:
        {
          SourceFileProto.ZipEntryOnDisk zipEntry = protoSourceFile.getZipEntry();
          return SourceFile.builder()
              .withKind(sourceKind)
              .withOriginalPath(protoSourceFile.getFilename())
              .withCharset(toCharset(zipEntry.getCharset()))
              .withZipEntryPath(zipEntry.getZipPath(), zipEntry.getEntryName())
              .build();
        }
      case LOADER_NOT_SET:
        break;
    }
    throw new AssertionError();
  }

  private static SourceKind getSourceKindFromProto(SourceFileProto protoSourceFile) {
    switch (protoSourceFile.getSourceKind()) {
      case EXTERN:
        return SourceKind.EXTERN;
      case CODE:
        return SourceKind.STRONG;
      case NOT_SPECIFIED:
      case UNRECOGNIZED:
        break;
    }
    throw new AssertionError();
  }

  private static Charset toCharset(String protoCharset) {
    if (protoCharset.isEmpty()) {
      return UTF_8;
    }
    return Charset.forName(protoCharset);
  }

  /** Create a new builder for source files. */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * A builder interface for source files.
   *
   * <p>Allows users to customize the Charset, and the original path of the source file (if it
   * differs from the path on disk).
   */
  public static final class Builder {
    private SourceKind kind = SourceKind.STRONG;
    private Charset charset = UTF_8;
    private @Nullable String originalPath = null;

    private @Nullable String path = null;
    private @Nullable Path pathWithFilesystem = null;
    private @Nullable String zipEntryPath = null;

    private @Nullable Supplier<String> lazyContent = null;

    private Builder() {}

    /** Set the source kind. */
    @CanIgnoreReturnValue
    public Builder withKind(SourceKind kind) {
      this.kind = kind;
      return this;
    }

    /** Set the charset to use when reading from an input stream or file. */
    @CanIgnoreReturnValue
    public Builder withCharset(Charset charset) {
      this.charset = charset;
      return this;
    }

    public Builder withPath(String path) {
      return this.withPathInternal(path, null);
    }

    public Builder withPath(Path path) {
      return this.withPathInternal(path.toString(), path);
    }

    @CanIgnoreReturnValue
    public Builder withContent(String x) {
      this.lazyContent = x::toString;
      return this;
    }

    @CanIgnoreReturnValue
    @GwtIncompatible
    public Builder withContent(InputStream x) {
      this.lazyContent =
          () -> {
            checkState(this.charset != null);
            try {
              return CharStreams.toString(new InputStreamReader(x, this.charset));
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          };
      return this;
    }

    @CanIgnoreReturnValue
    public Builder withZipEntryPath(String zipPath, String entryPath) {
      this.path = zipPath;
      this.zipEntryPath = entryPath;

      return this;
    }

    /**
     * Sets a name for this source file that does not need to correspond to a path on disk.
     *
     * <p>Allow passing a reasonable human-readable name in cases like for zip files and for
     * generated files with unstable artifact prefixes.
     *
     * <p>The name must still be unique.
     */
    @CanIgnoreReturnValue
    public Builder withOriginalPath(String originalPath) {
      this.originalPath = originalPath;
      return this;
    }

    public SourceFile build() {
      String displayPath =
          (this.originalPath != null)
              ? this.originalPath
              : ((this.zipEntryPath == null)
                  ? this.path
                  : this.path + BANG_SLASH + this.zipEntryPath);

      if (this.lazyContent != null) {
        return new SourceFile(
            new CodeLoader.Preloaded(this.lazyContent.get()), displayPath, this.kind);
      }

      if (this.zipEntryPath != null) {
        return new SourceFile(
            new CodeLoader.AtZip(this.path, this.zipEntryPath, this.charset),
            displayPath,
            this.kind);
      }

      return new SourceFile(
          new CodeLoader.OnDisk(
              (this.pathWithFilesystem != null) ? this.pathWithFilesystem : Path.of(this.path),
              this.charset),
          displayPath,
          this.kind);
    }

    private Builder withPathInternal(String path, @Nullable Path pathWithFilesystem) {
      // Check if this path should be inferred as a ZIP entry path.
      int bangSlashIndex = path.indexOf(BANG_SLASH);
      if (bangSlashIndex >= 0) {
        String zipPath = path.substring(0, bangSlashIndex);
        String entryPath = path.substring(bangSlashIndex + BANG_SLASH.length());

        if (zipPath.endsWith(".zip")
            && (entryPath.endsWith(".js") || entryPath.endsWith(".js.map"))) {
          return this.withZipEntryPath(zipPath, entryPath);
        }
      }

      // Path instances have an implicit reference to a FileSystem. Make sure to preserve it.
      this.path = path;
      this.pathWithFilesystem = pathWithFilesystem;

      return this;
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  // Implementations

  private abstract static class CodeLoader implements Serializable {
    /**
     * Return the source text of this file from its original storage.
     *
     * <p>The implementation may be a slow operation such as reading from a file. SourceFile
     * guarantees that this method is only called under synchronization.
     */
    String loadUncachedCode() throws IOException {
      throw new AssertionError();
    }

    /**
     * Return a Reader for the source text of this file from its original storage.
     *
     * <p>The implementation may be a slow operation such as reading from a file. SourceFile
     * guarantees that this method is only called under synchronization.
     */
    Reader openUncachedReader() throws IOException {
      return null;
    }

    /**
     * Returns a representation of this loader that can be serialized/deserialized to reconstruct
     * this SourceFile
     */
    abstract SourceFileProto.Builder toProtoLocationBuilder(String fileName);

    static final class Preloaded extends CodeLoader {
      private static final long serialVersionUID = 2L;
      private final String preloadedCode;

      Preloaded(String preloadedCode) {
        super();
        this.preloadedCode = checkNotNull(preloadedCode);
      }

      @Override
      String loadUncachedCode() {
        return this.preloadedCode;
      }

      @Override
      SourceFileProto.Builder toProtoLocationBuilder(String fileName) {
        return SourceFileProto.newBuilder().setPreloadedContents(this.preloadedCode);
      }
    }

    static final class OnDisk extends CodeLoader {
      private static final long serialVersionUID = 1L;

      private final String serializableCharset;
      private final Path relativePath;

      OnDisk(Path relativePath, Charset c) {
        super();
        this.serializableCharset = c.name();
        this.relativePath = relativePath;
      }

      @Override
      @GwtIncompatible
      String loadUncachedCode() throws IOException {
        try {
          return Files.readString(this.relativePath, this.getCharset());
        } catch (CharacterCodingException e) {
          throw new IOException(
              "Failed to read: " + this.relativePath + ", is this input UTF-8 encoded?", e);
        }
      }

      @Override
      @GwtIncompatible
      Reader openUncachedReader() throws IOException {
        return Files.newBufferedReader(this.relativePath, this.getCharset());
      }

      private Charset getCharset() {
        return Charset.forName(this.serializableCharset);
      }

      @Override
      SourceFileProto.Builder toProtoLocationBuilder(String fileName) {
        String actualPath = this.relativePath.toString();
        return SourceFileProto.newBuilder()
            .setFileOnDisk(
                FileOnDisk.newBuilder()
                    .setActualPath(
                        // to save space, don't serialize the path if equal to the fileName.
                        fileName.equals(actualPath) ? "" : actualPath)
                    // save space by not serializing UTF_8 (the default charset)
                    .setCharset(this.getCharset().equals(UTF_8) ? "" : this.serializableCharset));
      }
    }

    static final class AtZip extends CodeLoader {
      private static final long serialVersionUID = 1L;
      private final String zipName;
      private final String entryName;
      private final String serializableCharset;

      AtZip(String zipName, String entryName, Charset c) {
        super();
        this.zipName = zipName;
        this.entryName = entryName;
        this.serializableCharset = c.name();
      }

      @Override
      @GwtIncompatible
      String loadUncachedCode() throws IOException {
        return CharStreams.toString(this.openUncachedReader());
      }

      @Override
      @GwtIncompatible
      Reader openUncachedReader() throws IOException {
        return new InputStreamReader(
            JSCompZipFileCache.getEntryStream(this.zipName, this.entryName), this.getCharset());
      }

      private Charset getCharset() {
        return Charset.forName(this.serializableCharset);
      }

      @Override
      SourceFileProto.Builder toProtoLocationBuilder(String fileName) {
        return SourceFileProto.newBuilder()
            .setFilename(fileName)
            .setZipEntry(
                ZipEntryOnDisk.newBuilder()
                    .setEntryName(this.entryName)
                    .setZipPath(this.zipName)
                    // save space by not serializing UTF_8 (the default charset)
                    .setCharset(this.getCharset().equals(UTF_8) ? "" : this.serializableCharset)
                    .build());
      }
    }
  }

  public SourceFileProto getProto() {
    return this.loader
        .toProtoLocationBuilder(this.getName())
        .setFilename(this.getName())
        .setSourceKind(sourceKindToProto(this.getKind()))
        .setNumLinesPlusOne(this.numLines + 1)
        .setNumBytesPlusOne(this.numBytes + 1)
        .build();
  }

  private static SourceFileProto.SourceKind sourceKindToProto(SourceKind sourceKind) {
    switch (sourceKind) {
      case EXTERN:
        return SourceFileProto.SourceKind.EXTERN;
      case STRONG:
      case WEAK:
        return SourceFileProto.SourceKind.CODE;
      case NON_CODE:
        break;
    }
    throw new AssertionError();
  }
}
