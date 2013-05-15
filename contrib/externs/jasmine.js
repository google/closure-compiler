/*
 * Copyright 2013 The Closure Compiler Authors.
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
 * @fileoverview Externs for Jasmine.
 *
 * TODO: Remaining Spy properties and anything else missing.
 *
 * @see http://pivotal.github.com/jasmine/
 * @externs
 */


/** @const */
var jasmine = {};


/**
 * @param {string} name
 * @return {jasmine.Spy} spy
 */
jasmine.createSpy = function(name) {};



/** @constructor */
jasmine.Matcher = function() {};


/** @type {jasmine.Matcher} */
jasmine.Matcher.prototype.not;


/** @param {*} value */
jasmine.Matcher.prototype.toBe = function(value) {};


/** @return {void} */
jasmine.Matcher.prototype.toBeDefined = function() {};


/** @return {void} */
jasmine.Matcher.prototype.toBeFalsy = function() {};


/** @param {*} value */
jasmine.Matcher.prototype.toBeGreaterThan = function(value) {};


/** @param {*} value */
jasmine.Matcher.prototype.toBeLessThan = function(value) {};


/** @return {void} */
jasmine.Matcher.prototype.toBeNull = function() {};


/** @return {void} */
jasmine.Matcher.prototype.toBeTruthy = function() {};


/** @return {void} */
jasmine.Matcher.prototype.toBeUndefined = function() {};


/** @param {*} value */
jasmine.Matcher.prototype.toContain = function(value) {};


/** @param {*} value */
jasmine.Matcher.prototype.toEqual = function(value) {};


/** @return {void} */
jasmine.Matcher.prototype.toHaveBeenCalled = function() {};


/** @param {...*} var_args */
jasmine.Matcher.prototype.toHaveBeenCalledWith = function(var_args) {};


/** @param {(string|RegExp)} pattern */
jasmine.Matcher.prototype.toMatch = function(pattern) {};


/** @param {Error=} opt_expected */
jasmine.Matcher.prototype.toThrow = function(opt_expected) {};



/** @constructor */
jasmine.Spy = function() {};


/** @param {Function} fn */
jasmine.Spy.prototype.andCallFake = function(fn) {};


/** @return {void} */
jasmine.Spy.prototype.andCallThrough = function() {};


/** @param {*} value */
jasmine.Spy.prototype.andReturn = function(value) {};


/** @param {Error} exception */
jasmine.Spy.prototype.andThrow = function(exception) {};


/** @constructor */
jasmine.Helper = function() {};


/** @param {*} value */
jasmine.Helper.prototype.addMatchers = function(value) {};


/** @type {*} */
jasmine.Helper.prototype.actual;

/** @type {boolean} */
jasmine.Helper.prototype.isNot;


/** @param {function()} handler */
function afterEach(handler) {}


/** @param {function(this:jasmine.Helper)} handler */
function beforeEach(handler) {}


/**
 * @param {string} description
 * @param {function()} handler
 */
function describe(description, handler) {}


/**
 * @param {*} expectedValue
 * @return {jasmine.Matcher} matcher
 */
function expect(expectedValue) {}


/**
 * Provided by angular-mocks.js.
 * @param {...(Function|Array.<(string,Function)>)} var_args
 */
function inject(var_args) {}


/**
 * @param {string} description
 * @param {function()} handler
 */
function it(description, handler) {}


/**
 * Provided by angular-mocks.js.
 * @param {...(string|Function|Array.<(string,Function)>)} var_args
 */
function module(var_args) {}


/** @param {function()} handler */
function runs(handler) {}


/**
 * @param {Object} spiedOnObject
 * @param {string} methodName
 * @return {jasmine.Spy} spy
 */
function spyOn(spiedOnObject, methodName) {}


/** @param {number} timeout */
function waits(timeout) {}


/**
 * @param {function(): boolean} handler
 * @param {string=} opt_message
 * @param {number=} opt_timeout
 */
function waitsFor(handler, opt_message, opt_timeout) {}


/**
 * @nosideeffects
 * @param {string} description
 * @param {function()} handler
 */
function xdescribe(description, handler) {}


/**
 * @nosideeffects
 * @param {string} description
 * @param {function()} handler
 */
function xit(description, handler) {}
