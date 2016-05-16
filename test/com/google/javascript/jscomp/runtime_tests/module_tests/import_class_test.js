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
 * Tests import and extend from an exported class.
 *
 * @author moz@google.com (Michael Zhou)
 */

import {Parent} from './module_test_resources/exportClass';

export class Child extends Parent {
  /**
   * @param {./module_test_resources/exportClass.Parent} parent The parent.
   */
  useParent(parent) {
  }
}

export class GrandChild extends Child {}

function testClass() {
  new Child().useParent(new Parent());
}

function testStaticInheritanceAcrossModules() {
  assertEquals('Parent.staticFunction', Child.staticFunction());
  assertEquals('Parent.staticFunction', GrandChild.staticFunction());
}
