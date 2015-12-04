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

import java.util.List;

/** GWT compatible no-op replacement for {@code Instrumentation} */
public final class Instrumentation {
  public List<String> getInitList() {
    throw new UnsupportedOperationException("Instrumentation.getInitList not implemented");
  }

  public String getReportDefined() {
    throw new UnsupportedOperationException("Instrumentation.getReportDefined not implemented");
  }

  public String getReportCall() {
    throw new UnsupportedOperationException("Instrumentation.getReportCall not implemented");
  }

  public String getReportExit() {
    throw new UnsupportedOperationException("Instrumentation.getReportExit not implemented");
  }

  public String getAppNameSetter() {
    throw new UnsupportedOperationException("Instrumentation.getAppNameSetter not implemented");
  }

  public List<String> getDeclarationToRemoveList() {
    throw new UnsupportedOperationException(
        "Instrumentation.getDeclarationToRemoveList not implemented");
  }
}
