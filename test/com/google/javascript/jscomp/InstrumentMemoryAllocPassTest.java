/*
 * Copyright 2013 The Closure Compiler Authors.
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
 * Unit tests for {@link InstrumentMemoryAllocPass}.
 * Note: The order of test execution matters because the instrumentation
 * uniquely identifies memory allocation sites. Thus, the order is fixed by
 * combining tests into a single method.
 */
public class InstrumentMemoryAllocPassTest extends CompilerTestCase {

  public InstrumentMemoryAllocPassTest() {
    super();
    enableLineNumberCheck(false);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new InstrumentMemoryAllocPass(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    // This pass is not idempotent so run once.
    return 1;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setInstrumentMemoryAllocations(true);
    return getOptions(options);
  }

  public void testNoAllocation() {
    test(
        "var v",
        InstrumentMemoryAllocPass.JS_INSTRUMENT_ALLOCATION_CODE + "var v");
  }

  public void testNoStringInstrumentation() {
    test(
        "var s = 'a' + 'b'",
        InstrumentMemoryAllocPass.JS_INSTRUMENT_ALLOCATION_CODE
            + "var s=\"a\"+\"b\"");
  }

  public void testAllocations() {
    test(
        "var o = {}",
        InstrumentMemoryAllocPass.JS_INSTRUMENT_ALLOCATION_CODE
            + "var o=__alloc({},\"testcode:1\",1,\"Object\")");

    test(
        "var a = []",
        InstrumentMemoryAllocPass.JS_INSTRUMENT_ALLOCATION_CODE
            + "var a=__alloc([],\"testcode:1\",2,\"Array\")");

    test(
        "var f = function() {}",
        InstrumentMemoryAllocPass.JS_INSTRUMENT_ALLOCATION_CODE
            + "var f=__alloc(function(){},\"testcode:1\",3,\"Function\")");
  }
}
