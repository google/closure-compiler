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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class Es6ExtractClassesTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new Es6NormalizeClasses(compiler);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2015);
    setLanguageOut(LanguageMode.ECMASCRIPT3);

    enableTypeInfoValidation();
    enableRewriteClosureCode();
    enableTypeCheck();
    replaceTypesWithColors();
    enableMultistageCompilation();
    setGenericNameReplacements(Es6NormalizeClasses.GENERIC_NAME_REPLACEMENTS);
  }

  @Test
  public void testExtractionFromCall() {
    test(
        "f(class{});",
        """
        const CLASS_DECL$0 = class {};
        f(CLASS_DECL$0);
        """);
  }

  @Test
  public void testSelfReference1() {
    test(
        "var Outer = class Inner { constructor() { alert(Inner); } };",
        """
        const CLASS_DECL$0 = class {
          constructor() { alert(CLASS_DECL$0); }
        };
        /** @constructor */
        var Outer=CLASS_DECL$0
        """);

    test(
        "let Outer = class Inner { constructor() { alert(Inner); } };",
        """
        const CLASS_DECL$0 = class {
          constructor() { alert(CLASS_DECL$0); }
        };
        /** @constructor */
        let Outer=CLASS_DECL$0
        """);

    test(
        "const Outer = class Inner { constructor() { alert(Inner); } };",
        """
        const CLASS_DECL$0 = class {
          constructor() { alert(CLASS_DECL$0); }
        };
        /** @constructor */
        const Outer=CLASS_DECL$0
        """);
  }

  @Test
  public void testSelfReference2() {
    test(
        "alert(class C { constructor() { alert(C); } });",
        """
        const CLASS_DECL$0 = class {
          constructor() { alert(CLASS_DECL$0); }
        };
        alert(CLASS_DECL$0)
        """);
  }

  @Test
  public void testSelfReference3() {
    test(
        """
        alert(class C {
          m1() { class C {}; alert(C); }
          m2() { alert(C); }
        });
        """,
        """
        const CLASS_DECL$0 = class {
          m1() { class C {}; alert(C); }
          m2() { alert(CLASS_DECL$0); }
        };
        alert(CLASS_DECL$0)
        """);
  }

  @Test
  public void testSelfReference_googModule() {
    test(
        externs(new TestExternsBuilder().addClosureExterns().build()),
        srcs(
            """
            goog.module('example');
            exports = class Inner { constructor() { alert(Inner); } };
            """),
        expected(
            """
            /** @const */ const CLASS_DECL$0=class {
              constructor(){ alert(CLASS_DECL$0); }
            };
            /**
             * @constructor
             * @const
             */
            var module$exports$example=CLASS_DECL$0
            """));
  }

  @Test
  public void testSelfReference_qualifiedName() {
    test(
        """
        const outer = {};
        /** @const */ outer.qual = {};
        outer.qual.Name = class Inner { constructor() { alert(Inner); } };
        """,
        """
        const outer = {};
        /** @const */ outer.qual = {};
        const CLASS_DECL$0 = class {
          constructor() {
            alert(CLASS_DECL$0);
          }
        };
        /** @constructor */
        outer.qual.Name = CLASS_DECL$0;
        """);
  }

  @Test
  public void testConstAssignment() {
    test(
        "var foo = bar(class {});",
        """
        const CLASS_DECL$0 = class {};
        var foo = bar(CLASS_DECL$0);
        """);
  }

  @Test
  public void testLetAssignment() {
    test(
        "let foo = bar(class {});",
        """
        const CLASS_DECL$0 = class {};
        let foo = bar(CLASS_DECL$0);
        """);
  }

  @Test
  public void testVarAssignment() {
    test(
        "var foo = bar(class {});",
        """
        const CLASS_DECL$0 = class {};
        var foo = bar(CLASS_DECL$0);
        """);
  }

  @Test
  public void testJSDocOnVar() {
    test(
        "/** @unrestricted */ var foo = class bar {};",
        """
        const CLASS_DECL$0 = class {};
        /** @constructor */
        var foo = CLASS_DECL$0;
        """);
  }

  @Test
  public void testFilenameContainsAt() {
    test(
        srcs(SourceFile.fromCode("unusual@name", "alert(class {});")),
        expected(
            SourceFile.fromCode(
                "unusual@name",
                """
                const CLASS_DECL$0 = class{};
                alert(CLASS_DECL$0);
                """)));
  }

  @Test
  public void testFilenameContainsPlus() {
    test(
        srcs(SourceFile.fromCode("+some/+path/file", "alert(class {});")),
        expected(
            SourceFile.fromCode(
                "+path/file",
                """
                const CLASS_DECL$0 = class{};
                alert(CLASS_DECL$0);
                """)));
  }

  @Test
  public void testConditionalBlocksExtractionFromCall() {
    test(
        "maybeTrue() && f(class{});",
        """
        maybeTrue() && f((() => {
          const CLASS_DECL$0 = class {};
          return CLASS_DECL$0;
        })());
        """);
  }

  @Test
  public void testExtractionFromArrayLiteral() {
    test(
        "var c = [class C {}];",
        """
        const CLASS_DECL$0 = class {};
        var c = [CLASS_DECL$0];
        """);
  }

  @Test
  public void testTernaryOperatorBlocksExtraction() {
    test(
        "var c = maybeTrue() ? class A {} : anotherExpr",
        """
        var c = maybeTrue() ? (() => {
          const CLASS_DECL$0 = class {};
          return CLASS_DECL$0;
        })() : anotherExpr;
        """);
    test(
        "var c = maybeTrue() ? anotherExpr : class B {}",
        """
        var c = maybeTrue() ? anotherExpr : (() => {
          const CLASS_DECL$0 = class {};
          return CLASS_DECL$0;
        })();
        """);
  }

  @Test
  public void testNormalisedArrowFunction() {
    test(
        "(() => { return class A {};})();",
        "(() => { const CLASS_DECL$0 = class {}; return CLASS_DECL$0;})()");

    test(
        "function foo(x = () => {return class A {}; }) {} use(foo());",
        """
        function foo(
          x = () => {
            const CLASS_DECL$0 = class {};
            return CLASS_DECL$0;
          })
         {}
        use(foo());
        """);
  }

  // Tests that class that is a default parameter value are converted into normalized arrow
  // functions and successfully extracted
  @Test
  public void testClassAsDefaultParamValue() {
    test(
        """
        function foo(x = class A {}) {}
        use(foo());
        """,
        """
        function foo(x =
          (() => { const CLASS_DECL$0 = class {};
                   return CLASS_DECL$0;
                 }
          )()) {}
        use(foo());
        """);
  }

  @Test
  public void classExtractedInNormalizedArrowNested() {
    test(
        externs(new TestExternsBuilder().addClosureExterns().build()),
        srcs(
            """
            goog.module('some');
            exports.some = ((outer) => {
              ((c) => { return class extends c{}}
            )});
            """),
        expected(
            """
            /** @const */ var module$exports$some = {};
            /** @const */ module$exports$some.some = outer => {
            c => {
              const CLASS_DECL$0 = class extends c {};
              return CLASS_DECL$0;
              };
            };
            """));
  }

  @Test
  public void testCannotExtract() {
    test(
        "let x; while(x = class A{}) {use(x);}",
"""
let x;
while(x = (() => { const CLASS_DECL$0 = class {}; return CLASS_DECL$0;})())
{use(x);}
""");

    test(
        """
        /** @type {number} */ var x = 0;
        function f(x, y) {}
        while(f(x = 2, class Foo { bar() {} })){}
        """,
        """
        var x = 0;
        function f(x, y) {}
        while(
          f(x = 2,
            (() => { const CLASS_DECL$0 = class { bar() {} };
                  return CLASS_DECL$0;
                 })()
        )){}
        """);
  }

  @Test
  public void testClassesHandledByEs6ToEs3Converter() {
    testSame("class C{}");
    testSame("var c = class {};");
  }
}
