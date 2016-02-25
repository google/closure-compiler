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

import com.google.common.base.Predicates;
import com.google.javascript.rhino.Node;

public final class PolymerClassDefinitionTest extends CompilerTypeTestCase {

  private Node polymerCall;

  @Override
  protected void setUp() {
    super.setUp();
    polymerCall = null;
  }

  // TODO(jlklein): Add more complex test cases and verify behaviors and descriptors.

  public void testSimpleBehavior() {
    PolymerClassDefinition def = parseAndExtractClassDef(
        LINE_JOINER.join(
            "/** @polymerBehavior */",
            "var FunBehavior = {",
            "  properties: {",
            "    /** @type {boolean} */",
            "    isFun: {",
            "      type: Boolean,",
            "      value: true,",
            "    }",
            "  },",
            "  listeners: {",
            "    click: 'doSomethingFun',",
            "  },",
            "  /** @type {string} */",
            "  foo: 'hooray',",
            "",
            "  /** @param {string} funAmount */",
            "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "  /** @override */",
            "  created: function() {}",
            "};",
            "var A = Polymer({",
            "  is: 'x-element',",
            "  properties: {",
            "    pets: {",
            "      type: Array,",
            "      notify: true,",
            "    },",
            "    name: String,",
            "  },",
            "  behaviors: [ FunBehavior ],",
            "});"));

    assertNotNull(def);
    assertTrue(def.target.isName());
    assertEquals("A", def.target.getString());
    assertNull(def.nativeBaseElement);
    assertThat(def.behaviors).hasSize(1);
    assertThat(def.props).hasSize(3);
  }

  private PolymerClassDefinition parseAndExtractClassDef(String code) {
    Node rootNode = compiler.parseTestCode(code);
    GlobalNamespace globalNamespace =  new GlobalNamespace(compiler, rootNode);

    NodeUtil.visitPostOrder(rootNode, new NodeUtil.Visitor() {
      @Override
      public void visit(Node node) {
        if (PolymerPass.isPolymerCall(node)) {
          polymerCall = node;
        }
      }
    }, Predicates.<Node>alwaysTrue());

    assertNotNull(polymerCall);
    return PolymerClassDefinition.extractFromCallNode(polymerCall, compiler, globalNamespace);
  }
}
