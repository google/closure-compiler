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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.deps.ModuleLoader;
import com.google.javascript.jscomp.deps.ModuleLoader.PathEscaper;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@RunWith(JUnit4.class)
public final class Es6RewriteModulesToCommonJsModulesTest extends CompilerTestCase {
  private List<String> moduleRoots;
  private ModuleLoader.ResolutionMode resolutionMode;
  private ImmutableMap<String, String> prefixReplacements;
  private PathEscaper pathEscaper;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    // ECMASCRIPT5 to trigger module processing after parsing.
    setLanguage(LanguageMode.ECMASCRIPT_2015, LanguageMode.ECMASCRIPT5);
    enableRunTypeCheckAfterProcessing();
    moduleRoots = ImmutableList.of();
    resolutionMode = ModuleLoader.ResolutionMode.BROWSER;
    prefixReplacements = ImmutableMap.of();
    pathEscaper = PathEscaper.ESCAPE;
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    // ECMASCRIPT5 to Trigger module processing after parsing.
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.ERROR);
    options.setModuleRoots(moduleRoots);
    options.setModuleResolutionMode(resolutionMode);
    options.setBrowserResolverPrefixReplacements(prefixReplacements);
    options.setPathEscaper(pathEscaper);
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

  @Test
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

  @Test
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

  @Test
  public void testImport() {
    test(
        "import * as x from 'other.js'; use(x, x.y);",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  var x = $$require('other.js');",
            "  use(x, x.y);",
            "}, 'testcode', ['other.js']);"));

    test(
        "import Default, {x, y as z} from 'bogus.js'; use(x, z, Default);",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  var module$bogus = $$require('bogus.js');",
            "  use(module$bogus.x, module$bogus.y, module$bogus.default);",
            "}, 'testcode', ['bogus.js']);"));

    test(
        "import Default, * as x from 'other.js'; use(x, x.y, Default);",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  var x = $$require('other.js');",
            "  use(x, x.y, x.default);",
            "}, 'testcode', ['other.js']);"));

    test(
        "import First from 'other.js'; import {Second} from 'other.js'; use(First, Second);",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  var module$other = $$require('other.js');",
            "  use(module$other.default, module$other.Second);",
            "}, 'testcode', ['other.js']);"));

    test(
        "import First from 'first.js'; import {Second} from 'second.js'; use(First, Second);",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  var module$first = $$require('first.js');",
            "  var module$second = $$require('second.js');",
            "  use(module$first.default, module$second.Second);",
            "}, 'testcode', ['first.js', 'second.js']);"));
  }

  @Test
  public void testImportAndExport() {
    test(
        "export var x; import {y} from 'other.js';",
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
            "  var module$other = $$require('other.js');",
            "  var x;",
            "}, 'testcode', ['other.js']);"));

    test(
        "import {y} from 'other.js'; export var x;",
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
            "  var module$other = $$require('other.js');",
            "  var x;",
            "}, 'testcode', ['other.js']);"));

    test(
        "import {y as Y} from 'other.js'; export {Y as X};",
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
            "  var module$other = $$require('other.js');",
            "}, 'testcode', ['other.js']);"));
  }

  @Test
  public void testExportFrom() {
    test(
        "export {x, y as z} from 'other.js';",
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
            "  var module$other = $$require('other.js');",
            "}, 'testcode', ['other.js']);"));
  }

  @Test
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

  @Test
  public void testProtocolAndDomainAreRemovedInRegisteredPathWhenNotEscaping() {
    pathEscaper = PathEscaper.CANONICALIZE_ONLY;
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
                    "}, 'test.js', []);"))));
  }

  @Test
  public void testProtocolInImportPathIsError() {
    testError("import * as foo from 'file://imported.js';", Es6ToEs3Util.CANNOT_CONVERT);
  }

  @Test
  public void testRegisteredPathDoesNotIncludeModuleRoot() {
    moduleRoots = ImmutableList.of("module/root/");

    test(
        srcs(SourceFile.fromCode("module/root/test.js", "export {};")),
        expected(
            SourceFile.fromCode(
                "module/root/test.js",
                lines(
                    "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
                    "  'test pragma';",
                    "}, 'test.js', []);"))));
  }

  @Test
  public void testImportPathDoesNotIncludeModuleRoot() {
    moduleRoots = ImmutableList.of("module/root/");

    test(
        srcs(SourceFile.fromCode("not/root/test.js", "import * as foo from 'module/root/foo.js';")),
        expected(
            SourceFile.fromCode(
                "not/root/test.js",
                lines(
                    "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
                    "  'test pragma';",
                    "  var foo = $$require('foo.js');",
                    "}, 'not/root/test.js', ['foo.js']);"))));
  }

  @Test
  public void testImportPathWithBrowserPrefixReplacementResolution() {
    resolutionMode = ModuleLoader.ResolutionMode.BROWSER_WITH_TRANSFORMED_PREFIXES;
    prefixReplacements = ImmutableMap.of("@root/", "");

    test(
        srcs(SourceFile.fromCode("not/root/test.js", "import * as foo from '@root/foo.js';")),
        expected(
            SourceFile.fromCode(
                "not/root/test.js",
                lines(
                    "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
                    "  'test pragma';",
                    "  var foo = $$require('foo.js');",
                    "}, 'not/root/test.js', ['foo.js']);"))));
  }

  @Test
  public void testExportStarFrom() {
    test("export * from './other.js';",
        lines(
            "$jscomp.registerAndLoadModule(function($$require, $$exports, $$module) {",
            "  'test pragma';",
            "  var module$other = $$require('other.js');",
            "  $$module.exportAllFrom(module$other);",
            "}, 'testcode', ['other.js']);"));
  }
}
