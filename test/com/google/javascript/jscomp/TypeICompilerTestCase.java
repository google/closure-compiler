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
import java.util.List;
import java.util.logging.Logger;

/**
 * CompilerTestCase for passes that run after type checking and use type information.
 * Allows us to test those passes with both type checkers.
 *
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public abstract class TypeICompilerTestCase extends CompilerTestCase {

  private static final Logger logger =
      Logger.getLogger("com.google.javascript.jscomp.TypeICompilerTestCase");

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
      Externs externs,
      Sources js,
      Expected expected,
      Diagnostic diagnostic,
      List<Postcondition> postconditions) {
    if (this.mode.runsOTI()) {
      logger.info("Running with OTI");
      testOTI(externs, js, expected, diagnostic, postconditions);
    }
    if (this.mode.runsNTI()) {
      logger.info("Running with NTI");
      checkMinimalExterns(externs.externs);
      testNTI(externs, js, expected, diagnostic, postconditions);
    }
    if (this.mode.runsNeither()) {
      logger.info("Running without typechecking");
      super.testInternal(externs, js, expected, diagnostic, postconditions);
    }
  }

  @Override
  protected void testExternChanges(String extern, String input, String expectedExtern,
      DiagnosticType... warnings) {
    if (this.mode.runsOTI()) {
      enableTypeCheck();
      super.testExternChanges(extern, input, expectedExtern, warnings);
      disableTypeCheck();
    }
    if (this.mode.runsNTI()) {
      enableNewTypeInference();
      super.testExternChanges(extern, input, expectedExtern, warnings);
      disableNewTypeInference();
    }
    if (this.mode.runsNeither()) {
      super.testExternChanges(extern, input, expectedExtern, warnings);
    }
  }

  // Note: may be overridden to allow different externs if necessary.
  void checkMinimalExterns(Iterable<SourceFile> externs) {
    try {
      for (SourceFile extern : externs) {
        if (extern.getCode().contains(MINIMAL_EXTERNS)) {
          return;
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    fail("NTI reqires at least the MINIMAL_EXTERNS");
  }

  private void testOTI(
      Externs externs,
      Sources js,
      Expected expected,
      Diagnostic diagnostic,
      List<Postcondition> postconditions) {
    TypeInferenceMode saved = this.mode;
    this.mode = TypeInferenceMode.OTI_ONLY;
    enableTypeCheck();
    Diagnostic oti =
        diagnostic instanceof OtiNtiDiagnostic ? ((OtiNtiDiagnostic) diagnostic).oti : diagnostic;
    super.testInternal(externs, js, expected, oti, postconditions);
    disableTypeCheck();
    this.mode = saved;
  }

  private void testNTI(
      Externs externs,
      Sources js,
      Expected expected,
      Diagnostic diagnostic,
      List<Postcondition> postconditions) {
    /*
    TypeInferenceMode saved = this.mode;
    this.mode = TypeInferenceMode.NTI_ONLY;
    enableNewTypeInference();
    Diagnostic nti =
        diagnostic instanceof OtiNtiDiagnostic ? ((OtiNtiDiagnostic) diagnostic).nti : diagnostic;
    super.testInternal(externs, js, expected, nti, postconditions);
    disableNewTypeInference();
    this.mode = saved;
    */
  }

  void testWarningOtiNti(
      String js, DiagnosticType otiWarning, DiagnosticType ntiWarning) {
    TypeInferenceMode saved = this.mode;
    this.mode = TypeInferenceMode.OTI_ONLY;
    testWarning(js, otiWarning);
    /*
    this.mode = TypeInferenceMode.NTI_ONLY;
    testWarning(js, ntiWarning);
    */
    this.mode = saved;
  }

  @Override
  protected Compiler getLastCompiler() {
    switch (this.mode) {
      case BOTH:
        throw new AssertionError("getLastCompiler does not work correctly in BOTH mode.");
      default:
        return super.getLastCompiler();
    }
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
