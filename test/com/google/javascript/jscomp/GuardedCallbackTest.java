/*
 * Copyright 2020 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.testing.TestExternsBuilder;
import com.google.javascript.rhino.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link GuardedCallback} */
@RunWith(JUnit4.class)
public final class GuardedCallbackTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.UNSUPPORTED);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CompilerPass() {
      @Override
      public void process(Node externs, Node root) {
        NodeTraversal.traverse(compiler, root, new GuardSwitchingCallback(compiler));
      }
    };
  }

  /**
   * Replaces all guarded name and property references with "GUARDED_NAME" and "GUARDED_PROP",
   * respectively.
   */
  private class GuardSwitchingCallback extends GuardedCallback<String> {

    GuardSwitchingCallback(AbstractCompiler compiler) {
      super(compiler);
    }

    @Override
    void visitGuarded(NodeTraversal traversal, Node n, Node parent) {
      if (n.isName() && isGuarded(n.getString())) {
        n.setString("GUARDED_NAME");
        traversal.reportCodeChange();
      } else if (n.isGetProp()) {
        Node propNode = n.getSecondChild();
        if (isGuarded(propNode.getString())) {
          propNode.setString("GUARDED_PROP");
          traversal.reportCodeChange();
        }
      }
    }
  }

  @Test
  public void unguardedTest() {
    final String externs = new TestExternsBuilder().addConsole().addPromise().build();
    testSame(externs(externs), srcs("console.log(Promise.allSettled);"));
  }

  @Test
  public void guardedTest() {
    final String externs = new TestExternsBuilder().addConsole().addPromise().build();
    test(
        externs(externs),
        srcs("console.log(Promise && Promise.allSettled && Promise.allSettled);"),
        expected(
            "console.log(GUARDED_NAME && GUARDED_NAME.GUARDED_PROP && GUARDED_NAME.GUARDED_PROP)"));
  }

  @Test
  public void guardedAndUnguardedTest() {
    final String externs = new TestExternsBuilder().addConsole().addPromise().build();
    test(
        externs(externs),
        srcs("console.log(Promise && Promise.allSettled && Promise.finally);"),
        // The values of `Promise` and `Promise.allSettled` are only checked for truthiness,
        // so they can be considered guarded. However, the value of `Promise.finally` may end up
        // getting passed to `console.log()`, so it is not guarded.
        expected(
            "console.log(GUARDED_NAME && GUARDED_NAME.GUARDED_PROP && GUARDED_NAME.finally)"));
  }
}
