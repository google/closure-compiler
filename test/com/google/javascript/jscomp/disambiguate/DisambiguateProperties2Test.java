/*
 * Copyright 2008 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.disambiguate;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.WarningsGuard;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit test for the {@link DisambiguateProperties2} pass.
 *
 * <p>This also runs the typechecking passes, because DisambiguateProperties2 depends on the
 * typecheck passes behavior, and it's complicated to manually mimic the results of typechecking.
 */
@RunWith(JUnit4.class)
public final class DisambiguateProperties2Test extends CompilerTestCase {

  private static final String PROP_DEFINER_DEFINITION =
      lines(
          "/** @const */ var goog = {};",
          "/** @const */ goog.reflect = {};",
          "",
          "/** @return {string} */",
          "goog.reflect.objectProperty = function(prop, obj) { return ''; };");

  private ImmutableSet<String> propertiesThatMustDisambiguate = ImmutableSet.of();

  public DisambiguateProperties2Test() {
    super("");
  }

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    this.enableTypeCheck();
    replaceTypesWithColors();
    disableCompareJsDoc();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new DisambiguateProperties2(compiler, propertiesThatMustDisambiguate);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.addWarningsGuard(new SilenceNoiseGuard());
    return options;
  }

  @Test
  public void propertiesAreConflated_byExtends_fromInterface() {
    test(
        srcs(
            lines(
                "/** @interface */", //
                "class IFoo {",
                "  x() { }",
                "}",
                "",
                "/**",
                " * @interface",
                " * @extends {IFoo}",
                " */",
                "class IFoo2 {",
                "  x() { }",
                "}",
                "",
                "class Other {",
                "  x() { }",
                "}")),
        expected(
            lines(
                "/** @interface */", //
                "class IFoo {",
                "  JSC$3_x() { }",
                "}",
                "",
                "/**",
                " * @interface",
                " * @extends {IFoo}",
                " */",
                "class IFoo2 {",
                "  JSC$3_x() { }",
                "}",
                "",
                "class Other {",
                "  JSC$5_x() { }",
                "}")));
  }

  @Test
  public void propertiesAreConflated_byExtends_fromClass() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  y() { }",
                "}",
                "",
                "/** @extends {Foo} */",
                "class Foo2 {",
                "  y() { }",
                "}",
                "",
                "class Other {",
                "  y() { }",
                "}")),
        expected(
            lines(
                "class Foo {",
                "  JSC$1_y() { }",
                "}",
                "",
                "/** @extends {Foo} */",
                "class Foo2 {",
                "  JSC$1_y() { }",
                "}",
                "",
                "class Other {",
                "  JSC$5_y() { }",
                "}")));
  }

  @Test
  public void propertiesAreConflated_byExtends_viaPrototype() {
    test(
        srcs(
            lines(
                "/** @constructor */",
                "function Foo() { };",
                "/** @type {number} */",
                "Foo.z = 0;",
                "",
                "class Foo2 extends Foo {",
                "  static z() { }",
                "}",
                "",
                "class Other {",
                "  z() { }",
                "}")),
        expected(
            lines(
                "/** @constructor */",
                "function Foo() { };",
                "/** @type {number} */",
                "Foo.JSC$1_z = 0;",
                "",
                "class Foo2 extends Foo {",
                "  static JSC$1_z() { }",
                "}",
                "",
                "class Other {",
                "  JSC$3_z() { }",
                "}")));
  }

  @Test
  public void propertiesAreConflated_byImplements_fromInterface() {
    test(
        srcs(
            lines(
                "/** @interface */",
                "class IFoo {",
                "  a() { }",
                "}",
                "",
                "/** @implements {IFoo} */",
                "class Foo {",
                "  a() { }",
                "}",
                "",
                "class Other {",
                "  a() { }",
                "}")),
        expected(
            lines(
                "/** @interface */",
                "class IFoo {",
                "  JSC$3_a() { }",
                "}",
                "",
                "/** @implements {IFoo} */",
                "class Foo {",
                "  JSC$3_a() { }",
                "}",
                "",
                "class Other {",
                "  JSC$5_a() { }",
                "}")));
  }

  @Test
  public void propertiesAreConflated_byCommonDescendent() {
    test(
        srcs(
            lines(
                "/** @interface */",
                "class IFoo0 {",
                "  b() { }",
                "  c() { }",
                "}",
                "",
                "/** @interface */",
                "class IFoo1 {",
                "  b() { }",
                "  d() { }",
                "}",
                "",
                "/**",
                " * @implements {IFoo0}",
                " * @implements {IFoo1}",
                " */",
                "class Foo {",
                "  b() { }",
                "}",
                "",
                "class Other {",
                "  b() { }",
                "  c() { }",
                "  d() { }",
                "}")),
        expected(
            lines(
                "/** @interface */",
                "class IFoo0 {",
                "  JSC$5_b() { }",
                "  JSC$1_c() { }",
                "}",
                "",
                "/** @interface */",
                "class IFoo1 {",
                "  JSC$5_b() { }",
                "  JSC$3_d() { }",
                "}",
                "",
                "/**",
                " * @implements {IFoo0}",
                " * @implements {IFoo1}",
                " */",
                "class Foo {",
                "  JSC$5_b() { }",
                "}",
                "",
                "class Other {",
                "  JSC$7_b() { }",
                "  JSC$7_c() { }",
                "  JSC$7_d() { }",
                "}")));
  }

  @Test
  public void propertiesAreConflated_betweenDistantAncestors() {
    test(
        srcs(
            lines(
                "class Foo0 {",
                "  f() { }",
                "}",
                "",
                "class Foo1 extends Foo0 { }",
                "class Foo2 extends Foo1 { }",
                "class Foo3 extends Foo2 { }",
                "class Foo4 extends Foo3 { }",
                "",
                "class Foo5 extends Foo4 {",
                "  f() { }",
                "}",
                "",
                "class Other {",
                "  f() { }",
                "}")),
        expected(
            lines(
                "class Foo0 {",
                "  JSC$1_f() { }",
                "}",
                "",
                "class Foo1 extends Foo0 { }",
                "class Foo2 extends Foo1 { }",
                "class Foo3 extends Foo2 { }",
                "class Foo4 extends Foo3 { }",
                "",
                "class Foo5 extends Foo4 {",
                "  JSC$1_f() { }",
                "}",
                "",
                "class Other {",
                "  JSC$9_f() { }",
                "}")));
  }

  @Test
  public void propertiesAreConflated_byUnions_iffUnionUsesProperty() {
    test(
        srcs(
            lines(
                "class Foo0 {",
                "  a() { }",
                "  b() { }",
                "}",
                "",
                "class Foo1 {",
                "  a() { }",
                "  b() { }",
                "}",
                "",
                "function mix(/** (!Foo0|!Foo1) */ x) {",
                "  x.a();",
                "}",
                "",
                "class Other {",
                "  a() { }",
                "  b() { }",
                "}")),
        expected(
            lines(
                "class Foo0 {",
                "  JSC$5_a() { }",
                "  JSC$1_b() { }",
                "}",
                "",
                "class Foo1 {",
                "  JSC$5_a() { }",
                "  JSC$3_b() { }",
                "}",
                "",
                "function mix(/** (!Foo0|!Foo1) */ x) {",
                "  x.JSC$5_a();",
                "}",
                "",
                "class Other {",
                "  JSC$6_a() { }",
                "  JSC$6_b() { }",
                "}")));
  }

  @Test
  public void propertiesAreConflated_betweenUnionAncestors_andTypesMismatchedWithTheUnion() {
    testSame(
        srcs(
            lines(
                "class Foo0 {",
                "  a() { }",
                "}",
                "class Foo1 extends Foo0 { }",
                "class Foo2 extends Foo0 { }",
                "",
                "class Bar {",
                "  a() { }",
                "}",
                "",
                "/** @type {(!Foo1|!Foo2)} @suppress {checkTypes} */",
                "const x = new Bar();",
                "",
                "class Other {",
                "  a() { }",
                "}")));
  }

  @Test
  public void propertiesAreConflated_acrossTemplateSpecializations() {
    test(
        srcs(
            lines(
                "/** @template T */",
                "class Foo {",
                "  h() { }",
                "}",
                "",
                "const /** !Foo<string> */ a = new Foo();",
                "a.h();",
                "const /** !Foo<?> */ b = new Foo();",
                "b.h();",
                "const /** !Foo */ c = new Foo();",
                "c.h();",
                "",
                "class Other {",
                "  h() { }",
                "}")),
        expected(
            lines(
                "/** @template T */",
                "class Foo {",
                "  JSC$1_h() { }",
                "}",
                "",
                "const /** !Foo<string> */ a = new Foo();",
                "a.JSC$1_h();",
                "const /** !Foo<?> */ b = new Foo();",
                "b.JSC$1_h();",
                "const /** !Foo */ c = new Foo();",
                "c.JSC$1_h();",
                "",
                "class Other {",
                "  JSC$4_h() { }",
                "}")));
  }

  @Test
  public void propertiesAreConflated_betweenEnumsAndTheirValues() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  p() { }",
                "}",
                "",
                "/** @enum {!Foo} */",
                "const Bar = {",
                "  ONE: new Foo(),",
                "};",
                "",
                "Bar.ONE.p",
                "",
                "class Other {",
                "  p() { }",
                "}")),
        expected(
            lines(
                "class Foo {",
                "  JSC$1_p() { }",
                "}",
                "",
                "/** @enum {!Foo} */",
                "const Bar = {",
                "  ONE: new Foo(),",
                "};",
                "",
                "Bar.ONE.JSC$1_p",
                "",
                "class Other {",
                "  JSC$5_p() { }",
                "}")));
  }

  @Test
  public void propertiesAreConflated_acrossNullableAndVoidableReferences() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  g() { }",
                "}",
                "",
                "function use(/** (null|undefined|!Foo) */ x) {",
                "  x.g()",
                "}",
                "",
                "class Other {",
                "  g() { }",
                "}")),
        expected(
            lines(
                "class Foo {",
                "  JSC$1_g() { }",
                "}",
                "",
                "function use(/** (null|undefined|!Foo) */ x) {",
                "  x.JSC$1_g()",
                "}",
                "",
                "class Other {",
                "  JSC$4_g() { }",
                "}")));
  }

  @Test
  public void propertiesAreDisambiguated_betweenContructorAndInstanceTypes() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  static g() { }",
                "  g() { }",
                "}")),
        expected(
            lines(
                "class Foo {", //
                "  static JSC$1_g() { }",
                "  JSC$2_g() { }",
                "}")));
  }

  @Test
  public void propertiesAreDisambiguated_betweenAncestorTypes_ifCommonChildHasNoPropUse() {
    test(
        srcs(
            lines(
                "/** @interface */",
                "class IFoo {",
                "  t() { }",
                "}",
                "class Foo {",
                "  t() { }",
                "}",
                "/** @implements {IFoo} */",
                "class SubFoo extends Foo { }",
                "",
                "class Other {",
                "  t() { }",
                "}")),
        expected(
            lines(
                "/** @interface */",
                "class IFoo {",
                "  JSC$3_t() { }",
                "}",
                "class Foo {",
                "  JSC$3_t() { }",
                "}",
                "/** @implements {IFoo} */",
                "class SubFoo extends Foo { }",
                "",
                "class Other {",
                "  JSC$6_t() { }",
                "}")));
  }

  @Test
  public void propertiesAreInvalidated_byToplikeAndBottomlikeTypes() {
    ImmutableSet<String> annotations =
        ImmutableSet.of(
            "?", "*", "!null", "(?|undefined)", "null", "undefined", "(null|undefined)");

    for (String annotation : annotations) {
      test(
          srcs(
              lines(
                  "class Foo0 {",
                  "  k() { }",
                  "  t() { }",
                  "}",
                  "class Foo1 {",
                  "  k() { }",
                  "  t() { }",
                  "}",
                  "",
                  "function mix(/** " + annotation + " */ x) {",
                  "  x.k();",
                  "}")),
          expected(
              lines(
                  "class Foo0 {",
                  "  k() { }",
                  "  JSC$1_t() { }",
                  "}",
                  "class Foo1 {",
                  "  k() { }",
                  "  JSC$3_t() { }",
                  "}",
                  "",
                  "function mix(/** " + annotation + " */ x) {",
                  "  x.k();",
                  "}")));
    }
  }

  @Test
  public void propertiesAreInvalidated_onObjectLiteralTypes() {
    testSame(
        srcs(
            lines(
                "const z = {ab: 0};", //
                "",
                "class Other {",
                "  ab() { }",
                "}")));
  }

  @Test
  public void propertiesAreDisambiguated_acrossStructuralTypeMatches_iffMatchUsed() {
    testSame(
        srcs(
            lines(
                "/** @record */",
                "class Foo0 {",
                "  a() { }",
                "}",
                "/** @record */",
                "class Foo1 extends Foo0 {",
                "  a() { }",
                "  b() { }",
                "}",
                "class Foo2 {",
                "  a() { }",
                "  b() { }",
                "}",
                "",
                "const /** !Foo0 */ x = new Foo2();",
                "",
                "class Other {",
                " a() { }",
                "}")));
  }

  @Test
  public void propertiesAreInvalidated_byAnonymousFunctionTypes() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  static g() { }",
                "  static j() { }",
                "}",
                "",
                "function use(/** function() */ x) {",
                "  x.g;",
                "}",
                "",
                "class Other {",
                "  j() { }",
                "}")),
        expected(
            lines(
                "class Foo {",
                "  static g() { }",
                "  static JSC$1_j() { }",
                "}",
                "",
                "function use(/** function() */ x) {",
                "  x.g;",
                "}",
                "",
                "class Other {",
                "  JSC$3_j() { }",
                "}")));
  }

  @Test
  public void propertiesAreDisambiguated_acrossTypeMismatches() {
    testSame(
        srcs(
            lines(
                "class Foo0 {",
                "  a() { }",
                "}",
                "class Foo1 extends Foo0 {",
                "  a() { }",
                "  b() { }",
                "}",
                "class Foo2 {",
                "  a() { }",
                "  b() { }",
                "}",
                "",
                "const /** !Foo0 */ x = new Foo2();",
                "",
                "class Other {",
                " a() { }",
                "}")));
  }

  @Test
  public void propertiesAreNotRenamed_fromExternsClusters() {
    test(
        externs(
            lines(
                "class ExternFoo {", //
                "  v() { }",
                "}")),
        srcs(
            lines(
                "class Foo extends ExternFoo {",
                "  v() { }",
                "  p() { }",
                "}",
                "",
                "class Other {",
                "  v() { }",
                "  p() { }",
                "}")),
        expected(
            lines(
                "class Foo extends ExternFoo {",
                "  v() { }",
                "  JSC$3_p() { }",
                "}",
                "",
                "class Other {",
                "  JSC$5_v() { }",
                "  JSC$5_p() { }",
                "}")));
  }

  @Test
  public void propertiesAreNotRenamed_ifTheyHaveASingleCluster() {
    test(
        srcs(
            lines(
                "class Foo0 {",
                "  w() { }",
                "  x() { }",
                "}",
                "class Foo1 extends Foo0 {",
                "  w() { }",
                "  y() { }",
                "}",
                "class Foo2 {",
                "  w() { }",
                "  z() { }",
                "}",
                "",
                "function mix(/** (!Foo1|!Foo2) */ x) {",
                "  x.w();",
                "}",
                "",
                "class Other {",
                "  x() { }",
                "  y() { }",
                "  z() { }",
                "}")),
        expected(
            lines(
                "class Foo0 {",
                "  w() { }",
                "  JSC$1_x() { }",
                "}",
                "class Foo1 extends Foo0 {",
                "  w() { }",
                "  JSC$3_y() { }",
                "}",
                "class Foo2 {",
                "  w() { }",
                "  JSC$5_z() { }",
                "}",
                "",
                "function mix(/** (!Foo1|!Foo2) */ x) {",
                "  x.w();",
                "}",
                "",
                "class Other {",
                "  JSC$8_x() { }",
                "  JSC$8_y() { }",
                "  JSC$8_z() { }",
                "}")));
  }

  @Test
  public void propertiesAreInvalidated_givenMissingPropertyError() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  x() { }",
                "  y() { }",
                "  z() { }",
                "}",
                "class Bar { }",
                "",
                "function mix(/** (!Foo|!Bar) */ fooBar, /** !Bar */ bar) {",
                // Invalidate bar.y() but not fooBar.x(). This mirrors the Closure typechecker,
                // which thinks (!Foo|!Bar) may have an 'x' property and so allows the access.
                "  fooBar.w();",
                "  fooBar.x();",
                "  bar.y();",
                "}",
                "",
                "class Other {",
                "  w() { }",
                "  x() { }",
                "  y() { }",
                "  z() { }",
                "}")),
        expected(
            lines(
                "class Foo {",
                "  JSC$4_x() { }",
                "  y() { }",
                "  JSC$1_z() { }",
                "}",
                "class Bar { }",
                "",
                "function mix(/** (!Foo|!Bar) */ fooBar, /** !Bar */ bar) {",
                // x is disambiguated while w and y are invalidated.
                "  fooBar.w();",
                "  fooBar.JSC$4_x();",
                "  bar.y();",
                "}",
                "",
                "class Other {",
                "  w() { }",
                "  JSC$6_x() { }",
                "  y() { }",
                "  JSC$6_z() { }",
                "}")));
  }

  @Test
  public void propertiesAreInvalidated_ifUsedOnType_butNotDeclaredOnAncestor() {
    test(
        srcs(
            lines(
                "/** @interface */",
                "class IFoo {",
                "  a() { }",
                "}",
                "",
                "/** @implements {IFoo} */",
                "class Foo {",
                "  b() { }",
                "}",
                "",
                "new Foo().a;",
                "new Foo().b;",
                "new Foo().c;",
                "",
                "class Other {",
                "  a() { }",
                "  b() { }",
                "  c() { }",
                "}")),
        expected(
            lines(
                "/** @interface */",
                "class IFoo {",
                "  JSC$1_a() { }",
                "}",
                "",
                "/** @implements {IFoo} */",
                "class Foo {",
                "  JSC$3_b() { }",
                "}",
                "",
                "new Foo().JSC$1_a;",
                "new Foo().JSC$3_b;",
                "new Foo().c;",
                "",
                "class Other {",
                "  JSC$6_a() { }",
                "  JSC$6_b() { }",
                "  c() { }",
                "}")));
  }

  @Test
  public void propertiesReferenced_throughReflectorFunctions_areRenamed() {
    test(
        externs(PROP_DEFINER_DEFINITION),
        srcs(
            lines(
                "class Foo {",
                "  m() { }",
                "}",
                "",
                "class Other {",
                "  m() { }",
                "}",
                "",
                "goog.reflect.objectProperty('m', Foo.prototype);")),
        expected(
            lines(
                "class Foo {",
                "  JSC$3_m() { }",
                "}",
                "",
                "class Other {",
                "  JSC$5_m() { }",
                "}",
                "",
                "goog.reflect.objectProperty('JSC$3_m', Foo.prototype);")));
  }

  @Test
  public void errorReported_forInvalidation_ofSpecifiedPropNames_oncePerName() {
    this.allowSourcelessWarnings();
    this.propertiesThatMustDisambiguate =
        ImmutableSet.of(
            "mustDisambiguate0", //
            "mustDisambiguate1");

    test(
        srcs(
            lines(
                "class Foo {",
                "  mustDisambiguate0() { }",
                "  mustDisambiguate1() { }",
                "}",
                "",
                "function use(/** ? */ x) {",
                "  x.mustDisambiguate0",
                "  x.mustDisambiguate1",
                "  x.mustDisambiguate0",
                "  x.mustDisambiguate1",
                "}")),
        error(DisambiguateProperties2.PROPERTY_INVALIDATION),
        error(DisambiguateProperties2.PROPERTY_INVALIDATION));
  }

  @Test
  public void invalidatingSubtype_doesNotInvalidatePropertyOnlyReferencedOnSupertype() {
    // TODO(b/135045845): track mismatches through subtypes/supertypes
    test(
        srcs(
            lines(
                "class FooParent {",
                "  parent() {}",
                "}",
                "",
                "class FooChild extends FooParent {",
                "  child() {}",
                "}",
                "",
                "class BarParent {",
                "  parent() {}",
                "}",
                "",
                "class BarChild extends BarParent {",
                "  child() {}",
                "}",
                "",
                "/** ",
                " * @suppress {checkTypes} intentional type error",
                " * @type {!FooChild}",
                " */",
                "const fooChild = '';")),
        expected(
            lines(
                "class FooParent {",
                "  JSC$1_parent() {}",
                "}",
                "",
                "class FooChild extends FooParent {",
                "  child() {}",
                "}",
                "",
                "class BarParent {",
                "  JSC$5_parent() {}",
                "}",
                "",
                "class BarChild extends BarParent {",
                "  child() {}",
                "}",
                "",
                "/** ",
                " * @suppress {checkTypes} intentional type error",
                " * @type {!FooChild}",
                " */",
                "const fooChild = '';")));
  }

  private static final class SilenceNoiseGuard extends WarningsGuard {
    private static final ImmutableSet<DiagnosticType> RELEVANT_DIAGNOSTICS =
        ImmutableSet.of(DisambiguateProperties2.PROPERTY_INVALIDATION);

    @Override
    protected int getPriority() {
      return WarningsGuard.Priority.MAX.getValue();
    }

    @Override
    public CheckLevel level(JSError error) {
      if (error.getDescription().contains("Parse")) {
        return null;
      } else if (RELEVANT_DIAGNOSTICS.contains(error.getType())) {
        return null;
      } else {
        return CheckLevel.OFF;
      }
    }
  }

  private void generateDiagnosticFiles() {
    test(
        srcs(
            lines(
                "class Foo0 {",
                "  w() { }",
                "  x() { }",
                "}",
                "class Foo1 extends Foo0 {",
                "  w() { }",
                "  y() { }",
                "}",
                "class Foo2 {",
                "  w() { }",
                "  z() { }",
                "}",
                "class Bar {}",
                "function use(/** (!Foo0|!Foo2) */ x, /** !Bar */ y) {",
                "  x.x();",
                "  y.y();",
                "}")));
  }

  @GwtIncompatible
  private static String loadFile(Path path) {
    try (Stream<String> lines = Files.lines(path)) {
      return lines.collect(joining("\n"));
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  @GwtIncompatible
  private ImmutableList<Path> debugLogFiles() {
    try {
      Path dir =
          Paths.get(
              this.getLastCompiler().getOptions().getDebugLogDirectory().toString(),
              DisambiguateProperties2.class.getSimpleName());

      try (Stream<Path> files = Files.list(dir)) {
        return files.collect(toImmutableList());
      }
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private static void assertValidJson(String src) {
    assertThat(src).isNotEmpty();

    Class<?> clazz = (src.charAt(0) == '{') ? LinkedHashMap.class : ArrayList.class;
    new Gson().fromJson(src, clazz); // Throws if invalid
  }
}
