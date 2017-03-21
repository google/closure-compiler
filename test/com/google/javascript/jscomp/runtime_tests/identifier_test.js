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
 * @fileoverview
 * Tests identifier names.
 */
goog.require('goog.testing.jsunit');

// Test Scanner.java workaround to prevent GWT-compiled compiler from choking
// on a special character used by Angular in some names.
// Note that the non-GWT compiler allows all legal JS identifier names, but
// due to GWT lack for Unicode support for Java's Character.is* methods, the
// GWT-compiled compiler does not.
function testUnicodeInVariableName() {
  var ɵ = true;
  // Note: a failure of this test actually manifests as
  // No tests found in given test case: identifier_test
  assertTrue(ɵ);
}
