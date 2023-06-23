/*
 * Copyright 2019 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.deps;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.javascript.jscomp.deps.JsFileFullParser.FileInfo;
import com.google.javascript.jscomp.deps.JsFileFullParser.Reporter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link JsFileFullParser}.
 *
 * <p>TODO(tjgq): Add more tests.
 */
@RunWith(JUnit4.class)
public final class JsFileFullParserTest {

  @Test
  public void testModuleType_script() {
    FileInfo info = parse("alert(42);");
    assertThat(info.moduleType).isEqualTo(FileInfo.ModuleType.UNKNOWN);
  }

  @Test
  public void testModuleType_cjs() {
    FileInfo info = parse("const fs = require('fs');");
    assertThat(info.moduleType).isEqualTo(FileInfo.ModuleType.UNKNOWN);
  }

  @Test
  public void testModuleType_googProvide() {
    FileInfo info = parse("goog.provide('foo');");
    assertThat(info.moduleType).isEqualTo(FileInfo.ModuleType.GOOG_PROVIDE);
  }

  @Test
  public void testModuleType_googModule() {
    FileInfo info = parse("goog.module('foo');");
    assertThat(info.moduleType).isEqualTo(FileInfo.ModuleType.GOOG_MODULE);
  }

  @Test
  public void testModuleType_legacyGoogModule() {
    FileInfo info = parse("goog.module('foo');", "goog.module.declareLegacyNamespace();");
    assertThat(info.moduleType).isEqualTo(FileInfo.ModuleType.GOOG_MODULE);
  }

  @Test
  public void testModuleType_esModule_export() {
    FileInfo info = parse("export function f() {};");
    assertThat(info.moduleType).isEqualTo(FileInfo.ModuleType.ES_MODULE);
  }

  @Test
  public void testModuleType_esModule_import() {
    FileInfo info = parse("import * as x from './bar';");
    assertThat(info.moduleType).isEqualTo(FileInfo.ModuleType.ES_MODULE);
  }

  @Test
  public void testProvidesRequires() {
    FileInfo info =
        parse(
            "goog.provide('providedSymbol');",
            "goog.require('stronglyRequiredSymbol');",
            "goog.requireType('weaklyRequiredSymbol');",
            "async function test() {await goog.requireDynamic('dynamicallyRequiredSymbol');}");

    assertThat(info.provides).containsExactly("providedSymbol");
    assertThat(info.requires).containsExactly("stronglyRequiredSymbol");
    assertThat(info.typeRequires).containsExactly("weaklyRequiredSymbol");
    assertThat(info.dynamicRequires).containsExactly("dynamicallyRequiredSymbol");
  }

  @Test
  public void testProvidesRequires_syntaxError() {
    String src =
        Joiner.on('\n')
            .join(
                "goog.provide('providedSymbol');",
                "goog.require('stronglyRequiredSymbol');",
                "goog.requireType('weaklyRequiredSymbol');",
                "",
                "syntax error;");

    class ErrorCollector implements Reporter {
      private String lastErrorMessage;

      @Override
      public void report(
          boolean fatal, String message, String sourceName, int line, int lineOffset) {
        this.lastErrorMessage = message;
      }
    }

    ErrorCollector errorCollector = new ErrorCollector();
    FileInfo info = JsFileFullParser.parse(src, "file.js", errorCollector);

    assertThat(info.provides).isEmpty();
    assertThat(info.requires).isEmpty();
    assertThat(info.typeRequires).isEmpty();
    assertThat(errorCollector.lastErrorMessage).isEqualTo("Semi-colon expected");
  }

  @Test
  public void testGoogLoadModule() {
    FileInfo info =
        parse(
            "goog.loadModule((exports) => {",
            "  goog.module('inner');",
            "  const i = goog.require('inner_require');",
            "  exports = {inner: 1};",
            "  return exports;",
            "});",
            "goog.loadModule((exports) => {",
            "  goog.module('inner2');",
            "  const i = goog.require('inner');", // intentional self-edge
            "  exports = {inner2: 1};",
            "  return exports;",
            "});");

    assertThat(info.provides).containsExactly("inner", "inner2");
    assertThat(info.requires).containsExactly("inner", "inner_require");
  }

  @Test
  public void testGoogLoadModule_inModule() {
    FileInfo info =
        parse(
            "goog.module('outer');",
            "const i = goog.require('outer_require');",
            "goog.loadModule((exports) => {",
            "  goog.module('inner');",
            "  const i = goog.require('inner_require');",
            "  const i2 = goog.require('outer');", // intentional self-edge
            "  exports = {inner: 1};",
            "  return exports;",
            "});",
            "goog.loadModule((exports) => {",
            "  goog.module('inner2');",
            "  const i = goog.require('inner');", // intentional self-edge
            "  exports = {inner2: 1};",
            "  return exports;",
            "});",
            "exports = {outer: 1};");

    assertThat(info.provides).containsExactly("outer", "inner", "inner2");
    assertThat(info.requires).containsExactly("inner", "inner_require", "outer", "outer_require");
  }

  @Test
  public void testGoogLoadModule_doubleNested() {
    // NB: we cannot use assertThrows: the bazel CI setup doe not have JUnit 5.
    try {
      parse(
          "goog.module('outer');",
          "const i = goog.require('outer_require');",
          "goog.loadModule((exports) => {",
          "  goog.module('inner');",
          "  goog.loadModule((exports) => {",
          "    goog.module('inner.inner');",
          "    exports = {inner2: 1};",
          "    return exports;",
          "  });",
          "  const i = goog.require('inner_require');",
          "  const i2 = goog.require('outer');", // intentional self-edge
          "  exports = {inner: 1};",
          "  return exports;",
          "});",
          "exports = {outer: 1};");
      throw new RuntimeException("expected an AssertionError");
    } catch (AssertionError e) {
      assertThat(e).hasMessageThat().contains("goog.loadModule cannot be nested");
    }
  }

  @Test
  public void testSoyDeltemplate() {
    FileInfo info = parse("/**", " * @deltemplate {a}", " * @modName {m}", " */");
    assertThat(info.deltemplates).containsExactly("a");
  }

  @Test
  public void testSoyDeltemplate_legacy() {
    FileInfo info = parse("/**", " * @hassoydeltemplate {a}", " * @modName {m}", " */");
    assertThat(info.deltemplates).containsExactly("a");
  }

  @Test
  public void testSoyDelcall() {
    FileInfo info = parse("/**", " * @delcall {a}", " */");
    assertThat(info.delcalls).containsExactly("a");
  }

  @Test
  public void testSoyDelcall_legacy() {
    FileInfo info = parse("/**", " * @hassoydelcall {a}", " */");
    assertThat(info.delcalls).containsExactly("a");
  }

  @Test
  public void testReadToggle() {
    FileInfo info = parse("goog.readToggleInternalDoNotCallDirectly('foo_bar');");
    assertThat(info.readToggles).containsExactly("foo_bar");
  }

  private static FileInfo parse(String... lines) {
    return JsFileFullParser.parse(
        Joiner.on('\n').join(lines),
        "file.js",
        (boolean fatal, String message, String sourceName, int line, int lineOffset) ->
            fail(String.format("%s:%d:%d: %s", sourceName, line, lineOffset, message)));
  }
}
