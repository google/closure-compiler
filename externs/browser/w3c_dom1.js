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
 * @fileoverview Definitions for W3C's DOM Level 1 specification.
 *  The whole file has been fully type annotated. Created from
 *  http://www.w3.org/TR/REC-DOM-Level-1/ecma-script-language-binding.html
 *
 * @externs
 */

/**
 * @constructor
 * @param {string=} message
 * @param {string=} name
 * @see https://heycam.github.io/webidl/#idl-DOMException
 * @extends {Error}
 */
function DOMException(message, name) {}

/**
 * @const {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-258A00AF
 */
DOMException.INDEX_SIZE_ERR;

/**
 * @const {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-258A00AF
 */
DOMException.DOMSTRING_SIZE_ERR;

/**
 * @const {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-258A00AF
 */
DOMException.HIERARCHY_REQUEST_ERR;

/**
 * @const {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-258A00AF
 */
DOMException.WRONG_DOCUMENT_ERR;

/**
 * @const {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-258A00AF
 */
DOMException.INVALID_CHARACTER_ERR;

/**
 * @const {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-258A00AF
 */
DOMException.NO_DATA_ALLOWED_ERR;

/**
 * @const {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-258A00AF
 */
DOMException.NO_MODIFICATION_ALLOWED_ERR;

/**
 * @const {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-258A00AF
 */
DOMException.NOT_FOUND_ERR;

/**
 * @const {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-258A00AF
 */
DOMException.NOT_SUPPORTED_ERR;

/**
 * @const {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-258A00AF
 */
DOMException.INUSE_ATTRIBUTE_ERR;

/**
 * @constructor
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-258A00AF
 */
function ExceptionCode() {}

/**
 * @constructor
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-102161490
 */
function DOMImplementation() {}

/**
 * @param {string} feature
 * @param {string} version
 * @return {boolean}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-5CED94D7
 * @nosideeffects
 */
DOMImplementation.prototype.hasFeature = function(feature, version) {};

/**
 * @constructor
 * @implements {EventTarget}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1950641247
 */
function Node() {}

/** @override */
Node.prototype.addEventListener = function(type, listener, opt_options) {};

/** @override */
Node.prototype.removeEventListener = function(type, listener, opt_options) {};

/** @override */
Node.prototype.dispatchEvent = function(evt) {};

/**
 * @type {NamedNodeMap<!Attr>}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#attribute-attributes
 */
Node.prototype.attributes;

/**
 * @type {!NodeList<!Node>}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#attribute-childNodes
 */
Node.prototype.childNodes;

/**
 * @type {Node}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#attribute-firstChild
 */
Node.prototype.firstChild;

/**
 * @type {Node}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#attribute-lastChild
 */
Node.prototype.lastChild;

/**
 * @type {Node}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#attribute-nextSibling
 */
Node.prototype.nextSibling;

/**
 * @type {string}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#attribute-nodeName
 */
Node.prototype.nodeName;

/**
 * @type {string}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#attribute-nodeValue
 */
Node.prototype.nodeValue;

/**
 * @type {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#attribute-nodeType
 */
Node.prototype.nodeType;

/**
 * @type {Document}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#attribute-ownerDocument
 */
Node.prototype.ownerDocument;

/**
 * @type {Node}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#attribute-parentNode
 */
Node.prototype.parentNode;

/**
 * @type {Node}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#attribute-previousSibling
 */
Node.prototype.previousSibling;

/**
 * @param {Node} newChild
 * @return {!Node}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#method-appendChild
 */
Node.prototype.appendChild = function(newChild) {};

/**
 * @param {boolean} deep
 * @return {THIS}
 * @this {THIS}
 * @template THIS
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#method-cloneNode
 * @nosideeffects
 */
Node.prototype.cloneNode = function(deep) {};

/**
 * @return {boolean}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#method-hasChildNodes
 * @nosideeffects
 */
Node.prototype.hasChildNodes = function() {};

/**
 * @param {Node} newChild
 * @param {Node} refChild
 * @return {!Node}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#method-insertBefore
 */
Node.prototype.insertBefore = function(newChild, refChild) {};

/**
 * @param {Node} oldChild
 * @return {!Node}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#method-removeChild
 */
Node.prototype.removeChild = function(oldChild) {};

/**
 * @param {Node} newChild
 * @param {Node} oldChild
 * @return {!Node}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#method-replaceChild
 */
Node.prototype.replaceChild = function(newChild, oldChild) {};

/**
 * @const {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1950641247
 */
Node.ATTRIBUTE_NODE;

/**
 * @const {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1950641247
 */
Node.CDATA_SECTION_NODE;

/**
 * @const {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1950641247
 */
Node.COMMENT_NODE;

/**
 * @const {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1950641247
 */
Node.DOCUMENT_FRAGMENT_NODE;

/**
 * @const {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1950641247
 */
Node.DOCUMENT_NODE;

/**
 * @const {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1950641247
 */
Node.DOCUMENT_TYPE_NODE;

/**
 * @const {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1950641247
 */
Node.ELEMENT_NODE;

/**
 * @const {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1950641247
 */
Node.ENTITY_NODE;

/**
 * @const {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1950641247
 */
Node.ENTITY_REFERENCE_NODE;

/**
 * @const {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1950641247
 */
Node.PROCESSING_INSTRUCTION_NODE;

/**
 * @const {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1950641247
 */
Node.TEXT_NODE;

