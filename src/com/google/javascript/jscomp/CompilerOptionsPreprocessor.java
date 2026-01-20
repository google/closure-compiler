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

/**
 * Checks for combinations of options that are incompatible, i.e. will produce incorrect code.
 *
 * <p>This is run by Compiler#compileInternal, which is not run during unit tests. The catch is that
 * it's run after Compiler#initOptions, so if for example you want to change the warningsGuard, you
 * can't do it here.
 *
 * <p>Also, turns off options if the provided options don't make sense together.
 */
public final class CompilerOptionsPreprocessor {

  static void preprocess(CompilerOptions options) {
    if (options.getInlineFunctionsLevel() == CompilerOptions.Reach.NONE
        && options.getMaxFunctionSizeAfterInlining()
            != CompilerOptions.UNLIMITED_FUN_SIZE_AFTER_INLINING) {
      throw new InvalidOptionsException(
          "max_function_size_after_inlining has no effect if inlining is disabled.");
    }
  }

  /** Exception to indicate incompatible options in the CompilerOptions. */
  public static class InvalidOptionsException extends RuntimeException {
    private InvalidOptionsException(String message) {
      super(message);
    }
  }

  // Don't instantiate.
  private CompilerOptionsPreprocessor() {}
}
