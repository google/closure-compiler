/*
 * Copyright 2018 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.lint;

import static com.google.javascript.jscomp.lint.CheckEs6ModuleFileStructure.MUST_COME_BEFORE;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CheckEs6ModuleFileStructureTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckEs6ModuleFileStructure(compiler);
  }

  @Test
  public void testImportsMustBeFirstIfPresent() {
    testSame("var notAModule;");
    testSame("export let noImports;");
    testSame("import importsFirst from 'file'; let notFirst;");
    testWarning("let first; import notFirst from 'file';", MUST_COME_BEFORE);
  }

  @Test
  public void testDeclareModuleIdAfterImportsIfPresent() {
    testSame("import noDeclareNamespace from 'file';");

    testSame("goog.declareModuleId('name.space'); export let noImports;");
    testSame("goog.module.declareNamespace('name.space'); export let noImports;");

    testSame("import first from 'file'; goog.declareModuleId('name.space');");
    testSame("import first from 'file'; goog.module.declareNamespace('name.space');");

    testWarning("export let first; goog.declareModuleId('name.space');", MUST_COME_BEFORE);
    testWarning("export let first; goog.module.declareNamespace('name.space');", MUST_COME_BEFORE);

    testWarning(
        "import first from 'file'; let second; goog.declareModuleId('name.space');",
        MUST_COME_BEFORE);
    testWarning(
        "import first from 'file'; let second; goog.module.declareNamespace('name.space');",
        MUST_COME_BEFORE);
  }

  @Test
  public void testGoogRequireAfterImportsAndDeclareNamesButBeforeOthers() {
    testSame("const bar = goog.require('bar');");
    testSame("const bar = goog.require('bar'); let notAModule;");
    testSame("let notAModule; const bar = goog.require('bar');");

    testSame("const bar = goog.require('bar'); export {};");
    testSame("export {}; const bar = goog.require('bar');", MUST_COME_BEFORE);

    testSame("goog.module.declareNamespace('name'); const bar = goog.require('bar'); export {};");
    testWarning(
        "const bar = goog.require('bar'); goog.module.declareNamespace('name'); export {};",
        MUST_COME_BEFORE);

    testSame("import 'file'; const bar = goog.require('bar');");
    testWarning("const bar = goog.require('bar'); import 'file';", MUST_COME_BEFORE);

    testSame("import 'file'; goog.require('bar');");
    testWarning("goog.require('bar'); import 'file';", MUST_COME_BEFORE);

    testSame(
        lines(
            "import 'file';",
            "goog.module.declareNamespace('name');",
            "const bar = goog.require('bar');",
            "let rest;"));
  }
}
