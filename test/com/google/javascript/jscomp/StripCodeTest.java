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

import com.google.common.collect.ImmutableSet;
import java.util.Set;

/**
 * Tests for {@link StripCode}.
 *
 */
public final class StripCodeTest extends CompilerTestCase {

  private static final String EXTERNS = "";

  public StripCodeTest() {
    super(EXTERNS, true);
  }

  /**
   * Creates an instance for removing logging code.
   *
   * @param compiler The Compiler
   * @return A new {@link StripCode} instance
   */
  private static StripCode createLoggerInstance(Compiler compiler) {
    Set<String> stripTypes = ImmutableSet.of(
        "goog.debug.DebugWindow",
        "goog.debug.FancyWindow",
        "goog.debug.Formatter",
        "goog.debug.HtmlFormatter",
        "goog.debug.TextFormatter",
        "goog.debug.Logger",
        "goog.debug.LogManager",
        "goog.debug.LogRecord",
        "goog.net.BrowserChannel.LogSaver",
        "GA_GoogleDebugger");

    Set<String> stripNames = ImmutableSet.of(
        "logger",
        "logger_",
        "debugWindow",
        "debugWindow_",
        "logFormatter_",
        "logBuffer_");

    Set<String> stripNamePrefixes = ImmutableSet.of("trace");
    Set<String> stripTypePrefixes = ImmutableSet.of("e.f.Trace");

    return new StripCode(compiler, stripTypes, stripNames, stripTypePrefixes,
        stripNamePrefixes);
  }

  @Override public CompilerPass getProcessor(Compiler compiler) {
    return createLoggerInstance(compiler);
  }

  public void testLoggerDefinedInConstructor() {
    test("a.b.c = function() {" +
         "  this.logger = goog.debug.Logger.getLogger('a.b.c');" +
         "};",
         "a.b.c=function(){}");
  }

  public void testLoggerDefinedInPrototype1() {
    test("a.b.c = function() {};" +
         "a.b.c.prototype.logger = goog.debug.Logger.getLogger('a.b.c');",
         "a.b.c=function(){}");
  }

  public void testLoggerDefinedInPrototype2() {
    test("a.b.c = function() {};" +
         "a.b.c.prototype = {logger: goog.debug.Logger.getLogger('a.b.c')}",
         "a.b.c = function() {};" +
         "a.b.c.prototype = {}");
  }

  public void testLoggerDefinedInPrototype3() {
    test("a.b.c = function() {};" +
         "a.b.c.prototype = { " +
         "  get logger() {return goog.debug.Logger.getLogger('a.b.c')}" +
         "}",
         "a.b.c = function() {};" +
         "a.b.c.prototype = {}");
  }

  public void testLoggerDefinedInPrototype4() {
    test("a.b.c = function() {};" +
         "a.b.c.prototype = { " +
         "  set logger(a) {this.x = goog.debug.Logger.getLogger('a.b.c')}" +
         "}",
         "a.b.c = function() {};" +
         "a.b.c.prototype = {}");
  }

  public void testLoggerDefinedInPrototype5() {
    test("a.b.c = function() {};" +
         "a.b.c.prototype = { " +
         "  get f() {return this.x;}," +
         "  set f(a) {this.x = goog.debug.Logger.getLogger('a.b.c')}" +
         "}",
         "a.b.c = function() {};" +
         "a.b.c.prototype = { " +
         "  get f() {return this.x;}," +
         "  set f(a) {this.x = null}" +
         "}");
  }

  public void testLoggerDefinedStatically() {
    test("a.b.c = function() {};" +
         "a.b.c.logger = goog.debug.Logger.getLogger('a.b.c');",
         "a.b.c=function(){}");
  }

  public void testLoggerDefinedInObjectLiteral1() {
    test("a.b.c = {" +
         "  x: 0," +
         "  logger: goog.debug.Logger.getLogger('a.b.c')" +
         "};",
         "a.b.c={x:0}");
  }

  public void testLoggerDefinedInObjectLiteral2() {
    test("a.b.c = {" +
         "  x: 0," +
         "  get logger() {return goog.debug.Logger.getLogger('a.b.c')}" +
         "};",
         "a.b.c={x:0}");
  }

