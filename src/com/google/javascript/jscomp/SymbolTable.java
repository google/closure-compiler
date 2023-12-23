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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.common.collect.Table;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.base.format.SimpleFormat;
import com.google.javascript.jscomp.modules.Module;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleType;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Marker;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.JSTypeExpression;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.nullness.Nullable;

/**
 * A symbol table for people that want to use Closure Compiler as an indexer.
 *
 * <p>Contains an index of all the symbols in the code within a compilation job. The API is designed
 * for people who want to visit all the symbols, rather than people who want to lookup a specific
 * symbol by a certain key.
 *
 * <p>We can use this to combine different types of symbol tables. For example, one class might have
 * a {@code StaticSymbolTable} of all variable references, and another class might have a {@code
 * StaticSymbolTable} of all type names in JSDoc comments. This class allows you to combine them
 * into a unified index.
 *
 * <p>Most passes build their own "partial" symbol table that implements the same interface
 * (StaticSymbolTable, StaticSlot, and friends). Individual compiler passes usually need more or
 * less metadata about the certainty of symbol information. Building a complete symbol table with
 * all the necessary metadata for all passes would be too slow. However, as long as these "partial"
 * symbol tables implement the proper interfaces, we should be able to add them to this symbol table
 * to make it more complete.
 *
 * <p>If clients want fast lookup, they should build their own wrapper around this symbol table that
 * indexes symbols or references by the desired lookup key.
 *
 * <p>By design, when this symbol table creates symbols for types, it tries to mimic the symbol
 * table you would get in an OO language. For example, the "type Foo" and "the constructor that
 * creates objects of type Foo" are the same symbol. The types of "Foo.prototype" and "new Foo()"
 * also have the same symbol. Although JSCompiler internally treats these as distinct symbols, we
 * assume that most clients will not care about the distinction.
 *
 * @see #addSymbolsFrom For more information on how to write plugins for this symbol table.
 */
public final class SymbolTable {
  private static final Logger logger = Logger.getLogger(SymbolTable.class.getName());

  /**
   * The name we use for the JavaScript built-in Global object. It's anonymous in JavaScript, so we
   * have to give it an invalid identifier to avoid conflicts with user-defined property names.
   */
  public static final String GLOBAL_THIS = "*global*";

  /**
   * All symbols in the program, uniquely identified by the node where they're declared and their
   * name.
   */
  private final Table<Node, String, Symbol> symbols = HashBasedTable.create();

  /**
   * All syntactic scopes in the program, uniquely identified by the node where they're declared.
   */
  private final Map<Node, SymbolScope> scopes = new LinkedHashMap<>();

  /** All Nodes with JSDocInfo in the program. */
  private final List<Node> docInfos = new ArrayList<>();

  private @Nullable SymbolScope globalScope = null;

  private final AbstractCompiler compiler;

  private final JSTypeRegistry registry;

  /** Clients should get a symbol table by asking the compiler at the end of a compilation job. */
  SymbolTable(AbstractCompiler compiler, JSTypeRegistry registry) {
    this.compiler = compiler;
    this.registry = registry;
  }

  public Iterable<Reference> getReferences(Symbol symbol) {
    return Collections.unmodifiableCollection(symbol.references.values());
  }

  public ImmutableList<Reference> getReferenceList(Symbol symbol) {
    return ImmutableList.copyOf(symbol.references.values());
  }

  public ImmutableList<Symbol> getAllSymbols() {
    return ImmutableList.copyOf(symbols.values());
  }

  /** Get the symbols in their natural ordering. Always returns a mutable list. */
  public List<Symbol> getAllSymbolsSorted() {
    return getNaturalSymbolOrdering().sortedCopy(symbols.values());
  }

