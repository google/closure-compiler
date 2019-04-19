/*
 * Copyright 2016 The Closure Compiler Authors.
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


package com.google.javascript.jscomp.deps;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.testing.JSCompCorrespondences.DESCRIPTION_EQUALITY;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.jscomp.PrintStreamErrorManager;
import com.google.javascript.jscomp.SourceFile;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link DepsGenerator}. */
@RunWith(JUnit4.class)
public final class DepsGeneratorTest {

  private static final Joiner LINE_JOINER = Joiner.on("\n");
  private ErrorManager errorManager;

  @Before
  public void setUp() throws Exception {
    errorManager = new PrintStreamErrorManager(System.err);
  }

  // TODO(johnplaisted): This should eventually be an error. For now people are relying on this
  // behavior for interop / ordering. Until we have official channels for these allow this behavior,
  // but don't encourage it.
  @Test
  public void testEs6ModuleWithGoogProvide() throws Exception {
    List<SourceFile> srcs = new ArrayList<>();
    srcs.add(
        SourceFile.fromCode(
            "/base/javascript/foo/foo.js",
                "goog.provide('my.namespace');\nimport '../closure/goog/es6.js';"));
    srcs.add(SourceFile.fromCode("/base/javascript/closure/goog/es6.js", "export var es6;"));
    DepsGenerator depsGenerator =
        new DepsGenerator(
            ImmutableList.of(),
            srcs,
            DepsGenerator.InclusionStrategy.ALWAYS,
            "/base/javascript/closure",
            errorManager,
            new ModuleLoader(
                null,
                ImmutableList.of("/base/"),
                ImmutableList.of(),
                BrowserModuleResolver.FACTORY,
                ModuleLoader.PathResolver.ABSOLUTE));
    String output = depsGenerator.computeDependencyCalls();

    assertWarnings(
        "File cannot be a combination of goog.provide, goog.module, and/or ES6 "
            + "module: javascript/foo/foo.js");

    // Write the output.
    assertWithMessage("There should be output").that(output).isNotEmpty();

    // Write the expected output.
    String expected =
        LINE_JOINER.join(
            "goog.addDependency('../foo/foo.js', ['my.namespace'], "
                + "['goog/es6.js'], {'lang': 'es6', 'module': 'es6'});",
            "goog.addDependency('goog/es6.js', [], " + "[], {'lang': 'es6', 'module': 'es6'});",
            "");

    assertThat(output).isEqualTo(expected);
  }

  @Test
  public void testEs6Modules() throws Exception {
    List<SourceFile> srcs = new ArrayList<>();
    srcs.add(SourceFile.fromCode("/base/javascript/foo/foo.js", "import '../closure/goog/es6';"));
    srcs.add(SourceFile.fromCode("/base/javascript/closure/goog/es6.js", "export var es6;"));
    DepsGenerator depsGenerator =
        new DepsGenerator(
            ImmutableList.of(),
            srcs,
            DepsGenerator.InclusionStrategy.ALWAYS,
            "/base/javascript/closure",
            errorManager,
            new ModuleLoader(
                null,
                ImmutableList.of("/base/"),
                ImmutableList.of(),
                BrowserModuleResolver.FACTORY,
                ModuleLoader.PathResolver.ABSOLUTE));
    String output = depsGenerator.computeDependencyCalls();

    assertNoWarnings();

    // Write the output.
    assertWithMessage("There should be output").that(output).isNotEmpty();

    // Write the expected output.
    String expected =
        LINE_JOINER.join(
            "goog.addDependency('../foo/foo.js', [], "
                + "['goog/es6.js'], {'lang': 'es6', 'module': 'es6'});",
            "goog.addDependency('goog/es6.js', [], " + "[], {'lang': 'es6', 'module': 'es6'});",
            "");

    assertThat(output).isEqualTo(expected);
  }

