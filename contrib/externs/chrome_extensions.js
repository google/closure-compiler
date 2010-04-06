/*
 * Copyright 2009 Google Inc.
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
 * @fileoverview Definitions for the Chromium extensions API.
 * @externs
 *
*
*
 */

/** namespace */
var chrome = {};

/** @see http://code.google.com/chrome/extensions/extension.html */
chrome.extension = {};

/**
 * @param {string|Object.<string>=} opt_arg1 Either the extensionId to
 *     to connect to, in which case connectInfo params can be passed in the
 *     next optional argument, or the connectInfo params.
 * @param {Object.<string>=} opt_connectInfo The connectInfo object,
 *     if arg1 was the extensionId to connect to.
 * @return {Port} New port.
 */
chrome.extension.connect = function(opt_arg1, opt_connectInfo) {};

/**
 * @return {Window} The global JS object for the background page.
 */
chrome.extension.getBackgroundPage = function() {};

/**
 * @param {number} opt_windowId An optional windowId.
 * @return {Array.<Window>} The global JS objects for each content view.
 */
chrome.extension.getExtensionTabs = function(opt_windowId) {};

/**
 * @param {string} path A path to a resource within an extension expressed
 *     relative to it's install directory.
 * @return {string} The fully-qualified URL to the resource.
 */
chrome.extension.getURL = function(path) {};

/**
 * @return {Array.<Window>} The global JS objects for each content view.
 */
chrome.extension.getViews = function() {};

/**
 * @param {number|*} opt_arg1 Either the extensionId to send the request to,
 *     in which case the request is passed as the next arg, or the request.
 * @param {*} opt_request The request value, if arg1 was the extensionId.
 * @param {function(*) : void=} opt_callback The callback function which
 *     takes a JSON response object sent by the handler of the request.
 */
chrome.extension.sendRequest = function(opt_arg1, opt_request, opt_callback) {};

/** @type {ChromeEvent} */
chrome.extension.onConnect;
/** @type {ChromeEvent} */
chrome.extension.onConnectExternal;
/** @type {ChromeEvent} */
chrome.extension.onRequest;
/** @type {ChromeEvent} */
chrome.extension.onRequestExternal;


/** @see http://code.google.com/chrome/extensions/tabs.html */
chrome.tabs = {};

/**
 * @param {number?} windowId Window Id.
 * @param {function(string) : void} callback Callback function which accepts
 *    the data URL string of a JPEG encoding of the visible area of the
 *    captured tab. May be assigned to the 'src' property of an HTML Image
 *    element for display.
 */
chrome.tabs.captureVisibleTab = function(windowId, callback) {};

/**
 * @param {number} tabId Tab Id.
 * @param {Object.<string>=} opt_connectInfo Info Object.
 */
chrome.tabs.connect = function(tabId, opt_connectInfo) {};

/**
 * @param {Object} createProperties Info object.
 * @param {function(Tab) : void=} opt_callback The callback function.
 */
chrome.tabs.create = function(createProperties, opt_callback) {};

/**
 * @param {number?} tabId Tab id.
 * @param {function(string) : void} callback Callback function.
 */
chrome.tabs.detectLanguage = function(tabId, callback) {};

/**
 * @param {number?} tabId Tab id.
 * @param {Object?} details An object which may have 'code', 'file',
 *    or 'allFrames' keys.
 * @param {function() : void=} opt_callback Callback function.
 */
chrome.tabs.executeScript = function(tabId, details, opt_callback) {};

/**
 * @param {number} tabId Tab id.
 * @param {function(Array.<Tab>) : void} callback Callback.
 */
chrome.tabs.get = function(tabId, callback) {};

/**
 * @param {number?} windowId Window id.
 * @param {function(Array.<Tab>) : void} callback Callback.
 */
chrome.tabs.getAllInWindow = function(windowId, callback) {};

/**
 * @param {number?} windowId Window id.
 * @param {function(Tab) : void} callback Callback.
 */
