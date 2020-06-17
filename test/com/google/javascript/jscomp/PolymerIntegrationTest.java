/*
 * Copyright 2020 The Closure Compiler Authors.
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
import static com.google.javascript.jscomp.CompilerTestCase.CLOSURE_DEFS;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_MISPLACED_PROPERTY_JSDOC;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.DevMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerOptions.Reach;
import com.google.javascript.jscomp.parsing.Config.JsDocParsing;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for the compiler with the --polymer_pass enabled. */
@RunWith(JUnit4.class)
public final class PolymerIntegrationTest extends IntegrationTestCase {

  private static final String EXPORT_PROPERTY_DEF =
      lines(
          "goog.exportProperty = function(object, publicName, symbol) {",
          "  object[publicName] = symbol;",
          "};");

  /** Creates a CompilerOptions object with google coding conventions. */
  @Override
  protected CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setDevMode(DevMode.EVERY_PASS);
    options.setCodingConvention(new GoogleCodingConvention());
    ;
    return options;
  }

  private void addPolymerExterns() {
    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);
    externsList.add(
        SourceFile.fromCode(
            "polymer_externs.js",
            lines(
                "/** @return {function(new: PolymerElement)} */",
                "var Polymer = function(descriptor) {};",
                "",
                "/** @constructor @extends {HTMLElement} */",
                "var PolymerElement = function() {};",  // Polymer 1
                "",
                "/** @constructor @extends {HTMLElement} */",
                "Polymer.Element = function() {};",  // Polymer 2
                "",
                "/** @typedef {Object} */",
                "let PolymerElementProperties;",
                "")));
    externs = externsList.build();
  }

  @Test
  public void testPolymerCorrectlyResolvesGlobalTypedefs_forIIFEs() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setParseJsDocDocumentation(JsDocParsing.INCLUDE_ALL_COMMENTS);
    addPolymerExterns();

    test(
        options,
        lines(
            "/** @typedef {{foo: string}} */",
            "let MyTypedef;",
            "(function() {",
            "Polymer({",
            "is: 'x-foo',",
            "properties: {",
            "/** @type {!MyTypedef} */",
            "value: string,",
            "},",
            "});",
            "})();"),
        lines(
            "var XFooElement=function(){};",
            "var MyTypedef;",
            "(function(){",
            "XFooElement.prototype.value;",
            "Polymer({",
            "is:'x-foo',",
            "properties: {",
            "value:string}",
            "}",
            ")",
            "})()"));
  }

  @Test
  public void testPolymerPropertiesDoNotGetRenamed() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setParseJsDocDocumentation(JsDocParsing.INCLUDE_ALL_COMMENTS);
    options.setRenamingPolicy(VariableRenamingPolicy.ALL, PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setRemoveUnusedPrototypeProperties(true);
    options.polymerExportPolicy = PolymerExportPolicy.EXPORT_ALL;
    options.setGenerateExports(true);
    options.setExportLocalPropertyDefinitions(true);
    addPolymerExterns();

    testNoWarnings(
        options,
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
            "const obj = {randomProperty: 0, otherProperty: 1};"));

    String source = lastCompiler.getCurrentJsSource();
    String expectedSource =
        EMPTY_JOINER.join(
            "var a=function(){};",
            "(function(){",
            "a.prototype.value;",
            "Polymer({",
            "a:\"foo\",",
            "c:{value:Object}})})();",
            "var b={randomProperty:0,b:1};");
    assertThat(source).isEqualTo(expectedSource);
  }

  @Test
  public void testPolymerImplicitlyUnknownPropertiesDoNotGetRenamed() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setParseJsDocDocumentation(JsDocParsing.INCLUDE_ALL_COMMENTS);
    options.setRenamingPolicy(VariableRenamingPolicy.ALL, PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setRemoveUnusedPrototypeProperties(true);
    options.polymerExportPolicy = PolymerExportPolicy.EXPORT_ALL;
    options.setGenerateExports(true);
    options.setExportLocalPropertyDefinitions(true);
    addPolymerExterns();

    testNoWarnings(
        options,
        lines(
            "(function() {",
            "  Polymer({",
            "    is: 'foo',",
            "    properties: {",
            "     /** @type {{randomProperty}} */",
            "     value: Object",
            "  }",
            "  });",
            "})();",
            "",
            "const obj = {randomProperty: 0, otherProperty: 1};"));

    String source = lastCompiler.getCurrentJsSource();
    String expectedSource =
        EMPTY_JOINER.join(
            "var a=function(){};",
            "(function(){a.prototype.value;",
            "Polymer({",
            "a:\"foo\",",
            "c:{value:Object}})})();",
            "var b={randomProperty:0,b:1};");
    assertThat(source).isEqualTo(expectedSource);
  }

  @Test
  public void testPolymerCorrectlyResolvesUserDefinedLocalTypedefs_forIIFEs() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setParseJsDocDocumentation(JsDocParsing.INCLUDE_ALL_COMMENTS);
    addPolymerExterns();

    test(
        options,
        lines(
            "(function() {",
            "/** @typedef {{foo: string}} */",
            "let localTypeDef;",
            "Polymer({",
            "is: 'x-foo',",
            "properties: {",
            "/** @type {localTypeDef} */",
            "value: string,",
            "},",
            "});",
            "})();"),
        lines(
            "var XFooElement=function(){};",
            "(function(){",
            "XFooElement.prototype.value;",
            "var localTypeDef;",
            "Polymer({is:'x-foo',properties:{value:string}})})()"));
  }

  @Test
  public void testPolymerCorrectlyResolvesPrimitiveLocalTypes_forIIFEs() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setParseJsDocDocumentation(JsDocParsing.INCLUDE_ALL_COMMENTS);
    options.setClosurePass(true);
    addPolymerExterns();

    test(
        options,
        lines(
            "(function() {",
            "Polymer({",
            "is: 'x-foo',",
            "properties: {",
            "/** @type {string} */",
            "value: string,",
            "},",
            "});",
            "})();"),
        lines(
            "var XFooElement=function(){};",
            "(function(){",
            "XFooElement.prototype.value;",
            "Polymer({is:'x-foo',properties:{value:string}})})()"));
  }

  @Test
  public void testPolymerCorrectlyResolvesLocalTypes_forModules() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setParseJsDocDocumentation(JsDocParsing.INCLUDE_ALL_COMMENTS);
    options.setClosurePass(true);
    addPolymerExterns();
    test(
        options,
        lines(
            "goog.module('a');",
            "/** @typedef {{foo: number}} */",
            "let MyTypedef;",
            "Polymer({",
            "is: 'x-foo',",
            "properties: {",
            "/** @type {MyTypedef} */",
            "value: number,",
            "},",
            "});"),
        lines(
            "var module$exports$a={};",
            "var module$contents$a_MyTypedef;",
            "var XFooElement=function(){};",
            "XFooElement.prototype.value;",
            "Polymer({is:\"x-foo\",properties:{value:number}})"));
  }

  @Test
  public void testPolymerCallWithinES6Modules_CreatesDeclarationOutsideModule() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    addPolymerExterns();

    options.setBadRewriteModulesBeforeTypecheckingThatWeWantToGetRidOf(false);

    String[] srcs =
        new String[] {
          CLOSURE_DEFS,
          lines(
              "Polymer({", //
              "  is: 'x',",
              "});",
              "export {}"),
        };

    String[] compiledOut =
        new String[] {
          lines(
              "/** @constructor @extends {PolymerElement} @implements {PolymerXInterface0} */",
              "var XElement = function() {};",
              CLOSURE_DEFS),
          lines(
              "Polymer(/** @lends {X.prototype} */ {", //
              "  is: 'x',",
              "});",
              "var module$i1={}"),
        };

    test(options, srcs, compiledOut);
  }

  @Test
  public void testPolymer1() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    addPolymerExterns();

    test(
        options,
        "var XFoo = Polymer({ is: 'x-foo' });",
        "var XFoo=function(){};XFoo=Polymer({is:'x-foo'})");
  }

  @Test
  public void testPolymerBehaviorLegacyGoogModule() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setClosurePass(true);
    addPolymerExterns();

    test(
        options,
        new String[] {
          lines(
              "goog.module('behaviors.FunBehavior');",
              "goog.module.declareLegacyNamespace();",
              "/** @polymerBehavior */",
              "exports = {};"),
          "var XFoo = Polymer({ is: 'x-foo', behaviors: [ behaviors.FunBehavior ] });"
        },
        new String[] {
          "var behaviors = {}; behaviors.FunBehavior = {};",
          lines(
              "var XFoo=function(){};",
              "XFoo = Polymer({",
              "  is:'x-foo',",
              "  behaviors: [ behaviors.FunBehavior ]",
              "});")
        });
  }

  @Test
  public void testDuplicatePropertyNames_transitive() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setClosurePass(true);
    addPolymerExterns();
    testNoWarnings(
        options,
        new String[] {
            CLOSURE_DEFS,
            lines(
                "goog.module('A');",
                "/** @polymerBehavior */",
                "const FunBehavior = {",
                "  properties: {",
                "    isFun: Boolean",
                "  },",
                "};",
                "",
                "/** @polymerBehavior */",
                "const RadBehavior = {",
                "  properties: {",
                "    howRad: Number",
                "  },",
                "};",
                "",
                "/** @polymerBehavior */",
                "const SuperCoolBehaviors = [FunBehavior, RadBehavior];",
                "exports = {SuperCoolBehaviors, FunBehavior}"
            ),
            lines(
                "goog.module('B')",
                "const {SuperCoolBehaviors, FunBehavior} = goog.require('A')",
                "A = Polymer({",
                "  is: 'x-element',",
                "  properties: {",
                "    isFun: {",
                "      type: Array,",
                "      notify: true,",
                "    },",
                "    name: String,",
                "  },",
                "  behaviors: [ SuperCoolBehaviors, FunBehavior ],",
                "});")});
  }

  /**
   * Tests that no reference to the 'Data' module's local variable 'Item' gets generated in the
   * 'Client' module by the PolymerClassRewriter.
   */
  @Test
  public void testNoUnrecognizedTypeErrorForBehaviorInsideGoogModule() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setClosurePass(true);
    addPolymerExterns();
    testNoWarnings(
        options,
        new String[] {
          CLOSURE_DEFS,
          lines(
              "goog.module('Data');",
              "class Item {",
              "}",
              "exports.Item = Item;",
              "/**",
              " * A Polymer behavior providing common data access and formatting methods.",
              " * @polymerBehavior",
              " */",
              "exports.SummaryDataBehavior = {",
              "  /**",
              "   * @param {?Item} item",
              "   * @return {*}",
              "   * @export",
              "   */",
              "  getValue(item) {",
              "    return item;",
              "  },",
              "};"),
          lines(
              "goog.module('Client');",
              "const Data = goog.require('Data');",
              "var A = Polymer({",
              "  is: 'x-element',",
              "  behaviors: [ Data.SummaryDataBehavior ],",
              "});")
        });
  }

  @Test
  public void testConstPolymerElementAllowed() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(1);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    addPolymerExterns();

    testNoWarnings(options, "const Foo = Polymer({ is: 'x-foo' });");
  }

  private void addPolymer2Externs() {
    ImmutableList.Builder<SourceFile> externsList = ImmutableList.builder();
    externsList.addAll(externs);

    externsList.add(
        SourceFile.fromCode(
            "polymer_externs.js",
            lines(
                "",
                "/**",
                " * @param {!Object} init",
                " * @return {!function(new:HTMLElement)}",
                " */",
                "function Polymer(init) {}",
                "",
                "Polymer.ElementMixin = function(mixin) {}",
                "",
                "/** @typedef {!Object} */",
                "var PolymerElementProperties;",
                "",
                "/** @interface */",
                "function Polymer_ElementMixin() {}",
                "/** @type {string} */",
                "Polymer_ElementMixin.prototype._importPath;",

                "",
                "/**",
                "* @interface",
                "* @extends {Polymer_ElementMixin}",
                "*/",
                "function Polymer_LegacyElementMixin(){}",
                "/** @type {boolean} */",
                "Polymer_LegacyElementMixin.prototype.isAttached;",

                "/**",
                " * @constructor",
                " * @extends {HTMLElement}",
                " * @implements {Polymer_LegacyElementMixin}",
                " */",
                "var PolymerElement = function() {};",

                ""
                )));

    externsList.add(
        SourceFile.fromCode(
            "html5.js",
            lines(
                "/** @constructor */",
                "function Element() {}",
                "",
                "/**",
                " * @see"
                    + " https://html.spec.whatwg.org/multipage/custom-elements.html#customelementregistry",
                " * @constructor",
                " */",
                "function CustomElementRegistry() {}",
                "",
                "/**",
                " * @param {string} tagName",
                " * @param {function(new:HTMLElement)} klass",
                " * @param {{extends: string}=} options",
                " * @return {undefined}",
                " */",
                "CustomElementRegistry.prototype.define = function (tagName, klass, options) {};",
                "",
                "/**",
                " * @param {string} tagName",
                " * @return {function(new:HTMLElement)|undefined}",
                " */",
                "CustomElementRegistry.prototype.get = function(tagName) {};",
                "",
                "/**",
                " * @param {string} tagName",
                " * @return {!Promise<undefined>}",
                " */",
                "CustomElementRegistry.prototype.whenDefined = function(tagName) {};",
                "",
                "/** @type {!CustomElementRegistry} */",
                "var customElements;",
                "")));

    externs = externsList.build();
  }

  // Regression test for b/77650996
  @Test
  public void testPolymer2b() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(2);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    addPolymer2Externs();

    test(
        options,
        new String[] {
          lines(
              "class DeviceConfigEditor extends Polymer.Element {",
              "",
              "  static get is() {",
              "    return 'device-config-editor';",
              "  }",
              "",
              "  static get properties() {",
              "    return {};",
              "  }",
              "}",
              "",
              "window.customElements.define(DeviceConfigEditor.is, DeviceConfigEditor);"),
          lines(
              "(function() {",
              "  /**",
              "   * @customElement",
              "   * @polymer",
              "   * @memberof Polymer",
              "   * @constructor",
              "   * @implements {Polymer_ElementMixin}",
              "   * @extends {HTMLElement}",
              "   */",
              "  const Element = Polymer.ElementMixin(HTMLElement);",
              "",
              "  /**",
              "   * @constructor",
              "   * @implements {Polymer_ElementMixin}",
              "   * @extends {HTMLElement}",
              "   */",
              "  Polymer.Element = Element;",
              "})();",
              ""),
        },
        (String []) null);
  }

  @Test
  public void testPolymer1b() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(2);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    addPolymer2Externs();

    test(
        options,
        new String[] {
          lines(
              "Polymer({",
              "  is: 'paper-button'",
              "});"),
          lines(
              "(function() {",
              "  /**",
              "   * @customElement",
              "   * @polymer",
              "   * @memberof Polymer",
              "   * @constructor",
              "   * @implements {Polymer_ElementMixin}",
              "   * @extends {HTMLElement}",
              "   */",
              "  const Element = Polymer.ElementMixin(HTMLElement);",
              "",
              "  /**",
              "   * @constructor",
              "   * @implements {Polymer_ElementMixin}",
              "   * @extends {HTMLElement}",
              "   */",
              "  Polymer.Element = Element;",
              "})();",
              ""),
        },
        (String []) null);
  }

  @Test
  public void testPolymer2a() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(2);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    addPolymerExterns();

    Compiler compiler = compile(
        options,
        lines(
            "class XFoo extends Polymer.Element {",
            "  get is() { return 'x-foo'; }",
            "  static get properties() { return {}; }",
            "}"));
    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testPolymerElementImportedFromEsModule() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(2);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    addPolymerExterns();

    Compiler compiler =
        compile(
            options,
            new String[] {
              lines("export class PolymerElement {};"),
              lines(
                  "import {PolymerElement} from './i0.js';",
                  "class Foo extends PolymerElement {",
                  "  get is() { return 'foo-element'; }",
                  "  static get properties() { return { fooProp: String }; }",
                  "}",
                  "const foo = new Foo();",
                  // This property access would be an unknown property error unless the PolymerPass
                  // had successfully parsed the element definition.
                  "foo.fooProp;")
            });
    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testPolymerFunctionImportedFromEsModule() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(2);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    addPolymerExterns();

    Compiler compiler =
        compile(
            options,
            new String[] {
              lines("export function Polymer(def) {};"),
              lines(
                  "import {Polymer} from './i0.js';",
                  "Polymer({",
                  "  is: 'foo-element',",
                  "  properties: { fooProp: String },",
                  "});",
                  // This interface cast and property access would be an error unless the
                  // PolymerPass had successfully parsed the element definition.
                  "const foo = /** @type{!FooElementElement} */({});",
                  "foo.fooProp;")
            });
    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).isEmpty();
  }

  /** See b/64389806. */
  @Test
  public void testPolymerBehaviorWithTypeCast() {
    CompilerOptions options = createCompilerOptions();
    options.setPolymerVersion(2);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    addPolymerExterns();

    test(
        options,
        lines(
            "Polymer({",
            "  is: 'foo-element',",
            "  behaviors: [",
            "    ((/** @type {?} */ (Polymer))).SomeBehavior",
            "  ]",
            "});",
            "/** @polymerBehavior */",
            "Polymer.SomeBehavior = {};"),
        lines(
            "var FooElementElement=function(){};",
            "Polymer({",
            "  is:\"foo-element\",",
            "  behaviors:[Polymer.SomeBehavior]",
            "});",
            "Polymer.SomeBehavior={}"));
  }

  @Test
  public void testPolymerExportPolicyExportAllClassBased() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setPolymerVersion(2);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    addPolymerExterns();

    options.setRenamingPolicy(
        VariableRenamingPolicy.ALL, PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setRemoveUnusedPrototypeProperties(true);
    options.polymerExportPolicy = PolymerExportPolicy.EXPORT_ALL;
    options.setGenerateExports(true);
    options.setExportLocalPropertyDefinitions(true);

    Compiler compiler =
        compile(
            options,
            lines(
                EXPORT_PROPERTY_DEF,
                "class FooElement extends PolymerElement {",
                "  static get properties() {",
                "    return {",
                "      longUnusedProperty: String,",
                "    }",
                "  }",
                "  longUnusedMethod() {",
                "    return this.longUnusedProperty;",
                "  }",
                "}"));
    String source = compiler.getCurrentJsSource();

    // If we see these identifiers anywhere in the output source, we know that we successfully
    // protected it against removal and renaming.
    assertThat(source).contains("longUnusedProperty");
    assertThat(source).contains("longUnusedMethod");

    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testPolymerExportPolicyExportAllLegacyElement() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setPolymerVersion(2);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    addPolymerExterns();

    options.setRenamingPolicy(VariableRenamingPolicy.ALL, PropertyRenamingPolicy.ALL_UNQUOTED);
    options.setRemoveUnusedPrototypeProperties(true);
    options.setRemoveUnusedVariables(Reach.ALL);
    options.setRemoveDeadCode(true);
    options.polymerExportPolicy = PolymerExportPolicy.EXPORT_ALL;
    options.setGenerateExports(true);
    options.setExportLocalPropertyDefinitions(true);

    Compiler compiler =
        compile(
            options,
            lines(
                EXPORT_PROPERTY_DEF,
                "Polymer({",
                "  is: \"foo-element\",",
                "  properties: {",
                "    longUnusedProperty: String,",
                "  },",
                "  longUnusedMethod: function() {",
                "    return this.longUnusedProperty;",
                "  },",
                "});"));
    String source = compiler.getCurrentJsSource();

    // If we see these identifiers anywhere in the output source, we know that we successfully
    // protected them against removal and renaming.
    assertThat(source).contains("longUnusedProperty");
    assertThat(source).contains("longUnusedMethod");

    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).isEmpty();
  }

  @Test
  public void testPolymerPropertyDeclarationsWithConstructor() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setPolymerVersion(2);
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
    // By setting the EXPORT_ALL export policy, all properties will be added to an interface that
    // is injected into the externs. We need to make sure the types of the properties on this
    // interface aligns with the types we declared in the constructor, or else we'll get an error.
    options.polymerExportPolicy = PolymerExportPolicy.EXPORT_ALL;
    addPolymer2Externs();

    Compiler compiler =
        compile(
            options,
            lines(
                EXPORT_PROPERTY_DEF,
                "class FooElement extends PolymerElement {",
                "  constructor() {",
                "    super();",
                "    /** @type {number} */",
                "    this.p1 = 0;",
                "    /** @type {string|undefined} */",
                "    this.p2;",
                "    if (condition) {",
                "      this.p3 = true;",
                "    }",
                "  }",
                "  static get properties() {",
                "    return {",
                "      /** @type {boolean} */",
                "      p1: String,",
                "      p2: String,",
                "      p3: Boolean,",
                "      p4: Object,",
                "      /** @type {number} */",
                "      p5: String,",
                "    };",
                "  }",

                // p1 has 3 possible types that could win out: 1) string (inferred from the Polymer
                // attribute de-serialization function), 2) boolean (from the @type annotation in
                // the properties configuration), 3) number (from the @type annotation in the
                // constructor). We want the constructor annotation to win (number). If it didn't,
                // this method signature would have a type error.
                "  /** @return {number}  */ getP1() { return this.p1; }",
                "  /** @return {string|undefined}  */ getP2() { return this.p2; }",
                "  /** @return {boolean} */ getP3() { return this.p3; }",
                "  /** @return {!Object} */ getP4() { return this.p4; }",
                "  /** @return {number}  */ getP5() { return this.p5; }",
                "}"));

    assertThat(compiler.getErrors()).isEmpty();

    // We should have one warning: that property p1 shouldn't have any JSDoc inside the properties
    // configuration, because when a property is also declared in the constructor, the constructor
    // JSDoc will take precedence.
    ImmutableList<JSError> warnings = compiler.getWarnings();
    assertThat(warnings).hasSize(1);
    JSError warning = warnings.get(0);
    assertThat(warning.getType()).isEqualTo(POLYMER_MISPLACED_PROPERTY_JSDOC);
    assertThat(warning.getNode().getString()).isEqualTo("p1");
  }
}
