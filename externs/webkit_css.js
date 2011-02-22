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
 * @fileoverview Definitions for WebKit's custom CSS properties. Copied from:
 * http://trac.webkit.org/browser/trunk/WebCore/css/CSSPropertyNames.in
 *
 * If you make changes to this file, notice that every property appears
 * twice: once as an uppercase name and once as a lowercase name.
 * Webkit allows both. The uppercase version is preferred.
 *
 * @externs
 * @author nicksantos@google.com (Nick Santos)
 */

/** @type {string} */ CSSProperties.prototype.WebkitAppearance;
/** @type {string} */ CSSProperties.prototype.WebkitBackgroundClip;
/** @type {string} */ CSSProperties.prototype.WebkitBackgroundComposite;
/** @type {string} */ CSSProperties.prototype.WebkitBackgroundOrigin;
/** @type {string} */ CSSProperties.prototype.WebkitBackgroundSize;
/** @type {string} */ CSSProperties.prototype.WebkitBinding;
/** @type {string} */ CSSProperties.prototype.WebkitBorderBottomLeftRadius;
/** @type {string} */ CSSProperties.prototype.WebkitBorderBottomRightRadius;
/** @type {string} */ CSSProperties.prototype.WebkitBorderFit;
/** @type {string} */ CSSProperties.prototype.WebkitBorderHorizontalSpacing;
/** @type {string} */ CSSProperties.prototype.WebkitBorderImage;
/** @type {string} */ CSSProperties.prototype.WebkitBorderRadius;
/** @type {string} */ CSSProperties.prototype.WebkitBorderTopLeftRadius;
/** @type {string} */ CSSProperties.prototype.WebkitBorderTopRightRadius;
/** @type {string} */ CSSProperties.prototype.WebkitBorderVerticalSpacing;
/** @type {string} */ CSSProperties.prototype.WebkitBoxAlign;
/** @type {string} */ CSSProperties.prototype.WebkitBoxDirection;
/** @type {string} */ CSSProperties.prototype.WebkitBoxFlex;
/** @type {string} */ CSSProperties.prototype.WebkitBoxFlexGroup;
/** @type {string} */ CSSProperties.prototype.WebkitBoxLines;
/** @type {string} */ CSSProperties.prototype.WebkitBoxOrdinalGroup;
/** @type {string} */ CSSProperties.prototype.WebkitBoxOrient;
/** @type {string} */ CSSProperties.prototype.WebkitBoxPack;
/** @type {string} */ CSSProperties.prototype.WebkitBoxShadow;
/** @type {string} */ CSSProperties.prototype.WebkitBoxSizing;
/** @type {string} */ CSSProperties.prototype.WebkitColumnBreakAfter;
/** @type {string} */ CSSProperties.prototype.WebkitColumnBreakBefore;
/** @type {string} */ CSSProperties.prototype.WebkitColumnBreakInside;
/** @type {string} */ CSSProperties.prototype.WebkitColumnCount;
/** @type {string} */ CSSProperties.prototype.WebkitColumnGap;
/** @type {string} */ CSSProperties.prototype.WebkitColumnRule;
/** @type {string} */ CSSProperties.prototype.WebkitColumnRuleColor;
/** @type {string} */ CSSProperties.prototype.WebkitColumnRuleStyle;
/** @type {string} */ CSSProperties.prototype.WebkitColumnRuleWidth;
/** @type {string} */ CSSProperties.prototype.WebkitColumnWidth;
/** @type {string} */ CSSProperties.prototype.WebkitColumns;
/** @type {string} */ CSSProperties.prototype.WebkitDashboardRegion;
/** @type {string} */ CSSProperties.prototype.WebkitFontSizeDelta;
/** @type {string} */ CSSProperties.prototype.WebkitHighlight;
/** @type {string} */ CSSProperties.prototype.WebkitLineBreak;
/** @type {string} */ CSSProperties.prototype.WebkitLineClamp;
/** @type {string} */ CSSProperties.prototype.WebkitMarginBottomCollapse;
/** @type {string} */ CSSProperties.prototype.WebkitMarginCollapse;
/** @type {string} */ CSSProperties.prototype.WebkitMarginStart;
/** @type {string} */ CSSProperties.prototype.WebkitMarginTopCollapse;
/** @type {string} */ CSSProperties.prototype.WebkitMarquee;
/** @type {string} */ CSSProperties.prototype.WebkitMarqueeDirection;
/** @type {string} */ CSSProperties.prototype.WebkitMarqueeIncrement;
/** @type {string} */ CSSProperties.prototype.WebkitMarqueeRepetition;
/** @type {string} */ CSSProperties.prototype.WebkitMarqueeSpeed;
/** @type {string} */ CSSProperties.prototype.WebkitMarqueeStyle;
/** @type {string} */
CSSProperties.prototype.WebkitMatchNearestMailBlockquoteColor;
/** @type {string} */ CSSProperties.prototype.WebkitNbspMode;
/** @type {string} */ CSSProperties.prototype.WebkitPaddingStart;
/** @type {string} */ CSSProperties.prototype.WebkitRtlOrdering;
/** @type {string} */ CSSProperties.prototype.WebkitTextDecorationsInEffect;
/** @type {string} */ CSSProperties.prototype.WebkitTextFillColor;
/** @type {string} */ CSSProperties.prototype.WebkitTextSecurity;
/** @type {string} */ CSSProperties.prototype.WebkitTextSizeAdjust;
/** @type {string} */ CSSProperties.prototype.WebkitTextStroke;
/** @type {string} */ CSSProperties.prototype.WebkitTextStrokeColor;
/** @type {string} */ CSSProperties.prototype.WebkitTextStrokeWidth;
/** @type {string} */ CSSProperties.prototype.WebkitTransform;
/** @type {string} */ CSSProperties.prototype.WebkitTransformOrigin;
/** @type {string} */ CSSProperties.prototype.WebkitTransformOriginX;
/** @type {string} */ CSSProperties.prototype.WebkitTransformOriginY;
/** @type {string} */ CSSProperties.prototype.WebkitTransition;
/** @type {string} */ CSSProperties.prototype.WebkitTransitionDuration;
/** @type {string} */ CSSProperties.prototype.WebkitTransitionProperty;
/** @type {string} */ CSSProperties.prototype.WebkitTransitionRepeatCount;
/** @type {string} */ CSSProperties.prototype.WebkitTransitionTimingFunction;
/** @type {string} */ CSSProperties.prototype.WebkitUserDrag;
/** @type {string} */ CSSProperties.prototype.WebkitUserModify;
/** @type {string} */ CSSProperties.prototype.WebkitUserSelect;

