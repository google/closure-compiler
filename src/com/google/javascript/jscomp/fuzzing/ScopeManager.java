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
        "Array",
        "Boolean",
        "Function",
        "Number",
        "Object",
        "RegExp",
        "String",
        "Error",
        "JSON",
        "Math",
        "NaN",
        "undefined");
    numSym = globalScope.symbols.size();
    scopeStack.push(globalScope);
  }

  public void addFunctionScope() {
    Scope newScope = new Scope();
    newScope.symbols = Lists.newArrayList("arguments");
    numSym++;
    scopeStack.push(newScope);
  }

  public void removeScope() {
    numSym -= localSymbols().size();
    scopeStack.pop();
  }

  public void addSymbol(String symbol) {
    localSymbols().add(symbol);
    numSym++;
  }

  public void removeSymbol(String symbol) {
    if (localSymbols().remove(symbol)) {
      numSym--;
    }
  }

  private ArrayList<String> localSymbols() {
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

  public String getRandomSymbol(boolean excludeLocal) {
    if (excludeLocal) {
      Preconditions.checkArgument(getNumScopes() > 1);
    } else {
      Preconditions.checkArgument(getNumScopes() > 0);
    }
    List<String> symbols = getRandomScope(excludeLocal).symbols;
    String sym = symbols.get(random.nextInt(symbols.size()));
    if (excludeLocal && localSymbols().indexOf(sym) != -1) {
      // the symbol has been shadowed
      return null;
    } else {
      return sym;
    }
  }

  /**
   * Get a scope randomly. The more variables/functions the scope has, the more
   * likely it will be chosen
   */
  private Scope getRandomScope(boolean excludeLocal) {
    ArrayList<Scope> scopes = new ArrayList<Scope>(scopeStack);
    ArrayList<Double> weights = Lists.newArrayListWithCapacity(getNumScopes());
    int i;
    for (i = 0; i < scopeStack.size() - 1; i++) {
      Scope s = scopes.get(i);
      weights.add(Double.valueOf(s.symbols.size()));
    }
    if (!excludeLocal) {
      Scope s = scopes.get(i);
      weights.add(Double.valueOf(s.symbols.size()));
    }
    DiscreteDistribution<Scope> distribution =
        new DiscreteDistribution<Scope>(random, scopes, weights);
    return distribution.nextItem();
  }
}
