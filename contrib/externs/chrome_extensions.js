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
 *
 * Please do not add any chrome.experimental.* externs to this file. The
 * experimental APIs change very quickly, so rather than add them here, make a
 * separate externs file for your project, then move the API here when it moves
 * out of experimental.
 *
 * @externs
 *
 */


/** namespace */
var chrome = {};


/** @see http://code.google.com/chrome/extensions/extension.html */
chrome.extension = {};


/** * @type {!Object.<string,string>|undefined} */
chrome.extension.lastError;


/** * @type {boolean|undefined} */
chrome.extension.inIncognitoContext;


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
 * @param {string} path A path to a resource within an extension expressed
 *     relative to it's install directory.
 * @return {string} The fully-qualified URL to the resource.
 */
chrome.extension.getURL = function(path) {};


/**
 * @param {Object=} opt_fetchProperties An object with optional 'type' and
 *     optional 'windowId' keys.
 * @return {Array.<Window>} The global JS objects for each content view.
 */
chrome.extension.getViews = function(opt_fetchProperties) {};


/**
 * @param {function(boolean): void} callback Callback function.
 */
chrome.extension.isAllowedFileSchemeAccess = function(callback) {};


/**
 * @param {function(boolean): void} callback Callback function.
 */
chrome.extension.isAllowedIncognitoAccess = function(callback) {};


/**
 * @param {number|*=} opt_arg1 Either the extensionId to send the request to,
 *     in which case the request is passed as the next arg, or the request.
 * @param {*=} opt_request The request value, if arg1 was the extensionId.
 * @param {function(*): void=} opt_callback The callback function which
 *     takes a JSON response object sent by the handler of the request.
 */
chrome.extension.sendRequest = function(opt_arg1, opt_request, opt_callback) {};


/**
 * @param {string} data
 */
chrome.extension.setUpdateUrlData = function(data) {};


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
 * @param {Object?} options parameters of image capture, such as the format of
 *    the resulting image.
 * @param {function(string): void} callback Callback function which accepts
 *    the data URL string of a JPEG encoding of the visible area of the
 *    captured tab. May be assigned to the 'src' property of an HTML Image
 *    element for display.
 */
chrome.tabs.captureVisibleTab = function(windowId, options, callback) {};


/**
 * @param {number} tabId Tab Id.
 * @param {Object.<string>=} opt_connectInfo Info Object.
 */
chrome.tabs.connect = function(tabId, opt_connectInfo) {};


/**
 * @param {Object} createProperties Info object.
 * @param {function(Tab): void=} opt_callback The callback function.
 */
chrome.tabs.create = function(createProperties, opt_callback) {};


/**
 * @param {number?} tabId Tab id.
 * @param {function(string): void} callback Callback function.
 */
chrome.tabs.detectLanguage = function(tabId, callback) {};


/**
 * @param {number?} tabId Tab id.
 * @param {Object?} details An object which may have 'code', 'file',
 *    or 'allFrames' keys.
 * @param {function(): void=} opt_callback Callback function.
 */
chrome.tabs.executeScript = function(tabId, details, opt_callback) {};


/**
 * @param {number} tabId Tab id.
 * @param {function(Tab): void} callback Callback.
 */
chrome.tabs.get = function(tabId, callback) {};


/**
 * Note: as of 2012-04-12, this function is no longer documented on
 * the public web pages, but there are still existing usages
 *
 * @param {number?} windowId Window id.
 * @param {function(Array.<Tab>): void} callback Callback.
 */
chrome.tabs.getAllInWindow = function(windowId, callback) {};


/**
 * @param {function(Tab): void} callback Callback.
 */
chrome.tabs.getCurrent = function(callback) {};


/**
 * Note: as of 2012-04-12, this function is no longer documented on
 * the public web pages, but there are still existing usages.
 *
 * @param {number?} windowId Window id.
 * @param {function(Tab): void} callback Callback.
 */
chrome.tabs.getSelected = function(windowId, callback) {};


/**
 * @param {Object.<string, (number|Array.<number>)>} highlightInfo
 *     An object with 'windowId' (number) and 'tabs'
 *     (number or array of numbers) keys.
 * @param {function(Window): void} callback Callback function invoked
 *    with each appropriate Window.
 */
chrome.tabs.highlight = function(highlightInfo, callback) {};


/**
 * @param {number?} tabId Tab id.
 * @param {Object?} details An object which may have 'code', 'file',
 *     or 'allFrames' keys.
 * @param {function(): void=} opt_callback Callback function.
 */
chrome.tabs.insertCSS = function(tabId, details, opt_callback) {};


/**
 * @param {number} tabId Tab id.
 * @param {Object.<string, number>} moveProperties An object with 'index'
 *     and optional 'windowId' keys.
 * @param {function(Tab): void=} opt_callback Callback.
 */
chrome.tabs.move = function(tabId, moveProperties, opt_callback) {};


/**
 * @param {Object.<string, (number|string)>} queryInfo An object which may have
 *     'active', 'pinned', 'highlighted', 'status', 'title', 'url', 'windowId',
 *     and 'windowType' keys.
 * @param {function(Array.<Tab>): void=} opt_callback Callback.
 * @return {!Array.<Tab>}
 */
chrome.tabs.query = function(queryInfo, opt_callback) {};


/**
 * @param {number=} opt_tabId Tab id.
 * @param {Object.<string, boolean>=} opt_reloadProperties An object which
 *   may have a 'bypassCache' key.
 * @param {function(): void=} opt_callback The callback function invoked
 *    after the tab has been reloaded.
 */
chrome.tabs.reload = function(opt_tabId, opt_reloadProperties, opt_callback) {};


/**
 * @param {number|Array.<number>} tabIds A tab ID or an array of tab IDs.
 * @param {function(Tab): void=} opt_callback Callback.
 */
