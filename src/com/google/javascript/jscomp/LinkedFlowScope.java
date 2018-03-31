/*
 * Copyright 2008 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.type.FlowScope;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.StaticTypedRef;
import com.google.javascript.rhino.jstype.StaticTypedScope;
import com.google.javascript.rhino.jstype.StaticTypedSlot;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A flow scope that tries to store as little symbol information as possible,
 * instead delegating to its parents. Optimized for low memory use.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class LinkedFlowScope implements FlowScope {
  // The closest flow scope cache.
  private final FlatFlowScopeCache cache;

  // The parent flow scope.
  private final LinkedFlowScope parent;

  // The distance between this flow scope and the closest flat flow scope.
  private int depth;

  static final int MAX_DEPTH = 250;

  // A FlatFlowScopeCache equivalent to this scope.
  private FlatFlowScopeCache flattened;

  // Flow scopes assume that all their ancestors are immutable.
  // So once a child scope is created, this flow scope may not be modified.
  private boolean frozen = false;

  // The last slot defined in this flow instruction, and the head of the
  // linked list of slots.
  private LinkedFlowSlot lastSlot;

  /**
   * Creates a flow scope without a direct parent.  This can happen in three cases: (1) the "bottom"
   * scope for a CFG root, (2) a direct child of a parent at the maximum depth, or (3) a joined
   * scope with more than one direct parent.  The parent is non-null only in the second case.
   */
  private LinkedFlowScope(FlatFlowScopeCache cache) {
    this.cache = cache;
    this.lastSlot = null;
    this.depth = 0;
    this.parent = cache.linkedEquivalent;
  }

  /**
   * Creates a child flow scope with a single parent.
   */
  private LinkedFlowScope(LinkedFlowScope directParent) {
    this.cache = directParent.cache;
    this.lastSlot = directParent.lastSlot;
    this.depth = directParent.depth + 1;
    this.parent = directParent;
  }

  /** Gets the function scope for this flow scope. */
  private TypedScope getFunctionScope() {
    return cache.functionScope;
  }

  /** Whether this flows from a bottom scope. */
  private boolean flowsFromBottom() {
    return getFunctionScope().isBottom();
  }

  /**
   * Creates an entry lattice for the flow.
   */
  public static LinkedFlowScope createEntryLattice(TypedScope scope) {
    return new LinkedFlowScope(new FlatFlowScopeCache(scope));
  }

  @Override
  public void inferSlotType(String symbol, JSType type) {
    checkState(!frozen);
    ScopedName var = getVarFromFunctionScope(symbol);
    lastSlot = new LinkedFlowSlot(var, type, lastSlot);
    depth++;
    cache.dirtySymbols.add(var);
  }

  @Override
  public void inferQualifiedSlot(Node node, String symbol, JSType bottomType,
      JSType inferredType, boolean declared) {
    TypedScope functionScope = getFunctionScope();
    if (functionScope.isGlobal()) {
      return;
    }

    TypedVar v  = functionScope.getVar(symbol);
    if (v == null && !functionScope.isBottom()) {
      v = functionScope.declare(symbol, node, bottomType, null, !declared);
    }

    JSType declaredType = v != null ? v.getType() : null;
    if (v != null) {
      if (!v.isTypeInferred()) {
        // Use the inferred type over the declared type only if the
        // inferred type is a strict subtype of the declared type.
        if (declaredType == null
            || !inferredType.isSubtypeOf(declaredType)
            || declaredType.isSubtypeOf(inferredType)
            || inferredType.isEquivalentTo(declaredType)) {
          return;
        }
      } else if (declaredType != null && !inferredType.isSubtypeOf(declaredType)) {
        // If this inferred type is incompatible with another type previously
        // inferred and stored on the scope, then update the scope.
        v.setType(v.getType().getLeastSupertype(inferredType));
      }
    }
    inferSlotType(symbol, inferredType);
  }

  @Override
  public JSType getTypeOfThis() {
    return cache.functionScope.getTypeOfThis();
  }

  @Override
  public Node getRootNode() {
    return getFunctionScope().getRootNode();
  }

  @Override
  public StaticTypedScope<JSType> getParentScope() {
    throw new UnsupportedOperationException();
  }

  /**
   * Get the slot for the given symbol.
   */
  @Override
  public StaticTypedSlot<JSType> getSlot(String name) {
    return getSlot(getVarFromFunctionScope(name));
  }

  private StaticTypedSlot<JSType> getSlot(ScopedName var) {
    if (cache.dirtySymbols.contains(var)) {
      for (LinkedFlowSlot slot = lastSlot; slot != null; slot = slot.parent) {
        if (slot.var.equals(var)) {
          return slot;
        }
      }
    }
    LinkedFlowSlot slot = cache.symbols.get(var);
    return slot != null ? slot : getFunctionScope().getSlot(var.getName());
  }

  private static String getRootOfQualifiedName(String name) {
    int index = name.indexOf('.');
    return index < 0 ? name : name.substring(0, index);
  }

  // Returns a ScopedName that uniquely identifies the given name in this scope.
  // If the scope does not have a var for the name (this should only be the case
  // for qualified names, though some unit tests fail to declare simple names as
  // well), a simple ScopedName will be created, using the scope of the qualified
  // name's root, but not registered on the scope.
  private ScopedName getVarFromFunctionScope(String name) {
    TypedVar v = getFunctionScope().getVar(name);
    if (v != null) {
      return v;
    }
    TypedVar rootVar = getFunctionScope().getVar(getRootOfQualifiedName(name));
    TypedScope rootScope = rootVar != null ? rootVar.getScope() : null;
    rootScope = rootScope != null ? rootScope : getFunctionScope().getGlobalScope();
    return ScopedName.of(name, rootScope.getRootNode());
  }

  @Override
  public StaticTypedSlot<JSType> getOwnSlot(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public FlowScope createChildFlowScope() {
    frozen = true;

    if (depth > MAX_DEPTH) {
      if (flattened == null) {
        flattened = new FlatFlowScopeCache(this);
      }
      return new LinkedFlowScope(flattened);
    }

    return new LinkedFlowScope(this);
  }

  /**
   * Iterate through all the linked flow scopes before this one.
   * If there's one and only one slot defined between this scope
   * and the blind scope, return it.
   */
  @Override
  public StaticTypedSlot<JSType> findUniqueRefinedSlot(FlowScope blindScope) {
    LinkedFlowSlot result = null;

    for (LinkedFlowScope currentScope = this;
         currentScope != blindScope;
         currentScope = currentScope.parent) {
      LinkedFlowSlot parentSlot = currentScope.parent != null ? currentScope.parent.lastSlot : null;
      for (LinkedFlowSlot currentSlot = currentScope.lastSlot;
           currentSlot != null && currentSlot != parentSlot;
           currentSlot = currentSlot.parent) {
        if (result == null) {
          result = currentSlot;
        } else if (!currentSlot.var.equals(result.var)) {
          return null;
        }
      }
    }

    return result;
  }

  /**
   * Remove flow scopes that add nothing to the flow.
   */
  // NOTE(nicksantos): This function breaks findUniqueRefinedSlot, because
  // findUniqueRefinedSlot assumes that this scope is a direct descendant
  // of blindScope. This is not necessarily true if this scope has been
  // optimize()d and blindScope has not. This should be fixed. For now,
  // we only use optimize() where we know that we won't have to do
  // a findUniqueRefinedSlot on it (i.e. between CFG nodes, while the
  // latter is only used within a single node to backwards-infer the LHS
  // of short circuiting AND and OR operators).
  @Override
  public LinkedFlowScope optimize() {
    LinkedFlowScope current;
    for (current = this;
         current.parent != null && current.lastSlot == current.parent.lastSlot;
         current = current.parent) {}
    return current;
  }

  /** Returns whether this.optimize() == that.optimize(), but without walking up the chain. */
  private boolean optimizesToSameScope(LinkedFlowScope that) {
    // If lastSlot is null then there are no changes overlayed on top of the cache.  In this
    // case, the flow scopes are the same only if 'that' also has a null lastSlot and has the
    // same cache.
    if (this.lastSlot == null) {
      return that.lastSlot == null && this.cache == that.cache;
    }
    // If lastSlot is non-null, then the scopes optimize to the same thing if and only if their
    // lastSlots are the same object.  In that case, the caches *must* be the same as well, since
    // there's no way to change the cache without also changing lastSlot (which we verify).
    checkState((this.cache == that.cache) || (this.lastSlot != that.lastSlot));
    return this.lastSlot == that.lastSlot;
  }

  @Override
  public TypedScope getDeclarationScope() {
    return this.getFunctionScope();
  }

  /** Join the two FlowScopes. */
  static class FlowScopeJoinOp extends JoinOp.BinaryJoinOp<FlowScope> {
    @SuppressWarnings("ReferenceEquality")
    @Override
    public FlowScope apply(FlowScope a, FlowScope b) {
      // To join the two scopes, we have to
      LinkedFlowScope linkedA = (LinkedFlowScope) a;
      LinkedFlowScope linkedB = (LinkedFlowScope) b;
      linkedA.frozen = true;
      linkedB.frozen = true;
      if (linkedA.optimizesToSameScope(linkedB)) {
        return linkedA.createChildFlowScope();
      }
      // TODO(sdh): Consider reusing the input cache if both inputs are identical.
      // We can evaluate how often this happens to see whather this would be a win.
      return new LinkedFlowScope(new FlatFlowScopeCache(linkedA, linkedB));
    }
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof LinkedFlowScope)) {
      return false;
    }
    LinkedFlowScope that = (LinkedFlowScope) other;
    if (this.optimizesToSameScope(that)) {
      return true;
    }

    // If two flow scopes are in the same function, then they could have
    // two possible function scopes: the real one and the BOTTOM scope.
    // If they have different function scopes, we *should* iterate through all
    // the variables in each scope and compare. However, 99.9% of the time,
    // they're not equal. And the other .1% of the time, we can pretend
    // they're equal--this just means that data flow analysis will have
    // to propagate the entry lattice a little bit further than it
    // really needs to. Everything will still come out ok.
    if (this.getFunctionScope() != that.getFunctionScope()) {
      return false;
    }

    if (cache == that.cache) {
      // If the two flow scopes have the same cache, then we can check
      // equality a lot faster: by just looking at the "dirty" elements
      // in the cache, and comparing them in both scopes.
      for (ScopedName var : cache.dirtySymbols) {
        if (diffSlots(getSlot(var), that.getSlot(var))) {
          return false;
        }
      }

      return true;
    }

    Map<ScopedName, LinkedFlowSlot> myFlowSlots = allFlowSlots();
    Map<ScopedName, LinkedFlowSlot> otherFlowSlots = that.allFlowSlots();

    for (ScopedName name : Sets.union(myFlowSlots.keySet(), otherFlowSlots.keySet())) {
      if (diffSlots(myFlowSlots.get(name), otherFlowSlots.get(name))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Determines whether two slots are meaningfully different for the
   * purposes of data flow analysis.
   */
  private static boolean diffSlots(StaticTypedSlot<JSType> slotA, StaticTypedSlot<JSType> slotB) {
    boolean aIsNull = slotA == null || slotA.getType() == null;
    boolean bIsNull = slotB == null || slotB.getType() == null;
    if (aIsNull && bIsNull) {
      return false;
    } else if (aIsNull ^ bIsNull) {
      return true;
    }

    // Both slots and types must be non-null.
    return slotA.getType().differsFrom(slotB.getType());
  }

  /**
   * Gets all the symbols that have been defined before this point
   * in the current flow. Does not return slots that have not changed during
   * the flow.
   *
   * For example, consider the code:
   * <code>
   * var x = 3;
   * function f() {
   *   var y = 5;
   *   y = 6; // FLOW POINT
   *   var z = y;
   *   return z;
   * }
   * </code>
   * A FlowScope at FLOW POINT will return a slot for y, but not
   * a slot for x or z.
   */
  private Map<ScopedName, LinkedFlowSlot> allFlowSlots() {
    Map<ScopedName, LinkedFlowSlot> slots = new HashMap<>();
    for (LinkedFlowSlot slot = lastSlot; slot != null; slot = slot.parent) {
      slots.putIfAbsent(slot.var, slot);
    }

    for (Map.Entry<ScopedName, LinkedFlowSlot> symbolEntry : cache.symbols.entrySet()) {
      slots.putIfAbsent(symbolEntry.getKey(), symbolEntry.getValue());
    }

    return slots;
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException();
  }

  /** A static slot with a linked list built in. */
  private static class LinkedFlowSlot implements StaticTypedSlot<JSType> {
    final ScopedName var;
    final JSType type;
    final LinkedFlowSlot parent;

    LinkedFlowSlot(ScopedName var, JSType type, LinkedFlowSlot parent) {
      this.var = var;
      this.type = type;
      this.parent = parent;
    }

    @Override
    public String getName() {
      return var.getName();
    }

    @Override
    public JSType getType() {
      return type;
    }

    @Override
    public boolean isTypeInferred() {
      return true;
    }

    @Override
    public StaticTypedRef<JSType> getDeclaration() {
      return null;
    }

    @Override
    public JSDocInfo getJSDocInfo() {
      return null;
    }

    @Override
    public StaticTypedScope<JSType> getScope() {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * A map that tries to cache as much symbol table information
   * as possible in a map. Optimized for fast lookup.
   */
  private static class FlatFlowScopeCache {
    // The TypedScope for the entire function or for the global scope.
    final TypedScope functionScope;

    // The linked flow scope that this cache represents.
    final LinkedFlowScope linkedEquivalent;

    // All the symbols defined before this point in the local flow.
    // May not include lazily declared qualified names.
    final Map<ScopedName, LinkedFlowSlot> symbols;

    // Used to help make lookup faster for LinkedFlowScopes by recording
    // symbols that may be redefined "soon", for an arbitrary definition
    // of "soon". ;)
    //
    // More rigorously, if a symbol is redefined in a LinkedFlowScope,
    // and this is the closest FlatFlowScopeCache, then that symbol is marked
    // "dirty". In this way, we don't waste time looking in the LinkedFlowScope
    // list for symbols that aren't defined anywhere nearby.
    final Set<ScopedName> dirtySymbols = new HashSet<>();

    // The cache at the bottom of the lattice.
    FlatFlowScopeCache(TypedScope functionScope) {
      this.functionScope = functionScope;
      this.symbols = ImmutableMap.of();
      this.linkedEquivalent = null;
    }

    // A cache in the middle of a long scope chain.
    FlatFlowScopeCache(LinkedFlowScope directParent) {
      FlatFlowScopeCache cache = directParent.cache;

      this.functionScope = cache.functionScope;
      this.symbols = directParent.allFlowSlots();
      this.linkedEquivalent = directParent;
    }

    // A cache at the join of two scope chains.
    @SuppressWarnings("ReferenceEquality")
    FlatFlowScopeCache(LinkedFlowScope joinedScopeA, LinkedFlowScope joinedScopeB) {
      this.linkedEquivalent = null;

      // Always prefer the "real" function scope to the faked-out
      // bottom scope.
      this.functionScope = joinedScopeA.flowsFromBottom()
          ? joinedScopeB.getFunctionScope() : joinedScopeA.getFunctionScope();

      Map<ScopedName, LinkedFlowSlot> slotsA = joinedScopeA.allFlowSlots();
      Map<ScopedName, LinkedFlowSlot> slotsB = joinedScopeB.allFlowSlots();

      this.symbols = slotsA;

      // There are 5 different join cases:
      // 1) The type is declared in joinedScopeA, not in joinedScopeB,
      //    and not in functionScope. Just use the one in A.
      // 2) The type is declared in joinedScopeB, not in joinedScopeA,
      //    and not in functionScope. Just use the one in B.
      // 3) The type is declared in functionScope and joinedScopeA, but
      //    not in joinedScopeB. Join the two types.
      // 4) The type is declared in functionScope and joinedScopeB, but
      //    not in joinedScopeA. Join the two types.
      // 5) The type is declared in joinedScopeA and joinedScopeB. Join
      //    the two types.

      for (ScopedName var : Sets.union(slotsA.keySet(), slotsB.keySet())) {
        LinkedFlowSlot slotA = slotsA.get(var);
        LinkedFlowSlot slotB = slotsB.get(var);
        JSType joinedType = null;
        if (slotB == null || slotB.getType() == null) {
          TypedVar fnSlot = joinedScopeB.getFunctionScope().getVar(var.getName());
          JSType fnSlotType = fnSlot == null ? null : fnSlot.getType();
          if (fnSlotType == null) {
            // Case #1 -- already inserted.
          } else if (fnSlotType != slotA.getType()) {
            // Case #3
            joinedType = slotA.getType().getLeastSupertype(fnSlotType);
          }
        } else if (slotA == null || slotA.getType() == null) {
          TypedVar fnSlot = joinedScopeA.getFunctionScope().getVar(var.getName());
          JSType fnSlotType = fnSlot == null ? null : fnSlot.getType();
          if (fnSlotType == null || fnSlotType == slotB.getType()) {
            // Case #2
            symbols.put(var, slotB);
          } else {
            // Case #4
            joinedType = slotB.getType().getLeastSupertype(fnSlotType);
          }
        } else if (slotA.getType() != slotB.getType()) {
          // Case #5
          joinedType = slotA.getType().getLeastSupertype(slotB.getType());
        }

        if (joinedType != null) {
          symbols.put(var, new LinkedFlowSlot(var, joinedType, null));
        }
      }
    }
  }
}
