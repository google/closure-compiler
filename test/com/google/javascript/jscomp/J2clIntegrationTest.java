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

import com.google.javascript.jscomp.CompilerOptions.J2clPassMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;


public final class J2clIntegrationTest extends IntegrationTestCase {
  public void testStripNoSideEffectsClinit() {
    String source =
        LINE_JOINER.join(
            "class Preconditions {",
            "  static clinit() {",
            "    Preconditions.clinit = function() {};",
            "  }",
            "  static checkNotNull(obj) {",
            "    Preconditions.clinit();",
            "    return obj;",
            "  }",
            "}",
            "class Main {",
            "  static main() {",
            "    var a = Preconditions.checkNotNull(null);",
            "    alert('hello!');",
            "  }",
            "}",
            "Main.main();");
    // TODO(tdeegan): Change to just "alert('hello') when we can determine clinit() is
    // pure and can be stripped.
    test(createCompilerOptions(), source, "function a(){a=function(){}}a();alert('hello!')");
  }

  @Override
  CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT6_TYPED);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setJ2clPass(J2clPassMode.ON);
    return options;
  }
}