chrome.tabs.remove = function(tabIds, opt_callback) {};


/**
 * @param {number} tabId Tab id.
 * @param {*} request The request value of any type.
 * @param {function(*): void=} opt_callback The callback function which
 *     takes a JSON response object sent by the handler of the request.
 */
chrome.tabs.sendRequest = function(tabId, request, opt_callback) {};


/**
 * @param {number} tabId Tab id.
 * @param {Object.<string, (string|boolean)>} updateProperties An object which
 *     may have 'url' or 'selected' key.
 * @param {function(Tab): void=} opt_callback Callback.
 */
chrome.tabs.update = function(tabId, updateProperties, opt_callback) {};


/** @type {ChromeEvent} */
chrome.tabs.onActiveChanged;


/** @type {ChromeEvent} */
chrome.tabs.onAttached;


/** @type {ChromeEvent} */
chrome.tabs.onCreated;


/** @type {ChromeEvent} */
chrome.tabs.onDetached;


/** @type {ChromeEvent} */
chrome.tabs.onHighlightChanged;


/** @type {ChromeEvent} */
chrome.tabs.onMoved;


/** @type {ChromeEvent} */
chrome.tabs.onRemoved;


/** @type {ChromeEvent} */
chrome.tabs.onUpdated;

// DEPRECATED:
// TODO(user): Remove once all usage has been confirmed to have ended.


/** @type {ChromeEvent} */
chrome.tabs.onSelectionChanged;


/** @see http://code.google.com/chrome/extensions/windows.html */
chrome.windows = {};


/**
 * @param {Object?} createData May have 'url', 'left', 'top',
 *     'width', or 'height' properties.
 * @param {function(ChromeWindow): void=} opt_callback Callback.
 */
chrome.windows.create = function(createData, opt_callback) {};


/**
 * Note: This is a screwy function signature as the middle param is
 * optional, but the JS compiler only supports optional params at the
 * end. Sigh. This is dealt with by declaring the 2nd param to be of
 * either the type of the 2nd or 3rd params and declaring the 3rd
 * param to be optional.  This is not completely accurate as the last
 * param, whether there are 2 or 3, must be the callback and this signature
 * accepts a object for the 2nd and last param.
 *
 * @param {number} id Window id.
 * @param {!Object.<string,boolean>|function(!ChromeWindow)} param2
 *     An optional object with a "populate" (boolean) key or the
 *     callback function.
 * @param {function(!ChromeWindow): void=} opt_callback Callback when
 *     param2 is an object.
 */
chrome.windows.get = function(id, param2, opt_callback) {};


/**
 * @param {Object.<string, boolean>?} getInfo May have 'populate' key.
 * @param {function(!Array.<!ChromeWindow>): void} callback Callback.
 */
chrome.windows.getAll = function(getInfo, callback) {};


/**
 * @param {function(ChromeWindow): void} callback Callback.
 */
chrome.windows.getCurrent = function(callback) {};


/**
 * @param {function(ChromeWindow): void} callback Callback.
 */
chrome.windows.getLastFocused = function(callback) {};


/**
 * @param {number} tabId Tab Id.
 * @param {function(): void=} opt_callback Callback.
 */
chrome.windows.remove = function(tabId, opt_callback) {};


/**
 * @param {number} tabId Tab Id.
 * @param {Object.<string, number>} updateProperties An object which may
 *     have 'left', 'top', 'width', or 'height' keys.
 * @param {function(): void=} opt_callback Callback.
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


/**
 * @see http://code.google.com/chrome/extensions/windows.html#property-WINDOW_ID_CURRENT
 * @type {number}
 */
chrome.windows.WINDOW_ID_CURRENT;


/** @see http://code.google.com/chrome/extensions/i18n.html */
chrome.i18n = {};


/**
 * @param {function(Array.<string>): void} callback The callback function which
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


/**
 * @param {number} tabId Tab Id.
 */
chrome.pageAction.show = function(tabId) {};


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
 * @param {function(BookmarkTreeNode): void=} opt_callback The
 *     callback function which accepts a BookmarkTreeNode object.
 * @return {BookmarkTreeNode}
 */
chrome.bookmarks.create = function(bookmark, opt_callback) {};


/**
 * @param {(string|Array.<string>)} idOrIdList
 * @param {function(Array.<BookmarkTreeNode>): void} callback The
 *     callback function which accepts an array of BookmarkTreeNode.
 * @return {Array.<BookmarkTreeNode>}
 */
chrome.bookmarks.get = function(idOrIdList, callback) {};


/**
 * @param {string} id
 * @param {function(Array.<BookmarkTreeNode>): void} callback The
 *     callback function which accepts an array of BookmarkTreeNode.
 * @return {Array.<BookmarkTreeNode>}
 */
chrome.bookmarks.getChildren = function(id, callback) {};


/**
 * @param {number} numberOfItems The number of items to return.
 * @param {function(Array.<BookmarkTreeNode>): void} callback The
 *     callback function which accepts an array of BookmarkTreeNode.
 * @return {Array.<BookmarkTreeNode>}
 */
chrome.bookmarks.getRecent = function(numberOfItems, callback) {};


/**
 * @param {string} id The ID of the root of the subtree to retrieve.
 * @param {function(Array.<BookmarkTreeNode>): void} callback The
 *     callback function which accepts an array of BookmarkTreeNode.
 * @return {Array.<BookmarkTreeNode>}
 */
chrome.bookmarks.getSubTree = function(id, callback) {};


/**
 * @param {function(Array.<BookmarkTreeNode>): void} callback The
 *     callback function which accepts an array of BookmarkTreeNode.
 * @return {Array.<BookmarkTreeNode>}
 */
