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

package com.google.javascript.jscomp.integration;


import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.GoogleCodingConvention;
import com.google.javascript.jscomp.WarningLevel;
import com.google.javascript.jscomp.deps.ModuleLoader;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for type-checking across commonjs modules. */
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
          """
          var Hello = module$i0.default;
          var hello = new module$i0.default();
          """
        });
  }

  @Test
  public void testCrossModuleCtorCall2() {
    test(
        createCompilerOptions(),
        new String[] {
          """
          /** @constructor */ function Hello() {}
          module.exports = Hello;
          """,
          """
          var Hello = require('./i0');
          var hello = new Hello(1);
          """
        },
        DiagnosticGroups.CHECK_TYPES);
  }

  @Test
  public void testCrossModuleTypeAnnotation() {
    test(
        createCompilerOptions(),
        """
        /** @constructor */ function Hello() {}
        /** @type {!Hello} */ var hello = new Hello();
        module.exports = Hello;
        """,
        """
        /** @const */ var module$i0 = {};
        module$i0.default = function () {};
        var hello$$module$i0 = new module$i0.default();
        """);
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
          """
          /** @const */ var module$i0 = {};
          /** @const */ module$i0.default = /** @constructor */ function() {};
          """,
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
        DiagnosticGroups.CHECK_TYPES);
  }

  @Test
  public void testCrossModuleSubclass1() {
    test(
        createCompilerOptions(),
        new String[] {
          """
          /** @constructor */ function Hello() {}
          module.exports = Hello;
          """,
          """
          var Hello = require('./i0');
          var util = {inherits: function (x, y){}};
          /**

           * @constructor

           * @extends {Hello}

           */

          var SubHello = function () {};
          util.inherits(SubHello, Hello);
          """
        },
        new String[] {
          "/** @const */ var module$i0 = {}; /** @const */ module$i0.default = function (){};",
          """
          var Hello = module$i0.default;
          var util = { inherits : function(x,y) {} };
          var SubHello = function() {};
          util.inherits(SubHello, module$i0.default);
          """
        });
  }

  @Test
  public void testCrossModuleSubclass2() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {} module.exports = Hello;",
          """
          var Hello = require('./i0');
          var util = {inherits: function (x, y){}};
          /**
           * @constructor
           * @extends {Hello}
           */
          function SubHello() {}
          util.inherits(SubHello, Hello);
          """
        },
        new String[] {
          "/** @const */ var module$i0 = {}; module$i0.default = function (){};",
          """
          var Hello = module$i0.default;
          var util = { inherits : function(x,y) {} };
          function SubHello(){}
          util.inherits(SubHello, module$i0.default);
          """
        });
  }

  @Test
  public void testCrossModuleSubclass3() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {}  module.exports = Hello;",
          """
          var Hello = require('./i0');
          var util = {inherits: function (x, y){}};
          /**
           * @constructor
           * @extends {Hello}
           */
          function SubHello() { Hello.call(this); }
          util.inherits(SubHello, Hello);
          """
        },
        new String[] {
          """
          /** @const */ var module$i0 = {};
          /** @constructor */ module$i0.default = function (){};
          """,
          """
          var Hello = module$i0.default;
          var util = { inherits : function(x,y) {} };
          function SubHello(){ module$i0.default.call(this); }
          util.inherits(SubHello, module$i0.default);
          """
        });
  }

  @Test
  public void testCrossModuleSubclass4() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {}  module.exports = {Hello: Hello};",
          """
          var i0 = require('./i0');
          var util = {inherits: function (x, y) {}};
          /**
           * @constructor
           * @extends {i0.Hello}
           */
          function SubHello() { i0.Hello.call(this); }
          util.inherits(SubHello, i0.Hello);
          """
        },
        new String[] {
          """
          /** @const */ var module$i0 = { /** @const */ default: {}};
          module$i0.default.Hello = /** @constructor */ function (){};
          """,
          """
          var i0 = module$i0.default;
          var util = { inherits : function(x,y) {} };
          function SubHello(){ module$i0.default.Hello.call(this); }
          util.inherits(SubHello, module$i0.default.Hello);
          """
        });
  }

  @Test
  public void testCrossModuleSubclass5() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {} module.exports = Hello;",
          """
          var Hello = require('./i0');
          var util = {inherits: function (x, y){}};
          /**
           * @constructor
           * @extends {./i0}
           */
          function SubHello() { Hello.call(this); }
          util.inherits(SubHello, Hello);
          """
        },
        new String[] {
          """
          /** @const */ var module$i0 = {};
          /** @constructor */ module$i0.default = function (){};
          """,
          """
          var Hello = module$i0.default;
          var util = { inherits : function(x,y) {} };
          function SubHello(){ module$i0.default.call(this); }
          util.inherits(SubHello, module$i0.default);
          """
        });
  }

  @Test
  public void testCrossModuleSubclass6() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {} module.exports = {Hello: Hello};",
          """
          var i0 = require('./i0');
          var util = {inherits: function (x, y){}};
          /**
           * @constructor
           * @extends {./i0.Hello}
           */
          function SubHello() { i0.Hello.call(this); }
          util.inherits(SubHello, i0.Hello);
          """
        },
        new String[] {
          """
          /** @const */ var module$i0 = { /** @const */ default: {}};
          module$i0.default.Hello = /** @constructor */ function (){};
          """,
          """
          var i0 = module$i0.default;
          var util = {inherits:function(x,y){}};
          function SubHello(){ module$i0.default.Hello.call(this); }
          util.inherits(SubHello, module$i0.default.Hello);
          """
        });
  }

  @Test
  @Ignore // TODO(ChadKillingsworth)
  public void testCommonJSImportsESModule() {
    test(
        createCompilerOptions(),
        new String[] {
          """
          /** @constructor */ export default function Hello() {};
          export const foo = 1;
          """,
          """
          var i0 = require('./i0');
          var util = {inherits: function (x, y){}};
          /**
           * @constructor
           * @extends {./i0.default}
           */
          function SubHello() { i0.default.call(this); }
          util.inherits(SubHello, i0.default);
          const {foo} = i0;
          """
        },
        new String[] {
          """
          function Hello$$module$i0() {}
          var foo$$module$i0 = 1;
          var module$i0 = {};
          module$i0.default = Hello$$module$i0;
          module$i0.foo = foo$$module$i0;
          """,
          """
          var i0 = module$i0;
          var util = {inherits:function(x,y){}};
          function SubHello(){ module$i0.default.call(this); }
          util.inherits(SubHello, module$i0.default);
          var $jscomp$destructuring$var0 = module$i0;
          var foo = $jscomp$destructuring$var0.foo;
          """
        });
  }

  @Test
  public void testESModuleDefaultImportsCommonJS_inheritance() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {} module.exports = Hello;",
          """
          import Hello from './i0';
          var util = {inherits: function (x, y){}};
          /**
           * @constructor
           * @extends {Hello}
           */
          function SubHello() { Hello.call(this); }
          util.inherits(SubHello, Hello);
          """
        },
        new String[] {
          """
          /** @const */ var module$i0 = {};
          /** @const */ module$i0.default = /** @constructor */ function (){};
          """,
          """
          var util$$module$i1 = {inherits:function(x,y){}};
          /**
           * @constructor
           * @extends {module$i0.default}
           */
          function SubHello$$module$i1(){ module$i0.default.call(this); }
          util$$module$i1.inherits(SubHello$$module$i1, module$i0.default);
          var module$i1 = {};
          """
        });
  }

  @Test
  public void testESModuleStarImportsCommonJS() {
    test(
        createCompilerOptions(),
        new String[] {
          "/** @constructor */ function Hello() {} module.exports = Hello;",
          """
          import * as i0 from './i0';
          var util = {inherits: function (x, y){}};
          /**
           * @constructor
           * @extends {i0.default}
           */
          function SubHello() { i0.default.call(this); }
          util.inherits(SubHello, i0.default);
          """
        },
        new String[] {
          """
          /** @const */ var module$i0 = {};
          /** @const */ module$i0.default = /** @constructor */ function (){};
          """,
          """
          var util$$module$i1 = {inherits:function(x,y){}};
          function SubHello$$module$i1(){ module$i0.default.call(this); }
          util$$module$i1.inherits(SubHello$$module$i1, module$i0.default);
          var module$i1 = {};
          """
        });
  }

  @Test
  public void testESModuleDefaultImportsCommonJS_variableReference() {
    test(
        createCompilerOptions(),
        new String[] {
          "module.exports = {foo: 1, bar: function() { return 'bar'; }};",
          """
          import i0 from './i0';
          const {foo} = i0;
          const b = i0.bar();
          """
        },
        new String[] {
          """
          /** @const */ var module$i0 = {/** @const */ default: {}};
          module$i0.default.foo = 1;
          module$i0.default.bar = function() { return 'bar'; };
          """,
          """
          /** @const */ var $jscomp$destructuring$var0 = module$i0.default;
          /** @const */ var foo$$module$i1 = module$i0.default.foo;
          /** @const */ var b$$module$i1 = module$i0.default.bar();
          /** @const */ var module$i1 = {};
          """
        });
  }

  @Test
  public void testEsModuleImportNonDefaultFromCJS() {
    test(
        createCompilerOptions(),
        new String[] {
          "module.exports = {foo: 1, bar: function() { return 'bar'; }};",
          "import {foo} from './i0';"
        },
        DiagnosticGroups.MODULE_IMPORT);
  }

  @Test
  public void testEsModuleImportStarNonDefaultFromCJS() {
    test(
        createCompilerOptions(),
        new String[] {
          "module.exports = {foo: 1, bar: function() { return 'bar'; }};",
          "import * as m from './i0'; m.bar();"
        },
        new String[] {
          """
          /** @const */ var module$i0 = {/** @const */ default:{}};
          module$i0.default.foo = 1;
          module$i0.default.bar = function() {
            return "bar";
          };
          """,
          """
          module$i0.bar();
          /** @const */ var module$i1 = {};
          """
        },
        DiagnosticGroups.MISSING_PROPERTIES);
  }

  public CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setCodingConvention(new GoogleCodingConvention());
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setProcessCommonJSModules(true);
    options.setClosurePass(true);
    options.setModuleResolutionMode(ModuleLoader.ResolutionMode.NODE);
    // For debugging failures
    options.setPrettyPrint(true);
    options.setPreserveTypeAnnotations(true);
    return options;
  }
}
