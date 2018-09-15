/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test case for {@link DartSuperAccessorsPass}.
 *
 * @author ochafik@google.com (Olivier Chafik)
 */
@RunWith(JUnit4.class)
public final class DartSuperAccessorsPassTest extends CompilerTestCase {

  /** Signature of the member functions / accessors we'll wrap expressions into. */
  private static final ImmutableList<String> MEMBER_SIGNATURES = ImmutableList.of(
      "constructor()",
      "method()", "*generator()", "get prop()", "set prop(v)",
      // ES6 Computed properties:
      "[method]()", "*[generator]()", "get [prop]()", "set [prop](v)");

  private PropertyRenamingPolicy propertyRenaming;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    enableRunTypeCheckAfterProcessing();
    propertyRenaming = PropertyRenamingPolicy.ALL_UNQUOTED;
  }

  @Override
  protected Compiler createCompiler() {
    return new NoninjectingCompiler();
  }

  @Override
  protected NoninjectingCompiler getLastCompiler() {
    return (NoninjectingCompiler) super.getLastCompiler();
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setDartPass(true);
    options.setAmbiguateProperties(false);
    options.setDisambiguateProperties(false);
    options.setPropertyRenaming(propertyRenaming);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new DartSuperAccessorsPass(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Test
  public void testSuperGetElem() {
    checkConversionWithinMembers(
        "return super['prop']",
        "return $jscomp.superGet(this, 'prop')");
    assertThat(getLastCompiler().injected).containsExactly("es6_dart_runtime");
  }

  @Test
  public void testSuperGetProp_renameOff() {
    propertyRenaming = PropertyRenamingPolicy.OFF;
    checkConversionWithinMembers(
        "return super.prop",
        "return $jscomp.superGet(this, 'prop')");
  }

  @Test
  public void testSuperGetProp_renameAll() {
    propertyRenaming = PropertyRenamingPolicy.ALL_UNQUOTED;
    checkConversionWithinMembers(
        "return super.prop",
        "return $jscomp.superGet(this, JSCompiler_renameProperty('prop'))");
  }

  @Test
  public void testSuperSetElem() {
    checkConversionWithinMembers(
        "super['prop'] = x",
        "$jscomp.superSet(this, 'prop', x)");
  }

  @Test
  public void testSuperSetProp_renameOff() {
    propertyRenaming = PropertyRenamingPolicy.OFF;
    checkConversionWithinMembers(
        "super.prop = x",
        "$jscomp.superSet(this, 'prop', x)");
  }

  @Test
  public void testSuperSetProp_renameAll() {
    propertyRenaming = PropertyRenamingPolicy.ALL_UNQUOTED;
    checkConversionWithinMembers(
        "super.prop = x",
        "$jscomp.superSet(this, JSCompiler_renameProperty('prop'), x)");
  }

  @Test
  public void testSuperSetAssignmentOps() {
    propertyRenaming = PropertyRenamingPolicy.OFF;
    checkConversionWithinMembers(
        "super.a |= b",
        "$jscomp.superSet(this, 'a', $jscomp.superGet(this, 'a') | b)");
    checkConversionWithinMembers(
        "super.a ^= b",
        "$jscomp.superSet(this, 'a', $jscomp.superGet(this, 'a') ^ b)");
    checkConversionWithinMembers(
        "super.a &= b",
        "$jscomp.superSet(this, 'a', $jscomp.superGet(this, 'a') & b)");
    checkConversionWithinMembers(
        "super.a <<= b",
        "$jscomp.superSet(this, 'a', $jscomp.superGet(this, 'a') << b)");
    checkConversionWithinMembers(
        "super.a >>= b",
        "$jscomp.superSet(this, 'a', $jscomp.superGet(this, 'a') >> b)");
    checkConversionWithinMembers(
        "super.a >>>= b",
        "$jscomp.superSet(this, 'a', $jscomp.superGet(this, 'a') >>> b)");
    checkConversionWithinMembers(
        "super.a += b",
        "$jscomp.superSet(this, 'a', $jscomp.superGet(this, 'a') + b)");
    checkConversionWithinMembers(
        "super.a -= b",
        "$jscomp.superSet(this, 'a', $jscomp.superGet(this, 'a') - b)");
    checkConversionWithinMembers(
        "super.a *= b",
        "$jscomp.superSet(this, 'a', $jscomp.superGet(this, 'a') * b)");
    checkConversionWithinMembers(
        "super.a /= b",
        "$jscomp.superSet(this, 'a', $jscomp.superGet(this, 'a') / b)");
    checkConversionWithinMembers(
        "super.a %= b",
        "$jscomp.superSet(this, 'a', $jscomp.superGet(this, 'a') % b)");
  }

  @Test
  public void testSuperSetRecursion() {
    checkConversionWithinMembers(
        "super['x'] = super['y']",
        "$jscomp.superSet(this, 'x', $jscomp.superGet(this, 'y'))");
    checkConversionWithinMembers(
        "super['x'] = super['y'] = 10",
        "$jscomp.superSet(this, 'x', $jscomp.superSet(this, 'y', 10))");
    checkConversionWithinMembers(
        "super['x'] += super['y']",
        "$jscomp.superSet(this, 'x', $jscomp.superGet(this, 'x') + $jscomp.superGet(this, 'y'))");
  }

  @Test
  public void testExpressionsWithoutSuperAccessors() {
    String body = lines(
        "foo.bar;",
        "foo.bar();",
        "this.bar;",
        "this.bar();",
        "super();",
        "super.bar();");

    for (String sig : MEMBER_SIGNATURES) {
      testSame(wrap(sig, body));
    }
  }

  @Test
  public void testSuperAccessorsOutsideInstanceMembers() {
    String body = lines(
        "super.x;",
        "super.x = y;");

    testSame(body);

    for (String sig : MEMBER_SIGNATURES) {
      testSame(wrap("static " + sig, body));
    }
  }

  /**
   * Checks that when the provided {@code js} snippet is inside any kind of instance member
   * function (instance method, getter or setter), it is converted to the {@code expected} snippet.
   */
  private void checkConversionWithinMembers(String js, String expected) {
    for (String sig : MEMBER_SIGNATURES) {
      test(wrap(sig, js), wrap(sig, expected));
    }
  }

  /**
   * Wraps a body (statements) in a member function / accessor with the provided signature.
   * (can be static or not).
   */
  private String wrap(String memberSignature, String body) {
    return lines(
        "class X extends Y {",
        "  " + memberSignature + " {",
        "    " + body,
        "  }",
        "}");
  }
}
