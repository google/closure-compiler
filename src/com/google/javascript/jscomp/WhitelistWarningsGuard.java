/*
 * Copyright 2011 The Closure Compiler Authors.
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.google.common.io.CharSource;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An extension of {@code WarningsGuard} that provides functionality to maintain
 * a list of warnings (white-list). It is subclasses' responsibility to decide
 * what to do with the white-list by implementing the {@code level} function.
 * Warnings are defined by the name of the JS file and the first line of
 * warnings description.
 *
 * @author anatol@google.com (Anatol Pomazau)
 * @author bashir@google.com (Bashir Sadjad)
 */
@GwtIncompatible("java.io, java.util.regex")
public class WhitelistWarningsGuard extends WarningsGuard {
  private static final Splitter LINE_SPLITTER = Splitter.on('\n');

  /** The set of white-listed warnings, same format as {@code formatWarning}. */
  private final Set<String> whitelist;

  /** Pattern to match line number in error descriptions. */
  private static final Pattern LINE_NUMBER = Pattern.compile(":-?\\d+");

  public WhitelistWarningsGuard() {
    this(ImmutableSet.<String>of());
  }

  /**
   * This class depends on an input set that contains the white-list. The format
   * of each white-list string is:
   * {@code <file-name>:<line-number>?  <warning-description>}
   * {@code # <optional-comment>}
   *
   * @param whitelist The set of JS-warnings that are white-listed. This is
   *     expected to have similar format as {@code formatWarning(JSError)}.
   */
  public WhitelistWarningsGuard(Set<String> whitelist) {
    Preconditions.checkNotNull(whitelist);
    this.whitelist = normalizeWhitelist(whitelist);
  }

  /**
   * Loads legacy warnings list from the set of strings. During development line
   * numbers are changed very often - we just cut them and compare without ones.
   *
   * @return known legacy warnings without line numbers.
   */
  protected Set<String> normalizeWhitelist(Set<String> whitelist) {
    Set<String> result = new HashSet<>();
    for (String line : whitelist) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.charAt(0) == '#') {
        // strip out empty lines and comments.
        continue;
      }

