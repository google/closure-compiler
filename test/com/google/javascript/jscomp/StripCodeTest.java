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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link StripCode}. */
@RunWith(JUnit4.class)
public final class StripCodeTest extends CompilerTestCase {

  private static final String EXTERNS = "";

  public StripCodeTest() {
    super(EXTERNS);
  }

  /**
   * Creates an instance for removing logging code.
   *
   * @param compiler The Compiler
   * @return A new {@link StripCode} instance
   */
  private static StripCode createLoggerInstance(Compiler compiler) {
    ImmutableSet<String> stripTypes =
        ImmutableSet.of(
            "goog.debug.DebugWindow",
            "goog.debug.FancyWindow",
            "goog.debug.Formatter",
            "goog.debug.HtmlFormatter",
            "goog.debug.TextFormatter",
            "goog.debug.Logger",
            "goog.debug.LogManager",
            "goog.debug.LogRecord",
            "goog.net.BrowserChannel.LogSaver",
            "goog.log",
            "GA_GoogleDebugger");

    ImmutableSet<String> stripNames =
        ImmutableSet.of(
            "logger", "logger_", "debugWindow", "debugWindow_", "logFormatter_", "logBuffer_");

    ImmutableSet<String> stripNamePrefixes = ImmutableSet.of("trace");

    return new StripCode(compiler, stripTypes, stripNames, stripNamePrefixes, false);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return createLoggerInstance(compiler);
  }

  @Test
  public void testLowerCaseSuffixFunctionCallMatch() {
    // should lower-case match with "logger"
    test("errorLogger();", "");
  }

  @Test
  public void testChildNameOfStrippedTypeIsStripped() {
    test(
        lines(
            "goog.debug.FancyWindow.getStoredKeys_ = function() {};",
            "goog.debug.FancyWindow.getStoredKeys_();"),
        "");
    // It's safer to replace a variable's initial value with null instead of
    // removing the declaration.
    test(
        lines(
            "var goog$debug$FancyWindow$getStoredKeys_ = function() {};",
            "goog$debug$FancyWindow$getStoredKeys_();"),
        "var goog$debug$FancyWindow$getStoredKeys_ = null;");
  }

  @Test
  public void testPartiallyCollapsedTypeIsStripped() {
    test(
        lines(
            "goog$debug.FancyWindow.getStoredKeys_ = function() {};",
            "goog$debug.FancyWindow.getStoredKeys_();"),
        "");
  }

  @Test
  public void testLoggerDefinedInConstructor() {
    test(
        lines(
            "a.b.c = function() {", //
            "  this.logger = goog.debug.Logger.getLogger('a.b.c');",
            "};"),
        "a.b.c=function(){}");
    test(
        lines(
            "let a$b$c = function() {", //
            "  this.logger = goog$debug$Logger.getLogger('a.b.c');",
            "};"),
        "let a$b$c=function(){}");
  }

  @Test
  public void testLoggerDefinedInConstructorEs6() {
    test(
        lines(
            "class A {",
            "  constructor() {",
            "    this.logger = goog.debug.Logger.getLogger('A');",
            "    this.otherProperty = 3;",
            "  }",
            "}",
            "let a = new A;",
            "a.logger.warning('foobar');"),
        lines(
            "class A {",
            "  constructor() {",
            "    this.otherProperty = 3;",
            "  }",
            "}",
            "let a = new A;"));
    test(
        lines(
            "class A {",
            "  constructor() {",
            "    this.logger = goog$debug$Logger.getLogger('A');",
            "    this.otherProperty = 3;",
            "  }",
            "}",
            "let a = new A;",
            "a.logger.warning('foobar');"),
        lines(
            "class A {",
            "  constructor() {",
            "    this.otherProperty = 3;",
            "  }",
            "}",
            "let a = new A;"));
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableNormalize();
  }

  @Test
  public void testLoggerDefinedInPrototype1() {
    test(
        lines(
            "a.b.c = function() {}",
            "a.b.c.prototype.logger = goog.debug.Logger.getLogger('a.b.c');"),
        "a.b.c=function(){}");
    test(
        lines(
            "a$b$c = function() {};", //
            "a$b$c.prototype.logger = goog$debug$Logger.getLogger('a.b.c');"),
        "a$b$c = function(){}");
  }

  @Test
  public void testLoggerDefinedInPrototype2() {
    test(
        lines(
            "a.b.c = function() {};", //
            "a.b.c.prototype = {logger: goog.debug.Logger.getLogger('a.b.c')}"),
        lines(
            "a.b.c = function() {}", //
            "a.b.c.prototype = {}"));
    test(
        lines(
            "let a$b$c = function() {};", //
            "a$b$c.prototype = {logger: goog$debug$Logger.getLogger('a.b.c')}"),
        lines(
            "let a$b$c = function() {};", //
            "a$b$c.prototype = {}"));
  }