/**
 * @const {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1950641247
 */
Node.NOTATION_NODE;

/**
 * @constructor
 * @extends {Node}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-B63ED1A3
 */
function DocumentFragment() {}

/**
 * @constructor
 * @extends {Node}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#i-Document
 */
function Document() {}

/**
 * @param {string} html
 * @return {!Document}
 * @see https://developer.mozilla.org/docs/Web/API/Document/parseHTMLUnsafe_static
 */
Document.parseHTMLUnsafe = function(html) {};

/**
 * @type {!HTMLCollection}
 * @see https://dom.spec.whatwg.org/#parentnode
 */
Document.prototype.children;

/**
 * @type {DocumentType}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#attribute-doctype
 */
Document.prototype.doctype;

/**
 * @type {!HTMLHtmlElement}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#attribute-documentElement
 */
Document.prototype.documentElement;

/**
 * @type {DOMImplementation}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#attribute-implementation
 */
Document.prototype.implementation;

/**
 * @param {string} name
 * @return {!Attr}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#method-createAttribute
 * @nosideeffects
 */
Document.prototype.createAttribute = function(name) {};

/**
 * @param {string} data
 * @return {!Comment}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#method-createComment
 * @nosideeffects
 */
Document.prototype.createComment = function(data) {};

/**
 * @param {string} data
 * @return {!CDATASection}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#method-createCDATASection
 * @nosideeffects
 */
Document.prototype.createCDATASection = function(data) {};

/**
 * @return {!DocumentFragment}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#method-createDocumentFragment
 * @nosideeffects
 */
Document.prototype.createDocumentFragment = function() {};

/**
 * Create a DOM element.
 *
 * Web components introduced the second parameter as a way of extending existing
 * tags (e.g. document.createElement('button', {is: 'fancy-button'})).
 *
 * @param {string} tagName
 * @param {({is: string}|string)=} opt_typeExtension
 * @return {!Element}
 * @nosideeffects
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#method-createElement
 * @see https://dom.spec.whatwg.org/#dom-document-createelement
 */
Document.prototype.createElement = function(tagName, opt_typeExtension) {};

/**
 * @param {string} target
 * @param {string} data
 * @return {!ProcessingInstruction}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#method-createProcessingInstruction
 * @nosideeffects
 */
Document.prototype.createProcessingInstruction = function(target, data) {};

/**
 * @param {number|string} data
 * @return {!Text}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#method-createTextNode
 * @nosideeffects
 */
Document.prototype.createTextNode = function(data) {};

/**
 * @param {string} tagname
 * @return {!NodeList<!Element>}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-A6C9094
 * @nosideeffects
 */
Document.prototype.getElementsByTagName = function(tagname) {};

/**
 * @see https://developer.mozilla.org/en-US/docs/Web/API/Document/open
 * @see https://html.spec.whatwg.org/multipage/dynamic-markup-insertion.html#dom-document-open
 */
Document.prototype.open;

/**
 * @return {undefined}
 * @see https://html.spec.whatwg.org/multipage/dynamic-markup-insertion.html#dom-document-close
 */
Document.prototype.close = function() {};

/**
 * @param {!TrustedHTML|string} text
 * @return {undefined}
 * @see https://html.spec.whatwg.org/multipage/dynamic-markup-insertion.html#dom-document-write
 */
Document.prototype.write = function(text) {};

/**
 * @param {!TrustedHTML|string} text
 * @return {undefined}
 * @see https://html.spec.whatwg.org/multipage/dynamic-markup-insertion.html#dom-document-writeln
 */
Document.prototype.writeln = function(text) {};

/** @type {?function (!Event)} */
Document.prototype.onvisibilitychange;

/** @type {?function (!Event)} */
Document.prototype.onfullscreenchange;

/** @type {?function (!Event)} */
Document.prototype.onfullscreenerror;

/** @type {?function (!Event)} */
Document.prototype.onpointerlockchange;

/** @type {?function (!Event)} */
Document.prototype.onpointerlockerror;

/**
 * @type {!FragmentDirective|undefined}
 * @see https://developer.mozilla.org/docs/Web/API/Document/fragmentDirective
 */
Document.prototype.fragmentDirective;

/**
 * @constructor
 * @see https://developer.mozilla.org/docs/Web/API/FragmentDirective
 */
function FragmentDirective() {}

/**
 * @constructor
 * @implements {IArrayLike<T>}
 * @implements {Iterable<T>}
 * @template T
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-536297177
 */
function NodeList() {}

/** @override */
NodeList.prototype[Symbol.iterator] = function() {};

/**
 * @type {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-203510337
 */
NodeList.prototype.length;

/**
 * @param {number} index
 * @return {T|null}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-844377136
 */
NodeList.prototype.item = function(index) {};

/**
 * @param {?function(this:S, T, number, !NodeList<T>): ?} callback
 * @param {S=} opt_thisobj
 * @template S
 * @return {undefined}
 * @see https://developer.mozilla.org/en-US/docs/Web/API/NodeList/forEach
 */
NodeList.prototype.forEach = function(callback, opt_thisobj) {};

/**
 * @return {!IteratorIterable<!Array<number|T>>}
 * @see https://developer.mozilla.org/en-US/docs/Web/API/NodeList/entries
 */
NodeList.prototype.entries = function() {};

/**
 * @return {!IteratorIterable<number>}
 * @see https://developer.mozilla.org/en-US/docs/Web/API/NodeList/keys
 */
