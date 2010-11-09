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
 * @fileoverview Definitions for all the extensions over
 *  W3C's DOM specification by Gecko. This file depends on
 *  w3c_dom2.js.
 *
 * @externs
 */

// TODO: Almost all of it has not been annotated with types.

// Gecko DOM;

/**
 * Mozilla only???
 * @constructor
 * @extends {HTMLElement}
 */
function HTMLSpanElement() {}

/**
 * @see https://developer.mozilla.org/en/Components_object
 */
Window.prototype.Components;

/**
 * @type Window
 * @see https://developer.mozilla.org/en/DOM/window.content
 */
Window.prototype.content;

/**
 * @type {boolean}
 * @see https://developer.mozilla.org/en/DOM/window.closed
 */
Window.prototype.closed;

Window.prototype.console;

/** @see https://developer.mozilla.org/en/DOM/window.controllers */
Window.prototype.controllers;

/** @see https://developer.mozilla.org/en/DOM/window.crypto */
Window.prototype.crypto;

/**
 * Gets/sets the status bar text for the given window.
 * @type {string}
 * @see https://developer.mozilla.org/en/DOM/window.defaultStatus
 */
Window.prototype.defaultStatus;

/** @see https://developer.mozilla.org/en/DOM/window.dialogArguments */
Window.prototype.dialogArguments;

/** @see https://developer.mozilla.org/en/DOM/window.directories */
Window.prototype.directories;

/**
 * @type {!Document}
 * @see https://developer.mozilla.org/en/DOM/window.document
 */
Window.prototype.document;

Window.prototype.eval;

/**
 * @type {HTMLObjectElement|HTMLIFrameElement|null}
 * @see https://developer.mozilla.org/en/DOM/window.frameElement
 */
Window.prototype.frameElement;

/**
 * @type {?Array}
 * @see https://developer.mozilla.org/en/DOM/window.frames
 */
Window.prototype.frames;

/**
 * @type {boolean}
 * @see https://developer.mozilla.org/en/DOM/window.fullScreen
 */
Window.prototype.fullScreen;

/**
 * @see https://developer.mozilla.org/en/DOM/Storage#globalStorage
 */
Window.prototype.globalStorage;

/**
 * @type {!History}
 * @see https://developer.mozilla.org/en/DOM/window.history
 */
Window.prototype.history;

/**
 * @type {number}
 * @see https://developer.mozilla.org/en/DOM/window.innerHeight
 */
Window.prototype.innerHeight;

/**
 * @type {number}
 * @see https://developer.mozilla.org/en/DOM/window.innerWidth
 */
Window.prototype.innerWidth;

/**
 * Returns the number of frames (either frame or iframe elements) in the
 * window.
 *
 * @type {number}
 * @see https://developer.mozilla.org/en/DOM/window.length
 */
Window.prototype.length;

/**
 * @type {!Location}
 * @implicitCast
 * @see https://developer.mozilla.org/en/DOM/window.location
 */
Window.prototype.location;

/**
 * @see https://developer.mozilla.org/en/DOM/window.locationbar
 */
Window.prototype.locationbar;

/**
 * @see https://developer.mozilla.org/en/DOM/window.menubar
 */
Window.prototype.menubar;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en/DOM/window.name
 */
Window.prototype.name;

/**
 * @type {Navigator}
 * @see https://developer.mozilla.org/en/DOM/window.navigator
 */
Window.prototype.navigator;

/**
 * @type {?Window}
 * @see https://developer.mozilla.org/en/DOM/window.opener
 */
Window.prototype.opener;

/**
 * @type {number}
 * @see https://developer.mozilla.org/en/DOM/window.outerHeight
 */
Window.prototype.outerHeight;

/**
 * @type {number}
 * @see https://developer.mozilla.org/en/DOM/window.outerWidth
 */
Window.prototype.outerWidth;

/**
 * @type {number}
 * @see https://developer.mozilla.org/en/DOM/window.pageXOffset
 */
Window.prototype.pageXOffset;

