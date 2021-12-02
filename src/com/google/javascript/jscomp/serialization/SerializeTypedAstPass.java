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
import com.google.javascript.jscomp.RemoveCastNodes;
import com.google.javascript.rhino.Node;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * A compiler pass intended to serialize the types in the AST.
 *
 * <p>Under construction. Do not use!
 */
public final class SerializeTypedAstPass implements CompilerPass {

  private final AbstractCompiler compiler;
  private final Consumer<TypedAst> consumer;

  SerializeTypedAstPass(AbstractCompiler compiler, Consumer<TypedAst> astConsumer) {
    this.compiler = compiler;
    this.consumer = astConsumer;
  }

  public static SerializeTypedAstPass createFromOutputStream(AbstractCompiler c, OutputStream out) {
    Consumer<TypedAst> toOutputStream =
        ast -> {
          try {
            TypedAst.List.newBuilder().addTypedAsts(ast).build().writeTo(out);
          } catch (IOException e) {
            throw new IllegalArgumentException("Cannot write to stream", e);
          }
        };
    return new SerializeTypedAstPass(c, toOutputStream);
  }

  public static SerializeTypedAstPass createFromPath(AbstractCompiler compiler, Path outputPath) {
    Consumer<TypedAst> toPath =
        ast -> {
          try (OutputStream out = Files.newOutputStream(outputPath)) {
            TypedAst.List.newBuilder().addTypedAsts(ast).build().writeTo(out);
          } catch (IOException e) {
            throw new IllegalArgumentException("Cannot create TypedAst output file", e);
          }
        };
    return new SerializeTypedAstPass(compiler, toPath);
  }

  @Override
  public void process(Node externs, Node root) {
    new RemoveCastNodes(compiler).process(externs, root);
    SerializationOptions serializationMode =
        this.compiler.isDebugLoggingEnabled()
            ? SerializationOptions.INCLUDE_DEBUG_INFO_AND_EXPENSIVE_VALIDITY_CHECKS
            : SerializationOptions.SKIP_DEBUG_INFO;
    TypedAstSerializer serializer = new TypedAstSerializer(this.compiler, serializationMode);
    TypedAst ast = serializer.serializeRoots(externs, root);
    consumer.accept(ast);
  }
}
