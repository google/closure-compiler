/*
 * Copyright 2017 The Closure Compiler Authors
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
 * @fileoverview Externs for web app manifest APIs.
 *
 * @see https://w3c.github.io/manifest/
 * @externs
 */

/**
 * @see https://w3c.github.io/manifest/#beforeinstallpromptevent-interface
 * @constructor
 * @extends {Event}
 */
function BeforeInstallPromptEvent() {}

/** @type {!Promise<{outcome: !AppBannerPromptOutcome}>} */
BeforeInstallPromptEvent.prototype.userChoice;

/** @return {!Promise<!PromptResponseObject>} */
BeforeInstallPromptEvent.prototype.prompt = function() {};

/** @typedef {string} */
var AppBannerPromptOutcome;

/** @typedef {{userChoice: !AppBannerPromptOutcome}} */
var PromptResponseObject;

/** @type {?function(!BeforeInstallPromptEvent)} */
Window.prototype.onbeforeinstallpromptevent;

/** @type {?function(!Event)} */
Window.prototype.onappinstalled;
