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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prints a JSDocInfo, used for preserving type annotations in ES6 transpilation.
 *
 */
public final class JSDocInfoPrinter {

  private final boolean useOriginalName;

  JSDocInfoPrinter(boolean useOriginalName) {
    this.useOriginalName = useOriginalName;
  }

  public String print(JSDocInfo info) {
    boolean multiline = false;

    List<String> parts = new ArrayList<>();

    // order:
    //   externs|typeSummary
    //   export|public|private|package|protected
    //   abstract
    //   lends
    //   const
    //   final
    //   desc
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
    //   implicitCast
    //   nocollapse
    //   suppress
    //   deprecated
    //   polymer
    //   polymerBehavior
    //   mixinFunction
    parts.add("/**");

    if (info.isExterns()) {
      parts.add("@externs");
    } else if (info.isTypeSummary()) {
      parts.add("@typeSummary");
    }

    if (info.isExport()) {
      parts.add("@export");
    } else if (info.getVisibility() != null
        && info.getVisibility() != Visibility.INHERITED) {
      parts.add("@" + info.getVisibility().toString().toLowerCase());
    }

    if (info.isAbstract()) {
      parts.add("@abstract");
    }

    if (info.hasLendsName()) {
      parts.add(buildAnnotationWithType("lends", info.getLendsName().getRoot()));
    }

    if (info.hasConstAnnotation() && !info.isDefine()) {
      parts.add("@const");
    }

    if (info.isFinal()) {
      parts.add("@final");
    }

    String description = info.getDescription();
    if (description != null) {
      parts.add("@desc " + description + '\n');
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

    ImmutableMap<String, Node> typeTransformations = info.getTypeTransformations();
    if (!typeTransformations.isEmpty()) {
      multiline = true;
      for (Map.Entry<String, Node> e : typeTransformations.entrySet()) {
        String name = e.getKey();
        String tranformationDefinition = new CodePrinter.Builder(e.getValue()).build();
        parts.add("@template " + name + " := " +  tranformationDefinition  + " =:");
      }
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

    if (info.isImplicitCast()) {
      parts.add("@implicitCast");
    }

    if (info.isNoCollapse()) {
      parts.add("@nocollapse");
    }

    Set<String> suppressions = info.getSuppressions();
    if (!suppressions.isEmpty()) {
      // Print suppressions in sorted order to avoid non-deterministic output.
      String[] arr = suppressions.toArray(new String[0]);
      Arrays.sort(arr, Ordering.natural());
      parts.add("@suppress {" + Joiner.on(',').join(arr) + "}");
      multiline = true;
    }

    if (info.isDeprecated()) {
      parts.add("@deprecated " + info.getDeprecationReason());
      multiline = true;
    }

    if (info.isPolymer()) {
      multiline = true;
      parts.add("@polymer");
    }
    if (info.isPolymerBehavior()) {
      multiline = true;
      parts.add("@polymerBehavior");
    }
    if (info.isMixinFunction()) {
      multiline = true;
      parts.add("@mixinFunction");
    }
    if (info.isMixinClass()) {
      multiline = true;
      parts.add("@mixinClass");
    }
    if (info.isCustomElement()) {
      multiline = true;
      parts.add("@customElement");
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

  private Node stripBang(Node typeNode) {
    if (typeNode.getToken() == Token.BANG) {
      typeNode = typeNode.getFirstChild();
    }
    return typeNode;
  }

  private String buildAnnotationWithType(String annotation, JSTypeExpression type) {
    return buildAnnotationWithType(annotation, type.getRoot());
  }

  private String buildAnnotationWithType(String annotation, Node type) {
    StringBuilder sb = new StringBuilder();
    sb.append("@");
    sb.append(annotation);
    sb.append(" {");
    appendTypeNode(sb, type);
    sb.append("}");
    return sb.toString();
  }

  private String buildParamType(String name, JSTypeExpression type) {
    if (type != null) {
      return "{" + typeNode(type.getRoot()) + "} " + name;
    } else {
      return name;
    }
  }

  private String typeNode(Node typeNode) {
    StringBuilder sb = new StringBuilder();
    appendTypeNode(sb, typeNode);
    return sb.toString();
  }

  private void appendTypeNode(StringBuilder sb, Node typeNode) {
    if (useOriginalName && typeNode.getOriginalName() != null) {
      sb.append(typeNode.getOriginalName());
      return;
    }
    if (typeNode.getToken() == Token.BANG) {
      sb.append("!");
      appendTypeNode(sb, typeNode.getFirstChild());
    } else if (typeNode.getToken() == Token.EQUALS) {
      appendTypeNode(sb, typeNode.getFirstChild());
      sb.append("=");
    } else if (typeNode.getToken() == Token.PIPE) {
      sb.append("(");
      Node lastChild = typeNode.getLastChild();
      for (Node child = typeNode.getFirstChild(); child != null; child = child.getNext()) {
        appendTypeNode(sb, child);
        if (child != lastChild) {
          sb.append("|");
        }
      }
      sb.append(")");
    } else if (typeNode.getToken() == Token.ELLIPSIS) {
      sb.append("...");
      if (typeNode.hasChildren() && !typeNode.getFirstChild().isEmpty()) {
        appendTypeNode(sb, typeNode.getFirstChild());
      }
    } else if (typeNode.getToken() == Token.STAR) {
      sb.append("*");
    } else if (typeNode.getToken() == Token.QMARK) {
      sb.append("?");
      if (typeNode.hasChildren()) {
        appendTypeNode(sb, typeNode.getFirstChild());
      }
    } else if (typeNode.isFunction()) {
      appendFunctionNode(sb, typeNode);
    } else if (typeNode.getToken() == Token.LC) {
      sb.append("{");
      Node lb = typeNode.getFirstChild();
      Node lastColon = lb.getLastChild();
      for (Node colon = lb.getFirstChild(); colon != null; colon = colon.getNext()) {
        if (colon.hasChildren()) {
          sb.append(colon.getFirstChild().getString()).append(":");
          appendTypeNode(sb, colon.getLastChild());
        } else {
          sb.append(colon.getString());
        }
        if (colon != lastColon) {
          sb.append(",");
        }
      }
      sb.append("}");
    } else if (typeNode.isVoid()) {
      sb.append("void");
    } else if (typeNode.isTypeOf()) {
      sb.append("typeof ");
      appendTypeNode(sb, typeNode.getFirstChild());
    } else {
      if (typeNode.hasChildren()) {
        sb.append(typeNode.getString())
            .append("<");
        Node child = typeNode.getFirstChild();
        Node last = child.getLastChild();
        for (Node type = child.getFirstChild(); type != null; type = type.getNext()) {
          appendTypeNode(sb, type);
          if (type != last) {
            sb.append(",");
          }
        }
        sb.append(">");
      } else {
        sb.append(typeNode.getString());
      }
    }
  }

  private void appendFunctionNode(StringBuilder sb, Node function) {
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
