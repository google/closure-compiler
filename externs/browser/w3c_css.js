/*
 * Copyright 2025 The Closure Compiler Authors
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
 * @fileoverview Definitions for W3C's CSS specification
 *  The whole file has been fully type annotated.
 *  http://www.w3.org/TR/DOM-Level-2-Style/css.html
 * @externs
 * @author stevey@google.com (Steve Yegge)
 *
 * TODO(nicksantos): When there are no more occurrences of w3c_range.js and
 * gecko_dom.js being included directly in BUILD files, bug dbeam to split the
 * bottom part of this file into a separate externs.
 */

/**
 * @constructor
 * @see http://www.w3.org/TR/DOM-Level-2-Style/stylesheets.html#StyleSheets-StyleSheet
 */
function StyleSheet() {}

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/stylesheets.html#StyleSheets-StyleSheet-type
 */
StyleSheet.prototype.type;

/**
 * @type {boolean}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/stylesheets.html#StyleSheets-StyleSheet-disabled
 */
StyleSheet.prototype.disabled;

/**
 * @type {Node}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/stylesheets.html#StyleSheets-StyleSheet-owner
 */
StyleSheet.prototype.ownerNode;

/**
 * @type {StyleSheet}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/stylesheets.html#StyleSheets-StyleSheet-parentStyleSheet
 */
StyleSheet.prototype.parentStyleSheet;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/stylesheets.html#StyleSheets-StyleSheet-href
 */
StyleSheet.prototype.href;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/stylesheets.html#StyleSheets-StyleSheet-title
 */
StyleSheet.prototype.title;

/**
 * @type {MediaList}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/stylesheets.html#StyleSheets-StyleSheet-media
 */
StyleSheet.prototype.media;

/**
 * @constructor
 * @implements {IArrayLike<!StyleSheet>}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/stylesheets.html#StyleSheets-StyleSheetList
 */
function StyleSheetList() {}

/**
 * @type {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/stylesheets.html#StyleSheets-StyleSheetList-length
 */
StyleSheetList.prototype.length;

/**
 * @param {number} index
 * @return {StyleSheet}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/stylesheets.html#StyleSheets-StyleSheetList-item
 */
StyleSheetList.prototype.item = function(index) {};

/**
 * @constructor
 * @implements {IArrayLike<!MediaList>}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/stylesheets.html#StyleSheets-MediaList
 */
function MediaList() {}

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/stylesheets.html#StyleSheets-MediaList-mediaText
 */
MediaList.prototype.mediaText;

/**
 * @type {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/stylesheets.html#StyleSheets-MediaList-length
 */
MediaList.prototype.length;

/**
 * @param {number} index
 * @return {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/stylesheets.html#StyleSheets-MediaList-item
 */
MediaList.prototype.item = function(index) {};

/**
 * @param {string} medium
 * @return {undefined}
 */
MediaList.prototype.appendMedium = function(medium) {};

/**
 * @param {string} medium
 * @return {undefined}
 */
MediaList.prototype.deleteMedium = function(medium) {};

/**
 * @interface
 * @see http://www.w3.org/TR/DOM-Level-2-Style/stylesheets.html#StyleSheets-LinkStyle
 */
function LinkStyle() {}

/**
 * @type {StyleSheet}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/stylesheets.html#StyleSheets-LinkStyle-sheet
 */
LinkStyle.prototype.sheet;

/**
 * @constructor
 * @see http://www.w3.org/TR/DOM-Level-2-Style/stylesheets.html#StyleSheets-StyleSheet-DocumentStyle
 */
function DocumentStyle() {}

/**
 * @type {StyleSheetList}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/stylesheets.html#StyleSheets-StyleSheet-DocumentStyle-styleSheets
 */
DocumentStyle.prototype.styleSheets;

/**
 * Type of the `options` parameter for the `CSSStyleSheet` constructor.
 *
 * The actual property definitions are in wicg_constructable_stylesheets.js,
 * which must be explicitly passed to the compiler in order to use them. This
 * record is defined here since CSSStyleSheet cannot be redefined with its
 * optional init parameter.
 * @record
 * @see https://wicg.github.io/construct-stylesheets/#dictdef-cssstylesheetinit
 */
function CSSStyleSheetInit() {}

/**
 * @constructor
 * @extends {StyleSheet}
 * @param {CSSStyleSheetInit=} options
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSStyleSheet
 * @see https://wicg.github.io/construct-stylesheets/#dom-cssstylesheet-cssstylesheet
 */
function CSSStyleSheet(options) {}

/**
 * @type {CSSRule}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSStyleSheet-ownerRule
 */
CSSStyleSheet.prototype.ownerRule;

/**
 * @type {CSSRuleList}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSStyleSheet-cssRules
 */
CSSStyleSheet.prototype.cssRules;

/**
 * @param {string} rule
 * @param {number} index
 * @return {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSStyleSheet-insertRule
 */
CSSStyleSheet.prototype.insertRule = function(rule, index) {};

/**
 * @param {number} index
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSStyleSheet-deleteRule
 * @return {undefined}
 */
CSSStyleSheet.prototype.deleteRule = function(index) {};

/**
 * @constructor
 * @implements {IArrayLike<!CSSRule>}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSRuleList
 */
function CSSRuleList() {}

/**
 * @type {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSRuleList-length
 */
CSSRuleList.prototype.length;

/**
 * @param {number} index
 * @return {CSSRule}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSRuleList-item
 */
CSSRuleList.prototype.item = function(index) {};

/**
 * @constructor
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSRule
 */
function CSSRule() {}

/**
 * @type {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSRule-ruleType
 */
CSSRule.prototype.type;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSRule-cssText
 */
CSSRule.prototype.cssText;

/**
 * @type {CSSStyleSheet}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSRule-sheet
 */
CSSRule.prototype.parentStyleSheet;

/**
 * @type {CSSRule}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSRule-parentRule
 */
CSSRule.prototype.parentRule;

/**
 * @type {CSSStyleDeclaration}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSStyleRule
 */
CSSRule.prototype.style;

/**
 * Indicates that the rule is a {@see CSSUnknownRule}.
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSRule-ruleType
 */
CSSRule.UNKNOWN_RULE;

/**
 * Indicates that the rule is a {@see CSSStyleRule}.
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSRule-ruleType
 */
CSSRule.STYLE_RULE;

/**
 * Indicates that the rule is a {@see CSSCharsetRule}.
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSRule-ruleType
 */
CSSRule.CHARSET_RULE;

/**
 * Indicates that the rule is a {@see CSSImportRule}.
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSRule-ruleType
 */
CSSRule.IMPORT_RULE;

/**
 * Indicates that the rule is a {@see CSSMediaRule}.
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSRule-ruleType
 */
CSSRule.MEDIA_RULE;

/**
 * Indicates that the rule is a {@see CSSFontFaceRule}.
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSRule-ruleType
 */
CSSRule.FONT_FACE_RULE;

/**
 * Indicates that the rule is a {@see CSSPageRule}.
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSRule-ruleType
 */
CSSRule.PAGE_RULE;

/** @const {number} */ CSSRule.NAMESPACE_RULE;
/** @const {number} */ CSSRule.KEYFRAMES_RULE;
/** @const {number} */ CSSRule.KEYFRAME_RULE;
/** @const {number} */ CSSRule.SUPPORTS_RULE;
/** @const {number} */ CSSRule.COUNTER_STYLE_RULE;
/** @const {number} */ CSSRule.FONT_FEATURE_VALUES_RULE;

/**
 * @constructor
 * @extends {StylePropertyMapReadOnly}
 * @see https://developer.mozilla.org/docs/Web/API/StylePropertyMap
 */
function StylePropertyMap() {}

/**
 * @param {string} property
 * @param {...(CSSStyleValue|string)} values

 * @return {undefined}
 * @see https://developer.mozilla.org/docs/Web/API/StylePropertyMap/append
 */
StylePropertyMap.prototype.append = function(property, values) {};

/**
 * @return {undefined}
 * @see https://developer.mozilla.org/docs/Web/API/StylePropertyMap/clear
 */
StylePropertyMap.prototype.clear = function() {};

/**
 * @param {string} property
 * @return {undefined}
 * @see https://developer.mozilla.org/docs/Web/API/StylePropertyMap/delete
 */
StylePropertyMap.prototype.delete = function(property) {};

/**
 * @param {string} property
 * @param {...(CSSStyleValue|string)} values
 * @return {undefined}
 * @see https://developer.mozilla.org/docs/Web/API/StylePropertyMap/set
 */
StylePropertyMap.prototype.set = function(property, values) {};

/**
 * @constructor
 * @extends {CSSRule}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSStyleRule
 */
function CSSStyleRule() {}

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSStyleRule-selectorText
 */
CSSStyleRule.prototype.selectorText;

/**
 * @type {CSSStyleDeclaration}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSStyleRule-style
 */
CSSStyleRule.prototype.style;

/**
 * @type {!StylePropertyMap}
 * @see https://developer.mozilla.org/docs/Web/API/CSSStyleRule/styleMap
 */
CSSStyleRule.prototype.styleMap;

/**
 * @constructor
 * @extends {CSSRule}
 * @see https://www.w3.org/TR/css-conditional-3/#the-csssupportsrule-interface
 */
function CSSSupportsRule() {}

/**
 * @type {string}
 */
CSSSupportsRule.prototype.conditionText;

/**
 * @type {!CSSRuleList}
 */
CSSSupportsRule.prototype.cssRules;

/**
 * @constructor
 * @extends {CSSRule}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSMediaRule
 */
function CSSMediaRule() {}

/**
 * @type {MediaList}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSMediaRule-mediaTypes
 */
CSSMediaRule.prototype.media;

/**
 * @type {CSSRuleList}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSMediaRule-cssRules
 */
CSSMediaRule.prototype.cssRules;

/**
 * @param {string} rule
 * @param {number} index
 * @return {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSMediaRule-insertRule
 */
CSSMediaRule.prototype.insertRule = function(rule, index) {};

/**
 * @param {number} index
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSMediaRule-deleteRule
 * @return {undefined}
 */
CSSMediaRule.prototype.deleteRule = function(index) {};

/**
 * @constructor
 * @extends {CSSRule}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSFontFaceRule
 */
function CSSFontFaceRule() {}

/**
 * @type {CSSStyleDeclaration}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSFontFaceRule-style
 */
CSSFontFaceRule.prototype.style;

/**
 * @constructor
 * @extends {CSSRule}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPageRule
 */
function CSSPageRule() {}

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPageRule-name
 */
CSSPageRule.prototype.selectorText;

/**
 * @type {CSSStyleDeclaration}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPageRule-style
 */
CSSPageRule.prototype.style;

/**
 * @constructor
 * @extends {CSSRule}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSImportRule
 */
function CSSImportRule() {}

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSImportRule-href
 */
CSSImportRule.prototype.href;

/**
 * @type {MediaList}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSImportRule-media
 */
CSSImportRule.prototype.media;

/**
 * @type {CSSStyleSheet}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSImportRule-styleSheet
 */
CSSImportRule.prototype.styleSheet;

/** @type {?string} */
CSSImportRule.prototype.layerName;

/** @type {?string} */
CSSImportRule.prototype.supportsText;

/**
 * @constructor
 * @extends {CSSRule}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSCharsetRule
 */
function CSSCharsetRule() {}

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSCharsetRule-encoding
 */
CSSCharsetRule.prototype.encoding;

/**
 * @constructor
 * @extends {CSSRule}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSUnknownRule
 */
function CSSUnknownRule() {}

/**
 * @constructor
 * @extends {CSSRule}
 */
function CSSViewTransitionRule() {}

/** @type {string} */
CSSViewTransitionRule.prototype.navigation;

/** @type {!ReadonlyArray<string>} */
CSSViewTransitionRule.prototype.types;

/**
 * @constructor
 * @extends {CSSRule}
 */
function CSSGroupingRule() {}

/** @type {!CSSRuleList} */
CSSGroupingRule.prototype.cssRules;

/**
 * @param {number} index
 * @return {undefined}
 */
CSSGroupingRule.prototype.deleteRule = function(index) {}

/**
 * @param {string} rule
 * @param {number=} index
 * @return {number}
 */
CSSGroupingRule.prototype.insertRule = function(rule, index) {}

/**
 * @constructor
 * @extends {CSSGroupingRule}
 */
function CSSConditionRule() {}

/** @type {string} */
CSSConditionRule.prototype.conditionText;

/**
 * @constructor
 * @extends {CSSConditionRule}
 */
function CSSContainerRule() {}

/** @type {string} */ CSSContainerRule.prototype.containerName;
/** @type {string} */ CSSContainerRule.prototype.containerQuery;

/**
 * @constructor
 * @extends {CSSRule}
 */
function CSSCounterStyleRule() {}

