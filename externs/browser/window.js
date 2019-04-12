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
 * @fileoverview JavaScript Built-Ins for windows properties.
 *
 * @externs
 * @author stevey@google.com (Steve Yegge)
 */

// Window properties
// Only common properties are here.  Others such as open()
// should be used with an explicit Window object.

/**
 * @const {!Window}
 * @see https://developer.mozilla.org/en/DOM/window.top
 */
var top;

/**
 * @const {!Navigator}
 * @see https://developer.mozilla.org/en/DOM/window.navigator
 */
var navigator;

/**
 * @const {!HTMLDocument}
 * @see https://developer.mozilla.org/en/DOM/window.document
 */
var document;

/**
 * @const {!Location}
 * @see https://developer.mozilla.org/en/DOM/window.location
 * @suppress {duplicate}
 * @implicitCast
 */
var location;

/**
 * @const {!Screen}
 * @see https://developer.mozilla.org/En/DOM/window.screen
 */
var screen;

/**
 * @const {!Window}
 * @see https://developer.mozilla.org/En/DOM/Window.self
 */
var self;

/**
 * @const {boolean}
 * @see https://developer.mozilla.org/en-US/docs/Web/API/Window/isSecureContext
 */
var isSecureContext;

/**
 * @const {!VisualViewport}
 * @see https://developer.mozilla.org/en-US/docs/Web/API/Window/visualViewport
 */
var visualViewport;

// Magic functions for Firefox's LiveConnect.
// We'll probably never use these in practice. But redefining them
// will fire up the JVM, so we want to reserve the symbol names.

/**
 * @see https://developer.mozilla.org/en/Core_JavaScript_1.5_Reference/Global_Objects/JavaArray
 */
var JavaArray;

/**
 * @see https://developer.mozilla.org/en/Core_JavaScript_1.5_Reference/Global_Objects/JavaClass
 */
var JavaClass;

// We just ripped this from the FF source; it doesn't appear to be
// publicly documented.
var JavaMember;

/**
 * @see https://developer.mozilla.org/en/Core_JavaScript_1.5_Reference/Global_Objects/JavaObject
 */
var JavaObject;

/**
 * @see https://developer.mozilla.org/en/Core_JavaScript_1.5_Reference/Global_Objects/JavaPackage
 */
var JavaPackage;

/**
 * @see https://developer.mozilla.org/en/Core_JavaScript_1.5_Reference/Global_Objects/Packages
 */
var Packages;

/**
 * @see https://developer.mozilla.org/en/Core_JavaScript_1.5_Reference/Global_Objects/java
 */
var java;

/**
 * @see https://developer.mozilla.org/en/Core_JavaScript_1.5_Reference/Global_Objects/netscape
 */
var netscape;

/**
 * @param {*} message
 * @see https://developer.mozilla.org/en/DOM/window.alert
 * @return {undefined}
 */
function alert(message) {}

/**
 * @param {number|undefined|null} immediateID
 * @see https://developer.mozilla.org/en-US/docs/DOM/window.clearImmediate
 * @see http://msdn.microsoft.com/en-us/library/ie/hh924825(v=vs.85).aspx
 * @return {undefined}
 */
function clearImmediate(immediateID) {}

/**
 * @param {number|undefined?} intervalID
 * @see https://developer.mozilla.org/en/DOM/window.clearInterval
 * @suppress {duplicate}
 * @return {undefined}
 */
function clearInterval(intervalID) {}

/**
 * @param {number|undefined?} timeoutID
 * @see https://developer.mozilla.org/en/DOM/window.clearTimeout
 * @suppress {duplicate}
 * @return {undefined}
 */
function clearTimeout(timeoutID) {}

/**
 * @param {*} message
 * @return {boolean}
 * @see https://developer.mozilla.org/en/DOM/window.confirm
 */
function confirm(message) {}

/**
 * @see https://developer.mozilla.org/en/DOM/window.dump
 * @param {*} x
 * @return {undefined}
 */
function dump(x) {}

/**
 * @param {string} message
 * @param {string=} opt_value
 * @return {?string}
 * @see https://developer.mozilla.org/en/DOM/window.prompt
 */
function prompt(message, opt_value) {}

/**
 * @param {function()} callback
 * @param {...?} callbackParams
 * @return {number}
 * @see https://developer.mozilla.org/en-US/docs/DOM/window.setImmediate
 * @see http://msdn.microsoft.com/en-us/library/ie/hh773176(v=vs.85).aspx
 */
function setImmediate(callback, callbackParams) {}

/**
 * @param {Function|!TrustedScript|string} callback
 * @param {number=} opt_delay
 * @param {...?} callbackParams
 * @return {number}
 * @see https://developer.mozilla.org/en/DOM/window.setInterval
 * @see https://html.spec.whatwg.org/multipage/webappapis.html#timers
 */
function setInterval(callback, opt_delay, callbackParams) {}

/**
 * @param {Function|!TrustedScript|string} callback
 * @param {number=} opt_delay
 * @param {...*} callbackParams
 * @return {number}
 * @see https://developer.mozilla.org/en/DOM/window.setTimeout
 * @see https://html.spec.whatwg.org/multipage/webappapis.html#timers
 */
function setTimeout(callback, opt_delay, callbackParams) {}

/**
 * Returns whether the object has a property with the specified name.
 *
 * @param {*} propertyName Implicitly cast to a string.
 * @return {boolean}
 * @nosideeffects
 * @see http://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/hasOwnProperty
 */
function hasOwnProperty (propertyName) {}
