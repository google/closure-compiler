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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.PolymerClassRewriter.POLYMER_ELEMENT_PROP_CONFIG;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_CLASS_PROPERTIES_INVALID;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_CLASS_PROPERTIES_NOT_STATIC;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_DESCRIPTOR_NOT_VALID;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_INVALID_DECLARATION;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_INVALID_EXTENDS;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_INVALID_PROPERTY;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_MISSING_IS;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_UNANNOTATED_BEHAVIOR;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_UNEXPECTED_PARAMS;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_UNQUALIFIED_BEHAVIOR;
import static com.google.javascript.jscomp.TypeValidator.TYPE_MISMATCH_WARNING;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.NodeUtil.Visitor;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for PolymerPass
 *
 * @author jlklein@google.com (Jeremy Klein)
 */
@RunWith(JUnit4.class)
public class PolymerPassTest extends CompilerTestCase {
  private static final String EXTERNS_PREFIX =
      lines(
          MINIMAL_EXTERNS,
          "/** @constructor */",
          "var HTMLElement = function() {};",
          "/** @constructor @extends {HTMLElement} */",
          "var HTMLInputElement = function() {};",
          "/** @constructor @extends {HTMLElement} */",
          "var PolymerElement = function() {};");

  private static final String EXTERNS_SUFFIX =
      lines(
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
          " * @return {!Function}",
          " */",
          "var Polymer = function(a) {};",
          "var alert = function(msg) {};");

  private static final String EXTERNS = lines(EXTERNS_PREFIX, EXTERNS_SUFFIX);

  private static final String INPUT_EXTERNS =
      lines(
          EXTERNS_PREFIX,
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
          EXTERNS_SUFFIX,
          "/** @interface */",
          "var PolymerXInputElementInterface = function() {};");

  private static final String REFLECT_OBJECT_DEF =
      lines(
          "/** @const */ var $jscomp = $jscomp || {};",
          "/** @const */ $jscomp.scope = {};",
          "/**",
          " * @param {?Object} type",
          " * @param {T} object",
          " * @return {T}",
          " * @template T",
          " */",
          "$jscomp.reflectObject = function (type, object) { return object; };");

  private int polymerVersion = 1;
  private PolymerExportPolicy polymerExportPolicy = PolymerExportPolicy.LEGACY;
  private boolean propertyRenamingEnabled = false;

  public PolymerPassTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new PolymerPass(compiler, polymerVersion, polymerExportPolicy, propertyRenamingEnabled);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
    allowExternsChanges();
    enableRunTypeCheckAfterProcessing();
    enableParseTypeInfo();
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Test
  public void testVarTarget() {
    test(
        lines(
            "var X = Polymer({",
            "  is: 'x-element',",
            "});"),

        lines(
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface} */",
            "var X = function() {};",
            "X = Polymer(/** @lends {X.prototype} */ {",
            "  is: 'x-element',",
            "});"));

    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();

