/*
 * Copyright 2015 The Closure Compiler Authors.
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
 * @fileoverview Externs for Jasmine 3.3 (not backwards compatible with 1.3,
 * but should be backwards compatible with 2.0).
 *
 * TODO: Missing externs for real type support for defining matchers.
 *
 * @see https://jasmine.github.io/api/3.3/global
 * @externs
 */


/** @const */
var jasmine = {};


/**
 * @param {Object} matchers
 */
jasmine.addMatchers = function(matchers) {};


/**
 * @param {function(?, ?): (boolean|undefined)} tester
 */
jasmine.addCustomEqualityTester = function(tester) {};


/**
 * @return {!jasmine.Clock}
 */
jasmine.clock = function() {};


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
jasmine.Clock = function() {};


/** */
jasmine.Clock.prototype.install = function() {};


/** */
jasmine.Clock.prototype.uninstall = function() {};


/** @param {number} ms */
jasmine.Clock.prototype.tick = function(ms) {};


/** @param {!Date} date */
jasmine.Clock.prototype.mockDate = function(date) {};


/**
 * @constructor
 * @template T, U
 * (Template args here are unused, but added to align with
 * google3/third_party/javascript/typings/jasmine 's definition of
 * AsyncMatchers.)
 */
jasmine.AsyncMatchers = function() {};


/** @type {!jasmine.AsyncMatchers} */
jasmine.AsyncMatchers.prototype.not;


/** @return {!Promise<void>} */
jasmine.AsyncMatchers.prototype.toBeRejected = function() {};


/**
 * @param {*} value
 * @return {!Promise<void>}
 */
jasmine.AsyncMatchers.prototype.toBeRejectedWith = function(value) {};


/**
 * @param {function(new:Error, ...)|string|!RegExp=} errorOrMessage
 * @param {string|!RegExp=} message
 * @return {!Promise<void>}
 */
jasmine.AsyncMatchers.prototype.toBeRejectedWithError = function(
    errorOrMessage, message) {};


/** @return {!Promise<void>} */
jasmine.AsyncMatchers.prototype.toBeResolved = function() {};


/**
 * @param {*=} value
 * @return {!Promise<void>}
 */
jasmine.AsyncMatchers.prototype.toBeResolvedTo = function(value = undefined) {};

/**
 * @param {string} message
 * @return {!jasmine.AsyncMatchers}
 */
jasmine.AsyncMatchers.prototype.withContext = function(message) {};



/** @constructor @template T */
jasmine.Matchers = function() {};


/** @type {!jasmine.Matchers} */
jasmine.Matchers.prototype.not;


/** @type {T} */
jasmine.Matchers.prototype.actual;


/** @return {void} */
jasmine.Matchers.prototype.nothing = function() {};


/**
 * @param {*} value
 * @param {*=} expectationFailOutput
 */
jasmine.Matchers.prototype.toBe = function(value, expectationFailOutput) {};


/**
 * @param {*=} expectationFailOutput
 * @return {void}
 */
jasmine.Matchers.prototype.toBeDefined = function(expectationFailOutput) {};


/**
 * @param {*=} expectationFailOutput
 * @return {void}
 */
jasmine.Matchers.prototype.toBeFalsy = function(expectationFailOutput) {};


/**
 * @param {*} value
 * @param {*=} expectationFailOutput
 */
jasmine.Matchers.prototype.toBeGreaterThan = function(
    value, expectationFailOutput) {};


/**
 * @param {*} value
 * @param {*=} expectationFailOutput
 */
jasmine.Matchers.prototype.toBeLessThan = function(
    value, expectationFailOutput) {};


/**
 * @param {*} value
 * @param {*=} expectationFailOutput
 */
jasmine.Matchers.prototype.toBeGreaterThanOrEqual = function(
    value, expectationFailOutput) {};


/**
 * @param {*} value
 * @param {*=} expectationFailOutput
 */
jasmine.Matchers.prototype.toBeLessThanOrEqual = function(
    value, expectationFailOutput) {};


/**
 * @param {*} value
 * @param {*=} precision
 * @param {*=} expectationFailOutput
 */
jasmine.Matchers.prototype.toBeCloseTo = function(
    value, precision, expectationFailOutput) {};


/**
 * @param {*=} expectationFailOutput
 * @return {void}
 */
jasmine.Matchers.prototype.toBeNull = function(expectationFailOutput) {};


/**
 * @param {*=} expectationFailOutput
 * @return {void}
 */
