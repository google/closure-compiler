/*
 * Copyright 2013 The Closure Compiler Authors
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
 * @fileoverview Definitions for the JS Internationalization API as defined in
 * http://www.ecma-international.org/ecma-402/1.0/
 *
 * @externs
 */

/** @const */
var Intl = {};

/**
 * @record
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/Segmenter/Segmenter#parameters
 */
Intl.SegmenterOptions = function() {};

/**
 * @type {string|undefined}
 */
Intl.SegmenterOptions.prototype.localeMatcher;

/**
 * @type {string|undefined}
 */
Intl.SegmenterOptions.prototype.granularity;

/**
 * @record
 */
Intl.ResolvedSegmenterOptions = function() {};

/**
 * @type {string}
 */
Intl.ResolvedSegmenterOptions.prototype.locale;

/**
 * @type {string}
 */
Intl.ResolvedSegmenterOptions.prototype.granularity;

/**
 * @record
 * @template T
 * @extends {IteratorIterable<T>}
 */
Intl.SegmentIterator = function() {};

/**
 * @return {!Intl.SegmentIterator<T>}
 * @override
 */
Intl.SegmentIterator.prototype[Symbol.iterator] = function() {};

/**
 * @record
 */
Intl.Segments = function() {};

/**
 * @param {number=} codeUnitIndex
 * @return {!Intl.SegmentData}
 */
Intl.Segments.prototype.containing = function(codeUnitIndex) {};

/**
 * @return {!Intl.SegmentIterator<!Intl.SegmentData>}
 */
Intl.Segments.prototype[Symbol.iterator] = function() {};

/**
 * @record
 */
Intl.SegmentData = function() {};

/**
 * @type {string}
 */
Intl.SegmentData.prototype.segment;

/**
 * @type {number}
 */
Intl.SegmentData.prototype.index;

/**
 * @type {string}
 */
Intl.SegmentData.prototype.input;

/**
 * @type {boolean|undefined}
 */
Intl.SegmentData.prototype.isWordLike;

/**
 * @constructor
 * @param {string|!Array<string>=} locales
 * @param {!Intl.SegmenterOptions=} options
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/Segmenter.
 */
Intl.Segmenter = function(locales, options) {};

/**
 * @param {string} input
 * @return {!Intl.Segments}
 */
Intl.Segmenter.prototype.segment = function(input) {};

/**
 * @return {!Intl.ResolvedSegmenterOptions}
 */
Intl.Segmenter.prototype.resolvedOptions = function() {};

/**
 * @param {string|!Array<string>} locales
 * @param {!Intl.SegmenterOptions=} options
 * @return {Array<string>}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/Segmenter/supportedLocalesOf
 */
Intl.Segmenter.supportedLocalesOf = function(locales, options) {};

/**
 * @param {string} key
 * @return {Array<string>}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/supportedValuesOf
 */
Intl.supportedValuesOf = function(key) {};

/**
 * NOTE: this API is not from ecma402 and is subject to change.
 * @param {string|Array<string>=} locales
 * @param {{type: (string|undefined)}=}
 *         options
 * @constructor
 */
Intl.v8BreakIterator = function(locales, options) {};

/**
 * @param {string} text
 * @return {undefined}
 */
Intl.v8BreakIterator.prototype.adoptText = function(text) {};

/**
 * @return {string}
 */
Intl.v8BreakIterator.prototype.breakType = function() {};

/**
 * @return {number}
 */
Intl.v8BreakIterator.prototype.current = function() {};

/**
 * @return {number}
 */
Intl.v8BreakIterator.prototype.first = function() {};

/**
 * @return {number}
 */
Intl.v8BreakIterator.prototype.next = function() {};

/**
 * @constructor
 * @param {string|Array<string>=} locales
 * @param {{usage: (string|undefined), localeMatcher: (string|undefined),
 *     sensitivity: (string|undefined), ignorePunctuation: (boolean|undefined),
 *     numeric: (boolean|undefined), caseFirst: (string|undefined)}=}
 *         options
 */
Intl.Collator = function(locales, options) {};

/**
 * @param {Array<string>} locales
 * @param {{localeMatcher: (string|undefined)}=} options
 * @return {Array<string>}
 */
Intl.Collator.supportedLocalesOf = function(locales, options) {};

/**
 * @param {string} arg1
 * @param {string} arg2
 * @return {number}
 */
Intl.Collator.prototype.compare = function(arg1, arg2) {};

/**
 * @return {{locale: string, usage: string, sensitivity: string,
 *     ignorePunctuation: boolean, collation: string, numeric: boolean,
 *     caseFirst: string}}
 */
