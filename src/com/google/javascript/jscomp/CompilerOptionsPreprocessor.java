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

import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;

/**
 * Checks for combinations of options that are incompatible, i.e. will produce
 * incorrect code.
 *
 * <p>Also, turns off options if the provided options don't make sense together.
 *
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
final class CompilerOptionsPreprocessor {

  static void preprocess(CompilerOptions options) {
    if (options.checkMissingGetCssNameLevel.isOn()
        && (options.checkMissingGetCssNameBlacklist == null
            || options.checkMissingGetCssNameBlacklist.isEmpty())) {
      throw new InvalidOptionsException(
          "Cannot check use of goog.getCssName because of empty blacklist.");
    }

    if (options.removeUnusedPrototypePropertiesInExterns
        && !options.removeUnusedPrototypeProperties) {
      throw new InvalidOptionsException(
          "remove_unused_prototype_properties_in_externs requires "
          + "remove_unused_prototype_properties to be turned on.");
    }

    if (!options.inlineFunctions
        && options.maxFunctionSizeAfterInlining
        != CompilerOptions.UNLIMITED_FUN_SIZE_AFTER_INLINING) {
      throw new InvalidOptionsException(
          "max_function_size_after_inlining has no effect if inlining is"
          + " disabled.");
    }

    if (options.useNewTypeInference) {
      options.checkMissingReturn = CheckLevel.OFF;
      options.checkGlobalThisLevel = CheckLevel.OFF;
    }

    if (options.jqueryPass && options.closurePass) {
      throw new InvalidOptionsException(
          "The jQuery pass and the Closure pass cannot both be enabled.");
    }

    if (options.removeUnusedPrototypePropertiesInExterns
        && options.exportLocalPropertyDefinitions) {
      throw new InvalidOptionsException(
          "remove_unused_prototype_properties_in_externs "
          + "and export_local_property_definitions cannot be used together.");
    }

  }

  /**
   * Exception to indicate incompatible options in the CompilerOptions.
   */
  public static class InvalidOptionsException extends RuntimeException {
    private InvalidOptionsException(String message, Object... args) {
      super(SimpleFormat.format(message, args));
    }
  }

  // Don't instantiate.
  private CompilerOptionsPreprocessor() {
  }
}
