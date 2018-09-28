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
import junit.framework.TestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit test for {@link Xid}. */
@RunWith(JUnit4.class)
public class XidTest extends TestCase {

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
      assertWithMessage("Both " + dup + " and " + i + " map to: " + key).that(dup).isNull();
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
    assertEquals("z6ArXc", Xid.toString(1));
    assertEquals("A6ArXc", Xid.toString(2));
    assertEquals("x6ArXc", Xid.toString(-1));
    assertEquals("OcErXc", Xid.toString(10000));
    assertEquals("tTaYp", Xid.toString(-1951591049));
  }

  @Test
  public void testGet() {
    Xid dummyMap = new Xid();
    assertEquals("nZzm6c", dummyMap.get("today"));
    assertEquals("fkPKBb", dummyMap.get("tomorrow"));
    assertEquals("b6Lt6c", dummyMap.get("value"));

    assertEquals("QB6rXc", dummyMap.get("foo"));
    assertEquals("RW4o4b", dummyMap.get("foo.Bar"));

    assertEquals("MiB45c", dummyMap.get("prop1"));
    assertEquals("NiB45c", dummyMap.get("prop2"));
  }
}
