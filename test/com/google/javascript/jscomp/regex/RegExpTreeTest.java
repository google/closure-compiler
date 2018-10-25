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
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.truth.ThrowableSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RegExpTreeTest {

  private String parseRegExpAndPrintPattern(String regex, String flags) {
    RegExpTree tree = RegExpTree.parseRegExp(regex, flags);
    StringBuilder sb = new StringBuilder();
    tree.appendSourceCode(sb);
    return sb.toString();
  }

  private RuntimeException exceptionFrom(String regex, String flags) {
    try {
      String printed = parseRegExpAndPrintPattern(regex, flags);
      assertWithMessage("Expected exception, but none was thrown. Instead got back: " + printed)
          .fail();
      throw new AssertionError(); // unreachable
    } catch (RuntimeException thrownException) {
      return thrownException;
    }
  }

  private void assertRegexCompilesTo(String regex, String flags, String expected) {
    assertThat(parseRegExpAndPrintPattern(regex, flags)).isEqualTo(expected);
  }

  private ThrowableSubject assertRegexThrowsExceptionThat(String regex, String flags) {
    return assertThat(exceptionFrom(regex, flags));
  }

  private void assertRegexCompilesToSame(String regex, String flags) {
    assertRegexCompilesTo(regex, flags, regex);
  }

  @Test
  public void testValidEs2018LookbehindAssertions() {
    assertRegexCompilesToSame("(?<=asdf)", "");
    assertRegexCompilesToSame("(?<!asdf)", "");
    assertRegexCompilesToSame("(?<=(?<!asdf))", "");
    assertRegexCompilesToSame("(?<=(?<!asdf))", "");
  }

  @Test
  public void testInvalidEs2018LookbehindAssertions() {
    assertRegexThrowsExceptionThat("(?<asdf)", "")
        .hasMessageThat()
        .isEqualTo("Invalid capture group name: <asdf)");
  }

  @Test
  public void testValidEs2018UnicodePropertyEscapes() {
    assertRegexCompilesToSame("\\p{Script=Greek}", "u");
    assertRegexCompilesToSame("\\P{Script=Greek}", "u");
    assertRegexCompilesToSame("\\p{Letter}", "u");
    assertRegexCompilesToSame("\\P{Letter}", "u");
    assertRegexCompilesTo("\\p", "", "p"); // Without 'u' flag, '\p' is just 'p'
    assertRegexCompilesTo("\\P", "", "P");
  }

  @Test
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

  @Test
  public void testValidEs2018RegexNamedCaptureGroups() {
    assertRegexCompilesToSame("(?<name>)", "");
    assertRegexCompilesToSame("(?<h$h1h_>)", "u");
    assertRegexCompilesToSame("(?<$var_name>blah)", "");
    assertRegexCompilesToSame("(?<_var_name>>>>)", "");
  }

  @Test
  public void testInvalidEs2018RegexNamedCaptureGroups() {
    assertRegexThrowsExceptionThat("(?<name)", "")
        .hasMessageThat()
        .isEqualTo("Invalid capture group name: <name)");
    assertRegexThrowsExceptionThat("(?<1b>)", "")
        .hasMessageThat()
        .isEqualTo("Invalid capture group name: <1b>)");
    assertRegexThrowsExceptionThat("(?<>)", "")
        .hasMessageThat()
        .isEqualTo("Invalid capture group name: <>)");
    assertRegexThrowsExceptionThat("(?<.name>)", "")
        .hasMessageThat()
        .isEqualTo("Invalid capture group name: <.name>)");
  }

  @Test
  public void testNumCapturingGroups() {
    assertRegexCompilesToSame("(h(i))\\2", "");
    // TODO(b/116048051): reference to non-existent capture group should be an error.
    assertRegexCompilesTo("(h(i))\\3", "", "(h(i))\\x03");

    assertRegexCompilesToSame("(?<foo>.*(?<bar>))", "");
  }

  @Test
  public void testValidEs2018CaptureNameBackreferencing() {
    assertRegexCompilesToSame("(?<name>)\\k<name>", "");
    // Note that (?: ) only used for printing purposes to
    // indicate the nesting structure of Concatenation nodes.
    // It is not actually what the compiler prints out as source code.
    assertRegexCompilesTo(
        "(?<foo>(?<bar>))\\k<foo>\\k<bar>", "", "(?:(?<foo>(?<bar>))\\k<foo>)\\k<bar>");
    assertRegexCompilesTo(
        "(?<foo>(?<bar>)\\k<bar>)\\k<foo>", "", "(?<foo>(?<bar>)\\k<bar>)\\k<foo>");

    // The below examples where the backreference comes before the definition of named groups is
    // allowed syntactically, although it is not able to reference the original group semantically
    assertRegexCompilesToSame("\\k<foo>(?<foo>)", "");
    assertRegexCompilesToSame("\\k<foo>(?<foo>\\k<bar>(?<bar>))", "");

    // Backreferencing the name in the group it is defined is also allowed
    assertRegexCompilesToSame("(?<foo>\\k<foo>)", "");
  }

  @Test
  public void testInvalidEs2018CaptureNameBackreferencing() {
    assertRegexThrowsExceptionThat("(?<foo>)\\k<bar>", "")
        .hasMessageThat()
        .isEqualTo("Invalid named capture referenced: \\k<bar>");
    assertRegexThrowsExceptionThat("(?<foo>)\\k<foo", "")
        .hasMessageThat()
        .isEqualTo("Malformed named capture group: <foo");
    assertRegexThrowsExceptionThat("\\k<1b>(?<foo>)", "")
        .hasMessageThat()
        .isEqualTo("Invalid capture group name: <1b>(?<foo>)");

    // Even though enclosed in (?<>), 'foo' not a capture name definition
    assertRegexThrowsExceptionThat("[(?<foo>)](?<bar>)\\k<foo>", "")
        .hasMessageThat()
        .isEqualTo("Invalid named capture referenced: \\k<foo>");
  }

  @Test
  public void testBackreferencingTreatedAsStringIfNoGroup() {
    // Backreferencing without named group definitions is just treated as normal string
    assertRegexCompilesTo("\\k<foo>", "", "k<foo>");

    // Note that the (?: ) that is wrapped around "k<" at expected output is only used for printing
    // purposes to indicate the nesting structure of Concatenation nodes.
    // It is not actually what the compiler prints out as source code.
    assertRegexCompilesTo("\\k<.", "", "(?:k<).");

    // Even though enclosed in (?<>), 'foo' not a capture name definition
    // (?: ) in expected output serves same purpose as above test
    assertRegexCompilesTo("[(?<foo>)]\\k<foo>", "", "(?:[()<>?fo]k)<foo>");
  }
}
