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
package com.google.javascript.jscomp.graph;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Iterators.filter;
import static com.google.common.collect.Multimaps.asMap;

import com.google.common.annotations.GwtCompatible;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A Union-Find implementation.
 *
 * <p>This class implements Union-Find algorithm with rank and path
 * compression.
 *
 * <p>See <a
 * href="http://www.algorithmist.com/index.php?title=Union_Find&oldid=7575">
 * algorithmist</a> for more detail.
 *
 * @param <E> element type
 */
@GwtCompatible
public class StandardUnionFind<E> implements Serializable, UnionFind<E> {

  /** All values with the same root node are in the same equivalence set. */
  private final Map<E, Node<E>> elmap = new LinkedHashMap<>();

  /** Creates an empty UnionFind structure. */
  public StandardUnionFind() {
  }

  /**
   * Creates an UnionFind structure being a copy of other structure.
   * The created structure is optimal in a sense that the paths to
   * the root from any element will have a length of at most 1.
   *
   * @param other structure to be copied
   */
  public StandardUnionFind(UnionFind<E> other) {
    for (E elem : other.elements()) {
      union(other.find(elem), elem);
    }
  }

  @Override
  public void add(@Nullable E e) {
    union(e, e);
  }

  public void addAll(Iterable<E> es) {
    for (E e : es) {
      this.add(e);
    }
  }

  @CanIgnoreReturnValue
  @Override
  public E union(@Nullable E a, @Nullable E b) {
    Node<E> nodeA = findRootOrCreateNode(a);
    Node<E> nodeB = findRootOrCreateNode(b);

    if (nodeA == nodeB) {
      return nodeA.element;
    }
    // If possible, prefer nodeA over nodeB, to preserve insertion order.
    if (nodeA.rank >= nodeB.rank) {
      nodeB.parent = nodeA;
      nodeA.size += nodeB.size;
      if (nodeA.rank == nodeB.rank) {
        nodeA.rank++;
      }
      return nodeA.element;
    }
    nodeA.parent = nodeB;
    nodeB.size += nodeA.size;
    E temp = nodeB.element;
    nodeB.element = nodeA.element;
    nodeA.element = temp;
    return nodeB.element;
  }

  @Override
  public E find(@Nullable E e) {
    checkArgument(elmap.containsKey(e), "Element does not exist: %s", e);
    return findRoot(elmap.get(e)).element;
  }

  @Override
  public boolean areEquivalent(@Nullable E a, @Nullable E b) {
    E aRep = find(a);
    E bRep = find(b);
    return aRep == bRep;
  }

  @Override
  public Set<E> elements() {
    return Collections.unmodifiableSet(elmap.keySet());
  }

  @Override
  public ImmutableList<ImmutableSet<E>> allEquivalenceClasses() {
    SetMultimap<Node<E>, E> groupsTmp =
        MultimapBuilder.linkedHashKeys().linkedHashSetValues().build();
    for (Node<E> elem : elmap.values()) {
      groupsTmp.put(findRoot(elem), elem.element);
    }
    ImmutableList.Builder<ImmutableSet<E>> result = ImmutableList.builder();
    for (Set<E> group : asMap(groupsTmp).values()) {
      result.add(ImmutableSet.copyOf(group));
    }
    return result.build();
  }

  /**
   * Return the reprsentative elements of all the equivalence classes.
   *
   * <p>This is a "snapshot" view of the representatives at the time the method was called.
   */
  public ImmutableSet<E> allRepresentatives() {
    return this.elmap.values().stream()
        .filter((n) -> n == n.parent)
        .map((n) -> n.element)
        .collect(toImmutableSet());
  }

  /**
   * If e is already in a non-trivial equivalence class, that is, a class with
   * more than two elements, then return the {@link Node} corresponding to the
   * representative element. Otherwise, if e sits in an equivalence class by
   * itself, then create a {@link Node}, put it into elmap and return it.
   */
  private Node<E> findRootOrCreateNode(E e) {
    Node<E> node = elmap.get(e);
    if (node != null) {
      return findRoot(node);
    }
    node = new Node<E>(e);
    elmap.put(e, node);
    return node;
  }

  /**
   * Given a {@link Node}, walk the parent field as far as possible, until
   * reaching the root, which is the {@link Node} for the current
   * representative of this equivalence class. To achieve low runtime
   * complexity, also compress the path, by making each node a direct child of
   * the root.
   */
  private Node<E> findRoot(Node<E> node) {
    if (node.parent != node) {
      node.parent = findRoot(node.parent);
    }
    return node.parent;
  }

  @Override
  public Set<E> findAll(@Nullable final E value) {
    checkArgument(elmap.containsKey(value), "Element does not exist: %s", value);

    final Predicate<Object> isSameRoot = new Predicate<Object>() {

      /** some node that's close to the root, or null */
      Node<E> nodeForValue = elmap.get(value);

      @Override
      public boolean apply(@Nullable Object b) {
        if (Objects.equal(value, b)) {
          return true;
        }
        Node<E> nodeForB = elmap.get(b);
        if (nodeForB == null) {
          return false;
        }
        nodeForValue = findRoot(nodeForValue);
        return findRoot(nodeForB) == nodeForValue;
      }
    };

    return new AbstractSet<E>() {

      @Override public boolean contains(Object o) {
        return isSameRoot.apply(o);
      }

      @Override public Iterator<E> iterator() {
        return filter(elmap.keySet().iterator(), isSameRoot);
      }

      @Override public int size() {
        return findRoot(elmap.get(value)).size;
      }
    };
  }

  /** The internal node representation. */
  private static class Node<E> {
    /** The parent node of this element. */
    Node<E> parent;

    /** The element represented by this node. */
    E element;

    /** A bound on the depth of the subtree rooted to this node. */
    int rank = 0;

    /**
     * If this node is the root of a tree, this is the number of elements in the
     * tree. Otherwise, it's undefined.
     */
    int size = 1;

    Node(E element) {
      this.parent = this;
      this.element = element;
    }
  }
}
