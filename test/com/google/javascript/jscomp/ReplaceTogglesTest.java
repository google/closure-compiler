/*
 * Copyright 2023 The Closure Compiler Authors.
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
import static com.google.javascript.jscomp.ReplaceToggles.FALSE_VALUE;
import static com.google.javascript.jscomp.ReplaceToggles.TRUE_VALUE;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ReplaceToggles}. */
@RunWith(JUnit4.class)
public final class ReplaceTogglesTest extends CompilerTestCase {

  private ImmutableMap<String, Integer> toggles;
  private boolean check;

  @Before
  public void resetPassParameters() throws Exception {
    toggles = null;
    check = false;
  }

  @Test
  public void testBootstrapOrdinals_uninitialized() {
    check = true;
    testSame(srcs("var _F_toggleOrdinals;"), expectOrdinals(null));
  }

  @Test
  public void testBootstrapOrdinals_empty() {
    check = true;
    testSame(srcs("var _F_toggleOrdinals = {};"), expectOrdinals(ImmutableMap.of()));
  }

  @Test
  public void testBootstrapOrdinals_simple() {
    check = true;
    testSame(
        srcs("var _F_toggleOrdinals = {'foo_bar': 1};"),
        expectOrdinals(ImmutableMap.of("foo_bar", 1)));
    testSame(
        srcs("var _F_toggleOrdinals = {'foo_bar': 1, qux: 2};"),
        expectOrdinals(ImmutableMap.of("foo_bar", 1, "qux", 2)));
  }

  @Test
  public void testBootstrapOrdinals_ignoresExtraUninitializedDefinitions() {
    check = true;
    testSame(
        srcs(
            lines(
                "var _F_toggleOrdinals = {'foo': 1};", //
                "var _F_toggleOrdinals;")),
        expectOrdinals(ImmutableMap.of("foo", 1)));
    testSame(
        srcs(
            lines(
                "var _F_toggleOrdinals;", //
                "var _F_toggleOrdinals = {'foo': 1};")),
        expectOrdinals(ImmutableMap.of("foo", 1)));
  }

  @Test
  public void testBootstrapOrdinals_booleanAllowed() {
    check = true;
    testSame(
        srcs("var _F_toggleOrdinals = {'foo_bar': false, 'baz': true};"),
        expectOrdinals(ImmutableMap.of("foo_bar", FALSE_VALUE, "baz", TRUE_VALUE)));
  }

  @Test
  public void testBootstrapOrdinals_booleanAllowsDuplicates() {
    check = true;
    testSame(
        srcs("var _F_toggleOrdinals = {'foo_bar': false, 'baz': false, 'qux': 0};"),
        expectOrdinals(ImmutableMap.of("foo_bar", FALSE_VALUE, "baz", FALSE_VALUE, "qux", 0)));
    testSame(
        srcs("var _F_toggleOrdinals = {'foo_bar': true, 'baz': true, 'qux': 0};"),
        expectOrdinals(ImmutableMap.of("foo_bar", TRUE_VALUE, "baz", TRUE_VALUE, "qux", 0)));
  }

