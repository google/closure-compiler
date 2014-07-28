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

import java.util.ArrayList;

/**
 * A class for processing type transformation expressions
 *
 * @author lpino@google.com (Luis Fernando Pino Duque)
 */
class TypeTransformation {
  private Compiler compiler;
  private JSTypeRegistry typeRegistry;

  TypeTransformation(Compiler compiler) {
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

  private JSType getUnknownType() {
    return typeRegistry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE);
  }

  private JSType typeOrUnknown(JSType type) {
    return (type == null) ? getUnknownType() : type;
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
}