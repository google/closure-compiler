/*
 * Copyright 2022 The Closure Compiler Authors
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
 * @fileoverview The current draft spec of Ink API.
 * @see https://wicg.github.io/ink-enhancement/
 * @externs
 */

/**
 * @see https://wicg.github.io/ink-enhancement/#ink-presenter-param
 * @record
 * @struct
 */
function InkPresenterParam() {}

/** @type {?Element} */
InkPresenterParam.prototype.presentationArea;

/**
 * The Ink API provides the InkPresenter interface to expose the underlying
 * operating system API to achieve this and keeps the extensibility open in
 * order to support additional presenters in the future.
 * @see https://wicg.github.io/ink-enhancement/#ink-interface
 * @interface
 * @struct
 */
function Ink() {}

/**
 * @param {?InkPresenterParam=} param
 * @return {!Promise<!InkPresenter>}
 */
Ink.prototype.requestPresenter = function(param) {};


/**
 * @see https://wicg.github.io/ink-enhancement/#ink-presenter
 * @interface
 * @struct
 */
function InkPresenter() {}

/** @type {?Element} */
InkPresenter.prototype.presentationArea;

/** @type {number} */
InkPresenter.prototype.expectedImprovement;

/**
 * @param {!PointerEvent} event
 * @param {!InkTrailStyle} style
 * @return {void}
 */
InkPresenter.prototype.updateInkTrailStartPoint = function(event, style) {};


/**
 * @see https://wicg.github.io/ink-enhancement/#ink-trail-style
 * @record
 * @struct
 */
function InkTrailStyle() {}

/** @type {string} */
InkTrailStyle.prototype.color;

/** @type {number} */
InkTrailStyle.prototype.diameter;


/**
 * @see https://wicg.github.io/ink-enhancement/#navigator-interface-extensions
 * @type {!Ink}
 */
Navigator.prototype.ink;