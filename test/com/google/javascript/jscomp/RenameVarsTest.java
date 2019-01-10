/*
 * Copyright 2005 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link RenameVars}. */
@RunWith(JUnit4.class)
public final class RenameVarsTest extends CompilerTestCase {
  private static final String DEFAULT_PREFIX = "";
  private String prefix = DEFAULT_PREFIX;

  private VariableMap previouslyUsedMap =
      new VariableMap(ImmutableMap.<String, String>of());
  private RenameVars renameVars;
  private boolean withClosurePass = false;
  private boolean localRenamingOnly = false;
  private boolean preserveFunctionExpressionNames = false;
  private boolean useGoogleCodingConvention = true;
  private boolean generatePseudoNames = false;
  private boolean shouldShadow = false;
  private boolean preferStableNames = false;
  private boolean withNormalize = false;

  // NameGenerator to use, or null for a default.
  private DefaultNameGenerator nameGenerator = null;

  @Override
  protected CodingConvention getCodingConvention() {
    if (useGoogleCodingConvention) {
      return new GoogleCodingConvention();
    } else {
      return CodingConventions.getDefault();
    }
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    CompilerPass pass;
    if (withClosurePass) {
      pass = new ClosurePassAndRenameVars(compiler);
    } else if (nameGenerator != null) {
      pass =  renameVars = new RenameVars(compiler, prefix,
          localRenamingOnly, preserveFunctionExpressionNames,
          generatePseudoNames, shouldShadow, preferStableNames,
          previouslyUsedMap, null, null, nameGenerator);
    } else {
      pass =  renameVars = new RenameVars(compiler, prefix,
          localRenamingOnly, preserveFunctionExpressionNames,
          generatePseudoNames, shouldShadow, preferStableNames,
          previouslyUsedMap, null, null, new DefaultNameGenerator());
    }

    if (withNormalize) {
      // Don't use the standard CompilerTestCase normalization options
      // as renaming is a post denormalization operation, but we do still
      // want to run the normal normalizations on the input in some cases.
      pass = new NormalizePassWrapper(compiler, pass);
    }

    return pass;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    disableValidateAstChangeMarking();
    previouslyUsedMap = new VariableMap(ImmutableMap.<String, String>of());
    prefix = DEFAULT_PREFIX;
    withClosurePass = false;
    withNormalize = false;
    localRenamingOnly = false;
    preserveFunctionExpressionNames = false;
    generatePseudoNames = false;
    shouldShadow = false;
    preferStableNames = false;
    nameGenerator = null;
  }

  @Test
  public void testRenameSimple() {
    test("function Foo(v1, v2) {return v1;} Foo();",
         "function a(b, c) {return b;} a();");
  }

  @Test
  public void testRenameGlobals() {
    test("var Foo; var Bar, y; function x() { Bar++; }",
         "var a; var b, c; function d() { b++; }");
  }

  @Test
  public void testRenameLocals() {
    test("(function (v1, v2) {}); (function (v3, v4) {});",
        "(function (a, b) {}); (function (a, b) {});");
    test("function f1(v1, v2) {}; function f2(v3, v4) {};",
        "function c(a, b) {}; function d(a, b) {};");
  }

  @Test
  public void testRenameLocals_let() {
    test(
        "(function () { let var1 = 0; let another = 1; });",
        "(function () { let a = 0; let b = 1; });");
  }

  @Test
  public void testRenameLocals_const() {
    test(
        "(function () { const var1 = 0; const another = 1; });",
        "(function () { const a = 0; const b = 1; });");
  }

  @Test
  public void testRenameLocalsToSame() {
    preferStableNames = true;
    testSame("(function(a) {})");
    testSame("(function(a, b) {})");
    testSame("(function(a, b, c) {})");
    testSame("(function() { var a; })");
    testSame("(function() { var a, b; })");
    testSame("(function() { var a, b, c; })");
  }

