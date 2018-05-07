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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.javascript.jscomp.ClosureCodingConvention.AssertInstanceofSpec;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.NominalTypeBuilder;
import com.google.javascript.rhino.jstype.FunctionType;

/**
 * Coding convention used by the Chrome team to compile Chrome's JS.
 */
@Immutable
public final class ChromeCodingConvention extends CodingConventions.Proxy {
  private final ImmutableSet<String> indirectlyDeclaredProperties;

  public ChromeCodingConvention() {
    this(CodingConventions.getDefault());
  }

  public ChromeCodingConvention(CodingConvention wrapped) {
    super(wrapped);
    indirectlyDeclaredProperties = new ImmutableSet.Builder<String>()
        .add("instance_", "getInstance")
        .addAll(wrapped.getIndirectlyDeclaredProperties())
        .build();
  }

  @Override
  public String getSingletonGetterClassName(Node callNode) {
    Node callArg = callNode.getFirstChild();
    if (!callArg.matchesQualifiedName("cr.addSingletonGetter") || !callNode.hasTwoChildren()) {
      return super.getSingletonGetterClassName(callNode);
    }
    return callArg.getNext().getQualifiedName();
  }

  @Override
  public void applySingletonGetter(NominalTypeBuilder classType, FunctionType getterType) {
    Node defSite = classType.constructor().getSource();
    classType.declareConstructorProperty("getInstance", getterType, defSite);
    classType.declareConstructorProperty("instance_", classType.instance(), defSite);
  }

  @Override
  public ImmutableCollection<String> getIndirectlyDeclaredProperties() {
    return indirectlyDeclaredProperties;
  }

  @Override
  public ImmutableCollection<AssertionFunctionSpec> getAssertionFunctions() {
    return ImmutableList.of(
      new AssertionFunctionSpec("assert"),
      new AssertInstanceofSpec("cr.ui.decorate")
    );
  }

  @Override
  public boolean isFunctionCallThatAlwaysThrows(Node n) {
    return CodingConventions.defaultIsFunctionCallThatAlwaysThrows(
        n, "assertNotReached");
  }
}
