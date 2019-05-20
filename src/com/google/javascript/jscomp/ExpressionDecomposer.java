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
 * Partially or fully decomposes an expression with respect to some sub-expresison. Initially this
 * is intended to expand the locations where inlining can occur, but has other uses as well.
 *
 * <p>For example: `var x = y() + z();` becomes `var a = y(); var b = z(); var x = a + b;`.
 *
 * <p>Decomposing, in this context does not mean full decomposition to "atomic" expressions. While
 * it is possible to iteratively apply docomposition to get statements with at most one side-effect,
 * that isn't the intended purpose of this class. The focus is on decomposing "just enough" to
 * "free" a <em>particular</em> subexpression. For example:
 *
 * <ul>
 *   <li>Given: `return (alert() + alert()) + z();`
 *   <li>Exposing: `z()`
 *   <li>Sufficent decomposition: `var temp = alert() + alert(); return temp + z();`
 * </ul>
 *
 * @author johnlenz@google.com (John Lenz)
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
  private final JSType voidType;
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
    this.voidType = compiler.getTypeRegistry().getNativeType(JSTypeNative.VOID_TYPE);
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
   *   <li>nonconditionalExpr: The node that will be extracted either from expression.
   * </ul>
   *
   * @param expressionRoot The root of the subtree within which to expose {@code subExpression}.
   * @param subExpression A descendent of {@code expressionRoot} to be exposed.
   */
  private void exposeExpression(Node expressionRoot, Node subExpression) {
    Node nonconditionalExpr = findNonconditionalParent(subExpression, expressionRoot);
    // Before extraction, record whether there are side-effect
    boolean hasFollowingSideEffects = astAnalyzer.mayHaveSideEffects(nonconditionalExpr);

    Node exprInjectionPoint = findInjectionPoint(nonconditionalExpr);
    DecompositionState state = new DecompositionState();
    state.sideEffects = hasFollowingSideEffects;
    state.extractBeforeStatement = exprInjectionPoint;

    // Extract expressions in the reverse order of their evaluation. This is roughly, traverse up
    // the AST extracting any preceding expressions that may have side-effects or be side-effected.
    Node lastExposedSubexpression = null;
    Node expressionToExpose = nonconditionalExpr;
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
      } else if (expressionParent.isCall() && NodeUtil.isGet(expressionParent.getFirstChild())) {
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
    if (nonconditionalExpr == subExpression) {
      // Don't extract the call, as that introduces an extra constant VAR
      // that will simply need to be inlined back.  It will be handled as
      // an EXPRESSION call site type.
      // Node extractedCall = extractExpression(decomposition, expressionRoot);
    } else {
      Node parent = nonconditionalExpr.getParent();
      boolean needResult = !parent.isExprResult();
      extractConditional(nonconditionalExpr, exprInjectionPoint, needResult);
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
   * @return "expression" or the node closest to "expression", that does not have a conditional
   *     ancestor.
   */
  private static Node findNonconditionalParent(Node subExpression, Node expressionRoot) {
    Node result = subExpression;

    for (Node child = subExpression, parent = child.getParent();
        parent != expressionRoot;
        child = parent, parent = child.getParent()) {
      if (isConditionalOp(parent) && !child.isFirstChildOf(parent)) {
        // Only the first child is always executed, if the function may never
        // be called, don't inline it.
        result = parent;
      }
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
      // Object spread is assumed pure. We just need to extract the expression being spread.
      if (n.getParent().isObjectLit()) {
        n = n.getFirstChild();
      }
      // Iterable spreads are not expressions, but they can still be extracted using temp variables.
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
   * @param injectionPoint The before which extracted expression, would be injected.
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
    Node trueExpr = IR.block().srcref(expr);
    Node falseExpr = IR.block().srcref(expr);
    switch (expr.getToken()) {
      case HOOK:
        // a = x?y:z --> if (x) {a=y} else {a=z}
        cond = first;
        trueExpr.addChildToFront(
            NodeUtil.newExpr(buildResultExpression(second, needResult, tempName)));
        falseExpr.addChildToFront(
            NodeUtil.newExpr(buildResultExpression(last, needResult, tempName)));
        break;
      case AND:
        // a = x&&y --> if (a=x) {a=y} else {}
        cond = buildResultExpression(first, needResult, tempName);
        trueExpr.addChildToFront(
            NodeUtil.newExpr(buildResultExpression(last, needResult, tempName)));
        break;
      case OR:
        // a = x||y --> if (a=x) {} else {a=y}
        cond = buildResultExpression(first, needResult, tempName);
        falseExpr.addChildToFront(
            NodeUtil.newExpr(buildResultExpression(last, needResult, tempName)));
        break;
      default:
        // With a valid tree we should never get here.
        throw new IllegalStateException("Unexpected expression: " + expr);
    }

    Node ifNode;
    if (falseExpr.hasChildren()) {
      ifNode = IR.ifNode(cond, trueExpr, falseExpr);
    } else {
      ifNode = IR.ifNode(cond, trueExpr);
    }
    ifNode.useSourceInfoIfMissingFrom(expr);

    if (needResult) {
      Node tempVarNode =
          NodeUtil.newVarNode(tempName, null).useSourceInfoIfMissingFromForTree(expr);
      tempVarNode.getFirstChild().setJSType(voidType);
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
    if (isLhsOfAssignOp && NodeUtil.isGet(expr)) {
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
      checkState(expr.isName() || NodeUtil.isGet(expr), expr);
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
      // directly assigned to new variables. Instead we wrap them in an array-literal.
      //
      // We make sure to do `var tmp = [...fn()];` rather than `var tmp = fn()` because the
      // execution of a spread on an arbitrary iterable can both have side-effects and be
      // side-effected. However, once done we are then sure that spreading `tmp` is isolated.
      switch (parent.getToken()) {
        case ARRAYLIT:
        case CALL:
        case NEW:
          // Replace the expression with the spread for the temporary name.
          Node spreadCopy = expr.cloneNode();
          spreadCopy.addChildToBack(replacementValueNode);
          expr.replaceWith(spreadCopy);
          // Move the original node into an array-literal so that it's in a legal context.
          tempNameValue = astFactory.createArraylit(expr).useSourceInfoFrom(expr.getOnlyChild());
          break;
        case OBJECTLIT:
          // Object-spread is assumed to be side-effectless by the compiler. Therefore, it should
          // never be processed here.
          // Fall through.
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

    Node injectionPointParent = injectionPoint.getParent();
    injectionPointParent.addChildBefore(tempVarNode, injectionPoint);

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
    checkArgument(NodeUtil.isGet(first), first);

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
    checkArgument(NodeUtil.isGet(getExprNode), getExprNode);
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

  /** @see {@link #canDecomposeExpression} */
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
          if (requiresDecomposition && parent.isCall() && NodeUtil.isGet(first)) {
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
      switch (tree.getParent().getToken()) {
        case OBJECTLIT:
          // Spreading an object, rather than an iterable, is assumed to be pure. That assesment is
          // based on the compiler assumption that getters are pure. This check say nothing of the
          // expression being spread.
          break;
        case ARRAYLIT:
        case CALL:
        case NEW:
          // When extracted, spreads can't be assigned to a single variable and instead are put into
          // an array-literal. However, that literal must be spread again at the original site. This
          // check is what prevents the original spread from triggering recursion.
          if (isTempConstantValueName(tree.getOnlyChild())) {
            return false;
          }
          break;
        default:
          throw new IllegalStateException(
              "Unexpected parent of SPREAD: " + tree.getParent().toStringTree());
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
}
