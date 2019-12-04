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

import java.util.function.BiPredicate;
import javax.annotation.Nullable;

/** A minimal interface for null-hostile, persistent immutable maps. */
public interface PMap<K, V> {

  /** Returns whether this map is empty. */
  boolean isEmpty();

  /** Returns an iterable for the values in this map. */
  Iterable<V> values();

  /** Returns an iterable for the keys in this map. */
  Iterable<K> keys();

  /** Retrieves the given key from the map, or returns null if it is not present. */
  @Nullable V get(K key);

  /**
   * Returns a new map with the given key-value pair added. If the value is already present, then
   * this same map will be returned.
   */
  PMap<K, V> plus(K key, V value);

  /**
   * Returns a new map with the given key removed. If the key was not present in the first place,
   * then this same map will be returned.
   */
  PMap<K, V> minus(K key);

  /**
   * Performs a reconcile operation to merge {@code this} and {@code that}.
   *
   * <p>{@code joiner} is called for each pair of entries, one from each map, which share the same
   * key and whose values are not {@link Object#equals}. This includes entries that are absent from
   * one of the maps, for which {@code null} is passed as the absent value.
   *
   * <p>The return of calling {@code joiner} will appear in the merged map at the key of the
   * original entry pair. The return may not be null. If the values in a pair of entries are {@link
   * Object#equals}, that value will be used directly in the result without calling {@code joiner}.
   *
   * <p>The first value passed to {@code joiner} comes from {@code this}, and the second value comes
   * from {@code that}. There are no guarantees on the source of {@code key}. Note that {@code that}
   * map must be the same implementation.
   */
  PMap<K, V> reconcile(PMap<K, V> that, Reconciler<K, V> joiner);

  /**
   * Checks equality recursively based on the given equivalence. Short-circuits as soon as a 'false'
   * result is found, or if a key in one map is missing from the other. The equivalence will only be
   * called on two non-null values. Note that {@code that} map must be the same implementation. Note
   * that the equivalence MUST be reflective (i.e. equivalence.test(x, x) == true).
   */
  boolean equivalent(PMap<K, V> that, BiPredicate<V, V> equivalence);

  /** See {@link #reconcile}. */
  @FunctionalInterface
  public interface Reconciler<K, V> {
    V merge(K key, @Nullable V thisVal, @Nullable V thatVal);
  }
}
