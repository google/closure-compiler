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
 * @fileoverview Definitions for all the extensions over the
 *  W3C's DOM specification by IE in JScript. This file depends on
 *  w3c_dom2.js. The whole file has NOT been fully type annotated.
 *
*
*
 */

// TODO(nicksantos): Rewrite all the DOM interfaces as interfaces, instead
// of kluding them as an inheritance hierarchy.

/**
 * @constructor
 * @extends {Document}
 * @see http://msdn.microsoft.com/en-us/library/ms757878(VS.85).aspx
 */
function XMLDOMDocument() {}

/**
 * @type {boolean}
 * @see http://msdn.microsoft.com/en-us/library/ms761398(VS.85).aspx
 */
XMLDOMDocument.prototype.async;

/**
 * @type {!Function}
 * @see http://msdn.microsoft.com/en-us/library/ms762647(VS.85).aspx
 */
XMLDOMDocument.prototype.ondataavailable;

/**
 * @type {!Function}
 * @see http://msdn.microsoft.com/en-us/library/ms764640(VS.85).aspx
 */
XMLDOMDocument.prototype.onreadystatechange;

/**
 * @type {!Function}
 * @see http://msdn.microsoft.com/en-us/library/ms753795(VS.85).aspx
 */
XMLDOMDocument.prototype.ontransformnode;

/**
 * @type {Object}
 * @see http://msdn.microsoft.com/en-us/library/ms756041(VS.85).aspx
 */
XMLDOMDocument.prototype.parseError;

/**
 * @type {boolean}
 * @see http://msdn.microsoft.com/en-us/library/ms761353(VS.85).aspx
 */
XMLDOMDocument.prototype.preserveWhiteSpace;

/**
 * @type {number}
 * @see http://msdn.microsoft.com/en-us/library/ms753702(VS.85).aspx
 */
XMLDOMDocument.prototype.readyState;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms762283(VS.85).aspx
 * @type {boolean}
 */
XMLDOMDocument.prototype.resolveExternals;

/**
 * @type {string}
 * @see http://msdn.microsoft.com/en-us/library/ms767669(VS.85).aspx
 */
XMLDOMDocument.prototype.url;

/**
 * @type {boolean}
 * @see http://msdn.microsoft.com/en-us/library/ms762791(VS.85).aspx
 */
XMLDOMDocument.prototype.validateOnParse;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms763830(VS.85).aspx
 */
XMLDOMDocument.prototype.abort = function() {};

/**
 * @param {*} type
 * @param {string} name
 * @param {string} namespaceURI
 * @return {Node}
 * @see http://msdn.microsoft.com/en-us/library/ms757901(VS.85).aspx
 */
XMLDOMDocument.prototype.createNode = function(type, name, namespaceURI) {};

/**
 * @param {string} xmlSource
 * @return {boolean}
 * @see http://msdn.microsoft.com/en-us/library/ms762722(VS.85).aspx
 */
XMLDOMDocument.prototype.load = function(xmlSource) {};

/**
 * @param {string} xmlString
 * @return {boolean}
 * @see http://msdn.microsoft.com/en-us/library/ms754585(VS.85).aspx
 */
XMLDOMDocument.prototype.loadXML = function(xmlString) {};

/**
 * @param {string} id
 * @return {Node}
 * @see http://msdn.microsoft.com/en-us/library/ms766397(VS.85).aspx
 */
XMLDOMDocument.prototype.nodeFromID = function(id) {};

//==============================================================================
// XMLNode methods and properties
// In a real DOM hierarchy, XMLDOMDocument inherits from XMLNode and Document.
// Since we can't express that in our type system, we put XMLNode properties
// on Node.

/**
 * @type {string}
 * @see http://msdn.microsoft.com/en-us/library/ms767570(VS.85).aspx
 */
Node.prototype.baseName;

/**
 * @type {?string}
 * @see http://msdn.microsoft.com/en-us/library/ms762763(VS.85).aspx
 */
Node.prototype.dataType;

/**
 * @type {Node}
 * @see http://msdn.microsoft.com/en-us/library/ms764733(VS.85).aspx
 */
Node.prototype.definition;

