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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticSourceFile;

import java.util.ArrayDeque;

import javax.annotation.Nullable;

/**
 * A compiler pass that checks that the programmer has obeyed all the access
 * control restrictions indicated by JSDoc annotations, like
 * {@code @private} and {@code @deprecated}.
 *
 * Because access control restrictions are attached to type information,
 * it's important that TypedScopeCreator, TypeInference, and InferJSDocInfo
 * all run before this pass. TypedScopeCreator creates and resolves types,
 * TypeInference propagates those types across the AST, and InferJSDocInfo
 * propagates JSDoc across the types.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class CheckAccessControls implements ScopedCallback, HotSwapCompilerPass {

  static final DiagnosticType DEPRECATED_NAME = DiagnosticType.disabled(
      "JSC_DEPRECATED_VAR",
      "Variable {0} has been deprecated.");

  static final DiagnosticType DEPRECATED_NAME_REASON = DiagnosticType.disabled(
      "JSC_DEPRECATED_VAR_REASON",
      "Variable {0} has been deprecated: {1}");

  static final DiagnosticType DEPRECATED_PROP = DiagnosticType.disabled(
      "JSC_DEPRECATED_PROP",
      "Property {0} of type {1} has been deprecated.");

  static final DiagnosticType DEPRECATED_PROP_REASON = DiagnosticType.disabled(
      "JSC_DEPRECATED_PROP_REASON",
      "Property {0} of type {1} has been deprecated: {2}");

  static final DiagnosticType DEPRECATED_CLASS = DiagnosticType.disabled(
      "JSC_DEPRECATED_CLASS",
      "Class {0} has been deprecated.");

  static final DiagnosticType DEPRECATED_CLASS_REASON = DiagnosticType.disabled(
      "JSC_DEPRECATED_CLASS_REASON",
      "Class {0} has been deprecated: {1}");

  static final DiagnosticType BAD_PACKAGE_PROPERTY_ACCESS =
      DiagnosticType.error(
          "JSC_BAD_PACKAGE_PROPERTY_ACCESS",
          "Access to package-private property {0} of {1} not allowed here.");

  static final DiagnosticType BAD_PRIVATE_GLOBAL_ACCESS =
      DiagnosticType.error(
          "JSC_BAD_PRIVATE_GLOBAL_ACCESS",
          "Access to private variable {0} not allowed outside file {1}.");

  static final DiagnosticType BAD_PRIVATE_PROPERTY_ACCESS =
      DiagnosticType.warning(
          "JSC_BAD_PRIVATE_PROPERTY_ACCESS",
          "Access to private property {0} of {1} not allowed here.");

  static final DiagnosticType BAD_PROTECTED_PROPERTY_ACCESS =
      DiagnosticType.warning(
          "JSC_BAD_PROTECTED_PROPERTY_ACCESS",
          "Access to protected property {0} of {1} not allowed here.");

  static final DiagnosticType
      BAD_PROPERTY_OVERRIDE_IN_FILE_WITH_FILEOVERVIEW_VISIBILITY =
      DiagnosticType.error(
          "JSC_BAD_PROPERTY_OVERRIDE_IN_FILE_WITH_FILEOVERVIEW_VISIBILITY",
          "Overridden property {0} in file with fileoverview visibility {1}" +
          " must explicitly redeclare superclass visibility");

  static final DiagnosticType PRIVATE_OVERRIDE =
      DiagnosticType.warning(
          "JSC_PRIVATE_OVERRIDE",
          "Overriding private property of {0}.");

  static final DiagnosticType EXTEND_FINAL_CLASS =
      DiagnosticType.error(
          "JSC_EXTEND_FINAL_CLASS",
          "{0} is not allowed to extend final class {1}.");

  static final DiagnosticType VISIBILITY_MISMATCH =
      DiagnosticType.warning(
          "JSC_VISIBILITY_MISMATCH",
          "Overriding {0} property of {1} with {2} property.");

  static final DiagnosticType CONST_PROPERTY_REASSIGNED_VALUE =
      DiagnosticType.warning(
        "JSC_CONSTANT_PROPERTY_REASSIGNED_VALUE",
        "constant property {0} assigned a value more than once");

  static final DiagnosticType CONST_PROPERTY_DELETED =
      DiagnosticType.warning(
        "JSC_CONSTANT_PROPERTY_DELETED",
        "constant property {0} cannot be deleted");

  static final DiagnosticType CONVENTION_MISMATCH =
      DiagnosticType.warning(
          "JSC_CONVENTION_MISMATCH",
          "Declared access conflicts with access convention.");

  private final AbstractCompiler compiler;
  private final TypeValidator validator;
  private final boolean enforceCodingConventions;

  // State about the current traversal.
  private int deprecatedDepth = 0;
  private final ArrayDeque<JSType> currentClassStack = new ArrayDeque<>();
  private final JSType noTypeSentinel;

  private ImmutableMap<StaticSourceFile, Visibility> defaultVisibilityForFiles;
  private final Multimap<JSType, String> initializedConstantProperties;


  CheckAccessControls(
      AbstractCompiler compiler, boolean enforceCodingConventions) {
    this.compiler = compiler;
    this.validator = compiler.getTypeValidator();
    this.initializedConstantProperties = HashMultimap.create();
    this.enforceCodingConventions = enforceCodingConventions;
    this.noTypeSentinel = compiler.getTypeRegistry()
        .getNativeType(JSTypeNative.NO_TYPE);
  }

  @Override
  public void process(Node externs, Node root) {
    CollectFileOverviewVisibility collectPass =
        new CollectFileOverviewVisibility(compiler);
    collectPass.process(externs, root);
    defaultVisibilityForFiles = collectPass.getFileOverviewVisibilityMap();
    NodeTraversal.traverse(compiler, externs, this);
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    CollectFileOverviewVisibility collectPass =
        new CollectFileOverviewVisibility(compiler);
    collectPass.hotSwapScript(scriptRoot, originalRoot);
    defaultVisibilityForFiles = collectPass.getFileOverviewVisibilityMap();
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  @Override
  public void enterScope(NodeTraversal t) {
    if (!t.inGlobalScope()) {
      Node n = t.getScopeRoot();
      Node parent = n.getParent();
      if (isDeprecatedFunction(n)) {
        deprecatedDepth++;
      }
      JSType prevClass = getCurrentClass();
      JSType currentClass = prevClass == null
          ? getClassOfMethod(n, parent)
          : prevClass;
      // ArrayDeques can't handle nulls, so we reuse the bottom type
      // as a null sentinel.
      currentClassStack.addFirst(currentClass == null
          ? noTypeSentinel
          : currentClass);
    }
  }

  @Override
  public void exitScope(NodeTraversal t) {
    if (!t.inGlobalScope()) {
      Node n = t.getScopeRoot();
      if (isDeprecatedFunction(n)) {
        deprecatedDepth--;
      }
      currentClassStack.pop();
    }
  }

  /**
   * Gets the type of the class that "owns" a method, or null if
   * we know that its un-owned.
   */
  private JSType getClassOfMethod(Node n, Node parent) {
    if (parent.isAssign()) {
      Node lValue = parent.getFirstChild();
      if (NodeUtil.isGet(lValue)) {
        // We have an assignment of the form "a.b = ...".
        JSType lValueType = lValue.getJSType();
        if (lValueType != null && lValueType.isNominalConstructor()) {
          // If a.b is a constructor, then everything in this function
          // belongs to the "a.b" type.
          return (lValueType.toMaybeFunctionType()).getInstanceType();
        } else {
          // If a.b is not a constructor, then treat this as a method
          // of whatever type is on "a".
          return normalizeClassType(lValue.getFirstChild().getJSType());
        }
      } else {
        // We have an assignment of the form "a = ...", so pull the
        // type off the "a".
        return normalizeClassType(lValue.getJSType());
      }
    } else if (NodeUtil.isFunctionDeclaration(n) ||
               parent.isName()) {
      return normalizeClassType(n.getJSType());
    } else if (parent.isStringKey()
        || parent.isGetterDef() || parent.isSetterDef()) {
      Node objectLitParent = parent.getParent().getParent();
      if (!objectLitParent.isAssign()) {
        return null;
      }
      Node className = NodeUtil.getPrototypeClassName(objectLitParent.getFirstChild());
      if (className != null) {
        return normalizeClassType(className.getJSType());
      }
    }

    return null;
  }

  /**
   * Normalize the type of a constructor, its instance, and its prototype
   * all down to the same type (the instance type).
   */
  private static JSType normalizeClassType(JSType type) {
    if (type == null || type.isUnknownType()) {
      return type;
    } else if (type.isNominalConstructor()) {
      return (type.toMaybeFunctionType()).getInstanceType();
    } else if (type.isFunctionPrototypeType()) {
      FunctionType owner = ((ObjectType) type).getOwnerFunction();
      if (owner.isConstructor()) {
        return owner.getInstanceType();
      }
    }
    return type;
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()) {
      case Token.NAME:
        checkNameDeprecation(t, n, parent);
        checkNameVisibility(t, n, parent);
        break;
      case Token.GETPROP:
        checkPropertyDeprecation(t, n, parent);
        checkPropertyVisibility(t, n, parent);
        checkConstantProperty(t, n);
        break;
      case Token.STRING_KEY:
      case Token.GETTER_DEF:
      case Token.SETTER_DEF:
        checkKeyVisibilityConvention(t, n, parent);
        break;
      case Token.NEW:
        checkConstructorDeprecation(t, n, parent);
        break;
      case Token.FUNCTION:
        checkFinalClassOverrides(t, n, parent);
        break;
    }
  }

  /**
   * Checks the given NEW node to ensure that access restrictions are obeyed.
   */
  private void checkConstructorDeprecation(NodeTraversal t, Node n,
      Node parent) {
    JSType type = n.getJSType();

    if (type != null) {
      String deprecationInfo = getTypeDeprecationInfo(type);

      if (deprecationInfo != null &&
          shouldEmitDeprecationWarning(t, n, parent)) {

        if (!deprecationInfo.isEmpty()) {
            compiler.report(
                t.makeError(n, DEPRECATED_CLASS_REASON,
                    type.toString(), deprecationInfo));
        } else {
          compiler.report(
              t.makeError(n, DEPRECATED_CLASS, type.toString()));
        }
      }
    }
  }

  /**
   * Checks the given NAME node to ensure that access restrictions are obeyed.
   */
  private void checkNameDeprecation(NodeTraversal t, Node n, Node parent) {
    // Don't bother checking definitions or constructors.
    if (parent.isFunction() || parent.isVar() ||
        parent.isNew()) {
      return;
    }

    Scope.Var var = t.getScope().getVar(n.getString());
    JSDocInfo docInfo = var == null ? null : var.getJSDocInfo();

    if (docInfo != null && docInfo.isDeprecated() &&
        shouldEmitDeprecationWarning(t, n, parent)) {

      if (docInfo.getDeprecationReason() != null) {
        compiler.report(
            t.makeError(n, DEPRECATED_NAME_REASON, n.getString(),
                docInfo.getDeprecationReason()));
      } else {
        compiler.report(
            t.makeError(n, DEPRECATED_NAME, n.getString()));
      }
    }
  }

  /**
   * Checks the given GETPROP node to ensure that access restrictions are
   * obeyed.
   */
  private void checkPropertyDeprecation(NodeTraversal t, Node n, Node parent) {
    // Don't bother checking constructors.
    if (parent.isNew()) {
      return;
    }

    ObjectType objectType =
        ObjectType.cast(dereference(n.getFirstChild().getJSType()));
    String propertyName = n.getLastChild().getString();

    if (objectType != null) {
      String deprecationInfo
          = getPropertyDeprecationInfo(objectType, propertyName);

      if (deprecationInfo != null &&
          shouldEmitDeprecationWarning(t, n, parent)) {

        if (!deprecationInfo.isEmpty()) {
          compiler.report(
              t.makeError(n, DEPRECATED_PROP_REASON, propertyName,
                  validator.getReadableJSTypeName(n.getFirstChild(), true),
                  deprecationInfo));
        } else {
          compiler.report(
              t.makeError(n, DEPRECATED_PROP, propertyName,
                  validator.getReadableJSTypeName(n.getFirstChild(), true)));
        }
      }
    }
  }

  private boolean isPrivateByConvention(String name) {
    return enforceCodingConventions
        && compiler.getCodingConvention().isPrivate(name);
  }

  /**
   * Determines whether the given OBJECTLIT property visibility
   * violates the coding convention.
   * @param t The current traversal.
   * @param key The objectlit key node (STRING_KEY, GETTER_DEF, SETTER_DEF).
   */
  private void checkKeyVisibilityConvention(NodeTraversal t,
      Node key, Node parent) {
    JSDocInfo info = key.getJSDocInfo();
    if (info == null) {
      return;
    }
    if (!isPrivateByConvention(key.getString())) {
      return;
    }
    Node assign = parent.getParent();
    if (assign == null || !assign.isAssign()) {
      return;
    }
    Node left = assign.getFirstChild();
    if (!left.isGetProp()
        || !left.getLastChild().getString().equals("prototype")) {
      return;
    }
    Visibility declaredVisibility = info.getVisibility();
    // Visibility is declared to be something other than private.
    if (declaredVisibility != Visibility.INHERITED
        && declaredVisibility != Visibility.PRIVATE) {
      compiler.report(t.makeError(key, CONVENTION_MISMATCH));
    }
  }

  /**
   * Reports an error if the given name is not visible in the current context.
   * @param t The current traversal.
   * @param name The name node.
   */
  private void checkNameVisibility(NodeTraversal t, Node name, Node parent) {
    Var var = t.getScope().getVar(name.getString());
    if (var == null) {
      return;
    }

    Visibility v = checkPrivateNameConvention(
        AccessControlUtils.getEffectiveNameVisibility(
            name, var, defaultVisibilityForFiles), name);

    switch (v) {
      case PACKAGE:
        if (!isPackageAccessAllowed(var, name)) {
          compiler.report(
              t.makeError(name, BAD_PACKAGE_PROPERTY_ACCESS,
                  name.getString(), var.getSourceFile().getName()));
        }
        break;
      case PRIVATE:
        if (!isPrivateAccessAllowed(var, name, parent)) {
          compiler.report(
              t.makeError(name, BAD_PRIVATE_GLOBAL_ACCESS,
                  name.getString(), var.getSourceFile().getName()));
        }
        break;
      default:
        // Nothing to do for PUBLIC and PROTECTED
        // (which is irrelevant for names).
        break;
    }
  }


  /**
   * Returns the effective visibility of the given name, reporting an error
   * if there is a contradiction in the various sources of visibility
   * (example: a variable with a trailing underscore that is declared
   * {@code @public}).
   */
  private Visibility checkPrivateNameConvention(Visibility v, Node name) {
    if (isPrivateByConvention(name.getString())) {
      if (v != Visibility.PRIVATE && v != Visibility.INHERITED) {
        compiler.report(JSError.make(name, CONVENTION_MISMATCH));
      }
      return Visibility.PRIVATE;
    }
    return v;
  }

  private static boolean isPrivateAccessAllowed(Var var, Node name, Node parent) {
    StaticSourceFile varSrc = var.getSourceFile();
    StaticSourceFile refSrc = name.getStaticSourceFile();
    JSDocInfo docInfo = var.getJSDocInfo();
    if (varSrc != null
        && refSrc != null
        && !varSrc.getName().equals(refSrc.getName())) {
      return docInfo != null && docInfo.isConstructor()
          && isValidPrivateConstructorAccess(parent);
    } else {
      return true;
    }
  }

  private boolean isPackageAccessAllowed(Var var, Node name) {
    StaticSourceFile varSrc = var.getSourceFile();
    StaticSourceFile refSrc = name.getStaticSourceFile();
    CodingConvention codingConvention = compiler.getCodingConvention();
    if (varSrc != null && refSrc != null) {
      String srcPackage = codingConvention.getPackageName(varSrc);
      String refPackage = codingConvention.getPackageName(refSrc);
      return srcPackage != null
          && refPackage != null
          && srcPackage.equals(refPackage);
    } else {
      // If the source file of either var or name is unavailable, conservatively
      // assume they belong to different packages.
      // TODO(brndn): by contrast, isPrivateAccessAllowed does allow
      // private access when a source file is unknown. I didn't change it
      // in order not to break existing code.
      return false;
    }
  }

  private void checkOverriddenPropertyVisibilityMismatch(
      Visibility overriding,
      Visibility overridden,
      @Nullable Visibility fileOverview,
      NodeTraversal t,
      Node getprop) {
    if (overriding == Visibility.INHERITED
        && overriding != overridden
        && fileOverview != null
        && fileOverview != Visibility.INHERITED) {
      String propertyName = getprop.getLastChild().getString();
      compiler.report(
          t.makeError(getprop,
              BAD_PROPERTY_OVERRIDE_IN_FILE_WITH_FILEOVERVIEW_VISIBILITY,
              propertyName,
              fileOverview.name()));
    }
  }

  @Nullable private static Visibility getOverridingPropertyVisibility(Node parent) {
    JSDocInfo overridingInfo = parent.getJSDocInfo();
    return overridingInfo == null || !overridingInfo.isOverride()
        ? null
        : overridingInfo.getVisibility();
  }



  /**
   * Checks if a constructor is trying to override a final class.
   */
  private void checkFinalClassOverrides(NodeTraversal t, Node fn, Node parent) {
    JSType type = fn.getJSType().toMaybeFunctionType();
    if (type != null && type.isConstructor()) {
      JSType finalParentClass = getFinalParentClass(getClassOfMethod(fn, parent));
      if (finalParentClass != null) {
        compiler.report(
            t.makeError(fn, EXTEND_FINAL_CLASS,
                type.getDisplayName(), finalParentClass.getDisplayName()));
      }
    }
  }

  /**
   * Determines whether the given constant property got reassigned
   * @param t The current traversal.
   * @param getprop The getprop node.
   */
  private void checkConstantProperty(NodeTraversal t,
      Node getprop) {
    // Check whether the property is modified
    Node parent = getprop.getParent();
    boolean isDelete = parent.isDelProp();
    if (!(NodeUtil.isAssignmentOp(parent) && parent.getFirstChild() == getprop)
        && !parent.isInc() && !parent.isDec()
        && !isDelete) {
      return;
    }

    ObjectType objectType =
      ObjectType.cast(dereference(getprop.getFirstChild().getJSType()));

    String propertyName = getprop.getLastChild().getString();

    boolean isConstant = isPropertyDeclaredConstant(objectType, propertyName);

    // Check whether constant properties are reassigned
    if (isConstant) {
      if (isDelete) {
        compiler.report(
            t.makeError(getprop, CONST_PROPERTY_DELETED, propertyName));
        return;
      }

      // Can't check for constant properties on generic function types.
      // TODO(johnlenz): I'm not 100% certain this is necessary, or if
      // the type is being inspected incorrectly.
      if (objectType == null
          || (objectType.isFunctionType()
              && !objectType.toMaybeFunctionType().isConstructor())) {
        return;
      }

      ObjectType oType = objectType;
      while (oType != null) {
        if (initializedConstantProperties.containsEntry(
            oType, propertyName)) {
          compiler.report(
              t.makeError(getprop, CONST_PROPERTY_REASSIGNED_VALUE,
                  propertyName));
            break;
          }
        oType = oType.getImplicitPrototype();
      }

      initializedConstantProperties.put(objectType,
          propertyName);

      // Add the prototype when we're looking at an instance object
      if (objectType.isInstanceType()) {
        ObjectType prototype = objectType.getImplicitPrototype();
        if (prototype != null) {
          if (prototype.hasProperty(propertyName)) {
            initializedConstantProperties.put(prototype,
                propertyName);
          }
        }
      }
    }
  }

  /**
   * Reports an error if the given property is not visible in the current
   * context.
   * @param t The current traversal.
   * @param getprop The getprop node.
   */
  private void checkPropertyVisibility(NodeTraversal t,
      Node getprop, Node parent) {
    ObjectType referenceType =
        ObjectType.cast(dereference(getprop.getFirstChild().getJSType()));

    String propertyName = getprop.getLastChild().getString();
    boolean isPrivateByConvention = isPrivateByConvention(propertyName);

    if (isPrivateByConvention
        && propertyIsDeclaredButNotPrivate(getprop, parent)) {
      compiler.report(t.makeError(getprop, CONVENTION_MISMATCH));
      return;
    }

    StaticSourceFile definingSource = getDefiningSource(
        getprop, referenceType, propertyName);

    boolean isClassType = false;

    // Is this a normal property access, or are we trying to override
    // an existing property?
    boolean isOverride = parent.getJSDocInfo() != null
        && parent.isAssign()
        && parent.getFirstChild() == getprop;

    ObjectType objectType = getObjectType(
        referenceType, isOverride, propertyName);

    Visibility fileOverviewVisibility =
        defaultVisibilityForFiles.get(definingSource);

    Visibility visibility = AccessControlUtils.getEffectivePropertyVisibility(
        getprop,
        referenceType,
        defaultVisibilityForFiles,
        enforceCodingConventions ? compiler.getCodingConvention() : null);

    if (isOverride) {
      Visibility overriding = getOverridingPropertyVisibility(parent);
      if (overriding != null) {
        checkOverriddenPropertyVisibilityMismatch(
            overriding, visibility, fileOverviewVisibility, t, getprop);
      }
    }



    if (objectType != null) {
      JSDocInfo docInfo = objectType.getOwnPropertyJSDocInfo(propertyName);
      definingSource = docInfo.getStaticSourceFile();
      isClassType = docInfo.isConstructor();
    } else if (isPrivateByConvention) {
      // We can only check visibility references if we know what file
      // it was defined in.
      objectType = referenceType;
    } else if (fileOverviewVisibility == null) {
      // Otherwise just assume the property is public.
      return;
    }

    StaticSourceFile referenceSource = getprop.getStaticSourceFile();

    if (isOverride) {
      boolean sameInput = referenceSource != null
          && referenceSource.getName().equals(definingSource.getName());
      checkOverriddenPropertyVisibility(
          t,
          getprop,
          parent,
          visibility,
          fileOverviewVisibility,
          objectType,
          sameInput);
    } else {
      checkNonOverriddenPropertyVisibility(
          t,
          getprop,
          parent,
          visibility,
          isClassType,
          objectType,
          referenceSource,
          definingSource);
    }
  }

  private static boolean propertyIsDeclaredButNotPrivate(Node getprop, Node parent) {
    // This is a declaration with JSDoc
    JSDocInfo info = NodeUtil.getBestJSDocInfo(getprop);
    if ((parent.isAssign() || parent.isExprResult())
        && parent.getFirstChild() == getprop
        && info != null) {
      Visibility declaredVisibility = info.getVisibility();
      if (declaredVisibility != Visibility.PRIVATE
      && declaredVisibility != Visibility.INHERITED) {
          return true;
      }
    }
    return false;
  }

  @Nullable private static StaticSourceFile getDefiningSource(
      Node getprop,
      @Nullable ObjectType referenceType,
      String propertyName) {
      if (referenceType != null) {
        Node propDefNode = referenceType.getPropertyNode(propertyName);
        if (propDefNode != null) {
          return propDefNode.getStaticSourceFile();
        }
      }
    return getprop.getStaticSourceFile();
  }

  @Nullable private static ObjectType getObjectType(
      @Nullable ObjectType referenceType,
      boolean isOverride,
      String propertyName) {
    if (referenceType == null) {
      return null;
    }

    // Find the lowest property defined on a class with visibility
    // information.
    ObjectType objectType = isOverride
        ? referenceType.getImplicitPrototype()
        : referenceType;
    for (; objectType != null;
        objectType = objectType.getImplicitPrototype()) {
      JSDocInfo docInfo = objectType.getOwnPropertyJSDocInfo(propertyName);
      if (docInfo != null
          && docInfo.getVisibility() != Visibility.INHERITED) {
        return objectType;
      }
    }
    return null;
  }

  private void checkOverriddenPropertyVisibility(
      NodeTraversal t,
      Node getprop,
      Node parent,
      Visibility visibility,
      Visibility fileOverviewVisibility,
      ObjectType objectType,
      boolean sameInput) {
    // Check an ASSIGN statement that's trying to override a property
    // on a superclass.
    JSDocInfo overridingInfo = parent.getJSDocInfo();
    Visibility overridingVisibility = overridingInfo == null
        ? Visibility.INHERITED
        : overridingInfo.getVisibility();

    // Check that:
    // (a) the property *can* be overridden,
    // (b) the visibility of the override is the same as the
    //     visibility of the original property,
    // (c) the visibility is explicitly redeclared if the override is in
    //     a file with default visibility in the @fileoverview block.
    if (visibility == Visibility.PRIVATE && !sameInput) {
      compiler.report(
          t.makeError(getprop, PRIVATE_OVERRIDE,
              objectType.toString()));
    } else if (overridingVisibility != Visibility.INHERITED
        && overridingVisibility != visibility
        && fileOverviewVisibility == null) {
      compiler.report(
          t.makeError(getprop, VISIBILITY_MISMATCH,
              visibility.name(), objectType.toString(),
              overridingVisibility.name()));
    }
  }

  private void checkNonOverriddenPropertyVisibility(
      NodeTraversal t,
      Node getprop,
      Node parent,
      Visibility visibility,
      boolean isClassType,
      JSType objectType,
      StaticSourceFile referenceSource,
      StaticSourceFile definingSource) {
    // private access is always allowed in the same file.
    if (referenceSource != null
        && definingSource != null
        && referenceSource.getName().equals(definingSource.getName())) {
      return;
    }

    String propertyName = getprop.getLastChild().getString();
    JSType ownerType = normalizeClassType(objectType);

    switch (visibility) {
      case PACKAGE:
        checkPackagePropertyVisibility(t, getprop, referenceSource, definingSource);
        break;
      case PRIVATE:
        checkPrivatePropertyVisibility(t, getprop, parent, isClassType, ownerType);
        break;
      case PROTECTED:
        checkProtectedPropertyVisibility(t, getprop, ownerType);
        break;
      default:
        break;
    }
  }

  private void checkPackagePropertyVisibility(
      NodeTraversal t,
      Node getprop,
      StaticSourceFile referenceSource,
      StaticSourceFile definingSource) {
    CodingConvention codingConvention = compiler.getCodingConvention();
    String refPackage = codingConvention.getPackageName(referenceSource);
    String defPackage = codingConvention.getPackageName(definingSource);
    if (refPackage == null
        || defPackage == null
        || !refPackage.equals(defPackage)) {
      String propertyName = getprop.getLastChild().getString();
      compiler.report(
          t.makeError(getprop, BAD_PACKAGE_PROPERTY_ACCESS,
              propertyName,
              validator.getReadableJSTypeName(
                  getprop.getFirstChild(), true)));
      }
  }

  @Nullable private JSType getCurrentClass() {
    JSType cur = currentClassStack.peekFirst();
    return cur == noTypeSentinel
        ? null
        : cur;
  }

  private void checkPrivatePropertyVisibility(
      NodeTraversal t,
      Node getprop,
      Node parent,
      boolean isClassType,
      JSType ownerType) {
    JSType currentClass = getCurrentClass();
    if (currentClass != null && ownerType.isEquivalentTo(currentClass)) {
      return;
    }

    if (isClassType && isValidPrivateConstructorAccess(parent)) {
      return;
    }

    // private access is not allowed outside the file from a different
    // enclosing class.
    JSType accessedType = getprop.getFirstChild().getJSType();
    String propertyName = getprop.getLastChild().getString();
    String readableTypeName = ownerType.equals(accessedType)
        ? validator.getReadableJSTypeName(getprop.getFirstChild(), true)
        : ownerType.toString();
    compiler.report(
        t.makeError(getprop,
            BAD_PRIVATE_PROPERTY_ACCESS,
            propertyName,
            readableTypeName));
  }

  private void checkProtectedPropertyVisibility(
      NodeTraversal t,
      Node getprop,
      JSType ownerType) {
    // There are 3 types of legal accesses of a protected property:
    // 1) Accesses in the same file
    // 2) Overriding the property in a subclass
    // 3) Accessing the property from inside a subclass
    // The first two have already been checked for.
    JSType currentClass = getCurrentClass();
    if (currentClass == null || !currentClass.isSubtype(ownerType)) {
      String propertyName = getprop.getLastChild().getString();
      compiler.report(
          t.makeError(getprop,  BAD_PROTECTED_PROPERTY_ACCESS,
              propertyName,
              validator.getReadableJSTypeName(
                  getprop.getFirstChild(), true)));
    }
  }

  /**
   * Whether the given access of a private constructor is legal.
   *
   * For example,
   * new PrivateCtor_(); // not legal
   * PrivateCtor_.newInstance(); // legal
   * x instanceof PrivateCtor_ // legal
   *
   * This is a weird special case, because our visibility system is inherited
   * from Java, and JavaScript has no distinction between classes and
   * constructors like Java does.
   *
   * We may want to revisit this if we decide to make the restrictions tighter.
   */
  private static boolean isValidPrivateConstructorAccess(Node parent) {
    return !parent.isNew();
  }

  /**
   * Determines whether a deprecation warning should be emitted.
   * @param t The current traversal.
   * @param n The node which we are checking.
   * @param parent The parent of the node which we are checking.
   */
  private boolean shouldEmitDeprecationWarning(
      NodeTraversal t, Node n, Node parent) {
    // In the global scope, there are only two kinds of accesses that should
    // be flagged for warnings:
    // 1) Calls of deprecated functions and methods.
    // 2) Instantiations of deprecated classes.
    // For now, we just let everything else by.
    if (t.inGlobalScope()) {
      if (!((parent.isCall() && parent.getFirstChild() == n) ||
              n.isNew())) {
        return false;
      }
    }

    // We can always assign to a deprecated property, to keep it up to date.
    if (n.isGetProp() && n == parent.getFirstChild() &&
        NodeUtil.isAssignmentOp(parent)) {
      return false;
    }

    // Don't warn if the node is just declaring the property, not reading it.
    if (n.isGetProp() && parent.isExprResult() &&
        n.getJSDocInfo().isDeprecated()) {
      return false;
    }

    return !canAccessDeprecatedTypes(t);
  }

  /**
   * Returns whether it's currently OK to access deprecated names and
   * properties.
   *
   * There are 3 exceptions when we're allowed to use a deprecated
   * type or property:
   * 1) When we're in a deprecated function.
   * 2) When we're in a deprecated class.
   * 3) When we're in a static method of a deprecated class.
   */
  private boolean canAccessDeprecatedTypes(NodeTraversal t) {
    Node scopeRoot = t.getScopeRoot();
    Node scopeRootParent = scopeRoot.getParent();
    return
      // Case #1
      (deprecatedDepth > 0) ||
      // Case #2
      (getTypeDeprecationInfo(t.getScope().getTypeOfThis()) != null) ||
        // Case #3
      (scopeRootParent != null && scopeRootParent.isAssign() &&
       getTypeDeprecationInfo(
           getClassOfMethod(scopeRoot, scopeRootParent)) != null);
  }

  /**
   * Returns whether this is a function node annotated as deprecated.
   */
  private static boolean isDeprecatedFunction(Node n) {
    if (n.isFunction()) {
      JSType type = n.getJSType();
      if (type != null) {
        return getTypeDeprecationInfo(type) != null;
      }
    }

    return false;
  }

  /**
   * Returns the deprecation reason for the type if it is marked
   * as being deprecated. Returns empty string if the type is deprecated
   * but no reason was given. Returns null if the type is not deprecated.
   */
  private static String getTypeDeprecationInfo(JSType type) {
    if (type == null) {
      return null;
    }

    JSDocInfo info = type.getJSDocInfo();
    if (info != null && info.isDeprecated()) {
      if (info.getDeprecationReason() != null) {
        return info.getDeprecationReason();
      }
      return "";
    }
    ObjectType objType = ObjectType.cast(type);
    if (objType != null) {
      ObjectType implicitProto = objType.getImplicitPrototype();
      if (implicitProto != null) {
        return getTypeDeprecationInfo(implicitProto);
      }
    }
    return null;
  }

  /**
   * Returns if a property is declared constant.
   */
  private boolean isPropertyDeclaredConstant(
      ObjectType objectType, String prop) {
    if (enforceCodingConventions
        && compiler.getCodingConvention().isConstant(prop)) {
      return true;
    }
    for (;
         objectType != null;
         objectType = objectType.getImplicitPrototype()) {
      JSDocInfo docInfo = objectType.getOwnPropertyJSDocInfo(prop);
      if (docInfo != null && docInfo.isConstant()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the deprecation reason for the property if it is marked
   * as being deprecated. Returns empty string if the property is deprecated
   * but no reason was given. Returns null if the property is not deprecated.
   */
  private static String getPropertyDeprecationInfo(ObjectType type,
                                                   String prop) {
    JSDocInfo info = type.getOwnPropertyJSDocInfo(prop);
    if (info != null && info.isDeprecated()) {
      if (info.getDeprecationReason() != null) {
        return info.getDeprecationReason();
      }

      return "";
    }
    ObjectType implicitProto = type.getImplicitPrototype();
    if (implicitProto != null) {
      return getPropertyDeprecationInfo(implicitProto, prop);
    }
    return null;
  }

  /**
   * Dereference a type, autoboxing it and filtering out null.
   */
  private static JSType dereference(JSType type) {
    return type == null ? null : type.dereference();
  }

  /**
   * Returns the super class of the given type that has a constructor.
   */
  private static JSType getFinalParentClass(JSType type) {
    if (type != null) {
      ObjectType iproto = ObjectType.cast(type).getImplicitPrototype();
      while (iproto != null && iproto.getConstructor() == null) {
        iproto = iproto.getImplicitPrototype();
      }
      if (iproto != null) {
        Node source = iproto.getConstructor().getSource();
        JSDocInfo jsDoc = source != null ? NodeUtil.getBestJSDocInfo(source) : null;
        if (jsDoc != null && jsDoc.isConstant()) {
          return iproto;
        }
      }
    }
    return null;
  }
}
