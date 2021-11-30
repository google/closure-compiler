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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import javax.annotation.Nullable;

/**
 * Rewrites a single optional chain as one or more nested hook expressions.
 *
 * <p>Optional chains contained in OPTCHAIN_GETELEM indices or OPTCHAIN_CALL arguments are not
 * rewritten.
 *
 * <p>Example:
 *
 * <pre><code>
 *   a?.b[obj?.index]?.c?.(obj?.arg)
 *   // becomes
 *   (tmp0 = a) == null
 *       ? void 0
 *       : (tmp1 = tmp0.b[obj?.index]) == null
 *           ? void 0
 *           : (tmp2 = tmp1.c) == null
 *               ? void 0
 *               : tmp2.call(tmp1, obj?.arg);
 * </code></pre>
 *
 * <p>The unit tests for this class are in RewriteOptionalChainingOperatorTest, because it's most
 * convenient to test this class as part of the transpilation pass that uses it.
 */
class OptionalChainRewriter {
  final AbstractCompiler compiler;
  final AstFactory astFactory;
  final TmpVarNameCreator tmpVarNameCreator;
  // If a scope is provided, newly created variables will be declared in that scope
  @Nullable final Scope scope;
  final Node chainParent;
  final Node wholeChain;
  final Node enclosingStatement;
  final ArrayDeque<Node> deletesToDelete;

  /** Creates unique names to be used for temporary variables. */
  interface TmpVarNameCreator {

    /** Creates a unique temporary variable name each time it is called. */
    String createTmpVarName();
  }

  static class Builder {
    final AbstractCompiler compiler;
    final AstFactory astFactory;
    TmpVarNameCreator tmpVarNameCreator;
    Scope scope;

    private Builder(AbstractCompiler compiler) {
      this.compiler = checkNotNull(compiler);
      this.astFactory = compiler.createAstFactory();
    }

    Builder setTmpVarNameCreator(TmpVarNameCreator tmpVarNameCreator) {
      this.tmpVarNameCreator = checkNotNull(tmpVarNameCreator);
      return this;
    }

    /** Optionally sets a scope in which the rewriter will declare all temporary variables */
    Builder setScope(Scope scope) {
      this.scope = checkNotNull(scope);
      return this;
    }

    /** @param wholeChain The last Node in the optional chain. Parent of all the rest. */
    OptionalChainRewriter build(Node wholeChain) {
      return new OptionalChainRewriter(this, wholeChain);
    }
  }

  static Builder builder(AbstractCompiler compiler) {
    return new Builder(compiler);
  }

  private OptionalChainRewriter(Builder builder, Node wholeChain) {
    // This class will only operate on an entire chain.
    checkArgument(NodeUtil.isEndOfFullOptChain(wholeChain), wholeChain);
    this.compiler = builder.compiler;
    this.astFactory = builder.astFactory;
    this.tmpVarNameCreator = checkNotNull(builder.tmpVarNameCreator);
    this.scope = builder.scope;
    this.wholeChain = wholeChain;
    this.chainParent = checkNotNull(wholeChain.getParent(), wholeChain);
    this.enclosingStatement = NodeUtil.getEnclosingStatement(wholeChain);
    this.deletesToDelete = new ArrayDeque<>();
  }