/**
 * IE5 used document instead of ownerDocument.
 * Old versions of WebKit used document instead of contentDocument.
 * @type {Document}
 */
Node.prototype.document;

/**
 * @type {*}
 * @see http://msdn.microsoft.com/en-us/library/ms762308(VS.85).aspx
 */
Node.prototype.nodeTypedValue;

/**
 * @type {string}
 * @see http://msdn.microsoft.com/en-us/library/ms757895(VS.85).aspx
 */
Node.prototype.nodeTypeString;

/**
 * @type {boolean}
 * @see http://msdn.microsoft.com/en-us/library/ms762237(VS.85).aspx
 */
Node.prototype.parsed;

/**
 * @type {Element}
 * @see http://msdn.microsoft.com/en-us/library/ms534327(VS.85).aspx
 */
Node.prototype.parentElement;

/**
 * @type {boolean}
 * @see http://msdn.microsoft.com/en-us/library/ms753816(VS.85).aspx
 */
Node.prototype.specified;

/**
 * @type {string}
 * @see http://msdn.microsoft.com/en-us/library/ms762687(VS.85).aspx
 */
Node.prototype.text;

/**
 * @type {string}
 * @see http://msdn.microsoft.com/en-us/library/ms755989(VS.85).aspx
 */
Node.prototype.xml;

/**
 * @param {string} expression An XPath expression.
 * @return {NodeList}
 * @see http://msdn.microsoft.com/en-us/library/ms754523(VS.85).aspx
 */
Node.prototype.selectNodes = function(expression) {};

/**
 * @param {string} expression An XPath expression.
 * @return {Node}
 * @see http://msdn.microsoft.com/en-us/library/ms757846(VS.85).aspx
 */
Node.prototype.selectSingleNode = function(expression) {};

/**
 * @param {Node} stylesheet XSLT stylesheet.
 * @return {string}
 * @see http://msdn.microsoft.com/en-us/library/ms761399(VS.85).aspx
 */
Node.prototype.transformNode = function(stylesheet) {};

/**
 * @param {Node} stylesheet XSLT stylesheet.
 * @param {Object} outputObject
 * @see http://msdn.microsoft.com/en-us/library/ms766561(VS.85).aspx
 */
Node.prototype.transformNodeToObject =
    function(stylesheet, outputObject) {};

//==============================================================================
// Node methods

/**
 * @param {boolean=} opt_bRemoveChildren Whether to remove the entire sub-tree.
 *    Defaults to false.
 * @return {Node} The object that was removed.
 * @see http://msdn.microsoft.com/en-us/library/ms536708(VS.85).aspx
 */
Node.prototype.removeNode = function(opt_bRemoveChildren) {};

/**
 * @constructor
 */
function ClipboardData() {}

/**
 * @see http://msdn.microsoft.com/en-us/library/ms535220(VS.85).aspx
 * @param {string} type Type of clipboard data to clear. 'Text' or
 *     'URL' or 'File' or 'HTML' or 'Image'.
 */
ClipboardData.prototype.clearData = function(type) {};

/**
 * @see http://msdn.microsoft.com/en-us/library/ms535220(VS.85).aspx
 * @param {string} type Type of clipboard data to set ('Text' or 'URL').
 * @param {string} data Data to set
 * @return {boolean} Whether the data were set correctly.
 */
ClipboardData.prototype.setData = function(type, data) {};

/**
 * @see http://msdn.microsoft.com/en-us/library/ms535220(VS.85).aspx
 * @param {string} type Type of clipboard data to get ('Text' or 'URL').
 * @return {string} The current data
 */
ClipboardData.prototype.getData = function(type) { };

/**
 * @constructor
 * @extends {EventTarget}
 */
// NOTE(nicksantos): Technically, this doesn't extend EventTarget into HTML5.
// But there's no easy way to express this and still maintain separate files.
function Window() {}

/**
 * @type {Window}
 * @see https://developer.mozilla.org/en/DOM/window
 */
var window;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms535220(VS.85).aspx
 * @type ClipboardData
 */
Window.prototype.clipboardData;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533574(VS.85).aspx
 */
Window.prototype.closed;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533717(VS.85).aspx
 */
Window.prototype.defaultStatus;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533723(VS.85).aspx
 */
