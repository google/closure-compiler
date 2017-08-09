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

public final class PolymerProtectStaticPropertiesTest extends CompilerTestCase {

  private static final String EXTERNS =
      LINE_JOINER.join("var Polymer = {};", "Polymer.Element = function() {};");

  public PolymerProtectStaticPropertiesTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new PolymerProtectStaticProperties(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    // This pass only runs once.
    return 1;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    allowExternsChanges();
  }

  public void testExternsAdded() {
    this.disableCompareAsTree();
    testExternChanges(
        EXTERNS,
        "",
        LINE_JOINER.join(
            "Function.prototype.is;",
            "Function.prototype.properties;",
            "Function.prototype.observers;",
            EXTERNS));
  }

  public void testNoExternsAdded() {
    this.disableCompareAsTree();
    testExternChanges("", "", "");
  }
}
