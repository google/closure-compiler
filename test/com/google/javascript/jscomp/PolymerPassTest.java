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

import static com.google.javascript.jscomp.PolymerPass.POLYMER_DESCRIPTOR_NOT_VALID;
import static com.google.javascript.jscomp.PolymerPass.POLYMER_MISSING_IS;
import static com.google.javascript.jscomp.PolymerPass.POLYMER_UNEXPECTED_PARAMS;

import com.google.common.base.Joiner;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * Unit tests for PolymerPass
 * @author jlklein@google.com (Jeremy Klein)
 */
public class PolymerPassTest extends CompilerTestCase {
  private static final String EXTERNS = Joiner.on("\n").join(
      "/** @constructor */",
      "var PolymerBase = function() {};",
      "/**",
      " * @param a {!Object}",
      " * @return {!function()}",
      " */",
      "var Polymer = function(a) {};",
      "var alert = function(msg) {};");
  public PolymerPassTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new PolymerPass(compiler);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
    enableTypeCheck(CheckLevel.ERROR);
    runTypeCheckAfterProcessing = true;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testVarTarget() {
    test(Joiner.on("\n").join(
        "var X = Polymer({",
        "  is: 'x-element',",
        "});"),

        Joiner.on("\n").join(
        "/** @constructor @extends {PolymerBase} */",
        "var X = function() {};",
        "X = Polymer(/** @lends {X.prototype} */ {",
        "  is: 'x-element',",
        "});"));
  }

  public void testDefaultTypeNameTarget() {
    test(Joiner.on("\n").join(
        "Polymer({",
        "  is: 'x',",
        "});"),

        Joiner.on("\n").join(
        "/** @constructor @extends {PolymerBase} */",
        "var XElement = function() {};",
        "Polymer(/** @lends {XElement.prototype} */ {",
        "  is: 'x',",
        "});"));
  }

  public void testPathAssignmentTarget() {
    test(Joiner.on("\n").join(
        "var x = {};",
        "x.Z = Polymer({",
        "  is: 'x-element',",
        "});"),

        Joiner.on("\n").join(
        "var x = {};",
        "/** @constructor @extends {PolymerBase} */",
        "x.Z = function() {};",
        "x.Z = Polymer(/** @lends {x.Z.prototype} */ {",
        "  is: 'x-element',",
        "});"));
  }

  public void testConstructorExtraction() {
    test(Joiner.on("\n").join(
        "var X = Polymer({",
        "  is: 'x-element',",
        "  constructor: function() { alert('hi'); },",
        "});"),

        Joiner.on("\n").join(
        "/** @constructor @extends {PolymerBase} */",
        "var X = function() { alert('hi'); };",
        "X = Polymer(/** @lends {X.prototype} */ {",
        "  is: 'x-element',",
        "  constructor: function() { alert('hi'); },",
        "});"));
  }

  public void testOtherKeysIgnored() {
    test(Joiner.on("\n").join(
        "var X = Polymer({",
        "  is: 'x-element',",
        "  listeners: {",
        "    'click': 'handleClick',",
        "  },",
        "",
        "  handleClick: function(e) {",
        "    alert('Thank you for clicking');",
        "  },",
        "});"),

        Joiner.on("\n").join(
        "/** @constructor @extends {PolymerBase} */",
        "var X = function() {};",
        "X = Polymer(/** @lends {X.prototype} */ {",
        "  is: 'x-element',",
        "  listeners: {",
        "    'click': 'handleClick',",
        "  },",
        "",
        "  handleClick: function(e) {",
        "    alert('Thank you for clicking');",
        "  },",
        "});"));
  }

  public void testInvalid1() {
    testSame(
        "var x = Polymer();",
        POLYMER_DESCRIPTOR_NOT_VALID, true);
    testSame(
        "var x = Polymer({},'blah');",
        POLYMER_UNEXPECTED_PARAMS, true);
    testSame(
        "var x = Polymer({});",
        POLYMER_MISSING_IS, true);
  }
}
