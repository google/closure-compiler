/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.ast;

import org.mozilla.javascript.Node;
import org.mozilla.javascript.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a scope in the lexical scope chain.  Base type for
 * all {@link AstNode} implementations that can introduce a new scope.
 */
public class Scope extends Jump {

    // Use LinkedHashMap so that the iteration order is the insertion order
    protected Map<String,Symbol> symbolTable;
    protected Scope parentScope;
    protected ScriptNode top;     // current script or function scope

    private List<Scope> childScopes;

    {
        this.type = Token.BLOCK;
    }

    public Scope() {
    }

    public Scope(int pos) {
        this.position = pos;
    }

    public Scope(int pos, int len) {
        this(pos);
        this.length = len;
    }

    public Scope getParentScope() {
        return parentScope;
    }

    /**
     * Sets parent scope
     */
    public void setParentScope(Scope parentScope) {
        this.parentScope = parentScope;
        this.top = parentScope == null ? (ScriptNode)this : parentScope.top;
    }

    /**
     * Used only for code generation.
     */
    public void clearParentScope() {
        this.parentScope = null;
    }

    /**
     * Return a list of the scopes whose parent is this scope.
     * @return the list of scopes we enclose, or {@code null} if none
     */
    public List<Scope> getChildScopes() {
        return childScopes;
    }

    /**
     * Add a scope to our list of child scopes.
     * Sets the child's parent scope to this scope.
     * @throws IllegalStateException if the child's parent scope is
     * non-{@code null}
     */
    public void addChildScope(Scope child) {
        if (childScopes == null) {
            childScopes = new ArrayList<Scope>();
        }
        childScopes.add(child);
        child.setParentScope(this);
    }

    /**
     * Used by the parser; not intended for typical use.
     * Changes the parent-scope links for this scope's child scopes
     * to the specified new scope.  Copies symbols from this scope
     * into new scope.
     *
     * @param newScope the scope that will replace this one on the
     *        scope stack.
     */
    public void replaceWith(Scope newScope) {
        if (childScopes != null) {
            for (Scope kid : childScopes) {
                newScope.addChildScope(kid);  // sets kid's parent
            }
            childScopes.clear();
            childScopes = null;
        }
        if (symbolTable != null && !symbolTable.isEmpty()) {
            joinScopes(this, newScope);
        }
    }

    /**
     * Returns current script or function scope
     */
    public ScriptNode getTop() {
        return top;
    }

    /**
     * Sets top current script or function scope
     */
    public void setTop(ScriptNode top) {
        this.top = top;
    }

    /**
     * Creates a new scope node, moving symbol table information
     * from "scope" to the new node, and making "scope" a nested
     * scope contained by the new node.
     * Useful for injecting a new scope in a scope chain.
     */
    public static Scope splitScope(Scope scope) {
        Scope result = new Scope(scope.getType());
        result.symbolTable = scope.symbolTable;
        scope.symbolTable = null;
        result.parent = scope.parent;
        result.setParentScope(scope.getParentScope());
        result.setParentScope(result);
        scope.parent = result;
        result.top = scope.top;
        return result;
    }

    /**
     * Copies all symbols from source scope to dest scope.
     */
    public static void joinScopes(Scope source, Scope dest) {
        Map<String,Symbol> src = source.ensureSymbolTable();
        Map<String,Symbol> dst = dest.ensureSymbolTable();
        if (!Collections.disjoint(src.keySet(), dst.keySet())) {
            codeBug();
        }
        for (Map.Entry<String, Symbol> entry: src.entrySet()) {
            Symbol sym = entry.getValue();
            sym.setContainingTable(dest);
            dst.put(entry.getKey(), sym);
        }
    }

    /**
     * Returns the scope in which this name is defined
     * @param name the symbol to look up
     * @return this {@link Scope}, one of its parent scopes, or {@code null} if
     * the name is not defined any this scope chain
     */
    public Scope getDefiningScope(String name) {
        for (Scope s = this; s != null; s = s.parentScope) {
            Map<String,Symbol> symbolTable = s.getSymbolTable();
            if (symbolTable != null && symbolTable.containsKey(name)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Looks up a symbol in this scope.
     * @param name the symbol name
     * @return the Symbol, or {@code null} if not found
     */
    public Symbol getSymbol(String name) {
        return symbolTable == null ? null : symbolTable.get(name);
    }

    /**
     * Enters a symbol into this scope.
     */
    public void putSymbol(Symbol symbol) {
        if (symbol.getName() == null)
            throw new IllegalArgumentException("null symbol name");
        ensureSymbolTable();
        symbolTable.put(symbol.getName(), symbol);
        symbol.setContainingTable(this);
        top.addSymbol(symbol);
    }

    /**
     * Returns the symbol table for this scope.
     * @return the symbol table.  May be {@code null}.
     */
    public Map<String,Symbol> getSymbolTable() {
        return symbolTable;
    }

    /**
     * Sets the symbol table for this scope.  May be {@code null}.
     */
    public void setSymbolTable(Map<String, Symbol> table) {
        symbolTable = table;
    }

    private Map<String,Symbol> ensureSymbolTable() {
        if (symbolTable == null) {
            symbolTable = new LinkedHashMap<String,Symbol>(5);
        }
        return symbolTable;
    }

    /**
     * Returns a copy of the child list, with each child cast to an
     * {@link AstNode}.
     * @throws ClassCastException if any non-{@code AstNode} objects are
     * in the child list, e.g. if this method is called after the code
     * generator begins the tree transformation.
     */
    public List<AstNode> getStatements() {
        List<AstNode> stmts = new ArrayList<AstNode>();
        Node n = getFirstChild();
        while (n != null) {
            stmts.add((AstNode)n);
            n = n.getNext();
        }
        return stmts;
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append("{\n");
        for (Node kid : this) {
            sb.append(((AstNode)kid).toSource(depth+1));
        }
        sb.append(makeIndent(depth));
        sb.append("}\n");
        return sb.toString();
    }

    @Override
    public void visit(NodeVisitor v) {
        if (v.visit(this)) {
            for (Node kid : this) {
                ((AstNode)kid).visit(v);
            }
        }
    }
}
