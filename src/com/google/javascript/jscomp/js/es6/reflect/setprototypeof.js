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
'require es6/reflect/reflect';
'require es6/util/setprototypeof';


$jscomp.polyfill(
    'Reflect.setPrototypeOf',
    /**
     * These annotations are intended to match the signature of
     * $jscomp.polyfill(). Being more specific makes the compiler unhappy.
     * @suppress {reportUnknownTypes}
     * @param {?*} orig
     * @return {*}
     */
    function(orig) {
      if (orig) {
        return orig;
      } else if ($jscomp.setPrototypeOf) {
        /** @const {!function(!Object,?Object):!Object} */
        var setPrototypeOf = $jscomp.setPrototypeOf;
        /**
         * @param {!Object} target
         * @param {?Object} proto
         * @return {boolean}
         */
        var polyfill = function(target, proto) {
          try {
            setPrototypeOf(target, proto);
            return true;
          } catch (e) {
            return false;
          }
        };
        return polyfill;
      } else {
        // it isn't possible to implement this method
        return null;
      }
    },
    'es6', 'es5');
