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


/**
 * Tests for type-checking across commonjs modules.
 *
 * @author nicholas.j.santos@gmail.com (Nick Santos)
 */

public final class CommonJSIntegrationTest extends IntegrationTestCase {
  public void testCrossModuleCtorCall() {
    test(createCompilerOptions(),
         new String[] {
           "/** @constructor */ function Hello() {} " +
           "module.exports = Hello;",

           "var Hello = require('./i0');" +
           "var hello = new Hello();"
         },
         new String[] {
           "var module$i0 = {};" +
           "function Hello$$module$i0(){}" +
           "module$i0.default = Hello$$module$i0;",

           "var module$i1 = {};" +
           "var Hello$$module$i1 = Hello$$module$i0;" +
           "var hello$$module$i1 = new Hello$$module$i1();"
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
  }

  public void testCrossModuleTypeAnnotation() {
    test(createCompilerOptions(),
         new String[] {
           "/** @constructor */ function Hello() {} " +
           "/** @type {!Hello} */ var hello = new Hello();" +
           "module.exports = Hello;"
         },
         new String[] {
           "var module$i0 = {};" +
           "function Hello$$module$i0(){}" +
           "var hello$$module$i0 = new Hello$$module$i0();" +
           "module$i0.default = Hello$$module$i0;"
         });
  }

  public void testCrossModuleTypeAnnotation2() {
    test(createCompilerOptions(),
         new String[] {
           "/** @constructor */ function Hello() {} " +
           "module.exports = Hello;",
           "var Hello = require('./i0');" +
           "/** @type {!Hello} */ var hello = new Hello();"
         },
         new String[] {
           "var module$i0 = {};" +
           "function Hello$$module$i0(){}" +
           "module$i0.default = Hello$$module$i0;",
           "var module$i1 = {};" +
           "var Hello$$module$i1 = Hello$$module$i0;" +
           "var hello$$module$i1 = new Hello$$module$i1();"
         });
  }

  public void testCrossModuleTypeAnnotation3() {
    test(createCompilerOptions(),
         new String[] {
           "/** @constructor */ function Hello() {} " +
           "module.exports = Hello;",
           "var Hello = require('./i0');" +
           "/** @type {!Hello} */ var hello = 1;"
         },
         TypeValidator.TYPE_MISMATCH_WARNING);
  }

  public void testMultipleExportAssignments1() {
    test(createCompilerOptions(),
        new String[] {
            "/** @constructor */ function Hello() {} " +
                "module.exports = Hello;" +
                "/** @constructor */ function Bar() {} " +
                "Bar.prototype.foobar = function() { alert('foobar'); };" +
                "module.exports = Bar;",
            "var Foobar = require('./i0');" +
                "var show = new Foobar();" +
                "show.foobar();"
        },
        new String[] {
            "var module$i0 = {};" +
                "function Hello$$module$i0(){} " +
                "module$i0.default = Hello$$module$i0;" +
                "function Bar$$module$i0(){} " +
                "Bar$$module$i0.prototype.foobar=function(){alert(\"foobar\")};" +
                "module$i0.default=Bar$$module$i0;",
            "var module$i1 = {};" +
                "var Foobar$$module$i1=module$i0.default;" +
                "var show$$module$i1=new Foobar$$module$i1();" +
                "show$$module$i1.foobar();"
        });
  }

  public void testMultipleExportAssignments2() {
    test(createCompilerOptions(),
        new String[] {
            "/** @constructor */ function Hello() {} " +
                "module.exports.foo = Hello;" +
                "/** @constructor */ function Bar() {} " +
                "Bar.prototype.foobar = function() { alert('foobar'); };" +
                "module.exports.foo = Bar;",
            "var Foobar = require('./i0');" +
                "var show = new Foobar.foo();" +
                "show.foobar();"
        },
        new String[] {
            "var module$i0 = {};" +
                "module$i0.default = {};" +
                "function Hello$$module$i0(){} " +
                "module$i0.default.foo = Hello$$module$i0;" +
                "function Bar$$module$i0(){} " +
                "Bar$$module$i0.prototype.foobar=function(){alert(\"foobar\")};" +
                "module$i0.default.foo=Bar$$module$i0;",
            "var module$i1 = {};" +
                "var Foobar$$module$i1=module$i0.default;" +
                "var show$$module$i1=new Foobar$$module$i1.foo();" +
                "show$$module$i1.foobar();"
        });
  }

  public void testMultipleExportAssignments3() {
    test(createCompilerOptions(),
        new String[] {
            "/** @constructor */ function Hello() {} " +
                "module.exports.foo = Hello;" +
                "/** @constructor */ function Bar() {} " +
                "Bar.prototype.foobar = function() { alert('foobar'); };" +
                "exports.foo = Bar;",
            "var Foobar = require('./i0');" +
                "var show = new Foobar.foo();" +
                "show.foobar();"
        },
        new String[] {
            "var module$i0 = {};" +
                "module$i0.default = {};" +
                "function Hello$$module$i0(){} " +
                "module$i0.default.foo = Hello$$module$i0;" +
                "function Bar$$module$i0(){} " +
                "Bar$$module$i0.prototype.foobar=function(){alert(\"foobar\")};" +
                "module$i0.default.foo=Bar$$module$i0;",
            "var module$i1 = {};" +
                "var Foobar$$module$i1=module$i0.default;" +
                "var show$$module$i1=new Foobar$$module$i1.foo();" +
                "show$$module$i1.foobar();"
        });
  }

  public void testCrossModuleSubclass1() {
    test(createCompilerOptions(),
         new String[] {
           "/** @constructor */ function Hello() {} " +
           "module.exports = Hello;",
           "var Hello = require('./i0');" +
           "var util = {inherits: function (x, y){}};" +
           "/**\n" +
           " * @constructor\n" +
           " * @extends {Hello}\n" +
           " */\n" +
           "var SubHello = function () {};" +
           "util.inherits(SubHello, Hello);"
         },
         new String[] {
           "var module$i0 = {};" +
           "function Hello$$module$i0(){}" +
           "module$i0.default=Hello$$module$i0;",
           "var module$i1={};" +
           "var Hello$$module$i1=Hello$$module$i0;" +
           "var util$$module$i1={inherits:function(x,y){}};" +
           "var SubHello$$module$i1=function(){};" +
           "util$$module$i1.inherits(SubHello$$module$i1,Hello$$module$i1);"
         });
  }

  public void testCrossModuleSubclass2() {
    test(createCompilerOptions(),
         new String[] {
           "/** @constructor */ function Hello() {} " +
           "module.exports = Hello;",
           "var Hello = require('./i0');" +
           "var util = {inherits: function (x, y){}};" +
           "/**\n" +
           " * @constructor\n" +
           " * @extends {Hello}\n" +
           " */\n" +
           "function SubHello() {}" +
           "util.inherits(SubHello, Hello);"
         },
         new String[] {
           "var module$i0 = {};" +
           "function Hello$$module$i0(){}" +
           "module$i0.default=Hello$$module$i0;",
           "var module$i1={};" +
           "var Hello$$module$i1=Hello$$module$i0;" +
           "var util$$module$i1={inherits:function(x,y){}};" +
           "function SubHello$$module$i1(){}" +
           "util$$module$i1.inherits(SubHello$$module$i1,Hello$$module$i1);"
         });
  }

  public void testCrossModuleSubclass3() {
    test(createCompilerOptions(),
         new String[] {
           "/** @constructor */ function Hello() {} " +
           "module.exports = Hello;",
           "var Hello = require('./i0');" +
           "var util = {inherits: function (x, y){}};" +
           "/**\n" +
           " * @constructor\n" +
           " * @extends {Hello}\n" +
           " */\n" +
           "function SubHello() { Hello.call(this); }" +
           "util.inherits(SubHello, Hello);"
         },
         new String[] {
           "var module$i0 = {};" +
           "function Hello$$module$i0(){}" +
           "module$i0.default=Hello$$module$i0;",
           "var module$i1={};" +
           "var Hello$$module$i1=Hello$$module$i0;" +
           "var util$$module$i1={inherits:function(x,y){}};" +
           "function SubHello$$module$i1(){ Hello$$module$i1.call(this); }" +
           "util$$module$i1.inherits(SubHello$$module$i1,Hello$$module$i1);"
         });
  }

  public void testCrossModuleSubclass4() {
    test(createCompilerOptions(),
         new String[] {
           "/** @constructor */ function Hello() {} " +
           "module.exports = {Hello: Hello};",
           "var i0 = require('./i0');" +
           "var util = {inherits: function (x, y){}};" +
           "/**\n" +
           " * @constructor\n" +
           " * @extends {i0.Hello}\n" +
           " */\n" +
           "function SubHello() { i0.Hello.call(this); }" +
           "util.inherits(SubHello, i0.Hello);"
         },
         new String[] {
           "var module$i0 = {};" +
           "function Hello$$module$i0(){}" +
           "module$i0.default={Hello: Hello$$module$i0};",
           "var module$i1={};" +
           "var i0$$module$i1=module$i0.default;" +
           "var util$$module$i1={inherits:function(x,y){}};" +
           "function SubHello$$module$i1(){ i0$$module$i1.Hello.call(this); }" +
           "util$$module$i1.inherits(SubHello$$module$i1,i0$$module$i1.Hello);"
         });
  }

  public void testCrossModuleSubclass5() {
    test(createCompilerOptions(),
         new String[] {
           "/** @constructor */ function Hello() {} " +
           "module.exports = Hello;",
           "var Hello = require('./i0');" +
           "var util = {inherits: function (x, y){}};" +
           "/**\n" +
           " * @constructor\n" +
           " * @extends {./i0}\n" +
           " */\n" +
           "function SubHello() { Hello.call(this); }" +
           "util.inherits(SubHello, Hello);"
         },
         new String[] {
           "var module$i0 = {};" +
           "function Hello$$module$i0(){}" +
           "module$i0.default=Hello$$module$i0;",
           "var module$i1={};" +
           "var Hello$$module$i1=Hello$$module$i0;" +
           "var util$$module$i1={inherits:function(x,y){}};" +
           "function SubHello$$module$i1(){ Hello$$module$i1.call(this); }" +
           "util$$module$i1.inherits(SubHello$$module$i1,Hello$$module$i1);"
         });
  }

  public void testCrossModuleSubclass6() {
    test(createCompilerOptions(),
         new String[] {
           "/** @constructor */ function Hello() {} " +
           "module.exports = {Hello: Hello};",
           "var i0 = require('./i0');" +
           "var util = {inherits: function (x, y){}};" +
           "/**\n" +
           " * @constructor\n" +
           " * @extends {./i0.Hello}\n" +
           " */\n" +
           "function SubHello() { i0.Hello.call(this); }" +
           "util.inherits(SubHello, i0.Hello);"
         },
         new String[] {
           "var module$i0 = {};" +
           "function Hello$$module$i0(){}" +
           "module$i0.default={Hello: Hello$$module$i0};",
           "var module$i1={};" +
           "var i0$$module$i1=module$i0.default;" +
           "var util$$module$i1={inherits:function(x,y){}};" +
           "function SubHello$$module$i1(){ i0$$module$i1.Hello.call(this); }" +
           "util$$module$i1.inherits(SubHello$$module$i1,i0$$module$i1.Hello);"
         });
  }

  @Override
  protected CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setCodingConvention(new GoogleCodingConvention());
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setProcessCommonJSModules(true);
    options.setClosurePass(true);
    options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT5);
    options.setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT3);
    return options;
  }
}
