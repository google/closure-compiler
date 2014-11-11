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

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Gathers character encoding information based on parts of the code
 * that would not be renamed.
 *
 */
class GatherCharacterEncodingBias extends AbstractPostOrderCallback
    implements CompilerPass {
  private final NameGenerator nameGenerator;
  private final AbstractCompiler compiler;
  private final boolean renameGlobaVars;
  private final boolean renameProperties;

  public GatherCharacterEncodingBias(
      final AbstractCompiler compiler, final NameGenerator ng,
      boolean renameGlobalVars, boolean renameProperties) {
    this.compiler = compiler;
    this.nameGenerator = ng;
    this.renameGlobaVars = renameGlobalVars;
    this.renameProperties = renameProperties;
  }

  public GatherCharacterEncodingBias(
      final AbstractCompiler compiler, final NameGenerator ng,
      boolean renameGlobalVars) {
    this(compiler, ng, renameGlobalVars, true);
  }

  @Override
  public void process(Node externs, Node root) {
    // TODO(user): Traverse the externs and get and get an estimate
    // of what we cannot rename. Use that to estimate some bias information.
    new NodeTraversal(compiler, this).traverse(root);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.TRUE:
      case Token.FALSE:
      // TRUE and FALSE are purposely skipped as this gets removed in a late
      // peephole optimization pass.
        return;

      case Token.LABEL_NAME:
        return;

      // Case dealing with renamed names / properties are *NOT* handled here.
      // The idea is not to duplicate logics of variable renaming and property
      // renaming.
      case Token.NAME:
        visitName(t, n);
        return;

      case Token.GETPROP:
        visitGetProp(n);
        return;

      case Token.FUNCTION:
        if (!NodeUtil.isGetOrSetKey(n.getParent())) {
          nameGenerator.favors("function");
        }
        return;
      case Token.IF:
        nameGenerator.favors("if");
        if (n.getFirstChild().getNext().getNext() != null) {
          nameGenerator.favors("else");
        }
        return;
      case Token.FOR:
        nameGenerator.favors("for");
        return;
      // Probably not needed after normalization.
      case Token.WHILE:
        nameGenerator.favors("while");
        return;
      case Token.VAR:
        nameGenerator.favors("var");
        return;
      case Token.STRING:
        if (!parent.isGetProp()) {
          nameGenerator.favors(n.getString());
        }
        return;
      case Token.STRING_KEY:
        // TODO(user): Currently not taking into account of quoted properties
        // and 'advanced mode' stuff.
        nameGenerator.favors(n.getString());
        return;
      case Token.TRY:
        nameGenerator.favors("try");
        if (NodeUtil.hasFinally(n)) {
          nameGenerator.favors("finally");
        }
        return;
      case Token.CATCH:
        nameGenerator.favors("catch");
        return;
      case Token.SWITCH:
        nameGenerator.favors("switch");
        return;
      case Token.CASE:
        nameGenerator.favors("case");
        return;
      case Token.DEFAULT_CASE:
        nameGenerator.favors("default");
        return;
      case Token.NEW:
        nameGenerator.favors("new");
        return;
      case Token.RETURN:
        nameGenerator.favors("return");
        return;
      case Token.DO:
        nameGenerator.favors("do");
        nameGenerator.favors("while");
        return;
      case Token.VOID:
        nameGenerator.favors("void");
        return;
      case Token.WITH:
        nameGenerator.favors("with");
        return;
      case Token.DELPROP:
        nameGenerator.favors("delete");
        return;
      case Token.TYPEOF:
        nameGenerator.favors("typeof");
        return;
      case Token.THROW:
        nameGenerator.favors("throw");
        return;
      case Token.IN:
        nameGenerator.favors("in");
        return;
      case Token.INSTANCEOF:
        nameGenerator.favors("instanceof");
        return;
      case Token.BREAK:
        nameGenerator.favors("break");
        return;
      case Token.CONTINUE:
        nameGenerator.favors("continue");
        return;
      case Token.THIS:
        nameGenerator.favors("this");
        return;
      case Token.NULL:
        nameGenerator.favors("null");
        return;
      case Token.DEBUGGER:
        nameGenerator.favors("debugger");
        return;
      case Token.GETTER_DEF:
        visitGetterSetterDef(n, "get");
        return;
      case Token.SETTER_DEF:
        visitGetterSetterDef(n, "set");
        return;
      case Token.NUMBER:
        // TODO(user): This has to share some code with the code generator
        // to figure out how the number will eventually be printed.
        return;
    }
  }

  private void visitName(NodeTraversal t, Node n) {
    Preconditions.checkArgument(n.getType() == Token.NAME);
    if (renameGlobaVars) {
      // Assumes everything can be renamed. Don't change bias.
      return;
    }
    String name = n.getString();
    Var var = t.getScope().getVar(name);
    if (var == null || var.scope.isGlobal()) {
      nameGenerator.favors(name);
    }
  }

  private void visitGetProp(Node n) {
    Preconditions.checkArgument(n.getType() == Token.GETPROP);
    if (!renameProperties) {
      nameGenerator.favors(n.getLastChild().getString());
    }
  }

  private void visitGetterSetterDef(Node n, String method) {
    nameGenerator.favors(method);
    if (!renameProperties) {
      nameGenerator.favors(n.getString());
    }
  }
}
