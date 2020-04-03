/*
 * Copyright 2019 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.disambiguate;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.TypeMismatch;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph.LinkedDiGraphNode;
import com.google.javascript.jscomp.graph.LowestCommonAncestorFinder;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.ArrayList;
import java.util.Collection;

/** Builds a graph of the type-system from a specified set of seed types. */
final class TypeGraphBuilder {

  /**
   * The type relationship that caused an edge to be created.
   *
   * <p>This information is only retained for diagnostics, not correctness.
   */
  enum EdgeReason {
    ALGEBRAIC,
    ENUM_ELEMENT,
    FORCED,
    INTERFACE,
    PROTOTYPE;
  }

  private final TypeFlattener flattener;
  private final LowestCommonAncestorFinder<FlatType, Object> lcaFinder;

  private final LinkedDiGraphNode<FlatType, Object> topNode;

  /**
   * The graph of types as defined by `holdsInstanceOf`.
   *
   * <p>We use `holdsInstanceOf` rather than `isSupertypeOf` because we only actaully care about
   * edges that were "used" in the program. If instances never flow over an edge at runtime, then
   * properties also don't need to be tracked across that edge either. Of course, since we don't
   * track the types of all assignments in a program, many of the edges are `isSupertypeOf` edges
   * that we include to be conservative.
   *
   * <p>This graph, when fully constructed, is still only an approximation. This is the due to both
   * memory and time constraints. The following quirks are expected:
   *
   * <ul>
   *   <li>Some types, such as primitives, will not have nodes.
   *   <li>Transitive edges (shortcut edges for which there exist alternate paths) are kept minimal.
   * </ul>
   */
  private LinkedDirectedGraph<FlatType, Object> typeHoldsInstanceGraph =
      LinkedDirectedGraph.createWithoutAnnotations();

  private final ArrayList<TypeMismatch> deferredForcedEdges = new ArrayList<>();

  TypeGraphBuilder(
      TypeFlattener flattener,
      LowestCommonAncestorFinder.Factory<FlatType, Object> lcaFinderFactory) {
    this.flattener = flattener;
    this.lcaFinder = lcaFinderFactory.create(this.typeHoldsInstanceGraph);

    this.topNode =
        this.typeHoldsInstanceGraph.createNode(this.flattener.flatten(JSTypeNative.ALL_TYPE));
  }

  public void add(FlatType flat) {
    this.addInternal(flat);
  }

  public void addAll(Collection<FlatType> flats) {
    flats.forEach(this::add);
  }

  public void addForcedEdge(TypeMismatch mismatch) {
    this.deferredForcedEdges.add(mismatch);

    // The types on both sides of the edge need to be inserted immediately.
    this.addInternal(mismatch.getFound());
    this.addInternal(mismatch.getRequired());
  }

  public LinkedDirectedGraph<FlatType, Object> build() {
    this.typeHoldsInstanceGraph.getNodes().forEach(this::connectUnionWithAncestors);

    /**
     * A reference of the "required" type was forced to hold an instance of the "found" type.
     *
     * <p>This is done after unions are inserted because we don't want forced edges to influence the
     * LCAs of the unions' members. LCAs are being used to determine "what types could this union
     * have safely been asigned to". Considering forced edges would be incorrect for that purpose.
     */
    for (TypeMismatch mismatch : this.deferredForcedEdges) {
      this.connectSourceToDest(
          this.addInternal(mismatch.getRequired()),
          EdgeReason.FORCED,
          this.addInternal(mismatch.getFound()));
    }

    LinkedDirectedGraph<FlatType, Object> temp = this.typeHoldsInstanceGraph;
    this.typeHoldsInstanceGraph = null;
    return temp;
  }

  /**
   * During initial lattice construction unions were only given outbound edges, here we add any
   * ncessary inbound ones.
   *
   * <p>We defer this operation because adding union-to-union and common-supertype-to-union edges,
   * is a hard problem. Solving it after all other types are in place makes it easier.
   */
  private void connectUnionWithAncestors(LinkedDiGraphNode<FlatType, Object> unionNode) {
    FlatType flatUnion = unionNode.getValue();
    if (!flatUnion.hasArity(FlatType.Arity.UNION)) {
      return;
    }

    /**
     * Connect the LCAs to the union.
     *
     * <p>The union itself will be found in most cases, but since we don't add self-edges, that
     * won't matter.
     *
     * <p>Some of these edges may pollute the "lattice-ness" of the graph, but all the invariants we
     * actually care about will be maintained. If disambiguation is too slow and stricter invariants
     * would help, we could be more carful.
     */
    checkState(!unionNode.getOutEdges().isEmpty());
    for (FlatType lca : this.lcaFinder.findAll(flatUnion.getTypeUnion())) {
      this.connectSourceToDest(
          checkNotNull(this.typeHoldsInstanceGraph.getNode(lca)), EdgeReason.ALGEBRAIC, unionNode);
    }
  }

