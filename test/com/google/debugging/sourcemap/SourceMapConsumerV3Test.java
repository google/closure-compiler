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

  @Test
  public void testSources() throws Exception {
    String sourceMap =  "{\n" +
                        "\"version\":3,\n" +
                        "\"file\":\"testcode\",\n" +
                        "\"lineCount\":1,\n" +
                        "\"mappings\":\"AAAAA,QAASA,UAAS,EAAG;\",\n" +
                        "\"sources\":[\"testcode\"],\n" +
                        "\"names\":[\"__BASIC__\"]\n" +
                        "}\n";

    SourceMapConsumerV3 consumer = new SourceMapConsumerV3();
    consumer.parse(sourceMap);

    assertThat(consumer.getOriginalSources()).containsExactly("testcode");
    assertThat(consumer.getSourceRoot()).isNull();
  }

  @Test
  public void testMap() throws Exception {
    String sourceMap = ""
        + "{"
        + "  \"version\": 3,"
        + "  \"file\": \"testcode.js\","
        + "  \"sections\": ["
        + "    {"
        + "      \"map\": {"
        + "         \"version\": 3,"
        + "         \"mappings\": \"AAAAA,QAASA,UAAS,EAAG;\","
        + "         \"sources\": [\"testcode.js\"],"
        + "         \"names\": [\"foo\"]"
        + "      },"
        + "      \"offset\": {"
        + "        \"line\": 1,"
        + "        \"column\": 1"
        + "      }"
        + "    }"
        + "  ]"
        + "}";

    SourceMapConsumerV3 consumer = new SourceMapConsumerV3();
    consumer.parse(sourceMap);

  }

  @Test
  public void testSourcesWithRoot() throws Exception {
    String sourceMap =  "{\n" +
                        "\"version\":3,\n" +
                        "\"file\":\"testcode\",\n" +
                        "\"lineCount\":1,\n" +
                        "\"sourceRoot\":\"http://server/path/\",\n" +
                        "\"mappings\":\"AAAAA,QAASA,UAAS,EAAG;\",\n" +
                        "\"sources\":[\"testcode\"],\n" +
                        "\"names\":[\"__BASIC__\"]\n" +
                        "}\n";

    SourceMapConsumerV3 consumer = new SourceMapConsumerV3();
    consumer.parse(sourceMap);

    //By default sourceRoot is not prepended
    assertThat(consumer.getOriginalSources()).containsExactly("testcode");
    assertThat(consumer.getSourceRoot()).isEqualTo("http://server/path/");
  }

  @Test
  public void testExtensions() throws Exception {
    String sourceMap =  "{\n" +
                        "\"version\":3,\n" +
                        "\"file\":\"testcode\",\n" +
                        "\"lineCount\":1,\n" +
                        "\"mappings\":\"AAAAA,QAASA,UAAS,EAAG;\",\n" +
                        "\"sources\":[\"testcode\"],\n" +
                        "\"names\":[\"__BASIC__\"],\n" +
                        "\"x_org_int\":2,\n" +
                        "\"x_org_array\":[]\n" +
                        "}\n";

    SourceMapConsumerV3 consumer = new SourceMapConsumerV3();
    consumer.parse(sourceMap);

    Map<String, Object> exts = consumer.getExtensions();

    assertThat(exts).hasSize(2);
    assertThat(exts).doesNotContainKey("org_int");
    assertThat(((JsonElement) exts.get("x_org_int")).getAsInt()).isEqualTo(2);
    assertThat(((JsonArray) exts.get("x_org_array")).size()).isEqualTo(0);
  }
}