  @Test
  public void testLoggerDefinedInPrototype3() {
    test(
        lines(
            "a.b.c = function() {}",
            "a.b.c.prototype = { ",
            "  get logger() {return goog.debug.Logger.getLogger('a.b.c')}",
            "}"),
        lines(
            "a.b.c = function() {}", //
            "a.b.c.prototype = {}"));
    test(
        lines(
            "let a$b$c = function() {};", //
            "a$b$c.prototype = { ",
            "  get logger() {return goog$debug$Logger.getLogger('a.b.c')}",
            "}"),
        lines(
            "let a$b$c = function() {};", //
            "a$b$c.prototype = {}"));
  }

  @Test
  public void testLoggerDefinedInPrototype4() {
    test(
        lines(
            "a.b.c = function() {}",
            "a.b.c.prototype = { ",
            "  set logger(a) {this.x = goog.debug.Logger.getLogger('a.b.c')}",
            "}"),
        lines(
            "a.b.c = function() {}", //
            "a.b.c.prototype = {}"));
    test(
        lines(
            "let a$b$c = function() {};", //
            "a$b$c.prototype = { ",
            "  set logger(a) {this.x = goog$debug$Logger.getLogger('a.b.c')}",
            "}"),
        lines(
            "let a$b$c = function() {};", //
            "a$b$c.prototype = {}"));
  }

  @Test
  public void testLoggerDefinedInPrototype5() {
    test(
        lines(
            "a.b.c = function() {}",
            "a.b.c.prototype = { ",
            "  get f() {return this.x;},",
            "  set f(a) {this.x = goog.debug.Logger.getLogger('a.b.c')}",
            "}"),
        lines(
            "a.b.c = function() {}",
            "a.b.c.prototype = { ",
            "  get f() {return this.x;},",
            "  set f(a) {this.x = null}",
            "}"));
    test(
        lines(
            "let a$b$c = function() {}",
            "a$b$c.prototype = { ",
            "  get f() {return this.x;},",
            "  set f(a) {this.x = goog$debug$Logger.getLogger('a.b.c')}",
            "}"),
        lines(
            "let a$b$c = function() {}",
            "a$b$c.prototype = { ",
            "  get f() {return this.x;},",
            "  set f(a) {this.x = null}",
            "}"));
  }

  @Test
  public void testLoggerDefinedInPrototype6() {
    test(
        lines(
            "var a = {};",
            "a.b = function() {};",
            "a.b.prototype = { ",
            "  logger(a) {this.x = goog.debug.Logger.getLogger('a.b.c')}",
            "}"),
        "var a = {}; a.b = function() {}; a.b.prototype = {}");
    test(
        lines(
            "var a = {};",
            "var a$b = function() {};",
            "a$b.prototype = { ",
            "  logger(a) {this.x = goog$debug$Logger.getLogger('a.b.c')}",
            "}"),
        "var a = {}; var a$b = function() {}; a$b.prototype = {}");
  }

  @Test
  public void testLoggerDefinedInPrototype7() {
    test(
        lines(
            "var a = {};",
            "a.b = function() {};",
            "a.b.prototype = { ",
            "  ['logger']: goog.debug.Logger.getLogger('a.b.c')",
            "}"),
        lines(
            "var a = {};",
            "a.b = function() {};",
            "a.b.prototype = { ",
            "  ['logger']: null",
            "}"));
    test(
        lines(
            "var a = {};",
            "var a$b = function() {};",
            "a$b.prototype = { ",
            "  ['logger']: goog$debug$Logger.getLogger('a.b.c')",
            "}"),
        lines(
            "var a = {};",
            "var a$b = function() {};",
            "a$b.prototype = { ",
            "  ['logger']: null",
            "}"));
  }

  @Test
  public void testLoggerDefinedStatically() {
    test(
        lines(
            "a.b.c = function() {}", //
            "a.b.c.logger = goog.debug.Logger.getLogger('a.b.c');"),
        "a.b.c=function(){}");
    test(
        lines(
            "let a$b$c = function() {}", //
            "a$b$c.logger = goog$debug$Logger.getLogger('a.b.c');"),
        "let a$b$c=function(){}");
  }

  @Test
  public void testDeletedScopesAreReported() {
    test("var nodeLogger = function () {};", "");
  }

  @Test
  public void testLoggerDefinedInObjectLiteral1() {
    test(
        lines(
            "a.b.c = {", //
            "  x: 0,",
            "  logger: goog.debug.Logger.getLogger('a.b.c')",
            "};"),
        "a.b.c={x:0}");
    test(
        lines(
            "let a$b$c = {", //
            "  x: 0,",
            "  logger: goog$debug$Logger.getLogger('a.b.c')",
            "};"),
        "let a$b$c={x:0}");
  }

  @Test
  public void testLoggerDefinedInObjectLiteral2() {
    test(
        lines(
            "a.b.c = {",
            "  x: 0,",
            "  get logger() {return goog.debug.Logger.getLogger('a.b.c')}",
            "};"),
        "a.b.c={x:0}");
    test(
        lines(
            "let a$b$c = {",
            "  x: 0,",
            "  get logger() {return goog$debug$Logger.getLogger('a.b.c')}",
            "};"),
        "let a$b$c={x:0}");
  }

