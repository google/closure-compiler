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
 * @fileoverview Externs definitions for the Sinon library, 1.17 branch.
 *
 * Note that this file is incomplete.
 *
 * This file defines some virtual types, please don't use these directly, but
 * follow the official API guidelines.
 *
 * @externs
 * @see http://sinonjs.org/docs/
 */

var sinon;



sinon.sandbox;

/**
 * @param {!Object=} opt_config
 * @return {!SinonSandbox}
 */
sinon.sandbox.create = function(opt_config) {};

/**
 * @constructor
 */
var SinonSandbox = function() {};

SinonSandbox.prototype.restore = function() {};

/**
 * @type {!SinonFakeServer|undefined}
 */
SinonSandbox.prototype.server;

/**
 * @return {!SinonStub}
 */
SinonSandbox.prototype.stub = function() {};



/**
 * @constructor
 */
var SinonStub = function() {};



sinon.fakeServer;

/**
 * @param {!Object=} opt_config
 * @return {!SinonFakeServer}
 */
sinon.fakeServer.create = function(opt_config) {};

/**
 * @constructor
 */
var SinonFakeServer = function() {};

/**
 * @type {!Array<!SinonFakeXmlHttpRequest>}
 */
SinonFakeServer.prototype.requests;

/**
 * @type {boolean|undefined}
 */
SinonFakeServer.prototype.respondImmediately;

SinonFakeServer.prototype.restore = function() {};

/**
 * Note: incomplete definition because it is tricky.
 * @param {...*} var_args
 */
SinonFakeServer.prototype.respondWith = function(var_args) {};



/**
 * @constructor
 * @extends {XMLHttpRequest}
 */
var SinonFakeXmlHttpRequest = function() {};

/**
 * @type {?string}
 */
SinonFakeXmlHttpRequest.prototype.requestBody;

/**
 * @param {?number} status
 * @param {?Object<string, string>} headers
 * @param {?string} body
 */
SinonFakeXmlHttpRequest.prototype.respond = function(status, headers, body) {};
