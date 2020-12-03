/*
 * Copyright 2014 The Closure Compiler Authors.
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

package com.google.javascript.refactoring;

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.CompilerTestCase.lines;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ScriptMetadataTest extends CompilerTestCase {

  private ScriptMetadata lastMetadata;

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return (externs, root) ->
        this.lastMetadata = ScriptMetadata.create(root.getOnlyChild(), compiler);
  }

  @Test
  public void testParsesAliases() {
    testSame(
        lines(
            "const Foo = goog.require('a.b.Foo');",
            "const Bar = goog.requireType('a.b.Bar');",
            "const Qux = goog.forwardDeclare('a.b.Qux');"));

    assertThat(this.lastMetadata.getAlias("a.b.Foo")).isEqualTo("Foo");
    assertThat(this.lastMetadata.getAlias("a.b.Bar")).isEqualTo("Bar");
    assertThat(this.lastMetadata.getAlias("a.b.Qux")).isEqualTo("Qux");
  }
}