Window.prototype.dialogArguments;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533724(VS.85).aspx
 */
Window.prototype.dialogHeight;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533725(VS.85).aspx
 */
Window.prototype.dialogLeft;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533726(VS.85).aspx
 */
Window.prototype.dialogTop;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533727(VS.85).aspx
 */
Window.prototype.dialogWidth;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533738(VS.85).aspx
 */
Window.prototype.document;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms535863(VS.85).aspx
 */
Window.prototype.event;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533771(VS.85).aspx
 */
Window.prototype.frameElement;

/**
 * @see https://developer.mozilla.org/en/DOM/Storage#globalStorage
 */
Window.prototype.globalStorage;

/**
 * @see https://developer.mozilla.org/en/DOM/window.length
 */
Window.prototype.length;

/**
 * @see http://msdn.microsoft.com/en-us/library/cc197012(VS.85).aspx
 */
Window.prototype.maxConnectionsPer1_0Server;

/**
 * @see http://msdn.microsoft.com/en-us/library/cc197013(VS.85).aspx
 */
Window.prototype.maxConnectionsPerServer;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534187(VS.85).aspx
 */
Window.prototype.name;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534198(VS.85).aspx
 */
Window.prototype.offscreenBuffering;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534309(VS.85).aspx
 */
Window.prototype.opener;

/**
 * @see https://developer.mozilla.org/en/DOM/window.outerHeight
 */
Window.prototype.outerHeight;

/**
 * @see https://developer.mozilla.org/en/DOM/window.outerWidth
 */
Window.prototype.outerWidth;

/**
 * @see https://developer.mozilla.org/en/DOM/window.scrollX
 */
Window.prototype.pageXOffset;

/**
 * @see https://developer.mozilla.org/en/DOM/window.scrollY
 */
Window.prototype.pageYOffset;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534326(VS.85).aspx
 */
Window.prototype.parent;

/**
 * @see https://developer.mozilla.org/en/DOM/window.personalbar
 */
Window.prototype.personalbar;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534326(VS.85).aspx
 */
Window.prototype.returnValue;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534389(VS.85).aspx
 */
Window.prototype.screenLeft;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534389(VS.85).aspx
 */
Window.prototype.screenTop;

/**
 * @see https://developer.mozilla.org/en/DOM/window.scrollbars
 */
Window.prototype.scrollbars;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534627(VS.85).aspx
 */
Window.prototype.self;

/**
 * @see http://msdn.microsoft.com/en-us/library/cc197020(VS.85).aspx
 */
Window.prototype.sessionStorage;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534648(VS.85).aspx
 */
Window.prototype.status;

/**
 * @see https://developer.mozilla.org/en/DOM/window.statusbar
 */
Window.prototype.statusbar;

/**
 * @see https://developer.mozilla.org/en/DOM/window.toolbar
 */
Window.prototype.toolbar;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534687(VS.85).aspx
 */
Window.prototype.top;

/**
 * @see http://msdn.microsoft.com/en-us/library/cc287985(VS.85).aspx
 */
Window.prototype.XDomainRequest;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms535157(VS.85).aspx
 */
Window.prototype.XMLHttpRequest;

// Functions

/**
 * @see http://msdn.microsoft.com/en-us/library/ms535933(VS.85).aspx
 */
Window.prototype.alert;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms535933(VS.85).aspx
 */
Window.prototype.attachEvent;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536347(VS.85).aspx
 */
Window.prototype.blur;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536353(VS.85).aspx
 */
Window.prototype.clearInterval;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536357(VS.85).aspx
 */
Window.prototype.clearTimeout;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536367(VS.85).aspx
 */
Window.prototype.close;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536376(VS.85).aspx
 */
Window.prototype.confirm;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536392(VS.85).aspx
 */
Window.prototype.createPopup;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536411(VS.85).aspx
 */
Window.prototype.detachEvent;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536420(VS.85).aspx
 */
Window.prototype.execScript;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536425(VS.85).aspx
 */
Window.prototype.focus;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536618(VS.85).aspx
 */
Window.prototype.moveBy;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536626(VS.85).aspx
 */
Window.prototype.moveTo;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536638(VS.85).aspx
 */
Window.prototype.navigate;

