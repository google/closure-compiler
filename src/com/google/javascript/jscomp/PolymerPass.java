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

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_INVALID_EXTENDS;
import static com.google.javascript.jscomp.PolymerPassErrors.POLYMER_MISSING_EXTERNS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.NodeTraversal.ExternsSkippingCallback;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.jscomp.parsing.parser.FeatureSet.Feature;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.HashSet;
import java.util.Set;

/**
 * Rewrites "Polymer({})" calls into a form that is suitable for type checking and dead code
 * elimination. Also ensures proper format and types.
 *
 * <p>Only works with Polymer version 0.8 and above.
 *
 * <p>Design and examples: https://github.com/google/closure-compiler/wiki/Polymer-Pass
 */
final class PolymerPass extends ExternsSkippingCallback implements HotSwapCompilerPass {
  private static final String VIRTUAL_FILE = "<PolymerPass.java>";

  private final AbstractCompiler compiler;
  private final ImmutableMap<String, String> tagNameMap;
  private final int polymerVersion;
  private final PolymerExportPolicy polymerExportPolicy;
  private final boolean propertyRenamingEnabled;

  private Node polymerElementExterns;
  private Node externsInsertionRef = null;
  private final Set<String> nativeExternsAdded;
  private ImmutableList<Node> polymerElementProps;
  private GlobalNamespace globalNames;
  private PolymerBehaviorExtractor behaviorExtractor;
  private boolean warnedPolymer1ExternsMissing = false;
  private boolean propertySinkExternInjected = false;

  PolymerPass(
      AbstractCompiler compiler,
      Integer polymerVersion,
      PolymerExportPolicy polymerExportPolicy,
      boolean propertyRenamingEnabled) {
    checkArgument(
        polymerVersion == null || polymerVersion == 1 || polymerVersion == 2,
        "Invalid Polymer version:",
        polymerVersion);
    this.compiler = compiler;
    tagNameMap = TagNameToType.getMap();
    nativeExternsAdded = new HashSet<>();
    this.polymerVersion = polymerVersion == null ? 1 : polymerVersion;
    this.polymerExportPolicy =
        polymerExportPolicy == null ? PolymerExportPolicy.LEGACY : polymerExportPolicy;
    this.propertyRenamingEnabled = propertyRenamingEnabled;
  }

  @Override
  public void process(Node externs, Node root) {
    PolymerPassFindExterns externsCallback = new PolymerPassFindExterns();
    NodeTraversal.traverse(compiler, externs, externsCallback);
    polymerElementExterns = externsCallback.getPolymerElementExterns();
    polymerElementProps = externsCallback.getPolymerElementProps();

    if (polymerVersion == 1 && polymerElementExterns == null) {
      this.warnedPolymer1ExternsMissing = true;
      compiler.report(JSError.make(POLYMER_MISSING_EXTERNS));
      return;
    }

    if (polymerVersion > 1 && propertyRenamingEnabled) {
      compiler.ensureLibraryInjected("util/reflectobject", false);
    }

    globalNames = new GlobalNamespace(compiler, externs, root);
    behaviorExtractor =
        new PolymerBehaviorExtractor(
            compiler, globalNames, compiler.getModuleMetadataMap(), compiler.getModuleMap());

    Node externsAndJsRoot = root.getParent();
    hotSwapScript(externsAndJsRoot, null);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
    PolymerPassSuppressBehaviors suppressBehaviorsCallback =
        new PolymerPassSuppressBehaviors(compiler);
    NodeTraversal.traverse(compiler, scriptRoot, suppressBehaviorsCallback);
  }

  @Override
  public void visit(NodeTraversal traversal, Node node, Node parent) {
    checkNotNull(globalNames, "Cannot call visit() before process()");

    if (PolymerPassStaticUtils.isPolymerCall(node)) {
      if (polymerElementExterns != null) {
        rewritePolymer1ClassDefinition(node, parent, traversal);
      } else if (!warnedPolymer1ExternsMissing) {
        compiler.report(JSError.make(node, POLYMER_MISSING_EXTERNS));
        warnedPolymer1ExternsMissing = true;
      }
    } else if (PolymerPassStaticUtils.isPolymerClass(node)) {
      rewritePolymer2ClassDefinition(node, traversal);
    }
  }

