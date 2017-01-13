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

/**
 * Tests for {@link ObjectDefinePropertyPreprocess}
 *
 */
public class ObjectDefinePropertyPreprocessTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new ObjectDefinePropertyPreprocess(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected void setUp() {
    super.allowExternsChanges(true);
  }

  public void testObjectDefinePropery() {
    test("var foo = function() {};" +
         "Object.defineProperty(foo, 'bar', {" +
           "get: function() { return 1; }" +
         "});",
         "var foo = function() {};" +
         "/** @expose */foo.bar;" +
         "Object.defineProperty(foo, 'bar', {" +
           "get: function() { return 1; }" +
         "});");
    test("/** @constructor */var foo = function() {};" +
         "Object.defineProperty(foo.prototype, 'bar', {" +
           "get: function() { return 1; }" +
         "});",
         "/** @constructor */var foo = function() {};" +
         "/** @expose */foo.prototype.bar;" +
         "Object.defineProperty(foo.prototype, 'bar', {" +
           "get: /** @this {foo} */ function () { return 1; }" +
         "});");
  }
}
