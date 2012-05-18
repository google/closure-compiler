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
 * @fileoverview Provides the boilerplate code for run-time type checking.
 *
 */

/** @const */
$jscomp.typecheck = {};

/**
 * A state variable to suspend checking, to avoid infinite calls
 * caused by calling checked code from the checking functions.
 *
 * @type {boolean}
 */
$jscomp.typecheck.suspendChecking = false;


/**
 * Log and possibly format the run-time type check warning. This
 * function is customized at compile-time.
 *
 * @param {string} warning the warning to log.
 * @param {*} expr the faulty expression.
 */
$jscomp.typecheck.log = function(warning, expr) {};

/**
 * Checks that the given expression matches one of the given checkers,
 * logging if not, and returning the expression regardless.
 *
 * @param {*} expr the expression to check.
 * @param {!Array.<!$jscomp.typecheck.Checker>} checkers the checkers to
 *     use in checking, one of these has to match for checking to succeed.
 * #return {*} the given expression back.
 */
$jscomp.typecheck.checkType = function(expr, checkers) {
  if ($jscomp.typecheck.suspendChecking) {
    return expr;
  }
  $jscomp.typecheck.suspendChecking = true;

  for (var i = 0; i < checkers.length; i++) {
    var checker = checkers[i];
    var ok = checker.check(expr);
    if (ok) {
      $jscomp.typecheck.suspendChecking = false;
      return expr;
    }
  }

  var warning = $jscomp.typecheck.prettify_(expr) + ' not in ' +
      checkers.join(' ');

  $jscomp.typecheck.log(warning, expr);

  $jscomp.typecheck.suspendChecking = false;
  return expr;
};


/**
 * Prettify the given expression for printing.
 *
 * @param {*} expr the expression.
 * @return {string} a string representation of the given expression.
 * @private
 */
$jscomp.typecheck.prettify_ = function(expr) {
  return $jscomp.typecheck.getClassName_(expr) || String(expr);
};

/**
 * Gets the class name if the given expression is an object.
 *
 * @param {*} expr the expression.
 * @return {string|undefined} the class name or undefined if the
 *     expression is not an object.
 * @private
 */
