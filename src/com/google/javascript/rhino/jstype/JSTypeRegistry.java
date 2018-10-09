/*
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Rhino code, released
 * May 6, 1999.
 *
 * The Initial Developer of the Original Code is
 * Netscape Communications Corporation.
 * Portions created by the Initial Developer are Copyright (C) 1997-1999
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Bob Jervis
 *   Google Inc.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU General Public License Version 2 or later (the "GPL"), in which
 * case the provisions of the GPL are applicable instead of those above. If
 * you wish to allow use of your version of this file only under the terms of
 * the GPL and not to allow others to use your version of this file under the
 * MPL, indicate your decision by deleting the provisions above and replacing
 * them with the notice and other provisions required by the GPL. If you do
 * not delete the provisions above, a recipient may use your version of this
 * file under either the MPL or the GPL.
 *
 * ***** END LICENSE BLOCK ***** */

package com.google.javascript.rhino.jstype;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.javascript.rhino.jstype.JSTypeNative.ALL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.HamtPMap;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.PMap;
import com.google.javascript.rhino.SimpleErrorReporter;
import com.google.javascript.rhino.StaticScope;
import com.google.javascript.rhino.StaticSlot;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType.Kind;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * The type registry is used to resolve named types.
 *
 * <p>This class is not thread-safe.
 *
 */
public class JSTypeRegistry implements Serializable {
  /**
   * The template variable corresponding to the KEY type in {@code IObject<KEY, VALUE>}
   * (plus the builtin Javascript Object).
   */
  private TemplateType iObjectIndexTemplateKey;

  /**
   * The template variable corresponding to the VALUE type in {@code IObject<KEY, VALUE>}
   * (plus the builtin Javascript Object).
   */
  private TemplateType iObjectElementTemplateKey;
  private static final String I_OBJECT_ELEMENT_TEMPLATE = "IObject#VALUE";

  /** The template variable corresponding to the VALUE type in {@code Iterable<VALUE>} */
  private TemplateType iterableTemplate;

  /** The template variable corresponding to the VALUE type in {@code Iterator<VALUE>} */
  private TemplateType iteratorTemplate;

  /** The template variable corresponding to the VALUE type in {@code Generator<VALUE>} */
  private TemplateType generatorTemplate;

  /** The template variable corresponding to the VALUE type in {@code IThenable<VALUE>} */
  private TemplateType iThenableTemplateKey;

  /** The template variable corresponding to the TYPE in {@code Promise<TYPE>} */
  private TemplateType promiseTemplateKey;

  /**
   * The template variable in {@code Array<T>}
   */
  private TemplateType arrayElementTemplateKey;

  @Deprecated
  public static final String OBJECT_ELEMENT_TEMPLATE = I_OBJECT_ELEMENT_TEMPLATE;

  // TODO(user): An instance of this class should be used during
  // compilation. We also want to make all types' constructors package private
  // and force usage of this registry instead. This will allow us to evolve the
  // types without being tied by an open API.

  private final transient ErrorReporter reporter;

  // We use an Array instead of an immutable list because this lookup needs
  // to be very fast. When it was an immutable list, we were spending 5% of
  // CPU time on bounds checking inside get().
  private final JSType[] nativeTypes;

  private final Table<Node, String, JSType> scopedNameTable = HashBasedTable.create();

  // NOTE: This would normally be "static final" but that causes unit test failures
  // when serializing and deserializing compiler state for multistage builds.
  private final Node nameTableGlobalRoot = new Node(Token.ROOT);

  // NOTE(nicksantos): This is a terrible terrible hack. When type expressions
  // are evaluated, we need to be able to decide whether that type name
  // resolves to a nullable type or a non-nullable type. Object types are
  // nullable, but enum types are not.
  //
  // Notice that it's not good enough to just declare enum types sooner.
  // For example, if we have
  // /** @enum {MyObject} */ var MyEnum = ...;
  // we won't be to declare "MyEnum" without evaluating the expression
  // {MyObject}, and following those dependencies starts to lead us into
  // undecidable territory. Instead, we "pre-declare" enum types and typedefs,
  // so that the expression resolver can decide whether a given name is
  // nullable or not.
  private final Set<String> nonNullableTypeNames = new LinkedHashSet<>();

  // Types that have been "forward-declared."
  // If these types are not declared anywhere in the binary, we shouldn't
  // try to type-check them at all.
  private final transient Set<String> forwardDeclaredTypes;

  // A map of properties to the types on which those properties have been declared.
  private final Map<String, UnionTypeBuilder> typesIndexedByProperty = new HashMap<>();

  private JSType sentinelObjectLiteral;

  // To avoid blowing up the size of typesIndexedByProperty, we use the sentinel object
  // literal instead of registering arbitrarily many types.
  // But because of the way unions are constructed, some properties of record types in unions
  // are getting dropped and cause spurious "non-existent property" warnings.
  // The next two fields avoid the warnings. The first field contains property names of records
  // that participate in unions, and have caused properties to be dropped.
  // The second field contains the names of the dropped properties. When checking
  // canPropertyBeDefined, if the type has a property in propertiesOfSupertypesInUnions, we
  // consider it to possibly have any property in droppedPropertiesOfUnions. This is a loose
  // check, but we restrict it to records that may be present in unions, and it allows us to
  // keep typesIndexedByProperty small.
  private final Set<String> propertiesOfSupertypesInUnions = new HashSet<>();
  private final Set<String> droppedPropertiesOfUnions = new HashSet<>();

  // A map of properties to each reference type on which those
  // properties have been declared. Each type has a unique name used
  // for de-duping.
  private transient Map<String, Map<String, ObjectType>> eachRefTypeIndexedByProperty =
      new LinkedHashMap<>();

  // A map of properties to the greatest subtype on which those properties have
  // been declared. This is filled lazily from the types declared in
  // typesIndexedByProperty.
  private final Map<String, JSType> greatestSubtypeByProperty = new HashMap<>();

  // A map from interface name to types that implement it.
  private transient Multimap<String, FunctionType> interfaceToImplementors =
      LinkedHashMultimap.create();

  // All the unresolved named types.
  private final List<NamedType> unresolvedNamedTypes = new ArrayList<>();

  // A single empty TemplateTypeMap, which can be safely reused in cases where
  // there are no template types.
  private final TemplateTypeMap emptyTemplateTypeMap;

  public JSTypeRegistry(ErrorReporter reporter) {
    this(reporter, ImmutableSet.<String>of());
  }

  /** Constructs a new type registry populated with the built-in types. */
  public JSTypeRegistry(ErrorReporter reporter, Set<String> forwardDeclaredTypes) {
    this.reporter = reporter;
    this.forwardDeclaredTypes = forwardDeclaredTypes;
    this.emptyTemplateTypeMap = new TemplateTypeMap(
        this, ImmutableList.<TemplateType>of(), ImmutableList.<JSType>of());
    nativeTypes = new JSType[JSTypeNative.values().length];
    resetForTypeCheck();
  }

  private JSType getSentinelObjectLiteral() {
    if (sentinelObjectLiteral == null) {
      sentinelObjectLiteral = createAnonymousObjectType(null);
    }
    return sentinelObjectLiteral;
  }

  /**
   * @return The template variable corresponding to the property value type for
   * Javascript Objects and Arrays.
   */
  public TemplateType getObjectElementKey() {
    return iObjectElementTemplateKey;
  }

  /**
   * @return The template variable corresponding to the
   * property key type of the built-in Javascript object.
   */
  public TemplateType getObjectIndexKey() {
    checkNotNull(iObjectIndexTemplateKey);
    return iObjectIndexTemplateKey;
  }

  /**
   * @return The template variable corresponding to the
   * property key type of the built-in Javascript object.
   */
  public TemplateType getThenableValueKey() {
    checkNotNull(iThenableTemplateKey);
    return iThenableTemplateKey;
  }

  /**
   * @return The template variable for the Iterable interface.
   */
  public TemplateType getIterableTemplate() {
    return checkNotNull(iterableTemplate);
  }

  /** @return The template variable for the Iterator interface. */
  public TemplateType getIteratorTemplate() {
    return checkNotNull(iteratorTemplate);
  }

  /** @return The template variable for the IThenable interface. */
  public TemplateType getIThenableTemplate() {
    return checkNotNull(iThenableTemplateKey);
  }

  /** @return return an immutable list of template types of the given builtin. */
  public ImmutableList<TemplateType> maybeGetTemplateTypesOfBuiltin(String fnName) {
    JSType type = getType(null, fnName);
    ObjectType objType = type == null ? null : type.toObjectType();
    if (objType != null && objType.isNativeObjectType()) {
      ImmutableList<TemplateType> templateKeys =
          objType.getTemplateTypeMap().getUnfilledTemplateKeys();
      return templateKeys;
    }
    return null;
  }

  public ErrorReporter getErrorReporter() {
    return reporter;
  }

  /**
   * Reset to run the TypeCheck pass.
   */
  public void resetForTypeCheck() {
    typesIndexedByProperty.clear();
    eachRefTypeIndexedByProperty.clear();
    initializeBuiltInTypes();
    scopedNameTable.clear();
    initializeRegistry();
  }

