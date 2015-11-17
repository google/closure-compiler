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

package com.google.javascript.jscomp;

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/**
 * @author johnlenz@google.com (John Lenz)
 */
public final class CheckUnusedPrivatePropertiesTest extends CompilerTestCase {

  private static final String EXTERNS = LINE_JOINER.join(
      "var window;",
      "function alert(a) {}",
      "var EXT = {};",
      "EXT.ext;");

  public CheckUnusedPrivatePropertiesTest() {
    super(EXTERNS);
    enableGatherExternProperties();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6);
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckUnusedPrivateProperties(compiler);
  }

  @Override
  public CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.LINT_CHECKS, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.OFF);
    return options;
  }

  @Override
  protected void setUp() {
    this.enableTypeCheck();
  }

  private void unused(String code) {
    testSame(code, CheckUnusedPrivateProperties.UNUSED_PRIVATE_PROPERTY);
  }

  private void used(String code) {
    testSame(code);
  }

  public void testSimpleUnused1() {
    unused("/** @private */ this.a = 2");
    used("/** @private */ this.a = 2; alert(this.a);");
  }

  public void testConstructorPropertyUsed1() {
    unused("/** @constructor */ function C() {} /** @private */ C.prop = 1;");
    used("/** @constructor */ function C() {} /** @private */ C.prop = 1; alert(C.prop);");
    used("/** @constructor */ function C() {} /** @private */ C.prop = 1; function f(a) {a.prop}");
  }

  public void testClassPropUnused1() {
    this.disableTypeCheck();

    // A property defined on "this" can be removed
    unused("class C { constructor() { /** @private */ this.a = 2 } }");
  }

  public void testClassPropUnused2() {
    unused("/** @constructor */ function C() { /** @private */ this.prop = 1; }");
    used("/** @constructor */ function C() {/** @private */ this.prop = 1;} alert(some.prop);");
  }

  public void testClassMethodUnused1() {
    this.disableTypeCheck();

    unused("class C { constructor() {}  /** @private */ method() {} }");
    used("class C { constructor() {}  /** @private */ method() {} }\n new C().method();");
  }

  // The JSDoc seems to be missing here, reenable this test when it is fixed.
  public void disable_testClassMethodUnused2() {
    this.disableTypeCheck();

    unused("class C { constructor() {}\n  /** @private */ ['method']() {} }");
    used("class C { constructor() {}\n  /** @private */ ['method']() {} }\n new C()['method']();");
  }

  public void testSimple2() {
    // A property defined on "this" can be removed, even when defined
    // as part of an expression
    unused("/** @private */ this.a = 1; this.a = 2, f()");
    unused("/** @private */ this.a = 1; x = (this.a = 2, f())");
    unused("/** @private */ this.a = 1; x = (f(), this.a = 2)");
  }

  public void testSimple3() {
    // A property defined on an object other than "this" can not be removed.
    used("y.a = 2");
    // and is seen as a use.
    used("y.a = 2; /** @private */ this.a = 2");
    // Some use of the property "a" appears as a use.
    used("y.a = 2; /** @private */ this.a = 1; alert(x.a)");
  }

  public void testObjLit() {
    // A property defined on an object other than "this" is considered a use.
    used("({a:2})");
    // and is seen as a use on 'this'.
    used("({a:0}); /** @private */ this.a = 1;");
    // Some use of the property "a" anywhere is considered a use
    used("x = ({a:0}); /** @private */ this.a = 1; alert(x.a)");
  }

  public void testExtern() {
    // A property defined in the externs and isn't a warning.
    testSame("this.ext = 2");
  }

  public void testExport() {
    // An exported property can not be removed.
    testSame("this.ext = 2; window['export'] = this.ext;");
    testSame("function f() { this.ext = 2; } window['export'] = this.ext;");
  }


  public void testAssignOp1() {
    // Properties defined using a compound assignment can be removed if the
    // result of the assignment expression is not immediately used.
    unused("/** @private */ this.x; this.x += 2");
    used("/** @private */ this.x; x = (this.x += 2)");
    used("/** @private */ this.x; this.x += 2; x = this.x;");
    // But, of course, a later use prevents its removal.
    used("/** @private */ this.x; this.x += 2; x.x;");
  }

  public void testAssignOp2() {
    // Properties defined using a compound assignment can be removed if the
    // result of the assignment expression is not immediately used.
    unused("/** @private */ this.a; this.a += 2, f()");
    unused("/** @private */ this.a; x = (this.a += 2, f())");
    used("/** @private */ this.a; x = (f(), this.a += 2)");
  }

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

  public void testInc2() {
    // Increments and Decrements are handled similarly to compound assignments
    unused("/** @private */ this.a; this.a++, f()");
    unused("/** @private */ this.a;x = (this.a++, f())");
    used("/** @private */ this.a;x = (f(), this.a++)");

    unused("/** @private */ this.a; --this.a, f()");
    unused("/** @private */ this.a; x = (--this.a, f())");
    used("/** @private */ this.a; x = (f(), --this.a)");
  }

  public void testJSCompiler_renameProperty() {
    // JSCompiler_renameProperty introduces a use of the property
    used("/** @private */ this.a = 2; x[JSCompiler_renameProperty('a')]");
    used("/** @private */ this.a = 2; JSCompiler_renameProperty('a')");
  }

  public void testForIn() {
    // This is the basic assumption that this pass makes:
    // that it can warn even if it is used indirectly in a for-in loop
    unused(LINE_JOINER.join(
        "/** @constructor */ var X = function() {",
        "  /** @private */ this.y = 1;",
        "}",
        "for (var a in new X()) { alert(x[a]) }"));
  }

  public void testObjectReflection1() {
    // Verify reflection prevents warning.
    used(LINE_JOINER.join(
        "/** @constructor */ function A() {/** @private */ this.foo = 1;}",
        "use(goog.reflect.object(A, {foo: 'foo'}));"));
  }

  public void testObjectReflection2() {
    // Any object literal definition prevents warning.
    used(LINE_JOINER.join(
        "/** @constructor */ function A() {/** @private */  this.foo = 1;}",
        "use({foo: 'foo'});"));
  }

  public void testPrototypeProps1() {
    unused(LINE_JOINER.join(
        "/** @constructor */ function A() {this.foo = 1;}",
        "/** @private */ A.prototype.foo = 0;",
        "A.prototype.method = function() {this.foo++};",
        "new A().method()"));
  }

  public void testPrototypeProps2() {
    // don't warn about properties that are exported by convention
    used(LINE_JOINER.join(
        "/** @constructor */ function A() {this._foo = 1;}",
        "/** @private */ A.prototype._foo = 0;",
        "A.prototype.method = function() {this._foo++};",
        "new A().method()"));
  }

  public void testTypedef() {
    used(LINE_JOINER.join(
        "/** @constructor */ function A() {}",
        "/** @private @typedef {string} */ A.typedef_;"));
  }

  public void testInterface() {
    used(LINE_JOINER.join(
        "/** @constructor */ function A() {}",
        "/**",
        " * @interface",
        " * @private",
        " */",
        "A.Interface = function() {};"));
  }

  public void testConstructorProperty1() {
    unused("/** @constructor */ function C() {} /** @private */ C.prop = 1;");
  }

  public void testConstructorProperty2() {
    used(
        "/** @constructor */ function C() {} "
        + "/** @private */ C.prop = 1; "
        + "function use(a) { alert(a.prop) }; "
        + "use(C)");
  }
}
