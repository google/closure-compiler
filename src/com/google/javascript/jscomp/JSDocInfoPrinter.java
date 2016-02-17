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
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.Iterator;

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
    if (info.isInterface() && !info.usesImplicitMatch()) {
      sb.append("@interface ");
    }
    if (info.isInterface() && info.usesImplicitMatch()) {
      sb.append("@record ");
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
    if (info.isConstant() && !info.isDefine()) {
      sb.append("@const ");
    }
    if (info.isDeprecated()) {
      sb.append("@deprecated ");
      sb.append(info.getDeprecationReason() + "\n");
    }
    if (info.isExport()) {
      sb.append("@export ");
    } else if (info.getVisibility() != null
        && info.getVisibility() != Visibility.INHERITED) {
      sb.append("@" + info.getVisibility().toString().toLowerCase() + " ");
    }

    Iterator<String> suppressions = info.getSuppressions().iterator();
    if (suppressions.hasNext()) {
      sb.append("@suppress {");
      while (suppressions.hasNext()) {
        sb.append(suppressions.next());
        if (suppressions.hasNext()) {
          sb.append(",");
        }
      }
      sb.append("}\n");
    }

    ImmutableList<String> names = info.getTemplateTypeNames();
    if (!names.isEmpty()) {
      sb.append("@template ");
      Joiner.on(',').appendTo(sb, names);
      sb.append("\n"); // @template needs a newline afterwards
    }

    if (info.getParameterCount() > 0) {
      for (String name : info.getParameterNames()) {
        sb.append("\n@param ");
        if (info.getParameterType(name) != null) {
          sb.append("{");
          appendTypeNode(sb, info.getParameterType(name).getRoot());
          sb.append("} ");
        }
        sb.append(name);
        sb.append(' ');
      }
    }
    if (info.hasReturnType()) {
      sb.append("\n@return {");
      appendTypeNode(sb, info.getReturnType().getRoot());
      sb.append("} ");
    }
    if (info.hasThisType()) {
      sb.append("\n@this {");
      Node typeNode = info.getThisType().getRoot();
      if (typeNode.getType() == Token.BANG) {
        appendTypeNode(sb, typeNode.getFirstChild());
      } else {
        appendTypeNode(sb, typeNode);
      }
      sb.append("} ");
    }
    if (info.hasBaseType()) {
      sb.append("\n@extends {");
      Node typeNode = info.getBaseType().getRoot();
      if (typeNode.getType() == Token.BANG) {
        appendTypeNode(sb, typeNode.getFirstChild());
      } else {
        appendTypeNode(sb, typeNode);
      }
      sb.append("} ");
    }
    for (JSTypeExpression type : info.getExtendedInterfaces()) {
      sb.append("\n@extends {");
      Node typeNode = type.getRoot();
      if (typeNode.getType() == Token.BANG) {
        appendTypeNode(sb, typeNode.getFirstChild());
      } else {
        appendTypeNode(sb, typeNode);
      }
      sb.append("} ");
    }
    for (JSTypeExpression type : info.getImplementedInterfaces()) {
      sb.append("\n@implements {");
      Node typeNode = type.getRoot();
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
    if (info.hasType() && !info.isDefine()) {
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
      sb.append("(");
      for (int i = 0; i < typeNode.getChildCount() - 1; i++) {
        appendTypeNode(sb, typeNode.getChildAtIndex(i));
        sb.append("|");
      }
      appendTypeNode(sb, typeNode.getLastChild());
      sb.append(")");
    } else if (typeNode.getType() == Token.ELLIPSIS) {
      sb.append("...");
      if (typeNode.hasChildren()) {
        appendTypeNode(sb, typeNode.getFirstChild());
      }
    } else if (typeNode.getType() == Token.STAR) {
      sb.append("*");
    } else if (typeNode.getType() == Token.QMARK) {
      sb.append("?");
      if (typeNode.hasChildren()) {
        appendTypeNode(sb, typeNode.getFirstChild());
      }
    } else if (typeNode.isFunction()) {
      appendFunctionNode(sb, typeNode);
    } else if (typeNode.getType() == Token.LC) {
      sb.append("{");
      Node lb = typeNode.getFirstChild();
      for (int i = 0; i < lb.getChildCount() - 1; i++) {
        Node colon = lb.getChildAtIndex(i);
        if (colon.hasChildren()) {
          sb.append(colon.getFirstChild().getString() + ":");
          appendTypeNode(sb, colon.getLastChild());
        } else {
          sb.append(colon.getString());
        }
        sb.append(",");
      }
      Node lastColon = lb.getLastChild();
      if (lastColon.hasChildren()) {
        sb.append(lastColon.getFirstChild().getString() + ":");
        appendTypeNode(sb, lastColon.getLastChild());
      } else {
        sb.append(lastColon.getString());
      }
      sb.append("}");
    } else if (typeNode.getType() == Token.VOID) {
      sb.append("void");
    } else {
      if (typeNode.hasChildren()) {
        sb.append(typeNode.getString())
            .append("<");
        Node child = typeNode.getFirstChild();
        appendTypeNode(sb, child.getFirstChild());
        for (int i = 1; i < child.getChildCount(); i++) {
          sb.append(",");
          appendTypeNode(sb, child.getChildAtIndex(i));
        }
        sb.append(">");
      } else {
        sb.append(typeNode.getString());
      }
    }
  }

  private static void appendFunctionNode(StringBuilder sb, Node function) {
    boolean hasNewOrThis = false;
    sb.append("function(");
    Node first = function.getFirstChild();
    if (first.isNew()) {
      sb.append("new:");
      appendTypeNode(sb, first.getFirstChild());
      hasNewOrThis = true;
    } else if (first.isThis()) {
      sb.append("this:");
      appendTypeNode(sb, first.getFirstChild());
      hasNewOrThis = true;
    } else if (first.isEmpty()) {
      sb.append(")");
      return;
    } else if (!first.isParamList()) {
      sb.append("):");
      appendTypeNode(sb, first);
      return;
    }
    Node paramList = null;
    if (first.isParamList()) {
      paramList = first;
    } else if (first.getNext().isParamList()) {
      paramList = first.getNext();
    }
    if (paramList != null) {
      boolean firstParam = true;
      for (Node param : paramList.children()) {
        if (!firstParam || hasNewOrThis) {
          sb.append(",");
        }
        appendTypeNode(sb, param);
        firstParam = false;
      }
    }
    sb.append(")");

    Node returnType = function.getLastChild();
    if (!returnType.isEmpty()) {
      sb.append(":");
      appendTypeNode(sb, returnType);
    }
  }
}
