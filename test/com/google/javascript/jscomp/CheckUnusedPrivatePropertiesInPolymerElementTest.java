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

import com.google.javascript.rhino.Node;

/**
 * Checks that references to properties in strings, in Polymer elements, are counted as usages of
 * those properties.
 */
public final class CheckUnusedPrivatePropertiesInPolymerElementTest extends TypeICompilerTestCase {

  private static final String EXTERNS = lines(
      DEFAULT_EXTERNS,
      "var Polymer = function(descriptor) {};",
      "/** @constructor */",
      "var PolymerElement = function() {};");

  public CheckUnusedPrivatePropertiesInPolymerElementTest() {
    super(EXTERNS);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableGatherExternProperties();
    enableTranspile();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        new PolymerPass(compiler, 1, true).process(externs, root);
        new CheckUnusedPrivateProperties(compiler).process(externs, root);
      }
    };
  }

  @Override
  public CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.ANALYZER_CHECKS, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.OFF);
    // Global this is used deliberately to refer to Window in these tests
    options.setWarningLevel(new DiagnosticGroup(NewTypeInference.GLOBAL_THIS), CheckLevel.OFF);
    options.setPolymerVersion(1);
    return options;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testPolymerPropertyUsedAsObserver1() {
    allowExternsChanges();
    testNoWarning(
        lines(
            "Polymer({",
            "  is: 'example-elem',",
            "  properties: {",
            "    foo: {",
            "      type: Boolean,",
            "      observer: 'fooChanged_',",
            "    },",
            "  },",
            "",
            "  /** @private */",
            "  fooChanged_() {},",
            "});"));
  }

  public void testPolymerPropertyUsedAsObserver2() {
    allowExternsChanges();
    testNoWarning(
        lines(
            "Polymer({",
            "  is: 'example-elem',",
            "  properties: {",
            "    foo: {",
            "      type: Boolean,",
            "      observer: 'fooChanged_',",
            "    },",
            "  },",
            "",
            "  /** @private */",
            "  fooChanged_: function() {},",
            "});"));
  }

  public void testBehaviorPropertyUsedAsObserver() {
    allowExternsChanges();
    test(
        new String[] {
            lines(
                "/** @polymerBehavior */",
                "var Behavior = {",
                "  properties: {",
                "    foo: {",
                "      type: Boolean,",
                "      observer: 'fooChanged_',",
                "    },",
                "  },",
                "",
                "  /** @private */",
                "  fooChanged_: function() {},",
                "};"),
            lines(
                "Polymer({",
                "  is: 'example-elem',",
                "  behaviors: [Behavior],",
                "});"),
        },
        new String[] {
            lines(
                "/** @polymerBehavior @nocollapse */",
                "var Behavior = {",
                "  properties: {",
                "    foo: {",
                "      type: Boolean,",
                "      observer: 'fooChanged_',",
                "    },",
                "  },",
                "",
                "  /** @suppress {checkTypes|globalThis|visibility} */",
                "  fooChanged_: function() {},",
                "};"),
            lines(
                "/**",
                " * @constructor",
                " * @extends {PolymerElement}",
                " * @implements {PolymerExampleElemElementInterface}",
                " */",
                "var ExampleElemElement = function() {};",
                "",
                "/** @type {boolean} */",
                "ExampleElemElement.prototype.foo;",
                "",
                "/** @private @suppress {unusedPrivateMembers} */",
                "ExampleElemElement.prototype.fooChanged_ = function() {};",
                "Polymer(/** @lends {ExampleElemElement.prototype} */ {",
                "  is: 'example-elem',",
                "  behaviors: [Behavior],",
                "});"),
        });
  }
}
