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
 * AST node representing the ternary operator.  Node type is
 * {@link Token#HOOK}.
 *
 * <pre><i>ConditionalExpression</i> :
 *        LogicalORExpression
 *        LogicalORExpression ? AssignmentExpression
 *                            : AssignmentExpression</pre>
 *
 * <i>ConditionalExpressionNoIn</i> :
 *        LogicalORExpressionNoIn
 *        LogicalORExpressionNoIn ? AssignmentExpression
 *                                : AssignmentExpressionNoIn</pre>
 */
public class ConditionalExpression extends AstNode {

    private AstNode testExpression;
    private AstNode trueExpression;
    private AstNode falseExpression;
    private int questionMarkPosition = -1;
    private int colonPosition = -1;

    {
        type = Token.HOOK;
    }

    public ConditionalExpression() {
    }

    public ConditionalExpression(int pos) {
        super(pos);
    }

    public ConditionalExpression(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns test expression
     */
    public AstNode getTestExpression() {
        return testExpression;
    }

    /**
     * Sets test expression, and sets its parent.
     * @param testExpression test expression
     * @throws IllegalArgumentException if testExpression is {@code null}
     */
    public void setTestExpression(AstNode testExpression) {
        assertNotNull(testExpression);
        this.testExpression = testExpression;
        testExpression.setParent(this);
    }

    /**
     * Returns expression to evaluate if test is true
     */
    public AstNode getTrueExpression() {
        return trueExpression;
    }

    /**
     * Sets expression to evaluate if test is true, and
     * sets its parent to this node.
     * @param trueExpression expression to evaluate if test is true
     * @throws IllegalArgumentException if expression is {@code null}
     */
    public void setTrueExpression(AstNode trueExpression) {
        assertNotNull(trueExpression);
        this.trueExpression = trueExpression;
        trueExpression.setParent(this);
    }

    /**
     * Returns expression to evaluate if test is false
     */
    public AstNode getFalseExpression() {
        return falseExpression;
    }

    /**
     * Sets expression to evaluate if test is false, and sets its
     * parent to this node.
     * @param falseExpression expression to evaluate if test is false
     * @throws IllegalArgumentException if {@code falseExpression}
     * is {@code null}
     */
    public void setFalseExpression(AstNode falseExpression) {
        assertNotNull(falseExpression);
        this.falseExpression = falseExpression;
        falseExpression.setParent(this);
    }

    /**
     * Returns position of ? token
     */
    public int getQuestionMarkPosition() {
        return questionMarkPosition;
    }

    /**
     * Sets position of ? token
     * @param questionMarkPosition position of ? token
     */
    public void setQuestionMarkPosition(int questionMarkPosition) {
        this.questionMarkPosition = questionMarkPosition;
    }

    /**
     * Returns position of : token
     */
    public int getColonPosition() {
        return colonPosition;
    }

    /**
     * Sets position of : token
     * @param colonPosition position of : token
     */
    public void setColonPosition(int colonPosition) {
        this.colonPosition = colonPosition;
    }

    @Override
    public boolean hasSideEffects() {
        if (testExpression == null
            || trueExpression == null
            || falseExpression == null) codeBug();
        return trueExpression.hasSideEffects()
               && falseExpression.hasSideEffects();
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append(testExpression.toSource(depth));
        sb.append(" ? ");
        sb.append(trueExpression.toSource(0));
        sb.append(" : ");
        sb.append(falseExpression.toSource(0));
        return sb.toString();
    }

    /**
     * Visits this node, then the test-expression, the true-expression,
     * and the false-expression.
     */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            testExpression.visit(v);
            trueExpression.visit(v);
            falseExpression.visit(v);
        }
    }
}
