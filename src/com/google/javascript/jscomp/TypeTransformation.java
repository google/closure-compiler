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

import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.parsing.TypeTransformationParser;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.UnionType;

import java.util.ArrayList;
import java.util.Collection;

/**
 * A class for processing type transformation expressions
 *
 * @author lpino@google.com (Luis Fernando Pino Duque)
 */
class TypeTransformation {
  private AbstractCompiler compiler;
  private JSTypeRegistry typeRegistry;

  TypeTransformation(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.typeRegistry = compiler.getTypeRegistry();
  }

  private static boolean isTypeVar(Node n) {
    return n.isName();
  }

  private static boolean isCallTo(Node n,
      TypeTransformationParser.Keywords keyword) {
    if (!n.isCall()) {
      return false;
    }
    return n.getFirstChild().getString().equals(keyword.name);
  }

  /**
   * Checks if the expression is type()
   */
  private static boolean isTypePredicate(Node n) {
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

  private JSType getNativeType(JSTypeNative type) {
    return typeRegistry.getNativeObjectType(type);
  }

  private JSType getUnknownType() {
    return getNativeType(JSTypeNative.UNKNOWN_TYPE);
  }

  private JSType getNoType() {
    return getNativeType(JSTypeNative.NO_TYPE);
  }

  private JSType typeOrUnknown(JSType type) {
    return (type == null) ? getUnknownType() : type;
  }

  private JSType union(JSType... variants) {
    return typeRegistry.createUnionType(variants);
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

  private ArrayList<Node> getParameters(Node operation) {
    ArrayList<Node> params = new ArrayList<Node>();
    // Omit the keyword (first child)
    for (int i = 1; i < operation.getChildCount(); i++) {
      params.add(operation.getChildAtIndex(i));
    }
    return params;
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
    // Case type variable: T
    if (isTypeVar(ttlAst)) {
      return evalTypeVariable(ttlAst, typeVars);
    }
    // Case basic type: type(typename)
    if (isTypePredicate(ttlAst)) {
      return evalTypePredicate(ttlAst);
    }
    // Case union type: union(BasicType, BasicType, ...)
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
    throw new IllegalStateException(
        "Could not evaluate the type transformation expression");
  }

  private JSType evalTypePredicate(Node ttlAst) {
    String typeName = ttlAst.getChildAtIndex(1).getString();
    JSType resultingType = typeRegistry.getType(typeName);
    // If the type name is not defined then return UNKNOWN
    return typeOrUnknown(resultingType);
  }

  private JSType evalTypeVariable(Node ttlAst,
      ImmutableMap<String, JSType> typeVars) {
    String typeVar = ttlAst.getString();
    JSType resultingType = typeVars.get(typeVar);
    // If the type variable is not found in the environment then it will be
    // taken as UNKNOWN
    return typeOrUnknown(resultingType);
  }

  private JSType evalUnionType(Node ttlAst,
      ImmutableMap<String, JSType> typeVars) {
    // Get the parameters of the union
    ArrayList<Node> params = getParameters(ttlAst);
    int paramCount = params.size();
    // Create an array of types after evaluating each parameter
    JSType[] basicTypes = new JSType[paramCount];
    for (int i = 0; i < paramCount; i++) {
      basicTypes[i] = eval(params.get(i), typeVars);
    }
    return typeRegistry.createUnionType(basicTypes);
  }

  private boolean evalBoolean(Node ttlAst,
      ImmutableMap<String, JSType> typeVars) {
    ArrayList<Node> params = getParameters(ttlAst);
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
    ArrayList<Node> params = getParameters(ttlAst);
    if (evalBoolean(params.get(0), typeVars)) {
      return eval(params.get(1), typeVars);
    } else {
      return eval(params.get(2), typeVars);
    }
  }

  private JSType evalMapunion(Node ttlAst, ImmutableMap<String, JSType> typeVars) {
    ArrayList<Node> params = getParameters(ttlAst);
    Node unionParam = params.get(0);
    Node mapFunction = params.get(1);
    String paramName = getFunctionParameter(mapFunction, 0);
    Node mapFunctionBody = mapFunction.getChildAtIndex(2);
    JSType unionType;

    // The first parameter is either a union() or a type variable
    if (isUnionType(unionParam)) {
      unionType = evalUnionType(unionParam, typeVars);
    } else if (isTypeVar(unionParam)) {
      unionType = typeOrUnknown(typeVars.get(unionParam.getString()));
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

    return union(newUnionElms);
  }
}