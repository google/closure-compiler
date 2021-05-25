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


import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Iterator;

/**
 * Implements {@see go/js-style#file-goog-require} for selecting import aliases.
 *
 * <p>Instances act as lazy generators for an infinite sequence of names, in order of preferability.
 * Callers can pull from this sequence until they find a suitable name. This design allows
 * higher-level concerns, such as uniqueness, to be managed by higher-level code.
 *
 * <p>This impelementation is a heuristic for selecting likely good aliases. In addition to
 * conforming to the style guide, the ideal heurisitc is simple engough to explain in a few bullets,
 * in priority order:
 *
 * <ul>
 *   <li>Alias is not skiplisted
 *   <li>Alias is always only composed of segments of the full namespace
 *   <li>Shorter aliases are preferred over longer ones
 * </ul>
 */
final class RequireAliasGenerator implements Iterable<String> {

  private static final ImmutableSet<String> SKIPLISTED_ALIASES = createSkiplistedAliases();

  private static ImmutableSet<String> createSkiplistedAliases() {
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
      builder.add(captializeFirstChar(seed));
      builder.add(Ascii.toLowerCase(seed));
    }
    return builder.build();
  }

  private static final Splitter DOT_SPLITTER = Splitter.on('.');

  private final ImmutableList<String> parts;
  private final boolean capitalize;

  static Iterable<String> over(String namespace) {
    return new RequireAliasGenerator(namespace);
  }

  private RequireAliasGenerator(String namespace) {
    this.parts = ImmutableList.copyOf(DOT_SPLITTER.split(namespace)).reverse();
    this.capitalize = Ascii.isUpperCase(this.parts.get(0).charAt(0));
  }

  @Override
  public Iterator<String> iterator() {
    return new NameIterator();
  }

  private final class NameIterator extends AbstractIterator<String> {

    private String concat = "";
    private int index = 0;
    private int suffix = 0;

    @Override
    protected String computeNext() {
      String next;
      do {
        next = this.computeNextWithoutFiltering();
      } while (SKIPLISTED_ALIASES.contains(next));

      return next;
    }

    private String computeNextWithoutFiltering() {
      if (this.index >= RequireAliasGenerator.this.parts.size()) {
        return this.concat + this.suffix++;
      }

      String part = RequireAliasGenerator.this.parts.get(this.index++);

      if (RequireAliasGenerator.this.capitalize) {
        part = captializeFirstChar(part);
      }
      this.concat = part + captializeFirstChar(this.concat);

      return this.concat;
    }
  }

  private static String captializeFirstChar(String w) {
    if (w.isEmpty() || Ascii.isUpperCase(w.charAt(0))) {
      return w;
    }

    return Ascii.toUpperCase(w.charAt(0)) + w.substring(1);
  }
}
