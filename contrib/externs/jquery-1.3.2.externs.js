/*
 * Copyright 2009 Google Inc.
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
 * @fileoverview Externs for jQuery 1.3.2.
 * The externs defined here are in the order of the jQuery documentation pages.
 * Note that some functions use different return types depending on the number
 * of parameters passed in. In this cases, you may need to annotate the type
 * of the result in your code, so the JSCompiler understands which type you're
 * expecting. For example:
 *    <code>var elt = /** @type {Element} * / (foo.get(0));</code>
 * @see http://docs.jquery.com/
 */

/**
 * @param {string|Node|Element|Array.<Element>|Function|Window} arg
 * @param {(jQueryObject|Document)=} opt_arg
 * @return {jQueryObject}
 */
var $ = function(arg, opt_arg) {};

var jQuery = {};

/** @constructor */
function jQueryObject() {}


// http://docs.jquery.com/Core - Object accessors

/**
 * @param {Function} callback
 * @return {jQueryObject}
 */
jQueryObject.prototype.each = function(callback) {};

/** @return {number} */
jQueryObject.prototype.size = function() {};

/** @type {number} */
jQueryObject.prototype.length;

/** @return {string} */
jQueryObject.prototype.selector = function() {};

/** @return {Element} */
jQueryObject.prototype.context = function() {};

/**
 * @param {number=} opt_index
 * @return {Element|Array.<Element>}
 */
jQueryObject.prototype.get = function(opt_index) {};

/**
 * @param {Element|jQueryObject} subject
 * @return {number}
 */
jQueryObject.prototype.index = function(subject) {};


// http://docs.jquery.com/Core - Data

/**
 * @param {string} name
 * @param {string=} opt_value
 * @return {*}
 */
jQueryObject.prototype.data = function(name, opt_value) {};

/**
 * @param {string} name
 * @return {jQueryObject}
 */
jQueryObject.prototype.removeData = function(name) {};

/**
 * @param {(string|Function|Array.<Function>)=} opt_arg1
 * @param {(Function|Array.<Function>)=} opt_arg2
 * @return {Array.<Function>|jQueryObject}
 */
jQueryObject.prototype.queue = function(opt_arg1, opt_arg2) {};

/**
 * @param {string=} opt_name
 * @return {jQueryObject}
 */
jQueryObject.prototype.dequeue = function(opt_name) {};


// http://docs.jquery.com/Attributes - Attr

/**
 * @param {string|Object} nameOrProperties
 * @param {*=} opt_value
 * @return {Object|jQueryObject}
 */
jQueryObject.prototype.attr = function(nameOrProperties, opt_value) {};

/**
 * @param {string} name
 * @return {jQueryObject}
 */
jQueryObject.prototype.removeAttr = function(name) {};


// http://docs.jquery.com/Attributes - Class

/**
 * @param {string} klass
 * @return {jQueryObject}
 */
jQueryObject.prototype.addClass = function(klass) {};

/**
 * @param {string} klass
 * @return {boolean}
 */
jQueryObject.prototype.hasClass = function(klass) {};

/**
 * @param {string=} opt_klass
 * @return {jQueryObject}
 */
jQueryObject.prototype.removeClass = function(opt_klass) {};

/**
 * @param {string} klass
 * @param {boolean=} opt_switch
 * @return {jQueryObject}
 */
jQueryObject.prototype.toggleClass = function(klass, opt_switch) {};


// http://docs.jquery.com/Attributes - HTML, Text, Value

/**
 * @param {string} opt_val
 * @return {string|jQueryObject}
 */
jQueryObject.prototype.html = function(opt_val) {};

/**
 * @param {string=} opt_val
 * @return {string|jQueryObject}
 */
jQueryObject.prototype.text = function(opt_val) {};

/**
 * @param {string|Array.<string>} opt_val
 * @return {string|Array|jQueryObject}
 */
jQueryObject.prototype.val = function(opt_val) {};


