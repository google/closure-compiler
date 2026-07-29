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

/**
 * @typedef {string}
 * Valid values: 'after-transition', 'manual'.
 */
var NavigationFocusReset;

/**
 * @typedef {string}
 * Valid values: 'auto', 'push', 'replace'.
 */
var NavigationHistoryBehavior;

/**
 * @typedef {string}
 * Valid values: 'after-transition', 'manual'.
 */
var NavigationScrollBehavior;

/**
 * @typedef {string}
 * Valid values: 'push', 'reload', 'replace', 'traverse'.
 */
var NavigationType;

/** @typedef {function(): (void|!Promise<void>)} */
var NavigationInterceptHandler;

/**
 * @typedef {function(!NavigationPrecommitController): (void|!Promise<void>)}
 */
var NavigationPrecommitHandler;

/**
 * @record
 * @extends {EventInit}
 */
function NavigateEventInit() {}

/** @type {boolean|undefined} */
NavigateEventInit.prototype.canIntercept;

/** @type {!NavigationDestination} */
NavigateEventInit.prototype.destination;

/** @type {string|null|undefined} */
NavigateEventInit.prototype.downloadRequest;

/** @type {!FormData|null|undefined} */
NavigateEventInit.prototype.formData;

/** @type {boolean|undefined} */
NavigateEventInit.prototype.hasUAVisualTransition;

/** @type {boolean|undefined} */
NavigateEventInit.prototype.hashChange;

/** @type {*} */
NavigateEventInit.prototype.info;

/** @type {!NavigationType|undefined} */
NavigateEventInit.prototype.navigationType;

/** @type {!AbortSignal} */
NavigateEventInit.prototype.signal;

/** @type {!Element|null|undefined} */
NavigateEventInit.prototype.sourceElement;

/** @type {boolean|undefined} */
NavigateEventInit.prototype.userInitiated;


/**
 * @constructor
 * @extends {Event}
 * @param {string} type
 * @param {!NavigateEventInit} eventInitDict
 * @see https://developer.mozilla.org/docs/Web/API/NavigateEvent
 */
function NavigateEvent(type, eventInitDict) {}

/** @type {boolean} */
NavigateEvent.prototype.canIntercept;

/** @type {!NavigationDestination} */
NavigateEvent.prototype.destination;

/** @type {string|null} */
NavigateEvent.prototype.downloadRequest;

/** @type {!FormData|null} */
NavigateEvent.prototype.formData;

/** @type {boolean} */
NavigateEvent.prototype.hasUAVisualTransition;

/** @type {boolean} */
NavigateEvent.prototype.hashChange;

/** @type {*} */
NavigateEvent.prototype.info;

/** @type {!NavigationType} */
NavigateEvent.prototype.navigationType;

/** @type {!AbortSignal} */
NavigateEvent.prototype.signal;

/** @type {!Element|null} */
NavigateEvent.prototype.sourceElement;

/** @type {boolean} */
NavigateEvent.prototype.userInitiated;

/**
 * @param {!NavigationInterceptOptions=} options
 * @return {void}
 */
NavigateEvent.prototype.intercept = function(options) {};

/**
 * @return {void}
 */
NavigateEvent.prototype.scroll = function() {};


/**
 * @constructor
 * @implements {EventTarget}
 * @see https://developer.mozilla.org/docs/Web/API/Navigation
 */
function Navigation() {}

/** @type {!NavigationActivation|null} */
Navigation.prototype.activation;

/** @type {boolean} */
Navigation.prototype.canGoBack;

/** @type {boolean} */
Navigation.prototype.canGoForward;

/** @type {!NavigationHistoryEntry|null} */
Navigation.prototype.currentEntry;

/** @type {function(!NavigationCurrentEntryChangeEvent):?|null} */
Navigation.prototype.oncurrententrychange;

/** @type {function(!NavigateEvent):?|null} */
Navigation.prototype.onnavigate;

/** @type {function(!ErrorEvent):?|null} */
Navigation.prototype.onnavigateerror;

/** @type {function(!Event):?|null} */
Navigation.prototype.onnavigatesuccess;

/** @type {!NavigationTransition|null} */
Navigation.prototype.transition;

/**
 * @param {!NavigationOptions=} options
 * @return {!NavigationResult}
 */
Navigation.prototype.back = function(options) {};

/**
 * @return {!Array<!NavigationHistoryEntry>}
 */
Navigation.prototype.entries = function() {};

/**
 * @param {!NavigationOptions=} options
 * @return {!NavigationResult}
 */
Navigation.prototype.forward = function(options) {};

/**
 * @param {string|!URL} url
 * @param {!NavigationNavigateOptions=} options
 * @return {!NavigationResult}
 */
Navigation.prototype.navigate = function(url, options) {};

/**
 * @param {!NavigationReloadOptions=} options
 * @return {!NavigationResult}
 */
Navigation.prototype.reload = function(options) {};

/**
 * @param {string} key
 * @param {!NavigationOptions=} options
 * @return {!NavigationResult}
 */
Navigation.prototype.traverseTo = function(key, options) {};

/**
 * @param {!NavigationUpdateCurrentEntryOptions} options
 * @return {void}
 */
Navigation.prototype.updateCurrentEntry = function(options) {};


