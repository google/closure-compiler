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

import static com.google.javascript.jscomp.ClosureCheckModule.GOOG_MODULE_REFERENCES_THIS;
import static com.google.javascript.jscomp.ClosureCheckModule.GOOG_MODULE_USES_THROW;
import static com.google.javascript.jscomp.ClosureCheckModule.MODULE_AND_PROVIDES;
import static com.google.javascript.jscomp.ClosureCheckModule.MULTIPLE_MODULES_IN_FILE;

public final class ClosureCheckModuleTest extends Es6CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ClosureCheckModule(compiler);
  }

  public void testGoogModuleReferencesThis() {
    testError("goog.module('xyz');\nfoo.call(this, 1, 2, 3);", GOOG_MODULE_REFERENCES_THIS);

    testError(
        LINE_JOINER.join(
            "goog.module('xyz');",
            "",
            "var x = goog.require('other.x');",
            "",
            "if (x) {",
            "  alert(this);",
            "}"),
        GOOG_MODULE_REFERENCES_THIS);

    testSameEs6(
        LINE_JOINER.join(
            "goog.module('xyz');",
            "",
            "class Foo {",
            "  constructor() {",
            "    this.x = 5;",
            "  }",
            "}",
            "",
            "exports = Foo;"));
  }

  public void testGoogModuleUsesThrow() {
    testError("goog.module('xyz');\nthrow 4;", GOOG_MODULE_USES_THROW);

    testError(
        LINE_JOINER.join(
            "goog.module('xyz');",
            "",
            "var x = goog.require('other.x');",
            "",
            "if (x) {",
            "  throw 5;",
            "}"),
        GOOG_MODULE_USES_THROW);
  }

  public void testGoogModuleAndProvide() {
    testError("goog.module('xyz');\ngoog.provide('abc');", MODULE_AND_PROVIDES);
  }

  public void testMultipleGoogModules() {
    testError(
        LINE_JOINER.join(
            "goog.module('xyz');",
            "goog.module('abc');",
            "",
            "var x = goog.require('other.x');"),
        MULTIPLE_MODULES_IN_FILE);
  }
}
