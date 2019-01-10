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

package com.google.javascript.jscomp;


/** Gwt-compatible no-op version for {@code PrebuildAst}. */
// TODO(moz): Implement this using GWT's emulation of ListenableFuture and friends
class PrebuildAst {

  PrebuildAst(AbstractCompiler compiler, int numParalleThreads) {
  }

  void prebuild(Iterable<CompilerInput> inputList) {}
}
