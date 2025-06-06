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
 * @fileoverview Definitions for HTML Navigation History APIs.
 * @see https://developer.mozilla.org/en-US/docs/Web/API/Navigation_API
 * @externs
 */

// NavigationHistoryEntry

/**
 * @constructor
 * @implements {EventTarget}
 * @see https://html.spec.whatwg.org/multipage/nav-history-apis.html#navigationhistoryentry
 */
function NavigationHistoryEntry() {}

/**
 * @type {string}
 * @see https://html.spec.whatwg.org/multipage/nav-history-apis.html#dom-navigationhistoryentry-id
 */
NavigationHistoryEntry.prototype.id;

/**
 * @type {number}
 * @see https://html.spec.whatwg.org/multipage/nav-history-apis.html#dom-navigationhistoryentry-index
 */
NavigationHistoryEntry.prototype.index;

/**
 * @type {string}
 * @see https://html.spec.whatwg.org/multipage/nav-history-apis.html#dom-navigationhistoryentry-key
 */
NavigationHistoryEntry.prototype.key;

/**
 * @type {string|null}
 * @see https://html.spec.whatwg.org/multipage/nav-history-apis.html#dom-navigationhistoryentry-url
 */
NavigationHistoryEntry.prototype.url;

/**
 * @type {boolean}
 * @see https://html.spec.whatwg.org/multipage/nav-history-apis.html#dom-navigationhistoryentry-samedocument
 */
NavigationHistoryEntry.prototype.sameDocument;

/**
 * @return {?}
 * @see https://html.spec.whatwg.org/multipage/nav-history-apis.html#dom-navigationhistoryentry-getstate
*/
NavigationHistoryEntry.prototype.getState = function() {};

/**
 * @type {function(!Event)|undefined|null}
 * @see https://html.spec.whatwg.org/multipage/nav-history-apis.html#dom-navigationhistoryentry-ondispose
 */
NavigationHistoryEntry.prototype.ondispose;


/**
 * @constructor
 * @see https://developer.mozilla.org/docs/Web/API/NavigationActivation
 */
function NavigationActivation() {}

/** @type {NavigationHistoryEntry} */
NavigationActivation.prototype.entry;

/** @type {NavigationHistoryEntry|null} */
NavigationActivation.prototype.from;

/** @type {string} */
NavigationActivation.prototype.navigationType;


/**
 * @typedef {{
 *   activation: (NavigationActivation | null),
 *   viewTransition: (ViewTransition | null),
 * }}
 */
var PageSwapEventInit;

/**
 * @constructor
 * @extends {Event}
 * @param {string} type
 * @param {PageSwapEventInit=} opt_eventInitDict
 * @see https://developer.mozilla.org/docs/Web/API/PageSwapEvent
 */
function PageSwapEvent(type, opt_eventInitDict) {}

/** @type {NavigationActivation | null} */
PageSwapEvent.prototype.activation;

/** @type {ViewTransition | null} */
PageSwapEvent.prototype.viewTransition;

/**
 * @record
 * @extends {EventInit}
 */
function PageRevealEventInit() {}

/** @type {?ViewTransition} */
PageRevealEventInit.prototype.viewTransition;

/**
 * @constructor
 * @extends {Event}
 * @param {string} type
 * @param {!PageRevealEventInit=} opt_eventInitDict
 * @see https://developer.mozilla.org/docs/Web/API/PageRevealEvent
 */
function PageRevealEvent(type, opt_eventInitDict) {}

/** @type {ViewTransition | null} */
PageRevealEvent.prototype.viewTransition;
