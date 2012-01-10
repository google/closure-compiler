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
 * AST node for a single name:value entry in an Object literal.
 * For simple entries, the node type is {@link Token#COLON}, and
 * the name (left side expression) is either a {@link Name}, a
 * {@link StringLiteral} or a {@link NumberLiteral}.<p>
 *
 * This node type is also used for getter/setter properties in object
 * literals.  In this case the node bounds include the "get" or "set"
 * keyword.  The left-hand expression in this case is always a
 * {@link Name}, and the overall node type is {@link Token#GET} or
 * {@link Token#SET}, as appropriate.<p>
 *
 * The {@code operatorPosition} field is meaningless if the node is
 * a getter or setter.<p>
 *
 * <pre><i>ObjectProperty</i> :
 *       PropertyName <b>:</b> AssignmentExpression
 * <i>PropertyName</i> :
 *       Identifier
 *       StringLiteral
 *       NumberLiteral</pre>
 */
public class ObjectProperty extends InfixExpression {

    {
        type = Token.COLON;
    }

    /**
     * Sets the node type.  Must be one of
     * {@link Token#COLON}, {@link Token#GET}, or {@link Token#SET}.
     * @throws IllegalArgumentException if {@code nodeType} is invalid
     */
    public void setNodeType(int nodeType) {
        if (nodeType != Token.COLON
            && nodeType != Token.GET
            && nodeType != Token.SET)
            throw new IllegalArgumentException("invalid node type: "
                                               + nodeType);
        setType(nodeType);
    }

    public ObjectProperty() {
    }

    public ObjectProperty(int pos) {
        super(pos);
    }

    public ObjectProperty(int pos, int len) {
        super(pos, len);
    }

    /**
     * Marks this node as a "getter" property.
     */
    public void setIsGetter() {
        type = Token.GET;
    }

    /**
     * Returns true if this is a getter function.
     */
    public boolean isGetter() {
        return type == Token.GET;
    }

    /**
     * Marks this node as a "setter" property.
     */
    public void setIsSetter() {
        type = Token.SET;
    }

    /**
     * Returns true if this is a setter function.
     */
    public boolean isSetter() {
        return type == Token.SET;
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        if (isGetter()) {
            sb.append("get ");
        } else if (isSetter()) {
            sb.append("set ");
        }
        sb.append(left.toSource(0));
        if (type == Token.COLON) {
            sb.append(": ");
        }
        sb.append(right.toSource(0));
        return sb.toString();
    }
}