NodeList.prototype.keys = function() {};

/**
 * @return {!IteratorIterable<T>}
 * @see https://developer.mozilla.org/en-US/docs/Web/API/NodeList/values
 */
NodeList.prototype.values = function() {};

/**
 * @constructor
 * @implements {IObject<(string|number), T>}
 * @implements {IArrayLike<T>}
 * @implements {Iterable<T>}
 * @template T
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1780488922
 */
function NamedNodeMap() {}

/** @override */
NamedNodeMap.prototype[Symbol.iterator] = function() {};

/**
 * @type {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-6D0FB19E
 */
NamedNodeMap.prototype.length;

/**
 * @param {string} name
 * @return {Node}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1074577549
 * @nosideeffects
 */
NamedNodeMap.prototype.getNamedItem = function(name) {};

/**
 * @param {string} namespace
 * @param {string} localName
 * @return {?Node}
 * @see https://developer.mozilla.org/docs/Web/API/NamedNodeMap/getNamedItemNS
 */
NamedNodeMap.prototype.getNamedItemNS = function(namespace, localName) {};

/**
 * @param {number} index
 * @return {Node}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-349467F9
 * @nosideeffects
 */
NamedNodeMap.prototype.item = function(index) {};

/**
 * @param {string} name
 * @return {Node}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-D58B193
 */
NamedNodeMap.prototype.removeNamedItem = function(name) {};

/**
 * @param {string} namespace
 * @param {string} localName
 * @return {Node}
 * @see https://developer.mozilla.org/docs/Web/API/NamedNodeMap/removeNamedItemNS
 */
NamedNodeMap.prototype.removeNamedItemNS = function(namespace, localName) {};

/**
 * @param {Node} arg
 * @return {Node}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1025163788
 */
NamedNodeMap.prototype.setNamedItem = function(arg) {};

/**
 * @param {Node} arg
 * @return {?Node}
 * @see https://developer.mozilla.org/docs/Web/API/NamedNodeMap/setNamedItemNS
 */
NamedNodeMap.prototype.setNamedItemNS = function(arg) {};

/**
 * @constructor
 * @extends {Node}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-FF21A306
 */
function CharacterData() {}

/**
 * @type {string}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-72AB8359
 */
CharacterData.prototype.data;

/**
 * @type {number}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-7D61178C
 */
CharacterData.prototype.length;

/**
 * @param {string} arg
 * @return {undefined}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-32791A2F
 */
CharacterData.prototype.appendData = function(arg) {};

/**
 * @param {number} offset
 * @param {number} count
 * @return {undefined}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-7C603781
 */
CharacterData.prototype.deleteData = function(offset, count) {};

/**
 * @param {number} offset
 * @param {string} arg
 * @return {undefined}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-3EDB695F
 */
CharacterData.prototype.insertData = function(offset, arg) {};

/**
 * @param {number} offset
 * @param {number} count
 * @param {string} arg
 * @return {undefined}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-E5CBA7FB
 */
CharacterData.prototype.replaceData = function(offset, count, arg) {};

/**
 * @param {number} offset
 * @param {number} count
 * @return {string}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-6531BCCF
 * @nosideeffects
 */
CharacterData.prototype.substringData = function(offset, count) {};

/**
 * @constructor
 * @extends {Node}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-637646024
 */
function Attr() {}

/**
 * @type {string}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1112119403
 */
Attr.prototype.name;

/**
 * @type {boolean}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-862529273
 */
Attr.prototype.specified;

/**
 * @type {string}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-221662474
 */
Attr.prototype.value;

/**
 * @constructor
 * @see https://developer.mozilla.org/docs/Web/API/CSSStyleValue
 */
function CSSStyleValue() {}

/**
 * @param {string} property
 * @param {string} cssText
 * @return {!CSSStyleValue}
 * @see https://developer.mozilla.org/docs/Web/API/CSSStyleValue/parse_static
 */
CSSStyleValue.parse = function(property, cssText) {}

/**
 * @param {string} property
 * @param {string} cssText
 * @return {!Array<!CSSStyleValue>}
 * @see https://developer.mozilla.org/docs/Web/API/CSSStyleValue/parseAll_static
 */
CSSStyleValue.parseAll = function(property, cssText) {}

/**
 * @override
 * @return {string}
 */
CSSStyleValue.prototype.toString = function() {};


/**
 * @constructor
 * @extends {CSSStyleValue}
 * @see https://www.w3.org/TR/css-typed-om-1/#cssnumericvalue
 */
function CSSNumericValue() {}

/**
 * @param {string} cssText
 * @return {!CSSNumericValue}
 */
CSSNumericValue.parse = function(cssText) {}

/**
 * @param {...number|!CSSNumericValue} values
 * @return {!CSSNumericValue}
 */
CSSNumericValue.prototype.add = function(values) {}

/**
 * @param {...number|!CSSNumericValue} values
 * @return {!CSSNumericValue}
 */
CSSNumericValue.prototype.div = function(values) {}

/**
 * @param {...number|!CSSNumericValue} values
 * @return {boolean}
 */
CSSNumericValue.prototype.equals = function(values) {};

/**
 * @param {...number|!CSSNumericValue} values
 * @return {!CSSNumericValue}
 */
CSSNumericValue.prototype.max = function(values) {};

/**
 * @param {...number|!CSSNumericValue} values
 * @return {!CSSNumericValue}
 */
