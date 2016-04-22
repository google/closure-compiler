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

import static com.google.javascript.jscomp.Es6ToEs3Converter.CANNOT_CONVERT_YET;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

/**
 * Converts {@code super} nodes. This has to run before the main
 * {@link Es6ToEs3Converter} pass.
 */
public final class Es6ConvertSuper implements NodeTraversal.Callback, HotSwapCompilerPass {
  private final AbstractCompiler compiler;

  public Es6ConvertSuper(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if (n.isClass()) {
      boolean hasConstructor = false;
      for (Node member = n.getLastChild().getFirstChild();
          member != null;
          member = member.getNext()) {
        if (member.isMemberFunctionDef() && member.getString().equals("constructor")) {
          hasConstructor = true;
        }
      }
      if (!hasConstructor) {
        addSyntheticConstructor(n);
      }
    }
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.isSuper()) {
      visitSuper(n, parent);
    }
  }

  private void addSyntheticConstructor(Node classNode) {
    Node superClass = classNode.getSecondChild();
    Node classMembers = classNode.getLastChild();
    Node memberDef;
    if (superClass.isEmpty()) {
      memberDef = IR.memberFunctionDef("constructor",
          IR.function(IR.name(""), IR.paramList(), IR.block()));
    } else {
      if (!superClass.isQualifiedName()) {
        // This will be reported as an error in Es6ToEs3Converter.
        return;
      }
      Node body = IR.block();
      if (!classNode.isFromExterns()) {
        Node exprResult = IR.exprResult(IR.call(
            IR.getprop(superClass.cloneTree(), IR.string("apply")),
            IR.thisNode(),
            IR.name("arguments")));
        body.addChildrenToFront(exprResult);
      }
      Node constructor = IR.function(
          IR.name(""),
          IR.paramList(IR.name("var_args")),
          body);
      memberDef = IR.memberFunctionDef("constructor", constructor);
    }
    memberDef.useSourceInfoIfMissingFromForTree(classNode);
    classMembers.addChildToFront(memberDef);
  }

  private void visitSuper(Node node, Node parent) {
    Node enclosingCall = parent;
    Node potentialCallee = node;
    if (!parent.isCall()) {
      enclosingCall = parent.getParent();
      potentialCallee = parent;
    }
    if (!enclosingCall.isCall()
        || enclosingCall.getFirstChild() != potentialCallee
        || enclosingCall.getFirstChild().isGetElem()) {
      compiler.report(JSError.make(node, CANNOT_CONVERT_YET,
          "Only calls to super or to a method of super are supported."));
      return;
    }
    Node clazz = NodeUtil.getEnclosingClass(node);
    if (NodeUtil.getNameNode(clazz) == null) {
      // Unnamed classes of the form:
      //   f(class extends D { ... });
      // will be rejected when the class is processed.
      return;
    }

    Node superName = clazz.getSecondChild();
    if (!superName.isQualifiedName()) {
      // This will be reported as an error in Es6ToEs3Converter.
      return;
    }

    Node enclosingMemberDef = NodeUtil.getEnclosingClassMemberFunction(node);
    if (enclosingMemberDef.isStaticMember()) {
      Node callTarget;
      potentialCallee.detachFromParent();
      if (potentialCallee == node) {
        // of the form super()
        potentialCallee =
            IR.getprop(superName.cloneTree(), IR.string(enclosingMemberDef.getString()));
        enclosingCall.putBooleanProp(Node.FREE_CALL, false);
      } else {
        // of the form super.method()
        potentialCallee.replaceChild(node, superName.cloneTree());
      }
      callTarget = IR.getprop(potentialCallee, IR.string("call"));
      enclosingCall.addChildToFront(callTarget);
      enclosingCall.addChildAfter(IR.thisNode(), callTarget);
      enclosingCall.useSourceInfoIfMissingFromForTree(enclosingCall);
      compiler.reportCodeChange();
      return;
    }

    String methodName;
    Node callName = enclosingCall.removeFirstChild();
    if (callName.isSuper()) {
      methodName = enclosingMemberDef.getString();
    } else {
      methodName = callName.getLastChild().getString();
    }
    Node baseCall = baseCall(
        superName.getQualifiedName(), methodName, enclosingCall.removeChildren());
    baseCall.useSourceInfoIfMissingFromForTree(enclosingCall);
    enclosingCall.getParent().replaceChild(enclosingCall, baseCall);
    compiler.reportCodeChange();
  }

  private Node baseCall(String baseClass, String methodName, Node arguments) {
    Preconditions.checkNotNull(baseClass);
    Preconditions.checkNotNull(methodName);
    String baseMethodName;
    if (methodName.equals("constructor")) {
      baseMethodName = baseClass + ".call";
    } else {
      baseMethodName = Joiner.on('.').join(baseClass, "prototype", methodName, "call");
    }
    Node methodCall = NodeUtil.newQName(compiler, baseMethodName);
    Node callNode = IR.call(methodCall, IR.thisNode());
    if (arguments != null) {
      callNode.addChildrenToBack(arguments);
    }
    return callNode;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverseEs6(compiler, externs, this);
    NodeTraversal.traverseEs6(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverseEs6(compiler, scriptRoot, this);
  }
}
