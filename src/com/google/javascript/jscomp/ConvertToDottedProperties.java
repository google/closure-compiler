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
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/**
 * Converts property accesses from quoted string or bracket access syntax to dot or unquoted string
 * syntax, where possible. Dot syntax is more compact.
 */
class ConvertToDottedProperties extends AbstractPostOrderCallback implements CompilerPass {

  private final AbstractCompiler compiler;

  ConvertToDottedProperties(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
    GatherGetterAndSetterProperties.update(this.compiler, externs, root);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case COMPUTED_PROP:
      case COMPUTED_FIELD_DEF:
        Node leftElem = n.getFirstChild();
        Node rightElem = leftElem.getNext();

        // not convert property named constructor.
        // ['constructor']() and constructor() are different.
        if (leftElem.isStringLit()
            && NodeUtil.isValidPropertyName(FeatureSet.ES3, leftElem.getString())
            && !leftElem.getString().equals("constructor")) {
          leftElem.detach();
          rightElem.detach();
          Node temp;
          if (n.isComputedProp()) {
            if (rightElem.isFunction()) {
              if (n.getBooleanProp(Node.COMPUTED_PROP_GETTER)) {

                temp = IR.getterDef(leftElem.getString(), rightElem);

              } else if (n.getBooleanProp(Node.COMPUTED_PROP_SETTER)) {
                temp = IR.setterDef(leftElem.getString(), rightElem);
              } else {
                if (n.getParent().isClassMembers()) {

                  temp = IR.memberFunctionDef(leftElem.getString(), rightElem);
                  NodeUtil.addFeatureToScript(
                      t.getCurrentScript(), Feature.MEMBER_DECLARATIONS, compiler);
                } else {
                  // TODO - further optimize this code to a member function
                  temp = IR.stringKey(leftElem.getString(), rightElem);
                }
              }

            } else {
              temp = IR.stringKey(leftElem.getString(), rightElem);
            }
          } else {
            temp = IR.memberFieldDef(leftElem.getString(), rightElem);
          }

          temp.setStaticMember(n.isStaticMember());
          n.replaceWith(temp);
          compiler.reportChangeToEnclosingScope(temp);
        }
        break;
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
      case OPTCHAIN_GETELEM:
      case GETELEM:
        Node left = n.getFirstChild();
        Node right = left.getNext();
        if (right.isStringLit()
            && NodeUtil.isValidPropertyName(FeatureSet.ES3, right.getString())) {
          left.detach();
          right.detach();
          Node newGetProp =
              n.isGetElem()
                  ? IR.getprop(left, right.getString())
                  : IR.startOptChainGetprop(left, right.getString());
          n.replaceWith(newGetProp);
          compiler.reportChangeToEnclosingScope(newGetProp);
        }
        break;
      default:
        break;
    }
  }
}
