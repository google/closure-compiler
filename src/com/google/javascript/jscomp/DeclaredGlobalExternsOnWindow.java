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
 * the "window" object, if it is declared in externs.
 */
class DeclaredGlobalExternsOnWindow
    extends NodeTraversal.AbstractShallowStatementCallback
    implements CompilerPass {

  private final AbstractCompiler compiler;
  private final Set<Node> nodes = new LinkedHashSet<>();

  // Whether there is a "var window" declaration in the externs.
  private boolean windowInExterns = false;

  public DeclaredGlobalExternsOnWindow(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, externs, this);
    addWindowProperties();
  }

  private void addWindowProperties() {
    if (!nodes.isEmpty() && windowInExterns) {
      for (Node node : nodes) {
        addExtern(node);
      }
      compiler.reportCodeChange();
    }
  }

  private static void addExtern(Node node) {
    String name = node.getString();
    JSDocInfo oldJSDocInfo = NodeUtil.getBestJSDocInfo(node);

    // TODO(tbreisacher): Consider adding externs to 'this' instead of 'window',
    // for environments where the global object is not called 'window.'
    Node window = IR.name("window");
    Node string = IR.string(name);
    Node getprop = IR.getprop(window, string);
    Node newNode = getprop;

    if (oldJSDocInfo != null) {
      JSDocInfoBuilder builder;

      if (oldJSDocInfo.isConstructorOrInterface()
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
        if (oldJSDocInfo.usesImplicitMatch()) {
          builder.recordImplicitMatch();
        }
        if (oldJSDocInfo.hasEnumParameterType()) {
          builder.recordEnumParameterType(oldJSDocInfo.getEnumParameterType());
        }
      } else {
        if (NodeUtil.isNamespaceDecl(node)) {
          newNode = IR.assign(getprop, IR.name(name));
        }
        builder = JSDocInfoBuilder.copyFrom(oldJSDocInfo);
      }

      // TODO(blickly): Remove these suppressions when all externs declarations on window are gone.
      builder.recordSuppressions(ImmutableSet.of("const", "duplicate"));
      JSDocInfo jsDocInfo = builder.build();
      newNode.setJSDocInfo(jsDocInfo);
    }

    NodeUtil.setDebugInformation(newNode, node, name);
    node.getGrandparent().addChildToBack(IR.exprResult(newNode));
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isFunction()) {
      nodes.add(n.getFirstChild());
    } else if (n.isVar()) {
      for (Node c : n.children()) {
        if (c.getString().equals("window")) {
          windowInExterns = true;
          continue;
        }
        // Skip 'location' since there is an existing definition
        // for window.location which conflicts with the "var location" one.
        if (!c.getString().equals("location")) {
          nodes.add(c);
        }
      }
    }
  }

}
