/*
 * Copyright 2010 The Closure Compiler Authors.
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.SourceFile.Generator;
import com.google.javascript.rhino.Node;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

/**
 * Unit tests for {@link RecoverableJsAst}.
 *
 */
@RunWith(JUnit4.class)
public class RecoverableJsAstTest {
  protected static final Joiner LINE_JOINER = Joiner.on('\n');

  private String srcCode = "";

  @Test
  public void testSimple() {
    setSourceCode("var a;");
    SourceAst realAst = createRealAst();

    // Note that here we do not test the caching of the tree between calls,
    // since that is the responsibility of the JsAst class.  Here we just test
    // that proxying is successful and a copy is happening

    // Initial compile.
    RecoverableJsAst ast1 = new RecoverableJsAst(realAst, true);
    checkCompile(realAst, makeDefensiveCopy(ast1), "var a;\n");

    // Change in the file-system.
    setSourceCode("var b;");
    realAst.clearAst();

    // The first RecoverableJsAst should continue to have the same value.
    checkCompile(realAst, makeDefensiveCopy(ast1), "var a;\n");

    // A newly created one from the source should have a different value.
    RecoverableJsAst ast2 = new RecoverableJsAst(realAst, true);
    checkCompile(realAst, makeDefensiveCopy(ast2), "var b;\n");

    // Clearing the first AST should also pick up the new changes.
    ast1.clearAst();
    checkCompile(realAst, makeDefensiveCopy(ast1), "var b;\n");
  }

  @Test
  public void testWarningReplay() {
    setSourceCode("var f() = a;");
    SourceAst realAst = createRealAst();

    // Note that here we do not test the caching of the tree between calls,
    // since that is the responsibility of the JsAst class.  Here we just test
    // that proxying is successful and a copy is happening

    // Initial compile.
    RecoverableJsAst ast1 = new RecoverableJsAst(realAst, true);
    checkParseErrors(realAst, makeDefensiveCopy(ast1), "Parse error. Semi-colon expected");

    // The first RecoverableJsAst should continue to have the same value.
    checkParseErrors(realAst, makeDefensiveCopy(ast1), "Parse error. Semi-colon expected");
  }


  private RecoverableJsAst makeDefensiveCopy(SourceAst ast) {
    // NOTE: We reuse RecoverableJsAst as a way of making tree clones, because
    // compilation mutates the tree.  This is unrelated to testing
    // RecoverableJsAst.
    return new RecoverableJsAst(ast, true);
  }

  private String getSourceCode() {
    return srcCode;
  }

  private void setSourceCode(String code) {
    srcCode = code;
  }

  private SourceAst createRealAst() {
    SourceFile file = SourceFile.fromGenerator("tests.js", new Generator() {
      @Override
      public String getCode() {
        return getSourceCode();
      }
    });
    return new JsAst(file);
  }

  private static CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setPrettyPrint(true);
    return options;
  }

  private void checkParseErrors(SourceAst realAst, RecoverableJsAst ast, String... expectedErrors) {
    checkCompile(realAst, ast, null, ImmutableList.copyOf(expectedErrors));
  }

  private void checkCompile(SourceAst realAst, RecoverableJsAst ast, String expected) {
    checkCompile(realAst, ast, expected, ImmutableList.<String>of());
  }

  private void checkCompile(SourceAst realAst, RecoverableJsAst ast, String expected,
      ImmutableList<String> expectedErrors) {
    Compiler compiler = new Compiler();

    // Keep this a "small" test. Don't use threads.
    compiler.disableThreads();

    JSModule module = new JSModule("m0");
    module.add(new CompilerInput(ast));
    compiler.compileModules(ImmutableList.<SourceFile>of(),
        ImmutableList.of(module),
        createCompilerOptions());

    Node mainRoot = compiler.getRoot().getLastChild();

    Node expectedRoot = null;
    if (expected != null) {
      expectedRoot = parseExpectedJs(ImmutableList.of(
          SourceFile.fromCode("expected.js", expected)));
      expectedRoot.detachFromParent();
    }

    if (expectedRoot == null) {
      // We use null to signal a parse failure, which results in an empty sources root.
      assertTrue(mainRoot.isBlock() && !mainRoot.hasChildren());
    } else {
      String explanation = expectedRoot.checkTreeEqualsIncludingJsDoc(mainRoot);
      if (explanation != null) {
        String expectedAsSource = compiler.toSource(expectedRoot);
        String mainAsSource = compiler.toSource(mainRoot);
        if (expectedAsSource.equals(mainAsSource)) {
          fail("In: " + expectedAsSource + "\n" + explanation);
        } else {
          fail("\nExpected: "
              + expectedAsSource
              + "\nResult:   "
              + mainAsSource
              + "\n" + explanation);
        }
      }
    }

    JSError[] errors = compiler.getResult().errors;
    if (!expectedErrors.isEmpty()) {
      for (int i = 0; i < expectedErrors.size(); i++) {
        if (i < errors.length) {
          assertThat(errors[i].toString()).contains(expectedErrors.get(i));
        } else {
          fail("missing error: " + expectedErrors.get(i));
        }
      }
    } else {
      assertThat(errors).isEmpty();
    }

    assertThat(ast.getAstRoot(compiler)).isNotSameAs(realAst.getAstRoot(compiler));
  }

  /**
   * Parses expected JS inputs and returns the root of the parse tree.
   */
  protected Node parseExpectedJs(List<SourceFile> inputs) {
    Compiler compiler = new Compiler();

    compiler.init(ImmutableList.<SourceFile>of(), inputs, createCompilerOptions());
    compiler.parse();
    Node root = compiler.getRoot();
    assertThat(root).isNotNull();
    Node externsRoot = root.getFirstChild();
    Node mainRoot = externsRoot.getNext();
    return mainRoot;
  }
}
