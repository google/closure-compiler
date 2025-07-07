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

import com.google.common.collect.ImmutableList;
import com.google.javascript.jscomp.base.Tri;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;
import org.jspecify.annotations.Nullable;

/**
 * An unresolved type that was forward declared. So we know it exists, but that it wasn't pulled
 * into this binary.
 *
 * <p>In most cases, it behaves like a sibling of the top object type.
 *
 * <p>Why have this class at all, and not just use the top object type or `*`?
 *
 * <p>Clutz relies on this class to emit .d.ts typings for JS files without having to see all their
 * dependencies: Clutz expects unresolved types to be available when it runs, so that it can map
 * them back to the corresponding goog.required import.
 *
 * <p>Clutz also sets a special JSCompiler flag so that all unresolved types are treated as forward
 * declared. (In most non-Clutz cases, this class is only rarely used, and only if there's actually
 * an explicit `goog.forwardDeclare` call.)
 *
 * <p>To address this, we would need to significantly refactor Clutz.
 *
 * @author nicksantos@google.com (Nick Santos)
 */
public final class NoResolvedType extends ObjectType {
  /** The name originally used to reference this type. */
  private final String referenceName;

  /**
   * Any template arguments to this type, or {@code null} if none. This field is not used for
   * JSCompiler's type checking; it is only needed by Clutz.
   */
  private @Nullable ImmutableList<JSType> templateTypes;

  NoResolvedType(
      JSTypeRegistry registry, String referenceName, ImmutableList<JSType> templateTypes) {
    super(registry);
    this.referenceName = checkNotNull(referenceName);
    this.templateTypes = templateTypes;
    this.eagerlyResolveToSelf();
  }

  @Override
  JSTypeClass getTypeClass() {
    return JSTypeClass.NO_RESOLVED;
  }

  @Override
  public @Nullable String getReferenceName() {
    return referenceName;
  }

  @Override
  public @Nullable ImmutableList<JSType> getTemplateTypes() {
    return templateTypes;
  }

  @Override
  public boolean isNoResolvedType() {
    return true;
  }

  @Override
  public BooleanLiteralSet getPossibleToBooleanOutcomes() {
    return BooleanLiteralSet.BOTH;
  }

  @Override
  public Tri testForEquality(JSType that) {
    return Tri.UNKNOWN;
  }

  @Override
  final JSType resolveInternal(ErrorReporter reporter) {
    throw new AssertionError();
  }

  @Override
  public @Nullable FunctionType getConstructor() {
    return null;
  }

  @Override
  public final @Nullable ObjectType getImplicitPrototype() {
    return null;
  }

  @Override
  final int recursionUnsafeHashCode() {
    // NoResolvedType instances are unique within a JSTypeRegistry.
    return System.identityHashCode(this);
  }

  @Override
  public boolean defineProperty(
      Property.Key propertyName, JSType type, boolean inferred, Node propertyNode) {
    // nothing, all properties are defined
    return true;
  }

  @Override
  void appendTo(TypeStringBuilder sb) {
    sb.append(sb.isForAnnotations() ? "?" : "NoResolvedType<" + referenceName + ">");
  }
}