/**
 * @type {number}
 * @see https://developer.mozilla.org/en/DOM/window.pageYOffset
 */
Window.prototype.pageYOffset;

/**
 * @type {?Window}
 * @see https://developer.mozilla.org/en/DOM/window.parent
 */
Window.prototype.parent;

/** @see https://developer.mozilla.org/en/DOM/window.personalbar */
Window.prototype.personalbar;

/** @see https://developer.mozilla.org/en/DOM/window.pkcs11 */
Window.prototype.pkcs11;

Window.prototype.returnValue;

/** @see https://developer.mozilla.org/En/DOM/window.screen */
Window.prototype.screen;

/**
 * @type {number}
 * @see https://developer.mozilla.org/En/DOM/window.screen.availTop
 */
Window.prototype.screen.availTop;

/**
 * @type {number}
 * @see https://developer.mozilla.org/En/DOM/window.screen.availLeft
 */
Window.prototype.screen.availLeft;

/**
 * @type {number}
 * @see https://developer.mozilla.org/En/DOM/window.screen.availHeight
 */
Window.prototype.screen.availHeight;

/**
 * @type {number}
 * @see https://developer.mozilla.org/En/DOM/window.screen.availWidth
 */
Window.prototype.screen.availWidth;

/**
 * @type {number}
 * @see https://developer.mozilla.org/En/DOM/window.screen.colorDepth
 */
Window.prototype.screen.colorDepth;

/**
 * @type {number}
 * @see https://developer.mozilla.org/En/DOM/window.screen.height
 */
Window.prototype.screen.height;

/**
 * @type {number}
 * @see https://developer.mozilla.org/En/DOM/window.screen.left
 */
Window.prototype.screen.left;

/**
 * @type {number}
 * @see https://developer.mozilla.org/En/DOM/window.screen.pixelDepth
 */
Window.prototype.screen.pixelDepth;

/**
 * @type {number}
 * @see https://developer.mozilla.org/En/DOM/window.screen.top
 */
Window.prototype.screen.top;

/**
 * @type {number}
 * @see https://developer.mozilla.org/En/DOM/window.screen.width
 */
Window.prototype.screen.width;

/**
 * @type {number}
 * @see https://developer.mozilla.org/En/DOM/window.screenX
 */
Window.prototype.screenX;

/**
 * @type {number}
 * @see https://developer.mozilla.org/En/DOM/window.screenY
 */
Window.prototype.screenY;

/** @see https://developer.mozilla.org/en/DOM/window.scrollbars */
Window.prototype.scrollbars;

/**
 * @type {number}
 * @see https://developer.mozilla.org/En/DOM/window.scrollMaxX
 */
Window.prototype.scrollMaxX;

/**
 * @type {number}
 * @see https://developer.mozilla.org/En/DOM/window.scrollMaxY
 */
Window.prototype.scrollMaxY;

/**
 * @type {number}
 * @see https://developer.mozilla.org/En/DOM/window.scrollX
 */
Window.prototype.scrollX;

/**
 * @type {number}
 * @see https://developer.mozilla.org/En/DOM/window.scrollY
 */
Window.prototype.scrollY;

/**
 * @type {!Window}
 * @see https://developer.mozilla.org/en/DOM/window.self
 */
Window.prototype.self;

/** @see https://developer.mozilla.org/en/DOM/Storage#sessionStorage */
Window.prototype.sessionStorage;

/** @see https://developer.mozilla.org/en/DOM/window.sidebar */
Window.prototype.sidebar;

/**
 * @type {?string}
 * @see https://developer.mozilla.org/en/DOM/window.status
 */
Window.prototype.status;

/** @see https://developer.mozilla.org/en/DOM/window.statusbar */
Window.prototype.statusbar;

/** @see https://developer.mozilla.org/en/DOM/window.toolbar */
Window.prototype.toolbar;

/**
 * @type {!Window}
 * @see https://developer.mozilla.org/en/DOM/window.self
 */
Window.prototype.top;

/**
 * @type {!Window}
 * @see https://developer.mozilla.org/en/DOM/window.self
 */
