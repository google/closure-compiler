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
 * @fileoverview Test cases for the navigational_xss_sinks.js RefasterJs
 * template.
 */

goog.provide('refactoring_testcase');

goog.require('goog.asserts');
goog.require('goog.dom.safe');
goog.require('test_dependency');

//
// Test cases for .href assignments in well-typed contexts.
//

/**
 * @param {!Location} target The target.
 * @param {string} val The value.
 */
refactoring_testcase.nonnull_location_href = function(target, val) {
  // Should match before_setLocationHref.
  goog.dom.safe.setLocationHref(target, val);
};

/**
 * @param {?Location} target The target.
 * @param {string} val The value.
 */
refactoring_testcase.nullable_location_href = function(target, val) {
  // Should match before_setLocationHrefNullable.
  goog.dom.safe.setLocationHref(goog.asserts.assert(target), val);
};


//
// Test cases for .href assignments that match the un-typed catch-all rule.
//


/**
 * @param {!Location|!Element} target The target.
 * @param {string} val The value.
 */
refactoring_testcase.union_type_href = function(target, val) {
  // Should match before_setHrefUnknown.
  goog.dom.safe.setLocationHref(goog.asserts.assertInstanceof(requiresReview(target), 
Location), val);
};

/**
 * @param {?} targetElement The target.
 * @param {string} val The value.
 */
refactoring_testcase.unknowntype_href = function(targetElement, val) {
  // Should match before_setHrefUnknown.
  goog.dom.safe.setLocationHref(goog.asserts.assertInstanceof(requiresReview(targetElement), 
Location), val);
};

//
// Test cases for .location assignments in well-typed contexts.
//

/**
 * @param {!Window} target The target.
 * @param {string} val The value.
 */
refactoring_testcase.window_location = function(target, val) {
  // Should match before_setWindowLocation.
  goog.dom.safe.setLocationHref(target.location, val);
};

/**
 * @param {?Window} target The target.
 * @param {string} val The value.
 */
refactoring_testcase.window_location_nullable = function(target, val) {
  // Should match before_setWindowLocation.
  goog.dom.safe.setLocationHref(target.location, val);
};

/**
 * @param {!Document} target The target.
 * @param {string} val The value.
 */
refactoring_testcase.document_location = function(target, val) {
  // Should match before_setDocumentLocation.
  goog.dom.safe.setLocationHref(target.location, val);
};

/**
 * @param {?Document} target The target.
 * @param {string} val The value.
 */
refactoring_testcase.document_location_nullable = function(target, val) {
  // Should match before_setDocumentLocation.
  goog.dom.safe.setLocationHref(target.location, val);
};

/**
 * @param {?HTMLDocument} target The target.
 * @param {string} val The value.
 */
refactoring_testcase.document_location_subtype = function(target, val) {
  // Should match before_setDocumentLocation since {?HTMLDocument} is a subtype
  // of {?Document}.
  goog.dom.safe.setLocationHref(target.location, val);
};

//
// Test cases for .location assignments in poorly-typed contexts.
//

/**
 * @param {?} target The target.
 * @param {string} val The value.
 */
refactoring_testcase.unknown_location = function(target, val) {
  // Should match before_setLocationUnknown.
  goog.dom.safe.setLocationHref(goog.asserts.assertInstanceof(requiresReview(target), 
Window).location, val);
};

/**
 * @param {Object} target The target.
 * @param {string} val The value.
 */
refactoring_testcase.object_location = function(target, val) {
  // Should match before_setLocationUnknown.
  goog.dom.safe.setLocationHref(goog.asserts.assertInstanceof(requiresReview(target), 
Window).location, val);
};


/**
 * @param {*} target The target.
 * @param {string} val The value.
 */
refactoring_testcase.any_location = function(target, val) {
  // Should match before_setLocationUnknown.
  goog.dom.safe.setLocationHref(goog.asserts.assertInstanceof(requiresReview(target), 
Window).location, val);
};
