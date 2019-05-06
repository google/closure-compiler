/*
 * Copyright 2019 The Closure Compiler Authors.
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
import static com.google.javascript.jscomp.DiagnosticGroups.ES5_STRICT;

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.rhino.Node;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Unit tests for {@link AstAnalyzer}.
 *
 * <p>IMPORTANT: Do not put {@code {@literal @}Test} methods directly in this class, they must be
 * inside an inner class or they won't be executed, because we're using the {@code Enclosed} JUnit
 * runner.
 */
@RunWith(Enclosed.class)
public class AstAnalyzerTest {

  /** Provides methods for parsing and accessing the compiler used for the parsing. */
  private static class ParseHelper {
    private Compiler compiler = null;

    private Node parse(String js) {
      CompilerOptions options = new CompilerOptions();
      options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);

      // To allow octal literals such as 0123 to be parsed.
      options.setStrictModeInput(false);
      options.setWarningLevel(ES5_STRICT, CheckLevel.OFF);

      compiler = new Compiler();
      compiler.initOptions(options);
      Node n = compiler.parseTestCode(js);
      assertThat(compiler.getErrors()).isEmpty();
      return n;
    }

    private AstAnalyzer getAstAnalyzer() {
      return compiler.getAstAnalyzer();
    }
  }

  @RunWith(Parameterized.class)
  public static final class MayEffectMutableStateTest {
    @Parameter(0)
    public String jsExpression;

    @Parameter(1)
    public Boolean expectedResult;

    @Parameters(name = "({0}) -> {1}")
    public static Iterable<Object[]> cases() {
      return ImmutableList.copyOf(
          new Object[][] {
            {"i++", true},
            {"[b, [a, i++]]", true},
            {"i=3", true},
            {"[0, i=3]", true},
            {"b()", true},
            {"void b()", true},
            {"[1, b()]", true},
            {"b.b=4", true},
            {"b.b--", true},
            {"i--", true},
            {"a[0][i=4]", true},
            {"a += 3", true},
            {"a, b, z += 4", true},
            {"a ? c : d++", true},
            {"a + c++", true},
            {"a + c - d()", true},
            {"a + c - d()", true},
            {"function foo() {}", true},
            {"while(true);", true},
            {"if(true){a()}", true},
            {"if(true){a}", false},
            {"(function() { })", true},
            {"(function() { i++ })", true},
            {"[function a(){}]", true},
            {"a", false},
            {"[b, c [d, [e]]]", true},
            {"({a: x, b: y, c: z})", true},
            // Note: RegExp objects are not immutable, for instance, the exec
            // method maintains state for "global" searches.
            {"/abc/gi", true},
            {"'a'", false},
            {"0", false},
            {"a + c", false},
            {"'c' + a[0]", false},
            {"a[0][1]", false},
            {"'a' + c", false},
            {"'a' + a.name", false},
            {"1, 2, 3", false},
            {"a, b, 3", false},
            {"(function(a, b) {  })", true},
            {"a ? c : d", false},
            {"'1' + navigator.userAgent", false},
            {"new RegExp('foobar', 'i')", true},
            {"new RegExp(SomethingWacky(), 'i')", true},
            {"new Array()", true},
            {"new Array", true},
            {"new Array(4)", true},
            {"new Array('a', 'b', 'c')", true},
            {"new SomeClassINeverHeardOf()", true},
          });
    }

    @Test
    public void mayEffectMutableState() {
      ParseHelper helper = new ParseHelper();
      // we want the first child of the script, not the script itself.
      Node statementNode = helper.parse(jsExpression).getFirstChild();
      AstAnalyzer analyzer = helper.getAstAnalyzer();
      assertThat(analyzer.mayEffectMutableState(statementNode)).isEqualTo(expectedResult);
    }
  }
}
