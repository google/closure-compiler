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

package com.google.javascript.jscomp;

import static com.google.common.base.MoreObjects.firstNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.parsing.TypeTransformationParser;
import com.google.javascript.jscomp.parsing.TypeTransformationParser.Keywords;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticTypedScope;
import com.google.javascript.rhino.jstype.StaticTypedSlot;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A class for processing type transformation expressions
 *
 * @author lpino@google.com (Luis Fernando Pino Duque)
 */
class TypeTransformation {
  private static final String VIRTUAL_FILE = "<TypeTransformation.java>";

  static final DiagnosticType UNKNOWN_TYPEVAR =
      DiagnosticType.warning("TYPEVAR_UNDEFINED",
          "Reference to an unknown type variable {0}");
  static final DiagnosticType UNKNOWN_STRVAR =
      DiagnosticType.warning("UNKNOWN_STRVAR",
          "Reference to an unknown string variable {0}");
  static final DiagnosticType UNKNOWN_TYPENAME =
      DiagnosticType.warning("TYPENAME_UNDEFINED",
          "Reference to an unknown type name {0}");
  static final DiagnosticType BASETYPE_INVALID =
      DiagnosticType.warning("BASETYPE_INVALID",
          "The type {0} cannot be templatized");
  static final DiagnosticType TEMPTYPE_INVALID =
      DiagnosticType.warning("TEMPTYPE_INVALID",
          "Expected templatized type in {0} found {1}");
  static final DiagnosticType INDEX_OUTOFBOUNDS =
      DiagnosticType.warning("INDEX_OUTOFBOUNDS",
      "Index out of bounds in templateTypeOf: expected a number less than {0}, found {1}");
  static final DiagnosticType DUPLICATE_VARIABLE =
      DiagnosticType.warning("DUPLICATE_VARIABLE",
          "The variable {0} is already defined");
  // This warning is never exercised.
  static final DiagnosticType UNKNOWN_NAMEVAR =
      DiagnosticType.warning("UNKNOWN_NAMEVAR",
          "Reference to an unknown name variable {0}");
  static final DiagnosticType RECTYPE_INVALID =
      DiagnosticType.warning("RECTYPE_INVALID",
          "The first parameter of a maprecord must be a record type, "
          + "found {0}");
  static final DiagnosticType MAPRECORD_BODY_INVALID =
      DiagnosticType.warning("MAPRECORD_BODY_INVALID",
          "The body of a maprecord function must evaluate to a record type or "
          + "a no type, found {0}");
  static final DiagnosticType VAR_UNDEFINED =
      DiagnosticType.warning("VAR_UNDEFINED",
          "Variable {0} is undefined in the scope");
  static final DiagnosticType INVALID_CTOR =
      DiagnosticType.warning("INVALID_CTOR",
          "Expected a constructor type, found {0}");
  static final DiagnosticType RECPARAM_INVALID =
      DiagnosticType.warning("RECPARAM_INVALID",
          "Expected a record type, found {0}");
  static final DiagnosticType PROPTYPE_INVALID =
      DiagnosticType.warning("PROPTYPE_INVALID",
          "Expected object type, found {0}");

  private final AbstractCompiler compiler;
  private final JSTypeRegistry registry;
  private final StaticTypedScope typeEnv;

  /**
   * A helper class for holding the information about the type variables
   * and the name variables in maprecord expressions
   */
  private static class NameResolver {
    ImmutableMap<String, JSType> typeVars;
    ImmutableMap<String, String> nameVars;

    NameResolver(ImmutableMap<String, JSType> typeVars, ImmutableMap<String, String> nameVars) {
      this.typeVars = typeVars;
      this.nameVars = nameVars;
    }
  }

  @SuppressWarnings("unchecked")
  TypeTransformation(AbstractCompiler compiler, StaticTypedScope typeEnv) {
    this.compiler = compiler;
    this.registry = compiler.getTypeRegistry();
    this.typeEnv = typeEnv;
  }

  private boolean isTypeVar(Node n) {
    return n.isName();
  }

  private boolean isTypeName(Node n) {
    return n.isString();
  }

  private boolean isBooleanOperation(Node n) {
    return n.isAnd() || n.isOr() || n.isNot();
  }

  private Keywords nameToKeyword(String s) {
    return TypeTransformationParser.Keywords.valueOf(s.toUpperCase());
  }

