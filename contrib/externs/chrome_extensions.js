/*
 * Copyright 2009 The Closure Compiler Authors
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
 * This is the externs file for the Chrome Extensions API.
 * See https://developer.chrome.com/extensions/
 *
 * There are several problematic issues regarding Chrome extension APIs and
 * this externs files, including:
 * A. When to add packages to this file
 * B. Optional parameters
 * C. Pseudo-types
 * D. Events
 * E. Nullability
 *
 * The best practices for each are described in more detail below.  It
 * should be noted that, due to historical reasons, and the evolutionary
 * nature of this file, much this file currently violates the best practices
 * described below. As changed are made, the changes should adhere to the
 * best practices.
 *
 * A. When to Add Packages to this File?
 * Packages in chrome.experimental.* should *not* be added to this file. The
 * experimental APIs change very quickly, so rather than add them here, make a
 * separate externs file for your project, then move the API here when it moves
 * out of experimental.
 *
 * B. Optional Parameters
 * The Chrome extension APIs make extensive use of optional parameters that
 * are not at the end of the parameter list, "interior optional parameters",
 * while the JS Compiler's type system requires optional parameters to be
 * at the end. This creates a bit of tension:
 *
 * 1. If a method has N required params, then the parameter declarations
 *    should have N required params.
 * 2. If, due to interior optional params, a parameter can be of more than
 *    one type, its at-param should:
 *    a. be named to indicate both possibilities, eg, extensionIdOrRequest,
 *       or getInfoOrCallback.
 *    b. the type should include both types, in the same order as the parts
 *       of the name, even when one type subsumes the other, eg, {string|*}
 *       or {Object|function(string)}.
 * See chrome.runtime.sendMessage for a complex example as sendMessage
 * takes three params with the first and third being optional.
 *
 * C. Pseudo-types
 * The Chrome APIs define many types are that actually pseudo-types, that
 * is, they can't be instantiated by name, such as Port defined at
 * http://developer.chrome.com/extensions/runtime.html#type-Port.
 *
 * There are two fundamentally different kinds of pseudo-types: those
 * instantiated in extension code and those instantiated in extension
 * library functions. The latter are returned by library functions or passed
 * to callbacks. Currently, there are no instances of the former in Chrome
 * Extension APIs, however, the app APIs include CreateWindowOptions, defined at
 * http://developer.chrome.com/apps/app.window.html#type-CreateWindowOptions.
 *
 * Those types instantiated in extension code should be declared as typedefs
 * so that object literals and objects created via goog.object are acceptable,
 * for example, a subset of CreateWindowOptions would be:
 *
 *   * at-typedef {{id: (string|undefined), singleton: (boolean|undefined)}}
 *   chrome.app.window.CreateWindowOptions;
 *
 * Those types instantiated in library code should be declared as classes.
 * Always qualify the type name to reduce top-level pollution in this file:
 *
 *   Do:
 *        function chrome.extension.Port() {}
 *   Don't:
 *        function Port() {}
 *
 * In both cases, when the type is used by more than one package use "shared",
 * for example, chrome.shared.Port.
 *
 * D. Events
 * Most packages define a set of events with the standard set of methods:
 * addListener, removeListener, hasListener and hasListeners.  ChromeEvent
 * is the appropriate type when an event's listeners do not take any
 * parameters, however, many events take parameters specific to that event:
 *
 * 1. Create a pseudo-type for the event, for example,
 *    chrome.runtime.PortEvent and define the four methods on it.
 * 2. Fully describe the listener/callback's signature, for example,
 *
 *       * at-param {function(!chrome.runtime.Port): void} callback Callback.
 *      chrome.runtime.PortEvent.prototype.addListener =
 *          function(callback) {};
 *    or
 *
 *       * at-param {function(*, !chrome.runtime.MessageSender,
 *       *     function(*): void): (boolean|undefined)} callback Callback.
 *      chrome.runtime.MessageSenderEvent.prototype.addListener =
 *          function(callback) {};
 *
 * E. Nullability
 * We treat the Chrome Extension API pages as "the truth".  Not-null types
 * should be used in the following situations:
 *
 * 1. Parameters and return values that are not explicitly declared to handle
 *    null.
 * 2. Static event instances, for example, chrome.runtime.onConnect's type
 *    should be: !chrome.runtime.PortEvent.
 * 3. Optional params as there is little value to passing null when the
 *    parameter can be omitted, of course, if null is explicitly declared
 *    to be meaningful, then a nullable type should be used.
 *
 * @externs
 *
 */


/**
 * TODO(tbreisacher): Move all chrome.app.* externs into their own file.
 * @const
 */
chrome.app = {};


/**
 * @const
 * @see http://developer.chrome.com/apps/app.runtime.html
 */
chrome.app.runtime = {};


/**
 * @typedef {{id: (string|undefined), items: (!Array.<!Object>|undefined)}}
 */
chrome.app.runtime.LaunchData;



/**
 * The type of chrome.app.runtime.onLaunched.
 * @constructor
 */
chrome.app.runtime.LaunchEvent = function() {};


/**
 * @param {function(!chrome.app.runtime.LaunchData)} callback
 * @see http://developer.chrome.com/apps/app.runtime.html#event-onLaunched
 */
chrome.app.runtime.LaunchEvent.prototype.addListener = function(callback) {};


/**
 * @param {function(!chrome.app.runtime.LaunchData)} callback
 */
