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
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_NAME;
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_NAME_REASON;
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_PROP;
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_PROP_REASON;
import static com.google.javascript.jscomp.CheckAccessControls.EXTEND_FINAL_CLASS;
import static com.google.javascript.jscomp.CheckAccessControls.PRIVATE_OVERRIDE;
import static com.google.javascript.jscomp.CheckAccessControls.VISIBILITY_MISMATCH;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link CheckAccessControls}.
 *
 * <p>This file has a fork, {@link CheckAccessControlsEs6Test}, because nearly all cases require
 * duplication. If a case using `@constructor`, `@interface`, or `@record` is added to this suite, a
 * similar case should be added there under the same name using `class`.
 */

@RunWith(JUnit4.class)
public final class CheckAccessControlsTest extends CompilerTestCase {

  public CheckAccessControlsTest() {
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

  private void testDepName(String js, String errorMessage) {
    testError(js, DEPRECATED_NAME_REASON, errorMessage);
  }

  private void testDepProp(String js, String errorMessage) {
    testError(js, DEPRECATED_PROP_REASON, errorMessage);
  }

  private void testDepClass(String js, String errorMessage) {
    testError(js, DEPRECATED_CLASS_REASON, errorMessage);
  }

  @Test
  public void testDeprecatedFunctionNoReason() {
    testError("/** @deprecated */ function f() {} function g() { f(); }", DEPRECATED_NAME);
  }

  @Test
  public void testDeprecatedFunction() {
    testDepName(
        "/** @deprecated Some Reason */ function f() {} function g() { f(); }",
        "Variable f has been deprecated: Some Reason");
  }

  @Test
  public void testWarningOnDeprecatedConstVariable() {
    testDepName(
        "/** @deprecated Another reason */ var f = 4; function g() { alert(f); }",
        "Variable f has been deprecated: Another reason");
  }

  @Test
  public void testWarningOnDeprecatedConstVariableWithConst() {
    testDepName(
        "/** @deprecated Another reason */ const f = 4; function g() { alert(f); }",
        "Variable f has been deprecated: Another reason");
  }

  @Test
  public void testThatNumbersArentDeprecated() {
    testSame("/** @deprecated */ var f = 4; var h = 3; function g() { alert(h); }");
  }

  @Test
  public void testDeprecatedFunctionVariable() {
    testDepName(
        "/** @deprecated I like g... */ var f = function() {}; function g() { f(); }",
        "Variable f has been deprecated: I like g...");
  }

  @Test
  public void testNoWarningInGlobalScope() {
    testSame("var goog = {}; goog.makeSingleton = function(x) {};"
        + "/** @deprecated */ function f() {} goog.makeSingleton(f);");
  }

  @Test
  public void testNoWarningInGlobalScopeForCall() {
    testDepName(
        "/** @deprecated Some global scope */ function f() {} f();",
        "Variable f has been deprecated: Some global scope");
  }

  @Test
  public void testNoWarningInDeprecatedFunction() {
    testSame("/** @deprecated */ function f() {} /** @deprecated */ function g() { f(); }");
  }

  @Test
  public void testNoWarningInDeprecatedMethod() {
    testSame("/** @deprecated */ function f() {} var obj = {/** @deprecated */ g() { f(); }};");
  }

  @Test
  public void testWarningInNormalMethod() {
    testDepName(
        "/** @deprecated Msg */ function f() {} var obj = {g() { f(); }};",
        "Variable f has been deprecated: Msg");
  }

  @Test
  public void testNoWarningInDeprecatedComputedMethod() {
    testSame("/** @deprecated */ function f() {} var obj = {/** @deprecated */ ['g']() { f(); }};");
  }

  @Test
  public void testWarningInNormalComputedMethod() {
    testDepName(
        "/** @deprecated Msg */ function f() {} var obj = {['g']() { f(); }};",
        "Variable f has been deprecated: Msg");
  }

  @Test
  public void testWarningInNormalClass() {
    testDepName(
        "/** @deprecated FooBar */ function f() {}"
            + "/** @constructor */  var Foo = function() {}; "
            + "Foo.prototype.bar = function() { f(); }",
        "Variable f has been deprecated: FooBar");
  }

  @Test
  public void testWarningForProperty1() {
    testDepProp(
        "/** @constructor */ function Foo() {}"
            + "/** @deprecated A property is bad */ Foo.prototype.bar = 3;"
            + "Foo.prototype.baz = function() { alert((new Foo()).bar); };",
        "Property bar of type Foo has been deprecated: A property is bad");
  }

  @Test
  public void testWarningForProperty2() {
    testDepProp(
        "/** @constructor */ function Foo() {}"
            + "/** @deprecated Zee prop, it is deprecated! */ Foo.prototype.bar = 3;"
            + "Foo.prototype.baz = function() { alert(this.bar); };",
        "Property bar of type Foo has been deprecated: Zee prop, it is deprecated!");
  }

  @Test
  public void testWarningForDeprecatedClass() {
    testDepClass(
        "/** @constructor \n* @deprecated Use the class 'Bar' */ function Foo() {} "
            + "function f() { new Foo(); }",
        "Class Foo has been deprecated: Use the class 'Bar'");
  }

  @Test
  public void testWarningForDeprecatedClassNoReason() {
    testError(
        "/** @constructor \n* @deprecated */ function Foo() {} " + "function f() { new Foo(); }",
        DEPRECATED_CLASS);
  }

  @Test
  public void testNoWarningForDeprecatedClassInstance() {
    testSame("/** @constructor \n * @deprecated */ function Foo() {} "
        + "/** @param {Foo} x */ function f(x) { return x; }");
  }

  @Test
  public void testWarningForDeprecatedSuperClass() {
    testDepClass(
        "/** @constructor \n * @deprecated Superclass to the rescue! */ function Foo() {} "
            + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
            + "function f() { new SubFoo(); }",
        "Class SubFoo has been deprecated: Superclass to the rescue!");
  }

  @Test
  public void testWarningForDeprecatedSuperClass2() {
    testDepClass(
        "/** @constructor \n * @deprecated Its only weakness is Kryptoclass */ function Foo() {} "
            + "/** @const */ var namespace = {}; "
            + "/** @constructor \n * @extends {Foo} */ "
            + "namespace.SubFoo = function() {}; "
            + "function f() { new namespace.SubFoo(); }",
        "Class namespace.SubFoo has been deprecated: Its only weakness is Kryptoclass");
  }

  @Test
  public void testWarningForPrototypeProperty() {
    String js =
        "/** @constructor */ function Foo() {}"
        + "/** @deprecated It is now in production, use that model... */ Foo.prototype.bar = 3;"
        + "Foo.prototype.baz = function() { alert(Foo.prototype.bar); };";
    testDepProp(
        js,
        "Property bar of type Foo.prototype has been deprecated:"
            + " It is now in production, use that model...");
  }

  @Test
  public void testNoWarningForNumbers() {
    testSame("/** @constructor */ function Foo() {}"
        + "/** @deprecated */ Foo.prototype.bar = 3;"
        + "Foo.prototype.baz = function() { alert(3); };");
  }

  @Test
  public void testWarningForMethod1() {
    testDepProp(
        "/** @constructor */ function Foo() {}"
            + "/** @deprecated There is a madness to this method */"
            + "Foo.prototype.bar = function() {};"
            + "Foo.prototype.baz = function() { this.bar(); };",
        "Property bar of type Foo has been deprecated: There is a madness to this method");
  }

  @Test
  public void testWarningForMethod2() {
    testDepProp(
        "/** @constructor */ function Foo() {}"
        + "/** @deprecated Stop the ringing! */ Foo.prototype.bar;"
        + "Foo.prototype.baz = function() { this.bar(); };",
        "Property bar of type Foo has been deprecated: Stop the ringing!");
  }

  @Test
  public void testNoWarningInDeprecatedClass() {
    testSame("/** @deprecated */ function f() {} "
        + "/** @constructor \n * @deprecated */ "
        + "var Foo = function() {}; "
        + "Foo.prototype.bar = function() { f(); }");
  }

  @Test
  public void testNoWarningOnDeclaration() {
    testSame("/** @constructor */ function F() {\n"
        + "  /**\n"
        + "   * @type {number}\n"
        + "   * @deprecated Use something else.\n"
        + "   */\n"
        + "  this.code;\n"
        + "}");
  }

  @Test
  public void testNoWarningInDeprecatedClass2() {
    testSame("/** @deprecated */ function f() {} "
        + "/** @constructor \n * @deprecated */ "
        + "var Foo = function() {}; "
        + "Foo.bar = function() { f(); }");
  }

  @Test
  public void testNoWarningInDeprecatedStaticMethod() {
    testSame("/** @deprecated */ function f() {} "
        + "/** @constructor */ "
        + "var Foo = function() {}; "
        + "/** @deprecated */ Foo.bar = function() { f(); }");
  }

  @Test
  public void testWarningInStaticMethod() {
    testDepName(
        "/** @deprecated crazy! */ function f() {} "
            + "/** @constructor */ "
            + "var Foo = function() {}; "
            + "Foo.bar = function() { f(); }",
        "Variable f has been deprecated: crazy!");
  }

  @Test
  public void testDeprecatedObjLitKey() {
    testDepProp(
        "/** @const */ var f = {};"
            + "/** @deprecated It is literally not used anymore */ f.foo = 3;"
            + "function g() { return f.foo; }",
        "Property foo of type f has been deprecated: It is literally not used anymore");
  }

  @Test
  public void testWarningForSubclassMethod() {
    testDepProp(
        "/** @constructor */ function Foo() {}"
            + "Foo.prototype.bar = function() {};"
            + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
            + "/** @deprecated I have a parent class! */ SubFoo.prototype.bar = function() {};"
            + "function f() { (new SubFoo()).bar(); };",
        "Property bar of type SubFoo has been deprecated: I have a parent class!");
  }

  @Test
  public void testWarningForSuperClassWithDeprecatedSubclassMethod() {
    testSame("/** @constructor */ function Foo() {}"
        + "Foo.prototype.bar = function() {};"
        + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
        + "/** @deprecated \n * @override */ SubFoo.prototype.bar = "
        + "function() {};"
        + "function f() { (new Foo()).bar(); };");
  }

  @Test
  public void testWarningForSuperclassMethod() {
    testDepProp(
        "/** @constructor */ function Foo() {}"
            + "/** @deprecated I have a child class! */ Foo.prototype.bar = function() {};"
            + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
            + "SubFoo.prototype.bar = function() {};"
            + "function f() { (new SubFoo()).bar(); };",
        "Property bar of type SubFoo has been deprecated: I have a child class!");
  }

  @Test
  public void testWarningForSuperclassMethod2() {
    testDepProp(
        "/** @constructor */ function Foo() {}"
            + "/** @deprecated I have another child class... \n* @protected */"
            + "Foo.prototype.bar = function() {};"
            + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
            + "/** @protected */SubFoo.prototype.bar = function() {};"
            + "function f() { (new SubFoo()).bar(); };",
        "Property bar of type SubFoo has been deprecated: I have another child class...");
  }

  @Test
  public void testWarningForBind() {
    testDepProp(
        "/** @deprecated I'm bound to this method... */ Function.prototype.bind = function() {};"
            + "(function() {}).bind();",
        "Property bind of type function has been deprecated: I'm bound to this method...");
  }

  @Test
  public void testWarningForDeprecatedClassInGlobalScope() {
    testDepClass(
        "/** @constructor \n * @deprecated I'm a very worldly object! */ var Foo = function() {};"
            + "new Foo();",
        "Class Foo has been deprecated: I'm a very worldly object!");
  }

  @Test
  public void testNoWarningForPrototypeCopying() {
    testSame("/** @constructor */ var Foo = function() {};"
        + "Foo.prototype.bar = function() {};"
        + "/** @deprecated */ Foo.prototype.baz = Foo.prototype.bar;"
        + "(new Foo()).bar();");
  }

  @Test
  public void testNoWarningOnDeprecatedPrototype() {
    // This used to cause an NPE.
    testSame("/** @constructor */ var Foo = function() {};"
        + "/** @deprecated */ Foo.prototype = {};"
        + "Foo.prototype.bar = function() {};");
  }

  @Test
  public void testPrivateAccessForNames() {
    testSame("/** @private */ function foo_() {}; foo_();");
    testError(new String[] {"/** @private */ function foo_() {};", "foo_();"},
        BAD_PRIVATE_GLOBAL_ACCESS);
  }

  @Test
  public void testPrivateAccessForNames2() {
    // Private by convention
    testSame("function foo_() {}; foo_();");
    testError(new String[] {"function foo_() {};", "foo_();"}, BAD_PRIVATE_GLOBAL_ACCESS);
  }

  @Test
  public void testPrivateAccessForProperties1() {
    testSame("/** @constructor */ function Foo() {}"
        + "/** @private */ Foo.prototype.bar_ = function() {};"
        + "Foo.prototype.baz = function() { this.bar_(); }; (new Foo).bar_();");
  }

  @Test
  public void testPrivateAccessForProperties2() {
    testSame(new String[] {
        "/** @constructor */ function Foo() {}",
        "/** @private */ Foo.prototype.bar_ = function() {};"
        + "Foo.prototype.baz = function() { this.bar_(); }; (new Foo).bar_();"});
  }

  @Test
  public void testPrivateAccessForProperties3() {
    // Even though baz is "part of the Foo class" the access is disallowed since it's
    // not in the same file.
    testError(new String[] {
        "/** @constructor */ function Foo() {}"
        + "/** @private */ Foo.prototype.bar_ = function() {}; (new Foo).bar_();",
        "Foo.prototype.baz = function() { this.bar_(); };"},
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testPrivateAccessForProperties4() {
    testSame(
        "/** @constructor */ function Foo() {}"
        + "/** @private */ Foo.prototype.bar_ = function() {};"
        + "Foo.prototype['baz'] = function() { (new Foo()).bar_(); };");
  }

  @Test
  public void testPrivateAccessForProperties5() {
    testError(
        srcs(
            new String[] {
              lines(
                  "/** @constructor */",
                  "function Parent () {",
                  "  /** @private */",
                  "  this.prop = 'foo';",
                  "};"),
              lines(
                  "/**",
                  " * @constructor",
                  " * @extends {Parent}",
                  " */",
                  "function Child() {",
                  "  this.prop = 'asdf';",
                  "}",
                  "Child.prototype = new Parent();")
            }),
        error(BAD_PRIVATE_PROPERTY_ACCESS)
            .withMessage("Access to private property prop of Parent not allowed here."));
  }

  @Test
  public void testPrivatePropAccess_inSameFile_throughDestructuring() {
    test(
        srcs(
            lines(
                "/** @constructor */",
                "function Foo() { }", //
                "",
                "/** @private */",
                "Foo.prototype.bar_ = function() { };",
                "",
                "function f(/** !Foo */ x) {", //
                "  const {bar_: bar} = x;",
                "}")));
  }

  @Test
  public void testPrivateAccessForProperties6() {
    testError(
        srcs(
            new String[] {
              lines(
                  "goog.provide('x.y.z.Parent');",
                  "",
                  "/** @constructor */",
                  "x.y.z.Parent = function() {",
                  "  /** @private */",
                  "  this.prop = 'foo';",
                  "};"),
              lines(
                  "goog.require('x.y.z.Parent');",
                  "",
                  "/**",
                  " * @constructor",
                  " * @extends {x.y.z.Parent}",
                  " */",
                  "function Child() {",
                  "  this.prop = 'asdf';",
                  "}",
                  "Child.prototype = new x.y.z.Parent();")
            }),
        error(BAD_PRIVATE_PROPERTY_ACCESS)
            .withMessage("Access to private property prop of x.y.z.Parent not allowed here."));
  }

  @Test
  public void testPrivateAccess_googModule() {
    String[] js = new String[] {
          lines(
              "goog.module('example.One');",
              "/** @constructor */ function One() {}",
              "/** @private */ One.prototype.m = function() {};",
              "exports = One;"),
          lines(
              "goog.module('example.two');",
              "var One = goog.require('example.One');",
              "(new One()).m();"),
        };

    testError(
        srcs(js),
        error(BAD_PRIVATE_PROPERTY_ACCESS)
            .withMessage("Access to private property m of One not allowed here."));
  }

  @Test
  public void testNoPrivateAccessForProperties1() {
    testError(new String[] {
        "/** @constructor */ function Foo() {} (new Foo).bar_();",
        "/** @private */ Foo.prototype.bar_ = function() {};"
        + "Foo.prototype.baz = function() { this.bar_(); };"},
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPrivateAccessForProperties2() {
    testError(new String[] {
        "/** @constructor */ function Foo() {} "
        + "/** @private */ Foo.prototype.bar_ = function() {};"
        + "Foo.prototype.baz = function() { this.bar_(); };",
        "(new Foo).bar_();"},
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPrivateAccessForProperties3() {
    testError(new String[] {
        "/** @constructor */ function Foo() {} "
        + "/** @private */ Foo.prototype.bar_ = function() {};",
        "/** @constructor */ function OtherFoo() { (new Foo).bar_(); }"},
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPrivateAccessForProperties4() {
    testError(new String[] {
        "/** @constructor */ function Foo() {} "
        + "/** @private */ Foo.prototype.bar_ = function() {};",
        "/** @constructor \n * @extends {Foo} */ "
        + "function SubFoo() { this.bar_(); }"},
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPrivateAccessForProperties5() {
    testError(new String[] {
        "/** @constructor */ function Foo() {} "
        + "/** @private */ Foo.prototype.bar_ = function() {};",
        "/** @constructor \n * @extends {Foo} */ "
        + "function SubFoo() {};"
        + "SubFoo.prototype.baz = function() { this.bar_(); }"},
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPrivateAccessForProperties6() {
    // Overriding a private property with a non-private property
    // in a different file causes problems.
    test(
        srcs(
            lines(
                "/** @constructor */",
                "function Foo() {}",
                "",
                "/** @private */",
                "Foo.prototype.bar_ = function() {};"),
            lines(
                "/**",
                " * @constructor",
                " * @extends {Foo}",
                " */",
                "function SubFoo() {};",
                "",
                "SubFoo.prototype.bar_ = function() {};")),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForProperties6a() {
    // Same as above, except with namespaced constructors
    testError(new String[] {
        "/** @const */ var ns = {};"
        + "/** @constructor */ ns.Foo = function() {}; "
        + "/** @private */ ns.Foo.prototype.bar_ = function() {};",
        "/** @constructor \n * @extends {ns.Foo} */ "
        + "ns.SubFoo = function() {};"
        + "ns.SubFoo.prototype.bar_ = function() {};"},
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPrivateAccessForProperties7() {
    // It's OK to override a private property with a non-private property
    // in the same file, but you'll get yelled at when you try to use it.
    testError(new String[] {
        "/** @constructor */ function Foo() {} "
        + "/** @private */ Foo.prototype.bar_ = function() {};"
        + "/** @constructor \n * @extends {Foo} */ "
        + "function SubFoo() {};"
        + "SubFoo.prototype.bar_ = function() {};",
        "SubFoo.prototype.baz = function() { this.bar_(); }"},
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPrivateAccessForProperties8() {
    testError(new String[] {
        "/** @constructor */ function Foo() { /** @private */ this.bar_ = 3; }",
        "/** @constructor \n * @extends {Foo} */ "
        + "function SubFoo() { /** @private */ this.bar_ = 3; };"},
        PRIVATE_OVERRIDE);
  }

  @Test
  public void testNoPrivateAccessForProperties9() {
    testError(new String[] {
        "/** @constructor */ function Foo() {}"
        + "Foo.prototype = {"
        + "/** @private */ bar_: 3"
        + "}",
        "new Foo().bar_;"},
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPrivateAccessForProperties10() {
    testError(new String[] {
        "/** @constructor */ function Foo() {}"
        + "Foo.prototype = {"
        + "/** @private */ bar_: function() {}"
        + "}",
        "new Foo().bar_();"},
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPrivateAccessForProperties11() {
    testError(new String[] {
        "/** @constructor */ function Foo() {}"
        + "Foo.prototype = {"
        + "/** @private */ get bar_() { return 1; }"
        + "}",
        "var a = new Foo().bar_;"},
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPrivateAccessForProperties12() {
    testError(new String[] {
        "/** @constructor */ function Foo() {}"
        + "Foo.prototype = {"
        + "/** @private */ set bar_(x) { this.barValue = x; }"
        + "}",
        "new Foo().bar_ = 1;"},
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPrivatePropAccess_inDifferentFile_throughDestructuring() {
    test(
        srcs(
            lines(
                "/** @constructor */",
                "function Foo() { }", //
                "",
                "/** @private */",
                "Foo.prototype.bar_ = function() { };"),
            lines(
                "function f(/** !Foo */ x) {", //
                "  const {bar_: bar} = x;",
                "}")),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPrivateAccessForNamespaces() {
    testError(new String[] {
        "/** @const */ var foo = {};\n"
        + "/** @private */ foo.bar_ = function() {};",
        "foo.bar_();"},
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testProtectedAccessForProperties1() {
    testSame(new String[] {
        "/** @constructor */ function Foo() {}"
        + "/** @protected */ Foo.prototype.bar = function() {};"
        + "(new Foo).bar();",
        "Foo.prototype.baz = function() { this.bar(); };"});
  }

  @Test
  public void testProtectedAccessForProperties2() {
    testSame(new String[] {
        "/** @constructor */ function Foo() {}"
        + "/** @protected */ Foo.prototype.bar = function() {};"
        + "(new Foo).bar();",
        "/** @constructor \n * @extends {Foo} */"
        + "function SubFoo() { this.bar(); }"});
  }

  @Test
  public void testProtectedAccessForProperties3() {
    testSame(new String[] {
        "/** @constructor */ function Foo() {}"
        + "/** @protected */ Foo.prototype.bar = function() {};"
        + "(new Foo).bar();",
        "/** @constructor \n * @extends {Foo} */"
        + "function SubFoo() { }"
        + "SubFoo.baz = function() { (new Foo).bar(); }"});
  }

  @Test
  public void testProtectedAccessForProperties4() {
    testSame(new String[] {
        "/** @constructor */ function Foo() {}"
        + "/** @protected */ Foo.bar = function() {};",
        "/** @constructor \n * @extends {Foo} */"
        + "function SubFoo() { Foo.bar(); }"});
  }

  @Test
  public void testProtectedAccessForProperties5() {
    testSame(new String[] {
        "/** @constructor */ function Foo() {}"
        + "/** @protected */ Foo.prototype.bar = function() {};"
        + "(new Foo).bar();",
        "/** @constructor \n * @extends {Foo} */"
        + "var SubFoo = function() { this.bar(); }"});
  }

  @Test
  public void testProtectedAccessForProperties6() {
    testSame(new String[] {
        "/** @const */ var goog = {};"
        + "/** @constructor */ goog.Foo = function() {};"
        + "/** @protected */ goog.Foo.prototype.bar = function() {};",
        "/** @constructor \n * @extends {goog.Foo} */"
        + "goog.SubFoo = function() { this.bar(); };"});
  }

  @Test
  public void testProtectedAccessForProperties7() {
    testSame(new String[] {
        "/** @constructor */ var Foo = function() {};"
        + "Foo.prototype = { /** @protected */ bar: function() {} }",
        "/** @constructor \n * @extends {Foo} */"
        + "var SubFoo = function() { this.bar(); };"
        + "SubFoo.prototype = { moo: function() { this.bar(); }};"});
  }

  @Test
  public void testProtectedAccessForProperties8() {
    testSame(new String[] {
        "/** @constructor */ var Foo = function() {};"
        + "Foo.prototype = { /** @protected */ bar: function() {} }",
        "/** @constructor \n * @extends {Foo} */"
        + "var SubFoo = function() {};"
        + "SubFoo.prototype = { get moo() { this.bar(); }};"});
  }

  @Test
  public void testProtectedAccessForProperties9() {
    testSame(new String[] {
        "/** @constructor */ var Foo = function() {};"
        + "Foo.prototype = { /** @protected */ bar: function() {} }",
        "/** @constructor \n * @extends {Foo} */"
        + "var SubFoo = function() {};"
        + "SubFoo.prototype = { set moo(val) { this.x = this.bar(); }};"});
  }

  @Test
  public void testProtectedAccessForProperties10() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            "foo.js",
            "/** @constructor */ var Foo = function() {};"
            + "/** @protected */ Foo.prototype.bar = function() {};"),
        SourceFile.fromCode(
            "sub_foo.js",
            "/** @constructor @extends {Foo} */"
            + "var SubFoo = function() {};"
            + "(function() {"
            + "SubFoo.prototype.baz = function() { this.bar(); }"
            + "})();")));
  }

  @Test
  public void testProtectedAccessForProperties11() {
    testNoWarning(
        ImmutableList.of(
            SourceFile.fromCode(
                "foo.js",
                lines(
                    "goog.provide('Foo');",
                    "/** @interface */ Foo = function() {};",
                    "/** @protected */ Foo.prop = {};")),
            SourceFile.fromCode(
                "bar.js",
                lines(
                    "goog.require('Foo');",
                    "/** @constructor @implements {Foo} */",
                    "function Bar() { Foo.prop; };"))));
}

  @Test
  public void testProtectedAccessForProperties12() {
    testNoWarning(
        ImmutableList.of(
            SourceFile.fromCode(
                "a.js",
                lines(
                    "goog.provide('A');",
                    "/** @constructor */",
                    "var A = function() {",
                    "  /**",
                    "   * @type {?String}",
                    "   * @protected",
                    "   */",
                    "  this.prop;",
                    "}")),
            SourceFile.fromCode(
                "b.js",
                lines(
                    "goog.require('A');",
                    "/**",
                    " * @constructor",
                    " * @extends {A}",
                    " */",
                    "var B = function() {",
                    "  this.prop.length;",
                    "  this.prop.length;",
                    "};"))));
  }

  // FYI: Java warns for the b1.method access in c.js.
  // Instead of following that in NTI, we chose to follow the behavior of
  // the old JSCompiler type checker, to make migration easier.
  @Test
  public void testProtectedAccessForProperties13() {
    testNoWarning(
        ImmutableList.of(
            SourceFile.fromCode(
                "a.js",
                lines(
                    "goog.provide('A');",
                    "",
                    "/** @constructor */",
                    "var A = function() {}",
                    "",
                    "/** @protected */",
                    "A.prototype.method = function() {};")),
            SourceFile.fromCode(
                "b1.js",
                lines(
                    "goog.require('A');",
                    "goog.provide('B1');",
                    "",
                    "/**",
                    " * @constructor",
                    " * @extends {A}",
                    " */",
                    "var B1 = function() {};",
                    "",
                    "/** @override */",
                    "B1.prototype.method = function() {};")),
            SourceFile.fromCode(
                "b2.js",
                lines(
                    "goog.require('A');",
                    "goog.provide('B2');",
                    "",
                    "/**",
                    " * @constructor",
                    " * @extends {A}",
                    " */",
                    "var B2 = function() {};",
                    "",
                    "/** @override */",
                    "B2.prototype.method = function() {};")),
            SourceFile.fromCode(
                "c.js",
                lines(
                    "goog.require('B1');",
                    "goog.require('B2');",
                    "",
                    "/**",
                    " * @param {!B1} b1",
                    " * @constructor",
                    " * @extends {B2}",
                    " */",
                    "var C = function(b1) {",
                    "  var x = b1.method();",
                    "};"))));
  }

  @Test
  public void testProtectedAccessForProperties14() {
    // access in member function
    testNoWarning(
        new String[] {
          lines(
              "/** @constructor */ var Foo = function() {};",
              "Foo.prototype = { /** @protected */ bar: function() {} }"),
          lines(
              "/** @constructor @extends {Foo} */",
              "var OtherFoo = function() { this.bar(); };",
              "OtherFoo.prototype = { moo() { new Foo().bar(); }};")
        });
  }

  @Test
  public void testProtectedAccessForProperties15() {
    // access in computed member function
    testNoWarning(
        new String[] {
          lines(
              "/** @constructor */ var Foo = function() {};",
              "Foo.prototype = { /** @protected */ bar: function() {} }"),
          lines(
              "/** @constructor @extends {Foo} */",
              "var OtherFoo = function() { this['bar'](); };",
              "OtherFoo.prototype = { ['bar']() { new Foo().bar(); }};")
        });
  }

  @Test
  public void testProtectedAccessForProperties16() {
    // access in nested arrow function
    testNoWarning(
        new String[] {
          lines(
              "/** @constructor */ var Foo = function() {};",
              "/** @protected */ Foo.prototype.bar = function() {};"),
          lines(
              "/** @constructor @extends {Foo} */",
              "var OtherFoo = function() { var f = () => this.bar(); };",
              "OtherFoo.prototype.baz = function() { return () => this.bar(); };")
        });
  }

  @Test
  public void testProtectedPropAccess_inDifferentFile_inSubclass_throughDestructuring() {
    test(
        srcs(
            lines(
                "/** @constructor */",
                "function Foo() { }", //
                "",
                "/** @protected */",
                "Foo.prototype.bar = function() { }"),
            lines(
                "/** @constructor @extends {Foo} */",
                "function SubFoo() { }", //
                "",
                "SubFoo.prototype.method = function(/** !Foo */ x) {",
                "  const {bar: bar} = x;",
                "};")));
  }

  @Test
  public void testNoProtectedAccess_forOverriddenProperty_elsewhereInSubclassFile() {
    test(
        srcs(
            lines(
                "/** @constructor */", //
                "function Foo() { }",
                "",
                "/** @protected */",
                "Foo.prototype.bar = function() { };"),
            lines(
                "/**",
                " * @constructor",
                " * @extends {Foo}",
                " */",
                "function Bar() { }",
                "",
                "/** @override */",
                "Bar.prototype.bar = function() { };",
                "",
                // TODO(b/113705099): This should be legal.
                "(new Bar()).bar();" // But `Foo::bar` is still invisible.
                )),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testProtectedAccessThroughNestedFunction() {
    test(
        srcs(
            lines(
                "/** @constructor */", //
                "function Foo() { }",
                "",
                "/** @protected */",
                "Foo.prototype.bar = function() { };"),
            lines(
                "/**",
                " * @constructor",
                " * @extends {Foo}",
                " */",
                "function Bar() {",
                "  function f(/** !Foo */ foo) {",
                "    foo.bar();",
                "  }",
                "}")));
  }

  @Test
  public void testProtectedAccessThroughNestedEs5Class() {
    test(
        srcs(
            lines(
                "/** @constructor */", //
                "function Foo() { }",
                "",
                "/** @protected */",
                "Foo.prototype.bar = function() { };"),
            lines(
                "/**",
                " * @constructor",
                " * @extends {Foo}",
                " */",
                "function Bar() {",
                "  /** @constructor */",
                "  var Nested = function() { }",
                "",
                "  /** @param {!Foo} foo */",
                "  Nested.prototype.qux = function(foo) {",
                "    foo.bar();",
                "  }",
                "}")));
  }

  @Test
  public void testProtectedAccessThroughNestedEs6Class() {
    test(
        srcs(
            lines(
                "/** @constructor */", //
                "function Foo() { }",
                "",
                "/** @protected */",
                "Foo.prototype.bar = function() { };"),
            lines(
                "/**",
                " * @constructor",
                " * @extends {Foo}",
                " */",
                "function Bar() {",
                "  class Nested {",
                "    /** @param {!Foo} foo */",
                "    qux(foo) {",
                "      foo.bar();",
                "    }",
                "  }",
                "}")));
  }

  @Test
  public void testNoProtectedAccessForProperties1() {
    testError(new String[] {
        "/** @constructor */ function Foo() {} "
        + "/** @protected */ Foo.prototype.bar = function() {};",
        "(new Foo).bar();"},
        BAD_PROTECTED_PROPERTY_ACCESS);
  }

  @Test
  public void testNoProtectedAccessForProperties2() {
    testError(new String[] {
        "/** @constructor */ function Foo() {} "
        + "/** @protected */ Foo.prototype.bar = function() {};",
        "/** @constructor */ function OtherFoo() { (new Foo).bar(); }"},
        BAD_PROTECTED_PROPERTY_ACCESS);
  }

  @Test
  public void testNoProtectedAccessForProperties3() {
    testError(new String[] {
        "/** @constructor */ function Foo() {} "
        + "/** @constructor \n * @extends {Foo} */ "
        + "function SubFoo() {}"
        + "/** @protected */ SubFoo.prototype.bar = function() {};",
        "/** @constructor \n * @extends {Foo} */ "
        + "function SubberFoo() { (new SubFoo).bar(); }"},
        BAD_PROTECTED_PROPERTY_ACCESS);
  }

  @Test
  public void testNoProtectedAccessForProperties4() {
    testError(new String[] {
        "/** @constructor */ function Foo() { (new SubFoo).bar(); } ",
        "/** @constructor \n * @extends {Foo} */ "
        + "function SubFoo() {}"
        + "/** @protected */ SubFoo.prototype.bar = function() {};",
         },
        BAD_PROTECTED_PROPERTY_ACCESS);
  }

  @Test
  public void testNoProtectedAccessForProperties5() {
    testError(new String[] {
        "/** @const */ var goog = {};"
        + "/** @constructor */ goog.Foo = function() {};"
        + "/** @protected */ goog.Foo.prototype.bar = function() {};",
        "/** @constructor */"
        + "goog.NotASubFoo = function() { (new goog.Foo).bar(); };"},
        BAD_PROTECTED_PROPERTY_ACCESS);
  }

  @Test
  public void testNoProtectedAccessForProperties6() {
    testError(new String[] {
        "/** @constructor */ function Foo() {}"
        + "Foo.prototype = {"
        + "/** @protected */ bar: 3"
        + "}",
        "new Foo().bar;"},
        BAD_PROTECTED_PROPERTY_ACCESS);
  }

  @Test
  public void testNoProtectedAccessForProperties7() {
    testError(new String[] {
        "/** @constructor */ function Foo() {}"
        + "Foo.prototype = {"
        + "/** @protected */ bar: function() {}"
        + "}",
        "new Foo().bar();"},
        BAD_PROTECTED_PROPERTY_ACCESS);
  }

  @Test
  public void testNoProtectedAccessForProperties8() {
    testError(
        new String[] {
          lines(
              "/** @constructor */ var Foo = function() {};",
              "Foo.prototype = { /** @protected */ bar: function() {} }"),
          lines(
              "/** @constructor */",
              "var OtherFoo = function() { this.bar(); };",
              "OtherFoo.prototype = { moo() { new Foo().bar(); }};")
        },
        BAD_PROTECTED_PROPERTY_ACCESS);
  }

  @Test
  public void testNoProtectedAccessForProperties9() {
    testError(
        new String[] {
          lines(
              "/** @constructor */ var Foo = function() {};",
              "Foo.prototype = { /** @protected */ bar: function() {} }"),
          lines(
              "/** @constructor */",
              "var OtherFoo = function() { this['bar'](); };",
              "OtherFoo.prototype = { ['bar']() { new Foo().bar(); }};")
        },
        BAD_PROTECTED_PROPERTY_ACCESS);
  }

  @Test
  public void testNoProtectedPropAccess_inDifferentFile_throughDestructuring() {
    test(
        srcs(
            lines(
                "/** @constructor */", //
                "function Foo() { }", //
                "",
                "/** @protected */",
                "Foo.prototype.bar = function() { }"),
            lines(
                "function f(/** !Foo */ x) {", //
                "  const {bar: bar} = x;",
                "}")),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccess_forInheritedProperty_elsewhereInSubclassFile() {
    test(
        srcs(
            lines(
                "/** @constructor */", //
                "function Foo() { }",
                "",
                "/** @protected */",
                "Foo.prototype.bar = function() { };"),
            lines(
                "/**",
                " * @constructor",
                " * @extends {Foo}",
                " */",
                "function Bar() { }",
                "",
                "(new Bar()).bar();" // But `Foo::bar` is still invisible.
                )),
        error(BAD_PROTECTED_PROPERTY_ACCESS));
  }

  @Test
  public void testNoProtectedAccessForPropertiesWithNoRhs() {
    testSame(new String[] {
        lines(
            "/** @constructor */ function Foo() {}",
            "/** @protected */ Foo.prototype.x;"),
        lines(
            "/** @constructor @extends {Foo} */ function Bar() {}",
            "/** @protected */ Bar.prototype.x;")
    });
  }

  @Test
  public void testPackagePrivateAccessForNames() {
    testError(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/** @package */", //
                    "var name = 'foo';")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"), //
                lines("name;"))),
        BAD_PACKAGE_PROPERTY_ACCESS);
  }

  @Test
  public void testPackagePrivateAccessForProperties1() {
    testSame("/** @constructor */ function Foo() {}"
        + "/** @package */ Foo.prototype.bar = function() {};"
        + "Foo.prototype.baz = function() { this.bar(); }; (new Foo).bar();");
  }

  @Test
  public void testPackagePrivateAccessForProperties2() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(Compiler.joinPathParts("foo", "bar.js"),
            "/** @constructor */ function Foo() {}"),
        SourceFile.fromCode(
            Compiler.joinPathParts("baz", "quux.js"),
            "/** @package */ Foo.prototype.bar = function() {};"
            + "Foo.prototype.baz = function() { this.bar(); }; (new Foo).bar();")));
  }

  @Test
  public void testPackagePrivateAccessForProperties3() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            Compiler.joinPathParts("foo", "bar.js"),
            "/** @constructor */ function Foo() {}"
            + "/** @package */ Foo.prototype.bar = function() {}; (new Foo).bar();"),
        SourceFile.fromCode(Compiler.joinPathParts("foo", "baz.js"),
            "Foo.prototype.baz = function() { this.bar(); };")));
  }

  @Test
  public void testPackagePrivateAccessForProperties4() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            Compiler.joinPathParts("foo", "bar.js"),
            "/** @constructor */ function Foo() {}"
            + "/** @package */ Foo.prototype.bar = function() {};"),
        SourceFile.fromCode(
            Compiler.joinPathParts("foo", "baz.js"),
            "Foo.prototype['baz'] = function() { (new Foo()).bar(); };")));
  }

  @Test
  public void testPackagePrivateAccessForProperties5() {
    testError(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @constructor */\n"
                + "function Parent () {\n"
                + "  /** @package */\n"
                + "  this.prop = 'foo';\n"
                + "};"),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "/**\n"
                + " * @constructor\n"
                + " * @extends {Parent}\n"
                + " */\n"
                + "function Child() {\n"
                + "  this.prop = 'asdf';\n"
                + "}\n"
                + "Child.prototype = new Parent();")),
        BAD_PACKAGE_PROPERTY_ACCESS);
  }

  @Test
  public void testPackagePropAccess_inSamePackage_throughDestructuring() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/** @constructor */", //
                    "function Foo() { }",
                    "",
                    "/** @package */",
                    "Foo.prototype.bar = function() { };")),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "quux.js"),
                lines(
                    "function f(/** !Foo */ x) {", //
                    "  const {bar: bar} = x;",
                    "}"))));
  }

  @Test
  public void testNoPackagePropAccess_inDifferentPackage_throughDestructuring() {
    test(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/** @constructor */", //
                    "function Foo() { }",
                    "",
                    "/** @package */",
                    "Foo.prototype.bar = function() { };")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                lines(
                    "function f(/** !Foo */ x) {", //
                    "  const {bar: bar} = x;",
                    "}"))),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackagePrivateAccessForProperties1() {
    testError(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @constructor */ function Foo() {} (new Foo).bar();"),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "/** @package */ Foo.prototype.bar = function() {};"
                + "Foo.prototype.baz = function() { this.bar(); };")),
        BAD_PACKAGE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPackagePrivateAccessForProperties2() {
    testError(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @constructor */ function Foo() {} "
                + "/** @package */ Foo.prototype.bar = function() {};"
                + "Foo.prototype.baz = function() { this.bar(); };"),
            SourceFile.fromCode(Compiler.joinPathParts("baz", "quux.js"), "(new Foo).bar();")),
        BAD_PACKAGE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPackagePrivateAccessForProperties3() {
    testError(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @constructor */ function Foo() {} "
                + "/** @package */ Foo.prototype.bar = function() {};"),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "/** @constructor */ function OtherFoo() { (new Foo).bar(); }")),
        BAD_PACKAGE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPackagePrivateAccessForProperties4() {
    testError(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @constructor */ function Foo() {} "
                + "/** @package */ Foo.prototype.bar = function() {};"),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "/** @constructor \n * @extends {Foo} */ "
                + "function SubFoo() { this.bar(); }")),
        BAD_PACKAGE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPackagePrivateAccessForNamespaces() {
    testError(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @const */ var foo = {};\n"
                + "/** @package */ foo.bar = function() {};"),
            SourceFile.fromCode(Compiler.joinPathParts("baz", "quux.js"), "foo.bar();")),
        BAD_PACKAGE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPackagePrivateAccessForProperties5() {
    testError(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @constructor */ function Foo() {} "
                + "/** @package */ Foo.prototype.bar = function() {};"),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "/** @constructor \n * @extends {Foo} */ "
                + "function SubFoo() {};"
                + "SubFoo.prototype.baz = function() { this.bar(); }")),
        BAD_PACKAGE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPackagePrivateAccessForProperties6() {
    // Overriding a private property with a non-package-private property
    // in a different file causes problems.
    testError(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @constructor */ function Foo() {} "
                + "/** @package */ Foo.prototype.bar = function() {};"),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "/** @constructor \n * @extends {Foo} */ "
                + "function SubFoo() {};"
                + "SubFoo.prototype.bar = function() {};")),
        BAD_PACKAGE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPackagePrivateAccessForProperties7() {
    // It's OK to override a package-private property with a
    // non-package-private property in the same file, but you'll get
    // yelled at when you try to use it.
    testError(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @constructor */ function Foo() {} "
                + "/** @package */ Foo.prototype.bar = function() {};"
                + "/** @constructor \n * @extends {Foo} */ "
                + "function SubFoo() {};"
                + "SubFoo.prototype.bar = function() {};"),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "SubFoo.prototype.baz = function() { this.bar(); }")),
        BAD_PACKAGE_PROPERTY_ACCESS);
  }

  @Test
  public void
      testOverrideWithoutVisibilityRedeclInFileWithFileOverviewVisibilityNotAllowed_OneFile() {
    testError(
        "/**\n"
        + "* @fileoverview\n"
        + "* @package\n"
        + "*/\n"
        + "/** @struct @constructor */\n"
        + "Foo = function() {};\n"
        + "/** @private */\n"
        + "Foo.prototype.privateMethod_ = function() {};\n"
        + "/** @struct @constructor @extends {Foo} */\n"
        + "Bar = function() {};\n"
        + "/** @override */\n"
        + "Bar.prototype.privateMethod_ = function() {};\n",
        BAD_PROPERTY_OVERRIDE_IN_FILE_WITH_FILEOVERVIEW_VISIBILITY);
  }

  @Test
  public void testNamespacedFunctionDoesNotNeedVisibilityRedeclInFileWithFileOverviewVisibility() {
    testSame("/**\n"
        + " * @fileoverview\n"
        + " * @package\n"
        + " */\n"
        + "/** @return {string} */\n"
        + "foo.bar = function() { return 'asdf'; };");
  }

  @Test
  public void
      testOverrideWithoutVisibilityRedeclInFileWithFileOverviewVisibilityNotAllowed_TwoFiles() {
    testError(new String[] {
        "/** @struct @constructor */\n"
        + "Foo = function() {};\n"
        + "/** @protected */\n"
        + "Foo.prototype.protectedMethod = function() {};\n",
        "  /**\n"
        + "* @fileoverview \n"
        + "* @package\n"
        + "*/\n"
        + "/** @struct @constructor @extends {Foo} */\n"
        + "Bar = function() {};\n"
        + "/** @override */\n"
        + "Bar.prototype.protectedMethod = function() {};\n"},
        BAD_PROPERTY_OVERRIDE_IN_FILE_WITH_FILEOVERVIEW_VISIBILITY);
  }

  @Test
  public void testOverrideWithoutVisibilityRedeclInFileWithNoFileOverviewOk() {
    testSame("/** @struct @constructor */\n"
        + "Foo = function() {};\n"
        + "/** @private */\n"
        + "Foo.prototype.privateMethod_ = function() {};\n"
        + "/** @struct @constructor @extends {Foo} */\n"
        + "Bar = function() {};\n"
        + "/** @override */\n"
        + "Bar.prototype.privateMethod_ = function() {};\n");
  }

  @Test
  public void testOverrideWithoutVisibilityRedeclInFileWithNoFileOverviewVisibilityOk() {
    testSame("/**\n"
        + "  * @fileoverview\n"
        + "  */\n"
        + "/** @struct @constructor */\n"
        + "Foo = function() {};\n"
        + "/** @private */\n"
        + "Foo.prototype.privateMethod_ = function() {};\n"
        + "/** @struct @constructor @extends {Foo} */\n"
        + "Bar = function() {};\n"
        + "/** @override */\n"
        + "Bar.prototype.privateMethod_ = function() {};\n");
  }

  @Test
  public void testOverrideWithVisibilityRedeclInFileWithFileOverviewVisibilityOk_OneFile() {
    testSame("/**\n"
        + "  * @fileoverview\n"
        + "  * @package\n"
        + "  */\n"
        + "/** @struct @constructor */\n"
        + "Foo = function() {};\n"
        + "/** @private */\n"
        + "Foo.prototype.privateMethod_ = function() {};\n"
        + "/** @struct @constructor @extends {Foo} */\n"
        + "Bar = function() {};\n"
        + "/** @override @private */\n"
        + "Bar.prototype.privateMethod_ = function() {};\n");
  }

  @Test
  public void testOverrideWithVisibilityRedeclInFileWithFileOverviewVisibilityOk_TwoFiles() {
    testSame(new String[] {
        "/** @struct @constructor */\n"
        + "Foo = function() {};\n"
        + "/** @protected */\n"
        + "Foo.prototype.protectedMethod = function() {};\n",
        "  /**\n"
        + "* @fileoverview\n"
        + "* @package\n"
        + "*/\n"
        + "/** @struct @constructor @extends {Foo} */\n"
        + "Bar = function() {};\n"
        + "/** @override @protected */\n"
        + "Bar.prototype.protectedMethod = function() {};\n"});
  }

  @Test
  public void testPublicFileOverviewVisibilityDoesNotApplyToNameWithExplicitPackageVisibility() {
    testError(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/**\n"
                + " * @fileoverview\n"
                + " * @public\n"
                + " */\n"
                + "/** @constructor @package */ function Foo() {};"),
            SourceFile.fromCode(Compiler.joinPathParts("baz", "quux.js"), "new Foo();")),
        BAD_PACKAGE_PROPERTY_ACCESS);
  }

  @Test
  public void testPackageFileOverviewVisibilityDoesNotApplyToNameWithExplicitPublicVisibility() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            Compiler.joinPathParts("foo", "bar.js"),
            "/**\n"
            + " * @fileoverview\n"
            + " * @package\n"
            + " */\n"
            + "/** @constructor @public */ function Foo() {};"),
        SourceFile.fromCode(Compiler.joinPathParts("baz", "quux.js"), "new Foo();")));
  }

  @Test
  public void testPackageFileOverviewVisibilityAppliesToNameWithoutExplicitVisibility() {
    testError(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/**\n"
                + " * @fileoverview\n"
                + " * @package\n"
                + " */\n"
                + "/** @constructor */\n"
                + "var Foo = function() {};\n"),
            SourceFile.fromCode(Compiler.joinPathParts("baz", "quux.js"), "new Foo();")),
        BAD_PACKAGE_PROPERTY_ACCESS);
  }

  @Test
  public void
      testPackageFileOverviewVisibilityDoesNotApplyToPropertyWithExplicitPublicVisibility() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            Compiler.joinPathParts("foo", "bar.js"),
            "/**\n"
            + " * @fileoverview\n"
            + " * @package\n"
            + " */\n"
            + "/** @constructor */\n"
            + "Foo = function() {};\n"
            + "/** @public */\n"
            + "Foo.prototype.bar = function() {};\n"),
        SourceFile.fromCode(
            Compiler.joinPathParts("baz", "quux.js"),
            "var foo = new Foo();\n"
            + "foo.bar();")));
  }

  @Test
  public void testFileoverviewVisibilityDoesNotApplyToGoogProvidedNamespace1() {
    test(
        ImmutableList.of(
            SourceFile.fromCode("foo.js", "goog.provide('foo');"),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/**\n",
                    "  * @fileoverview\n",
                    "  * @package\n",
                    "  */\n",
                    "goog.provide('foo.bar');")),
            SourceFile.fromCode("bar.js", "goog.require('foo')")),
        ImmutableList.of(
            SourceFile.fromCode("foo.js", "/** @const */ var foo={};"),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/**\n",
                    "  * @fileoverview\n",
                    "  * @package\n",
                    "  */\n",
                    "/** @const */ foo.bar={};")),
            SourceFile.fromCode("bar.js", "")));
  }

  @Test
  public void testFileoverviewVisibilityDoesNotApplyToGoogProvidedNamespace2() {
    test(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @package",
                    " */",
                    "goog.provide('foo.bar');")),
            SourceFile.fromCode("foo.js", "goog.provide('foo');"),
            SourceFile.fromCode(
                "bar.js",
                lines(
                    "goog.require('foo');",
                    "var x = foo;"))),
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/** @const */var foo={};",
                    "/**",
                    " * @fileoverview",
                    " * @package",
                    " */",
                    "/** @const */foo.bar={};")),
            SourceFile.fromCode("foo.js", ""),
            SourceFile.fromCode("bar.js", "var x=foo")));
  }

  @Test
  public void testFileoverviewVisibilityDoesNotApplyToGoogProvidedNamespace3() {
    test(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @package",
                    " */",
                    "goog.provide('one.two');",
                    "one.two.three = function(){};")),
            SourceFile.fromCode(
                "baz.js",
                lines(
                    "goog.require('one.two');",
                    "var x = one.two;"))),
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/** @const */ var one={};",
                    "/**",
                    " * @fileoverview",
                    " * @package",
                    " */",
                    "/** @const */ one.two={};",
                    "one.two.three=function(){};")),
            SourceFile.fromCode("baz.js", "var x=one.two")));
  }

  @Test
  public void testFileoverviewVisibilityDoesNotApplyToGoogProvidedNamespace4() {
    testError(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/**\n"
                + " * @fileoverview\n"
                + " * @package\n"
                + " */\n"
                + "goog.provide('one.two');\n"
                + "one.two.three = function(){};"),
            SourceFile.fromCode(
                "baz.js",
                "goog.require('one.two');\n"
                + "var x = one.two.three();")),
        BAD_PACKAGE_PROPERTY_ACCESS);
  }

  @Test
  public void
      testPublicFileOverviewVisibilityDoesNotApplyToPropertyWithExplicitPackageVisibility() {
    testError(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/**\n"
                + " * @fileoverview\n"
                + " * @public\n"
                + " */\n"
                + "/** @constructor */\n"
                + "Foo = function() {};\n"
                + "/** @package */\n"
                + "Foo.prototype.bar = function() {};\n"),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "var foo = new Foo();\n"
                + "foo.bar();")),
        BAD_PACKAGE_PROPERTY_ACCESS);
  }

  @Test
  public void testPublicFileOverviewVisibilityAppliesToPropertyWithoutExplicitVisibility() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            Compiler.joinPathParts("foo", "bar.js"),
            "/**\n"
            + " * @fileoverview\n"
            + " * @public\n"
            + " */\n"
            + "/** @constructor */\n"
            + "Foo = function() {};\n"
            + "Foo.prototype.bar = function() {};\n"),
        SourceFile.fromCode(
            Compiler.joinPathParts("baz", "quux.js"),
            "var foo = new Foo();\n"
            + "foo.bar();")));
  }

  @Test
  public void testPackageFileOverviewVisibilityAppliesToPropertyWithoutExplicitVisibility() {
    testError(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/**\n"
                + " * @fileoverview\n"
                + " * @package\n"
                + " */\n"
                + "/** @constructor */\n"
                + "Foo = function() {};\n"
                + "Foo.prototype.bar = function() {};\n"),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "var foo = new Foo();\n"
                + "foo.bar();")),
        BAD_PACKAGE_PROPERTY_ACCESS);
  }

  @Test
  public void testFileOverviewVisibilityComesFromDeclarationFileNotUseFile() {
    testError(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/**\n"
                + " * @fileoverview\n"
                + " * @package\n"
                + " */\n"
                + "/** @constructor */\n"
                + "Foo = function() {};\n"
                + "Foo.prototype.bar = function() {};\n"),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "/**\n"
                + " * @fileoverview\n"
                + " * @public\n"
                + " */\n"
                + "var foo = new Foo();\n"
                + "foo.bar();")),
        BAD_PACKAGE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoExceptionsWithBadConstructors1() {
    testSame(new String[] {"function Foo() { (new SubFoo).bar(); } "
        + "/** @constructor */ function SubFoo() {}"
        + "/** @protected */ SubFoo.prototype.bar = function() {};"});
  }

  @Test
  public void testNoExceptionsWithBadConstructors2() {
    testSame(new String[] {"/** @constructor */ function Foo() {} "
        + "Foo.prototype.bar = function() {};"
        + "/** @constructor */"
        + "function SubFoo() {}"
        + "/** @protected */ "
        + "SubFoo.prototype.bar = function() { (new Foo).bar(); };"});
  }

  @Test
  public void testGoodOverrideOfProtectedProperty() {
    testSame(new String[] {
        "/** @constructor */ function Foo() { } "
        + "/** @protected */ Foo.prototype.bar = function() {};",
        "/** @constructor \n * @extends {Foo} */ "
        + "function SubFoo() {}"
        + "/** @inheritDoc */ SubFoo.prototype.bar = function() {};",
    });
  }

  @Test
  public void testBadOverrideOfProtectedProperty() {
    testError(new String[] {
        "/** @constructor */ function Foo() { } "
        + "/** @protected */ Foo.prototype.bar = function() {};",
        "/** @constructor \n * @extends {Foo} */ "
        + "function SubFoo() {}"
        + "/** @private */ SubFoo.prototype.bar = function() {};",
         },
        VISIBILITY_MISMATCH);
  }

  @Test
  public void testBadOverrideOfPrivateProperty() {
    testError(new String[] {
        "/** @constructor */ function Foo() { } "
        + "/** @private */ Foo.prototype.bar = function() {};",
        "/** @constructor \n * @extends {Foo} */ "
        + "function SubFoo() {}"
        + "/** @protected */ SubFoo.prototype.bar = function() {};",
         },
        PRIVATE_OVERRIDE);
  }

  @Test
  public void testAccessOfStaticMethodOnPrivateConstructor() {
    testSame(new String[] {
        "/** @constructor \n * @private */ function Foo() { } "
        + "Foo.create = function() { return new Foo(); };",
        "Foo.create()",
    });
  }

  @Test
  public void testAccessOfStaticMethodOnPrivateQualifiedConstructor() {
    testSame(new String[] {
        "/** @const */ var goog = {};"
        + "/** @constructor \n * @private */ goog.Foo = function() { }; "
        + "goog.Foo.create = function() { return new goog.Foo(); };",
        "goog.Foo.create()",
    });
  }

  @Test
  public void testInstanceofOfPrivateConstructor() {
    testSame(new String[] {
        "/** @const */ var goog = {};"
        + "/** @constructor \n * @private */ goog.Foo = function() { }; "
        + "goog.Foo.create = function() { return new goog.Foo(); };",
        "goog instanceof goog.Foo",
    });
  }

  @Test
  public void testOkAssignmentOfDeprecatedProperty() {
    testSame("/** @constructor */ function Foo() {"
        + " /** @deprecated */ this.bar = 3;"
        + "}");
  }

  @Test
  public void testBadReadOfDeprecatedProperty() {
    testDepProp(
        "/** @constructor */ function Foo() {"
        + " /** @deprecated GRR */ this.bar = 3;"
        + "  this.baz = this.bar;"
        + "}",
        "Property bar of type Foo has been deprecated: GRR");
  }

  @Test
  public void testAutoboxedDeprecatedProperty() {
    testError(
        externs(DEFAULT_EXTERNS),
        srcs("/** @deprecated %s */ String.prototype.prop; function f() { return 'x'.prop; }"),
        error(DEPRECATED_PROP_REASON));
  }

  @Test
  public void testAutoboxedPrivateProperty() {
    testError(
        externs(DEFAULT_EXTERNS + "/** @private */ String.prototype.prop;"),
        srcs("function f() { return 'x'.prop; }"),
        error(BAD_PRIVATE_PROPERTY_ACCESS));
  }

  @Test
  public void testNullableDeprecatedProperty() {
    testError(
        "/** @constructor */ function Foo() {}"
        + "/** @deprecated */ Foo.prototype.length;"
        + "/** @param {?Foo} x */ function f(x) { return x.length; }",
        DEPRECATED_PROP);
  }

  @Test
  public void testNullablePrivateProperty() {
    testError(new String[] {
        "/** @constructor */ function Foo() {}"
        + "/** @private */ Foo.prototype.length;",
        "/** @param {?Foo} x */ function f(x) { return x.length; }"},
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testPrivatePropertyByConvention1() {
    testError(new String[] {
        "/** @constructor */ function Foo() {}\n"
        + "/** @type {number} */ Foo.prototype.length_;\n",
        "/** @param {?Foo} x */ function f(x) { return x.length_; }\n"},
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testPrivatePropertyByConvention2() {
    testError(new String[] {
        "/** @constructor */ function Foo() {\n"
        + "  /** @type {number} */ this.length_ = 1;\n"
        + "}\n"
        + "/** @type {number} */ Foo.prototype.length_;\n",
        "/** @param {Foo} x */ function f(x) { return x.length_; }\n"},
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testDeclarationAndConventionConflict1() {
    testError(
        "/** @constructor */ function Foo() {} /** @protected */ Foo.prototype.length_;",
        CONVENTION_MISMATCH);
  }

  @Test
  public void testDeclarationAndConventionConflict2() {
    testError(
        "/** @constructor */ function Foo() {}\n"
            + "/** @public {number} */ Foo.prototype.length_;\n",
        CONVENTION_MISMATCH);
  }

  @Test
  public void testDeclarationAndConventionConflict3() {
    testError(
        "/** @constructor */ function Foo() {  /** @protected */ this.length_ = 1;\n}\n",
        CONVENTION_MISMATCH);
  }

  @Test
  public void testDeclarationAndConventionConflict4a() {

    testError(
        "/** @constructor */ function Foo() {}"
            + "Foo.prototype = { /** @protected */ length_: 1 }\n"
            + "new Foo().length_",
        CONVENTION_MISMATCH);
  }

  @Test
  public void testDeclarationAndConventionConflict4b() {

    testError(
        "/** @const */ var NS = {}; /** @constructor */ NS.Foo = function() {};"
            + "NS.Foo.prototype = { /** @protected */ length_: 1 };\n"
            + "(new NS.Foo()).length_;",
        CONVENTION_MISMATCH);
  }

  @Test
  public void testDeclarationAndConventionConflict5() {

    testError(
        "/** @constructor */ function Foo() {}\n"
            + "Foo.prototype = { /** @protected */ get length_() { return 1; } }\n",
        CONVENTION_MISMATCH);
  }

  @Test
  public void testDeclarationAndConventionConflict6() {

    testError(
        "/** @constructor */ function Foo() {}\n"
            + "Foo.prototype = { /** @protected */ set length_(x) { } }\n",
        CONVENTION_MISMATCH);
  }

  @Test
  public void testDeclarationAndConventionConflict7() {
    testError("/** @public */ var Foo_;", CONVENTION_MISMATCH);
  }

  @Test
  public void testDeclarationAndConventionConflict8() {
    testError("/** @package */ var Foo_;", CONVENTION_MISMATCH);
  }

  @Test
  public void testDeclarationAndConventionConflict9() {
    testError("/** @protected */ var Foo_;", CONVENTION_MISMATCH);
  }

  @Test
  public void testDeclarationAndConventionConflict10() {
    testError(
        lines(
            "/** @constructor */ function Foo() {}",
            "Foo.prototype = { /** @protected */ length_() { return 1; } }"),
        CONVENTION_MISMATCH);
  }

  @Test
  public void testConstantProperty1a() {
    testError(
        "/** @constructor */ function A() {"
        + "/** @const */ this.bar = 3;}"
        + "/** @constructor */ function B() {"
        + "/** @const */ this.bar = 3;this.bar += 4;}",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testConstantProperty1b() {
    testError(
        "/** @constructor */ function A() {"
        + "this.BAR = 3;}"
        + "/** @constructor */ function B() {"
        + "this.BAR = 3;this.BAR += 4;}",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testConstantProperty2a() {
    testError(
        "/** @constructor */ function Foo() {}"
        + "/** @const */ Foo.prototype.prop = 2;"
        + "var foo = new Foo();"
        + "foo.prop = 3;",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testConstantProperty2b() {
    testError(
        "/** @constructor */ function Foo() {}"
        + "Foo.prototype.PROP = 2;"
        + "var foo = new Foo();"
        + "foo.PROP = 3;",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testNamespaceConstantProperty1() {
    testError(
        ""
        + "/** @const */ var o = {};\n"
        + "/** @const */ o.x = 1;"
        + "o.x = 2;",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testNamespaceConstantProperty2() {
    testError(
        "var o = {};\n"
        + "/** @const */ o.x = 1;\n"
        + "o.x = 2;\n",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testNamespaceConstantProperty2a() {
    testSame("/** @const */ var o = {};\n"
        + "/** @const */ o.x = 1;\n"
        + "/** @const */ var o2 = {};\n"
        + "/** @const */ o2.x = 1;\n");
  }

  @Test
  public void testNamespaceConstantProperty3() {
    testError(
        "/** @const */ var o = {};\n"
        + "/** @const */ o.x = 1;"
        + "o.x = 2;",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testConstantProperty3a1() {
    // Known broken: Should be `error(CONST_PROPERTY_REASSIGNED_VALUE)`.
    testSame("var o = { /** @const */ x: 1 };" + "o.x = 2;");
  }

  @Test
  public void testConstantProperty3a2() {
    // Known broken: Should be `error(CONST_PROPERTY_REASSIGNED_VALUE)`.
    testSame("/** @const */ var o = { /** @const */ x: 1 };" + "o.x = 2;");
  }

  @Test
  public void testConstantProperty3b1() {
    // Known broken: Should be `error(CONST_PROPERTY_REASSIGNED_VALUE)`.
    testSame("var o = { XYZ: 1 };" + "o.XYZ = 2;");
  }

  @Test
  public void testConstantProperty3b2() {
    // Known broken: Should be `error(CONST_PROPERTY_REASSIGNED_VALUE)`.
    testSame("/** @const */ var o = { XYZ: 1 };" + "o.XYZ = 2;");
  }

  @Test
  public void testConstantProperty4() {
    testError(
        "/** @constructor */ function cat(name) {}"
        + "/** @const */ cat.test = 1;"
        + "cat.test *= 2;",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testConstantProperty4b() {
    testError(
        "/** @constructor */ function cat(name) {}"
        + "cat.TEST = 1;"
        + "cat.TEST *= 2;",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testConstantProperty5() {
    testError(
        "/** @constructor */ function Foo() { this.prop = 1;}"
        + "/** @const */ Foo.prototype.prop;"
        + "Foo.prototype.prop = 2",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testConstantProperty6() {
    testError(
        "/** @constructor */ function Foo() { this.prop = 1;}"
        + "/** @const */ Foo.prototype.prop = 2;",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testConstantProperty7() {
    testSame("/** @constructor */ function Foo() {} "
        + "Foo.prototype.bar_ = function() {};"
        + "/** @constructor \n * @extends {Foo} */ "
        + "function SubFoo() {};"
        + "/** @const */ /** @override */ SubFoo.prototype.bar_ = function() {};"
        + "SubFoo.prototype.baz = function() { this.bar_(); }");
  }

  @Test
  public void testConstantProperty8() {
    testSame("/** @const */ var o = { /** @const */ x: 1 };"
        + "var y = o.x;");
  }

  @Test
  public void testConstantProperty9() {
    testSame("/** @constructor */ function A() {"
        + "/** @const */ this.bar = 3;}"
        + "/** @constructor */ function B() {"
        + "this.bar = 4;}");
  }

  @Test
  public void testConstantProperty10a() {
    testSame("/** @constructor */ function Foo() { this.prop = 1;}"
        + "/** @const */ Foo.prototype.prop;");
  }

  @Test
  public void testConstantProperty10b() {
    testSame("/** @constructor */ function Foo() { this.PROP = 1;}"
        + "Foo.prototype.PROP;");
  }

  @Test
  public void testConstantProperty11() {
    testError(
        "/** @constructor */ function Foo() {}"
        + "/** @const */ Foo.prototype.bar;"
        + "/**\n"
        + " * @constructor\n"
        + " * @extends {Foo}\n"
        + " */ function SubFoo() { this.bar = 5; this.bar = 6; }",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testConstantProperty12() {
    testSame("/** @constructor */ function Foo() {}"
        + "/** @const */ Foo.prototype.bar;"
        + "/**\n"
        + " * @constructor\n"
        + " * @extends {Foo}\n"
        + " */ function SubFoo() { this.bar = 5; }"
        + "/**\n"
        + " * @constructor\n"
        + " * @extends {Foo}\n"
        + " */ function SubFoo2() { this.bar = 5; }");
  }

  @Test
  public void testConstantProperty13() {
    testError(
        "/** @constructor */ function Foo() {}"
        + "/** @const */ Foo.prototype.bar;"
        + "/**\n"
        + " * @constructor\n"
        + " * @extends {Foo}\n"
        + " */ function SubFoo() { this.bar = 5; }"
        + "/**\n"
        + " * @constructor\n"
        + " * @extends {SubFoo}\n"
        + " */ function SubSubFoo() { this.bar = 5; }",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testConstantProperty14() {
    testError(
        "/** @constructor */ function Foo() {"
        + "/** @const */ this.bar = 3; delete this.bar; }",
        CONST_PROPERTY_DELETED);
  }

  @Test
  public void testConstantPropertyInExterns() {
    String externs =
        DEFAULT_EXTERNS
        + "/** @constructor */ function Foo() {};\n"
        + "/** @const */ Foo.prototype.PROP;";
    String js = "var f = new Foo(); f.PROP = 1; f.PROP = 2;";
    testError(externs(externs), srcs(js), error(CONST_PROPERTY_REASSIGNED_VALUE));
  }

  @Test
  public void testConstantProperty15() {
    testSame("/** @constructor */ function Foo() {};\n"
        + "Foo.CONST = 100;\n"
        + "/** @type {Foo} */\n"
        + "var foo = new Foo();\n"
        + "/** @type {number} */\n"
        + "foo.CONST = Foo.CONST;");
  }

  @Test
  public void testConstantProperty15a() {
    testError(
        "/** @constructor */ function Foo() { this.CONST = 100; };\n"
        + "/** @type {Foo} */\n"
        + "var foo = new Foo();\n"
        + "/** @type {number} */\n"
        + "foo.CONST = 0;",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testConstantProperty15b() {
    testError(
        "/** @constructor */ function Foo() {};\n"
        + "Foo.prototype.CONST = 100;\n"
        + "/** @type {Foo} */\n"
        + "var foo = new Foo();\n"
        + "/** @type {number} */\n"
        + "foo.CONST = 0;",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testConstantProperty15c() {
    testError(
        ""
        + "/** @constructor */ function Bar() {this.CONST = 100;};\n"
        + "/** @constructor \n @extends {Bar} */ function Foo() {};\n"
        + "/** @type {Foo} */\n"
        + "var foo = new Foo();\n"
        + "/** @type {number} */\n"
        + "foo.CONST = 0;",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testConstantProperty16() {
    testSame("/** @constructor */ function Foo() {};\n"
        + "Foo.CONST = 100;\n"
        + "/** @constructor */ function Bar() {};\n"
        + "Bar.CONST = 100;\n");
  }

  @Test
  public void testConstantProperty17() {
    testSame("function Foo() {};\n"
        + "Foo.CONST = 100;\n"
        + "function Bar() {};\n"
        + "Bar.CONST = 100;\n");
  }

  @Test
  public void testConstantProperty18() {
    testSame("/** @param {string} a */\n"
        + "function Foo(a) {};\n"
        + "Foo.CONST = 100;\n"
        + "/** @param {string} a */\n"
        + "function Bar(a) {};\n"
        + "Bar.CONST = 100;\n");
  }

  @Test
  public void testConstantProperty19() {
    testSame("/** @param {string} a */\n"
        + "function Foo(a) {};\n"
        + "Foo.CONST = 100;\n"
        + "/** @param {number} a */\n"
        + "function Bar(a) {};\n"
        + "Bar.CONST = 100;\n");
  }

  @Test
  public void testFinalClassCannotBeSubclassed() {
    testError(
        lines(
            "/**",
            " * @constructor",
            " * @final",
            " */ var Foo = function() {};",
            "/**",
            " * @constructor",
            " * @extends {Foo}*",
            " */ var Bar = function() {};"),
        EXTEND_FINAL_CLASS);

    testError(
        lines(
            "/**",
            " * @constructor",
            " * @final",
            " */ function Foo() {};",
            "/**",
            " * @constructor",
            " * @extends {Foo}*",
            " */ function Bar() {};"),
        EXTEND_FINAL_CLASS);

    testSame(
        lines(
            "/**",
            " * @constructor",
            " * @const",
            " */ var Foo = function() {};",
            "/**",
            " * @constructor",
            " * @extends {Foo}",
            " */ var Bar = function() {};"));
  }

  @Test
  public void testCircularPrototypeLink() {
    // NOTE: this does yield a useful warning, except we don't check for it in this test:
    //      WARNING - Cycle detected in inheritance chain of type Foo
    // This warning already has a test: TypeCheckTest::testPrototypeLoop.
    testError(
        lines(
            "/** @constructor @extends {Foo} */ function Foo() {}",
            "/** @const */ Foo.prop = 1;",
            "Foo.prop = 2;"),
        CONST_PROPERTY_REASSIGNED_VALUE);
  }
}
