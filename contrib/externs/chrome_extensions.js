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
 * See http://developer.chrome.com/extensions/
 *
 * There are several problematic issues regarding Chrome extension APIs and
 * this externs files, including:
 * A. When to add packages to this file
 * B. Optional parameters
 * C. Pseudo-types
 * D. Events
 * E. Nullability
 * F. Private APIs
 * G. Enums
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
 * Some non-experimental APIs are still evolving or are not full documented. It
 * is still advantageous to include these in this file as doing so avoids a
 * proliferation of project-private externs files containing duplicated info. In
 * these cases, use comments to describe the situation.
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
 * is, they can't be instantiated by name. The extension APIs also pass
 * untyped objects (a bag of properties) to callbacks.
 *
 * The Chrome extension APIs include at least three different situations:
 *
 * 1. an object that must be created by an extension developer and passed
 *    into a Chrome extension API and for which there is no constructor.
 * 2. an instance of a type that is created inside the extension libraries
 *    and passed out to a callback/listener or returned by an extension API
 *    (the constructor implicity lives within the library).
 * 3. like #2, but a bag-of-properties object that is passed out to a
 *    callback/listener or returned by an extension API so there is no
 *    defined type.
 *
 * For #1, use a typedef so object literals and objects created via goog.object
 * are acceptable, for example, the Permissions type defined at
 * http://developer.chrome.com/extensions/permissions.html#type-Permissions
 * should be:
 *
 *   / **
 *     * at-typedef {?{
 *     *   permissions: (!Array.<string>|undefined),
 *     *   origins: (!Array.<string>|undefined)
 *     * }}
 *     * /
 *   chrome.permissions.Permissions;
 *
 * Using typedefs provides type-safety for the fields that are defined in
 * the object literal and also defined in the typedef. Note that typedefs define
 * a minimal interface and will not complain about extraneous (often
 * misspelled) fields.
 *
 * Also, typedefs of record types are non-nullable by default. The "{?{"
 * creates a nullable record-type typedef so ! has the same meaning in usages
 * as it does for real types.
 *
 * For #2, use a standard constructor, even though no constructor is provided
 * and extension writers will never instantiate an instance, as using a first
 * class type provides the strongest type checking. For example, see the Port
 * type defined at http://developer.chrome.com/apps/runtime.html#type-Port.
 * Always qualify the type name to reduce top-level pollution in this file:
 *
 *   Do:
 *        chrome.extension.Port = function() {}
 *   Don't:
 *        function Port() {}
 *
 * Note that, unfortunately, the actual Port class definition in this file
 * does not follow this recommendation.
 *
 * For #3, use {!Object}, that is, a bag of properites. This is a sad reality
 * given that the Chrome extensions do not document a real type. It is tempting
 * to define a real-type within this file and treat this situation as identical
 * to #2, but that means a new type is being defined in this file and developers
 * do not expect to find required new types in extension files.
 *
 * If a real type is declared here, then developers will need to incorporate
 * that type into the signature of their callback method and there will be
 * no indication from the docs that they need to do so.
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
 * F. Private APIs
 * Private Chrome APIs (such as those that end in "Private") should go at the
 * bottom of this file.
 *
 * G. Enums
 * The Chrome extension APIs define many enums that define a set of acceptable
 * strings, however, they do not reify those enum types, therefore, enum
 * parameters should be defined as {@code string}.
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
 * @constructor
 * @see http://developer.chrome.com/apps/app_runtime.html
 */
chrome.app.runtime.LaunchItem = function() {};


/** @type {!FileEntry} */
chrome.app.runtime.LaunchItem.prototype.entry;


/** @type {string} */
chrome.app.runtime.LaunchItem.prototype.type;



/**
 * @constructor
 * @see http://developer.chrome.com/apps/app_runtime.html
 */
chrome.app.runtime.LaunchData = function() {};


/** @type {string|undefined} */
chrome.app.runtime.LaunchData.prototype.id;


/** @type {!Array.<!chrome.app.runtime.LaunchItem>|undefined} */
chrome.app.runtime.LaunchData.prototype.items;


/** @type {string|undefined} */
chrome.app.runtime.LaunchData.prototype.url;


/** @type {string|undefined} */
chrome.app.runtime.LaunchData.prototype.referrerUrl;


/** @type {boolean|undefined} */
chrome.app.runtime.LaunchData.prototype.isKioskSession;



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
 * @see https://developer.chrome.com/apps/app_window#method-getAll
 * @return {!Array.<!chrome.app.window.AppWindow>}
 */
chrome.app.window.getAll = function() {};


/**
 * @see https://developer.chrome.com/apps/app_window#method-get
 * @param {string} id
 * @return {chrome.app.window.AppWindow}
 */
chrome.app.window.get = function(id) {};



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
 * @param {boolean=} opt_focus Should the window be focused? Defaults to true.
 * @see http://developer.chrome.com/apps/app.window.html#type-AppWindow
 */
chrome.app.window.AppWindow.prototype.show = function(opt_focus) {};


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


/**
 * @return {boolean}
 * @see http://developer.chrome.com/apps/app.window.html#type-AppWindow
 */
chrome.app.window.AppWindow.prototype.isAlwaysOnTop = function() {};


/**
 * @param {boolean} alwaysOnTop Set whether the window should stay above most
 *     other windows.
 * @see http://developer.chrome.com/apps/app.window.html#type-AppWindow
 */
chrome.app.window.AppWindow.prototype.setAlwaysOnTop = function(alwaysOnTop) {};


/** @type {!ChromeEvent} */
chrome.app.window.AppWindow.prototype.onBoundsChanged;


/** @type {!ChromeEvent} */
chrome.app.window.AppWindow.prototype.onClosed;


/** @type {!ChromeEvent} */
chrome.app.window.AppWindow.prototype.onFullscreened;


/** @type {!ChromeEvent} */
chrome.app.window.AppWindow.prototype.onMinimized;


/** @type {!ChromeEvent} */
chrome.app.window.AppWindow.prototype.onMaximized;


/** @type {!ChromeEvent} */
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
 *   state: (string|undefined),
 *   hidden: (boolean|undefined),
 *   resizable: (boolean|undefined),
 *   alwaysOnTop: (boolean|undefined),
 *   focused: (boolean|undefined)
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
 * @const
 * @see https://developer.chrome.com/apps/bluetooth
 */
chrome.bluetooth = function() {};



/**
 * @constructor
 * @see https://developer.chrome.com/apps/bluetooth#type-AdapterState
 */
chrome.bluetooth.AdapterState = function() {};


/** @type {string} */
chrome.bluetooth.AdapterState.prototype.address;


/** @type {string} */
chrome.bluetooth.AdapterState.prototype.name;


/** @type {boolean} */
chrome.bluetooth.AdapterState.prototype.powered;


/** @type {boolean} */
chrome.bluetooth.AdapterState.prototype.available;


/** @type {boolean} */
chrome.bluetooth.AdapterState.prototype.discovering;



/**
 * @constructor
 * @see https://developer.chrome.com/apps/bluetooth#type-Device
 */
chrome.bluetooth.Device = function() {};


/** @type {string} */
chrome.bluetooth.Device.prototype.address;


/** @type {string|undefined} */
chrome.bluetooth.Device.prototype.name;


/** @type {number|undefined} */
chrome.bluetooth.Device.prototype.deviceClass;


/** @type {string|undefined} */
chrome.bluetooth.Device.prototype.vendorIdSource;


/** @type {string|undefined} */
chrome.bluetooth.Device.prototype.vendorId;


/** @type {number|undefined} */
chrome.bluetooth.Device.prototype.productId;


/** @type {number|undefined} */
chrome.bluetooth.Device.prototype.deviceId;


/** @type {string|undefined} */
chrome.bluetooth.Device.prototype.type;


/** @type {boolean|undefined} */
chrome.bluetooth.Device.prototype.paired;


/** @type {boolean|undefined} */
chrome.bluetooth.Device.prototype.connected;


/** @type {!Array.<string>|undefined} */
chrome.bluetooth.Device.prototype.uuids;


/**
 * @param {function(!chrome.bluetooth.AdapterState)} callback
 * @see https://developer.chrome.com/apps/bluetooth#method-getAdapterState
 */
chrome.bluetooth.getAdapterState = function(callback) {};


/**
 * @param {string} deviceAddress
 * @param {function(!chrome.bluetooth.Device)} callback
 * @see https://developer.chrome.com/apps/bluetooth#method-getDevice
 */
chrome.bluetooth.getDevice = function(deviceAddress, callback) {};


/**
 * @param {function(!Array.<!chrome.bluetooth.Device>)} callback
 * @see https://developer.chrome.com/apps/bluetooth#method-getDevices
 */
chrome.bluetooth.getDevices = function(callback) {};


/**
 * @param {function()=} opt_callback
 * @see https://developer.chrome.com/apps/bluetooth#method-startDiscovery
 */
chrome.bluetooth.startDiscovery = function(opt_callback) {};


/**
 * @param {function()=} opt_callback
 * @see https://developer.chrome.com/apps/bluetooth#method-stopDiscovery
 */
chrome.bluetooth.stopDiscovery = function(opt_callback) {};



/**
 * Event whose listeners take an AdapaterState parameter.
 * @constructor
 */
chrome.bluetooth.AdapterStateEvent = function() {};


/** @param {function(!chrome.bluetooth.AdapterState): void} callback */
chrome.bluetooth.AdapterStateEvent.prototype.addListener =
    function(callback) {};


/** @param {function(!chrome.bluetooth.AdapterState): void} callback */
chrome.bluetooth.AdapterStateEvent.prototype.removeListener =
    function(callback) {};


/**
 * @param {function(!chrome.bluetooth.AdapterState): void} callback
 * @return {boolean}
 */
chrome.bluetooth.AdapterStateEvent.prototype.hasListener =
    function(callback) {};


/** @return {boolean} */
chrome.bluetooth.AdapterStateEvent.prototype.hasListeners = function() {};


/**
 * @type {!chrome.bluetooth.AdapterStateEvent}
 * @see https://developer.chrome.com/apps/bluetooth#event-onAdapterStateChanged
 */
chrome.bluetooth.onAdapterStateChanged;



/**
 * Event whose listeners take an Device parameter.
 * @constructor
 */
chrome.bluetooth.DeviceEvent = function() {};


/** @param {function(!chrome.bluetooth.Device): void} callback */
chrome.bluetooth.DeviceEvent.prototype.addListener = function(callback) {};


/** @param {function(!chrome.bluetooth.Device): void} callback */
chrome.bluetooth.DeviceEvent.prototype.removeListener = function(callback) {};


/**
 * @param {function(!chrome.bluetooth.Device): void} callback
 * @return {boolean}
 */
chrome.bluetooth.DeviceEvent.prototype.hasListener = function(callback) {};


/** @return {boolean} */
chrome.bluetooth.DeviceEvent.prototype.hasListeners = function() {};


/**
 * @type {!chrome.bluetooth.DeviceEvent}
 * @see https://developer.chrome.com/apps/bluetooth#event-onDeviceAdded
 */
chrome.bluetooth.onDeviceAdded;


/**
 * @type {!chrome.bluetooth.DeviceEvent}
 * @see https://developer.chrome.com/apps/bluetooth#event-onDeviceChanged
 */
chrome.bluetooth.onDeviceChanged;


/**
 * @type {!chrome.bluetooth.DeviceEvent}
 * @see https://developer.chrome.com/apps/bluetooth#event-onDeviceRemoved
 */
chrome.bluetooth.onDeviceRemoved;


/**
 * @const
 * @see https://developer.chrome.com/apps/bluetoothSocket
 */
chrome.bluetoothSocket = {};


/**
 * @typedef {{
 *   persistent: (boolean|undefined),
 *   name: (string|undefined),
 *   bufferSize: (number|undefined)
 * }}
 * @see https://developer.chrome.com/apps/bluetoothSocket#type-SocketProperties
 */
chrome.bluetoothSocket.SocketProperties;


/**
 * @typedef {{
 *   channel: (number|undefined),
 *   psm: (number|undefined),
 *   backlog: (number|undefined)
 * }}
 * @see https://developer.chrome.com/apps/bluetoothSocket#type-ListenOptions
 */
chrome.bluetoothSocket.ListenOptions;



/**
 * @constructor
 * @see https://developer.chrome.com/apps/bluetoothSocket#type-SocketInfo
 */
chrome.bluetoothSocket.SocketInfo = function() {};


/** @type {number} */
chrome.bluetoothSocket.SocketInfo.prototype.socketId;


/** @type {boolean} */
chrome.bluetoothSocket.SocketInfo.prototype.persistent;


/** @type {string|undefined} */
chrome.bluetoothSocket.SocketInfo.prototype.name;


/** @type {number|undefined} */
chrome.bluetoothSocket.SocketInfo.prototype.bufferSize;


/** @type {boolean} */
chrome.bluetoothSocket.SocketInfo.prototype.paused;


/** @type {boolean} */
chrome.bluetoothSocket.SocketInfo.prototype.connected;


/** @type {string|undefined} */
chrome.bluetoothSocket.SocketInfo.prototype.address;


/** @type {string|undefined} */
chrome.bluetoothSocket.SocketInfo.prototype.uuid;


/**
 * @param {!chrome.bluetoothSocket.SocketProperties|
 *     function(!{socketId: number})} propertiesOrCallback
 * @param {function(!{socketId: number})=} opt_callback
 * @see https://developer.chrome.com/apps/bluetoothSocket#method-create
 */
chrome.bluetoothSocket.create = function(propertiesOrCallback, opt_callback) {};


/**
 * @param {number} socketId
 * @param {!chrome.bluetoothSocket.SocketProperties} properties
 * @param {function()=} opt_callback
 * @see https://developer.chrome.com/apps/bluetoothSocket#method-update
 */
chrome.bluetoothSocket.update = function(socketId, properties, opt_callback) {};


/**
 * @param {number} socketId
 * @param {boolean} paused
 * @param {function()=} opt_callback
 * @see https://developer.chrome.com/apps/bluetoothSocket#method-setPaused
 */
chrome.bluetoothSocket.setPaused = function(socketId, paused, opt_callback) {};


/**
 * @param {number} socketId
 * @param {string} uuid
 * @param {!chrome.bluetoothSocket.ListenOptions|function()} optionsOrCallback
 * @param {function()=} opt_callback
 * @see https://developer.chrome.com/apps/bluetoothSocket#method-listenUsingRfcomm
 */
chrome.bluetoothSocket.listenUsingRfcomm =
    function(socketId, uuid, optionsOrCallback, opt_callback) {};


/**
 * @param {number} socketId
 * @param {string} uuid
 * @param {!chrome.bluetoothSocket.ListenOptions|function()} optionsOrCallback
 * @param {function()=} opt_callback
 * @see https://developer.chrome.com/apps/bluetoothSocket#method-listenUsingL2cap
 */
chrome.bluetoothSocket.listenUsingL2cap =
    function(socketId, uuid, optionsOrCallback, opt_callback) {};


/**
 * @param {number} socketId
 * @param {string} address
 * @param {string} uuid
 * @param {function()} callback
 * @see https://developer.chrome.com/apps/bluetoothSocket#method-connect
 */
chrome.bluetoothSocket.connect = function(socketId, address, uuid, callback) {};


/**
 * @param {number} socketId
 * @param {function()=} opt_callback
 * @see https://developer.chrome.com/apps/bluetoothSocket#method-disconnect
 */
chrome.bluetoothSocket.disconnect = function(socketId, opt_callback) {};


/**
 * @param {number} socketId
 * @param {function()=} opt_callback
 * @see https://developer.chrome.com/apps/bluetoothSocket#method-close
 */
chrome.bluetoothSocket.close = function(socketId, opt_callback) {};


/**
 * @param {number} socketId
 * @param {!ArrayBuffer} data
 * @param {function(number)=} opt_callback
 * @see https://developer.chrome.com/apps/bluetoothSocket#method-send
 */
chrome.bluetoothSocket.send = function(socketId, data, opt_callback) {};


/**
 * @param {number} socketId
 * @param {function(!chrome.bluetoothSocket.SocketInfo)} callback
 * @see https://developer.chrome.com/apps/bluetoothSocket#method-getInfo
 */
chrome.bluetoothSocket.getInfo = function(socketId, callback) {};


/**
 * @param {function(!Array.<!chrome.bluetoothSocket.SocketInfo>)} callback
 * @see https://developer.chrome.com/apps/bluetoothSocket#method-getSockets
 */
chrome.bluetoothSocket.getSockets = function(callback) {};



/**
 * @constructor
 * @see https://developer.chrome.com/apps/bluetoothSocket#event-onAccept
 */
chrome.bluetoothSocket.AcceptEventData = function() {};


/** @type {number} */
chrome.bluetoothSocket.AcceptEventData.prototype.socketId;


/** @type {number} */
chrome.bluetoothSocket.AcceptEventData.prototype.clientSocketId;



/**
 * Event whose listeners take a AcceptEventData parameter.
 * @constructor
 */
chrome.bluetoothSocket.AcceptEvent = function() {};


/**
 * @param {function(!chrome.bluetoothSocket.AcceptEventData): void} callback
 */
chrome.bluetoothSocket.AcceptEvent.prototype.addListener =
    function(callback) {};


/**
 * @param {function(!chrome.bluetoothSocket.AcceptEventData): void} callback
 */
chrome.bluetoothSocket.AcceptEvent.prototype.removeListener =
    function(callback) {};


/**
 * @param {function(!chrome.bluetoothSocket.AcceptEventData): void} callback
 * @return {boolean}
 */
chrome.bluetoothSocket.AcceptEvent.prototype.hasListener =
    function(callback) {};


/** @return {boolean} */
chrome.bluetoothSocket.AcceptEvent.prototype.hasListeners = function() {};


/** @type {!chrome.bluetoothSocket.AcceptEvent} */
chrome.bluetoothSocket.onAccept;



/**
 * @constructor
 * @see https://developer.chrome.com/apps/bluetoothSocket#event-onAcceptError
 */
chrome.bluetoothSocket.AcceptErrorEventData = function() {};


/** @type {number} */
chrome.bluetoothSocket.AcceptErrorEventData.prototype.socketId;


/** @type {string} */
chrome.bluetoothSocket.AcceptErrorEventData.prototype.errorMessage;


/** @type {string} */
chrome.bluetoothSocket.AcceptErrorEventData.prototype.error;



/**
 * Event whose listeners take a AcceptErrorEventData parameter.
 * @constructor
 */
chrome.bluetoothSocket.AcceptErrorEvent = function() {};


/**
 * @param {function(
 *     !chrome.bluetoothSocket.AcceptErrorEventData): void} callback
 */
chrome.bluetoothSocket.AcceptErrorEvent.prototype.addListener =
    function(callback) {};


/**
 * @param {function(
 *     !chrome.bluetoothSocket.AcceptErrorEventData): void} callback
 */
chrome.bluetoothSocket.AcceptErrorEvent.prototype.removeListener =
    function(callback) {};


/**
 * @param {function(
 *     !chrome.bluetoothSocket.AcceptErrorEventData): void} callback
 * @return {boolean}
 */
chrome.bluetoothSocket.AcceptErrorEvent.prototype.hasListener =
    function(callback) {};


/** @return {boolean} */
chrome.bluetoothSocket.AcceptErrorEvent.prototype.hasListeners =
    function() {};


/** @type {!chrome.bluetoothSocket.AcceptErrorEvent} */
chrome.bluetoothSocket.onAcceptError;



/**
 * @constructor
 * @see https://developer.chrome.com/apps/bluetoothSocket#event-onReceive
 */
