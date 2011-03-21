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
 * @fileoverview Externs for jQuery 1.5.1
 *
 * Note that some functions use different return types depending on the number
 * of parameters passed in. In these cases, you may need to annotate the type
 * of the result in your code, so the JSCompiler understands which type you're
 * expecting. For example:
 *    <code>var elt = /** @type {Element} * / (foo.get(0));</code>
 *
 * @see http://api.jquery.com/
 * @externs
 */

/** @typedef {(Window|Document|Element|Array.<Element>|string|jQueryObject)} */
var jQuerySelector;

/**
 * @param {(jQuerySelector|Element|Array.<Element>|jQueryObject|string|
 *     function()|Object)=} arg1
 * @param {(Element|jQueryObject|Document|
 *     Object.<string, (string|function(jQuery.event=))>)=} arg2
 * @return {jQueryObject}
 */
function $(arg1, arg2) {};

/**
 * @param {(jQuerySelector|Element|Array.<Element>|jQueryObject|string|
 *     function()|Object)=} arg1
 * @param {(Element|jQueryObject|Document|
 *     Object.<string, (string|function(jQuery.event=))>)=} arg2
 * @return {jQueryObject}
 */
function jQuery(arg1, arg2) {};

/**
 * @param {(string|Object.<string,*>)} arg1
 * @param {Object.<string,*>=} settings
 * @return {jQuery.jqXHR}
 */
jQuery.ajax = function(arg1, settings) {};

/** @param {Object.<string,*>} options */
jQuery.ajaxSetup = function(options) {};

/** @type {boolean} */
jQuery.boxModel;

/** @type {Object.<string,*>} */
jQuery.browser;

/**
 * @type {boolean}
 * @const
 */
jQuery.browser.mozilla;

/**
 * @type {boolean}
 * @const
 */
jQuery.browser.msie;

/**
 * @type {boolean}
 * @const
 */
jQuery.browser.opera;

/**
 * @type {boolean}
 * @const
 * @deprecated
 */
jQuery.browser.safari;

/** @type {string} */
jQuery.browser.version;

/**
 * @type {boolean}
 * @const
 */
jQuery.browser.webkit;

/**
 * @param {Element} container
 * @param {Element} contained
 * @return {boolean}
 */
jQuery.contains = function(container, contained) {};

/** @type {Object.<string, *>} */
jQuery.cssHooks;

/**
 * @param {Element} elem
 * @param {string=} key
 * @param {*=} value
 * @return {*}
 */
jQuery.data = function(elem, key, value) {};

/**
 * @constructor
 * @implements {jQuery.DeferredObject}
 * @param {function()=} opt_fn
 */
jQuery.deferred = function(opt_fn) {};

/**
 * @constructor
 * @implements {jQuery.DeferredObject}
 * @param {function()=} opt_fn
 */
jQuery.Deferred = function(opt_fn) {};

/**
 * @param {function()} doneCallbacks
 * @return {jQuery.DeferredObject}
 */
jQuery.deferred.prototype.done = function(doneCallbacks) {};

/**
 * @param {function()} doneCallbacks
 * @return {jQuery.DeferredObject}
 */
jQuery.Deferred.prototype.done = function(doneCallbacks) {};

/**
 * @param {function()} failCallbacks
 * @return {jQuery.DeferredObject}
 */
jQuery.deferred.prototype.fail = function(failCallbacks) {};

/**
 * @param {function()} failCallbacks
 * @return {jQuery.DeferredObject}
 */
jQuery.Deferred.prototype.fail = function(failCallbacks) {};

/**
 * @return {boolean}
 * @nosideeffects
 */
jQuery.deferred.prototype.isRejected = function() {};

/**
 * @return {boolean}
 * @nosideeffects
 */
jQuery.Deferred.prototype.isRejected = function() {};

/**
 * @return {boolean}
 * @nosideeffects
 */
jQuery.deferred.prototype.isResolved = function() {};

/**
 * @return {boolean}
 * @nosideeffects
 */
jQuery.Deferred.prototype.isResolved = function() {};

/** @return {jQuery.Promise} */
jQuery.deferred.prototype.promise = function() {};

/** @return {jQuery.Promise} */
jQuery.Deferred.prototype.promise = function() {};

/**
 * @param {...*} var_args
 * @return {jQuery.DeferredObject}
 */
jQuery.deferred.prototype.reject = function(var_args) {};

/**
 * @param {...*} var_args
 * @return {jQuery.DeferredObject}
 */
jQuery.Deferred.prototype.reject = function(var_args) {};

/**
 * @param {Object} context
 * @param {Array.<*>=} args
 * @return {jQuery.DeferredObject}
 */
jQuery.deferred.prototype.rejectWith = function(context, args) {};

/**
 * @param {Object} context
 * @param {Array.<*>=} args
 * @return {jQuery.DeferredObject}
 */
jQuery.Deferred.prototype.rejectWith = function(context, args) {};

