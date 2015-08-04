/*
 * Copyright 2015 The Closure Compiler Authors.
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
 * Tests for {@link RewriteBindThis}.
 *
 */
public final class RewriteBindThisTest extends Es6CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new RewriteBindThis(compiler);
  }

  public void testSimpleRewrite() {
    testEs6(LINE_JOINER.join(
        "someCall(function(){",
        "  something()",
        "}.bind(this))"),
        "someCall( ()=>{something()} )");
    testEs6(LINE_JOINER.join(
        "var x = function(){",
        "  return this.a;",
        "}.bind(this);"),
        "var x = ()=>{return this.a}");
  }

  public void testRewriteWithArg() {
    testEs6(LINE_JOINER.join(
        "someCall(function(msg) {",
        "  something(msg)",
        "}.bind(this))"),
        "someCall( msg=>{something(msg)} )");
    testEs6(LINE_JOINER.join(
        "var x = function(msg) {",
        "  something(msg)",
        "}.bind(this)"),
        "var x = msg=>{something(msg)} ");
  }

  public void testRemoveBindCallOnArrowFunction() {
    testEs6("var x = (()=>1).bind(this)",
        "var x = ()=>1");
  }

  public void testNoRewriteWithNamedFunction() {
    testSameEs6("(function x(){this.a}).bind(this);");
    testSameEs6("var x = function(){this.a}; x.bind(this);");
  }

  public void testNoRewriteWithArgumentsCall() {
    testSameEs6(LINE_JOINER.join(
        "someCall(function() {",
        "  var a = arguments",
        "}.bind(this))"));
    testSameEs6(LINE_JOINER.join(
        "someCall(function() {",
        "  var a = arguments[0]",
        "}.bind(this))"));
    testSameEs6(LINE_JOINER.join(
        "someCall(function() {",
        "  var a = arguments.callee",
        "}.bind(this))"));
    testSameEs6(LINE_JOINER.join(
        "someCall(function() {",
        "  var a = arguments.length",
        "}.bind(this))"));
  }

  public void testNestedBind1() {
    // Both don't refer arguments
    testEs6(LINE_JOINER.join(
        "var x = function() {",
        "  var y = function() {",
        "    return a;",
        "  }.bind(this);",
        "}.bind(this);"),
        "var x = ()=>{var y = ()=>{return a;}};");
    // Arguments-referred function inside non-referred function;
    testEs6(LINE_JOINER.join(
        "var x = function() {",
        "  var y = function() {",
        "    return arguments[0];",
        "  }.bind(this);",
        "}.bind(this);"),
        LINE_JOINER.join(
        "var x = ()=>{",
        "  var y = function() {",
        "    return arguments[0];",
        "  }.bind(this);",
        "}"));
    // Non-referred function inside arguments-referred function;
    testEs6(LINE_JOINER.join(
        "var x = function() {",
        "  var y = function() {",
        "    return a;",
        "  }.bind(this);",
        "  return arguments[0]",
        "}.bind(this);"),
        LINE_JOINER.join(
        "var x = function() {",
        "  var y = ()=>{return a;}",
        "  return arguments[0]",
        "}.bind(this);"));
    // Both referred arguments
    testSameEs6(LINE_JOINER.join(
        "var x = function() {",
        "  var y = function() {",
        "    return arguments[0];",
        "  }.bind(this);",
        "  return arguments[0]",
        "}.bind(this);"));
  }

  public void testNestedBind2() {
    // Arguments inside an arrow function nested in a function exp. The arguments
    // belongs to the function exp.
    testSameEs6(LINE_JOINER.join(
        "var x = function() {",
        "  var y = ()=>arguments[0]",
        "}.bind(this)"));

    testEs6(LINE_JOINER.join(
        "var x = function() {",
        "  var y = function() {",
        "    var y = ()=>arguments[0]",
        "  }.bind(this);",
        "}.bind(this)"),
        LINE_JOINER.join(
        "var x = ()=>{",
        "  var y = function() {",
        "    var y = ()=>arguments[0]",
        "  }.bind(this);",
        "}"));
  }

  public void testNestedBind3() {
    testEs6(LINE_JOINER.join(
        "var x = function() {",
        "  var y = (()=>1).bind(this)",
        "}.bind(this)"),
        "var x = ()=>{var y = ()=>1}");
  }
}
