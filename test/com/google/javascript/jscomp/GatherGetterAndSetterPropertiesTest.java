/*
 * Copyright 2018 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.AccessorSummary.PropertyAccessKind;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link GatherGetterAndSetterProperties}. */
@RunWith(JUnit4.class)
public class GatherGetterAndSetterPropertiesTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new GatherGetterAndSetterProperties(compiler);
  }

  @Test
  public void defaultToNormal() {
    testSame("");
    assertThat(getLastAccessorSummary().getKind("dne")).isEqualTo(PropertyAccessKind.NORMAL);
  }

  @Test
  public void findGettersAndSettersInExterns() {
    testSame(externs("({get x() {}, set y(v) {}})"), srcs(""));

    assertThat(getLastCompiler().getAccessorSummary().getAccessors())
        .containsExactlyEntriesIn(
            ImmutableMap.of(
                "x", PropertyAccessKind.GETTER_ONLY, //
                "y", PropertyAccessKind.SETTER_ONLY));
  }

  @Test
  public void findGettersAndSettersInSources() {
    testSame(srcs("({get x() {}, set y(v) {}})"));

    assertThat(getLastCompiler().getAccessorSummary().getAccessors())
        .containsExactlyEntriesIn(
            ImmutableMap.of(
                "x", PropertyAccessKind.GETTER_ONLY, //
                "y", PropertyAccessKind.SETTER_ONLY));
  }

  @Test
  public void mergeInExterns() {
    testSame(externs("({get x() {}}); ({set x(v) {}});"), srcs(""));
    assertThat(getLastAccessorSummary().getKind("x"))
        .isEqualTo(PropertyAccessKind.GETTER_AND_SETTER);
  }

  @Test
  public void mergeInSrcs() {
    testSame(srcs("({get x() {}}); ({set x(v) {}});"));
    assertThat(getLastAccessorSummary().getKind("x"))
        .isEqualTo(PropertyAccessKind.GETTER_AND_SETTER);
  }

  @Test
  public void mergeExtersAndSrcs() {
    testSame(externs("({get x() {}})"), srcs("({set x(v) {}})"));
    assertThat(getLastAccessorSummary().getKind("x"))
        .isEqualTo(PropertyAccessKind.GETTER_AND_SETTER);
  }

  @Test
  public void detectObjectLiteral() {
    testSame(srcs("var a = { set x(v) {} };"));
    assertThat(getLastAccessorSummary().getKind("x")).isEqualTo(PropertyAccessKind.SETTER_ONLY);

    testSame(srcs("var a = { get x() {} };"));
    assertThat(getLastAccessorSummary().getKind("x")).isEqualTo(PropertyAccessKind.GETTER_ONLY);
  }

  @Test
  public void detectClass() {
    testSame(srcs("class Class { set x(v) {} };"));
    assertThat(getLastAccessorSummary().getKind("x")).isEqualTo(PropertyAccessKind.SETTER_ONLY);

    testSame(srcs("class Class { get x() {} };"));
    assertThat(getLastAccessorSummary().getKind("x")).isEqualTo(PropertyAccessKind.GETTER_ONLY);
  }

  @Test
  public void detectClassStatic() {
    testSame(srcs("class Class { static set x(v) {} };"));
    assertThat(getLastAccessorSummary().getKind("x")).isEqualTo(PropertyAccessKind.SETTER_ONLY);

    testSame(srcs("class Class { static get x() {} };"));
    assertThat(getLastAccessorSummary().getKind("x")).isEqualTo(PropertyAccessKind.GETTER_ONLY);
  }

  @Test
  public void detectDefinePropertyQuotedKeys() {
    testSame(
        lines(
            "Object.defineProperty(something, 'prop', {", //
            "    'get': function() {},",
            "});"));

    assertThat(getLastAccessorSummary().getKind("prop")).isEqualTo(PropertyAccessKind.GETTER_ONLY);

    testSame(
        lines(
            "Object.defineProperty(something, 'prop', {", //
            "    'set': function(v) {}",
            "});"));

    assertThat(getLastAccessorSummary().getKind("prop")).isEqualTo(PropertyAccessKind.SETTER_ONLY);

    testSame(
        lines(
            "Object.defineProperty(something, 'prop', {",
            "    'get': function() {},",
            "    'set': function(v) {}",
            "});"));

    assertThat(getLastAccessorSummary().getKind("prop"))
        .isEqualTo(PropertyAccessKind.GETTER_AND_SETTER);
  }

  @Test
  public void detectDefinePropertyUnquotedKeys() {
    testSame(
        lines(
            "Object.defineProperty(something, 'prop', {", //
            "    get: function() {},",
            "});"));

    assertThat(getLastAccessorSummary().getKind("prop")).isEqualTo(PropertyAccessKind.GETTER_ONLY);

    testSame(
        lines(
            "Object.defineProperty(something, 'prop', {", //
            "    set: function(v) {}",
            "});"));

    assertThat(getLastAccessorSummary().getKind("prop")).isEqualTo(PropertyAccessKind.SETTER_ONLY);

    testSame(
        lines(
            "Object.defineProperty(something, 'prop', {",
            "    get: function() {},",
            "    set: function(v) {}",
            "});"));

    assertThat(getLastAccessorSummary().getKind("prop"))
        .isEqualTo(PropertyAccessKind.GETTER_AND_SETTER);
  }

  @Test
  public void detectDefinePropertyMemberFunctions() {
    testSame(
        lines(
            "Object.defineProperty(something, 'prop', {", //
            "    get() {},",
            "});"));

    assertThat(getLastAccessorSummary().getKind("prop")).isEqualTo(PropertyAccessKind.GETTER_ONLY);

    testSame(
        lines(
            "Object.defineProperty(something, 'prop', {", //
            "    set(v) {}",
            "});"));

    assertThat(getLastAccessorSummary().getKind("prop")).isEqualTo(PropertyAccessKind.SETTER_ONLY);

    testSame(
        lines(
            "Object.defineProperty(something, 'prop', {", //
            "    get() {},",
            "    set(v) {}",
            "});"));

    assertThat(getLastAccessorSummary().getKind("prop"))
        .isEqualTo(PropertyAccessKind.GETTER_AND_SETTER);
  }

  @Test
  public void detectDefinePropertiesQuotedKeys() {
    testSame(
        lines(
            "Object.defineProperties(something, {",
            "  'prop': {",
            "    'get': function() {},",
            "  }",
            "});"));

    assertThat(getLastAccessorSummary().getKind("prop")).isEqualTo(PropertyAccessKind.GETTER_ONLY);

    testSame(
        lines(
            "Object.defineProperties(something, {",
            "  'prop': {",
            "    'set': function(v) {},",
            "  }",
            "});"));

    assertThat(getLastAccessorSummary().getKind("prop")).isEqualTo(PropertyAccessKind.SETTER_ONLY);

    testSame(
        lines(
            "Object.defineProperties(something, {",
            "  'prop': {",
            "    'get': function() {},",
            "    'set': function(v) {},",
            "  }",
            "});"));

    assertThat(getLastAccessorSummary().getKind("prop"))
        .isEqualTo(PropertyAccessKind.GETTER_AND_SETTER);
  }

  @Test
  public void detectDefinePropertiesUnquotedKeys() {
    testSame(
        lines(
            "Object.defineProperties(something, {",
            "  prop: {",
            "    get: function() {},",
            "  }",
            "});"));

    assertThat(getLastAccessorSummary().getKind("prop")).isEqualTo(PropertyAccessKind.GETTER_ONLY);

    testSame(
        lines(
            "Object.defineProperties(something, {",
            "  prop: {",
            "    set: function(v) {},",
            "  }",
            "});"));

    assertThat(getLastAccessorSummary().getKind("prop")).isEqualTo(PropertyAccessKind.SETTER_ONLY);

    testSame(
        lines(
            "Object.defineProperties(something, {",
            "  prop: {",
            "    get: function() {},",
            "    set: function(v) {},",
            "  }",
            "});"));

    assertThat(getLastAccessorSummary().getKind("prop"))
        .isEqualTo(PropertyAccessKind.GETTER_AND_SETTER);
  }

  @Test
  public void detectDefinePropertiesMemberFunctions() {
    testSame(
        lines(
            "Object.defineProperties(something, {", //
            "  prop: {",
            "    get() {},",
            "  }",
            "});"));

    assertThat(getLastAccessorSummary().getKind("prop")).isEqualTo(PropertyAccessKind.GETTER_ONLY);

    testSame(
        lines(
            "Object.defineProperties(something, {", //
            "  'prop': {",
            "    set(v) {},",
            "  }",
            "});"));

    assertThat(getLastAccessorSummary().getKind("prop")).isEqualTo(PropertyAccessKind.SETTER_ONLY);

    testSame(
        lines(
            "Object.defineProperties(something, {",
            "  prop: {",
            "    get() {},",
            "    set(v) {},",
            "  }",
            "});"));

    assertThat(getLastAccessorSummary().getKind("prop"))
        .isEqualTo(PropertyAccessKind.GETTER_AND_SETTER);
  }

  @Test
  public void detectJscompGlobalObject() {
    testSame(
        lines(
            "$jscomp.global.Object.defineProperty(something, 'prop', {", //
            "    get: function() {},",
            "});"));

    assertThat(getLastAccessorSummary().getKind("prop")).isEqualTo(PropertyAccessKind.GETTER_ONLY);

    testSame(
        lines(
            "$jscomp$global.Object.defineProperty(something, 'prop', {", //
            "    get: function() {}",
            "});"));

    assertThat(getLastAccessorSummary().getKind("prop")).isEqualTo(PropertyAccessKind.GETTER_ONLY);

    testSame(
        lines(
            "other.Object.defineProperty(something, 'prop', {", //
            "    get: function() {},",
            "});"));

    assertThat(getLastAccessorSummary().getKind("prop")).isEqualTo(PropertyAccessKind.NORMAL);
  }

  private AccessorSummary getLastAccessorSummary() {
    return getLastCompiler().getAccessorSummary();
  }
}
