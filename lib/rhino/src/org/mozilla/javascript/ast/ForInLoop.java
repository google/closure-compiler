/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;

/**
 * For-in or for-each-in statement.  Node type is {@link Token#FOR}.<p>
 *
 * <pre><b>for</b> [<b>each</b>] ( LeftHandSideExpression <b>in</b> Expression ) Statement</pre>
 * <pre><b>for</b> [<b>each</b>] ( <b>var</b> VariableDeclarationNoIn <b>in</b> Expression ) Statement</pre>
 */
public class ForInLoop extends Loop {

    protected AstNode iterator;
    protected AstNode iteratedObject;
    protected int inPosition = -1;
    protected int eachPosition = -1;
    protected boolean isForEach;

    {
        type = Token.FOR;
    }

    public ForInLoop() {
    }

    public ForInLoop(int pos) {
        super(pos);
    }

    public ForInLoop(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns loop iterator expression
     */
    public AstNode getIterator() {
        return iterator;
    }

    /**
     * Sets loop iterator expression:  the part before the "in" keyword.
     * Also sets its parent to this node.
     * @throws IllegalArgumentException if {@code iterator} is {@code null}
     */
    public void setIterator(AstNode iterator) {
        assertNotNull(iterator);
        this.iterator = iterator;
        iterator.setParent(this);
    }

    /**
     * Returns object being iterated over
     */
    public AstNode getIteratedObject() {
        return iteratedObject;
    }

    /**
     * Sets object being iterated over, and sets its parent to this node.
     * @throws IllegalArgumentException if {@code object} is {@code null}
     */
    public void setIteratedObject(AstNode object) {
        assertNotNull(object);
        this.iteratedObject = object;
        object.setParent(this);
    }

    /**
     * Returns whether the loop is a for-each loop
     */
    public boolean isForEach() {
        return isForEach;
    }

    /**
     * Sets whether the loop is a for-each loop
     */
    public void setIsForEach(boolean isForEach) {
        this.isForEach = isForEach;
    }

    /**
     * Returns position of "in" keyword
     */
    public int getInPosition() {
        return inPosition;
    }

    /**
     * Sets position of "in" keyword
     * @param inPosition position of "in" keyword,
     * or -1 if not present (e.g. in presence of a syntax error)
     */
    public void setInPosition(int inPosition) {
        this.inPosition = inPosition;
    }

    /**
     * Returns position of "each" keyword
     */
    public int getEachPosition() {
        return eachPosition;
    }

    /**
     * Sets position of "each" keyword
     * @param eachPosition position of "each" keyword,
     * or -1 if not present.
     */
    public void setEachPosition(int eachPosition) {
        this.eachPosition = eachPosition;
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append("for ");
        if (isForEach()) {
            sb.append("each ");
        }
        sb.append("(");
        sb.append(iterator.toSource(0));
        sb.append(" in ");
        sb.append(iteratedObject.toSource(0));
        sb.append(") ");
        if (body.getType() == Token.BLOCK) {
            sb.append(body.toSource(depth).trim()).append("\n");
        } else {
            sb.append("\n").append(body.toSource(depth+1));
        }
        return sb.toString();
    }

    /**
     * Visits this node, the iterator, the iterated object, and the body.
     */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            iterator.visit(v);
            iteratedObject.visit(v);
            body.visit(v);
        }
    }
}
