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
    Both
  }

  protected TypeInferenceMode mode = TypeInferenceMode.Both;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    this.mode = TypeInferenceMode.Both;
  }

  @Override
  protected CompilerOptions getOptions(CompilerOptions options) {
    options = super.getOptions(options);
    options.setRunOTIAfterNTI(false);
    return options;
  }

  @Override
  public void test(
      List<SourceFile> externs,
      String js,
      String expected,
      DiagnosticType error,
      DiagnosticType warning,
      String description) {
    if (this.mode == TypeInferenceMode.Both || this.mode == TypeInferenceMode.OtiOnly) {
      enableTypeCheck();
      super.test(externs, js, expected, error, warning, description);
      disableTypeCheck();
    }
    if (this.mode == TypeInferenceMode.Both || this.mode == TypeInferenceMode.NtiOnly) {
      enableNewTypeInference();
      super.test(externs, js, expected, error, warning, description);
      disableNewTypeInference();
    }
  }
}

