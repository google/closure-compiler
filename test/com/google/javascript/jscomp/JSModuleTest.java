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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link JSChunk} */
@RunWith(JUnit4.class)
public final class JSModuleTest {
  private JSChunk mod1;
  private JSChunk mod2; // depends on mod1
  private JSChunk mod3; // depends on mod1
  private JSChunk mod4; // depends on mod2, mod3
  private JSChunk mod5; // depends on mod1

  @Before
  public void setUp() throws Exception {
    mod1 = new JSChunk("mod1");

    mod2 = new JSChunk("mod2");
    mod2.addDependency(mod1);

    mod3 = new JSChunk("mod3");
    mod3.addDependency(mod1);

    mod4 = new JSChunk("mod4");
    mod4.addDependency(mod2);
    mod4.addDependency(mod3);

    mod5 = new JSChunk("mod5");
    mod5.addDependency(mod1);
  }

  @Test
  public void testDependencies() {
    assertThat(mod1.getAllDependencies()).isEmpty();
    assertThat(mod2.getAllDependencies()).isEqualTo(ImmutableSet.of(mod1));
    assertThat(mod3.getAllDependencies()).isEqualTo(ImmutableSet.of(mod1));
    assertThat(mod4.getAllDependencies()).isEqualTo(ImmutableSet.of(mod1, mod2, mod3));

    assertThat(mod1.getThisAndAllDependencies()).isEqualTo(ImmutableSet.of(mod1));
    assertThat(mod2.getThisAndAllDependencies()).isEqualTo(ImmutableSet.of(mod1, mod2));
    assertThat(mod3.getThisAndAllDependencies()).isEqualTo(ImmutableSet.of(mod1, mod3));
    assertThat(mod4.getThisAndAllDependencies()).isEqualTo(ImmutableSet.of(mod1, mod2, mod3, mod4));
  }

  @Test
  public void testSortInputs() throws Exception {
    CompilerInput a =
        new CompilerInput(SourceFile.fromCode("a.js", "goog.require('b');goog.require('c')"));
    CompilerInput b =
        new CompilerInput(SourceFile.fromCode("b.js", "goog.provide('b');goog.require('d')"));
    CompilerInput c =
        new CompilerInput(SourceFile.fromCode("c.js", "goog.provide('c');goog.require('d')"));
    CompilerInput d = new CompilerInput(SourceFile.fromCode("d.js", "goog.provide('d')"));

    // Independent modules.
    CompilerInput e = new CompilerInput(SourceFile.fromCode("e.js", "goog.provide('e')"));
    CompilerInput f = new CompilerInput(SourceFile.fromCode("f.js", "goog.provide('f')"));

    assertSortedInputs(ImmutableList.of(d, b, c, a), ImmutableList.of(a, b, c, d));
    assertSortedInputs(ImmutableList.of(d, b, c, a), ImmutableList.of(d, b, c, a));
    assertSortedInputs(ImmutableList.of(d, c, b, a), ImmutableList.of(d, c, b, a));
    assertSortedInputs(ImmutableList.of(d, b, c, a), ImmutableList.of(d, a, b, c));

    assertSortedInputs(ImmutableList.of(d, b, c, a, e, f), ImmutableList.of(a, b, c, d, e, f));
    assertSortedInputs(ImmutableList.of(e, f, d, b, c, a), ImmutableList.of(e, f, a, b, c, d));
    assertSortedInputs(ImmutableList.of(d, b, c, a, e, f), ImmutableList.of(a, b, c, e, d, f));
    assertSortedInputs(ImmutableList.of(e, d, b, c, a, f), ImmutableList.of(e, a, f, b, c, d));
  }

  private void assertSortedInputs(List<CompilerInput> expected, List<CompilerInput> shuffled)
      throws Exception {
    JSChunk mod = new JSChunk("mod");
    for (CompilerInput input : shuffled) {
      input.setModule(null);
      mod.add(input);
    }
    Compiler compiler = new Compiler(System.err);
    compiler.initCompilerOptionsIfTesting();
    mod.sortInputsByDeps(compiler);

    assertThat(mod.getInputs()).isEqualTo(expected);
  }
}