/**
 * @param {...*} var_args
 * @return {jQuery.DeferredObject}
 */
jQuery.deferred.prototype.resolve = function(var_args) {};

/**
 * @param {...*} var_args
 * @return {jQuery.DeferredObject}
 */
jQuery.Deferred.prototype.resolve = function(var_args) {};

/**
 * @param {Object} context
 * @param {Array.<*>=} args
 * @return {jQuery.DeferredObject}
 */
jQuery.deferred.prototype.resolveWith = function(context, args) {};

/**
 * @param {Object} context
 * @param {Array.<*>=} args
 * @return {jQuery.DeferredObject}
 */
jQuery.Deferred.prototype.resolveWith = function(context, args) {};

/**
 * @param {function()} doneCallbacks
 * @param {function()} failCallbacks
 * @return {jQuery.DeferredObject}
 */
jQuery.deferred.prototype.then = function(doneCallbacks, failCallbacks) {};

/**
 * @param {function()} doneCallbacks
 * @param {function()} failCallbacks
 * @return {jQuery.DeferredObject}
 */
jQuery.Deferred.prototype.then = function(doneCallbacks, failCallbacks) {};

/**
 * @interface
 * @param {function()=} opt_fn
 * @private
 * @see http://api.jquery.com/category/deferred-object/
 */
jQuery.DeferredObject = function (opt_fn) {};

/**
 * @param {function()} doneCallbacks
 * @return {jQuery.DeferredObject}
 */
jQuery.DeferredObject.prototype.done = function(doneCallbacks) {};

/**
 * @param {function()} failCallbacks
 * @return {jQuery.DeferredObject}
 */
jQuery.DeferredObject.prototype.fail = function(failCallbacks) {};

/**
 * @return {boolean}
 * @nosideeffects
 */
jQuery.DeferredObject.prototype.isRejected = function() {};

/**
 * @return {boolean}
 * @nosideeffects
 */
jQuery.DeferredObject.prototype.isResolved = function() {};

/** @return {jQuery.Promise} */
jQuery.DeferredObject.prototype.promise = function() {};

/**
 * @param {...*} var_args
 * @return {jQuery.DeferredObject}
 */
jQuery.DeferredObject.prototype.reject = function(var_args) {};

/**
 * @param {Object} context
 * @param {Array.<*>=} args
 * @return {jQuery.DeferredObject}
 */
jQuery.DeferredObject.prototype.rejectWith = function(context, args) {};

/**
 * @param {...*} var_args
 * @return {jQuery.DeferredObject}
 */
jQuery.DeferredObject.prototype.resolve = function(var_args) {};

/**
 * @param {Object} context
 * @param {Array.<*>=} args
 * @return {jQuery.DeferredObject}
 */
jQuery.DeferredObject.prototype.resolveWith = function(context, args) {};

/**
 * @param {function()} doneCallbacks
 * @param {function()} failCallbacks
 * @return {jQuery.DeferredObject}
 */
jQuery.DeferredObject.prototype.then
    = function(doneCallbacks, failCallbacks) {};

/**
 * @param {Element} elem
 * @param {string=} queueName
 * @return {jQueryObject}
 */
jQuery.dequeue = function(elem, queueName) {};

/**
 * @param {Object} collection
 * @param {function(number,*)} callback
 * @return {Object}
 */
jQuery.each = function(collection, callback) {};

/** @param {string} message */
jQuery.error = function(message) {};

/**
 * @constructor
 * @param {string} eventType
 */
jQuery.event = function(eventType) {};

/** @type {Element} */
jQuery.event.prototype.currentTarget;

/** @type {*} */
jQuery.event.prototype.data;

/**
 * @return {boolean}
 * @nosideeffects
 */
jQuery.event.prototype.isDefaultPrevented = function() {};

/**
 * @return {boolean}
 * @nosideeffects
 */
jQuery.event.prototype.isImmediatePropagationStopped = function() {};

/**
 * @return {boolean}
 * @nosideeffects
 */
jQuery.event.prototype.isPropagationStopped = function() {};

/** @type {string} */
jQuery.event.prototype.namespace;

/** @type {number} */
jQuery.event.prototype.pageX;

/** @type {number} */
jQuery.event.prototype.pageY;

/** @return {undefined} */
jQuery.event.prototype.preventDefault = function() {};

/** @type {Element} */
jQuery.event.prototype.relatedTarget;

/** @type {*} */
jQuery.event.prototype.result;

/** @return {undefined} */
jQuery.event.prototype.stopImmediatePropagation = function() {};

/** @return {undefined} */
jQuery.event.prototype.stopPropagation = function() {};

/** @type {Element} */
jQuery.event.prototype.target;

/** @type {number} */
jQuery.event.prototype.timeStamp;

/** @type {string} */
jQuery.event.prototype.type;

/** @type {number} */
jQuery.event.prototype.which;

