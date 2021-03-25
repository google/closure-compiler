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

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author johnlenz@google.com (John Lenz) */
@RunWith(JUnit4.class)
public final class SourceInformationAnnotatorTest {

  @Test
  public void testPreserveAnnotatedName() {
    Node root = new Node(Token.SCRIPT);
    Node name = Node.newString("foo");
    name.setOriginalName("bar");
    root.addChildToBack(name);

    NodeTraversal.traverse(new Compiler(), root, SourceInformationAnnotator.create());
    assertThat(name.getOriginalName()).isEqualTo("bar");
  }

  @Test
  public void testSetOriginalGetpropNames() {
    Node root = new Node(Token.SCRIPT);
    Node getprop = IR.getprop(IR.name("x"), "y");
    root.addChildToBack(IR.exprResult(getprop));

    NodeTraversal.traverse(new Compiler(), root, SourceInformationAnnotator.create());

    assertThat(getprop.getOriginalName()).isEqualTo("y");
  }

  @Test
  public void testSetOriginalOptChainGetpropNames() {
    Node root = new Node(Token.SCRIPT);
    Node getprop = IR.startOptChainGetprop(IR.name("x"), "y");
    root.addChildToBack(IR.exprResult(getprop));

    NodeTraversal.traverse(new Compiler(), root, SourceInformationAnnotator.create());

    assertThat(getprop.getOriginalName()).isEqualTo("y");
  }

  @Test
  public void doesNotSetOriginalStringName() {
    Node root = new Node(Token.SCRIPT);
    Node string = IR.string("x");
    root.addChildToBack(IR.exprResult(string));

    NodeTraversal.traverse(new Compiler(), root, SourceInformationAnnotator.create());

    // No need for the original name because strings are almost never mangled by JSCompiler and
    // source information mapping "identifier"s don't care about raw strings.
    assertThat(string.getOriginalName()).isNull();
  }
}
