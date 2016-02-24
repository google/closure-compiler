/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Rewrites "Polymer({})" calls into a form that is suitable for type checking and dead code
 * elimination. Also ensures proper format and types.
 *
 * <p>Only works with Polymer version 0.8 and above.
 *
 * <p>Design and examples: https://github.com/google/closure-compiler/wiki/Polymer-Pass
 *
 * @author jlklein@google.com (Jeremy Klein)
 */
final class PolymerPass extends AbstractPostOrderCallback implements HotSwapCompilerPass {

  static final String VIRTUAL_FILE = "<PolymerPass.java>";

  private final AbstractCompiler compiler;
  private final Map<String, String> tagNameMap;

  private Node polymerElementExterns;
  private Set<String> nativeExternsAdded;
  private ImmutableList<Node> polymerElementProps;
  private GlobalNamespace globalNames;

  PolymerPass(AbstractCompiler compiler) {
    this.compiler = compiler;
    tagNameMap = TagNameToType.getMap();
    nativeExternsAdded = new HashSet<>();
  }

  @Override
  public void process(Node externs, Node root) {
    PolymerPassFindExterns externsCallback = new PolymerPassFindExterns();
    NodeTraversal.traverseEs6(compiler, externs, externsCallback);
    polymerElementExterns = externsCallback.getPolymerElementExterns();
    polymerElementProps = externsCallback.getPolymerElementProps();

    if (polymerElementExterns == null) {
      compiler.report(JSError.make(externs, PolymerPassErrors.POLYMER_MISSING_EXTERNS));
      return;
    }

    globalNames = new GlobalNamespace(compiler, externs, root);

    hotSwapScript(root, null);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverseEs6(compiler, scriptRoot, this);
    PolymerPassSuppressBehaviors suppressBehaviorsCallback =
        new PolymerPassSuppressBehaviors(compiler);
    NodeTraversal.traverseEs6(compiler, scriptRoot, suppressBehaviorsCallback);
  }

  @Override
  public void visit(NodeTraversal traversal, Node node, Node parent) {
    Preconditions.checkState(
        polymerElementExterns != null && polymerElementProps != null && globalNames != null,
        "Cannot call visit() before process()");
    if (isPolymerCall(node)) {
      rewriteClassDefinition(node, parent, traversal);
    }
  }

  private void rewriteClassDefinition(Node node, Node parent, NodeTraversal traversal) {
    Node grandparent = parent.getParent();
    if (grandparent.isConst()) {
      compiler.report(JSError.make(node, PolymerPassErrors.POLYMER_INVALID_DECLARATION));
      return;
    }
    PolymerClassDefinition def = PolymerClassDefinition.extractFromCallNode(
        node, compiler, globalNames);
    if (def != null) {
      if (def.nativeBaseElement != null) {
        appendPolymerElementExterns(def);
      }
      PolymerClassRewriter rewriter = new PolymerClassRewriter(compiler, polymerElementExterns);
      if (NodeUtil.isNameDeclaration(grandparent) || parent.isAssign()) {
        rewriter.rewritePolymerClass(grandparent, def, traversal.getScope().isGlobal());
      } else {
        rewriter.rewritePolymerClass(parent, def, traversal.getScope().isGlobal());
      }
    }
  }

  /**
   * Duplicates the PolymerElement externs with a different element base class if needed.
   * For example, if the base class is HTMLInputElement, then a class PolymerInputElement will be
   * added. If the element does not extend a native HTML element, this method is a no-op.
   */
  private void appendPolymerElementExterns(final PolymerClassDefinition def) {
    if (!nativeExternsAdded.add(def.nativeBaseElement)) {
      return;
    }

    Node block = IR.block();

    Node baseExterns = polymerElementExterns.cloneTree();
    String polymerElementType = PolymerPassStaticUtils.getPolymerElementType(def);
    baseExterns.getFirstChild().setString(polymerElementType);

    String elementType = tagNameMap.get(def.nativeBaseElement);
    JSTypeExpression elementBaseType =
        new JSTypeExpression(new Node(Token.BANG, IR.string(elementType)), VIRTUAL_FILE);
    JSDocInfoBuilder baseDocs = JSDocInfoBuilder.copyFrom(baseExterns.getJSDocInfo());
    baseDocs.changeBaseType(elementBaseType);
    baseExterns.setJSDocInfo(baseDocs.build());
    block.addChildToBack(baseExterns);

    for (Node baseProp : polymerElementProps) {
      Node newProp = baseProp.cloneTree();
      Node newPropRootName =
          NodeUtil.getRootOfQualifiedName(newProp.getFirstFirstChild());
      newPropRootName.setString(polymerElementType);
      block.addChildToBack(newProp);
    }

    block.useSourceInfoIfMissingFromForTree(polymerElementExterns);

    Node parent = polymerElementExterns.getParent();
    Node stmts = block.removeChildren();
    parent.addChildrenAfter(stmts, polymerElementExterns);

    compiler.reportCodeChange();
  }

  /**
   * @return Whether the call represents a call to the Polymer function.
   */
  @VisibleForTesting
  public static boolean isPolymerCall(Node value) {
    return value != null && value.isCall() && value.getFirstChild().matchesQualifiedName("Polymer");
  }

  /**
   * Any member of a Polymer element or Behavior. These can be functions, properties, etc.
   */
  static class MemberDefinition {
    /** Any {@link JSDocInfo} tied to this member. */
    final JSDocInfo info;

    /** Name {@link Node} for the definition of this member. */
    final Node name;

    /** Value {@link Node} (RHS) for the definition of this member. */
    final Node value;

    MemberDefinition(JSDocInfo info, Node name, Node value) {
      this.info = info;
      this.name = name;
      this.value = value;
    }
  }
}
