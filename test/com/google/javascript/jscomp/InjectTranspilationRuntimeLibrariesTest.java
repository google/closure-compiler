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
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.js.RuntimeJsLibManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class InjectTranspilationRuntimeLibrariesTest {

  private Compiler compiler;
  private LanguageMode languageOut;
  private boolean instrumentAsyncContext;

  @Before
  public void setup() {
    compiler = new Compiler();
    languageOut = LanguageMode.ECMASCRIPT5;
    instrumentAsyncContext = false;
  }

  /**
   * Parses the given code and runs the {@link InjectTranspilationRuntimeLibraries} pass over the
   * resulting AST
   *
   * @return the set of paths to all the injected libraries
   */
  private ImmutableSet<String> parseAndRunInjectionPass(String js) {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(this.languageOut);
    options.setInstrumentAsyncContext(instrumentAsyncContext);
    options.setRuntimeLibraryMode(RuntimeJsLibManager.RuntimeLibraryMode.RECORD_ONLY);

    compiler.init(
        ImmutableList.of(SourceFile.fromCode("externs", "")),
        ImmutableList.of(SourceFile.fromCode("testcode", js)),
        options);
    compiler.parse();
    new InjectTranspilationRuntimeLibraries(compiler)
        .process(compiler.getExternsRoot(), compiler.getJsRoot());

    return ImmutableSet.copyOf(compiler.getRuntimeJsLibManager().getInjectedLibraries());
  }

  @Test
  public void testEmptyInjected() {
    ImmutableSet<String> injected = parseAndRunInjectionPass("");

    assertThat(injected).isEmpty();
  }

  @Test
  public void testMakeIteratorAndObjectAssignInjectedForObjectPatternRest() {
    ImmutableSet<String> injected = parseAndRunInjectionPass("const {a, ...rest} = something();");
    assertThat(injected).containsExactly("es6/util/makeiterator", "es6/object/assign");
  }

  @Test
  public void testObjectAssignInjectedForObjectSpread() {
    ImmutableSet<String> injected = parseAndRunInjectionPass("const obj = {a, ...rest};");
    assertThat(injected).containsExactly("es6/object/assign");
  }

  @Test
  public void testForOf_injectsMakeIterator() {
    ImmutableSet<String> injected = parseAndRunInjectionPass("for (x of []) {}");

    assertThat(injected).containsExactly("es6/util/makeiterator");
  }

  @Test
  public void testArrayPattern_injectsMakeIterator() {
    ImmutableSet<String> injected = parseAndRunInjectionPass("var [a] = [];");

    assertThat(injected).containsExactly("es6/util/makeiterator");
  }

  @Test
  public void testObjectPattern_injectsNothing() {
    ImmutableSet<String> injected = parseAndRunInjectionPass("var {a} = {};");

    assertThat(injected).isEmpty();
  }

  @Test
  public void testArrayPatternRest_injectsArrayFromIterator() {
    ImmutableSet<String> injected = parseAndRunInjectionPass("var [...a] = [];");

    assertThat(injected).containsExactly("es6/util/makeiterator", "es6/util/arrayfromiterator");
  }

  @Test
  public void testInjectsExecuteAsyncFunctionSupport_es5Out_includesGeneratorEngine() {
    ImmutableSet<String> injected = parseAndRunInjectionPass("async function foo() {}");

    assertThat(injected).containsExactly("es6/execute_async_generator", "es6/generator_engine");
  }

  @Test
  public void testInjectsExecuteAsyncFunctionSupport_es6Out_doesNotIncludeGeneratorEngine() {
    languageOut = LanguageMode.ECMASCRIPT_2015;
    ImmutableSet<String> injected = parseAndRunInjectionPass("async function foo() {}");

    assertThat(injected).containsExactly("es6/execute_async_generator");
  }

  @Test
  public void testTaggedTemplateFirstArgCreaterInjected() {
    ImmutableSet<String> injected = parseAndRunInjectionPass("function tag(...a) {}; tag`hello`;");
    assertThat(injected)
        .containsExactly("es6/util/createtemplatetagfirstarg", "es6/util/restarguments");
  }

  @Test
  public void testClassInheritance_injectsInheritsAndConstruct() {
    ImmutableSet<String> injected = parseAndRunInjectionPass("class A {} class B extends A {}");
    assertThat(injected)
        .containsExactly("es6/util/inherits", "es6/util/construct", "es6/util/arrayfromiterable");
  }

  @Test
  public void testClass_noInheritances_doesNotInject() {
    ImmutableSet<String> injected = parseAndRunInjectionPass("class A {}");
    assertThat(injected).isEmpty();
  }
}
