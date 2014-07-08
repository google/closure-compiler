/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CheckLevel;

public class CheckNoSideEffectExternCallsTest extends CompilerTestCase {
  public CheckNoSideEffectExternCallsTest() {
    this.parseTypeInfo = true;
    enableTypeCheck(CheckLevel.WARNING);
    allowExternsChanges(true);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckNoSideEffectExternCalls(compiler, CheckLevel.WARNING, true);
  }

  final DiagnosticType e = CheckNoSideEffectExternCalls.USELESS_CODE_ERROR;
  final DiagnosticType ok = null; // no warning

  public void testUselessCode() {
    String externs = "/** @return {boolean}\n * @nosideeffects */\n" +
        "function NoSideEffectsExtern(){}";

    test(externs, "alert(NoSideEffectsExtern());",
        "alert(NoSideEffectsExtern());", ok, null);

    test(externs, "NoSideEffectsExtern();",
        "JSCOMPILER_PRESERVE(NoSideEffectsExtern());",
        null, e, "Suspicious code. The result of the extern function call " +
        "\"NoSideEffectsExtern\" is not being used.");
  }
}

