/*
 * Copyright 2026 The Closure Compiler Authors.
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
 * @fileoverview Utility method for getting the standard Iterator.prototype.
 * @suppress {uselessCode}
 */
'require base';

/**
 * Returns the standard Iterator.prototype if a global Iterator exists, else null.
 *
 * Intentionally not requiring es6/iterator to avoid a circular dependency
 * (es6/iterator requires es6/symbol) and to avoid unconditionally injecting the
 * Iterator polyfill into all binaries that use symbols or generators.
 * If Iterator is natively present or polyfilled (either by Closure's own
 * es6/iterator.js when user code references `Iterator`, or by user code), we can
 * access its prototype; otherwise, null.
 *
 * Older SpiderMonkey engines (e.g. Cobalt 9 / MozJS 45) define a legacy
 * non-standard global `Iterator(obj)` whose `Iterator.prototype` defines a
 * read-only `next` method (whereas standard %IteratorPrototype% does not define
 * `next`).
 *
 * @return {?Object}
 * @suppress {reportUnknownTypes}
 */
$jscomp.getIteratorPrototype = function() {
  return typeof Iterator != 'undefined' && Iterator.prototype &&
      !Iterator.prototype.next ? Iterator.prototype : null;
};
