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
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Gathers property names defined in externs.
 *
 * The collection of these property names could easily happen during
 * type checking. However, when exporting local property definitions,
 * the externs may be modified after type checking, and we want to
 * collect the new names as well.
 *
 * NOTE(dimvar): with NTI, we collect the relevant property names
 * during type checking, and we run this pass just to collect new
 * names that come from local exports. The type-visitor part is not
 * executed because getJSType returns null.
 */
class GatherExternProperties extends AbstractPostOrderCallback
    implements CompilerPass {
  private final Set<String> externProperties;
  private final AbstractCompiler compiler;

  public GatherExternProperties(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.externProperties = compiler.getExternProperties() == null
        ? new LinkedHashSet<String>()
        : new LinkedHashSet<String>(compiler.getExternProperties());
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, externs, this);
    compiler.setExternProperties(ImmutableSet.copyOf(externProperties));
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getToken()) {
      case GETPROP:
        // Gathers "name" from (someObject.name).
        Node dest = n.getSecondChild();
        if (dest.isString()) {
          externProperties.add(dest.getString());
        }
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

    JSDocInfo jsDocInfo = n.getJSDocInfo();
    if (jsDocInfo != null) {
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
