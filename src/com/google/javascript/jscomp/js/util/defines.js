/*
 * Copyright 2017 The Closure Compiler Authors.
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

'require base';


/**
 * Whether to assume ES5 is available.  This enables removing several
 * internal polyfills, which must otherwise be detected at runtime.
 * @define {boolean}
 */
$jscomp.ASSUME_ES5 = false;

/**
 * Whether to skip the conformance check and simply use the polyfill always.
 * @define {boolean}
 */
$jscomp.ASSUME_NO_NATIVE_MAP = false;

/**
 * Whether to skip the conformance check and simply use the polyfill always.
 * @define {boolean}
 */
$jscomp.ASSUME_NO_NATIVE_SET = false;

/**
 * Whether to provide an incorrect but tiny Math.fround polyfill that just
 * returns the number given.  This is usually okay to do, particularly if
 * `Math.fround` is only used to allow the JavaScript engine to use faster
 * 32-bit float operations, but could cause problems if program logic is
 * dependent on floats being truncated consistently.
 * @define {boolean}
 */
$jscomp.SIMPLE_FROUND_POLYFILL = false;
