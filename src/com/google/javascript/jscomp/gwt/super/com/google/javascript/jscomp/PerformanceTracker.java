/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CompilerOptions.TracerMode;
import com.google.javascript.rhino.Node;

import java.io.PrintStream;

/** GWT compatible no-op replacement for {@code PerformanceTracker} */
public final class PerformanceTracker {
  PerformanceTracker(Node jsRoot, TracerMode mode) {}

  void recordPassStart(String passName, boolean isOneTime) {}

  void recordPassStop(String passName, long runTime) {}

  CodeChangeHandler getCodeChangeHandler() {
    throw new UnsupportedOperationException(
        "PerformanceTracker.getCodeChangeHandler not implemented");
  }

  public void outputTracerReport(PrintStream pstr) {}
}
