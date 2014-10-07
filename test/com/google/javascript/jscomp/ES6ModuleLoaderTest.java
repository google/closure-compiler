/*
 * Copyright 2014 The Closure Compiler Authors.
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
import com.google.javascript.rhino.InputId;

import junit.framework.TestCase;

/**
 * Tests for {@link ES6ModuleLoader}.
 *
 * @author nicholas.j.santos@gmail.com (Nick Santos)
 */

public class ES6ModuleLoaderTest extends TestCase {
  private ES6ModuleLoader loader;
  private Compiler compiler;

  public void setUp() {
    SourceFile in1 = SourceFile.fromCode("js\\a.js", "alert('a');");
    SourceFile in2 = SourceFile.fromCode("js\\b.js", "alert('b');");
    compiler = new Compiler();
    compiler.init(
        ImmutableList.<SourceFile>of(),
        ImmutableList.of(in1, in2),
        new CompilerOptions());

    loader = ES6ModuleLoader.createNaiveLoader(compiler, ".");
  }

  public void testWindowsAddresses() {
    CompilerInput inputA = compiler.getInput(new InputId("js\\a.js"));
    CompilerInput inputB = compiler.getInput(new InputId("js\\b.js"));
    assertEquals("js/a.js", loader.getLoadAddress(inputA));
    assertEquals("js/b.js", loader.locate("./b.js", inputA));

  }
}