  @Test
  public void testLoggerDefinedInObjectLiteral3() {
    test(
        lines(
            "a.b.c = {",
            "  x: null,",
            "  get logger() {return this.x},",
            "  set logger(a) {this.x  = goog.debug.Logger.getLogger(a)}",
            "};"),
        "a.b.c={x:null}");
    test(
        lines(
            "let a$b$c = {",
            "  x: null,",
            "  get logger() {return this.x},",
            "  set logger(a) {this.x  = goog$debug$Logger.getLogger(a)}",
            "};"),
        "let a$b$c={x:null}");
  }

  @Test
  public void testLoggerDefinedInObjectLiteral4() {
    test(
        lines(
            "a.b.c = {",
            "  x: null,",
            "  get y() {return this.x},",
            "  set y(a) {this.x  = goog.debug.Logger.getLogger(a)}",
            "};"),
        lines(
            "a.b.c = {",
            "  x: null,",
            "  get y() {return this.x},",
            "  set y(a) {this.x  = null}",
            "};"));
    test(
        lines(
            "let a$b$c = {",
            "  x: null,",
            "  get y() {return this.x},",
            "  set y(a) {this.x  = goog$debug$Logger.getLogger(a)}",
            "};"),
        lines(
            "let a$b$c = {",
            "  x: null,",
            "  get y() {return this.x},",
            "  set y(a) {this.x  = null}",
            "};"));
  }

  @Test
  public void testLoggerDefinedInObjectLiteral5() {
    test(
        lines(
            "var a = {};",
            "a.b = {",
            "  x: 0,",
            "  logger() { return goog.debug.Logger.getLogger('a.b.c'); }",
            "};"),
        "var a = {}; a.b={x:0}");
    test(
        lines(
            "var a = {};",
            "var a$b = {",
            "  x: 0,",
            "  logger() { return goog$debug$Logger.getLogger('a.b.c'); }",
            "};"),
        "var a = {}; var a$b={x:0}");
  }

  @Test
  public void testLoggerDefinedInObjectLiteral6() {
    test(
        lines(
            "var a = {};",
            "a.b = {",
            "  x: 0,",
            "  ['logger']: goog.debug.Logger.getLogger('a.b.c')",
            "};",
            "a.b['logger']();"),
        lines(
            "var a = {};",
            "a.b = {",
            "  x: 0,",
            "  ['logger']: null", // Don't strip computed properties
            "};",
            "a.b['logger']()"));
    test(
        lines(
            "var a = {};",
            "var a$b = {",
            "  x: 0,",
            "  ['logger']: goog$debug$Logger.getLogger('a.b.c')",
            "};",
            "a$b['logger']();"),
        lines(
            "var a = {};",
            "var a$b = {",
            "  x: 0,",
            "  ['logger']: null", // Don't strip computed properties
            "};",
            "a$b['logger']()"));
  }

  @Test
  public void testLoggerDefinedInPrototypeAndUsedInConstructor() {
    test(
        lines(
            "a.b.c = function(level) {",
            "  if (!this.logger.isLoggable(level)) {",
            "    this.logger.setLevel(level);",
            "  }",
            "  this.logger.log(level, 'hi');",
            "};",
            "a.b.c.prototype.logger = goog.debug.Logger.getLogger('a.b.c');",
            "a.b.c.prototype.go = function() { this.logger.finer('x'); };"),
        lines(
            "a.b.c=function(level){if(!null);}", //
            "a.b.c.prototype.go=function(){}"));
    test(
        lines(
            "let a$b$c = function(level) {",
            "  if (!this.logger.isLoggable(level)) {",
            "    this.logger.setLevel(level);",
            "  }",
            "  this.logger.log(level, 'hi');",
            "};",
            "a$b$c.prototype.logger = goog$debug$Logger.getLogger('a.b.c');",
            "a$b$c.prototype.go = function() { this.logger.finer('x'); };"),
        lines(
            "let a$b$c=function(level){if(!null);}", //
            "a$b$c.prototype.go=function(){}"));
  }

  @Test
  public void testLoggerDefinedStaticallyAndUsedInConstructor() {
    test(
        lines(
            "a.b.c = function(level) {",
            "  if (!a.b.c.logger.isLoggable(level)) {",
            "    a.b.c.logger.setLevel(level);",
            "  }",
            "  a.b.c.logger.log(level, 'hi');",
            "};",
            "a.b.c.logger = goog.debug.Logger.getLogger('a.b.c');"),
        "a.b.c=function(level){if(!null);}");
    test(
        lines(
            "let a$b$c = function(level) {",
            "  if (!a$b$c$logger.isLoggable(level)) {",
            "    a$b$c$logger.setLevel(level);",
            "  }",
            "  a$b$c$logger.log(level, 'hi');",
            "};",
            "let a$b$c$logger = goog$debug$Logger.getLogger('a.b.c');"),
        lines(
            "let a$b$c=function(level){if(!null);}", //
            "let a$b$c$logger = null"));
  }

  @Test
  public void testLoggerVarDeclaration() {
    test("var logger = opt_logger || goog.debug.LogManager.getRoot();", "");
    test("var logger = opt_logger || goog$debug$LogManager.getRoot();", "");
  }

