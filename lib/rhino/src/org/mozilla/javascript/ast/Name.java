/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;

/**
 * AST node for a simple name.  A simple name is an identifier that is
 * not a keyword. Node type is {@link Token#NAME}.<p>
 *
 * This node type is also used to represent certain non-identifier names that
 * are part of the language syntax.  It's used for the "get" and "set"
 * pseudo-keywords for object-initializer getter/setter properties, and it's
 * also used for the "*" wildcard in E4X XML namespace and name expressions.
 */
public class Name extends AstNode {

    private String identifier;
    private Scope scope;

    {
        type = Token.NAME;
    }

    public Name() {
    }

    public Name(int pos) {
        super(pos);
    }

    public Name(int pos, int len) {
        super(pos, len);
    }

    /**
     * Constructs a new {@link Name}
     * @param pos node start position
     * @param len node length
     * @param name the identifier associated with this {@code Name} node
     */
    public Name(int pos, int len, String name) {
        super(pos, len);
        setIdentifier(name);
    }

    public Name(int pos, String name) {
        super(pos);
        setIdentifier(name);
        setLength(name.length());
    }

    /**
     * Returns the node's identifier
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * Sets the node's identifier
     * @throws IllegalArgumentException if identifier is null
     */
    public void setIdentifier(String identifier) {
        assertNotNull(identifier);
        this.identifier = identifier;
        setLength(identifier.length());
    }

    /**
     * Set the {@link Scope} associated with this node.  This method does not
     * set the scope's ast-node field to this node.  The field exists only
     * for temporary storage by the code generator.  Not every name has an
     * associated scope - typically only function and variable names (but not
     * property names) are registered in a scope.
     *
     * @param s the scope.  Can be null.  Doesn't set any fields in the
     * scope.
     */
    public void setScope(Scope s) {
        scope = s;
    }

    /**
     * Return the {@link Scope} associated with this node.  This is
     * <em>only</em> used for (and set by) the code generator, so it will always
     * be null in frontend AST-processing code.  Use {@link #getDefiningScope}
     * to find the lexical {@code Scope} in which this {@code Name} is defined,
     * if any.
     */
    public Scope getScope() {
        return scope;
    }

    /**
     * Returns the {@link Scope} in which this {@code Name} is defined.
     * @return the scope in which this name is defined, or {@code null}
     * if it's not defined in the current lexical scope chain
     */
    public Scope getDefiningScope() {
        Scope enclosing = getEnclosingScope();
        String name = getIdentifier();
        return enclosing == null ? null : enclosing.getDefiningScope(name);
    }

    /**
     * Return true if this node is known to be defined as a symbol in a
     * lexical scope other than the top-level (global) scope.
     *
     * @return {@code true} if this name appears as local variable, a let-bound
     * variable not in the global scope, a function parameter, a loop
     * variable, the property named in a {@link PropertyGet}, or in any other
     * context where the node is known not to resolve to the global scope.
     * Returns {@code false} if the node is defined in the top-level scope
     * (i.e., its defining scope is an {@link AstRoot} object), or if its
     * name is not defined as a symbol in the symbol table, in which case it
     * may be an external or built-in name (or just an error of some sort.)
     */
    public boolean isLocalName() {
        Scope scope = getDefiningScope();
        return scope != null && scope.getParentScope() != null;
    }

    /**
     * Return the length of this node's identifier, to let you pretend
     * it's a {@link String}.  Don't confuse this method with the
     * {@link AstNode#getLength} method, which returns the range of
     * characters that this node overlaps in the source input.
     */
    public int length() {
        return identifier == null ? 0 : identifier.length();
    }

    @Override
    public String toSource(int depth) {
        return makeIndent(depth) + (identifier == null ? "<null>" : identifier);
    }

    /**
     * Visits this node.  There are no children to visit.
     */
    @Override
    public void visit(NodeVisitor v) {
        v.visit(this);
    }
}
