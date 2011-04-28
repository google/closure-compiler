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

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Checks references to undefined properties of global variables.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class CheckGlobalNames implements CompilerPass {

  private final AbstractCompiler compiler;
  private final CheckLevel level;

  private GlobalNamespace namespace = null;

  // Warnings
  static final DiagnosticType UNDEFINED_NAME_WARNING = DiagnosticType.warning(
      "JSC_UNDEFINED_NAME",
      "{0} is never defined");

  static final DiagnosticType NAME_DEFINED_LATE_WARNING =
      DiagnosticType.warning(
          "JSC_NAME_DEFINED_LATE",
          "{0} is not defined yet, so properties cannot be defined on it");

  static final DiagnosticType STRICT_MODULE_DEP_QNAME =
      DiagnosticType.disabled(
          "JSC_STRICT_MODULE_DEP_QNAME",
          "module {0} cannot reference {2}, defined in " +
          "module {1}");

  /**
   * Creates a pass to check global name references at the given warning level.
   */
  CheckGlobalNames(AbstractCompiler compiler, CheckLevel level) {
    this.compiler = compiler;
    this.level = level;
  }

  /**
   * Injects a pre-computed global namespace, so that the same namespace
   * can be re-used for multiple check passes. Returns this for easy chaining.
   */
  CheckGlobalNames injectNamespace(GlobalNamespace namespace) {
    this.namespace = namespace;
    return this;
  }

  public void process(Node externs, Node root) {
    // TODO(nicksantos): Let CollapseProperties and CheckGlobalNames
    // share a namespace.
    if (namespace == null) {
      namespace = new GlobalNamespace(compiler, root);
    }

    for (Name name : namespace.getNameForest()) {
      checkDescendantNames(name, name.globalSets + name.localSets > 0);
    }
  }

  /**
   * Checks to make sure all the descendants of a name are defined if they
   * are referenced.
   *
   * @param name A global name.
   * @param nameIsDefined If true, {@code name} is defined. Otherwise, it's
   *    undefined, and any references to descendant names should emit warnings.
   */
  private void checkDescendantNames(Name name, boolean nameIsDefined) {
    if (name.props != null) {
      for (Name prop : name.props) {
        // if the ancestor of a property is not defined, then we should emit
        // warnings for all references to the property.
        boolean propIsDefined = false;
        if (nameIsDefined) {
          // if the ancestor of a property is defined, then let's check that
          // the property is also explicitly defined if it needs to be.
          propIsDefined = (!propertyMustBeInitializedByFullName(prop) ||
              prop.globalSets + prop.localSets > 0);
        }

        validateName(prop, propIsDefined);
        checkDescendantNames(prop, propIsDefined);
      }
    }
  }

  private void validateName(Name name, boolean isDefined) {
    // If the name is not defined, emit warnings for each reference. While
    // we're looking through each reference, check all the module dependencies.
    Ref declaration = name.declaration;
    Name parent = name.parent;
    if (isDefined &&
        declaration != null &&
        parent != null &&
        parent.declaration != null &&
        parent.localSets == 0 &&
        parent.declaration.preOrderIndex > declaration.preOrderIndex) {
      compiler.report(
          JSError.make(declaration.source.getName(), declaration.node,
              NAME_DEFINED_LATE_WARNING, parent.fullName()));
    }

    JSModuleGraph moduleGraph = compiler.getModuleGraph();
    for (Ref ref : name.getRefs()) {
      if (!isDefined) {
        reportRefToUndefinedName(name, ref);
      } else {
        if (declaration != null &&
            ref.getModule() != declaration.getModule() &&
            !moduleGraph.dependsOn(
                ref.getModule(), declaration.getModule())) {
          reportBadModuleReference(name, ref);
        }
      }
    }
  }

  private void reportBadModuleReference(Name name, Ref ref) {
    compiler.report(
        JSError.make(ref.source.getName(), ref.node, STRICT_MODULE_DEP_QNAME,
                     ref.getModule().getName(),
                     name.declaration.getModule().getName(),
                     name.fullName()));
  }

  private void reportRefToUndefinedName(Name name, Ref ref) {
    // grab the highest undefined ancestor to output in the warning message.
    while (name.parent != null &&
           name.parent.globalSets + name.parent.localSets == 0) {
      name = name.parent;
    }

    // If this is an annotated EXPR-GET, don't do anything.
    Node parent = ref.node.getParent();
    if (parent.getType() == Token.EXPR_RESULT) {
      JSDocInfo info = ref.node.getJSDocInfo();
      if (info != null && info.hasTypedefType()) {
        return;
      }
    }

    compiler.report(
        JSError.make(ref.getSourceName(), ref.node, level,
            UNDEFINED_NAME_WARNING, name.fullName()));
  }

  /**
   * Checks whether the given name is a property, and whether that property
   * must be initialized with its full qualified name.
   */
  private static boolean propertyMustBeInitializedByFullName(Name name) {
    // If an object literal in the global namespace  is never aliased,
    // then all of its properties must be defined using its full qualified
    // name. This implies that its properties must all be in the global
    // namespace as well.
    //
    // The same is not true for FUNCTION and OTHER types, because their
    // implicit prototypes have properties that are not captured by the global
    // namespace.
    return name.parent != null && name.parent.aliasingGets == 0 &&
        name.parent.type == Name.Type.OBJECTLIT;
  }
}