Window.prototype.window;

/**
 * @param {*} message
 * @see https://developer.mozilla.org/en/DOM/window.alert
 */
Window.prototype.alert = function(message) {};

/**
 * Decodes a string of data which has been encoded using base-64 encoding.
 *
 * @param {string} encodedData
 * @see https://developer.mozilla.org/en/DOM/window.atob
 * @nosideeffects
 */
Window.prototype.atob = function(encodedData) {};

/** @see https://developer.mozilla.org/en/DOM/window.back */
Window.prototype.back = function() {};

/** @see https://developer.mozilla.org/en/DOM/window.blur */
Window.prototype.blur = function() {};

/**
 * @param {string} stringToEncode
 * @return {string}
 * @see https://developer.mozilla.org/en/DOM/window.btoa
 * @nosideeffects
 */
Window.prototype.btoa = function(stringToEncode) {};

/** @deprecated */
Window.prototype.captureEvents;

/**
 * @param {number|undefined?} intervalID
 * @see https://developer.mozilla.org/en/DOM/window.clearInterval
 */
Window.prototype.clearInterval = function(intervalID) {};

/**
 * @param {number|undefined?} timeoutID
 * @see https://developer.mozilla.org/en/DOM/window.clearTimeout
 */
Window.prototype.clearTimeout = function(timeoutID) {};

/** @see https://developer.mozilla.org/en/DOM/window.close */
Window.prototype.close = function() {};

/**
 * @param {*} message
 * @return {boolean}
 */
Window.prototype.confirm = function(message) {};

/**
 * @param {string} regular
 * @return {string}
 * @see https://developer.mozilla.org/en/DOM/window.escape
 * @nosideeffects
 */
Window.prototype.escape = function(regular) {};

/** @see https://developer.mozilla.org/en/DOM/window.find */
Window.prototype.find;

/** @see https://developer.mozilla.org/en/DOM/window.focus */
window.focus = function() {};

/** @see https://developer.mozilla.org/en/DOM/window.forward */
Window.prototype.forward = function() {};

/** @see https://developer.mozilla.org/en/DOM/window.getAttention */
Window.prototype.getAttention = function() {};

/**
 * @param {Element} element
 * @param {string?} pseudoElt
 * @return {CSSStyleDeclaration}
 * @nosideeffects
 */
Window.prototype.getComputedStyle = function(element, pseudoElt) {};

/**
 * @return {Selection}
 * @see https://developer.mozilla.org/en/DOM/window.getSelection
 * @nosideeffects
 */
Window.prototype.getSelection = function() {};

/** @see https://developer.mozilla.org/en/DOM/window.home */
Window.prototype.home = function() {};

Window.prototype.openDialog;
Window.prototype.postMessage;
Window.prototype.releaseEvents;
Window.prototype.scrollByLines;
Window.prototype.scrollByPages;

/**
 * @param {Function|string} callback
 * @param {number} delay
 * @param {...*} var_args
 * @return {number}
 */
Window.prototype.setInterval;

/**
 * @param {Function|string} callback
 * @param {number} delay
 * @param {...*} var_args
 * @return {number}
 */
Window.prototype.setTimeout = function(callback, delay, var_args) {};
Window.prototype.showModalDialog;
Window.prototype.sizeToContent;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536769(VS.85).aspx
 */
Window.prototype.stop = function() {};

/**
 * @param {string} escaped
 * @return {string}
 * @see https://developer.mozilla.org/en/DOM/window.unescape
 * @nosideeffects
 */
Window.prototype.unescape = function(escaped) {};

