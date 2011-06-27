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
 * Base class for E4X XML attribute-access or property-get expressions.
 * Such expressions can take a variety of forms. The general syntax has
 * three parts:<p>
 *
 * <ol>
 *  <li>optional: an {@code @}</li>  (specifying an attribute access)</li>
 *  <li>optional: a namespace (a {@code Name}) and double-colon</li>
 *  <li>required:  either a {@code Name} or a bracketed [expression]</li>
 * </ol>
 *
 * The property-name expressions (examples:  {@code ns::name}, {@code @name})
 * are represented as {@link XmlPropRef} nodes.  The bracketed-expression
 * versions (examples:  {@code ns::[name]}, {@code @[name]}) become
 * {@link XmlElemRef} nodes.<p>
 *
 * This node type (or more specifically, its subclasses) will
 * sometimes be the right-hand child of a {@link PropertyGet} node or
 * an {@link XmlMemberGet} node.  The {@code XmlRef} node may also
 * be a standalone primary expression with no explicit target, which
 * is valid in certain expression contexts such as
 * {@code company..employee.(@id &lt; 100)} - in this case, the {@code @id}
 * is an {@code XmlRef} that is part of an infix '&lt;' expression
 * whose parent is an {@code XmlDotQuery} node.<p>
 */
public abstract class XmlRef extends AstNode {

    protected Name namespace;
    protected int atPos = -1;
    protected int colonPos = -1;

    public XmlRef() {
    }

    public XmlRef(int pos) {
        super(pos);
    }

    public XmlRef(int pos, int len) {
        super(pos, len);
    }

    /**
     * Return the namespace.  May be {@code @null}.
     */
    public Name getNamespace() {
        return namespace;
    }

    /**
     * Sets namespace, and sets its parent to this node.
     * Can be {@code null}.
     */
    public void setNamespace(Name namespace) {
        this.namespace = namespace;
        if (namespace != null)
            namespace.setParent(this);
    }

    /**
     * Returns {@code true} if this expression began with an {@code @}-token.
     */
    public boolean isAttributeAccess() {
        return atPos >= 0;
    }

    /**
     * Returns position of {@code @}-token, or -1 if this is not
     * an attribute-access expression.
     */
    public int getAtPos() {
        return atPos;
    }

    /**
     * Sets position of {@code @}-token, or -1
     */
    public void setAtPos(int atPos) {
        this.atPos = atPos;
    }

    /**
     * Returns position of {@code ::} token, or -1 if not present.
     * It will only be present if the namespace node is non-{@code null}.
     */
    public int getColonPos() {
        return colonPos;
    }

    /**
     * Sets position of {@code ::} token, or -1 if not present
     */
    public void setColonPos(int colonPos) {
        this.colonPos = colonPos;
    }
}
