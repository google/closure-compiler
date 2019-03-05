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
import static com.google.javascript.rhino.testing.Asserts.assertThrows;
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.GlobalNamespace.AstChange;
import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Name.Inlinability;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import java.util.Collection;
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
  public void firstGlobalAssignmentIsConsideredDeclaration() {
    Name n = Name.createForTesting("a");
    Ref set1 = n.addSingleRefForTesting(Ref.Type.SET_FROM_GLOBAL, 0);
    Ref set2 = n.addSingleRefForTesting(Ref.Type.SET_FROM_GLOBAL, 1);

    assertThat(n.getRefs()).containsExactly(set1, set2).inOrder();

    assertThat(n.getDeclaration()).isEqualTo(set1);
    assertThat(n.getGlobalSets()).isEqualTo(2);

    n.removeRef(set1);

    // declaration moves to next global assignment when first is removed
    assertThat(n.getDeclaration()).isEqualTo(set2);
    assertThat(n.getGlobalSets()).isEqualTo(1);
    assertThat(n.getRefs()).containsExactly(set2);
  }

  @Test
  public void localAssignmentWillNotBeConsideredADeclaration() {
    Name n = Name.createForTesting("a");
    Ref set1 = n.addSingleRefForTesting(Ref.Type.SET_FROM_GLOBAL, 0);
    Ref localSet = n.addSingleRefForTesting(Ref.Type.SET_FROM_LOCAL, 1);

    assertThat(n.getRefs()).containsExactly(set1, localSet).inOrder();

    assertThat(n.getDeclaration()).isEqualTo(set1);
    assertThat(n.getGlobalSets()).isEqualTo(1);
    assertThat(n.getLocalSets()).isEqualTo(1);

    n.removeRef(set1);

    // local set will not be used as the declaration
    assertThat(n.getDeclaration()).isNull();
    assertThat(n.getGlobalSets()).isEqualTo(0);
  }

  @Test
  public void firstDeclarationJSDocAlwaysWins() {
    GlobalNamespace namespace =
        parse(
            lines(
                "const X = {};", //
                "/** @type {symbol} */", // later assignment should win
                "X.number;",
                "/** @type {number} */", // this is the JSDoc we should use
                "X.number = 3;",
                "/** @type {string} */",
                "X.number = 'hi';",
                "/** @type {Object} */",
                "X.number;",
                ""));
    Name nameX = namespace.getOwnSlot("X.number");
    Ref declarationRef = nameX.getDeclaration();
    assertThat(declarationRef).isNotNull();

    // make sure first assignment is considered to be the declaration
    Node declarationNode = declarationRef.getNode();
    assertNode(declarationNode).matchesQualifiedName("X.number");
    Node assignNode = declarationNode.getParent();
    assertNode(assignNode).isAssign();
    Node valueNode = declarationNode.getNext();
    assertNode(valueNode).isNumber(3);

    // Make sure JSDoc on the first assignment is the JSDoc for the name
    JSDocInfo jsDocInfo = nameX.getJSDocInfo();
    assertThat(jsDocInfo).isNotNull();
    JSTypeExpression jsTypeExpression = jsDocInfo.getType();
    assertThat(jsTypeExpression).isNotNull();
    JSType jsType = jsTypeExpression.evaluate(/* scope= */ null, lastCompiler.getTypeRegistry());
    assertType(jsType).isNumber();
  }

  @Test
  public void withoutAssignmentFirstQnameDeclarationStatementJSDocWins() {
    GlobalNamespace namespace =
        parse(
            lines(
                "const X = {};", //
                "/** @type {string} */",
                "X.number;",
                "/** @type {Object} */",
                "X.number;",
                ""));
    Name nameX = namespace.getOwnSlot("X.number");
    Ref declarationRef = nameX.getDeclaration();
    assertThat(declarationRef).isNull();

    // Make sure JSDoc on the first assignment is the JSDoc for the name
    JSDocInfo jsDocInfo = nameX.getJSDocInfo();
    assertThat(jsDocInfo).isNotNull();
    JSTypeExpression jsTypeExpression = jsDocInfo.getType();
    assertThat(jsTypeExpression).isNotNull();
    JSType jsType = jsTypeExpression.evaluate(/* scope= */ null, lastCompiler.getTypeRegistry());
    assertType(jsType).isString();
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
  public void updateRefNodeRejectsRedundantUpdate() {
    GlobalNamespace namespace = parse("const A = 3;");

    Name nameA = namespace.getOwnSlot("A");
    Ref refA = nameA.getFirstRef();

    assertThrows(
        IllegalArgumentException.class,
        () -> {
          nameA.updateRefNode(refA, refA.getNode());
        });
  }

  @Test
  public void updateRefNodeMovesRefFromOldNodeToNewNode() {
    GlobalNamespace namespace = parse("const A = 3;");

    Name nameA = namespace.getOwnSlot("A");
    Ref refA = nameA.getFirstRef();

    Node oldNode = refA.getNode();
    Node newNode = IR.name("A");

    assertThat(nameA.getRefsForNode(oldNode)).containsExactly(refA);

    nameA.updateRefNode(refA, newNode);

    assertThat(refA.getNode()).isEqualTo(newNode);
    assertThat(nameA.getRefsForNode(oldNode)).isEmpty();
    assertThat(nameA.getRefsForNode(newNode)).containsExactly(refA);
  }

  @Test
  public void updateRefNodeCanSetNodeToNullButPreventsFurtherUpdates() {
    GlobalNamespace namespace = parse("const A = 3;");

    Name nameA = namespace.getOwnSlot("A");
    Ref refA = nameA.getFirstRef();

    Node oldNode = refA.getNode();

    assertThat(nameA.getRefsForNode(oldNode)).containsExactly(refA);

    nameA.updateRefNode(refA, null);

    assertThat(refA.getNode()).isNull();
    assertThat(nameA.getRefsForNode(oldNode)).isEmpty();
    // cannot get refs for null
    assertThrows(
        NullPointerException.class,
        () -> {
          nameA.getRefsForNode(null);
        });
    // cannot update the node again once it's been set to null
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          nameA.updateRefNode(refA, null);
        });
    assertThrows(
        IllegalStateException.class,
        () -> {
          nameA.updateRefNode(refA, oldNode);
        });
  }

  @Test
  public void updateRefNodeRejectsNodeWithExistingRefs() {
    GlobalNamespace namespace =
        parse(
            lines(
                "const A = 3;", // declaration ref
                "A;")); // use ref

    Name nameA = namespace.getOwnSlot("A");
    Ref declarationRef = nameA.getDeclaration();
    Ref useRef = Iterables.get(nameA.getRefs(), 1); // use ref is 2nd

    Node useNode = useRef.getNode();

    assertThrows(
        IllegalArgumentException.class,
        () -> {
          nameA.updateRefNode(declarationRef, useNode);
        });
  }

  @Test
  public void confirmTwinsAreCreated() {
    GlobalNamespace namespace =
        parse(
            lines(
                "let A;", //
                "const B = A = 3;")); // A will have twin refs here

    Name nameA = namespace.getOwnSlot("A");
    // first ref is declaration of A
    Ref setTwinRef = Iterables.get(nameA.getRefs(), 1); // second is the SET twin
    Ref getTwinRef = Iterables.get(nameA.getRefs(), 2); // third is the GET twin

    // confirm that they start as twins
    assertThat(setTwinRef.getTwin()).isEqualTo(getTwinRef);
    assertThat(getTwinRef.getTwin()).isEqualTo(setTwinRef);

    Node oldNode = getTwinRef.getNode();
    assertThat(setTwinRef.getNode()).isEqualTo(oldNode);

    // confirm that they are both associated with oldNode
    assertThat(nameA.getRefsForNode(oldNode)).containsExactly(setTwinRef, getTwinRef);
  }

  @Test
  public void updateRefNodeRemovesTwinRelationship() {
    GlobalNamespace namespace =
        parse(
            lines(
                "let A;", //
                "const B = A = 3;")); // A will have twin refs here

    Name nameA = namespace.getOwnSlot("A");
    // first ref is declaration of A
    Ref setTwinRef = Iterables.get(nameA.getRefs(), 1); // second is the SET twin
    Ref getTwinRef = Iterables.get(nameA.getRefs(), 2); // third is the GET twin

    Node oldNode = getTwinRef.getNode();

    // move the getTwinRef
    Node newNode = IR.name("A");
    nameA.updateRefNode(getTwinRef, newNode);

    // see confirmTwinsAreCreated() for verification of the original twin relationship

    // confirm that getTwinRef has been updated
    assertThat(getTwinRef.getNode()).isEqualTo(newNode);
    assertThat(nameA.getRefsForNode(newNode)).containsExactly(getTwinRef);

    // confirm that the getTwinRef and setTwinRef are no longer twins
    assertThat(getTwinRef.getTwin()).isNull();
    assertThat(setTwinRef.getTwin()).isNull();

    // confirm that setTwinRef remains otherwise unchanged
    assertThat(setTwinRef.getNode()).isEqualTo(oldNode);
    assertThat(nameA.getRefsForNode(oldNode)).containsExactly(setTwinRef);
  }

  @Test
  public void removeTwinRefsTogether() {
    GlobalNamespace namespace =
        parse(
            lines(
                "let A;", //
                "const B = A = 3;")); // A will have twin refs here

    Name nameA = namespace.getOwnSlot("A");
    // first ref is declaration of A
    Ref setTwinRef = Iterables.get(nameA.getRefs(), 1); // second is the SET twin
    Ref getTwinRef = Iterables.get(nameA.getRefs(), 2); // third is the GET twin

    // see confirmTwinsAreCreated() for verification of the original twin relationship

    Node oldNode = getTwinRef.getNode();

    nameA.removeRef(setTwinRef);

    assertThat(nameA.getRefs()).doesNotContain(setTwinRef);
    assertThat(nameA.getRefs()).contains(getTwinRef); // twin is still there
    assertThat(getTwinRef.getTwin()).isNull(); // and not a twin anymore
    assertThat(nameA.getRefsForNode(oldNode)).containsExactly(getTwinRef);
  }

  @Test
  public void removeOneRefOfAPairOfTwins() {
    GlobalNamespace namespace =
        parse(
            lines(
                "let A;", //
                "const B = A = 3;")); // A will have twin refs here

    Name nameA = namespace.getOwnSlot("A");
    // first ref is declaration of A
    Ref setTwinRef = Iterables.get(nameA.getRefs(), 1); // second is the SET twin
    Ref getTwinRef = Iterables.get(nameA.getRefs(), 2); // third is the GET twin

    // see confirmTwinsAreCreated() for verification of the original twin relationship

    Node oldNode = getTwinRef.getNode();

    // confirm that they are both associated with oldNode
    assertThat(nameA.getRefsForNode(oldNode)).containsExactly(setTwinRef, getTwinRef);

    nameA.removeTwinRefs(setTwinRef);

    assertThat(nameA.getRefs()).doesNotContain(setTwinRef);
    assertThat(nameA.getRefs()).doesNotContain(getTwinRef);
    assertThat(nameA.getRefsForNode(oldNode)).isEmpty();
  }

  @Test
  public void rescanningExistingNodesDoesNotCreateDuplicateRefs() {
    GlobalNamespace namespace = parse("class Foo {} const Bar = Foo; const Baz = Bar;");

    Name foo = namespace.getOwnSlot("Foo");
    Name bar = namespace.getOwnSlot("Bar");
    Name baz = namespace.getOwnSlot("Baz");
    Collection<Ref> originalFooRefs = ImmutableList.copyOf(foo.getRefs());
    Collection<Ref> originalBarRefs = ImmutableList.copyOf(bar.getRefs());
    Collection<Ref> originalBazRefs = ImmutableList.copyOf(baz.getRefs());

    // Rescan all of the nodes for which we got refs as if they were newly added
    Node root = lastCompiler.getJsRoot();
    ImmutableSet.Builder<AstChange> astChangeSetBuilder = ImmutableSet.builder();
    for (Name name : ImmutableList.of(foo, bar, baz)) {
      for (Ref ref : name.getRefs()) {
        astChangeSetBuilder.add(createGlobalAstChangeForNode(root, ref.getNode()));
      }
    }
    namespace.scanNewNodes(astChangeSetBuilder.build());

    // We should get the same Name objects
    assertThat(namespace.getOwnSlot("Foo")).isEqualTo(foo);
    assertThat(namespace.getOwnSlot("Bar")).isEqualTo(bar);
    assertThat(namespace.getOwnSlot("Baz")).isEqualTo(baz);

    // ...and they should contain the same refs with no duplicates added
    assertThat(foo.getRefs()).containsExactlyElementsIn(originalFooRefs).inOrder();
    assertThat(bar.getRefs()).containsExactlyElementsIn(originalBarRefs).inOrder();
    assertThat(baz.getRefs()).containsExactlyElementsIn(originalBazRefs).inOrder();
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

  @Test
  public void testClassPrototypeProp() {
    GlobalNamespace ns = parse("class C { x() {} }");

    assertThat(ns.getSlot("C.x")).isNull();
  }

  @Test
  public void testClassStaticAndPrototypePropWithSameName() {
    GlobalNamespace ns = parse("class C { x() {} static x() {} }");

    Name c = ns.getSlot("C");
    Name cDotX = ns.getSlot("C.x");

    assertThat(c.getGlobalSets()).isEqualTo(1);
    assertThat(c.props).containsExactly(cDotX);

    assertThat(cDotX.getGlobalSets()).isEqualTo(1);
    assertThat(cDotX.getParent()).isEqualTo(c);
  }

  @Test
  public void testObjectPatternAliasInDeclaration() {
    GlobalNamespace namespace = parse("const ns = {a: 3}; const {a: b} = ns;");

    Name bar = namespace.getSlot("ns");
    assertThat(bar.getGlobalSets()).isEqualTo(1);
    assertThat(bar.getAliasingGets()).isEqualTo(0);
    assertThat(bar.getTotalGets()).isEqualTo(1);

    Name nsA = namespace.getSlot("ns.a");
    assertThat(nsA.getGlobalSets()).isEqualTo(1);
    assertThat(nsA.getAliasingGets()).isEqualTo(1);

    Name b = namespace.getSlot("b");
    assertThat(b.getGlobalSets()).isEqualTo(1);
    assertThat(b.getTotalGets()).isEqualTo(0);
  }

  @Test
  public void testNestedObjectPatternAliasInDeclaration() {
    GlobalNamespace namespace = parse("const ns = {a: {b: 3}}; const {a: {b}} = ns;");

    Name bar = namespace.getSlot("ns");
    assertThat(bar.getGlobalSets()).isEqualTo(1);
    assertThat(bar.getAliasingGets()).isEqualTo(0);

    // we treat ns.a as having an 'aliasing' get since we don't traverse into the nested pattern
    Name nsA = namespace.getSlot("ns.a");
    assertThat(nsA.getGlobalSets()).isEqualTo(1);
    assertThat(nsA.getAliasingGets()).isEqualTo(1);

    Name nsAB = namespace.getSlot("ns.a.b");
    assertThat(nsAB.getGlobalSets()).isEqualTo(1);
    // we don't consider this an 'aliasing get' because it's in a nested pattern
    assertThat(nsAB.getAliasingGets()).isEqualTo(0);

    Name b = namespace.getSlot("b");
    assertThat(b.getGlobalSets()).isEqualTo(1);
    assertThat(b.getTotalGets()).isEqualTo(0);
  }

  @Test
  public void testObjectPatternAliasInAssign() {
    GlobalNamespace namespace = parse("const ns = {a: 3}; const x = {}; ({a: x.y} = ns);");

    Name bar = namespace.getSlot("ns");
    assertThat(bar.getGlobalSets()).isEqualTo(1);
    assertThat(bar.getAliasingGets()).isEqualTo(0);

    Name nsA = namespace.getSlot("ns.a");
    assertThat(nsA.getGlobalSets()).isEqualTo(1);
    assertThat(nsA.getAliasingGets()).isEqualTo(1);

    Name xY = namespace.getSlot("x.y");
    // TODO(b/117673791): this should be 1
    assertThat(xY.getGlobalSets()).isEqualTo(0);
  }

  @Test
  public void testObjectPatternRestInDeclaration() {
    GlobalNamespace namespace = parse("const ns = {a: 3}; const {a, ...b} = ns;");

    Name ns = namespace.getSlot("ns");
    assertThat(ns.getGlobalSets()).isEqualTo(1);
    assertThat(ns.getTotalGets()).isEqualTo(1);
    assertThat(ns.getAliasingGets()).isEqualTo(1);

    Name nsA = namespace.getSlot("ns.a");
    assertThat(nsA.getGlobalSets()).isEqualTo(1);
    assertThat(nsA.getTotalGets()).isEqualTo(1);
    assertThat(nsA.getAliasingGets()).isEqualTo(1);

    Name b = namespace.getSlot("b");
    assertThat(b.getGlobalSets()).isEqualTo(1);
    assertThat(b.getTotalGets()).isEqualTo(0);
  }

  @Test
  public void testObjectPatternRestNestedInDeclaration() {
    GlobalNamespace namespace = parse("const ns = {a: 3, b: {}}; const {a, b: {...c}} = ns;");

    Name ns = namespace.getSlot("ns");
    assertThat(ns.getGlobalSets()).isEqualTo(1);
    assertThat(ns.getTotalGets()).isEqualTo(1);
    // Nested patterns don't alias the top-level rhs.
    assertThat(ns.getAliasingGets()).isEqualTo(0);

    Name nsB = namespace.getSlot("ns.b");
    assertThat(nsB.getGlobalSets()).isEqualTo(1);
    assertThat(nsB.getTotalGets()).isEqualTo(1);
    assertThat(nsB.getAliasingGets()).isEqualTo(1);
  }

  @Test
  public void testObjectPatternRestAliasInAssign() {
    GlobalNamespace namespace = parse("const ns = {a: 3}; const x = {}; ({a, ...x.y} = ns);");

    Name ns = namespace.getSlot("ns");
    assertThat(ns.getGlobalSets()).isEqualTo(1);
    assertThat(ns.getAliasingGets()).isEqualTo(1);

    Name nsA = namespace.getSlot("ns.a");
    assertThat(nsA.getGlobalSets()).isEqualTo(1);
    assertThat(nsA.getAliasingGets()).isEqualTo(1);

    Name xY = namespace.getSlot("x.y");
    // TODO(b/117673791): this should be 1
    assertThat(xY.getGlobalSets()).isEqualTo(0);
  }

  @Test
  public void testObjectPatternAliasInForOf() {
    GlobalNamespace namespace = parse("const ns = {a: 3}; for (const {a: b} of [ns]) {}");

    Name bar = namespace.getSlot("ns");
    assertThat(bar.getGlobalSets()).isEqualTo(1);
    assertThat(bar.getAliasingGets()).isEqualTo(1);

    // GlobalNamespace ignores for-of and array literals, not realizing that `b` reads `ns.a`
    Name nsA = namespace.getSlot("ns.a");
    assertThat(nsA.getGlobalSets()).isEqualTo(1);
    assertThat(nsA.getAliasingGets()).isEqualTo(0);
  }

  @Test
  public void testObjectLitSpreadAliasInDeclaration() {
    GlobalNamespace namespace = parse("const ns = {a: 3}; const {a} = {...ns};");

    Name ns = namespace.getSlot("ns");
    assertThat(ns.getGlobalSets()).isEqualTo(1);
    assertThat(ns.getAliasingGets()).isEqualTo(1);

    Name nsA = namespace.getSlot("ns.a");
    assertThat(nsA.getGlobalSets()).isEqualTo(1);
    assertThat(nsA.getAliasingGets()).isEqualTo(0);

    Name a = namespace.getSlot("a");
    assertThat(a.getGlobalSets()).isEqualTo(1);
  }

  @Test
  public void testObjectLitSpreadAliasInAssign() {
    GlobalNamespace namespace = parse("const ns = {a: 3}; const x = {}; ({a: x.y} = {...ns});");

    Name ns = namespace.getSlot("ns");
    assertThat(ns.getGlobalSets()).isEqualTo(1);
    assertThat(ns.getAliasingGets()).isEqualTo(1);

    Name nsA = namespace.getSlot("ns.a");
    assertThat(nsA.getGlobalSets()).isEqualTo(1);
    assertThat(nsA.getAliasingGets()).isEqualTo(0);

    Name xY = namespace.getSlot("x.y");
    // TODO(b/117673791): this should be 1
    assertThat(xY.getGlobalSets()).isEqualTo(0);
  }

  @Test
  public void testLhsCastInAssignment() {
    // The type of the cast doesn't matter.
    // Casting is only legal JS syntax in simple assignments, not with destructuring or declaration.
    GlobalNamespace namespace = parse("const ns = {}; const b = 5; /** @type {*} */ (ns.a) = b;");

    Name ns = namespace.getSlot("ns");
    assertThat(ns.getGlobalSets()).isEqualTo(1);
    assertThat(ns.getTotalGets()).isEqualTo(0);

    Name nsA = namespace.getSlot("ns.a");
    assertThat(nsA.getGlobalSets()).isEqualTo(0); // TODO(b/127505242): Should be 1.
    assertThat(nsA.getTotalGets()).isEqualTo(1); // TODO(b/127505242): Should be 0.

    Name b = namespace.getSlot("b");
    assertThat(b.getGlobalSets()).isEqualTo(1);
    assertThat(b.getAliasingGets()).isEqualTo(1);
  }

  @Test
  public void testCannotCollapseAliasedObjectLitProperty() {
    GlobalNamespace namespace = parse("var foo = {prop: 0}; use(foo);");

    Name fooProp = namespace.getSlot("foo.prop");

    // We should not convert foo.prop -> foo$prop because use(foo) might read foo.prop
    assertThat(fooProp.canCollapse()).isFalse();
  }

  @Test
  public void testCanCollapseAliasedConstructorProperty() {
    GlobalNamespace namespace =
        parse(
            lines(
                "/** @constructor */",
                "var Foo = function() {}",
                "",
                "Foo.prop = prop;",
                "use(Foo);"));

    Name fooProp = namespace.getSlot("Foo.prop");

    // We should still convert Foo.prop -> Foo$prop, even though use(Foo) might read foo.prop,
    // because Foo is a constructor
    assertThat(fooProp.canCollapse()).isTrue();
  }

  @Test
  public void testCanCollapseAliasedClassProperty() {
    GlobalNamespace namespace = parse(lines("class Foo {} Foo.prop = prop; use(Foo);"));

    Name fooProp = namespace.getSlot("Foo.prop");

    // We should still convert Foo.prop -> Foo$prop, even though use(Foo) might read foo.prop,
    // because Foo is a constructor
    assertThat(fooProp.canCollapse()).isTrue();
  }

  @Test
  public void testCanCollapse_objectLitProperty_declaredBeforeASpread() {
    GlobalNamespace namespace = parse("var foo = {prop: 0, ...bar}; use(foo.prop);");

    Name fooProp = namespace.getSlot("foo.prop");
    assertThat(fooProp.canCollapse()).isFalse();
  }

  private GlobalNamespace parse(String js) {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setSkipNonTranspilationPasses(true);
    options.setLanguageIn(LanguageMode.ECMASCRIPT_NEXT);
    options.setLanguageOut(LanguageMode.ECMASCRIPT_NEXT);
    compiler.compile(SourceFile.fromCode("ex.js", ""), SourceFile.fromCode("test.js", js), options);
    assertThat(compiler.getErrors()).isEmpty();
    this.lastCompiler = compiler;

    return new GlobalNamespace(compiler, compiler.getRoot());
  }
}