  private JSType getType(String typeName) {
    JSType type = registry.getType(typeEnv, typeName);
    if (type != null) {
      return type;
    }
    StaticTypedSlot slot = typeEnv.getSlot(typeName);
    type = slot != null ? slot.getType() : null;
    if (type != null) {
      if (type.isConstructor() || type.isInterface()) {
        return type.toMaybeFunctionType().getInstanceType().getRawType();
      }
      if (type.isEnumElementType()) {
        return type.getEnumeratedTypeOfEnumElement();
      }
      return type;
    }
    JSDocInfo jsdoc = slot == null ? null : slot.getJSDocInfo();
    if (jsdoc != null && jsdoc.hasTypedefType()) {
      return this.registry.evaluateTypeExpression(jsdoc.getTypedefType(), typeEnv);
    }
    return null;
  }

  private JSType getUnknownType() {
    return registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE);
  }

  private JSType getNoType() {
    return registry.getNativeObjectType(JSTypeNative.NO_TYPE);
  }

  private JSType getAllType() {
    return registry.getNativeType(JSTypeNative.ALL_TYPE);
  }

  private JSType getObjectType() {
    return registry.getNativeType(JSTypeNative.OBJECT_TYPE);
  }

  private JSType createUnionType(JSType[] variants) {
    return registry.createUnionType(Arrays.asList(variants));
  }

  private JSType createTemplatizedType(ObjectType baseType, JSType[] params) {
    return registry.instantiateGenericType(baseType, ImmutableList.copyOf(params));
  }

  private JSType createRecordType(ImmutableMap<String, JSType> props) {
    return this.registry.createRecordType(props);
  }

  private void reportWarning(Node n, DiagnosticType msg, String... param) {
    compiler.report(JSError.make(n, msg, param));
  }

  private <T> ImmutableMap<String, T> addNewEntry(
      ImmutableMap<String, T> map, String name, T type) {
    return new ImmutableMap.Builder<String, T>()
        .putAll(map)
        .put(name, type)
        .build();
  }

  private String getFunctionParameter(Node n, int i) {
    Preconditions.checkArgument(n.isFunction(), "Expected a function node, found %s", n);
    return n.getSecondChild().getChildAtIndex(i).getString();
  }

  private String getCallName(Node n) {
    Preconditions.checkArgument(n.isCall(), "Expected a call node, found %s", n);
    return n.getFirstChild().getString();
  }

  private Node getCallArgument(Node n, int i) {
    Preconditions.checkArgument(n.isCall(), "Expected a call node, found %s", n);
    return n.getChildAtIndex(i + 1);
  }

  private int getCallParamCount(Node n) {
    Preconditions.checkArgument(n.isCall(), "Expected a call node, found %s", n);
    return n.getChildCount() - 1;
  }

  // TODO(dimvar): rewrite the uses of this method to use siblings() and delete it.
  // Copying is unnecessarily inefficient.
  private ImmutableList<Node> getCallParams(Node n) {
    Preconditions.checkArgument(n.isCall(), "Expected a call node, found %s", n);
    ImmutableList.Builder<Node> builder = new ImmutableList.Builder<>();
    for (int i = 0; i < getCallParamCount(n); i++) {
      builder.add(getCallArgument(n, i));
    }
    return builder.build();
  }

  private Node getComputedPropValue(Node n) {
    Preconditions.checkArgument(
        n.isComputedProp(), "Expected a computed property node, found %s", n);
    return n.getSecondChild();
  }

  private String getComputedPropName(Node n) {
    Preconditions.checkArgument(
        n.isComputedProp(), "Expected a computed property node, found %s", n);
    return n.getFirstChild().getString();
  }

  /** Evaluates the type transformation expression and returns the resulting type.
   *
   * @param ttlAst The node representing the type transformation expression
   * @param typeVars The environment containing the information about the type variables
   * @return JSType The resulting type after the transformation
   */
  JSType eval(Node ttlAst, ImmutableMap<String, JSType> typeVars) {
    return eval(ttlAst, typeVars, ImmutableMap.of());
  }

  /** Evaluates the type transformation expression and returns the resulting type.
   *
   * @param ttlAst The node representing the type transformation expression
   * @param typeVars The environment containing the information about the type variables
   * @param nameVars The environment containing the information about the name variables
   * @return JSType The resulting type after the transformation
   */
  @SuppressWarnings("unchecked")
  @VisibleForTesting
  JSType eval(Node ttlAst, ImmutableMap<String, JSType> typeVars,
      ImmutableMap<String, String> nameVars) {
    JSType result = evalInternal(ttlAst, new NameResolver(typeVars, nameVars));
    return result.isEmptyType() ? getUnknownType() : result;
  }

  private JSType evalInternal(Node ttlAst, NameResolver nameResolver) {
    if (isTypeName(ttlAst)) {
      return evalTypeName(ttlAst);
    }
    if (isTypeVar(ttlAst)) {
      return evalTypeVar(ttlAst, nameResolver);
    }
    String name = getCallName(ttlAst);
    Keywords keyword = nameToKeyword(name);
    switch (keyword.kind) {
      case TYPE_CONSTRUCTOR:
        return evalTypeExpression(ttlAst, nameResolver);
      case OPERATION:
        return evalOperationExpression(ttlAst, nameResolver);
      default:
        throw new IllegalStateException(
            "Could not evaluate the type transformation expression");
    }
  }

  private JSType evalOperationExpression(Node ttlAst, NameResolver nameResolver) {
    String name = getCallName(ttlAst);
    Keywords keyword = nameToKeyword(name);
    switch (keyword) {
      case COND:
        return evalConditional(ttlAst, nameResolver);
      case MAPUNION:
        return evalMapunion(ttlAst, nameResolver);
      case MAPRECORD:
        return evalMaprecord(ttlAst, nameResolver);
      case TYPEOFVAR:
        return evalTypeOfVar(ttlAst);
      case INSTANCEOF:
        return evalInstanceOf(ttlAst, nameResolver);
      case PRINTTYPE:
        return evalPrintType(ttlAst, nameResolver);
      case PROPTYPE:
        return evalPropType(ttlAst, nameResolver);
      default:
        throw new IllegalStateException("Invalid type transformation operation");
    }
  }

  private JSType evalTypeExpression(Node ttlAst, NameResolver nameResolver) {
    String name = getCallName(ttlAst);
    Keywords keyword = nameToKeyword(name);
    switch (keyword) {
      case TYPE:
        return evalTemplatizedType(ttlAst, nameResolver);
      case UNION:
        return evalUnionType(ttlAst, nameResolver);
      case NONE:
        return getNoType();
      case ALL:
        return getAllType();
      case UNKNOWN:
         return getUnknownType();
      case RAWTYPEOF:
        return evalRawTypeOf(ttlAst, nameResolver);
      case TEMPLATETYPEOF:
        return evalTemplateTypeOf(ttlAst, nameResolver);
      case RECORD:
        return evalRecordType(ttlAst, nameResolver);
      case TYPEEXPR:
        return evalNativeTypeExpr(ttlAst);
      default:
        throw new IllegalStateException("Invalid type expression");
    }
  }

  private JSType evalTypeName(Node ttlAst) {
    String typeName = ttlAst.getString();
    JSType resultingType = getType(typeName);
    // If the type name is not defined then return UNKNOWN and report a warning
    if (resultingType == null) {
      reportWarning(ttlAst, UNKNOWN_TYPENAME, typeName);
      return getUnknownType();
    }
    return resultingType;
  }

  private JSType evalTemplatizedType(Node ttlAst, NameResolver nameResolver) {
    ImmutableList<Node> params = getCallParams(ttlAst);
    JSType firstParam = evalInternal(params.get(0), nameResolver);
    if (firstParam.isFullyInstantiated()) {
      reportWarning(ttlAst, BASETYPE_INVALID, firstParam.toString());
      return getUnknownType();
    }
    // TODO(lpino): Check that the number of parameters correspond with the
    // number of template types that the base type can take when creating
    // a templatized type. For instance, if the base type is Array then there
    // must be just one parameter.
    JSType[] templatizedTypes = new JSType[params.size() - 1];
    for (int i = 0; i < templatizedTypes.length; i++) {
      templatizedTypes[i] = evalInternal(params.get(i + 1), nameResolver);
    }
    ObjectType baseType = firstParam.toMaybeObjectType();
    return createTemplatizedType(baseType, templatizedTypes);
  }

  private JSType evalTypeVar(Node ttlAst, NameResolver nameResolver) {
    String typeVar = ttlAst.getString();
    JSType resultingType = nameResolver.typeVars.get(typeVar);
    // If the type variable is not defined then return UNKNOWN and report a warning
    if (resultingType == null) {
      reportWarning(ttlAst, UNKNOWN_TYPEVAR, typeVar);
      return getUnknownType();
    }
    return resultingType;
  }

  private JSType evalUnionType(Node ttlAst, NameResolver nameResolver) {
    // Get the parameters of the union
    ImmutableList<Node> params = getCallParams(ttlAst);
    int paramCount = params.size();
    // Create an array of types after evaluating each parameter
    JSType[] basicTypes = new JSType[paramCount];
    for (int i = 0; i < paramCount; i++) {
      basicTypes[i] = evalInternal(params.get(i), nameResolver);
    }
    return createUnionType(basicTypes);
  }

  private JSType[] evalTypeParams(Node ttlAst, NameResolver nameResolver) {
    ImmutableList<Node> params = getCallParams(ttlAst);
    int paramCount = params.size();
    JSType[] result = new JSType[paramCount];
    for (int i = 0; i < paramCount; i++) {
      result[i] = evalInternal(params.get(i), nameResolver);
    }
    return result;
  }

  private String evalString(Node ttlAst, NameResolver nameResolver) {
    if (ttlAst.isName()) {
      // Return the empty string if the name variable cannot be resolved
      if (!nameResolver.nameVars.containsKey(ttlAst.getString())) {
        reportWarning(ttlAst, UNKNOWN_STRVAR, ttlAst.getString());
        return "";
      }
      return nameResolver.nameVars.get(ttlAst.getString());
    }
    return ttlAst.getString();
  }

  private String[] evalStringParams(Node ttlAst, NameResolver nameResolver) {
    ImmutableList<Node> params = getCallParams(ttlAst);
    int paramCount = params.size();
    String[] result = new String[paramCount];
    for (int i = 0; i < paramCount; i++) {
      result[i] = evalString(params.get(i), nameResolver);
    }
    return result;
  }

  private boolean evalTypePredicate(Node ttlAst, NameResolver nameResolver) {
    JSType[] params = evalTypeParams(ttlAst, nameResolver);
    String name = getCallName(ttlAst);
    Keywords keyword = nameToKeyword(name);
    JSType type = params[0];
    switch (keyword) {
      case EQ:
        return type.isEquivalentTo(params[1]);
      case SUB:
        return type.isSubtypeOf(params[1]);
      case ISCTOR:
        return type.isConstructor();
      case ISTEMPLATIZED:
        return type.isObjectType() && type.toMaybeObjectType().isGenericObjectType()
            && type.isPartiallyInstantiated();
      case ISRECORD:
        return type.isRecordType();
      case ISUNKNOWN:
        return type.isUnknownType();
      default:
        throw new IllegalStateException(
            "Invalid type predicate in the type transformation");
    }
  }

  private boolean evalStringPredicate(Node ttlAst,
      NameResolver nameResolver) {
    String[] params = evalStringParams(ttlAst, nameResolver);
    // If any of the parameters evaluates to the empty string then they were
    // not resolved by the name resolver. In this case we always return false.
    for (int i = 0; i < params.length; i++) {
      if (params[i].isEmpty()) {
        return false;
      }
    }
    String name = getCallName(ttlAst);
    Keywords keyword = nameToKeyword(name);
    switch (keyword) {
      case STREQ:
        return params[0].equals(params[1]);
      default:
        throw new IllegalStateException(
            "Invalid string predicate in the type transformation");
    }
  }

  private boolean evalTypevarPredicate(Node ttlAst, NameResolver nameResolver) {
    String name = getCallName(ttlAst);
    Keywords keyword = nameToKeyword(name);
    switch (keyword) {
      case ISDEFINED:
        return nameResolver.typeVars.containsKey(getCallArgument(ttlAst, 0).getString());
      default:
        throw new IllegalStateException(
            "Invalid typevar predicate in the type transformation");
    }
  }

   private boolean evalBooleanOperation(Node ttlAst, NameResolver nameResolver) {
     boolean param0 = evalBoolean(ttlAst.getFirstChild(), nameResolver);
     if (ttlAst.isNot()) {
       return !param0;
     }
     if (ttlAst.isAnd()) {
       return param0 && evalBoolean(ttlAst.getLastChild(), nameResolver);
     }
     if (ttlAst.isOr()) {
       return param0 || evalBoolean(ttlAst.getLastChild(), nameResolver);
     }
     throw new IllegalStateException(
         "Invalid boolean predicate in the type transformation");
  }

  private boolean evalBoolean(Node ttlAst, NameResolver nameResolver) {
    if (isBooleanOperation(ttlAst)) {
      return evalBooleanOperation(ttlAst, nameResolver);
    }
    String name = getCallName(ttlAst);
    Keywords keyword = nameToKeyword(name);
    switch (keyword.kind) {
      case STRING_PREDICATE:
        return evalStringPredicate(ttlAst, nameResolver);
      case TYPE_PREDICATE:
        return evalTypePredicate(ttlAst, nameResolver);
      case TYPEVAR_PREDICATE:
        return evalTypevarPredicate(ttlAst, nameResolver);
      default:
        throw new IllegalStateException(
            "Invalid boolean predicate in the type transformation");
    }
  }

  private JSType evalConditional(Node ttlAst, NameResolver nameResolver) {
    ImmutableList<Node> params = getCallParams(ttlAst);
    if (evalBoolean(params.get(0), nameResolver)) {
      return evalInternal(params.get(1), nameResolver);
    } else {
      return evalInternal(params.get(2), nameResolver);
    }
  }

  private JSType evalMapunion(Node ttlAst, NameResolver nameResolver) {
    ImmutableList<Node> params = getCallParams(ttlAst);
    Node unionParam = params.get(0);
    Node mapFunction = params.get(1);
    String paramName = getFunctionParameter(mapFunction, 0);

    // The mapunion variable must not be defined in the environment
    if (nameResolver.typeVars.containsKey(paramName)) {
      reportWarning(ttlAst, DUPLICATE_VARIABLE, paramName);
      return getUnknownType();
    }

    Node mapFunctionBody = NodeUtil.getFunctionBody(mapFunction);
    JSType unionType = evalInternal(unionParam, nameResolver);
    // If the first parameter does not correspond to a union type then
    // consider it as a union with a single type and evaluate
    if (!unionType.isUnionType()) {
      NameResolver newNameResolver = new NameResolver(
          addNewEntry(nameResolver.typeVars, paramName, unionType),
          nameResolver.nameVars);
      return evalInternal(mapFunctionBody, newNameResolver);
    }

    // Otherwise obtain the elements in the union type. Note that the block
    // above guarantees the casting to be safe
    Collection<JSType> unionElms = ImmutableList.copyOf(unionType.getUnionMembers());
    // Evaluate the map function body using each element in the union type
    int unionSize = unionElms.size();
    JSType[] newUnionElms = new JSType[unionSize];
    int i = 0;
    for (JSType elm : unionElms) {
      NameResolver newNameResolver = new NameResolver(
          addNewEntry(nameResolver.typeVars, paramName, elm),
          nameResolver.nameVars);
      newUnionElms[i] = evalInternal(mapFunctionBody, newNameResolver);
      i++;
    }

    return createUnionType(newUnionElms);
  }

  private JSType evalRawTypeOf(Node ttlAst, NameResolver nameResolver) {
    ImmutableList<Node> params = getCallParams(ttlAst);
    JSType type = evalInternal(params.get(0), nameResolver);
    if (!type.isGenericObjectType()) {
      reportWarning(ttlAst, TEMPTYPE_INVALID, "rawTypeOf", type.toString());
      return getUnknownType();
    }
    return type.toMaybeObjectType().getRawType();
  }

  private JSType evalTemplateTypeOf(Node ttlAst, NameResolver nameResolver) {
    ImmutableList<Node> params = getCallParams(ttlAst);
    JSType type = evalInternal(params.get(0), nameResolver);
    if (!type.isGenericObjectType()) {
      reportWarning(ttlAst, TEMPTYPE_INVALID, "templateTypeOf", type.toString());
      return getUnknownType();
    }
    int index = (int) params.get(1).getDouble();
    ImmutableList<? extends JSType> templateTypes = type.toMaybeObjectType().getTemplateTypes();
    if (index >= templateTypes.size()) {
      reportWarning(ttlAst, INDEX_OUTOFBOUNDS,
          Integer.toString(templateTypes.size()), Integer.toString(index));
      return getUnknownType();
    }
    return templateTypes.get(index);
  }

  private JSType evalRecord(Node record, NameResolver nameResolver) {
    Map<String, JSType> props = new LinkedHashMap<>();
    for (Node propNode : record.children()) {
      // If it is a computed property then find the property name using the resolver
      if (propNode.isComputedProp()) {
        String compPropName = getComputedPropName(propNode);
        // If the name does not exist then report a warning
        if (!nameResolver.nameVars.containsKey(compPropName)) {
          reportWarning(record, UNKNOWN_NAMEVAR, compPropName);
          return getUnknownType();
        }
        // Otherwise add the property
        Node propValue = getComputedPropValue(propNode);
        String resolvedName = nameResolver.nameVars.get(compPropName);
        JSType resultingType = evalInternal(propValue, nameResolver);
        props.put(resolvedName, resultingType);
      } else {
        String propName = propNode.getString();
        JSType resultingType = evalInternal(propNode.getFirstChild(),
            nameResolver);
        props.put(propName, resultingType);
      }
    }
    return this.registry.createRecordType(props);
  }

  private JSType evalRecordParam(Node ttlAst, NameResolver nameResolver) {
    if (ttlAst.isObjectLit()) {
      return evalRecord(ttlAst, nameResolver);
    }
    // The parameter of record can be a type transformation expression
    return evalInternal(ttlAst, nameResolver);
  }

  private JSType evalRecordType(Node ttlAst, NameResolver nameResolver) {
    int paramCount = getCallParamCount(ttlAst);
    ImmutableList.Builder<ObjectType> recTypesBuilder = new ImmutableList.Builder<>();
    for (int i = 0; i < paramCount; i++) {
      JSType type = evalRecordParam(getCallArgument(ttlAst, i), nameResolver);
      // Check that each parameter evaluates to an object
      ObjectType objType = type.toMaybeObjectType();
      if (objType == null || objType.isUnknownType()) {
        reportWarning(ttlAst, RECPARAM_INVALID, type.toString());
        return getUnknownType();
      }
      JSType recType = this.registry.buildRecordTypeFromObject(objType);
      if (!recType.isEquivalentTo(getObjectType())) {
        recTypesBuilder.add(recType.toMaybeObjectType());
      }
    }
    return joinRecordTypes(recTypesBuilder.build());
  }

  private void putNewPropInPropertyMap(Map<String, JSType> props,
      String newPropName, JSType newPropValue) {
    // TODO(lpino): Decide if the best strategy is to collapse the properties
    // to a union type or not. So far, new values replace the old ones except
    // if they are two record types in which case the properties are joined
    // together

    // Three cases:
    // (i) If the key does not exist then add it to the map with the new value
    // (ii) If the key to be added already exists in the map and the new value
    // is not a record type then the current value is replaced with the new one
    // (iii) If the new value is a record type and the current is not then
    // the current value is replaced with the new one
    if (!props.containsKey(newPropName)
        || !newPropValue.isRecordType()
        || !props.get(newPropName).isRecordType()) {
      props.put(newPropName, newPropValue);
      return;
    }
    // Otherwise join the current value with the new one since both are records
    props.put(newPropName,
        joinRecordTypes(ImmutableList.of(
            (ObjectType) props.get(newPropName),
            (ObjectType) newPropValue)));
  }

  /**
   * Merges a list of record types.
   * Example
   * {r:{s:string, n:number}} and {a:boolean}
   * is transformed into {r:{s:string, n:number}, a:boolean}
   */
  private JSType joinRecordTypes(ImmutableList<ObjectType> recTypes) {
    Map<String, JSType> props = new LinkedHashMap<>();
    for (ObjectType recType : recTypes) {
      for (String newPropName : recType.getOwnPropertyNames()) {
        JSType newPropValue = recType.getPropertyType(newPropName);
        // Put the new property depending if it already exists in the map
        putNewPropInPropertyMap(props, newPropName, newPropValue);
      }
    }
    return createRecordType(ImmutableMap.copyOf(props));
  }

  private JSType evalMaprecord(Node ttlAst, NameResolver nameResolver) {
    Node recordNode = ttlAst.getSecondChild();
    Node mapFunction = ttlAst.getChildAtIndex(2);
    JSType type = evalInternal(recordNode, nameResolver);

    // If it is an empty record type (Object) then return
    if (type.isEquivalentTo(getObjectType())) {
      return getObjectType();
    }

    // The parameter must be a valid record type
    if (!type.isRecordType()) {
      // TODO(lpino): Decide how to handle non-record types
      reportWarning(recordNode, RECTYPE_INVALID, type.toString());
      return getUnknownType();
    }

    ObjectType objtype = type.toMaybeObjectType();
    // Fetch the information of the map function
    String paramKey = getFunctionParameter(mapFunction, 0);
    String paramValue = getFunctionParameter(mapFunction, 1);

    // The maprecord variables must not be defined in the environment
    if (nameResolver.nameVars.containsKey(paramKey)) {
      reportWarning(ttlAst, DUPLICATE_VARIABLE, paramKey);
      return getUnknownType();
    }
    if (nameResolver.typeVars.containsKey(paramValue)) {
      reportWarning(ttlAst, DUPLICATE_VARIABLE, paramValue);
      return getUnknownType();
    }

    // Compute the new properties using the map function
    Node mapFnBody = NodeUtil.getFunctionBody(mapFunction);
    Map<String, JSType> newProps = new LinkedHashMap<>();
    for (String propName : objtype.getOwnPropertyNames()) {
      // The value of the current property
      JSType propValue = objtype.getPropertyType(propName);

      // Evaluate the map function body with paramValue and paramKey replaced
      // by the values of the current property
      NameResolver newNameResolver = new NameResolver(
          addNewEntry(nameResolver.typeVars, paramValue, propValue),
          addNewEntry(nameResolver.nameVars, paramKey, propName));
      JSType body = evalInternal(mapFnBody, newNameResolver);

      // If the body returns unknown then the whole expression returns unknown
      if (body.isUnknownType()) {
        return getUnknownType();
      }

      // Skip the property when the body evaluates to NO_TYPE
      // or the empty record (Object)
      if (body.isEmptyType() || body.isEquivalentTo(getObjectType())) {
        continue;
      }

      // Otherwise the body must evaluate to a record type
      if (!body.isRecordType()) {
        reportWarning(ttlAst, MAPRECORD_BODY_INVALID, body.toString());
        return getUnknownType();
      }

      // Add the properties of the resulting record type to the original one
      ObjectType bodyAsObj = body.toMaybeObjectType();
      for (String newPropName : bodyAsObj.getOwnPropertyNames()) {
        JSType newPropValue = bodyAsObj.getPropertyType(newPropName);
        // If the key already exists then we have to mix it with the current property value
        putNewPropInPropertyMap(newProps, newPropName, newPropValue);
      }
    }
    return createRecordType(ImmutableMap.copyOf(newProps));
  }

  private JSType evalTypeOfVar(Node ttlAst) {
    String name = getCallArgument(ttlAst, 0).getString();
    StaticTypedSlot slot = typeEnv.getSlot(name);
    JSType type = slot != null ? slot.getType() : null;
    if (type == null) {
      reportWarning(ttlAst, VAR_UNDEFINED, name);
      return getUnknownType();
    }
    return type;
  }

  private JSType evalInstanceOf(Node ttlAst, NameResolver nameResolver) {
    JSType type = evalInternal(getCallArgument(ttlAst, 0), nameResolver);
    if (type.isUnknownType() || !type.isConstructor()) {
      reportWarning(ttlAst, INVALID_CTOR, type.getDisplayName());
      return getUnknownType();
    }
    return type.toMaybeFunctionType().getInstanceType();
  }

  private JSType evalNativeTypeExpr(Node ttlAst) {
    JSTypeExpression expr = new JSTypeExpression(getCallArgument(ttlAst, 0), VIRTUAL_FILE);
    return this.registry.evaluateTypeExpression(expr, this.typeEnv);
  }

  private JSType evalPrintType(Node ttlAst, NameResolver nameResolver) {
    JSType type = evalInternal(getCallArgument(ttlAst, 1), nameResolver);
    String msg = getCallArgument(ttlAst, 0).getString() + type;
    System.out.println(msg);
    return type;
  }

  private JSType evalPropType(Node ttlAst, NameResolver nameResolver) {
    JSType type = evalInternal(getCallArgument(ttlAst, 1), nameResolver);
    ObjectType objType = type.toMaybeObjectType();
    if (objType == null) {
      reportWarning(ttlAst, PROPTYPE_INVALID, type.toString());
      return getUnknownType();
    }
    JSType propType = objType.getPropertyType(getCallArgument(ttlAst, 0).getString());
    return firstNonNull(propType, getUnknownType());
  }

}
