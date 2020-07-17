/*
 * Copyright 2009 The Closure Compiler Authors.
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

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.jscomp.Es6ToEs3Util.withType;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.javascript.jscomp.MakeDeclaredNamesUnique.ContextualRenamer;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Partially or fully decomposes an expression with respect to some sub-expression. Initially this
 * is intended to expand the locations where inlining can occur, but has other uses as well.
 *
 * <p>For example: `var x = y() + z();` becomes `var a = y(); var b = z(); var x = a + b;`.
 *
 * <p>Decomposing, in this context does not mean full decomposition to "atomic" expressions. While
 * it is possible to iteratively apply decomposition to get statements with at most one side-effect,
 * that isn't the intended purpose of this class. The focus is on decomposing "just enough" to
 * "free" a <em>particular</em> subexpression. For example:
 *
 * <ul>
 *   <li>Given: `return (alert() + alert()) + z();`
 *   <li>Exposing: `z()`
 *   <li>Sufficient decomposition: `var temp = alert() + alert(); return temp + z();`
 * </ul>
 */
class ExpressionDecomposer {

  /** @see {@link #canExposeExpression} */
  enum DecompositionType {
    UNDECOMPOSABLE,
    MOVABLE,
    DECOMPOSABLE
  }

  private final AbstractCompiler compiler;
  private final AstAnalyzer astAnalyzer;
  private final AstFactory astFactory;
  private final Supplier<String> safeNameIdSupplier;
  private final Set<String> knownConstants;
  private final Scope scope;
  private final JSType unknownType;
  private final JSType stringType;

  /**
   * Whether to allow decomposing foo.bar to "var fn = foo.bar; fn.call(foo);" Should be false if
   * targeting IE8 or IE9.
   */
  private final boolean allowMethodCallDecomposing;

  ExpressionDecomposer(
      AbstractCompiler compiler,
      Supplier<String> safeNameIdSupplier,
      Set<String> constNames,
      Scope scope,
      boolean allowMethodCallDecomposing) {
    checkNotNull(compiler);
    checkNotNull(safeNameIdSupplier);
    checkNotNull(constNames);
    this.compiler = compiler;
    this.astAnalyzer = compiler.getAstAnalyzer();
    this.astFactory = compiler.createAstFactory();
    this.safeNameIdSupplier = safeNameIdSupplier;
    this.knownConstants = constNames;
    this.scope = scope;
    this.allowMethodCallDecomposing = allowMethodCallDecomposing;
    this.unknownType = compiler.getTypeRegistry().getNativeType(JSTypeNative.UNKNOWN_TYPE);
    this.stringType = compiler.getTypeRegistry().getNativeType(JSTypeNative.STRING_TYPE);
  }

  // An arbitrary limit to prevent catch infinite recursion.
  private static final int MAX_ITERATIONS = 100;

  /**
   * If required, rewrite the statement containing the expression.
   *
   * @param expression The expression to be exposed.
   * @see #canExposeExpression
   */
  void maybeExposeExpression(Node expression) {
    // If the expression needs to exposed.
    int i = 0;
    while (DecompositionType.DECOMPOSABLE == canExposeExpression(expression)) {
      exposeExpression(expression);
      i++;
      if (i > MAX_ITERATIONS) {
        throw new IllegalStateException(
            "DecomposeExpression depth exceeded on:\n" + expression.toStringTree());
      }
    }
  }

  /**
   * Perform any rewriting necessary so that the specified expression is {@code MOVABLE}.
   *
   * <p>This method is a primary entrypoint into this class. It performs a partial expression
   * decomposition such that {@code expression} can be moved to a preceding statement without
   * changing behaviour.
   *
   * <p>Exposing {@code expression} generally doesn't mean that {@code expression} itself will
   * moved. An expression is exposed within a larger statement if no preceding expression would
   * interact with it.
   *
   * @see {@link #canExposeExpression}
   */
  void exposeExpression(Node expression) {
    Node expressionRoot = findExpressionRoot(expression);
    checkNotNull(expressionRoot);
    checkState(NodeUtil.isStatement(expressionRoot), expressionRoot);
    exposeExpression(expressionRoot, expression);
  }