  public void testLoggerDefinedInObjectLiteral3() {
    test("a.b.c = {" +
         "  x: null," +
         "  get logger() {return this.x}," +
         "  set logger(a) {this.x  = goog.debug.Logger.getLogger(a)}" +
         "};",
         "a.b.c={x:null}");
  }

  public void testLoggerDefinedInObjectLiteral4() {
    test("a.b.c = {" +
         "  x: null," +
         "  get y() {return this.x}," +
         "  set y(a) {this.x  = goog.debug.Logger.getLogger(a)}" +
         "};",
         "a.b.c = {" +
         "  x: null," +
         "  get y() {return this.x}," +
         "  set y(a) {this.x  = null}" +
         "};");
  }

  public void testLoggerDefinedInPrototypeAndUsedInConstructor() {
    test("a.b.c = function(level) {" +
         "  if (!this.logger.isLoggable(level)) {" +
         "    this.logger.setLevel(level);" +
         "  }" +
         "  this.logger.log(level, 'hi');" +
         "};" +
         "a.b.c.prototype.logger = goog.debug.Logger.getLogger('a.b.c');" +
         "a.b.c.prototype.go = function() { this.logger.finer('x'); };",
         "a.b.c=function(level){if(!null);};" +
         "a.b.c.prototype.go=function(){}");
  }

  public void testLoggerDefinedStaticallyAndUsedInConstructor() {
    test("a.b.c = function(level) {" +
         "  if (!a.b.c.logger.isLoggable(level)) {" +
         "    a.b.c.logger.setLevel(level);" +
         "  }" +
         "  a.b.c.logger.log(level, 'hi');" +
         "};" +
         "a.b.c.logger = goog.debug.Logger.getLogger('a.b.c');",
         "a.b.c=function(level){if(!null);}");
  }

  public void testLoggerVarDeclaration() {
    test("var logger = opt_logger || goog.debug.LogManager.getRoot();", "");
  }

  public void testLoggerMethodCallByVariableType() {
    test("var x = goog.debug.Logger.getLogger('a.b.c'); y.info(a); x.info(a);",
         "y.info(a)");
  }

  public void testSubPropertyAccessByVariableName() {
    test("var x, y = goog.debug.Logger.getLogger('a.b.c');" +
         "var logger = x;" +
         "var curlevel = logger.level_ ? logger.getLevel().name : 3;",
         "var x;var curlevel=null?null:3");
  }

  public void testPrefixedVariableName() {
    test("this.blcLogger_ = goog.debug.Logger.getLogger('a.b.c');" +
         "this.blcLogger_.fine('Raised dirty states.');", "");
  }

  public void testPrefixedPropertyName() {
    test("a.b.c.staticLogger_ = goog.debug.Logger.getLogger('a.b.c');" +
         "a.b.c.staticLogger_.fine('-' + a.b.c.d_())", "");
  }

  public void testPrefixedClassName() {
    test("a.b.MyLogger = function(logger) {" +
         "  this.logger_ = logger;" +
         "};" +
         "a.b.MyLogger.prototype.shout = function(msg, opt_x) {" +
         "  this.logger_.log(goog.debug.Logger.Level.SHOUT, msg, opt_x);" +
         "};",
         "a.b.MyLogger=function(logger){};" +
         "a.b.MyLogger.prototype.shout=function(msg,opt_x){}");
  }

  public void testLoggerClassDefinition() {
    test("goog.debug.Logger=function(name){this.name_=name}", "");
  }

  public void testStaticLoggerPropertyDefinition() {
    test("goog.debug.Logger.Level.SHOUT=" +
         "new goog.debug.Logger.Level(x,1200)", "");
  }

  public void testStaticLoggerMethodDefinition() {
    test("goog.debug.Logger.getLogger=function(name){" +
         "return goog.debug.LogManager.getLogger(name)" +
         "};", "");
  }

  public void testPrototypeFieldDefinition() {
    test("goog.debug.Logger.prototype.level_=null;", "");
  }

