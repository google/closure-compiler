/*
 * Copyright 2025 The Closure Compiler Authors.
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
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NestedCompilerRunner.Mode;
import com.google.javascript.jscomp.parsing.parser.FeatureSet;
import com.google.javascript.jscomp.testing.JSCompCorrespondences;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link NestedCompilerRunner} */
@RunWith(JUnit4.class)
public class NestedCompilerRunnerTest {

  private Compiler original;
  private CompilerOptions options;

  @Before
  public void setup() {
    this.original = new Compiler();
    this.options = new CompilerOptions();
  }

  @Test
  public void emptyInputListSucceeds() {
    original.init(ImmutableList.of(), ImmutableList.of(), options);

    NestedCompilerRunner.create(
            original, options, Mode.TRANSPILE_AND_OPTIMIZE, new DefaultPassConfig(options))
        .compile();

    verifyCompilationSucceeded();
  }

  @Test
  public void singleEmptyInputSucceeds() {
    original.init(ImmutableList.of(), ImmutableList.of(), options);
    Node script = createScript("");

    NestedCompilerRunner.create(
            original, options, Mode.TRANSPILE_AND_OPTIMIZE, new DefaultPassConfig(options))
        .addScript(script, "test")
        .compile();

    verifyCompilationSucceeded();
    assertNode(script).isEqualTo(parse(""));
  }

  @Test
  public void canRunPeepholeOptimizations() {
    options.setFoldConstants(true);
    original.init(ImmutableList.of(), ImmutableList.of(), options);

    Node script = createScript("console.log(1 + 2)");

    NestedCompilerRunner.create(
            original, options, Mode.TRANSPILE_AND_OPTIMIZE, new DefaultPassConfig(options))
        .addScript(script, "test")
        .compile();

    verifyCompilationSucceeded();
    assertNode(script).isEqualTo(parse("console.log(3)"));
  }

  @Test
  public void delegatesWarningsAndErrorsToOriginalCompiler() {
    options.setComputeFunctionSideEffects(true);
    options.setWarningLevel(DiagnosticGroups.CHECK_VARIABLES, CheckLevel.OFF);
    options.setWarningLevel(
        DiagnosticGroups.ARTIFICIAL_FUNCTION_PURITY_VALIDATION, CheckLevel.ERROR);
    original.init(ImmutableList.of(), ImmutableList.of(), options);

    Node script =
        createScript(
            """
            class A {
              /** @nosideeffects */
              error() { throw new Error('A'); }
            }
            class B {
              error() { throw new Error('B'); }
            }
            console.log((Math.random() < 0.5 ? new A() : new B()).error());
            """);

    NestedCompilerRunner.create(
            original, options, Mode.TRANSPILE_AND_OPTIMIZE, new DefaultPassConfig(options))
        .addScript(script, "test")
        .compile();

    assertThat(original.getWarnings()).isEmpty();
    assertThat(original.getErrors())
        .comparingElementsUsing(JSCompCorrespondences.DIAGNOSTIC_EQUALITY)
        .containsExactly(PureFunctionIdentifier.UNUSED_ARTIFICIAL_PURE_ANNOTATION);
  }

  @Test
  public void canDeleteUnusedVariables() {
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    original.init(ImmutableList.of(), ImmutableList.of(), options);
    options.setWarningLevel(DiagnosticGroups.CHECK_VARIABLES, CheckLevel.OFF);
    options.setRemoveUnusedVariables(CompilerOptions.Reach.ALL);

    Node script =
        createScript(
            """
            const used = 0;
            const unused = 1;
            console.log(used);
            """);

    NestedCompilerRunner.create(
            original, options, Mode.TRANSPILE_AND_OPTIMIZE, new DefaultPassConfig(options))
        .addScript(script, "test")
        .compile();

    verifyCompilationSucceeded();
    assertNode(script).isEqualTo(parse("const used = 0; console.log(used);"));
  }

  @Test
  public void transpiles_exponentialOperatorToES2015() {
    options.setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT_2015);
    options.setPreventLibraryInjection(true);
    original.init(ImmutableList.of(), ImmutableList.of(), options);

