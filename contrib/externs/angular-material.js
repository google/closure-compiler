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
 *   templateUrl: (string|undefined),
 *   template: (string|undefined),
 *   targetEvent: (Object|undefined),
 *   hasBackdrop: (boolean|undefined),
 *   clickOutsideToClose: (boolean|undefined),
 *   escapeToClose: (boolean|undefined),
 *   controller: (Function|string|undefined),
 *   locals: (Object|undefined),
 *   resolve: (Object|undefined),
 *   controllerAs: (string|undefined),
 *   parent: (Element|undefined)
 * }}
 */
md.$dialog.options;


/**
 * @typedef {{
 *   title: (function(string): md.$dialog.AlertConfig_),
 *   content: (function(string): md.$dialog.AlertConfig_),
 *   ariaLabel: (function(string): md.$dialog.AlertConfig_),
 *   ok: (function(string): md.$dialog.AlertConfig_),
 *   theme: (function(string): md.$dialog.AlertConfig_)
 * }}
 */
md.$dialog.AlertConfig_;


/**
 * @typedef {{
 *   title: (function(string): md.$dialog.ConfirmConfig_),
 *   content: (function(string): md.$dialog.ConfirmConfig_),
 *   ariaLabel: (function(string): md.$dialog.ConfirmConfig_),
 *   ok: (function(string): md.$dialog.ConfirmConfig_),
 *   cancel: (function(string): md.$dialog.ConfirmConfig_),
 *   theme: (function(string): md.$dialog.ConfirmConfig_)
 * }}
 */
md.$dialog.ConfirmConfig_;


/**
 * @param {md.$dialog.options|md.$dialog.ConfirmConfig_|
 *     md.$dialog.AlertConfig_} options
 * @return {!angular.$q.Promise}
 */
md.$dialog.prototype.show = function(options) {};


/**
 * @type {function(*=)}
 */
md.$dialog.prototype.hide = function() {};


/**
 * @type {function(*=)}
 */
md.$dialog.prototype.cancel = function() {};


/** @return {!md.$dialog.AlertConfig_} */
md.$dialog.prototype.alert = function() {};


/** @return {!md.$dialog.ConfirmConfig_} */
md.$dialog.prototype.confirm = function() {};


/******************************************************************************
 * $toast Service
 *****************************************************************************/


/** @constructor */
md.$toast = function() {};


/**
 * @typedef {{
 *   templateUrl: (string|undefined),
 *   template: (string|undefined),
 *   hideDelay: (number|undefined),
 *   position: (string|undefined),
 *   controller: (Function|string|undefined),
 *   locals: (Object|undefined),
 *   resolve: (Object|undefined),
 *   controllerAs: (string|undefined)
 * }}
 */
md.$toast.options;


/**
 * @param {md.$toast.options} options
 * @return {angular.$q.Promise}
 */
md.$toast.prototype.show = function(options) {};


/**
 * @type {function(*=)}
 */
md.$toast.prototype.hide = function() {};


/**
 * @type {function(*=)}
 */
md.$toast.prototype.cancel = function() {};

/**
 * @typedef {{
 *   content: function(string):md.$toast.preset,
 *   action: function(string):md.$toast.preset,
 *   highlightAction: function(boolean):md.$toast.preset,
 *   capsule: function(boolean):md.$toast.preset,
 *   position: function(string):md.$toast.preset,
 *   hidedelay: function(number):md.$toast.preset
 * }}
 */
md.$toast.preset;


/**
 * @return {md.$toast.preset}
 */
md.$toast.prototype.simple = function() {};

/******************************************************************************
 * $sidenav Service
 *****************************************************************************/


/**
 * @typedef {{
 *   isOpen: function():boolean,
 *   toggle: function(),
 *   open: function(),
 *   close: function()
 * }}
 */
md._sidenavService;

/**
 * Sidenav service is actually a function that returns an object.
 * @typedef {
 *   function(string):md._sidenavService
 * }
 */
md.$sidenav;


/******************************************************************************
 * $mdThemingProvider Service
 *****************************************************************************/

/**
 * @typedef {{
 *   alwaysWatchTheme: function(boolean),
 *   definePalette:
 *       function(string, !Object<string,string>) : md.$mdThemingProvider,
 *   extendPalette:
 *       function(string, !Object<string,string>) : !Object<string,string>,
 *   setDefaultTheme: function(string),
 *   theme: function(string,string=) : md.$mdThemingProvider.Theme
 * }}
 */
md.$mdThemingProvider;

/*****************************************************************************/

/**
 * @param {string} name
 * @constructor
 */
md.$mdThemingProvider.Theme = function(name) {};

/** @type {string} */
md.$mdThemingProvider.Theme.prototype.name;

/** @type {!Object<string,string>} */
md.$mdThemingProvider.Theme.prototype.colors;

/**
 * @param {string} primaryPalette
 * @param {Object<string,string>=} opt_colors
 * @return {md.$mdThemingProvider.Theme}
 */
md.$mdThemingProvider.Theme.prototype.primaryPalette =
    function(primaryPalette, opt_colors) {};

/**
 * @param {string} accentPalette
 * @param {Object<string,string>=} opt_colors
 * @return {md.$mdThemingProvider.Theme}
 */
md.$mdThemingProvider.Theme.prototype.accentPalette =
    function(accentPalette, opt_colors) {};

/**
 * @param {string} backgroundPalette
 * @param {Object<string,string>=} opt_colors
 * @return {md.$mdThemingProvider.Theme}
 */
md.$mdThemingProvider.Theme.prototype.backgroundPalette =
    function(backgroundPalette, opt_colors) {};

/**
 * @param {string} warnPalette
 * @return {md.$mdThemingProvider.Theme}
 */
md.$mdThemingProvider.Theme.prototype.warnPalette = function(warnPalette) {};

/*****************************************************************************/


/**
 * @param {string} themeName
 * @param {string=} opt_inheritFrom
 * @return {md.$mdThemingProvider.Theme}
 */
md.$mdThemingProvider.prototype.theme = function(themeName, opt_inheritFrom) {};


