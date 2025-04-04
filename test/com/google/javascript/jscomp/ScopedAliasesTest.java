/*
 * Copyright 2010 The Closure Compiler Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.javascript.jscomp.CompilerOptions.AliasTransformation;
import com.google.javascript.jscomp.CompilerOptions.AliasTransformationHandler;
import com.google.javascript.jscomp.ScopedAliases.InvalidModuleGetHandling;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SourcePosition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ScopedAliases} */
@RunWith(JUnit4.class)
public final class ScopedAliasesTest extends CompilerTestCase {

  private static final String GOOG_SCOPE_START_BLOCK = "goog.scope(function() {";
  private static final String GOOG_SCOPE_END_BLOCK = "});";

  private static final String EXTERNS = MINIMAL_EXTERNS + "var window;";

  private InvalidModuleGetHandling invalidModuleGetHandling;

  private AliasTransformationHandler transformationHandler =
      CompilerOptions.NULL_ALIAS_TRANSFORMATION_HANDLER;

  public ScopedAliasesTest() {
    super(EXTERNS);
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    disableTypeCheck();
    enableRunTypeCheckAfterProcessing();
    enableCreateModuleMap();
    this.invalidModuleGetHandling = InvalidModuleGetHandling.DELETE;
  }

  private void testScoped(String code, String expected, Postcondition... postconditions) {
    ImmutableList<TestPart> parts =
        ImmutableList.<TestPart>builder()
            .add(srcs(GOOG_SCOPE_START_BLOCK + code + GOOG_SCOPE_END_BLOCK))
            .add(expected(expected))
            .add(postconditions)
            .build();
    test(parts.toArray(new TestPart[0]));
  }

  private void testScopedNoChanges(String aliases, String code, Postcondition... postconditions) {
    testScoped(aliases + code, code, postconditions);
  }

  private static final Postcondition VERIFY_TYPES =
      (Compiler compiler) ->
          new TypeVerifyingPass(compiler).process(compiler.getExternsRoot(), compiler.getJsRoot());

  @Test
  public void testLet() {
    testScoped("let d = goog.dom; d.createElement(DIV);", "goog.dom.createElement(DIV)");
  }

  @Test
  public void testConst() {
    testScoped("const d = goog.dom; d.createElement(DIV);", "goog.dom.createElement(DIV)");
  }

  @Test
  public void testOneLevel() {
    testScoped(
        "var g = goog;g.dom.createElement(g.dom.TagName.DIV);",
        "goog.dom.createElement(goog.dom.TagName.DIV);");
  }

  @Test
  public void testTwoLevel() {
    testScoped(
        "var d = goog.dom;d.createElement(d.TagName.DIV);",
        "goog.dom.createElement(goog.dom.TagName.DIV);");
  }

  @Test
  public void testSourceInfo() {
    testScoped(
        """
        var d = dom;
        var e = event;
        alert(e.EventType.MOUSEUP);
        alert(d.TagName.DIV);
        """,
        "alert(event.EventType.MOUSEUP); alert(dom.TagName.DIV);");
    Node root = getLastCompiler().getRoot();
    Node dom = findQualifiedNameNode("dom", root);
    Node event = findQualifiedNameNode("event", root);
    assertWithMessage("Dom line should be after event line.")
        .that(dom.getLineno())
        .isGreaterThan(event.getLineno());
  }

  @Test
  public void testTransitive() {
    testScoped(
        "var d = goog.dom;var DIV = d.TagName.DIV;d.createElement(DIV);",
        "goog.dom.createElement(goog.dom.TagName.DIV);");
  }

  @Test
  public void testTransitiveInSameVar() {
    testScoped(
        "var d = goog.dom, DIV = d.TagName.DIV;d.createElement(DIV);",
        "goog.dom.createElement(goog.dom.TagName.DIV);");
  }

  @Test
  public void testMultipleTransitive() {
    testScoped(
        """
        var g=goog;var d=g.dom;var t=d.TagName;var DIV=t.DIV;
        d.createElement(DIV);
        """,
        "goog.dom.createElement(goog.dom.TagName.DIV);");
  }

  @Test
  public void testFourLevel() {
    testScoped(
        "var DIV = goog.dom.TagName.DIV;goog.dom.createElement(DIV);",
        "goog.dom.createElement(goog.dom.TagName.DIV);");
  }

  @Test
  public void testWorksInClosures() {
    testScoped(
        """
        var DIV = goog.dom.TagName.DIV;
        goog.x = function() {goog.dom.createElement(DIV);};
        """,
        "goog.x = function() {goog.dom.createElement(goog.dom.TagName.DIV);};");
  }

  @Test
  public void testOverridden() {
    // Test that the alias doesn't get unaliased when it's overridden by a
    // parameter.
    testScopedNoChanges("var g = goog;", "goog.x = function(g) {g.z()};");
    // Same for a local.
    testScopedNoChanges("var g = goog;", "goog.x = function() {var g = {}; g.z()};");
  }

  @Test
  public void testTwoScopes() {
    test(
        """
        goog.scope(function() {var g = goog;g.method()});
        goog.scope(function() {g.method();});
        """,
        "goog.method();g.method();");
  }

  @Test
  public void testTwoSymbolsInTwoScopes() {
    test(
        """
        var goog = {};
        goog.scope(function() { var g = goog; g.Foo = function() {}; });
        goog.scope(function() {
          var Foo = goog.Foo; goog.bar = function() { return new Foo(); };
        });
        """,
        """
        var goog = {};
        goog.Foo = function() {};
        goog.bar = function() { return new goog.Foo(); };
        """);
  }

  @Test
  public void testAliasOfSymbolInGoogScope() {
    test(
        """
        var goog = {};
        goog.scope(function() {
          var g = goog;
          g.Foo = function() {};
          var Foo = g.Foo;
          Foo.prototype.bar = function() {};
        });
        """,
        """
        var goog = {}; goog.Foo = function() {};
        goog.Foo.prototype.bar = function() {};
        """);
  }

  @Test
  public void testScopedFunctionReturnThis() {
    test(
        """
        goog.scope(function() {
          var g = goog; g.f = function() { return this; };
        });
        """,
        "goog.f = function() { return this; };");
  }

