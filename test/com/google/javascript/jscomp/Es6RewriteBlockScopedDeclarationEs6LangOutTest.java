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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test case for {@link Es6RewriteBlockScopedDeclaration} with Es6 as the Language Out
 *
 * <p>Created by simranarora on 7/6/17.
 *
 * @author simranarora@google.com (Simran Arora)
 */
@RunWith(JUnit4.class)
public class Es6RewriteBlockScopedDeclarationEs6LangOutTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    enableRunTypeCheckAfterProcessing();
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new Es6RewriteBlockScopedDeclaration(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Test
  public void testNoTranspilationForBlockScopedFunctionDeclarations() {
    testSame("for (var x of y) { function f() { x; } }");
  }

  // TODO (simarora) Correct output is commented out for now - transpilation mode is being entered
  // to convert lets and consts to vars even though output is ES6
  @Test
  public void testNoTranspilationForLetConstDeclarations() {
    /*testSame(
        "for (var x of y) {" +
        "  function f() {" +
        "    let z;" +
        "  }" +
        "}");*/

    test(
        "for (var x of y) { function f() { let z; } }",
        "for (var x of y) { function f() { var z; } }");
  }
}
