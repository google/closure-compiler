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
import com.google.javascript.jscomp.JSError;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link Transpiler}. */
@GwtIncompatible

@RunWith(JUnit4.class)
public final class TranspilerTest {

  private Source.Transformer transpiler;
  private Transpiler.CompilerSupplier compiler;

  @Mock(answer = RETURNS_SMART_NULLS)
  Transpiler.CompilerSupplier mockCompiler;

  private static final Path FOO_JS = Paths.get("foo.js");
  private static final Path SOURCE_JS = Paths.get("source.js");
  private static final JSError[] NO_ERRORS = new JSError[] {};

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    transpiler = new Transpiler(mockCompiler, "es6_runtime");
    compiler = Transpiler.compilerSupplier();
  }

  // Tests for Transpiler

  @Test
  public void testTranspiler_transpile() {
    when(mockCompiler.runtime("es6_runtime")).thenReturn("$jscomp.es6();");
    when(mockCompiler.compile(FOO_JS, "bar"))
        .thenReturn(new Transpiler.CompileResult("result", NO_ERRORS, true, "srcmap"));
    assertThat(transpiler.transform(source(FOO_JS, "bar")))
        .isEqualTo(
            Source.builder()
                .setPath(FOO_JS)
                .setOriginalCode("bar")
                .setCode("result")
                .setSourceMap("srcmap")
                .addRuntime("$jscomp.es6();")
                .build());
  }

  @Test
  public void testTranspiler_noTranspilation() {
    when(mockCompiler.compile(FOO_JS, "bar"))
        .thenReturn(new Transpiler.CompileResult("result", NO_ERRORS, false, "srcmap"));
    assertThat(transpiler.transform(source(FOO_JS, "bar"))).isEqualTo(source(FOO_JS, "bar"));
  }

  private static Source source(Path path, String code) {
    return Source.builder().setPath(path).setCode(code).build();
  }

  // Tests for CompilerSupplier

  @Test
  public void testCompilerSupplier_compileChanged() {
    Transpiler.CompileResult result = compiler.compile(SOURCE_JS, "const x = () => 42;");
    assertThat(result.source).isEqualTo("var x = function() {\n  return 42;\n};\n");
    assertThat(result.errors).isEmpty();
    assertThat(result.transformed).isTrue();
    assertThat(result.sourceMap)
        .contains("\"mappings\":\"AAAA,IAAMA,IAAIA,QAAA,EAAM;AAAA,SAAA,EAAA;AAAA,CAAhB;;\"");
  }

  @Test
  public void testCompilerSupplier_compileNoChange() {
    Transpiler.CompileResult result = compiler.compile(SOURCE_JS, "var x = 42;");
    assertThat(result.source).isEqualTo("var x = 42;\n");
    assertThat(result.errors).isEmpty();
    assertThat(result.transformed).isFalse();
    assertThat(result.sourceMap).isEmpty();
  }

  @Test
  public void testCompilerSupplier_runtime() {
    String runtime = compiler.runtime("es6_runtime");
    assertThat(runtime).contains("$jscomp.polyfill(\"Map\"");
    assertThat(runtime).contains("$jscomp.makeIterator");
    assertThat(runtime).contains("$jscomp.inherits");
  }
}