  @Test
  public void testEs6ModuleDeclareModuleId() throws Exception {
    List<SourceFile> srcs = new ArrayList<>();
    srcs.add(
        SourceFile.fromCode(
            "/base/javascript/foo/foo.js", "goog.declareModuleId('my.namespace');\nexport {};"));
    srcs.add(
        SourceFile.fromCode(
            "/base/javascript/closure/goog/googmodule.js",
            LINE_JOINER.join(
                "goog.module('my.goog.module');",
                "const namespace = goog.require('my.namespace');")));
    DepsGenerator depsGenerator =
        new DepsGenerator(
            ImmutableList.of(),
            srcs,
            DepsGenerator.InclusionStrategy.ALWAYS,
            "/base/javascript/closure",
            errorManager,
            new ModuleLoader(
                null,
                ImmutableList.of("/base/"),
                ImmutableList.of(),
                BrowserModuleResolver.FACTORY,
                ModuleLoader.PathResolver.ABSOLUTE));
    String output = depsGenerator.computeDependencyCalls();

    assertNoWarnings();

    // Write the output.
    assertWithMessage("There should be output").that(output).isNotEmpty();

    // Write the expected output.
    String expected =
        LINE_JOINER.join(
            "goog.addDependency('../foo/foo.js', ['my.namespace'], "
                + "[], {'lang': 'es6', 'module': 'es6'});",
            "goog.addDependency('goog/googmodule.js', ['my.goog.module'], ['my.namespace'], "
                + "{'lang': 'es6', 'module': 'goog'});",
            "");

    assertThat(output).isEqualTo(expected);
  }

  // Unit test for an issue run into by https://github.com/google/closure-compiler/pull/3026
  @Test
  public void testEs6ModuleScanDeps() throws Exception {
    // Simple ES6 modules
    ImmutableList<SourceFile> srcs =
        ImmutableList.of(
            SourceFile.fromCode("/src/css-parse.js", "export class StyleNode {}"),
            SourceFile.fromCode(
                "/src/apply-shim-utils.js", "import {StyleNode} from './css-parse.js';"));

    // Run them through a DepsGenerator that is set up the same way as our internal MakeJsDeps tool.
    DepsGenerator depsGenerator =
        new DepsGenerator(
            ImmutableList.of(),
            srcs,
            DepsGenerator.InclusionStrategy.ALWAYS,
            PathUtil.makeAbsolute("/base/javascript/closure"),
            errorManager,
            new ModuleLoader(
                null,
                ImmutableList.of("."),
                ImmutableList.of(),
                BrowserModuleResolver.FACTORY,
                ModuleLoader.PathResolver.ABSOLUTE));

    String output = depsGenerator.computeDependencyCalls();

    // Make sure that there are no spurious errors.
    assertNoWarnings();

    String expected =
        LINE_JOINER.join(
            "goog.addDependency('../../../src/css-parse.js',"
                + " [],"
                + " [],"
                + " {'lang': 'es6', 'module': 'es6'});",
            "goog.addDependency('../../../src/apply-shim-utils.js',"
                + " [],"
                + " ['../../../src/css-parse.js'],"
                + " {'lang': 'es6', 'module': 'es6'});",
            "");
    assertThat(output).isEqualTo(expected);
  }