  /**
   * Rewrite {@code expressionRoot} such that {@code subExpression} is a {@code MOVABLE} while
   * maintaining evaluation order.
   *
   * <p>Two types of subexpressions are extracted from the source expression:
   *
   * <ol>
   *   <li>subexpressions with side-effects
   *   <li>conditional expressions that contain {@code subExpression}, which are transformed into IF
   *       statements.
   * </ol>
   *
   * <p>The following terms are used:
   *
   * <ul>
   *   <li>expressionRoot: The top-level node, before which the any extracted expressions should be
   *       placed.
   *   <li>nodeWithNonconditionalParent: The node that will be extracted.
   * </ul>
   *
   * @param expressionRoot The root of the subtree within which to expose {@code subExpression}.
   * @param subExpression A descendant of {@code expressionRoot} to be exposed.
   */
  private void exposeExpression(Node expressionRoot, Node subExpression) {
    Node nodeWithNonconditionalParent = findNonconditionalParent(subExpression, expressionRoot);
    // Before extraction, record whether there are side-effect
    boolean hasFollowingSideEffects = astAnalyzer.mayHaveSideEffects(nodeWithNonconditionalParent);

    Node exprInjectionPoint = findInjectionPoint(nodeWithNonconditionalParent);
    DecompositionState state = new DecompositionState();
    state.sideEffects = hasFollowingSideEffects;
    state.extractBeforeStatement = exprInjectionPoint;

    // Extract expressions in the reverse order of their evaluation. This is roughly, traverse up
    // the AST extracting any preceding expressions that may have side-effects or be side-effected.
    Node lastExposedSubexpression = null;
    Node expressionToExpose = nodeWithNonconditionalParent;
    Node expressionParent = expressionToExpose.getParent();
    while (expressionParent != expressionRoot) {
      checkState(
          !isConditionalOp(expressionParent) || expressionToExpose.isFirstChildOf(expressionParent),
          expressionParent);

      if (expressionParent.isAssign()) {
        if (isSafeAssign(expressionParent, state.sideEffects)) {
          // It is always safe to inline "foo()" for expressions such as
          // "a = b = c = foo();"
          // As the assignment is unaffected by side effect of "foo()"
          // and the names assigned-to can not influence the state before
          // the call to foo.
          //
          // This is not true of more complex LHS values, such as
          // a.x = foo();
          // next().x = foo();
          // in these cases the checks below are necessary.
        } else if (!expressionToExpose.isFirstChildOf(expressionParent)) {
          // Alias "next()" in "next().foo"

          Node left = expressionParent.getFirstChild();
          switch (left.getToken()) {
            case GETELEM:
              decomposeSubExpressions(left.getLastChild(), null, state);
              // Fall through.
            case GETPROP:
              decomposeSubExpressions(left.getFirstChild(), null, state);
              break;
            default:
              throw new IllegalStateException("Expected a property access: " + left.toStringTree());
          }
        }
      } else if (expressionParent.isCall()
          && NodeUtil.isNormalGet(expressionParent.getFirstChild())) {
        Node callee = expressionParent.getFirstChild();
        decomposeSubExpressions(callee.getNext(), expressionToExpose, state);

        // Now handle the call expression. We only have to do this if we arrived at decomposing this
        // call through one of the arguments, rather than the callee; otherwise the callee would
        // already be safe.
        if (isExpressionTreeUnsafe(callee, state.sideEffects)
            && lastExposedSubexpression != callee.getFirstChild()) {
          checkState(allowMethodCallDecomposing, "Object method calls can not be decomposed.");
          // Either there were preexisting side-effects, or this node has side-effects.
          state.sideEffects = true;
          // Rewrite the call so "this" is preserved and continue walking up from there.
          expressionParent = rewriteCallExpression(expressionParent, state);
        }
      } else {
        decomposeSubExpressions(expressionParent.getFirstChild(), expressionToExpose, state);
      }

      lastExposedSubexpression = expressionToExpose;
      expressionToExpose = expressionParent;
      expressionParent = expressionToExpose.getParent();
    }

    // Now extract the expression that the decomposition is being performed to
    // to allow to be moved.  All expressions that need to be evaluated before
    // this have been extracted, so add the expression statement after the
    // other extracted expressions and the original statement (or replace
    // the original statement.
    if (nodeWithNonconditionalParent == subExpression) {
      // Don't extract the call, as that introduces an extra constant VAR
      // that will simply need to be inlined back.  It will be handled as
      // an EXPRESSION call site type.
      // Node extractedCall = extractExpression(decomposition, expressionRoot);
    } else {
      if (NodeUtil.isOptChainNode(nodeWithNonconditionalParent)) {
        //  e.g. for `result = x.y?.z.p?.q(foo());` exposing foo()
        //  `x.y?.z.p?.q(foo())` will be nodeWithNonConditionalParent
        //  the actual node to be extracted is its first child, `x.y?.z.p?.q`.
        extractOptionalChain(nodeWithNonconditionalParent, exprInjectionPoint);
      } else {
        Node parent = nodeWithNonconditionalParent.getParent();
        boolean needResult = !parent.isExprResult();
        extractConditional(nodeWithNonconditionalParent, exprInjectionPoint, needResult);
      }
    }
  }

  /**
   * Extract the specified expression from its parent expression.
   *
   * @see #canExposeExpression
   */
  void moveExpression(Node expression) {
    // TODO(johnlenz): This is not currently used by the function inliner,
    // as moving the call out of the expression before the actual function call
    // causes additional variables to be introduced.  As the variable
    // inliner is improved, this might be a viable option.

    String resultName = getResultValueName();
    Node injectionPoint = findInjectionPoint(expression);
    checkNotNull(injectionPoint);
    Node injectionPointParent = injectionPoint.getParent();
    checkNotNull(injectionPointParent);
    checkState(NodeUtil.isStatementBlock(injectionPointParent));

    // Replace the expression with a reference to the new name.
    Node expressionParent = expression.getParent();
    expressionParent.replaceChild(
        expression, withType(IR.name(resultName), expression.getJSType()));

    // Re-add the expression at the appropriate place.
    Node newExpressionRoot = NodeUtil.newVarNode(resultName, expression);
    newExpressionRoot.getFirstChild().setJSType(expression.getJSType());
    injectionPointParent.addChildBefore(newExpressionRoot, injectionPoint);

    compiler.reportChangeToEnclosingScope(injectionPointParent);
  }

  /**
   * Returns the enclosing expression to decompose
   *
   * <p>The intention is to indicate the top-most node that could be rewritten as an if-statement in
   * order to better expose subExpression for inlining.
   *
   * <p>Examples:
   *
   * <pre>{@code
   * a = (x() && y()) && subExpression; // result is (x() && y()) && subExpression
   * a = x() && (y() && subExpression); // result is x() && (y() && subExpression)
   * a = (x() && subExpression) && y(); // result is x() && subExpression
   * a = x() && (subExpression && y()); // result is x() && (subExpression && y())
   * a = (subExpression && x()) && y(); // result is subExpression
   * a = subExpression && (x() && y()); // result is subExpression
   * }</pre>
   *
   * <p>When subExpression is contained within an optional chain, we want to treat everything after
   * a `?.` up until the next `?.` as a single conditional operation.
   *
   * <p>Examples:
   *
   * <pre>
   * a = subExpression.x?.y.z();          // result is subExpression
   * a = x()?.[subExpression].y;          // result is x()?.[subExpression].y
   * a = x()?.y.z?.p(subExpression).q?.r; // result is x()?.y.z?.p(subExpression).q
   * a
   * </pre>
   *
   * @param subExpression the expression to consider entire chains
   * @param expressionRoot a node containing subExpression. The returned node will be a descendent
   *     of this one.
   */
  private static Node findNonconditionalParent(Node subExpression, Node expressionRoot) {
    Node result = subExpression;

    for (Node child = subExpression, parent = child.getParent();
        parent != expressionRoot;
        child = parent, parent = child.getParent()) {
      if (isConditionalOp(parent) && !child.isFirstChildOf(parent)) {
        // subExpression is not part of the first child (which is always executed), so
        // parent decides whether subExpression will be executed or not
        result = parent;
      }
    }
    if (NodeUtil.isOptChainNode(result)) {
      // the loop above may have left result pointing into the middle of an optional chain for
      // a case like this.
      // `x?.y.z(subExpression).p.q?.r.s`
      // result is currently `x?.y.z(subExpression)`, but we want it to be the full sub-chain
      // containing subExpression
      // `x?.y.z(subExpression).p.q`
      result = NodeUtil.getEndOfOptChain(result);
    }

    return result;
  }

