/*
 * Copyright 2016 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PolymerPassFindExternsTest extends CompilerTestCase {

  private static final String EXTERNS =
      lines(
          MINIMAL_EXTERNS,
          "/** @constructor */",
          "var HTMLElement = function() {};",
          "/** @constructor @extends {HTMLElement} */",
          "var HTMLInputElement = function() {};",
          "/** @constructor @extends {HTMLElement} */",
          "var PolymerElement = function() {};",
          "/** @type {!Object} */",
          "PolymerElement.prototype.$;",
          "PolymerElement.prototype.created = function() {};",
          "PolymerElement.prototype.ready = function() {};",
          "PolymerElement.prototype.attached = function() {};",
          "PolymerElement.prototype.domReady = function() {};",
          "PolymerElement.prototype.detached = function() {};",
          "/**",
          " * Call the callback after a timeout. Calling job again with the same name",
          " * resets the timer but will not result in additional calls to callback.",
          " *",
          " * @param {string} name",
          " * @param {Function} callback",
          " * @param {number} timeoutMillis The minimum delay in milliseconds before",
          " *     calling the callback.",
          " */",
          "PolymerElement.prototype.job = function(name, callback, timeoutMillis) {};",
          "/**",
          " * @param a {!Object}",
          " * @return {!function()}",
          " */",
          "var Polymer = function(a) {};",
          "var alert = function(msg) {};");

  private PolymerPassFindExterns findExternsCallback;

  public PolymerPassFindExternsTest() {
    super(EXTERNS);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    findExternsCallback = new PolymerPassFindExterns();
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        NodeTraversal.traverse(compiler, externs, findExternsCallback);
      }
    };
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    allowExternsChanges();
    enableRunTypeCheckAfterProcessing();
    enableParseTypeInfo();
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Test
  public void testFindsPolymerElementRoot() {
    testSame("");
    Node polymerElementNode = findExternsCallback.getPolymerElementExterns();

    assertThat(polymerElementNode).isNotNull();
    assertThat(polymerElementNode.isVar()).isTrue();
    assertThat(polymerElementNode.getFirstChild().matchesQualifiedName("PolymerElement")).isTrue();
  }

  @Test
  public void testFindsPolymerElementProps() {
    testSame("");
    final ImmutableList<String> expectedProps = ImmutableList.of(
        "$", "created", "ready", "attached", "domReady", "detached", "job");
    ImmutableList<Node> polymerElementProps = findExternsCallback.getPolymerElementProps();

    assertThat(polymerElementProps).isNotNull();
    assertThat(polymerElementProps).hasSize(expectedProps.size());
    for (int i = 0; i < polymerElementProps.size(); ++i) {
      assertThat(getPropertyName(polymerElementProps.get(i))).isEqualTo(expectedProps.get(i));
    }
  }

  private String getPropertyName(Node node) {
    Node rightName = node.getFirstChild().getSecondChild();
    return rightName.isFunction()
        ? node.getFirstFirstChild().getSecondChild().getString() : rightName.getString();
  }
}
