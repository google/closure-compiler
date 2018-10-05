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
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PolymerClassDefinitionTest extends CompilerTypeTestCase {

  private Node polymerCall;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    polymerCall = null;
  }

  // TODO(jlklein): Add more complex test cases and verify behaviors and descriptors.

  @Test
  public void testSimpleBehavior() {
    PolymerClassDefinition def =
        parseAndExtractClassDefFromCall(
            lines(
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
                "  doSomethingFun: function(funAmount) {",
                "    alert('Something ' + funAmount + ' fun!');",
                "  },",
                "",
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

    assertThat(def).isNotNull();
    assertThat(def.defType).isEqualTo(PolymerClassDefinition.DefinitionType.ObjectLiteral);
    assertNode(def.target).hasType(Token.NAME);
    assertThat(def.target.getString()).isEqualTo("A");
    assertThat(def.nativeBaseElement).isNull();
    assertThat(def.behaviors).hasSize(1);
    assertThat(def.props).hasSize(3);
  }

  @Test
  public void testBasicClass() {
    compiler.getOptions().setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_2015);
    PolymerClassDefinition def =
        parseAndExtractClassDefFromClass(
            lines(
                "class A extends Polymer.Element {",
                "  static get is() { return 'x-element'; }",
                "  static get properties() {",
                "    return {",
                "      pets: {",
                "        type: Array,",
                "        notify: true,",
                "      },",
                "      name: String",
                "    };",
                "  }",
                "}"));

    assertThat(def).isNotNull();
    assertThat(def.defType).isEqualTo(PolymerClassDefinition.DefinitionType.ES6Class);
    assertNode(def.target).hasType(Token.NAME);
    assertThat(def.target.getString()).isEqualTo("A");
    assertThat(def.descriptor).isNotNull();
    assertNode(def.descriptor).hasType(Token.OBJECTLIT);
    assertThat(def.nativeBaseElement).isNull();
    assertThat(def.behaviors).isNull();
    assertThat(def.props).hasSize(2);
  }

  @Test
  public void testDynamicDescriptor() {
    PolymerClassDefinition def = parseAndExtractClassDefFromCall(
        lines(
            "var A = Polymer({",
            "  is: x,",
            "});"));

    assertThat(def.target.getString()).isEqualTo("A");
  }

  @Test
  public void testDynamicDescriptor1() {
    PolymerClassDefinition def = parseAndExtractClassDefFromCall(
        lines(
            "Polymer({",
            "  is: x,",
            "});"));

    assertThat(def.target.getString()).isEqualTo("XElement");
  }

  @Test
  public void testDynamicDescriptor2() {
    PolymerClassDefinition def = parseAndExtractClassDefFromCall(
        lines(
            "Polymer({",
            "  is: foo.bar,",
            "});"));

    assertThat(def.target.getString()).isEqualTo("Foo$barElement");
  }

  @Test
  public void testDynamicDescriptor3() {
    PolymerClassDefinition def = parseAndExtractClassDefFromCall(
        lines(
            "Polymer({",
            "  is: this.bar,",
            "});"));

    assertThat(def.target.getString()).isEqualTo("This$barElement");
  }

  private PolymerClassDefinition parseAndExtractClassDefFromCall(String code) {
    Node rootNode = compiler.parseTestCode(code);
    GlobalNamespace globalNamespace =  new GlobalNamespace(compiler, rootNode);

    NodeUtil.visitPostOrder(
        rootNode,
        new NodeUtil.Visitor() {
          @Override
          public void visit(Node node) {
            if (PolymerPassStaticUtils.isPolymerCall(node)) {
              polymerCall = node;
            }
          }
        });

    assertThat(polymerCall).isNotNull();
    return PolymerClassDefinition.extractFromCallNode(polymerCall, compiler, globalNamespace);
  }

  private PolymerClassDefinition parseAndExtractClassDefFromClass(String code) {
    Node rootNode = compiler.parseTestCode(code);
    GlobalNamespace globalNamespace = new GlobalNamespace(compiler, rootNode);

    NodeUtil.visitPostOrder(
        rootNode,
        new NodeUtil.Visitor() {
          @Override
          public void visit(Node node) {
            if (PolymerPassStaticUtils.isPolymerClass(node)) {
              polymerCall = node;
            }
          }
        });

    assertThat(polymerCall).isNotNull();
    return PolymerClassDefinition.extractFromClassNode(polymerCall, compiler, globalNamespace);
  }
}
