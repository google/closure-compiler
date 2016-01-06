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

/**
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public final class NamespaceLit extends Namespace {
  public NamespaceLit(String name) {
    this.name = name;
  }

  // For function namespaces and when window is used as a namespace
  public JSType toJSTypeIncludingObject(JSTypes commonTypes, JSType obj) {
    if (obj == null) {
      return toJSType(commonTypes);
    }
    if (this.namespaceType == null) {
      ObjectType ot = obj.getObjTypeIfSingletonObj();
      if (ot == null) {
        ot = ObjectType.TOP_OBJECT;
      }
      this.namespaceType = computeJSType(commonTypes, ot.getNominalType(), ot.getFunType());
    }
    return this.namespaceType;
  }

  private JSType computeJSType(JSTypes commonTypes, NominalType nt, FunctionType ft) {
    ObjectType obj = ObjectType.makeObjectType(
        nt, otherProps, ft, false, ObjectKind.UNRESTRICTED);
    return withNamedTypes(commonTypes, obj);
  }

  @Override
  protected JSType computeJSType(JSTypes commonTypes) {
    return computeJSType(commonTypes, null, null);
  }

  public String toString() {
    return this.name;
  }
}
