/*
 * Copyright 2011 The Closure Compiler Authors.
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link ShadowVariables}.
 *
 */
@RunWith(JUnit4.class)
public final class ShadowVariablesTest extends CompilerTestCase {
  // Use pseudo names to make test easier to read.
  private boolean generatePseudoNames = false;
  private RenameVars pass = null;

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    pass = new RenameVars(
        compiler, "", false, false,
        generatePseudoNames, true, false, null, null, null,
        new DefaultNameGenerator());
    return  pass;
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT_2017);
    generatePseudoNames = false;
  }

  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();
    pass = null;
  }

  @Test
  public void testShadowSimple1() {
    test("function foo(x) { return function (y) {} }",
         "function   b(a) { return function (a) {} }");

    generatePseudoNames = true;

    test("function  foo  ( x  ) { return function ( y  ) {} }",
         "function $foo$$($x$$) { return function ($x$$) {} }");

  }

  @Test
  public void testShadowSimple2() {
    test("function foo(x,y) { return function (y,z) {} }",
         "function   c(a,b) { return function (a,b) {} }");

    generatePseudoNames = true;

    test("function  foo  ( x  , y  ) { return function ( y  , z  ) {} }",
         "function $foo$$($x$$,$y$$) { return function ($x$$,$y$$) {} }");
  }

  /** If we have a choice, pick out the most used variable to shadow. */
  @Test
  public void testShadowMostUsedVar() {
    generatePseudoNames = true;
    test("function  foo  () {var  x  ; var  y  ;  y  ; y  ; y  ; x  ;" +
         "  return function ( k  ) {} }",

         "function $foo$$() {var $x$$; var $y$$; $y$$;$y$$;$y$$;$x$$;" +
         "  return function ($y$$) {} }");
  }

  @Test
  public void testNoShadowReferencedVariables() {
    generatePseudoNames = true;
    // Unsafe to shadow function names on IE8
    test(lines(
        "function f1() {",
        "  var x; x; x; x;",
        "  return function f2(y) {",
        "    return function f3() {",
        "      x;",
        "    };",
        "  };",
        "}"),
        lines(
        "function $f1$$() {",
        "  var $x$$; $x$$; $x$$; $x$$;",
        "  return function $f2$$($y$$) {",
        "    return function $f3$$() {",
        "      $x$$;",
        "    };",
        "  };",
        "}"));
  }

  @Test
  public void testNoShadowGlobalVariables() {
    generatePseudoNames = true;
    test("var  x  ;  x  ; function  foo  () { return function ( y  ) {}}",
         "var $x$$; $x$$; function $foo$$() { return function ($y$$) {}}");
  }

  @Test
  public void testShadowBleedInFunctionName() {
    generatePseudoNames = true;
    test("function  foo  () { function  b  ( y  ) { y  }  b  ;  b  ;}",
         "function $foo$$() { function $b$$($b$$) {$b$$} $b$$; $b$$;}");
   }

  @Test
  public void testNoShadowLessPopularName() {
    generatePseudoNames = true;
    // We make sure that y doesn't pick x as a shadow and remains to be renamed
    // to 'a'.
    // If we do shadow y with whatever x renames to (say b) we will
    // get 4 b's and 7 a's while currently we get 3 b's and 8 a's.
    // I believe this arrangement will always be better for gzipping.
    test("function  f1  ( x  ) {" +
         "  function  f2  ( y  ) {}  x  ; x  ;}" +
         "function  f3  ( i  ) {" +
         "  var  k  ; var  j  ; j  ; j  ; j  ; j  ; j  ; j  ;}",

         "function $f1$$($x$$) {" +
         "  function $f2$$($y$$) {} $x$$;$x$$;}" +
         "function $f3$$($i$$) {" +
         "  var $k$$; var $j$$;$j$$;$j$$;$j$$;$j$$;$j$$;$j$$;}");
  }

  @Test
  public void testShadowFunctionName() {
    generatePseudoNames = true;
    test("var  g   = function() {" +
         "  var  x  ; return function(){function  y  (){}}}",
         "var $g$$ = function() {" +
         "  var $x$$; return function(){function $x$$(){}}}");
  }

  @Test
  public void testShadowLotsOfScopes1() {
    generatePseudoNames = true;
    test("var  g   = function( x  ) { return function() { return function() {" +
         " return function() { var  y   }}}}",
         "var $g$$ = function($x$$) { return function() { return function() {" +
         " return function() { var $x$$ }}}}");
  }

  @Test
  public void testShadowLotsOfScopes2() {
    generatePseudoNames = true;
    // 'y' doesn't have a candidate to shadow due to upward referencing.
    test("var  g   = function( x  ) { return function( y  ) " +
         " {return function() {return function() {  x   }}}}",
         "var $g$$ = function($x$$) { return function($y$$) " +
         " {return function() {return function() { $x$$ }}}}");

    test("var  g   = function( x  ) { return function() " +
        " {return function( y  ) {return function() {  x   }}}}",
        "var $g$$ = function($x$$) { return function() " +
        " {return function($y$$) {return function() { $x$$ }}}}");

    test("var  g   = function( x  ) { return function() " +
        " {return function() {return function( y  ) {  x   }}}}",
        "var $g$$ = function($x$$) { return function() " +
        " {return function() {return function($y$$) { $x$$ }}}}");
  }

  @Test
  public void testShadowLotsOfScopes3() {
    generatePseudoNames = true;
    // 'y' doesn't have a candidate to shadow due to upward referencing.
    test("var  g   = function( x  ) { return function() " +
        " {return function() {return function() {  x   }; var  y   }}}",
        "var $g$$ = function($x$$) { return function() " +
        " {return function() {return function() { $x$$ }; var $y$$}}}");
    test("var  g   = function( x  ) { return function() " +
        " {return function() {return function() {  x   }}; var  y   }}",
        "var $g$$ = function($x$$) { return function() " +
        " {return function() {return function() { $x$$ }}; var $y$$}}");
    test("var  g   = function( x  ) { return function() " +
        " {return function() {return function() {  x   }}}; var  y   }",
        "var $g$$ = function($x$$) { return function() " +
        " {return function() {return function() { $x$$ }}}; var $y$$}");
  }

  @Test
  public void testShadowLotsOfScopes4() {
    // Make sure we do get the optimal shadowing scheme where
    test("var g = function(x) { return function() { return function() {" +
         " return function(){return function(){};var m};var n};var o}}",
         "var b = function(a) { return function() { return function() {" +
         " return function(){return function(){};var a};var a};var a}}");
  }

  @Test
  public void testShadowLotsOfScopes5() {
    generatePseudoNames = true;
    test("var  g   = function( x  ) {" +
         " return function() { return function() {" +
         " return function() { return function() {" +
         "      x  }; o  };var  n  };var  o  };var  p  }",
         "var $g$$ = function($x$$) {" +
         " return function() { return function() {" +
         " return function() { return function() {" +
         "     $x$$};$o$$};var $p$$};var $o$$};var $p$$}");

    test("var  g   = function( x  ) {" +
        " return function() { return function() {" +
        " return function() { return function() {" +
        "      x  }; p  };var  n  };var  o  };var  p  }",
        "var $g$$ = function($x$$) {" +
        " return function() { return function() {" +
        " return function() { return function() {" +
        "     $x$$};$p$$};var $o$$};var $o$$};var $p$$}");
  }

  @Test
  public void testShadowWithShadowAlready() {
    test("var g = function(x) { return function() { return function() {" +
         " return function(){return function(){x}};var p};var o};var p}",
         "var c = function(b) { return function() { return function() {" +
         " return function(){return function(){b}};var a};var a};var a}");

    test("var g = function(x) { return function() { return function() {" +
         " return function(){return function(){x};p};var p};var o};var p}",
         "var c = function(b) { return function() { return function() {" +
         " return function(){return function(){b};a};var a};var a};var a}");
  }

  @Test
  public void testShadowBug1() {
    generatePseudoNames = true;
    test("function  f  ( x  ) { return function( y  ) {" +
         "    return function( x  ) {  x   +  y  ; }}}",
         "function $f$$($x$$) { return function($y$$) {" +
         "    return function($x$$) { $x$$ + $y$$; }}}");
  }

  @Test
  public void testOptimal() {
    // A test for a case that wasn't optimal in a single pass algorithm.
    test("function f(x) { function g(y) { function h(x) {}}}",
         "function c(a) { function b(a) { function b(a) {}}}");
  }

  @Test
  public void testSharingAcrossInnerScopes() {
    test("function f() {var f=function g(){g()}; var x=function y(){y()}}",
         "function c() {var d=function a(){a()}; var e=function b(){b()}}");
    test("function f(x) { return x ? function(y){} : function(z) {} }",
         "function b(a) { return a ? function(a){} : function(a) {} }");
  }

  @Test
  public void testExportedLocal1() {
    test("function f(a) { a();a();a(); return function($super){} }",
         "function b(a) { a();a();a(); return function($super){} }");
  }

  @Test
  public void testExportedLocal2() {
    test("function f($super) { $super();$super(); return function(a){} }",
         "function a($super) { $super();$super(); return function(b){} }");
  }

  @Test
  public void testRenameMapHasNoDuplicates() {
    test("function foo(x) { return function (y) {} }",
         "function   b(a) { return function (a) {} }");

    VariableMap vm = pass.getVariableMap();
    try {
      vm.getNewNameToOriginalNameMap();
    } catch (java.lang.IllegalArgumentException unexpected) {
      throw new AssertionError(
          "Invalid VariableMap generated: " + vm.getOriginalNameToNewNameMap(), unexpected);
    }
  }

  @Test
  public void testBug4172539() {
    // All the planets must line up. When we look at the 2nd inner function,
    // y can shadow x, also m can shadow x as well. Now all that is left for
    // n to shadow is 'y'. Now because y has already shadowed x, the pseudo
    // name maps has already updated y gets $x$$. This mean n will be updated
    // with "$x$$" in the name map which is incorrect. That is the reason
    // why we can't update the pseudo name map on-the-fly.

    generatePseudoNames = true;
    test(
        lines(
            "function f(x) {",
            "  x;x;x;",
            "  return function (y) { y; x };",
            "  return function (y) {",
            "    y;",
            "    return function (m, n) {",
            "       m;m;m;",
            "    };",
            "  };",
            "}"),
        lines(
            "function $f$$($x$$) {",
            "  $x$$;$x$$;$x$$;",
            "  return function ($y$$) { $y$$; $x$$ };",
            "  return function ($x$$) {",
            "    $x$$;",
            "    return function ($x$$, $y$$) {",
            "       $x$$;$x$$;$x$$;",
            "    };",
            "  };",
            "}"));
  }

  @Test
  public void testBlocks() {
    // Unsafe to shadow nested "var"s
    test(lines(
        "function f() {",
        "  var x = 1;",
        "  {",
        "    var y = 2;",
        "    {",
        "      var z = 3;",
        "    }",
        "  }",
        "}"),
        lines(
        "function a() {",
        "  var b = 1;",
        "  {",
        "    var c = 2;",
        "    {",
        "      var d = 3;",
        "    }",
        "  }",
        "}"));

    // Safe to shadow nested "let"s
    test(lines(
        "function f() {",
        "  let x = 1;",
        "  {",
        "    let y = 2;",
        "    {",
        "      let z = 3;",
        "    }",
        "  }",
        "}"),
        lines(
        "function b() {",
        "  let a = 1;",
        "  {",
        "    let a = 2;",
        "      {",
        "        let a = 3;",
        "      }",
        "  }",
        "}"));

    test(lines(
        "function f() {",
        "  let x = 1;",
        "  {",
        "    let y = x;",
        "    {",
        "      let z = y;",
        "      let w = x;",
        "    }",
        "  }",
        "}"),
        lines(
        "function c() {",
        "  let a = 1;",
        "  {",
        "    let b = a;",
        "    {",
        "      let d = b;",
        "      let e = a;",
        "    }",
        "  }",
        "}"));
  }

  @Test
  public void testCatch() {
    // Unsafe to shadow caught exceptions on IE8 since they are not block scoped
    test(lines(
        "function f(a) {",
        "  try {",
        "  } catch (e) {",
        "  }",
        "}"),
        lines(
        "function a(b) {",
        "  try {",
        "  } catch (c) {",
        "  }",
        "}"));
  }
}
