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
 * @fileoverview Basic no-op implementations of AsyncContext transpilation
 * runtime methods. These are overridden by `variable.js`, but when that file
 * is not included, they will inline away to nothing. These functions are
 * called by the transpiled versions of `await` and `yield`.
 *
 * NOTE: These functions are replaced with one of two alternatives: (1) if it's
 * polyfilling for the first time, then they're replaced with an implementation
 * that's consistent with the polyfill, and also stores them on the polyfill
 * itself; (2) if we detect an existing polyfill, then the functions stored in
 * the earlier binary are copied _back_ into these functions. This allows
 * multiple binaries to _maybe_ work together, with one exception: when two
 * binaries are present on the same page, the `variable.js` polyfill must not be
 * dead-code eliminated from _either_ binary, so as to ensure that all the
 * instrumentation remains as necessary.
 */

'require base';
'require es6/asynccontext/namespace';

/**
 * Returns a context-swap function.  This is called at the start of all async
 * and generator functions.  The returned closure simply returns its first
 * argument, and swaps into or out of the function's context depending on the
 * truthiness of the second argument (truthy is re-enter, falsy/absent is exit).
 *
 * Note that this is the legacy version, which returns the swap function
 * directly.  We are currently migrating to a new version which indirects via
 * one additional function.  This function will be deleted once Closure Compiler
 * and tsickle are fully released.
 *
 * @param {?=} exit Whether to immediately exit the context.
 * @return {function(?=): ?}
 */
$jscomp.asyncContextEnter = function(exit) {
  return $jscomp.asyncContextIdentity;
};

/**
 * Returns a context-swap factory function, which in turn returns the suspend
 * and resume functions.  This function is called at the start of all async and
 * generator functions.  The suspend and resume functions simply return their
 * argument, while swapping into or out of the function's context as a side
 * effect.
 *
 * @param {?=} suspend Whether to immediately suspend the context.
 * @return {function(?=): ?}
 */
$jscomp.asyncContextStart = function(suspend) {
  return $jscomp.asyncContextEnter;
};

/**
 * Identity function, which is the "no-op" default value returned by the swap
 * factory function.  It's useful to pull this into a separate named function so
 * that the compiler can inline it away.
 *
 * @param {?=} x
 * @return {?}
 */
$jscomp.asyncContextIdentity = function(x) {
  return x;
};

/**
 * Wraps the given function in a wrapper that saves and restores the context.
 * @param {!Function} fn
 * @return {!Function}
 */
$jscomp.asyncContextWrap = function(fn) {
  var state = $jscomp.asyncContextState;
  if (!state[0]) return fn;
  var context = state[0];
  var wrapped = function() {
    var save = state[0];
    state[0] = context;
    try {
      return fn.apply(this, arguments);
    } finally {
      state[0] = save;
    }
  };
  return wrapped;
};
