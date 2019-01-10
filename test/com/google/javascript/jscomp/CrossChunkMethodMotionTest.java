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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link CrossChunkMethodMotion}.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
@RunWith(JUnit4.class)
public final class CrossChunkMethodMotionTest extends CompilerTestCase {
  private static final String EXTERNS =
      "IFoo.prototype.bar; var mExtern; mExtern.bExtern; mExtern['cExtern'];";

  private boolean canMoveExterns = false;
  private boolean noStubs = false;
  private static final String STUB_DECLARATIONS = CrossChunkMethodMotion.STUB_DECLARATIONS;

  public CrossChunkMethodMotionTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CrossChunkMethodMotion(compiler, new IdGenerator(), canMoveExterns, noStubs);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    canMoveExterns = false;
    noStubs = false;
    enableNormalize();
  }

  @Test
  public void moveMethodAssignedToPrototype() {
    // bar property is defined in externs, so it cannot be moved
    testSame(
        createModuleChain(
            lines(
                "function Foo() {}", //
                "Foo.prototype.bar = function() {};"),
            // Chunk 2
            "(new Foo).bar()"));

    canMoveExterns = true;
    test(
        createModuleChain(
            lines(
                "function Foo() {}", //
                "Foo.prototype.bar = function() {};"),
            // Chunk 2
            "(new Foo).bar()"),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "function Foo() {}",
              "Foo.prototype.bar = JSCompiler_stubMethod(0);"),
          // Chunk 2
          lines(
              "Foo.prototype.bar = JSCompiler_unstubMethod(0, function() {});", //
              "(new Foo).bar()")
        });
  }

  @Test
  public void moveMethodDefinedInPrototypeLiteralWithStubs() {
    test(
        createModuleChain(
            lines(
                "function Foo() {}", //
                "Foo.prototype = { method: function() {} };"),
            // Chunk 2
            "(new Foo).method()"),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "function Foo() {}",
              "Foo.prototype = { method: JSCompiler_stubMethod(0) };"),
          // Chunk 2
          lines(
              "Foo.prototype.method = ", //
              "    JSCompiler_unstubMethod(0, function() {});",
              "(new Foo).method()")
        });
  }

  @Test
  public void moveMethodDefinedInPrototypeLiteralWithoutStubs() {
    noStubs = true;
    test(
        createModuleChain(
            lines(
                "function Foo() {}", //
                "Foo.prototype = { method: function() {} };"),
            // Chunk 2
            "(new Foo).method()"),
        new String[] {
          lines(
              "function Foo() {}", //
              "Foo.prototype = {};"),
          // Chunk 2
          lines(
              "Foo.prototype.method = function() {};", //
              "(new Foo).method()")
        });
  }

  @Test
  public void moveMethodDefinedInPrototypeLiteralUsingShorthandSyntaxWithStub() {
    test(
        createModuleChain(
            lines(
                "function Foo() {}", //
                "Foo.prototype = { method() {} };"),
            // Chunk 2
            "(new Foo).method()"),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "function Foo() {}",
              "Foo.prototype = { method: JSCompiler_stubMethod(0) };"),
          // Chunk 2
          lines(
              "Foo.prototype.method = ", //
              "    JSCompiler_unstubMethod(0, function() {});",
              "(new Foo).method()")
        });
  }

  @Test
  public void moveMethodDefinedInPrototypeLiteralUsingShorthandSyntaxWithoutStub() {
    noStubs = true;
    test(
        createModuleChain(
            lines(
                "function Foo() {}", //
                "Foo.prototype = { method() {} };"),
            // Chunk 2
            "(new Foo).method()"),
        new String[] {
          lines("function Foo() {}", "Foo.prototype = {};"),
          // Chunk 2
          lines("Foo.prototype.method = function() {};", "(new Foo).method()")
        });
  }

  @Test
  public void doNotMoveMethodDefinedInPrototypeLiteralAsComputedProp() {
    testSame(
        createModuleChain(
            lines(
                "function Foo() {}", //
                "Foo.prototype = { [1]:  {} };"),
            // Chunk 2
            "(new Foo)[1]()"));
  }

  @Test
  public void moveClassMethod() {
    test(
        createModuleChain(
            // Chunk 1
            "class Foo { method() {} }",
            // Chunk 2
            "(new Foo).method()"),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "class Foo {}",
              "Foo.prototype.method = JSCompiler_stubMethod(0);"),
          // Chunk 2
          lines(
              "Foo.prototype.method = JSCompiler_unstubMethod(0, function() {});",
              "(new Foo).method();")
        });
  }

  @Test
  public void doNotMoveClassComputedPropertyMethod() {
    testSame(
        createModuleChain(
            // Chunk 1
            "const methodName = 'method';",
            "class Foo { [methodName]() {} }",
            // Chunk 2
            "(new Foo)[methodName]()"));
  }

  @Test
  public void moveClassMethodForConstDefinition() {
    test(
        createModuleChain(
            // Chunk 1
            "const Foo = class FooInternal { method() {} }",
            // Chunk 2
            "(new Foo).method()"),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "const Foo = class FooInternal {}",
              "Foo.prototype.method = JSCompiler_stubMethod(0);"),
          // Chunk 2
          lines(
              "Foo.prototype.method = JSCompiler_unstubMethod(0, function() {});",
              "(new Foo).method();")
        });
  }

  @Test
  public void doNotMoveClassMethodWithLocalClassNameReference() {
    // We could probably rewrite the internal reference, but it is unlikely that the added
    // complexity of doing so would be worthwhile.
    testSame(
        createModuleChain(
            // Chunk 1
            "const Foo = class FooInternal { method() { FooInternal; } }",
            // Chunk 2
            "(new Foo).method()"));
  }

  @Test
  public void doNotMoveGetterDefinedInPrototypeLiteral() {
    testSame(
        createModuleChain(
            lines(
                "function Foo() {}", //
                "Foo.prototype = { get method() {} };"),
            // Chunk 2
            "(new Foo).method()"));
  }

  @Test
  public void doNotMoveClassGetter() {
    testSame(
        createModuleChain(
            "class Foo { get method() {} }",
            // Chunk 2
            "(new Foo).method()"));
  }

  @Test
  public void movePrototypeMethodWithoutStub() {
    testSame(
        createModuleChain(
            lines(
                "function Foo() {}", //
                "Foo.prototype.bar = function() {};"),
            // Chunk 2
            "(new Foo).bar()"));

    canMoveExterns = true;
    noStubs = true;
    test(
        createModuleChain(
            lines(
                "function Foo() {}", //
                "Foo.prototype.bar = function() {};"),
            // Chunk 2
            "(new Foo).bar()"),
        new String[] {
          "function Foo() {}",
          // Chunk 2
          lines(
              "Foo.prototype.bar = function() {};", //
              "(new Foo).bar()")
        });
  }

  @Test
  public void moveClassMethodWithoutStub() {
    testSame(
        createModuleChain(
            "class Foo { bar() {} }",
            // Chunk 2
            "(new Foo).bar()"));

    canMoveExterns = true;
    noStubs = true;
    test(
        createModuleChain(
            "class Foo { bar() {} }",
            // Chunk 2
            "(new Foo).bar()"),
        new String[] {
          "class Foo {}",
          // Chunk 2
          lines(
              "Foo.prototype.bar = function() {};", //
              "(new Foo).bar()")
        });
  }

  @Test
  public void doNotMovePrototypeMethodIfAliasedAndNoStubs() {
    // don't move if noStubs enabled and there's a reference to the method to be moved
    noStubs = true;
    testSame(
        createModuleChain(
            lines(
                "function Foo() {}",
                "Foo.prototype.m = function() {};",
                "Foo.prototype.m2 = Foo.prototype.m;"),
            // Chunk 2
            "(new Foo).m()"));

    testSame(
        createModuleChain(
            lines(
                "function Foo() {}",
                "Foo.prototype.m = function() {};",
                "Foo.prototype.m2 = Foo.prototype.m;"),
            // Chunk 2
            "(new Foo).m(), (new Foo).m2()"));

    noStubs = false;

    test(
        createModuleChain(
            lines(
                "function Foo() {}",
                "Foo.prototype.m = function() {};",
                "Foo.prototype.m2 = Foo.prototype.m;"),
            // Chunk 2
            "(new Foo).m()"),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "function Foo() {}",
              "Foo.prototype.m = JSCompiler_stubMethod(0);",
              "Foo.prototype.m2 = Foo.prototype.m;"),
          // Chunk 2
          lines(
              "Foo.prototype.m = JSCompiler_unstubMethod(0, function() {});", //
              "(new Foo).m()")
        });

    test(
        createModuleChain(
            lines(
                "function Foo() {}",
                "Foo.prototype.m = function() {};",
                "Foo.prototype.m2 = Foo.prototype.m;"),
            // Chunk 2
            "(new Foo).m(), (new Foo).m2()"),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "function Foo() {}",
              "Foo.prototype.m = JSCompiler_stubMethod(0);",
              "Foo.prototype.m2 = Foo.prototype.m;"),
          // Chunk 2
          lines(
              "Foo.prototype.m = JSCompiler_unstubMethod(0, function() {});",
              "(new Foo).m(), (new Foo).m2()"),
        });
  }

  @Test
  public void doNotMoveClassMethodIfAliasedAndNoStubs() {
    // don't move if noStubs enabled and there's a reference to the method to be moved
    noStubs = true;
    testSame(
        createModuleChain(
            lines(
                "class Foo { m() {} }", //
                "Foo.prototype.m2 = Foo.prototype.m;"),
            // Chunk 2
            "(new Foo).m()"));

    testSame(
        createModuleChain(
            lines(
                "class Foo { m() {} }", //
                "Foo.prototype.m2 = Foo.prototype.m;"),
            // Chunk 2
            "(new Foo).m(), (new Foo).m2()"));

    noStubs = false;

    test(
        createModuleChain(
            lines(
                "class Foo { m() {} }", //
                "Foo.prototype.m2 = Foo.prototype.m;"),
            // Chunk 2
            "(new Foo).m()"),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "class Foo {}",
              "Foo.prototype.m = JSCompiler_stubMethod(0);",
              "Foo.prototype.m2 = Foo.prototype.m;"),
          // Chunk 2
          lines(
              "Foo.prototype.m = JSCompiler_unstubMethod(0, function() {});", //
              "(new Foo).m()")
        });

    test(
        createModuleChain(
            lines(
                "class Foo { m() {} }", //
                "Foo.prototype.m2 = Foo.prototype.m;"),
            // Chunk 2
            "(new Foo).m(), (new Foo).m2()"),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "class Foo {}",
              "Foo.prototype.m = JSCompiler_stubMethod(0);",
              "Foo.prototype.m2 = Foo.prototype.m;"),
          // Chunk 2
          lines(
              "Foo.prototype.m = JSCompiler_unstubMethod(0, function() {});",
              "(new Foo).m(), (new Foo).m2()"),
        });
  }

  @Test
  public void doNotMovePrototypeMethodRedeclaredInSiblingChunk() {
    // don't move if it can be overwritten when a sibling of the first referencing chunk is loaded.
    testSame(
        createModuleStar(
            lines(
                "function Foo() {}", //
                "Foo.prototype.method = function() {};"),
            // Chunk 2
            "Foo.prototype.method = function() {};",
            // Chunk 3
            "(new Foo).method()"));
  }

  @Test
  public void doNotMoveClassMethodRedeclaredInSiblingChunk() {
    // don't move if it can be overwritten when a sibling of the first referencing chunk is loaded.
    testSame(
        createModuleStar(
            "class Foo { method() {} }",
            // Chunk 2
            "Foo.prototype.method = function() {};",
            // Chunk 3
            "(new Foo).method()"));
  }

  @Test
  public void doNotMovePrototypeMethodRedeclaredInDependentChunk() {
    // don't move if it can be overwritten by a chunk depending on the first referencing chunk.
    testSame(
        createModuleChain(
            lines(
                "function Foo() {}", //
                "Foo.prototype.method = function() {};"),
            // Chunk 2
            "(new Foo).method()",
            // Chunk 3
            "Foo.prototype.method = function() {};"));
  }

  @Test
  public void doNotMoveClassMethodRedeclaredInDependentChunk() {
    // don't move if it can be overwritten by a chunk depending on the first referencing chunk.
    testSame(
        createModuleChain(
            "class Foo { method() {} }",
            // Chunk 2
            "(new Foo).method()",
            // Chunk 3
            "Foo.prototype.method = function() {};"));
  }

  @Test
  public void doNotMovePrototypeMethodRedeclaredBeforeFirstReferencingChunk() {
    // Note: it is reasonable to move the method in this case,
    // but it is difficult enough to prove that we don't.
    testSame(
        createModuleChain(
            lines(
                "function Foo() {}", //
                "Foo.prototype.method = function() {};"),
            // Chunk 2
            "Foo.prototype.method = function() {};",
            // Chunk 3
            "(new Foo).method()"));
  }

  @Test
  public void doNotMoveClassMethodRedeclaredBeforeFirstReferencingChunk() {
    // Note: it is reasonable to move the method in this case,
    // but it is difficult enough to prove that we don't.
    testSame(
        createModuleChain(
            "class Foo { method() {} }",
            // Chunk 2
            "Foo.prototype.method = function() {};",
            // Chunk 3
            "(new Foo).method()"));
  }

  @Test
  public void movePrototypeRecursiveMethod() {
    test(
        createModuleChain(
            lines(
                "function Foo() {}", //
                "Foo.prototype.baz = function() { this.baz(); };"),
            // Chunk 2
            "(new Foo).baz()"),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "function Foo() {}",
              "Foo.prototype.baz = JSCompiler_stubMethod(0);"),
          // Chunk 2
          lines(
              "Foo.prototype.baz = JSCompiler_unstubMethod(0, function() { this.baz(); });",
              "(new Foo).baz()")
        });
  }

  @Test
  public void moveInstanceRecursiveMethod() {
    test(
        createModuleChain(
            "class Foo { baz() { this.baz(); } }",
            // Chunk 2
            "(new Foo).baz()"),
        new String[] {
          lines(
              STUB_DECLARATIONS, //
              "class Foo {}",
              "Foo.prototype.baz = JSCompiler_stubMethod(0);"),
          // Chunk 2
          lines(
              "Foo.prototype.baz = JSCompiler_unstubMethod(0, function() { this.baz(); });",
              "(new Foo).baz()")
        });
  }

  @Test
  public void doNotMoveNonLiteralFunction() {
    testSame(
        createModuleChain(
            lines(
                "function Foo() {}", //
                "Foo.prototype.baz = goog.nullFunction;"),
            // Chunk 2
            "(new Foo).baz()"));

    testSame(
        createModuleChain(
            lines(
                "class Foo {}", //
                "Foo.prototype.baz = goog.nullFunction;"),
            // Chunk 2
            "(new Foo).baz()"));
  }

  @Test
  public void movePrototypeDeclarationsInTheRightOrder() {
    test(
        createModuleChain(
            lines(
                "function Foo() {}",
                "Foo.prototype.baz = function() { return 1; };",
                "Foo.prototype.baz = function() { return 2; };"),
            // Chunk 2
            "(new Foo).baz()"),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "function Foo() {}",
              "Foo.prototype.baz = JSCompiler_stubMethod(1);",
              "Foo.prototype.baz = JSCompiler_stubMethod(0);"),
          // Chunk 2
          lines(
              "Foo.prototype.baz = JSCompiler_unstubMethod(1, function() { return 1; });",
              "Foo.prototype.baz = JSCompiler_unstubMethod(0, function() { return 2; });",
              "(new Foo).baz()")
        });
  }

  @Test
  public void moveClassMethodAndReclarationInTheRightOrder() {
    test(
        createModuleChain(
            lines(
                "class Foo { baz() { return 1; } }",
                "Foo.prototype.baz = function() { return 2; };"),
            // Chunk 2
            "(new Foo).baz()"),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "class Foo {}",
              "Foo.prototype.baz = JSCompiler_stubMethod(1);",
              "Foo.prototype.baz = JSCompiler_stubMethod(0);"),
          // Chunk 2
          lines(
              "Foo.prototype.baz = ",
              "JSCompiler_unstubMethod(1, function() { return 1; });",
              "Foo.prototype.baz = ",
              "JSCompiler_unstubMethod(0, function() { return 2; });",
              "(new Foo).baz()")
        });
  }

  @Test
  public void movePrototypeMethodsForDifferentClassesInTheRightOrder() {
    JSModule[] m =
        createModules(
            lines(
                "function Foo() {}",
                "Foo.prototype.baz = function() { return 1; };",
                "function Goo() {}",
                "Goo.prototype.baz = function() { return 2; };"),
            // Chunk 2, depends on 1
            "",
            // Chunk 3, depends on 2
            "(new Foo).baz()",
            // Chunk 4, depends on 3
            "",
            // Chunk 5, depends on 3
            "(new Goo).baz()");

    m[1].addDependency(m[0]);
    m[2].addDependency(m[1]);
    m[3].addDependency(m[2]);
    m[4].addDependency(m[2]);

    test(
        m,
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "function Foo() {}",
              "Foo.prototype.baz = JSCompiler_stubMethod(1);",
              "function Goo() {}",
              "Goo.prototype.baz = JSCompiler_stubMethod(0);"),
          // Chunk 2
          "",
          // Chunk 3
          lines(
              "Foo.prototype.baz = JSCompiler_unstubMethod(1, function() { return 1; });",
              "Goo.prototype.baz = JSCompiler_unstubMethod(0, function() { return 2; });",
              "(new Foo).baz()"),
          // Chunk 4
          "",
          // Chunk 5
          "(new Goo).baz()"
        });
  }

  @Test
  public void moveClassMethodsForDifferentClassesInTheRightOrder() {
    JSModule[] m =
        createModules(
            lines(
                "class Foo { baz() { return 1; } }", //
                "class Goo { baz() { return 2; } }"),
            // Chunk 2, depends on 1
            "",
            // Chunk 3, depends on 2
            "(new Foo).baz()",
            // Chunk 4, depends on 3
            "",
            // Chunk 5, depends on 3
            "(new Goo).baz()");

    m[1].addDependency(m[0]);
    m[2].addDependency(m[1]);
    m[3].addDependency(m[2]);
    m[4].addDependency(m[2]);

    test(
        m,
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "class Foo {}",
              "Foo.prototype.baz = JSCompiler_stubMethod(1);",
              "class Goo {}",
              "Goo.prototype.baz = JSCompiler_stubMethod(0);"),
          // Chunk 2
          "",
          // Chunk 3
          lines(
              "Foo.prototype.baz = JSCompiler_unstubMethod(1, function() { return 1; });",
              "Goo.prototype.baz = JSCompiler_unstubMethod(0, function() { return 2; });",
              "(new Foo).baz()"),
          // Chunk 4
          "",
          // Chunk 5
          "(new Goo).baz()"
        });
  }

  @Test
  public void doNotMovePrototypeMethodUsedInMultiplepDependentChunks() {
    testSame(
        createModuleStar(
            lines(
                "function Foo() {}", //
                "Foo.prototype.baz = function() {};"),
            // Chunk 2
            "(new Foo).baz()",
            // Chunk 3
            "(new Foo).baz()"));
  }

  @Test
  public void doNotMoveClassMethodUsedInMultiplepDependentChunks() {
    testSame(
        createModuleStar(
            "class Foo { baz() {} }",
            // Chunk 2
            "(new Foo).baz()",
            // Chunk 3
            "(new Foo).baz()"));
  }

  @Test
  public void movePrototypeMethodToDeepestCommonDependencyOfReferencingChunks() {
    JSModule[] modules =
        createModules(
            lines(
                "function Foo() {}", //
                "Foo.prototype.baz = function() {};"),
            // Chunk 2
            "", // a blank chunk in the middle
            // Chunk 3
            "(new Foo).baz() , 1",
            // Chunk 4
            "(new Foo).baz() , 2");

    modules[1].addDependency(modules[0]);
    modules[2].addDependency(modules[1]);
    modules[3].addDependency(modules[1]);
    test(
        modules,
        new String[] {
          lines(
              STUB_DECLARATIONS, //
              "function Foo() {}",
              "Foo.prototype.baz = JSCompiler_stubMethod(0);"),
          // Chunk 2
          "Foo.prototype.baz = JSCompiler_unstubMethod(0, function() {});",
          // Chunk 3
          "(new Foo).baz() , 1",
          // Chunk 4
          "(new Foo).baz() , 2"
        });
  }

  @Test
  public void moveClassMethodToDeepestCommonDependencyOfReferencingChunks() {
    JSModule[] modules =
        createModules(
            "class Foo { baz() {} }",
            // Chunk 2
            "", // a blank chunk in the middle
            // Chunk 3
            "(new Foo).baz() , 1",
            // Chunk 4
            "(new Foo).baz() , 2");

    modules[1].addDependency(modules[0]);
    modules[2].addDependency(modules[1]);
    modules[3].addDependency(modules[1]);

    test(
        modules,
        new String[] {
          lines(
              STUB_DECLARATIONS, //
              "class Foo {}",
              "Foo.prototype.baz = JSCompiler_stubMethod(0);"),
          // Chunk 2
          "Foo.prototype.baz = JSCompiler_unstubMethod(0, function() {});",
          // Chunk 3
          "(new Foo).baz() , 1",
          // Chunk 4
          "(new Foo).baz() , 2"
        });
  }

  @Test
  public void movePrototypeMethodThatRefersToAnotherOnTheSameClass() {
    test(
        createModuleChain(
            lines(
                "function Foo() {}", //
                "Foo.prototype.baz = function() {};"),
            // Chunk 2
            "Foo.prototype.callBaz = function() { this.baz(); }",
            // Chunk 3
            "(new Foo).callBaz()"),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "function Foo() {}",
              "Foo.prototype.baz = JSCompiler_stubMethod(0);"),
          // Chunk 2
          "Foo.prototype.callBaz = JSCompiler_stubMethod(1);",
          // Chunk 3
          lines(
              "Foo.prototype.callBaz = ",
              "  JSCompiler_unstubMethod(1, function() { this.baz(); });",
              "Foo.prototype.baz = JSCompiler_unstubMethod(0, function() {});",
              "(new Foo).callBaz()")
        });
  }

  @Test
  public void movePrototypeMethodThatRefersToAnClassMethodOnTheSameClass() {
    test(
        createModuleChain(
            "class Foo { baz() {} }",
            // Chunk 2
            "Foo.prototype.callBaz = function() { this.baz(); }",
            // Chunk 3
            "(new Foo).callBaz()"),
        new String[] {
          lines(
              STUB_DECLARATIONS, //
              "class Foo {}",
              "Foo.prototype.baz = JSCompiler_stubMethod(0);"),
          // Chunk 2
          "Foo.prototype.callBaz = JSCompiler_stubMethod(1);",
          // Chunk 3
          lines(
              "Foo.prototype.callBaz = JSCompiler_unstubMethod(1, function() { this.baz(); });",
              "Foo.prototype.baz = JSCompiler_unstubMethod(0, function() {});",
              "(new Foo).callBaz()")
        });
  }

  @Test
  public void doNotMovePrototypeMethodDefinitionThatFollowsFirstUse() {
    // if the programmer screws up the module order, we don't try to correct
    // the mistake.
    test(
        createModuleChain(
            lines(
                "function Foo() {}", //
                "Foo.prototype.baz = function() {};"),
            // Chunk 2
            "(new Foo).callBaz()", // call before definition
            // Chunk 3
            "Foo.prototype.callBaz = function() { this.baz(); }"),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "function Foo() {}",
              "Foo.prototype.baz = JSCompiler_stubMethod(0);"),
          // Chunk 2
          "(new Foo).callBaz()",
          // Chunk 3
          lines(
              "Foo.prototype.baz = JSCompiler_unstubMethod(0, function() {});",
              "Foo.prototype.callBaz = function() { this.baz(); };")
        });
  }

  @Test
  public void movePrototypeMethodPastUsageInAGlobalFunction() {
    test(
        createModuleChain(
            lines(
                "function Foo() {}",
                "Foo.prototype.baz = function() {};",
                // usage here doesn't really happen until x() is called, so
                // it's OK to move the definition of baz().
                "function x() { return (new Foo).baz(); }"),
            // Chunk 2
            "x();"),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "function Foo() {}",
              "Foo.prototype.baz = JSCompiler_stubMethod(0);",
              "function x() { return (new Foo).baz(); }"),
          // Chunk 2
          lines(
              "Foo.prototype.baz = JSCompiler_unstubMethod(0, function() {});", //
              "x();")
        });
  }

  @Test
  public void moveClassMethodPastUsageInAGlobalFunction() {
    test(
        createModuleChain(
            lines(
                "class Foo {",
                "  baz() {}",
                "}",
                // usage here doesn't really happen until x() is called, so
                // it's OK to move the definition of baz().
                "function x() { return (new Foo).baz(); }"),
            // Chunk 2
            "x();"),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "class Foo {}",
              "Foo.prototype.baz = JSCompiler_stubMethod(0);",
              "function x() { return (new Foo).baz(); }"),
          // Chunk 2
          lines(
              "Foo.prototype.baz = JSCompiler_unstubMethod(0, function() {});", //
              "x();")
        });
  }

  // Read of closure variable disables method motions.
  @Test
  public void doNotMovePrototypeMethodThatUsesLocalClosureVariable() {
    testSame(
        createModuleChain(
            lines(
                "function Foo() {}",
                "(function() {",
                "  var x = 'x';",
                "  Foo.prototype.baz = function() {x};",
                "})();"),
            // Chunk 2
            "var y = new Foo(); y.baz();"));
  }

  @Test
  public void doNotMoveClassMethodThatUsesLocalClosureVariable() {
    testSame(
        createModuleChain(
            lines(
                "const Foo = (function() {",
                "  var x = 'x';",
                "  return class Foo { baz() { return x; } };",
                "})();"),
            // Chunk 2
            "var y = new Foo(); y.baz();"));
  }

  @Test
  public void movePrototypeMethodThatDefinesOtherMethodsOnSameGlobalClass() {
    test(
        createModuleChain(
            lines(
                "function Foo() {}",
                "Foo.prototype.b1 = function() {",
                "  var x = 1;",
                "  Foo.prototype.b2 = function() {",
                "    Foo.prototype.b3 = function() {",
                "      x;",
                "    }",
                "  }",
                "};"),
            // Chunk 2
            "var y = new Foo(); y.b1();",
            // Chunk 3
            "y = new Foo(); z.b2();",
            // Chunk 4
            "y = new Foo(); z.b3();"),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "function Foo() {}",
              "Foo.prototype.b1 = JSCompiler_stubMethod(0);"),
          // Chunk 2
          lines(
              "Foo.prototype.b1 = JSCompiler_unstubMethod(0, function() {",
              "  var x = 1;",
              "  Foo.prototype.b2 = function() {",
              "    Foo.prototype.b3 = function() {",
              "      x;",
              "    }",
              "  }",
              "});",
              "var y = new Foo(); y.b1();"),
          // Chunk 3
          "y = new Foo(); z.b2();",
          // Chunk 4
          "y = new Foo(); z.b3();"
        });
  }

  @Test
  public void moveClassMethodThatDefinesOtherMethodsOnSameGlobalClass() {
    test(
        createModuleChain(
            lines(
                "class Foo {",
                "  b1() {",
                "    var x = 1;",
                // b2 cannot be extracted, because it contains a reference to x
                "    Foo.prototype.b2 = function() {",
                "      Foo.prototype.b3 = function() {",
                "        x;",
                "      }",
                "    }",
                "  };",
                "}"),
            // Chunk 2
            "var y = new Foo(); y.b1();",
            // Chunk 3
            "y = new Foo(); z.b2();",
            // Chunk 4
            "y = new Foo(); z.b3();"),
        new String[] {
          lines(
              STUB_DECLARATIONS, //
              "class Foo {}",
              "Foo.prototype.b1 = JSCompiler_stubMethod(0);"),
          // Chunk 2
          lines(
              "Foo.prototype.b1 = JSCompiler_unstubMethod(0, function() {",
              "  var x = 1;",
              "  Foo.prototype.b2 = function() {",
              "    Foo.prototype.b3 = function() {",
              "      x;",
              "    }",
              "  }",
              "});",
              "var y = new Foo(); y.b1();"),
          // Chunk 3
          "y = new Foo(); z.b2();",
          // Chunk 4
          "y = new Foo(); z.b3();"
        });
  }

  @Test
  public void extractPrototypeMethodDefinedInAnotherMethodWhenNoClosureReferencePreventsIt() {
    test(
        createModuleChain(
            lines(
                "function Foo() {}",
                "Foo.prototype.b1 = function() {",
                // definition of b2 can be extracted, because it doesn't refer to any variables
                // defined by b1.
                "  Foo.prototype.b2 = function() {",
                "    var x = 1;",
                // definition of b3 cannot be extracted, because it refers to x
                "    Foo.prototype.b3 = function() {",
                "      x;",
                "    }",
                "  }",
                "};"),
            // Chunk 2
            "var y = new Foo(); y.b1();",
            // Chunk 3
            "y = new Foo(); z.b2();",
            // Chunk 4
            "y = new Foo(); z.b3();"),
        new String[] {
          lines(
              STUB_DECLARATIONS, //
              "function Foo() {}",
              "Foo.prototype.b1 = JSCompiler_stubMethod(0);"),
          // Chunk 2
          lines(
              "Foo.prototype.b1 = JSCompiler_unstubMethod(0, function() {",
              "  Foo.prototype.b2 = JSCompiler_stubMethod(1);",
              "});",
              "var y = new Foo(); y.b1();"),
          // Chunk 3
          lines(
              "Foo.prototype.b2 = JSCompiler_unstubMethod(1, function() {",
              "  var x = 1;",
              "  Foo.prototype.b3 = function() {",
              "    x;",
              "  }",
              "});",
              "y = new Foo(); z.b2();"),
          // Chunk 4
          "y = new Foo(); z.b3();"
        });
  }

  @Test
  public void extractClassMethodDefinedInAnotherMethodWhenNoClosureReferencePreventsIt() {
    test(
        createModuleChain(
            lines(
                "class Foo {",
                "  b1() {",
                // definition of b2 can be extracted, because it doesn't refer to any variables
                // defined by b1.
                "    Foo.prototype.b2 = function() {",
                "      var x = 1;",
                // definition of b3 cannot be extracted, because it refers to x
                "      Foo.prototype.b3 = function() {",
                "        x;",
                "      }",
                "    }",
                "  }",
                "}"),
            // Chunk 2
            "var y = new Foo(); y.b1();",
            // Chunk 3
            "y = new Foo(); z.b2();",
            // Chunk 4
            "y = new Foo(); z.b3();"),
        new String[] {
          lines(
              STUB_DECLARATIONS, //
              "class Foo {}",
              "Foo.prototype.b1 = JSCompiler_stubMethod(0);"),
          // Chunk 2
          lines(
              "Foo.prototype.b1 =",
              "    JSCompiler_unstubMethod(",
              "        0,",
              "        function() {",
              "          Foo.prototype.b2 = JSCompiler_stubMethod(1);",
              "        });",
              "",
              "",
              "var y = new Foo(); y.b1();"),
          // Chunk 3
          lines(
              "Foo.prototype.b2 = JSCompiler_unstubMethod(1, function() {",
              "  var x = 1;",
              "  Foo.prototype.b3 = function() {",
              "    x;",
              "  }",
              "});",
              "y = new Foo(); z.b2();"),
          // Chunk 4
          "y = new Foo(); z.b3();"
        });
  }

  // Read of global variable is fine.
  @Test
  public void movePrototypeMethodThatReadsGlobalVar() {
    test(
        createModuleChain(
            lines(
                "function Foo() {}", //
                "var x = 'x';",
                "Foo.prototype.baz = function(){x};"),
            // Chunk 2
            "var y = new Foo(); y.baz();"),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "function Foo() {}",
              "var x = 'x';",
              "Foo.prototype.baz = JSCompiler_stubMethod(0);"),
          // Chunk 2
          lines(
              "Foo.prototype.baz = JSCompiler_unstubMethod(0, function(){x});", //
              "var y = new Foo(); y.baz();")
        });
  }

  // Read of global variable is fine.
  @Test
  public void moveClassMethodThatReadsGlobalVar() {
    test(
        createModuleChain(
            lines(
                "class Foo {", //
                "  baz() { x; }",
                "}",
                "var x = 'x';",
                ""),
            // Chunk 2
            "var y = new Foo(); y.baz();"),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "class Foo {}",
              "Foo.prototype.baz = JSCompiler_stubMethod(0);",
              "var x = 'x';",
              ""),
          // Chunk 2
          lines(
              "Foo.prototype.baz = JSCompiler_unstubMethod(0, function(){x});", //
              "var y = new Foo(); y.baz();")
        });
  }

  // Read of a local is fine.
  @Test
  public void movePrototypeMethodThatReferencesOnlyLocalVariables() {
    test(
        createModuleChain(
            lines(
                "function Foo() {}", //
                "Foo.prototype.baz = function(){var x = 1;x};"),
            // Chunk 2
            "var y = new Foo(); y.baz();"),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "function Foo() {}",
              "Foo.prototype.baz = JSCompiler_stubMethod(0);"),
          // Chunk 2
          lines(
              "Foo.prototype.baz = JSCompiler_unstubMethod(",
              "    0, function(){var x = 1; x});",
              "var y = new Foo(); y.baz();")
        });
  }

  // Read of a local is fine.
  @Test
  public void moveClassMethodThatReferencesOnlyLocalVariables() {
    test(
        createModuleChain(
            "class Foo { baz() {var x = 1; x; } }",
            // Chunk 2
            "var y = new Foo(); y.baz();"),
        new String[] {
          lines(
              STUB_DECLARATIONS, //
              "class Foo {}",
              "Foo.prototype.baz = JSCompiler_stubMethod(0);"),
          // Chunk 2
          lines(
              "Foo.prototype.baz = JSCompiler_unstubMethod(",
              "    0, function(){var x = 1; x});",
              "var y = new Foo(); y.baz();")
        });
  }

  // An anonymous inner function reading a closure variable is fine.
  @Test
  public void movePrototypeMethodContainingClosureOverLocalVariable() {
    test(
        createModuleChain(
            lines(
                "function Foo() {}",
                "Foo.prototype.baz = function() {",
                "  var x = 1;",
                "  return function(){x}",
                "};"),
            // Chunk 2
            "var y = new Foo(); y.baz();"),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "function Foo() {}",
              "Foo.prototype.baz = JSCompiler_stubMethod(0);"),
          // Chunk 2
          lines(
              "Foo.prototype.baz = JSCompiler_unstubMethod(",
              "    0, function(){var x = 1; return function(){x}});",
              "var y = new Foo(); y.baz();")
        });
  }

  @Test
  public void moveClassMethodContainingClosureOverLocalVariable() {
    test(
        createModuleChain(
            lines(
                "class Foo {",
                "  baz() {",
                "    var x = 1;",
                "    return function(){x}",
                "  }",
                "}"),
            // Chunk 2
            "var y = new Foo(); y.baz();"),
        new String[] {
          lines(
              STUB_DECLARATIONS, //
              "class Foo {}",
              "Foo.prototype.baz = JSCompiler_stubMethod(0);"),
          // Chunk 2
          lines(
              "Foo.prototype.baz = JSCompiler_unstubMethod(",
              "    0, function(){var x = 1; return function(){x}});",
              "var y = new Foo(); y.baz();")
        });
  }

  @Test
  public void testIssue600() {
    testSame(
        createModuleChain(
            lines(
                "var jQuery1 = (function() {",
                "  var jQuery2 = function() {};",
                "  var theLoneliestNumber = 1;",
                "  jQuery2.prototype = {",
                "    size: function() {",
                "      return theLoneliestNumber;",
                "    }",
                "  };",
                "  return jQuery2;",
                "})();"),
            // Chunk 2
            lines(
                "(function() {", //
                "  var div = jQuery1('div');",
                "  div.size();",
                "})();")));
  }

  @Test
  public void testIssue600b() {
    testSame(
        createModuleChain(
            lines(
                "var jQuery1 = (function() {",
                "  var jQuery2 = function() {};",
                "  jQuery2.prototype = {",
                "    size: function() {",
                "      return 1;",
                "    }",
                "  };",
                "  return jQuery2;",
                "})();\n"),
            // Chunk 2
            lines(
                "(function() {", //
                "  var div = jQuery1('div');",
                "  div.size();",
                "})();")));
  }

  @Test
  public void testIssue600c() {
    test(
        createModuleChain(
            lines(
                "var jQuery2 = function() {};",
                "jQuery2.prototype = {",
                "  size: function() {",
                "    return 1;",
                "  }",
                "};"),
            // Chunk 2
            lines(
                "(function() {", //
                "  var div = jQuery2('div');",
                "  div.size();",
                "})();")),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "var jQuery2 = function() {};",
              "jQuery2.prototype = {",
              "  size: JSCompiler_stubMethod(0)",
              "};"),
          // Chunk 2
          lines(
              "jQuery2.prototype.size=",
              "    JSCompiler_unstubMethod(0,function(){return 1});",
              "(function() {",
              "  var div = jQuery2('div');",
              "  div.size();",
              "})();")
        });
  }

  @Test
  public void testIssue600d() {
    test(
        createModuleChain(
            lines(
                "var jQuery2 = function() {};",
                "(function() {",
                "  jQuery2.prototype = {",
                "    size: function() {",
                "      return 1;",
                "    }",
                "  };",
                "})();"),
            lines(
                "(function() {", //
                "  var div = jQuery2('div');",
                "  div.size();",
                "})();")),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "var jQuery2 = function() {};",
              "(function() {",
              "  jQuery2.prototype = {",
              "    size: JSCompiler_stubMethod(0)",
              "  };",
              "})();"),
          lines(
              "jQuery2.prototype.size=",
              "    JSCompiler_unstubMethod(0,function(){return 1});",
              "(function() {",
              "  var div = jQuery2('div');",
              "  div.size();",
              "})();")
        });
  }

  @Test
  public void testIssue600e() {
    testSame(
        createModuleChain(
            lines(
                "var jQuery2 = function() {};",
                "(function() {",
                "  var theLoneliestNumber = 1;",
                "  jQuery2.prototype = {",
                "    size: function() {",
                "      return theLoneliestNumber;",
                "    }",
                "  };",
                "})();"),
            // Chunk 2
            lines(
                "(function() {", //
                "  var div = jQuery2('div');",
                "  div.size();",
                "})();")));
  }

  @Test
  public void testPrototypeOfThisAssign() {
    testSame(
        createModuleChain(
            "/** @constructor */",
            "function F() {}",
            "this.prototype.foo = function() {};",
            "(new F()).foo();"));
  }

  @Test
  public void testDestructuring() {
    test(
        createModuleChain(
            lines(
                "/** @constructor */", //
                "function F() {}",
                "F.prototype.foo = function() {};"),
            "const {foo} = new F();"),
        new String[] {
          lines(
              STUB_DECLARATIONS,
              "/** @constructor */",
              "function F() {}",
              "F.prototype.foo = JSCompiler_stubMethod(0);"),
          lines(
              "F.prototype.foo = JSCompiler_unstubMethod(0, function(){});", //
              "const {foo} = new F();")
        });
  }

  @Test
  public void testDestructuringWithQuotedProp() {
    testSame(
        createModuleChain(
            lines(
                "/** @constructor */", //
                "function F() {}",
                "F.prototype.foo = function() {};"),
            "const {'foo': foo} = new F();"));
  }

  @Test
  public void testDestructuringWithComputedProp() {
    // See https://github.com/google/closure-compiler/issues/3145
    testSame(
        createModuleChain(
            lines(
                "/** @constructor */", //
                "function F() {}",
                "F.prototype['foo'] = function() {};"),
            "const {['foo']: foo} = new F();"));
  }
}
