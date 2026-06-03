/*
 * Copyright 2024 The Closure Compiler Authors.
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
 * @fileoverview Provides a trivial "no-op" implementation of AsyncContext, for
 * use in cases where no variables are written.  This is important so that
 * library providers can freely call `AsyncContext.wrap` (or use `Snapshot`)
 * without incurring significant costs if the context is never actually used.
 */

'require util/polyfill';
'require es6/asynccontext/namespace';
'require es6/asynccontext/runtime';

/** @typedef {!Array<*>} */
$jscomp.Context;

$jscomp.polyfill('AsyncContext.Snapshot', function(original) {
  if (original) return original;

  var state = $jscomp.asyncContextState;

  /**
   * This is declared as abstract to avoid needing to initialize $savedContext,
   * which is only relevant if the variable polyfill is installed.
   * @abstract
   * @constructor
   * @extends {AsyncContext.Snapshot}
   */
  function Snapshot() {
    /**
     * Saved value of $jscomp.context (only referenced in variable.js).
     * @const {!$jscomp.Context}
     */
    this.$savedContext = state[0];
  }

  /**
   * NOTE: This is quoted because the `@extends` clause doesn't apply to static
   * functions, so the compiler doesn't consider this part of the public API.
   */
  Snapshot['wrap'] = $jscomp.asyncContextWrap;

  /**
   * @template T
   * @param {function(): T} fn
   * @return {T}
   * @override
   */
  Snapshot.prototype.run = function(fn) {
    var save = state[0];
    state[0] = this.$savedContext;
    try {
      return fn.apply(null, Array.prototype.slice.call(arguments, 1));
    } finally {
      state[0] = save;
    }
  };

  return Snapshot;
}, 'es_unstable', 'es3');
