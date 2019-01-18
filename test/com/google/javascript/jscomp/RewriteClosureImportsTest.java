/*
 * Copyright 2018 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleType;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link RewriteClosureImports}. */
@RunWith(JUnit4.class)
public class RewriteClosureImportsTest extends CompilerTestCase {

  private static final SourceFile PROVIDE =
      SourceFile.fromCode("provide.js", "goog.provide('some.provided.namespace');");

  private static final SourceFile GOOG_MODULE =
      SourceFile.fromCode("googmodule.js", "goog.module('some.module.namespace');");

  private static final SourceFile LEGACY_GOOG_MODULE =
      SourceFile.fromCode(
          "legacygoogmodule.js",
          lines(
              "goog.module('some.legacy.namespace');", //
              "goog.module.declareLegacyNamespace();"));

  private static final SourceFile ES_MODULE =
      SourceFile.fromCode(
          "esm.js",
          lines(
              "goog.declareModuleId('some.es.id');", //
              "export {};"));

  private static final ImmutableSet<ModuleType> NO_MODULES = ImmutableSet.of();
  private static final ImmutableSet<ModuleType> ALL_MODULES =
      ImmutableSet.copyOf(ModuleType.values());
  private ImmutableSet<ModuleType> typesToRewriteIn = NO_MODULES;

  @Before
  public void rewriteInAllModules() {
    typesToRewriteIn = ALL_MODULES;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return (externs, root) -> {
      // Populates the module metadata map on the compiler.
      new GatherModuleMetadata(
              compiler,
              compiler.getOptions().processCommonJSModules,
              compiler.getOptions().getModuleResolutionMode())
          .process(externs, root);

      new RewriteClosureImports(
              compiler,
              compiler.getModuleMetadataMap(),
              /* preprocessorSymbolTable= */ null,
              typesToRewriteIn)
          .process(externs, root);
    };
  }

