/*
 * Copyright 2024 The Closure Compiler Authors.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ManageClosureUnawareCode}, specifically the wrapping part. */
@RunWith(JUnit4.class)
public final class ManageClosureUnawareCodeTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    allowExternsChanges();
    // This is instead done via the ValidationCheck in PhaseOptimizer
    // This set of tests can't use the build-in AST change marking validation
    // as the "before" is supposed to be identical to the "after" (with changes happening between
    // each pass). Unfortunately, CompilerTestCase is designed to only handle a single pass running,
    // and so it sees that the AST hasn't changed (expected) but changes have been reported (yep -
    // the changes were "done" and then "undone"), and fails.
    disableValidateAstChangeMarking();
  }

  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();
    runWrapPass = true;
    runUnwrapPass = true;
    languageInOverride = Optional.empty();
  }

  public boolean runWrapPass = true;
  public boolean runUnwrapPass = true;
  public Optional<CompilerOptions.LanguageMode> languageInOverride = Optional.empty();

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    if (languageInOverride.isPresent()) {
      options.setLanguageIn(languageInOverride.get());
    }
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    PhaseOptimizer phaseopt = new PhaseOptimizer(compiler, null);
    List<PassFactory> passes = new ArrayList<>();
    if (runWrapPass) {
      passes.add(
          PassFactory.builder()
              .setName("wrapClosureUnawareCode")
              .setInternalFactory(ManageClosureUnawareCode::wrap)
              .build());
    }
    if (runUnwrapPass) {
      passes.add(
          PassFactory.builder()
              .setName("unwrapClosureUnawareCode")
              .setInternalFactory(ManageClosureUnawareCode::unwrap)
              .build());
    }
    phaseopt.consume(passes);
    phaseopt.setValidityCheck(
        PassFactory.builder()
            .setName("validityCheck")
            .setRunInFixedPointLoop(true)
            .setInternalFactory(ValidityCheck::new)
            .build());
    compiler.setPhaseOptimizer(phaseopt);
    return phaseopt;
  }

  private void doTest(String js, String expectedWrapped) {
    runWrapPass = true;
    runUnwrapPass = false; // Validate that only wrapping results in the expected wrapped contents.
    test(js, expectedWrapped);

    // now test with unwrapping enabled so it is a no-op
    runWrapPass = true;
    runUnwrapPass = true;
    testSame(js);
  }

  @Test
  public void testDirectLoad() {
    doTest(
        lines(
            "/**",
            " * @fileoverview",
            " * @closureUnaware",
            " */",
            "goog.module('foo.bar.baz_raw');",
            "/** @closureUnaware */",
            "(function() {",
            "  window['foo'] = 5;",
            "}).call(globalThis);"),
        lines(
            "/** @fileoverview @closureUnaware */",
            "goog.module('foo.bar.baz_raw');",
            "$jscomp_wrap_closure_unaware_code('{window[\"foo\"]=5}')"));
  }

  @Test
  public void testDirectLoadWithRequireAndExports() {
    doTest(
        lines(
            "/**",
            " * @fileoverview",
            " * @closureUnaware",
            " */",
            "goog.module('foo.bar.baz_raw');",
            "goog.require('foo.bar');",
            "const {a} = goog.require('foo.baz');",
            "/** @closureUnaware */",
            "(function() {",
            "  window['foo'] = 5;",
            "}).call(globalThis);",
            "exports = globalThis['foo'];"),
        lines(
            "/**",
            " * @fileoverview",
            " * @closureUnaware",
            " */",
            "goog.module('foo.bar.baz_raw');",
            "goog.require('foo.bar');",
            "const {a} = goog.require('foo.baz');",
            "$jscomp_wrap_closure_unaware_code('{window[\"foo\"]=5}')",
            "exports = globalThis['foo'];"));
  }

  @Test
  public void testConditionalLoad() {
    doTest(
        lines(
            "/**",
            " * @fileoverview",
            " * @closureUnaware",
            " */",
            "goog.module('foo.bar.baz_raw');",
            "if (!window['foo']) {",
            "  /** @closureUnaware */",
            "  (function() {",
            "    window['foo'] = 5;",
            "  }).call(globalThis);",
            "}"),
        lines(
            "/**",
            " * @fileoverview",
            " * @closureUnaware",
            " */",
            "goog.module('foo.bar.baz_raw');",
            "if (!window['foo']) {",
            "  $jscomp_wrap_closure_unaware_code('{window[\"foo\"]=5}');",
            "}"));
  }

  @Test
  public void testDebugSrcLoad() {
    doTest(
        lines(
            "/**",
            " * @fileoverview",
            " * @closureUnaware",
            " */",
            "goog.module('foo.bar.baz_raw');",
            "if (goog.DEBUG) {",
            "  /** @closureUnaware */",
            "  (function() {",
            "    window['foo'] = 5;",
            "  }).call(globalThis);",
            "} else {",
            "  /** @closureUnaware */",
            "  (function() {",
            "     window['foo'] = 10;",
            "  }).call(globalThis);",
            "}"),
        lines(
            "/**",
            " * @fileoverview",
            " * @closureUnaware",
            " */",
            "goog.module('foo.bar.baz_raw');",
            "if (goog.DEBUG) {",
            "  $jscomp_wrap_closure_unaware_code('{window[\"foo\"]=5}');",
            "} else {",
            "  $jscomp_wrap_closure_unaware_code('{window[\"foo\"]=10}');",
            "}"));
  }

  @Test
  public void testConditionalAndDebugSrcLoad() {
    doTest(
        lines(
            "/**",
            " * @fileoverview",
            " * @closureUnaware",
            " */",
            "goog.module('foo.bar.baz_raw');",
            "if (!window['foo']) {",
            "  if (goog.DEBUG) {",
            "    /** @closureUnaware */",
            "    (function() {",
            "      window['foo'] = 5;",
            "    }).call(globalThis);",
            "  } else {",
            "    /** @closureUnaware */",
            "    (function() {",
            "       window['foo'] = 10;",
            "    }).call(globalThis);",
            "  }",
            "}"),
        lines(
            "/**",
            " * @fileoverview",
            " * @closureUnaware",
            " */",
            "goog.module('foo.bar.baz_raw');",
            "if (!window['foo']) {",
            "  if (goog.DEBUG) {",
            "    $jscomp_wrap_closure_unaware_code('{window[\"foo\"]=5}');",
            "  } else {",
            "    $jscomp_wrap_closure_unaware_code('{window[\"foo\"]=10}');",
            "  }",
            "}"));
  }

  @Test
  public void testDirectLoad_nestedChangeScopes() {
    doTest(
        lines(
            "/**",
            " * @fileoverview",
            " * @closureUnaware",
            " */",
            "goog.module('foo.bar.baz_raw');",
            "if (goog.DEBUG) {",
            "  /** @closureUnaware */",
            "  (function() {",
            "    function bar() {",
            "      window['foo'] = 5;",
            "    }",
            "    bar();",
            "  }).call(globalThis);",
            "}"),
        lines(
            "/**",
            " * @fileoverview",
            " * @closureUnaware",
            " */",
            "goog.module('foo.bar.baz_raw');",
            "if (goog.DEBUG) {",
            "  $jscomp_wrap_closure_unaware_code('{function" + " bar(){window[\"foo\"]=5}bar()}');",
            "}"));
  }

  @Test
  public void testDirectLoad_nestedGlobalThisIIFEIsNotRewritten() {
    doTest(
        lines(
            "/**",
            " * @fileoverview",
            " * @closureUnaware",
            " */",
            "goog.module('foo.bar.baz_raw');",
            "if (goog.DEBUG) {",
            "  /** @closureUnaware */",
            "  (function() {",
            "    (function() {",
            "      window['foo'] = 10;",
            "    }).call(globalThis);",
            "  }).call(globalThis);",
            "}"),
        lines(
            "/**",
            " * @fileoverview",
            " * @closureUnaware",
            " */",
            "goog.module('foo.bar.baz_raw');",
            "if (goog.DEBUG) {",
            "  $jscomp_wrap_closure_unaware_code('{(function(){window[\"foo\"]=10}).call(globalThis)}');",
            "}"));
  }

  @Test
  public void testUnwrap_doesNotEmitParseErrorsThatShouldBeSuppressed() {
    runWrapPass = false;
    runUnwrapPass = true;
    languageInOverride = Optional.of(CompilerOptions.LanguageMode.ECMASCRIPT_2015);

    testNoWarning(
        lines(
            "/** @fileoverview @closureUnaware */",
            "goog.module('foo.bar.baz_raw');",
            "$jscomp_wrap_closure_unaware_code('{class C { foo = \"bar\"; } }');"));
  }

  @Test
  public void testErrorsOnUnwrapping_nonCallRef() {
    runWrapPass = false; // This error only occurs when unwrapping
    testError(
        lines(
            "/**",
            " * @fileoverview",
            " * @closureUnaware",
            " */",
            "goog.module('foo.bar.baz_raw');",
            "var x = $jscomp_wrap_closure_unaware_code;",
            "x('window[\"foo\"]=5')"),
        ManageClosureUnawareCode.UNEXPECTED_JSCOMPILER_CLOSURE_UNAWARE_PRESERVE);
  }

  @Test
  public void testErrorsOnUnwrapping_invalidCallRef_tooManyArgs() {
    runWrapPass = false; // This error only occurs when unwrapping
    testError(
        lines(
            "/**",
            " * @fileoverview",
            " * @closureUnaware",
            " */",
            "goog.module('foo.bar.baz_raw');",
            "$jscomp_wrap_closure_unaware_code(this, 'window[\"foo\"]=5')"),
        ManageClosureUnawareCode.UNEXPECTED_JSCOMPILER_CLOSURE_UNAWARE_PRESERVE);
  }

  @Test
  public void testErrorsOnUnwrapping_invalidCallRef_ignoresOtherCalls() {
    runWrapPass = false; // This error only occurs when unwrapping
    testNoWarning(
        lines(
            "/**",
            " * @fileoverview",
            " * @closureUnaware",
            " */",
            "goog.module('foo.bar.baz_raw');",
            "eval(this, 'window[\"foo\"]=5')"));
  }

  @Test
  public void testErrorsOnUnwrapping_invalidCallRef_wrongArgType() {
    runWrapPass = false; // This error only occurs when unwrapping
    testError(
        lines(
            "/**",
            " * @fileoverview",
            " * @closureUnaware",
            " */",
            "goog.module('foo.bar.baz_raw');",
            "$jscomp_wrap_closure_unaware_code(5)"),
        ManageClosureUnawareCode.UNEXPECTED_JSCOMPILER_CLOSURE_UNAWARE_PRESERVE);
  }

  @Test
  public void testAllowsSpecifyingAnnotationOnIIFE() {
    doTest(
        lines(
            "/**",
            " * @fileoverview",
            " * @closureUnaware",
            " */",
            "goog.module('foo.bar.baz_raw');",
            "/** @closureUnaware */",
            "(function() {",
            "  window['foo'] = 5;",
            "}).call(globalThis);"),
        lines(
            "/**",
            " * @fileoverview",
            " * @closureUnaware",
            " */",
            "goog.module('foo.bar.baz_raw');",
            "$jscomp_wrap_closure_unaware_code('{window[\"foo\"]=5}')"));
  }
}
