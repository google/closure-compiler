/*
 * Copyright 2014 The Closure Compiler Authors.
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
 * @fileoverview RefasterJS templates for replacing direct access to the
 * Location.prototype.href and Window.prototype.location DOM properties with a
 * call to the goog.dom.safe.setLocationHref wrapper.
 *
 * For benign URLs, setLocationHref simply forwards the provided URL to the
 * underlying DOM property. For malicious URLs (such as 'javascript:evil()')
 * however, the URL is sanitized and replaced with an innocuous value.
 *
 * As such, using the safe wrapper prevents XSS vulnerabilities that would
 * otherwise be present if the URL is derived from untrusted input.
 */

goog.require('goog.dom.safe');


/**
 * Replaces writes to Location.property.href with a call to the corresponding
 * goog.dom.safe.setLocationHref wrapper.
 * +require {goog.dom.safe}
 * @param {!Location} loc The location object.
 * @param {string} url The url.
 */
function before_setLocationHref(loc, url) {
  loc.href = url;
}

/**
 * @param {!Location} loc The location object.
 * @param {string} url The url.
 */
function after_setLocationHref(loc, url) {
  goog.dom.safe.setLocationHref(loc, url);
}

/**
 * Replaces writes to Window.property.location with a call to the corresponding
 * goog.dom.safe.setLocationHref wrapper.
 * +require {goog.dom.safe}
 * @param {!Window} win The window object.
 * @param {string} url The url.
 */
function before_setWindowLocation(win, url) {
  win.location = url;
}

/**
 * @param {!Window} win The window object.
 * @param {string} url The url.
 */
function after_setWindowLocation(win, url) {
  goog.dom.safe.setLocationHref(win.location, url);
}
