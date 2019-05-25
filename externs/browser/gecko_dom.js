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
 * @fileoverview Definitions for all the extensions over
 *  W3C's DOM specification by Gecko. This file depends on
 *  w3c_dom2.js.
 *
 * When a non-standard extension appears in both Gecko and IE, we put
 * it in gecko_dom.js
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
 * @type {Window}
 * @see https://developer.mozilla.org/en/DOM/window.content
 */
Window.prototype.content;

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
 * @type {boolean}
 * @see https://developer.mozilla.org/en/DOM/window.fullScreen
 */
Window.prototype.fullScreen;

/**
 * @see https://developer.mozilla.org/en/DOM/Storage#globalStorage
 */
Window.prototype.globalStorage;

/** @see https://developer.mozilla.org/en/DOM/window.pkcs11 */
Window.prototype.pkcs11;

/** @see https://developer.mozilla.org/en/DOM/window */
Window.prototype.returnValue;

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

/** @see https://developer.mozilla.org/en/DOM/Storage#sessionStorage */
Window.prototype.sessionStorage;

/** @see https://developer.mozilla.org/en/DOM/window.sidebar */
Window.prototype.sidebar;

/**
 * @see https://developer.mozilla.org/en/DOM/window.back
 * @return {undefined}
 */
Window.prototype.back = function() {};

/** @deprecated */
Window.prototype.captureEvents;

/**@see https://developer.mozilla.org/en/DOM/window.find */
Window.prototype.find;

/**
 * @see https://developer.mozilla.org/en/DOM/window.forward
 * @return {undefined}
 */
Window.prototype.forward = function() {};

/**
 * @see https://developer.mozilla.org/en/DOM/window.getAttention
 * @return {undefined}
 */
Window.prototype.getAttention = function() {};

/**
 * @return {?Selection}
 * @nosideeffects
 * @see https://w3c.github.io/selection-api/#dom-window-getselection
 */
Window.prototype.getSelection = function() {};

/**
 * @see https://developer.mozilla.org/en/DOM/window.home
 * @return {undefined}
 */
Window.prototype.home = function() {};

Window.prototype.openDialog;
Window.prototype.releaseEvents;
Window.prototype.scrollByLines;
Window.prototype.scrollByPages;

/**
 * @param {string} uri
 * @param {?=} opt_arguments
 * @param {string=} opt_options
 * @see https://developer.mozilla.org/en/DOM/window.showModalDialog
 */
Window.prototype.showModalDialog;

Window.prototype.sizeToContent;

Window.prototype.updateCommands;

// properties of Document

/**
 * @see https://developer.mozilla.org/en/DOM/document.alinkColor
 * @type {string}
 */
Document.prototype.alinkColor;

/**
 * @see https://developer.mozilla.org/en/DOM/document.anchors
 * @type {HTMLCollection<!HTMLAnchorElement>}
 */
Document.prototype.anchors;

/**
 * @see https://developer.mozilla.org/en/DOM/document.applets
 * @type {HTMLCollection<!HTMLAppletElement>}
 */
Document.prototype.applets;
/** @type {boolean} */ Document.prototype.async;
/** @type {?string} */ Document.prototype.baseURI;

/**
 * @see https://developer.mozilla.org/en/DOM/document.bgColor
 * @type {string}
 */
Document.prototype.bgColor;

/** @type {HTMLBodyElement} */ Document.prototype.body;
Document.prototype.characterSet;

/**
 * @see https://developer.mozilla.org/en/DOM/document.compatMode
 * @type {string}
 */
Document.prototype.compatMode;

Document.prototype.contentType;
/** @type {string} */ Document.prototype.cookie;

/**
 * @see https://developer.mozilla.org/en-US/docs/Web/API/Document/defaultView
 * @type {?Window}
 */
Document.prototype.defaultView;

/**
 * @see https://developer.mozilla.org/en/DOM/document.designMode
 * @type {string}
 */
Document.prototype.designMode;

Document.prototype.documentURIObject;

/**
 * @see https://developer.mozilla.org/en/DOM/document.domain
 * @type {string}
 */
Document.prototype.domain;

/**
 * @see https://developer.mozilla.org/en/DOM/document.embeds
 * @type {HTMLCollection<!HTMLEmbedElement>}
 */
Document.prototype.embeds;

/**
 * @see https://developer.mozilla.org/en/DOM/document.fgColor
 * @type {string}
 */
Document.prototype.fgColor;

/** @type {Element} */ Document.prototype.firstChild;

/**
 * @see https://developer.mozilla.org/en/DOM/document.forms
 * @type {HTMLCollection<!HTMLFormElement>}
 */
Document.prototype.forms;

/** @type {number} */
Document.prototype.height;

/** @type {HTMLCollection<!HTMLImageElement>} */
Document.prototype.images;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en/DOM/document.lastModified
 */
Document.prototype.lastModified;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en/DOM/document.linkColor
 */
