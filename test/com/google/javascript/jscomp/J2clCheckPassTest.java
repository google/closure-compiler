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

import static com.google.javascript.jscomp.J2clChecksPass.J2CL_REFERENCE_EQUALITY;
import static com.google.javascript.jscomp.J2clChecksPass.REFERENCE_EQUALITY_TYPE_PATTERNS;

public class J2clCheckPassTest extends TypeICompilerTestCase {

  public J2clCheckPassTest() {
    super(DEFAULT_EXTERNS);
    enableTypeCheck();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new J2clChecksPass(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setJ2clPass(CompilerOptions.J2clPassMode.ON);
    return options;
  }

  public void testReferenceEquality_noWarning_other() {
    setFilename("java/lang/SomeClass.impl.java.js");
    testNoWarning(
        LINE_JOINER.join(
            "/** @constructor */",
            "var SomeClass = function() {};",
            "var x = new SomeClass();",
            "var y = new SomeClass();",
            "var a = x == y;"));
  }

  public void testReferenceEquality_noWarning_null() {
    for (String value : REFERENCE_EQUALITY_TYPE_PATTERNS.values()) {
      setFilename(value);
      testNoWarning(
          LINE_JOINER.join(
              "/** @constructor */",
              "var SomeClass = function() {};",
              "var x = new SomeClass();",
              "var a = x == null;"));
    }
  }

  public void testReferenceEquality_noWarning_undefined() {
    for (String value : REFERENCE_EQUALITY_TYPE_PATTERNS.values()) {
      setFilename(value);
      testNoWarning(
          LINE_JOINER.join(
              "/** @constructor */",
              "var SomeClass = function() {};",
              "var x = new SomeClass();",
              "var a = x == undefined;"));
    }
  }

  public void testReferenceEquality_warning() {
    for (String value : REFERENCE_EQUALITY_TYPE_PATTERNS.values()) {
      setFilename(value);
      testWarning(
          LINE_JOINER.join(
              "/** @constructor */",
              "var SomeClass = function() {};",
              "var x = new SomeClass();",
              "var y = new SomeClass();",
              "var a = x == y;"),
          J2CL_REFERENCE_EQUALITY);
    }
  }
}