CSSNumericValue.prototype.min = function(values) {};

/**
 * @param {...number|!CSSNumericValue} values
 * @return {!CSSNumericValue}
 */
CSSNumericValue.prototype.mul = function(values) {};

/**
 * @param {...number|!CSSNumericValue} values
 * @return {!CSSNumericValue}
 */
CSSNumericValue.prototype.sub = function(values) {};

/**
 * @param {string} unit
 * @return {!CSSUnitValue}
 */
CSSNumericValue.prototype.to = function(unit) {};

/**
 * @param {...string} units
 * @return {!CSSNumericValue}
 * TODO(b/408277839): This should really return a CSSMathSum.
 * Change the return type when it is added.
 */
CSSNumericValue.prototype.toSum = function(units) {};

/**
 * @return {!CSSNumericType}
 */
CSSNumericValue.prototype.type = function() {};

/**
 * @constructor
 * @extends {CSSNumericValue}
 * @param {number} value
 * @param {string} unit
 * @see https://developer.mozilla.org/docs/Web/API/CSSUnitValue
 */
function CSSUnitValue(value, unit) {}

/**
 * @type {number}
 * @see https://developer.mozilla.org/docs/Web/API/CSSUnitValue/value
 */
CSSUnitValue.prototype.value;

/**
 * @const {string}
 * @see https://developer.mozilla.org/docs/Web/API/CSSUnitValue/unit
 */
CSSUnitValue.prototype.unit;

/**
 * @typedef {{
 *   angle: (number|undefined),
 *   flex: (number|undefined),
 *   frequency: (number|undefined),
 *   length: (number|undefined),
 *   percent: (number|undefined),
 *   percentHint: (string|undefined),
 *   resolution: (number|undefined),
 *   time: (number|undefined)
 * }}
 */
var CSSNumericType;

/**
 * @constructor
 * @extends {CSSStyleValue}
 * @param {string} value
 */
function CSSKeywordValue(value) {}

/**
 * @type {string}
 */
CSSKeywordValue.prototype.value;

/**
 * @constructor
 * @extends {CSSStyleValue}
 */
function CSSImageValue() {}

/**
 * @constructor
 * @extends {CSSNumericValue}
 */
function CSSMathValue() {}

/** @type {string} */
CSSMathValue.prototype.operator;

/**
 * @constructor
 * @extends {CSSMathValue}
 * @param {number|!CSSNumericValue} value
 */
function CSSMathInvert(value) {}

/** @type {!CSSNumericValue} */
CSSMathInvert.prototype.value

/**
 * @constructor
 * @extends {CSSMathValue}
 * @param {number|!CSSNumericValue} lower
 * @param {number|!CSSNumericValue} value
 * @param {number|!CSSNumericValue} upper
 */
function CSSMathClamp(lower, value, upper) {}

/** @type {!CSSNumericValue} */
CSSMathClamp.prototype.lower;

/** @type {!CSSNumericValue} */
CSSMathClamp.prototype.upper;

/** @type {!CSSNumericValue} */
CSSMathClamp.prototype.value;

/**
 * @constructor
 * @extends {CSSMathValue}
 * @param {...(number|!CSSNumericValue)} args
 */
function CSSMathMin(args) {}

/** @type {!CSSNumericArray} */
CSSMathMin.prototype.values;

/**
 * @constructor
 * @extends {CSSMathValue}
 * @param {number|!CSSNumericValue} arg
 */
function CSSMathNegate(arg) {}

/** @type {!CSSNumericValue} */
CSSMathNegate.prototype.value;

/**
 * @constructor
 * @extends {CSSMathValue}
 * @param {...(number|!CSSNumericValue)} args
 */
function CSSMathProduct(args) {}

/** @type {!CSSNumericArray} */
CSSMathProduct.prototype.values;

/**
 * @constructor
 * @extends {CSSMathValue}
 * @param {...(number|!CSSNumericValue)} args
 */
function CSSMathSum(args) {}

/** @type {!CSSNumericArray} */
CSSMathSum.prototype.values;

/**
 * @constructor
 */
function CSSTransformComponent() {}

/** @type {boolean} */
CSSTransformComponent.prototype.is2D;

/** @return {!DOMMatrix} */
CSSTransformComponent.prototype.toMatrix = function() {};

/**
 * @override
 * @return {string}
 */
CSSTransformComponent.prototype.toString = function() {};

/**
 * @constructor
 * @extends {CSSTransformComponent}
 * @param {!CSSNumericValue|number} angle_or_x
 * @param {!CSSNumericValue|number=} y
 * @param {!CSSNumericValue|number=} z
 * @param {!CSSNumericValue=} angle
 */
function CSSRotate(angle_or_x, y, z, angle) {}

/** @type {!CSSNumericValue} */
CSSRotate.prototype.angle;

/** @type {!CSSNumericValue|number} */
CSSRotate.prototype.x;

/** @type {!CSSNumericValue|number} */
CSSRotate.prototype.y;

/** @type {!CSSNumericValue|number} */
CSSRotate.prototype.z;

/**
 * @typedef {{
 *   is2D: (boolean|undefined)
 * }}
 */
var CSSMatrixComponentOptions;

/**
 * @constructor
 * @extends {CSSStyleValue}
 * @implements {IArrayLike<!CSSTransformComponent>}
 * @param {!Array<!CSSTransformComponent>} transforms
 */
