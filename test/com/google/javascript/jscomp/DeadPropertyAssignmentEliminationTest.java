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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

public class DeadPropertyAssignmentEliminationTest extends CompilerTestCase {

  @Override
  public void setUp() throws Exception {
    enableGatherExternProperties();
  }

  public void testBasic() {
    testSame(LINE_JOINER.join(
        "var foo = function() {",
        "  this.a = 20;",
        "}"));

    test(
        LINE_JOINER.join(
            "var foo = function() {",
            "  this.a = 10;",
            "  this.a = 20;",
            "}"),
        LINE_JOINER.join(
            "var foo = function() {",
            "  10;",
            "  this.a = 20;",
            "}"));

    testSame(
        LINE_JOINER.join(
            "var foo = function() {",
            "  this.a = 20;",
            "  this.a = this.a + 20;",
            "}"));
  }

  public void testMultipleProperties() {
    test(
        LINE_JOINER.join(
            "var foo = function() {",
            "  this.a = 10;",
            "  this.b = 15;",
            "  this.a = 20;",
            "}"),
        LINE_JOINER.join(
            "var foo = function() {",
            "  10;",
            "  this.b = 15;",
            "  this.a = 20;",
            "}"));
  }

  public void testNonStandardAssign() {
    test(
        LINE_JOINER.join(
            "var foo = function() {",
            "  this.a = 10;",
            "  this.a += 15;",
            "  this.a = 20;",
            "}"),
        LINE_JOINER.join(
            "var foo = function() {",
            "  10;",
            "  15;",
            "  this.a = 20;",
            "}"));
  }

  public void testChainingPropertiesAssignments() {
    test(
        LINE_JOINER.join(
            "var foo = function() {",
            "  this.a = this.b = this.c = 10;",
            "  this.b = 15;",
            "}"),
        LINE_JOINER.join(
            "var foo = function() {",
            "  this.a = this.c = 10;",
            "  this.b = 15;",
            "}"));
  }

  public void testConditionalProperties() {
    // We don't handle conditionals at all.
    testSame(
        LINE_JOINER.join(
            "var foo = function() {",
            "  this.a = 10;",
            "  if (true) { this.a = 20; } else { this.a = 30; }",
            "}"));

    // However, we do handle everything up until the conditional.
    test(
        LINE_JOINER.join(
            "var foo = function() {",
            "  this.a = 10;",
            "  this.a = 20;",
            "  if (true) { this.a = 20; } else { this.a = 30; }",
            "}"),
        LINE_JOINER.join(
            "var foo = function() {",
            "  10;",
            "  this.a = 20;",
            "  if (true) { this.a = 20; } else { this.a = 30; }",
            "}"));
  }

  public void testQualifiedNamePrefixAssignment() {
    testSame(
        LINE_JOINER.join(
            "var foo = function() {",
            "  a.b.c = 20;",
            "  a.b = other;",
            "  a.b.c = 30;",
            "}"));

    testSame(
        LINE_JOINER.join(
            "var foo = function() {",
            "  a.b = 20;",
            "  a = other;",
            "  a.b = 30;",
            "}"));
  }

  public void testCall() {
    testSame(
        LINE_JOINER.join(
            "var foo = function() {",
            "  a.b.c = 20;",
            "  doSomething();",
            "  a.b.c = 30;",
            "}"));

    if (ASSUME_CONSTRUCTORS_HAVENT_ESCAPED) {
      test(
          LINE_JOINER.join(
              "/** @constructor */",
              "var foo = function() {",
              "  this.c = 20;",
              "  doSomething();",
              "  this.c = 30;",
              "}"),
          LINE_JOINER.join(
              "/** @constructor */",
              "var foo = function() {",
              "  20;",
              "  doSomething();",
              "  this.c = 30;",
              "}"));
    }

    testSame(
        LINE_JOINER.join(
            "/** @constructor */",
            "var foo = function() {",
            "  this.c = 20;",
            "  doSomething(this);",
            "  this.c = 30;",
            "}"));

    testSame(
        LINE_JOINER.join(
            "/** @constructor */",
            "var foo = function() {",
            "  this.c = 20;",
            "  this.doSomething();",
            "  this.c = 30;",
            "}"));

    testSame(
        LINE_JOINER.join(
            "/** @constructor */",
            "var foo = function() {",
            "  this.c = 20;",
            "  doSomething(this.c);",
            "  this.c = 30;",
            "}"));
  }

