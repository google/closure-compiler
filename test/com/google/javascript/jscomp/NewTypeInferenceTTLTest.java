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

package com.google.javascript.jscomp;


/**
 * Tests the type transformation language as implemented in the new type inference.
 */

public final class NewTypeInferenceTTLTest extends NewTypeInferenceTestBase {

  public void testTypecheckFunctionBodyWithTTLvars() {
    typeCheck(LINE_JOINER.join(
        "/**",
        " * @template T := 'number' =:",
        " * @return {T}",
        " */",
        "function f() { return 123; }"));

    // TODO(dimvar): warn for invalid return type.
    typeCheck(LINE_JOINER.join(
        "/**",
        " * @template T := 'number' =:",
        " * @return {T}",
        " */",
        "function f() { return 'asdf'; }"));

    // TODO(dimvar): warn for invalid assignment.
    typeCheck(LINE_JOINER.join(
        "/**",
        " * @template T := 'number' =:",
        " * @param {T} x",
        " */",
        "function f(x) { var /** string */ s = x; }"));

    // TODO(dimvar): warn for invalid assignment.
    typeCheck(LINE_JOINER.join(
        "/**",
        " * @template T := 'number' =:",
        " * @this {T}",
        " */",
        "function f() { var /** string */ s = this; }"));
  }
}
