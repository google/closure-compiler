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
 * @param {number=} opt_windowId An optional windowId.
 * @return {Array.<Window>} The global JS objects for each content view.
 */
chrome.extension.getExtensionTabs = function(opt_windowId) {};

/**
 * @return {Array.<Window>} The global JS objects for each toolstrip view.
 */
chrome.extension.getToolstrips = function() {};

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
 * @param {number|*=} opt_arg1 Either the extensionId to send the request to,
 *     in which case the request is passed as the next arg, or the request.
 * @param {*=} opt_request The request value, if arg1 was the extensionId.
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
 * @param {function(Tab) : void=} opt_callback Callback.
 */
chrome.tabs.move = function(tabId, moveProperties, opt_callback) {};

/**
 * @param {number} tabId Tab id.
 * @param {function(Tab) : void=} opt_callback Callback.
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
 * @param {function(Tab) : void=} opt_callback Callback.
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
 * @param {function() : void=} opt_callback Callback.
 */
chrome.windows.remove = function(tabId, opt_callback) {};

/**
 * @param {number} tabId Tab Id.
 * @param {Object.<string, number>} updateProperties An object which may
 *     have 'left', 'top', 'width', or 'height' keys.
 * @param {function() : void=} opt_callback Callback.
 */
chrome.windows.update = function(tabId, updateProperties, opt_callback) {};

/** @type {ChromeEvent} */
chrome.windows.onCreated;
/** @type {ChromeEvent} */
chrome.windows.onFocusChanged;
/** @type {ChromeEvent} */
chrome.windows.onRemoved;

/**
 * @see http://code.google.com/chrome/extensions/windows.html#property-WINDOW_ID_NONE
 * @type {number}
 */
chrome.windows.WINDOW_ID_NONE;

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
 * @param {Object} details An object which may have 'popup' or 'tabId' as keys.
 */
chrome.pageAction.setPopup = function(details) {};

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
 * @param {Object} details An object which may have 'popup' or 'tabId' as keys.
 */
chrome.browserAction.setPopup = function(details) {};

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


/** @see http://code.google.com/chrome/extensions/dev/contextMenus.html */
chrome.contextMenus = {};

/**
 * @param {!Object} createProperties
 * @param {function()=} opt_callback
 */
chrome.contextMenus.create = function(createProperties, opt_callback) {};

/**
 * @param {number} menuItemId
 * @param {function()=} opt_callback
 */
chrome.contextMenus.remove = function(menuItemId, opt_callback) {};

/**
 * @param {function()=} opt_callback
 */
chrome.contextMenus.removeAll = function(opt_callback) {};

/**
 * @param {number} id
 * @param {!Object} updateProperties
 * @param {function()=} opt_callback
 */
chrome.contextMenus.update = function(id, updateProperties, opt_callback) {};


/** @see http://code.google.com/chrome/extensions/dev/cookies.html */
chrome.cookies = {};

/**
 * @param {Object} details
 * @param {function(Cookie=) : void} callback
 */
chrome.cookies.get = function(details, callback) {};

/**
 * @param {Object} details
 * @param {function(Array.<Cookie>) : void} callback
 */
chrome.cookies.getAll = function(details, callback) {};

/**
 * @param {function(Array.<CookieStore>) : void} callback
 */
chrome.cookies.getAllCookieStores = function(callback) {};

/**
 * @param {Object} details
 */
chrome.cookies.remove = function(details) {};

/**
 * @param {Object} details
 */
chrome.cookies.set = function(details) {};

/** @type {ChromeEvent} */
chrome.cookies.onChanged;


/** @see http://code.google.com/chrome/extensions/experimental.html */
chrome.experimental = {};


/** @see http://code.google.com/chrome/extensions/experimental.clipboard.html */
chrome.experimental.clipboard = {};

/**
 * @param {number} tabId
 * @param {function()=} opt_callback
 */
chrome.experimental.clipboard.executeCopy = function(tabId, opt_callback) {};

/**
 * @param {number} tabId
 * @param {function()=} opt_callback
 */
chrome.experimental.clipboard.executeCut = function(tabId, opt_callback) {};

/**
 * @param {number} tabId
 * @param {function()=} opt_callback
 */
chrome.experimental.clipboard.executePaste = function(tabId, opt_callback) {};


/** @see http://code.google.com/chrome/extensions/experimental.contextMenu.html */
chrome.experimental.contextMenu = {};

/**
 * @param {!Object} createProperties
 * @param {function(number)=} opt_callback
 */
chrome.experimental.contextMenu.create =
    function(createProperties, opt_callback) {};

/**
 * @param {number} menuItemId
 * @param {function()=} opt_callback
 */
chrome.experimental.contextMenu.remove = function(menuItemId, opt_callback) {};


/** @see http://src.chromium.org/viewvc/chrome/trunk/src/chrome/common/extensions/api/extension_api.json */
chrome.experimental.extension = {};

/**
 * @return {Window}
 */
chrome.experimental.extension.getPopupView = function() {};

/** @see http://code.google.com/chrome/extensions/experimental.infobars.html */
chrome.experimental.infobars = {};

/**
 * @param {!Object} details
 * @param {function(Window)=} opt_callback
 */
chrome.experimental.infobars.show = function(details, opt_callback) {};


/** @see http://src.chromium.org/viewvc/chrome/trunk/src/chrome/common/extensions/api/extension_api.json */
chrome.experimental.metrics = {};