chrome.bookmarks.getTree = function(callback) {};


/**
 * @param {string} id
 * @param {Object} destination An object which has 'parentId' and
 *     optionally 'index'.
 * @param {function(Array.<BookmarkTreeNode>): void=} opt_callback
 *     The callback function which accepts an array of
 *     BookmarkTreeNode.
 * @return {BookmarkTreeNode}
 */
chrome.bookmarks.move = function(id, destination, opt_callback) {};


/**
 * @param {string} id
 * @param {function(): void=} opt_callback
 */
chrome.bookmarks.remove = function(id, opt_callback) {};


/**
 * @param {string} id
 * @param {function(): void=} opt_callback
 */
chrome.bookmarks.removeTree = function(id, opt_callback) {};


/**
 * @param {string} query
 * @param {function(Array.<BookmarkTreeNode>): void} callback
 * @return {Array.<BookmarkTreeNode>}
 */
chrome.bookmarks.search = function(query, callback) {};


/**
 * @param {string} id
 * @param {Object} changes An object which may have 'title' as a key.
 * @param {function(BookmarkTreeNode): void=} opt_callback The
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
chrome.bookmarks.onImportBegan;


/** @type {ChromeEvent} */
chrome.bookmarks.onImportEnded;


/** @type {ChromeEvent} */
chrome.bookmarks.onMoved;


/** @type {ChromeEvent} */
chrome.bookmarks.onRemoved;


/** @see http://code.google.com/chrome/extensions/omnibox.html */
chrome.omnibox = {};


/**
 * @param {SuggestResult} suggestion A partial SuggestResult object.
 */
chrome.omnibox.setDefaultSuggestion = function(suggestion) {};


/** @type {ChromeEvent} */
chrome.omnibox.onInputCancelled;


/** @type {ChromeEvent} */
chrome.omnibox.onInputChanged;


/** @type {ChromeEvent} */
chrome.omnibox.onInputEntered;


/** @type {ChromeEvent} */
chrome.omnibox.onInputStarted;



/**
 * @constructor
 */
function SuggestResult() {}


/** @type {string} */
SuggestResult.prototype.content;


/** @type {string} */
SuggestResult.prototype.description;


/** @see http://code.google.com/chrome/extensions/dev/contextMenus.html */
chrome.contextMenus = {};


/**
 * @param {!Object} createProperties
 * @param {function()=} opt_callback
 * @return {number} The id of the newly created window.
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
 * @param {function(Cookie=): void} callback
 */
chrome.cookies.get = function(details, callback) {};


/**
 * @param {Object} details
 * @param {function(Array.<Cookie>): void} callback
 */
chrome.cookies.getAll = function(details, callback) {};


/**
 * @param {function(Array.<CookieStore>): void} callback
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


/**
 * @see http://code.google.com/chrome/extensions/cookies.html#event-onChanged
 * @type {ChromeEvent}
 */
chrome.cookies.onChanged;



/** @constructor */
function CookieChangeInfo() {}


/** @type {boolean} */
CookieChangeInfo.prototype.removed;


/** @type {Cookie} */
CookieChangeInfo.prototype.cookie;


/** @type {string} */
CookieChangeInfo.prototype.cause;

chrome.management = {};


/**
 * @param {string} id
 * @param {function(ExtensionInfo): void} callback
 */
chrome.management.get = function(id, callback) {};


/**
 * @param {function(Array.<ExtensionInfo>): void} callback Callback function.
 * @return {Array.<ExtensionInfo>}
 */
chrome.management.getAll = function(callback) {};


/**
 * @param {string} id The id of an already installed extension.
 * @param {function(Array.<string>)=} opt_callback Optional callback function.
 */
chrome.management.getPermissionWarningsById = function(id, opt_callback) {};


/**
 * @param {string} manifestStr Extension's manifest JSON string.
 * @param {function(Array.<string>)=} opt_callback An optional callback
 *     function.
 */
chrome.management.getPermissionWarningsByManifest =
    function(manifestStr, opt_callback) {};


/**
 * @param {function(Array.<ExtensionInfo>): void} callback Callback function.
 */
chrome.management.launchApp = function(id, callback) {};


/**
 * @param {string} id
 * @param {boolean} enabled
 * @param {function(): void} callback
 */
chrome.management.setEnabled = function(id, enabled, callback) {};


/**
 * @param {string} id
 * @param {function(): void} callback
 */
chrome.management.uninstall = function(id, callback) {};


/** @type {ChromeEvent} */
chrome.management.onDisabled;


/** @type {ChromeEvent} */
chrome.management.onEnabled;


/** @type {ChromeEvent} */
chrome.management.onInstalled;


/** @type {ChromeEvent} */
chrome.management.onUninstalled;


/** @see http://code.google.com/chrome/extensions/idle.html */
chrome.idle = {};


/**
 * @param {number} thresholdSeconds Threshold in seconds, used to determine
 *     when a machine is in the idle state.
 * @param {function(string): void} callback Callback to handle the state.
 */
chrome.idle.queryState = function(thresholdSeconds, callback) {};


/** @type {ChromeEvent} */
chrome.idle.onStateChanged;


/**
 * Chrome Text-to-Speech API.
 * @see http://code.google.com/chrome/extensions/tts.html
 */
chrome.tts = {};



/**
 * An event from the TTS engine to communicate the status of an utterance.
 * @constructor
 */
function TtsEvent() {}


/** @type {string} */
TtsEvent.type;


/** @type {number} */
TtsEvent.charIndex;


/** @type {string} */
TtsEvent.errorMessage;



/**
 * A description of a voice available for speech synthesis.
 * @constructor
 */
function TtsVoice() {}


/** @type {string} */
TtsVoice.voiceName;


/** @type {string} */
TtsVoice.lang;