  @Test
  public void testRenameRedeclaredGlobals() {
    test(
        lines(
            "function f1(v1, v2) {f1()};",
            "/** @suppress {duplicate} */",
            "function f1(v3, v4) {f1()};"),
        lines(
            "function a(b, c) {a()};",
            "/** @suppress {duplicate} */",
            "function a(b, c) {a()};"));

    localRenamingOnly = true;

    test(
        lines(
            "function f1(v1, v2) {f1()};",
            "/** @suppress {duplicate} */",
            "function f1(v3, v4) {f1()};"),
        lines(
            "function f1(a, b) {f1()};",
            "/** @suppress {duplicate} */",
            "function f1(a, b) {f1()};"));
  }

  @Test
  public void testRecursiveFunctions1() {
    test("var walk = function walk(node, aFunction) {" +
         "  walk(node, aFunction);" +
         "};",
         "var a = function a(b, c) {" +
         "  a(b, c);" +
         "};");

    localRenamingOnly = true;

    test("var walk = function walk(node, aFunction) {" +
         "  walk(node, aFunction);" +
         "};",
         "var walk = function walk(a, b) {" +
         "  walk(a, b);" +
         "};");
  }

  @Test
  public void testRecursiveFunctions2() {
    preserveFunctionExpressionNames = true;

    test("var walk = function walk(node, aFunction) {" +
         "  walk(node, aFunction);" +
         "};",
         "var c = function walk(a, b) {" +
         "  walk(a, b);" +
         "};");

    localRenamingOnly = true;

    test("var walk = function walk(node, aFunction) {" +
        "  walk(node, aFunction);" +
        "};",
        "var walk = function walk(a, b) {" +
        "  walk(a, b);" +
        "};");
  }

  @Test
  public void testRenameLocalsClashingWithGlobals() {
    test("function a(v1, v2) {return v1;} a();",
        "function a(b, c) {return b;} a();");
  }

  @Test
  public void testRenameNested() {
    test("function f1(v1, v2) { (function(v3, v4) {}) }",
         "function a(b, c) { (function(d, e) {}) }");
    test("function f1(v1, v2) { function f2(v3, v4) {} }",
         "function a(b, c) { function d(e, f) {} }");
  }

  @Test
  public void testBleedingRecursiveFunctions1() {
    // On IE, bleeding functions will interfere with each other if
    // they are in the same scope. In the below example, we want to be
    // sure that a and b get separate names.
    test("var x = function a(x) { return x ? 1 : a(1); };" +
         "var y = function b(x) { return x ? 2 : b(2); };",
         "var c = function b(a) { return a ? 1 : b(1); };" +
         "var e = function d(a) { return a ? 2 : d(2); };");
  }

  @Test
  public void testBleedingRecursiveFunctions2() {
    test(
        lines(
            "function f() {",
            "  var x = function a(x) { return x ? 1 : a(1); };",
            "  var y = function b(x) { return x ? 2 : b(2); };",
            "}"),
        lines(
            "function d() {",
            "  var e = function a(b) { return b ? 1 : a(1); };",
            "  var f = function c(a) { return a ? 2 : c(2); };",
            "}"));
  }

  @Test
  public void testBleedingRecursiveFunctions3() {
    test(
        lines(
            "function f() {",
            "  var x = function a(x) { return x ? 1 : a(1); };",
            "  var y = function b(x) { return x ? 2 : b(2); };",
            "  var z = function c(x) { return x ? y : c(2); };",
            "}"),
        lines(
            "function f() {",
            "  var g = function a(c) { return c ? 1 : a(1); };",
            "  var d = function b(a) { return a ? 2 : b(2); };",
            "  var h = function e(b) { return b ? d : e(2); };",
            "}"));
  }

  @Test
  public void testBleedingFunctionInBlocks() {
    test(lines(
            "if (true) {",
            "   var x = function a(x) {return x;}",
            "}"),
        lines(
            "if (true) {",
            "   var c = function b(a) {return a;}",
            "}"));
  }

  @Test
  public void testRenameWithExterns1() {
    String externs = "var foo;";
    test(
        externs(externs),
        srcs("var bar; foo(bar);"),
        expected("var a; foo(a);"));
  }

  @Test
  public void testRenameWithExterns2() {
    String externs = "var a;";
    test(
        externs(externs),
        srcs("var b = 5"),
        expected("var b = 5"));
  }

