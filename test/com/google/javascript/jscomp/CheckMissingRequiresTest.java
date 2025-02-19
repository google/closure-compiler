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

import static com.google.javascript.jscomp.deps.ModuleLoader.LOAD_WARNING;

import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link CheckMissingRequires}. */
@RunWith(JUnit4.class)
public final class CheckMissingRequiresTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return (externs, root) -> {
      GatherModuleMetadata gatherModuleMetadata =
          new GatherModuleMetadata(
              compiler, /* processCommonJsModules= */ false, ResolutionMode.BROWSER);
      gatherModuleMetadata.process(externs, root);
      new CheckMissingRequires(compiler, compiler.getModuleMetadataMap()).process(externs, root);
    };
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.MISSING_REQUIRE, CheckLevel.WARNING);
    return options;
  }

  @Test
  public void testNoWarning_existingRequire_withAlias() throws Exception {
    checkNoWarning(
        lines(
            "goog.module('foo.Bar');", //
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.module('test');", //
            "const Bar = goog.require('foo.Bar');",
            "let x = new Bar();"));
  }

  @Test
  public void testNoWarning_existingRequire_withDestructure() throws Exception {
    checkNoWarning(
        lines(
            "goog.module('foo.bar');", //
            "/** @constructor */",
            "exports.Baz = function() {}"),
        lines(
            "goog.module('test');",
            "const {Bar} = goog.require('foo.Bar');",
            "let x = new Bar();"));
  }

  @Test
  public void testWarning_missingRequire_inProvide() throws Exception {
    checkRequireInProvidesFileWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');",
            "goog.module.declareLegacyNamespace();",
            "/** @constructor */",
            "exports = function() {};"),
        lines(
            "goog.provide('test');", //
            "let x = new foo.Bar();"));
  }

  @Test
  public void testWarning_missingRequire_inScript() throws Exception {
    checkRequireInProvidesFileWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');",
            "goog.module.declareLegacyNamespace();",
            "/** @constructor */",
            "exports = function() {};"),
        lines(
            "goog.provide('test');", //
            "let x = new foo.Bar();"));
  }

  @Test
  public void testMissingRequire_inEsModuleExport() throws Exception {
    checkRequireWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');",
            "goog.module.declareLegacyNamespace();",
            "/** @constructor */",
            "exports = function() {};"),
        lines(
            "let x = new foo.Bar();", //
            "export default 42;"));
  }

  @Test
  public void testMissingRequire_inEsModuleImport() throws Exception {
    ignoreWarnings(LOAD_WARNING);
    checkRequireWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');",
            "goog.module.declareLegacyNamespace();",
            "/** @constructor */",
            "exports = function() {};"),
        lines(
            "import * as quux from './quux.js';", //
            "let x = new foo.Bar();"));
  }

  @Test
  public void testNoWarning_missingRequire_externs() throws Exception {
    checkNoWarning(
        lines(
            "/** @externs */", //
            "var foo = {}",
            "/** @constructor */",
            "foo.Bar = function() {};"),
        lines(
            "goog.provide('test');", //
            "let x = new foo.Bar();"));
  }

  @Test
  public void testNoWarning_missingRequire_unknown() throws Exception {
    checkNoWarning(
        lines(
            "goog.provide('test');", //
            "let x = new foo.Bar();"));
  }

  @Test
  public void testNoWarning_missingRequire_sameProvide() throws Exception {
    checkNoWarning(
        lines(
            "goog.provide('foo.Bar');", //
            "let x = new foo.Bar();"));
  }

  @Test
  public void testNoWarning_missingRequire_sameNestedProvide() throws Exception {
    checkNoWarning(
        lines("goog.provide('foo');"),
        lines(
            "goog.provide('foo.Bar');", //
            "let x = new foo.Bar();"));
  }

  @Test
  public void testWarning_missingRequire_strongRef_withRequireType() throws Exception {
    checkRequireInProvidesFileWarning(
        "a.b.C",
        lines(
            "goog.provide('a.b.C');", //
            "",
            "/** @constructor */",
            "a.b.C = function() { };"),
        lines(
            "goog.requireType('a.b.C');", //
            "",
            "new a.b.C;"));
  }

  @Test
  public void testNoWarning_missingRequire_withParentRequire_fromSameFile() throws Exception {
    checkNoWarning(
        lines(
            "goog.provide('a.b');", //
            "goog.provide('a.b.C');"),
        lines(
            "goog.require('a.b');", //
            "",
            "new a.b.C;"));
  }

  @Test
  public void testWarning_missingRequire_withSiblingRequire_fromSameFile() throws Exception {
    checkRequireInProvidesFileWarning(
        "a.b.D",
        lines(
            "goog.provide('a.b');",
            "goog.provide('a.b.C');", //
            "goog.provide('a.b.D');"),
        lines(
            "goog.require('a.b.C');", //
            "",
            "new a.b.D;"));
  }

  @Test
  public void testWarning_missingRequire_withParentRequire_fromDifferentFile() throws Exception {
    checkRequireInProvidesFileWarning(
        "a.b.C",
        lines("goog.provide('a.b');"),
        lines(
            "goog.require('a.b');", //
            "goog.provide('a.b.C');"),
        lines(
            "goog.require('a.b');", //
            "",
            "new a.b.C;"));
  }

  @Test
  public void testNoWarning_missingRequire_sameModule() throws Exception {
    checkNoWarning(
        lines(
            "goog.module('foo.Bar');", //
            "let x = new foo.Bar();"));
  }

  @Test
  public void testNoWarning_missingRequire_sameModule_nestedProvide() throws Exception {
    checkNoWarning(
        "goog.provide('foo.bar');",
        lines(
            "goog.module('foo.bar.Baz');",
            "/** @constructor */",
            "exports.Baz = function() {}",
            "let x = new foo.bar.Baz();"));
  }

  @Test
  public void testNoWarning_missingRequire_sameModuleWithLegacyNamespace_nestedProvide()
      throws Exception {
    checkNoWarning(
        "goog.provide('foo.bar');",
        lines(
            "goog.module('foo.bar.Baz');",
            "goog.module.declareLegacyNamespace();",
            "/** @constructor */",
            "exports.Baz = function() {}",
            "let x = new foo.bar.Baz();"));
  }

  @Test
  public void testNoWarning_existingRequireType_withAlias() throws Exception {
    checkNoWarning(
        lines(
            "goog.module('foo.Bar');", //
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.module('test');",
            "const Bar = goog.requireType('foo.Bar');",
            "/** @type {!Bar} */",
            "let x;"));
  }

  @Test
  public void testNoWarning_existingRequireType_withDestructure() throws Exception {
    checkNoWarning(
        lines(
            "goog.module('foo.bar');", //
            "/** @constructor */",
            "exports.Baz = function() {}"),
        lines(
            "goog.module('test');",
            "const {Bar} = goog.requireType('foo.Bar');",
            "/** @type {!Bar} */",
            "let x;"));
  }

  @Test
  public void testWarning_missingRequireType_inProvide() throws Exception {
    checkRequireTypeInProvidesFileWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');",
            "goog.module.declareLegacyNamespace();",
            "/** @constructor */",
            "exports = function() {};"),
        lines(
            "goog.provide('test');", //
            "/** @type {!foo.Bar} */",
            "let x;"));
  }

  @Test
  public void testWarning_missingRequireType_inScript() throws Exception {
    checkRequireTypeInProvidesFileWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');",
            "goog.module.declareLegacyNamespace();",
            "/** @constructor */",
            "exports = function() {};"),
        lines(
            "/** @type {!foo.Bar} */", //
            "let x;"));
  }

  @Test
  public void testMissingRequireType_inEsModuleExport() throws Exception {
    ignoreWarnings(LOAD_WARNING);
    checkRequireTypeWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');",
            "goog.module.declareLegacyNamespace();",
            "/** @constructor */",
            "exports = function() {};"),
        lines(
            "/** @type {!foo.Bar} */", //
            "let x;",
            "export default 42;"));
  }

  @Test
  public void testMissingRequireType_inEsModuleImport() throws Exception {
    ignoreWarnings(LOAD_WARNING);
    checkRequireTypeWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');",
            "goog.module.declareLegacyNamespace();",
            "/** @constructor */",
            "exports = function() {};"),
        lines(
            "import * as quux from './quux.js';", //
            "/** @type {!foo.Bar} */",
            "let x;"));
  }

  @Test
  public void testWarning_missingRequire_forProvide() throws Exception {
    checkRequireWarning(
        "foo.Bar",
        lines(
            "goog.provide('foo.Bar');", //
            "/** @constructor */",
            "foo.Bar = function() {}"),
        lines(
            "goog.module('test');", //
            "let x = new foo.Bar();"));
  }

  @Test
  public void testWarning_missingRequire_forProvide_usingProperty() throws Exception {
    checkRequireWarning(
        "foo.bar",
        lines(
            "goog.provide('foo.bar');", //
            "foo.bar = {};",
            "/** @constructor */",
            "foo.bar.Baz = function() {}"),
        lines(
            "goog.module('test');", //
            "let x = new foo.bar.Baz();"));
  }

  @Test
  public void testWarning_missingRequire_forNestedProvide() throws Exception {
    checkRequireWarning(
        "foo.Bar",
        lines("goog.provide('foo');"),
        lines(
            "goog.provide('foo.Bar');", //
            "/** @constructor */",
            "foo.Bar = function() {}"),
        lines(
            "goog.module('test');", //
            "goog.require('foo');",
            "let x = new foo.Bar();"));
  }

  @Test
  public void testWarning_missingRequire_forNestedProvide_usingProperty() throws Exception {
    checkRequireWarning(
        "foo.bar",
        lines("goog.provide('foo');"),
        lines(
            "goog.provide('foo.bar');", //
            "foo.bar = {};",
            "/** @constructor */",
            "foo.bar.Baz = function() {}"),
        lines(
            "goog.module('test');", //
            "goog.require('foo');",
            "let x = new foo.bar.Baz();"));
  }

  @Test
  public void testWarning_missingRequire_forModule() throws Exception {
    checkRequireWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');", //
            "goog.module.declareLegacyNamespace();",
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.module('test');", //
            "let x = new foo.Bar();"));
  }

  @Test
  public void testWarning_missingRequire_forModule_usingProperty() throws Exception {
    checkRequireWarning(
        "foo.bar",
        lines(
            "goog.module('foo.bar');",
            "goog.module.declareLegacyNamespace();",
            "/** @constructor */",
            "function Baz() {}",
            "exports = { Baz }"),
        lines(
            "goog.module('test');", //
            "let x = new foo.bar.Baz();"));
  }

  @Test
  public void testWarning_missingRequire_forModuleWithLegacyNamespace() throws Exception {
    checkRequireWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');",
            "goog.module.declareLegacyNamespace();",
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.module('test');", //
            "let x = new foo.Bar();"));
  }

  @Test
  public void testWarning_missingRequire_forLateModule() throws Exception {
    checkRequireWarning(
        "foo.Bar",
        lines(
            "goog.module('test');", //
            "let x = new foo.Bar();"),
        lines(
            "goog.module('foo.Bar');", //
            "/** @constructor */",
            "exports = function() {}"));
  }

  @Test
  public void testWarning_existingRequire_standalone() throws Exception {
    checkRequireWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');", //
            "goog.module.declareLegacyNamespace();",
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.module('test');", //
            "goog.require('foo.Bar');",
            "let x = new foo.Bar();"));
  }

  @Test
  public void testWarning_missingRequireType_forProvide() throws Exception {
    checkRequireTypeWarning(
        "foo.Bar",
        lines(
            "goog.provide('foo.Bar');", //
            "/** @constructor */",
            "foo.Bar = function() {}"),
        lines(
            "goog.module('test');", //
            "/** @type {!foo.Bar} */ let x;"));
  }

  @Test
  public void testWarning_missingRequireType_forProvide_usingProperty() throws Exception {
    checkRequireTypeWarning(
        "foo.bar",
        lines(
            "goog.provide('foo.bar');", //
            "foo.bar = {};",
            "/** @constructor */",
            "foo.bar.Baz = function() {}"),
        lines(
            "goog.module('test');", //
            "/** @type {!foo.bar.Baz} */ let x;"));
  }

  @Test
  public void testWarning_missingRequireType_forNestedProvide() throws Exception {
    checkRequireTypeWarning(
        "foo.Bar",
        lines("goog.provide('foo');"),
        lines(
            "goog.provide('foo.Bar');", //
            "/** @constructor */",
            "foo.Bar = function() {}"),
        lines(
            "goog.module('test');", //
            "goog.requireType('foo');",
            "/** @type {!foo.Bar} */ let x;"));
  }

  @Test
  public void testWarning_missingRequireType_forNestedProvide_usingProperty() throws Exception {
    checkRequireTypeWarning(
        "foo.bar",
        lines("goog.provide('foo');"),
        lines(
            "goog.provide('foo.bar');", //
            "foo.bar = {};",
            "/** @constructor */",
            "foo.bar.Baz = function() {}"),
        lines(
            "goog.module('test');", //
            "goog.require('foo');",
            "/** @type {!foo.bar.Baz} */ let x;"));
  }

  @Test
  public void testWarning_missingRequireType_forModule() throws Exception {
    checkRequireTypeWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');", //
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.module('test');", //
            "/** @type {!foo.Bar} */ let x;"));
  }

  @Test
  public void testWarning_missingRequireType_forModule_usingProperty() throws Exception {
    checkRequireTypeWarning(
        "foo.bar",
        lines(
            "goog.module('foo.bar');",
            "goog.module.declareLegacyNamespace();",
            "/** @constructor */",
            "function Baz() {}",
            "exports = { Baz }"),
        lines(
            "goog.module('test');", //
            "/** @type {!foo.bar.Baz} */ let x;"));
  }

  @Test
  public void testWarning_missingRequireType_forModuleWithLegacyNamespace() throws Exception {
    checkRequireTypeWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');",
            "goog.module.declareLegacyNamespace();",
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.module('test');", //
            "/** @type {!foo.Bar} */ let x;"));
  }

  @Test
  public void testWarning_missingRequireType_forLateModule() throws Exception {
    checkRequireTypeWarning(
        "foo.Bar",
        lines(
            "goog.provide('foo.Bar');", //
            "/** @type {!foo.Bar} */ let x;"),
        lines(
            "goog.module('test');", //
            "/** @type {!foo.Bar} */ let x;"));
  }

  @Test
  public void testWarning_existingRequireType_standalone() throws Exception {
    checkRequireTypeWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');", //
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.module('test');",
            "goog.requireType('foo.Bar');",
            "/** @type {!foo.Bar} */ let x;"));
  }

  @Test
  public void testNoWarning_missingRequireType_externs() throws Exception {
    checkNoWarning(
        lines(
            "/** @externs */", //
            "var foo = {}",
            "/** @constructor */",
            "foo.Bar = function() {};"),
        lines(
            "goog.provide('test');", //
            "/** @type {!foo.Bar} */",
            "let x;"));
  }

  @Test
  public void testNoWarning_missingRequireType_unknown() throws Exception {
    checkNoWarning(
        lines(
            "goog.provide('test');", //
            "/** @type {!foo.Bar} */",
            "let x;"));
  }

  @Test
  public void testNoWarning_missingRequireType_sameProvide() throws Exception {
    checkNoWarning(
        lines(
            "goog.provide('foo.Bar');", //
            "/** @type {!foo.Bar} */",
            "let x;"));
  }

  @Test
  public void testNoWarning_missingRequireType_sameNestedProvide() throws Exception {
    checkNoWarning(
        lines("goog.provide('foo');"),
        lines(
            "goog.provide('foo.Bar');", //
            "/** @type {!foo.Bar} */",
            "let x;"));
  }

  @Test
  public void testNoWarning_missingRequireType_sameModule() throws Exception {
    checkNoWarning(
        lines(
            "goog.module('foo.Bar');", //
            "/** @type {!foo.Bar} */",
            "let x;"));
  }

  @Test
  public void testNoWarning_missingRequireType_sameModule_nestedProvide() throws Exception {
    checkNoWarning(
        "goog.provide('foo.bar');",
        lines(
            "goog.module('foo.bar.Baz');",
            "/** @constructor */",
            "exports.Baz = function() {}",
            "/** @type {!foo.bar.Baz} */ let x;"));
  }

  @Test
  public void testWarning_missingRequire_nestedProvide() throws Exception {
    checkIncorrectNamespaceAliasRequireWarning(
        "foo.bar.Baz",
        "goog.provide('foo.bar');",
        "goog.provide('foo.bar.Baz');",
        lines(
            "goog.module('another');",
            "const {Baz} = goog.require('foo.bar');",
            "function ref(a) {};",
            "ref(Baz);"));
  }

  @Test
  public void testWarning_missingRequireType_nestedProvide() throws Exception {
    checkIncorrectNamespaceAliasRequireTypeWarning(
        "foo.bar.Baz",
        "goog.provide('foo.bar');",
        "goog.provide('foo.bar.Baz');foo.bar.Baz = class {};",
        lines(
            "goog.module('another');",
            "const {Baz} = goog.requireType('foo.bar');",
            "let /** !Baz */ x;"));
  }

  @Test
  public void testNoWarning_missingRequireType_nestedProvide() throws Exception {
    checkNoWarning(
        "goog.provide('foo.bar');",
        "goog.provide('foo.bar.Baz');foo.bar.Baz = class {};",
        lines(
            "goog.module('another');",
            "const {Baz} = goog.requireType('foo.bar.Baz');",
            "let /** !Baz */ x;"));
  }

  @Test
  public void testWarning_missingRequire_nestedModule() throws Exception {
    checkIncorrectNamespaceAliasRequireWarning(
        "foo.bar.Baz",
        "goog.module('foo.bar'); goog.module.declareLegacyNamespace();",
        "goog.module('foo.bar.Baz'); goog.module.declareLegacyNamespace();",
        lines(
            "goog.module('another');",
            "const {Baz} = goog.require('foo.bar');",
            "function ref(a) {};",
            "ref(Baz);"));
  }

  @Test
  public void testWarning_missingRequireType_nestedModule() throws Exception {
    checkIncorrectNamespaceAliasRequireTypeWarning(
        "foo.bar.Baz",
        "goog.module('foo.bar'); goog.module.declareLegacyNamespace();",
        "goog.module('foo.bar.Baz'); goog.module.declareLegacyNamespace(); exports = class {};",
        lines(
            "goog.module('another');",
            "const {Baz} = goog.requireType('foo.bar');",
            "let /** Baz */ x;"));
  }

  @Test
  public void testWarning_missingRequire_nestedProvideIndirectRef() throws Exception {
    checkIndirectNamespaceRefRequireWarning(
        "foo.bar.Baz",
        "goog.provide('foo.bar');",
        "goog.provide('foo.bar.Baz');",
        lines(
            "goog.module('another');",
            "const bar = goog.require('foo.bar');",
            "function ref(a) {};",
            "ref(bar.Baz);"));
  }

  @Test
  public void testWarning_missingRequireType_nestedProvideIndirectRef() throws Exception {
    checkIndirectNamespaceRefRequireTypeWarning(
        "foo.bar.Baz",
        "goog.provide('foo.bar');",
        "goog.provide('foo.bar.Baz');foo.bar.Baz = class {};",
        lines(
            "goog.module('another');",
            "const bar = goog.requireType('foo.bar');",
            "let /** bar.Baz */ x;"));
  }

  @Test
  public void testWarning_missingRequire_nestedModuleIndirectRef() throws Exception {
    checkIndirectNamespaceRefRequireWarning(
        "foo.bar.Baz",
        "goog.module('foo.bar'); goog.module.declareLegacyNamespace();",
        "goog.module('foo.bar.Baz'); goog.module.declareLegacyNamespace();",
        lines(
            "goog.module('another');",
            "const bar = goog.require('foo.bar');",
            "function ref(a) {};",
            "ref(bar.Baz);"));
  }

  @Test
  public void testWarning_missingRequireType_nestedModuleIndirectRef() throws Exception {
    checkIndirectNamespaceRefRequireTypeWarning(
        "foo.bar.Baz",
        "goog.module('foo.bar'); goog.module.declareLegacyNamespace();",
        "goog.module('foo.bar.Baz'); goog.module.declareLegacyNamespace();exports = class {};",
        lines(
            "goog.module('another');",
            "const bar = goog.requireType('foo.bar');",
            "let /** bar.Baz */ x;"));
  }

  @Test
  public void testWarning_wrongAlias_nestedModuleIndirectRef() throws Exception {
    checkIndirectNamespaceRefRequireTypeWarning(
        "foo.bar.Baz",
        "goog.module('foo.bar'); goog.module.declareLegacyNamespace();",
        "goog.module('foo.bar.Baz'); goog.module.declareLegacyNamespace();exports = class {};",
        lines(
            "goog.module('another');",
            "const bar = goog.requireType('foo.bar');",
            "const Baz = goog.requireType('foo.bar.Baz');",
            "let /** bar.Baz */ x;")); // error should be "Baz"
  }

  @Test
  public void testWarning_wrongAlias_nestedModuleIndirectRef2() throws Exception {
    checkIndirectNamespaceRefRequireWarning(
        "foo.bar.Baz",
        "goog.module('foo.bar'); goog.module.declareLegacyNamespace();",
        "goog.module('foo.bar.Baz'); goog.module.declareLegacyNamespace();exports = class {};",
        lines(
            "goog.module('another');",
            "const bar = goog.require('foo.bar');",
            "const Baz = goog.requireType('foo.bar.Baz');",
            "let x = bar.Baz;")); // error should be "Baz"
  }

  @Test
  public void testNoWarning_missingRequire_nestedDirectRef() throws Exception {
    checkNoWarning(
        "goog.module('foo.bar'); goog.module.declareLegacyNamespace();",
        "goog.module('foo.bar.Baz'); goog.module.declareLegacyNamespace();exports = class {};",
        lines(
            "goog.module('another');",
            "const Baz = goog.require('foo.bar.Baz');",
            "let x = new Baz.C();"));
  }

  @Test
  public void testNoWarningForRequireType_nestedDirectRef() throws Exception {
    checkNoWarning(
        "goog.module('foo.bar'); goog.module.declareLegacyNamespace();",
        "goog.module('foo.bar.Baz'); goog.module.declareLegacyNamespace();exports = class {};",
        lines(
            "goog.module('another');",
            "const Baz = goog.requireType('foo.bar.Baz');",
            "let /** !Baz.C */ x = null;"));
  }

  @Test
  public void testNoWarning_overlapping_module_id_and_legacy_namespace() throws Exception {
    checkNoWarning(
        "goog.module('jspb'); exports = {Message: class {}};",
        lines(
            "goog.module('jspb.Message');",
            "goog.module.declareLegacyNamespace();",
            "const {Message} = goog.require('jspb');",
            "exports = Message;"),
        lines(
            "goog.module('another');",
            "const {Message} = goog.requireType('jspb');",
            "let /** !Message */ x = null;"));
  }

  @Test
  public void testNoWarning_overlapping_legacy_namespaces() throws Exception {
    checkNoWarning(
        "goog.provide('wiz');",
        "goog.provide('wiz.controller');",
        lines(
            "goog.module('wiz.controller.idomcompatiblecontroller');",
            "goog.module.declareLegacyNamespace();",
            "class IdomCompatibleController {}",
            "exports = {IdomCompatibleController};"),
        lines(
            "goog.module('another');",
            "const SomeRandomThing = goog.require('wiz.controller.idomcompatiblecontroller');",
            "let /** !SomeRandomThing.IdomCompatibleController */ x = null;"));
  }

  @Test
  public void testNoWarning_missingRequireType_sameModuleWithLegacyNamespace_nestedProvide()
      throws Exception {
    checkNoWarning(
        "goog.provide('foo.bar');",
        lines(
            "goog.module('foo.bar.Baz');",
            "goog.module.declareLegacyNamespace();",
            "/** @constructor */",
            "exports.Baz = function() {}",
            "/** @type {!foo.bar.Baz} */ let x;"));
  }

  @Test
  public void testWarning_jsDocParam() throws Exception {
    checkRequireTypeWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');", //
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.module('test');", //
            "/** @param {!foo.Bar} x */",
            "function f(x) {}"));
  }

  @Test
  public void testWarning_jsDocThis() throws Exception {
    checkRequireTypeWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');", //
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.module('test');", //
            "/** @this {foo.Bar} */",
            "function f() {}"));
  }

  @Test
  public void testWarning_jsDocReturn() throws Exception {
    checkRequireTypeWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');", //
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.module('test');", //
            "/** @return {!foo.Bar} */",
            "function f() {}"));
  }

  @Test
  public void testWarning_jsDocEnum() throws Exception {
    checkRequireTypeWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');", //
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.module('test');", //
            "/** @enum {foo.Bar} */",
            "let x;"));
  }

  @Test
  public void testWarning_jsDocTypedef() throws Exception {
    checkRequireTypeWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');", //
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.module('test');", //
            "/** @typedef {foo.Bar} */",
            "let x;"));
  }

  @Test
  public void testWarning_jsDocExtendsClass() throws Exception {
    checkRequireWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');", //
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.module('test');",
            "/**",
            " * @constructor",
            " * @extends {foo.Bar}",
            " */",
            "function Bar() {}"));
  }

  @Test
  public void testWarning_jsDocExtendsInterface() throws Exception {
    checkRequireWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');", //
            "/** @interface */",
            "exports = function() {}"),
        lines(
            "goog.module('test');",
            "/**",
            " * @interface",
            " * @extends {foo.Bar}",
            " */",
            "function Bar() {}"));
  }

  @Test
  public void testWarning_jsDocImplements() throws Exception {
    checkRequireWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');", //
            "/** @interface */",
            "exports = function() {}"),
        lines(
            "goog.module('test');",
            "/**",
            " * @constructor",
            " * @implements {foo.Bar}",
            " */",
            "function Bar() {}"));
  }

  @Test
  public void testWarning_jsDocUnion() throws Exception {
    checkRequireTypeWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');", //
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.module('test');", //
            "/** @type {string|foo.Bar} */",
            "let x;"));
  }

  @Test
  public void testWarning_jsDocTemplate() throws Exception {
    checkRequireTypeWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');", //
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.module('test');",
            "/** @template T */",
            "class Quux {}",
            "/** @type {!Quux<!foo.Bar>} */",
            "let x;"));
  }

  @Test
  public void testWarning_jsDocRecord() throws Exception {
    checkRequireTypeWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');", //
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.module('test');", //
            "/** @type {{foo: !foo.Bar}} */",
            "let x;"));
  }

  @Test
  public void testWarning_jsDocFunctionParam() throws Exception {
    checkRequireTypeWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');", //
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.module('test');", //
            "/** @type {function(!foo.Bar)} */",
            "let x;"));
  }

  @Test
  public void testWarning_jsDocFunctionReturn() throws Exception {
    checkRequireTypeWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');", //
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.module('test');", //
            "/** @type {function():!foo.Bar} */",
            "let x;"));
  }

  @Test
  public void testWarning_jsDocFunctionNew() throws Exception {
    checkRequireTypeWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');", //
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.module('test');", //
            "/** @type {function(new:foo.Bar)} */",
            "let x;"));
  }

  @Test
  public void testWarning_jsDocFunctionThis() throws Exception {
    checkRequireTypeWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');", //
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.module('test');", //
            "/** @type {function(this:foo.Bar)} */",
            "let x;"));
  }

  @Test
  public void testWarning_jsDocTypeof() throws Exception {
    checkRequireTypeWarning(
        "foo.Bar",
        lines(
            "goog.module('foo.Bar');", //
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.module('test');", //
            "/** @type {typeof foo.Bar} */",
            "let x;"));
  }

  @Test
  public void testNoWarning_jsDocClassTemplateParam() throws Exception {
    checkNoWarning(
        lines(
            "goog.module('MyType');", //
            "goog.module.declareLegacyNamespace();"),
        lines(
            "goog.module('test');",
            "/** @template MyType */",
            "class Foo {",
            "  /** @param {!MyType} x */",
            "  constructor(x) {}",
            "}"));
  }

  @Test
  public void testNoWarning_jsDocFunctionTemplateParam() throws Exception {
    checkNoWarning(
        lines(
            "goog.module('MyType');", //
            "goog.module.declareLegacyNamespace();"),
        lines(
            "goog.module('test');",
            "/**",
            " * @param {!MyType} x",
            " * @return {!MyType}",
            " * @template MyType",
            " */",
            "function foo(x) {",
            "  return x;",
            "}"));
  }

  @Test
  public void testNoWarning_jsDocTtlParam() throws Exception {
    checkNoWarning(
        lines(
            "goog.module('MyType');", //
            "goog.module.declareLegacyNamespace();"),
        lines(
            "goog.module('YourType');", //
            "goog.module.declareLegacyNamespace();"),
        lines(
            "goog.module('test');",
            "/**",
            " * @param {!MyType} x",
            " * @return {!YourType}",
            " * @template MyType",
            " * @template YourType := MyType =:",
            " */",
            "function foo(x) {",
            "  return x;",
            "}"));
  }

  @Test
  public void testNoWarning_shadow_moduleScope() throws Exception {
    checkNoWarning(
        lines(
            "goog.module('Bar');", //
            "goog.module.declareLegacyNamespace();"),
        lines(
            "goog.module('test');", //
            "class Bar {}",
            "/** @type {!Bar} */",
            "let x = new Bar();"));
  }

  @Test
  public void testNoWarning_shadow_localScope() throws Exception {
    checkNoWarning(
        lines(
            "goog.module('Bar');", //
            "goog.module.declareLegacyNamespace();"),
        lines(
            "goog.module('test');",
            "function foo() {",
            "  class Bar {}",
            "  /** @type {!Bar} */",
            "  let x = new Bar();",
            "}"));
  }

  @Test
  public void testNoWarning_shadow_parentScope() throws Exception {
    checkNoWarning(
        lines(
            "goog.module('Bar');", //
            "goog.module.declareLegacyNamespace();"),
        lines(
            "goog.module('test');",
            "class Bar {}",
            "function foo() {",
            "  /** @type {!Bar} */",
            "  let x = new Bar();",
            "}"));
  }

  @Test
  public void testNoWarning_providedGoogModule() throws Exception {
    checkNoWarning(
        lines(
            "goog.provide('goog.module');", //
            "goog.module = goog.module || {};"),
        lines(
            "goog.module('test');", //
            "goog.module.declareLegacyNamespace();"));
  }

  @Test
  public void testWarning_providedGoogModule_missingRequireForNestedProvide() throws Exception {
    checkRequireWarning(
        "goog.module.ModuleManager",
        lines(
            "goog.provide('goog.module');", //
            "goog.module = goog.module || {};"),
        lines(
            "goog.provide('goog.module.ModuleManager');",
            "/** @constructor */",
            "goog.module.ModuleManager = function() {};"),
        lines(
            "goog.module('test');", //
            "let x = new goog.module.ModuleManager();"));
  }

  @Test
  public void testWarning_providedGoogModule_missingRequireTypeForNestedProvide() throws Exception {
    checkRequireTypeWarning(
        "goog.module.ModuleManager",
        lines(
            "goog.provide('goog.module');", //
            "goog.module = goog.module || {};"),
        lines(
            "goog.provide('goog.module.ModuleManager');",
            "/** @constructor */",
            "goog.module.ModuleManager = function() {};"),
        lines(
            "goog.module('test');", //
            "/** @type {!goog.module.ModuleManager} */",
            "let x;"));
  }

  @Test
  public void testNoCrashOnGetprops() throws Exception {
    checkNoWarning(
        lines(
            "goog.module('test');",
            "class Class {",
            "  constructor() {",
            "    foo().bar;",
            "    foo['bar'].baz;",
            "    this.foo;",
            "    super.foo;",
            "  }",
            "}"));
  }

  @Test
  public void testReferenceNonLegacyGoogModule_inScript_required_warns() {
    test(
        srcs(
            "goog.module('test.a'); exports.x = 1;",
            "goog.provide('test.b'); goog.require('test.a'); console.log(test.a.x);"),
        error(CheckMissingRequires.NON_LEGACY_GOOG_MODULE_REFERENCE));
  }

  @Test
  public void testReferenceNonLegacyGoogModule_inScript_typePosition_required_noWarning() {
    checkNoWarning(
        """
        goog.module('test.a');
        /** @interface */
        exports.C = class {};\
        """,
        """
        goog.provide('test.b');
        goog.require('test.a');
        /** @implements {test.a.C} */
        test.b.C = class {
          /** @param {!test.a.C} c */
          foo(c) {}
        };
        """);
  }

  @Test
  public void testReferenceNonLegacyGoogModule_inScript_typePosition_and_code_required_warning() {
    test(
        srcs(
            """
            goog.module('test.a');
            /** @interface */
            exports.C = class {};\
            """,
            """
            goog.provide('test.b');
            goog.require('test.a');
            /** @implements {test.a.C} */
            test.b.C = class {
              /** @param {!test.a.C} c */
              foo(c) { test.a.C; } // reports error
            };
            """),
        error(CheckMissingRequires.NON_LEGACY_GOOG_MODULE_REFERENCE));
  }

  @Test
  public void tesNonLegacyGoogModule_inScript_required_noWarning() {
    checkNoWarning(
        "goog.module('test.a'); goog.module.declareLegacyNamespace(); exports.x = 1;",
        "goog.provide('test.b'); goog.require('test.a'); console.log(test.a.x);");
  }

  @Test
  public void testWarning_googScope_googModuleGet_noRequire() {
    checkRequireForGoogModuleGetWarning(
        "foo.bar.Baz",
        lines(
            "goog.module('foo.bar.Baz');", //
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.provide('test');", //
            "",
            "goog.scope(function() {",
            "const Baz = goog.module.get('foo.bar.Baz');",
            "exports.fn = function() { new Baz(); }",
            "});"));
  }

  @Test
  public void testWarning_script_googModuleGet_inProvideInitialization_noRequire() {
    checkRequireForGoogModuleGetWarning(
        "foo.bar.Baz",
        lines(
            "goog.module('foo.bar.Baz');", //
            "/** @constructor */",
            "exports.Boo = function() {}"),
        lines(
            "goog.provide('test');", //
            "",
            "test.boo = new (goog.module.get('foo.bar.Baz').Boo)();"));
  }

  @Test
  public void testWarning_script_googModuleGet_inScriptIfBlock_noRequire() {
    checkRequireForGoogModuleGetWarning(
        "foo.bar.Baz",
        lines(
            "goog.module('foo.bar.Baz');", //
            "/** @constructor */",
            "exports.Boo = function() {}"),
        lines(
            "goog.provide('test');", //
            "",
            "if (true) { console.log(new (goog.module.get('foo.bar.Baz').Boo)()); }"));
  }

  @Test
  public void testWarning_script_googModuleGet_inScript_inTopLevelIIFE_noRequire() {
    checkRequireForGoogModuleGetWarning(
        "foo.bar.Baz",
        lines(
            "goog.module('foo.bar.Baz');", //
            "/** @constructor */",
            "exports.Boo = function() {}"),
        lines(
            "goog.provide('test');", //
            "",
            "(() => console.log(new (goog.module.get('foo.bar.Baz').Boo)()))();"));
  }

  @Test
  public void testWarning_googScope_googModuleGet_withPropAccess_noRequire() {
    checkRequireForGoogModuleGetWarning(
        "foo.bar.Baz",
        lines(
            "goog.module('foo.bar.Baz');", //
            "/** @constructor */",
            "exports.Boo = function() {}"),
        lines(
            "goog.provide('test');", //
            "",
            "goog.scope(function() {",
            "const Woo = goog.module.get('foo.bar.Baz').Boo.Goo.Woo;",
            "exports.fn = function() { new Woo(); }",
            "});"));
  }

  @Test
  public void testNoWarning_googScope_googModuleGet_hasRequire() {
    checkNoWarning(
        lines(
            "goog.module('foo.bar.Baz');", //
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.provide('test');", //
            "goog.require('foo.bar.Baz');",
            "",
            "goog.scope(function() {",
            "const Baz = goog.module.get('foo.bar.Baz');",
            "function fn() { new Baz(); }",
            "});"));
  }

  @Test
  public void testWarning_googScope_googModuleGet_IIFE_noRequire() {
    checkRequireForGoogModuleGetWarning(
        "foo.bar.Baz",
        lines(
            "goog.module('foo.bar.Baz');", //
            "/** @constructor */",
            "exports.Boo = function() {}"),
        lines(
            "goog.provide('test');", //
            "",
            "goog.scope(function() {",
            "  (() => console.log(new (goog.module.get('foo.bar.Baz').Boo)()))();",
            "});"));
  }

  @Test
  public void testWarning_script_googModuleGet_inFunctionInScript_noRequire_noWarning() {
    checkNoWarning(
        lines(
            "goog.module('foo.bar.Baz');", //
            "/** @constructor */",
            "exports.Boo = function() {}"),
        lines(
            "goog.provide('test');", //
            "",
            // We allow this goog.module.get despite the lack of goog.require: it's possible that
            // the foo.bar.Baz module will have been loaded by the time test.fn is called. It's up
            // to the caller to ensure that it's loaded.
            "test.fn = function() {",
            "  console.log(new (goog.module.get('foo.bar.Baz').Boo)());",
            "}"));
  }

  @Test
  public void
      testWarning_script_googModuleGet_inFunctionInScript_inNonTopLevelIIFE_noRequire_noWarning() {
    checkNoWarning(
        lines(
            "goog.module('foo.bar.Baz');", //
            "/** @constructor */",
            "exports.Boo = function() {}"),
        lines(
            "goog.provide('test');", //
            "",
            // We allow this goog.module.get despite the lack of goog.require: it's possible that
            // the foo.bar.Baz module will have been loaded by the time test.fn is called. It's up
            // to the caller to ensure that it's loaded.
            "test.fn = function() {",
            "  (() => console.log(new (goog.module.get('foo.bar.Baz').Boo)()))();",
            "}"));
  }

  @Test
  public void testNoWarning_nestedInGoogScope_googModuleGet_noRequire() {
    checkNoWarning(
        lines(
            "goog.module('foo.bar.Baz');", //
            "/** @constructor */",
            "exports = function() {}"),
        lines(
            "goog.provide('test');", //
            "",
            "goog.scope(function() {",
            "test.fn = function() {",
            // We allow this goog.module.get despite the lack of goog.require: it's possible that
            // the foo.bar.Baz module will have been loaded by the time test.fn is called. It's up
            // to the caller to ensure that it's loaded.
            "  const Baz = goog.module.get('foo.bar.Baz');",
            "  new Baz();",
            "  new goog.module.get('foo.bar.Baz');",
            "}",
            "});"));
  }

  private void checkNoWarning(String... js) {
    testSame(srcs(js));
  }

  private void checkRequireWarning(String namespace, String... js) {
    testSame(
        srcs(js),
        warning(CheckMissingRequires.MISSING_REQUIRE).withMessageContaining("'" + namespace + "'"));
  }

  private void checkRequireTypeWarning(String namespace, String... js) {
    testSame(
        srcs(js),
        warning(CheckMissingRequires.MISSING_REQUIRE_TYPE)
            .withMessageContaining("'" + namespace + "'"));
  }

  private void checkRequireInProvidesFileWarning(String namespace, String... js) {
    testSame(
        srcs(js),
        warning(CheckMissingRequires.MISSING_REQUIRE_IN_PROVIDES_FILE)
            .withMessageContaining("'" + namespace + "'"));
  }

  private void checkRequireTypeInProvidesFileWarning(String namespace, String... js) {
    testSame(
        srcs(js),
        warning(CheckMissingRequires.MISSING_REQUIRE_TYPE_IN_PROVIDES_FILE)
            .withMessageContaining("'" + namespace + "'"));
  }

  private void checkIndirectNamespaceRefRequireWarning(String namespace, String... js) {
    testSame(
        srcs(js),
        warning(CheckMissingRequires.INDIRECT_NAMESPACE_REF_REQUIRE)
            .withMessageContaining("'" + namespace + "'"));
  }

  private void checkIndirectNamespaceRefRequireTypeWarning(String namespace, String... js) {
    testSame(
        srcs(js),
        warning(CheckMissingRequires.INDIRECT_NAMESPACE_REF_REQUIRE_TYPE)
            .withMessageContaining("'" + namespace + "'"));
  }

  private void checkIncorrectNamespaceAliasRequireWarning(String namespace, String... js) {
    testSame(
        srcs(js),
        warning(CheckMissingRequires.INCORRECT_NAMESPACE_ALIAS_REQUIRE)
            .withMessageContaining("'" + namespace + "'"));
  }

  private void checkIncorrectNamespaceAliasRequireTypeWarning(String namespace, String... js) {
    testSame(
        srcs(js),
        warning(CheckMissingRequires.INCORRECT_NAMESPACE_ALIAS_REQUIRE_TYPE)
            .withMessageContaining("'" + namespace + "'"));
  }

  private void checkRequireForGoogModuleGetWarning(String namespace, String... js) {
    testSame(
        srcs(js),
        warning(CheckMissingRequires.MISSING_REQUIRE_FOR_GOOG_MODULE_GET)
            .withMessageContaining("'" + namespace + "'"));
  }
}
