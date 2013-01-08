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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.javascript.jscomp.MakeDeclaredNamesUnique.ContextualRenamer;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Set;

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

  public ExpressionDecomposer(
      AbstractCompiler compiler,
      Supplier<String> safeNameIdSupplier,
      Set<String> constNames) {
    Preconditions.checkNotNull(compiler);
    Preconditions.checkNotNull(safeNameIdSupplier);
    Preconditions.checkNotNull(constNames);
    this.compiler = compiler;
    this.safeNameIdSupplier = safeNameIdSupplier;
    this.knownConstants = constNames;
  }

  // An arbitrary limit to prevent catch infinite recursion.
  private static final int MAX_INTERATIONS = 100;

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
      if (i > MAX_INTERATIONS) {
        throw new IllegalStateException(
            "DecomposeExpression depth exceeded on :\n" +
            expression.toStringTree());
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
    Preconditions.checkState(expressionRoot != null);
    exposeExpression(expressionRoot, expression);
    compiler.reportCodeChange();
  }

  // TODO(johnlenz): This is not currently used by the function inliner,
  // as moving the call out of the expression before the actual function
  // results in additional variables being introduced.  As the variable
  // inliner is improved, this might be a viable option.
  /**
   * Extract the specified expression from its parent expression.
   * @see #canExposeExpression
   */
  void moveExpression(Node expression) {
    String resultName = getResultValueName();
    Node injectionPoint = findInjectionPoint(expression);
    Preconditions.checkNotNull(injectionPoint);
    Node injectionPointParent = injectionPoint.getParent();
    Preconditions.checkNotNull(injectionPointParent);
    Preconditions.checkState(NodeUtil.isStatementBlock(injectionPointParent));

    // Replace the expression with a reference to the new name.
    Node expressionParent = expression.getParent();
    expressionParent.replaceChild(
        expression, IR.name(resultName));

    // Re-add the expression at the appropriate place.
    Node newExpressionRoot = NodeUtil.newVarNode(resultName, expression);
    injectionPointParent.addChildBefore(newExpressionRoot, injectionPoint);
    compiler.reportCodeChange();
  }

  /**
   * Rewrite the expression such that the sub-expression is in a movable
   * expression statement while maintaining evaluation order.
   *
   * Two types of subexpressions are extracted from the source expression:
   * 1) subexpressions with side-effects.
   * 2) conditional expressions, that contain the call, which are transformed
   * into IF statements.
   *
   * The following terms are used:
   *    expressionRoot: The top-level node before which the any extracted
   *                    expressions should be placed before.
   *    nonconditionalExpr: The node that will be extracted either expres.
   *
   */
  private void exposeExpression(Node expressionRoot, Node subExpression) {
    Node nonconditionalExpr = findNonconditionalParent(
        subExpression, expressionRoot);
    // Before extraction, record whether there are side-effect
    boolean hasFollowingSideEffects = NodeUtil.mayHaveSideEffects(
        nonconditionalExpr, compiler);

    Node exprInjectionPoint = findInjectionPoint(nonconditionalExpr);
    DecompositionState state = new DecompositionState();
    state.sideEffects = hasFollowingSideEffects;
    state.extractBeforeStatement = exprInjectionPoint;

    // Extract expressions in the reverse order of their evaluation.
    for (Node grandchild = null,
            child = nonconditionalExpr,
            parent = child.getParent();
         parent != expressionRoot;
         grandchild = child,
             child = parent,
             parent = child.getParent()) {
      int parentType = parent.getType();
      Preconditions.checkState(
          !isConditionalOp(parent) || child == parent.getFirstChild());
      if (parentType == Token.ASSIGN) {
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
            int type = left.getType();
            if (left != child) {
              Preconditions.checkState(NodeUtil.isGet(left));
              if (type == Token.GETELEM) {
                decomposeSubExpressions(left.getLastChild(), null, state);
              }
              decomposeSubExpressions(left.getFirstChild(), null, state);
            }
          }
      } else if (parentType == Token.CALL
          && NodeUtil.isGet(parent.getFirstChild())) {
        Node functionExpression = parent.getFirstChild();
        decomposeSubExpressions(functionExpression.getNext(), child, state);
        // Now handle the call expression
        if (isExpressionTreeUnsafe(functionExpression, state.sideEffects)
            && functionExpression.getFirstChild() != grandchild) {
          // TODO(johnlenz): In Internet Explorer, non-JavaScript objects such
          // as DOM objects can not be decomposed.
          Preconditions.checkState(allowObjectCallDecomposing(),
              "Object method calls can not be decomposed.");
          // Either there were preexisting side-effects, or this node has
          // side-effects.
          state.sideEffects = true;

          // Rewrite the call so "this" is preserved.
          Node replacement = rewriteCallExpression(parent, state);
          // Continue from here.
          parent = replacement;
        }
      } else if (parentType == Token.OBJECTLIT) {
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

  private static boolean allowObjectCallDecomposing() {
    return false;
  }

  /**
   * @return Whether the node may represent an external method.
   */
  private boolean maybeExternMethod(Node node) {
    // TODO(johnlenz): Provide some mechanism for determining this.
    return true;
  }

  /**
   * @return "expression" or the node closest to "expression", that does not
   * have a conditional ancestor.
   */
  private static Node findNonconditionalParent(
      Node subExpression, Node expressionRoot) {
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
  }

  /**
   * Decompose an object literal.
   * @param key The object literal key.
   * @param stopNode A node after which to stop iterating.
   */
  private void decomposeObjectLiteralKeys(
      Node key, Node stopNode, DecompositionState state) {
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
  private void decomposeSubExpressions(
      Node n, Node stopNode, DecompositionState state) {
    if (n == null || n == stopNode) {
      return;
    }

    // Never try to decompose an object literal key.
    Preconditions.checkState(!NodeUtil.isObjectLitKey(n));

    // Decompose the children in reverse evaluation order.  This simplifies
    // determining if the any of the children following have side-effects.
    // If they do we need to be more aggressive about removing values
    // from the expression.
    decomposeSubExpressions(
        n.getNext(), stopNode, state);

    // Now this node.
    // TODO(johnlenz): Move "safety" code to a shared class.
    if (isExpressionTreeUnsafe(n, state.sideEffects)) {
      // Either there were preexisting side-effects, or this node has
      // side-effects.
      state.sideEffects = true;
      state.extractBeforeStatement = extractExpression(
          n, state.extractBeforeStatement);
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
  private Node extractConditional(
      Node expr, Node injectionPoint, boolean needResult) {
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
    switch (expr.getType()) {
      case Token.HOOK:
        // a = x?y:z --> if (x) {a=y} else {a=z}
        cond = first;
        trueExpr.addChildToFront(NodeUtil.newExpr(
            buildResultExpression(second, needResult, tempName)));
        falseExpr.addChildToFront(NodeUtil.newExpr(
            buildResultExpression(last, needResult, tempName)));
        break;
      case Token.AND:
        // a = x&&y --> if (a=x) {a=y} else {}
        cond = buildResultExpression(first, needResult, tempName);
        trueExpr.addChildToFront(NodeUtil.newExpr(
            buildResultExpression(last, needResult, tempName)));
        break;
      case Token.OR:
        // a = x||y --> if (a=x) {} else {a=y}
        cond = buildResultExpression(first, needResult, tempName);
        falseExpr.addChildToFront(NodeUtil.newExpr(
            buildResultExpression(last, needResult, tempName)));
        break;
      default:
        // With a valid tree we should never get here.
        throw new IllegalStateException("Unexpected.");
    }

    Node ifNode;
    if (falseExpr.hasChildren()) {
      ifNode = IR.ifNode(cond, trueExpr, falseExpr);
    } else {
      ifNode = IR.ifNode(cond, trueExpr);
    }
    ifNode.copyInformationFrom(expr);

    if (needResult) {
      Node tempVarNode = NodeUtil.newVarNode(tempName, null)
          .copyInformationFromForTree(expr);
      Node injectionPointParent = injectionPoint.getParent();
      injectionPointParent.addChildBefore(tempVarNode, injectionPoint);
      injectionPointParent.addChildAfter(ifNode, tempVarNode);

      // Replace the expression with the temporary name.
      Node replacementValueNode = IR.name(tempName);
      parent.replaceChild(expr, replacementValueNode);
    } else {
      // Only conditionals that are the direct child of an expression statement
      // don't need results, for those simply replace the expression statement.
      Preconditions.checkArgument(parent.isExprResult());
      Node gramps = parent.getParent();
      gramps.replaceChild(parent, ifNode);
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
  private static Node buildResultExpression(
      Node expr, boolean needResult, String tempName) {
    if (needResult) {
      return IR.assign(
          IR.name(tempName),
          expr).srcrefTree(expr);
    } else {
      return expr;
    }
  }

  private boolean isConstantName(Node n, Set<String> knownConstants) {
    // Non-constant names values may have been changed.
    return n.isName() && (NodeUtil.isConstantName(n)
        || knownConstants.contains(n.getString()));
  }

  /**
   * @param expr The expression to extract.
   * @param injectionPoint The node before which to added the extracted
   *     expression.
   * @return The extract statement node.
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
        if (!n.isString() && !isConstantName(n, knownConstants)) {
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

    Node tempNameValue;

    // If it is ASSIGN_XXX, keep the assignment in place and extract the
    // original value of the LHS operand.
    if (isLhsOfAssignOp) {
      Preconditions.checkState(expr.isName() || NodeUtil.isGet(expr));
      // Transform "x += 2" into "x = temp + 2"
      Node opNode = new Node(NodeUtil.getOpFromAssignmentOp(parent))
          .copyInformationFrom(parent);

      Node rightOperand = parent.getLastChild();

      parent.setType(Token.ASSIGN);
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

    Node injectionPointParent = injectionPoint.getParent();
    injectionPointParent.addChildBefore(tempVarNode, injectionPoint);

    if (firstExtractedNode == null) {
      firstExtractedNode = tempVarNode;
    }
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
    Preconditions.checkArgument(call.isCall());
    Node first = call.getFirstChild();
    Preconditions.checkArgument(NodeUtil.isGet(first));

    // Extracts the expression representing the function to call. For example:
    //   "a['b'].c" from "a['b'].c()"
    Node getVarNode = extractExpression(
        first, state.extractBeforeStatement);
    state.extractBeforeStatement = getVarNode;

    // Extracts the object reference to be used as "this". For example:
    //   "a['b']" from "a['b'].c"
    Node getExprNode = getVarNode.getFirstChild().getFirstChild();
    Preconditions.checkArgument(NodeUtil.isGet(getExprNode));
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
    Node newCall = IR.call(
        IR.getprop(
            functionNameNode.cloneNode(),
            IR.string("call")),
        thisNameNode.cloneNode()).srcref(call);

    // Throw away the call name
    call.removeFirstChild();
    if (call.hasChildren()) {
      // Add the call parameters to the new call.
      newCall.addChildrenToBack(call.removeChildren());
    }

    // Replace the call.
    Node callParent = call.getParent();
    callParent.replaceChild(call, newCall);

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

  /**
   * Create a unique temp name.
   */
  private String getTempValueName(){
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

  /**
   * Create a constant unique temp name.
   */
  private String getTempConstantValueName(){
    String name = tempNamePrefix + "_const"
        + ContextualRenamer.UNIQUE_ID_SEPARATOR
        + safeNameIdSupplier.get();
    this.knownConstants.add(name);
    return name;
  }

  /**
   * @return For the subExpression, find the nearest statement Node before which
   * it can be inlined.  Null if no such location can be found.
   */
  static Node findInjectionPoint(Node subExpression) {
    Node expressionRoot = findExpressionRoot(subExpression);
    Preconditions.checkNotNull(expressionRoot);

    Node injectionPoint = expressionRoot;

    Node parent = injectionPoint.getParent();
    while (parent.isLabel()) {
      injectionPoint = parent;
      parent = injectionPoint.getParent();
    }

    Preconditions.checkState(
        NodeUtil.isStatementBlock(injectionPoint.getParent()));
    return injectionPoint;
  }

  /**
   * @return Whether the node is a conditional op.
   */
  private static boolean isConditionalOp(Node n) {
    switch(n.getType()) {
      case Token.HOOK:
      case Token.AND:
      case Token.OR:
        return true;
      default:
        return false;
    }
  }

  /**
   * @return The statement containing the expression. null if subExpression
   *     is not contain by in by a Node where inlining is known to be possible.
   *     For example, a WHILE node condition expression.
   */
  static Node findExpressionRoot(Node subExpression) {
    Node child = subExpression;
    for (Node parent : child.getAncestors()) {
      int parentType = parent.getType();
      switch (parentType) {
        // Supported expression roots:
        // SWITCH and IF can have multiple children, but the CASE, DEFAULT,
        // or BLOCK will be encountered first for any of the children other
        // than the condition.
        case Token.EXPR_RESULT:
        case Token.IF:
        case Token.SWITCH:
        case Token.RETURN:
        case Token.VAR:
          Preconditions.checkState(child == parent.getFirstChild());
          return parent;
        // Any of these indicate an unsupported expression:
        case Token.SCRIPT:
        case Token.BLOCK:
        case Token.LABEL:
        case Token.CASE:
        case Token.DEFAULT_CASE:
          return null;
      }
      child = parent;
    }

    throw new IllegalStateException("Unexpected AST structure.");
  }

  /**
   * Determine whether a expression is movable, or can be be made movable be
   * decomposing the containing expression.
   *
   * An subExpression is MOVABLE if it can be replaced with a temporary holding
   * its results and moved to immediately before the root of the expression.
   * There are three conditions that must be met for this to occur:
   * 1) There must be a location to inject a statement for the expression.  For
   * example, this condition can not be met if the expression is a loop
   * condition or CASE condition.
   * 2) If the expression can be affect by side-effects, there can not be a
   * side-effect between original location and the expression root.
   * 3) If the expression has side-effects, there can not be any other
   * expression that can be effected between the original location and the
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
   * the portions of the expression that are evaluated before the call are:
   * 1) Unaffected by the the side-effects, if any, of the call.
   * 2) That there are no side-effects, that may influence the call.
   *
   * For example, if x has side-effects:
   *   a = 1 + x();
   * the call to x can be moved because "a" final value of a can not be
   * influenced by x(), but in:
   *   a = b + x();
   * the call to x can not be moved because the value of b may be modified
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
   * @return UNDECOMPOSABLE if the expression can not be moved, DECOMPOSABLE if
   * decomposition is required before the expression can be moved, otherwise
   * MOVABLE.
   */
  private DecompositionType isSubexpressionMovable(
      Node expressionRoot, Node subExpression) {
    boolean requiresDecomposition = false;
    boolean seenSideEffects = NodeUtil.mayHaveSideEffects(
        subExpression, compiler);

    Node child = subExpression;
    for (Node parent : child.getAncestors()) {
      if (parent == expressionRoot) {
        // Done. The walk back to the root of the expression is complete, and
        // nothing was encountered that blocks the call from being moved.
        return requiresDecomposition
            ? DecompositionType.DECOMPOSABLE
            : DecompositionType.MOVABLE;
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
        // NOTE: This depends on the siblings being in the same order as they
        // are evaluated.

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
          for (Node n : parent.children()) {
            if (n == child) {
              // None of the preceding siblings have side-effects.
              // This is OK.
              break;
            }

            if (isExpressionTreeUnsafe(
                n, seenSideEffects)) {
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
          if (requiresDecomposition
              && parent.isCall()
              && NodeUtil.isGet(first)) {
            if (maybeExternMethod(first)) {
              return DecompositionType.UNDECOMPOSABLE;
            } else {
              return DecompositionType.DECOMPOSABLE;
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
   * side-effects, otherwise if the tree has side-effects. @see
   * isExpressionTreeUnsafe
   * @return Whether the assignment is safe from side-effects.
   */
  private boolean isSafeAssign(Node n, boolean seenSideEffects) {
    if (n.isAssign()) {
      Node lhs = n.getFirstChild();
      switch (lhs.getType()) {
        case Token.NAME:
          return true;
        case Token.GETPROP:
          return !isExpressionTreeUnsafe(lhs.getFirstChild(), seenSideEffects);
        case Token.GETELEM:
          return !isExpressionTreeUnsafe(lhs.getFirstChild(), seenSideEffects)
              && !isExpressionTreeUnsafe(lhs.getLastChild(), seenSideEffects);
      }
    }
    return false;
  }

  /**
   * @return Whether anything in the expression tree prevents a call from
   * being moved.
   */
  private boolean isExpressionTreeUnsafe(
      Node n, boolean followingSideEffectsExist) {
    if (followingSideEffectsExist) {
      // If the call to be inlined has side-effects, check to see if this
      // expression tree can be affected by any side-effects.

      // This is a superset of "NodeUtil.mayHaveSideEffects".
      return NodeUtil.canBeSideEffected(n, this.knownConstants);
    } else {
      // The function called doesn't have side-effects but check to see if there
      // are side-effects that that may affect it.
      return NodeUtil.mayHaveSideEffects(n, compiler);
    }
  }
}
