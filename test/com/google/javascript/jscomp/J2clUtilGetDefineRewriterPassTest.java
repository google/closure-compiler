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

import com.google.javascript.jscomp.ProcessDefines.Mode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link J2clUtilGetDefineRewriterPass}. */
@RunWith(JUnit4.class)
public class J2clUtilGetDefineRewriterPassTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return (externs, root) -> {
      // We require the ProcessDefines pass to run first, so run it in optimize mode so that we
      // replicate the state we expect the code to be in when it reaches this pass.
      new ProcessDefines.Builder(compiler).setMode(Mode.OPTIMIZE).build().process(externs, root);
      new J2clUtilGetDefineRewriterPass(compiler).process(externs, root);
    };
  }

  @Override
  protected Compiler createCompiler() {
    Compiler compiler = super.createCompiler();
    J2clSourceFileChecker.markToRunJ2clPasses(compiler);
    return compiler;
  }

  @Test
  public void testUtilGetDefine() {
    test(
        """
        var a = {};
        a.b = {}
        /** @define {boolean} */ a.b.c = goog.define('a.b.c', true);
        nativebootstrap.Util.$getDefine('a.b.c', 'def');
        """,
        """
        var a = {};
        a.b = {}
        /** @define {boolean} */ a.b.c = true;
        var jscomp$defines$a$b$c = a.b.c;
        ('def', String(jscomp$defines$a$b$c));
        """);
    test(
        """
        var a = {};
        a.b = {}
        /** @define {boolean} */ a.b.c = goog.define('a.b.c', true);
        nativebootstrap.Util.$getDefine('a.b.c');
        """,
        """
        var a = {};
        a.b = {}
        /** @define {boolean} */ a.b.c = true;
        var jscomp$defines$a$b$c = a.b.c;
        (null, String(jscomp$defines$a$b$c));
        """);
    test(
        """
        /** @define {boolean} */ var x = goog.define('x', 1);
        /** @define {boolean} */ var y = goog.define('y', x);
        nativebootstrap.Util.$getDefine('x');
        nativebootstrap.Util.$getDefine('y');
        """,
        """
        /** @define {boolean} */ var x = 1;
        var jscomp$defines$x = x;
        /** @define {boolean} */ var y = x;
        var jscomp$defines$y = y;
        (null, String(jscomp$defines$x));
        (null, String(jscomp$defines$y));
        """);
    test("nativebootstrap.Util.$getDefine('COMPILED');", "(null, String(COMPILED));");
  }

  @Test
  public void testUtilGetDefine_notDefined() {
    test("nativebootstrap.Util.$getDefine('not.defined');", "null;");
    test("nativebootstrap.Util.$getDefine('not.defined', 'def');", "'def';");
  }
}
