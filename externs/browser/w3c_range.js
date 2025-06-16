/*
 * Copyright 2008 The Closure Compiler Authors
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
 * @fileoverview Definitions for W3C's range specification.
 *  This file depends on w3c_dom2.js.
 *  The whole file has been fully type annotated.
 *  Created from
 *   http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html
 *
 * @externs
 * @author stevey@google.com (Steve Yegge)
 */


/**
 * @constructor
 * @extends {AbstractRange}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level-2-Range-Interface
 */
function Range() {}

// constants on the constructor

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-compareHow
 */
Range.START_TO_START;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-compareHow
 */
Range.START_TO_END;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-compareHow
 */
Range.END_TO_END;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-compareHow
 */
Range.END_TO_START;

// constants repeated on the prototype

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-compareHow
 */
Range.prototype.START_TO_START;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-compareHow
 */
Range.prototype.START_TO_END;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-compareHow
 */
Range.prototype.END_TO_END;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-compareHow
 */
Range.prototype.END_TO_START;

/**
 * @type {Node}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level-2-Range-attr-commonParent
 */
Range.prototype.commonAncestorContainer;

/**
 * @param {Node} refNode
 * @param {number} offset
 * @return {undefined}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-method-setStart
 */
Range.prototype.setStart = function(refNode, offset) {};

/**
 * @param {Node} refNode
 * @param {number} offset
 * @return {undefined}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-method-setEnd
 */
Range.prototype.setEnd = function(refNode, offset) {};

/**
 * @param {Node} refNode
 * @return {undefined}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-setStartBefore
 */
Range.prototype.setStartBefore = function(refNode) {};

/**
 * @param {Node} refNode
 * @return {undefined}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-method-setStartAfter
 */
Range.prototype.setStartAfter = function(refNode) {};

/**
 * @param {Node} refNode
 * @return {undefined}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-method-setEndBefore
 */
Range.prototype.setEndBefore = function(refNode) {};

/**
 * @param {Node} refNode
 * @return {undefined}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-method-setEndAfter
 */
Range.prototype.setEndAfter = function(refNode) {};

/**
 * @param {boolean} toStart
 * @return {undefined}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-method-collapse
 */
Range.prototype.collapse = function(toStart) {};

/**
 * @param {Node} refNode
 * @return {undefined}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-method-selectNode
 */
Range.prototype.selectNode = function(refNode) {};

/**
 * @param {Node} refNode
 * @return {undefined}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-method-selectNodeContents
 */
Range.prototype.selectNodeContents = function(refNode) {};

/**
 * @param {number} how
 * @param {Range} sourceRange
 * @return {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-method-compareBoundaryPoints
 */
Range.prototype.compareBoundaryPoints = function(how, sourceRange) {};

/**
 * @return {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-method-deleteContents
 */
Range.prototype.deleteContents = function() {};

/**
 * @return {DocumentFragment}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-method-extractContents
 */
Range.prototype.extractContents = function() {};

/**
 * @return {DocumentFragment}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-method-cloneContents
 */
Range.prototype.cloneContents = function() {};

/**
 * @param {Node} newNode
 * @return {DocumentFragment}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-method-insertNode
 */
Range.prototype.insertNode = function(newNode) {};

/**
 * @param {Node} newParent
 * @return {undefined}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-method-surroundContents
 */
Range.prototype.surroundContents = function(newParent) {};

/**
 * @return {Range}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-method-clone
 */
Range.prototype.cloneRange = function() {};

/**
 * @param {Node} parent
 * @param {number} offset
 * @return {number}
 * @nosideeffects
 * @see https://dom.spec.whatwg.org/#ref-for-dom-range-comparepoint%E2%91%A0
 */
Range.prototype.comparePoint;

/**
 * @return {undefined}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-Range-method-detach
 */
Range.prototype.detach = function() {};

/**
 * @param {!TrustedHTML|string} tag
 * @return {DocumentFragment}
 * @see https://w3c.github.io/DOM-Parsing/#ref-for-dom-range-createcontextualfragment-2
 */
Range.prototype.createContextualFragment;

/**
 * @param {Node} n
 * @return {boolean}
 * @nosideeffects
 * @see https://dom.spec.whatwg.org/#ref-for-dom-range-intersectsnode%E2%91%A0
 */
Range.prototype.intersectsNode;

/**
 * @param {Node} parent
 * @param {number} offset
 * @return {boolean}
 * @nosideeffects
 * @see https://dom.spec.whatwg.org/#dom-range-ispointinrange
 */
Range.prototype.isPointInRange;

// Introduced in DOM Level 2:
/**
 * @interface
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level-2-DocumentRange-idl
 */
function DocumentRange() {}

/**
 * @return {Range}
 * @see http://www.w3.org/TR/DOM-Level-2-Traversal-Range/ranges.html#Level2-DocumentRange-method-createRange
 */
DocumentRange.prototype.createRange = function() {};