chrome.app.runtime.LaunchEvent.prototype.removeListener = function(callback) {};


/**
 * @param {function(!chrome.app.runtime.LaunchData)} callback
 * @return {boolean}
 */
chrome.app.runtime.LaunchEvent.prototype.hasListener = function(callback) {};


/**
 * @return {boolean}
 */
chrome.app.runtime.LaunchEvent.prototype.hasListeners = function() {};


/** @type {!chrome.app.runtime.LaunchEvent} */
chrome.app.runtime.onLaunched;


/**
 * @type {!ChromeEvent}
 * @see http://developer.chrome.com/apps/app.runtime.html#event-onRestarted
 */
chrome.app.runtime.onRestarted;


/**
 * @const
 * @see http://developer.chrome.com/apps/app.window.html
 */
chrome.app.window = {};



/**
 * @constructor
 * @see http://developer.chrome.com/apps/app.window.html#type-AppWindow
 */
chrome.app.window.AppWindow = function() {};


/**
 * @see http://developer.chrome.com/apps/app.window.html#type-AppWindow
 */
chrome.app.window.AppWindow.prototype.focus = function() {};


/**
 * @see http://developer.chrome.com/apps/app.window.html#type-AppWindow
 */
chrome.app.window.AppWindow.prototype.fullscreen = function() {};


/**
 * @return {boolean}
 * @see http://developer.chrome.com/apps/app.window.html#type-AppWindow
 */
chrome.app.window.AppWindow.prototype.isFullscreen = function() {};


/**
 * @see http://developer.chrome.com/apps/app.window.html#type-AppWindow
 */
chrome.app.window.AppWindow.prototype.minimize = function() {};


/**
 * @return {boolean}
 * @see http://developer.chrome.com/apps/app.window.html#type-AppWindow
 */
chrome.app.window.AppWindow.prototype.isMinimized = function() {};


/**
 * @see http://developer.chrome.com/apps/app.window.html#type-AppWindow
 */
chrome.app.window.AppWindow.prototype.maximize = function() {};


/**
 * @return {boolean}
 * @see http://developer.chrome.com/apps/app.window.html#type-AppWindow
 */
chrome.app.window.AppWindow.prototype.isMaximized = function() {};


/**
 * @see http://developer.chrome.com/apps/app.window.html#type-AppWindow
 */
chrome.app.window.AppWindow.prototype.restore = function() {};


/**
 * @param {number} left The new left position, in pixels.
 * @param {number} top The new top position, in pixels.
 * @see http://developer.chrome.com/apps/app.window.html#type-AppWindow
 */
chrome.app.window.AppWindow.prototype.moveTo = function(left, top) {};


/**
 * @param {number} width The new width, in pixels.
 * @param {number} height The new height, in pixels.
 * @see http://developer.chrome.com/apps/app.window.html#type-AppWindow
 */
chrome.app.window.AppWindow.prototype.resizeTo = function(width, height) {};


/**
 * @see http://developer.chrome.com/apps/app.window.html#type-AppWindow
 */
chrome.app.window.AppWindow.prototype.drawAttention = function() {};


/**
 * @see http://developer.chrome.com/apps/app.window.html#type-AppWindow
 */
chrome.app.window.AppWindow.prototype.clearAttention = function() {};


/**
 * @see http://developer.chrome.com/apps/app.window.html#type-AppWindow
 */
chrome.app.window.AppWindow.prototype.close = function() {};


/**
 * @see http://developer.chrome.com/apps/app.window.html#type-AppWindow
 */
chrome.app.window.AppWindow.prototype.show = function() {};


/**
 * @see http://developer.chrome.com/apps/app.window.html#type-AppWindow
 */
chrome.app.window.AppWindow.prototype.hide = function() {};


/**
 * @return {!chrome.app.window.Bounds} The current window bounds.
 * @see http://developer.chrome.com/apps/app.window.html#type-AppWindow
 */
chrome.app.window.AppWindow.prototype.getBounds = function() {};


/**
 * @param {!chrome.app.window.Bounds} bounds The new window bounds.
 * @see http://developer.chrome.com/apps/app.window.html#type-AppWindow
 */
chrome.app.window.AppWindow.prototype.setBounds = function(bounds) {};


/** @type {ChromeEvent} */
chrome.app.window.AppWindow.prototype.onBoundsChanged;


/** @type {ChromeEvent} */
chrome.app.window.AppWindow.prototype.onClosed;


/** @type {ChromeEvent} */
chrome.app.window.AppWindow.prototype.onFullscreened;


/** @type {ChromeEvent} */
chrome.app.window.AppWindow.prototype.onMinimized;


/** @type {ChromeEvent} */
chrome.app.window.AppWindow.prototype.onMaximized;


/** @type {ChromeEvent} */
chrome.app.window.AppWindow.prototype.onRestored;


/** @type {!Window} */
chrome.app.window.AppWindow.prototype.contentWindow;


/**
 * @typedef {{
 *   left: (number|undefined),
 *   top: (number|undefined),
 *   width: (number|undefined),
 *   height: (number|undefined)
 * }}
 * @see http://developer.chrome.com/apps/app.window.html#type-Bounds
 */
chrome.app.window.Bounds;