  private SourceFile replaceInSource(SourceFile sourceFile, String toReplace, String replacement) {
    try {
      return SourceFile.fromCode(
          sourceFile.getName(), sourceFile.getCode().replace(toReplace, replacement));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private TestPart replaceInPart(TestPart testPart, String toReplace, String replacement) {
    if (testPart instanceof FlatSources) {
      FlatSources fs = (FlatSources) testPart;
      return srcs(
          fs.sources.stream()
              .map(s -> replaceInSource(s, toReplace, replacement))
              .collect(ImmutableList.toImmutableList()));
    } else if (testPart instanceof Expected) {
      Expected e = (Expected) testPart;
      return expected(
          e.expected.stream()
              .map(s -> replaceInSource(s, toReplace, replacement))
              .collect(ImmutableList.toImmutableList()));
    } else {
      return testPart;
    }
  }

  /** A test common to goog.require, goog.forwardDeclare, and goog.requireType. */
  private void testCommonCase(TestPart... parts) {
    test(
        Arrays.stream(parts)
            .map(p -> replaceInPart(p, "<import>", "goog.require"))
            .toArray(TestPart[]::new));
    test(
        Arrays.stream(parts)
            .map(p -> replaceInPart(p, "<import>", "goog.forwardDeclare"))
            .toArray(TestPart[]::new));
    test(
        Arrays.stream(parts)
            .map(p -> replaceInPart(p, "<import>", "goog.requireType"))
            .toArray(TestPart[]::new));
  }

  private TestPart[] same(SourceFile... sources) {
    return new TestPart[] {srcs(sources), expected(sources)};
  }

  @Test
  public void launchSetGuardsRewriting() {
    typesToRewriteIn = NO_MODULES;

    testCommonCase(
        same(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.provide('test.provide');", //
                    "<import>('some.provided.namespace');",
                    "/** @type {!some.provided.namespace.x} */",
                    "test.provide = some.provided.namespace.x;"))));

    testCommonCase(
        same(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "const a = <import>('some.provided.namespace');", //
                    "let /** !a.x */ b = a.x;"))));

    testCommonCase(
        same(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "const a = <import>('some.provided.namespace');", //
                    "export let /** !a.x */ b = a.x;"))));
  }

  // goog.module.get tests

  @Test
  public void moduleGetProvide() {
    test(
        srcs(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines("const x = goog.module.get('some.provided.namespace');", "use(x);"))),
        expected(
            PROVIDE,
            SourceFile.fromCode(
                "testcode", lines("const x = some.provided.namespace;", "use(x);"))));
  }

  @Test
  public void moduleGetGoogModule() {
    test(
        srcs(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines("const x = goog.module.get('some.module.namespace');", "use(x);"))),
        expected(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode", lines("const x = module$exports$some$module$namespace;", "use(x);"))));
  }

  @Test
  public void moduleGetLegacyGoogModule() {
    test(
        srcs(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines("const x = goog.module.get('some.legacy.namespace');", "use(x);"))),
        expected(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode("testcode", lines("const x = some.legacy.namespace;", "use(x);"))));
  }

  @Test
  public void moduleGetEsModule() {
    test(
        srcs(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode", lines("const x = goog.module.get('some.es.id');", "use(x);"))),
        expected(
            ES_MODULE, SourceFile.fromCode("testcode", lines("const x = module$esm;", "use(x);"))));
  }

  @Test
  public void moduleGetFillInForwardDeclareForProvide() {
    test(
        srcs(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "let x = goog.forwardDeclare('some.provided.namespace');",
                    "before(x);",
                    "x = goog.module.get('some.provided.namespace');",
                    "after(x);"))),
        expected(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    // Yeah, this isn't technically correct (x should not be inlined in the argument
                    // to before), but it helps keep the logic simple in the rewriter.
                    "before(some.provided.namespace);", //
                    "after(some.provided.namespace);"))));
  }

  @Test
  public void moduleGetFillInForwardDeclareForGoogModule() {
    test(
        srcs(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "let x = goog.forwardDeclare('some.module.namespace');",
                    "before(x);",
                    "x = goog.module.get('some.module.namespace');",
                    "after(x);"))),
        expected(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    // Yeah, this isn't technically correct (x should not be inlined in the argument
                    // to before), but it helps keep the logic simple in the rewriter.
                    "before(module$exports$some$module$namespace);",
                    "after(module$exports$some$module$namespace);"))));
  }

  @Test
  public void moduleGetFillInForwardDeclareForLegacyGoogModule() {
    test(
        srcs(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "let x = goog.forwardDeclare('some.legacy.namespace');",
                    "before(x);",
                    "x = goog.module.get('some.legacy.namespace');",
                    "after(x);"))),
        expected(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    // Yeah, this isn't technically correct (x should not be inlined in the argument
                    // to before), but it helps keep the logic simple in the rewriter.
                    "before(some.legacy.namespace);", //
                    "after(some.legacy.namespace);"))));
  }

  @Test
  public void moduleGetFillInForwardDeclareForEsModule() {
    test(
        srcs(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "let x = goog.forwardDeclare('some.es.id');",
                    "before(x);",
                    "x = goog.module.get('some.es.id');",
                    "after(x);"))),
        expected(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    // Yeah, this isn't technically correct (x should not be inlined in the argument
                    // to before), but it helps keep the logic simple in the rewriter.
                    "let x = module$esm;", //
                    "before(x);",
                    "after(x);"))));
  }

  // Forward declare tests

  @Test
  public void forwardDeclareMissingNamespaceAssumesGlobal() {
    test(
        srcs(
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.forwardDeclare('MyGlobal');", //
                    "let /** !MyGlobal */ ix"))),
        expected(SourceFile.fromCode("testcode", "let /** !MyGlobal */ ix;")));
  }

  // Import goog.provide tests

  @Test
  public void importForProvideInMoocherIsDetached() {
    testCommonCase(
        srcs(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "<import>('some.provided.namespace');",
                    "use(some.provided.namespace, some.provided.namespace.export);",
                    "let /** !some.provided.namespace */ n;",
                    "let /** !some.provided.namespace.export */ x;"))),
        expected(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "use(some.provided.namespace, some.provided.namespace.export);",
                    "let /** !some.provided.namespace */ n;",
                    "let /** !some.provided.namespace.export */ x;"))));
  }

  @Test
  public void importForProvideInProvideIsDetached() {
    testCommonCase(
        srcs(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.provide('test');",
                    "<import>('some.provided.namespace');",
                    "use(some.provided.namespace, some.provided.namespace.export);",
                    "let /** !some.provided.namespace */ n;",
                    "let /** !some.provided.namespace.export */ x;"))),
        expected(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.provide('test');",
                    "use(some.provided.namespace, some.provided.namespace.export);",
                    "let /** !some.provided.namespace */ n;",
                    "let /** !some.provided.namespace.export */ x;"))));
  }

  @Test
  public void importForProvideInGoogModuleIsInlined() {
    testCommonCase(
        srcs(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "const alias = <import>('some.provided.namespace');",
                    "use(alias, alias.export);",
                    "let /** !alias */ n;",
                    "let /** !alias.export */ x;"))),
        expected(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "use(some.provided.namespace, some.provided.namespace.export);",
                    "let /** !some.provided.namespace */ n;",
                    "let /** !some.provided.namespace.export */ x;"))));
  }

  @Test
  public void importForProvideInGoogLoadModuleIsInlined() {
    testCommonCase(
        srcs(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  const alias = <import>('some.provided.namespace');",
                    "  use(alias, alias.export);",
                    "  let /** !alias */ n;",
                    "  let /** !alias.export */ x;",
                    "});",
                    "let /** !alias */ outside;"))),
        expected(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  use(some.provided.namespace, some.provided.namespace.export);",
                    "  let /** !some.provided.namespace */ n;",
                    "  let /** !some.provided.namespace.export */ x;",
                    "});",
                    "let /** !alias */ outside;"))));
  }

  @Test
  public void destructuredImportForProvideInGoogModuleIsInlined() {
    testCommonCase(
        srcs(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "const {x} = <import>('some.provided.namespace');",
                    "use(x, x.y);",
                    "let /** !x */ n;",
                    "let /** !x.y */ z;"))),
        expected(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "use(some.provided.namespace.x, some.provided.namespace.x.y);",
                    "let /** !some.provided.namespace.x */ n;",
                    "let /** !some.provided.namespace.x.y */ z;"))));

    testCommonCase(
        srcs(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "const {x: a} = <import>('some.provided.namespace');",
                    "use(a, a.b);",
                    "let /** !a */ n;",
                    "let /** !a.b */ x;"))),
        expected(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "use(some.provided.namespace.x, some.provided.namespace.x.b);",
                    "let /** !some.provided.namespace.x */ n;",
                    "let /** !some.provided.namespace.x.b */ x;"))));
  }

  @Test
  public void destructuredImportForProvideInGoogLoadModuleIsInlined() {
    testCommonCase(
        srcs(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  const {x} = <import>('some.provided.namespace');",
                    "  use(x, x.y);",
                    "  let /** !x */ n;",
                    "  let /** !x.y */ z;",
                    "});",
                    "let /** !x */ outside;"))),
        expected(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  use(some.provided.namespace.x, some.provided.namespace.x.y);",
                    "  let /** !some.provided.namespace.x */ n;",
                    "  let /** !some.provided.namespace.x.y */ z;",
                    "});",
                    "let /** !x */ outside;"))));

    testCommonCase(
        srcs(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  const {x: a} = <import>('some.provided.namespace');",
                    "  use(a, a.b);",
                    "  let /** !a */ n;",
                    "  let /** !a.b */ x;",
                    "});",
                    "let /** !a */ outside;"))),
        expected(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  use(some.provided.namespace.x, some.provided.namespace.x.b);",
                    "  let /** !some.provided.namespace.x */ n;",
                    "  let /** !some.provided.namespace.x.b */ x;",
                    "});",
                    "let /** !a */ outside;"))));
  }

  @Test
  public void importForProvideInEsModuleIsInlined() {
    testCommonCase(
        srcs(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "const alias = <import>('some.provided.namespace');",
                    "use(alias, alias.export);",
                    "export let /** !alias */ n;",
                    "export let /** !alias.export */ x;"))),
        expected(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "use(some.provided.namespace, some.provided.namespace.export);",
                    "export let /** !some.provided.namespace */ n;",
                    "export let /** !some.provided.namespace.export */ x;"))));
  }

  @Test
  public void destructuredImportForProvideInEsModuleIsInlined() {
    testCommonCase(
        srcs(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "const {x} = <import>('some.provided.namespace');",
                    "use(x, x.y);",
                    "export let /** !x */ n;",
                    "export let /** !x.y */ z;"))),
        expected(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "use(some.provided.namespace.x, some.provided.namespace.x.y);",
                    "export let /** !some.provided.namespace.x */ n;",
                    "export let /** !some.provided.namespace.x.y */ z;"))));

    testCommonCase(
        srcs(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "const {x: a} = <import>('some.provided.namespace');",
                    "use(a, a.b);",
                    "export let /** !a */ n;",
                    "export let /** !a.b */ x;"))),
        expected(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "use(some.provided.namespace.x, some.provided.namespace.x.b);",
                    "export let /** !some.provided.namespace.x */ n;",
                    "export let /** !some.provided.namespace.x.b */ x;"))));
  }

  // Import legacy goog.module tests

  @Test
  public void importForLegacyModuleInMoocherIsDetached() {
    testCommonCase(
        srcs(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "<import>('some.legacy.namespace');",
                    "use(some.legacy.namespace, some.legacy.namespace.export);",
                    "let /** !some.legacy.namespace */ n;",
                    "let /** !some.legacy.namespace.export */ x;"))),
        expected(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "use(some.legacy.namespace, some.legacy.namespace.export);",
                    "let /** !some.legacy.namespace */ n;",
                    "let /** !some.legacy.namespace.export */ x;"))));
  }

  @Test
  public void importForLegacyModuleInProvideIsDetached() {
    testCommonCase(
        srcs(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.provide('test');",
                    "<import>('some.legacy.namespace');",
                    "use(some.legacy.namespace, some.legacy.namespace.export);",
                    "let /** !some.legacy.namespace */ n;",
                    "let /** !some.legacy.namespace.export */ x;"))),
        expected(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.provide('test');",
                    "use(some.legacy.namespace, some.legacy.namespace.export);",
                    "let /** !some.legacy.namespace */ n;",
                    "let /** !some.legacy.namespace.export */ x;"))));
  }

  @Test
  public void importForLegacyModuleInGoogModuleIsInlined() {
    testCommonCase(
        srcs(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "const alias = <import>('some.legacy.namespace');",
                    "use(alias, alias.export);",
                    "let /** !alias */ n;",
                    "let /** !alias.export */ x;"))),
        expected(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "use(some.legacy.namespace, some.legacy.namespace.export);",
                    "let /** !some.legacy.namespace */ n;",
                    "let /** !some.legacy.namespace.export */ x;"))));
  }

  @Test
  public void importForLegacyModuleInGoogLoadModuleIsInlined() {
    testCommonCase(
        srcs(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  const alias = <import>('some.legacy.namespace');",
                    "  use(alias, alias.export);",
                    "  let /** !alias */ n;",
                    "  let /** !alias.export */ x;",
                    "});",
                    "let /** !alias */ outside;"))),
        expected(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  use(some.legacy.namespace, some.legacy.namespace.export);",
                    "  let /** !some.legacy.namespace */ n;",
                    "  let /** !some.legacy.namespace.export */ x;",
                    "});",
                    "let /** !alias */ outside;"))));
  }

  @Test
  public void destructuredImportForLegacyModuleInGoogModuleIsInlined() {
    testCommonCase(
        srcs(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "const {x} = <import>('some.legacy.namespace');",
                    "use(x, x.y);",
                    "let /** !x */ n;",
                    "let /** !x.y */ z;"))),
        expected(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "use(some.legacy.namespace.x, some.legacy.namespace.x.y);",
                    "let /** !some.legacy.namespace.x */ n;",
                    "let /** !some.legacy.namespace.x.y */ z;"))));

    testCommonCase(
        srcs(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "const {x: a} = <import>('some.legacy.namespace');",
                    "use(a, a.b);",
                    "let /** !a */ n;",
                    "let /** !a.b */ x;"))),
        expected(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "use(some.legacy.namespace.x, some.legacy.namespace.x.b);",
                    "let /** !some.legacy.namespace.x */ n;",
                    "let /** !some.legacy.namespace.x.b */ x;"))));
  }

  @Test
  public void destructuredImportForLegacyModuleInGoogLoadModuleIsInlined() {
    testCommonCase(
        srcs(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  const {x} = <import>('some.legacy.namespace');",
                    "  use(x, x.y);",
                    "  let /** !x */ n;",
                    "  let /** !x.y */ z;",
                    "});",
                    "let /** !x */ outside;"))),
        expected(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  use(some.legacy.namespace.x, some.legacy.namespace.x.y);",
                    "  let /** !some.legacy.namespace.x */ n;",
                    "  let /** !some.legacy.namespace.x.y */ z;",
                    "});",
                    "let /** !x */ outside;"))));

    testCommonCase(
        srcs(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  const {x: a} = <import>('some.legacy.namespace');",
                    "  use(a, a.b);",
                    "  let /** !a */ n;",
                    "  let /** !a.b */ x;",
                    "});",
                    "let /** !a */ outside;"))),
        expected(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  use(some.legacy.namespace.x, some.legacy.namespace.x.b);",
                    "  let /** !some.legacy.namespace.x */ n;",
                    "  let /** !some.legacy.namespace.x.b */ x;",
                    "});",
                    "let /** !a */ outside;"))));
  }

  @Test
  public void importForLegacyModuleInEsModuleIsInlined() {
    testCommonCase(
        srcs(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "const alias = <import>('some.legacy.namespace');",
                    "use(alias, alias.export);",
                    "export let /** !alias */ n;",
                    "export let /** !alias.export */ x;"))),
        expected(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "use(some.legacy.namespace, some.legacy.namespace.export);",
                    "export let /** !some.legacy.namespace */ n;",
                    "export let /** !some.legacy.namespace.export */ x;"))));
  }

  @Test
  public void destructuredImportForLegacyModuleInEsModuleIsInlined() {
    testCommonCase(
        srcs(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "const {x} = <import>('some.legacy.namespace');",
                    "use(x, x.y);",
                    "export let /** !x */ n;",
                    "export let /** !x.y */ z;"))),
        expected(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "use(some.legacy.namespace.x, some.legacy.namespace.x.y);",
                    "export let /** !some.legacy.namespace.x */ n;",
                    "export let /** !some.legacy.namespace.x.y */ z;"))));

    testCommonCase(
        srcs(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "const {x: a} = <import>('some.legacy.namespace');",
                    "use(a, a.b);",
                    "export let /** !a */ n;",
                    "export let /** !a.b */ x;"))),
        expected(
            LEGACY_GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "use(some.legacy.namespace.x, some.legacy.namespace.x.b);",
                    "export let /** !some.legacy.namespace.x */ n;",
                    "export let /** !some.legacy.namespace.x.b */ x;"))));
  }

  // Import goog.module tests

  @Test
  public void importForGoogModuleInMoocherIsDetached() {
    testCommonCase(
        srcs(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "<import>('some.module.namespace');",
                    "let /** !some.module.namespace */ n;",
                    "let /** !some.module.namespace.export */ x;"))),
        expected(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "let /** !module$exports$some$module$namespace */ n;",
                    "let /** !module$exports$some$module$namespace.export */ x;"))));
  }

  @Test
  public void importGoogModuleInProvideIsDetached() {
    testCommonCase(
        srcs(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.provide('test');",
                    "<import>('some.module.namespace');",
                    "let /** !some.module.namespace */ n;",
                    "let /** !some.module.namespace.export */ x;"))),
        expected(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.provide('test');",
                    "let /** !module$exports$some$module$namespace */ n;",
                    "let /** !module$exports$some$module$namespace.export */ x;"))));
  }

  @Test
  public void importGoogModuleInGoogModuleIsInlined() {
    testCommonCase(
        srcs(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "const alias = <import>('some.module.namespace');",
                    "use(alias, alias.export);",
                    "let /** !alias */ n;",
                    "let /** !alias.export */ x;"))),
        expected(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "use(module$exports$some$module$namespace,",
                    "  module$exports$some$module$namespace.export);",
                    "let /** !module$exports$some$module$namespace */ n;",
                    "let /** !module$exports$some$module$namespace.export */ x;"))));
  }

  @Test
  public void importGoogModuleInGoogLoadModuleIsInlined() {
    testCommonCase(
        srcs(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  const alias = <import>('some.module.namespace');",
                    "  use(alias, alias.export);",
                    "  let /** !alias */ n;",
                    "  let /** !alias.export */ x;",
                    "});",
                    "let /** !alias */ x;"))),
        expected(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  use(module$exports$some$module$namespace,",
                    "    module$exports$some$module$namespace.export);",
                    "  let /** !module$exports$some$module$namespace */ n;",
                    "  let /** !module$exports$some$module$namespace.export */ x;",
                    "});",
                    "let /** !alias */ x;"))));
  }

  @Test
  public void destructuredImportGoogModuleInGoogModuleIsInlined() {
    testCommonCase(
        srcs(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "const {x} = <import>('some.module.namespace');",
                    "use(x, x.y);",
                    "let /** !x */ n;",
                    "let /** !x.y */ z;"))),
        expected(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "use(module$exports$some$module$namespace.x,",
                    "  module$exports$some$module$namespace.x.y);",
                    "let /** !module$exports$some$module$namespace.x */ n;",
                    "let /** !module$exports$some$module$namespace.x.y */ z;"))));

    testCommonCase(
        srcs(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "const {x: a} = <import>('some.module.namespace');",
                    "use(a, a.b);",
                    "let /** !a */ n;",
                    "let /** !a.b */ x;"))),
        expected(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "use(module$exports$some$module$namespace.x,",
                    "  module$exports$some$module$namespace.x.b);",
                    "let /** !module$exports$some$module$namespace.x */ n;",
                    "let /** !module$exports$some$module$namespace.x.b */ x;"))));
  }

  @Test
  public void destructuredImportGoogModuleInGoogLoadModuleIsInlined() {
    testCommonCase(
        srcs(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  const {x} = <import>('some.module.namespace');",
                    "  use(x, x.y);",
                    "  let /** !x */ n;",
                    "  let /** !x.y */ z;",
                    "});",
                    "let /** !x */ outside;"))),
        expected(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  use(module$exports$some$module$namespace.x,",
                    "    module$exports$some$module$namespace.x.y);",
                    "  let /** !module$exports$some$module$namespace.x */ n;",
                    "  let /** !module$exports$some$module$namespace.x.y */ z;",
                    "});",
                    "let /** !x */ outside;"))));

    testCommonCase(
        srcs(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  const {x: a} = <import>('some.module.namespace');",
                    "  use(a, a.b);",
                    "  let /** !a */ n;",
                    "  let /** !a.b */ x;",
                    "});",
                    "let /** !a */ outside;"))),
        expected(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  use(module$exports$some$module$namespace.x,",
                    "    module$exports$some$module$namespace.x.b);",
                    "  let /** !module$exports$some$module$namespace.x */ n;",
                    "  let /** !module$exports$some$module$namespace.x.b */ x;",
                    "});",
                    "let /** !a */ outside;"))));
  }

  @Test
  public void importGoogModuleInEsModuleIsInlined() {
    testCommonCase(
        srcs(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "const alias = <import>('some.module.namespace');",
                    "use(alias, alias.export);",
                    "export let /** !alias */ n;",
                    "export let /** !alias.export */ x;"))),
        expected(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "use(module$exports$some$module$namespace,",
                    "  module$exports$some$module$namespace.export);",
                    "export let /** !module$exports$some$module$namespace */ n;",
                    "export let /** !module$exports$some$module$namespace.export */ x;"))));
  }

  @Test
  public void destructuredImportGoogModuleInEsModuleIsInlined() {
    testCommonCase(
        srcs(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "const {x} = <import>('some.module.namespace');",
                    "use(x, x.y);",
                    "export let /** !x */ n;",
                    "export let /** !x.y */ z;"))),
        expected(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "use(module$exports$some$module$namespace.x,",
                    "  module$exports$some$module$namespace.x.y);",
                    "export let /** !module$exports$some$module$namespace.x */ n;",
                    "export let /** !module$exports$some$module$namespace.x.y */ z;"))));

    testCommonCase(
        srcs(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "const {x: a} = <import>('some.module.namespace');",
                    "use(a, a.b);",
                    "export let /** !a */ n;",
                    "export let /** !a.b */ x;"))),
        expected(
            GOOG_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "use(module$exports$some$module$namespace.x,",
                    "  module$exports$some$module$namespace.x.b);",
                    "export let /** !module$exports$some$module$namespace.x */ n;",
                    "export let /** !module$exports$some$module$namespace.x.b */ x;"))));
  }

  // Import ES module tests

  @Test
  public void importForEsModuleInMoocherIsDetached() {
    testCommonCase(
        srcs(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "<import>('some.es.id');",
                    "let /** !some.es.id */ n;",
                    "let /** !some.es.id.export */ x;"))),
        expected(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines("let /** !module$esm*/ n;", "let /** !module$esm.export */ x;"))));
  }

  @Test
  public void importEsModuleInProvideIsDetached() {
    testCommonCase(
        srcs(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.provide('test');",
                    "<import>('some.es.id');",
                    "let /** !some.es.id */ n;",
                    "let /** !some.es.id.export */ x;"))),
        expected(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.provide('test');",
                    "let /** !module$esm */ n;",
                    "let /** !module$esm.export */ x;"))));
  }

  @Test
  public void importEsModuleInGoogModuleIsNotInlined() {
    testCommonCase(
        srcs(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "const alias = <import>('some.es.id');",
                    "use(alias, alias.export);",
                    "let /** !alias */ n;",
                    "let /** !alias.export */ x;"))),
        expected(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "const alias = module$esm",
                    "use(alias, alias.export);",
                    "let /** !alias */ n;",
                    "let /** !alias.export */ x;"))));
  }

  @Test
  public void importEsModuleInGoogLoadModuleIsNotInlined() {
    testCommonCase(
        srcs(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  const alias = <import>('some.es.id');",
                    "  use(alias, alias.export);",
                    "  let /** !alias */ n;",
                    "  let /** !alias.export */ x;",
                    "});",
                    "let /** !alias */ x;"))),
        expected(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  const alias = module$esm;",
                    "  use(alias, alias.export);",
                    "  let /** !alias */ n;",
                    "  let /** !alias.export */ x;",
                    "});",
                    "let /** !alias */ x;"))));
  }

  @Test
  public void destructuredImportEsModuleInGoogModuleIsNotInlined() {
    testCommonCase(
        srcs(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "const {x} = <import>('some.es.id');",
                    "use(x, x.y);",
                    "let /** !x */ n;",
                    "let /** !x.y */ z;"))),
        expected(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "const {x} = module$esm;",
                    "use(x, x.y);",
                    "let /** !x */ n;",
                    "let /** !x.y */ z;"))));

    testCommonCase(
        srcs(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "const {x: a} = <import>('some.es.id');",
                    "use(a, a.b);",
                    "let /** !a */ n;",
                    "let /** !a.b */ x;"))),
        expected(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('test');",
                    "const {x: a} = module$esm",
                    "use(a, a.b);",
                    "let /** !a */ n;",
                    "let /** !a.b */ x;"))));
  }

  @Test
  public void destructuredImportEsModuleInGoogLoadModuleIsNotInlined() {
    testCommonCase(
        srcs(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  const {x} = <import>('some.es.id');",
                    "  use(x, x.y);",
                    "  let /** !x */ n;",
                    "  let /** !x.y */ z;",
                    "});",
                    "let /** !x */ outside;"))),
        expected(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  const {x} = module$esm;",
                    "  use(x, x.y);",
                    "  let /** !x */ n;",
                    "  let /** !x.y */ z;",
                    "});",
                    "let /** !x */ outside;"))));

    testCommonCase(
        srcs(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  const {x: a} = <import>('some.es.id');",
                    "  use(a, a.b);",
                    "  let /** !a */ n;",
                    "  let /** !a.b */ x;",
                    "});",
                    "let /** !a */ outside;"))),
        expected(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.loadModule(function(exports) {",
                    "  goog.module('test');",
                    "  const {x: a} = module$esm;",
                    "  use(a, a.b);",
                    "  let /** !a */ n;",
                    "  let /** !a.b */ x;",
                    "});",
                    "let /** !a */ outside;"))));
  }

  @Test
  public void importEsModuleInEsModuleIsNotInlined() {
    testCommonCase(
        srcs(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "const alias = <import>('some.es.id');",
                    "use(alias, alias.export);",
                    "export let /** !alias */ n;",
                    "export let /** !alias.export */ x;"))),
        expected(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "const alias = module$esm;",
                    "use(alias, alias.export);",
                    "export let /** !alias */ n;",
                    "export let /** !alias.export */ x;"))));
  }

  @Test
  public void destructuredImportEsModuleInEsModuleIsNotInlined() {
    testCommonCase(
        srcs(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "const {x} = <import>('some.es.id');",
                    "use(x, x.y);",
                    "export let /** !x */ n;",
                    "export let /** !x.y */ z;"))),
        expected(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "const {x} = module$esm;",
                    "use(x, x.y);",
                    "export let /** !x */ n;",
                    "export let /** !x.y */ z;"))));

    testCommonCase(
        srcs(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "const {x: a} = <import>('some.es.id');",
                    "use(a, a.b);",
                    "export let /** !a */ n;",
                    "export let /** !a.b */ x;"))),
        expected(
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "const {x: a} = module$esm;",
                    "use(a, a.b);",
                    "export let /** !a */ n;",
                    "export let /** !a.b */ x;"))));
  }

  @Test
  public void googRequireInEsModuleReexported() {
    test(
        srcs(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "const x = goog.require('some.provided.namespace');", //
                    "let a, b, c;", //
                    "export {a, b as d, x as y, c};",
                    "use(x);"))),
        expected(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "let a, b, c;", //
                    "export {a, b as d, c};",
                    "export const y = some.provided.namespace;",
                    "use(some.provided.namespace);"))));
  }

  // TODO(johnplaisted): These tests should pass. However, most of the logic in this class is copied
  // from ClosureRewriteModules, which apparently also has this bug today. Fixing this would require
  // reworking the pass to first scan for Closure imports, rewrite references to them, and then
  // detach them. We could probably reuse the Import class / model in the modules package. But for
  // now just acknowledge this broken.
  // @Test
  // public void correctAnnotationIsRenamed() {
  //   test(
  //       srcs(
  //           PROVIDE,
  //           SourceFile.fromCode(
  //               "testcode",
  //               lines(
  //                   "const {Type} = goog.require('some.provided.namespace');",
  //                   "export let /** !Type */ value;",
  //                   "function foo() {",
  //                   "  class Type {}",
  //                   "  let /** !Type */ value;",
  //                   "}"))),
  //       expected(
  //           PROVIDE,
  //           SourceFile.fromCode(
  //               "testcode",
  //               lines(
  //                   "export let /** !some.provided.namespace.Type */ value;",
  //                   "function foo() {",
  //                   "  class Type {}",
  //                   "  let /** !Type */ value;",
  //                   "}"))));
  // }
  //
  // @Test
  // public void testGoogRequireTypeCorrectAnnotationIsRenamed() {
  //   test(
  //       srcs(
  //           PROVIDE,
  //           SourceFile.fromCode(
  //               "testcode",
  //               lines(
  //                   "const {Type} = goog.requireType('closure.provide');",
  //                   "export let /** !Type */ value;",
  //                   "function foo() {",
  //                   "  class Type {}",
  //                   "  let /** !Type */ value;",
  //                   "}"))),
  //       expected(
  //           PROVIDE,
  //           SourceFile.fromCode(
  //               "testcode",
  //               lines(
  //                   "let /** !closure.provide.Type */ value$$module$testcode;",
  //                   "function foo$$module$testcode() {",
  //                   "  class Type {}",
  //                   "  let /** !Type */ value;",
  //                   "}",
  //                   "/** @const */ var module$testcode={};",
  //                   "/** @const */ module$testcode.value = value$$module$testcode;"))));
  // }

  @Test
  public void assumeMissingRequireIsProvide() {
    test(
        srcs(
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('foo');", //
                    "const x = goog.require('does.not.exist');",
                    "use(x);"))),
        expected(
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('foo');", //
                    "use(does.not.exist);"))));
    test(
        srcs(
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('foo');", //
                    "const {X} = goog.require('does.not.exist');",
                    "let /** !X */ x;"))),
        expected(
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('foo');", //
                    "let /** !does.not.exist.X */ x;"))));
  }

  @Test
  public void rewriteTypesAndReferencesInInnerScope() {
    test(
        srcs(
            PROVIDE,
            GOOG_MODULE,
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('p.C');", //
                    "const A = goog.require('some.provided.namespace');",
                    "const B = goog.require('some.module.namespace');",
                    "const {C} = goog.require('some.es.id');",
                    "function main() {",
                    "  /** @type {A} */ const a = new A;",
                    "  /** @type {B} */ const b = new B;",
                    "  /** @type {C} */ const c = new C;",
                    "}"))),
        expected(
            PROVIDE,
            GOOG_MODULE,
            ES_MODULE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('p.C');", //
                    "const {C} = module$esm;",
                    "function main() {",
                    "  /** @type {some.provided.namespace} */",
                    "  const a = new some.provided.namespace;",
                    "  /** @type {module$exports$some$module$namespace} */",
                    "  const b = new module$exports$some$module$namespace;",
                    "  /** @type {C} */ const c = new C;",
                    "}"))));
  }

  @Test
  public void rewriteIsScopeAware() {
    test(
        srcs(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('p.C');", //
                    "const A = goog.require('some.provided.namespace');",
                    "/** @type {A} */ const a = new A;",
                    "function main() {",
                    "  class A {}",
                    "  /** @type {A} */ const a = new A;",
                    "}"))),
        expected(
            PROVIDE,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "goog.module('p.C');", //
                    "/** @type {some.provided.namespace} */",
                    "const a = new some.provided.namespace;",
                    "function main() {",
                    "  class A {}",
                    "  /** @type {A} */ const a = new A;",
                    "}"))));
  }
}