  /**
   * A simple class to track two things: - whether side effects have been seen. - the last statement
   * inserted
   */
  private static class DecompositionState {
    boolean sideEffects;
    Node extractBeforeStatement;

    @Override
    public String toString() {
      return toStringHelper(this)
          .add("sideEffects", sideEffects)
          .add("extractBeforeStatement", extractBeforeStatement)
          .toString();
    }
  }

  /**
   * @param n The node with which to start iterating.
   * @param stopNode A node after which to stop iterating.
   */
  private void decomposeSubExpressions(Node n, Node stopNode, DecompositionState state) {
    if (n == null || n == stopNode) {
      return;
    }

    // Decompose the children in reverse evaluation order. This simplifies determining if any of
    // the children following have side-effects. If they do we need to be more aggressive about
    // removing values from the expression. Reverse order also maintains evaluation order as each
    // extracted statemented is inserted on top of the others.
    decomposeSubExpressions(n.getNext(), stopNode, state);

    // Now this node.

    if (NodeUtil.mayBeObjectLitKey(n)
        // TODO(b/111621528): Delete when fixed.
        || n.isComputedProp()) {
      if (n.isComputedProp()) {
        // If the prop is computed we have to fork the decomposition between the key and value. This
        // is because we can't move the property assignment itself; COMPUTED_PROP must remain a
        // child of OBJECTLIT for example.
        //
        // We decompose the value of the prop first because decomposition is in reverse order of
        // evaluation.
        decomposeSubExpressions(n.getSecondChild(), stopNode, state);
      }

      // Decompose the children of the prop rather than the prop itself. In the computed case this
      // will be the key, otherwise it will be the value.
      n = n.getFirstChild();
    } else if (n.isTemplateLitSub()) {
      // A template literal substitution expression like ${f()} is represented in the AST as
      //   TEMPLATELIT_SUB
      //     CALL
      //       NAME f
      // The TEMPLATELIT_SUB node is not actually an expression and can't be extracted, but we may
      // need to extract the expression inside of it.
      n = n.getFirstChild();
    } else if (n.isSpread()) {
      // SPREADs aren't expression but they can still be extracted using temp variables.
      //
      // Because object-spread can trigger getters we assume all spreads have side-effects.
      // TODO(nickreid): Use `assumeGettersArePure` here. It would have been a pain to pipe it down
      // here and write all the tests. Since there are very few cases, and it doesn't affect code
      // removal, we didn't bother initially. Everything always works one way.
    } else if (!IR.mayBeExpression(n)) {
      // If n is not an expression then it can't be extracted. For example if n is the destructuring
      // pattern on the left side of a VAR statement:
      //   var {pattern} = rhs();
      // See test case: testExposeExpression18
      return;
    }

    // TODO(johnlenz): Move "safety" code to a shared class.
    if (isExpressionTreeUnsafe(n, state.sideEffects)) {
      // Either there were preexisting side-effects, or this node has side-effects.
      state.sideEffects = true;
      state.extractBeforeStatement = extractExpression(n, state.extractBeforeStatement);
    }
  }

  /**
   * Replaces an expression with a new temporary variable containing its value.
   *
   * <p>Replaces expr with a reference to the temporary variable. Then inserts a declaration of the
   * variable, with expr as its value.
   *
   * @param tempVarName name to use for the temporary variable
   * @param expr original expression to replace
   * @param injectionPoint declaration will be inserted before this node
   * @return the new statement declaring the temporary variable
   */
  private Node extractToTempVar(String tempVarName, Node expr, Node injectionPoint) {
    Node exprReplacement = astFactory.createName(tempVarName, expr.getJSType());
    expr.replaceWith(exprReplacement);
    Node tempVarNodeDeclaration =
        astFactory
            .createSingleVarNameDeclaration(tempVarName, expr)
            .useSourceInfoIfMissingFromForTree(expr);
    insertBefore(injectionPoint, tempVarNodeDeclaration);
    return tempVarNodeDeclaration;
  }

