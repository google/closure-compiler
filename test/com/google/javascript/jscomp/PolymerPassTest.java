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
import static com.google.javascript.jscomp.TypeValidator.TYPE_MISMATCH_WARNING;

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
    enableTypeCheck(CheckLevel.WARNING);
    runTypeCheckAfterProcessing = true;
    parseTypeInfo = true;
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
        "/** @constructor @extends {PolymerElement} @export */",
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
        "/** @constructor @extends {PolymerElement} @export */",
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
        "/** @constructor @extends {PolymerElement} @export */",
        "x.Z = function() {};",
        "x.Z = Polymer(/** @lends {x.Z.prototype} */ {",
        "  is: 'x-element',",
        "});"));
  }

  /**
   * Since 'x' is a global name, the type system understands
   * 'x.Z' as a type name, so there is no need to extract the
   * type to the global namespace.
   */
  public void testIIFEExtractionInGlobalNamespace() {
    test(Joiner.on("\n").join(
        "var x = {};",
        "(function() {",
        "  x.Z = Polymer({",
        "    is: 'x-element',",
        "    sayHi: function() { alert('hi'); },",
        "  });",
        "})()"),

        Joiner.on("\n").join(
        "var x = {};",
        "(function() {",
        "  /** @constructor @extends {PolymerElement} @export */",
        "  x.Z = function() {};",
        "  x.Z = Polymer(/** @lends {x.Z.prototype} */ {",
        "    is: 'x-element',",
        "    /** @this {x.Z} */",
        "    sayHi: function() { alert('hi'); },",
        "  });",
        "})()"));
  }

  /**
   * The definition of XElement is placed in the global namespace,
   * outside the IIFE so that the type system will understand that
   * XElement is a type.
   */
  public void testIIFEExtractionNoAssignmentTarget() {
    test(Joiner.on("\n").join(
        "(function() {",
        "  Polymer({",
        "    is: 'x',",
        "    sayHi: function() { alert('hi'); },",
        "  });",
        "})()"),

        Joiner.on("\n").join(
        "/** @constructor @extends {PolymerElement} @export */",
        "var XElement = function() {};",
        "(function() {",
        "  Polymer(/** @lends {XElement.prototype} */ {",
        "    is: 'x',",
        "    /** @this {XElement} */",
        "    sayHi: function() { alert('hi'); },",
        "  });",
        "})()"));
  }

  /**
   * The definition of FooThing is placed in the global namespace,
   * outside the IIFE so that the type system will understand that
   * FooThing is a type.
   */
  public void testIIFEExtractionVarTarget() {
    test(Joiner.on("\n").join(
        "(function() {",
        "  var FooThing = Polymer({",
        "    is: 'x',",
        "    sayHi: function() { alert('hi'); },",
        "  });",
        "})()"),

        Joiner.on("\n").join(
        "/** @constructor @extends {PolymerElement} @export */",
        "var FooThing = function() {};",
        "(function() {",
        "  FooThing = Polymer(/** @lends {FooThing.prototype} */ {",
        "    is: 'x',",
        "    /** @this {FooThing} */",
        "    sayHi: function() { alert('hi'); },",
        "  });",
        "})()"));
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
        " * @export ",
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
        "/** @constructor @extends {PolymerElement} @export */",
        "var X = function() {};",
        "X = Polymer(/** @lends {X.prototype} */ {",
        "  is: 'x-element',",
        "  listeners: {",
        "    'click': 'handleClick',",
        "  },",
        "",
        "  /** @this {X} */",
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
        "/** @constructor @extends {PolymerInputElement} @export */",
        "var XInputElement = function() {};",
        "Polymer(/** @lends {XInputElement.prototype} */ {",
        "  is: 'x-input',",
        "  extends: 'input',",
        "});"));

    testExternChanges(EXTERNS, js, INPUT_EXTERNS);
  }

  public void testNativeElementExtensionExternsNotDuplicated() {
    String js = Joiner.on("\n").join(
        "Polymer({",
        "  is: 'x-input',",
        "  extends: 'input',",
        "});",
        "Polymer({",
        "  is: 'y-input',",
        "  extends: 'input',",
        "});");

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
        "    /** @type {!User} @private */",
        "    user_: Object,",
        "    pets: {",
        "      type: Array,",
        "      notify: true,",
        "    },",
        "    name: String,",
        "  },",
        "});"),

        Joiner.on("\n").join(
        "/** @constructor */",
        "var User = function() {};",
        "var a = {};",
        "/** @constructor @extends {PolymerElement} @export */",
        "a.B = function() {};",
        "/** @type {!User} @private @export */",
        "a.B.prototype.user_;",
        "/** @type {!Array} @export */",
        "a.B.prototype.pets;",
        "/** @type {string} @export */",
        "a.B.prototype.name;",
        "a.B = Polymer(/** @lends {a.B.prototype} */ {",
        "  is: 'x-element',",
        "  properties: {",
        "    user_: Object,",
        "    pets: {",
        "      type: Array,",
        "      notify: true,",
        "    },",
        "    name: String,",
        "  },",
        "});"));
  }

  public void testReadOnlyPropertySetters() {
    test(Joiner.on("\n").join(
        "var a = {};",
        "a.B = Polymer({",
        "  is: 'x-element',",
        "  properties: {",
        "    pets: {",
        "      type: Array,",
        "      readOnly: true,",
        "    },",
        "    name: String,",
        "  },",
        "});"),

        Joiner.on("\n").join(
        "var a = {};",
        "/** @constructor @extends {PolymerElement} @export */",
        "a.B = function() {};",
        "/** @type {!Array} @export */",
        "a.B.prototype.pets;",
        "/**",
        " * @param {!Array} pets",
        " * @private",
        " * @export",
        " */",
        "a.B.prototype._setPets = function(pets) {};",
        "/** @type {string} @export */",
        "a.B.prototype.name;",
        "a.B = Polymer(/** @lends {a.B.prototype} */ {",
        "  is: 'x-element',",
        "  properties: {",
        "    pets: {",
        "      type: Array,",
        "      readOnly: true,",
        "    },",
        "    name: String,",
        "  },",
        "});"));
  }

  public void testThisTypeAddedToFunctions() {
    test(Joiner.on("\n").join(
        "var X = Polymer({",
        "  is: 'x-element',",
        "  sayHi: function() {",
        "    alert('hi');",
        "  },",
        "  /** @override */",
        "  created: function() {",
        "    this.sayHi();",
        "    this.sayHelloTo_('Tester');",
        "  },",
        "  /**",
        "   * @param {string} name",
        "   * @private",
        "   */",
        "  sayHelloTo_: function(name) {",
        "    alert('Hello, ' + name);",
        "  },",
        "});"),

        Joiner.on("\n").join(
        "/** @constructor @extends {PolymerElement} @export */",
        "var X = function() {};",
        "X = Polymer(/** @lends {X.prototype} */ {",
        "  is: 'x-element',",
        "  /** @this {X} */",
        "  sayHi: function() {",
        "    alert('hi');",
        "  },",
        "  /** @override @this {X} */",
        "  created: function() {",
        "    this.sayHi();",
        "    this.sayHelloTo_('Tester');",
        "  },",
        "  /**",
        "   * @param {string} name",
        "   * @private",
        "   * @this {X}",
        "   */",
        "  sayHelloTo_: function(name) {",
        "    alert('Hello, ' + name);",
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

  public void testInvalidTypeAssignment() {
    test(
        Joiner.on("\n").join(
        "var X = Polymer({",
        "  is: 'x-element',",
        "  properties: {",
        "    isHappy: Boolean,",
        "  },",
        "  /** @override */",
        "  created: function() {",
        "    this.isHappy = 7;",
        "  },",
        "});"),
        Joiner.on("\n").join(
        "/** @constructor @extends {PolymerElement} @export */",
        "var X = function() {};",
        "/** @type {boolean} @export */",
        "X.prototype.isHappy;",
        "X = Polymer(/** @lends {X.prototype} */ {",
        "  is: 'x-element',",
        "  properties: {",
        "    isHappy: Boolean,",
        "  },",
        "  /** @override @this {X} */",
        "  created: function() {",
        "    this.isHappy = 7;",
        "  },",
        "});"),
        null,
        TYPE_MISMATCH_WARNING);
  }
}
