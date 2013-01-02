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

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticSourceFile;

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

  static final DiagnosticType BAD_PRIVATE_GLOBAL_ACCESS =
      DiagnosticType.disabled(
          "JSC_BAD_PRIVATE_GLOBAL_ACCESS",
          "Access to private variable {0} not allowed outside file {1}.");

  static final DiagnosticType BAD_PRIVATE_PROPERTY_ACCESS =
      DiagnosticType.disabled(
          "JSC_BAD_PRIVATE_PROPERTY_ACCESS",
          "Access to private property {0} of {1} not allowed here.");

  static final DiagnosticType BAD_PROTECTED_PROPERTY_ACCESS =
      DiagnosticType.disabled(
          "JSC_BAD_PROTECTED_PROPERTY_ACCESS",
          "Access to protected property {0} of {1} not allowed here.");

  static final DiagnosticType PRIVATE_OVERRIDE =
      DiagnosticType.disabled(
          "JSC_PRIVATE_OVERRIDE",
          "Overriding private property of {0}.");

  static final DiagnosticType EXTEND_FINAL_CLASS =
      DiagnosticType.error(
          "JSC_EXTEND_FINAL_CLASS",
          "{0} is not allowed to extend final class {1}.");

  static final DiagnosticType VISIBILITY_MISMATCH =
      DiagnosticType.disabled(
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

  private final AbstractCompiler compiler;
  private final TypeValidator validator;

  // State about the current traversal.
  private int deprecatedDepth = 0;
  private int methodDepth = 0;
  private JSType currentClass = null;

  private final Multimap<String, String> initializedConstantProperties;

  CheckAccessControls(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.validator = compiler.getTypeValidator();
    this.initializedConstantProperties = HashMultimap.create();
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  @Override
  public void enterScope(NodeTraversal t) {
    if (!t.inGlobalScope()) {
      Node n = t.getScopeRoot();
      Node parent = n.getParent();
      if (isDeprecatedFunction(n, parent)) {
        deprecatedDepth++;
      }

      if (methodDepth == 0) {
        currentClass = getClassOfMethod(n, parent);
      }
      methodDepth++;
    }
  }

  @Override
  public void exitScope(NodeTraversal t) {
    if (!t.inGlobalScope()) {
      Node n = t.getScopeRoot();
      Node parent = n.getParent();
      if (isDeprecatedFunction(n, parent)) {
        deprecatedDepth--;
      }

      methodDepth--;
      if (methodDepth == 0) {
        currentClass = null;
      }
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
    }

    return null;
  }

  /**
   * Normalize the type of a constructor, its instance, and its prototype
   * all down to the same type (the instance type).
   */
  private JSType normalizeClassType(JSType type) {
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

  /**
   * Determines whether the given name is visible in the current context.
   * @param t The current traversal.
   * @param name The name node.
   */
  private void checkNameVisibility(NodeTraversal t, Node name, Node parent) {
    Var var = t.getScope().getVar(name.getString());
    if (var != null) {
      JSDocInfo docInfo = var.getJSDocInfo();
      if (docInfo != null) {
        // If a name is private, make sure that we're in the same file.
        Visibility visibility = docInfo.getVisibility();
        if (visibility == Visibility.PRIVATE) {
          StaticSourceFile varSrc = var.getSourceFile();
          StaticSourceFile refSrc = name.getStaticSourceFile();
          if (varSrc != null &&
              refSrc != null &&
              !varSrc.getName().equals(refSrc.getName())) {
            if (docInfo.isConstructor() &&
                isValidPrivateConstructorAccess(parent)) {
              return;
            }

            compiler.report(
                t.makeError(name, BAD_PRIVATE_GLOBAL_ACCESS,
                    name.getString(), varSrc.getName()));
          }
        }
      }
    }
  }

  /**
   * Checks if a constructor is trying to override a final class.
   * @param t The current traversal.
   * @param name The name node.
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
   * Determines whether the given property with @const tag got reassigned
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

      ObjectType oType = objectType;
      while (oType != null) {
        if (oType.hasReferenceName()) {
          if (initializedConstantProperties.containsEntry(
                  oType.getReferenceName(), propertyName)) {
            compiler.report(
                t.makeError(getprop, CONST_PROPERTY_REASSIGNED_VALUE,
                    propertyName));
            break;
          }
        }
        oType = oType.getImplicitPrototype();
      }

      Preconditions.checkState(objectType.hasReferenceName());
      initializedConstantProperties.put(objectType.getReferenceName(),
          propertyName);

      // Add the prototype when we're looking at an instance object
      if (objectType.isInstanceType()) {
        ObjectType prototype = objectType.getImplicitPrototype();
        if (prototype != null) {
          if (prototype.hasProperty(propertyName)
              && prototype.hasReferenceName()) {
            initializedConstantProperties.put(prototype.getReferenceName(),
                propertyName);
          }
        }
      }
    }
  }

  /**
   * Determines whether the given property is visible in the current context.
   * @param t The current traversal.
   * @param getprop The getprop node.
   */
  private void checkPropertyVisibility(NodeTraversal t,
      Node getprop, Node parent) {
    ObjectType objectType =
        ObjectType.cast(dereference(getprop.getFirstChild().getJSType()));
    String propertyName = getprop.getLastChild().getString();

    if (objectType != null) {
      // Is this a normal property access, or are we trying to override
      // an existing property?
      boolean isOverride = parent.getJSDocInfo() != null &&
          parent.isAssign() &&
          parent.getFirstChild() == getprop;

      // Find the lowest property defined on a class with visibility
      // information.
      if (isOverride) {
        objectType = objectType.getImplicitPrototype();
      }
      JSDocInfo docInfo = null;
      for (; objectType != null;
           objectType = objectType.getImplicitPrototype()) {
        docInfo = objectType.getOwnPropertyJSDocInfo(propertyName);
        if (docInfo != null &&
            docInfo.getVisibility() != Visibility.INHERITED) {
          break;
        }
      }

      if (objectType == null) {
        // We couldn't find a visibility modifier; assume it's public.
        return;
      }

      String referenceSource = getprop.getSourceFileName();
      String definingSource = docInfo.getSourceName();
      boolean sameInput = referenceSource != null
          && referenceSource.equals(definingSource);
      Visibility visibility = docInfo.getVisibility();
      JSType ownerType = normalizeClassType(objectType);
      if (isOverride) {
        // Check an ASSIGN statement that's trying to override a property
        // on a superclass.
        JSDocInfo overridingInfo = parent.getJSDocInfo();
        Visibility overridingVisibility = overridingInfo == null ?
            Visibility.INHERITED : overridingInfo.getVisibility();

        // Check that (a) the property *can* be overridden, and
        // (b) that the visibility of the override is the same as the
        // visibility of the original property.
        if (visibility == Visibility.PRIVATE && !sameInput) {
          compiler.report(
              t.makeError(getprop, PRIVATE_OVERRIDE,
                  objectType.toString()));
        } else if (overridingVisibility != Visibility.INHERITED &&
            overridingVisibility != visibility) {
          compiler.report(
              t.makeError(getprop, VISIBILITY_MISMATCH,
                  visibility.name(), objectType.toString(),
                  overridingVisibility.name()));
        }
      } else {
        if (sameInput) {
          // private access is always allowed in the same file.
          return;
        } else if (visibility == Visibility.PRIVATE &&
            (currentClass == null || !ownerType.isEquivalentTo(currentClass))) {
          if (docInfo.isConstructor() &&
              isValidPrivateConstructorAccess(parent)) {
            return;
          }

          // private access is not allowed outside the file from a different
          // enclosing class.
          compiler.report(
              t.makeError(getprop,
                  BAD_PRIVATE_PROPERTY_ACCESS,
                  propertyName,
                  validator.getReadableJSTypeName(
                      getprop.getFirstChild(), true)));
        } else if (visibility == Visibility.PROTECTED) {
          // There are 3 types of legal accesses of a protected property:
          // 1) Accesses in the same file
          // 2) Overriding the property in a subclass
          // 3) Accessing the property from inside a subclass
          // The first two have already been checked for.
          if (currentClass == null || !currentClass.isSubtype(ownerType)) {
            compiler.report(
                t.makeError(getprop,  BAD_PROTECTED_PROPERTY_ACCESS,
                    propertyName,
                    validator.getReadableJSTypeName(
                        getprop.getFirstChild(), true)));
          }
        }
      }
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
  private static boolean isDeprecatedFunction(Node n, Node parent) {
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
  private static boolean isPropertyDeclaredConstant(
      ObjectType objectType, String prop) {
    for (;
         // Only objects with reference names can have constant properties.
         objectType != null && objectType.hasReferenceName();

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
  private JSType getFinalParentClass(JSType type) {
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
