/*
 * Copyright 2018 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.lint;

import static com.google.javascript.jscomp.lint.CheckNoMutatedEs6Exports.MUTATED_EXPORT;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CheckNoMutatedEs6ExportsTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckNoMutatedEs6Exports(compiler);
  }

  @Test
  public void testNeverMutatedExportIsOk() {
    testSame("export let x = 0;");
  }

  @Test
  public void testLocalWithExportedNameCanBeMutated() {
    testSame("export let x = 0; () => { let x = 0; x++; }");
  }

  @Test
  public void testMutatedDuringInitializationIsOk() {
    testSame("export let x = 0; x++;");
    testSame("let x = 0; export {x as y}; x++;");
  }

  @Test
  public void testMutatedInInnerScopeIsError() {
    testWarning("export let x = 0; () => x++;", MUTATED_EXPORT);
    testWarning("let x = 0; export {x as y}; () => x++;", MUTATED_EXPORT);
    testWarning("export function foo() {}; () => foo = 0;", MUTATED_EXPORT);
    testWarning("export default function foo() {}; () => foo = 0;", MUTATED_EXPORT);
    testWarning("export function foo() { foo = 0; };", MUTATED_EXPORT);
    testWarning("export let x = 0; export function foo() { x++; };", MUTATED_EXPORT);
  }
}