/** @type {string} */
TtsVoice.gender;


/** @type {string} */
TtsVoice.extensionId;


/** @type {Array.<string>} */
TtsVoice.eventTypes;


/**
 * Gets an array of all available voices.
 * @param {function(Array.<TtsVoice>)=} opt_callback An optional callback
 *     function.
 */
chrome.tts.getVoices = function(opt_callback) {};


/**
 * Checks if the engine is currently speaking.
 * @param {function(boolean)=} opt_callback The callback function.
 */
chrome.tts.isSpeaking = function(opt_callback) {};


/**
 * Speaks text using a text-to-speech engine.
 * @param {string} utterance The text to speak, either plain text or a complete,
 *     well-formed SSML document. Speech engines that do not support SSML will
 *     strip away the tags and speak the text. The maximum length of the text is
 *     32,768 characters.
 * @param {Object=} opt_options The speech options.
 * @param {function()=} opt_callback Called right away, before speech finishes.
 */
chrome.tts.speak = function(utterance, opt_options, opt_callback) {};


/**
 * Stops any current speech.
 */
chrome.tts.stop = function() {};


/** @see http://code.google.com/chrome/extensions/ttsEngine.html */
chrome.ttsEngine = {};


/** @type {ChromeEvent} */
chrome.ttsEngine.onSpeak;


/** @type {ChromeEvent} */
chrome.ttsEngine.onStop;


/** @see http://code.google.com/chrome/extensions/contentSettings.html */
chrome.contentSettings = {};


/** @type {!ContentSetting} */
chrome.contentSettings.cookies;


/** @type {!ContentSetting} */
chrome.contentSettings.images;


/** @type {!ContentSetting} */
chrome.contentSettings.javascript;


/** @type {!ContentSetting} */
chrome.contentSettings.plugins;


/** @type {!ContentSetting} */
chrome.contentSettings.popups;


/** @type {!ContentSetting} */
chrome.contentSettings.notifications;


/** @see http://code.google.com/chrome/extensions/fileBrowserHandle.html */
chrome.fileBrowserHandle = {};


/** @type {ChromeEvent} */
chrome.fileBrowserHandle.onExecute;


/** @see http://code.google.com/chrome/extensions/history.html */
chrome.history = {};


/**
 * @param {Object.<string, string>} details Object with a 'url' key.
 */
chrome.history.addUrl = function(details) {};


/**
 * @param {function(): void} callback Callback function.
 */
chrome.history.deleteAll = function(callback) {};


/**
 * @param {Object.<string, string>} range Object with 'startTime'
 *     and 'endTime' keys.
 * @param {function(): void} callback Callback function.
 */
chrome.history.deleteRange = function(range, callback) {};


/**
 * @param {Object.<string, string>} details Object with a 'url' key.
 */
chrome.history.deleteUrl = function(details) {};


/**
 * @param {Object.<string, string>} details Object with a 'url' key.
 * @param {function(!Array.<!VisitItem>): void} callback Callback function.
 * @return {!Array.<!VisitItem>}
 */
chrome.history.getVisits = function(details, callback) {};


/**
 * @param {Object.<string, string>} query Object with a 'text' (string)
 *     key and optional 'startTime' (number), 'endTime' (number) and
 *     'maxResults' keys.
 * @param {function(!Array.<!HistoryItem>): void} callback Callback function.
 * @return {!Array.<!HistoryItem>}
 */
chrome.history.search = function(query, callback) {};


/** @type {ChromeEvent} */
chrome.history.onVisitRemoved;


/** @type {ChromeEvent} */
chrome.history.onVisited;


/** @see http://code.google.com/chrome/extensions/input.ime.html */
chrome.input = {};
chrome.input.ime = {};

/**
 * The OnKeyEvent event takes an extra argument.
 * @constructor
 */
function ChromeInputImeOnKeyEventEvent() {}


/**
 * @param {function(string, !ChromeKeyboardEvent): (boolean|undefined)} callback
 *     callback.
 * @param {Array.<string>=} opt_extraInfoSpec Array of extra information.
 */
ChromeInputImeOnKeyEventEvent.prototype.addListener =
    function(callback, opt_extraInfoSpec) {};


/**
 * @param {function(string, !ChromeKeyboardEvent): (boolean|undefined)} callback
 *     callback.
 */
ChromeInputImeOnKeyEventEvent.prototype.removeListener = function(callback) {};

/**
 * @param {function(string, !ChromeKeyboardEvent): (boolean|undefined)} callback
 *     callback.
 */
ChromeInputImeOnKeyEventEvent.prototype.hasListener = function(callback) {};

/**
 * @param {function(string, !ChromeKeyboardEvent): (boolean|undefined)} callback
 *     callback.
 */
ChromeInputImeOnKeyEventEvent.prototype.hasListeners = function(callback) {};


/**
 * @param {!Object.<string,number>} parameters An object with a
 *     'contextID' (number) key.
 * @param {function(boolean): void} callback Callback function.
 */
chrome.input.ime.clearComposition = function(parameters, callback) {};


/**
 * @param {!Object.<string,(string|number)>} parameters An object with
 *     'contextID' (number) and 'text' (string) keys.
 * @param {function(boolean): void=} opt_callback Callback function.
 */
chrome.input.ime.commitText = function(parameters, opt_callback) {};


/**
 * @param {!Object.<string,(number|Object.<string,(string|number|boolean)>)>}
 *     parameters An object with 'engineID' (string) and 'properties'
 *     (Object) keys.
 * @param {function(boolean): void=} opt_callback Callback function.
 */
chrome.input.ime.setCandidateWindowProperties =
    function(parameters, opt_callback) {};


