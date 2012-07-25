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
 * @fileoverview Externs for Angular 1.
 *
 * TODO: Mocks.
 * TODO: Remaining Services.
 * TODO: Resolve two issues with angular.$http
 *         1) angular.$http cannot be declared as a callable type.
 *            Its helper methods should be used instead.
 *         2) angular.$http.delete cannot be added as an extern
 *            as it is a reserved keyword.
 *            Its use is potentially not supported in IE.
 *            It may be aliased as 'remove' in a future version.
 *
 * @see http://angularjs.org/
 * @externs
 */

/**
 * @type {Object}
 * @const
 */
var angular = {};

/**
 * @param {Object} self
 * @param {function()} fn
 * @param {...*} args
 * @return {function()}
 */
angular.bind = function(self, fn, args) {};

/**
 * @param {Element} element
 * @param {Array.<string|Function>=} opt_modules
 * @return {function()}
 */
angular.bootstrap = function(element, opt_modules) {};

/**
 * @param {*} source
 * @param {(Object|Array)=} opt_dest
 * @return {*}
 */
angular.copy = function(source, opt_dest) {};

/**
 * @param {string|Element} element
 * @return {Object}
 */
angular.element = function(element) {};

/**
 * @param {*} o1
 * @param {*} o2
 * @return {boolean}
 */
angular.equals = function(o1, o2) {};

/**
 * @param {Object} dest
 * @param {...Object} srcs
 */
angular.extend = function(dest, srcs) {};

/**
 * @param {Object|Array} obj
 * @param {Function} iterator
 * @param {Object=} opt_context
 * @return {Object|Array}
 */
angular.forEach = function(obj, iterator, opt_context) {};

/**
 * @param {string} json
 * @return {Object|Array|Date|string|number}
 */
angular.fromJson = function(json) {};

/**
 * @param {*} arg
 * @return {*}
 */
angular.identity = function(arg) {};

/**
 * @param {Array.<string|Function>} modules
 * @return {function()}
 */
angular.injector = function(modules) {};

/**
 * @param {*} value
 * @return {boolean}
 */
angular.isArray = function(value) {};

/**
 * @param {*} value
 * @return {boolean}
 */
angular.isDate = function(value) {};

/**
 * @param {*} value
 * @return {boolean}
 */
angular.isDefined = function(value) {};

/**
 * @param {*} value
 * @return {boolean}
 */
angular.isElement = function(value) {};

/**
 * @param {*} value
 * @return {boolean}
 */
angular.isFunction = function(value) {};

/**
 * @param {*} value
 * @return {boolean}
 */
angular.isNumber = function(value) {};

/**
 * @param {*} value
 * @return {boolean}
 */
angular.isObject = function(value) {};

/**
 * @param {*} value
 * @return {boolean}
 */
angular.isString = function(value) {};

/**
 * @param {*} value
 * @return {boolean}
 */
angular.isUndefined = function(value) {};

/**
 * @param {string} s
 * @return {string}
 */
angular.lowercase = function(s) {};

angular.mock = {};

/**
 * @param {string} name
 * @param {Array.<string>=} opt_requires
 * @param {Function=} opt_configFn
 * @return {angular.Module}
 */
angular.module = function(name, opt_requires, opt_configFn) {};

/**
 * @typedef {{
 *   config: function((Function|Array.<string|Function>)):angular.Module,
 *   constant: function(string, *):angular.Module,
 *   controller:
 *       function(string, (Function|Array.<string|Function>)):angular.Module,
 *   directive:
 *       function(string, (Function|Array.<string|Function>)):angular.Module,
 *   factory:
 *       function(string, (Function|Array.<string|Function>)):angular.Module,
 *   filter:
 *       function(string, (Function|Array.<string|Function>)):angular.Module,
 *   name: string,
 *   provider:
 *       function(string, (Function|Array.<string|Function>)):angular.Module,
 *   requires: Array.<string>,
 *   run: function(function()):angular.Module,
 *   service:
 *       function(string, (Function|Array.<string|Function>)):angular.Module,
 *   value: function(string, *):angular.Module
 *   }}
 */
