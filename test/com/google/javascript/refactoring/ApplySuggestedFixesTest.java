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

package com.google.javascript.refactoring;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.Node;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test case for {@link ApplySuggestedFixes}.
 */
@RunWith(JUnit4.class)
public class ApplySuggestedFixesTest {

  @Test
  public void testApplyCodeReplacements_overlapsAreErrors() throws Exception {
    ImmutableList<CodeReplacement> replacements =
        ImmutableList.of(CodeReplacement.create(0, 10, ""), CodeReplacement.create(5, 3, ""));
    assertThrows(
        IllegalArgumentException.class,
        () -> ApplySuggestedFixes.applyCodeReplacements(replacements, ""));
  }

  @Test
  public void testApplyCodeReplacements_overlapsAreErrors_unlessEqual() throws Exception {
    ImmutableList<CodeReplacement> replacements =
        ImmutableList.of(CodeReplacement.create(0, 3, "A"), CodeReplacement.create(0, 3, "A"));
    ApplySuggestedFixes.applyCodeReplacements(replacements, "abcdef");
  }

  @Test
  public void testApplyCodeReplacements_noOverlapsSucceed() throws Exception {
    ImmutableList<CodeReplacement> replacements =
        ImmutableList.of(CodeReplacement.create(0, 3, ""), CodeReplacement.create(5, 3, ""));
    ApplySuggestedFixes.applyCodeReplacements(replacements, "abcdef");
  }

  @Test
  public void testApplyCodeReplacements() throws Exception {
    ImmutableList<CodeReplacement> replacements =
        ImmutableList.of(CodeReplacement.create(0, 1, "z"), CodeReplacement.create(3, 2, "qq"));
    String newCode = ApplySuggestedFixes.applyCodeReplacements(replacements, "abcdef");
    assertEquals("zbcqqf", newCode);
  }

  @Test
  public void testApplyCodeReplacements_insertion() throws Exception {
    ImmutableList<CodeReplacement> replacements =
        ImmutableList.of(CodeReplacement.create(0, 0, "z"));
    String newCode = ApplySuggestedFixes.applyCodeReplacements(replacements, "abcdef");
    assertEquals("zabcdef", newCode);
  }

  @Test
  public void testApplyCodeReplacements_deletion() throws Exception {
    ImmutableList<CodeReplacement> replacements =
        ImmutableList.of(CodeReplacement.create(0, 6, ""));
    String newCode = ApplySuggestedFixes.applyCodeReplacements(replacements, "abcdef");
    assertThat(newCode).isEmpty();
  }

  @Test
  public void testApplyCodeReplacements_boundaryCases() throws Exception {
    ImmutableList<CodeReplacement> replacements =
        ImmutableList.of(CodeReplacement.create(5, 1, "z"));
    String newCode = ApplySuggestedFixes.applyCodeReplacements(replacements, "abcdef");
    assertEquals("abcdez", newCode);
  }

  @Test
  public void testApplyCodeReplacements_multipleReplacements() throws Exception {
    ImmutableList<CodeReplacement> replacements =
        ImmutableList.of(
            CodeReplacement.create(0, 2, "z"),
            CodeReplacement.create(2, 1, "y"),
            CodeReplacement.create(3, 3, "xwvu"));
    String newCode = ApplySuggestedFixes.applyCodeReplacements(replacements, "abcdef");
    assertEquals("zyxwvu", newCode);
  }

  @Test
  public void testApplySuggestedFixes() throws Exception {
    String code = "var someNode;";
    Compiler compiler = getCompiler(code);
    Node root = compileToScriptRoot(compiler);
    ImmutableList<SuggestedFix> fixes =
        ImmutableList.of(new SuggestedFix.Builder().delete(root).build());
    ImmutableMap<String, String> codeMap = ImmutableMap.of("test", code);
    ImmutableMap<String, String> newCodeMap =
        ApplySuggestedFixes.applySuggestedFixesToCode(fixes, codeMap);
    assertThat(newCodeMap).containsExactly("test", "");
  }

  @Test
  public void testApplySuggestedFixes_insideJSDoc() throws Exception {
    String code = "/** @type {Foo} */\nvar foo = new Foo()";
    Compiler compiler = getCompiler(code);
    Node root = compileToScriptRoot(compiler);
    Node varNode = root.getFirstChild();
    Node jsdocRoot =
        Iterables.getOnlyElement(varNode.getJSDocInfo().getTypeNodes());
    SuggestedFix fix = new SuggestedFix.Builder()
        .insertBefore(jsdocRoot, "!")
        .build();
    ImmutableList<SuggestedFix> fixes = ImmutableList.of(fix);
    ImmutableMap<String, String> codeMap = ImmutableMap.of("test", code);
    ImmutableMap<String, String> newCodeMap =
        ApplySuggestedFixes.applySuggestedFixesToCode(fixes, codeMap);
    assertThat(newCodeMap).containsExactly("test", "/** @type {!Foo} */\nvar foo = new Foo()");
  }

