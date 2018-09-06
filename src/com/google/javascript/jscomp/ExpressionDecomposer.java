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
 * Methods necessary for partially or full decomposing an expression.  Initially
 * this is intended to expanded the locations were inlining can occur, but has
 * other uses as well.
 *
 * For example:
 *    var x = y() + z();
 *
 * Becomes:
 *    var a = y();
 *    var b = z();
 *    x = a + b;
 *
 * @author johnlenz@google.com (John Lenz)
 */
class ExpressionDecomposer {

  /**
   * @see #canExposeExpression
   */
  enum DecompositionType {
    UNDECOMPOSABLE,
    MOVABLE,
    DECOMPOSABLE
  }

  private final AbstractCompiler compiler;
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
   * Perform any rewriting necessary so that the specified expression
   * is movable. This is a partial expression decomposition.
   * @see #canExposeExpression
   */
  void exposeExpression(Node expression) {
    Node expressionRoot = findExpressionRoot(expression);
    checkNotNull(expressionRoot);
    checkState(NodeUtil.isStatement(expressionRoot), expressionRoot);
    exposeExpression(expressionRoot, expression);
  }

  /**
   * Rewrite the expression such that the sub-expression is in a movable expression statement while
   * maintaining evaluation order.
   *
   * <p>Two types of subexpressions are extracted from the source expression: 1) subexpressions with
   * side-effects. 2) conditional expressions, that contain the call, which are transformed into IF
   * statements.
   *
   * <p>The following terms are used: expressionRoot: The top-level node before which the any
   * extracted expressions should be placed before. nonconditionalExpr: The node that will be
   * extracted either express.
   */
  private void exposeExpression(Node expressionRoot, Node subExpression) {
    Node nonconditionalExpr = findNonconditionalParent(subExpression, expressionRoot);
    // Before extraction, record whether there are side-effect
    boolean hasFollowingSideEffects = NodeUtil.mayHaveSideEffects(nonconditionalExpr, compiler);

    Node exprInjectionPoint = findInjectionPoint(nonconditionalExpr);
    DecompositionState state = new DecompositionState();
    state.sideEffects = hasFollowingSideEffects;
    state.extractBeforeStatement = exprInjectionPoint;

    // Extract expressions in the reverse order of their evaluation.
    for (Node grandchild = null, child = nonconditionalExpr, parent = child.getParent();
        parent != expressionRoot;
        grandchild = child, child = parent, parent = child.getParent()) {
      checkState(!isConditionalOp(parent) || child == parent.getFirstChild());
      if (parent.isAssign()) {
        if (isSafeAssign(parent, state.sideEffects)) {
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
        } else {
          // Alias "next()" in "next().foo"
          Node left = parent.getFirstChild();
          Token type = left.getToken();
          if (left != child) {
            checkState(NodeUtil.isGet(left));
            if (type == Token.GETELEM) {
              decomposeSubExpressions(left.getLastChild(), null, state);
            }
            decomposeSubExpressions(left.getFirstChild(), null, state);
          }
        }
      } else if (parent.isCall() && NodeUtil.isGet(parent.getFirstChild())) {
        Node functionExpression = parent.getFirstChild();
        decomposeSubExpressions(functionExpression.getNext(), child, state);
        // Now handle the call expression
        if (isExpressionTreeUnsafe(functionExpression, state.sideEffects)
            && functionExpression.getFirstChild() != grandchild) {
          checkState(allowMethodCallDecomposing, "Object method calls can not be decomposed.");
          // Either there were preexisting side-effects, or this node has side-effects.
          state.sideEffects = true;

          // Rewrite the call so "this" is preserved.
          Node replacement = rewriteCallExpression(parent, state);
          // Continue from here.
          parent = replacement;
        }
      } else if (parent.isObjectLit()) {
        decomposeObjectLiteralKeys(parent.getFirstChild(), child, state);
      } else {
        decomposeSubExpressions(parent.getFirstChild(), child, state);
      }
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

  // TODO(johnlenz): This is not currently used by the function inliner,
  // as moving the call out of the expression before the actual function call
  // causes additional variables to be introduced.  As the variable
  // inliner is improved, this might be a viable option.
  /**
   * Extract the specified expression from its parent expression.
   *
   * @see #canExposeExpression
   */
  void moveExpression(Node expression) {
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
   * @return "expression" or the node closest to "expression", that does not
   * have a conditional ancestor.
   */
  private static Node findNonconditionalParent(Node subExpression, Node expressionRoot) {
     Node result = subExpression;

     for (Node child = subExpression, parent = child.getParent();
          parent != expressionRoot;
          child = parent, parent = child.getParent()) {
       if (isConditionalOp(parent)) {
         // Only the first child is always executed, if the function may never
         // be called, don't inline it.
         if (child != parent.getFirstChild()) {
           result = parent;
         }
       }
     }

     return result;
  }

  /**
   * A simple class to track two things:
   *   - whether side effects have been seen.
   *   - the last statement inserted
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
   * Decompose an object literal.
   * @param key The object literal key.
   * @param stopNode A node after which to stop iterating.
   */
  private void decomposeObjectLiteralKeys(Node key, Node stopNode, DecompositionState state) {
    if (key == null || key == stopNode) {
      return;
    }
    decomposeObjectLiteralKeys(key.getNext(), stopNode, state);
    decomposeSubExpressions(key.getFirstChild(), stopNode, state);
  }

  /**
   * @param n The node with which to start iterating.
   * @param stopNode A node after which to stop iterating.
   */
  private void decomposeSubExpressions(Node n, Node stopNode, DecompositionState state) {
    if (n == null || n == stopNode) {
      return;
    }

    // Never try to decompose an object literal key.
    checkState(!NodeUtil.isObjectLitKey(n), n);

    // Decompose the children in reverse evaluation order.  This simplifies
    // determining if the any of the children following have side-effects.
    // If they do we need to be more aggressive about removing values
    // from the expression.
    decomposeSubExpressions(n.getNext(), stopNode, state);

    // Now this node.

    if (n.isTemplateLitSub()) {
      // A template literal substitution expression like ${f()} is represented in the AST as
      //   TEMPLATELIT_SUB
      //     CALL
      //       NAME f
      // The TEMPLATELIT_SUB node is not actually an expression and can't be extracted, but we may
      // need to extract the expression inside of it.
      n = n.getFirstChild();
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
   *
   * @param expr The conditional expression to extract.
   * @param injectionPoint The before which extracted expression, would be
   *     injected.
   * @param needResult  Whether the result of the expression is required.
   * @return The node that contains the logic of the expression after
   *     extraction.
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
        trueExpr.addChildToFront(NodeUtil.newExpr(
            buildResultExpression(second, needResult, tempName)));
        falseExpr.addChildToFront(NodeUtil.newExpr(
            buildResultExpression(last, needResult, tempName)));
        break;
      case AND:
        // a = x&&y --> if (a=x) {a=y} else {}
        cond = buildResultExpression(first, needResult, tempName);
        trueExpr.addChildToFront(NodeUtil.newExpr(
            buildResultExpression(last, needResult, tempName)));
        break;
      case OR:
        // a = x||y --> if (a=x) {} else {a=y}
        cond = buildResultExpression(first, needResult, tempName);
        falseExpr.addChildToFront(NodeUtil.newExpr(
            buildResultExpression(last, needResult, tempName)));
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
      Node tempVarNode = NodeUtil.newVarNode(tempName, null)
          .useSourceInfoIfMissingFromForTree(expr);
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
   * If the result of the expression is needed, then:
   *    ASSIGN
   *       tempName
   *       expr
   * otherwise, simply:
   *       expr
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

    boolean isLhsOfAssignOp = NodeUtil.isAssignmentOp(parent)
        && !parent.isAssign()
        && parent.getFirstChild() == expr;

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
    Node replacementValueNode = IR.name(tempName).srcref(expr);
    replacementValueNode.setJSType(expr.getJSType());

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
   *   a.b(c);
   * becomes:
   *   var temp1 = a;
   *   var temp0 = temp1.b;
   *   temp0.call(temp1,c);
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
    Node thisVarNode = extractExpression(
        getExprNode.getFirstChild(), state.extractBeforeStatement);
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
            .useSourceInfoIfMissingFromForTree(call);
    newCall.setJSType(call.getJSType());

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

  /**
   * Allow the temp name to be overridden to make tests more readable.
   */
  @VisibleForTesting
  public void setTempNamePrefix(String prefix) {
    this.tempNamePrefix = prefix;
  }

  /** Create a unique temp name. */
  private String getTempValueName() {
    return tempNamePrefix + ContextualRenamer.UNIQUE_ID_SEPARATOR
        + safeNameIdSupplier.get();
  }

  /**
   * Allow the temp name to be overridden to make tests more readable.
   */
  @VisibleForTesting
  public void setResultNamePrefix(String prefix) {
    this.resultNamePrefix = prefix;
  }

  /**
   * Create a unique name for call results.
   */
  private String getResultValueName() {
    return resultNamePrefix
        + ContextualRenamer.UNIQUE_ID_SEPARATOR + safeNameIdSupplier.get();
  }

  /** Create a constant unique temp name. */
  private String getTempConstantValueName() {
    String name = tempNamePrefix + "_const"
        + ContextualRenamer.UNIQUE_ID_SEPARATOR
        + safeNameIdSupplier.get();
    this.knownConstants.add(name);
    return name;
  }

  private boolean isTempConstantValueName(String s) {
    return s.startsWith(tempNamePrefix + "_const" + ContextualRenamer.UNIQUE_ID_SEPARATOR);
  }

  /**
   * @return For the subExpression, find the nearest statement Node before which
   * it can be inlined.  Null if no such location can be found.
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

  /**
   * @return Whether the node is a conditional op.
   */
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
   * @return The statement containing the expression or null if the subExpression
   *     is not contain in a Node where inlining is known to be possible.
   *     For example, a WHILE node condition expression.
   */
  @Nullable
  static Node findExpressionRoot(Node subExpression) {
    Node child = subExpression;
    for (Node parent : child.getAncestors()) {
      Token parentType = parent.getToken();
      switch (parentType) {
        // Supported expression roots:
        // SWITCH and IF can have multiple children, but the CASE, DEFAULT,
        // or BLOCK will be encountered first for any of the children other
        // than the condition.
        case EXPR_RESULT:
        case IF:
        case SWITCH:
        case RETURN:
        case THROW:
          Preconditions.checkState(child == parent.getFirstChild());
          return parent;
        case VAR:
        case CONST:
        case LET:
          Preconditions.checkState(child == parent.getFirstChild());
          if (parent.getParent().isVanillaFor()
              && parent == parent.getParent().getFirstChild()) {
            return parent.getParent();
          } else {
            return parent;
          }
        // Any of these indicate an unsupported expression:
        case FOR:
          if (child == parent.getFirstChild()) {
            return parent;
          }
          // fall through
        case FOR_IN:
        case FOR_OF:
        case SCRIPT:
        case BLOCK:
        case LABEL:
        case CASE:
        case DEFAULT_CASE:
        case PARAM_LIST:
          return null;
        default:
          break;
      }
      child = parent;
    }

    throw new IllegalStateException("Unexpected AST structure.");
  }

  /**
   * Determine whether a expression is movable, or can be be made movable after
   * decomposing the containing expression.
   *
   * A subexpression is MOVABLE if it can be replaced with a temporary holding
   * its results and moved to immediately before the root of the expression.
   * There are three conditions that must be met for this to occur:
   * 1) There must be a location to inject a statement for the expression.  For
   * example, this condition can not be met if the expression is a loop
   * condition or CASE condition.
   * 2) If the expression can be affected by side-effects, there can not be a
   * side-effect between original location and the expression root.
   * 3) If the expression has side-effects, there can not be any other
   * expression that can be affected between the original location and the
   * expression root.
   *
   * An expression is DECOMPOSABLE if it can be rewritten so that an
   * subExpression is MOVABLE.
   *
   * An expression is decomposed by moving any other sub-expressions that
   * preventing an subExpression from being MOVABLE.
   *
   * @return Whether This is a call that can be moved to an new point in the
   * AST to allow it to be inlined.
   */
  DecompositionType canExposeExpression(Node subExpression) {
    Node expressionRoot = findExpressionRoot(subExpression);
    if (expressionRoot != null) {
      return isSubexpressionMovable(expressionRoot, subExpression);
    }
    return DecompositionType.UNDECOMPOSABLE;
  }

  /**
   * Walk the AST from the call site to the expression root and verify that
   * the portions of the expression that are evaluated before the call:
   * 1) are unaffected by the side-effects, if any, of the call.
   * 2) have no side-effects, that may influence the call.
   *
   * For example, if x has side-effects:
   *   a = 1 + x();
   * the call to x can be moved because the final value of "a" can not be
   * influenced by x(), but in:
   *   a = b + x();
   * the call to x cannot be moved because the value of "b" may be modified
   * by the call to x.
   *
   * If x is without side-effects in:
   *   a = b + x();
   * the call to x can be moved, but in:
   *   a = (b.foo = c) + x();
   * the call to x can not be moved because the value of b.foo may be referenced
   * by x().  Note: this is true even if b is a local variable; the object that
   * b refers to may have a global alias.
   *
   * @return UNDECOMPOSABLE if the expression cannot be moved, DECOMPOSABLE if
   * decomposition is required before the expression can be moved, otherwise MOVABLE.
   */
  private DecompositionType isSubexpressionMovable(Node expressionRoot, Node subExpression) {
    boolean requiresDecomposition = false;
    boolean seenSideEffects = NodeUtil.mayHaveSideEffects(subExpression, compiler);

    Node child = subExpression;
    for (Node parent : child.getAncestors()) {
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


  private enum EvaluationDirection {FORWARD, REVERSE}

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
   * It is always safe to inline "foo()" for expressions such as
   *    "a = b = c = foo();"
   * As the assignment is unaffected by side effect of "foo()"
   * and the names assigned-to can not influence the state before
   * the call to foo.
   *
   * It is also safe in cases like where the object is constant:
   *    CONST_NAME.a = foo()
   *    CONST_NAME[CONST_VALUE] = foo();
   *
   * This is not true of more complex LHS values, such as
   *     a.x = foo();
   *     next().x = foo();
   * in these cases the checks below are necessary.
   *
   * @param seenSideEffects If true, check to see if node-tree maybe affected by
   * side-effects, otherwise if the tree has side-effects. @see isExpressionTreeUnsafe
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
   * @return Whether anything in the expression tree prevents a call from
   * being moved.
   */
  private boolean isExpressionTreeUnsafe(Node n, boolean followingSideEffectsExist) {
    if (followingSideEffectsExist) {
      // If the call to be inlined has side-effects, check to see if this
      // expression tree can be affected by any side-effects.

      // Assume that "tmp1.call(...)" is safe (where tmp1 is a const temp variable created by
      // ExpressionDecomposer) otherwise we end up trying to decompose the same tree
      // an infinite number of times.
      Node parent = n.getParent();
      if (NodeUtil.isObjectCallMethod(parent, "call")
          && n == parent.getFirstChild()
          && n.getFirstChild().isName()
          && isTempConstantValueName(n.getFirstChild().getString())) {
        return false;
      }

      // This is a superset of "NodeUtil.mayHaveSideEffects".
      return NodeUtil.canBeSideEffected(n, this.knownConstants, scope);
    } else {
      // The function called doesn't have side-effects but check to see if there
      // are side-effects that that may affect it.
      return NodeUtil.mayHaveSideEffects(n, compiler);
    }
  }
}
