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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@RunWith(JUnit4.class)
public final class J2clIntegrationTest extends IntegrationTestCase {
  @Test
  public void testStripNoSideEffectsClinit() {
    String source =
        LINE_JOINER.join(
            "class Preconditions {",
            "  static $clinit() {",
            "    Preconditions.$clinit = function() {};",
            "  }",
            "  static check(str) {",
            "    Preconditions.$clinit();",
            "    if (str[0] > 'a') {",
            "      return Preconditions.check(str + str);",
            "    }",
            "    return str;",
            "  }",
            "}",
            "class Main {",
            "  static main() {",
            "    var a = Preconditions.check('a');",
            "    alert('hello');",
            "  }",
            "}",
            "Main.main();");
    test(createCompilerOptions(), source, "alert('hello')");
  }

  @Test
  public void testFoldJ2clClinits() {
    String code =
        LINE_JOINER.join(
            "function InternalWidget(){}",
            "InternalWidget.$clinit = function () {",
            "  InternalWidget.$clinit = function() {};",
            "  InternalWidget.$clinit();",
            "};",
            "InternalWidget.$clinit();");

    test(createCompilerOptions(), code, "");
  }

  @Override
  @Before
  public void setUp() {
    super.setUp();
    inputFileNameSuffix = ".java.js";
  }

  @Override
  CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT6_TYPED);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    return options;
  }
}