  /**
   * Extract the conditional in optional chain expressions into IF statements.
   *
   * @param optChainNode The end of the optional chain to extract.
   * @param injectionPoint The node before which the extracted expression would be injected.
   */
  private void extractOptionalChain(Node optChainNode, Node injectionPoint) {
    checkState(NodeUtil.isOptChainNode(optChainNode), optChainNode);

    // find the start of the chain & convert it to non-optional
    final Node optChainStart = NodeUtil.getStartOfOptChain(optChainNode);
    optionalToNonOptionalChain(optChainStart);

    // Identify or create the statement that will need to go into the if-statement body
    final Node ifBodyStatement;
    final Node optChainParent = optChainNode.getParent();
    if (optChainParent.isExprResult()) {
      // optional chain is a statement unto itself, so just put that statement into the
      // if-statement body
      ifBodyStatement = optChainParent;
    } else {
      // We need to replace the chain with a temporary holding its value.
      // ```
      // var tmpResult = optChain;
      // originalExpression(tmpResult)
      // ```
      // It is the tmpResult assignment that will need to go
      final String tmpResultName = getTempValueName();
      ifBodyStatement = extractToTempVar(tmpResultName, optChainNode, injectionPoint);
    }

    // Extract the value to be tested into a temporary variable
    // to get something like this.
    // ```
    // var tmpReceiver = receiverExpression;
    // tmpReceiver.rest.of.opt.chain; // ifBodyStatement
    // ```
    final Node receiverNode = optChainStart.getFirstChild();
    final String tmpReceiverName = getTempValueName();
    final Node receiverDeclaration =
        extractToTempVar(tmpReceiverName, receiverNode, ifBodyStatement);

    // If we've rewritten a call of one of these forms
    // obj.method?.() or obj[methodExpr]?.()
    // we must rewrite using 'call' and supply the correct value for `this`
    if (optChainStart.isCall() && NodeUtil.isNormalGet(receiverNode)) {
      final Node callNode = optChainNode; // for readability
      // break call receiver off from tmpReceiver that was created above
      // var tmpCallReceiver = callReceiver;
      // var tmpReceiver = callReceiver.method; (or callReceiver[methodExpression])
      final Node callReceiver = receiverNode.getFirstChild();
      final String tmpCallReceiverName = getTempValueName();
      extractToTempVar(tmpCallReceiverName, callReceiver, receiverDeclaration);
      // now rewrite the call
      // tmpReceiver(arg1, arg2).rest.of.chain
      // to
      // tmpReceiver.call(tmpCallReceiver, arg1, arg2).rest.of.chain
      final Node originalCallee = callNode.getFirstChild();
      originalCallee.detach();
      final Node newCallee =
          astFactory
              .createGetProp(originalCallee, "call")
              .useSourceInfoIfMissingFromForTree(originalCallee);
      final Node thisArgument =
          astFactory.createName(tmpCallReceiverName, callReceiver.getJSType()).srcref(callReceiver);
      callNode.addChildToFront(thisArgument);
      callNode.addChildToFront(newCallee);
    }

    // Wrap ifBodyStatement with the null check condition
    // ```
    // if (tmpReceiver != null) {
    //   tmpReceiver.rest.of.chain; // ifBodyStatement
    // }
    // ```
    // create detached `tmpReceiver != null`
    final Node nullCheck =
        astFactory
            .createNe(
                astFactory.createName(tmpReceiverName, receiverNode.getJSType()),
                astFactory.createNull())
            .srcrefTree(receiverNode);
    // ifBody is initially empty, since we'll want to inject the if-statement before
    // ifBodyStatement, then move ifBodyStatement into it.
    final Node ifBody = astFactory.createBlock().srcref(ifBodyStatement);
    final Node ifStatement = astFactory.createIf(nullCheck, ifBody).srcref(optChainNode);
    insertBefore(ifBodyStatement, ifStatement);
    ifBody.addChildToFront(ifBodyStatement.detach());
  }

  private static void insertBefore(Node injectionPoint, Node newNode) {
    final Node injectionParent = injectionPoint.getParent();
    injectionParent.addChildBefore(newNode, injectionPoint);
  }

  /**
   * @param expr The conditional expression to extract.
   * @param injectionPoint The node before which the extracted expression would be injected.
   * @param needResult Whether the result of the expression is required.
   * @return The node that contains the logic of the expression after extraction.
   */
  private Node extractConditional(Node expr, Node injectionPoint, boolean needResult) {
    Node parent = expr.getParent();
    String tempName = getTempValueName();

    // Break down the conditional.
    Node first = expr.getFirstChild();
    Node second = first.getNext();
    Node last = expr.getLastChild();

    // Isolate the children nodes.
    expr.detachChildren();

    // Transform the conditional to an IF statement.
    Node cond = null;
    Node trueExpr = astFactory.createBlock().srcref(expr);
    Node falseExpr = astFactory.createBlock().srcref(expr);
    switch (expr.getToken()) {
      case HOOK:
        // a = x?y:z --> if (x) {a=y} else {a=z}
        cond = first;
        trueExpr.addChildToFront(
            astFactory.exprResult(buildResultExpression(second, needResult, tempName)));
        falseExpr.addChildToFront(
            astFactory.exprResult(buildResultExpression(last, needResult, tempName)));
        break;
      case AND:
        // a = x&&y --> if (a=x) {a=y} else {}
        cond = buildResultExpression(first, needResult, tempName);
        trueExpr.addChildToFront(
            astFactory.exprResult(buildResultExpression(last, needResult, tempName)));
        break;
      case OR:
        // a = x||y --> if (a=x) {} else {a=y}
        cond = buildResultExpression(first, needResult, tempName);
        falseExpr.addChildToFront(
            astFactory.exprResult(buildResultExpression(last, needResult, tempName)));
        break;
      case COALESCE:
        // a = x ?? y --> if ((temp=x)!=null) {a=temp} else {a=y}
        String tempNameAssign = getTempValueName();
        Node tempVarNodeAssign =
            astFactory
                .createSingleVarNameDeclaration(tempNameAssign)
                .useSourceInfoIfMissingFromForTree(expr);
        Node injectionPointParent = injectionPoint.getParent();
        injectionPointParent.addChildBefore(tempVarNodeAssign, injectionPoint);

        Node assignLhs = buildResultExpression(first, true, tempNameAssign);
        Node nullNode = astFactory.createNull().useSourceInfoFrom(expr);
        cond = astFactory.createNe(assignLhs, nullNode).useSourceInfoFrom(expr);
        trueExpr.addChildToFront(
            astFactory.exprResult(
                buildResultExpression(
                    astFactory
                        .createName(tempNameAssign, first.getJSType())
                        .useSourceInfoFrom(expr),
                    needResult,
                    tempName)));
        falseExpr.addChildToFront(
            astFactory.exprResult(buildResultExpression(last, needResult, tempName)));
        break;
      default:
        // With a valid tree we should never get here.
        throw new IllegalStateException("Unexpected expression: " + expr);
    }

    Node ifNode;
    if (falseExpr.hasChildren()) {
      ifNode = astFactory.createIf(cond, trueExpr, falseExpr);
    } else {
      ifNode = astFactory.createIf(cond, trueExpr);
    }
    ifNode.useSourceInfoIfMissingFrom(expr);

    if (needResult) {
      Node tempVarNode =
          astFactory
              .createSingleVarNameDeclaration(tempName)
              .useSourceInfoIfMissingFromForTree(expr);
      Node injectionPointParent = injectionPoint.getParent();
      injectionPointParent.addChildBefore(tempVarNode, injectionPoint);
      injectionPointParent.addChildAfter(ifNode, tempVarNode);

      // Replace the expression with the temporary name.
      Node replacementValueNode = withType(IR.name(tempName), expr.getJSType());
      parent.replaceChild(expr, replacementValueNode);
    } else {
      // Only conditionals that are the direct child of an expression statement
      // don't need results, for those simply replace the expression statement.
      checkArgument(parent.isExprResult());
      Node grandparent = parent.getParent();
      grandparent.replaceChild(parent, ifNode);
    }

    return ifNode;
  }

