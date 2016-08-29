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
 * @fileoverview Externs needed for testing of polyfills
 */

// save_natives.js saves symbols here before force_polyfills deletes them.
var $native = {};
$native.Symbol;
$native.Reflect;

/**
 * @param {function(
 *             function((TYPE|IThenable<TYPE>|Thenable|null)=),
 *             function(*=))} executor
 * @constructor
 * @implements {IThenable<TYPE>}
 * @template TYPE
 */
function $jscomp_Promise(executor) {}

/** @override */
$jscomp_Promise.prototype.then = function(opt_onFulfilled, opt_onRejected) {};

/**
 * @param {function(*): RESULT} onRejected
 * @return {!$jscomp_Promise<RESULT>}
 * @template RESULT
 */
$jscomp_Promise.prototype.catch = function(onRejected) {};

/**
 * @param {VALUE=} opt_value
 * @return {RESULT}
 * @template VALUE
 * @template RESULT := type('$jscomp_Promise',
 *     cond(isUnknown(VALUE), unknown(),
 *       mapunion(VALUE, (V) =>
 *         cond(isTemplatized(V) && sub(rawTypeOf(V), 'IThenable'),
 *           templateTypeOf(V, 0),
 *           cond(sub(V, 'Thenable'),
 *              unknown(),
 *              V)))))
 * =:
 */
$jscomp_Promise.resolve = function(opt_value) {};


/**
 * @param {*=} opt_error
 * @return {!$jscomp_Promise<?>}
 */
$jscomp_Promise.reject = function(opt_error) {};


/**
 * @param {!Iterable<VALUE>} iterable
 * @return {!$jscomp_Promise<!Array<RESULT>>}
 * @template VALUE
 * @template RESULT := mapunion(VALUE, (V) =>
 *     cond(isUnknown(V),
 *         unknown(),
 *         cond(isTemplatized(V) && sub(rawTypeOf(V), 'IThenable'),
 *             templateTypeOf(V, 0),
 *             cond(sub(V, 'Thenable'), unknown(), V))))
 * =:
 */
$jscomp_Promise.all = function(iterable) {};


/**
 * @param {!Iterable<VALUE>} iterable
 * @return {!$jscomp_Promise<RESULT>}
 * @template VALUE
 * @template RESULT := mapunion(VALUE, (V) =>
 *     cond(isUnknown(V),
 *         unknown(),
 *         cond(isTemplatized(V) && sub(rawTypeOf(V), 'IThenable'),
 *             templateTypeOf(V, 0),
 *             cond(sub(V, 'Thenable'), unknown(), V))))
 * =:
 */
$jscomp_Promise.race = function(iterable) {};
