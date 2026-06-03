/*
 * Copyright 2026 The Closure Compiler Authors
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
 * @fileoverview The spec of the AsyncContext API.
 * @externs
 * @see https://github.com/tc39/proposal-async-context/
 */

/** @const */
var AsyncContext = {};

/**
 * @record
 * @template T
 */
AsyncContext.VariableOpts = function() {
  /** @type {string|undefined} */
  this.name;
  /** @type {T|undefined} */
  this.defaultValue;
};

/**
 * @constructor @struct
 * @param {AsyncContext.VariableOpts=} opts
 * @template T
 */
AsyncContext.Variable = function(opts) {};

/** @const {string} */
AsyncContext.Variable.prototype.name;

/** @return {T} */
AsyncContext.Variable.prototype.get = function() {};

/**
 * @param {T} value
 * @param {function(...?): R} fn
 * @param {...*} var_args
 * @return {R}
 * @template R
 */
AsyncContext.Variable.prototype.run = function(value, fn, var_args) {};

/** @constructor @struct */
AsyncContext.Snapshot = function() {};

/**
 * @param {function(...?): R} fn
 * @param {...*} var_args
 * @return {R}
 * @template R
 */
AsyncContext.Snapshot.prototype.run = function(fn, var_args) {};

/**
 * @param {F} fn
 * @return {F}
 * @template F
 */
AsyncContext.Snapshot.wrap = function(fn) {};
