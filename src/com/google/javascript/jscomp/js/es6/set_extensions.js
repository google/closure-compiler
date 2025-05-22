/*
 * Copyright 2025 The Closure Compiler Authors.
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

/**
 * @fileoverview Polyfills for ECMAScript 2025 Set methods.
 */
'require util/polyfill';
'require es6/set'
'require es6/map'

$jscomp.polyfill('Set.prototype.union', function(orig) {
  if (orig) return orig;

  /**
   * Returns a new set containing elements which are in either this set or the
   * given set-like object.
   * Follows the steps in
   * https://tc39.es/proposal-set-methods/#sec-set.prototype.union
   * @this {!Set}
   * @param {!SetLike} other
   * @return {!Set}
   * @throws {TypeError} If this is not a Set or other is not set-like.
   */
  function union(other) {
    // Perform ? RequireInternalSlot(O, [[SetData]]).
    $jscomp.checkIsSetInstance(this);

    // Let otherRec be ? GetSetRecord(other).
    $jscomp.checkIsSetLike(other);

    // Let keysIter be ? GetIteratorFromMethod(otherRec.[[Set]],
    // otherRec.[[Keys]]).
    var resultSet = new Set(this);

    //  Let keysIter be ? GetIteratorFromMethod(otherRec.[[Set]],
    //  otherRec.[[Keys]]).
    var iterator = $jscomp.checkIsValidIterator(other.keys())

    // var next be true.
    var next = iterator.next();
    // Repeat, while next is not false,
    while (!next.done) {
      resultSet.add(next.value);
      next = iterator.next();
    }
    // Let result be OrdinaryObjectCreate(%Set.prototype%, « [[SetData]] »).
    // Set result.[[SetData]] to resultSetData.
    // Return result.
    return resultSet;
  };

  return union;
}, 'es_next', 'es6');

$jscomp.polyfill('Set.prototype.difference', function(orig) {
  if (orig) return orig;

  /**
   * Returns a new set containing elements in this set but not in the given set.
   * https://tc39.es/proposal-set-methods/#sec-set.prototype.difference
   * @this {!Set}
   * @param {!SetLike} other
   * @return {!Set}
   */
  function difference(other) {
    $jscomp.checkIsSetInstance(this);
    $jscomp.checkIsSetLike(other);

    var sets = $jscomp.getSmallerAndLargerSets(this, other);
    var resultSet = new Set(this);
    var smallerSetIterator = sets.smallerSetIterator;
    var largerSet = sets.largerSet;

    var next = smallerSetIterator.next();
    while (!next.done) {
      if (largerSet.has(next.value)) {
        resultSet.delete(next.value);
      }
      next = smallerSetIterator.next();
    }

    return resultSet;
  };

  return difference;
}, 'es_next', 'es6');

$jscomp.polyfill('Set.prototype.symmetricDifference', function(orig) {
  if (orig) return orig;

  /**
   * Returns a new set containing elements which are in either this set or the
   * given set, but not in both.
   * https://tc39.es/proposal-set-methods/#sec-set.prototype.symmetricdifference
   * @this {!Set}
   * @param {!SetLike} other
   * @return {!Set}
   */
  function symmetricDifference(other) {
    $jscomp.checkIsSetInstance(this);
    $jscomp.checkIsSetLike(other);

    var resultSet = new Set(this);

    var iterator = $jscomp.checkIsValidIterator(other.keys())
    var next = iterator.next();
    while (!next.done) {
      if (this.has(next.value)) {
        resultSet.delete(next.value);
      } else {
        resultSet.add(next.value);
      }
      next = iterator.next();
    }
    return resultSet;
  };

  return symmetricDifference;
}, 'es_next', 'es6');

$jscomp.polyfill('Set.prototype.intersection', function(orig) {
  if (orig) return orig;

  /**
   * Returns a new set containing elements in both this set and the given set.
   * https://tc39.es/proposal-set-methods/#sec-set.prototype.intersection
   * @this {!Set}
   * @param {!SetLike} other
   * @return {!Set}
   */
  function intersection(other) {
    $jscomp.checkIsSetInstance(this);
    $jscomp.checkIsSetLike(other);

    var resultSet = new Set();

    var sets = $jscomp.getSmallerAndLargerSets(this, other);
    var smallerSetIterator = sets.smallerSetIterator;
    var largerSet = sets.largerSet;

    var next = smallerSetIterator.next();
    while (!next.done) {
      if (largerSet.has(next.value)) {
        resultSet.add(next.value);
      }
      next = smallerSetIterator.next();
    }
    return resultSet;
  };

  return intersection;
}, 'es_next', 'es6');


