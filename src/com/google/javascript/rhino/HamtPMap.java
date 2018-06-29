/*
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Google Inc.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package com.google.javascript.rhino;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * An immutable sorted map with efficient (persistent) updates.
 *
 * <p>Uses a hash array mapped trie: http://en.wikipedia.org/wiki/Hash_array_mapped_trie.
 *
 * <p>This implementation stores the bare minimum in each node: a key (with its hash), value, mask,
 * and children. It is also optimized to take maximum advantage of binary operations on shared
 * trees. Specifically, {@link #reconcile} avoids recursing into entire subtrees if they are
 * identical objects. Null keys and values are not allowed. The implementation special-cases away
 * the EMPTY map as soon as possible, using 'null' instead for all the logic (since EMPTY violates
 * the invariant that key and value are non-null). Finally, we maintain an invariant that the
 * entry with the smallest hash code is always at the root of the tree, which avoids almost all
 * extra tree rebuilding during binary operations.
 */
// TODO(sdh): Consider using tricks from https://bendyworks.com/blog/leveling-clojures-hash-maps
// We need a solid way to profile the results to see if it's actually worth the extra code.
public final class HamtPMap<K, V> implements PMap<K, V>, Serializable {

  /**
   * Number of bits of fan-out at each level. May be anywhere from 1 (a binary tree) to 5 (for a
   * fan-out of 32). Anything larger requires using a long for the mask (which is ugly for the
   * JavaScript-compiled version), and zero is essentially a linked list. The relevant trade-off is
   * that more fan-out means shallower trees, which should lead to quicker look-up times and more
   * efficient updates, but less fan-out makes reconcile() and equivalent() more efficient for maps
   * that are mostly the same, since bigger common trees can be pruned from the operation.
   * Preliminary profiling suggests that for type-checking purposes, all nonzero values are roughly
   * comparable.
   */
  private static final int BITS = 4;

  /** Number of bits to shift off to get the most significant BITS number of bits. */
  private static final int BITS_SHIFT = 32 - BITS;

  /** Non-null key (exception: empty map has a null key). */
  private final K key;

  /** Hash of the key, right-shifted by BITS*depth. */
  private final int hash;

  /** Non-null value (exceptions: (1) empty map, (2) result of pivot, if not found). */
  private final V value;

  /** Bit mask indicating the children that are present (bitCount(mask) == children.length). */
  private final int mask;

  /** Non-null array of children. Elements are never reassigned. */
  private final HamtPMap<K, V>[] children;

  private static final HamtPMap<?, ?>[] EMPTY_CHILDREN = new HamtPMap<?, ?>[0];
  private static final HamtPMap<?, ?> EMPTY =
      new HamtPMap<>(null, 0, null, 0, emptyChildren());

  private HamtPMap(K key, int hash, V value, int mask, HamtPMap<K, V>[] children) {
    this.key = key;
    this.hash = hash;
    this.value = value;
    this.mask = mask;
    this.children = children;
  }

  /** Returns an empty map. */
  @SuppressWarnings("unchecked") // Empty immutable collection is safe to cast.
  public static <K, V> HamtPMap<K, V> empty() {
    return (HamtPMap<K, V>) EMPTY;
  }

  /** Returns an empty array of child maps. */
  @SuppressWarnings("unchecked") // Empty array is safe to cast.
  private static <K, V> HamtPMap<K, V>[] emptyChildren() {
    return (HamtPMap<K, V>[]) EMPTY_CHILDREN;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder().append("{");
    if (!isEmpty()) {
      appendTo(sb);
    }
    return sb.append("}").toString();
  }

  /** Appends this map's contents to a string builder. */
  private void appendTo(StringBuilder sb) {
    if (sb.length() > 1) {
      sb.append(", ");
    }
    sb.append(key).append(": ").append(value);
    for (HamtPMap<K, V> child : children) {
      child.appendTo(sb);
    }
  }

