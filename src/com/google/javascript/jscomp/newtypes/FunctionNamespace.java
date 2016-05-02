/*
 * Copyright 2016 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;

/**
 *
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public final class FunctionNamespace extends Namespace {
  private DeclaredTypeRegistry scope;

  public FunctionNamespace(String name, DeclaredTypeRegistry scope) {
    Preconditions.checkNotNull(name);
    Preconditions.checkNotNull(scope);
    this.name = name;
    this.scope = scope;
  }

  @Override
  protected JSType computeJSType(JSTypes commonTypes) {
    Preconditions.checkState(this.namespaceType == null);
    return JSType.fromObjectType(ObjectType.makeObjectType(
        commonTypes.getFunctionType(),
        null,
        this.scope.getDeclaredFunctionType().toFunctionType(),
        this,
        false,
        ObjectKind.UNRESTRICTED));
  }

  DeclaredTypeRegistry getScope() {
    return this.scope;
  }
}
