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
 * A list of one or more var, const or let declarations.
 * Node type is {@link Token#VAR}, {@link Token#CONST} or
 * {@link Token#LET}.<p>
 *
 * If the node is for {@code var} or {@code const}, the node position
 * is the beginning of the {@code var} or {@code const} keyword.
 * For {@code let} declarations, the node position coincides with the
 * first {@link VariableInitializer} child.<p>
 *
 * A standalone variable declaration in a statement context returns {@code true}
 * from its {@link #isStatement()} method.
 */
public class VariableDeclaration extends AstNode {

    private List<VariableInitializer> variables = new ArrayList<VariableInitializer>();
    private boolean isStatement;

    {
        type = Token.VAR;
    }

    public VariableDeclaration() {
    }

    public VariableDeclaration(int pos) {
        super(pos);
    }

    public VariableDeclaration(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns variable list.  Never {@code null}.
     */
    public List<VariableInitializer> getVariables() {
        return variables;
    }

    /**
     * Sets variable list
     * @throws IllegalArgumentException if variables list is {@code null}
     */
    public void setVariables(List<VariableInitializer> variables) {
        assertNotNull(variables);
        this.variables.clear();
        for (VariableInitializer vi : variables) {
            addVariable(vi);
        }
    }

    /**
     * Adds a variable initializer node to the child list.
     * Sets initializer node's parent to this node.
     * @throws IllegalArgumentException if v is {@code null}
     */
    public void addVariable(VariableInitializer v) {
        assertNotNull(v);
        variables.add(v);
        v.setParent(this);
    }

    /**
     * Sets the node type and returns this node.
     * @throws IllegalArgumentException if {@code declType} is invalid
     */
    @Override
    public org.mozilla.javascript.Node setType(int type) {
        if (type != Token.VAR
            && type != Token.CONST
            && type != Token.LET)
            throw new IllegalArgumentException("invalid decl type: " + type);
        return super.setType(type);
    }

    /**
     * Returns true if this is a {@code var} (not
     * {@code const} or {@code let}) declaration.
     * @return true if {@code declType} is {@link Token#VAR}
     */
    public boolean isVar() {
        return type == Token.VAR;
    }

    /**
     * Returns true if this is a {@link Token#CONST} declaration.
     */
    public boolean isConst() {
        return type == Token.CONST;
    }

    /**
     * Returns true if this is a {@link Token#LET} declaration.
     */
    public boolean isLet() {
        return type == Token.LET;
    }

    /**
     * Returns true if this node represents a statement.
     */
    public boolean isStatement() {
        return isStatement;
    }

    /**
     * Set or unset the statement flag.
     */
    public void setIsStatement(boolean isStatement) {
        this.isStatement = isStatement;
    }

    private String declTypeName() {
        return Token.typeToName(type).toLowerCase();
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append(declTypeName());
        sb.append(" ");
        printList(variables, sb);
        if (isStatement()) {
            sb.append(";\n");
        }
        return sb.toString();
    }

    /**
     * Visits this node, then each {@link VariableInitializer} child.
     */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            for (AstNode var : variables) {
                var.visit(v);
            }
        }
    }
}
