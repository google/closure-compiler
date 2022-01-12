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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableSet;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.LinkedHashSet;

/**
 * Gathers property names defined in externs.
 *
 * <p>The collection of these property names could easily happen during type checking. However, when
 * exporting local property definitions, the externs may be modified after type checking, and we
 * want to collect the new names as well.
 *
 * <p>Mode.CHECK is set when checksOnly=true and only collects the externs in the type expressions
 * in JSDoc. Mode.OPTIMIZE is set when checksOnly=false and only collects the externs in AST
 * properties.
 *
 * <p>To optimize build time, we collect only type expression externs in getChecks() and only AST
 * property externs in getOptimizations() instead of collecting all in both.
 */
final class GatherExternProperties implements NodeTraversal.Callback, CompilerPass {
  private final AbstractCompiler compiler;

  private final LinkedHashSet<String> externProperties = new LinkedHashSet<>();

  enum Mode {
    CHECK(true, false),
    OPTIMIZE(false, true),
    CHECK_AND_OPTIMIZE(true, true);

    private final boolean check;
    private final boolean optimize;

    Mode(boolean check, boolean optimize) {
      this.check = check;
      this.optimize = optimize;
    }
  }

  private final Mode mode;

  GatherExternProperties(AbstractCompiler compiler, Mode mode) {
    this.compiler = compiler;
    this.mode = mode;

    if (compiler.getExternProperties() != null) {
      this.externProperties.addAll(compiler.getExternProperties());
    }
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, externs, this);
    compiler.setExternProperties(ImmutableSet.copyOf(externProperties));
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return !n.isScript() || !NodeUtil.isFromTypeSummary(n);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (this.mode.optimize) {
      switch (n.getToken()) {
        case GETPROP:
          // Gathers "name" from (someObject.name).
          externProperties.add(n.getString());
          break;
        case STRING_KEY:
          if (parent.isObjectLit()) {
            externProperties.add(n.getString());
          }
          break;
        case MEMBER_FUNCTION_DEF:
          externProperties.add(n.getString());
          break;
        default:
          break;
      }
    }

    JSDocInfo jsDocInfo = n.getJSDocInfo();
    if (jsDocInfo != null && this.mode.check) {
      gatherPropertiesFromJSDocInfo(jsDocInfo);
    }
  }

  private void gatherPropertiesFromJSDocInfo(JSDocInfo jsDocInfo) {
    for (Node jsTypeExpressionNode : jsDocInfo.getTypeNodes()) {
      gatherPropertiesFromJsTypeExpressionNode(jsTypeExpressionNode);
    }
  }

  private void gatherPropertiesFromJsTypeExpressionNode(Node jsTypeExpressionNode) {
    switch (jsTypeExpressionNode.getToken()) {
      case LB:
        gatherPropertiesFromJsDocRecordType(jsTypeExpressionNode);
        break;
      default:
        for (Node child = jsTypeExpressionNode.getFirstChild();
            child != null;
            child = child.getNext()) {
          gatherPropertiesFromJsTypeExpressionNode(child);
        }
    }
  }

  private void gatherPropertiesFromJsDocRecordType(Node jsDocRecordNode) {
    checkState(jsDocRecordNode.getToken() == Token.LB, jsDocRecordNode);
    for (Node fieldNode = jsDocRecordNode.getFirstChild();
        fieldNode != null;
        fieldNode = fieldNode.getNext()) {
      Node fieldNameNode;
      Node fieldTypeNode;
      if (fieldNode.getToken() == Token.COLON) {
        fieldNameNode = fieldNode.getFirstChild();
        fieldTypeNode = fieldNameNode.getNext();
      } else {
        fieldNameNode = fieldNode;
        fieldTypeNode = null;
      }
      checkState(fieldNameNode.isStringKey(), fieldNameNode);
      String fieldName = fieldNameNode.getString();
      // TODO(bradfordcsmith): The JSDoc parser should do this.
      if (fieldName.startsWith("'") || fieldName.startsWith("\"")) {
        fieldName = fieldName.substring(1, fieldName.length() - 1);
      }
      externProperties.add(fieldName);
      if (fieldTypeNode != null) {
        gatherPropertiesFromJsTypeExpressionNode(fieldTypeNode);
      }
    }
  }
}
