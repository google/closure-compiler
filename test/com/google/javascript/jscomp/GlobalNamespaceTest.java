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
import static com.google.javascript.rhino.testing.NodeSubject.assertNode;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.GlobalNamespace.AstChange;
import com.google.javascript.jscomp.GlobalNamespace.Inlinability;
import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.jscomp.GlobalNamespace.SimpleAstChange;
import com.google.javascript.jscomp.modules.ModuleMapCreator;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.jscomp.testing.JSChunkGraphBuilder;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import org.jspecify.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link GlobalNamespace}. */
@RunWith(JUnit4.class)
public final class GlobalNamespaceTest {

  private @Nullable Compiler lastCompiler = null;
  private boolean assumeStaticInheritanceIsNotUsed = true;

  @Test
  public void detectsPropertySetsInAssignmentOperators() {
    GlobalNamespace namespace = parse("const a = {b: 0}; a.b += 1; a.b = 2;");

    assertThat(namespace.getSlot("a.b").getGlobalSets()).isEqualTo(3);
  }

  @Test
  public void detectsPropertySetsInDestructuring() {
    GlobalNamespace namespace = parse("const a = {b: 0}; [a.b] = [1]; ({b: a.b} = {b: 2});");

    // TODO(b/120303257): this should be 3
    assertThat(namespace.getSlot("a.b").getGlobalSets()).isEqualTo(1);
  }

  @Test
  public void detectsPropertySetsInIncDecOperators() {
    GlobalNamespace namespace = parse("const a = {b: 0}; a.b++; a.b--;");

    assertThat(namespace.getSlot("a.b").getGlobalSets()).isEqualTo(3);
  }

