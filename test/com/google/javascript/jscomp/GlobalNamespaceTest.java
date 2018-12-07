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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.CompilerTypeTestCase.lines;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.GlobalNamespace.AstChange;
import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Name.Inlinability;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link GlobalNamespace}.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
@RunWith(JUnit4.class)
public final class GlobalNamespaceTest {

  @Nullable private Compiler lastCompiler = null;

  @Test
  public void testRemoveDeclaration1() {
    Name n = Name.createForTesting("a");
    Ref set1 = createNodelessRef(Ref.Type.SET_FROM_GLOBAL);
    Ref set2 = createNodelessRef(Ref.Type.SET_FROM_GLOBAL);

    n.addRef(set1);
    n.addRef(set2);

    assertThat(n.getDeclaration()).isEqualTo(set1);
    assertThat(n.getGlobalSets()).isEqualTo(2);
    assertThat(n.getRefs()).hasSize(2);

    n.removeRef(set1);

    assertThat(n.getDeclaration()).isEqualTo(set2);
    assertThat(n.getGlobalSets()).isEqualTo(1);
    assertThat(n.getRefs()).hasSize(1);
  }

  @Test
  public void testRemoveDeclaration2() {
    Name n = Name.createForTesting("a");
    Ref set1 = createNodelessRef(Ref.Type.SET_FROM_GLOBAL);
    Ref set2 = createNodelessRef(Ref.Type.SET_FROM_LOCAL);

    n.addRef(set1);
    n.addRef(set2);

    assertThat(n.getDeclaration()).isEqualTo(set1);
    assertThat(n.getGlobalSets()).isEqualTo(1);
    assertThat(n.getLocalSets()).isEqualTo(1);
    assertThat(n.getRefs()).hasSize(2);

    n.removeRef(set1);

    assertThat(n.getDeclaration()).isNull();
    assertThat(n.getGlobalSets()).isEqualTo(0);
  }

  @Test
  public void testSimpleSubclassingRefCollection() {
    GlobalNamespace namespace =
        parse(
            lines(
                "class Superclass {}", //
                "class Subclass extends Superclass {}"));

    Name superclass = namespace.getOwnSlot("Superclass");
    assertThat(superclass.getRefs()).hasSize(2);
    assertThat(superclass.getSubclassingGets()).isEqualTo(1);
  }

  @Test
  public void testStaticInheritedReferencesDontReferToSuperclass() {
    GlobalNamespace namespace =
        parse(
            lines(
                "class Superclass {",
                "  static staticMethod() {}",
                "}",
                "class Subclass extends Superclass {}",
                "Subclass.staticMethod();"));

    Name superclassStaticMethod = namespace.getOwnSlot("Superclass.staticMethod");
    assertThat(superclassStaticMethod.getRefs()).hasSize(1);
    assertThat(superclassStaticMethod.getDeclaration()).isNotNull();

    Name subclassStaticMethod = namespace.getOwnSlot("Subclass.staticMethod");
    assertThat(subclassStaticMethod.getRefs()).hasSize(1);
    assertThat(subclassStaticMethod.getDeclaration()).isNull();
  }

  @Test
  public void testScanFromNodeDoesntDuplicateVarDeclarationSets() {
    GlobalNamespace namespace = parse("class Foo {} const Bar = Foo; const Baz = Bar;");

    Name foo = namespace.getOwnSlot("Foo");
    assertThat(foo.getAliasingGets()).isEqualTo(1);
    Name baz = namespace.getOwnSlot("Baz");
    assertThat(baz.getGlobalSets()).isEqualTo(1);

    // Replace "const Baz = Bar" with "const Baz = Foo"
    Node root = lastCompiler.getJsRoot();
    Node barRef = root.getFirstChild().getLastChild().getFirstFirstChild();
    checkState(barRef.getString().equals("Bar"), barRef);
    Node fooName = IR.name("Foo");
    barRef.replaceWith(fooName);

    // Rescan the new nodes
    namespace.scanNewNodes(ImmutableSet.of(createGlobalAstChangeForNode(root, fooName)));

    assertThat(foo.getAliasingGets()).isEqualTo(2);
    // A bug in scanFromNode used to make this `2`
    assertThat(baz.getGlobalSets()).isEqualTo(1);
  }

