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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An AST construction helper class for Node
 *
 * @author alexeagle@google.com (Alex Eagle)
 * @author moz@google.com (Michael Zhou)
 */
public class TypeDeclarationsIR {

  /** @return a new node representing the string built-in type. */
  public static Node stringType() {
    return new Node(Token.STRING_TYPE);
  }

  /** @return a new node representing the number built-in type. */
  public static Node numberType() {
    return new Node(Token.NUMBER_TYPE);
  }

  /** @return a new node representing the boolean built-in type. */
  public static Node booleanType() {
    return new Node(Token.BOOLEAN_TYPE);
  }

  /**
   * Equivalent to the UNKNOWN type in Closure, expressed with {@code {?}}
   *
   * @return a new node representing any type, without type checking.
   */
  public static Node anyType() {
    return new Node(Token.ANY_TYPE);
  }

  /** @return a new node representing the Void type as defined by TypeScript. */
  public static Node voidType() {
    return new Node(Token.VOID_TYPE);
  }

  /** @return a new node representing the Undefined type as defined by TypeScript. */
  public static Node undefinedType() {
    return new Node(Token.UNDEFINED_TYPE);
  }

  /**
   * Splits a '.' separated qualified name into a tree of type segments.
   *
   * @param typeName a qualified name such as "goog.ui.Window"
   * @return a new node representing the type
   * @see #namedType(Iterable)
   */
  public static Node namedType(String typeName) {
    return namedType(Splitter.on('.').split(typeName));
  }

  /**
   * Produces a tree structure similar to the Rhino AST of a qualified name expression, under a
   * top-level NAMED_TYPE node.
   *
   * <p>Example:
   *
   * <pre>
   * NAMED_TYPE
   *   NAME goog
   *     STRING ui
   *       STRING Window
   * </pre>
   */
  public static Node namedType(Iterable<String> segments) {
    Iterator<String> segmentsIt = segments.iterator();
    Node node = IR.name(segmentsIt.next());
    while (segmentsIt.hasNext()) {
      node = IR.getprop(node, segmentsIt.next());
    }
    return new Node(Token.NAMED_TYPE, node);
  }

  /**
   * Represents a structural type. Closure calls this a Record Type and accepts the syntax {@code
   * {myNum: number, myObject}}
   *
   * <p>Example:
   *
   * <pre>
   * RECORD_TYPE
   *   STRING_KEY myNum
   *     NUMBER_TYPE
   *   STRING_KEY myObject
   * </pre>
   *
   * @param properties a map from property name to property type
   * @return a new node representing the record type
   */
  public static Node recordType(LinkedHashMap<String, Node> properties) {
    Node node = new Node(Token.RECORD_TYPE);
    for (Map.Entry<String, Node> prop : properties.entrySet()) {
      Node stringKey = IR.stringKey(prop.getKey());
      node.addChildToBack(stringKey);
      if (prop.getValue() != null) {
        stringKey.addChildToFront(prop.getValue());
      }
    }
    return node;
  }

  private static Node maybeAddType(Node node, Node type) {
    if (type != null) {
      node.setDeclaredTypeExpression(type);
    }
    return node;
  }

  /**
   * Represents a function type. Closure has syntax like {@code {function(string, boolean):number}}
   * Closure doesn't include parameter names. If the parameter types are unnamed, arbitrary names
   * can be substituted, eg. p1, p2, etc.
   *
   * <p>Example:
   *
   * <pre>
   * FUNCTION_TYPE
   *   NUMBER_TYPE
   *   STRING_KEY p1 [declared_type_expr: STRING_TYPE]
   *   STRING_KEY p2 [declared_type_expr: BOOLEAN_TYPE]
   * </pre>
   *
   * @param returnType the type returned by the function, possibly ANY_TYPE
   * @param requiredParams the names and types of the required parameters.
   * @param optionalParams the names and types of the optional parameters.
   * @param restName the name of the rest parameter, if any.
   * @param restType the type of the rest parameter, if any.
   */
  public static Node functionType(
      Node returnType,
      LinkedHashMap<String, Node> requiredParams,
      LinkedHashMap<String, Node> optionalParams,
      String restName,
      Node restType) {
    Node node = new Node(Token.FUNCTION_TYPE, returnType);
    checkNotNull(requiredParams);
    checkNotNull(optionalParams);

    for (Map.Entry<String, Node> param : requiredParams.entrySet()) {
      Node name = IR.name(param.getKey());
      node.addChildToBack(maybeAddType(name, param.getValue()));
    }

    for (Map.Entry<String, Node> param : optionalParams.entrySet()) {
      Node name = IR.name(param.getKey());
      name.putBooleanProp(Node.OPT_ES6_TYPED, true);
      node.addChildToBack(maybeAddType(name, param.getValue()));
    }

    if (restName != null) {
      Node rest = new Node(Token.ITER_REST, IR.name(restName));
      node.addChildToBack(maybeAddType(rest, restType));
    }
    return node;
  }

  /**
   * Represents a parameterized, or generic, type. Closure calls this a Type Application and accepts
   * syntax like {@code {Object.<string, number>}}
   *
   * <p>Example:
   *
   * <pre>
   * PARAMETERIZED_TYPE
   *   NAMED_TYPE
   *     NAME Object
   *   STRING_TYPE
   *   NUMBER_TYPE
   * </pre>
   *
   * @param baseType
   * @param typeParameters
   */
  public static Node parameterizedType(Node baseType, Iterable<Node> typeParameters) {
    if (Iterables.isEmpty(typeParameters)) {
      return baseType;
    }
    Node node = new Node(Token.PARAMETERIZED_TYPE, baseType);
    for (Node typeParameter : typeParameters) {
      node.addChildToBack(typeParameter);
    }
    return node;
  }

  /**
   * Represents an array type. In Closure, this is represented by a {@link #parameterizedType(Node,
   * Iterable) parameterized type} of {@code Array} with {@code elementType} as the sole type
   * parameter.
   *
   * <p>Example
   *
   * <pre>
   * ARRAY_TYPE
   *   elementType
   * </pre>
   */
  public static Node arrayType(Node elementType) {
    return new Node(Token.ARRAY_TYPE, elementType);
  }

  /**
   * Represents a union type, which can be one of the given types. Closure accepts syntax like
   * {@code {(number|boolean)}}
   *
   * <p>Example:
   *
   * <pre>
   * UNION_TYPE
   *   NUMBER_TYPE
   *   BOOLEAN_TYPE
   * </pre>
   *
   * @param options the types which are accepted
   * @return a new node representing the union type
   */
  public static Node unionType(Iterable<Node> options) {
    checkArgument(!Iterables.isEmpty(options), "union must have at least one option");
    Node node = new Node(Token.UNION_TYPE);
    for (Node option : options) {
      node.addChildToBack(option);
    }
    return node;
  }

  public static Node unionType(Node... options) {
    return unionType(Arrays.asList(options));
  }

  /**
   * Represents a function parameter that is optional. In closure syntax, this is {@code
   * function(?string=, number=)} In TypeScript syntax, it is {@code (firstName: string, lastName?:
   * string)=>string}
   *
   * @param parameterType the type of the parameter
   * @return a new node representing the function parameter type
   */
  public static Node optionalParameter(Node parameterType) {
    return new Node(Token.OPTIONAL_PARAMETER, parameterType);
  }
}
