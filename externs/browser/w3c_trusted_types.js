/*
 * Copyright 2018 The Closure Compiler Authors
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
 * @fileoverview Definitions for W3C's Trusted Types specification.
 * @see https://github.com/WICG/trusted-types
 * @externs
 */


/** @constructor */
function TrustedHTML() {}

/** @constructor */
function TrustedScript() {}

/** @constructor */
function TrustedScriptURL() {}

/** @constructor */
function TrustedURL() {}


/** @constructor */
function TrustedTypePolicy() {}

/**
 * @param {string} s
 * @return {!TrustedHTML}
 */
TrustedTypePolicy.prototype.createHTML = function(s) {};

/**
 * @param {string} s
 * @return {!TrustedScript}
 */
TrustedTypePolicy.prototype.createScript = function(s) {};

/**
 * @param {string} s
 * @return {!TrustedScriptURL}
 */
TrustedTypePolicy.prototype.createScriptURL = function(s) {};

/**
 * @param {string} s
 * @return {!TrustedURL}
 */
TrustedTypePolicy.prototype.createURL = function(s) {};


/** @constructor */
function TrustedTypePolicyFactory() {}

/** @record @private */
function TrustedTypePolicyOptions() {};

/**
 *  @type {(function(string, ...*): string)|undefined},
 */
TrustedTypePolicyOptions.prototype.createHTML;

/**
 *  @type {(function(string, ...*): string)|undefined},
 */
TrustedTypePolicyOptions.prototype.createScript;

/**
 *  @type {(function(string, ...*): string)|undefined},
 */
TrustedTypePolicyOptions.prototype.createScriptURL;

/**
 *  @type {(function(string, ...*): string)|undefined},
 */
TrustedTypePolicyOptions.prototype.createURL;

/**
 * @param {string} name
 * @param {!TrustedTypePolicyOptions} policy
 * @param {boolean=} opt_expose
 * @return {!TrustedTypePolicy}
 */
TrustedTypePolicyFactory.prototype.createPolicy = function(
    name, policy, opt_expose) {};

/**
 * @param {string} name
 * @return {!TrustedTypePolicy}
 * @deprecated
 */
TrustedTypePolicyFactory.prototype.getExposedPolicy = function(name) {};

/** @return {!Array<string>} */
TrustedTypePolicyFactory.prototype.getPolicyNames = function() {};

/**
 * @param {*} obj
 * @return {boolean}
 */
TrustedTypePolicyFactory.prototype.isHTML = function(obj) {};

/**
 * @param {*} obj
 * @return {boolean}
 */
TrustedTypePolicyFactory.prototype.isScript = function(obj) {};

/**
 * @param {*} obj
 * @return {boolean}
 */
TrustedTypePolicyFactory.prototype.isScriptURL = function(obj) {};

/**
 * @param {*} obj
 * @return {boolean}
 * @deprecated
 */
TrustedTypePolicyFactory.prototype.isURL = function(obj) {};

/** @type {!TrustedHTML} */
TrustedTypePolicyFactory.prototype.emptyHTML;

/**
 * @type {!TrustedTypePolicyFactory}
 * @deprecated
 */
var TrustedTypes;

/** @type {!TrustedTypePolicyFactory} */
var trustedTypes;