jasmine.Matchers.prototype.toBeTruthy = function(expectationFailOutput) {};


/**
 * @param {*=} expectationFailOutput
 * @return {void}
 */
jasmine.Matchers.prototype.toBeUndefined = function(expectationFailOutput) {};


/** @return {void} */
jasmine.Matchers.prototype.toBeNaN = function() {};


/**
 * @param {*} value
 * @param {*=} expectationFailOutput
 */
jasmine.Matchers.prototype.toContain = function(
    value, expectationFailOutput) {};


/**
 * @param {*} value
 * @param {*=} expectationFailOutput
 */
jasmine.Matchers.prototype.toEqual = function(value, expectationFailOutput) {};


/** @return {void} */
jasmine.Matchers.prototype.toHaveBeenCalled = function() {};

/**
 * @param {!jasmine.Spy} expected
 * @return {void}
 */
jasmine.Matchers.prototype.toHaveBeenCalledBefore = function(expected) {};


/** @param {...*} var_args */
jasmine.Matchers.prototype.toHaveBeenCalledWith = function(var_args) {};

/** @param {number} num */
jasmine.Matchers.prototype.toHaveBeenCalledTimes = function(num) {};

/**
 * @param {(string|RegExp)} pattern
 * @param {*=} expectationFailOutput
 */
jasmine.Matchers.prototype.toMatch = function(
    pattern, expectationFailOutput) {};


/** @param {!Object=} opt_expected */
jasmine.Matchers.prototype.toThrow = function(opt_expected) {};


/**
 * @param {(!Function|string|!RegExp)=} opt_errorTypeOrMessageOrPattern
 * @param {(string|RegExp)=} opt_messageOrPattern
 */
jasmine.Matchers.prototype.toThrowError = function(
    opt_errorTypeOrMessageOrPattern, opt_messageOrPattern) {};

/**
 * @param {string} message
 * @return {!jasmine.Matchers}
 */
jasmine.Matchers.prototype.withContext = function(message) {};


/**
 * @param {!Object} clazz
 * @return {!jasmine.Matchers}
 */
jasmine.any = function(clazz) {};

/**
 * @return {!jasmine.Matchers}
 */
jasmine.anything = function() {};

/**
 * @param {!Object} sample
 * @return {!jasmine.Matchers}
 */
jasmine.objectContaining = function(sample) {};

/**
 * @param {!Array} sample
 * @return {!jasmine.Matchers}
 */
jasmine.arrayContaining = function(sample) {};

/**
 * @param {string|!RegExp} sample
 * @return {!jasmine.Matchers}
 */
jasmine.stringMatching = function(sample) {};



/** @constructor */
jasmine.Spec = function() {};


/** @type {undefined|function(): string} */
jasmine.Spec.prototype.message;


/**
 * @param {function(this:jasmine.Spec)} after
 */
jasmine.Spec.prototype.after = function(after) {};


/** @param {Error|string} e */
jasmine.Spec.prototype.fail = function(e) {};


/**
 * @param {function()=} opt_onComplete
 */
jasmine.Spec.prototype.finish = function(opt_onComplete) {};


/** @type {undefined|function(): string} */
jasmine.Spec.prototype.getFullName;


/**
 * @constructor
 * @extends {Function}
 * @template T
 */
jasmine.Spy = function() {};


/** @type {!jasmine.SpyStrategy} */
jasmine.Spy.prototype.and;


/**
 * @return {void}
 */
jasmine.Spy.prototype.reset = function() {};


/** @type {!jasmine.CallTracker} */
jasmine.Spy.prototype.calls;


/**
 * @param {...*} var_args
 * @return {!jasmine.Spy}
 */
jasmine.Spy.prototype.withArgs = function(...var_args) {};



/**
 * @constructor
 */
jasmine.CallTracker = function() {};


/**
 * @return {boolean}
 */
jasmine.CallTracker.prototype.any = function() {};


/**
 * @return {!Array<{args: !Array, object: Object}>}
 */
jasmine.CallTracker.prototype.all = function() {};


/**
 * @return {number}
 */
jasmine.CallTracker.prototype.count = function() {};


/**
 * @param {number} index
 * @return {!Array}
 */
jasmine.CallTracker.prototype.argsFor = function(index) {};


/**
 * @return {!Array.<{args: !Array, object: Object}>}
 */
jasmine.CallTracker.prototype.allArgs = function() {};


/**
 * @return {{args: !Array, object: Object}}
 */
jasmine.CallTracker.prototype.mostRecent = function() {};


