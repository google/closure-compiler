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

/**
 * Represents a declaration of a javascript type.
 *
 * <p>See {@link #checkValid} for invariants on a declaration's state.
 * In most cases, only one of the instance fields can be non null.
 */
public class Declaration {
  // Type of local, formal, or extern that the declaration refers to
  private JSType simpleType;
  private Typedef typedef;
  private Namespace ns;
  private DeclaredTypeRegistry funScope;
  private boolean isTypeVar;
  private boolean isConstant;

  public Declaration(JSType simpleType, Typedef typedef, Namespace ns,
      DeclaredTypeRegistry funScope, boolean isTypeVar, boolean isConstant) {
    this.simpleType = simpleType;
    this.typedef = typedef;
    this.ns = ns;
    this.funScope = funScope;
    this.isTypeVar = isTypeVar;
    this.isConstant = isConstant;
    checkValid();
  }

  private void checkValid() {
    if (this.simpleType != null) {
      Preconditions.checkState(this.typedef == null && this.ns == null);
    }
    if (this.typedef != null) {
      Preconditions.checkState(
          this.simpleType == null && this.ns == null && this.funScope == null);
    }
    if (this.ns != null) {
      // Note: Non-null nominal with null function is allowed,
      // e.g., /** @constructor */ var Bar = Foo;
      Preconditions.checkState(this.simpleType == null && this.typedef == null);
    }
    if (this.funScope != null) {
      Preconditions.checkState(this.typedef == null);
    }
  }

  public JSType getTypeOfSimpleDecl() {
    return this.simpleType;
  }

  public Typedef getTypedef() {
    return this.typedef;
  }

  public EnumType getEnum() {
    return this.ns instanceof EnumType ? ((EnumType) this.ns) : null;
  }

  public DeclaredTypeRegistry getFunctionScope() {
    return this.funScope;
  }

  public RawNominalType getNominal() {
    return this.ns instanceof RawNominalType ? ((RawNominalType) this.ns) : null;
  }

  public boolean isTypeVar() {
    return this.isTypeVar;
  }

  public boolean isConstant() {
    return this.isConstant;
  }

  public Namespace getNamespace() {
    return this.ns;
  }

  public String toString() {
    return MoreObjects.toStringHelper(this).omitNullValues()
        .add("simpleType", this.simpleType)
        .add("typedef", this.typedef)
        .add("namespace", this.ns)
        .add("scope", this.funScope)
        .toString();
  }
}
