/*
 * Copyright 2014 The Closure Compiler Authors.
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

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.PrototypeObjectType;
import com.google.javascript.rhino.jstype.StaticSourceFile;

import javax.annotation.Nullable;

/**
 * Helper functions for computing the visibility of names and properties
 * in JavaScript source code.
 *
 * @author brndn@google.com (Brendan Linn)
 * @see {CheckAccessControls}
 */
public final class AccessControlUtils {

  /** Non-instantiable. */
  private AccessControlUtils() {}

  /**
   * Returns the effective visibility of the given name. This can differ
   * from the name's declared visibility if the file's {@code @fileoverview}
   * JsDoc specifies a default visibility.
   *
   * @param name The name node to compute effective visibility for.
   * @param var The name to compute effective visibility for.
   * @param fileVisibilityMap A map of {@code @fileoverview} visibility
   *     annotations, used to compute the name's default visibility.
   */
  static Visibility getEffectiveNameVisibility(Node name, Var var,
      ImmutableMap<StaticSourceFile, Visibility> fileVisibilityMap) {
    JSDocInfo jsDocInfo = var.getJSDocInfo();
    Visibility raw = (jsDocInfo == null || jsDocInfo.getVisibility() == null)
        ? Visibility.INHERITED
        : jsDocInfo.getVisibility();
    if (raw != Visibility.INHERITED) {
      return raw;
    }
    Visibility defaultVisibilityForFile =
        fileVisibilityMap.get(var.getSourceFile());
    JSType type = name.getJSType();
    boolean createdFromGoogProvide = (type instanceof PrototypeObjectType
        && ((PrototypeObjectType) type).isAnonymous());
    // Ignore @fileoverview visibility when computing the effective visibility
    // for names created by goog.provide.
    //
    // ProcessClosurePrimitives rewrites goog.provide()s as object literal
    // declarations, but the exact form depends on the ordering of the
    // input files. If goog.provide('a.b') occurs in the inputs before
    // goog.provide('a'), it is rewritten like
    //
    // var a={};a.b={};
    //
    // If the file containing goog.provide('a.b') also declares a @fileoverview
    // visibility, it must not apply to a, as this would make every a.* namespace
    // effectively package-private.
    return (createdFromGoogProvide || defaultVisibilityForFile == null)
        ? raw
        : defaultVisibilityForFile;
  }

  /**
   * Returns the effective visibility of the given property. This can differ
   * from the property's declared visibility if the property is inherited from
   * a superclass, or if the file's {@code @fileoverview} JsDoc specifies
   * a default visibility.
   *
   * @param property The property to compute effective visibility for.
   * @param referenceType The JavaScript type of the property.
   * @param fileVisibilityMap A map of {@code @fileoverview} visibility
   *     annotations, used to compute the property's default visibility.
   * @param codingConvention The coding convention in effect (if any),
   *     used to determine whether the property is private by lexical convention
   *     (example: trailing underscore).
   */
  static Visibility getEffectivePropertyVisibility(
      Node property,
      ObjectType referenceType,
      ImmutableMap<StaticSourceFile, Visibility> fileVisibilityMap,
      @Nullable CodingConvention codingConvention) {
    String propertyName = property.getLastChild().getString();
    StaticSourceFile definingSource = getDefiningSource(
        property, referenceType, propertyName);
    Visibility fileOverviewVisibility = fileVisibilityMap.get(definingSource);
    Node parent = property.getParent();
    boolean isOverride = parent.getJSDocInfo() != null
        && parent.isAssign()
        && parent.getFirstChild() == property;
    ObjectType objectType = getObjectType(
        referenceType, isOverride, propertyName);
    if (isOverride) {
      Visibility overridden = getOverriddenPropertyVisibility(
          objectType, propertyName);
      return getEffectiveVisibilityForOverriddenProperty(
          overridden, fileOverviewVisibility, propertyName, codingConvention);
    } else {
      return getEffectiveVisibilityForNonOverriddenProperty(
          property, objectType, fileOverviewVisibility, codingConvention);
    }
  }

  /**
   * Returns the source file in which the given property is defined,
   * or null if it is not known.
   */
  @Nullable private static StaticSourceFile getDefiningSource(
      Node getprop, @Nullable ObjectType referenceType, String propertyName) {
    if (referenceType != null) {
      Node propDefNode = referenceType.getPropertyNode(propertyName);
      if (propDefNode != null) {
        return propDefNode.getStaticSourceFile();
      }
    }
    return getprop.getStaticSourceFile();
  }

  /**
   * Returns the lowest property defined on a class with visibility information.
   */
  @Nullable private static ObjectType getObjectType(
      @Nullable ObjectType referenceType,
      boolean isOverride,
      String propertyName) {
    if (referenceType == null) {
      return null;
    }

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

  /**
   * Returns the original visibility of an overridden property.
   */
  private static Visibility getOverriddenPropertyVisibility(
      ObjectType objectType, String propertyName) {
    return objectType != null
        ? objectType.getOwnPropertyJSDocInfo(propertyName).getVisibility()
        : Visibility.INHERITED;
  }

  /**
   * Returns the effective visibility of the given overridden property.
   * An overridden propertiy inherits the visibility of the property it
   * overrides.
   */
  private static Visibility getEffectiveVisibilityForOverriddenProperty(
      Visibility visibility,
      @Nullable Visibility fileOverviewVisibility,
      String propertyName,
      @Nullable CodingConvention codingConvention) {
    if (codingConvention != null && codingConvention.isPrivate(propertyName)) {
      return Visibility.PRIVATE;
    }
    return (fileOverviewVisibility != null
        && visibility == Visibility.INHERITED)
        ? fileOverviewVisibility
        : visibility;
  }

  /**
   * Returns the effective visibility of the given non-overridden property.
   * Non-overridden properties without an explicit visibility annotation
   * receive the default visibility declared in the file's {@code @fileoverview}
   * block, if one exists.
   */
  private static Visibility getEffectiveVisibilityForNonOverriddenProperty(
      Node getprop,
      ObjectType objectType,
      @Nullable Visibility fileOverviewVisibility,
      @Nullable CodingConvention codingConvention) {
    String propertyName = getprop.getLastChild().getString();
    if (codingConvention != null && codingConvention.isPrivate(propertyName)) {
      return Visibility.PRIVATE;
    }
    Visibility raw = Visibility.INHERITED;
    if (objectType != null) {
      raw = objectType.getOwnPropertyJSDocInfo(propertyName).getVisibility();
    }
    JSType type = getprop.getJSType();
    boolean createdFromGoogProvide = (type instanceof PrototypeObjectType
        && ((PrototypeObjectType) type).isAnonymous());
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
    return (raw != Visibility.INHERITED
        || fileOverviewVisibility == null
        || createdFromGoogProvide)
        ? raw
        : fileOverviewVisibility;
  }
}

