/*
 * Copyright 2025 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import java.util.ArrayList;
import java.util.List;

/**
 * NestedCompilerRunner is a class for taking individual @closureUnaware "shadow AST" scripts, which
 * are expected to have been initialized as part of a main compilation, and running a nested
 * compilation over just those scripts.
 *
 * <p>This class isn't tightly coupled with the exact @closureUnaware implementation in JSCompiler,
 * but is still designed to only work for @closureUnaware scripts and will throw an exception if
 * asked to compile any other code.
 *
 * <p>A "nested" compilation is distinguished from a regular compilation mainly us knowing we are in
 * the middle of the optimization pass loop of a regular compilation; so we want to delegate certain
 * things like error reporting or debug logging to the main compiler.
 */
final class NestedCompilerRunner {

  private final Compiler shadowCompiler = new Compiler();
  private final List<CompilerInput> inputs = new ArrayList<>();
  private final JSChunk baseChunk = new JSChunk("shadow_base");

  private final AbstractCompiler original;
  private final CompilerOptions shadowOptions;
  private final Mode mode;

  enum Mode {
    TRANSPILE_AND_OPTIMIZE,
    TRANSPILE_ONLY
  }

  private NestedCompilerRunner(
      AbstractCompiler original, CompilerOptions shadowOptions, Mode mode) {
    this.original = original;
    this.shadowOptions = shadowOptions;
    this.mode = mode;
  }

  static NestedCompilerRunner create(
      AbstractCompiler original, CompilerOptions shadowOptions, Mode mode) {
    return new NestedCompilerRunner(original, shadowOptions, mode);
  }

  @CanIgnoreReturnValue
  NestedCompilerRunner addScript(Node shadowScript, String uniqueShadowId) {
    checkArgument(
        shadowScript.getIsInClosureUnawareSubtree(),
        "Expected closureUnaware script, found %s",
        shadowScript);

    SourceFile shadowFile = SourceFile.fromCode(uniqueShadowId, "", SourceKind.STRONG);

    CompilerInput shadowInput = new CompilerInput(shadowFile, new InputId(uniqueShadowId));
    shadowInput.initShadowAst(shadowScript);

    shadowScript.setInputId(shadowInput.getInputId());
    shadowScript.setStaticSourceFile(shadowFile);
    // TODO: b/421971366 - should we also update the shadow script children with this source file?

    this.inputs.add(shadowInput);
    return this;
  }

  /** Initializes a new Compiler with all shadow ASTs and runs transpilation/optimizations */
  void compile() {
    initializeCompiler();
    runCompilation();
    checkInvariants();
  }

  /** Creates a new Compiler and initializes it with a chunk graph holding all shadow ASTs */
  private void initializeCompiler() {
    ImmutableSet<String> externNames =
        NodeUtil.collectExternVariableNames(original, original.getRoot().getFirstChild());
    shadowCompiler.addExportedNames(externNames);
    shadowCompiler.setExternProperties(original.getExternProperties());
    shadowCompiler.setDebugMessage("<shadow AST compilation>");

    ImmutableList<JSChunk> chunks = createChunks();
    shadowCompiler.initChunks(ImmutableList.of(), chunks, shadowOptions);
    shadowCompiler.parseForCompilation();
  }

  private ImmutableList<JSChunk> createChunks() {
    baseChunk.add(new CompilerInput(SourceFile.fromCode("synthetic_base", "", SourceKind.STRONG)));
    var chunks = ImmutableList.<JSChunk>builder().add(baseChunk);
    for (CompilerInput input : inputs) {
      JSChunk chunk = new JSChunk("shadow_chunk_" + input.getSourceFile().getName());
      chunk.add(input);
      chunk.addDependency(baseChunk);
      chunks.add(chunk);
    }
    return chunks.build();
  }

  /** Runs compiler passes over the shadow ASTs */
  private void runCompilation() {
    switch (mode) {
      case TRANSPILE_AND_OPTIMIZE -> {
        shadowCompiler.stage2Passes(CompilerOptions.SegmentOfCompilationToRun.OPTIMIZATIONS);
        delegateDiagnosticsToOriginal();
        if (shadowCompiler.hasHaltingErrors()) {
          return;
        }
        shadowCompiler.stage3Passes();
        delegateDiagnosticsToOriginal();
      }
      case TRANSPILE_ONLY -> {
        shadowCompiler.transpileAndDontCheck();
        delegateDiagnosticsToOriginal();
      }
    }
  }

  private void delegateDiagnosticsToOriginal() {
    for (JSError error : shadowCompiler.getErrors()) {
      original.report(error);
    }
    for (JSError error : shadowCompiler.getWarnings()) {
      original.report(error);
    }
  }

  private void checkInvariants() {
    checkState(
        baseChunk.getInputs().size() == 1,
        "Expected exactly 1 base chunk input, got %s",
        baseChunk.getInputs());

    // Check that compilation did not introduce any new code into the base chunk: we don't
    // have a place to put that code in the main AST, once we move out of this nested compilation.
    // Note: if it turns out to be useful to have some shared common base code for shadow ASTs, we
    // could consider designing a "common" closureUnaware shadow AST that goes in the actual main
    // AST root chunk. But we don't have a use case for that yet.
    Node baseInputNode = baseChunk.getInputs().get(0).getAstRoot(shadowCompiler);
    checkState(
        !baseInputNode.hasChildren(),
        "Expected synthetic base input chunk to have zero children, but found %s",
        baseInputNode.toStringTree());
  }
}