  /**
   * Ensures that deps files are handled correctly both when listed as deps and when listed as
   * sources.
   */
  @Test
  public void testWithDepsAndSources() throws Exception {
    final SourceFile depsFile1 =
        SourceFile.fromCode(
            "/base/my-project/deps1.js",
            LINE_JOINER.join(
                "goog.addDependency('../prescanned1/file1.js', ['dep.string'], []);",
                "goog.addDependency('../prescanned1/file2.js', [], []);",
                // Test that this appears only once in the output.
                "goog.addDependency('../this/is/defined/thrice.js', [], []);",
                "goog.addDependency('../this/is/defined/thrice.js', [], []);",
                ""));
    final SourceFile depsFile2 =
        SourceFile.fromCode(
            "/base/my-project/deps2.js",
            LINE_JOINER.join(
                "goog.addDependency("
                    + "'../prescanned2/file1.js',"
                    + " ['dep.bool', 'dep.number'],"
                    + " ['dep.string']);",
                "goog.addDependency('../prescanned2/file2.js', [], []);",
                "goog.addDependency('../this/is/defined/thrice.js', [], []);",
                ""));
    final SourceFile srcFile1 =
        SourceFile.fromCode(
            "/base/my-project/src1.js",
            LINE_JOINER.join(
                "goog.provide('makejsdeps.file1');",
                "goog.provide('makejsdeps.file1.Test');",
                "",
                // Ensure comments are stripped. These cause syntax errors if not stripped.
                "/*",
                "goog.require('failure1)",
                "*/",
                "// goog.require('failure2)",
                "",
                "goog.require('makejsdeps.file2');",
                // 'goog' should be silently dropped.
                "goog.require(\"goog\");",
                "goog.require(\"dep.string\");",
                "goog.require(\"dep.number\");",
                ""));
    final SourceFile srcFile2 =
        SourceFile.fromCode("/base/my-project/src2.js", "goog.provide('makejsdeps.file2');");

    String expected =
        LINE_JOINER.join(
            "goog.addDependency("
                + "'../../my-project/src1.js',"
                + " ['makejsdeps.file1', 'makejsdeps.file1.Test'],"
                + " ['makejsdeps.file2', 'dep.string', 'dep.number']);",
            "goog.addDependency('../../my-project/src2.js', ['makejsdeps.file2'], []);",
            "",
            "// Included from: /base/my-project/deps1.js",
            "goog.addDependency('../prescanned1/file1.js', ['dep.string'], []);",
            "goog.addDependency('../prescanned1/file2.js', [], []);",
            "",
            "// Included from: /base/my-project/deps2.js",
            "goog.addDependency('../this/is/defined/thrice.js', [], []);",
            "goog.addDependency("
                + "'../prescanned2/file1.js',"
                + " ['dep.bool', 'dep.number'],"
                + " ['dep.string']);",
            "goog.addDependency('../prescanned2/file2.js', [], []);",
            "");

    DepsGenerator depsGenerator =
        new DepsGenerator(
            ImmutableList.of(depsFile1, depsFile2),
            ImmutableList.of(srcFile1, srcFile2),
            DepsGenerator.InclusionStrategy.ALWAYS,
            "/base/javascript/closure",
            errorManager,
            new ModuleLoader(
                null,
                ImmutableList.of("/base/"),
                ImmutableList.of(),
                BrowserModuleResolver.FACTORY,
                ModuleLoader.PathResolver.ABSOLUTE));

    String output = depsGenerator.computeDependencyCalls();

    assertNoWarnings();
    assertThat(output).isEqualTo(expected);

    // Repeat the test with the deps files listed as sources.
    // The only difference should be the addition of addDependency() calls for the deps files.
    depsGenerator =
        new DepsGenerator(
            ImmutableList.<SourceFile>of(),
            ImmutableList.of(depsFile1, depsFile2, srcFile1, srcFile2),
            DepsGenerator.InclusionStrategy.ALWAYS,
            "/base/javascript/closure",
            errorManager,
            new ModuleLoader(
                null,
                ImmutableList.of("/base/"),
                ImmutableList.of(),
                BrowserModuleResolver.FACTORY,
                ModuleLoader.PathResolver.ABSOLUTE));

    String expectedWithDepsAsSources =
        LINE_JOINER.join(
            "goog.addDependency('../../my-project/deps1.js', [], []);",
            "goog.addDependency('../../my-project/deps2.js', [], []);",
            expected);

    output = depsGenerator.computeDependencyCalls();

    assertNoWarnings();
    assertThat(output).isEqualTo(expectedWithDepsAsSources);
  }