Window.prototype.updateCommands;
/** @type {?function (Event)} */ Window.prototype.onabort;
/** @type {?function (Event)} */ Window.prototype.onbeforeunload;
/** @type {?function (Event)} */ Window.prototype.onblur;
/** @type {?function (Event)} */ Window.prototype.onchange;
/** @type {?function (Event)} */ Window.prototype.onclick;
/** @type {?function (Event)} */ Window.prototype.onclose;
/** @type {?function (Event)} */ Window.prototype.oncontextmenu;
/** @type {?function (Event)} */ Window.prototype.ondblclick;
/** @type {?function (Event)} */ Window.prototype.ondragdrop;
// onerror has a special signature.
// See https://developer.mozilla.org/en/DOM/window.onerror
// and http://msdn.microsoft.com/en-us/library/cc197053(VS.85).aspx
/** @type {?function (string, string, number)} */
Window.prototype.onerror;
/** @type {?function (Event)} */ Window.prototype.onfocus;
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
/** @type {?function (Event)} */ Window.prototype.onpaint;
/** @type {?function (Event)} */ Window.prototype.onreset;
/** @type {?function (Event)} */ Window.prototype.onresize;
/** @type {?function (Event)} */ Window.prototype.onscroll;
/** @type {?function (Event)} */ Window.prototype.onselect;
/** @type {?function (Event)} */ Window.prototype.onsubmit;
/** @type {?function (Event)} */ Window.prototype.onunload;

// properties of Document
Document.prototype.alinkColor;
Document.prototype.anchors;
Document.prototype.applets;
/** @type {boolean} */ Document.prototype.async;
/** @type {string?} */ Document.prototype.baseURI;
Document.prototype.baseURIObject;
Document.prototype.bgColor;
/** @type {HTMLBodyElement} */ Document.prototype.body;
Document.prototype.characterSet;
Document.prototype.compatMode;
Document.prototype.contentType;
/** @type {string} */ Document.prototype.cookie;
Document.prototype.defaultView;
Document.prototype.designMode;
Document.prototype.documentURIObject;
Document.prototype.domain;
Document.prototype.embeds;
Document.prototype.fgColor;
/** @type {Element} */ Document.prototype.firstChild;
Document.prototype.forms;
/** @type {number} */ Document.prototype.height;
/** @type {Array} */ Document.prototype.images;
Document.prototype.implementation;
Document.prototype.lastModified;
Document.prototype.linkColor;
Document.prototype.links;
/**
 * @type {!Location}
 * @implicitCast
 */
Document.prototype.location;
Document.prototype.namespaceURI;
Document.prototype.nodePrincipal;
Document.prototype.plugins;
Document.prototype.popupNode;
Document.prototype.referrer;
Document.prototype.styleSheets;
/** @type {?string} */ Document.prototype.title;
Document.prototype.tooltipNode;
/** @type {string} */ Document.prototype.URL;
Document.prototype.vlinkColor;
/** @type {number} */ Document.prototype.width;

// Methods of Document
Document.prototype.clear;
Document.prototype.close;
Document.prototype.createAttribute;
Document.prototype.createCDATASection;
Document.prototype.createComment;
Document.prototype.createDocumentFragment;

/**
 * @see https://developer.mozilla.org/en/DOM/document.createElementNS
 * @param {string} namespaceURI
 * @param {string} qualifiedName
 * @return {!Element}
 */
Document.prototype.createElementNS = function(namespaceURI, qualifiedName) {};
Document.prototype.createEntityReference;

/**
 * @param {string} type
 * @return {Event}
 */
Document.prototype.createEvent = function(type) {};
Document.prototype.createNSResolver;
Document.prototype.createProcessingInstruction;
/** @return {Range} */ Document.prototype.createRange = function() {};
Document.prototype.createTextNode;
Document.prototype.createTreeWalker;

/**
 * @param {number} x
 * @param {number} y
 * @return {Element}
 * @nosideeffects
 */
Document.prototype.elementFromPoint = function(x, y) {};
Document.prototype.evaluate;
Document.prototype.execCommand;

/**
 * @param {string} s id.
 * @return {HTMLElement}
 * @nosideeffects
 */
Document.prototype.getElementById = function(s) {};

/**
 * @param {string} name
 * @return {!NodeList}
 * @nosideeffects
 */
Document.prototype.getElementsByClassName = function(name) {};

/**
 * @param {string} name
 * @return {!NodeList}
 * @nosideeffects
 */