  @Test
  public void testLoggerLetDeclaration() {
    test("let logger = opt_logger || goog.debug.LogManager.getRoot();", "");
    test("let logger = opt_logger || goog$debug$LogManager.getRoot();", "");
  }

  @Test
  public void testLoggerConstDeclaration() {
    test("const logger = opt_logger || goog.debug.LogManager.getRoot();", "");
    test("const logger = opt_logger || goog$debug$LogManager.getRoot();", "");
  }

  @Test
  public void testLoggerDestructuringDeclaration() {
    // NOTE: StripCode currently does not optimize code in destructuring patterns, even if
    // it contains "strip names" or "strip types", since it's unclear what the correct optimization
    // would be or how much it would help users.

    testSame("const {Logger} = goog.debug; Logger;");
    testSame("const {Logger} = goog$debug; Logger;");
    testSame("const {Logger: logger} = goog.debug; logger;");
    testSame("const {Logger: logger} = goog$debug; logger;");
    testSame("const [logger] = [1]; logger;");

    test(
        "const {getLogger} = goog.debug.Logger; const logger = opt_logger || getLogger('A');",
        "const {getLogger} = goog.debug.Logger;");
    test(
        "const {getLogger} = goog$debug$Logger; const logger = opt_logger || getLogger('A');",
        "const {getLogger} = goog$debug$Logger;");
  }

  @Test
  public void testLoggerMethodCallByVariableType_var() {
    test("var x = goog.debug.Logger.getLogger('a.b.c'); y.info(a); x.info(a);", "y.info(a)");
    test("var x = goog$debug$Logger.getLogger('a.b.c'); y.info(a); x.info(a);", "y.info(a)");
  }

  @Test
  public void testLoggerMethodCallByVariableType_let() {
    test("let x = goog.debug.Logger.getLogger('a.b.c'); y.info(a); x.info(a);", "y.info(a)");
    test("let x = goog$debug$Logger.getLogger('a.b.c'); y.info(a); x.info(a);", "y.info(a)");
  }

  @Test
  public void testLoggerMethodCallByVariableType_const() {
    test("const x = goog.debug.Logger.getLogger('a.b.c'); y.info(a); x.info(a);", "y.info(a)");
    test("const x = goog$debug$Logger.getLogger('a.b.c'); y.info(a); x.info(a);", "y.info(a)");
  }

  @Test
  public void testSubPropertyAccessByVariableName_var() {
    test(
        lines(
            "var x, y = goog.debug.Logger.getLogger('a.b.c')",
            "var logger = x;",
            "var curlevel = logger.level_ ? logger.getLevel().name : 3;"),
        "var x;var curlevel=null?null:3");
    test(
        lines(
            "var x, y = goog$debug$Logger.getLogger('a.b.c')",
            "var logger = x;",
            "var curlevel = logger.level_ ? logger.getLevel().name : 3;"),
        "var x;var curlevel=null?null:3");
  }

  @Test
  public void testSubPropertyAccessByVariableName_let() {
    test(
        lines(
            "let x, y = goog.debug.Logger.getLogger('a.b.c');",
            "let logger = x;",
            "let curlevel = logger.level_ ? logger.getLevel().name : 3;"),
        "let x; let curlevel=null?null:3");
    test(
        lines(
            "let x, y = goog$debug$Logger.getLogger('a.b.c');",
            "let logger = x;",
            "let curlevel = logger.level_ ? logger.getLevel().name : 3;"),
        "let x; let curlevel=null?null:3");
  }

  @Test
  public void testSubPropertyAccessByVariableName_const() {
    test(
        lines(
            "const x = undefined, y = goog.debug.Logger.getLogger('a.b.c');",
            "const logger = x;",
            "const curlevel = logger.level_ ? logger.getLevel().name : 3;"),
        "const x = undefined; const curlevel=null?null:3");
    test(
        lines(
            "const x = undefined, y = goog$debug$Logger.getLogger('a.b.c');",
            "const logger = x;",
            "const curlevel = logger.level_ ? logger.getLevel().name : 3;"),
        "const x = undefined; const curlevel=null?null:3");
  }

  @Test
  public void testPrefixedVariableName() {
    test(
        lines(
            "this.blcLogger_ = goog.debug.Logger.getLogger('a.b.c')",
            "this.blcLogger_.fine('Raised dirty states.');"),
        "");
    test(
        lines(
            "this.blcLogger_ = goog$debug$Logger.getLogger('a.b.c')",
            "this.blcLogger_.fine('Raised dirty states.');"),
        "");
  }

  @Test
  public void testPrefixedPropertyName() {
    test(
        lines(
            "a.b.c.staticLogger_ = goog.debug.Logger.getLogger('a.b.c')",
            "a.b.c.staticLogger_.fine('-' + a.b.c.d_())"),
        "");
    test(
        lines(
            "a$b$c$staticLogger_ = goog$debug$Logger.getLogger('a.b.c')",
            "a$b$c$staticLogger_.fine('-' + a$b$c$d_())"),
        "");
  }

