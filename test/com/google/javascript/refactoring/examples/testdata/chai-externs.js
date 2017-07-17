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
 * @fileoverview Definitions for a few Chai functions. Just enough to make
 *     ChaiExpectToAssertTest work; don't use these for a real JS project that
 *     uses Chai.
 * @externs
 */

/**
 * Represents the chainable return type of `expect()`.
 * @constructor
 */
var ExpectChain = function() {};

/**
 * Represent a non-chainable terminal part in an `expect()` chain. These are
 * effectively assertions.
 * @constructor
 */
var ExpectChainTerminal = function() {};

/** @type {!ExpectChain} */ ExpectChain.prototype.to;
/** @type {!ExpectChain} */ ExpectChain.prototype.be;
/** @type {!ExpectChain} */ ExpectChain.prototype.not;

/** @type {!ExpectChainTerminal} */ ExpectChain.prototype.true;
/** @type {!ExpectChainTerminal} */ ExpectChain.prototype.false;
/** @type {!ExpectChainTerminal} */ ExpectChain.prototype.null;
/** @type {!ExpectChainTerminal} */ ExpectChain.prototype.undefined;
/** @type {!ExpectChainTerminal} */ ExpectChain.prototype.ok;

/**
 * @param {*} subject
 * @param {string=} opt_description
 * @return {!ExpectChain}
 */
var expect = function(subject, opt_description) {};

/** @const */
var assert = {};

/**
 * @param {*} value
 * @param {string=} opt_message
 */
assert.isTrue = function(value, opt_message) {};

/**
 * @param {*} value
 * @param {string=} opt_message
 */
assert.isFalse = function(value, opt_message) {};
