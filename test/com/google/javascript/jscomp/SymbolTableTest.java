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

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.javascript.jscomp.SymbolTable.Reference;
import com.google.javascript.jscomp.SymbolTable.Symbol;
import com.google.javascript.jscomp.SymbolTable.SymbolScope;
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

import java.util.List;

/**
 * @author nicksantos@google.com (Nick Santos)
 */
public class SymbolTableTest extends TestCase {

  private static final String EXTERNS = CompilerTypeTestCase.DEFAULT_EXTERNS +
      "\nfunction customExternFn(customExternArg) {}";

  public void testGlobalVar() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @type {number} */ var x = 5;");
    assertNull(getGlobalVar(table, "y"));
    assertNotNull(getGlobalVar(table, "x"));
    assertEquals("number", getGlobalVar(table, "x").getType().toString());

    // 2 == sizeof({x, *global*})
    assertEquals(2, getVars(table).size());
  }

  public void testGlobalThisReferences() throws Exception {
    SymbolTable table = createSymbolTable(
        "var x = this; function f() { return this + this + this; }");

    Symbol global = getGlobalVar(table, "*global*");
    assertNotNull(global);

    List<Reference> refs = table.getReferenceList(global);
    assertEquals(1, refs.size());
  }

  public void testGlobalThisReferences2() throws Exception {
    // Make sure the global this is declared, even if it isn't referenced.
    SymbolTable table = createSymbolTable("");

    Symbol global = getGlobalVar(table, "*global*");
    assertNotNull(global);

    List<Reference> refs = table.getReferenceList(global);
    assertEquals(0, refs.size());
  }

  public void testGlobalThisPropertyReferences() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @constructor */ function Foo() {} this.Foo;");

    Symbol foo = getGlobalVar(table, "Foo");
    assertNotNull(foo);

    List<Reference> refs = table.getReferenceList(foo);
    assertEquals(2, refs.size());
  }

  public void testGlobalVarReferences() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @type {number} */ var x = 5; x = 6;");
    Symbol x = getGlobalVar(table, "x");
    List<Reference> refs = table.getReferenceList(x);

    assertEquals(2, refs.size());
    assertEquals(x.getDeclaration(), refs.get(0));
    assertEquals(Token.VAR, refs.get(0).getNode().getParent().getType());
    assertEquals(Token.ASSIGN, refs.get(1).getNode().getParent().getType());
  }

  public void testLocalVarReferences() throws Exception {
    SymbolTable table = createSymbolTable(
        "function f(x) { return x; }");
    Symbol x = getLocalVar(table, "x");
    List<Reference> refs = table.getReferenceList(x);

    assertEquals(2, refs.size());
    assertEquals(x.getDeclaration(), refs.get(0));
    assertEquals(Token.LP, refs.get(0).getNode().getParent().getType());
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
    assertEquals(2, refs.size());
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
    assertEquals(2, refs.size());
  }

  public void testLocalThisReferences3() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @constructor */ function F() {}");

    Symbol baz = getGlobalVar(table, "F");
    assertNotNull(baz);

    Symbol t = table.getParameterInFunction(baz, "this");
    assertNotNull(t);

    List<Reference> refs = table.getReferenceList(t);
    assertEquals(0, refs.size());
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
    assertEquals(3, Iterables.size(table.getReferences(goog)));

    Symbol googDom = getGlobalVar(table, "goog.dom");
    assertNotNull(googDom);
    assertEquals(2, Iterables.size(table.getReferences(googDom)));

    Symbol googDomHelper = getGlobalVar(table, "goog.dom.DomHelper");
    assertNotNull(googDomHelper);
    assertEquals(1, Iterables.size(table.getReferences(googDomHelper)));
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
    assertEquals(2, Iterables.size(table.getReferences(googScope)));
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
    assertEquals(8, Iterables.size(table.getReferences(goog)));
  }

  public void testGlobalVarInExterns() throws Exception {
    SymbolTable table = createSymbolTable("customExternFn(1);");
    Symbol fn = getGlobalVar(table, "customExternFn");
    List<Reference> refs = table.getReferenceList(fn);
    assertEquals(2, refs.size());

    SymbolScope scope = table.getEnclosingScope(refs.get(0).getNode());
    assertTrue(scope.isGlobalScope());
    assertEquals(SymbolTable.GLOBAL_THIS,
        table.getSymbolForScope(scope).getName());
  }

  public void testLocalVarInExterns() throws Exception {
    SymbolTable table = createSymbolTable("");
    Symbol arg = getLocalVar(table, "customExternArg");
    List<Reference> refs = table.getReferenceList(arg);
    assertEquals(1, refs.size());

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
    Symbol obj = getGlobalVar(table, "Object");
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
    assertEquals(
        3, Iterables.size(table.getReferences(method)));
  }

  public void testMethodReferences() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @constructor */ var DomHelper = function(){};" +
        "/** method */ DomHelper.prototype.method = function() {};" +
        "function f() { " +
        "  (new DomHelper()).method(); (new DomHelper()).method(); };");

    Symbol method =
        getGlobalVar(table, "DomHelper.prototype.method");
    assertEquals(
        3, Iterables.size(table.getReferences(method)));
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
    assertEquals(
        2, Iterables.size(table.getReferences(methodA)));
  }

  public void testFieldReferences() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @constructor */ var DomHelper = function(){" +
        "  /** @type {number} */ this.field = 3;" +
        "};" +
        "function f() { " +
        "  return (new DomHelper()).field + (new DomHelper()).field; };");

    Symbol field = getGlobalVar(table, "DomHelper.prototype.field");
    assertEquals(
        3, Iterables.size(table.getReferences(field)));
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
    assertEquals(2, refs.size());
  }

  public void testPrototypeReferences3() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @constructor */ function Foo() {}");
    Symbol fooPrototype = getGlobalVar(table, "Foo.prototype");
    assertNotNull(fooPrototype);

    List<Reference> refs = Lists.newArrayList(
        table.getReferences(fooPrototype));
    assertEquals(1, refs.size());
    assertEquals(Token.FUNCTION, refs.get(0).getNode().getType());
  }

  public void testPrototypeReferences4() throws Exception {
    SymbolTable table = createSymbolTable(
        "/** @constructor */ function Foo() {}" +
        "Foo.prototype = {bar: 3}");
    Symbol fooPrototype = getGlobalVar(table, "Foo.prototype");
    assertNotNull(fooPrototype);

    List<Reference> refs = Lists.newArrayList(
        table.getReferences(fooPrototype));
    assertEquals(1, refs.size());
    assertEquals(Token.GETPROP, refs.get(0).getNode().getType());
    assertEquals("Foo.prototype", refs.get(0).getNode().getQualifiedName());
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
    assertEquals(5, refs.size());

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

  public void testReferencesInJSDocName() {
    String code = "/** @param {Object} x */ function f(x) {}\n";
    SymbolTable table = createSymbolTable(code);
    Symbol x = getLocalVar(table, "x");
    assertNotNull(x);

    List<Reference> refs = table.getReferenceList(x);
    assertEquals(2, refs.size());

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
    assertSymmetricOrdering(ordering, ab, f);
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
    assertEquals(2, refs.size());

    // Note that the declaration should show up second.
    assertEquals(7, method.getDeclaration().getNode().getLineno());
    assertEquals(5, refs.get(1).getNode().getLineno());
  }

  private void assertSymmetricOrdering(
      Ordering<Symbol> ordering, Symbol first, Symbol second) {
    assertTrue(ordering.compare(first, first) == 0);
    assertTrue(ordering.compare(second, second) == 0);
    assertTrue(ordering.compare(first, second) < 0);
    assertTrue(ordering.compare(second, first) > 0);
  }

  private Symbol getGlobalVar(SymbolTable table, String name) {
    return table.getGlobalScope().getSlot(name);
  }

  private Symbol getLocalVar(SymbolTable table, String name) {
    for (SymbolScope scope : table.getAllScopes()) {
      if (!scope.isGlobalScope() && scope.isLexicalScope() &&
          scope.getSlot(name) != null) {
        return scope.getSlot(name);
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
    List<JSSourceFile> inputs = Lists.newArrayList(
        JSSourceFile.fromCode("in1", input));
    List<JSSourceFile> externs = Lists.newArrayList(
        JSSourceFile.fromCode("externs1", EXTERNS));
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(
        options);
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.ideMode = true;

    Compiler compiler = new Compiler();
    compiler.compile(externs, inputs, options);
    return assertSymbolTableValid(compiler.buildKnownSymbolTable());
  }

  /**
   * Asserts that the symbol table meets some invariants.
   * Returns the same table for easy chaining.
   */
  private SymbolTable assertSymbolTableValid(SymbolTable table) {
    for (Symbol sym : table.getAllSymbols()) {
      // Make sure that grabbing the symbol's scope and looking it up
      // again produces the same symbol.
      assertEquals(sym, table.getScope(sym).getSlot(sym.getName()));

      for (Reference ref : table.getReferences(sym)) {
        // Make sure that the symbol and reference are mutually linked.
        assertEquals(sym, ref.getSymbol());
      }
    }

    return table;
  }
}
