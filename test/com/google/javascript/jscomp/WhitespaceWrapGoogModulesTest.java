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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class WhitespaceWrapGoogModulesTest extends CompilerTestCase {

  private LanguageMode languageOut;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    languageOut = LanguageMode.ECMASCRIPT_2015;

    disableCompareAsTree();
    // otherwise "use strict" in the expected output moves,
    // from where it should be (deliberately to match ClosureBundler),
    // to the top of the AST and breaks the comparison.
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new WhitespaceWrapGoogModules(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(languageOut);
    return options;
  }

  @Test
  public void testGoogModuleRewrite() {
    test(
        lines("goog.module('test');", "var f = 5;", "exports = f;"),
        "goog.loadModule(function(exports){"
            + "\"use strict\";"
            + "goog.module(\"test\");"
            + "var f=5;"
            + "exports=f;"
            + "return exports"
            + "})");
  }
}
