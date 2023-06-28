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
import static com.google.javascript.rhino.jstype.JSTypeIterations.mapTypes;
import static com.google.javascript.rhino.jstype.JSTypeNative.ALL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BIGINT_NUMBER;
import static com.google.javascript.rhino.jstype.JSTypeNative.BIGINT_NUMBER_OBJECT;
import static com.google.javascript.rhino.jstype.JSTypeNative.BIGINT_NUMBER_STRING;
import static com.google.javascript.rhino.jstype.JSTypeNative.BIGINT_NUMBER_STRING_OBJECT;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Table;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.HamtPMap;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Msg;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.PMap;
import com.google.javascript.rhino.QualifiedName;
import com.google.javascript.rhino.StaticScope;
import com.google.javascript.rhino.StaticSlot;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.NamedType.ResolutionKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.jspecify.nullness.Nullable;

/**
 * The type registry is used to resolve named types.
 *
 * <p>This class is not thread-safe.
 */
public final class JSTypeRegistry {
  private static final Splitter DOT_SPLITTER = Splitter.on('.');

  /**
   * The template variable in {@code IObject<IOBJECT_KEY, IOBJECT_VALUE>} (plus the builtin
   * Javascript Object).
   */
  private TemplateType iObjectIndexTemplateKey;

  /**
   * The template variable in {@code IObject<IOBJECT_KEY, IOBJECT_VALUE>} (plus the builtin
   * Javascript Object).
   */
  private TemplateType iObjectElementTemplateKey;

  private static final String I_OBJECT_ELEMENT_TEMPLATE = "IOBJECT_VALUE";

  /** The template variable corresponding to the VALUE type in {@code Iterable<VALUE>} */
  private TemplateType iterableTemplate;

  /** The template variable corresponding to the VALUE type in {@code IteratorIterable<T>} */
  private TemplateType iteratorIterableTemplateKey;

  /**
   * The template variable corresponding to the VALUE type in {@code Iterator<VALUE,
   * UNUSED_RETURN_T, UNUSED_NEXT_T>}
   */
  private TemplateType iteratorValueTemplate;

  /** The template variable corresponding to the VALUE type in {@code IIterableResult<VALUE>} */
  private TemplateType iiterableResultTemplate;

  /** The template variable corresponding to the VALUE type in {@code AsyncIterable<VALUE>} */
  private TemplateType asyncIterableTemplate;

  /** The template variable corresponding to the VALUE type in {@code AsyncIterator<VALUE>} */
  private TemplateType asyncIteratorValueTemplate;

  /**
   * The template variable corresponding to the VALUE type in {@code Generator<VALUE,
   * UNUSED_RETURN_T, UNUSED_NEXT_T>}
   */
  private TemplateType generatorValueTemplate;

  /**
   * The template variable corresponding to the VALUE type in {@code AsyncGenerator<VALUE,
   * UNUSED_RETURN_T, UNUSED_NEXT_T>}
   */
  private TemplateType asyncGeneratorValueTemplate;

  /** The template variable corresponding to the VALUE type in {@code IThenable<VALUE>} */
  private TemplateType iThenableTemplateKey;

  /** The template variable corresponding to the TYPE in {@code Promise<TYPE>} */
  private TemplateType promiseTemplateKey;

  /** The template variable in {@code Array<T>}. */
  private TemplateType arrayElementTemplateKey;

  /** The template variable in {@code ReadonlyArray<T>}. */
  private TemplateType readonlyArrayElementTemplateKey;

  /** The key variable in {@code ReadonlyMap<K, V>}. */
  private TemplateType readonlyMapKeyTemplateKey;

  /** The value variable in {@code ReadonlyMap<K, V>}. */
  private TemplateType readonlyMapValueTemplateKey;

  /** The key variable in {@code Map<K, V>}. */
  private TemplateType mapKeyTemplateKey;

  /** The value variable in {@code Map<K, V>}. */
  private TemplateType mapValueTemplateKey;

  @Deprecated public static final String OBJECT_ELEMENT_TEMPLATE = I_OBJECT_ELEMENT_TEMPLATE;

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

  // Only needed for type resolution at the moment
  private final transient Map<String, ClosureNamespace> closureNamespaces = new HashMap<>();

  // NOTE: This would normally be "static final" but that causes unit test failures
  // when serializing and deserializing compiler state for multistage builds.
  private final Node nameTableGlobalRoot = new Node(Token.ROOT);

  // NOTE(nicksantos): This is a terrible terrible hack. When type expressions are evaluated, we
  // need to be able to decide whether that type name resolves to a nullable type or a non-nullable
  // type. Object types are nullable, but enum types and typedefs are not.
  //
  // Notice that it's not good enough to just declare enum types and typedefs sooner.
  // For example, if we have
  //   /** @enum {MyObject} */ var MyEnum = ...;
  // we won't be to declare "MyEnum" without evaluating the expression {MyObject}, and following
  // those dependencies starts to lead us into undecidable territory. Instead, we "pre-declare" enum
  // types and typedefs, so that the expression resolver can decide whether a given name is
  // nullable or not.
  // Also, note that this solution is buggy and the type resolution still gets default nullability
  // wrong in some cases. See b/116853368. Probably NamedType should be responsible for
  // applying nullability to forward references instead of the type expression evaluation.
  private final Multimap<Node, String> nonNullableTypeNames =
      MultimapBuilder.hashKeys().hashSetValues().build();

  // Types that have been "forward-declared."
  // If these types are not declared anywhere in the binary, we shouldn't
  // try to type-check them at all.
  private final transient Set<String> forwardDeclaredTypes;

  // A map of properties to the types on which those properties have been declared.
  // "Reference" types are excluded because those already exist in eachRefTypeIndexedByProperty to
  // avoid blowing up the size of this map.
  private final transient SetMultimap<String, JSType> nonRefTypesIndexedByProperty =
      MultimapBuilder.hashKeys().linkedHashSetValues().build();

  private JSType sentinelObjectLiteral;

  // To avoid blowing up the size of nonRefTypesIndexedByProperty, we use the sentinel object
  // literal instead of registering arbitrarily many types.
  // But because of the way unions are constructed, some properties of record types in unions
  // are getting dropped and cause spurious "non-existent property" warnings.
  // The next two fields avoid the warnings. The first field contains property names of records
  // that participate in unions, and have caused properties to be dropped.
  // The second field contains the names of the dropped properties. When checking
  // canPropertyBeDefined, if the type has a property in propertiesOfSupertypesInUnions, we
  // consider it to possibly have any property in droppedPropertiesOfUnions. This is a loose
  // check, but we restrict it to records that may be present in unions, and it allows us to
  // keep nonRefTypesIndexedByProperty small.
  private final Set<String> propertiesOfSupertypesInUnions = new HashSet<>();
  private final Set<String> droppedPropertiesOfUnions = new HashSet<>();

  // A map of properties to each reference type on which those properties have been declared.
  private final SetMultimap<String, ObjectType> eachRefTypeIndexedByProperty =
      MultimapBuilder.SetMultimapBuilder.hashKeys().linkedHashSetValues().build();

  // A single empty TemplateTypeMap, which can be safely reused in cases where
  // there are no template types.
  private final TemplateTypeMap emptyTemplateTypeMap;

  private final JSTypeResolver resolver;

  public JSTypeRegistry(ErrorReporter reporter) {
    this(reporter, ImmutableSet.<String>of());
  }

  /** Constructs a new type registry populated with the built-in types. */
  public JSTypeRegistry(ErrorReporter reporter, Set<String> forwardDeclaredTypes) {
    this.reporter = reporter;
    this.forwardDeclaredTypes = forwardDeclaredTypes;
    this.emptyTemplateTypeMap = TemplateTypeMap.createEmpty(this);
    this.resolver = JSTypeResolver.create(this);
    this.nativeTypes = new JSType[JSTypeNative.values().length];

    try (JSTypeResolver.Closer closer = this.resolver.openForDefinition()) {
      initializeBuiltInTypes();
      initializeRegistry();
    }
  }

  private JSType getSentinelObjectLiteral() {
    if (sentinelObjectLiteral == null) {
      sentinelObjectLiteral = createAnonymousObjectType(null);
    }
    return sentinelObjectLiteral;
  }

  /** Returns the template variable for the element type of Arrays. */
  public TemplateType getArrayElementKey() {
    return arrayElementTemplateKey;
  }

  /** Returns the template variable for the element type of ReadonlyArrays. */
  public TemplateType getReadonlyArrayElementKey() {
    return readonlyArrayElementTemplateKey;
  }

  /** Returns the template variable for the key type of ReadonlyMaps. */
  public TemplateType getReadonlyMapKey() {
    return readonlyMapKeyTemplateKey;
  }

  /** Returns the template variable for the value type of ReadonlyMaps. */
  public TemplateType getReadonlyMapValue() {
    return readonlyMapValueTemplateKey;
  }

  /**
   * @return The template variable corresponding to the property value type for Javascript Objects
   *     and Arrays.
   */
  public TemplateType getObjectElementKey() {
    return iObjectElementTemplateKey;
  }

