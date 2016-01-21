
/**
 * Copyright 2016 ZapGroup LTD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * @fileoverview Externs for the Google Recaptcha v2 Public API (api.js).
 * @see https://developers.google.com/recaptcha/docs/display
 * @externs
 */

/**
 * @constructor
 */

function grecaptcha() { }


/**
 * @param {(string|Element)} container
 * @param {Array.<string>=} parameters
 * @return {number}
 */
grecaptcha.prototype.render = function (container, parameters) { };

/**
 * @param {number=} opt_widget_id
 */
grecaptcha.prototype.reset = function (opt_widget_id) { };

/**
 * @param {number} opt_widget_id
 */
grecaptcha.prototype.getResponse = function (opt_widget_id) { };
