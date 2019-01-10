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
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_INVALID_BEHAVIOR;
import static com.google.javascript.jscomp.testing.JSErrorSubject.assertError;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.PolymerBehaviorExtractor.BehaviorDefinition;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link PolymerBehaviorExtractor}. */
@RunWith(JUnit4.class)
public class PolymerBehaviorExtractorTest extends CompilerTypeTestCase {

  private PolymerBehaviorExtractor extractor;
  private Node behaviorArray;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    behaviorArray = null;
  }

  @Test
  public void testArrayBehavior() {
    parseAndInitializeExtractor(
        lines(
            "/** @polymerBehavior */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "  /** @param {string} funAmount */",
            "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "  /** @override */",
            "  created: function() {}",
            "};",
            "/** @polymerBehavior */",
            "var RadBehavior = {",
            "  properties: {",
            "    howRad: Number",
            "  },",
            "  /** @param {number} radAmount */",
            "  doSomethingRad: function(radAmount) { alert('Something ' + radAmount + ' rad!'); },",
            "  /** @override */",
            "  ready: function() {}",
            "};",
            "/** @polymerBehavior */",
            "var SuperCoolBehaviors = [FunBehavior, RadBehavior];",
            "/** @polymerBehavior */",
            "var BoringBehavior = {",
            "  properties: {",
            "    boringString: String",
            "  },",
            "  /** @param {boolean} boredYet */",
            "  doSomething: function(boredYet) { alert(boredYet + ' ' + this.boringString); },",
            "};",
            "var A = Polymer({",
            "  is: 'x-element',",
            "  behaviors: [ SuperCoolBehaviors, BoringBehavior ],",
            "});"));

    ImmutableList<BehaviorDefinition> defs = extractor.extractBehaviors(behaviorArray);
    assertThat(defs).hasSize(3);

    // TODO(jlklein): Actually verify the properties of the BehaviorDefinitions.
  }

  @Test
  public void testInlineLiteralBehavior() {
    parseAndInitializeExtractor(
        lines(
            "/** @polymerBehavior */",
            "var FunBehavior = {",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "  /** @param {string} funAmount */",
            "  doSomethingFun: function(funAmount) { alert('Something ' + funAmount + ' fun!'); },",
            "  /** @override */",
            "  created: function() {}",
            "};",
            "/** @polymerBehavior */",
            "var SuperCoolBehaviors = [FunBehavior, {",
            "  properties: {",
            "    howRad: Number",
            "  },",
            "  /** @param {number} radAmount */",
            "  doSomethingRad: function(radAmount) { alert('Something ' + radAmount + ' rad!'); },",
            "  /** @override */",
            "  ready: function() {}",
            "}];",
            "var A = Polymer({",
            "  is: 'x-element',",
            "  behaviors: [ SuperCoolBehaviors ],",
            "});"));

    ImmutableList<BehaviorDefinition> defs = extractor.extractBehaviors(behaviorArray);
    assertThat(defs).hasSize(2);

    // TODO(jlklein): Actually verify the properties of the BehaviorDefinitions.
  }

  @Test
  public void testIsPropInBehavior() {
    parseAndInitializeExtractor(
        lines(
            "/** @polymerBehavior */",
            "var FunBehavior = {",
            "  is: 'fun-behavior',",
            "",
            "  properties: {",
            "    isFun: Boolean",
            "  },",
            "};",
            "var A = Polymer({",
            "  is: 'x-element',",
            "  behaviors: [ FunBehavior ],",
            "});"));
    extractor.extractBehaviors(behaviorArray);

    assertThat(compiler.getErrors()).hasLength(1);
    assertError(compiler.getErrors()[0]).hasType(POLYMER_INVALID_BEHAVIOR);
  }

  // TODO(jlklein): Test more use cases: names to avoid copying, global vs. non-global, etc.

  private void parseAndInitializeExtractor(String code) {
    Node root = compiler.parseTestCode(code);
    GlobalNamespace globalNamespace = new GlobalNamespace(compiler, root);
    extractor = new PolymerBehaviorExtractor(compiler, globalNamespace);

    NodeUtil.visitPostOrder(
        root,
        node -> {
          if (isBehaviorArrayDeclaration(node)) {
            behaviorArray = node;
          }
        });

    assertThat(behaviorArray).isNotNull();
  }

  private boolean isBehaviorArrayDeclaration(Node node) {
    return node.isArrayLit()
        && node.getParent().isStringKey() && node.getParent().getString().equals("behaviors");
  }
}
