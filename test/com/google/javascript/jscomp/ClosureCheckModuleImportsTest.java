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

import static com.google.javascript.jscomp.ClosureCheckModuleImports.QUALIFIED_REFERENCE_TO_GOOG_MODULE;

public final class ClosureCheckModuleImportsTest extends Es6CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ClosureCheckModuleImports(compiler);
  }

  public void testGoogModuleReferencedWithGlobalName() {
    testError(
        new String[] {"goog.module('a.b.c');", "goog.require('a.b.c'); use(a.b.c);"},
        QUALIFIED_REFERENCE_TO_GOOG_MODULE);

    testError(
        new String[] {"goog.module('a.b.c');", "goog.require('a.b.c'); use(a.b.c.d);"},
        QUALIFIED_REFERENCE_TO_GOOG_MODULE);

    testError(
        new String[] {
          "goog.module('a.b.c');",
          "goog.module('x.y.z'); var c = goog.require('a.b.c'); use(a.b.c);"
        },
        QUALIFIED_REFERENCE_TO_GOOG_MODULE);

    testError(
        new String[] {"goog.module('a.b.c');", "use(a.b.c);"},
        QUALIFIED_REFERENCE_TO_GOOG_MODULE);
  }

  public void testGoogModuleValidReferences() {
    testSame(
        new String[] {
          "goog.module('a.b.c');", "goog.module('x.y.z'); var c = goog.require('a.b.c'); use(c);"
        });

    testSame(
        new String[] {
          "goog.module('a.b.c');",
          LINE_JOINER.join(
              "goog.require('a.b.c');",
              "goog.scope(function() {",
              "  var c = goog.module.get('a.b.c');",
              "  use(c);",
              "});")
        });

    testSame(
        new String[] {
          "goog.module('a.b.c'); goog.module.declareLegacyNamespace();",
          "goog.require('a.b.c'); use(a.b.c);"
        });
  }
}
