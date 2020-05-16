/*
 * Copyright 2020 The Closure Compiler Authors.
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
 * @fileoverview Helpers to decide whether to trust existing implementations of
 * polyfilled methods.
 * @suppress {uselessCode} the requires are considered "useless"
 */

'require util/defines';

/**
 * @const {boolean} whether Symbol is implemented natively (i.e. is not a
 * polyfill)
 */
$jscomp.IS_SYMBOL_NATIVE =
    typeof Symbol === 'function' && typeof Symbol('x') === 'symbol';

/**
 * Whether code should use built-in versions of ES6 methods when available.
 *
 * @const {boolean}
 */
$jscomp.TRUST_ES6_POLYFILLS =
    !$jscomp.ISOLATE_POLYFILLS || $jscomp.IS_SYMBOL_NATIVE;