  /**
   * Create an expression tree for an expression.
   *
   * <p>If the result of the expression is needed, then:
   *
   * <pre>
   * ASSIGN
   *   tempName
   *   expr
   * </pre>
   *
   * otherwise, simply: `expr`
   */
  private static Node buildResultExpression(Node expr, boolean needResult, String tempName) {
    if (needResult) {
      JSType type = expr.getJSType();
      return withType(IR.assign(withType(IR.name(tempName), type), expr), type).srcrefTree(expr);
    } else {
      return expr;
    }
  }

  private boolean isConstantNameNode(Node n) {
    // Non-constant names values may have been changed.
    return n.isName()
        && (NodeUtil.isConstantVar(n, scope) || knownConstants.contains(n.getString()));
  }

  /**
   * @param expr The expression to extract.
   * @param injectionPoint The node before which to added the extracted expression.
   * @return The extracted statement node.
   */
  private Node extractExpression(Node expr, Node injectionPoint) {
    Node parent = expr.getParent();

    boolean isLhsOfAssignOp =
        NodeUtil.isAssignmentOp(parent) && !parent.isAssign() && expr.isFirstChildOf(parent);

    Node firstExtractedNode = null;

    // Expressions on the LHS of an assignment-op must have any possible
    // side-effects extracted as the value must be duplicated:
    //    next().foo += 2;
    // becomes:
    //    var t1 = next();
    //    t1.foo = t1.foo + 2;
    if (isLhsOfAssignOp && NodeUtil.isNormalGet(expr)) {
      for (Node n : expr.children()) {
        if (!n.isString() && !isConstantNameNode(n)) {
          Node extractedNode = extractExpression(n, injectionPoint);
          if (firstExtractedNode == null) {
            firstExtractedNode = extractedNode;
          }
        }
      }
    }

    // The temp is known to be constant.
    String tempName = getTempConstantValueName();
    Node replacementValueNode = IR.name(tempName).setJSType(expr.getJSType()).srcref(expr);

    Node tempNameValue;

    // If it is ASSIGN_XXX, keep the assignment in place and extract the
    // original value of the LHS operand.
    if (isLhsOfAssignOp) {
      checkState(expr.isName() || NodeUtil.isNormalGet(expr), expr);
      // Transform "x += 2" into "x = temp + 2"
      Node opNode =
          withType(new Node(NodeUtil.getOpFromAssignmentOp(parent)), parent.getJSType())
              .useSourceInfoIfMissingFrom(parent);

      Node rightOperand = parent.getLastChild();

      parent.setToken(Token.ASSIGN);
      parent.replaceChild(rightOperand, opNode);
      opNode.addChildToFront(replacementValueNode);
      opNode.addChildToBack(rightOperand);

      // The original expression is still being used, so make a clone.
      tempNameValue = expr.cloneTree();
    } else if (expr.isSpread()) {
      // We need to treat spreads differently because unlike other expressions, they can't be
      // directly assigned to new variables. Instead we wrap them in a literal.
      //
      // We make sure to do `var tmp = [...fn()];` rather than `var tmp = fn()` because the
      // execution of a spread on an arbitrary iterable/object can both have side-effects and be
      // side-effected. However, once done we are then sure that spreading `tmp` is isolated.

      // Replace the expression with the spread for the temporary name.
      Node spreadCopy = expr.cloneNode();
      spreadCopy.addChildToBack(replacementValueNode);
      expr.replaceWith(spreadCopy);

      // Move the original node into a legal context.
      switch (parent.getToken()) {
        case ARRAYLIT:
        case CALL:
        case NEW:
          tempNameValue = astFactory.createArraylit(expr).useSourceInfoFrom(expr.getOnlyChild());
          break;
        case OBJECTLIT:
          tempNameValue = astFactory.createObjectLit(expr).useSourceInfoFrom(expr.getOnlyChild());
          break;
        default:
          throw new IllegalStateException("Unexpected parent of SPREAD:" + parent.toStringTree());
      }
    } else {
      // Replace the expression with the temporary name.
      parent.replaceChild(expr, replacementValueNode);

      // Keep the original node so that CALL expressions can still be found
      // and inlined properly.
      tempNameValue = expr;
    }

    // Re-add the expression in the declaration of the temporary name.
    Node tempVarNode = NodeUtil.newVarNode(tempName, tempNameValue);
    tempVarNode.getFirstChild().setJSType(tempNameValue.getJSType());

    insertBefore(injectionPoint, tempVarNode);

    if (firstExtractedNode == null) {
      firstExtractedNode = tempVarNode;
    }

    checkState(firstExtractedNode.isVar());
    return firstExtractedNode;
  }

