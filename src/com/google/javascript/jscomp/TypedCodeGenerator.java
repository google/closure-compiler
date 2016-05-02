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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.TypeIRegistry;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.ObjectType;

import java.util.Set;
import java.util.TreeSet;

/**
 * A code generator that outputs type annotations for functions and
 * constructors.
 */
class TypedCodeGenerator extends CodeGenerator {
  private final TypeIRegistry registry;

  TypedCodeGenerator(
      CodeConsumer consumer, CompilerOptions options, TypeIRegistry registry) {
    super(consumer, options);
    Preconditions.checkNotNull(registry);
    this.registry = registry;
  }

  @Override
  void add(Node n, Context context) {
    Node parent = n.getParent();
    if (parent != null
        && (parent.isBlock()
            || parent.isScript())) {
      if (n.isFunction()) {
        add(getFunctionAnnotation(n));
      } else if (n.isExprResult()
          && n.getFirstChild().isAssign()) {
        Node assign = n.getFirstChild();
        if (NodeUtil.isNamespaceDecl(assign.getFirstChild())) {
          add(JSDocInfoPrinter.print(assign.getJSDocInfo()));
        } else {
          Node rhs = assign.getLastChild();
          add(getTypeAnnotation(rhs));
        }
      } else if (n.isVar()
          && n.getFirstFirstChild() != null) {
        if (NodeUtil.isNamespaceDecl(n.getFirstChild())) {
          add(JSDocInfoPrinter.print(n.getJSDocInfo()));
        } else {
          add(getTypeAnnotation(n.getFirstFirstChild()));
        }
      }
    }

    super.add(n, context);
  }

  private String getTypeAnnotation(Node node) {
    // Only add annotations for things with JSDoc, or function literals.
    JSDocInfo jsdoc = NodeUtil.getBestJSDocInfo(node);
    if (jsdoc == null && !node.isFunction()) {
      return "";
    }

    JSType type = node.getJSType();
    if (type == null) {
      return "";
    } else if (type.isFunctionType()) {
      return getFunctionAnnotation(node);
    } else if (type.isEnumType()) {
      return "/** @enum {" +
          type.toMaybeEnumType().getElementsType().toAnnotationString() +
          "} */\n";
    } else if (!type.isUnknownType()
        && !type.isEmptyType()
        && !type.isVoidType()
        && !type.isFunctionPrototypeType()) {
      return "/** @type {" + node.getJSType().toAnnotationString() + "} */\n";
    } else {
      return "";
    }
  }

  /**
   * @param fnNode A node for a function for which to generate a type annotation
   */
  private String getFunctionAnnotation(Node fnNode) {
    JSType type = fnNode.getJSType();
    Preconditions.checkState(fnNode.isFunction() || type.isFunctionType());

    if (type == null || type.isUnknownType()) {
      return "";
    }

    FunctionType funType = type.toMaybeFunctionType();

    if (JSType.isEquivalent(
        type, (JSType) registry.getNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE))) {
      return "/** @type {!Function} */\n";
    }

    StringBuilder sb = new StringBuilder("/**\n");


    Node paramNode = null;
    // We need to use the child nodes of the function as the nodes for the
    // parameters of the function type do not have the real parameter names.
    // FUNCTION
    //   NAME
    //   LP
    //     NAME param1
    //     NAME param2
    if (fnNode != null && fnNode.isFunction()) {
      paramNode = NodeUtil.getFunctionParameters(fnNode).getFirstChild();
    }

    // Param types
    int i = 0;
    for (Node n : funType.getParameters()) {
      sb.append(" * ");
      appendAnnotation(sb, "param", getParameterNodeJSDocType(n));
      sb.append(" ")
          .append(paramNode == null ? "p" + i : paramNode.getString())
          .append("\n");
      if (paramNode != null) {
        paramNode = paramNode.getNext();
      } else {
        i++;
      }
    }

    // Return type
    JSType retType = funType.getReturnType();
    if (retType != null &&
        !retType.isEmptyType() && // There is no annotation for the empty type.
        !funType.isInterface() && // Interfaces never return a value.
        !(funType.isConstructor() && retType.isVoidType())) {
      sb.append(" * ");
      appendAnnotation(sb, "return", retType.toNonNullAnnotationString());
      sb.append("\n");
    }

    // Constructor/interface
    if (funType.isConstructor() || funType.isInterface()) {

      FunctionType superConstructor = funType.getSuperClassConstructor();

      if (superConstructor != null) {
        ObjectType superInstance =
          funType.getSuperClassConstructor().getInstanceType();
        if (!superInstance.toString().equals("Object")) {
          sb.append(" * ");
          appendAnnotation(sb, "extends", superInstance.toAnnotationString());
          sb.append("\n");
        }
      }

      if (funType.isInterface()) {
        for (ObjectType interfaceType : funType.getExtendedInterfaces()) {
          sb.append(" * ");
          appendAnnotation(sb, "extends", interfaceType.toAnnotationString());
          sb.append("\n");
        }
      }

      // Avoid duplicates, add implemented type to a set first
      Set<String> interfaces = new TreeSet<>();
      for (ObjectType interfaze : funType.getImplementedInterfaces()) {
        interfaces.add(interfaze.toAnnotationString());
      }
      for (String interfaze : interfaces) {
        sb.append(" * ");
        appendAnnotation(sb, "implements", interfaze);
        sb.append("\n");
      }

      if (funType.isConstructor()) {
        sb.append(" * @constructor\n");
      } else if (funType.isInterface()) {
        sb.append(" * @interface\n");
      }
    }

    if (!funType.getTemplateTypeMap().getTemplateKeys().isEmpty()) {
      sb.append(" * @template ");
      Joiner.on(",").appendTo(sb, funType.getTemplateTypeMap().getTemplateKeys());
      sb.append("\n");
    }

    sb.append(" */\n");
    return sb.toString();
  }

  private static void appendAnnotation(StringBuilder sb, String name, String type) {
    sb.append("@").append(name).append(" {").append(type).append("}");
  }

  /**
   * Creates a JSDoc-suitable String representation the type of a parameter.
   *
   * @param parameterNode The parameter node.
   */
  private String getParameterNodeJSDocType(Node parameterNode) {
    JSType parameterType = parameterNode.getJSType();
    String typeString;

    if (parameterNode.isOptionalArg()) {
      typeString = restrictByUndefined(parameterType).toNonNullAnnotationString() +
          "=";
    } else if (parameterNode.isVarArgs()) {
      typeString = "..." +
          restrictByUndefined(parameterType).toNonNullAnnotationString();
    } else {
      typeString = parameterType.toNonNullAnnotationString();
    }

    return typeString;
  }

  private JSType restrictByUndefined(JSType type) {
    if (type.isUnionType()) {
      return type.toMaybeUnionType().getRestrictedUnion(
          (JSType) registry.getNativeType(JSTypeNative.VOID_TYPE));
    }
    return type;
  }
}
