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
    public boolean hasSideEffects() {
        // just to avoid the default case for EXPR_VOID in AstNode
        return true;
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
