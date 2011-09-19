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
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.SimpleReference;
import com.google.javascript.rhino.jstype.SimpleSlot;
import com.google.javascript.rhino.jstype.StaticReference;
import com.google.javascript.rhino.jstype.StaticScope;
import com.google.javascript.rhino.jstype.StaticSlot;
import com.google.javascript.rhino.jstype.StaticSymbolTable;
import com.google.javascript.rhino.jstype.UnionType;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * By design, when this symbol table creates symbols for types, it tries
 * to mimic the symbol table you would get in an OO language. For example,
 * the "type Foo" and "the constructor that creates objects of type Foo"
 * are the same symbol. The types of "Foo.prototype" and "new Foo()" also
 * have the same symbol. Although JSCompiler internally treats these as
 * distinct symbols, we assume that most clients will not care about
 * the distinction.
 *
 * @see #addSymbolsFrom For more information on how to write plugins for this
 *    symbol table.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public final class SymbolTable
    implements StaticSymbolTable<SymbolTable.Symbol, SymbolTable.Reference> {
  /**
   * The name we use for the JavaScript built-in Global object.  It's
   * anonymous in JavaScript, so we have to give it an invalid identifier
   * to avoid conflicts with user-defined property names.
   */
  public static final String GLOBAL_THIS = "*global*";

  /**
   * All symbols in the program, uniquely identified by the node where
   * they're declared and their name.
   */
  private final Table<Node, String, Symbol> symbols = HashBasedTable.create();

  /**
   * All syntactic scopes in the program, uniquely identified by the node where
   * they're declared.
   */
  private final Map<Node, SymbolScope> scopes = Maps.newHashMap();

  /**
   * All JSDocInfo in the program.
   */
  private final List<JSDocInfo> docInfos = Lists.newArrayList();

  private SymbolScope globalScope = null;

  private final JSTypeRegistry registry;

  /**
   * Clients should get a symbol table by asking the compiler at the end
   * of a compilation job.
   */
  SymbolTable(JSTypeRegistry registry) {
    this.registry = registry;
  }

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

  public Collection<JSDocInfo> getAllJSDocInfo() {
    return Collections.unmodifiableList(docInfos);
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
    if (scope.isPropertyScope()) {
      JSType type = scope.getTypeOfThis();
      if (type != null) {
        if (type.isGlobalThisType()) {
          return globalScope.getSlot(GLOBAL_THIS);
        } else if (type.isNominalConstructor()) {
          return getSymbolDeclaredBy(type.toMaybeFunctionType());
        } else if (type.isFunctionPrototypeType()) {
          return getSymbolForInstancesOf(
              ((ObjectType) type).getOwnerFunction());
        }
      }
      return null;
    }

    Node rootNode = scope.getRootNode();
    if (rootNode.getType() != Token.FUNCTION) {
      return null;
    }

    String name = NodeUtil.getBestLValueName(
        NodeUtil.getBestLValue(rootNode));
    return name == null ? null : scope.getParentScope().getSlot(name);
  }

  /**
   * Get all symbols associated with the type of the given symbol.
   *
   * For example, given a variable x declared as
   * /* @type {Array|Date} /
   * var x = f();
   * this will return the constructors for Array and Date.
   */
  public Iterable<Symbol> getAllSymbolsForTypeOf(Symbol sym) {
    return getAllSymbolsForType(sym.getType());
  }

  /**
   * Returns the global scope.
   */
  public SymbolScope getGlobalScope() {
    return globalScope;
  }

  /**
   * Gets the symbol for the given constuctor or interface.
   */
  public Symbol getSymbolDeclaredBy(FunctionType fn) {
    Preconditions.checkState(fn.isConstructor() || fn.isInterface());
    ObjectType instanceType = fn.getInstanceType();
    String name = instanceType.getReferenceName();
    if (name == null || globalScope == null) {
      return null;
    }

    Node source = fn.getSource();
    return (source == null ?
        globalScope : getEnclosingScope(source)).getSlot(name);
  }

  /**
   * Gets the symbol for the prototype if this is the symbol for a constructor
   * or interface.
   */
  public Symbol getSymbolForInstancesOf(Symbol sym) {
    FunctionType fn = sym.getFunctionType();
    if (fn != null && fn.isNominalConstructor()) {
      return getSymbolForInstancesOf(fn);
    }
    return null;
  }

  /**
   * Gets the symbol for the prototype of the given constructor or interface.
   */
  public Symbol getSymbolForInstancesOf(FunctionType fn) {
    Preconditions.checkState(fn.isConstructor() || fn.isInterface());
    ObjectType pType = fn.getPrototype();
    String name = pType.getReferenceName();
    if (name == null || globalScope == null) {
      return null;
    }

    Node source = fn.getSource();
    return (source == null ?
        globalScope : getEnclosingScope(source)).getSlot(name);
  }

  /**
   * Gets all symbols associated with the given type.
   * For union types, this may be multiple symbols.
   */
  public List<Symbol> getAllSymbolsForType(JSType type) {
    if (type == null) {
      return ImmutableList.of();
    }

    UnionType unionType = type.toMaybeUnionType();
    if (unionType != null) {
      List<Symbol> result = Lists.newArrayListWithExpectedSize(2);
      for (JSType alt : unionType.getAlternates()) {
        // Our type system never has nested unions.
        Symbol altSym = getOnlySymbolForType(alt);
        if (altSym != null) {
          result.add(altSym);
        }
      }
      return result;
    }
    Symbol result = getOnlySymbolForType(type);
    return result == null
        ? ImmutableList.<Symbol>of() : ImmutableList.of(result);
  }

  /**
   * Gets all symbols associated with the given type.
   * If there are more that one symbol associated with the given type,
   * return null.
   */
  private Symbol getOnlySymbolForType(JSType type) {
    if (type == null) {
      return null;
    }

    FunctionType fnType = type.toMaybeFunctionType();
    if (fnType != null) {
      return globalScope.getSlot("Function");
    }

    ObjectType objType = type.toObjectType();
    if (objType != null) {
      String name = objType.getReferenceName();

      FunctionType ctor = objType.getConstructor();
      Node sourceNode = ctor == null ? null : ctor.getSource();
      SymbolScope scope = sourceNode == null
          ? globalScope : getEnclosingScope(sourceNode);

      return scope.getSlot(
          (name == null || !objType.isInstanceType())
          ? "Object" : name);
    }

    // TODO(nicksantos): Create symbols for value types (number, string).
    return null;
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
   * Make sure all the given scopes in {@code otherSymbolTable}
   * are in this symbol table.
   */
  <S extends StaticScope<JSType>>
  void addScopes(Collection<S> scopes) {
    for (S scope : scopes) {
      createScopeFrom(scope);
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
      String name = otherSymbol.getName();
      SymbolScope myScope = createScopeFrom(
          otherSymbolTable.getScope(otherSymbol));

      StaticReference<JSType> decl = otherSymbol.getDeclaration();
      Node declNode = decl == null ? null : decl.getNode();
      Symbol mySymbol = null;
      if (declNode != null && declNode.getStaticSourceFile() != null) {
        // If we have a declaration node, we can ensure the symbol is declared.
        mySymbol = symbols.get(declNode, name);
        if (mySymbol == null) {
          mySymbol = copySymbolTo(otherSymbol, myScope);
        }
      } else {
        // If we don't have a declaration node, we won't be able to declare
        // a symbol in this symbol table. But we may be able to salvage the
        // references if we already have a symbol.
        mySymbol = myScope.getOwnSlot(name);
      }

      if (mySymbol != null) {
        for (R otherRef : otherSymbolTable.getReferences(otherSymbol)) {
          mySymbol.defineReferenceAt(otherRef.getNode());
        }
      }
    }
  }

  private Symbol copySymbolTo(StaticSlot<JSType> sym, SymbolScope scope) {
    return declareSymbol(
        sym.getName(), sym.getType(), sym.isTypeInferred(), scope,
        // All symbols must have declaration nodes.
        Preconditions.checkNotNull(sym.getDeclaration().getNode()),
        sym.getJSDocInfo());
  }

  private Symbol declareSymbol(
      String name, JSType type, boolean inferred,
      SymbolScope scope, Node declNode, JSDocInfo info) {
    Symbol symbol = new Symbol(name, type, inferred, scope);
    symbol.setJSDocInfo(info);
    symbols.put(declNode, name, symbol);

    Symbol replacedSymbol = scope.ownSymbols.put(name, symbol);
    Preconditions.checkState(replacedSymbol == null);

    symbol.setDeclaration(new Reference(symbol, declNode));
    return symbol;
  }

  private void removeSymbol(Symbol s) {
    SymbolScope scope = getScope(s);
    if (scope.ownSymbols.remove(s.getName()) != s) {
      throw new IllegalStateException("Symbol not found in scope " + s);
    }
    if (symbols.remove(s.getDeclaration().getNode(), s.getName()) != s) {
      throw new IllegalStateException("Symbol not found in table " + s);
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
   * Create symbols and references for all properites of types in
   * this symbol table.
   *
   * This gets a little bit tricky, because of the way this symbol table
   * conflates "type Foo" and "the constructor of type Foo". So if you
   * have:
   *
   * <code>
   * SymbolTable symbolTale = for("var x = new Foo();");
   * Symbol x = symbolTable.getGlobalScope().getSlot("x");
   * Symbol type = symbolTable.getOnlySymbolForType(x.getType());
   * </code>
   *
   * Then type.getPropertyScope() will have the properties of the
   * constructor "Foo". To get the properties of instances of "Foo",
   * you will need to call:
   *
   * <code>
   * Symbol instance = symbolTable.getSymbolForInstancesOf(type);
   * </code>
   *
   * As described at the top of this file, notice that "new Foo()" and
   * "Foo.prototype" are represented by the same symbol.
   */
  void fillPropertySymbols(
      AbstractCompiler compiler, Node externs, Node root) {
    // Collect all ctors and interface ctors.
    // We need to create these lists first, so that we don't end up
    // mutating the symbol table while we're creating new symbols.
    List<Symbol> types = Lists.newArrayList();
    List<Symbol> instances = Lists.newArrayList();
    for (Symbol sym : getAllSymbols()) {
      FunctionType t = sym.getFunctionType();
      if (t != null && t.isNominalConstructor()) {
        types.add(sym);

        Symbol instance = getSymbolForInstancesOf(t);
        if (instance != null) {
          instances.add(instance);
        }
      }
    }

    // Create a property scope for each symbol, and populate
    // it with that symbol's properties.
    //
    // The order of operations here is significant.
    //
    // When we add properties to Foo, we'll remove Foo.prototype from
    // the symbol table and replace it with a fresh symbol in Foo's
    // property scope. So the symbol for Foo.prototype in
    // {@code instances} will be stale.
    //
    // To prevent this, we always populate {@code instances} before
    // their constructors.
    for (Symbol s : instances) {
      createPropertyScopeFor(s);
    }

    for (Symbol s : types) {
      createPropertyScopeFor(s);
    }

    (new PropertyRefCollector(compiler)).process(externs, root);
  }

  /** Index JSDocInfo. */
  void fillJSDocInfo(
      AbstractCompiler compiler, Node externs, Node root) {
    NodeTraversal.traverseRoots(
        compiler, Lists.newArrayList(externs, root),
        new JSDocInfoCollector(compiler.getTypeRegistry()));
  }

  private void createPropertyScopeFor(Symbol s) {
    // In order to build a property scope for s, we will need to build
    // a property scope for all its implicit prototypes first. This means
    // that sometimes we will already have built its property scope
    // for a previous symbol.
    if (s.propertyScope != null) {
      return;
    }

    SymbolScope parentPropertyScope = null;
    ObjectType type = s.getType().toObjectType();
    ObjectType proto = type.getParentScope();
    if (proto != null && proto != type && proto.getConstructor() != null) {
      Symbol parentSymbol = getSymbolForInstancesOf(proto.getConstructor());
      if (parentSymbol != null) {
        createPropertyScopeFor(parentSymbol);
        parentPropertyScope = parentSymbol.getPropertyScope();
      }
    }

    ObjectType instanceType = type;
    Iterable<String> propNames = type.getOwnPropertyNames();
    if (instanceType.isFunctionPrototypeType()) {
      // Merge the properties of "Foo.prototype" and "new Foo()" together.
      instanceType = instanceType.getOwnerFunction().getInstanceType();
      Set<String> set = Sets.newHashSet(propNames);
      Iterables.addAll(set, instanceType.getOwnPropertyNames());
      propNames = set;
    }

    s.propertyScope = new SymbolScope(null, parentPropertyScope, type);
    for (String propName : propNames) {
      StaticSlot<JSType> newProp = instanceType.getSlot(propName);
      if (newProp.getDeclaration() == null) {
        // Skip properties without declarations. We won't know how to index
        // them, because we index things by node.
        continue;
      }

      // We have symbol tables that do not do type analysis. They just try
      // to build a complete index of all objects in the program. So we might
      // already have symbols for things like "Foo.bar". If this happens,
      // throw out the old symbol and use the type-based symbol.
      Symbol oldProp = getScope(s).getSlot(s.getName() + "." + propName);
      if (oldProp != null) {
        removeSymbol(oldProp);
      }

      Symbol newSym = copySymbolTo(newProp, s.propertyScope);
      if (oldProp != null) {
        if (newSym.getJSDocInfo() == null) {
          newSym.setJSDocInfo(oldProp.getJSDocInfo());
        }
        newSym.propertyScope = oldProp.propertyScope;
        for (Reference ref : oldProp.references.values()) {
          newSym.defineReferenceAt(ref.getNode());
        }
      }
    }
  }

  /**
   * Fill in references to "this" variables.
   */
  void fillThisReferences(
      AbstractCompiler compiler, Node externs, Node root) {
    (new ThisRefCollector(compiler)).process(externs, root);
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
            globalScope == null, "Global scopes found at different roots");
      }

      myScope = new SymbolScope(
          otherScopeRoot,
          otherScopeParent == null ? null : createScopeFrom(otherScopeParent),
          otherScope.getTypeOfThis());
      scopes.put(otherScopeRoot, myScope);
      if (myScope.isGlobalScope()) {
        globalScope = myScope;
      }
    }
    return myScope;
  }

  public static final class Symbol extends SimpleSlot {
    // Use a linked hash map, so that the results are deterministic
    // (and so the declaration always comes first).
    private final Map<Node, Reference> references = Maps.newLinkedHashMap();

    private final SymbolScope scope;

    private SymbolScope propertyScope = null;

    private Reference declaration = null;

    private JSDocInfo docInfo = null;

    Symbol(String name, JSType type, boolean inferred, SymbolScope scope) {
      super(name, type, inferred);
      this.scope = scope;
    }

    @Override
    public Reference getDeclaration() {
      return declaration;
    }

    public FunctionType getFunctionType() {
      return JSType.toMaybeFunctionType(getType());
    }

    public void defineReferenceAt(Node n) {
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

    public SymbolScope getPropertyScope() {
      return propertyScope;
    }

    @Override
    public JSDocInfo getJSDocInfo() {
      return docInfo;
    }

    void setJSDocInfo(JSDocInfo info) {
      this.docInfo = info;
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

      Symbol ancestor = parent == null ? null : parent.getSlot(name);
      if (ancestor != null) {
        return ancestor;
      }

      int dot = name.lastIndexOf('.');
      if (dot != -1) {
        Symbol owner = getSlot(name.substring(0, dot));
        if (owner != null && owner.getPropertyScope() != null) {
          return owner.getPropertyScope().getSlot(name.substring(dot + 1));
        }
      }
      return null;
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
      return getParentScope() == null && getRootNode() != null;
    }

    public boolean isPropertyScope() {
      return getRootNode() == null;
    }

    public boolean isLexicalScope() {
      return getRootNode() != null;
    }
  }

  private class PropertyRefCollector
      extends NodeTraversal.AbstractPostOrderCallback
      implements CompilerPass {
    private final AbstractCompiler compiler;

    PropertyRefCollector(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
      NodeTraversal.traverseRoots(
          compiler,
          Lists.newArrayList(externs, root),
          this);
    }

    private void maybeDefineReference(Node n, Symbol ownerSymbol) {
      String propName = n.getLastChild().getString();

      // getPropertyScope() will be null in some rare cases where there
      // are no extern declarations for built-in types (like Function).
      if (ownerSymbol != null && ownerSymbol.getPropertyScope() != null) {
        Symbol prop = ownerSymbol.getPropertyScope().getSlot(propName);
        if (prop != null) {
          prop.defineReferenceAt(n);
        }
      }
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.getType() == Token.GETPROP) {
        JSType owner = n.getFirstChild().getJSType();
        if (owner == null) {
          return;
        }

        if (owner.isGlobalThisType()) {
          Symbol sym = globalScope.getSlot(n.getLastChild().getString());
          if (sym != null) {
            sym.defineReferenceAt(n);
          }
        } else if (owner.isNominalConstructor()) {
          maybeDefineReference(
              n, getSymbolDeclaredBy(owner.toMaybeFunctionType()));
        } else {
          for (Symbol ctor : getAllSymbolsForType(owner)) {
            maybeDefineReference(n, getSymbolForInstancesOf(ctor));
          }
        }
      }
    }
  }

  private class ThisRefCollector
      extends NodeTraversal.AbstractPostOrderCallback
      implements CompilerPass {
    private final AbstractCompiler compiler;

    ThisRefCollector(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
      NodeTraversal.traverseRoots(
          compiler,
          Lists.newArrayList(externs, root),
          this);
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.getType() != Token.THIS) {
        return;
      }

      Symbol symbol = null;
      if (t.inGlobalScope()) {
        // declare the global this at the first place it's used.
        if (globalScope.getSlot(GLOBAL_THIS) == null) {
          symbol = declareSymbol(
              GLOBAL_THIS,
              registry.getNativeType(JSTypeNative.GLOBAL_THIS),
              false /* declared */,
              globalScope,
              n,
              null);
        } else {
          symbol = globalScope.getSlot(GLOBAL_THIS);
        }
      } else {
        // Otherwise, declare a "this" property when possible.
        SymbolScope scope = scopes.get(t.getScopeRoot());
        Preconditions.checkNotNull(scope);
        Symbol scopeSymbol = getSymbolForScope(scope);
        if (scopeSymbol != null) {
          createPropertyScopeFor(scopeSymbol);

          SymbolScope propScope = scopeSymbol.getPropertyScope();
          symbol = propScope.getSlot("this");
          if (symbol == null) {
            JSType type = n.getJSType();
            symbol = declareSymbol(
                "this",
                type,
                type != null && !type.isUnknownType(),
                propScope,
                n,
                null);
          }
        }
      }

      if (symbol != null) {
        symbol.defineReferenceAt(n);
      }
    }
  }

  /** Collects references to types in JSDocInfo. */
  private class JSDocInfoCollector
      extends NodeTraversal.AbstractPostOrderCallback {
    private final JSTypeRegistry registry;

    private JSDocInfoCollector(JSTypeRegistry registry) {
      this.registry = registry;
    }

    @Override public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.getJSDocInfo() != null) {

        // Find references in the JSDocInfo.
        JSDocInfo info = n.getJSDocInfo();
        docInfos.add(info);
        for (Node typeAst : info.getTypeNodes()) {
          SymbolScope scope = scopes.get(t.getScopeRoot());
          visitTypeNode(scope == null ? globalScope : scope, typeAst);
        }
      }
    }

    public void visitTypeNode(SymbolScope scope, Node n) {
      if (n.getType() == Token.STRING) {
        Symbol symbol = scope.getSlot(n.getString());
        if (symbol == null) {
          // If we can't find this type, it might be a reference to a
          // primitive type (like {string}). Autobox it to check.
          JSType type = registry.getType(n.getString());
          JSType autobox = type == null ? null : type.autoboxesTo();
          symbol = autobox == null ? null : getOnlySymbolForType(autobox);
        }
        if (symbol != null) {
          symbol.defineReferenceAt(n);
        }
      }

      for (Node child = n.getFirstChild();
           child != null; child = child.getNext()) {
        visitTypeNode(scope, child);
      }
    }
  }
}
