/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Steve Yegge
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;

/**
 * AST node representing an expression in a statement context.  The node type is
 * {@link Token#EXPR_VOID} if inside a function, or else
 * {@link Token#EXPR_RESULT} if inside a script.
 */
public class ExpressionStatement extends AstNode {

    private AstNode expr;

    {
        type = Token.EXPR_VOID;
    }

    /**
     * Called by the parser to set node type to EXPR_RESULT
     * if this node is not within a Function.
     */
    public void setHasResult() {
        type = Token.EXPR_RESULT;
    }

    public ExpressionStatement() {
    }

    /**
     * Constructs a new {@code ExpressionStatement} wrapping
     * the specified expression.  Sets this node's position to the
     * position of the wrapped node, and sets the wrapped node's
     * position to zero.  Sets this node's length to the length of
     * the wrapped node.
     * @param expr the wrapped expression
     * @param hasResult {@code true} if this expression has side
     * effects.  If true, sets node type to EXPR_RESULT, else to EXPR_VOID.
     */
    public ExpressionStatement(AstNode expr, boolean hasResult) {
        this(expr);
        if (hasResult) setHasResult();
    }

    /**
     * Constructs a new {@code ExpressionStatement} wrapping
     * the specified expression.  Sets this node's position to the
     * position of the wrapped node, and sets the wrapped node's
     * position to zero.  Sets this node's length to the length of
     * the wrapped node.
     * @param expr the wrapped expression
     */
    public ExpressionStatement(AstNode expr) {
        this(expr.getPosition(), expr.getLength(), expr);
    }

    public ExpressionStatement(int pos, int len) {
        super(pos, len);
    }

    /**
     * Constructs a new {@code ExpressionStatement}
     * @param expr the wrapped {@link AstNode}.
     * The {@code ExpressionStatement}'s bounds are set to those of expr,
     * and expr's parent is set to this node.
     * @throws IllegalArgumentException if {@code expr} is null
     */
    public ExpressionStatement(int pos, int len, AstNode expr) {
        super(pos, len);
        setExpression(expr);
    }

    /**
     * Returns the wrapped expression
     */
    public AstNode getExpression() {
        return expr;
    }

    /**
     * Sets the wrapped expression, and sets its parent to this node.
     * @throws IllegalArgumentException} if expression is {@code null}
     */
    public void setExpression(AstNode expression) {
        assertNotNull(expression);
        expr = expression;
        expression.setParent(this);
        setLineno(expression.getLineno());
    }

    /**
     * Returns true if this node has side effects
     * @throws IllegalStateException if expression has not yet
     * been set.
     */
    @Override
    public boolean hasSideEffects() {
        return type == Token.EXPR_RESULT || expr.hasSideEffects();
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(expr.toSource(depth));
        sb.append(";\n");
        return sb.toString();
    }

    /**
     * Visits this node, then the wrapped statement.
     */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            expr.visit(v);
        }
    }
}