Intl.Collator.prototype.resolvedOptions = function() {};

/**
 * @constructor
 * @param {string|Array<string>=} locales
 * @param {{
 *     notation: (string|undefined),
 *     localeMatcher: (string|undefined), useGrouping: (boolean|undefined),
 *     numberingSystem: (string|undefined), style: (string|undefined),
 *     currency: (string|undefined), currencyDisplay: (string|undefined),
 *     minimumIntegerDigits: (number|undefined),
 *     minimumFractionDigits: (number|undefined),
 *     maximumFractionDigits: (number|undefined),
 *     minimumSignificantDigits: (number|undefined),
 *     maximumSignificantDigits: (number|undefined),
 *     compactDisplay: (string|undefined), currencySign: (string|undefined),
 *     signDisplay: (string|undefined), unit: (string|undefined),
 *     unitDisplay: (string|undefined), roundingIncrement: (number|undefined),
 *     roundingMode: (string|undefined), roundingPriority: (string|undefined),
 *     trailingZeroDisplay: (string|undefined)
 *     }=}
 *         options
 */
Intl.NumberFormat = function(locales, options) {};

/**
 * @param {Array<string>} locales
 * @param {{localeMatcher: (string|undefined)}=} options
 * @return {Array<string>}
 */
Intl.NumberFormat.supportedLocalesOf = function(locales, options) {};

/**
 * @param {number} num
 * @return {string}
 */
Intl.NumberFormat.prototype.format = function(num) {};

/**
 * @param {number} num
 * @return {!Array<{type: string, value: string}>}
 * @see http://www.ecma-international.org/ecma-402/#sec-intl.numberformat.prototype.formattoparts
 */
Intl.NumberFormat.prototype.formatToParts = function(num) {};

/**
 * @return {{locale: string, numberingSystem: string, style: string,
 *     currency: (string|undefined), currencyDisplay: (string|undefined),
 *     minimumIntegerDigits: number, minimumFractionDigits: number,
 *     maximumFractionDigits: number, minimumSignificantDigits: number,
 *     maximumSignificantDigits: number, useGrouping: boolean,
 *     compactDisplay: (string|undefined), currencySign: (string|undefined),
 *     signDisplay: (string|undefined), unit: (string|undefined),
 *     unitDisplay: (string|undefined), roundingIncrement: number,
 *     roundingMode: string, roundingPriority: string,
 *     trailingZeroDisplay: string
 * }}
 */
Intl.NumberFormat.prototype.resolvedOptions = function() {};

/**
 * @constructor
 * @param {string|Array<string>=} locales
 * @param {{localeMatcher: (string|undefined),
 *    formatMatcher: (string|undefined), calendar: (string|undefined),
 *    numberingSystem: (string|undefined), tz: (string|undefined),
 *    weekday: (string|undefined), era: (string|undefined),
 *    year: (string|undefined), month: (string|undefined),
 *    day: (string|undefined), hour: (string|undefined),
 *    minute: (string|undefined), second: (string|undefined),
 *    timeZoneName: (string|undefined), hour12: (boolean|undefined),
 *    dateStyle: (string|undefined), timeStyle: (string|undefined),
 *    timeZone: (string|undefined), dayPeriod: (string|undefined),
 *    hourCycle: (string|undefined),
 *    fractionalSecondDigits: (number|undefined)}=}
 *        options
 */
Intl.DateTimeFormat = function(locales, options) {};

/**
 * @param {Array<string>} locales
 * @param {{localeMatcher: string}=} options
 * @return {Array<string>}
 */
Intl.DateTimeFormat.supportedLocalesOf = function(locales, options) {};

/**
 * @param {(!Date|number)=} date
 * @return {string}
 */
Intl.DateTimeFormat.prototype.format = function(date) {};

/**
 * @param {(!Date|number)=} date
 * @return {Array<{type: string, value: string}>}
 */
Intl.DateTimeFormat.prototype.formatToParts = function(date) {};

/**
 * @param {(!Date|number)=} date1
 * @param {(!Date|number)=} date2
 * @return {string}
 */
Intl.DateTimeFormat.prototype.formatRange = function(date1, date2) {};

/**
 * @param {(!Date|number)=} date1
 * @param {(!Date|number)=} date2
 * @return {!Array<{type: string, value: string}>}
 */
Intl.DateTimeFormat.prototype.formatRangeToParts = function(date1, date2) {};

