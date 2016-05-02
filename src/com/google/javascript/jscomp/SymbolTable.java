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
import com.google.common.base.Predicates;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.common.collect.Table;
import com.google.javascript.jscomp.parsing.parser.util.format.SimpleFormat;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Marker;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SourcePosition;
import com.google.javascript.rhino.StaticRef;
import com.google.javascript.rhino.StaticScope;
import com.google.javascript.rhino.StaticSlot;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.StaticSymbolTable;
import com.google.javascript.rhino.jstype.EnumType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.SimpleReference;
import com.google.javascript.rhino.jstype.SimpleSlot;
import com.google.javascript.rhino.jstype.StaticTypedScope;
import com.google.javascript.rhino.jstype.StaticTypedSlot;
import com.google.javascript.rhino.jstype.UnionType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Logger;

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
public final class SymbolTable {
  private static final Logger logger =
      Logger.getLogger(SymbolTable.class.getName());

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
  private final Map<Node, SymbolScope> scopes = new LinkedHashMap<>();

  /**
   * All Nodes with JSDocInfo in the program.
   */
  private final List<Node> docInfos = new ArrayList<>();

  private SymbolScope globalScope = null;

  private final JSTypeRegistry registry;

  /**
   * Clients should get a symbol table by asking the compiler at the end
   * of a compilation job.
   */
  SymbolTable(JSTypeRegistry registry) {
    this.registry = registry;
  }

  public Iterable<Reference> getReferences(Symbol symbol) {
    return Collections.unmodifiableCollection(symbol.references.values());
  }

  public List<Reference> getReferenceList(Symbol symbol) {
    return ImmutableList.copyOf(symbol.references.values());
  }

  public Iterable<Symbol> getAllSymbols() {
    return Collections.unmodifiableCollection(symbols.values());
  }

  /**
   * Get the symbols in their natural ordering.
   * Always returns a mutable list.
   */
  public List<Symbol> getAllSymbolsSorted() {
    List<Symbol> sortedSymbols = getNaturalSymbolOrdering().sortedCopy(symbols.values());
    return sortedSymbols;
  }

  /**
   * Gets the 'natural' ordering of symbols.
   *
   * Right now, we only guarantee that symbols in the global scope will come
   * before symbols in local scopes. After that, the order is deterministic but
   * undefined.
   */
  public Ordering<Symbol> getNaturalSymbolOrdering() {
    return symbolOrdering;
  }

  public SymbolScope getScope(Symbol slot) {
    return slot.scope;
  }

  public Collection<Node> getAllJSDocInfoNodes() {
    return Collections.unmodifiableList(docInfos);
  }

  /**
   * Declare a symbol after the main symbol table was constructed.
   * Throws an exception if you try to declare a symbol twice.
   */
  public Symbol declareInferredSymbol(
      SymbolScope scope, String name, Node declNode) {
    return declareSymbol(name, null, true, scope, declNode, null);
  }

