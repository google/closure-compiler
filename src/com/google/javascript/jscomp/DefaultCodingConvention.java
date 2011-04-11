/*
 * Copyright 2007 The Closure Compiler Authors.
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

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * CodingConvention defines a set of hooks to customize the behavior of the
 * Compiler for a specific team/company.
 *
 */
public class DefaultCodingConvention implements CodingConvention {

  private static final long serialVersionUID = 1L;

  @Override
  public boolean isConstant(String variableName) {
    return false;
  }

  @Override
  public boolean isConstantKey(String variableName) {
    return false;
  }

  @Override
  public boolean isValidEnumKey(String key) {
    return key != null && key.length() > 0;
  }

  @Override
  public boolean isOptionalParameter(Node parameter) {
    // be as lax as possible, but this must be mutually exclusive from
    // var_args parameters.
    return !isVarArgsParameter(parameter);
  }

  @Override
  public boolean isVarArgsParameter(Node parameter) {
    // be as lax as possible
    return parameter.getParent().getLastChild() == parameter;
  }

  @Override
  public boolean isExported(String name, boolean local) {
    return local && name.startsWith("$super");
  }

  @Override
  public boolean isExported(String name) {
    return isExported(name, false) || isExported(name, true);
  }

  @Override
  public boolean isPrivate(String name) {
    return false;
  }

  @Override
  public SubclassRelationship getClassesDefinedByCall(Node callNode) {
    return null;
  }

  @Override
  public boolean isSuperClassReference(String propertyName) {
    return false;
  }

  @Override
  public String extractClassNameIfProvide(Node node, Node parent) {
    String message = "only implemented in GoogleCodingConvention";
    throw new UnsupportedOperationException(message);
  }

  @Override
  public String extractClassNameIfRequire(Node node, Node parent) {
    String message = "only implemented in GoogleCodingConvention";
    throw new UnsupportedOperationException(message);
  }

  @Override
  public String getExportPropertyFunction() {
    return null;
  }

  @Override
  public String getExportSymbolFunction() {
    return null;
  }

  @Override
  public List<String> identifyTypeDeclarationCall(Node n) {
    return null;
  }

  @Override
  public String identifyTypeDefAssign(Node n) {
    return null;
  }

  @Override
  public void applySubclassRelationship(FunctionType parentCtor,
      FunctionType childCtor, SubclassType type) {
    // do nothing
  }

  @Override
  public String getAbstractMethodName() {
    return null;
  }

  @Override
  public String getSingletonGetterClassName(Node callNode) {
    return null;
  }

  @Override
  public void applySingletonGetter(FunctionType functionType,
      FunctionType getterType, ObjectType objectType) {
    // do nothing.
  }

  @Override
  public DelegateRelationship getDelegateRelationship(Node callNode) {
    return null;
  }

  @Override
  public void applyDelegateRelationship(
      ObjectType delegateSuperclass, ObjectType delegateBase,
      ObjectType delegator, FunctionType delegateProxy,
      FunctionType findDelegate) {
    // do nothing.
  }

  @Override
  public String getDelegateSuperclassName() {
    return null;
  }

  @Override
  public void defineDelegateProxyPrototypeProperties(
      JSTypeRegistry registry, Scope scope,
      List<ObjectType> delegateProxyPrototypes) {
    // do nothing.
  }

  @Override
  public String getGlobalObject() {
    return "window";
  }

  @Override
  public boolean isPropertyTestFunction(Node call) {
    return false;
  }

  @Override
  public ObjectLiteralCast getObjectLiteralCast(NodeTraversal t,
      Node callNode) {
    return null;
  }

  @Override
  public Collection<AssertionFunctionSpec> getAssertionFunctions() {
    return Collections.emptySet();
  }
}
