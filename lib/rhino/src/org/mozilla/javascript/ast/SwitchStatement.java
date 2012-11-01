/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Switch statement AST node type.
 * Node type is {@link Token#SWITCH}.<p>
 *
 * <pre><i>SwitchStatement</i> :
 *        <b>switch</b> ( Expression ) CaseBlock
 * <i>CaseBlock</i> :
 *        { [CaseClauses] }
 *        { [CaseClauses] DefaultClause [CaseClauses] }
 * <i>CaseClauses</i> :
 *        CaseClause
 *        CaseClauses CaseClause
 * <i>CaseClause</i> :
 *        <b>case</b> Expression : [StatementList]
 * <i>DefaultClause</i> :
 *        <b>default</b> : [StatementList]</pre>
 */
public class SwitchStatement extends Jump {

    private static final List<SwitchCase> NO_CASES =
        Collections.unmodifiableList(new ArrayList<SwitchCase>());

    private AstNode expression;
    private List<SwitchCase> cases;
    private int lp = -1;
    private int rp = -1;

    {
        type = Token.SWITCH;
    }

    public SwitchStatement() {
    }

    public SwitchStatement(int pos) {
        // can't call super (Jump) for historical reasons
        position = pos;
    }

    public SwitchStatement(int pos, int len) {
        position = pos;
        length = len;
    }

    /**
     * Returns the switch discriminant expression
     */
    public AstNode getExpression() {
        return expression;
    }

    /**
     * Sets the switch discriminant expression, and sets its parent
     * to this node.
     * @throws IllegalArgumentException} if expression is {@code null}
     */
    public void setExpression(AstNode expression) {
        assertNotNull(expression);
        this.expression = expression;
        expression.setParent(this);
    }

    /**
     * Returns case statement list.  If there are no cases,
     * returns an immutable empty list.
     */
    public List<SwitchCase> getCases() {
        return cases != null ? cases : NO_CASES;
    }

    /**
     * Sets case statement list, and sets the parent of each child
     * case to this node.
     * @param cases list, which may be {@code null} to remove all the cases
     */
    public void setCases(List<SwitchCase> cases) {
        if (cases == null) {
            this.cases = null;
        } else {
            if (this.cases != null)
                this.cases.clear();
            for (SwitchCase sc : cases)
                addCase(sc);
        }
    }

    /**
     * Adds a switch case statement to the end of the list.
     * @throws IllegalArgumentException} if switchCase is {@code null}
     */
    public void addCase(SwitchCase switchCase) {
        assertNotNull(switchCase);
        if (cases == null) {
            cases = new ArrayList<SwitchCase>();
        }
        cases.add(switchCase);
        switchCase.setParent(this);
    }

    /**
     * Returns left paren position, -1 if missing
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
     * Returns right paren position, -1 if missing
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

    @Override
    public String toSource(int depth) {
        String pad = makeIndent(depth);
        StringBuilder sb = new StringBuilder();
        sb.append(pad);
        sb.append("switch (");
        sb.append(expression.toSource(0));
        sb.append(") {\n");
        for (SwitchCase sc : cases) {
            sb.append(sc.toSource(depth + 1));
        }
        sb.append(pad);
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Visits this node, then the switch-expression, then the cases
     * in lexical order.
     */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            expression.visit(v);
            for (SwitchCase sc: getCases()) {
                sc.visit(v);
            }
        }
    }
}
