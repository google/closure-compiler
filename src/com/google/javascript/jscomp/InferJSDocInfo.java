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

import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Set the JSDocInfo on all types.
 *
*
 */
class InferJSDocInfo extends AbstractPostOrderCallback
    implements CompilerPass {

  private AbstractCompiler compiler;
  private boolean inExterns;

  InferJSDocInfo(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  public void process(Node externs, Node root) {
    if (externs != null) {
      inExterns = true;
      NodeTraversal.traverse(compiler, externs, this);
    }
    if (root != null) {
      inExterns = false;
      NodeTraversal.traverse(compiler, root, this);
    }
  }

  public void visit(NodeTraversal t, Node n, Node parent) {
    JSDocInfo docInfo;

    switch (n.getType()) {
      case Token.FUNCTION:
        // Infer JSDocInfo on all programmer-defined types.
        // Conveniently, all programmer-defined types are functions.
        JSType fnType = n.getJSType();
        if (fnType == null) {
          break;
        }

        // There are four places the doc info could live.
        // 1) A FUNCTION node.
        // /** ... */ function f() { ... }
        // 2) An ASSIGN parent.
        // /** ... */ x = function () { ... }
        // 3) A NAME parent.
        // var x, /** ... */ y = function() { ... }
        // 4) A VAR gramps.
        // /** ... */ var x = function() { ... }
        docInfo = n.getJSDocInfo();
        if (docInfo == null &&
            (parent.getType() == Token.ASSIGN ||
             parent.getType() == Token.NAME)) {
          docInfo = parent.getJSDocInfo();

          if (docInfo == null) {
            Node gramps = parent.getParent();
            if (gramps != null && gramps.getType() == Token.VAR &&
                gramps.hasOneChild()) {
              docInfo = gramps.getJSDocInfo();
            }
          }
        }

        if (docInfo != null && fnType instanceof FunctionType) {
          FunctionType maybeCtorType = (FunctionType) fnType;
          maybeCtorType.setJSDocInfo(docInfo);
          if (maybeCtorType.isConstructor()) {
            maybeCtorType.getInstanceType().setJSDocInfo(docInfo);
          }
        }
        break;

      case Token.GETPROP:
        // Infer JSDocInfo on properties.
        // There are two ways to write doc comments on a property.
        //
        // 1)
        // /** @deprecated */
        // obj.prop = ...
        //
        // 2)
        // /** @deprecated */
        // obj.prop;
        if (NodeUtil.isExpressionNode(parent) ||
            (parent.getType() == Token.ASSIGN &&
             parent.getFirstChild() == n)) {
          docInfo = n.getJSDocInfo();
          if (docInfo == null) {
            docInfo = parent.getJSDocInfo();
          }
          if (docInfo != null) {
            JSType lhsType = n.getFirstChild().getJSType();
            if (lhsType != null &&
                lhsType instanceof ObjectType) {
              ObjectType objectType = (ObjectType) lhsType;
              objectType.setPropertyJSDocInfo(
                  n.getLastChild().getString(), docInfo, inExterns);
            }
          }
        }
        break;
    }
  }
}