      // Strip line number for matching.
      result.add(LINE_NUMBER.matcher(trimmed).replaceFirst(":"));
    }
    return ImmutableSet.copyOf(result);
  }

  @Override
  public CheckLevel level(JSError error) {
    if (containWarning(formatWarning(error))) {
      // If the message matches the guard we use WARNING, so that it
      // - Shows up on stderr, and
      // - Gets caught by the WhitelistBuilder downstream in the pipeline
      return CheckLevel.WARNING;
    }

    return null;
  }

  /**
   * Determines whether a given warning is included in the white-list.
   *
   * @param formattedWarning the warning formatted by {@code formatWarning}
   * @return whether the given warning is white-listed or not.
   */
  protected boolean containWarning(String formattedWarning) {
    return whitelist.contains(formattedWarning);
  }

  @Override
  public int getPriority() {
    return WarningsGuard.Priority.SUPPRESS_BY_WHITELIST.getValue();
  }

  /** Creates a warnings guard from a file. */
  public static WhitelistWarningsGuard fromFile(File file) {
    return new WhitelistWarningsGuard(loadWhitelistedJsWarnings(file));
  }

  /**
   * Loads legacy warnings list from the file.
   * @return The lines of the file.
   */
  public static Set<String> loadWhitelistedJsWarnings(File file) {
    return loadWhitelistedJsWarnings(
        Files.asCharSource(file, UTF_8));
  }

  /**
   * Loads legacy warnings list from the file.
   * @return The lines of the file.
   */
  protected static Set<String> loadWhitelistedJsWarnings(CharSource supplier) {
    try {
      return loadWhitelistedJsWarnings(supplier.openStream());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Loads legacy warnings list from the file.
   * @return The lines of the file.
   */
  // TODO(nicksantos): This is a weird API.
  static Set<String> loadWhitelistedJsWarnings(Reader reader)
      throws IOException {
    Preconditions.checkNotNull(reader);
    Set<String> result = new HashSet<>();

    result.addAll(CharStreams.readLines(reader));

    return result;
  }

  /**
   * If subclasses want to modify the formatting, they should override
   * #formatWarning(JSError, boolean), not this method.
   */
  protected String formatWarning(JSError error) {
    return formatWarning(error, false);
  }

  /**
   * @param withMetaData If true, include metadata that's useful to humans
   *     This metadata won't be used for matching the warning.
   */
  protected String formatWarning(JSError error, boolean withMetaData) {
    StringBuilder sb = new StringBuilder();
    sb.append(error.sourceName).append(":");
    if (withMetaData) {
      sb.append(error.lineNumber);
    }
    List<String> lines = LINE_SPLITTER.splitToList(error.description);
    sb.append("  ").append(lines.get(0));

    // Add the rest of the message as a comment.
    if (withMetaData) {
      for (int i = 1; i < lines.size(); i++) {
        sb.append("\n# ").append(lines.get(i));
      }
      sb.append("\n");
    }

    return sb.toString();
  }

  public static String getFirstLine(String warning) {
    int lineLength = warning.indexOf('\n');
    if (lineLength > 0) {
      warning = warning.substring(0, lineLength);
    }
    return warning;
  }

  /** Whitelist builder */
  public class WhitelistBuilder implements ErrorHandler {
    private final Set<JSError> warnings = new LinkedHashSet<>();
    private String productName = null;
    private String generatorTarget = null;
    private String headerNote = null;

    /** Fill in your product name to get a fun message! */
    public WhitelistBuilder setProductName(String name) {
      this.productName = name;
      return this;
    }

    /** Fill in instructions on how to generate this whitelist. */
    public WhitelistBuilder setGeneratorTarget(String name) {
      this.generatorTarget = name;
      return this;
    }

    /** A note to include at the top of the whitelist file. */
    public WhitelistBuilder setNote(String note) {
      this.headerNote  = note;
      return this;
    }

    @Override
    public void report(CheckLevel level, JSError error) {
      warnings.add(error);
    }

    /**
     * Writes the warnings collected in a format that the WhitelistWarningsGuard
     * can read back later.
     */
    public void writeWhitelist(File out) throws IOException {
      try (PrintStream stream = new PrintStream(out)) {
        appendWhitelist(stream);
      }
    }

    /**
     * Writes the warnings collected in a format that the WhitelistWarningsGuard
     * can read back later.
     */
    public void appendWhitelist(PrintStream out) {
      out.append(
          "# This is a list of legacy warnings that have yet to be fixed.\n");

      if (productName != null && !productName.isEmpty() && !warnings.isEmpty()) {
        out.append("# Please find some time and fix at least one of them "
            + "and it will be the happiest day for " + productName + ".\n");
      }

      if (generatorTarget != null && !generatorTarget.isEmpty()) {
        out.append("# When you fix any of these warnings, run "
            + generatorTarget + " task.\n");
      }

      if (headerNote != null) {
        out.append("#" + Joiner.on("\n# ").join(Splitter.on('\n').split(headerNote)) + "\n");
      }

      Multimap<DiagnosticType, String> warningsByType = TreeMultimap.create();
      for (JSError warning : warnings) {
        warningsByType.put(
            warning.getType(),
            formatWarning(warning, true /* withLineNumber */));
      }

      for (DiagnosticType type : warningsByType.keySet()) {
        if (DiagnosticGroups.DEPRECATED.matches(type)) {
          // Deprecation warnings are not raisable to error, so we don't need them in whitelists.
          continue;
        }
        out.append("\n# Warning ")
            .append(type.key)
            .append(": ")
            .println(Iterables.get(
                LINE_SPLITTER.split(type.format.toPattern()), 0));

        for (String warning : warningsByType.get(type)) {
          out.println(warning);
        }
      }
      out.flush();
    }
  }
}