/** @type {string} */ CSSCounterStyleRule.prototype.additiveSymbols;
/** @type {string} */ CSSCounterStyleRule.prototype.fallback;
/** @type {string} */ CSSCounterStyleRule.prototype.name;
/** @type {string} */ CSSCounterStyleRule.prototype.negative;
/** @type {string} */ CSSCounterStyleRule.prototype.pad;
/** @type {string} */ CSSCounterStyleRule.prototype.prefix;
/** @type {string} */ CSSCounterStyleRule.prototype.range;
/** @type {string} */ CSSCounterStyleRule.prototype.speakAs;
/** @type {string} */ CSSCounterStyleRule.prototype.suffix;
/** @type {string} */ CSSCounterStyleRule.prototype.symbols;
/** @type {string} */ CSSCounterStyleRule.prototype.system;

/**
 * @constructor
 * @extends {CSSRule}
 */
function CSSFontFeatureValuesRule() {}

/** @type {string} */ CSSFontFeatureValuesRule.prototype.fontFamily;

/**
 * @constructor
 * @extends {CSSRule}
 */
function CSSFontPaletteValuesRule() {}

/** @type {string} */ CSSFontPaletteValuesRule.prototype.basePalette;
/** @type {string} */ CSSFontPaletteValuesRule.prototype.fontFamily;
/** @type {string} */ CSSFontPaletteValuesRule.prototype.name;
/** @type {string} */ CSSFontPaletteValuesRule.prototype.overrideColors;

/**
 * @constructor
 * @extends {CSSGroupingRule}
 */
function CSSLayerBlockRule() {}

/** @type {string} */ CSSLayerBlockRule.prototype.name;

/**
 * @constructor
 * @extends {CSSRule}
 */
function CSSLayerStatementRule() {}

/** @type {!ReadonlyArray<string>} */
CSSLayerStatementRule.prototype.nameList;

/**
 * @constructor
 * @extends {CSSRule}
 */
function CSSNamespaceRule() {}

/** @type {string} */ CSSNamespaceRule.prototype.namespaceURI;
/** @type {string} */ CSSNamespaceRule.prototype.prefix;

/**
 * @constructor
 * @extends {CSSRule}
 */
function CSSPropertyRule() {}

/** @type {boolean} */ CSSPropertyRule.prototype.inherits;
/** @type {?string} */ CSSPropertyRule.prototype.initialValue;
/** @type {string}  */ CSSPropertyRule.prototype.name;
/** @type {string}  */ CSSPropertyRule.prototype.syntax;

/**
 * @constructor
 * @extends {CSSGroupingRule}
 */
function CSSScopeRule() {}

/** @type {?string} */ CSSScopeRule.prototype.end;
/** @type {?string} */ CSSScopeRule.prototype.start;

/**
 * @constructor
 * @extends {CSSGroupingRule}
 */
function CSSStartingStyleRule() {}

/**
 * @constructor
 * @extends {CSSStyleProperties}
 * @implements {IObject<(string|number), string>}
 * @implements {IArrayLike<string>}
 * @implements {Iterable<string>}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSStyleDeclaration
 */
function CSSStyleDeclaration() {}

/** @override */
CSSStyleDeclaration.prototype[Symbol.iterator] = function() {};

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSStyleDeclaration-cssText
 */
CSSStyleDeclaration.prototype.cssText;

/**
 * @type {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSStyleDeclaration-length
 */
CSSStyleDeclaration.prototype.length;

/**
 * @type {CSSRule}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSStyleDeclaration-parentRule
 */
CSSStyleDeclaration.prototype.parentRule;

/**
 * @param {string} propertyName
 * @return {CSSValue}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSStyleDeclaration-getPropertyCSSValue
 */
CSSStyleDeclaration.prototype.getPropertyCSSValue = function(propertyName) {};

/**
 * @param {string} propertyName
 * @return {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSStyleDeclaration-getPropertyPriority
 */
CSSStyleDeclaration.prototype.getPropertyPriority = function(propertyName) {};

/**
 * @param {string} propertyName
 * @return {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSStyleDeclaration-getPropertyValue
 */
CSSStyleDeclaration.prototype.getPropertyValue = function(propertyName) {};

/**
 * @param {number} index
 * @return {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSStyleDeclaration-item
 */
CSSStyleDeclaration.prototype.item = function(index) {};

/**
 * @param {string} propertyName
 * @return {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSStyleDeclaration-removeProperty
 */
CSSStyleDeclaration.prototype.removeProperty = function(propertyName) {};

/**
 * @param {string} propertyName
 * @param {string} value
 * @param {string=} opt_priority
 * @return {undefined}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSStyleDeclaration-setProperty
 */
CSSStyleDeclaration.prototype.setProperty = function(
    propertyName, value, opt_priority) {};

/**
 * @type {string}
 * @see https://drafts.csswg.org/css-view-transitions/#propdef-view-transition-name
 */
CSSStyleDeclaration.prototype.viewTransitionName;

// IE-specific

/**
 * @param {string} name
 * @param {number=} opt_flags
 * @return {string|number|boolean|null}
 * @see http://msdn.microsoft.com/en-us/library/ms536429(VS.85).aspx
 */
CSSStyleDeclaration.prototype.getAttribute = function(name, opt_flags) {};

/**
 * @param {string} name
 * @return {string|number|boolean|null}
 * @see http://msdn.microsoft.com/en-us/library/aa358797(VS.85).aspx
 */
CSSStyleDeclaration.prototype.getExpression = function(name) {};

/**
 * @param {string} name
 * @param {number=} opt_flags
 * @return {boolean}
 * @see http://msdn.microsoft.com/en-us/library/ms536696(VS.85).aspx
 */
CSSStyleDeclaration.prototype.removeAttribute = function(name, opt_flags) {};

/**
 * @param {string} name
 * @return {boolean}
 * @see http://msdn.microsoft.com/en-us/library/aa358798(VS.85).aspx
 */
CSSStyleDeclaration.prototype.removeExpression = function(name) {};

/**
 * @deprecated
 * @param {string} name
 * @param {*} value
 * @param {number=} opt_flags
 * @see http://msdn.microsoft.com/en-us/library/ms536739(VS.85).aspx
 * @return {undefined}
 */
CSSStyleDeclaration.prototype.setAttribute = function(
    name, value, opt_flags) {};

/**
 * @param {string} name
 * @param {string} expr
 * @param {string=} opt_language
 * @return {undefined}
 * @see http://msdn.microsoft.com/en-us/library/ms531196(VS.85).aspx
 */
CSSStyleDeclaration.prototype.setExpression = function(
    name, expr, opt_language) {};

/**
 * Standard W3C style properties defined on CSSStyleProperties.
 * @see https://developer.mozilla.org/docs/Web/CSS
 */
