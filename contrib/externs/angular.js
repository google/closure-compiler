/*
 * Copyright 2012 The Closure Compiler Authors.
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
 * @fileoverview Externs for Angular 1
 *
 * TODO: Mocks.
 * TODO: Modules.
 *
 * @see http://angularjs.org/
 * @externs
 */

/**
 * @type {Object}
 * @const
 */
var angular = {};

/**
 * @param {Object} self
 * @param {function()} fn
 * @param {...*} args
 * @return {function()}
 */
angular.bind = function(self, fn, args) {};

/**
 * @param {Element} element
 * @param {Array.<string|Function>=} opt_modules
 * @return {function()}
 */
angular.bootstrap = function(element, opt_modules) {};

/**
 * @param {*} source
 * @param {(Object|Array)=} dest
 * @return {*}
 */
angular.copy = function(source, dest) {};

/**
 * @param {string|Element} element
 * @return {Object}
 */
angular.element = function(element) {};

/**
 * @param {*} o1
 * @param {*} o2
 * @return {boolean}
 */
angular.equals = function(o1, o2) {};

/**
 * @param {Object} dest
 * @param {...Object} srcs
 */
angular.extend = function(dest, srcs) {};

/**
 * @param {Object|Array} obj
 * @param {Function} iterator
 * @param {Object=} opt_context
 * @return {Object|Array}
 */
angular.forEach = function(obj, iterator, opt_context) {};

/**
 * @param {string} json
 * @return {Object|Array|Date|string|number}
 */
angular.fromJson = function(json) {};

/**
 * @param {*} arg
 * @return {*}
 */
angular.identity = function(arg) {};

/**
 * @param {Array.<string|Function>} modules
 * @return {function()}
 */
angular.injector = function(modules) {};

/**
 * @param {*} value
 * @return {boolean}
 */
angular.isArray = function(value) {};

/**
 * @param {*} value
 * @return {boolean}
 */
angular.isDate = function(value) {};

/**
 * @param {*} value
 * @return {boolean}
 */
angular.isDefined = function(value) {};

/**
 * @param {*} value
 * @return {boolean}
 */
angular.isElement = function(value) {};

/**
 * @param {*} value
 * @return {boolean}
 */
angular.isFunction = function(value) {};

/**
 * @param {*} value
 * @return {boolean}
 */
angular.isNumber = function(value) {};

/**
 * @param {*} value
 * @return {boolean}
 */
angular.isObject = function(value) {};

/**
 * @param {*} value
 * @return {boolean}
 */
angular.isString = function(value) {};

/**
 * @param {*} value
 * @return {boolean}
 */
angular.isUndefined = function(value) {};

/**
 * @param {string} s
 * @return {string}
 */
angular.lowercase = function(s) {};

angular.mock = {};
angular.module = {};

angular.noop = function() {};

angular.scope = {};

/**
 * @param {(string|function())=} exp
 * @return {*}
 */
angular.scope.$apply = function(exp) {};

/**
 * @param {string} name
 * @param {...*} args
 */
angular.scope.$broadcast = function(name, args) {};

angular.scope.$destroy = function() {};

angular.scope.$digest = function() {};

/**
 * @param {string} name
 * @param {...*} args
 */
angular.scope.$emit = function(name, args) {};

/**
 * @param {(string|function())=} exp
 * @param {Object=} locals
 * @return {*}
 */
angular.scope.$eval = function(exp, locals) {};

/**
 * @param {(string|function())=} exp
 */
angular.scope.$evalAsync = function(exp) {};

/**
 * @return {Object}
 */
angular.scope.$new = function() {};

/**
 * @param {string} name
 * @param {function(Object)} listener
 * @return {function()}
 */
angular.scope.$on = function(name, listener) {};

/**
 * @param {string|function()} exp
 * @param {(string|function())=} opt_listener
 * @param {boolean=} opt_objectEquality
 * @return {function()}
 */
angular.scope.$watch = function(exp, opt_listener, opt_objectEquality) {};

/**
 * @param {Object|Array|Date|string|number} obj
 * @param {boolean=} pretty
 * @return {string}
 */
angular.toJson = function(obj, pretty) {};

/**
 * @param {string} s
 * @return {string}
 */
angular.uppercase = function(s) {};

/**
 * @type {Object}
 */
angular.version = {};

/**
 * @type {string}
 */
angular.version.full = '';

/**
 * @type {number}
 */
angular.version.major = 0;

/**
 * @type {number}
 */
angular.version.minor = 0;

/**
 * @type {number}
 */
angular.version.dot = 0;

/**
 * @type {string}
 */
angular.version.codeName = '';
