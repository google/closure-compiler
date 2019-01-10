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

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author johnlenz@google.com (John Lenz) */
@RunWith(JUnit4.class)
public final class SourceInformationAnnotatorTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        NodeTraversal.traverse(compiler, root,
            new SourceInformationAnnotator("", false));
      }};
  }

  @Test
  public void testPreserveAnnotatedName() {
    Node root = new Node(Token.SCRIPT);
    Node name = Node.newString("foo");
    name.setOriginalName("bar");
    root.addChildToBack(name);

    NodeTraversal.traverse(new Compiler(), root,
        new SourceInformationAnnotator("", false));
    assertThat(name.getOriginalName()).isEqualTo("bar");
  }
}
