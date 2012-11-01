/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;

/**
 * AST node for a Number literal. Node type is {@link Token#NUMBER}.<p>
 */
public class NumberLiteral extends AstNode {

    private String value;
    private double number;

    {
        type = Token.NUMBER;
    }

    public NumberLiteral() {
    }

    public NumberLiteral(int pos) {
        super(pos);
    }

    public NumberLiteral(int pos, int len) {
        super(pos, len);
    }

    /**
     * Constructor.  Sets the length to the length of the {@code value} string.
     */
    public NumberLiteral(int pos, String value) {
        super(pos);
        setValue(value);
        setLength(value.length());
    }

    /**
     * Constructor.  Sets the length to the length of the {@code value} string.
     */
    public NumberLiteral(int pos, String value, double number) {
        this(pos, value);
        setDouble(number);
    }

    public NumberLiteral(double number) {
        setDouble(number);
        setValue(Double.toString(number));
    }

    /**
     * Returns the node's string value (the original source token)
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the node's value
     * @throws IllegalArgumentException} if value is {@code null}
     */
    public void setValue(String value) {
        assertNotNull(value);
        this.value = value;
    }

    /**
     * Gets the {@code double} value.
     */
    public double getNumber() {
        return number;
    }

    /**
     * Sets the node's {@code double} value.
     */
    public void setNumber(double value) {
        number = value;
    }

    @Override
    public String toSource(int depth) {
        return makeIndent(depth) + (value == null ? "<null>" : value);
    }

    /**
     * Visits this node.  There are no children to visit.
     */
    @Override
    public void visit(NodeVisitor v) {
        v.visit(this);
    }
}