Document.prototype.linkColor;

/**
 * @see https://developer.mozilla.org/en/DOM/document.links
 * @type {HTMLCollection<(!HTMLAreaElement|!HTMLAnchorElement)>}
 */
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

/**
 * @type {string}
 * @see https://developer.mozilla.org/en/DOM/document.referrer
 */
Document.prototype.referrer;

/**
 * @type {StyleSheetList}
 * @see https://developer.mozilla.org/en/DOM/document.styleSheets
 */
Document.prototype.styleSheets;

/** @type {?string} */ Document.prototype.title;
Document.prototype.tooltipNode;
/** @type {string} */ Document.prototype.URL;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en/DOM/document.vlinkColor
 */
Document.prototype.vlinkColor;

/** @type {number} */ Document.prototype.width;

// Methods of Document
/**
 * @see https://developer.mozilla.org/en/DOM/document.clear
 * @return {undefined}
 */
Document.prototype.clear = function() {};

/**
 * @see https://developer.mozilla.org/en/DOM/document.close
 */
Document.prototype.close;

/**
 * @param {string} type
 * @return {Event}
 */
Document.prototype.createEvent = function(type) {};
Document.prototype.createNSResolver;
/** @return {Range} */ Document.prototype.createRange = function() {};
Document.prototype.createTreeWalker;

Document.prototype.evaluate;

/**
 * @param {string} commandName
 * @param {?boolean=} opt_showUi
 * @param {*=} opt_value
 * @see https://developer.mozilla.org/en/Rich-Text_Editing_in_Mozilla#Executing_Commands
 */
Document.prototype.execCommand;

/**
 * @param {string} uri
 * @return {undefined}
 */
Document.prototype.load = function(uri) {};
Document.prototype.loadOverlay;

/**
 * @see https://developer.mozilla.org/en/DOM/document.open
 */
Document.prototype.open;

/**
 * @see https://developer.mozilla.org/en/Midas
 * @see http://msdn.microsoft.com/en-us/library/ms536676(VS.85).aspx
 */
Document.prototype.queryCommandEnabled;

/**
 * @see https://developer.mozilla.org/en/Midas
 * @see http://msdn.microsoft.com/en-us/library/ms536678(VS.85).aspx
 */
Document.prototype.queryCommandIndeterm;

/**
 * @see https://developer.mozilla.org/en/Midas
 * @see http://msdn.microsoft.com/en-us/library/ms536679(VS.85).aspx
 */
Document.prototype.queryCommandState;

/**
 * @see https://developer.mozilla.org/en/DOM/document.queryCommandSupported
 * @see http://msdn.microsoft.com/en-us/library/ms536681(VS.85).aspx
 * @param {string} command
 * @return {?} Implementation-specific.
 */
Document.prototype.queryCommandSupported;

/**
 * @see https://developer.mozilla.org/en/Midas
 * @see http://msdn.microsoft.com/en-us/library/ms536683(VS.85).aspx
 */
Document.prototype.queryCommandValue;

/**
 * @see https://developer.mozilla.org/en/DOM/document.write
 * @param {!TrustedHTML|string} text
 * @return {undefined}
 */
Document.prototype.write = function(text) {};

/**
 * @see https://developer.mozilla.org/en/DOM/document.writeln
 * @param {!TrustedHTML|string} text
 * @return {undefined}
 */
Document.prototype.writeln = function(text) {};

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
 * @param {!TrustedHTML|string} tag
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


/**
 * @constructor
 * @see http://w3c.github.io/selection-api/#selection-interface
 */
function Selection() {}

/**
 * @type {?Node}
 * @see https://w3c.github.io/selection-api/#dom-selection-anchornode
 */
Selection.prototype.anchorNode;

/**
 * @type {number}
 * @see https://w3c.github.io/selection-api/#dom-selection-anchoroffset
 */
Selection.prototype.anchorOffset;

/**
 * @type {?Node}
 * @see https://w3c.github.io/selection-api/#dom-selection-focusnode
 */
Selection.prototype.focusNode;

/**
 * @type {number}
 * @see https://w3c.github.io/selection-api/#dom-selection-focusoffset
 */
Selection.prototype.focusOffset;

/**
 * @type {boolean}
 * @see https://w3c.github.io/selection-api/#dom-selection-iscollapsed
 */
Selection.prototype.isCollapsed;

/**
 * @type {number}
 * @see https://w3c.github.io/selection-api/#dom-selection-rangecount
 */
Selection.prototype.rangeCount;

/**
 * @param {Range} range
 * @return {undefined}
 * @see https://w3c.github.io/selection-api/#dom-selection-addrange
 */
Selection.prototype.addRange = function(range) {};

/**
 * @param {number} index
 * @return {!Range}
 * @nosideeffects
 * @see https://w3c.github.io/selection-api/#dom-selection-getrangeat
 */
Selection.prototype.getRangeAt = function(index) {};

