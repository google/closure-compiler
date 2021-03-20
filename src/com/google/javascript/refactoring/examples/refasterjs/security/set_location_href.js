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
 * Location.prototype.href DOM property with a
 * call to the goog.dom.safe.setLocationHref wrapper.
 *
 * For benign URLs, setLocationHref simply forwards the provided URL to the
 * underlying DOM property. For malicious URLs (such as 'javascript:evil()')
 * however, the URL is sanitized and replaced with an innocuous value.
 *
 * As such, using the safe wrapper prevents XSS vulnerabilities that would
 * otherwise be present if the URL is derived from untrusted input.
 */

goog.require('goog.asserts');
goog.require('goog.dom.safe');

/**
 * @param {?} loc
 * @param {string} string_literal_thing2
 */
function do_not_change_setHrefStringLiteral(loc, string_literal_thing2) {
  loc.href = string_literal_thing2;
}


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
 * Replaces writes to Location.property.href with a call to the corresponding
 * goog.dom.safe.setLocationHref wrapper.
 * +require {goog.asserts}
 * +require {goog.dom.safe}
 * @param {!Location} loc The location object.
 * @param {?} url The url.
 */
function before_setLocationUntypedHref(loc, url) {
  loc.href = url;
}

/**
 * @param {!Location} loc The location object.
 * @param {?} url The url.
 */
function after_setLocationUntypedHref(loc, url) {
  // TODO(bangert): add test once we have go/api-prohibition-design
  // (which will re-do the test infrastructure for this).
  // TODO(bangert): add tests for nullable locations
  goog.dom.safe.setLocationHref(loc, goog.asserts.assertString(url));
}
