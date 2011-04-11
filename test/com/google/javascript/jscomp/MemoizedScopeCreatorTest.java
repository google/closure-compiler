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

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

/**
 * A dopey test for {@link MemoizedScopeCreator}. This is mostly here
 * just so it's easy to write more tests if this becomes more complicated.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public class MemoizedScopeCreatorTest extends TestCase {

  public void testMemoization() throws Exception {
    Node trueNode = new Node(Token.TRUE);
    Node falseNode = new Node(Token.FALSE);
    // Wow, is there really a circular dependency between JSCompiler and
    // SyntacticScopeCreator?
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    ScopeCreator creator = new MemoizedScopeCreator(
        new SyntacticScopeCreator(compiler));
    Scope scopeA = creator.createScope(trueNode, null);
    assertSame(scopeA, creator.createScope(trueNode, null));
    assertNotSame(scopeA, creator.createScope(falseNode, null));
  }

  public void testPreconditionCheck() throws Exception {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    Node trueNode = new Node(Token.TRUE);
    ScopeCreator creator = new MemoizedScopeCreator(
        new SyntacticScopeCreator(compiler));
    Scope scopeA = creator.createScope(trueNode, null);

    boolean handled = false;
    try {
      creator.createScope(trueNode, scopeA);
    } catch (IllegalStateException e) {
      handled = true;
    }
    assertTrue(handled);
  }

}