/**
 * @param {*=} opt_url
 * @param {string=} opt_windowName
 * @param {string=} opt_windowFeatures
 * @param {boolean=} opt_replace
 * @return {Window}
 * @see http://msdn.microsoft.com/en-us/library/ms536651(VS.85).aspx
 */
Window.prototype.open = function(opt_url, opt_windowName, opt_windowFeatures,
                                 opt_replace) {};

/**
 * @see http://msdn.microsoft.com/en-us/library/cc197015(VS.85).aspx
 */
Window.prototype.postMessage;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536672(VS.85).aspx
 */
Window.prototype.print;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536673(VS.85).aspx
 */
Window.prototype.prompt;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536722(VS.85).aspx
 */
Window.prototype.resizeBy;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536723(VS.85).aspx
 */
Window.prototype.resizeTo;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536726(VS.85).aspx
 */
Window.prototype.scroll;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536728(VS.85).aspx
 */
Window.prototype.scrollBy;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536731(VS.85).aspx
 */
Window.prototype.scrollTo;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536738(VS.85).aspx
 */
Window.prototype.setActive;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536749(VS.85).aspx
 */
Window.prototype.setInterval;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536753(VS.85).aspx
 */
Window.prototype.setTimeout;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536758(VS.85).aspx
 */
Window.prototype.showHelp;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536759(VS.85).aspx
 */
Window.prototype.showModalDialog;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536761(VS.85).aspx
 */
Window.prototype.showModelessDialog;

/**
 * @constructor
 */
function History() { };

/**
 * @see http://msdn.microsoft.com/en-us/library/ms535864(VS.85).aspx
 * @param {number|string} delta The number of entries to go back, or
 *     the URL to which to go back. (URL form is supported only in IE)
 */
History.prototype.go = function(delta) {};

/**
 * @see http://msdn.microsoft.com/en-us/library/ms535864(VS.85).aspx
 * @param {number=} opt_distance The number of entries to go back
 *     (Mozilla doesn't support distance -- use #go instead)
 */
History.prototype.back = function(opt_distance) {};

/**
 * @see http://msdn.microsoft.com/en-us/library/ms535864(VS.85).aspx
 * @type {number}
 */
History.prototype.length;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms535864(VS.85).aspx
 */
History.prototype.forward = function() {};

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533692(VS.85).aspx
 */
HTMLFrameElement.prototype.contentWindow;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533692(VS.85).aspx
 */
HTMLIFrameElement.prototype.contentWindow;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536385(VS.85).aspx
 */
HTMLBodyElement.prototype.createControlRange;

/**
 * @constructor
 */
function ControlRange() {}

ControlRange.prototype.add;
ControlRange.prototype.addElement;
ControlRange.prototype.execCommand;
ControlRange.prototype.item;
ControlRange.prototype.queryCommandEnabled;
ControlRange.prototype.queryCommandIndeterm;
ControlRange.prototype.queryCommandState;
ControlRange.prototype.queryCommandSupported;
ControlRange.prototype.queryCommandValue;
ControlRange.prototype.remove;
ControlRange.prototype.scrollIntoView;
ControlRange.prototype.select;

/**
 * @constructor
 * @see http://msdn.microsoft.com/en-us/library/ms535872.aspx
 */
function TextRange() {}

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533538(VS.85).aspx
 */
TextRange.prototype.boundingHeight;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533539(VS.85).aspx
 */
TextRange.prototype.boundingLeft;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533540(VS.85).aspx
 */
TextRange.prototype.boundingTop;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533541(VS.85).aspx
 */
TextRange.prototype.boundingWidth;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533874(VS.85).aspx
 */
TextRange.prototype.htmlText;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534200(VS.85).aspx
 */
TextRange.prototype.offsetLeft;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534303(VS.85).aspx
 */
TextRange.prototype.offsetTop;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534676(VS.85).aspx
 */
TextRange.prototype.text;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536371(VS.85).aspx
 */
TextRange.prototype.collapse;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536373(VS.85).aspx
 */
TextRange.prototype.compareEndPoints;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536416(VS.85).aspx
 */
TextRange.prototype.duplicate;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536419(VS.85).aspx
 */
