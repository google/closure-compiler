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
import java.util.zip.GZIPOutputStream;

/**
 * A compiler pass intended to serialize the types in the AST.
 *
 * <p>Under construction. Do not use!
 */
public final class SerializeTypedAstPass implements CompilerPass {

  private final AbstractCompiler compiler;
  private final Consumer<TypedAst> consumer;
  private final SerializationOptions serializationOptions;

  SerializeTypedAstPass(
      AbstractCompiler compiler,
      Consumer<TypedAst> astConsumer,
      SerializationOptions serializationOptions) {
    this.compiler = compiler;
    this.consumer = astConsumer;
    this.serializationOptions = serializationOptions;
  }

  /**
   * Serializes a TypedAst to the given output stream.
   *
   * <p>Unlike {@link #createFromPath(AbstractCompiler, Path)}, this method does not automatically
   * gzip the TypedAST. The "out" parameter may or may not already be a GZIPOutputStream.
   */
  public static SerializeTypedAstPass createFromOutputStream(
      AbstractCompiler c, OutputStream out, SerializationOptions serializationOptions) {
    Consumer<TypedAst> toOutputStream =
        ast -> {
          try {
            TypedAst.List.newBuilder().addTypedAsts(ast).build().writeTo(out);
          } catch (IOException e) {
            throw new IllegalArgumentException("Cannot write to stream", e);
          }
        };
    return new SerializeTypedAstPass(c, toOutputStream, serializationOptions);
  }

  /** Serializes a gzipped TypedAst to the specified outputPath */
  public static SerializeTypedAstPass createFromPath(
      AbstractCompiler compiler, Path outputPath, SerializationOptions serializationOptions) {
    Consumer<TypedAst> toPath =
        ast -> {
          try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(outputPath))) {
            TypedAst.List.newBuilder().addTypedAsts(ast).build().writeTo(out);
          } catch (IOException e) {
            throw new IllegalArgumentException("Cannot create TypedAst output file", e);
          }
        };
    return new SerializeTypedAstPass(compiler, toPath, serializationOptions);
  }

  @Override
  public void process(Node externs, Node root) {
    new RemoveCastNodes(compiler).process(externs, root);
    TypedAstSerializer serializer = new TypedAstSerializer(this.compiler, serializationOptions);
    TypedAst ast = serializer.serializeRoots(externs, root);
    consumer.accept(ast);
  }
}
