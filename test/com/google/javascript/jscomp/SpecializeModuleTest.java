/*
 * Copyright 2010 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.SpecializeModule.SpecializationState;
import com.google.javascript.rhino.Node;

/**
 * Tests for {@link SpecializeModule}.
 *
 * @author dcc@google.com (Devin Coughlin)
 */
public class SpecializeModuleTest extends CompilerTestCase {

  private static final String SHARED_EXTERNS = "var alert = function() {}";

  public SpecializeModuleTest() {
    super(SHARED_EXTERNS);
  }

  private PassFactory inlineFunctions =
      new PassFactory("inlineFunctions", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new InlineFunctions(compiler,
          compiler.getUniqueNameIdSupplier(), true, false, true, true, true);
    }
  };

  private PassFactory removeUnusedPrototypeProperties =
    new PassFactory("removeUnusedPrototypeProperties", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new RemoveUnusedPrototypeProperties(compiler, false, false);
    }
  };

  private PassFactory devirtualizePrototypeMethods =
    new PassFactory("devirtualizePrototypeMethods", true) {
    @Override
    protected CompilerPass create(AbstractCompiler compiler) {
      return new DevirtualizePrototypeMethods(compiler);
    }
  };

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    final SpecializeModule specializeModule = new SpecializeModule(compiler,
        devirtualizePrototypeMethods, inlineFunctions,
        removeUnusedPrototypeProperties);

    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        specializeModule.process(externs, root);

        /* Make sure variables are declared before used */
        new VarCheck(compiler).process(externs, root);
      }
    };
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    enableNormalize();
  }

  public void testSpecializeInline() {
    JSModule[] modules = createModuleStar(
        // m1
        /* Recursion in A() prevents inline of A*/
        "var A = function() {alert(B());A()};" +
        "var B = function() {return 6};" +
        "A();",
        // m2
        "A();" +
        "B();" +
        "B = function() {return 7};" +
        "A();" +
        "B();"
        );

    test(modules, new String[] {
        // m1
        "var A = function() {alert(6);A()};" + /* Specialized A */
        "A();" +
        "var B;",
        // m2
        "A = function() {alert(B());A()};" + /* Unspecialized A */
        "B = function() {return 6};" + /* Removed from m1, so add to m2 */
        "A();" +
        "B();" +
        "B = function() {return 7};" +
        "A();" +
        "B();"
    });
  }

  public void testSpecializeCascadedInline() {
    JSModule[] modules = createModuleStar(
        // m1
        /* Recursion in A() prevents inline of A*/
        "var A = function() {alert(B());A()};" +
        "var B = function() {return C()};" +
        "var C = function() {return 6};" +
        "A();",
        // m2
        "B = function() {return 7};" +
    "A();");

    test(modules, new String[] {
        // m1
        "var A = function() {alert(6);A()};" + /* Specialized A */
        "A();" +
        "var B, C;",
        // m2
        "A = function() {alert(B());A()};" + /* Unspecialized A */
        "B = function() {return C()};" + /* Removed from m1, so add to m2 */
        "C = function() {return 6};" + /* Removed from m1, so add to m2 */
        "B = function() {return 7};" +
        "A();"
    });
  }

  public void testSpecializeInlineWithMultipleDependents() {
    JSModule[] modules = createModuleStar(
        // m1
        /* Recursion in A() prevents inline of A*/
        "var A = function() {alert(B());A()};" +
        "var B = function() {return 6};" +
        "A();",
        // m2
        "B = function() {return 7};" +
        "A();",
        // m3
        "A();"
    );

    test(modules, new String[] {
        // m1
        "var A = function() {alert(6);A()};" + /* Specialized A */
        "A();" +
        "var B;",
        // m2
        "A = function() {alert(B());A()};" + /* Unspecialized A */
        "B = function() {return 6};" + /* Removed from m1, so add to m2 */
        "B = function() {return 7};" +
        "A();",
        "A = function() {alert(B());A()};" + /* Unspecialized A */
        "B = function() {return 6};" + /* Removed from m1, so add to m2 */
        "A();",

    });
  }

  public void testSpecializeInlineWithNamespaces() {
    JSModule[] modules = createModuleStar(
        // m1
        "var ns = {};" +
        /* Recursion in A() prevents inline of A*/
        "ns.A = function() {alert(B());ns.A()};" +
        "var B = function() {return 6};" +
        "ns.A();",
        // m2
        "B = function() {return 7};" +
    "ns.A();");

    test(modules, new String[] {
        // m1
        "var ns = {};" +
        "ns.A = function() {alert(6);ns.A()};" + /* Specialized A */
        "ns.A();" +
        "var B;",
        // m2
        "ns.A = function() {alert(B());ns.A()};" + /* Unspecialized A */
        "B = function() {return 6};" + /* Removed from m1, so add to m2 */
        "B = function() {return 7};" +
        "ns.A();"
    });
  }

  public void testSpecializeInlineWithRegularFunctions() {
    JSModule[] modules = createModuleStar(
        // m1
        /* Recursion in A() prevents inline of A*/
        "function A() {alert(B());A()}" +
        "function B() {return 6}" +
        "A();",
        // m2
        "B = function() {return 7};" +
    "A();");

    test(modules, new String[] {
        // m1
        "function A() {alert(6);A()}" + /* Specialized A */
        "A();" +
        "var B;",
        // m2
        "A = function() {alert(B());A()};" + /* Unspecialized A */
        "B = function() {return 6};" + /* Removed from m1, so add to m2 */
        /* Start of original m2 */
        "B = function() {return 7};" +
        "A();"
    });
  }

  public void testDontSpecializeLocalNonAnonymousFunctions() {
    /* normalize result, but not expected */
    enableNormalize(false);

    JSModule[] modules = createModuleStar(
        // m1
        "(function(){var noSpecialize = " +
            "function() {alert(6)};noSpecialize()})()",
        // m2
        "");

    test(modules, new String[] {
        // m1
        "(function(){var noSpecialize = " +
            "function() {alert(6)};noSpecialize()})()",
        // m2
        ""
    });
  }

  public void testAddDummyVarsForRemovedFunctions() {
    JSModule[] modules = createModuleStar(
        // m1
        /* Recursion in A() prevents inline of A*/
        "var A = function() {alert(B() + C());A()};" +
        "var B = function() {return 6};" +
        "var C = function() {return 8};" +
        "A();",
        // m2
        "" +
    "A();");

    test(modules, new String[] {
        // m1
        "var A = function() {alert(6 + 8);A()};" + /* Specialized A */
        "A();" +
        "var B, C;",
        // m2
        "A = function() {alert(B() + C());A()};" + /* Unspecialized A */
        "B = function() {return 6};" + /* Removed from m1, so add to m2 */
        "C = function() {return 8};" + /* Removed from m1, so add to m2 */
        "A();"
    });
  }

  public void testSpecializeRemoveUnusedProperties() {
    JSModule[] modules = createModuleStar(
        // m1
        /* Recursion in A() prevents inline of A*/
        "var Foo = function(){};" + /* constructor */
        "Foo.prototype.a = function() {this.a()};" +
        "Foo.prototype.b = function() {return 6};" +
        "Foo.prototype.c = function() {return 7};" +
        "var aliasA = Foo.prototype.a;" + // Prevents devirtualization of a
        "var x = new Foo();" +
        "x.a();",
        // m2
        "");

    test(modules, new String[] {
        // m1
        "var Foo = function(){};" + /* constructor */
        "Foo.prototype.a = function() {this.a()};" +
        "var aliasA = Foo.prototype.a;" +
        "var x = new Foo();" +
        "x.a();",
        // m2
        "Foo.prototype.b = function() {return 6};" +
        "Foo.prototype.c = function() {return 7};"
    });
  }

  public void testDontSpecializeAliasedFunctions_inline() {
    JSModule[] modules = createModuleStar(
        // m1
        /* Recursion in A() prevents inline of A*/
        "function A() {alert(B());A()}" +
        "function B() {return 6}" +
        "var aliasA = A;" +
        "A();",
        // m2
        "B = function() {return 7};" +
        "B();");

    test(modules, new String[] {
        // m1
        /* Recursion in A() prevents inline of A*/
        "function A() {alert(B());A()}" +
        "function B() {return 6}" +
        "var aliasA = A;" +
        "A();",
        // m2
        "B = function() {return 7};" +
        "B();"
    });
  }

  public void testDontSpecializeAliasedFunctions_remove_unused_properties() {
    JSModule[] modules = createModuleStar(
        // m1
        "var Foo = function(){};" + /* constructor */
        "Foo.prototype.a = function() {this.a()};" +
        "Foo.prototype.b = function() {return 6};" +
        "var aliasB = Foo.prototype.b;" +
        "Foo.prototype.c = function() {return 7};" +
        "Foo.prototype.d = function() {return 7};" +
        "var aliasA = Foo.prototype.a;" + // Prevents devirtualization of a
        "var x = new Foo();" +
        "x.a();" +
        "var aliasC = (new Foo).c",
        // m2
        "");

    test(modules, new String[] {
        // m1
        "var Foo = function(){};" + /* constructor */
        "Foo.prototype.a = function() {this.a()};" +
        "Foo.prototype.b = function() {return 6};" +
        "var aliasB = Foo.prototype.b;" +
        "Foo.prototype.c = function() {return 7};" +
        "var aliasA = Foo.prototype.a;" + // Prevents devirtualization of a
        "var x = new Foo();" +
        "x.a();" +
        "var aliasC = (new Foo).c",
        // m2
        "Foo.prototype.d = function() {return 7};"
    });
  }

  public void testSpecializeDevirtualizePrototypeMethods() {
    JSModule[] modules = createModuleStar(
        // m1
        "/** @constructor */" +
        "var Foo = function(){};" + /* constructor */
        "Foo.prototype.a = function() {this.a();return 7};" +
        "Foo.prototype.b = function() {this.a()};" +
        "var x = new Foo();" +
        "x.a();",
        // m2
        "");

    test(modules, new String[] {
        // m1
        "var Foo = function(){};" + /* constructor */
        "var JSCompiler_StaticMethods_a =" +
              "function(JSCompiler_StaticMethods_a$self) {" +
           "JSCompiler_StaticMethods_a(JSCompiler_StaticMethods_a$self);" +
           "return 7" +
        "};" +
        "var x = new Foo();" +
        "JSCompiler_StaticMethods_a(x);",
        // m2
        "Foo.prototype.a = function() {this.a();return 7};" +
        "Foo.prototype.b = function() {this.a()};"
    });
  }

  public void testSpecializeDevirtualizePrototypeMethodsWithInline() {
    JSModule[] modules = createModuleStar(
        // m1
        "/** @constructor */" +
        "var Foo = function(){};" + /* constructor */
        "Foo.prototype.a = function() {return 7};" +
        "var x = new Foo();" +
        "var z = x.a();",
        // m2
        "");

    test(modules, new String[] {
        // m1
        "var Foo = function(){};" + /* constructor */
        "var x = new Foo();" +
        "var z = 7;",
        // m2
        "Foo.prototype.a = function() {return 7};"
    });
  }

  /**
   * Tests for {@link SpecializeModule.SpecializationState}.
   */
  public static class SpecializeModuleSpecializationStateTest
      extends CompilerTestCase {

    Compiler lastCompiler;

    SpecializationState lastState;

    @Override
    public CompilerPass getProcessor(final Compiler compiler) {
      lastCompiler = compiler;

      return new CompilerPass() {

        @Override
        public void process(Node externs, Node root) {
          SimpleDefinitionFinder defFinder =
              new SimpleDefinitionFinder(compiler);

          defFinder.process(externs, root);

          SimpleFunctionAliasAnalysis functionAliasAnalysis =
              new SimpleFunctionAliasAnalysis();

          functionAliasAnalysis.analyze(defFinder);

          lastState = new SpecializationState(functionAliasAnalysis);
        }
      };
    }

    public void testRemovedFunctions() {
      testSame("function F(){}\nvar G = function(a){};");

      assertEquals(ImmutableSet.of(), lastState.getRemovedFunctions());

      Node functionF = findFunction("F");

      lastState.reportRemovedFunction(functionF, functionF.getParent());
      assertEquals(ImmutableSet.of(functionF), lastState.getRemovedFunctions());

      Node functionG = findFunction("F");

      lastState.reportRemovedFunction(functionG, functionF.getParent());
      assertEquals(ImmutableSet.of(functionF, functionG),
          lastState.getRemovedFunctions());

      assertEquals(ImmutableSet.of(), lastState.getSpecializedFunctions());
    }

    public void testSpecializedFunctions() {
      testSame("function F(){}\nvar G = function(a){};");

      assertEquals(ImmutableSet.of(), lastState.getSpecializedFunctions());

      Node functionF = findFunction("F");

      lastState.reportSpecializedFunction(functionF);
      assertEquals(ImmutableSet.of(functionF),
          lastState.getSpecializedFunctions());

      Node functionG = findFunction("F");

      lastState.reportSpecializedFunction(functionG);
      assertEquals(ImmutableSet.of(functionF, functionG),
          lastState.getSpecializedFunctions());

      assertEquals(ImmutableSet.of(), lastState.getRemovedFunctions());
    }

    public void testCanFixupFunction() {
      testSame("function F(){}\n" +
               "var G = function(a){};\n" +
               "var ns = {};" +
               "ns.H = function(){};" +
               "var ns2 = {I : function anon1(){}};" +
               "(function anon2(){})();");

      assertTrue(lastState.canFixupFunction(findFunction("F")));
      assertTrue(lastState.canFixupFunction(findFunction("G")));
      assertTrue(lastState.canFixupFunction(findFunction("ns.H")));
      assertFalse(lastState.canFixupFunction(findFunction("anon1")));
      assertFalse(lastState.canFixupFunction(findFunction("anon2")));

      // Can't guarantee safe fixup for aliased functions
      testSame("function A(){}\n" +
          "var aliasA = A;\n");

      assertFalse(lastState.canFixupFunction(findFunction("A")));
    }

    private Node findFunction(String name) {
      FunctionFinder f = new FunctionFinder(name);
      new NodeTraversal(lastCompiler, f).traverse(lastCompiler.jsRoot);
      assertNotNull("Couldn't find " + name, f.found);
      return f.found;
    }

    /**
     * Quick Traversal to find a given function in the AST.
     */
    private class FunctionFinder extends AbstractPostOrderCallback {
      Node found = null;
      final String target;

      FunctionFinder(String target) {
        this.target = target;
      }

      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        if (n.isFunction()
            && target.equals(NodeUtil.getFunctionName(n))) {
          found = n;
        }
      }
    }
  }
}
