/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Set;

/**
 * External references of the form: "window['xx']" indicate names that must
 * be reserved when variable renaming to avoid conflicts.
 *
 * @author johnlenz@google.com (John Lenz)
 */
class GatherRawExports extends AbstractPostOrderCallback
    implements CompilerPass {

  private final AbstractCompiler compiler;

  private static final String GLOBAL_THIS_NAME = "window";

  private final Set<String> exportedVariables = Sets.newHashSet();

  GatherRawExports(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    Preconditions.checkState(compiler.getLifeCycleStage().isNormalized());
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    Node sibling = n.getNext();
    if (sibling != null
        && sibling.getType() == Token.STRING
        && NodeUtil.isGet(parent)) {
      // TODO(johnlenz): Should we warn if we see a property name that
      // hasn't been exported?
      if (isGlobalThisObject(t, n)) {
        exportedVariables.add(sibling.getString());
      }
    }
  }

  private boolean isGlobalThisObject(NodeTraversal t, Node n) {
    if (n.getType() == Token.THIS) {
      return t.inGlobalScope();
    } else if (n.getType() == Token.NAME) {
      String varName = n.getString();
      if (varName.equals(GLOBAL_THIS_NAME)) {
        return true;
      }
    }
    return false;
  }

  public Set<String> getExportedVariableNames() {
    return exportedVariables;
  }
}
