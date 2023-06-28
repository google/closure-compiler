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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.javascript.jscomp.CompilerTestCase.lines;
import static com.google.javascript.jscomp.parsing.Config.JsDocParsing.INCLUDE_DESCRIPTIONS_NO_WHITESPACE;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.common.testing.EqualsTester;
import com.google.javascript.jscomp.SymbolTable.Reference;
import com.google.javascript.jscomp.SymbolTable.Symbol;
import com.google.javascript.jscomp.SymbolTable.SymbolScope;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** An integration test for symbol table creation */
@RunWith(JUnit4.class)
public final class SymbolTableTest {

  private CompilerOptions options;

  @Before
  public void setUp() throws Exception {

    options = new CompilerOptions();
    options.setCodingConvention(new ClosureCodingConvention());
    CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    options.setChecksOnly(true);
    options.setPreserveDetailedSourceInfo(true);
    options.setPreserveClosurePrimitives(true);
    options.setContinueAfterErrors(true);
    options.setParseJsDocDocumentation(INCLUDE_DESCRIPTIONS_NO_WHITESPACE);
    options.setEnableModuleRewriting(false);
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
    assertThat(getGlobalVar(table, "y")).isNull();
    assertThat(getGlobalVar(table, "x")).isNotNull();
    assertThat(getGlobalVar(table, "x").getType().toString()).isEqualTo("number");

    // 2 == sizeof({x, *global*})
    assertThat(getVars(table)).hasSize(2);
  }

  @Test
  public void testGlobalThisReferences() {
    SymbolTable table =
        createSymbolTable("var x = this; function f() { return this + this + this; }");

    Symbol global = getGlobalVar(table, "*global*");
    assertThat(global).isNotNull();

    ImmutableList<Reference> refs = table.getReferenceList(global);
    assertThat(refs).hasSize(1);
  }

  @Test
  public void testGlobalThisReferences2() {
    // Make sure the global this is declared, even if it isn't referenced.
    SymbolTable table = createSymbolTable("");

    Symbol global = getGlobalVar(table, "*global*");
    assertThat(global).isNotNull();

    ImmutableList<Reference> refs = table.getReferenceList(global);
    assertThat(refs).isEmpty();
  }

  @Test
  public void testGlobalThisReferences3() {
    SymbolTable table = createSymbolTable("this.foo = {}; this.foo.bar = {};");

    Symbol global = getGlobalVar(table, "*global*");
    assertThat(global).isNotNull();

    ImmutableList<Reference> refs = table.getReferenceList(global);
    assertThat(refs).hasSize(2);
  }

  @Test
  public void testSourceInfoForProvidedSymbol() {
    SymbolTable table =
        createSymbolTable(lines("goog.provide('foo.bar.Baz'); foo.bar.Baz = class {};"));

    Symbol foo = getGlobalVar(table, "foo");
    assertThat(foo).isNotNull();
    // goog.provide doesn't define foo. Instead foo is declared by the firts occurence in actual
    // code.
    assertNode(table.getReferenceList(foo).get(0).getNode()).hasCharno(29);

    Symbol fooBar = getGlobalVar(table, "foo.bar");
    assertThat(fooBar).isNotNull();
    assertNode(table.getReferenceList(fooBar).get(0).getNode()).hasCharno(33);

    Symbol fooBarBaz = getGlobalVar(table, "foo.bar.Baz");
    assertThat(fooBarBaz).isNotNull();
    assertNode(table.getReferenceList(fooBarBaz).get(0).getNode()).hasCharno(37);
  }

  @Test
  public void testGlobalThisPropertyReferences() {
    SymbolTable table = createSymbolTable("/** @constructor */ function Foo() {} this.Foo;");

    Symbol foo = getGlobalVar(table, "Foo");
    assertThat(foo).isNotNull();

    ImmutableList<Reference> refs = table.getReferenceList(foo);
    assertThat(refs).hasSize(2);
  }

  @Test
  public void testGlobalVarReferences() {
    SymbolTable table = createSymbolTable("/** @type {number} */ var x = 5; x = 6;");
    Symbol x = getGlobalVar(table, "x");
    ImmutableList<Reference> refs = table.getReferenceList(x);

    assertThat(refs).hasSize(2);
    assertThat(refs.get(0)).isEqualTo(x.getDeclaration());
    assertThat(refs.get(0).getNode().getParent().getToken()).isEqualTo(Token.VAR);
    assertThat(refs.get(1).getNode().getParent().getToken()).isEqualTo(Token.ASSIGN);
  }

  @Test
  public void testLocalVarReferences() {
    SymbolTable table = createSymbolTable("function f(x) { return x; }");
    Symbol x = getLocalVar(table, "x");
    ImmutableList<Reference> refs = table.getReferenceList(x);

    assertThat(refs).hasSize(2);
    assertThat(refs.get(0)).isEqualTo(x.getDeclaration());
    assertThat(refs.get(0).getNode().getParent().getToken()).isEqualTo(Token.PARAM_LIST);
    assertThat(refs.get(1).getNode().getParent().getToken()).isEqualTo(Token.RETURN);
  }

  @Test
  public void testLocalThisReferences() {
    SymbolTable table =
        createSymbolTable("/** @constructor */ function F() { this.foo = 3; this.bar = 5; }");

    Symbol f = getGlobalVar(table, "F");
    assertThat(f).isNotNull();

    Symbol t = table.getParameterInFunction(f, "this");
    assertThat(t).isNotNull();

    ImmutableList<Reference> refs = table.getReferenceList(t);
    assertThat(refs).hasSize(2);
  }

  // No 'this' reference is created for empty functions.
  @Test
  public void testLocalThisReferences3() {
    SymbolTable table = createSymbolTable("/** @constructor */ function F() {}");

    Symbol baz = getGlobalVar(table, "F");
    assertThat(baz).isNotNull();

    Symbol t = table.getParameterInFunction(baz, "this");
    assertThat(t).isNull();
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
    assertThat(objFn).isNotNull();
    ImmutableList<Reference> references = table.getReferenceList(objFn);
    assertThat(references).hasSize(2);

    // The declaration node corresponds to "fn", not "fn() {}", in the source info.
    Node declaration = objFn.getDeclarationNode();
    assertThat(declaration.getCharno()).isEqualTo(12);
    assertThat(declaration.getLength()).isEqualTo(2);
  }

