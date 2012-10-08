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

import com.google.common.base.Charsets;
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

  public void cycleTest(Map<String, String> map) throws ParseException {
    VariableMap in = new VariableMap(map);
    String serialized = new String(in.toBytes(), Charsets.UTF_8);
    VariableMap out = VariableMap.fromBytes(serialized.getBytes());
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
    String serialized = new String(vm.toBytes(), Charsets.UTF_8);
    assertTrue(serialized.endsWith("\n"));

    List<String> lines = Arrays.asList(serialized.split("\n"));
    assertEquals(2, lines.size());
    assertTrue(lines.contains("AAA:a"));
    assertTrue(lines.contains("BBB:b"));
  }

  public void testFromBytes() throws ParseException {
    VariableMap vm = VariableMap.fromBytes("AAA:a\nBBB:b\n".getBytes());
    assertEquals(2, vm.getOriginalNameToNewNameMap().size());
    assertEquals("a", vm.lookupNewName("AAA"));
    assertEquals("b", vm.lookupNewName("BBB"));
    assertEquals("AAA", vm.lookupSourceName("a"));
    assertEquals("BBB", vm.lookupSourceName("b"));
  }

  public void testFromBytesComplex() throws ParseException {
    VariableMap vm = VariableMap.fromBytes("AAA[':f']:a\n".getBytes());
    assertEquals(1, vm.getOriginalNameToNewNameMap().size());
    assertEquals("a", vm.lookupNewName("AAA[':f']"));
  }
}
