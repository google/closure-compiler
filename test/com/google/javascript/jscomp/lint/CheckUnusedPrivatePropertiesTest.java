/*
 * Copyright 2015 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.lint;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.CompilerTestCase;
import com.google.javascript.jscomp.DiagnosticGroups;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CheckUnusedPrivateProperties} */
@RunWith(JUnit4.class)
public final class CheckUnusedPrivatePropertiesTest extends CompilerTestCase {

  private static final String EXTERNS =
      DEFAULT_EXTERNS
          + """
          /** @const */ goog.reflect = {};
          goog.reflect.object;
          /** @constructor */
          function Window() {}
          Window.prototype.x;
          Window.prototype.a;
          Window.prototype.ext;
          /** @type {!Window} */ var window;
          function alert(a) {}
          var EXT = {};
          EXT.ext;
          """;

  public CheckUnusedPrivatePropertiesTest() {
    super(EXTERNS);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableGatherExternProperties();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckUnusedPrivateProperties(compiler);
  }

  @Override
  public CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    return options;
  }

  private void unused(String code) {
    testWarning(code, CheckUnusedPrivateProperties.UNUSED_PRIVATE_PROPERTY);
  }

  private void used(String code) {
    testSame(code);
  }

  @Test
  public void testSimpleUnused1() {
    unused("/** @private */ this.a = 2");
    used("/** @private */ this.a = 2; alert(this.a);");
  }

  @Test
  public void testConstructorPropertyUsed1() {
    unused("/** @constructor */ function C() {} /** @private */ C.prop = 1;");
    used("/** @constructor */ function C() {} /** @private */ C.prop = 1; alert(C.prop);");
    used("/** @constructor */ function C() {} /** @private */ C.prop = 1; function f(a) {a.prop}");
  }

  @Test
  public void testClassConstructorPropertyUnused1() {
    // Don't ever warn about unused private constructors
    used("class C { /** @private */ constructor() {} }; ");
  }

  @Test
  public void testClassPropUnused1() {
    // A property defined on "this" can be removed
    unused("class C { constructor() { /** @private */ this.a = 2 } }");
  }

  @Test
  public void testClassPropUnused2() {
    unused("/** @constructor */ function C() { /** @private */ this.prop = 1; }");
    used("/** @constructor */ function C() {/** @private */ this.prop = 1;} alert(some.prop);");
  }

  @Test
  public void testClassMethodUnused1() {
    unused("class C { constructor() {}  /** @private */ method() {} }");
    used("class C { constructor() {}  /** @private */ method() {} }\n new C().method();");
  }

  @Test
  public void testClassQuotedMethodUnused_noWarning() {
    used("class C { constructor() {}\n  /** @private */ ['method']() {} }");
    used("class C { constructor() {}\n  /** @private */ ['method']() {} }\n new C()['method']();");
  }

  @Test
  public void testSimple2() {
    // A property defined on "this" can be removed, even when defined
    // as part of an expression
    unused("/** @private */ this.a = 1; this.a = 2, f()");
    unused("/** @private */ this.a = 1; x = (this.a = 2, f())");
    unused("/** @private */ this.a = 1; x = (f(), this.a = 2)");
  }

  @Test
  public void testSimple3() {
    // A property defined on an object other than "this" can not be removed.
    used("y.a = 2");
    // and is seen as a use.
    used("y.a = 2; /** @private */ this.a = 2");
    // Some use of the property "a" appears as a use.
    used("y.a = 2; /** @private */ this.a = 1; alert(x.a)");
  }

  @Test
  public void testObjLit() {
    // A property defined on an object other than "this" is considered a use.
    used("({a:2})");
    // and is seen as a use on 'this'.
    used("({a:0}); /** @private */ this.a = 1;");
    // Some use of the property "a" anywhere is considered a use
    used("x = ({a:0}); /** @private */ this.a = 1; alert(x.a)");
  }

  @Test
  public void testExtern() {
    // A property defined in the externs and isn't a warning.
    testSame("this.ext = 2");
  }

  @Test
  public void testExport() {
    // An exported property can not be removed.
    testSame("this.ext = 2; window['export'] = this.ext;");
    testSame("function f() { this.ext = 2; } window['export'] = this.ext;");
  }

  @Test
  public void testAssignOp1() {
    // Properties defined using a compound assignment can be removed if the
    // result of the assignment expression is not immediately used.
    unused("/** @private */ this.x; this.x += 2");
    used("/** @private */ this.x; x = (this.x += 2)");
    used("/** @private */ this.x; this.x += 2; x = this.x;");
    // But, of course, a later use prevents its removal.
    used("/** @private */ this.x; this.x += 2; x.x;");
  }

  @Test
  public void testAssignOp2() {
    // Properties defined using a compound assignment can be removed if the
    // result of the assignment expression is not immediately used.
    unused("/** @private */ this.a; this.a += 2, f()");
    unused("/** @private */ this.a; x = (this.a += 2, f())");
    used("/** @private */ this.a; x = (f(), this.a += 2)");
  }

  @Test
  public void testInc1() {
    // Increments and Decrements are handled similarly to compound assignments
    // but need a placeholder value when replaced.
    unused("/** @private */ this.x; this.x++");
    used("/** @private */ this.x; x = (this.x++)");
    used("/** @private */ this.x; this.x++; x = this.x;");

    unused("/** @private */ this.x; --this.x");
    used("/** @private */ this.x; x = (--this.x)");
    used("/** @private */ this.x; --this.x; x = this.x;");
  }

  @Test
  public void testInc2() {
    // Increments and Decrements are handled similarly to compound assignments
    unused("/** @private */ this.a; this.a++, f()");
    unused("/** @private */ this.a;x = (this.a++, f())");
    used("/** @private */ this.a;x = (f(), this.a++)");

    unused("/** @private */ this.a; --this.a, f()");
    unused("/** @private */ this.a; x = (--this.a, f())");
    used("/** @private */ this.a; x = (f(), --this.a)");
  }

  @Test
  public void testJSCompiler_renameProperty() {
    // JSCompiler_renameProperty introduces a use of the property
    used("/** @private */ this.a = 2; x[JSCompiler_renameProperty('a')]");
    used("/** @private */ this.a = 2; JSCompiler_renameProperty('a')");
  }

  @Test
  public void testForIn() {
    // This is the basic assumption that this pass makes:
    // that it can warn even if it is used indirectly in a for-in loop
    unused(
        """
        /** @constructor */ var X = function() {
          /** @private */ this.y = 1;
        }
        for (var a in new X()) { alert(x[a]) }
        """);
  }

  @Test
  public void testObjectReflection1() {
    // Verify reflection prevents warning.
    used(
        """
        /** @constructor */ function A() {/** @private */ this.foo = 1;}
        use(goog.reflect.object(A, {foo: 'foo'}));
        """);

    // Verify reflection prevents warning.
    used(
        """
        /** @const */ var $jscomp = {};
        /** @const */ $jscomp.scope = {};
        /**
         * @param {!Function} type
         * @param {Object} object
         * @return {Object}
         */
        $jscomp.reflectObject = function (type, object) { return object; };
        /** @constructor */ function A() {/** @private */ this.foo = 1;}
        use($jscomp.reflectObject(A, {foo: 'foo'}));
        """);
  }

  @Test
  public void testObjectReflection2() {
    // Any object literal definition prevents warning.
    used(
        """
        /** @constructor */ function A() {/** @private */  this.foo = 1;}
        use({foo: 'foo'});
        """);

    // member functions prevent renaming (since we allow them in goog.reflect.object)
    used(
        """
        /** @constructor */ function A() {/** @private */  this.foo = 1;}
        use({foo() {}});
        """);

    // computed property doesn't prevent warning
    unused(
        """
        /** @constructor */ function A() {/** @private */  this.foo = 1;}
        use({['foo']: 'foo'});
        """);
  }

  @Test
  public void testPrototypeProps1() {
    unused(
        """
        /** @constructor */ function A() {this.foo = 1;}
        /** @private */ A.prototype.foo = 0;
        A.prototype.method = function() {this.foo++};
        new A().method()
        """);
  }

  @Test
  public void testPrototypeProps2() {
    // warn about all private properties
    unused(
        """
        /** @constructor */ function A() {this._foo = 1;}
        /** @private */ A.prototype._foo = 0;
        A.prototype.method = function() {this._foo++};
        new A().method()
        """);
  }

  @Test
  public void testTypedef() {
    used(
        """
        /** @constructor */ function A() {}
        /** @private @typedef {string} */ A.typedef_;
        """);
  }

  @Test
  public void testInterface() {
    used(
        """
        /** @constructor */ function A() {}
        /**
         * @interface
         * @private
         */
        A.Interface = function() {};
        """);
  }

  @Test
  public void testConstructorProperty1() {
    unused(
        """
        /** @constructor */
        function C() {}
        /** @private */ C.prop = 1;
        """);
  }

  @Test
  public void testConstructorProperty2() {
    used(
        """
        /** @constructor */
        function C() {}
        /** @private */ C.prop = 1;
        function use(a) {
          alert(a.prop)
        };
        use(C)
        """);
  }

  @Test
  public void testInterfaceProperty() {
    unused(
        """
        /** @interface */
        function C() {}
        /** @private */ C.prop = 1;
        """);
  }

  @Test
  public void testConstructorProperty_nonQnameClass() {
    testSame(
        """
        const obj = {};
        /** @constructor */ obj['ctor'] = function () {};
        /** @private */ obj['ctor'].prop = 1;
        """);
  }

  @Test
  public void testEs6ClassProperty() {
    unused("class C {} /** @private */ C.prop = 1;");
  }
}
