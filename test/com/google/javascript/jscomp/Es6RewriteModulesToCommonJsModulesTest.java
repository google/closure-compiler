/*
 * Copyright 2018 The Closure Compiler Authors.
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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;


public final class Es6RewriteModulesToCommonJsModulesTest extends CompilerTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // ECMASCRIPT5 to trigger module processing after parsing.
    setLanguage(LanguageMode.ECMASCRIPT_2015, LanguageMode.ECMASCRIPT5);
    enableRunTypeCheckAfterProcessing();
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    // ECMASCRIPT5 to Trigger module processing after parsing.
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.ERROR);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new Es6RewriteModulesToCommonJsModules(compiler, "test pragma");
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  public void testExports() {
    test(
        "export var x;",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  Object.defineProperties($$exports, {",
            "    x: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return x;",
            "      },",
            "    },",
            "  });",
            "  var x;",
            "}, 'testcode', []);"));

    test(
        "var x;\nexport {x}",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  Object.defineProperties($$exports, {",
            "    x: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return x;",
            "      },",
            "    },",
            "  });",
            "  var x;",
            "}, 'testcode', []);"));

    test(
        "var x;\nexport {x as y}",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  Object.defineProperties($$exports, {",
            "    y: {",
            "      enumerable: true,",
            "      get: function() {",
            "       return x;",
            "      },",
            "    },",
            "  });",
            "  var x;",
            "}, 'testcode', []);"));

    test(
        "export function f() {}",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  Object.defineProperties($$exports, {",
            "    f: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return f;",
            "      },",
            "    },",
            "  });",
            "  function f() {}",
            "}, 'testcode', []);"));

    test(
        "export class c {}",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  Object.defineProperties($$exports, {",
            "    c: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return c;",
            "      },",
            "    },",
            "  });",
            "  class c {}",
            "}, 'testcode', []);"));

    test(
        "export default 123;",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  Object.defineProperties($$exports, {",
            "    default: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return $$default;",
            "      },",
            "    },",
            "  });",
            "  const $$default = 123;",
            "}, 'testcode', []);"));

    test(
        lines("const x = 0;", "export default x;", "x++;"),
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  Object.defineProperties($$exports, {",
            "    default: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return $$default;",
            "      },",
            "    },",
            "  });",
            "  const x = 0;",
            "  const $$default = x;",
            "  x++",
            "}, 'testcode', []);"));

    test(
        lines("export default function f() { return 5; }", "f = () => 0;"),
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  Object.defineProperties($$exports, {",
            "    default: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return f;",
            "      },",
            "    },",
            "  });",
            "  function f() { return 5; }",
            "  f = () => 0;",
            "}, 'testcode', []);"));

    test(
        "export var x, y, z;",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  Object.defineProperties($$exports, {",
            "    x: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return x;",
            "      },",
            "    },",
            "    y: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return y;",
            "      },",
            "    },",
            "    z: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return z;",
            "      },",
            "    },",
            "  });",
            "  var x, y, z;",
            "}, 'testcode', []);"));

    // Exports need to be ordered with the natural string ordering.
    test(
        "export var z, y, x;",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  Object.defineProperties($$exports, {",
            "    x: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return x;",
            "      },",
            "    },",
            "    y: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return y;",
            "      },",
            "    },",
            "    z: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return z;",
            "      },",
            "    },",
            "  });",
            "  var z, y, x;",
            "}, 'testcode', []);"));
  }

  public void testExportDestructureDeclaration() {
    test("export let {a, c:b} = obj;",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  Object.defineProperties($$exports, {",
            "    a: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return a;",
            "      },",
            "    },",
            "    b: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return b;",
            "      },",
            "    },",
            "  });",
            "  let {a, c:b} = obj;",
            "}, 'testcode', []);"));

    test("export let [a, b] = obj;",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  Object.defineProperties($$exports, {",
            "    a: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return a;",
            "      },",
            "    },",
            "    b: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return b;",
            "      },",
            "    },",
            "  });",
            "  let [a, b] = obj;",
            "}, 'testcode', []);"));

    test("export let {a, b:[c,d]}  = obj;",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  Object.defineProperties($$exports, {",
            "    a: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return a;",
            "      },",
            "    },",
            "    c: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return c;",
            "      },",
            "    },",
            "    d: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return d;",
            "      },",
            "    },",
            "  });",
            "  let {a, b:[c,d]}  = obj;",
            "}, 'testcode', []);"));
  }

  public void testImport() {
    test(
        "import * as x from 'other'; use(x, x.y);",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  var module$other = $$require('other');",
            "  use(module$other, module$other.y);",
            "}, 'testcode', ['other']);"));

    test(
        "import Default, {x, y as z} from './././bogus'; use(x, z, Default);",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  var module$bogus = $$require('./././bogus');",
            "  use(module$bogus.x, module$bogus.y, module$bogus.default);",
            "}, 'testcode', ['./././bogus']);"));

    test(
        "import First from 'other'; import {Second} from 'other'; use(First, Second);",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  var module$other = $$require('other');",
            "  use(module$other.default, module$other.Second);",
            "}, 'testcode', ['other']);"));

    test(
        "import First from 'first'; import {Second} from 'second'; use(First, Second);",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  var module$first = $$require('first');",
            "  var module$second = $$require('second');",
            "  use(module$first.default, module$second.Second);",
            "}, 'testcode', ['first', 'second']);"));
  }

  public void testImportAndExport() {
    test(
        "export var x; import {y} from 'other';",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  Object.defineProperties($$exports, {",
            "    x: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return x;",
            "      },",
            "    },",
            "  });",
            "  var module$other = $$require('other');",
            "  var x;",
            "}, 'testcode', ['other']);"));

    test(
        "import {y} from 'other'; export var x;",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  Object.defineProperties($$exports, {",
            "    x: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return x;",
            "      },",
            "    },",
            "  });",
            "  var module$other = $$require('other');",
            "  var x;",
            "}, 'testcode', ['other']);"));

    test(
        "import {y as Y} from 'other'; export {Y as X};",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  Object.defineProperties($$exports, {",
            "    X: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return module$other.y;",
            "      },",
            "    },",
            "  });",
            "  var module$other = $$require('other');",
            "}, 'testcode', ['other']);"));
  }

  public void testExportFrom() {
    test(
        "export {x, y as z} from 'other';",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  Object.defineProperties($$exports, {",
            "    x: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return module$other.x;",
            "      },",
            "    },",
            "    z: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return module$other.y;",
            "      },",
            "    },",
            "  });",
            "  var module$other = $$require('other');",
            "}, 'testcode', ['other']);"));
  }

  public void testExportWithArguments() {
    test(
        lines("export default function f() { return arguments[1]; }"),
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  Object.defineProperties($$exports, {",
            "    default: {",
            "      enumerable: true,",
            "      get: function() {",
            "        return f;",
            "      },",
            "    },",
            "  });",
            "function f() { return arguments[1]; }",
            "}, 'testcode', []);"));
  }

  public void testFileNameIsPreserved() {
    test(
        srcs(SourceFile.fromCode("https://example.domain.google.com/test.js", "export var x;")),
        expected(
            SourceFile.fromCode(
                "https://example.domain.google.com/test.js",
                lines(
                    "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
                    "  'test pragma';",
                    "  Object.defineProperties($$exports, {",
                    "    x: {",
                    "      enumerable: true,",
                    "      get: function() {",
                    "        return x;",
                    "      },",
                    "    },",
                    "  });",
                    "  var x;",
                    "}, 'https://example.domain.google.com/test.js', []);"))));
  }
}
