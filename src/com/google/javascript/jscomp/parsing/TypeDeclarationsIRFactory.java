/*
 * Copyright 2015 The Closure Compiler Authors.
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

package com.google.javascript.jscomp.parsing;

import static com.google.javascript.rhino.Node.TypeDeclarationNode;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Produces ASTs which represent JavaScript type declarations, both those created from
 * closure-style type declarations in a JSDoc node (via a conversion from the rhino AST
 * produced in {@link com.google.javascript.jscomp.parsing.NewIRFactory}) as well as
 * those created from TypeScript-style inline type declarations.
 *
 * This is an alternative to the AST found in the root property of JSTypeExpression, which
 * is a crufty AST that reuses language tokens.
 */
public class TypeDeclarationsIRFactory {

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
   * @return a new node representing the null built-in type.
   */
  public static TypeDeclarationNode nullType() {
    return new TypeDeclarationNode(Token.NULL_TYPE);
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
   * Produces a tree structure similar to the Rhino AST of a qualified name expression, under
   * a top-level NAMED_TYPE node.
   *
   * <p>Example:
   * <pre>
   * NAMED_TYPE
   *   NAME goog
   *     STRING ui
   *       STRING Window
   * </pre>
   *
   * @param typeName a qualified name such as "goog.ui.Window"
   * @return a new node representing the type
   */
  public static TypeDeclarationNode namedType(String typeName) {
    Iterator<String> parts = Splitter.on('.').split(typeName).iterator();
    Node node = IR.name(parts.next());
    while (parts.hasNext()) {
      node = IR.getprop(node, IR.string(parts.next()));
    }
    return new TypeDeclarationNode(Token.NAMED_TYPE, node);
  }

  /**
   * Represents a structural type.
   * Closure calls this a Record Type and accepts the syntax {@code {myNum: number, myObject}}
   *
   * <p>Example:
   * <pre>
   * RECORD_TYPE
   *   STRING_KEY myNum
   *     NUMBER_TYPE
   *   STRING myObject
   * </pre>
   * @param properties a map from property name to property type
   * @return a new node representing the record type
   */
  public static TypeDeclarationNode recordType(
      LinkedHashMap<String, TypeDeclarationNode> properties) {
    TypeDeclarationNode node = new TypeDeclarationNode(Token.RECORD_TYPE);
    for (Map.Entry<String, TypeDeclarationNode> property : properties.entrySet()) {
      if (property.getValue() == null) {
        node.addChildrenToBack(IR.string(property.getKey()));
      } else {
        Node stringKey = IR.stringKey(property.getKey());
        stringKey.addChildToFront(property.getValue());
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
   * @return
   */
  public static TypeDeclarationNode functionType(
      Node returnType, LinkedHashMap<String, TypeDeclarationNode> parameters) {
    TypeDeclarationNode node = new TypeDeclarationNode(Token.FUNCTION_TYPE, returnType);
    for (Map.Entry<String, TypeDeclarationNode> parameter : parameters.entrySet()) {
      Node stringKey = IR.stringKey(parameter.getKey());
      stringKey.addChildToFront(parameter.getValue());
      node.addChildToBack(stringKey);
    }
    return node;
  }

  /**
   * Represents a parameterized, or generic, type.
   * Closure calls this a Type Application and accepts syntax like {@code {Object.<string, number>}}
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
   * @return
   */
  public static TypeDeclarationNode parameterizedType(
      TypeDeclarationNode baseType, Iterable<TypeDeclarationNode> typeParameters) {
    TypeDeclarationNode node = new TypeDeclarationNode(Token.PARAMETERIZED_TYPE, baseType);
    for (Node typeParameter : typeParameters) {
      node.addChildToBack(typeParameter);
    }
    return node;
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
    Preconditions.checkArgument(!Iterables.isEmpty(options), "union must have at least one option");
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
   *   STRING_KEY p2
   *     REST_PARAMETER_TYPE
   *       NUMBER_TYPE
   * </pre>
   * @param type the type each of the parameters should have.
   *             (NB: TypeScript instead gives the array type that is seen inside the function)
   * @return a new node representing the function parameter type
   */
  public static TypeDeclarationNode restParams(TypeDeclarationNode type) {
    return new TypeDeclarationNode(Token.REST_PARAMETER_TYPE, type);
  }

  /**
   * Represents a function parameter that is optional.
   * In closure syntax, this is {@code function(?string=, number=)}
   * In TypeScript syntax, it is {@code (firstName: string, lastName?: string)=>string}
   * @param parameterType the type of the parameter
   * @return a new node representing the function parameter type
   */
  public static TypeDeclarationNode optionalParameter(TypeDeclarationNode parameterType) {
    return new TypeDeclarationNode(Token.OPTIONAL_PARAMETER, parameterType);
  }

  // Allow functional-style Iterables.transform over collections of nodes.
  private static final Function<Node, TypeDeclarationNode> CONVERT_TYPE_NODE =
      new Function<Node, TypeDeclarationNode>() {
        @Override
        public TypeDeclarationNode apply(Node node) {
          return convertTypeNodeAST(node);
        }
      };

  @Nullable
  public static TypeDeclarationNode convert(@Nullable JSTypeExpression typeExpr) {
    if (typeExpr == null) {
      return null;
    }
    return convertTypeNodeAST(typeExpr.getRoot());
  }

  /**
   * The root of a JSTypeExpression is very different from an AST node, even
   * though we use the same Java class to represent them.
   * This function converts root nodes of JSTypeExpressions into TypeDeclaration ASTs,
   * to make them more similar to ordinary AST nodes.
   */
  // TODO(dimvar): Eventually, we want to just parse types to the new
  // representation directly, and delete this function.
  public static TypeDeclarationNode convertTypeNodeAST(Node n) {
    int token = n.getType();
    switch (token) {
      case Token.STAR:
        return unionType(
            namedType("Object"), numberType(), stringType(),
            booleanType(), nullType(), undefinedType());
      case Token.VOID:
        return undefinedType();
      case Token.EMPTY: // for function types that don't declare a return type
        return anyType();
      case Token.BANG:
        // TODO(alexeagle): capture nullability constraints once we know how to express them
        return convertTypeNodeAST(n.getFirstChild());
      case Token.STRING:
        String typeName = n.getString();
        switch (typeName) {
          case "boolean":
            return booleanType();
          case "null":
            return nullType();
          case "number":
            return numberType();
          case "string":
            return stringType();
          case "undefined":
          case "void":
            return undefinedType();
          default:
            TypeDeclarationNode root = namedType(typeName);
            if (n.getChildCount() > 0 && n.getFirstChild().isBlock()) {
              return parameterizedType(root,
                  Iterables.transform(n.getFirstChild().children(), CONVERT_TYPE_NODE));
            }
            return root;
        }
      case Token.QMARK:
        Node child = n.getFirstChild();
        return child == null ? anyType() : unionType(nullType(), convertTypeNodeAST(child));
      case Token.LC:
        LinkedHashMap<String, TypeDeclarationNode> properties = new LinkedHashMap<>();
        for (Node field : n.getFirstChild().children()) {
          boolean isFieldTypeDeclared = field.getType() == Token.COLON;
          Node fieldNameNode = isFieldTypeDeclared ? field.getFirstChild() : field;
          String fieldName = fieldNameNode.getString();
          if (fieldName.startsWith("'") || fieldName.startsWith("\"")) {
            fieldName = fieldName.substring(1, fieldName.length() - 1);
          }
          TypeDeclarationNode fieldType = isFieldTypeDeclared
              ? convertTypeNodeAST(field.getLastChild()) : null;
          properties.put(fieldName, fieldType);
        }
        return recordType(properties);
      case Token.PIPE:
        return unionType(Iterables.transform(n.children(), CONVERT_TYPE_NODE));
      case Token.ELLIPSIS:
        return restParams(convertTypeNodeAST(n.getFirstChild()));
      case Token.FUNCTION:
        Node returnType = anyType();
        LinkedHashMap<String, TypeDeclarationNode> parameters = new LinkedHashMap<>();
        for (Node child2 : n.children()) {
          if (child2.isParamList()) {
            int paramIdx = 1;
            for (Node param : child2.children()) {
              parameters.put("p" + paramIdx++, convertTypeNodeAST(param));
            }
          } else if (child2.isNew()) {
            // TODO(alexeagle): keep the constructor signatures on the tree, and emit them following
            // the syntax in TypeScript 1.4 spec, section 3.7.8 Constructor Type Literals
          } else if (child2.isThis()) {
            // Not expressable in TypeScript syntax, so we omit them from the tree.
            // They could be added as properties on the result node.
          } else {
            returnType = convertTypeNodeAST(child2);
          }
        }
        return functionType(returnType, parameters);
      case Token.EQUALS:
        return optionalParameter(convertTypeNodeAST(n.getFirstChild()));
      default:
        throw new IllegalArgumentException(
            "Unsupported node type: " + Token.name(n.getType())
                + " " + n.toStringTree());
    }
  }
}
