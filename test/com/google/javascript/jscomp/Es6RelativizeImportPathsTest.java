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

import static com.google.javascript.jscomp.deps.ModuleLoader.LOAD_WARNING;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.deps.ModuleLoader.PathEscaper;
import com.google.javascript.jscomp.deps.ModuleLoader.ResolutionMode;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class Es6RelativizeImportPathsTest extends CompilerTestCase {

  private ImmutableMap<String, String> prefixReplacements;
  private ResolutionMode moduleResolutionMode;
  private List<String> moduleRoots;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    prefixReplacements = ImmutableMap.of();
    moduleResolutionMode = ResolutionMode.BROWSER;
    moduleRoots = ImmutableList.of();
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setBrowserResolverPrefixReplacements(prefixReplacements);
    options.setModuleResolutionMode(moduleResolutionMode);
    options.setModuleRoots(moduleRoots);
    options.setPathEscaper(PathEscaper.CANONICALIZE_ONLY);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new Es6RelativizeImportPaths(compiler);
  }

  @Test
  public void testLocalRelativePathStayTheSame() {
    ignoreWarnings(LOAD_WARNING);
    testSame(srcs(SourceFile.fromCode("/file.js", "import './foo.js';")));

    testSame(srcs(SourceFile.fromCode("/nested/file.js", "import './foo.js';")));

    testSame(srcs(SourceFile.fromCode("/file.js", "import './nested/foo.js';")));

    testSame(srcs(SourceFile.fromCode("http://google.com/file.js", "import './foo.js';")));
  }

  @Test
  public void testRelativeParentPathStayTheSame() {
    ignoreWarnings(LOAD_WARNING);
    testSame(srcs(SourceFile.fromCode("/nested/file.js", "import '../foo.js';")));

    testSame(srcs(SourceFile.fromCode("/nested/file.js", "import '../nested/foo.js';")));

    testSame(srcs(SourceFile.fromCode("/p0/p1/file.js", "import '../p2/foo.js';")));

    testSame(srcs(SourceFile.fromCode("/p0/p1/file.js", "import '../../foo.js';")));
    testSame(srcs(SourceFile.fromCode("http://google.com/nested/file.js", "import '../foo.js';")));
  }

  @Test
  public void testAbsolutePathsStayTheSame() {
    // Absolute means that with a scheme, not those starting with "/"
    testSame(
        srcs(
            SourceFile.fromCode(
                "http://google.com/file.js", "import 'http://google.com/other/file.js';")));

    testSame(
        srcs(
            SourceFile.fromCode(
                "http://golang.org/file.js", "import 'http://google.com/other/file.js';")));

    testSame(srcs(SourceFile.fromCode("/file.js", "import 'http://google.com/other/file.js';")));
  }

  @Test
  public void testRootedPathsBecomeRelative() {
    ignoreWarnings(LOAD_WARNING);
    test(
        srcs(SourceFile.fromCode("/file.js", "import '/foo.js';")),
        expected(SourceFile.fromCode("/file.js", "import './foo.js';")));

    test(
        srcs(SourceFile.fromCode("/nested/file.js", "import '/foo.js';")),
        expected(SourceFile.fromCode("/nested/file.js", "import '../foo.js';")));

    test(
        srcs(SourceFile.fromCode("/nested/file.js", "import '/path/foo.js';")),
        expected(SourceFile.fromCode("/nested/file.js", "import '../path/foo.js';")));

    test(
        srcs(SourceFile.fromCode("/file.js", "import '/nested/foo.js';")),
        expected(SourceFile.fromCode("/file.js", "import './nested/foo.js';")));
  }

  @Test
  public void testNonStandardResolutionBecomesRelative() {
    moduleResolutionMode = ResolutionMode.BROWSER_WITH_TRANSFORMED_PREFIXES;
    prefixReplacements = ImmutableMap.of("raw0/", "/mapped0/", "raw1/", "/mapped1/");

    test(
        srcs(SourceFile.fromCode("/file.js", "import 'raw0/foo.js';")),
        expected(SourceFile.fromCode("/file.js", "import './mapped0/foo.js';")));

    test(
        srcs(SourceFile.fromCode("/nested/file.js", "import 'raw0/foo.js';")),
        expected(SourceFile.fromCode("/nested/file.js", "import '../mapped0/foo.js';")));

    test(
        srcs(SourceFile.fromCode("/file.js", "import 'raw0/bar/foo.js';")),
        expected(SourceFile.fromCode("/file.js", "import './mapped0/bar/foo.js';")));

    test(
        srcs(SourceFile.fromCode("/file.js", "import 'raw1/foo.js';")),
        expected(SourceFile.fromCode("/file.js", "import './mapped1/foo.js';")));

    test(
        srcs(SourceFile.fromCode("/mapped1/file.js", "import 'raw1/foo.js';")),
        expected(SourceFile.fromCode("/mapped1/file.js", "import './foo.js';")));

    test(
        srcs(SourceFile.fromCode("http://google.com/file.js", "import 'raw1/foo.js';")),
        expected(SourceFile.fromCode("http://google.com/file.js", "import './mapped1/foo.js';")));

    test(
        srcs(SourceFile.fromCode("http://google.com/nested/file.js", "import 'raw1/foo.js';")),
        expected(SourceFile.fromCode("http://google.com/file.js", "import '../mapped1/foo.js';")));
  }

  @Test
  public void testModuleRootsBecomeRelative() {
    ignoreWarnings(LOAD_WARNING);
    moduleRoots = ImmutableList.of("root1", "root2");

    test(
        srcs(SourceFile.fromCode("/file.js", "import '/root1/foo.js';")),
        expected(SourceFile.fromCode("/file.js", "import './foo.js';")));

    test(
        srcs(SourceFile.fromCode("/nested/file.js", "import '/root2/foo.js';")),
        expected(SourceFile.fromCode("/nested/file.js", "import '../foo.js';")));

    test(
        srcs(SourceFile.fromCode("/file.js", "import '/root1/bar/foo.js';")),
        expected(SourceFile.fromCode("/file.js", "import './bar/foo.js';")));
  }
}