  public void testPrototypeFieldDefinitionWithoutAssignment() {
    test("goog.debug.Logger.prototype.level_;", "");
  }

  public void testPrototypeMethodDefinition() {
    test("goog.debug.Logger.prototype.addHandler=" +
         "function(handler){this.handlers_.push(handler)};", "");
  }

  public void testPublicPropertyAssignment() {
    // Eliminate property assignments on vars/properties that we
    // remove as otherwise we create invalid code.
    test("goog.debug.Logger = 1; goog.debug.Logger.prop=2; ", "");
    test("this.blcLogger_.level=x", "");
    test("goog.ui.Component.logger.prop=y", "");
    test("goog.ui.Component.logger.prop.foo.bar=baz", "");
  }

  public void testGlobalCallWithStrippedType() {
    testSame("window.alert(goog.debug.Logger)");
  }

  public void testClassDefiningCallWithStripType1() {
    test("goog.debug.Logger.inherits(Object)", "");
  }

  public void testClassDefiningCallWithStripType2() {
    test("goog.formatter=function(){};" +
         "goog.inherits(goog.debug.Formatter,goog.formatter)",
         "goog.formatter=function(){}");
  }

  public void testClassDefiningCallWithStripType3() {
    testError("goog.formatter=function(){};" +
         "goog.inherits(goog.formatter,goog.debug.Formatter)",
         StripCode.STRIP_TYPE_INHERIT_ERROR);
  }

  public void testClassDefiningCallWithStripType4() {
    testError("goog.formatter=function(){};" +
         "goog.formatter.inherits(goog.debug.Formatter)",
         StripCode.STRIP_TYPE_INHERIT_ERROR);
  }

  public void testClassDefiningCallWithStripType5() {
    testSame("goog.formatter=function(){};" +
             "goog.formatter.inherits(goog.debug.FormatterFoo)");
  }

  public void testClassDefiningCallWithStripType6() {
    testError("goog.formatter=function(){};" +
         "goog.formatter.inherits(goog.debug.Formatter.Foo)",
         StripCode.STRIP_TYPE_INHERIT_ERROR);
  }

  public void testClassDefiningCallWithStripType7() {
    test("goog.inherits(goog.debug.TextFormatter,goog.debug.Formatter)", "");
  }

  public void testClassDefiningCallWithStripType8() {
    // listed types should be removed.
    test("goog.debug.DebugWindow = function(){}", "");
    test("goog.inherits(goog.debug.DebugWindow,Base)", "");

    // types that happen to have strip types as prefix should not be
    // stripped.
    testSame("goog.debug.DebugWindowFoo=function(){}");
    testSame("goog.inherits(goog.debug.DebugWindowFoo,Base)");
    testSame("goog.debug.DebugWindowFoo");
    testSame("goog.debug.DebugWindowFoo=1");

    // qualified subtypes should be removed.
    test("goog.debug.DebugWindow.Foo=function(){}", "");
    test("goog.inherits(goog.debug.DebugWindow.Foo,Base)", "");
    test("goog.debug.DebugWindow.Foo", "");
    test("goog.debug.DebugWindow.Foo=1", "");
  }

  public void testPropertyWithEmptyStringKey() {
    test("goog.format.NUMERIC_SCALES_BINARY_ = {'': 1};",
         "goog.format.NUMERIC_SCALES_BINARY_={\"\":1}");
  }

  public void testVarinIf() {
    test("if(x)var logger=null;else foo()", "if(x);else foo()");
  }

  public void testGetElemInIf() {
    test("var logger=null;if(x)logger[f];else foo()", "if(x);else foo()");
  }

  public void testAssignInIf() {
    test("var logger=null;if(x)logger=1;else foo()",
         "if(x);else foo()");
  }

  public void testNamePrefix() {
    test("a = function(traceZZZ) {}; a.prototype.traceXXX = {x: 1};" +
         "a.prototype.z = function() { this.traceXXX.f(); };" +
         "var traceYYY = 0;",
         "a=function(traceZZZ){};a.prototype.z=function(){}");
  }

