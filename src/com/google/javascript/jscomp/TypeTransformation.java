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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.parsing.TypeTransformationParser;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.RecordTypeBuilder;
import com.google.javascript.rhino.jstype.TemplatizedType;
import com.google.javascript.rhino.jstype.UnionType;

import java.util.Collection;

/**
 * A class for processing type transformation expressions
 *
 * @author lpino@google.com (Luis Fernando Pino Duque)
 */
class TypeTransformation {
  private AbstractCompiler compiler;
  private JSTypeRegistry typeRegistry;

  static final DiagnosticType UNKNOWN_TYPEVAR =
      DiagnosticType.warning("TYPEVAR_UNDEFINED",
          "Reference to an unknown type variable {0}");
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
      "Index out of bounds in templateTypeOf: {0} > {1}");

  TypeTransformation(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.typeRegistry = compiler.getTypeRegistry();
  }

  private boolean isTypeVar(Node n) {
    return n.isName();
  }

  private boolean isTypeName(Node n) {
    return n.isString();
  }

  private boolean isCallTo(Node n, TypeTransformationParser.Keywords keyword) {
    if (!n.isCall()) {
      return false;
    }
    return n.getFirstChild().getString().equals(keyword.name);
  }

  private boolean isTypePredicate(Node n) {
    return isCallTo(n, TypeTransformationParser.Keywords.TYPE);
  }

  private boolean isUnionType(Node n) {
    return isCallTo(n, TypeTransformationParser.Keywords.UNION);
  }

  private boolean isEqtype(Node n) {
    return isCallTo(n, TypeTransformationParser.Keywords.EQTYPE);
  }

  private boolean isSubtype(Node n) {
    return isCallTo(n, TypeTransformationParser.Keywords.SUBTYPE);
  }

  private boolean isConditional(Node n) {
    return isCallTo(n, TypeTransformationParser.Keywords.COND);
  }

  private boolean isMapunion(Node n) {
    return isCallTo(n, TypeTransformationParser.Keywords.MAPUNION);
  }

  private boolean isNone(Node n) {
    return isCallTo(n, TypeTransformationParser.Keywords.NONE);
  }

  private boolean isRawTypeOf(Node n) {
    return isCallTo(n, TypeTransformationParser.Keywords.RAWTYPEOF);
  }

  private boolean isTemplateTypeOf(Node n) {
    return isCallTo(n, TypeTransformationParser.Keywords.TEMPTYPEOF);
  }

  private boolean isRecordType(Node n) {
    return isCallTo(n, TypeTransformationParser.Keywords.RECORD);
  }

  private JSType getType(String name) {
    return typeRegistry.getType(name);
  }

  private JSType getNativeType(JSTypeNative type) {
    return typeRegistry.getNativeObjectType(type);
  }

  private boolean isTemplatizable(JSType type) {
    return typeRegistry.isTemplatizable(type);
  }

  private JSType getUnknownType() {
    return getNativeType(JSTypeNative.UNKNOWN_TYPE);
  }

  private JSType getNoType() {
    return getNativeType(JSTypeNative.NO_TYPE);
  }

  private JSType createUnionType(JSType... variants) {
    return typeRegistry.createUnionType(variants);
  }

  private JSType createTemplatizedType(ObjectType baseType, JSType[] params) {
    return typeRegistry.createTemplatizedType(baseType, params);
  }

  private void reportWarning(Node n, DiagnosticType msg, String... param) {
    compiler.report(JSError.make(n, msg, param));
  }

  private ImmutableMap<String, JSType> addNewVar(
      ImmutableMap<String, JSType> typeVars, String name, JSType type) {
    return new ImmutableMap.Builder<String, JSType>()
        .putAll(typeVars)
        .put(name, type)
        .build();
  }

  private String getFunctionParameter(Node functionNode, int index) {
    return functionNode.getChildAtIndex(1).getChildAtIndex(index).getString();
  }

  private Node getCallArgument(Node n, int i) {
    return n.isCall() ? n.getChildAtIndex(i + 1) : null;
  }

  private ImmutableList<Node> getParameters(Node operation) {
    ImmutableList.Builder<Node> builder = new ImmutableList.Builder<Node>();
    // Omit the keyword (first child)
    for (int i = 1; i < operation.getChildCount(); i++) {
      builder.add(operation.getChildAtIndex(i));
    }
    return builder.build();
  }

  /** Evaluates the type transformation expression and returns the resulting
   * type.
   *
   * @param ttlAst The node representing the type transformation
   * expression
   * @param typeVars The environment containing the information about
   * the type variables
   * @return JSType The resulting type after the transformation
   */
  JSType eval(Node ttlAst, ImmutableMap<String, JSType> typeVars) {
    if (isTypeName(ttlAst)) {
      return evalTypeName(ttlAst);
    }
    if (isTypeVar(ttlAst)) {
      return evalTypeVar(ttlAst, typeVars);
    }
    if (isTypePredicate(ttlAst)) {
      return evalTemplatizedType(ttlAst, typeVars);
    }
    if (isUnionType(ttlAst)) {
      return evalUnionType(ttlAst, typeVars);
    }
    if (isConditional(ttlAst)) {
      return evalConditional(ttlAst, typeVars);
    }
    if (isMapunion(ttlAst)) {
      return evalMapunion(ttlAst, typeVars);
    }
    if (isNone(ttlAst)) {
      return getNoType();
    }
    if (isRawTypeOf(ttlAst)) {
      return evalRawTypeOf(ttlAst, typeVars);
    }
    if (isTemplateTypeOf(ttlAst)) {
      return evalTemplateTypeOf(ttlAst, typeVars);
    }
    if (isRecordType(ttlAst)) {
      return evalRecordType(ttlAst, typeVars);
    }
    throw new IllegalStateException(
        "Could not evaluate the type transformation expression");
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

  private JSType evalTemplatizedType(Node ttlAst,
      ImmutableMap<String, JSType> typeVars) {
    ImmutableList<Node> params = getParameters(ttlAst);
    JSType firstParam = eval(params.get(0), typeVars);
    if (!isTemplatizable(firstParam)) {
      reportWarning(ttlAst, BASETYPE_INVALID, firstParam.toString());
      return getUnknownType();
    }
    ObjectType baseType = firstParam.toObjectType();
    // TODO(lpino): Check that the number of parameters correspond with the
    // number of template types that the base type can take when creating
    // a templatized type. For instance, if the base type is Array then there
    // must be just one parameter.
    JSType[] templatizedTypes = new JSType[params.size() - 1];
    for (int i = 0; i < templatizedTypes.length; i++) {
      templatizedTypes[i] = eval(params.get(i + 1), typeVars);
    }
    return createTemplatizedType(baseType, templatizedTypes);
  }

  private JSType evalTypeVar(Node ttlAst, ImmutableMap<String, JSType> typeVars) {
    String typeVar = ttlAst.getString();
    JSType resultingType = typeVars.get(typeVar);
    // If the type variable is not defined then return UNKNOWN and report a warning
    if (resultingType == null) {
      reportWarning(ttlAst, UNKNOWN_TYPEVAR, typeVar);
      return getUnknownType();
    }
    return resultingType;
  }

  private JSType evalUnionType(Node ttlAst,
      ImmutableMap<String, JSType> typeVars) {
    // Get the parameters of the union
    ImmutableList<Node> params = getParameters(ttlAst);
    int paramCount = params.size();
    // Create an array of types after evaluating each parameter
    JSType[] basicTypes = new JSType[paramCount];
    for (int i = 0; i < paramCount; i++) {
      basicTypes[i] = eval(params.get(i), typeVars);
    }
    return createUnionType(basicTypes);
  }

  private boolean evalBoolean(Node ttlAst,
      ImmutableMap<String, JSType> typeVars) {
    ImmutableList<Node> params = getParameters(ttlAst);
    JSType type0 = eval(params.get(0), typeVars);
    JSType type1 = eval(params.get(1), typeVars);

    if (isEqtype(ttlAst)) {
      return type0.isEquivalentTo(type1);
    } else if (isSubtype(ttlAst)) {
      return type0.isSubtype(type1);
    }
    throw new IllegalStateException(
        "Invalid boolean predicate in the type transformation");
  }

  private JSType evalConditional(Node ttlAst,
      ImmutableMap<String, JSType> typeVars) {
    ImmutableList<Node> params = getParameters(ttlAst);
    if (evalBoolean(params.get(0), typeVars)) {
      return eval(params.get(1), typeVars);
    } else {
      return eval(params.get(2), typeVars);
    }
  }

  private JSType evalMapunion(Node ttlAst, ImmutableMap<String, JSType> typeVars) {
    ImmutableList<Node> params = getParameters(ttlAst);
    Node unionParam = params.get(0);
    Node mapFunction = params.get(1);
    String paramName = getFunctionParameter(mapFunction, 0);
    Node mapFunctionBody = mapFunction.getChildAtIndex(2);
    JSType unionType;

    // The first parameter is either a union() or a type variable
    if (isUnionType(unionParam)) {
      unionType = evalUnionType(unionParam, typeVars);
    } else if (isTypeVar(unionParam)) {
      unionType = evalTypeVar(unionParam, typeVars);
    } else {
      throw new IllegalStateException("Invalid union type parameter in mapunion");
    }

    // If the first parameter does not correspond to a union type then
    // consider it as a union with a single type and evaluate
    if (!unionType.isUnionType()) {
      return eval(mapFunctionBody, addNewVar(typeVars, paramName, unionType));
    }

    // Otherwise obtain the elements in the union type. Note that the block
    // above guarantees the casting to be safe
    Collection<JSType> unionElms = ((UnionType) unionType).getAlternates();
    // Evaluate the map function body using each element in the union type
    int unionSize = unionElms.size();
    JSType[] newUnionElms = new JSType[unionSize];
    int i = 0;
    for (JSType elm : unionElms) {
      newUnionElms[i] = eval(mapFunctionBody,
          addNewVar(typeVars, paramName, elm));
      i++;
    }

    return createUnionType(newUnionElms);
  }

  private JSType evalRawTypeOf(Node ttlAst, ImmutableMap<String, JSType> typeVars) {
    ImmutableList<Node> params = getParameters(ttlAst);
    JSType type = eval(params.get(0), typeVars);
    if (!type.isTemplatizedType()) {
      reportWarning(ttlAst, TEMPTYPE_INVALID, "rawTypeOf", type.toString());
      return getUnknownType();
    }
    return ((TemplatizedType) type).getReferencedType();
  }

  private JSType evalTemplateTypeOf(Node ttlAst,
      ImmutableMap<String, JSType> typeVars) {
    ImmutableList<Node> params = getParameters(ttlAst);
    JSType type = eval(params.get(0), typeVars);
    if (!type.isTemplatizedType()) {
      reportWarning(ttlAst, TEMPTYPE_INVALID, "templateTypeOf", type.toString());
      return getUnknownType();
    }
    int index = (int) params.get(1).getDouble();
    ImmutableList<JSType> templateTypes =
        ((TemplatizedType) type).getTemplateTypes();
    if (index > templateTypes.size()) {
      reportWarning(ttlAst, INDEX_OUTOFBOUNDS,
          Integer.toString(index), Integer.toString(templateTypes.size()));
      return getUnknownType();
    }
    return templateTypes.get(index);
  }

  private JSType evalRecordType(Node ttlAst,
      ImmutableMap<String, JSType> typeVars) {
    Node record = getCallArgument(ttlAst, 0);
    RecordTypeBuilder builder = new RecordTypeBuilder(typeRegistry);
    for (Node p : record.children()) {
      builder.addProperty(p.getString(),
          eval(p.getFirstChild(), typeVars), null);
    }
    return builder.build();
  }
}