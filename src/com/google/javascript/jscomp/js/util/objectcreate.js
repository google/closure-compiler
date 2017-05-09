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

/**
 * @fileoverview Provides a partial internal polyfill for Object.create.
 */
'require base';


/**
 * Polyfill for Object.create() method:
 * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/create
 *
 * Does not implement the second argument.
 * @param {!Object} prototype
 * @return {!Object}
 */
$jscomp.objectCreate =
    typeof Object.create == 'function' ?
        Object.create :
        function(prototype) {
          /** @constructor */
          var ctor = function() {};
          ctor.prototype = prototype;
          return new ctor();
        };
