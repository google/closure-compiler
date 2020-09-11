/**
 * Screen Wake Lock API
 * W3C Editor's Draft 01 September 2020
 * @externs
 * @see https://w3c.github.io/screen-wake-lock/
 */


/** @type {!WakeLock} */
Navigator.prototype.wakeLock;


/**
 * @interface
 * @see https://w3c.github.io/screen-wake-lock/#the-wakelock-interface
 */
function WakeLock() {};

/**
 * @param {string} type
 * @return {!Promise<!WakeLockSentinel>}
 */
WakeLock.prototype.request = function(type) {};


/**
 * @interface
 * @extends {EventTarget}
 * @see https://w3c.github.io/screen-wake-lock/#the-wakelocksentinel-interface
 */
function WakeLockSentinel() {};

/** @type {?function(!Event)} */
WakeLockSentinel.prototype.onrelease;

/** @return {!Promise<void>} */
WakeLockSentinel.prototype.release = function() {};

/** @type {boolean} @const */
WakeLockSentinel.prototype.released;

/** @type {string} @const */
WakeLockSentinel.prototype.type;
