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
    options.setWarningLevel(
        DiagnosticGroups.STRICTER_MISSING_REQUIRE_IN_PROVIDES_FILE, CheckLevel.WARNING);
    options.setWarningLevel(
        DiagnosticGroups.STRICTER_MISSING_REQUIRE_TYPE_IN_PROVIDES_FILE, CheckLevel.WARNING);
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

  private void checkNoWarning(String... js) {
    testSame(js);
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
}