/** @type {string} */ CSSStyleProperties.prototype.accentColor;
/** @type {string} */ CSSStyleProperties.prototype.alignmentBaseline;
/** @type {string} */ CSSStyleProperties.prototype.animationComposition;
/** @type {string} */ CSSStyleProperties.prototype.appearance;
/** @type {string} */ CSSStyleProperties.prototype.backgroundClip;
/** @type {string} */ CSSStyleProperties.prototype.backgroundOrigin;
/** @type {string} */ CSSStyleProperties.prototype.baselineShift;
/** @type {string} */ CSSStyleProperties.prototype.baselineSource;
/** @type {string} */ CSSStyleProperties.prototype.borderBlock;
/** @type {string} */ CSSStyleProperties.prototype.borderBlockColor;
/** @type {string} */ CSSStyleProperties.prototype.borderBlockEnd;
/** @type {string} */ CSSStyleProperties.prototype.borderBlockEndColor;
/** @type {string} */ CSSStyleProperties.prototype.borderBlockEndStyle;
/** @type {string} */ CSSStyleProperties.prototype.borderBlockEndWidth;
/** @type {string} */ CSSStyleProperties.prototype.borderBlockStart;
/** @type {string} */ CSSStyleProperties.prototype.borderBlockStartColor;
/** @type {string} */ CSSStyleProperties.prototype.borderBlockStartStyle;
/** @type {string} */ CSSStyleProperties.prototype.borderBlockStartWidth;
/** @type {string} */ CSSStyleProperties.prototype.borderBlockStyle;
/** @type {string} */ CSSStyleProperties.prototype.borderBlockWidth;
/** @type {string} */ CSSStyleProperties.prototype.borderEndEndRadius;
/** @type {string} */ CSSStyleProperties.prototype.borderEndStartRadius;
/** @type {string} */ CSSStyleProperties.prototype.borderInline;
/** @type {string} */ CSSStyleProperties.prototype.borderInlineColor;
/** @type {string} */ CSSStyleProperties.prototype.borderInlineEnd;
/** @type {string} */ CSSStyleProperties.prototype.borderInlineEndColor;
/** @type {string} */ CSSStyleProperties.prototype.borderInlineEndStyle;
/** @type {string} */ CSSStyleProperties.prototype.borderInlineEndWidth;
/** @type {string} */ CSSStyleProperties.prototype.borderInlineStart;
/** @type {string} */ CSSStyleProperties.prototype.borderInlineStartColor;
/** @type {string} */ CSSStyleProperties.prototype.borderInlineStartStyle;
/** @type {string} */ CSSStyleProperties.prototype.borderInlineStartWidth;
/** @type {string} */ CSSStyleProperties.prototype.borderInlineStyle;
/** @type {string} */ CSSStyleProperties.prototype.borderInlineWidth;
/** @type {string} */ CSSStyleProperties.prototype.borderStartEndRadius;
/** @type {string} */ CSSStyleProperties.prototype.borderStartStartRadius;
/** @type {string} */ CSSStyleProperties.prototype.breakAfter;
/** @type {string} */ CSSStyleProperties.prototype.breakBefore;
/** @type {string} */ CSSStyleProperties.prototype.breakInside;
/** @type {string} */ CSSStyleProperties.prototype.caretColor;
/** @type {string} */ CSSStyleProperties.prototype.clipRule;
/** @type {string} */ CSSStyleProperties.prototype.colorInterpolation;
/** @type {string} */ CSSStyleProperties.prototype.colorInterpolationFilters;
/** @type {string} */ CSSStyleProperties.prototype.columnCount;
/** @type {string} */ CSSStyleProperties.prototype.columnFill;
/** @type {string} */ CSSStyleProperties.prototype.columnGap;
/** @type {string} */ CSSStyleProperties.prototype.columnRule;
/** @type {string} */ CSSStyleProperties.prototype.columnRuleColor;
/** @type {string} */ CSSStyleProperties.prototype.columnRuleStyle;
/** @type {string} */ CSSStyleProperties.prototype.columnRuleWidth;
/** @type {string} */ CSSStyleProperties.prototype.columnSpan;
/** @type {string} */ CSSStyleProperties.prototype.columnWidth;
/** @type {string} */ CSSStyleProperties.prototype.columns;
/** @type {string} */ CSSStyleProperties.prototype.containIntrinsicBlockSize;
/** @type {string} */ CSSStyleProperties.prototype.containIntrinsicHeight;
/** @type {string} */ CSSStyleProperties.prototype.containIntrinsicInlineSize;
/** @type {string} */ CSSStyleProperties.prototype.containIntrinsicSize;
/** @type {string} */ CSSStyleProperties.prototype.containIntrinsicWidth;
/** @type {string} */ CSSStyleProperties.prototype.container;
/** @type {string} */ CSSStyleProperties.prototype.counterSet;
/** @type {string} */ CSSStyleProperties.prototype.dominantBaseline;
/** @type {string} */ CSSStyleProperties.prototype.float;
/** @type {string} */ CSSStyleProperties.prototype.floodColor;
/** @type {string} */ CSSStyleProperties.prototype.floodOpacity;
/** @type {string} */ CSSStyleProperties.prototype.fontFeatureSettings;
/** @type {string} */ CSSStyleProperties.prototype.fontKerning;
/** @type {string} */ CSSStyleProperties.prototype.fontOpticalSizing;
/** @type {string} */ CSSStyleProperties.prototype.fontPalette;
/** @type {string} */ CSSStyleProperties.prototype.fontSynthesis;
/** @type {string} */ CSSStyleProperties.prototype.fontSynthesisSmallCaps;
/** @type {string} */ CSSStyleProperties.prototype.fontSynthesisStyle;
/** @type {string} */ CSSStyleProperties.prototype.fontSynthesisWeight;
/** @type {string} */ CSSStyleProperties.prototype.fontVariantAlternates;
/** @type {string} */ CSSStyleProperties.prototype.fontVariantCaps;
/** @type {string} */ CSSStyleProperties.prototype.fontVariantEastAsian;
/** @type {string} */ CSSStyleProperties.prototype.fontVariantLigatures;
/** @type {string} */ CSSStyleProperties.prototype.fontVariantNumeric;
/** @type {string} */ CSSStyleProperties.prototype.fontVariantPosition;
/** @type {string} */ CSSStyleProperties.prototype.fontVariationSettings;
/** @type {string} */ CSSStyleProperties.prototype.forcedColorAdjust;
/** @type {string} */ CSSStyleProperties.prototype.gridColumnGap;
/** @type {string} */ CSSStyleProperties.prototype.gridGap;
/** @type {string} */ CSSStyleProperties.prototype.gridRowGap;
/** @type {string} */ CSSStyleProperties.prototype.hyphenateCharacter;
/** @type {string} */ CSSStyleProperties.prototype.hyphens;
/** @type {string} */ CSSStyleProperties.prototype.hyphenateLimitChars;
/** @type {string} */ CSSStyleProperties.prototype.imageRendering;
/** @type {string} */ CSSStyleProperties.prototype.justifyItems;
/** @type {string} */ CSSStyleProperties.prototype.justifySelf;
/** @type {string} */ CSSStyleProperties.prototype.lightingColor;
/** @type {string} */ CSSStyleProperties.prototype.lineBreak;
/** @type {string} */ CSSStyleProperties.prototype.marginBlock;
/** @type {string} */ CSSStyleProperties.prototype.marginBlockEnd;
/** @type {string} */ CSSStyleProperties.prototype.marginBlockStart;
/** @type {string} */ CSSStyleProperties.prototype.marginInline;
/** @type {string} */ CSSStyleProperties.prototype.marginInlineEnd;
/** @type {string} */ CSSStyleProperties.prototype.marginInlineStart;
/** @type {string} */ CSSStyleProperties.prototype.marker;
/** @type {string} */ CSSStyleProperties.prototype.markerEnd;
/** @type {string} */ CSSStyleProperties.prototype.markerMid;
/** @type {string} */ CSSStyleProperties.prototype.markerStart;
/** @type {string} */ CSSStyleProperties.prototype.mask;
/** @type {string} */ CSSStyleProperties.prototype.maskClip;
/** @type {string} */ CSSStyleProperties.prototype.maskComposite;
/** @type {string} */ CSSStyleProperties.prototype.maskMode;
/** @type {string} */ CSSStyleProperties.prototype.maskOrigin;
/** @type {string} */ CSSStyleProperties.prototype.maskPosition;
/** @type {string} */ CSSStyleProperties.prototype.maskRepeat;
/** @type {string} */ CSSStyleProperties.prototype.maskSize;
/** @type {string} */ CSSStyleProperties.prototype.maskType;
/** @type {string} */ CSSStyleProperties.prototype.mathDepth;
/** @type {string} */ CSSStyleProperties.prototype.mathStyle;
/** @type {string} */ CSSStyleProperties.prototype.maxBlockSize;
/** @type {string} */ CSSStyleProperties.prototype.maxInlineSize;
/** @type {string} */ CSSStyleProperties.prototype.minBlockSize;
/** @type {string} */ CSSStyleProperties.prototype.minInlineSize;
/** @type {string} */ CSSStyleProperties.prototype.offsetAnchor;
/** @type {string} */ CSSStyleProperties.prototype.offsetDistance;
/** @type {string} */ CSSStyleProperties.prototype.offsetPath;
/** @type {string} */ CSSStyleProperties.prototype.offsetPosition;
/** @type {string} */ CSSStyleProperties.prototype.offsetRotate;
/** @type {string} */ CSSStyleProperties.prototype.overflowAnchor;
/** @type {string} */ CSSStyleProperties.prototype.overflowBlock;
/** @type {string} */ CSSStyleProperties.prototype.overflowClipMargin;
/** @type {string} */ CSSStyleProperties.prototype.overflowInline;
/** @type {string} */ CSSStyleProperties.prototype.overscrollBehaviorBlock;
/** @type {string} */ CSSStyleProperties.prototype.overscrollBehaviorInline;
/** @type {string} */ CSSStyleProperties.prototype.overscrollBehaviorX;
/** @type {string} */ CSSStyleProperties.prototype.overscrollBehaviorY;
/** @type {string} */ CSSStyleProperties.prototype.paddingBlock;
/** @type {string} */ CSSStyleProperties.prototype.paddingBlockEnd;
/** @type {string} */ CSSStyleProperties.prototype.paddingBlockStart;
/** @type {string} */ CSSStyleProperties.prototype.paddingInline;
/** @type {string} */ CSSStyleProperties.prototype.paddingInlineEnd;
/** @type {string} */ CSSStyleProperties.prototype.paddingInlineStart;
/** @type {string} */ CSSStyleProperties.prototype.paintOrder;
/** @type {string} */ CSSStyleProperties.prototype.placeContent;
/** @type {string} */ CSSStyleProperties.prototype.placeItems;
/** @type {string} */ CSSStyleProperties.prototype.placeSelf;
/** @type {string} */ CSSStyleProperties.prototype.printColorAdjust;
/** @type {string} */ CSSStyleProperties.prototype.rowGap;
/** @type {string} */ CSSStyleProperties.prototype.rubyAlign;
/** @type {string} */ CSSStyleProperties.prototype.rubyPosition;
/** @type {string} */ CSSStyleProperties.prototype.scrollBehavior;
/** @type {string} */ CSSStyleProperties.prototype.scrollMargin;
/** @type {string} */ CSSStyleProperties.prototype.scrollMarginBlock;
/** @type {string} */ CSSStyleProperties.prototype.scrollMarginBlockEnd;
/** @type {string} */ CSSStyleProperties.prototype.scrollMarginBlockStart;
/** @type {string} */ CSSStyleProperties.prototype.scrollMarginBottom;
/** @type {string} */ CSSStyleProperties.prototype.scrollMarginInline;
/** @type {string} */ CSSStyleProperties.prototype.scrollMarginInlineEnd;
/** @type {string} */ CSSStyleProperties.prototype.scrollMarginInlineStart;
/** @type {string} */ CSSStyleProperties.prototype.scrollMarginLeft;
/** @type {string} */ CSSStyleProperties.prototype.scrollMarginRight;
/** @type {string} */ CSSStyleProperties.prototype.scrollMarginTop;
/** @type {string} */ CSSStyleProperties.prototype.scrollSnapAlign;
/** @type {string} */ CSSStyleProperties.prototype.scrollSnapStop;
/** @type {string} */ CSSStyleProperties.prototype.scrollSnapType;
/** @type {string} */ CSSStyleProperties.prototype.scrollbarColor;
/** @type {string} */ CSSStyleProperties.prototype.scrollbarGutter;
/** @type {string} */ CSSStyleProperties.prototype.scrollbarWidth;
/** @type {string} */ CSSStyleProperties.prototype.shapeImageThreshold;
/** @type {string} */ CSSStyleProperties.prototype.shapeMargin;
/** @type {string} */ CSSStyleProperties.prototype.shapeOutside;
/** @type {string} */ CSSStyleProperties.prototype.shapeRendering;
/** @type {string} */ CSSStyleProperties.prototype.stopColor;
/** @type {string} */ CSSStyleProperties.prototype.stopOpacity;
/** @type {string} */ CSSStyleProperties.prototype.tabSize;
/** @type {string} */ CSSStyleProperties.prototype.textAlignLast;
/** @type {string} */ CSSStyleProperties.prototype.textAnchor;
/** @type {string} */ CSSStyleProperties.prototype.textBox;
/** @type {string} */ CSSStyleProperties.prototype.textBoxEdge;
/** @type {string} */ CSSStyleProperties.prototype.textBoxTrim;
/** @type {string} */ CSSStyleProperties.prototype.textCombineUpright;
/** @type {string} */ CSSStyleProperties.prototype.textDecorationSkipInk;
/** @type {string} */ CSSStyleProperties.prototype.textDecorationThickness;
/** @type {string} */ CSSStyleProperties.prototype.textEmphasis;
/** @type {string} */ CSSStyleProperties.prototype.textEmphasisColor;
/** @type {string} */ CSSStyleProperties.prototype.textEmphasisPosition;
/** @type {string} */ CSSStyleProperties.prototype.textEmphasisStyle;
/** @type {string} */ CSSStyleProperties.prototype.textOrientation;
/** @type {string} */ CSSStyleProperties.prototype.textRendering;
/** @type {string} */ CSSStyleProperties.prototype.textUnderlineOffset;
/** @type {string} */ CSSStyleProperties.prototype.textUnderlinePosition;
/** @type {string} */ CSSStyleProperties.prototype.textWrap;
/** @type {string} */ CSSStyleProperties.prototype.textWrapMode;
/** @type {string} */ CSSStyleProperties.prototype.textWrapStyle;
/** @type {string} */ CSSStyleProperties.prototype.transformBox;
/** @type {string} */ CSSStyleProperties.prototype.transitionBehavior;
/** @type {string} */ CSSStyleProperties.prototype.vectorEffect;
/** @type {string} */ CSSStyleProperties.prototype.whiteSpaceCollapse;
/** @type {string} */ CSSStyleProperties.prototype.wordBreak;

/**
 * @constructor
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSValue
 */
function CSSValue() {}

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSValue-cssText
 */
CSSValue.prototype.cssText;

/**
 * @type {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSValue-cssValueType
 */
CSSValue.prototype.cssValueType;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSValue-types
 */
CSSValue.CSS_INHERIT;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSValue-types
 */
CSSValue.CSS_PRIMITIVE_VALUE;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSValue-types
 */
CSSValue.CSS_VALUE_LIST;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSValue-types
 */
CSSValue.CSS_CUSTOM;

/**
 * @constructor
 * @extends {CSSValue}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
function CSSPrimitiveValue() {}

/**
 * @type {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.prototype.primitiveType;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_UNKNOWN;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_NUMBER;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_PERCENTAGE;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_EMS;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_EXS;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_PX;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_CM;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_MM;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_IN;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_PT;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_PC;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_DEG;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_RAD;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_GRAD;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_MS;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_S;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_HZ;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_KHZ;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_DIMENSION;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_STRING;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_URI;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_IDENT;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_ATTR;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_COUNTER;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_RECT;

/**
 * @const {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue
 */
CSSPrimitiveValue.CSS_RGBCOLOR;

/**
 * @return {Counter}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue-getCounterValue
 * @throws DOMException {@see DomException.INVALID_ACCESS_ERR}
 */
CSSPrimitiveValue.prototype.getCounterValue = function() {};

/**
 * @param {number} unitType
 * @return {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue-getFloatValue
 * @throws DOMException {@see DomException.INVALID_ACCESS_ERR}
 */
CSSPrimitiveValue.prototype.getFloatValue = function(unitType) {};

/**
 * @return {RGBColor}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue-getRGBColorValue
 * @throws DOMException {@see DomException.INVALID_ACCESS_ERR}
 */
CSSPrimitiveValue.prototype.getRGBColorValue = function() {};

/**
 * @return {Rect}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue-getRectValue
 * @throws DOMException {@see DomException.INVALID_ACCESS_ERR}
 */
CSSPrimitiveValue.prototype.getRectValue = function() {};

/**
 * @return {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue-getStringValue
 * @throws DOMException {@see DomException.INVALID_ACCESS_ERR}
 */
CSSPrimitiveValue.prototype.getStringValue = function() {};

/**
 * @param {number} unitType
 * @param {number} floatValue
 * @return {undefined}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue-setFloatValue
 * @throws DOMException {@see DomException.INVALID_ACCESS_ERR},
 *                      {@see DomException.NO_MODIFICATION_ALLOWED_ERR}
 */
CSSPrimitiveValue.prototype.setFloatValue = function(unitType, floatValue) {};

/**
 * @param {number} stringType
 * @param {string} stringValue
 * @return {undefined}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue-setStringValue
 * @throws DOMException {@see DomException.INVALID_ACCESS_ERR},
 *                      {@see DomException.NO_MODIFICATION_ALLOWED_ERR}
 */
CSSPrimitiveValue.prototype.setStringValue = function(
    stringType, stringValue) {};

/**
 * @constructor
 * @extends {CSSValue}
 * @implements {IArrayLike<!CSSValue>}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSValueList
 */
function CSSValueList() {}

/**
 * @type {number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSValueList-length
 */
CSSValueList.prototype.length;

/**
 * @param {number} index
 * @return {CSSValue}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSValueList-item
 */
CSSValueList.prototype.item = function(index) {};

/**
 * @constructor
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-RGBColor
 */
function RGBColor() {}

/**
 * @type {CSSPrimitiveValue}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-RGBColor-red
 */
RGBColor.prototype.red;

/**
 * @type {CSSPrimitiveValue}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-RGBColor-green
 */
RGBColor.prototype.green;

/**
 * @type {CSSPrimitiveValue}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-RGBColor-blue
 */
RGBColor.prototype.blue;

/**
 * @constructor
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-Rect
 */
function Rect() {}

