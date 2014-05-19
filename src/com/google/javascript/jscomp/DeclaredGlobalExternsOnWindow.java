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
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A compiler pass to normalize externs by declaring global names on
 * the "window" object.
 */
class DeclaredGlobalExternsOnWindow
    extends NodeTraversal.AbstractShallowStatementCallback
    implements CompilerPass {

  private final AbstractCompiler compiler;
  private final Set<Node> nodes = new LinkedHashSet<>();

  public DeclaredGlobalExternsOnWindow(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, externs, this);
    addWindowProperties();
  }

  private void addWindowProperties() {
    if (nodes.size() > 0) {
      for (Node node : nodes) {
        addExtern(node);
      }
      compiler.reportCodeChange();
    }
  }

  private static void addExtern(Node node) {
    String name = node.getString();
    JSDocInfo oldJSDocInfo = NodeUtil.getBestJSDocInfo(node);

    Node window = IR.name("window");
    Node string = IR.string(name);
    Node getprop = IR.getprop(window, string);
    Node newNode = getprop;

    if (oldJSDocInfo != null) {
      JSDocInfoBuilder builder;

      if (oldJSDocInfo.isConstructor() || oldJSDocInfo.isInterface()
          || oldJSDocInfo.hasEnumParameterType()) {
        Node nameNode = IR.name(name);
        newNode = IR.assign(getprop, nameNode);

        builder = new JSDocInfoBuilder(false);
        if (oldJSDocInfo.isConstructor()) {
          builder.recordConstructor();
        }
        if (oldJSDocInfo.isInterface()) {
          builder.recordInterface();
        }
        if (oldJSDocInfo.hasEnumParameterType()) {
          builder.recordEnumParameterType(oldJSDocInfo.getEnumParameterType());
        }
      } else {
        builder = JSDocInfoBuilder.copyFrom(oldJSDocInfo);
      }

      builder.recordSuppressions(ImmutableSet.of("duplicate"));
      JSDocInfo jsDocInfo = builder.build(newNode);
      jsDocInfo.setAssociatedNode(newNode);
      newNode.setJSDocInfo(jsDocInfo);
    }

    NodeUtil.setDebugInformation(newNode, node, name);
    node.getParent().getParent().addChildToBack(IR.exprResult(newNode));
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isFunction()) {
      nodes.add(n.getFirstChild());
    } else if (n.isVar()) {
      for (Node c : n.children()) {
        // Skip 'location' since there is an existing definition
        // for window.location which conflicts with the "var location" one.
        if (!c.getString().equals("location")) {
          nodes.add(c);
        }
      }
    }
  }

}
