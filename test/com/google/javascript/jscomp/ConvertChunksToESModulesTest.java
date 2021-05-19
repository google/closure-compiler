/*
 * Copyright 2011 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.ConvertChunksToESModules.ASSIGNMENT_TO_IMPORT;
import static com.google.javascript.jscomp.ConvertChunksToESModules.DYNAMIC_IMPORT_CALLBACK_FN;
import static com.google.javascript.jscomp.ConvertChunksToESModules.UNABLE_TO_COMPUTE_RELATIVE_PATH;
import static com.google.javascript.jscomp.ConvertChunksToESModules.UNRECOGNIZED_DYNAMIC_IMPORT_CALLBACK;
import static com.google.javascript.jscomp.deps.ModuleLoader.LOAD_WARNING;

import com.google.javascript.jscomp.testing.JSChunkGraphBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ConvertChunksToESModules} */
@RunWith(JUnit4.class)
public final class ConvertChunksToESModulesTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ConvertChunksToESModules(compiler);
  }

  @Test
  public void testVarDeclarations_acrossModules() {
    ignoreWarnings(LOAD_WARNING);
    test(
        JSChunkGraphBuilder.forStar() //
            .addChunk("var a = 1;")
            .addChunk("a")
            .build(),
        JSChunkGraphBuilder.forStar() //
            .addChunk("var a = 1; export {a}")
            .addChunk("import {a} from './m0.js'; a")
            .build());
    test(
        JSChunkGraphBuilder.forStar() //
            .addChunk("var a = 1, b = 2, c = 3;")
            .addChunk("a;c;")
            .build(),
        JSChunkGraphBuilder.forStar() //
            .addChunk("var a = 1, b = 2, c = 3; export {a, c};")
            .addChunk("import {a, c} from './m0.js'; a; c;")
            .build());
    test(
        JSChunkGraphBuilder.forStar() //
            .addChunk("var a = 1, b = 2, c = 3;")
            .addChunk("b;c;")
            .build(),
        JSChunkGraphBuilder.forStar()
            .addChunk("var a = 1, b = 2, c = 3; export {b,c};")
            .addChunk("import {b, c} from './m0.js'; b;c;")
            .build());
  }

  @Test
  public void testMultipleInputsPerChunk() {
    ignoreWarnings(LOAD_WARNING);
    JSChunk[] original =
        JSChunkGraphBuilder.forStar() //
            .addChunk("var a = 1;")
            .addChunk("a")
            .build();

    original[0].add(SourceFile.fromCode("m0-1", "console.log(a)"));

    JSChunk[] expected =
        JSChunkGraphBuilder.forStar() //
            .addChunk("var a = 1; console.log(a); export {a}")
            .addChunk("import {a} from './m0.js'; a")
            .build();

    expected[0].add(SourceFile.fromCode("m0-1", ""));

    test(original, expected);
  }

  @Test
  public void testImportPathReferenceAbsolute() {
    ignoreWarnings(LOAD_WARNING);
    JSChunk[] original =
        JSChunkGraphBuilder.forStar() //
            .addChunkWithName("var a = 1;", "/js/m0")
            .addChunkWithName("a", "/js/m1")
            .build();

    JSChunk[] expected =
        JSChunkGraphBuilder.forStar() //
            .addChunkWithName("var a = 1; export {a}", "/js/m0")
            .addChunkWithName("import {a} from './m0.js'; a", "/js/m1")
            .build();

    test(original, expected);
  }

  @Test
  public void testImportPathReferenceAbsoluteWithRelative1() {
    ignoreWarnings(LOAD_WARNING);
    JSChunk[] original =
        JSChunkGraphBuilder.forStar() //
            .addChunkWithName("var a = 1;", "other/m0")
            .addChunkWithName("a", "/js/m1")
            .build();

    testError(
        original,
        UNABLE_TO_COMPUTE_RELATIVE_PATH,
        "Unable to compute relative import path from \"/js/m1.js\" to \"other/m0.js\"");
  }

  @Test
  public void testImportPathReferenceAbsoluteWithRelative2() {
    ignoreWarnings(LOAD_WARNING);
    JSChunk[] original =
        JSChunkGraphBuilder.forStar() //
            .addChunkWithName("var a = 1;", "/other/m0")
            .addChunkWithName("a", "js/m1")
            .build();

    testError(
        original,
        UNABLE_TO_COMPUTE_RELATIVE_PATH,
        "Unable to compute relative import path from \"js/m1.js\" to \"/other/m0.js\"");
  }

  @Test
  public void testImportPathAmbiguous() {
    ignoreWarnings(LOAD_WARNING);
    JSChunk[] original =
        JSChunkGraphBuilder.forStar() //
            .addChunkWithName("var a = 1;", "js/m0")
            .addChunkWithName("a", "js/m1")
            .build();

    JSChunk[] expected =
        JSChunkGraphBuilder.forStar() //
            .addChunkWithName("var a = 1; export {a}", "js/m0")
            .addChunkWithName("import {a} from './m0.js'; a", "js/m1")
            .build();

    test(original, expected);
  }

  @Test
  public void testImportPathMixedDepth1() {
    ignoreWarnings(LOAD_WARNING);
    JSChunk[] original =
        JSChunkGraphBuilder.forStar() //
            .addChunkWithName("var a = 1;", "js/m0")
            .addChunkWithName("a", "m1")
            .build();

    JSChunk[] expected =
        JSChunkGraphBuilder.forStar() //
            .addChunkWithName("var a = 1; export {a}", "js/m0")
            .addChunkWithName("import {a} from './js/m0.js'; a", "m1")
            .build();

    testError(
        original,
        UNABLE_TO_COMPUTE_RELATIVE_PATH,
        "Unable to compute relative import path from \"m1.js\" to \"js/m0.js\"");
  }

  @Test
  public void testImportPathMixedDepth2() {
    ignoreWarnings(LOAD_WARNING);
    JSChunk[] original =
        JSChunkGraphBuilder.forStar() //
            .addChunkWithName("var a = 1;", "m0")
            .addChunkWithName("a", "js/m1")
            .build();

    JSChunk[] expected =
        JSChunkGraphBuilder.forStar() //
            .addChunkWithName("var a = 1; export {a}", "m0")
            .addChunkWithName("import {a} from '../m0.js'; a", "js/m1")
            .build();

    test(original, expected);
  }

  @Test
  public void testImportPathMixedDepth3() {
    ignoreWarnings(LOAD_WARNING);
    JSChunk[] original =
        JSChunkGraphBuilder.forStar() //
            .addChunkWithName("var a = 1;", "js/other/path/one/m0")
            .addChunkWithName("a", "external/path/m1")
            .build();

    JSChunk[] expected =
        JSChunkGraphBuilder.forStar() //
            .addChunkWithName("var a = 1; export {a}", "js/other/path/one/m0")
            .addChunkWithName(
                "import {a} from '../../js/other/path/one/m0.js'; a", "external/path/m1")
            .build();

    test(original, expected);
  }

  @Test
  public void testImportPathParentAboveRoot() {
    JSChunk[] original =
        JSChunkGraphBuilder.forStar() //
            .addChunkWithName("var a = 1;", "js/m0")
            .addChunkWithName("a", "../node_modules/m1")
            .build();

    testError(
        original,
        UNABLE_TO_COMPUTE_RELATIVE_PATH,
        "Unable to compute relative import path from \"../node_modules/m1.js\" to \"js/m0.js\"");
  }

  @Test
  public void testForcedESModuleSemantics() {
    ignoreWarnings(LOAD_WARNING);
    test(
        JSChunkGraphBuilder.forStar() //
            .addChunk("var a = 1;")
            .addChunk("var b = 1;")
            .build(),
        JSChunkGraphBuilder.forStar()
            .addChunk("var a = 1; export {};")
            .addChunk("import './m0.js'; var b = 1;")
            .build());
  }

  @Test
  public void testAssignToImport() {
    testError(
        JSChunkGraphBuilder.forStar() //
            .addChunk("var a = 1;")
            .addChunk("a = 2;")
            .build(),
        ASSIGNMENT_TO_IMPORT,
        "Imported symbol \"a\" in chunk \"m1.js\" cannot be assigned");
  }

  @Test
  public void testChunkDependenciesCreateImportStatementsForSideEffects() {
    ignoreWarnings(LOAD_WARNING);
    test(
        JSChunkGraphBuilder.forStar() //
            .addChunk("window.a = true")
            .addChunk("console.log(window.a) // should be true")
            .build(),
        JSChunkGraphBuilder.forStar()
            .addChunk("window.a = true; export {};")
            .addChunk("import './m0.js'; console.log(window.a);")
            .build());
  }

  @Test
  public void testDynamicImportRewriting1() {
    ignoreWarnings(LOAD_WARNING);
    test(
        JSChunkGraphBuilder.forStar() //
            .addChunk(
                lines(
                    "import('./m1.js')",
                    "    .then("
                        + DYNAMIC_IMPORT_CALLBACK_FN
                        + "(function () { return module$i1; }))",
                    "    .then(function (ns) { console.log(ns.default); });"))
            .addChunk(
                lines(
                    "const a$$module$i1 = 1;",
                    "var $jscompDefaultExport$$module$i1 = a$$module$i1;",
                    "/** @const */ var module$i1 = {};",
                    "/** @const */ module$i1.default = $jscompDefaultExport$$module$i1;"))
            .build(),
        JSChunkGraphBuilder.forStar()
            .addChunk(
                lines(
                    "import('./m1.js')",
                    "    .then(function ($) { return $.module$i1; })",
                    "    .then(function (ns) { console.log(ns.default); });",
                    "export {};"))
            .addChunk(
                lines(
                    "import './m0.js';",
                    "const a$$module$i1 = 1;",
                    "var $jscompDefaultExport$$module$i1 = a$$module$i1;",
                    "/** @const */ var module$i1 = {};",
                    "/** @const */ module$i1.default = $jscompDefaultExport$$module$i1;",
                    "export {module$i1};"))
            .build());
  }

  @Test
  public void testDynamicImportRewriting2() {
    ignoreWarnings(LOAD_WARNING);
    test(
        JSChunkGraphBuilder.forStar() //
            .addChunk(
                lines(
                    "const a$$module$i0 = 1;",
                    "var $jscompDefaultExport$$module$i0 = a$$module$i0;",
                    "/** @const */ var module$i0 = {};",
                    "/** @const */ module$i0.default = $jscompDefaultExport$$module$i0;"))
            .addChunk(
                lines(
                    "import('./m0.js')",
                    "    .then(" + DYNAMIC_IMPORT_CALLBACK_FN + "(() => module$i0))",
                    "    .then((ns) => console.log(ns.default));"))
            .build(),
        JSChunkGraphBuilder.forStar()
            .addChunk(
                lines(
                    "const a$$module$i0 = 1;",
                    "var $jscompDefaultExport$$module$i0 = a$$module$i0;",
                    "/** @const */ var module$i0 = {};",
                    "/** @const */ module$i0.default = $jscompDefaultExport$$module$i0;",
                    "export {module$i0};"))
            .addChunk(
                lines(
                    "import './m0.js';",
                    "import('./m0.js')",
                    "    .then(($) => $.module$i0)",
                    "    .then((ns) => console.log(ns.default));"))
            .build());
  }

  @Test
  public void testDynamicImportWithoutCallback() {
    ignoreWarnings(LOAD_WARNING);
    test(
        JSChunkGraphBuilder.forStar() //
            .addChunk(
                lines(
                    "var a$$module$i0 = 1;",
                    "var $jscompDefaultExport$$module$i0 = a$$module$i0;",
                    "/** @const */ var module$i0 = {};",
                    "/** @const */ module$i0.default = $jscompDefaultExport$$module$i0;"))
            .addChunk(lines("import('./m0.js')", "    .then((ns) => console.log(ns.default));"))
            .build(),
        JSChunkGraphBuilder.forStar()
            .addChunk(
                lines(
                    "var a$$module$i0 = 1;",
                    "var $jscompDefaultExport$$module$i0 = a$$module$i0;",
                    "/** @const */ var module$i0 = {};",
                    "/** @const */ module$i0.default = $jscompDefaultExport$$module$i0;",
                    "export {};"))
            .addChunk(
                lines(
                    "import './m0.js';",
                    "import('./m0.js')",
                    "    .then((ns) => console.log(ns.default));"))
            .build());
  }

  @Test
  public void testDynamicImportRewritingErrors1() {
    ignoreWarnings(LOAD_WARNING);
    testError(
        JSChunkGraphBuilder.forStar() //
            .addChunk(
                lines(
                    "var a$$module$i0 = 1;",
                    "var $jscompDefaultExport$$module$i0 = a$$module$i0;",
                    "/** @const */ var module$i0 = {};",
                    "/** @const */ module$i0.default = $jscompDefaultExport$$module$i0;"))
            .addChunk(
                lines(
                    "import('./m0.js')",
                    "    .then(" + DYNAMIC_IMPORT_CALLBACK_FN + "(() => {}))",
                    "    .then((ns) => console.log(ns.default));"))
            .build(),
        UNRECOGNIZED_DYNAMIC_IMPORT_CALLBACK,
        "Dynamic import callback encountered wih an invalid format. "
            + "Unable to find valid namespace reference.");
  }

  @Test
  public void testDynamicImportRewritingErrors2() {
    ignoreWarnings(LOAD_WARNING);
    testError(
        JSChunkGraphBuilder.forStar() //
            .addChunk(
                lines(
                    "var a$$module$i0 = 1;",
                    "var $jscompDefaultExport$$module$i0 = a$$module$i0;",
                    "/** @const */ var module$i0 = {};",
                    "/** @const */ module$i0.default = $jscompDefaultExport$$module$i0;"))
            .addChunk(
                lines(
                    "import('./m0.js')",
                    "    .then(" + DYNAMIC_IMPORT_CALLBACK_FN + "(true))",
                    "    .then((ns) => console.log(ns.default));"))
            .build(),
        UNRECOGNIZED_DYNAMIC_IMPORT_CALLBACK,
        "Dynamic import callback encountered wih an invalid format. "
            + "Unable to find valid callback function.");
  }
}