  @Test
  public void testDoNotRenameExportedName() {
    testSame("_foo()");
  }

  @Test
  public void testDoNotRenameArguments() {
    testSame("function a() { arguments; }");
  }

  @Test
  public void testRenameWithNameOverlap() {
    testSame("var a = 1; var b = 2; b + b;");
  }

  @Test
  public void testRenameWithPrefix1() {
    prefix = "PRE_";
    test("function Foo(v1, v2) {return v1} Foo();",
        "function PRE_(a, b) {return a} PRE_();");
    prefix = DEFAULT_PREFIX;

  }

  @Test
  public void testRenameWithPrefix2() {
    prefix = "PRE_";
    test("function Foo(v1, v2) {var v3 = v1 + v2; return v3;} Foo();",
        "function PRE_(a, b) {var c = a + b; return c;} PRE_();");
    prefix = DEFAULT_PREFIX;
  }

  @Test
  public void testRenameWithPrefix3() {
    prefix = "a";
    test("function Foo() {return 1;}" +
         "function Bar() {" +
         "  var a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v,w,x,y,z," +
         "      A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z,aa,ab;" +
         "  Foo();" +
         "} Bar();",

        "function a() {return 1;}" +
         "function aa() {" +
         "  var b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v,w,x,y,z,A," +
         "      B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z,$,ba,ca;" +
         "  a();" +
         "} aa();");
    prefix = DEFAULT_PREFIX;
  }

  @Test
  public void testNamingBasedOnOrderOfOccurrence() {
    test("var q,p,m,n,l,k; " +
             "try { } catch(r) {try {} catch(s) {}}; var t = q + q;",
         "var a,b,c,d,e,f; " +
             "try { } catch(g) {try {} catch(h) {}}; var i = a + a;"
         );
    test("(function(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z," +
         "a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v,w,x,y,z,$){});" +
         "var a4,a3,a2,a1,b4,b3,b2,b1,ab,ac,ad,fg;function foo(){};",
         "(function(a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v,w,x,y,z," +
         "A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z,$){});" +
         "var aa,ba,ca,da,ea,fa,ga,ha,ia,ja,ka,la;function ma(){};");
  }

  @Test
  public void testTryCatchLifeTime() {
    test("var q,p,m,n,l,k; " +
        "(function (r) {}); try { } catch(s) {}; var t = q + q;",
    "var a,c,d,e,f,g; " +
        "(function(b) {}); try { } catch(b) {}; var h = a + a;"
    );

    test("try {try {} catch(p) {}} catch(s) {};",
         "try {try {} catch(a) {}} catch(a) {};"
    );

    test(
        lines(
            "try {",
            "  try { ",
            "  } catch(p) {",
            "    try { ",
            "    } catch(r) {}",
            "  }",
            "} catch(s) {",
            "  try { ",
            "  } catch(q) {}",
            "};"),
        lines(
            "try {",
            "  try { ",
            "  } catch(a) {",
            "    try { ",
            "    } catch(b) {}",
            "  }",
            "} catch(a) {",
            "  try { ",
            "  } catch(b) {}",
            "};"));
  }

  @Test
  public void testStableRenameSimple() {
    VariableMap expectedVariableMap = makeVariableMap(
        "Foo", "a", "L 0", "b", "L 1", "c");
    testRenameMap("function Foo(v1, v2) {return v1;} Foo();",
                  "function a(b, c) {return b;} a();", expectedVariableMap);

    expectedVariableMap = makeVariableMap(
        "Foo", "a", "L 0", "b", "L 1", "c", "L 2", "d");
    testRenameMapUsingOldMap("function Foo(v1, v2, v3) {return v1;} Foo();",
         "function a(b, c, d) {return b;} a();", expectedVariableMap);
  }

