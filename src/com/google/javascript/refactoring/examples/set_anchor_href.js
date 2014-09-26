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
 * HTMLAnchorElement.prototype.href DOM property with a call to the
 * goog.dom.safe.setAnchorHref wrapper.
 *
 * For benign URLs, setAnchorHref simply forwards the provided URL to the
 * underlying DOM property. For malicious URLs (such as 'javascript:evil()')
 * however, the URL is sanitized and replaced with an innocuous value.
 *
 * As such, using the safe wrapper prevents XSS vulnerabilities that would
 * otherwise be present if the URL is derived from untrusted input.
 */

goog.require('goog.dom.safe');


/**
 * +require {goog.dom.safe}
 * @param {!HTMLAnchorElement} anchor
 * @param {string} url
 */
function before_setAnchorHref(anchor, url) {
  anchor.href = url;
}

/**
 * @param {!HTMLAnchorElement} anchor
 * @param {string} url
 */
function after_setAnchorHref(anchor, url) {
  goog.dom.safe.setAnchorHref(anchor, url);
}
