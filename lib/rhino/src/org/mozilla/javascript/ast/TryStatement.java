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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Try/catch/finally statement.  Node type is {@link Token#TRY}.<p>
 *
 * <pre><i>TryStatement</i> :
 *        <b>try</b> Block Catch
 *        <b>try</b> Block Finally
 *        <b>try</b> Block Catch Finally
 * <i>Catch</i> :
 *        <b>catch</b> ( <i><b>Identifier</b></i> ) Block
 * <i>Finally</i> :
 *        <b>finally</b> Block</pre>
 */
public class TryStatement extends AstNode {

    private static final List<CatchClause> NO_CATCHES =
        Collections.unmodifiableList(new ArrayList<CatchClause>());

    private AstNode tryBlock;
    private List<CatchClause> catchClauses;
    private AstNode finallyBlock;
    private int finallyPosition = -1;

    {
        type = Token.TRY;
    }

    public TryStatement() {
    }

    public TryStatement(int pos) {
        super(pos);
    }

    public TryStatement(int pos, int len) {
        super(pos, len);
    }

    public AstNode getTryBlock() {
        return tryBlock;
    }

    /**
     * Sets try block.  Also sets its parent to this node.
     * @throws IllegalArgumentException} if {@code tryBlock} is {@code null}
     */
    public void setTryBlock(AstNode tryBlock) {
        assertNotNull(tryBlock);
        this.tryBlock = tryBlock;
        tryBlock.setParent(this);
    }

    /**
     * Returns list of {@link CatchClause} nodes.  If there are no catch
     * clauses, returns an immutable empty list.
     */
    public List<CatchClause> getCatchClauses() {
        return catchClauses != null ? catchClauses : NO_CATCHES;
    }

    /**
     * Sets list of {@link CatchClause} nodes.  Also sets their parents
     * to this node.  May be {@code null}.  Replaces any existing catch
     * clauses for this node.
     */
    public void setCatchClauses(List<CatchClause> catchClauses) {
        if (catchClauses == null) {
            this.catchClauses = null;
        } else {
            if (this.catchClauses != null)
                this.catchClauses.clear();
            for (CatchClause cc : catchClauses) {
                addCatchClause(cc);
            }
        }
    }

    /**
     * Add a catch-clause to the end of the list, and sets its parent to
     * this node.
     * @throws IllegalArgumentException} if {@code clause} is {@code null}
     */
    public void addCatchClause(CatchClause clause) {
        assertNotNull(clause);
        if (catchClauses == null) {
            catchClauses = new ArrayList<CatchClause>();
        }
        catchClauses.add(clause);
        clause.setParent(this);
    }

    /**
     * Returns finally block, or {@code null} if not present
     */
    public AstNode getFinallyBlock() {
        return finallyBlock;
    }

    /**
     * Sets finally block, and sets its parent to this node.
     * May be {@code null}.
     */
    public void setFinallyBlock(AstNode finallyBlock) {
        this.finallyBlock = finallyBlock;
        if (finallyBlock != null)
            finallyBlock.setParent(this);
    }

    /**
     * Returns position of {@code finally} keyword, if present, or -1
     */
    public int getFinallyPosition() {
        return finallyPosition;
    }

    /**
     * Sets position of {@code finally} keyword, if present, or -1
     */
    public void setFinallyPosition(int finallyPosition) {
        this.finallyPosition = finallyPosition;
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder(250);
        sb.append(makeIndent(depth));
        sb.append("try ");
        sb.append(tryBlock.toSource(depth).trim());
        for (CatchClause cc : getCatchClauses()) {
            sb.append(cc.toSource(depth));
        }
        if (finallyBlock != null) {
            sb.append(" finally ");
            sb.append(finallyBlock.toSource(depth));
        }
        return sb.toString();
    }

    /**
     * Visits this node, then the try-block, then any catch clauses,
     * and then any finally block.
     */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            tryBlock.visit(v);
            for (CatchClause cc : getCatchClauses()) {
                cc.visit(v);
            }
            if (finallyBlock != null) {
                finallyBlock.visit(v);
            }
        }
    }
}
