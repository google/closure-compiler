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
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractModuleCallback;
import com.google.javascript.jscomp.modules.ModuleMetadataMap;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import java.util.HashSet;
import java.util.Set;
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

  public static final DiagnosticType MISSING_REQUIRE_IN_PROVIDES_FILE =
      DiagnosticType.disabled(
          "JSC_MISSING_REQUIRE_IN_PROVIDES_FILE",
          "''{0}'' references a namespace which was not required by this file.\n"
              + "Please add a goog.require.");

  public static final DiagnosticType MISSING_REQUIRE_TYPE_IN_PROVIDES_FILE =
      DiagnosticType.disabled(
          "JSC_MISSING_REQUIRE_TYPE_IN_PROVIDES_FILE",
          "''{0}'' references a namespace which was not required by this file.\n"
              + "Please add a goog.requireType.");

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
      ModuleMetadata currentFile,
      QualifiedName qualifiedName,
      boolean isStrongReference) {
    if (qualifiedName.isSimple() && templateParamNames.contains(qualifiedName.getRoot())) {
      // This will produce a false negative when the same name is used in both template and
      // non-template capacity in the same file, and a false positive when the `@template` does not
      // precede the reference within the same source file (e.g. an ES5 ctor in a different file).
      return;
    }
    if (qualifiedName.isSimple() && qualifiedName.getRoot().equals("xid")) {
      // Specifically don't report the name 'xid', which is a function that is widely used
      // within Google without an accompanying goog.require, and which makes it hard to roll out
      // this check.
      // TODO(user): fix the remaining code involving xid and remove this workaround.
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
    for (QualifiedName subName = qualifiedName; subName != null; subName = subName.getOwner()) {
      String namespace = subName.join();
      if (namespace.equals("goog.module")) {
        // We must special case `goog.module` because Closure Library provides a namespace with that
        // name, but it's (confusingly) unrelated to the `goog.module` primitive.
        return;
      }

      // Do not report references to a namespace provided in the same file, and do not recurse
      // into parent namespaces either.
      // TODO(tjgq): Also check for these references.
      if (currentFile.googNamespaces().contains(namespace)) {
        return;
      }

      ModuleMetadata requiredFile = moduleByNamespace.get(namespace);
      if (requiredFile == null) {
        continue;
      }

      final DiagnosticType toReport;
      if (currentFile.isModule()) {
        /**
         * In files that represent modules, report a require without an alias the same as a totally
         * missing require.
         */
        toReport = isStrongReference ? MISSING_REQUIRE : MISSING_REQUIRE_TYPE;
      } else if (!hasAcceptableRequire(currentFile, subName, requiredFile, isStrongReference)) {
        /**
         * In files that aren't modules, report a qualified name reference only if there's no
         * require to satisfy it.
         */
        toReport =
            isStrongReference
                ? MISSING_REQUIRE_IN_PROVIDES_FILE
                : MISSING_REQUIRE_TYPE_IN_PROVIDES_FILE;
      } else {
        return;
      }

      t.report(n, toReport, namespace);
      return;
    }
  }

  /**
   * Does `rdep` contain an acceptable require for `namespace` from `dep`?
   *
   * <p>Any require for a parent of `namespace` from `rdep` onto `dep`, which declares that parent,
   * is sufficient. This constraint still ensures correct dependency ordering.
   *
   * <p>We loosen the check in this way because we want to make it easy to migrate multi-provide
   * files into modules. That includes deleting obsolete provides and splitting provides into
   * different files.
   */
  private static boolean hasAcceptableRequire(
      ModuleMetadata rdep, QualifiedName namespace, ModuleMetadata dep, boolean isStrongReference) {
    Set<String> acceptableRequires = rdep.stronglyRequiredGoogNamespaces().elementSet();
    if (!isStrongReference) {
      acceptableRequires =
          Sets.union(acceptableRequires, rdep.weaklyRequiredGoogNamespaces().elementSet());
    }
    acceptableRequires = Sets.intersection(acceptableRequires, dep.googNamespaces().elementSet());

    for (QualifiedName parent = namespace; parent != null; parent = parent.getOwner()) {
      if (acceptableRequires.contains(parent.join())) {
        return true;
      }
    }

    return false;
  }
}
