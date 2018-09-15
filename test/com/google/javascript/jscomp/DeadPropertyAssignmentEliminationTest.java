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

import static com.google.javascript.jscomp.DeadPropertyAssignmentElimination.ASSUME_CONSTRUCTORS_HAVENT_ESCAPED;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DeadPropertyAssignmentEliminationTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableGatherExternProperties();
  }

  @Test
  public void testBasic() {
    testSame(lines(
        "var foo = function() {",
        "  this.a = 20;",
        "}"));

    test(
        lines(
            "var foo = function() {",
            "  this.a = 10;",
            "  this.a = 20;",
            "}"),
        lines(
            "var foo = function() {",
            "  10;",
            "  this.a = 20;",
            "}"));

    testSame(
        lines(
            "var foo = function() {",
            "  this.a = 20;",
            "  this.a = this.a + 20;",
            "}"));
  }

  @Test
  public void testMultipleProperties() {
    test(
        lines(
            "var foo = function() {",
            "  this.a = 10;",
            "  this.b = 15;",
            "  this.a = 20;",
            "}"),
        lines(
            "var foo = function() {",
            "  10;",
            "  this.b = 15;",
            "  this.a = 20;",
            "}"));
  }

  @Test
  public void testNonStandardAssign() {
    test(
        lines(
            "var foo = function() {",
            "  this.a = 10;",
            "  this.a += 15;",
            "  this.a = 20;",
            "}"),
        lines(
            "var foo = function() {",
            "  this.a = 10;",
            "  this.a + 15;",
            "  this.a = 20;",
            "}"));
  }

  @Test
  public void testChainingPropertiesAssignments() {
    test(
        lines(
            "var foo = function() {",
            "  this.a = this.b = this.c = 10;",
            "  this.b = 15;",
            "}"),
        lines(
            "var foo = function() {",
            "  this.a = this.c = 10;",
            "  this.b = 15;",
            "}"));
  }

  @Test
  public void testConditionalProperties() {
    // We don't handle conditionals at all.
    testSame(
        lines(
            "var foo = function() {",
            "  this.a = 10;",
            "  if (true) { this.a = 20; } else { this.a = 30; }",
            "}"));

    // However, we do handle everything up until the conditional.
    test(
        lines(
            "var foo = function() {",
            "  this.a = 10;",
            "  this.a = 20;",
            "  if (true) { this.a = 20; } else { this.a = 30; }",
            "}"),
        lines(
            "var foo = function() {",
            "  10;",
            "  this.a = 20;",
            "  if (true) { this.a = 20; } else { this.a = 30; }",
            "}"));
  }

  @Test
  public void testQualifiedNamePrefixAssignment() {
    testSame(
        lines(
            "var foo = function() {",
            "  a.b.c = 20;",
            "  a.b = other;",
            "  a.b.c = 30;",
            "}"));

    testSame(
        lines(
            "var foo = function() {",
            "  a.b = 20;",
            "  a = other;",
            "  a.b = 30;",
            "}"));
  }

  @Test
  public void testCall() {
    testSame(
        lines(
            "var foo = function() {",
            "  a.b.c = 20;",
            "  doSomething();",
            "  a.b.c = 30;",
            "}"));

    if (ASSUME_CONSTRUCTORS_HAVENT_ESCAPED) {
      test(
          lines(
              "/** @constructor */",
              "var foo = function() {",
              "  this.c = 20;",
              "  doSomething();",
              "  this.c = 30;",
              "}"),
          lines(
              "/** @constructor */",
              "var foo = function() {",
              "  20;",
              "  doSomething();",
              "  this.c = 30;",
              "}"));
    }

    testSame(
        lines(
            "/** @constructor */",
            "var foo = function() {",
            "  this.c = 20;",
            "  doSomething(this);",
            "  this.c = 30;",
            "}"));

    testSame(
        lines(
            "/** @constructor */",
            "var foo = function() {",
            "  this.c = 20;",
            "  this.doSomething();",
            "  this.c = 30;",
            "}"));

    testSame(
        lines(
            "/** @constructor */",
            "var foo = function() {",
            "  this.c = 20;",
            "  doSomething(this.c);",
            "  this.c = 30;",
            "}"));

    test(
        lines(
            "var foo = function() {",
            "  a.b.c = 20;",
            "  doSomething(a.b.c = 25);",
            "  a.b.c = 30;",
            "}"),
        lines(
            "var foo = function() {",
            "  20;",
            "  doSomething(a.b.c = 25);",
            "  a.b.c = 30;",
            "}"));
  }

  @Test
  public void testYield() {
    // Assume that properties may be read during a yield
    testSame(
        lines(
            "var foo = function*() {",
            "  a.b.c = 20;",
            "  yield;",
            "  a.b.c = 30;",
            "}"));

    testSame(
        lines(
            "/** @constructor */",
            "var foo = function*() {",
            "  this.c = 20;",
            "  yield;",
            "  this.c = 30;",
            "}"));

    testSame(
        lines(
            "var obj = {",
            "  *gen() {",
            "    this.c = 20;",
            "    yield;",
            "    this.c = 30;",
            "  }",
            "}"));

    test(
        lines(
            "var foo = function*() {",
            "  a.b.c = 20;",
            "  yield a.b.c = 25;",
            "  a.b.c = 30;",
            "}"),
        lines(
            "var foo = function*() {",
            "  20;",
            "  yield a.b.c = 25;",
            "  a.b.c = 30;",
            "}"));
  }

  @Test
  public void testNew() {
    // Assume that properties may be read during a constructor call
    testSame(
        lines(
            "var foo = function() {",
            "  a.b.c = 20;",
            "  new C;",
            "  a.b.c = 30;",
            "}"));
  }

  @Test
  public void testTaggedTemplateLit() {
    // Assume that properties may be read during a tagged template lit invocation
    testSame(
        lines(
            "var foo = function() {",
            "  a.b.c = 20;",
            "  doSomething`foo`;",
            "  a.b.c = 30;",
            "}"));
  }

  @Test
  public void testAwait() {
    // Assume that properties may be read while waiting for "await"
    testSame(
        lines(
            "async function foo() {",
            "  a.b.c = 20;",
            "  await bar;",
            "  a.b.c = 30;",
            "}"));
  }

  @Test
  public void testUnknownLookup() {
    testSame(
        lines(
            "/** @constructor */",
            "var foo = function(str) {",
            "  this.x = 5;",
            "  var y = this[str];",
            "  this.x = 10;",
            "}"));

    testSame(
        lines(
            "/** @constructor */",
            "var foo = function(x, str) {",
            "  x.y = 5;",
            "  var y = x[str];",
            "  x.y = 10;",
            "}"));
  }

  @Test
  public void testName() {
    testSame(
        lines(
            "function f(x) {",
            "  var y = { a: 0 };",
            "  x.a = 123;",
            "  y = x;",
            "  x.a = 234;",
            "  return x.a + y.a;",
            "}"));
  }

  @Test
  public void testName2() {
    testSame(
        lines(
            "function f(x) {",
            "  var y = x;",
            "  x.a = 123;",
            "  x = {};",
            "  x.a = 234;",
            "  return x.a + y.a;",
            "}"));
  }

  @Test
  public void testAliasing() {
    testSame(
        lines(
            "function f(x) {",
            "  x.b.c = 1;",
            "  var y = x.a.c;", // x.b.c is read here
            "  x.b.c = 2;",
            "  return x.b.c + y;",
            "}",
            "var obj = { c: 123 };",
            "f({a: obj, b: obj});"));
  }

  @Test
  public void testHook() {
    testSame(
        lines(
            "function f(x, pred) {",
            "  var y;",
            "  x.p = 234;",
            "  y = pred ? (x.p = 123) : x.p;",
            "}"));

    testSame(
        lines(
            "function f(x, pred) {",
            "  var y;",
            "  x.p = 234;",
            "  y = pred ? (x.p = 123) : 123;",
            "  return x.p;",
            "}"));
  }

  @Test
  public void testConditionalExpression() {
    testSame(
        lines(
            "function f(x) {",
            "  return (x.p = 2) || (x.p = 3);", // Second assignment will never execute.
            "}"));

    testSame(
        lines(
            "function f(x) {",
            "  return (x.p = 0) && (x.p = 3);", // Second assignment will never execute.
            "}"));
  }

  @Test
  public void testBrackets() {
    testSame(
        lines(
            "function f(x, p) {",
            "  x.prop = 123;",
            "  x[p] = 234;",
            "  return x.prop;",
            "}"));
  }

  @Test
  public void testFor() {
    testSame(
        lines(
            "function f(x) {",
            "  x.p = 1;",
            "  for(;x;) {}",
            "  x.p = 2;",
            "}"));

    testSame(
        lines(
            "function f(x) {",
            "  for(;x;) {",
            "    x.p = 1;",
            "  }",
            "  x.p = 2;",
            "}"));

    testSame(
        lines(
            "function f(x) {",
            "  x.p = 1;",
            "  for(;;) {",
            "    x.p = 2;",
            "  }",
            "}"));

    testSame(
        lines(
            "function f(x) {",
            "  x.p = 1;",
            "  for(x.p = 2;;) {",
            "  }",
            "}"));

    testSame(
        lines(
            "function f(x) {",
            "  x.p = 1;",
            "  for(x.p = 2;;x.p=3) {",
            "    return x.p;", // Reads the "x.p = 2" assignment.
            "  }",
            "}"));

    testSame(
        lines(
            "function f(x) {",
            "  for(;;) {",
            "    x.p = 1;",
            "    x.p = 2;",
            "  }",
            "}"));

    testSame(
        lines(
            "function f(x) {",
            "  x.p = 1;",
            "  for(;;) {",
            "  }",
            "  x.p = 2;",
            "}"));
  }

  @Test
  public void testWhile() {
    testSame(
        lines(
            "function f(x) {",
            "  x.p = 1;",
            "  while(x);",
            "  x.p = 2;",
            "}"));

    testSame(
        lines(
            "function f(x) {",
            "  x.p = 1;",
            "  while(1) {",
            "    x.p = 2;",
            "  }",
            "}"));

    testSame(
        lines(
            "function f(x) {",
            "  while(true) {",
            "    x.p = 1;",
            "    if (random()) continue;",
            "    x.p = 2;",
            "  }",
            "}"));

    testSame(
        lines(
            "function f(x) {",
            "  while(true) {",
            "    x.p = 1;",
            "    if (random()) break;",
            "    x.p = 2;",
            "  }",
            "}"));

    test(
        lines(
            "function f(x) {",
            "  x.p = 1;",
            "  while(x.p = 2) {",
            "  }",
            "}"),
        lines(
            "function f(x) {",
            "  1;",
            "  while(x.p = 2) {",
            "  }",
            "}"));

    test(
        lines(
            "function f(x) {",
            "  while(true) {",
            "    x.p = 1;",
            "    x.p = 2;",
            "  }",
            "}"),
        lines(
            "function f(x) {",
            "  while(true) {",
            "    1;",
            "    x.p = 2;",
            "  }",
            "}"));

    test(
        lines(
            "function f(x) {",
            "  x.p = 1;",
            "  while(1) {}",
            "  x.p = 2;",
            "}"),
        lines(
            "function f(x) {",
            "  1;",
            "  while(1) {}",
            "  x.p = 2;",
            "}"));
  }

  @Test
  public void testTry() {
    testSame(
        lines(
            "function f(x) {",
            "  x.p = 1;",
            "  try {",
            "    x.p = 2;",
            "  } catch (e) {}",
            "}"));

    testSame(
        lines(
            "function f(x) {",
            "  x.p = 1;",
            "  try {",
            "  } catch (e) {",
            "    x.p = 2;",
            "  }",
            "}"));

    testSame(
        lines(
            "function f(x) {",
            "  try {",
            "    x.p = 1;",
            "  } catch (e) {",
            "    x.p = 2;",
            "  }",
            "}"));

    test(
        lines(
            "function f(x) {",
            "  try {",
            "    x.p = 1;",
            "    x.p = 2;",
            "  } catch (e) {",
            "  }",
            "}"),
        lines(
            "function f(x) {",
            "  try {",
            "    1;",
            "    x.p = 2;",
            "  } catch (e) {",
            "  }",
            "}"));

    testSame(
        lines(
            "function f(x) {",
            "  try {",
            "    x.p = 1;",
            "    maybeThrow();",
            "    x.p = 2;",
            "  } catch (e) {",
            "  }",
            "}"));

    testSame(
        lines(
            "/** @constructor */",
            "function f() {",
            "  try {",
            "    this.p = 1;",
            "    maybeThrow();",
            "    this.p = 2;",
            "  } catch (e) {",
            "  }",
            "}"));
  }

  @Test
  public void testThrow() {
    testSame(
        lines(
            "function f(x) {",
            "  x.p = 10",
            "  if (random) throw err;",
            "  x.p = 20;",
            "}"));

    testSame(
        lines(
            "function f(x) {",
            "  x.p = 10",
            "  throw err;",
            "  x.p = 20;",
            "}"));
  }

  @Test
  public void testSwitch() {
    testSame(
        lines(
            "function f(x, pred) {",
            "  x.p = 1;",
            "  switch (pred) {",
            "    case 1:",
            "      x.p = 2;",
            "    case 2:",
            "      x.p = 3;",
            "      break;",
            "    default:",
            "      return x.p;",
            "  }",
            "}"));

    testSame(
        lines(
            "function f(x, pred) {",
            "  x.p = 1;",
            "  switch (pred) {",
            "    default:",
            "      x.p = 2;",
            "  }",
            "  x.p = 3;",
            "}"));

    testSame(
        lines(
            "function f(x, pred) {",
            "  x.p = 1;",
            "  switch (pred) {",
            "    default:",
            "      return;",
            "  }",
            "  x.p = 2;",
            "}"));

    // For now we don't enter switch statements.
    testSame(
        lines(
            "function f(x, pred) {",
            "  switch (pred) {",
            "    default:",
            "      x.p = 2;",
            "      x.p = 3;",
            "  }",
            "}"));
  }

  @Test
  public void testIf() {
    test(
        lines(
            "function f(x, pred) {",
            "  if (pred) {",
            "    x.p = 1;",
            "    x.p = 2;",
            "    return x.p;",
            "  }",
            "}"),
        lines(
            "function f(x, pred) {",
            "  if (pred) {",
            "    1;",
            "    x.p = 2;",
            "    return x.p;",
            "  }",
            "}"));

    test(
        lines(
            "function f(x, pred) {",
            "  x.p = 1;",
            "  if (pred) {}",
            "  x.p = 2;",
            "  return x.p;",
            "}"),
        lines(
            "function f(x, pred) {",
            "  1;",
            "  if (pred) {}",
            "  x.p = 2;",
            "  return x.p;",
            "}"));

    testSame(
        lines(
            "function f(x, pred) {",
            "  if (pred) {",
            "    x.p = 1;",
            "  }",
            "  x.p = 2;",
            "}"));
  }

  @Test
  public void testCircularPropChain() {
    testSame(
        lines(
            "function f(x, y) {",
            "  x.p = {};",
            "  x.p.y.p.z = 10;",
            "  x.p = {};",
            "}"));
  }

  @Test
  public void testDifferentQualifiedNames() {
    testSame(
        lines(
            "function f(x, y) {",
            "  x.p = 10;",
            "  y.p = 11;",
            "}"));
  }

  @Test
  public void testGetPropContainsNonQualifiedNames() {
    testSame(
        lines(
            "function f(x) {",
            "  foo(x).p = 10;",
            "  foo(x).p = 11;",
            "}"));

    testSame(
        lines(
            "function f(x) {",
            "  (x = 10).p = 10;",
            "  (x = 10).p = 11;",
            "}"));
  }

  @Test
  public void testEs6Constructor() {
    testSame(
        lines(
            "class Foo {",
            "  constructor() {",
            "    this.p = 123;",
            "    var z = this.p;",
            "    this.p = 234;",
            "  }",
            "}"));

    test(
        lines(
            "class Foo {",
            "  constructor() {",
            "    this.p = 123;",
            "    this.p = 234;",
            "  }",
            "}"),
        lines(
            "class Foo {",
            "  constructor() {",
            "    123;",
            "    this.p = 234;",
            "  }",
            "}"));

    if (ASSUME_CONSTRUCTORS_HAVENT_ESCAPED) {
      test(
          lines(
              "class Foo {",
              "  constructor() {",
              "    this.p = 123;",
              "    foo();",
              "    this.p = 234;",
              "  }",
              "}"),
          lines(
              "class Foo {",
              "  constructor() {",
              "    123;",
              "    foo();",
              "    this.p = 234;",
              "  }",
              "}"));
    }
  }

  @Test
  public void testES6ClassExtends() {
    testSame(
        lines(
            "class C {",
            "  constructor() {",
            "    this.x = 20;",
            "  }",
            "}",
            "class D extends C {",
            "  constructor() {",
            "    super();",
            "    this.x = 40;",
            "  }",
            "}"
        )
    );
  }

  @Test
  public void testGetter() {
    testSame(
        lines(
            "/** @constructor */ function Foo() { this.enabled = false; };",
            "Object.defineProperties(Foo.prototype, {bar: {",
            "  get: function () { return this.enabled ? 'enabled' : 'disabled'; }",
            "}});",
            "function f() {",
            "  var foo = new Foo()",
            "  foo.enabled = true;",
            "  var f = foo.bar;",
            "  foo.enabled = false;",
            "}"));

    testSame(
        lines(
            "/** @constructor */ function Foo() { this.enabled = false; };",
            "Object.defineProperty(Foo, 'bar', {",
            "  get: function () { return this.enabled ? 'enabled' : 'disabled'; }",
            "});",
            "function f() {",
            "  var foo = new Foo()",
            "  foo.enabled = true;",
            "  var f = foo.bar;",
            "  foo.enabled = false;",
            "}"));
  }

  @Test
  public void testGetter_afterDeadAssignment() {
    testSame(
        lines(
            "function f() {",
            "  var foo = new Foo()",
            "  foo.enabled = true;",
            "  var f = foo.bar;",
            "  foo.enabled = false;",
            "}",
            "/** @constructor */ function Foo() { this.enabled = false; };",
            "Object.defineProperties(Foo.prototype, {bar: {",
            "  get: function () { return this.enabled ? 'enabled' : 'disabled'; }",
            "}});"));
  }

  @Test
  public void testGetter_onDifferentType() {
    testSame(
        lines(
            "/** @constructor */",
            "function Foo() {",
            "  this.enabled = false;",
            "};",
            "Object.defineProperties(",
            "    Foo.prototype, {",
            "      baz: {",
            "        get: function () { return this.enabled ? 'enabled' : 'disabled'; }",
            "      }",
            "    });",
            "/** @constructor */",
            "function Bar() {",
            "  this.enabled = false;",
            "  this.baz = 123;",
            "};",
            "function f() {",
            "  var bar = new Bar();",
            "  bar.enabled = true;",
            "  var ret = bar.baz;",
            "  bar.enabled = false;",
            "  return ret;",
            "};")
    );
  }

  @Test
  public void testSetter() {
    testSame(
        lines(
            "/** @constructor */ function Foo() { this.enabled = false; this.x = null; };",
            "Object.defineProperties(Foo.prototype, {bar: {",
            "  set: function (x) { this.x = this.enabled ? x * 2 : x; }",
            "}});",
            "function f() {",
            "  var foo = new Foo()",
            "  foo.enabled = true;",
            "  foo.bar = 10;",
            "  foo.enabled = false;",
            "}"));

    testSame(
        lines(
            "/** @constructor */ function Foo() { this.enabled = false; this.x = null; };",
            "Object.defineProperty(Foo, 'bar', {",
            "  set: function (x) { this.x = this.enabled ? x * 2 : x; }",
            "});",
            "function f() {",
            "  var foo = new Foo()",
            "  foo.enabled = true;",
            "  foo.bar = 10;",
            "  foo.enabled = false;",
            "}"));
  }

  @Test
  public void testEs5Getter() {
    testSame(
        lines(
            "var bar = {",
            "  enabled: false,",
            "  get baz() {",
            "    return this.enabled ? 'enabled' : 'disabled';",
            "  }",
            "};",
            "function f() {",
            "  bar.enabled = true;",
            "  var ret = bar.baz;",
            "  bar.enabled = false;",
            "  return ret;",
            "};")
    );
  }

  @Test
  public void testEs5Setter() {
    testSame(
        lines(
            "var bar = {",
            "  enabled: false,",
            "  set baz(x) {",
            "    this.x = this.enabled ? x * 2 : x;",
            "  }",
            "};",
            "function f() {",
            "  bar.enabled = true;",
            "  bar.baz = 10;",
            "  bar.enabled = false;",
            "};")
    );
  }

  @Test
  public void testObjectDefineProperty_aliasedParams() {
    testSame(
        lines(
            "function addGetter(obj, propName) {",
            "  Object.defineProperty(obj, propName, {",
            "    get: function() { return this[propName]; }",
            "  });",
            "};",
            "/** @constructor */ function Foo() { this.enabled = false; this.x = null; };",
            "addGetter(Foo.prototype, 'x');",
            "function f() {",
            "  var foo = new Foo()",
            "  foo.enabled = true;",
            "  foo.bar = 10;",
            "  foo.enabled = false;",
            "}",
            "function z() {",
            "  var x = {};",
            "  x.bar = 10;",
            "  x.bar = 20;",
            "}"));

    testSame(
        lines(
            "function f() {",
            "  var foo = new Foo()",
            "  foo.enabled = true;",
            "  foo.bar = 10;",
            "  foo.enabled = false;",
            "}",
            "function addGetter(obj, propName) {",
            "  Object.defineProperty(obj, propName, {",
            "    get: function() { return this[propName]; }",
            "  });",
            "};",
            "/** @constructor */ function Foo() { this.enabled = false; this.x = null; };",
            "addGetter(Foo.prototype, 'x');",
            "function z() {",
            "  var x = {};",
            "  x.bar = 10;",
            "  x.bar = 20;",
            "}"));
  }

  @Test
  public void testObjectDefineProperty_aliasedObject() {
    testSame(
        lines(
            "/** @constructor */ function Foo() { this.enabled = false; this.x = null; };",
            "var x = Foo.prototype;",
            "Object.defineProperty(x, 'bar', {",
            "  set: function (x) { this.x = this.enabled ? x * 2 : x; }",
            "});",
            "function f() {",
            "  var foo = new Foo()",
            "  foo.enabled = true;",
            "  foo.bar = 10;",
            "  foo.enabled = false;",
            "}",
            "function z() {",
            "  var x = {};",
            "  x.bar = 10;",
            "  x.bar = 20;",
            "}"));
  }

  @Test
  public void testObjectDefineProperty_aliasedPropName() {
    testSame(
        lines(
            "/** @constructor */ function Foo() { this.enabled = false; this.x = null; };",
            "var x = 'bar';",
            "Object.defineProperty(Foo.prototype, x, {",
            "  set: function (x) { this.x = this.enabled ? x * 2 : x; }",
            "});",
            "function f() {",
            "  var foo = new Foo()",
            "  foo.enabled = true;",
            "  foo.bar = 10;",
            "  foo.enabled = false;",
            "}",
            "function z() {",
            "  var x = {};",
            "  x.bar = 10;",
            "  x.bar = 20;",
            "}"));

    test(
        lines(
            "/** @constructor */ function Foo() { this.enabled = false; this.x = null; };",
            "var x = 'bar';",
            "Object.defineProperty(Foo.prototype, x, {",
            "  value: 10",
            "});",
            "function f() {",
            "  var foo = new Foo()",
            "  foo.enabled = true;",
            "  foo.bar = 10;",
            "  foo.enabled = false;",
            "}",
            "function z() {",
            "  var x = {};",
            "  x.bar = 10;",
            "  x.bar = 20;",
            "}"),
        lines(
            "/** @constructor */ function Foo() { this.enabled = false; this.x = null; };",
            "var x = 'bar';",
            "Object.defineProperty(Foo.prototype, x, {",
            "  value: 10",
            "});",
            "function f() {",
            "  var foo = new Foo()",
            "  true;",
            "  foo.bar = 10;",
            "  foo.enabled = false;",
            "}",
            "function z() {",
            "  var x = {};",
            "  10;",
            "  x.bar = 20;",
            "}"));
  }

  @Test
  public void testObjectDefineProperty_aliasedPropSet() {
    testSame(
        lines(
            "/** @constructor */ function Foo() { this.enabled = false; this.x = null; };",
            "var x = {",
            "  set: function (x) { this.x = this.enabled ? x * 2 : x; }",
            "};",
            "Object.defineProperty(Foo.prototype, 'bar', x);",
            "function f() {",
            "  var foo = new Foo()",
            "  foo.enabled = true;",
            "  foo.bar = 10;",
            "  foo.enabled = false;",
            "}",
            "function z() {",
            "  var x = {};",
            "  x.bar = 10;",
            "  x.bar = 20;",
            "}"));
  }

  @Test
  public void testObjectDefineProperties_aliasedPropertyMap() {
    testSame(
        lines(
            "/** @constructor */ function Foo() { this.enabled = false; this.x = null; };",
            "var properties = {bar: {",
            "  set: function (x) { this.x = this.enabled ? x * 2 : x; }",
            "}};",
            "Object.defineProperties(Foo.prototype, properties);",
            "function f() {",
            "  var foo = new Foo()",
            "  foo.enabled = true;",
            "  foo.bar = 10;",
            "  foo.enabled = false;",
            "}",
            "function z() {",
            "  var x = {};",
            "  x.bar = 10;",
            "  x.bar = 20;",
            "}"));

    testSame(
        lines(
            "/** @constructor */ function Foo() { this.enabled = false; this.x = null; };",
            "var properties = {",
            "  set: function (x) { this.x = this.enabled ? x * 2 : x; }",
            "};",
            "Object.defineProperties(Foo.prototype, {bar: properties});",
            "function f() {",
            "  var foo = new Foo()",
            "  foo.enabled = true;",
            "  foo.bar = 10;",
            "  foo.enabled = false;",
            "}",
            "function z() {",
            "  var x = {};",
            "  x.bar = 10;",
            "  x.bar = 20;",
            "}"));
  }

  @Test
  public void testObjectDefineProperties_aliasedObject() {
    test(
        lines(
            "/** @constructor */ function Foo() { this.enabled = false; this.x = null; };",
            "var properties = {bar: {",
            "  set: function (x) { this.x = this.enabled ? x * 2 : x; }",
            "}};",
            "var x = Foo.prototype;",
            "Object.defineProperties(x, {bar: {",
            "  set: function (x) { this.x = this.enabled ? x * 2 : x; }",
            "}});",
            "function f() {",
            "  var foo = new Foo()",
            "  foo.enabled = true;",
            "  foo.bar = 10;",
            "  foo.enabled = false;",
            "  foo.enabled = true;",
            "}",
            "function z() {",
            "  var x = {};",
            "  x.bar = 10;",
            "  x.bar = 20;",
            "}"),
        lines(
            "/** @constructor */ function Foo() { this.enabled = false; this.x = null; };",
            "var properties = {bar: {",
            "  set: function (x) { this.x = this.enabled ? x * 2 : x; }",
            "}};",
            "var x = Foo.prototype;",
            "Object.defineProperties(x, {bar: {",
            "  set: function (x) { this.x = this.enabled ? x * 2 : x; }",
            "}});",
            "function f() {",
            "  var foo = new Foo()",
            "  foo.enabled = true;",
            "  foo.bar = 10;",
            "  false;",
            "  foo.enabled = true;",
            "}",
            "function z() {",
            "  var x = {};",
            "  x.bar = 10;",
            "  x.bar = 20;",
            "}"));
  }

  @Test
  public void testPropertyDefinedInExterns() {
    String externs = lines(
        "var window = {};",
        "/** @type {number} */ window.innerWidth",
        "/** @constructor */",
        "var Image = function() {};",
        "/** @type {string} */ Image.prototype.src;"
    );

    testSame(
        externs(externs),
        srcs(
            lines(
                "function z() {", "  window.innerWidth = 10;", "  window.innerWidth = 20;", "}")));

    testSame(
        externs(externs),
        srcs(
            lines(
                "function z() {",
                "  var img = new Image();",
                "  img.src = '';",
                "  img.src = 'foo.bar';",
                "}")));

    testSame(
        externs(externs),
        srcs(lines("function z(x) {", "  x.src = '';", "  x.src = 'foo.bar';", "}")));
  }

  @Test
  public void testJscompInherits() {
    test(
        lines(
            "/** @constructor */ function Foo() { this.bar = null; };",
            "var $jscomp = {};",
            "$jscomp.inherits = function(x) {",
            "  Object.defineProperty(x, x, x);",
            "};",
            "function f() {",
            "  var foo = new Foo()",
            "  foo.bar = 10;",
            "  foo.bar = 20;",
            "}"),
        lines(
            "/** @constructor */ function Foo() { this.bar = null; };",
            "var $jscomp = {};",
            "$jscomp.inherits = function(x) {",
            "  Object.defineProperty(x, x, x);",
            "};",
            "function f() {",
            "  var foo = new Foo()",
            "  10;",
            "  foo.bar = 20;",
            "}"));
  }

  @Test
  public void testGithubIssue2874() {
    testSame(
        lines(
            "var globalObj = {i0:0};\n",
            "function func(b) {",
            "  var g = globalObj;",
            "  var f = b;",
            "  g.i0 = f.i0;",
            "  g = b;",
            "  g.i0 = 0;",
            "}",
            "func({i0:2});",
            "alert(globalObj);"));
  }

  @Test
  public void testReplaceShorthandAssignmentOpWithRegularOp() {
    // See https://github.com/google/closure-compiler/issues/3017
    test(
        lines(
            "function f(obj) {", // preserve newlines
            "  obj.a = (obj.a |= 2) | 8;",
            "  obj.a = (obj.a |= 16) | 32;",
            "}"),
        lines(
            "function f(obj) {", // preserve newlines
            "  obj.a = (obj.a | 2) | 8;",
            "  obj.a = (obj.a | 16) | 32;",
            "}"));
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new DeadPropertyAssignmentElimination(compiler);
  }
}