/**
 * @typedef {{
 *   id: (string|undefined),
 *   minWidth: (number|undefined),
 *   minHeight: (number|undefined),
 *   maxWidth: (number|undefined),
 *   maxHeight: (number|undefined),
 *   frame: (string|undefined),
 *   bounds: (!chrome.app.window.Bounds|undefined),
 *   transparentBackground: (boolean|undefined),
 *   hidden: (boolean|undefined),
 *   resizable: (boolean|undefined),
 *   singleton: (boolean|undefined)
 * }}
 * @see http://developer.chrome.com/apps/app.window.html#method-create
 */
chrome.app.window.CreateWindowOptions;


/**
 * @param {string} url URL to create.
 * @param {!chrome.app.window.CreateWindowOptions=} opt_options The options for
 *     the new window.
 * @param {function(!chrome.app.window.AppWindow)=} opt_createWindowCallback
 *     Callback to be run.
 * @see http://developer.chrome.com/apps/app.window.html#method-create
 */
chrome.app.window.create = function(
    url, opt_options, opt_createWindowCallback) {};


/**
 * Returns an AppWindow object for the current script context (ie JavaScript
 * 'window' object).
 * @return {!chrome.app.window.AppWindow}
 * @see http://developer.chrome.com/apps/app.window.html#method-current
 */
chrome.app.window.current = function() {};


/**
 * @type {!ChromeEvent}
 * @see http://developer.chrome.com/apps/app.window.html#event-onBoundsChanged
 */
chrome.app.window.onBoundsChanged;


/**
 * @type {!ChromeEvent}
 * @see http://developer.chrome.com/apps/app.window.html#event-onClosed
 */
chrome.app.window.onClosed;


/**
 * @type {!ChromeEvent}
 * @see http://developer.chrome.com/apps/app.window.html#event-onFullscreened
 */
chrome.app.window.onFullscreened;


/**
 * @type {!ChromeEvent}
 * @see http://developer.chrome.com/apps/app.window.html#event-onMaximized
 */
chrome.app.window.onMaximized;


/**
 * @type {!ChromeEvent}
 * @see http://developer.chrome.com/apps/app.window.html#event-onMinimized
 */
chrome.app.window.onMinimized;


/**
 * @type {!ChromeEvent}
 * @see http://developer.chrome.com/apps/app.window.html#event-onRestored
 */
chrome.app.window.onRestored;


/**
 * @see http://code.google.com/chrome/extensions/extension.html
 * @const
 */
chrome.extension = {};


/** @type {!Object.<string,string>|undefined} */
chrome.extension.lastError;


/** @type {boolean|undefined} */
chrome.extension.inIncognitoContext;


// TODO: change Object to !Object when it's clear nobody is passing in null
// TODO: change Port to !Port since it should never be null
/**
 * @param {string|Object.<string>=} opt_extensionIdOrConnectInfo Either the
 *     extensionId to connect to, in which case connectInfo params can be
 *     passed in the next optional argument, or the connectInfo params.
 * @param {Object.<string>=} opt_connectInfo The connectInfo object,
 *     if arg1 was the extensionId to connect to.
 * @return {Port} New port.
 */
chrome.extension.connect = function(
    opt_extensionIdOrConnectInfo, opt_connectInfo) {};


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
 * @param {string|*} extensionIdOrRequest Either the extensionId to send the
 *     request to, in which case the request is passed as the next arg, or the
 *     request.
 * @param {*=} opt_request The request value, if arg1 was the extensionId.
 * @param {function(*): void=} opt_callback The callback function which
 *     takes a JSON response object sent by the handler of the request.
 */
chrome.extension.sendMessage = function(
    extensionIdOrRequest, opt_request, opt_callback) {};


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
chrome.extension.onMessage;


/** @type {ChromeEvent} */
chrome.extension.onRequest;


/** @type {ChromeEvent} */
chrome.extension.onRequestExternal;


/**
 * @see http://code.google.com/chrome/extensions/runtime.html
 * @const
 */
chrome.runtime = {};


/** @type {!Object.<string,string>|undefined} */
chrome.runtime.lastError;


/** @type {string} */
chrome.runtime.id;


/**
 * @param {function(!Window=): void} callback Callback function.
 */
chrome.runtime.getBackgroundPage = function(callback) {};



/**
 * Manifest information returned from chrome.runtime.getManifest. See
 * http://developer.chrome.com/extensions/manifest.html. Note that there are
 * several other fields not included here. They should be added to these externs
 * as needed.
 * @constructor
 */
chrome.runtime.Manifest = function() {};


/** @type {string} */
chrome.runtime.Manifest.prototype.name;


/** @type {string} */
chrome.runtime.Manifest.prototype.version;


/** @type {number|undefined} */
chrome.runtime.Manifest.prototype.manifest_version;


/** @type {string|undefined} */
chrome.runtime.Manifest.prototype.description;


/** @type {!chrome.runtime.Manifest.Oauth2|undefined} */
chrome.runtime.Manifest.prototype.oauth2;



/**
 * Oauth2 info in the manifest.
 * See http://developer.chrome.com/apps/app_identity.html#update_manifest.
 * @constructor
 */
chrome.runtime.Manifest.Oauth2 = function() {};


/** @type {string} */
chrome.runtime.Manifest.Oauth2.prototype.client_id;


/**
 * http://developer.chrome.com/extensions/runtime.html#method-getManifest
 * @return {!chrome.runtime.Manifest} The full manifest file of the app or
 *     extension.
 */
chrome.runtime.getManifest = function() {};