  @Test
  public void testBootstrapOrdinals_notAnObject() {
    check = true;
    test(
        srcs("var _F_toggleOrdinals = 1;"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("not an object literal"));
    test(
        srcs("var _F_toggleOrdinals = [];"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("not an object literal"));
  }

  @Test
  public void testBootstrapOrdinals_badKey() {
    check = true;
    test(
        srcs("var _F_toggleOrdinals = {[x]: 1};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING).withMessageContaining("non-string key"));
    test(
        srcs("var _F_toggleOrdinals = {['x']: 1};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING).withMessageContaining("non-string key"));
    test(
        srcs("var _F_toggleOrdinals = {x() { return 1; }};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING).withMessageContaining("non-string key"));
  }

  @Test
  public void testBootstrapOrdinals_duplicateKey() {
    check = true;
    test(
        srcs("var _F_toggleOrdinals = {x: 1, y: 2, x: 3};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING).withMessageContaining("duplicate key: x"));
  }

  @Test
  public void testBootstrapOrdinals_badValue() {
    check = true;
    test(
        srcs("var _F_toggleOrdinals = {x: 'y'};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("value not a boolean or whole number literal"));
    test(
        srcs("var _F_toggleOrdinals = {x};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("value not a boolean or whole number literal"));
    test(
        srcs("var _F_toggleOrdinals = {x: 1 + 2};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("value not a boolean or whole number literal"));
    test(
        srcs("var _F_toggleOrdinals = {x: NaN};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("value not a boolean or whole number literal"));
    test(
        srcs("var _F_toggleOrdinals = {x: Infinity};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("value not a boolean or whole number literal"));
    test(
        srcs("var _F_toggleOrdinals = {x: -1};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("value not a boolean or whole number literal"));
    test(
        srcs("var _F_toggleOrdinals = {x: 1.2};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("value not a boolean or whole number literal"));
    test(
        srcs("var _F_toggleOrdinals = {x: null};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("value not a boolean or whole number literal"));
  }

  @Test
  public void testBootstrapOrdinals_duplicateOrdinal() {
    check = true;
    test(
        srcs("var _F_toggleOrdinals = {x: 1, y: 2, z: 1};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("duplicate ordinal: 1"));
  }

  @Test
  public void testNoTogglesGiven() {
    test(
        "const foo = goog.readToggleInternalDoNotCallDirectly('foo');", //
        "const foo = false;");
  }

  @Test
  public void testSimpleToggles() {
    toggles = ImmutableMap.of("foo", 1, "bar", 30, "baz", 59);
    test(
        lines(
            "const foo = goog.readToggleInternalDoNotCallDirectly('foo');",
            "const bar = goog.readToggleInternalDoNotCallDirectly('bar');",
            "const baz = goog.readToggleInternalDoNotCallDirectly('baz');"),
        lines(
            "const foo = !!(goog.TOGGLES_[0] & 2);",
            "const bar = !!(goog.TOGGLES_[1] & 1);",
            "const baz = !!(goog.TOGGLES_[1] >> 29 & 1);"));
  }

  @Test
  public void testUnsetToggles() {
    toggles = ImmutableMap.of("foo", FALSE_VALUE, "bar", TRUE_VALUE);
    test(
        lines(
            "const foo = goog.readToggleInternalDoNotCallDirectly('foo');",
            "const bar = goog.readToggleInternalDoNotCallDirectly('bar');"),
        lines("const foo = false;", "const bar = true;"));
  }

  @Test
  public void testCheckMode() {
    check = true;
    testSame("const foo = goog.readToggleInternalDoNotCallDirectly('foo');");
  }

  @Test
  public void testUnknownToggle() {
    check = true;
    toggles = ImmutableMap.of("foo", 0);
    testError(
        "const bar = goog.readToggleInternalDoNotCallDirectly('bar');", //
        ReplaceToggles.UNKNOWN_TOGGLE);
  }

  @Test
  public void testBadArgument() {
    check = true;
    testError(
        lines(
            "const fooString = 'foo';", //
            "const foo = goog.readToggleInternalDoNotCallDirectly(fooString);"),
        ReplaceToggles.INVALID_TOGGLE_PARAMETER);
    testError(
        "const foo = goog.readToggleInternalDoNotCallDirectly('foo', 'bar');", //
        ReplaceToggles.INVALID_TOGGLE_PARAMETER);
    testError(
        "const foo = goog.readToggleInternalDoNotCallDirectly();", //
        ReplaceToggles.INVALID_TOGGLE_PARAMETER);
  }

  private Postcondition expectOrdinals(ImmutableMap<String, Integer> expected) {
    return (Compiler c) -> assertThat(c.getToggleOrdinalMapping()).isEqualTo(expected);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    compiler.setToggleOrdinalMapping(toggles);
    return new ReplaceToggles(compiler, check);
  }
}
