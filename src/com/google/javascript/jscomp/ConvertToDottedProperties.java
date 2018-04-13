/*
 * Copyright 2007 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/**
 * Converts property accesses from quoted string syntax to dot syntax, where
 * possible. Dot syntax is more compact and avoids an object allocation in
 * IE 6.
 *
 */
class ConvertToDottedProperties extends AbstractPostOrderCallback
    implements CompilerPass {

  private final AbstractCompiler compiler;

  ConvertToDottedProperties(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case GETTER_DEF:
      case SETTER_DEF:
      case STRING_KEY:
        if (NodeUtil.isValidPropertyName(FeatureSet.ES3, n.getString())) {
          if (n.getBooleanProp(Node.QUOTED_PROP)) {
            n.putBooleanProp(Node.QUOTED_PROP, false);
            compiler.reportChangeToEnclosingScope(n);
          }
        }
        break;

      case GETELEM:
        Node left = n.getFirstChild();
        Node right = left.getNext();
        if (right.isString() && NodeUtil.isValidPropertyName(FeatureSet.ES3, right.getString())) {
          n.removeChild(left);
          n.removeChild(right);
          Node newGetProp = IR.getprop(left, right);
          parent.replaceChild(n, newGetProp);
          compiler.reportChangeToEnclosingScope(newGetProp);
        }
        break;
      default:
        break;
    }
  }
}
