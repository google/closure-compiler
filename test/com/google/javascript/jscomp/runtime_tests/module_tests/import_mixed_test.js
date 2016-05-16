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

import defaultStr, * as mod from './module_test_resources/exportDefault';
import defaultStr, {nonDefaultExport} from './module_test_resources/exportDefault';

function testImportMixed() {
  assertEquals('this is the default export', defaultStr);
  assertEquals('this is the default export', mod.default);
  assertEquals(2, mod.nonDefaultExport);
  assertEquals(2, nonDefaultExport);
}
