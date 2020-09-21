/*
 * Copyright 2020 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.serialization;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.rhino.Node;

/**
 * Ideally, we wouldn't have this super-source at all, and instead use the real version of this pass
 * along with the j2cl-compatible proto library at https://github.com/google/j2cl-protobuf
 */
public final class ConvertTypesToColors implements CompilerPass {
  public ConvertTypesToColors(AbstractCompiler compiler) {}

  @Override
  public void process(Node externs, Node root) {}
}
