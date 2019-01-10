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

import static com.google.javascript.jscomp.CheckConstPrivateProperties.MISSING_CONST_PROPERTY;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CheckConstPrivatePropertiesTest extends CompilerTestCase {

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    // TODO(tbreisacher): After the typechecker is updated to understand ES6, add non-transpiling
    // versions of these tests.
    enableTypeCheck();
    enableTranspile();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return new CheckConstPrivateProperties(compiler);
  }

  @Override
  public CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.MISSING_CONST_PROPERTY, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.OFF);
    return options;
  }

  @Test
  public void testConstructorPropModified1() {
    testWarning(
        "/** @constructor */ function C() {} /** @private */ C.prop = 1;", MISSING_CONST_PROPERTY);
    testSame("/** @constructor */ function C() {} /** @private */ C.prop = 1; C.prop = 2;");
  }

  @Test
  public void testConstructorPropModified_function() {
    testSame("/** @constructor */ function C() {} /** @private */ C.prop = function() {};");
  }

  @Test
  public void testConstructorPropModified_enum() {
    testSame(
        "/** @constructor */ function C() {} /** @enum {number} @private */ C.prop = { A: 1 };");
  }

  @Test
  public void testConstructorPropModified_const() {
    testSame("/** @constructor */ function C() {} /** @private @const */ C.prop = 1;");
  }

  @Test
  public void testConstructorPropModified2() {
    testWarning(
        "/** @constructor */ function C() { /** @private */ this.foo = 1; } ",
        MISSING_CONST_PROPERTY);
    testSame("/** @constructor */ function C() { /** @private */ this.foo = 1; this.foo = 2; } ");
    testSame(
        "/** @constructor */ function C() { /** @private */ this.foo = 1; } "
            + "C.prototype.bar = function() { this.foo = 2; }");
  }

  @Test
  public void testConstructorPropModified_delete() {
    testWarning(
        "/** @constructor */ function C() { /** @private */ this.foo = 1; } ",
        MISSING_CONST_PROPERTY);
    testSame(
        "/** @constructor */ function C() { /** @private */ this.foo = 1; delete this.foo; } ");
    testSame(
        "/** @constructor */ function C() { /** @private */ this.foo = 1; } "
            + "C.prototype.bar = function() { delete this.foo; }");
  }

  @Test
  public void testConstructorPropModified_arrayIndexGetProp() {
    testWarning(
        "/** @constructor */ function C() { /** @private */ this.foo = [1]; } ",
        MISSING_CONST_PROPERTY);
    // Even though the underlying data structure is testSame, the reference this['foo'] points to is
    // not modified.
    testWarning(
        "/** @constructor */ function C() { /** @private */ this.foo = [1]; this.foo[0] = 2; } ",
        MISSING_CONST_PROPERTY);
    testWarning(
        "/** @constructor */ function C() { /** @private */ this.foo = [1]; } "
            + "C.prototype.bar = function() { this.foo[0] = 2; }",
        MISSING_CONST_PROPERTY);
  }

  @Test
  public void testConstructorPropModified_objectElement() {
    testWarning(
        "/** @constructor */ function C() { /** @private */ this['foo'] = 1; } ",
        MISSING_CONST_PROPERTY);
    testSame(
        "/** @constructor */ function C() { /** @private */ this['foo'] = 1; this['foo'] = 2; } ");
    testSame(
        "/** @constructor */ function C() { /** @private */ this['foo'] = 1; } "
            + "C.prototype.bar = function() { this['foo'] = 2; }");
  }

  @Test
  public void testConstructorPropModified_arrayIndexGetElem() {
    testWarning(
        "/** @constructor */ function C() { /** @private */ this['foo'] = [1]; } ",
        MISSING_CONST_PROPERTY);
    // Even though the underlying data structure is testSame, the reference this['foo'] points to is
    // not modified.
    testWarning(
        "/** @constructor */ function C() { /** @private */ this['f'] = [1]; this['f'][0] = 2; } ",
        MISSING_CONST_PROPERTY);
    testWarning(
        "/** @constructor */ function C() { /** @private */ this['foo'] = [1]; } "
            + "C.prototype.bar = function() { this['foo'][0] = 2; }",
        MISSING_CONST_PROPERTY);
  }

  @Test
  public void testConstructorPropModified_mixedObjectAccess() {
    testSame(
        "/** @constructor */ function C() { /** @private */ this.foo = 1; this['foo'] = 2; } ");
    testSame(
        "/** @constructor */ function C() { /** @private */ this['foo'] = 1; } "
            + "C.prototype.bar = function() { this.foo = 2; }");
  }

  @Test
  public void testConstructorPropModified_lambda() {
    testSame(
        lines(
            "/** @constructor */",
            "function C() {",
            "  /** @private */",
            "  this.foo_ = 2;",
            "",
            "  (() => { this.foo_ = 1; })();",
            "}"));
  }

  @Test
  public void testClassPropModified1() {
    testWarning(
        "class C { constructor() { /** @private */ this.a = 2; } }", MISSING_CONST_PROPERTY);
    testSame("class C { constructor() { /** @private */ this.a = 2; this.a = 3; } }");
    testSame("class C { constructor() { /** @private */ this.a = 2; } foo() { this.a = 3; } }");
  }

  @Test
  public void testClassPropModified_const() {
    testSame("class C { constructor() { /** @private @const */ this.a = 2; } }");
  }

  @Test
  public void testClassPropModified_lambda() {
    testSame(
        lines(
            "class C { ",
            "  constructor() { ",
            "    /** @private */",
            "    this.foo_ = 2;",
            "",
            "    (() => { this.foo_ = 1; })();",
            "  }",
            "}"));
  }

  @Test
  public void testClassPropModified_assignModify() {
    testWarning(
        "class C { constructor() { /** @private */ this.a = 2; } }", MISSING_CONST_PROPERTY);
    testSame("class C { constructor() { /** @private */ this.a = 2; this.a += 3; } }");
    testSame("class C { constructor() { /** @private */ this.a = 2; } foo() { this.a += 3; } }");
    testSame("class C { constructor() { /** @private */ this.a = 2; this.a -= 3; } }");
    testSame("class C { constructor() { /** @private */ this.a = 2; } foo() { this.a -= 3; } }");
  }

  @Test
  public void testClassPropModified_increment() {
    testWarning(
        "class C { constructor() { /** @private */ this.a = 2; } }", MISSING_CONST_PROPERTY);
    testSame("class C { constructor() { /** @private */ this.a = 2; this.a++; } }");
    testSame("class C { constructor() { /** @private */ this.a = 2; } foo() { this.a++; } }");
  }

  @Test
  public void testClassPropModified_decrement() {
    testWarning(
        "class C { constructor() { /** @private */ this.a = 2; } }", MISSING_CONST_PROPERTY);
    testSame("class C { constructor() { /** @private */ this.a = 2; this.a--; } }");
    testSame("class C { constructor() { /** @private */ this.a = 2; } foo() { this.a--; } }");
  }

  @Test
  public void testClassPropModified_delete() {
    testWarning(
        "class C { constructor() { /** @private */ this.a = 2; } }", MISSING_CONST_PROPERTY);
    testSame("class C { constructor() { /** @private */ this.a = 2; delete this.a; } }");
    testSame("class C { constructor() { /** @private */ this.a = 2; } foo() { delete this.a; } }");
  }

  @Test
  public void testClassPropModified_multiFile() {
    testWarning(
        new String[] {
          "class A { constructor() { /** @private */ this.a = 2; } }",
          "class B { constructor() { /** @private */ this.a = 1; this.a = 4; } }",
        },
        MISSING_CONST_PROPERTY);
    testSame(
        new String[] {
          "class A { constructor() { /** @private */ this.a = 2; this.a = 3; } }",
          "class B { constructor() { /** @private */ this.a = 2; this.a = 4; } }",
        });
  }

  @Test
  public void testPrototype_Property() {
    testSame("/** @constructor */ function C() {} /** @private */ C.prototype.prop = 1;");
    testSame(
        "/** @constructor */ function C() {} /** @private */ C.prototype.prop = 1; "
            + "C.prototype.prop = 2;");
  }

  @Test
  public void testPrototype_Method() {
    testSame(
        "/** @constructor */ function C() {} /** @private */ C.prototype.method = function() {};");
  }

  @Test
  public void testPropertyAsRvalueOfDeclaration() {
    // Test bug where the rhs of a @private prop declaration was treated like a declaration.
    // See b/110938671

    // In the repro below, there was a spurious warning that Foo.SOME_PROP was an effectively
    // constant private property, and the JSDoc "/** @private {number} */" should be @const.
    testNoWarning(
        lines(
            "class Foo {}",
            "class C {",
            "  constructor() {",
            "    /** @private {number} */",
            "    this.nonConstProp_ = Foo.SOME_PROP;",
            "    this.nonConstProp_ = 4;", // change this.nonConstProp_ to avoid a warning
            "  }",
            "}"));
  }
}
