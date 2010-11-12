/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Inline function tests.
 * @author johnlenz@google.com (John Lenz)
 */
public class FunctionArgumentInjectorTest extends TestCase {

  // TODO(johnlenz): Add unit tests for:
  //    inject
  //    getFunctionCallParameterMap

  private static final Set<String> EMPTY_STRING_SET = Collections.emptySet();

  public void testFindModifiedParameters1() {
    assertEquals(Sets.newHashSet(),
        FunctionArgumentInjector.findModifiedParameters(
            parseFunction("function (a){ return a==0; }")));
  }

  public void testFindModifiedParameters2() {
    assertEquals(Sets.newHashSet(),
        FunctionArgumentInjector.findModifiedParameters(
            parseFunction("function (a){ b=a }")));
  }

  public void testFindModifiedParameters3() {
    assertEquals(Sets.newHashSet("a"),
        FunctionArgumentInjector.findModifiedParameters(
            parseFunction("function (a){ a=0 }")));
  }

  public void testFindModifiedParameters4() {
    assertEquals(Sets.newHashSet("a", "b"),
        FunctionArgumentInjector.findModifiedParameters(
            parseFunction("function (a,b){ a=0;b=0 }")));
  }

  public void testFindModifiedParameters5() {
    assertEquals(Sets.newHashSet("b"),
        FunctionArgumentInjector.findModifiedParameters(
            parseFunction("function (a,b){ a; if (a) b=0 }")));
  }

  public void testMaybeAddTempsForCallArguments1() {
    // Parameters with side-effects must be executed
    // even if they aren't referenced.
    testNeededTemps(
        "function foo(a,b){}; foo(goo(),goo());",
        "foo",
        Sets.newHashSet("a", "b"));
  }

  public void testMaybeAddTempsForCallArguments2() {
    // Unreferenced parameters without side-effects
    // can be ignored.
    testNeededTemps(
        "function foo(a,b){}; foo(1,2);",
        "foo",
        EMPTY_STRING_SET);
  }

  public void testMaybeAddTempsForCallArguments3() {
    // Referenced parameters without side-effects
    // don't need temps.
    testNeededTemps(
        "function foo(a,b){a;b;}; foo(x,y);",
        "foo",
        EMPTY_STRING_SET);
  }

  public void testMaybeAddTempsForCallArguments4() {
    // Parameters referenced after side-effect must
    // be assigned to temps.
    testNeededTemps(
        "function foo(a,b){a;goo();b;}; foo(x,y);",
        "foo",
        Sets.newHashSet("b"));
  }

  public void testMaybeAddTempsForCallArguments5() {
    // Parameters referenced after out-of-scope side-effect must
    // be assigned to temps.
    testNeededTemps(
        "function foo(a,b){x = b; y = a;}; foo(x,y);",
        "foo",
        Sets.newHashSet("a"));
  }

  public void testMaybeAddTempsForCallArguments6() {
    // Parameter referenced after a out-of-scope side-effect must
    // be assigned to a temp.
    testNeededTemps(
        "function foo(a){x++;a;}; foo(x);",
        "foo",
        Sets.newHashSet("a"));
  }

  public void testMaybeAddTempsForCallArguments7() {
    // No temp needed after local side-effects.
    testNeededTemps(
        "function foo(a){var c; c=0; a;}; foo(x);",
        "foo",
        EMPTY_STRING_SET);
  }

  public void testMaybeAddTempsForCallArguments8() {
    // Temp needed for side-effects to object using local name.
    testNeededTemps(
        "function foo(a){var c = {}; c.goo=0; a;}; foo(x);",
        "foo",
        Sets.newHashSet("a"));
  }

  public void testMaybeAddTempsForCallArguments9() {
    // Parameters referenced in a loop with side-effects must
    // be assigned to temps.
    testNeededTemps(
        "function foo(a,b){while(true){a;goo();b;}}; foo(x,y);",
        "foo",
        Sets.newHashSet("a", "b"));
  }

