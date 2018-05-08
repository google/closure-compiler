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
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link TranspileResult}. */
@RunWith(JUnit4.class)
public final class TranspileResultTest {

  /*
   * Java currently lacks a built in method for checking if a the file system is
   * case sensitive or not, so check if two different cased paths are equivalent
   * or not, to determine which of the two testEquals tests to run.
   */

  private static final boolean CASE_SENSITIVE_FILE_SYSTEM =
      !(Paths.get("a").equals(Paths.get("A")));

  @Test
  public void testEquals_CaseSensitiveFS() {
    Assume.assumeTrue(CASE_SENSITIVE_FILE_SYSTEM);
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

  @Test
  public void testEquals_CaseInsensitiveFS() {
    Assume.assumeFalse(CASE_SENSITIVE_FILE_SYSTEM);
    new EqualsTester()
        .addEqualityGroup(
            new TranspileResult(Paths.get("a"), "b", "c", "d"),
            new TranspileResult(Paths.get("a"), "b", "c", "d"),
            new TranspileResult(Paths.get("A"), "b", "c", "d"))
        .addEqualityGroup(new TranspileResult(Paths.get("a"), "B", "c", "d"))
        .addEqualityGroup(new TranspileResult(Paths.get("a"), "b", "C", "d"))
        .addEqualityGroup(new TranspileResult(Paths.get("a"), "b", "c", "D"))
        .testEquals();
  }

  @Test
  public void testEmbedSourceMap_noSourceMap() {
    TranspileResult result = new TranspileResult(Paths.get("a"), "b", "c", "");
    assertThat(result.embedSourcemap()).isSameAs(result);
    assertThat(result.embedSourcemapUrl("foo")).isSameAs(result);
  }

  @Test
  public void testEmbedSourceMap() {
    TranspileResult result = new TranspileResult(Paths.get("a"), "b", "c", "{\"version\": 3}");
    assertThat(result.embedSourcemap())
        .isEqualTo(new TranspileResult(
            Paths.get("a"), "b", "c\n//# sourceMappingURL=data:,%7B%22version%22%3A%203%7D\n", ""));
    assertThat(result.embedSourcemapUrl("foo.js.map"))
        .isEqualTo(new TranspileResult(
            Paths.get("a"), "b", "c\n//# sourceMappingURL=foo.js.map\n", "{\"version\": 3}"));
  }
}