function CSSTransformValue(transforms) {}

/** @type {boolean} */
CSSTransformValue.prototype.is2D;

/** @type {number} */
CSSTransformValue.prototype.length;

/** @return {!DOMMatrix} */
CSSTransformValue.prototype.toMatrix = function() {};

/**
 * @param {function(this: S, !CSSTransformComponent, number, !CSSTransformValue): ?} callbackfn
 * @param {S=} thisArg
 * @template S
 * @see https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Array/forEach
 */
CSSTransformValue.prototype.forEach = function(callbackfn, thisArg) {};

/**
 * @constructor
 * @extends {CSSTransformComponent}
 * @param {!DOMMatrixReadOnly} matrix
 * @param {!CSSMatrixComponentOptions=} options
 */
function CSSMatrixComponent(matrix, options) {}

/** @type {!DOMMatrix} */
CSSMatrixComponent.prototype.matrix;

/**
 * @constructor
 * @param {string} variable
 * @param {?CSSUnparsedValue=} fallback
 */
function CSSVariableReferenceValue(variable, fallback) {}

/** @type {string} */
CSSVariableReferenceValue.prototype.variable;

/** @type {?CSSUnparsedValue} */
CSSVariableReferenceValue.prototype.fallback;

/**
 * @typedef {(string|!CSSVariableReferenceValue)}
 */
var CSSUnparsedSegment;

/**
 * @constructor
 * @extends {CSSStyleValue}
 * @implements {IArrayLike<!CSSUnparsedSegment>}
 * @param {!Array<!CSSUnparsedSegment>} members
 */
function CSSUnparsedValue(members) {}

/** @type {number} */
CSSUnparsedValue.prototype.length;

/**
 * @param {function(this: S, !CSSUnparsedSegment, number, !CSSUnparsedValue): ?} callbackfn
 * @param {S=} thisArg
 * @template S
 * @see https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Array/forEach
 */
CSSUnparsedValue.prototype.forEach = function(callbackfn, thisArg) {};

/**
 * @constructor
 * @implements {IArrayLike<!CSSNumericValue>}
 */
function CSSNumericArray() {}

/**
 * @param {function(this: S, !CSSNumericValue, number, !CSSNumericArray): ?} callbackfn
 * @param {S=} thisArg
 * @template S
 * @see https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Array/forEach
 */
CSSNumericArray.prototype.forEach = function(callbackfn, thisArg) {};

/** @type {number} */
CSSNumericArray.prototype.length;

/**
 * @constructor
 * @extends {CSSMathValue}
 * @param {...(number|!CSSNumericValue)} args
 */
function CSSMathMax(args) {}

/** @type {!CSSNumericArray} */
CSSMathMax.prototype.values;

/**
 * @constructor
 * @extends {Animation}
 */
function CSSTransition() {}

/** @type {string} */
CSSTransition.prototype.transitionProperty;

/**
 * @constructor
 * @extends {Animation}
 */
function CSSAnimation() {}

/** @type {string} */
CSSAnimation.prototype.animationName;

/**
 * @constructor
 * @extends {CSSRule}
 */
function CSSNestedDeclarations() {}

/**
 * @type {!CSSStyleDeclaration}
 */
CSSNestedDeclarations.prototype.style;

/**
 * @constructor
 * @extends {CSSTransformComponent}
 * @param {!CSSNumericValue|string} length
 */
function CSSPerspective(length) {}

/** @type {!CSSNumericValue|string} */
CSSPerspective.prototype.length;

/**
 * @constructor
 * @extends {CSSTransformComponent}
 * @param {!CSSNumericValue|number} x
 * @param {!CSSNumericValue|number} y
 * @param {!CSSNumericValue|number} z
 * @param {!CSSNumericValue} angle
 */
function CSSTranslate(x, y, z, angle) {}

/** @type {!CSSNumericValue|number} */ CSSTranslate.prototype.x;
/** @type {!CSSNumericValue|number} */ CSSTranslate.prototype.y;
/** @type {!CSSNumericValue|number} */ CSSTranslate.prototype.z;
/** @type {!CSSNumericValue} */ CSSTranslate.prototype.angle;

/**
 * @constructor
 * @extends {CSSTransformComponent}
 * @param {!CSSNumericValue|number} x
 * @param {!CSSNumericValue|number} y
 * @param {!CSSNumericValue|number=} z
 */
function CSSScale(x, y, z) {}

/** @type {!CSSNumericValue|number} */ CSSScale.prototype.x;
/** @type {!CSSNumericValue|number} */ CSSScale.prototype.y;
/** @type {!CSSNumericValue|number} */ CSSScale.prototype.z;

/**
 * @constructor
 * @extends {CSSTransformComponent}
 * @param {!CSSNumericValue} ax
 * @param {!CSSNumericValue} ay
 */
function CSSSkew(ax, ay) {}

/** @type {!CSSNumericValue} */ CSSSkew.prototype.ax;
/** @type {!CSSNumericValue} */ CSSSkew.prototype.ay;

/**
 * @constructor
 * @extends {CSSTransformComponent}
 * @param {!CSSNumericValue} ax
 */
function CSSSkewX(ax) {}

/** @type {!CSSNumericValue} */ CSSSkewX.prototype.ax;

/**
 * @constructor
 * @extends {CSSTransformComponent}
 * @param {!CSSNumericValue} ay
 */