  /**
   * @return The template variable corresponding to the property key type of the built-in Javascript
   *     object.
   */
  public TemplateType getObjectIndexKey() {
    checkNotNull(iObjectIndexTemplateKey);
    return iObjectIndexTemplateKey;
  }

  /**
   * @return The template variable for the Iterable interface.
   */
  public TemplateType getIterableTemplate() {
    return checkNotNull(iterableTemplate);
  }

  /**
   * @return The template variable for the IteratorIterable interface.
   */
  public TemplateType getIteratorIterableTemplateKey() {
    return checkNotNull(iteratorIterableTemplateKey);
  }

  /**
   * @return The template variable for the IteratorIterable interface.
   */
  public TemplateType getIIterableResultTemplateKey() {
    return checkNotNull(iiterableResultTemplate);
  }

  /** Return the value template variable for the Iterator interface. */
  public TemplateType getIteratorValueTemplate() {
    return checkNotNull(iteratorValueTemplate);
  }

  /** Return the value template variable for the Generator interface. */
  public TemplateType getGeneratorValueTemplate() {
    return checkNotNull(generatorValueTemplate);
  }

  /** Returns the template variable for the AsyncIterable interface. */
  public TemplateType getAsyncIterableTemplate() {
    return checkNotNull(asyncIterableTemplate);
  }

  /** Returns the template variable for the AsyncIterator interface. */
  public TemplateType getAsyncIteratorValueTemplate() {
    return checkNotNull(asyncIteratorValueTemplate);
  }

  /**
   * @return The template variable for the IThenable interface.
   */
  public TemplateType getIThenableTemplate() {
    return checkNotNull(iThenableTemplateKey);
  }

  /** Returns an immutable list of template types of the given builtin. */
  public @Nullable ImmutableList<TemplateType> maybeGetTemplateTypesOfBuiltin(
      StaticScope scope, String fnName) {
    JSType type = getType(scope, fnName);
    ObjectType objType = type == null ? null : type.toObjectType();
    if (objType != null && objType.isNativeObjectType()) {
      return objType.getTypeParameters();
    }
    return null;
  }

  public ErrorReporter getErrorReporter() {
    return reporter;
  }

  private void initializeBuiltInTypes() {
    // These locals shouldn't be all caps.
    BooleanType booleanType = new BooleanType(this);
    registerNativeType(JSTypeNative.BOOLEAN_TYPE, booleanType);

    NullType nullType = new NullType(this);
    registerNativeType(JSTypeNative.NULL_TYPE, nullType);

    BigIntType bigIntType = new BigIntType(this);
    registerNativeType(JSTypeNative.BIGINT_TYPE, bigIntType);

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
    // These should match the template type name in externs files.
    iObjectIndexTemplateKey = new TemplateType(this, "IOBJECT_KEY");
    iObjectElementTemplateKey = new TemplateType(this, I_OBJECT_ELEMENT_TEMPLATE);
    iteratorValueTemplate = new TemplateType(this, "VALUE");

    // Array-related template types.
    TemplateType iArrayLikeTemplate = new TemplateType(this, "VALUE2");
    arrayElementTemplateKey = new TemplateType(this, "T");
    readonlyArrayElementTemplateKey = new TemplateType(this, "T");

    // Map-related template types.
    readonlyMapKeyTemplateKey = new TemplateType(this, "KEY");
    readonlyMapValueTemplateKey = new TemplateType(this, "VALUE");
    mapKeyTemplateKey = new TemplateType(this, "KEY");
    mapValueTemplateKey = new TemplateType(this, "VALUE");

    // TODO(b/142881197): start using these unused iterator (and related type) template params
    // https://github.com/google/closure-compiler/issues/3489
    TemplateType iteratorReturnTemplate = new TemplateType(this, "UNUSED_RETURN_T");
    TemplateType iteratorNextTemplate = new TemplateType(this, "UNUSED_NEXT_T");
    iiterableResultTemplate = new TemplateType(this, "VALUE");
    asyncIteratorValueTemplate = new TemplateType(this, "VALUE");
    TemplateType asyncIteratorReturnTemplate = new TemplateType(this, "UNUSED_RETURN_T");
    TemplateType asyncIteratorNextTemplate = new TemplateType(this, "UNUSED_NEXT_T");
    TemplateType asyncIteratorIterableTemplate = new TemplateType(this, "VALUE");
    generatorValueTemplate = new TemplateType(this, "VALUE");
    TemplateType generatorReturnTemplate = new TemplateType(this, "UNUSED_RETURN_T");
    TemplateType generatorNextTemplate = new TemplateType(this, "UNUSED_NEXT_T");
    asyncGeneratorValueTemplate = new TemplateType(this, "VALUE");
    TemplateType asyncGeneratorReturnTemplate = new TemplateType(this, "UNUSED_RETURN_T");
    TemplateType asyncGeneratorNextTemplate = new TemplateType(this, "UNUSED_NEXT_T");
    iterableTemplate = new TemplateType(this, "VALUE");
    iteratorIterableTemplateKey = new TemplateType(this, "T");
    asyncIterableTemplate = new TemplateType(this, "VALUE");
    iThenableTemplateKey = new TemplateType(this, "TYPE");
    promiseTemplateKey = new TemplateType(this, "TYPE");

    /*
     * The default implicit prototype of all functions, as well as the ".prototype" field of
     * `(typeof Function)`.
     *
     * <p>This is the interesting `Function` protoype, as all its properties are inhertied by other
     * functions.
     *
     * <p>Must be created and registered early so `FunctionType.Builder` has access to it.
     */
    PrototypeObjectType functionPrototype =
        PrototypeObjectType.builder(this)
            .setName("Function.prototype")
            .setNative(true)
            // Defer until `Object` is defined. .setImplicitPrototype(objectType)
            .build();
    registerNativeType(JSTypeNative.FUNCTION_PROTOTYPE, functionPrototype);

    /*
     * The ".prototype" property of the type `Function`.
     *
     * <p>So named because `Function` constructs `?`s. This type is not particularly interesting,
     * since its properties aren't inherited by any other type.
     */
    PrototypeObjectType functionInstancePrototype =
        PrototypeObjectType.builder(this)
            .setName("?.prototype")
            .setNative(true)
            // Defer until `Object` is defined. .setImplicitPrototype(objectType)
            .build();
    registerNativeType(JSTypeNative.FUNCTION_INSTANCE_PROTOTYPE, functionInstancePrototype);

    /*
     * `Function`
     *
     * <p>The default implicit prototype of all `FunctionType`s is `functionPrototype`.
     */
    FunctionType functionType =
        FunctionType.builder(this)
            .withName("Function")
            .forConstructor()
            .forNativeType()
            .withParameters(createParametersWithVarArgs(unknownType))
            .withTypeOfThis(unknownType)
            .withReturnType(unknownType)
            .build();
    functionType.setPrototype(functionInstancePrototype, null);
    registerNativeType(JSTypeNative.FUNCTION_TYPE, functionType);

    /*
     * `(typeof Function)`
     *
     * <p>The default implict prototype of all `FunctionType`s is `functionPrototype`.
     */
    FunctionType functionFunctionType =
        FunctionType.builder(this)
            .withName("Function")
            .forConstructor()
            .forNativeType()
            .withParameters(createParametersWithVarArgs(allType))
            .withTypeOfThis(functionType)
            // TODO(nickreid): .withReturnsOwnInstanceType()
            .build();
    functionFunctionType.setPrototype(functionPrototype, null);
    registerNativeType(JSTypeNative.FUNCTION_FUNCTION_TYPE, functionFunctionType);

    // `Object.prototype`
    ObjectType objectPrototype =
        PrototypeObjectType.builder(this)
            .setName("Object.prototype")
            .setNative(true)
            .setImplicitPrototype(null)
            .build();
    registerNativeType(JSTypeNative.OBJECT_PROTOTYPE, objectPrototype);

    // Object
    FunctionType objectFunctionType =
        nativeConstructorBuilder("Object")
            .withParameters(createOptionalParameters(allType))
            .withReturnsOwnInstanceType()
            .withTemplateKeys(iObjectIndexTemplateKey, iObjectElementTemplateKey)
            .build();
    objectFunctionType.setPrototype(objectPrototype, null);
    registerNativeType(JSTypeNative.OBJECT_FUNCTION_TYPE, objectFunctionType);

    ObjectType objectType = objectFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.OBJECT_TYPE, objectType);

    functionPrototype.clearCachedValues();
    functionPrototype.setImplicitPrototype(objectType);
    functionInstancePrototype.clearCachedValues();
    functionInstancePrototype.setImplicitPrototype(objectType);

