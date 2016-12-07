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

package com.google.javascript.jscomp.transpile;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.testing.EqualsTester;
import java.nio.file.Paths;
import junit.framework.TestCase;

/** Tests for {@link TranspileResult}. */

public final class TranspileResultTest extends TestCase {

  public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(
            new TranspileResult(Paths.get("a"), "b", "c", "d"),
            new TranspileResult(Paths.get("a"), "b", "c", "d"))
        .addEqualityGroup(new TranspileResult(Paths.get("A"), "b", "c", "d"))
        .addEqualityGroup(new TranspileResult(Paths.get("a"), "B", "c", "d"))
        .addEqualityGroup(new TranspileResult(Paths.get("a"), "b", "C", "d"))
        .addEqualityGroup(new TranspileResult(Paths.get("a"), "b", "c", "D"))
        .testEquals();
  }

  public void testEmbedSourceMap_noSourceMap() {
    TranspileResult result = new TranspileResult(Paths.get("a"), "b", "c", "");
    assertThat(result.embedSourcemap()).isSameAs(result);
    assertThat(result.embedSourcemapUrl("foo")).isSameAs(result);
  }

  public void testEmbedSourceMap() {
    TranspileResult result = new TranspileResult(Paths.get("a"), "b", "c", "{\"version\": 3}");
    assertThat(result.embedSourcemap())
        .isEqualTo(new TranspileResult(
            Paths.get("a"), "b", "c\n//# sourceMappingURL=data:,%7B%22version%22%3A+3%7D\n", ""));
    assertThat(result.embedSourcemapUrl("foo.js.map"))
        .isEqualTo(new TranspileResult(
            Paths.get("a"), "b", "c\n//# sourceMappingURL=foo.js.map\n", "{\"version\": 3}"));
  }
}
