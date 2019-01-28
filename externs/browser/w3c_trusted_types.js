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

/**
 * @param {string} name
 * @param {{
 *     createHTML: function(string): string,
 *     createScript: function(string): string,
 *     createScriptURL: function(string): string,
 *     createURL: function(string): string}} policy
 * @param {boolean=} opt_expose
 * @return {!TrustedTypePolicy}
 */
TrustedTypePolicyFactory.prototype.createPolicy = function(
    name, policy, opt_expose) {};

/**
 * @param {string} name
 * @return {!TrustedTypePolicy}
 */
TrustedTypePolicyFactory.prototype.getExposedPolicy = function(name) {};

/** @return {!Array<string>} */
TrustedTypePolicyFactory.prototype.getPolicyNames = function() {};


/** @type {!TrustedTypePolicyFactory} */
var TrustedTypes;
