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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.LinkedHashMultimap;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.Node;

/**
 * Runs a new Compiler instance over all @closureUnaware code (i.e. "shadow roots") in the AST.
 *
 * <p>This is a separate Compiler instance to avoid leaking compiler state between the shadow AST
 * and main AST. Otherwise, we risk doing unsafe optimizations on @closureUnaware code.
 */
class TranspileAndOptimizeClosureUnaware implements CompilerPass {
  private final AbstractCompiler original;

  TranspileAndOptimizeClosureUnaware(AbstractCompiler original) {
    this.original = original;
  }

  @Override
  public void process(Node externs, Node root) {
    var collector = new CollectShadowAsts();
    NodeTraversal.traverse(original, root, collector);
    if (collector.shadowAsts.isEmpty()) {
      return;
    }

    var shadowOptions = ClosureUnawareOptions.convert(original.getOptions());
    // TODO: b/421971366 - enable configuring Mode.TRANSPILE_ONLY.
    NestedCompilerRunner shadowCompiler =
        NestedCompilerRunner.create(
            original, shadowOptions, NestedCompilerRunner.Mode.TRANSPILE_AND_OPTIMIZE);

    initShadowInputs(shadowCompiler, collector.shadowAsts);
    shadowCompiler.compile();
    reattachShadowNodes(collector.shadowAsts);
  }

  private record ShadowAst(Node originalRoot, Node callNode, Node script) {}

  /** Traverses all closure unaware SCRIPTs to collect shadow ASTs. */
  private static final class CollectShadowAsts implements NodeTraversal.Callback {
    private final LinkedHashMultimap<SourceFile, ShadowAst> shadowAsts =
        LinkedHashMultimap.create();

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (!n.isScript()) {
        return true;
      }
      return n.isClosureUnawareCode();
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      Node shadow = n.getClosureUnawareShadow();
      if (shadow == null) {
        return;
      }
      // CALL
      //   GETPROP .call
      //     NAME $jscomp_wrap_closure_unaware_code
      // NAME globalThis
      //   or
      // CALL
      //   GETPROP .call
      //     NAME $jscomp_wrap_closure_unaware_code
      // NAME undefined
      //   or
      // CALL
      //   NAME $jscomp_wrap_closure_unaware_code
      Node callNode = n.getParent().isCall() ? n.getParent() : n.getGrandparent();
      checkState(callNode.isCall(), callNode);
      shadowAsts.put(
          t.getInput().getSourceFile(), new ShadowAst(shadow, callNode, shadow.getOnlyChild()));
    }
  }

  private void initShadowInputs(
      NestedCompilerRunner shadowCompiler,
      LinkedHashMultimap<SourceFile, ShadowAst> perFileInputs) {
    for (SourceFile sourceFile : perFileInputs.keySet()) {
      int indexInFile = 0;
      for (ShadowAst shadowAst : perFileInputs.get(sourceFile)) {
        String uniqueName = sourceFile.getName() + ".shadow" + indexInFile++;
        shadowCompiler.addScript(shadowAst.script().detach(), uniqueName);

        // TODO: b/421971366 - attach the correct FeatureSet, either here or possibly during
        // parsing.
        shadowAst.script().putProp(Node.FEATURE_SET, FeatureSet.BARE_MINIMUM);
      }
    }
  }

  /**
   * Re-attaches the shadow AST scripts, that were previously detached for compilation, to their
   * associated ROOT nodes from the main AST.
   */
  private void reattachShadowNodes(LinkedHashMultimap<SourceFile, ShadowAst> inputs) {
    for (ShadowAst shadowAst : inputs.values()) {
      Node script = shadowAst.script();
      checkState(script.hasOneChild(), script.toStringTree());
      shadowAst.originalRoot().addChildToFront(script.detach());
      // Remove traces of the shadow compilation.
      script.setInputId(null);
      script.setStaticSourceFile(null);
    }
  }
}
