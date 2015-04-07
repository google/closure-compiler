/*
 * Copyright 2015 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.PolymerPass.POLYMER_DESCRIPTOR_NOT_VALID;
import static com.google.javascript.jscomp.PolymerPass.POLYMER_INVALID_PROPERTY;
import static com.google.javascript.jscomp.PolymerPass.POLYMER_MISSING_IS;
import static com.google.javascript.jscomp.PolymerPass.POLYMER_UNEXPECTED_PARAMS;

import com.google.common.base.Joiner;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * Unit tests for PolymerPass
 * @author jlklein@google.com (Jeremy Klein)
 */
public class PolymerPassTest extends CompilerTestCase {
  private static final String EXTERNS = Joiner.on("\n").join(
      "/** @constructor */",
      "var HTMLElement = function() {};",
      "/** @constructor @extends {HTMLElement} */",
      "var HTMLInputElement = function() {};",
      "/** @constructor @extends {HTMLElement} */",
      "var PolymerElement = function() {",
      "  /** @type {Object.<string, !HTMLElement>} */",
      "  this.$;",
      "};",
      "PolymerElement.prototype.created = function() {};",
      "PolymerElement.prototype.ready = function() {};",
      "PolymerElement.prototype.attached = function() {};",
      "PolymerElement.prototype.domReady = function() {};",
      "PolymerElement.prototype.detached = function() {};",
      "/**",
      " * Call the callback after a timeout. Calling job again with the same name",
      " * resets the timer but will not result in additional calls to callback.",
      " *",
      " * @param {string} name",
      " * @param {Function} callback",
      " * @param {number} timeoutMillis The minimum delay in milliseconds before",
      " *     calling the callback.",
      " */",
      "PolymerElement.prototype.job = function(name, callback, timeoutMillis) {};",
      "/**",
      " * @param a {!Object}",
      " * @return {!function()}",
      " */",
      "var Polymer = function(a) {};",
      "var alert = function(msg) {};");

  private static final String INPUT_EXTERNS = Joiner.on("\n").join(
      "/** @constructor */",
      "var HTMLElement = function() {};",
      "/** @constructor @extends {HTMLElement} */",
      "var HTMLInputElement = function() {};",
      "/** @constructor @extends {HTMLElement} */",
      "var PolymerElement = function() {",
      "  /** @type {Object.<string, !HTMLElement>} */",
      "  this.$;",
      "};",
      "/** @constructor @extends {HTMLInputElement} */",
      "var PolymerInputElement = function() {",
      "  /** @type {Object.<string, !HTMLElement>} */",
      "  this.$;",
      "};",
      "PolymerInputElement.prototype.created = function() {};",
      "PolymerInputElement.prototype.ready = function() {};",
      "PolymerInputElement.prototype.attached = function() {};",
      "PolymerInputElement.prototype.domReady = function() {};",
      "PolymerInputElement.prototype.detached = function() {};",
      "/**",
      " * Call the callback after a timeout. Calling job again with the same name",
      " * resets the timer but will not result in additional calls to callback.",
      " *",
      " * @param {string} name",
      " * @param {Function} callback",
      " * @param {number} timeoutMillis The minimum delay in milliseconds before",
      " *     calling the callback.",
      " */",
      "PolymerInputElement.prototype.job = function(name, callback, timeoutMillis) {};",
      "PolymerElement.prototype.created = function() {};",
      "PolymerElement.prototype.ready = function() {};",
      "PolymerElement.prototype.attached = function() {};",
      "PolymerElement.prototype.domReady = function() {};",
      "PolymerElement.prototype.detached = function() {};",
      "/**",
      " * Call the callback after a timeout. Calling job again with the same name",
      " * resets the timer but will not result in additional calls to callback.",
      " *",
      " * @param {string} name",
      " * @param {Function} callback",
      " * @param {number} timeoutMillis The minimum delay in milliseconds before",
      " *     calling the callback.",
      " */",
      "PolymerElement.prototype.job = function(name, callback, timeoutMillis) {};",
      "/**",
      " * @param a {!Object}",
      " * @return {!function()}",
      " */",
      "var Polymer = function(a) {};",
      "var alert = function(msg) {};");

  public PolymerPassTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new PolymerPass(compiler);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    allowExternsChanges(true);
    enableTypeCheck(CheckLevel.ERROR);
    runTypeCheckAfterProcessing = true;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testVarTarget() {
    test(Joiner.on("\n").join(
        "var X = Polymer({",
        "  is: 'x-element',",
        "});"),

        Joiner.on("\n").join(
        "/** @constructor @extends {PolymerElement} */",
        "var X = function() {};",
        "X = Polymer(/** @lends {X.prototype} */ {",
        "  is: 'x-element',",
        "});"));
  }

