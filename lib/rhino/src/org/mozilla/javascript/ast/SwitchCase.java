/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * Switch-case AST node type.  The switch case is always part of a
 * switch statement.
 * Node type is {@link Token#CASE}.<p>
 *
 * <pre><i>CaseBlock</i> :
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
public class SwitchCase extends AstNode {

    private AstNode expression;
    private List<AstNode> statements;

    {
        type = Token.CASE;
    }

    public SwitchCase() {
    }

    public SwitchCase(int pos) {
        super(pos);
    }

    public SwitchCase(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns the case expression, {@code null} for default case
     */
    public AstNode getExpression() {
        return expression;
    }

    /**
     * Sets the case expression, {@code null} for default case.
     * Note that for empty fall-through cases, they still have
     * a case expression.  In {@code case 0: case 1: break;} the
     * first case has an {@code expression} that is a
     * {@link NumberLiteral} with value {@code 0}.
     */
    public void setExpression(AstNode expression) {
        this.expression = expression;
        if (expression != null)
            expression.setParent(this);
    }

    /**
     * Return true if this is a default case.
     * @return true if {@link #getExpression} would return {@code null}
     */
    public boolean isDefault() {
        return expression == null;
    }

    /**
     * Returns statement list, which may be {@code null}.
     */
    public List<AstNode> getStatements() {
        return statements;
    }

    /**
     * Sets statement list.  May be {@code null}.  Replaces any existing
     * statements.  Each element in the list has its parent set to this node.
     */
    public void setStatements(List<AstNode> statements) {
        if (this.statements != null) {
            this.statements.clear();
        }
        for (AstNode s : statements) {
            addStatement(s);
        }
    }

    /**
     * Adds a statement to the end of the statement list.
     * Sets the parent of the new statement to this node, updates
     * its start offset to be relative to this node, and sets the
     * length of this node to include the new child.
     *
     * @param statement a child statement
     * @throws IllegalArgumentException} if statement is {@code null}
     */
    public void addStatement(AstNode statement) {
        assertNotNull(statement);
        if (statements == null) {
            statements = new ArrayList<AstNode>();
        }
        int end = statement.getPosition() + statement.getLength();
        this.setLength(end - this.getPosition());
        statements.add(statement);
        statement.setParent(this);
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        if (expression == null) {
            sb.append("default:\n");
        } else {
            sb.append("case ");
            sb.append(expression.toSource(0));
            sb.append(":\n");
        }
        if (statements != null) {
            for (AstNode s : statements) {
                sb.append(s.toSource(depth+1));
            }
        }
        return sb.toString();
    }

    /**
     * Visits this node, then the case expression if present, then
     * each statement (if any are specified).
     */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            if (expression != null) {
                expression.visit(v);
            }
            if (statements != null) {
                for (AstNode s : statements) {
                    s.visit(v);
                }
            }
        }
    }
}
