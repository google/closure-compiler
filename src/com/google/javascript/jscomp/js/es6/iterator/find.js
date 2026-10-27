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
 * @fileoverview Polyfill for the ES2025 Iterator.prototype.find method.
 * @see https://tc39.es/ecma262/#sec-iterator.prototype.find
 */
'require es6/iterator';
'require es6/util/iteratordo';
'require util/polyfill';

$jscomp.polyfill('Iterator.prototype.find', function(orig) {
  return orig || function(fn) {
    var res;
    $jscomp.iteratorDo(this, fn, function(v, i) {
      if (fn(v, i)) {
        res = v;
        return true;
      }
    });
    return res;
  };
}, 'es_next', 'es3');