  @Test
  public void testScopedFunctionAssignsToVar() {
    test(
        """
        goog.scope(function() {
          var g = goog; g.f = function(x) { x = 3; return x; };
        });
        """,
        "goog.f = function(x) { x = 3; return x; };");
  }

  @Test
  public void testScopedFunctionThrows() {
    test(
        """
        goog.scope(function() {
          var g = goog; g.f = function() { throw 'error'; };
        });
        """,
        "goog.f = function() { throw 'error'; };");
  }

  @Test
  public void testPropertiesNotChanged() {
    testScopedNoChanges("var x = goog.dom;", "y.x();");
  }

  @Test
  public void testShadowedVar() {
    test(
        """
        var Popup = {};
        var OtherPopup = {};
        goog.scope(function() {
          var Popup = OtherPopup;
          Popup.newMethod = function() { return new Popup(); };
        });
        """,
        """
        var Popup = {};
        var OtherPopup = {};
        OtherPopup.newMethod = function() { return new OtherPopup(); };
        """);
  }

  @Test
  public void testShadowedScopedVar() {
    test(
        """
        var goog = {};
        goog.bar = {};
        goog.scope(function() {
          var bar = goog.bar;
          // This is bogus, because when the aliases are expanded, goog will shadow goog.bar.
          bar.newMethod = function(goog) { return goog + bar; };
        });
        """,
        """
        var goog={};
        goog.bar={};
        goog.bar.newMethod=function(goog$jscomp$1){return goog$jscomp$1 + goog.bar}
        """);
  }

  @Test
  public void testShadowedScopedVarTwoScopes() {
    test(
        """
        var goog = {};
        goog.bar = {};
        goog.scope(function() {
          var bar = goog.bar;
          bar.newMethod = function(goog, a) { return bar + a; };
        });
        goog.scope(function() {
          var bar = goog.bar;
          bar.newMethod2 = function(goog, b) { return bar + b; };
        });
        """,
        """
        var goog={};
        goog.bar={};
        goog.bar.newMethod=function(goog$jscomp$1, a){return goog.bar + a};
        goog.bar.newMethod2=function(goog$jscomp$1, b){return goog.bar + b};
        """);
  }

  @Test
  public void testIconImage2() {
    testScoped(
        """
        var getIconImage = (function(){
         let _iconsImage;
         function getIconImage(){
           _iconsImage = 3;
           return _iconsImage;
         }
        })
        """,
        """
        var $jscomp$scope$m1146332801$0$getIconImage = (function(){
         let _iconsImage;
         function getIconImage(){
           _iconsImage = 3;
           return _iconsImage;
         }
        })
        """);
  }

  @Test
  public void testIconImage() {
    testScoped(
        """
        let _iconsImage;
        function getIconImage(){
          _iconsImage = 3;
          return _iconsImage;
        }
        """,
        """
        var $jscomp$scope$m1146332801$1$getIconImage = function() {
          $jscomp$scope$m1146332801$0$_iconsImage = 3;
          return $jscomp$scope$m1146332801$0$_iconsImage;
        };
        var $jscomp$scope$m1146332801$0$_iconsImage;
        """);
  }

  @Test
  public void testFunctionDeclarationInScope() {
    testScoped("var foo = function() {};", "var $jscomp$scope$m1146332801$0$foo = function() {};");
  }

  @Test
  public void testFunctionDeclarationInScope_letConst() {
    testScoped(
        "var baz = goog.bar; let foo = function() {return baz;};",
        "var $jscomp$scope$m1146332801$0$foo = function() {return goog.bar;};");
    testScoped(
        "var baz = goog.bar; const foo = function() {return baz;};",
        "var $jscomp$scope$m1146332801$0$foo = function() {return goog.bar;};");
  }

  @Test
  public void testLetConstShadowing() {
    testScoped(
        """
        var foo = goog.bar; var f = function() {
        let foo = baz; return foo;};
        """,
        """
        var $jscomp$scope$m1146332801$0$f = function() {
        let foo = baz; return foo;};
        """);
    testScoped(
        """
        var foo = goog.bar; var f = function() {
        const foo = baz; return foo;};
        """,
        """
        var $jscomp$scope$m1146332801$0$f = function() {
        const foo = baz; return foo;};
        """);
  }

  @Test
  public void testYieldExpression() {
    testScoped(
        "var foo = goog.bar; var f = function*() {yield foo;};",
        "var $jscomp$scope$m1146332801$0$f = function*() {yield goog.bar;};");
  }

  @Test
  public void testDestructuringError() {
    testScopedError("var [x] = [1];", ScopedAliases.GOOG_SCOPE_NON_ALIAS_LOCAL);
  }

  @Test
  public void testObjectDescructuringError1() {
    testScopedError("var {x} = {x: 1};", ScopedAliases.GOOG_SCOPE_NON_ALIAS_LOCAL);
  }

  @Test
  public void testObjectDescructuringError2() {
    testScopedError("var {x: y} = {x: 1};", ScopedAliases.GOOG_SCOPE_NON_ALIAS_LOCAL);
  }

  @Test
  public void testNonTopLevelDestructuring() {
    testScoped(
        "var f = function() {var [x, y] = [1, 2];};",
        "var $jscomp$scope$m1146332801$0$f = function() {var [x, y] = [1, 2];};");
  }

  @Test
  public void testArrowFunction() {
    testScoped(
        "var foo = goog.bar; var v = (x => x + foo);",
        "var $jscomp$scope$m1146332801$0$v = (x => x + goog.bar)");
  }

  @Test
  public void testClassDefinition1() {
    testScoped("class Foo {}", "var $jscomp$scope$m1146332801$0$Foo=class{}");
  }

  @Test
  public void testClassDefinition2() {
    testScoped(
        """
        var bar = goog.bar;
        class Foo { constructor() { this.x = bar; }}
        """,
        "var $jscomp$scope$m1146332801$0$Foo = class { constructor() { this.x = goog.bar; } }");
  }

  @Test
  public void testClassDefinition3() {
    testScoped(
        """
        var bar = {};
        bar.Foo = class {};
        """,
        """
        var $jscomp$scope$m1146332801$0$bar = {}; $jscomp$scope$m1146332801$0$bar.Foo = class
         {}
        """);
  }

