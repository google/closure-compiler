/*
 * Copyright 2011 The Closure Compiler Authors.
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
 * Tests for {@link ExpandJqueryAliases}
 */
public class ExpandJqueryAliasesTest extends CompilerTestCase {
  private JqueryCodingConvention conv = new JqueryCodingConvention();

  public ExpandJqueryAliasesTest() {}

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    compiler.options.setCodingConvention(conv);
    return new ExpandJqueryAliases(compiler);
  }

  public void testJqueryFnAliasExpansion() {
    String setupCode = "var jQuery={};jQuery.fn=jQuery.prototype;";

    testSame(setupCode);

    test(setupCode + "jQuery.fn.foo='bar';",
        setupCode + "jQuery.prototype.foo='bar';");

    test(setupCode + "jQuerySub.fn.foo='bar';",
        setupCode + "jQuerySub.prototype.foo='bar';");
  }

  public void testJqueryExtendAliasExpansion() {
    String setupCode = "var jQuery={},obj2={};";

    //test invalid extend call
    testSame(setupCode + "jQuery.extend()");

    //test empty extend call
    testSame(setupCode + "jQuery.extend({})");

    test("jQuery.extend({a:'test'})",
        "(function(){jQuery.a='test';return jQuery})()");

    //Extend the jQuery prototype
    test(setupCode + "jQuery.fn=jQuery.prototype;" +
        "jQuery.fn.extend({a:'test', b:'test2'});",
        setupCode + "jQuery.fn=jQuery.prototype;(function(){" +
        "jQuery.prototype.a='test';jQuery.prototype.b='test2';" +
        "return jQuery;})()");

    //Extend obj2
    test(setupCode + "jQuery.extend(obj2, {a:'test', b:'test2'});",
        setupCode + "(function(){" +
        "obj2.a='test';obj2.b='test2';return obj2})()");

    //Extend the jQuery namespace - 2 argument call
    test(setupCode + "jQuery.extend(jQuery,{a:'test', b:'test2'});",
        setupCode + "(function(){" +
        "jQuery.a='test';jQuery.b='test2';return jQuery})()");
  }
}