  /** Rewrites the optional chain as a hook with temporary variables introduced as needed. */
  void rewrite() {
    checkState(NodeUtil.isOptChainNode(wholeChain), "already rewritten: %s", wholeChain);

    // `first?.start.second?.start`
    // We search from the end of the chain and push the start nodes onto the stack, so the first
    // one ends up on top.
    ArrayDeque<Node> startNodeStack = new ArrayDeque<>();
    Node subchainEnd = wholeChain;
    while (NodeUtil.isOptChainNode(subchainEnd)) {
      final Node subchainStart = NodeUtil.getStartOfOptChainSegment(subchainEnd);
      startNodeStack.push(subchainStart);
      subchainEnd = subchainStart.getFirstChild();
    }

    checkState(!startNodeStack.isEmpty());
    // Each time we rewrite the initial segment of the chain, the remaining chain gets wrapped
    // in a hook statement like `(tmp0 = a.b) == null ? void 0 : tmp0.rest?.of.chain?.()`,
    // So wholeChain ends up more deeply nested on each rewrite.
    // We only care about the top-most replacement here.
    final Node optChainReplacement = rewriteInitialSegment(startNodeStack.pop(), wholeChain);
    while (!startNodeStack.isEmpty()) {
      rewriteInitialSegment(startNodeStack.pop(), wholeChain);
    }

    // Handle a non-optional call to an optional chain that ends in an element or property
    // access.
    // `(a?.optional.chain)(arg1)`
    // Writing JavaScript code like this is a bad idea, but it might get automatically
    // generated, so we must handle it.
    // The optional chain could evaluate to `undefined`, which we then try to call as a
    // function. However, if it isn't undefined, we have to preserve the correct `this` value
    // for the call.
    if (chainParent.isCall()
        // The chain will have been replaced by optChainReplacement during the rewriting above.
        && optChainReplacement.isFirstChildOf(chainParent)
        // The wholeChain variable will still point to the rewritten final Node of the
        // chain. It will no longer be optional.
        && NodeUtil.isNormalGet(wholeChain)) {
      final Node thisValue = wholeChain.getFirstChild();
      final Node tmpThisNode = getSubExprNameNode(thisValue);
      optChainReplacement.detach();
      chainParent.addChildToFront(tmpThisNode);
      final Node dotCallNode =
          astFactory
              .createGetPropWithUnknownType(optChainReplacement, "call")
              .srcrefTreeIfMissing(optChainReplacement);
      chainParent.addChildToFront(dotCallNode);
    }

    // Report changes here; chainParent can get deleted below this.
    // E.g. code `(p = a?.b)=>{ return p}` changes to `let tmp0; (p = (tmp0=a)==null?void
    // 0:tmp0.b)=>{return p}`
    // This requires recording scope changes at two places:
    // 1. the function scope changed from rewriting to HOOK (recorded here),
    // 2. SCRIPT scope changed due to the `let tmp0` inserted (recorded in `declareTempVarName`
    // where declarations are created).
    compiler.reportChangeToEnclosingScope(chainParent);

    if (chainParent.isDelProp()) {
      // With rewriting above,
      // `delete a?.b?.c`
      //  synthesizes additional deletes
      // `delete (tmp0 = a) == null ? true : delete (tmp1 = tmp0.b) == null ? true : delete tmp1.c;`
      //  ^^^^^^                             ^^^^^^
      //
      // But we must generate only:
      // `(tmp0 = a) == null ? true : (tmp1 = tmp0.b) == null ? true : delete tmp1.c;`
      // That is, the preceding deletes for every hook must be removed.

      while (!deletesToDelete.isEmpty()) {
        Node delete = deletesToDelete.removeFirst();
        checkState(delete.getFirstChild().isHook(), delete);
        Node hook = delete.getFirstChild();
        hook.detach();
        delete.replaceWith(hook);
        compiler.reportChangeToEnclosingScope(hook);
      }
    }

    // Transpilation of the optional chain adds `let` declarations for temporary variables.
    // NOTE: If this class is being used before transpilation, it's OK to use `let`, since it will
    // be transpiled away, if necessary. If it is being used after transpilation, then using `let`
    // must be OK, because optional chains weren't transpiled away and `let` existed before they
    // did.
    final Node enclosingScript = NodeUtil.getEnclosingScript(enclosingStatement);
    NodeUtil.addFeatureToScript(enclosingScript, Feature.LET_DECLARATIONS, compiler);
  }

