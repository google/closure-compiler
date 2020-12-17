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

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.Node;

/** GWT compatible no-op replacement of {@code CheckConformance} */
public final class CheckConformance implements CompilerPass {

  static final DiagnosticType CONFORMANCE_ERROR = DiagnosticType.error("JSC_CONFORMANCE_ERROR", "");

  static final DiagnosticType CONFORMANCE_VIOLATION =
      DiagnosticType.warning("JSC_CONFORMANCE_VIOLATION", "");

  static final DiagnosticType CONFORMANCE_POSSIBLE_VIOLATION =
      DiagnosticType.warning("JSC_CONFORMANCE_POSSIBLE_VIOLATION", "");

  static final DiagnosticType INVALID_REQUIREMENT_SPEC =
      DiagnosticType.error("JSC_INVALID_REQUIREMENT_SPEC", "");

  CheckConformance(
      AbstractCompiler compiler,
      ImmutableList<ConformanceConfig> configs) {
  }

  @Override
  public void process(Node externs, Node root) {
  }
}
