/*
 * Copyright 2013 The Closure Compiler Authors.
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
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Collection;

/**
 * Disambiguate properties by file, when they are private by naming convention.
 */
class DisambiguatePrivateProperties
   implements NodeTraversal.Callback, CompilerPass {

  private final AbstractCompiler compiler;
  private final ImmutableSet<String> blacklist;
  private String fileid;
  private int id = 0;

  DisambiguatePrivateProperties(AbstractCompiler compiler) {
    this.compiler = compiler;
    CodingConvention convention = this.compiler.getCodingConvention();
    Collection<String> indirect = convention.getIndirectlyDeclaredProperties();
    blacklist = ImmutableSet.copyOf(indirect);
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (n.isScript()) {
      this.fileid = "$" + this.id++;
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.GETPROP:
        maybeRename(n.getLastChild());
        break;
      case Token.STRING_KEY:
      case Token.GETTER_DEF:
      case Token.SETTER_DEF:
      case Token.MEMBER_FUNCTION_DEF:
        maybeRename(n);
        break;
    }
  }

  private void maybeRename(Node n) {
    CodingConvention convention = compiler.getCodingConvention();
    String prop = n.getString();
    if (!n.getBooleanProp(Node.QUOTED_PROP) && convention.isPrivate(prop)
        && !blacklist.contains(prop)) {
      n.setString(prop + fileid);
      compiler.reportCodeChange();
    }
  }
}
