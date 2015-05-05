/*
 * Copyright 2015 The Closure Compiler Authors.
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
import static com.google.javascript.jscomp.IdMappingUtil.NEW_LINE;

import com.google.common.collect.BiMap;

import junit.framework.TestCase;

import java.util.Map;

/**
 * Tests for {@link IdMappingUtil}.
 */
public final class IdMappingUtilTest extends TestCase {

  public void testParseIdMapping() {
    StringBuilder mapping = new StringBuilder();
    mapping.append("[gen1]")
        .append(NEW_LINE)
        .append("id1:data1")
        .append(NEW_LINE)
        .append("id2:data2:data22")
        .append(NEW_LINE)
        .append(NEW_LINE)
        .append("[gen2]");

    Map<String, BiMap<String, String>> result =
        IdMappingUtil.parseSerializedIdMappings(mapping.toString());

    assertThat(result).hasSize(2);

    assertThat(result).containsKey("gen1");
    assertThat(result.get("gen1")).hasSize(2);
    assertThat(result.get("gen1")).containsEntry("id1", "data1");
    assertThat(result.get("gen1")).containsEntry("id2", "data2:data22");

    assertThat(result).containsKey("gen2");
    assertThat(result.get("gen2")).hasSize(0);
  }
}
