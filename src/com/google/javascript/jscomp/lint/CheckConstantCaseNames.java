/*
 * Copyright 2019 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.lint;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CodingConvention;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.Var;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

/**
 * This pass looks for module-level variable declarations that use CONSTANT_CASE, according to the
 * Google style guide, and verifies that they are also annotated @const or are in a const clause.
 *
 * <p>This pass could be extended to check CONSTANT_CASE properties in the future.
 *
 * <p>Non-module-level variables should always use camel case according to the Google style guide.
 * In order to not confuse users, this pass does not warn that they should be @const. (A more
 * correct lint check could warn that non-module-locals should not be constant case.)
 */
public class CheckConstantCaseNames implements NodeTraversal.Callback, CompilerPass {

  public static final DiagnosticType MISSING_CONST_PROPERTY =
      DiagnosticType.disabled(
          "JSC_MISSING_CONST_ON_CONSTANT_CASE",
          "CONSTANT_CASE name \"{0}\" is constant-by-convention, so must be explicitly"
              + " `const` or @const");

  public static final DiagnosticType REASSIGNED_CONSTANT_CASE_NAME =
      DiagnosticType.disabled(
          "JSC_REASSIGNED_CONSTANT_CASE_NAME",
          "CONSTANT_CASE name \"{0}\" is constant-by-convention but is reassigned. "
              + "Use camelCase instead.");

  private final AbstractCompiler compiler;
  private final CodingConvention convention;
  // Maps CONSTANT_CASE module-level names to their initializing NAME node
  private LinkedHashMap<String, Node> invalidNamesPerModule = new LinkedHashMap<>();
  // Subset of variables in `invalidNamesPerModule` that are mutated post-declaration.
  private LinkedHashSet<String> reassignedNames = new LinkedHashSet<>();

  public CheckConstantCaseNames(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.convention = compiler.getCodingConvention();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    // Only need to warn for module-level names, so don't visit other files.
    if (n.isScript()) {
      return n.hasChildren() && n.getFirstChild().isModuleBody();
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isModuleBody()) {
      reportWarningsAndClear();
      return;
    }
    switch (n.getToken()) {
      case VAR:
      case LET:
        // Skip CONST as it automatically meets the criteria and only look for module-level vars.
        if (!t.inModuleScope()) {
          return;
        }
        JSDocInfo info = n.getJSDocInfo();
        if (info != null && info.hasConstAnnotation()) {
          break;
        }
        for (Node name : NodeUtil.findLhsNodesInNode(n)) {
          if (convention.isConstant(name.getString())) {
            this.invalidNamesPerModule.put(name.getString(), name);
          }
        }
        break;

      case NAME:
        if (!this.invalidNamesPerModule.containsKey(n.getString())) {
          return;
        }
        if (!NodeUtil.isLValue(n)) {
          return;
        }
        // Verify this name is referring to the actual module-level var and not a local shadow.
        Var v = t.getScope().getVar(n.getString());
        if (v.getScopeRoot().isModuleBody()) {
          this.reassignedNames.add(n.getString());
        }
        break;

      default:
        break;
    }
  }

  private void reportWarningsAndClear() {
    for (Node nameNode : this.invalidNamesPerModule.values()) {
      String name = nameNode.getString();
      if (this.reassignedNames.contains(name)) {
        compiler.report(JSError.make(nameNode, REASSIGNED_CONSTANT_CASE_NAME, name));
      } else {
        compiler.report(JSError.make(nameNode, MISSING_CONST_PROPERTY, name));
      }
    }
    this.invalidNamesPerModule = new LinkedHashMap<>();
    this.reassignedNames = new LinkedHashSet<>();
  }
}
