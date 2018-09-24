/*
 * Copyright 2011 The Closure Compiler Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.CompilerTestCase.lines;
import static com.google.javascript.jscomp.parsing.Config.JsDocParsing.INCLUDE_DESCRIPTIONS_NO_WHITESPACE;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.common.testing.EqualsTester;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.SymbolTable.Reference;
import com.google.javascript.jscomp.SymbolTable.Symbol;
import com.google.javascript.jscomp.SymbolTable.SymbolScope;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * An integration test for symbol table creation
 *
 * @author nicksantos@google.com (Nick Santos)
 */

@RunWith(JUnit4.class)
public final class SymbolTableTest extends TestCase {

  private static final String EXTERNS =
      CompilerTypeTestCase.DEFAULT_EXTERNS
          + "var Number;"
          + "\nfunction customExternFn(customExternArg) {}";

  private CompilerOptions options;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT_2017);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    options.setCodingConvention(new ClosureCodingConvention());
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setChecksOnly(true);
    options.setPreserveDetailedSourceInfo(true);
    options.setContinueAfterErrors(true);
    options.setAllowHotswapReplaceScript(true);
    options.setParseJsDocDocumentation(INCLUDE_DESCRIPTIONS_NO_WHITESPACE);
  }

  /**
   * Make sure rewrite of super() call containing a function literal doesn't cause the SymbolTable
   * to crash.
   */
  @Test
  public void testFunctionInCall() {
    createSymbolTable(
        lines(
            "class Y { constructor(fn) {} }",
            "class X extends Y {",
            "  constructor() {",
            "    super(function() {});",
            "  }",
            "}"));
  }

  @Test
  public void testGlobalVar() {
    SymbolTable table = createSymbolTable("/** @type {number} */ var x = 5;");
    assertNull(getGlobalVar(table, "y"));
    assertNotNull(getGlobalVar(table, "x"));
    assertEquals("number", getGlobalVar(table, "x").getType().toString());

    // 2 == sizeof({x, *global*})
    assertThat(getVars(table)).hasSize(2);
  }

  @Test
  public void testGlobalThisReferences() {
    SymbolTable table =
        createSymbolTable(
            "var x = this; function f() { return this + this + this; }", /* externsCode= */ "");

    Symbol global = getGlobalVar(table, "*global*");
    assertNotNull(global);

    List<Reference> refs = table.getReferenceList(global);
    assertThat(refs).hasSize(1);
  }

  @Test
  public void testGlobalThisReferences2() {
    // Make sure the global this is declared, even if it isn't referenced.
    SymbolTable table = createSymbolTable("", /* externsCode= */ "");

    Symbol global = getGlobalVar(table, "*global*");
    assertNotNull(global);

    List<Reference> refs = table.getReferenceList(global);
    assertThat(refs).isEmpty();
  }

  @Test
  public void testGlobalThisReferences3() {
    SymbolTable table =
        createSymbolTable("this.foo = {}; this.foo.bar = {};", /* externsCode= */ "");

    Symbol global = getGlobalVar(table, "*global*");
    assertNotNull(global);

    List<Reference> refs = table.getReferenceList(global);
    assertThat(refs).hasSize(2);
  }

  @Test
  public void testGlobalThisPropertyReferences() {
    SymbolTable table = createSymbolTable("/** @constructor */ function Foo() {} this.Foo;");

    Symbol foo = getGlobalVar(table, "Foo");
    assertNotNull(foo);

    List<Reference> refs = table.getReferenceList(foo);
    assertThat(refs).hasSize(2);
  }

  @Test
  public void testGlobalVarReferences() {
    SymbolTable table = createSymbolTable("/** @type {number} */ var x = 5; x = 6;");
    Symbol x = getGlobalVar(table, "x");
    List<Reference> refs = table.getReferenceList(x);

    assertThat(refs).hasSize(2);
    assertEquals(x.getDeclaration(), refs.get(0));
    assertEquals(Token.VAR, refs.get(0).getNode().getParent().getToken());
    assertEquals(Token.ASSIGN, refs.get(1).getNode().getParent().getToken());
  }

  @Test
  public void testLocalVarReferences() {
    SymbolTable table = createSymbolTable("function f(x) { return x; }");
    Symbol x = getLocalVar(table, "x");
    List<Reference> refs = table.getReferenceList(x);

    assertThat(refs).hasSize(2);
    assertEquals(x.getDeclaration(), refs.get(0));
    assertEquals(Token.PARAM_LIST, refs.get(0).getNode().getParent().getToken());
    assertEquals(Token.RETURN, refs.get(1).getNode().getParent().getToken());
  }

  @Test
  public void testLocalThisReferences() {
    SymbolTable table =
        createSymbolTable("/** @constructor */ function F() { this.foo = 3; this.bar = 5; }");

    Symbol f = getGlobalVar(table, "F");
    assertNotNull(f);

    Symbol t = table.getParameterInFunction(f, "this");
    assertNotNull(t);

    List<Reference> refs = table.getReferenceList(t);
    assertThat(refs).hasSize(2);
  }

  @Test
  public void testLocalThisReferences2() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @constructor */ function F() {}",
                "F.prototype.baz = function() { this.foo = 3; this.bar = 5; };"));

    Symbol baz = getGlobalVar(table, "F.prototype.baz");
    assertNotNull(baz);

    Symbol t = table.getParameterInFunction(baz, "this");
    assertNotNull(t);

    List<Reference> refs = table.getReferenceList(t);
    assertThat(refs).hasSize(2);
  }

  // No 'this' reference is created for empty functions.
  @Test
  public void testLocalThisReferences3() {
    SymbolTable table = createSymbolTable("/** @constructor */ function F() {}");

    Symbol baz = getGlobalVar(table, "F");
    assertNotNull(baz);

    Symbol t = table.getParameterInFunction(baz, "this");
    assertNull(t);
  }

  @Test
  public void testObjectLiteral() {
    SymbolTable table = createSymbolTable("var obj = {foo: 0};");

    Symbol foo = getGlobalVar(table, "obj.foo");
    assertThat(foo.getName()).isEqualTo("foo");
  }

  @Test
  public void testObjectLiteralQuoted() {
    SymbolTable table = createSymbolTable("var obj = {'foo': 0};");

    // Quoted keys are not symbols.
    assertThat(getGlobalVar(table, "obj.foo")).isNull();
    // Only obj and *global* are symbols.
    assertThat(getVars(table)).hasSize(2);
  }

  @Test
  public void testObjectLiteralWithMemberFunction() {
    String input = lines("var obj = { fn() {} }; obj.fn();");
    SymbolTable table = createSymbolTable(input);

    Symbol objFn = getGlobalVar(table, "obj.fn");
    assertNotNull(objFn);
    List<Reference> references = table.getReferenceList(objFn);
    assertThat(references).hasSize(2);

    // The declaration node corresponds to "fn", not "fn() {}", in the source info.
    Node declaration = objFn.getDeclarationNode();
    assertThat(declaration.getCharno()).isEqualTo(12);
    assertThat(declaration.getLength()).isEqualTo(2);
  }

  @Test
  public void testNamespacedReferences() {
    // Because the type of goog is anonymous, we build its properties into
    // the global scope.
    SymbolTable table =
        createSymbolTable(
            lines("var goog = {};", "goog.dom = {};", "goog.dom.DomHelper = function(){};"));

    Symbol goog = getGlobalVar(table, "goog");
    assertNotNull(goog);
    assertThat(table.getReferences(goog)).hasSize(3);

    Symbol googDom = getGlobalVar(table, "goog.dom");
    assertNotNull(googDom);
    assertThat(table.getReferences(googDom)).hasSize(2);

    Symbol googDomHelper = getGlobalVar(table, "goog.dom.DomHelper");
    assertNotNull(googDomHelper);
    assertThat(table.getReferences(googDomHelper)).hasSize(1);
  }

  @Test
  public void testIncompleteNamespacedReferences() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @constructor */",
                "goog.dom.DomHelper = function(){};",
                "var y = goog.dom.DomHelper;"));
    Symbol goog = getGlobalVar(table, "goog");
    assertNotNull(goog);
    assertThat(table.getReferenceList(goog)).hasSize(2);

    Symbol googDom = getGlobalVar(table, "goog.dom");
    assertNotNull(googDom);
    assertThat(table.getReferenceList(googDom)).hasSize(2);

    Symbol googDomHelper = getGlobalVar(table, "goog.dom.DomHelper");
    assertNotNull(googDomHelper);
    assertThat(table.getReferences(googDomHelper)).hasSize(2);
  }

  @Test
  public void testGlobalRichObjectReference() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @constructor */",
                "function A(){};",
                "/** @type {?A} */ A.prototype.b;",
                "/** @type {A} */ var a = new A();",
                "function g() {",
                "  return a.b ? 'x' : 'y';",
                "}",
                "(function() {",
                "  var x; if (x) { x = a.b.b; } else { x = a.b.c; }",
                "  return x;",
                "})();"));

    Symbol ab = getGlobalVar(table, "a.b");
    assertNull(ab);

    Symbol propB = getGlobalVar(table, "A.prototype.b");
    assertNotNull(propB);
    assertThat(table.getReferenceList(propB)).hasSize(5);
  }

  @Test
  public void testRemovalOfNamespacedReferencesOfProperties() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @constructor */ var DomHelper = function(){};",
                "/** method */ DomHelper.method = function() {};"));

    Symbol domHelper = getGlobalVar(table, "DomHelper");
    assertNotNull(domHelper);

    Symbol domHelperNamespacedMethod = getGlobalVar(table, "DomHelper.method");
    assertEquals("method", domHelperNamespacedMethod.getName());

    Symbol domHelperMethod = domHelper.getPropertyScope().getSlot("method");
    assertNotNull(domHelperMethod);
  }

  @Test
  public void testGoogScopeReferences() {
    SymbolTable table =
        createSymbolTable(
            lines("var goog = {};", "goog.scope = function() {};", "goog.scope(function() {});"));

    Symbol googScope = getGlobalVar(table, "goog.scope");
    assertNotNull(googScope);
    assertThat(table.getReferences(googScope)).hasSize(2);
  }

  @Test
  public void testGoogRequireReferences() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "var goog = {};",
                "goog.provide = function() {};",
                "goog.require = function() {};",
                "goog.provide('goog.dom');",
                "goog.require('goog.dom');"));
    Symbol goog = getGlobalVar(table, "goog");
    assertNotNull(goog);

    // 8 references:
    // 5 in code
    // 2 in strings
    // 1 created by ProcessClosurePrimitives when it processes the provide.
    //
    // NOTE(nicksantos): In the future, we may de-dupe references such
    // that the one in the goog.provide string and the one created by
    // ProcessClosurePrimitives count as the same reference.
    assertThat(table.getReferences(goog)).hasSize(8);
  }

  @Test
  public void testGoogRequireReferences2() {
    options.setBrokenClosureRequiresLevel(CheckLevel.OFF);
    SymbolTable table =
        createSymbolTable(
            lines("foo.bar = function(){};  // definition", "goog.require('foo.bar')"));
    Symbol fooBar = getGlobalVar(table, "foo.bar");
    assertNotNull(fooBar);
    assertThat(table.getReferences(fooBar)).hasSize(2);
  }

  @Test
  public void testGlobalVarInExterns() {
    SymbolTable table = createSymbolTable("customExternFn(1);");
    Symbol fn = getGlobalVar(table, "customExternFn");
    List<Reference> refs = table.getReferenceList(fn);
    assertThat(refs).hasSize(3);

    SymbolScope scope = table.getEnclosingScope(refs.get(0).getNode());
    assertTrue(scope.isGlobalScope());
    assertEquals(SymbolTable.GLOBAL_THIS, table.getSymbolForScope(scope).getName());
  }

  @Test
  public void testLocalVarInExterns() {
    SymbolTable table = createSymbolTable("");
    Symbol arg = getLocalVar(table, "customExternArg");
    List<Reference> refs = table.getReferenceList(arg);
    assertThat(refs).hasSize(1);

    Symbol fn = getGlobalVar(table, "customExternFn");
    SymbolScope scope = table.getEnclosingScope(refs.get(0).getNode());
    assertFalse(scope.isGlobalScope());
    assertEquals(fn, table.getSymbolForScope(scope));
  }

  @Test
  public void testSymbolsForType() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "function random() { return 1; }",
                "/** @constructor */ function Foo() {}",
                "/** @constructor */ function Bar() {}",
                "var x = random() ? new Foo() : new Bar();"));

    Symbol x = getGlobalVar(table, "x");
    Symbol foo = getGlobalVar(table, "Foo");
    Symbol bar = getGlobalVar(table, "Bar");
    Symbol fooPrototype = getGlobalVar(table, "Foo.prototype");
    Symbol fn = getGlobalVar(table, "Function");
    assertThat(table.getAllSymbolsForTypeOf(x)).containsExactly(foo, bar);
    assertThat(table.getAllSymbolsForTypeOf(foo)).containsExactly(fn);
    assertThat(table.getAllSymbolsForTypeOf(fooPrototype)).containsExactly(foo);
    assertEquals(foo, table.getSymbolDeclaredBy(foo.getType().toMaybeFunctionType()));
  }

  @Test
  public void testStaticMethodReferences() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @constructor */ var DomHelper = function(){};",
                "/** method */ DomHelper.method = function() {};",
                "function f() { var x = DomHelper; x.method() + x.method(); }"));

    Symbol method = getGlobalVar(table, "DomHelper").getPropertyScope().getSlot("method");
    assertThat(table.getReferences(method)).hasSize(3);
  }

  @Test
  public void testMethodReferences() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @constructor */ var DomHelper = function(){};",
                "/** method */ DomHelper.prototype.method = function() {};",
                "function f() { ",
                "  (new DomHelper()).method(); (new DomHelper()).method(); };"));

    Symbol method = getGlobalVar(table, "DomHelper.prototype.method");
    assertThat(table.getReferences(method)).hasSize(3);
  }

  @Test
  public void testSuperClassMethodReferences() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "var goog = {};",
                "goog.inherits = function(a, b) {};",
                "/** @constructor */ var A = function(){};",
                "/** method */ A.prototype.method = function() {};",
                "/**",
                " * @constructor",
                " * @extends {A}",
                " */",
                "var B = function(){};",
                "goog.inherits(B, A);",
                "/** method */ B.prototype.method = function() {",
                "  B.superClass_.method();",
                "};"));

    Symbol methodA = getGlobalVar(table, "A.prototype.method");
    assertThat(table.getReferences(methodA)).hasSize(2);
  }

  @Test
  public void testMethodReferencesMissingTypeInfo() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "/**",
                " * @constructor",
                " * @extends {Missing}",
                " */ var DomHelper = function(){};",
                "/** method */ DomHelper.prototype.method = function() {",
                "  this.method();",
                "};",
                "function f() { ",
                "  (new DomHelper()).method();",
                "};"));

    Symbol method = getGlobalVar(table, "DomHelper.prototype.method");
    assertThat(table.getReferences(method)).hasSize(3);
  }

  @Test
  public void testFieldReferencesMissingTypeInfo() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "/**",
                " * @constructor",
                " * @extends {Missing}",
                " */ var DomHelper = function(){ this.prop = 1; };",
                "/** @type {number} */ DomHelper.prototype.prop = 2;",
                "function f() {",
                "  return (new DomHelper()).prop;",
                "};"));

    Symbol prop = getGlobalVar(table, "DomHelper.prototype.prop");
    assertThat(table.getReferenceList(prop)).hasSize(3);

    assertNull(getLocalVar(table, "this.prop"));
  }

  @Test
  public void testFieldReferences() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @constructor */ var DomHelper = function(){",
                "  /** @type {number} */ this.field = 3;",
                "};",
                "function f() { ",
                "  return (new DomHelper()).field + (new DomHelper()).field; };"));

    Symbol field = getGlobalVar(table, "DomHelper.prototype.field");
    assertThat(table.getReferences(field)).hasSize(3);
  }

  @Test
  public void testUndeclaredFieldReferences() {
    // We do not currently create symbol table entries for undeclared fields,
    // but this may change in the future.
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @constructor */ var DomHelper = function(){};",
                "DomHelper.prototype.method = function() { ",
                "  this.field = 3;",
                "  return x.field;",
                "}"));

    Symbol field = getGlobalVar(table, "DomHelper.prototype.field");
    assertNull(field);
  }

  @Test
  public void testPrototypeReferences() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @constructor */ function DomHelper() {}",
                "DomHelper.prototype.method = function() {};"));
    Symbol prototype = getGlobalVar(table, "DomHelper.prototype");
    assertNotNull(prototype);

    List<Reference> refs = table.getReferenceList(prototype);

    // One of the refs is implicit in the declaration of the function.
    assertEquals(refs.toString(), 2, refs.size());
  }

  @Test
  public void testPrototypeReferences2() {
    SymbolTable table =
        createSymbolTable(
            "/** @constructor */" + "function Snork() {}" + "Snork.prototype.baz = 3;");
    Symbol prototype = getGlobalVar(table, "Snork.prototype");
    assertNotNull(prototype);

    List<Reference> refs = table.getReferenceList(prototype);
    assertThat(refs).hasSize(2);
  }

  @Test
  public void testPrototypeReferences3() {
    SymbolTable table = createSymbolTable("/** @constructor */ function Foo() {}");
    Symbol fooPrototype = getGlobalVar(table, "Foo.prototype");
    assertNotNull(fooPrototype);

    List<Reference> refs = table.getReferenceList(fooPrototype);
    assertThat(refs).hasSize(1);
    assertEquals(Token.NAME, refs.get(0).getNode().getToken());

    // Make sure that the ctor and its prototype are declared at the
    // same node.
    assertEquals(
        refs.get(0).getNode(), table.getReferenceList(getGlobalVar(table, "Foo")).get(0).getNode());
  }

  @Test
  public void testPrototypeReferences4() {
    SymbolTable table =
        createSymbolTable(
            lines("/** @constructor */ function Foo() {}", "Foo.prototype = {bar: 3}"));
    Symbol fooPrototype = getGlobalVar(table, "Foo.prototype");
    assertNotNull(fooPrototype);

    List<Reference> refs = ImmutableList.copyOf(table.getReferences(fooPrototype));
    assertThat(refs).hasSize(1);
    assertEquals(Token.GETPROP, refs.get(0).getNode().getToken());
    assertEquals("Foo.prototype", refs.get(0).getNode().getQualifiedName());
  }

  @Test
  public void testPrototypeReferences5() {
    SymbolTable table =
        createSymbolTable("var goog = {}; /** @constructor */ goog.Foo = function() {};");
    Symbol fooPrototype = getGlobalVar(table, "goog.Foo.prototype");
    assertNotNull(fooPrototype);

    List<Reference> refs = table.getReferenceList(fooPrototype);
    assertThat(refs).hasSize(1);
    assertEquals(Token.GETPROP, refs.get(0).getNode().getToken());

    // Make sure that the ctor and its prototype are declared at the
    // same node.
    assertEquals(
        refs.get(0).getNode(),
        table.getReferenceList(getGlobalVar(table, "goog.Foo")).get(0).getNode());
  }

  @Test
  public void testReferencesInJSDocType() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @constructor */ function Foo() {}",
                "/** @type {Foo} */ var x;",
                "/** @param {Foo} x */ function f(x) {}",
                "/** @return {function(): Foo} */ function g() {}",
                "/**",
                " * @constructor",
                " * @extends {Foo}",
                " */ function Sub() {}"));
    Symbol foo = getGlobalVar(table, "Foo");
    assertNotNull(foo);

    List<Reference> refs = table.getReferenceList(foo);
    assertThat(refs).hasSize(5);

    assertEquals(1, refs.get(0).getNode().getLineno());
    assertEquals(29, refs.get(0).getNode().getCharno());
    assertEquals(3, refs.get(0).getNode().getLength());

    assertEquals(2, refs.get(1).getNode().getLineno());
    assertEquals(11, refs.get(1).getNode().getCharno());

    assertEquals(3, refs.get(2).getNode().getLineno());
    assertEquals(12, refs.get(2).getNode().getCharno());

    assertEquals(4, refs.get(3).getNode().getLineno());
    assertEquals(25, refs.get(3).getNode().getCharno());

    assertEquals(7, refs.get(4).getNode().getLineno());
    assertEquals(13, refs.get(4).getNode().getCharno());
  }

  @Test
  public void testReferencesInJSDocType2() {
    SymbolTable table = createSymbolTable("/** @param {string} x */ function f(x) {}");
    Symbol str = getGlobalVar(table, "String");
    assertNotNull(str);

    List<Reference> refs = table.getReferenceList(str);

    // We're going to pick up a lot of references from the externs,
    // so it's not meaningful to check the number of references.
    // We really want to make sure that all the references are in the externs,
    // except the last one.
    assertThat(refs.size()).isGreaterThan(1);

    int last = refs.size() - 1;
    for (int i = 0; i < refs.size(); i++) {
      Reference ref = refs.get(i);
      assertEquals(i != last, ref.getNode().isFromExterns());
      if (!ref.getNode().isFromExterns()) {
        assertEquals("in1", ref.getNode().getSourceFileName());
      }
    }
  }

  @Test
  public void testDottedReferencesInJSDocType() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "var goog = {};",
                "/** @constructor */ goog.Foo = function() {}",
                "/** @type {goog.Foo} */ var x;",
                "/** @param {goog.Foo} x */ function f(x) {}",
                "/** @return {function(): goog.Foo} */ function g() {}",
                "/**",
                " * @constructor",
                " * @extends {goog.Foo}",
                " */ function Sub() {}"));
    Symbol foo = getGlobalVar(table, "goog.Foo");
    assertNotNull(foo);

    List<Reference> refs = table.getReferenceList(foo);
    assertThat(refs).hasSize(5);

    assertEquals(2, refs.get(0).getNode().getLineno());
    assertEquals(20, refs.get(0).getNode().getCharno());
    assertEquals(8, refs.get(0).getNode().getLength());

    assertEquals(3, refs.get(1).getNode().getLineno());
    assertEquals(16, refs.get(1).getNode().getCharno());

    assertEquals(4, refs.get(2).getNode().getLineno());
    assertEquals(17, refs.get(2).getNode().getCharno());

    assertEquals(5, refs.get(3).getNode().getLineno());
    assertEquals(30, refs.get(3).getNode().getCharno());

    assertEquals(8, refs.get(4).getNode().getLineno());
    assertEquals(18, refs.get(4).getNode().getCharno());
  }

  @Test
  public void testReferencesInJSDocName() {
    String code = "/** @param {Object} x */ function f(x) {}";
    SymbolTable table = createSymbolTable(code);
    Symbol x = getLocalVar(table, "x");
    assertNotNull(x);

    List<Reference> refs = table.getReferenceList(x);
    assertThat(refs).hasSize(2);

    assertEquals(code.indexOf("x) {"), refs.get(0).getNode().getCharno());
    assertEquals(code.indexOf("x */"), refs.get(1).getNode().getCharno());
    assertEquals("in1", refs.get(0).getNode().getSourceFileName());
  }

  @Test
  public void testLocalQualifiedNamesInLocalScopes() {
    SymbolTable table = createSymbolTable("function f() { var x = {}; x.number = 3; }");
    Symbol xNumber = getLocalVar(table, "x.number");
    assertNotNull(xNumber);
    assertFalse(table.getScope(xNumber).isGlobalScope());

    assertEquals("number", xNumber.getType().toString());
  }

  @Test
  public void testNaturalSymbolOrdering() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @const */ var a = {};",
                "/** @const */ a.b = {};",
                "/** @param {number} x */ function f(x) {}"));
    Symbol a = getGlobalVar(table, "a");
    Symbol ab = getGlobalVar(table, "a.b");
    Symbol f = getGlobalVar(table, "f");
    Symbol x = getLocalVar(table, "x");
    Ordering<Symbol> ordering = table.getNaturalSymbolOrdering();
    assertSymmetricOrdering(ordering, a, ab);
    assertSymmetricOrdering(ordering, a, f);
    assertSymmetricOrdering(ordering, f, ab);
    assertSymmetricOrdering(ordering, f, x);
  }

  @Test
  public void testDeclarationDisagreement() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @const */ var goog = goog || {};",
                "/** @param {!Function} x */",
                "goog.addSingletonGetter2 = function(x) {};",
                "/** Wakka wakka wakka */",
                "goog.addSingletonGetter = goog.addSingletonGetter2;",
                "/** @param {!Function} x */",
                "goog.addSingletonGetter = function(x) {};"));

    Symbol method = getGlobalVar(table, "goog.addSingletonGetter");
    List<Reference> refs = table.getReferenceList(method);
    assertThat(refs).hasSize(2);

    // Note that the declaration should show up second.
    assertEquals(7, method.getDeclaration().getNode().getLineno());
    assertEquals(5, refs.get(1).getNode().getLineno());
  }

  @Test
  public void testMultipleExtends() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @const */ var goog = goog || {};",
                "goog.inherits = function(x, y) {};",
                "/** @constructor */",
                "goog.A = function() { this.fieldA = this.constructor; };",
                "/** @constructor */ goog.A.FooA = function() {};",
                "/** @return {void} */ goog.A.prototype.methodA = function() {};",
                "/**",
                " * @constructor",
                " * @extends {goog.A}",
                " */",
                "goog.B = function() { this.fieldB = this.constructor; };",
                "goog.inherits(goog.B, goog.A);",
                "/** @return {void} */ goog.B.prototype.methodB = function() {};",
                "/**",
                " * @constructor",
                " * @extends {goog.A}",
                " */",
                "goog.B2 = function() { this.fieldB = this.constructor; };",
                "goog.inherits(goog.B2, goog.A);",
                "/** @constructor */ goog.B2.FooB = function() {};",
                "/** @return {void} */ goog.B2.prototype.methodB = function() {};",
                "/**",
                " * @constructor",
                " * @extends {goog.B}",
                " */",
                "goog.C = function() { this.fieldC = this.constructor; };",
                "goog.inherits(goog.C, goog.B);",
                "/** @constructor */ goog.C.FooC = function() {};",
                "/** @return {void} */ goog.C.prototype.methodC = function() {};"));

    Symbol bCtor = getGlobalVar(table, "goog.B.prototype.constructor");
    assertNotNull(bCtor);

    List<Reference> bRefs = table.getReferenceList(bCtor);
    assertThat(bRefs).hasSize(2);
    assertEquals(11, bCtor.getDeclaration().getNode().getLineno());

    Symbol cCtor = getGlobalVar(table, "goog.C.prototype.constructor");
    assertNotNull(cCtor);

    List<Reference> cRefs = table.getReferenceList(cCtor);
    assertThat(cRefs).hasSize(2);
    assertEquals(26, cCtor.getDeclaration().getNode().getLineno());
  }

  @Test
  public void testJSDocAssociationWithBadNamespace() {
    SymbolTable table =
        createSymbolTable(
            // Notice that the declaration for "goog" is missing.
            // We want to recover anyway and print out what we know
            // about goog.Foo.
            "/** @constructor */ goog.Foo = function(){};");

    Symbol foo = getGlobalVar(table, "goog.Foo");
    assertNotNull(foo);

    JSDocInfo info = foo.getJSDocInfo();
    assertNotNull(info);
    assertTrue(info.isConstructor());
  }

  @Test
  public void testMissingConstructorTag() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "function F() {",
                "  this.field1 = 3;",
                "}",
                "F.prototype.method1 = function() {",
                "  this.field1 = 5;",
                "};",
                "(new F()).method1();"));

    // Because the constructor tag is missing, this is going
    // to be missing a lot of inference.
    assertNull(getGlobalVar(table, "F.prototype.field1"));

    Symbol sym = getGlobalVar(table, "F.prototype.method1");
    assertThat(table.getReferenceList(sym)).hasSize(1);
  }

  @Test
  public void testTypeCheckingOff() {
    options = new CompilerOptions();

    // Turning type-checking off is even worse than not annotating anything.
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @contstructor */",
                "function F() {",
                "  this.field1 = 3;",
                "}",
                "F.prototype.method1 = function() {",
                "  this.field1 = 5;",
                "};",
                "(new F()).method1();"));
    assertNull(getGlobalVar(table, "F.prototype.field1"));
    assertNull(getGlobalVar(table, "F.prototype.method1"));

    Symbol sym = getGlobalVar(table, "F");
    assertThat(table.getReferenceList(sym)).hasSize(3);
  }

  @Test
  public void testSuperClassReference() {
    SymbolTable table =
        createSymbolTable(
            "  var a = {b: {}};"
                + "/** @constructor */"
                + "a.b.BaseClass = function() {};"
                + "a.b.BaseClass.prototype.doSomething = function() {"
                + "  alert('hi');"
                + "};"
                + "/**"
                + " * @constructor"
                + " * @extends {a.b.BaseClass}"
                + " */"
                + "a.b.DerivedClass = function() {};"
                + "goog.inherits(a.b.DerivedClass, a.b.BaseClass);"
                + "/** @override */"
                + "a.b.DerivedClass.prototype.doSomething = function() {"
                + "  a.b.DerivedClass.superClass_.doSomething();"
                + "};");

    Symbol bad = getGlobalVar(table, "a.b.DerivedClass.superClass_.doSomething");
    assertNull(bad);

    Symbol good = getGlobalVar(table, "a.b.BaseClass.prototype.doSomething");
    assertNotNull(good);

    List<Reference> refs = table.getReferenceList(good);
    assertThat(refs).hasSize(2);
    assertEquals(
        "a.b.DerivedClass.superClass_.doSomething", refs.get(1).getNode().getQualifiedName());
  }

  @Test
  public void testInnerEnum() {
    SymbolTable table =
        createSymbolTable(
            "var goog = {}; goog.ui = {};"
                + "  /** @constructor */"
                + "goog.ui.Zippy = function() {};"
                + "/** @enum {string} */"
                + "goog.ui.Zippy.EventType = { TOGGLE: 'toggle' };");

    Symbol eventType = getGlobalVar(table, "goog.ui.Zippy.EventType");
    assertNotNull(eventType);
    assertTrue(eventType.getType().isEnumType());

    Symbol toggle = getGlobalVar(table, "goog.ui.Zippy.EventType.TOGGLE");
    assertNotNull(toggle);
  }

  @Test
  public void testMethodInAnonObject1() {
    SymbolTable table = createSymbolTable("var a = {}; a.b = {}; a.b.c = function() {};");
    Symbol a = getGlobalVar(table, "a");
    Symbol ab = getGlobalVar(table, "a.b");
    Symbol abc = getGlobalVar(table, "a.b.c");

    assertNotNull(abc);
    assertThat(table.getReferenceList(abc)).hasSize(1);

    assertEquals("{b: {c: function(): undefined}}", a.getType().toString());
    assertEquals("{c: function(): undefined}", ab.getType().toString());
    assertEquals("function(): undefined", abc.getType().toString());
  }

  @Test
  public void testMethodInAnonObject2() {
    SymbolTable table = createSymbolTable("var a = {b: {c: function() {}}};");
    Symbol a = getGlobalVar(table, "a");
    Symbol ab = getGlobalVar(table, "a.b");
    Symbol abc = getGlobalVar(table, "a.b.c");

    assertNotNull(abc);
    assertThat(table.getReferenceList(abc)).hasSize(1);

    assertEquals("{b: {c: function(): undefined}}", a.getType().toString());
    assertEquals("{c: function(): undefined}", ab.getType().toString());
    assertEquals("function(): undefined", abc.getType().toString());
  }

  @Test
  public void testJSDocOnlySymbol() {
    SymbolTable table =
        createSymbolTable(lines("/**", " * @param {number} x", " * @param y", " */", "var a;"));
    Symbol x = getDocVar(table, "x");
    assertNotNull(x);
    assertEquals("number", x.getType().toString());
    assertThat(table.getReferenceList(x)).hasSize(1);

    Symbol y = getDocVar(table, "y");
    assertNotNull(x);
    assertNull(y.getType());
    assertThat(table.getReferenceList(y)).hasSize(1);
  }

  @Test
  public void testNamespaceDefinitionOrder() {
    // Sometimes, weird things can happen where the files appear in
    // a strange order. We need to make sure we're robust against this.
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @const */ var goog = {};",
                "/** @constructor */ goog.dom.Foo = function() {};",
                "/** @const */ goog.dom = {};"));

    Symbol goog = getGlobalVar(table, "goog");
    Symbol dom = getGlobalVar(table, "goog.dom");
    Symbol Foo = getGlobalVar(table, "goog.dom.Foo");

    assertNotNull(goog);
    assertNotNull(dom);
    assertNotNull(Foo);

    assertEquals(dom, goog.getPropertyScope().getSlot("dom"));
    assertEquals(Foo, dom.getPropertyScope().getSlot("Foo"));
  }

  @Test
  public void testConstructorAlias() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @constructor */ var Foo = function() {};",
                "/** desc */ Foo.prototype.bar = function() {};",
                "/** @constructor */ var FooAlias = Foo;",
                "/** desc */ FooAlias.prototype.baz = function() {};"));

    Symbol foo = getGlobalVar(table, "Foo");
    Symbol fooAlias = getGlobalVar(table, "FooAlias");
    Symbol bar = getGlobalVar(table, "Foo.prototype.bar");
    Symbol baz = getGlobalVar(table, "Foo.prototype.baz");
    Symbol bazAlias = getGlobalVar(table, "FooAlias.prototype.baz");

    assertNotNull(foo);
    assertNotNull(fooAlias);
    assertNotNull(bar);
    assertNotNull(baz);
    assertNull(bazAlias);

    Symbol barScope = table.getSymbolForScope(table.getScope(bar));
    assertNotNull(barScope);

    Symbol bazScope = table.getSymbolForScope(table.getScope(baz));
    assertNotNull(bazScope);

    Symbol fooPrototype = foo.getPropertyScope().getSlot("prototype");
    assertNotNull(fooPrototype);

    assertEquals(fooPrototype, barScope);
    assertEquals(fooPrototype, bazScope);
  }

  @Test
  public void testSymbolForScopeOfNatives() {
    SymbolTable table = createSymbolTable("");

    // From the externs.
    Symbol sliceArg = getLocalVar(table, "sliceArg");
    assertNotNull(sliceArg);

    Symbol scope = table.getSymbolForScope(table.getScope(sliceArg));
    assertNotNull(scope);
    assertEquals(scope, getGlobalVar(table, "String.prototype.slice"));

    Symbol proto = getGlobalVar(table, "String.prototype");
    assertEquals("externs1", proto.getDeclaration().getNode().getSourceFileName());
  }

  @Test
  public void testJSDocNameVisibility() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @public */ var foo;",
                "/** @protected */ var bar;",
                "/** @package */ var baz;",
                "/** @private */ var quux;",
                "var xyzzy;"));

    assertEquals(Visibility.PUBLIC, getGlobalVar(table, "foo").getVisibility());
    assertEquals(Visibility.PROTECTED, getGlobalVar(table, "bar").getVisibility());
    assertEquals(Visibility.PACKAGE, getGlobalVar(table, "baz").getVisibility());
    assertEquals(Visibility.PRIVATE, getGlobalVar(table, "quux").getVisibility());
    assertEquals(Visibility.INHERITED, getGlobalVar(table, "xyzzy").getVisibility());
    assertNull(getGlobalVar(table, "xyzzy").getJSDocInfo());
  }

  @Test
  public void testJSDocNameVisibilityWithFileOverviewVisibility() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @fileoverview\n @package */",
                "/** @public */ var foo;",
                "/** @protected */ var bar;",
                "/** @package */ var baz;",
                "/** @private */ var quux;",
                "var xyzzy;"));
    assertEquals(Visibility.PUBLIC, getGlobalVar(table, "foo").getVisibility());
    assertEquals(Visibility.PROTECTED, getGlobalVar(table, "bar").getVisibility());
    assertEquals(Visibility.PACKAGE, getGlobalVar(table, "baz").getVisibility());
    assertEquals(Visibility.PRIVATE, getGlobalVar(table, "quux").getVisibility());
    assertEquals(Visibility.PACKAGE, getGlobalVar(table, "xyzzy").getVisibility());
    assertNull(getGlobalVar(table, "xyzzy").getJSDocInfo());
  }

  @Test
  public void testJSDocPropertyVisibility() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @constructor */ var Foo = function() {};",
                "/** @public */ Foo.prototype.bar;",
                "/** @protected */ Foo.prototype.baz;",
                "/** @package */ Foo.prototype.quux;",
                "/** @private */ Foo.prototype.xyzzy;",
                "Foo.prototype.plugh;",
                "/** @constructor @extends {Foo} */ var SubFoo = function() {};",
                "/** @override */ SubFoo.prototype.bar = function() {};",
                "/** @override */ SubFoo.prototype.baz = function() {};",
                "/** @override */ SubFoo.prototype.quux = function() {};",
                "/** @override */ SubFoo.prototype.xyzzy = function() {};",
                "/** @override */ SubFoo.prototype.plugh = function() {};"));

    assertEquals(Visibility.PUBLIC, getGlobalVar(table, "Foo.prototype.bar").getVisibility());
    assertEquals(Visibility.PROTECTED, getGlobalVar(table, "Foo.prototype.baz").getVisibility());
    assertEquals(Visibility.PACKAGE, getGlobalVar(table, "Foo.prototype.quux").getVisibility());
    assertEquals(Visibility.PRIVATE, getGlobalVar(table, "Foo.prototype.xyzzy").getVisibility());
    assertEquals(Visibility.INHERITED, getGlobalVar(table, "Foo.prototype.plugh").getVisibility());
    assertNull(getGlobalVar(table, "Foo.prototype.plugh").getJSDocInfo());

    assertEquals(Visibility.INHERITED, getGlobalVar(table, "SubFoo.prototype.bar").getVisibility());
    assertEquals(Visibility.INHERITED, getGlobalVar(table, "SubFoo.prototype.baz").getVisibility());
    assertEquals(
        Visibility.INHERITED, getGlobalVar(table, "SubFoo.prototype.quux").getVisibility());
    assertEquals(
        Visibility.INHERITED, getGlobalVar(table, "SubFoo.prototype.xyzzy").getVisibility());
    assertEquals(
        Visibility.INHERITED, getGlobalVar(table, "SubFoo.prototype.plugh").getVisibility());
  }

  @Test
  public void testJSDocPropertyVisibilityWithFileOverviewVisibility() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @fileoverview\n @package */",
                "/** @constructor */ var Foo = function() {};",
                "/** @public */ Foo.prototype.bar;",
                "/** @protected */ Foo.prototype.baz;",
                "/** @package */ Foo.prototype.quux;",
                "/** @private */ Foo.prototype.xyzzy;",
                "Foo.prototype.plugh;",
                "/** @constructor @extends {Foo} */ var SubFoo = function() {};",
                "/** @override @public */ SubFoo.prototype.bar = function() {};",
                "/** @override @protected */ SubFoo.prototype.baz = function() {};",
                "/** @override @package */ SubFoo.prototype.quux = function() {};",
                "/** @override @private */ SubFoo.prototype.xyzzy = function() {};",
                "/** @override */ SubFoo.prototype.plugh = function() {};"));

    assertEquals(Visibility.PUBLIC, getGlobalVar(table, "Foo.prototype.bar").getVisibility());
    assertEquals(Visibility.PROTECTED, getGlobalVar(table, "Foo.prototype.baz").getVisibility());
    assertEquals(Visibility.PACKAGE, getGlobalVar(table, "Foo.prototype.quux").getVisibility());
    assertEquals(Visibility.PRIVATE, getGlobalVar(table, "Foo.prototype.xyzzy").getVisibility());
    assertEquals(Visibility.PACKAGE, getGlobalVar(table, "Foo.prototype.plugh").getVisibility());
    assertNull(getGlobalVar(table, "Foo.prototype.plugh").getJSDocInfo());

    assertEquals(Visibility.INHERITED, getGlobalVar(table, "SubFoo.prototype.bar").getVisibility());
    assertEquals(Visibility.INHERITED, getGlobalVar(table, "SubFoo.prototype.baz").getVisibility());
    assertEquals(
        Visibility.INHERITED, getGlobalVar(table, "SubFoo.prototype.quux").getVisibility());
    assertEquals(
        Visibility.INHERITED, getGlobalVar(table, "SubFoo.prototype.xyzzy").getVisibility());
    assertEquals(
        Visibility.INHERITED, getGlobalVar(table, "SubFoo.prototype.plugh").getVisibility());
  }

  @Test
  public void testPrototypeSymbolEqualityForTwoPathsToSamePrototype() {
    String input =
        lines(
            "/**",
            "* An employer.",
            "*",
            "* @param {String} name name of employer.",
            "* @param {String} address address of employer.",
            "* @constructor",
            "*/",
            "function Employer(name, address) {",
            "this.name = name;",
            "this.address = address;",
            "}",
            "",
            "/**",
            "* @return {String} information about an employer.",
            "*/",
            "Employer.prototype.getInfo = function() {",
            "return this.name + '.' + this.address;",
            "};");
    SymbolTable table = createSymbolTable(input);
    Symbol employer = getGlobalVar(table, "Employer");
    assertNotNull(employer);
    SymbolScope propertyScope = employer.getPropertyScope();
    assertNotNull(propertyScope);
    Symbol prototypeOfEmployer = propertyScope.getQualifiedSlot("prototype");
    assertNotNull(prototypeOfEmployer);

    Symbol employerPrototype = getGlobalVar(table, "Employer.prototype");
    assertNotNull(employerPrototype);

    new EqualsTester().addEqualityGroup(employerPrototype, prototypeOfEmployer).testEquals();
  }

  @Test
  public void testRestParameter() {
    String input = "function f(...x) {} f(1, 2, 3);";

    SymbolTable table = createSymbolTable(input);

    Symbol f = getGlobalVar(table, "f");
    assertNotNull(f);

    Symbol x = table.getParameterInFunction(f, "x");
    assertNotNull(x);
  }

  @Test
  public void testGetEnclosingScope_Global() {
    SymbolTable table = createSymbolTable("const baz = 1;");

    Symbol baz = getLocalVar(table, "baz");
    SymbolScope bazScope = table.getEnclosingScope(baz.getDeclarationNode());

    assertNotNull(bazScope);
    assertTrue(bazScope.isGlobalScope());
  }

  @Test
  public void testGetEnclosingScope_GlobalNested() {
    SymbolTable table = createSymbolTable("{ const baz = 1; }");

    Symbol baz = getLocalVar(table, "baz");
    SymbolScope bazScope = table.getEnclosingScope(baz.getDeclarationNode());

    assertNotNull(bazScope);
    assertTrue(bazScope.isBlockScope());
    assertTrue(bazScope.getParentScope().isGlobalScope());
  }

  @Test
  public void testGetEnclosingScope_FunctionNested() {
    SymbolTable table = createSymbolTable("function foo() { { const baz = 1; } }");

    Symbol baz = getLocalVar(table, "baz");
    SymbolScope bazScope = table.getEnclosingScope(baz.getDeclarationNode());

    assertNotNull(bazScope);
    assertTrue(bazScope.isBlockScope());
    assertTrue(bazScope.getParentScope().isBlockScope());
    Node foo = getGlobalVar(table, "foo").getDeclarationNode().getParent();
    assertEquals(foo, bazScope.getParentScope().getParentScope().getRootNode());
  }

  @Test
  public void testGetEnclosingFunctionScope_GlobalScopeNoNesting() {
    SymbolTable table = createSymbolTable("const baz = 1;");

    Symbol baz = getLocalVar(table, "baz");
    SymbolScope bazFunctionScope = table.getEnclosingFunctionScope(baz.getDeclarationNode());

    assertNotNull(bazFunctionScope);
    assertTrue(bazFunctionScope.isGlobalScope());
  }

  @Test
  public void testGetEnclosingFunctionScope_GlobalScopeNested() {
    SymbolTable table = createSymbolTable("{ const baz = 1; }");

    Symbol baz = getLocalVar(table, "baz");
    SymbolScope bazFunctionScope = table.getEnclosingFunctionScope(baz.getDeclarationNode());

    assertNotNull(bazFunctionScope);
    assertTrue(bazFunctionScope.isGlobalScope());
  }

  @Test
  public void testGetEnclosingFunctionScope_FunctionNoNestedScopes() {
    SymbolTable table = createSymbolTable("function foo() { const baz = 1; }");

    Symbol baz = getLocalVar(table, "baz");
    SymbolScope bazFunctionScope = table.getEnclosingFunctionScope(baz.getDeclarationNode());

    assertNotNull(bazFunctionScope);
    Node foo = getGlobalVar(table, "foo").getDeclarationNode().getParent();
    assertEquals(foo, bazFunctionScope.getRootNode());
  }

  @Test
  public void testGetEnclosingFunctionScope_FunctionNested() {
    SymbolTable table = createSymbolTable("function foo() { { { const baz = 1; } } }");

    Symbol baz = getLocalVar(table, "baz");
    SymbolScope bazFunctionScope = table.getEnclosingFunctionScope(baz.getDeclarationNode());

    assertNotNull(bazFunctionScope);
    Node foo = getGlobalVar(table, "foo").getDeclarationNode().getParent();
    assertEquals(foo, bazFunctionScope.getRootNode());
  }

  @Test
  public void testGetEnclosingFunctionScope_FunctionInsideFunction() {
    SymbolTable table = createSymbolTable("function foo() { function baz() {} }");

    Symbol baz = getLocalVar(table, "baz");
    SymbolScope bazFunctionScope = table.getEnclosingFunctionScope(baz.getDeclarationNode());

    assertNotNull(bazFunctionScope);
    Node foo = getGlobalVar(table, "foo").getDeclarationNode().getParent();
    assertEquals(foo, bazFunctionScope.getRootNode());
  }

  @Test
  public void testSymbolSuperclassStaticInheritance() {
    // set this option so that typechecking sees untranspiled classes.
    // TODO(b/76025401): remove this option after class transpilation is always post-typechecking
    options.setLanguageOut(LanguageMode.ECMASCRIPT_2015);
    options.setSkipUnsupportedPasses(false);

    SymbolTable table =
        createSymbolTable(
            lines(
                "class Bar { static staticMethod() {} }",
                "class Foo extends Bar {",
                "  act() { Foo.staticMethod(); }",
                "}"));

    Symbol foo = getGlobalVar(table, "Foo");
    Symbol bar = getGlobalVar(table, "Bar");

    FunctionType barType = checkNotNull(bar.getType().toMaybeFunctionType());
    FunctionType fooType = checkNotNull(foo.getType().toMaybeFunctionType());

    FunctionType superclassCtorType = fooType.getSuperClassConstructor();

    assertType(superclassCtorType).isEqualTo(barType);
    Symbol superSymbol = table.getSymbolDeclaredBy(superclassCtorType);
    assertThat(superSymbol).isEqualTo(bar);

    Symbol barStaticMethod = getGlobalVar(table, "Bar.staticMethod");
    ImmutableList<Reference> barStaticMethodRefs = table.getReferenceList(barStaticMethod);
    assertThat(barStaticMethodRefs).hasSize(2);
    assertThat(barStaticMethodRefs.get(0)).isEqualTo(barStaticMethod.getDeclaration());
    // We recognize that the call to Foo.staticMethod() is actually a call to Bar.staticMethod().
    assertNode(barStaticMethodRefs.get(1).getNode()).matchesQualifiedName("Foo.staticMethod");
  }

  @Test
  public void testTypedefInNodeJsModule() {
    options.setProcessCommonJSModules(true);
    SymbolTable table =
        createSymbolTableFromTwoSources(
            lines("/** @typedef {number} */", "exports.MyTypedef;"),
            lines(
                "const file1 = require('./file1.js');",
                "/** @const {!file1.MyTypedef} */",
                "const one = 1;"));

    Symbol typedefSymbol = getGlobalVar(table, "module$file1.default.MyTypedef");
    assertThat(typedefSymbol).isNotNull();
    assertThat(table.getReferenceList(typedefSymbol)).hasSize(2);
  }

  @Test
  public void testSuperKeywords() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "class Parent { doFoo() {} }",
                "class Child extends Parent {",
                "  constructor() { super(); }",
                "  doFoo() { super.doFoo(); }",
                "}"));

    Symbol parentClass = getGlobalVar(table, "Parent");
    long numberOfSuperReferences =
        table.getReferenceList(parentClass).stream()
            .filter((Reference ref) -> ref.getNode().isSuper())
            .count();
    // TODO(b/112359882): change number of refs to 2 once Es6ConvertSuper pass is moved after type
    // checks.
    assertThat(numberOfSuperReferences).isEqualTo(1);
  }

  private void assertSymmetricOrdering(Ordering<Symbol> ordering, Symbol first, Symbol second) {
    assertEquals(0, ordering.compare(first, first));
    assertEquals(0, ordering.compare(second, second));
    assertThat(ordering.compare(first, second)).isLessThan(0);
    assertThat(ordering.compare(second, first)).isGreaterThan(0);
  }

  private Symbol getGlobalVar(SymbolTable table, String name) {
    return table.getGlobalScope().getQualifiedSlot(name);
  }

  private Symbol getDocVar(SymbolTable table, String name) {
    for (Symbol sym : table.getAllSymbols()) {
      if (sym.isDocOnlyParameter() && sym.getName().equals(name)) {
        return sym;
      }
    }
    return null;
  }

  private Symbol getLocalVar(SymbolTable table, String name) {
    for (SymbolScope scope : table.getAllScopes()) {
      if (!scope.isGlobalScope()
          && scope.isLexicalScope()
          && scope.getQualifiedSlot(name) != null) {
        return scope.getQualifiedSlot(name);
      }
    }
    return null;
  }

  /** Returns all non-extern vars. */
  private List<Symbol> getVars(SymbolTable table) {
    List<Symbol> result = new ArrayList<>();
    for (Symbol symbol : table.getAllSymbols()) {
      if (symbol.getDeclaration() != null && !symbol.getDeclaration().getNode().isFromExterns()) {
        result.add(symbol);
      }
    }
    return result;
  }

  private SymbolTable createSymbolTable(String input) {
    return createSymbolTable(input, EXTERNS);
  }

  private SymbolTable createSymbolTable(String input, String externsCode) {
    List<SourceFile> inputs = ImmutableList.of(SourceFile.fromCode("in1", input));
    List<SourceFile> externs = ImmutableList.of(SourceFile.fromCode("externs1", externsCode));

    Compiler compiler = new Compiler(new BlackHoleErrorManager());
    compiler.compile(externs, inputs, options);
    return assertSymbolTableValid(compiler.buildKnownSymbolTable());
  }

  private SymbolTable createSymbolTableFromTwoSources(String input1, String input2) {
    List<SourceFile> inputs =
        ImmutableList.of(
            SourceFile.fromCode("file1.js", input1), SourceFile.fromCode("file2.js", input2));
    List<SourceFile> externs = ImmutableList.of();

    Compiler compiler = new Compiler(new BlackHoleErrorManager());
    compiler.compile(externs, inputs, options);
    return assertSymbolTableValid(compiler.buildKnownSymbolTable());
  }

  /**
   * Asserts that the symbol table meets some invariants. Returns the same table for easy chaining.
   */
  private SymbolTable assertSymbolTableValid(SymbolTable table) {
    Set<Symbol> allSymbols = new HashSet<>();
    allSymbols.addAll(table.getAllSymbols());
    for (Symbol sym : table.getAllSymbols()) {
      // Make sure that grabbing the symbol's scope and looking it up
      // again produces the same symbol.
      assertEquals(sym, table.getScope(sym).getQualifiedSlot(sym.getName()));

      for (Reference ref : table.getReferences(sym)) {
        // Make sure that the symbol and reference are mutually linked.
        assertEquals(sym, ref.getSymbol());
      }

      Symbol scope = table.getSymbolForScope(table.getScope(sym));
      assertTrue(
          "The symbol's scope is a zombie scope that shouldn't exist.\n"
              + "Symbol: "
              + sym
              + "\n"
              + "Scope: "
              + table.getScope(sym),
          scope == null || allSymbols.contains(scope));
    }

    // Make sure that the global "this" is declared at the first input root.
    Symbol global = getGlobalVar(table, SymbolTable.GLOBAL_THIS);
    assertNotNull(global);
    assertNotNull(global.getDeclaration());
    assertEquals(Token.SCRIPT, global.getDeclaration().getNode().getToken());

    List<Reference> globalRefs = table.getReferenceList(global);

    // The main reference list should never contain the synthetic declaration
    // for the global root.
    assertThat(globalRefs).doesNotContain(global.getDeclaration());

    return table;
  }
}