/**
 * @param {!Object.<string,(number|Object.<string,(string|number)>)>}
 *     parameters An object with 'contextID' (number) and 'candidates'
 *     (array of object) keys.
 * @param {function(boolean): void=} opt_callback Callback function.
 */
chrome.input.ime.setCandidates = function(parameters, opt_callback) {};


/**
 * @param {!Object.<string,(string|number|Object.<string,(string|number)>)>}
 *     parameters An object with 'contextID' (number), 'text' (string),
 *     'selectionStart (number), 'selectionEnd' (number), 'cursor' (number),
 *     and 'segments' (array of object) keys.
 * @param {function(boolean): void=} opt_callback Callback function.
 */
chrome.input.ime.setComposition = function(parameters, opt_callback) {};


/**
 * @param {!Object.<string,number>} parameters An object with
 *     'contextID' (number) and 'candidateID' (number) keys.
 * @param {function(boolean): void=} opt_callback Callback function.
 */
chrome.input.ime.setCursorPosition = function(parameters, opt_callback) {};


/**
 * @param {!Object.<string,(string|Array.<Object.<string,(string|boolean)>>)>}
 *     parameters An object with 'engineID' (string) and 'items'
 *     (array of object) keys.
 * @param {function(): void=} opt_callback Callback function.
 */
chrome.input.ime.setMenuItems = function(parameters, opt_callback) {};


/**
 * @param {!Object.<string,(string|Array.<Object.<string,(string|boolean)>>)>}
 *     parameters An object with  'engineID' (string) and 'items'
 *     (array of object) keys.
 * @param {function(): void=} opt_callback Callback function.
 */
chrome.input.ime.updateMenuItems = function(parameters, opt_callback) {};

/**
 * @param {string} requestId Request id of the event that was handled. This
 *     should come from keyEvent.requestId.
 * @param {boolean} response True if the keystroke was handled, false if not.
 */
chrome.input.ime.keyEventHandled = function(requestId, response) {};


/** @type {!ChromeEvent} */
chrome.input.ime.onActivate;


/** @type {!ChromeEvent} */
chrome.input.ime.onBlur;


/** @type {!ChromeEvent} */
chrome.input.ime.onCandidateClicked;


/** @type {!ChromeEvent} */
chrome.input.ime.onDeactivated;


/** @type {!ChromeEvent} */
chrome.input.ime.onFocus;


/** @type {!ChromeEvent} */
chrome.input.ime.onInputContextUpdate;


/** @type {!ChromeInputImeOnKeyEventEvent} */
chrome.input.ime.onKeyEvent;


/** @type {!ChromeEvent} */
chrome.input.ime.onMenuItemActivated;


/** namespace */
chrome.mediaGalleries = {};


/**
 * @param {?{interactive: (string|undefined)}} details Whether the request
 *     should be interactive if permissions haven't been granted yet.
 * @param {function(!Array.<!FileSystem>)} callback A success callback.
 */
chrome.mediaGalleries.getMediaFileSystems = function(details, callback) {};


/** @see http://code.google.com/chrome/extensions/pageCapture.html */
chrome.pageCapture = {};


/**
 * @param {Object.<string, number>} details Object with a 'tabId' (number) key.
 * @param {function(Blob=): void} callback Callback function.
 */
chrome.pageCapture.saveAsMHTML = function(details, callback) {};


/** @see http://code.google.com/chrome/extensions/permissions.html */
chrome.permissions = {};


/**
 * @param {!Permissions} permissions Permissions.
 * @param {function(boolean): void} callback Callback function.
 */
chrome.permissions.contains = function(permissions, callback) {};


/**
 * @param {function(!Permissions): void} callback Callback function.
 */
chrome.permissions.getAll = function(callback) {};


/**
 * @param {!Permissions} permissions Permissions.
 * @param {function(boolean): void=} opt_callback Callback function.
 */
chrome.permissions.remove = function(permissions, opt_callback) {};


/**
 * @param {!Permissions} permissions Permissions.
 * @param {function(boolean): void=} opt_callback Callback function.
 */
chrome.permissions.request = function(permissions, opt_callback) {};


/** @type {!ChromeEvent} */
chrome.permissions.onAdded;


/** @type {!ChromeEvent} */
chrome.permissions.onRemoved;


/** @see http://code.google.com/chrome/extensions/privacy.html */
chrome.privacy = {};


/** @type {!Object.<string,!ChromeSetting>} */
chrome.privacy.network;


/** @type {!Object.<string,!ChromeSetting>} */
chrome.privacy.services;


/** @type {!Object.<string,!ChromeSetting>} */
chrome.privacy.websites;


/** @see http://code.google.com/chrome/extensions/proxy.html */
chrome.proxy = {};


/** @type {!Object.<string,!ChromeSetting>} */
chrome.proxy.settings;


/** @type {ChromeEvent} */
chrome.proxy.onProxyError;


/** @see http://code.google.com/chrome/extensions/storage.html */
chrome.storage = {};


/** @type {!StorageArea} */
chrome.storage.sync;


/** @type {!StorageArea} */
chrome.storage.local;


/** @type {!StorageChangeEvent} */
chrome.storage.onChanged;


/** @see http://code.google.com/chrome/extensions/types.html */
chrome.chromeSetting = {};


/** @type {ChromeEvent} */
chrome.chromeSetting.onChange;


/** @see http://code.google.com/chrome/extensions/webNavigation.html */
chrome.webNavigation = {};


/**
 * @param {Object} details Object with a 'tabId' (number) key.
 * @param {function(!Array.<Object.<string, (boolean|number|string)>>)} callback
 *     Callback function.
 */
chrome.webNavigation.getAllFrames = function(details, callback) {};


/**
 * @param {Object} details Object with 'tabId' (number) and 'frameId' (number)
 *     keys.
 * @param {function(Object.<string, (boolean|string)>)} callback
 *     Callback function.
 */
