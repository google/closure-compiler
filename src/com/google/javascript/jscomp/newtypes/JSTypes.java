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
import com.google.common.collect.Iterables;

/**
 * This class contains commonly used types, accessible from the jscomp package.
 * Also, any JSType utility methods that do not need to be in JSType.
 *
 * There should only be one instance of this class per Compiler object.
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public class JSTypes {
  // Instances of Boolean, Number and String; used for auto-boxing scalars.
  // Set at the end of GlobalTypeInfo.
  private JSType NUMBER_INSTANCE;
  private JSType BOOLEAN_INSTANCE;
  private JSType STRING_INSTANCE;

  private ObjectType NUMBER_INSTANCE_OBJTYPE;
  private ObjectType BOOLEAN_INSTANCE_OBJTYPE;
  private ObjectType STRING_INSTANCE_OBJTYPE;

  private JSType NUMBER_OR_number;
  private JSType STRING_OR_string;
  private JSType anyNumOrStr;

  private JSTypes() {}

  public static JSTypes make() {
    return new JSTypes();
  }

  JSType getNumberInstance() {
    return NUMBER_INSTANCE;
  }

  JSType getBooleanInstance() {
    return BOOLEAN_INSTANCE;
  }

  JSType getStringInstance() {
    return STRING_INSTANCE;
  }

  ObjectType getNumberInstanceObjType() {
    return NUMBER_INSTANCE_OBJTYPE;
  }

  ObjectType getBooleanInstanceObjType() {
    return BOOLEAN_INSTANCE_OBJTYPE;
  }

  ObjectType getStringInstanceObjType() {
    return STRING_INSTANCE_OBJTYPE;
  }

  public void setNumberInstance(JSType t) {
    Preconditions.checkState(NUMBER_INSTANCE == null);
    Preconditions.checkNotNull(t);
    NUMBER_INSTANCE = t;
    if (t.isUnknown()) {
      NUMBER_OR_number = JSType.NUMBER;
      NUMBER_INSTANCE_OBJTYPE = ObjectType.TOP_OBJECT;
    } else {
      NUMBER_OR_number = JSType.join(JSType.NUMBER, NUMBER_INSTANCE);
      NUMBER_INSTANCE_OBJTYPE = Iterables.getOnlyElement(t.getObjs());
    }
    if (STRING_INSTANCE != null) {
      anyNumOrStr = JSType.join(NUMBER_OR_number, STRING_OR_string);
    }
  }

  public void setBooleanInstance(JSType t) {
    Preconditions.checkState(BOOLEAN_INSTANCE == null);
    Preconditions.checkNotNull(t);
    BOOLEAN_INSTANCE = t;
    if (t.isUnknown()) {
      BOOLEAN_INSTANCE_OBJTYPE = ObjectType.TOP_OBJECT;
    } else {
      BOOLEAN_INSTANCE_OBJTYPE = Iterables.getOnlyElement(t.getObjs());
    }
  }

  public void setStringInstance(JSType t) {
    Preconditions.checkState(STRING_INSTANCE == null);
    Preconditions.checkNotNull(t);
    STRING_INSTANCE = t;
    if (t.isUnknown()) {
      STRING_OR_string = JSType.STRING;
      STRING_INSTANCE_OBJTYPE = ObjectType.TOP_OBJECT;
    } else {
      STRING_OR_string = JSType.join(JSType.STRING, STRING_INSTANCE);
      STRING_INSTANCE_OBJTYPE = Iterables.getOnlyElement(t.getObjs());
    }
    if (NUMBER_INSTANCE != null) {
      anyNumOrStr = JSType.join(NUMBER_OR_number, STRING_OR_string);
    }
  }

  public boolean isNumberScalarOrObj(JSType t) {
    return t.isSubtypeOf(NUMBER_OR_number);
  }

  public boolean isStringScalarOrObj(JSType t) {
    return t.isSubtypeOf(STRING_OR_string);
  }

  // This method is a bit ad-hoc, but it allows us to not make the boxed
  // instances (which are not final) public.
  public boolean isNumStrScalarOrObj(JSType t) {
    return t.isSubtypeOf(anyNumOrStr);
  }
}
