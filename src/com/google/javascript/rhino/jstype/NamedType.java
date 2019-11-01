/*
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Bob Jervis
 *   Google Inc.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package com.google.javascript.rhino.jstype;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.StaticScope;
import com.google.javascript.rhino.StaticSlot;
import com.google.javascript.rhino.jstype.JSTypeRegistry.ModuleSlot;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A {@code NamedType} is a named reference to some other type.  This provides
 * a convenient mechanism for implementing forward references to types; a
 * {@code NamedType} can be used as a placeholder until its reference is
 * resolved.  It is also useful for representing type names in JsDoc type
 * annotations, some of which may never be resolved (as they may refer to
 * types in host systems not yet supported by JSCompiler, such as the JVM.)<p>
 *
 * An important distinction: {@code NamedType} is a type name reference,
 * whereas {@link ObjectType} is a named type object, such as an Enum name.
 * The Enum itself is typically used only in a dot operator to name one of its
 * constants, or in a declaration, where its name will appear in a
 * NamedType.<p>
 *
 * A {@code NamedType} is not currently a full-fledged typedef, because it
 * cannot resolve to any JavaScript type.  It can only resolve to a named
 * {@link JSTypeRegistry} type, or to {@link FunctionType} or
 * {@link EnumType}.<p>
 *
 * If full typedefs are to be supported, then each method on each type class
 * needs to be reviewed to make sure that everything works correctly through
 * typedefs.  Alternatively, we would need to walk through the parse tree and
 * unroll each reference to a {@code NamedType} to its resolved type before
 * applying the rest of the analysis.<p>
 *
 * TODO(user): Revisit all of this logic.<p>
 *
 * The existing typing logic is hacky.  Unresolved types should get processed
 * in a more consistent way, but with the Rhino merge coming, there will be
 * much that has to be changed.<p>
 *
 */
public final class NamedType extends ProxyObjectType {
  private static final long serialVersionUID = 1L;

  static int nominalHashCode(ObjectType type) {
    checkState(type.hasReferenceName());
    String name = checkNotNull(type.getReferenceName());
    return name.hashCode();
  }

  private final String reference;
  private final String sourceName;
  private final int lineno;
  private final int charno;
  private final boolean nonNull;

  /**
   * Validates the type resolution.
   */
  private transient Predicate<JSType> validator;

  /** Property-defining continuations. */
  private transient List<PropertyContinuation> propertyContinuations = null;

  /**
   * Template types defined on a named, not yet resolved type, or {@code null} if none. These are
   * ignored during resolution, for backwards compatibility with existing usage. This field is not
   * used for JSCompiler's type checking; it is only needed by Clutz.
   */
  @Nullable private final ImmutableList<JSType> templateTypes;

  @Nullable private StaticTypedScope resolutionScope;

  /** Create a named type based on the reference. */
  NamedType(
      StaticTypedScope scope,
      JSTypeRegistry registry,
      String reference,
      String sourceName,
      int lineno,
      int charno) {
    this(scope, registry, reference, sourceName, lineno, charno, null);
  }

