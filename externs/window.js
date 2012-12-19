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
 * @fileoverview JavaScript Built-Ins for windows properties.
 *
 * @externs
 */

// Window properties
// Only common properties are here.  Others such as open()
// should be used with an explicit Window object.

/**
 * @type {!Window}
 * @see https://developer.mozilla.org/en/DOM/window.top
 * @const
 */
var top;

/**
 * @type {Navigator}
 * @see https://developer.mozilla.org/en/DOM/window.navigator
 * @const
 */
var navigator;

/**
 * @type {!HTMLDocument}
 * @see https://developer.mozilla.org/en/DOM/window.document
 * @const
 */
var document;

/**
 * @type {Location}
 * @see https://developer.mozilla.org/en/DOM/window.location
 * @const
 * @suppress {duplicate}
 */
var location;

/**
 * @see https://developer.mozilla.org/En/DOM/Window.screen
 * @const
 */
var screen;

/**
 * @type {!Window}
 * @see https://developer.mozilla.org/En/DOM/Window.self
 * @const
 */
var self;

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
 * @see https://developer.mozilla.org/en/Core_JavaScript_1.5_Reference/Global_Objects/sun
 */
var sun;

// Magic variable for Norton Identity Protection's Chrome extension.  This
// program will overwrite whatever's stored in the variable 'o' with random
// values, so we want to avoid it.
// NOTE(user): Added 8-30-2012.  We may want to remove this once Norton
// Identity Protection has been fixed and pushed to most people.
var o;

/**
 * @see https://developer.mozilla.org/en/DOM/window.alert
 */
function alert(x) {}

/**
 * @param {number|undefined?} intervalID
 * @see https://developer.mozilla.org/en/DOM/window.clearInterval
 */
function clearInterval(intervalID) {}

/**
 * @param {number|undefined?} timeoutID
 * @see https://developer.mozilla.org/en/DOM/window.clearTimeout
 */
function clearTimeout(timeoutID) {}

/**
 * @see https://developer.mozilla.org/en/DOM/window.confirm
 */
function confirm(x) {}

/**
 * @see https://developer.mozilla.org/en/DOM/window.dump
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
 * @param {Function|string} callback
 * @param {number} delay
 * @return {number}
 * @see https://developer.mozilla.org/en/DOM/window.setInterval
 * @see https://msdn.microsoft.com/en-us/library/ms536749(v=VS.85).aspx
 */
function setInterval(callback, delay) {}

/**
 * @param {Function|string} callback
 * @param {number} delay
 * @return {number}
 * @see https://developer.mozilla.org/en/DOM/window.setTimeout
 * @see https://msdn.microsoft.com/en-us/library/ms536753(VS.85).aspx
 */
function setTimeout(callback, delay) {}
