/*
 * Copyright 2017 The Closure Compiler Authors.
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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.TemplatizedType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import junit.framework.TestCase;

/**
 * Tests exercising {@link CompilerOptions#assumeForwardDeclaredForMissingTypes} and {@link
 * DiagnosticGroups#MISSING_SOURCES_WARNINGS}.
 */
public class PartialCompilationTest extends TestCase {

  private Compiler compiler;

  public void testUsesMissingCode() throws Exception {
    assertPartialCompilationSucceeds(
        "goog.provide('missing_code_user');",
        "goog.require('some.thing.Missing');",
        "missing_code_user.fnUsesMissingNs = function() {",
        "  missing_code_user.missingNamespace.foo();",
        "  missingTopLevelNamespace.bar();",
        "};");
  }

  public void testMissingType_variable() throws Exception {
    assertPartialCompilationSucceeds(
        "/** @type {!some.thing.Missing} */ var foo;");
  }

  public void testMissingType_assignment() throws Exception {
    assertPartialCompilationSucceeds(
        "/** @type {!some.thing.Missing} */ var foo;",
        "/** @type {number} */ var bar = foo;");
  }

  public void testMissingRequire() throws Exception {
    assertPartialCompilationSucceeds(
        "goog.provide('missing_extends');",
        "goog.require('some.thing.Missing');");
  }

  public void testMissingExtends() throws Exception {
    assertPartialCompilationSucceeds(
        "goog.provide('missing_extends');",
        "/** @constructor @extends {some.thing.Missing} */",
        "missing_extends.Extends = function() {}");
  }

  public void testMissingExtends_template() throws Exception {
    assertPartialCompilationSucceeds(
        "goog.provide('missing_extends');",
        "/** @constructor @extends {some.thing.Missing<string>} x */",
        "missing_extends.Extends = function() {}");
  }

  public void testMissingType_typedefAlias() throws Exception {
    assertPartialCompilationSucceeds(
        "/** @typedef {string} */ var typedef;");
  }

  public void testMissingType_typedefField() throws Exception {
    assertPartialCompilationSucceeds(
        "/** @typedef {some.thing.Missing} */ var typedef;");
  }

  public void testMissingEs6Externs() throws Exception {
    assertPartialCompilationSucceeds("let foo = {a, b};");
  }

  private void assertPartialCompilationSucceeds(String... code) throws Exception {
    compiler = new Compiler();
    compiler.setErrorManager(new BasicErrorManager() {
      @Override
      public void report(CheckLevel level, JSError error) {
        super.report(CheckLevel.ERROR, error);
      }
      @Override
      public void println(CheckLevel level, JSError error) { /* no-op */ }
      @Override
      protected void printSummary() { /* no-op */ }
    });
    CompilerOptions options = new CompilerOptions();
    options.setAssumeForwardDeclaredForMissingTypes(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setStrictModeInput(true);
    options.setPreserveDetailedSourceInfo(true);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    compiler.init(
        Collections.<SourceFile>emptyList(),
        Collections.singletonList(SourceFile.fromCode("input.js", Joiner.on('\n').join(code))),
        options);
    compiler.parse();
    compiler.check();
    List<JSError> errors = Arrays.asList(compiler.getErrors());
    for (JSError error : errors) {
      if (!DiagnosticGroups.MISSING_SOURCES_WARNINGS.matches(error)) {
        fail("Unexpected error " + error);
      }
    }
  }

  public void testUnresolvedGenerics() throws Exception {
    assertPartialCompilationSucceeds(
        "/** @type {!some.thing.Missing<string, !AlsoMissing>} */", "var x;");
    TypedVar x = compiler.getTopScope().getSlot("x");
    assertThat(x.getType().isTemplatizedType()).named("type %s", x.getType()).isTrue();
    TemplatizedType templatizedType = (TemplatizedType) x.getType();
    ObjectType referenced = templatizedType.getReferencedType();
    assertThat(referenced.isNoResolvedType()).isTrue();
    assertThat(referenced.getReferenceName()).isEqualTo("some.thing.Missing");
    ImmutableList<JSType> templateTypes = templatizedType.getTemplateTypes();
    assertThat(templateTypes).isEmpty();
    templateTypes = templatizedType.getUnresolvedTemplateTypes();
    assertThat(templateTypes.get(0).isString()).isTrue();
    assertThat(templateTypes.get(1).isObject()).isTrue();
    assertThat(((ObjectType) templateTypes.get(1)).getReferenceName()).isEqualTo("AlsoMissing");
  }

  public void testUnresolvedGenerics_defined() throws Exception {
    assertPartialCompilationSucceeds(
        "/** @param {!some.thing.Missing<string>} x */",
        "function useMissing(x) {}",
        "/** @const {!some.thing.Missing<string>} */",
        "var x;",
        "/** @constructor @template T */",
        "some.thing.Missing = function () {}",
        "function missingInside() {",
        "  useMissing(new some.thing.Missing());",
        "}");
  }
}
