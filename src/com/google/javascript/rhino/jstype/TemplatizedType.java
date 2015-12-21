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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * An object type with declared template types, such as
 * {@code Array<string>}.
 *
 */
public final class TemplatizedType extends ProxyObjectType {
  private static final long serialVersionUID = 1L;

  final ImmutableList<JSType> templateTypes;
  final TemplateTypeMapReplacer replacer;

  TemplatizedType(
      JSTypeRegistry registry, ObjectType objectType,
      ImmutableList<JSType> templateTypes) {
    super(registry, objectType, objectType.getTemplateTypeMap().addValues(
        templateTypes));

    // Cache which template keys were filled, and what JSTypes they were filled
    // with.
    ImmutableList<TemplateType> filledTemplateKeys =
        objectType.getTemplateTypeMap().getUnfilledTemplateKeys();
    ImmutableList.Builder<JSType> builder = ImmutableList.builder();
    for (TemplateType filledTemplateKey : filledTemplateKeys) {
      builder.add(getTemplateTypeMap().getResolvedTemplateType(filledTemplateKey));
    }
    this.templateTypes = builder.build();

    replacer = new TemplateTypeMapReplacer(registry, getTemplateTypeMap());
  }

  @Override
  String toStringHelper(final boolean forAnnotations) {
    String typeString = super.toStringHelper(forAnnotations);

    if (!templateTypes.isEmpty()) {
      typeString += "<"
          + Joiner.on(",").join(Lists.transform(templateTypes, new Function<JSType, String>() {
            @Override
            public String apply(JSType type) {
              return type.toStringHelper(forAnnotations);
            }
          })) + ">";
    }

    return typeString;
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

  @Override
  public boolean isSubtype(JSType that) {
    return isSubtype(that, ImplCache.create());
  }

  @Override
  protected boolean isSubtype(JSType that,
      ImplCache implicitImplCache) {
    return isSubtypeHelper(this, that, implicitImplCache);
  }

  boolean wrapsSameRawType(JSType that) {
    return that.isTemplatizedType() && this.getReferencedTypeInternal()
        .isEquivalentTo(
            that.toMaybeTemplatizedType().getReferencedTypeInternal());
  }

  /**
   * Computes the greatest subtype of two related templatized types.
   * @return The greatest subtype.
   */
  JSType getGreatestSubtypeHelper(JSType rawThat) {
    Preconditions.checkNotNull(rawThat);

    if (!wrapsSameRawType(rawThat)) {
      if (!rawThat.isTemplatizedType()) {
        if (this.isSubtype(rawThat)) {
          return this;
        } else if (rawThat.isSubtype(this)) {
          return filterNoResolvedType(rawThat);
        }
      }
      if (this.isObject() && rawThat.isObject()) {
        return this.getNativeType(JSTypeNative.NO_OBJECT_TYPE);
      }
      return this.getNativeType(JSTypeNative.NO_TYPE);
    }

    TemplatizedType that = rawThat.toMaybeTemplatizedType();
    Preconditions.checkNotNull(that);

    if (getTemplateTypeMap().checkEquivalenceHelper(
        that.getTemplateTypeMap(), EquivalenceMethod.INVARIANT)) {
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
}
