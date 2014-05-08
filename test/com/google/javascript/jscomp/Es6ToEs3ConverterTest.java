/*
 * Copyright 2014 The Closure Compiler Authors.
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

/**
 * Test case for {@link Es6ToEs3Converter}.
 *
 * @author tbreisacher@google.com (Tyler Breisacher)
 */
public class Es6ToEs3ConverterTest extends CompilerTestCase {
  @Override
  public void setUp() {
    enableEcmaScript5(false);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT6);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    return options;
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new Es6ToEs3Converter(compiler, compiler.getOptions());
  }

  public void testExtendedObjLit() {
    test("var x = {a, b};", "var x = {a: a, b: b};");
  }
}
