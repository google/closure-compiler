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
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.NodeUtil.Visitor;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for PolymerPass */
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
          EXTERNS_SUFFIX);

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
          "$jscomp.reflectObject = function (type, object) {};");

  private static final String EXTERNS = lines(EXTERNS_PREFIX, EXTERNS_SUFFIX, REFLECT_OBJECT_DEF);

  public PolymerPassTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new PolymerPass(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setPreventLibraryInjection(true);
    return options;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    allowExternsChanges();
    enableRunTypeCheckAfterProcessing();
    enableParseTypeInfo();
    enableCreateModuleMap();
  }

  @Test
  public void testPolymerRewriterGeneratesDeclarationOutsideLoadModule() {
    test(
        srcs(
            TestExternsBuilder.getClosureExternsAsSource(),
            lines(
                "goog.loadModule(function(exports) {",
                "  goog.module('ytu.app.ui.shared.YtuIcon');",
                "  YtuIcon = Polymer({is: 'ytu-icon' });",
                "  exports = YtuIcon;",
                "  return exports;",
                "})")),
        expected(
            TestExternsBuilder.getClosureExternsAsSource(),
            lines(
                "goog.loadModule(function(exports) {",
                "goog.module('ytu.app.ui.shared.YtuIcon');",
                "/**",
                " * @constructor",
                " * @extends {PolymerElement}",
                " * @implements {PolymerYtuIconInterface$UID$0}",
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
            TestExternsBuilder.getClosureExternsAsSource(),
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
                " * @implements {PolymerYtuIconElementInterface$m1176578414$0}",
                " */",
                "  var YtuIconElement = function(){}",
                TestExternsBuilder.getClosureExternsAsSource()),
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
            TestExternsBuilder.getClosureExternsAsSource(),
            lines(
                "goog.module('modOne');", //
                "exports = Polymer({",
                "  is: 'x-element',",
                "});")),
        expected(
            TestExternsBuilder.getClosureExternsAsSource(),
            lines(
                "goog.module('modOne');",
                "/** @constructor @extends {PolymerElement}",
                " * @implements {PolymerexportsForPolymer$jscomp0Interface$UID$0}",
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
            TestExternsBuilder.getClosureExternsAsSource(),
            lines(
                "goog.loadModule(function(exports) {",
                "goog.module('modOne');", //
                "exports = Polymer({",
                "  is: 'x-element',",
                "});",
                "});")),
        expected(
            TestExternsBuilder.getClosureExternsAsSource(),
            lines(
                "goog.loadModule(function(exports) {",
                "goog.module('modOne');",
                "/** @constructor @extends {PolymerElement}",
                " * @implements {PolymerexportsForPolymer$jscomp0Interface$UID$0}",
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
            TestExternsBuilder.getClosureExternsAsSource(),
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
            TestExternsBuilder.getClosureExternsAsSource(),
            lines(
                "goog.module('modOne');",
                "/** @constructor @extends {PolymerElement} @implements"
                    + " {PolymerButtonInterface$UID$0} */",
                "var Button = function() {};",
                "Button = Polymer(/** @lends {Button.prototype} */ {",
                "  is: 'x-element',",
                "});"),
            lines(
                "goog.module('modTwo');",
                "/** @constructor @extends {PolymerElement} @implements"
                    + " {PolymerButtonInterface$UID$0} */",
                "var Button = function() {};",
                "Button = Polymer(/** @lends {Button.prototype} */ {",
                "  is: 'y-element',",
                "});")));
  }

  @Test
  public void testPolymerRewriterGeneratesDeclaration_OutsideGoogModule() {
    test(
        srcs(
            TestExternsBuilder.getClosureExternsAsSource(),
            lines(
                "goog.module('mod');", //
                "var X = Polymer({",
                "  is: 'x-element',",
                "});")),
        expected(
            TestExternsBuilder.getClosureExternsAsSource(),
            lines(
                "goog.module('mod');",
                "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface$UID$0}"
                    + " */",
                "var X = function() {};",
                "X = Polymer(/** @lends {X.prototype} */ {",
                "  is: 'x-element',",
                "});")));
  }

  @Test
  public void testPolymerRewriterGeneratesDeclaration_OutsideGoogModule2() {
    test(
        srcs(
            TestExternsBuilder.getClosureExternsAsSource(),
            lines(
                "goog.module('mod');", //
                "Polymer({",
                "  is: 'x-element',",
                "});")),
        expected(
            lines(
                "/** @constructor @extends {PolymerElement} @implements"
                    + " {PolymerXElementElementInterface$m1176578414$0} */",
                "var XElementElement = function() {};",
                TestExternsBuilder.getClosureExternsAsSource()),
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
                TestExternsBuilder.getClosureExternsAsSource(),
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
                " * @implements {PolymerYtuCancelOfferElementInterface$m1176578413$0} ",
                " */",
                "var YtuCancelOfferElement = function() {};",
                TestExternsBuilder.getClosureExternsAsSource(),
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
                "Polymer(/** @lends {YtuCancelOfferElement.prototype} */ {",
                "  is:'ytu-cancel-offer',",
                "  properties: $jscomp.reflectObject(YtuCancelOfferElement, {data:Object})",
                "});")));
  }

  @Test
  public void testGeneratesCodeAfterGoogRequire_WithSetTestOnly() {
    test(
        srcs(
            lines(
                TestExternsBuilder.getClosureExternsAsSource(),
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
                " {PolymerYtuCancelOfferElementInterface$m1176578414$0} */",
                "var YtuCancelOfferElement = function() {};",
                TestExternsBuilder.getClosureExternsAsSource(),
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
                " {",
                "    is:'ytu-cancel-offer',",
                "    properties: $jscomp.reflectObject(YtuCancelOfferElement, {data:Object})",
                "});")));
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
                "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface$UID$0}"
                    + " */",
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
                    + " {PolymerXElementElementInterface$m1176578414$0} */",
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
            TestExternsBuilder.getClosureExternsAsSource(),
            lines(
                "goog.module('mod');", //
                "(function() {",
                "  var X = Polymer({",
                "    is: 'x-element',",
                "  });",
                "})();")),
        expected(
            TestExternsBuilder.getClosureExternsAsSource(),
            lines(
                "goog.module('mod');",
                "(function() {",
                "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface$UID$0}"
                    + " */",
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
            TestExternsBuilder.getClosureExternsAsSource(),
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
                    + " {PolymerXElementElementInterface$m1176578414$0} */",
                "var XElementElement = function() {};",
                TestExternsBuilder.getClosureExternsAsSource()),
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
    test(
        srcs(
            TestExternsBuilder.getClosureExternsAsSource(),
            lines(
                "goog.module('mod');", //
                "const Component = goog.require('a');",
                "goog.forwardDeclare('something.else');",
                "const someLocal = (function() { return 0; })();",
                "var X = Polymer({",
                "  is: 'x-element',",
                "});")),
        expected(
            TestExternsBuilder.getClosureExternsAsSource(),
            lines(
                "goog.module('mod');",
                "const Component = goog.require('a');",
                "goog.forwardDeclare('something.else');",
                "const someLocal = (function() { return 0; })();",
                "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface$UID$0}"
                    + " */",
                "var X = function() {};",
                "X = Polymer(/** @lends {X.prototype} */ {",
                "  is: 'x-element',",
                "});")));
  }

  @Test
  public void testPolymerRewriterGeneratesDeclaration_OutsideModule_WithRequires2() {
    test(
        srcs(
            TestExternsBuilder.getClosureExternsAsSource(),
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
                    + " {PolymerXElementElementInterface$m1176578414$0} */",
                "var XElementElement = function() {};",
                TestExternsBuilder.getClosureExternsAsSource()),
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface$UID$0} */",
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
            "/** @implements {PolymerXInterface$UID$0} */",
            "var X = class extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() { return $jscomp.reflectObject(X, {}); }",
            "};"));
  }

  @Test
  public void testVarTargetMissingExterns() {
    allowSourcelessWarnings(); // the missing Polymer externs warning has no source, since it's not
    // about any particular file
    testError(
        externs(""),
        srcs(
            lines(
                "var X = Polymer({", //
                "  is: 'x-element',",
                "});")),
        error(PolymerPassErrors.POLYMER_MISSING_EXTERNS));
  }

  @Test
  public void testLetTarget() {
    test(
        lines("let X = Polymer({", "  is: 'x-element',", "});"),
        lines(
            "/**",
            " * @constructor",
            " * @implements {PolymerXInterface$UID$0}",
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
            "/** @implements {PolymerXInterface$UID$0} */",
            "let X = class extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() { return $jscomp.reflectObject(X, {}); }",
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
            "* @implements {PolymerXInterface$UID$0}\n",
            "*/\n",
            "var X=function(){};X=Polymer(/** @lends {X.prototype} */ {is:\"x-element\"})"));

    test(
        lines(
            "const X = class extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  static get properties() { return {}; }",
            "};"),
        lines(
            "/** @implements {PolymerXInterface$UID$0} */",
            "const X = class extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() { return $jscomp.reflectObject(X, {}); }",
            "};"));
  }

  @Test
  public void testDefaultTypeNameTarget() {
    test(
        lines("Polymer({", "  is: 'x',", "});"),
        lines(
            "/**",
            " * @implements {PolymerXElementInterface$UID$0}",
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
            "/** @constructor @extends {PolymerElement} @implements {Polymerx_ZInterface$UID$0} */",
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
            "/** @implements {Polymerx_ZInterface$UID$0} */",
            "x.Z = class extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() { return $jscomp.reflectObject(x.Z, {}); }",
            "};"));

    test(
        lines("var x = {};", "x.Z = Polymer({", "  is: 'x-element',", "});"),
        lines(
            "var x = {};",
            "/** @constructor @extends {PolymerElement} @implements {Polymerx_ZInterface$UID$0} */",
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface$UID$0} */",
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
            "  /** @constructor @extends {PolymerElement} @implements"
                + " {Polymerx_ZInterface$UID$0}*/",
            "  x.Z = function() {};",
            "  x.Z = Polymer(/** @lends {x.Z.prototype} */ {",
            "    is: 'x-element',",
            "    /** @this {x.Z} */",
            "    sayHi: function() { alert('hi'); },",
            "  });",
            "  /** @export */",
            "  x.Z.prototype.sayHi;",
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
            "  /** @implements {Polymerx_ZInterface$UID$0} */",
            "  x.Z = class extends Polymer.Element {",
            "    /** @return {string} */",
            "    static get is() { return 'x-element'; }",
            "    /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "    static get properties() { return $jscomp.reflectObject(x.Z, {}); }",
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
            " * @implements {PolymerXElementInterface$UID$0}",
            " */",
            "var XElement = function() {};",
            "(function() {",
            "  Polymer(/** @lends {XElement.prototype} */ {",
            "    is: 'x',",
            "    /** @this {XElement} */",
            "    sayHi: function() { alert('hi'); },",
            "  });",
            "  /** @export */",
            "  XElement.prototype.sayHi;",
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
            "  /** @implements {PolymerXInterface$UID$0} */",
            "  const X = class extends Polymer.Element {",
            "    /** @return {string} */",
            "    static get is() { return 'x-element'; }",
            "    /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "    static get properties() { return $jscomp.reflectObject(X, {}); }",
            "  };",
            "})();"));
  }

  @Test
  public void testIIFEExtractionVarTarget() {
    test(
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
            " * @implements {PolymerFooThingElementInterface$UID$0}",
            " */",
            "var FooThingElement = function() {};",
            "(function() {",
            "  Polymer(/** @lends {FooThingElement.prototype} */ {",
            "    is: 'foo-thing',",
            "    /** @this {FooThingElement} */",
            "    sayHi: function() { alert('hi'); },",
            "  });",
            "  /** @export */",
            "  FooThingElement.prototype.sayHi;",
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
            " * @implements {PolymerXInterface$UID$0}",
            " */",
            "var X = function(name) { alert('hi, ' + name); };",
            "X = Polymer(/** @lends {X.prototype} */ {",
            "  is: 'x-element',",
            "  factoryImpl: function(name) { alert('hi, ' + name); },",
            "});",
            "/** @export */",
            "X.prototype.factoryImpl;"));
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
            " * @implements {PolymerXInterface$UID$0}",
            " */",
            "var X = function(name) { alert('hi, ' + name); };",
            "",
            "X = Polymer(/** @lends {X.prototype} */ {",
            "  is: 'x-element',",
            "  factoryImpl(name) { alert('hi, ' + name); },",
            "});",
            "/** @export */",
            "X.prototype.factoryImpl;"));
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface$UID$0}*/",
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
            "});",
            "/** @export */",
            "X.prototype.handleClick;"));

    test(
        lines(
            "class X extends Polymer.Element {",
            "  static get is() { return 'x-element'; }",
            "  handleClick(e) {",
            "    alert('Thank you for clicking');",
            "  }",
            "}"),
        lines(
            "/** @implements {PolymerXInterface$UID$0} */",
            "class X extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  handleClick(e) {",
            "    alert('Thank you for clicking');",
            "  }",
            "}",
            "/** @export */",
            "X.prototype.handleClick;"));
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface$UID$0}*/",
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
            "});",
            "/** @export */",
            "X.prototype.handleClick;"));
  }

  @Test
  public void testNativeElementExtension() {
    String js = lines("Polymer({", "  is: 'x-input',", "  extends: 'input',", "});");

    test(
        js,
        lines(
            "/**",
            " * @constructor @extends {PolymerInputElement}",
            " * @implements {PolymerXInputElementInterface$UID$0}",
            " */",
            "var XInputElement = function() {};",
            "Polymer(/** @lends {XInputElement.prototype} */ {",
            "  is: 'x-input',",
            "  extends: 'input',",
            "});"));

    testExternChanges(
        externs(EXTERNS),
        srcs(js),
        expected(
            lines(
                "/** @interface */",
                "var PolymerXInputElementInterface$m1146332801$0 = function() {};"),
            lines(INPUT_EXTERNS, REFLECT_OBJECT_DEF)));
  }

  @Test
  public void testExtendNonExistentElement() {
    String js = lines("Polymer({", "  is: 'x-input',", "  extends: 'nonexist',", "});");

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
    Expected newExterns =
        expected(
            lines(
                "",
                "/** @interface */",
                "var PolymerXInputElementInterface$m1146332801$0 = function() {};",
                "/** @interface */",
                "var PolymerYInputElementInterface$m1146332801$1 = function() {};"),
            lines(INPUT_EXTERNS, REFLECT_OBJECT_DEF));

    testExternChanges(externs(EXTERNS), srcs(js), newExterns);
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
            "/** @constructor @extends {PolymerElement} @implements {Polymera_BInterface$UID$0}*/",
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
            "/** @implements {Polymera_BInterface$UID$0} */",
            "a.B = class extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() {",
            "    return $jscomp.reflectObject(a.B, {",
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
            "/** @constructor @extends {PolymerElement} @implements {Polymera_BInterface$UID$0}*/",
            "a.B = function() {};",
            "/** @type {!User} @private */",
            "a.B.prototype.user_;",
            "/** @type {!Array} */",
            "a.B.prototype.pets;",
            "/** @type {string} */",
            "a.B.prototype.name;",
            "a.B = Polymer(/** @lends {a.B.prototype} */ {",
            "  is: 'x-element',",
            "  properties: $jscomp.reflectObject(a.B, {",
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
            "  }),",
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
            "/** @implements {Polymera_BInterface$UID$0} */",
            "a.B = class extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() {",
            "    return $jscomp.reflectObject(a.B, {",
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
            "    });",
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
            " * @implements {PolymerES6TestInterface$UID$0}",
            " */",
            "var ES6Test = function() {};",
            "/** @type {!Object} */",
            "ES6Test.prototype.user;",
            "",
            "ES6Test = Polymer(/** @lends {ES6Test.prototype} */ {",
            "  is: 'x-element',",
            "  properties: $jscomp.reflectObject(ES6Test, {",
            "    user: {",
            "      type: Object,",
            "      /** @this {ES6Test} @return {!Object} */",
            "      value() { return new User(); },",
            "    },",
            "  }),",
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
            "/** @implements {Polymera_BInterface$UID$0} */",
            "a.B = class extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() {",
            "    return $jscomp.reflectObject(a.B, {",
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
            "    });",
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
        js,
        lines(
            "/** @const */ var a = {};",
            "/** @constructor @extends {PolymerElement} @implements {Polymera_BInterface$UID$0} */",
            "a.B = function() {};",
            "/** @type {!Array<string>} */",
            "a.B.prototype.pets;",
            "/** @private {string} */",
            "a.B.prototype.name_;",
            "/*** @param {!Array<string>} pets @override */",
            "a.B.prototype._setPets = function(pets) {};",
            "a.B = Polymer(/** @lends {a.B.prototype} */ {",
            "  is: 'x-element',",
            "  properties: $jscomp.reflectObject(a.B, {",
            "    pets: {",
            "      type: Array,",
            "      readOnly: true,",
            "    },",
            "    name_: String,",
            "  }),",
            "});"));

    testExternChanges(
        externs(EXTERNS),
        srcs(js),
        expected(
            lines(
                "/** @interface */",
                "var Polymera_BInterface$m1146332801$0 = function() {};",
                "/** @type {?} */",
                "Polymera_BInterface$m1146332801$0.prototype.pets;",
                "/** @private {?} */",
                "Polymera_BInterface$m1146332801$0.prototype.name_;",
                "/** @param {?} pets **/",
                "Polymera_BInterface$m1146332801$0.prototype._setPets;"),
            EXTERNS));
    
    String jsClass =
        lines(
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
        externs(EXTERNS),
        srcs(jsClass),
        expected(
            lines(
                "/** @interface */",
                "var PolymerAInterface$m1146332801$0 = function() {};",
                "/** @type {?} */",
                "PolymerAInterface$m1146332801$0.prototype.pets;",
                "/** @param {?} pets **/",
                "PolymerAInterface$m1146332801$0.prototype._setPets;"),
            EXTERNS));

    test(
        jsClass,
        lines(
            "/** @implements {PolymerAInterface$UID$0} */",
            "class A extends Polymer.Element {",
            "  /** @return {string} */ static get is() { return 'a-element'; }",
            "  /** @return {PolymerElementProperties} */ static get properties() {",
            "    return $jscomp.reflectObject(A, {",
            "      pets: {",
            "        type: Array,",
            "        readOnly: true",
            "      }",
            "    });",
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
        js,
        lines(
            "/**",
            "* @constructor",
            "* @extends {PolymerElement}",
            "* @implements {PolymerFooElementInterface$UID$0}",
            "*/",
            "var FooElement=function(){};",
            "(function(){",
            "   /** @type {{randomProperty:string}} */ ",
            "   FooElement.prototype.value;",
            "   Polymer(/** @lends {FooElement.prototype} */ {",
            "     is:\"foo\",",
            "     properties: $jscomp.reflectObject(FooElement, {value:Object})",
            "   })})();",
            "const obj={randomProperty:0,otherProperty:1}"));

    testExternChanges(
        externs(INPUT_EXTERNS),
        srcs(js),
        expected(
            lines(
                "/** @interface */ var PolymerFooElementInterface$m1146332801$0=function(){};",
                "/** @type {{randomProperty:?}} */ var PolymerDummyVar0;",
                "/** @type {?} */ PolymerFooElementInterface$m1146332801$0.prototype.value"),
            INPUT_EXTERNS));
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
        js,
        lines(
            "/** @const */ var a = {};",
            "/** @constructor @extends {PolymerElement} @implements"
                + " {Polymera_BInterface$m1146332801$0} */",
            "a.B = function() {};",
            "/** @type {!Array<string>} */",
            "a.B.prototype.pets;",
            "/** @type {string} */",
            "a.B.prototype.name;",
            "a.B = Polymer(/** @lends {a.B.prototype} */ {",
            "  is: 'x-element',",
            "  properties: $jscomp.reflectObject(a.B, {",
            "    pets: {",
            "      type: Array,",
            "      readOnly: false,",
            "    },",
            "    name: {",
            "      type: String,",
            "      reflectToAttribute: true",
            "    }",
            "  }),",
            "});"));

    testExternChanges(
        externs(EXTERNS),
        srcs(js),
        expected(
            lines(
                "/** @interface */",
                "var Polymera_BInterface$m1146332801$0 = function() {};",
                "/** @type {?} */",
                "Polymera_BInterface$m1146332801$0.prototype.pets;",
                "/** @type {?} */",
                "Polymera_BInterface$m1146332801$0.prototype.name;"),
            EXTERNS));
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
            "/** @implements {PolymerFooElementInterface$UID$0} */",
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
            " * @implements {PolymerES6TestInterface$UID$0} ",
            " */",
            "var ES6Test = function() {};",
            "",
            "ES6Test = Polymer(/** @lends {ES6Test.prototype} */ {",
            "  is: 'x-element',",
            "  /** @this {ES6Test} */",
            "  sayHi() {",
            "    alert('hi');",
            "  },",
            "});",
            "/** @export */",
            "ES6Test.prototype.sayHi;"));
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
            " * @implements {PolymerES6TestInterface$UID$0} ",
            " */",
            "var ES6Test = function() {};",
            "",
            "ES6Test = Polymer(/** @lends {ES6Test.prototype} */ {",
            "  is: 'x-element',",
            "  /** @this {ES6Test} */",
            "  sayHi: () => 42,",
            "});",
            "/** @export */",
            "ES6Test.prototype.sayHi;"));
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
                " * @implements {Polymeres6_TestInterface$UID$0} ",
                " */",
                "es6.Test = function() {};",
                "/**",
                " * @suppress {unusedPrivateMembers}",
                " * @return {?}",
                " */",
                "es6.Test.prototype.sayHi = () => void 0;",
                "",
                "es6.Test = Polymer(/** @lends {es6.Test.prototype} */ {",
                "  is: 'x-element',",
                "  behaviors: [es6.Behavior]",
                "});",
                "/** @export */",
                "es6.Test.prototype.sayHi;")));
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
            " * @implements {PolymerES6TestInterface$UID$0} ",
            " */",
            "var ES6Test = function() {};",
            "",
            "ES6Test = Polymer(/** @lends {ES6Test.prototype} */ {",
            "  is: 'x-element',",
            "  /** @override @this {ES6Test} */",
            "  created() {",
            "    alert('Shorthand created');",
            "  },",
            "});",
            "/** @export */",
            "ES6Test.prototype.created;"));
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
            " * @implements {PolymerESTestInterface$UID$0} ",
            " */",
            "var ESTest = function() {};",
            "",
            "ESTest = Polymer(/** @lends {ESTest.prototype} */ {",
            "  is: 'x-element',",
            "  /** @this {ESTest} */",
            "  sayHi() {",
            "    return [1, 2];",
            "  },",
            "});",
            "/** @export */",
            "ESTest.prototype.sayHi;"));
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface$UID$0} */",
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
            "});",
            "/** @export @private */",
            "X.prototype.sayHelloTo_;",
            "/** @export */",
            "X.prototype.created;",
            "/** @export */",
            "X.prototype.sayHi;"));

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
            "/** @implements {PolymerFooInterface$UID$0} */",
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
            "}",
            "/** @export @private */",
            "Foo.prototype.sayHelloTo_;",
            "/** @export */",
            "Foo.prototype.connectedCallback;",
            "/** @export */",
            "Foo.prototype.sayHi;"));
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface$UID$0} */",
            "var X = function() {};",
            "/** @type {!HTMLElement} */",
            "X.prototype.propName;",
            "X = Polymer(/** @lends {X.prototype} */ {",
            "  is: 'x-element',",
            "  properties: $jscomp.reflectObject(X, {",
            "    propName: {",
            "      type: Object,",
            "      /** @this {X} @return {!HTMLElement} */",
            "      value: function() { return this.$['id']; },",
            "    },",
            "  }),",
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
            "});",
            "/** @export @private */",
            "X.prototype.sayHelloTo_;",
            "/** @export */",
            "X.prototype.created;",
            "/** @export */",
            "X.prototype.sayHi;"));
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
            "/** @implements {PolymerFooInterface$UID$0} */",
            "class Foo extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() {",
            "    return $jscomp.reflectObject(Foo, {",
            "      propName: {",
            "        type: Object,",
            "        /** @this {Foo} @return {!HTMLElement} */",
            "        value: function() {",
            "          return /** @type {!HTMLElement} */ (this.$['id']);",
            "        },",
            "      }",
            "    });",
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
            "Foo.prototype.propName;",
            "/** @export @private */",
            "Foo.prototype.sayHelloTo_;",
            "/** @export */",
            "Foo.prototype.connectedCallback;",
            "/** @export */",
            "Foo.prototype.sayHi;"));
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
            " * @implements {PolymerES6TestInterface$UID$0} ",
            " */",
            "var ES6Test = function() {};",
            "",
            "ES6Test = Polymer(/** @lends {ES6Test.prototype} */ {",
            "  is: 'x-element',",
            "  /** @this {ES6Test} */",
            "  sayHi() {",
            "    this.$['checkbox'].toggle();",
            "  },",
            "});",
            "/** @export */",
            "ES6Test.prototype.sayHi;"));
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
            " * @implements {PolymerES6TestInterface$UID$0} ",
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
            "});",
            "/** @export */",
            "ES6Test.prototype.toggle;",
            "/** @export */",
            "ES6Test.prototype.sayHi;"));
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface$UID$0}*/",
            "var A = function() {};",
            "/** @type {!FunObject} */",
            "A.prototype.funThing;",
            "/** @type {!Array} */",
            "A.prototype.pets;",
            "/** @type {string} */",
            "A.prototype.name;",
            "A = Polymer(/** @lends {A.prototype} */ {",
            "  is: 'x-element',",
            "  properties: $jscomp.reflectObject(A, {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  }),",
            "  behaviors: [ FunBehavior ],",
            "});",
            "/** @constructor @extends {PolymerElement} @implements"
                + " {PolymerBInterface$m1146332801$1*/",
            "var B = function() {};",
            "/** @type {!FunObject} */",
            "B.prototype.funThing;",
            "/** @type {string} */",
            "B.prototype.foo;",
            "B = Polymer(/** @lends {B.prototype} */ {",
            "  is: 'y-element',",
            "  properties: $jscomp.reflectObject(B, {",
            "    foo: String,",
            "  }),",
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
            TestExternsBuilder.getClosureExternsAsSource(),
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
            TestExternsBuilder.getClosureExternsAsSource(),
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
                "/** @constructor @extends {PolymerElement} @implements"
                    + " {PolymerAInterface$UID$0}*/",
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
                "  properties: $jscomp.reflectObject(A, {",
                "    pets: {",
                "      type: Array,",
                "      notify: true,",
                "    },",
                "    name: String,",
                "  }),",
                "  behaviors: [ FunBehavior ],",
                "});",
                "/** @export */",
                "A.prototype.doSomethingFun;")),
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
                " * @implements {PolymerMyElementInterface$UID$0}",
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
    test(
        srcs(
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
                "});")),
        expected(
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
                " * @implements {PolymerAInterface$UID$0}",
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
                "})",
                "/** @export */",
                "A.prototype.callBehaviorMethod;",
                "/** @export @protected */",
                "A.prototype.doSomethingFun;")));
  }

  /** If a behavior method is {@code @private} there is a visibility warning. */
  @Test
  public void testBehaviorWithPrivateMethod() {
    enableCheckAccessControls();
    testWarning(
        srcs(
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
                "});")),
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface$UID$0}*/",
            "var A = function() {};",
            "/** @type {!Array} */",
            "A.prototype.pets;",
            "/** @type {string} */",
            "A.prototype.name;",
            "A = Polymer(/** @lends {A.prototype} */ {",
            "  is: 'x-element',",
            "  properties: $jscomp.reflectObject(A, {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  }),",
            "  /**",
            "   * @param {string} funAmount",
            "   * @this {A}",
            "   */",
            "  doSomethingFun: function(funAmount) {",
            "    alert('Element doing something' + funAmount + ' fun!');",
            "  },",
            "  behaviors: [ FunBehavior ],",
            "});",
            "/** @export */",
            "A.prototype.doSomethingFun;"));
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface$UID$0}*/",
            "var A = function() {};",
            "",
            "/** @type {string} */",
            "A.prototype.name;",
            "",
            "A = Polymer(/** @lends {A.prototype} */ {",
            "  is: 'x-element',",
            "  properties: $jscomp.reflectObject(A, {",
            "    name: String,",
            "  }),",
            "  /**",
            "   * @param {string} funAmount",
            "   * @this {A}",
            "   */",
            "  doSomethingFun(funAmount) {",
            "    alert('Element doing something' + funAmount + ' fun!');",
            "  },",
            "  behaviors: [ FunBehavior ],",
            "});",
            "/** @export */",
            "A.prototype.doSomethingFun;"));
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface$UID$0}*/",
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
            "  properties: $jscomp.reflectObject(A, {",
            "    name: String,",
            "  }),",
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface$UID$0}*/",
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
            "  properties: $jscomp.reflectObject(A, {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  }),",
            "  behaviors: [ SuperCoolBehaviors, BoringBehavior ],",
            "});",
            "/** @export */",
            "A.prototype.doSomething;",
            "/** @export */",
            "A.prototype.doSomethingRad;",
            "/** @export */",
            "A.prototype.doSomethingFun;"));
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface$UID$0}*/",
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
            "  properties: $jscomp.reflectObject(A, {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  }),",
            "  behaviors: [ SuperCoolBehaviors ],",
            "});",
            "/** @export */",
            "A.prototype.doSomethingRad;",
            "/** @export */",
            "A.prototype.doSomethingFun;"));
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface$UID$0}*/",
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface$UID$0}*/",
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface$UID$0}*/",
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
            "  properties: $jscomp.reflectObject(A, {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  }),",
            "  behaviors: [ SuperCoolBehaviors, BoringBehavior ],",
            "});",
            "/** @export */",
            "A.prototype.doSomething;"));
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface$UID$0}*/",
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
            "  properties: $jscomp.reflectObject(A, {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  }),",
            "  behaviors: [ SuperCoolBehaviors, BoringBehavior ],",
            "});",
            "/** @export */",
            "A.prototype.doSomething;"));
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
            "/** @constructor @extends {PolymerElement} @implements"
                + " {PolymerAInterface$m1146332801$0}*/",
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
            "  properties: $jscomp.reflectObject(A, {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  }),",
            "  behaviors: [ FunBehavior ],",
            "});",
            "/** @export */",
            "A.prototype.doSomethingFun;"));

    testExternChanges(
        externs(EXTERNS),
        srcs(js),
        expected(
            lines(
                "/** @interface */",
                "var PolymerAInterface$m1146332801$0 = function() {};",
                "/** @type {?} */",
                "PolymerAInterface$m1146332801$0.prototype.isFun;",
                "/** @type {?} */",
                "PolymerAInterface$m1146332801$0.prototype.pets;",
                "/** @type {?} */",
                "PolymerAInterface$m1146332801$0.prototype.name;",
                "/** @param {?} isFun **/",
                "PolymerAInterface$m1146332801$0.prototype._setIsFun;"),
            EXTERNS));
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface$UID$0}*/",
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
            " * @return {?}",
            " */",
            "A.prototype.doSomethingFun = function(funAmount) {};",
            "/** @type {string} */",
            "A.prototype.foo;",
            "/** @type {number} */",
            "A.prototype.someNumber;",
            "A = Polymer(/** @lends {A.prototype} */ {",
            "  is: 'x-element',",
            "  properties: $jscomp.reflectObject(A, {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  }),",
            "  behaviors: [ Polymer.FunBehavior ],",
            "});",
            "/** @export */",
            "A.prototype.doSomethingFun;"));
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface$UID$0}*/",
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
            TestExternsBuilder.getClosureExternsAsSource(),
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
            TestExternsBuilder.getClosureExternsAsSource(),
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
                "/** @constructor @extends {PolymerElement} @implements"
                    + " {PolymerAInterface$UID$0}*/",
                "var A = function() {};",
                "/** @type {string} */ A.prototype.name;",
                "/**",
                " * @param {string} funAmount",
                " * @suppress {unusedPrivateMembers}",
                " * @return {?}",
                " */",
                "A.prototype.doSomethingFun = function(funAmount) {};",
                "/**",
                " * @suppress {unusedPrivateMembers}",
                " * @return {?}",
                " */",
                "A.prototype.doSomethingVeryFun =",
                "    function(a, param$polymer$1, c, veryFun = void 0) {};",
                "/**",
                " * @param {string} coolAmount",
                " * @suppress {unusedPrivateMembers}",
                " * @return {?}",
                " */",
                "A.prototype.doSomethingCool = function(coolAmount) {};",
                "A = Polymer(/** @lends {A.prototype} */ {",
                "  is: 'x-element',",
                "  properties: $jscomp.reflectObject(A, {",
                "    name: String,",
                "  }),",
                "  behaviors: [ Polymer.VeryFunBehavior, behaviors.CoolBehavior ],",
                "});",
                "/** @export */",
                "A.prototype.doSomethingCool;",
                "/** @export */",
                "A.prototype.doSomethingVeryFun;",
                "/** @export */",
                "A.prototype.doSomethingFun;")));
  }

  /**
   * See {@link #testBehaviorInIIFE()} for more information on what this is testing.
   *
   */
  @Test
  public void testBehaviorInModule_destructuringRestParams() {
    test(
        srcs(
            TestExternsBuilder.getClosureExternsAsSource(),
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
            TestExternsBuilder.getClosureExternsAsSource(),
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
                "/** @constructor @extends {PolymerElement} @implements"
                    + " {PolymerAInterface$UID$0}*/",
                "var A = function() {};",
                "/** @type {string} */ A.prototype.name;",
                "/**",
                " * @suppress {unusedPrivateMembers}",
                " * @return {?}",
                " */",
                "A.prototype.doSomethingCool =",
                "    function(param$polymer$0 = void 0, ...param$polymer$1) {};",
                "A = Polymer(/** @lends {A.prototype} */ {",
                "  is: 'x-element',",
                "  properties: $jscomp.reflectObject(A, {",
                "    name: String,",
                "  }),",
                "  behaviors: [ behaviors.CoolBehavior ],",
                "});",
                "/** @export */",
                "A.prototype.doSomethingCool;")));
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface$UID$0}*/",
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
            "  properties: $jscomp.reflectObject(A, {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  }),",
            "  behaviors: [ SuperCoolBehaviors, FunBehavior ],",
            "});",
            "/** @export */",
            "A.prototype.doSomethingRad;",
            "/** @export */",
            "A.prototype.doSomethingFun;"));
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
            "Polymer({", "  is: 'x-element',", "  behaviors: [", "    DoesNotExist", "  ],", "});"),
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface$UID$0} */",
            "var X = function() {};",
            "/** @type {boolean} */",
            "X.prototype.isHappy;",
            "X = Polymer(/** @lends {X.prototype} */ {",
            "  is: 'x-element',",
            "  properties: $jscomp.reflectObject(X, {",
            "    isHappy: Boolean,",
            "  }),",
            "  /** @override @this {X} */",
            "  created: function() {",
            "    this.isHappy = 7;",
            "  },",
            "});",
            "/** @export */",
            "X.prototype.created;"),
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
            "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface$UID$0} */",
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
            "});",
            "/** @export */",
            "X.prototype.funcWithES6;"));
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
            "/** @polymer @implements {PolymerFooInterface$UID$0} */",
            "class Foo extends Bar {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() {",
            "    return $jscomp.reflectObject(Foo, {",
            "      user_: Object,",
            "      pets: {",
            "        type: Array,",
            "        notify: true,",
            "      },",
            "      name: String,",
            "      thingToDo: Function",
            "    });",
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
            "/** @polymer @implements {Polymera_BInterface$UID$0} */",
            "a.B = class extends Foo {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() {",
            "    return $jscomp.reflectObject(a.B, {",
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
            " * @implements {Polymera_BInterface$UID$0}",
            " */",
            "a.B = class extends Foo {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() {",
            "    return $jscomp.reflectObject(a.B, {",
            "      id: Boolean,",
            "      other: {",
            "        type: String,",
            "        reflectToAttribute: true",
            "      }",
            "    });",
            "  }",
            "};",
            "/** @type {boolean} */",
            "a.B.prototype.id;",
            "/** @type {string} */",
            "a.B.prototype.other;"));
  }

  @Test
  public void testObjectReflectionAddedToConfigProperties1() {
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
            "/** @constructor @extends {PolymerElement} @implements {Polymera_BInterface$UID$0}*/",
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
            "/** @constructor */",
            "var User = function() {};",
            "var a = {};",
            "/** @implements {Polymera_BInterface$UID$0} */",
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
    test(
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
            "/** @constructor */",
            "var User = function() {};",
            "/** @constructor @extends {PolymerElement} @implements {PolymerAInterface$UID$0}*/",
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
            "/** @constructor */",
            "var User = function() {};",
            "/** @implements {PolymerAInterface$UID$0} */",
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
    test(
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
            "/** @constructor */",
            "var User = function() {};",
            "/**",
            " * @constructor @extends {PolymerElement}",
            " * @implements {PolymerXElementElementInterface$UID$0}",
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
            "/** @constructor */",
            "var User = function() {};",
            "/** @implements {PolymerXElementInterface$UID$0} */",
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
    test(
        lines(
            "class TestElement extends PolymerElement {",
            "  /** @public */ method1() {}",
            "  /** @private */ method2() {}",
            "}"),
        lines(
            "/** @implements {PolymerTestElementInterface$UID$0} */",
            "class TestElement extends PolymerElement {",
            "  /** @public */ method1() {}",
            "  /** @private */ method2() {}",
            "}",
            "/** @private @export */ TestElement.prototype.method2;",
            "/** @public @export */ TestElement.prototype.method1;"));
  }

  @Test
  public void testExportMethodsFromLegacyElement() {
    test(
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
            " * @implements {PolymerTestElementElementInterface$UID$0}",
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
    test(
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
            " * @implements {PolymerTestElementElementInterface$UID$0}",
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

    test(
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
            "/** @constructor */",
            "var User = function() {};",
            "/** @implements {PolymerXElementInterface$UID$0} */",
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
            "XElement.prototype.thingToDo;",
            "/** @export */",
            "XElement.prototype._nameChanged;",
            "/** @export */",
            "XElement.prototype._petsChanged"));
  }

  @Test
  public void testSimpleObserverStringsConvertedToReferences2() {
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
            "/** @constructor */",
            "var User = function() {};",
            "var a = {};",
            "/** @implements {Polymera_BInterface$UID$0} */",
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
            "a.B.prototype.thingToDo;",
            "/** @export */",
            "a.B.prototype._nameChanged;",
            "/** @export */",
            "a.B.prototype._petsChanged"));
  }

  @Test
  public void testSimpleObserverStringsConvertedToReferences3() {
    test(
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
            "/** @constructor */",
            "var User = function() {};",
            "/** @implements {PolymerAInterface$UID$0} */",
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
            "A.prototype.thingToDo;",
            "/** @export */",
            "A.prototype._nameChanged;",
            "/** @export */",
            "A.prototype._petsChanged"));
  }

  @Test
  public void testReflectionForComputedPropertyStrings1() {
    test(
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
            "/** @constructor */",
            "var User = function() {};",
            "/** @implements {PolymerXElementInterface$UID$0} */",
            "class XElement extends Polymer.Element {",
            "  /** @return {string} */",
            "  static get is() { return 'x-element'; }",
            "  /** @return {" + POLYMER_ELEMENT_PROP_CONFIG + "} */",
            "  static get properties() {",
            "    return $jscomp.reflectObject(XElement, {",
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
            "/** @export */",
            "XElement.prototype._computeName;",
            "/** @export */",
            "XElement.prototype._computePets;"));
  }

  @Test
  public void testReflectionForComputedPropertyStrings2() {
    test(
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
            "/** @constructor */",
            "var User = function() {};",
            "/** @type {string} */ User.prototype.id;",
            "/** @implements {PolymerXElementInterface$UID$0} */",
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
            "        computed: '_computeName(\"user\", 12.0, user_.id, user_.*)'",
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
            "/** @export */",
            "XElement.prototype._computeName;",
            "/** @export */",
            "XElement.prototype._computePets;"));
  }

  @Test
  public void testParseErrorForComputedPropertyStrings1() {
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
    test(
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
            "/** @constructor */",
            "var User = function() {};",
            "/** @implements {PolymerXElementInterface$UID$0} */",
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
            "      '_userChanged(user_)',",
            "      '_userOrThingToDoChanged(user_, thingToDo)'",
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
            "/** @export */",
            "XElement.prototype._userOrThingToDoChanged;",
            "/** @export */",
            "XElement.prototype._userChanged;"));
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
            " * @implements {PolymerPaperMenuButtonInterface$UID$0}",
            " */",
            "var PaperMenuButton = function() {};",
            "/** @type {boolean} */",
            "PaperMenuButton.prototype.opened;",
            "PaperMenuButton = Polymer(/** @lends {PaperMenuButton.prototype} */ {",
            "  is: 'paper-menu-button',",
            "  properties: $jscomp.reflectObject(PaperMenuButton, {",
            "    opened: {type: Boolean, value: false}",
            "  })",
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
            TestExternsBuilder.getClosureExternsAsSource(),
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
            TestExternsBuilder.getClosureExternsAsSource(),
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

  @Test
  public void testPolyerObjectPropertiesAccessedBeforeDefinition() {
    test(
        srcs(
            TestExternsBuilder.getClosureExternsAsSource(),
            lines(
                "/** @polymerBehavior */", //
                "var PtGaUXBehavior = {",
                " _abc: function() { ",
                "    if (true) {}",
                " }",
                "}",
                "function func() {",
                " if (true) {",
                "   Polymer({",
                "     is: 'pt-test-component',",
                "     behaviors: [PtGaUXBehavior],",
                "     properties: {disabled: Boolean}",
                "   });",
                " }",
                "}")),
        expected(
            TestExternsBuilder.getClosureExternsAsSource(),
            lines(
                "/**",
                " * @constructor",
                " * @extends {PolymerElement}",
                " * @implements {PolymerPtTestComponentElementInterface$m1176578414$0}",
                " */",
                // This `var PtTestComponentElement = ...` is placed before any
                // `PtTestComponentElement.prototype.<...>` calls. We don't want to access
                // properties on `PtTestComponentElement` before it is defined.
                "var PtTestComponentElement = function() {",
                "};",
                "/**",
                " * @polymerBehavior",
                " * @nocollapse",
                " * @polymerBehavior",
                " */",
                "var PtGaUXBehavior = {/**",
                " * @suppress {checkTypes,globalThis,visibility}",
                " */",
                "_abc:function() {",
                "  if (true) {",
                "  }",
                "}};",
                "function func() {",
                "  if (true) {",
                "    Polymer(/** @lends {PtTestComponentElement.prototype} */ ",
                "    {is:\"pt-test-component\", behaviors:[PtGaUXBehavior],"
                    + " properties:$jscomp.reflectObject(PtTestComponentElement,"
                    + " {disabled:Boolean})});",
                "    /** @export */",
                "    PtTestComponentElement.prototype._abc;",
                "  }",
                "}",
                "/** @type {boolean} */ ",
                "PtTestComponentElement.prototype.disabled;",
                "/**",
                " * @suppress {unusedPrivateMembers}",
                " */",
                "PtTestComponentElement.prototype._abc = function() {",
                "  if (true) {",
                "  }",
                "};")));
  }

  @Override
  public void test(String js, String expected) {
    this.test(srcs(js), expected(expected));
  }

  @Override
  public void test(TestPart... testParts) {
    // Override this method so that we can ensure getActualExpected gets called
    Sources srcs = null;
    Expected expected = null;
    ArrayList<TestPart> modifiedTestParts = new ArrayList<>();
    for (TestPart testPart : testParts) {
      if (testPart instanceof Sources) {
        srcs = (Sources) testPart;
        modifiedTestParts.add(srcs);
      } else if (testPart instanceof Expected) {
        expected = (Expected) testPart;
      } else {
        modifiedTestParts.add(testPart);
      }
    }
    Preconditions.checkNotNull(srcs);
    if (expected != null) {
      modifiedTestParts.add(getActualExpected(srcs, expected));
    }
    TestPart[] testPartsArray = modifiedTestParts.toArray(new TestPart[0]);
    super.test(testPartsArray);
  }

  // Note: we don't have a version of testExternChanges that calls getActualExpected() here and
  // require test cases to hardcode the
  // full "Interface$m123..456$0" id instead. This is because "Interface$m123..456$0" gets put
  // in the synthetic externs, but named based on an input source file path, so getActualExpected
  // doesn't know what file path to use.
  // TODO(lharker): it would be a minor readability improvement to add a different helper to
  // support this case.

  // Helper to change the generic name string "Interface$UID$0" in the expected code with
  // actual string "Interface$m123..456$0" that will get produced
  private Expected getActualExpected(Sources originalSources, Expected originalExpected) {
    return expected(
        UnitTestUtils.updateGenericVarNamesInExpectedFiles(
            (FlatSources) originalSources,
            originalExpected,
            ImmutableMap.of("Interface$UID", "Interface$")));
  }
}