/**
 * @param {string} path A path to a resource within an extension expressed
 *     relative to it's install directory.
 * @return {string} The fully-qualified URL to the resource.
 */
chrome.runtime.getURL = function(path) {};


/**
 * Reloads the app or extension.
 */
chrome.runtime.reload = function() {};


/**
 * @param {function(string, !Object=): void} callback
 */
chrome.runtime.requestUpdateCheck = function(callback) {};


/**
 * @param {string|!Object.<string>=} opt_extensionIdOrConnectInfo Either the
 *     extensionId to connect to, in which case connectInfo params can be
 *     passed in the next optional argument, or the connectInfo params.
 * @param {!Object.<string>=} opt_connectInfo The connectInfo object,
 *     if arg1 was the extensionId to connect to.
 * @return {!Port} New port.
 */
chrome.runtime.connect = function(
    opt_extensionIdOrConnectInfo, opt_connectInfo) {};


/**
 * @param {string|*} extensionIdOrMessage Either the extensionId to send the
 *     message to, in which case the message is passed as the next arg, or the
 *     message itself.
 * @param {(*|function(*): void)=} opt_messageOrCallback The message, if arg1
 *     was the extensionId, or the callback, if arg1 was the message, or
 *     optional.
 * @param {function(*): void=} opt_callback The callback function which
 *     takes a JSON response object sent by the handler of the request.
 */
chrome.runtime.sendMessage = function(
    extensionIdOrMessage, opt_messageOrCallback, opt_callback) {};


/** @type {!chrome.runtime.PortEvent} */
chrome.runtime.onConnect;


/** @type {!chrome.runtime.PortEvent} */
chrome.runtime.onConnectExternal;


/** @type {!chrome.runtime.ObjectEvent} */
chrome.runtime.onInstalled;


/** @type {!chrome.runtime.MessageSenderEvent} */
chrome.runtime.onMessage;


/** @type {!chrome.runtime.MessageSenderEvent} */
chrome.runtime.onMessageExternal;


/** @type {!ChromeEvent} */
chrome.runtime.onStartup;


/** @type {!ChromeEvent} */
chrome.runtime.onSuspend;


/** @type {!ChromeEvent} */
chrome.runtime.onSuspendCanceled;


/** @type {!chrome.runtime.ObjectEvent} */
chrome.runtime.onUpdateAvailable;



/**
 * Event whose listeners take an Object parameter.
 * @constructor
 */
chrome.runtime.ObjectEvent = function() {};


/**
 * @param {function(!Object): void} callback Callback.
 */
chrome.runtime.ObjectEvent.prototype.addListener = function(callback) {};


/**
 * @param {function(!Object): void} callback Callback.
 */
chrome.runtime.ObjectEvent.prototype.removeListener = function(callback) {};


/**
 * @param {function(!Object): void} callback Callback.
 * @return {boolean}
 */
chrome.runtime.ObjectEvent.prototype.hasListener = function(callback) {};


/**
 * @return {boolean}
 */
chrome.runtime.ObjectEvent.prototype.hasListeners = function() {};



/**
 * Event whose listeners take a Port parameter.
 * @constructor
 */
chrome.runtime.PortEvent = function() {};


/**
 * @param {function(!Port): void} callback Callback.
 */
chrome.runtime.PortEvent.prototype.addListener = function(callback) {};


/**
 * @param {function(!Port): void} callback Callback.
 */
chrome.runtime.PortEvent.prototype.removeListener = function(callback) {};


/**
 * @param {function(!Port): void} callback Callback.
 * @return {boolean}
 */
chrome.runtime.PortEvent.prototype.hasListener = function(callback) {};


/**
 * @return {boolean}
 */
chrome.runtime.PortEvent.prototype.hasListeners = function() {};



/**
 * Event whose listeners take a MessageSender and additional parameters.
 * @see https://developer.chrome.com/dev/apps/runtime.html#event-onMessage
 * @constructor
 */
chrome.runtime.MessageSenderEvent = function() {};


/**
 * @param {function(*, !MessageSender, function(*): void): (boolean|undefined)}
 *     callback Callback.
 */
chrome.runtime.MessageSenderEvent.prototype.addListener = function(callback) {};


/**
 * @param {function(*, !MessageSender, function(*): void): (boolean|undefined)}
 *     callback Callback.
 */
chrome.runtime.MessageSenderEvent.prototype.removeListener = function(callback)
    {};


/**
 * @param {function(*, !MessageSender, function(*): void): (boolean|undefined)}
 *     callback Callback.
 * @return {boolean}
 */
chrome.runtime.MessageSenderEvent.prototype.hasListener = function(callback) {};


/**
 * @return {boolean}
 */
chrome.runtime.MessageSenderEvent.prototype.hasListeners = function() {};


/**
 * @const
 * @see http://code.google.com/chrome/extensions/tabs.html
 */
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
chrome.tabs.sendMessage = function(tabId, request, opt_callback) {};


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
chrome.tabs.onActivated;


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


/**
 * @const
 * @see http://code.google.com/chrome/extensions/windows.html
 */
chrome.windows = {};


/**
 * @param {Object=} opt_createData May have many keys to specify parameters.
 *     Or the callback.
 * @param {function(ChromeWindow): void=} opt_callback Callback.
 */
chrome.windows.create = function(opt_createData, opt_callback) {};


