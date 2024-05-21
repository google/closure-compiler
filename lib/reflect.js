/**
 * @license
 * Copyright The Closure Library Authors.
 * Copyright The Closure Compiler Authors.
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * @fileoverview Useful compiler idioms related to renaming.
 */

goog.provide('goog.reflect');


/**
 * Syntax for object literal casts.
 * @see https://goo.gl/CRs09P
 *
 * Use this if you have an object literal whose keys need to have the same names
 * as the properties of some class even after they are renamed by the compiler.
 *
 * @param {!Function} type Type to cast to.
 * @param {!Object} object Object literal to cast.
 * @return {!Object} The object literal.
 */
goog.reflect.object = function(type, object) {
  'use strict';
  return object;
};


/**
 * Syntax for renaming property strings.
 * @see https://goo.gl/CRs09P
 *
 * Use this if you have an need to access a property as a string, but want
 * to also have the property renamed by the compiler. In contrast to
 * goog.reflect.object, this method takes an instance of an object.
 *
 * Properties must be simple names (not qualified names).
 *
 * @param {string} prop Name of the property
 * @param {!Object} object Instance of the object whose type will be used
 *     for renaming
 * @return {string} The renamed property.
 */
goog.reflect.objectProperty = function(prop, object) {
  'use strict';
  return prop;
};


/**
 * To assert to the compiler that an operation is needed when it would
 * otherwise be stripped. For example:
 *
 * ```javascript
 * // Force a layout
 * goog.reflect.sinkValue(dialog.offsetHeight);
 * ```
 * @param {T} x
 * @return {T}
 * @template T
 */
goog.reflect.sinkValue = function(x) {
  'use strict';
  goog.reflect.sinkValue[' '](x);
  return x;
};


/**
 * The compiler should optimize this function away iff no one ever uses
 * goog.reflect.sinkValue.
 */
goog.reflect.sinkValue[' '] = function() {};
