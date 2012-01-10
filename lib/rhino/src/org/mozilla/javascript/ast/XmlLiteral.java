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
 * AST node for an E4X (Ecma-357) embedded XML literal.  Node type is
 * {@link Token#XML}.  The parser generates a simple list of strings and
 * expressions.  In the future we may parse the XML and produce a richer set of
 * nodes, but for now it's just a set of expressions evaluated to produce a
 * string to pass to the {@code XML} constructor function.<p>
 */
public class XmlLiteral extends AstNode {

    private List<XmlFragment> fragments = new ArrayList<XmlFragment>();

    {
        type = Token.XML;
    }

    public XmlLiteral() {
    }

    public XmlLiteral(int pos) {
        super(pos);
    }

    public XmlLiteral(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns fragment list - a list of expression nodes.
     */
    public List<XmlFragment> getFragments() {
        return fragments;
    }

    /**
     * Sets fragment list, removing any existing fragments first.
     * Sets the parent pointer for each fragment in the list to this node.
     * @param fragments fragment list.  Replaces any existing fragments.
     * @throws IllegalArgumentException} if {@code fragments} is {@code null}
     */
    public void setFragments(List<XmlFragment> fragments) {
        assertNotNull(fragments);
        this.fragments.clear();
        for (XmlFragment fragment : fragments)
            addFragment(fragment);
    }

    /**
     * Adds a fragment to the fragment list.  Sets its parent to this node.
     * @throws IllegalArgumentException} if {@code fragment} is {@code null}
     */
    public void addFragment(XmlFragment fragment) {
        assertNotNull(fragment);
        fragments.add(fragment);
        fragment.setParent(this);
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder(250);
        for (XmlFragment frag : fragments) {
            sb.append(frag.toSource(0));
        }
        return sb.toString();
    }

    /**
     * Visits this node, then visits each child fragment in lexical order.
     */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            for (XmlFragment frag : fragments) {
                frag.visit(v);
            }
        }
    }
}
