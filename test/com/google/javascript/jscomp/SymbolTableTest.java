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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.SymbolTable.Reference;
import com.google.javascript.jscomp.SymbolTable.Symbol;
import com.google.javascript.jscomp.SymbolTable.SymbolScope;
import com.google.javascript.jscomp.testing.BlackHoleErrorManager;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

import java.util.List;
import java.util.Set;

/**
 * @author nicksantos@google.com (Nick Santos)
 */

public class SymbolTableTest extends TestCase {

  private static final String EXTERNS = CompilerTypeTestCase.DEFAULT_EXTERNS +
      "var Number;" +
      "\nfunction customExternFn(customExternArg) {}";

  private CompilerOptions options;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    options = new CompilerOptions();
    options.setCodingConvention(new ClosureCodingConvention());
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(
        options);
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setIdeMode(true);
  }

  public void testGlobalVar() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @type {number} */ var x = 5;");
    assertNull(getGlobalVar(table, "y"));
    assertNotNull(getGlobalVar(table, "x"));
    assertEquals("number", getGlobalVar(table, "x").getType().toString());

    // 2 == sizeof({x, *global*})
    assertThat(getVars(table)).hasSize(2);
  }

  public void testGlobalThisReferences() throws Exception {
    SymbolTable table = createSymbolTable(
        "var x = this; function f() { return this + this + this; }");

    Symbol global = getGlobalVar(table, "*global*");
    assertNotNull(global);

    List<Reference> refs = table.getReferenceList(global);
    assertThat(refs).hasSize(1);
  }

  public void testGlobalThisReferences2() throws Exception {
    // Make sure the global this is declared, even if it isn't referenced.
    SymbolTable table = createSymbolTable("");

    Symbol global = getGlobalVar(table, "*global*");
    assertNotNull(global);

    List<Reference> refs = table.getReferenceList(global);
    assertThat(refs).isEmpty();
  }

  public void testGlobalThisReferences3() throws Exception {
    SymbolTable table = createSymbolTable("this.foo = {}; this.foo.bar = {};");

    Symbol global = getGlobalVar(table, "*global*");
    assertNotNull(global);

    List<Reference> refs = table.getReferenceList(global);
    assertThat(refs).hasSize(2);
  }

  public void testGlobalThisPropertyReferences() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @constructor */ function Foo() {} this.Foo;");

    Symbol foo = getGlobalVar(table, "Foo");
    assertNotNull(foo);

    List<Reference> refs = table.getReferenceList(foo);
    assertThat(refs).hasSize(2);
  }

  public void testGlobalVarReferences() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @type {number} */ var x = 5; x = 6;");
    Symbol x = getGlobalVar(table, "x");
    List<Reference> refs = table.getReferenceList(x);

    assertThat(refs).hasSize(2);
    assertEquals(x.getDeclaration(), refs.get(0));
    assertEquals(Token.VAR, refs.get(0).getNode().getParent().getType());
    assertEquals(Token.ASSIGN, refs.get(1).getNode().getParent().getType());
  }

  public void testLocalVarReferences() throws Exception {
    SymbolTable table = createSymbolTable(
        "function f(x) { return x; }");
    Symbol x = getLocalVar(table, "x");
    List<Reference> refs = table.getReferenceList(x);

    assertThat(refs).hasSize(2);
    assertEquals(x.getDeclaration(), refs.get(0));
    assertEquals(Token.PARAM_LIST, refs.get(0).getNode().getParent().getType());
    assertEquals(Token.RETURN, refs.get(1).getNode().getParent().getType());
  }

  public void testLocalThisReferences() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @constructor */ function F() { this.foo = 3; this.bar = 5; }");

    Symbol f = getGlobalVar(table, "F");
    assertNotNull(f);

    Symbol t = table.getParameterInFunction(f, "this");
    assertNotNull(t);

    List<Reference> refs = table.getReferenceList(t);
    assertThat(refs).hasSize(2);
  }

  public void testLocalThisReferences2() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @constructor */ function F() {}" +
        "F.prototype.baz = " +
        "    function() { this.foo = 3; this.bar = 5; };");

    Symbol baz = getGlobalVar(table, "F.prototype.baz");
    assertNotNull(baz);

    Symbol t = table.getParameterInFunction(baz, "this");
    assertNotNull(t);

    List<Reference> refs = table.getReferenceList(t);
    assertThat(refs).hasSize(2);
  }

  public void testLocalThisReferences3() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @constructor */ function F() {}");

    Symbol baz = getGlobalVar(table, "F");
    assertNotNull(baz);

    Symbol t = table.getParameterInFunction(baz, "this");
    assertNotNull(t);

    List<Reference> refs = table.getReferenceList(t);
    assertThat(refs).isEmpty();
  }

  public void testNamespacedReferences() throws Exception {
    // Because the type of goog is anonymous, we build its properties into
    // the global scope.
    SymbolTable table = createSymbolTable(
        "var goog = {};" +
        "goog.dom = {};" +
        "goog.dom.DomHelper = function(){};");
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

  public void testIncompleteNamespacedReferences() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @constructor */\n" +
        "goog.dom.DomHelper = function(){};\n" +
        "var y = goog.dom.DomHelper;\n");
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

  public void testGlobalRichObjectReference() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @constructor */\n" +
        "function A(){};\n" +
        "/** @type {?A} */ A.prototype.b;\n" +
        "/** @type {A} */ var a = new A();\n" +
        "function g() {\n" +
        "  return a.b ? 'x' : 'y';\n" +
        "}\n" +
        "(function() {\n" +
        "  var x; if (x) { x = a.b.b; } else { x = a.b.c; }\n" +
        "  return x;\n" +
        "})();\n");

    Symbol ab = getGlobalVar(table, "a.b");
    assertNull(ab);

    Symbol propB = getGlobalVar(table, "A.prototype.b");
    assertNotNull(propB);
    assertThat(table.getReferenceList(propB)).hasSize(5);
  }

  public void testRemovalOfNamespacedReferencesOfProperties()
      throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @constructor */ var DomHelper = function(){};" +
        "/** method */ DomHelper.method = function() {};");

    Symbol domHelper = getGlobalVar(table, "DomHelper");
    assertNotNull(domHelper);

    Symbol domHelperNamespacedMethod = getGlobalVar(table, "DomHelper.method");
    assertEquals("method", domHelperNamespacedMethod.getName());

    Symbol domHelperMethod = domHelper.getPropertyScope().getSlot("method");
    assertNotNull(domHelperMethod);
  }

  public void testGoogScopeReferences() throws Exception {
    SymbolTable table = createSymbolTable(
        "var goog = {};" +
        "goog.scope = function() {};" +
        "goog.scope(function() {});");
    Symbol googScope = getGlobalVar(table, "goog.scope");
    assertNotNull(googScope);
    assertThat(table.getReferences(googScope)).hasSize(2);
  }

  public void testGoogRequireReferences() throws Exception {
    SymbolTable table = createSymbolTable(
        "var goog = {};" +
        "goog.provide = function() {};" +
        "goog.require = function() {};" +
        "goog.provide('goog.dom');" +
        "goog.require('goog.dom');");
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

  public void testGoogRequireReferences2() throws Exception {
    options.setBrokenClosureRequiresLevel(CheckLevel.OFF);
    SymbolTable table = createSymbolTable("foo.bar = function(){};  // definition\n"
        + "goog.require('foo.bar')\n");
    Symbol fooBar = getGlobalVar(table, "foo.bar");
    assertNotNull(fooBar);
    assertThat(table.getReferences(fooBar)).hasSize(2);
  }

  public void testGlobalVarInExterns() throws Exception {
    SymbolTable table = createSymbolTable("customExternFn(1);");
    Symbol fn = getGlobalVar(table, "customExternFn");
    List<Reference> refs = table.getReferenceList(fn);
    assertThat(refs).hasSize(2);

    SymbolScope scope = table.getEnclosingScope(refs.get(0).getNode());
    assertTrue(scope.isGlobalScope());
    assertEquals(SymbolTable.GLOBAL_THIS,
        table.getSymbolForScope(scope).getName());
  }

  public void testLocalVarInExterns() throws Exception {
    SymbolTable table = createSymbolTable("");
    Symbol arg = getLocalVar(table, "customExternArg");
    List<Reference> refs = table.getReferenceList(arg);
    assertThat(refs).hasSize(1);

    Symbol fn = getGlobalVar(table, "customExternFn");
    SymbolScope scope = table.getEnclosingScope(refs.get(0).getNode());
    assertFalse(scope.isGlobalScope());
    assertEquals(fn, table.getSymbolForScope(scope));
  }

  public void testSymbolsForType() throws Exception {
    SymbolTable table = createSymbolTable(
        "function random() { return 1; }" +
        "/** @constructor */ function Foo() {}" +
        "/** @constructor */ function Bar() {}" +
        "var x = random() ? new Foo() : new Bar();");

    Symbol x = getGlobalVar(table, "x");
    Symbol foo = getGlobalVar(table, "Foo");
    Symbol bar = getGlobalVar(table, "Bar");
    Symbol fooPrototype = getGlobalVar(table, "Foo.prototype");
    Symbol fn = getGlobalVar(table, "Function");
    assertEquals(
        Lists.newArrayList(foo, bar), table.getAllSymbolsForTypeOf(x));
    assertEquals(
        Lists.newArrayList(fn), table.getAllSymbolsForTypeOf(foo));
    assertEquals(
        Lists.newArrayList(foo), table.getAllSymbolsForTypeOf(fooPrototype));
    assertEquals(
        foo,
        table.getSymbolDeclaredBy(
            foo.getType().toMaybeFunctionType()));
  }

  public void testStaticMethodReferences() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @constructor */ var DomHelper = function(){};" +
        "/** method */ DomHelper.method = function() {};" +
        "function f() { var x = DomHelper; x.method() + x.method(); }");

    Symbol method =
        getGlobalVar(table, "DomHelper").getPropertyScope().getSlot("method");
    assertThat(table.getReferences(method)).hasSize(3);
  }

  public void testMethodReferences() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @constructor */ var DomHelper = function(){};" +
        "/** method */ DomHelper.prototype.method = function() {};" +
        "function f() { " +
        "  (new DomHelper()).method(); (new DomHelper()).method(); };");

    Symbol method =
        getGlobalVar(table, "DomHelper.prototype.method");
    assertThat(table.getReferences(method)).hasSize(3);
  }

  public void testSuperClassMethodReferences() throws Exception {
    SymbolTable table = createSymbolTable(
        "var goog = {};" +
        "goog.inherits = function(a, b) {};" +
        "/** @constructor */ var A = function(){};" +
        "/** method */ A.prototype.method = function() {};" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {A}\n" +
        " */\n" +
        "var B = function(){};\n" +
        "goog.inherits(B, A);" +
        "/** method */ B.prototype.method = function() {" +
        "  B.superClass_.method();" +
        "};");

    Symbol methodA =
        getGlobalVar(table, "A.prototype.method");
    assertThat(table.getReferences(methodA)).hasSize(2);
  }

  public void testMethodReferencesMissingTypeInfo() throws Exception {
    SymbolTable table = createSymbolTable(
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Missing}\n" +
        " */ var DomHelper = function(){};\n" +
        "/** method */ DomHelper.prototype.method = function() {\n" +
        "  this.method();\n" +
        "};\n" +
        "function f() { " +
        "  (new DomHelper()).method();\n" +
        "};");

    Symbol method =
        getGlobalVar(table, "DomHelper.prototype.method");
    assertThat(table.getReferences(method)).hasSize(3);
  }

  public void testFieldReferencesMissingTypeInfo() throws Exception {
    SymbolTable table = createSymbolTable(
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Missing}\n" +
        " */ var DomHelper = function(){ this.prop = 1; };\n" +
        "/** @type {number} */ DomHelper.prototype.prop = 2;\n" +
        "function f() {\n" +
        "  return (new DomHelper()).prop;\n" +
        "};");

    Symbol prop =
        getGlobalVar(table, "DomHelper.prototype.prop");
    assertThat(table.getReferenceList(prop)).hasSize(3);

    assertNull(getLocalVar(table, "this.prop"));
  }

  public void testFieldReferences() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @constructor */ var DomHelper = function(){" +
        "  /** @type {number} */ this.field = 3;" +
        "};" +
        "function f() { " +
        "  return (new DomHelper()).field + (new DomHelper()).field; };");

    Symbol field = getGlobalVar(table, "DomHelper.prototype.field");
    assertThat(table.getReferences(field)).hasSize(3);
  }

  public void testUndeclaredFieldReferences() throws Exception {
    // We do not currently create symbol table entries for undeclared fields,
    // but this may change in the future.
    SymbolTable table = createSymbolTable(
        "/** @constructor */ var DomHelper = function(){};" +
        "DomHelper.prototype.method = function() { " +
        "  this.field = 3;" +
        "  return x.field;" +
        "}");

    Symbol field = getGlobalVar(table, "DomHelper.prototype.field");
    assertNull(field);
  }

  public void testPrototypeReferences() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @constructor */ function DomHelper() {}" +
        "DomHelper.prototype.method = function() {};");
    Symbol prototype =
        getGlobalVar(table, "DomHelper.prototype");
    assertNotNull(prototype);

    List<Reference> refs = table.getReferenceList(prototype);

    // One of the refs is implicit in the declaration of the function.
    assertEquals(refs.toString(), 2, refs.size());
  }

  public void testPrototypeReferences2() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @constructor */\n"
        + "function Snork() {}\n"
        + "Snork.prototype.baz = 3;\n");
    Symbol prototype =
        getGlobalVar(table, "Snork.prototype");
    assertNotNull(prototype);

    List<Reference> refs = table.getReferenceList(prototype);
    assertThat(refs).hasSize(2);
  }

  public void testPrototypeReferences3() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @constructor */ function Foo() {}");
    Symbol fooPrototype = getGlobalVar(table, "Foo.prototype");
    assertNotNull(fooPrototype);

    List<Reference> refs = table.getReferenceList(fooPrototype);
    assertThat(refs).hasSize(1);
    assertEquals(Token.NAME, refs.get(0).getNode().getType());

    // Make sure that the ctor and its prototype are declared at the
    // same node.
    assertEquals(
        refs.get(0).getNode(),
        table.getReferenceList(getGlobalVar(table, "Foo")).get(0).getNode());
  }

  public void testPrototypeReferences4() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype = {bar: 3}");
    Symbol fooPrototype = getGlobalVar(table, "Foo.prototype");
    assertNotNull(fooPrototype);

    List<Reference> refs = Lists.newArrayList(
        table.getReferences(fooPrototype));
    assertThat(refs).hasSize(1);
    assertEquals(Token.GETPROP, refs.get(0).getNode().getType());
    assertEquals("Foo.prototype", refs.get(0).getNode().getQualifiedName());
  }

  public void testPrototypeReferences5() throws Exception {
    SymbolTable table = createSymbolTable(
        "var goog = {}; /** @constructor */ goog.Foo = function() {};");
    Symbol fooPrototype = getGlobalVar(table, "goog.Foo.prototype");
    assertNotNull(fooPrototype);

    List<Reference> refs = table.getReferenceList(fooPrototype);
    assertThat(refs).hasSize(1);
    assertEquals(Token.GETPROP, refs.get(0).getNode().getType());

    // Make sure that the ctor and its prototype are declared at the
    // same node.
    assertEquals(
        refs.get(0).getNode(),
        table.getReferenceList(
            getGlobalVar(table, "goog.Foo")).get(0).getNode());
  }

  public void testReferencesInJSDocType() {
    SymbolTable table = createSymbolTable(
        "/** @constructor */ function Foo() {}\n" +
        "/** @type {Foo} */ var x;\n" +
        "/** @param {Foo} x */ function f(x) {}\n" +
        "/** @return {function(): Foo} */ function g() {}\n" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {Foo}\n" +
        " */ function Sub() {}");
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

  public void testReferencesInJSDocType2() {
    SymbolTable table = createSymbolTable(
        "/** @param {string} x */ function f(x) {}\n");
    Symbol str = getGlobalVar(table, "String");
    assertNotNull(str);

    List<Reference> refs = table.getReferenceList(str);

    // We're going to pick up a lot of references from the externs,
    // so it's not meaningful to check the number of references.
    // We really want to make sure that all the references are in the externs,
    // except the last one.
    assertTrue(refs.size() > 1);

    int last = refs.size() - 1;
    for (int i = 0; i < refs.size(); i++) {
      Reference ref = refs.get(i);
      assertEquals(i != last, ref.getNode().isFromExterns());
      if (!ref.getNode().isFromExterns()) {
        assertEquals("in1", ref.getNode().getSourceFileName());
      }
    }
  }

  public void testDottedReferencesInJSDocType() {
    SymbolTable table = createSymbolTable(
        "var goog = {};\n" +
        "/** @constructor */ goog.Foo = function() {}\n" +
        "/** @type {goog.Foo} */ var x;\n" +
        "/** @param {goog.Foo} x */ function f(x) {}\n" +
        "/** @return {function(): goog.Foo} */ function g() {}\n" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {goog.Foo}\n" +
        " */ function Sub() {}");
    Symbol foo = getGlobalVar(table, "goog.Foo");
    assertNotNull(foo);

    List<Reference> refs = table.getReferenceList(foo);
    assertThat(refs).hasSize(5);

    assertEquals(2, refs.get(0).getNode().getLineno());
    assertEquals(20, refs.get(0).getNode().getCharno());
    assertEquals(8, refs.get(0).getNode().getLength());

    assertEquals(3, refs.get(1).getNode().getLineno());
    assertEquals(11, refs.get(1).getNode().getCharno());

    assertEquals(4, refs.get(2).getNode().getLineno());
    assertEquals(12, refs.get(2).getNode().getCharno());

    assertEquals(5, refs.get(3).getNode().getLineno());
    assertEquals(25, refs.get(3).getNode().getCharno());

    assertEquals(8, refs.get(4).getNode().getLineno());
    assertEquals(13, refs.get(4).getNode().getCharno());
  }

  public void testReferencesInJSDocName() {
    String code = "/** @param {Object} x */ function f(x) {}\n";
    SymbolTable table = createSymbolTable(code);
    Symbol x = getLocalVar(table, "x");
    assertNotNull(x);

    List<Reference> refs = table.getReferenceList(x);
    assertThat(refs).hasSize(2);

    assertEquals(code.indexOf("x) {"), refs.get(0).getNode().getCharno());
    assertEquals(code.indexOf("x */"), refs.get(1).getNode().getCharno());
    assertEquals("in1",
        refs.get(0).getNode().getSourceFileName());
  }

  public void testLocalQualifiedNamesInLocalScopes() {
    SymbolTable table = createSymbolTable(
        "function f() { var x = {}; x.number = 3; }");
    Symbol xNumber = getLocalVar(table, "x.number");
    assertNotNull(xNumber);
    assertFalse(table.getScope(xNumber).isGlobalScope());

    assertEquals("number", xNumber.getType().toString());
  }

  public void testNaturalSymbolOrdering() {
    SymbolTable table = createSymbolTable(
        "/** @const */ var a = {};" +
        "/** @const */ a.b = {};" +
        "/** @param {number} x */ function f(x) {}");
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

  public void testDeclarationDisagreement() {
    SymbolTable table = createSymbolTable(
        "/** @const */ var goog = goog || {};\n" +
        "/** @param {!Function} x */\n" +
        "goog.addSingletonGetter2 = function(x) {};\n" +
        "/** Wakka wakka wakka */\n" +
        "goog.addSingletonGetter = goog.addSingletonGetter2;\n" +
        "/** @param {!Function} x */\n" +
        "goog.addSingletonGetter = function(x) {};\n");

    Symbol method = getGlobalVar(table, "goog.addSingletonGetter");
    List<Reference> refs = table.getReferenceList(method);
    assertThat(refs).hasSize(2);

    // Note that the declaration should show up second.
    assertEquals(7, method.getDeclaration().getNode().getLineno());
    assertEquals(5, refs.get(1).getNode().getLineno());
  }

  public void testMultipleExtends() {
    SymbolTable table = createSymbolTable(
        "/** @const */ var goog = goog || {};\n" +
        "goog.inherits = function(x, y) {};\n" +
        "/** @constructor */\n" +
        "goog.A = function() { this.fieldA = this.constructor; };\n" +
        "/** @constructor */ goog.A.FooA = function() {};\n" +
        "/** @return {void} */ goog.A.prototype.methodA = function() {};\n" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {goog.A}\n" +
        " */\n" +
        "goog.B = function() { this.fieldB = this.constructor; };\n" +
        "goog.inherits(goog.B, goog.A);\n" +
        "/** @return {void} */ goog.B.prototype.methodB = function() {};\n" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {goog.A}\n" +
        " */\n" +
        "goog.B2 = function() { this.fieldB = this.constructor; };\n" +
        "goog.inherits(goog.B2, goog.A);\n" +
        "/** @constructor */ goog.B2.FooB = function() {};\n" +
        "/** @return {void} */ goog.B2.prototype.methodB = function() {};\n" +
        "/**\n" +
        " * @constructor\n" +
        " * @extends {goog.B}\n" +
        " */\n" +
        "goog.C = function() { this.fieldC = this.constructor; };\n" +
        "goog.inherits(goog.C, goog.B);\n" +
        "/** @constructor */ goog.C.FooC = function() {};\n" +
        "/** @return {void} */ goog.C.prototype.methodC = function() {};\n");

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

  public void testJSDocAssociationWithBadNamespace() {
    SymbolTable table = createSymbolTable(
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

  public void testMissingConstructorTag() {
    SymbolTable table = createSymbolTable(
        "function F() {" +
        "  this.field1 = 3;" +
        "}" +
        "F.prototype.method1 = function() {" +
        "  this.field1 = 5;" +
        "};" +
        "(new F()).method1();");

    // Because the constructor tag is missing, this is going
    // to be missing a lot of inference.
    assertNull(getGlobalVar(table, "F.prototype.field1"));

    Symbol sym = getGlobalVar(table, "F.prototype.method1");
    assertThat(table.getReferenceList(sym)).hasSize(1);
  }

  public void testTypeCheckingOff() {
    options = new CompilerOptions();

    // Turning type-checking off is even worse than not annotating anything.
    SymbolTable table = createSymbolTable(
        "/** @contstructor */" +
        "function F() {" +
        "  this.field1 = 3;" +
        "}" +
        "F.prototype.method1 = function() {" +
        "  this.field1 = 5;" +
        "};" +
        "(new F()).method1();");
    assertNull(getGlobalVar(table, "F.prototype.field1"));
    assertNull(getGlobalVar(table, "F.prototype.method1"));

    Symbol sym = getGlobalVar(table, "F");
    assertThat(table.getReferenceList(sym)).hasSize(3);
  }

  public void testSuperClassReference() throws Exception {
    SymbolTable table = createSymbolTable(
        "  var a = {b: {}};\n"
        + "/** @constructor */\n"
        + "a.b.BaseClass = function() {};\n"
        + "a.b.BaseClass.prototype.doSomething = function() {\n"
        + "  alert('hi');\n"
        + "};\n"
        + "/**\n"
        + " * @constructor\n"
        + " * @extends {a.b.BaseClass}\n"
        + " */\n"
        + "a.b.DerivedClass = function() {};\n"
        + "goog.inherits(a.b.DerivedClass, a.b.BaseClass);\n"
        + "/** @override */\n"
        + "a.b.DerivedClass.prototype.doSomething = function() {\n"
        + "  a.b.DerivedClass.superClass_.doSomething();\n"
        + "};\n");

    Symbol bad = getGlobalVar(
        table, "a.b.DerivedClass.superClass_.doSomething");
    assertNull(bad);

    Symbol good = getGlobalVar(
        table, "a.b.BaseClass.prototype.doSomething");
    assertNotNull(good);

    List<Reference> refs = table.getReferenceList(good);
    assertThat(refs).hasSize(2);
    assertEquals(
        "a.b.DerivedClass.superClass_.doSomething", refs.get(1).getNode().getQualifiedName());
  }

  public void testInnerEnum() throws Exception {
    SymbolTable table = createSymbolTable(
        "var goog = {}; goog.ui = {};"
        + "  /** @constructor */\n"
        + "goog.ui.Zippy = function() {};\n"
        + "/** @enum {string} */\n"
        + "goog.ui.Zippy.EventType = { TOGGLE: 'toggle' };\n");

    Symbol eventType = getGlobalVar(table, "goog.ui.Zippy.EventType");
    assertNotNull(eventType);
    assertTrue(eventType.getType().isEnumType());

    Symbol toggle = getGlobalVar(table, "goog.ui.Zippy.EventType.TOGGLE");
    assertNotNull(toggle);
  }

  public void testMethodInAnonObject1() throws Exception {
    SymbolTable table = createSymbolTable(
        "var a = {}; a.b = {}; a.b.c = function() {};");
    Symbol a = getGlobalVar(table, "a");
    Symbol ab = getGlobalVar(table, "a.b");
    Symbol abc = getGlobalVar(table, "a.b.c");

    assertNotNull(abc);
    assertThat(table.getReferenceList(abc)).hasSize(1);

    assertEquals("{b: {c: function (): undefined}}", a.getType().toString());
    assertEquals("{c: function (): undefined}", ab.getType().toString());
    assertEquals("function (): undefined", abc.getType().toString());
  }

  public void testMethodInAnonObject2() throws Exception {
    SymbolTable table = createSymbolTable(
        "var a = {b: {c: function() {}}};");
    Symbol a = getGlobalVar(table, "a");
    Symbol ab = getGlobalVar(table, "a.b");
    Symbol abc = getGlobalVar(table, "a.b.c");

    assertNotNull(abc);
    assertThat(table.getReferenceList(abc)).hasSize(1);

    assertEquals("{b: {c: function (): undefined}}", a.getType().toString());
    assertEquals("{c: function (): undefined}", ab.getType().toString());
    assertEquals("function (): undefined", abc.getType().toString());
  }

  public void testJSDocOnlySymbol() throws Exception {
    SymbolTable table = createSymbolTable(
        "/**\n"
        + " * @param {number} x\n"
        + " * @param y\n"
        + " */\n"
        + "var a;");
    Symbol x = getDocVar(table, "x");
    assertNotNull(x);
    assertEquals("number", x.getType().toString());
    assertThat(table.getReferenceList(x)).hasSize(1);

    Symbol y = getDocVar(table, "y");
    assertNotNull(x);
    assertNull(y.getType());
    assertThat(table.getReferenceList(y)).hasSize(1);
  }

  public void testNamespaceDefinitionOrder() throws Exception {
    // Sometimes, weird things can happen where the files appear in
    // a strange order. We need to make sure we're robust against this.
    SymbolTable table = createSymbolTable(
        "/** @const */ var goog = {};\n"
        + "/** @constructor */ goog.dom.Foo = function() {};\n"
        + "/** @const */ goog.dom = {};\n");

    Symbol goog = getGlobalVar(table, "goog");
    Symbol dom = getGlobalVar(table, "goog.dom");
    Symbol Foo = getGlobalVar(table, "goog.dom.Foo");

    assertNotNull(goog);
    assertNotNull(dom);
    assertNotNull(Foo);

    assertEquals(dom, goog.getPropertyScope().getSlot("dom"));
    assertEquals(Foo, dom.getPropertyScope().getSlot("Foo"));
  }

  public void testConstructorAlias() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @constructor */ var Foo = function() {};\n" +
        "/** desc */ Foo.prototype.bar = function() {};\n" +
        "/** @constructor */ var FooAlias = Foo;\n" +
        "/** desc */ FooAlias.prototype.baz = function() {};\n");

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

  public void testSymbolForScopeOfNatives() throws Exception {
    SymbolTable table = createSymbolTable("");

    // From the externs.
    Symbol sliceArg = getLocalVar(table, "sliceArg");
    assertNotNull(sliceArg);

    Symbol scope = table.getSymbolForScope(table.getScope(sliceArg));
    assertNotNull(scope);
    assertEquals(scope, getGlobalVar(table, "String.prototype.slice"));

    Symbol proto = getGlobalVar(table, "String.prototype");
    assertEquals(
        "externs1", proto.getDeclaration().getNode().getSourceFileName());
  }

  public void testJSDocNameVisibility() {
    SymbolTable table = createSymbolTable(
        "/** @public */ var foo;\n" +
        "/** @protected */ var bar;\n" +
        "/** @package */ var baz;\n" +
        "/** @private */ var quux;\n" +
        "var xyzzy;");

    assertEquals(Visibility.PUBLIC,
        getGlobalVar(table, "foo").getVisibility());
    assertEquals(Visibility.PROTECTED,
        getGlobalVar(table, "bar").getVisibility());
    assertEquals(Visibility.PACKAGE,
        getGlobalVar(table, "baz").getVisibility());
    assertEquals(Visibility.PRIVATE,
        getGlobalVar(table, "quux").getVisibility());
    assertEquals(Visibility.INHERITED,
        getGlobalVar(table, "xyzzy").getVisibility());
    assertNull(getGlobalVar(table, "xyzzy").getJSDocInfo());
  }

  public void testJSDocNameVisibilityWithFileOverviewVisibility() {
    SymbolTable table = createSymbolTable(
        "/** @fileoverview\n @package */\n" +
        "/** @public */ var foo;\n" +
        "/** @protected */ var bar;\n" +
        "/** @package */ var baz;\n" +
        "/** @private */ var quux;\n" +
        "var xyzzy;");
    assertEquals(Visibility.PUBLIC,
        getGlobalVar(table, "foo").getVisibility());
    assertEquals(Visibility.PROTECTED,
        getGlobalVar(table, "bar").getVisibility());
    assertEquals(Visibility.PACKAGE,
        getGlobalVar(table, "baz").getVisibility());
    assertEquals(Visibility.PRIVATE,
        getGlobalVar(table, "quux").getVisibility());
    assertEquals(Visibility.PACKAGE,
        getGlobalVar(table, "xyzzy").getVisibility());
    assertNull(getGlobalVar(table, "xyzzy").getJSDocInfo());
  }

  public void testJSDocPropertyVisibility() {
    SymbolTable table = createSymbolTable(
        "/** @constructor */ var Foo = function() {};\n" +
        "/** @public */ Foo.prototype.bar;\n" +
        "/** @protected */ Foo.prototype.baz;\n" +
        "/** @package */ Foo.prototype.quux;\n" +
        "/** @private */ Foo.prototype.xyzzy;\n" +
        "Foo.prototype.plugh;\n" +

        "/** @constructor @extends {Foo} */ var SubFoo = function() {};\n" +
        "/** @override */ SubFoo.prototype.bar = function() {};\n" +
        "/** @override */ SubFoo.prototype.baz = function() {};\n" +
        "/** @override */ SubFoo.prototype.quux = function() {};\n" +
        "/** @override */ SubFoo.prototype.xyzzy = function() {};\n" +
        "/** @override */ SubFoo.prototype.plugh = function() {};");

    assertEquals(Visibility.PUBLIC,
        getGlobalVar(table, "Foo.prototype.bar").getVisibility());
    assertEquals(Visibility.PROTECTED,
        getGlobalVar(table, "Foo.prototype.baz").getVisibility());
    assertEquals(Visibility.PACKAGE,
        getGlobalVar(table, "Foo.prototype.quux").getVisibility());
    assertEquals(Visibility.PRIVATE,
        getGlobalVar(table, "Foo.prototype.xyzzy").getVisibility());
    assertEquals(Visibility.INHERITED,
        getGlobalVar(table, "Foo.prototype.plugh").getVisibility());
    assertNull(getGlobalVar(table, "Foo.prototype.plugh").getJSDocInfo());


    assertEquals(Visibility.INHERITED,
        getGlobalVar(table, "SubFoo.prototype.bar").getVisibility());
    assertEquals(Visibility.INHERITED,
        getGlobalVar(table, "SubFoo.prototype.baz").getVisibility());
    assertEquals(Visibility.INHERITED,
        getGlobalVar(table, "SubFoo.prototype.quux").getVisibility());
    assertEquals(Visibility.INHERITED,
        getGlobalVar(table, "SubFoo.prototype.xyzzy").getVisibility());
    assertEquals(Visibility.INHERITED,
        getGlobalVar(table, "SubFoo.prototype.plugh").getVisibility());
  }

  public void testJSDocPropertyVisibilityWithFileOverviewVisibility() {
    SymbolTable table = createSymbolTable(
        "/** @fileoverview\n @package */\n" +
        "/** @constructor */ var Foo = function() {};\n" +
        "/** @public */ Foo.prototype.bar;\n" +
        "/** @protected */ Foo.prototype.baz;\n" +
        "/** @package */ Foo.prototype.quux;\n" +
        "/** @private */ Foo.prototype.xyzzy;\n" +
        "Foo.prototype.plugh;\n" +

        "/** @constructor @extends {Foo} */ var SubFoo = function() {};\n" +
        "/** @override @public */ SubFoo.prototype.bar = function() {};\n" +
        "/** @override @protected */ SubFoo.prototype.baz = function() {};\n" +
        "/** @override @package */ SubFoo.prototype.quux = function() {};\n" +
        "/** @override @private */ SubFoo.prototype.xyzzy = function() {};\n" +
        "/** @override */ SubFoo.prototype.plugh = function() {};");

    assertEquals(Visibility.PUBLIC,
        getGlobalVar(table, "Foo.prototype.bar").getVisibility());
    assertEquals(Visibility.PROTECTED,
        getGlobalVar(table, "Foo.prototype.baz").getVisibility());
    assertEquals(Visibility.PACKAGE,
        getGlobalVar(table, "Foo.prototype.quux").getVisibility());
    assertEquals(Visibility.PRIVATE,
        getGlobalVar(table, "Foo.prototype.xyzzy").getVisibility());
    assertEquals(Visibility.PACKAGE,
        getGlobalVar(table, "Foo.prototype.plugh").getVisibility());
    assertNull(getGlobalVar(table, "Foo.prototype.plugh").getJSDocInfo());


    assertEquals(Visibility.INHERITED,
        getGlobalVar(table, "SubFoo.prototype.bar").getVisibility());
    assertEquals(Visibility.INHERITED,
        getGlobalVar(table, "SubFoo.prototype.baz").getVisibility());
    assertEquals(Visibility.INHERITED,
        getGlobalVar(table, "SubFoo.prototype.quux").getVisibility());
    assertEquals(Visibility.INHERITED,
        getGlobalVar(table, "SubFoo.prototype.xyzzy").getVisibility());
    assertEquals(Visibility.INHERITED,
        getGlobalVar(table, "SubFoo.prototype.plugh").getVisibility());
  }

  private void assertSymmetricOrdering(
      Ordering<Symbol> ordering, Symbol first, Symbol second) {
    assertEquals(0, ordering.compare(first, first));
    assertEquals(0, ordering.compare(second, second));
    assertTrue(ordering.compare(first, second) < 0);
    assertTrue(ordering.compare(second, first) > 0);
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
      if (!scope.isGlobalScope() && scope.isLexicalScope() &&
          scope.getQualifiedSlot(name) != null) {
        return scope.getQualifiedSlot(name);
      }
    }
    return null;
  }

  /** Returns all non-extern vars. */
  private List<Symbol> getVars(SymbolTable table) {
    List<Symbol> result = Lists.newArrayList();
    for (Symbol symbol : table.getAllSymbols()) {
      if (symbol.getDeclaration() != null &&
          !symbol.getDeclaration().getNode().isFromExterns()) {
        result.add(symbol);
      }
    }
    return result;
  }

  private SymbolTable createSymbolTable(String input) {
    List<SourceFile> inputs = Lists.newArrayList(
        SourceFile.fromCode("in1", input));
    List<SourceFile> externs = Lists.newArrayList(
        SourceFile.fromCode("externs1", EXTERNS));

    Compiler compiler = new Compiler();
    BlackHoleErrorManager.silence(compiler);
    compiler.compile(externs, inputs, options);
    return assertSymbolTableValid(compiler.buildKnownSymbolTable());
  }

  /**
   * Asserts that the symbol table meets some invariants.
   * Returns the same table for easy chaining.
   */
  private SymbolTable assertSymbolTableValid(SymbolTable table) {
    Set<Symbol> allSymbols = Sets.newHashSet(table.getAllSymbols());
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
          "The symbol's scope is a zombie scope that shouldn't exist.\n" +
          "Symbol: " + sym + "\n" +
          "Scope: " + table.getScope(sym),
          scope == null || allSymbols.contains(scope));
    }

    // Make sure that the global "this" is declared at the first input root.
    Symbol global = getGlobalVar(table, SymbolTable.GLOBAL_THIS);
    assertNotNull(global);
    assertNotNull(global.getDeclaration());
    assertEquals(Token.SCRIPT, global.getDeclaration().getNode().getType());

    List<Reference> globalRefs = table.getReferenceList(global);

    // The main reference list should never contain the synthetic declaration
    // for the global root.
    assertThat(globalRefs).doesNotContain(global.getDeclaration());

    return table;
  }
}
