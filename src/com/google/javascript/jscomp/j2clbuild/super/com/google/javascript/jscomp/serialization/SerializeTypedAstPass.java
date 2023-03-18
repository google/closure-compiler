/*
 * Copyright 2019 The Closure Compiler Authors.
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
import java.nio.file.Path;

/** Fail-fast replacement */
public final class SerializeTypedAstPass implements CompilerPass {

  public SerializeTypedAstPass(
      AbstractCompiler compiler, Path out, SerializationOptions serializationOptions) {
    throw new RuntimeException("Serialization not yet supported in JS version of compiler");
  }

  public static SerializeTypedAstPass createFromPath(
      AbstractCompiler compiler, Path outputPath, SerializationOptions serializationOptions) {
    throw new RuntimeException("Serialization not yet supported in JS version of compiler");
  }

  @Override
  public void process(Node externs, Node root) {}
}