  public void testUnknownLookup() {
    testSame(
        LINE_JOINER.join(
            "/** @constructor */",
            "var foo = function(str) {",
            "  this.x = 5;",
            "  var y = this[str];",
            "  this.x = 10;",
            "}"));

    testSame(
        LINE_JOINER.join(
            "/** @constructor */",
            "var foo = function(x, str) {",
            "  x.y = 5;",
            "  var y = x[str];",
            "  x.y = 10;",
            "}"));
  }

  public void testName() {
    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  var y = { a: 0 };",
            "  x.a = 123;",
            "  y = x;",
            "  x.a = 234;",
            "  return x.a + y.a;",
            "}"));
  }

  public void testName2() {
    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  var y = x;",
            "  x.a = 123;",
            "  x = {};",
            "  x.a = 234;",
            "  return x.a + y.a;",
            "}"));
  }

  public void testAliasing() {
    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  x.b.c = 1;",
            "  var y = x.a.c;", // x.b.c is read here
            "  x.b.c = 2;",
            "  return x.b.c + y;",
            "}",
            "var obj = { c: 123 };",
            "f({a: obj, b: obj});"));
  }

  public void testHook() {
    testSame(
        LINE_JOINER.join(
            "function f(x, pred) {",
            "  var y;",
            "  x.p = 234;",
            "  y = pred ? (x.p = 123) : x.p;",
            "}"));

    testSame(
        LINE_JOINER.join(
            "function f(x, pred) {",
            "  var y;",
            "  x.p = 234;",
            "  y = pred ? (x.p = 123) : 123;",
            "  return x.p;",
            "}"));
  }

  public void testConditionalExpression() {
    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  return (x.p = 2) || (x.p = 3);", // Second assignment will never execute.
            "}"));

    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  return (x.p = 0) && (x.p = 3);", // Second assignment will never execute.
            "}"));
  }

  public void testBrackets() {
    testSame(
        LINE_JOINER.join(
            "function f(x, p) {",
            "  x.prop = 123;",
            "  x[p] = 234;",
            "  return x.prop;",
            "}"));
  }

  public void testFor() {
    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  x.p = 1;",
            "  for(;x;) {}",
            "  x.p = 2;",
            "}"));

    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  for(;x;) {",
            "    x.p = 1;",
            "  }",
            "  x.p = 2;",
            "}"));

    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  x.p = 1;",
            "  for(;;) {",
            "    x.p = 2;",
            "  }",
            "}"));

    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  x.p = 1;",
            "  for(x.p = 2;;) {",
            "  }",
            "}"));

    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  x.p = 1;",
            "  for(x.p = 2;;x.p=3) {",
            "    return x.p;", // Reads the "x.p = 2" assignment.
            "  }",
            "}"));

    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  for(;;) {",
            "    x.p = 1;",
            "    x.p = 2;",
            "  }",
            "}"));

    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  x.p = 1;",
            "  for(;;) {",
            "  }",
            "  x.p = 2;",
            "}"));
  }

  public void testWhile() {
    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  x.p = 1;",
            "  while(x);",
            "  x.p = 2;",
            "}"));

    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  x.p = 1;",
            "  while(1) {",
            "    x.p = 2;",
            "  }",
            "}"));

    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  while(true) {",
            "    x.p = 1;",
            "    if (random()) continue;",
            "    x.p = 2;",
            "  }",
            "}"));

    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  while(true) {",
            "    x.p = 1;",
            "    if (random()) break;",
            "    x.p = 2;",
            "  }",
            "}"));

    test(
        LINE_JOINER.join(
            "function f(x) {",
            "  x.p = 1;",
            "  while(x.p = 2) {",
            "  }",
            "}"),
        LINE_JOINER.join(
            "function f(x) {",
            "  1;",
            "  while(x.p = 2) {",
            "  }",
            "}"));

    test(
        LINE_JOINER.join(
            "function f(x) {",
            "  while(true) {",
            "    x.p = 1;",
            "    x.p = 2;",
            "  }",
            "}"),
        LINE_JOINER.join(
            "function f(x) {",
            "  while(true) {",
            "    1;",
            "    x.p = 2;",
            "  }",
            "}"));

    test(
        LINE_JOINER.join(
            "function f(x) {",
            "  x.p = 1;",
            "  while(1) {}",
            "  x.p = 2;",
            "}"),
        LINE_JOINER.join(
            "function f(x) {",
            "  1;",
            "  while(1) {}",
            "  x.p = 2;",
            "}"));
  }

  public void testTry() {
    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  x.p = 1;",
            "  try {",
            "    x.p = 2;",
            "  } catch (e) {}",
            "}"));

    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  x.p = 1;",
            "  try {",
            "  } catch (e) {",
            "    x.p = 2;",
            "  }",
            "}"));

    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  try {",
            "    x.p = 1;",
            "  } catch (e) {",
            "    x.p = 2;",
            "  }",
            "}"));

    test(
        LINE_JOINER.join(
            "function f(x) {",
            "  try {",
            "    x.p = 1;",
            "    x.p = 2;",
            "  } catch (e) {",
            "  }",
            "}"),
        LINE_JOINER.join(
            "function f(x) {",
            "  try {",
            "    1;",
            "    x.p = 2;",
            "  } catch (e) {",
            "  }",
            "}"));

    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  try {",
            "    x.p = 1;",
            "    maybeThrow();",
            "    x.p = 2;",
            "  } catch (e) {",
            "  }",
            "}"));

    testSame(
        LINE_JOINER.join(
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

  public void testThrow() {
    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  x.p = 10",
            "  if (random) throw err;",
            "  x.p = 20;",
            "}"));

    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  x.p = 10",
            "  throw err;",
            "  x.p = 20;",
            "}"));
  }

  public void testSwitch() {
    testSame(
        LINE_JOINER.join(
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
        LINE_JOINER.join(
            "function f(x, pred) {",
            "  x.p = 1;",
            "  switch (pred) {",
            "    default:",
            "      x.p = 2;",
            "  }",
            "  x.p = 3;",
            "}"));

    testSame(
        LINE_JOINER.join(
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
        LINE_JOINER.join(
            "function f(x, pred) {",
            "  switch (pred) {",
            "    default:",
            "      x.p = 2;",
            "      x.p = 3;",
            "  }",
            "}"));
  }

  public void testIf() {
    test(
        LINE_JOINER.join(
            "function f(x, pred) {",
            "  if (pred) {",
            "    x.p = 1;",
            "    x.p = 2;",
            "    return x.p;",
            "  }",
            "}"),
        LINE_JOINER.join(
            "function f(x, pred) {",
            "  if (pred) {",
            "    1;",
            "    x.p = 2;",
            "    return x.p;",
            "  }",
            "}"));

    test(
        LINE_JOINER.join(
            "function f(x, pred) {",
            "  x.p = 1;",
            "  if (pred) {}",
            "  x.p = 2;",
            "  return x.p;",
            "}"),
        LINE_JOINER.join(
            "function f(x, pred) {",
            "  1;",
            "  if (pred) {}",
            "  x.p = 2;",
            "  return x.p;",
            "}"));

    testSame(
        LINE_JOINER.join(
            "function f(x, pred) {",
            "  if (pred) {",
            "    x.p = 1;",
            "  }",
            "  x.p = 2;",
            "}"));
  }

  public void testCircularPropChain() {
    testSame(
        LINE_JOINER.join(
            "function f(x, y) {",
            "  x.p = {};",
            "  x.p.y.p.z = 10;",
            "  x.p = {};",
            "}"));
  }

  public void testDifferentQualifiedNames() {
    testSame(
        LINE_JOINER.join(
            "function f(x, y) {",
            "  x.p = 10;",
            "  y.p = 11;",
            "}"));
  }

  public void testGetPropContainsNonQualifiedNames() {
    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  foo(x).p = 10;",
            "  foo(x).p = 11;",
            "}"));

    testSame(
        LINE_JOINER.join(
            "function f(x) {",
            "  (x = 10).p = 10;",
            "  (x = 10).p = 11;",
            "}"));
  }

  public void testEs6Constrcutor() {
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);

    testSame(
        LINE_JOINER.join(
            "class Foo {",
            "  constructor() {",
            "    this.p = 123;",
            "    var z = this.p;",
            "    this.p = 234;",
            "  }",
            "}"));

    test(
        LINE_JOINER.join(
            "class Foo {",
            "  constructor() {",
            "    this.p = 123;",
            "    this.p = 234;",
            "  }",
            "}"),
        LINE_JOINER.join(
            "class Foo {",
            "  constructor() {",
            "    123;",
            "    this.p = 234;",
            "  }",
            "}"));

    if (ASSUME_CONSTRUCTORS_HAVENT_ESCAPED) {
      test(
          LINE_JOINER.join(
              "class Foo {",
              "  constructor() {",
              "    this.p = 123;",
              "    foo();",
              "    this.p = 234;",
              "  }",
              "}"),
          LINE_JOINER.join(
              "class Foo {",
              "  constructor() {",
              "    123;",
              "    foo();",
              "    this.p = 234;",
              "  }",
              "}"));
    }

    setAcceptedLanguage(LanguageMode.ECMASCRIPT5);
  }

  public void testGetter() {
    testSame(
        LINE_JOINER.join(
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
        LINE_JOINER.join(
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

  public void testGetter_afterDeadAssignment() {
    testSame(
        LINE_JOINER.join(
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

  public void testGetter_onDifferentType() {
    testSame(
        LINE_JOINER.join(
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

  public void testSetter() {
    testSame(
        LINE_JOINER.join(
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
        LINE_JOINER.join(
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

  public void testEs5Getter() {
    testSame(
        LINE_JOINER.join(
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

  public void testEs5Setter() {
    testSame(
        LINE_JOINER.join(
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

  public void testObjectDefineProperty_aliasedParams() {
    testSame(
        LINE_JOINER.join(
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
        LINE_JOINER.join(
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

  public void testObjectDefineProperty_aliasedObject() {
    testSame(
        LINE_JOINER.join(
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

  public void testObjectDefineProperty_aliasedPropName() {
    testSame(
        LINE_JOINER.join(
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
        LINE_JOINER.join(
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
        LINE_JOINER.join(
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

  public void testObjectDefineProperty_aliasedPropSet() {
    testSame(
        LINE_JOINER.join(
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

  public void testObjectDefineProperties_aliasedPropertyMap() {
    testSame(
        LINE_JOINER.join(
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
        LINE_JOINER.join(
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

  public void testObjectDefineProperties_aliasedObject() {
    test(
        LINE_JOINER.join(
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
        LINE_JOINER.join(
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

  public void testPropertyDefinedInExterns() {
    String externs = LINE_JOINER.join(
        "var window = {};",
        "/** @type {number} */ window.innerWidth",
        "/** @constructor */",
        "var Image = function() {};",
        "/** @type {string} */ Image.prototype.src;"
    );

    testSame(
        externs,
        LINE_JOINER.join(
            "function z() {",
            "  window.innerWidth = 10;",
            "  window.innerWidth = 20;",
            "}"),
        null);

    testSame(
        externs,
        LINE_JOINER.join(
            "function z() {",
            "  var img = new Image();",
            "  img.src = '';",
            "  img.src = 'foo.bar';",
            "}"),
        null);

    testSame(
        externs,
        LINE_JOINER.join(
            "function z(x) {",
            "  x.src = '';",
            "  x.src = 'foo.bar';",
            "}"),
        null);
  }

  public void testJscompInherits() {
    test(
        LINE_JOINER.join(
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
        LINE_JOINER.join(
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

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new DeadPropertyAssignmentElimination(compiler);
  }
}