  @Test
  public void firstGlobalAssignmentIsConsideredDeclaration() {
    GlobalNamespace namespace = parse("");
    Name n = namespace.createNameForTesting("a");
    Ref set1 = n.addSingleRefForTesting(IR.name("set1"), Ref.Type.SET_FROM_GLOBAL);
    Ref set2 = n.addSingleRefForTesting(IR.name("set2"), Ref.Type.SET_FROM_GLOBAL);

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
  public void testReferencesToUndefinedRootName() {
    GlobalNamespace namespace = parse("a; a.b = 0; a.b; a?.b");
    assertThat(namespace.getSlot("a")).isNull();
    assertThat(namespace.getSlot("a.b")).isNull();
  }

  @Test
  public void nullishCoalesce() {
    GlobalNamespace namespace = parse("var a = a ?? {};");
    Name a = namespace.getSlot("a");

    assertThat(a).isNotNull();
    assertThat(a.getRefs()).hasSize(2);
    assertThat(a.getLocalSets()).isEqualTo(0);
    assertThat(a.getGlobalSets()).isEqualTo(1);
  }

  @Test
  public void logicalAssignment1() {
    GlobalNamespace namespace = parse("var a = a ||= {};");
    Name a = namespace.getSlot("a");

    assertThat(a).isNotNull();
    assertThat(a.getRefs()).hasSize(2);
    assertThat(a.getLocalSets()).isEqualTo(0);
    assertThat(a.getGlobalSets()).isEqualTo(2);
  }

  @Test
  public void logicalAssignment2() {
    GlobalNamespace namespace = parse("var a = a || (a = {});");
    Name a = namespace.getSlot("a");

    assertThat(a).isNotNull();
    assertThat(a.getRefs()).hasSize(3);
    assertThat(a.getLocalSets()).isEqualTo(0);
    assertThat(a.getGlobalSets()).isEqualTo(2);
  }

  @Test
  public void testlogicalAssignmentGets1() {
    GlobalNamespace namespace =
        parse(
            """
            const ns = {};
            ns.n1 = 1;
            ns.n2 = 2;
            ns.n1 ??= ns.n2;
            ns.n1 &&= ns.n2;
            """);

    Name bar = namespace.getSlot("ns");
    assertThat(bar.getGlobalSets()).isEqualTo(1);
    assertThat(bar.getAliasingGets()).isEqualTo(0);
    assertThat(bar.getTotalGets()).isEqualTo(0);

    Name n1 = namespace.getSlot("ns.n1");
    assertThat(n1.getGlobalSets()).isEqualTo(3);
    assertThat(n1.getAliasingGets()).isEqualTo(0);
    assertThat(n1.getTotalGets()).isEqualTo(0);

    Name n2 = namespace.getSlot("ns.n2");
    assertThat(n2.getGlobalSets()).isEqualTo(1);
    assertThat(n2.getAliasingGets()).isEqualTo(2);
    assertThat(n2.getTotalGets()).isEqualTo(2);
  }

  @Test
  public void testlogicalAssignmentGets2() {
    GlobalNamespace namespace =
        parse(
            """
            const ns = {};
            ns.n1 = 1;
            ns.n2 = 2;
            ns.n1 ?? (ns.n1 = ns.n2);
            ns.n1 && (ns.n1 = ns.n2);
            """);

    Name bar = namespace.getSlot("ns");
    assertThat(bar.getGlobalSets()).isEqualTo(1);
    assertThat(bar.getAliasingGets()).isEqualTo(0);
    assertThat(bar.getTotalGets()).isEqualTo(0);

    Name n1 = namespace.getSlot("ns.n1");
    assertThat(n1.getGlobalSets()).isEqualTo(3);
    assertThat(n1.getAliasingGets()).isEqualTo(2);
    assertThat(n1.getTotalGets()).isEqualTo(4);

    Name n2 = namespace.getSlot("ns.n2");
    assertThat(n2.getGlobalSets()).isEqualTo(1);
    assertThat(n2.getAliasingGets()).isEqualTo(2);
    assertThat(n2.getTotalGets()).isEqualTo(2);
  }

  @Test
  public void detectsPropertySetsInLogicalAssignmentOperators1() {
    GlobalNamespace namespace = parse("const a = {b: 0}; a.b ||= 1; a.b = 2;");

    assertThat(namespace.getSlot("a.b").getGlobalSets()).isEqualTo(3);
  }

  @Test
  public void detectsPropertySetsInLogicalAssignmentOperators2() {
    GlobalNamespace namespace = parse("const a = {b: 0}; a.b || (a.b = 1); a.b = 2;");

    assertThat(namespace.getSlot("a.b").getGlobalSets()).isEqualTo(3);
  }

  @Test
  public void hook() {
    GlobalNamespace namespace = parse("var a = a ? a : {}");
    Name a = namespace.getSlot("a");

    assertThat(a).isNotNull();
    assertThat(a.getRefs()).hasSize(3);
    assertThat(a.getLocalSets()).isEqualTo(0);
    assertThat(a.getGlobalSets()).isEqualTo(1);
  }

  @Test
  public void localAssignmentWillNotBeConsideredADeclaration() {
    GlobalNamespace namespace = parse("");
    Name n = namespace.createNameForTesting("a");
    Ref set1 = n.addSingleRefForTesting(IR.name("set1"), Ref.Type.SET_FROM_GLOBAL);
    Ref localSet = n.addSingleRefForTesting(IR.name("localSet"), Ref.Type.SET_FROM_LOCAL);

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
            """
            const X = {};
            /** @type {symbol} */ // later assignment should win
            X.number;
            /** @type {number} */ // this is the JSDoc we should use
            X.number = 3;
            /** @type {string} */
            X.number = 'hi';
            /** @type {Object} */
            X.number;
            """);
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
            """
            const X = {};
            /** @type {string} */
            X.number;
            /** @type {Object} */
            X.number;
            """);
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
            """
            class Superclass {}
            class Subclass extends Superclass {}
            """);

    Name superclass = namespace.getOwnSlot("Superclass");
    assertThat(superclass.getRefs()).hasSize(2);
    assertThat(superclass.getSubclassingGets()).isEqualTo(1);
  }

  @Test
  public void testStaticInheritedReferencesDontReferToSuperclass() {
    GlobalNamespace namespace =
        parse(
            """
            class Superclass {
              static staticMethod() {}
            }
            class Subclass extends Superclass {}
            Subclass.staticMethod();
            Subclass.staticMethod?.();
            Subclass?.staticMethod();
            """);

    Name superclass = namespace.getOwnSlot("Superclass");
    assertThat(superclass.getSubclassingGets()).isEqualTo(1);

    Name superclassStaticMethod = namespace.getOwnSlot("Superclass.staticMethod");
    assertThat(superclassStaticMethod.getRefs()).hasSize(1);
    assertThat(superclassStaticMethod.getDeclaration()).isNotNull();

    Name subclassStaticMethod = namespace.getOwnSlot("Subclass.staticMethod");
    // 2 references:
    // `Subclass.staticMethod()`
    // `Subclass.staticMethod?.()`
    // `SubClass?.staticMethod()` is a reference to `SubClass`, but not
    // to `SubClass.staticmethod`.
    assertThat(subclassStaticMethod.getRefs()).hasSize(2);
    assertThat(subclassStaticMethod.getDeclaration()).isNull();
    assertThat(subclassStaticMethod.getCallGets()).isEqualTo(2);

    Name subclass = namespace.getOwnSlot("Subclass");
    assertThat(subclass.getRefs()).hasSize(2);
    // `class Subclass` is the declaration reference
    assertThat(subclass.getDeclaration()).isNotNull();
    // `SubClass?.staticMethod` is an aliasing get on `SubClass`
    assertThat(subclass.getAliasingGets()).isEqualTo(1);
  }

  @Test
  public void updateRefNodeRejectsRedundantUpdate() {
    GlobalNamespace namespace = parse("const A = 3;");

    Name nameA = namespace.getOwnSlot("A");
    Ref refA = nameA.getFirstRef();

    assertThrows(IllegalArgumentException.class, () -> nameA.updateRefNode(refA, refA.getNode()));
  }

  @Test
  public void updateRefNodeMovesRefFromOldNodeToNewNode() {
    GlobalNamespace namespace = parse("const A = 3;");

    Name nameA = namespace.getOwnSlot("A");
    Ref refA = nameA.getFirstRef();

    Node oldNode = refA.getNode();
    Node newNode = IR.name("A");

    assertThat(nameA.getRefForNode(oldNode)).isEqualTo(refA);

    nameA.updateRefNode(refA, newNode);

    assertThat(refA.getNode()).isEqualTo(newNode);
    assertThat(nameA.getRefForNode(oldNode)).isNull();
    assertThat(nameA.getRefForNode(newNode)).isEqualTo(refA);
  }

  @Test
  public void updateRefNodeCanSetNodeToNullButPreventsFurtherUpdates() {
    GlobalNamespace namespace = parse("const A = 3;");

    Name nameA = namespace.getOwnSlot("A");
    Ref refA = nameA.getFirstRef();

    Node oldNode = refA.getNode();

    assertThat(nameA.getRefForNode(oldNode)).isEqualTo(refA);

    nameA.updateRefNode(refA, null);

    assertThat(refA.getNode()).isNull();
    assertThat(nameA.getRefForNode(oldNode)).isNull();
    // cannot get refs for null
    assertThrows(NullPointerException.class, () -> nameA.getRefForNode(null));
    // cannot update the node again once it's been set to null
    assertThrows(IllegalArgumentException.class, () -> nameA.updateRefNode(refA, null));
    assertThrows(IllegalStateException.class, () -> nameA.updateRefNode(refA, oldNode));
  }

  @Test
  public void updateRefNodeRejectsNodeWithExistingRefs() {
    GlobalNamespace namespace =
        parse(
            """
            const A = 3; // declaration ref
            A;
            """); // use ref

    Name nameA = namespace.getOwnSlot("A");
    Ref declarationRef = nameA.getDeclaration();
    Ref useRef = Iterables.get(nameA.getRefs(), 1); // use ref is 2nd

    Node useNode = useRef.getNode();

    assertThrows(
        IllegalArgumentException.class, () -> nameA.updateRefNode(declarationRef, useNode));
  }

  @Test
  public void confirmTwinsAreCreated() {
    GlobalNamespace namespace =
        parse(
            """
            let A;
            const B = A = 3;
            """); // A will have twin refs here

    Name nameA = namespace.getOwnSlot("A");
    // first ref is declaration of A
    Ref twinRef = Iterables.get(nameA.getRefs(), 1); // second is the GET_AND_SET twin

    // confirm that they start as twins
    assertThat(twinRef.isTwin()).isTrue();
    assertThat(twinRef.isSetFromGlobal()).isTrue();
    assertThat(twinRef.isAliasingGet()).isTrue();

    Node oldNode = twinRef.getNode();

    // confirm that it is associated with oldNode
    assertThat(nameA.getRefForNode(oldNode)).isEqualTo(twinRef);

    // confirm that nameA correctly tracks its aliasingGets and globalSets
    assertThat(nameA.getGlobalSets()).isEqualTo(2);
    assertThat(nameA.getAliasingGets()).isEqualTo(1);
  }

  @Test
  public void updateRefNodeCanRemoveTwinRefs() {
    GlobalNamespace namespace =
        parse(
            """
            let A;
            const B = A = 3;
            """); // A will have twin refs here

    Name nameA = namespace.getOwnSlot("A");
    // first ref is declaration of A
    Ref twinRef = Iterables.get(nameA.getRefs(), 1); // second is the GET_AND_SET twin

    Node oldNode = twinRef.getNode();

    // move the getTwinRef
    Node newNode = IR.name("A");
    nameA.updateRefNode(twinRef, newNode);

    // see confirmTwinsAreCreated() for verification of the original twin relationship

    // confirm that getTwinRef has been updated
    assertThat(twinRef.getNode()).isEqualTo(newNode);
    assertThat(nameA.getRefForNode(newNode)).isEqualTo(twinRef);
    assertThat(twinRef.isTwin()).isTrue();

    // confirm that all references to oldNode are removed.
    assertThat(nameA.getRefForNode(oldNode)).isNull();
  }

  @Test
  public void removeTwinRef() {
    GlobalNamespace namespace =
        parse(
            """
            let A;
            const B = A = 3;
            """); // A will have twin refs here

    Name nameA = namespace.getOwnSlot("A");
    // first ref is declaration of A
    Ref twinRef = Iterables.get(nameA.getRefs(), 1); // second is the GET_AND_SET twin

    // see confirmTwinsAreCreated() for verification of the original twin relationship

    Node oldNode = twinRef.getNode();

    // confirm that they are both associated with oldNode
    assertThat(nameA.getRefForNode(oldNode)).isEqualTo(twinRef);

    nameA.removeRef(twinRef);

    assertThat(nameA.getRefs()).doesNotContain(twinRef);
    assertThat(nameA.getRefForNode(oldNode)).isNull();
    assertThat(nameA.getAliasingGets()).isEqualTo(0);
    assertThat(nameA.getGlobalSets()).isEqualTo(1);
  }

  @Test
  public void rescanningExistingNodesDoesNotCreateDuplicateRefs() {
    GlobalNamespace namespace = parse("class Foo {} const Bar = Foo; const Baz = Bar;");

    Name foo = namespace.getOwnSlot("Foo");
    Name bar = namespace.getOwnSlot("Bar");
    Name baz = namespace.getOwnSlot("Baz");
    ImmutableList<Ref> originalFooRefs = ImmutableList.copyOf(foo.getRefs());
    ImmutableList<Ref> originalBarRefs = ImmutableList.copyOf(bar.getRefs());
    ImmutableList<Ref> originalBazRefs = ImmutableList.copyOf(baz.getRefs());

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
    Ref xBarGet = xbar.getRefs().stream().filter(Ref::isAliasingGet).findFirst().get();
    assertThat(xBarGet.getNode()).isEqualTo(xName.getParent());
    assertThat(xBarGet.isAliasingGet()).isTrue();
    assertThat(xBarGet.getChunk()).isEqualTo(xbar.getDeclaration().getChunk());
  }

  @Test
  public void testScanFromNodeNoticesHasOwnProperty() {
    GlobalNamespace namespace = parse("const x = {bar: 0}; const y = x; y.hasOwnProperty('bar');");

    Name xName = namespace.getOwnSlot("x");
    Name yName = namespace.getOwnSlot("y");
    assertThat(xName.usesHasOwnProperty()).isFalse();
    assertThat(yName.usesHasOwnProperty()).isTrue();

    // Replace "const baz = y.bar" with "const baz = x.bar"
    Node root = lastCompiler.getJsRoot();
    Node yDotHasOwnProperty =
        root.getFirstChild() // SCRIPT
            .getLastChild() // EXPR_RESULT `y.hasOwnProperty('bar');`
            .getFirstFirstChild(); // `y.hasOwnProperty`
    Node yNode = yDotHasOwnProperty.getFirstChild(); // `y`
    assertNode(yDotHasOwnProperty).matchesQualifiedName("y.hasOwnProperty");
    Node xNode = IR.name("x");
    yNode.replaceWith(xNode);

    // Rescan the new nodes
    // In this case the new node is `x.hasOwnProperty`, since that's the full, new qualified name.
    namespace.scanNewNodes(ImmutableSet.of(createGlobalAstChangeForNode(root, yDotHasOwnProperty)));

    assertThat(xName.usesHasOwnProperty()).isTrue();
  }

  private AstChange createGlobalAstChangeForNode(Node jsRoot, Node n) {
    // This only creates a global scope, so don't use this with local nodes
    Scope globalScope = new SyntacticScopeCreator(lastCompiler).createScope(jsRoot, null);
    return new SimpleAstChange(n, Iterables.getFirst(lastCompiler.getChunks(), null), globalScope);
  }

  @Test
  public void testCollapsing_forEscapedConstructor_ignoringStaticInheritance() {
    GlobalNamespace namespace =
        parse(
            """
            /** @constructor */
            function Bar() {}
            use(Bar);
            """);

    Name bar = namespace.getSlot("Bar");
    assertThat(bar.canCollapse()).isTrue(); // trivially true, already collapsed
    // we collapse properties of Bar even though it's escaped, intentionally unsafe.
    // this is mostly to support minification for goog.provide namespaces containing @constructors
    assertThat(bar.canCollapseUnannotatedChildNames()).isTrue();
  }

  @Test
  public void testCollapsing_forEscapedConstructor_consideringStaticInheritance() {
    this.assumeStaticInheritanceIsNotUsed = false;
    GlobalNamespace namespace =
        parse(
            """
            /** @constructor */
            function Bar() {}
            use(Bar);
            """);

    Name bar = namespace.getSlot("Bar");
    assertThat(bar.canCollapse()).isTrue(); // trivially true, already collapsed
    assertThat(bar.canCollapseUnannotatedChildNames()).isFalse();
  }

  @Test
  public void testInlinability_forAliasingPropertyOnEscapedConstructor_ignoringStaticInheritance() {
    GlobalNamespace namespace =
        parse(
            """
            var prop = 1;
            /** @constructor */
            var Foo = function() {}

            Foo.prop = prop;

            /** @constructor */
            function Bar() {}
            Bar.aliasOfFoo = Foo; // alias Foo
            use(Bar); // uninlinable alias of Bar
            const BarAlias = Bar; // inlinable alias of Bar
            alert(Bar.aliasOfFoo.prop);
            alert(BarAlias.aliasOfFoo.prop);
            """);

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
  public void
      testInlinability_forAliasingPropertyOnEscapedConstructor_consideringStaticInheritance() {
    this.assumeStaticInheritanceIsNotUsed = false;
    GlobalNamespace namespace =
        parse(
            """
            var prop = 1;
            /** @constructor */
            var Foo = function() {}

            Foo.prop = prop;

            /** @constructor */
            function Bar() {}
            Bar.aliasOfFoo = Foo; // alias Foo
            use(Bar); // uninlinable alias of Bar
            const BarAlias = Bar; // inlinable alias of Bar
            alert(Bar.aliasOfFoo.prop);
            alert(BarAlias.aliasOfFoo.prop);
            """);

    Name barAliasOfFoo = namespace.getSlot("Bar.aliasOfFoo");
    Inlinability barAliasInlinability = barAliasOfFoo.calculateInlinability();

    assertThat(barAliasInlinability.shouldInlineUsages()).isFalse();
    assertThat(barAliasInlinability.shouldRemoveDeclaration()).isFalse();
  }

  @Test
  public void testClassPrototypeProp() {
    GlobalNamespace ns = parse("class C { x() {} }");

    assertThat(ns.getSlot("C.x")).isNull();
  }

  @Test
  public void testClassStaticField_withInitializer() {
    GlobalNamespace ns =
        parse(
            """
            class C {
              static x = 1;
            }
            """);

    Name c = ns.getSlot("C");
    Name cDotX = ns.getSlot("C.x");

    assertThat(c.getGlobalSets()).isEqualTo(1);
    assertThat(c.props).containsExactly(cDotX);

    assertThat(cDotX.getGlobalSets()).isEqualTo(1);
    assertThat(cDotX.getParent()).isEqualTo(c);
    assertThat(cDotX.canCollapse()).isTrue();
  }

  @Test
  public void testClassStaticField_withoutInitializer() {
    GlobalNamespace ns =
        parse(
            """
            class C {
              /** @type {number} */
              static x;
            }
            """);

    Name c = ns.getSlot("C");
    Name cDotX = ns.getSlot("C.x");

    assertThat(c.getGlobalSets()).isEqualTo(1);
    assertThat(c.props).containsExactly(cDotX);

    assertThat(cDotX.getGlobalSets()).isEqualTo(1);
    assertThat(cDotX.getParent()).isEqualTo(c);
    assertThat(cDotX.canCollapse()).isTrue();

    JSDocInfo jsDocInfo = cDotX.getJSDocInfo();
    assertThat(jsDocInfo).isNotNull();
    JSTypeExpression jsTypeExpression = jsDocInfo.getType();
    assertThat(jsTypeExpression).isNotNull();
    JSType jsType = jsTypeExpression.evaluate(/* scope= */ null, lastCompiler.getTypeRegistry());
    assertType(jsType).isNumber();
  }

  @Test
  public void testClassStaticField_withSuper() {
    this.assumeStaticInheritanceIsNotUsed = false;
    GlobalNamespace ns =
        parse(
            """
            class A {
              static y = 1;
            }
            class C extends A {
              static x = super.y;
            }
            """);

    Name cDotX = ns.getSlot("C.x");
    assertThat(cDotX.canCollapse()).isFalse();
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
  public void testLocalVarsDefinedinStaticBlocks() {
    GlobalNamespace namespace = parse("class C{ static{ var x; }}");
    assertThat(namespace.getSlot("x")).isNull();
  }

  @Test
  public void testAddPropertytoGlobalObjectinClassStaticBlock() {
    GlobalNamespace namespace = parse("const a = {}; class C{ static { a.b = 1;}}");

    Name a = namespace.getSlot("a");

    assertThat(a.getGlobalSets()).isEqualTo(1);
    assertThat(a.getTotalSets()).isEqualTo(1);

    Name ab = namespace.getSlot("a.b");

    assertThat(ab.getParent()).isEqualTo(a);
    assertThat(ab.getGlobalSets()).isEqualTo(0);
    assertThat(ab.getLocalSets()).isEqualTo(1);
  }

  @Test
  public void testDirectGets() {
    // None of the symbol uses here should be considered aliasing gets.
    GlobalNamespace namespace =
        parse(
            """
            const ns = {};
            ns.n1 = 1;
            ns.n2 = 2;
            ns.n1 === ns.n2;
            ns.n1 == ns.n2;
            ns.n1 !== ns.n2;
            ns.n1 != ns.n2;
            ns.n1 <  ns.n2;
            ns.n1 <= ns.n2;
            ns.n1 >  ns.n2;
            ns.n1 >= ns.n2;
            ns.n1 + ns.n2;
            ns.n1 - ns.n2;
            ns.n1 * ns.n2;
            ns.n1 / ns.n2;
            ns.n1 % ns.n2;
            ns.n1 ** ns.n2;
            ns.n1 & ns.n2;
            ns.n1 | ns.n2;
            ns.n1 ^ ns.n2;
            ns.n1 << ns.n2;
            ns.n1 >> ns.n2;
            ns.n1 >>> ns.n2;
            ns.n1 && ns.n2;
            ns.n1 || ns.n2;
            """);

    Name bar = namespace.getSlot("ns");
    assertThat(bar.getGlobalSets()).isEqualTo(1);
    assertThat(bar.getAliasingGets()).isEqualTo(0);
    // getting `ns.n1` doesn't count as a get on `ns`
    assertThat(bar.getTotalGets()).isEqualTo(0);

    Name n1 = namespace.getSlot("ns.n1");
    assertThat(n1.getGlobalSets()).isEqualTo(1);
    assertThat(n1.getAliasingGets()).isEqualTo(0);
    assertThat(n1.getTotalGets()).isEqualTo(22);

    Name n2 = namespace.getSlot("ns.n2");
    assertThat(n2.getGlobalSets()).isEqualTo(1);
    assertThat(n2.getAliasingGets()).isEqualTo(0);
    assertThat(n2.getTotalGets()).isEqualTo(22);
  }

  @Test
  public void testObjectPatternAliasInDeclaration() {
    GlobalNamespace namespace = parse("const ns = {a: 3}; const {a: b} = ns;");

    Name bar = namespace.getSlot("ns");
    assertThat(bar.getGlobalSets()).isEqualTo(1);
    assertThat(bar.getAliasingGets()).isEqualTo(1);
    assertThat(bar.getTotalGets()).isEqualTo(1);

    Name nsA = namespace.getSlot("ns.a");
    assertThat(nsA.getGlobalSets()).isEqualTo(1);
    assertThat(nsA.getAliasingGets()).isEqualTo(1);

    Name b = namespace.getSlot("b");
    assertThat(b.getGlobalSets()).isEqualTo(1);
    assertThat(b.getTotalGets()).isEqualTo(0);
  }

  @Test
  public void testConditionalDestructuringDoesNotHideAliasingGet() {
    GlobalNamespace namespace =
        parse(
            """
            const ns1 = {a: 3};
            const ns2 = {b: 3};
            // Creates an aliasing get for both ns1 and ns2
            const {a, b} = Math.random() ? ns1 : ns2;
            """);

    Name ns1 = namespace.getSlot("ns1");
    assertThat(ns1.getAliasingGets()).isEqualTo(1);
    assertThat(ns1.getTotalGets()).isEqualTo(1);

    Name ns2 = namespace.getSlot("ns2");
    assertThat(ns2.getAliasingGets()).isEqualTo(1);
    assertThat(ns2.getTotalGets()).isEqualTo(1);
  }

  @Test
  public void testNestedObjectPatternAliasInDeclaration() {
    GlobalNamespace namespace = parse("const ns = {a: {b: 3}}; const {a: {b}} = ns;");

    Name bar = namespace.getSlot("ns");
    assertThat(bar.getGlobalSets()).isEqualTo(1);
    assertThat(bar.getAliasingGets()).isEqualTo(1);

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
    assertThat(bar.getAliasingGets()).isEqualTo(1);

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
    assertThat(ns.getAliasingGets()).isEqualTo(1);

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
  public void testObjectAssignOntoAGetProp() {
    GlobalNamespace namespace =
        parse("const obj = {a:3}; const ns = {}; ns.a = Object.assign({}, obj); ");

    Name obj = namespace.getSlot("obj");
    assertThat(obj.getGlobalSets()).isEqualTo(1);
    assertThat(obj.getAliasingGets()).isEqualTo(1);

    Name objA = namespace.getSlot("obj.a");
    assertThat(objA.getGlobalSets()).isEqualTo(1);
    assertThat(objA.getAliasingGets()).isEqualTo(0);

    Name ns = namespace.getSlot("ns");
    assertThat(ns.getGlobalSets()).isEqualTo(1);
    assertThat(ns.getAliasingGets()).isEqualTo(0);

    Name nsA = namespace.getSlot("ns.a");
    assertThat(nsA.getGlobalSets()).isEqualTo(1);
    assertThat(nsA.getAliasingGets()).isEqualTo(0);
    assertThat(nsA.isObjectLiteral()).isFalse(); // `ns.a` is considered an "OTHER" type
  }

  @Test
  public void testObjectLitSpreadOntoAGetProp() {
    GlobalNamespace namespace = parse("const obj = {a:3}; const ns = {}; ns.a = {...obj}");

    Name obj = namespace.getSlot("obj");
    assertThat(obj.getGlobalSets()).isEqualTo(1);
    assertThat(obj.getAliasingGets()).isEqualTo(1);

    Name objA = namespace.getSlot("obj.a");
    assertThat(objA.getGlobalSets()).isEqualTo(1);
    assertThat(objA.getAliasingGets()).isEqualTo(0);

    Name ns = namespace.getSlot("ns");
    assertThat(ns.getGlobalSets()).isEqualTo(1);
    assertThat(ns.getAliasingGets()).isEqualTo(0);

    Name nsA = namespace.getSlot("ns.a");
    assertThat(nsA.getGlobalSets()).isEqualTo(1);
    assertThat(nsA.getAliasingGets()).isEqualTo(0);
    assertThat(nsA.isObjectLiteral())
        .isTrue(); // `ns.a` is an "OBJECTLIT" type despite containing spread.
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
  public void testDoubleLhsCastInAssignment_doesNotCrash() {
    // The type of the cast doesn't matter.
    // Casting is only legal JS syntax in simple assignments, not with destructuring or declaration.
    GlobalNamespace namespace =
        parse(
            """
            const ns = {};
             const b = 5;
             /** @type {*} */ (/** @type {*} */ (ns.a)) = b;
            """);

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
  public void testCannotCollapseConditionalObjectLitProperty() {
    GlobalNamespace namespace = parse("var foo = x || {prop: 0}; use(foo.prop);");

    Name fooProp = namespace.getSlot("foo.prop");

    // We should not convert foo.prop -> foo$prop because use(foo) might read foo.prop
    assertThat(fooProp.canCollapse()).isFalse();
  }

  @Test
  public void testCannotCollapseConditionalObjectLitNestedProperty() {
    GlobalNamespace namespace = parse("var foo = x || {prop: {nested: 0}}; use(foo.prop.nested);");

    Name fooProp = namespace.getSlot("foo.prop.nested");

    // We should not convert foo.prop -> foo$prop because use(foo) might read foo.prop
    assertThat(fooProp.canCollapse()).isFalse();
  }

  @Test
  public void testGitHubIssue3733() {
    GlobalNamespace namespace =
        parse(
            """
            const X = {Y: 1};

            function fn(a) {
              if (a) {
            // Before issue #3733 was fixed GlobalNamespace failed to see this reference
            // as creating an alias for X due to a switch statement that failed to check
            // for the RETURN node type, so X.Y was incorrectly collapsed.
                return a ? X : {};
              }
            }

            console.log(fn(true).Y);
            """);

    Name nameX = namespace.getSlot("X");
    assertThat(nameX.canCollapseUnannotatedChildNames()).isFalse();

    Name propY = namespace.getSlot("X.Y");
    assertThat(propY.canCollapse()).isFalse();
  }

  @Test
  public void testThrowPreventsCollapsingChildNames() {
    GlobalNamespace namespace =
        parse(
            """
            const X = {Y: 1};

            function fn(a) {
            // This is specifically testing a bugfix closely related to GitHub issue
            // #3733. A quirk of the implementation hides the bug when the throw isn't
            // inside an if statement or the thrown value isn't a conditional expression.
              if (a) {
                throw a ? X : {};
              }
            }

            console.log(fn(true).Y);
            """);

    Name nameX = namespace.getSlot("X");
    assertThat(nameX.canCollapseUnannotatedChildNames()).isFalse();

    Name propY = namespace.getSlot("X.Y");
    assertThat(propY.canCollapse()).isFalse();
  }

  @Test
  public void testCannotCollapseObjectLitPropertyEscapedWithOptChainCall() {
    GlobalNamespace namespace = parse("var foo = {prop: 0}; use?.(foo);");

    Name fooProp = namespace.getSlot("foo.prop");

    // We should not convert foo.prop -> foo$prop because use(foo) might read foo.prop
    assertThat(fooProp.canCollapse()).isFalse();
  }

  @Test
  public void testCanCollapseAliasedConstructorProperty_ignoringStaticInheritance() {
    GlobalNamespace namespace =
        parse(
            """
            /** @constructor */
            var Foo = function() {}

            Foo.prop = prop;
            use(Foo);
            """);

    Name fooProp = namespace.getSlot("Foo.prop");

    // We should still convert Foo.prop -> Foo$prop, even though use(Foo) might read Foo.prop,
    // because Foo is a constructor
    assertThat(fooProp.canCollapse()).isTrue();
  }

  @Test
  public void testCannotCollapseAliasedConstructorProperty_consideringStaticInheritance() {
    this.assumeStaticInheritanceIsNotUsed = false;
    GlobalNamespace namespace =
        parse(
            """
            /** @constructor */
            var Foo = function() {}

            Foo.prop = prop;
            use(Foo);
            """);

    Name fooProp = namespace.getSlot("Foo.prop");

    assertThat(fooProp.canCollapse()).isFalse();
  }

  @Test
  public void testCanCollapseAliasedInterfaceProperty_ignoringStaticInheritance() {
    GlobalNamespace namespace =
        parse(
            """
            /** @interface */
            var Foo = function() {}

            Foo.prop = prop;
            use(Foo);
            """);

    Name fooProp = namespace.getSlot("Foo.prop");

    // We should still convert Foo.prop -> Foo$prop, even though use(Foo) might read Foo.prop,
    // because Foo is a constructor
    assertThat(fooProp.canCollapse()).isTrue();
  }

  @Test
  public void testCannotCollapseAliasedInterfaceProperty_consideringStaticInheritance() {
    this.assumeStaticInheritanceIsNotUsed = false;
    GlobalNamespace namespace =
        parse(
            """
            /** @interface */
            var Foo = function() {}

            Foo.prop = prop;
            use(Foo);
            """);

    Name fooProp = namespace.getSlot("Foo.prop");
    assertThat(fooProp.canCollapse()).isFalse();
  }

  @Test
  public void testCanCollapseAliasedClassProperty_ignoringStaticInheritance() {
    GlobalNamespace namespace = parse("class Foo {} Foo.prop = prop; use(Foo);");

    Name fooProp = namespace.getSlot("Foo.prop");

    // We should still convert Foo.prop -> Foo$prop, even though use(Foo) might read Foo.prop,
    // because Foo is a constructor
    assertThat(fooProp.canCollapse()).isTrue();
  }

  @Test
  public void testCanCollapseAliasedClassProperty_consideringStaticInheritance() {
    this.assumeStaticInheritanceIsNotUsed = false;
    GlobalNamespace namespace = parse("class Foo {} Foo.prop = prop; use(Foo);");

    Name fooProp = namespace.getSlot("Foo.prop");
    assertThat(fooProp.canCollapse()).isFalse();
  }

  @Test
  public void testCannotCollapseOrInlineDeletedProperty() {
    GlobalNamespace namespace =
        parse(
            """
            const global = window;
            delete global.HTMLElement;
            global.HTMLElement = (class {});
            """);

    Name deletedProp = namespace.getSlot("global.HTMLElement");
    assertThat(deletedProp.canCollapseOrInline()).isEqualTo(Inlinability.DO_NOT_INLINE);
  }

  @Test
  public void testCanCollapse_objectLitProperty_declaredBeforeASpread() {
    GlobalNamespace namespace = parse("var foo = {prop: 0, ...bar}; use(foo.prop);");

    Name fooProp = namespace.getSlot("foo.prop");
    assertThat(fooProp.canCollapse()).isFalse();
  }

  @Test
  public void testGoogProvideName() {
    GlobalNamespace namespace = parse("goog.provide('a'); var a = {};");

    Name a = namespace.getSlot("a");
    assertThat(a).isNotNull();
    assertThat(a.getGlobalSets()).isEqualTo(1);
    // The VAR, not the goog.provide, is considered the 'declaration' of `a`.
    assertNode(a.getDeclaration().getNode().getParent()).hasToken(Token.VAR);
  }

  @Test
  public void testGoogProvideNamespace_noExplicitAssignment() {
    GlobalNamespace namespace = parse("goog.provide('a.b');");

    Name a = namespace.getSlot("a");
    assertThat(a).isNotNull();
    assertThat(a.getGlobalSets()).isEqualTo(0);
    Name ab = namespace.getSlot("a.b");
    assertThat(ab).isNotNull();
    assertThat(ab.getGlobalSets()).isEqualTo(0);
    assertThat(a.getDeclaration()).isNull();
    assertThat(ab.getDeclaration()).isNull();
    assertThat(ab.getParent()).isEqualTo(a);
  }

  @Test
  public void testGoogProvideLongNamespace() {
    GlobalNamespace namespace = parse("goog.provide('a.b.c.d');");

    assertThat(namespace.getSlot("a.b.c.d")).isNotNull();
  }

  @Test
  public void testGoogProvideNamespace_explicitAssignment() {
    GlobalNamespace namespace = parse("goog.provide('a.b'); /** @const */ a.b = {};");

    Name a = namespace.getSlot("a");
    assertThat(a).isNotNull();
    assertThat(a.getGlobalSets()).isEqualTo(0);
    Name ab = namespace.getSlot("a.b");
    assertThat(ab).isNotNull();
    assertThat(ab.getGlobalSets()).isEqualTo(1);
  }

  @Test
  public void testGoogProvideNamespace_assignmentToProperty() {
    GlobalNamespace namespace = parse("goog.provide('a.b'); a.b.Class = class {};");

    Name abClass = namespace.getSlot("a.b.Class");
    assertThat(abClass).isNotNull();
    assertThat(abClass.getGlobalSets()).isEqualTo(1);
    assertThat(abClass.getParent()).isEqualTo(namespace.getSlot("a.b"));
  }

  @Test
  public void testGoogProvideName_multipleProvidesForName() {
    GlobalNamespace namespace = parse("goog.provide('a.b'); goog.provide('a.c');");

    Name a = namespace.getSlot("a");
    assertThat(a).isNotNull();
    assertThat(a.getGlobalSets()).isEqualTo(0);
  }

  @Test
  public void googModuleLevelNamesAreCaptured() {
    GlobalNamespace namespace = parseAndGatherModuleData("goog.module('m'); const x = 0;");
    ModuleMetadata metadata =
        lastCompiler.getModuleMetadataMap().getModulesByGoogNamespace().get("m");
    Name x = namespace.getNameFromModule(metadata, "x");

    assertThat(x).isNotNull();
    assertThat(x.getDeclaration()).isNotNull();
  }

  @Test
  public void googModuleLevelQualifiedNamesAreCaptured() {
    GlobalNamespace namespace =
        parseAndGatherModuleData("goog.module('m'); class Foo {} Foo.Bar = 0;");
    ModuleMetadata metadata =
        lastCompiler.getModuleMetadataMap().getModulesByGoogNamespace().get("m");
    Name x = namespace.getNameFromModule(metadata, "Foo.Bar");

    assertThat(x).isNotNull();
    assertThat(x.getDeclaration()).isNotNull();
  }

  @Test
  public void googModule_containsExports() {
    GlobalNamespace namespace = parseAndGatherModuleData("goog.module('m'); const x = 0;");
    ModuleMetadata metadata =
        lastCompiler.getModuleMetadataMap().getModulesByGoogNamespace().get("m");
    Name exports = namespace.getNameFromModule(metadata, "exports");
    assertThat(exports.getGlobalSets()).isEqualTo(0);
  }

  @Test
  public void googLoadModule_containsExports() {
    GlobalNamespace namespace =
        parseAndGatherModuleData(
            """
            goog.loadModule(function(exports) {
              goog.module('m');
              const x = 0;
              return exports;
            });
            """);
    ModuleMetadata metadata =
        lastCompiler.getModuleMetadataMap().getModulesByGoogNamespace().get("m");
    Name exports = namespace.getNameFromModule(metadata, "exports");
    assertThat(exports.getGlobalSets()).isEqualTo(0);
  }

  @Test
  public void googLoadModule_capturesQualifiedNames() {
    GlobalNamespace namespace =
        parseAndGatherModuleData(
            """
            goog.loadModule(function(exports) {
              goog.module('m');
              class Foo {}
              Foo.Bar = class {};
              return exports;
            });
            """);
    ModuleMetadata metadata =
        lastCompiler.getModuleMetadataMap().getModulesByGoogNamespace().get("m");
    Name foo = namespace.getNameFromModule(metadata, "Foo");
    Name fooBar = namespace.getNameFromModule(metadata, "Foo.Bar");
    assertThat(fooBar.getParent()).isEqualTo(foo);
  }

  @Test
  public void googLoadModule_containsExportsPropertyAssignments() {
    GlobalNamespace namespace =
        parseAndGatherModuleData(
            """
            goog.loadModule(function(exports) {
              goog.module('m');
              exports.Foo = class {};
              return exports;
            });
            """);
    ModuleMetadata metadata =
        lastCompiler.getModuleMetadataMap().getModulesByGoogNamespace().get("m");
    Name exportsFoo = namespace.getNameFromModule(metadata, "exports.Foo");
    assertThat(exportsFoo.getGlobalSets()).isEqualTo(1);
  }

  @Test
  public void googModule_containsExports_explicitAssign() {
    GlobalNamespace namespace =
        parseAndGatherModuleData("goog.module('m'); const x = 0; exports = {x};");
    ModuleMetadata metadata =
        lastCompiler.getModuleMetadataMap().getModulesByGoogNamespace().get("m");
    Name exports = namespace.getNameFromModule(metadata, "exports");
    assertThat(exports.getGlobalSets()).isEqualTo(1);
    assertThat(namespace.getNameFromModule(metadata, "x").getGlobalSets()).isEqualTo(1);
  }

  @Test
  public void assignToGlobalNameInLoadModule_doesNotCreateModuleName() {
    GlobalNamespace namespace =
        parseAndGatherModuleData(
            """
            class Foo {}
            goog.loadModule(function(exports) {
              goog.module('m');
              Foo.Bar = 0
              return exports;
            });
            """);

    ModuleMetadata metadata =
        lastCompiler.getModuleMetadataMap().getModulesByGoogNamespace().get("m");
    assertThat(namespace.getNameFromModule(metadata, "Foo.Bar")).isNull();
    assertThat(namespace.getSlot("Foo.Bar")).isNotNull();
  }

  @Test
  public void moduleLevelNamesAreCaptured_esExportDecl() {
    GlobalNamespace namespace = parseAndGatherModuleData("export const x = 0;");
    ModuleMetadata metadata = lastCompiler.getModuleMetadataMap().getModulesByPath().get("test.js");
    Name x = namespace.getNameFromModule(metadata, "x");

    assertThat(x).isNotNull();
    assertNode(x.getDeclaration().getNode().getParent()).hasToken(Token.CONST);
  }

  @Test
  public void moduleLevelNamesAreCaptured_esExportClassDecl() {
    GlobalNamespace namespace = parseAndGatherModuleData("export class Foo {}");
    ModuleMetadata metadata = lastCompiler.getModuleMetadataMap().getModulesByPath().get("test.js");
    Name x = namespace.getNameFromModule(metadata, "Foo");

    assertThat(x).isNotNull();
    assertNode(x.getDeclaration().getNode().getParent()).hasToken(Token.CLASS);
  }

  @Test
  public void moduleLevelNamesAreCaptured_esExportFunctionDecl() {
    GlobalNamespace namespace = parseAndGatherModuleData("export function fn() {}");
    ModuleMetadata metadata = lastCompiler.getModuleMetadataMap().getModulesByPath().get("test.js");
    Name x = namespace.getNameFromModule(metadata, "fn");

    assertThat(x).isNotNull();
    assertNode(x.getDeclaration().getNode().getParent()).hasToken(Token.FUNCTION);
  }

  @Test
  public void moduleLevelNamesAreCaptured_esExportDefaultFunctionDecl() {
    GlobalNamespace namespace = parseAndGatherModuleData("export default function fn() {}");
    ModuleMetadata metadata = lastCompiler.getModuleMetadataMap().getModulesByPath().get("test.js");
    Name x = namespace.getNameFromModule(metadata, "fn");

    assertThat(x).isNotNull();
    assertNode(x.getDeclaration().getNode().getParent()).hasToken(Token.FUNCTION);
  }

  @Test
  public void esModuleLevelNamesAreCaptured() {
    GlobalNamespace namespace = parseAndGatherModuleData("class Foo {} Foo.Bar = 0; export {Foo};");
    ModuleMetadata metadata = lastCompiler.getModuleMetadataMap().getModulesByPath().get("test.js");
    Name x = namespace.getNameFromModule(metadata, "Foo.Bar");

    assertThat(x).isNotNull();
    assertThat(x.getDeclaration()).isNotNull();
  }

  @Test
  public void getCommonAncestorChunk_returnsDeclarationChunk_whenDeclarationLoadedFirst() {
    JSChunk[] chunks =
        JSChunkGraphBuilder.forChain()
            .addChunk("console.log('base');")
            .addChunk("const parent = {};")
            .addChunk("parent.child = {};")
            .build();

    GlobalNamespace namespace = parse(chunks);
    Name parentName = namespace.getSlot("parent");
    Name childName = namespace.getSlot("parent.child");

    JSChunkGraph chunkGraph = this.lastCompiler.getChunkGraph();
    assertThat(parentName.getDeepestCommonAncestorChunk(chunkGraph)).isEqualTo(chunks[1]);
    assertThat(childName.getDeepestCommonAncestorChunk(chunkGraph)).isEqualTo(chunks[2]);
  }

  @Test
  public void getCommonAncestorChunk_findsCommonAncestorOfSiblingChunks() {
    JSChunk[] chunks =
        JSChunkGraphBuilder.forBush()
            .addChunk("console.log('base');")
            .addChunk("const parent = {};") // parent depends on base
            .addChunk("parent.crossChunk = {};") // depends on parent
            .addChunk(
                "if (parent.crossChunk) { console.log(parent.crossChunk ); }") // depends on parent
            .build();

    GlobalNamespace namespace = parse(chunks);
    Name crossChunkName = namespace.getSlot("parent.crossChunk");

    JSChunkGraph chunkGraph = this.lastCompiler.getChunkGraph();
    assertThat(crossChunkName.getDeepestCommonAncestorChunk(chunkGraph)).isEqualTo(chunks[1]);
    assertThat(crossChunkName.getDeclaration().getChunk()).isEqualTo(chunks[2]);
  }

  // This method exists for testing module metadata lookups.
  private GlobalNamespace parseAndGatherModuleData(String js) {
    CompilerOptions options = getDefaultOptions();
    Compiler compiler = compile(js, options);

    // Disabling transpilation also disables these passes that we need to have run when
    // testing behavior related to module metadata.
    new GatherModuleMetadata(
            compiler, options.getProcessCommonJSModules(), options.getModuleResolutionMode())
        .process(compiler.getExternsRoot(), compiler.getJsRoot());
    new ModuleMapCreator(compiler, compiler.getModuleMetadataMap())
        .process(compiler.getExternsRoot(), compiler.getJsRoot());
    assertThat(compiler.getErrors()).isEmpty();
    this.lastCompiler = compiler;
    return new GlobalNamespace(compiler, compiler.getRoot());
  }

  private GlobalNamespace parse(String js) {
    CompilerOptions options = getDefaultOptions();
    compile(js, options);
    return new GlobalNamespace(this.lastCompiler, this.lastCompiler.getRoot());
  }

  private GlobalNamespace parse(JSChunk[] chunks) {
    CompilerOptions options = getDefaultOptions();
    Compiler compiler = new Compiler();
    var result = compiler.compileChunks(ImmutableList.of(), ImmutableList.copyOf(chunks), options);
    assertThat(compiler.getErrors()).isEmpty();
    assertThat(result.success).isTrue();
    this.lastCompiler = compiler;
    return new GlobalNamespace(this.lastCompiler, this.lastCompiler.getRoot());
  }

  private CompilerOptions getDefaultOptions() {
    CompilerOptions options = new CompilerOptions();
    options.setLanguage(LanguageMode.UNSUPPORTED);
    // Don't optimize, because we want to know how GlobalNamespace responds to the original code
    // in `js`.
    options.setSkipNonTranspilationPasses(true);
    options.setWrapGoogModulesForWhitespaceOnly(false);
    // Test the latest features supported for input and don't transpile, because we want to test how
    // GlobalNamespace deals with the language features actually present in `js`.
    options.setAssumeStaticInheritanceIsNotUsed(assumeStaticInheritanceIsNotUsed);
    return options;
  }

  @CanIgnoreReturnValue
  private Compiler compile(String js, CompilerOptions options) {
    Compiler compiler = new Compiler();
    compiler.compile(SourceFile.fromCode("ex.js", ""), SourceFile.fromCode("test.js", js), options);
    assertThat(compiler.getErrors()).isEmpty();
    this.lastCompiler = compiler;
    return compiler;
  }
}