  @Test
  public void testClassDefinition_letConst() {
    testScoped(
        """
        let bar = {};
        bar.Foo = class {};
        """,
        """
        var $jscomp$scope$m1146332801$0$bar = {}; $jscomp$scope$m1146332801$0$bar.Foo = class
         {}
        """);
    testScoped(
        """
        const bar = {};
        bar.Foo = class {};
        """,
        """
        var $jscomp$scope$m1146332801$0$bar = {}; $jscomp$scope$m1146332801$0$bar.Foo = class
         {}
        """);
  }

  @Test
  public void testDefaultParameter() {
    testScoped(
        "var foo = goog.bar; var f = function(y=foo) {};",
        "var $jscomp$scope$m1146332801$0$f = function(y=goog.bar) {};");
  }

  /**
   * Make sure we don't hit an IllegalStateException for this case.
   *
   * @see https://github.com/google/closure-compiler/issues/400
   */
  @Test
  public void testObjectLiteral() {
    testScoped(
        """
        var Foo = goog.Foo;
        goog.x = {
          /** @param {Foo} foo */
          y: function(foo) { }
        };
        """,
        """
        goog.x = {
          /** @param {goog.Foo} foo */
          y: function(foo) {}
        };
        """);

    testScoped(
        """
        var Foo = goog.Foo;
        goog.x = {
          y: /** @param {Foo} foo */ function(foo) {}
        };
        """,
        """
        goog.x = {
          y: /** @param {goog.Foo} foo */ function(foo) {}
        };
        """);

    testScoped(
        """
        var Foo = goog.Foo;
        goog.x = {
          y: /** @type {function(Foo)} */ (function(foo) {})
        };
        """,
        """
        goog.x = {
          y: /** @type {function(goog.Foo)} */ (function(foo) {})
        };
        """);
  }

  @Test
  public void testObjectLiteralShorthand() {
    testScoped(
        "var bar = goog.bar; var Foo = {bar};",
        "var $jscomp$scope$m1146332801$0$Foo={bar: goog.bar};");
  }

  @Test
  public void testObjectLiteralMethods() {
    testScoped(
        "var foo = goog.bar; var obj = {toString() {return foo}};",
        "var $jscomp$scope$m1146332801$0$obj = {toString() {return goog.bar}};");
  }

  @Test
  public void testObjectLiteralComputedPropertyNames() {
    testScoped(
        "var foo = goog.bar; var obj = {[(() => foo)()]: baz};",
        "var $jscomp$scope$m1146332801$0$obj = {[(() => goog.bar)()]:baz};");
    testScoped(
        "var foo = goog.bar; var obj = {[x => x + foo]: baz};",
        "var $jscomp$scope$m1146332801$0$obj = {[x => x + goog.bar]:baz};");
  }

  @Test
  public void testJsDocNotIgnored() {
    enableTypeCheck();

    String externs =
        MINIMAL_EXTERNS
            + """
            /** @const */ var ns = {};
            /** @constructor */
            ns.Foo;

            var goog;
            /** @param {function()} fn */
            goog.scope = function(fn) {}
            """;

    String js =
        """
        goog.scope(function() {
          var Foo = ns.Foo;
          var x = {
            /** @param {Foo} foo */ y: function(foo) {}
          };
          x.y('');
        });
        """;
    test(externs(externs), srcs(js), warning(TypeValidator.TYPE_MISMATCH_WARNING));

    js =
        """
        goog.scope(function() {
          var Foo = ns.Foo;
          var x = {
            y: /** @param {Foo} foo */ function(foo) {}
          };
          x.y('');
        });
        """;
    test(externs(externs), srcs(js), warning(TypeValidator.TYPE_MISMATCH_WARNING));
  }

  @Test
  public void testUsingObjectLiteralToEscapeScoping() {
    // There are many ways to shoot yourself in the foot with goog.scope
    // and make the compiler generate bad code. We generally don't care.
    //
    // We only try to protect against accidental mis-use, not deliberate
    // mis-use.
    test(
        """
        var goog = {};
        goog.bar = {};
        goog.scope(function() {
          var bar = goog.bar;
          var baz = goog.bar.baz;
          goog.foo = function() {
            goog.bar = {baz: 3};
            return baz;
          };
        });
        """,
        """
        var goog = {};
        goog.bar = {};
        goog.foo = function(){
          goog.bar = {baz:3};
          return goog.bar.baz;
        };
        """);
  }

  private void testTypes(String aliases, String code) {
    testScopedNoChanges(aliases, code, VERIFY_TYPES);
  }

  @Test
  public void testJsDocType() {
    testTypes(
        "var x = goog.Timer;",
        """
        /** @type {goog.Timer} */ types.actual;
        /** @type {goog.Timer} */ types.expected;
        """);
  }

  @Test
  public void testJsDocParameter() {
    testTypes(
        "var x = goog.Timer;",
        """
        /** @param {goog.Timer} a */ types.actual;
        /** @param {goog.Timer} a */ types.expected;
        """);
  }

  @Test
  public void testJsDocExtends() {
    testTypes(
        "var x = goog.Timer;",
        """
        /** @extends {goog.Timer} */ types.actual;
        /** @extends {goog.Timer} */ types.expected;
        """);
  }

  @Test
  public void testJsDocImplements() {
    testTypes(
        "var x = goog.Timer;",
        """
        /** @implements {goog.Timer} */ types.actual;
        /** @implements {goog.Timer} */ types.expected;
        """);
  }

  @Test
  public void testJsDocEnum() {
    testTypes(
        "var x = goog.Timer;",
        """
        /** @enum {goog.Timer} */ types.actual;
        /** @enum {goog.Timer} */ types.expected;
        """);
  }

  @Test
  public void testJsDocReturn() {
    testTypes(
        "var x = goog.Timer;",
        """
        /** @return {goog.Timer} */ types.actual;
        /** @return {goog.Timer} */ types.expected;
        """);
  }

  @Test
  public void testJsDocThis() {
    testTypes(
        "var x = goog.Timer;",
        """
        /** @this {goog.Timer} */ types.actual;
        /** @this {goog.Timer} */ types.expected;
        """);
  }

  @Test
  public void testJsDocThrows() {
    testTypes(
        "var x = goog.Timer;",
        """
        /** @throws {goog.Timer} */ types.actual;
        /** @throws {goog.Timer} */ types.expected;
        """);
  }

