/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;

/**
 * AST node for an E4X XML {@code [expr]} member-ref expression.
 * The node type is {@link Token#REF_MEMBER}.<p>
 *
 * Syntax:<p>
 *
 * <pre> @<i><sub>opt</sub></i> ns:: <i><sub>opt</sub></i> [ expr ]</pre>
 *
 * Examples include {@code ns::[expr]}, {@code @ns::[expr]}, {@code @[expr]},
 * {@code *::[expr]} and {@code @*::[expr]}.<p>
 *
 * Note that the form {@code [expr]} (i.e. no namespace or
 * attribute-qualifier) is not a legal {@code XmlElemRef} expression,
 * since it's already used for standard JavaScript {@link ElementGet}
 * array-indexing.  Hence, an {@code XmlElemRef} node always has
 * either the attribute-qualifier, a non-{@code null} namespace node,
 * or both.<p>
 *
 * The node starts at the {@code @} token, if present.  Otherwise it starts
 * at the namespace name.  The node bounds extend through the closing
 * right-bracket, or if it is missing due to a syntax error, through the
 * end of the index expression.<p>
 */
public class XmlElemRef extends XmlRef {

    private AstNode indexExpr;
    private int lb = -1;
    private int rb = -1;

    {
        type = Token.REF_MEMBER;
    }

    public XmlElemRef() {
    }

    public XmlElemRef(int pos) {
        super(pos);
    }

    public XmlElemRef(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns index expression: the 'expr' in {@code @[expr]}
     * or {@code @*::[expr]}.
     */
    public AstNode getExpression() {
        return indexExpr;
    }

    /**
     * Sets index expression, and sets its parent to this node.
     * @throws IllegalArgumentException if {@code expr} is {@code null}
     */
    public void setExpression(AstNode expr) {
        assertNotNull(expr);
        indexExpr = expr;
        expr.setParent(this);
    }

    /**
     * Returns left bracket position, or -1 if missing.
     */
    public int getLb() {
        return lb;
    }

    /**
     * Sets left bracket position, or -1 if missing.
     */
    public void setLb(int lb) {
        this.lb = lb;
    }

    /**
     * Returns left bracket position, or -1 if missing.
     */
    public int getRb() {
        return rb;
    }

    /**
     * Sets right bracket position, -1 if missing.
     */
    public void setRb(int rb) {
        this.rb = rb;
    }

    /**
     * Sets both bracket positions.
     */
    public void setBrackets(int lb, int rb) {
        this.lb = lb;
        this.rb = rb;
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        if (isAttributeAccess()) {
            sb.append("@");
        }
        if (namespace != null) {
            sb.append(namespace.toSource(0));
            sb.append("::");
        }
        sb.append("[");
        sb.append(indexExpr.toSource(0));
        sb.append("]");
        return sb.toString();
    }

    /**
     * Visits this node, then the namespace if provided, then the
     * index expression.
     */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            if (namespace != null) {
                namespace.visit(v);
            }
            indexExpr.visit(v);
        }
    }
}
