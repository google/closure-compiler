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

import com.google.javascript.jscomp.testing.JSChunkGraphBuilder;
import static com.google.javascript.jscomp.ConvertChunksToESModules.ASSIGNMENT_TO_IMPORT;
import static com.google.javascript.jscomp.ConvertChunksToESModules.UNABLE_TO_COMPUTE_RELATIVE_PATH;
import org.junit.Before;
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

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void testVarDeclarations_acrossModules() {
    test(
        JSChunkGraphBuilder.forStar()
          .addChunk("var a = 1;")
          .addChunk( "a")
          .build(),
        JSChunkGraphBuilder.forStar()
          .addChunk("var a = 1; export {a}")
          .addChunk("import {a} from './m0.js'; a")
          .build());
    test(
        JSChunkGraphBuilder.forStar()
          .addChunk("var a = 1, b = 2, c = 3;")
          .addChunk("a;c;")
          .build(),
        JSChunkGraphBuilder.forStar()
          .addChunk("var a = 1, b = 2, c = 3; export {a, c};")
          .addChunk("import {a, c} from './m0.js'; a; c;")
          .build());
    test(
        JSChunkGraphBuilder.forStar()
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
    JSModule[] original = JSChunkGraphBuilder.forStar()
        .addChunk("var a = 1;")
        .addChunk( "a")
        .build();

    original[0].add(SourceFile.fromCode("m0-1", "console.log(a)"));

    JSModule[] expected = JSChunkGraphBuilder.forStar()
        .addChunk("var a = 1; console.log(a); export {a}")
        .addChunk("import {a} from './m0.js'; a")
        .build();

    expected[0].add(SourceFile.fromCode("m0-1", ""));

    test(original, expected);
  }

  @Test
  public void testImportPathReferenceAbsolute() {
    JSModule[] original = JSChunkGraphBuilder.forStar()
        .addChunk("var a = 1;")
        .addChunk( "a")
        .build();
    original[0].setName("/js/m0");
    original[1].setName("/js/m1");

    JSModule[] expected = JSChunkGraphBuilder.forStar()
        .addChunk("var a = 1; export {a}")
        .addChunk("import {a} from './m0.js'; a")
        .build();
    expected[0].setName("/js/m0");
    expected[1].setName("/js/m1");

    test(original, expected);
  }

  @Test
  public void testImportPathReferenceAbsoluteWithRelative1() {
    JSModule[] original = JSChunkGraphBuilder.forStar()
        .addChunk("var a = 1;")
        .addChunk( "a")
        .build();
    original[0].setName("other/m0");
    original[1].setName("/js/m1");

    testError(
        original,
        UNABLE_TO_COMPUTE_RELATIVE_PATH,
        "Unable to compute relative import path from \"/js/m1.js\" to \"other/m0.js\"");
  }

  @Test
  public void testImportPathReferenceAbsoluteWithRelative2() {
    JSModule[] original = JSChunkGraphBuilder.forStar()
        .addChunk("var a = 1;")
        .addChunk( "a")
        .build();
    original[0].setName("/other/m0");
    original[1].setName("js/m1");

    testError(
        original,
        UNABLE_TO_COMPUTE_RELATIVE_PATH,
        "Unable to compute relative import path from \"js/m1.js\" to \"/other/m0.js\"");
  }

  @Test
  public void testImportPathAmbiguous() {
    JSModule[] original = JSChunkGraphBuilder.forStar()
        .addChunk("var a = 1;")
        .addChunk( "a")
        .build();
    original[0].setName("js/m0");
    original[1].setName("js/m1");

    JSModule[] expected = JSChunkGraphBuilder.forStar()
        .addChunk("var a = 1; export {a}")
        .addChunk("import {a} from './m0.js'; a")
        .build();
    expected[0].setName("js/m0");
    expected[1].setName("js/m1");

    test(original, expected);
  }

  @Test
  public void testImportPathMixedDepth1() {
    JSModule[] original = JSChunkGraphBuilder.forStar()
        .addChunk("var a = 1;")
        .addChunk( "a")
        .build();
    original[0].setName("js/m0");
    original[1].setName("m1");

    JSModule[] expected = JSChunkGraphBuilder.forStar()
        .addChunk("var a = 1; export {a}")
        .addChunk("import {a} from './js/m0.js'; a")
        .build();
    expected[0].setName("js/m0");
    expected[1].setName("m1");

    test(original, expected);
  }

  @Test
  public void testImportPathMixedDepth2() {
    JSModule[] original = JSChunkGraphBuilder.forStar()
        .addChunk("var a = 1;")
        .addChunk( "a")
        .build();
    original[0].setName("m0");
    original[1].setName("js/m1");

    JSModule[] expected = JSChunkGraphBuilder.forStar()
        .addChunk("var a = 1; export {a}")
        .addChunk("import {a} from '../m0.js'; a")
        .build();
    expected[0].setName("m0");
    expected[1].setName("js/m1");

    test(original, expected);
  }

  @Test
  public void testImportPathMixedDepth3() {
    JSModule[] original = JSChunkGraphBuilder.forStar()
        .addChunk("var a = 1;")
        .addChunk( "a")
        .build();
    original[0].setName("js/other/path/one/m0");
    original[1].setName("external/path/m1");

    JSModule[] expected = JSChunkGraphBuilder.forStar()
        .addChunk("var a = 1; export {a}")
        .addChunk("import {a} from '../../js/other/path/one/m0.js'; a")
        .build();
    expected[0].setName("js/other/path/one/m0");
    expected[1].setName("external/path/m1");

    test(original, expected);
  }

  @Test
  public void testImportPathParentAboveRoot() {
    JSModule[] original = JSChunkGraphBuilder.forStar()
        .addChunk("var a = 1;")
        .addChunk( "a")
        .build();
    original[0].setName("js/m0");
    original[1].setName("../node_modules/m1");

    testError(
        original,
        UNABLE_TO_COMPUTE_RELATIVE_PATH,
        "Unable to compute relative import path from \"../node_modules/m1.js\" to \"js/m0.js\"");
  }

  @Test
  public void testForcedESModuleSemantics() {
    test(
        JSChunkGraphBuilder.forStar()
            .addChunk("var a = 1;")
            .addChunk("var b = 1;")
            .build(),
        JSChunkGraphBuilder.forStar()
            .addChunk("var a = 1; export {};")
            .addChunk("var b = 1; export {};")
            .build());
  }

  @Test
  public void testAssignToImport() {
    testError(
        JSChunkGraphBuilder.forStar()
            .addChunk("var a = 1;")
            .addChunk("a = 2;")
            .build(),
        ASSIGNMENT_TO_IMPORT,
        "Imported symbol \"a\" in chunk \"m1.js\" cannot be assigned");
  }
}
