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

import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_DESCRIPTOR_NOT_VALID;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_INVALID_DECLARATION;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_INVALID_PROPERTY;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_MISSING_IS;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_SHORTHAND_NOT_SUPPORTED;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_UNANNOTATED_BEHAVIOR;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_UNEXPECTED_PARAMS;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_UNQUALIFIED_BEHAVIOR;
import static com.google.javascript.jscomp.TypeValidator.TYPE_MISMATCH_WARNING;

/**
 * Unit tests for PolymerPass
 * @author jlklein@google.com (Jeremy Klein)
 */
public class PolymerPassTest extends Es6CompilerTestCase {
  private static final String EXTERNS =
      LINE_JOINER.join(
          "/** @constructor */",
          "var HTMLElement = function() {};",
          "/** @constructor @extends {HTMLElement} */",
          "var HTMLInputElement = function() {};",
          "/** @constructor @extends {HTMLElement} */",
          "var PolymerElement = function() {};",
          "/** @type {!Object} */",
          "PolymerElement.prototype.$;",
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

  private static final String INPUT_EXTERNS =
      LINE_JOINER.join(
          "/** @constructor */",
          "var HTMLElement = function() {};",
          "/** @constructor @extends {HTMLElement} */",
          "var HTMLInputElement = function() {};",
          "/** @constructor @extends {HTMLElement} */",
          "var PolymerElement = function() {};",
          "/** @constructor @extends {HTMLInputElement} */",
          "var PolymerInputElement = function() {};",
          "/** @type {!Object} */",
          "PolymerInputElement.prototype.$;",
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
          "/** @type {!Object} */",
          "PolymerElement.prototype.$;",
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
          "var alert = function(msg) {};",
          "/** @interface */",
          "var PolymerXInputElementInterface = function() {};");

  private static final String READONLY_EXTERNS =
      EXTERNS
          + LINE_JOINER.join(
              "/** @interface */",
              "var Polymera_BInterface = function() {};",
              "/** @type {!Array<string>} */",
              "Polymera_BInterface.prototype.pets;",
              "/** @private {string} */",
              "Polymera_BInterface.prototype.name_;",
              "/** @param {!Array<string>} pets **/",
              "Polymera_BInterface.prototype._setPets;");

  private static final String BEHAVIOR_READONLY_EXTERNS =
      EXTERNS
          + LINE_JOINER.join(
              "/** @interface */",
              "var PolymerAInterface = function() {};",
              "/** @type {boolean} */",
              "PolymerAInterface.prototype.isFun;",
              "/** @type {!Array} */",
              "PolymerAInterface.prototype.pets;",
              "/** @type {string} */",
              "PolymerAInterface.prototype.name;",
              "/** @param {boolean} isFun **/",
              "PolymerAInterface.prototype._setIsFun;");

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
    allowExternsChanges(true);
    enableTypeCheck();
    runTypeCheckAfterProcessing = true;
    parseTypeInfo = true;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testVarTarget() {
    test(
        LINE_JOINER.join(
            "var X = Polymer({",
            "  is: 'x-element',",
            "});"),

        LINE_JOINER.join(
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface} */",
            "var X = function() {};",
            "X = Polymer(/** @lends {X.prototype} */ {",
            "  is: 'x-element',",
            "});"));
  }

  public void testLetTarget() {
    disableTypeCheck();
    testEs6(
        LINE_JOINER.join(
            "let X = Polymer({",
            "  is: 'x-element',",
            "});"),
        LINE_JOINER.join(
            "/**",
            " * @constructor",
            " * @implements {PolymerXInterface}",
            " * @extends {PolymerElement}",
            " */",
            "var X = function() {};",
            "X = Polymer(/** @lends {X.prototype} */ {is:'x-element'});"));
  }

  public void testConstTarget() {
    disableTypeCheck();
    testErrorEs6(
        LINE_JOINER.join(
            "const X = Polymer({",
            "  is: 'x-element',",
            "});"), POLYMER_INVALID_DECLARATION);
  }

  public void testDefaultTypeNameTarget() {
    test(
        LINE_JOINER.join(
            "Polymer({",
            "  is: 'x',",
            "});"),

        LINE_JOINER.join(
            "/**",
            " * @implements {PolymerXElementInterface}",
            " * @constructor @extends {PolymerElement}",
            " */",
            "var XElement = function() {};",
            "Polymer(/** @lends {XElement.prototype} */ {",
            "  is: 'x',",
            "});"));
  }

  public void testPathAssignmentTarget() {
    test(
        LINE_JOINER.join(
            "var x = {};",
            "x.Z = Polymer({",
            "  is: 'x-element',",
            "});"),

        LINE_JOINER.join(
            "var x = {};",
            "/** @constructor @extends {PolymerElement} @implements {Polymerx_ZInterface} */",
            "x.Z = function() {};",
            "x.Z = Polymer(/** @lends {x.Z.prototype} */ {",
            "  is: 'x-element',",
            "});"));
  }