chrome.tabs.getSelected = function(windowId, callback) {};

/**
 * @param {number?} tabId Tab id.
 * @param {Object?} details An object which may have 'code', 'file',
 *     or 'allFrames' keys.
 * @param {function() : void=} opt_callback Callback function.
 */
chrome.tabs.insertCSS = function(tabId, details, opt_callback) {};

/**
 * @param {number} tabId Tab id.
 * @param {Object.<string, number>} moveProperties An object with 'index'
 *     and optional 'windowId' keys.
 * @param {function(Tab) : void} opt_callback Callback.
 */
chrome.tabs.move = function(tabId, moveProperties, opt_callback) {};

/**
 * @param {number} tabId Tab id.
 * @param {function(Tab) : void} opt_callback Callback.
 */
chrome.tabs.remove = function(tabId, opt_callback) {};

/**
 * @param {number} tabId Tab id.
 * @param {*} request The request value of any type.
 * @param {function(*) : void=} opt_callback The callback function which
 *     takes a JSON response object sent by the handler of the request.
 */
chrome.tabs.sendRequest = function(tabId, request, opt_callback) {};

/**
 * @param {number} tabId Tab id.
 * @param {Object.<string, (string|boolean)>} updateProperties An object which
 *     may have 'url' or 'selected' key.
 * @param {function(Tab) : void} opt_callback Callback.
 */
chrome.tabs.update = function(tabId, updateProperties, opt_callback) {};

/** @type {ChromeEvent} */
chrome.tabs.onAttached;
/** @type {ChromeEvent} */
chrome.tabs.onCreated;
/** @type {ChromeEvent} */
chrome.tabs.onDetached;
/** @type {ChromeEvent} */
chrome.tabs.onMoved;
/** @type {ChromeEvent} */
chrome.tabs.onRemoved;
/** @type {ChromeEvent} */
chrome.tabs.onSelectionChanged;
/** @type {ChromeEvent} */
chrome.tabs.onUpdated;


/** @see http://code.google.com/chrome/extensions/windows.html */
chrome.windows = {};

/**
 * @param {Object?} createData May have 'url', 'left', 'top',
 *     'width', or 'height' properties.
 * @param {function(ChromeWindow) : void=} opt_callback Callback.
 */
chrome.windows.create = function(createData, opt_callback) {};

/**
 * @param {Object.<string, boolean>?} getInfo May have 'populate' key.
 * @param {function(Array.<ChromeWindow>): void} callback Callback.
 */
chrome.windows.getAll = function(getInfo, callback) {};

/**
 * @param {function(ChromeWindow) : void} callback Callback.
 */
chrome.windows.getCurrent = function(callback) {};

/**
 * @param {function(ChromeWindow) : void} callback Callback.
 */
chrome.windows.getLastFocused = function(callback) {};

/**
 * @param {number} tabId Tab Id.
 * @param {function() : void} opt_callback Callback.
 */
chrome.windows.remove = function(tabId, opt_callback) {};

/**
 * @param {number} tabId Tab Id.
 * @param {Object.<string, number>} updateProperties An object which may
 *     have 'left', 'top', 'width', or 'height' keys.
 * @param {function() : void} opt_callback Callback.
 */
chrome.windows.update = function(tabId, updateProperties, opt_callback) {};

/** @type {ChromeEvent} */
chrome.windows.onCreated;
/** @type {ChromeEvent} */
chrome.windows.onFocusChanged;
/** @type {ChromeEvent} */
chrome.windows.onRemoved;

/** @see http://code.google.com/chrome/extensions/i18n.html */
chrome.i18n = {};

/**
 * @param {function(Array.<string>) : void} callback The callback function which
 *     accepts an array of the accept languages of the browser, such as
 *     'en-US','en','zh-CN'.
 */
chrome.i18n.getAcceptLanguages = function(callback) {};

/**
 * @param {string} messageName
 * @param {(string|Array.<string>)=} opt_args
 * @return {string}
 */