Document.prototype.getElementsByName = function(name) {};

/**
 * @param {string} namespace
 * @param {string} name
 * @return {!NodeList}
 * @nosideeffects
 */
Document.prototype.getElementsByTagNameNS = function(namespace, name) {};

/**
 * @param {Node} externalNode
 * @param {boolean} deep
 * @return {Node}
 */
Document.prototype.importNode = function(externalNode, deep) {};

/** @param {string} uri */
Document.prototype.load = function(uri) {};
Document.prototype.loadOverlay;
Document.prototype.open;
Document.prototype.queryCommandEnabled;
Document.prototype.queryCommandIndeterm;
Document.prototype.queryCommandState;
Document.prototype.queryCommandValue;
Document.prototype.write;
Document.prototype.writeln;
Document.prototype.ononline;
Document.prototype.onoffline;

// XUL
/**
 * @see http://developer.mozilla.org/en/DOM/document.getBoxObjectFor
 * @return {BoxObject}
 * @nosideeffects
 */
Document.prototype.getBoxObjectFor = function(element) {};

// From:
// http://lxr.mozilla.org/mozilla1.8/source/dom/public/idl/range/nsIDOMNSRange.idl

/**
 * @param {string} tag
 * @return {DocumentFragment}
 */
Range.prototype.createContextualFragment;

/**
 * @param {Node} parent
 * @param {number} offset
 * @return {boolean}
 * @nosideeffects
 */
Range.prototype.isPointInRange;

/**
 * @param {Node} parent
 * @param {number} offset
 * @return {number}
 * @nosideeffects
 */
Range.prototype.comparePoint;

/**
 * @param {Node} n
 * @return {boolean}
 * @nosideeffects
 */
Range.prototype.intersectsNode;

/**
 * @param {Node} n
 * @return {number}
 * @nosideeffects
 */
Range.prototype.compareNode;


/** @constructor */
function Selection() {}

/**
 * @type {Node}
 * @see https://developer.mozilla.org/en/DOM/Selection/anchorNode
 */
Selection.prototype.anchorNode;

/**
 * @type {number}
 * @see https://developer.mozilla.org/en/DOM/Selection/anchorOffset
 */
Selection.prototype.anchorOffset;

/**
 * @type {Node}
 * @see https://developer.mozilla.org/en/DOM/Selection/focusNode
 */
Selection.prototype.focusNode;

/**
 * @type {number}
 * @see https://developer.mozilla.org/en/DOM/Selection/focusOffset
 */
Selection.prototype.focusOffset;

/**
 * @type {boolean}
 * @see https://developer.mozilla.org/en/DOM/Selection/isCollapsed
 */
Selection.prototype.isCollapsed;

/**
 * @type {number}
 * @see https://developer.mozilla.org/en/DOM/Selection/rangeCount
 */
Selection.prototype.rangeCount;

/**
 * @param {Range} range
 * @return {undefined}
 * @see https://developer.mozilla.org/en/DOM/Selection/addRange
 */
Selection.prototype.addRange = function(range) {};

/**
 * @param {number} index
 * @return {Range}
 * @see https://developer.mozilla.org/en/DOM/Selection/getRangeAt
 * @nosideeffects
 */
Selection.prototype.getRangeAt = function(index) {};

/**
 * @param {Node} node
 * @param {number} index
 * @return {undefined}
 * @see https://developer.mozilla.org/en/DOM/Selection/collapse
 */
Selection.prototype.collapse = function(node, index) {};

/**
 * @return {undefined}
 * @see https://developer.mozilla.org/en/DOM/Selection/collapseToEnd
 */
Selection.prototype.collapseToEnd = function() {};

/**
 * @return {undefined}
 * @see https://developer.mozilla.org/en/DOM/Selection/collapseToStart
 */
Selection.prototype.collapseToStart = function() {};

/**
 * @param {Node} node
 * @param {boolean} partlyContained
 * @return {boolean}
 * @see https://developer.mozilla.org/en/DOM/Selection/containsNode
 * @nosideeffects
 */