chrome.webNavigation.getFrame = function(details, callback) {};


/** @type {ChromeEvent} */
chrome.webNavigation.onBeforeNavigate;


/** @type {ChromeEvent} */
chrome.webNavigation.onCommitted;


/** @type {ChromeEvent} */
chrome.webNavigation.onCompleted;


/** @type {ChromeEvent} */
chrome.webNavigation.onCreatedNavigationTarget;


/** @type {ChromeEvent} */
chrome.webNavigation.onDOMContentLoaded;


/** @type {ChromeEvent} */
chrome.webNavigation.onErrorOccurred;


/** @type {ChromeEvent} */
chrome.webNavigation.onReferenceFragmentUpdated;



/**
 * Most event listeners for WebRequest take extra arguments.
 * @see http://code.google.com/chrome/extensions/webRequest.html.
 * @constructor
 */
function WebRequestEvent() {}


/**
 * @param {function(!Object): (void|!BlockingResponse)} listener Listener
 *     function.
 * @param {!RequestFilter} filter A set of filters that restrict
 *     the events that will be sent to this listener.
 * @param {Array.<string>=} opt_extraInfoSpec Array of extra information
 *     that should be passed to the listener function.
 */
WebRequestEvent.prototype.addListener =
    function(listener, filter, opt_extraInfoSpec) {};


/**
 * @param {function(!Object): (void|!BlockingResponse)} listener Listener
 *     function.
 */
WebRequestEvent.prototype.removeListener = function(listener) {};


/**
 * @param {function(!Object): (void|!BlockingResponse)} listener Listener
 *     function.
 */
WebRequestEvent.prototype.hasListener = function(listener) {};


/**
 * @param {function(!Object): (void|!BlockingResponse)} listener Listener
 *     function.
 */
WebRequestEvent.prototype.hasListeners = function(listener) {};



/**
 * The onErrorOccurred event takes one less parameter than the others.
 * @see http://code.google.com/chrome/extensions/webRequest.html.
 * @constructor
 */
function WebRequestOnErrorOccurredEvent() {}


/**
 * @param {function(!Object): void} listener Listener function.
 * @param {!RequestFilter} filter A set of filters that restrict
 *     the events that will be sent to this listener.
 */
WebRequestOnErrorOccurredEvent.prototype.addListener =
    function(listener, filter) {};


/**
 * @param {function(!Object): void} listener Listener function.
 */
WebRequestOnErrorOccurredEvent.prototype.removeListener = function(listener) {};


/**
 * @param {function(!Object): void} listener Listener function.
 */
WebRequestOnErrorOccurredEvent.prototype.hasListener = function(listener) {};


/**
 * @param {function(!Object): void} listener Listener function.
 */
WebRequestOnErrorOccurredEvent.prototype.hasListeners = function(listener) {};


/** @see http://code.google.com/chrome/extensions/webRequest.html */
chrome.webRequest = {};


/**
 * @param {function(): void=} opt_callback Callback function.
 */
chrome.webRequest.handlerBehaviorChanged = function(opt_callback) {};


/** @type {!WebRequestEvent} */
chrome.webRequest.onAuthRequired;


/** @type {!WebRequestEvent} */
chrome.webRequest.onBeforeRedirect;


/** @type {!WebRequestEvent} */
chrome.webRequest.onBeforeRequest;


/** @type {!WebRequestEvent} */
chrome.webRequest.onBeforeSendHeaders;


/** @type {!WebRequestEvent} */
chrome.webRequest.onCompleted;


/** @type {!WebRequestOnErrorOccurredEvent} */
chrome.webRequest.onErrorOccurred;


/** @type {!WebRequestEvent} */
chrome.webRequest.onHeadersReceived;


/** @type {!WebRequestEvent} */
chrome.webRequest.onResponseStarted;


/** @type {!WebRequestEvent} */
chrome.webRequest.onSendHeaders;


// Classes



/**onKeyEvent
 * @see http://code.google.com/chrome/extensions/management.html
 * @constructor
 */
function ExtensionInfo() {}


/** @type {string} */
ExtensionInfo.prototype.id;


/** @type {string} */
ExtensionInfo.prototype.name;


/** @type {string} */
ExtensionInfo.prototype.description;


/** @type {string} */
ExtensionInfo.prototype.version;


/** @type {boolean} */
ExtensionInfo.prototype.mayDisable;


/** @type {boolean} */
ExtensionInfo.prototype.enabled;


/** @type {string} */
ExtensionInfo.prototype.disabledReason;


/** @type {boolean} */
ExtensionInfo.prototype.isApp;


/** @type {string} */
ExtensionInfo.prototype.appLaunchUrl;


/** @type {string} */
ExtensionInfo.prototype.homePageUrl;


/** @type {string} */
ExtensionInfo.prototype.updateUrl;


/** @type {boolean} */
ExtensionInfo.prototype.offlineEnabled;


/** @type {string} */
ExtensionInfo.prototype.optionsUrl;


/** @type {Array.<IconInfo>} */
ExtensionInfo.prototype.icons;


/** @type {!Array.<string>} */
ExtensionInfo.prototype.permissions;


/** @type {!Array.<string>} */
ExtensionInfo.prototype.hostPermissions;



/**
 * @see http://code.google.com/chrome/extensions/management.html
 * @constructor
 */
function IconInfo() {}


/** @type {number} */
IconInfo.prototype.size;


/** @type {string} */
IconInfo.prototype.url;



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
//** @type {number} */
Tab.prototype.openerTabId;


/** @type {boolean} */
Tab.prototype.highlighted;


/** @type {boolean} */
Tab.prototype.active;


/** @type {boolean} */
Tab.prototype.pinned;


