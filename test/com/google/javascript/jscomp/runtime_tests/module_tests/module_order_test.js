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

import {A} from './module_test_resources/moduleOrderA.js';
import {B} from './module_test_resources/moduleOrderB.js';
import {C} from './module_test_resources/moduleOrderC.js';
import {record} from './module_test_resources/moduleOrderRecorder.js';

const testSuite = goog.require('goog.testing.testSuite');

testSuite({
  testModuleOrder() {
    assertEquals(record[0], 'processed module C');
    assertEquals(record[1], 'processed module A');
    assertEquals(record[2], 'processed module B');

    // Verify the imported aliases are usable.
    new A().create();
    new B();
    new C();
  }
});
