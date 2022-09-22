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

import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.jscomp.Var;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleSourceFile;
import com.google.javascript.rhino.StaticSourceFile;
import com.google.javascript.rhino.StaticSourceFile.SourceKind;
import com.google.javascript.rhino.Token;
import org.jspecify.nullness.Nullable;

/**
 * Static utility methods for dealing with inspecting and constructing JSDoc objects.
 */
final class JsdocUtil {
  private JsdocUtil() {}

  private static final String SYNTHETIC_FILE_NAME = "<synthetic>";
  private static final StaticSourceFile SYNTHETIC_FILE =
      new SimpleSourceFile(SYNTHETIC_FILE_NAME, SourceKind.EXTERN);
  private static final Node SYNTETIC_SRCINFO_NODE =
      new Node(Token.EMPTY).setStaticSourceFile(SYNTHETIC_FILE);

  static boolean isPrivate(@Nullable JSDocInfo jsdoc) {
    return jsdoc != null && jsdoc.getVisibility().equals(Visibility.PRIVATE);
  }

  static JSDocInfo getUnusableTypeJSDoc(JSDocInfo oldJSDoc) {
    return getConstJSDoc(oldJSDoc, "UnusableType");
  }

  static JSDocInfo getQmarkTypeJSDoc() {
    return makeBuilderWithType(null, new Node(Token.QMARK)).build();
  }

  private static JSDocInfo.Builder makeBuilderWithType(@Nullable JSDocInfo oldJSDoc, Node typeAst) {
    JSDocInfo.Builder builder = JSDocInfo.Builder.maybeCopyFrom(oldJSDoc);
    builder.recordType(
        new JSTypeExpression(typeAst.srcrefTree(SYNTETIC_SRCINFO_NODE), SYNTHETIC_FILE_NAME));
    return builder;
  }

  private static JSDocInfo getConstJSDoc(JSDocInfo oldJSDoc, String contents) {
    return getConstJSDoc(oldJSDoc, Node.newString(contents));
  }

  private static JSDocInfo getConstJSDoc(JSDocInfo oldJSDoc, Node typeAst) {
    JSDocInfo.Builder builder = makeBuilderWithType(oldJSDoc, typeAst);
    builder.recordConstancy();
    return builder.build();
  }

  static JSDocInfo markConstant(JSDocInfo oldJSDoc) {
    JSDocInfo.Builder builder = JSDocInfo.Builder.maybeCopyFrom(oldJSDoc);
    builder.recordConstancy();
    return builder.build();
  }

  static JSDocInfo mergeJsdocs(@Nullable JSDocInfo classicJsdoc, @Nullable JSDocInfo inlineJsdoc) {
    if (inlineJsdoc == null || !inlineJsdoc.hasType()) {
      return classicJsdoc;
    }
    JSDocInfo.Builder builder = JSDocInfo.Builder.maybeCopyFrom(classicJsdoc);
    builder.recordType(inlineJsdoc.getType());
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
        || jsdoc.hasTypedefType()
        || jsdoc.hasThisType()
        || jsdoc.hasEnumParameterType();
  }

  static @Nullable JSDocInfo getJSDocForRhs(Node rhs, JSDocInfo oldJSDoc) {
    switch (NodeUtil.getKnownValueType(rhs)) {
      case BOOLEAN:
        return getConstJSDoc(oldJSDoc, "boolean");
      case NUMBER:
        return getConstJSDoc(oldJSDoc, "number");
      case BIGINT:
        return getConstJSDoc(oldJSDoc, "bigint");
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
      case UNDETERMINED:
        if (oldJSDoc != null && oldJSDoc.getDescription() != null) {
          return getConstJSDoc(oldJSDoc, "string");
        }
        break;
    }
    if (rhs.isCast()) {
      return getConstJSDoc(oldJSDoc, rhs.getJSDocInfo().getType().getRoot());
    }
    return null;
  }

  static @Nullable JSDocInfo getJSDocForName(Var decl, JSDocInfo oldJSDoc) {
    if (decl == null) {
      return null;
    }
    JSTypeExpression expr = NodeUtil.getDeclaredTypeExpression(decl.getNameNode());
    if (expr == null) {
      return null;
    }
    Node typeAst = expr.getRoot();
    switch (typeAst.getToken()) {
      case EQUALS:
        Node typeRoot = typeAst.getFirstChild().cloneTree();
        if (!decl.isDefaultParam()) {
          typeRoot = new Node(Token.PIPE, typeRoot, IR.string("undefined"));
        }
        typeAst = typeRoot;
        break;
      case ITER_REST:
        {
          Node newType = new Node(Token.BANG);
          Node array = IR.string("Array");
          newType.addChildToBack(array);
          Node block = new Node(Token.BLOCK, typeAst.getFirstChild().cloneTree());
          array.addChildToBack(block);
          typeAst = newType;
          break;
        }
      default:
        break;
    }
    return getConstJSDoc(oldJSDoc, typeAst);
  }
}
