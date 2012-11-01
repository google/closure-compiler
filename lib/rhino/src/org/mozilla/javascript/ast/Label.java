/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;

/**
 * AST node representing a label.  It is a distinct node type so it can
 * record its length and position for code-processing tools.
 * Node type is {@link Token#LABEL}.<p>
 */
public class Label extends Jump {

    private String name;

    {
        type = Token.LABEL;
    }

    public Label() {
    }

    public Label(int pos) {
        this(pos, -1);
    }

    public Label(int pos, int len) {
        // can't call super (Jump) for historical reasons
        position = pos;
        length = len;
    }

    public Label(int pos, int len, String name) {
        this(pos, len);
        setName(name);
    }

    /**
     * Returns the label text
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the label text
     * @throws IllegalArgumentException if name is {@code null} or the
     * empty string.
     */
    public void setName(String name) {
        name = name == null ? null : name.trim();
        if (name == null || "".equals(name))
            throw new IllegalArgumentException("invalid label name");
        this.name = name;
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append(name);
        sb.append(":\n");
        return sb.toString();
    }

    /**
     * Visits this label.  There are no children to visit.
     */
    @Override
    public void visit(NodeVisitor v) {
        v.visit(this);
    }
}
