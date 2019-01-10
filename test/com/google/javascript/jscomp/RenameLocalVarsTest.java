/*
 * Copyright 2007 The Closure Compiler Authors.
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
 * Tests for {@link RenameVars}.
 *
 * @see RenameVarsTest
 */
@RunWith(JUnit4.class)
public final class RenameLocalVarsTest extends CompilerTestCase {
  private static final String DEFAULT_PREFIX = "";

  private String prefix = DEFAULT_PREFIX;

  // NameGenerator to use, or null for a default.
  private DefaultNameGenerator nameGenerator = null;

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    if (nameGenerator != null) {
      return new RenameVars(
          compiler, prefix, true, false, false, false, false,
          null, null, null, nameGenerator);
    } else {
      return new RenameVars(
          compiler, prefix, true, false, false, false, false,
          null, null, null,
          new DefaultNameGenerator());
    }
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    nameGenerator = null;
    disableValidateAstChangeMarking();
  }

  @Test
  public void testRenameSimple() {
    test("function Foo(v1, v2) {return v1;} Foo();",
         "function Foo(a, b) {return a;} Foo();");
  }

  @Test
  public void testRenameGlobals() {
    testSame("var Foo; var Bar, y; function x() { Bar++; }");
  }

  @Test
  public void testRenameLocals() {
    test("(function (v1, v2) {}); (function (v3, v4) {});",
         "(function (a, b) {}); (function (a, b) {});");
    test("function f1(v1, v2) {}; function f2(v3, v4) {};",
         "function f1(a, b) {}; function f2(a, b) {};");

  }

  @Test
  public void testRenameLocalsClashingWithGlobals() {
    test("function a(v1, v2) {return v1;} a();",
         "function a(b, c) {return b;} a();");
  }

  @Test
  public void testRenameNested() {
    test("function f1(v1, v2) { (function(v3, v4) {}) }",
         "function f1(a, b) { (function(c, d) {}) }");
    test("function f1(v1, v2) { function f2(v3, v4) {} }",
         "function f1(a, b) { function c(d, e) {} }");
  }

  @Test
  public void testRenameWithExterns1() {
    String externs = "var bar; function alert() {}";
    test(
        externs(externs),
        srcs("function foo(bar) { alert(bar); } foo(3)"),
        expected("function foo(a) { alert(a); } foo(3)"));
  }

  @Test
  public void testRenameWithExterns2() {
    test(
        externs("var a; function alert() {}"),
        srcs("function foo(bar) { alert(a);alert(bar); } foo(3);"),
        expected("function foo(b) { alert(a);alert(b); } foo(3);"));
  }

  @Test
  public void testDoNotRenameExportedName() {
    testSame("_foo()");
  }

  @Test
  public void testRenameWithNameOverlap() {
    test("function local() { var a = 1; var b = 2; b + b; }",
        "function local() { var b = 1; var a = 2; a + a; }");
  }

  @Test
  public void testRenameWithPrefix1() {
    prefix = "PRE_";
    test("function Foo(v1, v2) {return v1} Foo();",
         "function Foo(a, b) {return a} Foo();");
    prefix = DEFAULT_PREFIX;
  }

  @Test
  public void testRenameWithPrefix2() {
    prefix = "PRE_";
    test("function Foo(v1, v2) {var v3 = v1 + v2; return v3;} Foo();",
         "function Foo(a, b) {var c = a + b; return c;} Foo();");
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

         "function Foo() {return 1;}" +
         "function Bar() {" +
         "  var a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v,w,x,y,z,A,B,C," +
         "      D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z,$,aa;"  +
         "  Foo();" +
         "} Bar();");
    prefix = DEFAULT_PREFIX;
  }

  @Test
  public void testBias() {
    nameGenerator = new DefaultNameGenerator();
    nameGenerator.favors("AAAAAAAAHH");
    test("function foo(x,y){}", "function foo(A,H){}");
  }

  @Test
  public void testBias2() {
    nameGenerator = new DefaultNameGenerator();
    nameGenerator.favors("AAAAAAAAHH");
    test("function foo(x,y){ var z = z + z + z}",
         "function foo(H,a){ var A = A + A + A}");
  }
}
