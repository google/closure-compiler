/*
 * Copyright 2021 The Closure Compiler Authors.
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
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RecoverableJsAst}. */
@RunWith(JUnit4.class)
public class JsAstTest {

  @Test
  public void immutableAstSupplier_isUsedIfAvailable_ratherThanParsing() {
    // Given
    SourceFile file = SourceFile.fromCode("test.js", "var x = 'hello world'");
    Supplier<Node> supplier = () -> IR.script().setStaticSourceFile(file);
    JsAst ast = new JsAst(file, supplier);

    // When
    Node script = ast.getAstRoot(null);

    // Then
    assertThat(script.hasChildren()).isFalse();
    assertThat(script.getStaticSourceFile()).isSameInstanceAs(file);
  }
}