  private ModuleMetadata getModuleMetadata(NodeTraversal traversal) {
    Node script = traversal.getCurrentScript();
    if (script != null && script.getFirstChild().isModuleBody()) {
      return ModuleImportResolver.getModuleFromScopeRoot(
              compiler.getModuleMap(), compiler, script.getFirstChild())
          .metadata();
    }
    // Check for bundled goog.loadModule calls
    Node scopeRoot = traversal.getScopeRoot();
    if (NodeUtil.isBundledGoogModuleScopeRoot(scopeRoot)) {
      return ModuleImportResolver.getModuleFromScopeRoot(
              compiler.getModuleMap(), compiler, scopeRoot)
          .metadata();
    }
    return null;
  }

  /** Polymer 1.x and Polymer 2 Legacy Element Definitions */
  private void rewritePolymer1ClassDefinition(Node node, Node parent, NodeTraversal traversal) {
    Node grandparent = parent.getParent();
    if (grandparent.isConst()) {
      grandparent.setToken(Token.LET);
      Node scriptNode = traversal.getCurrentScript();
      if (scriptNode != null) {
        NodeUtil.addFeatureToScript(scriptNode, Feature.LET_DECLARATIONS);
      }
      traversal.reportCodeChange();
    }
    if (NodeUtil.isNameDeclaration(grandparent) && grandparent.getParent().isExport()) {
      normalizePolymerExport(grandparent, grandparent.getParent());
      traversal.reportCodeChange();
    }
    PolymerClassDefinition def =
        PolymerClassDefinition.extractFromCallNode(
            node, compiler, getModuleMetadata(traversal), behaviorExtractor);
    if (def != null) {
      if (def.nativeBaseElement != null) {
        appendPolymerElementExterns(def);
      }
      PolymerClassRewriter rewriter =
          new PolymerClassRewriter(
              compiler,
              getExtensInsertionRef(),
              polymerVersion,
              polymerExportPolicy,
              this.propertyRenamingEnabled);
      rewriter.rewritePolymerCall(def, traversal);
    }
  }

  /** Polymer 2.x Class Nodes */
  private void rewritePolymer2ClassDefinition(Node node, NodeTraversal traversal) {
    PolymerClassDefinition def =
        PolymerClassDefinition.extractFromClassNode(node, compiler, globalNames);
    if (def != null) {
      PolymerClassRewriter rewriter =
          new PolymerClassRewriter(
              compiler,
              getExtensInsertionRef(),
              polymerVersion,
              polymerExportPolicy,
              this.propertyRenamingEnabled);
      rewriter.propertySinkExternInjected = propertySinkExternInjected;
      rewriter.rewritePolymerClassDeclaration(node, traversal, def);
      propertySinkExternInjected = rewriter.propertySinkExternInjected;
    }
  }

  /** Replaces `export let Element = ...` with `let Element = ...; export {Element};` */
  private void normalizePolymerExport(Node nameDecl, Node export) {
    Node block = export.getParent();
    Node name = nameDecl.getFirstChild();
    block.addChildBefore(nameDecl.detach(), export);

    Node exportSpec = new Node(Token.EXPORT_SPEC);
    exportSpec.addChildToFront(name.cloneNode());
    exportSpec.addChildToFront(name.cloneNode());
    export.addChildToFront(new Node(Token.EXPORT_SPECS, exportSpec).srcrefTree(export));
  }

  private Node getExtensInsertionRef() {
    if (this.polymerElementExterns != null) {
      return this.polymerElementExterns;
    }

    if (this.externsInsertionRef == null) {
      this.externsInsertionRef = compiler.getSynthesizedExternsInputAtEnd().getAstRoot(compiler);
    }

    return this.externsInsertionRef;
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
    if (elementType == null) {
      compiler.report(JSError.make(def.descriptor, POLYMER_INVALID_EXTENDS, def.nativeBaseElement));
      return;
    }
    JSTypeExpression elementBaseType =
        new JSTypeExpression(
            new Node(Token.BANG, IR.string(elementType).srcrefTree(polymerElementExterns)),
            VIRTUAL_FILE);
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

    compiler.reportChangeToEnclosingScope(stmts);
  }

  /** Any member of a Polymer element or Behavior. These can be functions, properties, etc. */
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

    @Override
    public String toString() {
      return toStringHelper(this).add("name", name).add("value", value).toString();
    }
  }
}
