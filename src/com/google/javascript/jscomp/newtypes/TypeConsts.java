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
 * Defines constant types in the new type inference.
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public final class TypeConsts {

  public static final JSType BOOLEAN = UnionType.BOOLEAN;
  public static final JSType BOTTOM = UnionType.BOTTOM;
  public static final JSType FALSE_TYPE = UnionType.FALSE_TYPE;
  public static final JSType FALSY = UnionType.FALSY;
  public static final JSType NULL = UnionType.NULL;
  public static final JSType NUMBER = UnionType.NUMBER;
  public static final JSType STRING = UnionType.STRING;
  public static final JSType TOP = UnionType.TOP;
  public static final JSType TOP_SCALAR = UnionType.TOP_SCALAR;
  public static final JSType TRUE_TYPE = UnionType.TRUE_TYPE;
  public static final JSType TRUTHY = UnionType.TRUTHY;
  public static final JSType UNDEFINED = UnionType.UNDEFINED;
  public static final JSType UNKNOWN = UnionType.UNKNOWN;

  public static final JSType TOP_OBJECT = UnionType.fromObjectType(ObjectType.TOP_OBJECT);
  private static JSType TOP_FUNCTION = null;

  // Some commonly used types
  public static final JSType NULL_OR_UNDEF = JSType.join(NULL, UNDEFINED);
  public static final JSType NUM_OR_STR = JSType.join(NUMBER, STRING);

  public static JSType topFunction() {
    if (TOP_FUNCTION == null) {
      TOP_FUNCTION = UnionType.fromFunctionType(FunctionType.TOP_FUNCTION);
    }
    return TOP_FUNCTION;
  }
}
