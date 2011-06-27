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
 * C-style for-loop statement.
 * Node type is {@link Token#FOR}.<p>
 *
 * <pre><b>for</b> ( ExpressionNoInopt; Expressionopt ; Expressionopt ) Statement</pre>
 * <pre><b>for</b> ( <b>var</b> VariableDeclarationListNoIn; Expressionopt ; Expressionopt ) Statement</pre>
 */
public class ForLoop extends Loop {

    private AstNode initializer;
    private AstNode condition;
    private AstNode increment;

    {
        type = Token.FOR;
    }

    public ForLoop() {
    }

    public ForLoop(int pos) {
        super(pos);
    }

    public ForLoop(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns loop initializer variable declaration list.
     * This is either a {@link VariableDeclaration}, an
     * {@link Assignment}, or an {@link InfixExpression} of
     * type COMMA that chains multiple variable assignments.
     */
    public AstNode getInitializer() {
        return initializer;
    }

    /**
     * Sets loop initializer expression, and sets its parent
     * to this node.  Virtually any expression can be in the initializer,
     * so no error-checking is done other than a {@code null}-check.
     * @param initializer loop initializer.  Pass an
     * {@link EmptyExpression} if the initializer is not specified.
     * @throws IllegalArgumentException if condition is {@code null}
     */
    public void setInitializer(AstNode initializer) {
        assertNotNull(initializer);
        this.initializer = initializer;
        initializer.setParent(this);
    }

    /**
     * Returns loop condition
     */
    public AstNode getCondition() {
        return condition;
    }

    /**
     * Sets loop condition, and sets its parent to this node.
     * @param condition loop condition.  Pass an {@link EmptyExpression}
     * if the condition is missing.
     * @throws IllegalArgumentException} if condition is {@code null}
     */
    public void setCondition(AstNode condition) {
        assertNotNull(condition);
        this.condition = condition;
        condition.setParent(this);
    }

    /**
     * Returns loop increment expression
     */
    public AstNode getIncrement() {
        return increment;
    }

    /**
     * Sets loop increment expression, and sets its parent to
     * this node.
     * @param increment loop increment expression.  Pass an
     * {@link EmptyExpression} if increment is {@code null}.
     * @throws IllegalArgumentException} if increment is {@code null}
     */
    public void setIncrement(AstNode increment) {
        assertNotNull(increment);
        this.increment = increment;
        increment.setParent(this);
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append("for (");
        sb.append(initializer.toSource(0));
        sb.append("; ");
        sb.append(condition.toSource(0));
        sb.append("; ");
        sb.append(increment.toSource(0));
        sb.append(") ");
        if (body instanceof Block) {
            sb.append(body.toSource(depth).trim()).append("\n");
        } else {
            sb.append("\n").append(body.toSource(depth+1));
        }
        return sb.toString();
    }

    /**
     * Visits this node, the initializer expression, the loop condition
     * expression, the increment expression, and then the loop body.
     */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            initializer.visit(v);
            condition.visit(v);
            increment.visit(v);
            body.visit(v);
        }
    }
}