  @Test
  public void testJsDocSubType() {
    testTypes(
        "var x = goog.Timer;",
        """
        /** @type {goog.Timer.Enum} */ types.actual;
        /** @type {goog.Timer.Enum} */ types.expected;
        """);
  }

  @Test
  public void testJsDocTypedef() {
    testTypes(
        "var x = goog.Timer;",
        """
        /** @typedef {goog.Timer} */ types.actual;
        /** @typedef {goog.Timer} */ types.expected;
        """);

    testScoped(
        """
        /** @typedef {string} */ var s;
        /** @type {s} */ var t;
        """,
        """
        /** @typedef {string} */ var $jscomp$scope$m1146332801$0$s;
        /** @type {$jscomp$scope$m1146332801$0$s} */ var $jscomp$scope$m1146332801$1$t;
        """);

    testScoped(
        """
        /** @typedef {string} */ let s;
        /** @type {s} */ var t;
        """,
        """
        /** @typedef {string} */ var $jscomp$scope$m1146332801$0$s;
        /** @type {$jscomp$scope$m1146332801$0$s} */ var $jscomp$scope$m1146332801$1$t;
        """);
  }

  @Test
  public void testJsDocRecord() {
    enableTypeCheck();
    test(
        """
        /** @const */ var ns = {};
        goog.scope(function () {
          var x = goog.Timer;
          /** @type {{x: string}} */ ns.y = {'goog.Timer': 'x'};
        });
        """,
        """
        /** @const */ var ns = {};
        /** @type {{x: string}} */ ns.y = {'goog.Timer': 'x'};
        """,
        warning(TypeValidator.TYPE_MISMATCH_WARNING));
  }

  @Test
  public void testArrayJsDoc() {
    testTypes(
        "var x = goog.Timer;",
        """
        /** @type {Array.<goog.Timer>} */ types.actual;
        /** @type {Array.<goog.Timer>} */ types.expected;
        """);
  }

  @Test
  public void testObjectJsDoc() {
    testTypes(
        "var x = goog.Timer;",
        """
        /** @type {{someKey: goog.Timer}} */ types.actual;
        /** @type {{someKey: goog.Timer}} */ types.expected;
        """);
    testTypes(
        "var x = goog.Timer;",
        """
        /** @type {{x: number}} */ types.actual;
        /** @type {{x: number}} */ types.expected;
        """);
  }

  @Test
  public void testObjectJsDoc2() {
    testTypes(
        "var x = goog$Timer;",
        """
        /** @type {{someKey: goog$Timer}} */ types.actual;
        /** @type {{someKey: goog$Timer}} */ types.expected;
        """);
  }

  @Test
  public void testUnionJsDoc() {
    testTypes(
        "var x = goog.Timer;",
        """
        /** @type {goog.Timer|Object} */ types.actual;
        /** @type {goog.Timer|Object} */ types.expected;
        """);
  }

  @Test
  public void testFunctionJsDoc() {
    testTypes(
        "var x = goog.Timer;",
        """
        /** @type {function(goog.Timer) : void} */ types.actual;
        /** @type {function(goog.Timer) : void} */ types.expected;
        """);
    testTypes(
        "var x = goog.Timer;",
        """
        /** @type {function() : goog.Timer} */ types.actual;
        /** @type {function() : goog.Timer} */ types.expected;
        """);
  }

  @Test
  public void testForwardJsDoc() {
    testScoped(
        """
        /**
         * @constructor
         */
        foo.Foo = function() {};
        /** @param {Foo.Bar} x */ foo.Foo.actual = function(x) {3};
        var Foo = foo.Foo;
        /** @constructor */ Foo.Bar = function() {};
        /** @param {foo.Foo.Bar} x */ foo.Foo.expected = function(x) {};
        """,
        """
        /**
         * @constructor
         */
        foo.Foo = function() {};
        /** @param {foo.Foo.Bar} x */ foo.Foo.actual = function(x) {3};
        /** @constructor */ foo.Foo.Bar = function() {};
        /** @param {foo.Foo.Bar} x */ foo.Foo.expected = function(x) {};
        """,
        VERIFY_TYPES);
  }

  @Test
  public void testTestTypes() {
    try {
      testTypes(
          "var x = goog.Timer;",
          """
          /** @type {function() : x} */ types.actual;
          /** @type {function() : wrong.wrong} */ types.expected;
          """);
      throw new Error("Test types should fail here.");
    } catch (AssertionError expected) {
    }
  }

  @Test
  public void testNullType() {
    testTypes(
        "var x = goog.Timer;",
        """
        /** @param draggable */ types.actual;
        /** @param draggable */ types.expected;
        """);
  }

  @Test
  public void testJSDocCopiedForFunctions() {
    testScoped(
        "/** @export */ function Foo() {}",
        "/** @export */ var $jscomp$scope$m1146332801$0$Foo =/** @export */ function() {}");
  }

  @Test
  public void testJSDocCopiedForClasses() {
    testScoped(
        "/** @export */ class Foo {}",
        "/** @export */ var $jscomp$scope$m1146332801$0$Foo = /** @export */ class {}");
  }

  @Test
  public void testIssue772() {
    testTypes(
        """
        var b = a.b;
        var c = b.c;
        """,
        """
        /** @param {a.b.c.MyType} x */ types.actual;
        /** @param {a.b.c.MyType} x */ types.expected;
        """);
  }

  @Test
  public void testInlineJsDoc() {
    enableTypeCheck();
    test(
        srcs(
            """
            /** @const */ var ns = {};
            /** @constructor */ ns.A = function() {};
            goog.scope(function() {
              /** @const */ var A = ns.A;
              var /** @type {?A} */ b = null;
            });
            """),
        expected(
            """
            /** @const */ var ns = {};
            /** @constructor */ ns.A = function() {};
            /** @type {?ns.A} */ var $jscomp$scope$m1146332801$0$b = null;
            """),
        VERIFY_TYPES);
  }