// http://docs.jquery.com/Traversing - Filtering

/**
 * @param {number} index
 * @return {jQueryObject}
 */
jQueryObject.prototype.eq = function(index) {};

/**
 * @param {string|Function} arg
 * @return {jQueryObject}
 */
jQueryObject.prototype.filter = function(arg) {};

/**
 * @param {string} expr
 * @return {boolean}
 */
jQueryObject.prototype.is = function(expr) {};

/**
 * @param {Function} callback
 * @return {jQueryObject}
 */
jQueryObject.prototype.map = function(callback) {};

/**
 * @param {string} expr
 * @return {jQueryObject}
 */
jQueryObject.prototype.not = function(expr) {};

/**
 * @param {number} start
 * @param {number=} opt_end
 * @return {jQueryObject}
 */
jQueryObject.prototype.slice = function(start, opt_end) {};


// http://docs.jquery.com/Traversing - Finding, Chaining

/**
 * @param {string|Element|Array.<Element>} expr
 * @return {jQueryObject}
 */
jQueryObject.prototype.add = function(expr) {};

/**
 * @param {string=} opt_expr
 * @return {jQueryObject}
 */
jQueryObject.prototype.children = function(opt_expr) {};

/**
 * @param {string} expr
 * @return {jQueryObject}
 */
jQueryObject.prototype.closest = function(expr) {};

/**
 * @return {jQueryObject}
 */
jQueryObject.prototype.contents = function() {};

/**
 * @param {string} expr
 * @return {jQueryObject}
 */
jQueryObject.prototype.find = function(expr) {};

/**
 * @param {string=} opt_expr
 * @return {jQueryObject}
 */
jQueryObject.prototype.next = function(opt_expr) {};

/**
 * @param {string=} opt_expr
 * @return {jQueryObject}
 */
jQueryObject.prototype.nextAll = function(opt_expr) {};

/**
 * @return {jQueryObject}
 */
jQueryObject.prototype.offsetParent = function() {};

/**
 * @param {string=} opt_expr
 * @return {jQueryObject}
 */
jQueryObject.prototype.parent = function(opt_expr) {};

/**
 * @param {string=} opt_expr
 * @return {jQueryObject}
 */
jQueryObject.prototype.parents = function(opt_expr) {};

/**
 * @param {string=} opt_expr
 * @return {jQueryObject}
 */
jQueryObject.prototype.prev = function(opt_expr) {};

/**
 * @param {string=} opt_expr
 * @return {jQueryObject}
 */
jQueryObject.prototype.prevAll = function(opt_expr) {};

/**
 * @param {string=} opt_expr
 * @return {jQueryObject}
 */
jQueryObject.prototype.siblings = function(opt_expr) {};

/**
 * @return {jQueryObject}
 */
jQueryObject.prototype.andSelf = function() {};

/**
 * @return {jQueryObject}
 */
jQueryObject.prototype.end = function() {};


// http://docs.jquery.com/Manipulation

/**
 * @param {...(string|Node|Element|jQueryObject)} var_args
 * @return {jQueryObject}
 */
jQueryObject.prototype.append = function(var_args) {};

/**
 * @param {string|Node|Element|jQueryObject} target
 * @return {jQueryObject}
 */
jQueryObject.prototype.appendTo = function(target) {};

/**
 * @param {...(string|Node|Element|jQueryObject)} var_args
 * @return {jQueryObject}
 */
jQueryObject.prototype.prepend = function(var_args) {};

/**
 * @param {string|Node|Element|jQueryObject} target
 * @return {jQueryObject}
 */
jQueryObject.prototype.prependTo = function(target) {};

/**
 * @param {...(string|Node|Element|jQueryObject)} var_args
 * @return {jQueryObject}
 */
jQueryObject.prototype.after = function(var_args) {};

/**
 * @param {...(string|Node|Element|jQueryObject)} var_args
 * @return {jQueryObject}
 */