/**
 * @return {!{args: !Array, object: Object}}
 */
jasmine.CallTracker.prototype.first = function() {};


/** @return {void} */
jasmine.CallTracker.prototype.reset = function() {};



/**
 * @constructor
 */
jasmine.SpyStrategy = function() {};


/** @return {!jasmine.Spy} */
jasmine.SpyStrategy.prototype.callThrough = function() {};


/**
 * @param {*=} value
 * @return {!jasmine.Spy}
 */
jasmine.SpyStrategy.prototype.returnValue = function(value = undefined) {};


/**
 * @param {...*} values
 * @return {!jasmine.Spy}
 */
jasmine.SpyStrategy.prototype.returnValues = function(...values) {};


/**
 * @param {*} error
 * @return {!jasmine.Spy}
 */
jasmine.SpyStrategy.prototype.throwError = function(error) {};


/**
 * @param {!Function} fn
 * @return {!jasmine.Spy}
 */
jasmine.SpyStrategy.prototype.callFake = function(fn) {};


/** @return {!jasmine.Spy} */
jasmine.SpyStrategy.prototype.stub = function() {};



/** @constructor */
jasmine.Suite = function() {};


/**
 * @param {function()=} opt_onComplete
 */
jasmine.Suite.prototype.finish = function(opt_onComplete) {};


/**
 * @param {function(this:jasmine.Spec)} beforeEachFunction
 */
jasmine.Suite.prototype.beforeEach = function(beforeEachFunction) {};


/**
 * @param {function(this:jasmine.Spec)} beforeAllFunction
 */
jasmine.Suite.prototype.beforeAll = function(beforeAllFunction) {};


/**
 * @param {function(this:jasmine.Spec)} afterEachFunction
 */
jasmine.Suite.prototype.afterEach = function(afterEachFunction) {};


/**
 * @param {function(this:jasmine.Spec)} afterAllFunction
 */
jasmine.Suite.prototype.afterAll = function(afterAllFunction) {};



/** @constructor */
jasmine.Env = function() {};


/** @param {!jasmine.Reporter} reporterToAdd */
jasmine.Env.prototype.addReporter = function(reporterToAdd) {};


/** @type {jasmine.Spec} */
jasmine.Env.prototype.currentSpec;


/** @return {void} */
jasmine.Env.prototype.execute = function() {};


/** @param {function(this:jasmine.Spec)} handler */
jasmine.Env.prototype.beforeEach = function(handler) {};


/** @param {function(this:jasmine.Spec)} handler */
jasmine.Env.prototype.beforeAll = function(handler) {};


/** @param {function(this:jasmine.Spec)} handler */
jasmine.Env.prototype.afterEach = function(handler) {};


/** @param {function(this:jasmine.Spec)} handler */
jasmine.Env.prototype.afterAll = function(handler) {};


/** @param {!Object} configuration */
jasmine.Env.prototype.configure = function(configuration) {};


/**
 * @return {!jasmine.Env}
 */
jasmine.getEnv = function() {};


/**
 * @typedef {{
 *   id: number,
 *   description: string,
 *   fullName: string,
 *   failedExpectations: Array,
 *   passedExpectations: Array,
 *   deprecationWarnings: Array,
 *   pendingReason: string,
 *   status: string,
 *   duration: number
 * }}
 */
jasmine.SpecResult;


/**
 * @typedef {{
 *   overallStatus: string,
 *   totalTime: number,
 *   incompleteReason: string,
 *   order: string,
 *   failedExpectations: Array,
 *   deprecationWarnings: Array
 * }}
 */
jasmine.JasmineDoneInfo;


/** @record */
jasmine.Reporter = function() {};

/** @param {!jasmine.SpecResult} result */
jasmine.Reporter.prototype.specDone = function(result) {};

/** @param {!jasmine.SpecResult} result */
jasmine.Reporter.prototype.specStarted = function(result) {};


/**
 * @constructor
 * @implements {jasmine.Reporter}
 */
jasmine.jsApiReporter = function() {};

/** @return {!Array<!jasmine.SpecResult>} results */
jasmine.jsApiReporter.prototype.specs = function() {};

/** @param {!jasmine.JasmineDoneInfo} suiteInfo */
jasmine.jsApiReporter.prototype.jasmineDone = function(suiteInfo) {};


/**
 * @constructor
 * @implements {jasmine.Reporter}
 * @param {!Object} options
 */
jasmine.HtmlReporter = function(options) {};

