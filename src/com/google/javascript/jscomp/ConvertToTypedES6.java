/*
 * Copyright 2015 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.parsing.TypeDeclarationsIRFactory.convert;

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Converts JS with types in jsdocs to an extended JS syntax that includes types.
 * (Still keeps the jsdocs intact.)
 *
 * @author alexeagle@google.com (Alex Eagle)
 *
 * TODO(alexeagle): handle inline-style JSDoc annotations as well.
 */
public class ConvertToTypedES6
    extends AbstractPostOrderCallback implements CompilerPass {

  private final AbstractCompiler compiler;

  public ConvertToTypedES6(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.FUNCTION:
        JSDocInfo bestJSDocInfo = NodeUtil.getBestJSDocInfo(n);
        if (bestJSDocInfo != null) {
          n.setDeclaredTypeExpression(convert(bestJSDocInfo.getReturnType()));
          compiler.reportCodeChange();
        }
        break;
      case Token.NAME:
        if (parent == null) {
          break;
        }
        JSDocInfo parentJSDoc = NodeUtil.getBestJSDocInfo(parent);
        if (parentJSDoc == null) {
          break;
        }
        if (parent.isVar()) {
          n.setDeclaredTypeExpression(convert(parentJSDoc.getType()));
          compiler.reportCodeChange();
        } else if (parent.isParamList()) {
          JSTypeExpression parameterType =
              parentJSDoc.getParameterType(n.getString());
          if (parameterType != null) {
            n.setDeclaredTypeExpression(convert(parameterType));
            compiler.reportCodeChange();
          }
        }
        break;
      default:
        break;
    }
  }
}
