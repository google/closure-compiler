/*
 * Copyright 2008 The Closure Compiler Authors.
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
 * All warnings should be reported as errors.
 *
 * @author anatol@google.com (Anatol Pomazau)
 */
public final class StrictWarningsGuard extends WarningsGuard {
  private static final long serialVersionUID = 1L;

  static final DiagnosticType UNRAISABLE_WARNING =
      DiagnosticType.warning("JSC_UNRAISABLE_WARNING", "{0}");

  @Override
  public CheckLevel level(JSError error) {
    if (error.getType() == UNRAISABLE_WARNING) {
      return null;
    }
    return error.getDefaultLevel().isOn() ? CheckLevel.ERROR : null;
  }

  @Override
  protected int getPriority() {
    return WarningsGuard.Priority.STRICT.value; // applied last
  }

  @Override
  protected WarningsGuard makeNonStrict() {
    throw new UnsupportedOperationException(
        "Cannot make a StrictWarningsGuard non-strict");
  }
}
