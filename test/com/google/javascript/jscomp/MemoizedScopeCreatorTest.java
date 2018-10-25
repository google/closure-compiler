/*
 * Copyright 2017 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** A tests for {@link MemoizedScopeCreator}. */
@RunWith(JUnit4.class)
public final class MemoizedScopeCreatorTest {

  @Test
  public void testMemoization() {
    Node root1 = new Node(Token.ROOT);
    Node root2 = new Node(Token.ROOT);
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    ScopeCreator creator = new MemoizedScopeCreator(new Es6SyntacticScopeCreator(compiler));
    Scope scopeA = (Scope) creator.createScope(root1, null);
    assertThat(creator.createScope(root1, null)).isSameAs(scopeA);
    assertThat(creator.createScope(root2, null)).isNotSameAs(scopeA);
  }

  @Test
  public void testPreconditionCheck() {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    Node root = new Node(Token.ROOT);
    ScopeCreator creator = new MemoizedScopeCreator(new Es6SyntacticScopeCreator(compiler));
    Scope scopeA = (Scope) creator.createScope(root, null);

    boolean handled = false;
    try {
      creator.createScope(root, scopeA);
    } catch (IllegalStateException e) {
      handled = true;
    }
    assertThat(handled).isTrue();
  }
}