  @Test
  public void testPrefixedClassName() {
    test(
        lines(
            "a.b.MyLogger = function(logger) {",
            "  this.logger_ = logger;",
            "};",
            "a.b.MyLogger.prototype.shout = function(msg, opt_x) {",
            "  this.logger_.log(goog.debug.Logger.Level.SHOUT, msg, opt_x);",
            "};"),
        lines(
            "a.b.MyLogger=function(logger){}",
            "a.b.MyLogger.prototype.shout=function(msg,opt_x){}"));
    test(
        lines(
            "a$b$MyLogger = function(logger) {",
            "  this.logger_ = logger;",
            "};",
            "a$b$MyLogger.prototype.shout = function(msg, opt_x) {",
            "  this.logger_.log(goog$debug$Logger.Level.SHOUT, msg, opt_x);",
            "};"),
        lines(
            "a$b$MyLogger=function(logger){}",
            "a$b$MyLogger.prototype.shout=function(msg,opt_x){}"));
  }

  @Test
  public void testLoggerClassDefinition() {
    test("goog.debug.Logger=function(name){this.name_=name}", "");
    test("goog$debug$Logger=function(name){this.name_=name}", "");
  }

  @Test
  public void testStaticLoggerPropertyDefinition() {
    test(
        lines(
            "goog.debug.Logger.Level.SHOUT=", //
            "new goog.debug.Logger.Level(x,1200)"),
        "");
    test(
        lines(
            "goog$debug$Logger$Level$SHOUT=", //
            "new goog$debug$Logger$Level(x,1200)"),
        "");
  }

  @Test
  public void testStaticLoggerMethodDefinition() {
    test(
        lines(
            "goog.debug.Logger.getLogger=function(name){",
            "return goog.debug.LogManager.getLogger(name)",
            "};"),
        "");
    test(
        lines(
            "goog$debug$Logger$getLogger=function(name){",
            "return goog$debug$LogManager$getLogger(name)",
            "};"),
        "");
  }

  @Test
  public void testPrototypeFieldDefinition() {
    test("goog.debug.Logger.prototype.level_=null;", "");
    test("goog$debug$Logger.prototype.level_=null;", "");
  }

  @Test
  public void testPrototypeFieldDefinitionWithoutAssignment() {
    test("goog.debug.Logger.prototype.level_;", "");
    test("goog$debug$Logger.prototype.level_;", "");
  }

  @Test
  public void testPrototypeMethodDefinition() {
    test(
        lines(
            "goog.debug.Logger.prototype.addHandler=",
            "function(handler){this.handlers_.push(handler)};"),
        "");
    test(
        lines(
            "goog$debug$Logger.prototype.addHandler=",
            "function(handler){this.handlers_.push(handler)};"),
        "");
  }

  @Test
  public void testPublicPropertyAssignment() {
    // Eliminate property assignments on vars/properties that we
    // remove as otherwise we create invalid code.
    test("goog.debug.Logger = 1; goog.debug.Logger.prop=2; ", "");
    test("goog$debug$Logger = 1; goog$debug$Logger.prop=2; ", "");
    test("this.blcLogger_.level=x", "");
    test("goog.ui.Component.logger.prop=y", "");
    test("goog$ui$Component$logger$prop=y", "");
    test("goog.ui.Component.logger.prop.foo.bar=baz", "");
    test("goog$ui$Component$logger$prop$foo$bar=baz", "");
  }

  @Test
  public void testGlobalCallWithStrippedType() {
    testSame("window.alert(goog.debug.Logger)");
    testSame("window.alert(goog$debug$Logger)");
  }

  @Test
  public void testClassDefiningCallWithStripType1() {
    test("goog.debug.Logger.inherits(Object)", "");
    test("goog$debug$Logger.inherits(Object)", "");
  }

  @Test
  public void testClassDefiningCallWithStripType2() {
    test(
        lines(
            "goog.formatter=function(){};", //
            "goog.inherits(goog.debug.Formatter,goog.formatter)"),
        "goog.formatter=function(){}");
    test(
        lines(
            "let goog$formatter=function(){};", //
            "goog$inherits(goog$debug$Formatter,goog$formatter)"),
        "let goog$formatter=function(){}");
  }

  @Test
  public void testClassDefiningCallWithStripType3() {
    testError(
        lines(
            "goog.formatter=function(){}", //
            "goog.inherits(goog.formatter,goog.debug.Formatter)"),
        StripCode.STRIP_TYPE_INHERIT_ERROR);
    testError(
        lines(
            "let goog$formatter=function(){}", //
            "goog$inherits(goog$formatter,goog$debug$Formatter)"),
        StripCode.STRIP_TYPE_INHERIT_ERROR);
  }

  @Test
  public void testClassDefiningCallWithStripType4() {
    testSame("goog.formatter=function(){}; goog.formatter.inherits(goog.debug.FormatterFoo)");
    testSame("let goog$formatter=function(){}; goog$formatter$inherits(goog$debug$FormatterFoo)");
  }