angular.Module;

/**
 * @param {Function|Array.<string|Function>} configFn
 * @return {angular.Module}
 */
angular.Module.config = function(configFn) {};

/**
 * @param {string} name
 * @param {*} object
 * @return {angular.Module}
 */
angular.Module.constant = function(name, object) {};

/**
 * @param {string} name
 * @param {Function|Array.<string|Function>} constructor
 * @return {angular.Module}
 */
angular.Module.controller = function(name, constructor) {};

/**
 * @param {string} name
 * @param {Function|Array.<string|Function>} directiveFactory
 * @return {angular.Module}
 */
angular.Module.directive = function(name, directiveFactory) {};

/**
 * @param {string} name
 * @param {Function|Array.<string|Function>} providerFunction
 * @return {angular.Module}
 */
angular.Module.factory = function(name, providerFunction) {};

/**
 * @param {string} name
 * @param {Function|Array.<string|Function>} filterFactory
 * @return {angular.Module}
 */
angular.Module.filter = function(name, filterFactory) {};

/**
 * @param {string} name
 * @param {Function|Array.<string|Function>} providerType
 * @return {angular.Module}
 */
angular.Module.provider = function(name, providerType) {};

/**
 * @param {function()} initializationFn
 * @return {angular.Module}
 */
angular.Module.run = function(initializationFn) {};

/**
 * @param {string} name
 * @param {Function|Array.<string|Function>} constructor
 * @return {angular.Module}
 */
angular.Module.service = function(name, constructor) {};

/**
 * @param {string} name
 * @param {*} object
 * @return {angular.Module}
 */
angular.Module.value = function(name, object) {};

/**
 * @type {string}
 */
angular.Module.name = '';

/**
 * @type {Array.<string>}
 */
angular.Module.requires;

angular.noop = function() {};

/**
 * @typedef {{
 *   $apply: function((string|function())=):*,
 *   $broadcast: function(string, ...[*]),
 *   $destroy: function(),
 *   $digest: function(),
 *   $emit: function(string, ...[*]),
 *   $eval: function((string|function())=, Object=):*,
 *   $evalAsync: function((string|function())=),
 *   $id: number,
 *   $new: function():Object,
 *   $on: function(string, function(angular.Scope.Event)):function(),
 *   $watch: function(
 *       (string|function()),
 *       (function(*, *, angular.Scope)|string)=, boolean=):function()
 *   }}
 */
angular.Scope;

/**
 * @param {(string|function())=} opt_exp
 * @return {*}
 */
angular.Scope.$apply = function(opt_exp) {};

/**
 * @param {string} name
 * @param {...*} args
 */
angular.Scope.$broadcast = function(name, args) {};

angular.Scope.$destroy = function() {};

angular.Scope.$digest = function() {};

/**
 * @param {string} name
 * @param {...*} args
 */
angular.Scope.$emit = function(name, args) {};

/**
 * @param {(string|function())=} opt_exp
 * @param {Object=} opt_locals
 * @return {*}
 */
angular.Scope.$eval = function(opt_exp, opt_locals) {};

/**
 * @param {(string|function())=} opt_exp
 */
angular.Scope.$evalAsync = function(opt_exp) {};

/**
 * @return {Object}
 */
angular.Scope.$new = function() {};

/**
 * @param {string} name
 * @param {function(angular.Scope.Event)} listener
 * @return {function()}
 */
angular.Scope.$on = function(name, listener) {};

/**
 * @param {string|function()} exp
 * @param {(string|function())=} opt_listener
 * @param {boolean=} opt_objectEquality
 * @return {function()}
 */
angular.Scope.$watch = function(exp, opt_listener, opt_objectEquality) {};

/**
 * @typedef {{
 *   currentScope: angular.Scope,
 *   defaultPrevented: boolean,
 *   name: string,
 *   preventDefault: function(),
 *   stopPropagation: function(),
 *   targetScope: angular.Scope
 *   }}
 */
angular.Scope.Event;

