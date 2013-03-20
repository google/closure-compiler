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

  static final DiagnosticType NAME_ERROR =
      ExpandJqueryAliases.JQUERY_UNABLE_TO_EXPAND_INVALID_NAME_ERROR;

  static final DiagnosticType INVALID_LIT_ERROR =
      ExpandJqueryAliases.JQUERY_UNABLE_TO_EXPAND_INVALID_LIT_ERROR;

  static final DiagnosticType USELESS_EACH_ERROR =
      ExpandJqueryAliases.JQUERY_USELESS_EACH_EXPANSION;

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

  public void testJqueryExtendExpansion() {
    String setupCode = "var jQuery={},obj2={};";

    // test for extend call that should not be expanded - no arguments
    testSame(setupCode + "jQuery.extend()");

    // test for extend call that should not be expanded - empty argument
    // this statement has no effect in actual code
    testSame(setupCode + "jQuery.extend({})");

    // test single argument call - should assign to the jQuery namespace
    test(setupCode + "jQuery.extend({a:'test'})",
        setupCode + "{jQuery.a = 'test';}");

    // test expansion when extending the jQuery prototype
    test(setupCode + "jQuery.fn=jQuery.prototype;" +
        "jQuery.fn.extend({a:'test', b:'test2'});",
        setupCode + "jQuery.fn=jQuery.prototype;" +
        "{jQuery.prototype.a = 'test'; jQuery.prototype.b = 'test2';}");

    // Expand the extension of obj2
    test(setupCode + "jQuery.extend(obj2, {a:'test', b:'test2'});",
        setupCode + "{obj2=obj2||{}; obj2.a='test'; obj2.b='test2';}");

    // Expand the jQuery namespace - 2 argument call
    // Must ensure that the first argument is defined
    test(setupCode + "jQuery.extend(jQuery,{a:'test', b:'test2'});",
        setupCode + "{jQuery = jQuery || {}; jQuery.a = 'test';" +
        "jQuery.b = 'test2';}");

    // Test extend call where first argument includes a method call
    testSame(setupCode + "obj2.meth=function() { return { a:{} }; };" +
        "jQuery.extend(obj2.meth().a, {a: 'test'});");

    // Test extend call where returned object is used
    test(setupCode + "obj2 = jQuery.extend(obj2, {a:'test', " +
        "b:'test2'});",
        setupCode + "obj2 = function() {obj2 = obj2 || {}; " + 
        "obj2.a = 'test';obj2.b = 'test2';return obj2;}.call(this);");
  }

  public void testJqueryExpandedEachExpansion() {
    String setupCode = "var jQuery={};" +
        "jQuery.expandedEach=function(vals, callback){};";
    String resultCode =
        "var jQuery={ expandedEach: function(vals, callback){} };";

    testSame(setupCode);

    // Test expansion with object literal
    test(setupCode + "jQuery.expandedEach({'a': 1, 'b': 2, 'c': 8}," +
        "function(key, val) { var a = key; jQuery[key] = val; });",
        resultCode + "(function(){ var a = 'a'; jQuery.a = 1 })();" +
        "(function(){ var a = 'b'; jQuery.b = 2 })();" +
        "(function(){ var a = 'c'; jQuery.c = 8 })();");

    // Test expansion with array literal
    // For array literals, the key parameter will be the element index number
    // and the value parameter will be the string literal. In this case, the
    // string literal value should become a property name.
    test(setupCode + "jQuery.expandedEach(['a', 'b', 'c']," +
        "function(key, val){ jQuery[val] = key; });",
        resultCode + "(function(){ jQuery.a = 0; })();" +
        "(function(){ jQuery.b = 1; })();" +
        "(function(){ jQuery.c = 2 })();");

    // Test expansion with object literal using 'this' keyword
    test(setupCode + "jQuery.expandedEach({'a': 1, 'b': 2, 'c': 8}," +
        "function(key, val) { var a = key; jQuery[key] = this; });",
        resultCode + "(function(){ var a = 'a'; jQuery.a = 1 })();" +
        "(function(){ var a = 'b'; jQuery.b = 2 })();" +
        "(function(){ var a = 'c'; jQuery.c = 8 })();");

    // Test expansion with array literal using 'this' keyword
    test(setupCode + "jQuery.expandedEach(['a', 'b', 'c']," +
        "function(key, val){ jQuery[this] = key; });",
        resultCode + "(function(){ jQuery.a = 0; })();" +
        "(function(){ jQuery.b = 1; })();" +
        "(function(){ jQuery.c = 2 })();");

    // test nested function using argument name to shadow callback name
    test(setupCode + "jQuery.expandedEach(['a'], function(key,val) {" +
        "jQuery[val] = key; (function(key) { jQuery[key] = 1;})('test'); })",
        resultCode + "(function(){ jQuery.a = 0;" +
         "(function(key){ jQuery[key] = 1})('test') })()");

    // test nested function using var name to shadow callback name
    test(setupCode + "jQuery.expandedEach(['a'], function(key,val) {" +
        "jQuery[val] = key; (function(key) { var val = 2;" +
        "jQuery[key] = val;})('test');})",
        resultCode + "(function(){" +
        "jQuery.a=0;" +
        "(function(key){var val = 2; jQuery[key] = val;})('test')})()");

    // test nested function using function name to shadow callback name
    test(setupCode + "jQuery.expandedEach(['a'], function(key,val) {" +
        "jQuery[val] = key; (function(key1) {" +
        "function key() {}; key();" +
        "})('test');})",
        resultCode + "(function(){" +
        "jQuery.a=0;(function(key1) {" +
        "function key() {} key(); })('test')})()");

    // test using return val
    test(setupCode + "alert(jQuery.expandedEach(['a']," +
        "function(key,val) { jQuery[val] = key;})[0])",
        resultCode + "alert((function(){" +
        "(function(){ jQuery.a = 0;})(); return ['a']})()[0]);");

    // Loop object is a variable. Test that warning is raised.
    String testCode = "var a = ['a'];" +
        "jQuery.expandedEach(a, function(key,val){ jQuery[key]=val; })";
    test(setupCode + testCode, resultCode + testCode, null, INVALID_LIT_ERROR);

    // Invalid property name. Test that warning is raised.
    test(setupCode + "var obj2={};" +
        "jQuery.expandedEach(['foo','bar'], function(i, name) {" +
        "obj2[ '[object ' + name + ']' ] = 'a';});",
        resultCode + "var obj2={};" +
        "jQuery.expandedEach(['foo','bar'], function(i, name) {" +
        "obj2[ '[object foo]' ] = 'a';});",
        null, USELESS_EACH_ERROR);

    // Useless expansion (key not used). Test that warning is raised.
    testCode =
        "var obj2={}; jQuery.expandedEach(['foo','bar'], function(i, name) {" +
        "obj2[i] = 1;});";
    test(setupCode + testCode, resultCode + testCode, null, USELESS_EACH_ERROR);
  }
}