Selection.prototype.containsNode = function(node, partlyContained) {};

/**
 * @see https://developer.mozilla.org/en/DOM/Selection/deleteFromDocument
 */
Selection.prototype.deleteFromDocument = function() {};

/**
 * @param {Node} parentNode
 * @param {number} offset
 * @see https://developer.mozilla.org/en/DOM/Selection/extend
 */
Selection.prototype.extend = function(parentNode, offset) {};

/**
 * @see https://developer.mozilla.org/en/DOM/Selection/removeAllRanges
 */
Selection.prototype.removeAllRanges = function() {};

/**
 * @param {Range} range
 * @see https://developer.mozilla.org/en/DOM/Selection/removeRange
 */
Selection.prototype.removeRange = function(range) {};

/**
 * @param {Node} parentNode
 * @see https://developer.mozilla.org/en/DOM/Selection/selectAllChildren
 */
Selection.prototype.selectAllChildren;

/**
 * @see https://developer.mozilla.org/en/DOM/Selection/selectionLanguageChange
 */
Selection.prototype.selectionLanguageChange;

/** @type {NamedNodeMap} */ Element.prototype.attributes;
Element.prototype.baseURIObject;
/** @type {!NodeList} */ Element.prototype.childNodes;

/**
 * @type {!NodeList}
 * @see https://developer.mozilla.org/en/DOM/element.children
 */
Element.prototype.children;

/**
 * @type {string}
 * @implicitCast
 */
Element.prototype.className;
/** @type {number} */ Element.prototype.clientHeight;
/** @type {number} */ Element.prototype.clientLeft;
/** @type {number} */ Element.prototype.clientTop;
/** @type {number} */ Element.prototype.clientWidth;
/** @type {string} */ Element.prototype.dir;

/**
 * Firebug sets this property on elements it is inserting into the DOM.
 * @type {boolean}
 */
Element.prototype.firebugIgnore;

/** @type {Node} */ Element.prototype.firstChild;
/**
 * @type {string}
 * @implicitCast
 */
Element.prototype.id;
/**
 * @type {string}
 * @implicitCast
 */
Element.prototype.innerHTML;
/** @type {string} */ Element.prototype.lang;
/** @type {Node} */ Element.prototype.lastChild;
Element.prototype.localName;
Element.prototype.name;
Element.prototype.namespaceURI;
/** @type {Node} */ Element.prototype.nextSibling;
Element.prototype.nodeName;
Element.prototype.nodePrincipal;
/** @type {number} */ Element.prototype.nodeType;
Element.prototype.nodeValue;
/** @type {number} */ Element.prototype.offsetHeight;
/** @type {number} */ Element.prototype.offsetLeft;
/** @type {Element} */ Element.prototype.offsetParent;
/** @type {number} */ Element.prototype.offsetTop;
/** @type {number} */ Element.prototype.offsetWidth;
/** @type {Document} */ Element.prototype.ownerDocument;
/** @type {Node} */ Element.prototype.parentNode;
Element.prototype.prefix;
/** @type {Node} */ Element.prototype.previousSibling;
/** @type {number} */ Element.prototype.scrollHeight;
/** @type {number} */ Element.prototype.scrollLeft;
/** @type {number} */ Element.prototype.scrollTop;
/** @type {number} */ Element.prototype.scrollWidth;
/** @type {CSSStyleDeclaration} */ Element.prototype.style;
/**
 * @type {number}
 * @implicitCast
 */
Element.prototype.tabIndex;

/**
 * @type {string}
 * @implicitCast
 */
Element.prototype.textContent;
/** @type {string} */ Element.prototype.title;

/**
 * @param {Node} child
 * @return {Node} appendedElement.
 * @override
 */
Element.prototype.appendChild = function(child) {};

/** @override */
Element.prototype.cloneNode = function(deep) {};

/** @override */
Element.prototype.dispatchEvent = function(event) {};

/** @return {undefined} */
Element.prototype.blur = function() {};

