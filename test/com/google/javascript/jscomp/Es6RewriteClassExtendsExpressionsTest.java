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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

public final class Es6RewriteClassExtendsExpressionsTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new Es6RewriteClassExtendsExpressions(compiler);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    setLanguageOut(LanguageMode.ECMASCRIPT3);
    disableTypeCheck();
    enableRunTypeCheckAfterProcessing();
  }

  public void testBasic() {
    test(
        "const foo = {'bar': Object}; class Foo extends foo['bar'] {}",
        lines(
            "const foo = {'bar': Object};",
            "const testcode$classextends$var0 = foo['bar'];",
            "class Foo extends testcode$classextends$var0 {}"));
  }

  public void testName() {
    testSame("class Foo extends Object {}");
  }

  public void testGetProp() {
    testSame("const foo = { bar: Object}; class Foo extends foo.bar {}");
  }

  public void testMixinFunction() {
    test(
        lines(
            "/** @return {function(new:Object)} */",
            "function mixObject(Superclass) {",
            "  return class extends Superclass {",
            "    bar() { return 'bar'; }",
            "  };",
            "}",
            "class Foo {}",
            "class Bar extends mixObject(Foo) {}"),
        lines(
            "/** @return {function(new:Object)} */",
            "function mixObject(Superclass) {",
            "  return class extends Superclass {",
            "    bar() { return 'bar'; }",
            "  };",
            "}",
            "class Foo {}",
            "const testcode$classextends$var0 = mixObject(Foo);",
            "class Bar extends testcode$classextends$var0 {}"));
  }
}
