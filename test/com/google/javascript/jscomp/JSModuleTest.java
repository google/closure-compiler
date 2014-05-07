/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import junit.framework.TestCase;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tests for {@link JSModule}
 *
 */
public class JSModuleTest extends TestCase {
  private JSModule mod1;
  private JSModule mod2;  // depends on mod1
  private JSModule mod3;  // depends on mod1
  private JSModule mod4;  // depends on mod2, mod3
  private JSModule mod5;  // depends on mod1

  @Override
  protected void setUp() {
    List<JSModule> modulesInDepOrder = new ArrayList<>();

    mod1 = new JSModule("mod1");
    modulesInDepOrder.add(mod1);

    mod2 = new JSModule("mod2");
    mod2.addDependency(mod1);
    modulesInDepOrder.add(mod2);

    mod3 = new JSModule("mod3");
    mod3.addDependency(mod1);
    modulesInDepOrder.add(mod3);

    mod4 = new JSModule("mod4");
    mod4.addDependency(mod2);
    mod4.addDependency(mod3);
    modulesInDepOrder.add(mod4);

    mod5 = new JSModule("mod5");
    mod5.addDependency(mod1);
    modulesInDepOrder.add(mod5);
  }

  public void testDependencies() {
    assertTrue(mod1.getAllDependencies().isEmpty());
    assertEquals(ImmutableSet.of(mod1), mod2.getAllDependencies());
    assertEquals(ImmutableSet.of(mod1), mod3.getAllDependencies());
    assertEquals(ImmutableSet.of(mod1, mod2, mod3), mod4.getAllDependencies());

    assertEquals(ImmutableSet.of(mod1), mod1.getThisAndAllDependencies());
    assertEquals(ImmutableSet.of(mod1, mod2), mod2.getThisAndAllDependencies());
    assertEquals(ImmutableSet.of(mod1, mod3), mod3.getThisAndAllDependencies());
    assertEquals(ImmutableSet.of(mod1, mod2, mod3, mod4),
                 mod4.getThisAndAllDependencies());
  }

  public void testSortInputs() throws Exception {
    CompilerInput a = new CompilerInput(
        SourceFile.fromCode("a.js",
            "goog.require('b');goog.require('c')"));
    CompilerInput b = new CompilerInput(
        SourceFile.fromCode("b.js",
            "goog.provide('b');goog.require('d')"));
    CompilerInput c = new CompilerInput(
        SourceFile.fromCode("c.js",
            "goog.provide('c');goog.require('d')"));
    CompilerInput d = new CompilerInput(
        SourceFile.fromCode("d.js",
            "goog.provide('d')"));

    // Independent modules.
    CompilerInput e = new CompilerInput(
        SourceFile.fromCode("e.js",
            "goog.provide('e')"));
    CompilerInput f = new CompilerInput(
        SourceFile.fromCode("f.js",
            "goog.provide('f')"));

    assertSortedInputs(
        ImmutableList.of(d, b, c, a),
        ImmutableList.of(a, b, c, d));
    assertSortedInputs(
        ImmutableList.of(d, b, c, a),
        ImmutableList.of(d, b, c, a));
    assertSortedInputs(
        ImmutableList.of(d, c, b, a),
        ImmutableList.of(d, c, b, a));
    assertSortedInputs(
        ImmutableList.of(d, b, c, a),
        ImmutableList.of(d, a, b, c));

    assertSortedInputs(
        ImmutableList.of(d, b, c, a, e, f),
        ImmutableList.of(a, b, c, d, e, f));
    assertSortedInputs(
        ImmutableList.of(e, f, d, b, c, a),
        ImmutableList.of(e, f, a, b, c, d));
    assertSortedInputs(
        ImmutableList.of(e, d, b, c, a, f),
        ImmutableList.of(a, b, c, e, d, f));
    assertSortedInputs(
        ImmutableList.of(e, f, d, b, c, a),
        ImmutableList.of(e, a, f, b, c, d));
  }

  private void assertSortedInputs(
      List<CompilerInput> expected, List<CompilerInput> shuffled)
      throws Exception {
    JSModule mod = new JSModule("mod");
    for (CompilerInput input : shuffled) {
      input.setModule(null);
      mod.add(input);
    }
    Compiler compiler = new Compiler(System.err);
    compiler.initCompilerOptionsIfTesting();
    mod.sortInputsByDeps(compiler);

    assertEquals(expected, mod.getInputs());
  }

  public void testSortJsModules() throws Exception {
    // already in order:
    assertEquals(ImmutableList.of(mod1, mod2, mod3, mod4),
        Arrays.asList(JSModule.sortJsModules(
            ImmutableList.of(mod1, mod2, mod3, mod4))));
    assertEquals(ImmutableList.of(mod1, mod3, mod2, mod4),
        Arrays.asList(JSModule.sortJsModules(
            ImmutableList.of(mod1, mod3, mod2, mod4))));

    // one out of order:
    assertEquals(ImmutableList.of(mod1, mod3, mod2, mod4),
        Arrays.asList(JSModule.sortJsModules(
            ImmutableList.of(mod4, mod3, mod2, mod1))));
    assertEquals(ImmutableList.of(mod1, mod3, mod2, mod4),
        Arrays.asList(JSModule.sortJsModules(
            ImmutableList.of(mod3, mod1, mod2, mod4))));

    // more out of order:
    assertEquals(ImmutableList.of(mod1, mod3, mod2, mod4),
        Arrays.asList(JSModule.sortJsModules(
            ImmutableList.of(mod4, mod3, mod1, mod2))));
  }
}
