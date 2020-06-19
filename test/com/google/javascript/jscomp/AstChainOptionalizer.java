/*
 * Copyright 2020 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Converts normal GETPROP, GETELEM, and CALL, nodes into the corresponding OPTCHAIN_ version.
 *
 * <p>This is a utility to be used temporarily while implementing support for optional chaining. No
 * code that actually uses it should be checked in, and it should be removed once that support is
 * complete.
 */
public class AstChainOptionalizer {

  /** How long should each created optional chain be? */
  enum ChainStartStrategy {
    // Make every link in a chain start a new optional chain.
    // x[prop].y() -> x?.[prop]?.y?.()
    EVERY_NODE,
    // Only the first link in a chain starts a new optional chain.
    // x[prop].y() -> x?.[prop].y()
    FIRST_NODE_ONLY,
  }

  /** Builds a new AstOptionalizer. */
  static class Builder {

    private ChainStartStrategy chainStartStrategy = ChainStartStrategy.FIRST_NODE_ONLY;

    public Builder chainLength(ChainStartStrategy chainStartStrategy) {
      this.chainStartStrategy = chainStartStrategy;
      return this;
    }

    public AstChainOptionalizer build() {
      return new AstChainOptionalizer(this);
    }
  }

  static Builder builder() {
    return new Builder();
  }

  private final ChainStartStrategy chainStartStrategy;

  private AstChainOptionalizer(Builder builder) {
    this.chainStartStrategy = builder.chainStartStrategy;
  }

  void optionalize(AbstractCompiler compiler, Node root) {
    NodeTraversal.traverse(compiler, root, new OptionalizeCallback());
  }

  /** Turns non-optional chains into optional chains. */
  private final class OptionalizeCallback implements NodeTraversal.Callback {

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if (n.isScript()) {
        NodeUtil.addFeatureToScript(n, Feature.OPTIONAL_CHAINING, t.getCompiler());
        t.reportCodeChange();
      }
      if (n.isExprResult() && n.getOnlyChild().isQualifiedName()) {
        // Don't mess with cases that are probably type declarations.
        // e.g.
        // ```
        // /** @typedef {Array|{length:number}} */
        // goog.array.ArrayLike;
        // ```
        return false;
      }
      if (n.isCall() && isSpecialCallee(n.getFirstChild())) {
        // Don't modify the callee of special functions such as
        // goog.reflect.cache()
        // Object.defineProperties()
        return false;
      }
      // Optional chains are not allowed as assignment targets
      return !NodeUtil.isLValue(n);
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      final Node firstChild = n.getFirstChild();
      if (firstChild == null || firstChild.isSuper()) {
        // a "chain" node always has at least one child
        // `super?.` is not allowed.
        return;
      }
      final Token optChainToken;
      switch (n.getToken()) {
        case CALL:
          optChainToken = Token.OPTCHAIN_CALL;
          break;

        case GETELEM:
          optChainToken = Token.OPTCHAIN_GETELEM;
          break;

        case GETPROP:
          optChainToken = Token.OPTCHAIN_GETPROP;
          break;

        default:
          optChainToken = null;
      }
      if (optChainToken != null) {
        // Current node type is one that has an OPTCHAIN_ version.
        n.setToken(optChainToken);
        n.setIsOptionalChainStart(
            chainStartStrategy == ChainStartStrategy.EVERY_NODE
                || !NodeUtil.isOptChainNode(firstChild));
        t.reportCodeChange();
      }
    }
  }

  /** Special function qualified names that really should never be called with ?. */
  private static final ImmutableList<String> SPECIAL_CALLEE_QNAMES =
      ImmutableList.of(
          "Object.defineProperty",
          "Object.defineProperties",
          "goog.inherits",
          "goog.reflect.cache");

  /**
   * Is the callee a special function name that we would never expect to be called using optional
   * chaining. e.g. `Object.defineProperty()`
   */
  private boolean isSpecialCallee(Node callee) {
    if (callee.isQualifiedName()) {
      String calleeQName = checkNotNull(callee.getOriginalQualifiedName(), callee);
      // Check for end match instead of exact match to handle cases like
      // $jscomp.global.Object.defineProperties
      for (String specialName : SPECIAL_CALLEE_QNAMES) {
        if (calleeQName.endsWith(specialName)) {
          return true;
        }
      }
    }
    return false;
  }

  void deoptionalize(AbstractCompiler compiler, Node root) {
    NodeTraversal.traverse(compiler, root, new DeoptionalizeCallback());
  }

  /** Replaces all optional chains with non-optional ones. */
  private static class DeoptionalizeCallback extends NodeTraversal.AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getToken()) {
        case OPTCHAIN_CALL:
          n.setIsOptionalChainStart(false);
          n.setToken(Token.CALL);
          t.reportCodeChange();
          break;
        case OPTCHAIN_GETELEM:
          n.setIsOptionalChainStart(false);
          n.setToken(Token.GETELEM);
          t.reportCodeChange();
          break;
        case OPTCHAIN_GETPROP:
          n.setIsOptionalChainStart(false);
          n.setToken(Token.GETPROP);
          t.reportCodeChange();
          break;
        default:
          break;
      }
    }
  }
}
