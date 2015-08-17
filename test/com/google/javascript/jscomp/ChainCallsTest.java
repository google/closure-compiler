/*
 * Copyright 2009 The Closure Compiler Authors.
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
 * Tests for {@link ChainCalls}
 *
 */
public final class ChainCallsTest extends CompilerTestCase {
  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new ChainCalls(compiler);
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testUnchainedCalls() {
    test(
        ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.bar = function() { return this; };\n"
        + "var f = new Foo();\n"
        + "f.bar();\n"
        + "f.bar();\n",
        ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.bar = function() { return this; };\n"
        + "var f = new Foo();\n"
        + "f.bar().bar();\n");

  }

  public void testSecondCallReturnNotThis() {
    test(
        ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.bar = function() { return this; };\n"
        + "Foo.prototype.baz = function() {};\n"
        + "var f = new Foo();\n"
        + "f.bar();\n"
        + "f.baz();\n",
        ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.bar = function() { return this; };\n"
        + "Foo.prototype.baz = function() {};\n"
        + "var f = new Foo();\n"
        + "f.bar().baz();\n");
  }

  public void testCallInIfInFunction() {
    test(
        LINE_JOINER.join(
            "function blah() {",
            "  /** @constructor */ function Foo() {}",
            "  Foo.prototype.bar = function() { return this; };",
            "  var f = new Foo();",
            "  if (true) {f.bar(); f.bar()}",
            "}"),
        LINE_JOINER.join(
            "function blah() {",
            "  /** @constructor */ function Foo() {}",
            "  Foo.prototype.bar = function() { return this; };",
            "  var f = new Foo();",
            "  if (true) {f.bar().bar()}",
            "}"));
  }

  public void testDifferentInstance() {
    testSame(
        ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.bar = function() { return this; };\n"
        + "new Foo().bar();\n"
        + "new Foo().bar();\n");
  }

  public void testSubclass() {
    testSame(
        ""
        + "/** @constructor */ function Foo() {}\n"
        + "Foo.prototype.bar = function() { return this; };\n"
        + "/** @constructor\n@extends {Foo} */ function Baz() {}\n"
        + "Baz.prototype.bar = function() {};\n"
        + "/** @type {Foo} */ (new Baz()).bar();\n"
        + "/** @type {Foo} */ (new Baz()).bar();\n");
  }

  public void testSimpleDefinitionFinder() {
    String defs =
        "/** @constructor */ function Foo() {}\n" +
        "Foo.prototype.a = function() { return this; };" +
        "/** @constructor */ function Bar() {}\n" +
        "Bar.prototype.a = function() {};";
    testSame(
        defs +
        "var o = new Foo; o.a(); o.a();");
    testSame(
        defs +
        "var o = new Bar; o.a(); o.a();");
  }

  public void testSimpleDefinitionFinder2() {
    String defs =
        "/** @constructor */ function Foo() {}\n" +
        "Foo.prototype.a = function() { return this; };" +
        "/** @constructor */ function Bar() {}\n" +
        "Bar.prototype.a = function() { return this; };";
    testSame(
        defs +
        "var o = new Foo; o.a().a();");
    testSame(
        defs +
        "var o = new Bar; o.a().a();");
  }
}
