/*
 * Copyright 2016 The Closure Compiler Authors.
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
 * @fileoverview RefasterJS templates for replacing direct use of XSS-prone
 * "navigational" DOM APIs with corresponding goog.dom.safe wrapper functions.
 *
 * Navigational APIs include:  Assignments to the .href property, assignments to
 * the .location property, and method invocations on the Location object.
 *
 * For benign URLs, the wraper functions (such as,
 * goog.dom.safe.setLocationHref) simply forward the provided URL to the
 * underlying DOM property. For malicious URLs (such as 'javascript:evil()')
 * however, the URL is sanitized and replaced with an innocuous value.
 *
 * As such, using the safe wrapper prevents XSS vulnerabilities that would
 * otherwise be present if the URL is derived from untrusted input.
 *
 * The suite of templates in this file relies on the fact that RefasterJS
 * attempts matches in the order of the before-templates as specified in the
 * file. For each property/method, there is a template that matches in a
 * precisely typed context, followed by templates that match in more loosely
 * typed contexts (e.g., where an argument type is nullable), followed by a
 * "catch-all" template that matches in an un-typed context. The catch-all
 * template's after-template includes a call to a dummy marker function to flag
 * such matches for human review.
 */

goog.require('goog.asserts');
goog.require('goog.dom.safe');

/**
 * A function that serves as a marker for code that requires human review after
 * refactoring (e.g., code that resulted from refactoring of poorly typed
 * source). It is declared here to allow this template file to compile;
 * however it won't be declared in post-refactoring code and hence will
 * intentionally cause a compilation error to flag such code for human review.
 * @param {?} x
 * @return {?}
 */
function requiresReview(x) {
  return x;
}

//
// Refactorings for assignments to the href property
//


// Refactorings for assignments to the href property of a Location object into
// use of the corresponding safe wrapper, goog.dom.safe.setLocationHref.
//
// The wrapper expects a non-nullable Location. The first template matches the
// non-nullable type exactly. The second template matches the nullable type,
// which is accounted for in the after-template using an assert to coerce the
// value to the non-nullable type.

/**
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
 * +require {goog.asserts}
 * +require {goog.dom.safe}
 * @param {Location|null|undefined} loc The location object.
 * @param {string} url The url.
 */
function before_setLocationHrefNullable(loc, url) {
  loc.href = url;
}

/**
 * @param {Location|null|undefined} loc The location object.
 * @param {string} url The url.
 */
function after_setLocationHrefNullable(loc, url) {
  goog.dom.safe.setLocationHref(goog.asserts.assert(loc), url);
}


// Refactorings for assignments to the href property of a HTMLAnchorElement
// object into use of the corresponding safe wrapper,
// goog.dom.safe.setAnchorHref.
//
// The wrapper expects a non-nullable HTMLAnchorElement. The first template
// matches the non-nullable type exactly. The second template matches the
// nullable type, which is accounted for in the after-template using an assert
// to coerce the value to the non-nullable type.


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

/**
 * +require {goog.asserts}
 * +require {goog.dom.safe}
 * @param {HTMLAnchorElement|null|undefined} anchor
 * @param {string} url
 */
function before_setAnchorHrefNullable(anchor, url) {
  anchor.href = url;
}

/**
 * @param {HTMLAnchorElement|null|undefined} anchor
 * @param {string} url
 */
function after_setAnchorHrefNullable(anchor, url) {
  goog.dom.safe.setAnchorHref(goog.asserts.assert(anchor), url);
}


// Template to rewrite assignments to an href property of a target of
// unknown type. This acts as a catch-all rule for assignments to href that have
// not been matched by the specifically-typed rules above.
//
// Since these matches are based on incomplete type information and hence
// possibly incorrect, they are flagged for human review. This is accomplished
// by inserting a call to a dummy marker function that is easily greppable for,
// and won't be defined in code under refactoring (i.e., will intentionally
// result in a compilation error).

/**
 * +require {goog.asserts}
 * +require {goog.dom.safe}
 * @param {?} thing1
 * @param {?} thing2
 */
function before_setHrefUnknown(thing1, thing2) {
  thing1.href = thing2;
}

/**
 * @param {?} thing1
 * @param {?} thing2
 */
function after_setHrefUnknown(thing1, thing2) {
  goog.dom.safe.setLocationHref(
      goog.asserts.assertInstanceof(requiresReview(thing1), Location), thing2);
}

//
// Refactorings for assignments to the .location property. This accounts for the
// implicit cast to !Location for assignments to this property.
//


/**
 * +require {goog.dom.safe}
 * @param {?Window} win The window object.
 * @param {string} url The url.
 */
function before_setWindowLocation(win, url) {
  win.location = url;
}

/**
 * @param {?Window} win The window object.
 * @param {string} url The url.
 */
function after_setWindowLocation(win, url) {
  goog.dom.safe.setLocationHref(win.location, url);
}

/**
 * +require {goog.dom.safe}
 * @param {?Document} doc
 * @param {string} url
 */
function before_setDocumentLocation(doc, url) {
  doc.location = url;
}

/**
 * @param {?Document} doc
 * @param {string} url
 */
function after_setDocumentLocation(doc, url) {
  goog.dom.safe.setLocationHref(doc.location, url);
}

// Catch-all for matches in un-typed contexts.

/**
 * +require {goog.asserts}
 * +require {goog.dom.safe}
 * @param {?} thing1
 * @param {?} thing2
 */
function before_setLocationUnknown(thing1, thing2) {
  thing1.location = thing2;
}

/**
 * @param {?} thing1
 * @param {?} thing2
 */
function after_setLocationUnknown(thing1, thing2) {
  goog.dom.safe.setLocationHref(
      goog.asserts.assertInstanceof(requiresReview(thing1), Window).location,
      thing2);
}
