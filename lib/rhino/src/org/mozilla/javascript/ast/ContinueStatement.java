/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;

/**
 * A continue statement.
 * Node type is {@link Token#CONTINUE}.<p>
 *
 * <pre><i>ContinueStatement</i> :
 *   <b>continue</b> [<i>no LineTerminator here</i>] [Identifier] ;</pre>
 */
public class ContinueStatement extends Jump {

    private Name label;
    private Loop target;

    {
        type = Token.CONTINUE;
    }

    public ContinueStatement() {
    }

    public ContinueStatement(int pos) {
        this(pos, -1);
    }

    public ContinueStatement(int pos, int len) {
        // can't call super (Jump) for historical reasons
        position = pos;
        length = len;
    }

    public ContinueStatement(Name label) {
        setLabel(label);
    }

    public ContinueStatement(int pos, Name label) {
        this(pos);
        setLabel(label);
    }

    public ContinueStatement(int pos, int len, Name label) {
        this(pos, len);
        setLabel(label);
    }

    /**
     * Returns continue target
     */
    public Loop getTarget() {
        return target;
    }

    /**
     * Sets continue target.  Does NOT set the parent of the target node:
     * the target node is an ancestor of this node.
     * @param target continue target
     * @throws IllegalArgumentException if target is {@code null}
     */
    public void setTarget(Loop target) {
        assertNotNull(target);
        this.target = target;
        setJumpStatement(target);
    }

    /**
     * Returns the intended label of this continue statement
     * @return the continue label.  Will be {@code null} if the statement
     * consisted only of the keyword "continue".
     */
    public Name getLabel() {
        return label;
    }

    /**
     * Sets the intended label of this continue statement.
     * Only applies if the statement was of the form "continue &lt;label&gt;".
     * @param label the continue label, or {@code null} if not present.
     */
    public void setLabel(Name label) {
        this.label = label;
        if (label != null)
            label.setParent(this);
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append("continue");
        if (label != null) {
            sb.append(" ");
            sb.append(label.toSource(0));
        }
        sb.append(";\n");
        return sb.toString();
    }

    /**
     * Visits this node, then visits the label if non-{@code null}.
     */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this) && label != null) {
            label.visit(v);
        }
    }
}
