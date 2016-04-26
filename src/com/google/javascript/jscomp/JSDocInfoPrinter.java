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
import com.google.common.collect.Ordering;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Prints a JSDocInfo, used for preserving type annotations in ES6 transpilation.
 *
 */
public final class JSDocInfoPrinter {
  public static String print(JSDocInfo info) {
    boolean multiline = false;

    List<String> parts = new ArrayList<>();

    // order:
    //   export|public|private|package|protected
    //   const
    //   dict|struct|unrestricted
    //   constructor|interface|record
    //   extends
    //   implements
    //   this
    //   param
    //   return
    //   throws
    //   template
    //   override
    //   type|define|typedef|enum
    //   suppress
    //   deprecated
    parts.add("/**");

    if (info.isExport()) {
      parts.add("@export");
    } else if (info.getVisibility() != null
        && info.getVisibility() != Visibility.INHERITED) {
      parts.add("@" + info.getVisibility().toString().toLowerCase());
    }

    if (info.isConstant() && !info.isDefine()) {
      parts.add("@const");
    }

    if (info.makesDicts()) {
      parts.add("@dict");
    }

    if (info.makesStructs()) {
      parts.add("@struct");
    }

    if (info.makesUnrestricted()) {
      parts.add("@unrestricted ");
    }

    if (info.isConstructor()) {
      parts.add("@constructor");
    }

    if (info.isInterface() && !info.usesImplicitMatch()) {
      parts.add("@interface");
    }

    if (info.isInterface() && info.usesImplicitMatch()) {
      parts.add("@record");
    }

    if (info.hasBaseType()) {
      multiline = true;
      Node typeNode = stripBang(info.getBaseType().getRoot());
      parts.add(buildAnnotationWithType("extends", typeNode));
    }

    for (JSTypeExpression type : info.getExtendedInterfaces()) {
      multiline = true;
      Node typeNode = stripBang(type.getRoot());
      parts.add(buildAnnotationWithType("extends", typeNode));
    }

    for (JSTypeExpression type : info.getImplementedInterfaces()) {
      multiline = true;
      Node typeNode = stripBang(type.getRoot());
      parts.add(buildAnnotationWithType("implements", typeNode));
    }

    if (info.hasThisType()) {
      multiline = true;
      Node typeNode = stripBang(info.getThisType().getRoot());
      parts.add(buildAnnotationWithType("this", typeNode));
    }

    if (info.getParameterCount() > 0) {
      multiline = true;
      for (String name : info.getParameterNames()) {
        parts.add("@param " + buildParamType(name, info.getParameterType(name)));
      }
    }

    if (info.hasReturnType()) {
      multiline = true;
      parts.add(buildAnnotationWithType("return", info.getReturnType()));
    }

    if (!info.getThrownTypes().isEmpty()) {
      parts.add(buildAnnotationWithType("throws", info.getThrownTypes().get(0)));
    }

    ImmutableList<String> names = info.getTemplateTypeNames();
    if (!names.isEmpty()) {
      parts.add("@template " + Joiner.on(',').join(names));
      multiline = true;
    }

    if (info.isOverride()) {
      parts.add("@override");
    }

   if (info.hasType() && !info.isDefine()) {
      if (info.isInlineType()) {
        parts.add(typeNode(info.getType().getRoot()));
      } else {
        parts.add(buildAnnotationWithType("type", info.getType()));
      }
    }

    if (info.isDefine()) {
      parts.add(buildAnnotationWithType("define", info.getType()));
    }

    if (info.hasTypedefType()) {
      parts.add(buildAnnotationWithType("typedef", info.getTypedefType()));
    }

    if (info.hasEnumParameterType()) {
      parts.add(buildAnnotationWithType("enum", info.getEnumParameterType()));
    }

    Set<String> suppressions = info.getSuppressions();
    if (!suppressions.isEmpty()) {
      // Print suppressions in sorted order to avoid non-deterministic output.
      String[] arr = suppressions.toArray(new String[0]);
      Arrays.sort(arr, Ordering.<String>natural());
      parts.add("@suppress {" + Joiner.on(',').join(arr) + "}");
      multiline = true;
    }

    if (info.isDeprecated()) {
      parts.add("@deprecated " + info.getDeprecationReason());
      multiline = true;
    }

    parts.add("*/");

    StringBuilder sb = new StringBuilder();
    if (multiline) {
      Joiner.on("\n ").appendTo(sb, parts);
    } else {
      Joiner.on(" ").appendTo(sb, parts);
    }
    sb.append((multiline) ? "\n" : " ");
    return sb.toString();
  }

  private static Node stripBang(Node typeNode) {
    if (typeNode.getType() == Token.BANG) {
      typeNode = typeNode.getFirstChild();
    }
    return typeNode;
  }

  private static String buildAnnotationWithType(String annotation, JSTypeExpression type) {
    return buildAnnotationWithType(annotation, type.getRoot());
  }

  private static String buildAnnotationWithType(String annotation, Node type) {
    StringBuilder sb = new StringBuilder();
    sb.append("@");
    sb.append(annotation);
    sb.append(" {");
    appendTypeNode(sb, type);
    sb.append("}");
    return sb.toString();
  }

  private static String buildParamType(String name, JSTypeExpression type) {
    if (type != null) {
      return "{" + typeNode(type.getRoot()) + "} " + name;
    } else {
      return name;
    }
  }

  private static String typeNode(Node typeNode) {
    StringBuilder sb = new StringBuilder();
    appendTypeNode(sb, typeNode);
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
          sb.append(colon.getFirstChild().getString()).append(":");
          appendTypeNode(sb, colon.getLastChild());
        } else {
          sb.append(colon.getString());
        }
        sb.append(",");
      }
      Node lastColon = lb.getLastChild();
      if (lastColon.hasChildren()) {
        sb.append(lastColon.getFirstChild().getString()).append(":");
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