  /**
   * Ensures that everything still works when both a deps.js and a deps-runfiles.js file are
   * included. Also uses real files.
   */
  @Test
  public void testDepsAsSrcs() throws Exception {
    final SourceFile depsFile1 =
        SourceFile.fromCode(
            "/base/deps1.js",
            LINE_JOINER.join(
                "// Test deps file 1.",
                "",
                "goog.addDependency('../prescanned1/file1.js', ['dep.string'], []);",
                "goog.addDependency('../prescanned1/file2.js', [], []);",
                "// Test that this appears only once in the output. It's also defined in deps2.js",
                "goog.addDependency('../this/is/defined/thrice.js', [], []);",
                "goog.addDependency('../this/is/defined/thrice.js', [], []);",
                ""));
    final SourceFile depsFile2 =
        SourceFile.fromCode(
            "/base/deps2.js",
            LINE_JOINER.join(
                "// Test deps file 2.",
                "",
                "goog.addDependency("
                    + "'../prescanned2/file1.js', ['dep.bool', 'dep.number'], ['dep.string']);",
                "goog.addDependency('../prescanned2/file2.js', [], []);",
                "goog.addDependency('../prescanned2/generated.js', ['dep.generated'], []);",
                "goog.addDependency('../this/is/defined/thrice.js', [], []);",
                ""));
    DepsGenerator depsGenerator =
        new DepsGenerator(
            ImmutableList.of(depsFile1),
            ImmutableList.of(depsFile2),
            DepsGenerator.InclusionStrategy.ALWAYS,
            PathUtil.makeAbsolute("/base/javascript/closure"),
            errorManager,
            new ModuleLoader(
                null,
                ImmutableList.of("/base/" + "/"),
                ImmutableList.of(),
                BrowserModuleResolver.FACTORY,
                ModuleLoader.PathResolver.ABSOLUTE));

    String output = depsGenerator.computeDependencyCalls();

    assertWithMessage("There should be output").that(output).isNotEmpty();
    assertNoWarnings();
  }

  @Test
  public void testMergeStrategyAlways() throws Exception {
    String result = testMergeStrategyHelper(DepsGenerator.InclusionStrategy.ALWAYS);
    assertThat(result).contains("['a']");
    assertThat(result).contains("['b']");
    assertThat(result).contains("['c']");
    assertThat(result).contains("d.js");
  }

  @Test
  public void testMergeStrategyWhenInSrcs() throws Exception {
    String result = testMergeStrategyHelper(DepsGenerator.InclusionStrategy.WHEN_IN_SRCS);
    assertThat(result).doesNotContain("['a']");
    assertThat(result).contains("['b']");
    assertThat(result).contains("['c']");
    assertThat(result).doesNotContain("d.js");
  }

  @Test
  public void testMergeStrategyDoNotDuplicate() throws Exception {
    String result = testMergeStrategyHelper(DepsGenerator.InclusionStrategy.DO_NOT_DUPLICATE);
    assertThat(result).doesNotContain("['a']");
    assertThat(result).doesNotContain("['b']");
    assertThat(result).contains("['c']");
    assertThat(result).doesNotContain("d.js");
  }

  private String testMergeStrategyHelper(DepsGenerator.InclusionStrategy mergeStrategy)
      throws Exception {
    SourceFile dep1 =
        SourceFile.fromCode(
            "dep1.js",
            LINE_JOINER.join(
                "goog.addDependency('../../a.js', ['a'], []);",
                "goog.addDependency('../../src1.js', ['b'], []);",
                "goog.addDependency('../../d.js', ['d'], []);\n"));
    SourceFile src1 = SourceFile.fromCode("/base/" + "/src1.js", "goog.provide('b');\n");
    SourceFile src2 =
        SourceFile.fromCode(
            "/base/" + "/src2.js", LINE_JOINER.join("goog.provide('c');", "goog.require('d');"));
    DepsGenerator depsGenerator =
        new DepsGenerator(
            ImmutableList.of(dep1),
            ImmutableList.of(src1, src2),
            mergeStrategy,
            PathUtil.makeAbsolute("/base/javascript/closure"),
            errorManager,
            new ModuleLoader(
                null,
                ImmutableList.of("/base/"),
                ImmutableList.of(),
                BrowserModuleResolver.FACTORY,
                ModuleLoader.PathResolver.ABSOLUTE));

    String output = depsGenerator.computeDependencyCalls();

    assertNoWarnings();
    assertWithMessage("There should be output files").that(output).isNotEmpty();

    return output;
  }

