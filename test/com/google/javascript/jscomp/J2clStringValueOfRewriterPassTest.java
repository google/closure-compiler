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
package com.google.javascript.jscomp;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class J2clStringValueOfRewriterPassTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new PeepholeOptimizationsPass(compiler, getName(), new J2clStringValueOfRewriterPass());
  }

  @Override
  protected Compiler createCompiler() {
    Compiler compiler = super.createCompiler();
    J2clSourceFileChecker.markToRunJ2clPasses(compiler);
    return compiler;
  }

  @Test
  public void testRemoveStringValueOf() {
    test("module$exports$java$lang$String$impl.m_valueOf__java_lang_Object('')", "String('')");
    test(
        "module$exports$java$lang$String$impl.m_valueOf__java_lang_Object(null)", "String(null)");
    test("module$exports$java$lang$String$impl.m_valueOf__java_lang_Object(undefined)", "'null'");
    test("module$exports$java$lang$String$impl.m_valueOf__java_lang_Object(void 0)", "'null'");
    test(
        lines(
            "module$exports$java$lang$String$impl.m_valueOf__java_lang_Object('foo' +",
            "    module$exports$java$lang$String$impl.m_valueOf__java_lang_Object(bar))"),
        "String('foo' + module$exports$java$lang$String$impl.m_valueOf__java_lang_Object(bar))");
    test(
        "module$exports$java$lang$String$impl.m_valueOf__java_lang_Object(foo + 'bar' + baz)",
        "String(foo + 'bar' + baz)");
    test(
        "foo + module$exports$java$lang$String$impl.m_valueOf__java_lang_Object('bar' + baz)",
        "foo + String('bar' + baz)");
    test("module$exports$java$lang$String$impl.m_valueOf__java_lang_Object(1)", "String(1)");
    test(
        "module$exports$java$lang$String$impl.m_valueOf__java_lang_Object(null + foo)",
        "String(null + foo)");
    testSame("module$exports$java$lang$String$impl.m_valueOf__java_lang_Object(window)");
    testSame("module$exports$java$lang$String$impl.m_valueOf__java_lang_Object([])");
  }
}