  /**
   * Rewrite the call so "this" is preserved.
   *
   * <pre>a.b(c);</pre>
   *
   * becomes:
   *
   * <pre>
   * var temp1 = a; var temp0 = temp1.b;
   * temp0.call(temp1,c);
   * </pre>
   *
   * @return The replacement node.
   */
  private Node rewriteCallExpression(Node call, DecompositionState state) {
    checkArgument(call.isCall(), call);
    Node first = call.getFirstChild();
    checkArgument(NodeUtil.isNormalGet(first), first);

    // Find the type of (fn expression).call
    JSType fnType = first.getJSType();
    JSType fnCallType = null;
    if (fnType != null) {
      fnCallType =
          fnType.isFunctionType()
              ? fnType.toMaybeFunctionType().getPropertyType("call")
              : unknownType;
    }

    // Extracts the expression representing the function to call. For example:
    //   "a['b'].c" from "a['b'].c()"
    Node getVarNode = extractExpression(first, state.extractBeforeStatement);
    state.extractBeforeStatement = getVarNode;

    // Extracts the object reference to be used as "this". For example:
    //   "a['b']" from "a['b'].c"
    Node getExprNode = getVarNode.getFirstFirstChild();
    checkArgument(NodeUtil.isNormalGet(getExprNode), getExprNode);
    Node thisVarNode = extractExpression(getExprNode.getFirstChild(), state.extractBeforeStatement);
    state.extractBeforeStatement = thisVarNode;

    // Rewrite the CALL expression.
    Node thisNameNode = thisVarNode.getFirstChild();
    Node functionNameNode = getVarNode.getFirstChild();

    // CALL
    //   GETPROP
    //     functionName
    //     "call"
    //   thisName
    //   original-parameter1
    //   original-parameter2
    //   ...

    Node newCall =
        IR.call(
                withType(
                    IR.getprop(
                        functionNameNode.cloneNode(), withType(IR.string("call"), stringType)),
                    fnCallType),
                thisNameNode.cloneNode())
            .setJSType(call.getJSType())
            .useSourceInfoIfMissingFromForTree(call);

    // Throw away the call name
    call.removeFirstChild();
    if (call.hasChildren()) {
      // Add the call parameters to the new call.
      newCall.addChildrenToBack(call.removeChildren());
    }

    call.replaceWith(newCall);

    return newCall;
  }

  private String tempNamePrefix = "JSCompiler_temp";
  private String resultNamePrefix = "JSCompiler_inline_result";

  /** Allow the temp name to be overridden to make tests more readable. */
  @VisibleForTesting
  public void setTempNamePrefix(String prefix) {
    this.tempNamePrefix = prefix;
  }

  /** Create a unique temp name. */
  private String getTempValueName() {
    return tempNamePrefix + ContextualRenamer.UNIQUE_ID_SEPARATOR + safeNameIdSupplier.get();
  }

  /** Allow the temp name to be overridden to make tests more readable. */
  @VisibleForTesting
  public void setResultNamePrefix(String prefix) {
    this.resultNamePrefix = prefix;
  }

  /** Create a unique name for call results. */
  private String getResultValueName() {
    return resultNamePrefix + ContextualRenamer.UNIQUE_ID_SEPARATOR + safeNameIdSupplier.get();
  }

  /** Create a constant unique temp name. */
  private String getTempConstantValueName() {
    String name =
        tempNamePrefix
            + "_const"
            + ContextualRenamer.UNIQUE_ID_SEPARATOR
            + safeNameIdSupplier.get();
    this.knownConstants.add(name);
    return name;
  }

  private boolean isTempConstantValueName(Node name) {
    return name.isName()
        && name.getString()
            .startsWith(tempNamePrefix + "_const" + ContextualRenamer.UNIQUE_ID_SEPARATOR);
  }

  /**
   * @return For the subExpression, find the nearest statement Node before which it can be inlined.
   *     Null if no such location can be found.
   */
  @Nullable
  static Node findInjectionPoint(Node subExpression) {
    Node expressionRoot = findExpressionRoot(subExpression);
    checkNotNull(expressionRoot);

    Node injectionPoint = expressionRoot;

    Node parent = injectionPoint.getParent();
    while (parent.isLabel()) {
      injectionPoint = parent;
      parent = injectionPoint.getParent();
    }

    checkState(NodeUtil.isStatementBlock(parent), parent);
    return injectionPoint;
  }

  /** @return Whether the node is a conditional op. */
  private static boolean isConditionalOp(Node n) {
    switch (n.getToken()) {
      case HOOK:
      case AND:
      case OR:
      case COALESCE:
      case OPTCHAIN_GETELEM:
      case OPTCHAIN_GETPROP:
      case OPTCHAIN_CALL:
        return true;
      default:
        return false;
    }
  }

  /**
   * Finds the statement containing {@code subExpression}.
   *
   * <p>If {@code subExpression} is not contained by a statement where inlining is known to be
   * possible, {@code null} is returned. For example, the condition expression of a WHILE loop.
   */
  @Nullable
  private static Node findExpressionRoot(Node subExpression) {
    Node child = subExpression;
    for (Node current : child.getAncestors()) {
      Node parent = current.getParent();
      switch (current.getToken()) {
          // Supported expression roots:
          // SWITCH and IF can have multiple children, but the CASE, DEFAULT,
          // or BLOCK will be encountered first for any of the children other
          // than the condition.
        case EXPR_RESULT:
        case IF:
        case SWITCH:
        case RETURN:
        case THROW:
          Preconditions.checkState(child.isFirstChildOf(current));
          return current;

        case VAR:
          // Normalization will remove LABELs from VARs.
        case LET:
        case CONST:
          if (NodeUtil.isAnyFor(parent)) {
            break; // Name declarations may not be roots if they're for-loop initializers.
          }
          return current;

          // Any of these indicate an unsupported expression:
        case FOR:
          if (child.isFirstChildOf(current)) {
            // Only the initializer of a for-loop could possibly be decomposed since the other
            // statements need to execute each iteration.
            return current;
          }
          // fall through
        case FOR_IN:
        case FOR_OF:
        case FOR_AWAIT_OF:
        case DO:
        case WHILE:
        case SCRIPT:
        case BLOCK:
        case LABEL:
        case CASE:
        case DEFAULT_CASE:
        case DEFAULT_VALUE:
        case PARAM_LIST:
          return null;

        default:
          break;
      }
      child = current;
    }

    throw new IllegalStateException("Unexpected AST structure.");
  }

