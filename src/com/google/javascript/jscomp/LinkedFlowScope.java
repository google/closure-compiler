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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.javascript.jscomp.type.FlowScope;
import com.google.javascript.rhino.HamtPMap;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.PMap;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.StaticTypedRef;
import com.google.javascript.rhino.jstype.StaticTypedScope;
import com.google.javascript.rhino.jstype.StaticTypedSlot;

/**
 * A flow scope that tries to store as little symbol information as possible,
 * instead delegating to its parents. Optimized for low memory use.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class LinkedFlowScope implements FlowScope {

  // Map from TypedScope to OverlayScope.
  private final PMap<TypedScope, OverlayScope> scopes;

  private final TypedScope functionScope;

  // The TypedScope for the block that this flow scope is defined for.
  private final TypedScope syntacticScope;

  /**
   * Creates a flow scope without a direct parent. This can happen in three cases: (1) the "bottom"
   * scope for a CFG root, (2) a direct child of a parent at the maximum depth, or (3) a joined
   * scope with more than one direct parent. The parent is non-null only in the second case.
   */
  private LinkedFlowScope(
      PMap<TypedScope, OverlayScope> scopes,
      TypedScope syntacticScope,
      TypedScope functionScope) {
    this.scopes = scopes;
    this.syntacticScope = syntacticScope;
    this.functionScope = functionScope;
  }

  /**
   * Returns the scope map, trimmed to the common ancestor between this FlowScope's syntacticScope
   * and the given scope. Any inferred types on variables in deeper scopes cannot be propagated past
   * this point (since they're no longer in scope), and trimming them eagerly allows us to ignore
   * these irrelevant types when checking equality and joining.
   */
  private PMap<TypedScope, OverlayScope> trimScopes(TypedScope scope) {
    TypedScope thisScope = syntacticScope;
    TypedScope thatScope = scope;
    int thisDepth = thisScope.getDepth();
    int thatDepth = thatScope.getDepth();
    PMap<TypedScope, OverlayScope> result = scopes;
    while (thatDepth > thisDepth) {
      thatScope = thatScope.getParent();
      thatDepth--;
    }
    while (thisDepth > thatDepth) {
      result = result.minus(thisScope);
      thisScope = thisScope.getParent();
      thisDepth--;
    }
    while (thisScope != thatScope && thisScope != null && thatScope != null) {
      result = result.minus(thisScope);
      thisScope = thisScope.getParent();
      thatScope = thatScope.getParent();
    }
    return result;
  }

  /** Whether this flows from a bottom scope. */
  private boolean flowsFromBottom() {
    return functionScope.isBottom();
  }

  /**
   * Creates an entry lattice for the flow.
   */
  public static LinkedFlowScope createEntryLattice(TypedScope scope) {
    return new LinkedFlowScope(HamtPMap.<TypedScope, OverlayScope>empty(), scope, scope);
  }

  @Override
  public LinkedFlowScope inferSlotType(String symbol, JSType type) {
    OverlayScope scope = getOverlayScopeForVar(symbol, true);
    OverlayScope newScope = scope.infer(symbol, type);
    // Aggressively remove empty scopes to maintain a reasonable equivalence.
    PMap<TypedScope, OverlayScope> newScopes =
        !newScope.slots.isEmpty() ? scopes.plus(scope.scope, newScope) : scopes.minus(scope.scope);
    return newScopes != scopes
        ? new LinkedFlowScope(newScopes, syntacticScope, functionScope)
        : this;
  }

  @Override
  public LinkedFlowScope inferQualifiedSlot(
      Node node, String symbol, JSType bottomType, JSType inferredType, boolean declared) {
    if (functionScope.isGlobal()) {
      // Do not infer qualified names on the global scope.  Ideally these would be
      // added to the scope by TypedScopeCreator, but if they are not, adding them
      // here causes scaling problems (large projects can have tens of thousands of
      // undeclared qualified names in the global scope) with no real benefit.
      return this;
    }
    TypedVar v = syntacticScope.getVar(symbol);
    if (v == null && !functionScope.isBottom()) {
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
          return this;
        }
      } else if (declaredType != null && !inferredType.isSubtypeOf(declaredType)) {
        // If this inferred type is incompatible with another type previously
        // inferred and stored on the scope, then update the scope.
        v.setType(v.getType().getLeastSupertype(inferredType));
      }
    }
    return inferSlotType(symbol, inferredType);
  }

  @Override
  public JSType getTypeOfThis() {
    return functionScope.getTypeOfThis();
  }

  @Override
  public Node getRootNode() {
    return syntacticScope.getRootNode();
  }

  @Override
  public StaticTypedScope getParentScope() {
    throw new UnsupportedOperationException();
  }

  /** Get the slot for the given symbol. */
  @Override
  public StaticTypedSlot getSlot(String name) {
    OverlayScope scope = getOverlayScopeForVar(name, false);
    return scope != null ? scope.getSlot(name) : syntacticScope.getSlot(name);
  }

  private static String getRootOfQualifiedName(String name) {
    int index = name.indexOf('.');
    return index < 0 ? name : name.substring(0, index);
  }

  private OverlayScope getOverlayScopeForVar(String name, boolean create) {
    TypedVar v = syntacticScope.getVar(name);
    TypedScope scope = v != null ? v.getScope() : null;
    if (scope == null) {
      TypedVar rootVar = syntacticScope.getVar(getRootOfQualifiedName(name));
      scope = rootVar != null ? rootVar.getScope() : null;
      scope = scope != null ? scope : functionScope;
    }
    OverlayScope overlay = scopes.get(scope);
    if (overlay == null && create) {
      overlay = new OverlayScope(scope);
    }
    return overlay;
  }

  @Override
  public StaticTypedSlot getOwnSlot(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public FlowScope withSyntacticScope(StaticTypedScope scope) {
    TypedScope typedScope = (TypedScope) scope;
    return scope != syntacticScope
        ? new LinkedFlowScope(trimScopes(typedScope), typedScope, functionScope)
        : this;
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
    @Override
    public FlowScope apply(FlowScope a, FlowScope b) {
      // To join the two scopes, we have to
      LinkedFlowScope linkedA = (LinkedFlowScope) a;
      LinkedFlowScope linkedB = (LinkedFlowScope) b;
      if (linkedA.scopes == linkedB.scopes && linkedA.functionScope == linkedB.functionScope) {
        return linkedA;
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
      return new LinkedFlowScope(
          join(linkedA, linkedB, common),
          common,
          linkedA.flowsFromBottom() ? linkedB.functionScope : linkedA.functionScope);
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

    // If two flow scopes are in the same function, then they could have
    // two possible function scopes: the real one and the BOTTOM scope.
    // If they have different function scopes, we *should* iterate through all
    // the variables in each scope and compare. However, 99.9% of the time,
    // they're not equal. And the other .1% of the time, we can pretend
    // they're equal--this just means that data flow analysis will have
    // to propagate the entry lattice a little bit further than it
    // really needs to. Everything will still come out ok.
    return this.functionScope == that.functionScope
        && this.scopes.equivalent(that.scopes, LinkedFlowScope::equalScopes);
  }

  private static boolean equalScopes(OverlayScope left, OverlayScope right) {
    if (left == right) {
      return true;
    }
    return left.slots.equivalent(right.slots, LinkedFlowScope::equalSlots);
  }

  /**
   * Determines whether two slots are meaningfully different for the purposes of data flow analysis.
   */
  private static boolean equalSlots(StaticTypedSlot slotA, StaticTypedSlot slotB) {
    return slotA == slotB || !slotA.getType().differsFrom(slotB.getType());
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("ReferenceEquality") // JSType comparisons are expensive, so just use identity.
  private static PMap<TypedScope, OverlayScope> join(
      LinkedFlowScope linkedA, LinkedFlowScope linkedB, TypedScope commonParent) {
    return linkedA
        .trimScopes(commonParent)
        .reconcile(
            linkedB.trimScopes(commonParent),
            (scopeA, scopeB) -> {
              PMap<String, OverlaySlot> slotsA = scopeA != null ? scopeA.slots : EMPTY_SLOTS;
              PMap<String, OverlaySlot> slotsB = scopeB != null ? scopeB.slots : EMPTY_SLOTS;
              // TODO(sdh): Simplify this logic: we want the best non-bottom scope we can get,
              // for the purpose of (a) passing to the joined OverlayScope constructor, and
              // (b) joining types only present in one scope.
              TypedScope typedScopeA =
                  linkedA.flowsFromBottom() ? null : scopeA != null ? scopeA.scope : scopeB.scope;
              TypedScope typedScopeB =
                  linkedB.flowsFromBottom() ? null : scopeB != null ? scopeB.scope : scopeA.scope;
              TypedScope bestScope = typedScopeA != null ? typedScopeA : typedScopeB;
              bestScope =
                  bestScope != null ? bestScope : scopeA != null ? scopeA.scope : scopeB.scope;
              return new OverlayScope(
                  bestScope,
                  slotsA.reconcile(
                      slotsB,
                      (slotA, slotB) -> {
                        // There are 5 different join cases:
                        // 1) The type is present in joinedScopeA, not in joinedScopeB,
                        //    and not in functionScope. Just use the one in A.
                        // 2) The type is present in joinedScopeB, not in joinedScopeA,
                        //    and not in functionScope. Just use the one in B.
                        // 3) The type is present in functionScope and joinedScopeA, but
                        //    not in joinedScopeB. Join the two types.
                        // 4) The type is present in functionScope and joinedScopeB, but
                        //    not in joinedScopeA. Join the two types.
                        // 5) The type is present in joinedScopeA and joinedScopeB. Join
                        //    the two types.
                        String name = slotA != null ? slotA.getName() : slotB.getName();
                        if (slotB == null || slotB.getType() == null) {
                          TypedVar fnSlot = typedScopeB != null ? typedScopeB.getSlot(name) : null;
                          JSType fnSlotType = fnSlot != null ? fnSlot.getType() : null;
                          if (fnSlotType != null && fnSlotType != slotA.getType()) {
                            // Case #3
                            JSType joinedType = slotA.getType().getLeastSupertype(fnSlotType);
                            return joinedType != slotA.getType()
                                ? new OverlaySlot(name, joinedType)
                                : slotA;
                          } else {
                            // Case #1
                            return slotA;
                          }
                        } else if (slotA == null || slotA.getType() == null) {
                          TypedVar fnSlot = typedScopeA != null ? typedScopeA.getSlot(name) : null;
                          JSType fnSlotType = fnSlot != null ? fnSlot.getType() : null;
                          if (fnSlotType != null && fnSlotType != slotB.getType()) {
                            // Case #4
                            JSType joinedType = slotB.getType().getLeastSupertype(fnSlotType);
                            return joinedType != slotB.getType()
                                ? new OverlaySlot(name, joinedType)
                                : slotB;
                          } else {
                            // Case #2
                            return slotB;
                          }
                        }
                        // Case #5
                        if (slotA.getType() == slotB.getType()) {
                          return slotA;
                        }
                        JSType joinedType = slotA.getType().getLeastSupertype(slotB.getType());
                        return joinedType != slotA.getType()
                            ? new OverlaySlot(name, joinedType)
                            : slotA;
                      }));
            });
  }

  private static class OverlayScope {
    final TypedScope scope;
    final PMap<String, OverlaySlot> slots;

    OverlayScope(TypedScope scope) {
      this.scope = checkNotNull(scope);
      this.slots = EMPTY_SLOTS;
    }

    OverlayScope(TypedScope scope, PMap<String, OverlaySlot> slots) {
      this.scope = checkNotNull(scope);
      this.slots = slots;
    }

    @SuppressWarnings("ReferenceEquality") // JSType#equals is expensive, so use identity.
    OverlayScope infer(String name, JSType type) {
      // TODO(sdh): variants that do or don't clobber properties (i.e. look up and modify instead)
      OverlaySlot slot = slots.get(name);
      if (slot != null && type == slot.type) {
        return this;
      }
      return new OverlayScope(scope, slots.plus(name, new OverlaySlot(name, type)));
    }

    StaticTypedSlot getSlot(String name) {
      OverlaySlot slot = slots.get(name);
      return slot != null ? slot : scope.getSlot(name);
    }
  }

  private static class OverlaySlot implements StaticTypedSlot {
    // TODO(sdh): add a final PMap<String, OverlaySlot> for properties
    final String name;
    final JSType type;

    OverlaySlot(String name, JSType type) {
      this.name = name;
      this.type = type;
    }

    @Override
    public String getName() {
      return name;
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
    public StaticTypedRef getDeclaration() {
      return null;
    }

    @Override
    public JSDocInfo getJSDocInfo() {
      return null;
    }

    @Override
    public StaticTypedScope getScope() {
      throw new UnsupportedOperationException();
    }
  }

  private static final PMap<String, OverlaySlot> EMPTY_SLOTS = HamtPMap.empty();
}
