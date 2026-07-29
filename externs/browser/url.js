/*
 * Copyright 2015 The Closure Compiler Authors
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
 * @fileoverview Definitions for URL and URLSearchParams from the spec at
 * https://url.spec.whatwg.org.
 *
 * @externs
 * @author rdcronin@google.com (Devlin Cronin)
 */

/**
 * @typedef {Array<string>}
 */
var URLSearchParamsTupleType;

/**
 * Represents the query string of a URL.
 *
 * * When `init` is a string, it is basically parsed as a query string
 *   `'name1=value1&name2=value2'`.
 *
 * * When `init` is an array of arrays of string
 *   `([['name1', 'value1'], ['name2', 'value2']])`,
 *   it must contain pairs of strings, where the first item in the pair will be
 *   interpreted as a key and the second as a value.
 *
 *   NOTE: The specification uses Iterable rather than Array, but this is not
 *   supported in Edge 17 - 18.
 *
 * * When `init` is an object, keys and values will be interpreted as such
 *   `({name1: 'value1', name2: 'value2'}).
 *
 * @see https://url.spec.whatwg.org/#interface-urlsearchparams
 * @constructor
 * @implements {Iterable<!Array<string>>}
 * @param {(string|!Array<!URLSearchParamsTupleType>|!Object<string,string>)=}
 *     init
 */
function URLSearchParams(init) {}

/**
 * @param {string} name
 * @param {string} value
 * @return {undefined}
 */
URLSearchParams.prototype.append = function(name, value) {};

/**
 * @param {string} name
 * @return {undefined}
 */
URLSearchParams.prototype.delete = function(name) {};

/**
 * @return {!IteratorIterable<!Array<string>>}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/API/URLSearchParams/entries
 */
URLSearchParams.prototype.entries = function() {};

/**
 * @return {!IteratorIterable<!Array<string>>}
 * @nosideeffects
 * @see https://developer.mozilla.org/en-US/docs/Web/API/URLSearchParams/keys
 * @override
 */
URLSearchParams.prototype[Symbol.iterator] = function() {};

/**
 * @param {function(string, string)} callback
 * @return {undefined}
 */
URLSearchParams.prototype.forEach = function(callback) {};

/**
 * @param {string} name
 * @return {?string}
 */
URLSearchParams.prototype.get = function(name) {};

/**
 * @param {string} name
 * @return {!Array<string>}
 */
URLSearchParams.prototype.getAll = function(name) {};

/**
 * @param {string} name
 * @return {boolean}
 */
URLSearchParams.prototype.has = function(name) {};

/**
 * @return {!IteratorIterable<string>}
 */
URLSearchParams.prototype.keys = function() {};


/**
 * @param {string} name
 * @param {string} value
 * @return {undefined}
 */
URLSearchParams.prototype.set = function(name, value) {};

/**
 * @return {undefined}
 */
URLSearchParams.prototype.sort = function() {};

/**
 * @return {!IteratorIterable<string>}
 */
URLSearchParams.prototype.values = function() {};

/**
 * @see https://url.spec.whatwg.org
 * @constructor
 * @param {!URL|string} url
 * @param {(!URL|string)=} base
 */
function URL(url, base) {}

/** @type {string} */
URL.prototype.href;

/**
 * @const {string}
 */
URL.prototype.origin;

/** @type {string} */
URL.prototype.protocol;

/** @type {string} */
URL.prototype.username;

/** @type {string} */
URL.prototype.password;

/** @type {string} */
URL.prototype.host;

/** @type {string} */
URL.prototype.hostname;

/** @type {string} */
URL.prototype.port;

/** @type {string} */
URL.prototype.pathname;

/** @type {string} */
URL.prototype.search;

/**
 * @const {!URLSearchParams}
 */
URL.prototype.searchParams;

/** @type {string} */
URL.prototype.hash;

/**
 * @param {string} domain
 * @return {string}
 */
URL.domainToASCII = function(domain) {};

/**
 * @param {string} domain
 * @return {string}
 */
URL.domainToUnicode = function(domain) {};

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-createObjectURL
 * @param {!File|!Blob|!MediaSource|!MediaStream} obj
 * @return {string}
 */
URL.createObjectURL = function(obj) {};

