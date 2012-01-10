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
 * Node representing a catch-clause of a try-statement.
 * Node type is {@link Token#CATCH}.
 *
 * <pre><i>CatchClause</i> :
 *        <b>catch</b> ( <i><b>Identifier</b></i> [<b>if</b> Expression] ) Block</pre>
 */
public class CatchClause extends AstNode {

    private Name varName;
    private AstNode catchCondition;
    private Block body;
    private int ifPosition = -1;
    private int lp = -1;
    private int rp = -1;

    {
        type = Token.CATCH;
    }

    public CatchClause() {
    }

    public CatchClause(int pos) {
        super(pos);
    }

    public CatchClause(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns catch variable node
     * @return catch variable
     */
    public Name getVarName() {
        return varName;
    }

    /**
     * Sets catch variable node, and sets its parent to this node.
     * @param varName catch variable
     * @throws IllegalArgumentException if varName is {@code null}
     */
    public void setVarName(Name varName) {
        assertNotNull(varName);
        this.varName = varName;
        varName.setParent(this);
    }

    /**
     * Returns catch condition node, if present
     * @return catch condition node, {@code null} if not present
     */
    public AstNode getCatchCondition() {
        return catchCondition;
    }

    /**
     * Sets catch condition node, and sets its parent to this node.
     * @param catchCondition catch condition node.  Can be {@code null}.
     */
    public void setCatchCondition(AstNode catchCondition) {
        this.catchCondition = catchCondition;
        if (catchCondition != null)
            catchCondition.setParent(this);
    }

    /**
     * Returns catch body
     */
    public Block getBody() {
        return body;
    }

    /**
     * Sets catch body, and sets its parent to this node.
     * @throws IllegalArgumentException if body is {@code null}
     */
    public void setBody(Block body) {
        assertNotNull(body);
        this.body = body;
        body.setParent(this);
    }

    /**
     * Returns left paren position
     */
    public int getLp() {
        return lp;
    }

    /**
     * Sets left paren position
     */
    public void setLp(int lp) {
        this.lp = lp;
    }

    /**
     * Returns right paren position
     */
    public int getRp() {
        return rp;
    }

    /**
     * Sets right paren position
     */
    public void setRp(int rp) {
        this.rp = rp;
    }

    /**
     * Sets both paren positions
     */
    public void setParens(int lp, int rp) {
        this.lp = lp;
        this.rp = rp;
    }

    /**
     * Returns position of "if" keyword
     * @return position of "if" keyword, if present, or -1
     */
    public int getIfPosition() {
        return ifPosition;
    }

    /**
     * Sets position of "if" keyword
     * @param ifPosition position of "if" keyword, if present, or -1
     */
    public void setIfPosition(int ifPosition) {
        this.ifPosition = ifPosition;
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append("catch (");
        sb.append(varName.toSource(0));
        if (catchCondition != null) {
            sb.append(" if ");
            sb.append(catchCondition.toSource(0));
        }
        sb.append(") ");
        sb.append(body.toSource(0));
        return sb.toString();
    }

    /**
     * Visits this node, the catch var name node, the condition if
     * non-{@code null}, and the catch body.
     */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            varName.visit(v);
            if (catchCondition != null) {
                catchCondition.visit(v);
            }
            body.visit(v);
        }
    }
}
