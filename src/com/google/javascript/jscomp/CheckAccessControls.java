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

import com.google.auto.value.AutoValue;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Objects;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * A compiler pass that checks that the programmer has obeyed all the access control restrictions
 * indicated by JSDoc annotations, like {@code @private} and {@code @deprecated}.
 *
 * <p>Because access control restrictions are attached to type information, this pass must run after
 * TypeInference, and InferJSDocInfo.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
class CheckAccessControls implements Callback, HotSwapCompilerPass {

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
          "Overridden property {0} in file with fileoverview visibility {1}"
              + " must explicitly redeclare superclass visibility");

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
  private final JSTypeRegistry typeRegistry;
  private final boolean enforceCodingConventions;

  // State about the current traversal.
  private int deprecationDepth = 0;
  // NOTE: LinkedList is almost always the wrong choice, but in this case we have at most a small
  // handful of elements, it provides the smoothest API (push, pop, and a peek that doesn't throw
  // on empty), and (unlike ArrayDeque) is null-permissive. No other option meets all these needs.
  private final Deque<ObjectType> currentClassStack = new LinkedList<>();

  private ImmutableMap<StaticSourceFile, Visibility> defaultVisibilityForFiles;
  private final Multimap<JSType, String> initializedConstantProperties;


  CheckAccessControls(
      AbstractCompiler compiler, boolean enforceCodingConventions) {
    this.compiler = compiler;
    this.typeRegistry = compiler.getTypeRegistry();
    this.initializedConstantProperties = HashMultimap.create();
    this.enforceCodingConventions = enforceCodingConventions;
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

  private void enterAccessControlScope(Node root) {
    @Nullable ObjectType scopeType = bestInstanceTypeForMethodOrCtor(root);

    if (isMarkedDeprecated(root)) {
      deprecationDepth++;
    }
    currentClassStack.push(scopeType);
  }

  private void exitAccessControlScope(Node root) {
    if (isMarkedDeprecated(root)) {
      deprecationDepth--;
    }
    currentClassStack.pop();
  }

  /**
   * Maps {@code node} to the <em>primary</em> root of an access-control scope if it is some root,
   * or {@code null} if it is a non-root of the scope.
   *
   * <p>We define access-control scopes differently from {@code Scope}s because of mismatches
   * between ECMAScript scoping and our AST structure (e.g. the `extends` clause of a CLASS). {@link
   * NodeTraveral} is designed to walk the AST in ECMAScript scope order, which is not the pre-order
   * traversal that we would prefer here. This requires us to treat access-control scopes as a
   * forest with a primary root.
   *
   * <p>Each access-control scope corresponds to some "owner" JavaScript type which is used when
   * computing access-controls. At this time, each also corresponds to an AST subtree.
   *
   * <p>Known mismatches:
   *
   * <ul>
   *   <li>CLASS extends clause: secondary root of the scope defined by the CLASS.
   * </ul>
   */
  @Nullable
  private static Node primaryAccessControlScopeRootFor(Node node) {
    if (isExtendsTarget(node)) {
      return node.getParent();
    } else if (isFunctionOrClass(node)) {
      return node;
    } else {
      return null;
    }
  }

  /**
   * Returns the instance object type that best represents a method or constructor definition, or
   * {@code null} if there is no representative type.
   *
   * <ul>
   *   <li>Prototype methods => The instance type having that prototype
   *   <li>Instance methods => The type the method is attached to
   *   <li>Constructors => The type that constructor instantiates
   *   <li>Object-literal members => The object-literal type
   * </ul>
   */
  @Nullable
  private ObjectType bestInstanceTypeForMethodOrCtor(Node n) {
    checkState(isFunctionOrClass(n), n);
    Node parent = n.getParent();

    // We need to handle declaration syntaxes separately in a way that we can't determine based on
    // the type of just one node.
    // TODO(nickreid): Determine if these can be replaced with FUNCTION and CLASS cases below.
    if (NodeUtil.isFunctionDeclaration(n) || NodeUtil.isClassDeclaration(n)) {
      return instanceTypeFor(n.getJSType());
    }

    // All the remaining cases can be isolated based on `parent`.
    switch (parent.getToken()) {
      case NAME:
        return instanceTypeFor(n.getJSType());

      case ASSIGN:
        {
          Node lValue = parent.getFirstChild();
          if (NodeUtil.isGet(lValue)) {
            // We have an assignment of the form `a.b = ...`.
            JSType lValueType = lValue.getJSType();
            if (lValueType != null && (lValueType.isConstructor() || lValueType.isInterface())) {
              // Case `a.B = ...`
              return instanceTypeFor(lValueType);
            } else if (NodeUtil.isPrototypeProperty(lValue)) {
              // Case `a.B.prototype = ...`
              return instanceTypeFor(NodeUtil.getPrototypeClassName(lValue).getJSType());
            } else {
              // Case `a.b = ...`
              return instanceTypeFor(lValue.getFirstChild().getJSType());
            }
          } else {
            // We have an assignment of the form "a = ...", so pull the type off the "a".
            return instanceTypeFor(lValue.getJSType());
          }
        }

      case STRING_KEY:
      case GETTER_DEF:
      case SETTER_DEF:
      case MEMBER_FUNCTION_DEF:
      case COMPUTED_PROP:
        {
          Node grandparent = parent.getParent();
          Node greatGrandparent = grandparent.getParent();

          if (grandparent.isObjectLit()) {
            return grandparent.getJSType().isFunctionPrototypeType()
                // Case: `grandparent` is an object-literal prototype.
                // Example: `Foo.prototype = { a: function() {} };` where `parent` is "a".
                ? instanceTypeFor(grandparent.getJSType())
                : null;
          } else if (greatGrandparent.isClass()) {
            // Case: `n` is a class member definition.
            // Example: `class Foo { a() {} }` where `parent` is "a".
            return instanceTypeFor(greatGrandparent.getJSType());
          } else {
            // This would indicate the AST is malformed.
            throw new AssertionError(greatGrandparent);
          }
        }

      default:
        return null;
    }
  }

  /**
   * Returns the type that best represents the instance type for {@code type}.
   *
   * <ul>
   *   <li>Prototype type => The instance type having that prototype
   *   <li>Instance type => The type
   *   <li>Constructor type => The type that constructor instantiates
   *   <li>Object-literal type => The type
   * </ul>
   */
  @Nullable
  private static ObjectType instanceTypeFor(JSType type) {
    if (type == null) {
      return null;
    } else if (type.isUnionType()) {
      return null; // A union has no meaningful instance type.
    } else if (type.isInstanceType() || type.isUnknownType()) {
      return type.toMaybeObjectType();
    } else if (type.isConstructor() || type.isInterface()) {
      return type.toMaybeFunctionType().getInstanceType();
    } else if (type.isFunctionPrototypeType()) {
      return instanceTypeFor(type.toMaybeObjectType().getOwnerFunction());
    }

    return type.toMaybeObjectType();
  }

  @Override
  public boolean shouldTraverse(NodeTraversal traversal, Node node, Node parent) {
    @Nullable Node accessControlRoot = primaryAccessControlScopeRootFor(node);
    if (accessControlRoot != null) {
      enterAccessControlScope(accessControlRoot);
    }

    return true;
  }

  @Override
  public void visit(NodeTraversal traversal, Node node, Node parent) {

    IdentifierBehaviour identifierBehaviour = IdentifierBehaviour.select(node);
    @Nullable PropertyReference propRef = createPropertyReference(node);

    checkDeprecation(node, propRef, identifierBehaviour, traversal);
    checkVisibility(node, propRef, identifierBehaviour, traversal);
    checkConstantProperty(propRef, identifierBehaviour, traversal);

    checkFinalClassOverrides(node, traversal);

    @Nullable Node accessControlRoot = primaryAccessControlScopeRootFor(node);
    if (accessControlRoot != null) {
      exitAccessControlScope(accessControlRoot);
    }
  }

  private void checkDeprecation(
      Node node,
      @Nullable PropertyReference propRef,
      IdentifierBehaviour identifierBehaviour,
      NodeTraversal traversal) {

    switch (identifierBehaviour) {
      case ES5_CLASS_INVOCATION:
      case ES6_CLASS_INVOCATION:
      case ES6_CLASS_NAMESPACE:
        // At these usages, treat the deprecation applied to type-declaration as referring to the
        // type, not the identifier (e.g. "the use of class `Foo` is deprecated").
        checkTypeDeprecation(traversal, node);
        break;

      case NON_CONSTRUCTOR:
        // For all identifiers that are not constructors, deprecation refers to the identifier (e.g.
        // "the use of variable `x` is deprecated").
        checkNameDeprecation(traversal, node);
        break;

      default:
        break;
    }

    if (propRef != null && !identifierBehaviour.equals(IdentifierBehaviour.ES5_CLASS_NAMESPACE)) {
      checkPropertyDeprecation(traversal, propRef);
    }
  }

  private void checkVisibility(
      Node node,
      @Nullable PropertyReference propRef,
      IdentifierBehaviour identifierBehaviour,
      NodeTraversal traversal) {
    if (identifierBehaviour.equals(IdentifierBehaviour.ES6_CLASS_INVOCATION)) {
      checkEs6ConstructorInvocationVisibility(node, traversal);
    }

    if (!identifierBehaviour.equals(IdentifierBehaviour.ES5_CLASS_NAMESPACE)) {
      checkNameVisibility(traversal, node);
    }

    if (node.getParent().isObjectLit()) {
      // TODO(b/80580110): Eventually object literals should be covered by `PropertyReference`s.
      // However, doing so initially would have caused too many errors in existing code and
      // delayed support for class syntax.

      switch (node.getToken()) {
        case STRING_KEY:
        case GETTER_DEF:
        case SETTER_DEF:
        case MEMBER_FUNCTION_DEF:
          checkKeyVisibilityConvention(traversal, node, node.getParent());
          break;

        default:
          break;
      }
    }

    if (propRef != null && !identifierBehaviour.equals(IdentifierBehaviour.ES5_CLASS_NAMESPACE)) {
      checkPropertyVisibility(traversal, propRef);
    }
  }

  /**
   * Reports deprecation issue with regard to a type usage.
   *
   * <p>Precondition: {@code n} has a constructor {@link JSType}.
   */
  private void checkTypeDeprecation(NodeTraversal t, Node n) {
    if (!shouldEmitDeprecationWarning(t, n)) {
      return;
    }

    ObjectType instanceType = n.getJSType().toMaybeFunctionType().getInstanceType();

    String deprecationInfo = getTypeDeprecationInfo(instanceType);
    if (deprecationInfo == null) {
      return;
    }

    DiagnosticType message = deprecationInfo.isEmpty() ? DEPRECATED_CLASS : DEPRECATED_CLASS_REASON;
    compiler.report(t.makeError(n, message, instanceType.toString(), deprecationInfo));
  }

  /** Checks the given NAME node to ensure that access restrictions are obeyed. */
  private void checkNameDeprecation(NodeTraversal t, Node n) {
    if (!n.isName()) {
      return;
    }

    if (!shouldEmitDeprecationWarning(t, n)) {
      return;
    }

    Var var = t.getScope().getVar(n.getString());
    JSDocInfo docInfo = var == null ? null : var.getJSDocInfo();

    if (docInfo != null && docInfo.isDeprecated()) {
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

  /** Checks the given GETPROP node to ensure that access restrictions are obeyed. */
  private void checkPropertyDeprecation(NodeTraversal t, PropertyReference propRef) {
    if (!shouldEmitDeprecationWarning(t, propRef)) {
      return;
    }

    // Don't bother checking constructors.
    if (propRef.getSourceNode().getParent().isNew()) {
      return;
    }

    ObjectType objectType = castToObject(dereference(propRef.getReceiverType()));
    String propertyName = propRef.getName();

    if (objectType != null) {
      String deprecationInfo
          = getPropertyDeprecationInfo(objectType, propertyName);

      if (deprecationInfo != null) {

        if (!deprecationInfo.isEmpty()) {
          compiler.report(
              t.makeError(
                  propRef.getSourceNode(),
                  DEPRECATED_PROP_REASON,
                  propertyName,
                  propRef.getReadableTypeNameOrDefault(),
                  deprecationInfo));
        } else {
          compiler.report(
              t.makeError(
                  propRef.getSourceNode(),
                  DEPRECATED_PROP,
                  propertyName,
                  propRef.getReadableTypeNameOrDefault()));
        }
      }
    }
  }

  private boolean isPrivateByConvention(String name) {
    return enforceCodingConventions
        && compiler.getCodingConvention().isPrivate(name);
  }

  /**
   * Determines whether the given OBJECTLIT property visibility violates the coding convention.
   *
   * @param t The current traversal.
   * @param key The objectlit key node (STRING_KEY, GETTER_DEF, SETTER_DEF, MEMBER_FUNCTION_DEF).
   */
  private void checkKeyVisibilityConvention(NodeTraversal t, Node key, Node parent) {
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
   *
   * @param t The current traversal.
   * @param name The name node.
   */
  private void checkNameVisibility(NodeTraversal t, Node name) {
    if (!name.isName()) {
      return;
    }

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
        if (!isPrivateAccessAllowed(var, name)) {
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

  private static boolean isPrivateAccessAllowed(Var var, Node name) {
    StaticSourceFile varSrc = var.getSourceFile();
    StaticSourceFile refSrc = name.getStaticSourceFile();

    return varSrc == null || refSrc == null || Objects.equals(varSrc.getName(), refSrc.getName());
  }

  private boolean isPackageAccessAllowed(Var var, Node name) {
    StaticSourceFile varSrc = var.getSourceFile();
    StaticSourceFile refSrc = name.getStaticSourceFile();
    if (varSrc == null && refSrc == null) {
      // If the source file of either var or name is unavailable, conservatively assume they belong
      // to different packages.
      //
      // TODO(brndn): by contrast, isPrivateAccessAllowed does allow private access when a source
      // file is unknown. I didn't change it in order not to break existing code.
      return false;
    }

    CodingConvention codingConvention = compiler.getCodingConvention();
    String srcPackage = codingConvention.getPackageName(varSrc);
    String refPackage = codingConvention.getPackageName(refSrc);
    return srcPackage != null && refPackage != null && Objects.equals(srcPackage, refPackage);
  }

  private void checkOverriddenPropertyVisibilityMismatch(
      Visibility overriding,
      Visibility overridden,
      @Nullable Visibility fileOverview,
      NodeTraversal t,
      PropertyReference propRef) {
    if (overriding == Visibility.INHERITED
        && overriding != overridden
        && fileOverview != null
        && fileOverview != Visibility.INHERITED) {
      compiler.report(
          t.makeError(
              propRef.getSourceNode(),
              BAD_PROPERTY_OVERRIDE_IN_FILE_WITH_FILEOVERVIEW_VISIBILITY,
              propRef.getName(),
              fileOverview.name()));
    }
  }

  @Nullable
  private static Visibility getOverridingPropertyVisibility(PropertyReference propRef) {
    JSDocInfo overridingInfo = propRef.getJSDocInfo();
    return overridingInfo == null || !overridingInfo.isOverride()
        ? null
        : overridingInfo.getVisibility();
  }

  /** Checks if a constructor is trying to override a final class. */
  private void checkFinalClassOverrides(Node ctor, NodeTraversal t) {
    if (!isFunctionOrClass(ctor)) {
      return;
    }

    JSType type = ctor.getJSType().toMaybeFunctionType();
    if (type != null && type.isConstructor()) {
      JSType finalParentClass = getSuperClassInstanceIfFinal(bestInstanceTypeForMethodOrCtor(ctor));
      if (finalParentClass != null) {
        compiler.report(
            t.makeError(
                ctor,
                EXTEND_FINAL_CLASS,
                type.getDisplayName(),
                finalParentClass.getDisplayName()));
      }
    }
  }

  /** Determines whether the given constant property got reassigned */
  private void checkConstantProperty(
      @Nullable PropertyReference propRef,
      IdentifierBehaviour identifierBehaviour,
      NodeTraversal t) {
    if (propRef == null || identifierBehaviour.equals(IdentifierBehaviour.ES5_CLASS_NAMESPACE)) {
      return;
    }

    if (!propRef.isMutation()) {
      return;
    }

    ObjectType objectType = castToObject(dereference(propRef.getReceiverType()));

    String propertyName = propRef.getName();

    boolean isConstant = isPropertyDeclaredConstant(objectType, propertyName);

    // Check whether constant properties are reassigned
    if (isConstant) {
      if (propRef.isDeletion()) {
        compiler.report(t.makeError(propRef.getSourceNode(), CONST_PROPERTY_DELETED, propertyName));
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
        if (initializedConstantProperties.containsEntry(oType, propertyName)
            || initializedConstantProperties.containsEntry(
                getCanonicalInstance(oType), propertyName)) {
          compiler.report(
              t.makeError(propRef.getSourceNode(), CONST_PROPERTY_REASSIGNED_VALUE, propertyName));
          break;
        }
        oType = oType.getImplicitPrototype();
      }

      initializedConstantProperties.put(objectType, propertyName);

      // Add the prototype when we're looking at an instance object
      if (objectType.isInstanceType()) {
        ObjectType prototype = objectType.getImplicitPrototype();
        if (prototype != null && prototype.hasProperty(propertyName)) {
          initializedConstantProperties.put(prototype, propertyName);
        }
      }
    }
  }

  /**
   * Return an object with the same nominal type as obj,
   * but without any possible extra properties that exist on obj.
   */
  static ObjectType getCanonicalInstance(ObjectType obj) {
    FunctionType ctor = obj.getConstructor();
    return ctor == null ? obj : ctor.getInstanceType();
  }

  /** Dereference a type, autoboxing it and filtering out null. */
  @Nullable
  private static ObjectType dereference(JSType type) {
    return type == null ? null : type.dereference();
  }

  private JSType typeOrUnknown(JSType type) {
    return (type == null) ? typeRegistry.getNativeType(JSTypeNative.UNKNOWN_TYPE) : type;
  }

  private ObjectType typeOrUnknown(ObjectType type) {
    return (ObjectType) typeOrUnknown((JSType) type);
  }

  private ObjectType boxedOrUnknown(@Nullable JSType type) {
    return typeOrUnknown(dereference(type));
  }

  /** Reports an error if the given property is not visible in the current context. */
  private void checkPropertyVisibility(NodeTraversal t, PropertyReference propRef) {
    JSType rawReferenceType = typeOrUnknown(propRef.getReceiverType()).autobox();
    ObjectType referenceType = castToObject(rawReferenceType);

    String propertyName = propRef.getName();
    boolean isPrivateByConvention = isPrivateByConvention(propertyName);

    if (isPrivateByConvention && propertyIsDeclaredButNotPrivate(propRef)) {
      compiler.report(t.makeError(propRef.getSourceNode(), CONVENTION_MISMATCH));
      return;
    }

    StaticSourceFile definingSource =
        AccessControlUtils.getDefiningSource(propRef.getSourceNode(), referenceType, propertyName);

    // Is this a normal property access, or are we trying to override
    // an existing property?
    boolean isOverride = propRef.isDocumentedDeclaration() || propRef.isOverride();

    ObjectType objectType = AccessControlUtils.getObjectType(
        referenceType, isOverride, propertyName);

    Visibility fileOverviewVisibility =
        defaultVisibilityForFiles.get(definingSource);

    Visibility visibility =
        getEffectivePropertyVisibility(
            propRef,
            referenceType,
            defaultVisibilityForFiles,
            enforceCodingConventions ? compiler.getCodingConvention() : null);

    if (isOverride) {
      Visibility overriding = getOverridingPropertyVisibility(propRef);
      if (overriding != null) {
        checkOverriddenPropertyVisibilityMismatch(
            overriding, visibility, fileOverviewVisibility, t, propRef);
      }
    }

    JSType reportType = rawReferenceType;
    if (objectType != null) {
      Node node = objectType.getOwnPropertyDefSite(propertyName);
      if (node == null) {
        // Assume the property is public.
        return;
      }
      reportType = objectType;
      definingSource = node.getStaticSourceFile();
    } else if (!isPrivateByConvention && fileOverviewVisibility == null) {
      // We can only check visibility references if we know what file
      // it was defined in.
      // Otherwise just assume the property is public.
      return;
    }

    StaticSourceFile referenceSource = propRef.getSourceNode().getStaticSourceFile();

    if (isOverride) {
      boolean sameInput = referenceSource != null
          && referenceSource.getName().equals(definingSource.getName());
      checkOverriddenPropertyVisibility(
          t, propRef, visibility, fileOverviewVisibility, reportType, sameInput);
    } else {
      checkNonOverriddenPropertyVisibility(
          t, propRef, visibility, reportType, referenceSource, definingSource);
    }
  }

  /**
   * Reports visibility violations on ES6 class constructor invocations.
   *
   * <p>Precondition: {@code target} has an ES6 class {@link JSType}.
   */
  private void checkEs6ConstructorInvocationVisibility(Node target, NodeTraversal traversal) {
    FunctionType ctorType = target.getJSType().toMaybeFunctionType();
    ObjectType prototypeType = ctorType.getPrototype();

    // We use the class definition site because classes automatically get a implicit constructor,
    // so there may not be a definition node.
    @Nullable Node classDefinition = ctorType.getSource();

    @Nullable
    StaticSourceFile definingSource =
        (classDefinition == null)
            ? null
            : AccessControlUtils.getDefiningSource(classDefinition, prototypeType, "constructor");

    // Synthesize a `PropertyReference` for this constructor call as if we're accessing
    // `Foo.prototype.constructor`. This object allows us to reuse the
    // `checkNonOverriddenPropertyVisibility` method which actually reports violations.
    PropertyReference fauxCtorRef =
        PropertyReference.builder()
            .setSourceNode(target)
            .setName("constructor")
            .setReceiverType(prototypeType)
            .setMutation(false) // This shouldn't matter.
            .setDeclaration(false) // This shouldn't matter.
            .setOverride(false) // This shouldn't matter.
            .setReadableTypeName(() -> ctorType.getInstanceType().toString())
            .build();

    Visibility annotatedCtorVisibility =
        // This function defaults to `INHERITED` which isn't what we want here, but it does handle
        // combining inline and `@fileoverview` visibilities.
        getEffectiveVisibilityForNonOverriddenProperty(
            fauxCtorRef,
            prototypeType,
            defaultVisibilityForFiles.get(definingSource),
            enforceCodingConventions ? compiler.getCodingConvention() : null);
    Visibility effectiveCtorVisibility =
        annotatedCtorVisibility.equals(Visibility.INHERITED)
            ? Visibility.PUBLIC
            : annotatedCtorVisibility;

    checkNonOverriddenPropertyVisibility(
        traversal,
        fauxCtorRef,
        effectiveCtorVisibility,
        ctorType,
        target.getStaticSourceFile(),
        definingSource);
  }

  private static boolean propertyIsDeclaredButNotPrivate(PropertyReference propRef) {
    if (!propRef.isDocumentedDeclaration() && !propRef.isOverride()) {
      return false;
    }

    Visibility declaredVisibility = propRef.getJSDocInfo().getVisibility();
    if (declaredVisibility == Visibility.PRIVATE || declaredVisibility == Visibility.INHERITED) {
      return false;
    }

    return true;
  }

  private void checkOverriddenPropertyVisibility(
      NodeTraversal t,
      PropertyReference propRef,
      Visibility visibility,
      Visibility fileOverviewVisibility,
      JSType objectType,
      boolean sameInput) {
    Visibility overridingVisibility =
        propRef.isOverride() ? propRef.getJSDocInfo().getVisibility() : Visibility.INHERITED;

    // Check that:
    // (a) the property *can* be overridden,
    // (b) the visibility of the override is the same as the
    //     visibility of the original property,
    // (c) the visibility is explicitly redeclared if the override is in
    //     a file with default visibility in the @fileoverview block.
    if (visibility == Visibility.PRIVATE && !sameInput) {
      compiler.report(
          t.makeError(propRef.getSourceNode(), PRIVATE_OVERRIDE, objectType.toString()));
    } else if (overridingVisibility != Visibility.INHERITED
        && overridingVisibility != visibility
        && fileOverviewVisibility == null) {
      compiler.report(
          t.makeError(
              propRef.getSourceNode(),
              VISIBILITY_MISMATCH,
              visibility.name(),
              objectType.toString(),
              overridingVisibility.name()));
    }
  }

  private void checkNonOverriddenPropertyVisibility(
      NodeTraversal t,
      PropertyReference propRef,
      Visibility visibility,
      JSType objectType,
      StaticSourceFile referenceSource,
      StaticSourceFile definingSource) {
    // private access is always allowed in the same file.
    if (referenceSource != null
        && definingSource != null
        && referenceSource.getName().equals(definingSource.getName())) {
      return;
    }

    @Nullable ObjectType ownerType = instanceTypeFor(objectType);

    switch (visibility) {
      case PACKAGE:
        checkPackagePropertyVisibility(t, propRef, referenceSource, definingSource);
        break;
      case PRIVATE:
        checkPrivatePropertyVisibility(t, propRef, ownerType);
        break;
      case PROTECTED:
        checkProtectedPropertyVisibility(t, propRef, ownerType);
        break;
      default:
        break;
    }
  }

  private void checkPackagePropertyVisibility(
      NodeTraversal t,
      PropertyReference propRef,
      StaticSourceFile referenceSource,
      StaticSourceFile definingSource) {
    CodingConvention codingConvention = compiler.getCodingConvention();
    String refPackage = codingConvention.getPackageName(referenceSource);
    String defPackage = codingConvention.getPackageName(definingSource);
    if (refPackage == null
        || defPackage == null
        || !refPackage.equals(defPackage)) {
      compiler.report(
          t.makeError(
              propRef.getSourceNode(),
              BAD_PACKAGE_PROPERTY_ACCESS,
              propRef.getName(),
              propRef.getReadableTypeNameOrDefault()));
      }
  }

  private void checkPrivatePropertyVisibility(
      NodeTraversal t,
      PropertyReference propRef,
      @Nullable ObjectType ownerType) {

    // private access is not allowed outside the file from a different
    // enclosing class.
    // TODO(tbreisacher): Should we also include the filename where ownerType is defined?
    String readableTypeName =
        Objects.equals(ownerType, propRef.getReceiverType())
            ? propRef.getReadableTypeNameOrDefault()
            : ownerType.toString();
    compiler.report(
        t.makeError(
            propRef.getSourceNode(),
            BAD_PRIVATE_PROPERTY_ACCESS,
            propRef.getName(),
            readableTypeName));
  }

  private void checkProtectedPropertyVisibility(
      NodeTraversal t, PropertyReference propRef, @Nullable ObjectType ownerType) {
    // There are 3 types of legal accesses of a protected property:
    // 1) Accesses in the same file
    // 2) Overriding the property in a subclass
    // 3) Accessing the property from inside a subclass
    // The first two have already been checked for.
    if (ownerType != null) {
      for (JSType scopeType : currentClassStack) {
        if (scopeType == null) {
          continue;
        } else if (scopeType.isSubtypeOf(ownerType)) {
          return;
        }
      }
    }

    compiler.report(
        t.makeError(
            propRef.getSourceNode(),
            BAD_PROTECTED_PROPERTY_ACCESS,
            propRef.getName(),
            propRef.getReadableTypeNameOrDefault()));
  }

  /**
   * Determines whether a deprecation warning should be emitted.
   *
   * @param t The current traversal.
   * @param n The node which we are checking.
   * @param parent The parent of the node which we are checking.
   */
  private boolean shouldEmitDeprecationWarning(NodeTraversal t, Node n) {
    // In the global scope, there are only two kinds of accesses that should
    // be flagged for warnings:
    // 1) Calls of deprecated functions and methods.
    // 2) Instantiations of deprecated classes.
    // For now, we just let everything else by.
    if (t.inGlobalScope()) {
      if (!NodeUtil.isInvocationTarget(n) && !n.isNew()) {
        return false;
      }
    }

    return !canAccessDeprecatedTypes(t);
  }

  /** Determines whether a deprecation warning should be emitted. */
  private boolean shouldEmitDeprecationWarning(NodeTraversal t, PropertyReference propRef) {
    // In the global scope, there are only two kinds of accesses that should
    // be flagged for warnings:
    // 1) Calls of deprecated functions and methods.
    // 2) Instantiations of deprecated classes.
    // For now, we just let everything else by.
    if (t.inGlobalScope()) {
      if (!NodeUtil.isInvocationTarget(propRef.getSourceNode())) {
        return false;
      }
    }

    // We can always assign to a deprecated property, to keep it up to date.
    if (propRef.isMutation()) {
      return false;
    }

    // Don't warn if the node is just declaring the property, not reading it.
    JSDocInfo jsdoc = propRef.getJSDocInfo();
    if (propRef.isDeclaration() && (jsdoc != null) && jsdoc.isDeprecated()) {
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
    Node scopeRoot = t.getClosestHoistScopeRoot();
    if (NodeUtil.isFunctionBlock(scopeRoot)) {
      scopeRoot = scopeRoot.getParent();
    }
    Node scopeRootParent = scopeRoot.getParent();

    return
    // Case #1
    (deprecationDepth > 0)
        // Case #2
        || (getTypeDeprecationInfo(getTypeOfThis(scopeRoot)) != null)
        // Case #3
        || (scopeRootParent != null
            && scopeRootParent.isAssign()
            && getTypeDeprecationInfo(bestInstanceTypeForMethodOrCtor(scopeRoot)) != null);
  }

  /**
   * Returns whether this node roots a subtree under which references to deprecated constructs are
   * allowed.
   */
  private static boolean isMarkedDeprecated(Node n) {
    return getDeprecationReason(NodeUtil.getBestJSDocInfo(n)) != null;
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

    String depReason = getDeprecationReason(type.getJSDocInfo());
    if (depReason != null) {
      return depReason;
    }

    ObjectType objType = castToObject(type);
    if (objType != null) {
      ObjectType implicitProto = objType.getImplicitPrototype();
      if (implicitProto != null) {
        return getTypeDeprecationInfo(implicitProto);
      }
    }
    return null;
  }

  private static String getDeprecationReason(JSDocInfo info) {
    if (info != null && info.isDeprecated()) {
      if (info.getDeprecationReason() != null) {
        return info.getDeprecationReason();
      }
      return "";
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
    for (; objectType != null; objectType = objectType.getImplicitPrototype()) {
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
  @Nullable
  private static String getPropertyDeprecationInfo(ObjectType type, String prop) {
    String depReason = getDeprecationReason(type.getOwnPropertyJSDocInfo(prop));
    if (depReason != null) {
      return depReason;
    }

    ObjectType implicitProto = type.getImplicitPrototype();
    if (implicitProto != null) {
      return getPropertyDeprecationInfo(implicitProto, prop);
    }
    return null;
  }

  /**
   * If the superclass is final, this method returns an instance of the superclass.
   */
  @Nullable
  private static ObjectType getSuperClassInstanceIfFinal(@Nullable JSType type) {
    if (type != null) {
      ObjectType obj = castToObject(type);
      FunctionType ctor = obj == null ? null : obj.getSuperClassConstructor();
      JSDocInfo doc = ctor == null ? null : ctor.getJSDocInfo();
      if (doc != null && doc.isFinal()) {
        return ctor.getInstanceType();
      }
    }
    return null;
  }

  @Nullable
  private static ObjectType castToObject(@Nullable JSType type) {
    return type == null ? null : type.toMaybeObjectType();
  }

  private static boolean isFunctionOrClass(Node n) {
    return n.isFunction() || n.isClass();
  }

  private static boolean isExtendsTarget(Node node) {
    Node parent = node.getParent();
    return parent.isClass() && node.isSecondChildOf(parent);
  }

  @Nullable
  private JSType getTypeOfThis(Node scopeRoot) {
    if (scopeRoot.isRoot() || scopeRoot.isScript()) {
      return castToObject(scopeRoot.getJSType());
    }

    checkArgument(scopeRoot.isFunction(), scopeRoot);

    JSType nodeType = scopeRoot.getJSType();
    if (nodeType != null && nodeType.isFunctionType()) {
      return nodeType.toMaybeFunctionType().getTypeOfThis();
    } else {
      // Executed when the current scope has not been typechecked.
      return null;
    }
  }

  /**
   * The set of ways in which JSDoc and identifier usage can interact.
   *
   * <p>Before ES6, for a given type, there was no way to differentate the declaration of the
   * constructor function, namespace object, and type; the declaration of all three constructs was
   * the same node. This lead to some assumptions were made about how access-control modifiers
   * applied to each.
   *
   * <p>At ES6, class-syntax separated the constructor function from the namespace object and type
   * declaration. This allowed finer grained control over access-control modifiers; however it broke
   * some of the eariler assumptions.
   *
   * <p>This type exists to simplify maintaining both sets of assumptions. It allows other code to
   * branch on behaviour in a more obvious way.
   *
   * <p>TODO(b/113127707): Make this unnecessary by better modeling or decomposing this pass.
   */
  private static enum IdentifierBehaviour {
    NON_CONSTRUCTOR,
    ES5_CLASS_INVOCATION,
    ES5_CLASS_NAMESPACE,
    ES6_CLASS_INVOCATION,
    ES6_CLASS_NAMESPACE;

    public static IdentifierBehaviour select(Node target) {
      JSType type = target.getJSType();
      if (type == null || !type.isFunctionType()) {
        // If we aren't sure what we're dealing with be more strict.
        return IdentifierBehaviour.NON_CONSTRUCTOR;
      }

      FunctionType ctorType = type.toMaybeFunctionType();
      if (!ctorType.isConstructor()) {
        return IdentifierBehaviour.NON_CONSTRUCTOR;
      }

      boolean isInvocation = NodeUtil.isInvocationTarget(target) || isExtendsTarget(target);
      boolean isEs6 = (ctorType.getSource() != null) && ctorType.getSource().isClass();

      if (!isEs6) {
        return isInvocation
            ? IdentifierBehaviour.ES5_CLASS_INVOCATION
            : IdentifierBehaviour.ES5_CLASS_NAMESPACE;
      } else {
        return isInvocation
            ? IdentifierBehaviour.ES6_CLASS_INVOCATION
            : IdentifierBehaviour.ES6_CLASS_NAMESPACE;
      }
    }
  }

  /**
   * A representation of an object property reference in JS code.
   *
   * <p>This is an abstraction to smooth over the various AST structures that can act on
   * <em>properties</em>. It is not useful for names (variables) or anonymous JS constructs.
   *
   * <p>This class should only be used within {@link CheckAccessControls}. Having package-private
   * visibility is a quirk of {@link AutoValue}.
   */
  @AutoValue
  abstract static class PropertyReference {

    public static Builder builder() {
      return new AutoValue_CheckAccessControls_PropertyReference.Builder();
    }

    /** The {@link Node} that spawned this reference. */
    public abstract Node getSourceNode();

    public abstract String getName();

    /** The type from which the property is referenced, not necessarily the one that declared it. */
    public abstract ObjectType getReceiverType();

    public abstract boolean isMutation();

    public abstract boolean isDeclaration();

    public abstract boolean isOverride();

    /**
     * A lazy source for a human-readable type name to use when generating messages.
     *
     * <p>Most users probably want {@link #getReadableTypeNameOrDefault()}.
     */
    public abstract Supplier<String> getReadableTypeName();

    @AutoValue.Builder
    abstract interface Builder {
      Builder setSourceNode(Node node);

      Builder setName(String name);

      Builder setReceiverType(ObjectType receiverType);

      Builder setMutation(boolean isMutation);

      Builder setDeclaration(boolean isDeclaration);

      Builder setOverride(boolean isOverride);

      Builder setReadableTypeName(Supplier<String> typeName);
      PropertyReference build();
    }

    // Derived properties.

    public final Node getParentNode() {
      return getSourceNode().getParent();
    }

    public final JSType getJSType() {
      return getSourceNode().getJSType();
    }

    @Nullable
    public final JSDocInfo getJSDocInfo() {
      return NodeUtil.getBestJSDocInfo(getSourceNode());
    }

    public final boolean isDocumentedDeclaration() {
      return isDeclaration() && (getJSDocInfo() != null);
    }

    public final boolean isDeletion() {
      return getSourceNode().getParent().isDelProp();
    }

    public final String getReadableTypeNameOrDefault() {
      String preferred = getReadableTypeName().get();
      return preferred.isEmpty() ? getReceiverType().toString() : preferred;
    }
  }

  @Nullable
  private PropertyReference createPropertyReference(Node sourceNode) {
    Node parent = sourceNode.getParent();
    @Nullable JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(sourceNode);

    PropertyReference.Builder builder = PropertyReference.builder();

    switch (sourceNode.getToken()) {
      case GETPROP:
        {
          boolean isLValue = NodeUtil.isLValue(sourceNode);

          builder
              .setName(sourceNode.getLastChild().getString())
              .setReceiverType(boxedOrUnknown(sourceNode.getFirstChild().getJSType()))
              // Props are always mutated as L-values, even when assigned `undefined`.
              .setMutation(isLValue || sourceNode.getParent().isDelProp())
              .setDeclaration(parent.isExprResult())
              // TODO(b/113704668): This definition is way too loose. It was used to prevent
              // breakages during refactoring and should be tightened.
              .setOverride((jsdoc != null) && isLValue)
              .setReadableTypeName(
                  () -> typeRegistry.getReadableTypeName(sourceNode.getFirstChild()));
        }
        break;

      case STRING_KEY:
      case GETTER_DEF:
      case SETTER_DEF:
      case MEMBER_FUNCTION_DEF:
        {
          switch (parent.getToken()) {
            case OBJECTLIT:
              // TODO(b/80580110): Eventually object-literal members should be covered by
              // `PropertyReference`s. However, doing so initially would have caused too many errors
              // in existing code and delayed support for class syntax.
              return null;

            case OBJECT_PATTERN:
              builder
                  .setName(sourceNode.getString())
                  .setReceiverType(typeOrUnknown(ObjectType.cast(parent.getJSType())))
                  .setMutation(false)
                  .setDeclaration(false)
                  .setOverride(false)
                  .setReadableTypeName(() -> typeRegistry.getReadableTypeName(parent));
              break;

            case CLASS_MEMBERS:
              builder
                  .setName(sourceNode.getString())
                  .setReceiverType(typeRegistry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE))
                  .setMutation(true)
                  .setDeclaration(true)
                  // TODO(b/113704668): This definition is way too loose. It was used to prevent
                  // breakages during refactoring and should be tightened.
                  .setOverride(jsdoc != null)
                  .setReadableTypeName(() -> ""); // The default is fine for class types.

              JSType ctorType = parent.getParent().getJSType();
              if (ctorType != null && ctorType.isFunctionType()) {
                FunctionType ctorFunctionType = ctorType.toMaybeFunctionType();
                builder.setReceiverType(
                    sourceNode.isStaticMember()
                        ? ctorFunctionType
                        : ctorFunctionType.getPrototype());
              }
              break;

            default:
              throw new AssertionError();
          }
        }
        break;

      default:
        return null;
    }

    return builder.setSourceNode(sourceNode).build();
  }

  /**
   * Returns the effective visibility of the given property. This can differ from the property's
   * declared visibility if the property is inherited from a superclass, or if the file's
   * {@code @fileoverview} JsDoc specifies a default visibility.
   *
   * <p>TODO(b/111789692): The following methods are forked from `AccessControlUtils`. Consider
   * consolidating them.
   *
   * @param referenceType The JavaScript type of the property.
   * @param fileVisibilityMap A map of {@code @fileoverview} visibility annotations, used to compute
   *     the property's default visibility.
   * @param codingConvention The coding convention in effect (if any), used to determine whether the
   *     property is private by lexical convention (example: trailing underscore).
   */
  static Visibility getEffectivePropertyVisibility(
      PropertyReference propRef,
      ObjectType referenceType,
      ImmutableMap<StaticSourceFile, Visibility> fileVisibilityMap,
      @Nullable CodingConvention codingConvention) {
    String propertyName = propRef.getName();
    boolean isOverride = propRef.isOverride();

    StaticSourceFile definingSource =
        AccessControlUtils.getDefiningSource(propRef.getSourceNode(), referenceType, propertyName);
    Visibility fileOverviewVisibility = fileVisibilityMap.get(definingSource);
    ObjectType objectType =
        AccessControlUtils.getObjectType(referenceType, isOverride, propertyName);

    if (isOverride) {
      Visibility overridden =
          AccessControlUtils.getOverriddenPropertyVisibility(objectType, propertyName);
      return AccessControlUtils.getEffectiveVisibilityForOverriddenProperty(
          overridden, fileOverviewVisibility, propertyName, codingConvention);
    } else {
      return getEffectiveVisibilityForNonOverriddenProperty(
          propRef, objectType, fileOverviewVisibility, codingConvention);
    }
  }

  /**
   * Returns the effective visibility of the given non-overridden property. Non-overridden
   * properties without an explicit visibility annotation receive the default visibility declared in
   * the file's {@code @fileoverview} block, if one exists.
   *
   * <p>TODO(b/111789692): The following methods are forked from `AccessControlUtils`. Consider
   * consolidating them.
   */
  private static Visibility getEffectiveVisibilityForNonOverriddenProperty(
      PropertyReference propRef,
      ObjectType objectType,
      @Nullable Visibility fileOverviewVisibility,
      @Nullable CodingConvention codingConvention) {
    String propertyName = propRef.getName();
    if (codingConvention != null && codingConvention.isPrivate(propertyName)) {
      return Visibility.PRIVATE;
    }
    Visibility raw = Visibility.INHERITED;
    if (objectType != null) {
      JSDocInfo jsdoc = objectType.getOwnPropertyJSDocInfo(propertyName);
      if (jsdoc != null) {
        raw = jsdoc.getVisibility();
      }
    }
    JSType type = propRef.getJSType();
    boolean createdFromGoogProvide = (type != null && type.isLiteralObject());
    // Ignore @fileoverview visibility when computing the effective visibility
    // for properties created by goog.provide.
    //
    // ProcessClosurePrimitives rewrites goog.provide()s as object literal
    // declarations, but the exact form depends on the ordering of the
    // input files. If goog.provide('a.b.c') occurs in the inputs before
    // goog.provide('a'), it is rewritten like
    //
    // var a={};a.b={}a.b.c={};
    //
    // If the file containing goog.provide('a.b.c') also declares
    // a @fileoverview visibility, it must not apply to b, as this would make
    // every a.b.* namespace effectively package-private.
    return (raw != Visibility.INHERITED || fileOverviewVisibility == null || createdFromGoogProvide)
        ? raw
        : fileOverviewVisibility;
  }
}
