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
 *   Norris Boyd
 *   Roger Lawrence
 *   Mike McCabe
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

import org.mozilla.javascript.Node;
import org.mozilla.javascript.Token;

/**
 * Represents a symbol-table entry.
 */
public class Symbol {

    // One of Token.FUNCTION, Token.LP (for parameters), Token.VAR, 
    // Token.LET, or Token.CONST
    private int declType;
    private int index = -1;
    private String name;
    private Node node;
    private Scope containingTable;

    public Symbol() {
    }

    /**
     * Constructs a new Symbol with a specific name and declaration type
     * @param declType {@link Token#FUNCTION}, {@link Token#LP}
     * (for params), {@link Token#VAR}, {@link Token#LET} or {@link Token#CONST}
     */
    public Symbol(int declType, String name) {
        setName(name);
        setDeclType(declType);
    }

    /**
     * Returns symbol declaration type
     */
    public int getDeclType() {
        return declType;
    }

    /**
     * Sets symbol declaration type
     */
    public void setDeclType(int declType) {
        if (!(declType == Token.FUNCTION
              || declType == Token.LP
              || declType == Token.VAR
              || declType == Token.LET
              || declType == Token.CONST))
            throw new IllegalArgumentException("Invalid declType: " + declType);
        this.declType = declType;
    }

    /**
     * Returns symbol name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets symbol name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the node associated with this identifier
     */
    public Node getNode() {
        return node;
    }

    /**
     * Returns symbol's index in its scope
     */
    public int getIndex() {
        return index;
    }

    /**
     * Sets symbol's index in its scope
     */
    public void setIndex(int index) {
        this.index = index;
    }

    /**
     * Sets the node associated with this identifier
     */
    public void setNode(Node node) {
        this.node = node;
    }

    /**
     * Returns the Scope in which this symbol is entered
     */
    public Scope getContainingTable() {
        return containingTable;
    }

    /**
     * Sets this symbol's Scope
     */
    public void setContainingTable(Scope containingTable) {
        this.containingTable = containingTable;
    }

    public String getDeclTypeName() {
        return Token.typeToName(declType);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("Symbol (");
        result.append(getDeclTypeName());
        result.append(") name=");
        result.append(name);
        if (node != null) {
            result.append(" line=");
            result.append(node.getLineno());
        }
        return result.toString();
    }
}
