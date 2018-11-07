/*
 * Copyright 2016 The Closure Compiler Authors
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
 * @fileoverview Definitions for W3C's DOM4 specification. This file depends on
 * w3c_dom3.js. The whole file has been fully type annotated. Created from
 * https://www.w3.org/TR/domcore/.
 *
 * @externs
 * @author zhoumotongxue008@gmail.com (Michael Zhou)
 */

/**
 * @typedef {?(DocumentType|Element|CharacterData)}
 * @see https://www.w3.org/TR/domcore/#interface-childnode
 */
var ChildNode;

/**
 * @return {undefined}
 * @see https://www.w3.org/TR/domcore/#dom-childnode-remove
 */
DocumentType.prototype.remove = function() {};

/**
 * @return {undefined}
 * @see https://www.w3.org/TR/domcore/#dom-childnode-remove
 */
Element.prototype.remove = function() {};

/**
 * @return {undefined}
 * @see https://www.w3.org/TR/domcore/#dom-childnode-remove
 */
CharacterData.prototype.remove = function() {};

/**
 * @param {...(!Node|string)} nodes
 * @return {undefined}
 * @see https://dom.spec.whatwg.org/#dom-childnode-replacewith
 */
DocumentType.prototype.replaceWith = function(nodes) {};

/**
 * @type {number}
 * @see https://www.w3.org/TR/2015/REC-dom-20151119/#dfn-error-names-table
 */
DOMException.SECURITY_ERR = 18;

/**
 * @type {number}
 * @see https://www.w3.org/TR/2015/REC-dom-20151119/#dfn-error-names-table
 */
DOMException.NETWORK_ERR = 19;

/**
 * @type {number}
 * @see https://www.w3.org/TR/2015/REC-dom-20151119/#dfn-error-names-table
 */
DOMException.ABORT_ERR = 20;

/**
 * @type {number}
 * @see https://www.w3.org/TR/2015/REC-dom-20151119/#dfn-error-names-table
 */
DOMException.URL_MISMATCH_ERR = 21;

/**
 * @type {number}
 * @see https://www.w3.org/TR/2015/REC-dom-20151119/#dfn-error-names-table
 */
DOMException.QUOTA_EXCEEDED_ERR = 22;

/**
 * @type {number}
 * @see https://www.w3.org/TR/2015/REC-dom-20151119/#dfn-error-names-table
 */
DOMException.TIMEOUT_ERR = 23;

/**
 * @type {number}
 * @see https://www.w3.org/TR/2015/REC-dom-20151119/#dfn-error-names-table
 */
DOMException.INVALID_NODE_TYPE_ERR = 24;

/**
 * @type {number}
 * @see https://www.w3.org/TR/2015/REC-dom-20151119/#dfn-error-names-table
 */
DOMException.DATA_CLONE_ERR = 25;

/**
 * @param {...(!Node|string)} nodes
 * @return {undefined}
 * @see https://dom.spec.whatwg.org/#dom-childnode-replacewith
 */
Element.prototype.replaceWith = function(nodes) {};

/**
 * @param {...(!Node|string)} nodes
 * @return {undefined}
 * @see https://dom.spec.whatwg.org/#dom-childnode-replacewith
 */
CharacterData.prototype.replaceWith = function(nodes) {};

/**
 * @return {!Array<string>}
 * @see https://dom.spec.whatwg.org/#dom-element-getattributenames
 */
Element.prototype.getAttributeNames = function() {};

/**
 * @param {...(!Node|string)} nodes
 * @return {undefined}
 * @see https://dom.spec.whatwg.org/#dom-parentnode-append
 */
Element.prototype.append = function(nodes) {};

/**
 * @param {...(!Node|string)} nodes
 * @return {undefined}
 * @see https://dom.spec.whatwg.org/#dom-parentnode-append
 */
Document.prototype.append = function(nodes) {};

/**
 * @param {...(!Node|string)} nodes
 * @return {undefined}
 * @see https://dom.spec.whatwg.org/#dom-parentnode-append
 */
DocumentFragment.prototype.append = function(nodes) {};

/**
 * @param {...(!Node|string)} nodes
 * @return {undefined}
 * @see https://dom.spec.whatwg.org/#dom-parentnode-prepend
 */
Element.prototype.prepend = function(nodes) {};

/**
 * @param {...(!Node|string)} nodes
 * @return {undefined}
 * @see https://dom.spec.whatwg.org/#dom-parentnode-prepend
 */
Document.prototype.prepend = function(nodes) {};

/**
 * @param {...(!Node|string)} nodes
 * @return {undefined}
 * @see https://dom.spec.whatwg.org/#dom-parentnode-prepend
 */
DocumentFragment.prototype.prepend = function(nodes) {};

/**
 * @param {...(!Node|string)} nodes
 * @return {undefined}
 * @see https://dom.spec.whatwg.org/#dom-childnode-before
 */
Element.prototype.before = function(nodes) {};

/**
 * @param {...(!Node|string)} nodes
 * @return {undefined}
 * @see https://dom.spec.whatwg.org/#dom-childnode-before
 */
DocumentType.prototype.before = function(nodes) {};

/**
 * @param {...(!Node|string)} nodes
 * @return {undefined}
 * @see https://dom.spec.whatwg.org/#dom-childnode-before
 */
CharacterData.prototype.before = function(nodes) {};

/**
 * @param {...(!Node|string)} nodes
 * @return {undefined}
 * @see https://dom.spec.whatwg.org/#dom-childnode-after
 */
Element.prototype.after = function(nodes) {};

/**
 * @param {...(!Node|string)} nodes
 * @return {undefined}
 * @see https://dom.spec.whatwg.org/#dom-childnode-after
 */
DocumentType.prototype.after = function(nodes) {};

/**
 * @param {...(!Node|string)} nodes
 * @return {undefined}
 * @see https://dom.spec.whatwg.org/#dom-childnode-after
 */
CharacterData.prototype.after = function(nodes) {};
