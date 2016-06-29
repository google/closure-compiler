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

/**
 * Tests for {@link RemoveSuperMethodsPass}
 */
public final class RemoveSuperMethodsPassTest extends CompilerTestCase {

  public RemoveSuperMethodsPassTest() {
    super(DEFAULT_EXTERNS + "\n" + LINE_JOINER.join(
        "/** @constructor */",
        "var FooBase = function() {};",
        "FooBase.prototype.bar = function() {};",
        "/** @param {number} time */",
        "/** @param {number} loc */",
        "/** @return {number} */",
        "FooBase.prototype.baz = function(time, loc) { return 2; }",
        "/** @param {number} time */",
        "/** @param {number=} opt_loc */",
        "FooBase.prototype.buzz = function(time, opt_loc) {} ",
        "/** @constructor @extends {FooBase} */",
        "var Foo = function() {}",
        "Foo.superClass_ = FooBase.prototype"));
    enableTypeCheck();
  }

  @Override
  public CompilerPass getProcessor(final Compiler compiler) {
    return new RemoveSuperMethodsPass(compiler);
  }

  public void testOptimize_noArgs() {
    test(
        LINE_JOINER.join(
            "/** @override */",
            "Foo.prototype.bar = function() { Foo.superClass_.bar.call(this); };"),
        "");
  }

  public void testOptimize_baseClassName_noArgs() {
    enableTypeCheck();
    test(
        LINE_JOINER.join(
            "/** @override */",
            "Foo.prototype.bar = function() { FooBase.prototype.bar.call(this) };"),
        "");
  }

  public void testOptimize_twoArgs() {
    test(
        LINE_JOINER.join(
            "/** @override */",
            "Foo.prototype.baz = function(time, loc) {",
            "  return Foo.superClass_.baz.call(this, time, loc);",
            "};"),
        "");
  }

  public void testNoOptimize_numArgsMismatch() {
    testSame(
        LINE_JOINER.join(
            "/** @override */",
            "Foo.prototype.buzz = function(time, opt_loc) {",
            "  return Foo.superClass_.buzz.call(this, time);",
            "};"));
  }

  public void testNoOptimize_notBaseClass() {
    testSame(
        LINE_JOINER.join(
            "/** @override */",
            "Foo.prototype.bar = function() { FooBaz.prototype.bar.call(this) };"));
  }

  public void testNoOptimize_argOrderMismatch() {
    testSame(
        LINE_JOINER.join(
            "/** @override */",
            "Foo.prototype.baz = function(time, loc) {",
            "  return Foo.superClass_.baz.call(this, loc, time);",
            "};"));
  }

  public void testNoOptimize_argNameMismatch() {
    testSame(
        LINE_JOINER.join(
            "/** @override */",
            "Foo.prototype.baz = function(time, loc) {",
            "  return Foo.superClass_.baz.call(this, time + 2, loc);",
            "};"));
  }

  public void testNoOptimize_methodNameMismatch() {
    testSame(
        LINE_JOINER.join(
            "/** @override */",
            "Foo.prototype.baz = function(time, loc) {",
            "  return Foo.superClass_.buzz.call(this, time, loc);",
            "};"));
  }

  public void testNoOptimize_moreThanOneStatement() {
    testSame(
        LINE_JOINER.join(
            "/** @override */",
            "Foo.prototype.baz = function(time, loc) {",
            "  this.bar();",
            "  return Foo.superClass_.baz.call(this, time, loc);",
            "};"));
  }

  public void testNoOptimize_missingReturn() {
    testSame(
        LINE_JOINER.join(
            "/** @override */",
            "Foo.prototype.baz = function(time, loc) {",
            "  FooBase.prototype.baz.call(this, time, loc);",
            "};"));
  }
}
