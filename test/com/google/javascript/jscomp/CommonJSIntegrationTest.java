/*
 * Copyright 2013 The Closure Compiler Authors.
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
import com.google.javascript.jscomp.deps.ModuleLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for type-checking across commonjs modules.
 *
 * @author nicholas.j.santos@gmail.com (Nick Santos)
 */

@RunWith(JUnit4.class)
public final class CommonJSIntegrationTest extends IntegrationTestCase {
  @Test
  public void testCrossModuleCtorCall() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {} module.exports = Hello;",
          "var Hello = require('./i0'); var hello = new Hello();"
        },
        new String[] {
          "/** @const */ var module$i0 = {}; /** @const */ module$i0.default = function (){};",
          LINE_JOINER.join("var Hello = module$i0.default;", "var hello = new module$i0.default();")
        });
  }

  @Test
  public void testCrossModuleCtorCall2() {
    test(createCompilerOptions(),
         new String[] {
           "/** @constructor */ function Hello() {} " +
           "module.exports = Hello;",

           "var Hello = require('./i0');" +
           "var hello = new Hello(1);"
         },
         TypeCheck.WRONG_ARGUMENT_COUNT);
  }

  @Test
  public void testCrossModuleTypeAnnotation() {
    test(
        createCompilerOptions(),
        LINE_JOINER.join(
            "/** @constructor */ function Hello() {} ",
            "/** @type {!Hello} */ var hello = new Hello();",
            "module.exports = Hello;"),
        LINE_JOINER.join(
            "/** @const */ var module$i0 = {};",
            "module$i0.default = function () {};",
            "var hello$$module$i0 = new module$i0.default();"));
  }

  @Test
  public void testCrossModuleTypeAnnotation2() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {} module.exports = Hello;",
          "var Hello = require('./i0'); /** @type {!Hello} */ var hello = new Hello();"
        },
        new String[] {
          LINE_JOINER.join(
          "/** @const */ var module$i0 = {};",
          "/** @const */ module$i0.default = /** @constructor */ function() {};"),
          "var Hello = module$i0.default; var hello = new module$i0.default();"
        });
  }

  @Test
  public void testCrossModuleTypeAnnotation3() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {} module.exports = Hello;",
          "var Hello = require('./i0'); /** @type {!Hello} */ var hello = 1;"
        },
        TypeValidator.TYPE_MISMATCH_WARNING);
  }

  @Test
  public void testCrossModuleSubclass1() {
    test(
        createCompilerOptions(),
        new String[] {
          LINE_JOINER.join("/** @constructor */ function Hello() {}", "module.exports = Hello;"),
          LINE_JOINER.join(
              "var Hello = require('./i0');",
              "var util = {inherits: function (x, y){}};",
              "/**\n",
              " * @constructor\n",
              " * @extends {Hello}\n",
              " */\n",
              "var SubHello = function () {};",
              "util.inherits(SubHello, Hello);")
        },
        new String[] {
          "/** @const */ var module$i0 = {}; /** @const */ module$i0.default = function (){};",
          LINE_JOINER.join(
              "var Hello = module$i0.default;",
              "var util = { inherits : function(x,y) {} };",
              "var SubHello = function() {};",
              "util.inherits(SubHello, module$i0.default);")
        });
  }

  @Test
  public void testCrossModuleSubclass2() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {} module.exports = Hello;",
          LINE_JOINER.join(
              "var Hello = require('./i0');",
              "var util = {inherits: function (x, y){}};",
              "/**",
              " * @constructor",
              " * @extends {Hello}",
              " */",
              "function SubHello() {}",
              "util.inherits(SubHello, Hello);")
        },
        new String[] {
          "/** @const */ var module$i0 = {}; module$i0.default = function (){};",
          LINE_JOINER.join(
              "var Hello = module$i0.default;",
              "var util = { inherits : function(x,y) {} };",
              "function SubHello(){}",
              "util.inherits(SubHello, module$i0.default);")
        });
  }

  @Test
  public void testCrossModuleSubclass3() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {}  module.exports = Hello;",
          LINE_JOINER.join(
              "var Hello = require('./i0');",
              "var util = {inherits: function (x, y){}};",
              "/**",
              " * @constructor",
              " * @extends {Hello}",
              " */",
              "function SubHello() { Hello.call(this); }",
              "util.inherits(SubHello, Hello);")
        },
        new String[] {
          LINE_JOINER.join(
              "/** @const */ var module$i0 = {};",
              "/** @constructor */ module$i0.default = function (){};"),
          LINE_JOINER.join(
              "var Hello = module$i0.default;",
              "var util = { inherits : function(x,y) {} };",
              "function SubHello(){ module$i0.default.call(this); }",
              "util.inherits(SubHello, module$i0.default);")
        });
  }

  @Test
  public void testCrossModuleSubclass4() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {}  module.exports = {Hello: Hello};",
          LINE_JOINER.join(
              "var i0 = require('./i0');",
              "var util = {inherits: function (x, y) {}};",
              "/**",
              " * @constructor",
              " * @extends {i0.Hello}",
              " */",
              "function SubHello() { i0.Hello.call(this); }",
              "util.inherits(SubHello, i0.Hello);")
        },
        new String[] {
          LINE_JOINER.join(
              "/** @const */ var module$i0 = { /** @const */ default: {}};",
              "module$i0.default.Hello = /** @constructor */ function (){};"),
          LINE_JOINER.join(
              "var i0 = module$i0.default;",
              "var util = { inherits : function(x,y) {} };",
              "function SubHello(){ module$i0.default.Hello.call(this); }",
              "util.inherits(SubHello, module$i0.default.Hello);")
        });
  }

  @Test
  public void testCrossModuleSubclass5() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {} module.exports = Hello;",
          LINE_JOINER.join(
              "var Hello = require('./i0');",
              "var util = {inherits: function (x, y){}};",
              "/**",
              " * @constructor",
              " * @extends {./i0}",
              " */",
              "function SubHello() { Hello.call(this); }",
              "util.inherits(SubHello, Hello);")
        },
        new String[] {
          LINE_JOINER.join(
              "/** @const */ var module$i0 = {};",
              "/** @constructor */ module$i0.default = function (){};"),
          LINE_JOINER.join(
              "var Hello = module$i0.default;",
              "var util = { inherits : function(x,y) {} };",
              "function SubHello(){ module$i0.default.call(this); }",
              "util.inherits(SubHello, module$i0.default);")
        });
  }

  @Test
  public void testCrossModuleSubclass6() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {} module.exports = {Hello: Hello};",
          LINE_JOINER.join(
              "var i0 = require('./i0');",
              "var util = {inherits: function (x, y){}};",
              "/**",
              " * @constructor",
              " * @extends {./i0.Hello}",
              " */",
              "function SubHello() { i0.Hello.call(this); }",
              "util.inherits(SubHello, i0.Hello);")
        },
        new String[] {
          LINE_JOINER.join(
              "/** @const */ var module$i0 = { /** @const */ default: {}};",
              "module$i0.default.Hello = /** @constructor */ function (){};"),
          LINE_JOINER.join(
              "var i0 = module$i0.default;",
              "var util = {inherits:function(x,y){}};",
              "function SubHello(){ module$i0.default.Hello.call(this); }",
              "util.inherits(SubHello, module$i0.default.Hello);")
        });
  }

  @Override
  protected CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT5);
    options.setLanguageOut(LanguageMode.ECMASCRIPT3);
    options.setCodingConvention(new GoogleCodingConvention());
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setProcessCommonJSModules(true);
    options.setClosurePass(true);
    options.setModuleResolutionMode(ModuleLoader.ResolutionMode.NODE);
    return options;
  }
}
