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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ReplaceToggles}. */
@RunWith(JUnit4.class)
public final class ReplaceTogglesTest extends CompilerTestCase {

  @Test
  public void testBootstrapOrdinals_notAnObject() {
    test(
        srcs("var CLOSURE_TOGGLE_ORDINALS = 1;"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("not an object literal"));
    test(
        srcs("var CLOSURE_TOGGLE_ORDINALS = [];"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("not an object literal"));
  }

  @Test
  public void testBootstrapOrdinals_badKey() {
    test(
        srcs("var CLOSURE_TOGGLE_ORDINALS = {[x]: 1};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING).withMessageContaining("non-string key"));
    test(
        srcs("var CLOSURE_TOGGLE_ORDINALS = {['x']: 1};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING).withMessageContaining("non-string key"));
    test(
        srcs("var CLOSURE_TOGGLE_ORDINALS = {x() { return 1; }};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING).withMessageContaining("non-string key"));
  }

  @Test
  public void testBootstrapOrdinals_duplicateKey() {
    test(
        srcs("var CLOSURE_TOGGLE_ORDINALS = {x: 1, y: 2, x: 3};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING).withMessageContaining("duplicate key: x"));
  }

  @Test
  public void testBootstrapOrdinals_badValue() {
    test(
        srcs("var CLOSURE_TOGGLE_ORDINALS = {x: 'y'};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("value not a boolean or whole number literal"));
    test(
        srcs("var CLOSURE_TOGGLE_ORDINALS = {x};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("value not a boolean or whole number literal"));
    test(
        srcs("var CLOSURE_TOGGLE_ORDINALS = {x: 1 + 2};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("value not a boolean or whole number literal"));
    test(
        srcs("var CLOSURE_TOGGLE_ORDINALS = {x: NaN};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("value not a boolean or whole number literal"));
    test(
        srcs("var CLOSURE_TOGGLE_ORDINALS = {x: Infinity};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("value not a boolean or whole number literal"));
    test(
        srcs("var CLOSURE_TOGGLE_ORDINALS = {x: -1};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("value not a boolean or whole number literal"));
    test(
        srcs("var CLOSURE_TOGGLE_ORDINALS = {x: 1.2};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("value not a boolean or whole number literal"));
    test(
        srcs("var CLOSURE_TOGGLE_ORDINALS = {x: null};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("value not a boolean or whole number literal"));
  }

  @Test
  public void testBootstrapOrdinals_duplicateOrdinal() {
    test(
        srcs("var CLOSURE_TOGGLE_ORDINALS = {x: 1, y: 2, z: 1};"),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("duplicate ordinal: 1"));
  }

  @Test
  public void testBootstrapOrdinals_twoInitializers() {
    test(
        srcs(
            lines(
                "var CLOSURE_TOGGLE_ORDINALS = {x: 1, y: 2};", //
                "var CLOSURE_TOGGLE_ORDINALS = {x: 1, y: 2};")),
        error(ReplaceToggles.INVALID_ORDINAL_MAPPING)
            .withMessageContaining("multiple initialized copies"));
  }

  @Test
  public void testNoTogglesGiven() {
    test(
        "const foo = goog.readToggleInternalDoNotCallDirectly('foo');", //
        "const foo = false;");
  }

  @Test
  public void testSimpleToggles() {
    test(
        lines(
            "var CLOSURE_TOGGLE_ORDINALS = {'foo': 1, 'bar': 30, 'baz': 59};",
            "const foo = goog.readToggleInternalDoNotCallDirectly('foo');",
            "const bar = goog.readToggleInternalDoNotCallDirectly('bar');",
            "const baz = goog.readToggleInternalDoNotCallDirectly('baz');"),
        lines(
            "var CLOSURE_TOGGLE_ORDINALS = {'foo': 1, 'bar': 30, 'baz': 59};",
            "const foo = !!(goog.TOGGLES_[0] & 2);",
            "const bar = !!(goog.TOGGLES_[1] & 1);",
            "const baz = !!(goog.TOGGLES_[1] >> 29 & 1);"));
  }

  @Test
  public void testHardcodedToggles() {
    test(
        lines(
            "var CLOSURE_TOGGLE_ORDINALS = {'foo': false, 'bar': true};",
            "const foo = goog.readToggleInternalDoNotCallDirectly('foo');",
            "const bar = goog.readToggleInternalDoNotCallDirectly('bar');"),
        lines(
            "var CLOSURE_TOGGLE_ORDINALS = {'foo': false, 'bar': true};",
            "const foo = false;",
            "const bar = true;"));
  }

  @Test
  public void testBootstrapOrdinals_ignoresExtraUninitializedDefinitions() {
    test(
        lines(
            "var CLOSURE_TOGGLE_ORDINALS;", // ignored
            "var CLOSURE_TOGGLE_ORDINALS = {'foo': 1, 'bar': 30, 'baz': 59};",
            "var CLOSURE_TOGGLE_ORDINALS;", // ignored
            "const foo = goog.readToggleInternalDoNotCallDirectly('foo');",
            "const bar = goog.readToggleInternalDoNotCallDirectly('bar');",
            "const baz = goog.readToggleInternalDoNotCallDirectly('baz');"),
        lines(
            "var CLOSURE_TOGGLE_ORDINALS;",
            "var CLOSURE_TOGGLE_ORDINALS = {'foo': 1, 'bar': 30, 'baz': 59};",
            "var CLOSURE_TOGGLE_ORDINALS;",
            "const foo = !!(goog.TOGGLES_[0] & 2);",
            "const bar = !!(goog.TOGGLES_[1] & 1);",
            "const baz = !!(goog.TOGGLES_[1] >> 29 & 1);"));
  }

  @Test
  public void testBootstrapOrdinals_booleanAllowsDuplicates() {
    test(
        lines(
            "var CLOSURE_TOGGLE_ORDINALS = ",
            "    {'foo_bar': false, 'baz': false, 'qux': true, 'corge': true};",
            "const fooBar = goog.readToggleInternalDoNotCallDirectly('foo_bar');",
            "const baz = goog.readToggleInternalDoNotCallDirectly('baz');",
            "const qux = goog.readToggleInternalDoNotCallDirectly('qux');",
            "const corge = goog.readToggleInternalDoNotCallDirectly('corge');"),
        lines(
            "var CLOSURE_TOGGLE_ORDINALS = ",
            "    {'foo_bar': false, 'baz': false, 'qux': true, 'corge': true};",
            "const fooBar = false;",
            "const baz = false;",
            "const qux = true;",
            "const corge = true;"));
  }

  @Test
  public void testUnknownToggle() {
    test(
        lines(
            "var CLOSURE_TOGGLE_ORDINALS = {'foo': 0};",
            "const bar = goog.readToggleInternalDoNotCallDirectly('bar');"),
        lines(
            "var CLOSURE_TOGGLE_ORDINALS = {'foo': 0};", //
            "const bar = false;"));
  }

  @Test
  public void testBadArgument() {
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

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new ReplaceToggles(compiler);
  }
}
