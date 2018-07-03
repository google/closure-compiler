/*
 * Copyright 2016 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;

/**
 * Check for errors related to the `super` keyword.
 */
final class CheckSuper implements HotSwapCompilerPass, Callback {
  static final DiagnosticType MISSING_CALL_TO_SUPER =
      DiagnosticType.error("JSC_MISSING_CALL_TO_SUPER", "constructor is missing a call to super()");

  static final DiagnosticType THIS_BEFORE_SUPER =
      DiagnosticType.error("JSC_THIS_BEFORE_SUPER", "cannot access this before calling super()");

  static final DiagnosticType INVALID_SUPER_CALL = DiagnosticType.error(
      "JSC_INVALID_SUPER_CALL",
      "super() not allowed except in the constructor of a subclass");

  static final DiagnosticType INVALID_SUPER_USAGE =
      DiagnosticType.error(
          "JSC_INVALID_SUPER_USAGE", "''super'' may only be used in a call or property access");

  static final DiagnosticType INVALID_SUPER_ACCESS =
      DiagnosticType.error(
          "JSC_INVALID_SUPER_ACCESS", "''super'' may only be accessed within a class method");

  static final DiagnosticType INVALID_SUPER_CALL_WITH_SUGGESTION = DiagnosticType.error(
      "JSC_INVALID_SUPER_CALL_WITH_SUGGESTION",
      "super() not allowed here. Did you mean super.{0}?");

  private final AbstractCompiler compiler;

  public CheckSuper(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (n.isClass()) {
      return visitClass(t, n);
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isSuper()) {
      visitSuper(t, n, parent);
    }
  }

  /**
   * @return Whether to continue traversing this class
   */
  private boolean visitClass(NodeTraversal t, Node n) {
    Node superclass = n.getSecondChild();
    if (superclass.isEmpty()) {
      return true;
    }

    Node constructor =
        NodeUtil.getFirstPropMatchingKey(NodeUtil.getClassMembers(n), "constructor");
    if (constructor == null) {
      return true;
    }

    FindSuperOrReturn finder = new FindSuperOrReturn();
    NodeTraversal.traverse(compiler, NodeUtil.getFunctionBody(constructor), finder);

    if (!finder.found) {
      t.report(constructor, MISSING_CALL_TO_SUPER);
      return false;
    }
    return true;
  }

  private void visitSuper(NodeTraversal t, Node n, Node parent) {
    if (parent.isCall()) {
      visitSuperCall(t, n, parent);
    } else if (parent.isGetProp() || parent.isGetElem()) {
      visitSuperAccess(t, n);
    } else {
      t.report(n, INVALID_SUPER_USAGE);
    }
  }

  private void visitSuperCall(NodeTraversal t, Node n, Node parent) {
    Node classNode = NodeUtil.getEnclosingClass(n);
    if (classNode == null || classNode.getSecondChild().isEmpty()) {
      t.report(n, INVALID_SUPER_CALL);
      return;
    }

    Node fn = NodeUtil.getEnclosingFunction(parent);
    if (fn == null) {
      t.report(n, INVALID_SUPER_CALL);
      return;
    }

    Node memberDef = fn.getParent();
    if (memberDef.isMemberFunctionDef()) {
      if (memberDef.matchesQualifiedName("constructor")) {
        // No error.
      } else {
        t.report(n, INVALID_SUPER_CALL_WITH_SUGGESTION, memberDef.getString());
      }
    } else {
      t.report(n, INVALID_SUPER_CALL);
    }
  }

  private void visitSuperAccess(NodeTraversal t, Node n) {
    Node classNode = NodeUtil.getEnclosingClass(n);
    if (classNode == null) {
      t.report(n, INVALID_SUPER_ACCESS);
      return;
    }
  }

  private static final class FindSuperOrReturn implements Callback {
    boolean found = false;

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      // Stop traversal once the super() call is found. Also don't traverse into nested functions
      // since this and super() references may not be applicable within those scopes.
      return !found && !n.isFunction();
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isThis()) {
        t.report(n, THIS_BEFORE_SUPER);
      }
      if (n.isSuper() && parent.isCall()) {
        found = true;
        return;
      }
      // allow return <some expr>; instead of super(), which works at runtime as long as
      //  <some expr> is an object.
      if (n.isReturn() && n.hasChildren()) {
        found = true;
        return;
      }
    }
  }
}
