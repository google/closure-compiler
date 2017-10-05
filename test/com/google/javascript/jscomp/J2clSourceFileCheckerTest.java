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
package com.google.javascript.jscomp;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.J2clPassMode;

/** Tests for {@link J2clSourceFileChecker}. */
public class J2clSourceFileCheckerTest extends CompilerTestCase {
  private static final SourceFile J2CL_SOURCE_FILE =
      SourceFile.fromCode(
          "jar:file://foo.js.zip!foo/bar.impl.java.js",
          "goog.provide('bar'); /** @constructor */ function bar(){};");

  private J2clSourceFileChecker checkContainingJ2cl;
  private AbstractCompiler compiler;

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    this.compiler = compiler;
    checkContainingJ2cl = new J2clSourceFileChecker(compiler);
    return checkContainingJ2cl;
  }

  public void testShouldRunJ2clPassesWithoutJ2clSources() {
    testSame("");
    assertThat(J2clSourceFileChecker.shouldRunJ2clPasses(compiler)).isFalse();

    compiler.getOptions().setJ2clPass(J2clPassMode.ON);
    assertThat(J2clSourceFileChecker.shouldRunJ2clPasses(compiler)).isFalse();
    compiler.getOptions().setJ2clPass(J2clPassMode.TRUE);
    assertThat(J2clSourceFileChecker.shouldRunJ2clPasses(compiler)).isFalse();
    compiler.getOptions().setJ2clPass(J2clPassMode.AUTO);
    assertThat(J2clSourceFileChecker.shouldRunJ2clPasses(compiler)).isFalse();
  }

  public void testShouldRunJ2clPassesWithJ2clSources() {
    testSame(ImmutableList.of(J2CL_SOURCE_FILE));
    assertThat(J2clSourceFileChecker.shouldRunJ2clPasses(compiler)).isTrue();

    compiler.getOptions().setJ2clPass(J2clPassMode.AUTO);
    assertThat(J2clSourceFileChecker.shouldRunJ2clPasses(compiler)).isTrue();
  }
}
