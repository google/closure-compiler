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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;

import junit.framework.TestCase;

import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link VariableMap}.
 *
 */
public class VariableMapTest extends TestCase {

  public void testCycle1() throws ParseException {
    cycleTest(ImmutableMap.of("AAA", "a", "BBB", "b"));
    cycleTest(ImmutableMap.of("AA:AA", "a", "BB:BB", "b"));
    cycleTest(ImmutableMap.of("AAA", "a:a", "BBB", "b:b"));
  }

  public void cycleTest(ImmutableMap<String, String> map)
      throws ParseException {
    VariableMap in = new VariableMap(map);
    String serialized = new String(in.toBytes(), UTF_8);
    VariableMap out = VariableMap.fromBytes(serialized.getBytes(UTF_8));
    assertMapsEquals(in.toMap(), out.toMap());
  }

  public void assertMapsEquals(
      Map<String, String> expected, Map<String, String> result) {
    assertEquals(expected.size(), result.size());
    for (String key : expected.keySet()) {
      assertEquals(expected.get(key), result.get(key));
    }
  }

  public void testToBytes() {
    VariableMap vm = new VariableMap(ImmutableMap.of("AAA", "a", "BBB", "b"));
    String serialized = new String(vm.toBytes(), UTF_8);
    assertTrue(serialized.endsWith("\n"));

    List<String> lines = Arrays.asList(serialized.split("\n"));
    assertEquals(2, lines.size());
    assertTrue(lines.contains("AAA:a"));
    assertTrue(lines.contains("BBB:b"));
  }

  public void testFromBytes() throws ParseException {
    VariableMap vm = VariableMap.fromBytes("AAA:a\nBBB:b\n".getBytes(UTF_8));
    assertEquals(2, vm.getOriginalNameToNewNameMap().size());
    assertEquals("a", vm.lookupNewName("AAA"));
    assertEquals("b", vm.lookupNewName("BBB"));
    assertEquals("AAA", vm.lookupSourceName("a"));
    assertEquals("BBB", vm.lookupSourceName("b"));
  }

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

  public void testFromBytesComplex1() throws ParseException {
    // Verify we get out what we put in.
    cycleTest(ImmutableMap.of("AAA[':f']", "a"));

    // Verify the file format is as expected.
    VariableMap in = new VariableMap(ImmutableMap.of("AAA[':f']", "a"));
    assertEqual(in.toBytes(), "AAA['\\:f']:a\n".getBytes(UTF_8));
  }

  public void testFromBytesComplex2() throws ParseException {
    VariableMap vm = VariableMap.fromBytes("AAA['\\:f']:a\n".getBytes(UTF_8));

    assertEquals(1, vm.getOriginalNameToNewNameMap().size());
    assertEquals("a", vm.lookupNewName("AAA[':f']"));

    assertEquals(1, vm.getNewNameToOriginalNameMap().size());
    assertEquals("AAA[':f']", vm.lookupSourceName("a"));

  }

  private void assertEqual(byte[] bytes1, byte[] bytes2) {
    if (bytes1 != bytes2) {
      assertEquals("length differs.", bytes1.length, bytes2.length);
      for (int i = 0; i < bytes1.length; i++) {
        assertEquals("byte " + i + "differs.", bytes1[i], bytes2[i]);
      }
    }
  }

  public void testReverseThrowsErrorOnDuplicate() {
    VariableMap vm = new VariableMap(ImmutableMap.of("AA", "b", "BB", "b"));
    try {
      vm.getNewNameToOriginalNameMap();
      fail();
    } catch (java.lang.IllegalArgumentException expected) {
    }
  }

  public void testReverseLookupOfNullFindsNoName() {
    VariableMap vm = new VariableMap(ImmutableMap.of("AA", "a", "BB", "b"));
    assertNull(vm.lookupSourceName(null));
  }
}
