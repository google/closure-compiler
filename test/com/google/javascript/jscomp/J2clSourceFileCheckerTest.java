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

import com.google.common.collect.Lists;

/** Tests for {@link J2clSourceFileChecker}. */
public class J2clSourceFileCheckerTest extends CompilerTestCase {
  private static final SourceFile NO_J2CL_SOURCE_FILE =
      SourceFile.fromCode(
          "noJ2cl.js", "goog.provide('noJ2cl'); /** @constructor */ function noJ2cl(){};");
  private static final SourceFile NO_J2CL_SOURCE_FILE2 =
      SourceFile.fromCode(
          "noJ2cl2.js", "goog.provide('noJ2cl2'); /** @constructor */ function noJ2cl2(){};");
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

  public void testHasJ2cl() {
    testSame(Lists.newArrayList(NO_J2CL_SOURCE_FILE, J2CL_SOURCE_FILE, NO_J2CL_SOURCE_FILE2));
    assertThat(compiler.getAnnotation(J2clSourceFileChecker.HAS_J2CL_ANNOTATION_KEY))
        .isEqualTo(Boolean.TRUE);
  }

  public void testHasNoJ2cl() {
    testSame(Lists.newArrayList(NO_J2CL_SOURCE_FILE, NO_J2CL_SOURCE_FILE2));
    assertThat(compiler.getAnnotation(J2clSourceFileChecker.HAS_J2CL_ANNOTATION_KEY))
        .isEqualTo(Boolean.FALSE);
  }

  public void testShouldSkipExecutionWithoutJ2cl() {
    Compiler compiler = new Compiler();
    assertThat(J2clSourceFileChecker.shouldSkipExecution(compiler)).isFalse();

    compiler.setAnnotation(J2clSourceFileChecker.HAS_J2CL_ANNOTATION_KEY, false);
    assertThat(J2clSourceFileChecker.shouldSkipExecution(compiler)).isTrue();
  }

  public void testShouldSkipExecutionWithJ2cl() {
    Compiler compiler = new Compiler();
    assertThat(J2clSourceFileChecker.shouldSkipExecution(compiler)).isFalse();

    compiler.setAnnotation(J2clSourceFileChecker.HAS_J2CL_ANNOTATION_KEY, true);
    assertThat(J2clSourceFileChecker.shouldSkipExecution(compiler)).isFalse();
  }
}
