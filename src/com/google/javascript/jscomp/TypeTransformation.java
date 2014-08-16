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
import com.google.javascript.jscomp.parsing.TypeTransformationParser.Keywords;
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
  static final DiagnosticType DUPLICATE_VARIABLE =
      DiagnosticType.warning("DUPLICATE_VARIABLE",
          "The variable {0} is already defined");
  static final DiagnosticType UNKNOWN_NAMEVAR =
      DiagnosticType.warning("UNKNOWN_NAMEVAR",
          "Reference to an unknown name variable {0}");

  /**
   * A helper class for holding the information about the type variables
   * and the name variables in maprecord expressions
   */
  private class NameResolver {
    ImmutableMap<String, JSType> typeVars;
    ImmutableMap<String, String> nameVars;

    NameResolver(ImmutableMap<String, JSType> typeVars,
        ImmutableMap<String, String> nameVars) {
      this.typeVars = typeVars;
      this.nameVars = nameVars;
    }
  }

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

  private Keywords nameToKeyword(String s) {
    return TypeTransformationParser.Keywords.valueOf(s.toUpperCase());
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

  private <T> ImmutableMap<String, T> addNewEntry(
      ImmutableMap<String, T> map, String name, T type) {
    return new ImmutableMap.Builder<String, T>()
        .putAll(map)
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
    return eval(ttlAst, typeVars, ImmutableMap.<String, String>of());
  }

  /** Evaluates the type transformation expression and returns the resulting
   * type.
   *
   * @param ttlAst The node representing the type transformation
   * expression
   * @param typeVars The environment containing the information about
   * the type variables
   * @param nameVars The environment containing the information about
   * the name variables
   * @return JSType The resulting type after the transformation
   */
  JSType eval(Node ttlAst, ImmutableMap<String, JSType> typeVars,
      ImmutableMap<String, String> nameVars) {
    return evalInternal(ttlAst, new NameResolver(typeVars, nameVars));
  }

  private JSType evalInternal(Node ttlAst, NameResolver nameResolver) {
    if (isTypeName(ttlAst)) {
      return evalTypeName(ttlAst);
    }
    if (isTypeVar(ttlAst)) {
      return evalTypeVar(ttlAst, nameResolver);
    }
    String name = ttlAst.getFirstChild().getString();
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
    String name = ttlAst.getFirstChild().getString();
    Keywords keyword = nameToKeyword(name);
    switch (keyword) {
      case COND:
        return evalConditional(ttlAst, nameResolver);
      case MAPUNION:
        return evalMapunion(ttlAst, nameResolver);
      default:
        throw new IllegalStateException("Invalid type transformation operation");
    }
  }

  private JSType evalTypeExpression(Node ttlAst, NameResolver nameResolver) {
    String name = ttlAst.getFirstChild().getString();
    Keywords keyword = nameToKeyword(name);
    switch (keyword) {
      case TYPE:
        return evalTemplatizedType(ttlAst, nameResolver);
      case UNION:
        return evalUnionType(ttlAst, nameResolver);
      case NONE:
        return getNoType();
      case RAWTYPEOF:
        return evalRawTypeOf(ttlAst, nameResolver);
      case TEMPLATETYPEOF:
        return evalTemplateTypeOf(ttlAst, nameResolver);
      case RECORD:
        return evalRecordType(ttlAst, nameResolver);
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
    ImmutableList<Node> params = getParameters(ttlAst);
    JSType firstParam = evalInternal(params.get(0), nameResolver);
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
      templatizedTypes[i] = evalInternal(params.get(i + 1), nameResolver);
    }
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
    ImmutableList<Node> params = getParameters(ttlAst);
    int paramCount = params.size();
    // Create an array of types after evaluating each parameter
    JSType[] basicTypes = new JSType[paramCount];
    for (int i = 0; i < paramCount; i++) {
      basicTypes[i] = evalInternal(params.get(i), nameResolver);
    }
    return createUnionType(basicTypes);
  }

  private boolean evalBoolean(Node ttlAst, NameResolver nameResolver) {
    ImmutableList<Node> params = getParameters(ttlAst);
    JSType type0 = evalInternal(params.get(0), nameResolver);
    JSType type1 = evalInternal(params.get(1), nameResolver);
    String name = ttlAst.getFirstChild().getString();
    Keywords keyword = nameToKeyword(name);

    switch (keyword) {
      case EQ:
        return type0.isEquivalentTo(type1);
      case SUB:
        return type0.isSubtype(type1);
      default:
        throw new IllegalStateException(
            "Invalid boolean predicate in the type transformation");
    }
  }

  private JSType evalConditional(Node ttlAst, NameResolver nameResolver) {
    ImmutableList<Node> params = getParameters(ttlAst);
    if (evalBoolean(params.get(0), nameResolver)) {
      return evalInternal(params.get(1), nameResolver);
    } else {
      return evalInternal(params.get(2), nameResolver);
    }
  }

  private JSType evalMapunion(Node ttlAst, NameResolver nameResolver) {
    ImmutableList<Node> params = getParameters(ttlAst);
    Node unionParam = params.get(0);
    Node mapFunction = params.get(1);
    String paramName = getFunctionParameter(mapFunction, 0);

    // The mapunion variable must not be defined in the environment
    if (nameResolver.typeVars.containsKey(paramName)) {
      reportWarning(ttlAst, DUPLICATE_VARIABLE, paramName);
      return getUnknownType();
    }

    Node mapFunctionBody = mapFunction.getChildAtIndex(2);
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
    Collection<JSType> unionElms = ((UnionType) unionType).getAlternates();
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
    ImmutableList<Node> params = getParameters(ttlAst);
    JSType type = evalInternal(params.get(0), nameResolver);
    if (!type.isTemplatizedType()) {
      reportWarning(ttlAst, TEMPTYPE_INVALID, "rawTypeOf", type.toString());
      return getUnknownType();
    }
    return ((TemplatizedType) type).getReferencedType();
  }

  private JSType evalTemplateTypeOf(Node ttlAst, NameResolver nameResolver) {
    ImmutableList<Node> params = getParameters(ttlAst);
    JSType type = evalInternal(params.get(0), nameResolver);
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

  private JSType evalRecordType(Node ttlAst, NameResolver nameResolver) {
    Node record = getCallArgument(ttlAst, 0);
    RecordTypeBuilder builder = new RecordTypeBuilder(typeRegistry);
    for (Node p : record.children()) {
      // If it is a computed property then find the property name using the resolver
      if (p.isComputedProp()) {
        String nameVar = p.getFirstChild().getString();
        // If the name does not exist then report a warning
        if (!nameResolver.nameVars.containsKey(nameVar)) {
          reportWarning(ttlAst, UNKNOWN_NAMEVAR, nameVar);
          return getUnknownType();
        }
        // Otherwise add the property
        builder.addProperty(nameResolver.nameVars.get(nameVar),
            evalInternal(p.getChildAtIndex(1), nameResolver), null);
      } else {
        builder.addProperty(p.getString(),
            evalInternal(p.getFirstChild(), nameResolver), null);
      }
    }
    return builder.build();
  }
}
