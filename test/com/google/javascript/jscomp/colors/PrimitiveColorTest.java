/*
 * Copyright 2020 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.colors;

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.testing.ColorSubject.assertThat;
import static com.google.javascript.rhino.testing.Asserts.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PrimitiveColorTest {
  @Test
  public void isPrimitiveReturnsTrue() {
    assertThat((Color) PrimitiveColor.NUMBER).isPrimitive();
    assertThat((Color) PrimitiveColor.UNKNOWN).isPrimitive();
  }

  @Test
  public void isUnionReturnsFalse() {
    assertThat(PrimitiveColor.NUMBER.isUnion()).isFalse();
    assertThat(PrimitiveColor.NULL_OR_VOID.isUnion()).isFalse();
    assertThat(PrimitiveColor.UNKNOWN.isUnion()).isFalse();
  }

  @Test
  public void getAlternatesIsNotAllowed() {
    assertThrows(Exception.class, PrimitiveColor.NUMBER::getAlternates);
  }
}