/**
 * @type {CSSPrimitiveValue}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-Rect-top
 */
Rect.prototype.top;

/**
 * @type {CSSPrimitiveValue}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-Rect-right
 */
Rect.prototype.right;

/**
 * @type {CSSPrimitiveValue}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-Rect-bottom
 */
Rect.prototype.bottom;

/**
 * @type {CSSPrimitiveValue}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-Rect-left
 */
Rect.prototype.left;

/**
 * @constructor
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-Counter
 */
function Counter() {}

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-Counter-identifier
 */
Counter.prototype.identifier;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-Counter-listStyle
 */
Counter.prototype.listStyle;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-Counter-separator
 */
Counter.prototype.separator;

/**
 * @interface
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-ViewCSS
 */
function ViewCSS() {}

/**
 * @param {Element} elt
 * @param {?string=} opt_pseudoElt This argument is required according to the
 *     CSS2 specification, but optional in all major browsers. See the note at
 *     https://developer.mozilla.org/en-US/docs/Web/API/Window.getComputedStyle
 * @return {?CSSStyleDeclaration}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSview-getComputedStyle
 * @see https://bugzilla.mozilla.org/show_bug.cgi?id=548397
 */
ViewCSS.prototype.getComputedStyle = function(elt, opt_pseudoElt) {};

/**
 * @constructor
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-DocumentCSS
 */
function DocumentCSS() {}

/**
 * @param {Element} elt
 * @param {string} pseudoElt
 * @return {CSSStyleDeclaration}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-DocumentCSS-getOverrideStyle
 */
DocumentCSS.prototype.getOverrideStyle = function(elt, pseudoElt) {};

/**
 * @constructor
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-DOMImplementationCSS
 */
function DOMImplementationCSS() {}

/**
 * @param {string} title
 * @param {string} media
 * @return {CSSStyleSheet}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-DOMImplementationCSS-createCSSStyleSheet
 * @throws DOMException {@see DomException.SYNTAX_ERR}
 */
DOMImplementationCSS.prototype.createCSSStyleSheet = function(title, media) {};

/**
 * @record
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-ElementCSSInlineStyle
 */
function ElementCSSInlineStyle() {}

/**
 * @type {CSSStyleDeclaration}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-ElementCSSInlineStyle-style
 */
ElementCSSInlineStyle.prototype.style;

/**
 * @constructor
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties
 */
function CSSStyleProperties() {}

/**
 * Legacy compatibility class for style property dictionaries.
 * @constructor
 * @extends {CSSStyleProperties}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties
 */
function CSSProperties() {}

// CSS 2 properties

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-azimuth
 */
CSSStyleProperties.prototype.azimuth;

/**
 * @type {string}
 * @see https://drafts.fxtf.org/filter-effects-2/#BackdropFilterProperty
 */
CSSStyleProperties.prototype.backdropFilter;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-background
 */
CSSStyleProperties.prototype.background;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-backgroundAttachment
 */
CSSStyleProperties.prototype.backgroundAttachment;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-backgroundColor
 */
CSSStyleProperties.prototype.backgroundColor;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-backgroundImage
 */
CSSStyleProperties.prototype.backgroundImage;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-backgroundPosition
 */
CSSStyleProperties.prototype.backgroundPosition;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-backgroundRepeat
 */
CSSStyleProperties.prototype.backgroundRepeat;

/**
 * @type {string}
 * @see http://www.w3.org/TR/css3-background/#the-background-size
 */
CSSStyleProperties.prototype.backgroundSize;

/**
 * @implicitCast
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-border
 */
CSSStyleProperties.prototype.border;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-borderCollapse
 */
CSSStyleProperties.prototype.borderCollapse;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-borderColor
 */
CSSStyleProperties.prototype.borderColor;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-borderSpacing
 */
CSSStyleProperties.prototype.borderSpacing;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSPrimitiveValue-borderStyle
 */
CSSStyleProperties.prototype.borderStyle;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-borderTop
 */
CSSStyleProperties.prototype.borderTop;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-borderRight
 */
CSSStyleProperties.prototype.borderRight;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-borderBottom
 */
CSSStyleProperties.prototype.borderBottom;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-borderLeft
 */
CSSStyleProperties.prototype.borderLeft;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-borderTopColor
 */
CSSStyleProperties.prototype.borderTopColor;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-borderRightColor
 */
CSSStyleProperties.prototype.borderRightColor;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-borderBottomColor
 */
CSSStyleProperties.prototype.borderBottomColor;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-borderLeftColor
 */
CSSStyleProperties.prototype.borderLeftColor;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-borderTopStyle
 */
CSSStyleProperties.prototype.borderTopStyle;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-borderRightStyle
 */
CSSStyleProperties.prototype.borderRightStyle;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-borderBottomStyle
 */
CSSStyleProperties.prototype.borderBottomStyle;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-borderLeftStyle
 */
CSSStyleProperties.prototype.borderLeftStyle;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-borderTopWidth
 */
CSSStyleProperties.prototype.borderTopWidth;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-borderRightWidth
 */
CSSStyleProperties.prototype.borderRightWidth;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-borderBottomWidth
 */
CSSStyleProperties.prototype.borderBottomWidth;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-borderLeftWidth
 */
CSSStyleProperties.prototype.borderLeftWidth;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-borderWidth
 */
CSSStyleProperties.prototype.borderWidth;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/css3-background/#the-border-radius
 */
CSSStyleProperties.prototype.borderRadius;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/css3-background/#the-border-radius
 */
CSSStyleProperties.prototype.borderBottomLeftRadius;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/css3-background/#the-border-radius
 */
CSSStyleProperties.prototype.borderBottomRightRadius;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/css3-background/#the-border-radius
 */
CSSStyleProperties.prototype.borderTopLeftRadius;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/css3-background/#the-border-radius
 */
CSSStyleProperties.prototype.borderTopRightRadius;

/**
 * @type {string}
 * @see http://www.w3.org/TR/css3-background/#the-border-image-source
 */
CSSStyleProperties.prototype.borderImageSource;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/css3-background/#the-border-image-slice
 */
CSSStyleProperties.prototype.borderImageSlice;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/css3-background/#the-border-image-width
 */
CSSStyleProperties.prototype.borderImageWidth;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/css3-background/#the-border-image-outset
 */
CSSStyleProperties.prototype.borderImageOutset;

/**
 * @type {string}
 * @see http://www.w3.org/TR/css3-background/#the-border-image-repeat
 */
CSSStyleProperties.prototype.borderImageRepeat;

/**
 * @type {string}
 * @see http://www.w3.org/TR/css3-background/#the-border-image
 */
CSSStyleProperties.prototype.borderImage;

/**
 * @type {string}
 * @see https://www.w3.org/TR/1998/REC-CSS2-19980512/visuren.html#propdef-bottom
 */
CSSStyleProperties.prototype.bottom;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-captionSide
 */
CSSStyleProperties.prototype.captionSide;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-clear
 */
CSSStyleProperties.prototype.clear;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-clip
 */
CSSStyleProperties.prototype.clip;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-color
 */
CSSStyleProperties.prototype.color;

/**
 * @type {string}
 * @see https://drafts.csswg.org/css-color-adjust/#color-scheme-prop
 */
CSSStyleProperties.prototype.colorScheme;

/**
 * @type {string}
 * @see http://www.w3.org/TR/css-contain-2/#contain-property
 */
CSSStyleProperties.prototype.contain;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-content
 */
CSSStyleProperties.prototype.content;

/**
 * @type {string}
 * @see http://www.w3.org/TR/css-contain-2/#content-visibility
 */
CSSStyleProperties.prototype.contentVisibility;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-counterIncrement
 */
CSSStyleProperties.prototype.counterIncrement;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-counterReset
 */
CSSStyleProperties.prototype.counterReset;

/**
 * This is not an official part of the W3C spec. In practice, this is a settable
 * property that works cross-browser. It is used in goog.dom.setProperties() and
 * needs to be extern'd so the --disambiguate_properties JS compiler pass works.
 * @type {string}
 */
CSSStyleProperties.prototype.cssText;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-cue
 */
CSSStyleProperties.prototype.cue;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-cueAfter
 */
CSSStyleProperties.prototype.cueAfter;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-cueBefore
 */
CSSStyleProperties.prototype.cueBefore;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-cursor
 */
CSSStyleProperties.prototype.cursor;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-direction
 */
CSSStyleProperties.prototype.direction;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-display
 */
CSSStyleProperties.prototype.display;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-elevation
 */
CSSStyleProperties.prototype.elevation;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-emptyCells
 */
CSSStyleProperties.prototype.emptyCells;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-cssFloat
 */
CSSStyleProperties.prototype.cssFloat;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-font
 */
CSSStyleProperties.prototype.font;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-fontFamily
 */
CSSStyleProperties.prototype.fontFamily;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-fontSize
 */
CSSStyleProperties.prototype.fontSize;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-fontSizeAdjust
 */
CSSStyleProperties.prototype.fontSizeAdjust;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-fontStretch
 */
CSSStyleProperties.prototype.fontStretch;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-fontStyle
 */
CSSStyleProperties.prototype.fontStyle;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-fontVariant
 */
CSSStyleProperties.prototype.fontVariant;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-fontWeight
 */
CSSStyleProperties.prototype.fontWeight;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-align-3/#propdef-gap
 */
CSSStyleProperties.prototype.gap;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-grid-1/#propdef-grid
 */
CSSStyleProperties.prototype.grid;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-grid-1/#propdef-grid-area
 */
CSSStyleProperties.prototype.gridArea;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-grid-1/#propdef-grid-auto-columns
 */
CSSStyleProperties.prototype.gridAutoColumns;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-grid-1/#propdef-grid-auto-flow
 */
CSSStyleProperties.prototype.gridAutoFlow;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-grid-1/#propdef-grid-auto-rows
 */
CSSStyleProperties.prototype.gridAutoRows;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-grid-1/#propdef-grid-column
 */
CSSStyleProperties.prototype.gridColumn;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-grid-1/#propdef-grid-column-end
 */
CSSStyleProperties.prototype.gridColumnEnd;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-grid-1/#propdef-grid-column-start
 */
CSSStyleProperties.prototype.gridColumnStart;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-grid-1/#propdef-grid-row
 */
CSSStyleProperties.prototype.gridRow;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-grid-1/#propdef-grid-row-end
 */
CSSStyleProperties.prototype.gridRowEnd;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-grid-1/#propdef-grid-row-start
 */
CSSStyleProperties.prototype.gridRowStart;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-grid-1/#propdef-grid-template
 */
CSSStyleProperties.prototype.gridTemplate;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-grid-1/#propdef-grid-template-areas
 */
CSSStyleProperties.prototype.gridTemplateAreas;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-grid-1/#propdef-grid-template-columns
 */
CSSStyleProperties.prototype.gridTemplateColumns;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-grid-1/#propdef-grid-template-rows
 */
CSSStyleProperties.prototype.gridTemplateRows;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-height
 */
CSSStyleProperties.prototype.height;

/**
 * @type {string}
 * @see https://drafts.csswg.org/css-logical/#propdef-inset
 */
CSSStyleProperties.prototype.inset;

/**
 * @type {string}
 * @see https://drafts.csswg.org/css-logical/#position-properties
 */
CSSStyleProperties.prototype.insetBlock;

/**
 * @type {string}
 * @see https://drafts.csswg.org/css-logical/#position-properties
 */
CSSStyleProperties.prototype.insetBlockEnd;

/**
 * @type {string}
 * @see https://drafts.csswg.org/css-logical/#position-properties
 */
CSSStyleProperties.prototype.insetBlockStart;

/**
 * @type {string}
 * @see https://drafts.csswg.org/css-logical/#position-properties
 */
CSSStyleProperties.prototype.insetInline;

/**
 * @type {string}
 * @see https://drafts.csswg.org/css-logical/#position-properties
 */
CSSStyleProperties.prototype.insetInlineEnd;

/**
 * @type {string}
 * @see https://drafts.csswg.org/css-logical/#position-properties
 */
CSSStyleProperties.prototype.insetInlineStart;

/**
 * @type {string}
 * @see https://www.w3.org/TR/1998/REC-CSS2-19980512/visuren.html#propdef-left
 */
CSSStyleProperties.prototype.left;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-letterSpacing
 */
CSSStyleProperties.prototype.letterSpacing;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-lineHeight
 */
CSSStyleProperties.prototype.lineHeight;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-listStyle
 */
CSSStyleProperties.prototype.listStyle;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-listStyleImage
 */
CSSStyleProperties.prototype.listStyleImage;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-listStylePosition
 */
CSSStyleProperties.prototype.listStylePosition;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-listStyleType
 */
CSSStyleProperties.prototype.listStyleType;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-margin
 */
CSSStyleProperties.prototype.margin;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-marginTop
 */
CSSStyleProperties.prototype.marginTop;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-marginRight
 */
