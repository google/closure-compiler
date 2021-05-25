/*
 * Copyright 2017 The Closure Compiler Authors.
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
 * Element.prototype.href DOM property with a call to the
 * goog.dom.safe.setAnchorHref and goog.dom.asserts. wrapper.
 * Ignore assignments to HTMLAnchorElement.prototype.href.
 *
 * For benign URLs, setAnchorHref simply forwards the provided URL to the
 * underlying DOM property. For malicious URLs (such as 'javascript:evil()')
 * however, the URL is sanitized and replaced with an innocuous value.
 *
 * As such, using the safe wrapper prevents XSS vulnerabilities that would
 * otherwise be present if the URL is derived from untrusted input.
 */

goog.require('goog.asserts');
goog.require('goog.dom.asserts');
goog.require('goog.dom.safe');


/**
 * HTMLAnchorElement is handled in set_anchor_href.js
 * @param {!HTMLAnchorElement} anchor
 * @param {string} url
 */
function do_not_change_setAnchorHref(anchor, url) {
  anchor.href = url;
}

/**
 * @param {?} anchor
 * @param {string} string_literal_url
 */
function do_not_change_stringLiteral(anchor, string_literal_url) {
  anchor.href = string_literal_url;
}

/**
 * Assume all Elements with href are actually HTMLAnchorElement but poorly
 * typed.
 *
 * We insert a goog.dom.assert to make sure they are an Element. Tests will fail
 * if it is a link element (which should use setLinkHrefAndRel).
 */

/**
 * +require {goog.dom.asserts}
 * +require {goog.dom.safe}
 * @param {!Element} anchor
 * @param {!string} url
 */
function before_setElementHref(anchor, url) {
  anchor.href = url;
}


/**
 * @param {!Element} anchor
 * @param {!string} url
 */
function after_setElementHref(anchor, url) {
  goog.dom.safe.setAnchorHref(
      goog.dom.asserts.assertIsHTMLAnchorElement(anchor), url);
}

/**
 * +require {goog.asserts}
 * +require {goog.dom.asserts}
 * +require {goog.dom.safe}
 * @param {Element|null|undefined} anchor
 * @param {!string} url
 */
function before_setElementHrefOptionalDefiniteUrl(anchor, url) {
  anchor.href = url;
}

/**
 * @param {Element|null|undefined} anchor
 * @param {!string} url
 */
function after_setElementHrefOptionalDefiniteUrl(anchor, url) {
  goog.dom.safe.setAnchorHref(
      goog.dom.asserts.assertIsHTMLAnchorElement(goog.asserts.assert(anchor)),
      url);
}

/**
 * +require {goog.asserts}
 * +require {goog.dom.asserts}
 * +require {goog.dom.safe}
 * @param {!Element} anchor
 * @param {!string|null|undefined} url
 */
function before_setElementDefiniteHrefOptional(anchor, url) {
  anchor.href = url;
}


/**
 * @param {!Element} anchor
 * @param {!string|null|undefined} url
 */
function after_setElementDefiniteHrefOptional(anchor, url) {
  goog.dom.safe.setAnchorHref(
      goog.dom.asserts.assertIsHTMLAnchorElement(anchor),
      goog.asserts.assertString(url));
}

/**
 * +require {goog.asserts}
 * +require {goog.dom.asserts}
 * +require {goog.dom.safe}
 * @param {!Element|null|undefined} anchor
 * @param {?} url
 */
function before_setElementHrefBothOptional(anchor, url) {
  anchor.href = url;
}


/**
 * @param {!Element|null|undefined} anchor
 * @param {?} url
 */
function after_setElementHrefBothOptional(anchor, url) {
  goog.dom.safe.setAnchorHref(
      goog.dom.asserts.assertIsHTMLAnchorElement(goog.asserts.assert(anchor)),
      goog.asserts.assertString(url));
}