/**
 * @param {(Object|boolean)} arg1
 * @param {...*} var_args
 * @return {Object}
 */
jQuery.extend = function(arg1, var_args) {};

/** @see http://docs.jquery.com/Plugins/Authoring */
jQuery.fn;

/** @const */
jQuery.fx = {};

/** @type {number} */
jQuery.fx.interval;

/** @type {boolean} */
jQuery.fx.off;

/**
 * @param {string} url
 * @param {(Object.<string,*>|string)=} data
 * @param {function(string,string,jQuery.jqXHR)=} success
 * @param {string=} dataType
 * @return {jQuery.jqXHR}
 */
jQuery.get = function(url, data, success, dataType) {};

/**
 * @param {string} url
 * @param {Object.<string,*>=} data
 * @param {function(string,string,jQuery.jqXHR)=} success
 * @return {jQuery.jqXHR}
 */
jQuery.getJSON = function(url, data, success) {};

/**
 * @param {string} url
 * @param {function(string,string)=} success
 * @return {XMLHttpRequest}
 */
jQuery.getScript = function(url, success) {};

/** @param {string} code */
jQuery.globalEval = function(code) {};

/**
 * @param {Array.<*>} arr
 * @param {function(*,number)} fnc
 * @param {boolean=} invert
 * @return {Array.<*>}
 */
jQuery.grep = function(arr, fnc, invert) {};

/**
 * @param {Element} elem
 * @return {boolean}
 * @nosideeffects
 */
jQuery.hasData = function(elem) {};

/**
 * @param {*} value
 * @param {Array.<*>} arr
 * @return {number}
 * @nosideeffects
 */
jQuery.inArray = function(value, arr) {};

/**
 * @param {*} obj
 * @return {boolean}
 * @nosideeffects
 */
jQuery.isArray = function(obj) {};

/**
 * @param {Object} obj
 * @return {boolean}
 * @nosideeffects
 */
jQuery.isEmptyObject = function(obj) {};

/**
 * @param {*} obj
 * @return {boolean}
 * @nosideeffects
 */
jQuery.isFunction = function(obj) {};

/**
 * @param {Object} obj
 * @return {boolean}
 * @nosideeffects
 */
jQuery.isPlainObject = function(obj) {};

/**
 * @param {*} obj
 * @return {boolean}
 * @nosideeffects
 */
jQuery.isWindow = function(obj) {};

/**
 * @param {Element} node
 * @return {boolean}
 * @nosideeffects
 */
jQuery.isXMLDoc = function(node) {};

/**
 * @constructor
 * @extends {XMLHttpRequest}
 * @implements {jQuery.Promise}
 * @private
 * @see http://api.jquery.com/jQuery.ajax/#jqXHR
 */
jQuery.jqXHR = function () {};

/**
 * @param {function()} callback
 * @return {jQuery.jqXHR}
*/
jQuery.jqXHR.prototype.complete = function (callback) {};

/**
 * @param {function()} doneCallbacks
 * @return {jQuery.Promise}
 */
jQuery.jqXHR.prototype.done = function(doneCallbacks) {};


/**
 * @param {function()} callback
 * @return {jQuery.jqXHR}
*/
jQuery.jqXHR.prototype.error = function (callback) {};

/**
 * @param {function()} failCallbacks
 * @return {jQuery.Promise}
 */
jQuery.jqXHR.prototype.fail = function(failCallbacks) {};

/**
 * @return {boolean}
 * @nosideeffects
 */
jQuery.jqXHR.prototype.isRejected = function() {};

/**
 * @return {boolean}
 * @nosideeffects
 */
jQuery.jqXHR.prototype.isResolved = function() {};

/**
 * @override
 * @deprecated
 */
jQuery.jqXHR.prototype.onreadystatechange = function (callback) {};

/**
 * @param {function()} callback
 * @return {jQuery.jqXHR}
*/
jQuery.jqXHR.prototype.success = function (callback) {};

/**
 * @param {function()} doneCallbacks
 * @param {function()} failCallbacks
 * @return {jQuery.Promise}
 */
jQuery.jqXHR.prototype.then = function(doneCallbacks, failCallbacks) {};

/**
 * @param {*} obj
 * @return {Array.<*>}
 */
jQuery.makeArray = function(obj) {};

/**
 * @param {Array.<*>} arr
 * @param {function(*,number)} callback
 * @return {Array.<*>}
 */
jQuery.map = function(arr, callback) {};

/**
 * @param {Array.<*>} first
 * @param {Array.<*>} second
 * @return {Array.<*>}
 */
jQuery.merge = function(first, second) {};

/**
 * @param {boolean=} removeAll
 * @return {Object}
 */
jQuery.noConflict = function(removeAll) {};

/**
 * @return {function()}
 * @nosideeffects
 */
jQuery.noop = function() {};

/**
 * @return {number}
 * @nosideeffects
 */
jQuery.now = function() {};