function CSSSkewY(ay) {}

/** @type {!CSSNumericValue} */ CSSSkewY.prototype.ay;

/**
 * @constructor
 * @see https://developer.mozilla.org/docs/Web/API/StylePropertyMapReadOnly
 */
function StylePropertyMapReadOnly() {}

/**
 * @const {number}
 * @see https://developer.mozilla.org/docs/Web/API/StylePropertyMapReadOnly/size
 */
StylePropertyMapReadOnly.prototype.size;

/**
 * @param {string} property
 * @return {(CSSStyleValue|undefined)}
 * @see https://developer.mozilla.org/docs/Web/API/StylePropertyMapReadOnly/get
 */
StylePropertyMapReadOnly.prototype.get = function(property) {}

/**
 * @param {string} property
 * @return {!Array<!CSSStyleValue>}
 * @see https://developer.mozilla.org/docs/Web/API/StylePropertyMapReadOnly/getAll
 */
StylePropertyMapReadOnly.prototype.getAll = function(property) {}

/**
 * @param {string} property
 * @return {boolean}
 * @see https://developer.mozilla.org/docs/Web/API/StylePropertyMapReadOnly/has
 */
StylePropertyMapReadOnly.prototype.has = function(property) {}

/**
 * @param {function(!Array<!CSSStyleValue>, string, !StylePropertyMapReadOnly): void} callbackfn
 * @param {*=} opt_thisArg
 * @see https://developer.mozilla.org/docs/Web/API/StylePropertyMapReadOnly/forEach
 */
StylePropertyMapReadOnly.prototype.forEach = function(callbackfn, opt_thisArg) {}

/**
 * @typedef {{
 *   serializableShadowRoots: (boolean|undefined),
 *   shadowRoots: (!Array<!ShadowRoot>|undefined)
 * }}
 */
var GetHTMLOptions;

/**
 * @constructor
 * @extends {Node}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-745549614
 */
function Element() {}

/**
 * @type {!DOMTokenList}
 * @implicitCast
 * @see https://developer.mozilla.org/docs/Web/API/Element/part
 */
Element.prototype.part;

/**
 * @type {string}
 * @implicitCast
 * @see https://dom.spec.whatwg.org/index.html#dom-element-id
 */
Element.prototype.id;

/**
 * An Element always contains a non-null NamedNodeMap containing the attributes
 * of this node.
 * @type {!NamedNodeMap<!Attr>}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#attribute-attributes
 */
Element.prototype.attributes;

/**
 * @type {string}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#attribute-tagName
 */
Element.prototype.tagName;

/**
 * @implicitCast
 * @type {?}
 * @see https://developer.mozilla.org/en-US/docs/Web/API/Element/className
 *    We type it as ? even though it is a string, because some SVG elements have
 *    className that is an object, which isn't a subtype of string.
 *    Alternative: TypeScript types this as string and types className on
 *    SVGElement as ?.
 */
Element.prototype.className;

/**
 * @return {!StylePropertyMapReadOnly}
 * @see https://developer.mozilla.org/docs/Web/API/Element/computedStyleMap
 */
Element.prototype.computedStyleMap = function() {};

/**
 * @param {!GetHTMLOptions=} options
 * @return {string}
 * @see https://developer.mozilla.org/docs/Web/API/Element/getHTML
 */
Element.prototype.getHTML = function (options) {}

/**
 * @param {string} name
 * @param {?number=} flags
 * @return {string}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#method-getAttribute
 * @see http://msdn.microsoft.com/en-us/library/ms536429(VS.85).aspx
 * @nosideeffects
 */
Element.prototype.getAttribute = function(name, flags) {};

/**
 * @param {string} name
 * @return {Attr}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#method-getAttributeNode
 * @nosideeffects
 */
Element.prototype.getAttributeNode = function(name) {};

/**
 * @param {string} tagname
 * @return {!NodeList<!Element>}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1938918D
 * @nosideeffects
 */
Element.prototype.getElementsByTagName = function(tagname) {};

/**
 * @param {string} name
 * @return {undefined}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#method-removeAttribute
 */
Element.prototype.removeAttribute = function(name) {};

/**
 * @param {Attr} oldAttr
 * @return {?Attr}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#method-removeAttributeNode
 */
Element.prototype.removeAttributeNode = function(oldAttr) {};

/**
 * @param {string} name
 * @param {string|number|boolean|!TrustedHTML|!TrustedScriptURL|!TrustedScript}
 *     value Values are converted to strings with ToString, so we accept number
 *     and boolean since both convert easily to strings.
 * @return {undefined}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#method-setAttribute
 */
Element.prototype.setAttribute = function(name, value) {};

/**
 * @param {Attr} newAttr
 * @return {?Attr}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#method-setAttributeNode
 */
Element.prototype.setAttributeNode = function(newAttr) {};

// Event handlers
// The DOM level 3 spec has a good index of these
// http://www.w3.org/TR/DOM-Level-3-Events/#event-types