/** @type {string} */
Tab.prototype.url;


/** @type {string} */
Tab.prototype.title;


/** @type {string} */
Tab.prototype.favIconUrl;


/** @type {string} */
Tab.prototype.status;


/** @type {boolean} */
Tab.prototype.incognito;



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


/** @type {string} */
ChromeWindow.prototype.state;


/** @type {boolean} */
ChromeWindow.prototype.alwaysOnTop;



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


/** @type {ChromeEvent} */
Port.prototype.onDisconnect;


/** @type {ChromeEvent} */
Port.prototype.onMessage;


/** @type {MessageSender} */
Port.prototype.sender;


/**
 * @param {Object.<string>} obj Message object.
 */
Port.prototype.postMessage = function(obj) {};


/**
 * Note: as of 2012-04-12, this function is no longer documented on
 * the public web pages, but there are still existing usages.
 */
Port.prototype.disconnect = function() {};



/**
 * @see * http://code.google.com/chrome/extensions/extension.html#type-MessageSender
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



/**
 * @see http://code.google.com/chrome/extensions/debugger.html#type-Debuggee
 * @constructor
 */
function Debuggee() {}


/** @type {number} */
Debuggee.prototype.tabId;



/**
 * @see http://code.google.com/chrome/extensions/contentSettings.html#type-ResourceIdentifier
 * @constructor
 */
function ResourceIdentifier() {}


/** @type {string} */
ResourceIdentifier.prototype.id;


/** @type {string} */
ResourceIdentifier.prototype.description;



/**
 * @see http://code.google.com/chrome/extensions/contentSettings.html#type-ContentSetting
 * @constructor
 */
function ContentSetting() {}


/**
 * @param {!Object.<string,string>} details Settings details.
 * @param {function(): void=} opt_callback Callback function.
 */
ContentSetting.prototype.clear = function(details, opt_callback) {};


/**
 * @param {!Object.<string,(string|boolean|ResourceIdentifier)>} details
 *     Settings details.
 * @param {function(): void} callback Callback function.
 */
ContentSetting.prototype.get = function(details, callback) {};


/**
 * @param {function(): void} callback Callback function.
 */
ContentSetting.prototype.getResourceIdentifiers = function(callback) {};


/**
 * @param {!Object.<string,(string|ResourceIdentifier)>} details
 *     Settings details.
 * @param {function(): void=} opt_callback Callback function.
 */
ContentSetting.prototype.set = function(details, opt_callback) {};



/**
 * @see http://code.google.com/chrome/extensions/history.html#type-HistoryItem
 * @constructor
 */
function HistoryItem() {}


/** @type {string} */
HistoryItem.prototype.id;


/** @type {string} */
HistoryItem.prototype.url;


/** @type {string} */
HistoryItem.prototype.title;


/** @type {number} */
HistoryItem.prototype.lastVisitTime;


/** @type {number} */
HistoryItem.prototype.visitCount;


/** @type {number} */
HistoryItem.prototype.typedCount;



/**
 * @see http://code.google.com/chrome/extensions/history.html#type-VisitItem
 * @constructor
 */
function VisitItem() {}


/** @type {string} */
VisitItem.prototype.id;


/** @type {string} */
VisitItem.prototype.visitId;


/** @type {number} */
VisitItem.prototype.visitTime;


/** @type {string} */
VisitItem.prototype.referringVisitId;


/** @type {string} */
VisitItem.prototype.transition;



/**
 * @see http://code.google.com/chrome/extensions/fileBrowserHandler.html#type-FileHandlerExecuteEventDetails
 * @constructor
 */
function FileHandlerExecuteEventDetails() {}


/** @type {!Array.<!FileEntry>} */
FileHandlerExecuteEventDetails.prototype.entries;


/** @type {number} */
FileHandlerExecuteEventDetails.prototype.tab_id;



/**
 * @see http://code.google.com/chrome/extensions/input.ime.html#type-KeyboardEvent
 * @constructor
 */
function ChromeKeyboardEvent() {}


/** @type {string} */
ChromeKeyboardEvent.prototype.type;


/** @type {string} */
ChromeKeyboardEvent.prototype.requestId;


/** @type {string} */
ChromeKeyboardEvent.prototype.key;


/** @type {boolean} */
ChromeKeyboardEvent.prototype.altKey;


/** @type {boolean} */
ChromeKeyboardEvent.prototype.ctrlKey;


/** @type {boolean} */
ChromeKeyboardEvent.prototype.shiftKey;



/**
 * @see http://code.google.com/chrome/extensions/input.ime.html#type-InputContext
 * @constructor
 */
function InputContext() {}


/** @type {number} */
InputContext.prototype.contextID;


/** @type {string} */
InputContext.prototype.type;



/**
 * @see http://code.google.com/chrome/extensions/permissions.html#type-Permissions
 * @constructor
 */
function Permissions() {}


/** @type {!Array.<string>} */
Permissions.prototype.permissions;


/** @type {!Array.<string>} */
Permissions.prototype.origins;



/**
 * @see http://code.google.com/chrome/extensions/proxy.html#type-ProxyServer
 * @constructor
 */
function ProxyServer() {}


/** @type {string} */
ProxyServer.prototype.scheme;


/** @type {string} */
ProxyServer.prototype.host;


/** @type {number} */
ProxyServer.prototype.port;



/**
 * @see http://code.google.com/chrome/extensions/proxy.html#type-ProxyRules
 * @constructor
 */
function ProxyRules() {}


/** @type {ProxyServer} */
ProxyRules.prototype.singleProxy;


/** @type {ProxyServer} */
ProxyRules.prototype.proxyForHttp;


/** @type {ProxyServer} */
ProxyRules.prototype.proxyForHttps;


