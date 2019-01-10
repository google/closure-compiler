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
package com.google.javascript.jscomp;

import static com.google.javascript.jscomp.RewriteGoogJsImports.CANNOT_HAVE_MODULE_VAR_NAMED_GOOG;
import static com.google.javascript.jscomp.RewriteGoogJsImports.GOOG_JS_IMPORT_MUST_BE_GOOG_STAR;
import static com.google.javascript.jscomp.RewriteGoogJsImports.GOOG_JS_REEXPORTED;

import com.google.javascript.jscomp.RewriteGoogJsImports.Mode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link RewriteGoogJsImports} that involve only linting and no rewriting. */

@RunWith(JUnit4.class)
public final class CheckGoogJsImportTest extends CompilerTestCase {
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RewriteGoogJsImports(compiler, Mode.LINT_ONLY);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Test
  public void testNoGoogJsImport() {
    testSame("var x");
    testSame("import {stuff} from './somethingelse.js';");
    testSame("import * as o from './closure/other.js';");
    testSame("export * from './closure/other.js';");
    testSame("import {stuff} from './othergoog.js';");
  }

  @Test
  public void testValidImports() {
    testSame("import * as goog from './closure/goog.js';");
    testSame("import * as goog from './goog.js';");
  }

  @Test
  public void testImportStarMustBeNamedGoog() {
    testError("import * as closure from './goog.js';",
        GOOG_JS_IMPORT_MUST_BE_GOOG_STAR);
  }

  @Test
  public void testImportSpecIsError() {
    testError("import {require} from './closure/goog.js';",
        GOOG_JS_IMPORT_MUST_BE_GOOG_STAR);
  }

  @Test
  public void testImportDefaultIsError() {
    testError("import d from './closure/goog.js';",
        GOOG_JS_IMPORT_MUST_BE_GOOG_STAR);
  }

  @Test
  public void testMixedImportIsError() {
    testError("testcode", "import d, * as goog from './closure/goog.js';",
        GOOG_JS_IMPORT_MUST_BE_GOOG_STAR);
    testError("import d, {require} from './closure/goog.js';",
        GOOG_JS_IMPORT_MUST_BE_GOOG_STAR);
  }

  @Test
  public void testPathOnlyImportIsError() {
    testError("import './closure/goog.js';",
        GOOG_JS_IMPORT_MUST_BE_GOOG_STAR);
  }

  @Test
  public void testExportFromGoogJsIsError() {
    testError(
        "export {require} from './closure/goog.js';",
        GOOG_JS_REEXPORTED);

    testError(
        "export * from './closure/goog.js';",
        GOOG_JS_REEXPORTED);

    testError(lines("import * as goog from './closure/goog.js';", "export {goog};"),
        GOOG_JS_REEXPORTED);

    testError(
                lines("import * as goog from './closure/goog.js';", "export {goog as GOOG};"),
        GOOG_JS_REEXPORTED);

    testError(
                lines("import * as goog from './closure/goog.js';", "export default goog;"),
        GOOG_JS_REEXPORTED);
  }

  @Test
  public void testOtherModuleGoogVarIsError() {
    testError("export const goog = 0;", CANNOT_HAVE_MODULE_VAR_NAMED_GOOG);
    testError("export {}; const goog = 0;", CANNOT_HAVE_MODULE_VAR_NAMED_GOOG);
    testError("export function goog() {}", CANNOT_HAVE_MODULE_VAR_NAMED_GOOG);
  }
}