CSSStyleProperties.prototype.marginRight;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-marginBottom
 */
CSSStyleProperties.prototype.marginBottom;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-marginLeft
 */
CSSStyleProperties.prototype.marginLeft;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-markerOffset
 */
CSSStyleProperties.prototype.markerOffset;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-marks
 */
CSSStyleProperties.prototype.marks;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-maxHeight
 */
CSSStyleProperties.prototype.maxHeight;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-maxWidth
 */
CSSStyleProperties.prototype.maxWidth;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-minHeight
 */
CSSStyleProperties.prototype.minHeight;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-minWidth
 */
CSSStyleProperties.prototype.minWidth;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-orphans
 */
CSSStyleProperties.prototype.orphans;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-outline
 */
CSSStyleProperties.prototype.outline;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-outlineColor
 */
CSSStyleProperties.prototype.outlineColor;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-outlineStyle
 */
CSSStyleProperties.prototype.outlineStyle;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-outlineWidth
 */
CSSStyleProperties.prototype.outlineWidth;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-overflow
 */
CSSStyleProperties.prototype.overflow;

/**
 * @type {string}
 * @see https://drafts.csswg.org/css-scroll-anchoring/#exclusion-api
 */
CSSStyleProperties.prototype.overflowAnchor;

/**
 * @type {string}
 * @see https://drafts.csswg.org/css-overflow/#overflow-clip-margin
 */
CSSStyleProperties.prototype.overflowClipMargin;

/**
 * @type {string}
 * @see https://drafts.csswg.org/css-text/#overflow-wrap-property
 */
CSSStyleProperties.prototype.overflowWrap;

/**
 * @type {string}
 * @see https://drafts.csswg.org/css-overflow/#overflow-properties
 */
CSSStyleProperties.prototype.overflowX;

/**
 * @type {string}
 * @see https://drafts.csswg.org/css-overflow/#overflow-properties
 */
CSSStyleProperties.prototype.overflowY;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-overscroll-1/#propdef-overscroll-behavior
 */
CSSStyleProperties.prototype.overscrollBehavior;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-padding
 */
CSSStyleProperties.prototype.padding;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-paddingTop
 */
CSSStyleProperties.prototype.paddingTop;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-paddingRight
 */
CSSStyleProperties.prototype.paddingRight;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-paddingBottom
 */
CSSStyleProperties.prototype.paddingBottom;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-paddingLeft
 */
CSSStyleProperties.prototype.paddingLeft;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-page
 */
CSSStyleProperties.prototype.page;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-pageBreakAfter
 */
CSSStyleProperties.prototype.pageBreakAfter;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-pageBreakBefore
 */
CSSStyleProperties.prototype.pageBreakBefore;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-pageBreakInside
 */
CSSStyleProperties.prototype.pageBreakInside;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-pause
 */
CSSStyleProperties.prototype.pause;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-pauseAfter
 */
CSSStyleProperties.prototype.pauseAfter;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-pauseBefore
 */
CSSStyleProperties.prototype.pauseBefore;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-pitch
 */
CSSStyleProperties.prototype.pitch;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-pitchRange
 */
CSSStyleProperties.prototype.pitchRange;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-playDuring
 */
CSSStyleProperties.prototype.playDuring;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-position
 */
CSSStyleProperties.prototype.position;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-quotes
 */
CSSStyleProperties.prototype.quotes;

/**
 * @type {string}
 * @see http://www.w3.org/TR/css3-ui/#resize
 */
CSSStyleProperties.prototype.resize;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-richness
 */
CSSStyleProperties.prototype.richness;

/**
 * @type {string}
 * @see https://www.w3.org/TR/1998/REC-CSS2-19980512/visuren.html#propdef-right
 */
CSSStyleProperties.prototype.right;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-size
 */
CSSStyleProperties.prototype.size;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-speak
 */
CSSStyleProperties.prototype.speak;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-speakHeader
 */
CSSStyleProperties.prototype.speakHeader;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-speakNumeral
 */
CSSStyleProperties.prototype.speakNumeral;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-speakPunctuation
 */
CSSStyleProperties.prototype.speakPunctuation;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-speechRate
 */
CSSStyleProperties.prototype.speechRate;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-stress
 */
CSSStyleProperties.prototype.stress;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-tableLayout
 */
CSSStyleProperties.prototype.tableLayout;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-textAlign
 */
CSSStyleProperties.prototype.textAlign;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-textDecoration
 */
CSSStyleProperties.prototype.textDecoration;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-text-decor-3/#text-decoration-line-property
 */
CSSStyleProperties.prototype.textDecorationLine;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-text-decor-3/#text-decoration-style-property
 */
CSSStyleProperties.prototype.textDecorationStyle;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-text-decor-3/#text-decoration-color-property
 */
CSSStyleProperties.prototype.textDecorationColor;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-text-decor-3/#text-underline-position-property
 */
CSSStyleProperties.prototype.textDecorationPosition;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-textIndent
 */
CSSStyleProperties.prototype.textIndent;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-textShadow
 */
CSSStyleProperties.prototype.textShadow;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-textTransform
 */
CSSStyleProperties.prototype.textTransform;

/**
 * @type {string}
 * @see https://www.w3.org/TR/1998/REC-CSS2-19980512/visuren.html#propdef-top
 */
CSSStyleProperties.prototype.top;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-unicodeBidi
 */
CSSStyleProperties.prototype.unicodeBidi;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-verticalAlign
 */
CSSStyleProperties.prototype.verticalAlign;

/**
 * @type {string}
 * @see https://www.w3.org/TR/scroll-animations-1/#view-timeline-shorthand
 */
CSSStyleProperties.prototype.viewTimeline;

/**
 * @type {string}
 * @see https://www.w3.org/TR/scroll-animations-1/#view-timeline-axis
 */
CSSStyleProperties.prototype.viewTimelineAxis;

/**
 * @type {string}
 * @see https://www.w3.org/TR/scroll-animations-1/#view-timeline-inset
 */
CSSStyleProperties.prototype.viewTimelineInset;

/**
 * @type {string}
 * @see https://www.w3.org/TR/scroll-animations-1/#view-timeline-name
 */
CSSStyleProperties.prototype.viewTimelineName;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-view-transitions-2/#view-transition-class-prop
 */
CSSStyleProperties.prototype.viewTransitionClass;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-visibility
 */
CSSStyleProperties.prototype.visibility;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-voiceFamily
 */
CSSStyleProperties.prototype.voiceFamily;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-volume
 */
CSSStyleProperties.prototype.volume;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-whiteSpace
 */
CSSStyleProperties.prototype.whiteSpace;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-widows
 */
CSSStyleProperties.prototype.widows;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-width
 */
CSSStyleProperties.prototype.width;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-wordSpacing
 */
CSSStyleProperties.prototype.wordSpacing;

/**
 * @type {string}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-wordWrap
 */
CSSStyleProperties.prototype.wordWrap;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/DOM-Level-2-Style/css.html#CSS-CSSProperties-zIndex
 */
CSSStyleProperties.prototype.zIndex;

// CSS 3 properties

/** @type {string} */
CSSStyleProperties.prototype.boxDecorationBreak;

/**
 * @type {string}
 * @see http://www.w3.org/TR/css3-background/#box-shadow
 */
CSSStyleProperties.prototype.boxShadow;

/**
 * @type {string}
 * @see http://www.w3.org/TR/css3-ui/#box-sizing
 */
CSSStyleProperties.prototype.boxSizing;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/css3-color/#transparency
 */
CSSStyleProperties.prototype.opacity;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-ui-3/#outline-offset
 */
CSSStyleProperties.prototype.outlineOffset;

/**
 * @type {string}
 * @see http://www.w3.org/TR/css3-ui/#text-overflow
 */
CSSStyleProperties.prototype.textOverflow;

// CSS 3 animations

/**
 * @type {string|number}
 * @see https://www.w3.org/TR/css-animations-1/#animation
 */
CSSStyleProperties.prototype.animation;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-animations-1/#animation-delay
 */
CSSStyleProperties.prototype.animationDelay;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-animations-1/#animation-direction
 */
CSSStyleProperties.prototype.animationDirection;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-animations-1/#animation-duration
 */
CSSStyleProperties.prototype.animationDuration;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-animations-1/#animation-fill-mode
 */
CSSStyleProperties.prototype.animationFillMode;

/**
 * @type {string|number}
 * @see https://www.w3.org/TR/css-animations-1/#animation-iteration-count
 */
CSSStyleProperties.prototype.animationIterationCount;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-animations-1/#animation-name
 */
CSSStyleProperties.prototype.animationName;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-animations-1/#animation-play-state
 */
CSSStyleProperties.prototype.animationPlayState;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-animations-1/#animation-timing-function
 */
CSSStyleProperties.prototype.animationTimingFunction;

// CSS 3 transforms

/**
 * @type {string}
 * @see http://www.w3.org/TR/css3-2d-transforms/#backface-visibility-property
 */
CSSStyleProperties.prototype.backfaceVisibility;

/**
 * @type {string}
 * @see http://www.w3.org/TR/css3-2d-transforms/#perspective
 */
CSSStyleProperties.prototype.perspective;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/css3-2d-transforms/#perspective-origin
 */
CSSStyleProperties.prototype.perspectiveOrigin;

/**
 * @type {string}
 * @see http://www.w3.org/TR/css3-2d-transforms/#effects
 */
CSSStyleProperties.prototype.transform;

/**
 * @type {string|number}
 * @see http://www.w3.org/TR/css3-2d-transforms/#transform-origin
 */
CSSStyleProperties.prototype.transformOrigin;

/**
 * @type {string}
 * @see http://www.w3.org/TR/css3-2d-transforms/#transform-style
 */
CSSStyleProperties.prototype.transformStyle;

// CSS 3 transitions

/**
 * @type {string}
 * @see http://www.w3.org/TR/css3-transitions/#transition
 */
CSSStyleProperties.prototype.transition;

/**
 * @type {string}
 * @see http://www.w3.org/TR/css3-transitions/#transition-delay
 */
CSSStyleProperties.prototype.transitionDelay;

/**
 * @type {string}
 * @see http://www.w3.org/TR/css3-transitions/#transition-duration
 */
CSSStyleProperties.prototype.transitionDuration;

/**
 * @type {string}
 * @see http://www.w3.org/TR/css3-transitions/#transition-property-property
 */
CSSStyleProperties.prototype.transitionProperty;

/**
 * @type {string}
 * @see http://www.w3.org/TR/css3-transitions/#transition-timing-function
 */
CSSStyleProperties.prototype.transitionTimingFunction;

/**
 * @type {string}
 * @see http://www.w3.org/TR/SVG11/interact.html#PointerEventsProperty
 */
CSSStyleProperties.prototype.pointerEvents;

// CSS Compositing 1

/**
 * @type {string}
 * @see https://www.w3.org/TR/compositing-1/#mix-blend-mode
 */
CSSStyleProperties.prototype.mixBlendMode;

/**
 * @type {string}
 * @see https://www.w3.org/TR/compositing-1/#isolation
 */
CSSStyleProperties.prototype.isolation;

/**
 * @type {string}
 * @see https://www.w3.org/TR/compositing-1/#background-blend-mode
 */
CSSStyleProperties.prototype.backgroundBlendMode;


// CSS Flexbox 1


/**
 * @type {string}
 * @see https://www.w3.org/TR/css-flexbox-1/#align-content-property
 */
CSSStyleProperties.prototype.alignContent;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-flexbox-1/#align-items-property
 */
CSSStyleProperties.prototype.alignItems;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-flexbox-1/#align-items-property
 */
CSSStyleProperties.prototype.alignSelf;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-flexbox-1/#flex-property
 */
CSSStyleProperties.prototype.flex;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-flexbox-1/#flex-basis-property
 */
CSSStyleProperties.prototype.flexBasis;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-flexbox-1/#flex-direction-property
 */
CSSStyleProperties.prototype.flexDirection;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-flexbox-1/#flex-flow-property
 */
CSSStyleProperties.prototype.flexFlow;

/**
 * @type {number}
 * @see https://www.w3.org/TR/css-flexbox-1/#flex-grow-property
 */
CSSStyleProperties.prototype.flexGrow;

/**
 * @type {number}
 * @see https://www.w3.org/TR/css-flexbox-1/#flex-shrink-property
 */
CSSStyleProperties.prototype.flexShrink;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-flexbox-1/#flex-wrap-property
 */
CSSStyleProperties.prototype.flexWrap;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-flexbox-1/#justify-content-property
 */
CSSStyleProperties.prototype.justifyContent;

/**
 * @type {number}
 * @see https://www.w3.org/TR/css-flexbox-1/#order-property
 */
CSSStyleProperties.prototype.order;

// Externs for CSS Will Change Module Level 1
// http://www.w3.org/TR/css-will-change/