  /** Insert {@code type} and all necessary related types into the datastructures of this pass. */
  private LinkedDiGraphNode<FlatType, Object> addInternal(JSType type) {
    return this.addInternal(this.flattener.flatten(type));
  }

  /** Insert {@code flat} and all necessary related types into the datastructures of this pass. */
  private LinkedDiGraphNode<FlatType, Object> addInternal(FlatType flat) {
    LinkedDiGraphNode<FlatType, Object> flatNode = this.typeHoldsInstanceGraph.getNode(flat);
    if (flatNode != null) {
      return flatNode;
    }
    flatNode = this.typeHoldsInstanceGraph.createNode(flat);

    if (flat.hasArity(FlatType.Arity.UNION)) {
      for (FlatType alt : flat.getTypeUnion()) {
        this.connectSourceToDest(flatNode, EdgeReason.ALGEBRAIC, this.addInternal(alt));
      }
      return flatNode;
    }

    if (flat.getTypeSingle().isEnumElementType()) {
      LinkedDiGraphNode<FlatType, Object> elementNode =
          this.addInternal(flat.getTypeSingle().getEnumeratedTypeOfEnumElement());
      this.connectSourceToDest(elementNode, EdgeReason.ENUM_ELEMENT, flatNode);
      return flatNode;
    }

    if (flat.getTypeSingle().isObjectType()) {
      ObjectType flatObjectType = flat.getTypeSingle().toMaybeObjectType();

      for (ObjectType ifaceType : ownAncestorInterfacesOf(flatObjectType)) {
        this.connectSourceToDest(this.addInternal(ifaceType), EdgeReason.INTERFACE, flatNode);
      }

      JSType prototypeType = flatObjectType.getImplicitPrototype();
      if (prototypeType == null) {
        this.connectSourceToDest(this.topNode, EdgeReason.ALGEBRAIC, flatNode);
      } else {
        this.connectSourceToDest(this.addInternal(prototypeType), EdgeReason.PROTOTYPE, flatNode);
      }

      /**
       * Make sure we include all instance types we know about.
       *
       * <p>If there are no direct property accesses off of the instance type of this prototype we
       * won't see it during AST traversal. However, the instance type may still be used.
       */
      if (flatObjectType.isFunctionPrototypeType()) {
        FunctionType ownerFunction = flatObjectType.getOwnerFunction();
        if (ownerFunction.hasInstanceType()) {
          /**
           * Don't put an edge in.
           *
           * <p>Recursion up this type's prototype chain will create any required edges.
           * Additionally, this may not actually be the instance type of `flatObjectType`.
           *
           * <p>TODO(nickreid): Find out what syntax was causing this and add a test.
           */
          this.addInternal(ownerFunction.getInstanceType());
        }
      }

      return flatNode;
    }

    throw new AssertionError("Unexpected type: " + flat);
  }

  /**
   * Returns the interfaces directly implemented or extended by {@code type}.
   *
   * <p>This is distinct from any of the methods on {@link FunctionType}. Specifically, the result
   * only contains:
   *
   * <ul>
   *   <li>own/direct supertypes
   *   <li>supertypes that are actually interfaces
   * </ul>
   */
  private static ImmutableList<ObjectType> ownAncestorInterfacesOf(ObjectType type) {
    FunctionType ctorType = type.getConstructor();
    if (ctorType == null) {
      return ImmutableList.of();
    }

    final Collection<ObjectType> ifaceTypes;
    if (ctorType.isInterface()) {
      ifaceTypes = ctorType.getExtendedInterfaces();
    } else if (ctorType.isConstructor()) {
      ifaceTypes = ctorType.getOwnImplementedInterfaces();
    } else {
      throw new AssertionError();
    }

    if (ifaceTypes.isEmpty()) {
      return ImmutableList.of();
    }

    return ifaceTypes.stream()
        .filter((t) -> t.getConstructor() != null && t.getConstructor().isInterface())
        .collect(toImmutableList());
  }

  private void connectSourceToDest(
      LinkedDiGraphNode<FlatType, Object> source,
      EdgeReason reason,
      LinkedDiGraphNode<FlatType, Object> dest) {
    if (source.equals(dest)
        || this.typeHoldsInstanceGraph.isConnectedInDirection(source, (t) -> true, dest)) {
      return;
    }

    this.typeHoldsInstanceGraph.connect(source, reason, dest);
  }
}
