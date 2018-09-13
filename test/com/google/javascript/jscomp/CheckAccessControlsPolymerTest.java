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

import static com.google.javascript.jscomp.CheckAccessControls.BAD_PRIVATE_PROPERTY_ACCESS;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration test to check that {@link PolymerPass} and {@link CheckAccessControls} work together
 * as expected.
 */
@RunWith(JUnit4.class)
public final class CheckAccessControlsPolymerTest extends CompilerTestCase {
  private static final String EXTERNS = lines(
      CompilerTypeTestCase.DEFAULT_EXTERNS,
      "var Polymer = function(descriptor) {};",
      "/** @constructor */",
      "var PolymerElement = function() {};");

  public CheckAccessControlsPolymerTest() {
    super(EXTERNS);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    enableParseTypeInfo();
    enablePolymerPass();
    allowExternsChanges();
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CheckAccessControls(compiler, true);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.ERROR);
    options.setWarningLevel(DiagnosticGroups.CONSTANT_PROPERTY, CheckLevel.ERROR);
    return options;
  }

  @Test
  public void testPrivateMethodInElement() {
    testNoWarning(lines(
        "var AnElement = Polymer({",
        "  is: 'an-element',",
        "",
        "  /** @private */",
        "  foo_: function() {},",
        "  bar: function() { this.foo_(); },",
        "});"));
  }

  @Test
  public void testPrivateMethodInBehavior() {
    testNoWarning(new String[] {
      lines(
        "/** @polymerBehavior */",
        "var Behavior = {",
        "  /** @private */",
        "  foo_: function() {},",
        "  bar: function() { this.foo_(); },",
        "};"),
      lines(
        "var AnElement = Polymer({",
        "  is: 'an-element',",
        "  behaviors: [Behavior],",
        "});")
    });
  }

  @Test
  public void testPrivateMethodFromBehaviorUsedInElement() {
    testError(new String[] {
      lines(
        "/** @polymerBehavior */",
        "var Behavior = {",
        "  /** @private */",
        "  foo_: function() {},",
        "};"),
      lines(
        "var AnElement = Polymer({",
        "  is: 'an-element',",
        "  behaviors: [Behavior],",
        "  bar: function() { this.foo_(); },",
        "});")
    }, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testPrivatePropertyInBehavior() {
    testNoWarning(new String[] {
      lines(
        "/** @polymerBehavior */",
        "var Behavior = {",
        "  /** @private */",
        "  foo_: 'foo',",
        "  bar: function() { alert(this.foo_); },",
        "};"),
      lines(
        "var AnElement = Polymer({",
        "  is: 'an-element',",
        "  behaviors: [Behavior],",
        "});")
    });
  }

  @Test
  public void testPrivatePropertyFromBehaviorUsedInElement() {
    testError(new String[] {
      lines(
        "/** @polymerBehavior */",
        "var Behavior = {",
        "  /** @private */",
        "  foo_: 'foo',",
        "};"),
      lines(
        "var AnElement = Polymer({",
        "  is: 'an-element',",
        "  behaviors: [Behavior],",
        "  bar: function() { alert(this.foo_); },",
        "});")
    }, BAD_PRIVATE_PROPERTY_ACCESS);
  }
}
