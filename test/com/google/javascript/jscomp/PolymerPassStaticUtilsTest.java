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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PolymerPassStaticUtilsTest {

  @Test
  public void testGetPolymerElementType_noNativeElement() {
    PolymerClassDefinition def =
        new PolymerClassDefinition(
            PolymerClassDefinition.DefinitionType.ObjectLiteral,
            null,
            null,
            IR.objectlit(),
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
            IR.objectlit(),
            null,
            null,
            "input",
            null,
            null,
            null,
            null);
    assertThat(PolymerPassStaticUtils.getPolymerElementType(def)).isEqualTo("PolymerInputElement");
  }

  // TODO(jlklein): Add unit tests for remaining util functions.
}
