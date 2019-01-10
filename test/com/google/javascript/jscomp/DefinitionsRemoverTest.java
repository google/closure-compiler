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

import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test for {@link DefinitionsRemover}. Basically test for the simple removal cases. More
 * complicated cases are tested by the clients of {@link DefinitionsRemover} such as {@link
 * PureFunctionIdentifierTest} and {@link RemoveUnusedCodeTest}.
 *
 */
@RunWith(JUnit4.class)
public final class DefinitionsRemoverTest extends CompilerTestCase {

  @Test
  public void testRemoveFunction() {
    setAcceptedLanguage(CompilerOptions.LanguageMode.ECMASCRIPT_2015);
    testSame("{(function (){bar()})}");
    test("{function a(){bar()}}", "{}");
    test("foo(); function a(){} bar()", "foo(); bar();");
    test("foo(); function a(){} function b(){} bar()", "foo(); bar();");
  }

  @Test
  public void testRemoveAssignment() {
    test("x = 0;", "0");
    test("{x = 0}", "{0}");
    test("x = 0; y = 0;", "0; 0;");
    test("for (x = 0;x;x) {};", "for(0;x;x) {};");
  }

  @Test
  public void testRemoveVarAssignment() {
    test("var x = 0;", "0");
    test("{var x = 0}", "{0}");
    test("var x = 0; var y = 0;", "0;0");
    test("var x = 0; var y = 0;", "0;0");
  }

  @Test
  public void testRemoveLiteral() {
    test("foo({ 'one' : 1 })", "foo({ })");
    test("foo({ 'one' : 1 , 'two' : 2 })", "foo({ })");
  }

  @Test
  public void testRemoveFunctionExpressionName() {
    test("foo(function f(){})", "foo(function (){})");
    test("var c = function() {}", "(function() {})");
  }

  @Test
  public void testRemoveClass() {
    test("class C {}", "");
    test("f(class C {})", "f(class {})");
    test("var c = class C {}", "(class{})");
  }

  @Test
  public void testRemoveClassMemberFunctions() {
    test("f(class {func(){}})", "f(class{})");
    test("f(class {static func(){}})", "f(class{})");
    testSame("f(class {[Symbol.iterator](){}})");
  }

  @Test
  public void testRemoveObjectMemberFunctions() {
    test("use({func(){}});", "use({});");
    test("use({x: 1, func(){}});", "use({});");
    test("use({func(){}, x: 1});", "use({});");
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    // Create a pass that removes all the definitions.
    return (Node externs, Node root) -> {
      DefinitionsGatherer definitionsGatherer = new DefinitionsGatherer();
      NodeTraversal.traverse(compiler, root, definitionsGatherer);
      for (Definition def : definitionsGatherer.definitions) {
        def.remove(compiler);
      }
    };
  }

  /**
   * Gather all possible definition objects.
   */
  private static class DefinitionsGatherer extends AbstractPostOrderCallback {
    final List<Definition> definitions = new ArrayList<>();
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      Definition def = DefinitionsRemover.getDefinition(n, false);
      if (def != null) {
        definitions.add(def);
      }
    }
  }
}
