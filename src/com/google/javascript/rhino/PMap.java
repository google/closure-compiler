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

import javax.annotation.Nullable;

/** A minimal interface for null-hostile, persistent immutable maps. */
public interface PMap<K, V> {

  /** Returns whether this map is empty. */
  boolean isEmpty();

  /** Returns an iterable for the values in this map. */
  Iterable<V> values();

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
   * Performs a reconcile operation. The joiner is called for all conflicting (based on
   * Object#equals) values between the two maps, or if a key is missing from one map. If both maps
   * have equal values then that value will be added directly to the result without calling the
   * joiner. If the joiner returns null, then the entry is removed.  The first argument to the
   * joined always comes from 'this' map, and the second is always from 'that'.  Note that {@code
   * that} map must be the same implementation.
   */
  PMap<K, V> reconcile(PMap<K, V> that, BiFunction<V, V, V> joiner);

  /**
   * Checks equality recursively based on the given equivalence. Short-circuits as soon as a 'false'
   * result is found, or if a key in one map is missing from the other. The equivalence will only be
   * called on two non-null values. Note that {@code that} map must be the same implementation. Note
   * that the equivalence MUST be reflective (i.e. equivalence.test(x, x) == true).
   */
  boolean equivalent(PMap<K, V> that, BiPredicate<V, V> equivalence);

  // TODO(sdh): replace this with the Java 8 version once we're able to.
  /** See java.util.function.BiFunction. */
  interface BiFunction<A, B, C> {
    C apply(A a, B b);
  }

  // TODO(sdh): replace this with the Java 8 version once we're able to.
  /** See java.util.function.BiPredicate. */
  interface BiPredicate<A, B> {
    boolean test(A a, B b);
  }
}