/**
 * @param {number} id Window id.
 * @param {Object=} opt_getInfo May have 'populate' key. Or the callback.
 * @param {function(!ChromeWindow): void=} opt_callback Callback when
 *     opt_getInfo is an object.
 */
chrome.windows.get = function(id, opt_getInfo, opt_callback) {};


/**
 * @param {Object=} opt_getInfo May have 'populate' key. Or the callback.
 * @param {function(!Array.<!ChromeWindow>): void=} opt_callback Callback.
 */
chrome.windows.getAll = function(opt_getInfo, opt_callback) {};


/**
 * @param {Object=} opt_getInfo May have 'populate' key. Or the callback.
 * @param {function(ChromeWindow): void=} opt_callback Callback.
 */
chrome.windows.getCurrent = function(opt_getInfo, opt_callback) { };


/**
 * @param {Object=} opt_getInfo May have 'populate' key. Or the callback.
 * @param {function(ChromeWindow): void=} opt_callback Callback.
 */
chrome.windows.getLastFocused = function(opt_getInfo, opt_callback) { };


/**
 * @param {number} tabId Tab Id.
 * @param {function(): void=} opt_callback Callback.
 */
chrome.windows.remove = function(tabId, opt_callback) {};


/**
 * @param {number} tabId Tab Id.
 * @param {Object} updateProperties An object which may have many keys for
 *     various options.
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


/**
 * @const
 * @see http://code.google.com/chrome/extensions/i18n.html
 */
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


/**
 * @const
 * @see http://code.google.com/chrome/extensions/pageAction.html
 */
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


/**
 * @const
 * @see http://code.google.com/chrome/extensions/browserAction.html
 */
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


/**
 * @param {number} tabId the ID of the tab on which to disable this action.
 */
chrome.browserAction.disable = function(tabId) {};


/**
 * @param {number} tabId the ID of the tab on which to enable this action.
 */
chrome.browserAction.enable = function(tabId) {};


/**
 * @const
 * @see http://code.google.com/chrome/extensions/bookmarks.html
 */
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


/**
 * @const
 * @see http://code.google.com/chrome/extensions/omnibox.html
 */
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


/**
 * @const
 * @see http://code.google.com/chrome/extensions/dev/contextMenus.html
 */
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


/**
 * @const
 * @see http://code.google.com/chrome/extensions/dev/cookies.html
 */
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


/** @const */
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
 * @param {string} id The id of an already installed extension.
 * @param {(Object|function(): void)=} opt_optionsOrCallback An optional
 *     uninstall options object or an optional callback function.
 * @param {function(): void=} opt_callback An optional callback function.
 */
chrome.management.uninstall =
    function(id, opt_optionsOrCallback, opt_callback) {};


/**
 * @param {(Object|function(): void)=} opt_optionsOrCallback An optional
 *     uninstall options object or an optional callback function.
 * @param {function(): void=} opt_callback An optional callback function.
 */
chrome.management.uninstallSelf =
    function(opt_optionsOrCallback, opt_callback) {};


/** @type {ChromeEvent} */
chrome.management.onDisabled;


/** @type {ChromeEvent} */
chrome.management.onEnabled;


/** @type {ChromeEvent} */
chrome.management.onInstalled;


/** @type {ChromeEvent} */
chrome.management.onUninstalled;


/**
 * @const
 * @see http://code.google.com/chrome/extensions/idle.html
 */
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
 * @const
 * @see http://code.google.com/chrome/extensions/tts.html
 */
chrome.tts = {};



/**
 * An event from the TTS engine to communicate the status of an utterance.
 * @constructor
 */
function TtsEvent() {}


/** @type {string} */
TtsEvent.prototype.type;


/** @type {number} */
TtsEvent.prototype.charIndex;


/** @type {string} */
TtsEvent.prototype.errorMessage;



/**
 * A description of a voice available for speech synthesis.
 * @constructor
 */
function TtsVoice() {}


/** @type {string} */
TtsVoice.prototype.voiceName;


/** @type {string} */
TtsVoice.prototype.lang;


/** @type {string} */
TtsVoice.prototype.gender;


/** @type {string} */
TtsVoice.prototype.extensionId;


/** @type {Array.<string>} */
TtsVoice.prototype.eventTypes;


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


/**
 * @const
 * @see http://code.google.com/chrome/extensions/ttsEngine.html
 */
chrome.ttsEngine = {};


/** @type {ChromeEvent} */
chrome.ttsEngine.onSpeak;


/** @type {ChromeEvent} */
chrome.ttsEngine.onStop;


/**
 * @const
 * @see http://code.google.com/chrome/extensions/contentSettings.html
 */
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


/**
 * @const
 * @see http://code.google.com/chrome/extensions/fileBrowserHandle.html
 */
chrome.fileBrowserHandle = {};


/** @type {ChromeEvent} */
chrome.fileBrowserHandle.onExecute;


/**
 * @const
 * @see http://code.google.com/chrome/extensions/history.html
 */
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


/**
 * @const
 * @see https://developer.chrome.com/apps/identity.html
 */
chrome.identity = {};


/**
 * @param {(chrome.identity.TokenDetails|function(string=): void)}
 *     detailsOrCallback Token options or a callback function if no options are
 *     specified.
 * @param {function(string=): void=} opt_callback A callback function if options
 *     are specified.
 */
chrome.identity.getAuthToken = function(detailsOrCallback, opt_callback) {};