  public void testMaybeAddTempsForCallArguments10() {
    // No temps for parameters referenced in a loop with no side-effects.
    testNeededTemps(
        "function foo(a,b){while(true){a;true;b;}}; foo(x,y);",
        "foo",
        EMPTY_STRING_SET);
  }

  public void testMaybeAddTempsForCallArguments11() {
    // Parameters referenced in a loop with side-effects must
    // be assigned to temps.
    testNeededTemps(
        "function foo(a,b){do{a;b;}while(goo());}; foo(x,y);",
        "foo",
        Sets.newHashSet("a", "b"));
  }

  public void testMaybeAddTempsForCallArguments12() {
    // Parameters referenced in a loop with side-effects must
    // be assigned to temps.
    testNeededTemps(
        "function foo(a,b){for(;;){a;b;goo();}}; foo(x,y);",
        "foo",
        Sets.newHashSet("a", "b"));
  }

  public void testMaybeAddTempsForCallArguments13() {
    // Parameters referenced in a inner loop without side-effects must
    // be assigned to temps if the outer loop has side-effects.
    testNeededTemps(
        "function foo(a,b){for(;;){for(;;){a;b;}goo();}}; foo(x,y);",
        "foo",
        Sets.newHashSet("a", "b"));
  }

  public void testMaybeAddTempsForCallArguments14() {
    // Parameters referenced in a loop must
    // be assigned to temps.
    testNeededTemps(
        "function foo(a,b){goo();for(;;){a;b;}}; foo(x,y);",
        "foo",
        Sets.newHashSet("a", "b"));
  }  

  public void testMaybeAddTempsForCallArguments20() {
    // A long string referenced more than once should have a temp.
    testNeededTemps(
        "function foo(a){a;a;}; foo(\"blah blah\");",
        "foo",
        Sets.newHashSet("a"));
  }

  public void testMaybeAddTempsForCallArguments21() {
    // A short string referenced once should not have a temp.
    testNeededTemps(
        "function foo(a){a;a;}; foo(\"\");",
        "foo",
        EMPTY_STRING_SET);
  }

  public void testMaybeAddTempsForCallArguments22() {
    // A object literal not referenced.
    testNeededTemps(
        "function foo(a){}; foo({x:1});",
        "foo",
        EMPTY_STRING_SET);
    // A object literal referenced, should have a temp.
    testNeededTemps(
        "function foo(a){a;}; foo({x:1});",
        "foo",
        Sets.newHashSet("a"));
    // A object literal, referenced more than once, should have a temp.
    testNeededTemps(
        "function foo(a){a;a;}; foo({x:1});",
        "foo",
        Sets.newHashSet("a"));
  }

  public void testMaybeAddTempsForCallArguments23() {
    // A array literal, not referenced.
    testNeededTemps(
        "function foo(a){}; foo([1,2]);",
        "foo",
        EMPTY_STRING_SET);
    // A array literal, referenced once, should have a temp.
    testNeededTemps(
        "function foo(a){a;}; foo([1,2]);",
        "foo",
        Sets.newHashSet("a"));
    // A array literal, referenced more than once, should have a temp.
    testNeededTemps(
        "function foo(a){a;a;}; foo([1,2]);",
        "foo",
        Sets.newHashSet("a"));
  }

  public void testMaybeAddTempsForCallArguments24() {
    // A regex literal, not referenced.
    testNeededTemps(
        "function foo(a){}; foo(/mac/);",
        "foo",
        EMPTY_STRING_SET);
    // A regex literal, referenced once, should have a temp.
    testNeededTemps(
        "function foo(a){a;}; foo(/mac/);",
        "foo",
        Sets.newHashSet("a"));
    // A regex literal, referenced more than once, should have a temp.
    testNeededTemps(
        "function foo(a){a;a;}; foo(/mac/);",
        "foo",
        Sets.newHashSet("a"));
  }

