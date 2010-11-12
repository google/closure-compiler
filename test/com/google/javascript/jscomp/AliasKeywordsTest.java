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

/**
 * Tests for {@link AliasKeywords}.
 *
 */
public class AliasKeywordsTest extends CompilerTestCase {
  private static final int ENOUGH_TO_ALIAS_LITERAL
      = AliasKeywords.MIN_OCCURRENCES_REQUIRED_TO_ALIAS_LITERAL;
  private static final int TOO_FEW_TO_ALIAS_LITERAL
      = ENOUGH_TO_ALIAS_LITERAL - 1;

  private static final int ENOUGH_TO_ALIAS_THROW
    = AliasKeywords.MIN_OCCURRENCES_REQUIRED_TO_ALIAS_THROW;
  private static final int TOO_FEW_TO_ALIAS_THROW
    = ENOUGH_TO_ALIAS_THROW - 1;

  @Override
  public void setUp() {
    super.enableLineNumberCheck(false);
    super.enableNormalize();
  }

  @Override
  public CompilerPass getProcessor(Compiler compiler) {
    return new AliasKeywords(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  /**
   * Generate code of the form 'if (<keyword>);' repeated numReps
   * times, with prepend prepended.
   *
   * For example, generateCode("true", 2, "var a=b;") generates
   * <code>var a=b;if (true);if (true);</code>
   */
  private static String generateCode(
      String keyword, int numReps, String prepend) {
    StringBuilder sb = new StringBuilder(prepend);
    for (int i = 0; i < numReps; i++) {
      sb.append("if (");
      sb.append(keyword);
      sb.append(");");
    }
    return sb.toString();
  }

  private static String generateCode(String keyword, int numReps) {
    return generateCode(keyword, numReps, "");
  }

  private static String generatePreProcessThrowCode(int repititions,
                                                    String whatToThrow) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < repititions; i++) {
      sb.append("throw ");
      sb.append(whatToThrow);
      sb.append(";");
    }
    return sb.toString();
  }

  private static String generatePostProcessThrowCode(
      int repetitions, String code, String whatToThrow) {
    StringBuilder sb = new StringBuilder();
    sb.append("function ");
    sb.append(AliasKeywords.ALIAS_THROW);
    sb.append("(jscomp_throw_param){throw jscomp_throw_param;}");
    sb.append(code);
    for (int i = 0; i < repetitions; i++) {
      sb.append(AliasKeywords.ALIAS_THROW);
      sb.append("(");
      sb.append(whatToThrow);
      sb.append(");");
    }
    return sb.toString();
  }

  /**
   * Don't generate aliases if the keyword is not referenced enough.
   */
  public void testDontAlias() {
    testSame(generateCode("true", TOO_FEW_TO_ALIAS_LITERAL));
    testSame(generateCode("false", TOO_FEW_TO_ALIAS_LITERAL));
    testSame(generateCode("null", TOO_FEW_TO_ALIAS_LITERAL));
    testSame(generatePreProcessThrowCode(TOO_FEW_TO_ALIAS_THROW, "1"));
  }

  /**
   * Generate aliases if the keyword is referenced >= ENOUGH_TO_ALIAS
   * times.
   */
  public void testAlias() {
    test(generateCode("true", ENOUGH_TO_ALIAS_LITERAL),
         generateCode(AliasKeywords.ALIAS_TRUE, ENOUGH_TO_ALIAS_LITERAL,
                      "var JSCompiler_alias_TRUE=true;"));

    test(generateCode("false", ENOUGH_TO_ALIAS_LITERAL),
         generateCode(AliasKeywords.ALIAS_FALSE, ENOUGH_TO_ALIAS_LITERAL,
                      "var JSCompiler_alias_FALSE=false;"));

    test(generateCode("null", ENOUGH_TO_ALIAS_LITERAL),
         generateCode(AliasKeywords.ALIAS_NULL, ENOUGH_TO_ALIAS_LITERAL,
                      "var JSCompiler_alias_NULL=null;"));
    test(generatePreProcessThrowCode(ENOUGH_TO_ALIAS_THROW, "1"),
         generatePostProcessThrowCode(ENOUGH_TO_ALIAS_THROW, "", "1"));
  }

  public void testAliasTrueFalseNull() {
    StringBuilder actual = new StringBuilder();
    actual.append(generateCode("true", ENOUGH_TO_ALIAS_LITERAL));
    actual.append(generateCode("false", ENOUGH_TO_ALIAS_LITERAL));
    actual.append(generateCode("null", ENOUGH_TO_ALIAS_LITERAL));

    StringBuilder expected = new StringBuilder();
    expected.append(
        "var JSCompiler_alias_TRUE=true;" +
        "var JSCompiler_alias_NULL=null;" +
        "var JSCompiler_alias_FALSE=false;");
    expected.append(
        generateCode(AliasKeywords.ALIAS_TRUE, ENOUGH_TO_ALIAS_LITERAL));
    expected.append(
        generateCode(AliasKeywords.ALIAS_FALSE, ENOUGH_TO_ALIAS_LITERAL));
    expected.append(
        generateCode(AliasKeywords.ALIAS_NULL, ENOUGH_TO_ALIAS_LITERAL));

    test(actual.toString(), expected.toString());
  }

  public void testAliasThrowKeywordLiteral() {
    int repitions = Math.max(ENOUGH_TO_ALIAS_THROW, ENOUGH_TO_ALIAS_LITERAL);
    String afterCode = generatePostProcessThrowCode(
          repitions, "var JSCompiler_alias_TRUE=true;",
          AliasKeywords.ALIAS_TRUE);
    test(generatePreProcessThrowCode(repitions, "true"), afterCode);
  }

  public void testExistingAliasDefinitionFails() {
    try {
      testSame("var JSCompiler_alias_TRUE='foo';");
      fail();
    } catch (RuntimeException expected) {
      assertTrue(-1 != expected.getMessage().indexOf(
              "Existing alias definition"));
      // expected
    }
  }

  public void testWithNoInputs() {
    testSame(new String[] {});
  }
}