/** @type {?function (Event)} */ Element.prototype.onabort;
/** @type {?function (Event)} */ Element.prototype.onbeforeinput;
/** @type {?function (BeforeUnloadEvent)} */ Element.prototype.onbeforeunload;
/** @type {?function (Event)} */ Element.prototype.onblur;
/** @type {?function (Event)} */ Element.prototype.onchange;
/** @type {?function (Event)} */ Element.prototype.onclick;
/** @type {?function (Event)} */ Element.prototype.oncompositionstart;
/** @type {?function (Event)} */ Element.prototype.oncompositionupdate;
/** @type {?function (Event)} */ Element.prototype.oncompositionend;
/** @type {?function (Event)} */ Element.prototype.oncontextmenu;
/** @type {?function (!Event)} */ Element.prototype.oncontextlost;
/** @type {?function (!Event)} */ Element.prototype.oncontextrestored;
/** @type {?function (Event)} */ Element.prototype.oncopy;
/** @type {?function (Event)} */ Element.prototype.oncut;
/** @type {?function (Event)} */ Element.prototype.ondblclick;
/** @type {?function (Event)} */ Element.prototype.onerror;
/** @type {?function (Event)} */ Element.prototype.onfocus;
/** @type {?function (Event)} */ Element.prototype.onfocusin;
/** @type {?function (Event)} */ Element.prototype.onfocusout;
/** @type {?function (Event)} */ Element.prototype.oninput;
/** @type {?function (Event)} */ Element.prototype.onkeydown;
/** @type {?function (Event)} */ Element.prototype.onkeypress;
/** @type {?function (Event)} */ Element.prototype.onkeyup;
/** @type {?function (Event): void} */ Element.prototype.onload;
/** @type {?function (Event): void} */ Element.prototype.onunload;
/** @type {?function (Event)} */ Element.prototype.onmousedown;
/** @type {?function (Event)} */ Element.prototype.onmousemove;
/** @type {?function (Event)} */ Element.prototype.onmouseout;
/** @type {?function (Event)} */ Element.prototype.onmouseover;
/** @type {?function (Event)} */ Element.prototype.onmouseup;
/** @type {?function (Event)} */ Element.prototype.onmousewheel;
/** @type {?function (Event)} */ Element.prototype.onpaste;
/** @type {?function (Event)} */ Element.prototype.onreset;
/** @type {?function (Event)} */ Element.prototype.onresize;
/** @type {?function (Event)} */ Element.prototype.onscroll;
/** @type {?function (Event)} */ Element.prototype.onselect;
/** @type {?function (Event=)} */ Element.prototype.onsubmit;
/** @type {?function (Event)} */ Element.prototype.ontextinput;
/** @type {?function (Event)} */ Element.prototype.onwheel;
/** @type {?function (!DragEvent)} */ Element.prototype.ondrag;
/** @type {?function (!DragEvent)} */ Element.prototype.ondragend;
/** @type {?function (!DragEvent)} */ Element.prototype.ondragenter;
/** @type {?function (!DragEvent)} */ Element.prototype.ondragleave;
/** @type {?function (!DragEvent)} */ Element.prototype.ondragover;
/** @type {?function (!DragEvent)} */ Element.prototype.ondragstart;
/** @type {?function (!DragEvent)} */ Element.prototype.ondrop;

/**
 * @constructor
 * @extends {Element}
 * @see https://developer.mozilla.org/docs/Web/API/MathMLElement
 */
function MathMLElement() {}

/**
 * @constructor
 * @extends {CharacterData}
 * @param {string=} contents Optional textual content.
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1312295772
 */
function Text(contents) {}

/**
 * @param {number} offset
 * @return {Text}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-38853C1D
 */
Text.prototype.splitText = function(offset) {};

/**
 * @constructor
 * @extends {CharacterData}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1728279322
 */
function Comment() {}

/**
 * @constructor
 * @extends {Text}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-667469212
 */
function CDATASection() {}

/**
 * @constructor
 * @extends {Node}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-412266927
 */
function DocumentType() {}

/**
 * @type {string}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1844763134
 */
DocumentType.prototype.name;

/**
 * @constructor
 * @extends {Node}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1004215813
 */
function ProcessingInstruction() {}

/**
 * @type {string}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-837822393
 */
ProcessingInstruction.prototype.data;

/**
 * @type {string}
 * @see http://www.w3.org/TR/1998/REC-DOM-Level-1-19981001/level-one-core.html#ID-1478689192
 */
ProcessingInstruction.prototype.target;


/**
 * @constructor
 * @implements {EventTarget}
 */
function Window() {}
Window.prototype.Window;

/** @override */
Window.prototype.addEventListener = function(type, listener, opt_options) {};

/** @override */
Window.prototype.removeEventListener = function(type, listener, opt_options) {};

/** @override */
Window.prototype.dispatchEvent = function(evt) {};

/** @type {!Navigator} */
Window.prototype.clientInformation;

/** @type {?function(!DeviceMotionEvent)} */
Window.prototype.ondevicemotion;

/** @type {?function(!DeviceOrientationEvent)} */
Window.prototype.ondeviceorientation;

/** @type {?function(!DeviceOrientationEvent)} */
Window.prototype.ondeviceorientationabsolute;

