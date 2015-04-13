/*
 * Copyright 2011 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.parsing.parser.util;

import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;

/**
 * A conduit for reporting errors and warnings to the user.
 */
public abstract class ErrorReporter {
  public final void reportError(SourcePosition location, String format, Object... arguments) {
    hadError = true;
    String message = SimpleFormat.format(format, arguments);
    reportError(location, message);
  }

  public final void reportWarning(SourcePosition location, String format, Object... arguments) {
    String message = SimpleFormat.format(format, arguments);
    reportWarning(location, message);
  }

  protected abstract void reportError(SourcePosition location, String message);
  protected abstract void reportWarning(SourcePosition location, String message);

  public final boolean hadError() {
    return hadError;
  }

  private boolean hadError;
}