    // IObject
    FunctionType iObjectFunctionType =
        nativeInterface("IObject", iObjectIndexTemplateKey, iObjectElementTemplateKey);
    registerNativeType(JSTypeNative.I_OBJECT_FUNCTION_TYPE, iObjectFunctionType);
    ObjectType iObjectType = iObjectFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.I_OBJECT_TYPE, iObjectType);

    // Bottom Types
    NoType noType = new NoType(this);
    registerNativeType(JSTypeNative.NO_TYPE, noType);

    NoObjectType noObjectType = new NoObjectType(this);
    registerNativeType(JSTypeNative.NO_OBJECT_TYPE, noObjectType);

    NoResolvedType noResolvedType = new NoResolvedType(this);
    registerNativeType(JSTypeNative.NO_RESOLVED_TYPE, noResolvedType);

    FunctionType iterableFunctionType = nativeInterface("Iterable", iterableTemplate);
    registerNativeType(JSTypeNative.ITERABLE_FUNCTION_TYPE, iterableFunctionType);
    ObjectType iterableType = iterableFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.ITERABLE_TYPE, iterableType);

    FunctionType iteratorFunctionType =
        nativeInterface(
            "Iterator", iteratorValueTemplate, iteratorReturnTemplate, iteratorNextTemplate);
    registerNativeType(JSTypeNative.ITERATOR_FUNCTION_TYPE, iteratorFunctionType);
    ObjectType iteratorType = iteratorFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.ITERATOR_TYPE, iteratorType);

    FunctionType iteratorIterableFunctionType =
        nativeInterface("IteratorIterable", iteratorIterableTemplateKey);
    registerNativeType(JSTypeNative.ITERATOR_ITERABLE_FUNCTION_TYPE, iteratorIterableFunctionType);
    iteratorIterableFunctionType.setExtendedInterfaces(
        ImmutableList.of(
            createTemplatizedType(iterableType, iteratorIterableTemplateKey),
            createTemplatizedType(iteratorType, iteratorIterableTemplateKey)));
    ObjectType iteratorIterableType = iteratorIterableFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.ITERATOR_ITERABLE_TYPE, iteratorIterableType);

    FunctionType iiterableResultFunctionType =
        nativeInterface("IIterableResult", iiterableResultTemplate);
    registerNativeType(JSTypeNative.I_ITERABLE_RESULT_FUNCTION_TYPE, iiterableResultFunctionType);
    ObjectType iiterableResultType = iiterableResultFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.I_ITERABLE_RESULT_TYPE, iiterableResultType);

    // IArrayLike.
    FunctionType iArrayLikeFunctionType = nativeRecord("IArrayLike", iArrayLikeTemplate);
    iArrayLikeFunctionType.setExtendedInterfaces(
        ImmutableList.of(createTemplatizedType(iObjectType, numberType, iArrayLikeTemplate)));
    registerNativeType(JSTypeNative.I_ARRAY_LIKE_FUNCTION_TYPE, iArrayLikeFunctionType);
    ObjectType iArrayLikeType = iArrayLikeFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.I_ARRAY_LIKE_TYPE, iArrayLikeType);

    // ReadonlyArray
    FunctionType readonlyArrayFunctionType =
        nativeRecord("ReadonlyArray", readonlyArrayElementTemplateKey);
    registerNativeType(JSTypeNative.READONLY_ARRAY_FUNCTION_TYPE, readonlyArrayFunctionType);
    readonlyArrayFunctionType.setExtendedInterfaces(
        ImmutableList.of(
            createTemplatizedType(iArrayLikeType, readonlyArrayElementTemplateKey),
            createTemplatizedType(iterableType, readonlyArrayElementTemplateKey)));
    ObjectType readonlyArrayType = readonlyArrayFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.READONLY_ARRAY_TYPE, readonlyArrayType);

    // Array
    FunctionType arrayFunctionType =
        nativeConstructorBuilder("Array")
            .withParameters(createParametersWithVarArgs(allType))
            .withReturnsOwnInstanceType()
            .withTemplateKeys(arrayElementTemplateKey)
            .build();
    arrayFunctionType.getPrototype(); // Force initialization
    arrayFunctionType.setImplementedInterfaces(
        ImmutableList.of(createTemplatizedType(readonlyArrayType, arrayElementTemplateKey)));
    registerNativeType(JSTypeNative.ARRAY_FUNCTION_TYPE, arrayFunctionType);

    ObjectType arrayType = arrayFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.ARRAY_TYPE, arrayType);

    // ITemplateArray extends !Array<string>
    FunctionType iTemplateArrayFunctionType =
        nativeConstructorBuilder("ITemplateArray").withParameters().build();
    registerNativeType(
        JSTypeNative.I_TEMPLATE_ARRAY_TYPE, iTemplateArrayFunctionType.getInstanceType());

    FunctionType generatorFunctionType =
        nativeInterface(
            "Generator", generatorValueTemplate, generatorReturnTemplate, generatorNextTemplate);
    generatorFunctionType.setExtendedInterfaces(
        ImmutableList.of(createTemplatizedType(iteratorIterableType, generatorValueTemplate)));
    registerNativeType(JSTypeNative.GENERATOR_FUNCTION_TYPE, generatorFunctionType);
    registerNativeType(JSTypeNative.GENERATOR_TYPE, generatorFunctionType.getInstanceType());

    FunctionType asyncIteratorFunctionType =
        nativeInterface(
            "AsyncIterator",
            asyncIteratorValueTemplate,
            asyncIteratorReturnTemplate,
            asyncIteratorNextTemplate);
    registerNativeType(JSTypeNative.ASYNC_ITERATOR_FUNCTION_TYPE, asyncIteratorFunctionType);
    registerNativeType(
        JSTypeNative.ASYNC_ITERATOR_TYPE, asyncIteratorFunctionType.getInstanceType());

    FunctionType asyncIterableFunctionType =
        nativeInterface("AsyncIterable", asyncIterableTemplate);
    registerNativeType(JSTypeNative.ASYNC_ITERABLE_FUNCTION_TYPE, asyncIterableFunctionType);
    registerNativeType(
        JSTypeNative.ASYNC_ITERABLE_TYPE, asyncIterableFunctionType.getInstanceType());

    FunctionType asyncIteratorIterableFunctionType =
        nativeInterface("AsyncIteratorIterable", asyncIteratorIterableTemplate);
    asyncIteratorIterableFunctionType.setExtendedInterfaces(
        ImmutableList.of(
            createTemplatizedType(
                asyncIteratorFunctionType.getInstanceType(), asyncIteratorIterableTemplate),
            createTemplatizedType(
                asyncIterableFunctionType.getInstanceType(), asyncIteratorIterableTemplate)));

    registerNativeType(
        JSTypeNative.ASYNC_ITERATOR_ITERABLE_FUNCTION_TYPE, asyncIteratorIterableFunctionType);
    registerNativeType(
        JSTypeNative.ASYNC_ITERATOR_ITERABLE_TYPE,
        asyncIteratorIterableFunctionType.getInstanceType());

    FunctionType asyncGeneratorFunctionType =
        nativeInterface(
            "AsyncGenerator",
            asyncGeneratorValueTemplate,
            asyncGeneratorReturnTemplate,
            asyncGeneratorNextTemplate);
    registerNativeType(JSTypeNative.ASYNC_GENERATOR_FUNCTION_TYPE, asyncGeneratorFunctionType);
    registerNativeType(
        JSTypeNative.ASYNC_GENERATOR_TYPE, asyncGeneratorFunctionType.getInstanceType());

    FunctionType ithenableFunctionType = nativeInterface("IThenable", iThenableTemplateKey);
    ithenableFunctionType.setStruct();
    registerNativeType(JSTypeNative.I_THENABLE_FUNCTION_TYPE, ithenableFunctionType);
    ObjectType ithenableType = ithenableFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.I_THENABLE_TYPE, ithenableType);

    // Thenable is an @typedef
    JSType thenableType = createRecordType(ImmutableMap.of("then", unknownType));
    identifyNonNullableName(null, "Thenable");
    registerNativeType(JSTypeNative.THENABLE_TYPE, thenableType);

    // Create built-in Promise type, whose constructor takes one parameter.
    // @param {function(
    //             function((TYPE|IThenable<TYPE>|Thenable|null)=),
    //             function(*=))} resolver
    JSType promiseParameterType =
        createFunctionType(
            /* returnType= */ unknownType,
            /* parameterTypes...= */ createFunctionType(
                unknownType,
                createOptionalParameters(
                    createUnionType(
                        promiseTemplateKey,
                        createTemplatizedType(ithenableType, promiseTemplateKey),
                        thenableType,
                        nullType))),
            createFunctionType(unknownType, createOptionalParameters(allType)));

    FunctionType promiseFunctionType =
        nativeConstructorBuilder("Promise")
            .withParameters(this.createParameters(promiseParameterType))
            .withTemplateKeys(promiseTemplateKey)
            .build();
    promiseFunctionType.setImplementedInterfaces(
        ImmutableList.of(createTemplatizedType(ithenableType, promiseTemplateKey)));

    registerNativeType(JSTypeNative.PROMISE_FUNCTION_TYPE, promiseFunctionType);
    registerNativeType(JSTypeNative.PROMISE_TYPE, promiseFunctionType.getInstanceType());

    // Arguments
    FunctionType argumentsFunctionType =
        nativeConstructorBuilder("Arguments").withParameters().build();
    argumentsFunctionType.setImplementedInterfaces(
        ImmutableList.of(
            createTemplatizedType(iArrayLikeType, unknownType),
            createTemplatizedType(iterableType, unknownType)));
    registerNativeType(JSTypeNative.ARGUMENTS_FUNCTION_TYPE, argumentsFunctionType);
    registerNativeType(JSTypeNative.ARGUMENTS_TYPE, argumentsFunctionType.getInstanceType());

    // (bigint,number,string)
    JSType bigintNumberString = createUnionType(bigIntType, numberType, stringType);
    registerNativeType(BIGINT_NUMBER_STRING, bigintNumberString);

    // BigInt
    FunctionType bigIntObjectFunctionType =
        nativeConstructorBuilder("BigInt")
            .withParameters(createParameters(bigintNumberString))
            .withReturnType(bigIntType)
            .build();
    bigIntObjectFunctionType.getPrototype(); // Force initialization
    registerNativeType(JSTypeNative.BIGINT_OBJECT_FUNCTION_TYPE, bigIntObjectFunctionType);

    ObjectType bigIntObjectType = bigIntObjectFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.BIGINT_OBJECT_TYPE, bigIntObjectType);

    // Boolean
    FunctionType booleanObjectFunctionType =
        nativeConstructorBuilder("Boolean")
            .withParameters(createOptionalParameters(allType))
            .withReturnType(booleanType)
            .build();
    booleanObjectFunctionType.getPrototype(); // Force initialization
    registerNativeType(JSTypeNative.BOOLEAN_OBJECT_FUNCTION_TYPE, booleanObjectFunctionType);

    ObjectType booleanObjectType = booleanObjectFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.BOOLEAN_OBJECT_TYPE, booleanObjectType);

    // Date
    FunctionType dateFunctionType =
        nativeConstructorBuilder("Date")
            .withParameters(
                createOptionalParameters(
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
            .withParameters(createOptionalParameters(allType))
            .withReturnType(numberType)
            .build();
    numberObjectFunctionType.getPrototype(); // Force initialization
    registerNativeType(JSTypeNative.NUMBER_OBJECT_FUNCTION_TYPE, numberObjectFunctionType);

    ObjectType numberObjectType = numberObjectFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.NUMBER_OBJECT_TYPE, numberObjectType);

    // RegExp
    FunctionType regexpFunctionType =
        nativeConstructorBuilder("RegExp")
            .withParameters(createOptionalParameters(allType, allType))
            .withReturnsOwnInstanceType()
            .build();
    regexpFunctionType.getPrototype(); // Force initialization
    registerNativeType(JSTypeNative.REGEXP_FUNCTION_TYPE, regexpFunctionType);

    ObjectType regexpType = regexpFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.REGEXP_TYPE, regexpType);

    // String
    FunctionType stringObjectFunctionType =
        nativeConstructorBuilder("String")
            .withParameters(createOptionalParameters(allType))
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
            .withParameters(createOptionalParameters(allType))
            .withReturnType(symbolType)
            .build();
    symbolObjectFunctionType.getPrototype(); // Force initialization
    registerNativeType(JSTypeNative.SYMBOL_OBJECT_FUNCTION_TYPE, symbolObjectFunctionType);

    ObjectType symbolObjectType = symbolObjectFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.SYMBOL_OBJECT_TYPE, symbolObjectType);

    // (null|void)
    JSType nullVoid = createUnionType(nullType, voidType);
    registerNativeType(JSTypeNative.NULL_VOID, nullVoid);

    // (string|number|boolean)
    JSType numberStringBoolean = createUnionType(numberType, stringType, booleanType);
    registerNativeType(JSTypeNative.NUMBER_STRING_BOOLEAN, numberStringBoolean);

    // (string|number|boolean|symbol)
    JSType valueTypes = createUnionType(numberType, stringType, booleanType, symbolType);
    registerNativeType(JSTypeNative.VALUE_TYPES, valueTypes);

    // (number|symbol)
    JSType numberSymbol = createUnionType(numberType, symbolType);
    registerNativeType(JSTypeNative.NUMBER_SYMBOL, numberSymbol);

    // (string|symbol)
    JSType stringSymbol = createUnionType(stringType, symbolType);
    registerNativeType(JSTypeNative.STRING_SYMBOL, stringSymbol);

    // (string,number)
    JSType numberString = createUnionType(numberType, stringType);
    registerNativeType(JSTypeNative.NUMBER_STRING, numberString);

    // (bigint,number)
    JSType bigintNumber = createUnionType(bigIntType, numberType);
    registerNativeType(BIGINT_NUMBER, bigintNumber);

    // (BigInt,Number)
    JSType bigintNumberObject = createUnionType(bigIntObjectType, numberObjectType);
    registerNativeType(BIGINT_NUMBER_OBJECT, bigintNumberObject);

    // (Bigint,Number,String)
    JSType bigintNumberStringObject =
        createUnionType(bigIntObjectType, numberObjectType, stringObjectType);
    registerNativeType(BIGINT_NUMBER_STRING_OBJECT, bigintNumberStringObject);

    // (number,string,symbol)
    JSType numberStringSymbol = createUnionType(numberType, stringType, symbolType);
    registerNativeType(JSTypeNative.NUMBER_STRING_SYMBOL, numberStringSymbol);

    // (boolean,Boolean,number,Number,null,undefined)
    JSType numberAdditionSupertype =
        createUnionType(
            JSTypeNative.VOID_TYPE,
            JSTypeNative.NULL_TYPE,
            JSTypeNative.NUMBER_TYPE,
            JSTypeNative.NUMBER_OBJECT_TYPE,
            JSTypeNative.BOOLEAN_TYPE,
            JSTypeNative.BOOLEAN_OBJECT_TYPE);
    registerNativeType(JSTypeNative.NUMBER_ADDITION_SUPERTYPE, numberAdditionSupertype);

    // Native object properties are filled in by externs...

    // least function type, i.e. (All...) -> NoType
    FunctionType leastFunctionType = createNativeFunctionTypeWithVarArgs(noType, allType);
    registerNativeType(JSTypeNative.LEAST_FUNCTION_TYPE, leastFunctionType);

    // the 'this' object in the global scope
    FunctionType globalThisCtor =
        nativeConstructorBuilder("global this")
            .withParameters(createParameters(allType))
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

    // ReadonlyMap
    FunctionType readonlyMapFunctionType =
        nativeInterface("ReadonlyMap", readonlyMapKeyTemplateKey, readonlyMapValueTemplateKey);
    registerNativeType(JSTypeNative.READONLY_MAP_FUNCTION_TYPE, readonlyMapFunctionType);
    readonlyMapFunctionType.setExtendedInterfaces(
        ImmutableList.of(
            createTemplatizedType(
                iterableType,
                createTemplatizedType(
                    arrayType,
                    createUnionType(readonlyMapKeyTemplateKey, readonlyMapValueTemplateKey)))));
    ObjectType readonlyMapType = readonlyMapFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.READONLY_MAP_TYPE, readonlyMapType);

    // Map
    FunctionType mapFunctionType =
        nativeConstructorBuilder("Map")
            .withParameters(
                createOptionalParameters(
                    createUnionType(
                        nullType,
                        createTemplatizedType(
                            iterableType,
                            createTemplatizedType(
                                arrayType,
                                createUnionType(mapKeyTemplateKey, mapValueTemplateKey))),
                        createTemplatizedType(
                            arrayType,
                            createTemplatizedType(
                                arrayType,
                                createUnionType(mapKeyTemplateKey, mapValueTemplateKey))))))
            .withTemplateKeys(mapKeyTemplateKey, mapValueTemplateKey)
            .build();
    registerNativeType(JSTypeNative.MAP_FUNCTION_TYPE, mapFunctionType);
    mapFunctionType.getPrototype(); // Force initialization
    mapFunctionType.setImplementedInterfaces(
        ImmutableList.of(
            createTemplatizedType(readonlyMapType, mapKeyTemplateKey, mapValueTemplateKey)));
    ObjectType mapType = mapFunctionType.getInstanceType();
    registerNativeType(JSTypeNative.MAP_TYPE, mapType);
  }

  private void initializeRegistry() {
    registerGlobalType(getNativeType(JSTypeNative.ARGUMENTS_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.ARRAY_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.READONLY_ARRAY_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.MAP_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.READONLY_MAP_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.ASYNC_ITERABLE_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.ASYNC_ITERATOR_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.ASYNC_ITERATOR_ITERABLE_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.ASYNC_GENERATOR_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.BIGINT_OBJECT_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.BIGINT_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.BOOLEAN_OBJECT_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.BOOLEAN_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.I_ARRAY_LIKE_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.ITERABLE_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.ITERATOR_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.ITERATOR_ITERABLE_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.GENERATOR_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.DATE_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.I_OBJECT_TYPE));
    registerGlobalType(getNativeType(JSTypeNative.I_ITERABLE_RESULT_TYPE));
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
    registerGlobalType(getNativeType(JSTypeNative.FUNCTION_TYPE), "Function");
    registerGlobalType(getNativeType(JSTypeNative.GLOBAL_THIS), "Global");
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
   *     <p>This assumed that the Scope construction is complete. It can not be used during scope
   *     construction to determine if a name is already defined as a shadowed name from a parent
   *     scope would be returned.
   */
  private static StaticScope getLookupScope(StaticScope scope, String name) {
    if (scope != null && scope.getParentScope() != null) {
      return scope.getTopmostScopeOfEventualDeclaration(getRootElementOfName(name));
    }
    return scope;
  }

  private @Nullable Node getRootNodeForScope(StaticScope scope) {
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
    StaticScope declarationScope = getLookupScope(scope, name);
    JSType resolvedViaTable = getTypeForScopeInternal(declarationScope, name);
    if (resolvedViaTable != null) {
      return resolvedViaTable;
    }
    StaticScope bestScope = declarationScope != null ? declarationScope : scope;
    return resolveViaComponents(bestScope, name);
  }

  private @Nullable JSType resolveViaComponents(StaticScope scope, String qualifiedName) {
    if (qualifiedName.isEmpty() || !(scope instanceof StaticTypedScope)) {
      return null;
    }
    StaticTypedScope resolutionScope = (StaticTypedScope) scope;
    // Skip closure namespace resolution of types whose root component is defined in a local scope
    // (not a global scope). Those will follow the normal resolution scheme. (For legacy
    // compatibility reasons we don't check for global names that are the same as the module root).
    if (!isNameDefinedLocally(resolutionScope, getRootElementOfName(qualifiedName))) {
      JSType resolvedViaClosureNamespace = resolveViaClosureNamespace(qualifiedName);
      if (resolvedViaClosureNamespace != null) {
        return resolvedViaClosureNamespace;
      }
    }
    return resolveViaProperties(resolutionScope, qualifiedName);
  }

  private static boolean isNameDefinedLocally(StaticTypedScope resolutionScope, String reference) {
    StaticTypedSlot slot = resolutionScope.getSlot(reference);
    return slot != null && slot.getScope() != null && slot.getScope().getParentScope() != null;
  }

  /**
   * Resolves a named type by checking for the longest prefix that matches some Closure namespace,
   * if any, then attempting to resolve via properties based on the type of the `exports` object in
   * that namespace.
   */
  public @Nullable JSType resolveViaClosureNamespace(String reference) {
    // Find the `exports` type of the longest prefix match of this namespace, if any. Then resolve
    // it via property.
    String prefix = reference;
    ImmutableList.Builder<String> unusedComponents = ImmutableList.builder();
    while (true) {
      ClosureNamespace namespace = this.closureNamespaces.get(prefix);
      if (namespace != null) {
        if (namespace.isLegacy()) {
          // Try to resolve this name via registry or properties.
          return null;
        } else {
          // Always stop resolution here whether successful or not, instead of continuing with
          // resolution via registry or via properties, to match legacy behavior.
          return resolveViaPropertyGivenSlot(
              namespace.type(), namespace.definitionNode(), unusedComponents.build().reverse());
        }
      }

      int lastDot = prefix.lastIndexOf(".");
      if (lastDot < 0) {
        return null;
      }

      String postfix = prefix.substring(lastDot + 1);
      unusedComponents.add(postfix);
      prefix = prefix.substring(0, lastDot);
    }
  }

  private @Nullable JSType resolveViaProperties(
      StaticTypedScope declarationScope, String qualifiedName) {
    checkNotNull(qualifiedName);
    checkArgument(!qualifiedName.isEmpty());
    String rootName = getRootElementOfName(qualifiedName);
    StaticTypedSlot slot = declarationScope.getOwnSlot(rootName);
    if (slot == null) {
      return null;
    }
    ImmutableList<String> componentNames =
        ImmutableList.copyOf(Iterables.skip(DOT_SPLITTER.split(qualifiedName), 1));
    JSType slotType = slot.getType();
    return resolveViaPropertyGivenSlot(slotType, null, componentNames);
  }

  /**
   * Resolve a type using a given StaticTypedSlot and list of properties on that type.
   *
   * @param slotType the JSType of the slot, possibly null
   * @param definitionNode If known, the Node representing the type definition.
   */
  private @Nullable JSType resolveViaPropertyGivenSlot(
      JSType slotType, @Nullable Node definitionNode, List<String> componentNames) {
    if (componentNames.isEmpty()) {
      JSType typedefType = resolveTypeFromNodeIfTypedef(definitionNode);
      if (typedefType != null) {
        return typedefType;
      }
    }

    // If the first component has a type of 'Unknown', then any type
    // names using it should be regarded as silently 'Unknown' rather than be
    // noisy about it.
    if (slotType == null || slotType.isAllType() || slotType.isNoType()) {
      return null;
    }

    // resolving component by component
    for (int i = 0; i < componentNames.size(); i++) {
      String component = componentNames.get(i);
      ObjectType parentObj = ObjectType.cast(slotType);
      if (parentObj == null || component.isEmpty() || !parentObj.hasOwnProperty(component)) {
        return null;
      }
      if (i == componentNames.size() - 1) {
        // Look for a typedefTypeProp on the definition node of the last component.
        Node def = parentObj.getPropertyDefSite(component);
        JSType typedefType = resolveTypeFromNodeIfTypedef(def);
        if (typedefType != null) {
          return typedefType;
        }
      }
      slotType = parentObj.getPropertyType(component);
    }

    // Translate "constructor" types to "instance" types.
    if (slotType == null) {
      return null;
    } else if (slotType.isFunctionType() && (slotType.isConstructor() || slotType.isInterface())) {
      return slotType.toMaybeFunctionType().getInstanceType();
    } else if (slotType.isNoObjectType()) {
      return this.getNativeObjectType(JSTypeNative.NO_OBJECT_TYPE);
    } else if (slotType instanceof EnumType) {
      return ((EnumType) slotType).getElementsType();
    } else {
      return null;
    }
  }

  /** Checks the given Node for a typedef annotation, resolving to that type if existent. */
  private static @Nullable JSType resolveTypeFromNodeIfTypedef(Node node) {
    if (node == null) {
      return null;
    }
    return node.getTypedefTypeProp();
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

  private void register(@Nullable StaticScope scope, JSType type, String name) {
    checkTypeName(name);
    registerForScope(getLookupScope(scope, name), type, name);
  }

  private void registerForScope(StaticScope scope, JSType type, String name) {
    scopedNameTable.put(getRootNodeForScope(scope), name, type);
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
   * Tells the type system that {@code owner} may have a property named {@code propertyName}. This
   * allows the registry to keep track of what types a property is defined upon.
   *
   * <p>This is NOT the same as saying that {@code owner} must have a property named type.
   * ObjectType#hasProperty attempts to minimize false positives ("if we're not sure, then don't
   * type check this property"). The type registry, on the other hand, should attempt to minimize
   * false negatives ("if this property is assigned anywhere in the program, it must show up in the
   * type registry").
   */
  public void registerPropertyOnType(String propertyName, JSType type) {
    if (type.isUnionType()) {
      for (JSType alternate : type.toMaybeUnionType().getAlternates()) {
        registerPropertyOnType(propertyName, alternate);
      }
      return;
    }

    if (isObjectLiteralThatCanBeSkipped(type)) {
      type = getSentinelObjectLiteral();
    }

    if (type instanceof ObjectType && ((ObjectType) type).hasReferenceName()) {
      ObjectType objType = (ObjectType) type;
      eachRefTypeIndexedByProperty.put(propertyName, objType);
    } else {
      nonRefTypesIndexedByProperty.put(propertyName, type);
    }
  }

  /** A tristate value returned from canPropertyBeDefined. */
  public enum PropDefinitionKind {
    UNKNOWN, // The property is not known to be part of this type
    KNOWN, // The properties is known to be defined on a type or its super types
    LOOSE, // The property is loosely associated with a type, typically one of its subtypes
    LOOSE_UNION // The property is loosely associated with a union type
  }

  /** Returns whether the given property can possibly be set on the given type. */
  public PropDefinitionKind canPropertyBeDefined(JSType type, String propertyName) {
    if (type.isStruct()) {
      // We are stricter about "struct" types and only allow access to
      // properties that to the best of our knowledge are available at creation
      // time and specifically not properties only defined on subtypes.

      switch (type.getPropertyKind(propertyName)) {
        case KNOWN_PRESENT:
          return PropDefinitionKind.KNOWN;
        case MAYBE_PRESENT:
          return PropDefinitionKind.LOOSE_UNION;
        case ABSENT:
          return PropDefinitionKind.UNKNOWN;
      }
    } else {
      if (!type.isEmptyType() && !type.isUnknownType()) {
        switch (type.getPropertyKind(propertyName)) {
          case KNOWN_PRESENT:
            return PropDefinitionKind.KNOWN;
          case MAYBE_PRESENT:
            return PropDefinitionKind.LOOSE_UNION;
          case ABSENT:
            // check for loose properties below.
            break;
        }
      }

      Iterable<JSType> associatedTypes = ImmutableList.of();
      if (nonRefTypesIndexedByProperty.containsKey(propertyName)) {
        associatedTypes = nonRefTypesIndexedByProperty.get(propertyName);
      }
      if (eachRefTypeIndexedByProperty.containsKey(propertyName)) {
        associatedTypes =
            Iterables.concat(associatedTypes, eachRefTypeIndexedByProperty.get(propertyName));
      }

      for (JSType alternative : associatedTypes) {
        JSType greatestSubtype = alternative.getGreatestSubtype(type);
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
   * Returns each reference type that has a property {@code propertyName} defined on it.
   *
   * <p>Unlike most types in our type system, the collection of types returned will not be
   * collapsed. This means that if a type is defined on {@code Object} and on {@code Array}, this
   * method must return {@code [Object, Array]}. It would not be correct to collapse them to {@code
   * [Object]}.
   */
  public Iterable<ObjectType> getEachReferenceTypeWithProperty(String propertyName) {
    if (eachRefTypeIndexedByProperty.containsKey(propertyName)) {
      return eachRefTypeIndexedByProperty.get(propertyName);
    } else {
      return ImmutableList.of();
    }
  }

  /** Finds the common supertype of the two given object types. */
  ObjectType findCommonSuperObject(ObjectType a, ObjectType b) {
    List<ObjectType> stackA = getSuperStack(a);
    List<ObjectType> stackB = getSuperStack(b);

    ObjectType result = getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    while (!stackA.isEmpty() && !stackB.isEmpty()) {
      ObjectType currentA = stackA.remove(stackA.size() - 1);
      ObjectType currentB = stackB.remove(stackB.size() - 1);
      if (currentA.equals(currentB)) {
        result = currentA;
      } else {
        return result;
      }
    }
    return result;
  }

  private static List<ObjectType> getSuperStack(ObjectType a) {
    List<ObjectType> stack = new ArrayList<>(5);
    for (ObjectType current = a; current != null; current = current.getImplicitPrototype()) {
      stack.add(current);
    }
    return stack;
  }

  /**
   * Records declared global type names. This makes resolution faster and more robust in the common
   * case.
   *
   * @param name The name of the type to be recorded.
   * @param type The actual type being associated with the name.
   * @return True if this name is not already defined, false otherwise.
   */
  public boolean declareType(StaticScope scope, String name, JSType type) {
    checkState(!name.isEmpty());
    if (getTypeForScopeInternal(getLookupScope(scope, name), name) != null) {
      return false;
    }

    register(scope, type, name);
    return true;
  }

  /**
   * Records declared global type names. This makes resolution faster and more robust in the common
   * case.
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
  public void overwriteDeclaredType(StaticScope scope, String name, JSType type) {
    checkState(isDeclaredForScope(scope, name), "missing name %s", name);
    register(scope, type, name);
  }

  /** Whether this is a forward-declared type name. */
  public boolean isForwardDeclaredType(String name) {
    return forwardDeclaredTypes.contains(name);
  }

  /**
   * First dereferences the JSType to remove null/undefined then returns a human-readable type name
   */
  public String getReadableTypeName(Node n) {
    return getReadableJSTypeName(n, true);
  }

  public String getReadableTypeNameNoDeref(Node n) {
    return getReadableJSTypeName(n, false);
  }

  private static @Nullable String getSimpleReadableJSTypeName(JSType type) {
    if (type instanceof AllType) {
      return type.toString();
    } else if (type instanceof ValueType) {
      return type.toString();
    } else if (type.isFunctionPrototypeType()) {
      return type.toString();
    } else if (type instanceof ObjectType) {
      if (!isReadableObjectType(type.toMaybeObjectType())) {
        return null;
      }

      Node source = type.toObjectType().getConstructor().getSource();
      if (source != null) {
        checkState(source.isFunction() || source.isClass(), source);
        String readable = source.getFirstChild().getOriginalName();
        if (readable != null) {
          return readable;
        }
      }
      return type.toString();

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
   * Given a node, get a human-readable name for the type of that node so that will be easy for the
   * programmer to find the original declaration.
   *
   * <p>For example, if SubFoo's property "bar" might have the human-readable name
   * "Foo.prototype.bar".
   *
   * @param n The node.
   * @param dereference If true, the type of the node will be dereferenced to an Object type, if
   *     possible. Prefer to call #getReadableTypeName(String) or
   *     #getReadableTypeNameNoDeref(String) instead of passing this as an argument.
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
        String propName = n.getString();
        objectType = objectType.getClosestDefiningType(propName);

        // Don't show complex function names or anonymous types.
        // Instead, try to get a human-readable type name.
        if (isReadableObjectType(objectType)) {
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

  private static boolean isReadableObjectType(ObjectType type) {
    if (type == null) {
      return false;
    } else if (type.isInstanceType()) {
      return true;
    } else if (type.isFunctionPrototypeType()) {
      return true;
    } else if (type.isEnumElementType()) {
      // TODO(b/147236174): This case should be deleted as impossible.
      return type.toMaybeEnumElementType().getConstructor() != null;
    }
    return false;
  }

  private JSType getJSTypeOrUnknown(Node n) {
    JSType jsType = n.getJSType();
    if (jsType == null) {
      return getNativeType(UNKNOWN_TYPE);
    } else {
      return jsType;
    }
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
  public JSType getType(@Nullable StaticScope scope, String jsTypeName) {
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
    switch (jsTypeName) {
      case "boolean":
        return getNativeType(JSTypeNative.BOOLEAN_TYPE);
      case "number":
        return getNativeType(JSTypeNative.NUMBER_TYPE);
      case "bigint":
        return getNativeType(JSTypeNative.BIGINT_TYPE);
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
      // get the 'last' template type key with this name because if a superclass template key
      // shares the same name as a subclass key, the subclass key is guaranteed to be last.
      type = thisType.getTemplateTypeMap().getLastTemplateTypeKeyByName(jsTypeName);
      if (type != null) {
        checkState(type.isTemplateType(), "expected:%s", type);
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
      return createNamedType(scope, jsTypeName, sourceName, lineno, charno);
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

  public JSTypeResolver getResolver() {
    return this.resolver;
  }

  public JSType evaluateTypeExpressionInGlobalScope(JSTypeExpression expr) {
    return expr.evaluate(null, this);
  }

  /**
   * Creates a type representing optional values of the given type.
   *
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
   *
   * @return the union of the type and the Null type
   */
  public JSType createNullableType(JSType type) {
    return createUnionType(type, getNativeType(JSTypeNative.NULL_TYPE));
  }

  /**
   * Creates a nullable and undefine-able value of the given type.
   *
   * @return The union of the type and null and undefined.
   */
  public JSType createOptionalNullableType(JSType type) {
    return createUnionType(
        type, getNativeType(JSTypeNative.VOID_TYPE), getNativeType(JSTypeNative.NULL_TYPE));
  }

  /** Creates a union type whose variants are the arguments. */
  public JSType createUnionType(JSType... variants) {
    return createUnionType(ImmutableList.copyOf(variants));
  }

  public JSType createUnionType(List<? extends JSType> variants) {
    return UnionType.builder(this).addAlternates(variants).build();
  }

  /** Creates a union type whose variants are the built-in types specified by the arguments. */
  public JSType createUnionType(JSTypeNative... variants) {
    UnionType.Builder builder = UnionType.builder(this);
    for (JSTypeNative type : variants) {
      builder.addAlternate(getNativeType(type));
    }
    return builder.build();
  }

  /**
   * Creates a function type.
   *
   * @param returnType the function's return type
   * @param parameterTypes the parameters' types
   */
  public FunctionType createFunctionType(JSType returnType, JSType... parameterTypes) {
    return createFunctionType(returnType, createParameters(parameterTypes));
  }

  /**
   * @param parameters the function's parameters or {@code null} to indicate that the parameter
   *     types are unknown.
   * @param returnType the function's return type or {@code null} to indicate that the return type
   *     is unknown.
   */
  public FunctionType createFunctionType(
      JSType returnType, List<FunctionType.Parameter> parameters) {
    return FunctionType.builder(this).withParameters(parameters).withReturnType(returnType).build();
  }

  /**
   * Creates a function type. The last parameter type of the function is considered a variable
   * length argument.
   *
   * @param returnType the function's return type
   * @param parameterTypes the parameters' types
   */
  public FunctionType createFunctionTypeWithVarArgs(JSType returnType, JSType... parameterTypes) {
    return createFunctionType(returnType, createParametersWithVarArgs(parameterTypes));
  }

  /**
   * Creates a function type. The last parameter type of the function is considered a variable
   * length argument.
   *
   * @param returnType the function's return type
   * @param parameterTypes the parameters' types
   */
  private FunctionType createNativeFunctionTypeWithVarArgs(
      JSType returnType, JSType... parameterTypes) {
    return createNativeFunctionType(returnType, createParametersWithVarArgs(parameterTypes));
  }

  /**
   * Creates a function type in which {@code this} refers to an object instance.
   *
   * @param instanceType the type of {@code this}
   * @param returnType the function's return type
   * @param parameterTypes the parameters' types
   */
  public JSType createFunctionTypeWithInstanceType(
      ObjectType instanceType, JSType returnType, List<JSType> parameterTypes) {
    ImmutableList<FunctionType.Parameter> paramsNode =
        createParameters(parameterTypes.toArray(new JSType[0]));
    return FunctionType.builder(this)
        .withParameters(paramsNode)
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
  public ImmutableList<FunctionType.Parameter> createParameters(JSType... parameterTypes) {
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
  private ImmutableList<FunctionType.Parameter> createParameters(
      boolean lastVarArgs, JSType... parameterTypes) {
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
   * Creates a tree hierarchy representing a typed argument list. The last parameter type is
   * considered a variable length argument.
   *
   * @param parameterTypes the parameter types. The last element of this array is considered a
   *     variable length argument.
   * @return a tree hierarchy representing a typed argument list.
   */
  public ImmutableList<FunctionType.Parameter> createParametersWithVarArgs(
      JSType... parameterTypes) {
    return createParameters(true, parameterTypes);
  }

  /**
   * Creates a tree hierarchy representing a typed parameter list in which every parameter is
   * optional.
   */
  public ImmutableList<FunctionType.Parameter> createOptionalParameters(JSType... parameterTypes) {
    FunctionParamBuilder builder = new FunctionParamBuilder(this);
    builder.addOptionalParams(parameterTypes);
    return builder.build();
  }

  /**
   * Creates a new function type based on an existing function type but with a new return type.
   *
   * @param existingFunctionType the existing function type.
   * @param returnType the new return type.
   */
  public FunctionType createFunctionTypeWithNewReturnType(
      FunctionType existingFunctionType, JSType returnType) {
    return existingFunctionType.toBuilder().withReturnType(returnType).build();
  }

  private FunctionType createNativeFunctionType(
      JSType returnType, List<FunctionType.Parameter> parameters) {
    return FunctionType.builder(this)
        .withParameters(parameters)
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
    return createRecordType(props.buildOrThrow());
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

  /** Create an object type. */
  public ObjectType createObjectType(String name, ObjectType implicitPrototype) {
    return PrototypeObjectType.builder(this)
        .setName(name)
        .setImplicitPrototype(implicitPrototype)
        .build();
  }

  /**
   * Create an anonymous object type.
   *
   * @param info Used to mark object literals as structs; can be {@code null}
   */
  public ObjectType createAnonymousObjectType(@Nullable JSDocInfo info) {
    PrototypeObjectType type = PrototypeObjectType.builder(this).setAnonymous(true).build();
    type.setPrettyPrint(true);
    type.setJSDocInfo(info);
    return type;
  }

  /**
   * Set the implicit prototype if it's possible to do so. There are a few different reasons why
   * this could be a no-op: for example, numbers can't be implicit prototypes, and we don't want to
   * change the implicit prototype if other classes have already subclassed this one.
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
      List<FunctionType.Parameter> parameters,
      JSType returnType,
      @Nullable ImmutableList<TemplateType> templateKeys,
      boolean isAbstract) {
    checkArgument(source == null || source.isFunction() || source.isClass());
    return FunctionType.builder(this)
        .forConstructor()
        .withName(name)
        .withSourceNode(source)
        .withParameters(parameters)
        .withReturnType(returnType)
        .withTemplateKeys(templateKeys)
        .withIsAbstract(isAbstract)
        .build();
  }

  public TemplateType createTemplateType(String name) {
    return new TemplateType(this, name);
  }

  public TemplateType createTemplateType(String name, JSType bound) {
    return new TemplateType(this, name, bound);
  }

  public TemplateType createTemplateTypeWithTransformation(String name, Node expr) {
    return new TemplateType(this, name, expr);
  }

  public TemplateTypeMap getEmptyTemplateTypeMap() {
    return this.emptyTemplateTypeMap;
  }

  /**
   * Creates a templatized instance of the specified type. Only ObjectTypes can currently be
   * templatized; extend the logic in this function when more types can be templatized.
   *
   * @param baseType the type to be templatized.
   * @param templatizedTypes a list of the template JSTypes. Will be matched by list order to the
   *     template keys on the base type.
   */
  public TemplatizedType createTemplatizedType(
      ObjectType baseType, ImmutableList<JSType> templatizedTypes) {
    checkNotNull(baseType);
    // Only ObjectTypes can currently be templatized; extend this logic when
    // more types can be templatized.
    return new TemplatizedType(this, baseType, templatizedTypes);
  }

  /**
   * Creates a templatized instance of the specified type. Only ObjectTypes can currently be
   * templatized; extend the logic in this function when more types can be templatized.
   *
   * @param baseType the type to be templatized.
   * @param templatizedTypes a map from TemplateType to corresponding JSType value. Any unfilled
   *     TemplateTypes on the baseType that are *not* contained in this map will have UNKNOWN_TYPE
   *     used as their value.
   */
  public TemplatizedType createTemplatizedType(
      ObjectType baseType, Map<TemplateType, JSType> templatizedTypes) {
    JSType unknownType = getNativeType(UNKNOWN_TYPE);
    return createTemplatizedType(
        baseType,
        mapTypes(
            (key) -> templatizedTypes.getOrDefault(key, unknownType),
            baseType.getTypeParameters()));
  }

  /**
   * Creates a templatized instance of the specified type. Only ObjectTypes can currently be
   * templatized; extend the logic in this function when more types can be templatized.
   *
   * @param baseType the type to be templatized.
   * @param templatizedTypes a list of the template JSTypes. Will be matched by list order to the
   *     template keys on the base type.
   */
  public TemplatizedType createTemplatizedType(ObjectType baseType, JSType... templatizedTypes) {
    return createTemplatizedType(baseType, ImmutableList.copyOf(templatizedTypes));
  }

  /** Creates a named type. */
  @VisibleForTesting
  public NamedType createNamedType(
      StaticTypedScope scope, String reference, String sourceName, int lineno, int charno) {
    return NamedType.builder(this, reference)
        .setScope(scope)
        .setResolutionKind(ResolutionKind.TYPE_NAME)
        .setErrorReportingLocation(sourceName, lineno, charno)
        .build();
  }

  /** Identifies the name of a typedef or enum before we actually declare it. */
  public void identifyNonNullableName(@Nullable StaticScope scope, String name) {
    checkNotNull(name);
    StaticScope lookupScope = getLookupScope(scope, name);
    nonNullableTypeNames.put(getRootNodeForScope(lookupScope), name);
  }

  /** Identifies the name of a typedef or enum before we actually declare it. */
  public boolean isNonNullableName(StaticScope scope, String name) {
    checkNotNull(name);
    scope = getLookupScope(scope, name);
    return nonNullableTypeNames.containsEntry(getRootNodeForScope(scope), name);
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
  public JSType createTypeFromCommentNode(
      Node n, String sourceName, @Nullable StaticTypedScope scope) {
    switch (n.getToken()) {
      case LC: // Record type.
        return createRecordTypeFromNodes(n.getFirstChild(), sourceName, scope);

      case BANG: // Not nullable
        {
          JSType child = createTypeFromCommentNode(n.getFirstChild(), sourceName, scope);
          if (child instanceof NamedType) {
            return ((NamedType) child).getBangType();
          }
          return child.restrictByNotNullOrUndefined();
        }

      case QMARK: // Nullable or unknown
        Node firstChild = n.getFirstChild();
        if (firstChild == null) {
          return getNativeType(UNKNOWN_TYPE);
        }
        return createNullableType(createTypeFromCommentNode(firstChild, sourceName, scope));

      case EQUALS: // Optional
        // TODO(b/117162687): stop automatically converting {string=} to {(string|undefined)]}
        return createOptionalType(createTypeFromCommentNode(n.getFirstChild(), sourceName, scope));

      case ITER_REST: // Var args
        return createTypeFromCommentNode(n.getFirstChild(), sourceName, scope);

      case STAR: // The AllType
        return getNativeType(ALL_TYPE);

      case PIPE: // Union type
        ImmutableList.Builder<JSType> builder = ImmutableList.builder();
        for (Node child = n.getFirstChild(); child != null; child = child.getNext()) {
          builder.add(createTypeFromCommentNode(child, sourceName, scope));
        }
        return createUnionType(builder.build());

      case EMPTY: // When the return value of a function is not specified
        return getNativeType(UNKNOWN_TYPE);

      case VOID: // Only allowed in the return value of a function.
        return getNativeType(VOID_TYPE);

      case TYPEOF:
        {
          String name = n.getFirstChild().getString();
          // TODO(sdh): require var to be const?
          QualifiedName qname = QualifiedName.of(name);
          String root = qname.getRoot();
          StaticScope declarationScope = scope.getTopmostScopeOfEventualDeclaration(root);
          StaticSlot rootSlot = scope.getSlot(root);
          JSType type = scope.lookupQualifiedName(qname);
          if (type == null || type.isUnknownType() || rootSlot.getScope() != declarationScope) {
            // Create a NamedType via getType so that it will be added to the list of types to
            // eventually resolve if necessary.
            return NamedType.builder(this, "typeof " + name)
                .setScope(scope)
                .setResolutionKind(ResolutionKind.TYPEOF)
                .setErrorReportingLocationFrom(n)
                .build();
          }
          if (type.isLiteralObject()) {
            JSType scopeType = type;
            type =
                NamedType.builder(this, "typeof " + name)
                    .setResolutionKind(ResolutionKind.NONE)
                    .setReferencedType(scopeType)
                    .build();
          }
          return type;
        }

      case STRINGLIT:
        {
          JSType nominalType =
              getType(scope, n.getString(), sourceName, n.getLineno(), n.getCharno());
          ImmutableList<JSType> templateArgs = parseTemplateArgs(nominalType, n, sourceName, scope);

          // Handle forward declared types
          if (nominalType.isNamedType() && !nominalType.isResolved()) {
            if (templateArgs != null) {
              nominalType =
                  nominalType.toMaybeNamedType().toBuilder().setTemplateTypes(templateArgs).build();
            }
            return addNullabilityBasedOnParseContext(n, nominalType, scope);
          }

          if (!(nominalType instanceof ObjectType) || isNonNullableName(scope, n.getString())) {
            return nominalType;
          }

          if (templateArgs == null || !nominalType.isRawTypeOfTemplatizedType()) {
            // TODO(nickreid): This case leaves template parameters unbound if users fail to
            // specify any arguments, allowing raw types to leak into programs.
            return addNullabilityBasedOnParseContext(n, nominalType, scope);
          }

          return addNullabilityBasedOnParseContext(
              n, createTemplatizedType((ObjectType) nominalType, templateArgs), scope);
        }

      case FUNCTION:
        JSType thisType = null;
        boolean isConstructor = false;
        Node current = n.getFirstChild();
        if (current.isThis() || current.isNew()) {
          Node contextNode = current.getFirstChild();

          JSType candidateThisType = createTypeFromCommentNode(contextNode, sourceName, scope);

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
                  Msg.JSDOC_FUNCTION_NEWNOTOBJECT.format(),
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
            if (arg.getToken() == Token.ITER_REST) {
              if (!arg.hasChildren()) {
                paramBuilder.addVarArgs(getNativeType(UNKNOWN_TYPE));
              } else {
                paramBuilder.addVarArgs(
                    createTypeFromCommentNode(arg.getFirstChild(), sourceName, scope));
              }
            } else {
              JSType type = createTypeFromCommentNode(arg, sourceName, scope);
              if (arg.getToken() == Token.EQUALS) {
                boolean addSuccess = paramBuilder.addOptionalParams(type);
                if (!addSuccess) {
                  reporter.warning(
                      Msg.JSDOC_FUNCTION_VARARGS.format(),
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

        JSType returnType = createTypeFromCommentNode(current, sourceName, scope);

        return FunctionType.builder(this)
            .withParameters(paramBuilder.build())
            .withReturnType(returnType)
            .withTypeOfThis(thisType)
            .withKind(isConstructor ? FunctionType.Kind.CONSTRUCTOR : FunctionType.Kind.ORDINARY)
            .build();

      default:
        throw new IllegalStateException("Unexpected node in type expression: " + n);
    }
  }

  private JSType addNullabilityBasedOnParseContext(Node n, JSType type, StaticScope scope) {
    // Other node types may be appropriate in the future.
    checkState(n.isName() || n.isStringLit(), n);
    checkNotNull(type);

    if (isNonNullableName(scope, n.getString())) {
      return type;
    } else if (type.isTemplateType()) {
      // Template types represent the substituted type exactly and should
      // not be wrapped.
      return type;
    } else if (n.hasParent() && n.getParent().getToken() == Token.BANG) {
      // Names parsed from beneath a BANG never need nullability added.
      return type;
    } else {
      return createNullableType(type);
    }
  }

  private @Nullable ImmutableList<JSType> parseTemplateArgs(
      JSType nominalType, Node typeNode, String sourceName, StaticTypedScope scope) {
    Node typeList = typeNode.getFirstChild();
    if (typeList == null) {
      return null;
    }

    ArrayList<JSType> templateArgs = new ArrayList<>();
    for (Node templateNode = typeList.getFirstChild();
        templateNode != null;
        templateNode = templateNode.getNext()) {
      templateArgs.add(createTypeFromCommentNode(templateNode, sourceName, scope));
    }

    // TODO(b/138617950): Eliminate the special case for `Object`.
    boolean isObject =
        typeNode.getString().equals("Object") || typeNode.getString().equals("window.Object");
    if (isObject && templateArgs.size() == 1) {
      templateArgs.add(0, getNativeType(UNKNOWN_TYPE));
    }

    if (nominalType.isNamedType() && !nominalType.isResolved()) {
      // The required number of template args will not be known until resolution, so just return all
      // the args.
      return ImmutableList.copyOf(templateArgs);
    }

    int requiredTemplateArgCount = nominalType.getTemplateParamCount();
    if (templateArgs.size() <= requiredTemplateArgCount) {
      return ImmutableList.copyOf(templateArgs);
    }

    if (!nominalType.isUnknownType()
        // TODO(b/287880204): delete the following case after cleaning up the codebase
        && !isNonNullableName(scope, typeNode.getString())) {
      Node firstExtraTemplateParam =
          typeNode.getFirstChild().getChildAtIndex(requiredTemplateArgCount);
      String message =
          "Too many template parameters\nFound "
              + templateArgs.size()
              + ", required at most "
              + requiredTemplateArgCount;
      reporter.warning(
          message,
          sourceName,
          firstExtraTemplateParam.getLineno(),
          firstExtraTemplateParam.getCharno());
    }
    return ImmutableList.copyOf(templateArgs.subList(0, requiredTemplateArgCount));
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
        fieldType = createTypeFromCommentNode(fieldTypeNode.getLastChild(), sourceName, scope);
      } else {
        // Otherwise, the type is UNKNOWN.
        fieldType = getNativeType(JSTypeNative.UNKNOWN_TYPE);
      }

      builder.addProperty(fieldName, fieldType, fieldNameNode);
    }

    return builder.build();
  }

  /**
   * Registers template types on the given scope root. This takes a Node rather than a StaticScope
   * because at the time it is called, the scope has not yet been created.
   */
  public void registerTemplateTypeNamesInScope(Iterable<TemplateType> keys, Node scopeRoot) {
    for (TemplateType key : keys) {
      scopedNameTable.put(scopeRoot, key.getReferenceName(), key);
    }
  }

  /** Returns a new scope that includes the given template names for type resolution purposes. */
  public StaticTypedScope createScopeWithTemplates(
      StaticTypedScope scope, Iterable<TemplateType> templates) {
    return new SyntheticTemplateScope(scope, templates);
  }

  /**
   * Synthetic scope that includes template names. This is necessary for resolving template names
   * outside the body of templated functions (e.g. when evaluating JSDoc on things assigned to a
   * prototype, or the parameter or return types of an annotated function), since there is not yet
   * (and may never be) any real scope to attach the types to.
   */
  private static class SyntheticTemplateScope implements StaticTypedScope {
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

    @Nullable TemplateType getTemplateType(String name) {
      return types.get(name);
    }

    @Override
    public StaticScope getTopmostScopeOfEventualDeclaration(String name) {
      if (types.get(name) != null) {
        return this;
      }
      return delegate.getTopmostScopeOfEventualDeclaration(name);
    }
  }

  private FunctionType.Builder nativeConstructorBuilder(String name) {
    return FunctionType.builder(this).forNativeType().forConstructor().withName(name);
  }

  private FunctionType nativeInterface(String name, TemplateType... templateKeys) {
    FunctionType.Builder builder =
        FunctionType.builder(this).forNativeType().forInterface().withName(name);
    if (templateKeys.length > 0) {
      builder.withTemplateKeys(templateKeys);
    }
    return builder.build();
  }

  private FunctionType nativeRecord(String name, TemplateType... templateKeys) {
    FunctionType type = nativeInterface(name, templateKeys);
    type.setImplicitMatch(true);
    return type;
  }

  /**
   * Registers a goog.module namespace (that does not have goog.module.declareLegacyNamespace)
   *
   * <p>This allows JSTypeRegistry to resolve types that refer to goog.modules by namespace. These
   * have unique handling because they exist only in the type space and do not have a corresponding
   * value space value.
   */
  public void registerNonLegacyClosureNamespace(
      String moduleName, Node definitionNode, JSType type) {
    closureNamespaces.put(
        moduleName, ClosureNamespace.create(/* isLegacy= */ false, definitionNode, type));
  }

  /** Registers a goog.provide or legacy goog.module namespace with the type registry */
  public void registerLegacyClosureNamespace(String moduleName) {
    closureNamespaces.put(moduleName, ClosureNamespace.create(/* isLegacy= */ true, null, null));
  }

  /** Stores information about a Closure namespace. */
  @AutoValue
  abstract static class ClosureNamespace {
    /**
     * Returns true if this is a goog.provide'd namespace or a goog.module namespace followed by
     * `goog.module.declareLegacyNamespace()
     */
    abstract boolean isLegacy();

    abstract @Nullable Node definitionNode();

    abstract @Nullable JSType type();

    static ClosureNamespace create(
        boolean isLegacy, @Nullable Node definitionNode, @Nullable JSType type) {
      return new AutoValue_JSTypeRegistry_ClosureNamespace(isLegacy, definitionNode, type);
    }
  }
}
