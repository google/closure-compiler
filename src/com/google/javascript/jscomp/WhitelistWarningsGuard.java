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

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;
import com.google.javascript.jscomp.CheckLevel;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.HashSet;
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
public class WhitelistWarningsGuard extends WarningsGuard {
  /** The set of white-listed warnings, same format as {@code formatWarning}. */
  private final Set<String> whiteList;

  /** Pattern to match line number in error descriptions. */
  private static final Pattern LINE_NUMBER = Pattern.compile(":\\d+");

  /**
   * This class depends on an input set that contains the white-list. The format
   * of each white-list string is:
   * <file-name>:  <warning-description>
   *
   * @param whiteList The set of JS-warnings that are white-listed. This is
   *     expected to have similar format as {@code formatWarning(JSError)}.
   */
  public WhitelistWarningsGuard(Set<String> whiteList) {
    this.whiteList = whiteList;
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
    return whiteList.contains(formattedWarning);
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
   * Loads legacy warnings list from the file. As during development line
   * numbers are changed very often - we just cut it and compare without ones.
   *
   * @return known legacy warnings without line numbers.
   */
  public static Set<String> loadWhitelistedJsWarnings(File file) {
    return loadWhitelistedJsWarnings(
        Files.newReaderSupplier(file, Charsets.UTF_8));
  }

  /**
   * Loads legacy warnings list from the file. As during development line
   * numbers are changed very often - we just cut it and compare without ones.
   *
   * @return known legacy warnings without line numbers.
   */
  protected static Set<String> loadWhitelistedJsWarnings(
      InputSupplier<InputStreamReader> supplier) {
    Preconditions.checkNotNull(supplier);

    Set<String> result = new HashSet<String>();

    try {
      for (String line : CharStreams.readLines(supplier)) {
        line = line.trim();
        if (line.isEmpty() || line.charAt(0) == '#') {
          continue;
        }

        result.add(line);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return result;
  }

  public static String formatWarning(JSError error) {
    return formatWarning(error, false);
  }

  public static String formatWarning(JSError error, boolean withLineNumber) {
    StringBuilder sb = new StringBuilder();
    sb.append(error.sourceName).append(":");
    if (withLineNumber) {
      sb.append(error.lineNumber);
    }
    String descriptionFirstLine = getFirstLine(error.description);
    if (!withLineNumber) {
      descriptionFirstLine =
          LINE_NUMBER.matcher(descriptionFirstLine).replaceAll(":");
    }
    sb.append("  ").append(descriptionFirstLine);

    return sb.toString();
  }

  public static String getFirstLine(String warning) {
    int lineLength = warning.indexOf('\n');
    if (lineLength > 0) {
      warning = warning.substring(0, lineLength);
    }
    return warning;
  }

  public static class WhitelistBuilder implements ErrorHandler {
    private final Set<JSError> warnings = Sets.newLinkedHashSet();
    private String productName = null;
    private String generatorTarget = null;
    private boolean withLineNumber = false;

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

    /**
     * Sets whether line number are recorded in the whitelist.
     * This means that if lines are added below the warning, the warning
     * will need to be fixed or the whitelist will need to be regenerated.
     */
    public WhitelistBuilder setWithLineNumber(boolean line) {
      this.withLineNumber = line;
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
      PrintStream stream = new PrintStream(out);
      appendWhitelist(stream);
      stream.close();
    }

    /**
     * Writes the warnings collected in a format that the WhitelistWarningsGuard
     * can read back later.
     */
    public void appendWhitelist(PrintStream out) {
      out.append(
          "# This is a list of legacy warnings that have yet to be fixed.\n");

      if (productName != null) {
        out.append("# Please find some time and fix at least one of them "
            + "and it will be the happiest day for " + productName + ".\n");
      }

      if (generatorTarget != null) {
        out.append("# When you fix any of these warnings, run "
            + generatorTarget + " task.\n");
      }

      Multimap<DiagnosticType, String> warningsByType = TreeMultimap.create();
      for (JSError warning : warnings) {
        warningsByType.put(
            warning.getType(), formatWarning(warning, withLineNumber));
      }

      for (DiagnosticType type : warningsByType.keySet()) {
        out.append("\n# Warning ")
            .append(type.key)
            .append(": ")
            .println(getFirstLine(type.format.toPattern()));

        for (String warning : warningsByType.get(type)) {
          out.println(warning);
        }
      }
    }
  }
}
