/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.javascript.rhino.jstype.JSType;

/**
 * The different strategies for matching the {@code JSType} of nodes.
 */
public enum TypeMatchingStrategy {

  /**
   * Matches type or any subtype. Matches types with different nullability/voidability. Allows loose
   * matches.
   */
  LOOSE(true, true, true),

  /**
   * Matches type or any subtype. Does not match types with different nullability/voidability.
   * Allows loose matches.
   */
  STRICT_NULLABILITY(true, false, true),

  /**
   * Matches type or any subtype. Does not match types with different nullability/voidability.
   * Does not allow loose matches.
   */
  SUBTYPES(true, false, false),

  /**
   * Does not match subtypes. Does not match types with different nullability/voidability. Does not
   * allow loose matches.
   */
  EXACT(false, false, false);

  private final boolean allowSubtypes;
  private final boolean ignoreNullability;
  private final boolean allowLooseMatches;

  private TypeMatchingStrategy(
      boolean allowSubtypes, boolean ignoreNullability, boolean allowLooseMatches) {
    this.allowSubtypes = allowSubtypes;
    this.ignoreNullability = ignoreNullability;
    this.allowLooseMatches = allowLooseMatches;
  }

  public MatchResult match(JSType templateType, JSType type) {
    if (templateType.isUnknownType()) {
      // If the template type is '?', then any type is a match and this is not considered a loose
      // match.
      return new MatchResult(true, false);
    }

    if (type == null || type.isUnknownType() || type.isAllType()) {
      return new MatchResult(allowLooseMatches, allowLooseMatches);
    }

    if (allowSubtypes) {
      if (ignoreNullability) {
        type = type.restrictByNotNullOrUndefined();
      }
      if (type.isSubtype(templateType)) {
        return new MatchResult(true, false);
      }
    }

    boolean nullableMismatch = templateType.isNullable() != type.isNullable();
    boolean voidableMismatch = templateType.isVoidable() != type.isVoidable();
    if (!ignoreNullability && (nullableMismatch || voidableMismatch)) {
      return new MatchResult(false, false);
    }

    return new MatchResult(type.isEquivalentTo(templateType), false);
  }

  /**
   * The result of comparing two different {@code JSType} instances.
   */
  public static class MatchResult {
    private final boolean isMatch;
    private final boolean isLooseMatch;

    public MatchResult(boolean isMatch, boolean isLooseMatch) {
      this.isMatch = isMatch;
      this.isLooseMatch = isLooseMatch;
    }

    public boolean isMatch() {
      return isMatch;
    }

    public boolean isLooseMatch() {
      return isLooseMatch;
    }
  }
}
