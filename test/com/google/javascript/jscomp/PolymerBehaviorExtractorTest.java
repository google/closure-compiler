/*
 * Copyright 2016 The Closure Compiler Authors.
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
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_INVALID_BEHAVIOR;
import static com.google.javascript.jscomp.testing.JSErrorSubject.assertError;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.PolymerBehaviorExtractor.BehaviorDefinition;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.modules.ModuleMapCreator;
import com.google.javascript.jscomp.modules.ModuleMetadataMap;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import org.jspecify.nullness.Nullable;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link PolymerBehaviorExtractor}. */
@RunWith(JUnit4.class)
public class PolymerBehaviorExtractorTest extends CompilerTypeTestCase {

  private PolymerBehaviorExtractor extractor;
  private @Nullable Node behaviorArray;
  private ModuleMetadataMap moduleMetadataMap;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    behaviorArray = null;
  }

  @Test
  public void testArrayBehavior() {
    parseAndInitializeExtractor(
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
            "  behaviors: [ SuperCoolBehaviors, BoringBehavior ],",
            "});"));

    ImmutableList<BehaviorDefinition> defs = extractor.extractBehaviors(behaviorArray, null);
    assertThat(defs).hasSize(3);

    // TODO(jlklein): Actually verify the properties of the BehaviorDefinitions.
  }

  @Test
  public void testArrayBehavior_fromGoogProvide() {
    parseAndInitializeExtractor(
        lines(
            "goog.provide('my.project.RadBehavior');",
            "goog.provide('my.project.FunBehavior');",
            "/** @polymerBehavior */",
            "my.project.FunBehavior = {",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "  /** @param {string} funAmount */",
            "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "  /** @override */",
            "  created: function() {}",
            "};",
            "/** @polymerBehavior */",
            "my.project.RadBehavior = {",
            "  properties: {",
            "    howRad: Number",
            "  },",
            "  /** @param {number} radAmount */",
            "  doSomethingRad: function(radAmount) { alert('Something ' + radAmount + ' rad!'); },",
            "  /** @override */",
            "  ready: function() {}",
            "};",
            "/** @polymerBehavior */",
            "var SuperCoolBehaviors = [my.project.FunBehavior, my.project.RadBehavior];",
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
            "  behaviors: [ SuperCoolBehaviors, BoringBehavior ],",
            "});"));

    ImmutableList<BehaviorDefinition> defs = extractor.extractBehaviors(behaviorArray, null);
    assertThat(defs).hasSize(3);
  }

  @Test
  public void testBehaviorDeclaredAndUsedWithinModule() {
    parseAndInitializeExtractor(
        lines(
            "goog.module('modules.rad');",
            createBehaviorDefinition("RadBehavior"),
            createBehaviorUsage("RadBehavior")));

    assertSingleBehaviorExtractionSucceeds(
        moduleMetadataMap.getModulesByGoogNamespace().get("modules.rad"));
  }

  // Test global names set in modules

  @Test
  public void testBehavior_propertyOnGlobalSetInModule() {
    parseAndInitializeExtractor(
        "function Polymer() {}",
        createBehaviorDefinition("RadBehavior") + "export {RadBehavior};",
        // Verify that we can resolve 'RadBehavior' when we see the 'Polymer.RadBehavior' assignment
        // despite the assignment being 'global', not module-level.
        "import {RadBehavior} from './testcode1'; Polymer.RadBehavior = RadBehavior;",
        createBehaviorUsage("Polymer.RadBehavior"));

    assertSingleBehaviorExtractionSucceeds(moduleMetadataMap.getModulesByPath().get("testcode4"));
  }

  // Test cross-ES-module behaviors.

  @Test
  public void testBehavior_esExport_namedImport() {
    parseAndInitializeExtractor(
        lines(
            createBehaviorDefinition("RadBehaviorLocal"),
            "export {RadBehaviorLocal as RadBehavior};"),
        lines(
            "import {RadBehavior} from './testcode0'", //
            createBehaviorUsage("RadBehavior")));

    assertSingleBehaviorExtractionSucceeds(moduleMetadataMap.getModulesByPath().get("testcode1"));
  }

  @Test
  public void testBehavior_esExport_invalidNamedImport() {
    parseAndInitializeExtractor(
        "export const NotABehavior = {};",
        lines(
            "import {NotABehavior} from './testcode0'", //
            createBehaviorUsage("NotABehavior")));

    extractor.extractBehaviors(
        behaviorArray, moduleMetadataMap.getModulesByPath().get("testcode1"));
    assertThat(compiler.getErrors()).hasSize(1);
  }

  @Test
  public void testBehavior_esExport_namedImport_ofArray() {
    parseAndInitializeExtractor(
        lines(
            createBehaviorDefinition("RadBehaviorLocal"),
            "/** @polymerBehavior */",
            "export const RadBehavior = [ RadBehaviorLocal ];"),
        lines(
            "import {RadBehavior} from './testcode0'", //
            createBehaviorUsage("RadBehavior")));

    assertSingleBehaviorExtractionSucceeds(moduleMetadataMap.getModulesByPath().get("testcode1"));
  }

  @Test
  public void testBehavior_esExport_exportDeclaration() {
    parseAndInitializeExtractor(
        lines(
            "/** @polymerBehavior */",
            "export const RadBehavior = {",
            "  /** @param {number} radAmount */",
            "  doSomethingRad: function(radAmount) { alert('Something ' + radAmount + ' rad!'); },",
            "};"),
        lines(
            "import {RadBehavior} from './testcode0'", //
            createBehaviorUsage("RadBehavior")));

    assertSingleBehaviorExtractionSucceeds(moduleMetadataMap.getModulesByPath().get("testcode1"));
  }

  @Ignore // NOTE(b/137143479): this case seems uncommon, but we can support it if it turns out to
  // be necessary.
  @Test
  public void testBehavior_esExport_defaultExport() {
    parseAndInitializeExtractor(
        lines(createBehaviorDefinition("RadBehavior"), "export default RadBehavior"),
        lines(
            "import RadBehaviorImported from './testcode0'",
            createBehaviorUsage("RadBehaviorImported")));

    assertSingleBehaviorExtractionSucceeds(moduleMetadataMap.getModulesByPath().get("testcode1"));
  }

  @Test
  public void testBehavior_esExport_importStar() {
    parseAndInitializeExtractor(
        lines(
            createBehaviorDefinition("RadBehaviorLocal"),
            "export {RadBehaviorLocal as RadBehavior};"),
        lines(
            "import * as mod from './testcode0'", //
            createBehaviorUsage("mod.RadBehavior")));

    assertSingleBehaviorExtractionSucceeds(moduleMetadataMap.getModulesByPath().get("testcode1"));
  }

  @Test
  public void testBehavior_esExport_invalidImportStar() {
    parseAndInitializeExtractor(
        lines(
            createBehaviorDefinition("RadBehaviorLocal"),
            "export {RadBehaviorLocal as RadBehavior};"),
        lines(
            "import * as mod from './testcode0'", //
            // Try using 'mod' instead of 'mod.RadBehavior'.
            createBehaviorUsage("mod")));

    extractor.extractBehaviors(
        behaviorArray, moduleMetadataMap.getModulesByPath().get("testcode1"));
    assertThat(compiler.getErrors()).hasSize(1);
  }

  @Test
  public void testBehavior_esExport_PropOnImportStar() {
    parseAndInitializeExtractor(
        lines(
            createBehaviorDefinition("RadBehaviorLocal"),
            "const ns = {};",
            "ns.RadBehavior = RadBehaviorLocal;",
            "export {ns};"),
        lines("import * as mod from './testcode0'", createBehaviorUsage("mod.ns.RadBehavior")));

    assertSingleBehaviorExtractionSucceeds(moduleMetadataMap.getModulesByPath().get("testcode1"));
  }

  // Test provides used in modules

  @Test
  public void testBehavior_defaultGoogProvideImport() {
    parseAndInitializeExtractor(
        lines("goog.provide('rad.RadBehavior');", createBehaviorDefinition("rad.RadBehavior")),
        lines(
            "goog.module('a');",
            "const RadBehaviorLocal = goog.require('rad.RadBehavior');",
            createBehaviorUsage("RadBehaviorLocal")));

    assertSingleBehaviorExtractionSucceeds(moduleMetadataMap.getModulesByGoogNamespace().get("a"));
  }

  @Test
  public void testBehavior_destructuringGoogProvideImport() {
    parseAndInitializeExtractor(
        lines(
            "goog.provide('rad');",
            "/** @const */",
            "rad.behaviors = {};",
            createBehaviorDefinition("rad.behaviors.RadBehavior")),
        lines(
            "goog.module('a');",
            "const {behaviors: b} = goog.require('rad');",
            createBehaviorUsage("b.RadBehavior")));

    assertSingleBehaviorExtractionSucceeds(moduleMetadataMap.getModulesByGoogNamespace().get("a"));
  }

  // Test legacy goog.modules

  @Test
  public void testBehavior_legacyGoogModuleNamedExportOfLocalName() {
    parseAndInitializeExtractor(
        lines(
            "goog.module('modules.rad');",
            "goog.module.declareLegacyNamespace();",
            createBehaviorDefinition("RadBehavior"),
            "exports = {RadBehavior};"),
        lines(
            "goog.require('modules.rad');", //
            createBehaviorUsage("modules.rad.RadBehavior")));

    assertSingleBehaviorExtractionSucceeds();
  }

  @Test
  public void testBehavior_legacyGoogModuleNamedExport_inLoadModuleCall() {
    parseAndInitializeExtractor(
        lines(
            "goog.loadModule(function(exports) {",
            "  goog.module('modules.rad');",
            "  goog.module.declareLegacyNamespace();",
            createBehaviorDefinition("exports.RadBehavior"),
            "  return exports;",
            "});"),
        lines(
            "goog.require('modules.rad');", //
            createBehaviorUsage("modules.rad.RadBehavior")));

    assertSingleBehaviorExtractionSucceeds();
  }

  @Test
  public void testBehavior_defaultLegacyGoogModuleExport_exportsLocalVariable() {
    parseAndInitializeExtractor(
        lines(
            "goog.module('modules.rad.RadBehavior');",
            "goog.module.declareLegacyNamespace();",
            createBehaviorDefinition("RadBehavior"),
            "exports = RadBehavior;"),
        lines(
            "goog.require('rad.RadBehavior');", //
            createBehaviorUsage("modules.rad.RadBehavior")));

    assertSingleBehaviorExtractionSucceeds();
  }

  @Test
  public void testBehavior_defaultLegacyGoogModuleExport_assignedDirectly() {
    parseAndInitializeExtractor(
        lines(
            "goog.module('modules.rad.RadBehavior');",
            "goog.module.declareLegacyNamespace();",
            "/** @polymerBehavior */",
            "exports = {",
            "  properties: {",
            "    howRad: Number",
            "  },",
            "  /** @param {number} radAmount */",
            "  doSomethingRad:",
            "    function(radAmount) { alert('Something ' + radAmount + 'rad!'); },",
            "  /** @override */",
            "  ready: function() {}",
            "};"),
        lines(
            "goog.require('modules.rad.RadBehavior');", //
            createBehaviorUsage("modules.rad.RadBehavior")));

    assertSingleBehaviorExtractionSucceeds();
  }

  @Test
  public void testBehavior_nonLegacyGoogModuleDoesNotResolve() {
    parseAndInitializeExtractor(
        lines("goog.module('modules.rad');", createBehaviorDefinition("exports.RadBehavior")),
        lines(
            "goog.require('modules.rad');", //
            createBehaviorUsage("modules.rad.RadBehavior")));

    extractor.extractBehaviors(behaviorArray, null);
    assertThat(compiler.getErrors()).hasSize(1);
  }

  // Test goog.modules

  @Test
  public void testBehavior_googRequireDefaultGoogModuleExport() {
    parseAndInitializeExtractor(
        lines(
            "goog.module('rad.RadBehavior');",
            createBehaviorDefinition("RadBehaviorLocal"),
            "exports = RadBehaviorLocal;"),
        lines(
            "goog.module('a');",
            "const RadBehaviorImported = goog.require('rad.RadBehavior');",
            createBehaviorUsage("RadBehaviorImported")));

    assertSingleBehaviorExtractionSucceeds(moduleMetadataMap.getModulesByGoogNamespace().get("a"));
  }

  @Test
  public void testBehavior_googRequireDefaultGoogModuleExportInLoadModule() {
    parseAndInitializeExtractor(
        lines(
            "goog.loadModule(function(exports) {",
            "  goog.module('rad.RadBehavior');",
            createBehaviorDefinition("RadBehaviorLocal"),
            "  exports = RadBehaviorLocal;",
            "  return exports;",
            "});"),
        lines(
            "goog.module('a');",
            "const RadBehaviorImported = goog.require('rad.RadBehavior');",
            createBehaviorUsage("RadBehaviorImported")));

    assertSingleBehaviorExtractionSucceeds(moduleMetadataMap.getModulesByGoogNamespace().get("a"));
  }

  @Test
  public void testBehavior_defaultGoogRequire_ofNamedExport() {
    parseAndInitializeExtractor(
        lines(
            "goog.module('rad');",
            createBehaviorDefinition("RadBehaviorLocal"),
            "exports = {RadBehavior: RadBehaviorLocal};"),
        lines(
            "goog.module('a');",
            "const rad = goog.require('rad');",
            createBehaviorUsage("rad.RadBehavior")));

    assertSingleBehaviorExtractionSucceeds(moduleMetadataMap.getModulesByGoogNamespace().get("a"));
  }

  @Test
  public void testBehavior_destructuringGoogRequire() {
    parseAndInitializeExtractor(
        lines(
            "goog.module('rad');",
            createBehaviorDefinition("RadBehaviorLocal"),
            "exports = {RadBehavior: RadBehaviorLocal};"),
        lines(
            "goog.module('a');",
            "const {RadBehavior: RadBehaviorImported} = goog.require('rad');",
            createBehaviorUsage("RadBehaviorImported")));

    assertSingleBehaviorExtractionSucceeds(moduleMetadataMap.getModulesByGoogNamespace().get("a"));
  }

  @Test
  @Ignore // NOTE(b/137143479): we could fix this test case by improving 'aliasing' handling, but
  // at the moment it's uncommon enough to not seem worthwhile.
  public void testBehavior_propertyOnDefaultExportOfGoogModule() {
    parseAndInitializeExtractor(
        lines(
            "goog.module('rad.SomeClass');",
            "class SomeClass {}",
            createBehaviorDefinition("SomeClass.RadBehavior"),
            "exports = SomeClass;"),
        lines(
            "goog.module('a');",
            "const SomeClass = goog.require('rad.SomeClass');",
            createBehaviorUsage("SomeClass.RadBehavior")));

    assertSingleBehaviorExtractionSucceeds(moduleMetadataMap.getModulesByGoogNamespace().get("a"));
  }

  // Test goog.module.get

  @Test
  public void testBehavior_googModuleGetOfModule_DirectUsage() {
    parseAndInitializeExtractor(
        lines(
            "goog.module('rad');", //
            createBehaviorDefinition("RadBehaviorLocal"),
            "exports = RadBehaviorLocal;"),
        lines(
            "(function() {", //
            createBehaviorUsage("goog.module.get('rad')"),
            "})();"));

    ImmutableList<BehaviorDefinition> defs = extractor.extractBehaviors(behaviorArray, null);
    assertThat(compiler.getErrors()).isEmpty();
    assertThat(defs).hasSize(1);
  }

  @Test
  public void testBehavior_googModuleGetOfProvide_DirectUsage() {
    parseAndInitializeExtractor(
        lines(
            "goog.provide('rad');", //
            createBehaviorDefinition("rad")),
        lines(
            "(function() {", //
            createBehaviorUsage("goog.module.get('rad')"),
            "})();"));

    ImmutableList<BehaviorDefinition> defs = extractor.extractBehaviors(behaviorArray, null);
    assertThat(compiler.getErrors()).isEmpty();
    assertThat(defs).hasSize(1);
  }

  @Ignore
  @Test
  public void testBehavior_googModuleGetAssignedToVariable() {
    // Note: this test fails because, currently, we only support resolving variables that are either
    // global or module-scoped. In theory the PolymerPass could be extended to understand other
    // locals but it hasn't been necessary thus far.
    parseAndInitializeExtractor(
        lines(
            "goog.module('rad');",
            createBehaviorDefinition("RadBehaviorLocal"),
            "exports = RadBehaviorLocal;"),
        lines(
            "(function() {",
            "const rad = goog.module.get('rad');",
            createBehaviorUsage("rad"),
            "})();"));

    ImmutableList<BehaviorDefinition> defs = extractor.extractBehaviors(behaviorArray, null);
    assertThat(compiler.getErrors()).hasSize(1);
    assertThat(defs).isEmpty();
  }

  @Test
  public void testBehavior_invalidGoogModuleGet() {
    parseAndInitializeExtractor(
        lines(
            "(function() {",
            createBehaviorUsage("goog.module.get('rud')"), // oops, no such module.
            "})();"));

    ImmutableList<BehaviorDefinition> defs = extractor.extractBehaviors(behaviorArray, null);
    assertThat(compiler.getErrors()).hasSize(1);
    assertThat(defs).isEmpty();
  }

  private String createBehaviorDefinition(String name) {
    // Either `qualified.name` or `var simpleName`
    String lhs = name.indexOf('.') != -1 ? name : "var " + name;
    return lines(
        "/** @polymerBehavior */",
        lhs + " = {",
        "  properties: {",
        "    howRad: Number",
        "  },",
        "  /** @param {number} radAmount */",
        "  doSomethingRad:",
        "    function(radAmount) { alert('Something ' + radAmount + 'rad!'); },",
        "  /** @override */",
        "  ready: function() {}",
        "};");
  }

  private String createBehaviorUsage(String behaviorName) {
    return lines(
        "var A = Polymer({", //
        "  is: 'x-element',",
        "  behaviors: [ " + behaviorName + " ],",
        "});");
  }

  /** Tests that the behaviorArray resolves to exactly one behavior without error */
  private void assertSingleBehaviorExtractionSucceeds() {
    assertSingleBehaviorExtractionSucceeds(/* moduleMetadata */ null);
  }

  /**
   * Tests that the behaviorArray resolves to exactly one behavior, using the provided
   * ModuleMetadata for behavior extraction.
   */
  private void assertSingleBehaviorExtractionSucceeds(@Nullable ModuleMetadata metadata) {
    ImmutableList<BehaviorDefinition> defs = extractor.extractBehaviors(behaviorArray, metadata);
    assertThat(compiler.getErrors()).isEmpty();
    assertThat(defs).hasSize(1);
  }

  @Test
  public void testInlineLiteralBehavior() {
    parseAndInitializeExtractor(
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
            "  behaviors: [ SuperCoolBehaviors ],",
            "});"));

    ImmutableList<BehaviorDefinition> defs = extractor.extractBehaviors(behaviorArray, null);
    assertThat(defs).hasSize(2);

    // TODO(jlklein): Actually verify the properties of the BehaviorDefinitions.
  }

  @Test
  public void testIsPropInBehavior() {
    parseAndInitializeExtractor(
        lines(
            "/** @polymerBehavior */",
            "var FunBehavior = {",
            "  is: 'fun-behavior',",
            "",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "};",
            "var A = Polymer({",
            "  is: 'x-element',",
            "  behaviors: [ FunBehavior ],",
            "});"));
    extractor.extractBehaviors(behaviorArray, null);

    assertThat(compiler.getErrors()).hasSize(1);
    assertError(compiler.getErrors().get(0)).hasType(POLYMER_INVALID_BEHAVIOR);
  }

  // TODO(jlklein): Test more use cases: names to avoid copying, global vs. non-global, etc.

  private void parseAndInitializeExtractor(String... code) {
    Node root = compiler.parseTestCode(ImmutableList.copyOf(code));

    new GatherModuleMetadata(compiler, false, ResolutionMode.BROWSER).process(IR.root(), root);
    this.moduleMetadataMap = compiler.getModuleMetadataMap();
    new ModuleMapCreator(compiler, moduleMetadataMap).process(IR.root(), root);
    assertThat(compiler.getErrors()).isEmpty();
    assertThat(compiler.getWarnings()).isEmpty();
    GlobalNamespace globalNamespace = new GlobalNamespace(compiler, root);
    extractor =
        new PolymerBehaviorExtractor(
            compiler, globalNamespace, compiler.getModuleMetadataMap(), compiler.getModuleMap());

    NodeUtil.visitPostOrder(
        root,
        node -> {
          if (isBehaviorArrayDeclaration(node)) {
            behaviorArray = node;
          }
        });

    assertThat(behaviorArray).isNotNull();
  }

  private boolean isBehaviorArrayDeclaration(Node node) {
    return node.isArrayLit()
        && node.getParent().isStringKey()
        && node.getParent().getString().equals("behaviors");
  }
}