  public void testTypePrefix() {
    test("e.f.TraceXXX = function() {}; " +
         "e.f.TraceXXX.prototype.yyy = 2;", "");
  }

  public void testStripCallsToStrippedNames1() {
    test("a = function() { this.logger_ = function(msg){}; };" +
         "a.prototype.b = function() { this.logger_('hi'); }",
         "a=function(){};a.prototype.b=function(){}");
    test("a = function() {};" +
         "a.prototype.logger_ = function(msg) {};" +
         "a.prototype.b = function() { this.logger_('hi'); }",
         "a=function(){};a.prototype.b=function(){}");
  }

  public void testStripCallsToStrippedNames2() {
    test("a = function() {};" +
         "a.prototype.logger_ = function(msg) {};" +
         "a.prototype.b = function() { this.logger_('hi'); }",
         "a=function(){};a.prototype.b=function(){}");
  }

  public void testStripCallsToStrippedNames3() {
    test("a = function() { this.logger_ = function(msg){}; };" +
         "a.prototype.b = function() { this.logger_('hi').foo = 2; }",
         "a=function(){};a.prototype.b=function(){2;}");
  }

  public void testStripCallsToStrippedNames4() {
    test("a = this.logger_().foo;",
         "a = null;");
  }

  public void testStripVarsInitializedFromStrippedNames1() {
    test("a = function() { this.logger_ = function() { return 1; }; };" +
         "a.prototype.b = function() { " +
         "  var one = this.logger_(); if (one) foo() }",
          "a=function(){};a.prototype.b=function(){if(null)foo()}");
  }

  public void testStripVarsInitializedFromStrippedNames2() {
    test("a = function() { this.logger_ = function() { return 1; }; };" +
         "a.prototype.b = function() { " +
         "  var one = this.logger_.foo.bar(); if (one) foo() }",
         "a=function(){};a.prototype.b=function(){if(null)foo()}");
  }

  public void testReportErrorOnStripInNestedAssignment() {
    // Strip name
    testError("(foo.logger_ = 7) + 8",
         StripCode.STRIP_ASSIGNMENT_ERROR);

    // Strip namespaced type
    testError("(goog.debug.Logger.foo = 7) + 8",
         StripCode.STRIP_ASSIGNMENT_ERROR);

    // Strip non-namespaced type
    testError("(GA_GoogleDebugger.foo = 7) + 8",
         StripCode.STRIP_ASSIGNMENT_ERROR);
  }

  public void testNewOperatior1() {
    test("function foo() {} foo.bar = new goog.debug.Logger();",
         "function foo() {} foo.bar = null;");
  }

  public void testNewOperatior2() {
    test("function foo() {} foo.bar = (new goog.debug.Logger()).foo();",
         "function foo() {} foo.bar = null;");
  }

  public void testNewOperatior3() {
    test("(new goog.debug.Logger()).foo().bar = 2;",
         "2;");
  }

  public void testCrazyNesting1() {
    test("var x = {}; x[new goog.debug.Logger()] = 3;",
         "var x = {}; x[null] = 3;");
  }

  public void testCrazyNesting2() {
    test("var x = {}; x[goog.debug.Logger.getLogger()] = 3;",
         "var x = {}; x[null] = 3;");
  }

  public void testCrazyNesting3() {
    test("var x = function() {}; x(new goog.debug.Logger());",
         "var x = function() {}; x(null);");
  }

  public void testCrazyNesting4() {
    test("var x = function() {}; x(goog.debug.Logger.getLogger());",
         "var x = function() {}; x(null);");
  }

  public void testCrazyNesting5() {
    test("var x = function() {}; var y = {}; " +
         "var z = goog.debug.Logger.getLogger(); x(y[z['foo']]);",
         "var x = function() {}; var y = {}; x(y[null]);");
  }

  public void testNamespace1() {
    test(
        "var x = {};x.traceutil = {};x.traceutil.FOO = 1;",
        "var x = {};");
  }


  public void testMethodCallTriggersRemoval() {
    test("this.logger_.foo.bar();",
        "");
  }

  public void testRemoveExpressionByName() {
    test("this.logger_.foo.bar;",
        "");
  }

}
