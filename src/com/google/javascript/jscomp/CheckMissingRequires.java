/*
 * Copyright 2020 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.NodeTraversal.AbstractModuleCallback;
import com.google.javascript.jscomp.modules.ModuleMetadataMap;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import java.util.HashSet;
import javax.annotation.Nullable;

/** A pass to detect references to fully qualified Closure namespaces. */
public class CheckMissingRequires extends AbstractModuleCallback implements CompilerPass {

  public static final DiagnosticType MISSING_REQUIRE =
      DiagnosticType.warning(
          "JSC_MISSING_REQUIRE",
          "''{0}'' references a fully qualified namespace, which is disallowed by the style"
              + " guide.\nPlease add a goog.require, assign or destructure it into an alias, and "
              + "use the alias instead.");

  public static final DiagnosticType MISSING_REQUIRE_TYPE =
      DiagnosticType.warning(
          "JSC_MISSING_REQUIRE_TYPE",
          "''{0}'' references a fully qualified namespace, which is disallowed by the style"
              + " guide.\nPlease add a goog.requireType, assign or destructure it into an alias, "
              + "and use the alias instead.");

  /** The set of template parameter names found so far in the file currently being checked. */
  private final HashSet<String> templateParamNames = new HashSet<>();

  /** The mapping from Closure namespace into the module that provides it. */
  private final ImmutableMap<String, ModuleMetadata> moduleByNamespace;

  public CheckMissingRequires(AbstractCompiler compiler, ModuleMetadataMap moduleMetadataMap) {
    super(compiler, moduleMetadataMap);
    this.moduleByNamespace = moduleMetadataMap.getModulesByGoogNamespace();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public boolean shouldTraverse(
      NodeTraversal t, Node n, @Nullable ModuleMetadata currentModule, Node scopeRoot) {
    if (currentModule == null) {
      return true;
    }
    if (n == currentModule.rootNode() && !currentModule.isGoogModule()) {
      // Only check inside goog.module files.
      // TODO(tjgq): Extend the check to ES modules that interop with Closure.
      return false;
    }
    // Traverse nodes in preorder to collect `@template` parameter names before their use.
    visitNode(t, n, checkNotNull(currentModule));
    return true;
  }

  @Override
  public void visit(
      NodeTraversal t, Node n, @Nullable ModuleMetadata currentModule, @Nullable Node scopeRoot) {
    if (currentModule != null && n == currentModule.rootNode()) {
      // For this pass, template parameter names are only meaningful inside the file defining them.
      templateParamNames.clear();
    }
  }

  private void visitNode(NodeTraversal t, Node n, ModuleMetadata currentModule) {
    JSDocInfo info = n.getJSDocInfo();
    if (info != null) {
      visitJsDocInfo(t, currentModule, info);
    }
    if (n.isQualifiedName() && !n.getParent().isGetProp()) {
      QualifiedName qualifiedName = n.getQualifiedNameObject();
      String root = qualifiedName.getRoot();
      if (root.equals("this") || root.equals("super")) {
        return;
      }
      visitQualifiedName(
          t, n, currentModule, n.getQualifiedNameObject(), /* isStrongReference= */ true);
    }
  }

  private void visitJsDocInfo(NodeTraversal t, ModuleMetadata currentModule, JSDocInfo info) {
    // Collect template parameter names before checking, so that annotations on the same node that
    // reference the name are excluded from the check.
    templateParamNames.addAll(info.getTemplateTypeNames());
    templateParamNames.addAll(info.getTypeTransformations().keySet());
    if (info.hasType()) {
      visitJsDocExpr(t, currentModule, info.getType(), /* isStrongReference */ false);
    }
    for (String param : info.getParameterNames()) {
      if (info.hasParameterType(param)) {
        visitJsDocExpr(
            t, currentModule, info.getParameterType(param), /* isStrongReference=*/ false);
      }
    }
    if (info.hasReturnType()) {
      visitJsDocExpr(t, currentModule, info.getReturnType(), /* isStrongReference=*/ false);
    }
    if (info.hasEnumParameterType()) {
      visitJsDocExpr(t, currentModule, info.getEnumParameterType(), /* isStrongReference=*/ false);
    }
    if (info.hasTypedefType()) {
      visitJsDocExpr(t, currentModule, info.getTypedefType(), /* isStrongReference=*/ false);
    }
    if (info.hasThisType()) {
      visitJsDocExpr(t, currentModule, info.getThisType(), /* isStrongReference=*/ false);
    }
    if (info.hasBaseType()) {
      // Note that `@extends` requires a goog.require, not a goog.requireType.
      visitJsDocExpr(t, currentModule, info.getBaseType(), /* isStrongReference=*/ true);
    }
    for (JSTypeExpression expr : info.getExtendedInterfaces()) {
      // Note that `@extends` requires a goog.require, not a goog.requireType.
      visitJsDocExpr(t, currentModule, expr, /* isStrongReference=*/ true);
    }
    for (JSTypeExpression expr : info.getImplementedInterfaces()) {
      // Note that `@implements` requires a goog.require, not a goog.requireType.
      visitJsDocExpr(t, currentModule, expr, /* isStrongReference=*/ true);
    }
  }

  private void visitJsDocExpr(
      NodeTraversal t,
      ModuleMetadata currentModule,
      JSTypeExpression expr,
      boolean isStrongReference) {
    for (Node typeNode : expr.getAllTypeNodes()) {
      visitQualifiedName(
          t, typeNode, currentModule, QualifiedName.of(typeNode.getString()), isStrongReference);
    }
  }

  private void visitQualifiedName(
      NodeTraversal t,
      Node n,
      ModuleMetadata currentModule,
      QualifiedName qualifiedName,
      boolean isStrongReference) {
    if (qualifiedName.isSimple() && templateParamNames.contains(qualifiedName.getRoot())) {
      // This will produce a false negative when the same name is used in both template and
      // non-template capacity in the same file, and a false positive when the `@template` does not
      // precede the reference within the same source file (e.g. an ES5 ctor in a different file).
      return;
    }
    Var var = t.getScope().getVar(qualifiedName.getRoot());
    if (var != null && var.getScope().isLocal()) {
      // If the name refers to a nonexisting variable, the error will be caught elsewhere.
      // If it refers to a local variable, it's legal. Note that this includes variables introduced
      // by the aliasing and destructuring forms of `goog.require` and `goog.requireType`.
      return;
    }
    // Look for the longest prefix match against a provided namespace.
    while (qualifiedName != null) {
      String namespace = qualifiedName.join();
      if (namespace.equals("goog.module")) {
        // We must special case `goog.module` because Closure Library provides a namespace with that
        // name, but it's (confusingly) unrelated to the `goog.module` primitive.
        return;
      }
      ModuleMetadata module = moduleByNamespace.get(namespace);
      if (module != null) {
        // Do not report references to a namespace provided in the same file, but do not recurse
        // into parent namespaces either.
        // TODO(tjgq): Also check for these references.
        if (module.rootNode() != currentModule.rootNode()) {
          t.report(n, isStrongReference ? MISSING_REQUIRE : MISSING_REQUIRE_TYPE, namespace);
        }
        return;
      }
      qualifiedName = qualifiedName.getOwner();
    }
  }
}
