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

import com.google.common.collect.ImmutableSet;

/**
 * Static methods that operate on {@code TypeWithProperties} instances.
 */
final class TypeWithPropertiesStatics {

  // Never create any instances of this class
  private TypeWithPropertiesStatics() {}

  static JSType getProp(
      ImmutableSet<? extends TypeWithProperties> types, QualifiedName qname) {
    if (types == null) {
      return null;
    }
    JSType ptype = JSType.BOTTOM;
    boolean foundProp = false;
    for (TypeWithProperties t : types) {
      if (t.mayHaveProp(qname)) {
        foundProp = true;
        ptype = JSType.join(ptype, t.getProp(qname));
      }
    }
    return foundProp ? ptype : null;
  }

  static JSType getDeclaredProp(
      ImmutableSet<? extends TypeWithProperties> types, QualifiedName qname) {
    if (types == null) {
      return null;
    }
    JSType ptype = JSType.BOTTOM;
    for (TypeWithProperties t : types) {
      if (t.mayHaveProp(qname)) {
        JSType declType = t.getDeclaredProp(qname);
        if (declType != null) {
          ptype = JSType.join(ptype, declType);
        }
      }
    }
    return ptype.isBottom() ? null : ptype;
  }

  static boolean mayHaveProp(
      ImmutableSet<? extends TypeWithProperties> types, QualifiedName qname) {
    if (types == null) {
      return false;
    }
    for (TypeWithProperties t : types) {
      if (t.mayHaveProp(qname)) {
        return true;
      }
    }
    return false;
  }

  static boolean hasProp(
      ImmutableSet<? extends TypeWithProperties> types, QualifiedName qname) {
    if (types == null) {
      return false;
    }
    for (TypeWithProperties t : types) {
      if (!t.hasProp(qname)) {
        return false;
      }
    }
    return true;
  }

  static boolean hasConstantProp(
      ImmutableSet<? extends TypeWithProperties> types, QualifiedName qname) {
    if (types == null) {
      return false;
    }
    for (TypeWithProperties t : types) {
      if (t.hasConstantProp(qname)) {
        return true;
      }
    }
    return false;
  }
}
