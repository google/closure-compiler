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
import java.util.logging.Logger;

/**
 * CompilerTestCase for passes that run after type checking and use type information.
 *
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public abstract class TypeICompilerTestCase extends CompilerTestCase {

  private static final Logger logger =
      Logger.getLogger("com.google.javascript.jscomp.TypeICompilerTestCase");

  protected static enum TypeInferenceMode {
    DISABLED,
    CHECKED;

    boolean runsTypeCheck() {
      return this == CHECKED;
    }

    boolean runsWithoutTypeCheck() {
      return this == DISABLED;
    }
  }

  protected TypeInferenceMode mode = TypeInferenceMode.CHECKED;

  public TypeICompilerTestCase() {
    super(MINIMAL_EXTERNS);
  }

  public TypeICompilerTestCase(String defaultExterns) {
    super(defaultExterns);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    this.mode = TypeInferenceMode.CHECKED;
  }

  @Override
  protected void testInternal(
      Externs externs,
      Sources js,
      Expected expected,
      Diagnostic diagnostic,
      List<Postcondition> postconditions) {
    if (this.mode.runsTypeCheck()) {
      logger.info("Running with typechecking");
      enableTypeCheck();
      super.testInternal(externs, js, expected, diagnostic, postconditions);
      disableTypeCheck();
    }
    if (this.mode.runsWithoutTypeCheck()) {
      logger.info("Running without typechecking");
      super.testInternal(externs, js, expected, diagnostic, postconditions);
    }
  }

  @Override
  protected void testExternChanges(String extern, String input, String expectedExtern,
      DiagnosticType... warnings) {
    if (this.mode.runsTypeCheck()) {
      enableTypeCheck();
      super.testExternChanges(extern, input, expectedExtern, warnings);
      disableTypeCheck();
    }
    if (this.mode.runsWithoutTypeCheck()) {
      super.testExternChanges(extern, input, expectedExtern, warnings);
    }
  }
}
