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

/**
 * An error reporter which consumes all calls and performs no actions.
 *
 */
public abstract class NullErrorReporter  {
  private NullErrorReporter() {
  }

  public void error(String message, String sourceName, int line,
      int lineOffset) {
  }

  public void warning(String message, String sourceName, int line,
      int lineOffset) {
  }

  public static ErrorReporter forOldRhino() {
    return new OldRhinoNullReporter();
  }

  public static com.google.javascript.rhino.head.ErrorReporter
      forNewRhino() {
    return new NewRhinoNullReporter();
  }

  private static class NewRhinoNullReporter extends NullErrorReporter
      implements com.google.javascript.rhino.head.ErrorReporter {
    @Override
    public com.google.javascript.rhino.head.EvaluatorException
      runtimeError(String message, String sourceName, int line,
                   String lineSource, int lineOffset) {
      return new com.google.javascript.rhino.head.EvaluatorException(
          message);
    }

    @Override
    public void error(String message, String sourceName, int line,
        String sourceLine, int lineOffset) {
      super.error(message, sourceName, line, lineOffset);
    }

    @Override
    public void warning(String message, String sourceName, int line,
        String sourceLine, int lineOffset) {
      super.warning(message, sourceName, line, lineOffset);
    }
  }

  private static class OldRhinoNullReporter extends NullErrorReporter
      implements ErrorReporter {
  }
}
