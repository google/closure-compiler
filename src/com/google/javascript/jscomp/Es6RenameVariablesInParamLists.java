/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.common.collect.HashBasedTable;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Renames variables declared in function bodies so they don't shadow any variable referenced in the
 * param list.
 *
 * <p>This transformation prevents name collisions when executable code from the param list is moved
 * into the function body.
 */
public final class Es6RenameVariablesInParamLists
    implements NodeTraversal.ScopedCallback, CompilerPass {

  private final AbstractCompiler compiler;
  private static final FeatureSet transpiledFeatures =
      FeatureSet.BARE_MINIMUM.with(Feature.DEFAULT_PARAMETERS, Feature.COMPUTED_PROPERTIES);

  public Es6RenameVariablesInParamLists(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return true;
  }

  @Override
  public void enterScope(NodeTraversal t) {}

  @Override
  public void exitScope(NodeTraversal t) {
    Node block = t.getScopeRoot();
    Node function = block.getParent();
    if (!block.isBlock() || !function.isFunction()) {
      return;
    }

    final CollectReferences collector = new CollectReferences();
    collector.collect(function);

    Scope blockScope = t.getScope();
    HashBasedTable<Node, String, String> renameTable = HashBasedTable.create();
    Map<String, String> renameRow = renameTable.row(block);
    for (Var var : blockScope.getVarIterable()) {
      String oldName = var.getName();
      if (collector.currFuncReferences.contains(oldName)) {
        renameRow.computeIfAbsent(
            oldName, (x) -> oldName + "$" + compiler.getUniqueNameIdSupplier().get());
      }
    }

    // TODO(nickreid): This could probably be reowrked into a single traversal if the renameTable
    // were updated dynamically during enterScope.
    NodeTraversal.builder()
        .setCompiler(compiler)
        .setScopeCreator(t.getScopeCreator())
        .setCallback(new Es6RenameReferences(renameTable))
        .traverseAtScope(blockScope);
  }

  @Override
  public void visit(NodeTraversal t, Node block, Node function) {}

  @Override
  public void process(Node externs, Node root) {
    TranspilationPasses.processTranspile(compiler, root, transpiledFeatures, this);
  }

  /** Collects all references in a naive way. */
  private static class CollectReferences {

    private final Set<String> currFuncReferences = new HashSet<>();

    void collect(Node fn) {
      checkState(fn.isFunction());
      Node paramList = fn.getSecondChild();
      findCandiates(paramList);
    }

    void findCandiates(Node n) {
      Node parent = n.getParent();
      if ((parent.isDefaultValue() && n == parent.getLastChild())
          || (parent.isComputedProp() && n == parent.getFirstChild())) {
        visitCandiates(n);
        // Don't visit the children of these nodes.
        return;
      }
      for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
        visitCandiates(c);
      }
    }

    void visitCandiates(Node n) {
      if (NodeUtil.isReferenceName(n)) {
        currFuncReferences.add(n.getString());
      }
      for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
        visitCandiates(c);
      }
    }
  }
}