  @Test
  public void testStableRenameGlobals() {
    VariableMap expectedVariableMap = makeVariableMap(
        "Foo", "a", "Bar", "b", "y", "c", "x", "d");
    testRenameMap("var Foo; var Bar, y; function x() { Bar++; }",
                  "var a; var b, c; function d() { b++; }",
                  expectedVariableMap);

    expectedVariableMap = makeVariableMap(
        "Foo", "a", "Bar", "b", "y", "c", "x", "d", "Baz", "f", "L 0" , "e");
    testRenameMapUsingOldMap(
        "var Foo, Baz; var Bar, y; function x(R) { return R + Bar++; }",
        "var a, f; var b, c; function d(e) { return e + b++; }",
        expectedVariableMap);
  }

  @Test
  public void testStableRenameWithPointlesslyAnonymousFunctions() {
    VariableMap expectedVariableMap = makeVariableMap("L 0", "a", "L 1", "b");
    testRenameMap("(function (v1, v2) {}); (function (v3, v4) {});",
                  "(function (a, b) {}); (function (a, b) {});",
                  expectedVariableMap);

    expectedVariableMap = makeVariableMap("L 0", "a", "L 1", "b", "L 2", "c");
    testRenameMapUsingOldMap("(function (v0, v1, v2) {});" +
                             "(function (v3, v4) {});",
                             "(function (a, b, c) {});" +
                             "(function (a, b) {});",
                             expectedVariableMap);
  }

  @Test
  public void testStableRenameLocalsClashingWithGlobals() {
    test("function a(v1, v2) {return v1;} a();",
         "function a(b, c) {return b;} a();");
    previouslyUsedMap = renameVars.getVariableMap();
    test("function bar(){return;}function a(v1, v2) {return v1;} a();",
         "function d(){return;}function a(b, c) {return b;} a();");
  }

  @Test
  public void testStableRenameNested() {
    VariableMap expectedVariableMap = makeVariableMap(
        "f1", "a", "L 0", "b", "L 1", "c", "L 2", "d", "L 3", "e");
    testRenameMap("function f1(v1, v2) { (function(v3, v4) {}) }",
                  "function a(b, c) { (function(d, e) {}) }",
                  expectedVariableMap);

    expectedVariableMap = makeVariableMap(
        "f1", "a", "L 0", "b", "L 1", "c", "L 2", "d", "L 3", "e", "L 4", "f");
    testRenameMapUsingOldMap(
        "function f1(v1, v2) { (function(v3, v4, v5) {}) }",
        "function a(b, c) { (function(d, e, f) {}) }",
        expectedVariableMap);
  }

  @Test
  public void testStableRenameWithExterns1() {
    String externs = "var foo;";
    test(
        externs(externs),
        srcs("var bar; foo(bar);"),
        expected("var a; foo(a);"));
    previouslyUsedMap = renameVars.getVariableMap();
    test(
        externs(externs),
        srcs("var bar, baz; foo(bar, baz);"),
        expected("var a, b; foo(a, b);"));
  }

  @Test
  public void testStableRenameWithExterns2() {
    String externs = "var a;";
    test(
        externs(externs),
        srcs("var b = 5"),
        expected("var b = 5"));
    previouslyUsedMap = renameVars.getVariableMap();
    test(
        externs(externs),
        srcs("var b = 5, catty = 9;"),
        expected("var b = 5, c=9;"));
  }

  @Test
  public void testStableRenameWithNameOverlap() {
    testSame("var a = 1; var b = 2; b + b;");
    previouslyUsedMap = renameVars.getVariableMap();
    testSame("var a = 1; var c, b = 2; b + b;");
  }

  @Test
  public void testStableRenameWithAnonymousFunctions() {
    VariableMap expectedVariableMap = makeVariableMap("L 0", "a", "foo", "b");
    testRenameMap("function foo(bar){return bar;}foo(function(h){return h;});",
                  "function b(a){return a}b(function(a){return a;})",
                  expectedVariableMap);

    expectedVariableMap = makeVariableMap("foo", "b", "L 0", "a", "L 1", "c");
    testRenameMapUsingOldMap(
        "function foo(bar) {return bar;}foo(function(g,h) {return g+h;});",
        "function b(a){return a}b(function(a,c){return a+c;})",
        expectedVariableMap);
  }

