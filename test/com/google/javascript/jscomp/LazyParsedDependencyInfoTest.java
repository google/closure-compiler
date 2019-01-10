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

package com.google.javascript.jscomp;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.deps.DependencyInfo;
import com.google.javascript.jscomp.deps.DependencyInfo.Require;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.deps.SimpleDependencyInfo;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link LazyParsedDependencyInfo}. */
@RunWith(JUnit4.class)
public final class LazyParsedDependencyInfoTest {

  @Test
  public void testDelegation() {
    Require baz = Require.googRequireSymbol("baz");
    Require qux = Require.googRequireSymbol("qux");
    Compiler compiler = new Compiler();
    JsAst ast = new JsAst(SourceFile.fromCode("file.js", "// nothing here"));
    SimpleDependencyInfo delegate =
        SimpleDependencyInfo.builder("path/to/1.js", "path/2.js")
            .setProvides("foo", "bar")
            .setRequires(baz, qux)
            .build();
    DependencyInfo info = new LazyParsedDependencyInfo(delegate, ast, compiler);

    assertThat(info.getName()).isEqualTo("path/2.js");
    assertThat(info.getPathRelativeToClosureBase()).isEqualTo("path/to/1.js");
    assertThat(info.getProvides()).containsExactly("foo", "bar");
    assertThat(info.getRequires()).containsExactly(baz, qux);
  }

  @Test
  public void testLoadFlagsParsesEs3() {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    JsAst ast = new JsAst(SourceFile.fromCode("file.js", "// nothing here"));
    SimpleDependencyInfo delegate =
        SimpleDependencyInfo.builder("", "")
            .setLoadFlags(ImmutableMap.of("foo", "bar"))
            .build();
    DependencyInfo info = new LazyParsedDependencyInfo(delegate, ast, compiler);

    // TODO(sdh): We're currently stuck on an earlier version of Truth that doesn't
    // provide MapSubject#containsExactly(Object, Object, Object...), and it is very
    // hard to upgrade due to parallel ant and maven builds.  Once this restriction
    // is lifted and we can depend on a newer Truth, these assertions should be
    // changed to assertThat(info.getLoadFlags()).containsExactly(...)
    assertThat(info.getLoadFlags()).containsExactly("foo", "bar");
    assertThat(info.isModule()).isFalse();
  }

  @Test
  public void testLoadFlagsParsesEs5() {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    JsAst ast = new JsAst(SourceFile.fromCode("file.js", "var x = [1, 2,];"));
    SimpleDependencyInfo delegate =
        SimpleDependencyInfo.builder("", "")
            .setLoadFlags(ImmutableMap.of("module", "goog"))
            .build();
    DependencyInfo info = new LazyParsedDependencyInfo(delegate, ast, compiler);

    assertThat(info.getLoadFlags()).containsExactly("module", "goog", "lang", "es5");
    assertThat(info.isModule()).isTrue();
  }

  @Test
  public void testLoadFlagsParsesEs6Impl() {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    JsAst ast = new JsAst(SourceFile.fromCode("file.js", "class X {}"));
    SimpleDependencyInfo delegate =
        SimpleDependencyInfo.builder("", "")
            .setLoadFlags(ImmutableMap.of("foo", "bar"))
            .build();
    DependencyInfo info = new LazyParsedDependencyInfo(delegate, ast, compiler);

    assertThat(info.getLoadFlags()).containsExactly("foo", "bar", "lang", "es6");
    assertThat(info.isModule()).isFalse();
  }

  @Test
  public void testLoadFlagsParsesEs6() {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    JsAst ast = new JsAst(SourceFile.fromCode("file.js", "let [a, b] = [1, 2];"));
    SimpleDependencyInfo delegate =
        SimpleDependencyInfo.builder("", "")
            .setLoadFlags(ImmutableMap.of("foo", "bar"))
            .build();
    DependencyInfo info = new LazyParsedDependencyInfo(delegate, ast, compiler);

    assertThat(info.getLoadFlags()).containsExactly("foo", "bar", "lang", "es6");
    assertThat(info.isModule()).isFalse();
  }

  @Test
  public void testParseIsLazy() {
    Compiler compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
    JsAst ast = new JsAst(SourceFile.fromCode("file.js", "parse error"));
    SimpleDependencyInfo delegate =
        SimpleDependencyInfo.builder("", "").build();
    DependencyInfo info = new LazyParsedDependencyInfo(delegate, ast, compiler);

    info.getName();
    info.getPathRelativeToClosureBase();
    info.getProvides();
    info.getRequires();

    assertThat(compiler.getErrorManager().getErrorCount()).isEqualTo(0);
    info.getLoadFlags();
    assertThat(compiler.getErrorManager().getErrorCount()).isAtLeast(1);
  }

  @Test
  public void testModuleConflict() {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_2015);
    compiler.initOptions(options);
    JsAst ast = new JsAst(SourceFile.fromCode("file.js", "export let foo = 42;"));
    SimpleDependencyInfo delegate =
        SimpleDependencyInfo.builder("", "my/js.js")
            .setLoadFlags(ImmutableMap.of("module", "goog"))
            .build();
    DependencyInfo info = new LazyParsedDependencyInfo(delegate, ast, compiler);

    assertThat(Arrays.asList(compiler.getErrorManager().getWarnings())).isEmpty();
    assertThat(info.getLoadFlags()).containsExactly("module", "es6", "lang", "es6");
    assertThat(Arrays.asList(compiler.getErrorManager().getWarnings()))
        .containsExactly(JSError.make(ModuleLoader.MODULE_CONFLICT, "my/js.js"));
  }
}