  public void testDefaultTypeNameTarget() {
    test(Joiner.on("\n").join(
        "Polymer({",
        "  is: 'x',",
        "});"),

        Joiner.on("\n").join(
        "/** @constructor @extends {PolymerElement} */",
        "var XElement = function() {};",
        "Polymer(/** @lends {XElement.prototype} */ {",
        "  is: 'x',",
        "});"));
  }

  public void testPathAssignmentTarget() {
    test(Joiner.on("\n").join(
        "var x = {};",
        "x.Z = Polymer({",
        "  is: 'x-element',",
        "});"),

        Joiner.on("\n").join(
        "var x = {};",
        "/** @constructor @extends {PolymerElement} */",
        "x.Z = function() {};",
        "x.Z = Polymer(/** @lends {x.Z.prototype} */ {",
        "  is: 'x-element',",
        "});"));
  }

  public void testConstructorExtraction() {
    test(Joiner.on("\n").join(
        "var X = Polymer({",
        "  is: 'x-element',",
        "  /**",
        "   * @param {string} name",
        "   */",
        "  constructor: function(name) { alert('hi, ' + name); },",
        "});"),

        Joiner.on("\n").join(
        "/**",
        " * @param {string} name",
        " * @constructor @extends {PolymerElement}",
        " */",
        "var X = function(name) { alert('hi, ' + name); };",
        "X = Polymer(/** @lends {X.prototype} */ {",
        "  is: 'x-element',",
        "  constructor: function(name) { alert('hi, ' + name); },",
        "});"));
  }

  public void testOtherKeysIgnored() {
    test(Joiner.on("\n").join(
        "var X = Polymer({",
        "  is: 'x-element',",
        "  listeners: {",
        "    'click': 'handleClick',",
        "  },",
        "",
        "  handleClick: function(e) {",
        "    alert('Thank you for clicking');",
        "  },",
        "});"),

        Joiner.on("\n").join(
        "/** @constructor @extends {PolymerElement} */",
        "var X = function() {};",
        "X = Polymer(/** @lends {X.prototype} */ {",
        "  is: 'x-element',",
        "  listeners: {",
        "    'click': 'handleClick',",
        "  },",
        "",
        "  handleClick: function(e) {",
        "    alert('Thank you for clicking');",
        "  },",
        "});"));
  }

  public void testNativeElementExtension() {
    String js = Joiner.on("\n").join(
        "Polymer({",
        "  is: 'x-input',",
        "  extends: 'input',",
        "});");

    test(js,
        Joiner.on("\n").join(
        "/** @constructor @extends {PolymerInputElement} */",
        "var XInputElement = function() {};",
        "Polymer(/** @lends {XInputElement.prototype} */ {",
        "  is: 'x-input',",
        "  extends: 'input',",
        "});"));

    testExternChanges(EXTERNS, js, INPUT_EXTERNS);
  }

  public void testPropertiesAddedToPrototype() {
    test(Joiner.on("\n").join(
        "/** @constructor */",
        "var User = function() {};",
        "var a = {};",
        "a.B = Polymer({",
        "  is: 'x-element',",
        "  properties: {",
        "    /** @type {!User} */",
        "    user: Object,",
        "    pets: {",
        "      type: Array,",
        "      readOnly: true,",
        "    },",
        "    name: String,",
        "  },",
        "});"),

        Joiner.on("\n").join(
        "/** @constructor */",
        "var User = function() {};",
        "var a = {};",
        "/** @constructor @extends {PolymerElement} */",
        "a.B = function() {};",
        "/** @type {!User} */",
        "a.B.prototype.user;",
        "/** @type {!Array} */",
        "a.B.prototype.pets;",
        "/** @type {string} */",
        "a.B.prototype.name;",
        "a.B = Polymer(/** @lends {a.B.prototype} */ {",
        "  is: 'x-element',",
        "  properties: {",
        "    user: Object,",
        "    pets: {",
        "      type: Array,",
        "      readOnly: true,",
        "    },",
        "    name: String,",
        "  },",
        "});"));
  }

  public void testInvalid1() {
    testSame(
        "var x = Polymer();",
        POLYMER_DESCRIPTOR_NOT_VALID, true);
    testSame(
        "var x = Polymer({},'blah');",
        POLYMER_UNEXPECTED_PARAMS, true);
    testSame(
        "var x = Polymer({});",
        POLYMER_MISSING_IS, true);
  }

  public void testInvalidProperties() {
    testSame(
        Joiner.on("\n").join(
        "Polymer({",
        "  is: 'x-element',",
        "  properties: {",
        "    isHappy: true,",
        "  },",
        "});"),
        POLYMER_INVALID_PROPERTY, true);

    testSame(
        Joiner.on("\n").join(
        "Polymer({",
        "  is: 'x-element',",
        "  properties: {",
        "    isHappy: {",
        "      value: true,",
        "    },",
        "  },",
        "});"),
        POLYMER_INVALID_PROPERTY, true);
  }
}
