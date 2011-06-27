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
