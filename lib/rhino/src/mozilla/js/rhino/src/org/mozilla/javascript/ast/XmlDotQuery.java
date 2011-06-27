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
 * AST node representing an E4X {@code foo.(bar)} query expression.
 * The node type (operator) is {@link Token#DOTQUERY}.
 * Its {@code getLeft} node is the target ("foo" in the example),
 * and the {@code getRight} node is the filter expression node.<p>
 *
 * This class exists separately from {@link InfixExpression} largely because it
 * has different printing needs.  The position of the left paren is just after
 * the dot (operator) position, and the right paren is the final position in the
 * bounds of the node.  If the right paren is missing, the node ends at the end
 * of the filter expression.
 */
public class XmlDotQuery extends InfixExpression {

    private int rp = -1;

    {
        type = Token.DOTQUERY;
    }

    public XmlDotQuery() {
    }

    public XmlDotQuery(int pos) {
        super(pos);
    }

    public XmlDotQuery(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns right-paren position, -1 if missing.<p>
     *
     * Note that the left-paren is automatically the character
     * immediately after the "." in the operator - no whitespace is
     * permitted between the dot and lp by the scanner.
     */
    public int getRp() {
        return rp;
    }

    /**
     * Sets right-paren position
     */
    public void setRp(int rp) {
        this.rp = rp;
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append(getLeft().toSource(0));
        sb.append(".(");
        sb.append(getRight().toSource(0));
        sb.append(")");
        return sb.toString();
    }
}
