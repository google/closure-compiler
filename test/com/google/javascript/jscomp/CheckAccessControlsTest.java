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

/**
 * Tests for {@link CheckAccessControls}.
 *
 * @author nicksantos@google.com (Nick Santos)
 */

public final class CheckAccessControlsTest extends CompilerTestCase {
  public CheckAccessControlsTest() {
    super(CompilerTypeTestCase.DEFAULT_EXTERNS);
    parseTypeInfo = true;
    enableClosurePass();
    enableTypeCheck();
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
    options.setWarningLevel(DiagnosticGroups.CONSTANT_PROPERTY, CheckLevel.ERROR);
    return options;
  }

  private void testDepName(String js, String errorMessage) {
    test(js, null, DEPRECATED_NAME_REASON, null, errorMessage);
  }

  private void testDepProp(String js, String errorMessage) {
    test(js, null, DEPRECATED_PROP_REASON, null, errorMessage);
  }

  private void testDepClass(String js, String errorMessage) {
    test(js, null, DEPRECATED_CLASS_REASON, null, errorMessage);
  }

  public void testDeprecatedFunctionNoReason() {
    testError("/** @deprecated */ function f() {} function g() { f(); }", DEPRECATED_NAME);
  }

  public void testDeprecatedFunction() {
    testDepName(
        "/** @deprecated Some Reason */ function f() {} function g() { f(); }",
        "Variable f has been deprecated: Some Reason");
  }

  public void testWarningOnDeprecatedConstVariable() {
    testDepName(
        "/** @deprecated Another reason */ var f = 4; function g() { alert(f); }",
        "Variable f has been deprecated: Another reason");
  }

  public void testThatNumbersArentDeprecated() {
    testSame("/** @deprecated */ var f = 4; var h = 3; function g() { alert(h); }");
  }

  public void testDeprecatedFunctionVariable() {
    testDepName(
        "/** @deprecated I like g... */ var f = function() {}; function g() { f(); }",
        "Variable f has been deprecated: I like g...");
  }

  public void testNoWarningInGlobalScope() {
    testSame("var goog = {}; goog.makeSingleton = function(x) {};"
        + "/** @deprecated */ function f() {} goog.makeSingleton(f);");
  }

  public void testNoWarningInGlobalScopeForCall() {
    testDepName(
        "/** @deprecated Some global scope */ function f() {} f();",
        "Variable f has been deprecated: Some global scope");
  }

  public void testNoWarningInDeprecatedFunction() {
    testSame("/** @deprecated */ function f() {} /** @deprecated */ function g() { f(); }");
  }

  public void testWarningInNormalClass() {
    testDepName(
        "/** @deprecated FooBar */ function f() {}"
            + "/** @constructor */  var Foo = function() {}; "
            + "Foo.prototype.bar = function() { f(); }",
        "Variable f has been deprecated: FooBar");
  }

  public void testWarningForProperty1() {
    testDepProp(
        "/** @constructor */ function Foo() {}"
            + "/** @deprecated A property is bad */ Foo.prototype.bar = 3;"
            + "Foo.prototype.baz = function() { alert((new Foo()).bar); };",
        "Property bar of type Foo has been deprecated: A property is bad");
  }

  public void testWarningForProperty2() {
    testDepProp(
        "/** @constructor */ function Foo() {}"
            + "/** @deprecated Zee prop, it is deprecated! */ Foo.prototype.bar = 3;"
            + "Foo.prototype.baz = function() { alert(this.bar); };",
        "Property bar of type Foo has been deprecated: Zee prop, it is deprecated!");
  }

  public void testWarningForDeprecatedClass() {
    testDepClass(
        "/** @constructor \n* @deprecated Use the class 'Bar' */ function Foo() {} "
            + "function f() { new Foo(); }",
        "Class Foo has been deprecated: Use the class 'Bar'");
  }

  public void testWarningForDeprecatedClassNoReason() {
    testError(
        "/** @constructor \n* @deprecated */ function Foo() {} " + "function f() { new Foo(); }",
        DEPRECATED_CLASS);
  }

  public void testNoWarningForDeprecatedClassInstance() {
    testSame("/** @constructor \n * @deprecated */ function Foo() {} "
        + "/** @param {Foo} x */ function f(x) { return x; }");
  }

  public void testWarningForDeprecatedSuperClass() {
    testDepClass(
        "/** @constructor \n * @deprecated Superclass to the rescue! */ function Foo() {} "
            + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
            + "function f() { new SubFoo(); }",
        "Class SubFoo has been deprecated: Superclass to the rescue!");
  }

  public void testWarningForDeprecatedSuperClass2() {
    testDepClass(
        "/** @constructor \n * @deprecated Its only weakness is Kryptoclass */ function Foo() {} "
            + "var namespace = {}; "
            + "/** @constructor \n * @extends {Foo} */ "
            + "namespace.SubFoo = function() {}; "
            + "function f() { new namespace.SubFoo(); }",
        "Class namespace.SubFoo has been deprecated: Its only weakness is Kryptoclass");
  }

  public void testWarningForPrototypeProperty() {
    testDepProp(
        "/** @constructor */ function Foo() {}"
            + "/** @deprecated It is now in production, use that model... */ Foo.prototype.bar = 3;"
            + "Foo.prototype.baz = function() { alert(Foo.prototype.bar); };",
        "Property bar of type Foo.prototype has been deprecated:"
            + " It is now in production, use that model...");
  }

  public void testNoWarningForNumbers() {
    testSame("/** @constructor */ function Foo() {}"
        + "/** @deprecated */ Foo.prototype.bar = 3;"
        + "Foo.prototype.baz = function() { alert(3); };");
  }

