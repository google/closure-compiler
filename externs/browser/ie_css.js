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
 * @param {number=} opt_iIndex
 * @return {number}
 * @see http://msdn.microsoft.com/en-us/library/aa358796%28v=vs.85%29.aspx
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

/** @type {string} */ CSSStyleProperties.prototype.backgroundPositionX;

/** @type {string} */ CSSStyleProperties.prototype.backgroundPositionY;

/**
 * @see http://msdn.microsoft.com/en-us/library/ie/ms531081(v=vs.85).aspx
 * NOTE: Left untyped to avoid conflict with caller.
 */
CSSStyleProperties.prototype.behavior;

/**
 * @type {string}
 * @see http://msdn.microsoft.com/en-us/library/ms533883.aspx
 */
CSSStyleProperties.prototype.imeMode;

/**
 * @type {string}
 * @see http://msdn.microsoft.com/en-us/library/ms534176(VS.85).aspx
 */
CSSStyleProperties.prototype.msInterpolationMode;

/** @type {string} */ CSSStyleProperties.prototype.overflowX;

/** @type {string} */ CSSStyleProperties.prototype.overflowY;

/** @type {number} */ CSSStyleProperties.prototype.pixelWidth;

/** @type {number} */ CSSStyleProperties.prototype.pixelHeight;

/** @type {number} */ CSSStyleProperties.prototype.pixelLeft;

/** @type {number} */ CSSStyleProperties.prototype.pixelTop;

/** @type {string} */ CSSStyleProperties.prototype.styleFloat;

/**
 * @type {string|number}
 * @see http://msdn.microsoft.com/en-us/library/ms535169(VS.85).aspx
 */
CSSStyleProperties.prototype.zoom;

/**
 * @type {string}
 * @see http://msdn.microsoft.com/en-us/library/ms535153(VS.85).aspx
 */
CSSStyleProperties.prototype.writingMode;

/**
 * IE-specific extensions.
 * @see http://blogs.msdn.com/b/ie/archive/2008/09/08/microsoft-css-vendor-extensions.aspx
 */

/** @type {string} */
CSSStyleProperties.prototype.MsAccelerator;

/** @type {string} */
CSSStyleProperties.prototype.MsBackgroundPositionX;

/** @type {string} */
CSSStyleProperties.prototype.MsBackgroundPositionY;

/** @type {string} */
CSSStyleProperties.prototype.MsBehavior;

/** @type {string} */
CSSStyleProperties.prototype.MsBlockProgression;

/** @type {string} */
CSSStyleProperties.prototype.MsFilter;

/** @type {string} */
CSSStyleProperties.prototype.MsImeMode;

/** @type {string} */
CSSStyleProperties.prototype.MsLayoutGrid;

/** @type {string} */
CSSStyleProperties.prototype.MsLayoutGridChar;

/** @type {string} */
CSSStyleProperties.prototype.MsLayoutGridLine;

/** @type {string} */
CSSStyleProperties.prototype.MsLayoutGridMode;

/** @type {string} */
CSSStyleProperties.prototype.MsLayoutGridType;

/** @type {string} */
CSSStyleProperties.prototype.MsLineBreak;

/** @type {string} */
CSSStyleProperties.prototype.MsLineGridMode;

/** @type {string} */
CSSStyleProperties.prototype.MsInterpolationMode;

/** @type {string} */
CSSStyleProperties.prototype.MsOverflowX;

/** @type {string} */
CSSStyleProperties.prototype.MsOverflowY;

/** @type {string} */
CSSStyleProperties.prototype.MsScrollbar3dlightColor;

/** @type {string} */
CSSStyleProperties.prototype.MsScrollbarArrowColor;

/** @type {string} */
CSSStyleProperties.prototype.MsScrollbarBaseColor;

/** @type {string} */
CSSStyleProperties.prototype.MsScrollbarDarkshadowColor;

/** @type {string} */
CSSStyleProperties.prototype.MsScrollbarFaceColor;

CSSStyleProperties.prototype.MsScrollbarHighlightColor;

/** @type {string} */
CSSStyleProperties.prototype.MsScrollbarShadowColor;

/** @type {string} */
CSSStyleProperties.prototype.MsScrollbarTrackColor;

/** @type {string} */
CSSStyleProperties.prototype.MsTextAlignLast;

/** @type {string} */
CSSStyleProperties.prototype.MsTextAutospace;

/** @type {string} */
CSSStyleProperties.prototype.MsTextJustify;

/** @type {string} */
CSSStyleProperties.prototype.MsTextKashidaSpace;

/** @type {string} */
CSSStyleProperties.prototype.MsTextOverflow;

/** @type {string} */
CSSStyleProperties.prototype.MsTextUnderlinePosition;

/** @type {string} */
CSSStyleProperties.prototype.MsWordBreak;

/** @type {string} */
CSSStyleProperties.prototype.MsWordWrap;

/** @type {string} */
CSSStyleProperties.prototype.MsWritingMode;

/** @type {string} */
CSSStyleProperties.prototype.MsZoom;

/** @type {string} */
CSSStyleProperties.prototype.msUserSelect;

// See: http://msdn.microsoft.com/en-us/library/windows/apps/Hh702466.aspx

/** @type {string} */
CSSStyleProperties.prototype.msContentZooming;

/** @type {string} */
CSSStyleProperties.prototype.msTouchAction;

/** @type {string} */
CSSStyleProperties.prototype.msTransform;

/** @type {string} */
CSSStyleProperties.prototype.msTransition;
