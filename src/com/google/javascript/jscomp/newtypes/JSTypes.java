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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

/**
 * This class contains commonly used types, accessible from the jscomp package.
 * Also, any JSType utility methods that do not need to be in JSType.
 *
 * Built-in nominal types (Function, Object, Array, String, etc.) must be set
 * explicitly in externs, and are set with the corresponding setter methods
 * as they are crawled. They will remain null if not defined anywhere.
 *
 * There should only be one instance of this class per Compiler object.
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public final class JSTypes {
  // Instances of Boolean, Number and String; used for auto-boxing scalars.
  // Set when they are crawled in GlobalTypeInfo.
  private JSType numberInstance;
  private JSType booleanInstance;
  private JSType stringInstance;

  private ObjectType numberInstanceObjtype;
  private ObjectType booleanInstanceObjtype;
  private ObjectType stringInstanceObjtype;

  private JSType numberOrNumber;
  private JSType stringOrString;
  private JSType anyNumOrStr;

  private JSType regexpInstance;
  private RawNominalType arrayType;
  private RawNominalType builtinObject;
  private RawNominalType builtinFunction;
  private RawNominalType arguments;
  private RawNominalType iObject;

  private JSTypes() {}

  public static JSTypes make() {
    return new JSTypes();
  }

  public JSType fromFunctionType(FunctionType fn) {
    return JSType.fromFunctionType(fn, getFunctionType());
  }

  public NominalType getFunctionType() {
    if (builtinFunction == null) {
      return null;
    }
    return builtinFunction.getAsNominalType();
  }

  public JSType looseTopFunction() {
    return topFunction().withLoose();
  }

  public JSType topFunction() {
    return fromFunctionType(FunctionType.TOP_FUNCTION);
  }

  // Corresponds to Function, which is a subtype and supertype of all functions.
  public JSType qmarkFunction() {
    return fromFunctionType(FunctionType.QMARK_FUNCTION);
  }

  public JSType getArrayInstance() {
    return getArrayInstance(JSType.UNKNOWN);
  }

  public NominalType getObjectType() {
    return this.builtinObject == null ? null : this.builtinObject.getAsNominalType();
  }

  public NominalType getIObjectType() {
    return this.iObject == null ? null : this.iObject.getAsNominalType();
  }

  public JSType getArrayInstance(JSType t) {
    if (arrayType == null) {
      return JSType.UNKNOWN;
    }
    ImmutableList<String> typeParams = arrayType.getTypeParameters();
    JSType result = arrayType.getInstanceAsJSType();
    // typeParams can be != 1 in old externs files :-S
    if (typeParams.size() == 1) {
      String typeParam = Iterables.getOnlyElement(typeParams);
      result = result.substituteGenerics(ImmutableMap.of(typeParam, t));
    }
    return result;
  }

  public JSType getArgumentsArrayType(JSType t) {
    if (this.arguments == null) {
      return JSType.UNKNOWN;
    }
    ImmutableList<String> typeParams = this.arguments.getTypeParameters();
    JSType result = this.arguments.getInstanceAsJSType();
    // typeParams can be != 1 in old externs files :-S
    if (typeParams.size() == 1) {
      String typeParam = Iterables.getOnlyElement(typeParams);
      result = result.substituteGenerics(ImmutableMap.of(typeParam, t));
    }
    return result;
  }

  public JSType getRegexpType() {
    return regexpInstance != null ? regexpInstance : JSType.UNKNOWN;
  }

  JSType getNumberInstance() {
    return numberInstance != null ? numberInstance : JSType.NUMBER;
  }

  JSType getBooleanInstance() {
    return booleanInstance != null ? booleanInstance : JSType.BOOLEAN;
  }

  JSType getStringInstance() {
    return stringInstance != null ? stringInstance : JSType.STRING;
  }

  ObjectType getNumberInstanceObjType() {
    return numberInstanceObjtype != null
        ? numberInstanceObjtype : ObjectType.TOP_OBJECT;
  }

  ObjectType getBooleanInstanceObjType() {
    return booleanInstanceObjtype != null
        ? booleanInstanceObjtype : ObjectType.TOP_OBJECT;
  }

  ObjectType getStringInstanceObjType() {
    return stringInstanceObjtype != null
        ? stringInstanceObjtype : ObjectType.TOP_OBJECT;
  }

  public JSType getArgumentsArrayType() {
    return getArgumentsArrayType(JSType.UNKNOWN);
  }

  public void setArgumentsType(RawNominalType arguments) {
    this.arguments = arguments;
  }

  public void setFunctionType(RawNominalType builtinFunction) {
    this.builtinFunction = builtinFunction;
  }

  public void setObjectType(RawNominalType builtinObject) {
    this.builtinObject = builtinObject;
    ObjectType.setObjectType(builtinObject.getAsNominalType());
  }

  public void setArrayType(RawNominalType arrayType) {
    this.arrayType = arrayType;
  }

  public void setIObjectType(RawNominalType iObject) {
    this.iObject = iObject;
  }

  public void setRegexpInstance(JSType regexpInstance) {
    this.regexpInstance = regexpInstance;
  }

  public void setNumberInstance(JSType t) {
    Preconditions.checkState(numberInstance == null);
    Preconditions.checkNotNull(t);
    numberInstance = t;
    numberOrNumber = JSType.join(JSType.NUMBER, numberInstance);
    numberInstanceObjtype = Iterables.getOnlyElement(t.getObjs());
    if (stringInstance != null) {
      anyNumOrStr = JSType.join(numberOrNumber, stringOrString);
    }
  }

  public void setBooleanInstance(JSType t) {
    Preconditions.checkState(booleanInstance == null);
    Preconditions.checkNotNull(t);
    booleanInstance = t;
    booleanInstanceObjtype = Iterables.getOnlyElement(t.getObjs());
  }

  public void setStringInstance(JSType t) {
    Preconditions.checkState(stringInstance == null);
    Preconditions.checkNotNull(t);
    stringInstance = t;
    stringOrString = JSType.join(JSType.STRING, stringInstance);
    stringInstanceObjtype = Iterables.getOnlyElement(t.getObjs());
    if (numberInstance != null) {
      anyNumOrStr = JSType.join(numberOrNumber, stringOrString);
    }
  }

  public boolean isNumberScalarOrObj(JSType t) {
    if (numberOrNumber == null) {
      return t.isSubtypeOf(JSType.NUMBER);
    }
    return t.isSubtypeOf(numberOrNumber);
  }

  public boolean isStringScalarOrObj(JSType t) {
    if (numberOrNumber == null) {
      return t.isSubtypeOf(JSType.STRING);
    }
    return t.isSubtypeOf(stringOrString);
  }

  // This method is a bit ad-hoc, but it allows us to not make the boxed
  // instances (which are not final) public.
  public boolean isNumStrScalarOrObj(JSType t) {
    if (anyNumOrStr == null) {
      return t.isSubtypeOf(JSType.NUM_OR_STR);
    }
    return t.isSubtypeOf(anyNumOrStr);
  }
}
