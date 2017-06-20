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
 * @fileoverview Test that a class which is transpiled can extend a class that
 *     is not. Since the non-transpiled code is also not renamed, we need to
 *     use bracket notation in a few places.
 * @suppress {checkTypes|missingRequire|checkVars}
 */

goog.require('goog.testing.jsunit');

// Only run this test in browsers that support the class keyword.
function shouldRunTests() {
  try {
    eval('class c {}');
    return true;
  } catch (e) {
    return false;
  }
}

window['Es6BaseClass'] = class {
  constructor() {
    throw Error(
        'This should never be called. Each test overwrites Es6BaseClass.');
  }
};

function testInstanceof() {
  // Put the base class in an eval so that the compiler won't transpile it.
  eval(`
    Es6BaseClass = class {
      constructor() {
        this.foo = 'bar';
      }
    };
  `);
  let TranspiledSubclass = class extends Es6BaseClass {};

  let t = new TranspiledSubclass();
  assertEquals('bar', t['foo']);
  assertTrue(t instanceof TranspiledSubclass);
  assertTrue(t instanceof Es6BaseClass);
}

function testNotInstanceofCtorReturn() {
  // Put the base class in an eval so that the compiler won't transpile it.
  eval(`
    Es6BaseClass = class {
      constructor() {
        return {foo: 'bar'};
      }
    };
  `);
  let TranspiledSubclass = class extends Es6BaseClass {};

  let t = new TranspiledSubclass();
  assertEquals('bar', t['foo']);
  assertFalse(t instanceof TranspiledSubclass);
  assertFalse(t instanceof Es6BaseClass);
}
