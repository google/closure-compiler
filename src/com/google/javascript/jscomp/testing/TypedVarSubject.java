/*
 * Copyright 2019 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.testing;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.javascript.jscomp.TypedVar;
import com.google.javascript.rhino.testing.TypeSubject;

/**
 * A Truth Subject for the {@link TypedVar} class. Usage:
 *
 * <pre>
 *   import static com.google.javascript.jscomp.testing.TypedVarSubject.assertThat;
 *   ...
 *   assertThat(var).isInferred();
 *   assertThat(var).hasJSTypeThat().isString();
 * </pre>
 */
@CheckReturnValue
public final class TypedVarSubject extends Subject {
  public static TypedVarSubject assertThat(TypedVar var) {
    // NB: Eclipse's Java compiler bails on just passing TypedVarSubject::new below, so wrap it in a
    // Closure.
    return assertAbout(TypedVarSubject::new).that(var);
  }

  private final TypedVar actual;

  private TypedVarSubject(FailureMetadata failureMetadata, TypedVar var) {
    super(failureMetadata, var);
    this.actual = var;
  }

  private TypedVar actualNonNull() {
    isNotNull();
    return actual;
  }

  public TypeSubject hasJSTypeThat() {
    return check("getJSType()").about(TypeSubject.types()).that(actualNonNull().getType());
  }

  public void isInferred() {
    check("isTypeInferred()").that(actualNonNull().isTypeInferred()).isTrue();
  }

  public void isNotInferred() {
    check("isTypeInferred()").that(actualNonNull().isTypeInferred()).isFalse();
  }
}