    // Use the exponential operator as a very trivial transpilation test case.
    Node script = createScript("const x = 3 ** 4;");

    NestedCompilerRunner.create(
            original, options, Mode.TRANSPILE_AND_OPTIMIZE, new DefaultPassConfig(options))
        .addScript(script, "test")
        .compile();

    verifyCompilationSucceeded();
    assertNode(script).isEqualTo(parse("const x = Math.pow(3, 4);"));
  }

  @Test
  public void transpiles_exponentialOperatorToES2016_inTranspileOnlyMode() {
    options.setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT_2015);
    options.setPreventLibraryInjection(true);
    original.init(ImmutableList.of(), ImmutableList.of(), options);

    // Use the exponential operator as a very trivial transpilation test case.
    Node script = createScript("const x = 3 ** 4;");

    NestedCompilerRunner.create(
            original, options, Mode.TRANSPILE_ONLY, new DefaultPassConfig(options))
        .addScript(script, "test")
        .compile();

    verifyCompilationSucceeded();
    assertNode(script).isEqualTo(parse("const x = Math.pow(3, 4);"));
  }

  @Test
  public void doesNotMangleOriginalExternProperties() {
    options.setPropertyRenaming(PropertyRenamingPolicy.ALL_UNQUOTED);
    original.setExternProperties(ImmutableSet.of("external"));
    original.init(ImmutableList.of(), ImmutableList.of(), options);

    Node script = createScript("alert({external: 1, internal: 2});");

    NestedCompilerRunner.create(
            original, options, Mode.TRANSPILE_AND_OPTIMIZE, new DefaultPassConfig(options))
        .addScript(script, "test")
        .compile();

    verifyCompilationSucceeded();
    assertNode(script).isEqualTo(parse("alert({external: 1, a: 2})"));
  }

  @Test
  public void stackTrace_specifiesInShadowAstCompilation() {
    PassConfig alwaysThrowsPassConfig =
        new PassConfig.PassConfigDelegate(new DefaultPassConfig(options)) {
          @Override
          public PassListBuilder getOptimizations() {
            var passes = new PassListBuilder(options);
            passes.maybeAdd(
                PassFactory.builder()
                    .setName("throw")
                    .setInternalFactory(
                        (compiler) -> {
                          throw new AssertionError("foobarbaz");
                        })
                    .build());
            return passes;
          }
        };
    original.init(ImmutableList.of(), ImmutableList.of(), options);
    var compilerRunner =
        NestedCompilerRunner.create(
                original, options, Mode.TRANSPILE_AND_OPTIMIZE, alwaysThrowsPassConfig)
            .addScript(createScript(""), "test");

    RuntimeException ex = assertThrows(RuntimeException.class, compilerRunner::compile);

    assertThat(ex)
        .hasMessageThat()
        .isEqualTo("Exception during compilation: <shadow AST compilation>");
    assertThat(ex).hasCauseThat().hasMessageThat().isEqualTo("foobarbaz");
  }

  // TODO: lharker - add a test for debug logging.

  @Test
  public void doesNotRunTypechecking() {
    options.setCheckTypes(true);
    original.init(ImmutableList.of(), ImmutableList.of(), options);

    Node script = createScript("/** @type {string} */ const x = 0;");

    NestedCompilerRunner.create(
            original, options, Mode.TRANSPILE_AND_OPTIMIZE, new DefaultPassConfig(options))
        .addScript(script, "test")
        .compile();

    verifyCompilationSucceeded();
    assertNode(script).isEqualTo(parse("const x = 0;"));
  }

  private Node createScript(String code) {
    SourceFile file = SourceFile.fromCode("test.js", code, SourceKind.STRONG);

    Node script = IR.script();
    script.setStaticSourceFile(file);
    script.setIsInClosureUnawareSubtree(true);
    script.putProp(Node.FEATURE_SET, FeatureSet.ALL);

    script.addChildrenToFront(original.parseTestCode(code).removeChildren());
    return script;
  }

  private Node parse(String src) {
    return original.parseTestCode(src);
  }

  private void verifyCompilationSucceeded() {
    assertThat(original.getErrors()).isEmpty();
    assertThat(original.getWarnings()).isEmpty();
  }
}
