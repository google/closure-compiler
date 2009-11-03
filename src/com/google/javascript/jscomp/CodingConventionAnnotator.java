/*
 * Copyright 2009 Google Inc.
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

import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Annotates any nodes to which the coding convention applies so that the
 * annotations on the nodes, instead of the coding convention, can be used
 * by optimization passes.
 *
*
 */
class CodingConventionAnnotator extends NodeTraversal.AbstractPostOrderCallback
    implements CompilerPass {

  private CodingConvention convention;
  private AbstractCompiler compiler;

  public CodingConventionAnnotator(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.convention = compiler.getCodingConvention();
  }

  @Override
  public void process(Node externs, Node root) {
    if (externs != null) {
      NodeTraversal.traverse(compiler, externs, this);
    }
    if (root != null) {
      NodeTraversal.traverse(compiler, root, this);
    }
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.NAME:
      case Token.STRING:
        if (convention.isConstant(n.getString())) {
          n.putBooleanProp(Node.IS_CONSTANT_NAME, true);
        }
        break;

      case Token.FUNCTION:
        JSDocInfo fnInfo = n.getJSDocInfo();
        if (fnInfo == null) {
          // Look for the info on other nodes.
          if (parent.getType() == Token.ASSIGN) {
            // on ASSIGNs
            fnInfo = parent.getJSDocInfo();
          } else if (parent.getType() == Token.NAME) {
            // on var NAME = function() { ... };
            fnInfo = parent.getParent().getJSDocInfo();
          }
        }

        // Compute which function parameters are optional and
        // which are var_args.
        Node args = n.getFirstChild().getNext();
        for (Node arg = args.getFirstChild();
             arg != null;
             arg = arg.getNext()) {
          String argName = arg.getString();
          JSTypeExpression typeExpr = fnInfo == null ?
              null : fnInfo.getParameterType(argName);

          if (convention.isOptionalParameter(argName) ||
              typeExpr != null && typeExpr.isOptionalArg()) {
            arg.putBooleanProp(Node.IS_OPTIONAL_PARAM, true);
          }
          if (convention.isVarArgsParameter(arg, argName) ||
              typeExpr != null && typeExpr.isVarArgs()) {
            arg.putBooleanProp(Node.IS_VAR_ARGS_PARAM, true);
          }
        }
        break;
    }
  }
}
