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
    test(
        "module$exports$java$lang$String$impl.m_valueOf__java_lang_Object__java_lang_String('')",
        "String('')");
    test(
        "module$exports$java$lang$String$impl.m_valueOf__java_lang_Object__java_lang_String(null)",
        "String(null)");
    test(
        lines(
            "module$exports$java$lang$String$impl",
            "    .m_valueOf__java_lang_Object__java_lang_String(undefined)"),
        "'null'");
    test(
        lines(
            "module$exports$java$lang$String$impl",
            "    .m_valueOf__java_lang_Object__java_lang_String(void 0)"),
        "'null'");
    test(
        lines(
            "module$exports$java$lang$String$impl",
            "   .m_valueOf__java_lang_Object__java_lang_String('foo' +",
            "       module$exports$java$lang$String$impl",
            "           .m_valueOf__java_lang_Object__java_lang_String(bar))"),
        lines(
            "String('foo' + module$exports$java$lang$String$impl",
            "   .m_valueOf__java_lang_Object__java_lang_String(bar))"));
    test(
        lines(
            "module$exports$java$lang$String$impl",
            "   .m_valueOf__java_lang_Object__java_lang_String(foo + 'bar' + baz)"),
        "String(foo + 'bar' + baz)");
    test(
        lines(
            "foo + module$exports$java$lang$String$impl",
            "   .m_valueOf__java_lang_Object__java_lang_String('bar' + baz)"),
        "foo + String('bar' + baz)");
    test(
        "module$exports$java$lang$String$impl.m_valueOf__java_lang_Object__java_lang_String(1)",
        "String(1)");
    test(
        lines(
            "module$exports$java$lang$String$impl",
            "    .m_valueOf__java_lang_Object__java_lang_String(null + foo)"),
        "String(null + foo)");
    testSame(
        lines(
            "module$exports$java$lang$String$impl",
            "   .m_valueOf__java_lang_Object__java_lang_String(window)"));
    testSame(
        lines(
            "module$exports$java$lang$String$impl",
            "    .m_valueOf__java_lang_Object__java_lang_String([])"));
  }
}
