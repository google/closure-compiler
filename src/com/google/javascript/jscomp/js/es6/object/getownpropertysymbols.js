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

'require util/polyfill';

$jscomp.polyfill('Object.getOwnPropertySymbols', function(orig) {
  if (orig) return orig;

  // NOTE: The symbol polyfill is a string, so symbols show up in
  // Object.getOwnProperytyNames instead.  It's been decided that
  // the trade-off of "fixing" this behavior is not worth the costs
  // in (a) code size, (b) brittleness, and (c) complexity.
  return function() { return []; };
}, 'es6-impl', 'es5'); // Same as Object.getOwnPropertyNames