/**
 * @type {string}
 * @see http://www.w3.org/TR/css-will-change-1/#will-change
 */
CSSStyleProperties.prototype.willChange;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-ui-4/#propdef-user-select
 */
CSSStyleProperties.prototype.userSelect;

// CSS 3 Images

/**
 * @type {string}
 * @see https://www.w3.org/TR/css3-images/#the-object-fit
 */
CSSStyleProperties.prototype.objectFit;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css3-images/#object-position
 */
CSSStyleProperties.prototype.objectPosition;

// CSS Masking

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-masking-1/
 */
CSSStyleProperties.prototype.clipPath;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-masking-1/
 */
CSSStyleProperties.prototype.maskImage;

// CSS Containment

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-contain-1/
 */
CSSStyleProperties.prototype.contain;

// SVG Fill Properties

/**
 * @type {string}
 * @see https://www.w3.org/TR/fill-stroke-3/#fill-shorthand
 */
CSSStyleProperties.prototype.fill;

/**
 * @type {string}
 * @see https://www.w3.org/TR/fill-stroke-3/#fill-opacity
 */
CSSStyleProperties.prototype.fillOpacity;

/**
 * @type {string}
 * @see https://www.w3.org/TR/fill-stroke-3/#fill-rule
 */
CSSStyleProperties.prototype.fillRule;

// SVG Stroke Properties

/**
 * @type {string}
 * @see https://www.w3.org/TR/svg-strokes/
 */
CSSStyleProperties.prototype.stroke;

/**
 * @type {string}
 * @see https://www.w3.org/TR/svg-strokes/
 */
CSSStyleProperties.prototype.strokeAlignment;

/**
 * @type {string}
 * @see https://www.w3.org/TR/svg-strokes/
 */
CSSStyleProperties.prototype.strokeOpacity;

/**
 * @type {string}
 * @see https://www.w3.org/TR/svg-strokes/
 */
CSSStyleProperties.prototype.strokeWidth;

/**
 * @type {string}
 * @see https://www.w3.org/TR/svg-strokes/
 */
CSSStyleProperties.prototype.strokeLinecap;

/**
 * @type {string}
 * @see https://www.w3.org/TR/svg-strokes/
 */
CSSStyleProperties.prototype.strokeLinejoin;

/**
 * @type {string}
 * @see https://www.w3.org/TR/svg-strokes/
 */
CSSStyleProperties.prototype.strokeMiterlimit;

/**
 * @type {string}
 * @see https://www.w3.org/TR/svg-strokes/
 */
CSSStyleProperties.prototype.strokeDasharray;

/**
 * @type {string}
 * @see https://www.w3.org/TR/svg-strokes/
 */
CSSStyleProperties.prototype.strokeDashoffset;

/**
 * @type {string}
 * @see https://www.w3.org/TR/svg-strokes/
 */
CSSStyleProperties.prototype.strokeDashcorner;

/**
 * @type {string}
 * @see https://www.w3.org/TR/svg-strokes/
 */
CSSStyleProperties.prototype.strokeDashadjust;

// New properties in TypeScript 6

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/all
 */
CSSStyleProperties.prototype.all;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/anchor-name
 */
CSSStyleProperties.prototype.anchorName;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/anchor-scope
 */
CSSStyleProperties.prototype.anchorScope;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/animation-range
 */
CSSStyleProperties.prototype.animationRange;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/animation-range-end
 */
CSSStyleProperties.prototype.animationRangeEnd;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/animation-range-start
 */
CSSStyleProperties.prototype.animationRangeStart;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/animation-timeline
 */
CSSStyleProperties.prototype.animationTimeline;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/aspect-ratio
 */
CSSStyleProperties.prototype.aspectRatio;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/background-position-x
 */
CSSStyleProperties.prototype.backgroundPositionX;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/background-position-y
 */
CSSStyleProperties.prototype.backgroundPositionY;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/block-size
 */
CSSStyleProperties.prototype.blockSize;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/container-name
 */
CSSStyleProperties.prototype.containerName;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/container-type
 */
CSSStyleProperties.prototype.containerType;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/cx
 */
CSSStyleProperties.prototype.cx;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/cy
 */
CSSStyleProperties.prototype.cy;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/d
 */
CSSStyleProperties.prototype.d;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/dynamic-range-limit
 */
CSSStyleProperties.prototype.dynamicRangeLimit;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/field-sizing
 */
CSSStyleProperties.prototype.fieldSizing;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/filter
 */
CSSStyleProperties.prototype.filter;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/font-language-override
 */
CSSStyleProperties.prototype.fontLanguageOverride;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/font-variant-emoji
 */
CSSStyleProperties.prototype.fontVariantEmoji;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/image-orientation
 */
CSSStyleProperties.prototype.imageOrientation;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/inline-size
 */
CSSStyleProperties.prototype.inlineSize;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/math-shift
 */
CSSStyleProperties.prototype.mathShift;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/offset
 */
CSSStyleProperties.prototype.offset;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/position-anchor
 */
CSSStyleProperties.prototype.positionAnchor;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/position-area
 */
CSSStyleProperties.prototype.positionArea;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/position-try
 */
CSSStyleProperties.prototype.positionTry;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/position-try-fallbacks
 */
CSSStyleProperties.prototype.positionTryFallbacks;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/position-try-order
 */
CSSStyleProperties.prototype.positionTryOrder;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/position-visibility
 */
CSSStyleProperties.prototype.positionVisibility;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/r
 */
CSSStyleProperties.prototype.r;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/rotate
 */
CSSStyleProperties.prototype.rotate;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/rx
 */
CSSStyleProperties.prototype.rx;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/ry
 */
CSSStyleProperties.prototype.ry;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/scale
 */
CSSStyleProperties.prototype.scale;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/scroll-padding
 */
CSSStyleProperties.prototype.scrollPadding;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/scroll-padding-block
 */
CSSStyleProperties.prototype.scrollPaddingBlock;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/scroll-padding-block-end
 */
CSSStyleProperties.prototype.scrollPaddingBlockEnd;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/scroll-padding-block-start
 */
CSSStyleProperties.prototype.scrollPaddingBlockStart;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/scroll-padding-bottom
 */
CSSStyleProperties.prototype.scrollPaddingBottom;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/scroll-padding-inline
 */
CSSStyleProperties.prototype.scrollPaddingInline;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/scroll-padding-inline-end
 */
CSSStyleProperties.prototype.scrollPaddingInlineEnd;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/scroll-padding-inline-start
 */
CSSStyleProperties.prototype.scrollPaddingInlineStart;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/scroll-padding-left
 */
CSSStyleProperties.prototype.scrollPaddingLeft;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/scroll-padding-right
 */
CSSStyleProperties.prototype.scrollPaddingRight;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/scroll-padding-top
 */
CSSStyleProperties.prototype.scrollPaddingTop;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/scroll-timeline
 */
CSSStyleProperties.prototype.scrollTimeline;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/scroll-timeline-axis
 */
CSSStyleProperties.prototype.scrollTimelineAxis;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/scroll-timeline-name
 */
CSSStyleProperties.prototype.scrollTimelineName;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/text-autospace
 */
CSSStyleProperties.prototype.textAutospace;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/text-justify
 */
CSSStyleProperties.prototype.textJustify;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/timeline-scope
 */
CSSStyleProperties.prototype.timelineScope;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/touch-action
 */
CSSStyleProperties.prototype.touchAction;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/translate
 */
CSSStyleProperties.prototype.translate;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/view-transition-name
 */
CSSStyleProperties.prototype.viewTransitionName;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitAlignContent;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitAlignItems;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitAlignSelf;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitAnimation;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitAnimationDelay;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitAnimationDirection;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitAnimationDuration;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitAnimationFillMode;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitAnimationIterationCount;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitAnimationName;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitAnimationPlayState;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitAnimationTimingFunction;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitAppearance;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitBackfaceVisibility;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitBackgroundClip;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitBackgroundOrigin;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitBackgroundSize;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitBorderBottomLeftRadius;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitBorderBottomRightRadius;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitBorderRadius;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitBorderTopLeftRadius;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitBorderTopRightRadius;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitBoxAlign;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitBoxFlex;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitBoxOrdinalGroup;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitBoxOrient;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitBoxPack;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitBoxShadow;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitBoxSizing;

/**
 * @type {string}
 * @see https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Webkit_extensions
 */
CSSStyleProperties.prototype.webkitFilter;

/**
 * TODO(dbeam): Put this in separate file named w3c_cssom.js.
 * Externs for the CSSOM View Module.
 * @see http://www.w3.org/TR/cssom-view/
 */

// http://www.w3.org/TR/cssom-view/#extensions-to-the-window-interface

/**
 * @param {string} media_query_list
 * @return {!MediaQueryList}
 * @see http://www.w3.org/TR/cssom-view/#dom-window-matchmedia
 */
Window.prototype.matchMedia = function(media_query_list) {};

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-window-innerwidth
 */
Window.prototype.innerWidth;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-window-innerheight
 */
Window.prototype.innerHeight;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-window-scrollx
 */
Window.prototype.scrollX;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-window-pagexoffset
 */
Window.prototype.pageXOffset;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-window-scrolly
 */
Window.prototype.scrollY;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-window-pageyoffset
 */
Window.prototype.pageYOffset;

/**
 * @typedef {{
 *   left: (number|undefined),
 *   top: (number|undefined),
 *   behavior: (string|undefined)
 * }}
 * @see https://www.w3.org/TR/cssom-view/#dictdef-scrolltooptions
 */
var ScrollToOptions;

/**
 * @record
 * @see https://www.w3.org/TR/cssom-view/#dictdef-scrollintoviewoptions
 */
function ScrollIntoViewOptions() {}

/** @type {string|undefined} */
ScrollIntoViewOptions.prototype.behavior;

/** @type {string|undefined} */
ScrollIntoViewOptions.prototype.block;

/** @type {string|undefined} */
ScrollIntoViewOptions.prototype.inline;

/**
 * @param {number|!ScrollToOptions} scrollToOptionsOrX
 * @param {number=} opt_y
 * @see http://www.w3.org/TR/cssom-view/#dom-window-scroll
 * @return {undefined}
 */
Window.prototype.scroll = function(scrollToOptionsOrX, opt_y) {};

/**
 * @param {number|!ScrollToOptions} scrollToOptionsOrX
 * @param {number=} opt_y
 * @see http://www.w3.org/TR/cssom-view/#dom-window-scrollto
 * @return {undefined}
 */
Window.prototype.scrollTo = function(scrollToOptionsOrX, opt_y) {};

/**
 * @param {number|!ScrollToOptions} scrollToOptionsOrX
 * @param {number=} opt_y
 * @see http://www.w3.org/TR/cssom-view/#dom-window-scrollby
 * @return {undefined}
 */
Window.prototype.scrollBy = function(scrollToOptionsOrX, opt_y) {};

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-window-screenx
 */
Window.prototype.screenX;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-window-screeny
 */
Window.prototype.screenY;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-window-outerwidth
 */
Window.prototype.outerWidth;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-window-outerheight
 */
Window.prototype.outerHeight;

/**
 * @type {number}
 * @see https://www.w3.org/TR/cssom-view/#dom-window-devicepixelratio
 */
Window.prototype.devicePixelRatio;

/**
 * @param {number} x
 * @param {number} y
 * @return {undefined}
 * @see https://www.w3.org/TR/cssom-view/#dom-window-moveto
 */
Window.prototype.moveTo = function(x, y) {};

/**
 * @param {number} x
 * @param {number} y
 * @return {undefined}
 * @see https://www.w3.org/TR/cssom-view/#dom-window-moveby
 */
Window.prototype.moveBy = function(x, y) {};

/**
 * @param {number} x
 * @param {number} y
 * @return {undefined}
 * @see https://www.w3.org/TR/cssom-view/#dom-window-resizeto
 */
Window.prototype.resizeTo = function(x, y) {};

/**
 * @param {number} x
 * @param {number} y
 * @return {undefined}
 * @see https://www.w3.org/TR/cssom-view/#dom-window-resizeby
 */
Window.prototype.resizeBy = function(x, y) {};

/**
 * @constructor
 * @implements {EventTarget}
 * @see http://www.w3.org/TR/cssom-view/#mediaquerylist
 */
function MediaQueryList() {}

/**
 * @type {string}
 * @see http://www.w3.org/TR/cssom-view/#dom-mediaquerylist-media
 */
MediaQueryList.prototype.media;

/**
 * @type {boolean}
 * @see http://www.w3.org/TR/cssom-view/#dom-mediaquerylist-matches
 */
MediaQueryList.prototype.matches;

/**
 * @param {MediaQueryListListener} listener
 * @see http://www.w3.org/TR/cssom-view/#dom-mediaquerylist-addlistener
 * @return {undefined}
 */
