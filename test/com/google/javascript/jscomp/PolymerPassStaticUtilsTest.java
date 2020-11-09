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

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PolymerPassStaticUtilsTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return null; // unused
  }

  @Test
  public void testGetPolymerElementType_noNativeElement() {
    PolymerClassDefinition def =
        new PolymerClassDefinition(
            PolymerClassDefinition.DefinitionType.ObjectLiteral,
            null,
            null,
            false,
            IR.objectlit(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    assertThat(PolymerPassStaticUtils.getPolymerElementType(def)).isEqualTo("PolymerElement");
  }

  @Test
  public void testGetPolymerElementType_inputBaseElement() {
    PolymerClassDefinition def =
        new PolymerClassDefinition(
            PolymerClassDefinition.DefinitionType.ES6Class,
            null,
            null,
            false,
            IR.objectlit(),
            null,
            null,
            "input",
            null,
            null,
            null,
            null,
            null);
    assertThat(PolymerPassStaticUtils.getPolymerElementType(def)).isEqualTo("PolymerInputElement");
  }

  @Test
  public void testIsPolymerCall_basic() {
    Node script = parseExpectedJs("Polymer({});");
    // Unwrap ROOT -> SCRIPT -> STATMENT -> EXPRESSION to get to the call expression.
    Node n = script.getFirstChild().getFirstChild().getFirstChild();

    assertThat(PolymerPassStaticUtils.isPolymerCall(n)).isTrue();
  }

  @Test
  public void testIsPolymerCall_props() {
    // This can happen during TS transpilation.
    Node script = parseExpectedJs("module$polymer$polymer_legacy.Polymer({});");
    // Unwrap ROOT -> SCRIPT -> STATMENT -> EXPRESSION to get to the call expression.
    Node n = script.getFirstChild().getFirstChild().getFirstChild();

    assertThat(PolymerPassStaticUtils.isPolymerCall(n)).isTrue();
  }

  @Test
  public void testIsPolymerCall_anycast() {
    // During TS migration it can happen that one needs to suppress a type error using (Polymer as
    // any). This translates to the following in JS.
    Node script = parseExpectedJs("((/** @type {?} */ (Polymer)))({})");
    // Unwrap ROOT -> SCRIPT -> STATMENT -> EXPRESSION to get to the call expression.
    Node n = script.getFirstChild().getFirstChild().getFirstChild();

    assertThat(PolymerPassStaticUtils.isPolymerCall(n)).isTrue();
  }

  // TODO(jlklein): Add unit tests for remaining util functions.
}