  /**
   * Determines if {@code subExpression} can be moved before {@code expressionRoot} without changing
   * the behaviour of the code, or if there is a rewriting that would make such motion possible.
   *
   * <p>Walks the AST from {@code subExpression} to {@code expressionRoot} and verifies that the
   * portions of the {@code expressionRoot} subtree that are evaluated before {@code subExpression}:
   *
   * <ol>
   *   <li>are unaffected by the side-effects, if any, of the {@code subExpression}.
   *   <li>have no side-effects that may influence the {@code subExpression}.
   *   <li>have a syntactically legal rewriting.
   * </ol>
   *
   * <p>Examples:
   *
   * <ul>
   *   <ul>
   *     <li>{@code expressionRoot} = `a = 1 + x();`
   *     <li>{@code subExpression} = `x()`, has side-effects
   *     <li>{@code MOVABLE} because the final value of `a` can not be influenced by `x()`.
   *   </ul>
   *   <ul>
   *     <li>{@code expressionRoot} = `a = b + x();`
   *     <li>{@code subExpression} = `x()`, has side-effects
   *     <li>{@code DECOMPOSABLE} because `b` may be modified by `x()`, but `b` can be cached.
   *   </ul>
   *   <ul>
   *     <li>{@code expressionRoot} = `a = b + x();`
   *     <li>{@code subExpression} = `x()`, no side-effects
   *     <li>{@code MOVABLE} because `x()` can be computed before or after `b` is resolved.
   *   </ul>
   *   <ul>
   *     <li>{@code expressionRoot} = `a = (b = c) + x();`
   *     <li>{@code subExpression} = `x()`, no side-effects, is side-effected
   *     <li>{@code DECOMPOSABLE} because `x()` may read `b`.
   *   </ul>
   * </ul>
   *
   * @return
   *     <ul>
   *       <li>{@code MOVABLE} if {@code subExpression} can already be moved.
   *       <li>{@code DECOMPOSABLE} if the {@code expressionRoot} subtree could be rewritten such
   *           that {@code subExpression} would be made movable.
   *       <li>{@code UNDECOMPOSABLE} otherwise.
   *     </ul>
   */
  DecompositionType canExposeExpression(Node subExpression) {
    Node expressionRoot = findExpressionRoot(subExpression);
    if (expressionRoot != null) {
      return isSubexpressionMovable(expressionRoot, subExpression);
    }
    return DecompositionType.UNDECOMPOSABLE;
  }

  /** @see {@link #canExposeExpression(Node subExpression)} */
  private DecompositionType isSubexpressionMovable(Node expressionRoot, Node subExpression) {
    boolean requiresDecomposition = false;
    boolean seenSideEffects = astAnalyzer.mayHaveSideEffects(subExpression);

    Node child = subExpression;
    for (Node parent : child.getAncestors()) {
      if (NodeUtil.isNameDeclaration(parent) && !child.isFirstChildOf(parent)) {
        // Case: `let x = 5, y = 2 * x;` where `child = y`.
        // Compound declarations cannot generally be decomposed. Later declarations might reference
        // earlier ones and if it were possible to separate them, `Normalize` would already have
        // done so. Therefore, we only support decomposing the first declaration.
        // TODO(b/121157467): FOR initializers are probably the only source of these cases.
        return DecompositionType.UNDECOMPOSABLE;
      }

      if (parent == expressionRoot) {
        // Done. The walk back to the root of the expression is complete, and
        // nothing was encountered that blocks the call from being moved.
        return requiresDecomposition ? DecompositionType.DECOMPOSABLE : DecompositionType.MOVABLE;
      }

      if (isConditionalOp(parent)) {
        // Only the first child is always executed, otherwise it must be
        // decomposed.
        if (child != parent.getFirstChild()) {
          requiresDecomposition = true;
        }
      } else {
        // Only inline the call if none of the preceding siblings in the
        // expression have side-effects, and are unaffected by the side-effects,
        // if any, of the call in question.
        // NOTE: The siblings are not always in the order in which they are evaluated, so we call
        // getEvaluationDirection to see in which order to traverse the siblings.

        // SPECIAL CASE: Assignment to a simple name
        if (isSafeAssign(parent, seenSideEffects)) {
          // It is always safe to inline "foo()" for expressions such as
          //   "a = b = c = foo();"
          // As the assignment is unaffected by side effect of "foo()"
          // and the names assigned-to can not influence the state before
          // the call to foo.
          //
          // This is not true of more complex LHS values, such as
          //    a.x = foo();
          //    next().x = foo();
          // in these cases the checks below are necessary.
        } else {
          // Everything else.
          EvaluationDirection direction = getEvaluationDirection(parent);
          for (Node n = getFirstEvaluatedChild(parent, direction);
              n != null;
              n = getNextEvaluatedSibling(n, direction)) {
            if (n == child) {
              // None of the preceding siblings have side-effects.
              // This is OK.
              break;
            }

            if (isExpressionTreeUnsafe(n, seenSideEffects)) {
              seenSideEffects = true;
              requiresDecomposition = true;
            }
          }

          // In Internet Explorer, DOM objects and other external objects
          // methods can not be called indirectly, as is required when the
          // object or its property can be side-effected.  For example,
          // when exposing expression f() (with side-effects) in: x.m(f())
          // either the value of x or its property m might have changed, so
          // both the 'this' value ('x') and the function to be called ('x.m')
          // need to be preserved. Like so:
          //   var t1 = x, t2 = x.m, t3 = f();
          //   t2.call(t1, t3);
          // As IE doesn't support the call to these non-JavaScript objects
          // methods in this way. We can't do this.
          // We don't currently distinguish between these types of objects
          // in the extern definitions and if we did we would need accurate
          // type information.
          //
          Node first = parent.getFirstChild();
          if (requiresDecomposition && parent.isCall() && NodeUtil.isNormalGet(first)) {
            if (allowMethodCallDecomposing) {
              return DecompositionType.DECOMPOSABLE;
            } else {
              return DecompositionType.UNDECOMPOSABLE;
            }
          }
        }
      }
      // Continue looking up the expression tree.
      child = parent;
    }

    // With a valid tree we should never get here.
    throw new IllegalStateException("Unexpected.");
  }

