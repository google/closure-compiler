/*
 * Copyright 2012 The Closure Compiler Authors.
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
 *
 * @fileoverview an @externs file for the Angular Material library
 *
 */


/**
 * @const {Object}
 */
var md = {};


/******************************************************************************
 * $dialog Service
 *****************************************************************************/


/** @constructor */
md.$dialog = function() {};


/**
 * @typedef {{
 *   templateUrl: string=,
 *   template: string=,
 *   targetEvent: DOMClickEvent=,
 *   hasBackdrop: boolean=,
 *   clickOutsideToClose: boolean=,
 *   escapeToClose: boolean=,
 *   controller: (Function|string)=,
 *   locals: Object,=
 *   resolve: Object=,
 *   controllerAs: string=,
 *   parent: Element=
 * }}
 */
md.$dialog.options;


/**
 * @param {md.$dialog.options} options
 * @return {angular.$q.Promise}
 */
md.$dialog.prototype.show = function(options) {};


/**
 * @type {function(*)}
 */
md.$dialog.prototype.hide = function() {};


/**
 * @type {function(*)}
 */
md.$dialog.prototype.cancel = function() {};


/******************************************************************************
 * $toast Service
 *****************************************************************************/


/** @constructor */
md.$toast = function() {};


/**
 * @typedef {{
 *   templateUrl: string=,
 *   template: string=,
 *   hideDelay: number=,
 *   position: string=,
 *   controller: (Function|string)=,
 *   locals: Object=,
 *   resolve: Object=,
 *   controllerAs: string=
 * }}
 */
md.$toast.options;


/**
 * @param {md.$toast.options} options
 * @return {angular.$q.Promise}
 */
md.$toast.prototype.show = function(options) {};


/**
 * @type {function(*)}
 */
md.$toast.prototype.hide = function() {};


/**
 * @type {function(*)}
 */
md.$toast.prototype.cancel = function() {};
