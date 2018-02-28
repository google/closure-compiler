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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.ErrorManager;
import com.google.javascript.jscomp.PrintStreamErrorManager;
import com.google.javascript.jscomp.SourceFile;
import java.util.ArrayList;
import java.util.List;
import junit.framework.TestCase;

/** Tests for {@link DepsGenerator}. */
public final class DepsGeneratorTest extends TestCase {

  private static final Joiner LINE_JOINER = Joiner.on("\n");
  private ErrorManager errorManager;

  @Override
  protected void setUp() throws Exception {
    errorManager = new PrintStreamErrorManager(System.err);
  }

  // TODO(johnplaisted): This should eventually be an error. For now people are relying on this
  // behavior for interop / ordering. Until we have official channels for these allow this behavior,
  // but don't encourage it.
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
                ModuleLoader.PathResolver.ABSOLUTE,
                ModuleLoader.ResolutionMode.BROWSER));
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

    assertEquals(expected, output);
  }

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
                ModuleLoader.PathResolver.ABSOLUTE,
                ModuleLoader.ResolutionMode.BROWSER));
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

    assertEquals(expected, output);
  }

  public void testGoogPathRequireForEs6ModuleFromGoogModule() throws Exception {
    List<SourceFile> srcs = new ArrayList<>();
    srcs.add(
        SourceFile.fromCode(
            "/base/javascript/foo/foo.js",
            LINE_JOINER.join(
                "goog.module('foo');", "const es6 = goog.require('../closure/goog/es6.js');")));
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
                ModuleLoader.PathResolver.ABSOLUTE,
                ModuleLoader.ResolutionMode.BROWSER));
    String output = depsGenerator.computeDependencyCalls();

    assertNoWarnings();

    // Write the output.
    assertWithMessage("There should be output").that(output).isNotEmpty();

    // Write the expected output.
    String expected =
        LINE_JOINER.join(
            "goog.addDependency('../foo/foo.js', ['foo'], "
                + "['goog/es6.js'], {'lang': 'es6', 'module': 'goog'});",
            "goog.addDependency('goog/es6.js', [], " + "[], {'lang': 'es6', 'module': 'es6'});",
            "");

    assertEquals(expected, output);
  }

  public void testGoogPathRequireForEs6ModuleInDepsFileFromGoogModule() throws Exception {
    ImmutableList<SourceFile> deps =
        ImmutableList.of(
            SourceFile.fromCode(
                "deps.js",
                "goog.addDependency('goog/es6.js', [], [], {'lang': 'es6', 'module': 'es6'})"));
    ImmutableList<SourceFile> srcs =
        ImmutableList.of(
            SourceFile.fromCode(
                "/base/javascript/foo/foo.js",
                LINE_JOINER.join(
                    "goog.module('foo');", "const es6 = goog.require('../closure/goog/es6.js');")));
    DepsGenerator depsGenerator =
        new DepsGenerator(
            deps,
            srcs,
            DepsGenerator.InclusionStrategy.ALWAYS,
            "/base/javascript/closure",
            errorManager,
            new ModuleLoader(
                null,
                ImmutableList.of("/base/"),
                ImmutableList.of(),
                ModuleLoader.PathResolver.ABSOLUTE,
                ModuleLoader.ResolutionMode.BROWSER));
    String output = depsGenerator.computeDependencyCalls();

    assertNoWarnings();

    // Write the output.
    assertWithMessage("There should be output").that(output).isNotEmpty();

    // Write the expected output.
    String expected =
        LINE_JOINER.join(
            "goog.addDependency('../foo/foo.js', ['foo'], "
                + "['goog/es6.js'], {'lang': 'es6', 'module': 'goog'});",
            "",
            "// Included from: deps.js",
            "goog.addDependency('goog/es6.js', [], [], {'lang': 'es6', 'module': 'es6'});",
            "");

    assertEquals(expected, output);
  }

  public void testGoogPathRequireForGoogModuleFromGoogModuleIsInvalid() throws Exception {
    List<SourceFile> srcs = new ArrayList<>();
    srcs.add(
        SourceFile.fromCode(
            "/base/javascript/foo/foo.js",
            LINE_JOINER.join(
                "goog.module('foo');",
                "const examplemodule = goog.require('../closure/goog/examplemodule.js');")));
    srcs.add(
        SourceFile.fromCode(
            "/base/javascript/closure/goog/examplemodule.js",
            "goog.module('goog.examplemodule');"));

    doErrorMessagesRun(
        ImmutableList.of(),
        srcs,
        true /* fatal */,
        "Cannot goog.require \"/base/javascript/closure/goog/examplemodule.js\" by path. It is "
            + "not an ES6 module.");
  }

  public void testGoogPathRequireForGoogModuleFromEs6ModuleIsInvalid() throws Exception {
    List<SourceFile> srcs = new ArrayList<>();
    srcs.add(
        SourceFile.fromCode(
            "/base/javascript/foo/foo.js",
            LINE_JOINER.join(
                "const examplemodule = goog.require('../closure/goog/examplemodule.js');",
                "export default examplemodule;")));
    srcs.add(
        SourceFile.fromCode(
            "/base/javascript/closure/goog/examplemodule.js",
            "goog.module('goog.examplemodule');"));

    doErrorMessagesRun(
        ImmutableList.of(),
        srcs,
        true /* fatal */,
        "Cannot goog.require \"/base/javascript/closure/goog/examplemodule.js\" by path. It is "
            + "not an ES6 module.",
        "Cannot goog.require by path outside of a goog.module.");
  }

  /**
   * Ensures that deps files are handled correctly both when listed as deps and when listed as
   * sources.
   */
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
                ImmutableList.<DependencyInfo>of(),
                ModuleLoader.PathResolver.ABSOLUTE,
                ModuleLoader.ResolutionMode.BROWSER));

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
                ImmutableList.<DependencyInfo>of(),
                ModuleLoader.PathResolver.ABSOLUTE,
                ModuleLoader.ResolutionMode.BROWSER));

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
                ImmutableList.<DependencyInfo>of(),
                ModuleLoader.PathResolver.ABSOLUTE,
                ModuleLoader.ResolutionMode.BROWSER));

    String output = depsGenerator.computeDependencyCalls();

    assertWithMessage("There should be output").that(output).isNotEmpty();
    assertNoWarnings();
  }

  public void testMergeStrategyAlways() throws Exception {
    String result = testMergeStrategyHelper(DepsGenerator.InclusionStrategy.ALWAYS);
    assertContains("['a']", result);
    assertContains("['b']", result);
    assertContains("['c']", result);
    assertContains("d.js", result);
  }

  public void testMergeStrategyWhenInSrcs() throws Exception {
    String result = testMergeStrategyHelper(DepsGenerator.InclusionStrategy.WHEN_IN_SRCS);
    assertNotContains("['a']", result);
    assertContains("['b']", result);
    assertContains("['c']", result);
    assertNotContains("d.js", result);
  }

  public void testMergeStrategyDoNotDuplicate() throws Exception {
    String result = testMergeStrategyHelper(DepsGenerator.InclusionStrategy.DO_NOT_DUPLICATE);
    assertNotContains("['a']", result);
    assertNotContains("['b']", result);
    assertContains("['c']", result);
    assertNotContains("d.js", result);
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
                ImmutableList.<DependencyInfo>of(),
                ModuleLoader.PathResolver.ABSOLUTE,
                ModuleLoader.ResolutionMode.BROWSER));

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
                ImmutableList.<DependencyInfo>of(),
                ModuleLoader.PathResolver.ABSOLUTE,
                ModuleLoader.ResolutionMode.BROWSER));
    String output = depsGenerator.computeDependencyCalls();

    if (fatal) {
      assertWithMessage("No output should have been created.").that(output).isNull();
      assertErrors(errorMessages);
    } else {
      assertWithMessage("Output should have been created.").that(output).isNotNull();
      assertWarnings(errorMessages);
    }
  }

  public void testDuplicateProvides() throws Exception {
    SourceFile dep1 = SourceFile.fromCode("dep1.js",
        "goog.addDependency('a.js', ['a'], []);\n");
    SourceFile src1 = SourceFile.fromCode("src1.js",
        "goog.provide('a');\n");

    doErrorMessagesRun(ImmutableList.of(dep1), ImmutableList.of(src1), true /* fatal */,
        "Namespace \"a\" is already provided in other file dep1.js");
  }

  /**
   * Ensures that an error is thrown when the closure_path flag is set incorrectly.
   */
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
                ImmutableList.<DependencyInfo>of(),
                ModuleLoader.PathResolver.ABSOLUTE,
                ModuleLoader.ResolutionMode.BROWSER));

    String output = worker.computeDependencyCalls();

    assertWithMessage("Output should have been created.").that(output).isNotEmpty();
    assertNoWarnings();
  }

  public void testDuplicateProvidesSameFile() throws Exception {
    SourceFile dep1 = SourceFile.fromCode("dep1.js",
        "goog.addDependency('a.js', ['a'], []);\n");
    SourceFile src1 =
        SourceFile.fromCode(
            "src1.js", LINE_JOINER.join("goog.provide('b');", "goog.provide('b');\n"));

    doErrorMessagesRun(ImmutableList.of(dep1), ImmutableList.of(src1), false /* fatal */,
        "Multiple calls to goog.provide(\"b\")");
  }

  public void testDuplicateRequire() throws Exception {
    SourceFile dep1 = SourceFile.fromCode("dep1.js",
        "goog.addDependency('a.js', ['a'], []);\n");
    SourceFile src1 =
        SourceFile.fromCode(
            "src1.js", LINE_JOINER.join("goog.require('a');", "goog.require('a');", ""));

    doErrorMessagesRun(ImmutableList.of(dep1), ImmutableList.of(src1), false /* fatal */,
        "Namespace \"a\" is required multiple times");
  }

  public void testSameFileProvideRequire() throws Exception {
    SourceFile dep1 = SourceFile.fromCode("dep1.js",
        "goog.addDependency('a.js', ['a'], []);\n");
    SourceFile src1 =
        SourceFile.fromCode(
            "src1.js", LINE_JOINER.join("goog.provide('b');", "goog.require('b');", ""));

    doErrorMessagesRun(ImmutableList.of(dep1), ImmutableList.of(src1), false /* fatal */,
        "Namespace \"b\" is both required and provided in the same file.");
  }

  public void testUnknownNamespace() throws Exception {
    SourceFile dep1 = SourceFile.fromCode("dep1.js",
        "goog.addDependency('a.js', ['a'], []);\n");
    SourceFile src1 = SourceFile.fromCode("src1.js",
        "goog.require('b');\n");

    doErrorMessagesRun(ImmutableList.of(dep1), ImmutableList.of(src1), true /* fatal */,
        "Namespace \"b\" is required but never provided.");
  }

  public void testNoDepsInDepsFile() throws Exception {
    SourceFile dep1 = SourceFile.fromCode("dep1.js", "");

    doErrorMessagesRun(ImmutableList.of(dep1), ImmutableList.<SourceFile>of(), false /* fatal */,
        "No dependencies found in file");
  }

  public void testUnknownEs6Module() throws Exception {
    SourceFile src1 = SourceFile.fromCode("src1.js", "import './missing.js';\n");

    doErrorMessagesRun(
        ImmutableList.of(),
        ImmutableList.of(src1),
        true /* fatal */,
        "Could not find file \"./missing.js\".");
  }

  public void testGoogPathRequireFromNonModuleIsInvalid() throws Exception {
    List<SourceFile> srcs = new ArrayList<>();
    srcs.add(
        SourceFile.fromCode(
            "/base/javascript/foo/foo.js",
            LINE_JOINER.join(
                "goog.provide('foo');",
                "const array = goog.require('../closure/goog/array.js');")));
    srcs.add(SourceFile.fromCode("/base/javascript/closure/goog/array.js", "export var array;"));

    doErrorMessagesRun(
        ImmutableList.of(),
        srcs,
        true /* fatal */,
        "Cannot goog.require by path outside of a goog.module.");
  }

  public void testGoogPathRequireBetweenEs6ModulesIsInvalid() throws Exception {
    List<SourceFile> srcs = new ArrayList<>();
    srcs.add(
        SourceFile.fromCode(
            "/base/javascript/foo/foo.js",
            "export var foo; const array = goog.require('../closure/goog/array.js');"));
    srcs.add(SourceFile.fromCode("/base/javascript/closure/goog/array.js", "export var array;"));

    doErrorMessagesRun(
        ImmutableList.of(),
        srcs,
        true /* fatal */,
        "Use ES6 import rather than goog.require to import ES6 module "
            + "\"/base/javascript/closure/goog/array.js\".");
  }

  private void assertErrorWarningCount(int errorCount, int warningCount) {
    if (errorManager.getErrorCount() != errorCount) {
      fail(String.format("Expected %d errors but got\n%s",
          errorCount, Joiner.on("\n").join(errorManager.getErrors())));
    }
    if (errorManager.getWarningCount() != warningCount) {
      fail(String.format("Expected %d warnings but got\n%s",
          warningCount, Joiner.on("\n").join(errorManager.getWarnings())));
    }
  }

  private void assertNoWarnings() {
    assertErrorWarningCount(0, 0);
  }

  private void assertWarnings(String... messages) {
    assertErrorWarningCount(0, messages.length);
    for (int i = 0; i < messages.length; i++) {
      assertThat(errorManager.getWarnings()[i].description).isEqualTo(messages[i]);
    }
  }

  private void assertErrors(String... messages) {
    assertErrorWarningCount(messages.length, 0);
    for (int i = 0; i < messages.length; i++) {
      assertThat(errorManager.getErrors()[i].description).isEqualTo(messages[i]);
    }
  }

  private static void assertContains(String part, String whole) {
    assertWithMessage("Expected string to contain: " + part).that(whole.contains(part)).isTrue();
  }

  private static void assertNotContains(String part, String whole) {
    assertWithMessage("Expected string not to contain: " + part)
        .that(whole.contains(part))
        .isFalse();
  }
}