chrome.i18n.getMessage = function(messageName, opt_args) {};



/** @see http://code.google.com/chrome/extensions/pageAction.html */
chrome.pageAction = {};

/**
 * @param {number} tabId Tab Id.
 */
chrome.pageAction.hide = function(tabId) {};

/**
 * @param {number} tabId Tab Id.
 */
chrome.pageAction.show = function(tabId) {};

/**
 * @param {Object} details An object which has 'tabId' and either
 *     'imageData' or 'path'.
 */
chrome.pageAction.setIcon = function(details) {};

/**
 * @param {Object} details An object which has 'tabId' and 'title'.
 */
chrome.pageAction.setTitle = function(details) {};

/** @type {ChromeEvent} */
chrome.pageAction.onClicked;


/** @see http://code.google.com/chrome/extensions/browserAction.html */
chrome.browserAction = {};

/**
 * @param {Object} details An object whose keys are 'color' and
 *     optionally 'tabId'.
 */
chrome.browserAction.setBadgeBackgroundColor = function(details) {};

/**
 * @param {Object} details An object whose keys are 'text' and
 *     optionally 'tabId'.
 */
chrome.browserAction.setBadgeText = function(details) {};

/**
 * @param {Object} details An object which may have 'imageData',
 *     'path', or 'tabId' as keys.
 */
chrome.browserAction.setIcon = function(details) {};

/**
 * @param {Object} details An object which has 'title' and optionally
 *     'tabId'.
 */
chrome.browserAction.setTitle = function(details) {};

/** @type {ChromeEvent} */
chrome.browserAction.onClicked;


/** @see http://code.google.com/chrome/extensions/bookmarks.html */
chrome.bookmarks = {};

/**
 * @param {Object} bookmark An object which has 'parentId' and
 *     optionally 'index', 'title', and 'url'.
 * @param {function(BookmarkTreeNode) : void=} opt_callback The
 *     callback function which accepts a BookmarkTreeNode object.
 * @return {BookmarkTreeNode}
 */
chrome.bookmarks.create = function(bookmark, opt_callback) {};

/**
 * @param {(string|Array.<string>)} idOrIdList
 * @param {function(Array.<BookmarkTreeNode>) : void} callback The
 *     callback function which accepts an array of BookmarkTreeNode.
 * @return {Array.<BookmarkTreeNode>}
 */
chrome.bookmarks.get = function(idOrIdList, callback) {};

/**
 * @param {string} id
 * @param {function(Array.<BookmarkTreeNode>) : void} callback The
 *     callback function which accepts an array of BookmarkTreeNode.
 * @return {Array.<BookmarkTreeNode>}
 */
chrome.bookmarks.getChildren = function(id, callback) {};

/**
 * @param {function(Array.<BookmarkTreeNode>) : void} callback The
 *     callback function which accepts an array of BookmarkTreeNode.
 * @return {Array.<BookmarkTreeNode>}
 */
chrome.bookmarks.getTree = function(callback) {};

/**
 * @param {string} id
 * @param {Object} destination An object which has 'parentId' and
 *     optionally 'index'.
 * @param {function(Array.<BookmarkTreeNode>) : void=} opt_callback
 *     The callback function which accepts an array of
 *     BookmarkTreeNode.
 * @return {BookmarkTreeNode}
 */
chrome.bookmarks.move = function(id, destination, opt_callback) {};

/**
 * @param {string} id
 * @param {function() : void=} opt_callback
 */
chrome.bookmarks.remove = function(id, opt_callback) {};

/**
 * @param {string} id
 * @param {function() : void=} opt_callback
 */
chrome.bookmarks.removeTree = function(id, opt_callback) {};

/**
 * @param {string} query
 * @param {function(Array.<BookmarkTreeNode>) : void} callback
 * @return {Array.<BookmarkTreeNode>}
 */
chrome.bookmarks.search = function(query, callback) {};

