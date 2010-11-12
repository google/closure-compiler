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
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * CodingConvention defines a set of hooks to customize the behavior of the
 * Compiler for a specific team/company.
 *
 * // TODO(bolinfest): Tighten up this interface -- it is far too big.
 *
 */
public interface CodingConvention extends Serializable {

  /**
   * This checks whether a given variable name, such as a name in all-caps
   * should be treated as if it had the @const annotation.
   *
   * @param variableName potentially constant variable name
   * @return {@code true} if the name should be treated as a constant.
   */
  public boolean isConstant(String variableName);

  /**
   * This checks whether a given key of an object literal, such as a
   * name in all-caps should be treated as if it had the @const
   * annotation.
   */
  public boolean isConstantKey(String keyName);

  /**
   * This checks that a given {@code key} may be used as a key for an enum.
   *
   * @param key the potential key to an enum
   * @return {@code true} if the {@code key} may be used as an enum key,
   *     {@code false} otherwise
   */
  public boolean isValidEnumKey(String key);

  /**
   * This checks whether a given parameter name should be treated as an
   * optional parameter as far as type checking or function call arg count
   * checking is concerned. Note that an optional function parameter may be
   * declared as a simple type and is automatically converted to a union of the
   * declared type and Undefined.
   *
   * @param parameter The parameter's node.
   * @return {@code true} if the parameter should be treated as an optional
   * parameter.
   */
  public boolean isOptionalParameter(Node parameter);

  /**
   * This checks whether a given parameter should be treated as a marker
   * for a variable argument list function. A VarArgs parameter must be the
   * last parameter in a function declaration.
   *
   * @param parameter The parameter's node.
   * @return {@code true} if the parameter should be treated as a variable
   * length parameter.
   */
  public boolean isVarArgsParameter(Node parameter);

  /**
   * Checks whether a global variable or function name should be treated as
   * exported, or externally referenceable.
   *
   * @param name A global variable or function name.
   * @param local {@code true} if the name is a local variable.
   * @return {@code true} if the name should be considered exported.
   */
  public boolean isExported(String name, boolean local);

  /**
   * Should be isExported(name, true) || isExported(name, false);
   */
  public boolean isExported(String name);

  /**
   * Checks whether a name should be considered private. Private global
   * variables and functions can only be referenced within the source file in
   * which they are declared. Private properties and methods should only be
   * accessed by the class that defines them.
   *
   * @param name The name of a global variable or function, or a method or
   *     property.
   * @return {@code true} if the name should be considered private.
   */
  public boolean isPrivate(String name);

  /**
   * Checks if the given method defines a subclass relationship,
   * and if it does, returns information on that relationship. By default,
   * always returns null. Meant to be overridden by subclasses.
   *
   * @param callNode A CALL node.
   */
  public SubclassRelationship getClassesDefinedByCall(Node callNode);

  /**
   * Returns true if passed a string referring to the superclass.  The string
   * will usually be from the string node at the right of a GETPROP, e.g.
   * this.superClass_.
   */
  public boolean isSuperClassReference(String propertyName);

  /**
   * Convenience method for determining provided dependencies amongst different
   * js scripts.
   */
  public String extractClassNameIfProvide(Node node, Node parent);

  /**
   * Convenience method for determining required dependencies amongst different
   * js scripts.
   */
  public String extractClassNameIfRequire(Node node, Node parent);

  /**
   * Function name used when exporting properties.
   * Signature: fn(object, publicName, symbol).
   * @return function name.
   */
  public String getExportPropertyFunction();

  /**
   * Function name used when exporting symbols.
   * Signature: fn(publicPath, object).
   * @return function name.
   */
  public String getExportSymbolFunction();

  /**
   * Checks if the given CALL node is forward-declaring any types,
   * and returns the name of the types if it is.
   */
  public List<String> identifyTypeDeclarationCall(Node n);

  /**
   * Checks if the given ASSIGN node is a typedef, and returns the
   * name of the type if it is.
   */
  public String identifyTypeDefAssign(Node n);

  /**
   * In many JS libraries, the function that produces inheritance also
   * adds properties to the superclass and/or subclass.
   */
  public void applySubclassRelationship(FunctionType parentCtor,
      FunctionType childCtor, SubclassType type);

  /**
   * Function name for abstract methods. An abstract method can be assigned to
   * an interface method instead of an function expression in order to avoid
   * linter warnings produced by assigning a function without a return value
   * where a return value is expected.
   * @return function name.
   */
  public String getAbstractMethodName();

