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
 * A labeled statement.  A statement can have more than one label.  In
 * this AST representation, all labels for a statement are collapsed into
 * the "labels" list of a single {@link LabeledStatement} node. <p>
 *
 * Node type is {@link Token#EXPR_VOID}. <p>
 */
public class LabeledStatement extends AstNode {

    private List<Label> labels = new ArrayList<Label>();  // always at least 1
    private AstNode statement;

    {
        type = Token.EXPR_VOID;
    }

    public LabeledStatement() {
    }

    public LabeledStatement(int pos) {
        super(pos);
    }

    public LabeledStatement(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns label list
     */
    public List<Label> getLabels() {
        return labels;
    }

    /**
     * Sets label list, setting the parent of each label
     * in the list.  Replaces any existing labels.
     * @throws IllegalArgumentException} if labels is {@code null}
     */
    public void setLabels(List<Label> labels) {
        assertNotNull(labels);
        if (this.labels != null)
            this.labels.clear();
        for (Label l : labels) {
            addLabel(l);
        }
    }

    /**
     * Adds a label and sets its parent to this node.
     * @throws IllegalArgumentException} if label is {@code null}
     */
    public void addLabel(Label label) {
        assertNotNull(label);
        labels.add(label);
        label.setParent(this);
    }

    /**
     * Returns the labeled statement
     */
    public AstNode getStatement() {
        return statement;
    }

    /**
     * Returns label with specified name from the label list for
     * this labeled statement.  Returns {@code null} if there is no
     * label with that name in the list.
     */
    public Label getLabelByName(String name) {
        for (Label label : labels) {
            if (name.equals(label.getName())) {
                return label;
            }
        }
        return null;
    }

    /**
     * Sets the labeled statement, and sets its parent to this node.
     * @throws IllegalArgumentException if {@code statement} is {@code null}
     */
    public void setStatement(AstNode statement) {
        assertNotNull(statement);
        this.statement = statement;
        statement.setParent(this);
    }

    public Label getFirstLabel() {
        return labels.get(0);
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        for (Label label : labels) {
            sb.append(label.toSource(depth));  // prints newline
        }
        sb.append(statement.toSource(depth + 1));
        return sb.toString();
    }

    /**
     * Visits this node, then each label in the label-list, and finally the
     * statement.
     */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            for (AstNode label : labels) {
                label.visit(v);
            }
            statement.visit(v);
        }
    }
}
