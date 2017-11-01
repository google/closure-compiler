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
  expect(1 > 0).to.be.true;
  expect(2 < 0).to.be.false;
}

/** Another example */
function nullAndUndefinedTest() {
  expect(false || null).to.be.null;
  expect(someVar).to.be.undefined;
}

/** Truthiness! */
function okTest() {
  expect('a string').to.be.ok;
}

/** The .to.not.be variations. */
function negativeTest() {
  expect(1 > 0).to.not.be.true;
  expect(2 < 0).to.not.be.false;
  expect(false || null).to.not.be.null;
  expect(someVar).to.not.be.undefined;
  expect('a string').to.not.be.ok;
}

/** Tests method (not property) expectations */
function methodTests() {
  expect(1 + 1).to.equal(2);
  expect(1 + 1).to.not.equal(3);
  expect(1 + 1).not.to.equal(3);
  expect([1, 2]).to.deep.equal([1, 2]);
  expect([1, 2]).to.not.deep.equal([2, 1]);
  expect([]).is.an.instanceof(Array);
  expect([]).to.be.an.instanceof(Array);
  expect(1).to.be.oneOf([2, 1]);
  expect([1, 2]).to.contain(1);
  expect([1, 2]).to.have.lengthOf(2);
}