  @Test
  public void testStableRenameSimpleExternsChanges() {
    VariableMap expectedVariableMap = makeVariableMap(
        "Foo", "a", "L 0", "b", "L 1", "c");
    testRenameMap("function Foo(v1, v2) {return v1;} Foo();",
                  "function a(b, c) {return b;} a();", expectedVariableMap);

    expectedVariableMap = makeVariableMap("L 0", "b", "L 1", "c", "L 2", "a");
    String externs = "var Foo;";
    testRenameMapUsingOldMap(externs,
                             "function Foo(v1, v2, v0) {return v1;} Foo();",
                             "function Foo(b, c, a) {return b;} Foo();",
                             expectedVariableMap);
  }

  @Test
  public void testStableRenameSimpleLocalNameExterned() {
    test("function Foo(v1, v2) {return v1;} Foo();",
         "function a(b, c) {return b;} a();");

    previouslyUsedMap = renameVars.getVariableMap();

    String externs = "var b;";
    test(
        externs(externs),
        srcs("function Foo(v1, v2) {return v1;} Foo(b);"),
        expected("function a(d, c) {return d;} a(b);"));
  }

  @Test
  public void testStableRenameSimpleGlobalNameExterned() {
    test("function Foo(v1, v2) {return v1;} Foo();",
         "function a(b, c) {return b;} a();");

    previouslyUsedMap = renameVars.getVariableMap();

    String externs = "var Foo;";
    test(
        externs(externs),
        srcs("function Foo(v1, v2, v0) {return v1;} Foo();"),
        expected("function Foo(b, c, a) {return b;} Foo();"));
  }

  @Test
  public void testStableRenameWithPrefix1AndUnstableLocalNames() {
    prefix = "PRE_";
    test("function Foo(v1, v2) {return v1} Foo();",
         "function PRE_(a, b) {return a} PRE_();");

    previouslyUsedMap = renameVars.getVariableMap();

    prefix = "PRE_";
    test("function Foo(v0, v1, v2) {return v1} Foo();",
         "function PRE_(a, b, c) {return b} PRE_();");
  }

  @Test
  public void testStableRenameWithPrefix2() {
    prefix = "a";
    test("function Foo() {return 1;}" +
         "function Bar() {" +
         "  var a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v,w,x,y,z," +
         "      A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z,aa,ab;" +
         "  Foo();" +
         "} Bar();",

         "function a() {return 1;}" +
         "function aa() {" +
         "  var b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v,w,x,y,z,A," +
         "      B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z,$,ba,ca;" +
         "  a();" +
         "} aa();");

    previouslyUsedMap = renameVars.getVariableMap();

    prefix = "a";
    test("function Foo() {return 1;}" +
         "function Baz() {return 1;}" +
         "function Bar() {" +
         "  var a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v,w,x,y,z," +
         "      A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z,aa,ab;" +
         "  Foo();" +
         "} Bar();",

         "function a() {return 1;}" +
         "function ab() {return 1;}" +
         "function aa() {" +
         "  var b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v,w,x,y,z,A," +
         "      B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z,$,ba,ca;" +
         "  a();" +
         "} aa();");
  }

  @Test
  public void testContrivedExampleWhereConsistentRenamingIsWorse() {
    previouslyUsedMap = makeVariableMap(
        "Foo", "LongString", "L 0", "b", "L 1", "c");

    test("function Foo(v1, v2) {return v1;} Foo();",
         "function LongString(b, c) {return b;} LongString();");

    previouslyUsedMap = renameVars.getVariableMap();
    VariableMap expectedVariableMap = makeVariableMap(
        "Foo", "LongString", "L 0", "b", "L 1", "c");
    assertVariableMapsEqual(expectedVariableMap, previouslyUsedMap);
  }

  @Test
  public void testPrevUsedMapWithDuplicates() {
    try {
      makeVariableMap("Foo", "z", "Bar", "z");
      testSame("");
      throw new AssertionError();
    } catch (java.lang.IllegalArgumentException expected) {
    }
  }

  @Test
  public void testExportSimpleSymbolReservesName() {
    test("var goog, x; goog.exportSymbol('a', x);",
         "var a, b; a.exportSymbol('a', b);");
    withClosurePass = true;
    test("var goog, x; goog.exportSymbol('a', x);",
         "var b, c; b.exportSymbol('a', c);");
  }