    test(
        lines(
            "var X = class extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() { return {}; }",
            "};"),
        lines(
            "var X = class extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() { return {}; }",
            "};"));
  }

  @Test
  public void testLetTarget() {
    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();

    test(
        lines(
            "let X = Polymer({",
            "  is: 'x-element',",
            "});"),
        lines(
            "/**",
            " * @constructor",
            " * @implements {PolymerXInterface}",
            " * @extends {PolymerElement}",
            " */",
            "var X = function() {};",
            "X = Polymer(/** @lends {X.prototype} */ {is:'x-element'});"));

    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();

    test(
        lines(
            "let X = class extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() { return { }; }",
            "};"),
        lines(
            "let X = class extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() { return {}; }",
            "};"));
  }

  @Test
  public void testConstTarget() {
    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();

    testError(
        lines(
            "const X = Polymer({",
            "  is: 'x-element',",
            "});"), POLYMER_INVALID_DECLARATION);

    test(
        lines(
            "const X = class extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() { return {}; }",
            "};"),
        lines(
            "const X = class extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() { return {}; }",
            "};"));
  }

  @Test
  public void testDefaultTypeNameTarget() {
    test(
        lines(
            "Polymer({",
            "  is: 'x',",
            "});"),

        lines(
            "/**",
            " * @implements {PolymerXElementInterface}",
            " * @constructor @extends {PolymerElement}",
            " */",
            "var XElement = function() {};",
            "Polymer(/** @lends {XElement.prototype} */ {",
            "  is: 'x',",
            "});"));
  }

  @Test
  public void testPathAssignmentTarget() {
    test(
        lines(
            "/** @const */ var x = {};",
            "x.Z = Polymer({",
            "  is: 'x-element',",
            "});"),

        lines(
            "/** @const */ var x = {};",
            "/** @constructor @extends {PolymerElement} @implements {Polymerx_ZInterface} */",
            "x.Z = function() {};",
            "x.Z = Polymer(/** @lends {x.Z.prototype} */ {",
            "  is: 'x-element',",
            "});"));

    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();

    test(
        lines(
            "const x = {};",
            "x.Z = class extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() { return {}; }",
            "};"),
        lines(
            "const x = {};",
            "x.Z = class extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() { return {}; }",
            "};"));
  }

  @Test
  public void testComputedPropName() {
    // Type checker doesn't currently understand ES6 code. Remove when it does.
    // TypeCheck cannot grab a name from a complicated computedPropName
    disableTypeCheck();

    test("var X = Polymer({is:'x-element', [name + (() => 42)]: function() {return 42;}});",
        lines(
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
   * Since 'x' is a global name, the type system understands 'x.Z' as a type name, so there is no
   * need to extract the type to the global namespace.
   */
  @Test
  public void testIIFEExtractionInGlobalNamespace() {
    test(
        lines(
            "/** @const */ var x = {};",
            "(function() {",
            "  x.Z = Polymer({",
            "    is: 'x-element',",
            "    sayHi: function() { alert('hi'); },",
            "  });",
            "})()"),

        lines(
            "/** @const */ var x = {};",
            "(function() {",
            "  /** @constructor @extends {PolymerElement} @implements {Polymerx_ZInterface}*/",
            "  x.Z = function() {};",
            "  x.Z = Polymer(/** @lends {x.Z.prototype} */ {",
            "    is: 'x-element',",
            "    /** @this {x.Z} */",
            "    sayHi: function() { alert('hi'); },",
            "  });",
            "})()"));

    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();

    test(
        lines(
            "const x = {};",
            "(function() {",
            "  x.Z = class extends Polymer.Element {",
            "    static get is() { return 'x-element'; }",
            "    static get properties() { return {}; }",
            "  };",
            "})();"),
        lines(
            "const x = {};",
            "(function() {",
            "  x.Z = class extends Polymer.Element {",
            "    /** @return {string} */",
            "    static get is() { return 'x-element'; }",
            "    /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "    static get properties() { return {}; }",
            "  };",
            "})();"));
  }

  /**
   * The definition of XElement is placed in the global namespace, outside the IIFE so that the type
   * system will understand that XElement is a type.
   */
  @Test
  public void testIIFEExtractionNoAssignmentTarget() {
    test(
        lines(
            "(function() {",
            "  Polymer({",
            "    is: 'x',",
            "    sayHi: function() { alert('hi'); },",
            "  });",
            "})()"),
        lines(
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

    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();

    test(
        lines(
            "(function() {",
            "  const X = class extends Polymer.Element {",
            "    static get is() { return 'x-element'; }",
            "    static get properties() { return {}; }",
            "  };",
            "})();"),
        lines(
            "(function() {",
            "  const X = class extends Polymer.Element {",
            "    /** @return {string} */",
            "    static get is() { return 'x-element'; }",
            "    /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "    static get properties() { return {}; }",
            "  };",
            "})();"));
  }

  /**
   * The definition of FooThing is placed in the global namespace, outside the IIFE so that the type
   * system will understand that FooThing is a type.
   */
  @Test
  public void testIIFEExtractionVarTarget() {
    test(
        1,
        lines(
            "(function() {",
            "  var FooThing = Polymer({",
            "    is: 'x',",
            "    sayHi: function() { alert('hi'); },",
            "  });",
            "})()"),
        lines(
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

  @Test
  public void testConstructorExtraction() {
    test(
        lines(
            "var X = Polymer({",
            "  is: 'x-element',",
            "  /**",
            "   * @param {string} name",
            "   */",
            "  factoryImpl: function(name) { alert('hi, ' + name); },",
            "});"),

        lines(
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

  @Test
  public void testShorthandConstructorExtraction() {
    test(
        lines(
            "var X = Polymer({",
            "  is: 'x-element',",
            "  /**",
            "   * @param {string} name",
            "   */",
            "  factoryImpl(name) { alert('hi, ' + name); },",
            "});"),

        lines(
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

  @Test
  public void testOtherKeysIgnored() {
    test(
        lines(
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

        lines(
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

    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();

    test(
        lines(
            "class X extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  handleClick(e) {",
            "    alert('Thank you for clicking');",
            "  }",
            "}"),
        lines(
            "class X extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  handleClick(e) {",
            "    alert('Thank you for clicking');",
            "  }",
            "}"));
  }

  @Test
  public void testListenersAndHostAttributeKeysQuoted() {
    test(
        lines(
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

        lines(
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

  @Test
  public void testNativeElementExtension() {
    String js = lines(
        "Polymer({",
        "  is: 'x-input',",
        "  extends: 'input',",
        "});");

    test(
        js,
        lines(
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

  @Test
  public void testExtendNonExistentElement() {
    polymerVersion = 1;
    String js = lines(
        "Polymer({",
        "  is: 'x-input',",
        "  extends: 'nonexist',",
        "});");

    testError(js, POLYMER_INVALID_EXTENDS);
  }

  @Test
  public void testNativeElementExtensionExternsNotDuplicated() {
    String js =
        lines(
            "Polymer({",
            "  is: 'x-input',",
            "  extends: 'input',",
            "});",
            "Polymer({",
            "  is: 'y-input',",
            "  extends: 'input',",
            "});");
    String newExterns = lines(
        INPUT_EXTERNS,
        "",
        "/** @interface */",
        "var PolymerYInputElementInterface = function() {};");

    testExternChanges(EXTERNS, js, newExterns);
  }

  @Test
  public void testPropertiesAddedToPrototype() {
    test(
        lines(
            "/** @constructor */",
            "var User = function() {};",
            "/** @const */ var a = {};",
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

        lines(
            "/** @constructor */",
            "var User = function() {};",
            "/** @const */ var a = {};",
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

    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();

    test(
        lines(
            "/** @constructor */",
            "var User = function() {};",
            "var a = {};",
            "a.B = class extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() {",
            "    return {",
            "      /** @type {!User} @private */",
            "      user_: Object,",
            "      pets: {",
            "        type: Array,",
            "        notify: true,",
            "      },",
            "      name: String,",
            "      thingToDo: Function,",
            "    };",
            "  }",
            "};"),
        lines(
            "/** @constructor */",
            "var User = function() {};",
            "var a = {};",
            "a.B = class extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() {",
            "    return {",
            "      user_: Object,",
            "      pets: {",
            "        type: Array,",
            "        notify: true,",
            "      },",
            "      name: String,",
            "      thingToDo: Function,",
            "    };",
            "  }",
            "};",
            "/** @type {!User} @private */",
            "a.B.prototype.user_;",
            "/** @type {!Array} */",
            "a.B.prototype.pets;",
            "/** @type {string} */",
            "a.B.prototype.name;",
            "/** @type {!Function} */",
            "a.B.prototype.thingToDo;"));
  }

  @Test
  public void testPropertiesDefaultValueFunctions() {
    test(
        lines(
            "/** @constructor */",
            "var User = function() {};",
            "/** @const */ var a = {};",
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

        lines(
            "/** @constructor */",
            "var User = function() {};",
            "/** @const */ var a = {};",
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

    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();

    test(
        lines(
            "/** @constructor */",
            "var User = function() {};",
            "/** @const */ var a = {};",
            "a.B = class extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() {",
            "    return {",
            "      /** @type {!User} @private */",
            "      user_: {",
            "        type: Object,",
            "        value: function() { return new User(); },",
            "      },",
            "      pets: {",
            "        type: Array,",
            "        notify: true,",
            "        value: function() { return [this.name]; },",
            "      },",
            "      name: String,",
            "    };",
            "  }",
            "};"),
        lines(
            "/** @constructor */",
            "var User = function() {};",
            "/** @const */ var a = {};",
            "a.B = class extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() {",
            "    return {",
            "      user_: {",
            "        type: Object,",
            "        /** @this {a.B} @return {!User} */",
            "        value: function() { return new User(); },",
            "      },",
            "      pets: {",
            "        type: Array,",
            "        notify: true,",
            "        /** @this {a.B} @return {!Array} */",
            "        value: function() { return [this.name]; },",
            "      },",
            "      name: String,",
            "    };",
            "  }",
            "};",
            "/** @type {!User} @private */",
            "a.B.prototype.user_;",
            "/** @type {!Array} */",
            "a.B.prototype.pets;",
            "/** @type {string} */",
            "a.B.prototype.name;"));
  }

  @Test
  public void testPropertiesDefaultValueShortHandFunction() {
    test(
        lines(
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
        lines(
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

    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();

    test(
        lines(
            "/** @constructor */",
            "var User = function() {};",
            "var a = {};",
            "a.B = class extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() {",
            "    return {",
            "      /** @type {!User} @private */",
            "      user_: {",
            "        type: Object,",
            "        value() { return new User(); },",
            "      },",
            "      pets: {",
            "        type: Array,",
            "        notify: true,",
            "        value() { return [this.name]; },",
            "      },",
            "      name: String,",
            "    };",
            "  }",
            "};"),
        lines(
            "/** @constructor */",
            "var User = function() {};",
            "var a = {};",
            "a.B = class extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() {",
            "    return {",
            "      user_: {",
            "        type: Object,",
            "        /** @this {a.B} @return {!User} */",
            "        value() { return new User(); },",
            "      },",
            "      pets: {",
            "        type: Array,",
            "        notify: true,",
            "        /** @this {a.B} @return {!Array} */",
            "        value() { return [this.name]; },",
            "      },",
            "      name: String,",
            "    };",
            "  }",
            "};",
            "/** @type {!User} @private */",
            "a.B.prototype.user_;",
            "/** @type {!Array} */",
            "a.B.prototype.pets;",
            "/** @type {string} */",
            "a.B.prototype.name;"));
  }

  @Test
  public void testReadOnlyPropertySetters() {
    String js =
        lines(
            "/** @const */ var a = {};",
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
        1,
        js,
        lines(
            "/** @const */ var a = {};",
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

    testExternChanges(
        1,
        EXTERNS,
        js,
        lines(
            EXTERNS,
            "/** @interface */",
            "var Polymera_BInterface = function() {};",
            "/** @type {!Array<string>} */",
            "Polymera_BInterface.prototype.pets;",
            "/** @private {string} */",
            "Polymera_BInterface.prototype.name_;",
            "/** @param {!Array<string>} pets **/",
            "Polymera_BInterface.prototype._setPets;"));

    test(
        2,
        js,
        lines(
            "/** @const */ var a = {};",
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

    testExternChanges(
        2,
        EXTERNS,
        js,
        lines(
            EXTERNS,
            "/** @interface */",
            "var Polymera_BInterface = function() {};",
            "/** @type {!Array<string>} */",
            "Polymera_BInterface.prototype.pets;",
            "/** @param {!Array<string>} pets **/",
            "Polymera_BInterface.prototype._setPets;"));

    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();

    String jsClass = lines(
        "class A extends Polymer.Element {",
        "  static get is() { return 'a-element'; }",
        "  static get properties() {",
        "    return {",
        "      pets: {",
        "        type: Array,",
        "        readOnly: true",
        "      }",
        "    };",
        "  }",
        "}");

    testExternChanges(
        2,
        EXTERNS,
        jsClass,
        lines(
            EXTERNS,
            "/** @interface */",
            "var PolymerAInterface = function() {};",
            "/** @type {!Array} */",
            "PolymerAInterface.prototype.pets;",
            "/** @param {!Array} pets **/",
            "PolymerAInterface.prototype._setPets;"));

    test(
        2,
        jsClass,
        lines(
            "/** @implements {PolymerAInterface} */",
            "class A extends Polymer.Element {",
            "  /** @return {string} */ static get is() { return 'a-element'; }",
            "  /** @return {PolymerElementProperties} */ static get properties() {",
            "    return {",
            "      pets: {",
            "        type: Array,",
            "        readOnly: true",
            "      }",
            "    };",
            "  }",
            "}",
            "/** @type {!Array} */",
            "A.prototype.pets;",
            "/** @override */",
            "A.prototype._setPets = function(pets) {};"));
  }

  @Test
  public void testReflectToAttributeProperties() {
    String js =
        lines(
            "/** @const */ var a = {};",
            "a.B = Polymer({",
            "  is: 'x-element',",
            "  properties: {",
            "    /** @type {!Array<string>} */",
            "    pets: {",
            "      type: Array,",
            "      readOnly: false,",
            "    },",
            "    name: {",
            "      type: String,",
            "      reflectToAttribute: true",
            "    }",
            "  },",
            "});");

    test(
        1,
        js,
        lines(
            "/** @const */ var a = {};",
            "/** @constructor @extends {PolymerElement} @implements {Polymera_BInterface} */",
            "a.B = function() {};",
            "/** @type {!Array<string>} */",
            "a.B.prototype.pets;",
            "/** @type {string} */",
            "a.B.prototype.name;",
            "a.B = Polymer(/** @lends {a.B.prototype} */ {",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      readOnly: false,",
            "    },",
            "    name: {",
            "      type: String,",
            "      reflectToAttribute: true",
            "    }",
            "  },",
            "});"));

    testExternChanges(
        1,
        EXTERNS,
        js,
        lines(
            EXTERNS,
            "/** @interface */",
            "var Polymera_BInterface = function() {};",
            "/** @type {!Array<string>} */",
            "Polymera_BInterface.prototype.pets;",
            "/** @type {string} */",
            "Polymera_BInterface.prototype.name;"));

    test(
        2,
        js,
        lines(
            "/** @const */ var a = {};",
            "/** @constructor @extends {PolymerElement} @implements {Polymera_BInterface} */",
            "a.B = function() {};",
            "/** @type {!Array<string>} */",
            "a.B.prototype.pets;",
            "/** @type {string} */",
            "a.B.prototype.name;",
            "a.B = Polymer(/** @lends {a.B.prototype} */ {",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      readOnly: false,",
            "    },",
            "    name: {",
            "      type: String,",
            "      reflectToAttribute: true",
            "    }",
            "  },",
            "});"));

    testExternChanges(
        2,
        EXTERNS,
        js,
        lines(
            EXTERNS,
            "/** @interface */",
            "var Polymera_BInterface = function() {};",
            "/** @type {string} */",
            "Polymera_BInterface.prototype.name;"));
  }

  @Test
  public void testPolymerClassObserversTyped() {
    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();
    test(
        lines(
            "class FooElement extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get observers() {",
            "    return [];",
            "  }",
            "}"),
        lines(
            "class FooElement extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {!Array<string>} */",
            "  static get observers() {",
            "    return [];",
            "  }",
            "}"));
  }

  @Test
  public void testShorthandFunctionDefinition() {
    test(
        lines(
            "var ES6Test = Polymer({",
            "  is: 'x-element',",
            "  sayHi() {",
            "    alert('hi');",
            "  },",
            "});"),
        lines(
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

  @Test
  public void testArrowFunctionDefinition() {
    test(
        lines(
            "var ES6Test = Polymer({",
            "  is: 'x-element',",
            "  sayHi: ()=>42,",
            "});"),
        lines(
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

  @Test
  public void testShorthandLifecycleCallbacks() {
    test(
        lines(
            "var ES6Test = Polymer({",
            "  is: 'x-element',",
            "  /** @override */",
            "  created() {",
            "    alert('Shorthand created');",
            "  },",
            "});"),
        lines(
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

  @Test
  public void testShorthandFunctionDefinitionWithReturn() {
    test(
        lines(
            "var ESTest = Polymer({",
            "  is: 'x-element',",
            "  sayHi() {",
            "    return [1, 2];",
            "  },",
            "});"),
        lines(
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

  @Test
  public void testThisTypeAddedToFunctions() {
    test(
        lines(
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

        lines(
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

    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();
    test(
        lines(
            "class Foo extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  sayHi() {",
            "    alert('hi');",
            "  }",
            "  connectedCallback() {",
            "    this.sayHi();",
            "    this.sayHelloTo_('Tester');",
            "  }",
            "  /**",
            "   * @param {string} name",
            "   * @private",
            "   */",
            "  sayHelloTo_(name) {",
            "    alert('Hello, ' + name);",
            "  }",
            "}"),
        lines(
            "class Foo extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  sayHi() {",
            "    alert('hi');",
            "  }",
            "  connectedCallback() {",
            "    this.sayHi();",
            "    this.sayHelloTo_('Tester');",
            "  }",
            "  /**",
            "   * @param {string} name",
            "   * @private",
            "   */",
            "  sayHelloTo_(name) {",
            "    alert('Hello, ' + name);",
            "  }",
            "}"));
  }

  @Test
  public void testDollarSignPropsConvertedToBrackets() {
    test(
        lines(
            "/** @constructor */",
            "var SomeType = function() {};",
            "SomeType.prototype.toggle = function() {};",
            "SomeType.prototype.switch = function() {};",
            "SomeType.prototype.touch = function() {};",
            "var X = Polymer({",
            "  is: 'x-element',",
            "  properties: {",
            "    /** @type {!HTMLElement} */",
            "    propName: {",
            "      type: Object,",
            "      value: function() { return this.$.id; },",
            "    },",
            "  },",
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

        lines(
            "/** @constructor */",
            "var SomeType = function() {};",
            "SomeType.prototype.toggle = function() {};",
            "SomeType.prototype.switch = function() {};",
            "SomeType.prototype.touch = function() {};",
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface} */",
            "var X = function() {};",
            "/** @type {!HTMLElement} */",
            "X.prototype.propName;",
            "X = Polymer(/** @lends {X.prototype} */ {",
            "  is: 'x-element',",
            "  properties: {",
            "    propName: {",
            "      type: Object,",
            "      /** @this {X} @return {!HTMLElement} */",
            "      value: function() { return this.$['id']; },",
            "    },",
            "  },",
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

    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();
    test(
        lines(
            "class Foo extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() {",
            "    return {",
            "      /** @type {!HTMLElement} */",
            "      propName: {",
            "        type: Object,",
            "        value: function() { return this.$.id; },",
            "      }",
            "    };",
            "  }",
            "  sayHi() {",
            "    this.$.checkbox.toggle();",
            "  }",
            "  connectedCallback() {",
            "    this.sayHi();",
            "    this.$.radioButton.switch();",
            "  }",
            "  /**",
            "   * @param {string} name",
            "   * @private",
            "   */",
            "  sayHelloTo_(name) {",
            "    this.$.otherThing.touch();",
            "  }",
            "}"),
        lines(
            "class Foo extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() {",
            "    return {",
            "      propName: {",
            "        type: Object,",
            "        /** @this {Foo} @return {!HTMLElement} */",
            "        value: function() { return this.$['id']; },",
            "      }",
            "    };",
            "  }",
            "  sayHi() {",
            "    this.$['checkbox'].toggle();",
            "  }",
            "  connectedCallback() {",
            "    this.sayHi();",
            "    this.$['radioButton'].switch();",
            "  }",
            "  /**",
            "   * @param {string} name",
            "   * @private",
            "   */",
            "  sayHelloTo_(name) {",
            "    this.$['otherThing'].touch();",
            "  }",
            "}",
            "/** @type {!HTMLElement} */",
            "Foo.prototype.propName;"));
  }

  @Test
  public void testDollarSignPropsInShorthandFunctionConvertedToBrackets() {
    test(
        lines(
            "/** @constructor */",
            "var SomeType = function() {};",
            "SomeType.prototype.toggle = function() {};",
            "var ES6Test = Polymer({",
            "  is: 'x-element',",
            "  sayHi() {",
            "    this.$.checkbox.toggle();",
            "  },",
            "});"),

        lines(
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
  @Test
  public void testBehaviorForMultipleElements() {
    test(
        lines(
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

        lines(
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

  @Test
  public void testSimpleBehavior() {
    test(
        srcs(lines(
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
            "});")),
        expected(lines(
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
            "  /** @suppress {checkTypes|globalThis|visibility} */",
            "  get someNumber() {",
            "    return 5*7+2;",
            "  },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
            "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
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
            "/**",
            " * @param {string} funAmount",
            " * @suppress {unusedPrivateMembers}",
            " */",
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
            "});")),
        (Postcondition)
            compiler -> {
              // The original doSomethingFun definition in FunBehavior is on line 21, so make sure
              // that line number is preserved when it's copied into the Polymer() call.
              Node root = compiler.getRoot();
              DoSomethingFunFinder visitor = new DoSomethingFunFinder();
              NodeUtil.visitPreOrder(root, visitor);
              assertThat(visitor.found).isTrue();
            });
  }

  /** Check that we can resolve behaviors through a chain of identifiers. */
  @Test
  public void testIndirectBehaviorAssignment() {
    test(
        srcs(
            lines(
                "/** @polymerBehavior */",
                "var MyBehavior = {",
                "  properties: {",
                "    behaviorProperty: Boolean",
                "  }",
                "};",
                "var BehaviorAlias1 = MyBehavior;",
                "var BehaviorAlias2 = BehaviorAlias1;",
                "var MyElement = Polymer({",
                "  is: 'my-element',",
                "  behaviors: [ BehaviorAlias2 ]",
                "});")),
        expected(
            lines(
                "/** @polymerBehavior @nocollapse */",
                "var MyBehavior = {",
                "  properties: {",
                "    behaviorProperty: Boolean",
                "  }",
                "};",
                "var BehaviorAlias1 = MyBehavior;",
                "var BehaviorAlias2 = BehaviorAlias1;",
                "/**",
                " * @constructor",
                " * @extends {PolymerElement}",
                " * @implements {PolymerMyElementInterface}",
                " */",
                "var MyElement = function(){};",
                "/** @type {boolean} */",
                "MyElement.prototype.behaviorProperty;",
                "MyElement = Polymer(/** @lends {MyElement.prototype} */ {",
                "  is: 'my-element',",
                "  behaviors: [ BehaviorAlias2 ]",
                "});")));
  }

  private static class DoSomethingFunFinder implements Visitor {
    boolean found = false;

    @Override
    public void visit(Node n) {
      if (n.matchesQualifiedName("A.prototype.doSomethingFun")) {
        assertNode(n).hasLineno(21);
        found = true;
      }
    }
  }

  /** If a behavior method is {@code @protected} there is no visibility warning. */
  @Test
  public void testBehaviorWithProtectedMethod() {
    enableCheckAccessControls();
    for (int i = 1; i <= 2; i++) {
      this.polymerVersion = i;
      test(
          new String[] {
              lines(
                  "/** @polymerBehavior */",
                  "var FunBehavior = {",
                  "  /** @protected */",
                  "  doSomethingFun: function() {},",
                  "};"),
              lines(
                  "var A = Polymer({",
                  "  is: 'x-element',",
                  "  callBehaviorMethod: function() {",
                  "    this.doSomethingFun();",
                  "  },",
                  "  behaviors: [ FunBehavior ],",
                  "});"),
          },
          new String[] {
              lines(
                  "/** @polymerBehavior @nocollapse */",
                  "var FunBehavior = {",
                  "  /**",
                  "   * @suppress {checkTypes|globalThis|visibility}",
                  "   */",
                  "  doSomethingFun: function() {},",
                  "};"),
              lines(
                  "/**",
                  " * @constructor",
                  " * @extends {PolymerElement}",
                  " * @implements {PolymerAInterface}",
                  " */",
                  "var A = function() {};",
                  "",
                  "/**",
                  " * @public",
                  " * @suppress {unusedPrivateMembers}",
                  " */",
                  "A.prototype.doSomethingFun = function(){};",
                  "",
                  "A = Polymer(/** @lends {A.prototype} */ {",
                  "  is: 'x-element',",
                  "  /** @this {A} */",
                  "  callBehaviorMethod: function(){ this.doSomethingFun(); },",
                  "  behaviors: [FunBehavior],",
                  "})"),
          });
    }
  }

  /** If a behavior method is {@code @private} there is a visibility warning. */
  @Test
  public void testBehaviorWithPrivateMethod() {
    enableCheckAccessControls();
    testWarning(
        new String[] {
          lines(
              "/** @polymerBehavior */",
              "var FunBehavior = {",
              "  /** @private */",
              "  doSomethingFun: function() {},",
              "};"),
          lines(
              "var A = Polymer({",
              "  is: 'x-element',",
              "  callBehaviorMethod: function() {",
              "    this.doSomethingFun();",
              "  },",
              "  behaviors: [ FunBehavior ],",
              "});"),
        },
        CheckAccessControls.BAD_PRIVATE_PROPERTY_ACCESS);
  }

  /**
   * Test that if a behavior function is implemented by the Element, the function from the behavior
   * is not copied to the prototype of the Element.
   */
  @Test
  public void testBehaviorFunctionOverriddenByElement() {
    test(
        lines(
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

        lines(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
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

  @Test
  public void testBehaviorShorthandFunctionOverriddenByElement() {
    test(
        lines(
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

        lines(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
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

  @Test
  public void testBehaviorDefaultValueSuppression() {
    test(
        lines(
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

        lines(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: {",
            "      type: Boolean,",
            "      value: true,",
            "    },",
            "    funObject: {",
            "      type: Object,",
            "      /** @suppress {checkTypes|globalThis|visibility} */",
            "      value: function() { return {fun: this.isFun }; },",
            "    },",
            "    funArray: {",
            "      type: Array,",
            "      /** @suppress {checkTypes|globalThis|visibility} */",
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

  @Test
  public void testArrayBehavior() {
    test(
        lines(
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
        lines(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
            "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
            "  created: function() {}",
            "};",
            "/** @polymerBehavior @nocollapse */",
            "var RadBehavior = {",
            "  properties: {",
            "    howRad: Number",
            "  },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
            "  doSomethingRad: function(radAmount) { alert('Something ' + radAmount + ' rad!'); },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
            "  ready: function() {}",
            "};",
            "/** @polymerBehavior @nocollapse */",
            "var SuperCoolBehaviors = [FunBehavior, RadBehavior];",
            "/** @polymerBehavior @nocollapse */",
            "var BoringBehavior = {",
            "  properties: {",
            "    boringString: String",
            "  },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
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
            "/**",
            " * @param {string} funAmount",
            " * @suppress {unusedPrivateMembers}",
            " */",
            "A.prototype.doSomethingFun = function(funAmount) {",
            "  alert('Something ' + funAmount + ' fun!');",
            "};",
            "/**",
            " * @param {number} radAmount",
            " * @suppress {unusedPrivateMembers}",
            " */",
            "A.prototype.doSomethingRad = function(radAmount) {",
            "  alert('Something ' + radAmount + ' rad!');",
            "};",
            "/**",
            " * @param {boolean} boredYet",
            " * @suppress {unusedPrivateMembers}",
            " */",
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

  @Test
  public void testInlineLiteralBehavior() {
    test(
        lines(
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
        lines(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
            "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
            "  created: function() {}",
            "};",
            "/** @polymerBehavior @nocollapse */",
            "var SuperCoolBehaviors = [FunBehavior, {",
            "  properties: {",
            "    howRad: Number",
            "  },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
            "  doSomethingRad: function(radAmount) { alert('Something ' + radAmount + ' rad!'); },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
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
            "/**",
            " * @param {string} funAmount",
            " * @suppress {unusedPrivateMembers}",
            " */",
            "A.prototype.doSomethingFun = function(funAmount) {",
            "  alert('Something ' + funAmount + ' fun!');",
            "};",
            "/**",
            " * @param {number} radAmount",
            " * @suppress {unusedPrivateMembers}",
            " */",
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
  @Test
  public void testBehaviorFunctionOverriding() {
    test(
        lines(
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
        lines(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
            "  doSomething: function(boredYet) { alert(boredYet + ' ' + this.isFun); },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
            "  created: function() {}",
            "};",
            "/** @polymerBehavior @nocollapse */",
            "var RadBehavior = {",
            "  properties: {",
            "    howRad: Number",
            "  },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
            "  doSomething: function(boredYet) { alert(boredYet + ' ' + this.howRad); },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
            "  ready: function() {}",
            "};",
            "/** @polymerBehavior @nocollapse */",
            "var SuperCoolBehaviors = [FunBehavior, RadBehavior];",
            "/** @polymerBehavior @nocollapse */",
            "var BoringBehavior = {",
            "  properties: {",
            "    boringString: String",
            "  },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
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
            "/**",
            " * @param {boolean} boredYet",
            " * @suppress {unusedPrivateMembers}",
            " */",
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

  @Test
  public void testBehaviorShorthandFunctionOverriding() {
    test(
        lines(
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
        lines(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
            "  doSomething(boredYet) { alert(boredYet + ' ' + this.isFun); },",
            "};",
            "",
            "/** @polymerBehavior @nocollapse */",
            "var RadBehavior = {",
            "  properties: {",
            "    howRad: Number",
            "  },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
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
            "  /** @suppress {checkTypes|globalThis|visibility} */",
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
            "/**",
            " * @param {boolean} boredYet",
            " * @suppress {unusedPrivateMembers}",
            " */",
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

  @Test
  public void testBehaviorReadOnlyProp() {
    String js =
        lines(
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
        lines(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: {",
            "      type: Boolean,",
            "      readOnly: true,",
            "    },",
            "  },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
            "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
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
            "/**",
            " * @param {string} funAmount",
            " * @suppress {unusedPrivateMembers}",
            " */",
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

    testExternChanges(
        1,
        EXTERNS,
        js,
        lines(
            EXTERNS,
            "/** @interface */",
            "var PolymerAInterface = function() {};",
            "/** @type {boolean} */",
            "PolymerAInterface.prototype.isFun;",
            "/** @type {!Array} */",
            "PolymerAInterface.prototype.pets;",
            "/** @type {string} */",
            "PolymerAInterface.prototype.name;",
            "/** @param {boolean} isFun **/",
            "PolymerAInterface.prototype._setIsFun;"));

    testExternChanges(
        2,
        EXTERNS,
        js,
        lines(
            EXTERNS,
            "/** @interface */",
            "var PolymerAInterface = function() {};",
            "/** @type {boolean} */",
            "PolymerAInterface.prototype.isFun;",
            "/** @param {boolean} isFun **/",
            "PolymerAInterface.prototype._setIsFun;"));
  }

  /**
   * Behaviors whose declarations are not in the global scope may contain references to symbols
   * which do not exist in the element's scope. Only copy a function stub.
   *
   */
  @Test
  public void testBehaviorInIIFE() {
    test(
        lines(
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
        lines(
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
            "    /** @suppress {checkTypes|globalThis|visibility} */",
            "    get someNumber() {",
            "      return 5*7+2;",
            "    },",
            "    /** @suppress {checkTypes|globalThis|visibility} */",
            "    doSomethingFun: function(funAmount) {",
            "      alert('Something ' + funAmount + ' fun!');",
            "    },",
            "    /** @suppress {checkTypes|globalThis|visibility} */",
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
            "/**",
            " * @param {string} funAmount",
            " * @suppress {unusedPrivateMembers}",
            " */",
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

  @Test
  public void testDuplicatedBehaviorsAreCopiedOnce() {
    test(
        lines(
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
        lines(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
            "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
            "  created: function() {}",
            "};",
            "/** @polymerBehavior @nocollapse */",
            "var RadBehavior = {",
            "  properties: {",
            "    howRad: Number",
            "  },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
            "  doSomethingRad: function(radAmount) { alert('Something ' + radAmount + ' rad!'); },",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
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
            "/**",
            " * @param {number} radAmount",
            " * @suppress {unusedPrivateMembers}",
            " */",
            "A.prototype.doSomethingRad = function(radAmount) {",
            "  alert('Something ' + radAmount + ' rad!');",
            "};",
            "/**",
            " * @param {string} funAmount",
            " * @suppress {unusedPrivateMembers}",
            " */",
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

  @Test
  public void testInvalid1() {
    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();
    testWarning("var x = Polymer('blah');", POLYMER_DESCRIPTOR_NOT_VALID);
    testWarning("var x = Polymer('foo-bar', {});", POLYMER_DESCRIPTOR_NOT_VALID);
    testError("var x = Polymer({},'blah');", POLYMER_UNEXPECTED_PARAMS);
    testError("var x = Polymer({});", POLYMER_MISSING_IS);

    testError(
        lines(
            "var x = class extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() { return '' }",
            "};"),
        POLYMER_CLASS_PROPERTIES_INVALID);

    testError(
        lines(
            "var x = class extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  get properties() { return {} }",
            "};"),
        POLYMER_CLASS_PROPERTIES_NOT_STATIC);
  }

  @Test
  public void testInvalidProperties() {
    testError(
        lines(
            "Polymer({",
            "  is: 'x-element',",
            "  properties: {",
            "    isHappy: true,",
            "  },",
            "});"),
        POLYMER_INVALID_PROPERTY);

    testError(
        lines(
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
        lines(
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

    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();
    testError(
        lines(
            "var x = class extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() {",
            "    return {",
            "      isHappy: true,",
            "    };",
            "  }",
            "};"),
        POLYMER_INVALID_PROPERTY);

    testError(
        lines(
            "var x = class extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() {",
            "    return {",
            "      isHappy: {",
            "        value: true,",
            "      }",
            "    };",
            "  }",
            "};"),
        POLYMER_INVALID_PROPERTY);

    testError(
        lines(
            "var x = class extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() {",
            "    return {",
            "      isHappy: {",
            "        type: foo.Bar,",
            "        value: true,",
            "      }",
            "    };",
            "  }",
            "};"),
        POLYMER_INVALID_PROPERTY);
  }

  @Test
  public void testInvalidBehavior() {
    testError(
        lines(
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
        lines(
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
        lines(
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
        lines(
            "Polymer({",
            "  is: 'x-element',",
            "  behaviors: [",
            "    DoesNotExist",
            "  ],",
            "});"),
        POLYMER_UNQUALIFIED_BEHAVIOR);
  }

  @Test
  public void testUnannotatedBehavior() {
    testError(
        lines(
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

  @Test
  public void testInvalidTypeAssignment() {
    test(
        lines(
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
        lines(
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
        warning(TYPE_MISMATCH_WARNING));
  }

  @Test
  public void testFeaturesInFunctionBody() {
    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();
    test(
        lines(
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
        lines(
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

  @Test
  public void testPolymerElementAnnotation1() {
    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();
    test(
        lines(
            "/** @constructor */",
            "var User = function() {};",
            "/** @polymer */",
            "class Foo extends Bar {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() {",
            "    return {",
            "      /** @type {!User} @private */",
            "      user_: Object,",
            "      pets: {",
            "        type: Array,",
            "        notify: true,",
            "      },",
            "      name: String,",
            "      thingToDo: Function",
            "    };",
            "  }",
            "}"),
        lines(
            "/** @constructor */",
            "var User = function() {};",
            "/** @polymer */",
            "class Foo extends Bar {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() {",
            "    return {",
            "      user_: Object,",
            "      pets: {",
            "        type: Array,",
            "        notify: true,",
            "      },",
            "      name: String,",
            "      thingToDo: Function",
            "    };",
            "  }",
            "}",
            "/** @type {!User} @private */",
            "Foo.prototype.user_;",
            "/** @type {!Array} */",
            "Foo.prototype.pets;",
            "/** @type {string} */",
            "Foo.prototype.name;",
            "/** @type {!Function} */",
            "Foo.prototype.thingToDo;"));
  }

  @Test
  public void testPolymerElementAnnotation2() {
    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();
    test(
        lines(
            "/** @constructor */",
            "var User = function() {};",
            "var a = {};",
            "/** @polymer */",
            "a.B = class extends Foo {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() {",
            "    return {",
            "      /** @type {!User} @private */",
            "      user_: Object,",
            "      pets: {",
            "      type: Array,",
            "        notify: true,",
            "      },",
            "      name: String,",
            "      thingToDo: Function",
            "    };",
            "  }",
            "};"),
        lines(
            "/** @constructor */",
            "var User = function() {};",
            "var a = {};",
            "/** @polymer */",
            "a.B = class extends Foo {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() {",
            "    return {",
            "      user_: Object,",
            "      pets: {",
            "        type: Array,",
            "        notify: true,",
            "      },",
            "      name: String,",
            "      thingToDo: Function,",
            "    };",
            "  }",
            "};",
            "/** @type {!User} @private */",
            "a.B.prototype.user_;",
            "/** @type {!Array} */",
            "a.B.prototype.pets;",
            "/** @type {string} */",
            "a.B.prototype.name;",
            "/** @type {!Function} */",
            "a.B.prototype.thingToDo;"));
  }

  @Test
  public void testPolymerElementAnnotation3() {
    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();
    test(
        lines(
            "/** @interface */",
            "function User() {};",
            "/** @type {boolean} */ User.prototype.id;",
            "var a = {};",
            "/**",
            " * @polymer",
            " * @implements {User}",
            " */",
            "a.B = class extends Foo {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() {",
            "    return {",
            "      id: Boolean,",
            "      other: {",
            "        type: String,",
            "        reflectToAttribute: true",
            "      }",
            "    };",
            "  }",
            "};"),
        lines(
            "/** @interface */",
            "function User() {};",
            "/** @type {boolean} */ User.prototype.id;",
            "var a = {};",
            "/**",
            " * @polymer",
            " * @implements {User}",
            " * @implements {Polymera_BInterface}",
            " */",
            "a.B = class extends Foo {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() {",
            "    return {",
            "      id: Boolean,",
            "      other: {",
            "        type: String,",
            "        reflectToAttribute: true",
            "      }",
            "    };",
            "  }",
            "};",
            "/** @type {boolean} */",
            "a.B.prototype.id;",
            "/** @type {string} */",
            "a.B.prototype.other;"));
  }

  @Test
  public void testObjectReflectionAddedToConfigProperties1() {
    propertyRenamingEnabled = true;
    test(
        2,
        lines(
            "/** @constructor */",
            "var User = function() {};",
            "/** @const */ var a = {};",
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
        lines(
            REFLECT_OBJECT_DEF,
            "/** @constructor */",
            "var User = function() {};",
            "/** @const */ var a = {};",
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
            "  properties: $jscomp.reflectObject(a.B, {",
            "    user_: Object,",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "    thingToDo: Function,",
            "  }),",
            "});"));

    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();
    test(
        2,
        lines(
            "/** @constructor */",
            "var User = function() {};",
            "var a = {};",
            "a.B = class extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() {",
            "    return {",
            "      /** @type {!User} @private */",
            "      user_: Object,",
            "      pets: {",
            "        type: Array,",
            "        notify: true,",
            "      },",
            "      name: String,",
            "      thingToDo: Function",
            "    };",
            "  }",
            "};"),
        lines(
            REFLECT_OBJECT_DEF,
            "/** @constructor */",
            "var User = function() {};",
            "var a = {};",
            "a.B = class extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() {",
            "    return  $jscomp.reflectObject(a.B, {",
            "      user_: Object,",
            "      pets: {",
            "        type: Array,",
            "        notify: true,",
            "      },",
            "      name: String,",
            "      thingToDo: Function",
            "    });",
            "  }",
            "};",
            "/** @type {!User} @private */",
            "a.B.prototype.user_;",
            "/** @type {!Array} */",
            "a.B.prototype.pets;",
            "/** @type {string} */",
            "a.B.prototype.name;",
            "/** @type {!Function} */",
            "a.B.prototype.thingToDo;"));
  }

  @Test
  public void testObjectReflectionAddedToConfigProperties2() {
    propertyRenamingEnabled = true;
    test(
        2,
        lines(
            "/** @constructor */",
            "var User = function() {};",
            "var A = Polymer({",
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
        lines(
            REFLECT_OBJECT_DEF,
            "/** @constructor */",
            "var User = function() {};",
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface}*/",
            "var A = function() {};",
            "/** @type {!User} @private */",
            "A.prototype.user_;",
            "/** @type {!Array} */",
            "A.prototype.pets;",
            "/** @type {string} */",
            "A.prototype.name;",
            "/** @type {!Function} */",
            "A.prototype.thingToDo;",
            "A = Polymer(/** @lends {A.prototype} */ {",
            "  is: 'x-element',",
            "  properties: $jscomp.reflectObject(A, {",
            "    user_: Object,",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "    thingToDo: Function,",
            "  }),",
            "});"));

    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();
    test(
        2,
        lines(
            "/** @constructor */",
            "var User = function() {};",
            "const A = class extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() {",
            "    return {",
            "      /** @type {!User} @private */",
            "      user_: Object,",
            "      pets: {",
            "        type: Array,",
            "        notify: true,",
            "      },",
            "      name: String,",
            "      thingToDo: Function,",
            "    };",
            "  }",
            "};"),
        lines(
            REFLECT_OBJECT_DEF,
            "/** @constructor */",
            "var User = function() {};",
            "const A = class extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() {",
            "    return $jscomp.reflectObject(A, {",
            "      user_: Object,",
            "      pets: {",
            "        type: Array,",
            "        notify: true,",
            "      },",
            "      name: String,",
            "      thingToDo: Function,",
            "    });",
            "  }",
            "};",
            "/** @type {!User} @private */",
            "A.prototype.user_;",
            "/** @type {!Array} */",
            "A.prototype.pets;",
            "/** @type {string} */",
            "A.prototype.name;",
            "/** @type {!Function} */",
            "A.prototype.thingToDo;"));
  }

  @Test
  public void testObjectReflectionAddedToConfigProperties3() {
    propertyRenamingEnabled = true;
    test(
        2,
        lines(
            "/** @constructor */",
            "var User = function() {};",
            "Polymer({",
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
        lines(
            REFLECT_OBJECT_DEF,
            "/** @constructor */",
            "var User = function() {};",
            "/**",
            " * @constructor @extends {PolymerElement}",
            " * @implements {PolymerXElementElementInterface}",
            " */",
            "var XElementElement = function() {};",
            "/** @type {!User} @private */",
            "XElementElement.prototype.user_;",
            "/** @type {!Array} */",
            "XElementElement.prototype.pets;",
            "/** @type {string} */",
            "XElementElement.prototype.name;",
            "/** @type {!Function} */",
            "XElementElement.prototype.thingToDo;",
            "Polymer(/** @lends {XElementElement.prototype} */ {",
            "  is: 'x-element',",
            "  properties: $jscomp.reflectObject(XElementElement, {",
            "    user_: Object,",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "    thingToDo: Function,",
            "  }),",
            "});"));

    // Type checker doesn't currently understand ES6 code. Remove when it does.
    disableTypeCheck();
    test(
        2,
        lines(
            "/** @constructor */",
            "var User = function() {};",
            "class XElement extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() {",
            "    return {",
            "      /** @type {!User} @private */",
            "      user_: Object,",
            "      pets: {",
            "        type: Array,",
            "        notify: true,",
            "      },",
            "      name: String,",
            "      thingToDo: Function,",
            "    };",
            "  }",
            "}"),
        lines(
            REFLECT_OBJECT_DEF,
            "/** @constructor */",
            "var User = function() {};",
            "class XElement extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() {",
            "    return $jscomp.reflectObject(XElement, {",
            "      user_: Object,",
            "      pets: {",
            "        type: Array,",
            "        notify: true,",
            "      },",
            "      name: String,",
            "      thingToDo: Function,",
            "    });",
            "  }",
            "}",
            "/** @type {!User} @private */",
            "XElement.prototype.user_;",
            "/** @type {!Array} */",
            "XElement.prototype.pets;",
            "/** @type {string} */",
            "XElement.prototype.name;",
            "/** @type {!Function} */",
            "XElement.prototype.thingToDo;"));
  }

  @Test
  public void testExportsMethodsFromClassBasedElement() {
    polymerExportPolicy = PolymerExportPolicy.EXPORT_ALL;
    test(
        2,
        lines(
            "class TestElement extends PolymerElement {",
            "  /** @public */ method1() {}",
            "  /** @private */ method2() {}",
            "}"),
        lines(
            "/** @implements {PolymerTestElementInterface} */",
            "class TestElement extends PolymerElement {",
            "  /** @public */ method1() {}",
            "  /** @private */ method2() {}",
            "}",
            "/** @private @export */ TestElement.prototype.method2;",
            "/** @public @export */ TestElement.prototype.method1;"));
  }

  @Test
  public void testExportMethodsFromLegacyElement() {
    polymerExportPolicy = PolymerExportPolicy.EXPORT_ALL;
    test(
        2,
        lines(
            "Polymer({",
            "  is: 'test-element',",
            "  /** @public */ method1() {},",
            "  /** @private */ method2() {},",
            "});"),
        lines(
            "/**",
            " * @constructor",
            " * @extends {PolymerElement}",
            " * @implements {PolymerTestElementElementInterface}",
            " */",
            "var TestElementElement = function() {};",
            "Polymer(/** @lends {TestElementElement.prototype} */ {",
            "  is: \"test-element\",",
            "  /** @public @this {TestElementElement} */ method1() {},",
            "  /** @private @this {TestElementElement} */ method2() {},",
            "});",
            "/** @private @export */ TestElementElement.prototype.method2;",
            "/** @public @export */ TestElementElement.prototype.method1;"));
  }

  @Test
  public void testExportsUniqueMethodsFromLegacyElementAndBehaviors() {
    polymerExportPolicy = PolymerExportPolicy.EXPORT_ALL;
    test(
        2,
        lines(
            "/** @polymerBehavior */",
            "const Behavior1 = {",
            "  /** @public */ onAll: function() {},",
            "  /**",
            "   * @public",
            // Note we include this @return annotation to test that we aren't including @return,
            // @param and other redundant JSDoc in our generated @export statements, since that
            // would cause a re-declaration error.
            "   * @return {void}",
            "   */",
            "   onBehavior1: function() {},",
            "};",
            "/** @polymerBehavior */",
            "const Behavior2 = {",
            "  /** @private */ onAll: function() {},",
            "  /** @private */ onBehavior2: function() {},",
            "};",
            "Polymer({",
            "  is: 'test-element',",
            "  behaviors: [Behavior1, Behavior2],",
            "  /** @private */ onAll: function() {},",
            "  /** @private */ onElement: function() {},",
            "});"),
        lines(
            "/** @nocollapse @polymerBehavior */",
            "const Behavior1 = {",
            "  /** @suppress {checkTypes,globalThis,visibility} */",
            "  onAll: function() {},",
            "  /** @suppress {checkTypes,globalThis,visibility} */",
            "  onBehavior1: function() {}",
            "};",
            "/** @nocollapse @polymerBehavior */",
            "const Behavior2 = {",
            "  /** @suppress {checkTypes,globalThis,visibility} */",
            "  onAll: function() {},",
            "  /** @suppress {checkTypes,globalThis,visibility} */",
            "  onBehavior2: function() {}",
            "};",
            "/**",
            " * @constructor",
            " * @extends {PolymerElement}",
            " * @implements {PolymerTestElementElementInterface}",
            " */",
            "var TestElementElement = function() {};",
            "/**",
            " * @public",
            " * @suppress {unusedPrivateMembers}",
            " * @return {void}",
            " */",
            "TestElementElement.prototype.onBehavior1 = function() {};",
            "/** @private @suppress {unusedPrivateMembers} */",
            "TestElementElement.prototype.onBehavior2 = function() {};",
            "Polymer(/** @lends {TestElementElement.prototype} */ {",
            "  is: \"test-element\",",
            "  behaviors: [Behavior1, Behavior2],",
            "  /** @private @this {TestElementElement} */",
            "  onAll: function() {},",
            "  /** @private @this {TestElementElement} */",
            "  onElement: function() {}",
            "});",
            "/** @private @export */ TestElementElement.prototype.onElement;",
            "/** @private @export */ TestElementElement.prototype.onBehavior2;",
            "/** @public @export */ TestElementElement.prototype.onBehavior1;",
            "/** @private @export */ TestElementElement.prototype.onAll;"));
  }

  @Override
  public void test(String js, String expected) {
    polymerVersion = 1;
    super.test(js, expected);

    polymerVersion = 2;
    super.test(js, expected);
  }

  public void test(int polymerVersion, String js, String expected) {
    this.polymerVersion = polymerVersion;
    super.test(js, expected);
  }

  @Override
  protected void testExternChanges(String extern, String input, String expectedExtern) {
    polymerVersion = 1;
    super.testExternChanges(extern, input, expectedExtern);

    polymerVersion = 2;
    super.testExternChanges(extern, input, expectedExtern);
  }

  protected void testExternChanges(
      int polymerVersion, String extern, String input, String expectedExtern) {
    this.polymerVersion = polymerVersion;
    super.testExternChanges(extern, input, expectedExtern);
  }

  @Override
  public void testError(String js, DiagnosticType error) {
    polymerVersion = 1;
    super.testError(js, error);

    polymerVersion = 2;
    super.testError(js, error);
  }

  @Override
  public void testWarning(String js, DiagnosticType error) {
    polymerVersion = 1;
    super.testWarning(js, error);

    polymerVersion = 2;
    super.testWarning(js, error);
  }
}