  /**
   * Checks if the given method defines a singleton getter, and if it does,
   * returns the name of the class with the singleton getter. By default, always
   * returns null. Meant to be overridden by subclasses.
   *
   * @param callNode A CALL node.
   */
  public String getSingletonGetterClassName(Node callNode);

  /**
   * In many JS libraries, the function that adds a singleton getter to a class
   * adds properties to the class.
   */
  public void applySingletonGetter(FunctionType functionType,
      FunctionType getterType, ObjectType objectType);

  public DelegateRelationship getDelegateRelationship(Node callNode);

  /**
   * In many JS libraries, the function that creates a delegate relationship
   * also adds properties to the delegator and delegate base.
   */
  public void applyDelegateRelationship(
      ObjectType delegateSuperclass, ObjectType delegateBase,
      ObjectType delegator, FunctionType delegateProxy,
      FunctionType findDelegate);

  /**
   * @return the name of the delegate superclass.
   */
  public String getDelegateSuperclassName();

  /**
   * Defines the delegate proxy prototype properties. Their types depend on
   * properties of the delegate base methods.
   *
   * @param delegateProxyPrototypes List of delegate proxy prototypes.
   */
  public void defineDelegateProxyPrototypeProperties(
      JSTypeRegistry registry, Scope scope,
      List<ObjectType> delegateProxyPrototypes);

  /**
   * Gets the name of the global object.
   */
  public String getGlobalObject();

  /**
   * Whether this CALL function is testing for the existence of a property.
   */
  public boolean isPropertyTestFunction(Node call);

  /**
   * Checks if the given method performs a object literal cast, and if it does,
   * returns information on the cast. By default, always returns null. Meant
   * to be overridden by subclasses.
   *
   * @param t The node traversal.
   * @param callNode A CALL node.
   */
  public ObjectLiteralCast getObjectLiteralCast(NodeTraversal t,
      Node callNode);

  /**
   * Returns the set of AssertionFunction.
   */
  public Collection<AssertionFunctionSpec> getAssertionFunctions();

  static enum SubclassType {
    INHERITS,
    MIXIN
  }

  static class SubclassRelationship {
    final SubclassType type;
    final String subclassName;
    final String superclassName;

    SubclassRelationship(SubclassType type,
        Node subclassNode, Node superclassNode) {
      this.type = type;
      this.subclassName = subclassNode.getQualifiedName();
      this.superclassName = superclassNode.getQualifiedName();
    }
  }

  /**
   * Delegates provides a mechanism and structure for identifying where classes
   * can call out to optional code to augment their functionality. The optional
   * code is isolated from the base code through the use of a subclass in the
   * optional code derived from the delegate class in the base code.
   */
  static class DelegateRelationship {
    /** The subclass in the base code. */
    final String delegateBase;

    /** The class in the base code. */
    final String delegator;

    DelegateRelationship(String delegateBase, String delegator) {
      this.delegateBase = delegateBase;
      this.delegator = delegator;
    }
  }

  /**
   * An object literal cast provides a mechanism to cast object literals to
   * other types without a warning.
   */
  static class ObjectLiteralCast {
    /** Type to cast to. */
    final String typeName;

    /** Object to cast. */
    final Node objectNode;

    ObjectLiteralCast(String typeName, Node objectNode) {
      this.typeName = typeName;
      this.objectNode = objectNode;
    }
  }

  /**
   * A function that will throw an exception when either:
   *   -One or more of its parameters evaluate to false.
   *   -One or more of its parameters are not of a certain type.
   */
  public class AssertionFunctionSpec {
    private final String functionName;
    private final JSTypeNative assertedType;

    public AssertionFunctionSpec(String functionName) {
      this(functionName, null);
    }

    public AssertionFunctionSpec(String functionName,
        JSTypeNative assertedType) {
      this.functionName = functionName;
      this.assertedType = assertedType;
    }

    /** Returns the name of the function. */
    public String getFunctionName() {
      return functionName;
    }

    /**
     * Returns the parameter of the assertion function that is being checked.
     * @param firstParam The first parameter of the function call.
     */
    public Node getAssertedParam(Node firstParam) {
      return firstParam;
    }

    /**
     * Returns the type for a type assertion, or null if the function asserts
     * that the node must not be null or undefined.
     */
    public JSTypeNative getAssertedType() {
      return assertedType;
    }
  }
}