  /**
   * Gets the scope that contains the given node.
   * If {@code n} is a function name, we return the scope that contains the
   * function, not the function itself.
   */
  public SymbolScope getEnclosingScope(Node n) {
    Node current = n.getParent();
    if (n.isName() &&
        n.getParent().isFunction()) {
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
   * If {@code sym} is a function, try to find a Symbol for
   * a parameter with the given name.
   *
   * Returns null if we couldn't find one.
   *
   * Notice that this just makes a best effort, and may not be able
   * to find parameters for non-conventional function definitions.
   * For example, we would not be able to find "y" in this code:
   * <code>
   * var x = x() ? function(y) {} : function(y) {};
   * </code>
   */
  public Symbol getParameterInFunction(Symbol sym, String paramName) {
    SymbolScope scope = getScopeInFunction(sym);
    if (scope != null) {
      Symbol param = scope.getSlot(paramName);
      if (param != null && param.scope == scope) {
        return param;
      }
    }
    return null;
  }

  private SymbolScope getScopeInFunction(Symbol sym) {
    FunctionType type = sym.getFunctionType();
    if (type == null) {
      return null;
    }

    Node functionNode = type.getSource();
    if (functionNode == null) {
      return null;
    }

    return scopes.get(functionNode);
  }

  /**
   * All local scopes are associated with a function, and some functions
   * are associated with a symbol. Returns the symbol associated with the given
   * scope.
   */
  public Symbol getSymbolForScope(SymbolScope scope) {
    if (scope.getSymbolForScope() == null) {
      scope.setSymbolForScope(findSymbolForScope(scope));
    }
    return scope.getSymbolForScope();
  }

  /**
   * Find the symbol associated with the given scope.
   * Notice that we won't always be able to figure out this association
   * dynamically, so sometimes we'll just create the association when we
   * create the scope.
   */
  private Symbol findSymbolForScope(SymbolScope scope) {
    Node rootNode = scope.getRootNode();
    if (rootNode.getParent() == null) {
      return globalScope.getSlot(GLOBAL_THIS);
    }

    if (!rootNode.isFunction()) {
      return null;
    }

    String name = NodeUtil.getBestLValueName(
        NodeUtil.getBestLValue(rootNode));
    return name == null ? null : scope.getParentScope().getQualifiedSlot(name);
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
    return getAllSymbolsForType(getType(sym));
  }

  /**
   * Returns the global scope.
   */
  public SymbolScope getGlobalScope() {
    return globalScope;
  }

  /**
   * Gets the symbol for the given constructor or interface.
   */
  public Symbol getSymbolDeclaredBy(FunctionType fn) {
    Preconditions.checkState(fn.isConstructor() || fn.isInterface());
    ObjectType instanceType = fn.getInstanceType();
    return getSymbolForName(fn.getSource(), instanceType.getReferenceName());
  }

  /**
   * Gets the symbol for the given enum.
   */
  public Symbol getSymbolDeclaredBy(EnumType enumType) {
    return getSymbolForName(null,
        enumType.getElementsType().getReferenceName());
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
    return getSymbolForName(fn.getSource(), pType.getReferenceName());
  }

  private Symbol getSymbolForName(Node source, String name) {
    if (name == null || globalScope == null) {
      return null;
    }

    SymbolScope scope = source == null ?
        globalScope : getEnclosingScope(source);

    // scope will sometimes be null if one of the type-stripping passes
    // was run, and the symbol isn't in the AST anymore.
    return scope == null ? null : scope.getQualifiedSlot(name);
  }

  /**
   * Gets all symbols associated with the given type.
   * For union types, this may be multiple symbols.
   * For instance types, this will return the constructor of
   * that instance.
   */
  public List<Symbol> getAllSymbolsForType(JSType type) {
    if (type == null) {
      return ImmutableList.of();
    }

    UnionType unionType = type.toMaybeUnionType();
    if (unionType != null) {
      List<Symbol> result = new ArrayList<>(2);
      for (JSType alt : unionType.getAlternates()) {
        // Our type system never has nested unions.
        Symbol altSym = getSymbolForTypeHelper(alt, true);
        if (altSym != null) {
          result.add(altSym);
        }
      }
      return result;
    }
    Symbol result = getSymbolForTypeHelper(type, true);
    return result == null
        ? ImmutableList.<Symbol>of() : ImmutableList.of(result);
  }

  /**
   * Gets all symbols associated with the given type.
   * If there is more that one symbol associated with the given type,
   * return null.
   * @param type The type.
   * @param linkToCtor If true, we should link instance types back
   *     to their constructor function. If false, we should link
   *     instance types back to their prototype. See the comments
   *     at the top of this file for more information on how
   *     our internal type system is more granular than Symbols.
   */
  private Symbol getSymbolForTypeHelper(JSType type, boolean linkToCtor) {
    if (type == null) {
      return null;
    }

    if (type.isGlobalThisType()) {
      return globalScope.getSlot(GLOBAL_THIS);
    } else if (type.isNominalConstructor()) {
      return linkToCtor ?
          globalScope.getSlot("Function") :
          getSymbolDeclaredBy(type.toMaybeFunctionType());
    } else if (type.isFunctionPrototypeType()) {
      FunctionType ownerFn = ((ObjectType) type).getOwnerFunction();
      if (!ownerFn.isConstructor() && !ownerFn.isInterface()) {
        return null;
      }
      return linkToCtor ?
          getSymbolDeclaredBy(ownerFn) :
          getSymbolForInstancesOf(ownerFn);
    } else if (type.isInstanceType()) {
      FunctionType ownerFn = ((ObjectType) type).getConstructor();
      return linkToCtor ?
          getSymbolDeclaredBy(ownerFn) :
          getSymbolForInstancesOf(ownerFn);
    } else if (type.isFunctionType()) {
      return linkToCtor ?
          globalScope.getSlot("Function") :
          globalScope.getQualifiedSlot("Function.prototype");
    } else if (type.autoboxesTo() != null) {
      return getSymbolForTypeHelper(type.autoboxesTo(), linkToCtor);
    } else {
      return null;
    }
  }

  @SuppressWarnings("unused")
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
          SimpleFormat.format("'%s' : in global scope:\n", symbol.getName()));
    } else if (scope.getRootNode() != null) {
      builder.append(
          SimpleFormat.format("'%s' : in scope %s:%d\n",
              symbol.getName(),
              scope.getRootNode().getSourceFileName(),
              scope.getRootNode().getLineno()));
    } else if (scope.getSymbolForScope() != null) {
      builder.append(
          SimpleFormat.format("'%s' : in scope %s\n", symbol.getName(),
              scope.getSymbolForScope().getName()));
    } else {
      builder.append(
          SimpleFormat.format("'%s' : in unknown scope\n", symbol.getName()));
    }

