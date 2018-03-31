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
package com.google.javascript.jscomp;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertAbout;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import javax.annotation.CheckReturnValue;

/**
 * A Truth Subject for the AbstractScope class. Usage:
 *
 * <pre>
 *   import static com.google.javascript.jscomp.testing.ScopeSubject.assertScope;
 *   ...
 *   assertScope(scope).declares("somevar");
 *   assertScope(scope).declares("otherVar").directly();
 *   assertScope(scope).declares("yetAnotherVar").onClosestContainerScope();
 * </pre>
 */
public final class ScopeSubject extends Subject<ScopeSubject, AbstractScope<?, ?>> {
  @CheckReturnValue
  public static ScopeSubject assertScope(AbstractScope<?, ?> scope) {
    return assertAbout(ScopeSubject::new).that(scope);
  }

  private ScopeSubject(FailureMetadata failureMetadata, AbstractScope<?, ?> scope) {
    super(failureMetadata, scope);
  }

  public void doesNotDeclare(String name) {
    AbstractVar<?, ?> var = getVar(name);
    if (var != null) {
      failWithBadResults("does not declare", name, "declared", var);
    }
  }

  public DeclarationSubject declares(String name) {
    AbstractVar<?, ?> var = getVar(name);
    if (var == null) {
      ImmutableList<AbstractVar<?, ?>> declared =
          ImmutableList.copyOf(getSubject().getAllAccessibleVariables());
      ImmutableList<String> names =
          declared.stream().map(AbstractVar::getName).collect(toImmutableList());
      if (names.size() > 10) {
        names =
            ImmutableList.<String>builder()
                .addAll(names.subList(0, 9))
                .add("and " + (names.size() - 9) + " others")
                .build();
      }
      failWithBadResults("declares", name, "declares", Joiner.on(", ").join(names));
    }
    return new DeclarationSubject(var);
  }

  private AbstractVar<?, ?> getVar(String name) {
    return getSubject().hasSlot(name) ? checkNotNull(getSubject().getVar(name)) : null;
  }

  public final class DeclarationSubject {
    private final AbstractVar<?, ?> var;

    private DeclarationSubject(AbstractVar<?, ?> var) {
      this.var = var;
    }

    /**
     * Expects the variable to be defined on the given {@code scope}.  The {@code preposition}
     * is either "on" or "", depending on whether it is needed for grammatical correctness.
     * The {@code expected} object is displayed in brackets, in parallel to the actual scope
     * that the variable is defined on.
     */
    private void expectScope(String preposition, Object expected, AbstractScope<?, ?> scope) {
      if (var != null && var.getScope() != scope) {
        failWithBadResults(
            "declares " + var.getName() + (!preposition.isEmpty() ? " " : "") + preposition,
            expected,
            "declares it on",
            var.getScope());
      }
    }

    /** Expects the declared variable to be declared on the subject scope. */
    public void directly() {
      expectScope("", "directly", getSubject());
    }

    /** Expects the declared variable to be declared on the given scope. */
    public void on(AbstractScope<?, ?> scope) {
      checkState(
          scope != getSubject(),
          "It doesn't make sense to pass the scope already being asserted about. Use .directly()");
      expectScope("on", scope, scope);
    }

    /** Expects the declared variable to be declared on the closest container scope. */
    public void onClosestContainerScope() {
      expectScope("on", "the closest container scope", getSubject().getClosestContainerScope());
    }

    /** Expects the declared variable to be declared on the closest hoist scope. */
    public void onClosestHoistScope() {
      expectScope("on", "the closest hoist scope", getSubject().getClosestHoistScope());
    }

    /** Expects the declared variable to be declared on the global scope. */
    public void globally() {
      expectScope("", "globally", getSubject().getGlobalScope());
    }

    /** Expects the declared variable to be declared on any scope other than the subject. */
    public void onSomeParent() {
      if (var != null && var.getScope() == getSubject()) {
        failWithBadResults(
            "declares " + var.getName(), "on a parent scope", "declares it", "directly");
      }
    }
  }
}