  NamedType(
      StaticTypedScope scope,
      JSTypeRegistry registry,
      String reference,
      String sourceName,
      int lineno,
      int charno,
      ImmutableList<JSType> templateTypes) {
    super(registry, registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE));
    this.nonNull = reference.startsWith("!");
    this.resolutionScope = scope;
    this.reference = nonNull ? reference.substring(1) : reference;
    this.sourceName = sourceName;
    this.lineno = lineno;
    this.charno = charno;
    this.templateTypes = templateTypes;
  }

  /** Returns a new non-null version of this type. */
  JSType getBangType() {
    if (nonNull) {
      return this;
    } else if (resolutionScope == null) {
      // Already resolved, just restrict.
      return getReferencedType().restrictByNotNullOrUndefined();
    }
    return new NamedType(
        resolutionScope, registry, "!" + reference, sourceName, lineno, charno, templateTypes);
  }

  @Override
  public ImmutableList<JSType> getTemplateTypes() {
    return templateTypes;
  }

  @Override
  boolean defineProperty(String propertyName, JSType type,
      boolean inferred, Node propertyNode) {
    if (!isResolved()) {
      // If this is an unresolved object type, we need to save all its
      // properties and define them when it is resolved.
      if (propertyContinuations == null) {
        propertyContinuations = new ArrayList<>();
      }
      propertyContinuations.add(
          new PropertyContinuation(
              propertyName, type, inferred, propertyNode));
      return true;
    } else {
      return super.defineProperty(
          propertyName, type, inferred, propertyNode);
    }
  }

  private void finishPropertyContinuations() {
    ObjectType referencedObjType = getReferencedObjTypeInternal();
    if (referencedObjType != null
        && !referencedObjType.isUnknownType()
        && propertyContinuations != null) {
      for (PropertyContinuation c : propertyContinuations) {
        c.commit(this);
      }
    }
    propertyContinuations = null;
  }

  /** Returns the type to which this refers (which is unknown if unresolved). */
  public JSType getReferencedType() {
    return getReferencedTypeInternal();
  }

  @Override
  public String getReferenceName() {
    return reference;
  }

  @Override
  StringBuilder appendTo(StringBuilder sb, boolean forAnnotations) {
    JSType type = this.getReferencedType();
    if (!isResolved() || type.isNoResolvedType()) {
      return sb.append(this.reference);
    } else {
      return type.appendTo(sb, forAnnotations);
    }
  }

  @Override
  public NamedType toMaybeNamedType() {
    return this;
  }

  @Override
  public boolean isNominalType() {
    return true;
  }

  @Override
  int recursionUnsafeHashCode() {
    // Recall that equality on `NamedType` uses only the name until successful resolution, then
    // delegates to the resolved type.
    return isSuccessfullyResolved() ? super.recursionUnsafeHashCode() : nominalHashCode(this);
  }

  /**
   * Resolve the referenced type within the enclosing scope.
   */
  @Override
  JSType resolveInternal(ErrorReporter reporter) {
    if (!getReferencedType().isUnknownType()) {
      // In some cases (e.g. typeof(ns) when the actual type is just a literal object), a NamedType
      // is created solely for the purpose of naming an already-known type. When that happens,
      // there's nothing to look up, so just resolve the referenced type.
      return super.resolveInternal(reporter);
    }

    boolean resolved = false;
    if (reference.startsWith("typeof ")) {
      resolveTypeof(reporter);
      resolved = true;
    }

    // TODO(user): Investigate whether it is really necessary to keep two
    // different mechanisms for resolving named types, and if so, which order
    // makes more sense.
    // The `resolveViaClosureNamespace` mechanism can probably be deleted (or reworked) once the
    // compiler supports type annotations via path. The `resolveViaProperties` and
    // `resolveViaRegistry` are, unfortunately, both needed now with no migration plan.
    resolved =
        resolved
            || resolveViaClosureNamespace(reporter)
            || resolveViaRegistry(reporter);
    if (!resolved) {
      resolveViaProperties(reporter);
    }

    if (detectInheritanceCycle()) {
      handleTypeCycle(reporter);
    }
    super.resolveInternal(reporter);
    finishPropertyContinuations();

    JSType result = getReferencedType();
    if (isSuccessfullyResolved()) {
      this.resolutionScope = null;

      int numKeys = result.getTemplateParamCount();
      if (!result.isObjectType() || templateTypes == null || templateTypes.isEmpty()) {
        return result;
      }

      ImmutableList<JSType> resolvedTypeArgs =
          JSTypeIterations.mapTypes((t) -> t.resolve(reporter), this.templateTypes);
      // Ignore any extraneous type args (but only after resolving them!)
      // TODO(johnlenz): report an error
      if (numKeys < this.templateTypes.size()) {
        resolvedTypeArgs = resolvedTypeArgs.subList(0, numKeys);
      }

      result = registry.createTemplatizedType(result.toMaybeObjectType(), resolvedTypeArgs);
      setReferencedType(result.resolve(reporter));
    }
    return result;
  }

  /**
   * Resolves a named type by looking it up in the registry.
   * @return True if we resolved successfully.
   */
  private boolean resolveViaRegistry(ErrorReporter reporter) {
    JSType type = registry.getType(resolutionScope, reference);
    if (type != null) {
      setReferencedAndResolvedType(type, reporter);
      return true;
    }
    return false;
  }

  /**
   * Resolves a named type by looking up its first component in the scope, and subsequent components
   * as properties. The scope must have been fully parsed and a symbol table constructed.
   */
  private void resolveViaProperties(ErrorReporter reporter) {
    List<String> componentNames = Splitter.on('.').splitToList(reference);
    if (componentNames.get(0).isEmpty()) {
      handleUnresolvedType(reporter, /* ignoreForwardReferencedTypes= */ true);
      return;
    }

    StaticTypedSlot slot =
        checkNotNull(resolutionScope, "resolutionScope")
            .getSlot(checkNotNull(componentNames, "componentNames").get(0));
    if (slot == null) {
      handleUnresolvedType(reporter, /* ignoreForwardReferencedTypes= */ true);
      return;
    }
    Node definitionNode = slot.getDeclaration() != null ? slot.getDeclaration().getNode() : null;
    resolveViaPropertyGivenSlot(
        slot.getType(), definitionNode, componentNames, reporter, /* componentIndex= */ 1);
  }

  /**
   * Resolve a type using a given StaticTypedSlot and list of properties on that type.
   *
   * @param slotType the JSType of teh slot, possibly null
   * @param definitionNode If known, the Node representing the type definition.
   * @param componentIndex the index into {@code componentNames} at which to start resolving
   */
  private void resolveViaPropertyGivenSlot(
      JSType slotType,
      Node definitionNode,
      List<String> componentNames,
      ErrorReporter reporter,
      int componentIndex) {
    if (resolveTypeFromNodeIfTypedef(definitionNode, reporter)) {
      return;
    }

    // If the first component has a type of 'Unknown', then any type
    // names using it should be regarded as silently 'Unknown' rather than be
    // noisy about it.
    if (slotType == null || slotType.isAllType() || slotType.isNoType()) {
      handleUnresolvedType(reporter, /* ignoreForwardReferencedTypes= */ true);
      return;
    }

    // resolving component by component
    for (int i = componentIndex; i < componentNames.size(); i++) {
      String component = componentNames.get(i);
      ObjectType parentObj = ObjectType.cast(slotType);
      if (parentObj == null || component.length() == 0) {
        handleUnresolvedType(reporter, /* ignoreForwardReferencedTypes= */ true);
        return;
      }
      if (i == componentNames.size() - 1) {
        // Look for a typedefTypeProp on the definition node of the last component.
        Node def = parentObj.getPropertyDefSite(component);
        if (resolveTypeFromNodeIfTypedef(def, reporter)) {
          return;
        }
      }
      slotType = parentObj.getPropertyType(component);
    }

    // Translate "constructor" types to "instance" types.
    if (slotType == null) {
      handleUnresolvedType(reporter, /* ignoreForwardReferencedTypes= */ true);
    } else if (slotType.isFunctionType() && (slotType.isConstructor() || slotType.isInterface())) {
      setReferencedAndResolvedType(slotType.toMaybeFunctionType().getInstanceType(), reporter);
    } else if (slotType.isNoObjectType()) {
      setReferencedAndResolvedType(
          registry.getNativeObjectType(JSTypeNative.NO_OBJECT_TYPE), reporter);
    } else if (slotType instanceof EnumType) {
      setReferencedAndResolvedType(((EnumType) slotType).getElementsType(), reporter);
    } else {
      // We've been running into issues where people forward-declare
      // non-named types. (This is legitimate...our dependency management
      // code doubles as our forward-declaration code.)
      //
      // So if the type does resolve to an actual value, but it's not named,
      // then don't respect the forward declaration.
      handleUnresolvedType(reporter, slotType.isUnknownType());
    }
  }

  private void resolveTypeof(ErrorReporter reporter) {
    String name = reference.substring("typeof ".length());
    // TODO(sdh): require var to be const?
    JSType type = resolutionScope.lookupQualifiedName(QualifiedName.of(name));
    if (type == null || type.isUnknownType()) {
      warning(reporter, "Missing type for `typeof` value. The value must be declared and const.");
      setReferencedAndResolvedType(registry.getNativeType(JSTypeNative.UNKNOWN_TYPE), reporter);
    } else {
      if (type.isLiteralObject()) {
        // Create an extra layer of wrapping so that the "typeof" name is preserved for namespaces.
        // This is depended on by Clutz to prevent infinite loops in self-referential typeof types.
        JSType objlit = type;
        type = registry.createNamedType(resolutionScope, reference, sourceName, lineno, charno);
        ((NamedType) type).setReferencedType(objlit);
      }
      setReferencedAndResolvedType(type, reporter);
    }
  }

  /**
   * Resolves a named type by checking for the longest prefix that matches some Closure namespace,
   * if any, then attempting to resolve via properties based on the type of the `exports` object in
   * that namespace.
   */
  private boolean resolveViaClosureNamespace(ErrorReporter reporter) {
    List<String> componentNames = Splitter.on('.').splitToList(reference);
    if (componentNames.get(0).isEmpty()) {
      return false;
    }

    StaticTypedSlot slot = resolutionScope.getSlot(componentNames.get(0));
    // Skip types whose root component is defined in a local scope (not a global scope). Those will
    // follow the normal resolution scheme. (For legacy compatibility reasons we don't check for
    // global names that are the same as the module root).
    if (slot != null && slot.getScope() != null && slot.getScope().getParentScope() != null) {
      return false;
    }

    // Find the `exports` type of the longest prefix match of this namespace, if any. Then resolve
    // it via property.
    String prefix = reference;

    for (int remainingComponentIndex = componentNames.size();
        remainingComponentIndex > 0;
        remainingComponentIndex--) {
      ModuleSlot module = registry.getModuleSlot(prefix);
      if (module == null) {
        int lastDot = prefix.lastIndexOf(".");
        if (lastDot >= 0) {
          prefix = prefix.substring(0, lastDot);
        }
        continue;
      }

      if (module.isLegacyModule()) {
        // Try to resolve this name via registry or properties.
        return false;
      } else {
        // Always stop resolution here whether successful or not, instead of continuing with
        // resolution via registry or via properties, to match legacy behavior.
        resolveViaPropertyGivenSlot(
            module.type(),
            module.definitionNode(),
            componentNames,
            reporter,
            remainingComponentIndex);
        return true;
      }
    }
    return false; // Keep trying to resolve this name.
  }

  /** Checks the given Node for a typedef annotation, resolving to that type if existent. */
  private boolean resolveTypeFromNodeIfTypedef(Node node, ErrorReporter reporter) {
    if (node == null) {
      return false;
    }
    JSType typedefType = node.getTypedefTypeProp();
    if (typedefType == null) {
      return false;
    }
    setReferencedAndResolvedType(typedefType, reporter);
    return true;
  }

  private void setReferencedAndResolvedType(
      JSType type, ErrorReporter reporter) {
    if (nonNull) {
      type = type.restrictByNotNullOrUndefined();
    }
    if (validator != null) {
      validator.apply(type);
    }
    setReferencedType(type);
    checkEnumElementCycle(reporter);
    checkProtoCycle(reporter);
    setResolvedTypeInternal(getReferencedType());
  }

  private void handleTypeCycle(ErrorReporter reporter) {
    setReferencedType(
        registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE));
    warning(reporter, "Cycle detected in inheritance chain of type " + reference);
    setResolvedTypeInternal(getReferencedType());
  }

  private void checkEnumElementCycle(ErrorReporter reporter) {
    JSType referencedType = getReferencedType();
    if (referencedType instanceof EnumElementType
        && areIdentical(this, ((EnumElementType) referencedType).getPrimitiveType())) {
      handleTypeCycle(reporter);
    }
  }

  private void checkProtoCycle(ErrorReporter reporter) {
    JSType referencedType = getReferencedType();
    if (areIdentical(referencedType, this)) {
      handleTypeCycle(reporter);
    }
  }

  /** Warns about this type being unresolved iff it's not a forward-declared type name */
  private void handleUnresolvedType(
      ErrorReporter reporter, boolean ignoreForwardReferencedTypes) {
    boolean isForwardDeclared =
        ignoreForwardReferencedTypes && registry.isForwardDeclaredType(reference);
    if (!isForwardDeclared) {
      String msg = "Bad type annotation. Unknown type " + reference;
      // Look for a local variable that shadows a global namespace to give a clearer message.
      String root =
          reference.contains(".") ? reference.substring(0, reference.indexOf(".")) : reference;
      if (localVariableShadowsGlobalNamespace(root)) {
        msg += "\nIt's possible that a local variable called '" + root
            + "' is shadowing the intended global namespace.";
      }
      warning(reporter, msg);
    } else {
      setReferencedType(new NoResolvedType(registry, getReferenceName(), getTemplateTypes()));
      if (validator != null) {
        validator.apply(getReferencedType());
      }
    }

    setResolvedTypeInternal(getReferencedType());
  }

  /**
   * Check for an obscure but very confusing error condition where a local variable shadows a
   * global namespace.
   */
  private boolean localVariableShadowsGlobalNamespace(String root) {
    StaticSlot rootVar = resolutionScope.getSlot(root);
    if (rootVar != null) {
      StaticScope parent = rootVar.getScope().getParentScope();
      if (parent != null) {
        StaticSlot globalVar = parent.getSlot(root);
        return globalVar != null;
      }
    }
    return false;
  }

  @Override
  public boolean setValidator(Predicate<JSType> validator) {
    // If the type is already resolved, we can validate it now. If
    // the type has not been resolved yet, we need to wait till its
    // resolved before we can validate it.
    if (this.isResolved()) {
      return super.setValidator(validator);
    } else {
      this.validator = validator;
      return true;
    }
  }

  void warning(ErrorReporter reporter, String message) {
    reporter.warning(message, sourceName, lineno, charno);
  }

  /** Store enough information to define a property at a later time. */
  private static final class PropertyContinuation {
    private final String propertyName;
    private final JSType type;
    private final boolean inferred;
    private final Node propertyNode;

    private PropertyContinuation(
        String propertyName,
        JSType type,
        boolean inferred,
        Node propertyNode) {
      this.propertyName = propertyName;
      this.type = type;
      this.inferred = inferred;
      this.propertyNode = propertyNode;
    }

    void commit(ObjectType target) {
      target.defineProperty(
          propertyName, type, inferred, propertyNode);
    }
  }

  @Override
  public boolean isObject() {
    if (isEnumElementType()) {
      return toMaybeEnumElementType().isObject();
    }
    return super.isObject();
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    return visitor.caseNamedType(this);
  }
}
