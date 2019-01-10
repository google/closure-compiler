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

/** GWT compatible no-op replacement for {@code Requirement} */
public final class Requirement {
  /** No-op replacement for {@code Requirement.Severity} */
  public enum Severity {
    UNSPECIFIED,
    WARNING,
    ERROR
  }

  /** No-op replacement for {@code Requirement.WhitelistEntry} */
  public static class WhitelistEntry {
    /** No-op replacement for {@code Requirement.WhitelistEntry.Reason} */
    public enum Reason {
      UNSPECIFIED,
      LEGACY,
      OUT_OF_SCOPE,
      MANUALLY_REVIEWED
    }
  }
}
