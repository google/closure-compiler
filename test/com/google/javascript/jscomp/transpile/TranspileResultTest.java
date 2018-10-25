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
import java.net.URI;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link TranspileResult}. */

@RunWith(JUnit4.class)
public final class TranspileResultTest {

  @Test
  public void testEquals() throws Exception {
    new EqualsTester()
        .addEqualityGroup(
            new TranspileResult(new URI("a"), "b", "c", "d"),
            new TranspileResult(new URI("a"), "b", "c", "d"))
        .addEqualityGroup(new TranspileResult(new URI("A"), "b", "c", "d"))
        .addEqualityGroup(new TranspileResult(new URI("a"), "B", "c", "d"))
        .addEqualityGroup(new TranspileResult(new URI("a"), "b", "C", "d"))
        .addEqualityGroup(new TranspileResult(new URI("a"), "b", "c", "D"))
        .testEquals();
  }

  @Test
  public void testEmbedSourceMap_noSourceMap() throws Exception {
    TranspileResult result = new TranspileResult(new URI("a"), "b", "c", "");
    assertThat(result.embedSourcemap()).isSameAs(result);
    assertThat(result.embedSourcemapUrl("foo")).isSameAs(result);
  }

  @Test
  public void testEmbedSourceMap() throws Exception {
    TranspileResult result = new TranspileResult(new URI("a"), "b", "c", "{\"version\": 3}");
    assertThat(result.embedSourcemap())
        .isEqualTo(
            new TranspileResult(
                new URI("a"),
                "b",
                "c\n//# sourceMappingURL=data:,%7B%22version%22%3A%203%7D\n",
                ""));
    assertThat(result.embedSourcemapUrl("foo.js.map"))
        .isEqualTo(
            new TranspileResult(
                new URI("a"), "b", "c\n//# sourceMappingURL=foo.js.map\n", "{\"version\": 3}"));
  }
}
