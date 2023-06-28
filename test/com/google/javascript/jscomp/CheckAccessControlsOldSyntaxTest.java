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
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_CLASS;
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_CLASS_REASON;
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_NAME;
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
 * Tests for {@link CheckAccessControls}.
 *
 * <p>This file has a fork, {@link CheckAccessControlsTest}, because nearly all cases using legacy
 * ES5-sytle classes should also be tested with the more modern `class` keyword. If a case using
 * `@constructor`, `@interface`, or `@record` is added to this suite, a similar case should be added
 * there under the same name using `class`.
 */
@RunWith(JUnit4.class)
public final class CheckAccessControlsOldSyntaxTest extends CompilerTestCase {

  public CheckAccessControlsOldSyntaxTest() {
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
    enableCreateModuleMap();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CheckAccessControls(compiler);
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
  public void testDeprecatedFunctionNoReason_googModule() {
    disableRewriteClosureCode(); // Remove this line once closure rewriting is after typechecking
    testError(
        "goog.module('m'); /** @deprecated */ function f() {} function g() { f(); }",
        DEPRECATED_NAME);
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
    testSame(
        "goog.makeSingleton = function(x) {};"
            + "/** @deprecated */ function f() {} goog.makeSingleton(f);");
  }

  @Test
  public void testNoWarningInGlobalScopeForCall() {
    testDepName(
        "/** @deprecated Some global scope */ function f() {} f();",
        "Variable f has been deprecated: Some global scope");
  }

  @Test
  public void testWarningInGoogModuleScopeForCall() {
    disableRewriteClosureCode(); // Remove this line once Closure rewriting is after typechecking.
    testDepName(
        "goog.module('m'); /** @deprecated Some module scope */ function f() {} f();",
        "Variable f has been deprecated: Some module scope");
  }

  @Test
  public void testNoWarningInGoogModuleScopeWithFileoverviewForCall() {
    disableRewriteClosureCode(); // Remove this line once Closure rewriting is after typechecking.
    testDepName(
        "/** @deprecated @fileoverview */ goog.module('m'); /** @deprecated Some module scope */"
            + " function f() {} f();",
        "Variable f has been deprecated: Some module scope");
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
    testSame(
        "/** @constructor \n * @deprecated */ function Foo() {} "
            + "/** @param {Foo} x */ function f(x) { return x; }");
  }

  @Test
  public void testNoWarningForDeprecatedSuperClass() {
    testNoWarning(
        lines(
            "/** @constructor @deprecated Superclass to the rescue! */",
            "function Foo() {}",
            "/** @constructor * @extends {Foo} */",
            "function SubFoo() {}",
            "function f() { new SubFoo(); }"));
  }

  @Test
  public void testNoWarningForDeprecatedSuperClassOnNamespace() {
    testNoWarning(
        lines(
            "/**",
            " * @constructor",
            " * @deprecated Its only weakness is Kryptoclass",
            "*/",
            " function Foo() {} ",
            "/** @const */ var namespace = {};",
            "/** @constructor \n * @extends {Foo} */",
            "namespace.SubFoo = function() {};",
            "function f() { new namespace.SubFoo(); }"));
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
    testSame(
        "/** @constructor */ function Foo() {}"
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
    testSame(
        "/** @deprecated */ function f() {} "
            + "/** @constructor \n * @deprecated */ "
            + "var Foo = function() {}; "
            + "Foo.prototype.bar = function() { f(); }");
  }

  @Test
  public void testNoWarningOnDeclaration() {
    testSame(
        "/** @constructor */ function F() {\n"
            + "  /**\n"
            + "   * @type {number}\n"
            + "   * @deprecated Use something else.\n"
            + "   */\n"
            + "  this.code;\n"
            + "}");
  }

  @Test
  public void testNoWarningInDeprecatedClass2() {
    testSame(
        "/** @deprecated */ function f() {} "
            + "/** @constructor \n * @deprecated */ "
            + "var Foo = function() {}; "
            + "Foo.bar = function() { f(); }");
  }

  @Test
  public void testNoWarningInDeprecatedStaticMethod() {
    testSame(
        "/** @deprecated */ function f() {} "
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
    testSame(
        "/** @constructor */ function Foo() {}"
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
  public void testWarningForFunctionProp() {
    testDepProp(
        "/** @deprecated Don't call me... */ Function.prototype.deprecatedMethod = function() {};"
            + "(function() {}).deprecatedMethod();",
        "Property deprecatedMethod of type function has been deprecated: Don't call me...");
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
    testSame(
        "/** @constructor */ var Foo = function() {};"
            + "Foo.prototype.bar = function() {};"
            + "/** @deprecated */ Foo.prototype.baz = Foo.prototype.bar;"
            + "(new Foo()).bar();");
  }

  @Test
  public void testNoWarningOnDeprecatedPrototype() {
    // This used to cause an NPE.
    testSame(
        "/** @constructor */ var Foo = function() {};"
            + "/** @deprecated */ Foo.prototype = {};"
            + "Foo.prototype.bar = function() {};");
  }

  @Test
  public void testPrivateAccessForNames() {
    testSame("/** @private */ function foo_() {}; foo_();");
    testError(srcs("/** @private */ function foo_() {};", "foo_();"), BAD_PRIVATE_GLOBAL_ACCESS);
  }

  @Test
  public void testPrivateAccessForNames2() {
    // Not private by convention
    testSame("function foo_() {}; foo_();");
    testSame(srcs("function foo_() {};", "foo_();"));
  }

  @Test
  public void testPrivateAccessForProperties1() {
    testSame(
        "/** @constructor */ function Foo() {}"
            + "/** @private */ Foo.prototype.bar_ = function() {};"
            + "Foo.prototype.baz = function() { this.bar_(); }; (new Foo).bar_();");
  }

  @Test
  public void testPrivateAccessForProperties2() {
    testSame(
        srcs(
            "/** @constructor */ function Foo() {}",
            "/** @private */ Foo.prototype.bar_ = function() {};"
                + "Foo.prototype.baz = function() { this.bar_(); }; (new Foo).bar_();"));
  }

  @Test
  public void testPrivateAccessForProperties3() {
    // Even though baz is "part of the Foo class" the access is disallowed since it's
    // not in the same file.
    testError(
        srcs(
            "/** @constructor */ function Foo() {}"
                + "/** @private */ Foo.prototype.bar_ = function() {}; (new Foo).bar_();",
            "Foo.prototype.baz = function() { this.bar_(); };"),
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
  public void testBug133902968() {
    test(
        externs(
            lines(
                "/** @interface */", //
                "function CSSProperties() {}",
                "/** @type {string} */",
                "CSSProperties.prototype.fontStyle;",
                "",
                "/** @struct @interface @extends {CSSProperties} */",
                "function CSSStyleDeclaration() {}",
                "",
                "function alert(s) {}")),
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("apackage", "afile.js"),
                lines(
                    "/**", //
                    " * @fileoverview",
                    " * @package",
                    " */",
                    "",
                    "/** @param {!CSSStyleDeclaration} style */",
                    "function f(style) {",
                    "  style.fontStyle = 'normal';",
                    "}")),
            SourceFile.fromCode(
                Compiler.joinPathParts("anotherpackage", "anotherfile.js"),
                lines(
                    "/** @param {!CSSStyleDeclaration} style */", //
                    "function g(style) {",
                    "  alert(style.fontStyle);",
                    "}"))));
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
    String[] js =
        new String[] {
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
    testError(
        srcs(
            "/** @constructor */ function Foo() {} (new Foo).bar_();",
            "/** @private */ Foo.prototype.bar_ = function() {};"
                + "Foo.prototype.baz = function() { this.bar_(); };"),
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPrivateAccessForProperties2() {
    testError(
        srcs(
            "/** @constructor */ function Foo() {} "
                + "/** @private */ Foo.prototype.bar_ = function() {};"
                + "Foo.prototype.baz = function() { this.bar_(); };",
            "(new Foo).bar_();"),
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPrivateAccessForProperties3() {
    testError(
        srcs(
            "/** @constructor */ function Foo() {} "
                + "/** @private */ Foo.prototype.bar_ = function() {};",
            "/** @constructor */ function OtherFoo() { (new Foo).bar_(); }"),
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPrivateAccessForProperties4() {
    testError(
        srcs(
            "/** @constructor */ function Foo() {} "
                + "/** @private */ Foo.prototype.bar_ = function() {};",
            "/** @constructor \n * @extends {Foo} */ " + "function SubFoo() { this.bar_(); }"),
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPrivateAccessForProperties5() {
    testError(
        srcs(
            "/** @constructor */ function Foo() {} "
                + "/** @private */ Foo.prototype.bar_ = function() {};",
            "/** @constructor \n * @extends {Foo} */ "
                + "function SubFoo() {};"
                + "SubFoo.prototype.baz = function() { this.bar_(); }"),
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
    testError(
        srcs(
            "/** @const */ var ns = {};"
                + "/** @constructor */ ns.Foo = function() {}; "
                + "/** @private */ ns.Foo.prototype.bar_ = function() {};",
            "/** @constructor \n * @extends {ns.Foo} */ "
                + "ns.SubFoo = function() {};"
                + "ns.SubFoo.prototype.bar_ = function() {};"),
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPrivateAccessForProperties7() {
    // It's OK to override a private property with a non-private property
    // in the same file, but you'll get yelled at when you try to use it.
    testError(
        srcs(
            "/** @constructor */ function Foo() {} "
                + "/** @private */ Foo.prototype.bar_ = function() {};"
                + "/** @constructor \n * @extends {Foo} */ "
                + "function SubFoo() {};"
                + "SubFoo.prototype.bar_ = function() {};",
            "SubFoo.prototype.baz = function() { this.bar_(); }"),
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPrivateAccessForProperties8() {
    testError(
        srcs(
            "/** @constructor */ function Foo() { /** @private */ this.bar_ = 3; }",
            "/** @constructor \n * @extends {Foo} */ "
                + "function SubFoo() { /** @private */ this.bar_ = 3; };"),
        PRIVATE_OVERRIDE);
  }

  @Test
  public void testNoPrivateAccessForProperties9() {
    testError(
        srcs(
            "/** @constructor */ function Foo() {}"
                + "Foo.prototype = {"
                + "/** @private */ bar_: 3"
                + "}",
            "new Foo().bar_;"),
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPrivateAccessForProperties10() {
    testError(
        srcs(
            "/** @constructor */ function Foo() {}"
                + "Foo.prototype = {"
                + "/** @private */ bar_: function() {}"
                + "}",
            "new Foo().bar_();"),
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPrivateAccessForProperties11() {
    testError(
        srcs(
            "/** @constructor */ function Foo() {}"
                + "Foo.prototype = {"
                + "/** @private */ get bar_() { return 1; }"
                + "}",
            "var a = new Foo().bar_;"),
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPrivateAccessForProperties12() {
    testError(
        srcs(
            "/** @constructor */ function Foo() {}"
                + "Foo.prototype = {"
                + "/** @private */ set bar_(x) { this.barValue = x; }"
                + "}",
            "new Foo().bar_ = 1;"),
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
    testError(
        srcs(
            "/** @const */ var foo = {};\n" + "/** @private */ foo.bar_ = function() {};",
            "foo.bar_();"),
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testProtectedAccessForProperties1() {
    testSame(
        srcs(
            "/** @constructor */ function Foo() {}"
                + "/** @protected */ Foo.prototype.bar = function() {};"
                + "(new Foo).bar();",
            "Foo.prototype.baz = function() { this.bar(); };"));
  }

  @Test
  public void testProtectedAccessForProperties2() {
    testSame(
        srcs(
            "/** @constructor */ function Foo() {}"
                + "/** @protected */ Foo.prototype.bar = function() {};"
                + "(new Foo).bar();",
            "/** @constructor \n * @extends {Foo} */" + "function SubFoo() { this.bar(); }"));
  }

  @Test
  public void testProtectedAccessForProperties3() {
    testSame(
        srcs(
            "/** @constructor */ function Foo() {}"
                + "/** @protected */ Foo.prototype.bar = function() {};"
                + "(new Foo).bar();",
            "/** @constructor \n * @extends {Foo} */"
                + "function SubFoo() { }"
                + "SubFoo.baz = function() { (new Foo).bar(); }"));
  }

  @Test
  public void testProtectedAccessForProperties4() {
    testSame(
        srcs(
            "/** @constructor */ function Foo() {}" + "/** @protected */ Foo.bar = function() {};",
            "/** @constructor \n * @extends {Foo} */" + "function SubFoo() { Foo.bar(); }"));
  }

  @Test
  public void testProtectedAccessForProperties5() {
    testSame(
        srcs(
            "/** @constructor */ function Foo() {}"
                + "/** @protected */ Foo.prototype.bar = function() {};"
                + "(new Foo).bar();",
            "/** @constructor \n * @extends {Foo} */" + "var SubFoo = function() { this.bar(); }"));
  }

  @Test
  public void testProtectedAccessForProperties6() {
    testSame(
        srcs(
            "/** @constructor */ goog.Foo = function() {};"
                + "/** @protected */ goog.Foo.prototype.bar = function() {};",
            "/** @constructor \n * @extends {goog.Foo} */"
                + "goog.SubFoo = function() { this.bar(); };"));
  }

  @Test
  public void testProtectedAccessForProperties7() {
    testSame(
        srcs(
            "/** @constructor */ var Foo = function() {};"
                + "Foo.prototype = { /** @protected */ bar: function() {} }",
            "/** @constructor \n * @extends {Foo} */"
                + "var SubFoo = function() { this.bar(); };"
                + "SubFoo.prototype = { moo: function() { this.bar(); }};"));
  }

  @Test
  public void testProtectedAccessForProperties8() {
    testSame(
        srcs(
            "/** @constructor */ var Foo = function() {};"
                + "Foo.prototype = { /** @protected */ bar: function() {} }",
            "/** @constructor \n * @extends {Foo} */"
                + "var SubFoo = function() {};"
                + "SubFoo.prototype = { get moo() { this.bar(); }};"));
  }

  @Test
  public void testProtectedAccessForProperties9() {
    testSame(
        srcs(
            "/** @constructor */ var Foo = function() {};"
                + "Foo.prototype = { /** @protected */ bar: function() {} }",
            "/** @constructor \n * @extends {Foo} */"
                + "var SubFoo = function() {};"
                + "SubFoo.prototype = { set moo(val) { this.x = this.bar(); }};"));
  }

  @Test
  public void testProtectedAccessForProperties10() {
    testSame(
        srcs(
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
        srcs(
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
        srcs(
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
        srcs(
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
    test(
        srcs(
            lines(
                "/** @constructor */ var Foo = function() {};",
                "Foo.prototype = { /** @protected */ bar: function() {} }"),
            lines(
                "/** @constructor @extends {Foo} */",
                "var OtherFoo = function() { this.bar(); };",
                "OtherFoo.prototype = { moo() { new Foo().bar(); }};")));
  }

  @Test
  public void testProtectedAccessForProperties15() {
    // access in computed member function
    test(
        srcs(
            lines(
                "/** @constructor */ var Foo = function() {};",
                "Foo.prototype = { /** @protected */ bar: function() {} }"),
            lines(
                "/** @constructor @extends {Foo} */",
                "var OtherFoo = function() { this['bar'](); };",
                "OtherFoo.prototype = { ['bar']() { new Foo().bar(); }};")));
  }

  @Test
  public void testProtectedAccessForProperties16() {
    // access in nested arrow function
    test(
        srcs(
            lines(
                "/** @constructor */ var Foo = function() {};",
                "/** @protected */ Foo.prototype.bar = function() {};"),
            lines(
                "/** @constructor @extends {Foo} */",
                "var OtherFoo = function() { var f = () => this.bar(); };",
                "OtherFoo.prototype.baz = function() { return () => this.bar(); };")));
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
    testError(
        srcs(
            "/** @constructor */ function Foo() {} "
                + "/** @protected */ Foo.prototype.bar = function() {};",
            "(new Foo).bar();"),
        BAD_PROTECTED_PROPERTY_ACCESS);
  }

  @Test
  public void testNoProtectedAccessForProperties2() {
    testError(
        srcs(
            "/** @constructor */ function Foo() {} "
                + "/** @protected */ Foo.prototype.bar = function() {};",
            "/** @constructor */ function OtherFoo() { (new Foo).bar(); }"),
        BAD_PROTECTED_PROPERTY_ACCESS);
  }

  @Test
  public void testNoProtectedAccessForProperties3() {
    testError(
        srcs(
            "/** @constructor */ function Foo() {} "
                + "/** @constructor \n * @extends {Foo} */ "
                + "function SubFoo() {}"
                + "/** @protected */ SubFoo.prototype.bar = function() {};",
            "/** @constructor \n * @extends {Foo} */ "
                + "function SubberFoo() { (new SubFoo).bar(); }"),
        BAD_PROTECTED_PROPERTY_ACCESS);
  }

  @Test
  public void testNoProtectedAccessForProperties4() {
    testError(
        srcs(
            "/** @constructor */ function Foo() { (new SubFoo).bar(); } ",
            "/** @constructor \n * @extends {Foo} */ "
                + "function SubFoo() {}"
                + "/** @protected */ SubFoo.prototype.bar = function() {};"),
        BAD_PROTECTED_PROPERTY_ACCESS);
  }

  @Test
  public void testNoProtectedAccessForProperties5() {
    testError(
        srcs(
            "/** @constructor */ goog.Foo = function() {};"
                + "/** @protected */ goog.Foo.prototype.bar = function() {};",
            "/** @constructor */" + "goog.NotASubFoo = function() { (new goog.Foo).bar(); };"),
        BAD_PROTECTED_PROPERTY_ACCESS);
  }

  @Test
  public void testNoProtectedAccessForProperties6() {
    testError(
        srcs(
            "/** @constructor */ function Foo() {}"
                + "Foo.prototype = {"
                + "/** @protected */ bar: 3"
                + "}",
            "new Foo().bar;"),
        BAD_PROTECTED_PROPERTY_ACCESS);
  }

  @Test
  public void testNoProtectedAccessForProperties7() {
    testError(
        srcs(
            "/** @constructor */ function Foo() {}"
                + "Foo.prototype = {"
                + "/** @protected */ bar: function() {}"
                + "}",
            "new Foo().bar();"),
        BAD_PROTECTED_PROPERTY_ACCESS);
  }

  @Test
  public void testNoProtectedAccessForProperties8() {
    testError(
        srcs(
            lines(
                "/** @constructor */ var Foo = function() {};",
                "Foo.prototype = { /** @protected */ bar: function() {} }"),
            lines(
                "/** @constructor */",
                "var OtherFoo = function() { this.bar(); };",
                "OtherFoo.prototype = { moo() { new Foo().bar(); }};")),
        BAD_PROTECTED_PROPERTY_ACCESS);
  }

  @Test
  public void testNoProtectedAccessForProperties9() {
    testError(
        srcs(
            lines(
                "/** @constructor */ var Foo = function() {};",
                "Foo.prototype = { /** @protected */ bar: function() {} }"),
            lines(
                "/** @constructor */",
                "var OtherFoo = function() { this['bar'](); };",
                "OtherFoo.prototype = { ['bar']() { new Foo().bar(); }};")),
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
    testSame(
        srcs(
            lines("/** @constructor */ function Foo() {}", "/** @protected */ Foo.prototype.x;"),
            lines(
                "/** @constructor @extends {Foo} */ function Bar() {}",
                "/** @protected */ Bar.prototype.x;")));
  }

  @Test
  public void testPackagePrivateAccessForNames() {
    testError(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/** @package */", //
                    "var name = 'foo';")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"), //
                lines("name;"))),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testPackagePrivateAccessForNames_googModule() {
    // TODO(b/133450410): This should be a visibility violation.
    disableRewriteClosureCode();
    testNoWarning(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "goog.module('Foo');",
                    "/** @package */",
                    "var name = 'foo';",
                    "exports = name;")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "goog.module('client'); const name = goog.require('Foo'); name;")));
  }

  @Test
  public void testPackagePrivateAccessForNames_esModule() {
    // TODO(b/133450410): This should be a visibility violation.
    testNoWarning(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/** @package */", //
                    "var name = 'foo';",
                    "export {name};")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "import {name} from '/foo/bar.js'; name;")));
  }

  @Test
  public void testPackagePrivateAccessForProperties1() {
    testSame(
        "/** @constructor */ function Foo() {}"
            + "/** @package */ Foo.prototype.bar = function() {};"
            + "Foo.prototype.baz = function() { this.bar(); }; (new Foo).bar();");
  }

  @Test
  public void testPackagePrivateAccessForProperties2() {
    testSame(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"), "/** @constructor */ function Foo() {}"),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "/** @package */ Foo.prototype.bar = function() {};"
                    + "Foo.prototype.baz = function() { this.bar(); }; (new Foo).bar();")));
  }

  @Test
  public void testPackagePrivateAccessForProperties3() {
    testSame(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @constructor */ function Foo() {}"
                    + "/** @package */ Foo.prototype.bar = function() {}; (new Foo).bar();"),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "baz.js"),
                "Foo.prototype.baz = function() { this.bar(); };")));
  }

  @Test
  public void testPackagePrivateAccessForProperties4() {
    testSame(
        srcs(
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
        srcs(
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
        error(BAD_PACKAGE_PROPERTY_ACCESS));
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
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @constructor */ function Foo() {} (new Foo).bar();"),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "/** @package */ Foo.prototype.bar = function() {};"
                    + "Foo.prototype.baz = function() { this.bar(); };")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackagePrivateAccessForProperties2() {
    testError(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @constructor */ function Foo() {} "
                    + "/** @package */ Foo.prototype.bar = function() {};"
                    + "Foo.prototype.baz = function() { this.bar(); };"),
            SourceFile.fromCode(Compiler.joinPathParts("baz", "quux.js"), "(new Foo).bar();")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackagePrivateAccessForProperties3() {
    testError(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @constructor */ function Foo() {} "
                    + "/** @package */ Foo.prototype.bar = function() {};"),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "/** @constructor */ function OtherFoo() { (new Foo).bar(); }")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackagePrivateAccessForProperties4() {
    testError(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @constructor */ function Foo() {} "
                    + "/** @package */ Foo.prototype.bar = function() {};"),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "/** @constructor \n * @extends {Foo} */ " + "function SubFoo() { this.bar(); }")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackagePrivateAccessForNamespaces() {
    testError(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @const */ var foo = {};\n" + "/** @package */ foo.bar = function() {};"),
            SourceFile.fromCode(Compiler.joinPathParts("baz", "quux.js"), "foo.bar();")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackagePrivateAccessForProperties5() {
    testError(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @constructor */ function Foo() {} "
                    + "/** @package */ Foo.prototype.bar = function() {};"),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "/** @constructor \n * @extends {Foo} */ "
                    + "function SubFoo() {};"
                    + "SubFoo.prototype.baz = function() { this.bar(); }")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackagePrivateAccessForProperties6() {
    // Overriding a private property with a non-package-private property
    // in a different file causes problems.
    testError(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @constructor */ function Foo() {} "
                    + "/** @package */ Foo.prototype.bar = function() {};"),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "/** @constructor \n * @extends {Foo} */ "
                    + "function SubFoo() {};"
                    + "SubFoo.prototype.bar = function() {};")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoPackagePrivateAccessForProperties7() {
    // It's OK to override a package-private property with a
    // non-package-private property in the same file, but you'll get
    // yelled at when you try to use it.
    testError(
        srcs(
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
        error(BAD_PACKAGE_PROPERTY_ACCESS));
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
  public void
      testOverrideWithoutVisibilityRedeclInFileWithFileOverviewVisibilityNotAllowed_GoogModule() {
    testError(
        lines(
            "/**",
            " * @fileoverview",
            " * @package",
            " */",
            "goog.module('mod')",
            "/** @struct @constructor */",
            "Foo = function() {};",
            "/** @private */",
            "Foo.prototype.privateMethod_ = function() {};",
            "/** @struct @constructor @extends {Foo} */",
            "Bar = function() {};",
            "/** @override */",
            "Bar.prototype.privateMethod_ = function() {};"),
        BAD_PROPERTY_OVERRIDE_IN_FILE_WITH_FILEOVERVIEW_VISIBILITY);
  }

  @Test
  public void
      testOverrideWithoutVisibilityRedeclInFileWithFileOverviewVisibilityNotAllowed_esModule() {
    testError(
        lines(
            "/**",
            " * @fileoverview",
            " * @package",
            " */",
            "/** @struct @constructor */",
            "var Foo = function() {};",
            "/** @private */",
            "Foo.prototype.privateMethod_ = function() {};",
            "/** @struct @constructor @extends {Foo} */",
            "var Bar = function() {};",
            "/** @override */",
            "Bar.prototype.privateMethod_ = function() {};",
            "export {Foo, Bar};"),
        BAD_PROPERTY_OVERRIDE_IN_FILE_WITH_FILEOVERVIEW_VISIBILITY);
  }

  @Test
  public void testNamespacedFunctionDoesNotNeedVisibilityRedeclInFileWithFileOverviewVisibility() {
    testSame(
        "/**\n"
            + " * @fileoverview\n"
            + " * @package\n"
            + " */\n"
            + "/** @return {string} */\n"
            + "foo.bar = function() { return 'asdf'; };");
  }

  @Test
  public void
      testOverrideWithoutVisibilityRedeclInFileWithFileOverviewVisibilityNotAllowed_TwoFiles() {
    testError(
        srcs(
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
                + "Bar.prototype.protectedMethod = function() {};\n"),
        BAD_PROPERTY_OVERRIDE_IN_FILE_WITH_FILEOVERVIEW_VISIBILITY);
  }

  @Test
  public void testOverrideWithoutVisibilityRedeclInFileWithNoFileOverviewOk() {
    testSame(
        "/** @struct @constructor */\n"
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
    testSame(
        "/**\n"
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
    testSame(
        "/**\n"
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
    testSame(
        srcs(
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
                + "Bar.prototype.protectedMethod = function() {};\n"));
  }

  @Test
  public void testPublicFileOverviewVisibilityDoesNotApplyToNameWithExplicitPackageVisibility() {
    testError(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/**\n"
                    + " * @fileoverview\n"
                    + " * @public\n"
                    + " */\n"
                    + "/** @constructor @package */ function Foo() {};"),
            SourceFile.fromCode(Compiler.joinPathParts("baz", "quux.js"), "new Foo();")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testPackageFileOverviewVisibilityDoesNotApplyToNameWithExplicitPublicVisibility() {
    testSame(
        srcs(
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
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/**\n"
                    + " * @fileoverview\n"
                    + " * @package\n"
                    + " */\n"
                    + "/** @constructor */\n"
                    + "var Foo = function() {};\n"),
            SourceFile.fromCode(Compiler.joinPathParts("baz", "quux.js"), "new Foo();")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testPackageFileOverviewVisibilityAppliesToNameWithoutExplicitVisibility_googModule() {
    // TODO(b/133450410): Requiring 'Foo' should be a visibility violation.
    disableRewriteClosureCode();
    testNoWarning(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @package",
                    " */",
                    "goog.module('Foo');",
                    "/** @constructor */",
                    "var Foo = function() {};",
                    "exports = Foo;")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "goog.module('client'); const Foo = goog.require('Foo'); new Foo();")));
  }

  @Test
  public void testPackageFileOverviewVisibilityAppliesToNameWithoutExplicitVisibility_esModule() {
    // TODO(b/133450410): Importing 'Foo' should be a visibility violation.
    testNoWarning(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @package",
                    " */",
                    "/** @constructor */",
                    "var Foo = function() {};",
                    "export {Foo};")),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "import {Foo} from '/foo/bar.js'; new Foo();")));
  }

  @Test
  public void
      testPackageFileOverviewVisibilityDoesNotApplyToPropertyWithExplicitPublicVisibility() {
    testSame(
        srcs(
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
                "var foo = new Foo();\n" + "foo.bar();")));
  }

  @Test
  public void testFileoverviewVisibilityDoesNotApplyToGoogProvidedNamespace1() {
    testNoWarning(
        srcs(
            SourceFile.fromCode("foo.js", "goog.provide('foo');"),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/**\n",
                    "  * @fileoverview\n",
                    "  * @package\n",
                    "  */\n",
                    "goog.provide('foo.bar');")),
            SourceFile.fromCode("bar.js", "goog.require('foo')")));
  }

  @Test
  public void testFileoverviewVisibilityDoesNotApplyToGoogProvidedNamespace2() {
    testNoWarning(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines("/**", " * @fileoverview", " * @package", " */", "goog.provide('foo.bar');")),
            SourceFile.fromCode("foo.js", "goog.provide('foo');"),
            SourceFile.fromCode("bar.js", lines("goog.require('foo');", "var x = foo;"))));
  }

  @Test
  public void testFileoverviewVisibilityDoesNotApplyToGoogProvidedNamespace3() {
    testNoWarning(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                lines(
                    "/**",
                    " * @fileoverview",
                    " * @package",
                    " */",
                    "goog.provide('one.two');",
                    "one.two.three = function(){};")),
            SourceFile.fromCode("baz.js", lines("goog.require('one.two');", "var x = one.two;"))));
  }

  @Test
  public void testFileoverviewVisibilityDoesNotApplyToGoogProvidedNamespace4() {
    testError(
        srcs(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/**\n"
                    + " * @fileoverview\n"
                    + " * @package\n"
                    + " */\n"
                    + "goog.provide('one.two');\n"
                    + "one.two.three = function(){};"),
            SourceFile.fromCode(
                "baz.js", "goog.require('one.two');\n" + "var x = one.two.three();")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void
      testPublicFileOverviewVisibilityDoesNotApplyToPropertyWithExplicitPackageVisibility() {
    testError(
        srcs(
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
                Compiler.joinPathParts("baz", "quux.js"), "var foo = new Foo();\n" + "foo.bar();")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testPublicFileOverviewVisibilityAppliesToPropertyWithoutExplicitVisibility() {
    testSame(
        srcs(
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
                "var foo = new Foo();\n" + "foo.bar();")));
  }

  @Test
  public void testPackageFileOverviewVisibilityAppliesToPropertyWithoutExplicitVisibility() {
    testError(
        srcs(
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
                Compiler.joinPathParts("baz", "quux.js"), "var foo = new Foo();\n" + "foo.bar();")),
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testFileOverviewVisibilityComesFromDeclarationFileNotUseFile() {
    testError(
        srcs(
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
        error(BAD_PACKAGE_PROPERTY_ACCESS));
  }

  @Test
  public void testNoExceptionsWithBadConstructors1() {
    testSame(
        srcs(
            "function Foo() { (new SubFoo).bar(); } "
                + "/** @constructor */ function SubFoo() {}"
                + "/** @protected */ SubFoo.prototype.bar = function() {};"));
  }

  @Test
  public void testNoExceptionsWithBadConstructors2() {
    testSame(
        srcs(
            "/** @constructor */ function Foo() {} "
                + "Foo.prototype.bar = function() {};"
                + "/** @constructor */"
                + "function SubFoo() {}"
                + "/** @protected */ "
                + "SubFoo.prototype.bar = function() { (new Foo).bar(); };"));
  }

  @Test
  public void testGoodOverrideOfProtectedProperty() {
    testSame(
        srcs(
            "/** @constructor */ function Foo() { } "
                + "/** @protected */ Foo.prototype.bar = function() {};",
            "/** @constructor \n * @extends {Foo} */ "
                + "function SubFoo() {}"
                + "/** @inheritDoc */ SubFoo.prototype.bar = function() {};"));
  }

  @Test
  public void testBadOverrideOfProtectedProperty() {
    testError(
        srcs(
            "/** @constructor */ function Foo() { } "
                + "/** @protected */ Foo.prototype.bar = function() {};",
            "/** @constructor \n * @extends {Foo} */ "
                + "function SubFoo() {}"
                + "/** @private */ SubFoo.prototype.bar = function() {};"),
        VISIBILITY_MISMATCH);
  }

  @Test
  public void testBadOverrideOfPrivateProperty() {
    testError(
        srcs(
            "/** @constructor */ function Foo() { } "
                + "/** @private */ Foo.prototype.bar = function() {};",
            "/** @constructor \n * @extends {Foo} */ "
                + "function SubFoo() {}"
                + "/** @protected */ SubFoo.prototype.bar = function() {};"),
        PRIVATE_OVERRIDE);
  }

  @Test
  public void testAccessOfStaticMethodOnPrivateConstructor() {
    testSame(
        srcs(
            "/** @constructor \n * @private */ function Foo() { } "
                + "Foo.create = function() { return new Foo(); };",
            "Foo.create()"));
  }

  @Test
  public void testAccessOfStaticMethodOnPrivateQualifiedConstructor() {
    testSame(
        srcs(
            "/** @constructor \n * @private */ goog.Foo = function() { }; "
                + "goog.Foo.create = function() { return new goog.Foo(); };",
            "goog.Foo.create()"));
  }

  @Test
  public void testInstanceofOfPrivateConstructor() {
    testSame(
        srcs(
            "/** @constructor \n * @private */ goog.Foo = function() { }; "
                + "goog.Foo.create = function() { return new goog.Foo(); };",
            "goog instanceof goog.Foo"));
  }

  @Test
  public void testOkAssignmentOfDeprecatedProperty() {
    testSame("/** @constructor */ function Foo() {" + " /** @deprecated */ this.bar = 3;" + "}");
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
    testError(
        srcs(
            "/** @constructor */ function Foo() {}" + "/** @private */ Foo.prototype.length;",
            "/** @param {?Foo} x */ function f(x) { return x.length; }"),
        BAD_PRIVATE_PROPERTY_ACCESS);
  }

  @Test
  public void testNoPrivatePropertyByConvention1() {
    testSame(
        srcs(
            "/** @constructor */ function Foo() {}\n"
                + "/** @type {number} */ Foo.prototype.length_;\n",
            "/** @param {?Foo} x */ function f(x) { return x.length_; }\n"));
  }

  @Test
  public void testNoPrivatePropertyByConvention2() {
    testSame(
        srcs(
            "/** @constructor */ function Foo() {\n"
                + "  /** @type {number} */ this.length_ = 1;\n"
                + "}\n"
                + "/** @type {number} */ Foo.prototype.length_;\n",
            "/** @param {Foo} x */ function f(x) { return x.length_; }\n"));
  }

  @Test
  public void testDeclarationAndConventionConflict1() {
    // "private" convention is no longer recognized
    testSame("/** @constructor */ function Foo() {} /** @protected */ Foo.prototype.length_;");
  }

  @Test
  public void testDeclarationAndConventionConflict2() {
    // "private" convention is no longer recognized
    testSame("/** @constructor */ function Foo() {  /** @protected */ this.length_ = 1;\n}\n");
  }

  @Test
  public void testDeclarationAndConventionConflict3() {
    // "private" convention is no longer recognized
    testSame("/** @protected */ var Foo_;");
  }

  @Test
  public void testConstantPropertyByJsdoc_initialAssignmentOk() {
    testNoWarning(
        lines(
            "/** @constructor */ function A() {",
            "  /** @const */ this.bar = 3;",
            "}",
            "/** @constructor */ function B() {",
            "  /** @const */ this.bar = 3;",
            "}"));
  }

  @Test
  public void testConstantPropertyByConvention_initialAssignmentOk() {
    testNoWarning(
        lines(
            "/** @constructor */ function A() {",
            "  this.BAR = 3;",
            "}",
            "/** @constructor */ function B() {",
            "  this.BAR = 3;",
            "}"));
  }

  @Test
  public void testConstantPropertyByJsdoc_reassignmentWarns() {
    testError(
        "/** @constructor */ function A() {"
            + "/** @const */ this.bar = 3;}"
            + "/** @constructor */ function B() {"
            + "/** @const */ this.bar = 3;this.bar += 4;}",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testConstantPropertyReassigned_crossModuleWithCollidingNames() {
    disableRewriteClosureCode();
    testNoWarning(
        srcs(
            lines(
                "goog.module('mod1');",
                "/** @constructor */",
                "function A() {",
                "  /** @const */",
                "  this.bar = 3;",
                "}"),
            lines(
                "goog.module('mod2');",
                "/** @constructor */",
                "function A() {",
                "  /** @const */",
                "  this.bar = 3;",
                "}")));
  }

  @Test
  public void testNoConstantPropertyByConvention() {
    testSame(
        "/** @constructor */ function A() {"
            + "this.BAR = 3;}"
            + "/** @constructor */ function B() {"
            + "this.BAR = 3;this.BAR += 4;}");
  }

  @Test
  public void testConstantPropertyByJsdocOnPrototype_reassignmentWarns() {
    testError(
        "/** @constructor */ function Foo() {}"
            + "/** @const */ Foo.prototype.prop = 2;"
            + "var foo = new Foo();"
            + "foo.prop = 3;",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testConstantPropertyByConventionOnPrototype_notEnforced() {
    testSame(
        "/** @constructor */ function Foo() {}"
            + "Foo.prototype.PROP = 2;"
            + "var foo = new Foo();"
            + "foo.PROP = 3;");
  }

  @Test
  public void testConstantPropertyOnConstNamespaceByAssignment_initialAssignmentOk() {
    testNoWarning(lines("/** @const */ var o = {};", "/** @const */ o.x = 1;"));
  }

  @Test
  public void testConstantPropertyOnConstNamespaceByAssignment_reassignmentWarns() {
    testError(
        "" + "/** @const */ var o = {};\n" + "/** @const */ o.x = 1;" + "o.x = 2;",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testConstantPropertyOnNamespaceByAssignment_reassignmentWarns() {
    testError(
        "var o = {};\n" + "/** @const */ o.x = 1;\n" + "o.x = 2;\n",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testNamespaceConstantPropertyOnNamespace_assigningSeparateNamespacesOk() {
    testSame(
        "/** @const */ var o = {};\n"
            + "/** @const */ o.x = 1;\n"
            + "/** @const */ var o2 = {};\n"
            + "/** @const */ o2.x = 1;\n");
  }

  @Test
  public void testConstantPropertyOnObjectLiteralByLiteralKey_initialAssignmentOk() {
    testNoWarning(lines("var o = {", "  /** @const */ x: 1", " };"));
  }

  @Test
  public void testConstantPropertyOnObjectLiteralByLiteralKey_reassignmentWarns() {
    testError(
        lines("var o = {", "  /** @const */ x: 1", " };", "o.x = 2;"),
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testConstantPropertyOnConstObjectLiteralByLiteralKey_reassignmentWarns() {
    testError(
        lines("/** @const */", "var o = {", "  /** @const */ x: 1", " };", "o.x = 2;"),
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testConstantPropertyOnObjectLiteralByConvention_notEnforced() {
    testSame(lines("var o = {", "  XYZ: 1", " };", "o.XYZ = 2;"));
  }

  @Test
  public void testConstantPropertyOnConstObjectLiteralByConvention_notEnforced() {
    testSame(
        lines(
            "/** @const */ var o = {", //
            "  XYZ: 1",
            "};",
            "o.XYZ = 2;"));
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
    testSame(
        lines(
            "/** @constructor */ function cat(name) {}", //
            "cat.TEST = 1;",
            "cat.TEST *= 2;"));
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
    testSame(
        "/** @constructor */ function Foo() {} "
            + "Foo.prototype.bar_ = function() {};"
            + "/** @constructor \n * @extends {Foo} */ "
            + "function SubFoo() {};"
            + "/** @const */ /** @override */ SubFoo.prototype.bar_ = function() {};"
            + "SubFoo.prototype.baz = function() { this.bar_(); }");
  }

  @Test
  public void testConstantProperty8() {
    testSame("/** @const */ var o = { /** @const */ x: 1 };" + "var y = o.x;");
  }

  @Test
  public void testConstantProperty9() {
    testSame(
        "/** @constructor */ function A() {"
            + "/** @const */ this.bar = 3;}"
            + "/** @constructor */ function B() {"
            + "this.bar = 4;}");
  }

  @Test
  public void testConstantProperty10a() {
    testSame(
        "/** @constructor */ function Foo() { this.prop = 1;}"
            + "/** @const */ Foo.prototype.prop;");
  }

  @Test
  public void testConstantProperty10b() {
    testSame("/** @constructor */ function Foo() { this.PROP = 1;}" + "Foo.prototype.PROP;");
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
    testSame(
        "/** @constructor */ function Foo() {}"
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
        "/** @constructor */ function Foo() {" + "/** @const */ this.bar = 3; delete this.bar; }",
        CONST_PROPERTY_DELETED);
  }

  @Test
  public void testConstantProperty_recordType() {
    test(
        srcs(
            lines(
                "/** @record */",
                "function Foo() { }",
                "/** @const {number} */",
                "Foo.prototype.bar;",
                "",
                "const /** !Foo */ x = {",
                "  bar: 9,",
                "};",
                "x.bar = 0;")),
        error(CONST_PROPERTY_REASSIGNED_VALUE)
            .withMessageContaining("unknown location due to structural typing"));
  }

  @Test
  public void testConstantProperty_fromExternsOrIjs_duplicateExternOk() {
    testSame(
        externs(
            lines(
                "/** @constructor */ function Foo() {}", //
                "/** @const */ Foo.prototype.PROP;",
                "/** @const */ Foo.prototype.PROP;")),
        srcs(""));
  }

  @Test
  public void testConstantProperty_fromExternsOrIjs() {
    test(
        externs(
            lines(
                "/** @constructor */ function Foo() {}", //
                "/** @const */ Foo.prototype.PROP;")),
        srcs(
            lines(
                "var f = new Foo();", //
                "f.PROP = 1;")),
        error(CONST_PROPERTY_REASSIGNED_VALUE).withMessageContaining("at externs:2:"));
  }

  @Test
  public void testConstantProperty15() {
    testSame(
        "/** @constructor */ function Foo() {};\n"
            + "Foo.CONST = 100;\n"
            + "/** @type {Foo} */\n"
            + "var foo = new Foo();\n"
            + "/** @type {number} */\n"
            + "foo.CONST = Foo.CONST;");
  }

  @Test
  public void testConstantProperty_convention_not_enforced1() {
    testSame(
        "/** @constructor */ function Foo() { this.CONST = 100; };\n"
            + "/** @type {Foo} */\n"
            + "var foo = new Foo();\n"
            + "/** @type {number} */\n"
            + "foo.CONST = 0;");
  }

  @Test
  public void testConstantProperty_convention_not_enforced2() {
    testSame(
        "/** @constructor */ function Foo() {};\n"
            + "Foo.prototype.CONST = 100;\n"
            + "/** @type {Foo} */\n"
            + "var foo = new Foo();\n"
            + "/** @type {number} */\n"
            + "foo.CONST = 0;");
  }

  @Test
  public void testConstantProperty_convention_not_enforced3() {
    testSame(
        ""
            + "/** @constructor */ function Bar() {this.CONST = 100;};\n"
            + "/** @constructor \n @extends {Bar} */ function Foo() {};\n"
            + "/** @type {Foo} */\n"
            + "var foo = new Foo();\n"
            + "/** @type {number} */\n"
            + "foo.CONST = 0;");
  }

  @Test
  public void testConstantProperty16() {
    testSame(
        "/** @constructor */ function Foo() {};\n"
            + "Foo.CONST = 100;\n"
            + "/** @constructor */ function Bar() {};\n"
            + "Bar.CONST = 100;\n");
  }

  @Test
  public void testConstantProperty17() {
    testSame(
        "function Foo() {};\n"
            + "Foo.CONST = 100;\n"
            + "function Bar() {};\n"
            + "Bar.CONST = 100;\n");
  }

  @Test
  public void testConstantProperty18() {
    testSame(
        "/** @param {string} a */\n"
            + "function Foo(a) {};\n"
            + "Foo.CONST = 100;\n"
            + "/** @param {string} a */\n"
            + "function Bar(a) {};\n"
            + "Bar.CONST = 100;\n");
  }

  @Test
  public void testConstantProperty19() {
    testSame(
        "/** @param {string} a */\n"
            + "function Foo(a) {};\n"
            + "Foo.CONST = 100;\n"
            + "/** @param {number} a */\n"
            + "function Bar(a) {};\n"
            + "Bar.CONST = 100;\n");
  }

  @Test
  public void testNamespaceOnStructuralInterface() {
    // NOTE: The FooStatic pattern is not uncommon in TS externs.  If additional sub-namespaces are
    // defined underneath, tsickle generates code that looks like this.  It is important that the
    // moment.unitOfTime declaration be treated as a declaration so that it doesn't just look like
    // a mutation of the readonly property that it just defined.
    testNoWarning(
        lines(
            "/** @externs */",
            "/** @record */",
            "function MomentStatic() {}",
            "/** @return {number} */",
            "MomentStatic.prototype.now = function() {};",
            "/** @const */",
            "moment.unitOfTime = {};",
            "/** @const {!MomentStatic} */",
            "var moment;"));
  }

  @Test
  public void testConstantPropertyOnStructuralInterfaceByJsdoc_initializeFromObjLitOk() {
    testNoWarning(
        lines(
            "/** @record */",
            "function Foo() {",
            "  /** @const {number} */ this.bar;",
            "}",
            "var /** !Foo */ foo = {bar: 1};",
            "var /** !Foo */ baz = {bar: 2};"));
  }

  @Test
  public void testConstantPropertyOnStructuralInterfaceByJsdoc_reassignmentWarns() {
    testError(
        lines(
            "/** @record */",
            "function Foo() {",
            "  /** @const {number} */ this.bar;",
            "}",
            "var /** !Foo */ foo = {bar: 1};",
            "foo.bar = 2;"),
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testConstantPropertyOnStructuralInterfaceByJsdoc_assignmentOnOpaqueObjectWarns() {
    testError(
        lines(
            "/** @record */",
            "function Foo() {",
            "  /** @const {number} */ this.bar;",
            "}",
            "function f(/** !Foo */ foo) {",
            "  foo.bar = 2;",
            "}"),
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  @Test
  public void testConstantPropertyOnStructuralInterfaceByConvention_noReassignmentWarns() {
    testSame(
        lines(
            "/** @record */",
            "function Foo() {",
            "  /** @type {number} */ this.BAR;",
            "}",
            "var /** !Foo */ foo = {BAR: 1};",
            "foo.BAR = 2;"));
  }

  @Test
  public void testFunctionWithNewType_canReturnFinalClass() {
    testNoWarning(
        lines(
            "goog.forwardDeclare('Parent');",
            "/** @type {function(new: Parent): !Parent} */",
            "const PatchedParent = function Parent() { return /** @type {?} */ (0); }"));

    testNoWarning(
        lines(
            "/** @constructor @final */",
            "const Parent = function() {}",
            "/** @type {function(new: Parent): !Parent} */",
            "const PatchedParent = function Parent() { return /** @type {?} */ (0); }"));
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

  @Test
  public void testCheckFinalClass_prototypeMethodMarkedCtor_butNotOwnerFunction_doesNotCrash() {
    // Covers an edge case reported in b/129361702.
    testSame(
        lines(
            "function Foo() {}",
            "",
            "Foo.prototype = {",
            "  /** @constructor */",
            "  init: function() { }",
            "};"));
  }
}