  @Test
  public void testExportComplexSymbolReservesName() {
    test("var goog, x; goog.exportSymbol('a.b', x);",
         "var a, b; a.exportSymbol('a.b', b);");
    withClosurePass = true;
    test("var goog, x; goog.exportSymbol('a.b', x);",
         "var b, c; b.exportSymbol('a.b', c);");
  }

  @Test
  public void testExportToNonStringDoesntExplode() {
    withClosurePass = true;
    test("var goog, a, b; goog.exportSymbol(a, b);",
         "var a, b, c; a.exportSymbol(b, c);");
  }

  @Test
  public void testDollarSignSuperExport1() {
    useGoogleCodingConvention = false;
    // See http://blickly.github.io/closure-compiler-issues/#32
    test("var x = function($super,duper,$fantastic){}",
         "var c = function($super,    a,        b){}");

    localRenamingOnly = false;
    test("var $super = 1", "var a = 1");

    useGoogleCodingConvention = true;
    test("var x = function($super,duper,$fantastic){}",
         "var c = function($super,a,b){}");
  }

  @Test
  public void testDollarSignSuperExport2() {
    withNormalize = true;

    useGoogleCodingConvention = false;
    // See http://blickly.github.io/closure-compiler-issues/#32
    test("var x = function($super,duper,$fantastic){};" +
            "var y = function($super,duper){};",
         "var c = function($super,    a,         b){};" +
            "var d = function($super,    a){};");

    localRenamingOnly = false;
    test("var $super = 1", "var a = 1");

    useGoogleCodingConvention = true;
    test("var x = function($super,duper,$fantastic){};" +
            "var y = function($super,duper){};",
         "var c = function($super,   a,    b         ){};" +
            "var d = function($super,a){};");
  }

  @Test
  public void testBias() {
    nameGenerator = new DefaultNameGenerator(new HashSet<String>(), "", null);
    nameGenerator.favors("AAAAAAAAHH");
    test("var x, y", "var A, H");
  }

  @Test
  public void testPseudoNames() {
    generatePseudoNames = false;
    // See http://blickly.github.io/closure-compiler-issues/#32
    test("var foo = function(a, b, c){}",
         "var d = function(a, b, c){}");

    generatePseudoNames = true;
    test("var foo = function(a, b, c){}",
         "var $foo$$ = function($a$$, $b$$, $c$$){}");

    test("var a = function(a, b, c){}",
         "var $a$$ = function($a$$, $b$$, $c$$){}");
  }

  @Test
  public void testArrowFunctions() {
    test("foo => {return foo + 3;}",
        "a => {return a + 3;}");

    test("(foo, bar) => {return foo + bar + 3;}",
        "(a, b) => {return a + b + 3;}");
  }

  @Test
  public void testClasses() {
    test("class fooBar {}",
        "class a {}");

    test(
        lines(
            "class fooBar {",
            "  constructor(foo, bar) {",
            "    this.foo = foo;",
            "    this.bar = bar;",
            "  }",
            "}",
            "var x = new fooBar(2, 3);"),
        lines(
            "class a {",
            "  constructor(b, c) {",
            "    this.foo = b;",
            "    this.bar = c;",
            "  }",
            "}",
            "var d = new a(2, 3);"));

    test(
        lines(
            "class fooBar {",
            "  constructor(foo, bar) {",
            "    this.foo = foo;",
            "    this.bar = bar;",
            "  }",
            "  func(x) {",
            "    return this.foo + x;",
            "  }",
            "}",
            "var x = new fooBar(2,3);",
            "var abcd = x.func(5);"),
        lines(
            "class b {",
            "  constructor(a, c) {",
            "    this.foo = a;",
            "    this.bar = c;",
            "  }",
            "  func(a) {",
            "    return this.foo + a;",
            "  }",
            "}",
            "var d = new b(2,3);",
            "var e = d.func(5);"
            ));

  }

