/*
 * Copyright 2008 The Closure Compiler Authors.
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

import static com.google.javascript.jscomp.CheckAccessControls.BAD_PACKAGE_PROPERTY_ACCESS;
import static com.google.javascript.jscomp.CheckAccessControls.BAD_PRIVATE_GLOBAL_ACCESS;
import static com.google.javascript.jscomp.CheckAccessControls.BAD_PRIVATE_PROPERTY_ACCESS;
import static com.google.javascript.jscomp.CheckAccessControls.BAD_PROPERTY_OVERRIDE_IN_FILE_WITH_FILEOVERVIEW_VISIBILITY;
import static com.google.javascript.jscomp.CheckAccessControls.BAD_PROTECTED_PROPERTY_ACCESS;
import static com.google.javascript.jscomp.CheckAccessControls.CONST_PROPERTY_DELETED;
import static com.google.javascript.jscomp.CheckAccessControls.CONST_PROPERTY_REASSIGNED_VALUE;
import static com.google.javascript.jscomp.CheckAccessControls.CONVENTION_MISMATCH;
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_CLASS;
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_CLASS_REASON;
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_NAME_REASON;
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_PROP;
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_PROP_REASON;
import static com.google.javascript.jscomp.CheckAccessControls.EXTEND_FINAL_CLASS;
import static com.google.javascript.jscomp.CheckAccessControls.PRIVATE_OVERRIDE;
import static com.google.javascript.jscomp.CheckAccessControls.VISIBILITY_MISMATCH;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link CheckAccessControls} specifically for ES6 class syntax.
 *
 * <p>This file is forked from {@link CheckAccessControlsTest} because nearly all cases require
 * duplication. If a case using `@constructor`, `@interface`, or `@record` is added to that suite, a
 * similar case should be added here under the same name using `class`.
 */

@RunWith(JUnit4.class)
public final class CheckAccessControlsEs6ClassTest extends CompilerTestCase {

  public CheckAccessControlsEs6ClassTest() {
    super(CompilerTypeTestCase.DEFAULT_EXTERNS);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
    enableParseTypeInfo();
    enableClosurePass();
    enableRewriteClosureCode();
  }

