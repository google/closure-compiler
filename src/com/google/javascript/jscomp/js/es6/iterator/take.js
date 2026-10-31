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
 * @fileoverview Polyfill for the ES2025 Iterator.prototype.take method.
 * @see https://tc39.es/ecma262/#sec-iterator.prototype.take
 */
'require es6/iterator';
'require es6/util/iteratortakeordrop';
'require util/polyfill';

$jscomp.polyfill('Iterator.prototype.take', function(orig) {
  return orig ||
      /**
       * @this {!Iterator<VALUE>}
       * @param {number} limit
       * @return {!Iterator<VALUE>}
       * @template VALUE
       */
      function(limit) {
        return $jscomp.iteratorTakeOrDrop(this, limit, false);
      };
}, 'es_next', 'es3');
