/*
 * Copyright 2016 The Closure Compiler Authors.
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

/** @fileoverview Test cases for set_location_href RefasterJs template. */

goog.provide('refactoring_testcase');

goog.require('test_dependency');

/**
 * @param {!Location} target The target.
 * @param {string} val The value.
 */
refactoring_testcase.test_location_href = function(target, val) {
  // Should match.
  target.href = val;
};

/**
 * @param {!Window} target The target.
 * @param {string} val The value.
 */
refactoring_testcase.test_window_location = function(target, val) {
  // Should match.
  target.location = val;
};

/**
 * @param {Location|Element} target The target.
 * @param {string} val The value.
 */
refactoring_testcase.union_type_href = function(target, val) {
  // Shouldn't match.
  target.href = val;
};

/**
 * @param {Window|Element} target The target.
 * @param {string} val The value.
 */
refactoring_testcase.union_type_location = function(target, val) {
  // Shouldn't match.
  target.location = val;
};
