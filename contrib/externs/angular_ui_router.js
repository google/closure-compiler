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
 * @fileoverview Externs for ui-router.
 *
 * API Reference: http://angular-ui.github.io/ui-router/site/#/api/ui.router
 *
 * @externs
 */

/**
 * @type {Object}
 * @const
 */
var ui = {};


/**
 * @type {Object}
 * @const
 */
ui.router = {};


// TODO: Provide stronger types for properties on $state.
/**
 * @typedef {{
 *   params: Object,
 *   current: Object,
 *   transition: ?angular.$q.Promise,
 *   get: function(...),
 *   go: function(...),
 *   href: function(...),
 *   includes: function(...),
 *   is: function(...),
 *   reload: function(...),
 *   transitionTo: function(...)
 * }}
 */
ui.router.$state;


/**
 * @typedef {Object.<string, string>}
 */
ui.router.$stateParams;


/**
 * This is the object that the ui-router passes to callback functions listening
 * on ui router events such as {@code $stateChangeStart} or
 * {@code $stateChangeError} as the {@code toState} and {@code fromState}.
 * Example:
 * $rootScope.$on('$stateChangeStart', function(
 *     event, toState, toParams, fromState, fromParams){ ... });
 *
 * @typedef {{
 *     'abstract': (boolean|undefined),
 *     controller: (string|Function|undefined),
 *     controllerAs: (string|undefined),
 *     controllerProvider: (Function|undefined),
 *     data: (Object|undefined),
 *     name: string,
 *     onEnter: (Object|undefined),
 *     onExit: (Object|undefined),
 *     params: (Object|undefined),
 *     reloadOnSearch: (boolean|undefined),
 *     resolve: (Object.<string, !Function>|undefined),
 *     template: (string|Function|undefined),
 *     templateUrl: (string|Function|undefined),
 *     templateProvider: (Function|undefined),
 *     url: (string|undefined),
 *     views: (Object|undefined)
 * }}
 */
ui.router.State;


/**
 * @constructor
 */
ui.router.$urlMatcherFactory = function() {};


/**
 * @constructor
 * @param {!ui.router.$urlMatcherFactory} $urlMatcherFactory
 */
ui.router.$urlRouterProvider = function($urlMatcherFactory) {};


/**
 * @param {string|RegExp} url
 * @param {string|function(...)|Array.<!Object>} route
 */
ui.router.$urlRouterProvider.prototype.when = function(url, route) {};


/**
 * @param {string|function(...)} path
 */
ui.router.$urlRouterProvider.prototype.otherwise = function(path) {};


/**
 * @param {function(...)} rule
 */
ui.router.$urlRouterProvider.prototype.rule = function(rule) {};


/**
 * @constructor
 * @param {!ui.router.$urlRouterProvider} $urlRouterProvider
 * @param {!ui.router.$urlMatcherFactory} $urlMatcherFactory
 * @param {!angular.$locationProvider} $locationProvider
 */
ui.router.$stateProvider = function(
    $urlRouterProvider, $urlMatcherFactory, $locationProvider) {};


/**
 * @param {!string} name
 * @param {Object} definition
 * @return {!ui.router.$stateProvider}
 */
ui.router.$stateProvider.prototype.state = function(
    name, definition) {};