  @Test
  public void testScanFromNodeAddsReferenceToParentGetprop() {
    GlobalNamespace namespace = parse("const x = {bar: 0}; const y = x; const baz = y.bar;");

    Name xbar = namespace.getOwnSlot("x.bar");
    assertThat(xbar.getAliasingGets()).isEqualTo(0);
    Name baz = namespace.getOwnSlot("baz");
    assertThat(baz.getGlobalSets()).isEqualTo(1);

    // Replace "const baz = y.bar" with "const baz = x.bar"
    Node root = lastCompiler.getJsRoot();
    Node yRef = root.getFirstChild().getLastChild().getFirstFirstChild().getFirstChild();
    checkState(yRef.getString().equals("y"), yRef);
    Node xName = IR.name("x");
    yRef.replaceWith(xName);

    // Rescan the new nodes
    namespace.scanNewNodes(ImmutableSet.of(createGlobalAstChangeForNode(root, xName)));

    assertThat(xbar.getAliasingGets()).isEqualTo(1);
    assertThat(baz.getGlobalSets()).isEqualTo(1);
  }

  private AstChange createGlobalAstChangeForNode(Node jsRoot, Node n) {
    // This only creates a global scope, so don't use this with local nodes
    Scope globalScope = new Es6SyntacticScopeCreator(lastCompiler).createScope(jsRoot, null);
    // I don't know if lastCompiler.getModules() is correct but it works
    return new AstChange(Iterables.getFirst(lastCompiler.getModules(), null), globalScope, n);
  }

  @Test
  public void testCollapsing_forEscapedConstructor() {
    GlobalNamespace namespace =
        parse(lines("/** @constructor */", "function Bar() {}", "use(Bar);"));

    Name bar = namespace.getSlot("Bar");
    assertThat(bar.canCollapse()).isTrue(); // trivially true, already collapsed
    // we collapse properties of Bar even though it's escaped, intentionally unsafe.
    // this is mostly to support minification for goog.provide namespaces containing @constructors
    assertThat(bar.canCollapseUnannotatedChildNames()).isTrue();
  }

  @Test
  public void testInlinability_forAliasingPropertyOnEscapedConstructor() {
    GlobalNamespace namespace =
        parse(
            lines(
                "var prop = 1;",
                "/** @constructor */",
                "var Foo = function() {}",
                "",
                "Foo.prop = prop;",
                "",
                "/** @constructor */",
                "function Bar() {}",
                "Bar.aliasOfFoo = Foo;", // alias Foo
                "use(Bar);", // uninlinable alias of Bar
                "const BarAlias = Bar;", // inlinable alias of Bar
                "alert(Bar.aliasOfFoo.prop);",
                "alert(BarAlias.aliasOfFoo.prop);"));

    Name barAliasOfFoo = namespace.getSlot("Bar.aliasOfFoo");
    Inlinability barAliasInlinability = barAliasOfFoo.calculateInlinability();

    // We should convert references to `Bar.aliasOfFoo.prop` to become `Foo.prop`
    // because...
    assertThat(barAliasInlinability.shouldInlineUsages()).isTrue();
    // However, we should not remove the assignment (`Bar.aliasOfFoo = Foo`) that creates the alias,
    // because "BarAlias" still needs to be inlined to "Bar", which will create another usage of
    // "Bar.aliasOfFoo" in the last line, We will locate the value to inline Bar.aliasOfFoo
    // again from `Bar.aliasOfFoo = Foo`.
    assertThat(barAliasInlinability.shouldRemoveDeclaration()).isFalse();
  }

  private GlobalNamespace parse(String js) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setSkipNonTranspilationPasses(true);
    compiler.compile(SourceFile.fromCode("ex.js", ""), SourceFile.fromCode("test.js", js), options);
    assertThat(compiler.getErrors()).isEmpty();
    this.lastCompiler = compiler;

    return new GlobalNamespace(compiler, compiler.getRoot());
  }

  private Ref createNodelessRef(Ref.Type type) {
    return Ref.createRefForTesting(type);
  }
}
