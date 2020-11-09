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

import com.google.javascript.jscomp.testing.JSChunkGraphBuilder;
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
}