  /** Returns whether this map is empty. */
  @Override
  public boolean isEmpty() {
    return key == null;
  }

  /** Returns an iterable for a (possibly null) tree. */
  @Override
  public Iterable<V> values() {
    if (isEmpty()) {
      return Collections.<V>emptyList();
    }
    return () -> new Iter<>(this, map -> map.value);
  }

  /**
   * Retrieves the value associated with the given key from the map, or returns null if it is not
   * present.
   */
  @Override
  public V get(K key) {
    return !isEmpty() ? get(key, hash(key)) : null;
  }

  /** Internal recursive implementation of get(K). */
  private V get(K key, int hash) {
    if (hash == this.hash && key.equals(this.key)) {
      return this.value;
    }
    int bucket = bucket(hash);
    int bucketMask = 1 << bucket;
    return (mask & bucketMask) != 0 ? children[index(bucketMask)].get(key, shift(hash)) : null;
  }

  /**
   * Returns a new map with the given key-value pair added. If the value is already present, then
   * this same map will be returned.
   */
  @Override
  public HamtPMap<K, V> plus(K key, V value) {
    return !isEmpty()
        ? plus(key, hash(key), checkNotNull(value))
        : new HamtPMap<>(key, hash(key), value, 0, emptyChildren());
  }

  /** Internal recursive implementation of plus(K, V). */
  private HamtPMap<K, V> plus(K key, int hash, V value) {
    if (hash == this.hash && key.equals(this.key)) {
      return value.equals(this.value)
          ? this
          : new HamtPMap<>(key, hash, value, mask, children);
    }
    if (compareUnsigned(hash, this.hash) < 0) {
      return replaceRoot(key, hash, value);
    }
    int bucket = bucket(hash);
    hash = shift(hash);
    int bucketMask = 1 << bucket;
    int index = index(bucketMask);
    if ((mask & bucketMask) != 0) {
      // already a child, so overwrite
      HamtPMap<K, V> child = children[index];
      HamtPMap<K, V> newChild = child.plus(key, hash, value);
      return child == newChild ? this : withChildren(mask, replaceChild(children, index, newChild));
    } else {
      // insert at index
      HamtPMap<K, V> newChild = new HamtPMap<>(key, hash, value, 0, emptyChildren());
      return withChildren(mask | bucketMask, insertChild(children, index, newChild));
    }
  }

  private HamtPMap<K, V> replaceRoot(K key, int hash, V value) {
    int bucket = bucket(this.hash);
    int leafHash = shift(this.hash);
    int bucketMask = 1 << bucket;
    int index = index(bucketMask);
    HamtPMap<K, V>[] newChildren;
    if ((mask & bucketMask) != 0) {
      newChildren =
          replaceChild(children, index, children[index].plus(this.key, leafHash, this.value));
    } else {
      HamtPMap<K, V> newChild = new HamtPMap<>(this.key, leafHash, this.value, 0, emptyChildren());
      newChildren = insertChild(children, index, newChild);
    }
    return new HamtPMap<>(key, hash, value, mask | bucketMask, newChildren);
  }

  /**
   * Returns a new map with the given key removed. If the key was not present in the first place,
   * then this same map will be returned.
   */
  @Override
  public HamtPMap<K, V> minus(K key) {
    return !isEmpty() ? minus(key, hash(key), null) : this;
  }

  /**
   * Internal recursive implementation of minus(K). The value of the removed node is returned via
   * the 'value' array, if it is non-null.
   */
  private HamtPMap<K, V> minus(K key, int hash, V[] value) {
    if (hash == this.hash && key.equals(this.key)) {
      HamtPMap<K, V> result = deleteRoot(mask, children);
      if (value != null) {
        value[0] = this.value;
      }
      return result != null ? result : empty();
    }
    int bucket = bucket(hash);
    int bucketMask = 1 << bucket;
    if ((mask & bucketMask) == 0) {
      // not present, stop looking
      return this;
    }
    hash = shift(hash);
    int index = index(bucketMask);
    HamtPMap<K, V> child = children[index];
    HamtPMap<K, V> newChild = child.minus(key, hash, value);
    if (newChild == child) {
      return this;
    } else if (newChild == EMPTY) {
      return withChildren(mask & ~bucketMask, deleteChild(children, index));
    } else {
      return withChildren(mask, replaceChild(children, index, newChild));
    }
  }