/**
 * @constructor
 * @see https://developer.mozilla.org/docs/Web/API/NavigationActivation
 */
function NavigationActivation() {}

/** @type {!NavigationHistoryEntry} */
NavigationActivation.prototype.entry;

/** @type {!NavigationHistoryEntry|null} */
NavigationActivation.prototype.from;

/** @type {!NavigationType} */
NavigationActivation.prototype.navigationType;


/**
 * @constructor
 * @extends {Event}
 * @param {string} type
 * @param {!NavigationCurrentEntryChangeEventInit} eventInitDict
 * @see https://developer.mozilla.org/docs/Web/API/NavigationCurrentEntryChangeEvent
 */
function NavigationCurrentEntryChangeEvent(type, eventInitDict) {}

/** @type {!NavigationHistoryEntry} */
NavigationCurrentEntryChangeEvent.prototype.from;

/** @type {!NavigationType|null} */
NavigationCurrentEntryChangeEvent.prototype.navigationType;


/**
 * @record
 * @extends {EventInit}
 */
function NavigationCurrentEntryChangeEventInit() {}

/** @type {!NavigationHistoryEntry} */
NavigationCurrentEntryChangeEventInit.prototype.from;

/** @type {!NavigationType|null|undefined} */
NavigationCurrentEntryChangeEventInit.prototype.navigationType;


/**
 * @constructor
 * @see https://developer.mozilla.org/docs/Web/API/NavigationDestination
 */
function NavigationDestination() {}

/** @type {string} */
NavigationDestination.prototype.id;

/** @type {number} */
NavigationDestination.prototype.index;

/** @type {string} */
NavigationDestination.prototype.key;

/** @type {boolean} */
NavigationDestination.prototype.sameDocument;

/** @type {string} */
NavigationDestination.prototype.url;

/**
 * @return {*}
 */
NavigationDestination.prototype.getState = function() {};


/**
 * @constructor
 * @implements {EventTarget}
 * @see https://html.spec.whatwg.org/multipage/nav-history-apis.html#navigationhistoryentry
 */
function NavigationHistoryEntry() {}

/** @type {string} */
NavigationHistoryEntry.prototype.id;

/** @type {number} */
NavigationHistoryEntry.prototype.index;

/** @type {string} */
NavigationHistoryEntry.prototype.key;

/** @type {string|null} */
NavigationHistoryEntry.prototype.url;

/** @type {boolean} */
NavigationHistoryEntry.prototype.sameDocument;

/**
 * @return {*}
 */
NavigationHistoryEntry.prototype.getState = function() {};

/** @type {function(!Event)|undefined|null} */
NavigationHistoryEntry.prototype.ondispose;


/**
 * @constructor
 * @see https://developer.mozilla.org/docs/Web/API/NavigationPrecommitController
 */
function NavigationPrecommitController() {}

/**
 * @param {!NavigationInterceptHandler} handler
 * @return {void}
 */
NavigationPrecommitController.prototype.addHandler = function(handler) {};

/**
 * @param {string|!URL} url
 * @param {!NavigationNavigateOptions=} options
 * @return {void}
 */
NavigationPrecommitController.prototype.redirect = function(url, options) {};


/**
 * @constructor
 * @see https://developer.mozilla.org/docs/Web/API/NavigationTransition
 */
function NavigationTransition() {}

/** @type {!Promise<void>} */
NavigationTransition.prototype.committed;

/** @type {!Promise<void>} */
NavigationTransition.prototype.finished;

/** @type {!NavigationHistoryEntry} */
NavigationTransition.prototype.from;

/** @type {!NavigationType} */
NavigationTransition.prototype.navigationType;


/**
 * @record
 */
function NavigationInterceptOptions() {}

/** @type {!NavigationFocusReset|undefined} */
NavigationInterceptOptions.prototype.focusReset;

/** @type {!NavigationInterceptHandler|undefined} */
NavigationInterceptOptions.prototype.handler;

/** @type {!NavigationPrecommitHandler|undefined} */
NavigationInterceptOptions.prototype.precommitHandler;

/** @type {!NavigationScrollBehavior|undefined} */
NavigationInterceptOptions.prototype.scroll;


/**
 * @record
 * @extends {NavigationOptions}
 */
function NavigationNavigateOptions() {}

/** @type {!NavigationHistoryBehavior|undefined} */
NavigationNavigateOptions.prototype.history;

/** @type {*} */
NavigationNavigateOptions.prototype.state;


/**
 * @record
 */
function NavigationOptions() {}

/** @type {*} */
NavigationOptions.prototype.info;


/**
 * @record
 * @extends {NavigationOptions}
 */
function NavigationReloadOptions() {}

/** @type {*} */
NavigationReloadOptions.prototype.state;


/**
 * @record
 */
function NavigationResult() {}

/** @type {!Promise<!NavigationHistoryEntry>|undefined} */
NavigationResult.prototype.committed;

/** @type {!Promise<!NavigationHistoryEntry>|undefined} */
NavigationResult.prototype.finished;


/**
 * @record
 */
function NavigationUpdateCurrentEntryOptions() {}

/** @type {*} */
NavigationUpdateCurrentEntryOptions.prototype.state;


/** @type {!Navigation} */
Window.prototype.navigation;

/** @type {!Navigation} */
var navigation;


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
