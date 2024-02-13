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

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
    assertThat(mapping.getPrecision()).isEqualTo(OriginalMapping.Precision.EXACT);
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
                    "class Shape {\n"
                        + "\tconstructor (id) {\n"
                        + "\t\tthis.id = id;\n"
                        + "\t}\n"
                        + "}\n"
                        + "class Rectangle extends Shape {\n"
                        + "\tconstructor(id) {\n"
                        + "\t\tsuper(id);\n"
                        + "\t}\n"
                        + "}\n"
                        + "var s = new Rectangle(\"Shape ID\");")
                .setSources("testcode")
                .setNames("Shape", "id", "Rectangle", "s")
                .build()));

    OriginalMapping mapping = consumer.getMappingForLine(40, 10);

    assertThat(mapping).isNotNull();
    // The Previous line mapping was retrieved, and thus it is "approximated"
    assertThat(mapping.getLineNumber()).isEqualTo(9);
    assertThat(mapping.getPrecision()).isEqualTo(OriginalMapping.Precision.APPROXIMATE_LINE);
  }
}