  @Test
  public void testClassDefiningCallWithStripType5() {
    test("goog.inherits(goog.debug.TextFormatter, goog.debug.Formatter)", "");
    test("goog$inherits(goog$debug$TextFormatter, goog$debug$Formatter)", "");
  }

  @Test
  public void testClassDefiningCallWithStripType6() {
    // listed types should be removed.
    test("goog.debug.DebugWindow = function(){}", "");
    test("goog$debug$DebugWindow = function(){}", "");
    test("goog.inherits(goog.debug.DebugWindow,Base)", "");
    test("goog$inherits(goog$debug$DebugWindow,Base)", "");
    test("goog.debug.DebugWindow = class {}", "");
    test("goog$debug$DebugWindow = class {}", "");
    test("class GA_GoogleDebugger {}", "");
    test("if (class GA_GoogleDebugger {}) {}", "if (null) {}");

    // types that happen to have strip types as prefix should not be
    // stripped.
    testSame("goog.debug.DebugWindowFoo=function(){}");
    testSame("goog$debug$DebugWindowFoo=function(){}");
    testSame("goog.inherits(goog.debug.DebugWindowFoo,Base)");
    testSame("goog$inherits(goog$debug$DebugWindowFoo,Base)");
    testSame("goog.debug.DebugWindowFoo");
    testSame("goog$debug$DebugWindowFoo");
    testSame("goog.debug.DebugWindowFoo=1");
    testSame("goog$debug$DebugWindowFoo=1");
    testSame("goog.debug.DebugWindowFoo = class {}");
    testSame("goog$debug$DebugWindowFoo = class {}");

    // qualified subtypes should be removed.
    test("goog.debug.DebugWindow.Foo=function(){}", "");
    test("goog$debug$DebugWindow$Foo=function(){}", "");
    test("goog.inherits(goog.debug.DebugWindow.Foo,Base)", "");
    test("goog$inherits(goog$debug$DebugWindow$Foo,Base)", "");
    test("goog.debug.DebugWindow.Foo", "");
    test("goog$debug$DebugWindow$Foo", "");
    test("goog.debug.DebugWindow.Foo=1", "");
    test("goog$debug$DebugWindow$Foo=1", "");
    test("goog.debug.DebugWindow.Foo = class {}", "");
    test("goog$debug$DebugWindow$Foo = class {}", "");
  }

  @Test
  public void testStrippedES5ChildClass() {
    test(
        lines(
            "goog.debug.Logger.Child = function() {};", //
            "goog.inherits(goog.debug.Logger.Child, goog.debug.Logger);"),
        "");
    test(
        lines(
            "var goog$debug$Logger$Child = function() {};", //
            "goog$inherits(goog$debug$Logger$Child, goog$debug$Logger);"),
        "var goog$debug$Logger$Child = null;");
  }

  @Test
  public void testClassInheritanceFromStripType1() {
    // Formatter is not a strip name or type, so cannot extend a strip type.
    testError(
        "class Formatter extends goog.debug.Formatter {}", StripCode.STRIP_TYPE_INHERIT_ERROR);
    testError(
        "class Formatter extends goog$debug$Formatter {}", StripCode.STRIP_TYPE_INHERIT_ERROR);
  }

  @Test
  public void testClassInheritanceFromStripType2() {
    testError(
        "let Formatter = class extends goog.debug.Formatter {}",
        StripCode.STRIP_TYPE_INHERIT_ERROR);
    testError(
        "let Formatter = class extends goog$debug$Formatter {}",
        StripCode.STRIP_TYPE_INHERIT_ERROR);
  }

  @Test
  public void testClassInheritanceFromStripType3() {
    // Both subclass and superclass are strip types, so this is okay.
    test("goog.debug.HtmlFormatter = class extends goog.debug.Formatter {}", "");
    test(
        "let goog$debug$HtmlFormatter = class extends goog$debug$Formatter {}",
        "let goog$debug$HtmlFormatter = null;");
  }

  @Test
  public void testPropertyWithEmptyStringKey() {
    test(
        "goog.format.NUMERIC_SCALES_BINARY_ = {'': 1};",
        "goog.format.NUMERIC_SCALES_BINARY_={\"\":1}");
    test(
        "let goog$format$NUMERIC_SCALES_BINARY_ = {'': 1};",
        "let goog$format$NUMERIC_SCALES_BINARY_={\"\":1}");
  }

  @Test
  public void testVarinIf() {
    test("if(x)var logger=null;else foo()", "if(x);else foo()");
  }

  @Test
  public void testGetElemInIf() {
    test("var logger=null;if(x)logger[f];else foo()", "if(x);else foo()");
  }

  @Test
  public void testAssignInIf() {
    test("var logger=null;if(x)logger=1;else foo()", "if(x);else foo()");
  }

  @Test
  public void testNamePrefix() {
    test(
        lines(
            "a = function(traceZZZ) {}; a.prototype.traceXXX = {x: 1}",
            "a.prototype.z = function() { this.traceXXX.f(); };",
            "var traceYYY = 0;"),
        "a=function(traceZZZ){};a.prototype.z=function(){}");
  }

