/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;

/**
 * AST node for a RegExp literal.
 * Node type is {@link Token#REGEXP}.<p>
 */
public class RegExpLiteral extends AstNode {

    private String value;
    private String flags;

    {
        type = Token.REGEXP;
    }

    public RegExpLiteral() {
    }

    public RegExpLiteral(int pos) {
        super(pos);
    }

    public RegExpLiteral(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns the regexp string without delimiters
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the regexp string without delimiters
     * @throws IllegalArgumentException} if value is {@code null}
     */
    public void setValue(String value) {
        assertNotNull(value);
        this.value = value;
    }

    /**
     * Returns regexp flags, {@code null} or "" if no flags specified
     */
    public String getFlags() {
        return flags;
    }

    /**
     * Sets regexp flags.  Can be {@code null} or "".
     */
    public void setFlags(String flags) {
        this.flags = flags;
    }

    @Override
    public String toSource(int depth) {
        return makeIndent(depth) + "/" + value + "/"
                + (flags == null ? "" : flags);
    }

    /**
     * Visits this node.  There are no children to visit.
     */
    @Override
    public void visit(NodeVisitor v) {
        v.visit(this);
    }
}
