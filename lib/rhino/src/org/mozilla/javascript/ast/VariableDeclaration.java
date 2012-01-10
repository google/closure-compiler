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
 * A standalone variable declaration in a statement context is wrapped with an
 * {@link ExpressionStatement}.
 */
public class VariableDeclaration extends AstNode {

    private List<VariableInitializer> variables
        = new ArrayList<VariableInitializer>();

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
        if (!(getParent() instanceof Loop)) {
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