  @Test
  public void testTypePrefix() {
    testSame(
        lines(
            "e.f.TraceXXX = function() {};", //
            "e.f.TraceXXX.prototype.yyy = 2;"));
    testSame(
        lines(
            "let e$f$TraceXXX = function() {};", //
            "e$f$TraceXXX.prototype.yyy = 2;"));
  }

  @Test
  public void testStripCallsToStrippedNames1() {
    test(
        lines(
            "a = function() { this.logger_ = function(msg){}; }",
            "a.prototype.b = function() { this.logger_('hi'); }"),
        "a=function(){};a.prototype.b=function(){}");
    test(
        lines(
            "a = function() {}",
            "a.prototype.logger_ = function(msg) {};",
            "a.prototype.b = function() { this.logger_('hi'); }"),
        "a=function(){};a.prototype.b=function(){}");
  }

  @Test
  public void testStripCallsToStrippedNames2() {
    test(
        lines(
            "a = function() {}",
            "a.prototype.logger_ = function(msg) {};",
            "a.prototype.b = function() { this.logger_('hi'); }"),
        "a=function(){};a.prototype.b=function(){}");
  }

  @Test
  public void testStripCallsToStrippedNames3() {
    test(
        lines(
            "a = function() { this.logger_ = function(msg){}; }",
            "a.prototype.b = function() { this.logger_('hi').foo = 2; }"),
        "a=function(){};a.prototype.b=function(){2;}");
  }

  @Test
  public void testStripCallsToStrippedNames4() {
    test("a = this.logger_().foo;", "a = null;");
  }

  @Test
  public void testStripVarsInitializedFromStrippedNames1() {
    test(
        lines(
            "a = function() { this.logger_ = function() { return 1; }; }",
            "a.prototype.b = function() { ",
            "  var one = this.logger_(); if (one) foo() }"),
        "a=function(){};a.prototype.b=function(){if(null)foo()}");
  }

  @Test
  public void testStripVarsInitializedFromStrippedNames2() {
    test(
        lines(
            "a = function() { this.logger_ = function() { return 1; }; }",
            "a.prototype.b = function() { ",
            "  var one = this.logger_.foo.bar(); if (one) foo() }"),
        "a=function(){};a.prototype.b=function(){if(null)foo()}");
  }

  @Test
  public void testReportErrorOnStripInNestedAssignment() {
    // Strip name
    testError("(foo.logger_ = 7) + 8", StripCode.STRIP_ASSIGNMENT_ERROR);

    // Strip namespaced type
    testError("(goog.debug.Logger.foo = 7) + 8", StripCode.STRIP_ASSIGNMENT_ERROR);
    testError("(goog$debug$Logger$foo = 7) + 8", StripCode.STRIP_ASSIGNMENT_ERROR);

    // Strip non-namespaced type
    testError("(GA_GoogleDebugger.foo = 7) + 8", StripCode.STRIP_ASSIGNMENT_ERROR);
    testError("(GA_GoogleDebugger$foo = 7) + 8", StripCode.STRIP_ASSIGNMENT_ERROR);
  }

  @Test
  public void testNewOperator1() {
    test(
        "function foo() {} foo.bar = new goog.debug.Logger();",
        "function foo() {} foo.bar = null;");
    test(
        "function foo() {} foo.bar = new goog$debug$Logger();",
        "function foo() {} foo.bar = null;");
  }

  @Test
  public void testNewOperator2() {
    test(
        "function foo() {} foo.bar = (new goog.debug.Logger()).foo();",
        "function foo() {} foo.bar = null;");
    test(
        "function foo() {} let foo$bar = (new goog$debug$Logger()).foo();",
        "function foo() {} let foo$bar = null;");
  }

  @Test
  public void testNewOperator3() {
    test("(new goog.debug.Logger()).foo().bar = 2;", "2;");
    test("(new goog$debug$Logger()).foo().bar = 2;", "2;");
  }

  @Test
  public void testCrazyNesting1() {
    test("var x = {}; x[new goog.debug.Logger()] = 3;", "var x = {}; x[null] = 3;");
    test("var x = {}; x[new goog$debug$Logger()] = 3;", "var x = {}; x[null] = 3;");
  }

  @Test
  public void testCrazyNesting2() {
    test("var x = {}; x[goog.debug.Logger.getLogger()] = 3;", "var x = {}; x[null] = 3;");
    test("var x = {}; x[goog$debug$Logger.getLogger()] = 3;", "var x = {}; x[null] = 3;");
  }

  @Test
  public void testCrazyNesting3() {
    test("var x = function() {}; x(new goog.debug.Logger());", "var x = function() {}; x(null);");
    test("var x = function() {}; x(new goog$debug$Logger());", "var x = function() {}; x(null);");
  }

  @Test
  public void testCrazyNesting4() {
    test(
        "var x = function() {}; x(goog.debug.Logger.getLogger());",
        "var x = function() {}; x(null);");
    test(
        "var x = function() {}; x(goog$debug$Logger.getLogger());",
        "var x = function() {}; x(null);");
  }

