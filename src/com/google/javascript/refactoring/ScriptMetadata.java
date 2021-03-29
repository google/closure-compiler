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

package com.google.javascript.refactoring;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Splitter;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import javax.annotation.Nullable;

/**
 * A summary of a script for use during fixes.
 *
 * <p>Instances are mutable so that changes to a script caused by one fix cen be reflected during
 * subseuqent fixes.
 */
public final class ScriptMetadata {

  private final Node script;
  private boolean supportsRequireAliases;
  private final LinkedHashMap<String, String> requireAliases = new LinkedHashMap<>();
  private final LinkedHashSet<String> namesInUse = new LinkedHashSet<>();

  static ScriptMetadata create(Node script, NodeMetadata metadata) {
    return create(script, metadata.getCompiler());
  }

  public static ScriptMetadata create(Node script, AbstractCompiler compiler) {
    checkArgument(script.isScript());

    ScriptMetadata toFill = new ScriptMetadata(script);
    NodeTraversal.traverse(compiler, script, new FillerCallback(toFill));

    // Even if the file was not found to be a module, the existence of other aliases means aliases
    // are supported.
    if (!toFill.requireAliases.isEmpty()) {
      toFill.supportsRequireAliases = true;
    }
    return toFill;
  }

  private ScriptMetadata(Node script) {
    this.script = script;
  }

  Node getScript() {
    return this.script;
  }

  /** When requires are added to this script, should they be given local aliases? */
  boolean supportsRequireAliases() {
    return this.supportsRequireAliases;
  }

  /**
   * Is `name` in use within this script?
   *
   * <p>This includes both executable and JsDoc references.
   */
  boolean usesName(String name) {
    return this.namesInUse.contains(name);
  }

  /**
   * Returns the local alias for `namespace`.
   *
   * <p>If `namespace` is not required or has no alias, returns `null`. In particular, destructured
   * aliases will not be available.
   */
  @Nullable
  String getAlias(String namespace) {
    return this.requireAliases.get(namespace);
  }

  /**
   * Record that `namespace` has ben required with local alias `alias`.
   *
   * <p>As a corallary, any alias is also a name in use within this script.
   */
  void addAlias(String namespace, String alias) {
    checkState(this.supportsRequireAliases);
    this.namesInUse.add(alias);
    this.requireAliases.put(namespace, alias);
  }

  private static class FillerCallback extends AbstractPostOrderCallback {

    private final ScriptMetadata toFill;

    public FillerCallback(ScriptMetadata toFill) {
      this.toFill = toFill;
    }

    @Override
    public void visit(NodeTraversal nodeTraversal, Node n, Node parent) {
      this.updateSupportsRequireAlias(n);
      this.maybeCollectRequirelikeAlias(n);
      this.maybeCollectNames(n);
    }

    private void updateSupportsRequireAlias(Node n) {
      switch (n.getToken()) {
        case MODULE_BODY:
          break;

        case CALL:
          if (n.getChildCount() != 2
              || !n.getFirstChild().matchesQualifiedName("goog.module")
              || !n.getSecondChild().isStringLit()) {
            return;
          }
          break;

        default:
          return;
      }

      this.toFill.supportsRequireAliases = true;
    }

    private void maybeCollectRequirelikeAlias(Node n) {
      if (!NodeUtil.isNameDeclaration(n)) {
        return;
      }

      Node aliasNode = n.getFirstChild();

      // TODO(b/139953612): respect destructured goog.requires
      if (aliasNode.isDestructuringLhs()) {
        return;
      }

      Node requirelikeCall = aliasNode.getFirstChild();
      if (!this.isRequirelikeCall(requirelikeCall)) {
        return;
      }

      String alias = aliasNode.getOriginalName();
      if (alias == null) {
        alias = aliasNode.getString();
      }

      String namespace = requirelikeCall.getSecondChild().getString();
      this.toFill.requireAliases.put(namespace, alias);
    }

    private boolean isRequirelikeCall(Node call) {
      if (call == null
          || !call.isCall()
          || call.getChildCount() != 2
          || !call.getSecondChild().isStringLit()) {
        return false;
      }

      Node callee = call.getFirstChild();
      return callee.matchesQualifiedName("goog.require")
          || callee.matchesQualifiedName("goog.requireType")
          || callee.matchesQualifiedName("goog.forwardDeclare");
    }

    private void maybeCollectNames(Node n) {
      if (n.isName()) {
        this.toFill.namesInUse.add(n.getString());
      }

      JSDocInfo jsdoc = n.getJSDocInfo();
      if (jsdoc != null) {
        for (JSTypeExpression expr : jsdoc.getTypeExpressions()) {
          for (String type : expr.getAllTypeNames()) {
            this.toFill.namesInUse.add(DOT_SPLITTER.split(type).iterator().next());
          }
        }
      }
    }
  }

  private static final Splitter DOT_SPLITTER = Splitter.on('.');
}
