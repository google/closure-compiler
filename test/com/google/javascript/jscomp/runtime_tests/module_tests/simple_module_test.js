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

/**
 * Tests simple import from modules.
 *
 * @author moz@google.com (Michael Zhou)
 */

import {foo as f} from './module_test_resources/simpleExport';
import {bar as b, alpha, beta} from './module_test_resources/simpleExport';

goog.require('goog.testing.asserts');

function testBasic() {
  assertEquals(2, f + 1);
  assertEquals(3, f + b);
  assertEquals(12, alpha * beta);
}