  /**
   * Gets the 'natural' ordering of symbols.
   *
   * <p>Right now, we only guarantee that symbols in the global scope will come before symbols in
   * local scopes. After that, the order is deterministic but undefined.
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
   * Gets the scope that contains the given node. If {@code n} is a function name, we return the
   * scope that contains the function, not the function itself.
   */
  public @Nullable SymbolScope getEnclosingScope(Node n) {
    Node current = n.getParent();
    if (n.isName() && n.getParent().isFunction()) {
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
   * Gets the scope that contains the given node. If {@code n} is a function name, we return the
   * scope that contains the function, not the function itself. The returned scope is either
   * function or global scope.
   */
  public SymbolScope getEnclosingFunctionScope(Node n) {
    Node current = n.getParent();
    if (n.isName() && current != null && current.isFunction()) {
      current = current.getParent();
    }

    for (; current != null; current = current.getParent()) {
      SymbolScope scope = scopes.get(current);
      if (scope != null && !scope.isBlockScope()) {
        return scope;
      }
    }
    return globalScope;
  }

  /**
   * If {@code sym} is a function, try to find a Symbol for a parameter with the given name.
   *
   * <p>Returns null if we couldn't find one.
   *
   * <p>Notice that this just makes a best effort, and may not be able to find parameters for
   * non-conventional function definitions. For example, we would not be able to find "y" in this
   * code: <code>
   * var x = x() ? function(y) {} : function(y) {};
   * </code>
   */
  public @Nullable Symbol getParameterInFunction(Symbol sym, String paramName) {
    SymbolScope scope = getScopeInFunction(sym);
    if (scope != null) {
      Symbol param = scope.getSlot(paramName);
      if (param != null && param.scope == scope) {
        return param;
      }
    }
    return null;
  }

  private @Nullable SymbolScope getScopeInFunction(Symbol sym) {
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
   * All local scopes are associated with a function, and some functions are associated with a
   * symbol. Returns the symbol associated with the given scope.
   */
  public Symbol getSymbolForScope(SymbolScope scope) {
    if (scope.getSymbolForScope() == null) {
      scope.setSymbolForScope(findSymbolForScope(scope));
    }
    return scope.getSymbolForScope();
  }

  /**
   * Find the symbol associated with the given scope. Notice that we won't always be able to figure
   * out this association dynamically, so sometimes we'll just create the association when we create
   * the scope.
   */
  private @Nullable Symbol findSymbolForScope(SymbolScope scope) {
    Node rootNode = scope.getRootNode();
    if (rootNode.getParent() == null) {
      return globalScope.getSlot(GLOBAL_THIS);
    }

    if (!rootNode.isFunction()) {
      return null;
    }

    String name = NodeUtil.getBestLValueName(NodeUtil.getBestLValue(rootNode));
    return name == null ? null : scope.getParentScope().getQualifiedSlot(name);
  }

  /**
   * Get all symbols associated with the type of the given symbol.
   *
   * <p>For example, given a variable x declared as /* @type {Array|Date} / var x = f(); this will
   * return the constructors for Array and Date.
   */
  public Iterable<Symbol> getAllSymbolsForTypeOf(Symbol sym) {
    return getAllSymbolsForType(getType(sym));
  }

  /** Returns the global scope. */
  public SymbolScope getGlobalScope() {
    return globalScope;
  }

  /** Gets the symbol for the given constructor or interface. */
  public Symbol getSymbolDeclaredBy(FunctionType fn) {
    checkState(fn.isConstructor() || fn.isInterface());
    ObjectType instanceType = fn.getInstanceType();
    return getSymbolForName(fn.getSource(), instanceType.getReferenceName());
  }

  /** Gets the symbol for the given enum. */
  public Symbol getSymbolDeclaredBy(EnumType enumType) {
    return getSymbolForName(enumType.getSource(), enumType.getElementsType().getReferenceName());
  }

  /** Gets the symbol for the prototype if this is the symbol for a constructor or interface. */
  public @Nullable Symbol getSymbolForInstancesOf(Symbol sym) {
    FunctionType fn = sym.getFunctionType();
    if (fn != null && fn.isNominalConstructorOrInterface()) {
      return getSymbolForInstancesOf(fn);
    }
    return null;
  }

  /** Gets the symbol for the prototype of the given constructor or interface. */
  public Symbol getSymbolForInstancesOf(FunctionType fn) {
    checkState(fn.isConstructor() || fn.isInterface());
    ObjectType pType = fn.getPrototype();
    return getSymbolForName(fn.getSource(), pType.getReferenceName());
  }

  private @Nullable Symbol getSymbolForName(@Nullable Node source, String name) {
    if (name == null || globalScope == null) {
      return null;
    }

    SymbolScope scope = source == null ? globalScope : getEnclosingScope(source);
    if (scope == null) {
      return null;
    }
    if (scope.isModuleScope()) {
      // In the case of elements like
      //
      // goog.module('foo.bar');
      // exports.Pinky = {};
      //
      // we might get fully-qualified name `foo.bar.Pinky` but when looking it up within the module
      // - the symbol is just `Pinky`. So we need to remove module name to be able to find the
      // symbol in module scope.
      String moduleNameWithDot =
          Iterables.getFirst(
                  compiler
                      .getModuleMetadataMap()
                      .getModulesByPath()
                      .get(scope.getRootNode().getSourceFileName())
                      .googNamespaces(),
                  null)
              + ".";
      if (name.startsWith(moduleNameWithDot)) {
        name = name.substring(moduleNameWithDot.length());
      }
    }

    return scope.getQualifiedSlot(name);
  }

  /**
   * Gets all symbols associated with the given type. For union types, this may be multiple symbols.
   * For instance types, this will return the constructor of that instance.
   */
  public List<Symbol> getAllSymbolsForType(JSType type) {
    if (type == null) {
      return ImmutableList.of();
    }

    if (type.isUnionType()) {
      ImmutableList.Builder<Symbol> result = ImmutableList.builder();
      for (JSType alt : type.toMaybeUnionType().getAlternates()) {
        // Our type system never has nested unions.
        Symbol altSym = getSymbolForTypeHelper(alt, true);
        if (altSym != null) {
          result.add(altSym);
        }
      }
      return result.build();
    }
    Symbol result = getSymbolForTypeHelper(type, true);
    return result == null ? ImmutableList.of() : ImmutableList.of(result);
  }

  /**
   * Gets all symbols associated with the given type. If there is more that one symbol associated
   * with the given type, return null.
   *
   * @param type The type.
   * @param linkToCtor If true, we should link instance types back to their constructor function. If
   *     false, we should link instance types back to their prototype. See the comments at the top
   *     of this file for more information on how our internal type system is more granular than
   *     Symbols.
   */
  private @Nullable Symbol getSymbolForTypeHelper(JSType type, boolean linkToCtor) {
    if (type == null) {
      return null;
    }

    if (type.isGlobalThisType()) {
      return globalScope.getSlot(GLOBAL_THIS);
    } else if (type.isNominalConstructorOrInterface()) {
      return linkToCtor
          ? globalScope.getSlot("Function")
          : getSymbolDeclaredBy(type.toMaybeFunctionType());
    } else if (type.isFunctionPrototypeType()) {
      FunctionType ownerFn = ((ObjectType) type).getOwnerFunction();
      if (!ownerFn.isConstructor() && !ownerFn.isInterface()) {
        return null;
      }
      return linkToCtor ? getSymbolDeclaredBy(ownerFn) : getSymbolForInstancesOf(ownerFn);
    } else if (type.isInstanceType()) {
      FunctionType ownerFn = ((ObjectType) type).getConstructor();
      return linkToCtor ? getSymbolDeclaredBy(ownerFn) : getSymbolForInstancesOf(ownerFn);
    } else if (type.isFunctionType()) {
      return linkToCtor
          ? globalScope.getSlot("Function")
          : globalScope.getQualifiedSlot("Function.prototype");
    } else if (type.autoboxesTo() != null) {
      return getSymbolForTypeHelper(type.autoboxesTo(), linkToCtor);
    } else if (type.isEnumType()) {
      return getSymbolDeclaredBy((EnumType) type);
    } else {
      return null;
    }
  }

  /**
   * Methods returns debug representation of the SymbolTable. It starts with the global scope and
   * all its symbols recursively going to children scopes and printing their symbols.
   */
  @SuppressWarnings("unused")
  public String toDebugStringTree() {
    // We are recursively going through scopes from global to nested and need to build parent =>
    // children map as it doesn't exist at the moment.
    ImmutableListMultimap.Builder<SymbolScope, SymbolScope> childrenScopesBuilder =
        ImmutableListMultimap.builder();
    for (SymbolScope scope : getAllScopes()) {
      if (scope.getParentScope() != null) {
        childrenScopesBuilder.put(scope.getParentScope(), scope);
      }
    }
    ImmutableListMultimap<SymbolScope, SymbolScope> childrenScopes = childrenScopesBuilder.build();
    StringBuilder builder = new StringBuilder();
    // Go through all scopes that don't have parent. It's likely only Global scope.
    for (SymbolScope scope : getAllScopes()) {
      if (scope.getParentScope() == null) {
        toDebugStringTree(builder, "", scope, childrenScopes);
      }
    }

    // All objects/classes have property scopes. They usually don't have parents. These scopes
    // confusingly are not included in "getAllScopes()" pass above.
    LinkedHashSet<SymbolScope> visitedScopes = new LinkedHashSet<>(getAllScopes());
    for (Symbol symbol : getAllSymbols()) {
      if (symbol.propertyScope == null || visitedScopes.contains(symbol.propertyScope)) {
        continue;
      }
      visitedScopes.add(symbol.getPropertyScope());
      toDebugStringTree(builder, "", symbol.propertyScope, childrenScopes);
    }
    return builder.toString();
  }

  private void toDebugStringTree(
      StringBuilder builder,
      String prefix,
      SymbolScope scope,
      ImmutableListMultimap<SymbolScope, SymbolScope> childrenScopes) {
    builder.append(prefix).append(scope);
    String childrenPrefix = prefix + "    ";
    boolean printedSomething = false;
    if (!scope.ownSymbols.isEmpty()) {
      printedSomething = true;
      builder.append('\n').append(prefix).append("  Symbols:\n");
      for (Symbol symbol : scope.ownSymbols.values()) {
        toDebugString(builder, childrenPrefix, symbol, /* printScope= */ false);
      }
    }
    if (childrenScopes.containsKey(scope)) {
      printedSomething = true;
      builder.append('\n').append(prefix).append("  Scopes:\n");
      for (SymbolScope childScope : childrenScopes.get(scope)) {
        toDebugStringTree(builder, childrenPrefix, childScope, childrenScopes);
      }
    }
    if (!printedSomething) {
      builder.append('\n');
    }
    builder.append('\n');
  }

  @SuppressWarnings("unused")
  public String toDebugString() {
    StringBuilder builder = new StringBuilder();
    for (Symbol symbol : getAllSymbols()) {
      toDebugString(builder, "", symbol, /* printScope= */ true);
    }
    return builder.toString();
  }

  private void toDebugString(
      StringBuilder builder, String prefix, Symbol symbol, boolean printScope) {
    builder.append(prefix).append("'").append(symbol.getName()).append("'");
    if (printScope) {
      builder.append(" in ").append(symbol.scope);
    }
    builder.append('\n');

    int refCount = 0;
    for (Reference ref : getReferences(symbol)) {
      Node node = ref.getNode();
      builder
          .append(prefix)
          .append(
              SimpleFormat.format(
                  "  Ref %d: %s line: %d col: %d len: %d %s\n",
                  refCount,
                  node.getSourceFileName(),
                  node.getLineno(),
                  node.getCharno(),
                  node.getLength(),
                  node.isIndexable() ? "" : "non indexable"));
      refCount++;
    }
  }

  /** Make sure all the given scopes in {@code otherSymbolTable} are in this symbol table. */
  <S extends StaticScope> void addScopes(Collection<S> scopes) {
    for (S scope : scopes) {
      createScopeFrom(scope);
    }
  }

  /** Finds all the scopes and adds them to this symbol table. */
  void findScopes(Node externs, Node root) {
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
        externs,
        root);
  }

  /** Gets all the scopes in this symbol table. */
  public Collection<SymbolScope> getAllScopes() {
    return Collections.unmodifiableCollection(scopes.values());
  }

  /**
   * Finds anonymous functions in local scopes, and gives them names and symbols. They will show up
   * as local variables with names "function%0", "function%1", etc.
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
      Node rootNode = scope.getRootNode();
      if (scope.isLexicalScope()
          && !scope.isGlobalScope()
          && rootNode != null
          && !rootNode.isFromExterns()
          && scope.getParentScope() != null
          && rootNode.isFunction()) {
        SymbolScope parent = scope.getParentScope();

        String innerName = "function%" + scope.getIndexInParent();
        JSType type = rootNode.getJSType();

        // Functions defined on anonymous objects are considered anonymous as well:
        // doFoo({bar() {}});
        // bar is not technically anonymous, but it's a method on an anonymous object literal so
        // effectively it's anonymous/inaccessible. In this case, slightly correct rootNode to
        // be a MEMBER_FUNCTION_DEF node instead of a FUNCTION node.
        if (rootNode.getParent().isMemberFunctionDef()) {
          rootNode = rootNode.getParent();
        }

        Symbol anonymousFunctionSymbol =
            declareSymbol(
                innerName, type, /* inferred= */ true, parent, rootNode, /* info= */ null);
        scope.setSymbolForScope(anonymousFunctionSymbol);
      }
    }
  }

  /**
   * Make sure all the symbols and references in {@code otherSymbolTable} are in this symbol table.
   *
   * <p>Uniqueness of symbols and references is determined by the associated node.
   *
   * <p>If multiple symbol tables are mixed in, we do not check for consistency between symbol
   * tables. The first symbol we see dictates the type information for that symbol.
   */
  <S extends StaticSlot, R extends StaticRef> void addSymbolsFrom(
      StaticSymbolTable<S, R> otherSymbolTable) {
    for (S otherSymbol : otherSymbolTable.getAllSymbols()) {
      String name = otherSymbol.getName();
      SymbolScope myScope = createScopeFrom(otherSymbolTable.getScope(otherSymbol));

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
   * Checks if any symbol is already declared at the given node and scope for the given name. If so,
   * returns it.
   */
  private Symbol isAnySymbolDeclared(String name, Node declNode, SymbolScope scope) {
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
  private <S extends StaticSlot, R extends StaticRef> @Nullable StaticRef findBestDeclToAdd(
      StaticSymbolTable<S, R> otherSymbolTable, S slot) {
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
   * Helper for addSymbolsFrom, to determine whether a reference is acceptable. A reference must be
   * in the normal source tree.
   */
  private boolean isGoodRefToAdd(@Nullable StaticRef ref) {
    return ref != null
        && ref.getNode() != null
        && ref.getNode().getStaticSourceFile() != null
        && !NodeUtil.isInSyntheticScript(ref.getNode())
        // Typechecking assigns implicitly goog.provided names a declaration node of the expr:
        // For example, 'some' and 'some.name' in 'goog.provide('some.name.child');' have their
        // declaration node set to the entire call node. Use the actual declaration instead.
        && !NodeUtil.isGoogProvideCall(ref.getNode());
  }

  private Symbol copySymbolTo(StaticSlot sym, SymbolScope scope) {
    return copySymbolTo(sym, sym.getDeclaration().getNode(), scope);
  }

  private Symbol copySymbolTo(StaticSlot sym, Node declNode, SymbolScope scope) {
    // All symbols must have declaration nodes.
    checkNotNull(declNode);
    return declareSymbol(
        sym.getName(), getType(sym), isTypeInferred(sym), scope, declNode, sym.getJSDocInfo());
  }

  /**
   * Replace all \ with \\ so there will be no \0 or \n in the string, then replace all '\0' (NULL)
   * with \0 and all '\n' (newline) with \n.
   */
  private static String sanitizeSpecialChars(String s) {
    return s.replace("\\", "\\\\").replace("\0", "\\0").replace("\n", "\\n");
  }

  private Symbol addSymbol(
      String name, JSType type, boolean inferred, SymbolScope scope, Node declNode) {
    name = sanitizeSpecialChars(name);

    Symbol symbol = new Symbol(name, type, inferred, scope);
    Symbol replacedSymbol = symbols.put(declNode, name, symbol);
    Preconditions.checkState(
        replacedSymbol == null, "Found duplicate symbol %s in global index. Type %s", name, type);

    replacedSymbol = scope.ownSymbols.put(name, symbol);
    Preconditions.checkState(
        replacedSymbol == null, "Found duplicate symbol %s in its scope. Type %s", name, type);
    return symbol;
  }

  private Symbol declareSymbol(
      String name,
      @Nullable JSType type,
      boolean inferred,
      SymbolScope scope,
      Node declNode,
      @Nullable JSDocInfo info) {
    Symbol symbol = addSymbol(name, type, inferred, scope, declNode);
    symbol.setJSDocInfo(info);
    symbol.setDeclaration(symbol.defineReferenceAt(declNode));
    return symbol;
  }

  /**
   * Merges 'from' symbol to 'to' symbol by moving all references to point to the 'to' symbol and
   * removing 'from' symbol.
   */
  private void mergeSymbol(Symbol from, Symbol to) {
    for (Node nodeToMove : from.references.keySet()) {
      if (!nodeToMove.equals(from.getDeclarationNode())) {
        to.defineReferenceAt(nodeToMove);
      }
    }
    removeSymbol(from);
  }

  private void removeSymbol(Symbol s) {
    SymbolScope scope = getScope(s);
    if (!s.equals(scope.ownSymbols.remove(s.getName()))) {
      throw new IllegalStateException("Symbol not found in scope " + s);
    }
    if (!s.equals(symbols.remove(s.getDeclaration().getNode(), s.getName()))) {
      throw new IllegalStateException("Symbol not found in table " + s);
    }
    // If s declares a property scope then all child symbols should be removed as well.
    // For example:
    // let foo = {a: 1, b: 2};
    // foo declares property scope with a and b as its children. When removing foo we should also
    // remove a and b.
    if (s.propertyScope != null && s.equals(s.propertyScope.getSymbolForScope())) {
      // Need to iterate over copy of values list because removeSymbol() will change the map
      // and we'll get ConcurrentModificationException
      for (Symbol childSymbol : ImmutableList.copyOf(s.propertyScope.ownSymbols.values())) {
        removeSymbol(childSymbol);
      }
      scopes.remove(s.getDeclarationNode());
    }
  }

  private void renameSymbol(SymbolScope scope, Symbol sym, String newName) {
    Preconditions.checkState(
        sym.propertyScope == null,
        "Renaming supported for simple symbols that don't have property scopes.");
    Symbol existingSym = scope.getSlot(newName);
    if (existingSym == null) {
      existingSym =
          declareSymbol(
              newName,
              getType(sym),
              isTypeInferred(sym),
              scope,
              sym.getDeclarationNode(),
              sym.getJSDocInfo());
    }
    mergeSymbol(sym, existingSym);
  }

  /**
   * Not all symbol tables record references to "namespace" objects. For example, if you have:
   * goog.dom.DomHelper = function() {}; The symbol table may not record that as a reference to
   * "goog.dom", because that would be redundant.
   */
  void fillNamespaceReferences() {
    for (Symbol symbol : getAllSymbols()) {
      String qName = symbol.getName();
      int rootIndex = qName.indexOf('.');
      if (rootIndex == -1) {
        continue;
      }

      Symbol root = symbol.scope.getQualifiedSlot(qName.substring(0, rootIndex));
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
          if (name == null) {
            continue;
          }
          Symbol namespace = isAnySymbolDeclared(name, currentNode, root.scope);
          if (namespace == null) {
            namespace = root.scope.getQualifiedSlot(name);
          }

          if (namespace == null && root.scope.isGlobalScope()) {
            // Originally UNKNOWN_TYPE has been always used for namespace symbols even though
            // compiler does have type information attached to a node. Unclear why. Changing code to
            // property type mostly works. It only fails on Foo.prototype cases for some reason.
            // It's pretty rare case when Foo.prototype defined in global scope though so for now
            // just carve it out.
            JSType nodeType = currentNode.getJSType();
            JSType symbolType =
                (nodeType != null && !nodeType.isFunctionPrototypeType())
                    ? nodeType
                    : registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
            namespace =
                declareSymbol(
                    name, symbolType, true, root.scope, currentNode, null /* JsDoc info */);
          }

          if (namespace != null) {
            namespace.defineReferenceAt(currentNode);
          }
        }
      }
    }
  }

  void fillPropertyScopes() {
    // Collect all object symbols.
    // All symbols that came from goog.module are collected separately because they will have to
    // be processed first. See explanation below.
    List<Symbol> types = new ArrayList<>();
    List<Symbol> exports = new ArrayList<>();

    // Create a property scope for each named type and each anonymous object,
    // and populate it with that object's properties.
    //
    // We notably don't want to create a property scope for 'x' in
    // var x = new Foo();
    // where x is just an instance of another type.
    for (Symbol sym : getAllSymbols()) {
      if (needsPropertyScope(sym)) {
        String name = sym.getName();
        if (name.equals("exports")) {
          exports.add(sym);
        } else {
          types.add(sym);
        }
      }
    }

    // The order of operations here is significant.
    //
    // When we add properties to Foo, we'll remove Foo.prototype from
    // the symbol table and replace it with a fresh symbol in Foo's
    // property scope. So the symbol for Foo.prototype in
    // {@code instances} will be stale.
    //
    // If we order them in reverse lexicographical order symbols x.y and x will be processed before
    // foo. This is wrong as foo is in fact property of x.y namespace. So we must process all
    // module$exports$ symbols first. That's why we collected them in a separate list.
    Collections.sort(types, getNaturalSymbolOrdering().reverse());
    Iterable<Symbol> allTypes = Iterables.concat(types, exports);

    // If you thought we are done with tricky case - you were wrong. There is another one!
    // The problem with the same property scope appearing several times. For example when using
    // aliases:
    //
    // const OBJ = {one: 1};
    // function() {
    //   const alias = OBJ;
    //   console.log(alias.one);
    // }
    //
    // In this case both 'OBJ' and 'alias' are considered property scopes and are candidates for
    // processing even though they share the same "type" which is "{one: 1}". As they share the same
    // type we need to process only one of them. To do that we build a "type => root symbol" map.
    // In this case the map will be {one: 1} => OBJ. Using this map will skip 'alias' when creating
    // property scopes.
    //
    // NOTE: we are using IdentityHashMap to compare types using == because we need to find symbols
    // that point to the exact same type instance.
    IdentityHashMap<JSType, Symbol> symbolThatDeclaresType = new IdentityHashMap<>();
    for (Symbol s : allTypes) {
      // Symbols are sorted in reverse order so that those with more outer scope will come later in
      // the list, and therefore override those set by aliases in more inner scope. The sorting
      // happens few lines above.
      JSType type = s.getType();
      Symbol symbolForType = getSymbolForTypeHelper(type, /* linkToCtor= */ false);
      if ((type.isNominalConstructorOrInterface()
              || type.isFunctionPrototypeType()
              || type.isEnumType())
          && !s.equals(symbolForType)) {
        // Some cases can't be handled by sorting. For example
        //
        // goog.module('some.Foo');
        // class Foo {}
        // exports = Foo;
        //
        // goog.module('some.bar');
        // const Foo = goog.require('some.Foo');
        //
        // Both Foo symbols (declaration and alias) have the same type and equal candidates to
        // become "symbol that declares Foo type". But in reality we should use the Foo in some.Foo
        // module. So if symbolForType exists and not equal to current symbol - it means it's an
        // alias.
        continue;
      }
      symbolThatDeclaresType.put(s.getType(), s);
    }

    for (Symbol s : allTypes) {
      // Create property scopes only based on "root" symbols for each type to handle aliases.
      if (s.getType() == null || s.equals(symbolThatDeclaresType.get(s.getType()))) {
        createPropertyScopeFor(s);
      }
    }

    // Now we need to set the new property scope symbol to all aliases.
    for (Symbol s : allTypes) {
      if (s.getType() != null && symbolThatDeclaresType.get(s.getType()) != null) {
        s.propertyScope = symbolThatDeclaresType.get(s.getType()).getPropertyScope();
      }
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
    if (type.isNominalConstructorOrInterface() || type.isFunctionPrototypeType()) {
      return true;
    }
    if (type.isEnumType()) {
      return true;
    }
    return false;
  }

  /**
   * Removes symbols where the namespace they're on has been removed.
   *
   * <p>After filling property scopes, we may have two symbols represented in different ways. For
   * example, "A.superClass_.foo" and B.prototype.foo".
   *
   * <p>This resolves that ambiguity by pruning the duplicates. If we have a lexical symbol with a
   * constructor in its property chain, then we assume there's also a property path to this symbol.
   * In other words, we can remove "A.superClass_.foo" because it's rooted at "A", and we built a
   * property scope for "A" above.
   */
  void pruneOrphanedNames() {
    nextSymbol:
    for (Symbol s : getAllSymbols()) {
      String currentName = s.getName();
      if (s.isProperty() || compiler.getModuleMap().getClosureModule(currentName) != null) {
        continue;
      }

      int dot = -1;
      while (-1 != (dot = currentName.lastIndexOf('.'))) {
        currentName = currentName.substring(0, dot);

        Symbol owner = s.scope.getQualifiedSlot(currentName);
        if (owner != null
            && getType(owner) != null
            && (getType(owner).isNominalConstructorOrInterface()
                || getType(owner).isFunctionPrototypeType()
                || getType(owner).isEnumType())) {
          removeSymbol(s);
          continue nextSymbol;
        }
      }
    }
  }

  /**
   * Create symbols and references for all properties of types in this symbol table.
   *
   * <p>This gets a little bit tricky, because of the way this symbol table conflates "type Foo" and
   * "the constructor of type Foo". So if you have: <code>
   * SymbolTable symbolTable = for("var x = new Foo();");
   * Symbol x = symbolTable.getGlobalScope().getSlot("x");
   * Symbol type = symbolTable.getAllSymbolsForType(getType(x)).get(0);
   * </code> Then type.getPropertyScope() will have the properties of the constructor "Foo". To get
   * the properties of instances of "Foo", you will need to call: <code>
   * Symbol instance = symbolTable.getSymbolForInstancesOf(type);
   * </code> As described at the top of this file, notice that "new Foo()" and "Foo.prototype" are
   * represented by the same symbol.
   */
  void fillPropertySymbols(Node externs, Node root) {
    new PropertyRefCollector().process(externs, root);
  }

  /** Index JSDocInfo. */
  void fillJSDocInfo(Node externs, Node root) {
    NodeTraversal.traverseRoots(
        compiler, new JSDocInfoCollector(compiler.getTypeRegistry()), externs, root);

    // Create references to parameters in the JSDoc.
    for (Symbol sym : getAllSymbols()) {
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
            sym.docScope =
                new SymbolScope(
                    null /* root */, null /* parent scope */, null /* type of this */, sym);
          }

          // Check to make sure there's no existing symbol. In theory, this
          // should never happen, but we check anyway and fail silently
          // if our assumptions are wrong. (We do not want to put the symbol
          // table into an invalid state).
          Symbol existingSymbol = isAnySymbolDeclared(name, paramNode, sym.docScope);
          if (existingSymbol == null) {
            declareSymbol(name, type, type == null, sym.docScope, paramNode, /* info= */ null);
          }
        } else {
          param.defineReferenceAt(paramNode);
        }
      }
    }
  }

  /** Records the visibility of each symbol. */
  void fillSymbolVisibility(Node externs, Node root) {
    CollectFileOverviewVisibility collectPass = new CollectFileOverviewVisibility(compiler);
    collectPass.process(externs, root);
    ImmutableMap<StaticSourceFile, Visibility> visibilityMap =
        collectPass.getFileOverviewVisibilityMap();

    NodeTraversal.traverseRoots(compiler, new VisibilityCollector(visibilityMap), externs, root);
  }

  /**
   * Build a property scope for the given symbol. Any properties of the symbol will be added to the
   * property scope.
   *
   * <p>It is important that property scopes are created in order from the leaves up to the root, so
   * this should only be called from #fillPropertyScopes. If you try to create a property scope for
   * a parent before its leaf, then the leaf will get cut and re-added to the parent property scope,
   * and weird things will happen.
   */
  // This function uses == to compare types to be exact same instances.
  private void createPropertyScopeFor(Symbol s) {
    // In order to build a property scope for s, we will need to build
    // a property scope for all its implicit prototypes first. This means
    // that sometimes we will already have built its property scope
    // for a previous symbol.
    if (s.propertyScope != null) {
      return;
    }

    ObjectType type = getType(s) == null ? null : getType(s).toObjectType();
    if (type == null) {
      return;
    }

    // For cases like
    // const foo = goog.require('some.foo');
    // where createPropertyScopeFor() is called for 'foo' - instead of creating new PropertyScope we
    // reuse ModuleScope corresponding to 'some.foo' module.
    if (s.getName().equals("exports") && s.getDeclarationNode().isModuleBody()) {
      s.propertyScope = scopes.get(s.getDeclarationNode());
      return;
    }
    // Create an empty property scope for the given symbol, maybe with a parent scope if it has
    // an implicit prototype.
    SymbolScope parentPropertyScope = maybeGetParentPropertyScope(type);
    s.setPropertyScope(new SymbolScope(null, parentPropertyScope, type, s));

    // If this symbol represents some 'a.b.c.prototype', add any instance properties of a.b.c
    // into the symbol scope.
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

    // Add all declared properties in propNames into the property scope
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
      Symbol oldProp =
          symbols.get(newProp.getDeclaration().getNode(), s.getName() + "." + propName);

      if (oldProp != null && compiler.getModuleMap().getClosureModule(oldProp.getName()) != null) {
        // This handles cases like:
        // goog.provide('a.b.c');
        // goog.provide('a.b.c.Foo');
        // Here were are creating scope for module 'a.b.c' and 'Foo' looks like a property of that
        // module. But it's a module by itself - so we don't move it to the scope of 'a.b.c' and
        // instead keeping 'a.b.c.Foo'  in global namespace.
        continue;
      }

      // If we've already have an entry in the table for this symbol,
      // then skip it. This should only happen if we screwed up,
      // and declared multiple distinct properties with the same name
      // at the same node. We bail out here to be safe.
      if (symbols.get(newProp.getDeclaration().getNode(), newProp.getName()) != null) {
        if (logger.isLoggable(Level.FINE)) {
          logger.fine("Found duplicate symbol " + newProp);
        }
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
        // All references/scopes from oldProp were updated to use the newProp. Time to remove
        // oldProp.
        removeSymbol(oldProp);
      }
    }
  }

  /**
   * If this type has an implicit prototype set, returns the SymbolScope corresponding to the
   * properties of the implicit prototype. Otherwise returns null.
   *
   * <p>Note that currently we only handle cases where the implicit prototype is a) a class or b) is
   * an instance object.
   */
  private @Nullable SymbolScope maybeGetParentPropertyScope(ObjectType symbolObjectType) {
    ObjectType proto = symbolObjectType.getImplicitPrototype();
    if (proto == null || proto == symbolObjectType) {
      return null;
    }
    final Symbol parentSymbol;
    if (isEs6ClassConstructor(proto)) {
      // given `class Foo {} class Bar extends Foo {}`, `Foo` is the implicit prototype of `Bar`.
      parentSymbol = getSymbolDeclaredBy(proto.toMaybeFunctionType());
    } else if (proto.getConstructor() != null) {
      // given
      //   /** @constructor */ function Foo() {}
      //   /** @constructor */ function Bar() {}
      //   goog.inherits(Bar, Foo);
      // the implicit prototype of Bar.prototype is the instance of Foo.
      parentSymbol = getSymbolForInstancesOf(proto.getConstructor());
    } else {
      return null;
    }
    if (parentSymbol == null) {
      return null;
    }
    createPropertyScopeFor(parentSymbol);
    return parentSymbol.getPropertyScope();
  }

  private boolean isEs6ClassConstructor(JSType type) {
    return type.isFunctionType()
        && type.toMaybeFunctionType().getSource() != null
        && type.toMaybeFunctionType().getSource().isClass();
  }

  /** Fill in references to "this" variables. */
  void fillThisReferences(Node externs, Node root) {
    new ThisRefCollector().process(externs, root);
  }

  /** Fill in references to "super" variables. */
  void fillSuperReferences(Node externs, Node root) {
    NodeTraversal.Callback collectSuper =
        new AbstractPostOrderCallback() {
          @Override
          public void visit(NodeTraversal t, Node n, Node parent) {
            // Process only 'super' nodes with types.
            if (!n.isSuper() || n.getJSType() == null) {
              return;
            }
            Symbol classSymbol = getSymbolForTypeHelper(n.getJSType(), /* linkToCtor= */ false);
            if (classSymbol != null) {
              classSymbol.defineReferenceAt(n);
            }
          }
        };
    NodeTraversal.traverseRoots(compiler, collectSuper, externs, root);
  }

  /**
   * This function connects imported symbol back to its declaration. For example:
   *
   * <pre>
   * goog.module('some.Foo');
   * class Foo {}
   * exports = Foo;
   *
   * goog.module('some.bar');
   * const Foo = goog.require('some.Foo');
   * </pre>
   *
   * This method will add a reference from the `Foo` symbol in `some.Bar` module pointing to the
   * `class Foo` in `some.Foo` module.
   */
  private void addRefsInGoogRequireStatement(Node n, Map<String, SymbolScope> googModuleScopes) {
    if (compiler.getOptions().shouldRewriteModules()) {
      // When modules are rewritten - there is no need to connect imported names back to definitions
      // as that was done with the help of Preprocessor tables in ClosureRewriteModule pass.
      return;
    }
    Node require = n.getLastChild();
    String moduleName = require.getSecondChild().getString();
    Module module = compiler.getModuleMap().getClosureModule(moduleName);
    ModuleType moduleType = module == null ? null : module.metadata().moduleType();
    SymbolScope moduleScope = googModuleScopes.get(moduleName);
    if (n.isName()) {
      // This is `const Foo = goog.require('some.Foo');` case.
      // The type of Foo is either a class/interface in which case we can find its symbol that
      // declared class/interface.
      Symbol declaration = getSymbolForTypeHelper(n.getJSType(), false);
      if (declaration != null) {
        declaration.defineReferenceAt(n);
      }
      // This import might be for a constant like `const constant = goog.require('some.constant');`.
      // Try to find it in the corresponding definition in module.
      switch (moduleType) {
        case GOOG_MODULE:
        case LEGACY_GOOG_MODULE:
          // For goog.module search for 'exports' node and use that it as declaration. It would be
          // better to use right-handside of exports: `exports = constant;` but it's difficult to
          // get symbol for the right-handside. So do a more limited approach.
          declaration = moduleScope.getOwnSlot("exports");
          break;
        case GOOG_PROVIDE:
          // For goog.provide `some.constant` should be defined in global namespace.
          declaration = getSymbolForName(null, moduleName);
          break;
        default:
          // skip
      }
      if (declaration != null) {
        declaration.defineReferenceAt(n);
      }
    } else if (n.isDestructuringLhs()) {
      // This is `const {one} = goog.require('some.foo')` case.
      // For each imported symbol in destructuring we need to find corresponding declaration symbol
      // in `some.foo` namespace and connect them.
      checkState(n.getFirstChild().isObjectPattern());
      // Iterate through all symbols in destructuring.
      for (Node stringKey : n.getFirstChild().children()) {
        checkState(stringKey.isStringKey());
        String varName = stringKey.getString();
        Symbol varDeclaration = null;
        // AST of goog.module and goog.provide differs significantly so we need to lookup variables
        // differently.
        switch (moduleType) {
          case GOOG_MODULE:
          case LEGACY_GOOG_MODULE:
            varDeclaration = moduleScope.getOwnSlot(varName);
            break;
          case GOOG_PROVIDE:
            varDeclaration = getSymbolForName(null, moduleName + "." + varName);
            break;
          default:
            // skip
        }
        if (varDeclaration != null) {
          varDeclaration.defineReferenceAt(stringKey);
        }
      }
    }
  }

  /**
   * Connects goog.module/goog.provide calls with goog.require/goog.requireType. For each
   * goog.module/goog.provide call creates a symbol and all goog.require/goog.requireType added as
   * references.
   */
  void fillGoogProvideModuleRequires(Node externs, Node root) {
    // small optimization to keep symbols created within this function for fast access instead of
    // going through SymbolTable.getSymbolForName() every time.
    Map<String, Symbol> declaredNamespaces = new LinkedHashMap<>();
    // Map containind goog.module names to Scope object representing them. For goog.provide
    // namespaces there is no scope as elements of goog.provide are global.
    Map<String, SymbolScope> moduleScopes = new LinkedHashMap<>();
    Predicate<Node> looksLikeGoogCall =
        (Node n) -> {
          if (!n.isCall()) {
            return false;
          }
          Node left = n.getFirstChild();
          Node arg = n.getSecondChild();
          // We want only nodes of type `goog.xyz('somestring')`.
          if (!(left.isGetProp()
                  && left.getFirstChild().isName()
                  && left.getFirstChild().getString().equals("goog"))
              || arg == null
              || !arg.isStringLit()) {
            return false;
          }
          return true;
        };
    NodeTraversal.Callback collectModuleScopes =
        new AbstractPostOrderCallback() {
          @Override
          public void visit(NodeTraversal t, Node n, Node parent) {
            if (!looksLikeGoogCall.test(n)) {
              return;
            }
            Node arg = n.getSecondChild();
            String namespaceName = "ns$" + arg.getString();
            switch (n.getFirstChild().getString()) {
              case "module":
              case "provide":
                Symbol ns =
                    declareSymbol(
                        namespaceName,
                        /* type= */ null,
                        /* inferred= */ false,
                        getGlobalScope(),
                        arg,
                        /* info= */ null);
                declaredNamespaces.put(namespaceName, ns);
                if (n.getGrandparent().isModuleBody()) {
                  moduleScopes.put(arg.getString(), scopes.get(n.getGrandparent()));
                }
                break;
              default:
                // do nothing. Some other goog.xyz call.
            }
          }
        };
    NodeTraversal.Callback processRequireStatements =
        new AbstractPostOrderCallback() {
          @Override
          public void visit(NodeTraversal t, Node n, Node parent) {
            if (!looksLikeGoogCall.test(n)) {
              return;
            }
            Node arg = n.getSecondChild();
            String namespaceName = "ns$" + arg.getString();
            switch (n.getFirstChild().getString()) {
              case "require":
              case "requireType":
              case "forwardDeclare":
                addRefsInGoogRequireStatement(n.getParent(), moduleScopes);
                Symbol symbol = declaredNamespaces.get(namespaceName);
                // We expect that namespace was already processed by that point, but in some broken
                // code it might be missing. In which case just skip it.
                if (symbol != null) {
                  symbol.defineReferenceAt(arg);
                }
                break;
              default:
                // do nothing. Some other goog.xyz call.
            }
          }
        };
    NodeTraversal.traverseRoots(compiler, collectModuleScopes, externs, root);
    NodeTraversal.traverseRoots(compiler, processRequireStatements, externs, root);
  }

  /**
   * Flattens symbols within a module scope (goog.module). In goog.module we often have different
   * variations of declaring Foo, exports.Foo, exports = {Foo}. In some cases there are both Foo and
   * exports.Foo = Foo while in others there is only exports.Foo. Semantically they are the same to
   * the user so we flatten them into Foo. All 'exports.Foo' symbols renamed or merged into 'Foo'
   * (depending on whether it already exists). That way we get more predictable table which is
   * required for better index data and integration with other languages, e.g. JS <=> TS indexing.
   *
   * <p>This is not a correct operation in 100% of times. For example it will produce very incorrect
   * result for:
   *
   * <pre>
   *   class Foo {}
   *   exports.Foo = NotFoo;
   * </pre>
   *
   * But that should be very rare case and the code is misleading and should be avoided.
   */
  void flattenGoogModuleExports() {
    for (SymbolScope moduleScope : getAllScopes()) {
      if (!moduleScope.isModuleScope()) {
        continue;
      }
      for (Symbol sym : ImmutableList.copyOf(moduleScope.ownSymbols.values())) {
        if (sym.getName().startsWith("exports.")) {
          renameSymbol(moduleScope, sym, sym.getName().substring("exports.".length()));
        }
      }
    }
  }

  /*
   * Checks whether symbol is a quoted object literal key. In the following object:
   *
   * var foo = {'one': 1, two: 2};
   *
   * 'one' is quoted key. while two is not.
   */
  private boolean isSymbolAQuotedObjectKey(Symbol symbol) {
    Node node = symbol.getDeclarationNode();
    return node != null && node.isStringKey() && node.isQuotedStringKey();
  }

  /**
   * Heuristic method to check whether symbol was created by DeclaredGlobalExternsOnWindow.java
   * pass.
   */
  private boolean isSymbolDuplicatedExternOnWindow(Symbol symbol) {
    Node node = symbol.getDeclarationNode();
    // Check that node is of type "window.foo";
    return !node.isIndexable()
        && node.isGetProp()
        && node.getFirstChild().isName()
        && node.getFirstChild().getString().equals("window");
  }

  /**
   * DeclaredGLobalExternsOnWindow.java pass duplicates all global variables so that:
   *
   * <pre>
   * var foo;
   * </pre>
   *
   * becomes
   *
   * <pre>
   * var foo;
   * window.foo;
   * </pre>
   *
   * This function finds all such cases and merges window.foo symbol back to foo. It changes
   * window.foo references to point to foo symbol.
   */
  private void mergeExternSymbolsDuplicatedOnWindow() {
    // To find duplicated symbols we rely on the fact that duplicated symbol share the same
    // source position as original symbol and going to use filename => sourcePosition => symbol
    // table.
    Table<String, Integer, Symbol> externSymbols = HashBasedTable.create();
    for (Symbol symbol : ImmutableList.copyOf(symbols.values())) {
      if (symbol.getDeclarationNode() == null
          || symbol.getDeclarationNode().getStaticSourceFile() == null
          || !symbol.getDeclarationNode().getStaticSourceFile().isExtern()) {
        continue;
      }
      String sourceFile = symbol.getSourceFileName();
      int position = symbol.getDeclarationNode().getSourcePosition();
      if (!externSymbols.contains(sourceFile, position)) {
        externSymbols.put(sourceFile, position, symbol);
        continue;
      }
      Symbol existingSymbol = externSymbols.get(sourceFile, position);
      // Consider 2 possibilies: either symbol or existingSymbol might be the generated symbol we
      // are looking for.
      if (isSymbolDuplicatedExternOnWindow(existingSymbol)) {
        mergeSymbol(existingSymbol, symbol);
        externSymbols.put(sourceFile, position, symbol);
      } else if (isSymbolDuplicatedExternOnWindow(symbol)) {
        mergeSymbol(symbol, existingSymbol);
      }
    }
  }

  /**
   * Removes various generated symbols that are invisible to users and pollute or mess up index.
   * Jscompiler does transpilations that might introduce extra nodes/symbols. Most of these symbols
   * should not get into final SymbolTable because SymbolTable should contain only symbols that
   * correspond to a symbol in original source code (before transpilation).
   */
  void removeGeneratedSymbols() {
    // Need to iterate over copy of values list because removeSymbol() will change the map
    // and we'll get ConcurrentModificationException
    for (Symbol symbol : ImmutableList.copyOf(symbols.values())) {
      if (isSymbolAQuotedObjectKey(symbol)) {
        // Quoted object keys are not considered symbols. Only unquoted keys and dot-access
        // properties are considered symbols. Remove the quoted key.
        boolean symbolAlreadyRemoved = !getScope(symbol).ownSymbols.containsKey(symbol.getName());
        if (!symbolAlreadyRemoved) {
          removeSymbol(symbol);
        }
      } else if (symbol.getDeclarationNode() != null
          && symbol.getDeclarationNode().isModuleBody()) {
        // Symbols that represent whole module (goog.module) aren't needed for indexing. We already
        // recorded imports/references of that module in fillGoogProvideModuleRequires() function.
        // Individual exported functions/variables also have their own symbols.
        removeSymbol(symbol);
      }
    }
    mergeExternSymbolsDuplicatedOnWindow();
  }

  /**
   * Given a scope from another symbol table, returns the {@code SymbolScope} rooted at the same
   * node. Creates one if it doesn't exist yet.
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
        checkState(globalScope == null, "Global scopes found at different roots");
      }

      myScope =
          new SymbolScope(
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

    private @Nullable SymbolScope propertyScope = null;

    private @Nullable Reference declaration = null;

    private @Nullable JSDocInfo docInfo = null;

    /**
     * Stored separately from {@link #docInfo}, because the visibility stored in JSDocInfo is not
     */
    private @Nullable Visibility visibility = null;

    // A scope for symbols that are only documented in JSDoc.
    private @Nullable SymbolScope docScope = null;

    Symbol(String name, JSType type, boolean inferred, SymbolScope scope) {
      super(name, type, inferred);
      this.scope = scope;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Symbol)) {
        return false;
      }
      Symbol other = (Symbol) o;

      return isTypeInferred() == other.isTypeInferred()
          && Objects.equals(getName(), other.getName())
          && Objects.equals(getType(), other.getType())
          && Objects.equals(scope, other.scope);
    }

    @Override
    public int hashCode() {
      return Objects.hash(Boolean.valueOf(isTypeInferred()), getName(), getType(), scope);
    }

    @Override
    public Reference getDeclaration() {
      return declaration;
    }

    public FunctionType getFunctionType() {
      return JSType.toMaybeFunctionType(getType());
    }

    public Reference defineReferenceAt(Node n) {
      return references.computeIfAbsent(n, (Node k) -> new Reference(this, k));
    }

    /** Sets the declaration node. May only be called once. */
    void setDeclaration(Reference ref) {
      checkState(this.declaration == null);
      this.declaration = ref;
    }

    public @Nullable Node getDeclarationNode() {
      return declaration == null ? null : declaration.getNode();
    }

    public @Nullable String getSourceFileName() {
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

    public @Nullable Visibility getVisibility() {
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

    // Index of current scope in the parent scope. Used to generate unique names for variables
    // having the same name but defined in different scopes.
    private final int indexInParent;

    private int numberOfChildScopes = 0;

    // The symbol associated with a property scope or doc scope.
    private Symbol mySymbol;

    SymbolScope(
        @Nullable Node rootNode,
        @Nullable SymbolScope parent,
        @Nullable JSType typeOfThis,
        @Nullable Symbol mySymbol) {
      this.rootNode = rootNode;
      this.parent = parent;
      this.typeOfThis = typeOfThis;
      this.scopeDepth = parent == null ? 0 : (parent.getScopeDepth() + 1);
      this.mySymbol = mySymbol;
      if (parent == null) {
        this.indexInParent = 0;
      } else {
        this.indexInParent = parent.numberOfChildScopes;
        parent.numberOfChildScopes++;
      }
    }

    Symbol getSymbolForScope() {
      return mySymbol;
    }

    void setSymbolForScope(Symbol sym) {
      this.mySymbol = sym;
    }

    /** Gets a unique index for the symbol in this scope. */
    public int getIndexOfSymbol(Symbol sym) {
      return Iterables.indexOf(ownSymbols.values(), Predicates.equalTo(sym));
    }

    Node getRootNode() {
      return rootNode;
    }

    public SymbolScope getParentScope() {
      return parent;
    }

    /**
     * Get the slot for a fully-qualified name (e.g., "a.b.c") by trying to find property scopes at
     * each part of the path.
     */
    public @Nullable Symbol getQualifiedSlot(String name) {
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

    public @Nullable Symbol getSlot(String name) {
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

    public boolean isModuleScope() {
      return getRootNode() != null && getRootNode().isModuleBody();
    }

    /**
     * Returns whether this is a doc scope. A doc scope is a table for symbols that are documented
     * solely within a JSDoc comment.
     */
    public boolean isDocScope() {
      return getRootNode() == null && mySymbol != null && mySymbol.docScope == this;
    }

    public boolean isPropertyScope() {
      return getRootNode() == null && !isDocScope();
    }

    public boolean isLexicalScope() {
      return getRootNode() != null;
    }

    public boolean isBlockScope() {
      return getRootNode() != null && NodeUtil.createsBlockScope(getRootNode());
    }

    public int getScopeDepth() {
      return scopeDepth;
    }

    public int getIndexInParent() {
      return indexInParent;
    }

    @Override
    public String toString() {
      Node n = getRootNode();
      if (isGlobalScope()) {
        return "GlobalScope";
      } else if (n != null) {
        String type = "Scope";
        if (isModuleScope()) {
          type = "ModuleScope";
        } else if (isBlockScope()) {
          type = "BlockScope";
        } else if (isLexicalScope()) {
          type = "LexicalScope";
        }
        return type + "@" + n.getSourceFileName() + ":" + n.getLineno();
      } else {
        return "PropertyScope@" + getSymbolForScope();
      }
    }
  }

  private class PropertyRefCollector extends AbstractPostOrderCallback implements CompilerPass {
    @Override
    public void process(Node externs, Node root) {
      NodeTraversal.traverseRoots(compiler, this, externs, root);
    }

    private boolean maybeDefineReference(Node n, String propName, Symbol ownerSymbol) {
      if (ownerSymbol == null) {
        return false;
      }
      // If current symbol is ObjectType - try using ObjectType.getPropertyNode to find a node.
      // That function searches for property in all extended classes and interfaces, which supports
      // multiple extended interfaces.
      JSType owner = ownerSymbol.getType();
      if (owner != null && owner.isObjectType()) {
        Node propNode = owner.toObjectType().getPropertyNode(propName);
        Symbol propSymbol = symbols.get(propNode, propName);
        if (propSymbol != null) {
          propSymbol.defineReferenceAt(n);
          return true;
        }
      }
      // Use PropertyScope to find property. PropertyScope doesn't support multiple parents so all
      // cases where multiple parents can occur (e.g. class implementing 2+ interfaces) must be
      // handled above.
      if (ownerSymbol.getPropertyScope() != null) {
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
        if (lexicalSym != null
            && lexicalSym.isLexicalVariable()
            && lexicalSym.getDeclaration().getNode() == n) {
          removeSymbol(lexicalSym);
        }
      }
    }

    private boolean maybeDefineTypedReference(Node n, String propName, JSType owner) {
      if (owner.isGlobalThisType()) {
        Symbol sym = globalScope.getSlot(propName);
        if (sym != null) {
          sym.defineReferenceAt(n);
          return true;
        }
      } else if (owner.isNominalConstructorOrInterface()) {
        return maybeDefineReference(n, propName, getSymbolDeclaredBy(owner.toMaybeFunctionType()));
      } else if (owner.isEnumType()) {
        return maybeDefineReference(n, propName, getSymbolDeclaredBy(owner.toMaybeEnumType()));
      } else {
        boolean defined = false;
        for (Symbol ctor : getAllSymbolsForType(owner)) {
          if (maybeDefineReference(n, propName, getSymbolForInstancesOf(ctor))) {
            defined = true;
          }
        }
        if (defined) {
          return true;
        }
      }
      // Handle cases like accessing properties on a imported module.
      // const foo = goog.require('some.module.foo');
      // foo.doSomething();
      // here n is foo, propName is doSomething and owner is module type.
      if (owner.isObjectType() && owner.toMaybeObjectType() != null) {
        Node propNodeDecl = owner.assertObjectType().getPropertyNode(propName);
        if (propNodeDecl == null) {
          return false;
        }
        // There is no handy way to find symbol object the given property node. So we do
        // property node => namespace node => namespace symbol => property symbol.
        if (propNodeDecl != null
            && (propNodeDecl.isGetProp() || propNodeDecl.isOptChainGetProp())) {
          Node namespace = propNodeDecl.getFirstChild();
          Symbol namespaceSym = getSymbolForName(namespace, namespace.getQualifiedName());
          if (namespaceSym != null && namespaceSym.getPropertyScope() != null) {
            Symbol propSym = namespaceSym.getPropertyScope().getSlot(propName);
            if (propSym != null && propSym.getDeclarationNode() != n) {
              propSym.defineReferenceAt(n);
              return true;
            }
          }
        }

        // Here handle the case of goog.module exports that have shape of:
        //
        // goog.module('foo.bar');
        // function doOne() { ... }
        // exports = {doOne};
        //
        // propNodeDecl is `doOne` node on the last line with exports. From that node and propName
        // `doOne` we need to get symbol for the `function doOne`. To achieve that we get scope of
        // `doOne` node which should be whole module scope and look for symbol with corresponding
        // name. This relies on the assumption that function name in module scope exported name. But
        // if export is renamed: `exports = {doOneRenamed: doOne}`, then this approach will fail.
        SymbolScope propNodeDeclScope = getEnclosingScope(propNodeDecl);
        if (propNodeDeclScope == null || !propNodeDeclScope.isModuleScope()) {
          return false;
        }
        Symbol symbol = getSymbolForName(propNodeDecl, propName);
        if (symbol != null && symbol.getDeclarationNode() != n) {
          symbol.defineReferenceAt(n);
          return true;
        }
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

      if (n.isGetProp() || n.isOptChainGetProp()) {
        JSType owner = n.getFirstChild().getJSType();
        if (owner != null) {
          boolean defined = maybeDefineTypedReference(n, n.getString(), owner);

          if (defined) {
            tryRemoveLexicalQualifiedNameRef(n.getQualifiedName(), n);
            return;
          }
        }

        tryDefineLexicalQualifiedNameRef(n.getQualifiedName(), n);
      } else if (n.isStringKey()) {
        JSType owner = parent.getJSType();
        if (owner != null) {
          boolean defined = maybeDefineTypedReference(n, n.getString(), owner);

          if (defined) {
            tryRemoveLexicalQualifiedNameRef(NodeUtil.getBestLValueName(n), n);
            return;
          }
        }

        tryDefineLexicalQualifiedNameRef(NodeUtil.getBestLValueName(n), n);
      }
    }
  }

  private class ThisRefCollector extends NodeTraversal.AbstractScopedCallback
      implements CompilerPass {
    // The 'this' symbols in the current scope chain.
    //
    // If we don't know how to declare 'this' in a scope chain,
    // then null should be on the stack. But this should be a rare
    // occurrence. We should strive to always be able to come up
    // with some symbol for 'this'.
    //
    // This list only has entries for function scopes and the global scope, and doesn't store a
    // separate `this` value for other block scopes.
    private final List<Symbol> thisStack = new ArrayList<>();

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
          symbol =
              addSymbol(
                  GLOBAL_THIS,
                  registry.getNativeType(JSTypeNative.GLOBAL_THIS),
                  false /* declared */,
                  globalScope,
                  firstInputRoot);
          symbol.setDeclaration(new Reference(symbol, firstInputRoot));
        }
        thisStack.add(symbol);
      } else if (t.getScopeRoot().isFunction()) {
        // Otherwise, declare a "this" property when possible.
        Node scopeRoot = t.getScopeRoot();
        SymbolScope scope = scopes.get(scopeRoot);
        if (NodeUtil.getFunctionBody(scopeRoot).hasChildren()) {
          Symbol scopeSymbol = getSymbolForScope(scope);
          if (scopeSymbol != null) {
            SymbolScope propScope = scopeSymbol.getPropertyScope();
            if (propScope != null) {
              // If a function is assigned multiple times, we only want
              // one addressable "this" symbol.
              symbol = propScope.getOwnSlot("this");
              if (symbol == null) {
                JSType rootType = t.getScopeRoot().getJSType();
                FunctionType fnType = rootType == null ? null : rootType.toMaybeFunctionType();
                JSType type = fnType == null ? null : fnType.getTypeOfThis();
                symbol = addSymbol("this", type, /* inferred= */ false, scope, scopeRoot);
              }
            }
          }
        } else {
          logger.fine("Skipping empty function: " + scopeRoot);
        }
        thisStack.add(symbol);
      }
      // Don't add to the `thisStack` for other block scopes.
    }

    @Override
    public void exitScope(NodeTraversal t) {
      if (t.inGlobalScope() || t.getScopeRoot().isFunction()) {
        thisStack.remove(thisStack.size() - 1);
      }
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
  private class JSDocInfoCollector extends AbstractPostOrderCallback {
    private final JSTypeRegistry typeRegistry;

    private JSDocInfoCollector(JSTypeRegistry registry) {
      this.typeRegistry = registry;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      JSDocInfo info = n.getJSDocInfo();
      if (info == null) {
        return;
      }
      docInfos.add(n);

      for (Node typeAst : info.getTypeNodes()) {
        SymbolScope scope = scopes.get(t.getScopeRoot());
        visitTypeNode(info.getTemplateTypes(), scope == null ? globalScope : scope, typeAst);
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

    public void visitTypeNode(
        ImmutableMap<String, JSTypeExpression> templateTypeNames, SymbolScope scope, Node n) {
      if (n.isStringLit()
          && !isNativeSourcelessType(n.getString())
          && !templateTypeNames.containsKey(n.getString())) {
        Symbol symbol = lookupPossiblyDottedName(scope, n.getString());
        if (symbol != null) {
          Node ref = n;
          String typeString = n.getOriginalName() != null ? n.getOriginalName() : n.getString();
          // Qualified names in JSDoc types are kept as a single string: "foo.bar.MyType". In order
          // to have good indexing we need to make the SymbolTable reference include only "MyType"
          // instead of "foo.bar.MyType". To do that we clone the type node and change the source
          // info of the clone to include only the last part of the type ("MyType").
          if (typeString.contains(".")) {
            String lastPart = typeString.substring(typeString.lastIndexOf('.') + 1);
            Node copy = n.cloneNode();
            copy.setLinenoCharno(
                copy.getLineno(), copy.getCharno() + copy.getLength() - lastPart.length());
            copy.setLength(lastPart.length());
            ref = copy;
          }
          symbol.defineReferenceAt(ref);
        }
      }

      for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
        visitTypeNode(templateTypeNames, scope, child);
      }
    }

    // TODO(peterhal): @template types.
    private Symbol lookupPossiblyDottedName(SymbolScope scope, String dottedName) {
      // Try the dotted name to start.
      Symbol result = scope.getQualifiedSlot(dottedName);
      if (result != null) {
        return result;
      }
      // If we can't find this type, it might be a reference to a
      // primitive type (like {string}). Autobox it to check.
      JSType type = typeRegistry.getGlobalType(dottedName);
      JSType autobox = type == null ? null : type.autoboxesTo();
      return autobox == null
          ? getSymbolForTypeHelper(type, true)
          : getSymbolForTypeHelper(autobox, true);
    }
  }

  /** Collects the visibility information for each name/property. */
  private class VisibilityCollector extends AbstractPostOrderCallback {
    private final ImmutableMap<StaticSourceFile, Visibility> fileVisibilityMap;

    private VisibilityCollector(ImmutableMap<StaticSourceFile, Visibility> fileVisibilityMap) {
      this.fileVisibilityMap = fileVisibilityMap;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isName()) {
        visitName(t, n);
      } else if (n.isGetProp() || n.isOptChainGetProp()) {
        visitProperty(n, parent);
      }
    }

    private void visitName(NodeTraversal t, Node n) {
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
      Visibility v = AccessControlUtils.getEffectiveNameVisibility(n, var, fileVisibilityMap);
      if (v == null) {
        return;
      }
      symbol.setVisibility(v);
    }

    private void visitProperty(Node getprop, Node parent) {
      String propertyName = getprop.getString();
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
      boolean isOverride =
          parent.getJSDocInfo() != null && parent.isAssign() && parent.getFirstChild() == getprop;
      if (isOverride) {
        // Don't bother with AccessControlUtils for overridden properties.
        // AccessControlUtils currently has complicated logic for detecting
        // visibility mismatches for overridden properties that is still
        // too tightly coupled to CheckAccessControls. TODO(brndn): simplify.
        symbol.setVisibility(Visibility.INHERITED);
      } else {
        ObjectType referenceType = ObjectType.cast(jsType.dereference());
        Visibility v =
            AccessControlUtils.getEffectivePropertyVisibility(
                getprop, referenceType, fileVisibilityMap);
        if (v == null) {
          return;
        }
        symbol.setVisibility(v);
      }
    }
  }

  // Comparators
  private final Ordering<String> sourceNameOrdering = Ordering.natural().nullsFirst();

  private final Ordering<Node> nodeOrdering =
      new Ordering<Node>() {
        @Override
        public int compare(Node a, Node b) {
          int result = sourceNameOrdering.compare(a.getSourceFileName(), b.getSourceFileName());
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
          checkState(a.isLexicalScope() && b.isLexicalScope(), "We can only sort lexical scopes");
          int result = nodeOrdering.compare(a.getRootNode(), b.getRootNode());
          if (result != 0) {
            return result;
          }

          // If result = 0 it means that rootNodes either the same or that one of them was added
          // by compiler during transpilation and uses the same source info as original node.
          // In that case compare scopes by depth because one of them (probably generated one) is
          // a child of the other scope.
          // TODO(b/62349230): remove this once transpilation is disabled. No two different scopes
          // should have the same source info.
          return a.getScopeDepth() - b.getScopeDepth();
        }
      };

  private final Ordering<Symbol> symbolOrdering =
      new Ordering<Symbol>() {
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
   * <p>For a property scope, returns the number of scopes we have to search to find the nearest
   * lexical scope, plus that lexical scope's depth.
   *
   * <p>For a doc info scope, returns 0.
   */
  private int getLexicalScopeDepth(SymbolScope scope) {
    if (scope.isLexicalScope() || scope.isDocScope()) {
      return scope.getScopeDepth();
    } else {
      checkState(scope.isPropertyScope());
      Symbol sym = scope.getSymbolForScope();
      checkNotNull(sym);
      return getLexicalScopeDepth(getScope(sym)) + 1;
    }
  }

  private @Nullable JSType getType(StaticSlot sym) {
    if (sym instanceof StaticTypedSlot) {
      return ((StaticTypedSlot) sym).getType();
    }
    return null;
  }

  private @Nullable JSType getTypeOfThis(StaticScope s) {
    if (s instanceof StaticTypedScope) {
      return ((StaticTypedScope) s).getTypeOfThis();
    }
    return null;
  }

  private boolean isTypeInferred(StaticSlot sym) {
    if (sym instanceof StaticTypedSlot) {
      return ((StaticTypedSlot) sym).isTypeInferred();
    }
    return true;
  }
}
