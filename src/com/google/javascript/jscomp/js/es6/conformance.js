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

'require util/global';


/**
 * @fileoverview Check one of the most obscure features of ES6 as a proxy
 * for full conformance.  If this is enabled, this test is used instead of
 * larger, more specific conformance tests.
 */

/**
 * Check ES6 conformance by checking an obscure detail of Proxy that
 * wasn't implemented correctly until after all other ES6 features in
 * most browsers.
 * @return {boolean} Whether Proxy works correctly.
 * @suppress {reportUnknownTypes}
 */
$jscomp.checkEs6ConformanceViaProxy = function() {
  try {
    var proxied = {};
    var proxy = Object.create(new $jscomp.global['Proxy'](proxied, {
      'get': function (target, key, receiver) {
        return target == proxied && key == 'q' && receiver == proxy;
      }
    }));
    return proxy['q'] === true;
  } catch (err) {
    return false;
  }
};

/**
 * If this is true, assume that a runtime which implements Proxy also
 * implements the rest of the ECMAScript 2015 spec.
 * @define {boolean}
 */
$jscomp.USE_PROXY_FOR_ES6_CONFORMANCE_CHECKS = false;

/**
 * Whether the runtime implements the entire ECMAScript 2015 spec.
 * @const {boolean}
 */
$jscomp.ES6_CONFORMANCE =
    $jscomp.USE_PROXY_FOR_ES6_CONFORMANCE_CHECKS &&
    $jscomp.checkEs6ConformanceViaProxy();
