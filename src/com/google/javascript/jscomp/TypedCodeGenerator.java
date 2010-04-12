/*
 * Copyright 2009 Google Inc.
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
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionPrototypeType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;

import java.nio.charset.Charset;


/**
 * A code generator that outputs type annotations for functions and
 * constructors.
*
 */
class TypedCodeGenerator extends CodeGenerator {
  TypedCodeGenerator(CodeConsumer consumer, Charset outputCharset) {
    super(consumer, outputCharset, true);
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
        Node rhs = n.getFirstChild().getFirstChild();
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
   * @param node A node for a function for which to generate a type annotation
   */
  private String getFunctionAnnotation(Node node) {
    StringBuilder sb = new StringBuilder("/**\n");

    JSType type = node.getJSType();

    if (type == null || type.isUnknownType()) {
      return "";
    }

    FunctionType funType = (FunctionType) node.getJSType();

    // We need to use the child nodes of the function as the nodes for the
    // parameters of the function type do not have the real parameter names.
    // FUNCTION
    //   NAME
    //   LP
    //     NAME param1
    //     NAME param2
    Node fnNode = funType.getSource();
    if (fnNode != null) {
      Node paramNode = NodeUtil.getFnParameters(fnNode).getFirstChild();

      // Param types
      for (Node n : funType.getParameters()) {
        // Bail out if the paramNode is not there.
        if (paramNode == null) {
          break;
        }
        sb.append(" * @param {" + n.getJSType() + "} ");
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

      for (ObjectType interfaze : funType.getImplementedInterfaces()) {
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
}
