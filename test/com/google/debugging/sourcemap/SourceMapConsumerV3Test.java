/*
 * Copyright 2009 The Closure Compiler Authors.
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

package com.google.debugging.sourcemap;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping.Precision;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link SourceMapConsumerV3}
 *
 */
@RunWith(JUnit4.class)
public final class SourceMapConsumerV3Test {

  private static final Gson GSON = new Gson();

  private final SourceMapConsumerV3 consumer = new SourceMapConsumerV3();

  @Test
  public void testSources() throws Exception {
    consumer.parse(
        GSON.toJson(
            TestJsonBuilder.create()
                .setVersion(3)
                .setFile("testcode")
                .setLineCount(1)
                .setMappings("AAAAA,QAASA,UAAS,EAAG;")
                .setSources("testcode")
                .setNames("__BASIC__")
                .build()));

    assertThat(consumer.getOriginalSources()).containsExactly("testcode");
    assertThat(consumer.getSourceRoot()).isNull();
  }

  @Test
  public void testSectionsFormat_parsesSuccessfully() throws Exception {
    consumer.parse(
        GSON.toJson(
            TestJsonBuilder.create()
                .setVersion(3)
                .setFile("testcode")
                .addSection(
                    1,
                    2,
                    TestJsonBuilder.create()
                        .setVersion(3)
                        .setFile("part_a")
                        .setMappings("AAAAA,QAASA,UAAS,EAAG;")
                        .setSources("testcode.js")
                        .setNames("foo"))
                .build()));
  }

  @Test
  public void testSectionsFormat_fileNameIsOptional_inSectionAndTopLevel() throws Exception {
    consumer.parse(
        GSON.toJson(
            TestJsonBuilder.create()
                .setVersion(3)
                // .setFile("testcode")
                .addSection(
                    1,
                    2,
                    TestJsonBuilder.create()
                        .setVersion(3)
                        // .setFile("part_a")
                        .setMappings("AAAAA,QAASA,UAAS,EAAG;")
                        .setSources("testcode.js")
                        .setNames("foo"))
                .build()));
  }

  @Test
  public void testSourcesWithRoot() throws Exception {
    consumer.parse(
        GSON.toJson(
            TestJsonBuilder.create()
                .setVersion(3)
                .setFile("testcode")
                .setLineCount(1)
                .setMappings("AAAAA,QAASA,UAAS,EAAG;")
                .setSourceRoot("http://server/path/")
                .setSources("testcode")
                .setNames("__BASIC__")
                .build()));

    //By default sourceRoot is not prepended
    assertThat(consumer.getOriginalSources()).containsExactly("testcode");
    assertThat(consumer.getSourceRoot()).isEqualTo("http://server/path/");
  }

  @Test
  public void testExtensions() throws Exception {
    consumer.parse(
        GSON.toJson(
            TestJsonBuilder.create()
                .setVersion(3)
                .setFile("testcode")
                .setLineCount(1)
                .setMappings("AAAAA,QAASA,UAAS,EAAG;")
                .setSources("testcode")
                .setNames("__BASIC__")
                .setCustomProperty("x_org_int", 2)
                .setCustomProperty("x_org_array", ImmutableList.of())
                .build()));

    Map<String, Object> exts = consumer.getExtensions();

    assertThat(exts).hasSize(2);
    assertThat(exts).doesNotContainKey("org_int");
    assertThat(((JsonElement) exts.get("x_org_int")).getAsInt()).isEqualTo(2);
    assertThat((JsonArray) exts.get("x_org_array")).isEmpty();
  }

  @Test
  public void testSourceMappingExactMatch() throws Exception {
    consumer.parse(
        GSON.toJson(
            TestJsonBuilder.create()
                .setVersion(3)
                .setFile("testcode")
                .setLineCount(1)
                .setMappings("AAAAA,QAASA,UAAS,EAAG;")
                .setSources("testcode")
                .setNames("__BASIC__")
                .build()));

    OriginalMapping mapping = consumer.getMappingForLine(1, 1);

    assertThat(mapping).isNotNull();
    assertThat(mapping.getLineNumber()).isEqualTo(1);
    assertThat(mapping.getPrecision()).isEqualTo(Precision.EXACT);
  }

  @Test
  public void testSourceMappingApproximatedLine() throws Exception {
    consumer.parse(
        GSON.toJson(
            TestJsonBuilder.create()
                .setVersion(3)
                .setMappings(
                    ";;;;;;;;;;;;;;;;;;IAAMA,K,GACL,eAAaC,EAAb,EAAiB;AAAA;;AAAA;;AAChB,OAAKA,EAAL,GAAUA,EAAV;AACA,C;;IAEIC,S;;;;;;;AACL,qBAAYD,EAAZ,EAAgB;AAAA;;AAAA,6BACTA,EADS;AAEf;;;EAHsBD,K;;AAKxB,IAAIG,CAAC,GAAG,IAAID,SAAJ,CAAc,UAAd,CAAR")
                .setSourcesContent(
                    """
                    class Shape {
                    \tconstructor (id) {
                    \t\tthis.id = id;
                    \t}
                    }
                    class Rectangle extends Shape {
                    \tconstructor(id) {
                    \t\tsuper(id);
                    \t}
                    }
                    var s = new Rectangle("Shape ID");
                    """)
                .setSources("testcode")
                .setNames("Shape", "id", "Rectangle", "s")
                .build()));

    OriginalMapping mapping = consumer.getMappingForLine(40, 10);

    assertThat(mapping).isNotNull();
    // The Previous line mapping was retrieved, and thus it is "approximated"
    assertThat(mapping.getLineNumber()).isEqualTo(9);
    assertThat(mapping.getPrecision()).isEqualTo(Precision.APPROXIMATE_LINE);
  }

