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
public final class JSChunkTest {
  private JSChunk chunk1;
  private JSChunk chunk2; // depends on chunk1
  private JSChunk chunk3; // depends on chunk1
  private JSChunk chunk4; // depends on chunk2, chunk3
  private JSChunk chunk5; // depends on chunk1

  @Before
  public void setUp() throws Exception {
    chunk1 = new JSChunk("chunk1");

    chunk2 = new JSChunk("chunk2");
    chunk2.addDependency(chunk1);

    chunk3 = new JSChunk("chunk3");
    chunk3.addDependency(chunk1);

    chunk4 = new JSChunk("chunk4");
    chunk4.addDependency(chunk2);
    chunk4.addDependency(chunk3);

    chunk5 = new JSChunk("chunk5");
    chunk5.addDependency(chunk1);
  }

  @Test
  public void testDependencies() {
    assertThat(chunk1.getAllDependencies()).isEmpty();
    assertThat(chunk2.getAllDependencies()).isEqualTo(ImmutableSet.of(chunk1));
    assertThat(chunk3.getAllDependencies()).isEqualTo(ImmutableSet.of(chunk1));
    assertThat(chunk4.getAllDependencies()).isEqualTo(ImmutableSet.of(chunk1, chunk2, chunk3));

    assertThat(chunk1.getThisAndAllDependencies()).isEqualTo(ImmutableSet.of(chunk1));
    assertThat(chunk2.getThisAndAllDependencies()).isEqualTo(ImmutableSet.of(chunk1, chunk2));
    assertThat(chunk3.getThisAndAllDependencies()).isEqualTo(ImmutableSet.of(chunk1, chunk3));
    assertThat(chunk4.getThisAndAllDependencies()).isEqualTo(ImmutableSet.of(chunk1, chunk2, chunk3, chunk4));
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

    // Independent chunks.
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
    JSChunk chunk = new JSChunk("chunk");
    for (CompilerInput input : shuffled) {
      input.setChunk(null);
      chunk.add(input);
    }
    Compiler compiler = new Compiler(System.err);
    compiler.initCompilerOptionsIfTesting();
    chunk.sortInputsByDeps(compiler);

    assertThat(chunk.getInputs()).isEqualTo(expected);
  }
}
