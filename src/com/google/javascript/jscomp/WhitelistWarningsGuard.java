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
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An extension of {@code WarningsGuard} that provides functionality to maintain
 * a list of warnings (white-list). It is subclasses' responsibility to decide
 * what to do with the white-list by implementing the {@code level} function.
 * Warnings are defined by the name of the js file and the first line of
 * warnings description.
 *
 * @author anatol@google.com (Anatol Pomazau)
 * @author bashir@google.com (Bashir Sadjad)
 */
public abstract class WhitelistWarningsGuard extends WarningsGuard {
  /** The set of white-listed warnings, same format as {@code formatWarning}. */
  private final Set<String> whiteList;

  /** Pattern to match line number in error descriptions. */
  private static final Pattern LINE_NUMBER = Pattern.compile(":\\d+");

  /**
   * This class depends on an input set that contains the white-list. The format
   * of each white-list string is:
   * <file-name>:  <warning-description>
   *
   * @param whiteList The set of js-warnings that are white-listed. This is
   *     expected to have similar format as {@code formatWarning(JSError)}.
   */
  public WhitelistWarningsGuard(Set<String> whiteList) {
    this.whiteList = whiteList;
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
}
