/*
 * Copyright 2011 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.SimpleReference;
import com.google.javascript.rhino.jstype.SimpleSlot;
import com.google.javascript.rhino.jstype.StaticReference;
import com.google.javascript.rhino.jstype.StaticScope;
import com.google.javascript.rhino.jstype.StaticSlot;
import com.google.javascript.rhino.jstype.StaticSymbolTable;

import java.util.Collections;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * A symbol table for people that want to use Closure Compiler as an indexer.
 *
 * Contains an index of all the symbols in the code within a compilation
 * job. The API is designed for people who want to visit all the symbols, rather
 * than people who want to lookup a specific symbol by a certain key.
 *
 * We can use this to combine different types of symbol tables. For example,
 * one class might have a {@code StaticSymbolTable} of all variable references,
 * and another class might have a {@code StaticSymbolTable} of all type names
 * in JSDoc comments. This class allows you to combine them into a unified
 * index.
 *
 * Most passes build their own "partial" symbol table that implements the same
 * interface (StaticSymbolTable, StaticSlot, and friends). Individual compiler
 * passes usually need more or less metadata about the certainty of symbol
 * information. Building a complete symbol table with all the necessary metadata
 * for all passes would be too slow. However, as long as these "partial" symbol
 * tables implement the proper interfaces, we should be able to add them to this
 * symbol table to make it more complete.
 *
 * If clients want fast lookup, they should build their own wrapper around
 * this symbol table that indexes symbols or references by the desired lookup
 * key.
 *
 * @see #addSymbolsFrom For more information on how to write plugins for this
 *    symbol table.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public final class SymbolTable
    implements StaticSymbolTable<SymbolTable.Symbol, SymbolTable.Reference> {

  /**
   * All symbols in the program, uniquely identified by the node where
   * they're declared.
   */
  private final Map<Node, Symbol> symbols = Maps.newHashMap();

  /**
   * All scopes in the program, uniquely identified by the node where
   * they're declared.
   */
  private final Map<Node, SymbolScope> scopes = Maps.newHashMap();

  /**
   * Clients should get a symbol table by asking the compiler at the end
   * of a compilation job.
   */
  SymbolTable() {}

  @Override
  public Iterable<Reference> getReferences(Symbol symbol) {
    return Collections.unmodifiableCollection(symbol.references.values());
  }

  @Override
  public Iterable<Symbol> getAllSymbols() {
    return Collections.unmodifiableCollection(symbols.values());
  }

  @Override
  public SymbolScope getScope(Symbol slot) {
    return slot.scope;
  }

  /**
   * Gets the scope that contains the given node.
   * If {@code n} is a function name, we return the scope that contains the
   * function, not the function itself.
   */
  public SymbolScope getEnclosingScope(Node n) {
    Node current = n.getParent();
    if (n.getType() == Token.NAME &&
        n.getParent().getType() == Token.FUNCTION) {
      current = current.getParent();
    }

    for (; current != null; current = current.getParent()) {
      if (scopes.containsKey(current)) {
        return scopes.get(current);
      }
    }
    return null;
  }

  /**
   * All local scopes are associated with a function, and some functions
   * are associated with a symbol. Returns the symbol associated with the given
   * scope.
   */
  public Symbol getSymbolForScope(SymbolScope scope) {
    Node rootNode = scope.getRootNode();
    if (rootNode.getType() != Token.FUNCTION) {
      return null;
    }

    String name = NodeUtil.getBestLValueName(
        NodeUtil.getBestLValue(rootNode));
    return name == null ? null : scope.getParentScope().getSlot(name);
  }

  public String toDebugString() {
    StringBuilder builder = new StringBuilder();
    for (Symbol symbol : getAllSymbols()) {
      toDebugString(builder, symbol);
    }
    return builder.toString();
  }

  private void toDebugString(StringBuilder builder, Symbol symbol) {
    SymbolScope scope = symbol.scope;
    if (scope.isGlobalScope()) {
      builder.append(
          String.format("'%s' : in global scope:\n", symbol.getName()));
    } else {
      builder.append(
          String.format("'%s' : in scope %s:%d\n",
              symbol.getName(),
              scope.getRootNode().getSourceFileName(),
              scope.getRootNode().getLineno()));
    }

    int refCount = 0;
    for (Reference ref : getReferences(symbol)) {
      builder.append(
          String.format("  Ref %d: %s:%d\n",
              refCount,
              ref.getNode().getSourceFileName(),
              ref.getNode().getLineno()));
      refCount++;
    }
  }

  /**
   * Make sure all the symbols and references in {@code otherSymbolTable}
   * are in this symbol table.
   *
   * Uniqueness of symbols and references is determined by the associated
   * node.
   *
   * If multiple symbol tables are mixed in, we do not check for consistency
   * between symbol tables. The first symbol we see dictates the type
   * information for that symbol.
   */
  <S extends StaticSlot<JSType>, R extends StaticReference<JSType>>
  void addSymbolsFrom(StaticSymbolTable<S, R> otherSymbolTable) {
    for (S otherSymbol : otherSymbolTable.getAllSymbols()) {
      SymbolScope myScope = createScopeFrom(
          otherSymbolTable.getScope(otherSymbol));

      StaticReference<JSType> decl = otherSymbol.getDeclaration();
      Node declNode = decl == null ? null : decl.getNode();
      Symbol mySymbol = null;
      if (declNode != null && declNode.getStaticSourceFile() != null) {
        // If we have a declaration node, we can ensure the symbol is declared.
        mySymbol = symbols.get(declNode);
        if (mySymbol == null) {
          mySymbol = new Symbol(
              otherSymbol.getName(),
              otherSymbol.getType(),
              otherSymbol.isTypeInferred(),
              myScope);
          symbols.put(declNode, mySymbol);
          myScope.ownSymbols.put(mySymbol.getName(), mySymbol);

          mySymbol.setDeclaration(new Reference(mySymbol, declNode));
        }
      } else {
        // If we don't have a declaration node, we won't be able to declare
        // a symbol in this symbol table. But we may be able to salvage the
        // references if we already have a symbol.
        mySymbol = myScope.getOwnSlot(otherSymbol.getName());
      }

      if (mySymbol != null) {
        for (R otherRef : otherSymbolTable.getReferences(otherSymbol)) {
          mySymbol.defineReferenceAt(otherRef.getNode());
        }
      }
    }
  }

  /**
   * Not all symbol tables record references to "namespace" objects.
   * For example, if you have:
   * goog.dom.DomHelper = function() {};
   * The symbol table may not record that as a reference to "goog.dom",
   * because that would be redundant.
   */
  void fillNamespaceReferences() {
    for (Symbol symbol : getAllSymbols()) {
      for (Reference ref : getReferences(symbol)) {
        Node currentNode = ref.getNode();
        while (currentNode.getType() == Token.GETPROP) {
          currentNode = currentNode.getFirstChild();

          String name = currentNode.getQualifiedName();
          if (name != null) {
            Symbol namespace = symbol.scope.getSlot(name);

            // Never create new symbols, because we don't want to guess at
            // declarations if we're not sure. If this symbol doesn't exist,
            // then we probably want to add a better symbol table that does
            // have it.
            if (namespace != null) {
              namespace.defineReferenceAt(currentNode);
            }
          }
        }
      }
    }
  }

  /**
   * Given a scope from another symbol table, returns the {@code SymbolScope}
   * rooted at the same node. Creates one if it doesn't exist yet.
   */
  private SymbolScope createScopeFrom(StaticScope<JSType> otherScope) {
    Node otherScopeRoot = otherScope.getRootNode();
    SymbolScope myScope = scopes.get(otherScopeRoot);
    if (myScope == null) {
      StaticScope<JSType> otherScopeParent = otherScope.getParentScope();

      // If otherScope is a global scope, and we already have a global scope,
      // then something has gone seriously wrong.
      //
      // Not all symbol tables are rooted at the same global node, and
      // we do not want to mix and match symbol tables that are rooted
      // differently.

      if (otherScopeParent == null) {
        // The global scope must be created before any local scopes.
        Preconditions.checkState(
            scopes.isEmpty(), "Global scopes found at different roots");
      }

      myScope = new SymbolScope(
          otherScopeRoot,
          otherScopeParent == null ? null : createScopeFrom(otherScopeParent),
          otherScope.getTypeOfThis());
      scopes.put(otherScopeRoot, myScope);
    }
    return myScope;
  }

  public static final class Symbol extends SimpleSlot {
    // Use a linked hash map, so that the results are deterministic
    // (and so the declaration always comes first).
    private final Map<Node, Reference> references = Maps.newLinkedHashMap();

    private final SymbolScope scope;

    private Reference declaration = null;

    Symbol(String name, JSType type, boolean inferred, SymbolScope scope) {
      super(name, type, inferred);
      this.scope = scope;
    }

    @Override
    public Reference getDeclaration() {
      return declaration;
    }

    void defineReferenceAt(Node n) {
      if (!references.containsKey(n)) {
        references.put(n, new Reference(this, n));
      }
    }

    /** Sets the declaration node. May only be called once. */
    void setDeclaration(Reference ref) {
      Preconditions.checkState(this.declaration == null);
      this.declaration = ref;
      references.put(ref.getNode(), ref);
    }

    public boolean inGlobalScope() {
      return scope.isGlobalScope();
    }

    public boolean inExterns() {
      Node n = getDeclarationNode();
      return n == null ? false : n.isFromExterns();
    }

    public Node getDeclarationNode() {
      return declaration == null ? null : declaration.getNode();
    }

    public String getSourceFileName() {
      Node n = getDeclarationNode();
      return n == null ? null : n.getSourceFileName();
    }

    @Override
    public String toString() {
      Node n = getDeclarationNode();
      int lineNo = n == null ? -1 : n.getLineno();
      return getName() + "@" + getSourceFileName() + ":" + lineNo;
    }
  }

  public static final class Reference extends SimpleReference<Symbol> {
    Reference(Symbol symbol, Node node) {
      super(symbol, node);
    }
  }

  public static final class SymbolScope implements StaticScope<JSType> {
    private final Node rootNode;
    private final SymbolScope parent;
    private final JSType typeOfThis;
    private final Map<String, Symbol> ownSymbols = Maps.newHashMap();

    SymbolScope(
        Node rootNode,
        @Nullable SymbolScope parent,
        JSType typeOfThis) {
      this.rootNode = rootNode;
      this.parent = parent;
      this.typeOfThis = typeOfThis;
    }

    @Override
    public Node getRootNode() {
      return rootNode;
    }

    @Override
    public SymbolScope getParentScope() {
      return parent;
    }

    @Override
    public Symbol getSlot(String name) {
      Symbol own = getOwnSlot(name);
      if (own != null) {
        return own;
      }

      return parent == null ? null : parent.getSlot(name);
    }

    @Override
    public Symbol getOwnSlot(String name) {
      return ownSymbols.get(name);
    }

    @Override
    public JSType getTypeOfThis() {
      return typeOfThis;
    }

    public boolean isGlobalScope() {
      return getParentScope() == null;
    }
  }
}