    int refCount = 0;
    for (Reference ref : getReferences(symbol)) {
      builder.append(
          SimpleFormat.format("  Ref %d: %s:%d\n",
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
  <S extends StaticScope> void addScopes(Collection<S> scopes) {
    for (S scope : scopes) {
      createScopeFrom(scope);
    }
  }

  /** Finds all the scopes and adds them to this symbol table. */
  void findScopes(AbstractCompiler compiler, Node externs, Node root) {
    NodeTraversal.traverseRoots(
        compiler,
        new NodeTraversal.AbstractScopedCallback() {
          @Override
          public void enterScope(NodeTraversal t) {
            createScopeFrom(t.getScope());
          }

          @Override
          public void visit(NodeTraversal t, Node n, Node p) {}
        },
        externs, root);
  }

  /** Gets all the scopes in this symbol table. */
  public Collection<SymbolScope> getAllScopes() {
    return Collections.unmodifiableCollection(scopes.values());
  }

  /**
   * Finds anonymous functions in local scopes, and gives them names
   * and symbols. They will show up as local variables with names
   * "function%0", "function%1", etc.
   */
  public void addAnonymousFunctions() {
    TreeSet<SymbolScope> scopes = new TreeSet<>(lexicalScopeOrdering);
    for (SymbolScope scope : getAllScopes()) {
      if (scope.isLexicalScope()) {
        scopes.add(scope);
      }
    }

    for (SymbolScope scope : scopes) {
      addAnonymousFunctionsInScope(scope);
    }
  }

  private void addAnonymousFunctionsInScope(SymbolScope scope) {
    Symbol sym = getSymbolForScope(scope);
    if (sym == null) {
      // JSCompiler has no symbol for this scope. Check to see if it's a
      // local function. If it is, give it a name.
      if (scope.isLexicalScope() &&
          !scope.isGlobalScope() &&
          scope.getRootNode() != null &&
          !scope.getRootNode().isFromExterns() &&
          scope.getParentScope() != null) {
        SymbolScope parent = scope.getParentScope();

        int count = parent.innerAnonFunctionsWithNames++;
        String innerName = "function%" + count;
        scope.setSymbolForScope(
            declareInferredSymbol(
                parent, innerName, scope.getRootNode()));
      }
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
  <S extends StaticSlot, R extends StaticRef>
  void addSymbolsFrom(StaticSymbolTable<S, R> otherSymbolTable) {
    for (S otherSymbol : otherSymbolTable.getAllSymbols()) {
      String name = otherSymbol.getName();
      SymbolScope myScope = createScopeFrom(
          otherSymbolTable.getScope(otherSymbol));

      StaticRef decl = findBestDeclToAdd(otherSymbolTable, otherSymbol);
      Symbol mySymbol = null;
      if (decl != null) {
        Node declNode = decl.getNode();

        // If we have a declaration node, we can ensure the symbol is declared.
        mySymbol = isAnySymbolDeclared(name, declNode, myScope);
        if (mySymbol == null) {
          mySymbol = copySymbolTo(otherSymbol, declNode, myScope);
        }
      } else {
        // If we don't have a declaration node, we won't be able to declare
        // a symbol in this symbol table. But we may be able to salvage the
        // references if we already have a symbol.
        mySymbol = myScope.getOwnSlot(name);
      }

      if (mySymbol != null) {
        for (R otherRef : otherSymbolTable.getReferences(otherSymbol)) {
          if (isGoodRefToAdd(otherRef)) {
            mySymbol.defineReferenceAt(otherRef.getNode());
          }
        }
      }
    }
  }

  /**
   * Checks if any symbol is already declared at the given node and scope
   * for the given name. If so, returns it.
   */
  private Symbol isAnySymbolDeclared(
      String name, Node declNode, SymbolScope scope) {
    Symbol sym = symbols.get(declNode, name);
    if (sym == null) {
      // Sometimes, our symbol tables will disagree on where the
      // declaration node should be. In the rare case where this happens,
      // trust the existing symbol.
      // See SymbolTableTest#testDeclarationDisagreement.
      return scope.ownSymbols.get(name);
    }
    return sym;
  }

  /** Helper for addSymbolsFrom, to determine the best declaration spot. */
  private <S extends StaticSlot, R extends StaticRef>
  StaticRef findBestDeclToAdd(StaticSymbolTable<S, R> otherSymbolTable, S slot) {
    StaticRef decl = slot.getDeclaration();
    if (isGoodRefToAdd(decl)) {
      return decl;
    }

    for (R ref : otherSymbolTable.getReferences(slot)) {
      if (isGoodRefToAdd(ref)) {
        return ref;
      }
    }

    return null;
  }

  /**
   * Helper for addSymbolsFrom, to determine whether a reference is
   * acceptable. A reference must be in the normal source tree.
   */
  private boolean isGoodRefToAdd(@Nullable StaticRef ref) {
    return ref != null && ref.getNode() != null
        && ref.getNode().getStaticSourceFile() != null
        && !Compiler.SYNTHETIC_EXTERNS.equals(
            ref.getNode().getStaticSourceFile().getName());
  }

  private Symbol copySymbolTo(StaticSlot sym, SymbolScope scope) {
    return copySymbolTo(sym, sym.getDeclaration().getNode(), scope);
  }

  private Symbol copySymbolTo(
      StaticSlot sym, Node declNode, SymbolScope scope) {
    // All symbols must have declaration nodes.
    Preconditions.checkNotNull(declNode);
    return declareSymbol(
        sym.getName(), getType(sym), isTypeInferred(sym), scope, declNode,
        sym.getJSDocInfo());
  }

  private Symbol addSymbol(
      String name, JSType type, boolean inferred, SymbolScope scope,
      Node declNode) {
    Symbol symbol = new Symbol(name, type, inferred, scope);
    Symbol replacedSymbol = symbols.put(declNode, name, symbol);
    Preconditions.checkState(
        replacedSymbol == null,
        "Found duplicate symbol %s in global index. Type %s", name, type);

    replacedSymbol = scope.ownSymbols.put(name, symbol);
    Preconditions.checkState(
        replacedSymbol == null,
        "Found duplicate symbol %s in its scope. Type %s", name, type);
    return symbol;
  }

  private Symbol declareSymbol(
      String name, JSType type, boolean inferred,
      SymbolScope scope, Node declNode, JSDocInfo info) {
    Symbol symbol = addSymbol(name, type, inferred, scope, declNode);
    symbol.setJSDocInfo(info);
    symbol.setDeclaration(symbol.defineReferenceAt(declNode));
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
    for (Symbol symbol : getAllSymbolsSorted()) {
      String qName = symbol.getName();
      int rootIndex = qName.indexOf('.');
      if (rootIndex == -1) {
        continue;
      }

      Symbol root = symbol.scope.getQualifiedSlot(
          qName.substring(0, rootIndex));
      if (root == null) {
        // In theory, this should never happen, but we fail quietly anyway
        // just to be safe.
        continue;
      }

      for (Reference ref : getReferences(symbol)) {
        Node currentNode = ref.getNode();
        if (!currentNode.isQualifiedName()) {
          continue;
        }

        while (currentNode.isGetProp()) {
          currentNode = currentNode.getFirstChild();

          String name = currentNode.getQualifiedName();
          if (name != null) {
            Symbol namespace =
                isAnySymbolDeclared(name, currentNode, root.scope);
            if (namespace == null) {
              namespace = root.scope.getQualifiedSlot(name);
            }

            if (namespace == null && root.scope.isGlobalScope()) {
              namespace = declareSymbol(name,
                  registry.getNativeType(JSTypeNative.UNKNOWN_TYPE),
                  true,
                  root.scope,
                  currentNode,
                  null /* JsDoc info */);
            }

            if (namespace != null) {
              namespace.defineReferenceAt(currentNode);
            }
          }
        }
      }
    }
  }

  void fillPropertyScopes() {
    // Collect all object symbols.
    List<Symbol> types = new ArrayList<>();

    // Create a property scope for each named type and each anonymous object,
    // and populate it with that object's properties.
    //
    // We notably don't want to create a property scope for 'x' in
    // var x = new Foo();
    // where x is just an instance of another type.
    for (Symbol sym : getAllSymbols()) {
      if (needsPropertyScope(sym)) {
        types.add(sym);
      }
    }

    // The order of operations here is significant.
    //
    // When we add properties to Foo, we'll remove Foo.prototype from
    // the symbol table and replace it with a fresh symbol in Foo's
    // property scope. So the symbol for Foo.prototype in
    // {@code instances} will be stale.
    //
    // To prevent this, we sort the list by the reverse of the
    // default symbol order, which will do the right thing.
    Collections.sort(types, getNaturalSymbolOrdering().reverse());
    for (Symbol s : types) {
      createPropertyScopeFor(s);
    }

    pruneOrphanedNames();
  }

  private boolean needsPropertyScope(Symbol sym) {
    ObjectType type = ObjectType.cast(getType(sym));
    if (type == null) {
      return false;
    }

    // Anonymous objects
    if (type.getReferenceName() == null) {
      return true;
    }

    // Constructors/prototypes
    // Should this check for
    // (type.isNominalConstructor() || type.isFunctionPrototypeType())
    // ?
    if (sym.getName().equals(type.getReferenceName())) {
      return true;
    }

    // Enums
    return type.isEnumType()
        && sym.getName().equals(type.toMaybeEnumType().getElementsType().getReferenceName());
  }

  /**
   * Removes symbols where the namespace they're on has been removed.
   *
   * After filling property scopes, we may have two symbols represented
   * in different ways. For example, "A.superClass_.foo" and B.prototype.foo".
   *
   * This resolves that ambiguity by pruning the duplicates.
   * If we have a lexical symbol with a constructor in its property
   * chain, then we assume there's also a property path to this symbol.
   * In other words, we can remove "A.superClass_.foo" because it's rooted
   * at "A", and we built a property scope for "A" above.
   */
  void pruneOrphanedNames() {
    nextSymbol: for (Symbol s : getAllSymbolsSorted()) {
      if (s.isProperty()) {
        continue;
      }

      String currentName = s.getName();
      int dot = -1;
      while (-1 != (dot = currentName.lastIndexOf('.'))) {
        currentName = currentName.substring(0, dot);

        Symbol owner = s.scope.getQualifiedSlot(currentName);
        if (owner != null
            && getType(owner) != null
            && (getType(owner).isNominalConstructor() ||
                getType(owner).isFunctionPrototypeType() ||
                getType(owner).isEnumType())) {
          removeSymbol(s);
          continue nextSymbol;
        }
      }
    }
  }

  /**
   * Create symbols and references for all properties of types in
   * this symbol table.
   *
   * This gets a little bit tricky, because of the way this symbol table
   * conflates "type Foo" and "the constructor of type Foo". So if you
   * have:
   *
   * <code>
   * SymbolTable symbolTable = for("var x = new Foo();");
   * Symbol x = symbolTable.getGlobalScope().getSlot("x");
   * Symbol type = symbolTable.getAllSymbolsForType(getType(x)).get(0);
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
    (new PropertyRefCollector(compiler)).process(externs, root);
  }

  /** Index JSDocInfo. */
  void fillJSDocInfo(
      AbstractCompiler compiler, Node externs, Node root) {
    NodeTraversal.traverseRoots(
        compiler, new JSDocInfoCollector(compiler.getTypeRegistry()), externs, root);

    // Create references to parameters in the JSDoc.
    for (Symbol sym : getAllSymbolsSorted()) {
      JSDocInfo info = sym.getJSDocInfo();
      if (info == null) {
        continue;
      }

      for (Marker marker : info.getMarkers()) {
        SourcePosition<Node> pos = marker.getNameNode();
        if (pos == null) {
          continue;
        }

        Node paramNode = pos.getItem();
        String name = paramNode.getString();
        Symbol param = getParameterInFunction(sym, name);
        if (param == null) {
          // There is no reference to this parameter in the actual JavaScript
          // code, so we'll try to create a special JsDoc-only symbol in
          // a JsDoc-only scope.
          SourcePosition<Node> typePos = marker.getType();
          JSType type = null;
          if (typePos != null) {
            type = typePos.getItem().getJSType();
          }

          if (sym.docScope == null) {
            sym.docScope = new SymbolScope(null /* root */,
                null /* parent scope */, null /* type of this */, sym);
          }

          // Check to make sure there's no existing symbol. In theory, this
          // should never happen, but we check anyway and fail silently
          // if our assumptions are wrong. (We do not want to put the symbol
          // table into an invalid state).
          Symbol existingSymbol =
              isAnySymbolDeclared(name, paramNode, sym.docScope);
          if (existingSymbol == null) {
            declareSymbol(name, type, type == null, sym.docScope, paramNode,
                null /* info */);
          }
        } else {
          param.defineReferenceAt(paramNode);
        }
      }
    }
  }

  /** Records the visibility of each symbol. */
  void fillSymbolVisibility(
      AbstractCompiler compiler, Node externs, Node root) {
        CollectFileOverviewVisibility collectPass =
        new CollectFileOverviewVisibility(compiler);
    collectPass.process(externs, root);
    ImmutableMap<StaticSourceFile, Visibility> visibilityMap =
        collectPass.getFileOverviewVisibilityMap();
    NodeTraversal.traverseRoots(
        compiler,
        new VisibilityCollector(visibilityMap, compiler.getCodingConvention()),
        externs, root);
  }

  /**
   * Build a property scope for the given symbol. Any properties of the symbol
   * will be added to the property scope.
   *
   * It is important that property scopes are created in order from the leaves
   * up to the root, so this should only be called from #fillPropertyScopes.
   * If you try to create a property scope for a parent before its leaf,
   * then the leaf will get cut and re-added to the parent property scope,
   * and weird things will happen.
   */
  private void createPropertyScopeFor(Symbol s) {
    // In order to build a property scope for s, we will need to build
    // a property scope for all its implicit prototypes first. This means
    // that sometimes we will already have built its property scope
    // for a previous symbol.
    if (s.propertyScope != null) {
      return;
    }

    SymbolScope parentPropertyScope = null;
    ObjectType type = getType(s) == null ? null : getType(s).toObjectType();
    if (type == null) {
      return;
    }

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
      // Guard against modifying foo.prototype when foo is a regular (non-constructor) function.
      if (instanceType.getOwnerFunction().hasInstanceType()) {
        // Merge the properties of "Foo.prototype" and "new Foo()" together.
        instanceType = instanceType.getOwnerFunction().getInstanceType();
        propNames = Iterables.concat(propNames, instanceType.getOwnPropertyNames());
      }
    }

    s.setPropertyScope(new SymbolScope(null, parentPropertyScope, type, s));
    for (String propName : propNames) {
      StaticSlot newProp = instanceType.getSlot(propName);
      if (newProp.getDeclaration() == null) {
        // Skip properties without declarations. We won't know how to index
        // them, because we index things by node.
        continue;
      }

      // We have symbol tables that do not do type analysis. They just try
      // to build a complete index of all objects in the program. So we might
      // already have symbols for things like "Foo.bar". If this happens,
      // throw out the old symbol and use the type-based symbol.
      Symbol oldProp = symbols.get(newProp.getDeclaration().getNode(),
          s.getName() + "." + propName);
      if (oldProp != null) {
        removeSymbol(oldProp);
      }

      // If we've already have an entry in the table for this symbol,
      // then skip it. This should only happen if we screwed up,
      // and declared multiple distinct properties with the same name
      // at the same node. We bail out here to be safe.
      if (symbols.get(newProp.getDeclaration().getNode(),
              newProp.getName()) != null) {
        logger.fine("Found duplicate symbol " + newProp);
        continue;
      }

      Symbol newSym = copySymbolTo(newProp, s.propertyScope);
      if (oldProp != null) {
        if (newSym.getJSDocInfo() == null) {
          newSym.setJSDocInfo(oldProp.getJSDocInfo());
        }
        newSym.setPropertyScope(oldProp.propertyScope);
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
  private SymbolScope createScopeFrom(StaticScope otherScope) {
    Node otherScopeRoot = otherScope.getRootNode();
    SymbolScope myScope = scopes.get(otherScopeRoot);
    if (myScope == null) {
      StaticScope otherScopeParent = otherScope.getParentScope();

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
          getTypeOfThis(otherScope),
          null);
      scopes.put(otherScopeRoot, myScope);
      if (myScope.isGlobalScope()) {
        globalScope = myScope;
      }
    }
    return myScope;
  }

  /** A symbol-table entry */
  public static final class Symbol extends SimpleSlot {
    // Use a linked hash map, so that the results are deterministic
    // (and so the declaration always comes first).
    private final Map<Node, Reference> references = new LinkedHashMap<>();

    private final SymbolScope scope;

    private SymbolScope propertyScope = null;

    private Reference declaration = null;

    private JSDocInfo docInfo = null;

    /**
     * Stored separately from {@link #docInfo}, because the visibility stored
     * in JSDocInfo is not necessarily authoritative.
     */
    @Nullable private Visibility visibility = null;

    // A scope for symbols that are only documented in JSDoc.
    private SymbolScope docScope = null;

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

    public Reference defineReferenceAt(Node n) {
      Reference result = references.get(n);
      if (result == null) {
        result = new Reference(this, n);
        references.put(n, result);
      }
      return result;
    }

    /** Sets the declaration node. May only be called once. */
    void setDeclaration(Reference ref) {
      Preconditions.checkState(this.declaration == null);
      this.declaration = ref;
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

    void setPropertyScope(SymbolScope scope) {
      this.propertyScope = scope;
      if (scope != null) {
        this.propertyScope.setSymbolForScope(this);
      }
    }

    @Override
    public JSDocInfo getJSDocInfo() {
      return docInfo;
    }

    void setJSDocInfo(JSDocInfo info) {
      this.docInfo = info;
    }

    @Nullable public Visibility getVisibility() {
      return this.visibility;
    }

    void setVisibility(Visibility v) {
      this.visibility = v;
    }

    /** Whether this is a property of another variable. */
    public boolean isProperty() {
      return scope.isPropertyScope();
    }

    /** Whether this is a variable in a lexical scope. */
    public boolean isLexicalVariable() {
      return scope.isLexicalScope();
    }

    /** Whether this is a variable that's only in JSDoc. */
    public boolean isDocOnlyParameter() {
      return scope.isDocScope();
    }

    @Override
    public String toString() {
      Node n = getDeclarationNode();
      int lineNo = n == null ? -1 : n.getLineno();
      return getName() + "@" + getSourceFileName() + ":" + lineNo;
    }
  }

  /** Reference */
  public static final class Reference extends SimpleReference<Symbol> {
    Reference(Symbol symbol, Node node) {
      super(symbol, node);
    }
  }

  /** Scope of a symbol */
  public static final class SymbolScope {
    private final Node rootNode;
    private final SymbolScope parent;
    private final JSType typeOfThis;
    private final Map<String, Symbol> ownSymbols = new LinkedHashMap<>();
    private final int scopeDepth;

    // The number of inner anonymous functions that we've given names to.
    private int innerAnonFunctionsWithNames = 0;

    // The symbol associated with a property scope or doc scope.
    private Symbol mySymbol;

    SymbolScope(
        Node rootNode,
        @Nullable SymbolScope parent,
        JSType typeOfThis,
        Symbol mySymbol) {
      this.rootNode = rootNode;
      this.parent = parent;
      this.typeOfThis = typeOfThis;
      this.scopeDepth = parent == null ? 0 : (parent.getScopeDepth() + 1);
      this.mySymbol = mySymbol;
    }

    Symbol getSymbolForScope() {
      return mySymbol;
    }

    void setSymbolForScope(Symbol sym) {
      this.mySymbol = sym;
    }

    /** Gets a unique index for the symbol in this scope. */
    public int getIndexOfSymbol(Symbol sym) {
      return Iterables.indexOf(
          ownSymbols.values(), Predicates.equalTo(sym));
    }

    Node getRootNode() {
      return rootNode;
    }

    public SymbolScope getParentScope() {
      return parent;
    }

    /**
     * Get the slot for a fully-qualified name (e.g., "a.b.c") by trying
     * to find property scopes at each part of the path.
     */
    public Symbol getQualifiedSlot(String name) {
      Symbol fullyNamedSym = getSlot(name);
      if (fullyNamedSym != null) {
        return fullyNamedSym;
      }

      int dot = name.lastIndexOf('.');
      if (dot != -1) {
        Symbol owner = getQualifiedSlot(name.substring(0, dot));
        if (owner != null && owner.getPropertyScope() != null) {
          return owner.getPropertyScope().getSlot(name.substring(dot + 1));
        }
      }

      return null;
    }

    public Symbol getSlot(String name) {
      Symbol own = getOwnSlot(name);
      if (own != null) {
        return own;
      }

      Symbol ancestor = parent == null ? null : parent.getSlot(name);
      if (ancestor != null) {
        return ancestor;
      }
      return null;
    }

    Symbol getOwnSlot(String name) {
      return ownSymbols.get(name);
    }

    public JSType getTypeOfThis() {
      return typeOfThis;
    }

    public boolean isGlobalScope() {
      return getParentScope() == null && getRootNode() != null;
    }

    /**
     * Returns whether this is a doc scope. A doc scope is a table for symbols
     * that are documented solely within a JSDoc comment.
     */
    public boolean isDocScope() {
      return getRootNode() == null && mySymbol != null &&
          mySymbol.docScope == this;
    }

    public boolean isPropertyScope() {
      return getRootNode() == null && !isDocScope();
    }

    public boolean isLexicalScope() {
      return getRootNode() != null;
    }

    public int getScopeDepth() {
      return scopeDepth;
    }

    @Override
    public String toString() {
      Node n = getRootNode();
      if (n != null) {
        return "Scope@" + n.getSourceFileName() + ":" + n.getLineno();
      } else {
        return "PropertyScope@" + getSymbolForScope();
      }
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
      NodeTraversal.traverseRoots(compiler, this, externs, root);
    }

    private boolean maybeDefineReference(
        Node n, String propName, Symbol ownerSymbol) {
      // getPropertyScope() will be null in some rare cases where there
      // are no extern declarations for built-in types (like Function).
      if (ownerSymbol != null && ownerSymbol.getPropertyScope() != null) {
        Symbol prop = ownerSymbol.getPropertyScope().getSlot(propName);
        if (prop != null) {
          prop.defineReferenceAt(n);
          return true;
        }
      }
      return false;
    }

    // Try to find the symbol by its fully qualified name.
    private boolean tryDefineLexicalQualifiedNameRef(String name, Node n) {
      if (name != null) {
        Symbol lexicalSym = getEnclosingScope(n).getQualifiedSlot(name);
        if (lexicalSym != null) {
          lexicalSym.defineReferenceAt(n);
          return true;
        }
      }
      return false;
    }

    // Try to remove a reference by its fully qualified name.
    // If the symbol has no references left, remove it completely.
    private void tryRemoveLexicalQualifiedNameRef(String name, Node n) {
      if (name != null) {
        Symbol lexicalSym = getEnclosingScope(n).getQualifiedSlot(name);
        if (lexicalSym != null &&
            lexicalSym.isLexicalVariable() &&
            lexicalSym.getDeclaration().getNode() == n) {
          removeSymbol(lexicalSym);
        }
      }
    }

    private boolean maybeDefineTypedReference(
        Node n, String propName, JSType owner) {
      if (owner.isGlobalThisType()) {
        Symbol sym = globalScope.getSlot(propName);
        if (sym != null) {
          sym.defineReferenceAt(n);
          return true;
        }
      } else if (owner.isNominalConstructor()) {
        return maybeDefineReference(
            n, propName, getSymbolDeclaredBy(owner.toMaybeFunctionType()));
      } else if (owner.isEnumType()) {
        return maybeDefineReference(
            n, propName, getSymbolDeclaredBy(owner.toMaybeEnumType()));
      } else {
        boolean defined = false;
        for (Symbol ctor : getAllSymbolsForType(owner)) {
          if (maybeDefineReference(
                  n, propName, getSymbolForInstancesOf(ctor))) {
            defined = true;
          }
        }
        return defined;
      }
      return false;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      // There are two ways to define a property reference:
      // 1) As a fully qualified lexical symbol (e.g., x.y)
      // 2) As a property of another object (e.g., x's y)
      // Property definitions should take precedence over lexical
      // definitions. e.g., for "a.b", it's more useful to record
      // this as "property b of the type of a", than as "symbol a.b".

      if (n.isGetProp()) {
        JSType owner = n.getFirstChild().getJSType();
        if (owner != null) {
          boolean defined = maybeDefineTypedReference(
              n, n.getLastChild().getString(), owner);

          if (defined) {
            tryRemoveLexicalQualifiedNameRef(n.getQualifiedName(), n);
            return;
          }
        }

        tryDefineLexicalQualifiedNameRef(n.getQualifiedName(), n);
      } else if (n.isStringKey()) {
        JSType owner = parent.getJSType();
        if (owner != null) {
          boolean defined =
              maybeDefineTypedReference(n, n.getString(), owner);

          if (defined) {
            tryRemoveLexicalQualifiedNameRef(
                NodeUtil.getBestLValueName(n), n);
            return;
          }
        }

        tryDefineLexicalQualifiedNameRef(
            NodeUtil.getBestLValueName(n), n);
      }
    }
  }

  private class ThisRefCollector
      extends NodeTraversal.AbstractScopedCallback
      implements CompilerPass {
    private final AbstractCompiler compiler;

    // The 'this' symbols in the current scope chain.
    //
    // If we don't know how to declare 'this' in a scope chain,
    // then null should be on the stack. But this should be a rare
    // occurrence. We should strive to always be able to come up
    // with some symbol for 'this'.
    private final List<Symbol> thisStack = new ArrayList<>();

    ThisRefCollector(AbstractCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
      NodeTraversal.traverseRoots(compiler, this, externs, root);
    }

    @Override
    public void enterScope(NodeTraversal t) {
      Symbol symbol = null;
      if (t.inGlobalScope()) {
        // Declare the global this at the first input root.
        // This is a bizarre place to put it, but we need some
        // location with a real file path (because all symbols
        // must have a path).
        // Note that root.lastChild.firstChild is the first non-extern input.
        Node firstInputRoot = t.getScopeRoot().getLastChild().getFirstChild();
        if (firstInputRoot != null) {
          symbol = addSymbol(
              GLOBAL_THIS,
              registry.getNativeType(JSTypeNative.GLOBAL_THIS),
              false /* declared */,
              globalScope,
              firstInputRoot);
          symbol.setDeclaration(new Reference(symbol, firstInputRoot));
        }
      } else {
        // Otherwise, declare a "this" property when possible.
        SymbolScope scope = scopes.get(t.getScopeRoot());
        Preconditions.checkNotNull(scope);
        Symbol scopeSymbol = getSymbolForScope(scope);
        if (scopeSymbol != null) {
          SymbolScope propScope = scopeSymbol.getPropertyScope();
          if (propScope != null) {
            // If a function is assigned multiple times, we only want
            // one addressable "this" symbol.
            symbol = propScope.getOwnSlot("this");
            if (symbol == null) {
              JSType rootType = t.getScopeRoot().getJSType();
              FunctionType fnType = rootType == null
                  ? null : rootType.toMaybeFunctionType();
              JSType type = fnType == null
                  ? null : fnType.getTypeOfThis();
              symbol = addSymbol(
                  "this",
                  type,
                  false /* declared */,
                  scope,
                  t.getScopeRoot());
            }

            // TODO(nicksantos): It's non-obvious where the declaration of
            // the 'this' symbol should be. Figure this out later.
          }
        }
      }

      thisStack.add(symbol);
    }

    @Override
    public void exitScope(NodeTraversal t) {
      thisStack.remove(thisStack.size() - 1);
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!n.isThis()) {
        return;
      }

      Symbol symbol = Iterables.getLast(thisStack);
      if (symbol != null) {
        Reference ref = symbol.defineReferenceAt(n);
        if (symbol.getDeclaration() == null) {
          symbol.setDeclaration(ref);
        }
      }
    }
  }

  /** Collects references to types in JSDocInfo. */
  private class JSDocInfoCollector
      extends NodeTraversal.AbstractPostOrderCallback {
    private final JSTypeRegistry typeRegistry;

    private JSDocInfoCollector(JSTypeRegistry registry) {
      this.typeRegistry = registry;
    }

    @Override public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.getJSDocInfo() != null) {

        // Find references in the JSDocInfo.
        JSDocInfo info = n.getJSDocInfo();
        docInfos.add(n);

        for (Node typeAst : info.getTypeNodes()) {
          SymbolScope scope = scopes.get(t.getScopeRoot());
          visitTypeNode(
              n,
              info.getTemplateTypeNames(),
              scope == null ? globalScope : scope,
              typeAst);
        }
      }
    }

    private boolean isNativeSourcelessType(String name) {
      switch (name) {
        case "null":
        case "undefined":
        case "void":
          return true;

        default:
          return false;
      }
    }

    public void visitTypeNode(Node refNode, ImmutableList<String> templateTypeNames,
        SymbolScope scope, Node n) {
      if (n.isString()
          && !isNativeSourcelessType(n.getString())
          && !templateTypeNames.contains(n.getString())) {
        Symbol symbol = lookupPossiblyDottedName(scope, n.getString());
        if (symbol != null) {
          symbol.defineReferenceAt(n);
        }
      }

      for (Node child = n.getFirstChild();
           child != null; child = child.getNext()) {
        visitTypeNode(refNode, templateTypeNames, scope, child);
      }
    }

    // TODO(peterhal): @template types.
    private Symbol lookupPossiblyDottedName(SymbolScope scope, String dottedName) {
      // Try the dotted name to start.
      String[] names = dottedName.split("\\.");
      Symbol result = null;
      SymbolScope currentScope = scope;
      for (int i = 0; i < names.length; i++) {
        String name = names[i];
        result = currentScope.getSlot(name);
        if (result == null) {
          break;
        }
        if (i < (names.length - 1)) {
          currentScope = result.getPropertyScope();
          if (currentScope == null) {
            result = null;
            break;
          }
        }
      }

      if (result == null) {
        // If we can't find this type, it might be a reference to a
        // primitive type (like {string}). Autobox it to check.
        JSType type = typeRegistry.getType(dottedName);
        JSType autobox = type == null ? null : type.autoboxesTo();
        result = autobox == null
            ? null : getSymbolForTypeHelper(autobox, true);
      }
      return result;
    }
  }

  /** Collects the visibility information for each name/property. */
  private class VisibilityCollector
      extends NodeTraversal.AbstractPostOrderCallback {
    private final ImmutableMap<StaticSourceFile, Visibility> fileVisibilityMap;
    private final CodingConvention codingConvention;

    private VisibilityCollector(
        ImmutableMap<StaticSourceFile, Visibility> fileVisibilityMap,
        CodingConvention codingConvention) {
      this.fileVisibilityMap = fileVisibilityMap;
      this.codingConvention = codingConvention;
    }

    @Override public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName()) {
        visitName(t, n, parent);
      } else if (n.isGetProp()) {
        visitProperty(t, n, parent);
      }
    }

    private void visitName(NodeTraversal t, Node n, Node parent) {
      Symbol symbol = symbols.get(n, n.getString());
      if (symbol == null) {
        return;
      }
      // Visibility already set.
      if (symbol.getVisibility() != null) {
        return;
      }
      Var var = t.getScope().getVar(n.getString());
      if (var == null) {
        return;
      }
      Visibility v = AccessControlUtils.getEffectiveNameVisibility(
          n ,var, fileVisibilityMap);
      if (v == null) {
        return;
      }
      symbol.setVisibility(v);
    }

    private void visitProperty(NodeTraversal t, Node getprop, Node parent) {
      String propertyName = getprop.getLastChild().getString();
      Symbol symbol = symbols.get(getprop, propertyName);
      if (symbol == null) {
        return;
      }
      // Visibility already set.
      if (symbol.getVisibility() != null) {
        return;
      }
      JSType jsType = getprop.getFirstChild().getJSType();
      if (jsType == null) {
        return;
      }
      boolean isOverride = parent.getJSDocInfo() != null
          && parent.isAssign()
          && parent.getFirstChild() == getprop;
      if (isOverride) {
        // Don't bother with AccessControlUtils for overridden properties.
        // AccessControlUtils currently has complicated logic for detecting
        // visibility mismatches for overridden properties that is still
        // too tightly coupled to CheckAccessControls. TODO(brndn): simplify.
        symbol.setVisibility(Visibility.INHERITED);
      } else {
        ObjectType referenceType = ObjectType.cast(jsType.dereference());
        Visibility v = AccessControlUtils.getEffectivePropertyVisibility(
            getprop,
            referenceType,
            fileVisibilityMap,
            codingConvention);
        if (v == null) {
          return;
        }
        symbol.setVisibility(v);
      }
    }
  }

  // Comparators
  private final Ordering<String> sourceNameOrdering =
      Ordering.natural().nullsFirst();

  private final Ordering<Node> nodeOrdering = new Ordering<Node>() {
    @Override
    public int compare(Node a, Node b) {
      int result = sourceNameOrdering.compare(
          a.getSourceFileName(), b.getSourceFileName());
      if (result != 0) {
        return result;
      }

      // Source position is a bit mask of line in the top 4 bits, so this
      // is a quick way to compare order without computing absolute position.
      return a.getSourcePosition() - b.getSourcePosition();
    }
  };

  private final Ordering<SymbolScope> lexicalScopeOrdering =
      new Ordering<SymbolScope>() {
    @Override
    public int compare(SymbolScope a, SymbolScope b) {
      Preconditions.checkState(a.isLexicalScope() && b.isLexicalScope(),
                               "We can only sort lexical scopes");
      return nodeOrdering.compare(a.getRootNode(), b.getRootNode());
    }
  };

  private final Ordering<Symbol> symbolOrdering = new Ordering<Symbol>() {
    @Override
    public int compare(Symbol a, Symbol b) {
      SymbolScope scopeA = getScope(a);
      SymbolScope scopeB = getScope(b);

      // More deeply nested symbols should go later.
      int result = getLexicalScopeDepth(scopeA) - getLexicalScopeDepth(scopeB);
      if (result != 0) {
        return result;
      }

      // After than, just use lexicographic ordering.
      // This ensures "a.b" comes before "a.b.c".
      return a.getName().compareTo(b.getName());
    }
  };

  /**
   * For a lexical scope, just returns the normal scope depth.
   *
   * For a property scope, returns the number of scopes we have to search
   *     to find the nearest lexical scope, plus that lexical scope's depth.
   *
   * For a doc info scope, returns 0.
   */
  private int getLexicalScopeDepth(SymbolScope scope) {
    if (scope.isLexicalScope() || scope.isDocScope()) {
      return scope.getScopeDepth();
    } else {
      Preconditions.checkState(scope.isPropertyScope());
      Symbol sym = scope.getSymbolForScope();
      Preconditions.checkNotNull(sym);
      return getLexicalScopeDepth(getScope(sym)) + 1;
    }
  }

  private JSType getType(StaticSlot sym) {
    if (sym instanceof StaticTypedSlot) {
      return ((StaticTypedSlot<JSType>) sym).getType();
    }
    return null;
  }

  private JSType getTypeOfThis(StaticScope s) {
    if (s instanceof StaticTypedScope) {
      return ((StaticTypedScope<JSType>) s).getTypeOfThis();
    }
    return null;
  }

  private boolean isTypeInferred(StaticSlot sym) {
    if (sym instanceof StaticTypedSlot) {
      return ((StaticTypedSlot<JSType>) sym).isTypeInferred();
    }
    return true;
  }
}
