/**
 * @fileoverview The current draft spec of ResizeObserver.
 * @see https://wicg.github.io/ResizeObserver/
 * @externs
 */

/**
 * @typedef {function(!Array<!ResizeObserverEntry>, !ResizeObserver)}
 */
var ResizeObserverCallback;

/**
 * @constructor
 * @param {!ResizeObserverCallback} callback
 */
function ResizeObserver(callback) {}

/** @type {function(!Element)} */
ResizeObserver.prototype.observe;

/** @type {function(!Element)} */
ResizeObserver.prototype.unobserve;

/** @type {function()} */
ResizeObserver.prototype.disconnect;

/**
 * @constructor
 * @param {!Element} target
 */
function ResizeObserverEntry(target) {}

/** @const {!Element} */
ResizeObserverEntry.prototype.target;

/** @const {{
 *   x: number,
 *   y: number,
 *   width: number,
 *   height: number,
 * }}
 */
ResizeObserverEntry.prototype.contentRect;
