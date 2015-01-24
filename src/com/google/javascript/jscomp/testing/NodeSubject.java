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

import static org.junit.Assert.assertEquals;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * A Truth Subject for the Node class.
 */
public class NodeSubject extends Subject<NodeSubject, Node> {
  public static final SubjectFactory<NodeSubject, Node> NODE =
      new SubjectFactory<NodeSubject, Node>() {
        @Override
        public NodeSubject getSubject(FailureStrategy fs, Node target) {
          return new NodeSubject(fs, target);
        }
      };

  public NodeSubject(FailureStrategy fs, Node node) {
    super(fs, node);
  }

  public void hasType(int type) {
    String message = "Node is of type " + Token.name(getSubject().getType())
        + " not of type " + Token.name(type);
    assertEquals(message, type, getSubject().getType());
  }
}
