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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.RewriteGoogJsImports.GOOG_JS_IMPORT_MUST_BE_GOOG_STAR;
import static com.google.javascript.jscomp.RewriteGoogJsImports.GOOG_JS_REEXPORTED;
import static com.google.javascript.jscomp.deps.ModuleLoader.LOAD_WARNING;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.RewriteGoogJsImports.Mode;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import com.google.javascript.jscomp.modules.ModuleMapCreator;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link RewriteGoogJsImports} that involve rewriting. {@link CheckGoogJsImportTest} has
 * the link tests.
 */

@RunWith(JUnit4.class)
public final class RewriteGoogJsImportsTest extends CompilerTestCase {
  // JsFileRegexParser determines if this file is base.js by looking at the first comment of the
  // file.
  private static final SourceFile BASE =
      SourceFile.fromCode("/closure/base.js", "/** @provideGoog */");

  private static final SourceFile GOOG =
      SourceFile.fromCode(
          "/closure/goog.js",
          lines(
              "export const require = goog.require;",
              "export function foo() {}",
              "export class MyClass {}",
              "export const constant = 0;"));

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return (Node externs, Node root) -> {
      GatherModuleMetadata gmm =
          new GatherModuleMetadata(
              compiler, /* processCommonJsModules= */ false, ResolutionMode.BROWSER);
      gmm.process(externs, root);
      ModuleMapCreator mmc = new ModuleMapCreator(compiler, compiler.getModuleMetadataMap());
      mmc.process(externs, root);
      new RewriteGoogJsImports(compiler, Mode.LINT_AND_REWRITE, compiler.getModuleMap())
          .process(externs, root);
    };
  }

  @Test
  public void testBaseAndGoogUntouched() {
    testSame(srcs(BASE, GOOG));
  }

  @Test
  public void testIfCannotDetectGoogJsThenGlobalizesAll() {
    SourceFile testcode =
        SourceFile.fromCode(
            "testcode", "import * as goog from './closure/goog.js'; use(goog.bad);");

    SourceFile expected =
        SourceFile.fromCode("testcode", "import './closure/goog.js'; use(goog.bad);");

    ignoreWarnings(LOAD_WARNING);

    // No base.js = no detecting goog.js
    test(srcs(GOOG, testcode), expected(GOOG, expected));

    // No goog.js
    test(srcs(BASE, testcode), expected(BASE, expected));
    test(srcs(testcode), expected(expected));

    // Linting still happens.
    testError("import * as notgoog from './goog.js';", GOOG_JS_IMPORT_MUST_BE_GOOG_STAR);
  }

  @Test
  public void testImportStar() {
    test(
        srcs(
            BASE,
            GOOG,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "import * as goog from './closure/goog.js';",
                    "use(goog.require, goog.foo, goog.MyClass, goog.constant);"))),
        expected(
            BASE,
            GOOG,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "import './closure/goog.js';",
                    "use(goog.require, goog.foo, goog.MyClass, goog.constant);"))));
    assertThat(
            getLastCompiler()
                .getModuleMap()
                .getModule(getLastCompiler().getModuleLoader().resolve("testcode"))
                .boundNames()
                .keySet())
        .isEmpty();
  }

  @Test
  public void testGoogAndBaseInExterns() {
    ignoreWarnings(LOAD_WARNING);

    test(
        externs(BASE, GOOG),
        srcs(
            SourceFile.fromCode(
                "testcode",
                lines(
                    "import * as goog from './closure/goog.js';",
                    "use(goog.require, goog.foo, goog.MyClass, goog.constant);"))),
        expected(
            SourceFile.fromCode(
                "testcode",
                lines(
                    "import './closure/goog.js';",
                    "use(goog.require, goog.foo, goog.MyClass, goog.constant);"))));
  }

  @Test
  public void testKnownBadPropertyAccess() {
    test(
        srcs(
            BASE,
            GOOG,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "import * as goog from './closure/goog.js';", "use(goog.require, goog.bad);"))),
        expected(
            BASE,
            GOOG,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "import * as $goog from './closure/goog.js';",
                    "use(goog.require, $goog.bad);"))));
    assertThat(
            getLastCompiler()
                .getModuleMap()
                .getModule(getLastCompiler().getModuleLoader().resolve("testcode"))
                .boundNames()
                .keySet())
        .containsExactly("$goog");
  }

  @Test
  public void testReexportGoog() {
    testError(
        ImmutableList.of(
            BASE,
            GOOG,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "import * as goog from './closure/goog.js';", //
                    "export {goog};"))),
        GOOG_JS_REEXPORTED);

    testError(
        ImmutableList.of(
            BASE,
            GOOG,
            SourceFile.fromCode(
                "testcode",
                lines(
                    "import * as goog from './closure/goog.js';", //
                    "export default goog;"))),
        GOOG_JS_REEXPORTED);

    testError(
        ImmutableList.of(
            BASE, GOOG, SourceFile.fromCode("testcode", "export * from './closure/goog.js';")),
        GOOG_JS_REEXPORTED);

    testError(
        ImmutableList.of(
            BASE,
            GOOG,
            SourceFile.fromCode("testcode", "export {require} from './closure/goog.js';")),
        GOOG_JS_REEXPORTED);
  }
}
