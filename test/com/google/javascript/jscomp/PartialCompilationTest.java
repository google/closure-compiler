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
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.NamedType;
import com.google.javascript.rhino.jstype.NoType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.UnionType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests exercising {@link CompilerOptions#assumeForwardDeclaredForMissingTypes} and {@link
 * DiagnosticGroups#MISSING_SOURCES_WARNINGS}.
 */
@RunWith(JUnit4.class)
public class PartialCompilationTest {

  private Compiler compiler;

  /**
   * Asserts that the given lines of code compile and only give errors matching the {@link
   * DiagnosticGroups#MISSING_SOURCES_WARNINGS} category.
   */
  private void assertPartialCompilationSucceeds(String... code) throws Exception {
    compiler = new Compiler();
    compiler.setErrorManager(
        new BasicErrorManager() {
          @Override
          public void report(CheckLevel level, JSError error) {
            super.report(CheckLevel.ERROR, error);
          }

          @Override
          public void println(CheckLevel level, JSError error) {
            /* no-op */
          }

          @Override
          protected void printSummary() {
            /* no-op */
          }
        });
    CompilerOptions options = new CompilerOptions();
    options.setAssumeForwardDeclaredForMissingTypes(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2015);
    options.setStrictModeInput(true);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setPreserveDetailedSourceInfo(true);
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    compiler.init(
        ImmutableList.of(),
        Collections.singletonList(SourceFile.fromCode("input.js", Joiner.on('\n').join(code))),
        options);
    compiler.parse();
    compiler.check();
    List<JSError> errors = Arrays.asList(compiler.getErrors());
    for (JSError error : errors) {
      if (!DiagnosticGroups.MISSING_SOURCES_WARNINGS.matches(error)) {
        assertWithMessage("Unexpected error " + error).fail();
      }
    }
  }

  @Test
  public void testUsesMissingCode() throws Exception {
    assertPartialCompilationSucceeds(
        "goog.provide('missing_code_user');",
        "goog.require('some.thing.Missing');",
        "missing_code_user.fnUsesMissingNs = function() {",
        "  missing_code_user.missingNamespace.foo();",
        "  missingTopLevelNamespace.bar();",
        "};");
  }

  @Test
  public void testMissingType_variable() throws Exception {
    assertPartialCompilationSucceeds("/** @type {!some.thing.Missing} */ var foo;");
  }

  @Test
  public void testMissingType_assignment() throws Exception {
    assertPartialCompilationSucceeds(
        "/** @type {!some.thing.Missing} */ var foo;", // line break
        "/** @type {number} */ var bar = foo;");
  }

  @Test
  public void testMissingRequire() throws Exception {
    assertPartialCompilationSucceeds(
        "goog.provide('missing_extends');", // line break
        "goog.require('some.thing.Missing');");
  }

  @Test
  public void testMissingExtends() throws Exception {
    assertPartialCompilationSucceeds(
        "goog.provide('missing_extends');",
        "/** @constructor @extends {some.thing.Missing} */",
        "missing_extends.Extends = function() {}");
  }

  @Test
  public void testMissingExtends_template() throws Exception {
    assertPartialCompilationSucceeds(
        "goog.provide('missing_extends');",
        "/** @constructor @extends {some.thing.Missing<string>} x */",
        "missing_extends.Extends = function() {}");
  }

  @Test
  public void testMissingType_typedefAlias() throws Exception {
    assertPartialCompilationSucceeds("/** @typedef {string} */ var typedef;");
  }

  @Test
  public void testMissingType_typedefField() throws Exception {
    assertPartialCompilationSucceeds("/** @typedef {some.thing.Missing} */ var typedef;");
  }

  @Test
  public void testMissingEs6Externs() throws Exception {
    assertPartialCompilationSucceeds("let foo = {a, b};");
  }

  @Test
  public void testUnresolvedGenerics() throws Exception {
    assertPartialCompilationSucceeds(
        "/** @type {!some.thing.Missing<string, !AlsoMissing<!More>>} */", "var x;");
    TypedVar x = compiler.getTopScope().getSlot("x");
    assertThat(x.getType().isNoResolvedType()).named("type %s", x.getType()).isTrue();
    NoType templatizedType = (NoType) x.getType();
    assertThat(templatizedType.getReferenceName()).isEqualTo("some.thing.Missing");
    ImmutableList<JSType> templateTypes = templatizedType.getTemplateTypes();
    assertThat(templateTypes.get(0).isString()).isTrue();
    assertThat(templateTypes.get(1).isObject()).isTrue();
    ObjectType alsoMissing = (ObjectType) templateTypes.get(1);
    assertThat(alsoMissing.getReferenceName()).isEqualTo("AlsoMissing");
    assertThat(alsoMissing.getTemplateTypes()).hasSize(1);
    ObjectType more = (ObjectType) alsoMissing.getTemplateTypes().get(0);
    assertThat(more.getReferenceName()).isEqualTo("More");
  }

  @Test
  public void testUnresolvedUnions() throws Exception {
    assertPartialCompilationSucceeds("/** @type {some.thing.Foo|some.thing.Bar} */", "var x;");
    TypedVar x = compiler.getTopScope().getSlot("x");
    assertThat(x.getType().isUnionType()).named("type %s", x.getType()).isTrue();
    UnionType unionType = (UnionType) x.getType();

    Collection<JSType> alternatives = unionType.getAlternates();
    assertThat(alternatives).hasSize(3);

    int nullTypeCount = 0;
    List<String> namedTypes = new ArrayList<>();
    for (JSType alternative : alternatives) {
      assertThat(alternative.isNamedType() || alternative.isNullType()).isTrue();
      if (alternative.isNamedType()) {
        assertThat(alternative.isNoResolvedType()).isTrue();
        namedTypes.add(((NamedType) alternative).getReferenceName());
      }
      if (alternative.isNullType()) {
        nullTypeCount++;
      }
    }
    assertThat(nullTypeCount).isEqualTo(1);
    assertThat(namedTypes).containsExactly("some.thing.Foo", "some.thing.Bar");
  }

  @Test
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

  @Test
  public void testUnresolvedBaseClassDoesNotHideFields() throws Exception {
    assertPartialCompilationSucceeds(
        "/** @constructor @extends {MissingBase} */",
        "var Klass = function () {",
        "  /** @type {string} */",
        "  this.foo;",
        "};");
    TypedVar x = compiler.getTopScope().getSlot("Klass");
    JSType type = x.getType();
    assertThat(type.isFunctionType()).isTrue();

    FunctionType fType = (FunctionType) type;
    assertThat(fType.getTypeOfThis().hasProperty("foo")).isTrue();
  }
}