chrome.bluetoothSocket.ReceiveEventData = function() {};


/** @type {number} */
chrome.bluetoothSocket.ReceiveEventData.prototype.socketId;


/** @type {!ArrayBuffer} */
chrome.bluetoothSocket.ReceiveEventData.prototype.data;



/**
 * Event whose listeners take a ReceiveEventData parameter.
 * @constructor
 */
chrome.bluetoothSocket.ReceiveEvent = function() {};


/**
 * @param {function(!chrome.bluetoothSocket.ReceiveEventData): void} callback
 */
chrome.bluetoothSocket.ReceiveEvent.prototype.addListener =
    function(callback) {};


/**
 * @param {function(!chrome.bluetoothSocket.ReceiveEventData): void} callback
 */
chrome.bluetoothSocket.ReceiveEvent.prototype.removeListener =
    function(callback) {};


/**
 * @param {function(!chrome.bluetoothSocket.ReceiveEventData): void} callback
 * @return {boolean}
 */
chrome.bluetoothSocket.ReceiveEvent.prototype.hasListener =
    function(callback) {};


/** @return {boolean} */
chrome.bluetoothSocket.ReceiveEvent.prototype.hasListeners = function() {};


/** @type {!chrome.bluetoothSocket.ReceiveEvent} */
chrome.bluetoothSocket.onReceive;



/**
 * @constructor
 * @see https://developer.chrome.com/apps/bluetoothSocket#event-onReceiveError
 */
chrome.bluetoothSocket.ReceiveErrorEventData = function() {};


/** @type {number} */
chrome.bluetoothSocket.ReceiveErrorEventData.prototype.socketId;


/** @type {string} */
chrome.bluetoothSocket.ReceiveErrorEventData.prototype.errorMessage;


/** @type {string} */
chrome.bluetoothSocket.ReceiveErrorEventData.prototype.error;



/**
 * Event whose listeners take a ReceiveErrorEventData parameter.
 * @constructor
 */
chrome.bluetoothSocket.ReceiveErrorEvent = function() {};


/**
 * @param {function(
 *     !chrome.bluetoothSocket.ReceiveErrorEventData): void} callback
 */
chrome.bluetoothSocket.ReceiveErrorEvent.prototype.addListener =
    function(callback) {};


/**
 * @param {function(
 *     !chrome.bluetoothSocket.ReceiveErrorEventData): void} callback
 */
chrome.bluetoothSocket.ReceiveErrorEvent.prototype.removeListener =
    function(callback) {};


/**
 * @param {function(
 *     !chrome.bluetoothSocket.ReceiveErrorEventData): void} callback
 * @return {boolean}
 */
chrome.bluetoothSocket.ReceiveErrorEvent.prototype.hasListener =
    function(callback) {};


/** @return {boolean} */
chrome.bluetoothSocket.ReceiveErrorEvent.prototype.hasListeners =
    function() {};


/** @type {!chrome.bluetoothSocket.ReceiveErrorEvent} */
chrome.bluetoothSocket.onReceiveError;


/**
 * @see http://developer.chrome.com/extensions/commands.html
 * @const
 */
chrome.commands = {};


/**
 * @param {function(Array.<string>): void} callback Callback function.
 */
chrome.commands.getAll = function(callback) {};


/** @type {!ChromeEvent} */
chrome.commands.onCommand;


/**
 * @see https://developer.chrome.com/apps/copresence
 * @const
 */
chrome.copresence = {};


/**
 * @typedef {?{
 *   lowPower: (boolean|undefined),
 *   onlyBroadcast: (boolean|undefined),
 *   onlyScan: (boolean|undefined),
 *   audible: (boolean|undefined)
 * }}
 * @see https://developer.chrome.com/apps/copresence#type-Strategy
 */
chrome.copresence.Strategy;


/**
 * @typedef {?{
 *   type: string,
 *   payload: ArrayBuffer
 * }}
 * @see https://developer.chrome.com/apps/copresence#type-Message
 */
chrome.copresence.Message;


/**
 * @typedef {?{
 *   onlyEarshot: (boolean|undefined)
 * }}
 * https://developer.chrome.com/apps/copresence#type-AccessPolicy
 */
chrome.copresence.AccessPolicy;


/**
 * @typedef {?{
 *   id: string,
 *   message: !chrome.copresence.Message,
 *   timeToLiveMillis: (number|undefined),
 *   policy: (!chrome.copresence.AccessPolicy|undefined),
 *   strategies: (!chrome.copresence.Strategy|undefined)
 * }}
 * @see https://developer.chrome.com/apps/copresence#type-PublishOperation
 */
chrome.copresence.PublishOperation;


/** @typedef {?{type: string}} */
chrome.copresence.SubscriptionFilter;


/**
 * @typedef {?{
 *   id: string,
 *   filter: !chrome.copresence.SubscriptionFilter,
 *   timeToLiveMillis: (number|undefined),
 *   strategies: (!chrome.copresence.Strategy|undefined)
 * }}
 * @see https://developer.chrome.com/apps/copresence#type-SubscribeOperation
 */
chrome.copresence.SubscribeOperation;


/**
 * @typedef {?{
 *   unpublishId: string
 * }}
 * @see https://developer.chrome.com/apps/copresence#type-UnpublishOperation
 */
chrome.copresence.UnpublishOperation;


/**
 * @typedef {?{
 *   unsubscribeId: string
 * }}
 * @see https://developer.chrome.com/apps/copresence#type-UnsubscribeOperation
 */
chrome.copresence.UnsubscribeOperation;


/**
 * @typedef {?{
 *   publish: (!chrome.copresence.PublishOperation|undefined),
 *   subscribe: (!chrome.copresence.SubscribeOperation|undefined),
 *   unpublish: (!chrome.copresence.UnpublishOperation|undefined),
 *   unsubscribe: (!chrome.copresence.UnsubscribeOperation|undefined)
 * }}
 * @see https://developer.chrome.com/apps/copresence#type-Operation
 */
chrome.copresence.Operation;


/**
 * @param {!Array.<!chrome.copresence.Operation>} operations
 * @param {function(string): void} callback
 * @see https://developer.chrome.com/apps/copresence#method-execute
 */
chrome.copresence.execute = function(operations, callback) {};



/**
 * Event whose listeners take a subscription id and received messages as a
 * parameter.
 * @constructor
 * @see https://developer.chrome.com/apps/copresence#event-onMessagesReceived
 */
chrome.copresence.MessagesReceivedEvent = function() {};


/**
 * @param {function(string, !Array.<!chrome.copresence.Message>): void} callback
 */
chrome.copresence.MessagesReceivedEvent.prototype.addListener =
    function(callback) {};


/**
 * @param {function(string, !Array.<!chrome.copresence.Message>): void} callback
 */
chrome.copresence.MessagesReceivedEvent.prototype.removeListener =
    function(callback) {};


/**
 * @param {function(string, !Array.<!chrome.copresence.Message>): void} callback
 * @return {boolean}
 */
chrome.copresence.MessagesReceivedEvent.prototype.hasListener =
    function(callback) {};


/** @return {boolean} */
chrome.copresence.MessagesReceivedEvent.prototype.hasListeners = function() {};


/**
 * @type {!chrome.copresence.MessagesReceivedEvent}
 * @see https://developer.chrome.com/apps/copresence#event-onMessagesReceived
 */
chrome.copresence.onMessagesReceived;


/**
 * @type {!ChromeStringEvent}
 * @see https://developer.chrome.com/apps/copresence#event-onStatusUpdated
 */
chrome.copresence.onStatusUpdated;


/**
 * @see https://developer.chrome.com/extensions/extension.html
 * @const
 */
chrome.extension = {};


/** @type {!Object|undefined} */
chrome.extension.lastError = {};


/**
 * @type {string|undefined}
 */
chrome.extension.lastError.message;


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


/** @type {!ChromeEvent} */
chrome.extension.onConnect;


/** @type {!ChromeEvent} */
chrome.extension.onConnectExternal;


/** @type {!ChromeEvent} */
chrome.extension.onMessage;


/** @type {!ChromeEvent} */
chrome.extension.onRequest;


/** @type {!ChromeEvent} */
chrome.extension.onRequestExternal;


/**
 * @see https://developer.chrome.com/extensions/runtime.html
 * @const
 */
chrome.runtime = {};


/** @type {!Object|undefined} */
chrome.runtime.lastError = {};


/**
 * @type {string|undefined}
 */
chrome.runtime.lastError.message;


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


/** @type {!Array.<(string|!Object)>} */
chrome.runtime.Manifest.prototype.permissions;



/**
 * Oauth2 info in the manifest.
 * See http://developer.chrome.com/apps/app_identity.html#update_manifest.
 * @constructor
 */
chrome.runtime.Manifest.Oauth2 = function() {};


/** @type {string} */
chrome.runtime.Manifest.Oauth2.prototype.client_id;


/**@type {!Array.<string>} */
chrome.runtime.Manifest.Oauth2.prototype.scopes;


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
 * @param {string} url This may be used to clean up server-side data, do
 *     analytics, and implement surveys. Maximum 255 characters.
 */
chrome.runtime.setUninstallUrl = function(url) {};


/**
 * Reloads the app or extension.
 */
chrome.runtime.reload = function() {};


/**
 * @param {function(string, !Object=): void} callback Called with "throttled",
 *     "no_update", or "update_available". If an update is available, the object
 *     contains more information about the available update.
 */
chrome.runtime.requestUpdateCheck = function(callback) {};


/**
 * Restart the ChromeOS device when the app runs in kiosk mode. Otherwise, it's
 * no-op.
 */
chrome.runtime.restart = function() {};


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
 * @see http://developer.chrome.com/extensions/runtime.html#method-connectNative
 * @param {string} application Name of the registered native messaging host to
 *     connect to, like 'com.google.your_product'.
 * @return {!Port} New port.
 */
chrome.runtime.connectNative = function(application) {};


/**
 * @param {string|*} extensionIdOrMessage Either the extensionId to send the
 *     message to, in which case the message is passed as the next arg, or the
 *     message itself.
 * @param {(*|Object|function(*): void)=} opt_messageOrOptsOrCallback
 *     One of:
 *     The message, if arg1 was the extensionId.
 *     The options for message sending, if arg1 was the message and this
 *     argument is not a function.
 *     The callback, if arg1 was the message and this argument is a function.
 * @param {(Object|function(*): void)=} opt_optsOrCallback
 *     Either the options for message sending, if arg2 was the message,
 *     or the callback.
 * @param {function(*): void=} opt_callback The callback function which
 *     takes a JSON response object sent by the handler of the request.
 */
chrome.runtime.sendMessage = function(
    extensionIdOrMessage, opt_messageOrOptsOrCallback, opt_optsOrCallback,
    opt_callback) {};


/**
 * @see http://developer.chrome.com/extensions/runtime.html#method-sendNativeMessage
 * @param {string} application Name of the registered native messaging host to
 *     connect to, like 'com.google.your_product'.
 * @param {Object} message The message that will be passed to the native
 *     messaging host.
 * @param {function(*)=} opt_callback Called with the response message sent by
 *     the native messaging host. If an error occurs while connecting to the
 *     native messaging host, the callback will be called with no arguments and
 *     chrome.runtime.lastError will be set to the error message.
 */
chrome.runtime.sendNativeMessage = function(
    application, message, opt_callback) {};


/**
 *
 * @param {function(!Object)} callback
 */
chrome.runtime.getPlatformInfo = function(callback) {};


/**
 * @param {function(!DirectoryEntry)} callback
 */
chrome.runtime.getPackageDirectoryEntry = function(callback) {};


/** @type {!chrome.runtime.PortEvent} */
chrome.runtime.onConnect;


/** @type {!chrome.runtime.PortEvent} */
chrome.runtime.onConnectExternal;


/** @type {!ChromeObjectEvent} */
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


/** @type {!ChromeObjectEvent} */
chrome.runtime.onUpdateAvailable;


/** @type {!ChromeStringEvent} */
chrome.runtime.onRestartRequired;



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
 * @see http://developer.chrome.com/dev/apps/runtime.html#event-onMessage
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
 * @see https://developer.chrome.com/extensions/tabs.html
 */
chrome.tabs = {};


/**
 * @typedef {?{
 *   code: (string|undefined),
 *   file: (string|undefined),
 *   allFrames: (boolean|undefined),
 *   matchAboutBlank: (boolean|undefined),
 *   runAt: (string|undefined)
 * }}
 */
chrome.tabs.InjectDetails;


/**
 * @see https://developer.chrome.com/extensions/tabs#method-captureVisibleTab
 * @param {number|!chrome.types.ImageDetails|function(string):void}
 *     windowIdOrOptionsOrCallback One of:
 *     The target window.
 *     An object defining details about the format and quality of an image, in
 *     which case the window defaults to the current window.
 *     A callback function which accepts the data URL string of a JPEG encoding
 *     of the visible area of the captured tab.
 * @param {(!chrome.types.ImageDetails|function(string):void)=}
 *     opt_optionsOrCallback Either an object defining details about the
 *     format and quality of an image, or a callback function which accepts the
 *     data URL string of a JPEG encoding of the visible area of the captured
 *     tab.
 * @param {function(string):void=} opt_callback A callback function which
 *     accepts the data URL string of a JPEG encoding of the visible area of the
 *     captured tab.
 */
chrome.tabs.captureVisibleTab = function(windowIdOrOptionsOrCallback,
    opt_optionsOrCallback, opt_callback) {};


/**
 * @param {number} tabId Tab Id.
 * @param {{name: (string|undefined)}=} connectInfo Info Object.
 */
chrome.tabs.connect = function(tabId, connectInfo) {};


/**
 * @typedef {?{
 *   windowId: (number|undefined),
 *   index: (number|undefined),
 *   url: (string|undefined),
 *   active: (boolean|undefined),
 *   pinned: (boolean|undefined),
 *   openerTabId: (number|undefined)
 * }}
 */
chrome.tabs.CreateProperties;


/**
 * @param {!chrome.tabs.CreateProperties} createProperties Info object.
 * @param {function(!Tab): void=} opt_callback The callback function.
 */
chrome.tabs.create = function(createProperties, opt_callback) {};


/**
 * @see https://developer.chrome.com/extensions/tabs#method-detectLanguage
 * @param {number|function(string): void} tabIdOrCallback The tab id, or a
 *     callback function that will be invoked with the language of the active
 *     tab in the current window.
 * @param {function(string): void=} opt_callback An optional callback function
 *     that will be invoked with the language of the tab specified as first
 *     argument.
 */
chrome.tabs.detectLanguage = function(tabIdOrCallback, opt_callback) {};


/**
 * @see https://developer.chrome.com/extensions/tabs#method-executeScript
 * @param {number|!chrome.tabs.InjectDetails} tabIdOrDetails
 *     Either the id of the tab in which to run the script, or an object
 *     containing the details of the script to run, in which case the script
 *     will be executed in the active tab of the current window.
 * @param {(!chrome.tabs.InjectDetails|function(!Array.<*>):void)=}
 *     opt_detailsOrCallback Either an object containing the details of the
 *     script to run, if the tab id was speficied as first argument, or a
 *     callback that will be invoked with the result of the execution of the
 *     script in every injected frame.
 * @param {function(!Array.<*>):void=} opt_callback A callback that will be
 *     invoked with the result of the execution of the script in every
 *     injected frame.
 */
chrome.tabs.executeScript = function(tabIdOrDetails, opt_detailsOrCallback,
    opt_callback) {};


/**
 * @param {number} tabId Tab id.
 * @param {function(!Tab): void} callback Callback.
 */
chrome.tabs.get = function(tabId, callback) {};


/**
 * Note (2014-05-21): Because this function is deprecated, the types of it's
 * parameters were not upgraded to make the first parameter optional and to mark
 * the Array and Tab in the callback as non-null.
 *
 * @param {number?} windowId Window id.
 * @param {function(Array.<Tab>): void} callback Callback.
 * @deprecated Please use tabs.query {windowId: windowId}.
 */
chrome.tabs.getAllInWindow = function(windowId, callback) {};


/**
 * @param {function(!Tab=): void} callback Callback.
 */
chrome.tabs.getCurrent = function(callback) {};


/**
 * Note (2014-05-21): Because this function is deprecated, the types of it's
 * parameters were not upgraded to make the first parameter optional and to mark
 * the Array and Tab in the callback as non-null.
 *
 * @param {number?} windowId Window id.
 * @param {function(Tab): void} callback Callback.
 * @deprecated Please use tabs.query({active: true}).
 */
chrome.tabs.getSelected = function(windowId, callback) {};


/**
 * @typedef {?{
 *   windowId: (number|undefined),
 *   tabs: (number|!Array.<number>)
 * }}
 */
chrome.tabs.HighlightInfo;


/**
 * @param {!chrome.tabs.HighlightInfo} highlightInfo
 * @param {function(!Window): void} callback Callback function invoked
 *    with each appropriate Window.
 */
chrome.tabs.highlight = function(highlightInfo, callback) {};


/**
 * @link https://developer.chrome.com/extensions/tabs#method-insertCSS
 * @param {number|!chrome.tabs.InjectDetails} tabIdOrDetails
 *     Either the id of the tab in which to run the script, or an object
 *     containing the details of the CSS to insert, in which case the script
 *     will be executed in the active tab of the current window.
 * @param {(!chrome.tabs.InjectDetails|function():void)=}
 *     opt_detailsOrCallback Either an object containing the details of the
 *     CSS to insert, if the tab id was speficied as first argument, or a
 *     callback that will be invoked after the CSS has been injected.
 * @param {function():void=} opt_callback A callback that will be invoked after
 *     the CSS has been injected.
 */
chrome.tabs.insertCSS = function(tabIdOrDetails, opt_detailsOrCallback,
    opt_callback) {};


/**
 * @typedef {?{
 *   windowId: (number|undefined),
 *   index: number
 * }}
 */
chrome.tabs.MoveProperties;


/**
 * @param {number|!Array.<number>} tabId Tab id or array of tab ids.
 * @param {!chrome.tabs.MoveProperties} moveProperties
 * @param {function((!Tab|!Array.<!Tab>)): void=} opt_callback Callback.
 */
chrome.tabs.move = function(tabId, moveProperties, opt_callback) {};


/**
 * @typedef {?{
 *   active: (boolean|undefined),
 *   pinned: (boolean|undefined),
 *   highlighted: (boolean|undefined),
 *   currentWindow: (boolean|undefined),
 *   lastFocusedWindow: (boolean|undefined),
 *   status: (string|undefined),
 *   title: (string|undefined),
 *   url: (string|undefined),
 *   windowId: (number|undefined),
 *   windowType: (string|undefined),
 *   index: (number|undefined)
 * }}
 */
chrome.tabs.QueryInfo;


/**
 * @param {!chrome.tabs.QueryInfo} queryInfo
 * @param {function(!Array.<!Tab>): void} callback Callback.
 */
chrome.tabs.query = function(queryInfo, callback) {};


/**
 * @see https://developer.chrome.com/extensions/tabs#method-query
 * @param {number} tabId The ID of the tab which is to be duplicated.
 * @param {(function(!Tab=):void)=} opt_callback A callback to be invoked with
 *     details about the duplicated tab.
 */
chrome.tabs.duplicate = function(tabId, opt_callback) {};


/**
 * @typedef {?{
 *   bypassCache: (boolean|undefined)
 * }}
 */
chrome.tabs.ReloadProperties;


