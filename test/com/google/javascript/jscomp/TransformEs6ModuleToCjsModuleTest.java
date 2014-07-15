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

import com.google.common.base.Joiner;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * Unit tests for {@link TransformEs6ModuleToCjsModule}
 */
public class TransformEs6ModuleToCjsModuleTest extends CompilerTestCase {

  public TransformEs6ModuleToCjsModuleTest() {
    compareJsDoc = false;
  }

  @Override
  public void setUp() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
    enableAstValidation(true);
    runTypeCheckAfterProcessing = true;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new TransformEs6ModuleToCjsModule(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testImport() {
    test("import name from \"test\";", "var name = require(\"test\").name;");
    test("import {n as name} from \"test\";", "var name = require(\"test\").n;");
    test("import x, {f as foo, b as bar} from \"test\";", Joiner.on('\n').join(
        "var x = require(\"test\").x;",
        "var foo = require(\"test\").f;",
        "var bar = require(\"test\").b"
    ));
  }

  public void testExport() {
    test("export var a; export var b;", "var a; var b; module.exports = {a: a, b: b};");
    test("export {f as foo, b as bar};", "module.exports = {foo: f, bar: b};");
  }

  public void testImportAndExport() {
    test(Joiner.on('\n').join(
        "import {name as n} from \"test\";",
        "export {n as name};"
    ), Joiner.on('\n').join(
        "var n = require(\"test\").name;",
        "module.exports = {name: n};"
    ));
  }
}
