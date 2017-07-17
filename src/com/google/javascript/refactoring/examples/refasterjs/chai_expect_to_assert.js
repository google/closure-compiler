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
 * TODO(tbreisacher): Expand this to include chai-jquery assertions as well:
 * http://chaijs.com/plugins/chai-jquery/
 *
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
function before_not_true(param) {
  expect(param).to.not.be.true;
}

/**
 * @param {?} param
 */
function after_not_true(param) {
  assert.isNotTrue(param);
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

/**
 * @param {?} param
 */
function before_not_false(param) {
  expect(param).to.not.be.false;
}

/**
 * @param {?} param
 */
function after_not_false(param) {
  assert.isNotFalse(param);
}

/**
 * @param {?} param
 */
function before_null(param) {
  expect(param).to.be.null;
}

/**
 * @param {?} param
 */
function after_null(param) {
  assert.isNull(param);
}

/**
 * @param {?} param
 */
function before_not_null(param) {
  expect(param).to.not.be.null;
}

/**
 * @param {?} param
 */
function after_not_null(param) {
  assert.isNotNull(param);
}

/**
 * @param {?} param
 */
function before_undefined(param) {
  expect(param).to.be.undefined;
}

/**
 * @param {?} param
 */
function after_undefined(param) {
  assert.isUndefined(param);
}

/**
 * @param {?} param
 */
function before_not_undefined(param) {
  expect(param).to.not.be.undefined;
}

/**
 * @param {?} param
 */
function after_not_undefined(param) {
  assert.isDefined(param);
}

/**
 * @param {?} param
 */
function before_ok(param) {
  expect(param).to.be.ok;
}

/**
 * @param {?} param
 */
function after_ok(param) {
  assert.isOk(param);
}

/**
 * @param {?} param
 */
function before_not_ok(param) {
  expect(param).to.not.be.ok;
}

/**
 * @param {?} param
 */
function after_not_ok(param) {
  assert.isNotOk(param);
}