  private void doErrorMessagesRun(
      List<SourceFile> deps, List<SourceFile> srcs, boolean fatal, String... errorMessages)
      throws Exception {

    DepsGenerator depsGenerator =
        new DepsGenerator(
            deps,
            srcs,
            DepsGenerator.InclusionStrategy.ALWAYS,
            "/javascript/closure",
            errorManager,
            new ModuleLoader(
                null,
                ImmutableList.of(""),
                ImmutableList.of(),
                BrowserModuleResolver.FACTORY,
                ModuleLoader.PathResolver.ABSOLUTE));
    String output = depsGenerator.computeDependencyCalls();

    if (fatal) {
      assertWithMessage("No output should have been created.").that(output).isNull();
      assertErrors(errorMessages);
    } else {
      assertWithMessage("Output should have been created.").that(output).isNotNull();
      assertWarnings(errorMessages);
    }
  }

  @Test
  public void testDuplicateProvides() throws Exception {
    SourceFile dep1 = SourceFile.fromCode("dep1.js",
        "goog.addDependency('a.js', ['a'], []);\n");
    SourceFile src1 = SourceFile.fromCode("src1.js",
        "goog.provide('a');\n");

    doErrorMessagesRun(ImmutableList.of(dep1), ImmutableList.of(src1), true /* fatal */,
        "Namespace \"a\" is already provided in other file dep1.js");
  }

  /** Ensures that an error is thrown when the closure_path flag is set incorrectly. */
  @Test
  public void testDuplicateProvidesErrorThrownIfBadClosurePathSpecified() throws Exception {
    // Create a stub Closure Library.
    SourceFile fauxClosureDeps =
        SourceFile.fromCode("dep1.js", "goog.addDependency('foo/a.js', ['a'], []);\n");
    SourceFile fauxClosureSrc =
        SourceFile.fromCode("path/to/closure/foo/a.js", "goog.provide('a');\n");
    // Create a source file that depends on the stub Closure Library.
    SourceFile userSrc =
        SourceFile.fromCode("my/package/script.js", "goog.require('a');\n"
            + "goog.provide('my.package.script');\n");

    // doErrorMessagesRun uses closure_path //javascript/closure and therefore
    // fails to recognize and de-dupe the stub Closure Library at
    // //path/to/closure.
    doErrorMessagesRun(ImmutableList.of(fauxClosureDeps),
        ImmutableList.of(fauxClosureSrc, userSrc), true /* fatal */,
        "Namespace \"a\" is already provided in other file dep1.js");
  }

  /** Ensures that DepsGenerator deduplicates dependencies from custom Closure Library branches. */
  @Test
  public void testDuplicateProvidesIgnoredIfInClosureDirectory() throws Exception {
    // Create a stub Closure Library.
    SourceFile fauxClosureDeps =
        SourceFile.fromCode("dep1.js", "goog.addDependency('foo/a.js', ['a'], []);\n");
    SourceFile fauxClosureSrc =
        SourceFile.fromCode("path/to/closure/foo/a.js", "goog.provide('a');\n");
    // Create a source file that depends on the stub Closure Library.
    SourceFile userSrc =
        SourceFile.fromCode("my/package/script.js", "goog.require('a');\n"
            + "goog.provide('my.package.script');\n");
    DepsGenerator worker =
        new DepsGenerator(
            ImmutableList.of(fauxClosureDeps),
            ImmutableList.of(fauxClosureSrc, userSrc),
            DepsGenerator.InclusionStrategy.ALWAYS,
            PathUtil.makeAbsolute("./path/to/closure"),
            errorManager,
            new ModuleLoader(
                null,
                ImmutableList.of("."),
                ImmutableList.of(),
                BrowserModuleResolver.FACTORY,
                ModuleLoader.PathResolver.ABSOLUTE));

    String output = worker.computeDependencyCalls();

    assertWithMessage("Output should have been created.").that(output).isNotEmpty();
    assertNoWarnings();
  }

