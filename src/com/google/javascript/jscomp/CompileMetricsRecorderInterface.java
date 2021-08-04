/*
 * Copyright 2021 The Closure Compiler Authors.
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
 * Interface used by `AbstractCompilerRunner` to report metrics about the compilation action it
 * performs.
 */
public interface CompileMetricsRecorderInterface {

  void recordActionStart();

  void recordActionName(String actionName);

  void recordStartState(AbstractCompiler compiler);

  void recordResultMetrics(AbstractCompiler compiler, Result result);
}
