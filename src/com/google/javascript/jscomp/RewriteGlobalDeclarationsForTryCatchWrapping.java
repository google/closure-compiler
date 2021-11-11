/*
 * Copyright 2008 The Closure Compiler Authors.
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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

/**
 * Moves top-level function declarations to the top of the enclosing JSChunk and rewrites class
 * declarations.
 *
 * <p>Enable this pass if a try catch block wraps the output after compilation, and the output runs
 * on Firefox because function declarations are only defined when reached inside a try catch block
 * on older versions of Firefox.
 *
 * <p>On Firefox versions <= 45 (March 2016), this code works:
 *
 * <p>var g = f; function f() {}
 *
 * <p>but this code does not work:
 *
 * <p>try { var g = f; function f() {} } catch(e) {}
 *
 * <p>NOTE: As of Firefox version 46 the above code works. Projects not supporting older versions of
 * Firefox shouldn't need this pass.
 *
 * <p>RescopeGlobalSymbols still depends on this pass running first to preserve function hoisting
 * semantics.
 *
 * <p>NOTE: This pass is safe to turn on by default and delete the associated compiler option.
 * However, we don't do that because the pass is only useful for code wrapped in a try/catch, and
 * otherwise it makes debugging harder because it moves code around.
 *
 * <p>NOTE: now that this pass also moves class declarations it should be renamed along with the
 * associated options.
 */
class RewriteGlobalDeclarationsForTryCatchWrapping implements NodeTraversal.Callback, CompilerPass {
  private final AbstractCompiler compiler;
  private final ListMultimap<JSChunk, Node> functions = ArrayListMultimap.create();
  private final ArrayList<Node> classes = new ArrayList<>();

  RewriteGlobalDeclarationsForTryCatchWrapping(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
    for (Entry<JSChunk, List<Node>> entry : Multimaps.asMap(functions).entrySet()) {
      Node addingRoot = compiler.getNodeForCodeInsertion(entry.getKey());
      List<Node> fnNodes = Lists.reverse(entry.getValue());
      if (!fnNodes.isEmpty()) {
        for (Node n : fnNodes) {
          Node parent = n.getParent();
          n.detach();
          compiler.reportChangeToEnclosingScope(parent);

          Node nameNode = n.getFirstChild();
          String name = nameNode.getString();
          nameNode.setString("");
          addingRoot.addChildToFront(IR.var(IR.name(name), n).srcrefTreeIfMissing(n));
          compiler.reportChangeToEnclosingScope(nameNode);
        }
        compiler.reportChangeToEnclosingScope(addingRoot);
      }
    }

    for (Node n : classes) {
      // rewrite CLASS > NAME ... => VAR > NAME > CLASS > EMPTY ...
      Node originalNameNode = n.getFirstChild();
      originalNameNode.replaceWith(IR.empty());
      Node var = IR.var(originalNameNode);
      n.replaceWith(var);
      originalNameNode.addChildToFront(n);
      compiler.reportChangeToEnclosingScope(n);
    }
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    Node grandparent = n.getAncestor(2);
    return grandparent == null || !grandparent.isScript();
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (parent == null || !parent.isScript()) {
      return;
    }

    if (NodeUtil.isFunctionDeclaration(n)) {
      functions.put(t.getChunk(), n);
    }

    if (NodeUtil.isClassDeclaration(n)) {
      classes.add(n);
    }

    if (n.isConst() || n.isLet()) {
      n.setToken(Token.VAR);
      compiler.reportChangeToEnclosingScope(n);
    }
  }
}