/**
 * @see https://developer.chrome.com/extensions/tabs#method-reload
 * @param {(number|!chrome.tabs.ReloadProperties|function():void)=}
 *     opt_tabIdOrReloadPropertiesOrCallback One of:
 *     The ID of the tab to reload; defaults to the selected tab of the current
 *     window.
 *     An object specifying boolean flags to customize the reload operation.
 *     A callback to be invoked when the reload is complete.
 * @param {(!chrome.tabs.ReloadProperties|function():void)=}
 *     opt_reloadPropertiesOrCallback Either an object specifying boolean flags
 *     to customize the reload operation, or a callback to be invoked when the
 *     reload is complete, if no object needs to be specified.
 * @param {function():void=} opt_callback  A callback to be invoked when the
 *     reload is complete.
 */
chrome.tabs.reload = function(opt_tabIdOrReloadPropertiesOrCallback,
    opt_reloadPropertiesOrCallback, opt_callback) {};


/**
 * @param {number|!Array.<number>} tabIds A tab ID or an array of tab IDs.
 * @param {function(): void=} opt_callback Callback.
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
 * @deprecated Please use runtime.sendMessage.
 */
chrome.tabs.sendRequest = function(tabId, request, opt_callback) {};


/**
 * @typedef {?{
 *   url: (string|undefined),
 *   active: (boolean|undefined),
 *   highlighted: (boolean|undefined),
 *   pinned: (boolean|undefined),
 *   openerTabId: (number|undefined)
 * }}
 */
chrome.tabs.UpdateProperties;


/**
 * @see https://developer.chrome.com/extensions/tabs#method-update
 * @param {number|!chrome.tabs.UpdateProperties} tabIdOrUpdateProperties
 *     Either the id of the tab to update, or an object with new property
 *     values, in which case the selected tab of the current window will be
 *     updated.
 * @param {(!chrome.tabs.UpdateProperties|function(Tab):void)=}
 *     opt_updatePropertiesOrCallback Either an object with new property values,
 *     if the tabId was specified as first parameter, or an optional callback
 *     that will be invoked with information about the tab being updated.
 * @param {function(!Tab=): void=} opt_callback An optional callback that will
 *     be invoked with information about the tab being updated.
 */
chrome.tabs.update = function(tabIdOrUpdateProperties,
    opt_updatePropertiesOrCallback, opt_callback) {};


/**
 * @type {!ChromeEvent}
 * @deprecated Please use tabs.onActivated.
 */
chrome.tabs.onActiveChanged;


/** @type {!ChromeEvent} */
chrome.tabs.onActivated;


/** @type {!ChromeEvent} */
chrome.tabs.onAttached;


/** @type {!ChromeEvent} */
chrome.tabs.onCreated;


/** @type {!ChromeEvent} */
chrome.tabs.onDetached;


/**
 * @type {!ChromeEvent}
 * @deprecated Please use tabs.onHighlighted.
 */
chrome.tabs.onHighlightChanged;


/**
 * @type {!ChromeEvent}
 */
chrome.tabs.onHighlighted;


/** @type {!ChromeEvent} */
chrome.tabs.onMoved;


/** @type {!ChromeEvent} */
chrome.tabs.onRemoved;


/** @type {!ChromeEvent} */
chrome.tabs.onUpdated;


/** @type {!ChromeEvent} */
chrome.tabs.onReplaced;

// DEPRECATED:
// TODO(user): Remove once all usage has been confirmed to have ended.


/**
 * @type {!ChromeEvent}
 * @deprecated Please use tabs.onActivated.
 */
chrome.tabs.onSelectionChanged;


/**
 * @const
 * @see https://developer.chrome.com/extensions/windows.html
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


/** @type {!ChromeEvent} */
chrome.windows.onCreated;


/** @type {!ChromeEvent} */
chrome.windows.onFocusChanged;


/** @type {!ChromeEvent} */
chrome.windows.onRemoved;


/**
 * @see https://developer.chrome.com/extensions/windows.html#property-WINDOW_ID_NONE
 * @type {number}
 */
chrome.windows.WINDOW_ID_NONE;


/**
 * @see https://developer.chrome.com/extensions/windows.html#property-WINDOW_ID_CURRENT
 * @type {number}
 */
chrome.windows.WINDOW_ID_CURRENT;


/**
 * @const
 * @see https://developer.chrome.com/extensions/i18n.html
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
 * @return {string}
 */
chrome.i18n.getUILanguage = function() {};


/**
 * @const
 * @see https://developer.chrome.com/extensions/pageAction.html
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


/** @type {!ChromeEvent} */
chrome.pageAction.onClicked;


/**
 * @const
 */
chrome.browser = {};


/**
 * @param {{url: string}} details An object with a single 'url' key.
 * @param {function(): void} callback The callback function. If an error occurs
 * opening the URL, chrome.runtime.lastError will be set to the error message.
 */
chrome.browser.openTab = function(details, callback) {};


/**
 * @const
 * @see https://developer.chrome.com/extensions/browserAction.html
 */
chrome.browserAction = {};


/**
 * @typedef {?{
 *   tabId: (number|undefined)
 * }}
 */
chrome.browserAction.Tab;


/**
 * @typedef {Array<number>}
 * @see https://developer.chrome.com/extensions/browserAction#type-ColorArray
 */
chrome.browserAction.ColorArray;


/**
 * @typedef {{
 *   imageData: (!ImageData|!Object.<number, !ImageData>|undefined),
 *   path: (string|!Object.<number, string>|undefined),
 *   tabId: (number|undefined)
 * }}
 */
chrome.browserAction.SetIconImageData;


/**
 * @param {{
 *   title: string,
 *   tabId: (number|undefined)
 * }} details
 * @see https://developer.chrome.com/extensions/browserAction#method-setTitle
 */
chrome.browserAction.setTitle = function(details) {};


/**
 * @param {!chrome.browserAction.Tab} details
 * @param {function(string): void} callback
 * @see https://developer.chrome.com/extensions/browserAction#method-getTitle
 */
chrome.browserAction.getTitle = function(details, callback) {};


/**
 * @param {!chrome.browserAction.SetIconImageData} details
 * @param {function(): void=} opt_callback
 * @see https://developer.chrome.com/extensions/browserAction#method-setIcon
 */
chrome.browserAction.setIcon = function(details, opt_callback) {};


/**
 * @param {{
 *   tabId: (number|undefined),
 *   popup: string
 * }} details
 * @see https://developer.chrome.com/extensions/browserAction#method-setPopup
 */
chrome.browserAction.setPopup = function(details) {};


/**
 * @param {!chrome.browserAction.Tab} details
 * @param {function(string): void} callback
 * @see https://developer.chrome.com/extensions/browserAction#method-getPopup
 */
chrome.browserAction.getPopup = function(details, callback) {};


/**
 * @param {{
 *   text: string,
 *   tabId: (number|undefined)
 * }} details
 * @see https://developer.chrome.com/extensions/browserAction#method-setBadgeText
 */
chrome.browserAction.setBadgeText = function(details) {};


/**
 * @param {!chrome.browserAction.Tab} details
 * @param {function(string): void} callback
 * @see https://developer.chrome.com/extensions/browserAction#method-getBadgeText
 */
chrome.browserAction.getBadgeText = function(details, callback) {};


/**
 * @param {{
 *   color: (string|chrome.browserAction.ColorArray),
 *   tabId: (number|undefined)
 * }} details
 * @see https://developer.chrome.com/extensions/browserAction#method-setBadgeBackgroundColor
 */
chrome.browserAction.setBadgeBackgroundColor = function(details) {};


/**
 * @param {!chrome.browserAction.Tab} details
 * @param {function(chrome.browserAction.ColorArray): void} callback
 * @see https://developer.chrome.com/extensions/browserAction#method-getBadgeBackgroundColor
 */
chrome.browserAction.getBadgeBackgroundColor = function(details, callback) {};


/**
 * @param {number=} opt_tabId
 * @see https://developer.chrome.com/extensions/browserAction#method-enable
 */
chrome.browserAction.enable = function(opt_tabId) {};


/**
 * @param {number=} opt_tabId
 * @see https://developer.chrome.com/extensions/browserAction#method-disable
 */
chrome.browserAction.disable = function(opt_tabId) {};


/**
 * @constructor
 */
chrome.browserAction.BrowserActionTabEvent = function() {};


/**
 * @param {function(!Tab): void} callback
 */
chrome.browserAction.BrowserActionTabEvent.prototype.addListener =
    function(callback) {};


/**
 * @param {function(!Tab): void} callback
 */
chrome.browserAction.BrowserActionTabEvent.prototype.removeListener =
    function(callback) {};


/**
 * @param {function(!Tab): void} callback
 * @return {boolean}
 */
chrome.browserAction.BrowserActionTabEvent.prototype.hasListener =
    function(callback) {};


/** @return {boolean} */
chrome.browserAction.BrowserActionTabEvent.prototype.hasListeners =
    function() {};


/**
 * @type {!chrome.browserAction.BrowserActionTabEvent}
 * @see https://developer.chrome.com/extensions/browserAction#event-onClicked
 */
chrome.browserAction.onClicked;


/**
 * @const
 * @see https://developer.chrome.com/extensions/bookmarks.html
 */
chrome.bookmarks = {};


/**
 * @typedef {?{
 *   parentId: (string|undefined),
 *   index: (number|undefined),
 *   url: (string|undefined),
 *   title: (string|undefined)
 * }}
 * @see https://developer.chrome.com/extensions/bookmarks#method-create
 */
chrome.bookmarks.CreateDetails;


/**
 * @typedef {?{
 *   query: (string|undefined),
 *   url: (string|undefined),
 *   title: (string|undefined)
 * }}
 * @see https://developer.chrome.com/extensions/bookmarks#method-search
 */
chrome.bookmarks.SearchDetails;


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
 * @param {function(Array.<BookmarkTreeNode>): void} callback The
 *     callback function which accepts an array of BookmarkTreeNode.
 * @return {Array.<BookmarkTreeNode>}
 */
chrome.bookmarks.getTree = function(callback) {};


/**
 * @param {string} id The ID of the root of the subtree to retrieve.
 * @param {function(Array.<BookmarkTreeNode>): void} callback The
 *     callback function which accepts an array of BookmarkTreeNode.
 * @return {Array.<BookmarkTreeNode>}
 */
chrome.bookmarks.getSubTree = function(id, callback) {};


/**
 * @param {string|!chrome.bookmarks.SearchDetails} query
 * @param {function(Array.<BookmarkTreeNode>): void} callback
 * @return {Array.<BookmarkTreeNode>}
 */
chrome.bookmarks.search = function(query, callback) {};


/**
 * @param {chrome.bookmarks.CreateDetails} bookmark
 * @param {function(BookmarkTreeNode): void=} opt_callback The
 *     callback function which accepts a BookmarkTreeNode object.
 */
chrome.bookmarks.create = function(bookmark, opt_callback) {};


/**
 * @param {string} id
 * @param {Object} destination An object which has optional 'parentId' and
 *     optional 'index'.
 * @param {function(BookmarkTreeNode): void=} opt_callback
 *     The callback function which accepts a BookmarkTreeNode object.
 */
chrome.bookmarks.move = function(id, destination, opt_callback) {};


/**
 * @param {string} id
 * @param {Object} changes An object which may have 'title' as a key.
 * @param {function(BookmarkTreeNode): void=} opt_callback The
 *     callback function which accepts a BookmarkTreeNode object.
 */
chrome.bookmarks.update = function(id, changes, opt_callback) {};


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
 * @param {function(): void=} opt_callback
 */
chrome.bookmarks.import = function(opt_callback) {};


/**
 * @param {function(): void=} opt_callback
 */
chrome.bookmarks.export = function(opt_callback) {};


/** @type {!ChromeEvent} */
chrome.bookmarks.onChanged;


/** @type {!ChromeEvent} */
chrome.bookmarks.onChildrenReordered;


/** @type {!ChromeEvent} */
chrome.bookmarks.onCreated;


/** @type {!ChromeEvent} */
chrome.bookmarks.onImportBegan;


/** @type {!ChromeEvent} */
chrome.bookmarks.onImportEnded;


/** @type {!ChromeEvent} */
chrome.bookmarks.onMoved;


/** @type {!ChromeEvent} */
chrome.bookmarks.onRemoved;


/**
 * @typedef {?{
 *   content: string,
 *   description: string
 * }}
 */
var SuggestResult;


/**
 * @const
 * @see https://developer.chrome.com/extensions/omnibox.html
 */
chrome.omnibox = {};



/** @constructor */
chrome.omnibox.InputChangedEvent = function() {};


/**
 * @param {function(string, function(!Array.<!SuggestResult>)): void} callback
 */
chrome.omnibox.InputChangedEvent.prototype.addListener = function(callback) {};


/**
 * @param {function(string, function(!Array.<!SuggestResult>)): void} callback
 */
chrome.omnibox.InputChangedEvent.prototype.removeListener =
    function(callback) {};


/**
 * @param {function(string, function(!Array.<!SuggestResult>)): void} callback
 * @return {boolean}
 */
chrome.omnibox.InputChangedEvent.prototype.hasListener = function(callback) {};


/** @return {boolean} */
chrome.omnibox.InputChangedEvent.prototype.hasListeners = function() {};



/** @constructor */
chrome.omnibox.InputEnteredEvent = function() {};


/** @param {function(string, string): void} callback */
chrome.omnibox.InputEnteredEvent.prototype.addListener = function(callback) {};


/** @param {function(string, string): void} callback */
chrome.omnibox.InputEnteredEvent.prototype.removeListener =
    function(callback) {};


/**
 * @param {function(string, string): void} callback
 * @return {boolean}
 */
chrome.omnibox.InputEnteredEvent.prototype.hasListener = function(callback) {};


/** @return {boolean} */
chrome.omnibox.InputEnteredEvent.prototype.hasListeners = function() {};


/**
 * @param {{description: string}} suggestion A partial SuggestResult object.
 */
chrome.omnibox.setDefaultSuggestion = function(suggestion) {};


/** @type {!ChromeEvent} */
chrome.omnibox.onInputCancelled;


/** @type {!chrome.omnibox.InputChangedEvent} */
chrome.omnibox.onInputChanged;


/** @type {!chrome.omnibox.InputEnteredEvent} */
chrome.omnibox.onInputEntered;


/** @type {!ChromeEvent} */
chrome.omnibox.onInputStarted;


/**
 * @const
 * @see https://developer.chrome.com/extensions/dev/contextMenus.html
 */
chrome.contextMenus = {};


/**
 * @typedef {?{
 *   type: (string|undefined),
 *   id: (string|undefined),
 *   title: (string|undefined),
 *   checked: (boolean|undefined),
 *   contexts: (!Array.<string>|undefined),
 *   onclick: (function(!Object, !Tab)|undefined),
 *   parentId: (number|string|undefined),
 *   documentUrlPatterns: (!Array.<string>|undefined),
 *   targetUrlPatterns: (!Array.<string>|undefined),
 *   enabled: (boolean|undefined)
 * }}
 * @see https://developer.chrome.com/extensions/contextMenus#method-create
 */
chrome.contextMenus.CreateProperties;


/**
 * @typedef {?{
 *   type: (string|undefined),
 *   title: (string|undefined),
 *   checked: (boolean|undefined),
 *   contexts: (!Array.<string>|undefined),
 *   onclick: (function(!Object, !Tab)|undefined),
 *   parentId: (number|string|undefined),
 *   documentUrlPatterns: (!Array.<string>|undefined),
 *   targetUrlPatterns: (!Array.<string>|undefined),
 *   enabled: (boolean|undefined)
 * }}
 * @see https://developer.chrome.com/extensions/contextMenus#method-update
 */
chrome.contextMenus.UpdateProperties;


/**
 * @param {!chrome.contextMenus.CreateProperties} createProperties
 * @param {function()=} opt_callback
 * @return {(number|string)} The id of the newly created window.
 * @see https://developer.chrome.com/extensions/contextMenus#method-create
 */
chrome.contextMenus.create = function(createProperties, opt_callback) {};


/**
 * @param {(number|string)} id
 * @param {!chrome.contextMenus.UpdateProperties} updateProperties
 * @param {function()=} opt_callback
 * @see https://developer.chrome.com/extensions/contextMenus#method-update
 */
chrome.contextMenus.update = function(id, updateProperties, opt_callback) {};


/**
 * @param {(number|string)} menuItemId
 * @param {function()=} opt_callback
 * @see https://developer.chrome.com/extensions/contextMenus#method-remove
 */
chrome.contextMenus.remove = function(menuItemId, opt_callback) {};


/**
 * @param {function()=} opt_callback
 * @see https://developer.chrome.com/extensions/contextMenus#method-removeAll
 */
chrome.contextMenus.removeAll = function(opt_callback) {};


/**
 * @interface
 * @see https://developer.chrome.com/extensions/contextMenus#event-onClicked
 */
chrome.contextMenus.ClickedEvent = function() {};


/**
 * @param {function(!Object, !Tab=)} callback
 */
chrome.contextMenus.ClickedEvent.prototype.addListener = function(callback) {};


/**
 * @param {function(!Object, !Tab=)} callback
 */
chrome.contextMenus.ClickedEvent.prototype.removeListener =
    function(callback) {};


/**
 * @param {function(!Object, !Tab=)} callback
 * @return {boolean}
 */
chrome.contextMenus.ClickedEvent.prototype.hasListener = function(callback) {};


/**
 * @return {boolean}
 */
chrome.contextMenus.ClickedEvent.prototype.hasListeners = function() {};


/**
 * @type {!chrome.contextMenus.ClickedEvent}
 * @see https://developer.chrome.com/extensions/contextMenus#event-onClicked
 */
chrome.contextMenus.onClicked;


/**
 * @const
 * @see https://developer.chrome.com/extensions/dev/cookies.html
 */
chrome.cookies = {};


/**
 * This typedef is used for the parameters to chrome.cookies.get,
 * chrome.cookies.remove, and for the parameter to remove's callback. These uses
 * all identify a single cookie uniquely without specifying its content, and the
 * objects are identical except for the the storeId being optional vs required.
 * If greater divergence occurs, then going to two typedefs is recommended.
 *
 * @typedef {?{
 *   url: string,
 *   name: string,
 *   storeId: (string|undefined)
 * }}
 */
chrome.cookies.CookieIdentifier;


/**
 * @param {!chrome.cookies.CookieIdentifier} details
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
 * @param {!chrome.cookies.CookieIdentifier} details
 * @param {function(chrome.cookies.CookieIdentifier): void=} opt_callback If
 *     removal failed for any reason, the parameter will be "null", and
 *     "chrome.runtime.lastError" will be set.
 */
chrome.cookies.remove = function(details, opt_callback) {};


/**
 * @typedef {?{
 *   url: string,
 *   name: (string|undefined),
 *   value: (string|undefined),
 *   domain: (string|undefined),
 *   path: (string|undefined),
 *   secure: (boolean|undefined),
 *   httpOnly: (boolean|undefined),
 *   expirationDate: (number|undefined),
 *   storeId: (string|undefined)
 * }}
 */
chrome.cookies.CookieSetDetails;


/**
 * @param {!chrome.cookies.CookieSetDetails} details
 * @param {function(Cookie): void=} opt_callback If setting failed for any
 *    reason, the parameter will be "null", and "chrome.runtime.lastError" will
 *    be set.
 */
chrome.cookies.set = function(details, opt_callback) {};


/**
 * @see https://developer.chrome.com/extensions/cookies.html#event-onChanged
 * @type {!ChromeEvent}
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
 * @typedef {?{
 *   showConfirmDialog: (boolean|undefined)
 * }}
 */
chrome.management.InstallOptions;


