/*
 * Copyright 2007 The Closure Compiler Authors.
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

import junit.framework.TestCase;

public final class SourceFileTest extends TestCase {

  private class ResetableSourceFile extends SourceFile.Preloaded {
    ResetableSourceFile(String fileName, String code) {
      super(fileName, null, code);
    }

    void updateCode(String code) {
      setCode(code);
    }
  }

  /**
   * Tests that keys are assigned sequentially.
   */
  public void testLineOffset() throws Exception {
    ResetableSourceFile sf = new ResetableSourceFile(
      "test.js", "'1';\n'2';\n'3'\n");
    assertThat(sf.getLineOffset(1)).isEqualTo(0);
    assertThat(sf.getLineOffset(2)).isEqualTo(5);
    assertThat(sf.getLineOffset(3)).isEqualTo(10);

    sf.updateCode("'100';\n'200;'\n'300'\n");
    assertThat(sf.getLineOffset(1)).isEqualTo(0);
    assertThat(sf.getLineOffset(2)).isEqualTo(7);
    assertThat(sf.getLineOffset(3)).isEqualTo(14);
  }
}
