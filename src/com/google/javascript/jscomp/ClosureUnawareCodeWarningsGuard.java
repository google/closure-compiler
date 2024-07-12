/*
 * Copyright 2024 The Closure Compiler Authors.
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
import org.jspecify.annotations.Nullable;

/**
 * A warnings guard that suppresses warnings that are spurious for code that is unaware of Closure
 * compiler's requirements.
 */
public final class ClosureUnawareCodeWarningsGuard extends WarningsGuard {

  private static final DiagnosticGroup DEFAULT_CLOSURE_UNAWARE_CODE_SUPPRESSIONS =
      new DiagnosticGroup(
          "closureUnawareCodeJSDocIncompatible",
          RhinoErrorReporter.TYPE_PARSE_ERROR,
          RhinoErrorReporter.UNRECOGNIZED_TYPE_ERROR,
          RhinoErrorReporter.JSDOC_MISSING_BRACES_WARNING,
          RhinoErrorReporter.JSDOC_MISSING_TYPE_WARNING,
          RhinoErrorReporter.UNNECESSARY_ESCAPE,
          RhinoErrorReporter.BAD_JSDOC_ANNOTATION,
          RhinoErrorReporter.UNSUPPORTED_BOUNDED_GENERIC_TYPES,
          RhinoErrorReporter.BOUNDED_GENERIC_TYPE_ERROR);

  @Override
  public @Nullable CheckLevel level(JSError error) {
    if (!DEFAULT_CLOSURE_UNAWARE_CODE_SUPPRESSIONS.matches(error)) {
      return null;
    }
    @Nullable Node node = error.getNode();
    if (node == null) {
      return null;
    }
    if (node.isClosureUnawareCode()) {
      return CheckLevel.OFF;
    }
    return null;
  }

  @Override
  protected int getPriority() {
    return Priority.MAX.getValue();
  }
}