/**
 * @param {string} id
 * @param {function(!ExtensionInfo): void=} opt_callback Optional callback
 *     function.
 */
chrome.management.get = function(id, opt_callback) {};


/**
 * @param {function(!Array.<!ExtensionInfo>): void=} opt_callback Optional
 *     callback function.
 * @return {!Array.<!ExtensionInfo>}
 */
chrome.management.getAll = function(opt_callback) {};


/**
 * @param {string} id The id of an already installed extension.
 * @param {function(!Array.<string>)=} opt_callback Optional callback function.
 */
chrome.management.getPermissionWarningsById = function(id, opt_callback) {};


/**
 * @param {string} manifestStr Extension's manifest JSON string.
 * @param {function(!Array.<string>)=} opt_callback Optional callback function.
 */
chrome.management.getPermissionWarningsByManifest =
    function(manifestStr, opt_callback) {};


/**
 * @param {string} id The id of an already installed extension.
 * @param {function(): void=} opt_callback Optional callback function.
 */
chrome.management.launchApp = function(id, opt_callback) {};


/**
 * @param {string} id The id of an already installed extension.
 * @param {boolean} enabled Whether this item should be enabled.
 * @param {function(): void=} opt_callback Optional callback function.
 */
chrome.management.setEnabled = function(id, enabled, opt_callback) {};


/**
 * @param {string} id The id of an already installed extension.
 * @param {(!chrome.management.InstallOptions|function(): void)=}
 *     opt_optionsOrCallback An optional uninstall options object or an optional
 *     callback function.
 * @param {function(): void=} opt_callback Optional callback function.
 */
chrome.management.uninstall =
    function(id, opt_optionsOrCallback, opt_callback) {};


/**
 * @param {(!chrome.management.InstallOptions|function(): void)=}
 *     opt_optionsOrCallback An optional uninstall options object or an optional
 *     callback function.
 * @param {function(): void=} opt_callback An optional callback function.
 */
chrome.management.uninstallSelf =
    function(opt_optionsOrCallback, opt_callback) {};


/**
 * @param {string} id The id of an already installed extension.
 * @param {function(): void=} opt_callback Optional callback function.
 */
chrome.management.createAppShortcut = function(id, opt_callback) {};


/**
 * @param {string} id The id of an already installed extension.
 * @param {string} launchType The LaunchType enum value to set. Make sure this
 *     value is in ExtensionInfo.availableLaunchTypes because the available
 *     launch types vary on different platforms and configurations.
 * @param {function(): void=} opt_callback Optional callback function.
 */
chrome.management.setLaunchType = function(id, launchType, opt_callback) {};


/**
 * @param {string} url The URL of a web page. The scheme of the URL can only be
 *     "http" or "https".
 * @param {string} title The title of the generated app.
 * @param {function(!ExtensionInfo): void=} opt_callback Optional callback
 *     function.
 */
chrome.management.generateAppForLink = function(url, title, opt_callback) {};


/** @type {!ChromeExtensionInfoEvent} */
chrome.management.onDisabled;


/** @type {!ChromeExtensionInfoEvent} */
chrome.management.onEnabled;


/** @type {!ChromeExtensionInfoEvent} */
chrome.management.onInstalled;


/** @type {!ChromeStringEvent} */
chrome.management.onUninstalled;


/**
 * @const
 * @see https://developer.chrome.com/extensions/idle.html
 */
chrome.idle = {};


/**
 * @param {number} thresholdSeconds Threshold in seconds, used to determine
 *     when a machine is in the idle state.
 * @param {function(string): void} callback Callback to handle the state.
 */
chrome.idle.queryState = function(thresholdSeconds, callback) {};


/**
 * @param {number} intervalInSeconds Threshold, in seconds, used to determine
 *    when the system is in an idle state.
 */
chrome.idle.setDetectionInterval = function(intervalInSeconds) {};


/** @type {!ChromeEvent} */
chrome.idle.onStateChanged;


/**
 * Chrome Text-to-Speech API.
 * @const
 * @see https://developer.chrome.com/extensions/tts.html
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
 * @see https://developer.chrome.com/extensions/ttsEngine.html
 */
chrome.ttsEngine = {};


/** @type {!ChromeEvent} */
chrome.ttsEngine.onSpeak;


/** @type {!ChromeEvent} */
chrome.ttsEngine.onStop;


/**
 * @const
 * @see https://developer.chrome.com/extensions/contentSettings.html
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
 * @see https://developer.chrome.com/extensions/fileBrowserHandler
 */
chrome.fileBrowserHandler = {};


/**
 * @typedef {?{
 *   suggestedName: string,
 *   allowedFileExtensions: (!Array.<string>|undefined)
 * }}
 */
chrome.fileBrowserHandler.SelectFileSelectionParams;


/**
 * Prompts user to select file path under which file should be saved.
 * @see https://developer.chrome.com/extensions/fileBrowserHandler#method-selectFile
 * @param {!chrome.fileBrowserHandler.SelectFileSelectionParams} selectionParams
 *     Parameters that will be used while selecting the file.
 * @param {function(!Object)} callback Function called upon completion.
 */
chrome.fileBrowserHandler.selectFile = function(selectionParams, callback) {};


/**
 * @interface
 * @see https://developer.chrome.com/extensions/fileBrowserHandler#event-onExecute
 */
chrome.fileBrowserHandler.ExecuteEvent = function() {};


/**
 * @param {function(string, !FileHandlerExecuteEventDetails)} callback
 */
chrome.fileBrowserHandler.ExecuteEvent.prototype.addListener = function(
    callback) {};


/**
 * @param {function(string, !FileHandlerExecuteEventDetails)} callback
 */
chrome.fileBrowserHandler.ExecuteEvent.prototype.removeListener = function(
    callback) {};


/**
 * @param {function(string, !FileHandlerExecuteEventDetails)} callback
 * @return {boolean}
 */
chrome.fileBrowserHandler.ExecuteEvent.prototype.hasListener = function(
    callback) {};


/**
 * @return {boolean}
 */
chrome.fileBrowserHandler.ExecuteEvent.prototype.hasListeners = function() {};


/**
 * Fired when file system action is executed from ChromeOS file browser.
 * @see https://developer.chrome.com/extensions/fileBrowserHandler#event-onExecute
 * @type {!chrome.fileBrowserHandler.ExecuteEvent}
 */
chrome.fileBrowserHandler.onExecute;


/**
 * @const
 * @see https://developer.chrome.com/extensions/gcm
 */
chrome.gcm = {};


/**
 * @see https://developer.chrome.com/extensions/gcm#property-MAX_MESSAGE_SIZE
 * @type {number}
 */
chrome.gcm.MAX_MESSAGE_SIZE;


/**
 * Registers the application with GCM. The registration ID will be returned by
 * the callback. If register is called again with the same list of senderIds,
 * the same registration ID will be returned.
 * @see https://developer.chrome.com/extensions/gcm#method-register
 * @param {!Array.<string>} senderIds A list of server IDs that are allowed to
 *     send messages to the application.
 * @param {function(string): void} callback Function called when
 *     registration completes with registration ID as argument.
 */
chrome.gcm.register = function(senderIds, callback) {};


/**
 * Unregisters the application from GCM.
 * @see https://developer.chrome.com/extensions/gcm#method-unregister
 * @param {function(): void} callback Called when unregistration is done.
 */
chrome.gcm.unregister = function(callback) {};


/**
 * Sends an upstream message using GCM.
 * @see https://developer.chrome.com/extensions/gcm#method-send
 * @param {!chrome.gcm.Message} message Message to be sent.
 * @param {function(string): void} callback Called with message ID.
 */
chrome.gcm.send = function(message, callback) {};


/**
 * Outgoing message.
 * @typedef {?{
 *   destinationId: string,
 *   messageId: string,
 *   timeToLive: (number|undefined),
 *   data: !Object.<string, string>
 * }}
 */
chrome.gcm.Message;


/**
 * An event, fired when a message is received through GCM.
 * @see https://developer.chrome.com/extensions/gcm#event-onMessage
 * @type {!chrome.gcm.OnMessageEvent}
 */
chrome.gcm.onMessage;


/**
 * An event, fired when GCM server had to delete messages to the application
 * from its queue in order to manage its size.
 * @see https://developer.chrome.com/extensions/gcm#event-onMessagesDeleted
 * @type {!ChromeEvent}
 */
chrome.gcm.onMessagesDeleted;


/**
 * An event indicating problems with sending messages.
 * @see https://developer.chrome.com/extensions/gcm#event-onSendError
 * @type {!chrome.gcm.OnSendErrorEvent}
 */
chrome.gcm.onSendError;



/**
 * @constructor
 */
chrome.gcm.OnMessageEvent = function() {};


/**
 * @param {function(!Object): void} callback Callback.
 */
chrome.gcm.OnMessageEvent.prototype.addListener = function(callback) {};


/**
 * @param {function(!Object): void} callback Callback.
 */
chrome.gcm.OnMessageEvent.prototype.removeListener = function(callback) {};


/**
 * @param {function(!Object): void} callback Callback.
 * @return {boolean}
 */
chrome.gcm.OnMessageEvent.prototype.hasListener = function(callback) {};


/**
 * @return {boolean}
 */
chrome.gcm.OnMessageEvent.prototype.hasListeners = function() {};



/**
 * @constructor
 */
chrome.gcm.OnSendErrorEvent = function() {};


/**
 * @param {function(!Object): void} callback Callback.
 */
chrome.gcm.OnSendErrorEvent.prototype.addListener = function(callback) {};


/**
 * @param {function(!Object): void} callback Callback.
 */
chrome.gcm.OnSendErrorEvent.prototype.removeListener = function(callback) {};


/**
 * @param {function(!Object): void} callback Callback.
 * @return {boolean}
 */
chrome.gcm.OnSendErrorEvent.prototype.hasListener = function(callback) {};


/**
 * @return {boolean}
 */
chrome.gcm.OnSendErrorEvent.prototype.hasListeners = function() {};


/**
 * @const
 * @see https://developer.chrome.com/extensions/history.html
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


/** @type {!ChromeEvent} */
chrome.history.onVisitRemoved;


/** @type {!ChromeEvent} */
chrome.history.onVisited;


/**
 * @const
 * @see http://developer.chrome.com/apps/identity.html
 * TODO: replace TokenDetails, InvalidTokenDetails and
 *     WebAuthFlowDetails with Object.
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


/** @param {!function(!Object=):void} callback */
chrome.identity.getProfileUserInfo = function(callback) {};


/** @type {!ChromeEvent} */
chrome.identity.onSignInChanged;


/**
 * @const
 * @see https://developer.chrome.com/extensions/input.ime.html
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
 * @param {!Object.<string,(string|number)>} parameters An object with
 *     'contextID' (number) and 'text' (string) keys.
 * @param {function(boolean): void=} opt_callback Callback function.
 */
chrome.input.ime.deleteSurroundingText = function(parameters, opt_callback) {};


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


/** @type {!ChromeEvent} */
chrome.input.ime.onReset;


/** @type {!ChromeEvent} */
chrome.input.ime.onSurroundingTextChanged;


/**
 * namespace
 * @see http://developer.chrome.com/apps/mediaGalleries
 * @const
 */
chrome.mediaGalleries = {};


/**
 * @param {{interactive: (string|undefined)}|function(!Array.<!FileSystem>)}
 *     detailsOrCallback A details object for whether the request should be
 *     interactive if permissions haven't been granted yet or the callback.
 * @param {function(!Array.<!FileSystem>)=} opt_callback A success callback if
 *     no details were supplied as arg1.
 */
chrome.mediaGalleries.getMediaFileSystems = function(
    detailsOrCallback, opt_callback) {};


/**
 * @param {function(!Array.<!FileSystem>, string)} callback Callback function.
 */
chrome.mediaGalleries.addUserSelectedFolder = function(callback) {};

chrome.mediaGalleries.startMediaScan = function() {};

chrome.mediaGalleries.cancelMediaScan = function() {};


/**
 * @param {function(!Array.<!FileSystem>)} callback Callback function.
 */
chrome.mediaGalleries.addScanResults = function(callback) {};


/**
 * @typedef {{
 *   name: string,
 *   galleryId: string,
 *   deviceId: (string|undefined),
 *   isRemovable: boolean,
 *   isMediaDevice: boolean,
 *   isAvailable: boolean
 * }}
 */
chrome.mediaGalleries.MediaFileSystemMetadata;


/**
 * @param {!FileSystem} mediaFileSystem The file system to get metadata for.
 * @return {!chrome.mediaGalleries.MediaFileSystemMetadata}
 */
chrome.mediaGalleries.getMediaFileSystemMetadata = function(mediaFileSystem) {};


/**
 * @param {function(!Array.<!chrome.mediaGalleries.MediaFileSystemMetadata>)}
 *     callback Callback function.
 */
chrome.mediaGalleries.getAllMediaFileSystemMetadata = function(callback) {};


/**
 * @typedef {{
 *   mimeType: string,
 *   height: (number|undefined),
 *   width: (number|undefined),
 *   duration: (number|undefined),
 *   rotation: (number|undefined),
 *   album: (string|undefined),
 *   artist: (string|undefined),
 *   comment: (string|undefined),
 *   copyright: (string|undefined),
 *   disc: (number|undefined),
 *   genre: (string|undefined),
 *   language: (string|undefined),
 *   title: (string|undefined),
 *   track: (number|undefined)
 * }}
 */
chrome.mediaGalleries.MetaData;


/**
 * @param {!Blob} mediaFile The media file for which to get metadata.
 * @param {{metadataType: (string|undefined)}|
 *     function(!chrome.mediaGalleries.MetaData)} optionsOrCallback The options
 *     for the metadata to retrieve or the callback to invoke with the metadata.
 *     The metadataType should either be 'all' or 'mimeTypeOnly'. Defaults to
 *     'all' if the metadataType is omitted.
 * @param {function(!chrome.mediaGalleries.MetaData)=} opt_callback If options
 *     were passed as arg2, the callback to invoke with the metadata.
 */
chrome.mediaGalleries.getMetadata = function(
    mediaFile, optionsOrCallback, opt_callback) {};


/**
 * @typedef {{
 *   type: string,
 *   galleryCount: (number|undefined),
 *   audioCount: (number|undefined),
 *   imageCount: (number|undefined),
 *   videoCount: (number|undefined)
 * }}
 */
chrome.mediaGalleries.OnScanProgressDetails;



/**
 * Event whose listeners take a chrome.mediaGalleries.OnScanProgressDetails
 * parameter.
 * @constructor
 */
chrome.mediaGalleries.ScanProgressEvent = function() {};


/** @param {function(!chrome.mediaGalleries.OnScanProgressDetails)} callback */
chrome.mediaGalleries.ScanProgressEvent.prototype.addListener =
    function(callback) {};


/** @param {function(!chrome.mediaGalleries.OnScanProgressDetails)} callback */
chrome.mediaGalleries.ScanProgressEvent.prototype.removeListener =
    function(callback) {};


/**
 * @param {function(!chrome.mediaGalleries.OnScanProgressDetails)} callback
 * @return {boolean}
 */
chrome.mediaGalleries.ScanProgressEvent.prototype.hasListener =
    function(callback) {};


/** @return {boolean} */
chrome.mediaGalleries.ScanProgressEvent.prototype.hasListeners = function() {};


/** @type {!chrome.mediaGalleries.ScanProgressEvent} */
chrome.mediaGalleries.onScanProgress;


/**
 * @const
 * @see https://developer.chrome.com/extensions/pageCapture.html
 */
chrome.pageCapture = {};


/**
 * @param {Object.<string, number>} details Object with a 'tabId' (number) key.
 * @param {function(Blob=): void} callback Callback function.
 */
chrome.pageCapture.saveAsMHTML = function(details, callback) {};


/**
 * @const
 * @see https://developer.chrome.com/extensions/permissions.html
 */
chrome.permissions = {};


/**
 * @typedef {{
 *   permissions: (Array.<string>|undefined),
 *   origins: (Array.<string>|undefined)
 * }}
 * @see http://developer.chrome.com/extensions/permissions.html#type-Permissions
 */
chrome.permissions.Permissions;


/**
 * @param {!chrome.permissions.Permissions} permissions
 * @param {function(boolean): void} callback Callback function.
 */
chrome.permissions.contains = function(permissions, callback) {};


/**
 * @param {function(!chrome.permissions.Permissions): void} callback
 *     Callback function.
 */
chrome.permissions.getAll = function(callback) {};


/**
 * @param {!chrome.permissions.Permissions} permissions
 * @param {function(boolean): void=} opt_callback Callback function.
 */
chrome.permissions.remove = function(permissions, opt_callback) {};


/**
 * @param {!chrome.permissions.Permissions} permissions
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
 * @see https://developer.chrome.com/extensions/privacy.html
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
 * @see https://developer.chrome.com/extensions/proxy.html
 */
chrome.proxy = {};


/** @type {!Object.<string,!ChromeSetting>} */
chrome.proxy.settings;


/** @type {!ChromeEvent} */
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
  */
chrome.sockets = {};


/**
  * @const
  * @see https://developer.chrome.com/apps/sockets_tcp
  */
chrome.sockets.tcp = {};



/**
 * @constructor
 * @see https://developer.chrome.com/apps/sockets_tcp#type-SocketInfo
 */
chrome.sockets.tcp.SocketInfo = function() {};


/** @type {number} */
chrome.sockets.tcp.SocketInfo.prototype.socketId;


/** @type {boolean} */
chrome.sockets.tcp.SocketInfo.prototype.persistent;


/** @type {string|undefined} */
chrome.sockets.tcp.SocketInfo.prototype.name;


/** @type {number|undefined} */
chrome.sockets.tcp.SocketInfo.prototype.bufferSize;


/** @type {boolean} */
chrome.sockets.tcp.SocketInfo.prototype.paused;


/** @type {boolean} */
chrome.sockets.tcp.SocketInfo.prototype.connected;


/** @type {string|undefined} */
chrome.sockets.tcp.SocketInfo.prototype.localAddress;


/** @type {number|undefined} */
chrome.sockets.tcp.SocketInfo.prototype.localPort;


/** @type {string|undefined} */
chrome.sockets.tcp.SocketInfo.prototype.peerAddress;


/** @type {number|undefined} */
chrome.sockets.tcp.SocketInfo.prototype.peerPort;


/**
 * @typedef {?{
 *   persistent: (boolean|undefined),
 *   name: (string|undefined),
 *   bufferSize: (number|undefined)
 * }}
 * @see https://developer.chrome.com/apps/sockets_tcp#type-SocketProperties
 */
chrome.sockets.tcp.SocketProperties;


/**
 * @typedef {?{
 *   min: (string|undefined),
 *   max: (string|undefined)
 * }}
 * @see https://developer.chrome.com/apps/sockets_tcp#method-secure
 */
chrome.sockets.tcp.SecurePropertiesTlsVersion;


/**
 * @typedef {?{
 *   tlsVersion: (chrome.sockets.tcp.SecurePropertiesTlsVersion|undefined)
 * }}
 * @see https://developer.chrome.com/apps/sockets_tcp#method-secure
 */
chrome.sockets.tcp.SecureProperties;


/**
 * @param {!chrome.sockets.tcp.SocketProperties|
 *     function(!Object)} propertiesOrCallback
 * @param {function(!Object)=} opt_callback
 * @see https://developer.chrome.com/apps/sockets_tcp#method-create
 */
chrome.sockets.tcp.create = function(propertiesOrCallback, opt_callback) {};


/**
 * @param {number} socketId
 * @param {!chrome.sockets.tcp.SocketProperties} properties
 * @param {function()=} opt_callback
 * @see https://developer.chrome.com/apps/sockets_tcp#method-update
 */
