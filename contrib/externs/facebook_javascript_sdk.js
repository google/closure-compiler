/*
 * Copyright 2010 The Closure Compiler Authors.
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
 * @fileoverview Externs for Facebook Javascript SDK
 * @see http://developers.facebook.com/docs/reference/javascript/
 * @externs
 */

/** @const */
var FB = {};

/**
 * @see http://developers.facebook.com/docs/reference/javascript/fb.getsession/
 * @return {Object<string,*>}
 */
FB.getSession = function() { };

/**
 * @see http://developers.facebook.com/docs/reference/javascript/fb.api/
 * @param {string} path
 * @param {(string|Object<string, *>|function(Object<string,*>))=} method
 * @param {(Object<string, *>|function(Object<string,*>))=} params
 * @param {function(Object<string,*>)=} callback
 */
FB.api = function(path, method, params, callback) { };

/**
 * @see http://developers.facebook.com/docs/reference/javascript/fb.getloginstatus/
 * @param {function(Object<string,*>)} callback
 * @param {boolean=} force
 */
FB.getLoginStatus = function(callback, force) {};

/**
 * @see http://developers.facebook.com/docs/reference/javascript/fb.init/
 * @param {Object<string,*>=} opt_opts
 */
FB.init = function(opt_opts) {};

/**
 * @see http://developers.facebook.com/docs/reference/javascript/fb.login/
 * @param {function(Object<string,*>)} callback
 * @param {Object<string,*>=} opt_opts
 */
FB.login = function(callback, opt_opts) {};

/**
 * @see http://developers.facebook.com/docs/reference/javascript/fb.logout/
 * @param {function(Object<string,*>)=} callback
 */
FB.logout = function(callback) {};

/**
 * @see http://developers.facebook.com/docs/reference/javascript/fb.ui/
 * @param {Object<string, *>} params
 * @param {function(Object<string,*>)=} callback
 */
FB.ui = function(params, callback) { };

/** @const */
FB.Event = {};

/**
 * @see http://developers.facebook.com/docs/reference/javascript/fb.Event.subscribe/
 * @param {string} eventName
 * @param {function(Object<string,*>)} callback
 */
FB.Event.subscribe = function(eventName, callback) {};

/**
 * @see http://developers.facebook.com/docs/reference/javascript/fb.Event.unsubscribe/
 * @param {string} eventName
 * @param {function(Object<string,*>)} callback
 */
FB.Event.unsubscribe = function(eventName, callback) {};

/** @const */
FB.XFBML = {};

/**
 * @see http://developers.facebook.com/docs/reference/javascript/fb.XFBML.parse/
 * @param {Element=} node
 * @param {function(Object<string,*>)=} callback
 */
FB.XFBML.parse = function(node, callback) {};

/** @const */
FB.Data = {};

/**
 * This object is not constructed directly. It is returned by calls to
 * FB.Data.Query.
 * @constructor
 * @private
 */
FB.Data.queryObject = function() {};

/**
 * @see http://developers.facebook.com/docs/reference/javascript/fb.data.query/
 * @param {string} template
 * @param {...(string|number|FB.Data.queryObject)} var_data
 * @return {FB.Data.queryObject}
 */
FB.Data.query = function(template, var_data) {};

/**
 * @see http://developers.facebook.com/docs/reference/javascript/fb.data.waiton/
 * @param {Array<*>} dependencies
 * @param {function(Object<string,*>)} callback
 * @return {Object<string,*>}
 */
FB.Data.waitOn = function(dependencies, callback) {};

/** @const */
FB.Canvas = {};

/**
 * @see http://developers.facebook.com/docs/reference/javascript/fb.Canvas.setAutoResize/
 * @param {(boolean|number)=} onOrOff
 * @param {number=} interval
 */
FB.Canvas.setAutoResize = function(onOrOff, interval) {};

/**
 * @see http://developers.facebook.com/docs/reference/javascript/fb.Canvas.setSize/
 * @param {Object<string, number>} params
 */
FB.Canvas.setSize = function(params) {};

/**
 * @see https://developers.facebook.com/docs/reference/javascript/FB.Canvas.getPageInfo/
 * @param {function(Object<string,*>)} callback
 */
FB.Canvas.getPageInfo = function(callback) {};
