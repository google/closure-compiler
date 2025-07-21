/*
 * Copyright 2025 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link TypeCheck#getTypedPercent} */
@RunWith(JUnit4.class)
public class TypeCheckTypedPercentTest {

  @Test
  public void testGetTypedPercent1() {
    String js =
        """
        var id = function(x) { return x; }
        var id2 = function(x) { return id(x); }
        """;
    assertThat(getTypedPercent(js)).isWithin(0.1).of(50.0);
  }

  @Test
  public void testGetTypedPercent2() {
    String js = "var x = {}; x.y = 1;";
    assertThat(getTypedPercent(js)).isWithin(0.1).of(100.0);
  }

  @Test
  public void testGetTypedPercent3() {
    String js = "var f = function(x) { x.a = x.b; }";
    assertThat(getTypedPercent(js)).isWithin(0.1).of(25.0);
  }

  @Test
  public void testGetTypedPercent4() {
    String js =
        """
        var n = {};
        /** @constructor */ n.T = function() {};
        /** @type {n.T} */ var x = new n.T();
        """;
    assertThat(getTypedPercent(js)).isWithin(0.1).of(100.0);
  }

  @Test
  public void testGetTypedPercent5() {
    String js = "/** @enum {number} */ keys = {A: 1,B: 2,C: 3};";
    assertThat(getTypedPercent(js)).isWithin(0.1).of(100.0);
  }

  @Test
  public void testGetTypedPercent6() {
    String js = "a = {TRUE: 1, FALSE: 0};";
    assertThat(getTypedPercent(js)).isWithin(0.1).of(100.0);
  }

  @Test
  public void testResolvingNamedTypes() {
    String externs = new TestExternsBuilder().addObject().build();
    String js =
        """
        /** @constructor */
        var Foo = function() {}
        /** @param {number} a */
        Foo.prototype.foo = function(a) {
          return this.baz().toString();
        };
        /** @return {Baz} */
        Foo.prototype.baz = function() { return new Baz(); };
        /** @constructor
          * @extends Foo */
        var Bar = function() {};
        /** @constructor */
        var Baz = function() {};
        """;
    assertThat(getTypedPercentWithExterns(externs, js)).isWithin(0.1).of(100.0);
  }

  @Test
  public void testGetTypedPercent_constAndLet() {
    // Make sure names declared with `const` and `let` are counted correctly for typed percentage.
    String js =
        """
        const id = function(x) { return x; }
        let id2 = function(x) { return id(x); }
        """;
    assertThat(getTypedPercent(js)).isWithin(0.1).of(50.0);
  }

  private double getTypedPercent(String js) {
    return getTypedPercentWithExterns("", js);
  }

  private double getTypedPercentWithExterns(String externs, String js) {
    Compiler compiler = new Compiler();
    JSTypeRegistry registry = compiler.getTypeRegistry();
    Node jsRoot = IR.root(compiler.parseTestCode(js));

    Node externsRoot = IR.root(compiler.parseTestCode(externs));
    IR.root(externsRoot, jsRoot);

    TypeCheck t =
        new TypeCheck(compiler, new SemanticReverseAbstractInterpreter(registry), registry);
    t.processForTesting(externsRoot, jsRoot);
    assertThat(compiler.getErrors()).isEmpty();
    return t.getTypedPercent();
  }
}
