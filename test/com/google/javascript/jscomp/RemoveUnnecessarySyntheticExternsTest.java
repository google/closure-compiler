/*
 * Copyright 2021 The Closure Compiler Authors.
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

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.LinkedHashSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RemoveUnnecessarySyntheticExternsTest extends CompilerTestCase {

  // use this set to simulate an earlier compiler pass declaring a synthetic extern
  private LinkedHashSet<Node> syntheticExternsToAdd = null;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    enableMultistageCompilation();
    this.syntheticExternsToAdd = new LinkedHashSet<>();
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    return (Node externs, Node js) -> {
      Node syntheticRoot = compiler.getSynthesizedExternsInput().getAstRoot(compiler);
      for (Node declaration : this.syntheticExternsToAdd) {
        syntheticRoot.addChildToBack(declaration.cloneTree());
      }
      new RemoveUnnecessarySyntheticExterns(compiler).process(externs, js);
    };
  }

  @Test
  public void doesntChangeSyntheticExternThatIsNotDeclaredInCode() {
    this.syntheticExternsToAdd.add(createUnfulfilledDeclaration("x"));
    this.syntheticExternsToAdd.add(createUnfulfilledDeclaration("y"));

    testExternChanges(srcs("x;"), expected("var x; var y;"));
  }

  @Test
  public void doesntRemoveSyntheticExternIfShadowedInCode() {
    this.syntheticExternsToAdd.add(createUnfulfilledDeclaration("x"));

    testExternChanges(srcs("function fn() { var x; }"), expected("var x;"));
    testExternChanges(srcs("{ let x; }"), expected("var x;"));
    testExternChanges(srcs("{ const x = 0; }"), expected("var x;"));
    testExternChanges(srcs("{ class x {} }"), expected("var x;"));
  }

  @Test
  public void removesSyntheticExternDeclaredInCode() {
    this.syntheticExternsToAdd.add(createUnfulfilledDeclaration("x"));
    this.syntheticExternsToAdd.add(createUnfulfilledDeclaration("y"));

    testExternChanges(srcs("var x;"), expected("var y;"));
    testExternChanges(srcs("{ var x; }"), expected("var y;"));
    testExternChanges(srcs("let x;"), expected("var y;"));
    testExternChanges(srcs("const x = 0;"), expected("var y;"));
    testExternChanges(srcs("function x() {}"), expected("var y;"));
    testExternChanges(srcs("class x {}"), expected("var y;"));
  }

  @Test
  public void removesSyntheticExternDeclaredInOtherExterns() {
    this.syntheticExternsToAdd.add(createUnfulfilledDeclaration("x"));
    this.syntheticExternsToAdd.add(createUnfulfilledDeclaration("y"));

    testExternChanges(externs("var x;"), srcs(""), expected("var y;", "var x;"));
    testExternChanges(externs("var x;"), srcs("var y;"), expected("var x;"));
  }

  @Test
  public void removesDistinctSyntheticExternsDeclaredInCode() {
    this.syntheticExternsToAdd.add(createUnfulfilledDeclaration("x"));
    this.syntheticExternsToAdd.add(createUnfulfilledDeclaration("y"));

    testExternChanges(srcs("var x; var y;"), expected(""));
  }

  @Test
  public void removesDuplicateUnfulfilledSyntheticDeclarations() {
    this.syntheticExternsToAdd.add(createUnfulfilledDeclaration("x"));
    this.syntheticExternsToAdd.add(createUnfulfilledDeclaration("y"));
    this.syntheticExternsToAdd.add(createUnfulfilledDeclaration("x"));
    this.syntheticExternsToAdd.add(createUnfulfilledDeclaration("x"));

    testExternChanges(srcs(""), expected("var x; var y;"));
    testExternChanges(srcs("var x;"), expected("var y;"));
  }

  @Test
  public void removesSyntheticExternDeclaredInCode_multipleCodeDeclarations() {
    this.syntheticExternsToAdd.add(createUnfulfilledDeclaration("x"));
    this.syntheticExternsToAdd.add(createUnfulfilledDeclaration("y"));

    testExternChanges(srcs("var x; function x() {}"), expected("var y;"));
  }

  @Test
  public void doesntChangeSyntheticExtern_declaredInCodeNotMarkedUnfulfilled() {
    // add an extern that is not marked as an 'unfulfilled declaration'
    // we assume this extern was added to prevent renaming, not just enforce that all referenced
    // names are declared.
    this.syntheticExternsToAdd.add(IR.var(IR.name("x")));

    testExternChanges(srcs("x;"), expected("var x;"));
  }

  @Test
  public void removesOnlyUnfulfilledSyntheticExterns_ifMixOfFulfilledAndUnfulfilled() {
    // add an extern that is not marked as an 'unfulfilled declaration'
    // we assume this extern was added to actually prevent renaming, and so must not be removed
    // even if a duplicate of a non-synthetic extern.
    this.syntheticExternsToAdd.add(IR.var(IR.name("x")));
    // add a duplicate declaration of 'x', where the second and third only are unfulfilled.
    this.syntheticExternsToAdd.add(createUnfulfilledDeclaration("x"));
    this.syntheticExternsToAdd.add(createUnfulfilledDeclaration("x"));

    testExternChanges(srcs(""), expected("var x;"));
    testExternChanges(srcs("var x;"), expected("var x;"));
  }

  private Node createUnfulfilledDeclaration(String name) {
    // only VAR nodes are allowed to be marked "synthesized unfulfilled" so no need to test other
    // kinds of declarations.
    Node declaration = IR.var(IR.name(name));
    declaration.setIsSynthesizedUnfulfilledNameDeclaration(true);
    return declaration;
  }
}