  @Test
  public void testLargeMappings() throws Exception {
    // Generate a mapping with many entries to trigger array resizing.
    StringBuilder mappings = new StringBuilder();
    for (int i = 0; i < 2000; i++) {
      // AAAA = [0, 0, 0, 0]
      mappings.append("AAAA");
      if (i % 2 == 0) {
        mappings.append("A"); // 5th value
      }
      mappings.append(",");
    }
    mappings.append(";");

    consumer.parse(
        GSON.toJson(
            TestJsonBuilder.create()
                .setVersion(3)
                .setFile("testcode")
                .setLineCount(1)
                .setMappings(mappings.toString())
                .setSources("testcode")
                .setNames("foo")
                .build()));

    assertThat(consumer.getLineCount()).isEqualTo(1);
    OriginalMapping mapping = consumer.getMappingForLine(1, 1);
    assertThat(mapping).isNotNull();
  }

  @Test
  public void testMixedEntryTypes() throws Exception {
    // Test a mix of 1, 4, and 5 value entries.
    consumer.parse(
        GSON.toJson(
            TestJsonBuilder.create()
                .setVersion(3)
                .setFile("testcode")
                .setLineCount(1)
                // col 0: A (unmapped)
                // col 1: CAAA (mapped to src index 0, line 0, col 0 - 4 values)
                // col 3: EAAAA (mapped to src index 0, line 0, col 0, name index 0 - 5 values)
                .setMappings("A,CAAA,EAAAA;")
                .setSources("testcode")
                .setNames("name1")
                .build()));

    OriginalMapping m1 = consumer.getMappingForLine(1, 1); // col 0
    OriginalMapping m2 = consumer.getMappingForLine(1, 2); // col 1
    OriginalMapping m4 = consumer.getMappingForLine(1, 4); // col 3

    assertThat(m1).isNull();
    assertThat(m2).isNotNull();
    assertThat(m4).isNotNull();

    assertThat(m2.getOriginalFile()).isEqualTo("testcode");
    assertThat(m2.getIdentifier()).isEmpty();

    assertThat(m4.getOriginalFile()).isEqualTo("testcode");
    assertThat(m4.getIdentifier()).isEqualTo("name1");
  }

  @Test
  public void testReverseMapping() throws Exception {
    consumer.parse(
        GSON.toJson(
            TestJsonBuilder.create()
                .setVersion(3)
                .setFile("testcode")
                .setLineCount(1)
                .setMappings("AAAAA,QAASA,UAAS,EAAG;")
                .setSources("testcode")
                .setNames("__BASIC__")
                .build()));

    Collection<OriginalMapping> reverse = consumer.getReverseMapping("testcode", 0, 0);
    assertThat(reverse).isNotEmpty();
  }

  @Test
  public void testBinarySearchEdgeCases() throws Exception {
    // Test with different number of entries to verify binary search 'mid' calculation.
    for (int numEntries = 1; numEntries < 20; numEntries++) {
      StringBuilder mappings = new StringBuilder();
      for (int i = 0; i < numEntries; i++) {
        // VLQ for delta 1 is 'C'
        // AAAA = [0,0,0,0]
        // CAAA = [1,0,0,0]
        // Previous col starts at 0.
        // First entry: AAAA -> col 0
        // Subsequent entries: CAAA -> col 1, 2, 3...
        if (i == 0) {
          mappings.append("AAAA");
        } else {
          mappings.append("CAAA");
        }
        mappings.append(",");
      }
      mappings.append(";");

      consumer.parse(
          GSON.toJson(
              TestJsonBuilder.create()
                  .setVersion(3)
                  .setFile("testcode")
                  .setLineCount(1)
                  .setMappings(mappings.toString())
                  .setSources("testcode")
                  .setNames("foo")
                  .build()));

      for (int col = 0; col < numEntries; col++) {
        OriginalMapping mapping = consumer.getMappingForLine(1, col + 1);
        assertThat(mapping).isNotNull();
        // All map to source line 0, col 0 (OriginalMapping uses 1-indexed)
        assertThat(mapping.getLineNumber()).isEqualTo(1);
        assertThat(mapping.getColumnPosition()).isEqualTo(1);
      }
    }
  }

  @Test
  public void testVisitMappings() throws Exception {
    consumer.parse(
        GSON.toJson(
            TestJsonBuilder.create()
                .setVersion(3)
                .setFile("testcode")
                .setLineCount(1)
                .setMappings("AAAAA,QAASA,UAAS,EAAG;")
                .setSources("testcode")
                .setNames("__BASIC__")
                .build()));

    List<String> symbols = new ArrayList<>();
    consumer.visitMappings(
        (sourceName, symbolName, sourcePos, startPos, endPos) -> {
          if (symbolName != null) {
            symbols.add(symbolName);
          }
        });

    assertThat(symbols).containsExactly("__BASIC__", "__BASIC__");
  }

  @Test
  public void testInvalidEntryValuesThrows() {
    // A VLQ of length 2, e.g. "AA" -> [0, 0]
    String invalidMappings = "AA;";

    SourceMapParseException expected =
        assertThrows(
            SourceMapParseException.class,
            () ->
                consumer.parse(
                    GSON.toJson(
                        TestJsonBuilder.create()
                            .setVersion(3)
                            .setFile("testcode")
                            .setLineCount(1)
                            .setMappings(invalidMappings)
                            .setSources("testcode")
                            .setNames("foo")
                            .build())));

    assertThat(expected).hasMessageThat().contains("Unexpected number of values for entry:2");
  }
}
