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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.HashSet;
import java.util.Set;

/**
 * Checks references to undefined properties of global variables.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class CheckGlobalNames implements CompilerPass {

  private final AbstractCompiler compiler;
  private final CodingConvention convention;
  private final CheckLevel level;

  private GlobalNamespace namespace = null;
  private final Set<String> objectPrototypeProps = new HashSet<>();
  private final Set<String> functionPrototypeProps = new HashSet<>();

  // Warnings
  static final DiagnosticType UNDEFINED_NAME_WARNING = DiagnosticType.warning(
      "JSC_UNDEFINED_NAME",
      "{0} is never defined");

  static final DiagnosticType NAME_DEFINED_LATE_WARNING =
      DiagnosticType.warning(
          "JSC_NAME_DEFINED_LATE",
          "{0} defined before its owner. {1} is defined at {2}:{3}");

  static final DiagnosticType STRICT_MODULE_DEP_QNAME =
      DiagnosticType.disabled(
          "JSC_STRICT_MODULE_DEP_QNAME",
          // The newline below causes the JS compiler not to complain when the
          // referenced module's name changes because, for example, it's a
          // synthetic module.
          "cannot reference {2} because of a missing module dependency\n"
          + "defined in module {1}, referenced from module {0}");

  /**
   * Creates a pass to check global name references at the given warning level.
   */
  CheckGlobalNames(AbstractCompiler compiler, CheckLevel level) {
    this.compiler = compiler;
    this.convention = compiler.getCodingConvention();
    this.level = level;
  }

  /**
   * Injects a pre-computed global namespace, so that the same namespace
   * can be re-used for multiple check passes. Returns this for easy chaining.
   */
  CheckGlobalNames injectNamespace(GlobalNamespace namespace) {
    checkArgument(namespace.hasExternsRoot());
    this.namespace = namespace;
    return this;
  }

  @Override
  public void process(Node externs, Node root) {
    if (namespace == null) {
      namespace = new GlobalNamespace(compiler, externs, root);
    }

    // Find prototype properties that will affect our analysis.
    checkState(namespace.hasExternsRoot());
    findPrototypeProps("Object", objectPrototypeProps);
    findPrototypeProps("Function", functionPrototypeProps);
    objectPrototypeProps.addAll(
        convention.getIndirectlyDeclaredProperties());

    for (Name name : namespace.getNameForest()) {
      // Skip extern names. Externs are often not runnable as real code,
      // and will do things like:
      // var x;
      // x.method;
      // which this check forbids.
      if (name.getSourceKind() != GlobalNamespace.SourceKind.CODE) {
        continue;
      }

      checkDescendantNames(name, name.getGlobalSets() + name.getLocalSets() > 0);
    }
  }

  private void findPrototypeProps(String type, Set<String> props) {
    Name slot = namespace.getSlot(type);
    if (slot != null) {
      for (Ref ref : slot.getRefs()) {
        if (ref.type == Ref.Type.PROTOTYPE_GET) {
          Node fullName = ref.getNode().getGrandparent();
          if (fullName.isGetProp()) {
            props.add(fullName.getLastChild().getString());
          }
        }
      }
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
          propIsDefined =
              (!propertyMustBeInitializedByFullName(prop)
                  || prop.getGlobalSets() + prop.getLocalSets() > 0);
        }

        validateName(prop, propIsDefined);
        checkDescendantNames(prop, propIsDefined);
      }
    }
  }

  private void validateName(Name name, boolean isDefined) {
    // If the name is not defined, emit warnings for each reference. While
    // we're looking through each reference, check all the module dependencies.
    Ref declaration = name.getDeclaration();
    Name parent = name.getParent();

    JSModuleGraph moduleGraph = compiler.getModuleGraph();
    boolean isTypedef = isTypedef(name);
    for (Ref ref : name.getRefs()) {
      // Don't worry about global exprs.
      boolean isGlobalExpr = ref.getNode().getParent().isExprResult();

      if (!isDefined && !isTypedef) {
        if (!isGlobalExpr) {
          reportRefToUndefinedName(name, ref);
        }
      } else if (declaration != null &&
          ref.getModule() != declaration.getModule() &&
          !moduleGraph.dependsOn(
              ref.getModule(), declaration.getModule())) {
        reportBadModuleReference(name, ref);
      } else {
        // Check for late references.
        if (ref.scope.getClosestHoistScope().isGlobal()) {
          // Prototype references are special, because in our reference graph,
          // A.prototype counts as a reference to A.
          boolean isPrototypeGet = (ref.type == Ref.Type.PROTOTYPE_GET);
          Name owner = isPrototypeGet ? name : parent;
          boolean singleGlobalParentDecl =
              owner != null && owner.getDeclaration() != null && owner.getLocalSets() == 0;

          if (singleGlobalParentDecl &&
              owner.getDeclaration().preOrderIndex > ref.preOrderIndex) {
            String refName = isPrototypeGet
                ? name.getFullName() + ".prototype"
                : name.getFullName();
            compiler.report(
                JSError.make(ref.node,
                    NAME_DEFINED_LATE_WARNING,
                    refName,
                    owner.getFullName(),
                    owner.getDeclaration().getSourceFile().getName(),
                    String.valueOf(owner.getDeclaration().node.getLineno())));
          }
        }
      }
    }
  }

  private static boolean isTypedef(Name name) {
    if (name.getDeclaration() != null) {
      // typedefs don't have 'declarations' because you can't assign to them
      return false;
    }
    for (Ref ref : name.getRefs()) {
      // If this is an annotated EXPR-GET, don't do anything.
      Node parent = ref.node.getParent();
      if (parent.isExprResult()) {
        JSDocInfo info = ref.node.getJSDocInfo();
        if (info != null && info.hasTypedefType()) {
          return true;
        }
      }
    }
    return false;
  }

  private void reportBadModuleReference(Name name, Ref ref) {
    compiler.report(
        JSError.make(ref.node, STRICT_MODULE_DEP_QNAME,
                     ref.getModule().getName(),
                     name.getDeclaration().getModule().getName(),
                     name.getFullName()));
  }

  private void reportRefToUndefinedName(Name name, Ref ref) {
    // grab the highest undefined ancestor to output in the warning message.
    while (name.getParent() != null
        && name.getParent().getGlobalSets() + name.getParent().getLocalSets() == 0) {
      name = name.getParent();
    }

    compiler.report(
        JSError.make(ref.node, level,
            UNDEFINED_NAME_WARNING, name.getFullName()));
  }

  /**
   * The input name is a property. Check whether this property
   * must be initialized with its full qualified name.
   */
  private boolean propertyMustBeInitializedByFullName(Name name) {
    // If an object or function literal in the global namespace is never
    // aliased, then its properties can only come from one of 2 places:
    // 1) From its prototype chain, or
    // 2) From an assignment to its fully qualified name.
    // If we assume #1 is not the case, then #2 implies that its
    // properties must all be modeled in the GlobalNamespace as well.
    //
    // We assume that for global object literals and types (constructors and
    // interfaces), we can find all the properties inherited from the prototype
    // chain of functions and objects.
    if (name.getParent() == null) {
      return false;
    }

    if (isNameUnsafelyAliased(name.getParent())) {
      // e.g. if we have `const ns = {}; escape(ns); alert(ns.a.b);`
      // we don't expect ns.a.b to be defined somewhere because `ns` has escaped
      return false;
    }

    if (objectPrototypeProps.contains(name.getBaseName())) {
      // checks for things on Object.prototype, e.g. a call to
      // something.hasOwnProperty('a');
      return false;
    }

    if (name.getParent().isObjectLiteral()) {
      // if this is a property on an object literal, always expect an initialization somewhere
      return true;
    }

    if (name.getParent().isClass()) {
      // only warn on class properties if there is no superclass, because we don't handle
      // class side inheritance here very well yet.
      return !hasSuperclass(name.getParent());
    }

    // warn on remaining names if they are on a constructor and are not a Function.prototype
    // property (e.g. f.call(1);)
    return name.getParent().isFunction()
        && name.getParent().isDeclaredType()
        && !functionPrototypeProps.contains(name.getBaseName());
  }

  /** Returns whether the given ES6 class extends something. */
  private boolean hasSuperclass(Name es6Class) {
    Node decl = es6Class.getDeclaration().getNode();
    Node classNode = NodeUtil.getRValueOfLValue(decl);
    checkState(classNode.isClass(), classNode);
    Node superclass = classNode.getSecondChild();

    return !superclass.isEmpty();
  }

  private boolean isNameUnsafelyAliased(Name name) {
    if (name.getAliasingGets() > 0) {
      for (Ref ref : name.getRefs()) {
        if (ref.type == Ref.Type.ALIASING_GET) {
          Node aliaser = ref.getNode().getParent();

          // We don't need to worry about known aliased, because
          // they're already covered by the getIndirectlyDeclaredProperties
          // call at the top.
          boolean isKnownAlias =
              aliaser.isCall()
                  && (convention.getClassesDefinedByCall(aliaser) != null
                      || convention.getSingletonGetterClassName(aliaser) != null);
          if (!isKnownAlias) {
            return true;
          }
        }
      }
    }
    return name.getParent() != null && isNameUnsafelyAliased(name.getParent());
  }
}
