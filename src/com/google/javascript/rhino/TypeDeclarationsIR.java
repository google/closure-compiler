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
 *   Michael Zhou
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

package com.google.javascript.rhino;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.javascript.rhino.Node.TypeDeclarationNode;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An AST construction helper class for TypeDeclarationNode
 * @author alexeagle@google.com (Alex Eagle)
 * @author moz@google.com (Michael Zhou)
 */
public class TypeDeclarationsIR {

  /**
   * @return a new node representing the string built-in type.
   */
  public static TypeDeclarationNode stringType() {
    return new TypeDeclarationNode(Token.STRING_TYPE);
  }

  /**
   * @return a new node representing the number built-in type.
   */
  public static TypeDeclarationNode numberType() {
    return new TypeDeclarationNode(Token.NUMBER_TYPE);
  }

  /**
   * @return a new node representing the boolean built-in type.
   */
  public static TypeDeclarationNode booleanType() {
    return new TypeDeclarationNode(Token.BOOLEAN_TYPE);
  }

  /**
   * We assume that types are non-nullable by default. Wrap a type in nullable
   * to indicate that it should be allowed to take a null value.
   * NB: this is not currently supported in TypeScript so it is not printed
   * in the CodeGenerator for ES6_TYPED.
   * @return a new node indicating that the nested type is nullable
   */
  public static TypeDeclarationNode nullable(TypeDeclarationNode type) {
    return new TypeDeclarationNode(Token.NULLABLE_TYPE, type);
  }

  /**
   * Equivalent to the UNKNOWN type in Closure, expressed with {@code {?}}
   * @return a new node representing any type, without type checking.
   */
  public static TypeDeclarationNode anyType() {
    return new TypeDeclarationNode(Token.ANY_TYPE);
  }

  /**
   * @return a new node representing the Void type as defined by TypeScript.
   */
  public static TypeDeclarationNode voidType() {
    return new TypeDeclarationNode(Token.VOID_TYPE);
  }

  /**
   * @return a new node representing the Undefined type as defined by TypeScript.
   */
  public static TypeDeclarationNode undefinedType() {
    return new TypeDeclarationNode(Token.UNDEFINED_TYPE);
  }

  /**
   * Splits a '.' separated qualified name into a tree of type segments.
   *
   * @param typeName a qualified name such as "goog.ui.Window"
   * @return a new node representing the type
   * @see #namedType(Iterable)
   */
  public static TypeDeclarationNode namedType(String typeName) {
    return namedType(Splitter.on('.').split(typeName));
  }

  /**
   * Produces a tree structure similar to the Rhino AST of a qualified name
   * expression, under a top-level NAMED_TYPE node.
   *
   * <p>Example:
   * <pre>
   * NAMED_TYPE
   *   NAME goog
   *     STRING ui
   *       STRING Window
   * </pre>
   */
  public static TypeDeclarationNode namedType(Iterable<String> segments) {
    Iterator<String> segmentsIt = segments.iterator();
    Node node = IR.name(segmentsIt.next());
    while (segmentsIt.hasNext()) {
      node = IR.getprop(node, IR.string(segmentsIt.next()));
    }
    return new TypeDeclarationNode(Token.NAMED_TYPE, node);
  }

  /**
   * Represents a structural type.
   * Closure calls this a Record Type and accepts the syntax
   * {@code {myNum: number, myObject}}
   *
   * <p>Example:
   * <pre>
   * RECORD_TYPE
   *   STRING_KEY myNum
   *     NUMBER_TYPE
   *   STRING_KEY myObject
   * </pre>
   * @param properties a map from property name to property type
   * @return a new node representing the record type
   */
  public static TypeDeclarationNode recordType(
      LinkedHashMap<String, TypeDeclarationNode> properties) {
    TypeDeclarationNode node = new TypeDeclarationNode(Token.RECORD_TYPE);
    for (Map.Entry<String, TypeDeclarationNode> prop : properties.entrySet()) {
      if (prop.getValue() == null) {
        node.addChildToBack(IR.stringKey(prop.getKey()));
      } else {
        Node stringKey = IR.stringKey(prop.getKey());
        stringKey.addChildToFront(prop.getValue());
        node.addChildToBack(stringKey);
      }
    }
    return node;
  }