/**
 * @see https://url.spec.whatwg.org
 * @param {!URL|string} url
 * @param {(!URL|string)=} base
 * @return {boolean}
 */
URL.canParse = function(url, base) {};

/**
 * @see http://www.w3.org/TR/FileAPI/#dfn-revokeObjectURL
 * @param {!URL|string} url
 * @return {undefined}
 */
URL.revokeObjectURL = function(url) {};

/**
 * @record
 * @see https://urlpattern.spec.whatwg.org/#dictdef-urlpatterncomponentresult
 */
function URLPatternComponentResult() {}
/** @type {!Object<string, (string|undefined)>} */
URLPatternComponentResult.prototype.groups;
/** @type {string} */
URLPatternComponentResult.prototype.input;

/**
 * @record
 * @see https://urlpattern.spec.whatwg.org/#dictdef-urlpatterninit
 */
function URLPatternInit() {}
/** @type {string|undefined} */
URLPatternInit.prototype.baseURL;
/** @type {string|undefined} */
URLPatternInit.prototype.hash;
/** @type {string|undefined} */
URLPatternInit.prototype.hostname;
/** @type {string|undefined} */
URLPatternInit.prototype.password;
/** @type {string|undefined} */
URLPatternInit.prototype.pathname;
/** @type {string|undefined} */
URLPatternInit.prototype.port;
/** @type {string|undefined} */
URLPatternInit.prototype.protocol;
/** @type {string|undefined} */
URLPatternInit.prototype.search;
/** @type {string|undefined} */
URLPatternInit.prototype.username;

/**
 * @record
 * @see https://urlpattern.spec.whatwg.org/#dictdef-urlpatternoptions
 */
function URLPatternOptions() {}
/** @type {boolean|undefined} */
URLPatternOptions.prototype.ignoreCase;

/**
 * @record
 * @see https://urlpattern.spec.whatwg.org/#dictdef-urlpatternresult
 */
function URLPatternResult() {}
/** @type {!URLPatternComponentResult} */
URLPatternResult.prototype.hash;
/** @type {!URLPatternComponentResult} */
URLPatternResult.prototype.hostname;
/** @type {!Array<!URLPatternInput>} */
URLPatternResult.prototype.inputs;
/** @type {!URLPatternComponentResult} */
URLPatternResult.prototype.password;
/** @type {!URLPatternComponentResult} */
URLPatternResult.prototype.pathname;
/** @type {!URLPatternComponentResult} */
URLPatternResult.prototype.port;
/** @type {!URLPatternComponentResult} */
URLPatternResult.prototype.protocol;
/** @type {!URLPatternComponentResult} */
URLPatternResult.prototype.search;
/** @type {!URLPatternComponentResult} */
URLPatternResult.prototype.username;

/**
 * @constructor
 * @param {(!URLPatternInput)=} opt_input
 * @param {(string|!URL|!URLPatternOptions)=} opt_baseURLOrOptions
 * @param {!URLPatternOptions=} opt_options
 * @see https://developer.mozilla.org/en-US/docs/Web/API/URLPattern
   */
function URLPattern(opt_input, opt_baseURLOrOptions, opt_options) {}

/** @type {boolean} */
URLPattern.prototype.hasRegExpGroups;

/** @type {string} */
URLPattern.prototype.hash;

/** @type {string} */
URLPattern.prototype.hostname;

/** @type {string} */
URLPattern.prototype.password;

/** @type {string} */
URLPattern.prototype.pathname;

/** @type {string} */
URLPattern.prototype.port;

/** @type {string} */
URLPattern.prototype.protocol;

/** @type {string} */
URLPattern.prototype.search;

/** @type {string} */
URLPattern.prototype.username;

/**
 * @param {!URLPatternInput=} opt_input
 * @param {(string|!URL)=} opt_baseURL
 * @return {?URLPatternResult}
 */
URLPattern.prototype.exec = function(opt_input, opt_baseURL) {};

/**
 * @param {!URLPatternInput=} opt_input
 * @param {(string|!URL)=} opt_baseURL
 * @return {boolean}
 */
URLPattern.prototype.test = function(opt_input, opt_baseURL) {};

/**
 * @typedef {(string|!URLPatternInit)}
 * @see https://urlpattern.spec.whatwg.org/#typedefdef-urlpatterninput
 */
var URLPatternInput;
