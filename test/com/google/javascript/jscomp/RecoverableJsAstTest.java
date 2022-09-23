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
import static com.google.javascript.jscomp.testing.JSCompCorrespondences.DESCRIPTION_EQUALITY;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Jimfs;
import com.google.javascript.rhino.Node;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.nullness.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link RecoverableJsAst}.
 *
 */
@RunWith(JUnit4.class)
public class RecoverableJsAstTest {
  private Path tempFile;

  @Before
  public void setup() throws IOException {
    FileSystem fs = Jimfs.newFileSystem();
    this.tempFile = fs.getPath("/test.js");
    Files.createFile(this.tempFile);
  }

  @Test
  public void testSimple() throws IOException {
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
  public void testWarningReplay() throws IOException {
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

  private void setSourceCode(String code) throws IOException {
    Files.writeString(tempFile, code);
  }

  private SourceAst createRealAst() {
    // use SourceFile.fromPath instead of a preloaded SourceFile.fromCode so that test cases may
    // modify the contents of the SourceFile via changing the underlying file.
    SourceFile file = SourceFile.fromPath(tempFile, UTF_8);
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

  private void checkCompile(
      SourceAst realAst,
      RecoverableJsAst ast,
      @Nullable String expected,
      ImmutableList<String> expectedErrors) {
    Compiler compiler = new Compiler();

    // Keep this a "small" test. Don't use threads.
    compiler.disableThreads();

    JSChunk module = new JSChunk("m0");
    module.add(new CompilerInput(ast));
    compiler.compileModules(ImmutableList.<SourceFile>of(),
        ImmutableList.of(module),
        createCompilerOptions());

    Node mainRoot = compiler.getRoot().getLastChild();

    Node expectedRoot = null;
    if (expected != null) {
      expectedRoot = parseExpectedJs(ImmutableList.of(
          SourceFile.fromCode("expected.js", expected)));
      expectedRoot.detach();
    }

    if (expectedRoot == null) {
      // We use null to signal a parse failure, which results in an empty sources root.
      assertThat(mainRoot.isRoot()).isTrue();
      assertThat(mainRoot.hasChildren()).isFalse();
    } else {
      assertNode(mainRoot)
          .usingSerializer(compiler::toSource)
          .isEqualIncludingJsDocTo(expectedRoot);
    }

    assertThat(compiler.getResult().errors)
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactlyElementsIn(expectedErrors)
        .inOrder();

    assertThat(ast.getAstRoot(compiler)).isNotSameInstanceAs(realAst.getAstRoot(compiler));
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
    return externsRoot.getNext();
  }
}
