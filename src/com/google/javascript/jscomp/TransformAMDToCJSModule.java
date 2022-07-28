/*
 * Copyright 2011 The Closure Compiler Authors.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.function.BiConsumer;
import org.jspecify.nullness.Nullable;

/**
 * Rewrites an AMD module https://github.com/amdjs/amdjs-api/wiki/AMD to a
 * CommonJS module. See {@link ProcessCommonJSModules} for follow up processing
 * step.
 */
public final class TransformAMDToCJSModule implements CompilerPass {

  @VisibleForTesting
  static final DiagnosticType UNSUPPORTED_DEFINE_SIGNATURE_ERROR =
      DiagnosticType.error(
          "UNSUPPORTED_DEFINE_SIGNATURE",
          "Only define(function() ...), define(OBJECT_LITERAL) and define("
              + "['dep', 'dep1'], function(d0, d2, [exports, module]) ...) forms "
              + "are currently supported.");
  static final DiagnosticType NON_TOP_LEVEL_STATEMENT_DEFINE_ERROR =
      DiagnosticType.error(
            "NON_TOP_LEVEL_STATEMENT_DEFINE",
            "The define function must be called as a top-level statement.");
  static final DiagnosticType REQUIREJS_PLUGINS_NOT_SUPPORTED_WARNING =
    DiagnosticType.warning(
          "REQUIREJS_PLUGINS_NOT_SUPPORTED",
          "Plugins in define requirements are not supported: {0}");

  static final String VAR_RENAME_SUFFIX = "__alias";


  private final AbstractCompiler compiler;
  private int renameIndex = 0;

