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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.Set;

/**
 * Gathers property names defined in externs.
 */
class GatherExternProperties extends AbstractPostOrderCallback
    implements CompilerPass {
  private final Set<String> externProperties = Sets.newHashSet();
  private final AbstractCompiler compiler;

  public GatherExternProperties(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, externs, this);
    compiler.setExternProperties(ImmutableSet.copyOf(externProperties));
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.GETPROP:
        // Gathers "name" from (someObject.name).
        Node dest = n.getFirstChild().getNext();
        if (dest.isString()) {
          externProperties.add(dest.getString());
        }
        break;
      case Token.OBJECTLIT:
        // Gathers "name" and "address" from ({name: null, address: null}).
        for (Node child = n.getFirstChild();
             child != null;
             child = child.getNext()) {
          externProperties.add(child.getString());
        }
        break;
    }
  }
}