jQueryObject.prototype.before = function(var_args) {};

/**
 * @param {string|Node|Element|jQueryObject} target
 * @return {jQueryObject}
 */
jQueryObject.prototype.insertAfter = function(target) {};

/**
 * @param {string|Node|Element|jQueryObject} target
 * @return {jQueryObject}
 */
jQueryObject.prototype.insertBefore = function(target) {};

/**
 * @param {string|Element} arg
 * @return {jQueryObject}
 */
jQueryObject.prototype.wrap = function(arg) {};

/**
 * @param {string|Element} arg
 * @return {jQueryObject}
 */
jQueryObject.prototype.wrapAll = function(arg) {};

/**
 * @param {string|Element} arg
 * @return {jQueryObject}
 */
jQueryObject.prototype.wrapInner = function(arg) {};

/**
 * @param {string|Node|Element|jQueryObject} content
 * @return {jQueryObject}
 */
jQueryObject.prototype.replaceWith = function(content) {};

/**
 * @param {string} selector
 * @return {jQueryObject}
 */
jQueryObject.prototype.replaceAll = function(selector) {};

/**
 * @return {jQueryObject}
 */
jQueryObject.prototype.empty = function() {};

/**
 * @param {string=} opt_expr
 * @return {jQueryObject}
 */
jQueryObject.prototype.remove = function(opt_expr) {};

/**
 * @param {boolean=} opt_cloneEvents
 * @return {jQueryObject}
 */
jQueryObject.prototype.clone = function(opt_cloneEvents) {};


// http://docs.jquery.com/CSS

/**
 * @param {string|Object} nameOrProperties
 * @param {(string|number|Function)=} opt_value
 * @return {Object|jQueryObject|string}
 */
jQueryObject.prototype.css = function(nameOrProperties, opt_value) {};

/**
 * @return {Object}
 */
jQueryObject.prototype.offset = function() {};

/**
 * @return {Object}
 */
jQueryObject.prototype.position = function() {};

/**
 * @param {number=} opt_val
 * @return {number|jQueryObject}
 */
jQueryObject.prototype.scrollTop = function(opt_val) {};

/**
 * @param {number=} opt_val
 * @return {number|jQueryObject}
 */
jQueryObject.prototype.scrollLeft = function(opt_val) {};

/**
 * @param {number=} opt_val
 * @return {number|jQueryObject}
 */
jQueryObject.prototype.height = function(opt_val) {};

/**
 * @param {number=} opt_val
 * @return {number|jQueryObject}
 */
jQueryObject.prototype.width = function(opt_val) {};

/**
 * @return {number}
 */
jQueryObject.prototype.innerHeight = function() {};

/**
 * @return {number}
 */
jQueryObject.prototype.innerWidth = function() {};

/**
 * @param {boolean=} opt_margin
 * @return {number}
 */
jQueryObject.prototype.outerHeight = function(opt_margin) {};

/**
 * @param {boolean=} opt_margin
 * @return {number}
 */
jQueryObject.prototype.outerWidth = function(opt_margin) {};


// http://docs.jquery.com/Events

/**
 * @param {string} type
 * @param {Function} fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.live = function(type, fn) {};

/**
 * @param {string=} opt_type
 * @param {Function=} opt_fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.die = function(opt_type, opt_fn) {};

/**
 * @param {Function} over
 * @param {Function} out
 * @return {jQueryObject}
 */
jQueryObject.prototype.hover = function(over, out) {};

