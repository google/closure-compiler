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

import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.jscomp.CompilerTestCase.lines;

import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Name.Inlinability;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
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
    compiler.compile(SourceFile.fromCode("ex.js", ""), SourceFile.fromCode("test.js", js), options);
    assertThat(compiler.getErrors()).isEmpty();

    return new GlobalNamespace(compiler, compiler.getRoot());
  }

  private Ref createNodelessRef(Ref.Type type) {
    return Ref.createRefForTesting(type);
  }
}