/** @typedef {{interactive: (boolean|undefined)}} */
chrome.identity.TokenDetails;


/**
 * @param {chrome.identity.InvalidTokenDetails} details
 * @param {function(): void} callback
 */
chrome.identity.removeCachedAuthToken = function(details, callback) {};


/** @typedef {{token: string}} */
chrome.identity.InvalidTokenDetails;


/**
 * @param {chrome.identity.WebAuthFlowDetails} details
 * @param {function(string=): void} callback
 */
chrome.identity.launchWebAuthFlow = function(details, callback) {};


/** @typedef {{url: string, interactive: (boolean|undefined)}} */
chrome.identity.WebAuthFlowDetails;


/**
 * @const
 * @see http://code.google.com/chrome/extensions/input.ime.html
 */
chrome.input = {};


/** @const */
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


/**
 * namespace
 * @const
 */
chrome.mediaGalleries = {};


/**
 * @param {!FileSystem} mediaFileSystem The file system to get metadata for.
 * @return {!{name: string, galleryId: string, deviceId: (string|undefined),
 *     isRemovable: boolean, isMediaDevice: boolean}}
 */
chrome.mediaGalleries.getMediaFileSystemMetadata = function(mediaFileSystem) {};


/**
 * @param {?{interactive: (string|undefined)}} details Whether the request
 *     should be interactive if permissions haven't been granted yet.
 * @param {function(!Array.<!FileSystem>)} callback A success callback.
 */
chrome.mediaGalleries.getMediaFileSystems = function(details, callback) {};


/**
 * @const
 * @see http://code.google.com/chrome/extensions/pageCapture.html
 */
chrome.pageCapture = {};


/**
 * @param {Object.<string, number>} details Object with a 'tabId' (number) key.
 * @param {function(Blob=): void} callback Callback function.
 */
chrome.pageCapture.saveAsMHTML = function(details, callback) {};


/**
 * @const
 * @see http://code.google.com/chrome/extensions/permissions.html
 */
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


/**
 * @see http://developer.chrome.com/dev/extensions/power.html
 */
chrome.power = {};


/**
 * @param {string} level A string describing the degree to which power
 *     management should be disabled, should be either "system" or "display".
 */
chrome.power.requestKeepAwake = function(level) {};


/**
 * Releases a request previously made via requestKeepAwake().
 */
chrome.power.releaseKeepAwake = function() {};


/**
 * @const
 * @see http://code.google.com/chrome/extensions/privacy.html
 */
chrome.privacy = {};


/** @type {!Object.<string,!ChromeSetting>} */
chrome.privacy.network;


/** @type {!Object.<string,!ChromeSetting>} */
chrome.privacy.services;


/** @type {!Object.<string,!ChromeSetting>} */
chrome.privacy.websites;


/**
 * @const
 * @see http://code.google.com/chrome/extensions/proxy.html
 */
chrome.proxy = {};


/** @type {!Object.<string,!ChromeSetting>} */
chrome.proxy.settings;


/** @type {ChromeEvent} */
chrome.proxy.onProxyError;


/**
 * @const
 * @see http://developer.chrome.com/apps/socket.html
 */
chrome.socket = {};


/**
 * @constructor
 */
chrome.socket.CreateInfo = function() {};


/** @type {number} */
chrome.socket.CreateInfo.prototype.socketId;


/**
 * @constructor
 */
chrome.socket.ReadInfo = function() {};


/** @type {number} */
chrome.socket.ReadInfo.prototype.resultCode;


/** @type {!ArrayBuffer} */
chrome.socket.ReadInfo.prototype.data;


/**
 * @constructor
 */
chrome.socket.WriteInfo = function() {};


/** @type {number} */
chrome.socket.WriteInfo.prototype.bytesWritten;


/**
 * @constructor
 */
chrome.socket.RecvFromInfo = function() {};


/** @type {number} */
chrome.socket.RecvFromInfo.prototype.resultCode;


/** @type {!ArrayBuffer} */
chrome.socket.RecvFromInfo.prototype.data;


/** @type {string} */
chrome.socket.RecvFromInfo.prototype.address;


/** @type {number} */
chrome.socket.RecvFromInfo.prototype.port;


/**
 * @constructor
 */
chrome.socket.AcceptInfo = function() {};


/** @type {number} */
chrome.socket.AcceptInfo.prototype.resultCode;


/** @type {(number|undefined)} */
chrome.socket.AcceptInfo.prototype.socketId;


/**
 * @constructor
 */
chrome.socket.SocketInfo = function() {};


/** @type {string} */
chrome.socket.SocketInfo.prototype.socketType;


/** @type {boolean} */
chrome.socket.SocketInfo.prototype.connected;


/** @type {(string|undefined)} */
chrome.socket.SocketInfo.prototype.peerAddress;


/** @type {(number|undefined)} */
chrome.socket.SocketInfo.prototype.peerPort;


/** @type {(string|undefined)} */
chrome.socket.SocketInfo.prototype.localAddress;


/** @type {(number|undefined)} */
chrome.socket.SocketInfo.prototype.localPort;


/**
 * @constructor
 */
chrome.socket.NetworkAdapterInfo = function() {};


/** @type {string} */
chrome.socket.NetworkAdapterInfo.prototype.name;


/** @type {string} */
chrome.socket.NetworkAdapterInfo.prototype.address;


