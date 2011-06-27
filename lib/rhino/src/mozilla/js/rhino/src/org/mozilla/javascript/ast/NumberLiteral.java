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
 * AST node for a Number literal. Node type is {@link Token#NUMBER}.<p>
 */
public class NumberLiteral extends AstNode {

    private String value;
    private double number;

    {
        type = Token.NUMBER;
    }

    public NumberLiteral() {
    }

    public NumberLiteral(int pos) {
        super(pos);
    }

    public NumberLiteral(int pos, int len) {
        super(pos, len);
    }

    /**
     * Constructor.  Sets the length to the length of the {@code value} string.
     */
    public NumberLiteral(int pos, String value) {
        super(pos);
        setValue(value);
        setLength(value.length());
    }

    /**
     * Constructor.  Sets the length to the length of the {@code value} string.
     */
    public NumberLiteral(int pos, String value, double number) {
        this(pos, value);
        setDouble(number);
    }

    public NumberLiteral(double number) {
        setDouble(number);
        setValue(Double.toString(number));
    }

    /**
     * Returns the node's string value (the original source token)
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the node's value
     * @throws IllegalArgumentException} if value is {@code null}
     */
    public void setValue(String value) {
        assertNotNull(value);
        this.value = value;
    }

    /**
     * Gets the {@code double} value.
     */
    public double getNumber() {
        return number;
    }

    /**
     * Sets the node's {@code double} value.
     */
    public void setNumber(double value) {
        number = value;
    }

    @Override
    public String toSource(int depth) {
        return makeIndent(depth) + (value == null ? "<null>" : value);
    }

    /**
     * Visits this node.  There are no children to visit.
     */
    @Override
    public void visit(NodeVisitor v) {
        v.visit(this);
    }
}