  private enum EvaluationDirection {
    FORWARD,
    REVERSE
  }

  /**
   * Returns the order in which the given node's children should be evaluated.
   *
   * <p>In most cases, this is EvaluationDirection.FORWARD because the AST order matches the actual
   * evaluation order. A few nodes require reversed evaluation instead.
   */
  private static EvaluationDirection getEvaluationDirection(Node node) {
    switch (node.getToken()) {
      case DESTRUCTURING_LHS:
      case ASSIGN:
      case DEFAULT_VALUE:
        if (node.getFirstChild().isDestructuringPattern()) {
          // The lhs of a destructuring assignment is evaluated AFTER the rhs. This is only true for
          // destructuring, though, not assignments like "first().x = second()" where "first()" is
          // evaluated first.
          return EvaluationDirection.REVERSE;
        }
        // fall through
      default:
        return EvaluationDirection.FORWARD;
    }
  }

  private Node getFirstEvaluatedChild(Node parent, EvaluationDirection direction) {
    return direction == EvaluationDirection.FORWARD
        ? parent.getFirstChild()
        : parent.getLastChild();
  }

  private Node getNextEvaluatedSibling(Node node, EvaluationDirection direction) {
    return direction == EvaluationDirection.FORWARD ? node.getNext() : node.getPrevious();
  }

  /**
   * It is always safe to inline "foo()" for expressions such as "a = b = c = foo();" As the
   * assignment is unaffected by side effect of "foo()" and the names assigned-to can not influence
   * the state before the call to foo.
   *
   * <p>It is also safe in cases where the object is constant:
   *
   * <pre>
   * CONST_NAME.a = foo()
   * CONST_NAME[CONST_VALUE] = foo();
   * </pre>
   *
   * <p>This is not true of more complex LHS values, such as
   *
   * <pre>
   * a.x = foo();
   * next().x = foo();
   * </pre>
   *
   * in these cases the checks below are necessary.
   *
   * @param seenSideEffects If true, check to see if node-tree maybe affected by side-effects,
   *     otherwise if the tree has side-effects. @see isExpressionTreeUnsafe
   * @return Whether the assignment is safe from side-effects.
   */
  private boolean isSafeAssign(Node n, boolean seenSideEffects) {
    if (n.isAssign()) {
      Node lhs = n.getFirstChild();
      switch (lhs.getToken()) {
        case NAME:
          return true;
        case GETPROP:
          return !isExpressionTreeUnsafe(lhs.getFirstChild(), seenSideEffects);
        case GETELEM:
          return !isExpressionTreeUnsafe(lhs.getFirstChild(), seenSideEffects)
              && !isExpressionTreeUnsafe(lhs.getLastChild(), seenSideEffects);
        default:
          break;
      }
    }
    return false;
  }

  /**
   * Determines if there is any subexpression below {@code tree} that would make it incorrect for
   * some expression that follows {@code tree}, {@code E}, to be executed before {@code tree}.
   *
   * @param followingSideEffectsExist whether {@code E} causes side-effects.
   * @return {@code true} if {@code tree} contains any subexpressions that would make movement
   *     incorrect.
   */
  private boolean isExpressionTreeUnsafe(Node tree, boolean followingSideEffectsExist) {
    if (tree.isSpread()) {
      // Spread expressions would cause recursive rewriting if not special cased here.
      // When extracted, spreads can't be assigned to a single variable and instead are put into
      // a literal. However, that literal must be spread again at the original site. This
      // check is what prevents the original spread from triggering recursion.
      if (isTempConstantValueName(tree.getOnlyChild())) {
        return false;
      }
    }

    if (followingSideEffectsExist) {
      // If the call to be inlined has side-effects, check to see if this
      // expression tree can be affected by any side-effects.

      // Assume that "tmp1.call(...)" is safe (where tmp1 is a const temp variable created by
      // ExpressionDecomposer) otherwise we end up trying to decompose the same tree
      // an infinite number of times.
      Node parent = tree.getParent();
      if (NodeUtil.isObjectCallMethod(parent, "call")
          && tree.isFirstChildOf(parent)
          && isTempConstantValueName(tree.getFirstChild())) {
        return false;
      }

      // This is a superset of "NodeUtil.mayHaveSideEffects".
      return NodeUtil.canBeSideEffected(tree, this.knownConstants, scope);
    } else {
      // The function called doesn't have side-effects but check to see if there
      // are side-effects that that may affect it.
      return astAnalyzer.mayHaveSideEffects(tree);
    }
  }

  /** Given a the start node of an optional chain, change the whole chain to non-optional. */
  private static void optionalToNonOptionalChain(Node optChainStart) {
    checkState(optChainStart.isOptionalChainStart(), optChainStart);
    optChainStart.setIsOptionalChainStart(false);
    for (Node n = optChainStart;
        // Stop when we hit top, a non-chain node, or the start of a new chain
        n != null && NodeUtil.isOptChainNode(n) && !n.isOptionalChainStart();
        n = n.getParent()) {
      switch (n.getToken()) {
        case OPTCHAIN_CALL:
          n.setToken(Token.CALL);
          break;
        case OPTCHAIN_GETELEM:
          n.setToken(Token.GETELEM);
          break;
        case OPTCHAIN_GETPROP:
          n.setToken(Token.GETPROP);
          break;
        default:
          throw new IllegalStateException(
              "Should be an OPTCHAIN node. Unexpected expression: " + n);
      }
    }
  }
}
