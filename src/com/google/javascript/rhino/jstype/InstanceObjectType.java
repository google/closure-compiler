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

import com.google.javascript.rhino.Node;

/** An object type that is an instance of some function constructor. */
final class InstanceObjectType extends PrototypeObjectType {
  private static final JSTypeClass TYPE_CLASS = JSTypeClass.INSTANCE_OBJECT;

  private final FunctionType constructor;

  private InstanceObjectType(Builder builder) {
    super(builder);
    this.constructor = checkNotNull(builder.constructor);

    registry.getResolver().resolveIfClosed(this, TYPE_CLASS);
  }

  static final class Builder extends PrototypeObjectType.Builder<Builder> {
    private FunctionType constructor;

    Builder(JSTypeRegistry registry) {
      super(registry);
    }

    Builder setConstructor(FunctionType x) {
      this.constructor = x;
      return this;
    }

    @Override
    InstanceObjectType build() {
      return new InstanceObjectType(this);
    }
  }

  static Builder builderForCtor(FunctionType ctor) {
    return new Builder(ctor.registry)
        .setName(ctor.getReferenceName())
        .setImplicitPrototype(null) // This isn't shared by a function and it's instances.
        .setNative(ctor.isNativeObjectType())
        .setAnonymous(ctor.isAnonymous())
        .setTemplateTypeMap(ctor.getTemplateTypeMap())
        // Recall that in ES5 code, instance and constructor template parameters were
        // indistinguishable. That asumption is maintained here by deault, but later code may
        // may overwrite the template parameter count.
        .setTemplateParamCount(ctor.getTemplateParamCount())
        .setConstructor(ctor);
  }

  @Override
  JSTypeClass getTypeClass() {
    return TYPE_CLASS;
  }

  @Override
  public String getReferenceName() {
    return getConstructor().getReferenceName();
  }

  @Override
  public ObjectType getImplicitPrototype() {
    return getConstructor().getPrototype();
  }

  @Override
  public FunctionType getConstructor() {
    return constructor;
  }

  @Override
  boolean defineProperty(String name, JSType type, boolean inferred,
      Node propertyNode) {
    ObjectType proto = getImplicitPrototype();
    if (proto != null && proto.hasOwnDeclaredProperty(name)) {
      return false;
    }
    return super.defineProperty(name, type, inferred, propertyNode);
  }

  @Override
  void appendTo(TypeStringBuilder sb) {
    if (!constructor.hasReferenceName()) {
      super.appendTo(sb);
      return;
    } else if (sb.isForAnnotations()) {
      sb.append(constructor.getNormalizedReferenceName());
      return;
    }

    String name = constructor.getReferenceName();
    if (name.isEmpty()) {
      Node n = constructor.getSource();
      sb.append("<anonymous@")
          .append(n != null ? n.getSourceFileName() : "unknown")
          .append(":")
          .append(Integer.toString(n != null ? n.getLineno() : 0))
          .append(">");
    }
    sb.append(name);
  }

  @Override
  boolean isTheObjectType() {
    return getConstructor().isNativeObjectType()
        && "Object".equals(getReferenceName());
  }

  @Override
  public boolean isInstanceType() {
    return true;
  }

  @Override
  public boolean isArrayType() {
    return getConstructor().isNativeObjectType()
        && "Array".equals(getReferenceName());
  }

  @Override
  public boolean isBigIntObjectType() {
    return getConstructor().isNativeObjectType() && "BigInt".equals(getReferenceName());
  }

  @Override
  public boolean isStringObjectType() {
    return getConstructor().isNativeObjectType()
        && "String".equals(getReferenceName());
  }

  @Override
  public boolean isSymbolObjectType() {
    return getConstructor().isNativeObjectType() && "Symbol".equals(getReferenceName());
  }

  @Override
  public boolean isBooleanObjectType() {
    return getConstructor().isNativeObjectType()
        && "Boolean".equals(getReferenceName());
  }

  @Override
  public boolean isNumberObjectType() {
    return getConstructor().isNativeObjectType()
        && "Number".equals(getReferenceName());
  }

  @Override
  public boolean isDateType() {
    return getConstructor().isNativeObjectType()
        && "Date".equals(getReferenceName());
  }

  @Override
  public boolean isRegexpType() {
    return getConstructor().isNativeObjectType()
        && "RegExp".equals(getReferenceName());
  }

  @Override
  public boolean isNominalType() {
    return hasReferenceName();
  }

  @Override
  int recursionUnsafeHashCode() {
    if (hasReferenceName()) {
      return NamedType.nominalHashCode(this);
    } else {
      return super.hashCode();
    }
  }

  @Override
  public Iterable<ObjectType> getCtorImplementedInterfaces() {
    return getConstructor().getImplementedInterfaces();
  }

  @Override
  public Iterable<ObjectType> getCtorExtendedInterfaces() {
    return getConstructor().getExtendedInterfaces();
  }

  // The owner will always be a resolved type, so there's no need to set
  // the constructor in resolveInternal.
  // (it would lead to infinite loops if we did).
  // JSType resolveInternal(ErrorReporter reporter);
}
