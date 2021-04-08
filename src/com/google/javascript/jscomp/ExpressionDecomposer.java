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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.MakeDeclaredNamesUnique.ContextualRenamer;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import java.util.ArrayDeque;
import java.util.EnumSet;
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

  /**
   * Identify special case logic that needs to be enabled to work around edge cases, such as bad
   * JSVM behavior.
   */
  public enum Workaround {
    // IE11 throws an exception for `window.location.assign.call(window.location, url)`
    BROKEN_IE11_LOCATION_ASSIGN
  }

  private final AbstractCompiler compiler;
  private final AstAnalyzer astAnalyzer;
  private final AstFactory astFactory;
  private final Supplier<String> safeNameIdSupplier;
  private final ImmutableSet<String> knownConstantFunctions;
  private final Scope scope;
  private final EnumSet<Workaround> enabledWorkarounds;
  @Nullable private final JSType unknownType;

  /**
   * Create an ExpressionDecomposer.
   *
   * <p>DO NOT USE THIS METHOD DIRECTLY. Call `compiler.createExpressionDecomposer()` instead so the
   * compiler can supply the workarounds needed for this compilation.
   *
   * @param constFunctionNames set of names known to be constant functions. Used by InlineFunctions
   *     to prevent this pass from breaking bookkeeping for functions it's inlining.
   * @param enabledWorkarounds indicates which workarounds for bad JSVM behavior should be enabled.
   */
  ExpressionDecomposer(
      AbstractCompiler compiler,
      Supplier<String> safeNameIdSupplier,
      ImmutableSet<String> constFunctionNames,
      Scope scope,
      EnumSet<Workaround> enabledWorkarounds) {
    checkNotNull(compiler);
    checkNotNull(safeNameIdSupplier);
    checkNotNull(constFunctionNames);
    this.compiler = compiler;
    this.astAnalyzer = compiler.getAstAnalyzer();
    this.astFactory = compiler.createAstFactory();
    this.safeNameIdSupplier = safeNameIdSupplier;
    this.knownConstantFunctions = constFunctionNames;
    this.scope = scope;
    this.enabledWorkarounds = enabledWorkarounds;
    this.unknownType =
        compiler.hasTypeCheckingRun() && !compiler.hasOptimizationColors()
            ? compiler.getTypeRegistry().getNativeType(JSTypeNative.UNKNOWN_TYPE)
            : null;
  }

  // An arbitrary limit to prevent catch infinite recursion.
  // Raised from 100->1000 on Jan 22, 2021
  private static final int MAX_ITERATIONS = 1000;

  /**
   * Perform any rewriting necessary so that the specified expression is {@code MOVABLE}.
   *
   * <p>This method is a primary entrypoint into this class. It performs expression decomposition
   * such that {@code expression} can be moved to a preceding statement without changing behaviour.
   *
   * <p>Exposing {@code expression} generally doesn't mean that {@code expression} itself will
   * moved. An expression is exposed within a larger statement if no preceding expression would
   * interact with it.
   *
   * @see {@link #canExposeExpression}
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
   * Perform partial decomposition to get the given expression closer to being {@code MOVEABLE}.
   *
   * <p>This method should not be called from outside of this class. Instead call {@link
   * #maybeExposeExpression(Node)}.
   */
  private void exposeExpression(Node expression) {
    // First rewrite all optional chains containing the expression.
    // This must be done first, because the expression root may be an optional chain, and rewriting
    // it creates a new node to be the expression root.
    rewriteAllContainingOptionalChains(expression);
    Node expressionRoot = findExpressionRoot(expression);
    checkNotNull(expressionRoot);
    checkState(NodeUtil.isStatement(expressionRoot), expressionRoot);
    exposeExpression(expressionRoot, expression);
  }

  /**
   * Rewrite {@code expressionRoot} such that {@code subExpression} is a {@code MOVABLE} while
   * maintaining evaluation order.
   *
   * <p>IMPORTANT: This method assumes there are no optional chain parents of subExpression. The
   * single-argument version of this method takes care of that and should be the only caller of this
   * method.
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
   *   <li>expressionRoot: The top-level node, before which any extracted expressions should be
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
          // and the names assigned-to cannot influence the state before
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
          // Either there were preexisting side-effects, or this node has side-effects.
          state.sideEffects = true;
          // Rewrite the call so "this" is preserved and continue walking up from there.
          rewriteCallExpression(expressionParent, state);
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
      Node parent = nodeWithNonconditionalParent.getParent();
      boolean needResult = !parent.isExprResult();
      extractConditional(nodeWithNonconditionalParent, exprInjectionPoint, needResult);
    }
  }

  /** Rewrite all of the optional chains containing the given subExpression. */
  private void rewriteAllContainingOptionalChains(Node subExpression) {
    final OptionalChainRewriter.Builder optChainRewriterBuilder =
        OptionalChainRewriter.builder(compiler)
            .setTmpVarNameCreator(this::getTempConstantValueName)
            .setScope(this.scope);
    // Rewriting the chains changes the shape of the AST in a way that would interfere
    // with the simple traversal from child to parent done here, so we'll traverse
    // them all first, then rewrite them.
    final ArrayDeque<OptionalChainRewriter> rewriters = new ArrayDeque<>();

    for (Node exprParent = subExpression.getParent();
        !NodeUtil.isStatement(exprParent);
        exprParent = exprParent.getParent()) {
      if (NodeUtil.isEndOfFullOptChain(exprParent)) {
        // We want to rewrite the outermost chain first, so the last one
        // we find is the first one we rewrite.
        rewriters.addFirst(optChainRewriterBuilder.build(exprParent));
      } else if (exprParent.isCall()) {
        // It is possible to make a non-optional call against an optional chain callee by applying
        // parentheses like this.
        // `(obj?.method)(arg)`
        // I don't think there's a good reason to do that, since it will cause a runtime exception
        // if the chain is ever undefined, but it is allowed, so we must handle it.
        // Fortunately the OptionalChainRewriter knows how to fix the call so it will still get
        // made with the right `this` value.
        Node callee = exprParent.getFirstChild();
        if (NodeUtil.isOptChainGet(callee)) {
          // By definition callee must end an optional chain, because it is the first child of a
          // non-optional parent.
          // checkState(NodeUtil.isEndOfFullOptChain(callee))
          rewriters.addFirst(optChainRewriterBuilder.build(callee));
        }
      }
    }
    for (OptionalChainRewriter rewriter : rewriters) {
      rewriter.rewrite();
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
    expression.replaceWith(IR.name(resultName).copyTypeFrom(expression));

    // Re-add the expression at the appropriate place.
    Node newExpressionRoot = NodeUtil.newVarNode(resultName, expression);
    newExpressionRoot.getFirstChild().copyTypeFrom(expression);
    newExpressionRoot.insertBefore(injectionPoint);

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
      result = NodeUtil.getEndOfOptChainSegment(result);
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
            astFactory.createSingleVarNameDeclaration(tempNameAssign).srcrefTreeIfMissing(expr);
        tempVarNodeAssign.insertBefore(injectionPoint);

        Node assignLhs = buildResultExpression(first, true, tempNameAssign);
        Node nullNode = astFactory.createNull().srcref(expr);
        cond = astFactory.createNe(assignLhs, nullNode).srcref(expr);
        trueExpr.addChildToFront(
            astFactory.exprResult(
                buildResultExpression(
                    astFactory.createName(tempNameAssign, first.getJSType()).srcref(expr),
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
    ifNode.srcrefIfMissing(expr);

    if (needResult) {
      Node tempVarNode =
          astFactory.createSingleVarNameDeclaration(tempName).srcrefTreeIfMissing(expr);
      tempVarNode.insertBefore(injectionPoint);
      ifNode.insertAfter(tempVarNode);

      // Replace the expression with the temporary name.
      Node replacementValueNode = IR.name(tempName).copyTypeFrom(expr);
      expr.replaceWith(replacementValueNode);
    } else {
      // Only conditionals that are the direct child of an expression statement
      // don't need results, for those simply replace the expression statement.
      checkArgument(parent.isExprResult());
      parent.replaceWith(ifNode);
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
      return IR.assign(IR.name(tempName).copyTypeFrom(expr), expr)
          .copyTypeFrom(expr)
          .srcrefTree(expr);
    } else {
      return expr;
    }
  }

  private boolean isConstantNameNode(Node n) {
    // Non-constant names values may have been changed.
    return n.isName()
        && (NodeUtil.isConstantVar(n, scope) || knownConstantFunctions.contains(n.getString()));
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
      for (Node n = expr.getFirstChild(); n != null; n = n.getNext()) {
        if (!n.isStringLit() && !isConstantNameNode(n)) {
          Node extractedNode = extractExpression(n, injectionPoint);
          if (firstExtractedNode == null) {
            firstExtractedNode = extractedNode;
          }
        }
      }
    }

    // The temp is known to be constant.
    String tempName = getTempConstantValueName();
    Node replacementValueNode = IR.name(tempName).copyTypeFrom(expr).srcref(expr);

    final Node tempNameValue;

    // If it is ASSIGN_XXX, keep the assignment in place and extract the
    // original value of the LHS operand.
    if (isLhsOfAssignOp) {
      checkState(expr.isName() || NodeUtil.isNormalGet(expr), expr);
      // Transform "x += 2" into "x = temp + 2"
      Node opNode =
          new Node(NodeUtil.getOpFromAssignmentOp(parent))
              .copyTypeFrom(parent)
              .srcrefIfMissing(parent);

      Node rightOperand = parent.getLastChild();

      parent.setToken(Token.ASSIGN);
      rightOperand.replaceWith(opNode);
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
          tempNameValue = astFactory.createArraylit(expr).srcref(expr.getOnlyChild());
          break;
        case OBJECTLIT:
          tempNameValue = astFactory.createObjectLit(expr).srcref(expr.getOnlyChild());
          break;
        default:
          throw new IllegalStateException("Unexpected parent of SPREAD:" + parent.toStringTree());
      }
    } else {
      // Replace the expression with the temporary name.
      expr.replaceWith(replacementValueNode);

      // Keep the original node so that CALL expressions can still be found
      // and inlined properly.
      tempNameValue = expr;
    }

    // Re-add the expression in the declaration of the temporary name.
    Node tempVarNode = NodeUtil.newVarNode(tempName, tempNameValue);
    tempVarNode.getFirstChild().copyTypeFrom(tempNameValue);
    tempVarNode.getFirstChild().setInferredConstantVar(true);
    Scope containingHoistScope = scope.getClosestHoistScope();
    containingHoistScope.declare(tempName, tempVarNode.getFirstChild(), /* input= */ null);

    tempVarNode.insertBefore(injectionPoint);

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
  private void rewriteCallExpression(Node call, DecompositionState state) {
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
    final Node origThisValue = getExprNode.getFirstChild();
    final Node functionNameNode = getVarNode.getFirstChild().cloneNode();
    final Node receiverNode;

    if (origThisValue.isThis()) {
      // No need to create a variable for `this`, just clone it.
      receiverNode = origThisValue.cloneNode();
    } else if (origThisValue.isSuper()) {
      // Original callee was like `super.prop(args)`.
      // The correct way to call the value `super.prop` from a temporary variable is
      // `tmpVar.call(this, args)`, so just create a `this` here.
      receiverNode = astFactory.createThis(origThisValue.getJSType()).srcref(origThisValue);
    } else {
      final Node thisVarNode = extractExpression(origThisValue, state.extractBeforeStatement);
      state.extractBeforeStatement = thisVarNode;
      receiverNode = thisVarNode.getFirstChild().cloneNode();
    }

    // CALL
    //   GETPROP "call"
    //     functionName
    //   thisName
    //   original-parameter1
    //   original-parameter2
    //   ...

    // Reuse the existing CALL node instead of creating a new one to avoid breaking InlineFunction's
    // bookkeeping. See b/124253050.
    call.removeFirstChild();
    call.addChildToFront(receiverNode);
    call.addChildToFront(
        IR.getprop(functionNameNode, "call").setJSType(fnCallType).srcrefTreeIfMissing(call));
    call.putBooleanProp(Node.FREE_CALL, false);
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
    return tempNamePrefix
        + "_const"
        + ContextualRenamer.UNIQUE_ID_SEPARATOR
        + safeNameIdSupplier.get();
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
   *     <li>{@code MOVABLE} because the final value of `1` cannot be influenced by `x()`.
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

    if (NodeUtil.isOptChainNode(subExpression) && !NodeUtil.isEndOfFullOptChain(subExpression)) {
      // e.g `sub?.expression.rest?.of.expression`
      // It is always necessary to decompose the prefix of an optional chain.
      requiresDecomposition = true;
    }
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
          // and the names assigned-to cannot influence the state before
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

          Node first = parent.getFirstChild();
          if (requiresDecomposition && parent.isCall() && NodeUtil.isNormalGet(first)) {
            return DecompositionType.DECOMPOSABLE;
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
   * assignment is unaffected by side effect of "foo()" and the names assigned-to cannot influence
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
   * We must not decompose this method qname.
   *
   * <p>See usage location for more information.
   */
  private static final QualifiedName WINDOW_LOCATION_ASSIGN =
      QualifiedName.of("window.location.assign");

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
      // an infinite number of times. Also assume that "fn.call" is safe when "fn" is a known
      // constant function, as decomposing it may mess up InlineFunction's bookkeeping if it is
      // attempting to inline "fn".
      Node parent = tree.getParent();
      if (NodeUtil.isObjectCallMethod(parent, "call")
          && tree.isFirstChildOf(parent)
          && (isTempConstantValueName(tree.getFirstChild())
              || knownConstantFunctions.contains(tree.getFirstChild().getQualifiedName()))) {
        return false;
      }

      if (enabledWorkarounds.contains(Workaround.BROKEN_IE11_LOCATION_ASSIGN)
          && WINDOW_LOCATION_ASSIGN.matches(tree)) {
        // IE11 throws an exception if we decompose a call to `window.location.assign`
        // e.g.
        // ```js
        // obj = window.location, method = obj.assign, method.call(obj, url); // exception on IE11
        // ```
        // So we will consider it to be a constant value that doesn't need to be decomposed to
        // protect it against side effects.
        // This is a hack to work around a specific case we've seen in real world code.
        // We know it won't catch some cases, but we would rather miss those than add more
        // sophisticated logic that would also encounter false-positives (e.g. is `x.assign` a
        // reference to `window.location.assign`?).
        //
        // We cannot have canBeSideEffected() (below) do this check for us, because it only accepts
        // simple names as constants.
        return false;
      }
      // This is a superset of "NodeUtil.mayHaveSideEffects".
      return NodeUtil.canBeSideEffected(tree, this.knownConstantFunctions, scope);
    } else {
      // The function called doesn't have side-effects but check to see if there
      // are side-effects that that may affect it.
      return astAnalyzer.mayHaveSideEffects(tree);
    }
  }
}