/**
 * @param {(Object.<string, *>|Array.<Object.<string, *>>)} obj
 * @param {boolean=} traditional
 * @return {string}
 */
jQuery.param = function(obj, traditional) {};

/**
 * @param {string} json
 * @return {Object.<string, *>}
 */
jQuery.parseJSON = function(json) {};

/**
 * @param {string} data
 * @return {Document}
 */
jQuery.parseXML = function(data) {};

/**
 * @param {string} url
 * @param {(Object.<string,*>|string)=} data
 * @param {function(string,string,jQuery.jqXHR)=} success
 * @param {string=} dataType
 * @return {jQuery.jqXHR}
 */
jQuery.post = function(url, data, success, dataType) {};

/**
 * @interface
 * @private
 * @see http://api.jquery.com/Types/#Promise
 */
jQuery.Promise = function () {};

/**
 * @param {function()} doneCallbacks
 * @return {jQuery.Promise}
 */
jQuery.Promise.prototype.done = function(doneCallbacks) {};

/**
 * @param {function()} failCallbacks
 * @return {jQuery.Promise}
 */
jQuery.Promise.prototype.fail = function(failCallbacks) {};

/**
 * @return {boolean}
 * @nosideeffects
 */
jQuery.Promise.prototype.isRejected = function() {};

/**
 * @return {boolean}
 * @nosideeffects
 */
jQuery.Promise.prototype.isResolved = function() {};

/**
 * @param {function()} doneCallbacks
 * @param {function()} failCallbacks
 * @return {jQuery.Promise}
 */
jQuery.Promise.prototype.then = function(doneCallbacks, failCallbacks) {};

/**
 * @param {(function()|Object)} arg1
 * @param {(Object|string)} arg2
 * @return {function()}
 */
jQuery.proxy = function(arg1, arg2) {};

/**
 * @param {Element} elem
 * @param {string=} queueName
 * @param {(Array.<function(this:jQueryObject)>|function())=} arg3
 * @return {(Array.<Element>|jQueryObject)}
 */
jQuery.queue = function(elem, queueName, arg3) {};

/**
 * @param {Element} elem
 * @param {string=} name
 * @return {jQueryObject}
 */
jQuery.removeData = function(elem, name) {};

/**
 * @return {jQueryObject}
 * @nosideeffects
 */
jQuery.sub = function() {};

/** @type {Object.<string, *>} */
jQuery.support;

/** @type {boolean} */
jQuery.support.boxModel;

/** @type {boolean} */
jQuery.support.changeBubbles;

/** @type {boolean} */
jQuery.support.cssFloat;

/** @type {boolean} */
jQuery.support.hrefNormalized;

/** @type {boolean} */
jQuery.support.htmlSerialize;

/** @type {boolean} */
jQuery.support.leadingWhitespace;

/** @type {boolean} */
jQuery.support.noCloneEvent;

/** @type {boolean} */
jQuery.support.opacity;

/** @type {boolean} */
jQuery.support.scriptEval;

/** @type {boolean} */
jQuery.support.style;

/** @type {boolean} */
jQuery.support.submitBubbles;

/** @type {boolean} */
jQuery.support.tbody;

/**
 * @param {string} str
 * @return {string}
 * @nosideeffects
 */
jQuery.trim = function(str) {};

/**
 * @param {*} obj
 * @return {string}
 * @nosideeffects
 */
jQuery.type = function(obj) {};

/**
 * @param {Array.<Element>} arr
 * @return {Array.<Element>}
 */
jQuery.unique = function(arr) {};

/**
 * @param {jQuery.DeferredObject} deferreds
 * @return {jQuery.Promise}
 */
jQuery.when = function(deferreds) {};

/**
 * @constructor
 * @private
 */
function jQueryObject() {};

/**
 * @param {(Object|boolean)} arg1
 * @param {...*} var_args
 * @return {Object}
 */
jQueryObject.prototype.extend = function(arg1, var_args) {};

/**
 * @param {(jQuerySelector|Array.<Element>|string)} arg1
 * @param {Element=} context
 * @return {jQueryObject}
 * @nosideeffects
 */
jQueryObject.prototype.add = function(arg1, context) {};

/**
 * @param {(string|function(number,string))} arg1
 * @return {jQueryObject}
 */
jQueryObject.prototype.addClass = function(arg1) {};

/**
 * @param {(string|Element|jQueryObject|function(number))} arg1
 * @param {(string|Element|Array.<Element>|jQueryObject)=} content
 * @return {jQueryObject}
 */
jQueryObject.prototype.after = function(arg1, content) {};