/** @return {void} */
jasmine.HtmlReporter.prototype.initialize = function() {};


/**
 * @constructor
 * @param {!Object} options
 */
jasmine.QueryString = function(options) {};


/**
 * @param {string} key
 * @return {undefined|string|Object} value
 */
jasmine.QueryString.prototype.getParam = function(key) {};


/**
 * @param {string} key
 * @param {?Object} value
 */
jasmine.QueryString.prototype.navigateWithNewParam = function(key, value) {};


/**
 * @param {string} key
 * @param {?Object} value
 * @return {string} params
 */
jasmine.QueryString.prototype.fullStringWithNewParam = function(key, value) {};


/**
 * @constructor
 * @param {!Object} options
 */
jasmine.HtmlSpecFilter = function(options) {};


/**
 * @param {string} specName
 * @return {boolean} match
 */
jasmine.HtmlSpecFilter.prototype.matches = function(specName) {};


/** @constructor */
jasmine.Timer = function() {};

/**
 * @param {function(this:jasmine.Spec, function())} handler
 * @param {number=} timeout
 */
function beforeEach(handler, timeout = undefined) {}


/**
 * @param {function(this:jasmine.Spec, function())} handler
 * @param {number=} timeout
 */
function beforeAll(handler, timeout = undefined) {}


/**
 * @param {function(this:jasmine.Spec, function())} handler
 * @param {number=} timeout
 */
function afterEach(handler, timeout = undefined) {}


/**
 * @param {function(this:jasmine.Spec, function())} handler
 * @param {number=} timeout
 */
function afterAll(handler, timeout = undefined) {}


/**
 * @param {string} description
 * @param {function(this:jasmine.Suite)} handler
 */
function describe(description, handler) {}


/**
 * @param {?=} message Reason for the failure. NOTE: the type should
 *     be (string|!Error=), however closure's goog.testing.asserts.fail is
 *     aliased as a global and sometimes conflicts, and is typed with an
 *     unknown parameter type.
 */
function fail(message) {}


/**
 * @param {string} description
 * @param {function(this:jasmine.Suite)} handler
 */
function fdescribe(description, handler) {}


/**
 * @param {*} expectedValue
 * @return {!jasmine.AsyncMatchers} matcher
 */
function expectAsync(expectedValue) {}


/**
 * @param {*=} expectedValue
 * @return {!jasmine.Matchers} matcher
 */
function expect(expectedValue = undefined) {}

/**
 * Sets a user-defined property that will be provided to reporters as part of
 * SpecResult#properties
 * @param {string} key The name of the property
 * @param {*} value The value of the property
 */
function setSpecProperty(key, value) {}

/**
 * Sets a user-defined property that will be provided to reporters as part of
 * SuiteResult#properties
 * @param {string} key The name of the property
 * @param {*} value The value of the property
 */
function setSuiteProperty(key, value) {}

/** @typedef {function()} */
var DoneFunc;

/** @type {DoneFunc} */
var doneFuncInst_;
/** @type {function(?=)} */
doneFuncInst_.fail;

/**
 * @param {string} description
 * @param {function(this:jasmine.Spec, DoneFunc)} handler
 * @param {number=} timeout
 */
function it(description, handler, timeout = undefined) {}


/**
 * @param {string} description
 * @param {function(this:jasmine.Spec, DoneFunc)} handler
 * @param {number=} timeout
 */
function fit(description, handler, timeout = undefined) {}


/**
 * @param {string=} description
 */
function pending(description) {}


/**
 * @param {Object} spiedOnObject
 * @param {string} methodName
 * @return {!jasmine.Spy}
 */
function spyOn(spiedOnObject, methodName) {}


/**
 * @param {Object} spiedOnObject
 * @return {!jasmine.Spy}
 */
function spyOnAllFunctions(spiedOnObject) {}

/**
 * @param {!Object} spiedOnObject
 * @param {string} propertyName
 * @param {string=} accessType
 * @return {!jasmine.Spy}
 */
function spyOnProperty(spiedOnObject, propertyName, accessType) {}

/**
 * @param {string} description
 * @param {function(this:jasmine.Suite)} handler
 */
function xdescribe(description, handler) {}


/**
 * @param {string} description
 * @param {function(this:jasmine.Spec, DoneFunc)} handler
 * @param {number=} timeout
 */
function xit(description, handler, timeout = undefined) {}


/**
 * @type {jasmine.Spec}
 */
var currentSpec;


/** @type {!jasmine.jsApiReporter} */
var jsApiReporter;
