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
package com.google.javascript.jscomp.testing;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Fact.fact;
import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.google.javascript.rhino.testing.TypeSubject.assertType;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.javascript.jscomp.AbstractScope;
import com.google.javascript.jscomp.AbstractVar;
import com.google.javascript.jscomp.TypedVar;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.testing.TypeSubject;
import org.jspecify.nullness.Nullable;

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
public final class ScopeSubject extends Subject {
  @CheckReturnValue
  public static ScopeSubject assertScope(AbstractScope<?, ?> scope) {
    // NB: Eclipse's Java compiler bails on just passing ScopeSubject::new below, so wrap it in a
    // Closure.
    return assertAbout(ScopeSubject::new).that(scope);
  }

  private final AbstractScope<?, ?> actual;

  private ScopeSubject(FailureMetadata failureMetadata, AbstractScope<?, ?> scope) {
    super(failureMetadata, scope);
    this.actual = scope;
  }

  public void doesNotDeclare(String name) {
    AbstractVar<?, ?> var = getVar(name);
    if (var != null) {
      failWithoutActual(
          fact("expected not to declare", name),
          fact("but declared it with value", var),
          fact("scope was", actual));
    }
  }

  public DeclarationSubject declares(String name) {
    AbstractVar<?, ?> var = getVar(name);
    if (var == null) {
      ImmutableList<AbstractVar<?, ?>> declared =
          ImmutableList.copyOf(actual.getAllAccessibleVariables());
      ImmutableList<String> names =
          declared.stream().map(AbstractVar::getName).collect(toImmutableList());
      if (names.size() > 10) {
        names =
            ImmutableList.<String>builder()
                .addAll(names.subList(0, 9))
                .add("and " + (names.size() - 9) + " others")
                .build();
      }
      failWithoutActual(
          fact("expected to declare", name),
          simpleFact("but did not"),
          fact("did declare", Joiner.on(", ").join(names)),
          fact("scope was", actual));
    }
    assertThat(actual.getTopmostScopeOfEventualDeclaration(name)).isEqualTo(var.getScope());
    return new DeclarationSubject(var);
  }

  private AbstractVar<?, ?> getVar(String name) {
    return actual.hasSlot(name) ? checkNotNull(actual.getVar(name)) : null;
  }

  /** A subject for an {@link AbstractVar} declared by this particular scope. */
  public final class DeclarationSubject {
    private final AbstractVar<?, ?> var;

    private DeclarationSubject(AbstractVar<?, ?> var) {
      this.var = checkNotNull(var);
    }

    /**
     * Expects the variable to be defined on the given {@code scope}. The {@code preposition} is
     * either "on" or "", depending on whether it is needed for grammatical correctness. The {@code
     * expected} object is displayed in brackets, in parallel to the actual scope that the variable
     * is defined on.
     */
    private void expectScope(String preposition, Object expected, AbstractScope<?, ?> scope) {
      if (var.getScope() != scope) {
        failWithoutActual(
            fact("for var", var.getName()),
            fact(
                "expected to be declared" + (!preposition.isEmpty() ? " " : "") + preposition,
                expected),
            fact("but is declared on", var.getScope()));
      }
    }

    /** Expects the declared variable to be declared on the subject scope. */
    @CanIgnoreReturnValue
    public DeclarationSubject directly() {
      expectScope("", "directly", actual);
      return this;
    }

    /** Expects the declared variable to be declared on the given scope. */
    @CanIgnoreReturnValue
    public DeclarationSubject on(AbstractScope<?, ?> scope) {
      checkState(
          scope != actual,
          "It doesn't make sense to pass the scope already being asserted about. Use .directly()");
      expectScope("on", scope, scope);
      return this;
    }

    /** Expects the declared variable to be declared on the closest container scope. */
    @CanIgnoreReturnValue
    public DeclarationSubject onClosestContainerScope() {
      expectScope("on", "the closest container scope", actual.getClosestContainerScope());
      return this;
    }

    /** Expects the declared variable to be declared on the closest hoist scope. */
    @CanIgnoreReturnValue
    public DeclarationSubject onClosestHoistScope() {
      expectScope("on", "the closest hoist scope", actual.getClosestHoistScope());
      return this;
    }

    /** Expects the declared variable to be declared on the global scope. */
    @CanIgnoreReturnValue
    public DeclarationSubject globally() {
      expectScope("", "globally", actual.getGlobalScope());
      return this;
    }

    /** Expects the declared variable to be declared on any scope other than the subject. */
    @CanIgnoreReturnValue
    public DeclarationSubject onSomeParent() {
      if (var != null && var.getScope() == actual) {
        failWithoutActual(
            fact("for var", var.getName()),
            simpleFact("expected a declaration on a parent scope"),
            simpleFact("but found it declared directly"),
            fact("scope was", actual));
      }
      return this;
    }

    /** Expects the declared variable to be declared on some scope with the given label. */
    @CanIgnoreReturnValue
    public DeclarationSubject onScopeLabeled(String expectedLabel) {
      checkNotNull(expectedLabel);
      String actualLabel = getLabel(var.getScopeRoot());
      if (actualLabel == null) {
        failWithoutActual(
            fact("expected to declare", var.getName()),
            fact("on a scope labeled", expectedLabel),
            simpleFact("but declared it on an unlabeled scope"),
            fact("scope under test was", actual));
      } else if (!actualLabel.equals(expectedLabel)) {
        failWithoutActual(
            fact("expected to declare", var.getName()),
            fact("on a scope labeled", expectedLabel),
            fact("but declared it on an scope labeled", actualLabel),
            fact("scope under test was", actual));
      }
      return this;
    }

    public TypeSubject withTypeThat() {
      TypedVar typedVar = (TypedVar) var.getSymbol();
      return assertType(typedVar.getType());
    }
  }

  /** Returns the name of the label applied to n, or null if none exists. */
  private @Nullable String getLabel(Node n) {
    // If the node is labeled it will be the second child of a LABEL and the first child
    // will be a LABEL_NAME.
    Node parent = n.getParent();
    if (parent != null && parent.isLabel()) {
      Node labelNameNode = parent.getFirstChild();
      checkState(labelNameNode.isLabelName(), labelNameNode);
      checkState(labelNameNode.getNext() == n, n);
      return labelNameNode.getString();
    } else {
      return null;
    }
  }
}