  @Override
  protected int getNumRepetitions() {
    return 1;
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CheckAccessControls(compiler, true);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.ERROR);
    options.setWarningLevel(DiagnosticGroups.DEPRECATED, CheckLevel.ERROR);
    options.setWarningLevel(DiagnosticGroups.CONSTANT_PROPERTY, CheckLevel.ERROR);
    return options;
  }

  @Test
  public void testWarningInNormalClass() {
    test(
        srcs(
            lines(
                "/** @deprecated FooBar */ function f() {}",
                "",
                "var Foo = class {",
                "  bar() { f(); }",
                "}; ")),
        deprecatedName("Variable f has been deprecated: FooBar"));
  }

  @Test
  public void testWarningForProperty1() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  baz() { alert((new Foo()).bar); }",
                "}",
                "/** @deprecated A property is bad */ Foo.prototype.bar = 3;")),
        deprecatedProp("Property bar of type Foo has been deprecated: A property is bad"));
  }

  @Test
  public void testWarningForProperty2() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  baz() { alert(this.bar); }",
                "}",
                "/** @deprecated Zee prop, it is deprecated! */ Foo.prototype.bar = 3;")),
        deprecatedProp(
            "Property bar of type Foo has been deprecated: Zee prop, it is deprecated!"));
  }

  @Test
  public void testWarningForDeprecatedClass() {
    test(
        srcs(
            lines(
                "/** @deprecated Use the class 'Bar' */",
                "class Foo {}",
                "",
                "function f() { new Foo(); }")),
        deprecatedClass("Class Foo has been deprecated: Use the class 'Bar'"));
  }

  @Test
  public void testWarningForDeprecatedClassNoReason() {
    test(
        srcs(
            lines(
                "/** @deprecated */", //
                "class Foo {} ",
                "",
                "function f() { new Foo(); }")),
        error(DEPRECATED_CLASS));
  }

  @Test
  public void testNoWarningForDeprecatedClassInstance() {
    test(
        srcs(
            lines(
                "/** @deprecated */",
                "class Foo {}",
                "",
                "/** @param {Foo} x */",
                "function f(x) { return x; }")));
  }

  @Test
  public void testWarningForDeprecatedSuperClass() {
    test(
        srcs(
            lines(
                "/** @deprecated Superclass to the rescue! */",
                "class Foo {}",
                "",
                "class SubFoo extends Foo {}",
                "",
                "function f() { new SubFoo(); }")),
        deprecatedClass("Class SubFoo has been deprecated: Superclass to the rescue!"));
  }

  @Test
  public void testWarningForDeprecatedSuperClass2() {
    test(
        srcs(
            lines(
                "/** @deprecated Its only weakness is Kryptoclass */",
                "class Foo {} ",
                "",
                "/** @const */",
                "var namespace = {}; ",
                "",
                "namespace.SubFoo = class extends Foo {};",
                "",
                "function f() { new namespace.SubFoo(); }")),
        deprecatedClass(
            "Class namespace.SubFoo has been deprecated: Its only weakness is Kryptoclass"));
  }

  @Test
  public void testWarningForPrototypeProperty() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  baz() { alert(Foo.prototype.bar); }",
                "}",
                "",
                "/** @deprecated It is now in production, use that one */ Foo.prototype.bar = 3;")),
        deprecatedProp(
            "Property bar of type Foo.prototype has been deprecated:"
                + " It is now in production, use that one"));
  }

  @Test
  public void testNoWarningForNumbers() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  baz() { alert(3); }",
                "}",
                "",
                "/** @deprecated */ Foo.prototype.bar = 3;")));
  }

  @Test
  public void testWarningForMethod1() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  /** @deprecated There is a madness to this method */",
                "  bar() {}",
                "",
                "  baz() { this.bar(); }",
                "}")),
        deprecatedProp(
            "Property bar of type Foo has been deprecated: There is a madness to this method"));
  }

  @Test
  public void testWarningForMethod2() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  baz() { this.bar(); }",
                "}",
                "",
                "/** @deprecated Stop the ringing! */",
                "Foo.prototype.bar;")),
        deprecatedProp("Property bar of type Foo has been deprecated: Stop the ringing!"));
  }

  @Test
  public void testNoWarningInDeprecatedClass() {
    test(
        srcs(
            lines(
                "/** @deprecated */",
                "function f() {}",
                "",
                "/** @deprecated */ ",
                "var Foo = class {",
                "  bar() { f(); }",
                "};")));
  }

  @Test
  public void testNoWarningOnDeclaration() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  constructor() {",
                "    /**",
                "     * @type {number}",
                "     * @deprecated Use something else.",
                "     */",
                "    this.code;",
                "  }",
                "}")));
  }

  @Test
  public void testNoWarningInDeprecatedClass2() {
    test(
        srcs(
            lines(
                "/** @deprecated */",
                "function f() {}",
                "",
                "/** @deprecated */ ",
                "var Foo = class {",
                "  static bar() { f(); }",
                "};")));
  }

  @Test
  public void testNoWarningInDeprecatedStaticMethod() {
    test(
        srcs(
            lines(
                "/** @deprecated */",
                "function f() {}",
                "",
                "var Foo = class {",
                "  /** @deprecated */",
                "  static bar() { f(); }",
                "};")));
  }

  @Test
  public void testWarningInStaticMethod() {
    test(
        srcs(
            lines(
                "/** @deprecated crazy! */",
                "function f() {}",
                "",
                "var Foo = class {",
                "  static bar() { f(); }",
                "};")),
        deprecatedName("Variable f has been deprecated: crazy!"));
  }

  @Test
  public void testWarningForSubclassMethod() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  bar() {}",
                "}",
                "",
                "class SubFoo extends Foo {",
                "  /**",
                "   * @override",
                "   * @deprecated I have a parent class!",
                "   */",
                "  bar() {}",
                "}",
                "",
                "function f() { (new SubFoo()).bar(); }")),
        deprecatedProp("Property bar of type SubFoo has been deprecated: I have a parent class!"));
  }

  @Test
  public void testWarningForSuperClassWithDeprecatedSubclassMethod() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  bar() {}",
                "}",
                "",
                "class SubFoo extends Foo {",
                "  /**",
                "   * @override",
                "   * @deprecated I have a parent class!",
                "   */",
                "  bar() {}",
                "}",
                "",
                "function f() { (new Foo()).bar(); };")));
  }

  @Test
  public void testWarningForSuperclassMethod() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  /** @deprecated I have a child class! */",
                "  bar() {}",
                "}",
                "",
                "class SubFoo extends Foo {",
                "  /** @override */",
                "  bar() {}",
                "}",
                "",
                "function f() { (new SubFoo()).bar(); };")),
        deprecatedProp("Property bar of type SubFoo has been deprecated: I have a child class!"));
  }

  @Test
  public void testWarningForSuperclassMethod2() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  /**",
                "   * @protected",
                "   * @deprecated I have another child class...",
                "   */",
                "  bar() {}",
                "}",
                "",
                "class SubFoo extends Foo {",
                "  /**",
                "   * @protected",
                "   * @override",
                "   */",
                "  bar() {}",
                "}",
                "",
                "function f() { (new SubFoo()).bar(); };")),
        deprecatedProp(
            "Property bar of type SubFoo has been deprecated: I have another child class..."));
  }

  @Test
  public void testWarningForDeprecatedClassInGlobalScope() {
    test(
        srcs(
            lines(
                "/** @deprecated I'm a very worldly object! */", //
                "var Foo = class {};",
                "",
                "new Foo();")),
        deprecatedClass("Class Foo has been deprecated: I'm a very worldly object!"));
  }

  @Test
  public void testNoWarningForPrototypeCopying() {
    test(
        srcs(
            lines(
                "var Foo = class {",
                "  bar() {}",
                "};",
                "",
                "/** @deprecated */",
                "Foo.prototype.baz = Foo.prototype.bar;",
                "",
                "(new Foo()).bar();")));
  }

  @Test
  public void testNoWarningOnDeprecatedPrototype() {
    test(
        srcs(
            lines(
                "var Foo = class {};", //
                "",
                "/** @deprecated */",
                "Foo.prototype;")));
  }

  @Test
  public void testPrivateAccessForProperties1() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  /** @private */",
                "  bar_() {}",
                "",
                "  baz() { this.bar_(); }",
                "}",
                "",
                "(new Foo).bar_();")));
  }

  @Test
  public void testPrivateAccessForProperties2() {
    test(
        srcs(
            lines("class Foo {}"),
            lines(
                "Foo.prototype.bar_ = function() {};",
                "Foo.prototype.baz = function() { this.bar_(); };",
                "",
                "(new Foo).bar_();")));
  }

  @Test
  public void testPrivateAccessForProperties3() {
    // Even though baz is "part of the Foo class" the access is disallowed since it's
    // not in the same file.
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @private */",
                "  bar_() {}",
                "}"),
            lines("Foo.prototype.baz = function() { this.bar_(); };")),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testPrivateAccessForProperties4() {
    test(
        srcs(
            lines(
                "/** @unrestricted */",
                "class Foo {",
                "  /** @private */",
                "  bar_() {}",
                "",
                "  ['baz']() { (new Foo()).bar_(); }",
                "}")));
  }

  @Test
  public void testPrivateAccessForProperties5() {
    test(
        srcs(
            lines(
                "class Parent {",
                "  constructor() {",
                "    /** @private */",
                "    this.prop = 'foo';",
                "  }",
                "};"),
            lines(
                "class Child extends Parent {",
                "  constructor() {",
                "    this.prop = 'asdf';",
                "  }",
                "}")),
        error(BAD_PRIVATE_PROPERTY_ACCESS)
            .withMessage("Access to private property prop of Parent not allowed here."));
  }

  @Test
  public void testPrivateAccessForProperties6() {
    test(
        srcs(
            lines(
                "goog.provide('x.y.z.Parent');",
                "",
                "x.y.z.Parent = class {",
                "  constructor() {",
                "    /** @private */",
                "    this.prop = 'foo';",
                "  }",
                "};"),
            lines(
                "goog.require('x.y.z.Parent');",
                "",
                "class Child extends x.y.z.Parent {",
                "  constructor() {",
                "    this.prop = 'asdf';",
                "  }",
                "}")),
        error(BAD_PRIVATE_PROPERTY_ACCESS)
            .withMessage("Access to private property prop of x.y.z.Parent not allowed here."));
  }

  @Test
  public void testPrivateAccessToConstructorThroughNew() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @private */",
                "  constructor() { }",
                "}",
                "",
                "new Foo();")));
  }

  @Test
  public void testPrivateAccessToConstructorThroughExtendsClause() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @private */",
                "  constructor() { }",
                "}",
                "",
                "class SubFoo extends Foo { }")));
  }

  @Test
  public void testPrivateAccessToClass() {
    test(
        srcs(
            lines(
                "/** @private */", //
                "class Foo { }",
                "",
                "Foo;")));
  }

  @Test
  public void testPrivateAccess_googModule() {
    test(
        srcs(
            lines(
                "goog.module('example.One');",
                "",
                "class One {",
                "  /** @private */",
                "  m() {}",
                "}",
                "",
                "exports = One;"),
            lines(
                "goog.module('example.two');",
                "",
                "const One = goog.require('example.One');",
                "",
                "(new One()).m();")),
        error(BAD_PRIVATE_PROPERTY_ACCESS)
            .withMessage("Access to private property m of One not allowed here."));
  }

  @Test
  public void testNoPrivateAccessForProperties1() {
    test(
        srcs(
            lines(
                "class Foo {};", //
                "(new Foo).bar_();"),
            lines(
                "/** @private */",
                "Foo.prototype.bar_ = function() {};",
                "",
                "Foo.prototype.baz = function() { this.bar_(); };")),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties2() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  /** @private */",
                "  bar_() {}",
                "",
                "  baz() { this.bar_(); }",
                "}"),
            "(new Foo).bar_();"),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties3() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @private */",
                "  bar_() {}",
                "}"),
            lines(
                "class OtherFoo {", //
                "  constructor() {",
                "    (new Foo).bar_();",
                "  }",
                "}")),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties4() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @private */",
                "  bar_() {}",
                "}"),
            lines(
                "class SubFoo extends Foo {", //
                "  constructor() {",
                "    this.bar_();",
                "  }",
                "}")),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties5() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @private */",
                "  bar_() {}",
                "}"),
            lines(
                "class SubFoo extends Foo {", //
                "  baz() { this.bar_(); }",
                "}")),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties6() {
    // Overriding a private property with a non-private property
    // in a different file causes problems.
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @private */",
                "  bar_() {}",
                "}"),
            lines(
                "class SubFoo extends Foo {", //
                "  bar_() {}",
                "}")),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties6a() {
    // Same as above, except with namespaced constructors
    test(
        srcs(
            lines(
                "/** @const */ var ns = {};",
                "",
                "ns.Foo = class {",
                "  /** @private */",
                "  bar_() {}",
                "};"),
            lines(
                "ns.SubFoo = class extends ns.Foo {", //
                "  bar_() {}",
                "};")),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties7() {
    // It's OK to override a private property with a non-private property
    // in the same file, but you'll get yelled at when you try to use it.
    test(
        srcs(
            lines(
                "class Foo {",
                "  /** @private */",
                "  bar_() {}",
                "}",
                "",
                "class SubFoo extends Foo {",
                "  bar_() {}",
                "}"),
            lines("SubFoo.prototype.baz = function() { this.bar_(); }")),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties8() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  constructor() {",
                "    /** @private */",
                "    this.bar_ = 3;",
                "  }",
                "}"),
            lines(
                "class SubFoo extends Foo {",
                "  constructor() {",
                "    /** @private */",
                "    this.bar_ = 3;",
                "  }",
                "}")),
        error(PRIVATE_OVERRIDE));
  }

  @Test
  public void testNoPrivateAccessForProperties9() {
    test(
        srcs(
            lines(
                "class Foo {}", //
                "",
                "/** @private */",
                "Foo.prototype.bar_ = 3;"),
            lines("new Foo().bar_;")),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties10() {
    test(
        srcs(
            lines(
                "class Foo {}", //
                "",
                "/** @private */",
                "Foo.prototype.bar_ = function() {};"),
            lines("new Foo().bar_;")),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties11() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @private */",
                "  get bar_() { return 1; }",
                "}"),
            lines("var a = new Foo().bar_;")),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties12() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @private */",
                "  set bar_(x) { this.barValue = x; }",
                "}"),
            lines("new Foo().bar_ = 1;")),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessToConstructorThroughNew() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @private */",
                "  constructor() { }",
                "}"),
            lines("new Foo();")),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessToConstructorThroughExtendsClause() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @private */",
                "  constructor() { }",
                "}"),
            lines("class SubFoo extends Foo { }")),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessToClass() {
    test(
        srcs(
            lines(
                "/** @private */", //
                "class Foo { }"),
            lines("Foo")),
        error(BAD_PRIVATE_GLOBAL_ACCESS));
  }

  @Test
  public void testProtectedAccessForProperties1() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @protected */",
                "  bar() {}",
                "}",
                "",
                "(new Foo).bar();"),
            lines("Foo.prototype.baz = function() { this.bar(); };")));
  }

  @Test
  public void testProtectedAccessForProperties2() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @protected */",
                "  bar() {}",
                "}",
                "",
                "(new Foo).bar();"),
            lines(
                "class SubFoo extends Foo {", //
                "  constructor() {",
                "    this.bar();",
                "  }",
                "}")));
  }

  @Test
  public void testProtectedAccessForProperties3() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @protected */",
                "  bar() {}",
                "}",
                "",
                "(new Foo).bar();"),
            lines(
                "class SubFoo extends Foo {", //
                "  static baz() { (new Foo()).bar(); }",
                "}")));
  }

  @Test
  public void testProtectedAccessForProperties4() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @protected */",
                "  static bar() {}",
                "}"),
            lines(
                "class SubFoo extends Foo {", //
                "  static foo() { super.bar(); }",
                "}")));
  }

  @Test
  public void testProtectedAccessForProperties5() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @protected */ bar() {}",
                "}",
                "",
                "(new Foo).bar();"),
            lines(
                "var SubFoo = class extends Foo {",
                "  constructor() {",
                "    super();",
                "    this.bar();",
                "  }",
                "}")));
  }

  @Test
  public void testProtectedAccessForProperties6() {
    test(
        srcs(
            lines(
                "/** @const */ var goog = {};",
                "",
                "goog.Foo = class {",
                "  /** @protected */",
                "  bar() {}",
                "}"),
            lines(
                "goog.SubFoo = class extends goog.Foo {",
                "  constructor() {",
                "    super();",
                "    this.bar();",
                "  }",
                "};")));
  }

  @Test
  public void testProtectedAccessForProperties7() {
    test(
        srcs(
            lines(
                "var Foo = class {};", //
                "",
                "/** @protected */",
                "Foo.prototype.bar = function() {};"),
            lines(
                "var SubFoo = class extends Foo {",
                "  constructor() {",
                "    super();",
                "    this.bar();",
                "  }",
                "};",
                "",
                "SubFoo.prototype.moo = function() { this.bar(); };")));
  }

  @Test
  public void testProtectedAccessForProperties8() {
    test(
        srcs(
            lines(
                "var Foo = class {};", //
                "",
                "/** @protected */",
                "Foo.prototype.bar = function() {};"),
            lines(
                "var SubFoo = class extends Foo {", //
                "  get moo() { this.bar(); }",
                "};")));
  }

  @Test
  public void testProtectedAccessForProperties9() {
    test(
        srcs(
            lines(
                "/** @unrestricted */", //
                "var Foo = class {};",
                "",
                "/** @protected */",
                "Foo.prototype.bar = function() {};"),
            lines(
                "/** @unrestricted */", //
                "var SubFoo = class extends Foo {",
                "  set moo(val) { this.x = this.bar(); }",
                "};")));
  }

  @Test
  public void testProtectedAccessForProperties10() {
    test(
        srcs(
            SourceFile.fromCode(
                "foo.js",
                lines(
                    "var Foo = class {", //
                    "  /** @protected */",
                    "  bar() {}",
                    "}")),
            SourceFile.fromCode(
                "sub_foo.js",
                lines(
                    "var SubFoo = class extends Foo {};",
                    "",
                    "(function() {",
                    "  SubFoo.prototype.baz = function() { this.bar(); }",
                    "})();"))));
  }

  @Test
  public void testProtectedAccessForProperties11() {
    test(
        srcs(
            SourceFile.fromCode(
                "foo.js",
                lines(
                    "goog.provide('Foo');",
                    "",
                    "/** @interface */",
                    "Foo = class {};",
                    "",
                    "/** @protected */",
                    "Foo.prop = {};")),
            SourceFile.fromCode(
                "bar.js",
                lines(
                    "goog.require('Foo');",
                    "",
                    "/** @implements {Foo} */",
                    "class Bar {",
                    "  constructor() {",
                    "    Foo.prop;",
                    "  }",
                    "};"))));
  }

  @Test
  public void testProtectedAccessForProperties12() {
    test(
        srcs(
            SourceFile.fromCode(
                "a.js",
                lines(
                    "goog.provide('A');",
                    "",
                    "var A = class {",
                    "  constructor() {",
                    "    /** @protected {string} */",
                    "    this.prop;",
                    "  }",
                    "};")),
            SourceFile.fromCode(
                "b.js",
                lines(
                    "goog.require('A');",
                    "",
                    "var B = class extends A {",
                    "  constructor() {",
                    "    super();",
                    "",
                    "    this.prop.length;",
                    "  }",
                    "};"))));
  }

  // FYI: Java warns for the b1.method access in c.js.
  // Instead of following that in NTI, we chose to follow the behavior of
  // the old JSCompiler type checker, to make migration easier.
  @Test
  public void testProtectedAccessForProperties13() {
    test(
        srcs(
            SourceFile.fromCode(
                "a.js",
                lines(
                    "goog.provide('A');",
                    "",
                    "var A = class {",
                    "  /** @protected */",
                    "  method() {}",
                    "}")),
            SourceFile.fromCode(
                "b1.js",
                lines(
                    "goog.require('A');",
                    "",
                    "goog.provide('B1');",
                    "",
                    "var B1 = class extends A {",
                    "  /** @override */",
                    "  method() {}",
                    "}")),
            SourceFile.fromCode(
                "b2.js",
                lines(
                    "goog.require('A');",
                    "",
                    "goog.provide('B2');",
                    "",
                    "var B2 = class extends A {",
                    "  /** @override */",
                    "  method() {}",
                    "};")),
            SourceFile.fromCode(
                "c.js",
                lines(
                    "goog.require('B1');",
                    "goog.require('B2');",
                    "",
                    "var C = class extends B2 {",
                    "  /** @param {!B1} b1 */",
                    "  constructor(b1) {",
                    "    var x = b1.method();",
                    "  }",
                    "};"))));
  }

  @Test
  public void testProtectedAccessForProperties14() {
    // access in member function
    test(
        srcs(
            lines(
                "var Foo = class {};", //
                "",
                "/** @protected */",
                "Foo.prototype.bar = function() {};"),
            lines(
                "var OtherFoo = class extends Foo {",
                "  constructor() {",
                "    super();",
                "",
                "    this.bar();",
                "  }",
                "};",
                "",
                "OtherFoo.prototype.moo = function() { new Foo().bar(); };")));
  }

  @Test
  public void testProtectedAccessForProperties15() {
    // access in computed member function
    test(
        srcs(
            lines(
                "/** @unrestricted */",
                "var Foo = class {};", //
                "",
                "/** @protected */",
                "Foo.prototype.bar = function() {};"),
            lines(
                "/** @unrestricted */",
                "var OtherFoo = class extends Foo {",
                "  constructor() {",
                "    super();",
                "    this['bar']();",
                "  };",
                "}",
                "",
                "OtherFoo.prototype['bar'] = function() { new Foo().bar(); };")));
  }

  @Test
  public void testProtectedAccessForProperties16() {
    // access in nested arrow function
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @protected */ bar() {}",
                "}"),
            lines(
                "class OtherFoo extends Foo {",
                "  constructor() { super(); var f = () => this.bar(); }",
                "  baz() { return () => this.bar(); }",
                "}")));
  }

  @Test
  public void testNoProtectedAccess_forOverriddenProperty_elsewhereInSubclassFile() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @protected */",
                "  bar() { }",
                "}"),
            lines(
                "class Bar extends Foo {", //
                "  /** @override */",
                "  bar() { }",
                "}",
                "",
                // TODO(b/113705099): This should be legal.
                "(new Bar()).bar();" // But `Foo::bar` is still invisible.
                )),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testProtectedAccessToConstructorThroughExtendsClause() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @protected */ constructor() {}",
                "}"),
            lines("class OtherFoo extends Foo { }")));
  }

  @Test
  public void testProtectedAccessToConstructorThroughSubclassInstanceMethod() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @protected */ constructor() {}",
                "}"),
            lines(
                "class OtherFoo extends Foo {", //
                "  bar() { new Foo(); }",
                "}")));
  }

  @Test
  public void testProtectedAccessToClassThroughSubclassInstanceMethod() {
    test(
        srcs(
            lines(
                "/** @protected */", //
                "class Foo {}"),
            lines(
                "class OtherFoo extends Foo {", //
                "  bar() { Foo; }",
                "}")));
  }

  @Test
  public void testProtectedAccessThroughNestedFunction() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @protected */ bar() {}",
                "}"),
            lines(
                "class Bar extends Foo {",
                "  constructor(/** Foo */ foo) {",
                "    function f() {",
                "      foo.bar();",
                "    }",
                "  }",
                "}")));
  }

  @Test
  public void testProtectedAccessThroughNestedEs5Class() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @protected */ bar() {}",
                "}"),
            lines(
                "class Bar extends Foo {",
                "  constructor() {",
                "    /** @constructor */",
                "    const Nested = function() {}",
                "",
                "    /** @param {!Foo} foo */",
                "    Nested.prototype.qux = function(foo) {",
                "      foo.bar();",
                "    }",
                "  }",
                "}")));
  }

  @Test
  public void testProtectedAccessThroughNestedEs6Class() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @protected */ bar() {}",
                "}"),
            lines(
                "class Bar extends Foo {",
                "  constructor() {",
                "    class Nested {",
                "      qux(/** Foo */ foo) {",
                "        foo.bar();",
                "      }",
                "    }",
                "  }",
                "}")));
  }

  @Test
  public void testNoProtectedAccessForProperties1() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @protected */",
                "  bar() {}",
                "}"),
            "(new Foo).bar();"),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForProperties2() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @protected */",
                "  bar() {}",
                "}"),
            lines(
                "class OtherFoo {", //
                "  constructor() {",
                "    (new Foo).bar();",
                "  }",
                "}")),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForProperties3() {
    test(
        srcs(
            lines(
                "class Foo {}",
                "",
                "class SubFoo extends Foo {",
                "  /** @protected */",
                "  bar() {}",
                "}"),
            lines(
                "class SubberFoo extends Foo {",
                "  constructor() {",
                "    (new SubFoo).bar();",
                "  }",
                "}")),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForProperties4() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  constructor() {",
                "    (new SubFoo).bar();",
                "  }",
                "}"),
            lines(
                "class SubFoo extends Foo {", //
                "  /** @protected */",
                "  bar() {}",
                "}")),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForProperties5() {
    test(
        srcs(
            lines(
                "/** @const */ var goog = {};",
                "",
                "goog.Foo = class {",
                "  /** @protected */",
                "  bar() {}",
                "}"),
            lines(
                "goog.NotASubFoo = class {",
                "  constructor() {",
                "    (new goog.Foo).bar();",
                "  }",
                "}")),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForProperties6() {
    test(
        srcs(
            lines(
                "class Foo {}", //
                "",
                "/** @protected */",
                "Foo.prototype.bar = 3;"),
            lines("new Foo().bar;")),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForProperties7() {
    test(
        srcs(
            lines(
                "class Foo {}", //
                "",
                "/** @protected */",
                "Foo.prototype.bar = function() {};"),
            lines("new Foo().bar();")),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForProperties8() {
    test(
        srcs(
            lines(
                "var Foo = class {};", //
                "",
                "/** @protected */",
                "Foo.prototype.bar = function() {};"),
            lines(
                "var OtherFoo = class {",
                "  constructor() {",
                "    this.bar();",
                "  }",
                "}",
                "",
                "OtherFoo.prototype.moo = function() { new Foo().bar(); };")),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForProperties9() {
    test(
        srcs(
            lines(
                "var Foo = class {};", //
                "",
                "/** @protected */",
                "Foo.prototype.bar = function() {};"),
            lines(
                "var OtherFoo = class {",
                "  constructor() { this['bar'](); }",
                "};",
                "",
                "OtherFoo.prototype['bar'] = function() { new Foo().bar(); };")),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccess_forInheritedProperty_elsewhereInSubclassFile() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @protected */",
                "  bar() { }",
                "}"),
            lines(
                "class Bar extends Foo { }", //
                "",
                "(new Bar()).bar();" // But `Foo::bar` is still invisible.
                )),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessToConstructorFromUnrelatedClass() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @protected */",
                "  constructor() {}",
                "}"),
            lines(
                "class OtherFoo {", //
                "  bar() { new Foo(); }",
                "}")),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForPropertiesWithNoRhs() {
    test(
        srcs(
            lines(
                "class Foo {}", //
                "",
                "/** @protected */",
                "Foo.prototype.x;"),
            lines(
                "class Bar extends Foo {}", //
                "",
                "/** @protected */",
                "Bar.prototype.x;")));
  }

  @Test
  public void testPackagePrivateAccessForProperties1() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  /** @package */",
                "  bar() {}",
                "",
                "  baz() { this.bar(); }",
                "}",
                "",
                "(new Foo).bar();")));
  }

  @Test
  public void testPackagePrivateAccessForProperties2() {
    test(
        srcs(
            SourceFile.fromCode(Compiler.joinPathParts("foo", "bar.js"), "class Foo {}"),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                lines(
                    "/** @package */", //
                    "Foo.prototype.bar = function() {};",
                    "",
                    "Foo.prototype.baz = function() { this.bar(); };",
                    "",
                    "(new Foo).bar();"))));
  }

  @Test
  public void testPackagePrivateAccessForProperties3() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "class Foo {", //
                    "  /** @package */",
                    "  bar() {}",
                    "}",
                    "",
                    "(new Foo).bar();")),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "baz.js"),
                "Foo.prototype.baz = function() { this.bar(); };")));
  }

  @Test
  public void testPackagePrivateAccessForProperties4() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "class Foo {", //
                    "  /** @package */",
                    "  bar() {}",
                    "}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "baz.js"),
                "Foo.prototype['baz'] = function() { (new Foo()).bar(); };")));
  }

  @Test
  public void testPackagePrivateAccessForProperties5() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "class Parent {", //
                    "  constructor() {",
                    "    /** @package */",
                    "    this.prop = 'foo';",
                    "  }",
                    "}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                lines(
                    "class Child extends Parent {",
                    "  constructor() {",
                    "    this.prop = 'asdf';",
                    "  }",
                    "}",
                    "",
                    "Child.prototype = new Parent();"))),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testPackageAccessToConstructorThroughNew() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "class Foo {", //
                    "  /** @package */",
                    "  constructor() {}",
                    "}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "quux.js"), //
                "new Foo();")));
  }

  @Test
  public void testPackageAccessToConstructorThroughExtendsClause() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "class Foo {", //
                    "  /** @package */",
                    "  constructor() {}",
                    "}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "quux.js"), //
                "class SubFoo extends Foo { }")));
  }

  @Test
  public void testPackageAccessToClass() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/** @package */", //
                    "class Foo {}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "quux.js"), //
                "Foo;")));
  }

  @Test
  public void testPackageAccessToConstructorThroughNew_fileOveriew() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/** @fileoverview @package */", //
                    "",
                    "class Foo {", //
                    "  constructor() {}",
                    "}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "quux.js"), //
                "new Foo();")));
  }

  @Test
  public void testPackageAccessToConstructorThroughExtendsClause_fileOveriew() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/** @fileoverview @package */", //
                    "",
                    "class Foo {", //
                    "  constructor() {}",
                    "}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "quux.js"), //
                "class SubFoo extends Foo { }")));
  }

  @Test
  public void testPackageAccessToClass_fileOveriew() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/** @fileoverview @package */", //
                    "",
                    "class Foo {}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "quux.js"), //
                "Foo;")));
  }

  @Test
  public void testNoPackagePrivateAccessForProperties1() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "class Foo {}", //
                    "",
                    "(new Foo).bar();")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                lines(
                    "/** @package */",
                    "Foo.prototype.bar = function() {};",
                    "",
                    "Foo.prototype.baz = function() { this.bar(); };"))),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackagePrivateAccessForProperties2() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "class Foo {",
                    "  /** @package */",
                    "  bar() {}",
                    "",
                    "  baz() { this.bar(); };",
                    "}")),
            SourceFile.fromCode(Compiler.joinPathParts("baz", "quux.js"), "(new Foo).bar();")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackagePrivateAccessForProperties3() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "class Foo {", //
                    "  /** @package */",
                    "  bar() {};",
                    "}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                lines(
                    "class OtherFoo {", //
                    "  constructor() {",
                    "    (new Foo).bar();",
                    "  }",
                    "}"))),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackagePrivateAccessForProperties4() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "class Foo {", //
                    "  /** @package */",
                    "  bar() {}",
                    "}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                lines(
                    "class SubFoo extends Foo {",
                    "  constructor() {",
                    "    this.bar();",
                    "  }",
                    "}"))),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackagePrivateAccessForProperties5() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "class Foo {", //
                    "  /** @package */",
                    "  bar() {}",
                    "}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                lines(
                    "class SubFoo extends Foo {", //
                    "  baz() { this.bar(); }",
                    "}"))),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackagePrivateAccessForProperties6() {
    // Overriding a private property with a non-package-private property
    // in a different file causes problems.
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "class Foo {", //
                    "  /** @package */",
                    "  bar() {}",
                    "}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                lines(
                    "class SubFoo extends Foo {", //
                    "  bar() {}",
                    "}"))),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackagePrivateAccessForProperties7() {
    // It's OK to override a package-private property with a
    // non-package-private property in the same file, but you'll get
    // yelled at when you try to use it.
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "class Foo {",
                    "  /** @package */",
                    "  bar() {}",
                    "}",
                    "",
                    "class SubFoo extends Foo {",
                    "  bar() {}",
                    "}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "SubFoo.prototype.baz = function() { this.bar(); }")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackgeAccessToConstructorThroughNew() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "class Foo {", //
                    "  /** @package */",
                    "  constructor() {}",
                    "}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"), //
                "new Foo();")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackageAccessToConstructorThroughExtendsClause() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "class Foo {", //
                    "  /** @package */",
                    "  constructor() {}",
                    "}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"), //
                "class SubFoo extends Foo { }")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackageAccessToClass() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/** @package */", //
                    "class Foo {}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"), //
                "Foo;")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackgeAccessToConstructorThroughNew_fileOveriew() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/** @fileoverview @package */", //
                    "",
                    "/** @public */",
                    "class Foo {",
                    "  constructor() {}",
                    "}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"), //
                "new Foo();")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackageAccessToConstructorThroughExtendsClause_fileOveriew() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/** @fileoverview @package */", //
                    "",
                    "/** @public */",
                    "class Foo {",
                    "  constructor() {}",
                    "}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"), //
                "class SubFoo extends Foo { }")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackageAccessToClass_fileOveriew() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/** @fileoverview @package */", //
                    "",
                    "class Foo {}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"), //
                "Foo;")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void
      testOverrideWithoutVisibilityRedeclInFileWithFileOverviewVisibilityNotAllowed_OneFile() {
    test(
        srcs(
            lines(
                "/**",
                " * @fileoverview",
                " * @package",
                " */",
                "",
                "Foo = class {",
                "  /** @private */",
                "  privateMethod_() {}",
                "}",
                "",
                "Bar = class extends Foo {",
                "  /** @override */",
                "  privateMethod_() {}",
                "}")),
        error(BAD_PROPERTY_OVERRIDE_IN_FILE_WITH_FILEOVERVIEW_VISIBILITY));
  }

  @Test
  public void
      testOverrideWithoutVisibilityRedeclInFileWithFileOverviewVisibilityNotAllowed_TwoFiles() {
    test(
        srcs(
            lines(
                "Foo = class {", //
                "  /** @protected */",
                "  protectedMethod() {}",
                "};"),
            lines(
                "/**",
                " * @fileoverview ",
                " * @package",
                " */",
                "",
                "Bar = class extends Foo {",
                "  /** @override */",
                "  protectedMethod() {}",
                "};")),
        error(BAD_PROPERTY_OVERRIDE_IN_FILE_WITH_FILEOVERVIEW_VISIBILITY));
  }

  @Test
  public void testOverrideWithoutVisibilityRedeclInFileWithNoFileOverviewOk() {
    test(
        srcs(
            lines(
                "Foo = class {",
                "  /** @private */",
                "  privateMethod_() {}",
                "};",
                "",
                "Bar = class extends Foo {",
                "  /** @override */",
                "  privateMethod_() {}",
                "};")));
  }

  @Test
  public void testOverrideWithoutVisibilityRedeclInFileWithNoFileOverviewVisibilityOk() {
    test(
        srcs(
            lines(
                "/**",
                " * @fileoverview",
                " */",
                "",
                "Foo = class {",
                "  /** @private */",
                "  privateMethod_() {}",
                "};",
                "",
                "Bar = class extends Foo {",
                "  /** @override */",
                "  privateMethod_() {}",
                "};")));
  }

  @Test
  public void testOverrideWithVisibilityRedeclInFileWithFileOverviewVisibilityOk_OneFile() {
    test(
        srcs(
            lines(
                "/**",
                " * @fileoverview",
                " * @package",
                " */",
                "",
                "Foo = class {",
                "  /** @private */",
                "  privateMethod_() {};",
                "};",
                "",
                "Bar = class extends Foo {",
                "  /** @override @private */",
                "  privateMethod_() {};",
                "};")));
  }

  @Test
  public void testOverrideWithVisibilityRedeclInFileWithFileOverviewVisibilityOk_TwoFiles() {
    test(
        srcs(
            lines(
                "Foo = class {", //
                "  /** @protected */",
                "  protectedMethod() {}",
                "};"),
            lines(
                "/**",
                " * @fileoverview",
                " * @package",
                " */",
                "",
                "Bar = class extends Foo {",
                "  /** @override @protected */",
                "  protectedMethod() {}",
                "};")));
  }

  @Test
  public void testPublicFileOverviewVisibilityDoesNotApplyToNameWithExplicitPackageVisibility() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @public",
                    " */",
                    "",
                    "/** @package */",
                    "class Foo {}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"), //
                "Foo;")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testPackageFileOverviewVisibilityDoesNotApplyToNameWithExplicitPublicVisibility() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @package",
                    " */",
                    "/** @public */",
                    "class Foo {}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"), //
                "Foo;")));
  }

  @Test
  public void testPackageFileOverviewVisibilityAppliesToNameWithoutExplicitVisibility() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/**", //
                    " * @fileoverview",
                    " * @package",
                    " */",
                    "",
                    "var Foo = class {};")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"), //
                "Foo;")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void
      testPackageFileOverviewVisibilityDoesNotApplyToPropertyWithExplicitPublicVisibility() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @package",
                    " */",
                    "",
                    "/** @public */", // The class must be visible.
                    "Foo = class {",
                    "  /** @public */", // The constructor must be visible.
                    "  constructor() { }",
                    "",
                    "  /** @public */",
                    "  bar() {}",
                    "};")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                lines(
                    "var foo = new Foo();", //
                    "foo.bar();"))));
  }

  @Test
  public void
      testPublicFileOverviewVisibilityDoesNotApplyToPropertyWithExplicitPackageVisibility() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @public",
                    " */",
                    "",
                    "Foo = class {",
                    "  /** @package */",
                    "  bar() {}",
                    "};")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                lines(
                    "var foo = new Foo();", //
                    "foo.bar();"))),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testPublicFileOverviewVisibilityAppliesToPropertyWithoutExplicitVisibility() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @public",
                    " */",
                    "",
                    "Foo = class {",
                    "  bar() {}",
                    "}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                lines(
                    "var foo = new Foo();", //
                    "foo.bar();"))));
  }

  @Test
  public void testPackageFileOverviewVisibilityAppliesToPropertyWithoutExplicitVisibility() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @package",
                    " */",
                    "",
                    "/** @public */", // The class must be visible.
                    "Foo = class {",
                    "  /** @public */", // The constructor must be visible.
                    "  constructor() { }",
                    "",
                    "  bar() {}",
                    "}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                lines(
                    "var foo = new Foo();", //
                    "foo.bar();"))),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testFileOverviewVisibilityComesFromDeclarationFileNotUseFile() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @package",
                    " */",
                    "",
                    "/** @public */", // The class must be visible.
                    "Foo = class {",
                    "  /** @public */", // The constructor must be visible.
                    "  constructor() { }",
                    "",
                    "  bar() {}",
                    "}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @public",
                    " */",
                    "",
                    "var foo = new Foo();",
                    "foo.bar();"))),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testImplicitSubclassConstructor_doesNotInheritVisibility_andIsPublic() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @private */",
                "  constructor() { }",
                "}",
                "",
                // The implict constructor for `SubFoo` should be treated as public.
                "class SubFoo extends Foo { }"),
            // So we get no warning for using it here.
            lines("new SubFoo();")));
  }

  @Test
  public void testImplicitSubclassConstructor_doesNotInheritVisibility_andUsesFileOverview() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @package",
                    " */",
                    "",
                    "class Foo {", //
                    "  /** @private */",
                    "  constructor() { }",
                    "}",
                    "",
                    // The implict constructor for `SubFoo` should be trated as package.
                    "/** @public */", // The class must be visible.
                    "class SubFoo extends Foo { }")),
            SourceFile.fromCode(Compiler.joinPathParts("baz", "quux.js"), lines("new SubFoo();"))),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testUnannotatedSubclassConstructor_doesNotInheritVisibility_andIsPublic() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @private */",
                "  constructor() { }",
                "}",
                "",
                "class SubFoo extends Foo {",
                // The unannotated constructor for `SubFoo` should be treated as public.
                "  constructor() {",
                "    super();",
                "  }",
                "}"),
            // So we get no warning for using it here.
            lines("new SubFoo();")));
  }

  @Test
  public void testUnannotatedSubclassConstructor_doesNotInheritVisibility_andUsesFileOverview() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @package",
                    " */",
                    "",
                    "class Foo {", //
                    "  /** @private */",
                    "  constructor() { }",
                    "}",
                    "",
                    "/** @public */", // The class must be visible.
                    "class SubFoo extends Foo {",
                    // The unannotated constructor for `SubFoo` should be treated as package.
                    "  constructor() {",
                    "    super();",
                    "  }",
                    "}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"), //
                lines("new SubFoo();"))),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoExceptionsWithBadConstructors1() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  constructor() {",
                "    (new Bar).bar();",
                "  }",
                "}",
                "",
                "class Bar {",
                "  /** @protected */",
                "  bar() {}",
                "}")));
  }

  @Test
  public void testNoExceptionsWithBadConstructors2() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  bar() {}",
                "}",
                "",
                "class Bar {",
                "  /** @protected */ ",
                "  bar() { (new Foo).bar(); }",
                "}")));
  }

  @Test
  public void testGoodOverrideOfProtectedProperty() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @protected */",
                "  bar() {}",
                "}"),
            lines(
                "class SubFoo extends Foo {", //
                "  /** @inheritDoc */",
                "  bar() {}",
                "}")));
  }

  @Test
  public void testBadOverrideOfProtectedProperty() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @protected */",
                "  bar() {}",
                "}"),
            lines(
                "class SubFoo extends Foo {", //
                "  /** @private */",
                "  bar() {}",
                "}")),
        error(VISIBILITY_MISMATCH));
  }

  @Test
  public void testBadOverrideOfPrivateProperty() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @private */",
                "  bar() {}",
                "}"),
            lines(
                "class SubFoo extends Foo {", //
                "  /** @protected */",
                "  bar() {}",
                "}")),
        error(PRIVATE_OVERRIDE));
  }

  @Test
  public void testAccessOfStaticMethodOnPrivateClass() {
    test(
        srcs(
            lines(
                "/** @private */", //
                "class Foo {",
                "  static create() { return new Foo(); }",
                "}"),
            lines("Foo.create()")),
        error(BAD_PRIVATE_GLOBAL_ACCESS));
  }

  @Test
  public void testAccessOfStaticMethodOnPrivateQualifiedConstructor() {
    test(
        srcs(
            lines(
                "/** @const */",
                "var goog = {};",
                "",
                "/** @private */",
                "goog.Foo = class {",
                "  static create() { return new goog.Foo(); }",
                "}"),
            lines("goog.Foo.create()")),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testInstanceofOfPrivateConstructor() {
    test(
        srcs(
            lines(
                "/** @const */",
                "var goog = {};",
                "",
                "goog.Foo = class {",
                "  /** @private */",
                "  constructor() {}",
                "};"),
            lines("goog instanceof goog.Foo")));
  }

  @Test
  public void testOkAssignmentOfDeprecatedProperty() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  constructor() {",
                "    /** @deprecated */",
                "    this.bar = 3;",
                "  }",
                "}")));
  }

  @Test
  public void testBadReadOfDeprecatedProperty() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  constructor() {",
                "    /** @deprecated GRR */",
                "    this.bar = 3;",
                "",
                "    this.baz = this.bar;",
                "  }",
                "}")),
        deprecatedProp("Property bar of type Foo has been deprecated: GRR"));
  }

  @Test
  public void testNullableDeprecatedProperty() {
    test(
        srcs(
            lines(
                "class Foo {}",
                "",
                "/** @deprecated */",
                "Foo.prototype.length;",
                "",
                "/** @param {?Foo} x */",
                "function f(x) { return x.length; }")),
        error(DEPRECATED_PROP));
  }

  @Test
  public void testNullablePrivateProperty() {
    test(
        srcs(
            lines(
                "class Foo {}", //
                "",
                "/** @private */",
                "Foo.prototype.length;"),
            lines(
                "/** @param {?Foo} x */", //
                "function f(x) { return x.length; }")),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testPrivatePropertyByConvention1() {
    test(
        srcs(
            lines(
                "class Foo {}", //
                "",
                "/** @type {number} */",
                "Foo.prototype.length_;"),
            lines(
                "/** @param {?Foo} x */", //
                "function f(x) { return x.length_; }")),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testPrivatePropertyByConvention2() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  constructor() {",
                "    /** @type {number} */",
                "    this.length_ = 1;",
                "  }",
                "}",
                "",
                "/** @type {number} */",
                " Foo.prototype.length_;"),
            lines(
                "/** @param {Foo} x */", //
                "function f(x) { return x.length_; }")),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testDeclarationAndConventionConflict1() {
    test(
        srcs(
            lines(
                "class Foo {}", //
                "",
                "/** @protected */",
                "Foo.prototype.length_;")),
        error(CONVENTION_MISMATCH));
  }

  @Test
  public void testDeclarationAndConventionConflict2() {
    test(
        srcs(
            lines(
                "class Foo {}", //
                "",
                "/** @public {number} */",
                "Foo.prototype.length_;")),
        error(CONVENTION_MISMATCH));
  }

  @Test
  public void testDeclarationAndConventionConflict3() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  constructor() {",
                "    /** @protected */",
                "    this.length_ = 1;",
                "  }",
                "}")),
        error(CONVENTION_MISMATCH));
  }

  @Test
  public void testDeclarationAndConventionConflict4a() {
    test(
        srcs(
            lines(
                "class Foo {}",
                "",
                "/** @protected */",
                "Foo.prototype.length_ = 1;",
                "",
                "new Foo().length_")),
        error(CONVENTION_MISMATCH));
  }

  @Test
  public void testDeclarationAndConventionConflict4b() {
    test(
        srcs(
            lines(
                "/** @const */ var NS = {};",
                "",
                "NS.Foo = class {};",
                "",
                "/** @protected */",
                "NS.Foo.prototype.length_ = 1;",
                "",
                "(new NS.Foo()).length_;")),
        error(CONVENTION_MISMATCH));
  }

  @Test
  public void testDeclarationAndConventionConflict5() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @protected */",
                "  get length_() { return 1; }",
                "}")),
        error(CONVENTION_MISMATCH));
  }

  @Test
  public void testDeclarationAndConventionConflict6() {
    test(
        srcs(
            lines(
                "class Foo {", //
                "  /** @protected */",
                "  set length_(x) { }",
                "}")),
        error(CONVENTION_MISMATCH));
  }

  @Test
  public void testDeclarationAndConventionConflict10() {
    test(
        srcs(
            lines(
                "class Foo {}",
                "",
                "/** @protected */",
                "Foo.prototype.length_ = function() { return 1; };")),
        error(CONVENTION_MISMATCH));
  }

  @Test
  public void testConstantProperty1a() {
    test(
        srcs(
            lines(
                "class A {",
                "  constructor() {",
                "    /** @const */",
                "    this.bar = 3;",
                "  }",
                "}",
                "",
                "class B {",
                "  constructor() {",
                "    /** @const */",
                "    this.bar = 3;",
                "",
                "    this.bar += 4;",
                "  }",
                "}")),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantProperty1b() {
    test(
        srcs(
            lines(
                "class A {",
                "  constructor() {",
                "    this.BAR = 3;",
                "  }",
                "}",
                "",
                "class B {",
                "  constructor() {",
                "    this.BAR = 3;",
                "",
                "    this.BAR += 4;",
                "  }",
                "}")),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantProperty2a() {
    test(
        srcs(
            lines(
                "class  Foo {}",
                "",
                "/** @const */",
                "Foo.prototype.prop = 2;",
                "",
                "var foo = new Foo();",
                "foo.prop = 3;")),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantProperty2b() {
    test(
        srcs(
            lines(
                "class  Foo {}",
                "",
                "Foo.prototype.PROP = 2;",
                "",
                "var foo = new Foo();",
                "foo.PROP = 3;")),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantProperty4() {
    test(
        srcs(
            lines(
                "class Cat {}", //
                "",
                "/** @const */",
                "Cat.test = 1;",
                "",
                "Cat.test *= 2;")),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantProperty4b() {
    test(
        srcs(
            lines(
                "class Cat { }", //
                "",
                "Cat.TEST = 1;",
                "Cat.TEST *= 2;")),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantProperty5() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  constructor() {",
                "    this.prop = 1;",
                "  }",
                "}",
                "",
                "/** @const */",
                "Foo.prototype.prop;",
                "",
                "Foo.prototype.prop = 2")),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantProperty6() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  constructor() {",
                "    this.prop = 1;",
                "  }",
                "}",
                "",
                "/** @const */",
                "Foo.prototype.prop = 2;")),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantProperty7() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  bar_() {}",
                "}",
                "",
                "class SubFoo extends Foo {",
                "  /**",
                "   * @const",
                "   * @override",
                "   */",
                "  bar_() {}",
                "",
                "  baz() { this.bar_(); }",
                "}")));
  }

  @Test
  public void testConstantProperty9() {
    test(
        srcs(
            lines(
                "class A {",
                "  constructor() {",
                "    /** @const */",
                "    this.bar = 3;",
                "  }",
                "}",
                "",
                "class B {",
                "  constructor() {",
                "    this.bar = 4;",
                "  }",
                "}")));
  }

  @Test
  public void testConstantProperty10a() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  constructor() {",
                "    this.prop = 1;",
                "  }",
                "}",
                "",
                "/** @const */",
                "Foo.prototype.prop;")));
  }

  @Test
  public void testConstantProperty10b() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  constructor() {",
                "    this.PROP = 1;",
                "  }",
                "}",
                "",
                "Foo.prototype.PROP;")));
  }

  @Test
  public void testConstantProperty11() {
    test(
        srcs(
            lines(
                "class Foo {}",
                "",
                "/** @const */",
                "Foo.prototype.bar;",
                "",
                "class SubFoo extends Foo {",
                "  constructor() {",
                "    this.bar = 5;",
                "    this.bar = 6;",
                "  }",
                "}")),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantProperty12() {
    test(
        srcs(
            lines(
                "class Foo {}",
                "",
                "/** @const */",
                "Foo.prototype.bar;",
                "",
                "class SubFoo extends Foo {",
                "  constructor() {",
                "    this.bar = 5;",
                "  }",
                "}",
                "",
                "class SubFoo2 extends Foo {",
                "  constructor() {",
                "    this.bar = 5;",
                "  }",
                "}")));
  }

  @Test
  public void testConstantProperty13() {
    test(
        srcs(
            lines(
                "class Foo {}",
                "",
                "/** @const */",
                "Foo.prototype.bar;",
                "",
                "class SubFoo extends Foo {",
                "  constructor() {",
                "    this.bar = 5;",
                "  }",
                "}",
                "",
                "class SubSubFoo extends SubFoo {",
                "  constructor() {",
                "    this.bar = 5;",
                "  }",
                "}")),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantProperty14() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  constructor() {",
                "    /** @const */",
                "    this.bar = 3;",
                "",
                "    delete this.bar;",
                "  }",
                "}")),
        error(CONST_PROPERTY_DELETED));
  }

  @Test
  public void testConstantPropertyInExterns() {
    test(
        externs(
            lines(
                "class Foo {}", //
                "",
                "/** @const */",
                "Foo.prototype.PROP;")),
        srcs(
            lines(
                "var f = new Foo();", //
                "",
                "f.PROP = 1;",
                "f.PROP = 2;")),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantProperty15a() {
    test(
        srcs(
            lines(
                "class Foo {",
                "  constructor() {",
                "    this.CONST = 100;",
                "  }",
                "}",
                "",
                "/** @type {Foo} */",
                "var foo = new Foo();",
                "",
                "/** @type {number} */",
                "foo.CONST = 0;")),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantProperty15b() {
    test(
        srcs(
            lines(
                "class Foo {}",
                "",
                "Foo.prototype.CONST = 100;",
                "",
                "/** @type {Foo} */",
                "var foo = new Foo();",
                "",
                "/** @type {number} */",
                "foo.CONST = 0;")),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantProperty15c() {
    test(
        srcs(
            lines(
                "class Bar {",
                "  constructor() {",
                "    this.CONST = 100;",
                "  }",
                "}",
                "",
                "class Foo extends Bar {};",
                "",
                "/** @type {Foo} */",
                "var foo = new Foo();",
                "",
                "/** @type {number} */",
                "foo.CONST = 0;")),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantProperty16() {
    test(
        srcs(
            lines(
                "class Foo {}", //
                "",
                "Foo.CONST = 100;",
                "",
                "class Bar {}",
                "",
                "Bar.CONST = 100;")));
  }

  @Test
  public void testFinalClassCannotBeSubclassed() {
    test(
        srcs(
            lines(
                "/** @final */", //
                "var Foo = class {};",
                "",
                "var Bar = class extends Foo {};")),
        error(EXTEND_FINAL_CLASS));

    test(
        srcs(
            lines(
                "/** @final */", //
                "class Foo {};",
                "",
                "class Bar extends Foo {};")),
        error(EXTEND_FINAL_CLASS));

    test(
        srcs(
            lines(
                "/** @const */", //
                "var Foo = class {};",
                "",
                "var Bar = class extends Foo {};")));
  }

  @Test
  public void testCircularPrototypeLink() {
    // NOTE: this does yield a useful warning, except we don't check for it in this test:
    //      WARNING - Cycle detected in inheritance chain of type Foo
    // This warning already has a test: TypeCheckTest::testPrototypeLoop.
    test(
        srcs(
            lines(
                "class Foo extends Foo {}", //
                "",
                "/** @const */",
                "Foo.prop = 1;",
                "",
                "Foo.prop = 2;")),
        error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  private static Diagnostic deprecatedName(String errorMessage) {
    return error(DEPRECATED_NAME_REASON).withMessage(errorMessage);
  }

  private static Diagnostic deprecatedProp(String errorMessage) {
    return error(DEPRECATED_PROP_REASON).withMessage(errorMessage);
  }

  private static Diagnostic deprecatedClass(String errorMessage) {
    return error(DEPRECATED_CLASS_REASON).withMessage(errorMessage);
  }
}
