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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.javascript.jscomp.SyntacticScopeCreator.RedeclarationHandler;
import com.google.javascript.rhino.Node;
import java.util.LinkedHashSet;

/**
 * Deletes name declarations from synthetic externs if they are in fact declared in source
 *
 * <p>The VarCheck pass will create synthetic extern declarations (`var someName;`) for every
 * variable name referenced without a corresponding externs or code declaration. Such names are
 * "unfulfilled". VarCheck only does this because later compiler passes expect the following
 * invariant to hold: all variable names referenced in externs or code are also declared in externs
 * or code.
 *
 * <p>When running optimizations based on precompiled TypedAST library shards, it's possible for a
 * name to be unfulfilled in shard A, but declared in another shard B. Shard B "fulfills" the
 * reference from shard A. This pass removes the corresponding synthetic extern that came from shard
 * A now that the name is fulfilled. The purpose is to improve optimizations, e.g. to allow mangling
 * the name if it is not defined in externs.
 */
final class RemoveUnnecessarySyntheticExterns implements CompilerPass {

  private final AbstractCompiler compiler;
  private final LinkedHashSet<Node> nodesToDetach = new LinkedHashSet<>();

  RemoveUnnecessarySyntheticExterns(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    Node globalRoot = externs.getParent();
    SyntacticScopeCreator scopeCreator =
        new SyntacticScopeCreator(compiler, new RedeclarationCheckHandler(), true);

    // This call will invoke the RedeclarationCheckHandler on duplicate synthetic names
    scopeCreator.createScope(globalRoot, /* parent= */ null);

    if (nodesToDetach.isEmpty()) {
      return;
    }

    for (Node n : nodesToDetach) {
      n.detach();
    }

    Node syntheticExternsScript = compiler.getSynthesizedExternsInput().getAstRoot(compiler);
    compiler.reportChangeToEnclosingScope(syntheticExternsScript);
  }

  /** The handler for duplicate declarations. */
  private class RedeclarationCheckHandler implements RedeclarationHandler {

    @Override
    public void onRedeclaration(Scope s, String name, Node n, CompilerInput input) {
      checkArgument(s.isGlobal(), "Unexpected non-global redeclaration %s in scope %s", n, s);

      boolean isSynthetic = input.equals(compiler.getSynthesizedExternsInput());
      Var origVar = s.getVar(name);
      // origNode will be null for `arguments`, since there's no node that declares it.
      Node origNode = origVar.getNode();

      if (isSynthetic) {
        // Delete duplicate unfulfilled synthetic declarations.
        // The original declaration will be deleted later iff it's also synthetic and we visit a
        // non-synthetic duplicate declaration.
        if (n.getParent().isSynthesizedUnfulfilledNameDeclaration()) {
          nodesToDetach.add(n.getParent());
        }
      } else if (origNode != null
          && origNode.getParent().isSynthesizedUnfulfilledNameDeclaration()) {
        // Found a synthetic extern declaration that's fulfilled by some non-synthetic code.
        nodesToDetach.add(origNode.getParent());
      }
    }
  }
}