$jscomp.polyfill('Set.prototype.isSubsetOf', function(orig) {
  if (orig) return orig;

  /**
   * Returns a boolean indicating if all elements of this set are in the given
   * set.
   * https://tc39.es/proposal-set-methods/#sec-set.prototype.issubsetof
   * @this {!Set}
   * @param {!SetLike} other
   * @return {boolean}
   */
  function isSubsetOf(other) {
    $jscomp.checkIsSetInstance(this);
    $jscomp.checkIsSetLike(other);

    if (this.size > other.size) {
      return false;
    }
    var iterator = this.keys();
    var next = iterator.next();
    while (!next.done) {
      if (!other.has(next.value)) {
        return false;
      }
      next = iterator.next();
    }
    return true;
  };

  return isSubsetOf;
}, 'es_next', 'es6');


$jscomp.polyfill('Set.prototype.isSupersetOf', function(orig) {
  if (orig) return orig;

  /**
   * Returns a boolean indicating if all elements of the given set are in this
   * set.
   * https://tc39.es/proposal-set-methods/#sec-set.prototype.issupersetof
   * @this {!Set}
   * @param {!SetLike} other
   * @return {boolean}
   */
  function isSupersetOf(other) {
    $jscomp.checkIsSetInstance(this);
    $jscomp.checkIsSetLike(other);

    if (this.size < other.size) {
      return false;
    }

    var iterator = $jscomp.checkIsValidIterator(other.keys());
    var next = iterator.next();
    while (!next.done) {
      if (!this.has(next.value)) {
        return false;
      }
      next = iterator.next();
    }
    return true;
  };

  return isSupersetOf;
}, 'es_next', 'es6');


$jscomp.polyfill('Set.prototype.isDisjointFrom', function(orig) {
  if (orig) return orig;

  /**
   * Returns a boolean indicating if this set has no elements in common with
   * the given set.
   * https://tc39.es/proposal-set-methods/#sec-set.prototype.isdisjointfrom
   * @this {!Set}
   * @param {!SetLike} other
   * @return {boolean}
   */
  function isDisjointFrom(other) {
    $jscomp.checkIsSetInstance(this);
    $jscomp.checkIsSetLike(other);

    var sets = $jscomp.getSmallerAndLargerSets(this, other);
    var smallerSetIterator = sets.smallerSetIterator;
    var largerSet = sets.largerSet;

    var next = smallerSetIterator.next();
    while (!next.done) {
      if (largerSet.has(next.value)) {
        return false;
      }
      next = smallerSetIterator.next();
    }
    return true;
  };
  return isDisjointFrom;
}, 'es_next', 'es6');

/**
 * Checks that the given object is set-like.
 * @param {!Object} other
 * @throws {TypeError} If other is not set-like or lacks required properties.
 */
$jscomp.checkIsSetLike =
    function(other) {
  // Check that other is an object that has a size, keys, and has methods.
  if ((typeof other !== 'object' || other === null) ||
      (typeof other.size !== 'number' || other.size < 0) ||
      (typeof other.keys !== 'function') || (typeof other.has !== 'function')) {
    throw new TypeError('Argument must be set-like');
  }
};

/**
 * Checks that the given object is a valid iterator object and returns it.
 * @param {!Object} iterator An iterator object.
 * @return {!Object} The iterator object.
 * @throws {TypeError} If the iterator is not a valid iterator object.
 */
  $jscomp.checkIsValidIterator =
        function(iterator) {
  if (typeof iterator !== 'object' || iterator === null ||
      typeof iterator.next !== 'function') {
    throw new TypeError('Invalid iterator.');
  }
  return iterator;
};

/**
 * Helper function to identify the smaller and larger sets.
 * Returns an object containing {smallerSetIterator, largerSet}
 * @param {!Set} thisSet
 * @param {!SetLike} otherSet
 * @return {{smallerSetIterator: !Object, largerSet: !SetLike}}
 */
$jscomp.getSmallerAndLargerSets =
    function(thisSet, otherSet) {
  // Optimize for the case where this set is smaller than the other set.
  var smallerSetIterator, largerSet;
  if (thisSet.size <= otherSet.size) {
    return {smallerSetIterator: thisSet.keys(), largerSet: otherSet};
  } else {
    // need to validate the iterator, since `other` may only be a SetLike.
    return {
      smallerSetIterator: $jscomp.checkIsValidIterator(otherSet.keys()),
      largerSet: thisSet
    };
  }
};

/**
 * Checks that the obj being called on is a Set instance.
 * @param {?} obj The object being called on.
 * @throws {TypeError} If the object is not a Set instance.
 */
$jscomp.checkIsSetInstance = function(obj) {
  if (!(obj instanceof Set)) {
    throw new TypeError('Method must be called on an instance of Set.');
  }
};
