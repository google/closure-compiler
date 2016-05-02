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

package com.google.javascript.jscomp.newtypes;

import com.google.common.base.Preconditions;

/**
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public final class NamespaceLit extends Namespace {
  // For when window is used as a namespace
  private NominalType window = null;

  public NamespaceLit(String name) {
    this.name = name;
  }

  NominalType getWindowType() {
    return this.window;
  }

  public void maybeSetWindowInstance(JSType obj) {
    if (obj != null) {
      this.window = obj.getNominalTypeIfSingletonObj();
    }
  }

  @Override
  protected JSType computeJSType(JSTypes commonTypes) {
    Preconditions.checkState(this.namespaceType == null);
    return JSType.fromObjectType(ObjectType.makeObjectType(
        this.window, null, null, this, false, ObjectKind.UNRESTRICTED));
  }
}