  @Test
  public void testLetConst() {
    test("let xyz;",
        "let a;"
    );

    test("const xyz = 1;",
        "const a = 1");

    test(
        lines(
            "let zyx = 1; {",
            "  const xyz = 1;",
            "  let zyx = 2;",
            "  zyx = 3;",
            "}",
            "let xyz = 'potato';",
            "zyx = 4;"
        ),
        lines(
            "let a = 1; {",
            "  const c = 1;",
            "  let b = 2;",
            "  b = 3;",
            "}",
            "let d = 'potato';",
            "a = 4;"));
  }

  @Test
  public void testGenerators() {
    test(
        lines(
            "function* gen() {",
            "  var xyz = 3;",
            "  yield xyz + 4;",
            "}",
            "gen().next()"
        ),
        lines(
            "function* a() {",
            "  var b = 3;",
            "  yield b + 4;",
            "}",
            "a().next()"));
  }

  @Test
  public void testForOf() {
    test(
        "for (var item of items) {}",
        "for (var a of items) {}");
  }

  @Test
  public void testTemplateStrings() {
    test(
        lines(
            "var name = 'Foo';",
            "`My name is ${name}`;"
        ),
        lines(
            "var a = 'Foo';",
            "`My name is ${a}`;"));
  }

  @Test
  public void testArrayDestructuring() {
    test("var [x, y, z] = [1, 2, 3];",
        "var [a, b, c] = [1, 2, 3];");
  }

  @Test
  public void testObjectDestructuring() {
    // TODO(sdh): Teach RenameVars to take advantage of shorthand properties by
    // building up a Map from var name strings to property name multisets.  We
    // should be able to treat this similar to the "previous names" map, where
    // we preferentially pick names with the most lined-up properties, provided
    // the property names are short (should be easy enough to do the math).
    // Note, the same property name could get different var names in different
    // scopes, so we probably need to do the comparison per scope.
    // Also, this is only relevant if language_out >= ES6.
    test(
        lines(
            "var obj = {p: 5, h: false};",
            "var {p, h} = obj;"),
        lines(
            "var a = {p: 5, h: false};",
            "var {p: b, h: c} = a;"));

   test(
        lines(
            "var obj = {p: 5, h: false};",
            "var {p: x, h: y} = obj;"),
        lines(
            "var a = {p: 5, h: false};",
            "var {p: b, h: c} = a;"));
  }

  @Test
  public void testDefaultFunction() {
    test(
        lines(
            "function f(x, y=12) {",
            "  return x * y;",
            "}"
        ),
        lines(
            "function c(a, b=12) {",
            "  return a * b;",
            "}"));
  }

  @Test
  public void testRestFunction() {
    test(
        lines(
            "function f(x, ...y) {",
            "  return x * y[0];",
            "}"
        ),
        lines(
            "function c(a, ...b) {",
            "  return a * b[0];",
            "}"));
  }

  @Test
  public void testObjectLiterals() {
    test(
        lines(
            "var objSuper = {",
            "  f: 'potato'",
            "};",
            "var obj = {",
            "  __proto__: objSuper,",
            "  g: false,",
            "  x() {",
            "    return super.f;",
            "  }",
            "};",
            "obj.x();"
        ),
        lines(
            "var a = {",
            "  f: 'potato'",
            "};",
            "var b = {",
            "  __proto__: a,",
            "  g: false,",
            "  x() {",
            "    return super.f;",
            "  }",
            "};",
            "b.x();"));
  }

  @Test
  public void testImport1() {
    test("import name from './other.js'; use(name);", "import a from './other.js'; use(a);");

    test(
        "import * as name from './other.js'; use(name);",
        "import * as a from './other.js'; use(a);");

    test(
        "import {default as name} from './other.js'; use(name);",
        "import {default as a} from './other.js'; use(a);");
  }

  @Test
  public void testImport2() {
    withNormalize = true;
    test(
        "import {name} from './other.js'; use(name);",
        "import {name as a} from './other.js'; use(a);");
  }

  private void testRenameMapUsingOldMap(String input, String expected,
                                        VariableMap expectedMap) {
    previouslyUsedMap = renameVars.getVariableMap();
    testRenameMap("", input, expected, expectedMap);
  }