  @Test
  public void testInlineReturn() {
    enableTypeCheck();
    test(
        srcs(
            """
            /** @const */ var ns = {};
            /** @constructor */ ns.A = function() {};
            goog.scope(function() {
              /** @const */ var A = ns.A;
              function /** ?A */ b() { return null; }
            });
            """),
        expected(
            """
            /** @const */ var ns = {};
            /** @constructor */ ns.A = function() {};
            // TODO(moz): See if we can avoid generating duplicate @return's
            /** @return {?ns.A} */ var $jscomp$scope$m1146332801$0$b =
                /** @return {?ns.A} */ function() { return null; };
            """),
        VERIFY_TYPES);
  }

  @Test
  public void testInlineParam() {
    enableTypeCheck();
    test(
        srcs(
            """
            /** @const */ var ns = {};
            /** @constructor */ ns.A = function() {};
            goog.scope(function() {
              /** @const */ var A = ns.A;
              function b(/** ?A */ bee) {}
            });
            """),
        expected(
            """
            /** @const */ var ns = {};
            /** @constructor */ ns.A = function() {};
            var $jscomp$scope$m1146332801$0$b = function(/** ?ns.A */ bee) {};
            """),
        VERIFY_TYPES);
  }

  // TODO(robbyw): What if it's recursive?  var goog = goog.dom;

  // FAILURE CASES

  private void testScopedError(String code, DiagnosticType expectedError) {
    testError("goog.scope(function() {" + code + "});", expectedError);
  }

  @Test
  public void testScopedThis() {
    testScopedError("this.y = 10;", ScopedAliases.GOOG_SCOPE_REFERENCES_THIS);
    testScopedError("var x = this;", ScopedAliases.GOOG_SCOPE_REFERENCES_THIS);
    testScopedError("fn(this);", ScopedAliases.GOOG_SCOPE_REFERENCES_THIS);
  }

  @Test
  public void testAliasRedefinition() {
    testScopedError("var x = goog.dom; x = goog.events;", ScopedAliases.GOOG_SCOPE_ALIAS_REDEFINED);
  }

  @Test
  public void testAliasNonRedefinition() {
    test("var y = {}; goog.scope(function() { goog.dom = y; });", "var y = {}; goog.dom = y;");
  }

  @Test
  public void testCtorAlias() {
    test(
        """
        var x = {y: {}};
        goog.scope(function() {
          var y = x.y;
          y.ClassA = function() { this.b = new ClassB(); };
          y.ClassB = function() {};
          var ClassB = y.ClassB;
        });
        """,
        """
        var x = {y: {}};
        x.y.ClassA = function() { this.b = new x.y.ClassB(); };
        x.y.ClassB = function() { };
        """);
  }

  @Test
  public void testAliasCycle() {
    testError(
        """
        var x = {y: {}};
        goog.scope(function() {
          var y = z.x;
          var z = y.x;
          y.ClassA = function() {};
          z.ClassB = function() {};
        });
        """,
        ScopedAliases.GOOG_SCOPE_ALIAS_CYCLE);
  }

  @Test
  public void testScopedReturn() {
    testScopedError("return;", ScopedAliases.GOOG_SCOPE_USES_RETURN);
    testScopedError("var x = goog.dom; return;", ScopedAliases.GOOG_SCOPE_USES_RETURN);
  }

  @Test
  public void testScopedThrow() {
    testScopedError("throw 'error';", ScopedAliases.GOOG_SCOPE_USES_THROW);
  }

  @Test
  public void testUsedImproperly() {
    testError("var x = goog.scope(function() {});", ScopedAliases.GOOG_SCOPE_MUST_BE_ALONE);
    testError(
        "var f = function() { goog.scope(function() {}); }",
        ScopedAliases.GOOG_SCOPE_MUST_BE_IN_GLOBAL_SCOPE);
  }

  @Test
  public void testScopeCallInIf() {
    test("if (true) { goog.scope(function() {});}", "if (true) {}");
    test("if (true) { goog.scope(function()  { var x = foo; });}", "if (true) { }");
    test(
        "if (true) { goog.scope(function()  { var x = foo; console.log(x); });}",
        "if (true) { console.log(foo); }");
  }

  @Test
  public void testBadParameters() {
    testError("goog.scope()", ScopedAliases.GOOG_SCOPE_HAS_BAD_PARAMETERS);
    testError("goog.scope(10)", ScopedAliases.GOOG_SCOPE_HAS_BAD_PARAMETERS);
    testError("goog.scope(function() {}, 10)", ScopedAliases.GOOG_SCOPE_HAS_BAD_PARAMETERS);
    testError("goog.scope(function z() {})", ScopedAliases.GOOG_SCOPE_HAS_BAD_PARAMETERS);
    testError("goog.scope(function(a, b, c) {})", ScopedAliases.GOOG_SCOPE_HAS_BAD_PARAMETERS);
  }

  @Test
  public void testNonAliasLocal() {
    testScopedError("for (var k in { a: 1, b: 2 }) {}", ScopedAliases.GOOG_SCOPE_NON_ALIAS_LOCAL);
    testScopedError("for (var k of [1, 2, 3]) {}", ScopedAliases.GOOG_SCOPE_NON_ALIAS_LOCAL);
  }

  @Test
  public void testInvalidVariableInScope() {
    testScopedError("if (true) { function f() {}}", ScopedAliases.GOOG_SCOPE_INVALID_VARIABLE);
    testScopedError("for (;;) { function f() {}}", ScopedAliases.GOOG_SCOPE_INVALID_VARIABLE);
  }

  @Test
  public void testWithCatch1() {
    testScoped(
        "var x = foo(); try { } catch (e) {}",
        "var $jscomp$scope$m1146332801$0$x = foo(); try { } catch (e) {}");
  }

  @Test
  public void testWithCatch2() {
    testScoped(
        "try { } catch (e) {var x = foo();}",
        "try { } catch (e) {var $jscomp$scope$m1146332801$0$x = foo();}");
  }

  @Test
  public void testVariablesInCatchBlock() {
    testScopedNoChanges("", "try {} catch (e) {}");
    testScopedNoChanges("", "try {} catch (e) { let x = foo }");
    testScopedNoChanges("", "try {} catch (e) { const x = foo }");
  }

  @Test
  public void testLetConstInBlock() {
    testScopedNoChanges("", "if (true) {let x = foo;}");
    testScopedNoChanges("", "if (true) {const x = foo;}");
  }