  @Test
  public void testApplySuggestedFixes_multipleFixesInJsdoc() throws Exception {
    String code = "/** @type {Array<Foo>} */\nvar arr = [new Foo()];";
    Compiler compiler = getCompiler(code);
    Node root = compileToScriptRoot(compiler);
    Node varNode = root.getFirstChild();
    Node jsdocRoot =
        Iterables.getOnlyElement(varNode.getJSDocInfo().getTypeNodes());
    SuggestedFix fix1 = new SuggestedFix.Builder()
        .insertBefore(jsdocRoot, "!")
        .build();
    Node foo = jsdocRoot.getFirstFirstChild();
    SuggestedFix fix2 = new SuggestedFix.Builder()
        .insertBefore(foo, "!")
        .build();
    ImmutableList<SuggestedFix> fixes = ImmutableList.of(fix1, fix2);
    ImmutableMap<String, String> codeMap = ImmutableMap.of("test", code);
    ImmutableMap<String, String> newCodeMap =
        ApplySuggestedFixes.applySuggestedFixesToCode(fixes, codeMap);
    assertThat(newCodeMap)
        .containsExactly("test", "/** @type {!Array<!Foo>} */\nvar arr = [new Foo()];");
  }

  @Test
  public void testApplySuggestedFixes_noFixes() throws Exception {
    ImmutableMap<String, String> codeMap =
        ImmutableMap.of(
            "file1", "abcdef",
            "file2", "abcdef");
    ImmutableMap<String, String> expectedNewCodeMap = ImmutableMap.of();
    ImmutableList<SuggestedFix> fixes = ImmutableList.of();
    ImmutableMap<String, String> newCodeMap =
        ApplySuggestedFixes.applySuggestedFixesToCode(fixes, codeMap);
    assertEquals(expectedNewCodeMap, newCodeMap);
  }

  @Test
  public void testApplySuggestedFixes_missingCodeForFile() throws Exception {
    ImmutableMap<String, String> codeMap = ImmutableMap.of();
    String code = "var someNode;";
    Compiler compiler = getCompiler(code);
    Node root = compileToScriptRoot(compiler);
    ImmutableList<SuggestedFix> fixes =
        ImmutableList.of(new SuggestedFix.Builder().delete(root).build());
    assertThrows(
        IllegalArgumentException.class,
        () -> ApplySuggestedFixes.applySuggestedFixesToCode(fixes, codeMap));
  }

  @Test
  public void testApplySuggestedFixes_withOveralppingEqualParts_areAllApplied() throws Exception {
    String code = "var first, second, shared;";
    Compiler compiler = getCompiler(code);
    Node root = compileToScriptRoot(compiler);
    System.out.println(root.toStringTree());
    Node var = root.getFirstChild();

    ImmutableList<SuggestedFix> fixes =
        ImmutableList.of(
            new SuggestedFix.Builder()
                .rename(var.getLastChild(), "newShared")
                .rename(var.getFirstChild(), "newFirst")
                .build(),
            new SuggestedFix.Builder()
                .rename(var.getLastChild(), "newShared")
                .rename(var.getSecondChild(), "newSecond")
                .build());

    ImmutableMap<String, String> newCodeMap =
        ApplySuggestedFixes.applySuggestedFixesToCode(fixes, ImmutableMap.of("test", code));
    assertThat(newCodeMap).containsExactly("test", "var newFirst, newSecond, newShared;");
  }

  /** Returns the root script node produced from the compiled JS input. */
  private static Node compileToScriptRoot(Compiler compiler) {
    Node root = compiler.getRoot();
    // The last child of the compiler root is a Block node, and the first child
    // of that is the Script node.
    return root.getLastChild().getFirstChild();
  }

  private static Compiler getCompiler(String jsInput) {
    Compiler compiler = new Compiler();
    CompilerOptions options = RefactoringDriver.getCompilerOptions();
    compiler.init(
        ImmutableList.<SourceFile>of(), // Externs
        ImmutableList.of(SourceFile.fromCode("test", jsInput)),
        options);
    compiler.parse();
    return compiler;
  }
}