  @Test
  public void testCrazyNesting5() {
    test(
        lines(
            "var x = function() {}; var y = {}; ",
            "var z = goog.debug.Logger.getLogger(); x(y[z['foo']]);"),
        "var x = function() {}; var y = {}; x(y[null]);");
    test(
        lines(
            "var x = function() {}; var y = {}; ",
            "var z = goog$debug$Logger.getLogger(); x(y[z['foo']]);"),
        "var x = function() {}; var y = {}; x(y[null]);");
  }

  @Test
  public void testNamespace1() {
    test("var x = {};x.traceutil = {};x.traceutil.FOO = 1;", "var x = {};");
  }

  @Test
  public void testMethodCallTriggersRemoval() {
    test("this.logger_.foo.bar();", "");
  }

  @Test
  public void testRemoveExpressionByName() {
    test("this.logger_.foo.bar;", "");
  }

  @Test
  public void testAliasOfRemovedVar() {
    test(
        "var logger_ = goog.debug.Logger.getLogger(); var alias; alias = logger_;",
        "                                             var alias; alias = null;");
    test(
        "var logger_ = goog$debug$Logger.getLogger(); var alias; alias = logger_;",
        "                                             var alias; alias = null;");
  }

  @Test
  public void testComplexExpression() {
    test(
        "var logger_ = goog.debug.Logger.getLogger(); var alias; alias = (logger_ = 3) + 4;",
        "                                             var alias; alias = 3 + 4;");
    test(
        "var logger_ = goog$debug$Logger.getLogger(); var alias; alias = (logger_ = 3) + 4;",
        "                                             var alias; alias = 3 + 4;");
  }

  @Test
  public void testStrippedFunctionCallAssignedToNonStrippedStaticField() {
    test(
        lines(
            "a.b.C = function() {", //
            "  this.nonStripped_ = a.b.C.nonStripped_;",
            "}",
            "a.b.C.prototype.method = function() {",
            "  goog.log(this.nonStripped_, 'some message');",
            "}",
            "a.b.C.nonStripped_ = goog.debug.Logger.getLogger();"),
        lines(
            "a.b.C = function() {", //
            "  this.nonStripped_ = a.b.C.nonStripped_;",
            "}",
            "a.b.C.prototype.method = function() {",
            "}",
            "a.b.C.nonStripped_ = null;",
            ""));
    test(
        lines(
            "let a$b$C = function() {", //
            "  this.nonStripped_ = a$b$C$nonStripped_;",
            "}",
            "a$b$C.prototype.method = function() {",
            "  goog$log(this.nonStripped_, 'some message');",
            "}",
            "var a$b$C$nonStripped_ = goog$debug$Logger$getLogger();"),
        lines(
            "let a$b$C = function() {", //
            "  this.nonStripped_ = a$b$C$nonStripped_;",
            "}",
            "a$b$C.prototype.method = function() {",
            "}",
            "var a$b$C$nonStripped_ = null;",
            ""));
  }

  @Test
  public void testVarsIntroducedByCollapsePropertiesInitializedToNull() {
    // There's known code where `a.b.strippableName` appears in the AST earlier
    // than where it is assigned a value. CollapseProperties will turn this into
    // a variable before StripCode sees it, so the reference will appear in the AST
    // earlier than the declaration.
    //
    // For such cases, we need to make sure we initialize the variable
    // to `null` instead of removing the declaration. Otherwise, we'll get an
    // error later due to the reference to a variable without a declaration.
    //
    // It would be nicer if we replaced all references to the variable with `null`
    // and removed its declaration, but doing that makes some other things we
    // want to remove harder to recognize when we visit them. (e.g. `goog$inherits(null, null)`
    // where we need to see `goog$inherits(strippable$Child, strippable$Parent)`).
    test(
        lines(
            "let a$b$C = function() {", //
            "  this.nonStripped_ = a$b$C$logger_;",
            "}",
            "a$b$C.prototype.method = function() {",
            "  goog$log(this.nonStripped_, 'some message');",
            "  this.obj[a$b$C$logger_];",
            "}",
            "var a$b$C$logger_ = {};"),
        lines(
            "let a$b$C = function() {", //
            "  this.nonStripped_ = a$b$C$logger_;",
            "}",
            "a$b$C.prototype.method = function() {",
            "  this.obj[a$b$C$logger_];",
            "}",
            "var a$b$C$logger_ = null;",
            ""));
    // Also handle the case where there's no initial value
    // assigned to the variable.
    test(
        lines(
            "let a$b$C = function() {", //
            "  this.nonStripped_ = a$b$C$logger_;",
            "}",
            "a$b$C.prototype.method = function() {",
            "  goog$log(this.nonStripped_, 'some message');",
            "  this.obj[a$b$C$logger_];",
            "}",
            "var a$b$C$logger_;"),
        lines(
            "let a$b$C = function() {", //
            "  this.nonStripped_ = a$b$C$logger_;",
            "}",
            "a$b$C.prototype.method = function() {",
            "  this.obj[a$b$C$logger_];",
            "}",
            "var a$b$C$logger_ = null;",
            ""));
  }
}
