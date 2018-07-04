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

  public void testClassExpressions() {
    testSame(
        lines(
            "const foo = { bar: Object};",
            "function baz(arg) {}",
            "baz(class extends foo.bar {});"));
  }

  public void testVarDeclaration() {
    test(
        "const foo = {'bar': Object}; var Foo = class extends foo['bar'] {};",
        lines(
            "const foo = {'bar': Object};",
            "const testcode$classextends$var0 = foo['bar'];",
            "var Foo = class extends testcode$classextends$var0 {};"));

    test(
        "const foo = {'bar': Object}; let Foo = class extends foo['bar'] {};",
        lines(
            "const foo = {'bar': Object};",
            "const testcode$classextends$var0 = foo['bar'];",
            "let Foo = class extends testcode$classextends$var0 {};"));

    test(
        "const foo = {'bar': Object}; const Foo = class extends foo['bar'] {};",
        lines(
            "const foo = {'bar': Object};",
            "const testcode$classextends$var0 = foo['bar'];",
            "const Foo = class extends testcode$classextends$var0 {};"));

    test(
        lines(
            "const foo = {'bar': Object};",
            "for (let Foo = class extends foo['bar'] {}, i = 0; i < 1; i++) {}"),
        lines(
            "const foo = {'bar': Object};",
            "for (let Foo = (function() {",
            "    const testcode$classextends$var0 = foo['bar'];",
            "    return class extends testcode$classextends$var0 {};",
            "  })(), i = 0; i < 1; i++) {}"));
  }

  public void testAssign() {
    test(
        "const foo = {'bar': Object}; var Foo; Foo = class extends foo['bar'] {};",
        lines(
            "const foo = {'bar': Object};",
            "var Foo;",
            "const testcode$classextends$var0 = foo['bar'];",
            "Foo = class extends testcode$classextends$var0 {};"));

    test(
        "const foo = {'bar': Object}; foo.baz = class extends foo['bar'] {};",
        lines(
            "const foo = {'bar': Object};",
            "const testcode$classextends$var0 = foo['bar'];",
            "foo.baz = class extends testcode$classextends$var0 {};"));

    test(
        "const foo = {'bar': Object}; foo.foo = foo.baz = class extends foo['bar'] {};",
        lines(
            "const foo = {'bar': Object};",
            "foo.foo = foo.baz = (function() {",
            "  const testcode$classextends$var0 = foo['bar'];",
            "  return class extends testcode$classextends$var0 {};",
            "})();"));

    test(
        "const foo = {'bar': Object}; foo['baz'] = class extends foo['bar'] {};",
        lines(
            "const foo = {'bar': Object};",
            "const testcode$classextends$var0 = foo['bar'];",
            "foo['baz'] = class extends testcode$classextends$var0 {};"));

    test(
        "const foo = {'bar': Object, baz: {}}; foo.baz['foo'] = class extends foo['bar'] {};",
        lines(
            "const foo = {'bar': Object, baz: {}};",
            "const testcode$classextends$var0 = foo['bar'];",
            "foo.baz['foo'] = class extends testcode$classextends$var0 {};"));

    test(
        lines(
            "let baz = 1;",
            "const foo = {'bar': Object};",
            "foo[baz++] = class extends foo['bar'] {};"),
        lines(
            "let baz = 1;",
            "const foo = {'bar': Object};",
            "foo[baz++] = (function() {",
            "  const testcode$classextends$var0 = foo['bar'];",
            "  return class extends testcode$classextends$var0 {};",
            "})();"));
  }

  public void testMultipleVarDeclaration() {
    test(
        lines(
            "const foo = { 'bar': Object};",
            "function mayHaveSideEffects() {}",
            "var baz = mayHaveSideEffects(), Foo = class extends foo['bar'] {};"),
        lines(
            "const foo = { 'bar': Object};",
            "function mayHaveSideEffects() {}",
            "var baz = mayHaveSideEffects(), Foo = (function() {",
            "  const testcode$classextends$var0 = foo['bar'];",
            "  return class extends testcode$classextends$var0 {};",
            "})();"));

    test(
        "const foo = {'bar': Object}; const Foo = class extends foo['bar'] {}, baz = false;",
        lines(
            "const foo = {'bar': Object};",
            "const testcode$classextends$var0 = foo['bar'];",
            "const Foo = class extends testcode$classextends$var0 {}, baz = false;"));
  }
}