  @Test
  public void testHoistedAliases() {
    testScoped("if (true) { var x = foo;}", "if (true) {}");
    testScoped("if (true) { var x = foo; console.log(x); }", "if (true) { console.log(foo); }");
  }

  @Test
  public void testOkAliasLocal() {
    testScoped("var x = 10;", "var $jscomp$scope$m1146332801$0$x = 10");
    testScoped("var x = goog['dom'];", "var $jscomp$scope$m1146332801$0$x = goog['dom']");
    testScoped(
        "var x = 10, y = 9;",
        "var $jscomp$scope$m1146332801$0$x = 10; var $jscomp$scope$m1146332801$1$y = 9;");
    testScoped(
        "var x = 10, y = 9; goog.getX = function () { return x + y; }",
        """
        var $jscomp$scope$m1146332801$0$x = 10; var $jscomp$scope$m1146332801$1$y = 9;
        goog.getX = function () {
            return $jscomp$scope$m1146332801$0$x + $jscomp$scope$m1146332801$1$y; }
        """);
  }

  @Test
  public void testOkAliasLocal_letConst() {
    testScoped("let x = 10;", "var $jscomp$scope$m1146332801$0$x = 10");
    testScoped("const x = 10;", "var $jscomp$scope$m1146332801$0$x = 10");
  }

  @Test
  public void testHoistedFunctionDeclaration() {
    testScoped(
        " g(f); function f() {} ",
        """
         var $jscomp$scope$m1146332801$0$f = function () {};
        g($jscomp$scope$m1146332801$0$f);\s
        """);
  }

  @Test
  public void testAliasReassign() {
    testScopedError("var x = 3; x = 5;", ScopedAliases.GOOG_SCOPE_ALIAS_REDEFINED);
  }

  @Test
  public void testMultipleLocals() {
    test(
        """
        goog.scope(function () { var x = 3; });
        goog.scope(function () { var x = 4; });
        """,
        "var $jscomp$scope$m1146332801$0$x = 3; var $jscomp$scope$m1146332801$1$x = 4");
  }

  @Test
  public void testIssue1103a() {
    test(
        """
        goog.scope(function () {
          var a;
          foo.bar = function () { a = 1; };
        });
        """,
        """
        var $jscomp$scope$m1146332801$0$a;
        foo.bar = function () {
          $jscomp$scope$m1146332801$0$a = 1;
        }
        """);
  }

  @Test
  public void testIssue1103b() {
    test(
        """
        goog.scope(function () {
          var a = foo, b, c = 1;
        });
        """,
        "var $jscomp$scope$m1146332801$0$b; var $jscomp$scope$m1146332801$1$c=1");
  }

  @Test
  public void testIssue1103c() {
    test(
        """
        goog.scope(function () {
          /** @type {number} */ var a;
        });
        """,
        "/** @type {number} */ var $jscomp$scope$m1146332801$0$a;");
  }

  @Test
  public void testIssue1144() {
    test(
        """
        var ns = {};
        ns.sub = {};
        /** @constructor */ ns.sub.C = function () {};
        goog.scope(function () {
          var sub = ns.sub;
          /** @type {sub.C} */
          var x = null;
        });
        """,
        """
        var ns = {};
        ns.sub = {};
        /** @constructor */ ns.sub.C = function () {};
        /** @type {ns.sub.C} */
        var $jscomp$scope$m1146332801$0$x = null;
        """);
  }

  // https://github.com/google/closure-compiler/issues/2211
  @Test
  public void testIssue2211() {
    test(
        """
        var ns = {};
        var y = 1;
        goog.scope(function () {
          ns.fact = function y(n) {
            return n == 1 ? 1 : n * y(n - 1);
          };
        });
        """,
        """
        var ns = {};
        var y = 1;
        ns.fact = function y$jscomp$scopedAliases$0(n) {
          return n == 1 ? 1 : n * y$jscomp$scopedAliases$0(n - 1);
        };
        """);
  }

  // https://github.com/google/closure-compiler/issues/2211
  @Test
  public void testIssue2211b() {
    test(
        """
        var ns = {};
        var y = 1;
        goog.scope(function () {
          function x(y) {}
          ns.fact = function y(n) {
            return n == 1 ? 1 : n * y(n - 1);
          };
        });
        """,
        """
        var ns = {};
        var y = 1;
        var $jscomp$scope$m1146332801$0$x = function (y) {};
        ns.fact = function y$jscomp$scopedAliases$0(n) {
          return n == 1 ? 1 : n * y$jscomp$scopedAliases$0(n - 1);
        };
        """);
  }

  // https://github.com/google/closure-compiler/issues/2211
  @Test
  public void testIssue2211c() {
    testScoped(
        """
        foo(() => {
          const y = function y() {
            use(y);
          };
        });
        """,
        """
        foo(() => {
          const y = function y$jscomp$scopedAliases$0() {
            use(y$jscomp$scopedAliases$0);
          };
        });
        """);
  }

  @Test
  public void testGoogModuleGet1() {
    test(
        """
        goog.provide('provided');
        goog.provide('other.name.Foo');
        goog.scope(function() {
          var Foo = goog.module.get('other.name.Foo')
          /** @type {!Foo} */
          provided.f = new Foo;
        });
        """,
        """
        goog.provide('provided');
        goog.provide('other.name.Foo');
        /** @type {!other.name.Foo} */
        provided.f = new (goog.module.get('other.name.Foo'));
        """);
  }

  @Test
  public void testGoogModuleGet2() {
    test(
        """
        goog.provide('foo.baz');
        goog.provide('other.thing');

        goog.scope(function() {

        const a = goog.module.get('other.thing');
        const b = a.b;
        foo.baz = b

        }); // goog.scope
        """,
        """
        goog.provide('foo.baz');
        goog.provide('other.thing');
        foo.baz = goog.module.get('other.thing').b;
        """);
  }

  @Test
  public void testGoogModuleGet3() {
    test(
        """
        goog.provide('foo.baz');
        goog.provide('other.thing');

        goog.scope(function() {

        const a = goog.module.get('other.thing').b;
        foo.baz = a

        }); // goog.scope
        """,
        """
        goog.provide('foo.baz');
        goog.provide('other.thing');
        foo.baz = goog.module.get('other.thing').b;
        """);
  }

