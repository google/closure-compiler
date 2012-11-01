/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;
import org.mozilla.javascript.ScriptRuntime;

/**
 * AST node for a single- or double-quoted string literal.
 * Node type is {@link Token#STRING}.<p>
 */
public class StringLiteral extends AstNode {

    private String value;
    private char quoteChar;

    {
        type = Token.STRING;
    }

    public StringLiteral() {
    }

    public StringLiteral(int pos) {
        super(pos);
    }

    /**
     * Creates a string literal node at the specified position.
     * @param len the length <em>including</em> the enclosing quotes
     */
    public StringLiteral(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns the node's value:  the parsed string without the enclosing quotes
     * @return the node's value, a {@link String} of unescaped characters
     * that includes the delimiter quotes.
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns the string value, optionally including the enclosing quotes.
     */
    public String getValue(boolean includeQuotes) {
        if (!includeQuotes)
            return value;
        return quoteChar + value + quoteChar;
    }

    /**
     * Sets the node's value.  Do not include the enclosing quotes.
     * @param value the node's value
     * @throws IllegalArgumentException} if value is {@code null}
     */
    public void setValue(String value) {
        assertNotNull(value);
        this.value = value;
    }

    /**
     * Returns the character used as the delimiter for this string.
     */
    public char getQuoteCharacter() {
        return quoteChar;
    }

    public void setQuoteCharacter(char c) {
        quoteChar = c;
    }

    @Override
    public String toSource(int depth) {
        return new StringBuilder(makeIndent(depth))
                .append(quoteChar)
                .append(ScriptRuntime.escapeString(value, quoteChar))
                .append(quoteChar)
                .toString();
    }

    /**
     * Visits this node.  There are no children to visit.
     */
    @Override
    public void visit(NodeVisitor v) {
        v.visit(this);
    }
}