  private void initializeBuiltInTypes() {
    // These locals shouldn't be all caps.
    BooleanType booleanType = new BooleanType(this);
    registerNativeType(JSTypeNative.BOOLEAN_TYPE, booleanType);

    NullType nullType = new NullType(this);
    registerNativeType(JSTypeNative.NULL_TYPE, nullType);

    NumberType numberType = new NumberType(this);
    registerNativeType(JSTypeNative.NUMBER_TYPE, numberType);

    StringType stringType = new StringType(this);
    registerNativeType(JSTypeNative.STRING_TYPE, stringType);

    SymbolType symbolType = new SymbolType(this);
    registerNativeType(JSTypeNative.SYMBOL_TYPE, symbolType);

    UnknownType unknownType = new UnknownType(this, false);
    registerNativeType(JSTypeNative.UNKNOWN_TYPE, unknownType);
    UnknownType checkedUnknownType = new UnknownType(this, true);
    registerNativeType(JSTypeNative.CHECKED_UNKNOWN_TYPE, checkedUnknownType);

    VoidType voidType = new VoidType(this);
    registerNativeType(JSTypeNative.VOID_TYPE, voidType);

    AllType allType = new AllType(this);
    registerNativeType(JSTypeNative.ALL_TYPE, allType);

    // Template Types
    iObjectIndexTemplateKey = new TemplateType(this, "IObject#KEY1");
    iObjectElementTemplateKey = new TemplateType(this, I_OBJECT_ELEMENT_TEMPLATE);
    // These should match the template type name in externs files.
    arrayElementTemplateKey = new TemplateType(this, "T");
    iteratorTemplate = new TemplateType(this, "VALUE");
    generatorTemplate = new TemplateType(this, "VALUE");
    iterableTemplate = new TemplateType(this, "VALUE");
    iThenableTemplateKey = new TemplateType(this, "TYPE");
    promiseTemplateKey = new TemplateType(this, "TYPE");

    // Top Level Prototype (the One)
    // The initializations of TOP_LEVEL_PROTOTYPE and OBJECT_FUNCTION_TYPE
    // use each other's results, so at least one of them will get null
    // instead of an actual type; however, this seems to be benign.
    PrototypeObjectType topLevelPrototype =
        new PrototypeObjectType(this, null, null, true, null);
    registerNativeType(JSTypeNative.TOP_LEVEL_PROTOTYPE, topLevelPrototype);

    // IObject
    FunctionType iObjectFunctionType =
        nativeInterface("IObject", iObjectIndexTemplateKey, iObjectElementTemplateKey);
    registerNativeType(JSTypeNative.I_OBJECT_FUNCTION_TYPE, iObjectFunctionType);
    registerNativeType(JSTypeNative.I_OBJECT_TYPE, iObjectFunctionType.getInstanceType());

    // Object
    FunctionType objectFunctionType =
        nativeConstructorBuilder("Object")
            .withParamsNode(createOptionalParameters(allType))
            .withReturnsOwnInstanceType()
            .withTemplateKeys(iObjectIndexTemplateKey, iObjectElementTemplateKey)
            .build();
    objectFunctionType.setPrototype(topLevelPrototype, null);
    registerNativeType(JSTypeNative.OBJECT_FUNCTION_TYPE, objectFunctionType);
    registerNativeType(JSTypeNative.OBJECT_PROTOTYPE, objectFunctionType.getPrototype());

    ObjectType objectType = objectFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.OBJECT_TYPE, objectType);

    // Function
    FunctionType functionFunctionType =
        nativeConstructorBuilder("Function")
            .withParamsNode(createParametersWithVarArgs(allType))
            .withReturnType(unknownType)
            .withPrototypeBasedOn(objectType)
            .build();
    functionFunctionType.setPrototypeBasedOn(objectType);
    registerNativeType(JSTypeNative.FUNCTION_FUNCTION_TYPE, functionFunctionType);

    ObjectType functionPrototype = functionFunctionType.getPrototype();
    registerNativeType(JSTypeNative.FUNCTION_PROTOTYPE, functionPrototype);

    // Bottom Types
    NoType noType = new NoType(this);
    registerNativeType(JSTypeNative.NO_TYPE, noType);

    NoObjectType noObjectType = new NoObjectType(this);
    registerNativeType(JSTypeNative.NO_OBJECT_TYPE, noObjectType);

    NoResolvedType noResolvedType = new NoResolvedType(this);
    registerNativeType(JSTypeNative.NO_RESOLVED_TYPE, noResolvedType);

    // Array
    FunctionType arrayFunctionType =
        nativeConstructorBuilder("Array")
            .withParamsNode(createParametersWithVarArgs(allType))
            .withReturnsOwnInstanceType()
            .withTemplateTypeMap(
                new TemplateTypeMap(
                    this,
                    ImmutableList.of(iObjectElementTemplateKey, arrayElementTemplateKey),
                    ImmutableList.<JSType>of(arrayElementTemplateKey)))
            .build();
    arrayFunctionType.getPrototype(); // Force initialization
    registerNativeType(JSTypeNative.ARRAY_FUNCTION_TYPE, arrayFunctionType);

