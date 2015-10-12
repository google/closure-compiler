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

/**
 * A symbol table for references that are removed by preprocessor passes
 * (like {@code ProcessClosurePrimitives}).
 *
 * @author nicksantos@google.com (Nick Santos)
 */
final class PreprocessorSymbolTable
    implements StaticTypedScope<JSType>,
               StaticSymbolTable<SimpleSlot,
                                 PreprocessorSymbolTable.Reference> {

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
  public Node getRootNode() { return root; }

  @Override
  public JSType getTypeOfThis() { return null; }

  @Override
  public StaticTypedScope<JSType> getParentScope() { return null; }

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
  public StaticTypedScope<JSType> getScope(SimpleSlot slot) {
    return this;
  }

  void addReference(Node node) {
    String name = node.getQualifiedName();
    Preconditions.checkNotNull(name);

    if (!symbols.containsKey(name)) {
      symbols.put(name, new SimpleSlot(name, null, true));
    }

    refs.put(name, new Reference(symbols.get(name), node));
  }

  static final class Reference extends SimpleReference<SimpleSlot> {
    Reference(SimpleSlot symbol, Node node) {
      super(symbol, node);
    }
  }
}