  /**
   * Represents a function type.
   * Closure has syntax like {@code {function(string, boolean):number}}
   * Closure doesn't include parameter names. If the parameter types are unnamed,
   * arbitrary names can be substituted, eg. p1, p2, etc.
   *
   * <p>Example:
   * <pre>
   * FUNCTION_TYPE
   *   NUMBER_TYPE
   *   STRING_KEY p1
   *     STRING_TYPE
   *   STRING_KEY p2
   *     BOOLEAN_TYPE
   * </pre>
   * @param returnType the type returned by the function, possibly UNKNOWN_TYPE
   * @param parameters the types of the parameters.
   * @param restName the name of the rest parameter, if any.
   * @param restType the type of the rest parameter, if any.
   */
  public static TypeDeclarationNode functionType(
      Node returnType, LinkedHashMap<String, TypeDeclarationNode> parameters,
      String restName, TypeDeclarationNode restType) {
    TypeDeclarationNode node = new TypeDeclarationNode(Token.FUNCTION_TYPE, returnType);
    for (Map.Entry<String, TypeDeclarationNode> parameter : parameters.entrySet()) {
      Node stringKey = IR.stringKey(parameter.getKey());
      stringKey.addChildToFront(parameter.getValue());
      node.addChildToBack(stringKey);
    }
    if (restName != null) {
      Node rest = IR.stringKey(restName);
      if (restType != null) {
        rest.addChildToBack(arrayType(restType));
      }
      node.addChildToBack(restParams(rest));
    }
    return node;
  }

  /**
   * Represents a parameterized, or generic, type.
   * Closure calls this a Type Application and accepts syntax like
   * {@code {Object.<string, number>}}
   *
   * <p>Example:
   * <pre>
   * PARAMETERIZED_TYPE
   *   NAMED_TYPE
   *     NAME Object
   *   STRING_TYPE
   *   NUMBER_TYPE
   * </pre>
   * @param baseType
   * @param typeParameters
   */
  public static TypeDeclarationNode parameterizedType(
      TypeDeclarationNode baseType, Iterable<TypeDeclarationNode> typeParameters) {
    if (Iterables.isEmpty(typeParameters)) {
      return baseType;
    }
    TypeDeclarationNode node = new TypeDeclarationNode(Token.PARAMETERIZED_TYPE, baseType);
    for (Node typeParameter : typeParameters) {
      node.addChildToBack(typeParameter);
    }
    return node;
  }

  /**
   * Represents an array type. In Closure, this is represented by a
   * {@link #parameterizedType(TypeDeclarationNode, Iterable) parameterized type} of {@code Array}
   * with {@code elementType} as the sole type parameter.
   *
   * <p>Example
   * <pre>
   * ARRAY_TYPE
   *   elementType
   * </pre>
   */
  public static TypeDeclarationNode arrayType(Node elementType) {
    return new TypeDeclarationNode(Token.ARRAY_TYPE, elementType);
  }

  /**
   * Represents a union type, which can be one of the given types.
   * Closure accepts syntax like {@code {(number|boolean)}}
   *
   * <p>Example:
   * <pre>
   * UNION_TYPE
   *   NUMBER_TYPE
   *   BOOLEAN_TYPE
   * </pre>
   * @param options the types which are accepted
   * @return a new node representing the union type
   */
  public static TypeDeclarationNode unionType(Iterable<TypeDeclarationNode> options) {
    Preconditions.checkArgument(!Iterables.isEmpty(options),
        "union must have at least one option");
    TypeDeclarationNode node = new TypeDeclarationNode(Token.UNION_TYPE);
    for (Node option : options) {
      node.addChildToBack(option);
    }
    return node;
  }

  public static TypeDeclarationNode unionType(TypeDeclarationNode... options) {
    return unionType(Arrays.asList(options));
  }

  /**
   * Represents a function parameter type which may be repeated.
   * Closure calls this Variable Parameters and accepts a syntax like
   * {@code {function(string, ...number): number}}
   *
   * <p>
   * <pre>
   * FUNCTION_TYPE
   *   NUMBER_TYPE
   *   STRING_KEY p1
   *     STRING_TYPE
   *   REST_PARAMETER_TYPE
   *     STRING_KEY p2
   *       NUMBER_TYPE
   * </pre>
   * @param type an array type that is seen inside the function body
   * @return a new node representing the function parameter type
   */
  public static TypeDeclarationNode restParams(Node type) {
    TypeDeclarationNode node = new TypeDeclarationNode(Token.REST_PARAMETER_TYPE);
    if (type != null) {
      node.addChildToBack(type);
    }
    return node;
  }

  /**
   * Represents a function parameter that is optional.
   * In closure syntax, this is {@code function(?string=, number=)}
   * In TypeScript syntax, it is
   * {@code (firstName: string, lastName?: string)=>string}
   * @param parameterType the type of the parameter
   * @return a new node representing the function parameter type
   */
  public static TypeDeclarationNode optionalParameter(TypeDeclarationNode parameterType) {
    return new TypeDeclarationNode(Token.OPTIONAL_PARAMETER, parameterType);
  }
}
