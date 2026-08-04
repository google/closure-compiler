/*
 * Copyright 2026 The Closure Compiler Authors
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
 * @fileoverview Externs for the HTML Sanitizer API.
 * @see https://wicg.github.io/sanitizer-api/
 * @externs
 */

/**
 * @record
 * @see https://wicg.github.io/sanitizer-api/#dictdef-sanitizerattributenamespace
 */
function SanitizerAttributeNamespace() {}
/** @type {string} */
SanitizerAttributeNamespace.prototype.name;
/** @type {string|null|undefined} */
SanitizerAttributeNamespace.prototype.namespace;

/**
 * @record
 * @see https://wicg.github.io/sanitizer-api/#dictdef-sanitizerconfig
 */
function SanitizerConfig() {}
/** @type {!Array<!SanitizerAttribute>|undefined} */
SanitizerConfig.prototype.attributes;
/** @type {boolean|undefined} */
SanitizerConfig.prototype.comments;
/** @type {boolean|undefined} */
SanitizerConfig.prototype.dataAttributes;
/** @type {!Array<!SanitizerElementWithAttributes>|undefined} */
SanitizerConfig.prototype.elements;
/** @type {!Array<!SanitizerAttribute>|undefined} */
SanitizerConfig.prototype.removeAttributes;
/** @type {!Array<!SanitizerElement>|undefined} */
SanitizerConfig.prototype.removeElements;
/** @type {!Array<!SanitizerElement>|undefined} */
SanitizerConfig.prototype.replaceWithChildrenElements;

/**
 * @record
 * @see https://wicg.github.io/sanitizer-api/#dictdef-sanitizerelementnamespace
 */
function SanitizerElementNamespace() {}
/** @type {string} */
SanitizerElementNamespace.prototype.name;
/** @type {string|null|undefined} */
SanitizerElementNamespace.prototype.namespace;


/**
 * @record
 * @extends {SanitizerElementNamespace}
 * @see https://wicg.github.io/sanitizer-api/#dictdef-sanitizerelementnamespacewithattributes
 */
function SanitizerElementNamespaceWithAttributes() {}
/** @type {!Array<!SanitizerAttribute>|undefined} */
SanitizerElementNamespaceWithAttributes.prototype.attributes;
/** @type {!Array<!SanitizerAttribute>|undefined} */
SanitizerElementNamespaceWithAttributes.prototype.removeAttributes;

/**
 * @constructor
 * @param {!SanitizerConfig|!SanitizerPresets=} opt_configuration
 * @see https://wicg.github.io/sanitizer-api/#sanitizer
 */
function Sanitizer(opt_configuration) {}

/**
 * @param {!SanitizerAttribute} attribute
 * @return {boolean}
 */
Sanitizer.prototype.allowAttribute = function(attribute) {};

/**
 * @param {!SanitizerElementWithAttributes} element
 * @return {boolean}
 */
Sanitizer.prototype.allowElement = function(element) {};

/**
 * @return {!SanitizerConfig}
 */
Sanitizer.prototype.get = function() {};

/**
 * @param {!SanitizerAttribute} attribute
 * @return {boolean}
 */
Sanitizer.prototype.removeAttribute = function(attribute) {};

/**
 * @param {!SanitizerElement} element
 * @return {boolean}
 */
Sanitizer.prototype.removeElement = function(element) {};

/**
 * @return {boolean}
 */
Sanitizer.prototype.removeUnsafe = function() {};

/**
 * @param {!SanitizerElement} element
 * @return {boolean}
 */
Sanitizer.prototype.replaceElementWithChildren = function(element) {};

/**
 * @param {boolean} allow
 * @return {boolean}
 */
Sanitizer.prototype.setComments = function(allow) {};

/**
 * @param {boolean} allow
 * @return {boolean}
 */
Sanitizer.prototype.setDataAttributes = function(allow) {};

/**
 * @typedef {string|!SanitizerAttributeNamespace}
 * @see https://wicg.github.io/sanitizer-api/#typedefdef-sanitizerattribute
 */
var SanitizerAttribute;

/**
 * @typedef {string|!SanitizerElementNamespace}
 * @see https://wicg.github.io/sanitizer-api/#typedefdef-sanitizerelement
 */
var SanitizerElement;

/**
 * @typedef {string|!SanitizerElementNamespaceWithAttributes}
 * @see https://wicg.github.io/sanitizer-api/#typedefdef-sanitizerelementwithattributes
 */
var SanitizerElementWithAttributes;

/**
 * @typedef {string}
 * Valid values: 'default'.
 * @see https://wicg.github.io/sanitizer-api/#enumdef-sanitizerpresets
 */
var SanitizerPresets;
