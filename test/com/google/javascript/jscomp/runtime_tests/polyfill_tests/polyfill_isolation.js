/*
 * Copyright 2020 The Closure Compiler Authors.
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
 * @fileoverview This file uses various polyfilled methods and places the
 * results onto window.
 *
 * This sets up the ability for a test case to compare the
 * polyfilled versions with the versions on window and verify the polyfills work
 * as expected.
 */

goog.module('polyfill_isolation');

/**
 * Uses Map in various forms, to verify that they all a) don't crash b) and do
 * not cause Map to be injected into the global scope.
 */
function tryAllFormsOfGlobalValueAccess() {
  // Verify that all forms of global value access work
  window['global_map_var'] = new Map();
  window['window_Map'] = new window.Map();
  window['goog_global_Map'] = new goog.global.Map();
  window['globalThis_Map'] = new globalThis.Map();
}

/**
 * Makes available the implementation of various ES6 methods seen by compiled
 * code.
 *
 * These methods may be the same as the native code in the case that we are
 * running on a modern browser.
 */
function addJSCompPolyfillsToWindow() {
  window['jscomp_Promise'] = Promise;
  window['jscomp_Symbol'] = Symbol;
  window['jscomp_Map'] = Map;
  window['jscomp_Set'] = Set;
  window['jscomp_WeakMap'] = WeakMap;
  window['jscomp_WeakSet'] = WeakSet;

  window['jscomp_Math_sign'] = Math.sign;
  window['jscomp_Symbol_iterator'] = Symbol.iterator;
  window['jscomp_Array_of'] = Array.of;
  window['jscomp_Promise_settled'] = Promise.allSettled;
  window['jscomp_Object_setPrototypeOf'] = Object.setPrototypeOf;
  window['jscomp_Object_assign'] = Object.assign;
  window['jscomp_Reflect_construct'] = Reflect.construct;

  window['jscomp_String_startsWith'] = String.prototype.startsWith;
  window['jscomp_Array_includes'] = Array.prototype.includes;
}


tryAllFormsOfGlobalValueAccess();
addJSCompPolyfillsToWindow();


let counter = 0;

/** Returns the number of calls to `identityWithCounter` */
window['getCounterValue'] = () => counter;
/**
 * Proxies String.prototype.startsWith and increments a counter
 *
 * The counter is incremented once per invocation. (Verifying that polyfill
 * isolation does not accidentally introduce multiple increments of the
 * counter)
 */
window['invokeStrStartsWith'] = (str, prefix) =>
    (counter++, str).startsWith(prefix);
