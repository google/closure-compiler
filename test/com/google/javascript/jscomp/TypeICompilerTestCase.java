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

import java.util.List;

/**
 * CompilerTestCase for passes that run after type checking and use type information.
 * Allows us to test those passes with both type checkers.
 *
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public abstract class TypeICompilerTestCase extends CompilerTestCase {

  protected static enum TypeInferenceMode {
    OtiOnly,
    NtiOnly,
    Both;

    boolean runsOTI() {
      return this == OtiOnly || this == Both;
    }

    boolean runsNTI() {
      return this == NtiOnly || this == Both;
    }
  }

  protected TypeInferenceMode mode = TypeInferenceMode.Both;

  public TypeICompilerTestCase() {
    super();
  }

  public TypeICompilerTestCase(String defaultExterns) {
    super(defaultExterns);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    this.mode = TypeInferenceMode.Both;
  }

  // NOTE(aravindpg): the idea with these selective overrides is that every `test` call
  // in a subclass must go through one and exactly one of the overrides here, which are
  // the ones that actually run the test twice (once under OTI and once under NTI).
  // The `test` methods in CompilerTestCase overload each other in complicated ways,
  // and this is the minimal set of overrides (of visible methods) that essentially
  // "post-dominates" any `test` call.

  @Override
  public void test(
      List<SourceFile> externs,
      String js,
      String expected,
      DiagnosticType error,
      DiagnosticType warning,
      String description) {
    if (this.mode.runsOTI()) {
      enableTypeCheck();
      super.test(externs, js, expected, error, warning, description);
      disableTypeCheck();
    }
    if (this.mode.runsNTI()) {
      enableNewTypeInference();
      super.test(externs, js, expected, error, warning, description);
      disableNewTypeInference();
    }
  }

  @Override
  public void test(
      List<SourceFile> inputs,
      String[] expected,
      DiagnosticType error,
      DiagnosticType warning,
      String description) {
    if (this.mode.runsOTI()) {
      enableTypeCheck();
      super.test(inputs, expected, error, warning, description);
      disableTypeCheck();
    }
    if (this.mode.runsNTI()) {
      enableNewTypeInference();
      super.test(inputs, expected, error, warning, description);
      disableNewTypeInference();
    }
  }

  @Override
  public void test(
      List<SourceFile> js,
      List<SourceFile> expected,
      DiagnosticType error,
      DiagnosticType warning,
      String description) {
    if (this.mode.runsOTI()) {
      enableTypeCheck();
      super.test(js, expected, error, warning, description);
      disableTypeCheck();
    }
    if (this.mode.runsNTI()) {
      enableNewTypeInference();
      super.test(js, expected, error, warning, description);
      disableNewTypeInference();
    }
  }

  @Override
  protected void test(
      Compiler compiler, String[] expected, DiagnosticType error, DiagnosticType warning) {
    if (this.mode.runsOTI()) {
      enableTypeCheck();
      super.test(compiler, expected, error, warning);
      disableTypeCheck();
    }
    if (this.mode.runsNTI()) {
      enableNewTypeInference();
      super.test(compiler, expected, error, warning);
      disableNewTypeInference();
    }
  }
}