$jscomp.typecheck.getClassName_ = function(expr) {
  var className = void 0;
  if (typeof expr == 'object' && expr && expr.constructor) {
    className = expr.constructor.name;
    if (!className) {
      var funNameRe = /function (.{1,})\(/;
      var m = (funNameRe).exec(expr.constructor.toString());
      className = m && m.length > 1 ? m[1] : void 0;
    }
  }
  return className;
};

/**
 * Interface for all checkers.
 *
 * @interface
 */
$jscomp.typecheck.Checker = function() {};


/**
 * Checks the given expression.
 *
 * @param {*} expr the expression to check.
 * @return {boolean} whether the given expression matches this checker.
 */
$jscomp.typecheck.Checker.prototype.check = function(expr) {};



/**
 * A class for all value checkers, except the null checker.
 *
 * @param {string} type the value type (e.g. 'number') of this checker.
 * @constructor
 * @implements {$jscomp.typecheck.Checker}
 * @private
 */
$jscomp.typecheck.ValueChecker_ = function(type) {
  /**
   * The value type of this checker.
   * @type {string}
   * @private
   */
  this.type_ = type;
};


/** @inheritDoc */
$jscomp.typecheck.ValueChecker_.prototype.check = function(expr) {
  return typeof(expr) == this.type_;
};


/** @inheritDoc */
$jscomp.typecheck.ValueChecker_.prototype.toString = function() {
  return 'value(' + this.type_ + ')';
};



/**
 * A checker class for null values.
 *
 * @constructor
 * @implements {$jscomp.typecheck.Checker}
 * @private
 */
$jscomp.typecheck.NullChecker_ = function() {};


/** @inheritDoc */
$jscomp.typecheck.NullChecker_.prototype.check = function(expr) {
  return expr === null;
};


/** @inheritDoc */
$jscomp.typecheck.NullChecker_.prototype.toString = function() {
  return 'value(null)';
};


/**
 * A checker class for a class defined in externs, including built-in
 * JS types.
 *
 * <p>If the class type is undefined, then checking is suspended to
 * avoid spurious warnings. This is necessary because some externs
 * types are not defined in all browsers. For example, Window is not
 * defined Chrome, as window has the type DOMWindow.
 *
 * <p>Another subtlety is that a built-in type may be referenced in a
 * different frame than the one in which it was created. This causes
 * instanceOf to return false even though the object is of the correct
 * type. We work around this by checking as many windows as possible,
 * redefining open on top and window to keep track of them.
 *
 * @param {string} className the name of the extern class to check.
 * @constructor
 * @implements {$jscomp.typecheck.Checker}
 * @private
 */
$jscomp.typecheck.ExternClassChecker_ = function(className) {
  /**
   * The name of the extern class to check.
   * @type {string}
   * @private
   */
  this.className_ = className;
};


/**
 * A list of (hopefully all) open windows.
 *
 * @type {!Array.<!Window>}
 */
$jscomp.typecheck.ExternClassChecker_.windows = [];


/**
 * A list of the original open methods that have been redefined.
 *
 * @type {!Array.<!Function>}
 */
$jscomp.typecheck.ExternClassChecker_.oldOpenFuns = [];


/**
 * Redefines the open method on the given window, adding tracking.
 *
 * @param {!Object} win the window to track.
 */
$jscomp.typecheck.ExternClassChecker_.trackOpenOnWindow = function(win) {
  if (win.tracked) {
    return;
  }
  win.tracked = true;

  var key = $jscomp.typecheck.ExternClassChecker_.oldOpenFuns.length;

  $jscomp.typecheck.ExternClassChecker_.oldOpenFuns.push(win.open);
  $jscomp.typecheck.ExternClassChecker_.windows.push(win);

  win.open = function() {
    var w = $jscomp.typecheck.ExternClassChecker_.oldOpenFuns[key].apply(
        this, arguments);
    $jscomp.typecheck.ExternClassChecker_.trackOpenOnWindow(w);
    return w;
  };
};


/**
 * Returns the global 'this' object. This will normally be the same as 'window'
 * but when running in a worker thread, the DOM is not available.
 * @return {!Object}
 * @private
 */
$jscomp.typecheck.ExternClassChecker_.getGlobalThis_ = function() {
  return (function() { return this; }).call(null);
};


// Install listeners on the global 'this' object.
(function() {
  var globalThis = $jscomp.typecheck.ExternClassChecker_.getGlobalThis_();
  $jscomp.typecheck.ExternClassChecker_.trackOpenOnWindow(globalThis);

  var theTop = globalThis['top'];
  if (theTop) {
    $jscomp.typecheck.ExternClassChecker_.trackOpenOnWindow(theTop);
  }
})();


/** @inheritDoc */
$jscomp.typecheck.ExternClassChecker_.prototype.check = function(expr) {
  var classTypeDefined = [ false ];
  for (var i = 0; i < $jscomp.typecheck.ExternClassChecker_.windows.length;
      i++) {
    var w = $jscomp.typecheck.ExternClassChecker_.windows[i];
    if (this.checkWindow_(w, expr, classTypeDefined)) {
      return true;
    }
  }
  return !classTypeDefined[0];
};


/** @inheritDoc */
$jscomp.typecheck.ExternClassChecker_.prototype.toString = function() {
  return 'ext_class(' + this.className_ + ')';
};


/**
 * Checks whether the given expression is an instance of this extern
 * class in this window or any of its frames and subframes.
 *
 * @param {!Window} w the window to start checking from.
 * @param {*} expr the expression to check.
 * @param {!Array.<boolean>} classTypeDefined a wrapped boolean
 *     updated to indicate whether the class type was seen in any frame.
 * @return true if the given expression is an instance of this class.
 * @private
 */
$jscomp.typecheck.ExternClassChecker_.prototype.checkWindow_ =
    function(w, expr, classTypeDefined) {
  var classType = w[this.className_];
  classTypeDefined[0] |= !!classType;
  if (classType && expr instanceof classType) {
    return true;
  }
  for (var i = 0; i < w.length; i++) {
    if (this.checkWindow_(w.frames[i], expr, classTypeDefined)) {
      return true;
    }
  }
  return false;
};



/**
 * A class for all checkers of user-defined classes.
 *
 * @param {string} className name of the class to check.
 * @constructor
 * @implements {$jscomp.typecheck.Checker}
 * @private
 */
$jscomp.typecheck.ClassChecker_ = function(className) {

  /**
   * The name of the class to check.
   * #type {string}
   * @private
   */
  this.className_ = className;
};


/** @inheritDoc */
$jscomp.typecheck.ClassChecker_.prototype.check = function(expr) {
  return !!(expr && expr['instance_of__' + this.className_]);
};


/** @inheritDoc */
$jscomp.typecheck.ClassChecker_.prototype.toString = function() {
  return 'class(' + this.className_ + ')';
};



/**
 * A class for all checkers of user-defined interfaces.
 *
 * @param {string} interfaceName name of the interface to check.
 * @constructor
 * @implements {$jscomp.typecheck.Checker}
 * @private
 */
$jscomp.typecheck.InterfaceChecker_ = function(interfaceName) {

  /**
   * The name of the interface to check.
   * #type {string}
   * @private
   */
  this.interfaceName_ = interfaceName;
};


/** @inheritDoc */
$jscomp.typecheck.InterfaceChecker_.prototype.check = function(expr) {
  return !!(expr && expr['implements__' + this.interfaceName_]);
};


/** @inheritDoc */
$jscomp.typecheck.InterfaceChecker_.prototype.toString = function() {
  return 'interface(' + this.interfaceName_ + ')';
};



/**
 * A checker for null values.
 *
 * #type {!$jscomp.typecheck.Checker} a checker.
 */
$jscomp.typecheck.nullChecker = new $jscomp.typecheck.NullChecker_();


/**
 * Creates a checker for the given value type (excluding the null type).
 *
 * @param {string} type the value type.
 * @return {!$jscomp.typecheck.Checker} a checker.
 */
$jscomp.typecheck.valueChecker = function(type) {
  return new $jscomp.typecheck.ValueChecker_(type);
};


/**
 * Creates a checker for the given extern class name.
 *
 * @param {string} className the class name.
 * @return {!$jscomp.typecheck.Checker} a checker.
 */
$jscomp.typecheck.externClassChecker = function(className) {
  return new $jscomp.typecheck.ExternClassChecker_(className);
};


/**
 * Creates a checker for the given user-defined class.
 *
 * @param {string} className the class name.
 * @return {!$jscomp.typecheck.Checker} a checker.
 */
$jscomp.typecheck.classChecker = function(className) {
  return new $jscomp.typecheck.ClassChecker_(className);
};


/**
 * Creates a checker for the given user-defined interface.
 *
 * @param {string} interfaceName the interface name.
 * @return {!$jscomp.typecheck.Checker} a checker.
 */
$jscomp.typecheck.interfaceChecker = function(interfaceName) {
  return new $jscomp.typecheck.InterfaceChecker_(interfaceName);
};
