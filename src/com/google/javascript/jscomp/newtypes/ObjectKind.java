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
 * Used by NominalType and ObjectType for @struct, @dict and @unrestricted.
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
class ObjectKind {
  private static final int UNRESTRICTED_MASK = 0;
  private static final int STRUCT_MASK = 1;
  private static final int DICT_MASK = 2;
  // No matter how you access its properties, it complains.
  private static final int BOTH_MASK = 3;

  private int mask;

  static final ObjectKind UNRESTRICTED = new ObjectKind(UNRESTRICTED_MASK);
  static final ObjectKind STRUCT = new ObjectKind(STRUCT_MASK);
  static final ObjectKind DICT = new ObjectKind(DICT_MASK);
  private static final ObjectKind BOTH = new ObjectKind(BOTH_MASK);

  private static final ObjectKind[] vals = {
    UNRESTRICTED,
    STRUCT,
    DICT,
    BOTH
  };

  private ObjectKind(int mask) {
    this.mask = mask;
  }

  static ObjectKind meet(ObjectKind ok1, ObjectKind ok2) {
    return vals[ok1.mask & ok2.mask];
  }

  static ObjectKind join(ObjectKind ok1, ObjectKind ok2) {
    return vals[ok1.mask | ok2.mask];
  }

  boolean isUnrestricted() {
    return this.mask == UNRESTRICTED_MASK;
  }

  boolean isStruct() {
    return (this.mask & STRUCT_MASK) != 0;
  }

  boolean isDict() {
    return (this.mask & DICT_MASK) != 0;
  }

  boolean isSubtypeOf(ObjectKind other) {
    return this.mask == (this.mask & other.mask);
  }
}