  private void testRenameMapUsingOldMap(String externs, String input,
                                        String expected,
                                        VariableMap expectedMap) {
    previouslyUsedMap = renameVars.getVariableMap();
    testRenameMap(externs, input, expected, expectedMap);
  }

  private void testRenameMap(String input, String expected,
                             VariableMap expectedRenameMap) {
    testRenameMap("", input, expected, expectedRenameMap);
  }

  private void testRenameMap(String externs, String input, String expected,
                             VariableMap expectedRenameMap) {
    test(
        externs(externs),
        srcs(input),
        expected(expected));
    VariableMap renameMap = renameVars.getVariableMap();
    assertVariableMapsEqual(expectedRenameMap, renameMap);
  }

  @Test
  public void testPreferStableNames() {
    preferStableNames = true;
    // Locals in scopes with too many local variables (>1000) should
    // not receive temporary names (eg, 'L 123').  These locals will
    // appear in the name maps with the same name as in the code (eg,
    // 'a0' in this case).
    test(createManyVarFunction(1000), null);
    assertThat(renameVars.getVariableMap().lookupNewName("a0")).isNull();
    assertThat(renameVars.getVariableMap().lookupNewName("L 0")).isEqualTo("b");
    test(createManyVarFunction(1001), null);
    assertThat(renameVars.getVariableMap().lookupNewName("a0")).isEqualTo("b");
    assertThat(renameVars.getVariableMap().lookupNewName("L 0")).isNull();

    // With {@code preferStableNames} off locals should
    // unconditionally receive temporary names.
    preferStableNames = false;
    test(createManyVarFunction(1000), null);
    assertThat(renameVars.getVariableMap().lookupNewName("a0")).isNull();
    assertThat(renameVars.getVariableMap().lookupNewName("L 0")).isEqualTo("b");
    test(createManyVarFunction(1001), null);
    assertThat(renameVars.getVariableMap().lookupNewName("a0")).isNull();
    assertThat(renameVars.getVariableMap().lookupNewName("L 0")).isEqualTo("b");
  }

  private static String createManyVarFunction(int numVars) {
    List<String> locals = new ArrayList<>();
    for (int i = 0; i < numVars; i++) {
      locals.add("a" + i);
    }
    return "function foo() { var " + Joiner.on(",").join(locals) + "; }";
  }

  private VariableMap makeVariableMap(String... keyValPairs) {
    checkArgument(keyValPairs.length % 2 == 0);

    ImmutableMap.Builder<String, String> renameMap = ImmutableMap.builder();
    for (int i = 0; i < keyValPairs.length; i += 2) {
      renameMap.put(keyValPairs[i], keyValPairs[i + 1]);
    }

    return new VariableMap(renameMap.build());
  }

  private static void assertVariableMapsEqual(VariableMap a, VariableMap b) {
    Map<String, String> ma = a.getOriginalNameToNewNameMap();
    Map<String, String> mb = b.getOriginalNameToNewNameMap();
    assertWithMessage("VariableMaps not equal").that(mb).isEqualTo(ma);
  }

  private class ClosurePassAndRenameVars implements CompilerPass {
    private final Compiler compiler;

    private ClosurePassAndRenameVars(Compiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
      ProcessClosurePrimitives closurePass =
          new ProcessClosurePrimitives(
              compiler, null, CheckLevel.WARNING, false);
      closurePass.process(externs, root);
      renameVars = new RenameVars(compiler, prefix,
          false, false, false, false, false, previouslyUsedMap, null,
          closurePass.getExportedVariableNames(),
          new DefaultNameGenerator());
      renameVars.process(externs, root);
    }
  }

  private static class NormalizePassWrapper implements CompilerPass {
    private final Compiler compiler;
    private final CompilerPass wrappedPass;

    private NormalizePassWrapper(Compiler compiler,
        CompilerPass wrappedPass) {
      this.compiler = compiler;
      this.wrappedPass = wrappedPass;
    }

    @Override
    public void process(Node externs, Node root) {
      Normalize normalize = new Normalize(compiler, false);
      normalize.process(externs, root);

      wrappedPass.process(externs, root);
    }
  }
}