TextRange.prototype.execCommand;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536421(VS.85).aspx
 */
TextRange.prototype.expand;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536422(VS.85).aspx
 */
TextRange.prototype.findText;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536432(VS.85).aspx
 */
TextRange.prototype.getBookmark;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536433(VS.85).aspx
 */
TextRange.prototype.getBoundingClientRect;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536435(VS.85).aspx
 */
TextRange.prototype.getClientRects;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536450(VS.85).aspx
 */
TextRange.prototype.inRange;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536458(VS.85).aspx
 */
TextRange.prototype.isEqual;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536616(VS.85).aspx
 */
TextRange.prototype.move;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536620(VS.85).aspx
 */
TextRange.prototype.moveEnd;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536623(VS.85).aspx
 */
TextRange.prototype.moveStart;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536628(VS.85).aspx
 */
TextRange.prototype.moveToBookmark;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536630(VS.85).aspx
 */
TextRange.prototype.moveToElementText;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536632(VS.85).aspx
 */
TextRange.prototype.moveToPoint;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536654(VS.85).aspx
 */
TextRange.prototype.parentElement;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536656(VS.85).aspx
 */
TextRange.prototype.pasteHTML;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536676(VS.85).aspx
 */
TextRange.prototype.queryCommandEnabled;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536678(VS.85).aspx
 */
TextRange.prototype.queryCommandIndeterm;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536679(VS.85).aspx
 */
TextRange.prototype.queryCommandState;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536681(VS.85).aspx
 */
TextRange.prototype.queryCommandSupported;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536683(VS.85).aspx
 */
TextRange.prototype.queryCommandValue;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536730(VS.85).aspx
 */
TextRange.prototype.scrollIntoView;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536735(VS.85).aspx
 */
TextRange.prototype.select;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536745(VS.85).aspx
 */
TextRange.prototype.setEndPoint;

/**
 * @return {undefined}
 * @see http://msdn.microsoft.com/en-us/library/ms536418(VS.85).aspx
 */
Selection.prototype.clear = function() {};

/**
 * @return {TextRange|ControlRange}
 * @see http://msdn.microsoft.com/en-us/library/ms536394(VS.85).aspx
 */
Selection.prototype.createRange = function() {};

/**
 * @return {Array.<TextRange>}
 * @see http://msdn.microsoft.com/en-us/library/ms536396(VS.85).aspx
 */
Selection.prototype.createRangeCollection = function() {};

/**
 * @constructor
 * @see http://msdn.microsoft.com/en-us/library/ms537447(VS.85).aspx
 */
function controlRange() {}


Document.prototype.loadXML;


// http://msdn.microsoft.com/en-us/library/ms531073(VS.85).aspx

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533065(VS.85).aspx
 */
Document.prototype.activeElement;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533071(VS.85).aspx
 */
Document.prototype.alinkColor;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533506(VS.85).aspx
 */
Document.prototype.bgColor;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533553(VS.85).aspx
 */
Document.prototype.charset;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533687(VS.85).aspx
 */
Document.prototype.compatMode;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533693(VS.85).aspx
 */
Document.prototype.cookie;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533714(VS.85).aspx
 */
Document.prototype.defaultCharset;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533720(VS.85).aspx
 */
Document.prototype.designMode;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533731(VS.85).aspx
 */
Document.prototype.dir;

/**
 * @see http://msdn.microsoft.com/en-us/library/cc196988(VS.85).aspx
 */
Document.prototype.documentMode;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533740(VS.85).aspx
 */
Document.prototype.domain;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533747(VS.85).aspx
 */
Document.prototype.expando;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533749(VS.85).aspx
 */
Document.prototype.fgColor;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533750(VS.85).aspx
 */
Document.prototype.fileCreatedDate;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533751(VS.85).aspx
 */
Document.prototype.fileModifiedDate;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533752(VS.85).aspx
 */
Document.prototype.fileSize;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533884(VS.85).aspx
 */
Document.prototype.implementation;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533946(VS.85).aspx
 */
Document.prototype.lastModified;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534117(VS.85).aspx
 */
Document.prototype.linkColor;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534331(VS.85).aspx
 */
Document.prototype.parentWindow;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534353(VS.85).aspx
 */
Document.prototype.protocol;