/**
 * @param {function(jQuery.event,XMLHttpRequest,Object.<string, *>)} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.ajaxComplete = function(handler) {};

/**
 * @param {function(jQuery.event,XMLHttpRequest,Object.<string, *>,*)} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.ajaxError = function(handler) {};

/**
 * @param {function(jQuery.event,XMLHttpRequest,Object.<string, *>)} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.ajaxSend = function(handler) {};

/**
 * @param {function()} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.ajaxStart = function(handler) {};

/**
 * @param {function()} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.ajaxStop = function(handler) {};

/**
 * @param {function(jQuery.event,XMLHttpRequest,Object.<string, *>)} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.ajaxSuccess = function(handler) {};

/**
 * @return {jQueryObject}
 * @nosideeffects
 */
jQueryObject.prototype.andSelf = function() {};

/**
 * @param {Object.<string,*>} properties
 * @param {(string|number|Object.<string,*>)=} arg2
 * @param {string=} easing
 * @param {function()=} complete
 * @return {jQueryObject}
 */
jQueryObject.prototype.animate
    = function(properties, arg2, easing, complete) {};

/**
 * @param {(string|Element|jQueryObject|function(number,string))} arg1
 * @param {(string|Element|Array.<Element>|jQueryObject)=} content
 * @return {jQueryObject}
 */
jQueryObject.prototype.append = function(arg1, content) {};

/**
 * @param {(jQuerySelector|Element|jQueryObject)} target
 * @return {jQueryObject}
 */
jQueryObject.prototype.appendTo = function(target) {};

/**
 * @param {(string|Object.<string,*>)} arg1
 * @param {(string|number|function(number,string))=} arg2
 * @return {(string|jQueryObject)}
 */
jQueryObject.prototype.attr = function(arg1, arg2) {};

/**
 * @param {(string|Element|jQueryObject|function())} arg1
 * @param {(string|Element|Array.<Element>|jQueryObject)=} content
 * @return {jQueryObject}
 */
jQueryObject.prototype.before = function(arg1, content) {};

/**
 * @param {(string|Object.<string, function(jQuery.event=)>)} arg1
 * @param {Object.<string, *>=} eventData
 * @param {(function(jQuery.event)|boolean)=} arg3
 * @return {jQueryObject}
 */