MediaQueryList.prototype.addListener = function(listener) {};

/**
 * @param {MediaQueryListListener} listener
 * @see http://www.w3.org/TR/cssom-view/#dom-mediaquerylist-removelistener
 * @return {undefined}
 */
MediaQueryList.prototype.removeListener = function(listener) {};

/** @override Not available in some browsers; use addListener instead. */
MediaQueryList.prototype.addEventListener = function(
    type, listener, opt_options) {};

/** @override Not available in old browsers; use removeListener instead. */
MediaQueryList.prototype.removeEventListener = function(
    type, listener, opt_options) {};

/** @override */
MediaQueryList.prototype.dispatchEvent = function(evt) {};

/**
 * @typedef {(function(!MediaQueryList) : void)}
 * @see http://www.w3.org/TR/cssom-view/#mediaquerylistlistener
 */
var MediaQueryListListener;

/**
 * @see https://developer.mozilla.org/en-US/docs/Web/API/MediaQueryListEvent
 * @constructor
 * @extends {Event}
 */
function MediaQueryListEvent() {}

/**
 * A boolean value; returns true if the document currently matches the media
 * query list, false if not.
 * @const {boolean}
 */
MediaQueryListEvent.prototype.matches;

/**
 * A String representing a serialized media query.
 * @const {string}
 */
MediaQueryListEvent.prototype.media;

/**
 * @constructor
 * @see http://www.w3.org/TR/cssom-view/#screen
 */
function Screen() {}

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-screen-availwidth
 */
Screen.prototype.availWidth;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-screen-availheight
 */
Screen.prototype.availHeight;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-screen-width
 */
Screen.prototype.width;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-screen-height
 */
Screen.prototype.height;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-screen-colordepth
 */
Screen.prototype.colorDepth;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-screen-pixeldepth
 */
Screen.prototype.pixelDepth;


// http://www.w3.org/TR/cssom-view/#extensions-to-the-document-interface

/**
 * @param {number} x
 * @param {number} y
 * @return {?Element}
 * @see http://www.w3.org/TR/cssom-view/#dom-document-elementfrompoint
 */
Document.prototype.elementFromPoint = function(x, y) {};

/**
 * @param {number} x
 * @param {number} y
 * @return {!IArrayLike<!Element>}
 * @see http://www.w3.org/TR/cssom-view/#dom-document-elementsfrompoint
 */
Document.prototype.elementsFromPoint = function(x, y) {};

/**
 * @param {number} x
 * @param {number} y
 * @return {CaretPosition}
 * @see http://www.w3.org/TR/cssom-view/#dom-document-caretpositionfrompoint
 */
Document.prototype.caretPositionFromPoint = function(x, y) {};

/**
 * @type {Element}
 * @see http://dev.w3.org/csswg/cssom-view/#dom-document-scrollingelement
 */
Document.prototype.scrollingElement;

/**
 * @constructor
 * @see http://www.w3.org/TR/cssom-view/#caretposition
 */
function CaretPosition() {}

/**
 * @type {Node}
 * @see http://www.w3.org/TR/cssom-view/#dom-caretposition-offsetnode
 */
CaretPosition.prototype.offsetNode;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-caretposition-offset
 */
CaretPosition.prototype.offset;

/**
 * @return {?DOMRect}
 */
CaretPosition.prototype.getClientRect = function() {};

/**
 * @type {!StylePropertyMap}
 */
Element.prototype.attributeStyleMap;

// http://www.w3.org/TR/cssom-view/#extensions-to-the-element-interface

/**
 * @return {!ClientRectList}
 * @see http://www.w3.org/TR/cssom-view/#dom-element-getclientrects
 */
Element.prototype.getClientRects = function() {};

/**
 * @return {!DOMRect}
 * @see http://www.w3.org/TR/cssom-view/#dom-element-getboundingclientrect
 */
Element.prototype.getBoundingClientRect = function() {};

/**
 * @param {(boolean|ScrollIntoViewOptions)=} top
 * @see http://www.w3.org/TR/cssom-view/#dom-element-scrollintoview
 * @return {undefined}
 */
Element.prototype.scrollIntoView = function(top) {};

/**
 * @param {number|!ScrollToOptions} scrollToOptionsOrX
 * @param {number=} opt_y
 * @see https://www.w3.org/TR/cssom-view/#extension-to-the-element-interface
 * @return {undefined}
 */
Element.prototype.scrollTo = function(scrollToOptionsOrX, opt_y) {};

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-element-scrolltop
 */
Element.prototype.scrollTop;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-element-scrollleft
 */
Element.prototype.scrollLeft;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-element-scrollwidth
 */
Element.prototype.scrollWidth;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-element-scrollheight
 */
Element.prototype.scrollHeight;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-scroll-snap-1/
 */
Element.prototype.scrollPadding;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-scroll-snap-1/
 */
Element.prototype.scrollPaddingBlock;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-scroll-snap-1/
 */
Element.prototype.scrollPaddingBlockEnd;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-scroll-snap-1/
 */
Element.prototype.scrollPaddingBlockStart;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-scroll-snap-1/
 */
Element.prototype.scrollPaddingBottom;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-scroll-snap-1/
 */
Element.prototype.scrollPaddingInline;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-scroll-snap-1/
 */
Element.prototype.scrollPaddingInlineEnd;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-scroll-snap-1/
 */
Element.prototype.scrollPaddingInlineStart;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-scroll-snap-1/
 */
Element.prototype.scrollPaddingLeft;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-scroll-snap-1/
 */
Element.prototype.scrollPaddingRight;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-scroll-snap-1/
 */
Element.prototype.scrollPaddingTop;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-element-clienttop
 */
Element.prototype.clientTop;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-element-clientleft
 */
Element.prototype.clientLeft;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-element-clientwidth
 */
Element.prototype.clientWidth;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-element-clientheight
 */
Element.prototype.clientHeight;

/**
 * @type {number}
 * @see https://drafts.csswg.org/cssom-view/#dom-element-currentcsszoom
 */
Element.prototype.currentCSSZoom;

/**
 * @record
 * @see https://drafts.csswg.org/cssom-view/#dictdef-checkvisibilityoptions
 */
function CheckVisibilityOptions() {}

/** @type {(boolean|undefined)} */
CheckVisibilityOptions.prototype.checkOpacity;

/** @type {(boolean|undefined)} */
CheckVisibilityOptions.prototype.checkVisibilityCSS;

/** @type {(boolean|undefined)} */
CheckVisibilityOptions.prototype.contentVisibilityAuto;

/** @type {(boolean|undefined)} */
CheckVisibilityOptions.prototype.opacityProperty;

/** @type {(boolean|undefined)} */
CheckVisibilityOptions.prototype.visibilityProperty;

/**
 * @param {CheckVisibilityOptions=} options
 * @return {boolean}
 * @see https://drafts.csswg.org/cssom-view/#dom-element-checkvisibility
 */
Element.prototype.checkVisibility = function(options) {};

// http://www.w3.org/TR/cssom-view/#extensions-to-the-htmlelement-interface

/**
 * @type {Element}
 * @see http://www.w3.org/TR/cssom-view/#dom-htmlelement-offsetparent
 */
HTMLElement.prototype.offsetParent;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-htmlelement-offsettop
 */
HTMLElement.prototype.offsetTop;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-htmlelement-offsetleft
 */
HTMLElement.prototype.offsetLeft;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-htmlelement-offsetwidth
 */
HTMLElement.prototype.offsetWidth;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-htmlelement-offsetheight
 */
HTMLElement.prototype.offsetHeight;


// http://www.w3.org/TR/cssom-view/#extensions-to-the-range-interface

/**
 * @return {!ClientRectList}
 * @see http://www.w3.org/TR/cssom-view/#dom-range-getclientrects
 */
Range.prototype.getClientRects = function() {};

/**
 * @return {!DOMRect}
 * @see http://www.w3.org/TR/cssom-view/#dom-range-getboundingclientrect
 */
Range.prototype.getBoundingClientRect = function() {};


// http://www.w3.org/TR/cssom-view/#extensions-to-the-mouseevent-interface

// MouseEvent: screen{X,Y} and client{X,Y} are in DOM Level 2/3 Event as well,
// so it seems like a specification issue. I've emailed www-style@w3.org in
// hopes of resolving the conflict, but in the mean time they can live here
// (http://lists.w3.org/Archives/Public/www-style/2012May/0039.html).

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-mouseevent-screenx
 */
// MouseEvent.prototype.screenX;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-mouseevent-screeny
 */
// MouseEvent.prototype.screenY;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-mouseevent-pagex
 */
MouseEvent.prototype.pageX;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-mouseevent-pagey
 */
MouseEvent.prototype.pageY;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-mouseevent-clientx
 */
// MouseEvent.prototype.clientX;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-mouseevent-clienty
 */
// MouseEvent.prototype.clientY;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-mouseevent-x
 */
MouseEvent.prototype.x;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-mouseevent-y
 */
MouseEvent.prototype.y;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-mouseevent-offsetx
 */
MouseEvent.prototype.offsetX;

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-mouseevent-offsety
 */
MouseEvent.prototype.offsetY;


// http://www.w3.org/TR/cssom-view/#rectangles

/**
 * @constructor
 * @see http://www.w3.org/TR/cssom-view/#the-clientrectlist-interface
 * @implements {IArrayLike<!DOMRect>}
 */
function ClientRectList() {}

/**
 * @type {number}
 * @see http://www.w3.org/TR/cssom-view/#dom-clientrectlist-length
 */
ClientRectList.prototype.length;

/**
 * @param {number} index
 * @return {?DOMRect}
 * @see http://www.w3.org/TR/cssom-view/#dom-clientrectlist-item
 */
ClientRectList.prototype.item = function(index) {};

/**
 * @constructor
 * http://www.w3.org/TR/css3-conditional/#CSS-interface
 */
function CSSInterface() {}

/**
 * @param {string} ident
 * @return {string}
 * @see http://www.w3.org/TR/cssom/#the-css.escape()-method
 * @throws DOMException {@see DOMException.INVALID_CHARACTER_ERR}
 */
CSSInterface.prototype.escape = function(ident) {};

/**
 * @param {string} property
 * @param {string=} opt_value
 * @return {boolean}
 */
CSSInterface.prototype.supports = function(property, opt_value) {};

/**
* @typedef {{
*   name: string,
*   syntax: (string|undefined),
*   inherits: boolean,
*   initialValue: (string|undefined),
* }}
* @see https://www.w3.org/TR/css-properties-values-api-1/#the-propertydefinition-dictionary
*/
var PropertyDefinition;

/**
 * @param {PropertyDefinition} propertyDefinition
 * @return {undefined}
 * @see https://www.w3.org/TR/css-properties-values-api-1/#the-registerproperty-function
 * @throws {DOMException|TypeError} {@see DOMException.InvalidModificationError}, {@see DOMException.SyntaxError}
 */
CSSInterface.prototype.registerProperty = function(propertyDefinition) {};

/**
 * @constructor
 * @param {...!Range} initialRanges
 */
function Highlight(initialRanges) {}

/** @type {number} */
Highlight.prototype.priority;

/** @type {string} */
Highlight.prototype.type;

/**
 * @param {function(!Range, !Range, !Highlight): void} callbackfn
 * @param {?=} thisArg
 * @return {undefined}
 */
Highlight.prototype.forEach = function(callbackfn, thisArg) {};

/**
 * @interface
 * @see https://www.w3.org/TR/css-highlight-api-1/#registration
 */
function HighlightRegistry () {}

/**
 * @param {function(!Highlight, string, !HighlightRegistry): void} callbackfn
 * @param {?=} thisArg
 * @return {undefined}
 */
HighlightRegistry.prototype.forEach = function(callbackfn, thisArg) {};

/**
 * @param {string} key
 * @param {!Highlight} value
 * @return {!HighlightRegistry}
 */
HighlightRegistry.prototype.set = function(key, value) {};

/**
 * @param {string} key
 * @return {boolean}
 */
HighlightRegistry.prototype.delete = function(key) {};

/**
 * @return {void}
 */
HighlightRegistry.prototype.clear = function() {};

/**
 * @param {string} key
 * @return {boolean}
 */
HighlightRegistry.prototype.has = function(key) {};

/**
 * @param {string} key
 * @return {?Highlight}
 */
HighlightRegistry.prototype.get = function(key) {};

/** @type {number} */
HighlightRegistry.prototype.size;


