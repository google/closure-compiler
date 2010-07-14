/*
 * Copyright 2008 Google Inc.
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
 * @fileoverview Definitions for all the extensions over W3C's DOM
 *  specification by webkit. This file depends on w3c_dom2.js.
 *  All the provided definitions has been type annotated
 *
 * @externs
*
 */

Window.prototype.console = {};

/**
 * @param {string} msg
 */
Window.prototype.console.error = function(msg) {};

/**
 * @param {string} msg
 */
Window.prototype.console.info = function(msg) {};

/**
 * @param {string} msg
 */
Window.prototype.console.log = function(msg) {};

/**
 * @param {string} msg
 */
Window.prototype.console.warn = function(msg) {};

/** @type {Node} */
Selection.prototype.baseNode;

/** @type {number} */
Selection.prototype.baseOffset;

/** @type {Node} */
Selection.prototype.extentNode;

/** @type {number} */
Selection.prototype.extentOffset;

/** @type {string} */
Selection.prototype.type;

/**
 * @return {undefined}
 */
Selection.prototype.empty = function() {};

/**
 * @param {Node} baseNode
 * @param {number} baseOffset
 * @param {Node} extentNode
 * @param {number} extentOffset
 * @return {undefined}
 */
Selection.prototype.setBaseAndExtent =
 function(baseNode, baseOffset, extentNode, extentOffset) {};

/**
 * @param {string} alter
 * @param {string} direction
 * @param {string} granularity
 * @return {undefined}
 */
Selection.prototype.modify = function(alter, direction, granularity) {};

/**
 * @param {Element} element
 * @param {string} pseudoElement
 * @param {boolean=} opt_authorOnly
 * @return {CSSRuleList}
 * @nosideeffects
 */
ViewCSS.prototype.getMatchedCSSRules =
    function(element, pseudoElement, opt_authorOnly) {};

/**
 * @param {string} contextId
 * @param {string} name
 * @param {number} width
 * @param {number} height
 * @nosideeffects
 */
Document.prototype.getCSSCanvasContext =
    function(contextId, name, width, height) {};

