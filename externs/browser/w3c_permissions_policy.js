/*
 * Copyright 2021 The Closure Compiler Authors
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
 * @fileoverview Definitions for W3C's Permissions Policy API and Feature Policy API.
 * @see https://w3c.github.io/webappsec-permissions-policy/
 * @see https://developer.mozilla.org/en-US/docs/Web/HTTP/Feature_Policy
 *
 * @externs
 * @author zhengwe@google.com (Zheng Wei)
 */

/**
 * @interface
 * @see https://w3c.github.io/webappsec-permissions-policy/#the-policy-object
 */
function PermissionsPolicy() {}

/**
 * @param {string} feature
 * @param {string=} origin
 * @return {boolean}
 */
PermissionsPolicy.prototype.allowsFeature = function(feature, origin) {};

/**
 * @return {!Array<string>}
 */
PermissionsPolicy.prototype.features = function() {};

/**
 * @return {!Array<string>}
 */
PermissionsPolicy.prototype.allowedFeatures = function() {};

/**
 * @param {string} feature
 * @return {!Array<string>}
 */
PermissionsPolicy.prototype.getAllowlistForFeature = function(feature) {};

/**
 * @type {!PermissionsPolicy|undefined}
 */
Document.prototype.featurePolicy;

/**
 * @type {!PermissionsPolicy|undefined}
 * @see https://w3c.github.io/webappsec-permissions-policy/#the-policy-object
 */
Document.prototype.permissionsPolicy;

/**
 * @type {!PermissionsPolicy|undefined}
 * @see https://w3c.github.io/webappsec-permissions-policy/#the-policy-object
 */
HTMLIFrameElement.prototype.featurePolicy;

/**
 * @type {!PermissionsPolicy|undefined}
 * @see https://w3c.github.io/webappsec-permissions-policy/#the-policy-object
 */
HTMLIFrameElement.prototype.permissionsPolicy;


