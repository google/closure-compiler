/*
 * Copyright 2013 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.fuzzing;


import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * @author zplin@google.com (Zhongpeng Lin)
 */
public class ScopeManager {
  private ArrayDeque<Scope> scopeStack = new ArrayDeque<Scope>();
  // Random generator for getting random scope/symbol for fuzzer
  private Random random;
  // Total number of symbols currently available
  private int numSym;

  public ScopeManager(Random random) {
    this.random = random;
    Scope globalScope = new Scope();
    globalScope.symbols = Lists.newArrayList(
        new Symbol("Array", Type.FUNCTION),
        new Symbol("Boolean", Type.FUNCTION),
        new Symbol("Function", Type.FUNCTION),
        new Symbol("Object", Type.FUNCTION),
        new Symbol("RegExp", Type.FUNCTION),
        new Symbol("String", Type.FUNCTION),
        new Symbol("Error", Type.FUNCTION),
        new Symbol("JSON", Type.FUNCTION),
        new Symbol("Math", Type.OBJECT),
        new Symbol("Number", Type.FUNCTION));
    numSym = globalScope.symbols.size();
    scopeStack.push(globalScope);
  }

  public void addScope() {
    Scope newScope = new Scope();
    newScope.symbols = Lists.newArrayList(new Symbol("arguments", Type.ARRAY));
    numSym++;
    scopeStack.push(newScope);
  }

  public void removeScope() {
    numSym -= localSymbols().size();
    scopeStack.pop();
  }

  public void addSymbol(Symbol symbol) {
    localSymbols().add(symbol);
    numSym++;
  }

  public void removeSymbol(String symbolName) {
    Symbol symbol = null;
    for (Symbol s : localSymbols()) {
      if (s.name.equals(symbolName)) {
        symbol = s;
        break;
      }
    }
    if (symbol != null && localSymbols().remove(symbol)) {
      numSym--;
    }
  }

  private ArrayList<Symbol> localSymbols() {
    return scopeStack.peek().symbols;
  }

  public Scope localScope() {
    return scopeStack.peek();
  }

  public int getSize() {
    return numSym;
  }

  public int getNumScopes() {
    return scopeStack.size();
  }

  public boolean hasNonLocals() {
    return scopeStack.size() > 1;
  }

  public Symbol getRandomSymbol(boolean excludeLocal) {
    return getRandomSymbol(null, excludeLocal);
  }

  public Symbol getRandomSymbol(Type type, boolean excludeLocal) {
    if (excludeLocal) {
      Preconditions.checkArgument(getNumScopes() > 1);
    } else {
      Preconditions.checkArgument(getNumScopes() > 0);
    }

    List<Symbol> symbols = new ArrayList<>();
    ArrayList<Scope> scopes = new ArrayList<Scope>(scopeStack);
    int i;
    if (excludeLocal) {
      i = 1;
    } else {
      i = 0;
    }
    while (i < scopes.size()) {
      Scope scope = scopes.get(i);
      for (Symbol s : scope.symbols) {
        if (type == null || s.type == type) {
          symbols.add(s);
        }
      }
      i++;
    }

    int numCandidates = symbols.size();
    if (numCandidates == 0) {
      return null;
    }
    Symbol sym = symbols.get(random.nextInt(numCandidates));
    if (excludeLocal && localSymbols().indexOf(sym) != -1) {
      // the symbol has been shadowed
      return null;
    } else {
      return sym;
    }
  }
}