/**
 * @return {{locale: string, calendar: string, numberingSystem: string,
 *    timeZone: (string|undefined), weekday: (string|undefined),
 *    era: (string|undefined), year: (string|undefined),
 *    month: (string|undefined), day: (string|undefined),
 *    hour: (string|undefined), minute: (string|undefined),
 *    second: (string|undefined), timeZoneName: (string|undefined),
 *    hour12: (boolean|undefined),
 *    dateStyle: (string|undefined), timeStyle: (string|undefined),
 *    dayPeriod: (string|undefined), hourCycle: (string|undefined),
 *    fractionalSecondDigits: (number|undefined)}}
 */
Intl.DateTimeFormat.prototype.resolvedOptions = function() {};

/**
 * @constructor
 * @param {string|!Array<string>=} locales
 * @param {{localeMatcher: (string|undefined),
 *    style: (string|undefined), type: string,
 *    languageDisplay: (string|undefined), fallback: (string|undefined)}=}
 *        options
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/DisplayNames
 */
Intl.DisplayNames = function(locales, options) {};

/**
 * @param {!Array<string>} locales
 * @param {{localeMatcher: string}=} options
 * @return {!Array<string>}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/DisplayNames/supportedLocalesOf
 */
Intl.DisplayNames.supportedLocalesOf = function(locales, options) {};

/**
 * @param {string=} code
 * @return {(string|undefined)}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/DisplayNames/of
 */
Intl.DisplayNames.prototype.of = function(code) {};

/**
 * @return {{locale: string, style: string, type: string,
 *    fallback: string, languageDisplay: (string|undefined)}}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/DisplayNames/resolvedOptions
 */
Intl.DisplayNames.prototype.resolvedOptions = function() {};

/**
 * @constructor
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/PluralRules#Syntax
 * @see https://tc39.es/ecma402/#sec-intl.pluralrules
 * @param {string|Array<string>=} locales
 * @param {{
 *   type: (string|undefined),
 *   localeMatcher: (string|undefined),
 *   notation: (string|undefined),
 *   minimumIntegerDigits: (number|undefined),
 *   minimumFractionDigits: (number|undefined),
 *   maximumFractionDigits: (number|undefined),
 *   minimumSignificantDigits: (number|undefined),
 *   maximumSignificantDigits: (number|undefined),
 *   roundingMode: (string|undefined),
 *   roundingIncrement: (number|undefined),
 *   roundingPriority: (string|undefined),
 *   trailingZeroDisplay: (string|undefined),
 * }=} options
 */
Intl.PluralRules = function(locales, options) {};

/**
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/PluralRules/supportedLocalesOf#Syntax
 * @param {Array<string>} locales
 * @param {{localeMatcher: string}=} options
 * @return {Array<string>}
 */
Intl.PluralRules.supportedLocalesOf = function(locales, options) {};

/**
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/PluralRules/resolvedOptions#Syntax
 * @return {{locale: string, pluralCategories: Array<string>, type: string,
 *            minimumIntegerDigits: number, minimumFractionDigits: number,
 *            maximumFractionDigits: number, minimumSignificantDigits: number,
 *            maximumSignificantDigits: number, roundingMode: string}}
 */
Intl.PluralRules.prototype.resolvedOptions = function() {};

/**
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/PluralRules/select#Syntax
 * @param {number} number
 * @return {string}
 */
Intl.PluralRules.prototype.select = function(number) {};

/**
 * @constructor
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RelativeTimeFormat#Syntax
 * @param {string|Array<string>=} locales
 * @param {{localeMatcher: (string|undefined),
 *    numeric: (string|undefined),
 *    style: (string|undefined)}=}
 *        options
 */
Intl.RelativeTimeFormat = function(locales, options) {};

/**
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RelativeTimeFormat/supportedLocalesOf#Syntax
 * @param {Array<string>} locales
 * @param {{localeMatcher: string}=} options
 * @return {Array<string>}
 */
Intl.RelativeTimeFormat.supportedLocalesOf = function(locales, options) {};

/**
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RelativeTimeFormat/format#Syntax
 * @param {number} value
 * @param {string} unit
 * @return {string}
 */
Intl.RelativeTimeFormat.prototype.format = function(value, unit) {};

/**
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RelativeTimeFormat/formatToParts#Syntax
 * @param {number} value
 * @param {string} unit
 * @return {Array<string>}
 */
Intl.RelativeTimeFormat.prototype.formatToParts = function(value, unit) {};

/**
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RelativeTimeFormat/resolvedOptions#Syntax
 * @return {{locale: string, pluralCategories: Array<string>, type: string}}
 */
Intl.RelativeTimeFormat.prototype.resolvedOptions = function() {};

