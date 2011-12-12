/*
 * Copyright 2007 The Closure Compiler Authors.
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
 * Tests for {@link ConvertToDottedProperties}.
 *
 */
public class ConvertToDottedPropertiesTest extends CompilerTestCase {
  @Override public CompilerPass getProcessor(Compiler compiler) {
    return new ConvertToDottedProperties(compiler);
  }

  public void testConvert() {
    test("a['p']", "a.p");
    test("a['_p_']", "a._p_");
    test("a['_']", "a._");
    test("a['$']", "a.$");
    test("a.b.c['p']", "a.b.c.p");
    test("a.b['c'].p", "a.b.c.p");
    test("a['p']();", "a.p();");
    test("a()['p']", "a().p");
    // ASCII in Unicode is safe.
    test("a['\u0041A']", "a.AA");
  }

  public void testDoNotConvert() {
    testSame("a[0]");
    testSame("a['']");
    testSame("a[' ']");
    testSame("a[',']");
    testSame("a[';']");
    testSame("a[':']");
    testSame("a['.']");
    testSame("a['0']");
    testSame("a['p ']");
    testSame("a['p' + '']");
    testSame("a[p]");
    testSame("a[P]");
    testSame("a[$]");
    testSame("a[p()]");
    testSame("a['default']");
    // upper case lower half of o from phonetic extensions set.
    // valid in Safari, not in Firefox, IE.
    test("a['\u1d17A']", "a['\u1d17A']");
    // Latin capital N with tilde - nice if we handled it, but for now let's
    // only allow simple Latin (aka ASCII) to be converted.
    test("a['\u00d1StuffAfter']", "a['\u00d1StuffAfter']");
  }


  public void testQuotedProps() {
    testSame("({'':0})");
    testSame("({'1.0':0})");
    testSame("({'\u1d17A':0})");
  }

  public void test5746867() {
    testSame("var a = { '$\\\\' : 5 };");
    testSame("var a = { 'x\\\\u0041$\\\\' : 5 };");
  }
}