/** @type {ProxyServer} */
ProxyRules.prototype.proxyForFtp;


/** @type {ProxyServer} */
ProxyRules.prototype.fallbackProxy;


/** @type {!Array.<string>} */
ProxyRules.prototype.bypassList;



/**
 * @see http://code.google.com/chrome/extensions/proxy.html#type-PacScript
 * @constructor
 */
function PacScript() {}


/** @type {string} */
PacScript.prototype.url;


/** @type {string} */
PacScript.prototype.data;


/** @type {boolean} */
PacScript.prototype.mandatory;



/**
 * @see http://code.google.com/chrome/extensions/proxy.html#type-ProxyConfig
 * @constructor
 */
function ProxyConfig() {}


/** @type {ProxyRules} */
ProxyConfig.prototype.rules;


/** @type {PacScript} */
ProxyConfig.prototype.pacScript;


/** @type {string} */
ProxyConfig.prototype.mode;



/**
 * The event listener for Storage receives an Object mapping each
 * key that changed to its corresponding StorageChange for that item.
 *
 * @see http://code.google.com/chrome/extensions/storage.html
 * @constructor
 */
function StorageChangeEvent() {}


/**
 * @param {function(!Object.<string, !StorageChange>, string)} callback
 *    Listener will receive an object that maps each key to its StorageChange,
 *    and the namespace ("sync" or "local") of the storage area the changes
 *    are for.
 */
StorageChangeEvent.prototype.addListener = function(callback) {};


/** @param {function(!Object.<string, !StorageChange>, string)} callback */
StorageChangeEvent.prototype.removeListener = function(callback) {};


/** @param {function(!Object.<string, !StorageChange>, string)} callback */
StorageChangeEvent.prototype.hasListener = function(callback) {};


/** @param {function(!Object.<string, !StorageChange>, string)} callback */
StorageChangeEvent.prototype.hasListeners = function(callback) {};



/**
 * @see http://code.google.com/chrome/extensions/storage.html#type-StorageChange
 * @constructor
 */
function StorageChange() {}


/** @type {?} */
StorageChange.prototype.oldValue;


/** @type {?} */
StorageChange.prototype.newValue;



/**
 * @see http://code.google.com/chrome/extensions/storage.html#type-StorageArea
 * @constructor
 */
function StorageArea() {}


/**
 * @param {function()=} opt_callback callback on success, or on failure.
 */
StorageArea.prototype.clear = function(opt_callback) {};


/**
 * @param {(string|Array.<string>|Object.<string>)} keys
 *    A single key to get, list of keys to get, or a dictionary
 *    specifying default values (see description of the
 *    object). An empty list or object will return an empty
 *    result object. Pass in null to get the entire contents of storage.
 * @param {!function(Object.<string>)} callback
 *    Callback with storage items, or on failure.
 */
StorageArea.prototype.get = function(keys, callback) {};


/**
 * @param {(string|Array.<string>)} keys
 *    A single key or a list of keys for items to remove.
 * @param {function(Object.<string>)=} opt_callback
 *    Callback on success, or on failure.
 */
StorageArea.prototype.remove = function(keys, opt_callback) {};


/**
 * @param {!Object.<string>} keys
 *    Object specifying items to augment storage
 *    with. Values that cannot be serialized (functions, etc) will be ignored.
 * @param {function(Object.<string>)=} opt_callback
 *    Callback with storage items, or on failure.
 */
StorageArea.prototype.set = function(keys, opt_callback) {};



/**
 * @see http://code.google.com/chrome/extensions/types.html#type-ChromeSetting
 * @constructor
 */
function ChromeSetting() {}


/**
 * @param {Object} details Object with a 'scope' (string) key.
 * @param {function(): void=} opt_callback Callback function.
 */
ChromeSetting.prototype.clear = function(details, opt_callback) {};


/**
 * @param {Object} details Object with an 'incognito' (boolean) key.
 * @param {function(Object.<string, *>): void} callback Callback function.
 */
ChromeSetting.prototype.get = function(details, callback) {};


/**
 * @param {Object} details Object with a 'value' (*) key and an optional
 *     'scope' (string) key.
 * @param {function(): void=} opt_callback Callback function.
 */
ChromeSetting.prototype.set = function(details, opt_callback) {};



/**
 * @see http://code.google.com/chrome/extensions/webRequest.html#type-RequestFilter
 * @constructor
 */
function RequestFilter() {}


/** @type {!Array.<string>} */
RequestFilter.prototype.urls;


/** @type {!Array.<string>} */
RequestFilter.prototype.types;


/** @type {number} */
RequestFilter.prototype.tabId;


/** @type {number} */
RequestFilter.prototype.windowId;



/**
 * @see http://code.google.com/chrome/extensions/webRequest.html#type-HttpHeaders
 * @constructor
 */
function HttpHeader() {}


/** @type {string} */
HttpHeader.prototype.name;


/** @type {string} */
HttpHeader.prototype.value;


/** @type {!Array.<number>} */
HttpHeader.prototype.binaryValue;


/**
 * @see http://code.google.com/chrome/extensions/webRequest.html#type-HttpHeaders
 * @typedef {Array.<!HttpHeader>}
 * @private
 */
var HttpHeaders_;



/**
 * @see http://code.google.com/chrome/extensions/webRequest.html#type-BlockingResponse
 * @constructor
 */
function BlockingResponse() {}


/** @type {boolean} */
BlockingResponse.prototype.cancel;


/** @type {string} */
BlockingResponse.prototype.redirectUrl;


/** @type {!HttpHeaders_} */
BlockingResponse.prototype.requestHeaders;


/** @type {!HttpHeaders_} */
BlockingResponse.prototype.responseHeaders;


/** @type {Object.<string,string>} */
BlockingResponse.prototype.authCredentials;
