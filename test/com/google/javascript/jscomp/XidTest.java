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
import static com.google.common.truth.Truth.assertWithMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit test for {@link Xid}. */
@RunWith(JUnit4.class)
public class XidTest {

  /**
   * Verifies that {@link Xid#toString} generates unique strings for the integers close to zero,
   * {@code Integer.MIN_VALUE}, and {@code Integer.MAX_VALUE}.
   */
  @Test
  public void testUniqueness() {
    Map<String, Integer> map = new HashMap<>();
    helpTestUniqueness(map, -1000, 1000);
    helpTestUniqueness(map, Integer.MIN_VALUE, Integer.MIN_VALUE + 1000);
    helpTestUniqueness(map, Integer.MAX_VALUE, Integer.MAX_VALUE - 1000);
  }

  private void helpTestUniqueness(Map<String, Integer> map, int lo, int hi) {
    for (int i = lo; i <= hi; ++i) {
      String key = Xid.toString(i);
      Integer dup = map.get(key);
      assertWithMessage("Both %s and %s map to: %s", dup, i, key).that(dup).isNull();
      map.put(key, i);
    }
  }

  /** Verifies that {@link Xid#toString} generates strings with lengths between 1 and 6. */
  @Test
  public void testLength() {
    helpTestLength(0);
    helpTestLength(1);
    helpTestLength(-1);
    helpTestLength(Integer.MIN_VALUE);
    helpTestLength(Integer.MAX_VALUE);

    Random r = new Random();
    for (int i = 0; i <= 10000; ++i) {
      int n = r.nextInt();
      helpTestLength(n);
    }
  }

  private void helpTestLength(int i) {
    assertThat(Xid.toString(i)).matches(".{1,6}");
  }

  @Test
  public void testToString() {
    assertThat(Xid.toString(1)).isEqualTo("z6ArXc");
    assertThat(Xid.toString(2)).isEqualTo("A6ArXc");
    assertThat(Xid.toString(-1)).isEqualTo("x6ArXc");
    assertThat(Xid.toString(10000)).isEqualTo("OcErXc");
    assertThat(Xid.toString(-1951591049)).isEqualTo("tTaYp");
  }

  @Test
  public void testGet() {
    Xid dummyMap = new Xid();
    assertThat(dummyMap.get("today")).isEqualTo("nZzm6c");
    assertThat(dummyMap.get("tomorrow")).isEqualTo("fkPKBb");
    assertThat(dummyMap.get("value")).isEqualTo("b6Lt6c");

    assertThat(dummyMap.get("foo")).isEqualTo("QB6rXc");
    assertThat(dummyMap.get("foo.Bar")).isEqualTo("RW4o4b");

    assertThat(dummyMap.get("prop1")).isEqualTo("MiB45c");
    assertThat(dummyMap.get("prop2")).isEqualTo("NiB45c");
  }
}
