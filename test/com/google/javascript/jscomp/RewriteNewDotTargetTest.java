/*
 * Copyright 2019 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.Es6ToEs3Util.CANNOT_CONVERT_YET;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link RewriteNewDotTarget}. */
@RunWith(JUnit4.class)
@SuppressWarnings("RhinoNodeGetFirstFirstChild")
public class RewriteNewDotTargetTest extends CompilerTestCase {

  @Before
  public void enableTypeCheckBeforePass() {
    enableTypeCheck();
    enableTypeInfoValidation();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RewriteNewDotTarget(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT_IN);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5_STRICT);
    return options;
  }

  @Test
  public void testNewTargetInNonConstructorMethod() {
    testError(
        lines(
            "", //
            "/** @constructor */",
            "function Foo() { new.target; }", // not an ES6 class constructor
            ""),
        CANNOT_CONVERT_YET);

    testError(
        "function foo() { new.target; }", // not a constructor at all
        CANNOT_CONVERT_YET);

    testError(
        lines(
            "", //
            "class Foo {",
            "  method() {", // not a constructor
            "    new.target;",
            "  }",
            "}",
            ""),
        CANNOT_CONVERT_YET);
  }

  @Test
  public void testNewTargetInEs6Constructor() {
    test(
        lines(
            "", //
            "class Foo {",
            "  constructor() {",
            "    new.target;",
            "    () => new.target;", // works in arrow functions, too
            "  }",
            "}",
            ""),
        lines(
            "", //
            "class Foo {",
            "  constructor() {",
            "    this.constructor;",
            "    () => this.constructor;", // works in arrow functions, too
            "  }",
            "}",
            ""));
  }
}
