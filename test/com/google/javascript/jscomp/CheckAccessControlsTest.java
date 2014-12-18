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

public class CheckAccessControlsTest extends CompilerTestCase {

  public CheckAccessControlsTest() {
    super(CompilerTypeTestCase.DEFAULT_EXTERNS);
    parseTypeInfo = true;
    enableClosurePass();
    enableTypeCheck(CheckLevel.WARNING);
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
    options.setWarningLevel(DiagnosticGroups.CONSTANT_PROPERTY,
        CheckLevel.ERROR);
    return options;
  }

  /**
   * Tests that the given JavaScript code has a @deprecated marker
   * somewhere in it which raises an error. Also tests that the
   * deprecated marker works with a message. The JavaScript should
   * have a JsDoc of the form "@deprecated %s\n".
   *
   * @param js The JavaScript code to parse and test.
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

  public void testNoWarningOnDeclaration() {
    testSame("/** @constructor */ function F() {\n" +
             "  /**\n" +
             "   * @type {number}\n" +
             "   * @deprecated Use something else.\n" +
             "   */\n" +
             "  this.code;\n" +
             "}");
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
             "/** @deprecated \n * @override */ SubFoo.prototype.bar = " +
             "function() {};" +
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

  public void testPrivateAccessForNames2() {
    // Private by convention
    testSame("function foo_() {}; foo_();");
    test(new String[] {
      "function foo_() {};",
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

  public void testPrivateAccessForProperties4() {
    testSame(new String[] {
      "/** @constructor */ function Foo() {}" +
      "/** @private */ Foo.prototype.bar_ = function() {};",
      "Foo.prototype['baz'] = function() { (new Foo()).bar_(); };"
    });
  }

  public void testPrivateAccessForProperties5() {
    test(new String[] {
          "/** @constructor */\n" +
          "function Parent () {\n" +
          "  /** @private */\n" +
          "  this.prop = 'foo';\n" +
          "};",
          "/**\n" +
          " * @constructor\n" +
          " * @extends {Parent}\n" +
          " */\n" +
          "function Child() {\n" +
          "  this.prop = 'asdf';\n" +
          "}\n" +
          "Child.prototype = new Parent();"
        }, null, BAD_PRIVATE_PROPERTY_ACCESS);
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
    }, null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForProperties7() {
    // It's OK to override a private property with a non-private property
    // in the same file, but you'll get yelled at when you try to use it.
    test(new String[] {
      "/** @constructor */ function Foo() {} " +
      "/** @private */ Foo.prototype.bar_ = function() {};" +
      "/** @constructor \n * @extends {Foo} */ " +
      "function SubFoo() {};" +
      "SubFoo.prototype.bar_ = function() {};",
      "SubFoo.prototype.baz = function() { this.bar_(); }"
    }, null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForProperties8() {
    test(new String[] {
      "/** @constructor */ function Foo() { /** @private */ this.bar_ = 3; }",
      "/** @constructor \n * @extends {Foo} */ " +
      "function SubFoo() { /** @private */ this.bar_ = 3; };"
    }, null, PRIVATE_OVERRIDE);
  }

  public void testNoPrivateAccessForProperties9() {
    test(new String[] {
      "/** @constructor */ function Foo() {}" +
      "Foo.prototype = {" +
      "/** @private */ bar_: 3" +
      "}",
      "new Foo().bar_;"
    }, null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForProperties10() {
    test(new String[] {
      "/** @constructor */ function Foo() {}" +
      "Foo.prototype = {" +
      "/** @private */ bar_: function() {}" +
      "}",
      "new Foo().bar_();"
    }, null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForProperties11() {
    test(new String[] {
      "/** @constructor */ function Foo() {}" +
      "Foo.prototype = {" +
      "/** @private */ get bar_() { return 1; }" +
      "}",
      "var a = new Foo().bar_;"
    }, null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForProperties12() {
    test(new String[] {
      "/** @constructor */ function Foo() {}" +
      "Foo.prototype = {" +
      "/** @private */ set bar_(x) { this.barValue = x; }" +
      "}",
      "new Foo().bar_ = 1;"
    }, null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testNoPrivateAccessForNamespaces() {
    test(new String[]{
      "var foo = {};\n" +
          "/** @private */ foo.bar_ = function() {};",
      "foo.bar_();"
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

  public void testProtectedAccessForProperties7() {
    testSame(new String[] {
      "/** @constructor */ var Foo = function() {};" +
      "Foo.prototype = { /** @protected */ bar: function() {} }",
      "/** @constructor \n * @extends {Foo} */" +
      "var SubFoo = function() { this.bar(); };" +
      "SubFoo.prototype = { moo: function() { this.bar(); }};"
    });
  }

  public void testProtectedAccessForProperties8() {
    testSame(new String[] {
      "/** @constructor */ var Foo = function() {};" +
      "Foo.prototype = { /** @protected */ bar: function() {} }",
      "/** @constructor \n * @extends {Foo} */" +
      "var SubFoo = function() {};" +
      "SubFoo.prototype = { get moo() { this.bar(); }};"
    });
  }

  public void testProtectedAccessForProperties9() {
    testSame(new String[] {
      "/** @constructor */ var Foo = function() {};" +
      "Foo.prototype = { /** @protected */ bar: function() {} }",
      "/** @constructor \n * @extends {Foo} */" +
      "var SubFoo = function() {};" +
      "SubFoo.prototype = { set moo(val) { this.x = this.bar(); }};"
    });
  }

  public void testProtectedAccessForProperties10() {
    testSame(ImmutableList.of(
        SourceFile.fromCode("foo.js",
            "/** @constructor */ var Foo = function() {};" +
            "/** @protected */ Foo.prototype.bar = function() {};"),
        SourceFile.fromCode("sub_foo.js",
            "/** @constructor @extends {Foo} */" +
            "var SubFoo = function() {};" +
            "(function() {" +
              "SubFoo.prototype.baz = function() { this.bar(); }" +
            "})();")));
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

  public void testNoProtectedAccessForProperties6() {
      test(new String[] {
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype = {" +
        "/** @protected */ bar: 3" +
        "}",
        "new Foo().bar;"
      }, null, BAD_PROTECTED_PROPERTY_ACCESS);
    }

    public void testNoProtectedAccessForProperties7() {
      test(new String[] {
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype = {" +
        "/** @protected */ bar: function() {}" +
        "}",
        "new Foo().bar();"
      }, null, BAD_PROTECTED_PROPERTY_ACCESS);
    }

  public void testPackagePrivateAccessForNames() {
    test(ImmutableList.of(
      SourceFile.fromCode(
        "foo/bar.js",
        "/** @constructor */\n" +
          "function Parent() {\n" +
          "/** @package */\n" +
          "this.prop = 'foo';\n" +
          "}\n;"),
      SourceFile.fromCode(
        "baz/quux.js",
          "/**" +
          " * @constructor\n" +
          " * @extends {Parent}\n" +
          " */\n" +
          "function Child() {\n" +
          "  this.prop = 'asdf';\n" +
          "}\n" +
          "Child.prototype = new Parent();"
      )), null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testPackagePrivateAccessForProperties1() {
    testSame("/** @constructor */ function Foo() {}" +
        "/** @package */ Foo.prototype.bar = function() {};" +
        "Foo.prototype.baz = function() { this.bar(); }; (new Foo).bar();");
  }

  public void testPackagePrivateAccessForProperties2() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            "foo/bar.js",
            "/** @constructor */ function Foo() {}"),
        SourceFile.fromCode(
            "baz/quux.js",
            "/** @package */ Foo.prototype.bar = function() {};" +
      "Foo.prototype.baz = function() { this.bar(); }; (new Foo).bar();")));
  }

  public void testPackagePrivateAccessForProperties3() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            "foo/bar.js",
            "/** @constructor */ function Foo() {}" +
            "/** @package */ Foo.prototype.bar = function() {}; (new Foo).bar();"),
        SourceFile.fromCode(
            "foo/baz.js",
            "Foo.prototype.baz = function() { this.bar(); };")));
  }

  public void testPackagePrivateAccessForProperties4() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            "foo/bar.js",
            "/** @constructor */ function Foo() {}" +
            "/** @package */ Foo.prototype.bar = function() {};"),
         SourceFile.fromCode(
            "foo/baz.js",
            "Foo.prototype['baz'] = function() { (new Foo()).bar(); };")));
  }

  public void testPackagePrivateAccessForProperties5() {
    test(ImmutableList.of(
          SourceFile.fromCode(
              "foo/bar.js",
              "/** @constructor */\n" +
                  "function Parent () {\n" +
                  "  /** @package */\n" +
                  "  this.prop = 'foo';\n" +
                  "};"),
           SourceFile.fromCode(
               "baz/quux.js",
               "/**\n" +
                   " * @constructor\n" +
                   " * @extends {Parent}\n" +
                   " */\n" +
                   "function Child() {\n" +
                   "  this.prop = 'asdf';\n" +
                   "}\n" +
                   "Child.prototype = new Parent();")),
        null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testNoPackagePrivateAccessForProperties1() {
    test(ImmutableList.of(
        SourceFile.fromCode(
            "foo/bar.js",
            "/** @constructor */ function Foo() {} (new Foo).bar();"),
            SourceFile.fromCode(
                "baz/quux.js",
                "/** @package */ Foo.prototype.bar = function() {};" +
                    "Foo.prototype.baz = function() { this.bar(); };")),
    null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testNoPackagePrivateAccessForProperties2() {
    test(ImmutableList.of(
      SourceFile.fromCode(
          "foo/bar.js",
          "/** @constructor */ function Foo() {} " +
              "/** @package */ Foo.prototype.bar = function() {};" +
              "Foo.prototype.baz = function() { this.bar(); };"),
      SourceFile.fromCode(
          "baz/quux.js",
          "(new Foo).bar();")),
    null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testNoPackagePrivateAccessForProperties3() {
    test(ImmutableList.of(
        SourceFile.fromCode(
            "foo/bar.js",
            "/** @constructor */ function Foo() {} " +
                "/** @package */ Foo.prototype.bar = function() {};"),
        SourceFile.fromCode(
            "baz/quux.js",
            "/** @constructor */ function OtherFoo() { (new Foo).bar(); }")),
    null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testNoPackagePrivateAccessForProperties4() {
    test(ImmutableList.of(
        SourceFile.fromCode(
            "foo/bar.js",
            "/** @constructor */ function Foo() {} " +
            "/** @package */ Foo.prototype.bar = function() {};"),
        SourceFile.fromCode(
            "baz/quux.js",
            "/** @constructor \n * @extends {Foo} */ " +
                "function SubFoo() { this.bar(); }")),
    null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testNoPackagePrivateAccessForNamespaces() {
    test(ImmutableList.of(
        SourceFile.fromCode(
            "foo/bar.js",
            "var foo = {};\n" +
            "/** @package */ foo.bar = function() {};"),
        SourceFile.fromCode(
            "baz/quux.js",
            "foo.bar();")),
    null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testNoPackagePrivateAccessForProperties5() {
    test(ImmutableList.of(
        SourceFile.fromCode(
            "foo/bar.js",
            "/** @constructor */ function Foo() {} " +
            "/** @package */ Foo.prototype.bar = function() {};"),
      SourceFile.fromCode(
          "baz/quux.js",
          "/** @constructor \n * @extends {Foo} */ " +
              "function SubFoo() {};" +
              "SubFoo.prototype.baz = function() { this.bar(); }")),
    null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testNoPackagePrivateAccessForProperties6() {
    // Overriding a private property with a non-package-private property
    // in a different file causes problems.
    test(ImmutableList.of(
        SourceFile.fromCode(
            "foo/bar.js",
            "/** @constructor */ function Foo() {} " +
                "/** @package */ Foo.prototype.bar = function() {};"),
        SourceFile.fromCode(
            "baz/quux.js",
            "/** @constructor \n * @extends {Foo} */ " +
                "function SubFoo() {};" +
                "SubFoo.prototype.bar = function() {};")),
    null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testNoPackagePrivateAccessForProperties7() {
    // It's OK to override a package-private property with a
    // non-package-private property in the same file, but you'll get
    // yelled at when you try to use it.
    test(ImmutableList.of(
        SourceFile.fromCode(
            "foo/bar.js",
            "/** @constructor */ function Foo() {} " +
                "/** @package */ Foo.prototype.bar = function() {};" +
                "/** @constructor \n * @extends {Foo} */ " +
                "function SubFoo() {};" +
                "SubFoo.prototype.bar = function() {};"),
                SourceFile.fromCode(
                    "baz/quux.js",
                    "SubFoo.prototype.baz = function() { this.bar(); }")),
    null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testOverrideWithoutVisibilityRedeclInFileWithFileOverviewVisibilityNotAllowed_OneFile() {
    test("/**\n" +
          "* @fileoverview\n" +
          "* @package\n" +
          "*/\n" +
          "/** @struct @constructor */\n" +
          "Foo = function() {};\n" +
          "/** @private */\n" +
          "Foo.prototype.privateMethod_ = function() {};\n" +
          "/** @struct @constructor @extends {Foo} */\n" +
          "Bar = function() {};\n" +
          "/** @override */\n" +
          "Bar.prototype.privateMethod_ = function() {};\n",
        null, BAD_PROPERTY_OVERRIDE_IN_FILE_WITH_FILEOVERVIEW_VISIBILITY);
  }

  public void testNamespacedFunctionDoesNotNeedVisibilityRedeclInFileWithFileOverviewVisibility() {
    testSame(
        "/**\n" +
        " * @fileoverview\n" +
        " * @package\n" +
        " */\n" +
        "/** @return {string} */\n" +
        "foo.bar = function() {};");
  }

  public void testOverrideWithoutVisibilityRedeclInFileWithFileOverviewVisibilityNotAllowed_TwoFiles() {
    test(new String[]{
      "/** @struct @constructor */\n" +
          "Foo = function() {};\n" +
          "/** @protected */\n" +
          "Foo.prototype.protectedMethod = function() {};\n",
      "  /**\n" +
          "* @fileoverview \n" +
          "* @package\n" +
          "*/\n" +
          "/** @struct @constructor @extends {Foo} */\n" +
          "Bar = function() {};\n" +
          "/** @override */\n" +
          "Bar.prototype.protectedMethod = function() {};\n"
    }, null, BAD_PROPERTY_OVERRIDE_IN_FILE_WITH_FILEOVERVIEW_VISIBILITY);
  }

  public void testOverrideWithoutVisibilityRedeclInFileWithNoFileOverviewOk() {
    testSame("/** @struct @constructor */\n" +
        "Foo = function() {};\n" +
        "/** @private */\n" +
        "Foo.prototype.privateMethod_ = function() {};\n" +
        "/** @struct @constructor @extends {Foo} */\n" +
        "Bar = function() {};\n" +
        "/** @override */\n" +
        "Bar.prototype.privateMethod_ = function() {};\n");
  }

  public void testOverrideWithoutVisibilityRedeclInFileWithNoFileOverviewVisibilityOk() {
    testSame("/**\n" +
        "  * @fileoverview\n" +
        "  */\n" +
        "/** @struct @constructor */\n" +
          "Foo = function() {};\n" +
          "/** @private */\n" +
          "Foo.prototype.privateMethod_ = function() {};\n" +
          "/** @struct @constructor @extends {Foo} */\n" +
          "Bar = function() {};\n" +
          "/** @override */\n" +
          "Bar.prototype.privateMethod_ = function() {};\n");
  }

  public void testOverrideWithVisibilityRedeclInFileWithFileOverviewVisibilityOk_OneFile() {
    testSame("/**\n" +
        "  * @fileoverview\n" +
        "  * @package\n" +
        "  */\n" +
        "/** @struct @constructor */\n" +
          "Foo = function() {};\n" +
          "/** @private */\n" +
          "Foo.prototype.privateMethod_ = function() {};\n" +
          "/** @struct @constructor @extends {Foo} */\n" +
          "Bar = function() {};\n" +
          "/** @override @private */\n" +
          "Bar.prototype.privateMethod_ = function() {};\n");
  }

  public void testOverrideWithVisibilityRedeclInFileWithFileOverviewVisibilityOk_TwoFiles() {
    testSame(new String[]{
      "/** @struct @constructor */\n" +
          "Foo = function() {};\n" +
          "/** @protected */\n" +
          "Foo.prototype.protectedMethod = function() {};\n",
      "  /**\n" +
          "* @fileoverview\n" +
          "* @package\n" +
          "*/\n" +
          "/** @struct @constructor @extends {Foo} */\n" +
          "Bar = function() {};\n" +
          "/** @override @protected */\n" +
          "Bar.prototype.protectedMethod = function() {};\n"
    });
  }

  public void testPublicFileOverviewVisibilityDoesNotApplyToNameWithExplicitPackageVisibility() {
    test(ImmutableList.of(
        SourceFile.fromCode(
            "foo/bar.js",
            "/**\n" +
            " * @fileoverview\n" +
            " * @public\n" +
            " */\n" +
            "/** @constructor @package */ function Foo() {};"),
        SourceFile.fromCode(
            "baz/quux.js",
            "new Foo();")),
    null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testPackageFileOverviewVisibilityDoesNotApplyToNameWithExplicitPublicVisibility() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            "foo/bar.js",
            "/**\n" +
            " * @fileoverview\n" +
            " * @package\n" +
            " */\n" +
            "/** @constructor @public */ function Foo() {};"),
        SourceFile.fromCode(
            "baz/quux.js",
            "new Foo();")));
  }

  public void testPackageFileOverviewVisibilityAppliesToNameWithoutExplicitVisibility() {
    test(ImmutableList.of(
        SourceFile.fromCode(
            "foo/bar.js",
            "/**\n" +
            " * @fileoverview\n" +
            " * @package\n" +
            " */\n" +
            "/** @constructor */\n" +
            "var Foo = function() {};\n"),
        SourceFile.fromCode(
            "baz/quux.js",
            "new Foo();")),
        null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testPackageFileOverviewVisibilityDoesNotApplyToPropertyWithExplicitPublicVisibility() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            "foo/bar.js",
            "/**\n" +
            " * @fileoverview\n" +
            " * @package\n" +
            " */\n" +
            "/** @constructor */\n" +
            "Foo = function() {};\n" +
            "/** @public */\n" +
            "Foo.prototype.bar = function() {};\n"),
        SourceFile.fromCode(
            "baz/quux.js",
            "var foo = new Foo();\n" +
            "foo.bar();")));
  }

  public void testFileoverviewVisibilityDoesNotApplyToGoogProvidedNamespace1() {
    // Don't compare the generated JsDoc. It includes annotations we're not interested in,
    // like @inherited.
    compareJsDoc = false;

    test(ImmutableList.of(
        SourceFile.fromCode(
            "foo.js",
            "goog.provide('foo');"),
        SourceFile.fromCode(
            "foo/bar.js",
            "/**\n" +
            "  * @fileoverview\n" +
            "  * @package\n" +
            "  */\n" +
            "goog.provide('foo.bar');"),
        SourceFile.fromCode(
            "bar.js",
            "goog.require('foo')")),
        ImmutableList.of(
            SourceFile.fromCode("foo.js", "var foo={};"),
            SourceFile.fromCode("foo/bar.js", "foo.bar={};"),
            SourceFile.fromCode("bar.js", "")),
        null, null);

    compareJsDoc = true;
  }

  public void testFileoverviewVisibilityDoesNotApplyToGoogProvidedNamespace2() {
    // Don't compare the generated JsDoc. It includes annotations we're not interested in,
    // like @inherited.
    compareJsDoc = false;

    test(ImmutableList.of(
        SourceFile.fromCode(
            "foo/bar.js",
            "/**\n" +
            "  * @fileoverview\n" +
            "  * @package\n" +
            "  */\n" +
            "goog.provide('foo.bar');"),
        SourceFile.fromCode(
            "foo.js",
            "goog.provide('foo');"),
        SourceFile.fromCode(
            "bar.js",
            "goog.require('foo');\n" +
            "var x = foo;")),
        ImmutableList.of(
            SourceFile.fromCode("foo/bar.js", "var foo={};foo.bar={};"),
            SourceFile.fromCode("foo.js", ""),
            SourceFile.fromCode("bar.js", "var x=foo")),
        null, null);

    compareJsDoc = true;
  }

  public void testFileoverviewVisibilityDoesNotApplyToGoogProvidedNamespace3() {
    // Don't compare the generated JsDoc. It includes annotations we're not interested in,
    // like @inherited.
    compareJsDoc = false;

    test(ImmutableList.of(
        SourceFile.fromCode(
            "foo/bar.js",
            "/**\n" +
            " * @fileoverview\n" +
            " * @package\n" +
            " */\n" +
            "goog.provide('one.two');\n" +
            "one.two.three = function(){};"),
        SourceFile.fromCode(
            "baz.js",
            "goog.require('one.two');\n" +
            "var x = one.two;")),
        ImmutableList.of(
            SourceFile.fromCode(
                "foo/bar.js",
                "var one={};one.two={};one.two.three=function(){};"),
            SourceFile.fromCode(
                "baz.js",
                "var x=one.two")),
        null, null);

    compareJsDoc = true;
  }

  public void testFileoverviewVisibilityDoesNotApplyToGoogProvidedNamespace4() {
    test(ImmutableList.of(
        SourceFile.fromCode(
            "foo/bar.js",
            "/**\n" +
            " * @fileoverview\n" +
            " * @package\n" +
            " */\n" +
            "goog.provide('one.two');\n" +
            "one.two.three = function(){};"),
        SourceFile.fromCode(
            "baz.js",
            "goog.require('one.two');\n" +
            "var x = one.two.three();")),
      null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testPublicFileOverviewVisibilityDoesNotApplyToPropertyWithExplicitPackageVisibility() {
    test(ImmutableList.of(
        SourceFile.fromCode(
            "foo/bar.js",
            "/**\n" +
            " * @fileoverview\n" +
            " * @public\n" +
            " */\n" +
            "/** @constructor */\n" +
            "Foo = function() {};\n" +
            "/** @package */\n" +
            "Foo.prototype.bar = function() {};\n"),
        SourceFile.fromCode(
            "baz/quux.js",
            "var foo = new Foo();\n" +
            "foo.bar();")),
        null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testPublicFileOverviewVisibilityAppliesToPropertyWithoutExplicitVisibility() {
    testSame(ImmutableList.of(
        SourceFile.fromCode(
            "foo/bar.js",
            "/**\n" +
            " * @fileoverview\n" +
            " * @public\n" +
            " */\n" +
            "/** @constructor */\n" +
            "Foo = function() {};\n" +
            "Foo.prototype.bar = function() {};\n"),
        SourceFile.fromCode(
            "baz/quux.js",
            "var foo = new Foo();\n" +
            "foo.bar();")));
  }

  public void testPackageFileOverviewVisibilityAppliesToPropertyWithoutExplicitVisibility() {
    test(ImmutableList.of(
        SourceFile.fromCode(
            "foo/bar.js",
            "/**\n" +
            " * @fileoverview\n" +
            " * @package\n" +
            " */\n" +
            "/** @constructor */\n" +
            "Foo = function() {};\n" +
            "Foo.prototype.bar = function() {};\n"),
        SourceFile.fromCode(
            "baz/quux.js",
            "var foo = new Foo();\n" +
            "foo.bar();")),
        null, BAD_PACKAGE_PROPERTY_ACCESS);
  }

  public void testFileOverviewVisibilityComesFromDeclarationFileNotUseFile() {
    test(ImmutableList.of(
        SourceFile.fromCode(
            "foo/bar.js",
            "/**\n" +
            " * @fileoverview\n" +
            " * @package\n" +
            " */\n" +
            "/** @constructor */\n" +
            "Foo = function() {};\n" +
            "Foo.prototype.bar = function() {};\n"),
        SourceFile.fromCode(
            "baz/quux.js",
            "/**\n" +
            " * @fileoverview\n" +
            " * @public\n" +
            " */\n" +
            "var foo = new Foo();\n" +
            "foo.bar();")),
        null, BAD_PACKAGE_PROPERTY_ACCESS);
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

    testSame(new String[] {
      "/** @constructor */ function Foo() { } " +
      "/** @private */ Foo.prototype.bar = function() {};",
      "/** @constructor \n * @extends {Foo} */ " +
      "function SubFoo() {}" +
      "/** @override \n *@suppress{visibility} */\n" +
      " SubFoo.prototype.bar = function() {};",
    });
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
    test(
        "", // no externs
        "/** @constructor */ function String() {}" +
        "/** @deprecated %s */ String.prototype.length;" +
        "function f() { return 'x'.length; }",
        "GRR",
        DEPRECATED_PROP_REASON,
        null);
  }

  public void testAutoboxedPrivateProperty() {
    test(
        "/** @constructor */ function String() {}" +
        "/** @private */ String.prototype.length;", // externs
        "function f() { return 'x'.length; }",
        "", // output
        BAD_PRIVATE_PROPERTY_ACCESS,
        null);
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

  public void testPrivatePropertyByConvention1() {
    test(new String[] {
        "/** @constructor */ function Foo() {}\n" +
        "/** @type {number} */ Foo.prototype.length_;\n",
        "/** @param {?Foo} x */ function f(x) { return x.length_; }\n"
    }, null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testPrivatePropertyByConvention2() {
    test(new String[] {
        "/** @constructor */ function Foo() {\n" +
        "  /** @type {number} */ this.length_ = 1;\n" +
        "}\n" +
        "/** @type {number} */ Foo.prototype.length_;\n",
        "/** @param {Foo} x */ function f(x) { return x.length_; }\n"
    }, null, BAD_PRIVATE_PROPERTY_ACCESS);
  }

  public void testDeclarationAndConventionConflict1() {
    testSame(
        "/** @constructor */ function Foo() {}" +
        "/** @protected */ Foo.prototype.length_;",
        CONVENTION_MISMATCH, true);
  }

  public void testDeclarationAndConventionConflict2() {
    testSame(
        "/** @constructor */ function Foo() {}\n" +
        "/** @public {number} */ Foo.prototype.length_;\n",
        CONVENTION_MISMATCH, true);
  }

  public void testDeclarationAndConventionConflict3() {
    testSame(
        "/** @constructor */ function Foo() {" +
        "  /** @protected */ this.length_ = 1;\n" +
        "}\n",
        CONVENTION_MISMATCH, true);
  }

  public void testDeclarationAndConventionConflict4a() {
    testSame(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype = { /** @protected */ length_: 1 }\n" +
        "new Foo().length_",
        CONVENTION_MISMATCH, true);
  }

  public void testDeclarationAndConventionConflict4b() {
    testSame(
        "var NS = {}; /** @constructor */ NS.Foo = function() {};" +
        "NS.Foo.prototype = { /** @protected */ length_: 1 };\n" +
        "(new NS.Foo()).length_;",
        CONVENTION_MISMATCH, true);
  }

  public void testDeclarationAndConventionConflict5() {
    testSame(
        "/** @constructor */ function Foo() {}\n" +
        "Foo.prototype = { /** @protected */ get length_() { return 1; } }\n",
        CONVENTION_MISMATCH, true);
  }

  public void testDeclarationAndConventionConflict6() {
    testSame(
        "/** @constructor */ function Foo() {}\n" +
        "Foo.prototype = { /** @protected */ set length_(x) { } }\n",
        CONVENTION_MISMATCH, true);
  }

  public void testDeclarationAndConventionConflict7() {
    testSame("/** @public */ var Foo_;", CONVENTION_MISMATCH, true);
  }

  public void testDeclarationAndConventionConflict8() {
    testSame("/** @package */ var Foo_;", CONVENTION_MISMATCH, true);
  }

  public void testDeclarationAndConventionConflict9() {
    testSame("/** @protected */ var Foo_;", CONVENTION_MISMATCH, true);
  }

  public void testConstantProperty1a() {
    test("/** @constructor */ function A() {" +
        "/** @const */ this.bar = 3;}" +
        "/** @constructor */ function B() {" +
        "/** @const */ this.bar = 3;this.bar += 4;}",
        null, CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testConstantProperty1b() {
    test("/** @constructor */ function A() {" +
        "this.BAR = 3;}" +
        "/** @constructor */ function B() {" +
        "this.BAR = 3;this.BAR += 4;}",
        null, CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testConstantProperty2a() {
    test("/** @constructor */ function Foo() {}" +
        "/** @const */ Foo.prototype.prop = 2;" +
        "var foo = new Foo();" +
        "foo.prop = 3;",
        null , CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testConstantProperty2b() {
    test("/** @constructor */ function Foo() {}" +
        "Foo.prototype.PROP = 2;" +
        "var foo = new Foo();" +
        "foo.PROP = 3;",
        null , CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testNamespaceConstantProperty1() {
    test("" +
        "/** @const */ var o = {};\n" +
        "/** @const */ o.x = 1;" +
        "o.x = 2;",
        null , CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testNamespaceConstantProperty2() {
    test("" +
        "var o = {};\n" +
        "/** @const */ o.x = 1;\n" +
        "o.x = 2;\n",
        null , CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testNamespaceConstantProperty2a() {
    testSame("" +
        "var o = {};\n" +
        "/** @const */ o.x = 1;\n" +
        "var o2 = {};\n" +
        "/** @const */ o2.x = 1;\n");
  }

  public void testNamespaceConstantProperty3() {
    test("" +
        "/** @const */ var o = {};\n" +
        "/** @const */ o.x = 1;" +
        "o.x = 2;",
        null , CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testConstantProperty3a1() {
    // We don't currently check constants defined in object literals.
    testSame("var o = { /** @const */ x: 1 };" +
        "o.x = 2;");
  }

  public void testConstantProperty3a2() {
    // We should report this but we don't.
    testSame("/** @const */ var o = { /** @const */ x: 1 };" +
        "o.x = 2;");
  }

  public void testConstantProperty3b1() {
    // We should report this but we don't.
    testSame("var o = { XYZ: 1 };" +
        "o.XYZ = 2;");
  }

  public void testConstantProperty3b2() {
    // We should report this but we don't.
    testSame("/** @const */ var o = { XYZ: 1 };" +
        "o.XYZ = 2;");
  }

  public void testConstantProperty4() {
    test("/** @constructor */ function cat(name) {}" +
        "/** @const */ cat.test = 1;" +
        "cat.test *= 2;",
        null, CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testConstantProperty4b() {
    test("/** @constructor */ function cat(name) {}" +
        "cat.TEST = 1;" +
        "cat.TEST *= 2;",
        null, CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testConstantProperty5() {
    test("/** @constructor */ function Foo() { this.prop = 1;}" +
        "/** @const */ Foo.prototype.prop;" +
        "Foo.prototype.prop = 2",
        null , CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testConstantProperty6() {
    test("/** @constructor */ function Foo() { this.prop = 1;}" +
        "/** @const */ Foo.prototype.prop = 2;",
        null , CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testConstantProperty7() {
    testSame("/** @constructor */ function Foo() {} " +
      "Foo.prototype.bar_ = function() {};" +
      "/** @constructor \n * @extends {Foo} */ " +
      "function SubFoo() {};" +
      "/** @const */ /** @override */ SubFoo.prototype.bar_ = function() {};" +
      "SubFoo.prototype.baz = function() { this.bar_(); }");
  }

  public void testConstantProperty8() {
    testSame("var o = { /** @const */ x: 1 };" +
        "var y = o.x;");
  }

  public void testConstantProperty9() {
    testSame("/** @constructor */ function A() {" +
        "/** @const */ this.bar = 3;}" +
        "/** @constructor */ function B() {" +
        "this.bar = 4;}");
  }

  public void testConstantProperty10a() {
    testSame("/** @constructor */ function Foo() { this.prop = 1;}" +
        "/** @const */ Foo.prototype.prop;");
  }

  public void testConstantProperty10b() {
    testSame("/** @constructor */ function Foo() { this.PROP = 1;}" +
        "Foo.prototype.PROP;");
  }

  public void testConstantProperty11() {
    test("/** @constructor */ function Foo() {}" +
        "/** @const */ Foo.prototype.bar;" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */ function SubFoo() { this.bar = 5; this.bar = 6; }",
        null , CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testConstantProperty12() {
    testSame("/** @constructor */ function Foo() {}" +
        "/** @const */ Foo.prototype.bar;" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */ function SubFoo() { this.bar = 5; }" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */ function SubFoo2() { this.bar = 5; }");
  }

  public void testConstantProperty13() {
    test("/** @constructor */ function Foo() {}" +
        "/** @const */ Foo.prototype.bar;" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */ function SubFoo() { this.bar = 5; }" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {SubFoo}\n" +
        " */ function SubSubFoo() { this.bar = 5; }",
        null , CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testConstantProperty14() {
    test("/** @constructor */ function Foo() {" +
        "/** @const */ this.bar = 3; delete this.bar; }",
        null, CONST_PROPERTY_DELETED);
  }

  public void testConstantPropertyInExterns() {
    String externs = "" +
        "/** @constructor */ function Foo() {};\n" +
        "/** @const */ Foo.prototype.PROP;";
    String js = "var f = new Foo(); f.PROP = 1; f.PROP = 2;";
    test(externs, js, (String) null, CONST_PROPERTY_REASSIGNED_VALUE, null);
  }

  public void testConstantProperty15() {
    testSame("/** @constructor */ function Foo() {};\n" +
        "Foo.CONST = 100;\n" +
        "/** @type {Foo} */\n" +
        "var foo = new Foo();\n" +
        "/** @type {number} */\n" +
        "foo.CONST = Foo.CONST;");
  }

  public void testConstantProperty15a() {
    test("/** @constructor */ function Foo() { this.CONST = 100; };\n" +
        "/** @type {Foo} */\n" +
        "var foo = new Foo();\n" +
        "/** @type {number} */\n" +
        "foo.CONST = 0;",
        null, CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testConstantProperty15b() {
    test("/** @constructor */ function Foo() {};\n" +
        "Foo.prototype.CONST = 100;\n" +
        "/** @type {Foo} */\n" +
        "var foo = new Foo();\n" +
        "/** @type {number} */\n" +
        "foo.CONST = 0;",
        null, CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testConstantProperty15c() {
    test("" +
        "/** @constructor */ function Bar() {this.CONST = 100;};\n" +
        "/** @constructor \n @extends {Bar} */ function Foo() {};\n" +
        "/** @type {Foo} */\n" +
        "var foo = new Foo();\n" +
        "/** @type {number} */\n" +
        "foo.CONST = 0;",
        null, CONST_PROPERTY_REASSIGNED_VALUE);
  }

  public void testConstantProperty16() {
    testSame(
        "/** @constructor */ function Foo() {};\n" +
        "Foo.CONST = 100;\n" +
        "/** @constructor */ function Bar() {};\n" +
        "Bar.CONST = 100;\n");
  }

  public void testConstantProperty17() {
    testSame(
        "function Foo() {};\n" +
        "Foo.CONST = 100;\n" +
        "function Bar() {};\n" +
        "Bar.CONST = 100;\n");
  }

  public void testConstantProperty18() {
    testSame(
        "/** @param {string} a */\n" +
        "function Foo(a) {};\n" +
        "Foo.CONST = 100;\n" +
        "/** @param {string} a */\n" +
        "function Bar(a) {};\n" +
        "Bar.CONST = 100;\n");
  }

  public void testConstantProperty19() {
    testSame(
        "/** @param {string} a */\n" +
        "function Foo(a) {};\n" +
        "Foo.CONST = 100;\n" +
        "/** @param {number} a */\n" +
        "function Bar(a) {};\n" +
        "Bar.CONST = 100;\n");
  }

  public void testSuppressConstantProperty() {
    testSame("/** @constructor */ function A() {" +
        "/** @const */ this.bar = 3;}" +
        "/**\n" +
        " * @suppress {constantProperty}\n" +
        " * @constructor\n" +
        " */ function B() {" +
        "/** @const */ this.bar = 3;this.bar += 4;}");
  }

  public void testSuppressConstantProperty2() {
    testSame("/** @constructor */ function A() {" +
        "/** @const */ this.bar = 3;}" +
        "/**\n" +
        " * @suppress {const}\n" +
        " * @constructor\n" +
        " */ function B() {" +
        "/** @const */ this.bar = 3;this.bar += 4;}");
  }

  public void testFinalClassCannotBeSubclassed() {
    test(
        "/**\n"
        + " * @constructor\n"
        + " * @const\n"
        + " */ Foo = function() {};\n"
        + "/**\n"
        + " * @constructor\n"
        + " * @extends {Foo}\n*"
        + " */ Bar = function() {};",
        null, EXTEND_FINAL_CLASS);
    test(
        "/**\n"
        + " * @constructor\n"
        + " * @const\n"
        + " */ function Foo() {};\n"
        + "/**\n"
        + " * @constructor\n"
        + " * @extends {Foo}\n*"
        + " */ function Bar() {};",
        null, EXTEND_FINAL_CLASS);
  }
}