/**
 * @param {string} type The type of socket to create. Must be 'tcp' or 'udp'.
 * @param {(Object|function(!chrome.socket.CreateInfo))} optionsOrCallback The
 *     socket options or callback.
 * @param {function(!chrome.socket.CreateInfo)=} opt_callback Called when the
 *     socket has been created.
 */
chrome.socket.create = function(type, optionsOrCallback, opt_callback) {};


/**
 * @param {number} socketId The id of the socket to destroy.
 */
chrome.socket.destroy = function(socketId) {};


/**
 * @param {number} socketId The id of the socket.
 * @param {string} hostname The hostname or IP address of the remote machine.
 * @param {number} port The port of the remote machine.
 * @param {function(number)} callback Called when the connection attempt is
 *     complete.
 */
chrome.socket.connect = function(socketId, hostname, port, callback) {};


/**
 * @param {number} socketId The id of the socket.
 * @param {string} address The address of the local machine.
 * @param {number} port The port of the local machine.
 * @param {function(number)} callback Called when the bind attempt is complete.
 */
chrome.socket.bind = function(socketId, address, port, callback) {};


/**
 * @param {number} socketId The id of the socket to disconnect.
 */
chrome.socket.disconnect = function(socketId) {};


/**
 * @param {number} socketId The id of the socket to read from.
 * @param {(number|function(!chrome.socket.ReadInfo))} bufferSizeOrCallback The
 *     read buffer size or the callback.
 * @param {function(!chrome.socket.ReadInfo)=} opt_callback Called with data
 *     that was available to be read without blocking.
 */
chrome.socket.read = function(socketId, bufferSizeOrCallback, opt_callback) {};


/**
 * @param {number} socketId The id of the socket to write to.
 * @param {!ArrayBuffer} data The data to write.
 * @param {function(!chrome.socket.WriteInfo)} callback Called when the write
 *     operation completes without blocking or an error occurs.
 */
chrome.socket.write = function(socketId, data, callback) {};


/**
 * @param {number} socketId The id of the socket to read from.
 * @param {(number|function(!chrome.socket.RecvFromInfo))} bufferSizeOrCallback
 *     The read buffer size or the callback.
 * @param {function(!chrome.socket.RecvFromInfo)=} opt_callback Called with data
 *     that was available to be read without blocking.
 */
chrome.socket.recvFrom = function(socketId, bufferSizeOrCallback,
    opt_callback) {};


/**
 * @param {number} socketId The id of the socket to write to.
 * @param {!ArrayBuffer} data The data to write.
 * @param {string} address The address of the remote machine.
 * @param {number} port The port of the remote machine.
 * @param {function(!chrome.socket.WriteInfo)} callback Called when the write
 *     operation completes without blocking or an error occurs.
 */
chrome.socket.sendTo = function(socketId, data, address, port, callback) {};


/**
 * @param {number} socketId The id of the socket to listen on.
 * @param {string} address The address of the local machine to listen on. Use
 *     '0' to listen on all addresses.
 * @param {number} port The port of the local machine.
 * @param {(number|function(number))} backlogOrCallback The length of the
 *     socket's listen queue or the callback.
 * @param {function(number)=} opt_callback Called when the listen operation
 *     completes.
 */
chrome.socket.listen =
    function(socketId, address, port, backlogOrCallback, opt_callback) {};


/**
 * @param {number} socketId The id of the socket to accept a connection on.
 * @param {function(!chrome.socket.AcceptInfo)} callback Called when a new
 *     socket is accepted.
 */
chrome.socket.accept = function(socketId, callback) {};


/**
 * @param {number} socketId The id of the socket to listen on.
 * @param {boolean} enable If true, enable keep-alive functionality.
 * @param {(number|function(boolean))} delayOrCallback The delay in seconds
 *     between the last packet received and the first keepalive probe (default
 *     is 0) or the callback
 * @param {function(boolean)=} opt_callback Called when the setKeepAlive attempt
 *     is complete.
 */
chrome.socket.setKeepAlive = function(socketId, enable, delayOrCallback,
    opt_callback) {};


/**
 * @param {number} socketId The id of the socket to listen on.
 * @param {boolean} noDelay If true, disables Nagle's algorithm.
 * @param {function(boolean)} callback Called when the setNoDelay attempt is
 *     complete.
 */
chrome.socket.setNoDelay = function(socketId, noDelay, callback) {};


/**
 * @param {number} socketId The id of the socket.
 * @param {function(!chrome.socket.SocketInfo)} callback Called when the state
 *     is available.
 */
chrome.socket.getInfo = function(socketId, callback) {};


/**
 * @param {function(!Array.<!chrome.socket.NetworkAdapterInfo>)} callback Called
 *     when local adapter information is available.
 */
chrome.socket.getNetworkList = function(callback) {};


/**
 * @param {number} socketId The id of the socket.
 * @param {string} address The group address to join. Domain names are not
 *     supported.
 * @param {function(number)} callback Called when the join operation is done.
 */
chrome.socket.joinGroup = function(socketId, address, callback) {};


/**
 * @param {number} socketId The id of the socket.
 * @param {string} address The group address to leave. Domain names are not
 *     supported.
 * @param {function(number)} callback Called when the leave operation is done.
 */
chrome.socket.leaveGroup = function(socketId, address, callback) {};


/**
 * @param {number} socketId The id of the socket.
 * @param {number} ttl The time-to-live value.
 * @param {function(number)} callback Called when the configuration operation is
 *     done.
 */