chrome.sockets.tcp.update = function(socketId, properties, opt_callback) {};


/**
 * @param {number} socketId
 * @param {boolean} paused
 * @param {function()=} opt_callback
 * @see https://developer.chrome.com/apps/sockets_tcp#method-setPaused
 */
chrome.sockets.tcp.setPaused = function(socketId, paused, opt_callback) {};


/**
 * @param {number} socketId
 * @param {boolean} enable
 * @param {(number|function(number))} delayOrCallback
 * @param {function(number)=} opt_callback
 * @see https://developer.chrome.com/apps/sockets_tcp#method-setKeepAlive
 */
chrome.sockets.tcp.setKeepAlive = function(socketId, enable, delayOrCallback,
    opt_callback) {};


/**
 * @param {number} socketId
 * @param {boolean} noDelay
 * @param {function(number)} callback
 * @see https://developer.chrome.com/apps/sockets_tcp#method-setNoDelay
 */
chrome.sockets.tcp.setNoDelay = function(socketId, noDelay, callback) {};


/**
 * @param {number} socketId
 * @param {string} peerAddress
 * @param {number} peerPort
 * @param {function(number)} callback
 * @see https://developer.chrome.com/apps/sockets_tcp#method-connect
 */
chrome.sockets.tcp.connect = function(socketId, peerAddress, peerPort,
    callback) {};


/**
 * @param {number} socketId The id of the socket to disconnect.
 * @param {function()=} opt_callback
 * @see https://developer.chrome.com/apps/sockets_tcp#method-disconnect
 */
chrome.sockets.tcp.disconnect = function(socketId, opt_callback) {};


/**
 * @param {number} socketId
 * @param {!chrome.sockets.tcp.SecureProperties|function(number)}
 *     optionsOrCallback
 * @param {function(number)=} opt_callback
 * @see https://developer.chrome.com/apps/sockets_tcp#method-secure
 */
chrome.sockets.tcp.secure = function(socketId, optionsOrCallback,
    opt_callback) {};


/**
 * @param {number} socketId
 * @param {!ArrayBuffer} data
 * @param {function(!Object)} callback
 * @see https://developer.chrome.com/apps/sockets_tcp#method-send
 */
chrome.sockets.tcp.send = function(socketId, data, callback) {};


/**
 * @param {number} socketId
 * @param {function()=} opt_callback
 * @see https://developer.chrome.com/apps/sockets_tcp#method-close
 */
chrome.sockets.tcp.close = function(socketId, opt_callback) {};


/**
 * @param {number} socketId
 * @param {function(!chrome.sockets.tcp.SocketInfo)} callback
 * @see https://developer.chrome.com/apps/sockets_tcp#method-getInfo
 */
chrome.sockets.tcp.getInfo = function(socketId, callback) {};


/**
 * @param {function(!Array.<!chrome.sockets.tcp.SocketInfo>)} callback
 * @see https://developer.chrome.com/apps/sockets_tcp#method-getSockets
 */
chrome.sockets.tcp.getSockets = function(callback) {};



/**
 * @constructor
 * @see https://developer.chrome.com/apps/sockets_tcp#event-onReceive
 */
chrome.sockets.tcp.ReceiveEventData = function() {};


/** @type {number} */
chrome.sockets.tcp.ReceiveEventData.prototype.socketId;


/** @type {!ArrayBuffer} */
chrome.sockets.tcp.ReceiveEventData.prototype.data;



/**
 * Event whose listeners take a ReceiveEventData parameter.
 * @constructor
 */
chrome.sockets.tcp.ReceiveEvent = function() {};


/**
 * @param {function(!chrome.sockets.tcp.ReceiveEventData): void} callback
 */
chrome.sockets.tcp.ReceiveEvent.prototype.addListener =
    function(callback) {};


/**
 * @param {function(!chrome.sockets.tcp.ReceiveEventData): void} callback
 */
chrome.sockets.tcp.ReceiveEvent.prototype.removeListener =
    function(callback) {};


/**
 * @param {function(!chrome.sockets.tcp.ReceiveEventData): void} callback
 * @return {boolean}
 */
chrome.sockets.tcp.ReceiveEvent.prototype.hasListener =
    function(callback) {};


/** @return {boolean} */
chrome.sockets.tcp.ReceiveEvent.prototype.hasListeners = function() {};


/** @type {!chrome.sockets.tcp.ReceiveEvent} */
chrome.sockets.tcp.onReceive;



/**
 * @constructor
 * @see https://developer.chrome.com/apps/sockets_tcp#event-onReceiveError
 */
chrome.sockets.tcp.ReceiveErrorEventData = function() {};


/** @type {number} */
chrome.sockets.tcp.ReceiveErrorEventData.prototype.socketId;


/** @type {number} */
chrome.sockets.tcp.ReceiveErrorEventData.prototype.resultCode;



/**
 * Event whose listeners take a ReceiveErrorEventData parameter.
 * @constructor
 */
chrome.sockets.tcp.ReceiveErrorEvent = function() {};


/**
 * @param {function(
 *     !chrome.sockets.tcp.ReceiveErrorEventData): void} callback
 */
chrome.sockets.tcp.ReceiveErrorEvent.prototype.addListener =
    function(callback) {};


/**
 * @param {function(
 *     !chrome.sockets.tcp.ReceiveErrorEventData): void} callback
 */
chrome.sockets.tcp.ReceiveErrorEvent.prototype.removeListener =
    function(callback) {};


/**
 * @param {function(
 *     !chrome.sockets.tcp.ReceiveErrorEventData): void} callback
 * @return {boolean}
 */
chrome.sockets.tcp.ReceiveErrorEvent.prototype.hasListener =
    function(callback) {};


/** @return {boolean} */
chrome.sockets.tcp.ReceiveErrorEvent.prototype.hasListeners =
    function() {};


/** @type {!chrome.sockets.tcp.ReceiveErrorEvent} */
chrome.sockets.tcp.onReceiveError;


/**
 * @const
 * @see https://developer.chrome.com/extensions/storage.html
 */
chrome.storage = {};


/** @type {!StorageArea} */
chrome.storage.sync;


/** @type {!StorageArea} */
chrome.storage.local;


/** @type {!StorageChangeEvent} */
chrome.storage.onChanged;


/** @const */
chrome.system = {};


/**
 * @const
 * @see http://developer.chrome.com/apps/system_display.html
 */
chrome.system.display = {};


/** @type {!ChromeEvent} */
chrome.system.display.onDisplayChanged;



/**
 * @constructor
 */
chrome.system.display.Bounds = function() {};


/** @type {number} */
chrome.system.display.Bounds.prototype.left;


/** @type {number} */
chrome.system.display.Bounds.prototype.top;


/** @type {number} */
chrome.system.display.Bounds.prototype.width;


/** @type {number} */
chrome.system.display.Bounds.prototype.height;


/**
 * @typedef {{
 *   left: (number|undefined),
 *   top: (number|undefined),
 *   right: (number|undefined),
 *   bottom: (number|undefined)
 * }}
 */
chrome.system.display.Insets;



/**
 * @constructor
 */
chrome.system.display.DisplayInfo = function() {};


/** @type {string} */
chrome.system.display.DisplayInfo.prototype.id;


/** @type {string} */
chrome.system.display.DisplayInfo.prototype.name;


/** @type {string} */
chrome.system.display.DisplayInfo.prototype.mirroringSourceId;


/** @type {boolean} */
chrome.system.display.DisplayInfo.prototype.isPrimary;


/** @type {boolean} */
chrome.system.display.DisplayInfo.prototype.isInternal;


/** @type {boolean} */
chrome.system.display.DisplayInfo.prototype.isEnabled;


/** @type {number} */
chrome.system.display.DisplayInfo.prototype.dpiX;


/** @type {number} */
chrome.system.display.DisplayInfo.prototype.dpiY;


/** @type {number} */
chrome.system.display.DisplayInfo.prototype.rotation;


/** @type {!chrome.system.display.Bounds} */
chrome.system.display.DisplayInfo.prototype.bounds;


/** @type {!chrome.system.display.Insets} */
chrome.system.display.DisplayInfo.prototype.overscan;


/** @type {!chrome.system.display.Bounds} */
chrome.system.display.DisplayInfo.prototype.workArea;


/**
 * @typedef {{
 *   mirroringSourceId: (string|undefined),
 *   isPrimary: (boolean|undefined),
 *   overscan: (!chrome.system.display.Insets|undefined),
 *   rotation: (number|undefined),
 *   boundsOriginX: (number|undefined),
 *   boundsOriginY: (number|undefined)
 * }}
 */
chrome.system.display.SettableDisplayInfo;


chrome.types = {};


/**
 * @typedef {?{
 *   format: (string|undefined),
 *   quality: (number|undefined)
 * }}
 */
chrome.types.ImageDetails;


/**
 * @param {function(!Array.<!chrome.system.display.DisplayInfo>)}
 *     callback Called with an array of objects representing display info.
 */
chrome.system.display.getInfo = function(callback) {};


/**
 * @param {string} id The display's unique identifier.
 * @param {!chrome.system.display.SettableDisplayInfo} info The information
 *     about display properties that should be changed.
 * @param {function()=} opt_callback The callback to execute when the display
 *     info has been changed.
 */
chrome.system.display.setDisplayProperties =
    function(id, info, opt_callback) {};


/**
 * @const
 * @see https://developer.chrome.com/extensions/types.html
 */
chrome.chromeSetting = {};


/** @type {!ChromeEvent} */
chrome.chromeSetting.onChange;


/**
 * @const
 * @see https://developer.chrome.com/extensions/webNavigation.html
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


/** @type {!ChromeEvent} */
chrome.webNavigation.onBeforeNavigate;


/** @type {!ChromeEvent} */
chrome.webNavigation.onCommitted;


/** @type {!ChromeEvent} */
chrome.webNavigation.onDOMContentLoaded;


/** @type {!ChromeEvent} */
chrome.webNavigation.onCompleted;


/** @type {!ChromeEvent} */
chrome.webNavigation.onErrorOccurred;


/** @type {!ChromeEvent} */
chrome.webNavigation.onCreatedNavigationTarget;


/** @type {!ChromeEvent} */
chrome.webNavigation.onReferenceFragmentUpdated;


/** @type {!ChromeEvent} */
chrome.webNavigation.onTabReplaced;


/** @type {!ChromeEvent} */
chrome.webNavigation.onHistoryStateUpdated;



/**
 * Most event listeners for WebRequest take extra arguments.
 * @see https://developer.chrome.com/extensions/webRequest.html.
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
 * @see https://developer.chrome.com/extensions/webRequest.html.
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
 * @see https://developer.chrome.com/extensions/webRequest.html
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
 * @see https://developer.chrome.com/extensions/management.html
 * @constructor
 */
function ExtensionInfo() {}


/** @type {string} */
ExtensionInfo.prototype.id;


/** @type {string} */
ExtensionInfo.prototype.name;


/** @type {string} */
ExtensionInfo.prototype.shortName;


/** @type {string} */
ExtensionInfo.prototype.description;


/** @type {string} */
ExtensionInfo.prototype.version;


/** @type {boolean} */
ExtensionInfo.prototype.mayDisable;


/** @type {boolean} */
ExtensionInfo.prototype.enabled;


/** @type {string|undefined} */
ExtensionInfo.prototype.disabledReason;


/** @type {boolean} */
ExtensionInfo.prototype.isApp;


/** @type {string} */
ExtensionInfo.prototype.type;


/** @type {string|undefined} */
ExtensionInfo.prototype.appLaunchUrl;


/** @type {string|undefined} */
ExtensionInfo.prototype.homepageUrl;


/** @type {string|undefined} */
ExtensionInfo.prototype.updateUrl;


/** @type {boolean} */
ExtensionInfo.prototype.offlineEnabled;


/** @type {string} */
ExtensionInfo.prototype.optionsUrl;


/** @type {!Array.<!IconInfo>|undefined} */
ExtensionInfo.prototype.icons;


/** @type {!Array.<string>} */
ExtensionInfo.prototype.permissions;


/** @type {!Array.<string>} */
ExtensionInfo.prototype.hostPermissions;


/** @type {string} */
ExtensionInfo.prototype.installType;


/** @type {string|undefined} */
ExtensionInfo.prototype.launchType;


/** @type {!Array.<string>|undefined} */
ExtensionInfo.prototype.availableLaunchTypes;



/**
 * @see https://developer.chrome.com/extensions/management.html
 * @constructor
 */
function IconInfo() {}


/** @type {number} */
IconInfo.prototype.size;


/** @type {string} */
IconInfo.prototype.url;



/**
 * @see https://developer.chrome.com/extensions/tabs
 * @constructor
 */
function Tab() {}


// TODO: Make this field optional once dependent projects have been updated.
/**
 * @type {number}
 */
Tab.prototype.id;


/** @type {number} */
Tab.prototype.index;


/** @type {number} */
Tab.prototype.windowId;


// TODO: Make this field optional once dependent projects have been updated.
/**
 * @type {number}
 */
Tab.prototype.openerTabId;


/** @type {boolean} */
Tab.prototype.highlighted;


/** @type {boolean} */
Tab.prototype.active;


/** @type {boolean} */
Tab.prototype.pinned;


// TODO: Make this field optional once dependent projects have been updated.
/**
 * @type {string}
 */
Tab.prototype.url;


// TODO: Make this field optional once dependent projects have been updated.
/**
 * @type {string}
 */
Tab.prototype.title;


// TODO: Make this field optional once dependent projects have been updated.
/**
 * @type {string}
 */
Tab.prototype.favIconUrl;


// TODO: Make this field optional once dependent projects have been updated.
/**
 * @type {string}
 */
Tab.prototype.status;


/** @type {boolean} */
Tab.prototype.incognito;


/** @type {number|undefined} */
Tab.prototype.width;


/** @type {number|undefined} */
Tab.prototype.height;


/** @type {number|undefined} */
Tab.prototype.sessionId;



/**
 * @see https://developer.chrome.com/extensions/windows.html
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
 * @see https://developer.chrome.com/extensions/events.html
 * @constructor
 */
function ChromeEvent() {}


/** @param {!Function} callback */
ChromeEvent.prototype.addListener = function(callback) {};


/** @param {!Function} callback */
ChromeEvent.prototype.removeListener = function(callback) {};


/**
 * @param {!Function} callback
 * @return {boolean}
 */
ChromeEvent.prototype.hasListener = function(callback) {};


/** @return {boolean} */
ChromeEvent.prototype.hasListeners = function() {};



/**
 * Event whose listeners take a string parameter.
 * @constructor
 */
function ChromeStringEvent() {}


/** @param {function(string): void} callback */
ChromeStringEvent.prototype.addListener = function(callback) {};


/** @param {function(string): void} callback */
ChromeStringEvent.prototype.removeListener = function(callback) {};


/**
 * @param {function(string): void} callback
 * @return {boolean}
 */
ChromeStringEvent.prototype.hasListener = function(callback) {};


/** @return {boolean} */
ChromeStringEvent.prototype.hasListeners = function() {};



/**
 * Event whose listeners take a boolean parameter.
 * @constructor
 */

function ChromeBooleanEvent() {}


/**
 * @param {function(boolean): void} callback
 */
ChromeBooleanEvent.prototype.addListener = function(callback) {};


/**
 * @param {function(boolean): void} callback
 */
ChromeBooleanEvent.prototype.removeListener = function(callback) {};


/**
 * @param {function(boolean): void} callback
 * @return {boolean}
 */
ChromeBooleanEvent.prototype.hasListener = function(callback) {};


/**
 * @return {boolean}
 */
ChromeBooleanEvent.prototype.hasListeners = function() {};



/**
 * Event whose listeners take a number parameter.
 * @constructor
 */

function ChromeNumberEvent() {}


/**
 * @param {function(number): void} callback
 */
ChromeNumberEvent.prototype.addListener = function(callback) {};


/**
 * @param {function(number): void} callback
 */
ChromeNumberEvent.prototype.removeListener = function(callback) {};


/**
 * @param {function(number): void} callback
 * @return {boolean}
 */
ChromeNumberEvent.prototype.hasListener = function(callback) {};


/**
 * @return {boolean}
 */
ChromeNumberEvent.prototype.hasListeners = function() {};



/**
 * Event whose listeners take an Object parameter.
 * @constructor
 */
function ChromeObjectEvent() {}


/**
 * @param {function(!Object): void} callback Callback.
 */
ChromeObjectEvent.prototype.addListener = function(callback) {};


/**
 * @param {function(!Object): void} callback Callback.
 */
ChromeObjectEvent.prototype.removeListener = function(callback) {};


/**
 * @param {function(!Object): void} callback Callback.
 * @return {boolean}
 */
ChromeObjectEvent.prototype.hasListener = function(callback) {};


/**
 * @return {boolean}
 */
ChromeObjectEvent.prototype.hasListeners = function() {};



/**
 * Event whose listeners take an ExtensionInfo parameter.
 * @constructor
 */
function ChromeExtensionInfoEvent() {}


/** @param {function(!ExtensionInfo): void} callback */
ChromeExtensionInfoEvent.prototype.addListener = function(callback) {};


/** @param {function(!ExtensionInfo): void} callback */
ChromeExtensionInfoEvent.prototype.removeListener = function(callback) {};


/**
 * @param {function(!ExtensionInfo): void} callback
 * @return {boolean}
 */
ChromeExtensionInfoEvent.prototype.hasListener = function(callback) {};


/** @return {boolean} */
ChromeExtensionInfoEvent.prototype.hasListeners = function() {};



/**
 * Event whose listeners take a string array parameter.
 * @constructor
 */
function ChromeStringArrayEvent() {}


/** @param {function(!Array.<string>): void} callback */
ChromeStringArrayEvent.prototype.addListener = function(callback) {};


/** @param {function(!Array.<string>): void} callback */
ChromeStringArrayEvent.prototype.removeListener = function(callback) {};


/**
 * @param {function(!Array.<string>): void} callback
 * @return {boolean}
 */
ChromeStringArrayEvent.prototype.hasListener = function(callback) {};


/** @return {boolean} */
ChromeStringArrayEvent.prototype.hasListeners = function() {};



/**
 * Event whose listeners take two strings as parameters.
 * @constructor
 */
function ChromeStringStringEvent() {}


/** @param {function(string, string): void} callback */
ChromeStringStringEvent.prototype.addListener = function(callback) {};


/** @param {function(string, string): void} callback */
ChromeStringStringEvent.prototype.removeListener = function(callback) {};


/**
 * @param {function(string, string): void} callback
 * @return {boolean}
 */
ChromeStringStringEvent.prototype.hasListener = function(callback) {};


/** @return {boolean} */
ChromeStringStringEvent.prototype.hasListeners = function() {};


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
 * @see http://developer.chrome.com/apps/runtime.html#type-Port
 * @constructor
 */
function Port() {}


/** @type {string} */
Port.prototype.name;


/** @type {!ChromeEvent} */
Port.prototype.onDisconnect;


/** @type {!ChromeEvent} */
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
 * @see http://developer.chrome.com/extensions/runtime.html#type-MessageSender
 * @constructor
 */
function MessageSender() {}


/** @type {!Tab|undefined} */
MessageSender.prototype.tab;


/** @type {string|undefined} */
MessageSender.prototype.id;


/** @type {string|undefined} */
MessageSender.prototype.url;


/** @type {string|undefined} */
MessageSender.prototype.tlsChannelId;



/**
 * @see https://developer.chrome.com/extensions/bookmarks.html#type-BookmarkTreeNode
 * @constructor
 */
