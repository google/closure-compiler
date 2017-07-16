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
 * @fileoverview RefasterJS templates for converting from Chai assertions like
 *
 *    expect(thing).to.be.true;
 *
 * to
 *
 *    assert.isTrue(thing);
 *
 * TODO(tbreisacher): Expand this to include "expect(thing).to.be.null" etc.
 * Then, once it's in a good state, mention it at go/js-practices/testing#chai
 */

/**
 * @param {?} param
 */
function before_true(param) {
  expect(param).to.be.true;
}

/**
 * @param {?} param
 */
function after_true(param) {
  assert.isTrue(param);
}

/**
 * @param {?} param
 */
function before_false(param) {
  expect(param).to.be.false;
}

/**
 * @param {?} param
 */
function after_false(param) {
  assert.isFalse(param);
}