  @Test
  public void testDuplicateProvidesSameFile() throws Exception {
    SourceFile dep1 = SourceFile.fromCode("dep1.js",
        "goog.addDependency('a.js', ['a'], []);\n");
    SourceFile src1 =
        SourceFile.fromCode(
            "src1.js", LINE_JOINER.join("goog.provide('b');", "goog.provide('b');\n"));

    doErrorMessagesRun(ImmutableList.of(dep1), ImmutableList.of(src1), false /* fatal */,
        "Multiple calls to goog.provide(\"b\")");
  }

  @Test
  public void testDuplicateRequire() throws Exception {
    SourceFile dep1 = SourceFile.fromCode("dep1.js",
        "goog.addDependency('a.js', ['a'], []);\n");
    SourceFile src1 =
        SourceFile.fromCode(
            "src1.js", LINE_JOINER.join("goog.require('a');", "goog.require('a');", ""));

    doErrorMessagesRun(ImmutableList.of(dep1), ImmutableList.of(src1), false /* fatal */,
        "Namespace \"a\" is required multiple times");
  }

  @Test
  public void testSameFileProvideRequire() throws Exception {
    SourceFile dep1 = SourceFile.fromCode("dep1.js",
        "goog.addDependency('a.js', ['a'], []);\n");
    SourceFile src1 =
        SourceFile.fromCode(
            "src1.js", LINE_JOINER.join("goog.provide('b');", "goog.require('b');", ""));

    doErrorMessagesRun(ImmutableList.of(dep1), ImmutableList.of(src1), false /* fatal */,
        "Namespace \"b\" is both required and provided in the same file.");
  }

  @Test
  public void testUnknownNamespace() throws Exception {
    SourceFile dep1 = SourceFile.fromCode("dep1.js",
        "goog.addDependency('a.js', ['a'], []);\n");
    SourceFile src1 = SourceFile.fromCode("src1.js",
        "goog.require('b');\n");

    doErrorMessagesRun(
        ImmutableList.of(dep1),
        ImmutableList.of(src1),
        true /* fatal */,
        "Namespace \"b\" is required but never provided.\n"
            + "You need to pass a library that has it in srcs or exports to your target's deps.");
  }

  @Test
  public void testNoDepsInDepsFile() throws Exception {
    SourceFile dep1 = SourceFile.fromCode("dep1.js", "");

    doErrorMessagesRun(ImmutableList.of(dep1), ImmutableList.<SourceFile>of(), false /* fatal */,
        "No dependencies found in file");
  }

  @Test
  public void testUnknownEs6Module() throws Exception {
    SourceFile src1 = SourceFile.fromCode("src1.js", "import './missing.js';\n");

    doErrorMessagesRun(
        ImmutableList.of(),
        ImmutableList.of(src1),
        true /* fatal */,
        "Could not find file \"./missing.js\".");
  }

  private void assertNoWarnings() {
    assertThat(errorManager.getWarnings()).isEmpty();
    assertThat(errorManager.getErrors()).isEmpty();
  }

  private void assertWarnings(String... messages) {
    assertThat(errorManager.getWarnings())
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactly(messages)
        .inOrder();
    assertThat(errorManager.getErrors()).isEmpty();
  }

  private void assertErrors(String... messages) {
    assertThat(errorManager.getWarnings()).isEmpty();
    assertThat(errorManager.getErrors())
        .comparingElementsUsing(DESCRIPTION_EQUALITY)
        .containsExactly(messages)
        .inOrder();
  }
}
