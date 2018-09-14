/*
 * Copyright 2015 The Closure Compiler Authors.
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

/** Tests for {@link J2clUtilGetDefineRewriterPass}. */
@RunWith(JUnit4.class)
public class J2clUtilGetDefineRewriterPassTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new J2clUtilGetDefineRewriterPass(compiler);
  }

  @Override
  protected Compiler createCompiler() {
    Compiler compiler = super.createCompiler();
    J2clSourceFileChecker.markToRunJ2clPasses(compiler);
    return compiler;
  }

  @Test
  public void testUtilGetDefine() {
    String defineAbc = "var a={}; a.b={}; /** @define {boolean} */ a.b.c = true;\n";
    test(
        defineAbc + "nativebootstrap.Util.$getDefine('a.b.c', 'def');",
        defineAbc + "('def', String(a.b.c));");
    test(
        defineAbc + "nativebootstrap.Util.$getDefine('a.b.c');",
        defineAbc + "(null, String(a.b.c));");
  }

  @Test
  public void testUtilGetDefine_notDefined() {
    test("nativebootstrap.Util.$getDefine('not.defined');", "null;");
    test("nativebootstrap.Util.$getDefine('not.defined', 'def');", "'def';");
  }
}
