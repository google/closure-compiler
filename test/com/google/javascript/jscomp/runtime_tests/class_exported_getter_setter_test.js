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

goog.require('goog.testing.jsunit');

/** @unrestricted */
var ExportedGetter = class {
  /** @export */
  get exportMe() {
    return 0;
  }
}

/** @unrestricted */
var ExportedSetter = class {
  /** @export */
  set exportMe(val) {
  }
}

function testGetterNotRenamed() {
  var eg = new ExportedGetter();
  assertTrue('exportMe' in eg);
}

function testSetterNotRenamed() {
  var es = new ExportedSetter();
  assertTrue('exportMe' in es);
}
