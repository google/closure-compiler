/*
 * Copyright 2010 Google Inc.
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
 * @fileoverview Definitions for W3C's Notifications specification.
 * @externs
 */

/**
 * @constructor
 * @implements {EventTarget}
 * @see http://dev.w3.org/2006/webapi/WebNotifications/publish/#idl-if-Notification
 */
function Notification() {}

/** @override */
Notification.prototype.addEventListener =
    function(type, listener, useCapture) {};

/** @override */
Notification.prototype.removeEventListener =
    function(type, listener, useCapture) {};

/** @override */
Notification.prototype.dispatchEvent = function(evt) {};

/**
 * The ID used by clients to uniquely identify notifications to eliminate
 * duplicate notifications.
 * @type {string}
 */
Notification.prototype.replaceId;

/**
 * The string used by clients to specify the directionality (rtl/ltr) of the
 * notification.
 * @type {string}
 * @see http://dev.w3.org/2006/webapi/WebNotifications/publish/Notifications.html#dfn-dir
 */
Notification.prototype.dir;

/**
 * Displays the notification.
 */
Notification.prototype.show = function() {};

/**
 * Prevents the notification from being displayed, or closes it if it is already
 * displayed.
 */
Notification.prototype.cancel = function() {};

/**
 * An event handler called when notification is closed.
 * @type {?function(Event)}
 */
Notification.prototype.onclose;

/**
 * An event handler called if the notification could not be displayed due to
 * an error (i.e. resource could not be loaded).
 * @type {?function(Event)}
 */
Notification.prototype.onerror;

/**
 * An event handler called when the notification has become visible.
 * @type {?function(Event)}
 */
Notification.prototype.ondisplay;

/**
 * An event handler called when the notification has been clicked on.
 * @type {?function(Event)}
 */
Notification.prototype.onclick;

/**
 * @constructor
 * @see http://dev.w3.org/2006/webapi/WebNotifications/publish/#dialog-if
 */
function NotificationCenter() {}

/**
 * Creates a text+icon notification and displays it to the user.
 * @param {string} iconUrl
 * @param {string} title
 * @param {string} body
 * @return {Notification}
 */
NotificationCenter.prototype.createNotification =
    function(iconUrl, title, body) {};

/**
 * Creates an HTML notification and displays it to the user.
 * @param {string} url
 * @return {Notification}
 */
NotificationCenter.prototype.createHTMLNotification = function(url) {};

/**
 * Checks if the user has permission to display notifications.
 * @return {number}
 */
NotificationCenter.prototype.checkPermission = function() {};

/**
 * Requests permission from the user to display notifications.
 * @param {Function=} opt_callback
 * @return {void}
 */
NotificationCenter.prototype.requestPermission = function(opt_callback) {};

/**
 * WebKit browsers expose the NotificationCenter API through
 * window.webkitNotifications.
 * @type {NotificationCenter}
 */
Window.prototype.webkitNotifications;