/** @return {undefined} */
Element.prototype.click = function() {};

/**
 * @return { {top: number, left: number, right: number, bottom: number} }
 * @see https://developer.mozilla.org/En/DOM:element.getBoundingClientRect
 * @nosideeffects
 */
Element.prototype.getBoundingClientRect = function() {};

/** @return {undefined} */
Element.prototype.focus = function() {};

/**
 * @return {boolean}
 * @override
 * @nosideeffects
 */
Element.prototype.hasAttributes = function() {};

/**
 * @return {boolean}
 * @override
 * @nosideeffects
 */
Element.prototype.hasChildNodes = function() {};

/** @override */
Element.prototype.insertBefore = function(insertedNode, adjacentNode) {};

/**
 * @return {undefined}
 * @override
 */
Element.prototype.normalize = function() {};

/**
 * @param {Node} removedNode
 * @return {Node}
 * @override
 */
Element.prototype.removeChild = function(removedNode) {};

/** @override */
Element.prototype.removeEventListener = function(type, handler, useCapture) {};

/** @override */
Element.prototype.replaceChild = function(insertedNode, replacedNode) {};

/**
 * @param {boolean=} opt_alignWithTop
 */
Element.prototype.scrollIntoView = function(opt_alignWithTop) {};

// Event handlers
/** @type {?function (Event)} */ Element.prototype.oncopy;
/** @type {?function (Event)} */ Element.prototype.oncut;
/** @type {?function (Event)} */ Element.prototype.onpaste;
/** @type {?function (Event)} */ Element.prototype.onbeforeunload;
/** @type {?function (Event)} */ Element.prototype.onblur;
/** @type {?function (Event)} */ Element.prototype.onchange;
/** @type {?function (Event)} */ Element.prototype.onclick;
/** @type {?function (Event)} */ Element.prototype.oncontextmenu;
/** @type {?function (Event)} */ Element.prototype.ondblclick;
/** @type {?function (Event)} */ Element.prototype.onfocus;
/** @type {?function (Event)} */ Element.prototype.onkeydown;
/** @type {?function (Event)} */ Element.prototype.onkeypress;
/** @type {?function (Event)} */ Element.prototype.onkeyup;
/** @type {?function (Event)} */ Element.prototype.onmousedown;
/** @type {?function (Event)} */ Element.prototype.onmousemove;
/** @type {?function (Event)} */ Element.prototype.onmouseout;
/** @type {?function (Event)} */ Element.prototype.onmouseover;
/** @type {?function (Event)} */ Element.prototype.onmouseup;
/** @type {?function (Event)} */ Element.prototype.onmousewheel;
/** @type {?function (Event)} */ Element.prototype.onresize;
/** @type {?function (Event)} */ Element.prototype.onscroll;

/** @type {number} */
HTMLInputElement.prototype.selectionStart;

/** @type {number} */
HTMLInputElement.prototype.selectionEnd;

/**
 * @param {number} selectionStart
 * @param {number} selectionEnd
 * @see http://www.whatwg.org/specs/web-apps/current-work/multipage/editing.html#dom-textarea/input-setselectionrange
 */
HTMLInputElement.prototype.setSelectionRange =
    function(selectionStart, selectionEnd) {};

/** @type {number} */
HTMLTextAreaElement.prototype.selectionStart;

/** @type {number} */
HTMLTextAreaElement.prototype.selectionEnd;

/**
 * @param {number} selectionStart
 * @param {number} selectionEnd
 * @see http://www.whatwg.org/specs/web-apps/current-work/multipage/editing.html#dom-textarea/input-setselectionrange
 */
HTMLTextAreaElement.prototype.setSelectionRange =
    function(selectionStart, selectionEnd) {};

/** @constructor */
function Navigator() {}

/**
 * @type {string}
 * @see https://developer.mozilla.org/en/Navigator.appCodeName
 */
Navigator.prototype.appCodeName;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en/Navigator.appVersion
 */
Navigator.prototype.appName;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en/Navigator.appVersion
 */