/**
 * @type {string}
 * @see http://msdn.microsoft.com/en-us/library/ms534359(VS.85).aspx
 */
Document.prototype.readyState;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534365(VS.85).aspx
 */
Document.prototype.referrer;

/**
 * @type {Selection}
 * @see http://msdn.microsoft.com/en-us/library/ms535869(VS.85).aspx
 */
Document.prototype.selection;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534704(VS.85).aspx
 */
Document.prototype.uniqueID;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534708(VS.85).aspx
 */
Document.prototype.URL;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534709(VS.85).aspx
 */
Document.prototype.URLUnencoded;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms535139(VS.85).aspx
 */
Document.prototype.vlinkColor;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms535155(VS.85).aspx
 */
Document.prototype.XMLDocument;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms535163(VS.85).aspx
 */
Document.prototype.XSLDocument;

// functions

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536343(VS.85).aspx
 */
Document.prototype.attachEvent;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536361(VS.85).aspx
 */
Document.prototype.clear;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536369(VS.85).aspx
 */
Document.prototype.close;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536379(VS.85).aspx
 */
Document.prototype.createAttribute;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536383(VS.85).aspx
 */
Document.prototype.createComment;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536387(VS.85).aspx
 */
Document.prototype.createDocumentFragment;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536389(VS.85).aspx
 */
Document.prototype.createElement;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536390(VS.85).aspx
 */
Document.prototype.createEventObject;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms531194(VS.85).aspx
 */
Document.prototype.createStyleSheet;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536400(VS.85).aspx
 */
Document.prototype.createTextNode;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536411(VS.85).aspx
 */
Document.prototype.detachEvent;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536417(VS.85).aspx
 */
Document.prototype.elementFromPoint;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536419(VS.85).aspx
 */
Document.prototype.execCommand;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536425(VS.85).aspx
 */
Document.prototype.focus;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536437(VS.85).aspx
 */
Document.prototype.getElementById;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536438(VS.85).aspx
 */
Document.prototype.getElementsByName;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536439(VS.85).aspx
 */
Document.prototype.getElementsByTagName;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536447(VS.85).aspx
 * @return {boolean}
 */
Document.prototype.hasFocus = function() {};

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536614(VS.85).aspx
 */
Document.prototype.mergeAttributes;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536652(VS.85).aspx
 */
Document.prototype.open;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536676(VS.85).aspx
 */
Document.prototype.queryCommandEnabled;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536678(VS.85).aspx
 */
Document.prototype.queryCommandIndeterm;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536679(VS.85).aspx
 */
Document.prototype.queryCommandState;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536681(VS.85).aspx
 */
Document.prototype.queryCommandSupported;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536683(VS.85).aspx
 */
Document.prototype.queryCommandValue;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536685(VS.85).aspx
 */
Document.prototype.recalc;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536689(VS.85).aspx
 */
Document.prototype.releaseCapture;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536738(VS.85).aspx
 */
Document.prototype.setActive;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536782(VS.85).aspx
 */
Document.prototype.write;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536783(VS.85).aspx
 */
Document.prototype.writeln;


// collections

/**
 * @see http://msdn.microsoft.com/en-us/library/ms537434(VS.85).aspx
 */
Document.prototype.all;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms537435(VS.85).aspx
 */
Document.prototype.anchors;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms537436(VS.85).aspx
 */
Document.prototype.applets;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms537445(VS.85).aspx
 */
Document.prototype.childNodes;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms537445(VS.85).aspx
 */
Document.prototype.embeds;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms537457(VS.85).aspx
 */
Document.prototype.forms;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms537459(VS.85).aspx
 */
Document.prototype.frames;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms537461(VS.85).aspx
 */
Document.prototype.images;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms537465(VS.85).aspx
 */
Document.prototype.links;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms537470(VS.85).aspx
 */
Document.prototype.namespaces;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms537487(VS.85).aspx
 */
Document.prototype.scripts;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms531200(VS.85).aspx
 */
Document.prototype.styleSheets;

/**
 * @type {boolean}
 * @see http://msdn.microsoft.com/en-us/library/ms533546(VS.85).aspx
 */
Element.prototype.canHaveChildren;