  /**
   * Rewrites the first part of a possibly-multi-part optional chain.
   *
   * <p>e.g.
   *
   * <pre>{@code
   * a()?.b.c?.d;
   * // becomes
   * let tmp0;
   * (tmp0 = a()) == null
   *     ? void 0
   *     : tmp0.b.c?d;
   * }</pre>
   *
   * If this optional chain is under a delete, the l-r rewriting must synthesize another `delete` to
   * ensure the next chain(if present), knows that it must delete the `fullChainEnd`.
   *
   * <p>e.g. * *
   *
   * <pre>{@code
   * * delete a?.b.c?.d;
   * * // becomes
   * * let tmp0;
   * * (tmp0 = a()) == null
   * *     ? true
   * *     : delete tmp0.b.c?d;
   * *
   * }</pre>
   *
   * @param fullChainStart The very first `?.` node
   * @param fullChainEnd The very last optional chain node.
   * @return The hook expression that replaced the chain.
   */
  private Node rewriteInitialSegment(final Node fullChainStart, final Node fullChainEnd) {
    // `receiverNode?.restOfChain`
    Node receiverNode = fullChainStart.getFirstChild();
    // for `a?.b.c?.d`, this will be `a?.b.c`, because the NodeUtil method finds the end
    // of the sub-chain, not the full chain.
    final Node initialChainEnd = NodeUtil.getEndOfOptChainSegment(fullChainStart);

    // Is this optional chain under delete
    boolean isBeingDeleted = fullChainEnd.getParent().isDelProp();
    if (isBeingDeleted) {
      deletesToDelete.addLast(fullChainEnd.getParent());
    }

    // If the receiver is an optional chain, we weren't really given the start of a full
    // chain.
    checkArgument(!NodeUtil.isOptChainNode(receiverNode), receiverNode);

    // change the initial chain's nodes to be non-optional
    NodeUtil.convertToNonOptionalChainSegment(initialChainEnd);

    final Node placeholder = IR.empty();
    fullChainEnd.replaceWith(placeholder);
    // NOTE: convertToNonOptionalChain() above will have made the chain start
    // and all the other nodes in the first segment of the chain non-optional,
    // so fullChainStart.isCall() is the right test here.
    if (NodeUtil.isNormalGet(receiverNode) && fullChainStart.isCall()) {
      // `expr.prop?.(x).y`
      // Needs to become
      // `(t1 = (t0 = expr).prop) == null ? void 0 : t1.call(t0, x).y`
      final Node thisValue = receiverNode.getFirstChild();
      final Node tmpThisNode = getSubExprNameNode(thisValue);
      final Node tmpReceiverNode = getSubExprNameNode(receiverNode);
      receiverNode = fullChainStart.removeFirstChild();
      fullChainStart.addChildToFront(tmpThisNode);
      fullChainStart.addChildToFront(
          astFactory
              .createGetPropWithUnknownType(tmpReceiverNode, "call")
              .srcrefTreeIfMissing(receiverNode));
    } else {
      // `expr?.x.y`
      // needs to become
      // `((t0 = expr) == null) ? void 0 : t0.x.y`
      final Node tmpReceiverNode = getSubExprNameNode(receiverNode);
      receiverNode = fullChainStart.getFirstChild();
      receiverNode.replaceWith(tmpReceiverNode);
    }
    final Node optChainReplacement =
        astFactory
            .createHook(
                astFactory.createEq(receiverNode, astFactory.createNull()),
                isBeingDeleted ? astFactory.createBoolean(true) : astFactory.createUndefinedValue(),
                isBeingDeleted ? astFactory.createDelProp(fullChainEnd) : fullChainEnd)
            .srcrefTreeIfMissing(fullChainEnd);

    placeholder.replaceWith(optChainReplacement);

    return optChainReplacement;
  }

  /**
   * Given an expression node, declare a temporary variable to hold that expression and replace the
   * expression with `(tmp = expr)`.
   *
   * <p>e.g. `subExpr.moreExpr` becomes `(tmp = subExpr).moreExpr`, and `let tmp;` gets inserted
   * before the enclosing statement of this optional chain.
   *
   * @param subExpr The sub expression Node
   * @return A detached NAME node for the temporary variable name and with source info and type
   *     matching `subExpr`, that may be inserted where needed.
   */
  Node getSubExprNameNode(Node subExpr) {
    String tempVarName = declareTempVarName(subExpr);
    Node placeholder = IR.empty();
    subExpr.replaceWith(placeholder);
    Node replacement = astFactory.createAssign(tempVarName, subExpr).srcrefTreeIfMissing(subExpr);
    placeholder.replaceWith(replacement);
    return replacement.getFirstChild().cloneNode();
  }

  /**
   * Declare a temporary variable name that will be used to hold the given value.
   *
   * <p>The generated declaration has no assignment, it's just `let tmp;`.
   *
   * @param valueNode A node from which to copy the source info and type to be used for the new
   *     variable.
   * @return the name used for the new temporary variable.
   */
  String declareTempVarName(Node valueNode) {
    String tempVarName = tmpVarNameCreator.createTmpVarName();
    Node declarationStatement =
        astFactory.createSingleLetNameDeclaration(tempVarName).srcrefTree(valueNode);
    declarationStatement.getFirstChild().setInferredConstantVar(true);
    declarationStatement.insertBefore(enclosingStatement);
    compiler.reportChangeToEnclosingScope(declarationStatement);
    if (scope != null) {
      scope.declare(tempVarName, declarationStatement.getFirstChild(), /* input= */ null);
    }
    return tempVarName;
  }
}