function BookmarkTreeNode() {}


/** @type {string} */
BookmarkTreeNode.prototype.id;


/** @type {string|undefined} */
BookmarkTreeNode.prototype.parentId;


/** @type {number|undefined} */
BookmarkTreeNode.prototype.index;


/** @type {string|undefined} */
BookmarkTreeNode.prototype.url;


/** @type {string} */
BookmarkTreeNode.prototype.title;


/** @type {number|undefined} */
BookmarkTreeNode.prototype.dateAdded;


/** @type {number|undefined} */
BookmarkTreeNode.prototype.dateGroupModified;


/** @type {string|undefined} */
BookmarkTreeNode.prototype.unmodifiable;


/** @type {!Array.<!BookmarkTreeNode>|undefined} */
BookmarkTreeNode.prototype.children;



/**
 * @see https://developer.chrome.com/extensions/dev/cookies.html#type-Cookie
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
 * @see https://developer.chrome.com/extensions/dev/cookies.html#type-CookieStore
 * @constructor
 */
function CookieStore() {}


/** @type {string} */
CookieStore.prototype.id;


/** @type {Array.<number>} */
CookieStore.prototype.tabIds;



/**
 * @see https://developer.chrome.com/extensions/dev/contextMenus.html#type-OnClickData
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
 * @see https://developer.chrome.com/extensions/debugger.html#type-Debuggee
 * @constructor
 */
function Debuggee() {}


/** @type {number} */
Debuggee.prototype.tabId;



/**
 * @see https://developer.chrome.com/extensions/contentSettings.html#type-ResourceIdentifier
 * @constructor
 */
function ResourceIdentifier() {}


/** @type {string} */
ResourceIdentifier.prototype.id;


/** @type {string} */
ResourceIdentifier.prototype.description;



/**
 * @see https://developer.chrome.com/extensions/contentSettings.html#type-ContentSetting
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
 * @see https://developer.chrome.com/extensions/history.html#type-HistoryItem
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
 * @see https://developer.chrome.com/extensions/history.html#type-VisitItem
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
 * @see https://developer.chrome.com/extensions/fileBrowserHandler.html#type-FileHandlerExecuteEventDetails
 * @constructor
 */
function FileHandlerExecuteEventDetails() {}


/** @type {!Array.<!FileEntry>} */
FileHandlerExecuteEventDetails.prototype.entries;


/** @type {number|undefined} */
FileHandlerExecuteEventDetails.prototype.tab_id;



/**
 * @see https://developer.chrome.com/extensions/input.ime.html#type-KeyboardEvent
 * @constructor
 */
function ChromeKeyboardEvent() {}


/** @type {string} */
ChromeKeyboardEvent.prototype.type;


/** @type {string} */
ChromeKeyboardEvent.prototype.requestId;


/** @type {string|undefined} */
ChromeKeyboardEvent.prototype.extensionId;


/** @type {string} */
ChromeKeyboardEvent.prototype.key;


/** @type {string} */
ChromeKeyboardEvent.prototype.code;


/** @type {number|undefined} */
ChromeKeyboardEvent.prototype.keyCode;


/** @type {boolean|undefined} */
ChromeKeyboardEvent.prototype.altKey;


/** @type {boolean|undefined} */
ChromeKeyboardEvent.prototype.ctrlKey;


/** @type {boolean|undefined} */
ChromeKeyboardEvent.prototype.shiftKey;


/** @type {boolean|undefined} */
ChromeKeyboardEvent.prototype.capsLock;



/**
 * @see https://developer.chrome.com/extensions/input.ime.html#type-InputContext
 * @constructor
 */
function InputContext() {}


/** @type {number} */
InputContext.prototype.contextID;


/** @type {string} */
InputContext.prototype.type;



/**
 * @see https://developer.chrome.com/extensions/proxy.html#type-ProxyServer
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
 * @see https://developer.chrome.com/extensions/proxy.html#type-ProxyRules
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
 * @see https://developer.chrome.com/extensions/proxy.html#type-PacScript
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
 * @see https://developer.chrome.com/extensions/proxy.html#type-ProxyConfig
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
 * @see https://developer.chrome.com/extensions/storage.html
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
 * @see https://developer.chrome.com/extensions/storage.html#type-StorageChange
 * @constructor
 */
function StorageChange() {}


/** @type {?} */
StorageChange.prototype.oldValue;


/** @type {?} */
StorageChange.prototype.newValue;



/**
 * @see https://developer.chrome.com/extensions/storage.html#type-StorageArea
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
 * @see https://developer.chrome.com/extensions/types.html#type-ChromeSetting
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
 * @see https://developer.chrome.com/extensions/webRequest.html#type-RequestFilter
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
 * @see https://developer.chrome.com/extensions/webRequest.html#type-HttpHeaders
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
 * @see https://developer.chrome.com/extensions/webRequest.html#type-HttpHeaders
 * @typedef {Array.<!HttpHeader>}
 * @private
 */
var HttpHeaders_;



/**
 * @see https://developer.chrome.com/extensions/webRequest.html#type-BlockingResponse
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


/**
 * The {@code chrome.fileSystem} API makes use of the Entry and FileEntry types
 * defined in {@code javascript/externs/fileapi.js}.
 * @const
 * @see http://developer.chrome.com/apps/fileSystem.html
 */
chrome.fileSystem = {};


/**
 * @param {!Entry} entry The entry to get the display path for. The entry can
 *     originally be obtained through
 *     {@code chrome.fileSystem.chooseEntry} or
 *     {@code chrome.fileSystem.restoreEntry}.
 * @param {function(string)} callback A success callback.
 * @see http://developer.chrome.com/apps/fileSystem.html#method-getDisplayPath
 */
chrome.fileSystem.getDisplayPath = function(entry, callback) {};


/**
 * @param {!Entry} entry The entry to get a writable entry for.
 * @param {function(!Entry)} callback A success callback.
 * @see http://developer.chrome.com/apps/fileSystem.html#method-getWritableEntry
 */
chrome.fileSystem.getWritableEntry = function(entry, callback) {};


/**
 * @param {!Entry} entry The entry to query writability.
 * @param {function(boolean)} callback A success callback.
 * @see http://developer.chrome.com/apps/fileSystem.html#method-isWritableEntry
 */
chrome.fileSystem.isWritableEntry = function(entry, callback) {};


/**
 * @typedef {{
 *   description: (string|undefined),
 *   mimeTypes: (!Array.<string>|undefined),
 *   extensions: (!Array.<string>|undefined)
 * }}
 * @see http://developer.chrome.com/apps/fileSystem.html#method-chooseEntry
 */
chrome.fileSystem.AcceptsOption;


/**
 * @typedef {{
 *   type: (string|undefined),
 *   suggestedName: (string|undefined),
 *   accepts: (!Array.<!chrome.fileSystem.AcceptsOption>|undefined),
 *   acceptsAllTypes: (boolean|undefined),
 *   acceptsMultiple: (boolean|undefined)
 * }}
 * @see http://developer.chrome.com/apps/fileSystem.html#method-chooseEntry
 */
chrome.fileSystem.ChooseEntryOptions;


/**
 * @param {!chrome.fileSystem.ChooseEntryOptions|
 *     function(Entry=, !Array.<!FileEntry>=)} optionsOrCallback The
 *     options for the file prompt or the callback.
 * @param {function(Entry=, !Array.<!FileEntry>=)=} opt_callback A success
 *     callback, if arg1 is options.
 * @see http://developer.chrome.com/apps/fileSystem.html#method-chooseEntry
 */
chrome.fileSystem.chooseEntry = function(optionsOrCallback, opt_callback) {};


/**
 * @param {string} id The ID of the file entry to restore.
 * @param {function(!Entry)} callback A success callback.
 * @see http://developer.chrome.com/apps/fileSystem.html#method-restoreEntry
 */
chrome.fileSystem.restoreEntry = function(id, callback) {};


/**
 * @param {string} id The ID of the file entry to query restorability.
 * @param {function(boolean)} callback A success callback.
 * @see http://developer.chrome.com/apps/fileSystem.html#method-isRestorable
 */
chrome.fileSystem.isRestorable = function(id, callback) {};


/**
 * @param {!Entry} entry The entry to regain access to.
 * @return {string} The ID that can be passed to restoreEntry to regain access
 *     to the given file entry.
 * @see http://developer.chrome.com/apps/fileSystem.html#method-retainEntry
 */
chrome.fileSystem.retainEntry = function(entry) {};


/**
 * @const
 * @see https://developer.chrome.com/apps/syncFileSystem
 */
chrome.syncFileSystem = {};


/**
 * Returns a syncable filesystem backed by Google Drive. The returned
 * DOMFileSystem instance can be operated on in the same way as
 * the Temporary and Persistant file systems (see
 * http://www.w3.org/TR/file-system-api/), except that the filesystem
 * object returned for Sync FileSystem does NOT support directory
 * operations (yet). You can get a list of file entries by reading
 * the root directory (by creating a new DirectoryReader),
 * but cannot create a new directory in it.
 *
 * <p>Calling this multiple times from the same app will return the same
 * handle to the same file system.
 *
 * <p>Note this call can fail. For example, if the user is not signed in
 * to Chrome or if there is no network operation. To handle these errors
 * it is important chrome.runtime.lastError is checked in the callback.
 *
 * @param {function(!FileSystem)} callback A callback type for
 *     requestFileSystem.
 * @see https://developer.chrome.com/apps/syncFileSystem#method-requestFileSystem
 */
chrome.syncFileSystem.requestFileSystem = function(callback) {};


/**
 * Sets the default conflict resolution policy for the 'syncable' file
 * storage for the app. By default it is set to 'last_write_win'.
 * When conflict resolution policy is set to 'last_write_win' conflicts
 * for existing files are automatically resolved next time the file is updated.
 * {@code callback} can be optionally given to know if the request has
 * succeeded or not.
 *
 * @param {string} policy Any of 'last_write_win' or 'manual'
 * @param {function()=} opt_callback
 *
 * @see https://developer.chrome.com/apps/syncFileSystem#method-setConflictResolutionPolicy
 */
chrome.syncFileSystem.setConflictResolutionPolicy =
    function(policy, opt_callback) {};


/**
 * Gets the current conflict resolution policy.
 *
 * @param {function(string)} callback Accepting any of 'last_write_win'
 *     or 'manual'.
 * @see https://developer.chrome.com/apps/syncFileSystem#method-getConflictResolutionPolicy
 */
chrome.syncFileSystem.getConflictResolutionPolicy = function(callback) {};


/**
 * Returns the current usage and quota in bytes for the 'syncable' file
 * storage for the app.
 *
 * @param {!FileSystem} fileSystem
 * @param {function(!Object)} callback Taking an object substantially similar
 *     to {@code {'usageBytes': number, quotaBytes: number}}.
 * @see https://developer.chrome.com/apps/syncFileSystem#method-getUsageAndQuota
 */
chrome.syncFileSystem.getUsageAndQuota = function(fileSystem, callback) {};


/**
 * Returns the FileStatus for the given fileEntry. The status value can be
 * 'synced', 'pending' or 'conflicting'. Note that 'conflicting' state only
 * happens when the service's conflict resolution policy is set to 'manual'.
 *
 * @param {!Entry} fileEntry
 * @param {function(string)} callback Called with any of 'synced', 'pending'
 *     or 'conflicting'.
 *
 * @see https://developer.chrome.com/apps/syncFileSystem#method-getFileStatus
 */
chrome.syncFileSystem.getFileStatus = function(fileEntry, callback) {};


/**
 * Returns each FileStatus for the given fileEntry array. Typically called
 * with the result from dirReader.readEntries().
 *
 * @param {!Array.<!FileEntry>} fileEntries
 * @param {function(!Array.<!Object>)} callback Each object will look like:
 *     {@code {'fileEntry': Entry, 'status': string, 'error': string?}}.
 *
 * @see https://developer.chrome.com/apps/syncFileSystem#method-getFileStatuses
 */
chrome.syncFileSystem.getFileStatuses = function(fileEntries, callback) {};


/**
 * Since Chrome 31.
 *
 * <p>Returns the current sync backend status.
 *
 * @param {function(string)} callback Arg is any of 'initializing', 'running',
 *     'authentication_required', 'temporary_unavailable', or 'disabled'.
 *
 * @see https://developer.chrome.com/apps/syncFileSystem#method-getServiceStatus
 */
chrome.syncFileSystem.getServiceStatus = function(callback) {};


/**
 * Fired when an error or other status change has happened in the sync
 * backend (for example, when the sync is temporarily disabled due
 * to network or authentication error).
 *
 * @type {!ChromeObjectEvent}
 *
 * @see https://developer.chrome.com/apps/syncFileSystem#event-onServiceStatusChanged
 */
chrome.syncFileSystem.onServiceStatusChanged;


/**
 * Fired when a file has been updated by the background sync service.
 *
 * @type {!ChromeObjectEvent}
 *
 * @see https://developer.chrome.com/apps/syncFileSystem#event-onFileStatusChanged
 */
chrome.syncFileSystem.onFileStatusChanged;


/**
 * @const
 * @see http://developer.chrome.com/extensions/alarms.html
 */
chrome.alarms = {};


/**
 * Creates an alarm. Near the time(s) specified by alarmInfo, the onAlarm event
 * is fired. If there is another alarm with the same name (or no name if none is
 * specified), it will be cancelled and replaced by this alarm.
 * @param {string|!chrome.alarms.AlarmCreateInfo} nameOrAlarmCreateInfo Either
 *     the name to identify this alarm or the info used to create the alarm. If
 *     no name is passed, the empty string is used to identify the alarm.
 * @param {!chrome.alarms.AlarmCreateInfo=} opt_alarmInfo If a name was passed
 *     as arg1, the info used to create the alarm.
 * @see http://developer.chrome.com/extensions/alarms.html#method-create
 */
chrome.alarms.create = function(nameOrAlarmCreateInfo, opt_alarmInfo) {};


/**
 * Retrieves details about the specified alarm.
 * @param {string|function(!chrome.alarms.Alarm)} nameOrCallback The name
 *     of the alarm to get or the callback to invoke with the alarm. If no name
 *     is passed, the empty string is used to get the alarm.
 * @param {function(!chrome.alarms.Alarm)=} opt_callback If a name was passed
 *     as arg1, the callback to invoke with the alarm.
 * @see http://developer.chrome.com/extensions/alarms.html#method-get
 */
chrome.alarms.get = function(nameOrCallback, opt_callback) {};


/**
 * Gets an array of all the alarms.
 * @param {function(!Array.<!chrome.alarms.Alarm>)} callback
 * @see http://developer.chrome.com/extensions/alarms.html#method-getAll
 */
chrome.alarms.getAll = function(callback) {};


/**
 * Clears the alarm with the given name.
 * @param {string=} opt_name
 * @see http://developer.chrome.com/extensions/alarms.html#method-clear
 */
chrome.alarms.clear = function(opt_name) {};


/**
 * Clears all alarms.
 * @see http://developer.chrome.com/extensions/alarms.html#method-clearAll
 */
chrome.alarms.clearAll = function() {};


/**
 * Fired when an alarm has elapsed. Useful for event pages.
 * @type {!chrome.alarms.AlarmEvent}
 * @see http://developer.chrome.com/extensions/alarms.html#event-onAlarm
 */
chrome.alarms.onAlarm;



/**
 * @constructor
 */
chrome.alarms.AlarmEvent = function() {};


/**
 * @param {function(!chrome.alarms.Alarm): void} callback
 */
chrome.alarms.AlarmEvent.prototype.addListener = function(callback) {};


/**
 * @param {function(!chrome.alarms.Alarm): void} callback
 */
chrome.alarms.AlarmEvent.prototype.removeListener = function(callback) {};


/**
 * @param {function(!chrome.alarms.Alarm): void} callback
 * @return {boolean}
 */
chrome.alarms.AlarmEvent.prototype.hasListener = function(callback) {};


/**
 * @return {boolean}
 */
chrome.alarms.AlarmEvent.prototype.hasListeners = function() {};



/**
 * @interface
 * @see http://developer.chrome.com/extensions/alarms.html#type-Alarm
 */
chrome.alarms.Alarm = function() {};


/**
 * Name of this alarm.
 * @type {string}
 */
chrome.alarms.Alarm.prototype.name;


/**
 * Time at which this alarm was scheduled to fire, in milliseconds past the
 * epoch (e.g. Date.now() + n). For performance reasons, the alarm may have been
 * delayed an arbitrary amount beyond this.
 * @type {number}
 */
chrome.alarms.Alarm.prototype.scheduledTime;


/**
 * If not null, the alarm is a repeating alarm and will fire again in
 * periodInMinutes minutes.
 * @type {?number}
 */
chrome.alarms.Alarm.prototype.periodInMinutes;


/**
 * @typedef {{
 *   when: (number|undefined),
 *   delayInMinutes: (number|undefined),
 *   periodInMinutes: (number|undefined)
 * }}
 * @see http://developer.chrome.com/extensions/alarms.html#method-create
 */
chrome.alarms.AlarmCreateInfo;


/**
 * @see https://developer.chrome.com/apps/hid
 * @const
 */
chrome.hid = {};


/**
 * @typedef {?{
 *   vendorId: number,
 *   productId: number
 * }}
 * @see https://developer.chrome.com/apps/hid#method-getDevices
 */
chrome.hid.HidGetDevicesOptions;


/**
 * @typedef {?{
 *   usagePage: number,
 *   usage: number,
 *   reportIds: !Array.<number>
 * }}
* @see https://developer.chrome.com/apps/hid#method-getDevices
*/
chrome.hid.HidDeviceUsage;


/**
 * @typedef {?{
 *   deviceId: number,
 *   vendorId: number,
 *   productId: number,
 *   collections: !Array.<!chrome.hid.HidDeviceUsage>,
 *   maxInputReportSize: number,
 *   maxOutputReportSize: number,
 *   maxFeatureReportSize: number
 * }}
* @see https://developer.chrome.com/apps/hid#method-getDevices
*/
chrome.hid.HidDeviceInfo;


/**
 * @typedef {?{
 *   connectionId: number
 * }}
 * @see https://developer.chrome.com/apps/hid#method-connect
 */
chrome.hid.HidConnectInfo;


/**
 * @see https://developer.chrome.com/apps/hid#method-getDevices
 * Enumerates all the connected HID devices specified by the
 * vendorId/productId/interfaceId tuple.
 * @param {!chrome.hid.HidGetDevicesOptions} options The properties to search
 *     for on target devices.
 * @param {function(!Array.<!Object>)} callback Invoked with a list of
 *     |HidDeviceInfo|s on complete.
 */
chrome.hid.getDevices = function(options, callback) {};


/**
 * @see https://developer.chrome.com/apps/hid#method-connect
 * Opens a connection to a HID device for communication.
 * @param {number} deviceId The ID of the device to open.
 * @param {function(!Object=)} callback Invoked with an |HidConnectInfo| if the
 *     connection succeeds, or undefined if it fails.
 */
chrome.hid.connect = function(deviceId, callback) {};


/**
 * @see https://developer.chrome.com/apps/hid#method-disconnect
 * Disconnects from a device.
 * @param {number} connectionId The connection to close.
 * @param {function()=} opt_callback The callback to invoke once the connection
 *     is closed.
 */
chrome.hid.disconnect = function(connectionId, opt_callback) {};


/**
 * @see https://developer.chrome.com/apps/hid#method-receive
 * Receives an input report from an HID device.
 * @param {number} connectionId The connection from which to receive the report.
 * @param {function(number, !ArrayBuffer)} callback The callback to invoke with
 *     the received report.
 */