    ObjectType arrayType = arrayFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.ARRAY_TYPE, arrayType);

    // ITemplateArray extends !Array<string>
    FunctionType iTemplateArrayFunctionType =
        nativeConstructorBuilder("ITemplateArray")
            .withParamsNode(createEmptyParams())
            .build();
    registerNativeType(
        JSTypeNative.I_TEMPLATE_ARRAY_TYPE, iTemplateArrayFunctionType.getInstanceType());

    FunctionType iterableFunctionType = nativeInterface("Iterable", iterableTemplate);
    registerNativeType(JSTypeNative.ITERABLE_FUNCTION_TYPE, iterableFunctionType);
    registerNativeType(JSTypeNative.ITERABLE_TYPE, iterableFunctionType.getInstanceType());

    FunctionType iteratorFunctionType = nativeInterface("Iterator", iteratorTemplate);
    registerNativeType(JSTypeNative.ITERATOR_FUNCTION_TYPE, iteratorFunctionType);
    registerNativeType(JSTypeNative.ITERATOR_TYPE, iteratorFunctionType.getInstanceType());

    FunctionType generatorFunctionType = nativeInterface("Generator", generatorTemplate);
    registerNativeType(JSTypeNative.GENERATOR_FUNCTION_TYPE, generatorFunctionType);
    registerNativeType(JSTypeNative.GENERATOR_TYPE, generatorFunctionType.getInstanceType());

    FunctionType ithenableFunctionType = nativeInterface("IThenable", iThenableTemplateKey);
    registerNativeType(JSTypeNative.I_THENABLE_FUNCTION_TYPE, ithenableFunctionType);
    registerNativeType(JSTypeNative.I_THENABLE_TYPE, ithenableFunctionType.getInstanceType());

    // Thenable is an @typedef
    JSType thenableType = createRecordType(ImmutableMap.of("then", unknownType));
    identifyNonNullableName("Thenable");
    registerNativeType(JSTypeNative.THENABLE_TYPE, thenableType);

    // Create built-in Promise type, whose constructor takes one parameter.
    // @param {function(
    //             function((TYPE|IThenable<TYPE>|Thenable|null)=),
    //             function(*=))} resolver
    JSType promiseParameterType =
        createFunctionType(
            /* returnType= */ unknownType,
            /* parameterTypes= */ createFunctionType(
                unknownType,
                createOptionalParameters(
                    createUnionType(
                        promiseTemplateKey,
                        createTemplatizedType(
                            ithenableFunctionType.getInstanceType(), promiseTemplateKey),
                        thenableType,
                        nullType))),
            /* parameterTypes= */ createFunctionType(
                unknownType, createOptionalParameters(allType)));
    Node promiseParameter = IR.name("");
    promiseParameter.setJSType(promiseParameterType);

    FunctionType promiseFunctionType =
        nativeConstructorBuilder("Promise")
            .withParamsNode(IR.paramList(promiseParameter))
            .withTemplateKeys(promiseTemplateKey)
            .withExtendedTemplate(iThenableTemplateKey, promiseTemplateKey)
            .build();

    registerNativeType(JSTypeNative.PROMISE_FUNCTION_TYPE, promiseFunctionType);
    registerNativeType(JSTypeNative.PROMISE_TYPE, promiseFunctionType.getInstanceType());

    // Boolean
    FunctionType booleanObjectFunctionType =
        nativeConstructorBuilder("Boolean")
            .withParamsNode(createOptionalParameters(allType))
            .withReturnType(booleanType)
            .build();
    booleanObjectFunctionType.getPrototype(); // Force initialization
    registerNativeType(JSTypeNative.BOOLEAN_OBJECT_FUNCTION_TYPE, booleanObjectFunctionType);

    ObjectType booleanObjectType = booleanObjectFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.BOOLEAN_OBJECT_TYPE, booleanObjectType);

    // Date
    FunctionType dateFunctionType =
        nativeConstructorBuilder("Date")
            .withParamsNode(createOptionalParameters(
                unknownType,
                unknownType,
                unknownType,
                unknownType,
                unknownType,
                unknownType,
                unknownType))
            .withReturnType(stringType)
            .build();
    dateFunctionType.getPrototype(); // Force initialization
    registerNativeType(JSTypeNative.DATE_FUNCTION_TYPE, dateFunctionType);

    ObjectType dateType = dateFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.DATE_TYPE, dateType);

    // Number
    FunctionType numberObjectFunctionType =
        nativeConstructorBuilder("Number")
            .withParamsNode(createOptionalParameters(allType))
            .withReturnType(numberType)
            .build();
    numberObjectFunctionType.getPrototype(); // Force initialization
    registerNativeType(JSTypeNative.NUMBER_OBJECT_FUNCTION_TYPE, numberObjectFunctionType);

    ObjectType numberObjectType = numberObjectFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.NUMBER_OBJECT_TYPE, numberObjectType);

    // RegExp
    FunctionType regexpFunctionType =
        nativeConstructorBuilder("RegExp")
            .withParamsNode(createOptionalParameters(allType, allType))
            .withReturnsOwnInstanceType()
            .build();
    regexpFunctionType.getPrototype(); // Force initialization
    registerNativeType(JSTypeNative.REGEXP_FUNCTION_TYPE, regexpFunctionType);

    ObjectType regexpType = regexpFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.REGEXP_TYPE, regexpType);

    // String
    FunctionType stringObjectFunctionType =
        nativeConstructorBuilder("String")
            .withParamsNode(createOptionalParameters(allType))
            .withReturnType(stringType)
            .build();
    stringObjectFunctionType.getPrototype(); // Force initialization
    registerNativeType(JSTypeNative.STRING_OBJECT_FUNCTION_TYPE, stringObjectFunctionType);

    ObjectType stringObjectType = stringObjectFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.STRING_OBJECT_TYPE, stringObjectType);

    // Symbol
    // NOTE: While "Symbol" is a class, with an instance type and prototype
    // it is illegal to call "new Symbol".  This is checked in the type checker.
    FunctionType symbolObjectFunctionType =
        nativeConstructorBuilder("Symbol")
            .withParamsNode(createOptionalParameters(allType))
            .withReturnType(symbolType)
            .build();
    symbolObjectFunctionType.getPrototype(); // Force initialization
    registerNativeType(
        JSTypeNative.SYMBOL_OBJECT_FUNCTION_TYPE, symbolObjectFunctionType);

    ObjectType symbolObjectType = symbolObjectFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.SYMBOL_OBJECT_TYPE, symbolObjectType);

    // (null|void)
    JSType nullVoid = createUnionType(nullType, voidType);
    registerNativeType(JSTypeNative.NULL_VOID, nullVoid);

    // (Object|symbol)
    JSType objectSymbol = createUnionType(objectType, symbolType);
    registerNativeType(JSTypeNative.OBJECT_SYMBOL, objectSymbol);

    // (Object|string|number)
    JSType objectNumberString = createUnionType(objectType, numberType, stringType);
    registerNativeType(JSTypeNative.OBJECT_NUMBER_STRING, objectNumberString);

    // (Object|string|number|boolean)
    JSType objectNumberStringBoolean =
        createUnionType(objectType, numberType, stringType, booleanType);
    registerNativeType(JSTypeNative.OBJECT_NUMBER_STRING_BOOLEAN, objectNumberStringBoolean);

    // (Object|string|number|boolean|symbol)
    JSType objectNumberStringBooleanSymbol =
        createUnionType(objectType, numberType, stringType, booleanType, symbolType);
    registerNativeType(JSTypeNative.OBJECT_NUMBER_STRING_BOOLEAN_SYMBOL,
        objectNumberStringBooleanSymbol);

    // (string|number|boolean)
    JSType numberStringBoolean = createUnionType(numberType, stringType, booleanType);
    registerNativeType(JSTypeNative.NUMBER_STRING_BOOLEAN, numberStringBoolean);

    // (string|number|boolean|symbol)
    JSType numberStringBooleanSymbol =
        createUnionType(numberType, stringType, booleanType, symbolType);
    registerNativeType(JSTypeNative.NUMBER_STRING_BOOLEAN_SYMBOL, numberStringBooleanSymbol);

    // (number|symbol)
    JSType numberSymbol = createUnionType(numberType, symbolType);
    registerNativeType(JSTypeNative.NUMBER_SYMBOL, numberSymbol);

    // (string|symbol)
    JSType stringSymbol = createUnionType(stringType, symbolType);
    registerNativeType(JSTypeNative.STRING_SYMBOL, stringSymbol);

    // (string,number)
    JSType numberString = createUnionType(numberType, stringType);
    registerNativeType(JSTypeNative.NUMBER_STRING, numberString);

    // (string,number,symbol)
    JSType numberStringSymbol = createUnionType(numberType, stringType, symbolType);
    registerNativeType(JSTypeNative.NUMBER_STRING_SYMBOL, numberStringSymbol);

    // Native object properties are filled in by externs...

    // (String|string)
    JSType stringValueOrObjectType = createUnionType(stringObjectType, stringType);
    registerNativeType(JSTypeNative.STRING_VALUE_OR_OBJECT_TYPE, stringValueOrObjectType);

    // (Number|number)
    JSType numberValueOrObjectType = createUnionType(numberObjectType, numberType);
    registerNativeType(JSTypeNative.NUMBER_VALUE_OR_OBJECT_TYPE, numberValueOrObjectType);

    // (Symbol, symbol)
    JSType symbolValueOrObjectType = createUnionType(symbolObjectType, symbolType);
    registerNativeType(JSTypeNative.SYMBOL_VALUE_OR_OBJECT_TYPE, symbolValueOrObjectType);

    // unknown function type, i.e. (?...) -> ?
    FunctionType u2uFunctionType = createFunctionTypeWithVarArgs(unknownType, unknownType);
    registerNativeType(JSTypeNative.U2U_FUNCTION_TYPE, u2uFunctionType);

    // unknown constructor type, i.e. (?...) -> ? with the Unknown type
    // as instance type
    FunctionType u2uConstructorType =
        // This is equivalent to
        // createConstructorType(unknownType, true, unknownType), but,
        // in addition, overrides getInstanceType() to return the NoObject type
        // instead of a new anonymous object.
        new FunctionType(
            this,
            "Function",
            /* source= */ null,
            createArrowType(createParametersWithVarArgs(unknownType), unknownType),
            /* typeOfThis= */ unknownType,
            /* templateTypeMap= */ null,
            Kind.CONSTRUCTOR,
            /* nativeType= */ true,
            /* isAbstract= */ false) {
          private static final long serialVersionUID = 1L;

          @Override
          public FunctionType getConstructor() {
            return registry.getNativeFunctionType(JSTypeNative.FUNCTION_FUNCTION_TYPE);
          }
        };

    // The u2uConstructor is weird, because it's the supertype of its own constructor.
    functionFunctionType.setInstanceType(u2uConstructorType);
    u2uConstructorType.setImplicitPrototype(functionPrototype);
    registerNativeType(JSTypeNative.U2U_CONSTRUCTOR_TYPE, u2uConstructorType);
    registerNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE, u2uConstructorType);

    // least function type, i.e. (All...) -> NoType
    FunctionType leastFunctionType = createNativeFunctionTypeWithVarArgs(noType, allType);
    registerNativeType(JSTypeNative.LEAST_FUNCTION_TYPE, leastFunctionType);

    // the 'this' object in the global scope
    FunctionType globalThisCtor =
        nativeConstructorBuilder("global this")
            .withParamsNode(createParameters(allType))
            .withReturnType(numberType)
            .build();
    ObjectType globalThis = globalThisCtor.getInstanceType();
    registerNativeType(JSTypeNative.GLOBAL_THIS, globalThis);

    // greatest function type, i.e. (NoType...) -> All
    FunctionType greatestFunctionType = createNativeFunctionTypeWithVarArgs(allType, noType);
    registerNativeType(JSTypeNative.GREATEST_FUNCTION_TYPE, greatestFunctionType);

    // Register the prototype property. See the comments below in
    // registerPropertyOnType about the bootstrapping process.
    registerPropertyOnType("prototype", objectFunctionType);
  }

  private void initializeRegistry() {
    registerGlobalType(getNativeType(JSTypeNative.ARRAY_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.BOOLEAN_OBJECT_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.BOOLEAN_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.ITERABLE_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.ITERATOR_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.GENERATOR_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.DATE_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.I_OBJECT_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.I_TEMPLATE_ARRAY_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.I_THENABLE_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.NULL_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.NULL_TYPE), "Null");
    registerGlobalType(getNativeType(JSTypeNative.NUMBER_OBJECT_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.NUMBER_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.OBJECT_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.PROMISE_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.REGEXP_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.STRING_OBJECT_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.STRING_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.SYMBOL_OBJECT_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.SYMBOL_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.THENABLE_TYPE), "Thenable");
    registerGlobalType(getNativeType(JSTypeNative.VOID_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.VOID_TYPE), "Undefined");
    registerGlobalType(getNativeType(JSTypeNative.VOID_TYPE), "void");
    registerGlobalType(getNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE), "Function");
    registerGlobalType(getNativeType(JSTypeNative.GLOBAL_THIS), "Global");
  }

  private static boolean isCompoundName(String name) {
    return name.indexOf('.') != -1;
  }

  private static String getRootElementOfName(String name) {
    int index = name.indexOf('.');
    if (index != -1) {
      return name.substring(0, index);
    }
    return name;
  }

  /**
   * @return Which scope in the provided scope chain the provided name is declared in, or else null.
   *
   * This assumed that the Scope construction is
   * complete.  It can not be used during scope construction to determine if a name is already
   * defined as a shadowed name from a parent scope would be returned.
   */
  private static StaticScope getLookupScope(StaticScope scope, String name) {
    if (scope != null && scope.getParentScope() != null) {
      StaticSlot slot = scope.getSlot(getRootElementOfName(name));
      return slot != null ? slot.getScope() : null;
    }
    return scope;
  }

  // TODO(johnlenz): Remove the use of this.  Instead the callers should provide the correct scope.
  // This method isn't valid if the scope is under construction.  The type registry does not
  // have enough context to select the correct scope if the symbol isn't already present.
  // Specifically, a "var" declaration in a block belongs in an outer scope.
  /**
   * @return Which scope in the provided scope chain the provided name is declared in, or else
   * the provide scope if it is not declared.  The assumption is that provided scopes is the
   * correct scope for new names.
   */
  private static StaticScope getCreationScope(StaticScope scope, String name) {
    if (scope != null && scope.getParentScope() != null) {
      String rootName = getRootElementOfName(name);
      StaticSlot slot = scope.getSlot(rootName);
      checkState(
          slot != null || !isCompoundName(name) || rootName.equals("this"), "missing %s", name);
      return slot != null ? slot.getScope() : scope;
    }
    return scope;
  }

  @Nullable
  private Node getRootNodeForScope(StaticScope scope) {
    Node root = scope != null ? scope.getRootNode() : null;
    return (root == null || root.isRoot() || root.isScript()) ? nameTableGlobalRoot : root;
  }

  private boolean isDeclaredForScope(StaticScope scope, String name) {
    return getTypeInternal(scope, name) != null;
  }

  private static void checkTypeName(String typeName) {
    checkArgument(!typeName.contains("<"), "Type names cannot contain template annotations.");
  }

  private JSType getTypeInternal(StaticScope scope, String name) {
    checkTypeName(name);
    if (scope instanceof SyntheticTemplateScope) {
      TemplateType type = ((SyntheticTemplateScope) scope).getTemplateType(name);
      if (type != null) {
        return type;
      }
    }
    return getTypeForScopeInternal(getLookupScope(scope, name), name);
  }

  private JSType getTypeForScopeInternal(StaticScope scope, String name) {
    Node rootNode = getRootNodeForScope(scope);
    JSType type = scopedNameTable.get(rootNode, name);
    return type;
  }

  private void registerGlobalType(JSType type) {
    register(null, type, type.toString());
  }

  private void registerGlobalType(JSType type, String name) {
    register(null, type, name);
  }

  private void reregister(StaticScope scope, JSType type, String name) {
    checkTypeName(name);
    registerForScope(getLookupScope(scope, name), type, name);
  }

  private void register(StaticScope scope, JSType type, String name) {
    checkTypeName(name);
    registerForScope(getCreationScope(scope, name), type, name);
  }

  private void registerForScope(StaticScope scope, JSType type, String name) {
    scopedNameTable.put(getRootNodeForScope(scope), name, type);
  }

  /**
   * Removes a type by name.
   *
   * @param name The name string.
   */
  public void removeType(StaticScope scope, String name) {
    scopedNameTable.remove(getRootNodeForScope(getLookupScope(scope, name)), name);
  }

  private void registerNativeType(JSTypeNative typeId, JSType type) {
    nativeTypes[typeId.ordinal()] = type;
  }

  // When t is an object that is not the prototype of some class,
  // and its nominal type is Object, and it has some properties,
  // we don't need to store these properties in the propertyIndex separately.
  private static boolean isObjectLiteralThatCanBeSkipped(JSType t) {
    t = t.restrictByNotNullOrUndefined();
    return t.isRecordType() || t.isLiteralObject();
  }

  void registerDroppedPropertiesInUnion(RecordType subtype, RecordType supertype) {
    boolean foundDroppedProperty = false;
    for (String pname : subtype.getPropertyMap().getOwnPropertyNames()) {
      if (!supertype.hasProperty(pname)) {
        foundDroppedProperty = true;
        this.droppedPropertiesOfUnions.add(pname);
      }
    }
    if (foundDroppedProperty) {
      this.propertiesOfSupertypesInUnions.addAll(supertype.getPropertyMap().getOwnPropertyNames());
    }
  }

  /**
   * Tells the type system that {@code owner} may have a property named
   * {@code propertyName}. This allows the registry to keep track of what
   * types a property is defined upon.
   *
   * This is NOT the same as saying that {@code owner} must have a property
   * named type. ObjectType#hasProperty attempts to minimize false positives
   * ("if we're not sure, then don't type check this property"). The type
   * registry, on the other hand, should attempt to minimize false negatives
   * ("if this property is assigned anywhere in the program, it must
   * show up in the type registry").
   */
  public void registerPropertyOnType(String propertyName, JSType type) {
    UnionTypeBuilder typeSet =
        typesIndexedByProperty.computeIfAbsent(
            propertyName, k -> UnionTypeBuilder.createForPropertyChecking(this));

    if (isObjectLiteralThatCanBeSkipped(type)) {
      type = getSentinelObjectLiteral();
    }

    typeSet.addAlternate(type);
    addReferenceTypeIndexedByProperty(propertyName, type);

    // Clear cached values that depend on typesIndexedByProperty.
    greatestSubtypeByProperty.remove(propertyName);
  }

  private void addReferenceTypeIndexedByProperty(
      String propertyName, JSType type) {
    if (type instanceof ObjectType && ((ObjectType) type).hasReferenceName()) {
      Map<String, ObjectType> typeSet =
          eachRefTypeIndexedByProperty.computeIfAbsent(propertyName, k -> new LinkedHashMap<>());
      ObjectType objType = (ObjectType) type;
      typeSet.put(objType.getReferenceName(), objType);
    } else if (type instanceof NamedType) {
      addReferenceTypeIndexedByProperty(
          propertyName, ((NamedType) type).getReferencedType());
    } else if (type.isUnionType()) {
      for (JSType alternate : type.toMaybeUnionType().getAlternates()) {
        addReferenceTypeIndexedByProperty(propertyName, alternate);
      }
    }
  }

  /**
   * Removes the index's reference to a property on the given type (if it is
   * currently registered). If the property is not registered on the type yet,
   * this method will not change internal state.
   *
   * @param propertyName the name of the property to unregister
   * @param type the type to unregister the property on.
   */
  public void unregisterPropertyOnType(String propertyName, JSType type) {
    // TODO(bashir): typesIndexedByProperty should also be updated!
    Map<String, ObjectType> typeSet =
        eachRefTypeIndexedByProperty.get(propertyName);
    if (typeSet != null) {
      typeSet.remove(type.toObjectType().getReferenceName());
    }
  }

  /**
   * Gets the greatest subtype of the {@code type} that has a property
   * {@code propertyName} defined on it.
   */
  public JSType getGreatestSubtypeWithProperty(
      JSType type, String propertyName) {
    JSType withProperty = greatestSubtypeByProperty.get(propertyName);
    if (withProperty != null) {
      return withProperty.getGreatestSubtype(type);
    }
    UnionTypeBuilder typesWithProp = typesIndexedByProperty.get(propertyName);
    if (typesWithProp != null) {
      JSType built = typesWithProp.build();
      greatestSubtypeByProperty.put(propertyName, built);
      return built.getGreatestSubtype(type);
    }
    return getNativeType(NO_TYPE);
  }

  /** A tristate value returned from canPropertyBeDefined. */
  public enum PropDefinitionKind {
    UNKNOWN, // The property is not known to be part of this type
    KNOWN,   // The properties is known to be defined on a type or its super types
    LOOSE,   // The property is loosely associated with a type, typically one of its subtypes
    LOOSE_UNION // The property is loosely associated with a union type
  }

  /**
   * Returns whether the given property can possibly be set on the given type.
   */
  public PropDefinitionKind canPropertyBeDefined(JSType type, String propertyName) {
    if (type.isStruct()) {
      // We are stricter about "struct" types and only allow access to
      // properties that to the best of our knowledge are available at creation
      // time and specifically not properties only defined on subtypes.

      switch (type.getPropertyKind(propertyName)) {
        case KNOWN_PRESENT:
          return PropDefinitionKind.KNOWN;
        case MAYBE_PRESENT:
          // TODO(johnlenz): return LOOSE_UNION here.
          return PropDefinitionKind.KNOWN;
        case ABSENT:
          return PropDefinitionKind.UNKNOWN;
      }
    } else {
      if (!type.isEmptyType() && !type.isUnknownType()) {
        switch (type.getPropertyKind(propertyName)) {
          case KNOWN_PRESENT:
            return PropDefinitionKind.KNOWN;
          case MAYBE_PRESENT:
            // TODO(johnlenz): return LOOSE_UNION here.
            return PropDefinitionKind.KNOWN;
          case ABSENT:
            // check for loose properties below.
            break;
        }
      }

      if (typesIndexedByProperty.containsKey(propertyName)) {
        for (JSType alt :
                 typesIndexedByProperty.get(propertyName).getAlternates()) {
          JSType greatestSubtype = alt.getGreatestSubtype(type);
          if (!greatestSubtype.isEmptyType()) {
            // We've found a type with this property. Now we just have to make
            // sure it's not a type used for internal bookkeeping.
            RecordType maybeRecordType = greatestSubtype.toMaybeRecordType();
            if (maybeRecordType != null && maybeRecordType.isSynthetic()) {
              continue;
            }

            return PropDefinitionKind.LOOSE;
          }
        }
      }

      if (type.toMaybeRecordType() != null) {
        RecordType rec = type.toMaybeRecordType();
        boolean mayBeInUnion = false;
        for (String pname : rec.getPropertyMap().getOwnPropertyNames()) {
          if (this.propertiesOfSupertypesInUnions.contains(pname)) {
            mayBeInUnion = true;
            break;
          }
        }

        if (mayBeInUnion && this.droppedPropertiesOfUnions.contains(propertyName)) {
          return PropDefinitionKind.LOOSE;
        }
      }
    }
    return PropDefinitionKind.UNKNOWN;
  }

  /**
   * Returns each reference type that has a property {@code propertyName}
   * defined on it.
   *
   * Unlike most types in our type system, the collection of types returned
   * will not be collapsed. This means that if a type is defined on
   * {@code Object} and on {@code Array}, this method must return
   * {@code [Object, Array]}. It would not be correct to collapse them to
   * {@code [Object]}.
   */
  public Iterable<ObjectType> getEachReferenceTypeWithProperty(
      String propertyName) {
    if (eachRefTypeIndexedByProperty.containsKey(propertyName)) {
      return eachRefTypeIndexedByProperty.get(propertyName).values();
    } else {
      return ImmutableList.of();
    }
  }

  /**
   * Finds the common supertype of the two given object types.
   */
  ObjectType findCommonSuperObject(ObjectType a, ObjectType b) {
    List<ObjectType> stackA = getSuperStack(a);
    List<ObjectType> stackB = getSuperStack(b);

    ObjectType result = getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    while (!stackA.isEmpty() && !stackB.isEmpty()) {
      ObjectType currentA = stackA.remove(stackA.size() - 1);
      ObjectType currentB = stackB.remove(stackB.size() - 1);
      if (currentA.isEquivalentTo(currentB)) {
        result = currentA;
      } else {
        return result;
      }
    }
    return result;
  }

  private static List<ObjectType> getSuperStack(ObjectType a) {
    List<ObjectType> stack = new ArrayList<>(5);
    for (ObjectType current = a;
         current != null;
         current = current.getImplicitPrototype()) {
      stack.add(current);
    }
    return stack;
  }

  /**
   * Tells the type system that {@code type} implements interface {@code
   * interfaceInstance}.
   * {@code inter} must be an ObjectType for the instance of the interface as it
   * could be a named type and not yet have the constructor.
   */
  void registerTypeImplementingInterface(
      FunctionType type, ObjectType interfaceInstance) {
    interfaceToImplementors.put(interfaceInstance.getReferenceName(), type);
  }

  /**
   * Returns a collection of types that directly implement {@code
   * interfaceInstance}.  Subtypes of implementing types are not guaranteed to
   * be returned.  {@code interfaceInstance} must be an ObjectType for the
   * instance of the interface.
   */
  public Collection<FunctionType> getDirectImplementors(ObjectType interfaceInstance) {
    return interfaceToImplementors.get(interfaceInstance.getReferenceName());
  }

  /**
   * Records declared global type names. This makes resolution faster
   * and more robust in the common case.
   *
   * @param name The name of the type to be recorded.
   * @param type The actual type being associated with the name.
   * @return True if this name is not already defined, false otherwise.
   */
  public boolean declareType(StaticScope scope, String name, JSType type) {
    checkState(!name.isEmpty());
    if (getTypeForScopeInternal(getCreationScope(scope, name), name) != null) {
      return false;
    }

    register(scope, type, name);
    return true;
  }

  /**
   * Records declared global type names. This makes resolution faster
   * and more robust in the common case.
   *
   * @param name The name of the type to be recorded.
   * @param type The actual type being associated with the name.
   * @return True if this name is not already defined, false otherwise.
   */
  public boolean declareTypeForExactScope(StaticScope scope, String name, JSType type) {
    checkState(!name.isEmpty());
    if (getTypeForScopeInternal(scope, name) != null) {
      return false;
    }

    registerForScope(scope, type, name);
    return true;
  }

  /**
   * Overrides a declared global type name. Throws an exception if this type name hasn't been
   * declared yet.
   */
  public void overwriteDeclaredType(String name, JSType type) {
    overwriteDeclaredType(null, name, type);
  }

  /**
   * Overrides a declared global type name. Throws an exception if this type name hasn't been
   * declared yet.
   */
  public void overwriteDeclaredType(StaticScope scope, String name, JSType type) {
    checkState(isDeclaredForScope(scope, name), "missing name %s", name);
    reregister(scope, type, name);
  }

  /**
   * Whether this is a forward-declared type name.
   */
  public boolean isForwardDeclaredType(String name) {
    return forwardDeclaredTypes.contains(name);
  }

  /**
   * The nice API for this method is a single argument; dereference is a detail. In the old type
   * checker, most calls to getReadableJSTypeName are with true (do dereferencing).
   * When we implement this method in the new type checker, we won't do dereferencing, but that's
   * fine because we are stricter about null/undefined checking.
   * (So, null and undefined wouldn't be in the type in the first place.)
   */
  public String getReadableTypeName(Node n) {
    return getReadableJSTypeName(n, true);
  }

  public String getReadableTypeNameNoDeref(Node n) {
    return getReadableJSTypeName(n, false);
  }

  public String createGetterPropName(String originalPropName) {
    return originalPropName;
  }

  public String createSetterPropName(String originalPropName) {
    return originalPropName;
  }

  private String getSimpleReadableJSTypeName(JSType type) {
    if (type instanceof AllType) {
      return type.toString();
    } else if (type instanceof ValueType) {
      return type.toString();
    } else if (type.isFunctionPrototypeType()) {
      return type.toString();
    } else if (type instanceof ObjectType) {
      if (type.toObjectType() != null && type.toObjectType().getConstructor() != null) {
        Node source = type.toObjectType().getConstructor().getSource();
        if (source != null) {
          checkState(source.isFunction() || source.isClass(), source);
          String readable = source.getFirstChild().getOriginalName();
          if (readable != null) {
            return readable;
          }
        }
        return type.toString();
      }
      return null;
    } else if (type instanceof UnionType) {
      UnionType unionType = type.toMaybeUnionType();
      String union = null;
      for (JSType alternate : unionType.getAlternates()) {
        String name = getSimpleReadableJSTypeName(alternate);
        if (name == null) {
          return null;
        }
        if (union == null) {
          union = "(" + name;
        } else {
          union += "|" + name;
        }
      }
      union += ")";
      return union;
    }
    return null;
  }

  /**
   * Given a node, get a human-readable name for the type of that node so
   * that will be easy for the programmer to find the original declaration.
   *
   * For example, if SubFoo's property "bar" might have the human-readable
   * name "Foo.prototype.bar".
   *
   * @param n The node.
   * @param dereference If true, the type of the node will be dereferenced
   *     to an Object type, if possible.
   */
  @VisibleForTesting
  String getReadableJSTypeName(Node n, boolean dereference) {
    JSType type = getJSTypeOrUnknown(n);
    if (dereference) {
      JSType autoboxed = type.autobox();
      if (!autoboxed.isNoType()) {
        type = autoboxed;
      }
    }

    String name = getSimpleReadableJSTypeName(type);
    if (name != null) {
      return name;
    }

    // If we're analyzing a GETPROP, the property may be inherited by the
    // prototype chain. So climb the prototype chain and find out where
    // the property was originally defined.
    if (n.isGetProp()) {
      ObjectType objectType = getJSTypeOrUnknown(n.getFirstChild()).dereference();
      if (objectType != null) {
        String propName = n.getLastChild().getString();
        if (objectType.getConstructor() != null
            && objectType.getConstructor().isInterface()) {
          objectType = objectType.getTopDefiningInterface(propName);
        } else {
          // classes
          while (objectType != null && !objectType.hasOwnProperty(propName)) {
            objectType = objectType.getImplicitPrototype();
          }
        }

        // Don't show complex function names or anonymous types.
        // Instead, try to get a human-readable type name.
        if (objectType != null
            && (objectType.getConstructor() != null
                || objectType.isFunctionPrototypeType())) {
          return objectType + "." + propName;
        }
      }
    }

    if (n.isQualifiedName()) {
      return n.getQualifiedName();
    } else if (type.isFunctionType()) {
      // Don't show complex function names.
      return "function";
    } else {
      return type.toString();
    }
  }

  private JSType getJSTypeOrUnknown(Node n) {
    JSType jsType = n.getJSType();
    if (jsType == null) {
      return getNativeType(UNKNOWN_TYPE);
    } else {
      return jsType;
    }
  }

  /**
   * Looks up a native type by name.
   *
   * @param jsTypeName The name string.
   * @return the corresponding JSType object or {@code null} it cannot be found
   */
  public JSType getTypeForScope(StaticScope scope, String jsTypeName) {
    return getTypeForScopeInternal(scope, jsTypeName);
  }

  public JSType getGlobalType(String jsTypeName) {
    return getType(null, jsTypeName);
  }

  /**
   * Looks up a native type by name.
   *
   * @param jsTypeName The name string.
   * @return the corresponding JSType object or {@code null} it cannot be found
   */
  public JSType getType(StaticScope scope, String jsTypeName) {
    return getTypeInternal(scope, jsTypeName);
  }

  /**
   * Looks up a type by name. To allow for forward references to types, an unrecognized string has
   * to be bound to a NamedType object that will be resolved later.
   *
   * @param scope A scope for doing type name resolution.
   * @param jsTypeName The name string.
   * @param sourceName The name of the source file where this reference appears.
   * @param lineno The line number of the reference.
   * @return a NamedType if the string argument is not one of the known types, otherwise the
   *     corresponding JSType object.
   */
  public JSType getType(
      StaticTypedScope scope, String jsTypeName, String sourceName, int lineno, int charno) {
    return getType(scope, jsTypeName, sourceName, lineno, charno, true);
  }

  /**
   * @param recordUnresolvedTypes record unresolved named types and resolve them later. Set to false
   *     if types should be ignored for backwards compatibility (i.e. previously unparsed template
   *     type args).
   */
  private JSType getType(
      StaticTypedScope scope,
      String jsTypeName,
      String sourceName,
      int lineno,
      int charno,
      boolean recordUnresolvedTypes) {
    switch (jsTypeName) {
      case "boolean":
        return getNativeType(JSTypeNative.BOOLEAN_TYPE);
      case "number":
        return getNativeType(JSTypeNative.NUMBER_TYPE);
      case "string":
        return getNativeType(JSTypeNative.STRING_TYPE);
      case "undefined":
      case "void":
        return getNativeType(JSTypeNative.VOID_TYPE);
    }
    // Resolve template type names
    JSType type = null;
    JSType thisType = null;
    if (scope != null && scope.getTypeOfThis() != null) {
      thisType = scope.getTypeOfThis().toObjectType();
    }
    if (thisType != null) {
      type = thisType.getTemplateTypeMap().getTemplateTypeKeyByName(jsTypeName);
      if (type != null) {
        Preconditions.checkState(type.isTemplateType(), "expected:%s", type);
        return type;
      }
    }

    // TODO(sdh): The use of "getType" here is incorrect. This currently will pick up a type
    // in an outer scope if it will be shadowed by a local type.  But creating a unique NamedType
    // object for every name referenced (even if interned) in every scope would be expensive.
    //
    // Instead perhaps a standard (untyped) scope object might be used to pick the right scope
    // during type construction.
    type = getType(scope, jsTypeName);
    if (type == null) {
      // TODO(user): Each instance should support named type creation using
      // interning.
      NamedType namedType = createNamedType(scope, jsTypeName, sourceName, lineno, charno);
      if (recordUnresolvedTypes) {
        unresolvedNamedTypes.add(namedType);
      }
      type = namedType;
    }
    return type;
  }

  public JSType getNativeType(JSTypeNative typeId) {
    return nativeTypes[typeId.ordinal()];
  }

  public ObjectType getNativeObjectType(JSTypeNative typeId) {
    return (ObjectType) getNativeType(typeId);
  }

  public FunctionType getNativeFunctionType(JSTypeNative typeId) {
    return (FunctionType) getNativeType(typeId);
  }

  /**
   * Flushes out the current resolved and unresolved Named Types from
   * the type registry.  This is intended to be used ONLY before a
   * compile is run.
   */
  public void clearNamedTypes() {
    unresolvedNamedTypes.clear();
  }

  /** Resolve all the unresolved types in the given scope. */
  public void resolveTypes() {
    for (NamedType type : unresolvedNamedTypes) {
      type.resolve(reporter);
    }

    unresolvedNamedTypes.clear();

    // By default, the global "this" type is just an anonymous object.
    // If the user has defined a Window type, make the Window the
    // implicit prototype of "this".
    PrototypeObjectType globalThis = (PrototypeObjectType) getNativeType(
        JSTypeNative.GLOBAL_THIS);
    JSType windowType = getTypeInternal(null, "Window");
    if (globalThis.isUnknownType()) {
      ObjectType windowObjType = ObjectType.cast(windowType);
      if (windowObjType != null) {
        globalThis.setImplicitPrototype(windowObjType);
      } else {
        globalThis.setImplicitPrototype(
            getNativeObjectType(JSTypeNative.OBJECT_TYPE));
      }
    }
  }

  public JSType evaluateTypeExpressionInGlobalScope(JSTypeExpression expr) {
    return expr.evaluate(null, this);
  }

  /**
   * Creates a type representing optional values of the given type.
   * @return the union of the type and the void type
   */
  public JSType createOptionalType(JSType type) {
    if (type instanceof UnknownType || type.isAllType()) {
      return type;
    } else {
      return createUnionType(type, getNativeType(JSTypeNative.VOID_TYPE));
    }
  }

  /**
   * Creates a type representing nullable values of the given type.
   * @return the union of the type and the Null type
   */
  public JSType createDefaultObjectUnion(JSType type) {
    if (type.isTemplateType()) {
      // Template types represent the substituted type exactly and should
      // not be wrapped.
      return type;
    } else {
      return createNullableType(type);
    }
  }

  /**
   * Creates a type representing nullable values of the given type.
   * @return the union of the type and the Null type
   */
  public JSType createNullableType(JSType type) {
    return createUnionType(type, getNativeType(JSTypeNative.NULL_TYPE));
  }

  /**
   * Creates a nullable and undefine-able value of the given type.
   * @return The union of the type and null and undefined.
   */
  public JSType createOptionalNullableType(JSType type) {
    return createUnionType(type, getNativeType(JSTypeNative.VOID_TYPE),
        getNativeType(JSTypeNative.NULL_TYPE));
  }

  /**
   * Creates a union type whose variants are the arguments.
   */
  public JSType createUnionType(JSType... variants) {
    UnionTypeBuilder builder = UnionTypeBuilder.create(this);
    for (JSType type : variants) {
      builder.addAlternate(type);
    }
    return builder.build();
  }

  public JSType createUnionType(List<? extends JSType> variants) {
    return createUnionType(variants.toArray(new JSType[0]));
  }

  /**
   * Creates a union type whose variants are the built-in types specified
   * by the arguments.
   */
  public JSType createUnionType(JSTypeNative... variants) {
    UnionTypeBuilder builder = UnionTypeBuilder.create(this);
    for (JSTypeNative typeId : variants) {
      builder.addAlternate(getNativeType(typeId));
    }
    return builder.build();
  }

  /**
   * Creates an enum type.
   */
  public EnumType createEnumType(
      String name, Node source, JSType elementsType) {
    return new EnumType(this, name, source, elementsType);
  }

  /**
   * Creates an arrow type, an abstract representation of the parameters
   * and return value of a function.
   *
   * @param parametersNode the parameters' types, formatted as a Node with
   *     param names and optionality info.
   * @param returnType the function's return type
   */
  ArrowType createArrowType(Node parametersNode, JSType returnType) {
    return new ArrowType(this, parametersNode, returnType);
  }

  /**
   * Creates an arrow type with an unknown return type.
   *
   * @param parametersNode the parameters' types, formatted as a Node with
   *     param names and optionality info.
   */
  ArrowType createArrowType(Node parametersNode) {
    return new ArrowType(this, parametersNode, null);
  }

  /** Creates an arrow type with no parameters and an unknown return type. */
  ArrowType createArrowType() {
    return new ArrowType(this, createEmptyParams(), null);
  }

  /** Creates an empty parameter list node. */
  Node createEmptyParams() {
    return new Node(Token.PARAM_LIST);
  }

  /**
   * Creates a function type.
   *
   * @param returnType the function's return type
   * @param parameterTypes the parameters' types
   */
  public FunctionType createFunctionType(
      JSType returnType, JSType... parameterTypes) {
    return createFunctionType(returnType, createParameters(parameterTypes));
  }

  /**
   * @param parameters the function's parameters or {@code null} to indicate that the parameter
   *     types are unknown.
   * @param returnType the function's return type or {@code null} to indicate that the return type
   *     is unknown.
   */
  public FunctionType createFunctionType(JSType returnType, Node parameters) {
    return new FunctionBuilder(this).withParamsNode(parameters).withReturnType(returnType).build();
  }

  /**
   * Creates a function type. The last parameter type of the function is
   * considered a variable length argument.
   *
   * @param returnType the function's return type
   * @param parameterTypes the parameters' types
   */
  public FunctionType createFunctionTypeWithVarArgs(
      JSType returnType, JSType... parameterTypes) {
    return createFunctionType(
        returnType, createParametersWithVarArgs(parameterTypes));
  }

  /**
   * Creates a function type. The last parameter type of the function is
   * considered a variable length argument.
   *
   * @param returnType the function's return type
   * @param parameterTypes the parameters' types
   */
  private FunctionType createNativeFunctionTypeWithVarArgs(
      JSType returnType, JSType... parameterTypes) {
    return createNativeFunctionType(
        returnType, createParametersWithVarArgs(parameterTypes));
  }

  /**
   * Creates a function type in which {@code this} refers to an object instance.
   *
   * @param instanceType the type of {@code this}
   * @param returnType the function's return type
   * @param parameterTypes the parameters' types
   */
  public JSType createFunctionTypeWithInstanceType(ObjectType instanceType,
      JSType returnType, List<JSType> parameterTypes) {
    Node paramsNode = createParameters(parameterTypes.toArray(new JSType[parameterTypes.size()]));
    return new FunctionBuilder(this)
        .withParamsNode(paramsNode)
        .withReturnType(returnType)
        .withTypeOfThis(instanceType)
        .build();
  }

  /**
   * Creates a tree hierarchy representing a typed argument list.
   *
   * @param parameterTypes the parameter types.
   * @return a tree hierarchy representing a typed argument list.
   */
  public Node createParameters(JSType... parameterTypes) {
    return createParameters(false, parameterTypes);
  }

  /**
   * Creates a tree hierarchy representing a typed argument list.
   *
   * @param lastVarArgs whether the last type should considered as a variable length argument.
   * @param parameterTypes the parameter types. The last element of this array is considered a
   *     variable length argument is {@code lastVarArgs} is {@code true}.
   * @return a tree hierarchy representing a typed argument list
   */
  private Node createParameters(boolean lastVarArgs, JSType... parameterTypes) {
    FunctionParamBuilder builder = new FunctionParamBuilder(this);
    int max = parameterTypes.length - 1;
    for (int i = 0; i <= max; i++) {
      if (lastVarArgs && i == max) {
        builder.addVarArgs(parameterTypes[i]);
      } else {
        builder.addRequiredParams(parameterTypes[i]);
      }
    }
    return builder.build();
  }

  /**
   * Creates a tree hierarchy representing a typed argument list. The last
   * parameter type is considered a variable length argument.
   *
   * @param parameterTypes the parameter types. The last element of this array
   *     is considered a variable length argument.
   * @return a tree hierarchy representing a typed argument list.
   */
  public Node createParametersWithVarArgs(JSType... parameterTypes) {
    return createParameters(true, parameterTypes);
  }

  /**
   * Creates a tree hierarchy representing a typed parameter list in which
   * every parameter is optional.
   */
  public Node createOptionalParameters(JSType... parameterTypes) {
    FunctionParamBuilder builder = new FunctionParamBuilder(this);
    builder.addOptionalParams(parameterTypes);
    return builder.build();
  }

  /**
   * Creates a new function type based on an existing function type but
   * with a new return type.
   * @param existingFunctionType the existing function type.
   * @param returnType the new return type.
   */
  public FunctionType createFunctionTypeWithNewReturnType(
      FunctionType existingFunctionType, JSType returnType) {
    return new FunctionBuilder(this)
        .copyFromOtherFunction(existingFunctionType)
        .withReturnType(returnType)
        .build();
  }

  private FunctionType createNativeFunctionType(
      JSType returnType, Node parameters) {
    return new FunctionBuilder(this)
        .withParamsNode(parameters)
        .withReturnType(returnType)
        .forNativeType()
        .build();
  }

  public JSType buildRecordTypeFromObject(ObjectType objType) {
    RecordType recType = objType.toMaybeRecordType();
    // If it can be casted to a record type then return
    if (recType != null) {
      return recType;
    }
    // TODO(lpino): Handle inherited properties
    Set<String> propNames = objType.getOwnPropertyNames();
    // If the type has no properties then return Object
    if (propNames.isEmpty()) {
      return getNativeType(JSTypeNative.OBJECT_TYPE);
    }
    ImmutableMap.Builder<String, JSType> props = new ImmutableMap.Builder<>();
    // Otherwise collect the properties and build a record type
    for (String propName : propNames) {
      props.put(propName, objType.getPropertyType(propName));
    }
    return createRecordType(props.build());
  }

  public JSType createRecordType(Map<String, ? extends JSType> props) {
    @SuppressWarnings("unchecked")
    Map<String, JSType> propMap = (Map<String, JSType>) props;
    RecordTypeBuilder builder = new RecordTypeBuilder(this);
    for (Entry<String, JSType> e : propMap.entrySet()) {
      builder.addProperty(e.getKey(), e.getValue(), null);
    }
    return builder.build();
  }

  /**
   * Create an object type.
   */
  public ObjectType createObjectType(String name, ObjectType implicitPrototype) {
    return new PrototypeObjectType(this, name, implicitPrototype);
  }

  /**
   * Create an anonymous object type.
   * @param info Used to mark object literals as structs; can be {@code null}
   */
  public ObjectType createAnonymousObjectType(JSDocInfo info) {
    PrototypeObjectType type = new PrototypeObjectType(
        this, null, null, true /* anonymousType */);
    type.setPrettyPrint(true);
    type.setJSDocInfo(info);
    return type;
  }

  /**
   * Set the implicit prototype if it's possible to do so.
   * There are a few different reasons why this could be a no-op: for example,
   * numbers can't be implicit prototypes, and we don't want to change the implicit prototype
   * if other classes have already subclassed this one.
   */
  public void resetImplicitPrototype(JSType type, ObjectType newImplicitProto) {
    if (type instanceof PrototypeObjectType) {
      PrototypeObjectType poType = (PrototypeObjectType) type;
      poType.clearCachedValues();
      poType.setImplicitPrototype(newImplicitProto);
    }
  }

  /**
   * Creates a constructor function type.
   *
   * @param name the function's name or {@code null} to indicate that the function is anonymous.
   * @param source the node defining this function. Its type ({@link Node#getToken()} ()}) must be
   *     {@link Token#FUNCTION}.
   * @param parameters the function's parameters or {@code null} to indicate that the parameter
   *     types are unknown.
   * @param returnType the function's return type or {@code null} to indicate that the return type
   *     is unknown.
   * @param templateKeys the templatized types for the class.
   * @param isAbstract whether the function type represents an abstract class
   */
  public FunctionType createConstructorType(
      String name,
      Node source,
      Node parameters,
      JSType returnType,
      ImmutableList<TemplateType> templateKeys,
      boolean isAbstract) {
    checkArgument(source == null || source.isFunction() || source.isClass());
    return new FunctionBuilder(this)
        .forConstructor()
        .withName(name)
        .withSourceNode(source)
        .withParamsNode(parameters)
        .withReturnType(returnType)
        .withTemplateKeys(templateKeys)
        .withIsAbstract(isAbstract)
        .build();
  }

  /**
   * Creates an interface function type.
   *
   * @param name the function's name
   * @param source the node defining this function. Its type ({@link Node#getToken()}) must be
   *     {@link Token#FUNCTION}.
   * @param templateKeys the templatized types for the interface.
   */
  public FunctionType createInterfaceType(
      String name, Node source, ImmutableList<TemplateType> templateKeys, boolean struct) {
    FunctionType fn = new FunctionBuilder(this)
        .forInterface()
        .withName(name)
        .withSourceNode(source)
        .withEmptyParams()
        .withTemplateKeys(templateKeys)
        .build();
    if (struct) {
      fn.setStruct();
    }
    return fn;
  }

  public TemplateType createTemplateType(String name) {
    return new TemplateType(this, name);
  }

  public TemplateType createTemplateTypeWithTransformation(
      String name, Node expr) {
    return new TemplateType(this, name, expr);
  }

  /**
   * Creates a template type map from the specified list of template keys and
   * template value types.
   */
  public TemplateTypeMap createTemplateTypeMap(
      ImmutableList<TemplateType> templateKeys,
      ImmutableList<JSType> templateValues) {
    if (templateKeys == null) {
      templateKeys = ImmutableList.of();
    }
    if (templateValues == null) {
      templateValues = ImmutableList.of();
    }
    return (templateKeys.isEmpty() && templateValues.isEmpty())
        ? emptyTemplateTypeMap
            : new TemplateTypeMap(this, templateKeys, templateValues);
  }

  public ObjectType instantiateGenericsWithUnknown(ObjectType obj) {
    if (obj.isTemplatizedType()) {
      ImmutableList.Builder<JSType> unknowns = ImmutableList.builder();
      for (TemplateType unused : obj.getTemplateTypeMap().getTemplateKeys()) {
        unknowns.add(getNativeType(UNKNOWN_TYPE));
      }
      return createTemplatizedType(obj.toMaybeTemplatizedType().getRawType(), unknowns.build());
    }
    return obj;
  }

  public JSType instantiateGenericType(
      ObjectType genericType, ImmutableList<JSType> typeArgs) {
    return createTemplatizedType(genericType, typeArgs);
  }

  /**
   * Creates a templatized instance of the specified type.  Only ObjectTypes
   * can currently be templatized; extend the logic in this function when
   * more types can be templatized.
   * @param baseType the type to be templatized.
   * @param templatizedTypes a list of the template JSTypes. Will be matched by
   *     list order to the template keys on the base type.
   */
  public TemplatizedType createTemplatizedType(
      ObjectType baseType, ImmutableList<JSType> templatizedTypes) {
    // Only ObjectTypes can currently be templatized; extend this logic when
    // more types can be templatized.
    return new TemplatizedType(this, baseType, templatizedTypes);
  }

  /**
   * Creates a templatized instance of the specified type.  Only ObjectTypes
   * can currently be templatized; extend the logic in this function when
   * more types can be templatized.
   * @param baseType the type to be templatized.
   * @param templatizedTypes a map from TemplateType to corresponding JSType
   *     value. Any unfilled TemplateTypes on the baseType that are *not*
   *     contained in this map will have UNKNOWN_TYPE used as their value.
   */
  public TemplatizedType createTemplatizedType(
      ObjectType baseType, Map<TemplateType, JSType> templatizedTypes) {
    ImmutableList.Builder<JSType> builder = ImmutableList.builder();
    TemplateTypeMap baseTemplateTypeMap = baseType.getTemplateTypeMap();
    for (TemplateType key : baseTemplateTypeMap.getUnfilledTemplateKeys()) {
      JSType templatizedType = templatizedTypes.containsKey(key) ?
          templatizedTypes.get(key) : getNativeType(UNKNOWN_TYPE);
      builder.add(templatizedType);
    }
    return createTemplatizedType(baseType, builder.build());
  }

  /**
   * Creates a templatized instance of the specified type.  Only ObjectTypes
   * can currently be templatized; extend the logic in this function when
   * more types can be templatized.
   * @param baseType the type to be templatized.
   * @param templatizedTypes a list of the template JSTypes. Will be matched by
   *     list order to the template keys on the base type.
   */
  public TemplatizedType createTemplatizedType(ObjectType baseType, JSType... templatizedTypes) {
    return createTemplatizedType(baseType, ImmutableList.copyOf(templatizedTypes));
  }

  /** Creates a named type. */
  @VisibleForTesting
  public NamedType createNamedType(
      StaticTypedScope scope, String reference, String sourceName, int lineno, int charno) {
    return new NamedType(scope, this, reference, sourceName, lineno, charno);
  }

  /**
   * Identifies the name of a typedef or enum before we actually declare it.
   */
  public void identifyNonNullableName(String name) {
    checkNotNull(name);
    nonNullableTypeNames.add(name);
  }

  public JSType evaluateTypeExpression(JSTypeExpression expr, StaticTypedScope scope) {
    return createTypeFromCommentNode(expr.getRoot(), expr.getSourceName(), scope);
  }

  public JSType createTypeFromCommentNode(Node n) {
    return createTypeFromCommentNode(n, "[internal]", null);
  }

  /**
   * Creates a JSType from the nodes representing a type.
   *
   * @param n The node with type info.
   * @param sourceName The source file name.
   * @param scope A scope for doing type name lookups.
   */
  @SuppressWarnings("unchecked")
  public JSType createTypeFromCommentNode(Node n, String sourceName, StaticTypedScope scope) {
    return createFromTypeNodesInternal(n, sourceName, scope, true);
  }

  private JSType createFromTypeNodesInternal(
      Node n, String sourceName, StaticTypedScope scope, boolean recordUnresolvedTypes) {
    switch (n.getToken()) {
      case LC: // Record type.
        return createRecordTypeFromNodes(
            n.getFirstChild(), sourceName, scope);

      case BANG: // Not nullable
        return createFromTypeNodesInternal(
            n.getFirstChild(), sourceName, scope, recordUnresolvedTypes)
            .restrictByNotNullOrUndefined();

      case QMARK: // Nullable or unknown
        Node firstChild = n.getFirstChild();
        if (firstChild == null) {
          return getNativeType(UNKNOWN_TYPE);
        }
        return createNullableType(
            createFromTypeNodesInternal(
                firstChild, sourceName, scope, recordUnresolvedTypes));

      case EQUALS: // Optional
        // TODO(b/117162687): stop automatically converting {string=} to {(string|undefined)]}
        return createOptionalType(
            createFromTypeNodesInternal(
                n.getFirstChild(), sourceName, scope, recordUnresolvedTypes));

      case ELLIPSIS: // Var args
        return createFromTypeNodesInternal(
            n.getFirstChild(), sourceName, scope, recordUnresolvedTypes);

      case STAR: // The AllType
        return getNativeType(ALL_TYPE);

      case PIPE: // Union type
        UnionTypeBuilder builder = UnionTypeBuilder.create(this);
        for (Node child = n.getFirstChild(); child != null;
             child = child.getNext()) {
          builder.addAlternate(
              createFromTypeNodesInternal(child, sourceName, scope, recordUnresolvedTypes));
        }
        return builder.build();

      case EMPTY: // When the return value of a function is not specified
        return getNativeType(UNKNOWN_TYPE);

      case VOID: // Only allowed in the return value of a function.
        return getNativeType(VOID_TYPE);

      case TYPEOF:
        {
          String name = n.getFirstChild().getString();
          StaticTypedSlot slot = scope.getSlot(name);
          if (slot == null) {
            reporter.warning("Not in scope: " + name, sourceName, n.getLineno(), n.getCharno());
            return getNativeType(UNKNOWN_TYPE);
          }
          // TODO(sdh): require var to be const?
          JSType type = slot.getType();
          if (type == null) {
            reporter.warning("No type for: " + name, sourceName, n.getLineno(), n.getCharno());
            return getNativeType(UNKNOWN_TYPE);
          }
          if (type.isLiteralObject()) {
            type =
                createNamedType(scope, "typeof " + name, sourceName, n.getLineno(), n.getCharno());
            ((NamedType) type).setReferencedType(slot.getType());
          }
          return type;
        }

      case STRING:
        // TODO(martinprobst): The new type syntax resolution should be separate.
        // Remove the NAME case then.
      case NAME:
        JSType namedType =
            getType(
                scope,
                n.getString(),
                sourceName,
                n.getLineno(),
                n.getCharno(),
                recordUnresolvedTypes);
        if (namedType instanceof ObjectType && !nonNullableTypeNames.contains(n.getString())) {
          Node typeList = n.getFirstChild();
          boolean isForwardDeclared = namedType instanceof NamedType;
          if ((!namedType.isUnknownType() || isForwardDeclared) && typeList != null) {
            // Templatized types.
            ImmutableList.Builder<JSType> templateTypes = ImmutableList.builder();

            // Special case for Object, where Object<X> implies Object<?,X>.
            if ((n.getString().equals("Object") || n.getString().equals("window.Object"))
                && typeList.hasZeroOrOneChild()) {
              templateTypes.add(getNativeType(UNKNOWN_TYPE));
            }

            int nAllowedTypes =
                isForwardDeclared
                    ? Integer.MAX_VALUE
                    : namedType.getTemplateTypeMap().numUnfilledTemplateKeys();
            boolean recordTemplateArgs = recordUnresolvedTypes && !isForwardDeclared;
            int templateNodeIndex = 0;
            for (Node templateNode : typeList.children()) {
              // Don't parse more templatized type nodes than the type can
              // accommodate. This is because some existing clients have
              // template annotations on non-templatized classes, for instance:
              //   goog.structs.Set<SomeType>
              // The problem in these cases is that the previously-unparsed
              // SomeType is not actually a valid type. To prevent these clients
              // from seeing unknown type errors, we explicitly don't parse
              // these types.
              // TODO(dimvar): Address this issue by removing bad template
              // annotations on non-templatized classes.
              if (++templateNodeIndex > nAllowedTypes) {
                reporter.warning(
                    "Too many template parameters",
                    sourceName,
                    templateNode.getLineno(),
                    templateNode.getCharno());
                break;
              }
              templateTypes.add(
                  createFromTypeNodesInternal(templateNode, sourceName, scope, recordTemplateArgs));
            }
            if (isForwardDeclared) {
              namedType =
                  new NamedType(
                      scope,
                      this,
                      n.getString(),
                      sourceName,
                      n.getLineno(),
                      n.getCharno(),
                      templateTypes.build());
            } else {
              namedType = createTemplatizedType((ObjectType) namedType, templateTypes.build());
            }
            checkNotNull(namedType);
          }
          return createDefaultObjectUnion(namedType);
        } else {
          return namedType;
        }

      case FUNCTION:
        JSType thisType = null;
        boolean isConstructor = false;
        Node current = n.getFirstChild();
        if (current.isThis() || current.isNew()) {
          Node contextNode = current.getFirstChild();

          JSType candidateThisType =
              createFromTypeNodesInternal(contextNode, sourceName, scope, recordUnresolvedTypes);

          // Allow null/undefined 'this' types to indicate that
          // the function is not called in a deliberate context,
          // and 'this' access should raise warnings.
          if (candidateThisType.isNullType() || candidateThisType.isVoidType()) {
            thisType = candidateThisType;
          } else if (current.isThis()) {
            thisType = candidateThisType.restrictByNotNullOrUndefined();
          } else if (current.isNew()) {
            thisType = ObjectType.cast(candidateThisType.restrictByNotNullOrUndefined());
            if (thisType == null) {
              reporter.warning(
                  SimpleErrorReporter.getMessage0("msg.jsdoc.function.newnotobject"),
                  sourceName,
                  contextNode.getLineno(),
                  contextNode.getCharno());
            }
          }

          isConstructor = current.getToken() == Token.NEW;
          current = current.getNext();
        }

        FunctionParamBuilder paramBuilder = new FunctionParamBuilder(this);

        if (current.getToken() == Token.PARAM_LIST) {
          for (Node arg = current.getFirstChild(); arg != null; arg = arg.getNext()) {
            if (arg.getToken() == Token.ELLIPSIS) {
              if (!arg.hasChildren()) {
                paramBuilder.addVarArgs(getNativeType(UNKNOWN_TYPE));
              } else {
                paramBuilder.addVarArgs(
                    createFromTypeNodesInternal(
                        arg.getFirstChild(), sourceName, scope, recordUnresolvedTypes));
              }
            } else {
              JSType type =
                  createFromTypeNodesInternal(arg, sourceName, scope, recordUnresolvedTypes);
              if (arg.getToken() == Token.EQUALS) {
                boolean addSuccess = paramBuilder.addOptionalParams(type);
                if (!addSuccess) {
                  reporter.warning(
                      SimpleErrorReporter.getMessage0("msg.jsdoc.function.varargs"),
                      sourceName,
                      arg.getLineno(),
                      arg.getCharno());
                }
              } else {
                paramBuilder.addRequiredParams(type);
              }
            }
          }
          current = current.getNext();
        }

        JSType returnType =
            createFromTypeNodesInternal(current, sourceName, scope, recordUnresolvedTypes);

        return new FunctionBuilder(this)
            .withParamsNode(paramBuilder.build())
            .withReturnType(returnType)
            .withTypeOfThis(thisType)
            .withKind(isConstructor ? FunctionType.Kind.CONSTRUCTOR : FunctionType.Kind.ORDINARY)
            .build();

      default:
        break;
    }

    throw new IllegalStateException("Unexpected node in type expression: " + n);
  }

  /**
   * Creates a RecordType from the nodes representing said record type.
   *
   * @param n The node with type info.
   * @param sourceName The source file name.
   * @param scope A scope for doing type name lookups.
   */
  private JSType createRecordTypeFromNodes(Node n, String sourceName, StaticTypedScope scope) {

    RecordTypeBuilder builder = new RecordTypeBuilder(this);

    // For each of the fields in the record type.
    for (Node fieldTypeNode = n.getFirstChild();
         fieldTypeNode != null;
         fieldTypeNode = fieldTypeNode.getNext()) {

      // Get the property's name.
      Node fieldNameNode = fieldTypeNode;
      boolean hasType = false;

      if (fieldTypeNode.getToken() == Token.COLON) {
        fieldNameNode = fieldTypeNode.getFirstChild();
        hasType = true;
      }

      String fieldName = fieldNameNode.getString();

      // TODO(user): Move this into the lexer/parser.
      // Remove the string literal characters around a field name,
      // if any.
      if (fieldName.startsWith("'") || fieldName.startsWith("\"")) {
        fieldName = fieldName.substring(1, fieldName.length() - 1);
      }

      // Get the property's type.
      JSType fieldType = null;

      if (hasType) {
        // We have a declared type.
        fieldType = createFromTypeNodesInternal(
            fieldTypeNode.getLastChild(), sourceName, scope, true);
      } else {
        // Otherwise, the type is UNKNOWN.
        fieldType = getNativeType(JSTypeNative.UNKNOWN_TYPE);
      }

      builder.addProperty(fieldName, fieldType, fieldNameNode);
    }

    return builder.build();
  }

  /**
   * Registers template types on the given scope root. This takes a Node rather than a
   * StaticScope because at the time it is called, the scope has not yet been created.
   */
  public void registerTemplateTypeNamesInScope(Iterable<TemplateType> keys, Node scopeRoot) {
    for (TemplateType key : keys) {
      scopedNameTable.put(scopeRoot, key.getReferenceName(), key);
    }
  }

  /**
   * Returns a new scope that includes the given template names for type resolution
   * purposes.
   */
  public StaticTypedScope createScopeWithTemplates(
      StaticTypedScope scope, Iterable<TemplateType> templates) {
    return new SyntheticTemplateScope(scope, templates);
  }

  /**
   * Synthetic scope that includes template names. This is necessary for resolving
   * template names outside the body of templated functions (e.g. when evaluating
   * JSDoc on things assigned to a prototype, or the parameter or return types of
   * an annotated function), since there is not yet (and may never be) any real
   * scope to attach the types to.
   */
  private static class SyntheticTemplateScope implements StaticTypedScope, Serializable {
    final StaticTypedScope delegate;
    final PMap<String, TemplateType> types;

    SyntheticTemplateScope(StaticTypedScope delegate, Iterable<TemplateType> templates) {
      this.delegate = delegate;
      PMap<String, TemplateType> types =
          delegate instanceof SyntheticTemplateScope
              ? ((SyntheticTemplateScope) delegate).types
              : HamtPMap.<String, TemplateType>empty();
      for (TemplateType key : templates) {
        types = types.plus(key.getReferenceName(), key);
      }
      this.types = types;
    }

    @Override
    public Node getRootNode() {
      return delegate.getRootNode();
    }

    @Override
    public StaticTypedScope getParentScope() {
      return delegate.getParentScope();
    }

    @Override
    public StaticTypedSlot getSlot(String name) {
      return delegate.getSlot(name);
    }

    @Override
    public StaticTypedSlot getOwnSlot(String name) {
      return delegate.getOwnSlot(name);
    }

    @Override
    public JSType getTypeOfThis() {
      return delegate.getTypeOfThis();
    }

    @Nullable
    TemplateType getTemplateType(String name) {
      return types.get(name);
    }
  }

  /**
   * Saves the derived state.
   *
   * Note: This should be only used when serializing the compiler state and needs to be done at the
   * end, after serializing CompilerState.
   */
  @SuppressWarnings("unchecked")
  @GwtIncompatible("ObjectOutputStream")
  public void saveContents(ObjectOutputStream out) throws IOException {
    out.writeObject(eachRefTypeIndexedByProperty);
    out.writeObject(interfaceToImplementors);
  }

  /**
   * Restores the derived state.
   *
   * Note: This should be only used when deserializing the compiler state and needs to be done at
   * the end, after deserializing CompilerState.
   */
  @SuppressWarnings("unchecked")
  @GwtIncompatible("ObjectInputStream")
  public void restoreContents(ObjectInputStream in) throws IOException, ClassNotFoundException {
    eachRefTypeIndexedByProperty = (Map<String, Map<String, ObjectType>>) in.readObject();
    interfaceToImplementors = (Multimap<String, FunctionType>) in.readObject();
  }

  private FunctionBuilder nativeConstructorBuilder(String name) {
    return new FunctionBuilder(this)
        .forNativeType()
        .forConstructor()
        .withName(name);
  }

  private FunctionType nativeInterface(String name, TemplateType... templateKeys) {
    FunctionBuilder builder = new FunctionBuilder(this)
        .forNativeType()
        .forInterface()
        .withName(name);
    if (templateKeys.length > 0) {
      builder.withTemplateKeys(templateKeys);
    }
    return builder.build();
  }
}
