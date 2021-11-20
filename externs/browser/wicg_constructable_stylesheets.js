/*
 * Copyright 2021 The Closure Compiler Authors
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
 * @fileoverview The current draft spec of constructable stylesheets.
 * @see https://wicg.github.io/construct-stylesheets/
 * @see https://developer.mozilla.org/en-US/docs/Web/API/CSSStyleSheet/CSSStyleSheet
 * @externs
 */

// https://wicg.github.io/construct-stylesheets/#dictdef-cssstylesheetinit
/** @type {(undefined|MediaList|string)} */
CSSStyleSheetInit.prototype.media;

/** @type {(undefined|string)} */
CSSStyleSheetInit.prototype.title;

/** @type {(undefined|boolean)} */
CSSStyleSheetInit.prototype.alternate;

/** @type {(undefined|boolean)} */
CSSStyleSheetInit.prototype.disabled;

/**
 * @param {string} text
 * @return {!Promise<!CSSStyleSheet>}
 * @see https://wicg.github.io/construct-stylesheets/#dom-cssstylesheet-replace
 */
CSSStyleSheet.prototype.replace = function(text) {};

/**
 * @param {string} text
 * @return {void}
 * @see https://wicg.github.io/construct-stylesheets/#dom-cssstylesheet-replacesync
 */
CSSStyleSheet.prototype.replaceSync = function(text) {};

/**
 * @type {!Array<!CSSStyleSheet>}
 * @see https://wicg.github.io/construct-stylesheets/#dom-documentorshadowroot-adoptedstylesheets
 */
Document.prototype.adoptedStyleSheets;

/**
 * @type {!Array<!CSSStyleSheet>}
 * @see https://wicg.github.io/construct-stylesheets/#dom-documentorshadowroot-adoptedstylesheets
 */
ShadowRoot.prototype.adoptedStyleSheets;
