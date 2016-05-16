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

/**
 * @fileoverview
 * Tests transpilation of template literals.
 *
 * @author moz@google.com (Michael Zhou)
 */
goog.require('goog.testing.jsunit');

function testEmpty() {
  assertEquals(``, "");
}

function testNoSubstitution() {
  assertEquals(`hello world`, "hello world");
}

function testNewline() {
  assertEquals(`hello
world`, "hello\nworld");
  assertEquals(`hello\nworld`, "hello\nworld");
  assertEquals(`\n`, '\n');
  assertEquals(`\r`, '\r');
  assertEquals(`\r\n`, '\r\n');
}

function testLineContinuation() {
  assertEquals(`

`, '\n\n');
}

function testDoubleSlash() {
  assertEquals(`\\"`, '\\"');
  assertEquals(`"\\`, '"\\');
}

function testDollarSigns() {
  assertEquals(`$$$`, '$$$');
  // TODO(moz): We ignore the backslash in the following case. Add a warning
  // because the user probably intended to type an escape sequence.
  assertEquals(`\$$$`, '$$$');
}

function testSubstitution() {
  var world = "world";
  assertEquals(`hello ${world}`, "hello world");

  var a, b, c = 3;
  assertEquals(`hello ${a} ${b} ${c}`, "hello undefined undefined 3");
}

function testTaggedTemplateLiteral() {
  function tag(strings, ...values) {
    assertArrayEquals(['a', 'b'], strings);
    assertArrayEquals([42], values);
  }
  tag`a${42}b`;
}

function testCallExpression() {
  var i = 0;
  function f() {
    return function(strings, ...values) {
      return ++i;
    }
  }
  assertEquals(1, f()`foo`);
}

function testMemberExpression() {
  var obj = {
    a: function(strings, ...values) {
      return 3;
    }
  };
  assertEquals(3, obj.a`foo`);
}

function testCommaExpression() {
  var i = 0;
  assertEquals('24', `${i++, 2}${i++, 4}`);
  assertEquals('6', `${i++, 6}`);

  function tag(strings, ...values) {
    assertEquals(2, values[0]);
    assertEquals(4, values[1]);
    return values[0] + values[1];
  }
  assertEquals(6, tag`${i++, 2}${i++, 4}`);
}

function testRaw() {
  function r(strings, ...values) {
    assertArrayEquals(["a\tb"], strings);
    assertArrayEquals(["a\\tb"], strings.raw);
  }
  r`a\tb`;
}

function testRawWithLineContinuation() {
  var x = 3;
  function r(strings, ...values) {
    assertArrayEquals(['\n', '\n'], strings);
    assertArrayEquals(['\n', '\\n'], strings.raw);
  }
  r`
${x}\n`;
}

// Just check that we don't produce invalid code.
// See https://github.com/google/closure-compiler/issues/1299
function testGH1299() {
  function html(strings, ...values) {
    return ''
  }

  let x = 'text';
  assertEquals('', html`<p class="foo">${x}</p>`);
  assertEquals('', html`<p class='foo'>${x}</p>`);
}

function testUnicode() {
  function r(strings, ...values) {
    assertArrayEquals(['☃'], strings);
    assertArrayEquals([], values);
  }

  r`☃`;
}

