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
        createModules("var a = 1;", "a"),
        createModules("var a = 1; export {a}", "import {a} from './m0.js'; a"));
    test(
        createModules("var a = 1, b = 2, c = 3;", "a;c;"),
        createModules(
            "var a = 1, b = 2, c = 3; export {a, c};", "import {a, c} from './m0.js'; a; c;"));
    test(
        createModules("var a = 1, b = 2, c = 3;", "b;c;"),
        createModules(
            "var a = 1, b = 2, c = 3; export {b,c};", "import {b, c} from './m0.js'; b;c;"));
  }
}
