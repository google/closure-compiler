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


/**
 * @fileoverview Tests for runtime utilities for modules.
 * @suppress {checkTypes}
 */
goog.module('jscomp.runtime_tests.polyfill_tests.es6.esmodules_tests.runtime_test');
goog.setTestOnly();

const array = goog.require('goog.array');
const testSuite = goog.require('goog.testing.testSuite');

testSuite({
  setUp() {
    $jscomp.clearModules();
  },

  testLoadAndRequireModule: function() {
    let loaded = false;

    $jscomp.registerModule(function(require, exports, module) {
      loaded = true;
      module.exports.myExportedString = 'exported string';
      module.exports.myExportedNumber = 42;
    }, '/some/path/to/file.js');

    assertFalse(loaded);
    const {myExportedString, myExportedNumber} =
        $jscomp.require('/some/path/to/file.js');
    assertTrue(loaded);
    assertEquals('exported string', myExportedString);
    assertEquals(42, myExportedNumber);
  },

  testRequireInvalidModuleThrows: function() {
    const error = assertThrows(function() {
      $jscomp.require('/does/not/exist.js');
    });
    assertEquals('Module /does/not/exist.js does not exist.', error.message);
  },

  testRequireInvalidRelativeModuleThrows: function() {
    $jscomp.registerModule(function() {
      $jscomp.require('./does/not/exist.js');
    }, 'a.js');
    const error = assertThrows(function() {
      $jscomp.require('a.js');
    });
    assertEquals('Module ./does/not/exist.js does not exist.', error.message);
  },

  testNestedModulesThrow: function() {
    $jscomp.registerModule(function() {
      $jscomp.registerModule(function() {}, '/inner/module.js');
    }, '/outter/module/js.js');
    const error = assertThrows(function() {
      $jscomp.require('/outter/module/js.js');
    });
    assertEquals('Cannot nest modules.', error.message);
  },

  testLoadAndRequireModuleFromModule: function() {
    $jscomp.registerModule(function(require, exports, module) {
      module.exports.myExportedString = 'exported string';
      module.exports.myExportedNumber = 42;
    }, 'a.js');

    let loaded = false;

    $jscomp.registerModule(function(require) {
      const {myExportedString, myExportedNumber} = require('a.js');
      assertEquals('exported string', myExportedString);
      assertEquals(42, myExportedNumber);
      loaded = true;
    }, 'b.js');

    $jscomp.require('b.js');
    assertTrue(loaded);
  },

  testLoadAndRequireModuleFromModuleRelative: function() {
    $jscomp.registerModule(function(require, exports, module) {
      module.exports.myExportedString = 'exported string';
      module.exports.myExportedNumber = 42;
    }, 'a.js');

    $jscomp.registerModule(function(require, exports, module) {
      const A = require('../a.js');
      const {myExportedString, myExportedNumber} = A;
      assertEquals('exported string', myExportedString);
      assertEquals(42, myExportedNumber);
      module.exports = A;
    }, 'nested/a.js');

    let loaded = false;

    $jscomp.registerModule(function(require, exports, module) {
      const {myExportedString, myExportedNumber} = require('./nested/a.js');
      assertEquals('exported string', myExportedString);
      assertEquals(42, myExportedNumber);
      loaded = true;
    }, 'b.js');

    $jscomp.require('b.js');
    assertTrue(loaded);
  },

  testDeps() {
    let anyModulesLoaded = false;
    let available = false;

    $jscomp.require.ensure(['test.js'], () => available = true);

    assertFalse(available);
    assertFalse(anyModulesLoaded);

    $jscomp.registerModule(
        () => anyModulesLoaded = true, 'test.js', ['deep.js']);

    assertFalse(available);
    assertFalse(anyModulesLoaded);

    $jscomp.registerModule(() => anyModulesLoaded = true, 'deep.js');

    assertTrue(available);
    assertFalse(anyModulesLoaded);
  },

  testDepsRegistrationOrderInvariant() {
    class Module {
      constructor(id, deps) {
        this.id = id;
        this.available = false;
        this.reset = () => {
          this.available = false;
          $jscomp.require.ensure([id], () => this.available = true);
        };
        this.register = () => $jscomp.registerModule(() => {}, id, deps);
      }

      toString() {
        return this.id;
      }
    }

    const module0 = new Module('0', []);
    const module1 = new Module('1', []);
    const module2 = new Module('2', ['0', '1']);
    const module3 = new Module('3', ['2']);
    const entryPoint = new Module('e', ['3']);
    const allModules = [module0, module1, module2, module3, entryPoint];

    function assertAvailable(...modules) {
      assertSameElements(
          modules, array.filter(allModules, module => module.available));
    }

    function reset() {
      $jscomp.clearModules();
      array.forEach(allModules, module => module.reset());
    }

    reset();

    module0.register();
    assertAvailable(module0);
    module1.register();
    assertAvailable(module0, module1);
    module2.register();
    assertAvailable(module0, module1, module2);
    module3.register();
    assertAvailable(module0, module1, module2, module3);
    entryPoint.register();
    assertAvailable(module0, module1, module2, module3, entryPoint);

    reset();

    entryPoint.register();
    assertAvailable();
    module3.register();
    assertAvailable();
    module2.register();
    assertAvailable();
    module1.register();
    assertAvailable(module1);
    module0.register();
    assertAvailable(module0, module1, module2, module3, entryPoint);

    reset();

    module2.register();
    assertAvailable();
    module0.register();
    assertAvailable(module0);
    module1.register();
    assertAvailable(module0, module1, module2);
    entryPoint.register();
    assertAvailable(module0, module1, module2);
    module3.register();
    assertAvailable(module0, module1, module2, module3, entryPoint);
  },

  testInjectExports: function() {
    const exports = {Art: 'Vandelay'};
    $jscomp.registerEs6ModuleExports('injected.js', exports);

    $jscomp.registerModule(function(require) {
      const imports = require('injected.js');
      assertEquals(exports, imports);
    }, 'raw.js');

    $jscomp.require('raw.js');
  },

  testRequireEnsure() {
    let anyModulesLoaded = false;
    let called = false;

    // Require nothing, called immediately.
    $jscomp.require.ensure([], () => called = true);

    assertTrue(called);
    called = false;

    $jscomp.registerModule(function(require, exports, module) {
      module.exports = 'FIRST';
      anyModulesLoaded = true;
    }, 'first.js');

    // Require something already available, called immediately.
    $jscomp.require.ensure(['first.js'], () => called = true);

    assertTrue(called);
    called = false;

    $jscomp.registerModule(function(require, exports, module) {
      module.exports = 'SECOND';
      anyModulesLoaded = true;
    }, 'second.js');

    // Require something not yet available, called as soon as it is available.
    $jscomp.require.ensure(
        ['first.js', 'second.js', 'third.js'], function(require) {
          assertFalse(anyModulesLoaded);
          const first = require('first.js');
          const second = require('second.js');
          const third = require('third.js');
          assertEquals('FIRST', first);
          assertEquals('SECOND', second);
          assertEquals('THIRD', third);
          called = true;
        });

    assertFalse(called);

    $jscomp.registerModule(function(require, exports, module) {
      module.exports = 'THIRD';
      anyModulesLoaded = true;
    }, 'third.js');

    assertTrue(called);
  },

  testRegisterAndLoadModule() {
    let loaded = false;
    let available = false;

    // Available before loaded.
    $jscomp.require.ensure(['test.js'], function() {
      assertFalse(loaded);
      available = true;
    });

    assertFalse(loaded);
    assertFalse(available);

    $jscomp.registerAndLoadModule(function(require, exports, module) {
      assertTrue(available);
      loaded = true;
    }, 'test.js');

    assertTrue(loaded);
    assertTrue(available);
  },

  testCircularDeps() {
    let firstLoaded = false;
    let secondLoaded = false;
    let firstAvailable = false;
    let secondAvailable = false;

    $jscomp.require.ensure(['first.js'], function() {
      firstAvailable = true;
    });

    $jscomp.require.ensure(['second.js'], function() {
      secondAvailable = true;
    });

    $jscomp.registerModule(function(require, exports, module) {
      // This *finishes* loading first because second was require'd, which in
      // turn requires this.
      assertFalse(secondLoaded);
      require('second.js');
      assertFalse(secondLoaded);
      firstLoaded = true;
    }, 'first.js', ['second.js']);

    assertFalse(firstLoaded);
    assertFalse(secondLoaded);
    assertFalse(firstAvailable);
    assertFalse(secondAvailable);

    $jscomp.registerModule(function(require, exports, module) {
      assertFalse(firstLoaded);
      require('first.js');
      assertTrue(firstLoaded);
      secondLoaded = true;
    }, 'second.js', ['first.js']);

    assertFalse(firstLoaded);
    assertFalse(secondLoaded);
    assertTrue(firstAvailable);
    assertTrue(secondAvailable);

    $jscomp.require('second.js');

    assertTrue(firstLoaded);
    assertTrue(secondLoaded);
  },

  testRegisterAndLoadModuleCircular() {
    let firstLoaded = false;
    let secondLoaded = false;
    let firstAvailable = false;
    let secondAvailable = false;

    $jscomp.require.ensure(['first.js'], function() {
      // first was loaded by second before we were alerted.
      assertTrue(firstLoaded);
      firstAvailable = true;
    });

    $jscomp.require.ensure(['second.js'], function() {
      assertFalse(secondLoaded);
      secondAvailable = true;
    });

    $jscomp.registerAndLoadModule(function(require, exports, module) {
      // This *finishes* loading first because it is registered first.
      assertFalse(secondLoaded);
      require('second.js');
      assertFalse(secondLoaded);
      firstLoaded = true;
    }, 'first.js', ['second.js']);

    assertFalse(firstLoaded);
    assertFalse(secondLoaded);
    assertFalse(firstAvailable);
    assertFalse(secondAvailable);

    $jscomp.registerAndLoadModule(function(require, exports, module) {
      assertFalse(firstLoaded);
      require('first.js');
      assertTrue(firstLoaded);
      secondLoaded = true;
    }, 'second.js', ['first.js']);

    assertTrue(firstLoaded);
    assertTrue(secondLoaded);
    assertTrue(firstAvailable);
    assertTrue(secondAvailable);
  },

  testRequireEnsureCircular() {
    $jscomp.registerModule(function(require, exports, module) {
      module.exports.fullyLoaded = false;
      module.exports.wasSecondLoaded = false;

      require.ensure(['second.js'], function(require) {
        const second = require('second.js');
        module.exports.fullyLoaded = true;
        module.exports.wasSecondLoaded = second.fullyLoaded;
      });
    }, 'first.js');

    $jscomp.registerModule(function(require, exports, module) {
      module.exports.fullyLoaded = false;
      module.exports.wasFirstLoaded = false;

      require.ensure(['first.js'], function(require) {
        const first = require('first.js');
        module.exports.fullyLoaded = true;
        module.exports.wasFirstLoaded = first.fullyLoaded;
      });
    }, 'second.js');

    $jscomp.require.ensure(['second.js', 'first.js'], function(require) {
      const first = require('first.js');
      const second = require('second.js');
      assertTrue(first.fullyLoaded);
      assertTrue(second.fullyLoaded);

      // First loads first, but only partially. Second is furthest in the
      // tree so it ends up fully finishing first (note first is require'd
      // before second above).
      assertTrue(first.wasSecondLoaded);
      assertFalse(second.wasFirstLoaded);
    });
  },

  testExportAllFrom() {
    $jscomp.registerModule(function(require, exports, module) {
      module.exports.foo = 'foo';
      module.exports.bar = 'bar';
    }, 'first.js');

    $jscomp.registerModule(function(require, exports, module) {
      module.exportAllFrom(require('first.js'));
    }, 'second.js');

    const second = $jscomp.require('second.js');
    assertEquals('foo', second.foo);
    assertEquals('bar', second.bar);
    assertEquals(2, Object.keys(second).length);
  },
});
