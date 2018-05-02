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

  // The TypedScope for the block that this flow scope is defined for.
  private final TypedScope syntacticScope;

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
   * Creates a flow scope without a direct parent. This can happen in three cases: (1) the "bottom"
   * scope for a CFG root, (2) a direct child of a parent at the maximum depth, or (3) a joined
   * scope with more than one direct parent. The parent is non-null only in the second case.
   */
  private LinkedFlowScope(FlatFlowScopeCache cache, TypedScope syntacticScope) {
    this.cache = cache;
    this.lastSlot = null;
    this.depth = 0;
    this.parent = cache.linkedEquivalent;
    this.syntacticScope = syntacticScope;
  }

  /** Creates a child flow scope with a single parent. */
  private LinkedFlowScope(LinkedFlowScope directParent, TypedScope syntacticScope) {
    this.cache = directParent.cache;
    this.lastSlot = directParent.lastSlot;
    this.depth = directParent.depth + 1;
    this.parent = directParent;
    this.syntacticScope = syntacticScope;
  }

  /** Whether this flows from a bottom scope. */
  private boolean flowsFromBottom() {
    return cache.functionScope.isBottom();
  }

  /**
   * Creates an entry lattice for the flow.
   */
  public static LinkedFlowScope createEntryLattice(TypedScope scope) {
    return new LinkedFlowScope(new FlatFlowScopeCache(scope), scope);
  }

  @Override
  public void inferSlotType(String symbol, JSType type) {
    checkState(!frozen);
    ScopedName var = getVarFromSyntacticScope(symbol);
    lastSlot = new LinkedFlowSlot(var, type, lastSlot);
    depth++;
    cache.dirtySymbols.add(var);
  }

  @Override
  public void inferQualifiedSlot(Node node, String symbol, JSType bottomType,
      JSType inferredType, boolean declared) {
    if (cache.functionScope.isGlobal()) {
      // Do not infer qualified names on the global scope.  Ideally these would be
      // added to the scope by TypedScopeCreator, but if they are not, adding them
      // here causes scaling problems (large projects can have tens of thousands of
      // undeclared qualified names in the global scope) with no real benefit.
      return;
    }
    TypedVar v = syntacticScope.getVar(symbol);
    if (v == null && !cache.functionScope.isBottom()) {
      // NOTE(sdh): Qualified names are declared on scopes lazily via this method.
      // The difficulty is that it's not always clear which scope they need to be
      // defined on.  In particular, syntacticScope is wrong because it is often a
      // nested block scope that is ignored when branches are joined; functionScope
      // is also wrong because it could lead to ambiguity if the same root name is
      // declared in multiple different blocks.  Instead, the qualified name is declared
      // on the scope that owns the root, when possible.
      TypedVar rootVar = syntacticScope.getVar(getRootOfQualifiedName(symbol));
      TypedScope rootScope =
          rootVar != null ? rootVar.getScope() : syntacticScope.getClosestHoistScope();
      v = rootScope.declare(symbol, node, bottomType, null, !declared);
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
    return syntacticScope.getRootNode();
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
    return getSlot(getVarFromSyntacticScope(name));
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
    return slot != null ? slot : syntacticScope.getSlot(var.getName());
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
  private ScopedName getVarFromSyntacticScope(String name) {
    TypedVar v = syntacticScope.getVar(name);
    if (v != null) {
      return v;
    }
    TypedVar rootVar = syntacticScope.getVar(getRootOfQualifiedName(name));
    TypedScope rootScope = rootVar != null ? rootVar.getScope() : null;
    rootScope = rootScope != null ? rootScope : cache.functionScope;
    return ScopedName.of(name, rootScope.getRootNode());
  }

  @Override
  public StaticTypedSlot<JSType> getOwnSlot(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public FlowScope createChildFlowScope() {
    return createChildFlowScope(syntacticScope);
  }

  @Override
  public FlowScope createChildFlowScope(StaticTypedScope<JSType> scope) {
    frozen = true;

    TypedScope typedScope = (TypedScope) scope;
    if (depth > MAX_DEPTH) {
      if (flattened == null) {
        flattened = new FlatFlowScopeCache(this);
      }
      return new LinkedFlowScope(flattened, typedScope);
    }

    return new LinkedFlowScope(this, typedScope);
  }

  /**
   * Remove flow scopes that add nothing to the flow.
   */
  @Override
  public LinkedFlowScope optimize() {
    LinkedFlowScope current = this;
    // NOTE(sdh): This function does not take syntacticScope into account.
    // This means that an optimized scope cannot be used to look up names
    // by string without first creating a child in the correct block.  This
    // is not a problem, since this is only used for (a) determining whether
    // to join two scopes, (b) determining whether two scopes are equal, or
    // (c) optimizing away unnecessary children generated by flowing through
    // an expression.  In (a) and (b) the result is only inspected locally and
    // not escaped.  In (c) the result is fed directly into further joins and
    // will always have a block scope reassigned before flowing into another
    // node.  In all cases, it's therefore safe to ignore block scope changes
    // when optimizing.
    while (current.parent != null && current.lastSlot == current.parent.lastSlot) {
      current = current.parent;
    }
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
    return syntacticScope;
  }

  /** Join the two FlowScopes. */
  static class FlowScopeJoinOp extends JoinOp.BinaryJoinOp<FlowScope> {
    // NOTE(sdh): When joining flow scopes with different syntactic scopes,
    // we do not attempt to recover the correct syntactic scope.  This is
    // okay because joins only occur in two situations: (1) performed by
    // the DataFlowAnalysis class automatically between CFG nodes, and (2)
    // requested manually while traversing a single expression within a CFG
    // node.  The syntactic scope is always set at the beginning of flowing
    // through a CFG node.  In the case of (1), the join result's syntactic
    // scope is immediately replaced with the correct one when we flow through
    // the next node.  In the case of (2), both inputs will always have the
    // same syntactic scope.  So simply propagating either input's scope is
    // perfectly fine.
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
      // NOTE: it would be nice to put 'null' as the syntactic scope if they're not
      // equal, but this is not currently feasible.  For joins that occur within a
      // single CFG node's flow, it's irrelevant, but for joins between separate
      // CFG nodes, there is *one* place where the syntactic scope is actually used:
      // when joining more than two scopes, the first two scopes are joined, and
      // then the join result is joined with the third.  When joining, we look up
      // the types (and existence) of vars in one scope in the other; so when a var
      // from the third scope (say, a local) is missing from the join result, it
      // looks through the syntactic scope before realizing  this.  A quick fix
      // might be to just check that the scope is non-null before trying to join;
      // a better long-term fix would be to improve how we do joins to avoid
      // excessive map entry creation: find a common ancestor, etc.  One
      // interesting consequence of the current approach is that we may end up
      // adding irrelevant block-local variables to the joined scope unnecessarily.
      TypedScope common = getCommonParentDeclarationScope(linkedA, linkedB);

      // TODO(sdh): Consider reusing the input cache if both inputs are identical.
      // We can evaluate how often this happens to see whather this would be a win.
      FlatFlowScopeCache cache = new FlatFlowScopeCache(linkedA, linkedB, common.getRootNode());
      return new LinkedFlowScope(cache, common);
    }
  }

  static TypedScope getCommonParentDeclarationScope(LinkedFlowScope left, LinkedFlowScope right) {
    if (left.flowsFromBottom()) {
      return right.syntacticScope;
    } else if (right.flowsFromBottom()) {
      return left.syntacticScope;
    }
    return left.syntacticScope.getCommonParent(right.syntacticScope);
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
    if (this.cache.functionScope != that.cache.functionScope) {
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
    if (aIsNull || bIsNull) {
      return aIsNull != bIsNull;
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

    // A cache at the join of two scope chains.  The 'common' node is the root of the closest shared
    // ancestor scope between the two joined scopes.  Any symbols in more deeply nested scopes than
    // this are excluded from the join operations.
    @SuppressWarnings("ReferenceEquality")
    FlatFlowScopeCache(LinkedFlowScope joinedScopeA, LinkedFlowScope joinedScopeB, Node common) {
      this.linkedEquivalent = null;

      // Always prefer the "real" function scope to the faked-out
      // bottom scope.
      this.functionScope =
          joinedScopeA.flowsFromBottom()
              ? joinedScopeB.cache.functionScope
              : joinedScopeA.cache.functionScope;

      Map<ScopedName, LinkedFlowSlot> slotsA = joinedScopeA.allFlowSlots();
      Map<ScopedName, LinkedFlowSlot> slotsB = joinedScopeB.allFlowSlots();
      Set<Node> commonAncestorScopeRootNodes = new HashSet<>();
      commonAncestorScopeRootNodes.add(common);
      if (common.getParent() != null) {
        for (Node n : common.getAncestors()) {
          if (NodeUtil.createsScope(n)) {
            commonAncestorScopeRootNodes.add(n);
          }
        }
      }

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

      // Stores names that are not in a common ancestor of slotsA and slotsB for later removal
      Set<ScopedName> obsoleteNames = new HashSet<>();
      for (ScopedName var : Sets.union(slotsA.keySet(), slotsB.keySet())) {
        if (!commonAncestorScopeRootNodes.contains(var.getScopeRoot())) {
          // Variables not defined in a common ancestor no longer exist after the join.
          // Since this.symbols is initialized to slotsA, this.symbols may already contain var.
          // Remove obsolete names after this for loop (to avoid a ConcurrentModificationException)
          obsoleteNames.add(var);
          continue;
        }
        LinkedFlowSlot slotA = slotsA.get(var);
        LinkedFlowSlot slotB = slotsB.get(var);
        JSType joinedType = null;
        if (slotB == null || slotB.getType() == null) {
          TypedVar fnSlot = joinedScopeB.syntacticScope.getSlot(var.getName());
          JSType fnSlotType = fnSlot == null ? null : fnSlot.getType();
          if (fnSlotType == null) {
            // Case #1 -- already inserted.
          } else if (fnSlotType != slotA.getType()) {
            // Case #3
            joinedType = slotA.getType().getLeastSupertype(fnSlotType);
          }
        } else if (slotA == null || slotA.getType() == null) {
          TypedVar fnSlot = joinedScopeA.syntacticScope.getSlot(var.getName());
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

        if (joinedType != null && (slotA == null || joinedType != slotA.getType())) {
          symbols.put(var, new LinkedFlowSlot(var, joinedType, null));
        }
      }
      for (ScopedName var : obsoleteNames) {
        this.symbols.remove(var);
      }
    }
  }
}