// Webkit also adds bindings for the lowercase versions of these properties.
// The uppercase version is preferred.

/** @type {string} */ CSSProperties.prototype.webkitAppearance;
/** @type {string} */ CSSProperties.prototype.webkitBackgroundClip;
/** @type {string} */ CSSProperties.prototype.webkitBackgroundComposite;
/** @type {string} */ CSSProperties.prototype.webkitBackgroundOrigin;
/** @type {string} */ CSSProperties.prototype.webkitBackgroundSize;
/** @type {string} */ CSSProperties.prototype.webkitBinding;
/** @type {string} */ CSSProperties.prototype.webkitBorderBottomLeftRadius;
/** @type {string} */ CSSProperties.prototype.webkitBorderBottomRightRadius;
/** @type {string} */ CSSProperties.prototype.webkitBorderFit;
/** @type {string} */ CSSProperties.prototype.webkitBorderHorizontalSpacing;
/** @type {string} */ CSSProperties.prototype.webkitBorderImage;
/** @type {string} */ CSSProperties.prototype.webkitBorderRadius;
/** @type {string} */ CSSProperties.prototype.webkitBorderTopLeftRadius;
/** @type {string} */ CSSProperties.prototype.webkitBorderTopRightRadius;
/** @type {string} */ CSSProperties.prototype.webkitBorderVerticalSpacing;
/** @type {string} */ CSSProperties.prototype.webkitBoxAlign;
/** @type {string} */ CSSProperties.prototype.webkitBoxDirection;
/** @type {string} */ CSSProperties.prototype.webkitBoxFlex;
/** @type {string} */ CSSProperties.prototype.webkitBoxFlexGroup;
/** @type {string} */ CSSProperties.prototype.webkitBoxLines;
/** @type {string} */ CSSProperties.prototype.webkitBoxOrdinalGroup;
/** @type {string} */ CSSProperties.prototype.webkitBoxOrient;
/** @type {string} */ CSSProperties.prototype.webkitBoxPack;
/** @type {string} */ CSSProperties.prototype.webkitBoxShadow;
/** @type {string} */ CSSProperties.prototype.webkitBoxSizing;
/** @type {string} */ CSSProperties.prototype.webkitColumnBreakAfter;
/** @type {string} */ CSSProperties.prototype.webkitColumnBreakBefore;
/** @type {string} */ CSSProperties.prototype.webkitColumnBreakInside;
/** @type {string} */ CSSProperties.prototype.webkitColumnCount;
/** @type {string} */ CSSProperties.prototype.webkitColumnGap;
/** @type {string} */ CSSProperties.prototype.webkitColumnRule;
/** @type {string} */ CSSProperties.prototype.webkitColumnRuleColor;
/** @type {string} */ CSSProperties.prototype.webkitColumnRuleStyle;
/** @type {string} */ CSSProperties.prototype.webkitColumnRuleWidth;
/** @type {string} */ CSSProperties.prototype.webkitColumnWidth;
/** @type {string} */ CSSProperties.prototype.webkitColumns;
/** @type {string} */ CSSProperties.prototype.webkitDashboardRegion;
/** @type {string} */ CSSProperties.prototype.webkitFontSizeDelta;
/** @type {string} */ CSSProperties.prototype.webkitHighlight;
/** @type {string} */ CSSProperties.prototype.webkitLineBreak;
/** @type {string} */ CSSProperties.prototype.webkitLineClamp;
/** @type {string} */ CSSProperties.prototype.webkitMarginBottomCollapse;
/** @type {string} */ CSSProperties.prototype.webkitMarginCollapse;
/** @type {string} */ CSSProperties.prototype.webkitMarginStart;
/** @type {string} */ CSSProperties.prototype.webkitMarginTopCollapse;
/** @type {string} */ CSSProperties.prototype.webkitMarquee;
/** @type {string} */ CSSProperties.prototype.webkitMarqueeDirection;
/** @type {string} */ CSSProperties.prototype.webkitMarqueeIncrement;
/** @type {string} */ CSSProperties.prototype.webkitMarqueeRepetition;
/** @type {string} */ CSSProperties.prototype.webkitMarqueeSpeed;
/** @type {string} */ CSSProperties.prototype.webkitMarqueeStyle;
/** @type {string} */
CSSProperties.prototype.webkitMatchNearestMailBlockquoteColor;
/** @type {string} */ CSSProperties.prototype.webkitNbspMode;
/** @type {string} */ CSSProperties.prototype.webkitPaddingStart;
/** @type {string} */ CSSProperties.prototype.webkitRtlOrdering;
/** @type {string} */ CSSProperties.prototype.webkitTextDecorationsInEffect;
/** @type {string} */ CSSProperties.prototype.webkitTextFillColor;
/** @type {string} */ CSSProperties.prototype.webkitTextSecurity;
/** @type {string} */ CSSProperties.prototype.webkitTextSizeAdjust;
/** @type {string} */ CSSProperties.prototype.webkitTextStroke;
/** @type {string} */ CSSProperties.prototype.webkitTextStrokeColor;
/** @type {string} */ CSSProperties.prototype.webkitTextStrokeWidth;
/** @type {string} */ CSSProperties.prototype.webkitTransform;
/** @type {string} */ CSSProperties.prototype.webkitTransformOrigin;
/** @type {string} */ CSSProperties.prototype.webkitTransformOriginX;
/** @type {string} */ CSSProperties.prototype.webkitTransformOriginY;
/** @type {string} */ CSSProperties.prototype.webkitTransition;
/** @type {string} */ CSSProperties.prototype.webkitTransitionDuration;
/** @type {string} */ CSSProperties.prototype.webkitTransitionProperty;
/** @type {string} */ CSSProperties.prototype.webkitTransitionRepeatCount;
/** @type {string} */ CSSProperties.prototype.webkitTransitionTimingFunction;
/** @type {string} */ CSSProperties.prototype.webkitUserDrag;
/** @type {string} */ CSSProperties.prototype.webkitUserModify;
/** @type {string} */ CSSProperties.prototype.webkitUserSelect;
