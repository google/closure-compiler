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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.javascript.rhino.jstype.JSTypeNative;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * This class contains state that stays the same throughout a single type check,
 * but can vary across different compilations.
 * 1) It contains the built-in types, which we initialize when we read their
 *    definitions in externs.
 * 2) It knows whether we are in compatibility mode (looser checks in the style
 *    of the old type checker).
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
public final class JSTypes implements Serializable {
  // The builtin Object type represents instances of Object, but also objects
  // whose class we don't know, such as when a function @param is annotated as !Object.
  // However, it is very useful to know that some objects, such as object literals,
  // are *certainly* instances of Object and not some other class.
  // Knowing this allows things such as:
  // 1) Distinguishing between the top-object type and the type of an empty object literal.
  // 2) Calculating the meet of an object-literal type and an array to be bottom instead
  //    of an array type.
  // We use a special Object$ nominal type to represent explicit instances of Object.
  // For now, we only tag object literals as belonging to this class. Prototypes of classes
  // that inherit from Object, and objects created by "new Object" still have the general
  // "Object" as their nominal type.
  public static final String OBJLIT_CLASS_NAME = "Object{}";

  // The types that are final don't depend on the externs. The types that
  // are non-final, such as numberInstance, are filled in when we traverse
  // the externs during GlobalTypeInfo.
  @SuppressWarnings("ConstantField")
  public final JSType BOOLEAN;
  @SuppressWarnings("ConstantField")
  public final JSType BOTTOM;
  @SuppressWarnings("ConstantField")
  public final JSType FALSE_TYPE;
  @SuppressWarnings("ConstantField")
  public final JSType FALSY;
  @SuppressWarnings("ConstantField")
  public final JSType NULL;
  @SuppressWarnings("ConstantField")
  public final JSType NUMBER;
  @SuppressWarnings("ConstantField")
  public final JSType STRING;
  @SuppressWarnings("ConstantField")
  public final JSType TOP;
  @SuppressWarnings("ConstantField")
  public final JSType TOP_SCALAR;
  @SuppressWarnings("ConstantField")
  public final JSType TRUE_TYPE;
  @SuppressWarnings("ConstantField")
  public final JSType TRUTHY;
  @SuppressWarnings("ConstantField")
  public final JSType UNDEFINED;
  @SuppressWarnings("ConstantField")
  public final JSType UNKNOWN;
  @SuppressWarnings("ConstantField")
  public final JSType UNRESOLVED;

  private ObjectType topObjectType;
  @SuppressWarnings("ConstantField")
  final PersistentMap<String, Property> BOTTOM_PROPERTY_MAP;
  private JSType topObject;
  private ObjectType looseTopObject;
  private JSType topStruct;
  private JSType topDict;
  private ObjectType bottomObject;

  // Corresponds to Function, which is a subtype and supertype of all functions.
  @SuppressWarnings("ConstantField")
  final FunctionType QMARK_FUNCTION;
  @SuppressWarnings("ConstantField")
  final FunctionType BOTTOM_FUNCTION;
  // Theoretically, the top function takes an infinite number of required
  // arguments of type BOTTOM and returns TOP. If this function is ever called,
  // it's a type error. Despite that, we want to represent it and not go
  // directly to JSType.TOP, to avoid spurious warnings.
  // Eg, after an IF, we may see a type (number | top_function); this type could
  // get specialized to number and used legitimately.
  // We can't represent the theoretical top function, so we special-case
  // TOP_FUNCTION below. However, the outcome is the same; if our top function
  // is ever called, a warning is inevitable.
  @SuppressWarnings("ConstantField")
  final FunctionType TOP_FUNCTION;
  @SuppressWarnings("ConstantField")
  final FunctionType LOOSE_TOP_FUNCTION;

  @SuppressWarnings("ConstantField")
  final Map<String, JSType> MAP_TO_UNKNOWN;

  // Commonly-used types. We create them once here and reuse them
  @SuppressWarnings("ConstantField")
  public final JSType NUMBER_OR_STRING;
  @SuppressWarnings("ConstantField")
  final JSType UNDEFINED_OR_BOOLEAN;
  @SuppressWarnings("ConstantField")
  final JSType UNDEFINED_OR_NUMBER;
  @SuppressWarnings("ConstantField")
  final JSType UNDEFINED_OR_STRING;
  @SuppressWarnings("ConstantField")
  public final JSType NULL_OR_UNDEFINED;
  @SuppressWarnings("ConstantField")
  final JSType NULL_OR_BOOLEAN;
  @SuppressWarnings("ConstantField")
  final JSType NULL_OR_NUMBER;
  @SuppressWarnings("ConstantField")
  final JSType NULL_OR_STRING;

