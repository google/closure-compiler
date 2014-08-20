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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Prints a JSDocInfo, used for preserving type annotations in ES6 transpilation.
 *
 */
public final class JSDocInfoPrinter {
  public static String print(JSDocInfo info) {
    StringBuilder sb = new StringBuilder("/**");
    if (info.isConstructor()) {
      sb.append("@constructor ");
    }
    if (info.isInterface()) {
      sb.append("@interface ");
    }
    if (info.makesDicts()) {
      sb.append("@dict ");
    }
    if (info.makesStructs()) {
      sb.append("@struct ");
    }
    if (info.makesUnrestricted()) {
      sb.append("@unrestricted ");
    }
    if (info.isDefine()) {
      sb.append("@define {");
      appendTypeNode(sb, info.getType().getRoot());
      sb.append("} ");
    }

    if (info.isOverride()) {
      sb.append("@override ");
    }
    if (info.isConstant()) {
      sb.append("@const ");
    }
    if (info.isDeprecated()) {
      sb.append("@deprecated ");
      sb.append(info.getDeprecationReason() + " ");
    }
    if (info.getVisibility() != null
        && info.getVisibility() != Visibility.INHERITED) {
      sb.append("@" + info.getVisibility().toString().toLowerCase() + " ");
    }

    for (String suppression : info.getSuppressions()) {
      sb.append("@suppress {" + suppression + "} ");
    }

    ImmutableList<String> names = info.getTemplateTypeNames();
    if (!names.isEmpty()) {
      sb.append("@template ");
      Joiner.on(',').appendTo(sb, names);
      sb.append("\n"); // @template needs a newline afterwards
    }

    if (info.getParameterCount() > 0) {
      for (String name : info.getParameterNames()) {
        sb.append("@param {");
        appendTypeNode(sb, info.getParameterType(name).getRoot());
        sb.append("} " + name + " ");
      }
    }
    if (info.hasReturnType()) {
      sb.append("@return {");
      appendTypeNode(sb, info.getReturnType().getRoot());
      sb.append("} ");
    }
    if (info.hasThisType()) {
      sb.append("@this {");
      Node typeNode = info.getThisType().getRoot();
      if (typeNode.getType() == Token.BANG) {
        appendTypeNode(sb, typeNode.getFirstChild());
      } else {
        appendTypeNode(sb, typeNode);
      }
      sb.append("} ");
    }
    if (info.hasBaseType()) {
      sb.append("@extends {");
      Node typeNode = info.getBaseType().getRoot();
      if (typeNode.getType() == Token.BANG) {
        appendTypeNode(sb, typeNode.getFirstChild());
      } else {
        appendTypeNode(sb, typeNode);
      }
      sb.append("} ");
    }
    if (info.hasTypedefType()) {
      sb.append("@typedef {");
      appendTypeNode(sb, info.getTypedefType().getRoot());
      sb.append("} ");
    }
    if (info.hasType()) {
      if (info.isInlineType()) {
        sb.append(" ");
        appendTypeNode(sb, info.getType().getRoot());
        sb.append(" ");
      } else {
        sb.append("@type {");
        appendTypeNode(sb, info.getType().getRoot());
        sb.append("} ");
      }
    }
    if (!info.getThrownTypes().isEmpty()) {
      sb.append("@throws {");
      appendTypeNode(sb, info.getThrownTypes().get(0).getRoot());
      sb.append("} ");
    }
    if (info.hasEnumParameterType()) {
      sb.append("@enum {");
      appendTypeNode(sb, info.getEnumParameterType().getRoot());
      sb.append("} ");
    }
    sb.append("*/");
    return sb.toString();
  }

  private static void appendTypeNode(StringBuilder sb, Node typeNode) {
    if (typeNode.getType() == Token.BANG) {
      sb.append("!");
      appendTypeNode(sb, typeNode.getFirstChild());
    } else if (typeNode.getType() == Token.EQUALS) {
      appendTypeNode(sb, typeNode.getFirstChild());
      sb.append("=");
    } else if (typeNode.getType() == Token.PIPE) {
      for (int i = 0; i < typeNode.getChildCount() - 1; i++) {
        appendTypeNode(sb, typeNode.getChildAtIndex(i));
        sb.append("|");
      }
      appendTypeNode(sb, typeNode.getLastChild());
    } else if (typeNode.getType() == Token.ELLIPSIS) {
      sb.append("...");
      if (typeNode.hasChildren()) {
        boolean inFunction = typeNode.getParent() != null
            && typeNode.getParent().getParent() != null
            && typeNode.getParent().getParent().isFunction();
        if (inFunction) {
          sb.append("[");
        }
        appendTypeNode(sb, typeNode.getFirstChild());
        if (inFunction) {
          sb.append("]");
        }
      }
    } else if (typeNode.getType() == Token.STAR) {
      sb.append("*");
    } else if (typeNode.getType() == Token.QMARK) {
      sb.append("?");
    } else if (typeNode.isFunction()) {
      sb.append("function(");
      Node first = typeNode.getFirstChild();
      if (first.isNew()) {
        sb.append("new:");
        appendTypeNode(sb, typeNode.getFirstChild().getFirstChild());
        sb.append(",");
      } else if (first.isThis()) {
        sb.append("this:");
        appendTypeNode(sb, typeNode.getFirstChild().getFirstChild());
        sb.append(",");
      } else if (first.isEmpty()) {
        sb.append(")");
        return;
      } else if (first.isVoid()) {
        sb.append("):void");
        return;
      }
      Node paramList = typeNode.getFirstChild().isParamList()
          ? typeNode.getFirstChild()
          : typeNode.getChildAtIndex(1);
      for (int i = 0; i < paramList.getChildCount() - 1; i++) {
        appendTypeNode(sb, paramList.getChildAtIndex(i));
        sb.append(",");
      }
      appendTypeNode(sb, paramList.getLastChild());
      sb.append(")");
      Node returnType = typeNode.getLastChild();
      if (!returnType.isEmpty()) {
        sb.append(":");
        appendTypeNode(sb, returnType);
      }
    } else if (typeNode.getType() == Token.LC) {
      sb.append("{");
      Node lb = typeNode.getFirstChild();
      for (int i = 0; i < lb.getChildCount() - 1; i++) {
        Node colon = lb.getChildAtIndex(i);
        sb.append(colon.getFirstChild().getString() + ":");
        appendTypeNode(sb, colon.getLastChild());
        sb.append(",");
      }
      Node lastColon = lb.getLastChild();
      sb.append(lastColon.getFirstChild().getString() + ":");
      appendTypeNode(sb, lastColon.getLastChild());
      sb.append("}");
    } else if (typeNode.getType() == Token.VOID) {
      sb.append("void");
    } else {
      if (typeNode.getString().equals("Array")) {
        if (typeNode.hasChildren()) {
          sb.append("Array.<");
          appendTypeNode(sb, typeNode.getFirstChild().getFirstChild());
          sb.append(">");
        } else {
          sb.append("Array");
        }
      } else {
        sb.append(typeNode.getString());
      }
    }
  }
}
