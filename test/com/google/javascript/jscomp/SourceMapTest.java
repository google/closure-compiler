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

package com.google.javascript.jscomp;

import com.google.debugging.sourcemap.SourceMapConsumer;
import com.google.debugging.sourcemap.SourceMapConsumerV2;
import com.google.debugging.sourcemap.SourceMapTestCase;
import com.google.javascript.jscomp.SourceMap.Format;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author johnlenz@google.com (John Lenz)
 */
public class SourceMapTest extends SourceMapTestCase {

  public SourceMapTest() {
  }

  private List<SourceMap.LocationMapping> mappings;

  public void testPrefixReplacement1() throws IOException {
    mappings = new ArrayList<SourceMap.LocationMapping>();
    // mapping can be used to remove a prefix
    mappings.add( new SourceMap.LocationMapping("pre/","") );
    checkSourceMap2("", "pre/file1", "", "pre/file2" , "{\n" +
        "\"version\":2,\n" +
        "\"file\":\"testcode\",\n" +
        "\"lineCount\":1,\n" +
        "\"lineMaps\":[\"\"],\n" +
        "\"mappings\":[],\n" +
        "\"sources\":[\"file1\",\"file2\"],\n" +
        "\"names\":[]\n" +
        "}\n");
  }

    public void testPrefixReplacement2() throws IOException {
    mappings = new ArrayList<SourceMap.LocationMapping>();
    // mapping can be used to replace a prefix
    mappings.add( new SourceMap.LocationMapping("pre/file","src") );
    checkSourceMap2("", "pre/file1", "", "pre/file2" , "{\n" +
        "\"version\":2,\n" +
        "\"file\":\"testcode\",\n" +
        "\"lineCount\":1,\n" +
        "\"lineMaps\":[\"\"],\n" +
        "\"mappings\":[],\n" +
        "\"sources\":[\"src1\",\"src2\"],\n" +
        "\"names\":[]\n" +
        "}\n");
  }

  public void testPrefixReplacement3() throws IOException {
    mappings = new ArrayList<SourceMap.LocationMapping>();
    // multiple mappings can be applied
    mappings.add( new SourceMap.LocationMapping("file1","x") );
    mappings.add( new SourceMap.LocationMapping("file2","y") );
    checkSourceMap2("", "file1", "", "file2" , "{\n" +
        "\"version\":2,\n" +
        "\"file\":\"testcode\",\n" +
        "\"lineCount\":1,\n" +
        "\"lineMaps\":[\"\"],\n" +
        "\"mappings\":[],\n" +
        "\"sources\":[\"x\",\"y\"],\n" +
        "\"names\":[]\n" +
        "}\n");
  }

  public void testPrefixReplacement4() throws IOException {
    mappings = new ArrayList<SourceMap.LocationMapping>();
    // first match wins
    mappings.add( new SourceMap.LocationMapping("file1","x") );
    mappings.add( new SourceMap.LocationMapping("file","y") );
    checkSourceMap2("", "file1", "", "file2" , "{\n" +
        "\"version\":2,\n" +
        "\"file\":\"testcode\",\n" +
        "\"lineCount\":1,\n" +
        "\"lineMaps\":[\"\"],\n" +
        "\"mappings\":[],\n" +
        "\"sources\":[\"x\",\"y2\"],\n" +
        "\"names\":[]\n" +
        "}\n");
  }

  @Override
  protected CompilerOptions getCompilerOptions() {
    CompilerOptions options = super.getCompilerOptions();
    if (mappings != null) {
      options.sourceMapLocationMappings = mappings;
    }
    return options;
  }

  @Override
  public void setUp() {
    super.setUp();
  }

  private void checkSourceMap2(
      String js1, String file1, String js2, String file2, String expectedMap)
      throws IOException {
    RunResult result = compile(js1, file1, js2, file2);
    assertEquals(expectedMap, result.sourceMapFileContent);
    assertEquals(result.sourceMapFileContent, getSourceMap(result));
  }

  @Override
  protected Format getSourceMapFormat() {
    return Format.V2;
  }

  @Override
  protected SourceMapConsumer getSourceMapConsumer() {
    return new SourceMapConsumerV2();
  }
}
