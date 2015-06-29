/*
 * Copyright 2015 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.newtypes;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.newtypes.NominalType.RawNominalType;

/**
 * Represents a declaration of a javascript type.
 *
 * <p>See {@link #checkValid} for invariants on a declaration's state.
 * In most cases, only one of the instance fields can be non null.
 */
public class Declaration {
  private JSType simpleType; // Type of local, formal, or extern that the declaration refers to
  private Typedef typedef;
  private NamespaceLit namespaceLit;
  private EnumType enumType;
  private DeclaredTypeRegistry functionScope;
  private RawNominalType nominal;
  private boolean isTypeVar;
  private boolean isConstant;

  public Declaration(JSType simpleType,
      Typedef typedef, NamespaceLit namespaceLit, EnumType enumType,
      DeclaredTypeRegistry functionScope, RawNominalType nominal,
      boolean isTypeVar, boolean isConstant, boolean isForwardDeclaration) {
    this.simpleType = simpleType;
    this.typedef = typedef;
    this.namespaceLit = namespaceLit;
    this.enumType = enumType;
    this.functionScope = functionScope;
    this.nominal = nominal;
    this.isTypeVar = isTypeVar;
    this.isConstant = isConstant;
    this.checkValid();
  }

  private void checkValid() {
    if (simpleType != null) {
      Preconditions.checkState(typedef == null && namespaceLit == null
          && enumType == null && nominal == null);
    }
    if (typedef != null) {
      Preconditions.checkState(simpleType == null && namespaceLit == null
          && enumType == null && functionScope == null && nominal == null);
    }
    if (namespaceLit != null) {
      Preconditions.checkState(simpleType == null && typedef == null
          && enumType == null && nominal == null);
    }
    if (enumType != null) {
      Preconditions.checkState(simpleType == null && typedef == null
          && namespaceLit == null && functionScope == null && nominal == null);
    }
    if (functionScope != null) {
      Preconditions.checkState(typedef == null && enumType == null);
    }
    if (nominal != null) {
      // Note: Non-null nominal with null function is allowed. e.g. /** @ctor */ var Bar = Foo;
      Preconditions.checkState(simpleType == null
          && typedef == null && namespaceLit == null && enumType == null);
    }
  }

  public JSType getTypeOfSimpleDecl() {
    return simpleType;
  }

  public Typedef getTypedef() {
     return typedef;
  }

  public EnumType getEnum() {
     return enumType;
  }

  public DeclaredTypeRegistry getFunctionScope() {
     return functionScope;
  }

  public RawNominalType getNominal() {
     return nominal;
  }

  public boolean isTypeVar() {
     return isTypeVar;
  }

  public boolean isConstant() {
     return isConstant;
  }

  public Namespace getNamespace() {
    if (namespaceLit != null) { return namespaceLit; }
    if (enumType != null) { return enumType; }
    if (nominal != null) { return nominal; }
    return null;
  }

  public String toString() {
    return MoreObjects.toStringHelper(this).omitNullValues()
        .add("simpleType", simpleType)
        .add("typedef", typedef)
        .add("namespace", namespaceLit)
        .add("enum", enumType)
        .add("scope", functionScope)
        .add("nominal", nominal)
        .toString();
  }
}
