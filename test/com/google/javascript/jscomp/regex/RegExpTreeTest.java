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
import com.google.javascript.jscomp.regex.RegExpTree.BackReference;
import com.google.javascript.jscomp.regex.RegExpTree.CapturingGroup;
import com.google.javascript.jscomp.regex.RegExpTree.Charset;
import com.google.javascript.jscomp.regex.RegExpTree.Concatenation;
import com.google.javascript.jscomp.regex.RegExpTree.Empty;
import com.google.javascript.jscomp.regex.RegExpTree.LookbehindAssertion;
import com.google.javascript.jscomp.regex.RegExpTree.NamedBackReference;
import com.google.javascript.jscomp.regex.RegExpTree.NamedCaptureGroup;
import com.google.javascript.jscomp.regex.RegExpTree.Repetition;
import com.google.javascript.jscomp.regex.RegExpTree.Text;
import com.google.javascript.jscomp.regex.RegExpTree.UnicodePropertyEscape;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RegExpTreeTest {

  private RuntimeException exceptionFrom(String regex, String flags) {
    try {
      RegExpTree parsed = RegExpTree.parseRegExp(regex, flags);
      assertWithMessage("Expected exception, but none was thrown. Instead got back: %s", parsed)
          .fail();
      throw new AssertionError(); // unreachable
    } catch (RuntimeException thrownException) {
      return thrownException;
    }
  }

  private void assertRegexParsesTo(String regex, String flags, RegExpTree expected) {
    assertThat(RegExpTree.parseRegExp(regex, flags)).isEqualTo(expected);
  }

  private ThrowableSubject assertRegexThrowsExceptionThat(String regex, String flags) {
    return assertThat(exceptionFrom(regex, flags));
  }

  @Test
  public void testValidEs2018LookbehindAssertions() {
    assertRegexParsesTo("(?<=asdf)", "", new LookbehindAssertion(new Text("asdf"), true));
    assertRegexParsesTo("(?<!asdf)", "", new LookbehindAssertion(new Text("asdf"), false));
    assertRegexParsesTo(
        "(?<=(?<!asdf))",
        "",
        new LookbehindAssertion(new LookbehindAssertion(new Text("asdf"), false), true));
  }

  @Test
  public void testInvalidEs2018LookbehindAssertions() {
    assertRegexThrowsExceptionThat("(?<asdf)", "")
        .hasMessageThat()
        .isEqualTo("Invalid capture group name: <asdf)");
  }

  @Test
  public void testValidEs2018UnicodePropertyEscapes() {
    assertRegexParsesTo(
        "\\p{Script=Greek}", "u", new UnicodePropertyEscape("Script", "Greek", false));
    assertRegexParsesTo(
        "\\P{Script=Greek}", "u", new UnicodePropertyEscape("Script", "Greek", true));
    assertRegexParsesTo("\\p{Letter}", "u", new UnicodePropertyEscape(null, "Letter", false));
    assertRegexParsesTo("\\P{Letter}", "u", new UnicodePropertyEscape(null, "Letter", true));
    assertRegexParsesTo(
        "\\p{Script=Greek}", "v", new UnicodePropertyEscape("Script", "Greek", false));
    assertRegexParsesTo(
        "\\P{Script=Greek}", "v", new UnicodePropertyEscape("Script", "Greek", true));
    assertRegexParsesTo("\\p{RGI_Emoji}", "v", new UnicodePropertyEscape(null, "RGI_Emoji", false));
    assertRegexParsesTo(
        "\\p{Script=Greek}", "gv", new UnicodePropertyEscape("Script", "Greek", false));
    assertRegexParsesTo("\\p", "", new Text("p")); // Without 'u' or 'v' flag, '\p' is just 'p'
    assertRegexParsesTo("\\P", "", new Text("P"));
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
    assertRegexParsesTo("(?<name>)", "", new NamedCaptureGroup(Empty.INSTANCE, "name"));
    assertRegexParsesTo("(?<h$h1h_>)", "u", new NamedCaptureGroup(Empty.INSTANCE, "h$h1h_"));
    assertRegexParsesTo(
        "(?<$var_name>blah)", "", new NamedCaptureGroup(new Text("blah"), "$var_name"));
    assertRegexParsesTo(
        "(?<_var_name>>>>)", "", new NamedCaptureGroup(new Text(">>>"), "_var_name"));
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
    CapturingGroup nested =
        new CapturingGroup(new Concatenation(new Text("h"), new CapturingGroup(new Text("i"))));
    assertRegexParsesTo("(h(i))\\2", "", new Concatenation(nested, new BackReference(2)));
    // TODO(b/116048051): reference to non-existent capture group should be an error.
    assertRegexParsesTo("(h(i))\\3", "", new Concatenation(nested, new Text("\u0003")));

    assertRegexParsesTo(
        "(?<foo>.*(?<bar>))",
        "",
        new NamedCaptureGroup(
            new Concatenation(
                new Repetition(Charset.DOT, 0, Integer.MAX_VALUE, true),
                new NamedCaptureGroup(Empty.INSTANCE, "bar")),
            "foo"));
  }

  @Test
  public void testValidEs2018CaptureNameBackreferencing() {
    assertRegexParsesTo(
        "(?<name>)\\k<name>",
        "",
        new Concatenation(
            new NamedCaptureGroup(Empty.INSTANCE, "name"), new NamedBackReference("name")));
    assertRegexParsesTo(
        "(?<foo>(?<bar>))\\k<foo>\\k<bar>",
        "",
        new Concatenation(
            new Concatenation(
                new NamedCaptureGroup(new NamedCaptureGroup(Empty.INSTANCE, "bar"), "foo"),
                new NamedBackReference("foo")),
            new NamedBackReference("bar")));
    assertRegexParsesTo(
        "(?<foo>(?<bar>)\\k<bar>)\\k<foo>",
        "",
        new Concatenation(
            new NamedCaptureGroup(
                new Concatenation(
                    new NamedCaptureGroup(Empty.INSTANCE, "bar"), new NamedBackReference("bar")),
                "foo"),
            new NamedBackReference("foo")));

    // The below examples where the backreference comes before the definition of named groups is
    // allowed syntactically, although it is not able to reference the original group semantically
    assertRegexParsesTo(
        "\\k<foo>(?<foo>)",
        "",
        new Concatenation(
            new NamedBackReference("foo"), new NamedCaptureGroup(Empty.INSTANCE, "foo")));
    assertRegexParsesTo(
        "\\k<foo>(?<foo>\\k<bar>(?<bar>))",
        "",
        new Concatenation(
            new NamedBackReference("foo"),
            new NamedCaptureGroup(
                new Concatenation(
                    new NamedBackReference("bar"), new NamedCaptureGroup(Empty.INSTANCE, "bar")),
                "foo")));

    // Backreferencing the name in the group it is defined is also allowed
    assertRegexParsesTo(
        "(?<foo>\\k<foo>)", "", new NamedCaptureGroup(new NamedBackReference("foo"), "foo"));
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
    assertRegexParsesTo("\\k<foo>", "", new Concatenation(new Text("k"), new Text("<foo>")));
    assertRegexParsesTo(
        "\\k<.",
        "",
        new Concatenation(new Concatenation(new Text("k"), new Text("<")), Charset.DOT));

    // Even though enclosed in (?<>), 'foo' not a capture name definition
    assertRegexParsesTo(
        "[(?<foo>)]\\k<foo>",
        "",
        new Concatenation(
            new Concatenation(new Charset("[(?<foo>)]"), new Text("k")), new Text("<foo>")));
  }

  @Test
  public void testCharsets() {
    assertRegexParsesTo("[a-z]", "", new Charset("[a-z]"));
    assertRegexParsesTo("\\d", "", new Charset("\\d"));
    assertRegexThrowsExceptionThat("[z-a]", "").hasMessageThat().isEqualTo("122 > 97");
  }

  @Test
  public void testValidUnicodeEscape() {
    assertRegexParsesTo("\\u0061", "", new Text("a"));
    assertRegexParsesTo("\\u10b1", "u", new Text("\u10b1"));
    assertRegexParsesTo("\\u{61}", "u", new Text("a"));
    assertRegexParsesTo("\\u{10b1}", "u", new Text("\u10b1"));
    assertRegexParsesTo("\\u{1bc}", "u", new Text("\u01bc"));
    assertRegexParsesTo("\\u{100A3}", "u", new Text("\ud800\udca3"));
    assertRegexParsesTo("\\u{61}", "v", new Text("a"));
    assertRegexParsesTo("\\u{61}", "iv", new Text("a"));
    assertRegexParsesTo("\\u{100A3}", "v", new Text("\ud800\udca3"));
  }

  @Test
  public void testValidEs2024RegexVModeCharacterClasses() {
    // Set subtraction (--), intersection (&&), union, and nested character classes
    assertRegexParsesTo("[a--b]", "v", new Charset("[a--b]"));
    assertRegexParsesTo("[a--b]", "iv", new Charset("[a--b]"));
    assertRegexParsesTo("[\\w--[a-g]]", "gv", new Charset("[\\w--[a-g]]"));
    assertRegexParsesTo("[[a-z]--[aeiou]]", "v", new Charset("[[a-z]--[aeiou]]"));
    assertRegexParsesTo("[[a-z]--[aeiou]]", "iv", new Charset("[[a-z]--[aeiou]]"));
    assertRegexParsesTo(
        "[\\p{Decimal_Number}--[0-9]]", "v", new Charset("[\\p{Decimal_Number}--[0-9]]"));
    assertRegexParsesTo(
        "[\\p{Script=Greek}&&\\p{Letter}]", "v", new Charset("[\\p{Script=Greek}&&\\p{Letter}]"));
    assertRegexParsesTo("[[a-z]&&[^aeiou]]", "v", new Charset("[[a-z]&&[^aeiou]]"));
    assertRegexParsesTo("[[[a-z]--[aeiou]]&&[a-m]]", "v", new Charset("[[[a-z]--[aeiou]]&&[a-m]]"));

    // Empty and negated empty character classes
    assertRegexParsesTo("[]", "v", new Charset("[]"));
    assertRegexParsesTo("[^]", "v", new Charset("[^]"));
    assertRegexParsesTo("[^^]", "v", new Charset("[^^]"));

    // Isolated escape-before-bracket boundaries
    assertRegexParsesTo("[\\\\]", "v", new Charset("[\\\\]"));
    assertRegexParsesTo("[\\\\]a", "v", new Concatenation(new Charset("[\\\\]"), new Text("a")));
    assertRegexParsesTo("[\\[]", "v", new Charset("[\\[]"));
    assertRegexParsesTo("[\\]]", "v", new Charset("[\\]]"));

    // Supplementary code point ranges and escaped brackets/punctuators
    assertRegexParsesTo("[\\u{10000}-\\u{10005}]", "v", new Charset("[\\u{10000}-\\u{10005}]"));
    assertRegexParsesTo(
        "[\uD83D\uDE00-\uD83D\uDE4F]", "v", new Charset("[\uD83D\uDE00-\uD83D\uDE4F]"));
    assertRegexParsesTo(
        "[\\(\\)\\[\\]\\{\\}\\/\\-\\\\\\|]", "v", new Charset("[\\(\\)\\[\\]\\{\\}\\/\\-\\\\\\|]"));
    assertRegexParsesTo("[a\\&\\&b]", "v", new Charset("[a\\&\\&b]"));

    // Multi-character string disjunctions (\q{...}) and repetitions
    assertRegexParsesTo("[\\q{abc|def}]", "v", new Charset("[\\q{abc|def}]"));
    assertRegexParsesTo("[\\q{}]", "v", new Charset("[\\q{}]"));
    assertRegexParsesTo("[\\q{a\\[b\\]c\\\\}]", "v", new Charset("[\\q{a\\[b\\]c\\\\}]"));
    assertRegexParsesTo(
        "[[\\q{ab|cd}]--[\\q{cd}]]+",
        "v",
        new Repetition(new Charset("[[\\q{ab|cd}]--[\\q{cd}]]"), 1, Integer.MAX_VALUE, true));
    assertRegexParsesTo(
        "(?<word>[\\w--[0-9]]+)\\k<word>",
        "v",
        new Concatenation(
            new NamedCaptureGroup(
                new Repetition(new Charset("[\\w--[0-9]]"), 1, Integer.MAX_VALUE, true), "word"),
            new NamedBackReference("word")));
  }

  @Test
  public void testInvalidEs2024RegexVModeCharacterClasses() {
    // Unclosed character classes (including nested brackets and escaped closing brackets)
    assertRegexThrowsExceptionThat("[abc", "v")
        .hasMessageThat()
        .isEqualTo("Unclosed character class");
    assertRegexThrowsExceptionThat("[[a-z]", "v")
        .hasMessageThat()
        .isEqualTo("Unclosed character class");
    assertRegexThrowsExceptionThat("[[a-z]", "gv")
        .hasMessageThat()
        .isEqualTo("Unclosed character class");
    assertRegexThrowsExceptionThat("[[a-z]--[aeiou]", "v")
        .hasMessageThat()
        .isEqualTo("Unclosed character class");
    assertRegexThrowsExceptionThat("[a\\]", "v")
        .hasMessageThat()
        .isEqualTo("Unclosed character class");

    // Trailing backslash inside a character class
    assertRegexThrowsExceptionThat("[abc\\", "v")
        .hasMessageThat()
        .isEqualTo("Trailing '\\' in character class");

    // Top-level structural errors outside character classes are still caught
    assertRegexThrowsExceptionThat("([a--b]", "v")
        .hasMessageThat()
        .isEqualTo("Unclosed parenthetical group: ([a--b]");
    assertRegexThrowsExceptionThat("(?<foo>[a--b])\\k<bar>", "v")
        .hasMessageThat()
        .isEqualTo("Invalid named capture referenced: \\k<bar>");
    assertRegexThrowsExceptionThat("\\p{", "v")
        .hasMessageThat()
        .isEqualTo("Malformed Unicode Property Escape: expected '=' or '}' after \\p{");
    assertRegexThrowsExceptionThat("\\p{", "iv")
        .hasMessageThat()
        .isEqualTo("Malformed Unicode Property Escape: expected '=' or '}' after \\p{");
  }

  @Test
  public void testInvalidUnicodeEscape() {
    for (String flags : new String[] {"u", "v", "iv"}) {
      assertRegexThrowsExceptionThat("\\u{a012", flags)
          .hasMessageThat()
          .isEqualTo("Malformed unicode escape: expected '}' after {a012");
      assertRegexThrowsExceptionThat("\\u{}", flags) //
          .hasMessageThat()
          .isEqualTo("Empty unicode escape");
      assertRegexThrowsExceptionThat("\\u{10za}", flags) //
          .hasMessageThat()
          .isEqualTo("za}");
      assertRegexThrowsExceptionThat("\\u{FFFFFF}", flags)
          .hasMessageThat()
          .isEqualTo("Unicode must be at most 0x10FFFF: FFFFFF");
      assertRegexThrowsExceptionThat("\\u{FF00FFFF}", flags)
          .hasMessageThat()
          .isEqualTo("Cannot parse hexadecimal encoding wider than 28 bits: FF00FFFF");
    }
  }
}
