/*
 * Copyright 2017 The Closure Compiler Authors.
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
 * @fileoverview
 * @suppress {uselessCode}
 */

var someVar;

/** Example test */
function mathTest() {
  assert.isTrue(1 > 0);
  assert.isFalse(2 < 0);
}

/** Another example */
function nullAndUndefinedTest() {
  assert.isNull(false || null);
  assert.isUndefined(someVar);
}

/** Truthiness! */
function okTest() {
  assert.isOk('a string');
}

/** The .to.not.be variations. */
function negativeTest() {
  assert.isNotTrue(1 > 0);
  assert.isNotFalse(2 < 0);
  assert.isNotNull(false || null);
  assert.isDefined(someVar);
  assert.isNotOk('a string');
}

/** Tests method (not property) expectations */
function methodTests() {
  assert.equal(1 + 1, 2);
  assert.notEqual(1 + 1, 3);
  assert.notEqual(1 + 1, 3);
  assert.deepEqual([1, 2], [1, 2]);
  assert.notDeepEqual([1, 2], [2, 1]);
  assert.instanceOf([], Array);
  assert.instanceOf([], Array);
  assert.oneOf(1, [2, 1]);
  assert.include([1, 2], 1);
  assert.lengthOf([1, 2], 2);
}
