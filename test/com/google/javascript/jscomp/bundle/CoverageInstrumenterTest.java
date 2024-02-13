/*
 * Copyright 2017 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.bundle;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Answers.RETURNS_SMART_NULLS;
import static org.mockito.Mockito.when;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.debugging.sourcemap.OriginalMapping;
import com.google.debugging.sourcemap.SourceMapConsumerV3;
import com.google.javascript.jscomp.JSError;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link CoverageInstrumenter}. */
@GwtIncompatible
@RunWith(JUnit4.class)
public final class CoverageInstrumenterTest {

  private Source.Transformer instrumenter;
  private CoverageInstrumenter.CompilerSupplier compiler;

  @Mock(answer = RETURNS_SMART_NULLS)
  CoverageInstrumenter.CompilerSupplier mockCompiler;

  private static final Path FOO_JS = Path.of("foo.js");
  private static final Path SOURCE_JS = Path.of("source.js");
  private static final ImmutableList<JSError> NO_ERRORS = ImmutableList.of();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    instrumenter = new CoverageInstrumenter(mockCompiler);
    compiler = CoverageInstrumenter.compilerSupplier();
  }

  // Tests for CoverageInstrumenter

  @Test
  public void testCoverageInstrumenter_instrument() {
    when(mockCompiler.compile(FOO_JS, "bar"))
        .thenReturn(new CoverageInstrumenter.CompileResult("result", NO_ERRORS, true, "srcmap"));
    assertThat(instrumenter.transform(source(FOO_JS, "bar")))
        .isEqualTo(
            Source.builder()
                .setPath(FOO_JS)
                .setOriginalCode("bar")
                .setCode("result")
                .setSourceMap("srcmap")
                .build());
  }

  @Test
  public void testCoverageInstrumenter_noInstrumentation() {
    when(mockCompiler.compile(FOO_JS, "bar"))
        .thenReturn(new CoverageInstrumenter.CompileResult("result", NO_ERRORS, false, "srcmap"));
    assertThat(instrumenter.transform(source(FOO_JS, "bar"))).isEqualTo(source(FOO_JS, "bar"));
  }

  private static Source source(Path path, String code) {
    return Source.builder().setPath(path).setCode(code).build();
  }

  // Tests for CompilerSupplier

  @Test
  public void testCompilerSupplier_instruments() throws Exception {
    CoverageInstrumenter.CompileResult result = compiler.compile(SOURCE_JS, "var x = 42;");
    String[] expected =
        new String[] {
          "(function(self){if(!self.window){self.window=self;self.window.top=self}})(typeof"
              + " self!==\"undefined\"?self:globalThis);",
          "var __jscov=window.top[\"__jscov\"]||",
          "(window.top[\"__jscov\"]={\"fileNames\":[],\"instrumentedLines\":[],\"executedLines\":[]});",
          "var JSCompiler_lcov_data_source_js=[];",
          "__jscov[\"executedLines\"].push(JSCompiler_lcov_data_source_js);",
          "__jscov[\"instrumentedLines\"].push(\"01\");",
          "__jscov[\"fileNames\"].push(\"source.js\");",
          "JSCompiler_lcov_data_source_js[0]=true;var x=42;"
        };

    assertThat(result.source).isEqualTo(Joiner.on("").join(expected));
    assertThat(result.errors).isEmpty();
    assertThat(result.transformed).isTrue();

    // Ensure that source map for "x" is correct.
    SourceMapConsumerV3 sourceMap = new SourceMapConsumerV3();
    sourceMap.parse(result.sourceMap);
    OriginalMapping mappingFoX = sourceMap.getMappingForLine(1, result.source.indexOf("x=42") + 1);
    assertThat(mappingFoX)
        .isEqualTo(
            OriginalMapping.builder()
                .setOriginalFile(SOURCE_JS.toString())
                .setLineNumber(1)
                .setColumnPosition(5)
                .setIdentifier("x")
                .setPrecision(OriginalMapping.Precision.EXACT)
                .build());
  }
}
