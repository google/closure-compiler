/*
 * Copyright 2010 The Closure Compiler Authors.
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
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Keeps track of a function's known side effects by type and the
 * list of calls that appear in a function's body.
 *
 */
class FunctionSideEffectData {
  private final boolean extern;
  private final List<Node> callsInFunctionBody = Lists.newArrayList();
  private final Scope localScope;
  private Set<ValueEntry> nonLocalValues = Sets.newHashSet();
  private Set<ValueEntry> modifiedLocals = Sets.newHashSet();
  private Multimap<ValueEntry, ValueEntry> valueInfluenceMap =
      HashMultimap.create();
  private boolean pureFunction = false;
  private boolean functionThrows = false;
  private boolean taintsGlobalState = false;
  private boolean taintsThis = false;
  private boolean taintsArguments = false;
  private boolean taintsUnknown = false;
  private boolean taintsReturn = false;

  /**
   * Represents a value in the value locality and modification structures:
   * #nonLocalValues, #modifiedLocals, #valueInfluenceMap
   */
  interface ValueEntry {
  }

  /**
   * A representation of the values associated with these the: this and return
   * keywords and the arguments pseudo-keyword.
   */
  static class KeywordValueEntry implements ValueEntry {
    // These special token objects are used to track values that we care about
    // that are not represented by Vars.
    final static ValueEntry THIS = new KeywordValueEntry();
    final static ValueEntry RETURN = new KeywordValueEntry();
    final static ValueEntry ARGUMENTS = new KeywordValueEntry();

    KeywordValueEntry() {}
  }

  /**
   * A named value (vars, functions) entry.
   */
  static class NameValueEntry implements ValueEntry {
    private final String name;

    NameValueEntry(Node node) {
      Preconditions.checkState(node.getType() == Token.NAME);
      this.name = node.getString();
    }

    NameValueEntry(Var var) {
      this.name = var.name;
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      return o != null
          && o instanceof NameValueEntry
          && name.equals(((NameValueEntry)o).name);
    }

    @Override
    public String toString() {
      return name;
    }
  }

  /**
   * An entry representing a NEW or CALL.
   */
  static class CallValueEntry implements ValueEntry {
    private final Node node;

    CallValueEntry(Node node) {
      Preconditions.checkState(
          node.getType() == Token.CALL || node.getType() == Token.NEW);
      this.node = node;
    }

    @Override
    public int hashCode() {
      return node.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      return o != null
          && o instanceof CallValueEntry
          && node.equals(((CallValueEntry)o).node);
    }

    @Override
    public String toString() {
      return node.toString();
    }
  }

  FunctionSideEffectData(boolean extern, Scope scope) {
    this.extern = extern;
    this.localScope = scope;
    checkInvariant();
  }

  Scope getScope() {
    return localScope;
  }

  private Multimap<ValueEntry, ValueEntry> getValueInfluenceMap() {
    return valueInfluenceMap;
  }

  // TODO(johnlenz): The use of isInformationStable and clearLocalityState
  // is fragile.  Consider using a singleton instance of FunctionSideEffectData
  // that represents this state (everything is mutating).
  
  boolean isInformationStable() {
    // Once we know there are global changes and the result is non-local
    // there is no point in looking for additional information.
    return (mutatesGlobalState() && taintsReturn);
  }

  /**
   * Free up the data structure that are used to calculate value locality.
   */
  void clearLocalityState() {
    nonLocalValues = null;
    modifiedLocals = null;
    valueInfluenceMap = null;
  }

  /**
   * @param var
   */
  void addModified(Var var) {
    Preconditions.checkState(var.scope == localScope);
    modifiedLocals.add(new NameValueEntry(var));
  }

  /**
   * @param var
   */
  void addNonLocalValue(Var var) {
    Preconditions.checkState(var.scope == localScope);
    nonLocalValues.add(new NameValueEntry(var));
  }

  /**
   * Add a value dependency from the source ValueEntry to the sink
   * ValueEntry.
   * @param source
   * @param sink
   */
  void addInfluence(ValueEntry source, ValueEntry sink) {
    valueInfluenceMap.put(source, sink);
  }

  /**
   * For use with NodeUtil#evaluatesToLocalValue
   * @return Whether the node represents a value that is currently considered
   *     a known-local-unescaped value.
   */
  boolean isLocalValue(Node value) {
    switch (value.getType()) {
      case Token.NAME:
        String name = value.getString();
        return getScope().isDeclared(name, false)
            && !nonLocalValues.contains(new NameValueEntry(value));
      case Token.CALL:
        return !nonLocalValues.contains(new CallValueEntry(value));
    }
    return false;
  }

  /**
   * @returns false if function known to have side effects.
   */
  boolean mayBePure() {
    return !(functionThrows ||
             taintsGlobalState ||
             taintsThis ||
             taintsArguments ||
             taintsUnknown);
  }

  /**
   * @returns false if function known to be pure.
   */
  boolean mayHaveSideEffects() {
    return !pureFunction;
  }

  /**
   * Mark the function as being pure.
   */
  void setIsPure() {
    pureFunction = true;
    checkInvariant();
  }

  /**
   * Marks the function as having "modifies globals" side effects.
   */
  void setTaintsGlobalState() {
    taintsGlobalState = true;
    checkInvariant();
  }

  /**
   * Marks the function as having "modifies this" side effects.
   */
  void setTaintsThis() {
    taintsThis = true;
    checkInvariant();
  }

  /**
   * Marks the function as having "modifies arguments" side effects.
   */
  void setTaintsArguments() {
    taintsArguments = true;
    checkInvariant();
  }

  /**
   * Marks the function as having "throw" side effects.
   */
  void setFunctionThrows() {
    functionThrows = true;
    checkInvariant();
  }

