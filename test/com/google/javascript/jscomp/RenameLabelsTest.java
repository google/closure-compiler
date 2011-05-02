/*
 * Copyright 2008 The Closure Compiler Authors.
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
 * Tests for {@link RenameLabels}.
 */
public class RenameLabelsTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RenameLabels(compiler);
  }


  public void testRenameInFunction() {
    test("function x(){ Foo:a(); }",
         "function x(){ a(); }");
    test("function x(){ Foo:{ a(); break Foo; } }",
         "function x(){ a:{ a(); break a; } }");
    test("function x() { " +
            "Foo:{ " +
              "function goo() {" +
                "Foo: {" +
                  "a(); " +
                  "break Foo; " +
                "}" +
              "}" +
            "}" +
          "}",
          "function x(){function goo(){a:{ a(); break a; }}}");
    test("function x() { " +
          "Foo:{ " +
            "function goo() {" +
              "Foo: {" +
                "a(); " +
                "break Foo; " +
              "}" +
            "}" +
            "break Foo;" +
          "}" +
        "}",
        "function x(){a:{function goo(){a:{ a(); break a; }} break a;}}");
  }

  public void testRenameGlobals() {
    test("Foo:{a();}",
         "a();");
    test("Foo:{a(); break Foo;}",
         "a:{a(); break a;}");
    test("Foo:{Goo:a(); break Foo;}",
         "a:{a(); break a;}");
    test("Foo:{Goo:while(1){a(); continue Goo; break Foo;}}",
         "a:{b:while(1){a(); continue b;break a;}}");
    test("Foo:Goo:while(1){a(); continue Goo; break Foo;}",
         "a:b:while(1){a(); continue b;break a;}");

    test("Foo:Bar:X:{ break Bar; }",
         "a:{ break a; }");
    test("Foo:Bar:X:{ break Bar; break X; }",
         "a:b:{ break a; break b;}");
    test("Foo:Bar:X:{ break Bar; break Foo; }",
         "a:b:{ break b; break a;}");

    test("Foo:while (1){a(); break;}",
         "while (1){a(); break;}");

    // Remove label that is not referenced.
    test("Foo:{a(); while (1) break;}",
         "a(); while (1) break;");
  }

  public void testRenameReused() {
    test("foo:{break foo}; foo:{break foo}", "a:{break a};a:{break a}");
  }

}
