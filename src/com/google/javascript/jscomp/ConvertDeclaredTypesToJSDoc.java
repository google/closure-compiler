/*
 * Copyright 2015 The Closure Compiler Authors.
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

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Node.TypeDeclarationNode;
import com.google.javascript.rhino.Token;

/**
 * Converts {@link Node#getDeclaredTypeExpression()} to {@link JSDocInfo#getType()} type
 * annotations. Types are marked as inline types.
 */
public class ConvertDeclaredTypesToJSDoc extends AbstractPostOrderCallback implements CompilerPass {

  private final AbstractCompiler compiler;

  ConvertDeclaredTypesToJSDoc(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node scriptRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    TypeDeclarationNode type = n.getDeclaredTypeExpression();
    if (type == null) {
      return;
    }

    JSDocInfo info = n.getJSDocInfo();
    Preconditions.checkState(info == null || info.getType() == null,
        "Nodes must not have both type declarations and JSDoc types");
    JSDocInfoBuilder builder = JSDocInfoBuilder.maybeCopyFrom(info);

    Node typeRoot = convertWithLocation(type);
    JSTypeExpression typeExpression = new JSTypeExpression(typeRoot, n.getSourceFileName());
    if (n.isFunction()) {
      builder.recordReturnType(typeExpression);
    } else {
      builder.recordType(typeExpression);
      builder.recordInlineType();
    }

    info = builder.build(n);
    n.setJSDocInfo(info);
    n.setDeclaredTypeExpression(null); // clear out declared type
  }

  private Node convertWithLocation(Node type) {
    return convertDeclaredTypeToJSDoc(type).copyInformationFrom(type);
  }

  private Node convertDeclaredTypeToJSDoc(Node type) {
    switch (type.getType()) {
      // "Primitive" types.
      case Token.STRING_TYPE:
        return IR.string("string");
      case Token.BOOLEAN_TYPE:
        return IR.string("boolean");
      case Token.NUMBER_TYPE:
        return IR.string("number");
      case Token.VOID_TYPE:
        return IR.string("void");
      case Token.ANY_TYPE:
        return new Node(Token.QMARK);
        // Named types.
      case Token.NAMED_TYPE:
        return convertNamedType(type);
      case Token.ARRAY_TYPE: {
        Node arrayType = IR.string("Array");
        Node memberType = convertWithLocation(type.getFirstChild());
        arrayType.addChildToFront(new Node(Token.BLOCK, memberType).copyInformationFrom(type));
        return arrayType;
      }
      case Token.PARAMETERIZED_TYPE: {
        Node namedType = type.getFirstChild();
        Node result = convertWithLocation(namedType);
        Node parameters = IR.block().copyInformationFrom(type);
        result.addChildToFront(parameters);
        for (Node param = namedType.getNext(); param != null; param = param.getNext()) {
          parameters.addChildToBack(convertWithLocation(param));
        }
        return result;
      }
      // Composite types.
      case Token.FUNCTION_TYPE:
      case Token.UNION_TYPE:
      case Token.OPTIONAL_PARAMETER:
      case Token.RECORD_TYPE:
      default:
        // TODO(martinprobst): Implement.
        break;
    }
    throw new IllegalArgumentException("Unexpected node type for type conversion: "
        + type.getType());
  }

  private Node convertNamedType(Node type) {
    Node propTree = type.getFirstChild();
    String dotted = propTree.getQualifiedName();
    return IR.string(dotted);
  }
}
