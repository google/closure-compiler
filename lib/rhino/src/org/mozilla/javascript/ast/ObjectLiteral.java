/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AST node for an Object literal (also called an Object initialiser in
 * Ecma-262).  The elements list will always be non-{@code null}, although
 * the list will have no elements if the Object literal is empty.<p>
 *
 * Node type is {@link Token#OBJECTLIT}.<p>
 *
 * <pre><i>ObjectLiteral</i> :
 *       <b>{}</b>
 *       <b>{</b> PropertyNameAndValueList <b>}</b>
 * <i>PropertyNameAndValueList</i> :
 *       PropertyName <b>:</b> AssignmentExpression
 *       PropertyNameAndValueList , PropertyName <b>:</b> AssignmentExpression
 * <i>PropertyName</i> :
 *       Identifier
 *       StringLiteral
 *       NumericLiteral</pre>
 */
public class ObjectLiteral extends AstNode implements DestructuringForm {

    private static final List<ObjectProperty> NO_ELEMS =
        Collections.unmodifiableList(new ArrayList<ObjectProperty>());

    private List<ObjectProperty> elements;
    boolean isDestructuring;

    {
        type = Token.OBJECTLIT;
    }

    public ObjectLiteral() {
    }

    public ObjectLiteral(int pos) {
        super(pos);
    }

    public ObjectLiteral(int pos, int len) {
        super(pos, len);
    }

    /**
     * Returns the element list.  Returns an immutable empty list if there are
     * no elements.
     */
    public List<ObjectProperty> getElements() {
        return elements != null ? elements : NO_ELEMS;
    }

    /**
     * Sets the element list, and updates the parent of each element.
     * Replaces any existing elements.
     * @param elements the element list.  Can be {@code null}.
     */
    public void setElements(List<ObjectProperty> elements) {
        if (elements == null) {
            this.elements = null;
        } else {
            if (this.elements != null)
                this.elements.clear();
            for (ObjectProperty o : elements)
                addElement(o);
        }
    }

    /**
     * Adds an element to the list, and sets its parent to this node.
     * @param element the property node to append to the end of the list
     * @throws IllegalArgumentException} if element is {@code null}
     */
    public void addElement(ObjectProperty element) {
        assertNotNull(element);
        if (elements == null) {
            elements = new ArrayList<ObjectProperty>();
        }
        elements.add(element);
        element.setParent(this);
    }

    /**
     * Marks this node as being a destructuring form - that is, appearing
     * in a context such as {@code for ([a, b] in ...)} where it's the
     * target of a destructuring assignment.
     */
    public void setIsDestructuring(boolean destructuring) {
        isDestructuring = destructuring;
    }

    /**
     * Returns true if this node is in a destructuring position:
     * a function parameter, the target of a variable initializer, the
     * iterator of a for..in loop, etc.
     */
    public boolean isDestructuring() {
        return isDestructuring;
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append("{");
        if (elements != null) {
            printList(elements, sb);
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Visits this node, then visits each child property node, in lexical
     * (source) order.
     */
    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            for (ObjectProperty prop : getElements()) {
                prop.visit(v);
            }
        }
    }
}
