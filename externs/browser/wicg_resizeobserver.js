/**
 * @fileoverview The current draft spec of ResizeObserver.
 * @see https://wicg.github.io/ResizeObserver/
 * @see https://developer.mozilla.org/en-US/docs/Web/API/ResizeObserver
 * @externs
 */

/**
 * @typedef {function(!Array<!ResizeObserverEntry>, !ResizeObserver)}
 */
var ResizeObserverCallback;

/**
 * @typedef {{box: string}}
 */
var ResizeObserverOptions;

/**
 * @constructor
 * @param {!ResizeObserverCallback} callback
 * @see https://developer.mozilla.org/en-US/docs/Web/API/ResizeObserver/ResizeObserver
 */
function ResizeObserver(callback) {}

/**
 * @param {!Element} target
 * @param {!ResizeObserverOptions=} opt_options
 * @return {void}
 * @see https://developer.mozilla.org/en-US/docs/Web/API/ResizeObserver/observe
 */
ResizeObserver.prototype.observe = function(target, opt_options) {};

/**
 * @param {!Element} target
 * @return {void}
 * @see https://developer.mozilla.org/en-US/docs/Web/API/ResizeObserver/unobserve
 */
ResizeObserver.prototype.unobserve = function(target) {};

/**
 * @return {void}
 * @see https://developer.mozilla.org/en-US/docs/Web/API/ResizeObserver/disconnect
 */
ResizeObserver.prototype.disconnect = function() {};

/**
 * @interface
 * @see https://developer.mozilla.org/en-US/docs/Web/API/ResizeObserverEntry
 */
function ResizeObserverEntry() {}

/**
 * @const {!Element}
 * @see https://developer.mozilla.org/en-US/docs/Web/API/ResizeObserverEntry/target
 */
ResizeObserverEntry.prototype.target;

/**
 * @const {!DOMRectReadOnly}
 * @see https://developer.mozilla.org/en-US/docs/Web/API/ResizeObserverEntry/contentRect
 */
ResizeObserverEntry.prototype.contentRect;
