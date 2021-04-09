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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.serialization.SerializationOptions;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

/**
 * A compiler pass intended to serialize the types in the AST.
 *
 * <p>Under construction. Do not use!
 */
public final class SerializeTypedAstPass implements CompilerPass {

  private final AbstractCompiler compiler;
  private final Consumer<TypedAst> consumer;
  private final SerializationOptions serializationOptions;

  public SerializeTypedAstPass(
      AbstractCompiler compiler, SerializationOptions serializationOptions, OutputStream out) {
    this(
        compiler,
        serializationOptions,
        ast -> {
          try {
            ast.writeTo(out);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @VisibleForTesting
  SerializeTypedAstPass(
      AbstractCompiler compiler,
      SerializationOptions serializationOptions,
      Consumer<TypedAst> astConsumer) {
    this.compiler = compiler;
    this.consumer = astConsumer;
    this.serializationOptions = serializationOptions;
  }

  @Override
  public void process(Node externs, Node root) {
    JSTypeRegistry registry = compiler.getTypeRegistry();
    TypedAstSerializer serializer =
        TypedAstSerializer.createFromRegistryWithOptions(
            registry,
            serializationOptions,
            compiler.hasTypeCheckingRun()
                ? ImmutableList.copyOf(compiler.getTypeMismatches())
                : ImmutableList.of());
    TypedAst ast = serializer.serializeRoots(externs, root);
    consumer.accept(ast);
  }
}
