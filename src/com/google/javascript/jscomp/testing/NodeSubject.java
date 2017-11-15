/*
 * Copyright 2015 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.testing;

import static com.google.common.truth.Truth.assertAbout;
import static org.junit.Assert.assertEquals;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import javax.annotation.CheckReturnValue;

/**
 * A Truth Subject for the Node class. Usage:
 * <pre>
 *   import static com.google.javascript.jscomp.testing.NodeSubject.assertNode;
 *   ...
 *   assertNode(node1).isEqualTo(node2);
 *   assertNode(node1).hasType(Token.FUNCTION);
 * </pre>
 */
public final class NodeSubject extends Subject<NodeSubject, Node> {
  @CheckReturnValue
  public static NodeSubject assertNode(Node node) {
    return assertAbout(NodeSubject::new).that(node);
  }

  public NodeSubject(FailureMetadata failureMetadata, Node node) {
    super(failureMetadata, node);
  }

  public void isEqualTo(Node node) {
    String treeDiff = node.checkTreeEquals(actual());
    if (treeDiff != null) {
      failWithRawMessage("%s", treeDiff);
    }
  }

  public void hasType(Token type) {
    String message = "Node is of type " + actual().getToken() + " not of type " + type;
    assertEquals(message, type, actual().getToken());
  }

  public void hasCharno(int charno) {
    assertEquals(charno, actual().getCharno());
  }

  public void hasLineno(int lineno) {
    assertEquals(lineno, actual().getLineno());
  }

  public void hasLength(int length) {
    assertEquals(length, actual().getLength());
  }
}