chrome.hid.receive = function(connectionId, callback) {};


/**
 * @see https://developer.chrome.com/apps/hid#method-send
 * Sends an output report to an HID device.
 * @param {number} connectionId The connection to which to send the report.
 * @param {number} reportId The report ID to use, or 0 if none.
 * @param {!ArrayBuffer} data The report data.
 * @param {function()} callback The callback to invoke once the write is
 *     finished.
 */
chrome.hid.send = function(connectionId, reportId, data, callback) {};


/**
 * @see https://developer.chrome.com/apps/hid#method-receiveFeatureReport
 * Receives a feature report from the device.
 * @param {number} connectionId The connection from which to read the feature
 *     report.
 * @param {number} reportId The report ID to use, or 0 if none.
 * @param {number} size The size of the feature report to receive.
 * @param {function(!ArrayBuffer)} callback The callback to invoke with the
 *     received report.
 */
chrome.hid.receiveFeatureReport =
    function(connectionId, reportId, size, callback) {};


/**
 * @see https://developer.chrome.com/apps/hid#method-sendFeatureReport
 * Sends a feature report to the device.
 * @param {number} connectionId The connection to which to send the feature
 *     report.
 * @param {number} reportId The report ID to use, or 0 if none.
 * @param {!ArrayBuffer} data The report data.
 * @param {function()} callback The callback to invoke once the write is
 *     finished.
 */
chrome.hid.sendFeatureReport =
    function(connectionId, reportId, data, callback) {};


/**
 * @see http://developer.chrome.com/extensions/notifications.html
 * @const
 */
chrome.notifications = {};


/**
 * @typedef {{
 *   title: string,
 *   iconUrl: (string|undefined)
 * }}
 * @see http://developer.chrome.com/extensions/notifications.html#type-NotificationOptions
 */
chrome.notifications.NotificationButton;


/**
 * @typedef {{
 *   title: string,
 *   message: string
 * }}
 * @see http://developer.chrome.com/extensions/notifications.html#type-NotificationOptions
 */
chrome.notifications.NotificationItem;


/**
 * @typedef {{
 *   type: (string|undefined),
 *   iconUrl: (string|undefined),
 *   title: (string|undefined),
 *   message: (string|undefined),
 *   contextMessage: (string|undefined),
 *   priority: (number|undefined),
 *   eventTime: (number|undefined),
 *   buttons: (!Array.<!chrome.notifications.NotificationButton>|undefined),
 *   imageUrl: (string|undefined),
 *   items: (!Array.<!chrome.notifications.NotificationItem>|undefined),
 *   progress: (number|undefined),
 *   isClickable: (boolean|undefined)
 * }}
 * @see http://developer.chrome.com/extensions/notifications.html#type-NotificationOptions
 */
chrome.notifications.NotificationOptions;


/**
 * @typedef {function(boolean): void}
 * @see http://developer.chrome.com/extensions/notifications.html#method-update
 * @see http://developer.chrome.com/extensions/notifications.html#method-clear
 */
chrome.notifications.BooleanCallback;


/**
 * @typedef {function(!Object): void}
 * @see http://developer.chrome.com/extensions/notifications.html#method-getAll
 */
chrome.notifications.ObjectCallback;


/**
 * @typedef {function(string, boolean): void}
 * @see http://developer.chrome.com/extensions/notifications.html#event-onClosed
 */
chrome.notifications.ClosedCallback;


/**
 * @typedef {function(string, number): void}
 * @see http://developer.chrome.com/extensions/notifications.html#event-onButtonClicked
 */
chrome.notifications.ButtonCallback;


/**
 * @param {string} notificationId
 * @param {!chrome.notifications.NotificationOptions} options
 * @param {function(string): void} callback
 * @see http://developer.chrome.com/extensions/notifications.html#method-create
 */
chrome.notifications.create = function(notificationId, options, callback) {};


/**
 * @param {string} notificationId
 * @param {!chrome.notifications.NotificationOptions} options
 * @param {!chrome.notifications.BooleanCallback} callback
 * @see http://developer.chrome.com/extensions/notifications.html#method-update
 */
chrome.notifications.update = function(notificationId, options, callback) {};


/**
 * @param {string} notificationId
 * @param {!chrome.notifications.BooleanCallback} callback
 * @see http://developer.chrome.com/extensions/notifications.html#method-clear
 */
chrome.notifications.clear = function(notificationId, callback) {};


/**
 * @see http://developer.chrome.com/extensions/notifications.html#method-getAll
 * @param {!chrome.notifications.ObjectCallback} callback
 */
chrome.notifications.getAll = function(callback) {};


/**
 * @see http://developer.chrome.com/extensions/notifications.html#method-getPermissionLevel
 * @param {function(string): void} callback takes 'granted' or 'denied'
 */
chrome.notifications.getPermissionLevel = function(callback) {};


/**
 * @type {!chrome.notifications.ClosedEvent}
 * @see http://developer.chrome.com/extensions/notifications.html#event-onClosed
 */
chrome.notifications.onClosed;


/**
 * The user clicked a non-button area of the notification. Callback receives a
 * notificationId.
 * @type {!ChromeStringEvent}
 * @see http://developer.chrome.com/extensions/notifications.html#event-onClicked
 */
chrome.notifications.onClicked;


/**
 * @type {!chrome.notifications.ButtonClickedEvent}
 * @see http://developer.chrome.com/extensions/notifications.html#event-onButtonClicked
 */
chrome.notifications.onButtonClicked;


/**
 * Indicates permission level change. Callback should expect 'granted' or
 * 'denied'.
 * @type {!ChromeStringEvent}
 * @see http://developer.chrome.com/extensions/notifications.html#event-onPermissionLevelChanged
 */
chrome.notifications.onPermissionLevelChanged;


/**
 * @type {!ChromeEvent}
 * @see http://developer.chrome.com/extensions/notifications.html#event-onShowSettings
 */
chrome.notifications.onShowSettings;



/**
 * @interface
 * @see http://developer.chrome.com/extensions/notifications.html#event-onClosed
 */
chrome.notifications.ClosedEvent = function() {};


/**
 * @param {!chrome.notifications.ClosedCallback} callback
 */
chrome.notifications.ClosedEvent.prototype.addListener = function(callback) {};


/**
 * @param {!chrome.notifications.ClosedCallback} callback
 */
chrome.notifications.ClosedEvent.prototype.removeListener =
    function(callback) {};


/**
 * @param {!chrome.notifications.ClosedCallback} callback
 * @return {boolean}
 */
chrome.notifications.ClosedEvent.prototype.hasListener = function(callback) {};


/**
 * @return {boolean}
 */
chrome.notifications.ClosedEvent.prototype.hasListeners = function() {};



/**
 * @interface
 * @see http://developer.chrome.com/extensions/notifications.html#event-onButtonClicked
 */
chrome.notifications.ButtonClickedEvent = function() {};


/**
 * @param {!chrome.notifications.ButtonCallback} callback
 */
chrome.notifications.ButtonClickedEvent.prototype.addListener =
    function(callback) {};


/**
 * @param {!chrome.notifications.ButtonCallback} callback
 */
chrome.notifications.ButtonClickedEvent.prototype.removeListener =
    function(callback) {};


/**
 * @param {!chrome.notifications.ButtonCallback} callback
 * @return {boolean}
 */
chrome.notifications.ButtonClickedEvent.prototype.hasListener =
    function(callback) {};


/**
 * @return {boolean}
 */
chrome.notifications.ButtonClickedEvent.prototype.hasListeners = function() {};


/**
 * @const
 * @see http://developer.chrome.com/apps/system_storage.html
 */
chrome.system.storage = {};



/** @constructor */
chrome.system.storage.StorageUnitInfo = function() {};


/** @type {string} */
chrome.system.storage.StorageUnitInfo.id;


/** @type {string} */
chrome.system.storage.StorageUnitInfo.name;


/** @type {string} Any of 'fixed', 'removable', or 'unknown' */
chrome.system.storage.StorageUnitInfo.type;


/** @type {number} */
chrome.system.storage.StorageUnitInfo.capacity;



/**
 * Event whose listeners take a StorageUnitInfoEvent parameter.
 * @constructor
 */
chrome.system.storage.StorageUnitInfoEvent = function() {};


/** @param {function(!chrome.system.storage.StorageUnitInfo): void} callback */
chrome.system.storage.StorageUnitInfoEvent.prototype.addListener =
    function(callback) {};


/** @param {function(!chrome.system.storage.StorageUnitInfo): void} callback */
chrome.system.storage.StorageUnitInfoEvent.prototype.removeListener =
    function(callback) {};


/**
 * @param {function(!chrome.system.storage.StorageUnitInfo): void} callback
 * @return {boolean}
 */
chrome.system.storage.StorageUnitInfoEvent.prototype.hasListener =
    function(callback) {};


/** @return {boolean} */
chrome.system.storage.StorageUnitInfoEvent.prototype.hasListeners =
    function() {};


/** @type {chrome.system.storage.StorageUnitInfoEvent} */
chrome.system.storage.onAttached;


/** @type {!ChromeStringEvent} */
chrome.system.storage.onDetached;


/**
 * Gets the storage information from the system.
 * @param {function(!Array.<!chrome.system.storage.StorageUnitInfo>)} callback
 */
chrome.system.storage.getInfo = function(callback) {};


/**
 * Ejects a removable storage device.
 * @param {string} id The transient device ID from StorageUnitInfo.
 * @param {function(string)} callback Callback function where the value
 *     is any of: "success", "in_use", "no_such_device", "failure"
 */
chrome.system.storage.ejectDevice = function(id, callback) {};


/**
 * Gets the available capacity of a specified storage device.
 * @param {string} id The transient device ID from StorageUnitInfo.
 * @param {function(Object.<string, number>)} callback A callback function that
 *     accepts an object with {@code id} and {@code availableCapacity} fields.
 */
chrome.system.storage.getAvailableCapacity = function(id, callback) {};


/**
 * @see http://developer.chrome.com/apps/usb.html
 * @const
 */
chrome.usb = {};



/** @constructor */
chrome.usb.Device = function Device() {};


/** @type {number} */
chrome.usb.Device.prototype.device;


/** @type {number} */
chrome.usb.Device.prototype.vendorId;


/** @type {number} */
chrome.usb.Device.prototype.productId;



/** @constructor */
chrome.usb.ConnectionHandle = function ConnectionHandle() {};


/** @type {number} */
chrome.usb.ConnectionHandle.prototype.handle;


/** @type {number} */
chrome.usb.ConnectionHandle.prototype.vendorId;


/** @type {number} */
chrome.usb.ConnectionHandle.prototype.productId;


/**
 * @typedef {?{
 *   direction: string,
 *   endpoint: number,
 *   length: (number|undefined),
 *   data: (!ArrayBuffer|undefined)
 * }}
 */
chrome.usb.GenericTransferInfo;


/**
 * @typedef {?{
 *   direction: string,
 *   recipient: string,
 *   requestType: string,
 *   request: number,
 *   value: number,
 *   index: number,
 *   length: (number|undefined),
 *   data: (!ArrayBuffer|undefined)
 * }}
 */
chrome.usb.ControlTransferInfo;



/** @constructor */
chrome.usb.TransferResultInfo = function() {};


/** @type {number|undefined} */
chrome.usb.TransferResultInfo.prototype.resultCode;


/** @type {!ArrayBuffer|undefined} */
chrome.usb.TransferResultInfo.prototype.data;


/**
 * @typedef {?{
 *   deviceId: number,
 *   productId: number,
 *   interfaceId: (number|undefined)
 * }}
 */
chrome.usb.FindDevicesOptions;


/**
 * @see http://developer.chrome.com/apps/usb.html#method-getDevices
 * @param {!Object} options The properties to search for on target devices.
 * @param {function(!Array.<!chrome.usb.Device>)} callback Invoked with a list
 *     of |Device|s on complete.
 */
chrome.usb.getDevices = function(options, callback) {};


/**
 * @see http://developer.chrome.com/apps/usb.html#method-requestAccess
 * @param {!chrome.usb.Device} device The device to request access to.
 * @param {number} interfaceId
 * @param {function(boolean)} callback
 */
chrome.usb.requestAccess = function(device, interfaceId, callback) {};


/**
 * @see http://developer.chrome.com/apps/usb.html#method-openDevice
 * @param {!chrome.usb.Device} device The device to open.
 * @param {function(!chrome.usb.ConnectionHandle=)} callback Invoked with the
 *     created ConnectionHandle on complete.
 */
chrome.usb.openDevice = function(device, callback) {};


/**
 * @see http://developer.chrome.com/apps/usb.html#method-findDevices
 * @param {!chrome.usb.FindDevicesOptions} options The properties to search for
 *     on target devices.
 * @param {function(!Array.<!chrome.usb.ConnectionHandle>)} callback Invoked
 *     with the opened ConnectionHandle on complete.
 */
chrome.usb.findDevices = function(options, callback) {};


/**
 * @see http://developer.chrome.com/apps/usb.html#method-closeDevice
 * @param {!chrome.usb.ConnectionHandle} handle The connection handle to close.
 * @param {function()=} opt_callback The callback to invoke once the device is
 *     closed.
 */
chrome.usb.closeDevice = function(handle, opt_callback) {};


/**
 * @see http://developer.chrome.com/apps/usb.html#method-listInterfaces
 * @param {!chrome.usb.ConnectionHandle} handle The device from which the
 *     interfaces should be listed.
 * @param {function(!Array.<!Object>)} callback
 *     The callback to invoke when the interfaces are enumerated.
 */
chrome.usb.listInterfaces = function(handle, callback) {};


/**
 * @see http://developer.chrome.com/apps/usb.html#method-claimInterface
 * @param {!chrome.usb.ConnectionHandle} handle The device on which the
 *     interface is to be claimed.
 * @param {number} interfaceNumber
 * @param {function()} callback The callback to invoke once the interface is
 *     claimed.
 */
chrome.usb.claimInterface = function(handle, interfaceNumber, callback) {};


/**
 * @see http://developer.chrome.com/apps/usb.html#method-releaseInterface
 * @param {!chrome.usb.ConnectionHandle} handle The device on which the
 *     interface is to be released.
 * @param {number} interfaceNumber
 * @param {function()} callback The callback to invoke once the interface is
 *     released.
 */
chrome.usb.releaseInterface = function(handle, interfaceNumber, callback) {};


/**
 * @see http://developer.chrome.com/apps/usb.html#method-setInterfaceAlternateSetting
 * @param {!chrome.usb.ConnectionHandle} handle The device on which the
 *     interface settings are to be set.
 * @param {number} interfaceNumber
 * @param {number} alternateSetting The alternate setting to set.
 * @param {function()} callback The callback to invoke once the interface
 *     setting is set.
 */
chrome.usb.setInterfaceAlternateSetting = function(
    handle, interfaceNumber, alternateSetting, callback) {};


/**
 * @see http://developer.chrome.com/apps/usb.html#method-controlTransfer
 * @param {!chrome.usb.ConnectionHandle} handle A connection handle to make the
 *     transfer on.
 * @param {!chrome.usb.ControlTransferInfo} transferInfo The parameters to the
 *     transfer.
 * @param {function(!chrome.usb.TransferResultInfo)} callback Invoked once the
 *     transfer has completed.
 */
chrome.usb.controlTransfer = function(handle, transferInfo, callback) {};


/**
 * @see http://developer.chrome.com/apps/usb.html#method-bulkTransfer
 * @param {!chrome.usb.ConnectionHandle} handle A connection handle to make
 *     the transfer on.
 * @param {!chrome.usb.GenericTransferInfo} transferInfo The parameters to the
 *     transfer. See GenericTransferInfo.
 * @param {function(!chrome.usb.TransferResultInfo)} callback Invoked once the
 *     transfer has completed.
 */
chrome.usb.bulkTransfer = function(handle, transferInfo, callback) {};


/**
 * @see http://developer.chrome.com/apps/usb.html#method-interruptTransfer
 * @param {!chrome.usb.ConnectionHandle} handle A connection handle to make the
 *     transfer on.
 * @param {!chrome.usb.GenericTransferInfo} transferInfo The parameters to the
 *     transfer. See GenericTransferInfo.
 * @param {function(!chrome.usb.TransferResultInfo)} callback Invoked once the
 *     transfer has completed.
 */
chrome.usb.interruptTransfer = function(handle, transferInfo, callback) {};


/**
 * @see http://developer.chrome.com/apps/usb.html#method-isochronousTransfer
 * @param {!chrome.usb.ConnectionHandle} handle A connection handle to make the
 *     transfer on.
 * @param {!Object} transferInfo The parameters to the transfer. See
 *     IsochronousTransferInfo.
 * @param {function(!chrome.usb.TransferResultInfo)} callback Invoked once the
 *     transfer has been completed.
 */
chrome.usb.isochronousTransfer = function(handle, transferInfo, callback) {};


/**
 * @see http://developer.chrome.com/apps/usb.html#method-resetDevice
 * @param {!chrome.usb.ConnectionHandle} handle A connection handle to reset.
 * @param {function(boolean)} callback Invoked once the device is reset with a
 *     boolean indicating whether the reset completed successfully.
 */
chrome.usb.resetDevice = function(handle, callback) {};


/**
 * @const
 * @see https://developer.chrome.com/apps/webstore
 */
chrome.webstore = {};


/**
 * @param {string|function()|function(string)=}
 *     opt_urlOrSuccessCallbackOrFailureCallback Either the URL to install or
 *     the succcess callback taking no arg or the failure callback taking an
 *     error string arg.
 * @param {function()|function(string)=} opt_successCallbackOrFailureCallback
 *     Either the succcess callback taking no arg or the failure callback
 *     taking an error string arg.
 * @param {function(string)=} opt_failureCallback The failure callback.
 */
chrome.webstore.install = function(
    opt_urlOrSuccessCallbackOrFailureCallback,
    opt_successCallbackOrFailureCallback,
    opt_failureCallback) {};


/** @type {!ChromeStringEvent} */
chrome.webstore.onInstallStageChanged;


/** @type {!ChromeNumberEvent} */
chrome.webstore.onDownloadProgress;


////////////////////////////////////////////////////////////////////////////////
/////////////////////////// Chrome Private APIs ////////////////////////////////
////////////////////////////////////////////////////////////////////////////////


/** @const */
chrome.screenlockPrivate = {};


/**
 * @param {string} message Displayed on the unlock screen.
 */
chrome.screenlockPrivate.showMessage = function(message) {};


/**
 * @param {function(boolean)} callback
 */
chrome.screenlockPrivate.getLocked = function(callback) {};


/**
 * @param {boolean} locked If true and the screen is unlocked, locks the screen.
 *     If false and the screen is locked, unlocks the screen.
 */
chrome.screenlockPrivate.setLocked = function(locked) {};


/** @type {!ChromeBooleanEvent} */
chrome.screenlockPrivate.onChanged;


/**
 * @const
 */
chrome.musicManagerPrivate = {};


/**
 * @param {function(string): void} callback
 */
chrome.musicManagerPrivate.getDeviceId = function(callback) {};


/**
 * @const
 */
chrome.mediaGalleriesPrivate = {};


/**
 * @typedef {function({deviceId: string, deviceName: string}): void}
 */
chrome.mediaGalleriesPrivate.DeviceCallback;


/**
 * @typedef {function({galleryId: string}): void}
 */
chrome.mediaGalleriesPrivate.GalleryChangeCallback;


/**
 * @typedef {function({galleryId: string, success: boolean}): void}
 */
