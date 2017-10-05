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

/**
 * @fileoverview
 * @suppress {uselessCode}
 */
'require util/polyfill';
'require es6/util/setprototypeof';


$jscomp.polyfill('Object.setPrototypeOf', function(orig) {
  // Note that $jscomp.setPrototypeOf will be `null` if it isn't possible to
  // implement this method.
  return orig || $jscomp.setPrototypeOf;
}, 'es6', 'es5');