  public TransformAMDToCJSModule(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new TransformAMDModulesCallback());
  }

  private static void unsupportedDefineError(NodeTraversal t, Node n) {
    t.report(n, UNSUPPORTED_DEFINE_SIGNATURE_ERROR);
  }

  /**
   * The modules "exports", "require" and "module" are virtual in terms of
   * existing implicitly in CommonJS.
   */
  private static boolean isVirtualModuleName(String moduleName) {
    return "exports".equals(moduleName) || "require".equals(moduleName) ||
        "module".equals(moduleName);
  }

  /**
   * Rewrites calls to define which has to be in void context just below the
   * current script node.
   */
  private class TransformAMDModulesCallback extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isCall()
          && n.hasChildren()
          && n.getFirstChild().isName()
          && "define".equals(n.getFirstChild().getString())) {
        Var define = t.getScope().getVar(n.getFirstChild().
            getString());
        if (define != null && !define.isGlobal()) {
          // Ignore non-global define.
          return;
        }
        if (!(parent.isExprResult() && parent.getParent().isScript())) {
          t.report(n, NON_TOP_LEVEL_STATEMENT_DEFINE_ERROR);
          return;
        }
        Node script = parent.getParent();
        Node requiresNode = null;
        final Node callback;
        int defineArity = n.getChildCount() - 1;
        if (defineArity == 0) {
          unsupportedDefineError(t, n);
          return;
        } else if (defineArity == 1) {
          callback = n.getSecondChild();
          if (callback.isObjectLit()) {
            handleDefineObjectLiteral(t, parent, callback);
            return;
          }
        } else if (defineArity == 2) {
          requiresNode = n.getSecondChild();
          callback = n.getChildAtIndex(2);
        } else if (defineArity >= 3) {
          unsupportedDefineError(t, n);
          return;
        } else {
          callback = null;
        }

        if (!callback.isFunction() ||
            (requiresNode != null && !requiresNode.isArrayLit())) {
          unsupportedDefineError(t, n);
          return;
        }

        zipChildren(
            callback.getSecondChild(),
            requiresNode,
            (param, moduleName) -> handleRequire(t, n, script, callback, param, moduleName));

        Node callbackBlock = callback.getChildAtIndex(2);
        NodeTraversal.traverse(compiler, callbackBlock,
            new DefineCallbackReturnCallback());

        moveCallbackContentToTopLevel(parent, script, callbackBlock);
        t.reportCodeChange();
      }
    }

    /** When define is called with an object literal, assign it to module.exports and we're done. */
    private void handleDefineObjectLiteral(NodeTraversal t, Node parent, Node onlyExport) {
      onlyExport.detach();
      parent.replaceWith(
          IR.exprResult(IR.assign(NodeUtil.newQName(compiler, "module.exports"), onlyExport))
              .srcrefTreeIfMissing(onlyExport));
      t.reportCodeChange();
    }

    /**
     * Rewrite a single require call.
     */
    private void handleRequire(NodeTraversal t, Node defineNode, Node script,
        Node callback, Node aliasNode, Node modNode) {
      String moduleName = null;
      if (modNode != null) {
        moduleName = handlePlugins(t, script, modNode.getString(), modNode);
      }

      if (isVirtualModuleName(moduleName)) {
        return;
      }

      String aliasName = aliasNode != null ? aliasNode.getString() : null;
      Scope globalScope = t.getScope();
      if (aliasName != null && globalScope.hasSlot(aliasName)) {
        while (true) {
          String renamed = aliasName + VAR_RENAME_SUFFIX + renameIndex;
          if (!globalScope.hasSlot(renamed)) {
            NodeTraversal.traverse(compiler, callback,
                new RenameCallback(aliasName, renamed));
            aliasName = renamed;
            break;
          }
          renameIndex++;
        }
      }

      Node requireNode;
      if (moduleName != null) {
        Node call = IR.call(IR.name("require"), IR.string(moduleName));
        call.putBooleanProp(Node.FREE_CALL, true);
        if (aliasName != null) {
          requireNode = IR.var(IR.name(aliasName), call).srcrefTreeIfMissing(aliasNode);
        } else {
          requireNode = IR.exprResult(call).srcrefTreeIfMissing(modNode);
        }
      } else {
        // ignore exports, require and module (because they are implicit
        // in CommonJS);
        if (isVirtualModuleName(aliasName)) {
          return;
        }
        requireNode = IR.var(IR.name(aliasName), IR.nullNode()).srcrefTreeIfMissing(aliasNode);
      }

      requireNode.insertBefore(defineNode.getParent());
    }

    /**
     * Require.js supports a range of plugins that are hard to support statically. Generally none
     * are supported right now with the exception of a simple hack to support condition loading.
     * This was added to make compilation of Dojo work better but will probably break, so just don't
     * use them :)
     */
    private @Nullable String handlePlugins(
        NodeTraversal t, Node script, String moduleName, Node modNode) {
      if (moduleName.contains("!")) {
        t.report(modNode, REQUIREJS_PLUGINS_NOT_SUPPORTED_WARNING, moduleName);
        int condition = moduleName.indexOf('?');
        if (condition > 0) {
          if (moduleName.contains(":")) {
            return null;
          }
          return handlePlugins(t, script, moduleName.substring(condition + 1),
              modNode);
        }
        moduleName = null;
      }
      return moduleName;
    }

    /**
     * Moves the statements in the callback to be direct children of the
     * current script.
     */
    private void moveCallbackContentToTopLevel(Node defineParent, Node script,
        Node callbackBlock) {
      int curIndex = script.getIndexOfChild(defineParent);
      defineParent.detach();
      NodeUtil.markFunctionsDeleted(defineParent, compiler);
      callbackBlock.detach();
      Node before = script.getChildAtIndex(curIndex);
      if (before != null) {
        callbackBlock.insertBefore(before);
      }
      script.addChildToBack(callbackBlock);
      NodeUtil.tryMergeBlock(callbackBlock, false);
    }
  }

  /**
   * Rewrites the return statement of the callback to be an assignment to
   * module.exports.
   */
  private static class DefineCallbackReturnCallback extends
      NodeTraversal.AbstractShallowStatementCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isReturn() && n.hasChildren()) {
        Node retVal = n.getFirstChild();
        retVal.detach();
        n.replaceWith(
            IR.exprResult(IR.assign(IR.getprop(IR.name("module"), "exports"), retVal))
                .srcrefTree(n));
      }
    }
  }

  /**
   * Renames names;
   */
  private static class RenameCallback extends AbstractPostOrderCallback {

    private final String from;
    private final String to;

    public RenameCallback(String from, String to) {
      this.from = from;
      this.to = to;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName() && from.equals(n.getString())) {
        n.setString(to);
        n.setOriginalName(from);
      }
    }
  }

  private static void zipChildren(Node leftParent, Node rightParent, BiConsumer<Node, Node> zip) {
    Node left = (leftParent == null) ? null : leftParent.getFirstChild();
    Node right = (rightParent == null) ? null : rightParent.getFirstChild();

    while (left != null || right != null) {
      zip.accept(left, right);
      if (left != null) {
        left = left.getNext();
      }
      if (right != null) {
        right = right.getNext();
      }
    }
  }
}
