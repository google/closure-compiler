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

/**
 * CompilerTestCase for passes that run after type checking and use type information.
 * Allows us to test those passes with both type checkers.
 *
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public abstract class TypeICompilerTestCase extends CompilerTestCase {
  @Override
  public void testSame(String js) {
    enableTypeCheck();
    super.testSame(js);
    disableTypeCheck();
    enableNewTypeInference();
    super.testSame(js);
    disableNewTypeInference();
  }

  @Override
  public void testSame(String js, DiagnosticType warning) {
    enableTypeCheck();
    super.testSame(js, warning);
    disableTypeCheck();
    enableNewTypeInference();
    super.testSame(js, warning);
    disableNewTypeInference();
  }

  @Override
  public void testSame(String externs, String js, DiagnosticType warning) {
    enableTypeCheck();
    super.testSame(externs, js, warning);
    disableTypeCheck();
    enableNewTypeInference();
    super.testSame(externs, js, warning);
    disableNewTypeInference();
  }
}

