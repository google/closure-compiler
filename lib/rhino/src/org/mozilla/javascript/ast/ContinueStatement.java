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
