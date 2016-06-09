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

import java.util.Collection;

/**
 * Disambiguate properties by file, when they are private by naming convention.
 *
 * This pass is unsafe. When some code doesn't respect the coding convention,
 * the pass silently breaks the code.
 * Projects who use this pass must also turn on CheckAccessControls for code
 * using the coding convention (by default CheckAccessControls looks only at
 * jsdoc annotations).
 *
 * If someone plans to make this pass non-experimental, or turn it on by
 * default, they should modify CheckAccessControls to store violations, and use
 * that list here to back-off renaming, like DisambiguateProperties does.
 *
 * Another option is that, in CompilerOptionsPreprocessor, if this pass is
 * enabled, but the access controls are not, to throw an error.
 */
class DisambiguatePrivateProperties
   implements NodeTraversal.Callback, CompilerPass {

  private final AbstractCompiler compiler;
  private final CodingConvention convention;
  private final ImmutableSet<String> blacklist;
  private String fileid;
  private int id = 0;

  DisambiguatePrivateProperties(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.convention = this.compiler.getCodingConvention();
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
      case GETPROP:
        maybeRename(n.getLastChild());
        break;
      case STRING_KEY:
      case GETTER_DEF:
      case SETTER_DEF:
      case MEMBER_FUNCTION_DEF:
        maybeRename(n);
        break;
    }
  }

  private void maybeRename(Node n) {
    String prop = n.getString();
    if (!n.getBooleanProp(Node.QUOTED_PROP) && this.convention.isPrivate(prop)
        && !blacklist.contains(prop)) {
      n.setString(prop + fileid);
      compiler.reportCodeChange();
    }
  }
}
