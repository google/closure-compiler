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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ConvertToDottedProperties}. */
@RunWith(JUnit4.class)
public final class ConvertToDottedPropertiesTest extends CompilerTestCase {

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new ConvertToDottedProperties(compiler);
  }

  @Test
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

  @Test
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
    // Ignorable control characters are ok in Java identifiers, but not in JS.
    testSame("a['A\u0004']");
    // upper case lower half of o from phonetic extensions set.
    // valid in Safari, not in Firefox, IE.
    testSame("a['\u1d17A']");
    // Latin capital N with tilde - nice if we handled it, but for now let's
    // only allow simple Latin (aka ASCII) to be converted.
    testSame("a['\u00d1StuffAfter']");
  }

  @Test
  public void testAlreadyDotted() {
    testSame("a.b");
    testSame("var a = {b: 0};");
  }

  @Test
  public void testQuotedProps() {
    testSame("({'':0})");
    testSame("({'1.0':0})");
    testSame("({'\u1d17A':0})");
    testSame("({'a\u0004b':0})");
  }

  @Test
  public void test5746867() {
    testSame("var a = { '$\\\\' : 5 };");
    testSame("var a = { 'x\\\\u0041$\\\\' : 5 };");
  }

  @Test
  public void testOptionalChaining() {
    test("data?.['name']", "data?.name");
    test("data?.['name']?.['first']", "data?.name?.first");
    test("data['name']?.['first']", "data.name?.first");
    testSame("a?.[0]");
    testSame("a?.['']");
    testSame("a?.[' ']");
    testSame("a?.[',']");
    testSame("a?.[';']");
    testSame("a?.[':']");
    testSame("a?.['.']");
    testSame("a?.['0']");
    testSame("a?.['p ']");
    testSame("a?.['p' + '']");
    testSame("a?.[p]");
    testSame("a?.[P]");
    testSame("a?.[$]");
    testSame("a?.[p()]");
    testSame("a?.['default']");
  }

  @Test
  public void testComputedPropertyOrField() {
    test("const test1 = {['prop1']:87};", "const test1 = {prop1:87};");
    test(
        "const test1 = {['prop1']:87,['prop2']:bg,['prop3']:'hfd'};",
        "const test1 = {prop1:87,prop2:bg,prop3:'hfd'};");
    test(
        "o = {['x']: async function(x) { return await x + 1; }};",
        "o = {x:async function (x) { return await x + 1; }};");
    test("o = {['x']: function*(x) {}};", "o = {x: function*(x) {}};");
    test(
        "o = {['x']: async function*(x) { return await x + 1; }};",
        "o = {x:async function*(x) { return await x + 1; }};");
    test("class C {'x' = 0;  ['y'] = 1;}", "class C { x= 0;y= 1;}");
    test("class C {'m'() {} }", "class C {m() {}}");

    test("const o = {'b'() {}, ['c']() {}};", "const o = {b: function() {}, c:function(){}};");
    test("o = {['x']: () => this};", "o = {x: () => this};");

    test("const o = {get ['d']() {}};", "const o = {get d() {}};");
    test("const o = { set ['e'](x) {}};", "const o = { set e(x) {}};");
    test(
        "class C {'m'() {}  ['n']() {} 'x' = 0;  ['y'] = 1;}",
        "class C {m() {}  n() {} x= 0;y= 1;}");
    test("const o = { get ['d']() {},  set ['e'](x) {}};", "const o = {get d() {},  set e(x){}};");
    test(
        "const o = {['a']: 1,'b'() {}, ['c']() {},  get ['d']() {},  set ['e'](x) {}};",
        "const o = {a: 1,b: function() {}, c: function() {},  get d() {},  set e(x) {}};");

    // test static keyword
    test(
        lines(
            "class C {", //
            "'m'(){}",
            "['n'](){}",
            "static 'x' = 0;",
            "static ['y'] = 1;}"),
        lines(
            "class C {", //
            "m(){}",
            "n(){}",
            "static x = 0;",
            "static y= 1;}"));
    test(
        lines(
            "window[\"MyClass\"] = class {", //
            "static [\"Register\"](){}",
            "};"),
        lines(
            "window.MyClass = class {", //
            "static Register(){}",
            "};"));
    test(
        lines(
            "class C { ",
            "'method'(){} ",
            "async ['method1'](){}",
            "*['method2'](){}",
            "static ['smethod'](){}",
            "static async ['smethod1'](){}",
            "static *['smethod2'](){}}"),
        lines(
            "class C {",
            "method(){}",
            "async method1(){}",
            "*method2(){}",
            "static smethod(){}",
            "static async smethod1(){}",
            "static *smethod2(){}}"));

    testSame("const o = {[fn()]: 0}");
    testSame("const test1 = {[0]:87};");
    testSame("const test1 = {['default']:87};");
    testSame("class C { ['constructor']() {} }");
    testSame("class C { ['constructor'] = 0 }");
  }
}
