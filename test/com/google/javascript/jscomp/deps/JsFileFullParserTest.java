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

import com.google.common.base.Joiner;
import com.google.javascript.jscomp.deps.JsFileFullParser.FileInfo;
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
    FileInfo info = parse("import * from './bar';");
    assertThat(info.moduleType).isEqualTo(FileInfo.ModuleType.ES_MODULE);
  }

  @Test
  public void testProvidesRequires() {
    FileInfo info =
        parse(
            "goog.provide('providedSymbol');",
            "goog.require('stronglyRequiredSymbol');",
            "goog.requireType('weaklyRequiredSymbol');");

    assertThat(info.provides).containsExactly("providedSymbol");
    assertThat(info.requires).containsExactly("stronglyRequiredSymbol");
    assertThat(info.typeRequires).containsExactly("weaklyRequiredSymbol");
  }

  private static FileInfo parse(String... lines) {
    return JsFileFullParser.parse(Joiner.on('\n').join(lines), "file.js", null);
  }
}
