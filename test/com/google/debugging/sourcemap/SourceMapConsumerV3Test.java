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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import junit.framework.TestCase;

import java.util.Map;

/**
 * Tests for {@link SourceMapConsumerV3}
 *
 */
public class SourceMapConsumerV3Test extends TestCase {

  public void testSources() throws Exception{
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

    String[] sources = (String[]) consumer.getOriginalSources().toArray();

    assertEquals(1, sources.length);
    assertEquals(null, consumer.getSourceRoot());
    assertEquals("testcode", sources[0]);
  }

  public void testMap() throws Exception{
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

  public void testSourcesWithRoot() throws Exception{
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

    String[] sources = (String[]) consumer.getOriginalSources().toArray();

    assertEquals(1, sources.length);
    assertEquals("http://server/path/", consumer.getSourceRoot());
    //By default sourceRoot is not prepended
    assertEquals("testcode", sources[0]);
  }

  public void testExtensions() throws Exception{
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

    assertEquals(2, exts.size());
    assertFalse(exts.containsKey("org_int"));
    assertEquals(2, ((JsonElement) exts.get("x_org_int")).getAsInt());
    assertEquals(0, ((JsonArray) exts.get("x_org_array")).size());
  }

}