  public void testWarningForMethod1() {
    testDepProp(
        "/** @constructor */ function Foo() {}"
            + "/** @deprecated There is a madness to this method */"
            + "Foo.prototype.bar = function() {};"
            + "Foo.prototype.baz = function() { this.bar(); };",
        "Property bar of type Foo has been deprecated: There is a madness to this method");
  }

  public void testWarningForMethod2() {
    testDepProp(
        "/** @constructor */ function Foo() {} "
            + "/** @deprecated Stop the ringing! */ Foo.prototype.bar; "
            + "Foo.prototype.baz = function() { this.bar(); };",
        "Property bar of type Foo has been deprecated: Stop the ringing!");
  }

  public void testNoWarningInDeprecatedClass() {
    testSame("/** @deprecated */ function f() {} "
        + "/** @constructor \n * @deprecated */ "
        + "var Foo = function() {}; "
        + "Foo.prototype.bar = function() { f(); }");
  }

  public void testNoWarningOnDeclaration() {
    testSame("/** @constructor */ function F() {\n"
        + "  /**\n"
        + "   * @type {number}\n"
        + "   * @deprecated Use something else.\n"
        + "   */\n"
        + "  this.code;\n"
        + "}");
  }

  public void testNoWarningInDeprecatedClass2() {
    testSame("/** @deprecated */ function f() {} "
        + "/** @constructor \n * @deprecated */ "
        + "var Foo = function() {}; "
        + "Foo.bar = function() { f(); }");
  }

  public void testNoWarningInDeprecatedStaticMethod() {
    testSame("/** @deprecated */ function f() {} "
        + "/** @constructor */ "
        + "var Foo = function() {}; "
        + "/** @deprecated */ Foo.bar = function() { f(); }");
  }

  public void testWarningInStaticMethod() {
    testDepName(
        "/** @deprecated crazy! */ function f() {} "
            + "/** @constructor */ "
            + "var Foo = function() {}; "
            + "Foo.bar = function() { f(); }",
        "Variable f has been deprecated: crazy!");
  }

  public void testDeprecatedObjLitKey() {
    testDepProp(
        "var f = {};"
            + "/** @deprecated It is literally not used anymore */ f.foo = 3;"
            + "function g() { return f.foo; }",
        "Property foo of type f has been deprecated: It is literally not used anymore");
  }

  public void testWarningForSubclassMethod() {
    testDepProp(
        "/** @constructor */ function Foo() {}"
            + "Foo.prototype.bar = function() {};"
            + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
            + "/** @deprecated I have a parent class! */ SubFoo.prototype.bar = function() {};"
            + "function f() { (new SubFoo()).bar(); };",
        "Property bar of type SubFoo has been deprecated: I have a parent class!");
  }

  public void testWarningForSuperClassWithDeprecatedSubclassMethod() {
    testSame("/** @constructor */ function Foo() {}"
        + "Foo.prototype.bar = function() {};"
        + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
        + "/** @deprecated \n * @override */ SubFoo.prototype.bar = "
        + "function() {};"
        + "function f() { (new Foo()).bar(); };");
  }

  public void testWarningForSuperclassMethod() {
    testDepProp(
        "/** @constructor */ function Foo() {}"
            + "/** @deprecated I have a child class! */ Foo.prototype.bar = function() {};"
            + "/** @constructor \n * @extends {Foo} */ function SubFoo() {}"
            + "SubFoo.prototype.bar = function() {};"
            + "function f() { (new SubFoo()).bar(); };",
        "Property bar of type SubFoo has been deprecated: I have a child class!");
  }

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

  public void testWarningForBind() {
    testDepProp(
        "/** @deprecated I'm bound to this method... */ Function.prototype.bind = function() {};"
            + "(function() {}).bind();",
        "Property bind of type function has been deprecated: I'm bound to this method...");
  }

  public void testWarningForDeprecatedClassInGlobalScope() {
    testDepClass(
        "/** @constructor \n * @deprecated I'm a very worldly object! */ var Foo = function() {};"
            + "new Foo();",
        "Class Foo has been deprecated: I'm a very worldly object!");
  }

  public void testNoWarningForPrototypeCopying() {
    testSame("/** @constructor */ var Foo = function() {};"
        + "Foo.prototype.bar = function() {};"
        + "/** @deprecated */ Foo.prototype.baz = Foo.prototype.bar;"
        + "(new Foo()).bar();");
  }

  public void testNoWarningOnDeprecatedPrototype() {
    // This used to cause an NPE.
    testSame("/** @constructor */ var Foo = function() {};"
        + "/** @deprecated */ Foo.prototype = {};"
        + "Foo.prototype.bar = function() {};");
  }

  public void testPrivateAccessForNames() {
    testSame("/** @private */ function foo_() {}; foo_();");
    test(new String[] {"/** @private */ function foo_() {};", "foo_();"}, null,
        BAD_PRIVATE_GLOBAL_ACCESS);
  }

  public void testPrivateAccessForNames2() {
    // Private by convention
    testSame("function foo_() {}; foo_();");
    test(new String[] {"function foo_() {};", "foo_();"}, null, BAD_PRIVATE_GLOBAL_ACCESS);
  }

  public void testPrivateAccessForProperties1() {
    testSame("/** @constructor */ function Foo() {}"
        + "/** @private */ Foo.prototype.bar_ = function() {};"
        + "Foo.prototype.baz = function() { this.bar_(); }; (new Foo).bar_();");
  }

  public void testPrivateAccessForProperties2() {
    testSame(new String[] {
        "/** @constructor */ function Foo() {}",
        "/** @private */ Foo.prototype.bar_ = function() {};"
        + "Foo.prototype.baz = function() { this.bar_(); }; (new Foo).bar_();"});
  }

  public void testPrivateAccessForProperties3() {
    testSame(new String[] {
        "/** @constructor */ function Foo() {}"
        + "/** @private */ Foo.prototype.bar_ = function() {}; (new Foo).bar_();",
        "Foo.prototype.baz = function() { this.bar_(); };"});
  }

