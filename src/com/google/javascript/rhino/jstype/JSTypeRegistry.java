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

import static com.google.javascript.rhino.jstype.JSTypeNative.ALL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.ARRAY_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleErrorReporter;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.RecordTypeBuilder.RecordProperty;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The type registry is used to resolve named types.
 *
 * <p>This class is not thread-safe.
 *
 */
public class JSTypeRegistry implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * The name associated with the template variable corresponding to the
   * property key type of the built-in Javascript object.
   */
  public static final String OBJECT_INDEX_TEMPLATE = "Object#Key";

  private TemplateType objectIndexTemplateKey;

  /**
   * The name associated with the template variable corresponding to the
   * property value type for Javascript Objects and Arrays.
   */
  public static final String OBJECT_ELEMENT_TEMPLATE = "Object#Element";

  private TemplateType objectElementTemplateKey;

  /**
   * The UnionTypeBuilder caps the maximum number of alternate types it
   * remembers and then defaults to "?" (unknown type). By default this max
   * is 20, but it's very easy for the same property to appear on more than 20
   * types. Use larger unions for property checking. 3000 was picked
   * semi-randomly for use by the Google+ FE project.
   */
  private static final int PROPERTY_CHECKING_UNION_SIZE = 3000;

  // TODO(user): An instance of this class should be used during
  // compilation. We also want to make all types' constructors package private
  // and force usage of this registry instead. This will allow us to evolve the
  // types without being tied by an open API.

  private final transient ErrorReporter reporter;

  // We use an Array instead of an immutable list because this lookup needs
  // to be very fast. When it was an immutable list, we were spending 5% of
  // CPU time on bounds checking inside get().
  private final JSType[] nativeTypes;

  private final Map<String, JSType> namesToTypes;

  // Set of namespaces in which types (or other namespaces) exist.
  private final Set<String> namespaces = new HashSet<String>();

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
  private final Set<String> nonNullableTypeNames = new HashSet<String>();

  // Types that have been "forward-declared."
  // If these types are not declared anywhere in the binary, we shouldn't
  // try to type-check them at all.
  private final Set<String> forwardDeclaredTypes = new HashSet<String>();

  // A map of properties to the types on which those properties have been
  // declared.
  private final Map<String, UnionTypeBuilder> typesIndexedByProperty =
      Maps.newHashMap();

  // A map of properties to each reference type on which those
  // properties have been declared. Each type has a unique name used
  // for de-duping.
  private final Map<String, Map<String, ObjectType>>
      eachRefTypeIndexedByProperty = Maps.newHashMap();

  // A map of properties to the greatest subtype on which those properties have
  // been declared. This is filled lazily from the types declared in
  // typesIndexedByProperty.
  private final Map<String, JSType> greatestSubtypeByProperty =
      Maps.newHashMap();

  // A map from interface name to types that implement it.
  private final Multimap<String, FunctionType> interfaceToImplementors =
      LinkedHashMultimap.create();

  // All the unresolved named types.
  private final Multimap<StaticScope<JSType>, NamedType> unresolvedNamedTypes =
      ArrayListMultimap.create();

  // All the resolved named types.
  private final Multimap<StaticScope<JSType>, NamedType> resolvedNamedTypes =
      ArrayListMultimap.create();

  // NamedType warns about unresolved types in the last generation.
  private boolean lastGeneration = true;

  // The template type name.
  private Map<String, TemplateType> templateTypes = Maps.newHashMap();

  // A single empty TemplateTypeMap, which can be safely reused in cases where
  // there are no template types.
  private final TemplateTypeMap emptyTemplateTypeMap;

  private final boolean tolerateUndefinedValues;

  /**
   * Constructs a new type registry populated with the built-in types.
   */
  public JSTypeRegistry(ErrorReporter reporter) {
    this(reporter, false);
  }

  /**
   * Constructs a new type registry populated with the built-in types.
   */
  public JSTypeRegistry(
      ErrorReporter reporter, boolean tolerateUndefinedValues) {
    this.reporter = reporter;
    this.emptyTemplateTypeMap = new TemplateTypeMap(
        this, ImmutableList.<TemplateType>of(), ImmutableList.<JSType>of());
    nativeTypes = new JSType[JSTypeNative.values().length];
    namesToTypes = new HashMap<String, JSType>();
    resetForTypeCheck();
    this.tolerateUndefinedValues = tolerateUndefinedValues;
  }

  /**
   * @return The template variable corresponding to the property value type for
   * Javascript Objects and Arrays.
   */
  public TemplateType getObjectElementKey() {
    return this.objectElementTemplateKey;
  }

  /**
   * @return The template variable corresponding to the
   * property key type of the built-in Javascript object.
   */
  public TemplateType getObjectIndexKey() {
    Preconditions.checkNotNull(objectIndexTemplateKey);
    return objectIndexTemplateKey;
  }

  public ErrorReporter getErrorReporter() {
    return reporter;
  }

  public boolean shouldTolerateUndefinedValues() {
    return tolerateUndefinedValues;
  }

  /**
   * Reset to run the TypeCheck pass.
   */
  public void resetForTypeCheck() {
    typesIndexedByProperty.clear();
    eachRefTypeIndexedByProperty.clear();
    initializeBuiltInTypes();
    namesToTypes.clear();
    namespaces.clear();
    initializeRegistry();
  }

  private void initializeBuiltInTypes() {
    objectIndexTemplateKey = new TemplateType(this, OBJECT_INDEX_TEMPLATE);
    objectElementTemplateKey = new TemplateType(this, OBJECT_ELEMENT_TEMPLATE);

    // These locals shouldn't be all caps.
    BooleanType BOOLEAN_TYPE = new BooleanType(this);
    registerNativeType(JSTypeNative.BOOLEAN_TYPE, BOOLEAN_TYPE);

    NullType NULL_TYPE = new NullType(this);
    registerNativeType(JSTypeNative.NULL_TYPE, NULL_TYPE);

    NumberType NUMBER_TYPE = new NumberType(this);
    registerNativeType(JSTypeNative.NUMBER_TYPE, NUMBER_TYPE);

    StringType STRING_TYPE = new StringType(this);
    registerNativeType(JSTypeNative.STRING_TYPE, STRING_TYPE);

    UnknownType UNKNOWN_TYPE = new UnknownType(this, false);
    registerNativeType(JSTypeNative.UNKNOWN_TYPE, UNKNOWN_TYPE);
    UnknownType checkedUnknownType = new UnknownType(this, true);
    registerNativeType(
        JSTypeNative.CHECKED_UNKNOWN_TYPE, checkedUnknownType);

    VoidType VOID_TYPE = new VoidType(this);
    registerNativeType(JSTypeNative.VOID_TYPE, VOID_TYPE);

    AllType ALL_TYPE = new AllType(this);
    registerNativeType(JSTypeNative.ALL_TYPE, ALL_TYPE);

    // Top Level Prototype (the One)
    // The initializations of TOP_LEVEL_PROTOTYPE and OBJECT_FUNCTION_TYPE
    // use each other's results, so at least one of them will get null
    // instead of an actual type; however, this seems to be benign.
    PrototypeObjectType TOP_LEVEL_PROTOTYPE =
        new PrototypeObjectType(this, null, null, true, null);
    registerNativeType(JSTypeNative.TOP_LEVEL_PROTOTYPE, TOP_LEVEL_PROTOTYPE);

    // Object
    FunctionType OBJECT_FUNCTION_TYPE =
        new FunctionType(this, "Object", null,
            createArrowType(createOptionalParameters(ALL_TYPE), UNKNOWN_TYPE),
            null,
            createTemplateTypeMap(ImmutableList.of(
                objectIndexTemplateKey, objectElementTemplateKey), null),
            true, true);

    OBJECT_FUNCTION_TYPE.setPrototype(TOP_LEVEL_PROTOTYPE, null);
    registerNativeType(JSTypeNative.OBJECT_FUNCTION_TYPE, OBJECT_FUNCTION_TYPE);

    ObjectType OBJECT_TYPE = OBJECT_FUNCTION_TYPE.getInstanceType();
    registerNativeType(JSTypeNative.OBJECT_TYPE, OBJECT_TYPE);

    ObjectType OBJECT_PROTOTYPE = OBJECT_FUNCTION_TYPE.getPrototype();
    registerNativeType(JSTypeNative.OBJECT_PROTOTYPE, OBJECT_PROTOTYPE);

    // Function
    FunctionType FUNCTION_FUNCTION_TYPE =
        new FunctionType(this, "Function", null,
            createArrowType(
                createParametersWithVarArgs(ALL_TYPE), UNKNOWN_TYPE),
            null, null, true, true);
    FUNCTION_FUNCTION_TYPE.setPrototypeBasedOn(OBJECT_TYPE);
    registerNativeType(
        JSTypeNative.FUNCTION_FUNCTION_TYPE, FUNCTION_FUNCTION_TYPE);

    ObjectType FUNCTION_PROTOTYPE = FUNCTION_FUNCTION_TYPE.getPrototype();
    registerNativeType(JSTypeNative.FUNCTION_PROTOTYPE, FUNCTION_PROTOTYPE);

    NoType NO_TYPE = new NoType(this);
    registerNativeType(JSTypeNative.NO_TYPE, NO_TYPE);

    NoObjectType NO_OBJECT_TYPE = new NoObjectType(this);
    registerNativeType(JSTypeNative.NO_OBJECT_TYPE, NO_OBJECT_TYPE);

    NoObjectType NO_RESOLVED_TYPE = new NoResolvedType(this);
    registerNativeType(JSTypeNative.NO_RESOLVED_TYPE, NO_RESOLVED_TYPE);

    // Array
    FunctionType ARRAY_FUNCTION_TYPE =
      new FunctionType(this, "Array", null,
          createArrowType(createParametersWithVarArgs(ALL_TYPE), null),
          null,
          createTemplateTypeMap(ImmutableList.of(
              objectElementTemplateKey), null),
          true, true);
    ARRAY_FUNCTION_TYPE.getInternalArrowType().returnType =
        ARRAY_FUNCTION_TYPE.getInstanceType();

    ObjectType arrayPrototype = ARRAY_FUNCTION_TYPE.getPrototype();
    registerNativeType(JSTypeNative.ARRAY_FUNCTION_TYPE, ARRAY_FUNCTION_TYPE);

    ObjectType ARRAY_TYPE = ARRAY_FUNCTION_TYPE.getInstanceType();
    registerNativeType(JSTypeNative.ARRAY_TYPE, ARRAY_TYPE);

    // Boolean
    FunctionType BOOLEAN_OBJECT_FUNCTION_TYPE =
        new FunctionType(this, "Boolean", null,
            createArrowType(createOptionalParameters(ALL_TYPE), BOOLEAN_TYPE),
            null, null, true, true);
    ObjectType booleanPrototype = BOOLEAN_OBJECT_FUNCTION_TYPE.getPrototype();
    registerNativeType(
        JSTypeNative.BOOLEAN_OBJECT_FUNCTION_TYPE,
        BOOLEAN_OBJECT_FUNCTION_TYPE);

    ObjectType BOOLEAN_OBJECT_TYPE =
        BOOLEAN_OBJECT_FUNCTION_TYPE.getInstanceType();
    registerNativeType(JSTypeNative.BOOLEAN_OBJECT_TYPE, BOOLEAN_OBJECT_TYPE);

    // Date
    FunctionType DATE_FUNCTION_TYPE =
      new FunctionType(this, "Date", null,
          createArrowType(
              createOptionalParameters(UNKNOWN_TYPE, UNKNOWN_TYPE, UNKNOWN_TYPE,
                  UNKNOWN_TYPE, UNKNOWN_TYPE, UNKNOWN_TYPE, UNKNOWN_TYPE),
              STRING_TYPE),
          null, null, true, true);
    ObjectType datePrototype = DATE_FUNCTION_TYPE.getPrototype();
    registerNativeType(JSTypeNative.DATE_FUNCTION_TYPE, DATE_FUNCTION_TYPE);

    ObjectType DATE_TYPE = DATE_FUNCTION_TYPE.getInstanceType();
    registerNativeType(JSTypeNative.DATE_TYPE, DATE_TYPE);

    // Error
    FunctionType ERROR_FUNCTION_TYPE = new ErrorFunctionType(this, "Error");
    registerNativeType(JSTypeNative.ERROR_FUNCTION_TYPE, ERROR_FUNCTION_TYPE);

    ObjectType ERROR_TYPE = ERROR_FUNCTION_TYPE.getInstanceType();
    registerNativeType(JSTypeNative.ERROR_TYPE, ERROR_TYPE);

    // EvalError
    FunctionType EVAL_ERROR_FUNCTION_TYPE =
        new ErrorFunctionType(this, "EvalError");
    EVAL_ERROR_FUNCTION_TYPE.setPrototypeBasedOn(ERROR_TYPE);
    registerNativeType(
        JSTypeNative.EVAL_ERROR_FUNCTION_TYPE, EVAL_ERROR_FUNCTION_TYPE);

    ObjectType EVAL_ERROR_TYPE = EVAL_ERROR_FUNCTION_TYPE.getInstanceType();
    registerNativeType(JSTypeNative.EVAL_ERROR_TYPE, EVAL_ERROR_TYPE);

    // RangeError
    FunctionType RANGE_ERROR_FUNCTION_TYPE =
        new ErrorFunctionType(this, "RangeError");
    RANGE_ERROR_FUNCTION_TYPE.setPrototypeBasedOn(ERROR_TYPE);
    registerNativeType(
        JSTypeNative.RANGE_ERROR_FUNCTION_TYPE, RANGE_ERROR_FUNCTION_TYPE);

    ObjectType RANGE_ERROR_TYPE = RANGE_ERROR_FUNCTION_TYPE.getInstanceType();
    registerNativeType(JSTypeNative.RANGE_ERROR_TYPE, RANGE_ERROR_TYPE);

    // ReferenceError
    FunctionType REFERENCE_ERROR_FUNCTION_TYPE =
        new ErrorFunctionType(this, "ReferenceError");
    REFERENCE_ERROR_FUNCTION_TYPE.setPrototypeBasedOn(ERROR_TYPE);
    registerNativeType(
        JSTypeNative.REFERENCE_ERROR_FUNCTION_TYPE,
        REFERENCE_ERROR_FUNCTION_TYPE);

    ObjectType REFERENCE_ERROR_TYPE =
        REFERENCE_ERROR_FUNCTION_TYPE.getInstanceType();
    registerNativeType(JSTypeNative.REFERENCE_ERROR_TYPE, REFERENCE_ERROR_TYPE);

    // SyntaxError
    FunctionType SYNTAX_ERROR_FUNCTION_TYPE =
        new ErrorFunctionType(this, "SyntaxError");
    SYNTAX_ERROR_FUNCTION_TYPE.setPrototypeBasedOn(ERROR_TYPE);
    registerNativeType(
        JSTypeNative.SYNTAX_ERROR_FUNCTION_TYPE, SYNTAX_ERROR_FUNCTION_TYPE);

    ObjectType SYNTAX_ERROR_TYPE = SYNTAX_ERROR_FUNCTION_TYPE.getInstanceType();
    registerNativeType(JSTypeNative.SYNTAX_ERROR_TYPE, SYNTAX_ERROR_TYPE);

    // TypeError
    FunctionType TYPE_ERROR_FUNCTION_TYPE =
        new ErrorFunctionType(this, "TypeError");
    TYPE_ERROR_FUNCTION_TYPE.setPrototypeBasedOn(ERROR_TYPE);
    registerNativeType(
        JSTypeNative.TYPE_ERROR_FUNCTION_TYPE, TYPE_ERROR_FUNCTION_TYPE);

    ObjectType TYPE_ERROR_TYPE = TYPE_ERROR_FUNCTION_TYPE.getInstanceType();
    registerNativeType(JSTypeNative.TYPE_ERROR_TYPE, TYPE_ERROR_TYPE);

    // URIError
    FunctionType URI_ERROR_FUNCTION_TYPE =
        new ErrorFunctionType(this, "URIError");
    URI_ERROR_FUNCTION_TYPE.setPrototypeBasedOn(ERROR_TYPE);
    registerNativeType(
        JSTypeNative.URI_ERROR_FUNCTION_TYPE, URI_ERROR_FUNCTION_TYPE);

    ObjectType URI_ERROR_TYPE = URI_ERROR_FUNCTION_TYPE.getInstanceType();
    registerNativeType(JSTypeNative.URI_ERROR_TYPE, URI_ERROR_TYPE);

    // Number
    FunctionType NUMBER_OBJECT_FUNCTION_TYPE =
        new FunctionType(this, "Number", null,
            createArrowType(createOptionalParameters(ALL_TYPE), NUMBER_TYPE),
            null, null, true, true);
    ObjectType numberPrototype = NUMBER_OBJECT_FUNCTION_TYPE.getPrototype();
    registerNativeType(
        JSTypeNative.NUMBER_OBJECT_FUNCTION_TYPE, NUMBER_OBJECT_FUNCTION_TYPE);

    ObjectType NUMBER_OBJECT_TYPE =
        NUMBER_OBJECT_FUNCTION_TYPE.getInstanceType();
    registerNativeType(JSTypeNative.NUMBER_OBJECT_TYPE, NUMBER_OBJECT_TYPE);

    // RegExp
    FunctionType REGEXP_FUNCTION_TYPE =
      new FunctionType(this, "RegExp", null,
          createArrowType(createOptionalParameters(ALL_TYPE, ALL_TYPE)),
          null, null, true, true);
    REGEXP_FUNCTION_TYPE.getInternalArrowType().returnType =
        REGEXP_FUNCTION_TYPE.getInstanceType();

    ObjectType regexpPrototype = REGEXP_FUNCTION_TYPE.getPrototype();
    registerNativeType(JSTypeNative.REGEXP_FUNCTION_TYPE, REGEXP_FUNCTION_TYPE);

    ObjectType REGEXP_TYPE = REGEXP_FUNCTION_TYPE.getInstanceType();
    registerNativeType(JSTypeNative.REGEXP_TYPE, REGEXP_TYPE);

    // String
    FunctionType STRING_OBJECT_FUNCTION_TYPE =
        new FunctionType(this, "String", null,
            createArrowType(createOptionalParameters(ALL_TYPE), STRING_TYPE),
            null, null, true, true);
    ObjectType stringPrototype = STRING_OBJECT_FUNCTION_TYPE.getPrototype();
    registerNativeType(
        JSTypeNative.STRING_OBJECT_FUNCTION_TYPE, STRING_OBJECT_FUNCTION_TYPE);

    ObjectType STRING_OBJECT_TYPE =
        STRING_OBJECT_FUNCTION_TYPE.getInstanceType();
    registerNativeType(
        JSTypeNative.STRING_OBJECT_TYPE, STRING_OBJECT_TYPE);

    // (null,void)
    JSType NULL_VOID =
        createUnionType(NULL_TYPE, VOID_TYPE);
    registerNativeType(JSTypeNative.NULL_VOID, NULL_VOID);

    // (Object,string,number)
    JSType OBJECT_NUMBER_STRING =
        createUnionType(OBJECT_TYPE, NUMBER_TYPE, STRING_TYPE);
    registerNativeType(JSTypeNative.OBJECT_NUMBER_STRING, OBJECT_NUMBER_STRING);

    // (Object,string,number,boolean)
    JSType OBJECT_NUMBER_STRING_BOOLEAN =
        createUnionType(OBJECT_TYPE, NUMBER_TYPE, STRING_TYPE, BOOLEAN_TYPE);
    registerNativeType(JSTypeNative.OBJECT_NUMBER_STRING_BOOLEAN,
        OBJECT_NUMBER_STRING_BOOLEAN);

    // (string,number,boolean)
    JSType NUMBER_STRING_BOOLEAN =
        createUnionType(NUMBER_TYPE, STRING_TYPE, BOOLEAN_TYPE);
    registerNativeType(JSTypeNative.NUMBER_STRING_BOOLEAN,
        NUMBER_STRING_BOOLEAN);

    // (string,number)
    JSType NUMBER_STRING = createUnionType(NUMBER_TYPE, STRING_TYPE);
    registerNativeType(JSTypeNative.NUMBER_STRING, NUMBER_STRING);

    // Native object properties are filled in by externs...

    // (String, string)
    JSType STRING_VALUE_OR_OBJECT_TYPE =
        createUnionType(STRING_OBJECT_TYPE, STRING_TYPE);
    registerNativeType(
        JSTypeNative.STRING_VALUE_OR_OBJECT_TYPE, STRING_VALUE_OR_OBJECT_TYPE);

    // (Number, number)
    JSType NUMBER_VALUE_OR_OBJECT_TYPE =
        createUnionType(NUMBER_OBJECT_TYPE, NUMBER_TYPE);
    registerNativeType(
        JSTypeNative.NUMBER_VALUE_OR_OBJECT_TYPE, NUMBER_VALUE_OR_OBJECT_TYPE);

    // unknown function type, i.e. (?...) -> ?
    FunctionType U2U_FUNCTION_TYPE =
        createFunctionType(UNKNOWN_TYPE, true, UNKNOWN_TYPE);
    registerNativeType(JSTypeNative.U2U_FUNCTION_TYPE, U2U_FUNCTION_TYPE);

    // unknown constructor type, i.e. (?...) -> ? with the Unknown type
    // as instance type
    FunctionType U2U_CONSTRUCTOR_TYPE =
        // This is equivalent to
        // createConstructorType(UNKNOWN_TYPE, true, UNKNOWN_TYPE), but,
        // in addition, overrides getInstanceType() to return the NoObject type
        // instead of a new anonymous object.
        new FunctionType(this, "Function", null,
            createArrowType(
                createParametersWithVarArgs(UNKNOWN_TYPE),
                UNKNOWN_TYPE),
            UNKNOWN_TYPE, null, true, true) {
          private static final long serialVersionUID = 1L;

          @Override public FunctionType getConstructor() {
            return registry.getNativeFunctionType(
                JSTypeNative.FUNCTION_FUNCTION_TYPE);
          }
        };

    // The U2U_CONSTRUCTOR is weird, because it's the supertype of its
    // own constructor.
    registerNativeType(JSTypeNative.U2U_CONSTRUCTOR_TYPE, U2U_CONSTRUCTOR_TYPE);
    registerNativeType(
        JSTypeNative.FUNCTION_INSTANCE_TYPE, U2U_CONSTRUCTOR_TYPE);

    FUNCTION_FUNCTION_TYPE.setInstanceType(U2U_CONSTRUCTOR_TYPE);
    U2U_CONSTRUCTOR_TYPE.setImplicitPrototype(FUNCTION_PROTOTYPE);

    // least function type, i.e. (All...) -> NoType
    FunctionType LEAST_FUNCTION_TYPE =
        createNativeFunctionTypeWithVarArgs(NO_TYPE, ALL_TYPE);
    registerNativeType(JSTypeNative.LEAST_FUNCTION_TYPE, LEAST_FUNCTION_TYPE);

    // the 'this' object in the global scope
    FunctionType GLOBAL_THIS_CTOR =
        new FunctionType(this, "global this", null,
            createArrowType(createParameters(false, ALL_TYPE), NUMBER_TYPE),
            null, null, true, true);
    ObjectType GLOBAL_THIS = GLOBAL_THIS_CTOR.getInstanceType();
    registerNativeType(JSTypeNative.GLOBAL_THIS, GLOBAL_THIS);

    // greatest function type, i.e. (NoType...) -> All
    FunctionType GREATEST_FUNCTION_TYPE =
        createNativeFunctionTypeWithVarArgs(ALL_TYPE, NO_TYPE);
    registerNativeType(JSTypeNative.GREATEST_FUNCTION_TYPE,
        GREATEST_FUNCTION_TYPE);

    // Register the prototype property. See the comments below in
    // registerPropertyOnType about the bootstrapping process.
    registerPropertyOnType("prototype", OBJECT_FUNCTION_TYPE);
  }

  private void initializeRegistry() {
    register(getNativeType(JSTypeNative.ARRAY_TYPE));
    register(getNativeType(JSTypeNative.BOOLEAN_OBJECT_TYPE));
    register(getNativeType(JSTypeNative.BOOLEAN_TYPE));
    register(getNativeType(JSTypeNative.DATE_TYPE));
    register(getNativeType(JSTypeNative.NULL_TYPE));
    register(getNativeType(JSTypeNative.NULL_TYPE), "Null");
    register(getNativeType(JSTypeNative.NUMBER_OBJECT_TYPE));
    register(getNativeType(JSTypeNative.NUMBER_TYPE));
    register(getNativeType(JSTypeNative.OBJECT_TYPE));
    register(getNativeType(JSTypeNative.ERROR_TYPE));
    register(getNativeType(JSTypeNative.URI_ERROR_TYPE));
    register(getNativeType(JSTypeNative.EVAL_ERROR_TYPE));
    register(getNativeType(JSTypeNative.TYPE_ERROR_TYPE));
    register(getNativeType(JSTypeNative.RANGE_ERROR_TYPE));
    register(getNativeType(JSTypeNative.REFERENCE_ERROR_TYPE));
    register(getNativeType(JSTypeNative.SYNTAX_ERROR_TYPE));
    register(getNativeType(JSTypeNative.REGEXP_TYPE));
    register(getNativeType(JSTypeNative.STRING_OBJECT_TYPE));
    register(getNativeType(JSTypeNative.STRING_TYPE));
    register(getNativeType(JSTypeNative.VOID_TYPE));
    register(getNativeType(JSTypeNative.VOID_TYPE), "Undefined");
    register(getNativeType(JSTypeNative.VOID_TYPE), "void");
    register(getNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE), "Function");
  }

  private void register(JSType type) {
    register(type, type.toString());
  }

  private void register(JSType type, String name) {
    Preconditions.checkArgument(
        !name.contains("<"), "Type names cannot contain template annotations.");

    namesToTypes.put(name, type);

    // Add all the namespaces in which this name lives.
    while (name.indexOf('.') > 0) {
      name = name.substring(0, name.lastIndexOf('.'));
      namespaces.add(name);
    }
  }

  private void registerNativeType(JSTypeNative typeId, JSType type) {
    nativeTypes[typeId.ordinal()] = type;
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
    UnionTypeBuilder typeSet = typesIndexedByProperty.get(propertyName);
    if (typeSet == null) {
      typeSet = new UnionTypeBuilder(this, PROPERTY_CHECKING_UNION_SIZE);
      typesIndexedByProperty.put(propertyName, typeSet);
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
          eachRefTypeIndexedByProperty.get(propertyName);
      if (typeSet == null) {
        typeSet = Maps.newHashMap();
        eachRefTypeIndexedByProperty.put(propertyName, typeSet);
      }
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
    if (greatestSubtypeByProperty.containsKey(propertyName)) {
      return greatestSubtypeByProperty.get(propertyName)
          .getGreatestSubtype(type);
    }
    if (typesIndexedByProperty.containsKey(propertyName)) {
      JSType built = typesIndexedByProperty.get(propertyName).build();
      greatestSubtypeByProperty.put(propertyName, built);
      return built.getGreatestSubtype(type);
    }
    return getNativeType(NO_TYPE);
  }

  /**
   * Returns whether the given property can possibly be set on the given type.
   */
  public boolean canPropertyBeDefined(JSType type, String propertyName) {
    if (type.isStruct()) {
      // We are stricter about "struct" types and only allow access to
      // properties that to the best of our knowledge are available at creation
      // time and specifically not properties only defined on subtypes.
      return type.hasProperty(propertyName);
    } else {
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

            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Returns each type that has a property {@code propertyName} defined on it.
   *
   * Like most types in our type system, the collection of types returned
   * will be collapsed. This means that if a type is defined on
   * {@code Object} and on {@code Array}, it would be reasonable for this
   * method to return either {@code [Object, Array]} or just {@code [Object]}.
   */
  public Iterable<JSType> getTypesWithProperty(String propertyName) {
    if (typesIndexedByProperty.containsKey(propertyName)) {
      return typesIndexedByProperty.get(propertyName).getAlternates();
    } else {
      return ImmutableList.of();
    }
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
    List<ObjectType> stack = Lists.newArrayListWithExpectedSize(5);
    for (ObjectType current = a;
         current != null;
         current = current.getImplicitPrototype()) {
      stack.add(current);
    }
    return stack;
  }

  /**
   * Increments the current generation. Clients must call this in order to
   * move to the next generation of type resolution, allowing types to attempt
   * resolution again.
   */
  public void incrementGeneration() {
    for (NamedType type : resolvedNamedTypes.values()) {
      type.clearResolved();
    }
    unresolvedNamedTypes.putAll(resolvedNamedTypes);
    resolvedNamedTypes.clear();
  }

  boolean isLastGeneration() {
    return lastGeneration;
  }

  /**
   * Sets whether this is the last generation. In the last generation,
   * {@link NamedType} warns about unresolved types.
   */
  public void setLastGeneration(boolean lastGeneration) {
    this.lastGeneration = lastGeneration;
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
  public Collection<FunctionType> getDirectImplementors(
      ObjectType interfaceInstance) {
    return interfaceToImplementors.get(interfaceInstance.getReferenceName());
  }

  /**
   * Records declared global type names. This makes resolution faster
   * and more robust in the common case.
   *
   * @param name The name of the type to be recorded.
   * @param t The actual type being associated with the name.
   * @return True if this name is not already defined, false otherwise.
   */
  public boolean declareType(String name, JSType t) {
    if (namesToTypes.containsKey(name)) {
      return false;
    }
    register(t, name);
    return true;
  }

  /**
   * Overrides a declared global type name. Throws an exception if this
   * type name hasn't been declared yet.
   */
  public void overwriteDeclaredType(String name, JSType t) {
    Preconditions.checkState(namesToTypes.containsKey(name));
    register(t, name);
  }

  /**
   * Records a forward-declared type name. We will not emit errors if this
   * type name never resolves to anything.
   */
  public void forwardDeclareType(String name) {
    forwardDeclaredTypes.add(name);
  }

  /**
   * Whether this is a forward-declared type name.
   */
  public boolean isForwardDeclaredType(String name) {
    return forwardDeclaredTypes.contains(name);
  }

  /** Determines whether the given JS package exists. */
  public boolean hasNamespace(String name) {
    return namespaces.contains(name);
  }

  /**
   * Looks up a type by name.
   *
   * @param jsTypeName The name string.
   * @return the corresponding JSType object or {@code null} it cannot be found
   */
  public JSType getType(String jsTypeName) {
    // TODO(user): Push every local type name out of namesToTypes so that
    // NamedType#resolve is correct.
    TemplateType templateType = templateTypes.get(jsTypeName);
    if (templateType != null) {
      return templateType;
    }
    return namesToTypes.get(jsTypeName);
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
   * Looks up a type by name. To allow for forward references to types, an
   * unrecognized string has to be bound to a NamedType object that will be
   * resolved later.
   *
   * @param scope A scope for doing type name resolution.
   * @param jsTypeName The name string.
   * @param sourceName The name of the source file where this reference appears.
   * @param lineno The line number of the reference.
   * @return a NamedType if the string argument is not one of the known types,
   *     otherwise the corresponding JSType object.
   */
  public JSType getType(StaticScope<JSType> scope, String jsTypeName,
      String sourceName, int lineno, int charno) {
    // Resolve template type names
    JSType type = null;
    JSType thisType = null;
    if (scope != null && scope.getTypeOfThis() != null) {
      thisType = scope.getTypeOfThis().toObjectType();
    }
    if (thisType != null) {
      type = thisType.getTemplateTypeMap().getTemplateTypeKeyByName(jsTypeName);
      if (type != null) {
        Preconditions.checkState(type.isTemplateType(), "expected:" + type);
        return type;
      }
    }

    type = getType(jsTypeName);
    if (type == null) {
      // TODO(user): Each instance should support named type creation using
      // interning.
      NamedType namedType = createNamedType(jsTypeName, sourceName, lineno, charno);
      unresolvedNamedTypes.put(scope, namedType);
      type = namedType;
    }
    return type;
  }

  /**
   * Flushes out the current resolved and unresolved Named Types from
   * the type registry.  This is intended to be used ONLY before a
   * compile is run.
   */
  public void clearNamedTypes() {
    resolvedNamedTypes.clear();
    unresolvedNamedTypes.clear();
  }

  /**
   * Resolve all the unresolved types in the given scope.
   */
  public void resolveTypesInScope(StaticScope<JSType> scope) {
    for (NamedType type : unresolvedNamedTypes.get(scope)) {
      type.resolve(reporter, scope);
    }

    resolvedNamedTypes.putAll(scope, unresolvedNamedTypes.removeAll(scope));

    if (scope != null && scope.getParentScope() == null) {
      // By default, the global "this" type is just an anonymous object.
      // If the user has defined a Window type, make the Window the
      // implicit prototype of "this".
      PrototypeObjectType globalThis = (PrototypeObjectType) getNativeType(
          JSTypeNative.GLOBAL_THIS);
      JSType windowType = getType("Window");
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
      return shouldTolerateUndefinedValues()
        ? createOptionalNullableType(type)
        : createNullableType(type);
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
    UnionTypeBuilder builder = new UnionTypeBuilder(this);
    for (JSType type : variants) {
      builder.addAlternate(type);
    }
    return builder.build();
  }

  /**
   * Creates a union type whose variants are the built-in types specified
   * by the arguments.
   */
  public JSType createUnionType(JSTypeNative... variants) {
    UnionTypeBuilder builder = new UnionTypeBuilder(this);
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
   * Creates a function type. The last parameter type of the function is
   * considered a variable length argument.
   *
   * @param returnType the function's return type
   * @param parameterTypes the parameters' types
   */
  public FunctionType createFunctionTypeWithVarArgs(
      JSType returnType, List<JSType> parameterTypes) {
    return createFunctionType(
        returnType, createParametersWithVarArgs(parameterTypes));
  }

  /**
   * Creates a function type.
   *
   * @param returnType the function's return type
   * @param parameterTypes the parameters' types
   */
  public FunctionType createFunctionType(
      JSType returnType, List<JSType> parameterTypes) {
    return createFunctionType(returnType, createParameters(parameterTypes));
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
   * Creates a function type which can act as a constructor.
   *
   * @param returnType the function's return type
   * @param parameterTypes the parameters' types
   */
  public FunctionType createConstructorType(
      JSType returnType, JSType... parameterTypes) {
    return createConstructorType(
        null, null, createParameters(parameterTypes), returnType, null);
  }

  /**
   * Creates a function type which can act as a constructor. The last
   * parameter type of the constructor is considered a variable length argument.
   *
   * @param returnType the function's return type
   * @param parameterTypes the parameters' types
   */
  private FunctionType createConstructorTypeWithVarArgs(
      JSType returnType, JSType... parameterTypes) {
    return createConstructorType(
        null, null, createParametersWithVarArgs(parameterTypes), returnType,
        null);
  }

  /**
   * Creates a function type in which {@code this} refers to an object instance.
   *
   * @param instanceType the type of {@code this}
   * @param returnType the function's return type
   * @param parameterTypes the parameters' types
   */
  public JSType createFunctionType(ObjectType instanceType,
      JSType returnType, List<JSType> parameterTypes) {
    return new FunctionBuilder(this)
        .withParamsNode(createParameters(parameterTypes))
        .withReturnType(returnType)
        .withTypeOfThis(instanceType)
        .build();
  }

  /**
   * Creates a function type in which {@code this} refers to an object instance.
   * The last parameter type of the function is considered a variable length
   * argument.
   *
   * @param instanceType the type of {@code this}
   * @param returnType the function's return type
   * @param parameterTypes the parameters' types
   */
  public JSType createFunctionTypeWithVarArgs(ObjectType instanceType,
      JSType returnType, List<JSType> parameterTypes) {
    return new FunctionBuilder(this)
        .withParamsNode(createParametersWithVarArgs(parameterTypes))
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
  public Node createParameters(List<JSType> parameterTypes) {
    return createParameters(
        parameterTypes.toArray(new JSType[parameterTypes.size()]));
  }

  /**
   * Creates a tree hierarchy representing a typed argument list. The last
   * parameter type is considered a variable length argument.
   *
   * @param parameterTypes the parameter types. The last element of this array
   *     is considered a variable length argument.
   * @return a tree hierarchy representing a typed argument list.
   */
  public Node createParametersWithVarArgs(List<JSType> parameterTypes) {
    return createParametersWithVarArgs(
        parameterTypes.toArray(new JSType[parameterTypes.size()]));
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
   * Creates a tree hierarchy representing a typed argument list.
   *
   * @param lastVarArgs whether the last type should considered as a variable
   *     length argument.
   * @param parameterTypes the parameter types. The last element of this array
   *     is considered a variable length argument is {@code lastVarArgs} is
   *     {@code true}.
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
   * Creates a function type.
   * @param returnType the function's return type
   * @param lastVarArgs whether the last parameter type should be considered as
   * an extensible var_args parameter
   * @param parameterTypes the parameters' types
   */
  public FunctionType createFunctionType(JSType returnType,
      boolean lastVarArgs, JSType... parameterTypes) {
    if (lastVarArgs) {
      return createFunctionTypeWithVarArgs(returnType, parameterTypes);
    } else {
      return createFunctionType(returnType, parameterTypes);
    }
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

  /**
   * Creates a new function type based on an existing function type but
   * with a new {@code this} type.
   * @param existingFunctionType the existing function type.
   * @param thisType the new this type.
   */
  public FunctionType createFunctionTypeWithNewThisType(
      FunctionType existingFunctionType, ObjectType thisType) {
    return new FunctionBuilder(this)
        .copyFromOtherFunction(existingFunctionType)
        .withTypeOfThis(thisType)
        .build();
  }

  /**
   * @param parameters the function's parameters or {@code null}
   *        to indicate that the parameter types are unknown.
   * @param returnType the function's return type or {@code null} to indicate
   *        that the return type is unknown.
   */
  public FunctionType createFunctionType(
      JSType returnType, Node parameters) {
    return new FunctionBuilder(this)
        .withParamsNode(parameters)
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

  /**
   * Creates a function type which can act as a constructor.
   * @param returnType the function's return type
   * @param lastVarArgs whether the last parameter type should be considered as
   * an extensible var_args parameter
   * @param parameterTypes the parameters' types
   */
  public FunctionType createConstructorType(JSType returnType,
      boolean lastVarArgs, JSType... parameterTypes) {
    if (lastVarArgs) {
      return createConstructorTypeWithVarArgs(returnType, parameterTypes);
    } else {
      return createConstructorType(returnType, parameterTypes);
    }
  }

  /**
   * Create an object type.
   */
  public ObjectType createObjectType(ObjectType implicitPrototype) {
    return createObjectType(null, null, implicitPrototype);
  }

  /**
   * Creates a record type.
   */
  public RecordType createRecordType(Map<String, RecordProperty> properties) {
    return new RecordType(this, properties);
  }

  /**
   * Create an object type.
   */
  public ObjectType createObjectType(String name, Node n,
      ObjectType implicitPrototype) {
    return new PrototypeObjectType(this, name, implicitPrototype);
  }

  /**
   * Create an anonymous object type.
   * @param info Used to mark object literals as structs; can be {@code null}
   */
  public ObjectType createAnonymousObjectType(JSDocInfo info) {
    PrototypeObjectType type = new PrototypeObjectType(this, null, null);
    type.setPrettyPrint(true);
    type.setJSDocInfo(info);
    return type;
  }

  /**
   * Set the implicit prototype if it's possible to do so.
   * @return True if we were able to set the implicit prototype successfully,
   *     false if it was not possible to do so for some reason. There are
   *     a few different reasons why this could fail: for example, numbers
   *     can't be implicit prototypes, and we don't want to change the implicit
   *     prototype if other classes have already subclassed this one.
   */
  public boolean resetImplicitPrototype(
      JSType type, ObjectType newImplicitProto) {
    if (type instanceof PrototypeObjectType) {
      PrototypeObjectType poType = (PrototypeObjectType) type;
      poType.clearCachedValues();
      poType.setImplicitPrototype(newImplicitProto);
      return true;
    }
    return false;
  }

  /**
   * Create an anonymous object type for a native type.
   */
  ObjectType createNativeAnonymousObjectType() {
    PrototypeObjectType type =
        new PrototypeObjectType(this, null, null, true, null);
    type.setPrettyPrint(true);
    return type;
  }

  /**
   * Creates a constructor function type.
   * @param name the function's name or {@code null} to indicate that the
   *     function is anonymous.
   * @param source the node defining this function. Its type
   *     ({@link Node#getType()}) must be {@link Token#FUNCTION}.
   * @param parameters the function's parameters or {@code null}
   *     to indicate that the parameter types are unknown.
   * @param returnType the function's return type or {@code null} to indicate
   *     that the return type is unknown.
   * @param templateKeys the templatized types for the class.
   */
  public FunctionType createConstructorType(String name, Node source,
      Node parameters, JSType returnType, ImmutableList<TemplateType> templateKeys) {
    return new FunctionType(this, name, source,
        createArrowType(parameters, returnType), null,
        createTemplateTypeMap(templateKeys, null), true, false);
  }

  ImmutableList<TemplateType> createTemplateMapKeys(ImmutableList<String> keys) {
    ImmutableList.Builder<TemplateType> builder = ImmutableList.builder();
    if (keys != null) {
      for (String key : keys) {
        builder.add(new TemplateType(this, key));
      }
    }
    return builder.build();
  }

  /**
   * Creates an interface function type.
   * @param name the function's name
   * @param source the node defining this function. Its type
   *     ({@link Node#getType()}) must be {@link Token#FUNCTION}.
   * @param templateKeys the templatized types for the interface.
   */
  public FunctionType createInterfaceType(String name, Node source,
      ImmutableList<TemplateType> templateKeys) {
    return FunctionType.forInterface(this, name, source,
        createTemplateTypeMap(templateKeys, null));
  }

  public TemplateType createTemplateType(String name) {
    return new TemplateType(this, name);
  }

  /**
   * Creates a template type map from the specified list of template keys and
   * template value types.
   */
  public TemplateTypeMap createTemplateTypeMap(
      ImmutableList<TemplateType> templateKeys,
      ImmutableList<JSType> templateValues) {
    templateKeys = templateKeys == null ?
        ImmutableList.<TemplateType>of() : templateKeys;
    templateValues = templateValues == null ?
        ImmutableList.<JSType>of() : templateValues;

    return (templateKeys.isEmpty() && templateValues.isEmpty()) ?
        emptyTemplateTypeMap :
        new TemplateTypeMap(this, templateKeys, templateValues);
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
  public TemplatizedType createTemplatizedType(
      ObjectType baseType, JSType... templatizedTypes) {
    return createTemplatizedType(
        baseType, ImmutableList.copyOf(templatizedTypes));
  }

  /**
   * Creates a named type.
   */
  @VisibleForTesting
  public NamedType createNamedType(String reference,
      String sourceName, int lineno, int charno) {
    if (reference.endsWith(".")) {
      return new NamespaceType(this, reference, sourceName, lineno, charno);
    } else {
      return new NamedType(this, reference, sourceName, lineno, charno);
    }
  }

  /**
   * Identifies the name of a typedef or enum before we actually declare it.
   */
  public void identifyNonNullableName(String name) {
    Preconditions.checkNotNull(name);
    nonNullableTypeNames.add(name);
  }

  /**
   * Creates a JSType from the nodes representing a type.
   * @param n The node with type info.
   * @param sourceName The source file name.
   * @param scope A scope for doing type name lookups.
   */
  public JSType createFromTypeNodes(Node n, String sourceName,
      StaticScope<JSType> scope) {
    return createFromTypeNodesInternal(n, sourceName, scope);
  }

  private boolean hasTypeName(Node n) {
    if (n.getType() == Token.STRING) {
      return true;
    }

    for (Node child = n.getFirstChild();
         child != null; child = child.getNext()) {
      if (hasTypeName(child)) {
        return true;
      }
    }

    return false;
  }

  /** @see #createFromTypeNodes(Node, String, StaticScope) */
  private JSType createFromTypeNodesInternal(Node n, String sourceName,
      StaticScope<JSType> scope) {
    switch (n.getType()) {
      case Token.LC: // Record type.
        return createRecordTypeFromNodes(
            n.getFirstChild(), sourceName, scope);

      case Token.BANG: // Not nullable
        return createFromTypeNodesInternal(
            n.getFirstChild(), sourceName, scope)
            .restrictByNotNullOrUndefined();

      case Token.QMARK: // Nullable or unknown
        Node firstChild = n.getFirstChild();
        if (firstChild == null) {
          return getNativeType(UNKNOWN_TYPE);
        }
        return createDefaultObjectUnion(
            createFromTypeNodesInternal(
                firstChild, sourceName, scope));

      case Token.EQUALS: // Optional
        return createOptionalType(
            createFromTypeNodesInternal(
                n.getFirstChild(), sourceName, scope));

      case Token.ELLIPSIS: // Var args
        return createOptionalType(
            createFromTypeNodesInternal(
                n.getFirstChild(), sourceName, scope));

      case Token.STAR: // The AllType
        return getNativeType(ALL_TYPE);

      case Token.LB: // Array type
        // TODO(nicksantos): Enforce membership restrictions on the Array.
        return getNativeType(ARRAY_TYPE);

      case Token.PIPE: // Union type
        UnionTypeBuilder builder = new UnionTypeBuilder(this);
        for (Node child = n.getFirstChild(); child != null;
             child = child.getNext()) {
          builder.addAlternate(
              createFromTypeNodesInternal(child, sourceName, scope));
        }
        return builder.build();

      case Token.EMPTY: // When the return value of a function is not specified
        return getNativeType(UNKNOWN_TYPE);

      case Token.VOID: // Only allowed in the return value of a function.
        return getNativeType(VOID_TYPE);

      case Token.STRING:
        JSType namedType = getType(scope, n.getString(), sourceName,
            n.getLineno(), n.getCharno());
        if ((namedType instanceof ObjectType) &&
            !(namedType instanceof NamespaceType) &&
            !(nonNullableTypeNames.contains(n.getString()))) {
          Node typeList = n.getFirstChild();
          int nAllowedTypes =
              namedType.getTemplateTypeMap().numUnfilledTemplateKeys();
          if (typeList != null && nAllowedTypes > 0) {
            // Templatized types.
            ImmutableList.Builder<JSType> templateTypes =
                ImmutableList.builder();

            // Special case for Object, where Object.<X> implies Object.<?,X>.
            if (n.getString().equals("Object") &&
                typeList.getFirstChild() == typeList.getLastChild()) {
              templateTypes.add(getNativeType(UNKNOWN_TYPE));
            }

            int templateNodeIndex = 0;
            for (Node templateNode : typeList.getFirstChild().siblings()) {
              // Don't parse more templatized type nodes than the type can
              // accommodate. This is because some existing clients have
              // template annotations on non-templatized classes, for instance:
              //   goog.structs.Set.<SomeType>
              // The problem in these cases is that the previously-unparsed
              // SomeType is not actually a valid type. To prevent these clients
              // from seeing unknown type errors, we explicitly don't parse
              // these types.
              // TODO(user): Address this issue by removing bad template
              // annotations on non-templatized classes.
              if (++templateNodeIndex > nAllowedTypes) {
                break;
              }
              templateTypes.add(createFromTypeNodesInternal(
                  templateNode, sourceName, scope));
            }
            namedType = createTemplatizedType(
                (ObjectType) namedType, templateTypes.build());
            Preconditions.checkNotNull(namedType);
          }
          return createDefaultObjectUnion(namedType);
        } else {
          return namedType;
        }

      case Token.FUNCTION:
        ObjectType thisType = null;
        boolean isConstructor = false;
        Node current = n.getFirstChild();
        if (current.getType() == Token.THIS ||
            current.getType() == Token.NEW) {
          Node contextNode = current.getFirstChild();
          thisType =
              ObjectType.cast(
                  createFromTypeNodesInternal(
                      contextNode, sourceName, scope)
                  .restrictByNotNullOrUndefined());
          if (thisType == null) {
            reporter.warning(
                SimpleErrorReporter.getMessage0(
                    current.getType() == Token.THIS ?
                    "msg.jsdoc.function.thisnotobject" :
                    "msg.jsdoc.function.newnotobject"),
                sourceName,
                contextNode.getLineno(), contextNode.getCharno());
          }

          isConstructor = current.getType() == Token.NEW;
          current = current.getNext();
        }

        FunctionParamBuilder paramBuilder = new FunctionParamBuilder(this);

        if (current.getType() == Token.PARAM_LIST) {
          for (Node arg = current.getFirstChild(); arg != null;
               arg = arg.getNext()) {
            if (arg.getType() == Token.ELLIPSIS) {
              if (arg.getChildCount() == 0) {
                paramBuilder.addVarArgs(getNativeType(UNKNOWN_TYPE));
              } else {
                paramBuilder.addVarArgs(
                    createFromTypeNodesInternal(
                        arg.getFirstChild(), sourceName, scope));
              }
            } else {
              JSType type = createFromTypeNodesInternal(
                  arg, sourceName, scope);
              if (arg.getType() == Token.EQUALS) {
                boolean addSuccess = paramBuilder.addOptionalParams(type);
                if (!addSuccess) {
                  reporter.warning(
                      SimpleErrorReporter.getMessage0(
                          "msg.jsdoc.function.varargs"),
                      sourceName, arg.getLineno(), arg.getCharno());
                }
              } else {
                paramBuilder.addRequiredParams(type);
              }
            }
          }
          current = current.getNext();
        }

        JSType returnType =
            createFromTypeNodesInternal(current, sourceName, scope);

        return new FunctionBuilder(this)
            .withParams(paramBuilder)
            .withReturnType(returnType)
            .withTypeOfThis(thisType)
            .setIsConstructor(isConstructor)
            .build();
    }

    throw new IllegalStateException(
        "Unexpected node in type expression: " + n.toString());
  }

  /**
   * Creates a RecordType from the nodes representing said record type.
   * @param n The node with type info.
   * @param sourceName The source file name.
   * @param scope A scope for doing type name lookups.
   */
  private JSType createRecordTypeFromNodes(Node n, String sourceName,
      StaticScope<JSType> scope) {

    RecordTypeBuilder builder = new RecordTypeBuilder(this);

    // For each of the fields in the record type.
    for (Node fieldTypeNode = n.getFirstChild();
         fieldTypeNode != null;
         fieldTypeNode = fieldTypeNode.getNext()) {

      // Get the property's name.
      Node fieldNameNode = fieldTypeNode;
      boolean hasType = false;

      if (fieldTypeNode.getType() == Token.COLON) {
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
            fieldTypeNode.getLastChild(), sourceName, scope);
      } else {
        // Otherwise, the type is UNKNOWN.
        fieldType = getNativeType(JSTypeNative.UNKNOWN_TYPE);
      }

      // Add the property to the record.
      if (builder.addProperty(fieldName, fieldType, fieldNameNode) == null) {
        // Duplicate field name, warning and skip
        reporter.warning(
            "Duplicate record field " + fieldName,
            sourceName,
            n.getLineno(), fieldNameNode.getCharno());
      }
    }

    return builder.build();
  }

  /**
   * Sets the template type name.
   */
  public void setTemplateTypeNames(List<TemplateType> keys) {
    Preconditions.checkNotNull(keys);
    for (TemplateType key : keys) {
      templateTypes.put(key.getReferenceName(), key);
    }
  }

  /**
   * Clears the template type name.
   */
  public void clearTemplateTypeNames() {
    templateTypes.clear();
  }
}
