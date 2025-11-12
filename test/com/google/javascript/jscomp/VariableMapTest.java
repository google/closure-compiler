/*
 * Copyright 2008 The Closure Compiler Authors.
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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link VariableMap}. */
@RunWith(JUnit4.class)
public final class VariableMapTest {

  @Test
  public void testCycle1() throws ParseException {
    cycleTest(ImmutableMap.of("AAA", "a", "BBB", "b"));
    cycleTest(ImmutableMap.of("AA:AA", "a", "BB:BB", "b"));
    cycleTest(ImmutableMap.of("AAA", "a:a", "BBB", "b:b"));
  }

  public void cycleTest(ImmutableMap<String, String> map) throws ParseException {
    VariableMap in = new VariableMap(map);
    String serialized = new String(in.toBytes(), UTF_8);
    VariableMap out = VariableMap.fromBytes(serialized.getBytes(UTF_8));
    assertMapsEquals(in.toMap(), out.toMap());
  }

  public void assertMapsEquals(Map<String, String> expected, Map<String, String> result) {
    assertThat(result).hasSize(expected.size());
    for (String key : expected.keySet()) {
      assertThat(result).containsEntry(key, expected.get(key));
    }
  }

  @Test
  public void testToBytes() {
    VariableMap vm = new VariableMap(ImmutableMap.of("AAA", "a", "BBB", "b"));
    String serialized = new String(vm.toBytes(), UTF_8);
    assertThat(serialized).endsWith("\n");

    List<String> lines = Arrays.asList(serialized.split("\n"));
    assertThat(lines).hasSize(2);
    assertThat(lines).contains("AAA:a");
    assertThat(lines).contains("BBB:b");
  }

  @Test
  public void testFromBytes() throws ParseException {
    VariableMap vm = VariableMap.fromBytes("AAA:a\nBBB:b\n".getBytes(UTF_8));
    assertThat(vm.getOriginalNameToNewNameMap()).hasSize(2);
    assertThat(vm.lookupNewName("AAA")).isEqualTo("a");
    assertThat(vm.lookupNewName("BBB")).isEqualTo("b");
    assertThat(vm.lookupSourceName("a")).isEqualTo("AAA");
    assertThat(vm.lookupSourceName("b")).isEqualTo("BBB");
  }

  @Test
  public void testFromBytesWithEmptyValue() throws ParseException {
    VariableMap vm = VariableMap.fromBytes("AAA:".getBytes(UTF_8));
    assertThat(vm.lookupNewName("AAA")).isEmpty();
  }

  @Test
  public void testFileFormat1() {
    assertEqual(
        new VariableMap(ImmutableMap.of("x\ny", "a")).toBytes(), "x\\ny:a\n".getBytes(UTF_8));

    assertEqual(
        new VariableMap(ImmutableMap.of("x:y", "a")).toBytes(), "x\\:y:a\n".getBytes(UTF_8));

    assertEqual(
        new VariableMap(ImmutableMap.of("x\ny", "a")).toBytes(), "x\\ny:a\n".getBytes(UTF_8));

    assertEqual(
        new VariableMap(ImmutableMap.of("x\\y", "a")).toBytes(), "x\\\\y:a\n".getBytes(UTF_8));

    assertEqual(new VariableMap(ImmutableMap.of("\n", "a")).toBytes(), "\\n:a\n".getBytes(UTF_8));

    assertEqual(new VariableMap(ImmutableMap.of(":", "a")).toBytes(), "\\::a\n".getBytes(UTF_8));

    assertEqual(new VariableMap(ImmutableMap.of("\n", "a")).toBytes(), "\\n:a\n".getBytes(UTF_8));

    assertEqual(new VariableMap(ImmutableMap.of("\\", "a")).toBytes(), "\\\\:a\n".getBytes(UTF_8));
  }

  @Test
  public void testFromBytesComplex1() throws ParseException {
    // Verify we get out what we put in.
    cycleTest(ImmutableMap.of("AAA[':f']", "a"));

    // Verify the file format is as expected.
    VariableMap in = new VariableMap(ImmutableMap.of("AAA[':f']", "a"));
    assertEqual(in.toBytes(), "AAA['\\:f']:a\n".getBytes(UTF_8));
  }

  @Test
  public void testFromBytesComplex2() throws ParseException {
    VariableMap vm = VariableMap.fromBytes("AAA['\\:f']:a\n".getBytes(UTF_8));

    assertThat(vm.getOriginalNameToNewNameMap()).hasSize(1);
    assertThat(vm.lookupNewName("AAA[':f']")).isEqualTo("a");

    assertThat(vm.getNewNameToOriginalNameMap()).hasSize(1);
    assertThat(vm.lookupSourceName("a")).isEqualTo("AAA[':f']");
  }

  private void assertEqual(byte[] bytes1, byte[] bytes2) {
    if (bytes1 != bytes2) {
      assertWithMessage("length differs.").that(bytes2.length).isEqualTo(bytes1.length);
      for (int i = 0; i < bytes1.length; i++) {
        assertWithMessage("byte %sdiffers.", i).that(bytes2[i]).isEqualTo(bytes1[i]);
      }
    }
  }

  @Test
  public void testReverseThrowsErrorOnDuplicate() {
    try {
      new VariableMap(ImmutableMap.of("AA", "b", "BB", "b"));
      throw new AssertionError();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testReverseLookupOfNullFindsNoName() {
    VariableMap vm = new VariableMap(ImmutableMap.of("AA", "a", "BB", "b"));
    assertThat(vm.lookupSourceName(null)).isNull();
  }
}
