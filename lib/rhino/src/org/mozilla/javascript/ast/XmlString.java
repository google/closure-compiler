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

/**
 * AST node for an XML-text-only component of an XML literal expression.  This
 * node differs from a {@link StringLiteral} in that it does not have quotes for
 * delimiters.
 */
public class XmlString extends XmlFragment {

    private String xml;

    public XmlString() {
    }

    public XmlString(int pos) {
        super(pos);
    }

    public XmlString(int pos, String s) {
        super(pos);
        setXml(s);
    }

    /**
     * Sets the string for this XML component.  Sets the length of the
     * component to the length of the passed string.
     * @param s a string of xml text
     * @throws IllegalArgumentException} if {@code s} is {@code null}
     */
    public void setXml(String s) {
        assertNotNull(s);
        xml = s;
        setLength(s.length());
    }

    /**
     * Returns the xml string for this component.
     * Note that it may not be well-formed XML; it is a fragment.
     */
    public String getXml() {
        return xml;
    }

    @Override
    public String toSource(int depth) {
        return makeIndent(depth) + xml;
    }

    /**
     * Visits this node.  There are no children to visit.
     */
    @Override
    public void visit(NodeVisitor v) {
        v.visit(this);
    }
}
