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
 * @fileoverview Specifies objects that the compiler does NOT polyfill.
 * NOTE: this file should never be injected, since all the implementations
 * are null.
 */

'require util/polyfill';

$jscomp.polyfill('Proxy', null, 'es6', 'es6');

$jscomp.polyfill('WeakMap', null, 'es6-impl', 'es6-impl');
$jscomp.polyfill('WeakSet', null, 'es6-impl', 'es6-impl');
$jscomp.polyfill('Reflect', null, 'es6-impl', 'es6-impl');
$jscomp.polyfill('Object.setPrototypeOf', null, 'es6-impl', 'es6-impl');
$jscomp.polyfill('String.raw', null, 'es6-impl', 'es6-impl');
$jscomp.polyfill('String.prototype.normalize', null, 'es6-impl', 'es6-impl');
