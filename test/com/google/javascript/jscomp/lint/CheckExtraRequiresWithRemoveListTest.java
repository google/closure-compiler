/*
 * Copyright 2015 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.lint.CheckExtraRequires.EXTRA_REQUIRE_WARNING;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the "extra requires" check in {@link CheckMissingAndExtraRequires}. */
@RunWith(JUnit4.class)
public final class CheckExtraRequiresWithRemoveListTest extends CompilerTestCase {

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.EXTRA_REQUIRE, CheckLevel.ERROR);
    options.setWarningLevel(DiagnosticGroups.MODULE_LOAD, CheckLevel.OFF);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckExtraRequires(compiler, ImmutableSet.of("xx", "yy", "zz"));
  }

  @Test
  public void testNoChange() {
    testSame("goog.require('aa');");
    testSame("goog.require('xx.foo');");
  }

  @Test
  public void testAffected() {
    testError(srcs("goog.require('xx');"), EXTRA_REQUIRE_WARNING);
    testError(srcs("goog.require('yy');"), EXTRA_REQUIRE_WARNING);
    testError(srcs("goog.require('zz');"), EXTRA_REQUIRE_WARNING);
  }

  @Test
  public void testShouldntRemove() {
    testSame("goog.require('xx'); var x = new xx();");
  }

  @Test
  public void testUsedInJsDoc() {
    testSame(lines("goog.require('xx');", "/** @type {xx}*/", "var a;"));
  }

  @Test
  public void testUsedInSubNamespace() {
    testSame("goog.require('xx.foo'); var a = xx.foo();");
  }
}
