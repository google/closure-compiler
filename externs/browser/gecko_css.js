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
 * @fileoverview Definitions for Gecko's custom CSS properties. Copied from:
 * http://mxr.mozilla.org/mozilla2.0/source/dom/interfaces/css/nsIDOMCSS2Properties.idl
 *
 * @externs
 * @author nicksantos@google.com (Nick Santos)
 */


/** @type {string} */ CSSStyleProperties.prototype.MozAppearance;
/** @type {string} */ CSSStyleProperties.prototype.MozBackfaceVisibility;
/** @type {string} */ CSSStyleProperties.prototype.MozBackgroundClip;
/** @type {string} */ CSSStyleProperties.prototype.MozBackgroundInlinePolicy;
/** @type {string} */ CSSStyleProperties.prototype.MozBackgroundOrigin;
/** @type {string} */ CSSStyleProperties.prototype.MozBinding;
/** @type {string} */ CSSStyleProperties.prototype.MozBorderBottomColors;
/** @type {string} */ CSSStyleProperties.prototype.MozBorderEnd;
/** @type {string} */ CSSStyleProperties.prototype.MozBorderEndColor;
/** @type {string} */ CSSStyleProperties.prototype.MozBorderEndStyle;
/** @type {string} */ CSSStyleProperties.prototype.MozBorderEndWidth;
/** @type {string} */ CSSStyleProperties.prototype.MozBorderImage;
/** @type {string} */ CSSStyleProperties.prototype.MozBorderLeftColors;
/** @type {string} */ CSSStyleProperties.prototype.MozBorderRadius;
/** @type {string} */ CSSStyleProperties.prototype.MozBorderRadiusTopleft;
/** @type {string} */ CSSStyleProperties.prototype.MozBorderRadiusTopright;
/** @type {string} */ CSSStyleProperties.prototype.MozBorderRadiusBottomleft;
/** @type {string} */ CSSStyleProperties.prototype.MozBorderRadiusBottomright;
/** @type {string} */ CSSStyleProperties.prototype.MozBorderRightColors;
/** @type {string} */ CSSStyleProperties.prototype.MozBorderStart;
/** @type {string} */ CSSStyleProperties.prototype.MozBorderStartColor;
/** @type {string} */ CSSStyleProperties.prototype.MozBorderStartStyle;
/** @type {string} */ CSSStyleProperties.prototype.MozBorderStartWidth;
/** @type {string} */ CSSStyleProperties.prototype.MozBorderTopColors;
/** @type {string} */ CSSStyleProperties.prototype.MozBoxAlign;
/** @type {string} */ CSSStyleProperties.prototype.MozBoxDirection;
/** @type {string} */ CSSStyleProperties.prototype.MozBoxFlex;
/** @type {string} */ CSSStyleProperties.prototype.MozBoxOrdinalGroup;
/** @type {string} */ CSSStyleProperties.prototype.MozBoxOrient;
/** @type {string} */ CSSStyleProperties.prototype.MozBoxPack;
/** @type {string} */ CSSStyleProperties.prototype.MozBoxSizing;
/** @type {string} */ CSSStyleProperties.prototype.MozBoxShadow;
/** @type {string} */ CSSStyleProperties.prototype.MozColumnCount;
/** @type {string} */ CSSStyleProperties.prototype.MozColumnGap;
/** @type {string} */ CSSStyleProperties.prototype.MozColumnRule;
/** @type {string} */ CSSStyleProperties.prototype.MozColumnRuleColor;
/** @type {string} */ CSSStyleProperties.prototype.MozColumnRuleStyle;
/** @type {string} */ CSSStyleProperties.prototype.MozColumnRuleWidth;
/** @type {string} */ CSSStyleProperties.prototype.MozColumnWidth;
/** @type {string} */ CSSStyleProperties.prototype.MozFloatEdge;
/** @type {string} */ CSSStyleProperties.prototype.MozFontFeatureSettings;
/** @type {string} */ CSSStyleProperties.prototype.MozFontLanguageOverride;
/** @type {string} */ CSSStyleProperties.prototype.MozForceBrokenImageIcon;
/** @type {string} */ CSSStyleProperties.prototype.MozImageRegion;
/** @type {string} */ CSSStyleProperties.prototype.MozMarginEnd;
/** @type {string} */ CSSStyleProperties.prototype.MozMarginStart;
/** @type {number|string} */ CSSStyleProperties.prototype.MozOpacity;
/** @type {string} */ CSSStyleProperties.prototype.MozOutline;
/** @type {string} */ CSSStyleProperties.prototype.MozOutlineColor;
/** @type {string} */ CSSStyleProperties.prototype.MozOutlineOffset;
/** @type {string} */ CSSStyleProperties.prototype.MozOutlineRadius;
/** @type {string} */ CSSStyleProperties.prototype.MozOutlineRadiusBottomleft;
/** @type {string} */ CSSStyleProperties.prototype.MozOutlineRadiusBottomright;
/** @type {string} */ CSSStyleProperties.prototype.MozOutlineRadiusTopleft;
/** @type {string} */ CSSStyleProperties.prototype.MozOutlineRadiusTopright;
/** @type {string} */ CSSStyleProperties.prototype.MozOutlineStyle;
/** @type {string} */ CSSStyleProperties.prototype.MozOutlineWidth;
/** @type {string} */ CSSStyleProperties.prototype.MozPaddingEnd;
/** @type {string} */ CSSStyleProperties.prototype.MozPaddingStart;
/** @type {string} */ CSSStyleProperties.prototype.MozPerspective;
/** @type {string} */ CSSStyleProperties.prototype.MozStackSizing;
/** @type {string} */ CSSStyleProperties.prototype.MozTabSize;
/** @type {string} */ CSSStyleProperties.prototype.MozTransform;
/** @type {string} */ CSSStyleProperties.prototype.MozTransformOrigin;
/** @type {string} */ CSSStyleProperties.prototype.MozTransition;
/** @type {string} */ CSSStyleProperties.prototype.MozTransitionDelay;
/** @type {string} */ CSSStyleProperties.prototype.MozTransitionDuration;
/** @type {string} */ CSSStyleProperties.prototype.MozTransitionProperty;
/** @type {string} */ CSSStyleProperties.prototype.MozTransitionTimingFunction;
/** @type {string} */ CSSStyleProperties.prototype.MozUserFocus;
/** @type {string} */ CSSStyleProperties.prototype.MozUserInput;
/** @type {string} */ CSSStyleProperties.prototype.MozUserModify;
/** @type {string} */ CSSStyleProperties.prototype.MozUserSelect;
/** @type {string} */ CSSStyleProperties.prototype.MozWindowShadow;


// These are non-standard Gecko CSSOM properties on Window.prototype.screen.

/**
 * @type {number}
 * @see https://developer.mozilla.org/En/DOM/window.screen.availTop
 */
Screen.prototype.availTop;

/**
 * @type {number}
 * @see https://developer.mozilla.org/En/DOM/window.screen.availLeft
 */
Screen.prototype.availLeft;

/**
 * @type {number}
 * @see https://developer.mozilla.org/En/DOM/window.screen.left
 */
Screen.prototype.left;

/**
 * @type {number}
 * @see https://developer.mozilla.org/En/DOM/window.screen.top
 */
Screen.prototype.top;
