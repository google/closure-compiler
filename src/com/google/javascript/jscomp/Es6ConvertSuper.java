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
import com.google.javascript.rhino.JSDocInfoBuilder;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Converts {@code super} nodes. This has to run before the main
 * {@link Es6ToEs3Converter} pass.
 */
public final class Es6ConvertSuper implements NodeTraversal.Callback, HotSwapCompilerPass {
  static final DiagnosticType NO_SUPERTYPE = DiagnosticType.error(
      "JSC_NO_SUPERTYPE",
      "The super keyword may only appear in classes with an extends clause.");

  private static final String SUPER_THIS = "$jscomp$super$this";

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
          break;
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
        Node baseCall = IR.call(
            IR.getprop(superClass.cloneTree(), IR.string("apply")),
            IR.thisNode(),
            IR.name("arguments"));

        body.addChildToFront(IR.returnNode(IR.or(baseCall, IR.thisNode())));
      }
      Node constructor = IR.function(
          IR.name(""),
          IR.paramList(IR.name("var_args")),
          body);
      memberDef = IR.memberFunctionDef("constructor", constructor);
      JSDocInfoBuilder info = new JSDocInfoBuilder(false);
      info.recordParameter(
          "var_args",
          new JSTypeExpression(
              new Node(Token.ELLIPSIS, new Node(Token.QMARK)), "<Es6ConvertSuper>"));
      memberDef.setJSDocInfo(info.build());
    }
    memberDef.useSourceInfoIfMissingFromForTree(classNode);
    classMembers.addChildToFront(memberDef);
  }

  /**
   * @return Whether this is a 'super' node that we know how to handle.
   */
  private boolean validateSuper(Node superNode, Node parent) {
    Preconditions.checkArgument(superNode.isSuper());
    Node enclosingCall = parent;
    Node potentialCallee = superNode;
    if (!parent.isCall()) {
      enclosingCall = parent.getParent();
      potentialCallee = parent;
    }
    if (!enclosingCall.isCall()
        || enclosingCall.getFirstChild() != potentialCallee
        || enclosingCall.getFirstChild().isGetElem()) {
      compiler.report(
          JSError.make(
              superNode,
              CANNOT_CONVERT_YET,
              "Only calls to super or to a method of super are supported."));
      return false;
    }

    Node clazz = NodeUtil.getEnclosingClass(superNode);
    if (clazz == null) {
      compiler.report(JSError.make(superNode, NO_SUPERTYPE));
      return false;
    }
    if (NodeUtil.getNameNode(clazz) == null) {
      // Unnamed classes of the form:
      //   f(class extends D { ... });
      // will be rejected when the class is processed.
      return false;
    }

    Node superName = clazz.getSecondChild();
    if (!superName.isQualifiedName()) {
      // This will be reported as an error in Es6ToEs3Converter.
      return false;
    }

    return true;
  }

  private void visitSuper(Node superNode, Node parent) {
    if (!validateSuper(superNode, parent)) {
      return;
    }
    Node enclosingCall = parent;
    Node callee = superNode;
    if (!parent.isCall()) {
      enclosingCall = parent.getParent();
      callee = parent;
    }
    Node clazz = NodeUtil.getEnclosingClass(superNode);
    String className = NodeUtil.getName(clazz);
    Node superName = clazz.getSecondChild();

    Node enclosingMemberDef = NodeUtil.getEnclosingClassMemberFunction(superNode);
    if (enclosingMemberDef.isStaticMember()) {
      Node callTarget;
      callee.detachFromParent();
      if (callee == superNode) {
        // of the form super()
        callee = IR.getprop(superName.cloneTree(), IR.string(enclosingMemberDef.getString()));
        enclosingCall.putBooleanProp(Node.FREE_CALL, false);
      } else {
        // of the form super.method()
        callee.replaceChild(superNode, superName.cloneTree());
      }
      callTarget = IR.getprop(callee, IR.string("call"));
      enclosingCall.addChildToFront(callTarget);
      enclosingCall.addChildAfter(IR.thisNode(), callTarget);
      enclosingCall.useSourceInfoIfMissingFromForTree(enclosingCall);
      compiler.reportCodeChange();
      return;
    }

    String methodName;
    Node callName = enclosingCall.removeFirstChild();
    Node superThis = IR.name(SUPER_THIS);
    superThis.setOriginalName("this");
    if (callName.isSuper()) {
      methodName = enclosingMemberDef.getString();
      if (methodName.equals("constructor")) {
        Node ctorFunction = enclosingMemberDef.getFirstChild();
        Node body = NodeUtil.getFunctionBody(ctorFunction);
        NodeTraversal.traverseEs6(compiler, body, new UpdateThisReferences());
        body.addChildToBack(IR.returnNode(superThis.cloneNode()));
      }
    } else {
      methodName = callName.getLastChild().getString();
    }

    Node baseCall = baseCall(
        superName.getQualifiedName(), methodName, enclosingCall.removeChildren());
    Node nodeToReplace = enclosingCall;
    if (methodName.equals("constructor")) {
      baseCall = IR.var(
          superThis.cloneNode(),
          IR.or(baseCall, IR.thisNode()));
      JSDocInfoBuilder info = new JSDocInfoBuilder(false);
      info.recordType(new JSTypeExpression(
          new Node(Token.BANG, IR.string(className)), clazz.getSourceFileName()));
      info.recordConstancy();
      baseCall.setJSDocInfo(info.build());
      nodeToReplace = enclosingCall.getParent();
      Preconditions.checkState(nodeToReplace.isExprResult());
    }
    baseCall.useSourceInfoIfMissingFromForTree(enclosingCall);
    nodeToReplace.getParent().replaceChild(nodeToReplace, baseCall);
    compiler.reportCodeChange();
  }

  private static class UpdateThisReferences implements NodeTraversal.Callback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (n.isThis()) {
        Node name = IR.name(SUPER_THIS).srcref(n);
        name.setOriginalName("this");
        parent.replaceChild(n, name);
      }
    }

    @Override
    public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return !n.isFunction() || n.isArrowFunction();
    }
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
    // Might need to synthesize constructors for ambient classes in .d.ts externs
    TranspilationPasses.processTranspile(compiler, externs, this);
    TranspilationPasses.processTranspile(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    TranspilationPasses.hotSwapTranspile(compiler, scriptRoot, this);
  }
}
