/*
 * Copyright 2008 The Closure Compiler Authors.
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


package com.google.javascript.jscomp;

import com.google.javascript.rhino.testing.BaseJSTypeTestCase;

abstract class CompilerTypeTestCase extends BaseJSTypeTestCase {

  static final String ACTIVE_X_OBJECT_DEF =
      "/**\n" +
      " * @param {string} progId\n" +
      " * @param {string=} opt_location\n" +
      " * @constructor\n" +
      " * @see http://msdn.microsoft.com/en-us/library/7sw4ddf8.aspx\n" +
      " */\n" +
      "function ActiveXObject(progId, opt_location) {}\n";

  static final String CLOSURE_DEFS =
      "var goog = {};" +
      "goog.inherits = function(x, y) {};" +
      "goog.abstractMethod = function() {};" +
      "goog.isArray = function(x) {};" +
      "goog.isDef = function(x) {};" +
      "goog.isFunction = function(x) {};" +
      "goog.isNull = function(x) {};" +
      "goog.isString = function(x) {};" +
      "goog.isObject = function(x) {};" +
      "goog.isDefAndNotNull = function(x) {};";

  /** A default set of externs for testing. */
  static final String DEFAULT_EXTERNS =
      "/** @constructor \n * @param {*} var_args */ " +
      "function Function(var_args) {}" +
      "/** @type {!Function} */ Function.prototype.apply;" +
      "/** @type {!Function} */ Function.prototype.call;" +
      "/** @constructor \n * @param {*} arg \n @return {string} */" +
      "function String(arg) {}" +
      "/** @type {number} */ String.prototype.length;" +
      "/** @constructor \n * @param {*} var_args \n @return {!Array} */" +
      "function Array(var_args) {}" +
      "/** @type {number} */ Array.prototype.length;" + ACTIVE_X_OBJECT_DEF;

  protected Compiler compiler;

  protected CompilerOptions getOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setWarningLevel(
        DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.WARNING);
    options.setCodingConvention(getCodingConvention());
    return options;
  }

  protected CodingConvention getCodingConvention() {
    return new GoogleCodingConvention();
  }

  @Override
  protected void setUp() throws Exception {
    compiler = new Compiler();
    compiler.initOptions(getOptions());
    registry = compiler.getTypeRegistry();
    initTypes();
  }
}
