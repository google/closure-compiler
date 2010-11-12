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

import static com.google.javascript.jscomp.CheckAccessControls.BAD_PRIVATE_GLOBAL_ACCESS;
import static com.google.javascript.jscomp.CheckAccessControls.BAD_PRIVATE_PROPERTY_ACCESS;
import static com.google.javascript.jscomp.CheckAccessControls.BAD_PROTECTED_PROPERTY_ACCESS;
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_CLASS;
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_CLASS_REASON;
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_NAME;
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_NAME_REASON;
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_PROP;
import static com.google.javascript.jscomp.CheckAccessControls.DEPRECATED_PROP_REASON;
import static com.google.javascript.jscomp.CheckAccessControls.PRIVATE_OVERRIDE;
import static com.google.javascript.jscomp.CheckAccessControls.VISIBILITY_MISMATCH;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CompilerOptions;

/**
 * Tests for {@link CheckAccessControls}.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public class CheckAccessControlsTest extends CompilerTestCase {

  public CheckAccessControlsTest() {
    parseTypeInfo = true;
    enableTypeCheck(CheckLevel.WARNING);
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new CheckAccessControls(compiler);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setWarningLevel(DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.ERROR);
    return options;
  }

  /**
   * Tests that the given Javascript code has a @deprecated marker
   * somewhere in it which raises an error. Also tests that the
   * deprecated marker works with a message. The Javascript should
   * have a JsDoc of the form "@deprecated %s\n".
   *
   * @param js The Javascript code to parse and test.
   * @param reason A simple deprecation reason string, used for testing
   *    the addition of a deprecation reason to the @deprecated tag.
   * @param error The deprecation error expected when no reason is given.
   * @param errorWithMessage The deprecation error expected when a reason
   *    message is given.
   */
  private void testDep(String js, String reason,
                       DiagnosticType error,
                       DiagnosticType errorWithMessage) {

    // Test without a reason.
    test(String.format(js, ""), null, error);

    // Test with a reason.
    test(String.format(js, reason), null, errorWithMessage, null, reason);
  }

  public void testDeprecatedFunction() {
    testDep("/** @deprecated %s */ function f() {} function g() { f(); }",
            "Some Reason",
            DEPRECATED_NAME, DEPRECATED_NAME_REASON);
  }

  public void testWarningOnDeprecatedConstVariable() {
    testDep("/** @deprecated %s */ var f = 4; function g() { alert(f); }",
            "Another reason",
            DEPRECATED_NAME, DEPRECATED_NAME_REASON);
  }

  public void testThatNumbersArentDeprecated() {
    testSame("/** @deprecated */ var f = 4; var h = 3; " +
             "function g() { alert(h); }");
  }

  public void testDeprecatedFunctionVariable() {
    testDep("/** @deprecated %s */ var f = function() {}; " +
            "function g() { f(); }", "I like g...",
            DEPRECATED_NAME, DEPRECATED_NAME_REASON);
  }

  public void testNoWarningInGlobalScope() {
    testSame("var goog = {}; goog.makeSingleton = function(x) {};" +
        "/** @deprecated */ function f() {} goog.makeSingleton(f);");
  }

  public void testNoWarningInGlobalScopeForCall() {
    testDep("/** @deprecated %s */ function f() {} f();",
            "Some global scope", DEPRECATED_NAME, DEPRECATED_NAME_REASON);
  }

  public void testNoWarningInDeprecatedFunction() {
    testSame("/** @deprecated */ function f() {} " +
             "/** @deprecated */ function g() { f(); }");
  }

  public void testWarningInNormalClass() {
    testDep("/** @deprecated %s */ function f() {}" +
            "/** @constructor */  var Foo = function() {}; " +
            "Foo.prototype.bar = function() { f(); }",
            "FooBar", DEPRECATED_NAME, DEPRECATED_NAME_REASON);
  }

  public void testWarningForProperty1() {
    testDep("/** @constructor */ function Foo() {}" +
            "/** @deprecated %s */ Foo.prototype.bar = 3;" +
            "Foo.prototype.baz = function() { alert((new Foo()).bar); };",
            "A property is bad",
            DEPRECATED_PROP, DEPRECATED_PROP_REASON);
  }

  public void testWarningForProperty2() {
    testDep("/** @constructor */ function Foo() {}" +
            "/** @deprecated %s */ Foo.prototype.bar = 3;" +
            "Foo.prototype.baz = function() { alert(this.bar); };",
            "Zee prop, it is deprecated!",
            DEPRECATED_PROP,
            DEPRECATED_PROP_REASON);
  }

  public void testWarningForDeprecatedClass() {
    testDep("/** @constructor \n* @deprecated %s */ function Foo() {} " +
            "function f() { new Foo(); }",
            "Use the class 'Bar'",
            DEPRECATED_CLASS,
            DEPRECATED_CLASS_REASON);
  }

  public void testNoWarningForDeprecatedClassInstance() {
    testSame("/** @constructor \n * @deprecated */ function Foo() {} " +
             "/** @param {Foo} x */ function f(x) { return x; }");
  }

  public void testWarningForDeprecatedSuperClass() {
    testDep("/** @constructor \n * @deprecated %s */ function Foo() {} " +
            "/** @constructor \n * @extends {Foo} */ function SubFoo() {}" +
            "function f() { new SubFoo(); }",
            "Superclass to the rescue!",
            DEPRECATED_CLASS,
            DEPRECATED_CLASS_REASON);
  }

  public void testWarningForDeprecatedSuperClass2() {
    testDep("/** @constructor \n * @deprecated %s */ function Foo() {} " +
            "var namespace = {}; " +
            "/** @constructor \n * @extends {Foo} */ " +
            "namespace.SubFoo = function() {}; " +
            "function f() { new namespace.SubFoo(); }",
            "Its only weakness is Kryptoclass",
            DEPRECATED_CLASS,
            DEPRECATED_CLASS_REASON);
  }

  public void testWarningForPrototypeProperty() {
    testDep("/** @constructor */ function Foo() {}" +
            "/** @deprecated %s */ Foo.prototype.bar = 3;" +
            "Foo.prototype.baz = function() { alert(Foo.prototype.bar); };",
            "It is now in production, use that model...",
            DEPRECATED_PROP,
            DEPRECATED_PROP_REASON);
  }

  public void testNoWarningForNumbers() {
    testSame("/** @constructor */ function Foo() {}" +
             "/** @deprecated */ Foo.prototype.bar = 3;" +
             "Foo.prototype.baz = function() { alert(3); };");
  }

  public void testWarningForMethod1() {
    testDep("/** @constructor */ function Foo() {}" +
            "/** @deprecated %s */ Foo.prototype.bar = function() {};" +
            "Foo.prototype.baz = function() { this.bar(); };",
            "There is a madness to this method",
            DEPRECATED_PROP,
            DEPRECATED_PROP_REASON);
  }

  public void testWarningForMethod2() {
    testDep("/** @constructor */ function Foo() {} " +
            "/** @deprecated %s */ Foo.prototype.bar; " +
            "Foo.prototype.baz = function() { this.bar(); };",
            "Stop the ringing!",
            DEPRECATED_PROP,
            DEPRECATED_PROP_REASON);
  }

  public void testNoWarningInDeprecatedClass() {
    testSame("/** @deprecated */ function f() {} " +
             "/** @constructor \n * @deprecated */ " +
             "var Foo = function() {}; " +
             "Foo.prototype.bar = function() { f(); }");
  }

  public void testNoWarningInDeprecatedClass2() {
    testSame("/** @deprecated */ function f() {} " +
             "/** @constructor \n * @deprecated */ " +
             "var Foo = function() {}; " +
             "Foo.bar = function() { f(); }");
  }

  public void testNoWarningInDeprecatedStaticMethod() {
    testSame("/** @deprecated */ function f() {} " +
             "/** @constructor */ " +
             "var Foo = function() {}; " +
             "/** @deprecated */ Foo.bar = function() { f(); }");
  }

  public void testWarningInStaticMethod() {
    testDep("/** @deprecated %s */ function f() {} " +
            "/** @constructor */ " +
            "var Foo = function() {}; " +
            "Foo.bar = function() { f(); }",
            "crazy!",
            DEPRECATED_NAME,
            DEPRECATED_NAME_REASON);
  }

  public void testDeprecatedObjLitKey() {
    testDep("var f = {}; /** @deprecated %s */ f.foo = 3; " +
            "function g() { return f.foo; }",
            "It is literally not used anymore",
            DEPRECATED_PROP,
            DEPRECATED_PROP_REASON);
  }

  public void testWarningForSubclassMethod() {
    testDep("/** @constructor */ function Foo() {}" +
            "Foo.prototype.bar = function() {};" +
            "/** @constructor \n * @extends {Foo} */ function SubFoo() {}" +
            "/** @deprecated %s */ SubFoo.prototype.bar = function() {};" +
            "function f() { (new SubFoo()).bar(); };",
            "I have a parent class!",
            DEPRECATED_PROP,
            DEPRECATED_PROP_REASON);
  }

  public void testWarningForSuperClassWithDeprecatedSubclassMethod() {
    testSame("/** @constructor */ function Foo() {}" +
             "Foo.prototype.bar = function() {};" +
             "/** @constructor \n * @extends {Foo} */ function SubFoo() {}" +
             "/** @deprecated \n * @override */ SubFoo.prototype.bar = function() {};" +
             "function f() { (new Foo()).bar(); };");
  }

  public void testWarningForSuperclassMethod() {
    testDep("/** @constructor */ function Foo() {}" +
            "/** @deprecated %s */ Foo.prototype.bar = function() {};" +
            "/** @constructor \n * @extends {Foo} */ function SubFoo() {}" +
            "SubFoo.prototype.bar = function() {};" +
            "function f() { (new SubFoo()).bar(); };",
            "I have a child class!",
            DEPRECATED_PROP,
            DEPRECATED_PROP_REASON);
  }

  public void testWarningForSuperclassMethod2() {
    testDep("/** @constructor */ function Foo() {}" +
            "/** @deprecated %s \n* @protected */" +
            "Foo.prototype.bar = function() {};" +
            "/** @constructor \n * @extends {Foo} */ function SubFoo() {}" +
            "/** @protected */SubFoo.prototype.bar = function() {};" +
            "function f() { (new SubFoo()).bar(); };",
            "I have another child class...",
            DEPRECATED_PROP,
            DEPRECATED_PROP_REASON);
  }

  public void testWarningForBind() {
    testDep("/** @deprecated %s */ Function.prototype.bind = function() {};" +
            "(function() {}).bind();",
            "I'm bound to this method...",
            DEPRECATED_PROP,
            DEPRECATED_PROP_REASON);
  }

  public void testWarningForDeprecatedClassInGlobalScope() {
    testDep("/** @constructor \n * @deprecated %s */ var Foo = function() {};" +
            "new Foo();",
            "I'm a very worldly object!",
            DEPRECATED_CLASS,
            DEPRECATED_CLASS_REASON);
  }

  public void testNoWarningForPrototypeCopying() {
    testSame("/** @constructor */ var Foo = function() {};" +
             "Foo.prototype.bar = function() {};" +
             "/** @deprecated */ Foo.prototype.baz = Foo.prototype.bar;" +
             "(new Foo()).bar();");
  }

  public void testNoWarningOnDeprecatedPrototype() {
    // This used to cause an NPE.
    testSame("/** @constructor */ var Foo = function() {};" +
        "/** @deprecated */ Foo.prototype = {};" +
        "Foo.prototype.bar = function() {};");
  }

  public void testPrivateAccessForNames() {
    testSame("/** @private */ function foo_() {}; foo_();");
    test(new String[] {
      "/** @private */ function foo_() {};",
      "foo_();"
    }, null, BAD_PRIVATE_GLOBAL_ACCESS);
  }

  public void testPrivateAccessForProperties1() {
    testSame("/** @constructor */ function Foo() {}" +
        "/** @private */ Foo.prototype.bar_ = function() {};" +
        "Foo.prototype.baz = function() { this.bar_(); }; (new Foo).bar_();");
  }

  public void testPrivateAccessForProperties2() {
    testSame(new String[] {
      "/** @constructor */ function Foo() {}",
      "/** @private */ Foo.prototype.bar_ = function() {};" +
      "Foo.prototype.baz = function() { this.bar_(); }; (new Foo).bar_();"
    });
  }

  public void testPrivateAccessForProperties3() {
    testSame(new String[] {
      "/** @constructor */ function Foo() {}" +
      "/** @private */ Foo.prototype.bar_ = function() {}; (new Foo).bar_();",
      "Foo.prototype.baz = function() { this.bar_(); };"
    });
  }

  public void testNoPrivateAccessForProperties1() {
    test(new String[] {
      "/** @constructor */ function Foo() {} (new Foo).bar_();",
      "/** @private */ Foo.prototype.bar_ = function() {};" +
      "Foo.prototype.baz = function() { this.bar_(); };"
    }, null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForProperties2() {
    test(new String[] {
      "/** @constructor */ function Foo() {} " +
      "/** @private */ Foo.prototype.bar_ = function() {};" +
      "Foo.prototype.baz = function() { this.bar_(); };",
      "(new Foo).bar_();"
    }, null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForProperties3() {
    test(new String[] {
      "/** @constructor */ function Foo() {} " +
      "/** @private */ Foo.prototype.bar_ = function() {};",
      "/** @constructor */ function OtherFoo() { (new Foo).bar_(); }"
    }, null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForProperties4() {
    test(new String[] {
      "/** @constructor */ function Foo() {} " +
      "/** @private */ Foo.prototype.bar_ = function() {};",
      "/** @constructor \n * @extends {Foo} */ " +
      "function SubFoo() { this.bar_(); }"
    }, null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForProperties5() {
    test(new String[] {
      "/** @constructor */ function Foo() {} " +
      "/** @private */ Foo.prototype.bar_ = function() {};",
      "/** @constructor \n * @extends {Foo} */ " +
      "function SubFoo() {};" +
      "SubFoo.prototype.baz = function() { this.bar_(); }"
    }, null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForProperties6() {
    // Overriding a private property with a non-private property
    // in a different file causes problems.
    test(new String[] {
      "/** @constructor */ function Foo() {} " +
      "/** @private */ Foo.prototype.bar_ = function() {};",
      "/** @constructor \n * @extends {Foo} */ " +
      "function SubFoo() {};" +
      "SubFoo.prototype.bar_ = function() {};"
    }, null, PRIVATE_OVERRIDE);
  }

  public void testNoPrivateAccessForProperties7() {
    // It's ok to override a private property with a non-private property
    // in the same file, but you'll get yelled at when you try to use it.
    test(new String[] {
      "/** @constructor */ function Foo() {} " +
      "/** @private */ Foo.prototype.bar_ = function() {};" +
      "/** @constructor \n * @extends {Foo} */ " +
      "function SubFoo() {};" +
      "/** @const */ SubFoo.prototype.bar_ = function() {};",
      "SubFoo.prototype.baz = function() { this.bar_(); }"
    }, null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testProtectedAccessForProperties1() {
    testSame(new String[] {
      "/** @constructor */ function Foo() {}" +
      "/** @protected */ Foo.prototype.bar = function() {};" +
      "(new Foo).bar();",
      "Foo.prototype.baz = function() { this.bar(); };"
    });
  }

  public void testProtectedAccessForProperties2() {
    testSame(new String[] {
      "/** @constructor */ function Foo() {}" +
      "/** @protected */ Foo.prototype.bar = function() {};" +
      "(new Foo).bar();",
      "/** @constructor \n * @extends {Foo} */" +
      "function SubFoo() { this.bar(); }"
    });
  }

  public void testProtectedAccessForProperties3() {
    testSame(new String[] {
      "/** @constructor */ function Foo() {}" +
      "/** @protected */ Foo.prototype.bar = function() {};" +
      "(new Foo).bar();",
      "/** @constructor \n * @extends {Foo} */" +
      "function SubFoo() { }" +
      "SubFoo.baz = function() { (new Foo).bar(); }"
    });
  }

  public void testProtectedAccessForProperties4() {
    testSame(new String[] {
      "/** @constructor */ function Foo() {}" +
      "/** @protected */ Foo.bar = function() {};",
      "/** @constructor \n * @extends {Foo} */" +
      "function SubFoo() { Foo.bar(); }"
    });
  }

  public void testProtectedAccessForProperties5() {
    testSame(new String[] {
      "/** @constructor */ function Foo() {}" +
      "/** @protected */ Foo.prototype.bar = function() {};" +
      "(new Foo).bar();",
      "/** @constructor \n * @extends {Foo} */" +
      "var SubFoo = function() { this.bar(); }"
    });
  }

  public void testProtectedAccessForProperties6() {
    testSame(new String[] {
      "var goog = {};" +
      "/** @constructor */ goog.Foo = function() {};" +
      "/** @protected */ goog.Foo.prototype.bar = function() {};",
      "/** @constructor \n * @extends {goog.Foo} */" +
      "goog.SubFoo = function() { this.bar(); };"
    });
  }

  public void testNoProtectedAccessForProperties1() {
    test(new String[] {
      "/** @constructor */ function Foo() {} " +
      "/** @protected */ Foo.prototype.bar = function() {};",
      "(new Foo).bar();"
    }, null, BAD_PROTECTED_PROPERTY_ACCESS);
  }

  public void testNoProtectedAccessForProperties2() {
    test(new String[] {
      "/** @constructor */ function Foo() {} " +
      "/** @protected */ Foo.prototype.bar = function() {};",
      "/** @constructor */ function OtherFoo() { (new Foo).bar(); }"
    }, null, BAD_PROTECTED_PROPERTY_ACCESS);
  }

  public void testNoProtectedAccessForProperties3() {
    test(new String[] {
      "/** @constructor */ function Foo() {} " +
      "/** @constructor \n * @extends {Foo} */ " +
      "function SubFoo() {}" +
      "/** @protected */ SubFoo.prototype.bar = function() {};",
      "/** @constructor \n * @extends {Foo} */ " +
      "function SubberFoo() { (new SubFoo).bar(); }"
    }, null, BAD_PROTECTED_PROPERTY_ACCESS);
  }

  public void testNoProtectedAccessForProperties4() {
    test(new String[] {
      "/** @constructor */ function Foo() { (new SubFoo).bar(); } ",
      "/** @constructor \n * @extends {Foo} */ " +
      "function SubFoo() {}" +
      "/** @protected */ SubFoo.prototype.bar = function() {};",
    }, null, BAD_PROTECTED_PROPERTY_ACCESS);
  }

  public void testNoProtectedAccessForProperties5() {
    test(new String[] {
      "var goog = {};" +
      "/** @constructor */ goog.Foo = function() {};" +
      "/** @protected */ goog.Foo.prototype.bar = function() {};",
      "/** @constructor */" +
      "goog.NotASubFoo = function() { (new goog.Foo).bar(); };"
    }, null, BAD_PROTECTED_PROPERTY_ACCESS);
  }

  public void testNoExceptionsWithBadConstructors1() {
    testSame(new String[] {
      "function Foo() { (new SubFoo).bar(); } " +
      "/** @constructor */ function SubFoo() {}" +
      "/** @protected */ SubFoo.prototype.bar = function() {};"
    });
  }

  public void testNoExceptionsWithBadConstructors2() {
    testSame(new String[] {
      "/** @constructor */ function Foo() {} " +
      "Foo.prototype.bar = function() {};" +
      "/** @constructor */" +
      "function SubFoo() {}" +
      "/** @protected */ " +
      "SubFoo.prototype.bar = function() { (new Foo).bar(); };"
    });
  }

  public void testGoodOverrideOfProtectedProperty() {
    testSame(new String[] {
      "/** @constructor */ function Foo() { } " +
      "/** @protected */ Foo.prototype.bar = function() {};",
      "/** @constructor \n * @extends {Foo} */ " +
      "function SubFoo() {}" +
      "/** @inheritDoc */ SubFoo.prototype.bar = function() {};",
    });
  }

  public void testBadOverrideOfProtectedProperty() {
    test(new String[] {
      "/** @constructor */ function Foo() { } " +
      "/** @protected */ Foo.prototype.bar = function() {};",
      "/** @constructor \n * @extends {Foo} */ " +
      "function SubFoo() {}" +
      "/** @private */ SubFoo.prototype.bar = function() {};",
    }, null, VISIBILITY_MISMATCH);
  }

  public void testBadOverrideOfPrivateProperty() {
    test(new String[] {
      "/** @constructor */ function Foo() { } " +
      "/** @private */ Foo.prototype.bar = function() {};",
      "/** @constructor \n * @extends {Foo} */ " +
      "function SubFoo() {}" +
      "/** @protected */ SubFoo.prototype.bar = function() {};",
    }, null, PRIVATE_OVERRIDE);
  }

  public void testAccessOfStaticMethodOnPrivateConstructor() {
    testSame(new String[] {
      "/** @constructor \n * @private */ function Foo() { } " +
      "Foo.create = function() { return new Foo(); };",
      "Foo.create()",
    });
  }

  public void testAccessOfStaticMethodOnPrivateQualifiedConstructor() {
    testSame(new String[] {
      "var goog = {};" +
      "/** @constructor \n * @private */ goog.Foo = function() { }; " +
      "goog.Foo.create = function() { return new goog.Foo(); };",
      "goog.Foo.create()",
    });
  }

  public void testInstanceofOfPrivateConstructor() {
    testSame(new String[] {
      "var goog = {};" +
      "/** @constructor \n * @private */ goog.Foo = function() { }; " +
      "goog.Foo.create = function() { return new goog.Foo(); };",
      "goog instanceof goog.Foo",
    });
  }

  public void testOkAssignmentOfDeprecatedProperty() {
    testSame(
        "/** @constructor */ function Foo() {" +
        " /** @deprecated */ this.bar = 3;" +
        "}");
  }

  public void testBadReadOfDeprecatedProperty() {
    testDep(
        "/** @constructor */ function Foo() {" +
        " /** @deprecated %s */ this.bar = 3;" +
        "  this.baz = this.bar;" +
        "}",
        "GRR",
        DEPRECATED_PROP,
        DEPRECATED_PROP_REASON);
  }

  public void testAutoboxedDeprecatedProperty() {
    testDep(
        "/** @constructor */ function String() {}" +
        "/** @deprecated %s */ String.prototype.length;" +
        "function f() { return 'x'.length; }",
        "GRR",
        DEPRECATED_PROP,
        DEPRECATED_PROP_REASON);
  }

  public void testAutoboxedPrivateProperty() {
    test(new String[] {
        "/** @constructor */ function String() {}" +
        "/** @private */ String.prototype.length;",
        "function f() { return 'x'.length; }"
    }, null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNullableDeprecatedProperty() {
    testDep(
        "/** @constructor */ function Foo() {}" +
        "/** @deprecated %s */ Foo.prototype.length;" +
        "/** @param {?Foo} x */ function f(x) { return x.length; }",
        "GRR",
        DEPRECATED_PROP,
        DEPRECATED_PROP_REASON);
  }

  public void testNullablePrivateProperty() {
    test(new String[] {
        "/** @constructor */ function Foo() {}" +
        "/** @private */ Foo.prototype.length;",
        "/** @param {?Foo} x */ function f(x) { return x.length; }"
    }, null, BAD_PRIVATE_PROPERTY_ACCESS);
  }
}