/**
 * @param {?Node} node
 * @param {number=} offset
 * @return {undefined}
 * @see https://w3c.github.io/selection-api/#dom-selection-collapse
 */
Selection.prototype.collapse = function(node, offset) {};

/**
 * @param {?Node} node
 * @param {number=} offset
 * @return {undefined}
 * @see https://w3c.github.io/selection-api/#dom-selection-setposition
 */
Selection.prototype.setPosition = function(node, offset) {};

/**
 * @return {undefined}
 * @see https://w3c.github.io/selection-api/#dom-selection-collapsetoend
 */
Selection.prototype.collapseToEnd = function() {};

/**
 * @return {undefined}
 * @see https://w3c.github.io/selection-api/#dom-selection-collapsetostart
 */
Selection.prototype.collapseToStart = function() {};

/**
 * @param {!Node} node
 * @param {boolean=} allowPartialContainment
 * @return {boolean}
 * @nosideeffects
 * @see https://w3c.github.io/selection-api/#dom-selection-containsnode
 */
Selection.prototype.containsNode = function(node, allowPartialContainment) {};

/**
 * @return {undefined}
 * @see https://w3c.github.io/selection-api/#dom-selection-deletefromdocument
 */
Selection.prototype.deleteFromDocument = function() {};

/**
 * @param {Node} parentNode
 * @param {number=} offset
 * @return {undefined}
 * @see https://w3c.github.io/selection-api/#dom-selection-extend
 */
Selection.prototype.extend = function(parentNode, offset) {};

/**
 * @return {undefined}
 * @see https://w3c.github.io/selection-api/#dom-selection-removeallranges
 */
Selection.prototype.removeAllRanges = function() {};

/**
 * @param {!Range} range
 * @return {undefined}
 * @see https://w3c.github.io/selection-api/#dom-selection-removerange
 */
Selection.prototype.removeRange = function(range) {};

/**
 * @param {Node} parentNode
 * @see http://w3c.github.io/selection-api/#dom-selection-selectallchildren
 */
Selection.prototype.selectAllChildren;

/**
 * @see https://developer.mozilla.org/en/DOM/Selection/selectionLanguageChange
 */
Selection.prototype.selectionLanguageChange;

/**
 * @type {!NodeList<!Element>}
 * @see https://developer.mozilla.org/en/DOM/element.children
 */
Element.prototype.children;

/**
 * Firebug sets this property on elements it is inserting into the DOM.
 * @type {boolean}
 */
Element.prototype.firebugIgnore;

/**
 * Note: According to the spec, name is defined on specific types of
 * HTMLElements, rather than on Node, Element, or HTMLElement directly.
 * Ignore this.
 * @type {string}
 */
Element.prototype.name;

Element.prototype.nodePrincipal;

/**
 * @type {!CSSStyleDeclaration}
 * This belongs on HTMLElement rather than Element, but that
 * breaks a lot.
 * TODO(rdcronin): Remove this declaration once the breakage is fixed.
 */
Element.prototype.style;

/**
 * @override
 * @return {!Element}
 */
Element.prototype.cloneNode = function(deep) {};

/** @return {undefined} */
Element.prototype.blur = function() {};

/** @return {undefined} */
Element.prototype.click = function() {};

/**
 * @param {{preventScroll: boolean}=} focusOption
 * @return {undefined}
 * @see https://html.spec.whatwg.org/multipage/interaction.html#focus-management-apis
 */
Element.prototype.focus = function(focusOption) {};

/** @type {number} */
HTMLInputElement.prototype.selectionStart;

/** @type {number} */
HTMLInputElement.prototype.selectionEnd;

/**
 * @param {number} selectionStart
 * @param {number} selectionEnd
 * @see http://www.whatwg.org/specs/web-apps/current-work/multipage/editing.html#dom-textarea/input-setselectionrange
 * @return {undefined}
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
 * @return {undefined}
 */
HTMLTextAreaElement.prototype.setSelectionRange =
    function(selectionStart, selectionEnd) {};

/**
 * @type {string}
 * @see https://developer.mozilla.org/en/Navigator.buildID
 */
Navigator.prototype.buildID;

/**
 * @type {!Array<string>|undefined}
 * @see https://developer.mozilla.org/en/Navigator.languages
 */
Navigator.prototype.languages;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en/Navigator.oscpu
 */
Navigator.prototype.oscpu;

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
 * @param {string} url
 * @param {ArrayBufferView|Blob|string|FormData=} opt_data
 * @return {boolean}
 * @see https://developer.mozilla.org/en-US/docs/Web/API/navigator.sendBeacon
 */
Navigator.prototype.sendBeacon = function(url, opt_data) {};

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
 * @param {Element} element
 * @param {?string=} pseudoElt
 * @return {?CSSStyleDeclaration}
 * @nosideeffects
 * @see https://bugzilla.mozilla.org/show_bug.cgi?id=548397
 */
function getComputedStyle(element, pseudoElt) {}
