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
 * @return {!jasmine.Spy} spy
 */
jasmine.createSpy = function(name) {};


/**
 * @param {string} baseName
 * @param {Array} methodNames
 */
jasmine.createSpyObj = function(baseName, methodNames) {};


/** @constructor */
jasmine.TimeProvider = function() {};


/**
 * @param {Function} cb
 * @param {number} time
 * @return {string}
 */
jasmine.TimeProvider.prototype.setTimeout = function(cb, time) {};


/**
 * @param {Function} cb
 * @param {number} time
 * @return {string}
 */
jasmine.TimeProvider.prototype.setInterval = function(cb, time) {};


/**
 * @param {string} id
 */
jasmine.TimeProvider.prototype.clearTimeout = function(id) {};


/**
 * @param {string} id
 */
jasmine.TimeProvider.prototype.clearInterval = function(id) {};


/**
 * @type {number}
 */
jasmine.TimeProvider.prototype.nowMillis;


/** @constructor */
jasmine.Clock = function() {};


/** @type {!jasmine.TimeProvider} */
jasmine.Clock.installed;


/** @return {void} */
jasmine.Clock.useMock = function() {};


/** @param {number} ms */
jasmine.Clock.tick = function(ms) {};


/** @type {number} */
jasmine.Clock.prototype.nowMillis;


/** @constructor */
jasmine.Matcher = function() {};


/** @type {jasmine.Matcher} */
jasmine.Matcher.prototype.not;


/** @type {*} */
jasmine.Matcher.prototype.actual;


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


/**
 * @param {!Object} clazz
 * @return {!jasmine.Matcher}
 */
jasmine.any = function(clazz) {};


/** @constructor */
jasmine.Spec = function() {};


/** @param {Error|string} e */
jasmine.Spec.prototype.fail = function(e) {};



/**
 * @constructor
 * @extends {Function}
 */
jasmine.Spy = function() {};


/**
 * @param {!Function} fn
 * @return {!jasmine.Spy}
 */
jasmine.Spy.prototype.andCallFake = function(fn) {};


/** @return {!jasmine.Spy} */
jasmine.Spy.prototype.andCallThrough = function() {};


/**
 * @param {*} value
 * @return {!jasmine.Spy}
 */
jasmine.Spy.prototype.andReturn = function(value) {};


/**
 * @param {!Error} exception
 * @return {!jasmine.Spy}
 */
jasmine.Spy.prototype.andThrow = function(exception) {};


/**
 * @return {void}
 */
jasmine.Spy.prototype.reset = function() {};


/** @type {number} */
jasmine.Spy.prototype.callCount;


/** @type {!Array.<!Object>} */
jasmine.Spy.prototype.calls;


/** @type {*} */
jasmine.Spy.prototype.mostRecentCall;


/** @type {!Array} */
jasmine.Spy.prototype.mostRecentCall.args;


/** @constructor */
jasmine.Helper = function() {};


/** @param {*} value */
jasmine.Helper.prototype.addMatchers = function(value) {};


/** @type {*} */
jasmine.Helper.prototype.actual;

/** @type {boolean} */
jasmine.Helper.prototype.isNot;

/** @type {undefined|function(): string} */
jasmine.Helper.prototype.message;


/** @constructor */
jasmine.JsApiReporter = function() {};


/** @type {!Array.<string>} */
jasmine.JsApiReporter.prototype.messages;


/** @type {boolean} */
jasmine.JsApiReporter.prototype.finished;


/** @return {!Array.<{id:string,name:string,type:string,children:!Array}>} id */
jasmine.JsApiReporter.prototype.suites = function() {};


/** @return {boolean} */
jasmine.JsApiReporter.prototype.isInitialized = function() {};


/** @return {boolean} */
jasmine.JsApiReporter.prototype.isFinished = function() {};


/** @return {boolean} */
jasmine.JsApiReporter.prototype.isSuccess = function() {};


/** @return {string} */
jasmine.JsApiReporter.prototype.getReport = function() {};


/** @return {number} */
jasmine.JsApiReporter.prototype.getRunTime = function() {};


/** @param {Object} runner */
jasmine.JsApiReporter.prototype.reportRunnerStarting = function(runner) {};


/** @return {Object} runner */
jasmine.JsApiReporter.prototype.reportRunnerResults = function(runner) {};


/**
 * @param {string} id
 * @return {{messages:Array,result:string}}
 */
jasmine.JsApiReporter.prototype.resultsForSpec = function(id) {};


/** @constructor */
jasmine.Env = function() {};


/** @return {void} */
jasmine.Env.prototype.execute = function() {};


/** @param {jasmine.JsApiReporter} reporter */
jasmine.Env.prototype.addReporter = function(reporter) {};


/** @param {function()} handler */
jasmine.Env.prototype.afterEach = function(handler) {};


/** @param {function(this:jasmine.Helper)} handler */
jasmine.Env.prototype.beforeEach = function(handler) {};


/**
 * @return {!jasmine.Env}
 */
jasmine.getEnv = function() {};


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
 * @param {function(this:jasmine.Spec)} handler
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
