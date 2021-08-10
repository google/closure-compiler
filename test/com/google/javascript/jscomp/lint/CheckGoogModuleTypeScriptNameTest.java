/*
 * Copyright 2021 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.lint;

import static com.google.javascript.jscomp.lint.CheckGoogModuleTypeScriptName.MODULE_NAMESPACE_MISMATCHES_TYPESCRIPT_NAMESPACE;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.SourceFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CheckGoogModuleTypeScriptName}. */
@RunWith(JUnit4.class)
public final class CheckGoogModuleTypeScriptNameTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckGoogModuleTypeScriptName(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  @Test
  public void testNoWarning() {
    testNoWarning(
        srcs(
            SourceFile.fromCode(
                "gws/js/test.js", "goog.module('google3.gws.js.test');\nalert(1);")));
  }

  @Test
  public void testNoWarning_notOnAllowList() {
    testNoWarning(
        srcs(
            SourceFile.fromCode("javascript/apps/foo/test.js", "goog.module('test');\nalert(1);")));
  }

  @Test
  public void testNoWarning_noModule() {
    testNoWarning(lines("goog.provide('test');", "alert(1)"));
  }

  @Test
  public void testWarning() {
    test(
        srcs(
            ImmutableList.of(
                SourceFile.fromCode("gws/js/test.js", "goog.module('test');\nalert(1);"))),
        warning(MODULE_NAMESPACE_MISMATCHES_TYPESCRIPT_NAMESPACE)
            .withMessageContaining(lines("The correct namespace is: \"google3.gws.js.test\"")));
  }

  // MOE::begin_strip
  @Test
  public void testWarningWithFullSourceName() {
    test(
        srcs(
            ImmutableList.of(
                SourceFile.fromCode(
                    "/google/src/cloud/someuser/someworkspace/google3/gws/js/test.js",
                    "goog.module('test');\nalert(1);"))),
        warning(MODULE_NAMESPACE_MISMATCHES_TYPESCRIPT_NAMESPACE)
            .withMessageContaining(lines("The correct namespace is: \"google3.gws.js.test\"")));
  }
  // MOE::end_strip
}