/** @type {angular.Scope} */
angular.Scope.Event.currentScope;

/** @type {boolean} */
angular.Scope.Event.defaultPrevented;

/** @type {string} */
angular.Scope.Event.name;

angular.Scope.Event.preventDefault = function() {};

angular.Scope.Event.stopPropagation = function() {};

/** @type {angular.Scope} */
angular.Scope.Event.targetScope;

/**
 * @param {Object|Array|Date|string|number} obj
 * @param {boolean=} opt_pretty
 * @return {string}
 */
angular.toJson = function(obj, opt_pretty) {};

/**
 * @param {string} s
 * @return {string}
 */
angular.uppercase = function(s) {};

/**
 * @type {Object}
 */
angular.version = {};

/**
 * @type {string}
 */
angular.version.full = '';

/**
 * @type {number}
 */
angular.version.major = 0;

/**
 * @type {number}
 */
angular.version.minor = 0;

/**
 * @type {number}
 */
angular.version.dot = 0;

/**
 * @type {string}
 */
angular.version.codeName = '';

/******************************************************************************
 * $http Service
 *****************************************************************************/

/**
 * @param {string} cacheId
 * @param {angular.$cacheFactory.Options=} opt_options
 * @return {angular.$cacheFactory.Cache}
 */
angular.$cacheFactory = function(cacheId, opt_options) {};

/** @typedef {{capacity: (number|undefined)}} */
angular.$cacheFactory.Options;

/**
 * @typedef {{
 *   info: function():angular.$cacheFactory.Cache.Info,
 *   put: function(string, *),
 *   get: function(string):*,
 *   remove: function(string),
 *   removeAll: function(),
 *   destroy: function()
 *   }}
 */
angular.$cacheFactory.Cache;

/**
 * @typedef {{
 *   id: string,
 *   size: number,
 *   options: angular.$cacheFactory.Options
 *   }}
 */
angular.$cacheFactory.Cache.Info;

/**
 * This is a typedef because the closure compiler does not allow
 * defining a type that is a function with properties.
 * If you are trying to use the $http service as a function, try
 * using one of the helper functions instead.
 * @typedef {{
 *   delete: function(string, angular.$http.Config=):angular.$http.HttpPromise,
 *   get: function(string, angular.$http.Config=):angular.$http.HttpPromise,
 *   head: function(string, angular.$http.Config=):angular.$http.HttpPromise,
 *   jsonp: function(string, angular.$http.Config=):angular.$http.HttpPromise,
 *   post: function(string, *, angular.$http.Config=):angular.$http.HttpPromise,
 *   put: function(string, *, angular.$http.Config=):angular.$http.HttpPromise,
 *   defaults: angular.$http.Config,
 *   pendingRequests: Array.<angular.$http.Config>
 *   }}
 */
angular.$http;

/**
 * @typedef {{
 *   cache: (boolean|angular.$cacheFactory.Cache|undefined),
 *   data: (string|Object|undefined),
 *   headers: (Object|undefined),
 *   method: (string|undefined),
 *   params: (Object.<(string|Object)>|undefined),
 *   timeout: (number|undefined),
 *   transformRequest:
 *       (function((string|Object), Object):(string|Object)|
 *       Array.<function((string|Object), Object):(string|Object)>|undefined),
 *   url: (string|undefined),
 *   withCredentials: (boolean|undefined)
 *   }}
 */
angular.$http.Config;

// /**
//  * This extern is currently incomplete as delete is a reserved word.
//  * @param {string} url
//  * @param {angular.$http.Config=} opt_config
//  * @return {angular.$http.HttpPromise}
//  */
// angular.$http.delete = function(url, opt_config) {};

/**
 * @param {string} url
 * @param {angular.$http.Config=} opt_config
 * @return {angular.$http.HttpPromise}
 */
angular.$http.get = function(url, opt_config) {};

/**
 * @param {string} url
 * @param {angular.$http.Config=} opt_config
 * @return {angular.$http.HttpPromise}
 */
angular.$http.head = function(url, opt_config) {};

