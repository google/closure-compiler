/*
 * Copyright 2014 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.regex;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.truth.ThrowableSubject;
import junit.framework.TestCase;

public class RegExpTreeTest extends TestCase {

  private String parseRegExpAndPrintPattern(String regex, String flags) {
    RegExpTree tree = RegExpTree.parseRegExp(regex, flags);
    StringBuilder sb = new StringBuilder();
    tree.appendSourceCode(sb);
    return sb.toString();
  }

  private RuntimeException exceptionFrom(String regex, String flags) {
    try {
      String printed = parseRegExpAndPrintPattern(regex, flags);
      fail("Expected exception, but none was thrown. Instead got back: " + printed);
      throw new AssertionError(); // unreachable
    } catch (RuntimeException thrownException) {
      return thrownException;
    }
  }

  private void assertRegexCompilesTo(String regex, String flags, String expected) {
    assertEquals(expected, parseRegExpAndPrintPattern(regex, flags));
  }

  private ThrowableSubject assertRegexThrowsExceptionThat(String regex, String flags) {
    return assertThat(exceptionFrom(regex, flags));
  }

  private void assertRegexCompilesToSame(String regex, String flags) {
    assertRegexCompilesTo(regex, flags, regex);
  }

  public void testValidEs2018LookbehindAssertions() {
    assertRegexCompilesToSame("(?<=asdf)", "");
    assertRegexCompilesToSame("(?<!asdf)", "");
    assertRegexCompilesToSame("(?<=(?<!asdf))", "");
    assertRegexCompilesToSame("(?<=(?<!asdf))", "");
  }

  public void testInvalidEs2018LookbehindAssertions() {
    assertRegexThrowsExceptionThat("(?<asdf)", "")
        .hasMessageThat()
        .isEqualTo("Malformed named capture group: (?<asdf)");
  }

  public void testValidEs2018UnicodePropertyEscapes() {
    assertRegexCompilesToSame("\\p{Script=Greek}", "u");
    assertRegexCompilesToSame("\\P{Script=Greek}", "u");
    assertRegexCompilesToSame("\\p{Letter}", "u");
    assertRegexCompilesToSame("\\P{Letter}", "u");
    assertRegexCompilesTo("\\p", "", "p"); // Without 'u' flag, '\p' is just 'p'
    assertRegexCompilesTo("\\P", "", "P");
  }

  public void testInvalidEs2018UnicodePropertyEscapes() {
    assertRegexThrowsExceptionThat("\\p{", "u")
        .hasMessageThat()
        .isEqualTo("Malformed Unicode Property Escape: expected '=' or '}' after \\p{");

    assertRegexThrowsExceptionThat("\\P{", "u")
        .hasMessageThat()
        .isEqualTo("Malformed Unicode Property Escape: expected '=' or '}' after \\P{");

    assertRegexThrowsExceptionThat("\\p{=Greek}", "u")
        .hasMessageThat()
        .isEqualTo("if '=' is present in a unicode property escape, the name cannot be empty");

    assertRegexThrowsExceptionThat("\\P{=Greek}", "u")
        .hasMessageThat()
        .isEqualTo("if '=' is present in a unicode property escape, the name cannot be empty");

    assertRegexThrowsExceptionThat("\\p{}", "u")
        .hasMessageThat()
        .isEqualTo("unicode property escape value cannot be empty");

    assertRegexThrowsExceptionThat("\\P{}", "u")
        .hasMessageThat()
        .isEqualTo("unicode property escape value cannot be empty");
  }

  public void testValidEs2018RegexNamedCaptureGroups() {
    assertRegexCompilesToSame("(?<name>)", "");
    assertRegexCompilesToSame("(?<h$h1h_>)", "u");
    assertRegexCompilesToSame("(?<$var_name>blah)", "");
    assertRegexCompilesToSame("(?<_var_name>>>>)", "");
  }

  public void testInvalidEs2018RegexNamedCaptureGroups() {
    assertRegexThrowsExceptionThat("(?<name)", "")
        .hasMessageThat()
        .isEqualTo("Malformed named capture group: (?<name)");
    assertRegexThrowsExceptionThat("(?<1b>)", "")
        .hasMessageThat()
        .isEqualTo("Malformed named capture group: (?<1b>)");
    assertRegexThrowsExceptionThat("(?<>)", "")
        .hasMessageThat()
        .isEqualTo("Malformed named capture group: (?<>)");
    assertRegexThrowsExceptionThat("(?<.name>)", "")
        .hasMessageThat()
        .isEqualTo("Malformed named capture group: (?<.name>)");
  }

  public void testNumCapturingGroups() {
    assertRegexCompilesToSame("(h(i))\\2", "");
    // TODO(b/116048051): reference to non-existent capture group should be an error.
    assertRegexCompilesTo("(h(i))\\3", "", "(h(i))\\x03");

    assertRegexCompilesToSame("(?<foo>.*(?<bar>))", "");
  }
}
