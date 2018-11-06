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
import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;
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

  /**
   * Validates the type resolution.
   */
  private transient Predicate<JSType> validator;

  /**
   * Property-defining continuations.
   */
  private List<PropertyContinuation> propertyContinuations = null;

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
    checkNotNull(reference);
    this.resolutionScope = scope;
    this.reference = reference;
    this.sourceName = sourceName;
    this.lineno = lineno;
    this.charno = charno;
    this.templateTypes = templateTypes;
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

    // TODO(user): Investigate whether it is really necessary to keep two
    // different mechanisms for resolving named types, and if so, which order
    // makes more sense. Now, resolution via registry is first in order to
    // avoid triggering the warnings built into the resolution via properties.
    boolean resolved = resolveViaRegistry(reporter);
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
      int numKeys = result.getTemplateTypeMap().numUnfilledTemplateKeys();
      if (result.isObjectType()
          && (templateTypes != null && !templateTypes.isEmpty())
          && numKeys > 0) {
        ImmutableList<JSType> typeArgs = this.templateTypes;

        // Ignore any extraneous type args
        // TODO(johnlenz): report an error
        if (numKeys < this.templateTypes.size()) {
          typeArgs = typeArgs.subList(0, numKeys);
        }

        result = registry.createTemplatizedType(result.toMaybeObjectType(), typeArgs);
        setReferencedType(result);
      }

      resolutionScope = null;
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
    String[] componentNames = reference.split("\\.", -1);
    if (componentNames[0].length() == 0) {
      handleUnresolvedType(reporter, /* ignoreForwardReferencedTypes= */ true);
      return;
    }

    StaticTypedSlot slot = resolutionScope.getSlot(componentNames[0]);
    if (slot == null) {
      handleUnresolvedType(reporter, /* ignoreForwardReferencedTypes= */ true);
      return;
    }

    // If the first component has a type of 'Unknown', then any type
    // names using it should be regarded as silently 'Unknown' rather than be
    // noisy about it.
    JSType slotType = slot.getType();
    if (slotType == null || slotType.isAllType() || slotType.isNoType()) {
      handleUnresolvedType(reporter, /* ignoreForwardReferencedTypes= */ true);
      return;
    }

    // resolving component by component
    for (int i = 1; i < componentNames.length; i++) {
      ObjectType parentObj = ObjectType.cast(slotType);
      if (parentObj == null || componentNames[i].length() == 0) {
        handleUnresolvedType(reporter, /* ignoreForwardReferencedTypes= */ true);
        return;
      }
      if (i == componentNames.length - 1) {
        // Look for a typedefTypeProp on the definition node of the last component.
        Node def = parentObj.getPropertyDefSite(componentNames[i]);
        JSType typedefType = def != null ? def.getTypedefTypeProp() : null;
        if (typedefType != null) {
          setReferencedAndResolvedType(typedefType, reporter);
          return;
        }
      }
      slotType = parentObj.getPropertyType(componentNames[i]);
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

  private void setReferencedAndResolvedType(
      JSType type, ErrorReporter reporter) {
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

  // Warns about this type being unresolved iff it's not a forward-declared
  // type name.
  private void handleUnresolvedType(
      ErrorReporter reporter, boolean ignoreForwardReferencedTypes) {
    boolean isForwardDeclared =
        ignoreForwardReferencedTypes && registry.isForwardDeclaredType(reference);
    if (!isForwardDeclared) {
      String msg = "Bad type annotation. Unknown type " + reference;
      warning(reporter, msg);
    } else {
      setReferencedType(new NoResolvedType(registry, getReferenceName(), getTemplateTypes()));
      if (validator != null) {
        validator.apply(getReferencedType());
      }
    }

    setResolvedTypeInternal(getReferencedType());
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