/**
 * @param {string} url
 * @param {angular.$http.Config=} opt_config
 * @return {angular.$http.HttpPromise}
 */
angular.$http.jsonp = function(url, opt_config) {};

/**
 * @param {string} url
 * @param {*} data
 * @param {angular.$http.Config=} opt_config
 * @return {angular.$http.HttpPromise}
 */
angular.$http.post = function(url, data, opt_config) {};

/**
 * @param {string} url
 * @param {*} data
 * @param {angular.$http.Config=} opt_config
 * @return {angular.$http.HttpPromise}
 */
angular.$http.put = function(url, data, opt_config) {};

/**
 * @type {angular.$http.Config}
 */
angular.$http.defaults;

/**
 * @type {Array.<angular.$http.Config>}
 * @const
 */
angular.$http.pendingRequests;

/**
 * @typedef {{
 *   then: function(function(*), function(*)=): angular.$http.HttpPromise,
 *   success: function(
 *       function((string|Object), number, function(string):string, Object)),
 *   error: function(
 *       function((string|Object), number, function(string):string, Object))
 *   }}
 */
angular.$http.HttpPromise;

/**
 * @param {function(*)} successCallback
 * @param {function(*)=} opt_errorCallback
 * @return {angular.$http.HttpPromise}
 */
angular.$http.HttpPromise.then = function(
    successCallback, opt_errorCallback) {};

/**
 * @param {function((string|Object), number,
 *         function(string):string, Object)} callback
 */
angular.$http.HttpPromise.success = function(callback) {};

/**
 * @param {function((string|Object), number,
 *         function(string):string, Object)} callback
 */
angular.$http.HttpPromise.error = function(callback) {};

/******************************************************************************
 * $location Service
 *****************************************************************************/

/**
 * @typedef {{
 *   absUrl: function():string,
 *   hash: function(string=):string,
 *   host: function():string,
 *   path: function(string=):string,
 *   port: function():number,
 *   protocol: function():string,
 *   replace: function(),
 *   search: function((string|Object.<string,string>)=, string=):string,
 *   url: function(string=):string
 *   }}
 */
angular.$location;

/**
 * @return {string}
 */
angular.$location.absUrl = function() {};

/**
 * @param {string=} opt_hash
 * @return {string}
 */
angular.$location.hash = function(opt_hash) {};

/**
 * @return {string}
 */
angular.$location.host = function() {};

/**
 * @param {string=} opt_path
 * @return {string}
 */
angular.$location.path = function(opt_path) {};

/**
 * @return {number}
 */
angular.$location.port = function() {};

/**
 * @return {string}
 */
angular.$location.protocol = function() {};

/**
 * @type {function()}
 */
angular.$location.replace = function() {};

/**
 * @param {(string|Object.<string, string>)=} opt_search
 * @param {string=} opt_paramValue
 * @return {string}
 */
angular.$location.search = function(opt_search, opt_paramValue) {};

/**
 * @param {string=} opt_url
 * @return {string}
 */
angular.$location.url = function(opt_url) {};

/******************************************************************************
 * $log Service
 *****************************************************************************/

/**
 * @typedef {{
 *   error: function(...[*]),
 *   info: function(...[*]),
 *   log: function(...[*]),
 *   warn: function(...[*])
 *   }}
 */
angular.$log;

/**
 * @param {...*} var_args
 */
angular.$log.error = function(var_args) {};

/**
 * @param {...*} var_args
 */
angular.$log.info = function(var_args) {};

/**
 * @param {...*} var_args
 */
angular.$log.log = function(var_args) {};

/**
 * @param {...*} var_args
 */
angular.$log.warn = function(var_args) {};

/******************************************************************************
 * $q Service
 *****************************************************************************/

/**
 * @typedef {{
 *   all: function(Array.<angular.$q.Promise>): angular.$q.Promise,
 *   defer: function():angular.$q.Deferred,
 *   reject: function():angular.$q.Promise,
 *   when: function(*):angular.$q.Promise
 *   }}
 */
angular.$q;

/**
 * @param {Array.<angular.$q.Promise>} promises
 * @return {angular.$q.Promise}
 */
