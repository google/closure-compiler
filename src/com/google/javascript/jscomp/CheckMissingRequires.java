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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractModuleCallback;
import com.google.javascript.jscomp.modules.ModuleMetadataMap;
import com.google.javascript.jscomp.modules.ModuleMetadataMap.ModuleMetadata;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** A pass to detect references to fully qualified Closure namespaces. */
public class CheckMissingRequires extends AbstractModuleCallback implements CompilerPass {

  // NOTE: "references a .*namespace" is recognized by tooling.
  public static final DiagnosticType MISSING_REQUIRE =
      DiagnosticType.warning(
          "JSC_MISSING_REQUIRE",
          "''{0}'' references a fully qualified namespace, which is disallowed by the style"
              + " guide.\nPlease add a goog.require, assign or destructure it into an alias, and "
              + "use the alias instead.");

  public static final DiagnosticType MISSING_REQUIRE_TYPE =
      DiagnosticType.disabled(
          "JSC_MISSING_REQUIRE_TYPE",
          "''{0}'' references a fully qualified namespace, which is disallowed by the style"
              + " guide.\nPlease add a goog.requireType, assign or destructure it into an alias, "
              + "and use the alias instead.");

  public static final DiagnosticType INCORRECT_NAMESPACE_ALIAS_REQUIRE =
      DiagnosticType.disabled(
          "JSC_INCORRECT_NAMESPACE_ALIAS_REQUIRE",
          "''{0}'' is its own namespace.\nPlease add a separate goog.require and "
              + "use that alias instead.");

  public static final DiagnosticType INCORRECT_NAMESPACE_ALIAS_REQUIRE_TYPE =
      DiagnosticType.disabled(
          "JSC_INCORRECT_NAMESPACE_ALIAS_REQUIRE_TYPE",
          "''{0}'' is its own namespace.\nPlease add a separate goog.requireType and "
              + "use that alias instead.");

  public static final DiagnosticType INDIRECT_NAMESPACE_REF_REQUIRE =
      DiagnosticType.disabled(
          "JSC_INDIRECT_NAMESPACE_REF_REQUIRE",
          "''{0}'' should have its own goog.require.\nPlease add a separate goog.require and "
              + "use that alias instead.");

  public static final DiagnosticType INDIRECT_NAMESPACE_REF_REQUIRE_TYPE =
      DiagnosticType.disabled(
          "JSC_INDIRECT_NAMESPACE_REF_REQUIRE_TYPE",
          "''{0}'' should have its own goog.requireType.\n"
              + "Please add a separate goog.requireType and use that alias instead.");

  public static final DiagnosticType MISSING_REQUIRE_IN_PROVIDES_FILE =
      DiagnosticType.warning(
          "JSC_MISSING_REQUIRE_IN_PROVIDES_FILE",
          "''{0}'' references a namespace which was not required by this file.\n"
              + "Please add a goog.require.");

  public static final DiagnosticType MISSING_REQUIRE_TYPE_IN_PROVIDES_FILE =
      DiagnosticType.disabled(
          "JSC_MISSING_REQUIRE_TYPE_IN_PROVIDES_FILE",
          "''{0}'' references a namespace which was not required by this file.\n"
              + "Please add a goog.requireType.");

  /** The set of template parameter names found so far in the file currently being checked. */
  private final LinkedHashSet<String> templateParamNames = new LinkedHashSet<>();

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
    visitNode(t, n, currentModule);
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
    if (!n.getParent().isGetProp() && n.isQualifiedName()) {
      QualifiedName qualifiedName = n.getQualifiedNameObject();
      String root = qualifiedName.getRoot();
      if (root.equals("this") || root.equals("super")) {
        return;
      }
      visitQualifiedName(t, n, currentModule, qualifiedName, /* isStrongReference= */ true);
    }

