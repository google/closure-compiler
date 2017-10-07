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

import com.google.javascript.jscomp.deps.ModuleLoader;

/**
 * Tests for type-checking across commonjs modules.
 *
 * @author nicholas.j.santos@gmail.com (Nick Santos)
 */

public final class CommonJSIntegrationTest extends IntegrationTestCase {
  public void testCrossModuleCtorCall() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {} module.exports = Hello;",
          "var Hello = require('./i0'); var hello = new Hello();"
        },
        new String[] {
          "var module$i0 = {}; module$i0.cjs = function (){};",
          LINE_JOINER.join(
              "var Hello = module$i0.cjs;",
              "var hello = new module$i0.cjs();")
        });

    test(
        createEs6CompilerOptions(),
        new String[] {
            "/** @constructor */ function Hello() {} export default Hello;",
            "var Hello = require('./i0').default; var hello = new Hello();"
        },
        new String[] {
            LINE_JOINER.join(
                "var module$i0 = {};",
                "function Hello$$module$i0(){}",
                "var $jscompDefaultExport$$module$i0=Hello$$module$i0;",
                "module$i0.default=$jscompDefaultExport$$module$i0;"),
            LINE_JOINER.join(
                "var Hello = module$i0.default;",
                "var hello = new module$i0.default();")
        });

    test(
        createEs6CompilerOptions(),
        new String[] {
            "/** @constructor */ function Hello() {} module.exports = Hello;",
            "import Hello from './i0'; var hello = new Hello();"
        },
        new String[] {
            LINE_JOINER.join(
                "var module$i0 = {};",
                "module$i0.default = function(){}"),
            "var hello$$module$i1 = new module$i0.default();"
        });
  }

  public void testCrossModuleCtorCall2() {
    test(createCompilerOptions(),
         new String[] {
           "/** @constructor */ function Hello() {} " +
           "module.exports = Hello;",

           "var Hello = require('./i0');" +
           "var hello = new Hello(1);"
         },
         TypeCheck.WRONG_ARGUMENT_COUNT);

    test(
        createEs6CompilerOptions(),
        new String[] {
            "/** @constructor */ function Hello() {} " +
                "export default Hello;",

            "var Hello = require('./i0').default;" +
                "var hello = new Hello(1);"
        },
        NewTypeInference.WRONG_ARGUMENT_COUNT);

    test(
        createEs6CompilerOptions(),
        new String[] {
            "/** @constructor */ function Hello() {} " +
                "module.exports = Hello;",

            "import Hello from './i0';" +
                "var hello = new Hello(1);"
        },
        NewTypeInference.WRONG_ARGUMENT_COUNT);
  }

  public void testCrossModuleTypeAnnotation() {
    test(
        createCompilerOptions(),
        LINE_JOINER.join(
            "/** @constructor */ function Hello() {} ",
            "/** @type {!Hello} */ var hello = new Hello();",
            "module.exports = Hello;"),
        LINE_JOINER.join(
            "/** @const */ var module$i0 = {};",
            "module$i0.cjs = function () {};",
            "var hello$$module$i0 = new module$i0.cjs();"));

    test(
        createEs6CompilerOptions(),
        LINE_JOINER.join(
            "/** @constructor */ function Hello() {} ",
            "/** @type {!Hello} */ var hello = new Hello();",
            "export default Hello;"),
        LINE_JOINER.join(
            "var module$i0 = {};",
            "function Hello$$module$i0(){}",
            "var hello$$module$i0 = new Hello$$module$i0();",
            "var $jscompDefaultExport$$module$i0 = Hello$$module$i0;",
            "module$i0.default = $jscompDefaultExport$$module$i0;"));
  }

  public void testCrossModuleTypeAnnotation2() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {} module.exports = Hello;",
          "var Hello = require('./i0'); /** @type {!Hello} */ var hello = new Hello();"
        },
        new String[] {
          "var module$i0 = {}; module$i0.cjs = /** @constructor */ function() {};",
          "var Hello = module$i0.cjs; var hello = new module$i0.cjs();"
        });

    test(
        createEs6CompilerOptions(),
        new String[] {
            "/** @constructor */ function Hello() {} export default Hello;",
            LINE_JOINER.join(
                "var Hello = require('./i0').default;",
                "/** @type {!Hello} */ var hello = new Hello();")
        },
        new String[] {
            LINE_JOINER.join(
                "var module$i0 = {};",
                "function Hello$$module$i0(){}",
                "var $jscompDefaultExport$$module$i0 = Hello$$module$i0;",
                "module$i0.default = $jscompDefaultExport$$module$i0;"),
            "var Hello = module$i0.default; var hello = new module$i0.default();"
        });
  }

  public void testCrossModuleTypeAnnotation3() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {} module.exports = Hello;",
          "var Hello = require('./i0'); /** @type {!Hello} */ var hello = 1;"
        },
        TypeValidator.TYPE_MISMATCH_WARNING);
  }

  public void testCrossModuleSubclass1() {
    test(
        createCompilerOptions(),
        new String[] {
          LINE_JOINER.join(
              "/** @constructor */ function Hello() {}",
              "module.exports = Hello;"),
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
          "/** @const */ var module$i0 = {}; module$i0.cjs = function (){};",
          LINE_JOINER.join(
              "var Hello = module$i0.cjs;",
              "var util = { inherits : function(x,y) {} };",
              "var SubHello = function() {};",
              "util.inherits(SubHello, module$i0.cjs);")
        });

    test(
        createEs6CompilerOptions(),
        new String[] {
            LINE_JOINER.join(
                "/** @constructor */ function Hello() {}",
                "export default Hello;"),
            LINE_JOINER.join(
                "var Hello = require('./i0').default;",
                "var util = {inherits: function (x, y){}};",
                "/**",
                " * @constructor",
                " * @extends {Hello}",
                " */",
                "var SubHello = function () {};",
                "util.inherits(SubHello, Hello);")
        },
        new String[] {
            LINE_JOINER.join(
                "/** @const */ var module$i0 = {};",
                "/** @constructor */ function Hello$$module$i0(){}",
                "var $jscompDefaultExport$$module$i0=Hello$$module$i0;",
                "module$i0.default=$jscompDefaultExport$$module$i0;"),
            LINE_JOINER.join(
                "var Hello = module$i0.default;",
                "var util = { inherits : function(x,y) {} };",
                "/**",
                " * @constructor",
                " * @extends {module$i0.default}",
                " */",
                "var SubHello = function() {};",
                "util.inherits(SubHello, module$i0.default);")
        });
  }

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
          "/** @const */ var module$i0 = {}; module$i0.cjs = function (){};",
          LINE_JOINER.join(
              "var Hello = module$i0.cjs;",
              "var util = { inherits : function(x,y) {} };",
              "function SubHello(){}",
              "util.inherits(SubHello, module$i0.cjs);")
        });

    test(
        createEs6CompilerOptions(),
        new String[] {
            "/** @constructor */ function Hello() {} export default Hello;",
            LINE_JOINER.join(
                "var Hello = require('./i0').default;",
                "var util = {inherits: function (x, y){}};",
                "/**",
                " * @constructor",
                " * @extends {Hello}",
                " */",
                "function SubHello() {}",
                "util.inherits(SubHello, Hello);")
        },
        new String[] {
            LINE_JOINER.join(
                "var module$i0 = {};",
                "function Hello$$module$i0(){}",
                "var $jscompDefaultExport$$module$i0=Hello$$module$i0;",
                "module$i0.default=$jscompDefaultExport$$module$i0;"),
            LINE_JOINER.join(
                "var Hello = module$i0.default;",
                "var util = { inherits : function(x,y) {} };",
                "function SubHello(){}",
                "util.inherits(SubHello, module$i0.default);")
        });
  }

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
          "/** @const */ var module$i0 = {}; /** @constructor */ module$i0.cjs = function (){};",
          LINE_JOINER.join(
              "var Hello = module$i0.cjs;",
              "var util = { inherits : function(x,y) {} };",
              "function SubHello(){ module$i0.cjs.call(this); }",
              "util.inherits(SubHello, module$i0.cjs);")
        });

    test(
        createEs6CompilerOptions(),
        new String[] {
            "/** @constructor */ function Hello() {}  export default Hello;",
            LINE_JOINER.join(
                "var Hello = require('./i0').default;",
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
                "var module$i0 = {};",
                "function Hello$$module$i0(){}",
                "var $jscompDefaultExport$$module$i0=Hello$$module$i0;",
                "module$i0.default=$jscompDefaultExport$$module$i0;"),
            LINE_JOINER.join(
                "var Hello = module$i0.default;",
                "var util = { inherits : function(x,y) {} };",
                "function SubHello(){ module$i0.default.call(this); }",
                "util.inherits(SubHello, module$i0.default);")
        });
  }

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
              "/** @const */ var module$i0 = { /** @const */ cjs: {}};",
              "module$i0.cjs.Hello = /** @constructor */ function (){};"),
          LINE_JOINER.join(
              "var i0 = module$i0.cjs;",
              "var util = { inherits : function(x,y) {} };",
              "function SubHello(){ module$i0.cjs.Hello.call(this); }",
              "util.inherits(SubHello, module$i0.cjs.Hello);")
        });

    test(
        createEs6CompilerOptions(),
        new String[] {
            "/** @constructor */ function Hello() {}  export { Hello };",
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
                "var module$i0 = {};",
                "function Hello$$module$i0(){}",
                "module$i0.Hello=Hello$$module$i0;"),
            LINE_JOINER.join(
                "var i0 = module$i0;",
                "var util = { inherits : function(x,y) {} };",
                "function SubHello(){ module$i0.Hello.call(this); }",
                "util.inherits(SubHello, module$i0.Hello);")
        });
  }

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
          "/** @const */ var module$i0 = {}; /** @constructor */ module$i0.cjs = function (){};",
          LINE_JOINER.join(
              "var Hello = module$i0.cjs;",
              "var util = { inherits : function(x,y) {} };",
              "function SubHello(){ module$i0.cjs.call(this); }",
              "util.inherits(SubHello, module$i0.cjs);")
        });

    test(
        createEs6CompilerOptions(),
        new String[] {
            "/** @constructor */ function Hello() {} export default Hello;",
            LINE_JOINER.join(
                "var Hello = require('./i0').default;",
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
                "var module$i0 = {};",
                "function Hello$$module$i0(){}",
                "var $jscompDefaultExport$$module$i0=Hello$$module$i0;",
                "module$i0.default=$jscompDefaultExport$$module$i0;"),
            LINE_JOINER.join(
                "var Hello = module$i0.default;",
                "var util = { inherits : function(x,y) {} };",
                "function SubHello(){ module$i0.default.call(this); }",
                "util.inherits(SubHello, module$i0.default);")
        });
  }

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
              "/** @const */ var module$i0 = { /** @const */ cjs: {}};",
              "module$i0.cjs.Hello = /** @constructor */ function (){};"),
          LINE_JOINER.join(
              "var i0 = module$i0.cjs;",
              "var util = {inherits:function(x,y){}};",
              "function SubHello(){ module$i0.cjs.Hello.call(this); }",
              "util.inherits(SubHello, module$i0.cjs.Hello);")
        });

    test(
        createEs6CompilerOptions(),
        new String[] {
            "/** @constructor */ function Hello() {}  export { Hello };",
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
                "var module$i0 = {};",
                "function Hello$$module$i0(){}",
                "module$i0.Hello=Hello$$module$i0;"),
            LINE_JOINER.join(
                "var i0 = module$i0;",
                "var util = {inherits:function(x,y){}};",
                "function SubHello(){ module$i0.Hello.call(this); }",
                "util.inherits(SubHello, module$i0.Hello);")
        });
  }

  @Override
  protected CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setCodingConvention(new GoogleCodingConvention());
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setProcessCommonJSModules(true);
    options.setClosurePass(true);
    options.setModuleResolutionMode(ModuleLoader.ResolutionMode.NODE);
    return options;
  }

  protected CompilerOptions createEs6CompilerOptions() {
    CompilerOptions options = createCompilerOptions();
    options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_2015);
    options.setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT5);
    options.setNewTypeInference(true);
    return options;
  }
}
