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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSymbolTable;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.SimpleReference;
import com.google.javascript.rhino.jstype.SimpleSlot;
import com.google.javascript.rhino.jstype.StaticTypedScope;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.nullness.Nullable;

/**
 * A symbol table for references that are removed by preprocessor passes (like {@code
 * ProcessClosurePrimitives}).
 */
final class PreprocessorSymbolTable
    implements StaticTypedScope, StaticSymbolTable<SimpleSlot, PreprocessorSymbolTable.Reference> {

  /**
   * All preprocessor symbols are globals.
   */
  private final Map<String, SimpleSlot> symbols = new LinkedHashMap<>();

  private final Multimap<String, Reference> refs =
      ArrayListMultimap.create();

  private final Node root;

  PreprocessorSymbolTable(Node root) {
    this.root = root;
  }

  @Override
  public Node getRootNode() {
    return root;
  }

  @Override
  public @Nullable JSType getTypeOfThis() {
    return null;
  }

  @Override
  public @Nullable StaticTypedScope getParentScope() {
    return null;
  }

  @Override
  public SimpleSlot getSlot(String name) {
    return symbols.get(name);
  }

  @Override
  public SimpleSlot getOwnSlot(String name) {
    return getSlot(name);
  }

  @Override
  public Iterable<Reference> getReferences(SimpleSlot symbol) {
    return Collections.unmodifiableCollection(refs.get(symbol.getName()));
  }

  @Override
  public Iterable<SimpleSlot> getAllSymbols() {
    return Collections.unmodifiableCollection(symbols.values());
  }

  @Override
  public StaticTypedScope getScope(SimpleSlot slot) {
    return this;
  }

  void addReference(Node node) {
    addReference(node, getQualifiedName(node));
  }

  void addReference(Node node, String name) {
    checkNotNull(name);

    symbols.computeIfAbsent(name, (String k) -> new SimpleSlot(k, null, true));

    refs.put(name, new Reference(symbols.get(name), node));
  }

  /**
   * This variant of Node#getQualifiedName is adds special support STRING nodes which represent
   * module names.
   */
  public String getQualifiedName(Node n) {
    return n.isStringLit() ? n.getString() : n.getQualifiedName();
  }

  static final class Reference extends SimpleReference<SimpleSlot> {
    Reference(SimpleSlot symbol, Node node) {
      super(symbol, node);
    }
  }

  /**
   * Object that maybe contains instance of the table. This object is needed because
   * PreprocessorSymbolTable is used by multiple passes in different parts of code which initialized
   * at different times (some even before compiler object is created). Instead instance of factory
   * is passed around. Each pass that uses PreprocessorSymbolTable has to call maybeInitialize()
   * before getting instance.
   */
  public static class CachedInstanceFactory {

    private @Nullable PreprocessorSymbolTable instance;

    public void maybeInitialize(AbstractCompiler compiler) {
      if (compiler.getOptions().preservesDetailedSourceInfo()) {
        Node root = compiler.getRoot();
        if (instance == null || instance.getRootNode() != root) {
          instance = new PreprocessorSymbolTable(root);
        }
      }
    }

    public @Nullable PreprocessorSymbolTable getInstanceOrNull() {
      return instance;
    }
  }
}
