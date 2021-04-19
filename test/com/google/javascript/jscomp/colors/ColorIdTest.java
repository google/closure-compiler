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
import static com.google.javascript.rhino.testing.Asserts.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ColorIdTest {

  @Test
  public void equals_sameInputTrue() {
    assertEqualsAndRelatedMethods(ColorId.fromAscii("a"), ColorId.fromAscii("a"));
  }

  @Test
  public void equals_differentInputFalse() {
    assertNotEqualsAndRelatedMethods(ColorId.fromAscii("a"), ColorId.fromAscii("b"));
  }

  @Test
  public void equals_leadingZerosIgnored() {
    assertEqualsAndRelatedMethods(ColorId.fromAscii("a"), ColorId.fromAscii("\0a"));
  }

  @Test
  public void equals_maxSize() {
    assertEqualsAndRelatedMethods(ColorId.fromAscii("12345678"), ColorId.fromAscii("12345678"));
  }

  @Test
  public void maxSize64Bits() {
    ColorId.fromAscii("12345678"); // Max size is OK.
    assertThrows(Exception.class, () -> ColorId.fromAscii("123456789"));
  }

  private void assertEqualsAndRelatedMethods(ColorId actual, ColorId expected) {
    assertThat(actual).isEqualTo(expected);
    assertThat(actual.hashCode()).isEqualTo(expected.hashCode());
    assertThat(actual.toString()).isEqualTo(expected.toString());
  }

  private void assertNotEqualsAndRelatedMethods(ColorId actual, ColorId expected) {
    assertThat(actual).isNotEqualTo(expected);
    assertThat(actual.toString()).isNotEqualTo(expected.toString());
  }
}