  /**
   * Marks the function as having "complex" side effects that are
   * not otherwise explicitly tracked.
   */
  void setTaintsUnknown() {
    taintsUnknown = true;
    checkInvariant();
  }

  /**
   * Marks the function as having non-local return result.
   */
  void setTaintsReturn() {
    taintsReturn = true;
    checkInvariant();
  }

  /**
   * @return Whether the function has a non-local return value.
   */
  boolean hasNonLocalReturnValue() {
    return taintsReturn;
  }

  /**
   * Returns true if function mutates global state.
   */
  boolean mutatesGlobalState() {
    // TODO(johnlenz): track arguments separately.
    return taintsGlobalState || taintsArguments || taintsUnknown;
  }

  /**
   * Returns true if function mutates "this".
   */
  boolean mutatesThis() {
    return taintsThis;
  }

  /**
   * Returns true if function has an explicit "throw".
   */
  boolean functionThrows() {
    return functionThrows;
  }

  /**
   * Verify internal consistency.  Should be called at the end of
   * every method that mutates internal state.
   */
  private void checkInvariant() {
    boolean invariant = mayBePure() || mayHaveSideEffects();
    if (!invariant) {
      throw new IllegalStateException("Invariant failed.  " + toString());
    }
  }

  /**
   * Add a CALL or NEW node to the list of calls this function makes.
   */
  void appendCall(Node callNode) {
    callsInFunctionBody.add(callNode);
  }

  /**
   * Gets the list of CALL and NEW nodes.
   */
  List<Node> getCallsInFunctionBody() {
    return callsInFunctionBody;
  }

  @Override
  public String toString() {
    List<String> status = Lists.newArrayList();
    if (extern) {
      status.add("extern");
    }

    if (pureFunction) {
      status.add("pure");
    }

    if (taintsThis) {
      status.add("this");
    }

    if (taintsGlobalState) {
      status.add("global");
    }

    if (functionThrows) {
      status.add("throw");
    }

    if (taintsUnknown) {
      status.add("complex");
    }

    return "Side effects: " + status.toString();
  }

  /**
   * Normalize the ValueEntry maps relation using the value dependencies
   * so that any non-local-value that may have been modified has been
   * accounted for.
   */
  void normalizeValueMaps() {
    //
    // Normalize the value influence map.
    //
    if (!mutatesGlobalState() || !taintsReturn) {
      // propagate non-local objects values
      // Note: propagateNonLocal adds entries so grap a snapshot
      // as an array to interate over.
      for (ValueEntry entry : nonLocalValues.toArray(new ValueEntry[0])) {
        propagateNonLocal(entry);
      }
    }

    if (!mutatesGlobalState()) {
      // propagate object modification
      // Note: propagateNonLocal adds entries so grap a snapshot
      // as an array to interate over.
      for (ValueEntry entry : modifiedLocals.toArray(new ValueEntry[0])) {
        propagateModified(entry);
      }
    }

    // Don't retain information that won't be used.
    if (isInformationStable()){
      // a global state change maybe a inner function modifying these local
      // names, or a call to eval,  so there are locals that are known to be
      // unmodified.
      clearLocalityState();
    }
  }

  /**
   * @param value A Var or token representing the value.
   * @return Whether the FunctionInformation was updated.
   */
  boolean maybePropagateNonLocal(ValueEntry value) {
    if (!nonLocalValues.contains(value)) {
      nonLocalValues.add(value);
      return propagateNonLocal(value);
    }
    return false;
  }
  
  /**
   * @param value The ValueEntry of interest.
   * @return Whether the FunctionInformation was updated.
   */
  boolean propagateNonLocal(ValueEntry value) {
    Preconditions.checkState(nonLocalValues != null);
    Preconditions.checkState(modifiedLocals != null);

    boolean changed = false;
    if (modifiedLocals.contains(value)) {
      if (!mutatesGlobalState()) {
        setTaintsGlobalState();
        changed = true;
      }
    }

    if (value == KeywordValueEntry.RETURN) {
      if (!taintsReturn) {
        setTaintsReturn();
        changed = true;
      }
    }

    // Add the value now to prevent recursion
    Collection<ValueEntry> dependents = getValueInfluenceMap().get(value);
    if (dependents != null) {
      for (ValueEntry dependent : dependents) {
        if (!nonLocalValues.contains(dependent)) {
          nonLocalValues.add(dependent);
          changed = propagateNonLocal(dependent) || changed;
        }
      }
    }

    return changed;
  }

  /**
   * @param value A Var or token representing the value.
   * @return Whether the FunctionInformation was updated.
   */
  private boolean propagateModified(ValueEntry value) {
    Preconditions.checkState(nonLocalValues != null);
    Preconditions.checkState(modifiedLocals != null);

    boolean changed = false;
    if (nonLocalValues != null
        && nonLocalValues.contains(value)) {
      if (!mutatesGlobalState()) {
        setTaintsUnknown();
        changed = true;
      }
    } else if (value == KeywordValueEntry.THIS) {
      if (!taintsThis) {
        setTaintsThis();
        changed = true;
      }
    } else if (value == KeywordValueEntry.ARGUMENTS) {
      if (!taintsArguments) {
        setTaintsArguments();
        changed = true;
      }
    }
    // Add the value now to prevent recursion
    Collection<ValueEntry> dependents = getValueInfluenceMap().get(value);
    if (dependents != null) {
      for (ValueEntry dependent : dependents) {
        if (!modifiedLocals.contains(dependent)) {
          modifiedLocals.add(dependent);
          changed = propagateModified(dependent) || changed;
        }
      }
    }
    return changed;
  }
}