/** @type {?function (Event)} */ Window.prototype.onabort;
/** @type {?function (BeforeUnloadEvent)} */ Window.prototype.onbeforeunload;
/** @type {?function (Event)} */ Window.prototype.onblur;
/** @type {?function (Event)} */ Window.prototype.onchange;
/** @type {?function (Event)} */ Window.prototype.onclick;
/** @type {?function (Event)} */ Window.prototype.onclose;
/** @type {?function (Event)} */ Window.prototype.oncontextmenu;
/** @type {?function (Event)} */ Window.prototype.ondblclick;
/** @type {?function (!DragEvent)} */ Window.prototype.ondrag;
/** @type {?function (!DragEvent)} */ Window.prototype.ondragend;
/** @type {?function (!DragEvent)} */ Window.prototype.ondragenter;
/** @type {?function (!DragEvent)} */ Window.prototype.ondragleave;
/** @type {?function (!DragEvent)} */ Window.prototype.ondragover;
/** @type {?function (!DragEvent)} */ Window.prototype.ondragstart;
/** @type {?function (!DragEvent)} */ Window.prototype.ondrop;
// onerror has a special signature.
// See
//  https://developer.mozilla.org/en-US/docs/Web/API/GlobalEventHandlers/onerror
/**
 * @type {?function (string, string, number, number, !Error):?}
 */
Window.prototype.onerror;
/** @type {?function (MessageEvent<*>)} */ Window.prototype.onmessageerror
/** @type {?function (PageTransitionEvent)} */ Window.prototype.onpagehide;
/** @type {?function (PageTransitionEvent)} */ Window.prototype.onpageshow;
/** @type {?function (!Event)} */ Window.prototype.onpageswap;
/** @type {?function (!Event)} */ Window.prototype.onpagereveal;
/** @type {?function (Event)} */ Window.prototype.onfocus;
/** @type {?function (Event)} */ Window.prototype.onhashchange;
/** @type {?function (Event)} */ Window.prototype.onkeydown;
/** @type {?function (Event)} */ Window.prototype.onkeypress;
/** @type {?function (Event)} */ Window.prototype.onkeyup;
/** @type {?function (Event)} */ Window.prototype.onload;
/** @type {?function (Event)} */ Window.prototype.onmousedown;
/** @type {?function (Event)} */ Window.prototype.onmousemove;
/** @type {?function (Event)} */ Window.prototype.onmouseout;
/** @type {?function (Event)} */ Window.prototype.onmouseover;
/** @type {?function (Event)} */ Window.prototype.onmouseup;
/** @type {?function (Event)} */ Window.prototype.onmousewheel;
/** @type {?function (!Event)} */ Window.prototype.onauxclick;
/** @type {?function (Event)} */ Window.prototype.onpaint;
/** @type {?function (Event)} */ Window.prototype.onpopstate;
/** @type {?function (Event)} */ Window.prototype.onreset;
/** @type {?function (Event)} */ Window.prototype.onresize;
/** @type {?function (Event)} */ Window.prototype.onscroll;
/** @type {?function (!Event)} */ Window.prototype.onscrollend;
/** @type {?function (Event)} */ Window.prototype.onselect;
/** @type {?function (Event=)} */ Window.prototype.onsubmit;
/** @type {?function (TransitionEvent)} */ Window.prototype.ontransitioncancel;
/** @type {?function (TransitionEvent)} */ Window.prototype.ontransitionend;
/** @type {?function (TransitionEvent)} */ Window.prototype.ontransitionrun;
/** @type {?function (TransitionEvent)} */ Window.prototype.ontransitionstart;
/** @type {?function (Event)} */ Window.prototype.onunhandledrejection;
/** @type {?function (Event)} */ Window.prototype.onunload;
/** @type {?function (Event)} */ Window.prototype.onwheel;
/** @type {?function (Event)} */ Window.prototype.onstorage;
/** @type {?function (!AnimationEvent)} */ Window.prototype.onanimationcancel;
/** @type {?function (!AnimationEvent)} */ Window.prototype.onanimationend;
/** @type {?function (!AnimationEvent)} */ Window.prototype.onanimationiteration;
/** @type {?function (!AnimationEvent)} */ Window.prototype.onanimationstart;
/** @type {?function (!SecurityPolicyViolationEvent)} */ Window.prototype.onsecuritypolicyviolation;
/** @type {?function (!Event)} */ Window.prototype.oninvalid;
/** @type {?function (!PointerEvent)} */ Window.prototype.onlostpointercapture;
/** @type {?function (!PointerEvent)} */ Window.prototype.ongotpointercapture;
/** @type {?function (!PointerEvent)} */ Window.prototype.onpointercancel;
/** @type {?function (!PointerEvent)} */ Window.prototype.onpointerdown;
/** @type {?function (!PointerEvent)} */ Window.prototype.onpointerenter;
/** @type {?function (!PointerEvent)} */ Window.prototype.onpointerleave;
/** @type {?function (!PointerEvent)} */ Window.prototype.onpointermove;
/** @type {?function (!PointerEvent)} */ Window.prototype.onpointerout;
/** @type {?function (!PointerEvent)} */ Window.prototype.onpointerover;
/** @type {?function (!PointerEvent)} */ Window.prototype.onpointerup;
/** @type {?function (!Event)} */ Window.prototype.onslotchange;
/** @type {?function (!Event)} */ Window.prototype.ontoggle;
/** @type {?function (!Event)} */ Window.prototype.onbeforetoggle;
/** @type {?function (!Event)} */ Window.prototype.onlanguagechange;
/** @type {?function (!Event)} */ Window.prototype.onafterprint;
/** @type {?function (!Event)} */ Window.prototype.onbeforeprint;
/** @type {?function (!GamepadEvent)} */ Window.prototype.ongamepadconnected;
/** @type {?function (!GamepadEvent)} */ Window.prototype.ongamepaddisconnected;
/** @type {?function (!PromiseRejectionEvent)} */ Window.prototype.onrejectionhandled;
