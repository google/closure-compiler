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

package com.google.javascript.jscomp;

import com.google.common.collect.Sets;

/**
 * Test case for {@link GatherExternProperties}.
 */
public class GatherExternPropertiesTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new GatherExternProperties(compiler);
  }

  public void testGatherExternProperties() throws Exception {
    // Properties.
    assertExternProperties(
        "foo.bar;",
        "bar");

    // Object literals.
    assertExternProperties(
        "foo = {bar: null, 'baz': {foobar: null}};",
        "bar", "baz", "foobar");

    // Object literal with numeric propertic.
    assertExternProperties(
        "foo = {0: null};",
         "0");

    // Top-level variables do not count.
    assertExternProperties(
        "var foo;");

    // String-key access does not count.
    assertExternProperties(
        "foo['bar'] = {};");

    // Record types not supported yet.
    assertExternProperties(
        "/** @type {{bar: string, baz: string}} */ var foo;");
  }

  private void assertExternProperties(String externs, String... properties) {
    testSame(externs, "", null);
    assertEquals(Sets.newHashSet(properties),
        getLastCompiler().getExternProperties());
  }
}
