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
class ScopeManager {
  private ArrayDeque<Scope> scopeStack = new ArrayDeque<>();
  // Random generator for getting random scope/symbol for fuzzer
  private Random random;
  // Total number of symbols currently available
  private int numSym;
  static final int EXCLUDE_LOCALS = 1;
  static final int EXCLUDE_EXTERNS = 1 << 1;

  public ScopeManager(Random random) {
    this.random = random;
    Scope externs = new Scope();
    externs.symbols = Lists.newArrayList(
        new Symbol("Array", Type.FUNCTION),
        new Symbol("Boolean", Type.FUNCTION),
        new Symbol("Function", Type.FUNCTION),
        new Symbol("Object", Type.FUNCTION),
        new Symbol("String", Type.FUNCTION),
        new Symbol("Error", Type.FUNCTION),
        new Symbol("JSON", Type.OBJECT),
        new Symbol("Math", Type.OBJECT),
        new Symbol("Number", Type.FUNCTION),
        new Symbol("isFinite", Type.FUNCTION),
        new Symbol("parseFloat", Type.FUNCTION),
        new Symbol("parseInt", Type.FUNCTION),
        new Symbol("decodeURI", Type.FUNCTION),
        new Symbol("decodeURIComponent", Type.FUNCTION),
        new Symbol("encodeURI", Type.FUNCTION),
        new Symbol("encodeURIComponent", Type.FUNCTION),
        new Symbol("isNaN", Type.FUNCTION));
    scopeStack.push(externs);
    // global scope
    scopeStack.push(new Scope());
    numSym = 0;
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
    Symbol symbol = searchLocalFor(symbolName);
    if (symbol != null && localSymbols().remove(symbol)) {
      numSym--;
    }
  }

  private Symbol searchLocalFor(String symbolName) {
    Symbol symbol = null;
    for (Symbol s : localSymbols()) {
      if (s.name.equals(symbolName)) {
        symbol = s;
        break;
      }
    }
    return symbol;
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

  public boolean isInFunction() {
    return scopeStack.size() > 2;
  }

  public boolean hasNonLocals() {
    return numSym - localSymbols().size() > 0;
  }

  public Symbol getRandomSymbol(int flags) {
    return getRandomSymbol(null, flags);
  }

  public Symbol getRandomSymbol(Type type, int flags) {
    int minScopes = 1;
    boolean excludeLocals = (flags & EXCLUDE_LOCALS) != 0;
    if (excludeLocals) {
      minScopes++;
    }
    boolean excludeExterns = (flags & EXCLUDE_EXTERNS) != 0;
    if (excludeExterns) {
      minScopes++;
    }
    Preconditions.checkArgument(scopeStack.size() >= minScopes);

    List<Symbol> symbols = new ArrayList<>();
    ArrayList<Scope> scopes = new ArrayList<>(scopeStack);
    int start = excludeLocals ? 1 : 0;
    int end = excludeExterns ? scopes.size() - 1 : scopes.size();
    for (int i = start; i < end; i++) {
      Scope scope = scopes.get(i);
      for (Symbol s : scope.symbols) {
        if (type == null || s.type == type) {
          symbols.add(s);
        }
      }
    }

    int numCandidates = symbols.size();
    if (numCandidates == 0) {
      return null;
    }
    Symbol sym = symbols.get(random.nextInt(numCandidates));
    if (excludeLocals && searchLocalFor(sym.name) != null) {
      // the symbol has been shadowed
      return null;
    } else {
      return sym;
    }
  }
}