  @Override
  public HamtPMap<K, V> reconcile(PMap<K, V> that, BiFunction<V, V, V> joiner) {
    HamtPMap<K, V> result =
        reconcile(
            !this.isEmpty() ? this : null,
            !that.isEmpty() ? (HamtPMap<K, V>) that : null,
            joiner);
    return result != null ? result : empty();
  }

  /** Internal recursive implementation of reconcile(HamtPMap, BiFunction), factoring out empies. */
  private static <K, V> HamtPMap<K, V> reconcile(
      @Nullable HamtPMap<K, V> t1,
      @Nullable HamtPMap<K, V> t2,
      BiFunction<V, V, V> joiner) {
    if (t1 == t2) {
      return t1;
    } else if (t1 == null) {
      V newValue = joiner.apply(null, t2.value);
      HamtPMap<K, V>[] newChildren = Arrays.copyOf(t2.children, t2.children.length);
      for (int i = 0; i < newChildren.length; i++) {
        newChildren[i] = reconcile(null, newChildren[i], joiner);
      }
      return newValue != null
          ? new HamtPMap<>(t2.key, t2.hash, newValue, t2.mask, newChildren)
          : deleteRoot(t2.mask, newChildren);
    } else if (t2 == null) {
      V newValue = joiner.apply(t1.value, null);
      HamtPMap<K, V>[] newChildren = Arrays.copyOf(t1.children, t1.children.length);
      for (int i = 0; i < newChildren.length; i++) {
        newChildren[i] = reconcile(newChildren[i], null, joiner);
      }
      return newValue != null
          ? new HamtPMap<>(t1.key, t1.hash, newValue, t1.mask, newChildren)
          : deleteRoot(t1.mask, newChildren);
    }

    // Try as hard as possible to return input trees exactly.
    boolean sameChildrenAs1 = true;
    boolean sameChildrenAs2 = true;

    // If the hashes are different, we need to keep the lower one at the top.
    int hashCmp = compareUnsigned(t1.hash, t2.hash);
    K key = t1.key;
    int hash = t1.hash;
    if (hashCmp < 0) {
      // t1.key is missing from t2
      t2 = t2.vacateRoot();
      sameChildrenAs2 = false;
    } else if (hashCmp > 0) {
      // t2.key is missing from t1
      t1 = t1.vacateRoot();
      sameChildrenAs1 = false;
      key = t2.key;
      hash = t2.hash;
    } else if (!t1.key.equals(t2.key)) {
      // Hash collision: try to rearrange t2 to have the same root as t1
      t2 = t2.pivot(t1.key, t1.hash);
      sameChildrenAs2 = false;
    }
    // Note: one or the other (but not both) tree may have a null key at root.

    V newValue = Objects.equals(t1.value, t2.value) ? t1.value : joiner.apply(t1.value, t2.value);
    int newMask = t1.mask | t2.mask;
    sameChildrenAs1 &= (newMask == t1.mask);
    sameChildrenAs2 &= (newMask == t2.mask);

    @SuppressWarnings("unchecked") // only used internally.
    HamtPMap<K, V>[] newChildren =
        newMask != 0
            ? (HamtPMap<K, V>[]) new HamtPMap<?, ?>[Integer.bitCount(newMask)]
            : emptyChildren();
    int mask = newMask;
    int index = 0;
    while (mask != 0) {
      int childBit = Integer.lowestOneBit(mask);
      mask &= ~childBit;
      HamtPMap<K, V> child1 = t1.getChild(childBit);
      HamtPMap<K, V> child2 = t2.getChild(childBit);
      newChildren[index] = reconcile(child1, child2, joiner);
      sameChildrenAs1 &= (newChildren[index] == child1);
      sameChildrenAs2 &= (newChildren[index] == child2);
      if (newChildren[index] != null) {
        index++;
      } else {
        newMask &= ~childBit;
      }
    }
    if (sameChildrenAs1 && t1.value.equals(newValue)) {
      return t1;
    } else if (sameChildrenAs2 && t2.value.equals(newValue)) {
      return t2;
    } else if (newValue == null) {
      return deleteRoot(newMask, newChildren);
    }
    return new HamtPMap<>(key, hash, newValue, newMask, newChildren);
  }

