/*
 * Copyright 2008 The Closure Compiler Authors.
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

import com.google.common.collect.Maps;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Map;

/**
 * Insures '@constructor X' has a 'goog.provide("X")' .
 *
 */
class CheckProvides implements CompilerPass {
  private final AbstractCompiler compiler;
  private final CheckLevel checkLevel;
  private final CodingConvention codingConvention;

  static final DiagnosticType MISSING_PROVIDE_WARNING = DiagnosticType.disabled(
      "JSC_MISSING_PROVIDE",
      "missing goog.provide(''{0}'')");

  CheckProvides(AbstractCompiler compiler, CheckLevel checkLevel) {
    this.compiler = compiler;
    this.checkLevel = checkLevel;
    this.codingConvention = compiler.getCodingConvention();
  }

  @Override
  public void process(Node externs, Node root) {
    CheckProvidesCallback callback =
      new CheckProvidesCallback(codingConvention);
    new NodeTraversal(compiler, callback).traverse(root);
  }

  private class CheckProvidesCallback extends AbstractShallowCallback {
    private final Map<String, Node> provides = Maps.newHashMap();
    private final Map<String, Node> ctors = Maps.newHashMap();
    private final CodingConvention convention;

    CheckProvidesCallback(CodingConvention convention){
      this.convention = convention;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()) {
        case Token.CALL:
          String providedClassName =
            codingConvention.extractClassNameIfProvide(n, parent);
          if (providedClassName != null) {
            provides.put(providedClassName, n);
          }
          break;
        case Token.FUNCTION:
          visitFunctionNode(n, parent);
          break;
        case Token.SCRIPT:
          visitScriptNode(t, n);
      }
    }

    private void visitFunctionNode(Node n, Node parent) {
      Node name = null;
      JSDocInfo info = parent.getJSDocInfo();
      if (info != null && info.isConstructor()) {
        name = parent.getFirstChild();
      } else {
        // look to the child, maybe it's a named function
        info = n.getJSDocInfo();
        if (info != null && info.isConstructor()) {
          name = n.getFirstChild();
        }
      }
      if (name != null && name.isQualifiedName()) {
        String qualifiedName = name.getQualifiedName();
        if (!this.convention.isPrivate(qualifiedName)) {
          Visibility visibility = info.getVisibility();
          if (!visibility.equals(JSDocInfo.Visibility.PRIVATE)) {
            ctors.put(qualifiedName, name);
          }
        }
      }
    }

    private void visitScriptNode(NodeTraversal t, Node n) {
      for (String ctorName : ctors.keySet()) {
        if (!provides.containsKey(ctorName)) {
          compiler.report(
              t.makeError(ctors.get(ctorName), checkLevel,
                  MISSING_PROVIDE_WARNING, ctorName));
        }
      }
      provides.clear();
      ctors.clear();
    }
  }
}
