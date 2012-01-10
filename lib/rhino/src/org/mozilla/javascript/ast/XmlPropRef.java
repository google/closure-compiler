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
