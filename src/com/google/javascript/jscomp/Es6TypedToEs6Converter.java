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
import com.google.javascript.jscomp.Es6ToEs3Converter.ClassDeclarationMetadata;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Conversion pass that converts ES6 type syntax code to plain ES6, currently transpiles member
 * variables (aka fields).
 */
public class Es6TypedToEs6Converter implements NodeTraversal.Callback, HotSwapCompilerPass {
  static final DiagnosticType CANNOT_CONVERT_MEMBER_VARIABLES = DiagnosticType.error(
      "JSC_CANNOT_CONVERT_FIELDS",
      "Can only convert class member variables (fields) in declarations or the right hand side of "
          + "a simple assignment.");

  private final AbstractCompiler compiler;

  Es6TypedToEs6Converter(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.getType() != Token.CLASS) {
      return;
    }
    Node classNode = n;

    Node classMembers = classNode.getLastChild();
    // Find the constructor, see if it has member variables.
    Node constructor = null;
    boolean hasMemberVariable = false;
    for (Node member : classMembers.children()) {
      if (member.isMemberFunctionDef() && member.getString().equals("constructor")) {
        constructor = member.getFirstChild();
      } else {
        hasMemberVariable |=
            member.isMemberVariableDef()
                || (member.isComputedProp() && member.getBooleanProp(Node.COMPUTED_PROP_VARIABLE));
      }
      if (constructor != null && hasMemberVariable) {
        break;
      }
    }

    if (!hasMemberVariable) {
      return;
    }

    Preconditions.checkNotNull(constructor, "Constructor should be added by Es6ConvertSuper");

    ClassDeclarationMetadata metadata = ClassDeclarationMetadata.create(n, parent);
    if (metadata == null) {
      compiler.report(JSError.make(n, CANNOT_CONVERT_MEMBER_VARIABLES));
      return;
    }

    Node classNameAccess = NodeUtil.newQName(compiler, metadata.fullClassName);
    Node memberVarInsertionPoint = null;  // To insert up front initially
    for (Node member : classMembers.children()) {
      // Functions are handled by the regular Es6ToEs3Converter
      if (!member.isMemberVariableDef() && !member.getBooleanProp(Node.COMPUTED_PROP_VARIABLE)) {
        continue;
      }
      compiler.reportCodeChange();
      member.getParent().removeChild(member);

      Node qualifiedMemberAccess =
          Es6ToEs3Converter.getQualifiedMemberAccess(compiler, member, classNameAccess,
              IR.thisNode());
      // Copy type information.
      qualifiedMemberAccess.setJSDocInfo(member.getJSDocInfo());
      Node newNode = NodeUtil.newExpr(qualifiedMemberAccess);
      newNode.useSourceInfoIfMissingFromForTree(member);
      if (member.isStaticMember()) {
        // Static fields are transpiled on the ctor function.
        metadata.insertStaticMember(newNode);
      } else {
        // Instance fields are transpiled to statements inside the ctor function.
        constructor.getLastChild().addChildAfter(newNode, memberVarInsertionPoint);
        memberVarInsertionPoint = newNode;
      }
    }
  }
}
