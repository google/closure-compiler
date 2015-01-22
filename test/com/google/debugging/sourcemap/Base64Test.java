/*
 * Copyright 2011 The Closure Compiler Authors.
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

package com.google.debugging.sourcemap;

import static com.google.common.truth.Truth.assertThat;

import junit.framework.TestCase;

/**
 * @author johnlenz@google.com (John Lenz)
 */
public class Base64Test extends TestCase {
  public void testBase64() {
    for (int i = 0; i < 64; i++) {
      testValue(i);
    }
  }

  public void testBase64EncodeInt() {
    assertThat(Base64.base64EncodeInt(0)).isEqualTo("AAAAAA");
    assertThat(Base64.base64EncodeInt(1)).isEqualTo("AAAAAQ");
    assertThat(Base64.base64EncodeInt(42)).isEqualTo("AAAAKg");
    assertThat(Base64.base64EncodeInt(-100)).isEqualTo("////nA");
    assertThat(Base64.base64EncodeInt(0xffffffff)).isEqualTo("/////w");
  }

  private void testValue(int value) {
    assertThat(Base64.fromBase64(Base64.toBase64(value))).isEqualTo(value);
  }
}