jQueryObject.prototype.bind = function(arg1, eventData, arg3) {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.blur = function(arg1, handler) {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.change = function(arg1, handler) {};

/**
 * @param {jQuerySelector=} selector
 * @return {jQueryObject}
 * @nosideeffects
 */
jQueryObject.prototype.children = function(selector) {};

/**
 * @param {string=} queueName
 * @return {jQueryObject}
 */
jQueryObject.prototype.clearQueue = function(queueName) {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.click = function(arg1, handler) {};

/**
 * @param {boolean=} withDataAndEvents
 * @param {boolean=} deepWithDataAndEvents
 * @return {jQueryObject}
 */
jQueryObject.prototype.clone
    = function(withDataAndEvents, deepWithDataAndEvents) {};

/**
 * @param {(jQuerySelector|string|Array.<string>)} arg1
 * @param {Element=} context
 * @return {(jQueryObject|Array.<Element>)}
 * @nosideeffects
 */
jQueryObject.prototype.closest = function(arg1, context) {};

/**
 * @return {jQueryObject}
 * @nosideeffects
 */
jQueryObject.prototype.contents = function() {};

/** @type {Element} */
jQueryObject.prototype.context;

/**
 * @param {(string|Object.<string,*>)} arg1
 * @param {(string|number|function(number,*))=} arg2
 * @return {(string|jQueryObject)}
 */
jQueryObject.prototype.css = function(arg1, arg2) {};

/**
 * @param {(string|Object.<string, *>)=} arg1
 * @param {*=} value
 * @return {*}
 */
jQueryObject.prototype.data = function(arg1, value) {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.dblclick = function(arg1, handler) {};

/**
 * @param {number} duration
 * @param {string=} queueName
 * @return {jQueryObject}
 */
jQueryObject.prototype.delay = function(duration, queueName) {};

/**
 * @param {string} selector
 * @param {string} eventType
 * @param {(function()|Object.<string, *>)} arg3
 * @param {function()=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.delegate
    = function(selector, eventType, arg3, handler) {};

/**
 * @param {string=} queueName
 * @return {jQueryObject}
 */
jQueryObject.prototype.dequeue = function(queueName) {};

/**
 * @param {jQuerySelector=} selector
 * @return {jQueryObject}
 */
jQueryObject.prototype.detach = function(selector) {};

/**
 * @param {string=} eventType
 * @param {string=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.die = function(eventType, handler) {};

/**
 * @param {function(number,Element)} fnc
 * @return {jQueryObject}
 */
jQueryObject.prototype.each = function(fnc) {};

/** @return {jQueryObject} */
jQueryObject.prototype.empty = function() {};

/**
 * @return {jQueryObject}
 * @nosideeffects
 */
jQueryObject.prototype.end = function() {};

/**
 * @param {number} arg1
 * @return {jQueryObject}
 * @nosideeffects
 */
jQueryObject.prototype.eq = function(arg1) {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.error = function(arg1, handler) {};

/**
 * @param {(string|number)=} duration
 * @param {(function()|string)=} arg2
 * @param {function()=} callback
 * @return {jQueryObject}
 */
jQueryObject.prototype.fadeIn = function(duration, arg2, callback) {};

/**
 * @param {(string|number)=} duration
 * @param {(function()|string)=} arg2
 * @param {function()=} callback
 * @return {jQueryObject}
 */
jQueryObject.prototype.fadeOut = function(duration, arg2, callback) {};

/**
 * @param {(string|number)} duration
 * @param {number} opacity
 * @param {(function()|string)=} arg3
 * @param {function()=} callback
 * @return {jQueryObject}
 */
jQueryObject.prototype.fadeTo = function(duration, opacity, arg3, callback) {};

/**
 * @param {(string|number)=} duration
 * @param {string=} easing
 * @param {function()=} callback
 * @return {jQueryObject}
 */
jQueryObject.prototype.fadeToggle = function(duration, easing, callback) {};

/**
 * @param {(jQuerySelector|function(number)|Element|jQueryObject)} arg1
 * @return {jQueryObject}
 */
jQueryObject.prototype.filter = function(arg1) {};

/**
 * @param {jQuerySelector} selector
 * @return {jQueryObject}
 * @nosideeffects
 */
jQueryObject.prototype.find = function(selector) {};

/**
 * @return {jQueryObject}
 * @nosideeffects
 */
jQueryObject.prototype.first = function() {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.focus = function(arg1, handler) {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.focusin = function(arg1, handler) {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.focusout = function(arg1, handler) {};

/**
 * @param {number=} index
 * @return {(Element|Array.<Element>)}
 * @nosideeffects
 */
jQueryObject.prototype.get = function(index) {};

/**
 * @param {(string|Element)} arg1
 * @return {jQueryObject}
 * @nosideeffects
 */
jQueryObject.prototype.has = function(arg1) {};

/**
 * @param {string} className
 * @return {boolean}
 * @nosideeffects
 */
jQueryObject.prototype.hasClass = function(className) {};

/**
 * @param {(string|number|function(number,number))=} arg1
 * @return {(number|jQueryObject)}
 */
jQueryObject.prototype.height = function(arg1) {};

/**
 * @param {(string|number)=} duration
 * @param {(function()|string)=} arg2
 * @param {function()=} callback
 * @return {jQueryObject}
 */
jQueryObject.prototype.hide = function(duration, arg2, callback) {};

/**
 * @param {function(jQuery.event)} arg1
 * @param {function(jQuery.event)=} handlerOut
 * @return {jQueryObject}
 */
jQueryObject.prototype.hover = function(arg1, handlerOut) {};

/**
 * @param {(string|function(number,string))=} arg1
 * @return {(string|jQueryObject)}
 */
jQueryObject.prototype.html = function(arg1) {};

/**
 * @param {(jQuerySelector|Element|jQueryObject)=} arg1
 * @return {number}
 */
jQueryObject.prototype.index = function(arg1) {};

/**
 * @return {number}
 * @nosideeffects
 */
jQueryObject.prototype.innerHeight = function() {};

/**
 * @return {number}
 * @nosideeffects
 */
jQueryObject.prototype.innerWidth = function() {};

/**
 * @param {(jQuerySelector|Element|jQueryObject)} target
 * @return {jQueryObject}
 */
jQueryObject.prototype.insertAfter = function(target) {};

/**
 * @param {(jQuerySelector|Element|jQueryObject)} target
 * @return {jQueryObject}
 */
jQueryObject.prototype.insertBefore = function(target) {};

/**
 * @param {jQuerySelector} selector
 * @return {boolean}
 * @nosideeffects
 */
jQueryObject.prototype.is = function(selector) {};

/** @type {string} */
jQueryObject.prototype.jquery;

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.keydown = function(arg1, handler) {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.keypress = function(arg1, handler) {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.keyup = function(arg1, handler) {};

/**
 * @return {jQueryObject}
 * @nosideeffects
 */
jQueryObject.prototype.last = function() {};

/** @type {number} */
jQueryObject.prototype.length;

/**
 * @param {(string|Object.<string, function(jQuery.event=)>)} arg1
 * @param {(function()|Object.<string, *>)=} arg2
 * @param {function()=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.live = function(arg1, arg2, handler) {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>|string)=} arg1
 * @param {(function(jQuery.event)|Object.<string,*>|string)=} arg2
 * @param {function(string,string,XMLHttpRequest)=} complete
 * @return {jQueryObject}
 */
jQueryObject.prototype.load = function(arg1, arg2, complete) {};

/**
 * @param {function(number,Element)} callback
 * @return {jQueryObject}
 */
jQueryObject.prototype.map = function(callback) {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.mousedown = function(arg1, handler) {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.mouseenter = function(arg1, handler) {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.mouseleave = function(arg1, handler) {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.mousemove = function(arg1, handler) {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.mouseout = function(arg1, handler) {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.mouseover = function(arg1, handler) {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.mouseup = function(arg1, handler) {};

/**
 * @param {jQuerySelector=} selector
 * @return {jQueryObject}
 * @nosideeffects
 */
jQueryObject.prototype.next = function(selector) {};

/**
 * @param {string=} selector
 * @return {jQueryObject}
 * @nosideeffects
 */
jQueryObject.prototype.nextAll = function(selector) {};

/**
 * @param {jQuerySelector=} selector
 * @return {jQueryObject}
 * @nosideeffects
 */
jQueryObject.prototype.nextUntil = function(selector) {};

/**
 * @param {(jQuerySelector|Array.<Element>|function(number))} arg1
 * @return {jQueryObject}
 */
jQueryObject.prototype.not = function(arg1) {};

/**
 * @param {({left:number,top:number}|
 *     function(number,{top:number,left:number}))=} arg1
 * @return {({left:number,top:number}|jQueryObject)}
 */
jQueryObject.prototype.offset = function(arg1) {};

/**
 * @return {jQueryObject}
 * @nosideeffects
 */
jQueryObject.prototype.offsetParent = function() {};

/**
 * @param {string} eventType
 * @param {Object.<string, *>=} eventData
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.one = function(eventType, eventData, handler) {};

/**
 * @param {boolean=} includeMargin
 * @return {number}
 * @nosideeffects
 */
jQueryObject.prototype.outerHeight = function(includeMargin) {};

/**
 * @param {boolean=} includeMargin
 * @return {number}
 * @nosideeffects
 */
jQueryObject.prototype.outerWidth = function(includeMargin) {};

/**
 * @param {jQuerySelector=} selector
 * @return {jQueryObject}
 * @nosideeffects
 */
jQueryObject.prototype.parent = function(selector) {};

/**
 * @param {jQuerySelector=} selector
 * @return {jQueryObject}
 * @nosideeffects
 */
jQueryObject.prototype.parents = function(selector) {};

/**
 * @param {jQuerySelector=} selector
 * @return {jQueryObject}
 * @nosideeffects
 */
jQueryObject.prototype.parentsUntil = function(selector) {};

/**
 * @return {{left:number,top:number}}
 * @nosideeffects
 */
jQueryObject.prototype.position = function() {};

/**
 * @param {(string|Element|jQueryObject|function(number,string))} arg1
 * @param {(string|Element|jQueryObject)=} content
 * @return {jQueryObject}
 */
jQueryObject.prototype.prepend = function(arg1, content) {};

/**
 * @param {(jQuerySelector|Element|jQueryObject)} target
 * @return {jQueryObject}
 */
jQueryObject.prototype.prependTo = function(target) {};

/**
 * @param {jQuerySelector=} selector
 * @return {jQueryObject}
 * @nosideeffects
 */
jQueryObject.prototype.prev = function(selector) {};

/**
 * @param {jQuerySelector=} selector
 * @return {jQueryObject}
 * @nosideeffects
 */
jQueryObject.prototype.prevAll = function(selector) {};

/**
 * @param {jQuerySelector=} selector
 * @return {jQueryObject}
 * @nosideeffects
 */
jQueryObject.prototype.prevUntil = function(selector) {};

/**
 * @param {Array.<Element>} elements
 * @param {string=} name
 * @param {Array.<*>=} args
 * @return {jQueryObject}
 */
jQueryObject.prototype.pushStack = function(elements, name, args) {};

/**
 * @param {string=} queueName
 * @param {(Array.<function(this:jQueryObject)>|function(function()))=} arg2
 * @return {(Array.<Element>|jQueryObject)}
 */
jQueryObject.prototype.queue = function(queueName, arg2) {};

/**
 * @param {function()} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.ready = function(handler) {};

/**
 * @param {string=} selector
 * @return {jQueryObject}
 */
jQueryObject.prototype.remove = function(selector) {};

/**
 * @param {string} attributeName
 * @return {jQueryObject}
 */
jQueryObject.prototype.removeAttr = function(attributeName) {};

/**
 * @param {(string|function(number,string))=} arg1
 * @return {jQueryObject}
 */
jQueryObject.prototype.removeClass = function(arg1) {};

/**
 * @param {string=} name
 * @return {jQueryObject}
 */
jQueryObject.prototype.removeData = function(name) {};

/**
 * @param {jQuerySelector} target
 * @return {jQueryObject}
 */
jQueryObject.prototype.replaceAll = function(target) {};

/**
 * @param {(string|Element|jQueryObject|function())} arg1
 * @return {jQueryObject}
 */
jQueryObject.prototype.replaceWith = function(arg1) {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.resize = function(arg1, handler) {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.scroll = function(arg1, handler) {};

/**
 * @param {number=} value
 * @return {(number|jQueryObject)}
 */
jQueryObject.prototype.scrollLeft = function(value) {};

/**
 * @param {number=} value
 * @return {(number|jQueryObject)}
 */
jQueryObject.prototype.scrollTop = function(value) {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.select = function(arg1, handler) {};

/** @type {string} */
jQueryObject.prototype.selector;

/**
 * @return {string}
 * @nosideeffects
 */
jQueryObject.prototype.serialize = function() {};

/**
 * @return {Array.<Object.<string, *>>}
 * @nosideeffects
 */
jQueryObject.prototype.serializeArray = function() {};

/**
 * @param {(string|number)=} duration
 * @param {(function()|string)=} arg2
 * @param {function()=} callback
 * @return {jQueryObject}
 */
jQueryObject.prototype.show = function(duration, arg2, callback) {};

/**
 * @param {jQuerySelector=} selector
 * @return {jQueryObject}
 * @nosideeffects
 */
jQueryObject.prototype.siblings = function(selector) {};

/**
 * @return {number}
 * @nosideeffects
 */
jQueryObject.prototype.size = function() {};

/**
 * @param {number} start
 * @param {number=} end
 * @return {jQueryObject}
 * @nosideeffects
 */
jQueryObject.prototype.slice = function(start, end) {};

/**
 * @param {(string|number)=} duration
 * @param {(function()|string)=} arg2
 * @param {function()=} callback
 * @return {jQueryObject}
 */
jQueryObject.prototype.slideDown = function(duration, arg2, callback) {};

/**
 * @param {(string|number)=} duration
 * @param {(function()|string)=} arg2
 * @param {function()=} callback
 * @return {jQueryObject}
 */
jQueryObject.prototype.slideToggle = function(duration, arg2, callback) {};

/**
 * @param {(string|number)=} duration
 * @param {(function()|string)=} arg2
 * @param {function()=} callback
 * @return {jQueryObject}
 */
jQueryObject.prototype.slideUp = function(duration, arg2, callback) {};

/**
 * @param {boolean=} clearQueue
 * @param {boolean=} jumpToEnd
 * @return {jQueryObject}
 */
jQueryObject.prototype.stop = function(clearQueue, jumpToEnd) {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.submit = function(arg1, handler) {};

/**
 * @param {(string|function(number,string))=} arg1
 * @return {(string|jQueryObject)}
 */
jQueryObject.prototype.text = function(arg1) {};

/**
 * @return {Array.<Element>}
 * @nosideeffects
 */
jQueryObject.prototype.toArray = function() {};

/**
 * @param {(function(jQuery.event)|string|number|boolean)=} arg1
 * @param {(function(jQuery.event)|string)=} arg2
 * @param {function(jQuery.event)=} arg3
 * @return {jQueryObject}
 */
jQueryObject.prototype.toggle = function(arg1, arg2, arg3) {};

/**
 * @param {(string|function(number,string))} arg1
 * @param {boolean=} flag
 * @return {jQueryObject}
 */
jQueryObject.prototype.toggleClass = function(arg1, flag) {};

/**
 * @param {(string|jQuery.event)} arg1
 * @param {Array.<*>=} extraParameters
 * @return {jQueryObject}
 */
jQueryObject.prototype.trigger = function(arg1, extraParameters) {};

/**
 * @param {string} eventType
 * @param {Array.<*>} extraParameters
 * @return {*}
 */
jQueryObject.prototype.triggerHandler = function(eventType, extraParameters) {};

/**
 * @param {(string|jQuery.event)=} arg1
 * @param {(function(jQuery.event)|boolean)=} arg2
 * @return {jQueryObject}
 */
jQueryObject.prototype.unbind = function(arg1, arg2) {};

/**
 * @param {string=} selector
 * @param {string=} eventType
 * @param {function()=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.undelegate = function(selector, eventType, handler) {};

/**
 * @param {(function(jQuery.event)|Object.<string, *>)=} arg1
 * @param {function(jQuery.event)=} handler
 * @return {jQueryObject}
 */
jQueryObject.prototype.unload = function(arg1, handler) {};

/** @return {jQueryObject} */
jQueryObject.prototype.unwrap = function() {};

/**
 * @param {(string|function(number,*))=} arg1
 * @return {(string|Array.<string>|jQueryObject)}
 */
jQueryObject.prototype.val = function(arg1) {};

/**
 * @param {(string|number|function(number,number))=} arg1
 * @return {(number|jQueryObject)}
 */
jQueryObject.prototype.width = function(arg1) {};

/**
 * @param {(string|jQuerySelector|Element|jQueryObject|function())} arg1
 * @return {jQueryObject}
 */
jQueryObject.prototype.wrap = function(arg1) {};

/**
 * @param {(string|jQuerySelector|Element|jQueryObject)} wrappingElement
 * @return {jQueryObject}
 */
jQueryObject.prototype.wrapAll = function(wrappingElement) {};

/**
 * @param {(string|function())} arg1
 * @return {jQueryObject}
 */
jQueryObject.prototype.wrapInner = function(arg1) {};