/**
 * @param {Function=} opt_fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.blur = function(opt_fn) {};

/**
 * @param {Function=} opt_fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.change = function(opt_fn) {};

/**
 * @param {Function=} opt_fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.click = function(opt_fn) {};

/**
 * @param {Function=} opt_fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.dblclick = function(opt_fn) {};

/**
 * @param {Function=} opt_fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.error = function(opt_fn) {};

/**
 * @param {Function=} opt_fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.focus = function(opt_fn) {};

/**
 * @param {Function=} opt_fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.keydown = function(opt_fn) {};

/**
 * @param {Function=} opt_fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.keypress = function(opt_fn) {};

/**
 * @param {Function} fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.keyup = function(fn) {};

/**
 * @param {Function} fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.load = function(fn) {};

/**
 * @param {Function} fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.mousedown = function(fn) {};

/**
 * @param {Function} fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.mouseenter = function(fn) {};

/**
 * @param {Function} fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.mouseleave = function(fn) {};

/**
 * @param {Function} fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.mousemove = function(fn) {};

/**
 * @param {Function} fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.mouseout = function(fn) {};

/**
 * @param {Function} fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.mouseover = function(fn) {};

/**
 * @param {Function} fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.mouseup = function(fn) {};

/**
 * @param {Function} fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.resize = function(fn) {};

/**
 * @param {Function} fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.scroll = function(fn) {};

/**
 * @param {Function=} opt_fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.select = function(opt_fn) {};

/**
 * @param {Function=} opt_fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.submit = function(opt_fn) {};

/**
 * @param {Function} fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.unload = function(fn) {};


// http://docs.jquery.com/Effects

/**
 * @param {(number|string)=} opt_speed
 * @param {Function=} opt_fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.show = function(opt_speed, opt_fn) {};

/**
 * @param {(number|string)=} opt_speed
 * @param {Function=} opt_fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.hide = function(opt_speed, opt_fn) {};

/**
 * toggle() is defined as both an event and an effect... sigh.
 * @param {(Function|boolean|number|string)=} opt_arg1
 * @param {Function=} opt_fn2
 * @param {...Function} var_args
 * @return {jQueryObject}
 */
jQueryObject.prototype.toggle = function(opt_arg1, opt_fn2, var_args) {};

/**
 * @param {(Object.<string,*>|number|string)=} optionsOrDuration
 * @param {Function=} complete
 * @return {jQueryObject}
 */
jQueryObject.prototype.slideDown = function(optionsOrDuration, complete) {};

/**
 * @param {(Object.<string,*>|number|string)=} optionsOrDuration
 * @param {Function=} complete
 * @return {jQueryObject}
 */
jQueryObject.prototype.slideUp = function(optionsOrDuration, complete) {};

/**
 * @param {(Object.<string,*>|number|string)=} optionsOrDuration
 * @param {Function=} complete
 * @return {jQueryObject}
 */
jQueryObject.prototype.slideToggle = function(optionsOrDuration, complete) {};

/**
 * @param {(number|string)=} opt_speed
 * @param {Function=} opt_fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.fadeIn = function(opt_speed, opt_fn) {};

/**
 * @param {(number|string)=} opt_speed
 * @param {Function=} opt_fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.fadeOut = function(opt_speed, opt_fn) {};

/**
 * @param {(number|string)=} opt_speed
 * @param {number=} opt_opacity
 * @param {Function=} opt_fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.fadeTo = function(opt_speed, opt_opacity, opt_fn) {};

/**
 * @param {Object} params
 * @param {(number|string|Object)=} opt_durationOrOptions
 * @param {string=} opt_easing
 * @param {Function=} opt_fn
 * @return {jQueryObject}
 */
jQueryObject.prototype.animate = function(
    params, opt_durationOrOptions, opt_easing, opt_fn) {};

/**
 * @param {boolean} opt_clearQueue
 * @param {boolean} opt_gotoEnd
 * @return {jQueryObject}
 */
jQueryObject.prototype.stop = function(opt_clearQueue, opt_gotoEnd) {};

jQuery.fx = {};

/** @type {boolean} */
jQuery.fx.off;

// http://docs.jquery.com/Utilities

/**
 * @param {number|string|Object} value
 * @param {Array} array
 * @return {number}
 */
jQueryObject.prototype.inArray = function(value, array) {};
