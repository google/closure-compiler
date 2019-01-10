/*
 * Copyright 2008 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;

import com.google.javascript.rhino.Node;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit test for {@link InlineCostEstimator}.
 *
 * @author johnlenz@google.com (John Lenz)
 */
@RunWith(JUnit4.class)
public final class InlineCostEstimatorTest {

  static Node parse(String js) {
    Compiler compiler = new Compiler();
    Node n = compiler.parseTestCode(js);
    assertThat(compiler.getErrors()).isEmpty();
    return n;
  }

  static String minimize(String js) {
    CompilerOptions options = new CompilerOptions();
    options.setLineLengthThreshold(Integer.MAX_VALUE);
    return new CodePrinter.Builder(parse(js)).
        setCompilerOptions(options).
        build();
  }

  static long cost(String js) {
    return InlineCostEstimator.getCost(parse(js));
  }

  @Test
  public void testCost() {
    checkCost("1", "1");
    checkCost("true", "1");
    checkCost("false", "1");
    checkCost("a", "xx");
    checkCost("a + b", "xx+xx");
    checkCost("foo()", "xx()");
    checkCost("foo(a,b)", "xx(xx,xx)");
    checkCost("10 + foo(a,b)", "0+xx(xx,xx)");
    checkCost("1 + foo(a,b)", "1+xx(xx,xx)");
    checkCost("a ? 1 : 0", "xx?1:0");
    checkCost("a.b", "xx.xx");
    checkCost("new Obj()", "new xx");
    checkCost("function a() {return \"monkey\"}",
              "function xx(){return\"monkey\"}");
  }

  private void checkCost(String source, String example) {

    // The example string should have been minified already.
    assertThat(example).isEqualTo(minimize(example));

    // cost estimate should be the same as the length of the example string.
    assertThat(cost(source)).isEqualTo(example.length());
  }
}