/**
 * @param {string} id
 * @param {Object} changes An object which may have 'title' as a key.
 * @param {function(BookmarkTreeNode) : void=} opt_callback The
 *     callback function which accepts a BookmarkTreeNode object.
 * @return {BookmarkTreeNode}
 */
chrome.bookmarks.update = function(id, changes, opt_callback) {};

/** @type {ChromeEvent} */
chrome.bookmarks.onChanged;
/** @type {ChromeEvent} */
chrome.bookmarks.onChildrenReordered;
/** @type {ChromeEvent} */
chrome.bookmarks.onCreated;
/** @type {ChromeEvent} */
chrome.bookmarks.onMoved;
/** @type {ChromeEvent} */
chrome.bookmarks.onRemoved;


// Classes

/**
 * @see http://code.google.com/chrome/extensions/tabs.html
 * @constructor
 */
function Tab() {}
/** @type {number} */
Tab.prototype.id;
/** @type {number} */
Tab.prototype.index;
/** @type {number} */
Tab.prototype.windowId;
/** @type {boolean} */
Tab.prototype.selected;
/** @type {string} */
Tab.prototype.url;
/** @type {string} */
Tab.prototype.title;
/** @type {string} */
Tab.prototype.favIconUrl;
/** @type {string} */
Tab.prototype.status;

/**
 * @see http://code.google.com/chrome/extensions/windows.html
 * @constructor
 */
function ChromeWindow() {}
/** @type {number} */
ChromeWindow.prototype.id;
/** @type {boolean} */
ChromeWindow.prototype.focused;
/** @type {number} */
ChromeWindow.prototype.top;
/** @type {number} */
ChromeWindow.prototype.left;
/** @type {number} */
ChromeWindow.prototype.width;
/** @type {number} */
ChromeWindow.prototype.height;
/** @type {Array.<Tab>} */
ChromeWindow.prototype.tabs;

/**
 * @see http://code.google.com/chrome/extensions/events.html
 * @constructor
 */
function ChromeEvent() {}
/** @param {Function} callback */
ChromeEvent.prototype.addListener = function(callback) {};
/** @param {Function} callback */
ChromeEvent.prototype.removeListener = function(callback) {};
/** @param {Function} callback */
ChromeEvent.prototype.hasListener = function(callback) {};
/** @param {Function} callback */
ChromeEvent.prototype.hasListeners = function(callback) {};

/**
 * @see http://code.google.com/chrome/extensions/extension.html#type-Port
 * @constructor
 */
function Port() {}
/** @type {string} */
Port.prototype.name;
/** @type {Tab} */
Port.prototype.tab;
/** @type {MessageSender} */
Port.prototype.sender;
/** @type {ChromeEvent} */
Port.prototype.onMessage;
/** @type {ChromeEvent} */
Port.prototype.onDisconnect;
/**
 * @param {Object.<string>} obj Message object.
 */
Port.prototype.postMessage = function(obj) {};

/**
 * @see http://code.google.com/chrome/extensions/extension.html#type-MessageSender
 * @constructor
 */
function MessageSender() {}
/** @type {Tab} */
MessageSender.prototype.tab;
/** @type {string} */
MessageSender.prototype.id;

/**
 * @see http://code.google.com/chrome/extensions/bookmarks.html#type-BookmarkTreeNode
 * @constructor
 */
function BookmarkTreeNode() {}
/** @type {string} */
BookmarkTreeNode.prototype.id;
/** @type {string} */
BookmarkTreeNode.prototype.parentId;
/** @type {number} */
BookmarkTreeNode.prototype.index;
/** @type {string} */
BookmarkTreeNode.prototype.url;
/** @type {string} */
BookmarkTreeNode.prototype.title;
/** @type {number} */
BookmarkTreeNode.prototype.dateAdded;
/** @type {number} */
BookmarkTreeNode.prototype.dateGroupModified;
/** @type {Array.<BookmarkTreeNode>} */
BookmarkTreeNode.prototype.children;
