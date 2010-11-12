/*
 * Copyright 2009 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;

import java.nio.charset.Charset;
import java.util.Set;


/**
 * A code generator that outputs type annotations for functions and
 * constructors.
 */
class TypedCodeGenerator extends CodeGenerator {
  TypedCodeGenerator(CodeConsumer consumer, Charset outputCharset) {
    super(consumer, outputCharset);
  }

  @Override
  void add(Node n, Context context) {
    Node parent = n.getParent();
    if (parent != null
        && (parent.getType() == Token.BLOCK
            || parent.getType() == Token.SCRIPT)) {
      if (n.getType() == Token.FUNCTION) {
        add(getFunctionAnnotation(n));
      } else if (n.getType() == Token.EXPR_RESULT
          && n.getFirstChild().getType() == Token.ASSIGN) {
        Node rhs = n.getFirstChild().getLastChild();
        add(getTypeAnnotation(rhs));
      } else if (n.getType() == Token.VAR
          && n.getFirstChild().getFirstChild() != null
          && n.getFirstChild().getFirstChild().getType() == Token.FUNCTION) {
        add(getFunctionAnnotation(n.getFirstChild().getFirstChild()));
      }
    }

    super.add(n, context);
  }

  private String getTypeAnnotation(Node node) {
    JSType type = node.getJSType();
    if (type instanceof FunctionType) {
      return getFunctionAnnotation(node);
    } else if (type != null && !type.isUnknownType()
        && !type.isEmptyType() && !type.isVoidType() &&
        !type.isFunctionPrototypeType()) {
      return "/** @type {" + node.getJSType() + "} */\n";
    } else {
      return "";
    }
  }

  /**
   * @param fnNode A node for a function for which to generate a type annotation
   */
  private String getFunctionAnnotation(Node fnNode) {
    Preconditions.checkState(fnNode.getType() == Token.FUNCTION);
    StringBuilder sb = new StringBuilder("/**\n");

    JSType type = fnNode.getJSType();

    if (type == null || type.isUnknownType()) {
      return "";
    }

    FunctionType funType = (FunctionType) fnNode.getJSType();

    // We need to use the child nodes of the function as the nodes for the
    // parameters of the function type do not have the real parameter names.
    // FUNCTION
    //   NAME
    //   LP
    //     NAME param1
    //     NAME param2
    if (fnNode != null) {
      Node paramNode = NodeUtil.getFnParameters(fnNode).getFirstChild();

      // Param types
      for (Node n : funType.getParameters()) {
        // Bail out if the paramNode is not there.
        if (paramNode == null) {
          break;
        }
        sb.append(" * @param {" + getParameterNodeJSDocType(n) + "} ");
        sb.append(paramNode.getString());
        sb.append("\n");
        paramNode = paramNode.getNext();
      }
    }

    // Return type
    JSType retType = funType.getReturnType();
    if (retType != null && !retType.isUnknownType() && !retType.isEmptyType()) {
      sb.append(" * @return {" + retType + "}\n");
    }

    // Constructor/interface
    if (funType.isConstructor() || funType.isInterface()) {

      FunctionType superConstructor = funType.getSuperClassConstructor();

      if (superConstructor != null) {
        ObjectType superInstance =
          funType.getSuperClassConstructor().getInstanceType();
        if (!superInstance.toString().equals("Object")) {
          sb.append(" * @extends {"  + superInstance + "}\n");
        }
      }

      // Avoid duplicates, add implemented type to a set first
      Set<String> interfaces = Sets.newTreeSet();
      for (ObjectType interfaze : funType.getImplementedInterfaces()) {
        interfaces.add(interfaze.toString());
      }
      for (String interfaze : interfaces) {
        sb.append(" * @implements {"  + interfaze + "}\n");
      }

      if (funType.isConstructor()) {
        sb.append(" * @constructor\n");
      } else if (funType.isInterface()) {
        sb.append(" * @interface\n");
      }
    }

    if (fnNode != null && fnNode.getBooleanProp(Node.IS_DISPATCHER)) {
      sb.append(" * @javadispatch\n");
    }

    sb.append(" */\n");
    return sb.toString();
  }

  /**
   * Creates a JSDoc-suitable String representation the type of a parameter.
   *
   * @param parameterNode The parameter node.
   */
  private String getParameterNodeJSDocType(Node parameterNode) {
    JSType parameterType = parameterNode.getJSType();
    String typeString;

    // Emit unknown types as '*' (AllType) since '?' (UnknownType) is not
    // a valid JSDoc type.
    if (parameterType.isUnknownType()) {
      typeString = "*";
    } else {
      // Fix-up optional and vararg parameters to match JSDoc type language
      if (parameterNode.isOptionalArg()) {
        typeString = parameterType.restrictByNotNullOrUndefined() + "=";
      } else if (parameterNode.isVarArgs()) {
        typeString = "..." + parameterType.restrictByNotNullOrUndefined();
      } else {
        typeString = parameterType.toString();
      }
    }

    return typeString;
  }
}
