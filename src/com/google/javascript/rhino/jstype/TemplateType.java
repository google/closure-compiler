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

/**
 * For functions with function(this: T, ...) and T as arguments, type inference
 * will set the type of this on a function literal argument to the actual type
 * of T.
 *
 */
package com.google.javascript.rhino.jstype;

import com.google.common.base.Predicate;
import com.google.javascript.rhino.Node;

/** A placeholder type, used as keys in {@link TemplateTypeMap}s. */
public final class TemplateType extends ProxyObjectType {
  private static final long serialVersionUID = 1L;

  private static final JSTypeClass TYPE_CLASS = JSTypeClass.TEMPLATE;

  private final String name;
  private JSType bound;
  private final Node typeTransformation;

  TemplateType(JSTypeRegistry registry, String name) {
    this(registry, name, null, null);
  }

  TemplateType(JSTypeRegistry registry, String name, JSType bound) {
    this(registry, name, bound, null);
  }

  TemplateType(JSTypeRegistry registry, String name, Node typeTransformation) {
    this(registry, name, null, typeTransformation);
  }

  private TemplateType(
      JSTypeRegistry registry, String name, JSType bound, Node typeTransformation) {
    super(
        registry, bound == null ? registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE) : bound);
    this.name = name;
    this.bound = bound == null ? registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE) : bound;
    this.typeTransformation = typeTransformation;

    registry.getResolver().resolveIfClosed(this, TYPE_CLASS);
  }

  @Override
  JSTypeClass getTypeClass() {
    return TYPE_CLASS;
  }

  @Override
  public String getReferenceName() {
    return name;
  }

  @Override
  void appendTo(TypeStringBuilder sb) {
    if (JSType.areIdentical(bound, registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE))) {
      // This is an unbound template, don't print it's bound
      sb.append(this.name);
    } else {
      sb.append(this.name).append(" extends ").append(bound);
    }
  }

  @Override
  public TemplateType toMaybeTemplateType() {
    return this;
  }

  @Override
  public boolean hasAnyTemplateTypesInternal() {
    return true;
  }

  @Override
  public <T> T visit(Visitor<T> visitor) {
    return visitor.caseTemplateType(this);
  }

  @Override <T> T visit(RelationshipVisitor<T> visitor, JSType that) {
    return visitor.caseTemplateType(this, that);
  }

  public boolean isTypeTransformation() {
    return typeTransformation != null;
  }

  public Node getTypeTransformation() {
    return typeTransformation;
  }

  public JSType getBound() {
    return bound;
  }

  public void setBound(JSType bound) {
    this.bound = bound;
    this.setReferencedType(bound);
  }

  @Override
  public boolean setValidator(Predicate<JSType> validator) {
    // ProxyObjectType will delegate to the unknown type's setValidator, which we don't want.
    // Some validator functions special-case template types.
    // Note that this.isUnknownType() is still true, so this override only affects validators that
    // treat TemplateTypes differently from a random type for which isUnknownType() is true.
    // (e.g. FunctionTypeBuilder#ExtendedTypeValidator)
    return validator.apply(this);
  }

  /**
   * This function returns whether or not there is a cycle in the reference chain of this type.
   */
  public boolean containsCycle() {
    // By passing in an unreachable type `null` as the target, the visitor will only return if a
    // cycle is found or a terminating type is reached.
    ContainsUpperBoundSuperTypeVisitor typeVisitor = new ContainsUpperBoundSuperTypeVisitor(null);
    return this.visit(typeVisitor) == ContainsUpperBoundSuperTypeVisitor.Result.CYCLE;
  }
}
