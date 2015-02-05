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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Process aliases in goog.modules.
 * <pre>
 * goog.module('namespace');
 * var foo = goog.require('another.namespace');
 * ...
 * </pre>
 *
 * becomes
 *
 * <pre>
 * goog.provide('namespace');
 * goog.require('another.namespace');
 * goog.scope(function() {
 *   var foo = another.namespace;
 *   ...
 * });
 * </pre>
 *
 * @author johnlenz@google.com (John Lenz)
 */
public class ClosureRewriteModule
    implements NodeTraversal.Callback, HotSwapCompilerPass {

  // TODO(johnlenz): Don't use goog.scope as an intermediary; add type checker
  // support instead.
  // TODO(johnlenz): harden this class to warn about misuse
  // TODO(johnlenz): handle non-namespace module identifiers aka 'foo/bar'

  static final DiagnosticType INVALID_MODULE_IDENTIFIER =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_MODULE_IDENTIFIER",
          "Module idenifiers must be string literals");

  static final DiagnosticType INVALID_REQUIRE_IDENTIFIER =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_REQUIRE_IDENTIFIER",
          "goog.require parameter must be a string literal.");

  static final DiagnosticType INVALID_GET_IDENTIFIER =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_GET_IDENTIFIER",
          "goog.module.get parameter must be a string literal.");

  static final DiagnosticType INVALID_GET_CALL_SCOPE =
      DiagnosticType.error(
          "JSC_GOOG_MODULE_INVALID_GET_CALL_SCOPE",
          "goog.module.get can not be called in global scope.");

  private final AbstractCompiler compiler;

  private static class ModuleDescription {
    Node moduleDecl;
    String moduleNamespace = "";
    Node requireInsertNode = null;
    final Node moduleScopeRoot;
    final Node moduleStatementRoot;
    final List<Node> requires = new ArrayList<>();
    final List<Node> provides = new ArrayList<>();
    final List<Node> exports = new ArrayList<>();
    public Scope moduleScope = null;

    ModuleDescription(Node n) {
      if (isLoadModuleCall(n)) {
        this.moduleScopeRoot = getModuleScopeRootForLoadModuleCall(n);
        this.moduleStatementRoot = getModuleStatementRootForLoadModuleCall(n);
      } else {
        this.moduleScopeRoot = n;
        this.moduleStatementRoot = n;
      }
    }
  }

  // Per "goog.module" state need for rewriting.
  private ModuleDescription current = null;

  ClosureRewriteModule(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    // Each module is its own scope, prevent building a global scope,
    // so we can use the scope for the file.
    // TODO(johnlenz): this is a little odd, rework this once we have
    // a concept of a module scope.
    for (Node c = root.getFirstChild(); c != null; c = c.getNext()) {
      Preconditions.checkState(c.isScript());
      hotSwapScript(c, null);
    }
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (isModuleFile(n) || isLoadModuleCall(n)) {
      enterModule(n);
    }
    if (isGetModuleCall(n)) {
      rewriteGetModuleCall(t, n);
    }

    if (inModule()) {
      switch (n.getType()) {
        case Token.BLOCK:
          if (current.moduleScopeRoot == parent && parent.isFunction()) {
            current.moduleScope = t.getScope();
          }
          break;
        case Token.SCRIPT:
          current.moduleScope = t.getScope();
          break;
      }
    }

    return true;
  }

  private static boolean isCallTo(Node n, String qname) {
    return n.isCall()
        && n.getFirstChild().matchesQualifiedName(qname);
  }

  private static boolean isLoadModuleCall(Node n) {
    return isCallTo(n, "goog.loadModule");
  }

  private static boolean isGetModuleCall(Node n) {
    return isCallTo(n, "goog.module.get");
  }

  private void rewriteGetModuleCall(NodeTraversal t, Node n) {
    // "use(goog.module.get('a.namespace'))" to "use(a.namespace)"
    Node namespace = n.getFirstChild().getNext();
    if (!namespace.isString()) {
      t.report(namespace, INVALID_GET_IDENTIFIER);
      return;
    }

    if (!inModule() && t.inGlobalScope()) {
      t.report(namespace, INVALID_GET_CALL_SCOPE);
      return;
    }

    Node replacement = NodeUtil.newQName(compiler, namespace.getString());
    replacement.srcrefTree(namespace);

    n.getParent().replaceChild(n, replacement);
    compiler.reportCodeChange();
  }


  private static boolean isModuleFile(Node n) {
    return n.isScript() && n.hasChildren()
        && isGoogModuleCall(n.getFirstChild());
  }

  private void enterModule(Node n) {
    current = new ModuleDescription(n);
  }

  private boolean inModule() {
    return current != null;
  }

  private static boolean isGoogModuleCall(Node n) {
    if (NodeUtil.isExprCall(n)) {
      Node target = n.getFirstChild().getFirstChild();
      return (target.matchesQualifiedName("goog.module"));
    }
    return false;
  }

  /**
   * Rewrite:
   *   goog.module('foo')
   *   var bar = goog.require('bar');
   *   exports = something;
   * to:
   *   goog.provide('foo');
   *   goog.require('ns.bar');
   *   goog.scope(function() {
   *     var bar = ns.bar;
   *     foo = something;
   *   });
   */
  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (!inModule()) {
      // Nothing to do if we aren't within a module file.
      return;
    }

    switch (n.getType()) {
      case Token.EXPR_RESULT:
        // Handle "goog.module.declareLegacyNamespace".  Currently, we simply
        // need to remove it.
        if (isCallTo(n.getFirstChild(),
            "goog.module.declareLegacyNamespace")) {
          n.detachFromParent();
        }
        break;

      case Token.CALL:
        if (isCallTo(n, "goog.module")) {
          recordAndUpdateModule(t, n);
        } else if (isCallTo(n, "goog.require")) {
          recordRequire(t, n);
        } else if (isLoadModuleCall(n)) {
          rewriteModuleAsScope(n);
        }
        break;

      case Token.GETPROP:
        if (isExportPropAssign(n)) {
          Node rhs = parent.getLastChild();
          maybeUpdateExportDeclToNode(t, parent, rhs);
        }
        break;

      case Token.NAME:
        if (n.getString().equals("exports")) {
          current.exports.add(n);
          if (isAssignTarget(n)) {
            maybeUpdateExportObjectDecl(t, n);
          }
        }
        break;

      case Token.SCRIPT:
        // Exiting the script, fixup everything else;
        rewriteModuleAsScope(n);
        break;

      case Token.RETURN:
        // Remove the "return exports" for bundled goog.module files.
        if (t.getScopeRoot() == current.moduleScopeRoot) {
          n.detachFromParent();
        }
        break;
    }
  }

  /**
   * For exports like "exports = {prop: value}" update the declarations to enforce
   * @const ness (and typedef exports).
   */
  private void maybeUpdateExportObjectDecl(NodeTraversal t, Node n) {
    Node parent = n.getParent();
    Node rhs = parent.getLastChild();
    // The export declaration itself
    maybeUpdateExportDeclToNode(t, parent, rhs);

    if (rhs.isObjectLit()) {
      for (Node c = rhs.getFirstChild(); c != null; c = c.getNext()) {
        if (c.isStringKey()) {
          Node value = c.getFirstChild();
          maybeUpdateExportDeclToNode(t, c, value);
        }
      }
    }
  }

  private void maybeUpdateExportDeclToNode(
      NodeTraversal t, Node target, Node value) {
    // If the RHS is a typedef, clone the declaration.
    // Hack alert: clone the typedef declaration if one exists
    // this is a simple attempt that covers the common case of the
    // exports being in the same scope as the typedef declaration.
    // Otherwise the type name might be invalid.
    if (value.isName()) {
      Scope currentScope = t.getScope();
      Var v = t.getScope().getVar(value.getString());
      if (v != null) {
        Scope varScope = v.getScope();
        if (varScope.getDepth() == currentScope.getDepth()) {
          JSDocInfo info = v.getJSDocInfo();
          if (info != null && info.hasTypedefType()) {
            JSDocInfoBuilder builder = JSDocInfoBuilder.copyFrom(info);
            target.setJSDocInfo(builder.build(target));
            return;
          }
        }
      }
    }

    // Not a known typedef export, simple declare the props to be @const,
    // this is valid because we freeze module export objects.
    JSDocInfo info = target.getJSDocInfo();
    if (info == null) {
      JSDocInfoBuilder builder = new JSDocInfoBuilder(false);
      builder.recordConstancy();
      target.setJSDocInfo(builder.build(target));
    } else if (!info.isConstant() && !info.hasTypedefType()) {
      JSDocInfoBuilder builder = JSDocInfoBuilder.copyFrom(info);
      builder.recordConstancy();
      target.setJSDocInfo(builder.build(target));
    }
  }


  /**
   * @return Whether the getprop is used as an assignment target, and that
   *     target represents a module export.
   * Note: that "export.name = value" is an export, while "export.name.foo = value"
   *     is not (it is an assignment to a property of an exported value).
   */
  private static boolean isExportPropAssign(Node n) {
    Preconditions.checkState(n.isGetProp());
    Node target = n.getFirstChild();
    return isAssignTarget(n) && target.isName()
        && target.getString().equals("exports");
  }

  private static boolean isAssignTarget(Node n) {
    Node parent = n.getParent();
    return parent.isAssign() && parent.getFirstChild() == n;
  }

  private void recordAndUpdateModule(NodeTraversal t, Node call) {
    Node idNode = call.getLastChild();
    if (!idNode.isString()) {
      t.report(idNode, INVALID_MODULE_IDENTIFIER);
      return;
    }

    current.moduleNamespace = idNode.getString();
    current.moduleDecl = call;

    // rewrite "goog.module('foo')" to "goog.provide('foo')"
    Node target = call.getFirstChild();
    target.getLastChild().setString("provide");

    current.provides.add(call);
  }

  private void recordRequire(NodeTraversal t, Node call) {
    Node idNode = call.getLastChild();
    if (!idNode.isString()) {
      t.report(idNode, INVALID_REQUIRE_IDENTIFIER);
      return;
    }
    current.requires.add(call);
  }

  private void updateRequires(List<Node> requires) {
    for (Node node : requires) {
      updateRequire(node);
    }
  }

  private void updateRequire(Node call) {
    Node idNode = call.getLastChild();
    String namespace = idNode.getString();
    if (current.requireInsertNode == null) {
      current.requireInsertNode = getInsertRoot(call);
    }

    // rewrite:
    //   var foo = goog.require('ns.foo')
    // to
    //   goog.require('foo');
    //   var foo = ns.foo;

    // replace the goog.require statementment with a reference to the
    // namespace.
    Node replacement = NodeUtil.newQName(compiler, namespace).srcrefTree(call);
    call.getParent().replaceChild(call, replacement);

    // readd the goog.require statement
    Node require = IR.exprResult(call).srcref(call);
    Node insertAt = current.requireInsertNode;
    insertAt.getParent().addChildBefore(require, insertAt);
  }

  private List<String> collectRoots(ModuleDescription module) {
    List<String> result = new ArrayList<>();
    for (Node n : module.provides) {
      result.add(getRootName(n.getFirstChild().getNext()));
    }
    for (Node n : module.requires) {
      result.add(getRootName(n.getFirstChild().getNext()));
    }
    return result;
  }

  private String getRootName(Node n) {
    String qname = n.getString();
    int endPos = qname.indexOf('.');
    return (endPos == -1) ? qname : qname.substring(0, endPos);
  }

  private void rewriteModuleAsScope(Node root) {
    // Moving everything following the goog.module/goog.requires into a
    // goog.scope so that the aliases can be resolved.

    Node moduleRoot = current.moduleStatementRoot;

    // The moduleDecl will be null if it is invalid.
    Node srcref = current.moduleDecl != null ? current.moduleDecl : root;

    ImmutableSet<String> roots = ImmutableSet.copyOf(collectRoots(current));
    updateRootShadows(current.moduleScope, roots);
    updateRequires(current.requires);
    updateExports(current.exports);


    Node block = IR.block();
    Node scope = IR.exprResult(IR.call(
        IR.getprop(IR.name("goog"), IR.string("scope")),
        IR.function(IR.name(""), IR.paramList(), block)))
        .srcrefTree(srcref);

    // Skip goog.module, etc.
    Node fromNode = skipHeaderNodes(moduleRoot);
    Preconditions.checkNotNull(fromNode);
    moveChildrenAfter(fromNode, block);
    moduleRoot.addChildAfter(scope, fromNode);

    if (root.isCall()) {
      Node expr = root.getParent();
      Preconditions.checkState(expr.isExprResult(), expr);
      expr.getParent().addChildrenAfter(moduleRoot.removeChildren(), expr);
      expr.detachFromParent();
    }
    compiler.reportCodeChange();

    // reset the module.
    current = null;
  }

  private void updateExports(List<Node> exports) {
    for (Node n : exports) {
      Node replacement = NodeUtil.newQName(compiler, current.moduleNamespace);
      replacement.srcrefTree(n);
      n.getParent().replaceChild(n, replacement);
    }
  }

  private void updateRootShadows(Scope s, ImmutableSet<String> roots) {
    final Map<String, String> nameMap = new HashMap<>();
    for (String root : roots) {
      if (s.getOwnSlot(root) != null) {
        nameMap.put(root, root + "_module");
      }
    }

    if (nameMap.isEmpty()) {
      // Don't traverse if there is nothing to do.
      return;
    }

    new NodeTraversal(compiler, new AbstractPostOrderCallback() {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        if (n.isName()) {
          String rename = nameMap.get(n.getString());
          if (rename != null) {
            n.setString(rename);
          }
        }
      }
    }).traverseAtScope(s);
  }

  private static Node getModuleScopeRootForLoadModuleCall(Node n) {
    Preconditions.checkState(n.isCall());
    Node fn = n.getLastChild();
    Preconditions.checkState(fn.isFunction());
    return fn;
  }

  private static Node getModuleStatementRootForLoadModuleCall(Node n) {
    Node fn = getModuleScopeRootForLoadModuleCall(n);
    Node block = fn.getLastChild();
    Preconditions.checkState(block.isBlock());
    return block;
  }

  private Node skipHeaderNodes(Node script) {
    Node lastHeaderNode = null;
    Node child = script.getFirstChild();
    while (child != null && isHeaderNode(child)) {
      lastHeaderNode = child;
      child = child.getNext();
    }
    return lastHeaderNode;
  }

  private boolean isHeaderNode(Node n) {
    if (n.isEmpty()) {
      return true;
    }
    if (NodeUtil.isExprCall(n)) {
      Node target = n.getFirstChild().getFirstChild();
      return (
          target.matchesQualifiedName("goog.module")
          || target.matchesQualifiedName("goog.provide")
          || target.matchesQualifiedName("goog.require")
          || target.matchesQualifiedName("goog.setTestOnly"));
    }
    return false;
  }

  private void moveChildrenAfter(Node fromNode, Node targetBlock) {
    Node parent = fromNode.getParent();
    while (fromNode.getNext() != null) {
      Node child = parent.removeChildAfter(fromNode);
      targetBlock.addChildToBack(child);
    }
  }

  private Node getInsertRoot(Node n) {
    while (n.getParent() != current.moduleStatementRoot) {
      n = n.getParent();
    }
    return n;
  }
}
