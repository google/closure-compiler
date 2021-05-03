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
import static com.google.javascript.jscomp.base.JSCompObjects.identical;

import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.ErrorReporter;
import java.util.LinkedHashSet;
import java.util.Objects;

/**
 * An object type with declared template types, such as
 * {@code Array<string>}.
 *
 */
public final class TemplatizedType extends ProxyObjectType {
  private static final JSTypeClass TYPE_CLASS = JSTypeClass.TEMPLATIZED;

  /** A cache of the type parameter values for this specialization. */
  private final ImmutableList<JSType> templateTypes;
  /** Whether all type parameter values for this specialization are `?`. */
  private final boolean isSpecializedOnlyWithUnknown;

  private transient TemplateTypeReplacer replacer;

  TemplatizedType(
      JSTypeRegistry registry, ObjectType objectType,
      ImmutableList<JSType> templateTypes) {
    super(
        registry, objectType, objectType.getTemplateTypeMap().copyFilledWithValues(templateTypes));

    ImmutableList.Builder<JSType> builder = ImmutableList.builder();
    boolean maybeIsSpecializedOnlyWithUnknown = true;
    for (TemplateType newlyFilledTemplateKey : objectType.getTypeParameters()) {
      JSType resolvedType = getTemplateTypeMap().getResolvedTemplateType(newlyFilledTemplateKey);

      builder.add(resolvedType);
      if (maybeIsSpecializedOnlyWithUnknown) {
        maybeIsSpecializedOnlyWithUnknown =
            this.getNativeType(JSTypeNative.UNKNOWN_TYPE).equals(resolvedType);
      }
    }
    this.templateTypes = builder.build();
    this.isSpecializedOnlyWithUnknown = maybeIsSpecializedOnlyWithUnknown;

    this.replacer = TemplateTypeReplacer.forPartialReplacement(registry, getTemplateTypeMap());

    registry.getResolver().resolveIfClosed(this, TYPE_CLASS);
  }

  @Override
  JSTypeClass getTypeClass() {
    return TYPE_CLASS;
  }

  // NOTE(dimvar): If getCtorImplementedInterfaces is implemented here, this is the
  // correct implementation. The one inherited from ProxyObjectType is not correct
  // because it doesn't instantiate the generic types. However, our unit tests don't
  // actually ever call this method, so it could alternatively just throw
  // an UnsupportedOperationException.
  @Override
  public Iterable<ObjectType> getCtorImplementedInterfaces() {
    LinkedHashSet<ObjectType> resolvedImplementedInterfaces = new LinkedHashSet<>();
    for (ObjectType obj : getReferencedObjTypeInternal().getCtorImplementedInterfaces()) {
      resolvedImplementedInterfaces.add(obj.visit(replacer).toObjectType());
    }
    return resolvedImplementedInterfaces;
  }

  @Override
  public Iterable<ObjectType> getCtorExtendedInterfaces() {
    LinkedHashSet<ObjectType> resolvedExtendedInterfaces = new LinkedHashSet<>();
    for (ObjectType obj : getReferencedObjTypeInternal().getCtorExtendedInterfaces()) {
      resolvedExtendedInterfaces.add(obj.visit(replacer).toObjectType());
    }
    return resolvedExtendedInterfaces;
  }

  @Override
  void appendTo(TypeStringBuilder sb) {
    super.appendTo(sb);
    if (!this.templateTypes.isEmpty()) {
      sb.append("<").appendAll(this.templateTypes, ",").append(">");
    }
  }

  @Override
  int recursionUnsafeHashCode() {
    int baseHash = super.recursionUnsafeHashCode();

    // TODO(b/110224889): This case can probably be removed if `equals()` is updated.
    if (this.isSpecializedOnlyWithUnknown) {
      return baseHash;
    }
    return Objects.hash(templateTypes, baseHash);
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    return visitor.caseTemplatizedType(this);
  }

  @Override <T> T visit(RelationshipVisitor<T> visitor, JSType that) {
    return visitor.caseTemplatizedType(this, that);
  }

  @Override
  public TemplatizedType toMaybeTemplatizedType() {
    return this;
  }

  @Override
  public ImmutableList<JSType> getTemplateTypes() {
    return templateTypes;
  }

  @Override
  public JSType getPropertyType(String propertyName) {
    JSType result = super.getPropertyType(propertyName);
    return result == null ? null : result.visit(replacer);
  }

  boolean wrapsSameRawType(JSType that) {
    return that.isTemplatizedType() && this.getReferencedTypeInternal()
        .equals(
            that.toMaybeTemplatizedType().getReferencedTypeInternal());
  }

  /**
   * Computes the greatest subtype of two related templatized types.
   * @return The greatest subtype.
   */
  JSType getGreatestSubtypeHelper(JSType rawThat) {
    checkNotNull(rawThat);

    if (!wrapsSameRawType(rawThat)) {
      if (!rawThat.isTemplatizedType()) {
        if (this.isSubtype(rawThat)) {
          return this;
        } else if (rawThat.isSubtypeOf(this)) {
          return filterNoResolvedType(rawThat);
        }
      }
      if (this.isObject() && rawThat.isObject()) {
        return this.getNativeType(JSTypeNative.NO_OBJECT_TYPE);
      }
      return this.getNativeType(JSTypeNative.NO_TYPE);
    }

    TemplatizedType that = rawThat.toMaybeTemplatizedType();
    checkNotNull(that);

    if (this.equals(that)) {
      return this;
    }

    // For types that have the same raw type but different type parameters,
    // we simply create a type has a "unknown" type parameter.  This is
    // equivalent to the raw type.
    return getReferencedObjTypeInternal();
  }

  @Override
  public TemplateTypeMap getTemplateTypeMap() {
    return templateTypeMap;
  }

  @Override
  public boolean hasAnyTemplateTypesInternal() {
    return templateTypeMap.hasAnyTemplateTypesInternal();
  }

  /**
   * @return The referenced ObjectType wrapped by this TemplatizedType.
   */
  public ObjectType getReferencedType() {
    return getReferencedObjTypeInternal();
  }

  @Override
  JSType resolveInternal(ErrorReporter reporter) {
    JSType baseTypeBefore = getReferencedType();
    super.resolveInternal(reporter);

    boolean rebuild = baseTypeBefore != getReferencedType();
    ImmutableList.Builder<JSType> builder = ImmutableList.builder();
    for (JSType type : templateTypes) {
      JSType resolved = type.resolve(reporter);
      rebuild |= !identical(resolved, type);
      builder.add(resolved);
    }

    if (rebuild) {
      return new TemplatizedType(registry, getReferencedType(), builder.build());
    } else {
      return this;
    }
  }
}
