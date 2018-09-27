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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.CompilerTestCase.NoninjectingCompiler;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class Es6InjectRuntimeLibrariesTest {

  /**
   * Parses the given code and runs the Es6InjectRuntimeLibraries pass over the resulting AST
   *
   * @return the set of paths to all the injected libraries
   */
  private Set<String> parseAndRunInjectionPass(String js) {
    NoninjectingCompiler compiler = new NoninjectingCompiler();
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);

    compiler.init(
        ImmutableList.of(SourceFile.fromCode("externs", "")),
        ImmutableList.of(SourceFile.fromCode("testcode", js)),
        options);
    compiler.parseInputs();
    new Es6InjectRuntimeLibraries(compiler)
        .process(compiler.getExternsRoot(), compiler.getJsRoot());

    return compiler.injected;
  }

  @Test
  public void testEmptyInjected() {
    Set<String> injected = parseAndRunInjectionPass("");

    assertThat(injected).isEmpty();
  }

  @Test
  public void testForOf_injectsMakeIterator() {
    Set<String> injected = parseAndRunInjectionPass("for (x of []) {}");

    assertThat(injected).containsExactly("es6/util/makeiterator");
  }

  @Test
  public void testArrayPattern_injectsMakeIterator() {
    Set<String> injected = parseAndRunInjectionPass("var [a] = [];");

    assertThat(injected).containsExactly("es6/util/makeiterator");
  }

  @Test
  public void testObjectPattern_injectsNothing() {
    Set<String> injected = parseAndRunInjectionPass("var {a} = {};");

    assertThat(injected).isEmpty();
  }

  @Test
  public void testArrayPatternRest_injectsArrayFromIterator() {
    Set<String> injected = parseAndRunInjectionPass("var [...a] = [];");

    assertThat(injected).containsExactly("es6/util/makeiterator", "es6/util/arrayfromiterator");
  }
}
