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
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_INVALID_EXTENDS;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_INVALID_PROPERTY;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_MISSING_IS;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_UNANNOTATED_BEHAVIOR;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_UNEXPECTED_PARAMS;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_UNQUALIFIED_BEHAVIOR;
import static com.google.javascript.jscomp.TypeValidator.TYPE_MISMATCH_WARNING;
import static com.google.javascript.jscomp.modules.ModuleMapCreator.MISSING_NAMESPACE_IMPORT;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.NodeUtil.Visitor;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
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
          "var Element = function() {};",
          "/** @constructor @extends {Element} */",
          "var HTMLElement = function() {};",
          "/** @constructor @extends {HTMLElement} */",
          "var HTMLInputElement = function() {};",
          "/** @constructor @extends {HTMLElement} */",
          "var PolymerElement = function() {};");

  private static final String EXTERNS_SUFFIX =
      lines(
          "/**",
          " * @typedef {{",
          " *   type: !Function,",
          " *   value: (* | undefined),",
          " *   readOnly: (boolean | undefined),",
          " *   computed: (string | undefined),",
          " *   reflectToAttribute: (boolean | undefined),",
          " *   notify: (boolean | undefined),",
          " *   observer: (string | function(this:?, ?, ?) | undefined)",
          " * }}",
          " */",
          "let PolymerElementPropertiesMeta;",
          "",
          "/**",
          " * @typedef {Object<string, !Function|!PolymerElementPropertiesMeta>}",
          " */",
          "let PolymerElementProperties;",
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
          // In the actual code Polymer.Element comes from a mixin. This definition is simplified.
          "/**",
          " * @polymer",
          " */",
          "Polymer.Element = class {};",
          "",
          "/** @type {!Object<string, !Element>} */",
          "Polymer.Element.prototype.$;",
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
          "var PolymerXInputElementInterface0 = function() {};");

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
          "$jscomp.reflectObject = function (type, object) { return object; };",
          "/**",
          " * @param {string} propName",
          " * @param {?Object} type class, interface, or record",
          " * @return {string}",
          " */",
          "$jscomp.reflectProperty = function(propName, type) {",
          "  return propName;",
          "};");

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
    enableCreateModuleMap();
    polymerExportPolicy = PolymerExportPolicy.LEGACY;
  }

  @Test
  public void testPolymerRewriterGeneratesDeclarationOutsideLoadModule() {
    test(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.loadModule(function(exports) {",
                "  goog.module('ytu.app.ui.shared.YtuIcon');",
                "  YtuIcon = Polymer({is: 'ytu-icon' });",
                "  exports = YtuIcon;",
                "  return exports;",
                "})")),
        expected(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.loadModule(function(exports) {",
                "goog.module('ytu.app.ui.shared.YtuIcon');",
                "/**",
                " * @constructor",
                " * @extends {PolymerElement}",
                " * @implements {PolymerYtuIconInterface0}",
                " */",
                "  var YtuIcon = function(){}",
                "  YtuIcon = Polymer(/** @lends {YtuIcon.prototype} */ {is:\"ytu-icon\"});",
                "  exports = YtuIcon;",
                "  return exports;",
                "})")));
  }

  @Test
  public void testPolymerRewriterGeneratesDeclarationOutsideLoadModule2() {
    test(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.loadModule(function(exports) {",
                "  goog.module('ytu.app.ui.shared.YtuIcon');",
                "  Polymer({is: 'ytu-icon' });",
                "  return exports;",
                "})")),
        expected(
            lines(
                "/**",
                " * @constructor",
                " * @extends {PolymerElement}",
                " * @implements {PolymerYtuIconElementInterface0}",
                " */",
                "  var YtuIconElement = function(){}",
                new TestExternsBuilder().addClosureExterns().build()),
            lines(
                "goog.loadModule(function(exports) {",
                "goog.module('ytu.app.ui.shared.YtuIcon');",
                "  Polymer(/** @lends {YtuIconElement.prototype} */ {is:\"ytu-icon\"});",
                "  return exports;",
                "})")));
  }

  @Test
  public void testAssignToGoogModuleExports() {
    test(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.module('modOne');", //
                "exports = Polymer({",
                "  is: 'x-element',",
                "});")),
        expected(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.module('modOne');",
                "/** @constructor @extends {PolymerElement}",
                " * @implements {PolymerexportsForPolymer$jscomp0Interface1}",
                " */",
                "var exportsForPolymer$jscomp0 = function() {};",
                "exportsForPolymer$jscomp0 = Polymer(",
                "  /** @lends {exportsForPolymer$jscomp0.prototype} */ {",
                "  is: 'x-element',",
                "});",
                "exports = exportsForPolymer$jscomp0;")));
  }

  @Test
  public void testAssignToGoogLoadModuleExports() {
    test(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.loadModule(function(exports) {",
                "goog.module('modOne');", //
                "exports = Polymer({",
                "  is: 'x-element',",
                "});",
                "});")),
        expected(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.loadModule(function(exports) {",
                "goog.module('modOne');",
                "/** @constructor @extends {PolymerElement}",
                " * @implements {PolymerexportsForPolymer$jscomp0Interface1}",
                " */",
                "var exportsForPolymer$jscomp0 = function() {};",
                "exportsForPolymer$jscomp0 = Polymer(",
                "  /** @lends {exportsForPolymer$jscomp0.prototype} */ {",
                "  is: 'x-element',",
                "});",
                "exports = exportsForPolymer$jscomp0;",
                "});")));
  }

  @Test
  public void testSameNamedPolymerClassesInTwoModules() {
    test(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.module('modOne');", //
                "const Button = Polymer({",
                "  is: 'x-element',",
                "});"),
            lines(
                "goog.module('modTwo');", //
                "const Button = Polymer({",
                "  is: 'y-element',",
                "});")),
        expected(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.module('modOne');",
                "/** @constructor @extends {PolymerElement} @implements {PolymerButtonInterface0}"
                    + " */",
                "var Button = function() {};",
                "Button = Polymer(/** @lends {Button.prototype} */ {",
                "  is: 'x-element',",
                "});"),
            lines(
                "goog.module('modTwo');",
                "/** @constructor @extends {PolymerElement} @implements {PolymerButtonInterface1}"
                    + " */",
                "var Button = function() {};",
                "Button = Polymer(/** @lends {Button.prototype} */ {",
                "  is: 'y-element',",
                "});")));
  }

  @Test
  public void testPolymerRewriterGeneratesDeclaration_OutsideGoogModule() {
    test(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.module('mod');", //
                "var X = Polymer({",
                "  is: 'x-element',",
                "});")),
        expected(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.module('mod');",
                "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface0} */",
                "var X = function() {};",
                "X = Polymer(/** @lends {X.prototype} */ {",
                "  is: 'x-element',",
                "});")));
  }

  @Test
  public void testPolymerRewriterGeneratesDeclaration_OutsideGoogModule2() {
    test(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.module('mod');", //
                "Polymer({",
                "  is: 'x-element',",
                "});")),
        expected(
            lines(
                "/** @constructor @extends {PolymerElement} @implements"
                    + " {PolymerXElementElementInterface0} */",
                "var XElementElement = function() {};",
                new TestExternsBuilder().addClosureExterns().build()),
            lines(
                "goog.module('mod');",
                "Polymer(/** @lends {XElementElement.prototype} */ {",
                "  is: 'x-element',",
                "});")));
  }

  @Test
  public void testGeneratesCodeAfterGoogRequire_WithLegacyNamespace() {
    test(
        srcs(
            lines(
                new TestExternsBuilder().addClosureExterns().build(),
                "goog.provide('a.UnpluggedCancelOfferRenderer');",
                "goog.provide('a.b');",
                "/** @constructor */",
                "a.UnpluggedCancelOfferRenderer = function() {",
                "};"),
            lines(
                "goog.module('a.mod');", //
                "exports.Property = 'a property';"),
            lines(
                "goog.module('mod');",
                "goog.module.declareLegacyNamespace()",
                "const CancelOfferRenderer = goog.require('a.UnpluggedCancelOfferRenderer');",
                "const {Property} = goog.require('a.mod');",
                "Polymer(",
                " {is:'ytu-cancel-offer', properties:{",
                "/** @type {CancelOfferRenderer} */",
                "data:Object}});")),
        expected(
            lines(
                "/**",
                " * @constructor @extends {PolymerElement}",
                " * @implements {PolymerYtuCancelOfferElementInterface0} ",
                " */",
                "var YtuCancelOfferElement = function() {};",
                new TestExternsBuilder().addClosureExterns().build(),
                "goog.provide('a.UnpluggedCancelOfferRenderer');",
                "goog.provide('a.b');",
                "/** @constructor */ a.UnpluggedCancelOfferRenderer = function() {};"),
            lines(
                "goog.module('a.mod');", //
                "exports.Property = 'a property';"),
            lines(
                "goog.module('mod');",
                "goog.module.declareLegacyNamespace()",
                "const CancelOfferRenderer = goog.require('a.UnpluggedCancelOfferRenderer');",
                "const {Property} = goog.require('a.mod');",
                "/** @type {CancelOfferRenderer} */ YtuCancelOfferElement.prototype.data;",
                "Polymer(/** @lends {YtuCancelOfferElement.prototype} */",
                " {is:'ytu-cancel-offer', properties:{data:Object}});")));
  }

  @Test
  public void testGeneratesCodeAfterGoogRequire_WithSetTestOnly() {
    test(
        srcs(
            lines(
                new TestExternsBuilder().addClosureExterns().build(),
                "goog.provide('a.UnpluggedCancelOfferRenderer');",
                "/** @constructor */",
                "a.UnpluggedCancelOfferRenderer = function() {",
                "};"),
            lines(
                "goog.module('mod');",
                "goog.setTestOnly()",
                "const CancelOfferRenderer =" + " goog.require('a.UnpluggedCancelOfferRenderer');",
                "Polymer(",
                " {is:'ytu-cancel-offer', properties:{",
                "   /** @type {CancelOfferRenderer} */",
                "   data:Object}});")),
        expected(
            lines(
                "/** @constructor @extends {PolymerElement} @implements",
                " {PolymerYtuCancelOfferElementInterface0} */",
                "var YtuCancelOfferElement = function() {};",
                new TestExternsBuilder().addClosureExterns().build(),
                "goog.provide('a.UnpluggedCancelOfferRenderer');",
                "/** @constructor */ a.UnpluggedCancelOfferRenderer = function() {};"),
            lines(
                "goog.module('mod');",
                "goog.setTestOnly()",
                "const CancelOfferRenderer =" + " goog.require('a.UnpluggedCancelOfferRenderer');",
                "/** @type {CancelOfferRenderer} */",
                "YtuCancelOfferElement.prototype.data;",
                "Polymer(",
                "/** @lends {YtuCancelOfferElement.prototype} */",
                " {is:'ytu-cancel-offer', properties:{data:Object}});")));
  }

  @Test
  public void testPolymerRewriterGeneratesDeclaration_OutsideES6Module() {
    test(
        srcs(
            lines(""), // empty script for getNodeForCodeInsertion
            lines(
                "var X = Polymer({", //
                "  is: 'x-element',",
                "});",
                "export {X};")),
        expected(
            lines(""),
            lines(
                "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface0} */",
                "var X = function() {};",
                "X = Polymer(/** @lends {X.prototype} */ {",
                "  is: 'x-element',})",
                "export {X};")));
  }

  @Test
  public void testPolymerRewriterGeneratesDeclaration_OutsideES6Module2() {
    test(
        srcs(
            lines(""), // empty script for getNodeForCodeInsertion
            lines(
                "var PI = 3.14",
                "Polymer({", //
                "  is: 'x-element',",
                "});",
                "export {PI};")),
        expected(
            lines(
                "/** @constructor @extends {PolymerElement} @implements"
                    + " {PolymerXElementElementInterface0} */",
                "var XElementElement = function() {};"),
            lines(
                "var PI = 3.14",
                "Polymer(/** @lends {XElementElement.prototype} */ {",
                "  is: 'x-element',})",
                "export {PI};")));
  }

  @Test
  public void testPolymerRewriterGeneratesDeclaration_OutsideModule_IIFE() {
    test(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.module('mod');", //
                "(function() {",
                "  var X = Polymer({",
                "    is: 'x-element',",
                "  });",
                "})();")),
        expected(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.module('mod');",
                "(function() {",
                "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface0} */",
                "var X = function() {};",
                "    X = Polymer(/** @lends {X.prototype} */ {",
                "    is: 'x-element',",
                "  });",
                "})();")));
  }

  @Test
  public void testPolymerRewriterGeneratesDeclaration_OutsideModule_IIFE2() {
    test(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.module('mod');", //
                "(function() {",
                "  Polymer({",
                "    is: 'x-element',",
                "  });",
                "})();")),
        expected(
            lines(
                "/** @constructor @extends {PolymerElement} @implements"
                    + " {PolymerXElementElementInterface0} */",
                "var XElementElement = function() {};",
                new TestExternsBuilder().addClosureExterns().build()),
            lines(
                "goog.module('mod');",
                "(function() {",
                "    Polymer(/** @lends {XElementElement.prototype} */ {",
                "    is: 'x-element',",
                "  });",
                "})();")));
  }

  @Test
  public void testPolymerRewriterGeneratesDeclaration_OutsideModule_WithRequires() {
    ignoreWarnings(MISSING_NAMESPACE_IMPORT);
    test(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.module('mod');", //
                "const Component = goog.require('a');",
                "goog.forwardDeclare('something.else');",
                "const someLocal = (function() { return 0; })();",
                "var X = Polymer({",
                "  is: 'x-element',",
                "});")),
        expected(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.module('mod');",
                "const Component = goog.require('a');",
                "goog.forwardDeclare('something.else');",
                "const someLocal = (function() { return 0; })();",
                "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface0} */",
                "var X = function() {};",
                "X = Polymer(/** @lends {X.prototype} */ {",
                "  is: 'x-element',",
                "});")));
  }

  @Test
  public void testPolymerRewriterGeneratesDeclaration_OutsideModule_WithRequires2() {
    ignoreWarnings(MISSING_NAMESPACE_IMPORT);
    test(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.module('mod');", //
                "const Component = goog.require('a');",
                "goog.forwardDeclare('something.else');",
                "const someLocal = (function() { return 0; })();",
                "Polymer({",
                "  is: 'x-element',",
                "});")),
        expected(
            lines(
                "/** @constructor @extends {PolymerElement} @implements"
                    + " {PolymerXElementElementInterface0} */",
                "var XElementElement = function() {};",
                new TestExternsBuilder().addClosureExterns().build()),
            lines(
                "goog.module('mod');",
                "const Component = goog.require('a');",
                "goog.forwardDeclare('something.else');",
                "const someLocal = (function() { return 0; })();",
                "Polymer(/** @lends {XElementElement.prototype} */ {",
                "  is: 'x-element',",
                "});")));
  }

  @Test
  public void testVarTarget() {
    test(
        lines("var X = Polymer({", "  is: 'x-element',", "});"),
        lines(
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface0} */",
            "var X = function() {};",
            "X = Polymer(/** @lends {X.prototype} */ {",
            "  is: 'x-element',",
            "});"));

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
  public void testVarTargetMissingExterns() {
    allowSourcelessWarnings(); // the missing Polymer externs warning has no source, since it's not
    // about any particular file
    testError(
        /* externs= */ "",
        lines(
            "var X = Polymer({", //
            "  is: 'x-element',",
            "});"),
        PolymerPassErrors.POLYMER_MISSING_EXTERNS);
  }

  @Test
  public void testLetTarget() {
    test(
        lines("let X = Polymer({", "  is: 'x-element',", "});"),
        lines(
            "/**",
            " * @constructor",
            " * @implements {PolymerXInterface0}",
            " * @extends {PolymerElement}",
            " */",
            "var X = function() {};",
            "X = Polymer(/** @lends {X.prototype} */ {is:'x-element'});"));

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
    test(
        lines("const X = Polymer({", "  is: 'x-element',", "});"),
        lines(
            "/**\n",
            "* @constructor\n",
            "* @extends {PolymerElement}\n",
            "* @implements {PolymerXInterface0}\n",
            "*/\n",
            "var X=function(){};X=Polymer(/** @lends {X.prototype} */ {is:\"x-element\"})"));

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
        lines("Polymer({", "  is: 'x',", "});"),
        lines(
            "/**",
            " * @implements {PolymerXElementInterface0}",
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
        lines("/** @const */ var x = {};", "x.Z = Polymer({", "  is: 'x-element',", "});"),
        lines(
            "/** @const */ var x = {};",
            "/** @constructor @extends {PolymerElement} @implements {Polymerx_ZInterface0} */",
            "x.Z = function() {};",
            "x.Z = Polymer(/** @lends {x.Z.prototype} */ {",
            "  is: 'x-element',",
            "});"));

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

    test(
        lines("var x = {};", "x.Z = Polymer({", "  is: 'x-element',", "});"),
        lines(
            "var x = {};",
            "/** @constructor @extends {PolymerElement} @implements {Polymerx_ZInterface0} */",
            "x.Z = function() {};",
            "x.Z = Polymer(/** @lends {x.Z.prototype} */ {",
            "  is: 'x-element',",
            "});"));
  }

  @Test
  public void testComputedPropName() {
    // TypeCheck cannot grab a name from a complicated computedPropName
    test(
        "var X = Polymer({is:'x-element', [name + (() => 42)]: function() {return 42;}});",
        lines(
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface0} */",
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
            "  /** @constructor @extends {PolymerElement} @implements {Polymerx_ZInterface0}*/",
            "  x.Z = function() {};",
            "  x.Z = Polymer(/** @lends {x.Z.prototype} */ {",
            "    is: 'x-element',",
            "    /** @this {x.Z} */",
            "    sayHi: function() { alert('hi'); },",
            "  });",
            "})()"));

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
            " * @implements {PolymerXElementInterface0}",
            " */",
            "var XElement = function() {};",
            "(function() {",
            "  Polymer(/** @lends {XElement.prototype} */ {",
            "    is: 'x',",
            "    /** @this {XElement} */",
            "    sayHi: function() { alert('hi'); },",
            "  });",
            "})()"));

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

  @Test
  public void testIIFEExtractionVarTarget() {
    test(
        1,
        lines(
            "(function() {",
            "  Polymer({",
            "    is: 'foo-thing',",
            "    sayHi: function() { alert('hi'); },",
            "  });",
            "})()"),
        lines(
            "/**",
            " * @constructor @extends {PolymerElement}",
            " * @implements {PolymerFooThingElementInterface0}",
            " */",
            "var FooThingElement = function() {};",
            "(function() {",
            "  Polymer(/** @lends {FooThingElement.prototype} */ {",
            "    is: 'foo-thing',",
            "    /** @this {FooThingElement} */",
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
            " * @implements {PolymerXInterface0}",
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
            " * @implements {PolymerXInterface0}",
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface0}*/",
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface0}*/",
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
            " * @implements {PolymerXInputElementInterface0}",
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
    String newExterns =
        lines(
            INPUT_EXTERNS,
            "",
            "/** @interface */",
            "var PolymerYInputElementInterface1 = function() {};");

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
            "/** @constructor @extends {PolymerElement} @implements {Polymera_BInterface0}*/",
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
            "/** @constructor @extends {PolymerElement} @implements {Polymera_BInterface0}*/",
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
            " * @implements {PolymerES6TestInterface0}",
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
            "/** @constructor @extends {PolymerElement} @implements {Polymera_BInterface0} */",
            "a.B = function() {};",
            "/** @type {!Array<string>} */",
            "a.B.prototype.pets;",
            "/** @private {string} */",
            "a.B.prototype.name_;",
            "/*** @param {!Array<string>} pets @override */",
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
            "var Polymera_BInterface0 = function() {};",
            "/** @type {?} */",
            "Polymera_BInterface0.prototype.pets;",
            "/** @private {?} */",
            "Polymera_BInterface0.prototype.name_;",
            "/** @param {?} pets **/",
            "Polymera_BInterface0.prototype._setPets;"));

    test(
        2,
        js,
        lines(
            "/** @const */ var a = {};",
            "/** @constructor @extends {PolymerElement} @implements {Polymera_BInterface0} */",
            "a.B = function() {};",
            "/** @type {!Array<string>} */",
            "a.B.prototype.pets;",
            "/** @private {string} */",
            "a.B.prototype.name_;",
            "/** @param {!Array<string>} pets @override */",
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
            "var Polymera_BInterface0 = function() {};",
            "/** @type {?} */",
            "Polymera_BInterface0.prototype.pets;",
            "/** @param {?} pets **/",
            "Polymera_BInterface0.prototype._setPets;"));

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
            "var PolymerAInterface0 = function() {};",
            "/** @type {?} */",
            "PolymerAInterface0.prototype.pets;",
            "/** @param {?} pets **/",
            "PolymerAInterface0.prototype._setPets;"));

    test(
        2,
        jsClass,
        lines(
            "/** @implements {PolymerAInterface0} */",
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
            "/*** @param {!Array} pets @override */",
            "A.prototype._setPets = function(pets) {};"));
  }

  @Test
  public void testPolymerPropertyNamesGeneratedInExterns() {
    String js =
        lines(
            "(function() {",
            "  Polymer({",
            "    is: 'foo',",
            "    properties: {",
            "     /** @type {{randomProperty: string}} */",
            "     value: Object",
            "  }",
            "  });",
            "})();",
            "",
            "const obj = {randomProperty: 0, otherProperty: 1};");
    test(
        1,
        js,
        lines(
            "/**",
            "* @constructor",
            "* @extends {PolymerElement}",
            "* @implements {PolymerFooElementInterface0}",
            "*/",
            "var FooElement=function(){};",
            "(function(){",
            "   /** @type {{randomProperty:string}} */ ",
            "   FooElement.prototype.value;",
            "   Polymer(/** @lends {FooElement.prototype} */ {",
            "     is:\"foo\",",
            "     properties:{",
            "       value:Object}})})();",
            "const obj={randomProperty:0,otherProperty:1}"));

    polymerExportPolicy = PolymerExportPolicy.EXPORT_ALL;
    testExternChanges(
        INPUT_EXTERNS,
        js,
        lines(
            INPUT_EXTERNS,
            lines(
                "/** @interface */ var PolymerFooElementInterface0=function(){};",
                "/** @type {{randomProperty:?}} */ var PolymerDummyVar1;",
                "/** @type {?} */ PolymerFooElementInterface0.prototype.value")));
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
            "/** @constructor @extends {PolymerElement} @implements {Polymera_BInterface0} */",
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
            "var Polymera_BInterface0 = function() {};",
            "/** @type {?} */",
            "Polymera_BInterface0.prototype.pets;",
            "/** @type {?} */",
            "Polymera_BInterface0.prototype.name;"));

    test(
        2,
        js,
        lines(
            "/** @const */ var a = {};",
            "/** @constructor @extends {PolymerElement} @implements {Polymera_BInterface0} */",
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
            "var Polymera_BInterface0 = function() {};",
            "/** @type {?} */",
            "Polymera_BInterface0.prototype.name;"));
  }

  @Test
  public void testPolymerClassObserversTyped() {
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
            " * @implements {PolymerES6TestInterface0} ",
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
            "var ES6Test = Polymer({", //
            "  is: 'x-element',",
            "  sayHi: () => 42,",
            "});"),
        lines(
            "/** ",
            " * @constructor @extends {PolymerElement} ",
            " * @implements {PolymerES6TestInterface0} ",
            " */",
            "var ES6Test = function() {};",
            "",
            "ES6Test = Polymer(/** @lends {ES6Test.prototype} */ {",
            "  is: 'x-element',",
            "  /** @this {ES6Test} */",
            "  sayHi: () => 42,",
            "});"));
  }

  @Test
  public void testArrowFunctionDefinitionInIifeBehavior() {
    test(
        srcs(
            lines(
                "var es6 = {};",
                "(function() {",
                "  class Foo {}",
                "  /** @polymerBehavior */",
                "  es6.Behavior = {",
                "    sayHi: () => new Foo(),",
                "  };",
                "})();",
                "es6.Test = Polymer({", //
                "  is: 'x-element',",
                "  behaviors: [es6.Behavior]",
                "});")),
        expected(
            lines(
                "var es6 = {};",
                "",
                "(function() {",
                "  class Foo {}",
                "  /** @polymerBehavior @nocollapse */",
                "  es6.Behavior = {",
                "    /** @suppress {checkTypes,globalThis,visibility} */",
                "    sayHi: () => new Foo(),",
                "  };",
                "})();",
                "",
                "/** ",
                " * @constructor @extends {PolymerElement} ",
                " * @implements {Polymeres6_TestInterface0} ",
                " */",
                "es6.Test = function() {};",
                "/** @suppress {unusedPrivateMembers} */",
                "es6.Test.prototype.sayHi = () => void 0;",
                "",
                "es6.Test = Polymer(/** @lends {es6.Test.prototype} */ {",
                "  is: 'x-element',",
                "  behaviors: [es6.Behavior]",
                "});")));
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
            " * @implements {PolymerES6TestInterface0} ",
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
            " * @implements {PolymerESTestInterface0} ",
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface0} */",
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
  public void testDollarSignPropsConvertedToBrackets_polymer1Style() {
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface0} */",
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
  }

  @Test
  public void testDollarSignPropsConvertedToBrackets_polymer2Style() {
    ignoreWarnings(DiagnosticGroups.MISSING_PROPERTIES);
    test(
        lines(
            "class Foo extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() {",
            "    return {",
            "      /** @type {!HTMLElement} */",
            "      propName: {",
            "        type: Object,",
            "        value: function() {",
            "          return /** @type {!HTMLElement} */ (this.$['id']);",
            "        },",
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
            "        value: function() {",
            "          return /** @type {!HTMLElement} */ (this.$['id']);",
            "        },",
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
            " * @implements {PolymerES6TestInterface0} ",
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

  @Test
  public void testDollarSignMethodCallInShorthandFunctionNotConvertedToBrackets() {
    test(
        lines(
            "class ReceiverHelper {",
            "  constructor() {}",
            "  foo() {}",
            "};",
            "class Receiver {",
            "  constructor() {",
            "    this.$ = new ReceiverHelper();",
            "  }",
            "  toggle(el) {",
            "    el.toggle();",
            "  }",
            "};",
            "var ES6Test = Polymer({",
            "  is: 'x-element',",
            "  sayHi() {",
            "    const receiver = new Receiver();",
            "    receiver.$.foo();",
            "    toggle(this.$.checkbox);",
            "    receiver.toggle(this.$.checkbox);",
            "  },",
            "  toggle(el) {",
            "    el.toggle();",
            "  },",
            "});"),
        lines(
            "class ReceiverHelper {",
            "  constructor() {}",
            "  foo() {}",
            "};",
            "class Receiver {",
            "  constructor() {",
            "    this.$ = new ReceiverHelper();",
            "  }",
            "  toggle(el) {",
            "    el.toggle();",
            "  }",
            "};",
            "/** ",
            " * @constructor @extends {PolymerElement} ",
            " * @implements {PolymerES6TestInterface0} ",
            " */",
            "var ES6Test = function() {};",
            "",
            "ES6Test = Polymer(/** @lends {ES6Test.prototype} */ {",
            "  is: 'x-element',",
            "  /** @this {ES6Test} */",
            "  sayHi() {",
            "    const receiver = new Receiver();",
            "    receiver.$.foo();",
            "    toggle(this.$['checkbox']);",
            "    receiver.toggle(this.$['checkbox']);",
            "  },",
            "  /** @this {ES6Test} */",
            "  toggle(el) {",
            "    el.toggle();",
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface0}*/",
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerBInterface1}*/",
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

  /**
   * Tests that no reference to the 'Data' module's local variable 'Item' gets generated in the
   * 'Client' module by the PolymerClassRewriter.
   */
  @Test
  public void testBehaviorInModule_NoBehaviorProps() {
    test(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.module('Data');\n",
                "class Item {\n",
                "}\n",
                "exports.Item = Item;\n",
                "/**\n",
                " * A Polymer behavior providing common data access and formatting methods.\n",
                " * @polymerBehavior\n",
                " */\n",
                "exports.SummaryDataBehavior = {\n",
                "  /**\n",
                "   * @param {?Item} item\n",
                "   * @export\n",
                "   */\n",
                "  getValue(item) {\n",
                "    return this.getItemValue_(item);\n",
                "  },\n",
                "};"),
            lines(
                "goog.module('Client');",
                "const Data = goog.require('Data');",
                "var A = Polymer({",
                "  is: 'x-element',",
                "  behaviors: [ Data.SummaryDataBehavior ],",
                "});")));
  }

  @Test
  public void testBehaviorInModule_WithBehaviorProps() {
    test(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.module('Data');",
                "class Item {}",
                "exports.Item = Item;",
                "/** @polymerBehavior */",
                "exports.SummaryDataBehavior = {",
                "  properties: {",
                "    /** @type {!Item} */",
                "    ABC: {type: Object}",
                "  }",
                "};"),
            lines(
                "goog.module(\"client\");",
                "const Data = goog.require('Data');",
                "",
                "var A = Polymer({",
                "  is: 'x',",
                "  behaviors: [Data.SummaryDataBehavior],",
                "});")));
  }

  @Test
  public void testSimpleBehavior() {
    test(
        srcs(
            lines(
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
                "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + '"
                    + " fun!'); },",
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
        expected(
            lines(
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
                "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + '"
                    + " fun!'); },",
                "  /** @suppress {checkTypes|globalThis|visibility} */",
                "  created: function() {}",
                "};",
                "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface0}*/",
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
                " * @implements {PolymerMyElementInterface0}",
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
                " * @implements {PolymerAInterface0}",
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface0}*/",
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface0}*/",
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface0}*/",
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface0}*/",
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface0}*/",
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

  @Test
  public void testGlobalBehaviorPropsPrototypesGetCreated() {
    test(
        lines(
            "/** @polymerBehavior */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "}",
            "var A = Polymer({",
            "  is: 'x-element',",
            "  behaviors: [ FunBehavior]",
            "});"),
        lines(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "}",
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface0}*/",
            "var A = function() {};",
            "/** @type {boolean} */",
            "A.prototype.isFun;",
            "A = Polymer(/** @lends {A.prototype} */ {",
            "  is: 'x-element',",
            "  behaviors: [ FunBehavior ],",
            "});"));
  }

  @Test
  public void testGlobalBehaviorPropsPrototypesGetCreated_transitively() {
    test(
        lines(
            "/** @polymerBehavior */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "}",
            "/** @polymerBehavior */",
            "var SuperCoolBehaviors = [FunBehavior];",
            "var A = Polymer({",
            "  is: 'x-element',",
            "  behaviors: [ SuperCoolBehaviors]",
            "});"),
        lines(
            "/** @polymerBehavior @nocollapse */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "}",
            "/** @polymerBehavior @nocollapse */",
            "var SuperCoolBehaviors = [FunBehavior];",
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface0}*/",
            "var A = function() {};",
            "/** @type {boolean} */",
            "A.prototype.isFun;",
            "A = Polymer(/** @lends {A.prototype} */ {",
            "  is: 'x-element',",
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface0}*/",
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface0}*/",
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
            "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + ' fun!');",
            "},",
            "  /** @suppress {checkTypes|globalThis|visibility} */",
            "  created: function() {}",
            "};",
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface0}*/",
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
            "/** @param {boolean} isFun @override */",
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
            "var PolymerAInterface0 = function() {};",
            "/** @type {?} */",
            "PolymerAInterface0.prototype.isFun;",
            "/** @type {?} */",
            "PolymerAInterface0.prototype.pets;",
            "/** @type {?} */",
            "PolymerAInterface0.prototype.name;",
            "/** @param {?} isFun **/",
            "PolymerAInterface0.prototype._setIsFun;"));

    testExternChanges(
        2,
        EXTERNS,
        js,
        lines(
            EXTERNS,
            "/** @interface */",
            "var PolymerAInterface0 = function() {};",
            "/** @type {?} */",
            "PolymerAInterface0.prototype.isFun;",
            "/** @param {?} isFun **/",
            "PolymerAInterface0.prototype._setIsFun;"));
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface0}*/",
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

  /**
   * Behaviors whose declarations are not in the global scope may contain references to symbols
   * which do not exist in the element's scope. Only copy a function stub.
   *
   */
  @Test
  public void testBehaviorInIIFE_NoTypeAnnotation() {
    test(
        lines(
            "(function() {",
            "  /** @polymerBehavior */",
            "  Polymer.FunBehavior = {",
            "    properties: {", // no annotation /** @type {number} */
            "      isFun: {",
            "        type: Boolean,",
            "        value: true,",
            "      }",
            "    },",
            "  };",
            "})();",
            "var A = Polymer({",
            "  is: 'x-element',",
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
            "  };",
            "})();",
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface0}*/",
            "var A = function() {};",
            "/** @type {boolean} */",
            "A.prototype.isFun;",
            "A = Polymer(/** @lends {A.prototype} */ {",
            "  is: 'x-element',",
            "  behaviors: [ Polymer.FunBehavior ],",
            "});"));
  }

  /**
   * See {@link #testBehaviorInIIFE()} for more information on what this is testing.
   *
   */
  @Test
  public void testBehaviorInModule() {
    test(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.module('behaviors.CoolBehavior');",
                "goog.module.declareLegacyNamespace();",
                "const MODULE_LOCAL = 0;",
                "/** @polymerBehavior */",
                "Polymer.FunBehavior = {",
                "  /** @param {string} funAmount */",
                "  doSomethingFun: function(funAmount) {",
                "    alert(MODULE_LOCAL);",
                "    alert('Something ' + funAmount + ' fun!');",
                "  },",
                "  /** @override */",
                "  created: function() {}",
                "};",
                "/** @polymerBehavior */",
                "Polymer.VeryFunBehavior = [",
                "  Polymer.FunBehavior,",
                "  {",
                "    doSomethingVeryFun: function(a, [b], c, veryFun = MODULE_LOCAL) {",
                "      alert(MODULE_LOCAL);",
                "      alert('Something very ' + veryFunAmount + ' fun!');",
                "    },",
                "    /** @override */",
                "    created: function() {}",
                "  }",
                "]",
                "/** @polymerBehavior */",
                "exports = {",
                "  /** @param {string} coolAmount */",
                "  doSomethingCool: function(coolAmount) {",
                "    alert(MODULE_LOCAL);",
                "    alert('Something ' + funAmount + ' cool!');",
                "  },",
                "  /** @override */",
                "  created: function() {}",
                "};"),
            lines(
                "goog.require('behaviors.CoolBehavior');",
                "var A = Polymer({",
                "  is: 'x-element',",
                "  properties: {",
                "    name: String,",
                "  },",
                "  behaviors: [ Polymer.VeryFunBehavior, behaviors.CoolBehavior ],",
                "});")),
        expected(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.module('behaviors.CoolBehavior');",
                "goog.module.declareLegacyNamespace();",
                "const MODULE_LOCAL = 0;",
                "/** @polymerBehavior @nocollapse */",
                "Polymer.FunBehavior = {",
                "  /** @suppress {checkTypes|globalThis|visibility} */",
                "  doSomethingFun: function(funAmount) {",
                "    alert(MODULE_LOCAL);",
                "    alert('Something ' + funAmount + ' fun!');",
                "  },",
                "  /** @suppress {checkTypes|globalThis|visibility} */",
                "  created: function() {}",
                "};",
                "/** @polymerBehavior @nocollapse */",
                "Polymer.VeryFunBehavior = [",
                "  Polymer.FunBehavior,",
                "  {",
                "    /** @suppress {checkTypes|globalThis|visibility} */",
                "    doSomethingVeryFun: function(a, [b], c, veryFun = MODULE_LOCAL) {",
                "      alert(MODULE_LOCAL);",
                "      alert('Something very ' + veryFunAmount + ' fun!');",
                "    },",
                "    /** @suppress {checkTypes|globalThis|visibility} */",
                "    created: function() {}",
                "  }",
                "]",
                "/** @polymerBehavior @nocollapse */",
                "exports = {",
                "  /** @suppress {checkTypes|globalThis|visibility} */",
                "  doSomethingCool: function(coolAmount) {",
                "    alert(MODULE_LOCAL);",
                "    alert('Something ' + funAmount + ' cool!');",
                "  },",
                "  /** @suppress {checkTypes|globalThis|visibility} */",
                "  created: function() {}",
                "};"),
            lines(
                "goog.require('behaviors.CoolBehavior');",
                "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface0}*/",
                "var A = function() {};",
                "/** @type {string} */ A.prototype.name;",
                "/**",
                " * @param {string} funAmount",
                " * @suppress {unusedPrivateMembers}",
                " */",
                "A.prototype.doSomethingFun = function(funAmount) {};",
                "/**",
                " * @suppress {unusedPrivateMembers}",
                " */",
                "A.prototype.doSomethingVeryFun =",
                "    function(a, param$polymer$1, c, veryFun = void 0) {};",
                "/**",
                " * @param {string} coolAmount",
                " * @suppress {unusedPrivateMembers}",
                " */",
                "A.prototype.doSomethingCool = function(coolAmount) {};",
                "A = Polymer(/** @lends {A.prototype} */ {",
                "  is: 'x-element',",
                "  properties: {",
                "    name: String,",
                "  },",
                "  behaviors: [ Polymer.VeryFunBehavior, behaviors.CoolBehavior ],",
                "});")));
  }

  /**
   * See {@link #testBehaviorInIIFE()} for more information on what this is testing.
   *
   */
  @Test
  public void testBehaviorInModule_destructuringRestParams() {
    test(
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.module('behaviors.CoolBehavior');",
                "goog.module.declareLegacyNamespace();",
                "const MODULE_LOCAL = 0;",
                "/** @polymerBehavior */",
                "exports = {",
                "  doSomethingCool: function([coolAmount = MODULE_LOCAL] = [], ...[more]) {",
                "    alert(MODULE_LOCAL);",
                "    alert('Something ' + funAmount + ' cool!');",
                "  },",
                "  /** @override */",
                "  created: function() {}",
                "};"),
            lines(
                "goog.require('behaviors.CoolBehavior');",
                "var A = Polymer({",
                "  is: 'x-element',",
                "  properties: {",
                "    name: String,",
                "  },",
                "  behaviors: [ behaviors.CoolBehavior ],",
                "});")),
        expected(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "goog.module('behaviors.CoolBehavior');",
                "goog.module.declareLegacyNamespace();",
                "const MODULE_LOCAL = 0;",
                "/** @polymerBehavior @nocollapse */",
                "exports = {",
                "  /** @suppress {checkTypes|globalThis|visibility} */",
                "  doSomethingCool: function([coolAmount = MODULE_LOCAL] = [], ...[more]) {",
                "    alert(MODULE_LOCAL);",
                "    alert('Something ' + funAmount + ' cool!');",
                "  },",
                "  /** @suppress {checkTypes|globalThis|visibility} */",
                "  created: function() {}",
                "};"),
            lines(
                "goog.require('behaviors.CoolBehavior');",
                "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface0}*/",
                "var A = function() {};",
                "/** @type {string} */ A.prototype.name;",
                "/** @suppress {unusedPrivateMembers} */",
                "A.prototype.doSomethingCool =",
                "    function(param$polymer$0 = void 0, ...param$polymer$1) {};",
                "A = Polymer(/** @lends {A.prototype} */ {",
                "  is: 'x-element',",
                "  properties: {",
                "    name: String,",
                "  },",
                "  behaviors: [ behaviors.CoolBehavior ],",
                "});")));
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface0}*/",
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
    testWarning("var x = Polymer('blah');", POLYMER_DESCRIPTOR_NOT_VALID);
    test(
        srcs("var x = Polymer('foo-bar', {});"),
        warning(POLYMER_DESCRIPTOR_NOT_VALID),
        warning(TypeCheck.WRONG_ARGUMENT_COUNT));
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface0} */",
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface0} */",
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
    test(
        lines(
            "class Bar {}",
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
            "class Bar {}",
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
    test(
        lines(
            "class Foo {}",
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
            "class Foo {}",
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
    test(
        lines(
            "class Foo {}",
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
            "class Foo {}",
            "/** @interface */",
            "function User() {};",
            "/** @type {boolean} */ User.prototype.id;",
            "var a = {};",
            "/**",
            " * @polymer",
            " * @implements {User}",
            " * @implements {Polymera_BInterface0}",
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
            "/** @constructor @extends {PolymerElement} @implements {Polymera_BInterface0}*/",
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface0}*/",
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
            " * @implements {PolymerXElementElementInterface0}",
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
            "/** @implements {PolymerTestElementInterface0} */",
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
            " * @implements {PolymerTestElementElementInterface0}",
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
            " * @implements {PolymerTestElementElementInterface0}",
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

  @Test
  public void testSimpleObserverStringsConvertedToReferences1() {
    propertyRenamingEnabled = true;

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
            "        observer: '_petsChanged'",
            "      },",
            "      name: {",
            "        type: String,",
            "        observer: '_nameChanged'",
            "      },",
            "      thingToDo: Function,",
            "    };",
            "  }",
            "  _petsChanged(newValue, oldValue) {}",
            "  _nameChanged(newValue, oldValue) {}",
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
            "        observer: XElement.prototype._petsChanged",
            "      },",
            "      name: {",
            "        type: String,",
            "        observer: XElement.prototype._nameChanged",
            "      },",
            "      thingToDo: Function,",
            "    });",
            "  }",
            "  _petsChanged(newValue, oldValue) {}",
            "  _nameChanged(newValue, oldValue) {}",
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
  public void testSimpleObserverStringsConvertedToReferences2() {
    propertyRenamingEnabled = true;

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
            "        observer: '_petsChanged'",
            "      },",
            "      name: {",
            "        type: String,",
            "        observer: '_nameChanged'",
            "      },",
            "      thingToDo: Function",
            "    };",
            "  }",
            "  _petsChanged(newValue, oldValue) {}",
            "  _nameChanged(newValue, oldValue) {}",
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
            "        observer: a.B.prototype._petsChanged",
            "      },",
            "      name: {",
            "        type: String,",
            "        observer: a.B.prototype._nameChanged",
            "      },",
            "      thingToDo: Function",
            "    });",
            "  }",
            "  _petsChanged(newValue, oldValue) {}",
            "  _nameChanged(newValue, oldValue) {}",
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
  public void testSimpleObserverStringsConvertedToReferences3() {
    propertyRenamingEnabled = true;

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
            "        observer: '_petsChanged'",
            "      },",
            "      name: {",
            "        type: String,",
            "        observer: '_nameChanged'",
            "      },",
            "      thingToDo: Function,",
            "    };",
            "  }",
            "  _petsChanged(newValue, oldValue) {}",
            "  _nameChanged(newValue, oldValue) {}",
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
            "        observer: A.prototype._petsChanged",
            "      },",
            "      name: {",
            "        type: String,",
            "        observer: A.prototype._nameChanged",
            "      },",
            "      thingToDo: Function,",
            "    });",
            "  }",
            "  _petsChanged(newValue, oldValue) {}",
            "  _nameChanged(newValue, oldValue) {}",
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
  public void testReflectionForComputedPropertyStrings1() {
    propertyRenamingEnabled = true;

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
            "        computed: '_computePets()'",
            "      },",
            "      name: {",
            "        type: String,",
            "        computed: '_computeName(user_, thingToDo)'",
            "      },",
            "      thingToDo: Function,",
            "    };",
            "  }",
            "  _computePets() {}",
            "  _computeName(user, thingToDo) {}",
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
            "        computed: $jscomp.reflectProperty(",
            "            '_computePets', /** @type {!XElement} */ ({})) + '()'",
            "      },",
            "      name: {",
            "        type: String,",
            "        computed: $jscomp.reflectProperty(",
            "            '_computeName', /** @type {!XElement} */ ({})) + '(' +",
            "            $jscomp.reflectProperty('user_', /** @type {!XElement} */ ({})) + ',' + ",
            "            $jscomp.reflectProperty('thingToDo', /** @type {!XElement} */ ({})) + ",
            "            ')'",
            "      },",
            "      thingToDo: Function,",
            "    });",
            "  }",
            "  _computePets() {}",
            "  _computeName(user, thingToDo) {}",
            "}",
            "/** @type {!User} @private */",
            "XElement.prototype.user_;",
            "/** @type {!Array} */",
            "XElement.prototype.pets;",
            "/** @type {string} */",
            "XElement.prototype.name;",
            "/** @type {!Function} */",
            "XElement.prototype.thingToDo;",
            "JSCOMPILER_PRESERVE(XElement.prototype._computePets);",
            "JSCOMPILER_PRESERVE(XElement.prototype._computeName);",
            "JSCOMPILER_PRESERVE(XElement.prototype.user_);",
            "JSCOMPILER_PRESERVE(XElement.prototype.thingToDo);"));
  }

  @Test
  public void testReflectionForComputedPropertyStrings2() {
    propertyRenamingEnabled = true;

    test(
        2,
        lines(
            "/** @constructor */",
            "var User = function() {};",
            "/** @type {string} */ User.prototype.id;",
            "class XElement extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() {",
            "    return {",
            "      /** @type {!User} @private */",
            "      user_: Object,",
            "      pets: Array,",
            "      name: {",
            "        type: String,",
            "        computed: '_computeName(\"user\", 12.0, user_.id, user_.*)'",
            "      },",
            "      thingToDo: Function,",
            "    };",
            "  }",
            "  _computePets() {}",
            "  _computeName(user, thingToDo) {}",
            "}"),
        lines(
            REFLECT_OBJECT_DEF,
            "/** @constructor */",
            "var User = function() {};",
            "/** @type {string} */ User.prototype.id;",
            "class XElement extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() {",
            "    return $jscomp.reflectObject(XElement, {",
            "      user_: Object,",
            "      pets: Array,",
            "      name: {",
            "        type: String,",
            "        computed: $jscomp.reflectProperty(",
            "            '_computeName', /** @type {!XElement} */ ({})) + '(' +",
            "            '\"user\"' + ',' + '12.0' + ',' + ",
            "            $jscomp.reflectProperty('user_', /** @type {!XElement} */ ({})) + '.' + ",
            "            $jscomp.reflectProperty(",
            "                'id', /** @type {!XElement} */ ({}).user_)",
            "                + ',' + ",
            "            $jscomp.reflectProperty('user_', /** @type {!XElement} */ ({})) + ",
            "            '.*' + ')'",
            "      },",
            "      thingToDo: Function,",
            "    });",
            "  }",
            "  _computePets() {}",
            "  _computeName(user, thingToDo) {}",
            "}",
            "/** @type {!User} @private */",
            "XElement.prototype.user_;",
            "/** @type {!Array} */",
            "XElement.prototype.pets;",
            "/** @type {string} */",
            "XElement.prototype.name;",
            "/** @type {!Function} */",
            "XElement.prototype.thingToDo;",
            "JSCOMPILER_PRESERVE(XElement.prototype._computeName);",
            "JSCOMPILER_PRESERVE(XElement.prototype.user_);",
            "JSCOMPILER_PRESERVE(XElement.prototype.user_);"));
  }

  @Test
  public void testParseErrorForComputedPropertyStrings1() {
    propertyRenamingEnabled = true;

    polymerVersion = 2;
    super.testError(
        lines(
            "/** @constructor */",
            "var User = function() {};",
            "/** @type {string} */ User.prototype.id;",
            "class XElement extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() {",
            "    return {",
            "      /** @type {!User} @private */",
            "      user_: Object,",
            "      pets: Array,",
            "      name: {",
            "        type: String,",
            "        computed: '_computeName(\"user\", 12.0, user_.id'",
            "      },",
            "      thingToDo: Function,",
            "    };",
            "  }",
            "  _computePets() {}",
            "  _computeName(user, thingToDo) {}",
            "}"),
        PolymerPassErrors.POLYMER_UNPARSABLE_STRING);
  }

  @Test
  public void testParseErrorForComputedPropertyStrings2() {
    propertyRenamingEnabled = true;
    
    polymerVersion = 2;
    super.testError(
        lines(
            "/** @constructor */",
            "var User = function() {};",
            "/** @type {string} */ User.prototype.id;",
            "class XElement extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() {",
            "    return {",
            "      /** @type {!User} @private */",
            "      user_: Object,",
            "      pets: Array,",
            "      name: {",
            "        type: String,",
            "        computed: '_computeName(\"user)'",
            "      },",
            "      thingToDo: Function,",
            "    };",
            "  }",
            "  _computePets() {}",
            "  _computeName(user, thingToDo) {}",
            "}"),
        PolymerPassErrors.POLYMER_UNPARSABLE_STRING);
  }

  @Test
  public void testReflectionForComplexObservers() {
    propertyRenamingEnabled = true;
    
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
            "      pets: Array,",
            "      name: String,",
            "      thingToDo: Function,",
            "    };",
            "  }",
            "  static get observers() {",
            "    return [",
            "      '_userChanged(user_)',",
            "      '_userOrThingToDoChanged(user_, thingToDo)'",
            "    ];",
            "  }",
            "  _userChanged(user_) {}",
            "  _userOrThingToDoChanged(user, thingToDo) {}",
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
            "      pets: Array,",
            "      name: String,",
            "      thingToDo: Function,",
            "    });",
            "  }",
            "  /** @return {!Array<string>} */",
            "  static get observers() {",
            "    return [",
            "      $jscomp.reflectProperty('_userChanged', /** @type {!XElement} */ ({})) + '(' +",
            "          $jscomp.reflectProperty('user_', /** @type {!XElement} */ ({})) + ')',",
            "      $jscomp.reflectProperty(",
            "          '_userOrThingToDoChanged', /** @type {!XElement} */ ({})) + '(' +",
            "          $jscomp.reflectProperty('user_', /** @type {!XElement} */ ({})) + ',' + ",
            "          $jscomp.reflectProperty('thingToDo', /** @type {!XElement} */ ({})) + ",
            "          ')'",
            "    ];",
            "  }",
            "  _userChanged(user_) {}",
            "  _userOrThingToDoChanged(user, thingToDo) {}",
            "}",
            "/** @type {!User} @private */",
            "XElement.prototype.user_;",
            "/** @type {!Array} */",
            "XElement.prototype.pets;",
            "/** @type {string} */",
            "XElement.prototype.name;",
            "/** @type {!Function} */",
            "XElement.prototype.thingToDo;",
            "JSCOMPILER_PRESERVE(XElement.prototype._userChanged);",
            "JSCOMPILER_PRESERVE(XElement.prototype.user_);",
            "JSCOMPILER_PRESERVE(XElement.prototype._userOrThingToDoChanged);",
            "JSCOMPILER_PRESERVE(XElement.prototype.user_);",
            "JSCOMPILER_PRESERVE(XElement.prototype.thingToDo);"));
  }

  @Test
  public void testPolymerInEsModuleExport() {
    test(
        lines(
            "export let PaperMenuButton = Polymer({",
            "  is: 'paper-menu-button',",
            "  properties: {",
            "    opened: {type: Boolean, value: false}",
            "  }",
            "});"),
        lines(
            "/** @constructor @extends {PolymerElement}",
            " * @implements {PolymerPaperMenuButtonInterface0}",
            " */",
            "var PaperMenuButton = function() {};",
            "/** @type {boolean} */",
            "PaperMenuButton.prototype.opened;",
            "PaperMenuButton = Polymer(/** @lends {PaperMenuButton.prototype} */ {",
            "  is: 'paper-menu-button',",
            "  properties: {",
            "    opened: {type: Boolean, value: false}",
            "  }",
            "});",
            "export {PaperMenuButton};"));
  }

  @Test
  public void testCanAccessPropertyOnPolymerElement_fromEsModuleTypeSummary() {
    test(
        externs(
            EXTERNS,
            lines(
                "/** @typeSummary */",
                "Polymer({",
                "  is: 'foo',",
                "  properties: {",
                "    opened: {type: Boolean, value: false}",
                "  },",
                "  toggle: function() {}",
                "})",
                "export {}")),
        srcs(
            lines(
                "function fn(/** !FooElement */ elem) {", //
                "  elem.toggle();",
                "  return elem.opened;",
                "}")));
  }

  @Test
  public void testCanAccessPropertyOnPolymerElement_fromBundledGoogModuleTypeSummary() {
    test(
        externs(
            EXTERNS,
            lines(
                "/** @typeSummary */",
                "goog.loadModule(function(exports) {",
                "goog.module('a.b.c');",
                "Polymer({",
                "  is: 'foo',",
                "  properties: {",
                "    opened: {type: Boolean, value: false}",
                "  },",
                "  toggle: function() {}",
                "})",
                "return exports;",
                "});")),
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "function fn(/** !FooElement */ elem) {", //
                "  elem.toggle();",
                "  return elem.opened;",
                "}")));
  }

  @Test
  public void testCanAccessPropertyOnPolymerElement_fromBundledGoogModuleTypeSummaryBehavior() {
    test(
        externs(
            EXTERNS,
            lines(
                "/** @typeSummary */",
                "goog.loadModule(function(exports) {",
                "goog.module('SomeBehavior');",
                "/** @polymerBehavior */",
                "exports = {",
                "  properties: {",
                "    opened: {type: Boolean, value: false}",
                "  },",
                "  toggle: function() {}",
                "};",
                "return exports;",
                "});"),
            lines(
                "/** @typeSummary */",
                "goog.loadModule(function(exports) {",
                "goog.module('a.b.c');",
                "const SomeBehavior = goog.require('SomeBehavior');",
                "Polymer({",
                "  is: 'foo',",
                "  behaviors: [SomeBehavior],",
                "  properties: {",
                "    opened: {type: Boolean, value: false}",
                "  },",
                "  toggle: function() {}",
                "})",
                "return exports;",
                "});")),
        srcs(
            new TestExternsBuilder().addClosureExterns().build(),
            lines(
                "function fn(/** !FooElement */ elem) {", //
                "  elem.toggle();",
                "  return elem.opened;",
                "}")));
  }

  @Test
  public void testPolymerImplicitGlobalNamingConflict() {
    testError(
        lines(
            "var FooElement;", //
            "Polymer({is: 'foo'})"),
        PolymerClassRewriter.IMPLICIT_GLOBAL_CONFLICT);

    test(
        srcs(
            "var FooElement;",
            lines(
                "goog.module('m');", //
                "Polymer({is: 'foo'})")),
        error(PolymerClassRewriter.IMPLICIT_GLOBAL_CONFLICT));

    test(
        srcs(
            // First file in compilation needs to be a script, not a module, as the compiler injects
            // global declarations into the first file.
            "",
            lines(
                "goog.module('m');", //
                "var FooElement;",
                "Polymer({is: 'foo'})")),
        error(PolymerClassRewriter.IMPLICIT_GLOBAL_CONFLICT));

    testNoWarning("var FooElement = Polymer({is: 'foo'});");
  }

  @Test
  public void testShadowPolymerElement() {
    testError(
        lines(
            "goog.module('m');", //
            "const Foo = Polymer({is: 'foo'});",
            "var PolymerElement;"),
        PolymerClassRewriter.POLYMER_ELEMENT_CONFLICT);
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
  public void testError(String externs, String js, DiagnosticType error) {
    polymerVersion = 1;
    super.testError(externs, js, error);

    polymerVersion = 2;
    super.testError(externs, js, error);
  }

  @Override
  public void testWarning(String js, DiagnosticType error) {
    polymerVersion = 1;
    super.testWarning(js, error);

    polymerVersion = 2;
    super.testWarning(js, error);
  }
}

