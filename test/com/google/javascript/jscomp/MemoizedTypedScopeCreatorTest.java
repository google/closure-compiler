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
 * A dopey test for {@link MemoizedTypedScopeCreator}. This is mostly here
 * just so it's easy to write more tests if this becomes more complicated.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public final class MemoizedTypedScopeCreatorTest extends TestCase {

  public void testMemoization() throws Exception {
    Node root1 = new Node(Token.ROOT);
    Node root2 = new Node(Token.ROOT);
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    ScopeCreator creator = new MemoizedTypedScopeCreator(
        SyntacticScopeCreator.makeTyped(compiler));
    Scope scopeA = creator.createScope(root1, null);
    assertSame(scopeA, creator.createScope(root1, null));
    assertNotSame(scopeA, creator.createScope(root2, null));
  }

  public void testPreconditionCheck() throws Exception {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    Node root = new Node(Token.ROOT);
    ScopeCreator creator = new MemoizedTypedScopeCreator(
        SyntacticScopeCreator.makeTyped(compiler));
    Scope scopeA = creator.createScope(root, null);

    boolean handled = false;
    try {
      creator.createScope(root, scopeA);
    } catch (IllegalStateException e) {
      handled = true;
    }
    assertTrue(handled);
  }

}