  @Test
  public void testGoogModuleGet_ofModule() {
    test(
        srcs(
            "goog.module('other.name.Foo');",
            """
            goog.provide('provided');
            goog.scope(function() {
              var Foo = goog.module.get('other.name.Foo')
              /** @type {!Foo} */
              provided.f = new Foo;
            });
            """),
        expected(
            "goog.module('other.name.Foo');",
            """
            goog.provide('provided');
            /** @type {!other.name.Foo} */
            provided.f = new (goog.module.get('other.name.Foo'));
            """));
  }

  @Test
  public void testGoogModuleGet_missing_noDeletion() {
    invalidModuleGetHandling = InvalidModuleGetHandling.PRESERVE;
    test(
        """
        goog.provide('provided');

        goog.scope(function() {
          var Foo = goog.module.get('missing.name.Foo');
          /** @type {!Foo} */
          provided.f = new Foo;
        });
        """,
        """
        goog.provide('provided');

        /** @type {!missing.name.Foo} */
        provided.f = new (goog.module.get('missing.name.Foo'));
        """);
  }

  @Test
  public void testGoogModuleGet_missing() {
    test(
        """
        goog.provide('provided');

        goog.scope(function() {
          var Foo = goog.module.get('missing.name.Foo')
          /** @type {!Foo} */
          provided.f = new Foo;
        });
        """,
        """
        goog.provide('provided');

        /** @type {!Foo} */
        provided.f = new Foo();
        """);
  }

  @Test
  public void testGoogModuleGet_missingUsedInRhs() {
    test(
        """
        goog.provide('provided');

        goog.scope(function() {
          var Foo = goog.module.get('missing.name.Foo')
          provided.Bar = class extends Foo {};
        });
        """,
        """
        goog.provide('provided');

        provided.Bar=class extends Foo {}
        """);
  }

  @Test
  public void testGoogModuleGet_missingRecursive() {
    test(
        """
        goog.provide('provided');

        goog.scope(function() {
          var Foo = goog.module.get('missing.name.Foo')
          /** @type {!Foo.Bar} */
          provided.f = new Foo.Bar;
        });
        """,
        """
        goog.provide('provided');

        /** @type {!Foo.Bar} */
        provided.f = new Foo.Bar();
        """);
  }

  @Test
  public void testGoogModuleGet_missingProperty() {
    test(
        """
        goog.provide('provided');

        goog.scope(function() {
          var Bar = goog.module.get('missing.name.Foo').Bar
          /** @type {!Bar} */
          provided.f = new Bar;
        });
        """,
        """
        goog.provide('provided');

        /** @type {!Bar} */
        provided.f = new Bar;
        """);
  }

  @Test
  public void testGoogModuleGet_aliasOfDeletedModuleGetAlias() {
    test(
        """
        goog.provide('provided');

        goog.scope(function() {
          var Foo = goog.module.get('missing.name.Foo');
          var Bar = Foo.Bar
          /** @type {!Bar} */
          provided.f = new Bar;
        });
        """,
        """
        goog.provide('provided');

        /** @type {!Foo.Bar} */
        provided.f = new Foo.Bar();
        """);
  }

  @Test
  public void testObjectPattern() {
    testScopedNoChanges("", "{foo: ({bar}) => baz};");
  }

  @Test
  public void testTypeCheck() {
    enableTypeCheck();

    test(
        """
        goog.scope(function () {
          /** @constructor */ function F() {}
          /** @return {F} */ function createFoo() { return 1; }
        });
        """,
        """
        /** @return {$jscomp$scope$m1146332801$0$F} */
        var $jscomp$scope$m1146332801$1$createFoo =
        /** @return {$jscomp$scope$m1146332801$0$F} */
        function() { return 1; };
        /** @constructor */ var $jscomp$scope$m1146332801$0$F =
        /** @constructor */ function() { };
        """,
        warning(TypeValidator.TYPE_MISMATCH_WARNING));
  }

  // Alias Recording Tests
  // TODO(tylerg) : update these to EasyMock style tests once available
  @Test
  public void testNoGoogScope() {
    String fullJsCode = "var g = goog;\n g.dom.createElement(g.dom.TagName.DIV);";
    TransformationHandlerSpy spy = new TransformationHandlerSpy();
    transformationHandler = spy;
    testSame(fullJsCode);

    assertThat(spy.observedPositions).isEmpty();
  }

  @Test
  public void testRecordOneAlias() {
    String fullJsCode =
        GOOG_SCOPE_START_BLOCK
            + "var g = goog;\n g.dom.createElement(g.dom.TagName.DIV);\n"
            + GOOG_SCOPE_END_BLOCK;
    String expectedJsCode = "goog.dom.createElement(goog.dom.TagName.DIV);\n";

    TransformationHandlerSpy spy = new TransformationHandlerSpy();
    transformationHandler = spy;
    test(fullJsCode, expectedJsCode);

    assertThat(spy.observedPositions).containsKey("testcode");
    List<SourcePosition<AliasTransformation>> positions = spy.observedPositions.get("testcode");
    assertThat(positions).hasSize(1);
    verifyAliasTransformationPosition(1, 0, 2, 1, positions.get(0));

    assertThat(spy.constructedAliases).hasSize(1);
    AliasSpy aliasSpy = (AliasSpy) spy.constructedAliases.get(0);
    assertThat(aliasSpy.observedDefinitions).containsEntry("g", "goog");
  }

  @Test
  public void testRecordOneAlias2() {
    String fullJsCode =
        GOOG_SCOPE_START_BLOCK
            + "var g$1 = goog;\n g$1.dom.createElement(g$1.dom.TagName.DIV);\n"
            + GOOG_SCOPE_END_BLOCK;
    String expectedJsCode = "goog.dom.createElement(goog.dom.TagName.DIV);\n";

    TransformationHandlerSpy spy = new TransformationHandlerSpy();
    transformationHandler = spy;
    test(fullJsCode, expectedJsCode);

    assertThat(spy.observedPositions).containsKey("testcode");
    List<SourcePosition<AliasTransformation>> positions = spy.observedPositions.get("testcode");
    assertThat(positions).hasSize(1);
    verifyAliasTransformationPosition(1, 0, 2, 1, positions.get(0));

    assertThat(spy.constructedAliases).hasSize(1);
    AliasSpy aliasSpy = (AliasSpy) spy.constructedAliases.get(0);
    assertThat(aliasSpy.observedDefinitions).containsEntry("g$1", "goog");
  }