  public void testMaybeAddTempsForCallArguments25() {
    // A side-effect-less constructor, not referenced.
    testNeededTemps(
        "function foo(a){}; foo(new Date());",
        "foo",
        EMPTY_STRING_SET);
    // A side-effect-less constructor, referenced once, should have a temp.
    testNeededTemps(
        "function foo(a){a;}; foo(new Date());",
        "foo",
        Sets.newHashSet("a"));
    // A side-effect-less constructor, referenced more than once, should have
    // a temp.
    testNeededTemps(
        "function foo(a){a;a;}; foo(new Date());",
        "foo",
        Sets.newHashSet("a"));
  }

  public void testMaybeAddTempsForCallArguments26() {
    // A constructor, not referenced.
    testNeededTemps(
        "function foo(a){}; foo(new Bar());",
        "foo",
        Sets.newHashSet("a"));
    // A constructor, referenced once, should have a temp.
    testNeededTemps(
        "function foo(a){a;}; foo(new Bar());",
        "foo",
        Sets.newHashSet("a"));
    // A constructor, referenced more than once, should have a temp.
    testNeededTemps(
        "function foo(a){a;a;}; foo(new Bar());",
        "foo",
        Sets.newHashSet("a"));
  }

  public void testMaybeAddTempsForCallArguments27() {
    // Ensure the correct parameter is given a temp, when there is
    // a this value in the call.
    testNeededTemps(
        "function foo(a,b,c){}; foo.call(this,1,goo(),2);",
        "foo",
        Sets.newHashSet("b"));
  }

  public void testMaybeAddTempsForCallArgumentsInLoops() {
    // A mutable parameter referenced in loop needs a
    // temporary.
    testNeededTemps(
        "function foo(a){for(;;)a;}; foo(new Bar());",
        "foo",
        Sets.newHashSet("a"));

    testNeededTemps(
        "function foo(a){while(true)a;}; foo(new Bar());",
        "foo",
        Sets.newHashSet("a"));

    testNeededTemps(
        "function foo(a){do{a;}while(true)}; foo(new Bar());",
        "foo",
        Sets.newHashSet("a"));
  }

  private void testNeededTemps(
      String code, String fnName, Set<String> expectedTemps) {
    Node n = parse(code);
    Node fn = findFunction(n, fnName);
    assertNotNull(fn);
    Node call = findCall(n, fnName);
    assertNotNull(call);
    Map<String, Node> args =
      FunctionArgumentInjector.getFunctionCallParameterMap(
          fn, call, getNameSupplier());

    Set<String> actualTemps = Sets.newHashSet();
    FunctionArgumentInjector.maybeAddTempsForCallArguments(
        fn, args, actualTemps, new ClosureCodingConvention());

    assertEquals(expectedTemps, actualTemps);
  }

  private static Supplier<String> getNameSupplier() {
    return new Supplier<String>() {
      int i = 0;
      public String get() {
        return String.valueOf(i++);
      }
    };
  }

  private static Node findCall(Node n, String name) {
    if (n.getType() == Token.CALL) {
      Node callee;
      if (NodeUtil.isGet(n.getFirstChild())) {
        callee = n.getFirstChild().getFirstChild();
        Node prop = callee.getNext();
        // Only "call" is support at this point.
        Preconditions.checkArgument(prop.getType() == Token.STRING &&
            prop.getString().equals("call"));
      } else {
        callee = n.getFirstChild();
      }

      if (callee.getType() == Token.NAME
          && callee.getString().equals(name)) {
        return n;
      }
    }

    for (Node c : n.children()) {
      Node result = findCall(c, name);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  private static Node findFunction(Node n, String name) {
    if (n.getType() == Token.FUNCTION) {
      if (n.getFirstChild().getString().equals(name)) {
        return n;
      }
    }

    for (Node c : n.children()) {
      Node result = findFunction(c, name);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  private static Node parseFunction(String js) {
    return parse(js).getFirstChild();
  }

  private static Node parse(String js) {
    Compiler compiler = new Compiler();
    Node n = compiler.parseTestCode(js);
    assertEquals(0, compiler.getErrorCount());
    return n;
  }
}