  /**
   * Checks equality recursively based on the given equivalence. Short-circuits as soon as a 'false'
   * result is found.
   */
  @Override
  public boolean equivalent(PMap<K, V> that, BiPredicate<V, V> equivalence) {
    return equivalent(
        !this.isEmpty() ? this : null, !that.isEmpty() ? (HamtPMap<K, V>) that : null, equivalence);
  }

  /** Internal recursive implementation of equivalent(HamtPMap, BiPredicate). */
  private static <K, V> boolean equivalent(
      @Nullable HamtPMap<K, V> t1,
      @Nullable HamtPMap<K, V> t2,
      BiPredicate<V, V> equivalence) {
    if (t1 == t2) {
      return true;
    } else if (t1 == null || t2 == null) {
      return false;
    }

    if (t1.hash != t2.hash) {
      // Due to our invariant, we can safely conclude that there's a discrepancy in the
      // keys without any extra work.
      return false;
    } else if (!t1.key.equals(t2.key)) {
      // Hash collision: try to rearrange t2 to have the same root as t1
      t2 = t2.pivot(t1.key, t1.hash);
      if (t2.key == null) {
        // t1.key not found in t2
        return false;
      }
    }

    if (!equivalence.test(t1.value, t2.value)) {
      return false;
    }
    int mask = t1.mask | t2.mask;
    while (mask != 0) {
      int childBit = Integer.lowestOneBit(mask);
      mask &= ~childBit;
      if (!equivalent(t1.getChild(childBit), t2.getChild(childBit), equivalence)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the index into the 'children' array for the given bit, which must have exactly one bit
   * set in its binary representation (i.e. must be a power of two).
   */
  private int index(int bit) {
    return Integer.bitCount(mask & (bit - 1));
  }

  /**
   * Returns the child for the given bit, which must have exactly one bit set. Returns null if there
   * is no child for that bit.
   */
  private HamtPMap<K, V> getChild(int bit) {
    return (mask & bit) != 0 ? children[index(bit)] : null;
  }

  /**
   * Perform the hash operation.  This is done once per object as it enters the map, then never
   * again since the result is passed around internally.  We reverse the bits since certain types
   * (such as small integers and short strings) have hash codes that only vary in the least
   * significant bits, but we use the most significant bits for bucketing in order to maintain
   * a canonical structure where the key with the smallest hash is always at the root of the
   * tree.
   */
  private static int hash(Object key) {
    return Integer.reverse(key.hashCode());
  }

  /** Return the current bucket index from the hash. */
  private static int bucket(int hash) {
    return hash >>> BITS_SHIFT;
  }

  /** Return a new hash with the next bucket number shifted off. */
  private static int shift(int hash) {
    return hash << BITS;
  }

  /** Unshift the bucket number back onto a hash. */
  private static int unshift(int hash, int bucket) {
    return (hash >>> BITS) | (bucket << BITS_SHIFT);
  }

  /** Compare two unsigned integers. */
  private static int compareUnsigned(int left, int right) {
    // NOTE: JDK 7 does not have a built-in operation for this, other than casting to longs.
    //       In JDK 8 it's just Integer.compareUnsigned(left, right).  For now we emulate it
    //       by shifting the sign bit away, with a fallback second compare only if needed.
    int diff = (left >>> 2) - (right >>> 2);
    return diff != 0 ? diff : (left & 3) - (right & 3);
  }

  /**
   * Returns a new version of this map with the given key at the root, and the root element moved to
   * some deeper node. If the key is not found, then value will be null.
   */
  @SuppressWarnings("unchecked")
  private HamtPMap<K, V> pivot(K key, int hash) {
    return pivot(key, hash, null, (V[]) new Object[1]);
  }

  /**
   * Internal recursive version of pivot. If parent is null then the result is used for the value in
   * the returned map. The value, if found, is stored in the 'result' array as a secondary return.
   */
  private HamtPMap<K, V> pivot(K key, int hash, HamtPMap<K, V> parent, V[] result) {
    int newMask = mask;
    HamtPMap<K, V>[] newChildren = this.children;
    if (hash == this.hash && key.equals(this.key)) {
      // Found the key: swap out this key/value with the parent and return the result in the holder.
      result[0] = this.value;
    } else {
      // Otherwise, see if the value might be present in a child.
      int searchBucket = bucket(hash);
      int replacementBucket = bucket(this.hash);

      if (searchBucket == replacementBucket) {
        int bucketMask = 1 << searchBucket;
        if ((mask & bucketMask) != 0) {
          // Found a child, call pivot recursively.
          int index = index(bucketMask);
          HamtPMap<K, V> child = newChildren[index];
          HamtPMap<K, V> newChild = child.pivot(key, shift(hash), this, result);
          newChildren = replaceChild(newChildren, index, newChild);
        }
      } else {
        int searchMask = 1 << searchBucket;
        if ((mask & searchMask) != 0) {
          int index = index(searchMask);
          HamtPMap<K, V> child = newChildren[index];
          HamtPMap<K, V> newChild = child.minus(key, shift(hash), result);
          if (!newChild.isEmpty()) {
            newChildren = replaceChild(newChildren, index, newChild);
          } else {
            newChildren = deleteChild(newChildren, index);
            newMask &= ~searchMask;
          }
        }
        int replacementMask = 1 << replacementBucket;
        int index = Integer.bitCount(newMask & (replacementMask - 1));
        if ((mask & replacementMask) != 0) {
          HamtPMap<K, V> child = newChildren[index];
          HamtPMap<K, V> newChild = child.plus(this.key, shift(this.hash), this.value);
          newChildren = replaceChild(newChildren, index, newChild);
        } else {
          newChildren =
              insertChild(
                  newChildren,
                  index,
                  new HamtPMap<>(this.key, shift(this.hash), this.value, 0, emptyChildren()));
          newMask |= replacementMask;
        }
      }
    }
    return parent != null
        ? new HamtPMap<>(parent.key, shift(parent.hash), parent.value, newMask, newChildren)
        : new HamtPMap<>(key, hash, result[0], newMask, newChildren);
  }

  /** Moves the root into the appropriate child. */
  private HamtPMap<K, V> vacateRoot() {
    int bucket = bucket(this.hash);
    int bucketMask = 1 << bucket;
    int index = index(bucketMask);
    if ((mask & bucketMask) != 0) {
      HamtPMap<K, V> newChild = children[index].plus(this.key, shift(this.hash), this.value);
      return new HamtPMap<>(null, 0, null, mask, replaceChild(children, index, newChild));
    }
    HamtPMap<K, V> newChild =
        new HamtPMap<>(this.key, shift(this.hash), this.value, 0, emptyChildren());
    return new HamtPMap<>(null, 0, null, mask | bucketMask, insertChild(children, index, newChild));
  }

  /** Returns a copy of this node with a different array of children. */
  private HamtPMap<K, V> withChildren(int mask, HamtPMap<K, V>[] children) {
    return mask == this.mask && children == this.children
        ? this
        : new HamtPMap<>(key, hash, value, mask, children);
  }

  /**
   * Returns a new map with the elements from children. One element is removed from one of the
   * children and promoted to a root node. If there are no children, returns null.
   */
  private static <K, V> HamtPMap<K, V> deleteRoot(int mask, HamtPMap<K, V>[] children) {
    if (mask == 0) {
      return null;
    }
    HamtPMap<K, V> child = children[0];
    int hashBits = Integer.numberOfTrailingZeros(mask);
    int newHash = unshift(child.hash, hashBits);
    HamtPMap<K, V> newChild = deleteRoot(child.mask, child.children);
    if (newChild == null) {
      int newMask = mask & ~Integer.lowestOneBit(mask);
      return new HamtPMap<>(child.key, newHash, child.value, newMask, deleteChild(children, 0));
    } else {
      return new HamtPMap<>(
          child.key, newHash, child.value, mask, replaceChild(children, 0, newChild));
    }
  }

  /** Returns a new array of children with an additional child inserted at the given index. */
  private static <K, V> HamtPMap<K, V>[] insertChild(
      HamtPMap<K, V>[] children, int index, HamtPMap<K, V> child) {
    @SuppressWarnings("unchecked") // only used internally.
    HamtPMap<K, V>[] newChildren = (HamtPMap<K, V>[]) new HamtPMap<?, ?>[children.length + 1];
    newChildren[index] = child;
    System.arraycopy(children, 0, newChildren, 0, index);
    System.arraycopy(children, index, newChildren, index + 1, children.length - index);
    return newChildren;
  }

  /** Returns a new array of children with the child at the given index replaced. */
  private static <K, V> HamtPMap<K, V>[] replaceChild(
      HamtPMap<K, V>[] children, int index, HamtPMap<K, V> child) {
    HamtPMap<K, V>[] newChildren = Arrays.copyOf(children, children.length);
    newChildren[index] = child;
    return newChildren;
  }

  /** Returns a new array of children with the child at the given index deleted. */
  private static <K, V> HamtPMap<K, V>[] deleteChild(
      HamtPMap<K, V>[] children, int index) {
    if (children.length == 1) {
      // Note: index should always be zero.
      return emptyChildren();
    }
    @SuppressWarnings("unchecked") // only used internally.
    HamtPMap<K, V>[] newChildren = (HamtPMap<K, V>[]) new HamtPMap<?, ?>[children.length - 1];
    System.arraycopy(children, 0, newChildren, 0, index);
    System.arraycopy(children, index + 1, newChildren, index, children.length - index - 1);
    return newChildren;
  }

  /** Iterates sequentially over a tree. */
  private static class Iter<K, V, O> implements Iterator<O> {
    final Deque<HamtPMap<K, V>> queue = new ArrayDeque<>();
    final Function<HamtPMap<K, V>, O> transformer;

    Iter(HamtPMap<K, V> map, Function<HamtPMap<K, V>, O> transformer) {
      this.transformer = transformer;
      if (!map.isEmpty()) {
        queue.add(map);
      }
    }

    @Override
    public boolean hasNext() {
      return !queue.isEmpty();
    }

    @Override
    public O next() {
      HamtPMap<K, V> top = queue.removeFirst();
      for (int i = top.children.length - 1; i >= 0; i--) {
        queue.add(top.children[i]);
      }
      return transformer.apply(top);
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  /** Throws an assertion error if the map invariant is violated. */
  @VisibleForTesting
  HamtPMap<K, V> assertCorrectStructure() {
    if (isEmpty()) {
      return this;
    }
    int hash = hash(key);
    for (int i = 0; i < children.length; i++) {
      int childHash = hash(children[i].key);
      if (compareUnsigned(childHash, hash) < 0) {
          throw new AssertionError("Invalid map has decreasing hash " + children[i].key + "("
              + Integer.toHexString(childHash) + ") beneath " + key + "("
              + Integer.toHexString(hash) + ": " + this);
      }
      children[i].assertCorrectStructure();
    }
    return this;
  }
}
