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

/**
 * Integration test to check that {@link PolymerPass} and {@link CheckAccessControls} work together
 * as expected.
 */
public final class CheckAccessControlsPolymerTest extends TypeICompilerTestCase {
  private static final String EXTERNS = LINE_JOINER.join(
      CompilerTypeTestCase.DEFAULT_EXTERNS,
      "var Polymer = function(descriptor) {};",
      "/** @constructor */",
      "var PolymerElement = function() {};");

  public CheckAccessControlsPolymerTest() {
    super(EXTERNS);
    parseTypeInfo = true;
    enablePolymerPass();
    allowExternsChanges(true);
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

  public void testPrivateMethodInElement() {
    testNoWarning(LINE_JOINER.join(
        "var AnElement = Polymer({",
        "  is: 'an-element',",
        "",
        "  /** @private */",
        "  foo_: function() {},",
        "  bar: function() { this.foo_(); },",
        "});"));
  }

  public void testPrivateMethodInBehavior() {
    testNoWarning(new String[] {
      LINE_JOINER.join(
        "/** @polymerBehavior */",
        "var Behavior = {",
        "  /** @private */",
        "  foo_: function() {},",
        "  bar: function() { this.foo_(); },",
        "};"),
      LINE_JOINER.join(
        "var AnElement = Polymer({",
        "  is: 'an-element',",
        "  behaviors: [Behavior],",
        "});")
    });
  }

  public void testPrivateMethodFromBehaviorUsedInElement() {
    testError(new String[] {
      LINE_JOINER.join(
        "/** @polymerBehavior */",
        "var Behavior = {",
        "  /** @private */",
        "  foo_: function() {},",
        "};"),
      LINE_JOINER.join(
        "var AnElement = Polymer({",
        "  is: 'an-element',",
        "  behaviors: [Behavior],",
        "  bar: function() { this.foo_(); },",
        "});")
    }, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testPrivatePropertyInBehavior() {
    testNoWarning(new String[] {
      LINE_JOINER.join(
        "/** @polymerBehavior */",
        "var Behavior = {",
        "  /** @private */",
        "  foo_: 'foo',",
        "  bar: function() { alert(this.foo_); },",
        "};"),
      LINE_JOINER.join(
        "var AnElement = Polymer({",
        "  is: 'an-element',",
        "  behaviors: [Behavior],",
        "});")
    });
  }

  public void testPrivatePropertyFromBehaviorUsedInElement() {
    testError(new String[] {
      LINE_JOINER.join(
        "/** @polymerBehavior */",
        "var Behavior = {",
        "  /** @private */",
        "  foo_: 'foo',",
        "};"),
      LINE_JOINER.join(
        "var AnElement = Polymer({",
        "  is: 'an-element',",
        "  behaviors: [Behavior],",
        "  bar: function() { alert(this.foo_); },",
        "});")
    }, BAD_PRIVATE_PROPERTY_ACCESS);
  }
}