angular.$q.all = function(promises) {};

/**
 * @return {angular.$q.Deferred}
 */
angular.$q.defer = function() {};

/**
 * @return {angular.$q.Promise}
 */
angular.$q.reject = function() {};

/**
 * @param {*} value
 * @return {angular.$q.Promise}
 */
angular.$q.when = function(value) {};

/**
 * @typedef {{
 *   resolve: function(*),
 *   reject: function(*),
 *   promise: angular.$q.Promise
 *   }}
 */
angular.$q.Deferred;

/** @param {*} value */
angular.$q.Deferred.resolve = function(value) {};

/** @param {*} reason */
angular.$q.Deferred.reject = function(reason) {};

/** @type {angular.$q.Promise} */
angular.$q.Deferred.promise;

/**
 * @typedef {{then: function(function(*), function(*)=): angular.$q.Promise}}
 */
angular.$q.Promise;

/**
 * @param {function(*)} successCallback
 * @param {function(*)=} opt_errorCallback
 * @return {angular.$q.Promise}
 */
angular.$q.Promise.then = function(successCallback, opt_errorCallback) {};

/******************************************************************************
 * $route Service
 *****************************************************************************/

/**
 * @typedef {{
 *   reload: function(),
 *   current: angular.$route.Route,
 *   routes: Array.<angular.$route.Route>
 * }}
 */
angular.$route;

/** @type {function()} */
angular.$route.reload = function() {};

/** @type {angular.$route.Route} */
angular.$route.current;

/** @type {Array.<angular.$route.Route>} */
angular.$route.routes;

/**
 * @typedef {{
 *   $route: angular.$routeProvider.Params,
 *   locals: Object.<string, *>,
 *   params: Object.<string, string>,
 *   pathParams: Object.<string, string>,
 *   scope: Object.<string, *>
 * }}
 */
angular.$route.Route;

/** @type {angular.$routeProvider.Params} */
angular.$route.Route.$route;

/** @type {Object.<string, *>} */
angular.$route.Route.locals;

/** @type {Object.<string, string>} */
angular.$route.Route.params;

/** @type {Object.<string, string>} */
angular.$route.Route.pathParams;

/** @type {Object.<string, *>} */
angular.$route.Route.scope;

/******************************************************************************
 * $routeProvider Service
 *****************************************************************************/

/**
 * @typedef {{
 *   otherwise:
 *       function(angular.$routeProvider.Params): angular.$routeProvider,
 *   when:
 *       function(
 *           string, angular.$routeProvider.Params): angular.$routeProvider
 *   }}
 */
angular.$routeProvider;

/**
 * @param {angular.$routeProvider.Params} params
 * @return {angular.$routeProvider}
 */
angular.$routeProvider.otherwise = function(params) {};

/**
 * @param {string} path
 * @param {angular.$routeProvider.Params} route
 * @return {angular.$routeProvider}
 */
angular.$routeProvider.when = function(path, route) {};

/**
 * @typedef {{
 *   controller: (Function|Array.<string|Function>|undefined),
 *   template: (string|undefined),
 *   templateUrl: (string|undefined),
 *   resolve: (Object.<string, (
 *       string|Function|Array.<string|Function>|angular.$q.Promise
 *       )>|undefined),
 *   redirectTo: (string|function()|undefined),
 *   reloadOnSearch: (boolean|undefined)
 *   }}
 */
angular.$routeProvider.Params;

/** @type {Function|Array.<string|Function>} */
angular.$routeProvider.Params.controller;

/** @type {string} */
angular.$routeProvider.Params.template;

/** @type {string} */
angular.$routeProvider.Params.templateUrl;

/**
 * @type {
 *   Object.<string, (
 *       string|Function|Array.<string|Function>|angular.$q.Promise
 *       )>}
 */
angular.$routeProvider.Params.resolve;

/** @type {string|function()} */
angular.$routeProvider.Params.redirectTo;

/** @type {boolean} */
angular.$routeProvider.Params.reloadOnSearch;
