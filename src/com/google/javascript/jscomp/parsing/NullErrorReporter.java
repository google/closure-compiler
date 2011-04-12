/*
 * Copyright 2009 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.parsing;

import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.EvaluatorException;

/**
 * An error reporter which consumes all calls and performs no actions.
 *
 */
public abstract class NullErrorReporter  {
  private NullErrorReporter() {
  }

  public void error(String message, String sourceName, int line,
      String lineSource, int lineOffset) {
  }

  public void warning(String message, String sourceName, int line,
      String lineSource, int lineOffset) {
  }

  public static ErrorReporter forOldRhino() {
    return new OldRhinoNullReporter();
  }

  public static com.google.javascript.jscomp.mozilla.rhino.ErrorReporter
      forNewRhino() {
    return new NewRhinoNullReporter();
  }

  private static class NewRhinoNullReporter extends NullErrorReporter
      implements com.google.javascript.jscomp.mozilla.rhino.ErrorReporter {
    public com.google.javascript.jscomp.mozilla.rhino.EvaluatorException
      runtimeError(String message, String sourceName, int line,
                   String lineSource, int lineOffset) {
      return new com.google.javascript.jscomp.mozilla.rhino.EvaluatorException(
          message);
    }
  }

  private static class OldRhinoNullReporter extends NullErrorReporter
      implements ErrorReporter {
    public EvaluatorException runtimeError(String message, String sourceName,
                                           int line, String lineSource,
                                           int lineOffset) {
      return new EvaluatorException(message);
    }
  }
}
