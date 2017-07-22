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

import java.io.IOException;

/**
 * CompilerTestCase for passes that run after type checking and use type information.
 * Allows us to test those passes with both type checkers.
 *
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public abstract class TypeICompilerTestCase extends CompilerTestCase {

  protected static enum TypeInferenceMode {
    NEITHER,
    OTI_ONLY,
    NTI_ONLY,
    BOTH;

    boolean runsOTI() {
      return this == OTI_ONLY || this == BOTH;
    }

    boolean runsNTI() {
      return this == NTI_ONLY || this == BOTH;
    }

    boolean runsNeither() {
      return this == NEITHER;
    }
  }

  protected TypeInferenceMode mode = TypeInferenceMode.BOTH;

  public TypeICompilerTestCase() {
    super(MINIMAL_EXTERNS);
  }

  public TypeICompilerTestCase(String defaultExterns) {
    super(defaultExterns);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    this.mode = TypeInferenceMode.BOTH;
  }

  // NOTE(aravindpg): the idea with these selective overrides is that every `test` call
  // in a subclass must go through one and exactly one of the overrides here, which are
  // the ones that actually run the test twice (once under OTI and once under NTI).
  // The `test` methods in CompilerTestCase overload each other in complicated ways,
  // and this is the minimal set of overrides (of visible methods) that essentially
  // "post-dominates" any `test` call.

  @Override
  protected void testInternal(
      Externs externs, Sources js, Expected expected, Diagnostic diagnostic) {
    if (this.mode.runsOTI()) {
      testOTI(externs, js, expected, diagnostic);
    }
    if (this.mode.runsNTI()) {
      if (!findMinimalExterns(externs.externs)) {
        fail("NTI reqires at least the MINIMAL_EXTERNS");
      }
      testNTI(externs, js, expected, diagnostic);
    }
    if (this.mode.runsNeither()) {
      super.testInternal(externs, js, expected, diagnostic);
    }
  }

  private static boolean findMinimalExterns(Iterable<SourceFile> externs) {
    try {
      for (SourceFile extern : externs) {
        if (extern.getCode().contains(MINIMAL_EXTERNS)) {
          return true;
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return false;
  }

  private void testOTI(Externs externs, Sources js, Expected expected, Diagnostic diagnostic) {
    enableTypeCheck();
    Diagnostic oti =
        diagnostic instanceof OtiNtiDiagnostic ? ((OtiNtiDiagnostic) diagnostic).oti : diagnostic;
    super.testInternal(externs, js, expected, oti);
    disableTypeCheck();
  }

  private void testNTI(Externs externs, Sources js, Expected expected, Diagnostic diagnostic) {
    enableNewTypeInference();
    Diagnostic nti =
        diagnostic instanceof OtiNtiDiagnostic ? ((OtiNtiDiagnostic) diagnostic).nti : diagnostic;
    super.testInternal(externs, js, expected, nti);
    disableNewTypeInference();
  }

  void testWarningOtiNti(
      String js, DiagnosticType otiWarning, DiagnosticType ntiWarning) {
    TypeInferenceMode saved = this.mode;
    this.mode = TypeInferenceMode.OTI_ONLY;
    testWarning(js, otiWarning);
    this.mode = TypeInferenceMode.NTI_ONLY;
    testWarning(js, ntiWarning);
    this.mode = saved;
  }

  // Helpers to test separate warnings/errors with OTI and NTI.

  protected static OtiNtiDiagnostic warningOtiNti(DiagnosticType oti, DiagnosticType nti) {
    return new OtiNtiDiagnostic(
        oti != null ? warning(oti) : null, nti != null ? warning(nti) : null);
  }

  protected static OtiNtiDiagnostic diagnosticOtiNti(Diagnostic oti, Diagnostic nti) {
    return new OtiNtiDiagnostic(oti, nti);
  }

  protected static class OtiNtiDiagnostic extends Diagnostic {
    private final Diagnostic oti;
    private final Diagnostic nti;

    private OtiNtiDiagnostic(Diagnostic oti, Diagnostic nti) {
      super(null, null, null);
      this.oti = oti;
      this.nti = nti;
    }
  }
}