  public void testPrivateAccessForProperties4() {
    testSame(new String[] {
        "/** @constructor */ function Foo() {}"
        + "/** @private */ Foo.prototype.bar_ = function() {};",
        "Foo.prototype['baz'] = function() { (new Foo()).bar_(); };"});
  }

  public void testPrivateAccessForProperties4a() {
    // Identical to 4 except the computed access
    testSame(new String[] {
        "/** @constructor */ function Foo() {}"
        + "/** @private */ Foo.prototype.bar_ = function() {};",
        "Foo.prototype.baz = function() { (new Foo()).bar_(); };"});
  }

  public void testPrivateAccessForProperties5() {
    test(new String[] {
        "/** @constructor */\n"
        + "function Parent () {\n"
        + "  /** @private */\n"
        + "  this.prop = 'foo';\n"
        + "};",
        "/**\n"
        + " * @constructor\n"
        + " * @extends {Parent}\n"
        + " */\n"
        + "function Child() {\n"
        + "  this.prop = 'asdf';\n"
        + "}\n"
        + "Child.prototype = new Parent();"},
        null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForProperties1() {
    test(new String[] {
        "/** @constructor */ function Foo() {} (new Foo).bar_();",
        "/** @private */ Foo.prototype.bar_ = function() {};"
        + "Foo.prototype.baz = function() { this.bar_(); };"},
        null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForProperties2() {
    test(new String[] {
        "/** @constructor */ function Foo() {} "
        + "/** @private */ Foo.prototype.bar_ = function() {};"
        + "Foo.prototype.baz = function() { this.bar_(); };",
        "(new Foo).bar_();"},
        null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForProperties3() {
    test(new String[] {
        "/** @constructor */ function Foo() {} "
        + "/** @private */ Foo.prototype.bar_ = function() {};",
        "/** @constructor */ function OtherFoo() { (new Foo).bar_(); }"},
        null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForProperties4() {
    test(new String[] {
        "/** @constructor */ function Foo() {} "
        + "/** @private */ Foo.prototype.bar_ = function() {};",
        "/** @constructor \n * @extends {Foo} */ "
        + "function SubFoo() { this.bar_(); }"},
        null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForProperties5() {
    test(new String[] {
        "/** @constructor */ function Foo() {} "
        + "/** @private */ Foo.prototype.bar_ = function() {};",
        "/** @constructor \n * @extends {Foo} */ "
        + "function SubFoo() {};"
        + "SubFoo.prototype.baz = function() { this.bar_(); }"},
        null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForProperties6() {
    // Overriding a private property with a non-private property
    // in a different file causes problems.
    test(new String[] {
        "/** @constructor */ function Foo() {} "
        + "/** @private */ Foo.prototype.bar_ = function() {};",
        "/** @constructor \n * @extends {Foo} */ "
        + "function SubFoo() {};"
        + "SubFoo.prototype.bar_ = function() {};"},
        null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForProperties7() {
    // It's OK to override a private property with a non-private property
    // in the same file, but you'll get yelled at when you try to use it.
    test(new String[] {
        "/** @constructor */ function Foo() {} "
        + "/** @private */ Foo.prototype.bar_ = function() {};"
        + "/** @constructor \n * @extends {Foo} */ "
        + "function SubFoo() {};"
        + "SubFoo.prototype.bar_ = function() {};",
        "SubFoo.prototype.baz = function() { this.bar_(); }"},
        null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForProperties8() {
    test(new String[] {
        "/** @constructor */ function Foo() { /** @private */ this.bar_ = 3; }",
        "/** @constructor \n * @extends {Foo} */ "
        + "function SubFoo() { /** @private */ this.bar_ = 3; };"},
        null, PRIVATE_OVERRIDE);
  }

  public void testNoPrivateAccessForProperties9() {
    test(new String[] {
        "/** @constructor */ function Foo() {}"
        + "Foo.prototype = {"
        + "/** @private */ bar_: 3"
        + "}",
        "new Foo().bar_;"},
        null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForProperties10() {
    test(new String[] {
        "/** @constructor */ function Foo() {}"
        + "Foo.prototype = {"
        + "/** @private */ bar_: function() {}"
        + "}",
        "new Foo().bar_();"},
        null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForProperties11() {
    test(new String[] {
        "/** @constructor */ function Foo() {}"
        + "Foo.prototype = {"
        + "/** @private */ get bar_() { return 1; }"
        + "}",
        "var a = new Foo().bar_;"},
        null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForProperties12() {
    test(new String[] {
        "/** @constructor */ function Foo() {}"
        + "Foo.prototype = {"
        + "/** @private */ set bar_(x) { this.barValue = x; }"
        + "}",
        "new Foo().bar_ = 1;"},
        null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForNamespaces() {
    test(new String[] {
        "var foo = {};\n"
        + "/** @private */ foo.bar_ = function() {};",
        "foo.bar_();"},
        null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testProtectedAccessForProperties1() {
    testSame(new String[] {
        "/** @constructor */ function Foo() {}"
        + "/** @protected */ Foo.prototype.bar = function() {};"
        + "(new Foo).bar();",
        "Foo.prototype.baz = function() { this.bar(); };"});
  }

  public void testProtectedAccessForProperties2() {
    testSame(new String[] {
        "/** @constructor */ function Foo() {}"
        + "/** @protected */ Foo.prototype.bar = function() {};"
        + "(new Foo).bar();",
        "/** @constructor \n * @extends {Foo} */"
        + "function SubFoo() { this.bar(); }"});
  }

  public void testProtectedAccessForProperties3() {
    testSame(new String[] {
        "/** @constructor */ function Foo() {}"
        + "/** @protected */ Foo.prototype.bar = function() {};"
        + "(new Foo).bar();",
        "/** @constructor \n * @extends {Foo} */"
        + "function SubFoo() { }"
        + "SubFoo.baz = function() { (new Foo).bar(); }"});
  }

  public void testProtectedAccessForProperties4() {
    testSame(new String[] {
        "/** @constructor */ function Foo() {}"
        + "/** @protected */ Foo.bar = function() {};",
        "/** @constructor \n * @extends {Foo} */"
        + "function SubFoo() { Foo.bar(); }"});
  }

  public void testProtectedAccessForProperties5() {
    testSame(new String[] {
        "/** @constructor */ function Foo() {}"
        + "/** @protected */ Foo.prototype.bar = function() {};"
        + "(new Foo).bar();",
        "/** @constructor \n * @extends {Foo} */"
        + "var SubFoo = function() { this.bar(); }"});
  }

  public void testProtectedAccessForProperties6() {
    testSame(new String[] {
        "/** @const */ var goog = {};"
        + "/** @constructor */ goog.Foo = function() {};"
        + "/** @protected */ goog.Foo.prototype.bar = function() {};",
        "/** @constructor \n * @extends {goog.Foo} */"
        + "goog.SubFoo = function() { this.bar(); };"});
  }

  public void testProtectedAccessForProperties7() {
    testSame(new String[] {
        "/** @constructor */ var Foo = function() {};"
        + "Foo.prototype = { /** @protected */ bar: function() {} }",
        "/** @constructor \n * @extends {Foo} */"
        + "var SubFoo = function() { this.bar(); };"
        + "SubFoo.prototype = { moo: function() { this.bar(); }};"});
  }

  public void testProtectedAccessForProperties8() {
    testSame(new String[] {
        "/** @constructor */ var Foo = function() {};"
        + "Foo.prototype = { /** @protected */ bar: function() {} }",
        "/** @constructor \n * @extends {Foo} */"
        + "var SubFoo = function() {};"
        + "SubFoo.prototype = { get moo() { this.bar(); }};"});
  }

  public void testProtectedAccessForProperties9() {
    testSame(new String[] {
        "/** @constructor */ var Foo = function() {};"
        + "Foo.prototype = { /** @protected */ bar: function() {} }",
        "/** @constructor \n * @extends {Foo} */"
        + "var SubFoo = function() {};"
        + "SubFoo.prototype = { set moo(val) { this.x = this.bar(); }};"});
  }

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

  public void testNoProtectedAccessForProperties1() {
    test(new String[] {
        "/** @constructor */ function Foo() {} "
        + "/** @protected */ Foo.prototype.bar = function() {};",
        "(new Foo).bar();"},
        null, BAD_PROTECTED_PROPERTY_ACCESS);
  }

  public void testNoProtectedAccessForProperties2() {
    test(new String[] {
        "/** @constructor */ function Foo() {} "
        + "/** @protected */ Foo.prototype.bar = function() {};",
        "/** @constructor */ function OtherFoo() { (new Foo).bar(); }"},
        null, BAD_PROTECTED_PROPERTY_ACCESS);
  }

  public void testNoProtectedAccessForProperties3() {
    test(new String[] {
        "/** @constructor */ function Foo() {} "
        + "/** @constructor \n * @extends {Foo} */ "
        + "function SubFoo() {}"
        + "/** @protected */ SubFoo.prototype.bar = function() {};",
        "/** @constructor \n * @extends {Foo} */ "
        + "function SubberFoo() { (new SubFoo).bar(); }"},
        null, BAD_PROTECTED_PROPERTY_ACCESS);
  }

  public void testNoProtectedAccessForProperties4() {
    test(new String[] {
        "/** @constructor */ function Foo() { (new SubFoo).bar(); } ",
        "/** @constructor \n * @extends {Foo} */ "
        + "function SubFoo() {}"
        + "/** @protected */ SubFoo.prototype.bar = function() {};",
         },
        null, BAD_PROTECTED_PROPERTY_ACCESS);
  }

  public void testNoProtectedAccessForProperties5() {
    test(new String[] {
        "/** @const */ var goog = {};"
        + "/** @constructor */ goog.Foo = function() {};"
        + "/** @protected */ goog.Foo.prototype.bar = function() {};",
        "/** @constructor */"
        + "goog.NotASubFoo = function() { (new goog.Foo).bar(); };"},
        null, BAD_PROTECTED_PROPERTY_ACCESS);
  }

  public void testNoProtectedAccessForProperties6() {
    test(new String[] {
        "/** @constructor */ function Foo() {}"
        + "Foo.prototype = {"
        + "/** @protected */ bar: 3"
        + "}",
        "new Foo().bar;"},
        null, BAD_PROTECTED_PROPERTY_ACCESS);
  }

  public void testNoProtectedAccessForProperties7() {
    test(new String[] {
        "/** @constructor */ function Foo() {}"
        + "Foo.prototype = {"
        + "/** @protected */ bar: function() {}"
        + "}",
        "new Foo().bar();"},
        null, BAD_PROTECTED_PROPERTY_ACCESS);
  }

  public void testPackagePrivateAccessForNames() {
    test(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @constructor */\n"
                + "function Parent() {\n"
                + "/** @package */\n"
                + "this.prop = 'foo';\n"
                + "}\n;"),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "/**"
                + " * @constructor\n"
                + " * @extends {Parent}\n"
                + " */\n"
                + "function Child() {\n"
                + "  this.prop = 'asdf';\n"
                + "}\n"
                + "Child.prototype = new Parent();")),
        null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testPackagePrivateAccessForProperties1() {
    testSame("/** @constructor */ function Foo() {}"
        + "/** @package */ Foo.prototype.bar = function() {};"
        + "Foo.prototype.baz = function() { this.bar(); }; (new Foo).bar();");
  }

  public void testPackagePrivateAccessForProperties2() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(Compiler.joinPathParts("foo", "bar.js"),
            "/** @constructor */ function Foo() {}"),
        SourceFile.fromCode(
            Compiler.joinPathParts("baz", "quux.js"),
            "/** @package */ Foo.prototype.bar = function() {};"
            + "Foo.prototype.baz = function() { this.bar(); }; (new Foo).bar();")));
  }

  public void testPackagePrivateAccessForProperties3() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            Compiler.joinPathParts("foo", "bar.js"),
            "/** @constructor */ function Foo() {}"
            + "/** @package */ Foo.prototype.bar = function() {}; (new Foo).bar();"),
        SourceFile.fromCode(Compiler.joinPathParts("foo", "baz.js"),
            "Foo.prototype.baz = function() { this.bar(); };")));
  }

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

  public void testPackagePrivateAccessForProperties5() {
    test(
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
        null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testNoPackagePrivateAccessForProperties1() {
    test(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @constructor */ function Foo() {} (new Foo).bar();"),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "/** @package */ Foo.prototype.bar = function() {};"
                + "Foo.prototype.baz = function() { this.bar(); };")),
        null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testNoPackagePrivateAccessForProperties2() {
    test(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @constructor */ function Foo() {} "
                + "/** @package */ Foo.prototype.bar = function() {};"
                + "Foo.prototype.baz = function() { this.bar(); };"),
            SourceFile.fromCode(Compiler.joinPathParts("baz", "quux.js"), "(new Foo).bar();")),
        null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testNoPackagePrivateAccessForProperties3() {
    test(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @constructor */ function Foo() {} "
                + "/** @package */ Foo.prototype.bar = function() {};"),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "/** @constructor */ function OtherFoo() { (new Foo).bar(); }")),
        null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testNoPackagePrivateAccessForProperties4() {
    test(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @constructor */ function Foo() {} "
                + "/** @package */ Foo.prototype.bar = function() {};"),
            SourceFile.fromCode(
                Compiler.joinPathParts("baz", "quux.js"),
                "/** @constructor \n * @extends {Foo} */ "
                + "function SubFoo() { this.bar(); }")),
        null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testNoPackagePrivateAccessForNamespaces() {
    test(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/** @const */ var foo = {};\n"
                + "/** @package */ foo.bar = function() {};"),
            SourceFile.fromCode(Compiler.joinPathParts("baz", "quux.js"), "foo.bar();")),
        null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testNoPackagePrivateAccessForProperties5() {
    test(
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
        null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testNoPackagePrivateAccessForProperties6() {
    // Overriding a private property with a non-package-private property
    // in a different file causes problems.
    test(
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
        null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testNoPackagePrivateAccessForProperties7() {
    // It's OK to override a package-private property with a
    // non-package-private property in the same file, but you'll get
    // yelled at when you try to use it.
    test(
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
        null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

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

  public void testNamespacedFunctionDoesNotNeedVisibilityRedeclInFileWithFileOverviewVisibility() {
    testSame("/**\n"
        + " * @fileoverview\n"
        + " * @package\n"
        + " */\n"
        + "/** @return {string} */\n"
        + "foo.bar = function() { return 'asdf'; };");
  }

  public void
      testOverrideWithoutVisibilityRedeclInFileWithFileOverviewVisibilityNotAllowed_TwoFiles() {
    test(new String[] {
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
        null, BAD_PROPERTY_OVERRIDE_IN_FILE_WITH_FILEOVERVIEW_VISIBILITY);
  }

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

  public void testPublicFileOverviewVisibilityDoesNotApplyToNameWithExplicitPackageVisibility() {
    test(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/**\n"
                + " * @fileoverview\n"
                + " * @public\n"
                + " */\n"
                + "/** @constructor @package */ function Foo() {};"),
            SourceFile.fromCode(Compiler.joinPathParts("baz", "quux.js"), "new Foo();")),
        null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

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

  public void testPackageFileOverviewVisibilityAppliesToNameWithoutExplicitVisibility() {
    test(
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
        null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

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

  public void testFileoverviewVisibilityDoesNotApplyToGoogProvidedNamespace1() {
    // Don't compare the generated JsDoc. It includes annotations we're not interested in,
    // like @inherited.
    compareJsDoc = false;

    test(
        ImmutableList.of(
            SourceFile.fromCode("foo.js", "goog.provide('foo');"),
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/**\n"
                + "  * @fileoverview\n"
                + "  * @package\n"
                + "  */\n"
                + "goog.provide('foo.bar');"),
            SourceFile.fromCode("bar.js", "goog.require('foo')")),
        ImmutableList.of(SourceFile.fromCode("foo.js", "var foo={};"),
            SourceFile.fromCode(Compiler.joinPathParts("foo", "bar.js"), "foo.bar={};"),
            SourceFile.fromCode("bar.js", "")),
        null, null);

    compareJsDoc = true;
  }

  public void testFileoverviewVisibilityDoesNotApplyToGoogProvidedNamespace2() {
    // Don't compare the generated JsDoc. It includes annotations we're not interested in,
    // like @inherited.
    compareJsDoc = false;

    test(
        ImmutableList.of(
            SourceFile.fromCode(
                Compiler.joinPathParts("foo", "bar.js"),
                "/**\n"
                + "  * @fileoverview\n"
                + "  * @package\n"
                + "  */\n"
                + "goog.provide('foo.bar');"),
            SourceFile.fromCode("foo.js", "goog.provide('foo');"),
            SourceFile.fromCode(
                "bar.js",
                "goog.require('foo');\n"
                + "var x = foo;")),
        ImmutableList.of(SourceFile.fromCode(Compiler.joinPathParts("foo", "bar.js"),
                "var foo={};foo.bar={};"),
            SourceFile.fromCode("foo.js", ""), SourceFile.fromCode("bar.js", "var x=foo")),
        null, null);

    compareJsDoc = true;
  }

  public void testFileoverviewVisibilityDoesNotApplyToGoogProvidedNamespace3() {
    // Don't compare the generated JsDoc. It includes annotations we're not interested in,
    // like @inherited.
    compareJsDoc = false;

    test(
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
                + "var x = one.two;")),
        ImmutableList.of(
            SourceFile.fromCode(Compiler.joinPathParts("foo", "bar.js"),
                "var one={};one.two={};one.two.three=function(){};"),
            SourceFile.fromCode("baz.js", "var x=one.two")),
        null, null);

    compareJsDoc = true;
  }

  public void testFileoverviewVisibilityDoesNotApplyToGoogProvidedNamespace4() {
    test(
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
        null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void
      testPublicFileOverviewVisibilityDoesNotApplyToPropertyWithExplicitPackageVisibility() {
    test(
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
        null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

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

  public void testPackageFileOverviewVisibilityAppliesToPropertyWithoutExplicitVisibility() {
    test(
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
        null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testFileOverviewVisibilityComesFromDeclarationFileNotUseFile() {
    test(
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
        null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testNoExceptionsWithBadConstructors1() {
    testSame(new String[] {"function Foo() { (new SubFoo).bar(); } "
        + "/** @constructor */ function SubFoo() {}"
        + "/** @protected */ SubFoo.prototype.bar = function() {};"});
  }

  public void testNoExceptionsWithBadConstructors2() {
    testSame(new String[] {"/** @constructor */ function Foo() {} "
        + "Foo.prototype.bar = function() {};"
        + "/** @constructor */"
        + "function SubFoo() {}"
        + "/** @protected */ "
        + "SubFoo.prototype.bar = function() { (new Foo).bar(); };"});
  }

  public void testGoodOverrideOfProtectedProperty() {
    testSame(new String[] {
        "/** @constructor */ function Foo() { } "
        + "/** @protected */ Foo.prototype.bar = function() {};",
        "/** @constructor \n * @extends {Foo} */ "
        + "function SubFoo() {}"
        + "/** @inheritDoc */ SubFoo.prototype.bar = function() {};",
    });
  }

  public void testBadOverrideOfProtectedProperty() {
    test(new String[] {
        "/** @constructor */ function Foo() { } "
        + "/** @protected */ Foo.prototype.bar = function() {};",
        "/** @constructor \n * @extends {Foo} */ "
        + "function SubFoo() {}"
        + "/** @private */ SubFoo.prototype.bar = function() {};",
         },
        null, VISIBILITY_MISMATCH);
  }

  public void testBadOverrideOfPrivateProperty() {
    test(new String[] {
        "/** @constructor */ function Foo() { } "
        + "/** @private */ Foo.prototype.bar = function() {};",
        "/** @constructor \n * @extends {Foo} */ "
        + "function SubFoo() {}"
        + "/** @protected */ SubFoo.prototype.bar = function() {};",
         },
        null, PRIVATE_OVERRIDE);

    testSame(new String[] {
        "/** @constructor */ function Foo() { } "
        + "/** @private */ Foo.prototype.bar = function() {};",
        "/** @constructor \n * @extends {Foo} */ "
        + "function SubFoo() {}"
        + "/** @override \n *@suppress{visibility} */\n"
        + " SubFoo.prototype.bar = function() {};",
    });
  }

  public void testAccessOfStaticMethodOnPrivateConstructor() {
    testSame(new String[] {
        "/** @constructor \n * @private */ function Foo() { } "
        + "Foo.create = function() { return new Foo(); };",
        "Foo.create()",
    });
  }

  public void testAccessOfStaticMethodOnPrivateQualifiedConstructor() {
    testSame(new String[] {
        "/** @const */ var goog = {};"
        + "/** @constructor \n * @private */ goog.Foo = function() { }; "
        + "goog.Foo.create = function() { return new goog.Foo(); };",
        "goog.Foo.create()",
    });
  }

  public void testInstanceofOfPrivateConstructor() {
    testSame(new String[] {
        "/** @const */ var goog = {};"
        + "/** @constructor \n * @private */ goog.Foo = function() { }; "
        + "goog.Foo.create = function() { return new goog.Foo(); };",
        "goog instanceof goog.Foo",
    });
  }

  public void testOkAssignmentOfDeprecatedProperty() {
    testSame("/** @constructor */ function Foo() {"
        + " /** @deprecated */ this.bar = 3;"
        + "}");
  }

  public void testBadReadOfDeprecatedProperty() {
    testDepProp(
        "/** @constructor */ function Foo() {"
            + " /** @deprecated GRR */ this.bar = 3;"
            + "  this.baz = this.bar;"
            + "}",
        "Property bar of type Foo has been deprecated: GRR");
  }

  public void testAutoboxedDeprecatedProperty() {
    test("", // no externs
        "/** @constructor */ function String() {}"
        + "/** @deprecated %s */ String.prototype.length;"
        + "function f() { return 'x'.length; }",
        (String) null, DEPRECATED_PROP_REASON, null);
  }

  public void testAutoboxedPrivateProperty() {
    test(
        "/** @constructor */ function String() {}"
        + "/** @private */ String.prototype.length;",
        // externs
        "function f() { return 'x'.length; }",
        (String) null, // no output
        BAD_PRIVATE_PROPERTY_ACCESS, null);
  }

  public void testNullableDeprecatedProperty() {
    testError(
        "/** @constructor */ function Foo() {}"
        + "/** @deprecated */ Foo.prototype.length;"
        + "/** @param {?Foo} x */ function f(x) { return x.length; }",
        DEPRECATED_PROP);
  }

  public void testNullablePrivateProperty() {
    test(new String[] {
        "/** @constructor */ function Foo() {}"
        + "/** @private */ Foo.prototype.length;",
        "/** @param {?Foo} x */ function f(x) { return x.length; }"},
        null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testPrivatePropertyByConvention1() {
    test(new String[] {
        "/** @constructor */ function Foo() {}\n"
        + "/** @type {number} */ Foo.prototype.length_;\n",
        "/** @param {?Foo} x */ function f(x) { return x.length_; }\n"},
        null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testPrivatePropertyByConvention2() {
    test(new String[] {
        "/** @constructor */ function Foo() {\n"
        + "  /** @type {number} */ this.length_ = 1;\n"
        + "}\n"
        + "/** @type {number} */ Foo.prototype.length_;\n",
        "/** @param {Foo} x */ function f(x) { return x.length_; }\n"},
        null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testDeclarationAndConventionConflict1() {
    testError(
        "/** @constructor */ function Foo() {} /** @protected */ Foo.prototype.length_;",
        CONVENTION_MISMATCH);
  }

  public void testDeclarationAndConventionConflict2() {
    testError(
        "/** @constructor */ function Foo() {}\n"
            + "/** @public {number} */ Foo.prototype.length_;\n",
        CONVENTION_MISMATCH);
  }

  public void testDeclarationAndConventionConflict3() {
    testError(
        "/** @constructor */ function Foo() {  /** @protected */ this.length_ = 1;\n}\n",
        CONVENTION_MISMATCH);
  }

  public void testDeclarationAndConventionConflict4a() {

    testError(
        "/** @constructor */ function Foo() {}"
            + "Foo.prototype = { /** @protected */ length_: 1 }\n"
            + "new Foo().length_",
        CONVENTION_MISMATCH);
  }

  public void testDeclarationAndConventionConflict4b() {

    testError(
        "/** @const */ var NS = {}; /** @constructor */ NS.Foo = function() {};"
            + "NS.Foo.prototype = { /** @protected */ length_: 1 };\n"
            + "(new NS.Foo()).length_;",
        CONVENTION_MISMATCH);
  }

  public void testDeclarationAndConventionConflict5() {

    testError(
        "/** @constructor */ function Foo() {}\n"
            + "Foo.prototype = { /** @protected */ get length_() { return 1; } }\n",
        CONVENTION_MISMATCH);
  }

  public void testDeclarationAndConventionConflict6() {

    testError(
        "/** @constructor */ function Foo() {}\n"
            + "Foo.prototype = { /** @protected */ set length_(x) { } }\n",
        CONVENTION_MISMATCH);
  }

  public void testDeclarationAndConventionConflict7() {
    testError("/** @public */ var Foo_;", CONVENTION_MISMATCH);
  }

  public void testDeclarationAndConventionConflict8() {
    testError("/** @package */ var Foo_;", CONVENTION_MISMATCH);
  }

  public void testDeclarationAndConventionConflict9() {
    testError("/** @protected */ var Foo_;", CONVENTION_MISMATCH);
  }

  public void testConstantProperty1a() {
    testError(
        "/** @constructor */ function A() {"
        + "/** @const */ this.bar = 3;}"
        + "/** @constructor */ function B() {"
        + "/** @const */ this.bar = 3;this.bar += 4;}",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testConstantProperty1b() {
    testError(
        "/** @constructor */ function A() {"
        + "this.BAR = 3;}"
        + "/** @constructor */ function B() {"
        + "this.BAR = 3;this.BAR += 4;}",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testConstantProperty2a() {
    testError(
        "/** @constructor */ function Foo() {}"
        + "/** @const */ Foo.prototype.prop = 2;"
        + "var foo = new Foo();"
        + "foo.prop = 3;",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testConstantProperty2b() {
    testError(
        "/** @constructor */ function Foo() {}"
        + "Foo.prototype.PROP = 2;"
        + "var foo = new Foo();"
        + "foo.PROP = 3;",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testConstantProperty3a() {
    testSame("/** @constructor */ function Foo() {}\n"
        + "/** @type {number} */ Foo.prototype.PROP = 2;\n"
        + "/** @suppress {duplicate|const} */ Foo.prototype.PROP = 3;\n");
  }

  public void testConstantProperty3b() {
    testSame("/** @constructor */ function Foo() {}\n"
        + "/** @const */ Foo.prototype.prop = 2;\n"
        + "/** @suppress {const} */ Foo.prototype.prop = 3;\n");
  }

  public void testNamespaceConstantProperty1() {
    testError(
        ""
        + "/** @const */ var o = {};\n"
        + "/** @const */ o.x = 1;"
        + "o.x = 2;",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testNamespaceConstantProperty2() {
    testError(
        "/** @const */ var o = {};\n"
        + "/** @const */ o.x = 1;\n"
        + "o.x = 2;\n",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testNamespaceConstantProperty2a() {
    testSame("/** @const */ var o = {};\n"
        + "/** @const */ o.x = 1;\n"
        + "/** @const */ var o2 = {};\n"
        + "/** @const */ o2.x = 1;\n");
  }

  public void testNamespaceConstantProperty3() {
    testError(
        "/** @const */ var o = {};\n"
        + "/** @const */ o.x = 1;"
        + "o.x = 2;",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testConstantProperty3a1() {
    testSame("var o = { /** @const */ x: 1 };"
        + "o.x = 2;");
  }

  public void testConstantProperty3a2() {
    // The old type checker should report this but it doesn't.
    testSame("/** @const */ var o = { /** @const */ x: 1 };"
        + "o.x = 2;");
  }

  public void testConstantProperty3b1() {
    // We should report this but we don't.
    testSame("var o = { XYZ: 1 };"
        + "o.XYZ = 2;");
  }

  public void testConstantProperty3b2() {
    // The old type checker should report this but it doesn't.
    testSame("/** @const */ var o = { XYZ: 1 };"
        + "o.XYZ = 2;");
  }

  public void testConstantProperty4() {
    testError(
        "/** @constructor */ function cat(name) {}"
        + "/** @const */ cat.test = 1;"
        + "cat.test *= 2;",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testConstantProperty4b() {
    testError(
        "/** @constructor */ function cat(name) {}"
        + "cat.TEST = 1;"
        + "cat.TEST *= 2;",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testConstantProperty5() {
    testError(
        "/** @constructor */ function Foo() { this.prop = 1;}"
        + "/** @const */ Foo.prototype.prop;"
        + "Foo.prototype.prop = 2",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testConstantProperty6() {
    testError(
        "/** @constructor */ function Foo() { this.prop = 1;}"
        + "/** @const */ Foo.prototype.prop = 2;",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testConstantProperty7() {
    testSame("/** @constructor */ function Foo() {} "
        + "Foo.prototype.bar_ = function() {};"
        + "/** @constructor \n * @extends {Foo} */ "
        + "function SubFoo() {};"
        + "/** @const */ /** @override */ SubFoo.prototype.bar_ = function() {};"
        + "SubFoo.prototype.baz = function() { this.bar_(); }");
  }

  public void testConstantProperty8() {
    testSame("/** @const */ var o = { /** @const */ x: 1 };"
        + "var y = o.x;");
  }

  public void testConstantProperty9() {
    testSame("/** @constructor */ function A() {"
        + "/** @const */ this.bar = 3;}"
        + "/** @constructor */ function B() {"
        + "this.bar = 4;}");
  }

  public void testConstantProperty10a() {
    testSame("/** @constructor */ function Foo() { this.prop = 1;}"
        + "/** @const */ Foo.prototype.prop;");
  }

  public void testConstantProperty10b() {
    testSame("/** @constructor */ function Foo() { this.PROP = 1;}"
        + "Foo.prototype.PROP;");
  }

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

  public void testConstantProperty14() {
    testError(
        "/** @constructor */ function Foo() {"
        + "/** @const */ this.bar = 3; delete this.bar; }",
        CONST_PROPERTY_DELETED);
  }

  public void testConstantPropertyInExterns() {
    String externs =
        ""
        + "/** @constructor */ function Foo() {};\n"
        + "/** @const */ Foo.prototype.PROP;";
    String js = "var f = new Foo(); f.PROP = 1; f.PROP = 2;";
    test(externs, js, (String) null, CONST_PROPERTY_REASSIGNED_VALUE, null);
  }

  public void testConstantProperty15() {
    testSame("/** @constructor */ function Foo() {};\n"
        + "Foo.CONST = 100;\n"
        + "/** @type {Foo} */\n"
        + "var foo = new Foo();\n"
        + "/** @type {number} */\n"
        + "foo.CONST = Foo.CONST;");
  }

  public void testConstantProperty15a() {
    testError(
        "/** @constructor */ function Foo() { this.CONST = 100; };\n"
        + "/** @type {Foo} */\n"
        + "var foo = new Foo();\n"
        + "/** @type {number} */\n"
        + "foo.CONST = 0;",
        CONST_PROPERTY_REASSIGNED_VALUE);
  }

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

  public void testConstantProperty16() {
    testSame("/** @constructor */ function Foo() {};\n"
        + "Foo.CONST = 100;\n"
        + "/** @constructor */ function Bar() {};\n"
        + "Bar.CONST = 100;\n");
  }

  public void testConstantProperty17() {
    testSame("function Foo() {};\n"
        + "Foo.CONST = 100;\n"
        + "function Bar() {};\n"
        + "Bar.CONST = 100;\n");
  }

  public void testConstantProperty18() {
    testSame("/** @param {string} a */\n"
        + "function Foo(a) {};\n"
        + "Foo.CONST = 100;\n"
        + "/** @param {string} a */\n"
        + "function Bar(a) {};\n"
        + "Bar.CONST = 100;\n");
  }

  public void testConstantProperty19() {
    testSame("/** @param {string} a */\n"
        + "function Foo(a) {};\n"
        + "Foo.CONST = 100;\n"
        + "/** @param {number} a */\n"
        + "function Bar(a) {};\n"
        + "Bar.CONST = 100;\n");
  }

  public void testSuppressConstantProperty() {
    testSame("/** @constructor */ function A() {"
        + "/** @const */ this.bar = 3;}"
        + "/**\n"
        + " * @suppress {constantProperty}\n"
        + " * @constructor\n"
        + " */ function B() { /** @const */ this.bar = 3; this.bar += 4; }");
  }

  public void testSuppressConstantProperty2() {
    testSame("/** @constructor */ function A() {"
        + "/** @const */ this.bar = 3;}"
        + "/**\n"
        + " * @suppress {const}\n"
        + " * @constructor\n"
        + " */ function B() {"
        + "/** @const */ this.bar = 3;this.bar += 4;}");
  }

  public void testFinalClassCannotBeSubclassed() {
    testError(
        "/**\n"
        + " * @constructor\n"
        + " * @const\n"
        + " */ Foo = function() {};\n"
        + "/**\n"
        + " * @constructor\n"
        + " * @extends {Foo}\n*"
        + " */ Bar = function() {};",
        EXTEND_FINAL_CLASS);
    testError(
        "/**\n"
        + " * @constructor\n"
        + " * @const\n"
        + " */ function Foo() {};\n"
        + "/**\n"
        + " * @constructor\n"
        + " * @extends {Foo}\n*"
        + " */ function Bar() {};",
        EXTEND_FINAL_CLASS);
  }
}