/** @type {!HighlightRegistry} */
CSSInterface.prototype.highlights;

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.Hz = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.Q = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.cap = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.ch = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.cm = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.cqb = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.cqh = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.cqi = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.cqmax = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.cqmin = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.cqw = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.deg = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.dpcm = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.dpi = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.dppx = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.dvb = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.dvh = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.dvi = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.dvmax = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.dvmin = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.dvw = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.em = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.ex = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.fr = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.grad = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.ic = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.kHz = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.lh = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.lvb = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.lvh = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.lvi = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.lvmax = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.lvmin = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.lvw = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.mm = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.ms = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.number = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.pc = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.percent = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.pt = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.px = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.rad = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.rcap = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.rch = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.rem = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.rex = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.ric = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.rlh = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.s = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.svb = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.svh = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.svi = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.svmax = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.svmin = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.svw = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.turn = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.vb = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.vh = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.vi = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.vmax = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.vmin = function(value) {};

/**
 * @param {number} value
 * @return {CSSUnitValue}
 */
CSSInterface.prototype.vw = function(value) {};

/**
 * @type {CSSInterface}
 */
var CSS;

/** @type {CSSInterface} */
Window.prototype.CSS;

// http://dev.w3.org/csswg/css-font-loading/

/**
 * Set of possible string values: 'error', 'loaded', 'loading', 'unloaded'.
 * @typedef {string}
 * @see http://dev.w3.org/csswg/css-font-loading/#enumdef-fontfaceloadstatus
 */
var FontFaceLoadStatus;

/**
 * @typedef {{
 *   display: (string|undefined),
 *   style: (string|undefined),
 *   weight: (string|undefined),
 *   stretch: (string|undefined),
 *   unicodeRange: (string|undefined),
 *   variant: (string|undefined),
 *   featureSettings: (string|undefined)
 * }}
 * @see http://dev.w3.org/csswg/css-font-loading/#dictdef-fontfacedescriptors
 */
var FontFaceDescriptors;

/**
 * @constructor
 * @param {string} fontFamily
 * @param {(string|ArrayBuffer|ArrayBufferView)} source
 * @param {!FontFaceDescriptors=} opt_descriptors
 * @see http://dev.w3.org/csswg/css-font-loading/#font-face-constructor
 */
function FontFace(fontFamily, source, opt_descriptors) {}

/**
 * @type {string}
 * @see http://dev.w3.org/csswg/css-font-loading/#dom-fontface-family
 */
FontFace.prototype.family;

/**
 * @type {string}
 * @see http://dev.w3.org/csswg/css-font-loading/#dom-fontface-style
 */
FontFace.prototype.style;

/**
 * @type {string}
 * @see http://dev.w3.org/csswg/css-font-loading/#dom-fontface-weight
 */
FontFace.prototype.weight;

/**
 * @type {string}
 * @see http://dev.w3.org/csswg/css-font-loading/#dom-fontface-stretch
 */
FontFace.prototype.stretch;

/**
 * @type {string}
 * @see http://dev.w3.org/csswg/css-font-loading/#dom-fontface-unicoderange
 */
FontFace.prototype.unicodeRange;

/**
 * @type {string}
 * @see http://dev.w3.org/csswg/css-font-loading/#dom-fontface-variant
 */
FontFace.prototype.variant;

/**
 * @type {string}
 * @see http://dev.w3.org/csswg/css-font-loading/#dom-fontface-featuresettings
 */
FontFace.prototype.featureSettings;

/**
 * @type {string}
 * @see http://dev.w3.org/csswg/css-font-loading/#dom-fontface-variationsettings
 */
FontFace.prototype.variationSettings;

/**
 * @type {string}
 * @see http://dev.w3.org/csswg/css-font-loading/#dom-fontface-display
 */
FontFace.prototype.display;

/**
 * @type {string}
 * @see http://dev.w3.org/csswg/css-font-loading/#dom-fontface-ascentoverride
 */
FontFace.prototype.ascentOverride;

/**
 * @type {string}
 * @see http://dev.w3.org/csswg/css-font-loading/#dom-fontface-descentoverride
 */
FontFace.prototype.descentOverride;

/**
 * @type {string}
 * @see http://dev.w3.org/csswg/css-font-loading/#dom-fontface-linegapoverride
 */
FontFace.prototype.lineGapOverride;

/**
 * @type {!FontFaceLoadStatus}
 * @see http://dev.w3.org/csswg/css-font-loading/#dom-fontface-status
 */
FontFace.prototype.status;

/**
 * @return {!Promise<!FontFace>}
 * @see http://dev.w3.org/csswg/css-font-loading/#font-face-load
 */
FontFace.prototype.load = function() {};

/**
 * @type {!Promise<!FontFace>}
 * @see http://dev.w3.org/csswg/css-font-loading/#dom-fontface-loaded
 */
FontFace.prototype.loaded;

/**
 * @typedef {{
 *   fontfaces: (Array<!FontFace>|undefined)
 * }}
 * @see http://dev.w3.org/css-font-loading/#dictdef-fontfacesetloadeventinit
 */
var FontFaceSetLoadEventInit;

/**
 * @constructor
 * @param {string} type
 * @param {!FontFaceSetLoadEventInit=} eventInitDict
 * @extends {Event}
 * @see https://drafts.csswg.org/css-font-loading/#fontfacesetloadevent
 */
function FontFaceSetLoadEvent(type, eventInitDict) {}

/**
 * @type {!Array<!FontFace>}
 * @see http://dev.w3.org/css-font-loading/#dom-fontfacesetloadevent-fontfaces
 */
FontFaceSetLoadEvent.prototype.fontfaces;

/**
 * Set of possible string values: 'loaded', 'loading'.
 * @typedef {string}
 * @see http://dev.w3.org/csswg/css-font-loading/#enumdef-fontfacesetloadstatus
 */
var FontFaceSetLoadStatus;

/**
 * @interface
 * @extends {EventTarget}
 * @see http://dev.w3.org/csswg/css-font-loading/#FontFaceSet-interface
 */
function FontFaceSet() {}

// Event handlers
// http://dev.w3.org/csswg/css-font-loading/#FontFaceSet-events

/** @type {?function (Event)} */ FontFaceSet.prototype.onloading;
/** @type {?function (Event)} */ FontFaceSet.prototype.onloadingdone;
/** @type {?function (Event)} */ FontFaceSet.prototype.onloadingerror;

/**
 * @param {!FontFace} value
 * @see http://dev.w3.org/csswg/css-font-loading/#dom-fontfaceset-add
 * @return {undefined}
 */
FontFaceSet.prototype.add = function(value) {};

/**
 * @see http://dev.w3.org/csswg/css-font-loading/#dom-fontfaceset-clear
 * @return {undefined}
 */
FontFaceSet.prototype.clear = function() {};

/**
 * @param {!FontFace} value
 * @see http://dev.w3.org/csswg/css-font-loading/#dom-fontfaceset-delete
 * @return {undefined}
 */
FontFaceSet.prototype.delete = function(value) {};

/**
 * @param {!FontFace} font
 * @return {boolean}
 * @see http://dev.w3.org/csswg/css-font-loading/#dom-fontfaceset-has
 */
FontFaceSet.prototype.has = function(font) {};

/**
 * @param {function(!FontFace, number, !FontFaceSet)} callback
 * @param {?Object=} selfObj
 * see http://dev.w3.org/csswg/css-font-loading/#dom-fontfaceset-foreach
 * @return {undefined}
 */
FontFaceSet.prototype.forEach = function(callback, selfObj) {};

/**
 * @param {string} font
 * @param {string=} opt_text
 * @return {!Promise<!Array<!FontFace>>}
 * @see http://dev.w3.org/csswg/css-font-loading/#dom-fontfaceset-load
 */
FontFaceSet.prototype.load = function(font, opt_text) {};

/**
 * @param {string} font
 * @param {string=} opt_text
 * @return {boolean}
 * @see http://dev.w3.org/csswg/css-font-loading/#dom-fontfaceset-check
 */
FontFaceSet.prototype.check = function(font, opt_text) {};

/**
 * @type {!Promise<!FontFaceSet>}
 * @see http://dev.w3.org/csswg/css-font-loading/#dom-fontfaceset-ready
 */
FontFaceSet.prototype.ready;

/**
 * @type {FontFaceSetLoadStatus}
 * @see http://dev.w3.org/csswg/css-font-loading/#dom-fontfaceset-status
 */
FontFaceSet.prototype.status;

/**
 * @constructor
 * @param {string} type
 * @param {{
 *   animationName: (string|undefined),
 *   elapsedTime: (number|undefined),
 *   pseudoElement: (string|undefined)
 * }=} opt_animationEventInitDict
 * @extends {Event}
 * @see https://drafts.csswg.org/css-animations/#interface-animationevent
 */
function AnimationEvent(type, opt_animationEventInitDict) {};

/**
 * @type {string}
 * @see https://drafts.csswg.org/css-animations/#dom-animationevent-animationname
 */
AnimationEvent.prototype.animationName;

/**
 * @type {number}
 * @see https://drafts.csswg.org/css-animations/#dom-animationevent-elapsedtime
 */
AnimationEvent.prototype.elapsedTime;

/**
 * @type {string}
 * @see https://drafts.csswg.org/css-animations/#dom-animationevent-pseudoelement
 */
AnimationEvent.prototype.pseudoElement;

/**
 * @record
 * @extends {EventInit}
 * @see https://www.w3.org/TR/css-transitions-1/#dictdef-transitioneventinit
 */
function TransitionEventInit() {};

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-transitions-1/#dom-transitioneventinit-propertyname
 */
TransitionEventInit.prototype.propertyName;

/**
 * @type {number}
 * @see https://www.w3.org/TR/css-transitions-1/#dom-transitioneventinit-elapsedtime
 */
TransitionEventInit.prototype.elapsedTime;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-transitions-1/#dom-transitioneventinit-pseudoelement
 */
TransitionEventInit.prototype.pseudoElement;

/**
 * @constructor
 * @param {string} type
 * @param {!TransitionEventInit=} transitionEventInitDict
 * @extends {Event}
 * @see https://www.w3.org/TR/css-transitions-1/#interface-transitionevent
 */
function TransitionEvent(type, transitionEventInitDict) {};

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-transitions-1/#Events-TransitionEvent-propertyName
 */
TransitionEvent.prototype.propertyName;

/**
 * @type {number}
 * @see https://www.w3.org/TR/css-transitions-1/#Events-TransitionEvent-elapsedTime
 */
TransitionEvent.prototype.elapsedTime;

/**
 * @type {string}
 * @see https://www.w3.org/TR/css-transitions-1/#Events-TransitionEvent-pseudoElement
 */
TransitionEvent.prototype.pseudoElement;

/**
 * @record
 * @extends {EventInit}
 */
function ContentVisibilityAutoStateChangeEventInit() {}

/**
 * @type {boolean|undefined}
 */
ContentVisibilityAutoStateChangeEventInit.prototype.skipped;

/**
 * @constructor
 * @extends {Event}
 * @param {string} type
 * @param {!ContentVisibilityAutoStateChangeEventInit=} eventInitDict
 * @see https://developer.mozilla.org/docs/Web/API/ContentVisibilityAutoStateChangeEvent
 */
function ContentVisibilityAutoStateChangeEvent(type, eventInitDict) {}

/**
 * @type {boolean}
 */
ContentVisibilityAutoStateChangeEvent.prototype.skipped;

/**
 * @constructor
 * @extends {CSSRule}
 * @see http://dev.w3.org/csswg/css-animations/#csskeyframerule
 */
function CSSKeyframeRule() {}

/**
 * @type {string}
 * @see https://drafts.csswg.org/css-animations/#dom-csskeyframerule-keytext
 */
CSSKeyframeRule.prototype.keyText;

/**
 * @type {!CSSStyleDeclaration}
 * @see https://drafts.csswg.org/css-animations/#dom-csskeyframerule-style
 */
CSSKeyframeRule.prototype.style;


/**
 * @constructor
 * @extends {CSSRule}
 * @see http://dev.w3.org/csswg/css-animations/#csskeyframesrule
 */
function CSSKeyframesRule() {}

/**
 * @see https://drafts.csswg.org/css-animations/#dom-csskeyframesrule-name
 * @type {string}
 */
CSSKeyframesRule.prototype.name;

/**
 * @see https://drafts.csswg.org/css-animations/#dom-csskeyframesrule-cssrules
 * @type {!CSSRuleList}
 */
CSSKeyframesRule.prototype.cssRules;

/**
 * @see https://drafts.csswg.org/css-animations/#dom-csskeyframesrule-findrule
 * @param {string} key The key text for the rule to find.
 * @return {?CSSKeyframeRule}
 */
CSSKeyframesRule.prototype.findRule = function(key) {};

/**
 * @see https://drafts.csswg.org/css-animations/#dom-csskeyframesrule-appendrule
 * @param {string} rule The text for the rule to append.
 */
CSSKeyframesRule.prototype.appendRule = function(rule) {};

/**
 * @see https://drafts.csswg.org/css-animations/#dom-csskeyframesrule-deleterule
 * @param {string} key The key text for the rule to delete.
 */
CSSKeyframesRule.prototype.deleteRule = function(key) {};