chrome.socket.setMulticastTimeToLive = function(socketId, ttl, callback) {};


/**
 * @param {number} socketId The id of the socket.
 * @param {boolean} enabled True to enable loopback mode.
 * @param {function(number)} callback Called when the configuration operation is
 *     done.
 */
chrome.socket.setMulticastLoopbackMode = function(socketId, enabled,
    callback) {};


/**
 * @param {number} socketId The id of the socket.
 * @param {function(!Array.<string>)} callback Called with an array of string
 *     groups.
 */
chrome.socket.getJoinedGroups = function(socketId, callback) {};


/**
 * @const
 * @see http://code.google.com/chrome/extensions/storage.html
 */
chrome.storage = {};


/** @type {!StorageArea} */
chrome.storage.sync;


/** @type {!StorageArea} */
chrome.storage.local;


/** @type {!StorageChangeEvent} */
chrome.storage.onChanged;


/**
 * @const
 * @see http://code.google.com/chrome/extensions/types.html
 */
chrome.chromeSetting = {};


/** @type {ChromeEvent} */
chrome.chromeSetting.onChange;


/**
 * @const
 * @see http://code.google.com/chrome/extensions/webNavigation.html
 */
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


/**
 * @const
 * @see http://code.google.com/chrome/extensions/webRequest.html
 */
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


/** @type {number} */
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


// TODO: this returns boolean
/** @param {Function} callback */
ChromeEvent.prototype.hasListener = function(callback) {};


// TODO: this returns boolean, and doesn't take any parameters
/** @param {Function} callback */
ChromeEvent.prototype.hasListeners = function(callback) {};


/**
 * @see http://developer.chrome.com/extensions/pushMessaging.html
 * @const
 */
chrome.pushMessaging = {};


/**
 * @type {!chrome.pushMessaging.PushMessageEvent}
 */
chrome.pushMessaging.onMessage;


/**
 * @param {boolean|function(!chrome.pushMessaging.ChannelIdResult)}
 *     interactiveOrCallback Either a flag(optional), if set to true, user will
 *     be asked to log in if they are not already logged in, or, when he flag is
 *     not given, the callback.
 * @param {function(!chrome.pushMessaging.ChannelIdResult)=} opt_callback
 *     Callback.
 */
chrome.pushMessaging.getChannelId =
    function(interactiveOrCallback, opt_callback) {};



/**
 * Event whose listeners take a chrome.pushMessaging.Message parameter.
 * @constructor
 */
chrome.pushMessaging.PushMessageEvent = function() {};


/**
 * @param {function(!chrome.pushMessaging.Message): void} callback
 */
chrome.pushMessaging.PushMessageEvent.prototype.addListener =
    function(callback) {};


/**
 * @param {function(!chrome.pushMessaging.Message): void} callback
 */
chrome.pushMessaging.PushMessageEvent.prototype.removeListener =
    function(callback) {};


/**
 * @param {function(!chrome.pushMessaging.Message): void} callback
 * @return {boolean}
 */
chrome.pushMessaging.PushMessageEvent.prototype.hasListener =
    function(callback) {};


/**
 * @return {boolean}
 */
chrome.pushMessaging.PushMessageEvent.prototype.hasListeners = function() {};



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
 * Removes all items from storage.
 * @param {function(): void=} opt_callback Callback function.
 */
StorageArea.prototype.clear = function(opt_callback) {};


/**
 * @param {(string|!Array.<string>|!Object|null)=} opt_keys
 *    A single key to get, list of keys to get, or a dictionary
 *    specifying default values (see description of the
 *    object). An empty list or object will return an empty
 *    result object. Pass in null to get the entire contents of storage.
 * @param {function(Object)=} opt_callback Callback with storage items, or null
 *    on failure.
 */
StorageArea.prototype.get = function(opt_keys, opt_callback) {};


/**
 * @param {(string|!Array.<string>)} keys
 *    A single key or a list of keys for items to remove.
 * @param {function()=} opt_callback Callback.
 */
StorageArea.prototype.remove = function(keys, opt_callback) {};


/**
 * @param {!Object.<string>} keys
 *    Object specifying items to augment storage
 *    with. Values that cannot be serialized (functions, etc) will be ignored.
 * @param {function()=} opt_callback Callback.
 */
StorageArea.prototype.set = function(keys, opt_callback) { };


/**
 * @param {(string|!Array.<string>|null)=} opt_keys
 *    A single key or list of keys to get the total usage for. An empty list
 *    will return 0. Pass in null to get the total usage of all of storage.
 * @param {function(number)=} opt_callback
 *    Callback with the amount of space being used by storage.
 */
StorageArea.prototype.getBytesInUse = function(opt_keys, opt_callback) { };



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



/**
 * @see http://developer.chrome.com/extensions/pushMessaging.html#type-Message
 * @constructor
 */
chrome.pushMessaging.Message = function() {};


/**
 * @type {number}
 */
chrome.pushMessaging.Message.prototype.subchannelId;


/**
 * @type {string}
 */
chrome.pushMessaging.Message.prototype.payload;



/**
 * @see http://developer.chrome.com/extensions/pushMessaging.html#type-ChannelIdResult
 * @constructor
 */
chrome.pushMessaging.ChannelIdResult = function() {};


/**
 * @type {string}
 */
chrome.pushMessaging.ChannelIdResult.prototype.channelId;
