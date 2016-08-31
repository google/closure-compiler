/*
 * Copyright 2016 The Closure Compiler Authors.
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

import junit.framework.TestCase;

/**
 * Tests {@link GwtProperties}.
 */
public final class GwtPropertiesTest extends TestCase {

  public void testLoadEmpty() {
    GwtProperties p = GwtProperties.load("");
    assertThat(p.propertyNames()).isEmpty();
  }

  public void testLoadWithComments() {
    String src = "\n"
        + "# not.set=value\n"
        + "is.set=value\n";
    GwtProperties p = GwtProperties.load(src);

    assertEquals(null, p.getProperty("not.set"));
    assertEquals("value", p.getProperty("is.set"));
  }

  public void testNoEquals() {
    GwtProperties p = GwtProperties.load("foo bar");
    assertEquals("bar", p.getProperty("foo"));
  }

  public void testExtraCR() {
    GwtProperties p = GwtProperties.load("value is \\\r\nradical");
    assertEquals("is radical", p.getProperty("value"));
  }

  public void testLongValue() {
    String src = "\n"
        + "property : 1,\\\n"
        + "   2,\\\n"
        + "   3\n";
    GwtProperties p = GwtProperties.load(src);

    assertEquals("1,2,3", p.getProperty("property"));
  }

}
