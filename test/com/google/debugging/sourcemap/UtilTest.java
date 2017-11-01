/*
 * Copyright 2017 The Closure Compiler Authors.
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

public final class UtilTest extends TestCase {
  public void testAppendHexJavaScriptRepresentation(char ch, String expectedOut) {
    StringBuilder sb = new StringBuilder();
    Util.appendHexJavaScriptRepresentation(sb, ch);
    assertThat(sb.toString()).isEqualTo(expectedOut);
  }

  public void testAppendHexJavaScriptRepresentation() {
    testAppendHexJavaScriptRepresentation('a', "\\u0061");
    testAppendHexJavaScriptRepresentation('z', "\\u007a");
    testAppendHexJavaScriptRepresentation('\0', "\\u0000");
    testAppendHexJavaScriptRepresentation('¡', "\\u00a1");
  }
}