  public void testComputedPropName() {
    disableTypeCheck(); // TypeCheck cannot grab a name from a complicated computedPropName
    testEs6("var X = Polymer({is:'x-element', [name + (() => 42)]: function() {return 42;}});",
        LINE_JOINER.join(
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface} */",
            "var X = function() {}",
            "",
            "X = Polymer(/** @lends {X.prototype} */{",
            "  is: 'x-element',",
            "  /** @this {X} */",
            "  [name + (()=>42)]: function() {return 42;},",
            "});"));
  }

  /**
   * Since 'x' is a global name, the type system understands
   * 'x.Z' as a type name, so there is no need to extract the
   * type to the global namespace.
   */
  public void testIIFEExtractionInGlobalNamespace() {
    test(
        LINE_JOINER.join(
            "var x = {};",
            "(function() {",
            "  x.Z = Polymer({",
            "    is: 'x-element',",
            "    sayHi: function() { alert('hi'); },",
            "  });",
            "})()"),

        LINE_JOINER.join(
            "var x = {};",
            "(function() {",
            "  /** @constructor @extends {PolymerElement} @implements {Polymerx_ZInterface}*/",
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
    test(
        LINE_JOINER.join(
            "(function() {",
            "  Polymer({",
            "    is: 'x',",
            "    sayHi: function() { alert('hi'); },",
            "  });",
            "})()"),

        LINE_JOINER.join(
            "/**",
            " * @constructor @extends {PolymerElement}",
            " * @implements {PolymerXElementInterface}",
            " */",
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
    test(
        LINE_JOINER.join(
            "(function() {",
            "  var FooThing = Polymer({",
            "    is: 'x',",
            "    sayHi: function() { alert('hi'); },",
            "  });",
            "})()"),

        LINE_JOINER.join(
            "/**",
            " * @constructor @extends {PolymerElement}",
            " * @implements {PolymerFooThingInterface}",
            " */",
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
    test(
        LINE_JOINER.join(
            "var X = Polymer({",
            "  is: 'x-element',",
            "  /**",
            "   * @param {string} name",
            "   */",
            "  factoryImpl: function(name) { alert('hi, ' + name); },",
            "});"),

        LINE_JOINER.join(
            "/**",
            " * @param {string} name",
            " * @constructor @extends {PolymerElement}",
            " * @implements {PolymerXInterface}",
            " */",
            "var X = function(name) { alert('hi, ' + name); };",
            "X = Polymer(/** @lends {X.prototype} */ {",
            "  is: 'x-element',",
            "  factoryImpl: function(name) { alert('hi, ' + name); },",
            "});"));
  }

  public void testShorthandConstructorExtraction() {
    testEs6(
        LINE_JOINER.join(
            "var X = Polymer({",
            "  is: 'x-element',",
            "  /**",
            "   * @param {string} name",
            "   */",
            "  factoryImpl(name) { alert('hi, ' + name); },",
            "});"),

        LINE_JOINER.join(
            "/**",
            " * @param {string} name",
            " * @constructor @extends {PolymerElement}",
            " * @implements {PolymerXInterface}",
            " */",
            "var X = function(name) { alert('hi, ' + name); };",
            "",
            "X = Polymer(/** @lends {X.prototype} */ {",
            "  is: 'x-element',",
            "  factoryImpl(name) { alert('hi, ' + name); },",
            "});"));
  }

  public void testOtherKeysIgnored() {
    test(
        LINE_JOINER.join(
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

        LINE_JOINER.join(
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface}*/",
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

  public void testListenersAndHostAttributeKeysQuoted() {
    test(
        LINE_JOINER.join(
            "var X = Polymer({",
            "  is: 'x-element',",
            "  listeners: {",
            "    click: 'handleClick',",
            "    'foo-bar': 'handleClick',",
            "  },",
            "  hostAttributes: {",
            "    role: 'button',",
            "    'foo-bar': 'done',",
            "    blah: 1,",
            "  },",
            "",
            "  handleClick: function(e) {",
            "    alert('Thank you for clicking');",
            "  },",
            "});"),

        LINE_JOINER.join(
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface}*/",
            "var X = function() {};",
            "X = Polymer(/** @lends {X.prototype} */ {",
            "  is: 'x-element',",
            "  listeners: {",
            "    'click': 'handleClick',",
            "    'foo-bar': 'handleClick',",
            "  },",
            "  hostAttributes: {",
            "    'role': 'button',",
            "    'foo-bar': 'done',",
            "    'blah': 1,",
            "  },",
            "",
            "  /** @this {X} */",
            "  handleClick: function(e) {",
            "    alert('Thank you for clicking');",
            "  },",
            "});"));
  }

  public void testNativeElementExtension() {
    String js = LINE_JOINER.join("Polymer({", "  is: 'x-input',", "  extends: 'input',", "});");

    test(
        js,
        LINE_JOINER.join(
            "/**",
            " * @constructor @extends {PolymerInputElement}",
            " * @implements {PolymerXInputElementInterface}",
            " */",
            "var XInputElement = function() {};",
            "Polymer(/** @lends {XInputElement.prototype} */ {",
            "  is: 'x-input',",
            "  extends: 'input',",
            "});"));

    testExternChanges(EXTERNS, js, INPUT_EXTERNS);
  }

  public void testNativeElementExtensionExternsNotDuplicated() {
    String js =
        LINE_JOINER.join(
            "Polymer({",
            "  is: 'x-input',",
            "  extends: 'input',",
            "});",
            "Polymer({",
            "  is: 'y-input',",
            "  extends: 'input',",
            "});");
    String newExterns =
        INPUT_EXTERNS
            + "\n"
            + LINE_JOINER.join(
                "/** @interface */", "var PolymerYInputElementInterface = function() {};");

    testExternChanges(EXTERNS, js, newExterns);
  }

  public void testPropertiesAddedToPrototype() {
    test(
        LINE_JOINER.join(
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
            "    thingToDo: Function,",
            "  },",
            "});"),

        LINE_JOINER.join(
            "/** @constructor */",
            "var User = function() {};",
            "var a = {};",
            "/** @constructor @extends {PolymerElement} @implements {Polymera_BInterface}*/",
            "a.B = function() {};",
            "/** @type {!User} @private */",
            "a.B.prototype.user_;",
            "/** @type {!Array} */",
            "a.B.prototype.pets;",
            "/** @type {string} */",
            "a.B.prototype.name;",
            "/** @type {!Function} */",
            "a.B.prototype.thingToDo;",
            "a.B = Polymer(/** @lends {a.B.prototype} */ {",
            "  is: 'x-element',",
            "  properties: {",
            "    user_: Object,",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "    thingToDo: Function,",
            "  },",
            "});"));
  }

  public void testPropertiesDefaultValueFunctions() {
    test(
        LINE_JOINER.join(
            "/** @constructor */",
            "var User = function() {};",
            "var a = {};",
            "a.B = Polymer({",
            "  is: 'x-element',",
            "  properties: {",
            "    /** @type {!User} @private */",
            "    user_: {",
            "      type: Object,",
            "      value: function() { return new User(); },",
            "    },",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "      value: function() { return [this.name]; },",
            "    },",
            "    name: String,",
            "  },",
            "});"),

        LINE_JOINER.join(
            "/** @constructor */",
            "var User = function() {};",
            "var a = {};",
            "/** @constructor @extends {PolymerElement} @implements {Polymera_BInterface}*/",
            "a.B = function() {};",
            "/** @type {!User} @private */",
            "a.B.prototype.user_;",
            "/** @type {!Array} */",
            "a.B.prototype.pets;",
            "/** @type {string} */",
            "a.B.prototype.name;",
            "a.B = Polymer(/** @lends {a.B.prototype} */ {",
            "  is: 'x-element',",
            "  properties: {",
            "    user_: {",
            "      type: Object,",
            "      /** @this {a.B} @return {!User} */",
            "      value: function() { return new User(); },",
            "    },",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "      /** @this {a.B} @return {!Array} */",
            "      value: function() { return [this.name]; },",
            "    },",
            "    name: String,",
            "  },",
            "});"));
  }

  public void testPropertiesDefaultValueShortHandFunction() {
    testEs6(
        LINE_JOINER.join(
            "/** @constructor */",
            "var User = function() {};",
            "var ES6Test = Polymer({",
            "  is: 'x-element',",
            "  properties: {",
            "    user: {",
            "      type: Object,",
            "      value() { return new User();},",
            "    },",
            "  },",
            "});"),
        LINE_JOINER.join(
            "/** @constructor */",
            "var User = function() {};",
            "",
            "/** ",
            " * @constructor @extends {PolymerElement} ",
            " * @implements {PolymerES6TestInterface}",
            " */",
            "var ES6Test = function() {};",
            "/** @type {!Object} */",
            "ES6Test.prototype.user;",
            "",
            "ES6Test = Polymer(/** @lends {ES6Test.prototype} */ {",
            "  is: 'x-element',",
            "  properties: {",
            "    user: {",
            "      type: Object,",
            "      /** @this {ES6Test} @return {!Object} */",
            "      value() { return new User(); },",
            "    },",
            "  },",
            "});"));
  }

  public void testReadOnlyPropertySetters() {
    String js =
        LINE_JOINER.join(
            "var a = {};",
            "a.B = Polymer({",
            "  is: 'x-element',",
            "  properties: {",
            "    /** @type {!Array<string>} */",
            "    pets: {",
            "      type: Array,",
            "      readOnly: true,",
            "    },",
            "    /** @private */",
            "    name_: String,",
            "  },",
            "});");

    test(
        js,
        LINE_JOINER.join(
            "var a = {};",
            "/** @constructor @extends {PolymerElement} @implements {Polymera_BInterface} */",
            "a.B = function() {};",
            "/** @type {!Array<string>} */",
            "a.B.prototype.pets;",
            "/** @private {string} */",
            "a.B.prototype.name_;",
            "/** @override */",
            "a.B.prototype._setPets = function(pets) {};",
            "a.B = Polymer(/** @lends {a.B.prototype} */ {",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      readOnly: true,",
            "    },",
            "    name_: String,",
            "  },",
            "});"));

    testExternChanges(EXTERNS, js, READONLY_EXTERNS);
  }

  public void testShorthandFunctionDefinition() {
    testEs6(
        LINE_JOINER.join(
            "var ES6Test = Polymer({",
            "  is: 'x-element',",
            "  sayHi() {",
            "    alert('hi');",
            "  },",
            "});"),
        LINE_JOINER.join(
            "/** ",
            " * @constructor @extends {PolymerElement} ",
            " * @implements {PolymerES6TestInterface} ",
            " */",
            "var ES6Test = function() {};",
            "",
            "ES6Test = Polymer(/** @lends {ES6Test.prototype} */ {",
            "  is: 'x-element',",
            "  /** @this {ES6Test} */",
            "  sayHi() {",
            "    alert('hi');",
            "  },",
            "});"));
  }

  public void testArrowFunctionDefinition() {
    testEs6(
        LINE_JOINER.join(
            "var ES6Test = Polymer({",
            "  is: 'x-element',",
            "  sayHi: ()=>42,",
            "});"),
        LINE_JOINER.join(
            "/** ",
            " * @constructor @extends {PolymerElement} ",
            " * @implements {PolymerES6TestInterface} ",
            " */",
            "var ES6Test = function() {};",
            "",
            "ES6Test = Polymer(/** @lends {ES6Test.prototype} */ {",
            "  is: 'x-element',",
            "  /** @this {ES6Test} */",
            "  sayHi: ()=>42,",
            "});"));
  }

  public void testShorthandLifecycleCallbacks() {
    testEs6(
        LINE_JOINER.join(
            "var ES6Test = Polymer({",
            "  is: 'x-element',",
            "  /** @override */",
            "  created() {",
            "    alert('Shorthand created');",
            "  },",
            "});"),
        LINE_JOINER.join(
            "/** ",
            " * @constructor @extends {PolymerElement} ",
            " * @implements {PolymerES6TestInterface} ",
            " */",
            "var ES6Test = function() {};",
            "",
            "ES6Test = Polymer(/** @lends {ES6Test.prototype} */ {",
            "  is: 'x-element',",
            "  /** @override @this {ES6Test} */",
            "  created() {",
            "    alert('Shorthand created');",
            "  },",
            "});"));
  }

  public void testShorthandFunctionDefinitionWithReturn() {
    testEs6(
        LINE_JOINER.join(
            "var ESTest = Polymer({",
            "  is: 'x-element',",
            "  sayHi() {",
            "    return [1, 2];",
            "  },",
            "});"),
        LINE_JOINER.join(
            "/** ",
            " * @constructor @extends {PolymerElement} ",
            " * @implements {PolymerESTestInterface} ",
            " */",
            "var ESTest = function() {};",
            "",
            "ESTest = Polymer(/** @lends {ESTest.prototype} */ {",
            "  is: 'x-element',",
            "  /** @this {ESTest} */",
            "  sayHi() {",
            "    return [1, 2];",
            "  },",
            "});"));
  }

  public void testThisTypeAddedToFunctions() {
    test(
        LINE_JOINER.join(
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

        LINE_JOINER.join(
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface} */",
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

  public void testDollarSignPropsConvertedToBrackets() {
    test(
        LINE_JOINER.join(
            "/** @constructor */",
            "var SomeType = function() {};",
            "SomeType.prototype.toggle = function() {};",
            "SomeType.prototype.switch = function() {};",
            "SomeType.prototype.touch = function() {};",
            "var X = Polymer({",
            "  is: 'x-element',",
            "  sayHi: function() {",
            "    this.$.checkbox.toggle();",
            "  },",
            "  /** @override */",
            "  created: function() {",
            "    this.sayHi();",
            "    this.$.radioButton.switch();",
            "  },",
            "  /**",
            "   * @param {string} name",
            "   * @private",
            "   */",
            "  sayHelloTo_: function(name) {",
            "    this.$.otherThing.touch();",
            "  },",
            "});"),

        LINE_JOINER.join(
            "/** @constructor */",
            "var SomeType = function() {};",
            "SomeType.prototype.toggle = function() {};",
            "SomeType.prototype.switch = function() {};",
            "SomeType.prototype.touch = function() {};",
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface} */",
            "var X = function() {};",
            "X = Polymer(/** @lends {X.prototype} */ {",
            "  is: 'x-element',",
            "  /** @this {X} */",
            "  sayHi: function() {",
            "    this.$['checkbox'].toggle();",
            "  },",
            "  /** @override @this {X} */",
            "  created: function() {",
            "    this.sayHi();",
            "    this.$['radioButton'].switch();",
            "  },",
            "  /**",
            "   * @param {string} name",
            "   * @private",
            "   * @this {X}",
            "   */",
            "  sayHelloTo_: function(name) {",
            "    this.$['otherThing'].touch();",
            "  },",
            "});"));
  }

  public void testDollarSignPropsInShorthandFunctionConvertedToBrackets() {
    testEs6(
        LINE_JOINER.join(
            "/** @constructor */",
            "var SomeType = function() {};",
            "SomeType.prototype.toggle = function() {};",
            "var ES6Test = Polymer({",
            "  is: 'x-element',",
            "  sayHi() {",
            "    this.$.checkbox.toggle();",
            "  },",
            "});"),

        LINE_JOINER.join(
            "/** @constructor */",
            "var SomeType = function() {};",
            "SomeType.prototype.toggle = function() {};",
            "",
            "/** ",
            " * @constructor @extends {PolymerElement} ",
            " * @implements {PolymerES6TestInterface} ",
            " */",
            "var ES6Test = function() {};",
            "",
            "ES6Test = Polymer(/** @lends {ES6Test.prototype} */ {",
            "  is: 'x-element',",
            "  /** @this {ES6Test} */",
            "  sayHi() {",
            "    this.$['checkbox'].toggle();",
            "  },",
            "});"));
  }

  /**
   * Test that behavior property types are copied correctly to multiple elements. See b/21929103.
   */
  public void testBehaviorForMultipleElements() {
    test(
        LINE_JOINER.join(
            "/** @constructor */",
            "var FunObject = function() {};",
            "/** @polymerBehavior */",
            "var FunBehavior = {",
            "  properties: {",
            "    /** @type {!FunObject} */",
            "    funThing: {",
            "      type: Object,",
            "    }",
            "  },",
            "};",
            "var A = Polymer({",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  },",
            "  behaviors: [ FunBehavior ],",
            "});",
            "var B = Polymer({",
            "  is: 'y-element',",
            "  properties: {",
            "    foo: String,",
            "  },",
            "  behaviors: [ FunBehavior ],",
            "});"),

        LINE_JOINER.join(
            "/** @constructor */",
            "var FunObject = function() {};",
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  properties: {",
            "    funThing: {",
            "      type: Object,",
            "    }",
            "  },",
            "};",
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface}*/",
            "var A = function() {};",
            "/** @type {!FunObject} */",
            "A.prototype.funThing;",
            "/** @type {!Array} */",
            "A.prototype.pets;",
            "/** @type {string} */",
            "A.prototype.name;",
            "A = Polymer(/** @lends {A.prototype} */ {",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  },",
            "  behaviors: [ FunBehavior ],",
            "});",
            "/** @constructor @extends {PolymerElement} @implements {PolymerBInterface}*/",
            "var B = function() {};",
            "/** @type {!FunObject} */",
            "B.prototype.funThing;",
            "/** @type {string} */",
            "B.prototype.foo;",
            "B = Polymer(/** @lends {B.prototype} */ {",
            "  is: 'y-element',",
            "  properties: {",
            "    foo: String,",
            "  },",
            "  behaviors: [ FunBehavior ],",
            "});"));
  }

  public void testSimpleBehavior() {
    test(
        LINE_JOINER.join(
            "/** @polymerBehavior */",
            "var FunBehavior = {",
            "  properties: {",
            "    /** @type {boolean} */",
            "    isFun: {",
            "      type: Boolean,",
            "      value: true,",
            "    }",
            "  },",
            "  listeners: {",
            "    click: 'doSomethingFun',",
            "  },",
            "  /** @type {string} */",
            "  foo: 'hooray',",
            "",
            "  /** @return {number} */",
            "  get someNumber() {",
            "    return 5*7+2;",
            "  },",
            "  /** @param {string} funAmount */",
            "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "  /** @override */",
            "  created: function() {}",
            "};",
            "var A = Polymer({",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  },",
            "  behaviors: [ FunBehavior ],",
            "});"),

        LINE_JOINER.join(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: {",
            "      type: Boolean,",
            "      value: true,",
            "    }",
            "  },",
            "  listeners: {",
            "    'click': 'doSomethingFun',",
            "  },",
            "  /** @type {string} */",
            "  foo: 'hooray',",
            "",
            "  /** @suppress {checkTypes|globalThis} */",
            "  get someNumber() {",
            "    return 5*7+2;",
            "  },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  created: function() {}",
            "};",
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface}*/",
            "var A = function() {};",
            "/** @type {boolean} */",
            "A.prototype.isFun;",
            "/** @type {!Array} */",
            "A.prototype.pets;",
            "/** @type {string} */",
            "A.prototype.name;",
            "/** @param {string} funAmount */",
            "A.prototype.doSomethingFun = function(funAmount) {",
            "  alert('Something ' + funAmount + ' fun!');",
            "};",
            "/** @type {string} */",
            "A.prototype.foo;",
            "/** @type {number} */",
            "A.prototype.someNumber;",
            "A = Polymer(/** @lends {A.prototype} */ {",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  },",
            "  behaviors: [ FunBehavior ],",
            "});"));
  }

  /**
   * Test that if a behavior function is implemented by the Element, the function from the behavior
   * is not copied to the prototype of the Element.
   */
  public void testBehaviorFunctionOverriddenByElement() {
    test(
        LINE_JOINER.join(
            "/** @polymerBehavior */",
            "var FunBehavior = {",
            "  /** @param {string} funAmount */",
            "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "};",
            "var A = Polymer({",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  },",
            "  /** @param {string} funAmount */",
            "  doSomethingFun: function(funAmount) {",
            "    alert('Element doing something' + funAmount + ' fun!');",
            "  },",
            "  behaviors: [ FunBehavior ],",
            "});"),

        LINE_JOINER.join(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  /** @suppress {checkTypes|globalThis} */",
            "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "};",
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface}*/",
            "var A = function() {};",
            "/** @type {!Array} */",
            "A.prototype.pets;",
            "/** @type {string} */",
            "A.prototype.name;",
            "A = Polymer(/** @lends {A.prototype} */ {",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  },",
            "  /**",
            "   * @param {string} funAmount",
            "   * @this {A}",
            "   */",
            "  doSomethingFun: function(funAmount) {",
            "    alert('Element doing something' + funAmount + ' fun!');",
            "  },",
            "  behaviors: [ FunBehavior ],",
            "});"));
  }

  public void testBehaviorShorthandFunctionOverriddenByElement() {
    testEs6(
        LINE_JOINER.join(
            "/** @polymerBehavior */",
            "var FunBehavior = {",
            "  /** @param {string} funAmount */",
            "  doSomethingFun(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "};",
            "",
            "var A = Polymer({",
            "  is: 'x-element',",
            "  properties: {",
            "    name: String,",
            "  },",
            "  /** @param {string} funAmount */",
            "  doSomethingFun(funAmount) {",
            "    alert('Element doing something' + funAmount + ' fun!');",
            "  },",
            "  behaviors: [ FunBehavior ],",
            "});"),

        LINE_JOINER.join(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  /** @suppress {checkTypes|globalThis} */",
            "  doSomethingFun(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "};",
            "",
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface}*/",
            "var A = function() {};",
            "",
            "/** @type {string} */",
            "A.prototype.name;",
            "",
            "A = Polymer(/** @lends {A.prototype} */ {",
            "  is: 'x-element',",
            "  properties: {",
            "    name: String,",
            "  },",
            "  /**",
            "   * @param {string} funAmount",
            "   * @this {A}",
            "   */",
            "  doSomethingFun(funAmount) {",
            "    alert('Element doing something' + funAmount + ' fun!');",
            "  },",
            "  behaviors: [ FunBehavior ],",
            "});"));
  }

  public void testBehaviorDefaultValueSuppression() {
    test(
        LINE_JOINER.join(
            "/** @polymerBehavior */",
            "var FunBehavior = {",
            "  properties: {",
            "    /** @type {boolean} */",
            "    isFun: {",
            "      type: Boolean,",
            "      value: true,",
            "    },",
            "    funObject: {",
            "      type: Object,",
            "      value: function() { return {fun: this.isFun }; },",
            "    },",
            "    funArray: {",
            "      type: Array,",
            "      value: function() { return [this.isFun]; },",
            "    },",
            "  },",
            "};",
            "var A = Polymer({",
            "  is: 'x-element',",
            "  properties: {",
            "    name: String,",
            "  },",
            "  behaviors: [ FunBehavior ],",
            "});"),

        LINE_JOINER.join(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: {",
            "      type: Boolean,",
            "      value: true,",
            "    },",
            "    funObject: {",
            "      type: Object,",
            "      /** @suppress {checkTypes|globalThis} */",
            "      value: function() { return {fun: this.isFun }; },",
            "    },",
            "    funArray: {",
            "      type: Array,",
            "      /** @suppress {checkTypes|globalThis} */",
            "      value: function() { return [this.isFun]; },",
            "    },",
            "  },",
            "};",
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface}*/",
            "var A = function() {};",
            "/** @type {boolean} */",
            "A.prototype.isFun;",
            "/** @type {!Object} */",
            "A.prototype.funObject;",
            "/** @type {!Array} */",
            "A.prototype.funArray;",
            "/** @type {string} */",
            "A.prototype.name;",
            "A = Polymer(/** @lends {A.prototype} */ {",
            "  is: 'x-element',",
            "  properties: {",
            "    name: String,",
            "  },",
            "  behaviors: [ FunBehavior ],",
            "});"));
  }

  public void testArrayBehavior() {
    test(
        LINE_JOINER.join(
            "/** @polymerBehavior */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "  /** @param {string} funAmount */",
            "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "  /** @override */",
            "  created: function() {}",
            "};",
            "/** @polymerBehavior */",
            "var RadBehavior = {",
            "  properties: {",
            "    howRad: Number",
            "  },",
            "  /** @param {number} radAmount */",
            "  doSomethingRad: function(radAmount) { alert('Something ' + radAmount + ' rad!'); },",
            "  /** @override */",
            "  ready: function() {}",
            "};",
            "/** @polymerBehavior */",
            "var SuperCoolBehaviors = [FunBehavior, RadBehavior];",
            "/** @polymerBehavior */",
            "var BoringBehavior = {",
            "  properties: {",
            "    boringString: String",
            "  },",
            "  /** @param {boolean} boredYet */",
            "  doSomething: function(boredYet) { alert(boredYet + ' ' + this.boringString); },",
            "};",
            "var A = Polymer({",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  },",
            "  behaviors: [ SuperCoolBehaviors, BoringBehavior ],",
            "});"),

        LINE_JOINER.join(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  created: function() {}",
            "};",
            "/** @polymerBehavior @nocollapse */",
            "var RadBehavior = {",
            "  properties: {",
            "    howRad: Number",
            "  },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  doSomethingRad: function(radAmount) { alert('Something ' + radAmount + ' rad!'); },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  ready: function() {}",
            "};",
            "/** @polymerBehavior @nocollapse */",
            "var SuperCoolBehaviors = [FunBehavior, RadBehavior];",
            "/** @polymerBehavior @nocollapse */",
            "var BoringBehavior = {",
            "  properties: {",
            "    boringString: String",
            "  },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  doSomething: function(boredYet) { alert(boredYet + ' ' + this.boringString); },",
            "};",
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface}*/",
            "var A = function() {};",
            "/** @type {boolean} */",
            "A.prototype.isFun;",
            "/** @type {number} */",
            "A.prototype.howRad;",
            "/** @type {string} */",
            "A.prototype.boringString;",
            "/** @type {!Array} */",
            "A.prototype.pets;",
            "/** @type {string} */",
            "A.prototype.name;",
            "/** @param {string} funAmount */",
            "A.prototype.doSomethingFun = function(funAmount) {",
            "  alert('Something ' + funAmount + ' fun!');",
            "};",
            "/** @param {number} radAmount */",
            "A.prototype.doSomethingRad = function(radAmount) {",
            "  alert('Something ' + radAmount + ' rad!');",
            "};",
            "/** @param {boolean} boredYet */",
            "A.prototype.doSomething = function(boredYet) {",
            "  alert(boredYet + ' ' + this.boringString);",
            "};",
            "A = Polymer(/** @lends {A.prototype} */ {",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  },",
            "  behaviors: [ SuperCoolBehaviors, BoringBehavior ],",
            "});"));
  }

  public void testInlineLiteralBehavior() {
    test(
        LINE_JOINER.join(
            "/** @polymerBehavior */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "  /** @param {string} funAmount */",
            "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "  /** @override */",
            "  created: function() {}",
            "};",
            "/** @polymerBehavior */",
            "var SuperCoolBehaviors = [FunBehavior, {",
            "  properties: {",
            "    howRad: Number",
            "  },",
            "  /** @param {number} radAmount */",
            "  doSomethingRad: function(radAmount) { alert('Something ' + radAmount + ' rad!'); },",
            "  /** @override */",
            "  ready: function() {}",
            "}];",
            "var A = Polymer({",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  },",
            "  behaviors: [ SuperCoolBehaviors ],",
            "});"),

        LINE_JOINER.join(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  created: function() {}",
            "};",
            "/** @polymerBehavior @nocollapse */",
            "var SuperCoolBehaviors = [FunBehavior, {",
            "  properties: {",
            "    howRad: Number",
            "  },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  doSomethingRad: function(radAmount) { alert('Something ' + radAmount + ' rad!'); },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  ready: function() {}",
            "}];",
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface}*/",
            "var A = function() {};",
            "/** @type {boolean} */",
            "A.prototype.isFun;",
            "/** @type {number} */",
            "A.prototype.howRad;",
            "/** @type {!Array} */",
            "A.prototype.pets;",
            "/** @type {string} */",
            "A.prototype.name;",
            "/** @param {string} funAmount */",
            "A.prototype.doSomethingFun = function(funAmount) {",
            "  alert('Something ' + funAmount + ' fun!');",
            "};",
            "/** @param {number} radAmount */",
            "A.prototype.doSomethingRad = function(radAmount) {",
            "  alert('Something ' + radAmount + ' rad!');",
            "};",
            "A = Polymer(/** @lends {A.prototype} */ {",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  },",
            "  behaviors: [ SuperCoolBehaviors ],",
            "});"));
  }

  /**
   * If an element has two or more behaviors which define the same function, only the last
   * behavior's function should be copied over to the element's prototype.
   */
  public void testBehaviorFunctionOverriding() {
    test(
        LINE_JOINER.join(
            "/** @polymerBehavior */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "  /** @param {boolean} boredYet */",
            "  doSomething: function(boredYet) { alert(boredYet + ' ' + this.isFun); },",
            "  /** @override */",
            "  created: function() {}",
            "};",
            "/** @polymerBehavior */",
            "var RadBehavior = {",
            "  properties: {",
            "    howRad: Number",
            "  },",
            "  /** @param {boolean} boredYet */",
            "  doSomething: function(boredYet) { alert(boredYet + ' ' + this.howRad); },",
            "  /** @override */",
            "  ready: function() {}",
            "};",
            "/** @polymerBehavior */",
            "var SuperCoolBehaviors = [FunBehavior, RadBehavior];",
            "/** @polymerBehavior */",
            "var BoringBehavior = {",
            "  properties: {",
            "    boringString: String",
            "  },",
            "  /** @param {boolean} boredYet */",
            "  doSomething: function(boredYet) { alert(boredYet + ' ' + this.boringString); },",
            "};",
            "var A = Polymer({",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  },",
            "  behaviors: [ SuperCoolBehaviors, BoringBehavior ],",
            "});"),

        LINE_JOINER.join(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  doSomething: function(boredYet) { alert(boredYet + ' ' + this.isFun); },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  created: function() {}",
            "};",
            "/** @polymerBehavior @nocollapse */",
            "var RadBehavior = {",
            "  properties: {",
            "    howRad: Number",
            "  },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  doSomething: function(boredYet) { alert(boredYet + ' ' + this.howRad); },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  ready: function() {}",
            "};",
            "/** @polymerBehavior @nocollapse */",
            "var SuperCoolBehaviors = [FunBehavior, RadBehavior];",
            "/** @polymerBehavior @nocollapse */",
            "var BoringBehavior = {",
            "  properties: {",
            "    boringString: String",
            "  },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  doSomething: function(boredYet) { alert(boredYet + ' ' + this.boringString); },",
            "};",
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface}*/",
            "var A = function() {};",
            "/** @type {boolean} */",
            "A.prototype.isFun;",
            "/** @type {number} */",
            "A.prototype.howRad;",
            "/** @type {string} */",
            "A.prototype.boringString;",
            "/** @type {!Array} */",
            "A.prototype.pets;",
            "/** @type {string} */",
            "A.prototype.name;",
            "/** @param {boolean} boredYet */",
            "A.prototype.doSomething = function(boredYet) {",
            "  alert(boredYet + ' ' + this.boringString);",
            "};",
            "A = Polymer(/** @lends {A.prototype} */ {",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  },",
            "  behaviors: [ SuperCoolBehaviors, BoringBehavior ],",
            "});"));
  }

  public void testBehaviorShorthandFunctionOverriding() {
    testEs6(
        LINE_JOINER.join(
            "/** @polymerBehavior */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "  /** @param {boolean} boredYet */",
            "  doSomething(boredYet) { alert(boredYet + ' ' + this.isFun); },",
            "};",
            "",
            "/** @polymerBehavior */",
            "var RadBehavior = {",
            "  properties: {",
            "    howRad: Number",
            "  },",
            "  /** @param {boolean} boredYet */",
            "  doSomething(boredYet) { alert(boredYet + ' ' + this.howRad); },",
            "};",
            "",
            "/** @polymerBehavior */",
            "var SuperCoolBehaviors = [FunBehavior, RadBehavior];",
            "",
            "/** @polymerBehavior */",
            "var BoringBehavior = {",
            "  properties: {",
            "    boringString: String",
            "  },",
            "  /** @param {boolean} boredYet */",
            "  doSomething(boredYet) { alert(boredYet + ' ' + this.boringString); },",
            "};",
            "",
            "var A = Polymer({",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  },",
            "  behaviors: [ SuperCoolBehaviors, BoringBehavior ],",
            "});"),

        LINE_JOINER.join(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  doSomething(boredYet) { alert(boredYet + ' ' + this.isFun); },",
            "};",
            "",
            "/** @polymerBehavior @nocollapse */",
            "var RadBehavior = {",
            "  properties: {",
            "    howRad: Number",
            "  },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  doSomething(boredYet) { alert(boredYet + ' ' + this.howRad); },",
            "};",
            "",
            "/** @polymerBehavior @nocollapse */",
            "var SuperCoolBehaviors = [FunBehavior, RadBehavior];",
            "/** @polymerBehavior @nocollapse */",
            "var BoringBehavior = {",
            "  properties: {",
            "    boringString: String",
            "  },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  doSomething(boredYet) { alert(boredYet + ' ' + this.boringString); },",
            "};",
            "",
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface}*/",
            "var A = function() {};",
            "",
            "/** @type {boolean} */",
            "A.prototype.isFun;",
            "/** @type {number} */",
            "A.prototype.howRad;",
            "/** @type {string} */",
            "A.prototype.boringString;",
            "/** @type {!Array} */",
            "A.prototype.pets;",
            "/** @type {string} */",
            "A.prototype.name;",
            "/** @param {boolean} boredYet */",
            "A.prototype.doSomething = function(boredYet) {",
            "  alert(boredYet + ' ' + this.boringString);",
            "};",
            "",
            "A = Polymer(/** @lends {A.prototype} */ {",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  },",
            "  behaviors: [ SuperCoolBehaviors, BoringBehavior ],",
            "});"));
  }

  public void testBehaviorReadOnlyProp() {
    String js =
        LINE_JOINER.join(
            "/** @polymerBehavior */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: {",
            "      type: Boolean,",
            "      readOnly: true,",
            "    },",
            "  },",
            "  /** @param {string} funAmount */",
            "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "  /** @override */",
            "  created: function() {}",
            "};",
            "var A = Polymer({",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  },",
            "  behaviors: [ FunBehavior ],",
            "});");

    test(
        js,
        LINE_JOINER.join(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: {",
            "      type: Boolean,",
            "      readOnly: true,",
            "    },",
            "  },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  created: function() {}",
            "};",
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface}*/",
            "var A = function() {};",
            "/** @type {boolean} */",
            "A.prototype.isFun;",
            "/** @type {!Array} */",
            "A.prototype.pets;",
            "/** @type {string} */",
            "A.prototype.name;",
            "/** @param {string} funAmount */",
            "A.prototype.doSomethingFun = function(funAmount) {",
            "  alert('Something ' + funAmount + ' fun!');",
            "};",
            "/** @override */",
            "A.prototype._setIsFun = function(isFun) {};",
            "A = Polymer(/** @lends {A.prototype} */ {",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  },",
            "  behaviors: [ FunBehavior ],",
            "});"));

    testExternChanges(EXTERNS, js, BEHAVIOR_READONLY_EXTERNS);
  }

  /**
   * Behaviors whose declarations are not in the global scope may contain references to
   * symbols which do not exist in the element's scope. Only copy a function stub.
   */
  public void testBehaviorInIIFE() {
    test(
        LINE_JOINER.join(
            "(function() {",
            "  /** @polymerBehavior */",
            "  Polymer.FunBehavior = {",
            "    properties: {",
            "      /** @type {boolean} */",
            "      isFun: {",
            "        type: Boolean,",
            "        value: true,",
            "      }",
            "    },",
            "    /** @type {string} */",
            "    foo: 'hooray',",
            "    /** @return {number} */",
            "    get someNumber() {",
            "      return 5*7+2;",
            "    },",
            "    /** @param {string} funAmount */",
            "    doSomethingFun: function(funAmount) {",
            "      alert('Something ' + funAmount + ' fun!');",
            "    },",
            "    /** @override */",
            "    created: function() {}",
            "  };",
            "})();",
            "var A = Polymer({",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  },",
            "  behaviors: [ Polymer.FunBehavior ],",
            "});"),

        LINE_JOINER.join(
            "(function() {",
            "  /** @polymerBehavior @nocollapse */",
            "  Polymer.FunBehavior = {",
            "    properties: {",
            "      isFun: {",
            "        type: Boolean,",
            "        value: true,",
            "      }",
            "    },",
            "    /** @type {string} */",
            "    foo: 'hooray',",
            "    /** @suppress {checkTypes|globalThis} */",
            "    get someNumber() {",
            "      return 5*7+2;",
            "    },",
            "    /** @suppress {checkTypes|globalThis} */",
            "    doSomethingFun: function(funAmount) {",
            "      alert('Something ' + funAmount + ' fun!');",
            "    },",
            "    /** @suppress {checkTypes|globalThis} */",
            "    created: function() {}",
            "  };",
            "})();",
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface}*/",
            "var A = function() {};",
            "/** @type {boolean} */",
            "A.prototype.isFun;",
            "/** @type {!Array} */",
            "A.prototype.pets;",
            "/** @type {string} */",
            "A.prototype.name;",
            "/** @param {string} funAmount */",
            "A.prototype.doSomethingFun = function(funAmount) {};",
            "/** @type {string} */",
            "A.prototype.foo;",
            "/** @type {number} */",
            "A.prototype.someNumber;",
            "A = Polymer(/** @lends {A.prototype} */ {",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  },",
            "  behaviors: [ Polymer.FunBehavior ],",
            "});"));
  }

  public void testDuplicatedBehaviorsAreCopiedOnce() {
    test(
        LINE_JOINER.join(
            "/** @polymerBehavior */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "  /** @param {string} funAmount */",
            "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "  /** @override */",
            "  created: function() {}",
            "};",
            "",
            "/** @polymerBehavior */",
            "var RadBehavior = {",
            "  properties: {",
            "    howRad: Number",
            "  },",
            "  /** @param {number} radAmount */",
            "  doSomethingRad: function(radAmount) { alert('Something ' + radAmount + ' rad!'); },",
            "  /** @override */",
            "  ready: function() {}",
            "};",
            "",
            "/** @polymerBehavior */",
            "var SuperCoolBehaviors = [FunBehavior, RadBehavior];",
            "var A = Polymer({",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  },",
            "  behaviors: [ SuperCoolBehaviors, FunBehavior ],",
            "});"),

        LINE_JOINER.join(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  created: function() {}",
            "};",
            "/** @polymerBehavior @nocollapse */",
            "var RadBehavior = {",
            "  properties: {",
            "    howRad: Number",
            "  },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  doSomethingRad: function(radAmount) { alert('Something ' + radAmount + ' rad!'); },",
            "  /** @suppress {checkTypes|globalThis} */",
            "  ready: function() {}",
            "};",
            "/** @polymerBehavior @nocollapse */",
            "var SuperCoolBehaviors = [FunBehavior, RadBehavior];",
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface}*/",
            "var A = function() {};",
            "/** @type {number} */",
            "A.prototype.howRad;",
            "/** @type {boolean} */",
            "A.prototype.isFun;",
            "/** @type {!Array} */",
            "A.prototype.pets;",
            "/** @type {string} */",
            "A.prototype.name;",
            "/** @param {number} radAmount */",
            "A.prototype.doSomethingRad = function(radAmount) {",
            "  alert('Something ' + radAmount + ' rad!');",
            "};",
            "/** @param {string} funAmount */",
            "A.prototype.doSomethingFun = function(funAmount) {",
            "  alert('Something ' + funAmount + ' fun!');",
            "};",
            "A = Polymer(/** @lends {A.prototype} */ {",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  },",
            "  behaviors: [ SuperCoolBehaviors, FunBehavior ],",
            "});"));
  }

  public void testInvalid1() {
    disableTypeCheck();
    testWarning("var x = Polymer('blah');", POLYMER_DESCRIPTOR_NOT_VALID);
    testWarning("var x = Polymer('foo-bar', {});", POLYMER_DESCRIPTOR_NOT_VALID);
    testError("var x = Polymer({},'blah');", POLYMER_UNEXPECTED_PARAMS);
    testError("var x = Polymer({});", POLYMER_MISSING_IS);
    testErrorEs6("var x = Polymer({is});", POLYMER_MISSING_IS);
    testErrorEs6("var x = Polymer({is: 'x-element', shortHand,});",
        POLYMER_SHORTHAND_NOT_SUPPORTED);
  }

  public void testInvalidProperties() {
    testError(
        LINE_JOINER.join(
            "Polymer({",
            "  is: 'x-element',",
            "  properties: {",
            "    isHappy: true,",
            "  },",
            "});"),
        POLYMER_INVALID_PROPERTY);

    testError(
        LINE_JOINER.join(
            "Polymer({",
            "  is: 'x-element',",
            "  properties: {",
            "    isHappy: {",
            "      value: true,",
            "    },",
            "  },",
            "});"),
        POLYMER_INVALID_PROPERTY);

    testError(
        LINE_JOINER.join(
            "var foo = {};",
            "foo.bar = {};",
            "Polymer({",
            "  is: 'x-element',",
            "  properties: {",
            "    isHappy: {",
            "      type: foo.Bar,",
            "      value: true,",
            "    },",
            "  },",
            "});"),
        POLYMER_INVALID_PROPERTY);
  }

  public void testInvalidBehavior() {
    testError(
        LINE_JOINER.join(
            "(function() {",
            "  var isNotGloabl = {};",
            "  Polymer({",
            "    is: 'x-element',",
            "    behaviors: [",
            "      isNotGlobal",
            "    ],",
            "  });",
            "})();"),
        POLYMER_UNQUALIFIED_BEHAVIOR);

    testError(
        LINE_JOINER.join(
            "var foo = {};",
            "(function() {",
            "  Polymer({",
            "    is: 'x-element',",
            "    behaviors: [",
            "      foo.IsNotDefined",
            "    ],",
            "  });",
            "})();"),
        POLYMER_UNQUALIFIED_BEHAVIOR);

    testError(
        LINE_JOINER.join(
            "var foo = {};",
            "foo.Bar;",
            "(function() {",
            "  Polymer({",
            "    is: 'x-element',",
            "    behaviors: [",
            "      foo.Bar",
            "    ],",
            "  });",
            "})();"),
        POLYMER_UNQUALIFIED_BEHAVIOR);

    testError(
        LINE_JOINER.join(
            "Polymer({", "  is: 'x-element',", "  behaviors: [", "    DoesNotExist", "  ],", "});"),
        POLYMER_UNQUALIFIED_BEHAVIOR);
  }

  public void testUnannotatedBehavior() {
    testError(
        LINE_JOINER.join(
            "var FunBehavior = {",
            "  /** @override */",
            "  created: function() {}",
            "};",
            "var A = Polymer({",
            "  is: 'x-element',",
            "  behaviors: [ FunBehavior ],",
            "});"),
        POLYMER_UNANNOTATED_BEHAVIOR);
  }

  public void testInvalidTypeAssignment() {
    test(
        LINE_JOINER.join(
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
        LINE_JOINER.join(
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface} */",
            "var X = function() {};",
            "/** @type {boolean} */",
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

  public void testES6FeaturesInFunctionBody() {
    disableTypeCheck();
    testEs6(
        LINE_JOINER.join(
            "var X = Polymer({",
            "  is: 'x-element',",
            "  funcWithES6() {",
            "    var tag = 'tagged';",
            "    alert(`${tag}Template`);",
            "    var taggedTemp = `${tag}Template`;",
            "    var arrFunc = () => 42;",
            "    var num = arrFunc();",
            "    var obj = {one: 1, two: 2, three: 3};",
            "    var {one, two, three} = obj;",
            "    var arr = [1, 2, 3];",
            "    var [eins, zwei, drei] = arr;",
            "  },",
            "});"),
        LINE_JOINER.join(
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface} */",
            "var X = function() {};",
            "X = Polymer(/** @lends {X.prototype} */ {",
            "  is: 'x-element',",
            "  /** @this {X} */",
            "  funcWithES6() {",
            "    var tag = 'tagged';",
            "    alert(`${tag}Template`);",
            "    var taggedTemp = `${tag}Template`;",
            "    var arrFunc = () => 42;",
            "    var num = arrFunc();",
            "    var obj = {one: 1, two: 2, three: 3};",
            "    var {one, two, three} = obj;",
            "    var arr = [1, 2, 3];",
            "    var [eins, zwei, drei] = arr;",
            "  },",
            "});"));
  }
}
