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

import com.google.javascript.rhino.Node;
import java.util.Locale;

/** Util functions for transpilation passes */
public final class TranspilationUtil {

  private TranspilationUtil() {} // prevent instantiation

  public static final DiagnosticType CANNOT_CONVERT =
      DiagnosticType.error("JSC_CANNOT_CONVERT", "This code cannot be transpiled. {0}");

  // TODO(tbreisacher): Remove this once we have implemented transpilation for all the features
  // we intend to support.
  public static final DiagnosticType CANNOT_CONVERT_YET =
      DiagnosticType.error(
          "JSC_CANNOT_CONVERT_YET", "Transpilation of ''{0}'' is not yet implemented.");

  static void cannotConvert(AbstractCompiler compiler, Node n, String message) {
    compiler.report(JSError.make(n, CANNOT_CONVERT, message));
  }

  /**
   * Warns the user that the given feature cannot be transpiled because the transpilation is not yet
   * implemented. A call to this method is essentially a "TODO(tbreisacher): Implement {@code
   * feature}" comment.
   */
  static void cannotConvertYet(AbstractCompiler compiler, Node n, String feature) {
    compiler.report(JSError.make(n, CANNOT_CONVERT_YET, feature));
  }

  static void preloadTranspilationRuntimeFunction(AbstractCompiler compiler, String function) {
    compiler.ensureLibraryInjected("es6/util/" + function.toLowerCase(Locale.ROOT), false);
  }
}
