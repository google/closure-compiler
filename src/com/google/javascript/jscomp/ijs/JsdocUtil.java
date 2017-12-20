/*
 * Copyright 2017 The Closure Compiler Authors.
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
package com.google.javascript.jscomp.ijs;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.Var;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import javax.annotation.Nullable;

/**
 * Static utility methods for dealing with inspecting and constructing JSDoc objects.
 */
final class JsdocUtil {
  private JsdocUtil() {}

  static JSDocInfo updateJsdoc(AbstractCompiler compiler, Node nameNode) {
    checkArgument(nameNode.isStringKey(), nameNode);
    Node jsdocNode = nameNode;
    JSDocInfo jsdoc = jsdocNode.getJSDocInfo();
    if (jsdoc == null) {
      jsdoc = JsdocUtil.getAllTypeJSDoc();
    } else if (ConvertToTypedInterface.isConstToBeInferred(jsdoc, nameNode, false)) {
      jsdoc = JsdocUtil.pullJsdocTypeFromAst(compiler, jsdoc, nameNode);
    }
    jsdocNode.setJSDocInfo(jsdoc);
    return jsdoc;
  }

  static JSDocInfo pullJsdocTypeFromAst(
      AbstractCompiler compiler, JSDocInfo oldJSDoc, Node nameNode) {
    checkArgument(nameNode.isQualifiedName() || nameNode.isStringKey(), nameNode);
    if (oldJSDoc != null && oldJSDoc.getDescription() != null) {
      return getConstJSDoc(oldJSDoc, "string");
    }
    if (!nameNode.isFromExterns() && !isPrivate(oldJSDoc)) {
      compiler.report(
          JSError.make(nameNode, ConvertToTypedInterface.CONSTANT_WITHOUT_EXPLICIT_TYPE));
    }
    return getConstJSDoc(oldJSDoc, new Node(Token.STAR));
  }

  private static boolean isPrivate(@Nullable JSDocInfo jsdoc) {
    return jsdoc != null && jsdoc.getVisibility().equals(Visibility.PRIVATE);
  }

  static JSDocInfo getAllTypeJSDoc() {
    return getConstJSDoc(null, new Node(Token.STAR));
  }

  static JSDocInfo getQmarkTypeJSDoc() {
    return getConstJSDoc(null, new Node(Token.QMARK));
  }

  private static JSTypeExpression asTypeExpression(Node typeAst) {
    return new JSTypeExpression(typeAst, "<synthetic>");
  }

  private static JSDocInfo getConstJSDoc(JSDocInfo oldJSDoc, String contents) {
    return getConstJSDoc(oldJSDoc, Node.newString(contents));
  }

  private static JSDocInfo getConstJSDoc(JSDocInfo oldJSDoc, Node typeAst) {
    return getConstJSDoc(oldJSDoc, asTypeExpression(typeAst));
  }

  private static JSDocInfo getConstJSDoc(JSDocInfo oldJSDoc, JSTypeExpression newType) {
    JSDocInfoBuilder builder = JSDocInfoBuilder.maybeCopyFrom(oldJSDoc);
    builder.recordType(newType);
    builder.recordConstancy();
    return builder.build();
  }

  static boolean hasAnnotatedType(JSDocInfo jsdoc) {
    if (jsdoc == null) {
      return false;
    }
    return jsdoc.hasType()
        || jsdoc.hasReturnType()
        || jsdoc.getParameterCount() > 0
        || jsdoc.isConstructorOrInterface()
        || jsdoc.hasThisType()
        || jsdoc.hasEnumParameterType();
  }

  static JSDocInfo getJSDocForRhs(Node rhs, JSDocInfo oldJSDoc) {
    switch (NodeUtil.getKnownValueType(rhs)) {
      case BOOLEAN:
        return getConstJSDoc(oldJSDoc, "boolean");
      case NUMBER:
        return getConstJSDoc(oldJSDoc, "number");
      case STRING:
        return getConstJSDoc(oldJSDoc, "string");
      case NULL:
        return getConstJSDoc(oldJSDoc, "null");
      case VOID:
        return getConstJSDoc(oldJSDoc, "void");
      case OBJECT:
        if (rhs.isRegExp()) {
          return getConstJSDoc(oldJSDoc, new Node(Token.BANG, IR.string("RegExp")));
        }
        break;
      default:
        break;
    }
    return null;
  }

  static JSDocInfo getJSDocForName(Var decl, JSDocInfo oldJSDoc) {
    if (decl == null) {
      return null;
    }
    JSTypeExpression expr = NodeUtil.getDeclaredTypeExpression(decl.getNameNode());
    if (expr == null) {
      return null;
    }
    switch (expr.getRoot().getToken()) {
      case EQUALS:
        Node typeRoot = expr.getRoot().getFirstChild().cloneTree();
        if (!decl.isDefaultParam()) {
          typeRoot = new Node(Token.PIPE, typeRoot, IR.string("undefined"));
        }
        expr = asTypeExpression(typeRoot);
        break;
      case ELLIPSIS:
        {
          Node type = new Node(Token.BANG);
          Node array = IR.string("Array");
          type.addChildToBack(array);
          Node block = new Node(Token.BLOCK, expr.getRoot().getFirstChild().cloneTree());
          array.addChildToBack(block);
          expr = asTypeExpression(type);
          break;
        }
      default:
        break;
    }
    return getConstJSDoc(oldJSDoc, expr);
  }
}
