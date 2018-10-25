/*
 * Copyright 2016 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.transpile;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.Answers.RETURNS_SMART_NULLS;
import static org.mockito.Mockito.when;

import com.google.javascript.jscomp.bundle.TranspilationException;
import java.net.URI;
import java.net.URISyntaxException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link BaseTranspiler}. */

@RunWith(JUnit4.class)
public final class BaseTranspilerTest {

  private Transpiler transpiler;
  private BaseTranspiler.CompilerSupplier compiler;
  @Mock(answer = RETURNS_SMART_NULLS) BaseTranspiler.CompilerSupplier mockCompiler;

  private static final URI FOO_JS;
  private static final URI SOURCE_JS;

  static {
    try {
      FOO_JS = new URI("foo.js");
      SOURCE_JS = new URI("source.js");
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    transpiler = new BaseTranspiler(mockCompiler, "es6_runtime");
    compiler = new BaseTranspiler.CompilerSupplier();
  }

  // Tests for BaseTranspiler

  @Test
  public void testTranspiler_transpile() {
    when(mockCompiler.compile(FOO_JS, "bar"))
        .thenReturn(new BaseTranspiler.CompileResult("result", true, "srcmap"));
    assertThat(transpiler.transpile(FOO_JS, "bar"))
        .isEqualTo(new TranspileResult(FOO_JS, "bar", "result", "srcmap"));
  }

  @Test
  public void testTranspiler_noTranspilation() {
    when(mockCompiler.compile(FOO_JS, "bar"))
        .thenReturn(new BaseTranspiler.CompileResult("result", false, "srcmap"));
    assertThat(transpiler.transpile(FOO_JS, "bar"))
        .isEqualTo(new TranspileResult(FOO_JS, "bar", "bar", ""));
  }

  @Test
  public void testTranspiler_runtime() {
    when(mockCompiler.runtime("es6_runtime")).thenReturn("$jscomp.es6();");
    assertThat(transpiler.runtime()).isEqualTo("$jscomp.es6();");
  }

  // Tests for CompilerSupplier

  @Test
  public void testCompilerSupplier_compileChanged() {
    BaseTranspiler.CompileResult result = compiler.compile(SOURCE_JS, "const x = () => 42;");
    assertThat(result.source).isEqualTo("var x = function() {\n  return 42;\n};\n");
    assertThat(result.transpiled).isTrue();
    assertThat(result.sourceMap)
        .contains("\"mappings\":\"AAAA,IAAMA,IAAIA,QAAA,EAAM;AAAA,SAAA,EAAA;AAAA,CAAhB;;\"");
  }

  @Test
  public void testCompilerSupplier_compileNoChange() {
    BaseTranspiler.CompileResult result = compiler.compile(SOURCE_JS, "var x = 42;");
    assertThat(result.source).isEqualTo("var x = 42;\n");
    assertThat(result.transpiled).isFalse();
    assertThat(result.sourceMap).isEmpty();
  }

  @Test
  public void testCompilerSupplier_error() {
    try {
      compiler.compile(SOURCE_JS, "cons x = () => 42;");
      assertWithMessage("Expected an exception.").fail();
    } catch (TranspilationException expected) {
    }
  }

  @Test
  public void testCompilerSupplier_runtime() {
    String runtime = compiler.runtime("es6_runtime");
    assertThat(runtime).contains("$jscomp.polyfill(\"Map\"");
    assertThat(runtime).contains("$jscomp.makeIterator");
    assertThat(runtime).contains("$jscomp.inherits");
  }
}