  @Test
  public void testNamespacedReferences() {
    // Because the type of 'my' is anonymous, we build its properties into
    // the global scope.
    SymbolTable table =
        createSymbolTable(
            lines("var my = {};", "my.dom = {};", "my.dom.DomHelper = function(){};"));

    Symbol my = getGlobalVar(table, "my");
    assertThat(my).isNotNull();
    assertThat(table.getReferences(my)).hasSize(3);

    Symbol myDom = getGlobalVar(table, "my.dom");
    assertThat(myDom).isNotNull();
    assertThat(table.getReferences(myDom)).hasSize(2);

    Symbol myDomHelper = getGlobalVar(table, "my.dom.DomHelper");
    assertThat(myDomHelper).isNotNull();
    assertThat(table.getReferences(myDomHelper)).hasSize(1);
  }

  @Test
  public void testIncompleteNamespacedReferences() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @constructor */",
                "my.dom.DomHelper = function(){};",
                "var y = my.dom.DomHelper;"));
    Symbol my = getGlobalVar(table, "my");
    assertThat(my).isNotNull();
    assertThat(table.getReferenceList(my)).hasSize(2);

    Symbol myDom = getGlobalVar(table, "my.dom");
    assertThat(myDom).isNotNull();
    assertThat(table.getReferenceList(myDom)).hasSize(2);

    Symbol myDomHelper = getGlobalVar(table, "my.dom.DomHelper");
    assertThat(myDomHelper).isNotNull();
    assertThat(table.getReferences(myDomHelper)).hasSize(2);
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
    assertThat(ab).isNull();

    Symbol propB = getGlobalVar(table, "A.prototype.b");
    assertThat(propB).isNotNull();
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
    assertThat(domHelper).isNotNull();

    Symbol domHelperNamespacedMethod = getGlobalVar(table, "DomHelper.method");
    assertThat(domHelperNamespacedMethod.getName()).isEqualTo("method");

    Symbol domHelperMethod = domHelper.getPropertyScope().getSlot("method");
    assertThat(domHelperMethod).isNotNull();
  }

  @Test
  public void testGoogScopeReferences() {
    SymbolTable table =
        createSymbolTableWithDefaultExterns(
            // goog.scope is defined in the default externs, among other Closure methods
            lines("goog.scope(function() {});"));

    Symbol googScope = getGlobalVar(table, "goog.scope");
    assertThat(googScope).isNotNull();
    assertThat(table.getReferences(googScope)).hasSize(2);
  }

  @Test
  public void testGoogRequireReferences() {
    SymbolTable table =
        createSymbolTableWithDefaultExterns(
            lines(
                // goog.require is defined in the default externs, among other Closure methods
                "goog.provide('goog.dom');", "goog.require('goog.dom');"));
    Symbol googRequire = getGlobalVar(table, "goog.require");
    assertThat(googRequire).isNotNull();

    assertThat(table.getReferences(googRequire)).hasSize(2);
  }

  @Test
  public void testNamespaceReferencesInGoogRequire() {
    SymbolTable table =
        createSymbolTable(lines("goog.provide('my.dom');", "goog.require('my.dom');"));
    Symbol googRequire = getGlobalVar(table, "ns$my.dom");
    assertThat(googRequire).isNotNull();

    assertThat(table.getReferences(googRequire)).hasSize(2);
  }

  private void exerciseGoogRequireReferenceCorrectly(String inputProvidingNamespace) {
    SymbolTable table =
        createSymbolTableFromManySources(
            inputProvidingNamespace,
            lines(
                "goog.provide('module.two');",
                "goog.require('module.one');",
                "goog.requireType('module.one');",
                "goog.forwardDeclare('module.one');"),
            lines(
                "goog.module('module.three');",
                "const one = goog.require('module.one');",
                "const oneType = goog.requireType('module.one');",
                "const oneForward = goog.forwardDeclare('module.one');"),
            lines(
                "goog.module('module.four');",
                "goog.module.declareLegacyNamespace();",
                "const one = goog.require('module.one');",
                "const oneType = goog.requireType('module.one');",
                "const oneForward = goog.forwardDeclare('module.one');"));
    Symbol namespace = getGlobalVar(table, "ns$module.one");
    assertThat(namespace).isNotNull();
    // 1 ref from declaration and then 2 refs in each of 3 files.
    assertThat(table.getReferences(namespace)).hasSize(10);
  }

  @Test
  public void testGoogProvideReferenced() {
    exerciseGoogRequireReferenceCorrectly("goog.provide('module.one');");
  }

  @Test
  public void testGoogModuleReferenced() {
    exerciseGoogRequireReferenceCorrectly("goog.module('module.one');");
  }

  @Test
  public void testGoogLegacyModuleReferenced() {
    exerciseGoogRequireReferenceCorrectly(
        lines("goog.module('module.one');", "goog.module.declareLegacyNamespace();"));
  }

  private void verifySymbolReferencedInSecondFile(
      String firstFile, String secondFile, String symbolName) {
    SymbolTable table = createSymbolTableFromManySources(firstFile, secondFile);
    Symbol symbol =
        table.getAllSymbols().stream()
            .filter(
                (s) -> s.getSourceFileName().equals("file1.js") && s.getName().equals(symbolName))
            .findFirst()
            .get();
    for (Reference ref : table.getReferences(symbol)) {
      if (ref.getNode().getSourceFileName().equals("file2.js")) {
        return;
      }
    }
    Assert.fail("Did not find references in file2.js of symbol " + symbol);
  }

  @Test
  public void testGoogRequiredSymbolsConnectedToDefinitions_moduleDefaultExportClass() {
    verifySymbolReferencedInSecondFile(
        lines("goog.module('some.Foo');", "class Foo {}", "exports = Foo;"),
        lines("goog.module('some.bar');", "const Foo = goog.require('some.Foo');"),
        "Foo");
  }

  @Test
  public void testGoogRequiredSymbolsConnectedToDefinitions_moduleDefaultExportConstant() {
    verifySymbolReferencedInSecondFile(
        lines("goog.module('some.constant');", "const constant = 42;", "exports = constant;"),
        lines("goog.module('some.bar');", "const constant = goog.require('some.constant');"),
        // This is a tricky one. `const constant` require from the second file is a reference to the
        // `exports` symbol in the first file.
        "exports");
  }

  @Test
  public void testGoogRequiredSymbolsConnectedToDefinitions_moduleIndividualExports() {
    verifySymbolReferencedInSecondFile(
        lines("goog.module('some.foo');", "exports.one = 1;"),
        lines("goog.module('some.bar');", "const {one} = goog.require('some.foo');"),
        "one");
  }

  @Test
  public void testGoogRequiredSymbolsConnectedToDefinitions_requireType() {
    SymbolTable table =
        createSymbolTableFromManySources(
            lines("goog.module('some.bar');", "const {one} = goog.requireType('some.foo');"),
            lines("goog.module('some.foo');", "exports.one = 1;"));
    Symbol symbol =
        table.getAllSymbols().stream()
            .filter((s) -> s.getSourceFileName().equals("file2.js") && s.getName().equals("one"))
            .findFirst()
            .get();
    for (Reference ref : table.getReferences(symbol)) {
      if (ref.getNode().getSourceFileName().equals("file1.js")) {
        return;
      }
    }
    Assert.fail("Did not find references in file2.js of symbol " + symbol);
  }

  @Test
  public void testGoogRequiredSymbolsConnectedToDefinitions_provideDefaultExportClass() {
    verifySymbolReferencedInSecondFile(
        lines("goog.provide('some.Foo');", "some.Foo = class {}"),
        lines("goog.module('some.bar');", "const Foo = goog.require('some.Foo');"),
        "some.Foo");
  }

  @Test
  public void testGoogRequiredSymbolsConnectedToDefinitions_provideDefaultExportPrimivite() {
    verifySymbolReferencedInSecondFile(
        lines("goog.provide('some.constant');", "some.constant = 123;"),
        lines("goog.module('some.bar');", "const constant = goog.require('some.constant');"),
        "some.constant");
  }

  @Test
  public void testNestedGoogProvides() {
    SymbolTable table =
        createSymbolTableFromManySources(
            lines(
                "goog.provide('a.b.c.d');",
                "goog.provide('a.b.c.d.Foo');",
                "goog.provide('a.b.c.d.Foo.Bar');",
                "a.b.c.d.Foo = class {};",
                "a.b.c.d.Foo.Bar = class {};"));
    assertThat(getGlobalVar(table, "a.b.c.d.Foo")).isNotNull();
    assertThat(getGlobalVar(table, "a.b.c.d.Foo.Bar")).isNotNull();
  }

  @Test
  public void testGoogRequiredSymbolsConnectedToDefinitions_provideIndividualExports() {
    verifySymbolReferencedInSecondFile(
        lines("goog.provide('some.foo');", "some.foo.one = 1;"),
        lines("goog.module('some.bar');", "const {one} = goog.require('some.foo');"),
        "one");
  }

  @Test
  public void testGoogRequiredSymbolsConnectedToDefinitions_property_moduleImportsModule() {
    verifySymbolReferencedInSecondFile(
        lines("goog.module('some.foo');", "exports.one = 1;"),
        lines("goog.module('some.bar');", "const foo = goog.require('some.foo');", "foo.one;"),
        "one");
  }

  @Test
  public void testGoogRequiredSymbolsConnectedToDefinitions_moduleEarlyLexicographically() {
    // Tests bug where imported module is named 'a' which is lexigraphically earlier than 'exports'
    // so 'a' becomes the symbol on which exported module properties are defined. The issue that
    // both 'a' and 'exports' have the same object type and we don't know which one is the true
    // object that declares it. So we just treat 'exports' as special name.
    verifySymbolReferencedInSecondFile(
        lines("goog.module('some.foo');", "const one = 1", "exports.one = one;"),
        lines("goog.module('some.bar');", "const a = goog.require('some.foo');", "a.one;"),
        "one");
  }

  @Test
  public void testGoogRequiredSymbolsConnectedToDefinitions_property_moduleImportsProvide() {
    verifySymbolReferencedInSecondFile(
        lines("goog.provide('some.foo');", "some.foo.one = 1;"),
        lines("goog.module('some.bar');", "const foo = goog.require('some.foo');", "foo.one;"),
        "one");
  }

  @Test
  public void testGoogRequiredSymbolsConnectedToDefinitions_property_provideImportsProvide() {
    verifySymbolReferencedInSecondFile(
        lines("goog.provide('some.foo');", "some.foo.one = 1;"),
        lines("goog.provide('some.bar');", "goog.require('some.foo');", "some.foo.one;"),
        "one");
  }

  @Test
  public void testGoogRequiredSymbolsConnectedToDefinitions_class() {
    verifySymbolReferencedInSecondFile(
        lines("goog.module('some.foo');", "exports.Foo = class { doFoo() {} };"),
        lines(
            "goog.module('some.bar');",
            "const {Foo} = goog.require('some.foo');",
            "new Foo().doFoo();"),
        "doFoo");
  }

  @Test
  public void testGoogRequiredSymbolsConnectedToDefinitions_typedef_module() {
    verifySymbolReferencedInSecondFile(
        lines("goog.module('some.foo');", "/** @typedef {string} */ exports.StringType;"),
        lines(
            "goog.module('some.bar');",
            "const foo = goog.require('some.foo');",
            "const /** !foo.StringType */ k = '123';"),
        "StringType");
  }

  @Test
  public void testGoogRequiredSymbolsConnectedToDefinitions_typedef_provide() {
    verifySymbolReferencedInSecondFile(
        lines("goog.provide('some.foo');", "/** @typedef {string} */ some.foo.StringType;"),
        lines(
            "goog.module('some.bar');",
            "const foo = goog.require('some.foo');",
            "const /** !foo.StringType */ k = '123';"),
        "StringType");
  }

  @Test
  public void testClassPropertiesDisableModuleRewriting() {
    SymbolTable table =
        createSymbolTableFromManySources(
            lines(
                "goog.module('foo');",
                "class Person {",
                "  constructor() {",
                "    /** @type {string} */",
                "    this.lastName;",
                "  }",
                "}",
                "exports = {Person};"),
            lines(
                "goog.module('bar');",
                "const {Person} = goog.require('foo');",
                "let /** !Person */ p;",
                "p.lastName;"));
    Symbol lastName =
        table.getAllSymbols().stream()
            .filter((s) -> s.getName().equals("lastName"))
            .findFirst()
            .get();
    assertThat(table.getReferences(lastName)).hasSize(2);
  }

  @Test
  public void testEnums() {
    SymbolTable table =
        createSymbolTableFromManySources(
            lines(
                "goog.module('foo');",
                "/** @enum {number} */",
                "const Color = {RED: 1};",
                "exports.Color = Color;",
                "Color.RED;"),
            lines("goog.module('bar');", "const foo = goog.require('foo');", "foo.Color.RED;"),
            lines("goog.module('baz');", "const {Color} = goog.require('foo');", "Color.RED;"));
    Symbol red =
        table.getAllSymbols().stream().filter((s) -> s.getName().equals("RED")).findFirst().get();
    // Make sure that RED belongs to the scope of Color that is defined in the first file and not
    // third. Because the third file also has "Color" enum that has the same type.
    assertThat(table.getScope(red).getSymbolForScope().getSourceFileName()).isEqualTo("file1.js");
    assertThat(table.getReferences(red)).hasSize(4);
  }

  @Test
  public void testEnumsWithDirectExport() {
    SymbolTable table =
        createSymbolTableFromManySources(
            lines(
                "goog.module('foo');",
                "/** @enum {number} */",
                "exports.Color = {RED: 1}; // exports.Color = Color;"),
            lines("goog.module('bar');", "const foo = goog.require('foo');", "foo.Color.RED;"),
            lines("goog.module('baz');", "const {Color} = goog.require('foo');", "Color.RED;"));
    Symbol red =
        table.getAllSymbols().stream().filter((s) -> s.getName().equals("RED")).findFirst().get();
    assertThat(table.getScope(red).getSymbolForScope().getSourceFileName()).isEqualTo("file1.js");
    assertThat(table.getReferences(red)).hasSize(3);
  }

  @Test
  public void testEnumsProvidedAsNamespace() {
    SymbolTable table =
        createSymbolTableFromManySources(
            lines(
                "goog.provide('foo.bar.Color');",
                "/** @enum {number} */",
                "foo.bar.Color = {RED: 1};",
                "const /** !foo.bar.Color */ color = ",
                "  foo.bar.Color.RED;"));
    Symbol red =
        table.getAllSymbols().stream().filter((s) -> s.getName().equals("RED")).findFirst().get();
    assertThat(table.getScope(red).getSymbolForScope().getSourceFileName()).isEqualTo("file1.js");
    assertThat(table.getReferences(red)).hasSize(2);
    assertThat(table.getReferences(getGlobalVar(table, "foo.bar.Color"))).hasSize(3);
  }

  @Test
  public void testEnumsProvidedAsMemberOfNamespace() {
    SymbolTable table =
        createSymbolTableFromManySources(
            lines(
                "goog.provide('foo.bar');",
                "/** @enum {number} */",
                "foo.bar.Color = {RED: 1};",
                "const /** !foo.bar.Color */ color = ",
                "  foo.bar.Color.RED;"));
    Symbol red =
        table.getAllSymbols().stream().filter((s) -> s.getName().equals("RED")).findFirst().get();
    // Make sure that RED belongs to the scope of Color that is defined in the first file and not
    // third. Because the third file also has "Color" enum that has the same type.
    assertThat(table.getScope(red).getSymbolForScope().getSourceFileName()).isEqualTo("file1.js");
    assertThat(table.getReferences(red)).hasSize(2);
    Symbol colorEnum =
        table.getAllSymbols().stream().filter((s) -> s.getName().equals("Color")).findFirst().get();
    assertThat(table.getReferences(colorEnum)).hasSize(3);
  }

  @Test
  public void testGlobalVarInExterns() {
    SymbolTable table =
        createSymbolTable("customExternFn(1);", "function customExternFn(customExternArg) {}");
    Symbol fn = getGlobalVar(table, "customExternFn");
    ImmutableList<Reference> refs = table.getReferenceList(fn);
    assertThat(refs).hasSize(3);

    SymbolScope scope = table.getEnclosingScope(refs.get(0).getNode());
    assertThat(scope.isGlobalScope()).isTrue();
    assertThat(table.getSymbolForScope(scope).getName()).isEqualTo(SymbolTable.GLOBAL_THIS);
  }

  @Test
  public void testLocalVarInExterns() {
    SymbolTable table = createSymbolTable("", "function customExternFn(customExternArg) {}");
    Symbol arg = getLocalVar(table, "customExternArg");
    ImmutableList<Reference> refs = table.getReferenceList(arg);
    assertThat(refs).hasSize(1);

    Symbol fn = getGlobalVar(table, "customExternFn");
    SymbolScope scope = table.getEnclosingScope(refs.get(0).getNode());
    assertThat(scope.isGlobalScope()).isFalse();
    assertThat(table.getSymbolForScope(scope)).isEqualTo(fn);
  }

  @Test
  public void testSymbolsForType() {
    SymbolTable table =
        createSymbolTableWithDefaultExterns(
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
    assertThat(table.getSymbolDeclaredBy(foo.getType().toMaybeFunctionType())).isEqualTo(foo);
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

    assertThat(getLocalVar(table, "this.prop")).isNull();
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
    assertThat(field).isNull();
  }

  @Test
  public void testPrototypeReferences() {
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @constructor */ function DomHelper() {}",
                "DomHelper.prototype.method = function() {};"));
    Symbol prototype = getGlobalVar(table, "DomHelper.prototype");
    assertThat(prototype).isNotNull();

    ImmutableList<Reference> refs = table.getReferenceList(prototype);

    // One of the refs is implicit in the declaration of the function.
    assertWithMessage(refs.toString()).that(refs).hasSize(2);
  }

  @Test
  public void testPrototypeReferences2() {
    SymbolTable table =
        createSymbolTable(
            "/** @constructor */" + "function Snork() {}" + "Snork.prototype.baz = 3;");
    Symbol prototype = getGlobalVar(table, "Snork.prototype");
    assertThat(prototype).isNotNull();

    ImmutableList<Reference> refs = table.getReferenceList(prototype);
    assertThat(refs).hasSize(2);
  }

  @Test
  public void testPrototypeReferences3() {
    SymbolTable table = createSymbolTable("/** @constructor */ function Foo() {}");
    Symbol fooPrototype = getGlobalVar(table, "Foo.prototype");
    assertThat(fooPrototype).isNotNull();

    ImmutableList<Reference> refs = table.getReferenceList(fooPrototype);
    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getNode().getToken()).isEqualTo(Token.NAME);

    // Make sure that the ctor and its prototype are declared at the
    // same node.
    assertThat(table.getReferenceList(getGlobalVar(table, "Foo")).get(0).getNode())
        .isEqualTo(refs.get(0).getNode());
  }

  @Test
  public void testPrototypeReferences4() {
    SymbolTable table =
        createSymbolTable(
            lines("/** @constructor */ function Foo() {}", "Foo.prototype = {bar: 3}"));
    Symbol fooPrototype = getGlobalVar(table, "Foo.prototype");
    assertThat(fooPrototype).isNotNull();

    ImmutableList<Reference> refs = ImmutableList.copyOf(table.getReferences(fooPrototype));
    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getNode().getToken()).isEqualTo(Token.GETPROP);
    assertThat(refs.get(0).getNode().getQualifiedName()).isEqualTo("Foo.prototype");
  }

  @Test
  public void testPrototypeReferences5() {
    SymbolTable table =
        createSymbolTable("var goog = {}; /** @constructor */ goog.Foo = function() {};");
    Symbol fooPrototype = getGlobalVar(table, "goog.Foo.prototype");
    assertThat(fooPrototype).isNotNull();

    ImmutableList<Reference> refs = table.getReferenceList(fooPrototype);
    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getNode().getToken()).isEqualTo(Token.GETPROP);

    // Make sure that the ctor and its prototype are declared at the
    // same node.
    assertThat(table.getReferenceList(getGlobalVar(table, "goog.Foo")).get(0).getNode())
        .isEqualTo(refs.get(0).getNode());
  }

  @Test
  public void testPrototypeReferences_es6Class() {
    SymbolTable table = createSymbolTable(lines("class DomHelper { method() {} }"));
    Symbol prototype = getGlobalVar(table, "DomHelper.prototype");
    assertThat(prototype).isNotNull();
    ImmutableList<Reference> refs = table.getReferenceList(prototype);

    // The class declaration creates an implicit .prototype reference.
    assertWithMessage(refs.toString()).that(refs).hasSize(1);
    assertNode(refs.get(0).getNode().getParent()).hasToken(Token.CLASS);
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
    assertThat(foo).isNotNull();

    ImmutableList<Reference> refs = table.getReferenceList(foo);
    assertThat(refs).hasSize(5);

    assertThat(refs.get(0).getNode().getLineno()).isEqualTo(1);
    assertThat(refs.get(0).getNode().getCharno()).isEqualTo(29);
    assertThat(refs.get(0).getNode().getLength()).isEqualTo(3);

    assertThat(refs.get(1).getNode().getLineno()).isEqualTo(2);
    assertThat(refs.get(1).getNode().getCharno()).isEqualTo(11);

    assertThat(refs.get(2).getNode().getLineno()).isEqualTo(3);
    assertThat(refs.get(2).getNode().getCharno()).isEqualTo(12);

    assertThat(refs.get(3).getNode().getLineno()).isEqualTo(4);
    assertThat(refs.get(3).getNode().getCharno()).isEqualTo(25);

    assertThat(refs.get(4).getNode().getLineno()).isEqualTo(7);
    assertThat(refs.get(4).getNode().getCharno()).isEqualTo(13);
  }

  @Test
  public void testReferencesInJSDocType2() {
    SymbolTable table =
        createSymbolTableWithDefaultExterns("/** @param {string} x */ function f(x) {}");
    Symbol str = getGlobalVar(table, "String");
    assertThat(str).isNotNull();

    ImmutableList<Reference> refs = table.getReferenceList(str);

    // We're going to pick up a lot of references from the externs,
    // so it's not meaningful to check the number of references.
    // We really want to make sure that all the references are in the externs,
    // except the last one.
    assertThat(refs.size()).isGreaterThan(1);

    int last = refs.size() - 1;
    for (int i = 0; i < refs.size(); i++) {
      Reference ref = refs.get(i);
      assertThat(ref.getNode().isFromExterns()).isEqualTo(i != last);
      if (!ref.getNode().isFromExterns()) {
        assertThat(ref.getNode().getSourceFileName()).isEqualTo("in1");
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
    assertThat(foo).isNotNull();

    ImmutableList<Reference> refs = table.getReferenceList(foo);
    assertThat(refs).hasSize(5);

    assertThat(refs.get(0).getNode().getLineno()).isEqualTo(2);
    assertThat(refs.get(0).getNode().getCharno()).isEqualTo(25);
    assertThat(refs.get(0).getNode().getLength()).isEqualTo(3);

    assertThat(refs.get(1).getNode().getLineno()).isEqualTo(3);
    assertThat(refs.get(1).getNode().getCharno()).isEqualTo(16);

    assertThat(refs.get(2).getNode().getLineno()).isEqualTo(4);
    assertThat(refs.get(2).getNode().getCharno()).isEqualTo(17);

    assertThat(refs.get(3).getNode().getLineno()).isEqualTo(5);
    assertThat(refs.get(3).getNode().getCharno()).isEqualTo(30);

    assertThat(refs.get(4).getNode().getLineno()).isEqualTo(8);
    assertThat(refs.get(4).getNode().getCharno()).isEqualTo(18);
  }

  @Test
  public void testReferencesInJSDocName() {
    String code = "/** @param {Object} x */ function f(x) {}";
    SymbolTable table = createSymbolTable(code);
    Symbol x = getLocalVar(table, "x");
    assertThat(x).isNotNull();

    ImmutableList<Reference> refs = table.getReferenceList(x);
    assertThat(refs).hasSize(2);

    assertThat(refs.get(0).getNode().getCharno()).isEqualTo(code.indexOf("x) {"));
    assertThat(refs.get(1).getNode().getCharno()).isEqualTo(code.indexOf("x */"));
    assertThat(refs.get(0).getNode().getSourceFileName()).isEqualTo("in1");
  }

  @Test
  public void testLocalQualifiedNamesInLocalScopes() {
    SymbolTable table = createSymbolTable("function f() { var x = {}; x.number = 3; }");
    Symbol xNumber = getLocalVar(table, "x.number");
    assertThat(xNumber).isNotNull();
    assertThat(table.getScope(xNumber).isGlobalScope()).isFalse();

    assertThat(xNumber.getType().toString()).isEqualTo("number");
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
    ImmutableList<Reference> refs = table.getReferenceList(method);
    assertThat(refs).hasSize(2);

    // Note that the declaration should show up second.
    assertThat(method.getDeclaration().getNode().getLineno()).isEqualTo(7);
    assertThat(refs.get(1).getNode().getLineno()).isEqualTo(5);
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
    assertThat(bCtor).isNotNull();

    ImmutableList<Reference> bRefs = table.getReferenceList(bCtor);
    assertThat(bRefs).hasSize(2);
    assertThat(bCtor.getDeclaration().getNode().getLineno()).isEqualTo(11);

    Symbol cCtor = getGlobalVar(table, "goog.C.prototype.constructor");
    assertThat(cCtor).isNotNull();

    ImmutableList<Reference> cRefs = table.getReferenceList(cCtor);
    assertThat(cRefs).hasSize(2);
    assertThat(cCtor.getDeclaration().getNode().getLineno()).isEqualTo(26);
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
    assertThat(foo).isNotNull();

    JSDocInfo info = foo.getJSDocInfo();
    assertThat(info).isNotNull();
    assertThat(info.isConstructor()).isTrue();
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
    assertThat(getGlobalVar(table, "F.prototype.field1")).isNull();

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
    assertThat(getGlobalVar(table, "F.prototype.field1")).isNull();
    assertThat(getGlobalVar(table, "F.prototype.method1")).isNull();

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
    assertThat(bad).isNull();

    Symbol good = getGlobalVar(table, "a.b.BaseClass.prototype.doSomething");
    assertThat(good).isNotNull();

    ImmutableList<Reference> refs = table.getReferenceList(good);
    assertThat(refs).hasSize(2);
    assertThat(refs.get(1).getNode().getQualifiedName())
        .isEqualTo("a.b.DerivedClass.superClass_.doSomething");
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
    assertThat(eventType).isNotNull();
    assertThat(eventType.getType().isEnumType()).isTrue();

    Symbol toggle = getGlobalVar(table, "goog.ui.Zippy.EventType.TOGGLE");
    assertThat(toggle).isNotNull();
  }

  @Test
  public void testMethodInAnonObject1() {
    SymbolTable table = createSymbolTable("var a = {}; a.b = {}; a.b.c = function() {};");
    Symbol a = getGlobalVar(table, "a");
    Symbol ab = getGlobalVar(table, "a.b");
    Symbol abc = getGlobalVar(table, "a.b.c");

    assertThat(abc).isNotNull();
    assertThat(table.getReferenceList(abc)).hasSize(1);

    assertThat(a.getType().toString()).isEqualTo("{b: {c: function(): undefined}}");
    assertThat(ab.getType().toString()).isEqualTo("{c: function(): undefined}");
    assertThat(abc.getType().toString()).isEqualTo("function(): undefined");
  }

  @Test
  public void testMethodInAnonObject2() {
    SymbolTable table = createSymbolTable("var a = {b: {c: function() {}}};");
    Symbol a = getGlobalVar(table, "a");
    Symbol ab = getGlobalVar(table, "a.b");
    Symbol abc = getGlobalVar(table, "a.b.c");

    assertThat(abc).isNotNull();
    assertThat(table.getReferenceList(abc)).hasSize(1);

    assertThat(a.getType().toString()).isEqualTo("{b: {c: function(): undefined}}");
    assertThat(ab.getType().toString()).isEqualTo("{c: function(): undefined}");
    assertThat(abc.getType().toString()).isEqualTo("function(): undefined");
  }

  @Test
  public void testJSDocOnlySymbol() {
    SymbolTable table =
        createSymbolTable(lines("/**", " * @param {number} x", " * @param y", " */", "var a;"));
    Symbol x = getDocVar(table, "x");
    assertThat(x).isNotNull();
    assertThat(x.getType().toString()).isEqualTo("number");
    assertThat(table.getReferenceList(x)).hasSize(1);

    Symbol y = getDocVar(table, "y");
    assertThat(x).isNotNull();
    assertThat(y.getType()).isNull();
    assertThat(table.getReferenceList(y)).hasSize(1);
  }

  @Test
  public void testNamespaceDefinitionOrder() {
    // Sometimes, weird things can happen where the files appear in
    // a strange order. We need to make sure we're robust against this.
    SymbolTable table =
        createSymbolTable(
            lines(
                "/** @constructor */ goog.dom.Foo = function() {};",
                "/** @const */ goog.dom = {};",
                "/** @const */ var goog = {};"));

    Symbol goog = getGlobalVar(table, "goog");
    Symbol dom = getGlobalVar(table, "goog.dom");
    Symbol foo = getGlobalVar(table, "goog.dom.Foo");

    assertThat(goog).isNotNull();
    assertThat(dom).isNotNull();
    assertThat(foo).isNotNull();

    assertThat(goog.getPropertyScope().getSlot("dom")).isEqualTo(dom);
    assertThat(dom.getPropertyScope().getSlot("Foo")).isEqualTo(foo);
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

    assertThat(foo).isNotNull();
    assertThat(fooAlias).isNotNull();
    assertThat(bar).isNotNull();
    assertThat(baz).isNotNull();
    assertThat(baz).isEqualTo(bazAlias);

    Symbol barScope = table.getSymbolForScope(table.getScope(bar));
    assertThat(barScope).isNotNull();

    Symbol bazScope = table.getSymbolForScope(table.getScope(baz));
    assertThat(bazScope).isNotNull();

    Symbol fooPrototype = foo.getPropertyScope().getSlot("prototype");
    assertThat(fooPrototype).isNotNull();

    assertThat(barScope).isEqualTo(fooPrototype);
    assertThat(bazScope).isEqualTo(fooPrototype);
  }

  @Test
  public void testSymbolForScopeOfNatives() {
    SymbolTable table = createSymbolTableWithDefaultExterns("");

    // From the externs.
    Symbol sliceArg = getLocalVar(table, "sliceArg");
    assertThat(sliceArg).isNotNull();

    Symbol scope = table.getSymbolForScope(table.getScope(sliceArg));
    assertThat(scope).isNotNull();
    assertThat(getGlobalVar(table, "String.prototype.slice")).isEqualTo(scope);

    Symbol proto = getGlobalVar(table, "String.prototype");
    assertThat(proto.getDeclaration().getNode().getSourceFileName()).isEqualTo("externs1");
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

    assertThat(getGlobalVar(table, "foo").getVisibility()).isEqualTo(Visibility.PUBLIC);
    assertThat(getGlobalVar(table, "bar").getVisibility()).isEqualTo(Visibility.PROTECTED);
    assertThat(getGlobalVar(table, "baz").getVisibility()).isEqualTo(Visibility.PACKAGE);
    assertThat(getGlobalVar(table, "quux").getVisibility()).isEqualTo(Visibility.PRIVATE);
    assertThat(getGlobalVar(table, "xyzzy").getVisibility()).isEqualTo(Visibility.INHERITED);
    assertThat(getGlobalVar(table, "xyzzy").getJSDocInfo()).isNull();
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
    assertThat(getGlobalVar(table, "foo").getVisibility()).isEqualTo(Visibility.PUBLIC);
    assertThat(getGlobalVar(table, "bar").getVisibility()).isEqualTo(Visibility.PROTECTED);
    assertThat(getGlobalVar(table, "baz").getVisibility()).isEqualTo(Visibility.PACKAGE);
    assertThat(getGlobalVar(table, "quux").getVisibility()).isEqualTo(Visibility.PRIVATE);
    assertThat(getGlobalVar(table, "xyzzy").getVisibility()).isEqualTo(Visibility.PACKAGE);
    assertThat(getGlobalVar(table, "xyzzy").getJSDocInfo()).isNull();
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

    assertThat(getGlobalVar(table, "Foo.prototype.bar").getVisibility())
        .isEqualTo(Visibility.PUBLIC);
    assertThat(getGlobalVar(table, "Foo.prototype.baz").getVisibility())
        .isEqualTo(Visibility.PROTECTED);
    assertThat(getGlobalVar(table, "Foo.prototype.quux").getVisibility())
        .isEqualTo(Visibility.PACKAGE);
    assertThat(getGlobalVar(table, "Foo.prototype.xyzzy").getVisibility())
        .isEqualTo(Visibility.PRIVATE);
    assertThat(getGlobalVar(table, "Foo.prototype.plugh").getVisibility())
        .isEqualTo(Visibility.INHERITED);
    assertThat(getGlobalVar(table, "Foo.prototype.plugh").getJSDocInfo()).isNull();

    assertThat(getGlobalVar(table, "SubFoo.prototype.bar").getVisibility())
        .isEqualTo(Visibility.INHERITED);
    assertThat(getGlobalVar(table, "SubFoo.prototype.baz").getVisibility())
        .isEqualTo(Visibility.INHERITED);
    assertThat(getGlobalVar(table, "SubFoo.prototype.quux").getVisibility())
        .isEqualTo(Visibility.INHERITED);
    assertThat(getGlobalVar(table, "SubFoo.prototype.xyzzy").getVisibility())
        .isEqualTo(Visibility.INHERITED);
    assertThat(getGlobalVar(table, "SubFoo.prototype.plugh").getVisibility())
        .isEqualTo(Visibility.INHERITED);
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

    assertThat(getGlobalVar(table, "Foo.prototype.bar").getVisibility())
        .isEqualTo(Visibility.PUBLIC);
    assertThat(getGlobalVar(table, "Foo.prototype.baz").getVisibility())
        .isEqualTo(Visibility.PROTECTED);
    assertThat(getGlobalVar(table, "Foo.prototype.quux").getVisibility())
        .isEqualTo(Visibility.PACKAGE);
    assertThat(getGlobalVar(table, "Foo.prototype.xyzzy").getVisibility())
        .isEqualTo(Visibility.PRIVATE);
    assertThat(getGlobalVar(table, "Foo.prototype.plugh").getVisibility())
        .isEqualTo(Visibility.PACKAGE);
    assertThat(getGlobalVar(table, "Foo.prototype.plugh").getJSDocInfo()).isNull();

    assertThat(getGlobalVar(table, "SubFoo.prototype.bar").getVisibility())
        .isEqualTo(Visibility.INHERITED);
    assertThat(getGlobalVar(table, "SubFoo.prototype.baz").getVisibility())
        .isEqualTo(Visibility.INHERITED);
    assertThat(getGlobalVar(table, "SubFoo.prototype.quux").getVisibility())
        .isEqualTo(Visibility.INHERITED);
    assertThat(getGlobalVar(table, "SubFoo.prototype.xyzzy").getVisibility())
        .isEqualTo(Visibility.INHERITED);
    assertThat(getGlobalVar(table, "SubFoo.prototype.plugh").getVisibility())
        .isEqualTo(Visibility.INHERITED);
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
    assertThat(employer).isNotNull();
    SymbolScope propertyScope = employer.getPropertyScope();
    assertThat(propertyScope).isNotNull();
    Symbol prototypeOfEmployer = propertyScope.getQualifiedSlot("prototype");
    assertThat(prototypeOfEmployer).isNotNull();

    Symbol employerPrototype = getGlobalVar(table, "Employer.prototype");
    assertThat(employerPrototype).isNotNull();

    new EqualsTester().addEqualityGroup(employerPrototype, prototypeOfEmployer).testEquals();
  }

  @Test
  public void testRestParameter() {
    String input = "function f(...x) {} f(1, 2, 3);";

    SymbolTable table = createSymbolTable(input);

    Symbol f = getGlobalVar(table, "f");
    assertThat(f).isNotNull();

    Symbol x = table.getParameterInFunction(f, "x");
    assertThat(x).isNotNull();
  }

  @Test
  public void testGetEnclosingScope_Global() {
    SymbolTable table = createSymbolTable("const baz = 1;");

    Symbol baz = getGlobalVar(table, "baz");
    SymbolScope bazScope = table.getEnclosingScope(baz.getDeclarationNode());

    assertThat(bazScope).isNotNull();
    assertThat(bazScope.isGlobalScope()).isTrue();
  }

  @Test
  public void testGetEnclosingScope_GlobalNested() {
    SymbolTable table = createSymbolTable("{ const baz = 1; }");

    Symbol baz = getLocalVar(table, "baz");
    SymbolScope bazScope = table.getEnclosingScope(baz.getDeclarationNode());

    assertThat(bazScope).isNotNull();
    assertThat(bazScope.isBlockScope()).isTrue();
    assertThat(bazScope.getParentScope().isGlobalScope()).isTrue();
  }

  @Test
  public void testGetEnclosingScope_FunctionNested() {
    SymbolTable table = createSymbolTable("function foo() { { const baz = 1; } }");

    Symbol baz = getLocalVar(table, "baz");
    SymbolScope bazScope = table.getEnclosingScope(baz.getDeclarationNode());

    assertThat(bazScope).isNotNull();
    assertThat(bazScope.isBlockScope()).isTrue();
    assertThat(bazScope.getParentScope().isBlockScope()).isTrue();
    Node foo = getGlobalVar(table, "foo").getDeclarationNode().getParent();
    assertThat(bazScope.getParentScope().getParentScope().getRootNode()).isEqualTo(foo);
  }

  @Test
  public void testGetEnclosingFunctionScope_GlobalScopeNoNesting() {
    SymbolTable table = createSymbolTable("const baz = 1;");

    Symbol baz = getGlobalVar(table, "baz");
    SymbolScope bazFunctionScope = table.getEnclosingFunctionScope(baz.getDeclarationNode());

    assertThat(bazFunctionScope).isNotNull();
    assertThat(bazFunctionScope.isGlobalScope()).isTrue();
  }

  @Test
  public void testGetEnclosingFunctionScope_GlobalScopeNested() {
    SymbolTable table = createSymbolTable("{ const baz = 1; }");

    Symbol baz = getLocalVar(table, "baz");
    SymbolScope bazFunctionScope = table.getEnclosingFunctionScope(baz.getDeclarationNode());

    assertThat(bazFunctionScope).isNotNull();
    assertThat(bazFunctionScope.isGlobalScope()).isTrue();
  }

  @Test
  public void testGetEnclosingFunctionScope_FunctionNoNestedScopes() {
    SymbolTable table = createSymbolTable("function foo() { const baz = 1; }");

    Symbol baz = getLocalVar(table, "baz");
    SymbolScope bazFunctionScope = table.getEnclosingFunctionScope(baz.getDeclarationNode());

    assertThat(bazFunctionScope).isNotNull();
    Node foo = getGlobalVar(table, "foo").getDeclarationNode().getParent();
    assertThat(bazFunctionScope.getRootNode()).isEqualTo(foo);
  }

  @Test
  public void testGetEnclosingFunctionScope_FunctionNested() {
    SymbolTable table = createSymbolTable("function foo() { { { const baz = 1; } } }");

    Symbol baz = getLocalVar(table, "baz");
    SymbolScope bazFunctionScope = table.getEnclosingFunctionScope(baz.getDeclarationNode());

    assertThat(bazFunctionScope).isNotNull();
    Node foo = getGlobalVar(table, "foo").getDeclarationNode().getParent();
    assertThat(bazFunctionScope.getRootNode()).isEqualTo(foo);
  }

  @Test
  public void testGetEnclosingFunctionScope_FunctionInsideFunction() {
    SymbolTable table = createSymbolTable("function foo() { function baz() {} }");

    Symbol baz = getLocalVar(table, "baz");
    SymbolScope bazFunctionScope = table.getEnclosingFunctionScope(baz.getDeclarationNode());

    assertThat(bazFunctionScope).isNotNull();
    Node foo = getGlobalVar(table, "foo").getDeclarationNode().getParent();
    assertThat(bazFunctionScope.getRootNode()).isEqualTo(foo);
  }

  @Test
  public void testSymbolSuperclassStaticInheritance() {
    // set this option so that typechecking sees untranspiled classes.
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

  @Test
  public void testDuplicatedWindowExternsMerged() {
    // Add window so that it triggers logic that defines all global externs on window.
    // See DeclaredGlobalExternsOnWindow.java pass.
    String externs = lines("/** @externs */", "var window", "/** @type {number} */ var foo;");
    String mainCode = lines("foo = 2;", "window.foo = 1;");
    SymbolTable table = createSymbolTable(mainCode, externs);

    Map<String, Integer> refsPerFile = new HashMap<>();
    for (Reference reference : table.getReferenceList(getGlobalVar(table, "foo"))) {
      if (!reference.getNode().isIndexable()) {
        continue;
      }
      String file = reference.getSourceFile().getName();
      refsPerFile.put(file, refsPerFile.getOrDefault(file, 0) + 1);
    }
    assertThat(refsPerFile).containsExactly("in1", 2, "externs1", 1);
  }

  @Test
  public void testDuplicatedWindowExternsMergedWithWindowPrototype() {
    // Add window so that it triggers logic that defines all global externs on window.
    // See DeclaredGlobalExternsOnWindow.java pass.
    // Also add Window class as it affects symbols (e.g. window.foo is set on Window.prototype.foo
    // instead).
    String externs =
        lines(
            "/** @externs */",
            "/** @constructor */ function Window() {}",
            "/** @type {!Window} */ var window;",
            "/** @type {number} */ var foo;");
    String mainCode = lines("foo = 2;", "window.foo = 1;");
    SymbolTable table = createSymbolTable(mainCode, externs);

    Map<String, Integer> refsPerFile = new HashMap<>();
    for (Reference reference : table.getReferenceList(getGlobalVar(table, "foo"))) {
      String file = reference.getSourceFile().getName();
      refsPerFile.put(file, refsPerFile.getOrDefault(file, 0) + 1);
    }
    assertThat(refsPerFile).containsExactly("in1", 2, "externs1", 1);
  }

  private void assertSymmetricOrdering(Ordering<Symbol> ordering, Symbol first, Symbol second) {
    assertThat(ordering.compare(first, first)).isEqualTo(0);
    assertThat(ordering.compare(second, second)).isEqualTo(0);
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

  private SymbolTable createSymbolTableWithDefaultExterns(String input) {
    return createSymbolTable(input, CompilerTypeTestCase.DEFAULT_EXTERNS);
  }

  private SymbolTable createSymbolTable(String input) {
    return createSymbolTable(input, "");
  }

  private SymbolTable createSymbolTable(String input, String externsCode) {
    ImmutableList<SourceFile> inputs = ImmutableList.of(SourceFile.fromCode("in1", input));
    ImmutableList<SourceFile> externs =
        ImmutableList.of(SourceFile.fromCode("externs1", externsCode));

    Compiler compiler = new Compiler(new BlackHoleErrorManager());
    compiler.compile(externs, inputs, options);
    return assertSymbolTableValid(compiler.buildKnownSymbolTable());
  }

  private SymbolTable createSymbolTableFromManySources(String... inputs) {
    ImmutableList.Builder<SourceFile> sources = ImmutableList.builder();
    for (int i = 0; i < inputs.length; i++) {
      sources.add(SourceFile.fromCode("file" + (i + 1) + ".js", inputs[i]));
    }
    ImmutableList<SourceFile> externs = ImmutableList.of();

    Compiler compiler = new Compiler(new BlackHoleErrorManager());
    compiler.compile(externs, sources.build(), options);
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
      assertThat(table.getScope(sym).getQualifiedSlot(sym.getName())).isEqualTo(sym);

      for (Reference ref : table.getReferences(sym)) {
        // Make sure that the symbol and reference are mutually linked.
        assertThat(ref.getSymbol()).isEqualTo(sym);
      }

      Symbol symbolForScope = table.getSymbolForScope(table.getScope(sym));
      if (symbolForScope != null) {
        assertWithMessage("The %s has a scope that shouldn't exist; a zombie scope.", sym)
            .that(allSymbols)
            .contains(symbolForScope);
      }
    }

    // Make sure that the global "this" is declared at the first input root.
    Symbol global = getGlobalVar(table, SymbolTable.GLOBAL_THIS);
    assertThat(global).isNotNull();
    assertThat(global.getDeclaration()).isNotNull();
    assertThat(global.getDeclaration().getNode().getToken()).isEqualTo(Token.SCRIPT);

    ImmutableList<Reference> globalRefs = table.getReferenceList(global);

    // The main reference list should never contain the synthetic declaration
    // for the global root.
    assertThat(globalRefs).doesNotContain(global.getDeclaration());

    return table;
  }
}
