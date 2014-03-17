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
package com.google.javascript.jscomp;

import java.util.List;

/**
 * Checks for combinations of options that are incompatible, i.e. will produce
 * incorrect code.
 *
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
final class CompilerOptionsValidator {

  // This method runs before parsing. Therefore, we can't pass externsRoot from
  // Compiler.java because it may still not have its correct value.
  static void validate(CompilerOptions options, List<CompilerInput> externs) {
    if (options.crossModuleMethodMotion &&
        (externs == null || externs.size() == 0)) {
      throw new InvalidOptionsException(
          "Cross-module method motion requires use of externs for the " +
          "arguments array.");
    }

    if (options.checkMissingGetCssNameLevel.isOn() &&
        (options.checkMissingGetCssNameBlacklist == null ||
            options.checkMissingGetCssNameBlacklist.isEmpty())) {
      throw new InvalidOptionsException(
          "Cannot check use of goog.getCssName because of empty blacklist.");
    }
  }

  /**
   * Exception to indicate incompatible options in the CompilerOptions.
   */
  public static class InvalidOptionsException extends RuntimeException {
    private InvalidOptionsException(String message) {
      super(message);
    }
  }

  // Don't instantiate.
  private CompilerOptionsValidator() {
  }
}