  // Instances of Boolean, Number and String; used for auto-boxing scalars.
  private JSType numberInstance;
  private JSType booleanInstance;
  private JSType stringInstance;

  private ObjectType numberInstanceObjtype;
  private ObjectType booleanInstanceObjtype;
  private ObjectType stringInstanceObjtype;

  private JSType numberOrNumber;
  private JSType stringOrString;
  private JSType anyNumOrStr;

  private JSType globalThis;
  private JSType regexpInstance;
  private RawNominalType arrayType;
  private RawNominalType builtinObject;
  private RawNominalType literalObject;
  private RawNominalType builtinFunction;
  private RawNominalType arguments;
  private RawNominalType iObject;
  private RawNominalType iArrayLike;
  private RawNominalType iterable;
  private RawNominalType iterator;
  private RawNominalType iIterableResult;
  private RawNominalType iTemplateArray;
  private RawNominalType generator;

  final boolean allowMethodsAsFunctions;
  final boolean looseSubtypingForLooseObjects;
  final boolean bivariantArrayGenerics;

  private JSTypes(boolean inCompatibilityMode) {
    Map<String, JSType> types = JSType.createScalars(this);
    this.BOOLEAN = checkNotNull(types.get("BOOLEAN"));
    this.BOTTOM = checkNotNull(types.get("BOTTOM"));
    this.FALSE_TYPE = checkNotNull(types.get("FALSE_TYPE"));
    this.FALSY = checkNotNull(types.get("FALSY"));
    this.NULL = checkNotNull(types.get("NULL"));
    this.NUMBER = checkNotNull(types.get("NUMBER"));
    this.STRING = checkNotNull(types.get("STRING"));
    this.TOP = checkNotNull(types.get("TOP"));
    this.TOP_SCALAR = checkNotNull(types.get("TOP_SCALAR"));
    this.TRUE_TYPE = checkNotNull(types.get("TRUE_TYPE"));
    this.TRUTHY = checkNotNull(types.get("TRUTHY"));
    this.UNDEFINED = checkNotNull(types.get("UNDEFINED"));
    this.UNKNOWN = checkNotNull(types.get("UNKNOWN"));
    this.UNRESOLVED = checkNotNull(types.get("UNRESOLVED"));

    this.UNDEFINED_OR_BOOLEAN = checkNotNull(types.get("UNDEFINED_OR_BOOLEAN"));
    this.UNDEFINED_OR_NUMBER = checkNotNull(types.get("UNDEFINED_OR_NUMBER"));
    this.UNDEFINED_OR_STRING = checkNotNull(types.get("UNDEFINED_OR_STRING"));
    this.NULL_OR_BOOLEAN = checkNotNull(types.get("NULL_OR_BOOLEAN"));
    this.NULL_OR_NUMBER = checkNotNull(types.get("NULL_OR_NUMBER"));
    this.NULL_OR_STRING = checkNotNull(types.get("NULL_OR_STRING"));
    this.NULL_OR_UNDEFINED = checkNotNull(types.get("NULL_OR_UNDEFINED"));
    this.NUMBER_OR_STRING = checkNotNull(types.get("NUMBER_OR_STRING"));

    Map<String, FunctionType> functions = FunctionType.createInitialFunctionTypes(this);
    this.QMARK_FUNCTION = checkNotNull(functions.get("QMARK_FUNCTION"));
    this.BOTTOM_FUNCTION = checkNotNull(functions.get("BOTTOM_FUNCTION"));
    this.TOP_FUNCTION = checkNotNull(functions.get("TOP_FUNCTION"));
    this.LOOSE_TOP_FUNCTION = checkNotNull(functions.get("LOOSE_TOP_FUNCTION"));
    this.BOTTOM_PROPERTY_MAP = PersistentMap.of("_", Property.make(this.BOTTOM, this.BOTTOM));

    this.allowMethodsAsFunctions = inCompatibilityMode;
    this.looseSubtypingForLooseObjects = inCompatibilityMode;
    this.bivariantArrayGenerics = inCompatibilityMode;

    class MapToUnknown implements Map<String, JSType>, Serializable {
      @Override
      public void clear() {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean containsKey(Object k) {
        return true;
      }

      @Override
      public boolean containsValue(Object v) {
        return v == UNKNOWN;
      }

      @Override
      public Set<Map.Entry<String, JSType>> entrySet() {
        throw new UnsupportedOperationException();
      }

      @Override
      public JSType get(Object k) {
        return UNKNOWN;
      }

      @Override
      public boolean isEmpty() {
        return false;
      }

      @Override
      public Set<String> keySet() {
        throw new UnsupportedOperationException();
      }

      @Override
      public JSType put(String k, JSType v) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void putAll(Map<? extends String, ? extends JSType> m) {
        throw new UnsupportedOperationException();
      }

      @Override
      public JSType remove(Object k) {
        throw new UnsupportedOperationException();
      }

      @Override
      public int size() {
        throw new UnsupportedOperationException();
      }

      @Override
      public Collection<JSType> values() {
        return Collections.singleton(UNKNOWN);
      }

      @Override
      public String toString() {
        return "MAP_TO_UNKNOWN";
      }
    }

    this.MAP_TO_UNKNOWN = new MapToUnknown();
  }

  public static JSTypes init(boolean inCompatibilityMode) {
    return new JSTypes(inCompatibilityMode);
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
    return fromFunctionType(this.TOP_FUNCTION);
  }

  // Corresponds to Function, which is a subtype and supertype of all functions.
  public JSType qmarkFunction() {
    return fromFunctionType(this.QMARK_FUNCTION);
  }

  public JSType getArrayInstance() {
    return getArrayInstance(this.UNKNOWN);
  }

  public JSType getIArrayLikeInstance(JSType t) {
    return this.iArrayLike == null
        ? this.UNKNOWN
        : this.iArrayLike.getInstanceAsJSType().instantiateGenerics(ImmutableList.of(t));
  }

  public JSType getIterableInstance(JSType t) {
    return this.iterable == null
        ? this.UNKNOWN
        : this.iterable.getInstanceAsJSType().instantiateGenerics(ImmutableList.of(t));
  }

  public JSType getIteratorInstance(JSType t) {
    return this.iterator == null
        ? this.UNKNOWN
        : this.iterator.getInstanceAsJSType().instantiateGenerics(ImmutableList.of(t));
  }

  public JSType getIIterableResultInstance(JSType t) {
    return this.iIterableResult == null
        ? this.UNKNOWN
        : this.iIterableResult.getInstanceAsJSType().instantiateGenerics(ImmutableList.of(t));
  }

  public JSType getGeneratorInstance(JSType t) {
    return this.generator == null
        ? this.UNKNOWN
        : this.generator.getInstanceAsJSType().instantiateGenerics(ImmutableList.of(t));
  }

  public NominalType getObjectType() {
    return this.builtinObject == null ? null : this.builtinObject.getAsNominalType();
  }

  ObjectType getTopObjectType() {
    return this.topObjectType;
  }

  ObjectType getLooseTopObjectType() {
    return this.looseTopObject;
  }

  public NominalType getLiteralObjNominalType() {
    return this.literalObject == null ? null : this.literalObject.getAsNominalType();
  }

  public JSType getEmptyObjectLiteral() {
    return this.literalObject == null ? null : this.literalObject.getInstanceAsJSType();
  }

  public JSType getTopObject() {
    return topObject;
  }

  public JSType getTopStruct() {
    return this.topStruct;
  }

  public JSType getTopDict() {
    return this.topDict;
  }

  ObjectType getBottomObject() {
    return this.bottomObject;
  }

  public RawNominalType getIObjectType() {
    return this.iObject;
  }

  public JSType getArrayInstance(JSType t) {
    if (arrayType == null) {
      return this.UNKNOWN;
    }
    ImmutableList<String> typeParams = arrayType.getTypeParameters();
    // typeParams can be != 1 in old externs files :-S
    if (typeParams.size() == 1) {
      return JSType.fromObjectType(ObjectType.fromNominalType(
          this.arrayType.getAsNominalType().instantiateGenerics(ImmutableList.of(t))));
    }
    return arrayType.getInstanceAsJSType();
  }

  public JSType getArgumentsArrayType(JSType t) {
    if (this.arguments == null) {
      return this.UNKNOWN;
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
    return regexpInstance != null ? regexpInstance : this.UNKNOWN;
  }

  public JSType getGlobalThis() {
    return globalThis != null ? globalThis : UNKNOWN;
  }

  public JSType getNumberInstance() {
    return numberInstance != null ? numberInstance : this.NUMBER;
  }

  public JSType getBooleanInstance() {
    return booleanInstance != null ? booleanInstance : this.BOOLEAN;
  }

  public JSType getStringInstance() {
    return stringInstance != null ? stringInstance : this.STRING;
  }

  ObjectType getNumberInstanceObjType() {
    return numberInstanceObjtype != null ? numberInstanceObjtype : this.topObjectType;
  }

  ObjectType getBooleanInstanceObjType() {
    return booleanInstanceObjtype != null ? booleanInstanceObjtype : this.topObjectType;
  }

  ObjectType getStringInstanceObjType() {
    return stringInstanceObjtype != null ? stringInstanceObjtype : this.topObjectType;
  }

  public JSType getArgumentsArrayType() {
    return getArgumentsArrayType(this.UNKNOWN);
  }

  public JSType getITemplateArrayType() {
    return iTemplateArray != null ? iTemplateArray.getInstanceAsJSType() : UNKNOWN;
  }

  public JSType getNativeType(JSTypeNative typeId) {
    // NOTE(aravindpg): not all JSTypeNative variants are handled here; add more as-needed.
    switch (typeId) {
      case ALL_TYPE:
        return TOP;
      case NO_TYPE:
        return BOTTOM;
      case UNKNOWN_TYPE:
        return UNKNOWN;
      case VOID_TYPE:
        return UNDEFINED;
      case NULL_TYPE:
        return NULL;
      case FALSE_TYPE:
        return FALSE_TYPE;
      case TRUE_TYPE:
        return TRUE_TYPE;
      case BOOLEAN_TYPE:
        return BOOLEAN;
      case STRING_TYPE:
        return STRING;
      case NUMBER_TYPE:
        return NUMBER;
      case NUMBER_STRING_BOOLEAN:
        return JSType.join(NUMBER_OR_STRING, BOOLEAN);
      case REGEXP_TYPE:
        return getRegexpType();
      case ARRAY_TYPE:
        return getArrayInstance();
      case OBJECT_TYPE:
        return getTopObject();
      case EMPTY_OBJECT_LITERAL_TYPE:
        return getEmptyObjectLiteral();
      case OBJECT_FUNCTION_TYPE:
        return fromFunctionType(getObjectType().getConstructorFunction());
      case TRUTHY:
        return TRUTHY;
      case NO_OBJECT_TYPE:
        return JSType.fromObjectType(getBottomObject());
      case FUNCTION_PROTOTYPE:
        return getFunctionType().getPrototypeObject();
      case FUNCTION_INSTANCE_TYPE:
        return fromFunctionType(QMARK_FUNCTION);
      case FUNCTION_FUNCTION_TYPE:
        return builtinFunction.toJSType();
      case OBJECT_PROTOTYPE:
      case TOP_LEVEL_PROTOTYPE:
        return getTopObject().getNominalTypeIfSingletonObj().getPrototypeObject();
      case GLOBAL_THIS:
        return getGlobalThis();
      case I_ITERABLE_RESULT_TYPE:
        return getIIterableResultInstance(UNKNOWN);
      case I_TEMPLATE_ARRAY_TYPE:
        return getITemplateArrayType();
      case ITERABLE_TYPE:
        return getIterableInstance(UNKNOWN);
      case ITERATOR_TYPE:
        return getIteratorInstance(UNKNOWN);
      case GENERATOR_TYPE:
        return getGeneratorInstance(UNKNOWN);
      default:
        throw new RuntimeException("Native type " + typeId.name() + " not found");
    }
  }

  public void setArgumentsType(RawNominalType arguments) {
    this.arguments = arguments;
  }

  public void setFunctionType(RawNominalType builtinFunction) {
    this.builtinFunction = builtinFunction;
  }

  public void setObjectType(RawNominalType builtinObject) {
    NominalType builtinObjectNT = builtinObject.getAsNominalType();
    this.builtinObject = builtinObject;
    this.topObjectType = builtinObject.getInstanceAsJSType().getObjTypeIfSingletonObj();
    this.looseTopObject = ObjectType.makeObjectType(
        this, builtinObjectNT, PersistentMap.<String, Property>create(),
        null, null, true, ObjectKind.UNRESTRICTED);
    this.topObject = JSType.fromObjectType(this.topObjectType);
    this.topStruct = JSType.fromObjectType(ObjectType.makeObjectType(
        this, builtinObjectNT, PersistentMap.<String, Property>create(),
        null, null, false, ObjectKind.STRUCT));
    this.topDict = JSType.fromObjectType(ObjectType.makeObjectType(
        this, builtinObjectNT, PersistentMap.<String, Property>create(),
        null, null, false, ObjectKind.DICT));
    this.bottomObject = ObjectType.createBottomObject(this);
  }

  public void setLiteralObjNominalType(RawNominalType literalObject) {
    this.literalObject = literalObject;
  }

  public void setArrayType(RawNominalType arrayType) {
    this.arrayType = arrayType;
  }

  public void setIObjectType(RawNominalType iObject) {
    this.iObject = iObject;
  }

  public void setIArrayLikeType(RawNominalType iArrayLike) {
    this.iArrayLike = iArrayLike;
  }

  public void setIterableType(RawNominalType iterable) {
    this.iterable = iterable;
  }

  public void setIteratorType(RawNominalType iterator) {
    this.iterator = iterator;
  }

  public void setIIterableResultType(RawNominalType iIterableResult) {
    this.iIterableResult = iIterableResult;
  }

  public void setITemplateArrayType(RawNominalType iTemplateArray) {
    this.iTemplateArray = iTemplateArray;
  }

  public void setGeneratorType(RawNominalType generator) {
    this.generator = generator;
  }

  public void setRegexpInstance(JSType regexpInstance) {
    this.regexpInstance = regexpInstance;
  }

  public void setGlobalThis(JSType globalThis) {
    Preconditions.checkState(this.globalThis == null,
        "Tried to reassign globalThis from %s to %s", this.globalThis, globalThis);
    this.globalThis = globalThis;
  }

  public void setNumberInstance(JSType t) {
    checkState(numberInstance == null);
    checkNotNull(t);
    numberInstance = t;
    numberOrNumber = JSType.join(this.NUMBER, numberInstance);
    numberInstanceObjtype = Iterables.getOnlyElement(t.getObjs());
    if (stringInstance != null) {
      anyNumOrStr = JSType.join(numberOrNumber, stringOrString);
    }
  }

  public void setBooleanInstance(JSType t) {
    checkState(booleanInstance == null);
    checkNotNull(t);
    booleanInstance = t;
    booleanInstanceObjtype = Iterables.getOnlyElement(t.getObjs());
  }

  public void setStringInstance(JSType t) {
    checkState(stringInstance == null);
    checkNotNull(t);
    stringInstance = t;
    stringOrString = JSType.join(this.STRING, stringInstance);
    stringInstanceObjtype = Iterables.getOnlyElement(t.getObjs());
    if (numberInstance != null) {
      anyNumOrStr = JSType.join(numberOrNumber, stringOrString);
    }
  }

  public boolean isNumberScalarOrObj(JSType t) {
    if (numberOrNumber == null) {
      return t.isSubtypeOf(this.NUMBER);
    }
    return t.isSubtypeOf(numberOrNumber);
  }

  public boolean isStringScalarOrObj(JSType t) {
    if (numberOrNumber == null) {
      return t.isSubtypeOf(this.STRING);
    }
    return t.isSubtypeOf(stringOrString);
  }

  // This method is a bit ad-hoc, but it allows us to not make the boxed
  // instances (which are not final) public.
  public boolean isNumStrScalarOrObj(JSType t) {
    if (anyNumOrStr == null) {
      return t.isSubtypeOf(this.NUMBER_OR_STRING);
    }
    return t.isSubtypeOf(anyNumOrStr);
  }

  @SuppressWarnings("ReferenceEquality")
  boolean isBottomPropertyMap(PersistentMap<String, Property> map) {
    return map == BOTTOM_PROPERTY_MAP;
  }

  public String createGetterPropName(String originalPropName) {
    return "%getter_fun" + originalPropName;
  }

  public String createSetterPropName(String originalPropName) {
    return "%setter_fun" + originalPropName;
  }
}