  @Test
  public void testRecordMultipleAliases() {
    String fullJsCode =
        GOOG_SCOPE_START_BLOCK
            + """
            var g = goog;
            var b= g.bar;
            var f = goog.something.foo;
            g.dom.createElement(g.dom.TagName.DIV);
            b.foo();
            """
            + GOOG_SCOPE_END_BLOCK;
    String expectedJsCode = "goog.dom.createElement(goog.dom.TagName.DIV);\n goog.bar.foo();";
    TransformationHandlerSpy spy = new TransformationHandlerSpy();
    transformationHandler = spy;
    test(fullJsCode, expectedJsCode);

    assertThat(spy.observedPositions).containsKey("testcode");
    List<SourcePosition<AliasTransformation>> positions = spy.observedPositions.get("testcode");
    assertThat(positions).hasSize(1);
    verifyAliasTransformationPosition(1, 0, 3, 1, positions.get(0));

    assertThat(spy.constructedAliases).hasSize(1);
    AliasSpy aliasSpy = (AliasSpy) spy.constructedAliases.get(0);
    assertThat(aliasSpy.observedDefinitions).containsEntry("g", "goog");
    assertThat(aliasSpy.observedDefinitions).containsEntry("b", "g.bar");
    assertThat(aliasSpy.observedDefinitions).containsEntry("f", "goog.something.foo");
  }

  @Test
  public void testRecordAliasFromMultipleGoogScope() {
    String firstGoogScopeBlock =
        GOOG_SCOPE_START_BLOCK
            + "\n var g = goog;\n g.dom.createElement(g.dom.TagName.DIV);\n"
            + GOOG_SCOPE_END_BLOCK;
    String fullJsCode =
        firstGoogScopeBlock
            + "\n\nvar l = abc.def;\n\n"
            + GOOG_SCOPE_START_BLOCK
            + "\n var z = namespace.Zoo;\n z.getAnimals(l);\n"
            + GOOG_SCOPE_END_BLOCK;

    String expectedJsCode =
        """
        goog.dom.createElement(goog.dom.TagName.DIV);


        var l = abc.def;


         namespace.Zoo.getAnimals(l);
        """;

    TransformationHandlerSpy spy = new TransformationHandlerSpy();
    transformationHandler = spy;
    test(fullJsCode, expectedJsCode);

    assertThat(spy.observedPositions).containsKey("testcode");
    List<SourcePosition<AliasTransformation>> positions = spy.observedPositions.get("testcode");
    assertThat(positions).hasSize(2);

    verifyAliasTransformationPosition(1, 0, 6, 0, positions.get(0));

    verifyAliasTransformationPosition(8, 0, 11, 4, positions.get(1));

    assertThat(spy.constructedAliases).hasSize(2);
    AliasSpy aliasSpy = (AliasSpy) spy.constructedAliases.get(0);
    assertThat(aliasSpy.observedDefinitions).containsEntry("g", "goog");

    aliasSpy = (AliasSpy) spy.constructedAliases.get(1);
    assertThat(aliasSpy.observedDefinitions).containsEntry("z", "namespace.Zoo");
  }

  private void verifyAliasTransformationPosition(
      int startLine,
      int startChar,
      int endLine,
      int endChar,
      SourcePosition<AliasTransformation> pos) {
    assertThat(pos.getStartLine()).isEqualTo(startLine);
    assertThat(pos.getPositionOnStartLine()).isEqualTo(startChar);
    assertWithMessage("Endline smaller than expected.").that(pos.getEndLine()).isAtLeast(endLine);
    assertWithMessage("Endchar is smaller thatn expected.")
        .that(pos.getPositionOnEndLine())
        .isAtLeast(endChar);
  }

  @Override
  protected ScopedAliases getProcessor(Compiler compiler) {
    return ScopedAliases.builder(compiler)
        .setAliasTransformationHandler(transformationHandler)
        .setModuleMetadataMap(compiler.getModuleMetadataMap())
        .setInvalidModuleGetHandling(invalidModuleGetHandling)
        .build();
  }

  private static class TransformationHandlerSpy implements AliasTransformationHandler {

    private final ListMultimap<String, SourcePosition<AliasTransformation>> observedPositions =
        MultimapBuilder.hashKeys().arrayListValues().build();

    public final List<AliasTransformation> constructedAliases = new ArrayList<>();

    @Override
    public AliasTransformation logAliasTransformation(
        String sourceFile, SourcePosition<AliasTransformation> position) {
      observedPositions.put(sourceFile, position);
      AliasTransformation spy = new AliasSpy();
      constructedAliases.add(spy);
      return spy;
    }
  }

  private static class AliasSpy implements AliasTransformation {
    public final Map<String, String> observedDefinitions = new HashMap<>();

    @Override
    public void addAlias(String alias, String definition) {
      observedDefinitions.put(alias, definition);
    }
  }

  private static class TypeVerifyingPass implements CompilerPass, NodeTraversal.Callback {
    private final Compiler compiler;
    private @Nullable List<Node> actualTypes = null;

    public TypeVerifyingPass(Compiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public void process(Node externs, Node root) {
      NodeTraversal.traverse(compiler, root, this);
    }

    @Override
    public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      return true;
    }

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      JSDocInfo info = n.getJSDocInfo();
      if (info != null) {
        Collection<Node> typeNodes = info.getTypeNodes();
        if (!typeNodes.isEmpty()) {
          if (actualTypes != null) {
            List<Node> expectedTypes = new ArrayList<>();
            expectedTypes.addAll(info.getTypeNodes());
            assertWithMessage("Wrong number of JsDoc types")
                .that(actualTypes.size())
                .isEqualTo(expectedTypes.size());
            for (int i = 0; i < expectedTypes.size(); i++) {
              assertNode(actualTypes.get(i)).isEqualTo(expectedTypes.get(i));
            }
          } else {
            actualTypes = new ArrayList<>();
            actualTypes.addAll(info.getTypeNodes());
          }
        }
      }
    }
  }
}
