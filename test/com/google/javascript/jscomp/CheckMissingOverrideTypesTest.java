/*
 * Copyright 2021 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.CheckMissingOverrideTypes.OVERRIDE_WITHOUT_ALL_TYPES;

import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.modules.ModuleMapCreator;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@code @CheckMissingOverrideTypes} */
@RunWith(JUnit4.class)
public final class CheckMissingOverrideTypesTest extends CompilerTestCase {

  private static final ResolutionMode MODULE_RESOLUTION_MODE = ResolutionMode.BROWSER;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeInfoValidation();
    enableFixMissingOverrideTypes();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return (Node externs, Node root) -> {
      new GatherModuleMetadata(compiler, false, MODULE_RESOLUTION_MODE).process(externs, root);
      new ModuleMapCreator(compiler, compiler.getModuleMetadataMap()).process(externs, root);
      TypedScopeCreator scopeCreator = new TypedScopeCreator(compiler);
      new TypeInferencePass(compiler, compiler.getReverseAbstractInterpreter(), scopeCreator)
          .inferAllScopes(root.getParent());
      new CheckMissingOverrideTypes(compiler).process(externs, root);
    };
  }

  // Test missing param types in JSDoc
  @Test
  public void testMissingParamTypesOnOverride_primitiveParamType() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  /** @param {string} argFoo */",
                "  method(argFoo) {}",
                "}",
                "class Bar extends Foo {",
                "  /** @override */", // missing @param {string} arg
                "  method(argBar) {",
                "  }",
                "}")),
        error(OVERRIDE_WITHOUT_ALL_TYPES)
            .withMessageContaining(
                lines(
                    "/**",
                    " * @param {string} argBar", // generates @param {string} with `argBar` name
                    " * @override",
                    " */")));
  }

  @Test
  public void testMissingParamTypesOnOverride_errorLocationPrinted() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  /** @param {string} argFoo */",
                "  method(argFoo) {}",
                "}",
                "class Bar extends Foo {",
                "  /** @override */", // missing @param {string} arg
                "  method(argBar) {",
                "  }",
                "}")),
        error(OVERRIDE_WITHOUT_ALL_TYPES)
            .withMessageContaining(
                lines(
                    "/**",
                    " * @param {string} argBar",
                    " * @override",
                    " */",
                    " for function at location testcode:7:2" // generates <filename>:<line>:<offset>
                    )));
  }

  @Test
  public void testMissingParamTypesOnOverride_multipleMissingParams() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  /**",
                "   * @param {number} arg1",
                "   * @param {boolean} arg2",
                "   */",
                "  method(arg1, arg2) {}",
                "}",
                "class Bar extends Foo {",
                "  /** @override */", // missing @param for both arg3 and arg4 names
                "  method(arg3, arg4) {",
                "  }",
                "}")),
        error(OVERRIDE_WITHOUT_ALL_TYPES)
            .withMessageContaining(
                lines(
                    "/**",
                    " * @param {number} arg3", // generates @param for both arg3 and arg4
                    " * @param {boolean} arg4",
                    " * @override",
                    " */")));
  }

  @Test
  public void testMissingParamTypesOnOverride_partiallyMissingParams() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  /**",
                "   * @param {number} arg1",
                "   * @param {boolean} arg2",
                "   */",
                "  method(arg1, arg2) {}",
                "}",
                "class Bar extends Foo {",
                "  /** ",
                "    * @param {number} arg3",
                "    * @override */", // missing @param for arg4
                "  method(arg3, arg4) {",
                "  }",
                "}")),
        error(OVERRIDE_WITHOUT_ALL_TYPES)
            .withMessageContaining(
                lines(
                    "/**",
                    " * @param {number} arg3", // generates @param for both arg3 and arg4
                    " * @param {boolean} arg4",
                    " * @override",
                    " */")));
  }

  @Test
  public void testMissingParamTypesOnOverride_objectParamType() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  /** @param {{X:(number|string)}} arg */",
                "  method(arg) {}",
                "}",
                "class Bar extends Foo {",
                "  /** @override */", // missing @param
                "  method(arg) {",
                "  }",
                "}")),
        error(OVERRIDE_WITHOUT_ALL_TYPES)
            .withMessageContaining(
                lines(
                    "/**",
                    " * @param {{X:(number|string)}} arg", // generates @param
                    " * @override",
                    " */")));
  }

  @Test
  public void testMissingParamTypesOnOverride_restParam() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  /** @param {!Array<number>} arg */",
                "  method(...arg) {}",
                "}",
                "class Bar extends Foo {",
                "  /** @override */", // missing @param
                "  method(...arg) {",
                "  }",
                "}")),
        error(OVERRIDE_WITHOUT_ALL_TYPES)
            .withMessageContaining(
                lines(
                    "/**",
                    " * @param {Array} arg", // generates @param
                    " * @override",
                    " */")));
  }

  @Test
  public void testMissingParamTypesOnOverride_enumParamType() {
    test(
        srcs(
            lines(
                "/** @enum {string} */ const enumT = { A:'a'};",
                "class Foo {",
                "  /** @param {!enumT} arg */",
                "  method(arg) {}",
                "}",
                "class Bar extends Foo {",
                "  /** @override */", // missing @param
                "  method(arg) {",
                "  }",
                "}")),
        error(OVERRIDE_WITHOUT_ALL_TYPES)
            .withMessageContaining(
                lines(
                    "/**",
                    " * @param {enumT} arg", // generates @param
                    " * @override",
                    " */")));
  }

  // Below tests check missing return types in JSDoc
  @Test
  public void testMissingReturnTypesOnOverride_primitiveReturnType() {
    test(
        srcs(
            lines(
                "class Foo {",
                "/** @return {number} */",
                "  method() { return 2; }",
                "}",
                "class Bar extends Foo {",
                "  /**",
                "    * @override", // missing @return {number}
                "    */",
                "  method() {",
                "    return 2;",
                "  }",
                "}")),
        error(OVERRIDE_WITHOUT_ALL_TYPES)
            .withMessageContaining(lines("/**", " * @return {number}", " * @override", " */")));
  }

  @Test
  public void testMissingReturnTypesOnOverride_classReturnType() {
    test(
        srcs(
            lines(
                "class RetT {};",
                "class Foo {",
                "/** @return {!RetT} */",
                "  method() { return new RetT(); }",
                "}",
                "class Bar extends Foo {",
                "  /**",
                "    * @override", // missing @return {!RetT}
                "    */",
                "  method() {",
                "    return new RetT();",
                "  }",
                "}")),
        error(OVERRIDE_WITHOUT_ALL_TYPES)
            .withMessageContaining(
                lines(
                    "/**", //
                    " * @return {RetT}",
                    " * @override",
                    " */")));
  }

  @Test
  public void testMissingReturnTypesOnOverride_enumReturnType_sameModule() {
    test(
        srcs(
            lines(
                "/** @enum {number} */ const enumT = {A:1, B:2};",
                "class Foo {",
                "  /**",
                "   * @param {!enumT} x ",
                "   * @return {!enumT}",
                "   */",
                "  method(x) { return x.A; }",
                "}",
                "class Bar extends Foo {",
                "  /**",
                "    * @param {!enumT} x", // missing @return {number}
                "    * @override",
                "    */",
                "  method(x) {",
                "    return x.A;",
                "  }",
                "}")),
        error(OVERRIDE_WITHOUT_ALL_TYPES)
            .withMessageContaining(
                lines(
                    "/**",
                    " * @param {!enumT} x",
                    " * @return {enumT}", // creates @return type
                    " * @override",
                    " */")));
  }

  // Below tests check JSDoc for missing param and return types across module imports. For these
  // test, we want to generate fully qualified names in the types.
  @Test
  public void testMissingParamTypesOnOverride_primitiveParamType_twoModules() {
    testError(
        srcs(
            lines(
                "goog.module('some.foo');",
                "class Foo {",
                "  /** @param {number} arg */",
                "  method(arg) {}",
                "}",
                "exports.Foo = Foo;"),
            lines(
                "goog.module('some.bar');",
                "const {Foo} = goog.require('some.foo');",
                "class Bar extends Foo {",
                "  /** @override */", // missing @param here
                "  method(arg) {",
                "  }",
                "}")),
        error(OVERRIDE_WITHOUT_ALL_TYPES)
            .withMessageContaining(
                lines(
                    "/**",
                    " * @param {number} arg", //
                    " * @override",
                    " */")));
  }

  @Test
  public void testMissingParamTypesOnOverride_typedefParamType_acrossModuleImportChain() {
    test(
        srcs(
            lines(
                "goog.module('a');",
                "/** @typedef {{X:number}} */",
                "let objT;",
                "exports.objT = objT;"),
            lines(
                "goog.module('b');",
                "const {objT} = goog.require('a');",
                "class Foo {",
                "  /** @param {!objT} arg */",
                "  method(arg) {}",
                "}",
                "exports.Foo = Foo;"),
            lines(
                "goog.module('c');",
                "const {Foo} = goog.require('b')",
                "class Bar extends Foo {",
                "  /** @override */",
                "  method(arg) {",
                "  }",
                "}")),
        error(OVERRIDE_WITHOUT_ALL_TYPES)
            .withMessageContaining(
                lines(
                    "/**",
                    " * @param {{X:number}} arg", // known
                    // http://b/26304488 issue which causes expansion of typedefs during print.
                    " * @override",
                    " */")));
  }

  @Test
  public void testMissingParamTypesOnOverride_typedefUsingExportedLocal_acrossModuleImportChain() {
    test(
        srcs(
            lines(
                "goog.module('a');",
                "class Local {}",
                "/** @typedef {{X:!Local}} */",
                "let objT;",
                "exports.Local = Local",
                "exports.objT = objT;"),
            lines(
                "goog.module('b');",
                "const {objT} = goog.require('a');",
                "class Foo {",
                "  /** @param {!objT} arg */",
                "  method(arg) {}",
                "}",
                "exports.Foo = Foo;"),
            lines(
                "goog.module('c');",
                "const {Foo} = goog.require('b')",
                "class Bar extends Foo {",
                "  /** @override */",
                "  method(arg) {",
                "  }",
                "}")),
        error(OVERRIDE_WITHOUT_ALL_TYPES)
            .withMessageContaining(
                lines(
                    "/**",
                    " * @param {{X:!a.Local}} arg", // generates `a.Local`; known
                    // http://b/26304488 issue which causes expansion of typedefs during print.
                    " * @override",
                    " */")));
  }

  @Test
  public void
      testMissingParamTypesOnOverride_typedefUsingUnexportedLocal_acrossModuleImportChain() {
    test(
        srcs(
            lines(
                "goog.module('a');",
                "class Local {}",
                "/** @typedef {{X:!Local}} */",
                "let objT;",
                "exports.objT = objT;"),
            lines(
                "goog.module('b');",
                "const {objT} = goog.require('a');",
                "class Foo {",
                "  /** @param {!objT} arg */",
                "  method(arg) {}",
                "}",
                "exports.Foo = Foo;"),
            lines(
                "goog.module('c');",
                "const {Foo} = goog.require('b')",
                "class Bar extends Foo {",
                "  /** @override */",
                "  method(arg) {",
                "  }",
                "}")),
        error(OVERRIDE_WITHOUT_ALL_TYPES)
            .withMessageContaining(
                lines(
                    "/**",
                    " * @param {{X:!a.Local}} arg", // generates `a.Local`; known
                    // http://b/26304488 issue which causes expansion of typedefs during print.
                    " * @override",
                    " */")));
  }

  @Test
  public void testMissingParamTypesOnOverride_classTypeParam_acrossModuleImportChain() {
    test(
        srcs(
            lines("goog.module('a');", "class objT {}", "exports.objT = objT;"),
            lines(
                "goog.module('b');",
                "const {objT} = goog.require('a');",
                "class Foo {",
                "  /** @param {!objT} arg */",
                "  method(arg) {}",
                "}",
                "exports.Foo = Foo;"),
            lines(
                "goog.module('c');",
                "const {Foo} = goog.require('b')",
                "class Bar extends Foo {",
                "  /** @override */",
                "  method(arg) {",
                "  }",
                "}")),
        error(OVERRIDE_WITHOUT_ALL_TYPES)
            .withMessageContaining(
                lines(
                    "/**",
                    " * @param {a.objT} arg", // reports fully qualified type `a.objT`
                    " * @override",
                    " */")));
  }

  @Test
  public void testMissingParamTypesOnOverride_enumTypeParam_acrossModuleImportChain() {
    test(
        srcs(
            lines(
                "goog.module('a');",
                "/** @enum {number} */ const objT = {}",
                "exports.objT = objT;"),
            lines(
                "goog.module('b');",
                "const {objT} = goog.require('a');",
                "class Foo {",
                "  /** @param {!objT} arg */",
                "  method(arg) {}",
                "}",
                "exports.Foo = Foo;"),
            lines(
                "goog.module('c');",
                "const {Foo} = goog.require('b')",
                "class Bar extends Foo {",
                "  /** @override */",
                "  method(arg) {",
                "  }",
                "}")),
        error(OVERRIDE_WITHOUT_ALL_TYPES)
            .withMessageContaining(
                lines(
                    "/**",
                    " * @param {a.objT} arg", // reports inferred enum element type.
                    " * @override",
                    " */")));
  }

  @Test
  public void testMissingParamTypesOnOverride_enumTypeParam_exportedWithDifferentName() {
    test(
        srcs(
            lines(
                "goog.module('a');",
                "/** @enum {number} */ const objT = {}",
                "exports.objTAlias = objT;"),
            lines(
                "goog.module('b');",
                "const {objTAlias} = goog.require('a');",
                "class Foo {",
                "  /** @param {!objTAlias} arg */",
                "  method(arg) {}",
                "}",
                "exports.FooAlias = Foo;"),
            lines(
                "goog.module('c');",
                "const {FooAlias} = goog.require('b')",
                "class Bar extends FooAlias {",
                "  /** @override */",
                "  method(arg) {",
                "  }",
                "}")),
        error(OVERRIDE_WITHOUT_ALL_TYPES)
            .withMessageContaining(
                lines(
                    "/**",
                    " * @param {a.objT} arg", // reports inferred enum element type even if the enum
                    // is exported with a differnt name.
                    " * @override",
                    " */")));
  }

  @Test
  public void testMissingParamTypesOnOverride_classTypeParam_exportedWithDifferentName() {
    test(
        srcs(
            lines("goog.module('a');", "class classT {}", "exports.classTAlias = classT;"),
            lines(
                "goog.module('b');",
                "const {classTAlias} = goog.require('a');",
                "class Foo {",
                "  /** @param {!classTAlias} arg */",
                "  method(arg) {}",
                "}",
                "exports.FooAlias = Foo;"),
            lines(
                "goog.module('c');",
                "const {FooAlias} = goog.require('b')",
                "class Bar extends FooAlias {",
                "  /** @override */",
                "  method(arg) {",
                "  }",
                "}")),
        error(OVERRIDE_WITHOUT_ALL_TYPES)
            .withMessageContaining(
                lines(
                    "/**",
                    " * @param {a.classT} arg", // ERROR - generates local class name classT
                    // instead of
                    // the exported class name.
                    " * @override",
                    " */")));
  }

  @Test
  public void testMissingParamTypesOnOverride_destructuringParams() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  /** @param {!Object=} x */",
                "  method({x,y} = {}) {}",
                "}",
                "class Bar extends Foo {",
                "  /** @override */", // missing @param
                "  method({p,q} = {}) {",
                "  }",
                "}")),
        error(OVERRIDE_WITHOUT_ALL_TYPES)
            .withMessageContaining(
                lines(
                    "/**",
                    " * @param {Object=} objectParam", // generates
                    // `PLACEHOLDER_OBJ_PARAM_NAME`
                    " * @override",
                    " */")));
  }

  @Test
  public void testMissingParamTypesOnOverride_destructuringParams_array() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  /** @param {!Array=} x */",
                "  method([x,y] = [1,2,3]) {}",
                "}",
                "class Bar extends Foo {",
                "  /** @override */", // missing @param
                "  method([p,q] = {}) {",
                "  }",
                "}")),
        error(OVERRIDE_WITHOUT_ALL_TYPES)
            .withMessageContaining(
                lines(
                    "/**",
                    " * @param {Array=} objectParam", // generates
                    // `PLACEHOLDER_OBJ_PARAM_NAME`
                    " * @override",
                    " */")));
  }
}
