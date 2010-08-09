/*
 * Copyright 2009 Google Inc.
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
 * Unit test for {@linke RemoveConstantExpressionsParallel}. It does not verify
 * the correctness of {@link RemoveConstantExpressions} since that's the job
 * of {@link RemoveConstantExpressionsTest}.
 *
 *
 */
public class RemoveConstantExpressionsParallelTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RemoveConstantExpressionsParallel(compiler, 4);
  }

  public void testOneFile() {
    runInParallel(1);
  }

  public void testTwoFiles() {
    runInParallel(2);
  }

  public void testFourFiles() {
    runInParallel(4);
  }

  public void testManyFiles() {
    runInParallel(100);
  }

  private void runInParallel(int numFiles) {
    String input = "1 + (x.a = 2)";
    String expected = "x.a = 2";
    String[] inputFiles = new String[numFiles];
    String[] expectedFiles = new String[numFiles];
    for (int i = 0; i < numFiles; i++) {
      inputFiles[i] = input;
      expectedFiles[i] = expected;
    }
    test(inputFiles, expectedFiles);
  }
}
