/*
 * Copyright 2014 The Closure Compiler Authors.
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
 * @fileoverview Externs for ui-bootstrap.
 *
 * API Reference: http://angular-ui.github.io/bootstrap/
 *
 * @externs
 */


/**
 * @type {Object}
 */
var ui = {};


/**
 * @type {Object}
 * @const
 */
ui.bootstrap = {};

/******************************************************************************
 * $transition Service
 *****************************************************************************/


/**
 * @typedef {function(!angular.JQLite, !(string|Object|Function),
 *     Object=):!angular.$q.Promise
 *   }
 */
ui.bootstrap.$transition;


/**
 * Augment the ui.bootstrap.$transition type definition by reopening the type
 * via an artificial ui.bootstrap.$transition instance.
 *
 * This allows us to define methods on function objects which is something
 * that can't be expressed via typical type annotations.
 *
 * @type {ui.bootstrap.$transition}
 */
ui.bootstrap.$transition_;


/**
 * @type {(string)}
 */
ui.bootstrap.$transition_.animationEndEventName;


/**
 * @type {(string)}
 */
ui.bootstrap.$transition_.transitionEndEventName;

/******************************************************************************
 * $modal service
 *****************************************************************************/


/**
 * @typedef {{
 *   close: function(*=),
 *   dismiss: function(*=),
 *   opened: !angular.$q.Promise,
 *   result: !angular.$q.Promise
 * }}
 */
ui.bootstrap.modalInstance;


/** @type {function(*=)} */
ui.bootstrap.modalInstance.close;


/** @type {function(*=)} */
ui.bootstrap.modalInstance.dismiss;


/** @type {!angular.$q.Promise} */
ui.bootstrap.modalInstance.opened;


/** @type {!angular.$q.Promise} */
ui.bootstrap.modalInstance.result;


/**
 * @typedef {{
 *   backdrop: (boolean|string|undefined),
 *   backdropClass: (string|undefined),
 *   controller: (Function|Array.<string|Function>|string|undefined),
 *   controllerAs: (string|undefined),
 *   keyboard: (boolean|undefined),
 *   resolve: (Object.<string, (
 *       string|Function|Array.<string|Function>|!angular.$q.Promise
 *       )>|undefined),
 *   scope: (!angular.Scope|undefined),
 *   size: (string|undefined),
 *   template: (string|undefined),
 *   templateUrl: (string|undefined),
 *   windowClass: (string|undefined),
 *   windowTemplateUrl: (string|undefined)
 * }}
 */
ui.bootstrap.modalOptions;


/** @type {(boolean|string)} */
ui.bootstrap.modalOptions.backdrop;


/** @type {string} */
ui.bootstrap.modalOptions.backdropClass;


/** @type {(Function|Array.<string|Function>|string)} */
ui.bootstrap.modalOptions.controller;


/** @type {string} */
ui.bootstrap.modalOptions.controllerAs;


/** @type {boolean} */
ui.bootstrap.modalOptions.keyboard;


/**
 * @type {Object.<string, (string|Function|Array.<string|Function>|
 *   !angular.$q.Promise)>}
 */
ui.bootstrap.modalOptions.resolve;


/** @type {!angular.Scope} */
ui.bootstrap.modalOptions.scope;


/** @type {string} */
ui.bootstrap.modalOptions.size;


/** @type {string} */
ui.bootstrap.modalOptions.template;


/** @type {string} */
ui.bootstrap.modalOptions.templateUrl;


/** @type {string} */
ui.bootstrap.modalOptions.windowClass;


/** @type {string} */
ui.bootstrap.modalOptions.windowTemplateUrl;


/**
 * @typedef {{
 *   open: function(Object=):!ui.bootstrap.modalInstance
 * }}
 */
ui.bootstrap.$modal;

/******************************************************************************
 * Tooltip
 *****************************************************************************/


/**
 * @typedef {{
 *   compile: (function(!angular.JQLite=, !angular.Attributes=,
 *       Function=)|undefined),
 *   restrict: (string|undefined)
 *   }}
 */
ui.bootstrap.Tooltip;


/** @type {function(!angular.JQLite=, !angular.Attributes=, Function=)} */
ui.bootstrap.Tooltip.compile;


/** @type {string} */
ui.bootstrap.Tooltip.restrict;

/******************************************************************************
 * $tooltip service
 *****************************************************************************/


/**
 * @typedef {function(string, string, string=):!ui.bootstrap.Tooltip}
 */
ui.bootstrap.$tooltip;

/******************************************************************************
 * $tooltipProvider service
 *****************************************************************************/


/**
 * @typedef {{ animation:boolean|undefined, appendToBody:boolean|undefined,
 *   placement:string|undefined, popupDelay:number|undefined}}
 */
ui.bootstrap.tooltipOptions;


/**
 * @typedef {{
 *   options: function(!ui.bootstrap.tooltipOptions=):
 *     !ui.bootstrap.tooltipOptions,
 *   setTriggers: function(!Object.<string,string>):!Object.<string, string>
 *   }}
 */
ui.bootstrap.$tooltipProvider;


/**
 * @param {!ui.bootstrap.tooltipOptions} options
 * @return {!ui.bootstrap.tooltipOptions}
 */
ui.bootstrap.$tooltipProvider.options = function(options) {};


/**
 * @param {!Object.<string, string>} triggers
 * @return {!Object.<string, string>}
 */
ui.bootstrap.$tooltipProvider.setTriggers = function(triggers) {};