    if (n.isName() && !n.getString().isEmpty()) {
      visitMaybeDeclaration(t, n, currentModule);
    }
  }

  private void visitJsDocInfo(NodeTraversal t, ModuleMetadata currentModule, JSDocInfo info) {
    // Collect template parameter names before checking, so that annotations on the same node that
    // reference the name are excluded from the check.
    templateParamNames.addAll(info.getTemplateTypeNames());
    templateParamNames.addAll(info.getTypeTransformations().keySet());
    if (info.hasType()) {
      visitJsDocExpr(t, currentModule, info.getType(), /* isStrongReference= */ false);
    }
    for (String param : info.getParameterNames()) {
      if (info.hasParameterType(param)) {
        visitJsDocExpr(
            t, currentModule, info.getParameterType(param), /* isStrongReference= */ false);
      }
    }
    if (info.hasReturnType()) {
      visitJsDocExpr(t, currentModule, info.getReturnType(), /* isStrongReference= */ false);
    }
    if (info.hasEnumParameterType()) {
      visitJsDocExpr(t, currentModule, info.getEnumParameterType(), /* isStrongReference= */ false);
    }
    if (info.hasTypedefType()) {
      visitJsDocExpr(t, currentModule, info.getTypedefType(), /* isStrongReference= */ false);
    }
    if (info.hasThisType()) {
      visitJsDocExpr(t, currentModule, info.getThisType(), /* isStrongReference= */ false);
    }
    if (info.hasBaseType()) {
      // Note that `@extends` requires a goog.require, not a goog.requireType.
      visitJsDocExpr(t, currentModule, info.getBaseType(), /* isStrongReference= */ true);
    }
    for (JSTypeExpression expr : info.getExtendedInterfaces()) {
      // Note that `@extends` requires a goog.require, not a goog.requireType.
      visitJsDocExpr(t, currentModule, expr, /* isStrongReference= */ true);
    }
    for (JSTypeExpression expr : info.getImplementedInterfaces()) {
      // Note that `@implements` requires a goog.require, not a goog.requireType.
      visitJsDocExpr(t, currentModule, expr, /* isStrongReference= */ true);
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

  private void visitMaybeDeclaration(NodeTraversal t, Node n, ModuleMetadata currentFile) {
    if (!currentFile.isModule()) {
      // This check only makes sense in goog.module files.
      return;
    }

    // Currently, tsickle can introduce these
    // TODO(b/333952917): Remove this once tsickle is fixed.
    if (isTypeScriptSource(n)) {
      return;
    }

    checkState(n.isName());
    String rootName = n.getString();
    Var var = t.getScope().getVar(rootName);
    if (var == null) {
      return;
    }
    Var declaration = var.getDeclaration();
    if (declaration == null || declaration.getNode() != n) {
      // Not a declaration reference
      return;
    }

    if (var.getScope().isLocal()) {
      var require = NodeUtil.getGoogRequireInfo(var);
      if (require == null) {
        // If it refers to a local variable, it's legal.
        return;
      }

      // Not destructured.
      if (require.property() == null) {
        return;
      }

      // TODO: validate that this is the correct thing to do for wiz
      if (require == null || require.namespace().equals("wiz")) {
        return;
      }

      ModuleMetadata requiredFile = moduleByNamespace.get(require.namespace());
      if (requiredFile == null || !requiredFile.hasLegacyGoogNamespaces()) {
        // If we are importing from a non-legacy namespace than any property reference is valid,
        // as it is assumed that the property reference is exported intentionally.

        // NOTE There are corner cases if a legacy namespace object is explicitly reexported
        // but (1) it isn't clear what the "correct" behavior is and (2) we don't have the
        // information here in order to validate it as we would need to the object shapes.
        return;
      }

      String reference = require.namespace() + '.' + require.property();
      QualifiedName qualifiedName = QualifiedName.of(reference);

      // Do not report references to a namespace provided in the same file, and do not recurse
      // into parent namespaces either.
      if (!currentFile.googNamespaces().contains(reference)) {
        // Try to find an alternate file to import from if, if we construct a namespace using
        // the destructured property name.
        ModuleMetadata alternateFile = moduleByNamespace.get(reference);

        // If the alternate isn't a legacy namespace there there can't be accidental references
        // through namespace destructuring, otherwise...
        if (alternateFile != null && alternateFile.hasLegacyGoogNamespaces()) {
          if (!hasAcceptableRequire(
              currentFile, qualifiedName, alternateFile, require.isStrongRequire())) {
            // TODO: report on the node that needs to be removed, include the namespace that needs
            // to be added.
            final DiagnosticType toReport =
                require.isStrongRequire()
                    ? INCORRECT_NAMESPACE_ALIAS_REQUIRE
                    : INCORRECT_NAMESPACE_ALIAS_REQUIRE_TYPE;
            t.report(n, toReport, reference);
          }
        }
      }
    }
  }

  private boolean isTypeScriptSource(Node n) {
    return n.getStaticSourceFile().isTypeScriptSource();
  }

  private void visitQualifiedName(
      NodeTraversal t,
      Node n,
      ModuleMetadata currentFile,
      QualifiedName qualifiedName,
      boolean isStrongReference) {

    String rootName = qualifiedName.getRoot();
    if (qualifiedName.isSimple()) {
      if (templateParamNames.contains(rootName)) {
        // This will produce a false negative when the same name is used in both template and
        // non-template capacity in the same file, and a false positive when the `@template` does
        // not precede the reference within the same source file (e.g. an ES5 ctor in a different
        // file).
        return;
      }
      if (rootName.equals("xid")) {
        // TODO(b/160167649): Decide if we should report `xid` which initially was too common to
        // fix.
        return;
      }
    }

    if (NodeUtil.isDeclarationLValue(n)) {
      // NOTE: maybe do something here.
      return;
    }

    Var var = t.getScope().getVar(rootName);
    if (var != null && var.getScope().isLocal()) {
      // Currently, tsickle can introduce these
      // TODO(b/333952917): Remove this once tsickle is fixed.
      if (isTypeScriptSource(n)) {
        return;
      }

      if (!currentFile.isModule()) {
        // Don't worry about aliases outside of module files for now.
        return;
      }

      // The qualified name *is* the root name if it is "simple"
      if (qualifiedName.isSimple()) {
        // It is explicitly imported and we have already validated the import in
        // `visitMaybeDeclaration`
        return;
      }

      NodeUtil.GoogRequire require = NodeUtil.getGoogRequireInfo(var);
      // TODO: validate that this is the correct thing to do
      if (require == null || require.namespace().equals("wiz")) {
        // It is a local name, not an import.
        // NOTE: this *could be a local alias* of an imported name
        //   but at some point the only real fix is to not use `goog.provide`
        //   or `goog.module.declareLegacyNamespace` because it is always
        //   possible to obfuscate the use of the namespace.
        return;
      }

      ModuleMetadata originalRequiredFile = moduleByNamespace.get(require.namespace());
      if (originalRequiredFile == null || !originalRequiredFile.hasLegacyGoogNamespaces()) {
        // We trust the import from a non-legacy module, as they should not overlap.
        return;
      }

      // Verify namespace usage
      QualifiedName normalizeQualifiedName = normalizeQualifiedName(qualifiedName, require);

      // TESTCASES, where A.B should not be used through A:
      // checked here
      // X  const A = require('A'); ref(A.B);  // error, missing require A.B
      // X  const A = require('A'); const B = require('B');   ref(A.B);  // error, incorrect ref
      // checked in declaration
      // X  const {B} = require('A');  // error bad import
      //

      // Look for the longest prefix match against a provided namespace.
      for (QualifiedName subName = normalizeQualifiedName;
          subName != null;
          subName = subName.getOwner()) {
        String namespace = subName.join();
        if (isAllowedNamespace(currentFile, namespace)) {
          return;
        }

        ModuleMetadata requiredFile = moduleByNamespace.get(namespace);
        if (requiredFile == null) {
          // Not a known namespace check the parent
          continue;
        }

        // TODO: report on the node that needs rewritten, include the namespace that needs
        // to be added:
        // Autofix: add the require if necessary, rewrite to be the full namespace, and let fixjs
        // fix
        // it up. Write a different message if the namespace is already imported vs missing.

        if (!hasAcceptableRequire(currentFile, subName, requiredFile, isStrongReference)
            || originalRequiredFile != requiredFile) {

          // `originalRequiredFile != requiredFile` then the file is being referenced through
          // the wrong namespace even though it is being `goog.require`d in the file.

          DiagnosticType toReport =
              isStrongReference
                  ? INDIRECT_NAMESPACE_REF_REQUIRE
                  : INDIRECT_NAMESPACE_REF_REQUIRE_TYPE;
          t.report(n, toReport, namespace);
        }

        // We found the imported namespace: done
        return;
      }

      return;
    }

    // Look for the longest prefix match against a provided namespace.
    for (QualifiedName subName = qualifiedName; subName != null; subName = subName.getOwner()) {
      String namespace = subName.join();
      if (isAllowedNamespace(currentFile, namespace)) {
        return;
      }

      ModuleMetadata requiredFile = moduleByNamespace.get(namespace);
      if (requiredFile == null) {
        // Not a known namespace check the parent
        continue;
      }

      final DiagnosticType toReport;
      if (currentFile.isModule()) {
        /*
         * In files that represent modules, report a require without an alias the same as a totally
         * missing require.
         */
        toReport = isStrongReference ? MISSING_REQUIRE : MISSING_REQUIRE_TYPE;
      } else if (!hasAcceptableRequire(currentFile, subName, requiredFile, isStrongReference)) {
        /*
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

  static Node getRootNode(Node n) {
    while (n.isGetProp()) {
      n = n.getFirstChild();
    }
    return n;
  }

  /**
   * Given a qualified name with local root name and a import that defines that name, transform that
   * name so that it is a fully qualified name.
   */
  private QualifiedName normalizeQualifiedName(
      QualifiedName qualifiedName, NodeUtil.GoogRequire imported) {
    QualifiedName newQName = QualifiedName.of(imported.namespace());

    if (imported.property() != null) {
      newQName = newQName.getprop(imported.property());
    }

    var components = qualifiedName.components().iterator();
    components.next(); // skip the root
    while (components.hasNext()) {
      String component = components.next();
      newQName = newQName.getprop(component);
    }
    // Verify namespace usage
    return newQName;
  }

  boolean isAllowedNamespace(ModuleMetadata currentFile, String namespace) {
    if (namespace.equals("goog.module")) {
      // We must special case `goog.module` because Closure Library provides a namespace with that
      // name, but it's (confusingly) unrelated to the `goog.module` primitive.
      return true;
    }

    // Do not report references to a namespace provided in the same file, and do not recurse
    // into parent namespaces either.
    // TODO(tjgq): Also check for these references.
    if (currentFile.googNamespaces().contains(namespace)) {
      return true;
    }

    return false;
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
