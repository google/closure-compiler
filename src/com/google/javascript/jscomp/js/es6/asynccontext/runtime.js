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
 * runtime methods.  These are overridden by `variable.js`, but when that file
 * is not included, they will inline away to nothing.
 */

'require base';

// NOTE: it's important that we don't clobber these if they already exist:
// the runtime library should be idempotent, and the polyfill will only
// overwrite these once (since the second time it returns early due to
// AsyncContext.Variable already existing).

/** @return {?} */
$jscomp.asyncContextEnter = $jscomp.asyncContextEnter || function() {};

/** @param {?} snapshot */
$jscomp.asyncContextSnapshot =
    $jscomp.asyncContextSnapshot || function(snapshot) {};

/**
 * @param {?} value
 * @param {?} context
 * @return {?}
 */
$jscomp.asyncContextExit =
    $jscomp.asyncContextExit || function(value, context) {
      return value;
    };

/**
 * @param {?} value
 * @param {?} context
 * @return {?}
 */
$jscomp.asyncContextReenter =
    $jscomp.asyncContextReenter || function(value, context) {
      return value;
    };

/**
 * @param {?} fn
 * @return {?}
 */
$jscomp.asyncContextWrap = $jscomp.asyncContextWrap || function(fn) {
  return fn;
};

/**
 * @param {!Function} fn
 * @param {!Array<*>} args
 * @param {*} snapshot
 * @return {*}
 */
$jscomp.asyncContextRun =
    $jscomp.asyncContextRun || function(fn, args, snapshot) {
      return fn.apply(null, args);
    };