/**
 * @param {string} metricName
 */
chrome.experimental.metrics.recordUserAction = function(metricName) {};

/**
 * @param {string} metricName
 * @param {number} value
 */
chrome.experimental.metrics.recordPercentage = function(metricName, value) {};

/**
 * @param {string} metricName
 * @param {number} value
 */
chrome.experimental.metrics.recordCount = function(metricName, value) {};

/**
 * @param {string} metricName
 * @param {number} value
 */
chrome.experimental.metrics.recordSmallCount = function(metricName, value) {};

/**
 * @param {string} metricName
 * @param {number} value
 */
chrome.experimental.metrics.recordMediumCount = function(metricName, value) {};

/**
 * @param {string} metricName
 * @param {number} value
 */
chrome.experimental.metrics.recordTime = function(metricName, value) {};

/**
 * @param {string} metricName
 * @param {number} value
 */
chrome.experimental.metrics.recordMediumTime = function(metricName, value) {};

/**
 * @param {string} metricName
 * @param {number} value
 */
chrome.experimental.metrics.recordLongTime = function(metricName, value) {};

/**
 * @param {MetricType} metric
 * @param {number} value
 */
chrome.experimental.metrics.recordValue = function(metric, value) {};


/** @see http://src.chromium.org/viewvc/chrome/trunk/src/chrome/common/extensions/api/extension_api.json */
chrome.experimental.popup = {};

/**
 * @param {string} url
 * @param {Object} showDetails
 * @param {function() : void=} opt_callback
 */
chrome.experimental.popup.show = function(url, showDetails, opt_callback) {};

/** @type {ChromeEvent} */
chrome.experimental.popup.onClosed;

/** @see http://code.google.com/chrome/extensions/experimental.processes.html */
chrome.experimental.processes = {};

/**
 * @param {number} tabId
 * @param {function(Process)} callback
 */
chrome.experimental.processes.getProcessForTab = function(tabId, callback) {}


chrome.experimental.rlz = {};

/**
 * @param {string} product
 * @param {string} accessPoint
 * @param {string} event
 */
chrome.experimental.rlz.recordProductEvent = function(product, accessPoint,
                                                      event) {};

/**
 * @param {string} product
 * @param {Array.<string>} accessPoints
 */
chrome.experimental.rlz.clearProductState = function(product, accessPoints) {};

/**
 * @param {string} product
 * @param {Array.<string>} accessPoints
 * @param {string} signature
 * @param {string} brand
 * @param {string} id
 * @param {string} lang
 * @param {boolean} excludeMachineId
 */
chrome.experimental.rlz.sendFinancialPing = function(product, accessPoints,
                                                     signature, brand, id, lang,
                                                     excludeMachineId) {};

/**
 * @param {string} accessPoint
 * @param {function(string): void} callback
 */
chrome.experimental.rlz.getAccessPointRlz = function(accessPoint, callback) {};


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
/** @type {boolean} */
ChromeWindow.prototype.incognito;
/** @type {string} */
ChromeWindow.prototype.type;

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
Port.prototype.disconnect = function() {};

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

/**
 * @see http://code.google.com/chrome/extensions/dev/cookies.html#type-Cookie
 * @constructor
 */
function Cookie() {}
/** @type {string} */
Cookie.prototype.name;
/** @type {string} */
Cookie.prototype.value;
/** @type {string} */
Cookie.prototype.domain;
/** @type {boolean} */
Cookie.prototype.hostOnly;
/** @type {string} */
Cookie.prototype.path;
/** @type {boolean} */
Cookie.prototype.secure;
/** @type {boolean} */
Cookie.prototype.httpOnly;
/** @type {boolean} */
Cookie.prototype.session;
/** @type {number} */
Cookie.prototype.expirationDate;
/** @type {string} */
Cookie.prototype.storeId;

/**
 * @see http://code.google.com/chrome/extensions/dev/cookies.html#type-CookieStore
 * @constructor
 */
function CookieStore() {}
/** @type {string} */
CookieStore.prototype.id;
/** @type {Array.<number>} */
CookieStore.prototype.tabIds;

/**
 * Experimental MetricType
 * @constructor
 */
function MetricType() {}
/** @type {string} */
MetricType.prototype.metricName;
/** @type {string} */
MetricType.prototype.type;
/** @type {number} */
MetricType.prototype.min;
/** @type {number} */
MetricType.prototype.max;
/** @type {number} */
MetricType.prototype.buckets;

/**
 * Experimental Process type.
 * @see http://code.google.com/chrome/extensions/experimental.processes.html#type-Process
 * @constructor
 */
function Process() {}
/** @type {number} */
Process.prototype.id;

/**
 * @see http://code.google.com/chrome/extensions/dev/contextMenus.html#type-OnClickData
 * @constructor
 */
function OnClickData() {}
/** @type {number} */
OnClickData.prototype.menuItemId;
/** @type {number} */
OnClickData.prototype.parentMenuItemId;
/** @type {string} */
OnClickData.prototype.mediaType;
/** @type {string} */
OnClickData.prototype.linkUrl;
/** @type {string} */
OnClickData.prototype.srcUrl;
/** @type {string} */
OnClickData.prototype.pageUrl;
/** @type {string} */
OnClickData.prototype.frameUrl;
/** @type {string} */
OnClickData.prototype.selectionText;
/** @type {string} */
OnClickData.prototype.editable;
