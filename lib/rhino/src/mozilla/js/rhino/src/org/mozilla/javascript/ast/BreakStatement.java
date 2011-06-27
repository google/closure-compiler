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
 * A break statement.  Node type is {@link Token#BREAK}.<p>
 *
 * <pre><i>BreakStatement</i> :
 *   <b>break</b> [<i>no LineTerminator here</i>] [Identifier] ;</pre>
 */
public class BreakStatement extends Jump {

    private Name breakLabel;
    private AstNode target;

    {
        type = Token.BREAK;
    }

    public BreakStatement() {
    }

    public BreakStatement(int pos) {
        // can't call super (Jump) for historical reasons
        position = pos;
    }

    public BreakStatement(int pos, int len) {
        position = pos;
        length = len;
    }

    /**
     * Returns the intended label of this break statement
     * @return the break label.  {@code null} if the source code did
     * not specify a specific break label via "break &lt;target&gt;".
     */
    public Name getBreakLabel() {
        return breakLabel;
    }

    /**
     * Sets the intended label of this break statement, e.g.  'foo'
     * in "break foo". Also sets the parent of the label to this node.
     * @param label the break label, or {@code null} if the statement is
     * just the "break" keyword by itself.
     */
    public void setBreakLabel(Name label) {
        breakLabel = label;
        if (label != null)
            label.setParent(this);
    }

    /**
     * Returns the statement to break to
     * @return the break target.  Only {@code null} if the source
     * code has an error in it.
     */
    public AstNode getBreakTarget() {
        return target;
    }

    /**
     * Sets the statement to break to.
     * @param target the statement to break to
     * @throws IllegalArgumentException if target is {@code null}
     */
    public void setBreakTarget(Jump target) {
        assertNotNull(target);
        this.target = target;
        setJumpStatement(target);
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append("break");
        if (breakLabel != null) {
            sb.append(" ");
            sb.append(breakLabel.toSource(0));
        }
        sb.append(";\n");
        return sb.toString();
    }

    /**
     * Visits this node, then visits the break label if non-{@code null}.
     */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this) && breakLabel != null) {
            breakLabel.visit(v);
        }
    }
}
