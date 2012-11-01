/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;

/**
 * Return statement.  Node type is {@link Token#RETURN}.<p>
 *
 * <pre><i>ReturnStatement</i> :
 *      <b>return</b> [<i>no LineTerminator here</i>] [Expression] ;</pre>
 */
public class ReturnStatement extends AstNode {

    private AstNode returnValue;

    {
        type = Token.RETURN;
    }

    public ReturnStatement() {
    }

    public ReturnStatement(int pos) {
        super(pos);
    }

    public ReturnStatement(int pos, int len) {
        super(pos, len);
    }

    public ReturnStatement(int pos, int len, AstNode returnValue) {
        super(pos, len);
        setReturnValue(returnValue);
    }

    /**
     * Returns return value, {@code null} if return value is void
     */
    public AstNode getReturnValue() {
        return returnValue;
    }

    /**
     * Sets return value expression, and sets its parent to this node.
     * Can be {@code null}.
     */
    public void setReturnValue(AstNode returnValue) {
        this.returnValue = returnValue;
        if (returnValue != null)
            returnValue.setParent(this);
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append("return");
        if (returnValue != null) {
            sb.append(" ");
            sb.append(returnValue.toSource(0));
        }
        sb.append(";\n");
        return sb.toString();
    }

    /**
     * Visits this node, then the return value if specified.
     */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this) && returnValue != null) {
            returnValue.visit(v);
        }
    }
}
