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
 * @fileoverview Externs definitions for Mocha, 2.5 branch.
 *
 * This file currently only defines the TDD API, and that part should be
 * complete.
 *
 * @externs
 * @see https://mochajs.org/
 */

// Below are the externs for the TDD API: https://mochajs.org/#tdd

/**
 * @param {string} name
 * @param {!Function} cb
 */
var suite = function(name, cb) {};

/**
 * @param {!Function} cb
 */
var setup = function(cb) {};

/**
 * @param {!Function} cb
 */
var teardown = function(cb) {};

/**
 * @param {!Function} cb
 */
var suiteSetup = function(cb) {};

/**
 * @param {!Function} cb
 */
var suiteTeardown = function(cb) {};

/**
 * @param {string} name
 * @param {!Function} cb
 */
var test = function(name, cb) {};
