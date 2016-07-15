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


$jscomp.polyfill('Reflect.getOwnPropertyDescriptor', function(orig) {
  // NOTE: We don't make guarantees about correct throwing behavior.
  // Non-object arguments should be prevented by the type checker.
  return orig || Object.getOwnPropertyDescriptor;
}, 'es6', 'es5'); // ES5: Requires Object.getOwnPropertyDescriptor