/**
 * @param {number} iCoordX Integer that specifies the client window coordinate
 *     of x.
 * @param {number} iCoordY Integer that specifies the client window coordinate
 *     of y.
 * @return {string} The component of an element located at the specified
 *     coordinates.
 * @see http://msdn.microsoft.com/en-us/library/ms536375(VS.85).aspx
 */
Element.prototype.componentFromPoint = function(iCoordX, iCoordY) {};

/**
 * @param {Element} el The element to check
 * @return {boolean} If the element is contained within this one.
 * @see http://msdn.microsoft.com/en-us/library/ms536377(VS.85).aspx
 */
Element.prototype.contains = function(el) {};

/**
 * @type {boolean}
 * @see http://msdn.microsoft.com/en-us/library/ms533690(VS.85).aspx
 */
Element.prototype.contentEditable;

/**
 * @return {TextRange}
 * @see http://msdn.microsoft.com/en-us/library/ms536401(VS.85).aspx
 */
Element.prototype.createTextRange;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms535231(VS.85).aspx
 */
Element.prototype.currentStyle;

/**
 * @param {string} opt_action
 * @see http://msdn.microsoft.com/en-us/library/ms536414%28VS.85%29.aspx
 */
Element.prototype.doScroll = function(opt_action) {};

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536423(VS.85).aspx
 */
Element.prototype.fireEvent;

/**
 * @type {boolean}
 * @see http://msdn.microsoft.com/en-us/library/ms533783(VS.85).aspx
 */
Element.prototype.hideFocus;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533899.aspx
 */
Element.prototype.innerText;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms537838(VS.85).aspx
 */
Element.prototype.isContentEditable;

/**
 * @see http://msdn.microsoft.com/en-us/library/aa752326(VS.85).aspx
 */
Element.prototype.outerHTML;

/**
 * @type {string}
 * @see http://msdn.microsoft.com/en-us/library/ms534359(VS.85).aspx
 */
Element.prototype.readyState;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536689(VS.85).aspx
 */
Element.prototype.releaseCapture = function() {};

/**
 * @see http://msdn.microsoft.com/en-us/library/aa703996(VS.85).aspx
 */
Element.prototype.runtimeStyle;

/**
 * @param {boolean=} opt_bContainerCapture Events originating in a container are
 *     captured by the container. Defaults to true.
 * @see http://msdn.microsoft.com/en-us/library/ms536742(VS.85).aspx
 */
Element.prototype.setCapture = function(opt_bContainerCapture) {};

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534635(VS.85).aspx
 */
Element.prototype.sourceIndex;

/**
 * @type {string}
 * @see http://msdn.microsoft.com/en-us/library/ms537840.aspx
 */
Element.prototype.unselectable;

/**
 * @constructor
 * @see http://msdn.microsoft.com/en-us/library/ms535866(VS.85).aspx
 */
function Location() {}

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533775(VS.85).aspx
 */
Location.prototype.hash;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533784(VS.85).aspx
 */
Location.prototype.host;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533785(VS.85).aspx
 */
Location.prototype.hostname;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms533867(VS.85).aspx
 */
Location.prototype.href;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534332(VS.85).aspx
 */
Location.prototype.pathname;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534342(VS.85).aspx
 */
Location.prototype.port;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534353(VS.85).aspx
 */
Location.prototype.protocol;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms534620(VS.85).aspx
 */
Location.prototype.search;

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536342(VS.85).aspx
 */
Location.prototype.assign;

/**
 * @param opt_forceReload If true, reloads the page from the server. Defaults
 *     to false.
 * @see http://msdn.microsoft.com/en-us/library/ms536691(VS.85).aspx
 */
Location.prototype.reload = function(opt_forceReload) {};

/**
 * @see http://msdn.microsoft.com/en-us/library/ms536712(VS.85).aspx
 */
Location.prototype.replace;


// For IE, returns an object representing key-value pairs for all the global
// variables prefixed with str, e.g. test*

function RuntimeObject(opt_str) {}


/**
 * @type {StyleSheet}
 * @see http://msdn.microsoft.com/en-us/library/dd347030(VS.85).aspx
 */
HTMLStyleElement.prototype.styleSheet;
