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
 * @fileoverview Definitions for IE's custom CSS properties, as defined here:
 * http://msdn.microsoft.com/en-us/library/aa768661(VS.85).aspx
 *
 * This page is also useful for the IDL definitions:
 * http://source.winehq.org/source/include/mshtml.idl
 *
 * @externs
 * @author nicksantos@google.com
 */

/** @type {Element} */
StyleSheet.prototype.owningElement;

/** @type {boolean} */
StyleSheet.prototype.readOnly;

/** @type {StyleSheetList} */
StyleSheet.prototype.imports;

/** @type {string} */
StyleSheet.prototype.id;

/**
 * @param {string} bstrURL
 * @param {number} lIndex
 * @return {number}
 */
StyleSheet.prototype.addImport;

/**
 * @param {string} bstrSelector
 * @param {string} bstrStyle
 * @param {number} lIndex
 * @return {number}
 */
StyleSheet.prototype.addRule;

/**
 * @param {number} lIndex
 */
StyleSheet.prototype.removeImport;

/**
 * @param {number} lIndex
 */
StyleSheet.prototype.removeRule;

/** @type {string} */
StyleSheet.prototype.cssText;

/** @type {CSSRuleList} */
StyleSheet.prototype.rules;

// StyleSheet methods

/**
 * @param {string} propName
 * @return {string}
 * @see http://msdn.microsoft.com/en-us/library/aa358797(VS.85).aspx
 */
StyleSheet.prototype.getExpression;

/**
 * @param {string} name
 * @param {string} expression
 * @return {undefined}
 * @see http://msdn.microsoft.com/en-us/library/ms531196(VS.85).aspx
 */
StyleSheet.prototype.setExpression;

/**
 * @param {string} expression
 * @return {undefined}
 * @see http://msdn.microsoft.com/en-us/library/aa358798(VS.85).aspx
 */
StyleSheet.prototype.removeExpression;

// IE-only CSS style names.

/** @type {string} */ CSSProperties.prototype.backgroundPositionX;

/** @type {string} */ CSSProperties.prototype.backgroundPositionY;

/** @type {string} */ CSSProperties.prototype.msInterpolationMode;

/** @type {string} */ CSSProperties.prototype.overflowX;

/** @type {string} */ CSSProperties.prototype.overflowY;

/** @type {number} */ CSSProperties.prototype.pixelWidth;

/** @type {number} */ CSSProperties.prototype.pixelHeight;

/** @type {number} */ CSSProperties.prototype.pixelLeft;

/** @type {number} */ CSSProperties.prototype.pixelTop;

/** @type {string} */ CSSProperties.prototype.styleFloat;

/** @type {string|number} */ CSSProperties.prototype.zoom;
