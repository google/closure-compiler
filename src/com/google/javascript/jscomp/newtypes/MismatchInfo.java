/*
 * Copyright 2016 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.newtypes;

import com.google.common.base.Preconditions;

/**
 * When the expected and the found type don't match, this class contains
 * information about the mismatch that allows us to pinpoint which parts of
 * the types don't match.
 */
public class MismatchInfo {
  private static enum Kind {
    PROPERTY_TYPE_MISMATCH,
    MISSING_PROPERTY,
    WANTED_REQUIRED_PROP_FOUND_OPTIONAL,
    ARGUMENT_TYPE_MISMATCH,
    RETURN_TYPE_MISMATCH,
    UNION_TYPE_MISMATCH
  }

  private final Kind kind;
  private String propName;
  private int argIndex = -1;
  private JSType expected;
  private JSType found;

  private MismatchInfo(Kind kind) {
    this.kind = kind;
  }

  //// Factories

  static MismatchInfo makeUnionTypeMismatch(JSType found) {
    MismatchInfo info = new MismatchInfo(Kind.UNION_TYPE_MISMATCH);
    info.found = found;
    return info;
  }

  static MismatchInfo makePropTypeMismatch(
      String propName, JSType expected, JSType found) {
    MismatchInfo info = new MismatchInfo(Kind.PROPERTY_TYPE_MISMATCH);
    info.propName = propName;
    info.expected = expected;
    info.found = found;
    return info;
  }

  static MismatchInfo makeMissingPropMismatch(String propName) {
    MismatchInfo info =  new MismatchInfo(Kind.MISSING_PROPERTY);
    info.propName = propName;
    return info;
  }

  static MismatchInfo makeMaybeMissingPropMismatch(String propName) {
    MismatchInfo info =
        new MismatchInfo(Kind.WANTED_REQUIRED_PROP_FOUND_OPTIONAL);
    info.propName = propName;
    return info;
  }

  static MismatchInfo makeArgTypeMismatch(
      int argIndex, JSType expected, JSType found) {
    MismatchInfo info = new MismatchInfo(Kind.ARGUMENT_TYPE_MISMATCH);
    info.argIndex = argIndex;
    info.expected = expected;
    info.found = found;
    return info;
  }

  static MismatchInfo makeRetTypeMismatch(JSType expected, JSType found) {
    MismatchInfo info = new MismatchInfo(Kind.RETURN_TYPE_MISMATCH);
    info.expected = expected;
    info.found = found;
    return info;
  }

  //// Getters

  public String getPropName() {
    return Preconditions.checkNotNull(this.propName);
  }

  public JSType getFoundType() {
    return Preconditions.checkNotNull(this.found);
  }

  public JSType getExpectedType() {
    return Preconditions.checkNotNull(this.expected);
  }

  public int getArgIndex() {
    Preconditions.checkState(this.argIndex >= 0);
    return this.argIndex;
  }

  //// Predicates

  public boolean isPropMismatch() {
    return this.kind == Kind.PROPERTY_TYPE_MISMATCH;
  }

  public boolean isMissingProp() {
    return this.kind == Kind.MISSING_PROPERTY;
  }

  public boolean wantedRequiredFoundOptional() {
    return this.kind == Kind.WANTED_REQUIRED_PROP_FOUND_OPTIONAL;
  }

  public boolean isArgTypeMismatch() {
    return this.kind == Kind.ARGUMENT_TYPE_MISMATCH;
  }

  public boolean isRetTypeMismatch() {
    return this.kind == Kind.RETURN_TYPE_MISMATCH;
  }

  public boolean isUnionTypeMismatch() {
    return this.kind == Kind.UNION_TYPE_MISMATCH;
  }
}