Navigator.prototype.appVersion;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en/Navigator.buildID
 */
Navigator.prototype.buildID;

/**
 * @type {boolean}
 * @see https://developer.mozilla.org/en/Navigator.cookieEnabled
 */
Navigator.prototype.cookieEnabled;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en/Navigator.language
 */
Navigator.prototype.language;

/**
 * @type {MimeTypeArray}
 * @see https://developer.mozilla.org/en/Navigator.mimeTypes
 */
Navigator.prototype.mimeTypes;

/**
 * @type {boolean}
 * @see https://developer.mozilla.org/en/Navigator.onLine
 */
Navigator.prototype.onLine;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en/Navigator.oscpu
 */
Navigator.prototype.oscpu;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en/Navigator.platform
 */
Navigator.prototype.platform;

/**
 * @type {PluginArray}
 * @see https://developer.mozilla.org/en/Navigator.plugins
 */
Navigator.prototype.plugins;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en/Navigator.product
 */
Navigator.prototype.product;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en/Navigator.productSub
 */
Navigator.prototype.productSub;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en/Navigator.securityPolicy
 */
Navigator.prototype.securityPolicy;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en/Navigator.userAgent
 */
Navigator.prototype.userAgent;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en/Navigator.vendor
 */
Navigator.prototype.vendor;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en/Navigator.vendorSub
 */
Navigator.prototype.vendorSub;

/**
 * @type {function(): boolean}
 * @see https://developer.mozilla.org/en/Navigator.javaEnabled
 * @nosideeffects
 */
Navigator.prototype.javaEnabled = function() {};

/**
 * @constructor
 * @see https://developer.mozilla.org/en/DOM/PluginArray
 */
function PluginArray() {}

/** @type {number} */
PluginArray.prototype.length;

/**
 * @param {number} index
 * @return {Plugin}
 */
PluginArray.prototype.item = function(index) {};

/**
 * @param {string} name
 * @return {Plugin}
 */
PluginArray.prototype.namedItem = function(name) {};

PluginArray.prototype.refresh = function() {};

/** @constructor */
function MimeTypeArray() {}

/**
 * @param {number} index
 * @return {MimeType}
 */
MimeTypeArray.prototype.item = function(index) {};

/**
 * @type {number}
 * @see https://developer.mozilla.org/en/DOM/window.navigator.mimeTypes
 */
MimeTypeArray.prototype.length;

/**
 * @param {string} name
 * @return {MimeType}
 */
MimeTypeArray.prototype.namedItem = function(name) {};

/** @constructor */
function MimeType() {}

/** @type {string} */
MimeType.prototype.description;

/** @type {Plugin} */
MimeType.prototype.enabledPlugin;

/** @type {string} */
MimeType.prototype.suffixes;

/** @type {string} */
MimeType.prototype.type;

/** @constructor */
function Plugin() {}

/** @type {string} */
Plugin.prototype.description;

/** @type {string} */
Plugin.prototype.filename;

/** @type {number} */
Plugin.prototype.length;

/** @type {string} */
Plugin.prototype.name;

/** @constructor */
function BoxObject() {}

/** @type {Element} */
BoxObject.prototype.element;

/** @type {number} */
BoxObject.prototype.screenX;

/** @type {number} */
BoxObject.prototype.screenY;

/** @type {number} */
BoxObject.prototype.x;

/** @type {number} */
BoxObject.prototype.y;

/** @type {number} */
BoxObject.prototype.width;


/**
 * @type {number}
 * @see http://www.google.com/codesearch/p?hl=en#eksvcKKj5Ng/mozilla/dom/public/idl/html/nsIDOMNSHTMLImageElement.idl&q=naturalWidth
 */
HTMLImageElement.prototype.naturalWidth;

/**
 * @type {number}
 * @see http://www.google.com/codesearch/p?hl=en#eksvcKKj5Ng/mozilla/dom/public/idl/html/nsIDOMNSHTMLImageElement.idl&q=naturalHeight
 */
HTMLImageElement.prototype.naturalHeight;
