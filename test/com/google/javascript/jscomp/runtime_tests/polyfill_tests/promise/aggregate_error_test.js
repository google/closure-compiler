/*
 * Copyright 2021 The Closure Compiler Authors.
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
 * @fileoverview Tests for the AggregateError polyfilled method.
 */
goog.module('jscomp.runtime_tests.polyfill_tests.promise_aggregate_error_test');
goog.setTestOnly();

const testSuite = goog.require('goog.testing.testSuite');

testSuite({
  testSimple() {
    let errors = [new Error('e1'), new Error('e2')];
    let message = 'I am an aggregate error';
    let aggregateError = new AggregateError(errors, message);
    assertEquals(aggregateError.errors.length, 2);
    assertEquals(aggregateError.errors[0].message, errors[0].message);
    assertEquals(aggregateError.errors[1].message, errors[1].message);
    assertEquals(aggregateError.message, message);
    assertTrue(aggregateError.name == 'AggregateError');
    assertFalse(aggregateError.hasOwnProperty('name'));
    assertTrue(aggregateError instanceof Error);
  },
});
