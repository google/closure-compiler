/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;

/**
 * AST node for an E4X XML {@code [expr]} property-ref expression.
 * The node type is {@link Token#REF_NAME}.<p>
 *
 * Syntax:<p>
 *
 * <pre> @<i><sub>opt</sub></i> ns:: <i><sub>opt</sub></i> name</pre>
 *
 * Examples include {@code name}, {@code ns::name}, {@code ns::*},
 * {@code *::name}, {@code *::*}, {@code @attr}, {@code @ns::attr},
 * {@code @ns::*}, {@code @*::attr}, {@code @*::*} and {@code @*}.<p>
 *
 * The node starts at the {@code @} token, if present.  Otherwise it starts
 * at the namespace name.  The node bounds extend through the closing
 * right-bracket, or if it is missing due to a syntax error, through the
 * end of the index expression.<p>
 */
public class XmlPropRef extends XmlRef {

    private Name propName;

    {
        type = Token.REF_NAME;
    }

    public XmlPropRef() {
    }

    public XmlPropRef(int pos) {
        super(pos);
    }

    public XmlPropRef(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns property name.
     */
    public Name getPropName() {
        return propName;
    }

    /**
     * Sets property name, and sets its parent to this node.
     * @throws IllegalArgumentException if {@code propName} is {@code null}
     */
    public void setPropName(Name propName) {
        assertNotNull(propName);
        this.propName = propName;
        propName.setParent(this);
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
        sb.append(propName.toSource(0));
        return sb.toString();
    }

    /**
     * Visits this node, then the namespace if present, then the property name.
     */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            if (namespace != null) {
                namespace.visit(v);
            }
            propName.visit(v);
        }
    }
}