chrome.mediaGalleriesPrivate.AddGalleryWatchCallback;


/**
 * @param {string} galleryId
 * @param {!chrome.mediaGalleriesPrivate.AddGalleryWatchCallback} callback
 */
chrome.mediaGalleriesPrivate.addGalleryWatch = function(galleryId, callback) {};


/**
 * @type {!chrome.mediaGalleriesPrivate.DeviceEvent}
 * @deprecated Use {chrome.system.storage.onAttach}.
 */
chrome.mediaGalleriesPrivate.onDeviceAttached;


/**
 * @type {!chrome.mediaGalleriesPrivate.DeviceEvent}
 * @deprecated Use {chrome.system.storage.onDetach}.
 */
chrome.mediaGalleriesPrivate.onDeviceDetached;


/**
 * @type {!chrome.mediaGalleriesPrivate.GalleryChangeEvent}
 */
chrome.mediaGalleriesPrivate.onGalleryChanged;



/**
 * @interface
 * @deprecated Use {chrome.system.storage.DeviceEvent}.
 */
chrome.mediaGalleriesPrivate.DeviceEvent = function() {};


/**
 * @param {!chrome.mediaGalleriesPrivate.DeviceCallback} callback
 * @deprecated Use {chrome.system.storage.DeviceEvent.addListener}.
 */
chrome.mediaGalleriesPrivate.DeviceEvent.prototype.addListener =
    function(callback) {};


/**
 * @param {!chrome.mediaGalleriesPrivate.DeviceCallback} callback
 * @deprecated Use {chrome.system.storage.DeviceEvent.removeListener}.
 */
chrome.mediaGalleriesPrivate.DeviceEvent.prototype.removeListener =
    function(callback) {};


/**
 * @param {!chrome.mediaGalleriesPrivate.DeviceCallback} callback
 * @deprecated Use {chrome.system.storage.DeviceEvent.hasListener}.
 */
chrome.mediaGalleriesPrivate.DeviceEvent.prototype.hasListener =
    function(callback) {};


/**
 * @return {boolean}
 * @deprecated Use {chrome.system.storage.DeviceEvent.hasListener}
 */
chrome.mediaGalleriesPrivate.DeviceEvent.prototype.hasListeners =
    function(callback) {};



/**
 * @interface
 */
chrome.mediaGalleriesPrivate.GalleryChangeEvent = function() {};


/**
 * @param {!chrome.mediaGalleriesPrivate.GalleryChangeCallback} callback
 */
chrome.mediaGalleriesPrivate.GalleryChangeEvent.prototype.addListener =
    function(callback) {};


/**
 * @param {!chrome.mediaGalleriesPrivate.GalleryChangeCallback} callback
 */
chrome.mediaGalleriesPrivate.GalleryChangeEvent.prototype.removeListener =
    function(callback) {};


/**
 * @param {!chrome.mediaGalleriesPrivate.GalleryChangeCallback} callback
 */
chrome.mediaGalleriesPrivate.GalleryChangeEvent.prototype.hasListener =
    function(callback) {};


/**
 * @return {boolean}
 */
chrome.mediaGalleriesPrivate.GalleryChangeEvent.prototype.hasListeners =
    function() {};


/**
 * WARNING(2014/08/04): This API is still under active initial development and
 * unstable and has a number of issues:
 *
 * 1. The types NetworkProperties and ManagedNetworkProperties are not defined
 *    in the docs; that is, there is no list of fields and their types.
 *    Therefore, these types are treated as bags-of-objects, rather than types.
 * 2. According to Steven Bennetts, NetworkProperties *should* be a
 *    bag-of-properties as it's a map containing ONC properties and the ONC
 *    properties do not follow the JS field naming conventions; specifically,
 *    the properties start with an uppercase letter, and at least one property
 *    is in all uppercase.
 * 3. The deviceSsid and deviceBssid fields of VerticationProperties are listed
 *    as being required while their description mentions "Only set if" which
 *    sound optional. The dev team was unclear whether they are required or
 *    optional.
 * 4. Some parameters to some functions are marked as being in the Beta channel
 *    only (for example, the networkGuid parameter to getCaptivePortalStatus).
 *
 * Because of the above issues, this API should not be used as an example for
 * other APIs added to this file. Please contact mednik@ for questions on and
 * maintenance for this API.
 * @const
 * @see https://developer.chrome.com/extensions/networkingPrivate
 */
chrome.networkingPrivate = {};


/**
 * @typedef {?{
 *   certificate: string,
 *   publicKey: string,
 *   nonce: string,
 *   signedData: string,
 *   deviceSerial: string,
 *   deviceSsid: string,
 *   deviceBssid: string
 * }}
 */
chrome.networkingPrivate.VerificationProperties;


/**
 * @typedef {?{
 *   networkType: string,
 *   visible: (boolean|undefined),
 *   configured: (boolean|undefined),
 *   limit: (number|undefined)
 * }}
 */
chrome.networkingPrivate.NetworkFilter;


/**
 * @param {string} guid
 * @param {function(!Object)} callback
 */
chrome.networkingPrivate.getProperties = function(guid, callback) {};


/**
 * @param {string} guid
 * @param {function(!Object)} callback
 */
chrome.networkingPrivate.getManagedProperties = function(guid, callback) {};


/**
 * @param {string} guid
 * @param {function(!Object)} callback
 */
chrome.networkingPrivate.getState = function(guid, callback) {};


/**
 * @param {string} guid
 * @param {!Object} properties
 * @param {function()} callback
 */
chrome.networkingPrivate.setProperties = function(guid, properties, callback) {
};


/**
 * @param {boolean} shared
 * @param {!Object} properties
 * @param {function(string)} callback Returns guid of the configured
 *     configuration.
 */
chrome.networkingPrivate.createNetwork =
    function(shared, properties, callback) {};


/**
 * @param {!chrome.networkingPrivate.NetworkFilter} filter
 * @param {function(!Array.<!Object>)=} opt_callback
 */
chrome.networkingPrivate.getNetworks = function(filter, opt_callback) {};


/**
 * @param {string} type
 * @param {function(!Array.<!Object>)=} opt_callback
 */
chrome.networkingPrivate.getVisibleNetworks = function(type, opt_callback) {};


/** @param {function(!Array.<string>)=} opt_callback */
chrome.networkingPrivate.getEnabledNetworkTypes = function(opt_callback) {};


/** @param {string} networkType */
chrome.networkingPrivate.enableNetworkType = function(networkType) {};


/** @param {string} networkType */
chrome.networkingPrivate.disableNetworkType = function(networkType) {};


/**
 * Requests that the networking subsystem scan for new networks and update the
 * list returned by getVisibleNetworks.
 */
chrome.networkingPrivate.requestNetworkScan = function() {};


/**
 * @param {string} guid
 * @param {function()=} opt_callback
 */
chrome.networkingPrivate.startConnect = function(guid, opt_callback) {};


/**
 * @param {string} guid
 * @param {function()=} opt_callback
 */
chrome.networkingPrivate.startDisconnect = function(guid, opt_callback) {};


/**
 * @param {!chrome.networkingPrivate.VerificationProperties} verificationInfo
 * @param {function(boolean)} callback
 */
chrome.networkingPrivate.verifyDestination =
    function(verificationInfo, callback) {};


/**
 * @param {!chrome.networkingPrivate.VerificationProperties} verificationInfo
 * @param {string} guid
 * @param {function(string)} callback
 */
chrome.networkingPrivate.verifyAndEncryptCredentials =
    function(verificationInfo, guid, callback) {};


/**
 * @param {!chrome.networkingPrivate.VerificationProperties} verificationInfo
 * @param {string} data
 * @param {function(string)} callback
 */
chrome.networkingPrivate.verifyAndEncryptData =
    function(verificationInfo, data, callback) {};


/**
 * @param {string} ipOrMacAddress
 * @param {boolean} enabled
 * @param {function(string)} callback
 */
chrome.networkingPrivate.setWifiTDLSEnabledState =
    function(ipOrMacAddress, enabled, callback) {};


/**
 * @param {string} ipOrMacAddress
 * @param {function(string)} callback
 */
chrome.networkingPrivate.getWifiTDLSStatus =
    function(ipOrMacAddress, callback) {};


/**
 * @param {string} guid
 * @param {function(string)} callback
 */
chrome.networkingPrivate.getCaptivePortalStatus = function(guid, callback) {};


/** @type {!ChromeStringArrayEvent} */
chrome.networkingPrivate.onNetworksChanged;


/** @type {!ChromeStringArrayEvent} */
chrome.networkingPrivate.onNetworkListChanged;


/** @type {!ChromeStringStringEvent} */
chrome.networkingPrivate.onPortalDetectionCompleted;


/**
 * WARNING(2014/08/14): This API is still under active initial development and
 * unstable. The types are not well defined or documented, and this API
 * definition here should not be used as an example for other APIs added to this
 * file. Please contact mednik@ for questions on and maintenance for this API.
 * @const
 * @see http://goo.gl/afV8wB
 */
chrome.mdns = {};


/**
 * Data type sent to the event handler of chrome.mdns.onServiceList.
 * @constructor
 */
chrome.mdns.MdnsService = function() {};


/** @type {string} */
chrome.mdns.MdnsService.prototype.serviceName;


/** @type {string} */
chrome.mdns.MdnsService.prototype.serviceHostPort;


/** @type {string} */
chrome.mdns.MdnsService.prototype.ipAddress;


/** @type {!Array.<string>} */
chrome.mdns.MdnsService.prototype.serviceData;


/**
 * Event whose listeners take an array of MdnsService parameter.
 * @constructor
 */
chrome.mdns.ServiceListEvent = function() {};


/**
 * @param {function(!Array.<!chrome.mdns.MdnsService>): void} callback
 * @param {!Object=} opt_filter
 */
chrome.mdns.ServiceListEvent.prototype.addListener =
    function(callback, opt_filter) {};


/** @param {function(!Array.<!chrome.mdns.MdnsService>): void} callback */
chrome.mdns.ServiceListEvent.prototype.removeListener = function(callback) {};


/**
 * @param {function(!Array.<!chrome.mdns.MdnsService>): void} callback
 * @return {boolean}
 */
chrome.mdns.ServiceListEvent.prototype.hasListener = function(callback) {};


/** @return {boolean} */
chrome.mdns.ServiceListEvent.prototype.hasListeners = function() {};


/** @type {!chrome.mdns.ServiceListEvent} */
chrome.mdns.onServiceList;


/**
 * @const
 * @see http://goo.gl/79p5h5
 */
chrome.gcdPrivate = {};


/**
 * Represents a GCD device discovered locally or registered to a given user.
 * deviceId: Opaque device identifier to be passed to API.
 * setupType: How this device was discovered.
 * cloudId: Cloud identifier string.
 * deviceName: Device human readable name.
 * deviceType: Device type (camera, printer, etc).
 * deviceDescription: Device human readable description.
 * @typedef {?{
 *   deviceId: string,
 *   setupType: string,
 *   cloudId: (string|undefined),
 *   deviceType: string,
 *   deviceName: string,
 *   deviceDescription: string
 * }}
 */
chrome.gcdPrivate.Device;


/**
 * Returns the list of cloud devices visible locally or available in the
 * cloud for user account.
 * @param {function(!Array.<!chrome.gcdPrivate.Device>): void} callback
 */
chrome.gcdPrivate.getCloudDeviceList = function(callback) {};


/**
 * Queries network for local devices. Triggers onDeviceStateChanged and
 * onDeviceRemoved events. Call this function *only* after registering for
 * onDeviceStateChanged and onDeviceRemoved events, or it will do nothing.
 */
chrome.gcdPrivate.queryForNewLocalDevices = function() {};


/**
 * Cache the WiFi password in the browser process for use during
 * provisioning. This is done to allow the gathering of the wifi password to
 * not be done while connected to the device's network. Callback is called
 * with true if wifi password was cached and false if it was unavailable.
 * @param {string} ssid
 * @param {function(boolean): void} callback
 */
chrome.gcdPrivate.prefetchWifiPassword = function(ssid, callback) {};


/**
 * Establish the session.
 * @param {string} ipAddress
 * @param {number} port
 * @param {function(number, string, !Array.<string>): void}
 *     callback Called when the session is established or on error. 1st param,
 *     |sessionId|, is the session ID (identifies the session for future calls).
 *     2nd param, |status|, is the status (success or type of error). 3rd param,
 *     |pairingTypes|, is a list of pairing types supported by this device.
 */
chrome.gcdPrivate.establishSession = function(ipAddress, port, callback) {};


/**
 * Start pairing with the selected method.
 * @param {number} sessionId
 * @param {string} pairingType
 * @param {function(string): void} callback
 */
chrome.gcdPrivate.startPairing = function(sessionId, pairingType, callback) {};


/**
 * Confirm pairing code.
 * @param {number} sessionId
 * @param {string} code
 * @param {function(string): void} callback
 */
chrome.gcdPrivate.confirmCode = function(sessionId, code, callback) {};


/**
 * Send an encrypted message to the device. If the message is a setup message
 * with a wifi ssid specified but no password, the password cached from
 * prefetchWifiPassword() will be used and the call will fail if it's not
 * available. For open networks use an empty string as the password.
 * @param {number} sessionId
 * @param {string} api The API path.
 * @param {!Object} input The input message to be sent over the encrypted
 *     channel.
 * @param {function(string, ?Object): void} callback
 */
chrome.gcdPrivate.sendMessage = function(sessionId, api, input, callback) {};


/**
 * Terminate the session with the device.
 * @param {number} sessionId
 */
chrome.gcdPrivate.terminateSession = function(sessionId) {};


/**
 * Returns command definitions.
 * @param {string} deviceId The device to get command definitions for.
 * @param {function(!Object): void} callback The result callback.
 */
chrome.gcdPrivate.getCommandDefinitions = function(deviceId, callback) {};


/**
 * Creates and sends a new command.
 * @param {string} deviceId The device to send the command to.
 * @param {number} expireInMs The number of milliseconds since now before the
 *     command expires. An expired command should not be executed by the device.
 *     Acceptable values are 10 sec (10000 ms) to 30 days (2592000000 ms),
 *     inclusive. All values outside that range will be replaced by 30 days.
 * @param {!Object} command Described at
 *     https://developers.google.com/cloud-devices/v1/reference/commands.
 * @param {function(!Object): void} callback  The result callback.
 */
chrome.gcdPrivate.insertCommand = function(
    deviceId, expireInMs, command, callback) {};


/**
 * Returns a particular command.
 * @param {string} commandId Unique command ID.
 * @param {function(!Object): void} callback  The result callback.
 */
chrome.gcdPrivate.getCommand = function(commandId, callback) {};


/**
 * Cancels a command.
 * @param {string} commandId Unique command ID.
 * @param {function(!Object): void} callback  The result callback.
 */
chrome.gcdPrivate.cancelCommand = function(commandId, callback) {};


/**
 * Lists all commands in order of creation.
 * @param {string} deviceId The device to send the command to.
 * @param {string} byUser List all the commands issued by the user. Special
 *     value 'me' can be used to list by the current user.
 * @param {string} state Command state.
 * @param {function(!Array.<!Object>): void} callback  The result callback.
 */
chrome.gcdPrivate.getCommandsList = function(
    deviceId, byUser, state, callback) {};



/**
 * Event whose listeners take a chrome.gcdPrivate.Device.
 * @constructor
 */
chrome.gcdPrivate.DeviceEvent = function() {};


/** @param {function(!chrome.gcdPrivate.Device): void} callback */
chrome.gcdPrivate.DeviceEvent.prototype.addListener = function(callback) {};


/** @param {function(!chrome.gcdPrivate.Device): void} callback */
chrome.gcdPrivate.DeviceEvent.prototype.removeListener = function(callback) {};


/**
 * @param {function(!chrome.gcdPrivate.Device): void} callback
 * @return {boolean}
 */
chrome.gcdPrivate.DeviceEvent.prototype.hasListener = function(callback) {};


/** @return {boolean} */
chrome.gcdPrivate.DeviceEvent.prototype.hasListeners = function() {};


/**
 * Fires when a device's state changes. When a listener is first added, this
 * event fires for all known devices on the network. Afterwards, it will fire
 * with device status updates.
 * @type {!chrome.gcdPrivate.DeviceEvent}
 */
chrome.gcdPrivate.onDeviceStateChanged;


/**
 * Fires when a given device disappears.
 * |deviceId| The device that has disappeared.
 * @type {!ChromeStringEvent}
 */
chrome.gcdPrivate.onDeviceRemoved;


/**
 * @const
 * @see http://goo.gl/bKHibo
 */
chrome.bluetoothPrivate = {};



/** @constructor */
chrome.bluetoothPrivate.PairingEvent = function() {};


/** @type {string} */
chrome.bluetoothPrivate.PairingEvent.prototype.pairing;


/** @type {!chrome.bluetooth.Device} */
chrome.bluetoothPrivate.PairingEvent.prototype.device;


/** @type {string|undefined} */
chrome.bluetoothPrivate.PairingEvent.prototype.pincode;


/** @type {number|undefined} */
chrome.bluetoothPrivate.PairingEvent.prototype.passkey;


/** @type {number|undefined} */
chrome.bluetoothPrivate.PairingEvent.prototype.enteredKey;


/**
 * @typedef {{
 *   name: (string|undefined),
 *   powered: (boolean|undefined),
 *   discoverable: (boolean|undefined)
 * }}
 */
chrome.bluetoothPrivate.NewAdapterState;


/**
 * @typedef {{
 *   device: !chrome.bluetooth.Device,
 *   response: (string|undefined),
 *   pincode: (string|undefined),
 *   passkey: (number|undefined),
 *   enteredKey: (number|undefined)
 * }}
 */
chrome.bluetoothPrivate.SetPairingResponseOptions;


/**
 * @param {!chrome.bluetoothPrivate.NewAdapterState} adapterState
 * @param {function()} callback
 */
chrome.bluetoothPrivate.setAdapterState = function(adapterState, callback) {};


/**
 * @param {!chrome.bluetoothPrivate.SetPairingResponseOptions} options
 * @param {function()} callback
 */
chrome.bluetoothPrivate.setPairingResponse = function(options, callback) {};



/**
 * Event whose listeners take a PairingEvent parameter.
 * @constructor
 */
chrome.bluetoothPrivate.PairingEventEvent = function() {};


/** @param {function(!chrome.bluetoothPrivate.PairingEvent): void} callback */
chrome.bluetoothPrivate.PairingEventEvent.prototype.addListener =
    function(callback) {};


/** @param {function(!chrome.bluetoothPrivate.PairingEvent): void} callback */
chrome.bluetoothPrivate.PairingEventEvent.prototype.removeListener =
    function(callback) {};


/**
 * @param {function(!chrome.bluetoothPrivate.PairingEvent): void} callback
 * @return {boolean}
 */
chrome.bluetoothPrivate.PairingEventEvent.prototype.hasListener =
    function(callback) {};


/** @return {boolean} */
chrome.bluetoothPrivate.PairingEventEvent.prototype.hasListeners =
    function() {};


/** @type {!chrome.bluetoothPrivate.PairingEventEvent} */
chrome.bluetoothPrivate.onPairing;



/**
 * @const
 * @see http://goo.gl/XmVdHm
 */
chrome.inlineInstallPrivate = {};


/**
 * Installs the given app ID.
 * @param {string} id
 * @param {function(string, string): void=} opt_callback Response callback that
 *     returns two string: (1) an error string (or empty string on success) and
 *     (2) an error code in case of error
 */
chrome.inlineInstallPrivate.install = function(id, opt_callback) {};
