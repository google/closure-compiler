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
'require es6/asynccontext/runtime';
'require es6/asynccontext/namespace';

$jscomp.polyfill('AsyncContext.Snapshot', function(original) {
  if (original) return original;

  /** @constructor @extends {AsyncContext.Snapshot} */
  function Snapshot() {
    $jscomp.asyncContextSnapshot(/** @type {?} */ (this));
  }

  // NOTE: Can't use @override on static method, so quote the property instead
  // to prevent disambiguation.  Without this, any usages of `Snapshot.wrap`
  // will be preserved due to externs, but this definition will be treated as an
  // unrelated property, renamed, and then deleted as unused, breaking any
  // usages.
  /**
   * @param {!Function} fn
   * @return {!Function}
   */
  Snapshot['wrap'] = function(fn) {
    return $jscomp.asyncContextWrap(fn);
  };

  /**
   * @template T
   * @param {function(): T} fn
   * @return {T}
   * @override
   */
  Snapshot.prototype.run = function(fn) {
    return $jscomp.asyncContextRun(
        fn, Array.prototype.slice.call(arguments, 1), this);
  };
  return Snapshot;
}, 'es_unstable', 'es3');
