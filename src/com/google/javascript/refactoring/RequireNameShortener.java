/*
 * Copyright 2014 The Closure Compiler Authors.
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

package com.google.javascript.refactoring;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.LinkedHashSet;

/**
 * Implements {@see go/js-style#file-goog-require} for selecting import aliases.
 *
 * <p>This imeplementation is a heuristic for selecting a likely good alias. In addition to
 * conforming to the style guide, the ideal heurisitc is simple engough to explain in a few bullets,
 * in priority order:
 *
 * <ul>
 *   <li>Alias never collides with a name already in use in the same file
 *   <li>Alias is not blacklisted
 *   <li>Alias is always only composed of segments of the full namespace
 * </ul>
 */
final class RequireNameShortener {

  private static final ImmutableSet<String> BLACKLISTED_ALIASES = createBlacklistedAliases();

  private static ImmutableSet<String> createBlacklistedAliases() {
    ImmutableSet<String> seeds =
        ImmutableSet.of(
            "", //
            "array",
            "event",
            "map",
            "math",
            "object",
            "promise",
            "set",
            "string",
            "thenable");

    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    for (String seed : seeds) {
      builder.add(upperCaseFirstChar(seed));
      builder.add(Ascii.toLowerCase(seed));
    }
    return builder.build();
  }

  private static final Splitter DOT_SPLITTER = Splitter.on('.');

  private final LinkedHashSet<String> namesInUse = new LinkedHashSet<>(BLACKLISTED_ALIASES);

  static String shorten(String namespace, Node script) {
    checkArgument(script.isScript() || script.isModuleBody());

    RequireNameShortener shortener = new RequireNameShortener();
    shortener.recordNamesInUse(script, false);
    return shortener.shortenInternal(namespace);
  }

  private RequireNameShortener() {}

  private String shortenInternal(String namespace) {
    ImmutableList<String> parts = ImmutableList.copyOf(DOT_SPLITTER.split(namespace)).reverse();
    String lastPart = parts.get(0);
    boolean shouldUpperCaseFirstChar = Ascii.isUpperCase(lastPart.charAt(0));

    String result = "";

    for (String part : parts) {
      if (this.isValidAlias(result)) {
        return result;
      }

      if (shouldUpperCaseFirstChar) {
        part = upperCaseFirstChar(part);
      }
      result = part + upperCaseFirstChar(result);
    }

    final String allParts = result;

    for (int i = 0; !this.isValidAlias(result); i++) {
      result = allParts + i;
    }

    return result;
  }

  private boolean isValidAlias(String alias) {
    return !this.namesInUse.contains(alias);
  }

  private void recordNamesInUse(Node n, boolean isJsdoc) {
    if (isJsdoc) {
      if (n.isString()) {
        this.namesInUse.add(DOT_SPLITTER.split(n.getString()).iterator().next());
      }
    } else {
      if (n.isName()) {
        this.namesInUse.add(n.getString());
      }
    }

    JSDocInfo jsdoc = n.getJSDocInfo();
    if (jsdoc != null) {
      for (Node expr : jsdoc.getTypeNodes()) {
        this.recordNamesInUse(expr, true);
      }
    }

    for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      this.recordNamesInUse(c, isJsdoc);
    }
  }

  private static String upperCaseFirstChar(String w) {
    if (w.isEmpty() || Ascii.isUpperCase(w.charAt(0))) {
      return w;
    }

    return Ascii.toUpperCase(w.charAt(0)) + w.substring(1);
  }
}