/**
 * @constructor
 * @param {string|Array<string>=} locales
 * @param {{
 *     localeMatcher: (string|undefined),
 *     type: (string|undefined),
 *     style: (string|undefined)
 *     }=} options
 */
Intl.ListFormat = function(locales, options) {};

/**
 * @param {Array<string>} locales
 * @param {{localeMatcher: (string|undefined)}=} options
 * @return {Array<string>}
 */
Intl.ListFormat.supportedLocalesOf = function(locales, options) {};

/**
 * @param {Array<string|number>} items
 * @return {string}
 */
Intl.ListFormat.prototype.format = function(items) {};

/**
 * @param {Array<string|number>} items
 * @return {!Array<{type: string, value: string}>}
 * @see http://www.ecma-international.org/ecma-402/#sec-intl.listformat.prototype.formattoparts
 */
Intl.ListFormat.prototype.formatToParts = function(items) {};

/**
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/ListFormat/resolvedOptions#Syntax
 * @return {{locale: string, style: string, type: string}}
 */
Intl.ListFormat.prototype.resolvedOptions = function() {};

/**
 * A string that is a valid [Unicode BCP 47 Locale
 * Identifier](https://unicode.org/reports/tr35/#Unicode_locale_identifier).
 *
 * For example: "fa", "es-MX", "zh-Hant-TW".
 *
 * @typedef {string}
 * @see https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Intl#locales_argument.
 */
Intl.UnicodeBCP47LocaleIdentifier;

/**
 * Set of possible values: "h12", "h23", "h11", "h24".
 * @typedef {string}
 */
Intl.LocaleHourCycleKey;

/**
 * Set of possible values: "upper", "lower", "false".
 * @typedef {string}
 */
Intl.LocaleCollationCaseFirst;

/**
 * @record
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/Locale/Locale#parameters
 */
Intl.LocaleOptions = function() {};

/** @type {string|undefined} */
Intl.LocaleOptions.prototype.baseName;

/** @type {string|undefined} */
Intl.LocaleOptions.prototype.calendar;

/** @type {!Intl.LocaleCollationCaseFirst|undefined} */
Intl.LocaleOptions.prototype.caseFirst;

/** @type {string|undefined} */
Intl.LocaleOptions.prototype.collation;

/** @type {!Intl.LocaleHourCycleKey|undefined} */
Intl.LocaleOptions.prototype.hourCycle;

/** @type {string|undefined} */
Intl.LocaleOptions.prototype.language;

/** @type {string|undefined} */
Intl.LocaleOptions.prototype.numberingSystem;

/** @type {boolean|undefined} */
Intl.LocaleOptions.prototype.numeric;

/** @type {string|undefined} */
Intl.LocaleOptions.prototype.region;

/** @type {string|undefined} */
Intl.LocaleOptions.prototype.script;

/**
 * @constructor
 * @implements {Intl.LocaleOptions}
 * @param {!Intl.UnicodeBCP47LocaleIdentifier} tag
 * @param {!Intl.LocaleOptions|undefined} options
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/Locale/Locale
 */
Intl.Locale = function(tag, options) {};

/** @type {string} */
Intl.Locale.prototype.baseName;

/** @type {string|undefined} */
Intl.Locale.prototype.calendar;

/** @type {!Intl.LocaleCollationCaseFirst|undefined} */
Intl.Locale.prototype.caseFirst;

/** @type {string|undefined} */
Intl.Locale.prototype.collation;

/** @type {!Intl.LocaleHourCycleKey|undefined} */
Intl.Locale.prototype.hourCycle;

/** @type {string} */
Intl.Locale.prototype.language;

/** @type {string|undefined} */
Intl.Locale.prototype.numberingSystem;

/** @type {boolean|undefined} */
Intl.Locale.prototype.numeric;

/** @type {string|undefined} */
Intl.Locale.prototype.region;

/** @type {string|undefined} */
Intl.Locale.prototype.script;

/**
 * @return {!Intl.Locale}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/Locale/maximize
 */
Intl.Locale.prototype.maximize = function() {};

/**
 * @return {!Intl.Locale}
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/Locale/minimize
 */
Intl.Locale.prototype.minimize = function() {};

/**
 * @override
 * @return {!Intl.UnicodeBCP47LocaleIdentifier}
 */
Intl.Locale.prototype.toString = function() {};

/**
 * @param {(string|!Array<string>)=} locale
 * @return {!Array<string>}
 * @see https://developer.mozilla.org/docs/Web/JavaScript/Reference/Global_Objects/Intl/getCanonicalLocales
 */
Intl.getCanonicalLocales = function(locale) {};
