/*
 * Copyright 2017 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.bundle;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.ErrorFormat;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.MessageFormatter;
import com.google.javascript.jscomp.SourceExcerptProvider;

/**
 * An unchecked exception thrown when transpilation fails due to one or
 * more errors in the input script.
 */
@GwtIncompatible
public class TranspilationException extends RuntimeException {

  private final ImmutableList<JSError> errors;
  private final ImmutableList<JSError> warnings;

  public TranspilationException(Exception cause) {
    this(tryCastToTranspilationException(cause.getCause()), cause);
  }

  public TranspilationException(
      SourceExcerptProvider source,
      ImmutableList<JSError> errors,
      ImmutableList<JSError> warnings) {
    this(errors, warnings, format(source, errors, warnings), null);
  }

  private TranspilationException(TranspilationException root, Exception cause) {
    this(root.errors, root.warnings, root.getMessage(), cause);
  }

  private TranspilationException(
      ImmutableList<JSError> errors,
      ImmutableList<JSError> warnings,
      String formatted,
      Exception cause) {
    super(formatted, cause);
    this.errors = errors;
    this.warnings = warnings;
  }

  public ImmutableList<JSError> errors() {
    return errors;
  }

  public ImmutableList<JSError> warnings() {
    return warnings;
  }

  private static String format(
      SourceExcerptProvider source,
      ImmutableList<JSError> errors,
      ImmutableList<JSError> warnings) {
    StringBuilder sb = new StringBuilder().append("Transpilation failed:\n");
    MessageFormatter formatter =
        source != null
            ? ErrorFormat.SINGLELINE.toFormatter(source, false)
            : ErrorFormat.SOURCELESS.toFormatter(source, false);
    for (JSError error : errors) {
      sb.append("\n").append(formatter.formatError(error));
    }
    for (JSError warning : warnings) {
      sb.append("\n").append(formatter.formatError(warning));
    }
    return sb.toString();
  }

  private static TranspilationException tryCastToTranspilationException(Throwable t) {
    if (t instanceof TranspilationException) {
      return (TranspilationException) t;
    }
    return null;
  }
}
